package com.example.gistracker.model

enum class TrackingMode(
    val title: String,
    val description: String,
    val intervalMs: Long,
    val minDistanceMeters: Float,
    val expectedAccuracy: String,
    val batteryImpact: String
) {
    HIGH_ACCURACY(
        title = "High Accuracy (GPS)",
        description = "Continuous multi-constellation GNSS (GPS/Galileo/GLONASS) with 1s updates. Best for active movement.",
        intervalMs = 1000L,
        minDistanceMeters = 2f,
        expectedAccuracy = "2 – 5 meters",
        batteryImpact = "High (~20-30 mA)"
    ),
    BALANCED(
        title = "Balanced Power (Fused)",
        description = "Fused location prioritizing Wi-Fi & cellular networks with 10s updates. Great for urban tracking.",
        intervalMs = 10000L,
        minDistanceMeters = 15f,
        expectedAccuracy = "10 – 30 meters",
        batteryImpact = "Moderate (~5-10 mA)"
    ),
    POWER_SAVER(
        title = "Power Saver (Cell Only)",
        description = "Coarse cellular tower updates with 30s intervals. Maximizes battery for long-term tracking.",
        intervalMs = 30000L,
        minDistanceMeters = 50f,
        expectedAccuracy = "100 – 1,000 meters",
        batteryImpact = "Minimal (< 2 mA)"
    )
}

data class LocationPoint(
    val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val speed: Float = 0f, // in m/s
    val altitude: Double = 0.0,
    val bearing: Float = 0f,
    val provider: String = "gps",
    val timestamp: Long = System.currentTimeMillis(),
    val sessionId: String
) {
    val speedKmh: Float get() = speed * 3.6f
}

data class TrackingSession(
    val id: String,
    val name: String,
    val startTime: Long,
    val endTime: Long? = null,
    val totalDistanceMeters: Double = 0.0,
    val avgSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val pointCount: Int = 0,
    val trackingMode: TrackingMode = TrackingMode.HIGH_ACCURACY,
    val isRecording: Boolean = false
) {
    val durationSeconds: Long
        get() {
            val end = endTime ?: System.currentTimeMillis()
            return ((end - startTime) / 1000).coerceAtLeast(0)
        }

    val formattedDuration: String
        get() {
            val totalSec = durationSeconds
            val hours = totalSec / 3600
            val minutes = (totalSec % 3600) / 60
            val seconds = totalSec % 60
            return if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }
        }

    val formattedDistance: String
        get() = if (totalDistanceMeters >= 1000) {
            String.format("%.2f km", totalDistanceMeters / 1000.0)
        } else {
            String.format("%.0f m", totalDistanceMeters)
        }
}

data class CellTowerInfo(
    val type: String, // LTE, GSM, WCDMA, NR
    val mcc: Int?, // Mobile Country Code (e.g. 641 for Uganda)
    val mnc: Int?, // Mobile Network Code (10 for MTN, 22 for Airtel)
    val cid: Long?, // Cell Identity
    val lac: Int?, // Location Area Code / TAC
    val signalDbm: Int?, // e.g. -85 dBm
    val signalLevel: Int, // 0 to 4 bars
    val isRegistered: Boolean,
    val operatorName: String?,
    val timingAdvance: Int? = null,
    val estimatedDistanceKm: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val isDemonstrationData: Boolean = false
) {
    val operatorDisplayName: String
        get() = when {
            operatorName != null && operatorName.isNotBlank() -> operatorName
            mcc == 641 && (mnc == 10 || mnc == 14) -> "MTN Uganda (PLMN 64110)"
            mcc == 641 && (mnc == 22 || mnc == 1) -> "Airtel Uganda (PLMN 64122)"
            mcc == 641 && mnc == 11 -> "Uganda Telecom (UTL)"
            mcc in 310..316 -> "Android Virtual Radio / US Test (PLMN $mcc-$mnc)"
            mcc != null && mnc != null -> "PLMN $mcc-$mnc"
            else -> "Unknown Cellular Network"
        }

    val countryDisplayName: String
        get() = when (mcc) {
            641 -> "Uganda"
            in 310..316 -> "USA / Virtual"
            640 -> "Tanzania"
            639 -> "Kenya"
            635 -> "Rwanda"
            null -> "N/A"
            else -> "MCC $mcc"
        }
}

enum class GeofenceCategory(val label: String, val iconEmoji: String, val defaultColor: String) {
    SAFE_ZONE("Safe Zone", "🛡️", "#006948"),
    CAMPUS("University Campus", "🎓", "#006948"),
    LIBRARY("Main Library", "📚", "#0051D5"),
    HOSTEL("Student Hostel", "🏠", "#7C3AED"),
    SCHOOL("Academic Faculty", "🏫", "#006194"),
    HOME("Home Residence", "🏡", "#006948"),
    WORK("Workplace", "🏢", "#0051D5"),
    BODA_HOTSPOT("Boda Boda Hotspot", "🏍️", "#D97706"),
    CUSTOM("Custom Zone", "📍", "#006194")
}

data class GeofenceZone(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val category: GeofenceCategory = GeofenceCategory.SAFE_ZONE,
    val colorHex: String = "#00E5FF",
    val alertOnEntry: Boolean = true,
    val alertOnExit: Boolean = true,
    val isInside: Boolean = false,
    val lastTransitionTime: Long? = null
)

data class GeofenceAlert(
    val id: String,
    val zoneName: String,
    val eventType: String, // "ENTERED" or "EXITED"
    val timestamp: Long = System.currentTimeMillis()
)
