package com.geotrace.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotrace.LocationTrackerApp
import com.geotrace.data.P2PRingSendResult
import com.geotrace.model.Group
import com.geotrace.model.LocationPoint
import com.geotrace.model.MapFocusTarget
import com.geotrace.model.MemberLocation
import com.geotrace.model.P2PMeshState
import com.geotrace.model.P2PPeer
import com.geotrace.model.PersonalDevice
import com.geotrace.service.FindDeviceAlarmManager
import com.geotrace.service.LocationForegroundService
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class GroupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as LocationTrackerApp).groupRepository
    private val locationRepository = (application as LocationTrackerApp).repository
    private val auth: FirebaseAuth = Firebase.auth
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)

    val currentDeviceId: String = repository.currentDeviceId

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { fbAuth ->
        val prevUser = _currentUser.value
        val nextUser = fbAuth.currentUser
        _currentUser.value = nextUser
        if (nextUser != null) {
            repository.ensurePersonalDevicesListenerActive()
            ensureProtectionStartedIfEligible(refreshNotification = true)
            if (prevUser == null || prevUser.uid != nextUser.uid) {
                if (locationRepository.currentLocation.value == null) {
                    seedLocationIfMissingAndBroadcast()
                } else {
                    triggerImmediateBroadcast()
                }
            }
        } else {
            if (prevUser != null) {
                _activeGroup.value = null
                _mapFocusTarget.value = null
                repository.clearSyncStateOnSignOut()
            }
            if (locationRepository.activeSession.value == null) {
                LocationForegroundService.stopProtectionMode(
                    getApplication(),
                    disablePreference = false
                )
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val userGroups: StateFlow<List<Group>> = _currentUser
        .map { it?.uid }
        .distinctUntilChanged()
        .flatMapLatest { uid ->
            if (uid != null) {
                repository.observeUserGroups()
                    .retryWhen { cause, attempt ->
                        if (cause is CancellationException) {
                            false
                        } else if (attempt < 4 && auth.currentUser != null) {
                            delay(2_000L * (attempt + 1))
                            true
                        } else {
                            false
                        }
                    }
                    .catch { e ->
                        _errorMessage.value = e.localizedMessage ?: "Failed to sync tracking groups."
                        emit(emptyList())
                    }
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val personalDevices: StateFlow<List<PersonalDevice>> = repository.personalDevices

    private val _activeGroup = MutableStateFlow<Group?>(null)
    val activeGroup: StateFlow<Group?> = _activeGroup.asStateFlow()

    private val groupMembersRefreshTick = MutableStateFlow(0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val groupMembers: StateFlow<List<MemberLocation>> = combine(
        _activeGroup.map { it?.id }.distinctUntilChanged(),
        groupMembersRefreshTick
    ) { groupId, _ -> groupId }
        .flatMapLatest { groupId ->
            if (!groupId.isNullOrBlank()) {
                repository.observeGroupLocations(groupId)
                    .retryWhen { cause, attempt ->
                        if (cause is CancellationException) {
                            false
                        } else if (attempt < 4 && auth.currentUser != null) {
                            delay(2_000L * (attempt + 1))
                            true
                        } else {
                            false
                        }
                    }
                    .catch { e ->
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
    val p2pRingCooldownsMs: StateFlow<Map<String, Long>> = repository.p2pRingCooldownsMs
    val offlineCallsign: StateFlow<String> = repository.offlineCallsign

    val isRingingAlarm: StateFlow<Boolean> = FindDeviceAlarmManager.isRinging
    val ringCallerName: StateFlow<String?> = FindDeviceAlarmManager.ringCallerName

    val isGhostMode: StateFlow<Boolean> = repository.isGhostMode
    val isBackgroundProtectionEnabled: StateFlow<Boolean> = repository.isBackgroundProtectionEnabled
    val isProtectionServiceRunning: StateFlow<Boolean> = LocationForegroundService.isProtectionRunningFlow
    val isProtectionPaused: StateFlow<Boolean> = LocationForegroundService.isProtectionPausedFlow

    private val _isP2PEnabled = MutableStateFlow(false)
    val isP2PEnabled: StateFlow<Boolean> = _isP2PEnabled.asStateFlow()

    private val _isActionLoading = MutableStateFlow(false)
    val isActionLoading: StateFlow<Boolean> = _isActionLoading.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    val lastSyncTimestampMs: StateFlow<Long?> = repository.lastSyncTimestampMs

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
        ensureProtectionStartedIfEligible(refreshNotification = false)
        repository.onPeerConnectedCallback = { _ ->
            if (!isGhostMode.value) {
                val loc = locationRepository.currentLocation.value
                if (loc != null) {
                    triggerImmediateBroadcast()
                } else {
                    pushP2PPresenceWithoutFix()
                    seedLocationIfMissingAndBroadcast()
                }
            }
        }
        startBroadcastSyncLoop()
        observeLocationAndGroupForCloudSync()
        observeLocationForP2P()
        viewModelScope.launch {
            userGroups.collect { groups ->
                val savedId = repository.getSelectedGroupId()
                val current = _activeGroup.value
                val nextGroup = when {
                    current != null -> groups.firstOrNull { it.id == current.id }
                        ?: savedId?.let { id -> groups.firstOrNull { it.id == id } }
                        ?: groups.firstOrNull()
                    savedId != null -> groups.firstOrNull { it.id == savedId } ?: groups.firstOrNull()
                    else -> groups.firstOrNull()
                }
                _activeGroup.value = nextGroup
                if (nextGroup != null) {
                    repository.saveSelectedGroupId(nextGroup.id)
                } else if (current != null && _currentUser.value != null) {
                    repository.saveSelectedGroupId(null)
                }
                if (current == null && nextGroup != null) {
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

    private fun observeLocationAndGroupForCloudSync() {
        viewModelScope.launch {
            combine(
                _currentUser.map { it?.uid }.distinctUntilChanged(),
                _activeGroup.map { it?.id }.distinctUntilChanged(),
                locationRepository.currentLocation,
                isGhostMode
            ) { uid, groupId, loc, ghost ->
                SyncTriggerSnapshot(uid, groupId, loc, ghost)
            }.collect { snapshot ->
                if (snapshot.uid == null || snapshot.loc == null) return@collect
                val now = System.currentTimeMillis()
                val elapsedGroup = now - (repository.lastGroupSyncTimestampMs.value ?: 0L)
                val elapsedFleet = now - (repository.lastSyncTimestampMs.value ?: 0L)
                val shouldPublishGroup = !snapshot.groupId.isNullOrBlank() && elapsedGroup >= 4_000L
                val shouldPublishFleet = elapsedFleet >= 9_000L
                if (shouldPublishGroup || shouldPublishFleet) {
                    performBroadcast(_activeGroup.value, snapshot.loc)
                }
            }
        }
    }

    private data class SyncTriggerSnapshot(
        val uid: String?,
        val groupId: String?,
        val loc: LocationPoint?,
        val ghost: Boolean
    )

    private fun observeLocationForP2P() {
        viewModelScope.launch {
            locationRepository.currentLocation.collect { loc ->
                if (loc != null && _isP2PEnabled.value && !isGhostMode.value) {
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
        repository.saveSelectedGroupId(group?.id)
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
                repository.saveSelectedGroupId(created.id)
                _statusMessage.value = "Group '${created.name}' created! Share code: ${created.inviteCode}"
                if (locationRepository.currentLocation.value != null) {
                    triggerImmediateBroadcast()
                } else {
                    seedLocationIfMissingAndBroadcast()
                }
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
                repository.saveSelectedGroupId(joined.id)
                _statusMessage.value = "Joined group '${joined.name}'!"
                if (locationRepository.currentLocation.value != null) {
                    triggerImmediateBroadcast()
                } else {
                    seedLocationIfMissingAndBroadcast()
                }
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to join group."
            }
        }
    }

    fun leaveActiveGroup() {
        val group = _activeGroup.value ?: return
        leaveGroup(group)
    }

    fun leaveGroup(group: Group) {
        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.leaveGroup(group.id)
            _isActionLoading.value = false

            result.onSuccess {
                val focusId = _mapFocusTarget.value?.id
                val wasActive = _activeGroup.value?.id == group.id
                if (focusId != null && (focusId in group.memberIds || (wasActive && groupMembers.value.any { it.id == focusId || it.userId == focusId || it.deviceId == focusId }))) {
                    _mapFocusTarget.value = null
                }
                if (wasActive) {
                    _activeGroup.value = null
                    repository.saveSelectedGroupId(null)
                }
                val wasSole = group.memberIds.size <= 1
                _statusMessage.value = if (wasSole) {
                    "Deleted '${group.name}' (you were the last member)."
                } else {
                    "Left '${group.name}'."
                }
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to leave group."
            }
        }
    }

    fun deleteActiveGroup() {
        val group = _activeGroup.value ?: return
        deleteGroup(group)
    }

    fun deleteGroup(group: Group) {
        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.deleteGroup(group.id)
            _isActionLoading.value = false

            result.onSuccess {
                val focusId = _mapFocusTarget.value?.id
                val wasActive = _activeGroup.value?.id == group.id
                if (focusId != null && (focusId in group.memberIds || (wasActive && groupMembers.value.any { it.id == focusId || it.userId == focusId || it.deviceId == focusId }))) {
                    _mapFocusTarget.value = null
                }
                if (wasActive) {
                    _activeGroup.value = null
                    repository.saveSelectedGroupId(null)
                }
                _statusMessage.value = "Deleted group '${group.name}'."
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to delete group."
            }
        }
    }

    fun removeGroupMemberDevice(member: MemberLocation) {
        val group = _activeGroup.value ?: return
        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.removeGroupMemberDevice(group.id, member)
            _isActionLoading.value = false

            result.onSuccess {
                val focusId = _mapFocusTarget.value?.id
                if (focusId != null && (focusId == member.id || focusId == member.userId || focusId == member.deviceId)) {
                    _mapFocusTarget.value = null
                }
                _statusMessage.value = "Removed '${member.displayName}' from '${group.name}'."
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to remove device from group."
            }
        }
    }

    fun toggleGhostMode() {
        val newMode = !isGhostMode.value
        repository.setGhostMode(newMode)
        repository.onP2PGhostModeChanged(newMode)
        _statusMessage.value = if (newMode) {
            "Ghost Mode ON: Hidden from groups & P2P peers"
        } else {
            "Ghost Mode OFF: Live sharing resumed"
        }
        if (newMode) {
            val activeIds = (userGroups.value.map { it.id } + repository.getKnownGroupIdsSnapshot() + listOfNotNull(_activeGroup.value?.id)).distinct()
            if (activeIds.isNotEmpty()) {
                viewModelScope.launch {
                    for (groupId in activeIds) {
                        repository.clearGroupPresence(groupId)
                    }
                }
            }
        } else if (_isP2PEnabled.value && locationRepository.currentLocation.value == null) {
            pushP2PPresenceWithoutFix()
            seedLocationIfMissingAndBroadcast()
        }
        ensureProtectionStartedIfEligible(refreshNotification = true)
        triggerImmediateBroadcast()
    }

    fun toggleBackgroundProtection(enabled: Boolean) {
        val context = getApplication<Application>()
        repository.setBackgroundProtectionEnabled(enabled)
        if (enabled) {
            if (auth.currentUser != null) {
                val started = LocationForegroundService.startProtectionMode(context, forceResume = true)
                val hasNotif = LocationForegroundService.hasNotificationPermission(context)
                _statusMessage.value = when {
                    !started -> "24/7 Protection saved — grant Location permission to start background listener."
                    !hasNotif -> "24/7 Protection active — allow Notifications in Settings to keep status bar badge visible."
                    else -> "24/7 Protection ON: Persistent status bar indicator & background sync active."
                }
            } else {
                _statusMessage.value = "24/7 Protection enabled — sign in with Google to activate."
            }
        } else {
            LocationForegroundService.stopProtectionMode(context, disablePreference = true)
            _statusMessage.value = "24/7 Protection OFF: Background sync & Remote Ring paused when closed."
        }
    }

    fun pauseBackgroundProtection() {
        val context = getApplication<Application>()
        LocationForegroundService.pauseProtectionMode(context)
        _statusMessage.value = "24/7 Protection paused — tap Resume anytime to continue background sync."
    }

    fun resumeBackgroundProtection() {
        val context = getApplication<Application>()
        if (auth.currentUser == null) {
            _statusMessage.value = "Sign in with Google to resume 24/7 Protection."
            return
        }
        if (!repository.isBackgroundProtectionEnabled.value) {
            repository.setBackgroundProtectionEnabled(true)
        }
        val started = LocationForegroundService.resumeProtectionMode(context)
        val hasNotif = LocationForegroundService.hasNotificationPermission(context)
        _statusMessage.value = when {
            !started -> "Grant Location permission to resume 24/7 Protection."
            !hasNotif -> "24/7 Protection resumed — allow Notifications to keep status badge visible."
            else -> "24/7 Protection resumed."
        }
    }

    fun ensureProtectionStartedIfEligible(refreshNotification: Boolean = false) {
        val context = getApplication<Application>()
        val activeSess = locationRepository.activeSession.value
        if (activeSess != null) {
            if (!LocationForegroundService.isServiceActive && LocationForegroundService.hasLocationPermission(context)) {
                if (activeSess.isPaused) {
                    LocationForegroundService.startServicePaused(context, activeSess.trackingMode)
                } else {
                    LocationForegroundService.startService(context, activeSess.trackingMode)
                }
            } else if (refreshNotification) {
                LocationForegroundService.refreshProtectionNotification(context)
            }
            return
        }
        if (repository.isBackgroundProtectionActiveAndSignedIn() && LocationForegroundService.hasLocationPermission(context)) {
            if (LocationForegroundService.isServiceActive) {
                if (refreshNotification) {
                    LocationForegroundService.refreshProtectionNotification(context)
                }
            } else {
                LocationForegroundService.startProtectionMode(context)
            }
        }
    }

    fun updateOfflineCallsign(callsign: String) {
        val cleaned = callsign.trim().take(24)
        if (cleaned.isBlank()) {
            _errorMessage.value = "Device name cannot be empty."
            return
        }
        repository.setOfflineCallsign(cleaned)
        _statusMessage.value = "Device name updated to '$cleaned'"
        viewModelScope.launch {
            repository.syncDeviceNameInCloud(_activeGroup.value?.id)
        }
        triggerImmediateBroadcast()
    }

    fun toggleP2PMesh() {
        val newEnabled = !_isP2PEnabled.value
        _isP2PEnabled.value = newEnabled
        if (newEnabled) {
            val myName = repository.getCurrentUserDisplayName()
            repository.startP2PMesh(myName)
            _statusMessage.value = if (isGhostMode.value) {
                "Off-Grid P2P Finder ON (Ghost Mode: coordinates hidden)"
            } else {
                "Off-Grid P2P Device Finder ON"
            }
            if (!isGhostMode.value) {
                if (locationRepository.currentLocation.value != null) {
                    triggerImmediateBroadcast()
                } else {
                    seedLocationIfMissingAndBroadcast()
                }
            }
        } else {
            val focusId = _mapFocusTarget.value?.id
            if (focusId != null && nearbyPeers.value.any { it.endpointId == focusId }) {
                _mapFocusTarget.value = null
            }
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
                val focusId = _mapFocusTarget.value?.id
                val devId = device.deviceId.ifBlank { device.id }
                if (focusId != null && (focusId == devId || focusId == device.deviceId || focusId == device.id)) {
                    _mapFocusTarget.value = null
                }
                _statusMessage.value = "Removed '${device.deviceName.ifBlank { device.deviceModel }}' from My Devices."
            }.onFailure { e ->
                _errorMessage.value = e.localizedMessage ?: "Failed to remove device."
            }
        }
    }

    fun syncNow() {
        groupMembersRefreshTick.value += 1
        val group = _activeGroup.value
        val p2pActive = _isP2PEnabled.value
        val isSignedIn = auth.currentUser != null
        if (group == null && !p2pActive && !isSignedIn) {
            _errorMessage.value = "Sign in or enable Off-Grid P2P to broadcast your location."
            return
        }
        val loc = locationRepository.currentLocation.value
        if (loc == null) {
            seedLocationIfMissingAndBroadcast(forceUiFeedback = true)
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
        when (repository.sendP2PRingCommand(endpointId)) {
            P2PRingSendResult.SENT ->
                _statusMessage.value = "Offline P2P Ring sent to $peerName!"
            P2PRingSendResult.COOLDOWN_ACTIVE ->
                _statusMessage.value = "Ring already sent to $peerName — please wait a few seconds."
            P2PRingSendResult.NOT_CONNECTED ->
                _errorMessage.value = "$peerName is not connected. Tap Rescan Nearby P2P Mesh."
            P2PRingSendResult.FAILED ->
                _errorMessage.value = "Failed to send P2P ring to $peerName."
        }
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
                val elapsedSinceLastSync = System.currentTimeMillis() - (repository.lastSyncTimestampMs.value ?: 0L)
                if (loc != null && (group != null || p2pActive || isSignedIn) && elapsedSinceLastSync >= 9_000L) {
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

    @android.annotation.SuppressLint("MissingPermission")
    private fun seedLocationIfMissingAndBroadcast(forceUiFeedback: Boolean = false) {
        val context = getApplication<Application>()
        if (!LocationForegroundService.hasLocationPermission(context)) {
            if (forceUiFeedback) {
                _errorMessage.value = "Grant Location permission to sync your device position."
            }
            return
        }
        if (forceUiFeedback) {
            _isSyncing.value = true
        }
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                if (lastLoc != null) {
                    applySeededLocation(lastLoc, forceUiFeedback)
                } else {
                    val tokenSource = CancellationTokenSource()
                    fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                        .addOnSuccessListener { quickLoc ->
                            if (quickLoc != null) {
                                applySeededLocation(quickLoc, forceUiFeedback)
                            } else if (forceUiFeedback) {
                                _isSyncing.value = false
                                _errorMessage.value = "Waiting for GPS or Wi-Fi fix before syncing."
                            }
                        }
                        .addOnFailureListener {
                            if (forceUiFeedback) {
                                _isSyncing.value = false
                                _errorMessage.value = "Waiting for GPS or cellular fix before syncing."
                            }
                        }
                }
            }.addOnFailureListener {
                if (forceUiFeedback) {
                    _isSyncing.value = false
                    _errorMessage.value = "Waiting for GPS or cellular fix before syncing."
                }
            }
        } catch (_: SecurityException) {
            if (forceUiFeedback) {
                _isSyncing.value = false
                _errorMessage.value = "Location permission required to sync."
            }
        } catch (_: Exception) {
            if (forceUiFeedback) {
                _isSyncing.value = false
            }
        }
    }

    private fun applySeededLocation(loc: android.location.Location, showToast: Boolean) {
        val normalizedTime = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        val point = LocationPoint(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracy = loc.accuracy,
            speed = if (loc.hasSpeed()) loc.speed else 0f,
            altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
            bearing = if (loc.hasBearing()) loc.bearing else 0f,
            provider = loc.provider ?: "fused",
            timestamp = normalizedTime,
            sessionId = locationRepository.activeSession.value?.id ?: "group_seed"
        )
        viewModelScope.launch {
            locationRepository.recordLocation(point)
            val ok = performBroadcast(_activeGroup.value, point)
            if (showToast) {
                _isSyncing.value = false
                if (ok) {
                    _statusMessage.value = "Live telemetry synced."
                } else {
                    _errorMessage.value = "Sync incomplete — check network connection."
                }
            }
        }
    }

    private fun pushP2PPresenceWithoutFix() {
        if (!_isP2PEnabled.value || isGhostMode.value) return
        val context = getApplication<Application>()
        val (batteryLevel, _) = getBatteryInfo(context)
        val displayName = repository.getCurrentUserDisplayName()
        repository.sendP2PPresenceWithoutFix(displayName, batteryLevel)
    }

    private suspend fun performBroadcast(group: Group?, loc: LocationPoint): Boolean {
        val context = getApplication<Application>()
        val (batteryLevel, isCharging) = getBatteryInfo(context)
        val mode = locationRepository.trackingMode.value.name
        val displayName = repository.getCurrentUserDisplayName()
        var anySucceeded = false

        // Always push over off-grid P2P first so offline Firestore awaits never delay or block nearby peers
        if (_isP2PEnabled.value && !isGhostMode.value) {
            repository.broadcastP2PLocationDirect(loc, displayName, batteryLevel)
            lastP2PLocationPushMs = System.currentTimeMillis()
            anySucceeded = true
        }

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

        val targetGroupIds = (userGroups.value.map { it.id } + repository.getKnownGroupIdsSnapshot() + listOfNotNull(group?.id)).distinct()
        if (targetGroupIds.isNotEmpty() && auth.currentUser != null) {
            val colorPalette = listOf("#2563EB", "#059669", "#D97706", "#DC2626", "#7C3AED", "#DB2777", "#0891B2")
            val avatarColor = colorPalette[((auth.currentUser?.uid ?: "").hashCode() and 0x7fffffff) % colorPalette.size]
            for (groupId in targetGroupIds) {
                val groupRes = repository.publishLocation(
                    groupId = groupId,
                    location = loc,
                    displayName = displayName,
                    avatarColor = avatarColor,
                    batteryPercent = batteryLevel,
                    isCharging = isCharging,
                    trackingMode = mode,
                    isSharingEnabled = !isGhostMode.value
                )
                if (groupRes.isSuccess) anySucceeded = true
            }
        }

        return anySucceeded
    }

    private fun getBatteryInfo(context: Context): Pair<Int, Boolean> {
        return try {
            val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) {
                (level * 100 / scale).coerceIn(0, 100)
            } else {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val cap = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                if (cap in 0..100) cap else 100
            }
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            Pair(batteryPct, isCharging)
        } catch (_: Exception) {
            Pair(100, false)
        }
    }
}
