package com.geotrace.data

import android.content.Context
import android.util.Log
import com.geotrace.R
import com.geotrace.model.Group
import com.geotrace.model.LocationPoint
import com.geotrace.model.MemberLocation
import com.geotrace.model.P2PMeshState
import com.geotrace.model.P2PPeer
import com.geotrace.model.PersonalDevice
import com.geotrace.service.FindDeviceAlarmManager
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "GroupRepository"
private const val NEARBY_SERVICE_ID = "com.geotrace.p2p.mesh"
private const val P2P_PAYLOAD_VERSION_V2: Byte = 2
private const val P2P_PAYLOAD_VERSION_V3: Byte = 3
private const val P2P_CMD_RING: Byte = 10

class GroupRepository(
    private val context: Context,
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        context.applicationContext,
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth = Firebase.auth
    private val prefs = context.getSharedPreferences("geotrace_prefs", Context.MODE_PRIVATE)
    private val p2pScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    val currentDeviceId: String = loadOrInitDeviceId()
    val bleTag: String = currentDeviceId
        .filter { it.isLetterOrDigit() }
        .take(6)
        .uppercase()
        .padEnd(6, '0')

    // Nearby P2P Peers & Connection Pipeline State
    private val _nearbyPeers = MutableStateFlow<List<P2PPeer>>(emptyList())
    val nearbyPeers = _nearbyPeers.asStateFlow()

    private val _p2pMeshState = MutableStateFlow(P2PMeshState())
    val p2pMeshState = _p2pMeshState.asStateFlow()

    private val _offlineCallsign = MutableStateFlow(loadOrInitOfflineCallsign())
    val offlineCallsign = _offlineCallsign.asStateFlow()

    private val _firestoreError = MutableStateFlow<String?>(null)
    val firestoreError = _firestoreError.asStateFlow()

    private val _isBackgroundProtectionEnabled = MutableStateFlow(
        prefs.getBoolean("background_protection_enabled", true)
    )
    val isBackgroundProtectionEnabled = _isBackgroundProtectionEnabled.asStateFlow()

    private val _isGhostMode = MutableStateFlow(
        prefs.getBoolean("is_ghost_mode", false)
    )
    val isGhostMode = _isGhostMode.asStateFlow()

    private val _personalDevices = MutableStateFlow<List<PersonalDevice>>(emptyList())
    val personalDevices = _personalDevices.asStateFlow()

    private val _lastSyncTimestampMs = MutableStateFlow<Long?>(null)
    val lastSyncTimestampMs = _lastSyncTimestampMs.asStateFlow()

    fun clearFirestoreError() {
        _firestoreError.value = null
    }

    fun setBackgroundProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("background_protection_enabled", enabled).apply()
        _isBackgroundProtectionEnabled.value = enabled
    }

    fun setGhostMode(enabled: Boolean) {
        prefs.edit().putBoolean("is_ghost_mode", enabled).apply()
        _isGhostMode.value = enabled
    }

    fun getSelectedGroupId(): String? = prefs.getString("selected_group_id", null)

    fun saveSelectedGroupId(groupId: String?) {
        prefs.edit().putString("selected_group_id", groupId).apply()
    }

    fun isBackgroundProtectionActiveAndSignedIn(): Boolean {
        return _isBackgroundProtectionEnabled.value && auth.currentUser != null
    }

    private fun isValidCoordinate(lat: Double, lng: Double): Boolean {
        return lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
    }

    private val discoveredEndpoints = ConcurrentHashMap<String, String>()
    private val pendingConnections = ConcurrentHashMap.newKeySet<String>()
    private val connectedEndpoints = ConcurrentHashMap<String, String>()
    private val activePeers = ConcurrentHashMap<String, P2PPeer>()

    @Volatile
    private var isP2PMeshRequested = false
    @Volatile
    private var isP2PAdvertising = false
    @Volatile
    private var isP2PDiscovering = false
    @Volatile
    private var lastP2PError: String? = null
    @Volatile
    private var lastHandledRingTimestampMs: Long = prefs.getLong("last_handled_ring_ms", 0L)

    private var personalDevicesListener: ListenerRegistration? = null
    private var personalDevicesListenerUid: String? = null
    private val personalDevicesListenerLock = Any()

    private val repoAuthListener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { fbAuth ->
        val uid = fbAuth.currentUser?.uid
        if (uid != null) {
            ensurePersonalDevicesListenerActive()
        } else {
            stopPersonalDevicesListener()
            _personalDevices.value = emptyList()
        }
    }

    var onPeerConnectedCallback: ((String) -> Unit)? = null

    init {
        FindDeviceAlarmManager.onAlarmStoppedByUser = {
            p2pScope.launch {
                clearRingCommandOnSelf()
            }
        }
        auth.addAuthStateListener(repoAuthListener)
        if (auth.currentUser != null) {
            ensurePersonalDevicesListenerActive()
        }
    }

    private fun loadOrInitDeviceId(): String {
        val saved = prefs.getString("geotrace_device_id", null)
        if (!saved.isNullOrBlank()) return saved
        val modelPrefix = android.os.Build.MODEL
            .filter { it.isLetterOrDigit() }
            .take(6)
            .lowercase()
            .ifBlank { "dev" }
        val generated = "${modelPrefix}_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        prefs.edit().putString("geotrace_device_id", generated).apply()
        return generated
    }

    private fun loadOrInitOfflineCallsign(): String {
        val saved = prefs.getString("offline_p2p_callsign", null)
        if (!saved.isNullOrBlank()) return saved
        val modelTag = android.os.Build.MODEL
            .filter { it.isLetterOrDigit() }
            .take(6)
            .ifBlank { "Phone" }
        val suffix = currentDeviceId.takeLast(4).uppercase()
        val generated = "$modelTag-$suffix"
        prefs.edit().putString("offline_p2p_callsign", generated).apply()
        return generated
    }

    fun setOfflineCallsign(callsign: String) {
        val cleaned = callsign.trim().take(24)
        if (cleaned.isNotBlank()) {
            prefs.edit().putString("offline_p2p_callsign", cleaned).apply()
            _offlineCallsign.value = cleaned
            if (isP2PMeshRequested) {
                stopP2PMesh()
                startP2PMesh(cleaned)
            }
        }
    }

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun getCurrentUserDisplayName(): String {
        val customDevice = _offlineCallsign.value.takeIf { it.isNotBlank() }
        val accountName = auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email?.substringBefore("@")?.takeIf { it.isNotBlank() }
        val raw = when {
            !customDevice.isNullOrBlank() -> customDevice
            !accountName.isNullOrBlank() -> accountName
            else -> "GeoTrace Device"
        }
        return raw.take(50)
    }

    /**
     * Create a new tracking group with a unique short invite code.
     */
    suspend fun createGroup(name: String): Result<Group> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in to create a group."))

        return try {
            val inviteCode = generateInviteCode()
            val docRef = db.collection("groups").document()
            val groupData = mapOf(
                "name" to name.trim(),
                "inviteCode" to inviteCode,
                "createdBy" to uid,
                "memberIds" to listOf(uid),
                "createdAt" to FieldValue.serverTimestamp()
            )
            docRef.set(groupData).await()
            val created = Group(
                id = docRef.id,
                name = name.trim(),
                inviteCode = inviteCode,
                createdBy = uid,
                memberIds = listOf(uid)
            )
            Result.success(created)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create group: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Join an existing tracking group via its invite code.
     */
    suspend fun joinGroupByCode(inviteCode: String): Result<Group> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in to join a group."))

        val cleanCode = inviteCode.trim().uppercase()
        return try {
            val querySnapshot = db.collection("groups")
                .whereEqualTo("inviteCode", cleanCode)
                .limit(1)
                .get()
                .await()

            if (querySnapshot.isEmpty) {
                return Result.failure(IllegalArgumentException("No group found with invite code '$cleanCode'."))
            }

            val doc = querySnapshot.documents.first()
            val group = doc.toObject(Group::class.java)?.copy(id = doc.id)
                ?: return Result.failure(IllegalStateException("Failed to parse group data."))

            if (!group.memberIds.contains(uid)) {
                db.collection("groups").document(doc.id)
                    .update("memberIds", FieldValue.arrayUnion(uid))
                    .await()
            }
            Result.success(group)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join group by code: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Observe all tracking groups that the current user belongs to.
     */
    fun observeUserGroups(): Flow<List<Group>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener: ListenerRegistration = db.collection("groups")
            .whereArrayContains("memberIds", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing user groups: ${error.code} ${error.message}", error)
                    _firestoreError.value = "Cloud groups sync error (${error.code.name}): ${error.localizedMessage ?: "Check connection & Firestore rules"}"
                    trySend(emptyList())
                    close()
                    return@addSnapshotListener
                }
                val groups = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(Group::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping malformed group doc ${doc.id}: ${e.message}")
                        null
                    }
                } ?: emptyList()
                trySend(groups)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Leave a tracking group and delete this device's live location document.
     */
    suspend fun leaveGroup(groupId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in to leave a group."))

        return try {
            db.collection("groups").document(groupId)
                .update("memberIds", FieldValue.arrayRemove(uid))
                .await()
            try {
                val locCol = db.collection("groups").document(groupId).collection("live_locations")
                locCol.document("${uid}_${currentDeviceId}").delete().await()
                locCol.document(uid).delete().await()
            } catch (_: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to leave group: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Permanently delete a tracking group created by the signed-in user.
     */
    suspend fun deleteGroup(groupId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in to delete a group."))

        return try {
            try {
                val locCol = db.collection("groups").document(groupId).collection("live_locations")
                locCol.document("${uid}_${currentDeviceId}").delete().await()
                locCol.document(uid).delete().await()
            } catch (_: Exception) {}
            db.collection("groups").document(groupId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete group: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Publish current device's live location & telemetry to a group's live_locations collection
     * using `{uid}_{deviceId}` so multiple devices under one account never overwrite each other.
     */
    suspend fun publishLocation(
        groupId: String,
        location: LocationPoint,
        displayName: String,
        avatarColor: String,
        batteryPercent: Int,
        isCharging: Boolean,
        trackingMode: String,
        isSharingEnabled: Boolean
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User not signed in."))

        return try {
            val docId = "${uid}_${currentDeviceId}"
            val docRef = db.collection("groups").document(groupId)
                .collection("live_locations").document(docId)

            val payload = mapOf(
                "userId" to uid,
                "deviceId" to currentDeviceId,
                "deviceModel" to android.os.Build.MODEL,
                "bleTag" to bleTag,
                "displayName" to displayName,
                "avatarColor" to avatarColor,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracy" to location.accuracy,
                "speed" to location.speed,
                "bearing" to location.bearing,
                "altitude" to location.altitude,
                "gait" to location.gaitLabel,
                "batteryPercent" to batteryPercent,
                "isCharging" to isCharging,
                "trackingMode" to trackingMode,
                "isSharingEnabled" to isSharingEnabled,
                "lastUpdated" to FieldValue.serverTimestamp()
            )

            docRef.set(payload).await()
            _lastSyncTimestampMs.value = System.currentTimeMillis()

            if (isSharingEnabled && (isP2PAdvertising || isP2PDiscovering || connectedEndpoints.isNotEmpty())) {
                broadcastP2PLocation(location, displayName, batteryPercent)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish live location to group $groupId: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Real-time stream of all device locations within a specific group.
     */
    fun observeGroupLocations(groupId: String): Flow<List<MemberLocation>> = callbackFlow {
        val listener: ListenerRegistration = db.collection("groups").document(groupId)
            .collection("live_locations")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing group locations: ${error.code} ${error.message}", error)
                    _firestoreError.value = "Group location sync error (${error.code.name}): ${error.localizedMessage ?: "Unavailable"}"
                    trySend(emptyList())
                    close()
                    return@addSnapshotListener
                }
                val locations = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(MemberLocation::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping malformed member location doc ${doc.id}: ${e.message}")
                        null
                    }
                }?.filter { it.isSharingEnabled && isValidCoordinate(it.latitude, it.longitude) } ?: emptyList()

                trySend(locations)
            }

        awaitClose { listener.remove() }
    }

    // -----------------------------------------------------------------------------------------
    // Personal Multi-Device Fleet (`/users/{uid}/devices/{deviceId}`) + Remote Ring
    // -----------------------------------------------------------------------------------------

    suspend fun publishPersonalDevice(
        location: LocationPoint,
        batteryPercent: Int,
        isCharging: Boolean,
        trackingMode: String
    ): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("User not signed in."))

        return try {
            val docRef = db.collection("users").document(uid)
                .collection("devices").document(currentDeviceId)

            val payload = mapOf(
                "userId" to uid,
                "deviceId" to currentDeviceId,
                "deviceName" to _offlineCallsign.value,
                "deviceModel" to android.os.Build.MODEL,
                "bleTag" to bleTag,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracy" to location.accuracy,
                "speed" to location.speed,
                "bearing" to location.bearing,
                "batteryPercent" to batteryPercent,
                "isCharging" to isCharging,
                "trackingMode" to trackingMode,
                "lastUpdated" to FieldValue.serverTimestamp()
            )
            // Merge so another phone's pending ringRequestedAt trigger is preserved
            docRef.set(payload, SetOptions.merge()).await()
            _lastSyncTimestampMs.value = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update personal device doc: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Publishes device fleet location + active tracking group telemetry safely from either foreground UI
     * or 24/7 background protection / trail service.
     */
    suspend fun syncDeviceAndGroupTelemetry(
        location: LocationPoint,
        trackingModeName: String,
        explicitGroupId: String? = getSelectedGroupId()
    ): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val (batteryPercent, isCharging) = readBatteryInfo()
        val displayName = getCurrentUserDisplayName()
        var anySucceeded = false

        val fleetRes = publishPersonalDevice(
            location = location,
            batteryPercent = batteryPercent,
            isCharging = isCharging,
            trackingMode = trackingModeName
        )
        if (fleetRes.isSuccess) anySucceeded = true

        if (!explicitGroupId.isNullOrBlank()) {
            val colorPalette = listOf("#2563EB", "#059669", "#D97706", "#DC2626", "#7C3AED", "#DB2777", "#0891B2")
            val avatarColor = colorPalette[(uid.hashCode() and 0x7fffffff) % colorPalette.size]
            val groupRes = publishLocation(
                groupId = explicitGroupId,
                location = location,
                displayName = displayName,
                avatarColor = avatarColor,
                batteryPercent = batteryPercent,
                isCharging = isCharging,
                trackingMode = trackingModeName,
                isSharingEnabled = !_isGhostMode.value
            )
            if (groupRes.isSuccess) anySucceeded = true
        }
        return anySucceeded
    }

    fun readBatteryInfo(): Pair<Int, Boolean> {
        return try {
            val intentFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, intentFilter)
            val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: 100
            val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: 100
            val batteryPct = if (scale > 0) (level * 100 / scale) else 100
            val status = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == android.os.BatteryManager.BATTERY_STATUS_CHARGING ||
                status == android.os.BatteryManager.BATTERY_STATUS_FULL
            Pair(batteryPct, isCharging)
        } catch (_: Exception) {
            Pair(100, false)
        }
    }

    fun ensurePersonalDevicesListenerActive() {
        val uid = auth.currentUser?.uid ?: return
        synchronized(personalDevicesListenerLock) {
            if (personalDevicesListener != null && personalDevicesListenerUid == uid) return
            personalDevicesListener?.remove()
            personalDevicesListenerUid = uid
            personalDevicesListener = db.collection("users").document(uid)
                .collection("devices")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error observing personal devices: ${error.code} ${error.message}", error)
                        _firestoreError.value = "My Devices sync error (${error.code.name}): ${error.localizedMessage ?: "Check Firestore rules deployment"}"
                        return@addSnapshotListener
                    }

                    val devices = snapshot?.documents?.mapNotNull { doc ->
                        try {
                            doc.toObject(PersonalDevice::class.java)?.copy(id = doc.id)
                        } catch (e: Exception) {
                            Log.w(TAG, "Skipping malformed personal device doc ${doc.id}: ${e.message}")
                            null
                        }
                    }?.filter { isValidCoordinate(it.latitude, it.longitude) } ?: emptyList()

                    _personalDevices.value = devices

                    // Check if another one of our devices requested THIS device to ring
                    val selfDoc = devices.firstOrNull { it.deviceId == currentDeviceId || it.id == currentDeviceId }
                    if (selfDoc != null) {
                        val reqAt = selfDoc.ringRequestedAt
                        val ageMs = System.currentTimeMillis() - reqAt
                        if (reqAt > 0L && reqAt > lastHandledRingTimestampMs && ageMs in -120_000L..120_000L) {
                            lastHandledRingTimestampMs = reqAt
                            prefs.edit().putLong("last_handled_ring_ms", reqAt).apply()
                            val caller = selfDoc.ringRequestedBy.ifBlank { "Your GeoTrace Device" }
                            FindDeviceAlarmManager.startRinging(context, caller)
                        }
                    }
                }
        }
    }

    private fun stopPersonalDevicesListener() {
        synchronized(personalDevicesListenerLock) {
            personalDevicesListener?.remove()
            personalDevicesListener = null
            personalDevicesListenerUid = null
        }
    }

    suspend fun sendRingCommandToDevice(targetDeviceId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Sign in to ring your cloud devices."))

        return try {
            val nowMs = System.currentTimeMillis()
            db.collection("users").document(uid)
                .collection("devices").document(targetDeviceId)
                .update(
                    mapOf(
                        "ringRequestedAt" to nowMs,
                        "ringRequestedBy" to _offlineCallsign.value
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send remote ring command: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun clearRingCommandOnSelf() {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid)
                .collection("devices").document(currentDeviceId)
                .update(
                    mapOf(
                        "ringRequestedAt" to 0L,
                        "ringRequestedBy" to ""
                    )
                )
                .await()
        } catch (_: Exception) {}
    }

    suspend fun cancelRingCommandForDevice(targetDeviceId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Sign in to manage device ring alerts."))

        return try {
            db.collection("users").document(uid)
                .collection("devices").document(targetDeviceId)
                .update(
                    mapOf(
                        "ringRequestedAt" to 0L,
                        "ringRequestedBy" to ""
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel ring command: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun removePersonalDevice(targetDeviceId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Sign in to remove linked devices."))

        return try {
            db.collection("users").document(uid)
                .collection("devices").document(targetDeviceId)
                .delete()
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove personal device: ${e.message}", e)
            Result.failure(e)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Nearby Connections P2P Mesh (Off-Grid Location Exchange & Remote Ring)
    // -----------------------------------------------------------------------------------------

    fun startP2PMesh(displayName: String) {
        val myName = displayName.ifBlank { getCurrentUserDisplayName() }
        isP2PMeshRequested = true
        lastP2PError = null
        updateMeshState()
        startAdvertising(myName)
        startDiscovery(myName)
    }

    fun rescanP2PMesh(displayName: String) {
        val myName = displayName.ifBlank { getCurrentUserDisplayName() }
        stopP2PMesh()
        startP2PMesh(myName)
    }

    fun stopP2PMesh() {
        isP2PMeshRequested = false
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping P2P mesh: ${e.message}")
        } finally {
            isP2PAdvertising = false
            isP2PDiscovering = false
            lastP2PError = null
            discoveredEndpoints.clear()
            pendingConnections.clear()
            connectedEndpoints.clear()
            activePeers.clear()
            _nearbyPeers.value = emptyList()
            updateMeshState()
        }
    }

    private fun updateMeshState() {
        val discCount = discoveredEndpoints.size
        val connCount = connectedEndpoints.size
        val peerCount = activePeers.size

        val statusMsg = when {
            !isP2PMeshRequested -> "Off-grid P2P scanner idle"
            lastP2PError != null -> lastP2PError!!
            peerCount > 0 -> "Receiving live telemetry from $peerCount offline peer(s)"
            connCount > 0 -> "Connected to $connCount peer(s); exchanging coordinates..."
            discCount > 0 -> "Found $discCount device(s) nearby; establishing encrypted P2P link..."
            isP2PAdvertising && isP2PDiscovering -> "Broadcasting & scanning via BLE / Wi-Fi Direct (50-200m)"
            isP2PAdvertising || isP2PDiscovering -> "Initializing Nearby radios..."
            else -> "Starting Nearby Connections radios..."
        }

        _p2pMeshState.value = P2PMeshState(
            isActive = isP2PMeshRequested,
            isAdvertising = isP2PAdvertising,
            isDiscovering = isP2PDiscovering,
            discoveredCount = discCount,
            connectedCount = connCount,
            discoveredNames = discoveredEndpoints.values.toList(),
            statusText = statusMsg,
            errorText = lastP2PError
        )
    }

    private fun startAdvertising(displayName: String) {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            displayName,
            NEARBY_SERVICE_ID,
            createLifecycleCallback(),
            advertisingOptions
        ).addOnSuccessListener {
            isP2PAdvertising = true
            lastP2PError = null
            updateMeshState()
            Log.i(TAG, "Nearby advertising started as '$displayName'")
        }.addOnFailureListener { ex ->
            val code = (ex as? ApiException)?.statusCode
            if (code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                isP2PAdvertising = true
                lastP2PError = null
            } else {
                isP2PAdvertising = false
                lastP2PError = mapNearbyError("Advertise", ex)
            }
            updateMeshState()
            Log.w(TAG, "Nearby advertising result: code=$code, err=$lastP2PError")
        }
    }

    private fun startDiscovery(myDisplayName: String) {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            NEARBY_SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    if (!isP2PMeshRequested) return
                    val remoteName = info.endpointName.ifBlank { "GeoPeer" }
                    discoveredEndpoints[endpointId] = remoteName
                    updateMeshState()

                    if (connectedEndpoints.containsKey(endpointId) || !pendingConnections.add(endpointId)) {
                        return
                    }

                    val delayMs = if (myDisplayName <= remoteName) 60L else 1100L
                    p2pScope.launch {
                        delay(delayMs)
                        if (!isP2PMeshRequested || connectedEndpoints.containsKey(endpointId)) {
                            pendingConnections.remove(endpointId)
                            return@launch
                        }
                        connectionsClient.requestConnection(
                            myDisplayName,
                            endpointId,
                            createLifecycleCallback(remoteName)
                        ).addOnFailureListener { ex ->
                            pendingConnections.remove(endpointId)
                            Log.w(TAG, "requestConnection to $endpointId failed: ${ex.message}")
                        }
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    discoveredEndpoints.remove(endpointId)
                    pendingConnections.remove(endpointId)
                    if (!connectedEndpoints.containsKey(endpointId)) {
                        activePeers.remove(endpointId)
                        _nearbyPeers.value = activePeers.values.toList()
                    }
                    updateMeshState()
                }
            },
            discoveryOptions
        ).addOnSuccessListener {
            isP2PDiscovering = true
            lastP2PError = null
            updateMeshState()
        }.addOnFailureListener { ex ->
            val code = (ex as? ApiException)?.statusCode
            if (code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                isP2PDiscovering = true
                lastP2PError = null
            } else {
                isP2PDiscovering = false
                lastP2PError = mapNearbyError("Discovery", ex)
            }
            updateMeshState()
        }
    }

    private fun createLifecycleCallback(knownName: String? = null): ConnectionLifecycleCallback {
        return object : ConnectionLifecycleCallback() {
            private var endpointDisplayName: String = knownName ?: "GeoPeer"

            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                endpointDisplayName = connectionInfo.endpointName.ifBlank { endpointDisplayName }
                discoveredEndpoints[endpointId] = endpointDisplayName
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                updateMeshState()
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                pendingConnections.remove(endpointId)
                val statusCode = result.status.statusCode
                if (result.status.isSuccess || statusCode == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
                    connectedEndpoints[endpointId] = discoveredEndpoints[endpointId] ?: endpointDisplayName
                    lastP2PError = null
                    updateMeshState()
                    onPeerConnectedCallback?.invoke(endpointId)
                } else {
                    updateMeshState()
                }
            }

            override fun onDisconnected(endpointId: String) {
                pendingConnections.remove(endpointId)
                connectedEndpoints.remove(endpointId)
                activePeers.remove(endpointId)
                _nearbyPeers.value = activePeers.values.toList()
                updateMeshState()
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            if (bytes.isEmpty()) return
            try {
                // Handle Remote Ring Alarm Control Command over offline P2P
                if (bytes[0] == P2P_CMD_RING) {
                    val caller = if (bytes.size > 1) {
                        String(bytes, 1, bytes.size - 1, StandardCharsets.UTF_8)
                    } else {
                        connectedEndpoints[endpointId] ?: "Nearby GeoTrace Device"
                    }
                    FindDeviceAlarmManager.startRinging(context, caller)
                    return
                }

                val buffer = ByteBuffer.wrap(bytes)
                val peer = if (buffer.remaining() >= 30 && bytes[0] == P2P_PAYLOAD_VERSION_V3) {
                    buffer.get() // v3
                    val lat = buffer.double
                    val lng = buffer.double
                    val speed = buffer.float
                    val bearing = buffer.float
                    val battery = buffer.int.coerceIn(0, 100)
                    val tagLen = (buffer.get().toInt() and 0xFF).coerceAtMost(buffer.remaining())
                    val tagBytes = ByteArray(tagLen)
                    buffer.get(tagBytes)
                    val peerTag = String(tagBytes, StandardCharsets.US_ASCII)
                    val nameBytes = ByteArray(buffer.remaining())
                    buffer.get(nameBytes)
                    val name = String(nameBytes, StandardCharsets.UTF_8)
                        .ifBlank { connectedEndpoints[endpointId] ?: "Nearby Peer" }

                    P2PPeer(
                        endpointId = endpointId,
                        displayName = name,
                        latitude = lat,
                        longitude = lng,
                        speed = speed,
                        bearing = bearing,
                        batteryPercent = battery,
                        bleTag = peerTag,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                } else if (buffer.remaining() >= 29 && bytes[0] == P2P_PAYLOAD_VERSION_V2) {
                    buffer.get() // v2
                    val lat = buffer.double
                    val lng = buffer.double
                    val speed = buffer.float
                    val bearing = buffer.float
                    val battery = buffer.int.coerceIn(0, 100)
                    val nameBytes = ByteArray(buffer.remaining())
                    buffer.get(nameBytes)
                    val name = String(nameBytes, StandardCharsets.UTF_8)
                        .ifBlank { connectedEndpoints[endpointId] ?: "Nearby Peer" }

                    P2PPeer(
                        endpointId = endpointId,
                        displayName = name,
                        latitude = lat,
                        longitude = lng,
                        speed = speed,
                        bearing = bearing,
                        batteryPercent = battery,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                } else if (buffer.remaining() >= 24) {
                    val lat = buffer.double
                    val lng = buffer.double
                    val speed = buffer.float
                    val battery = buffer.int.coerceIn(0, 100)
                    val nameBytes = ByteArray(buffer.remaining())
                    buffer.get(nameBytes)
                    val name = String(nameBytes, StandardCharsets.UTF_8)
                        .ifBlank { connectedEndpoints[endpointId] ?: "Nearby Peer" }

                    P2PPeer(
                        endpointId = endpointId,
                        displayName = name,
                        latitude = lat,
                        longitude = lng,
                        speed = speed,
                        batteryPercent = battery,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                } else {
                    null
                }

                if (peer != null && isValidCoordinate(peer.latitude, peer.longitude)) {
                    connectedEndpoints.putIfAbsent(endpointId, peer.displayName)
                    activePeers[endpointId] = peer
                    _nearbyPeers.value = activePeers.values.toList()
                    updateMeshState()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error unpacking P2P payload: ${e.message}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun sendP2PRingCommand(endpointId: String) {
        try {
            val callerBytes = _offlineCallsign.value.toByteArray(StandardCharsets.UTF_8)
            val buffer = ByteBuffer.allocate(1 + callerBytes.size)
            buffer.put(P2P_CMD_RING)
            buffer.put(callerBytes)
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(buffer.array()))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send P2P ring command to $endpointId: ${e.message}")
        }
    }

    fun broadcastP2PLocationDirect(loc: LocationPoint, displayName: String, batteryPercent: Int) {
        broadcastP2PLocation(loc, displayName, batteryPercent)
    }

    private fun broadcastP2PLocation(loc: LocationPoint, displayName: String, batteryPercent: Int) {
        val targets = connectedEndpoints.keys.toList()
        if (targets.isEmpty()) return
        try {
            val tagBytes = bleTag.toByteArray(StandardCharsets.US_ASCII)
            val nameBytes = displayName.toByteArray(StandardCharsets.UTF_8)
            val buffer = ByteBuffer.allocate(1 + 8 + 8 + 4 + 4 + 4 + 1 + tagBytes.size + nameBytes.size)
            buffer.put(P2P_PAYLOAD_VERSION_V3)
            buffer.putDouble(loc.latitude)
            buffer.putDouble(loc.longitude)
            buffer.putFloat(loc.speed)
            buffer.putFloat(loc.bearing)
            buffer.putInt(batteryPercent)
            buffer.put(tagBytes.size.toByte())
            buffer.put(tagBytes)
            buffer.put(nameBytes)

            val payload = Payload.fromBytes(buffer.array())
            connectionsClient.sendPayload(targets, payload)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to broadcast P2P location: ${e.message}")
        }
    }

    private fun mapNearbyError(phase: String, ex: Exception): String {
        if (ex is ApiException) {
            return when (ex.statusCode) {
                ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR ->
                    "Bluetooth is disabled or unavailable. Enable Bluetooth for off-grid P2P discovery."
                ConnectionsStatusCodes.STATUS_RADIO_ERROR ->
                    "Device wireless radios (Bluetooth/Wi-Fi) are off or in Airplane Mode."
                ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_SCAN,
                ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_ADVERTISE,
                ConnectionsStatusCodes.MISSING_PERMISSION_BLUETOOTH_CONNECT,
                ConnectionsStatusCodes.MISSING_PERMISSION_NEARBY_WIFI_DEVICES,
                ConnectionsStatusCodes.MISSING_PERMISSION_ACCESS_FINE_LOCATION ->
                    "Missing Nearby, Bluetooth, or Location permission ($phase)."
                else -> "Nearby $phase failed (code ${ex.statusCode})"
            }
        }
        return ex.localizedMessage ?: "Nearby $phase error"
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val prefix = (1..3).map { chars.random() }.joinToString("")
        val suffix = (1..3).map { chars.random() }.joinToString("")
        return "$prefix-$suffix"
    }
}
