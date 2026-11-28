package com.geotrace.navigation

import com.geotrace.model.LocationPoint
import java.util.Locale

enum class NavProfile(
    val label: String,
    val orsProfile: String,
    val valhallaCosting: String,
    val fallbackSpeedMps: Double
) {
    DRIVING("Drive", "driving-car", "auto", 12.5),
    MOTORCYCLE("Moto", "driving-car", "motorcycle", 10.5),
    CYCLING("Cycle", "cycling-regular", "bicycle", 4.5),
    WALKING("Walk", "foot-walking", "pedestrian", 1.4)
}

enum class NavRouteSource(val label: String) {
    ONLINE_ORS("OpenRouteService"),
    ONLINE_VALHALLA("Valhalla OSM"),
    OFFLINE_OSM_GRAPH("Offline Road Graph"),
    CACHED_OFFLINE("Offline Route Cache")
}

enum class NavManeuverType {
    START,
    STRAIGHT,
    SLIGHT_LEFT,
    LEFT,
    SHARP_LEFT,
    SLIGHT_RIGHT,
    RIGHT,
    SHARP_RIGHT,
    UTURN,
    ROUNDABOUT_ENTER,
    ROUNDABOUT_EXIT,
    MERGE_KEEP,
    ARRIVE
}

enum class VoiceGuidanceMode(val label: String) {
    VOICE_ON("Voice On"),
    ALERTS_ONLY("Alerts"),
    MUTED("Muted")
}

data class NavStep(
    val index: Int,
    val maneuverType: NavManeuverType,
    val instruction: String,
    val verbalPrompt: String,
    val streetName: String,
    val distanceMeters: Double,
    val durationSeconds: Double,
    val beginPointIndex: Int,
    val endPointIndex: Int,
    val roundaboutExit: Int? = null
)

data class NavRoute(
    val id: String,
    val profile: NavProfile,
    val originLat: Double,
    val originLon: Double,
    val destinationName: String,
    val destinationLat: Double,
    val destinationLon: Double,
    val points: List<LocationPoint>,
    val steps: List<NavStep>,
    val totalDistanceMeters: Double,
    val totalDurationSeconds: Double,
    val source: NavRouteSource,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    val formattedDistance: String get() = formatNavDistance(totalDistanceMeters)
    val formattedDuration: String get() = formatNavDuration(totalDurationSeconds)
}

sealed interface NavPreviewState {
    data object Idle : NavPreviewState

    data class Loading(
        val destinationName: String,
        val destinationLat: Double,
        val destinationLon: Double,
        val profile: NavProfile
    ) : NavPreviewState

    data class Ready(
        val destinationName: String,
        val destinationLat: Double,
        val destinationLon: Double,
        val profile: NavProfile,
        val routes: List<NavRoute>,
        val selectedIndex: Int = 0
    ) : NavPreviewState {
        val selectedRoute: NavRoute?
            get() = routes.getOrNull(selectedIndex) ?: routes.firstOrNull()
    }

    data class Error(
        val destinationName: String,
        val destinationLat: Double,
        val destinationLon: Double,
        val profile: NavProfile,
        val message: String,
        val cachedFallbackRoute: NavRoute? = null
    ) : NavPreviewState
}

data class ActiveNavigationState(
    val route: NavRoute,
    val currentStepIndex: Int,
    val currentStep: NavStep?,
    val nextStep: NavStep?,
    val distanceToNextTurnMeters: Double,
    val remainingDistanceMeters: Double,
    val remainingDurationSeconds: Double,
    val etaEpochMs: Long,
    val nearestPointIndex: Int,
    val crossTrackDistanceMeters: Double,
    val snappedLatitude: Double,
    val snappedLongitude: Double,
    val snappedBearingDegrees: Float,
    val isSnappedToRoad: Boolean,
    val isOffRoute: Boolean,
    val isRerouting: Boolean = false,
    val isOfflineRerouteBlocked: Boolean = false,
    val rerouteStatusNote: String? = null,
    val hasArrived: Boolean = false,
    val voiceMode: VoiceGuidanceMode = VoiceGuidanceMode.VOICE_ON,
    val headingBackToRouteDegrees: Float? = null
)

fun formatNavDistance(meters: Double): String {
    if (!meters.isFinite() || meters < 0.0) return "0 m"
    return when {
        meters < 100.0 -> "${((meters / 5.0).toInt() * 5).coerceAtLeast(0)} m"
        meters < 950.0 -> "${((meters / 10.0).toInt() * 10)} m"
        meters < 10_000.0 -> String.format(Locale.US, "%.1f km", meters / 1000.0)
        else -> String.format(Locale.US, "%.0f km", meters / 1000.0)
    }
}

fun formatNavDuration(seconds: Double): String {
    if (!seconds.isFinite() || seconds <= 0.0) return "1 min"
    val totalMin = ((seconds + 30.0) / 60.0).toInt().coerceAtLeast(1)
    val hrs = totalMin / 60
    val mins = totalMin % 60
    return when {
        hrs == 0 -> "$mins min"
        mins == 0 -> "$hrs hr"
        else -> "$hrs hr $mins min"
    }
}
