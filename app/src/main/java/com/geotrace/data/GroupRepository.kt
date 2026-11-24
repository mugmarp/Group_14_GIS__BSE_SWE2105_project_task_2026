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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
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
private const val P2P_CMD_GHOST_HIDE: Byte = 11
private const val P2P_CMD_PRESENCE_NO_FIX: Byte = 12
private const val P2P_MAX_CONNECT_RETRIES = 2

enum class P2PRingSendResult {
    SENT,
    COOLDOWN_ACTIVE,
    NOT_CONNECTED,
    FAILED
}

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
    private val identityPrefs = context.getSharedPreferences("geotrace_device_identity", Context.MODE_PRIVATE)
    private val p2pScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    val currentDeviceId: String = loadOrInitDeviceId()
    val bleTag: String = currentDeviceId
        .filter { it.isLetterOrDigit() }
        .takeLast(6)
        .uppercase()
        .padStart(6, '0')

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

    private val _lastGroupSyncTimestampMs = MutableStateFlow<Long?>(null)
    val lastGroupSyncTimestampMs = _lastGroupSyncTimestampMs.asStateFlow()

    private val knownGroupIds = java.util.concurrent.CopyOnWriteArraySet<String>()

    @Volatile
    private var lastPermissionToastMs: Long = 0L

    private val _p2pRingCooldownsMs = MutableStateFlow<Map<String, Long>>(emptyMap())
    val p2pRingCooldownsMs = _p2pRingCooldownsMs.asStateFlow()

    @Volatile
    private var lastSyncedLat: Double? = null
    @Volatile
    private var lastSyncedLng: Double? = null
    @Volatile
    private var lastSyncedBattery: Int = -1
    @Volatile
    private var lastSyncedCharging: Boolean? = null
    @Volatile
    private var lastSyncedMode: String? = null
    @Volatile
    private var lastSyncedGhost: Boolean? = null
    @Volatile
    private var lastSyncedGroupCount: Int = -1
    @Volatile
    private var lastFullSyncWallMs: Long = 0L

    fun clearFirestoreError() {
        _firestoreError.value = null
    }

    fun clearSyncStateOnSignOut() {
        _lastSyncTimestampMs.value = null
        _lastGroupSyncTimestampMs.value = null
        lastSyncedLat = null
        lastSyncedLng = null
        lastSyncedBattery = -1
        lastSyncedCharging = null
        lastSyncedMode = null
        lastSyncedGhost = null
        lastSyncedGroupCount = -1
        lastFullSyncWallMs = 0L
        knownGroupIds.clear()
        saveSelectedGroupId(null)
    }

    fun shouldSkipRedundantCloudSync(
        location: LocationPoint,
        batteryPercent: Int,
        isCharging: Boolean,
        trackingModeName: String,
        isGhost: Boolean,
        groupCount: Int,
        force: Boolean = false
    ): Boolean {
        if (force) return false
        val prevLat = lastSyncedLat ?: return false
        val prevLng = lastSyncedLng ?: return false
        val elapsedMs = System.currentTimeMillis() - lastFullSyncWallMs
        if (elapsedMs >= 90_000L) return false
        if (lastSyncedCharging != isCharging ||
            lastSyncedMode != trackingModeName ||
            lastSyncedGhost != isGhost ||
            lastSyncedGroupCount != groupCount ||
            kotlin.math.abs(lastSyncedBattery - batteryPercent) >= 2
        ) {
            return false
        }
        val dist = FloatArray(1)
        android.location.Location.distanceBetween(prevLat, prevLng, location.latitude, location.longitude, dist)
        return dist[0] < 3.0f
    }

    fun markCloudSyncCompleted(
        location: LocationPoint,
        batteryPercent: Int,
        isCharging: Boolean,
        trackingModeName: String,
        isGhost: Boolean,
        groupCount: Int
    ) {
        lastSyncedLat = location.latitude
        lastSyncedLng = location.longitude
        lastSyncedBattery = batteryPercent
        lastSyncedCharging = isCharging
        lastSyncedMode = trackingModeName
        lastSyncedGhost = isGhost
        lastSyncedGroupCount = groupCount
        lastFullSyncWallMs = System.currentTimeMillis()
    }

    fun getKnownGroupIdsSnapshot(): List<String> = knownGroupIds.toList()

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
        return lat.isFinite() && lng.isFinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0 &&
            (lat != 0.0 || lng != 0.0)
    }

    private val discoveredEndpoints = ConcurrentHashMap<String, String>()
    private val pendingConnections = ConcurrentHashMap.newKeySet<String>()
    private val incomingEndpoints = ConcurrentHashMap.newKeySet<String>()
    private val connectedEndpoints = ConcurrentHashMap<String, String>()
    private val activePeers = ConcurrentHashMap<String, P2PPeer>()
    private val connectionRetryCounts = ConcurrentHashMap<String, Int>()
    private val retryJobs = ConcurrentHashMap<String, Job>()
    private var p2pRestartJob: Job? = null

    @Volatile
    private var isP2PMeshRequested = false
    @Volatile
    private var isP2PAdvertising = false
    @Volatile
    private var isP2PDiscovering = false
    @Volatile
    private var lastP2PError: String? = null
    @Volatile
    private var lastHandledRingTimestampMs: Long = loadIdentityLong("last_handled_ring_ms", 0L)
    @Volatile
    private var lastHandledRingToken: String = loadIdentityString("last_handled_ring_token", "")
    @Volatile
    private var lastIncomingP2PRingMs: Long = 0L
    @Volatile
    private var estimatedClockSkewMs: Long = 0L

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

    private fun loadIdentityString(key: String, default: String): String {
        val local = identityPrefs.getString(key, null)
        if (local != null) return local
        val migrated = prefs.getString(key, null)
        if (migrated != null) {
            identityPrefs.edit().putString(key, migrated).apply()
            return migrated
        }
        return default
    }

    private fun loadIdentityLong(key: String, default: Long): Long {
        if (identityPrefs.contains(key)) return identityPrefs.getLong(key, default)
        if (prefs.contains(key)) {
            val migrated = prefs.getLong(key, default)
            identityPrefs.edit().putLong(key, migrated).apply()
            return migrated
        }
        return default
    }

    private fun loadOrInitDeviceId(): String {
        val modelPrefix = android.os.Build.MODEL
            .filter { it.isLetterOrDigit() }
            .take(6)
            .lowercase()
            .ifBlank { "dev" }

        val rawAndroidId = try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )?.trim()?.lowercase()
        } catch (_: Exception) {
            null
        }

        // Known Android emulator placeholder or unavailable SSAID falls back to persisted UUID
        val hasValidSsaid = !rawAndroidId.isNullOrBlank() &&
            rawAndroidId != "9774d56d682e549c" &&
            rawAndroidId.length >= 8

        if (hasValidSsaid) {
            val salted = "geotrace:device:v1:${context.packageName}:$rawAndroidId"
            val digest = java.security.MessageDigest.getInstance("SHA-256")
                .digest(salted.toByteArray(StandardCharsets.UTF_8))
            val hexHash = digest.joinToString("") { "%02x".format(it) }.take(12)
            val deterministicId = "${modelPrefix}_$hexHash"
            if (identityPrefs.getString("ssaid_device_id", null) != deterministicId) {
                identityPrefs.edit().putString("ssaid_device_id", deterministicId).apply()
            }
            return deterministicId
        }

        val savedFallback = identityPrefs.getString("fallback_device_id", null)
            ?: prefs.getString("geotrace_device_id", null)
        if (!savedFallback.isNullOrBlank()) {
            identityPrefs.edit().putString("fallback_device_id", savedFallback).apply()
            return savedFallback
        }
        val generated = "${modelPrefix}_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        identityPrefs.edit().putString("fallback_device_id", generated).apply()
        return generated
    }

    private fun loadOrInitOfflineCallsign(): String {
        val saved = identityPrefs.getString("offline_p2p_callsign", null)
            ?: prefs.getString("offline_p2p_callsign", null)
        if (!saved.isNullOrBlank()) {
            identityPrefs.edit().putString("offline_p2p_callsign", saved).apply()
            return saved
        }
        val modelTag = android.os.Build.MODEL
            .filter { it.isLetterOrDigit() }
            .take(6)
            .ifBlank { "Phone" }
        val suffix = currentDeviceId.takeLast(4).uppercase()
        val generated = "$modelTag-$suffix"
        identityPrefs.edit().putString("offline_p2p_callsign", generated).apply()
        return generated
    }

    fun setOfflineCallsign(callsign: String) {
        val cleaned = callsign.trim().take(24)
        if (cleaned.isNotBlank()) {
            identityPrefs.edit().putString("offline_p2p_callsign", cleaned).apply()
            _offlineCallsign.value = cleaned
            if (isP2PMeshRequested) {
                restartP2PMeshAsync(cleaned)
            }
        }
    }

    suspend fun syncDeviceNameInCloud(activeGroupId: String?) {
        val uid = auth.currentUser?.uid ?: return
        val name = getCurrentUserDisplayName()
        try {
            db.collection("users").document(uid)
                .collection("devices").document(currentDeviceId)
                .update("deviceName", name)
                .await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Doc may not exist yet before first fix; ignored
        }
        if (!activeGroupId.isNullOrBlank() && !_isGhostMode.value) {
            try {
                db.collection("groups").document(activeGroupId)
                    .collection("live_locations").document("${uid}_${currentDeviceId}")
                    .update("displayName", name)
                    .await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}
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
     * Create a new tracking group with a collision-checked short invite code.
     */
    suspend fun createGroup(name: String): Result<Group> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in to create a group."))

        return try {
            var inviteCode = generateInviteCode()
            for (attempt in 0 until 5) {
                val existing = db.collection("groups")
                    .whereEqualTo("inviteCode", inviteCode)
                    .limit(1)
                    .get()
                    .await()
                if (existing.isEmpty) break
                inviteCode = generateInviteCode()
            }
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
            knownGroupIds.add(docRef.id)
            Result.success(created)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
            knownGroupIds.add(doc.id)
            val updatedGroup = group.copy(memberIds = (group.memberIds + uid).distinct())
            Result.success(updatedGroup)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
                    close(error)
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
                knownGroupIds.clear()
                knownGroupIds.addAll(groups.map { it.id })
                trySend(groups)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Leave a tracking group and delete this device's live location document.
     * - If the user is the last member, cleanly deletes the group & subcollection docs.
     * - If the creator leaves a multi-member group, transfers `createdBy` to the next member.
     */
    suspend fun leaveGroup(groupId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in to leave a group."))

        return try {
            val groupRef = db.collection("groups").document(groupId)
            val locCol = groupRef.collection("live_locations")
            try {
                locCol.document("${uid}_${currentDeviceId}").delete().await()
                locCol.document(uid).delete().await()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {}

            val snapshot = groupRef.get().await()
            val group = snapshot.toObject(Group::class.java)
            if (group == null) {
                return Result.success(Unit)
            }

            val remaining = group.memberIds.filter { it != uid }
            if (remaining.isEmpty()) {
                return deleteGroup(groupId)
            } else if (group.createdBy == uid) {
                groupRef.update(
                    mapOf(
                        "memberIds" to remaining,
                        "createdBy" to remaining.first()
                    )
                ).await()
            } else {
                groupRef.update("memberIds", FieldValue.arrayRemove(uid)).await()
            }
            knownGroupIds.remove(groupId)
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to leave group: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Permanently delete a tracking group and batch-clean all live_locations docs.
     */
    suspend fun deleteGroup(groupId: String): Result<Unit> {
        auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in to delete a group."))

        return try {
            val groupRef = db.collection("groups").document(groupId)
            val docs = try {
                groupRef.collection("live_locations").get().await().documents
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                emptyList()
            }
            for (chunk in docs.chunked(450)) {
                val batch = db.batch()
                for (doc in chunk) {
                    batch.delete(doc.reference)
                }
                batch.commit().await()
            }
            groupRef.delete().await()
            knownGroupIds.remove(groupId)
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to delete group: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a stale or unwanted device location doc from a group (callable by group creator or doc owner).
     * If the target belongs to another user and has no remaining active devices in this group, also removes their UID from memberIds.
     */
    suspend fun removeGroupMemberDevice(groupId: String, target: MemberLocation): Result<Unit> {
        val callerUid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in to remove a device."))

        return try {
            val groupRef = db.collection("groups").document(groupId)
            val locCol = groupRef.collection("live_locations")

            val docIdsToDelete = buildSet {
                if (target.id.isNotBlank()) add(target.id)
                if (target.userId.isNotBlank() && target.deviceId.isNotBlank()) {
                    add("${target.userId}_${target.deviceId}")
                }
                if (target.userId.isNotBlank() && target.deviceId.isBlank()) {
                    add(target.userId)
                }
            }
            for (docId in docIdsToDelete) {
                try {
                    locCol.document(docId).delete().await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {}
            }

            if (target.userId.isNotBlank() && target.userId != callerUid) {
                val remaining = locCol.whereEqualTo("userId", target.userId).get().await()
                if (remaining.isEmpty) {
                    groupRef.update("memberIds", FieldValue.arrayRemove(target.userId)).await()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to remove group device ${target.id}: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Remove this device's live coordinates from a group (used when Ghost Mode activates).
     */
    suspend fun clearGroupPresence(groupId: String?) {
        val uid = auth.currentUser?.uid ?: return
        if (groupId.isNullOrBlank()) return
        try {
            val locCol = db.collection("groups").document(groupId).collection("live_locations")
            locCol.document("${uid}_${currentDeviceId}").delete().await()
            locCol.document(uid).delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {}
    }

    /**
     * Publish current device's live location & telemetry to a group's live_locations collection
     * using `{uid}_{deviceId}` so multiple devices under one account never overwrite each other.
     * In Ghost Mode (`isSharingEnabled = false`), scrubs coordinates from `live_locations` rather than uploading GPS.
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

        if (!isSharingEnabled) {
            clearGroupPresence(groupId)
            return Result.success(Unit)
        }

        if (!isValidCoordinate(location.latitude, location.longitude)) {
            return Result.failure(IllegalArgumentException("Invalid GPS coordinates."))
        }

        val safeDisplayName = displayName.trim().ifBlank { "GeoTrace Device" }.take(50)
        val safeBattery = batteryPercent.coerceIn(0, 100)
        val safeAccuracy = if (location.accuracy.isFinite() && location.accuracy >= 0f) location.accuracy else 0f
        val safeSpeed = if (location.speed.isFinite() && location.speed >= 0f) location.speed else 0f
        val safeBearing = if (location.bearing.isFinite()) location.bearing else 0f
        val safeAltitude = if (location.altitude.isFinite()) location.altitude else 0.0

        return try {
            val docId = "${uid}_${currentDeviceId}"
            val docRef = db.collection("groups").document(groupId)
                .collection("live_locations").document(docId)

            val payload = mapOf(
                "userId" to uid,
                "deviceId" to currentDeviceId,
                "deviceModel" to android.os.Build.MODEL.take(80),
                "bleTag" to bleTag.take(16),
                "displayName" to safeDisplayName,
                "avatarColor" to avatarColor,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracy" to safeAccuracy,
                "speed" to safeSpeed,
                "bearing" to safeBearing,
                "altitude" to safeAltitude,
                "gait" to location.gaitLabel,
                "batteryPercent" to safeBattery,
                "isCharging" to isCharging,
                "trackingMode" to trackingMode,
                "isSharingEnabled" to true,
                "lastUpdated" to FieldValue.serverTimestamp()
            )

            docRef.set(payload).await()
            val now = System.currentTimeMillis()
            _lastSyncTimestampMs.value = now
            _lastGroupSyncTimestampMs.value = now

            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to publish live location to group $groupId: ${e.message}")
            maybeReportWritePermissionError(e, "group location")
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
                    close(error)
                    return@addSnapshotListener
                }
                val locations = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(
                            MemberLocation::class.java,
                            com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )?.copy(id = doc.id)
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

        if (!isValidCoordinate(location.latitude, location.longitude)) {
            return Result.failure(IllegalArgumentException("Invalid GPS coordinates."))
        }

        val safeDeviceName = _offlineCallsign.value.trim().ifBlank { "GeoTrace Device" }.take(60)
        val safeBattery = batteryPercent.coerceIn(0, 100)
        val safeAccuracy = if (location.accuracy.isFinite() && location.accuracy >= 0f) location.accuracy else 0f
        val safeSpeed = if (location.speed.isFinite() && location.speed >= 0f) location.speed else 0f
        val safeBearing = if (location.bearing.isFinite()) location.bearing else 0f

        return try {
            val docRef = db.collection("users").document(uid)
                .collection("devices").document(currentDeviceId)

            val payload = mapOf(
                "userId" to uid,
                "deviceId" to currentDeviceId,
                "deviceName" to safeDeviceName,
                "deviceModel" to android.os.Build.MODEL.take(80),
                "bleTag" to bleTag.take(16),
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracy" to safeAccuracy,
                "speed" to safeSpeed,
                "bearing" to safeBearing,
                "batteryPercent" to safeBattery,
                "isCharging" to isCharging,
                "trackingMode" to trackingMode,
                "lastUpdated" to FieldValue.serverTimestamp()
            )
            // Merge so another phone's pending ringRequestedAt trigger is preserved
            docRef.set(payload, SetOptions.merge()).await()
            _lastSyncTimestampMs.value = System.currentTimeMillis()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to update personal device doc: ${e.message}")
            maybeReportWritePermissionError(e, "device fleet")
            Result.failure(e)
        }
    }

    /**
     * Publishes device fleet location + all known joined tracking groups safely from either foreground UI
     * or 24/7 background protection / trail service.
     */
    suspend fun syncDeviceAndGroupTelemetry(
        location: LocationPoint,
        trackingModeName: String,
        explicitGroupId: String? = getSelectedGroupId(),
        force: Boolean = false,
        includeP2PBroadcast: Boolean = true
    ): Boolean {
        val uid = auth.currentUser?.uid ?: return false
        val (batteryPercent, isCharging) = readBatteryInfo()
        val displayName = getCurrentUserDisplayName()
        val targetGroupIds = (listOfNotNull(explicitGroupId) + knownGroupIds.toList()).distinct()
        var anySucceeded = false

        // Broadcast over off-grid P2P first so lack of internet never blocks Nearby updates
        if (includeP2PBroadcast && !_isGhostMode.value && (isP2PMeshRequested || isP2PAdvertising || isP2PDiscovering || connectedEndpoints.isNotEmpty())) {
            broadcastP2PLocation(location, displayName, batteryPercent)
        }

        if (shouldSkipRedundantCloudSync(
                location = location,
                batteryPercent = batteryPercent,
                isCharging = isCharging,
                trackingModeName = trackingModeName,
                isGhost = _isGhostMode.value,
                groupCount = targetGroupIds.size,
                force = force
            )
        ) {
            return true
        }

        val fleetDeferred: kotlinx.coroutines.Deferred<Result<Unit>>
        val groupDeferreds: List<kotlinx.coroutines.Deferred<Result<Unit>>>
        coroutineScope {
            fleetDeferred = async {
                publishPersonalDevice(
                    location = location,
                    batteryPercent = batteryPercent,
                    isCharging = isCharging,
                    trackingMode = trackingModeName
                )
            }
            val colorPalette = listOf("#2563EB", "#059669", "#D97706", "#DC2626", "#7C3AED", "#DB2777", "#0891B2")
            val avatarColor = colorPalette[(uid.hashCode() and 0x7fffffff) % colorPalette.size]
            groupDeferreds = targetGroupIds.map { groupId ->
                async {
                    publishLocation(
                        groupId = groupId,
                        location = location,
                        displayName = displayName,
                        avatarColor = avatarColor,
                        batteryPercent = batteryPercent,
                        isCharging = isCharging,
                        trackingMode = trackingModeName,
                        isSharingEnabled = !_isGhostMode.value
                    )
                }
            }
            if (fleetDeferred.await().isSuccess) anySucceeded = true
            if (groupDeferreds.awaitAll().any { it.isSuccess }) anySucceeded = true
        }
        if (anySucceeded) {
            markCloudSyncCompleted(
                location = location,
                batteryPercent = batteryPercent,
                isCharging = isCharging,
                trackingModeName = trackingModeName,
                isGhost = _isGhostMode.value,
                groupCount = targetGroupIds.size
            )
        }
        return anySucceeded
    }

    private fun maybeReportWritePermissionError(error: Exception, targetLabel: String) {
        val fsErr = error as? com.google.firebase.firestore.FirebaseFirestoreException ?: return
        if (fsErr.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            val now = System.currentTimeMillis()
            if (now - lastPermissionToastMs >= 60_000L) {
                lastPermissionToastMs = now
                _firestoreError.value = "Sync blocked by permissions on $targetLabel. Check sign-in and group membership."
            }
        }
    }

    fun readBatteryInfo(): Pair<Int, Boolean> {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager
            if (bm != null) {
                val cap = bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val batteryPct = if (cap in 0..100) cap else 100
                Pair(batteryPct, bm.isCharging)
            } else {
                Pair(100, false)
            }
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

                    // Check if another one of our devices requested THIS device to ring (clock-skew & duplicate safe)
                    val selfDoc = devices.firstOrNull { it.deviceId == currentDeviceId || it.id == currentDeviceId }
                    if (selfDoc != null) {
                        if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                            selfDoc.lastUpdated?.toDate()?.time?.let { serverLastUpdated ->
                                estimatedClockSkewMs = System.currentTimeMillis() - serverLastUpdated
                            }
                        }

                        val reqAt = selfDoc.ringRequestedAt
                        val reqToken = selfDoc.ringToken
                        val serverTriggerMs = selfDoc.ringServerTimestamp?.toDate()?.time
                        val isNewToken = reqToken.isNotBlank() && reqToken != lastHandledRingToken
                        val isNewTimestamp = reqAt > lastHandledRingTimestampMs

                        if (reqAt > 0L && (isNewToken || (reqToken.isBlank() && isNewTimestamp))) {
                            val nowServerApprox = System.currentTimeMillis() - estimatedClockSkewMs
                            val ageMs = if (serverTriggerMs != null) {
                                nowServerApprox - serverTriggerMs
                            } else {
                                System.currentTimeMillis() - reqAt
                            }
                            if (ageMs in -180_000L..180_000L) {
                                lastHandledRingTimestampMs = reqAt
                                if (reqToken.isNotBlank()) {
                                    lastHandledRingToken = reqToken
                                }
                                identityPrefs.edit()
                                    .putLong("last_handled_ring_ms", reqAt)
                                    .putString("last_handled_ring_token", lastHandledRingToken)
                                    .apply()
                                val caller = selfDoc.ringRequestedBy.ifBlank { "Your GeoTrace Device" }
                                FindDeviceAlarmManager.startRinging(context, caller)
                            }
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
            val token = UUID.randomUUID().toString()
            db.collection("users").document(uid)
                .collection("devices").document(targetDeviceId)
                .update(
                    mapOf(
                        "ringRequestedAt" to nowMs,
                        "ringRequestedBy" to _offlineCallsign.value,
                        "ringToken" to token,
                        "ringServerTimestamp" to FieldValue.serverTimestamp()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
                        "ringRequestedBy" to "",
                        "ringToken" to ""
                    )
                )
                .await()
        } catch (e: CancellationException) {
            throw e
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
                        "ringRequestedBy" to "",
                        "ringToken" to ""
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
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
            if (e is CancellationException) throw e
            Log.e(TAG, "Failed to remove personal device: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun unlinkCurrentDeviceBeforeSignOut() {
        val uid = auth.currentUser?.uid ?: return
        try {
            db.collection("users").document(uid)
                .collection("devices").document(currentDeviceId)
                .delete()
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Failed to unlink personal device doc on sign out: ${e.message}")
        }
        val groupIds = (listOfNotNull(getSelectedGroupId()) + knownGroupIds.toList()).distinct()
        for (groupId in groupIds) {
            clearGroupPresence(groupId)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Nearby Connections P2P Mesh (Off-Grid Location Exchange & Remote Ring)
    // -----------------------------------------------------------------------------------------

    fun startP2PMesh(displayName: String) {
        val myName = displayName.ifBlank { getCurrentUserDisplayName() }
        p2pRestartJob?.cancel()
        p2pRestartJob = null
        cancelAllRetryJobs()
        isP2PMeshRequested = true
        lastP2PError = null
        updateMeshState()
        startRadiosInternal(myName)
    }

    fun rescanP2PMesh(displayName: String) {
        restartP2PMeshAsync(displayName)
    }

    private fun restartP2PMeshAsync(displayName: String) {
        val myName = displayName.ifBlank { getCurrentUserDisplayName() }
        p2pRestartJob?.cancel()
        cancelAllRetryJobs()
        stopNearbyRadiosOnly()
        isP2PMeshRequested = true
        lastP2PError = null
        updateMeshState()
        p2pRestartJob = p2pScope.launch {
            delay(350L)
            p2pRestartJob = null
            if (!isP2PMeshRequested) return@launch
            startRadiosInternal(myName)
        }
    }

    private fun startRadiosInternal(myName: String) {
        if (!isP2PMeshRequested) return
        if (!_isGhostMode.value) {
            startAdvertising(myName)
        } else {
            isP2PAdvertising = false
            updateMeshState()
        }
        startDiscovery(myName)
    }

    fun onP2PGhostModeChanged(ghostEnabled: Boolean) {
        if (!isP2PMeshRequested) return
        if (ghostEnabled) {
            sendGhostHideCommand(connectedEndpoints.keys.toList())
            try {
                connectionsClient.stopAdvertising()
            } catch (e: Exception) {
                Log.w(TAG, "Error pausing P2P advertising in Ghost Mode: ${e.message}")
            }
            isP2PAdvertising = false
            updateMeshState()
        } else {
            startAdvertising(getCurrentUserDisplayName())
            updateMeshState()
        }
    }

    fun stopP2PMesh() {
        p2pRestartJob?.cancel()
        p2pRestartJob = null
        isP2PMeshRequested = false
        cancelAllRetryJobs()
        stopNearbyRadiosOnly()
        lastP2PError = null
        updateMeshState()
    }

    private fun cancelAllRetryJobs() {
        retryJobs.values.forEach { it.cancel() }
        retryJobs.clear()
        connectionRetryCounts.clear()
    }

    private fun stopNearbyRadiosOnly() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping P2P mesh: ${e.message}")
        } finally {
            isP2PAdvertising = false
            isP2PDiscovering = false
            discoveredEndpoints.clear()
            pendingConnections.clear()
            incomingEndpoints.clear()
            connectedEndpoints.clear()
            activePeers.clear()
            _nearbyPeers.value = emptyList()
        }
    }

    private fun updateMeshState() {
        val discCount = discoveredEndpoints.size
        val connCount = connectedEndpoints.size
        val peerCount = activePeers.size
        val isGhost = _isGhostMode.value

        val statusMsg = when {
            !isP2PMeshRequested -> "Off-grid P2P scanner idle"
            lastP2PError != null -> lastP2PError!!
            peerCount > 0 && isGhost -> "Ghost Mode: receiving $peerCount offline peer(s) • You are hidden"
            peerCount > 0 -> "Receiving live telemetry from $peerCount offline peer(s)"
            connCount > 0 && isGhost -> "Ghost Mode: $connCount link(s) active • Waiting for peer telemetry"
            connCount > 0 -> "Connected to $connCount peer(s); exchanging telemetry..."
            discCount > 0 -> "Found $discCount device(s) nearby; establishing encrypted P2P link..."
            isGhost && isP2PDiscovering -> "Ghost Mode: scanning nearby only (hidden from peers)"
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
        if (!isP2PMeshRequested || _isGhostMode.value) return
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            displayName,
            NEARBY_SERVICE_ID,
            createLifecycleCallback(),
            advertisingOptions
        ).addOnSuccessListener {
            if (!isP2PMeshRequested || _isGhostMode.value) {
                try {
                    connectionsClient.stopAdvertising()
                } catch (_: Exception) {}
                isP2PAdvertising = false
                updateMeshState()
                return@addOnSuccessListener
            }
            isP2PAdvertising = true
            lastP2PError = null
            updateMeshState()
            Log.i(TAG, "Nearby advertising started as '$displayName'")
        }.addOnFailureListener { ex ->
            val code = (ex as? ApiException)?.statusCode
            if (code == ConnectionsStatusCodes.STATUS_ALREADY_ADVERTISING) {
                isP2PAdvertising = !_isGhostMode.value && isP2PMeshRequested
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
        if (!isP2PMeshRequested) return
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

                    if (connectedEndpoints.containsKey(endpointId) ||
                        incomingEndpoints.contains(endpointId) ||
                        !pendingConnections.add(endpointId)
                    ) {
                        return
                    }

                    val delayMs = when {
                        myDisplayName < remoteName -> 60L
                        myDisplayName > remoteName -> 1100L
                        else -> kotlin.random.Random.nextLong(250L, 1450L)
                    }
                    p2pScope.launch {
                        delay(delayMs)
                        if (!isP2PMeshRequested ||
                            connectedEndpoints.containsKey(endpointId) ||
                            incomingEndpoints.contains(endpointId) ||
                            !discoveredEndpoints.containsKey(endpointId)
                        ) {
                            pendingConnections.remove(endpointId)
                            return@launch
                        }
                        initiateConnectionToEndpoint(endpointId, remoteName)
                    }
                }

                override fun onEndpointLost(endpointId: String) {
                    discoveredEndpoints.remove(endpointId)
                    pendingConnections.remove(endpointId)
                    retryJobs.remove(endpointId)?.cancel()
                    connectionRetryCounts.remove(endpointId)
                    if (!connectedEndpoints.containsKey(endpointId)) {
                        activePeers.remove(endpointId)
                        emitSortedPeers()
                    }
                    updateMeshState()
                }
            },
            discoveryOptions
        ).addOnSuccessListener {
            if (!isP2PMeshRequested) {
                try {
                    connectionsClient.stopDiscovery()
                } catch (_: Exception) {}
                isP2PDiscovering = false
                updateMeshState()
                return@addOnSuccessListener
            }
            isP2PDiscovering = true
            lastP2PError = null
            updateMeshState()
        }.addOnFailureListener { ex ->
            val code = (ex as? ApiException)?.statusCode
            if (code == ConnectionsStatusCodes.STATUS_ALREADY_DISCOVERING) {
                isP2PDiscovering = isP2PMeshRequested
                lastP2PError = null
            } else {
                isP2PDiscovering = false
                lastP2PError = mapNearbyError("Discovery", ex)
            }
            updateMeshState()
        }
    }

    private fun initiateConnectionToEndpoint(endpointId: String, remoteName: String) {
        if (!isP2PMeshRequested ||
            connectedEndpoints.containsKey(endpointId) ||
            incomingEndpoints.contains(endpointId)
        ) {
            pendingConnections.remove(endpointId)
            return
        }
        connectionsClient.requestConnection(
            getCurrentUserDisplayName(),
            endpointId,
            createLifecycleCallback(remoteName)
        ).addOnFailureListener { ex ->
            pendingConnections.remove(endpointId)
            val statusCode = (ex as? ApiException)?.statusCode
            Log.w(TAG, "requestConnection to $endpointId failed (code=$statusCode): ${ex.message}")
            when (statusCode) {
                ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT -> {
                    connectedEndpoints.putIfAbsent(endpointId, remoteName)
                    seedConnectedPeerIfAbsent(endpointId, remoteName)
                    updateMeshState()
                    if (!_isGhostMode.value) {
                        onPeerConnectedCallback?.invoke(endpointId)
                    }
                }
                ConnectionsStatusCodes.STATUS_ENDPOINT_UNKNOWN -> {
                    discoveredEndpoints.remove(endpointId)
                    connectionRetryCounts.remove(endpointId)
                    updateMeshState()
                }
                else -> scheduleConnectionRetry(endpointId, remoteName)
            }
        }
    }

    private fun scheduleConnectionRetry(endpointId: String, remoteName: String) {
        if (!isP2PMeshRequested ||
            connectedEndpoints.containsKey(endpointId) ||
            incomingEndpoints.contains(endpointId) ||
            !discoveredEndpoints.containsKey(endpointId)
        ) return
        val attempt = (connectionRetryCounts[endpointId] ?: 0) + 1
        if (attempt > P2P_MAX_CONNECT_RETRIES) return
        connectionRetryCounts[endpointId] = attempt
        val backoffMs = if (attempt == 1) 1500L else 3500L
        retryJobs.remove(endpointId)?.cancel()
        retryJobs[endpointId] = p2pScope.launch {
            delay(backoffMs)
            retryJobs.remove(endpointId)
            if (!isP2PMeshRequested ||
                connectedEndpoints.containsKey(endpointId) ||
                incomingEndpoints.contains(endpointId) ||
                !discoveredEndpoints.containsKey(endpointId)
            ) {
                return@launch
            }
            if (pendingConnections.add(endpointId)) {
                initiateConnectionToEndpoint(endpointId, remoteName)
            }
        }
    }

    private fun seedConnectedPeerIfAbsent(endpointId: String, displayName: String) {
        if (activePeers.containsKey(endpointId)) return
        activePeers[endpointId] = P2PPeer(
            endpointId = endpointId,
            displayName = displayName.ifBlank { "Nearby Peer" },
            latitude = 0.0,
            longitude = 0.0,
            speed = 0f,
            bearing = 0f,
            batteryPercent = 100,
            bleTag = "",
            lastSeenTimestamp = System.currentTimeMillis()
        )
        emitSortedPeers()
    }

    private fun emitSortedPeers() {
        _nearbyPeers.value = activePeers.values
            .sortedWith(compareBy<P2PPeer> { it.displayName.lowercase() }.thenBy { it.endpointId })
    }

    private fun createLifecycleCallback(knownName: String? = null): ConnectionLifecycleCallback {
        return object : ConnectionLifecycleCallback() {
            private var endpointDisplayName: String = knownName ?: "GeoPeer"

            override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                if (!isP2PMeshRequested) {
                    connectionsClient.rejectConnection(endpointId)
                    return
                }
                endpointDisplayName = connectionInfo.endpointName.ifBlank { endpointDisplayName }
                discoveredEndpoints[endpointId] = endpointDisplayName
                incomingEndpoints.add(endpointId)
                retryJobs.remove(endpointId)?.cancel()
                connectionsClient.acceptConnection(endpointId, payloadCallback)
                    .addOnFailureListener { ex ->
                        Log.w(TAG, "acceptConnection to $endpointId failed: ${ex.message}")
                        incomingEndpoints.remove(endpointId)
                        pendingConnections.remove(endpointId)
                    }
                updateMeshState()
            }

            override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                pendingConnections.remove(endpointId)
                incomingEndpoints.remove(endpointId)
                val statusCode = result.status.statusCode
                if (result.status.isSuccess || statusCode == ConnectionsStatusCodes.STATUS_ALREADY_CONNECTED_TO_ENDPOINT) {
                    retryJobs.remove(endpointId)?.cancel()
                    connectionRetryCounts.remove(endpointId)
                    val resolvedName = discoveredEndpoints[endpointId] ?: endpointDisplayName
                    connectedEndpoints[endpointId] = resolvedName
                    seedConnectedPeerIfAbsent(endpointId, resolvedName)
                    lastP2PError = null
                    updateMeshState()
                    if (_isGhostMode.value) {
                        sendGhostHideCommand(listOf(endpointId))
                    } else {
                        onPeerConnectedCallback?.invoke(endpointId)
                    }
                } else {
                    Log.w(TAG, "onConnectionResult failure for $endpointId: code=$statusCode")
                    if (statusCode == ConnectionsStatusCodes.STATUS_ENDPOINT_UNKNOWN) {
                        discoveredEndpoints.remove(endpointId)
                        connectionRetryCounts.remove(endpointId)
                    } else if (statusCode != ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED) {
                        scheduleConnectionRetry(endpointId, endpointDisplayName)
                    }
                    updateMeshState()
                }
            }

            override fun onDisconnected(endpointId: String) {
                pendingConnections.remove(endpointId)
                incomingEndpoints.remove(endpointId)
                retryJobs.remove(endpointId)?.cancel()
                connectionRetryCounts.remove(endpointId)
                connectedEndpoints.remove(endpointId)
                activePeers.remove(endpointId)
                emitSortedPeers()
                updateMeshState()
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            if (bytes.isEmpty()) return
            try {
                // Handle Remote Ring Alarm Control Command over offline P2P (rate-limited against spam)
                if (bytes[0] == P2P_CMD_RING) {
                    val now = System.currentTimeMillis()
                    if (now - lastIncomingP2PRingMs < 4_000L) return
                    lastIncomingP2PRingMs = now
                    val caller = if (bytes.size > 1) {
                        String(bytes, 1, bytes.size - 1, StandardCharsets.UTF_8)
                    } else {
                        connectedEndpoints[endpointId] ?: "Nearby GeoTrace Device"
                    }
                    FindDeviceAlarmManager.startRinging(context, caller)
                    return
                }

                if (bytes[0] == P2P_CMD_GHOST_HIDE) {
                    if (activePeers.remove(endpointId) != null) {
                        emitSortedPeers()
                        updateMeshState()
                    }
                    return
                }

                if (bytes[0] == P2P_CMD_PRESENCE_NO_FIX) {
                    val buffer = ByteBuffer.wrap(bytes)
                    if (buffer.remaining() >= 6) {
                        buffer.get() // cmd
                        val battery = buffer.int.coerceIn(0, 100)
                        val tagLen = (buffer.get().toInt() and 0xFF).coerceAtMost(buffer.remaining())
                        val tagBytes = ByteArray(tagLen)
                        buffer.get(tagBytes)
                        val peerTag = String(tagBytes, StandardCharsets.US_ASCII)
                        val nameBytes = ByteArray(buffer.remaining())
                        buffer.get(nameBytes)
                        val name = String(nameBytes, StandardCharsets.UTF_8)
                            .ifBlank { connectedEndpoints[endpointId] ?: "Nearby Peer" }
                        val prev = activePeers[endpointId]
                        val lat = if (prev != null && prev.hasValidCoordinates) prev.latitude else 0.0
                        val lng = if (prev != null && prev.hasValidCoordinates) prev.longitude else 0.0
                        connectedEndpoints[endpointId] = name
                        activePeers[endpointId] = P2PPeer(
                            endpointId = endpointId,
                            displayName = name,
                            latitude = lat,
                            longitude = lng,
                            speed = prev?.speed ?: 0f,
                            bearing = prev?.bearing ?: 0f,
                            batteryPercent = battery,
                            bleTag = peerTag.ifBlank { prev?.bleTag ?: "" },
                            lastSeenTimestamp = System.currentTimeMillis()
                        )
                        emitSortedPeers()
                        updateMeshState()
                    }
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
                    connectedEndpoints[endpointId] = peer.displayName
                    activePeers[endpointId] = peer
                    emitSortedPeers()
                    updateMeshState()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error unpacking P2P payload: ${e.message}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    fun sendP2PRingCommand(endpointId: String): P2PRingSendResult {
        if (!connectedEndpoints.containsKey(endpointId)) {
            return P2PRingSendResult.NOT_CONNECTED
        }
        val now = System.currentTimeMillis()
        val lastSent = _p2pRingCooldownsMs.value[endpointId] ?: 0L
        if (now - lastSent < 8_000L) return P2PRingSendResult.COOLDOWN_ACTIVE
        return try {
            val callerBytes = _offlineCallsign.value.toByteArray(StandardCharsets.UTF_8)
            val buffer = ByteBuffer.allocate(1 + callerBytes.size)
            buffer.put(P2P_CMD_RING)
            buffer.put(callerBytes)
            _p2pRingCooldownsMs.value = _p2pRingCooldownsMs.value + (endpointId to now)
            connectionsClient.sendPayload(endpointId, Payload.fromBytes(buffer.array()))
                .addOnFailureListener { ex ->
                    Log.w(TAG, "Async failure sending P2P ring command to $endpointId: ${ex.message}")
                    _p2pRingCooldownsMs.value = _p2pRingCooldownsMs.value - endpointId
                }
            P2PRingSendResult.SENT
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send P2P ring command to $endpointId: ${e.message}")
            _p2pRingCooldownsMs.value = _p2pRingCooldownsMs.value - endpointId
            P2PRingSendResult.FAILED
        }
    }

    private fun sendGhostHideCommand(targets: List<String>) {
        if (targets.isEmpty()) return
        try {
            val payload = Payload.fromBytes(byteArrayOf(P2P_CMD_GHOST_HIDE))
            connectionsClient.sendPayload(targets, payload)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send P2P ghost hide command: ${e.message}")
        }
    }

    fun sendP2PPresenceWithoutFix(displayName: String, batteryPercent: Int) {
        if (_isGhostMode.value) return
        val targets = connectedEndpoints.keys.toList()
        if (targets.isEmpty()) return
        try {
            val tagBytes = bleTag.toByteArray(StandardCharsets.US_ASCII)
            val nameBytes = displayName.toByteArray(StandardCharsets.UTF_8)
            val buffer = ByteBuffer.allocate(1 + 4 + 1 + tagBytes.size + nameBytes.size)
            buffer.put(P2P_CMD_PRESENCE_NO_FIX)
            buffer.putInt(batteryPercent.coerceIn(0, 100))
            buffer.put(tagBytes.size.toByte())
            buffer.put(tagBytes)
            buffer.put(nameBytes)
            connectionsClient.sendPayload(targets, Payload.fromBytes(buffer.array()))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send P2P no-fix presence: ${e.message}")
        }
    }

    fun broadcastP2PLocationDirect(loc: LocationPoint, displayName: String, batteryPercent: Int) {
        broadcastP2PLocation(loc, displayName, batteryPercent)
    }

    private fun broadcastP2PLocation(loc: LocationPoint, displayName: String, batteryPercent: Int) {
        if (_isGhostMode.value) return
        if (!isValidCoordinate(loc.latitude, loc.longitude)) return
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
            buffer.putInt(batteryPercent.coerceIn(0, 100))
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
                ConnectionsStatusCodes.STATUS_RADIO_ERROR ->
                    "Bluetooth or Wi-Fi radio is disabled or unavailable. Enable Bluetooth/Wi-Fi for off-grid P2P discovery."
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
