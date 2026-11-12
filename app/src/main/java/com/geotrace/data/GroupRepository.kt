package com.geotrace.data

import android.content.Context
import android.util.Log
import com.geotrace.R
import com.geotrace.model.Group
import com.geotrace.model.LocationPoint
import com.geotrace.model.MemberLocation
import com.geotrace.model.P2PPeer
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID

private const val TAG = "GroupRepository"
private const val NEARBY_SERVICE_ID = "com.geotrace.p2p.mesh"

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
    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context)
    }

    // Phase 2: Nearby P2P Peers state
    private val _nearbyPeers = MutableStateFlow<List<P2PPeer>>(emptyList())
    val nearbyPeers = _nearbyPeers.asStateFlow()

    private val activeEndpoints = mutableMapOf<String, P2PPeer>()
    private var isP2PAdvertising = false
    private var isP2PDiscovering = false

    fun getCurrentUserId(): String? = auth.currentUser?.uid

    fun getCurrentUserDisplayName(): String {
        return auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.email?.substringBefore("@")
            ?: "User-${(auth.currentUser?.uid?.take(4) ?: "Local").uppercase()}"
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

            // Also broadcast via Nearby P2P mesh if active
            if (isP2PAdvertising || isP2PDiscovering) {
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
        startAdvertising(myName)
        startDiscovery()
    }

    fun stopP2PMesh() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
            isP2PAdvertising = false
            isP2PDiscovering = false
            activeEndpoints.clear()
            _nearbyPeers.value = emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping P2P mesh: ${e.message}")
        }
    }

    private fun startAdvertising(displayName: String) {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startAdvertising(
            displayName,
            NEARBY_SERVICE_ID,
            object : ConnectionLifecycleCallback() {
                override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
                    connectionsClient.acceptConnection(endpointId, payloadCallback)
                }

                override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
                    Log.d(TAG, "P2P connection result for $endpointId: ${result.status.statusCode}")
                }

                override fun onDisconnected(endpointId: String) {
                    activeEndpoints.remove(endpointId)
                    _nearbyPeers.value = activeEndpoints.values.toList()
                }
            },
            advertisingOptions
        ).addOnSuccessListener {
            isP2PAdvertising = true
            Log.i(TAG, "Nearby advertising started as '$displayName'")
        }.addOnFailureListener {
            isP2PAdvertising = false
            Log.w(TAG, "Nearby advertising failed: ${it.message}")
        }
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER)
            .build()

        connectionsClient.startDiscovery(
            NEARBY_SERVICE_ID,
            object : EndpointDiscoveryCallback() {
                override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                    Log.d(TAG, "Discovered P2P endpoint: $endpointId (${info.endpointName})")
                    connectionsClient.requestConnection(
                        getCurrentUserDisplayName(),
                        endpointId,
                        object : ConnectionLifecycleCallback() {
                            override fun onConnectionInitiated(id: String, info: ConnectionInfo) {
                                connectionsClient.acceptConnection(id, payloadCallback)
                            }
                            override fun onConnectionResult(id: String, res: ConnectionResolution) {}
                            override fun onDisconnected(id: String) {
                                activeEndpoints.remove(id)
                                _nearbyPeers.value = activeEndpoints.values.toList()
                            }
                        }
                    )
                }

                override fun onEndpointLost(endpointId: String) {
                    activeEndpoints.remove(endpointId)
                    _nearbyPeers.value = activeEndpoints.values.toList()
                }
            },
            discoveryOptions
        ).addOnSuccessListener {
            isP2PDiscovering = true
            Log.i(TAG, "Nearby discovery started")
        }.addOnFailureListener {
            isP2PDiscovering = false
            Log.w(TAG, "Nearby discovery failed: ${it.message}")
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            val bytes = payload.asBytes() ?: return
            try {
                // Struct: [Double lat, Double lng, Float speed, Int battery, UTF8 name]
                val buffer = ByteBuffer.wrap(bytes)
                if (buffer.remaining() >= 8 + 8 + 4 + 4) {
                    val lat = buffer.double
                    val lng = buffer.double
                    val speed = buffer.float
                    val battery = buffer.int
                    val nameBytes = ByteArray(buffer.remaining())
                    buffer.get(nameBytes)
                    val name = String(nameBytes, StandardCharsets.UTF_8).ifBlank { "Nearby Peer" }

                    val peer = P2PPeer(
                        endpointId = endpointId,
                        displayName = name,
                        latitude = lat,
                        longitude = lng,
                        speed = speed,
                        batteryPercent = battery,
                        lastSeenTimestamp = System.currentTimeMillis()
                    )
                    activeEndpoints[endpointId] = peer
                    _nearbyPeers.value = activeEndpoints.values.toList()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error unpacking P2P payload: ${e.message}")
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    private fun broadcastP2PLocation(loc: LocationPoint, displayName: String, batteryPercent: Int) {
        if (activeEndpoints.isEmpty()) return
        try {
            val nameBytes = displayName.toByteArray(StandardCharsets.UTF_8)
            val buffer = ByteBuffer.allocate(8 + 8 + 4 + 4 + nameBytes.size)
            buffer.putDouble(loc.latitude)
            buffer.putDouble(loc.longitude)
            buffer.putFloat(loc.speed)
            buffer.putInt(batteryPercent)
            buffer.put(nameBytes)

            val payload = Payload.fromBytes(buffer.array())
            connectionsClient.sendPayload(activeEndpoints.keys.toList(), payload)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to broadcast P2P location: ${e.message}")
        }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val prefix = (1..3).map { chars.random() }.joinToString("")
        val suffix = (1..3).map { chars.random() }.joinToString("")
        return "$prefix-$suffix"
    }
}
