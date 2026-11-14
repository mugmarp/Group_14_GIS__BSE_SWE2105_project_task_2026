package com.geotrace.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotrace.LocationTrackerApp
import com.geotrace.model.Group
import com.geotrace.model.LocationPoint
import com.geotrace.model.MapFocusTarget
import com.geotrace.model.MemberLocation
import com.geotrace.model.P2PMeshState
import com.geotrace.model.P2PPeer
import com.geotrace.model.PersonalDevice
import com.geotrace.service.FindDeviceAlarmManager
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GroupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as LocationTrackerApp).groupRepository
    private val locationRepository = (application as LocationTrackerApp).repository
    private val auth: FirebaseAuth = Firebase.auth

    val currentDeviceId: String = repository.currentDeviceId

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { fbAuth ->
        val wasSignedOut = _currentUser.value == null
        _currentUser.value = fbAuth.currentUser
        if (wasSignedOut && fbAuth.currentUser != null) {
            triggerImmediateBroadcast()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val userGroups: StateFlow<List<Group>> = _currentUser
        .flatMapLatest { user ->
            if (user != null) {
                repository.observeUserGroups().catch { e ->
                    _errorMessage.value = e.localizedMessage ?: "Failed to sync tracking groups."
                    emit(emptyList())
                }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val personalDevices: StateFlow<List<PersonalDevice>> = _currentUser
        .flatMapLatest { user ->
            if (user != null) {
                repository.observePersonalDevices().catch { e ->
                    _errorMessage.value = e.localizedMessage ?: "Failed to sync personal devices."
                    emit(emptyList())
                }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _activeGroup = MutableStateFlow<Group?>(null)
    val activeGroup: StateFlow<Group?> = _activeGroup.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val groupMembers: StateFlow<List<MemberLocation>> = _activeGroup
        .flatMapLatest { group ->
            if (group != null) {
                repository.observeGroupLocations(group.id).catch { e ->
                    _errorMessage.value = e.localizedMessage ?: "Failed to load group member locations."
                    emit(emptyList())
                }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nearbyPeers: StateFlow<List<P2PPeer>> = repository.nearbyPeers
    val p2pMeshState: StateFlow<P2PMeshState> = repository.p2pMeshState
    val offlineCallsign: StateFlow<String> = repository.offlineCallsign

    val isRingingAlarm: StateFlow<Boolean> = FindDeviceAlarmManager.isRinging
    val ringCallerName: StateFlow<String?> = FindDeviceAlarmManager.ringCallerName

    private val _isGhostMode = MutableStateFlow(false)
    val isGhostMode: StateFlow<Boolean> = _isGhostMode.asStateFlow()

    private val _isP2PEnabled = MutableStateFlow(false)
    val isP2PEnabled: StateFlow<Boolean> = _isP2PEnabled.asStateFlow()

    private val _isActionLoading = MutableStateFlow(false)
    val isActionLoading: StateFlow<Boolean> = _isActionLoading.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncTimestampMs = MutableStateFlow<Long?>(null)
    val lastSyncTimestampMs: StateFlow<Long?> = _lastSyncTimestampMs.asStateFlow()

    private val _mapFocusTarget = MutableStateFlow<MapFocusTarget?>(null)
    val mapFocusTarget: StateFlow<MapFocusTarget?> = _mapFocusTarget.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var broadcastJob: Job? = null
    private var lastP2PLocationPushMs = 0L

    init {
        auth.addAuthStateListener(authListener)
        repository.onPeerConnectedCallback = { _ ->
            triggerImmediateBroadcast()
        }
        startBroadcastSyncLoop()
        observeLocationForP2P()
        viewModelScope.launch {
            userGroups.collect { groups ->
                val current = _activeGroup.value
                if (current != null) {
                    val refreshed = groups.firstOrNull { it.id == current.id }
                    _activeGroup.value = refreshed ?: groups.firstOrNull()
                } else if (groups.isNotEmpty()) {
                    _activeGroup.value = groups.first()
                    triggerImmediateBroadcast()
                }
            }
        }
        viewModelScope.launch {
            repository.firestoreError.collect { err ->
                if (!err.isNullOrBlank()) {
                    _errorMessage.value = err
                    repository.clearFirestoreError()
                }
            }
        }
    }

    private fun observeLocationForP2P() {
        viewModelScope.launch {
            locationRepository.currentLocation.collect { loc ->
                if (loc != null && _isP2PEnabled.value && !_isGhostMode.value) {
                    val now = System.currentTimeMillis()
                    if (now - lastP2PLocationPushMs >= 4_000L) {
                        lastP2PLocationPushMs = now
                        val context = getApplication<Application>()
                        val (batteryLevel, _) = getBatteryInfo(context)
                        val displayName = repository.getCurrentUserDisplayName()
                        repository.broadcastP2PLocationDirect(loc, displayName, batteryLevel)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(authListener)
        broadcastJob?.cancel()
        if (_isP2PEnabled.value) {
            repository.stopP2PMesh()
        }
    }

    fun selectGroup(group: Group?) {
        _activeGroup.value = group
        if (group != null) {
            _statusMessage.value = "Active Group: ${group.name}"
            triggerImmediateBroadcast()
        }
    }

    fun createGroup(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _errorMessage.value = "Please enter a valid group name."
            return
        }

        viewModelScope.launch {
            _isActionLoading.value = true
            _errorMessage.value = null
            val result = repository.createGroup(trimmed)
            _isActionLoading.value = false

            result.onSuccess { created ->
                _activeGroup.value = created
                _statusMessage.value = "Group '${created.name}' created! Share code: ${created.inviteCode}"
                triggerImmediateBroadcast()
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to create group."
            }
        }
    }

    fun joinGroupByCode(code: String) {
        val trimmed = code.trim().uppercase()
        if (trimmed.length < 4) {
            _errorMessage.value = "Please enter a valid group code."
            return
        }

        viewModelScope.launch {
            _isActionLoading.value = true
            _errorMessage.value = null
            val result = repository.joinGroupByCode(trimmed)
            _isActionLoading.value = false

            result.onSuccess { joined ->
                _activeGroup.value = joined
                _statusMessage.value = "Joined group '${joined.name}'!"
                triggerImmediateBroadcast()
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to join group."
            }
        }
    }

    fun leaveActiveGroup() {
        val group = _activeGroup.value ?: return
        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.leaveGroup(group.id)
            _isActionLoading.value = false

            result.onSuccess {
                _activeGroup.value = null
                _statusMessage.value = "Left '${group.name}'."
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to leave group."
            }
        }
    }

    fun deleteActiveGroup() {
        val group = _activeGroup.value ?: return
        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.deleteGroup(group.id)
            _isActionLoading.value = false

            result.onSuccess {
                _activeGroup.value = null
                _statusMessage.value = "Deleted group '${group.name}'."
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to delete group."
            }
        }
    }

    fun toggleGhostMode() {
        val newMode = !_isGhostMode.value
        _isGhostMode.value = newMode
        _statusMessage.value = if (newMode) "Ghost Mode ON: Location sharing paused" else "Ghost Mode OFF: Live sharing resumed"
        triggerImmediateBroadcast()
    }

    fun updateOfflineCallsign(callsign: String) {
        val cleaned = callsign.trim().take(24)
        if (cleaned.isBlank()) {
            _errorMessage.value = "Device name cannot be empty."
            return
        }
        repository.setOfflineCallsign(cleaned)
        _statusMessage.value = "Device name updated to '$cleaned'"
        triggerImmediateBroadcast()
    }

    fun toggleP2PMesh() {
        val newEnabled = !_isP2PEnabled.value
        _isP2PEnabled.value = newEnabled
        if (newEnabled) {
            val myName = repository.getCurrentUserDisplayName()
            repository.startP2PMesh(myName)
            _statusMessage.value = if (_isGhostMode.value) {
                "Off-Grid P2P Finder ON (Ghost Mode: coordinates hidden)"
            } else {
                "Off-Grid P2P Device Finder ON"
            }
            triggerImmediateBroadcast()
        } else {
            repository.stopP2PMesh()
            _statusMessage.value = "Off-Grid P2P Device Finder OFF"
        }
    }

    fun rescanP2PMesh() {
        if (!_isP2PEnabled.value) return
        val myName = repository.getCurrentUserDisplayName()
        repository.rescanP2PMesh(myName)
        _statusMessage.value = "Restarting Off-Grid P2P scan..."
        triggerImmediateBroadcast()
    }

    fun ringPersonalDevice(device: PersonalDevice) {
        viewModelScope.launch {
            val result = repository.sendRingCommandToDevice(device.deviceId.ifBlank { device.id })
            result.onSuccess {
                _statusMessage.value = "Ring alert sent to ${device.deviceName}!"
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to ring ${device.deviceName}."
            }
        }
    }

    fun cancelRingOnPersonalDevice(device: PersonalDevice) {
        viewModelScope.launch {
            val result = repository.cancelRingCommandForDevice(device.deviceId.ifBlank { device.id })
            result.onSuccess {
                _statusMessage.value = "Cancelled ring alert for ${device.deviceName}."
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to cancel ring alert."
            }
        }
    }

    fun removePersonalDevice(device: PersonalDevice) {
        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.removePersonalDevice(device.deviceId.ifBlank { device.id })
            _isActionLoading.value = false
            result.onSuccess {
                _statusMessage.value = "Removed '${device.deviceName.ifBlank { device.deviceModel }}' from My Devices."
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to remove device."
            }
        }
    }

    fun testSelfRing() {
        FindDeviceAlarmManager.startRinging(
            getApplication(),
            "Self Test (${offlineCallsign.value})"
        )
        _statusMessage.value = "Testing local device alarm. Tap Stop Alarm to silence."
    }

    fun syncNow() {
        val loc = locationRepository.currentLocation.value
        if (loc == null) {
            _errorMessage.value = "Waiting for GPS or cellular fix before syncing."
            return
        }
        val group = _activeGroup.value
        val p2pActive = _isP2PEnabled.value
        val isSignedIn = auth.currentUser != null
        if (group == null && !p2pActive && !isSignedIn) {
            _errorMessage.value = "Sign in or enable Off-Grid P2P to broadcast your location."
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            val ok = performBroadcast(group, loc)
            _isSyncing.value = false
            if (ok) {
                _statusMessage.value = "Live telemetry synced."
            } else {
                _errorMessage.value = "Sync incomplete — check network connection."
            }
        }
    }

    fun focusMapOnTarget(target: MapFocusTarget) {
        _mapFocusTarget.value = target.copy(requestedAtMs = System.currentTimeMillis())
    }

    fun clearMapFocusTarget() {
        _mapFocusTarget.value = null
    }

    fun ringP2PPeer(endpointId: String, peerName: String) {
        repository.sendP2PRingCommand(endpointId)
        _statusMessage.value = "Offline P2P Ring sent to $peerName!"
    }

    fun stopRingingAlarm() {
        FindDeviceAlarmManager.stopRinging(getApplication())
    }

    fun clearStatusMessage() {
        _statusMessage.value = null
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private fun startBroadcastSyncLoop() {
        broadcastJob?.cancel()
        broadcastJob = viewModelScope.launch {
            while (isActive) {
                val loc = locationRepository.currentLocation.value
                val isMoving = (loc?.speed ?: 0f) > 0.5f
                val intervalMs = if (isMoving) 10_000L else 25_000L

                val group = _activeGroup.value
                val p2pActive = _isP2PEnabled.value
                val isSignedIn = auth.currentUser != null
                if (loc != null && (group != null || p2pActive || isSignedIn)) {
                    performBroadcast(group, loc)
                }

                delay(intervalMs)
            }
        }
    }

    private fun triggerImmediateBroadcast() {
        val loc = locationRepository.currentLocation.value ?: return
        val group = _activeGroup.value
        val p2pActive = _isP2PEnabled.value
        val isSignedIn = auth.currentUser != null
        if (group != null || p2pActive || isSignedIn) {
            viewModelScope.launch {
                performBroadcast(group, loc)
            }
        }
    }

    private suspend fun performBroadcast(group: Group?, loc: LocationPoint): Boolean {
        val context = getApplication<Application>()
        val (batteryLevel, isCharging) = getBatteryInfo(context)
        val mode = locationRepository.trackingMode.value.name
        val displayName = repository.getCurrentUserDisplayName()
        var anySucceeded = false

        // Always update personal device fleet doc when signed in so multi-device tracking & Find works out of the box
        if (auth.currentUser != null) {
            val fleetRes = repository.publishPersonalDevice(
                location = loc,
                batteryPercent = batteryLevel,
                isCharging = isCharging,
                trackingMode = mode
            )
            if (fleetRes.isSuccess) anySucceeded = true
        }

        val colorPalette = listOf("#2563EB", "#059669", "#D97706", "#DC2626", "#7C3AED", "#DB2777", "#0891B2")
        val avatarColor = colorPalette[((auth.currentUser?.uid ?: "").hashCode() and 0x7fffffff) % colorPalette.size]

        if (group != null) {
            val groupRes = repository.publishLocation(
                groupId = group.id,
                location = loc,
                displayName = displayName,
                avatarColor = avatarColor,
                batteryPercent = batteryLevel,
                isCharging = isCharging,
                trackingMode = mode,
                isSharingEnabled = !_isGhostMode.value
            )
            if (groupRes.isSuccess) anySucceeded = true
        } else if (_isP2PEnabled.value && !_isGhostMode.value) {
            repository.broadcastP2PLocationDirect(loc, displayName, batteryLevel)
            lastP2PLocationPushMs = System.currentTimeMillis()
            anySucceeded = true
        }

        if (anySucceeded) {
            _lastSyncTimestampMs.value = System.currentTimeMillis()
        }
        return anySucceeded
    }

    private fun getBatteryInfo(context: Context): Pair<Int, Boolean> {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
            val batteryPct = if (scale > 0) (level * 100 / scale) else 100
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            Pair(batteryPct, isCharging)
        } catch (_: Exception) {
            Pair(100, false)
        }
    }
}
