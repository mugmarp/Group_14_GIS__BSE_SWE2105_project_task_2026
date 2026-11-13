package com.geotrace.data

import android.content.Context
import android.util.Log
import com.geotrace.R
import com.geotrace.model.Group
import com.geotrace.model.LocationPoint
import com.geotrace.model.MemberLocation
import com.geotrace.model.P2PMeshState
import com.geotrace.model.P2PPeer
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
private const val P2P_PAYLOAD_VERSION: Byte = 2

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

    // Phase 2: Nearby P2P Peers & Connection Pipeline State
    private val _nearbyPeers = MutableStateFlow<List<P2PPeer>>(emptyList())
    val nearbyPeers = _nearbyPeers.asStateFlow()

    private val _p2pMeshState = MutableStateFlow(P2PMeshState())
    val p2pMeshState = _p2pMeshState.asStateFlow()

    private val _offlineCallsign = MutableStateFlow(loadOrInitOfflineCallsign())
    val offlineCallsign = _offlineCallsign.asStateFlow()

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

    var onPeerConnectedCallback: ((String) -> Unit)? = null

    private fun loadOrInitOfflineCallsign(): String {
        val saved = prefs.getString("offline_p2p_callsign", null)
        if (!saved.isNullOrBlank()) return saved
        val modelTag = android.os.Build.MODEL
            .filter { it.isLetterOrDigit() }
            .take(5)
            .uppercase()
            .ifBlank { "FIELD" }
        val suffix = UUID.randomUUID().toString().take(4).uppercase()
        val generated = "Geo-$modelTag-$suffix"
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
        return auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: _offlineCallsign.value
    }

    /**
     * Create a new group circle with a unique short invite code.
     */
    suspend fun createGroup(name: String, displayName: String): Result<Group> {
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
     * Join an existing group circle via its 6-character code (e.g., FAM-7K2).
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
     * Observe all groups that the current user is a member of.
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
                    Log.e(TAG, "Error observing user groups: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                val groups = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(Group::class.java)?.copy(id = doc.id)
                } ?: emptyList()
                trySend(groups)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Leave a group circle.
     */
    suspend fun leaveGroup(groupId: String): Result<Unit> {
        val uid = auth.currentUser?.uid
            ?: return Result.failure(IllegalStateException("Must be signed in to leave a group."))

        return try {
            db.collection("groups").document(groupId)
                .update("memberIds", FieldValue.arrayRemove(uid))
                .await()
            // Also clean up member's live location record
            try {
                db.collection("groups").document(groupId)
                    .collection("live_locations").document(uid)
                    .delete()
                    .await()
            } catch (ignored: Exception) {}
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to leave group: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Publish current device's live location & telemetry to a group's live_locations collection.
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
            val docRef = db.collection("groups").document(groupId)
                .collection("live_locations").document(uid)

            val payload = mapOf(
                "userId" to uid,
                "displayName" to displayName,
                "avatarColor" to avatarColor,
                "latitude" to location.latitude,
                "longitude" to location.longitude,
                "accuracy" to location.accuracy,
                "speed" to location.speed,
                "bearing" to location.bearing,
                "altitude" to location.altitude,
                "gait" to (location.gaitLabel ?: "Stationary"),
                "batteryPercent" to batteryPercent,
                "isCharging" to isCharging,
                "trackingMode" to trackingMode,
                "isSharingEnabled" to isSharingEnabled,
                "lastUpdated" to FieldValue.serverTimestamp()
            )

            docRef.set(payload).await()

            // Also broadcast via Nearby P2P mesh if active AND Ghost Mode is NOT freezing sharing
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
     * Real-time stream of all member locations within a specific group.
     */
    fun observeGroupLocations(groupId: String): Flow<List<MemberLocation>> = callbackFlow {
        val listener: ListenerRegistration = db.collection("groups").document(groupId)
            .collection("live_locations")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing group locations: ${error.message}")
                    close(error)
                    return@addSnapshotListener
                }
                val locations = snapshot?.documents?.mapNotNull { doc ->
                    doc.toObject(MemberLocation::class.java)?.copy(id = doc.id)
                }?.filter { it.isSharingEnabled } ?: emptyList()

                trySend(locations)
            }

        awaitClose { listener.remove() }
    }

    // -----------------------------------------------------------------------------------------
    // Phase 2: Nearby Connections P2P Mesh (Wilderness Off-Grid Tracking)
    // -----------------------------------------------------------------------------------------

    fun startP2PMesh(displayName: String) {
        val myName = displayName.ifBlank { getCurrentUserDisplayName() }
        isP2PMeshRequested = true
        lastP2PError = null
        updateMeshState()
        startAdvertising(myName)
        startDiscovery(myName)
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
            connCount > 0 -> "Connected to $connCount peer(s); exchanging GPS coordinates..."
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
                    Log.d(TAG, "Discovered P2P endpoint: $endpointId ($remoteName)")

                    if (connectedEndpoints.containsKey(endpointId) || !pendingConnections.add(endpointId)) {
                        return
                    }

                    // Tie-breaker delay to avoid simultaneous P2P_CLUSTER requestConnection collisions
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
                    // Keep activePeers only if still connected; otherwise remove stale endpoint
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
            Log.i(TAG, "Nearby discovery started")
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
            Log.w(TAG, "Nearby discovery result: code=$code, err=$lastP2PError")
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
                    Log.i(TAG, "P2P connected to $endpointId (${connectedEndpoints[endpointId]})")
                    // Immediately push our location telemetry to the newly connected peer!
                    onPeerConnectedCallback?.invoke(endpointId)
                } else {
                    Log.w(TAG, "P2P connection rejected/failed for $endpointId (code=$statusCode)")
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
            try {
                val buffer = ByteBuffer.wrap(bytes)
                val peer = if (buffer.remaining() >= 29 && bytes[0] == P2P_PAYLOAD_VERSION) {
                    buffer.get() // version byte
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
                    // Backward-compatible v1 packet
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

                if (peer != null) {
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

    fun broadcastP2PLocationDirect(loc: LocationPoint, displayName: String, batteryPercent: Int) {
        broadcastP2PLocation(loc, displayName, batteryPercent)
    }

    private fun broadcastP2PLocation(loc: LocationPoint, displayName: String, batteryPercent: Int) {
        val targets = connectedEndpoints.keys.toList()
        if (targets.isEmpty()) return
        try {
            val nameBytes = displayName.toByteArray(StandardCharsets.UTF_8)
            val buffer = ByteBuffer.allocate(1 + 8 + 8 + 4 + 4 + 4 + nameBytes.size)
            buffer.put(P2P_PAYLOAD_VERSION)
            buffer.putDouble(loc.latitude)
            buffer.putDouble(loc.longitude)
            buffer.putFloat(loc.speed)
            buffer.putFloat(loc.bearing)
            buffer.putInt(batteryPercent)
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
