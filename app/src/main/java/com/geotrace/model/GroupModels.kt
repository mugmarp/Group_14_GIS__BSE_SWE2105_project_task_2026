package com.geotrace.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

/**
 * Group or Circle representing a family or team tracking circle.
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
 * Real-time live telemetry published by an individual member within a group.
 */
@IgnoreExtraProperties
data class MemberLocation(
    @DocumentId val id: String = "",
    val userId: String = "",
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
    /**
     * Compute approximate human-readable distance from user's current location.
     */
    fun distanceToMeters(userLat: Double, userLng: Double): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            userLat, userLng,
            this.latitude, this.longitude,
            results
        )
        return results[0]
    }

    /**
     * Compute bearing degrees from user to this member.
     */
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
 * Peer-to-Peer discovered device via Android Nearby Connections (Phase 2 Wilderness Mesh).
 */
data class P2PPeer(
    val endpointId: String,
    val displayName: String,
    val latitude: Double,
    val longitude: Double,
    val speed: Float = 0f,
    val batteryPercent: Int = 100,
    val distanceMeters: Float = 0f,
    val bearingDegrees: Float = 0f,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
)
