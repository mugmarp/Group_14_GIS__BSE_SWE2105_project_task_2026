package com.geotrace.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geotrace.LocationTrackerApp
import com.geotrace.model.Group
import com.geotrace.model.LocationPoint
import com.geotrace.model.MemberLocation
import com.geotrace.model.P2PPeer
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "GroupViewModel"

class GroupViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as LocationTrackerApp).groupRepository
    private val locationRepository = (application as LocationTrackerApp).repository
    private val auth: FirebaseAuth = Firebase.auth

    private val _currentUser = MutableStateFlow<FirebaseUser?>(auth.currentUser)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val authListener = FirebaseAuth.AuthStateListener { fbAuth ->
        _currentUser.value = fbAuth.currentUser
    }

    val userGroups: StateFlow<List<Group>> = repository.observeUserGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeGroup = MutableStateFlow<Group?>(null)
    val activeGroup: StateFlow<Group?> = _activeGroup.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val groupMembers: StateFlow<List<MemberLocation>> = _activeGroup
        .flatMapLatest { group ->
            if (group != null) {
                repository.observeGroupLocations(group.id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val nearbyPeers: StateFlow<List<P2PPeer>> = repository.nearbyPeers

    private val _isGhostMode = MutableStateFlow(false)
    val isGhostMode: StateFlow<Boolean> = _isGhostMode.asStateFlow()

    private val _isP2PEnabled = MutableStateFlow(false)
    val isP2PEnabled: StateFlow<Boolean> = _isP2PEnabled.asStateFlow()

    private val _isActionLoading = MutableStateFlow(false)
    val isActionLoading: StateFlow<Boolean> = _isActionLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var broadcastJob: Job? = null
    private var lastBroadcastTimestamp = 0L

    init {
        auth.addAuthStateListener(authListener)
        startBroadcastSyncLoop()
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
            _statusMessage.value = "Active Circle: ${group.name}"
            // Immediately broadcast first fix
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
            val displayName = repository.getCurrentUserDisplayName()
            val result = repository.createGroup(trimmed, displayName)
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
            _errorMessage.value = "Please enter a valid invite code (e.g. FAM-7K2)."
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

    fun toggleGhostMode() {
        val newMode = !_isGhostMode.value
        _isGhostMode.value = newMode
        _statusMessage.value = if (newMode) "Ghost Mode ON: Location hidden from group" else "Ghost Mode OFF: Sharing location"
        triggerImmediateBroadcast()
    }

    fun toggleP2PMesh() {
        val newEnabled = !_isP2PEnabled.value
        _isP2PEnabled.value = newEnabled
        if (newEnabled) {
            repository.startP2PMesh(repository.getCurrentUserDisplayName())
            _statusMessage.value = "P2P Wilderness Mesh ON: Discovering nearby devices"
        } else {
            repository.stopP2PMesh()
            _statusMessage.value = "P2P Wilderness Mesh OFF"
        }
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
                // Adaptive sync interval: 10s if moving, 30s if stationary
                val loc = locationRepository.currentLocation.value
                val isMoving = (loc?.speed ?: 0f) > 0.5f
                val intervalMs = if (isMoving) 10_000L else 30_000L

                val group = _activeGroup.value
                if (group != null && loc != null) {
                    performBroadcast(group, loc)
                }

                delay(intervalMs)
            }
        }
    }

    private fun triggerImmediateBroadcast() {
        val group = _activeGroup.value ?: return
        val loc = locationRepository.currentLocation.value ?: return
        viewModelScope.launch {
            performBroadcast(group, loc)
        }
    }

    private suspend fun performBroadcast(group: Group, loc: LocationPoint) {
        val context = getApplication<Application>()
        val (batteryLevel, isCharging) = getBatteryInfo(context)
        val mode = locationRepository.trackingMode.value.name
        val displayName = repository.getCurrentUserDisplayName()

        // Distinct deterministic avatar color based on userId hash
        val colorPalette = listOf("#2563EB", "#059669", "#D97706", "#DC2626", "#7C3AED", "#DB2777", "#0891B2")
        val avatarColor = colorPalette[Math.abs((auth.currentUser?.uid ?: "").hashCode()) % colorPalette.size]

        repository.publishLocation(
            groupId = group.id,
            location = loc,
            displayName = displayName,
            avatarColor = avatarColor,
            batteryPercent = batteryLevel,
            isCharging = isCharging,
            trackingMode = mode,
            isSharingEnabled = !_isGhostMode.value
        )
        lastBroadcastTimestamp = System.currentTimeMillis()
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
        } catch (e: Exception) {
            Pair(100, false)
        }
    }
}
