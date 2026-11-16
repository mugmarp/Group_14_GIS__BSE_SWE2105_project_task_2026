package com.geotrace.service

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.geotrace.model.GeofenceZone
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "GeofenceManager"

data class GeofenceApiStatus(
    val isEnabled: Boolean = false,
    val isRegisteredWithPlayServices: Boolean = false,
    val registeredZoneCount: Int = 0,
    val academicZoneCount: Int = 0,
    val needsBackgroundPermission: Boolean = false,
    val needsNotificationPermission: Boolean = false,
    val statusMessage: String = "Geofencing API triggers are OFF by default.",
    val errorMessage: String? = null
)

/**
 * Manages OS-level geofence registration via Google Play Services [GeofencingClient]
 * backed by [GeofenceBroadcastReceiver].
 */
class GeofenceManager(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)

    private val _apiStatus = MutableStateFlow(GeofenceApiStatus())
    val apiStatus: StateFlow<GeofenceApiStatus> = _apiStatus.asStateFlow()

    private val geofencePendingIntent: PendingIntent by lazy {
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java).apply {
            action = GeofenceBroadcastReceiver.ACTION_GEOFENCE_EVENT
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        PendingIntent.getBroadcast(context, 4201, intent, flags)
    }

    fun hasFineLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun syncGeofences(zones: List<GeofenceZone>, enabled: Boolean, hysteresisMeters: Float = 10f) {
        if (!enabled) {
            unregisterAllGeofences()
            return
        }

        val fineGranted = hasFineLocationPermission()
        val bgGranted = hasBackgroundLocationPermission()
        val notifGranted = hasNotificationPermission()

        val activeZones = zones
            .filter { it.alertOnEntry || it.alertOnExit }
            .take(100) // Play Services maximum geofences per app is 100

        val academicCount = activeZones.count { it.category.isAcademicZone }

        if (!fineGranted) {
            _apiStatus.value = GeofenceApiStatus(
                isEnabled = true,
                isRegisteredWithPlayServices = false,
                registeredZoneCount = 0,
                academicZoneCount = academicCount,
                needsBackgroundPermission = !bgGranted,
                needsNotificationPermission = !notifGranted,
                statusMessage = "Waiting for Precise Location permission.",
                errorMessage = "Grant Fine Location permission to activate GeofencingClient."
            )
            return
        }

        if (activeZones.isEmpty()) {
            geofencingClient.removeGeofences(geofencePendingIntent)
            _apiStatus.value = GeofenceApiStatus(
                isEnabled = true,
                isRegisteredWithPlayServices = false,
                registeredZoneCount = 0,
                academicZoneCount = 0,
                needsBackgroundPermission = !bgGranted,
                needsNotificationPermission = !notifGranted,
                statusMessage = "Enabled, but no geofence zones have entry/exit alerts selected.",
                errorMessage = null
            )
            return
        }

        val useDwell = hysteresisMeters > 0f
        var invalidZoneCount = 0
        val geofenceList = activeZones.mapNotNull { zone ->
            if (!zone.latitude.isFinite() || !zone.longitude.isFinite() ||
                zone.latitude !in -90.0..90.0 || zone.longitude !in -180.0..180.0 ||
                (zone.latitude == 0.0 && zone.longitude == 0.0)
            ) {
                invalidZoneCount++
                return@mapNotNull null
            }

            var transitionTypes = 0
            if (zone.alertOnEntry) {
                transitionTypes = transitionTypes or if (useDwell) {
                    Geofence.GEOFENCE_TRANSITION_DWELL
                } else {
                    Geofence.GEOFENCE_TRANSITION_ENTER
                }
            }
            if (zone.alertOnExit) {
                transitionTypes = transitionTypes or Geofence.GEOFENCE_TRANSITION_EXIT
            }
            if (transitionTypes == 0) return@mapNotNull null

            val effectiveRadius = if (zone.isInside) {
                (zone.radiusMeters + hysteresisMeters).coerceAtLeast(15f)
            } else {
                (zone.radiusMeters - hysteresisMeters).coerceAtLeast(minOf(15f, zone.radiusMeters * 0.5f))
            }

            try {
                Geofence.Builder()
                    .setRequestId(zone.id)
                    .setCircularRegion(
                        zone.latitude,
                        zone.longitude,
                        effectiveRadius
                    )
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .setTransitionTypes(transitionTypes)
                    .apply {
                        if (useDwell && zone.alertOnEntry) {
                            setLoiteringDelay(8_000)
                        }
                    }
                    .setNotificationResponsiveness(5_000)
                    .build()
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Skipping invalid geofence '${zone.name}': ${e.message}")
                invalidZoneCount++
                null
            }
        }

        if (geofenceList.isEmpty()) {
            unregisterAllGeofences()
            if (invalidZoneCount > 0) {
                _apiStatus.value = _apiStatus.value.copy(
                    isEnabled = true,
                    errorMessage = "Skipped $invalidZoneCount zone(s) with invalid coordinates."
                )
            }
            return
        }

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0) // Trigger only on actual boundary transitions
            .addGeofences(geofenceList)
            .build()

        // Remove previous registrations first, then add fresh set
        geofencingClient.removeGeofences(geofencePendingIntent).addOnCompleteListener {
            try {
                geofencingClient.addGeofences(request, geofencePendingIntent)
                    .addOnSuccessListener {
                        Log.i(TAG, "Registered ${geofenceList.size} geofences ($academicCount academic) with GeofencingClient")
                        _apiStatus.value = GeofenceApiStatus(
                            isEnabled = true,
                            isRegisteredWithPlayServices = true,
                            registeredZoneCount = geofenceList.size,
                            academicZoneCount = academicCount,
                            needsBackgroundPermission = !bgGranted,
                            needsNotificationPermission = !notifGranted,
                            statusMessage = "Geofencing API Active • Monitoring ${geofenceList.size} zones ($academicCount academic)",
                            errorMessage = null
                        )
                    }
                    .addOnFailureListener { ex ->
                        val msg = mapGeofenceError(ex, bgGranted)
                        Log.w(TAG, "GeofencingClient addGeofences failed: $msg")
                        _apiStatus.value = GeofenceApiStatus(
                            isEnabled = true,
                            isRegisteredWithPlayServices = false,
                            registeredZoneCount = 0,
                            academicZoneCount = academicCount,
                            needsBackgroundPermission = !bgGranted,
                            needsNotificationPermission = !notifGranted,
                            statusMessage = "In-app perimeter monitor active; OS background Geofencing API awaiting background permission.",
                            errorMessage = msg
                        )
                    }
            } catch (sec: SecurityException) {
                Log.w(TAG, "SecurityException registering geofences: ${sec.message}")
                _apiStatus.value = GeofenceApiStatus(
                    isEnabled = true,
                    isRegisteredWithPlayServices = false,
                    registeredZoneCount = 0,
                    academicZoneCount = academicCount,
                    needsBackgroundPermission = !bgGranted,
                    needsNotificationPermission = !notifGranted,
                    statusMessage = "Location permission required for OS Geofencing API.",
                    errorMessage = "Missing location permission for GeofencingClient."
                )
            }
        }
    }

    fun unregisterAllGeofences() {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnCompleteListener {
                Log.i(TAG, "Unregistered all OS GeofencingClient triggers")
            }
        _apiStatus.value = GeofenceApiStatus(
            isEnabled = false,
            isRegisteredWithPlayServices = false,
            registeredZoneCount = 0,
            academicZoneCount = 0,
            needsBackgroundPermission = false,
            needsNotificationPermission = false,
            statusMessage = "Geofencing API triggers are OFF by default (0 background battery drain).",
            errorMessage = null
        )
    }

    private fun mapGeofenceError(ex: Exception, hasBackgroundPermission: Boolean): String {
        if (ex is ApiException) {
            return when (ex.statusCode) {
                GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE ->
                    "Play Services Geofencing unavailable on this device (check Google Location Accuracy)."
                GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES ->
                    "Maximum geofence limit exceeded."
                GeofenceStatusCodes.GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION ->
                    if (!hasBackgroundPermission) {
                        "Background Location ('Allow all the time') required for OS Geofencing API triggers."
                    } else {
                        "Insufficient location permission for Geofencing API."
                    }
                else -> "Geofencing API error code ${ex.statusCode}: ${ex.localizedMessage}"
            }
        }
        return ex.localizedMessage ?: "Failed to register geofences."
    }
}
