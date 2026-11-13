package com.geotrace.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Group representing a shared multi-user or multi-device tracking group.
 */
@IgnoreExtraProperties
data class Group(
    @DocumentId val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val createdBy: String = "",
    val memberIds: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
)

/**
 * Real-time live telemetry published by an individual device within a group.
 * Document ID uses `{userId}_{deviceId}` so a user with multiple devices does not overwrite themselves.
 */
@IgnoreExtraProperties
data class MemberLocation(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceId: String = "",
    val deviceModel: String = "",
    val bleTag: String = "",
    val displayName: String = "",
    val avatarColor: String = "#2563EB",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val altitude: Double = 0.0,
    val gait: String = "Stationary",
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val trackingMode: String = "BALANCED",
    val isSharingEnabled: Boolean = true,
    @ServerTimestamp val lastUpdated: Timestamp? = null
) {
    fun distanceToMeters(userLat: Double, userLng: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            userLat, userLng,
            this.latitude, this.longitude,
            results
        )
        return results[0]
    }

    fun bearingFrom(userLat: Double, userLng: Double): Float {
        val userLoc = android.location.Location("user").apply {
            latitude = userLat
            longitude = userLng
        }
        val memberLoc = android.location.Location("member").apply {
            latitude = this@MemberLocation.latitude
            longitude = this@MemberLocation.longitude
        }
        return userLoc.bearingTo(memberLoc)
    }
}

/**
 * A personal phone or tablet signed into the user's Google account under `/users/{uid}/devices/{deviceId}`.
 */
@IgnoreExtraProperties
data class PersonalDevice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val deviceModel: String = "",
    val bleTag: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val trackingMode: String = "BALANCED",
    val ringRequestedAt: Long = 0L,
    val ringRequestedBy: String = "",
    @ServerTimestamp val lastUpdated: Timestamp? = null
) {
    fun distanceToMeters(userLat: Double, userLng: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            userLat, userLng,
            this.latitude, this.longitude,
            results
        )
        return results[0]
    }

    fun bearingFrom(userLat: Double, userLng: Double): Float {
        val userLoc = android.location.Location("user").apply {
            latitude = userLat
            longitude = userLng
        }
        val devLoc = android.location.Location("device").apply {
            latitude = this@PersonalDevice.latitude
            longitude = this@PersonalDevice.longitude
        }
        return userLoc.bearingTo(devLoc)
    }
}

/**
 * Peer-to-Peer discovered device via Android Nearby Connections.
 */
data class P2PPeer(
    val endpointId: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float = 0f,
    val bearing: Float = 0f,
    val batteryPercent: Int = 100,
    val bleTag: String = "",
    val distanceMeters: Float = 0f,
    val bearingDegrees: Float = 0f,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    fun distanceToMeters(userLat: Double, userLng: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            userLat, userLng,
            this.latitude, this.longitude,
            results
        )
        return results[0]
    }

    fun bearingFrom(userLat: Double, userLng: Double): Float {
        val userLoc = android.location.Location("user").apply {
            latitude = userLat
            longitude = userLng
        }
        val peerLoc = android.location.Location("peer").apply {
            latitude = this@P2PPeer.latitude
            longitude = this@P2PPeer.longitude
        }
        return userLoc.bearingTo(peerLoc)
    }
}

/**
 * Live diagnostic telemetry for the off-grid Nearby Connections discovery and connection pipeline.
 */
data class P2PMeshState(
    val isActive: Boolean = false,
    val isAdvertising: Boolean = false,
    val isDiscovering: Boolean = false,
    val discoveredCount: Int = 0,
    val connectedCount: Int = 0,
    val discoveredNames: List<String> = emptyList(),
    val statusText: String = "Off-grid P2P scanner idle",
    val errorText: String? = null
)
