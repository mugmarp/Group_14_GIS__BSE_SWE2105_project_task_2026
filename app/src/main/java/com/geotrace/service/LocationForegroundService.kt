package com.geotrace.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.geotrace.LocationTrackerApp
import com.geotrace.MainActivity
import com.geotrace.model.LocationPoint
import com.geotrace.model.TrackingMode
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class LocationForegroundService : Service() {

    companion object {
        private const val TAG = "LocationFgService"
        const val CHANNEL_ID = "gis_tracker_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_UPDATE_MODE = "ACTION_UPDATE_MODE"
        const val ACTION_PAUSE = "ACTION_PAUSE_TRACKING"
        const val ACTION_RESUME = "ACTION_RESUME_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val ACTION_START_PROTECTION = "ACTION_START_PROTECTION"
        const val ACTION_STOP_PROTECTION = "ACTION_STOP_PROTECTION"
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_PERSIST_DISABLE = "EXTRA_PERSIST_DISABLE"
        const val EXTRA_STARTED_AS_FOREGROUND = "EXTRA_STARTED_AS_FOREGROUND"

        fun hasLocationPermission(context: Context): Boolean {
            val fine = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            val coarse = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
            return fine || coarse
        }

        fun hasBackgroundLocationPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        }

        fun startService(context: Context, mode: TrackingMode) {
            if (!hasLocationPermission(context)) return
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode.name)
            }
            safeStartForegroundService(context, intent)
        }

        @Volatile
        var isServiceActive: Boolean = false
            private set

        fun updateMode(context: Context, mode: TrackingMode) {
            if (!isServiceActive || !hasLocationPermission(context)) return
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_UPDATE_MODE
                putExtra(EXTRA_MODE, mode.name)
            }
            safeStartForegroundService(context, intent)
        }

        fun stopTrackingOrDowngrade(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // Service wasn't running
            }
        }

        fun pauseTracking(context: Context) {
            if (!isServiceActive) return
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_PAUSE
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun resumeTracking(context: Context) {
            if (!hasLocationPermission(context)) return
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_RESUME
            }
            safeStartForegroundService(context, intent)
        }

        fun startProtectionMode(context: Context): Boolean {
            if (!hasLocationPermission(context)) return false
            if (Firebase.auth.currentUser == null) return false
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START_PROTECTION
            }
            return safeStartForegroundService(context, intent)
        }

        fun stopProtectionMode(context: Context, disablePreference: Boolean = true) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP_PROTECTION
                putExtra(EXTRA_PERSIST_DISABLE, disablePreference)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // Service wasn't running
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, LocationForegroundService::class.java))
        }

        private fun safeStartForegroundService(context: Context, intent: Intent): Boolean {
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    intent.putExtra(EXTRA_STARTED_AS_FOREGROUND, true)
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                Log.w(TAG, "Unable to start foreground service (${intent.action}): ${e.message}")
                false
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var systemLocationManager: LocationManager? = null
    private var networkLocationListener: LocationListener? = null
    private lateinit var cellManager: CellularDiagnosticManager
    private var locationCallback: LocationCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var cellDiagnosticsJob: Job? = null
    private var protectionHeartbeatJob: Job? = null
    private var currentMode: TrackingMode = TrackingMode.HIGH_ACCURACY
    private var isForegroundStarted: Boolean = false
    private var isRecordingSession: Boolean = false
    private var isProtectionActive: Boolean = false
    @Volatile
    private var lastCloudSyncMs: Long = 0L
    @Volatile
    private var lastModeSwitchMs: Long = 0L
    private var lastBgCellularFallbackKey: String? = null
    private var lastBgCellularFallbackMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        isServiceActive = true
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        systemLocationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        cellManager = CellularDiagnosticManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as LocationTrackerApp
        val repo = app.repository
        val groupRepo = app.groupRepository
        val startedAsForeground = intent?.getBooleanExtra(EXTRA_STARTED_AS_FOREGROUND, false) == true

        when (intent?.action) {
            ACTION_START -> {
                val modeStr = intent.getStringExtra(EXTRA_MODE) ?: repo.trackingMode.value.name
                val mode = parseMode(modeStr)
                currentMode = mode
                lastModeSwitchMs = System.currentTimeMillis()
                isRecordingSession = true
                isProtectionActive = groupRepo.isBackgroundProtectionActiveAndSignedIn()
                startForegroundTracking(mode)
            }
            ACTION_UPDATE_MODE -> {
                val modeStr = intent.getStringExtra(EXTRA_MODE) ?: repo.trackingMode.value.name
                val mode = parseMode(modeStr)
                currentMode = mode
                lastModeSwitchMs = System.currentTimeMillis()
                if (isRecordingSession || repo.activeSession.value != null) {
                    ensureForeground(buildStatusNotification(mode))
                    requestLocationUpdates(mode)
                } else if (isProtectionActive || (groupRepo.isBackgroundProtectionActiveAndSignedIn() && hasLocationPermission(this))) {
                    ensureForeground(buildStatusNotification(mode))
                    requestProtectionLocationUpdates()
                } else {
                    if (startedAsForeground && !isForegroundStarted && hasLocationPermission(this)) {
                        ensureForeground(buildStatusNotification(mode))
                    }
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                }
            }
            ACTION_START_PROTECTION -> {
                if (!groupRepo.isBackgroundProtectionActiveAndSignedIn() || !hasLocationPermission(this)) {
                    if (!isRecordingSession && repo.activeSession.value == null) {
                        if (startedAsForeground && !isForegroundStarted && hasLocationPermission(this)) {
                            ensureForeground(buildStatusNotification(currentMode))
                        }
                        stopForegroundAndSelf()
                        return START_NOT_STICKY
                    }
                    ensureForeground(buildStatusNotification(currentMode))
                    return START_STICKY
                }
                isProtectionActive = true
                groupRepo.ensurePersonalDevicesListenerActive()
                if (!isRecordingSession && repo.activeSession.value == null) {
                    startLightweightProtectionMode()
                } else {
                    ensureForeground(buildStatusNotification(currentMode))
                }
            }
            ACTION_STOP_PROTECTION -> {
                val persistDisable = intent.getBooleanExtra(EXTRA_PERSIST_DISABLE, true)
                if (persistDisable) {
                    groupRepo.setBackgroundProtectionEnabled(false)
                }
                isProtectionActive = false
                protectionHeartbeatJob?.cancel()
                protectionHeartbeatJob = null
                if (!isRecordingSession && repo.activeSession.value == null) {
                    stopLocationUpdatesOnly()
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                } else {
                    ensureForeground(buildStatusNotification(currentMode))
                }
            }
            ACTION_PAUSE -> {
                serviceScope.launch {
                    repo.pauseCurrentSession()
                    ensureForeground(buildStatusNotification(currentMode))
                }
            }
            ACTION_RESUME -> {
                serviceScope.launch {
                    repo.resumeCurrentSession()
                    isRecordingSession = true
                    ensureForeground(buildStatusNotification(currentMode))
                }
            }
            ACTION_STOP -> {
                stopTrackingOrDowngradeInternal()
            }
            null -> {
                // Restarted by system (START_STICKY) after process kill
                if (repo.activeSession.value != null) {
                    isRecordingSession = true
                    currentMode = repo.trackingMode.value
                    startForegroundTracking(currentMode)
                } else if (groupRepo.isBackgroundProtectionActiveAndSignedIn() && hasLocationPermission(this)) {
                    isProtectionActive = true
                    groupRepo.ensurePersonalDevicesListenerActive()
                    startLightweightProtectionMode()
                } else {
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                }
            }
        }
        return START_STICKY
    }

    private fun parseMode(raw: String): TrackingMode {
        return try {
            TrackingMode.valueOf(raw)
        } catch (_: Exception) {
            TrackingMode.HIGH_ACCURACY
        }
    }

    private fun startForegroundTracking(mode: TrackingMode) {
        val groupRepo = (application as LocationTrackerApp).groupRepository
        if (groupRepo.getCurrentUserId() != null) {
            groupRepo.ensurePersonalDevicesListenerActive()
        }
        ensureForeground(buildStatusNotification(mode))
        acquireWakeLock()
        requestLocationUpdates(mode)
        startPeriodicCellDiagnostics()
        stopProtectionHeartbeat()
    }

    @SuppressLint("MissingPermission")
    private fun startLightweightProtectionMode() {
        val app = application as LocationTrackerApp
        val repo = app.repository
        val groupRepo = app.groupRepository

        currentMode = repo.trackingMode.value
        ensureForeground(buildStatusNotification(currentMode))
        releaseWakeLock()
        cellDiagnosticsJob?.cancel()
        cellDiagnosticsJob = null
        if (currentMode == TrackingMode.POWER_SAVER) {
            startPeriodicCellDiagnostics()
        }
        requestProtectionLocationUpdates()
        startProtectionHeartbeat()

        // Seed immediate device visibility using existing fix or lastLocation (deduped against recent VM sync)
        serviceScope.launch {
            val latestRepoSync = groupRepo.lastSyncTimestampMs.value ?: 0L
            val existing = repo.currentLocation.value
            if (existing != null) {
                val now = System.currentTimeMillis()
                if (now - maxOf(lastCloudSyncMs, latestRepoSync) >= 10_000L) {
                    lastCloudSyncMs = now
                    groupRepo.syncDeviceAndGroupTelemetry(existing, repo.trackingMode.value.name)
                }
            } else {
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        if (loc != null) {
                            val point = LocationPoint(
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                accuracy = loc.accuracy,
                                speed = if (loc.hasSpeed()) loc.speed else 0f,
                                altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
                                bearing = if (loc.hasBearing()) loc.bearing else 0f,
                                provider = loc.provider ?: "fused",
                                timestamp = loc.time,
                                sessionId = "protection"
                            )
                            serviceScope.launch {
                                repo.recordLocation(point)
                                val now = System.currentTimeMillis()
                                val latest = maxOf(lastCloudSyncMs, groupRepo.lastSyncTimestampMs.value ?: 0L)
                                if (now - latest >= 10_000L) {
                                    lastCloudSyncMs = now
                                    groupRepo.syncDeviceAndGroupTelemetry(point, repo.trackingMode.value.name)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun ensureForeground(notification: Notification) {
        if (!isForegroundStarted) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                isForegroundStarted = true
            } catch (e: Exception) {
                Log.e(TAG, "startForeground failed: ${e.message}")
            }
        } else {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildStatusNotification(mode: TrackingMode): Notification {
        val app = application as LocationTrackerApp
        val repo = app.repository
        val groupRepo = app.groupRepository
        val activeSession = repo.activeSession.value
        val calibratedPoint = repo.currentLocation.value

        if (activeSession != null || isRecordingSession) {
            if (activeSession?.isPaused == true) {
                return buildNotification(
                    title = "Paused: ${activeSession.name}",
                    content = "Dist: ${activeSession.formattedDistance} • ${activeSession.pointCount} pts • Tap Resume when ready",
                    isRecordingUi = true,
                    isPausedUi = true
                )
            }
            return if (calibratedPoint != null && activeSession != null) {
                val speedKmh = calibratedPoint.speedKmh
                val gait = calibratedPoint.gaitLabel
                val distFormatted = activeSession.formattedDistance
                val title = "Tracking (${mode.title}): ${activeSession.name}"
                val content = "Dist: $distFormatted | Speed: ${String.format(Locale.US, "%.1f", speedKmh)} km/h ($gait) | Acc: ±${calibratedPoint.accuracy.toInt()}m"
                buildNotification(title, content, isRecordingUi = true, isPausedUi = false)
            } else {
                buildNotification(
                    title = "GeoTrace Active (${mode.title})",
                    content = "Acquiring live location telemetry...",
                    isRecordingUi = true,
                    isPausedUi = false
                )
            }
        }

        val callsign = groupRepo.offlineCallsign.value
        val privacyTag = if (groupRepo.isGhostMode.value) "Ghost Mode (My Devices only)" else "Fleet & Remote Ring ready"
        return buildNotification(
            title = "GeoTrace 24/7 Protection • $callsign",
            content = "$privacyTag • Locatable even when closed",
            isRecordingUi = false,
            isPausedUi = false
        )
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock == null) {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "geotrace:foreground_tracking_wakelock"
                )?.apply {
                    setReferenceCounted(false)
                }
            }
            wakeLock?.acquire(12 * 60 * 60 * 1000L)
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(mode: TrackingMode) {
        stopLocationUpdatesOnly()

        when (mode) {
            TrackingMode.HIGH_ACCURACY -> {
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, mode.intervalMs)
                    .setMinUpdateDistanceMeters(mode.minDistanceMeters)
                    .setMinUpdateIntervalMillis(mode.intervalMs / 2)
                    .build()

                locationCallback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        for (loc in result.locations) {
                            onNewLocationReceived(loc)
                        }
                    }
                }
                try {
                    fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
                } catch (_: SecurityException) {
                }
            }

            TrackingMode.BALANCED -> {
                val lm = systemLocationManager
                val networkEnabled = try {
                    lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
                } catch (_: Exception) {
                    false
                }

                if (networkEnabled && lm != null) {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            onNewLocationReceived(location)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    networkLocationListener = listener
                    try {
                        lm.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            mode.intervalMs,
                            mode.minDistanceMeters,
                            listener,
                            Looper.getMainLooper()
                        )
                    } catch (_: SecurityException) {
                    }
                } else {
                    val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, mode.intervalMs)
                        .setMinUpdateDistanceMeters(mode.minDistanceMeters)
                        .setMinUpdateIntervalMillis(mode.intervalMs)
                        .build()
                    locationCallback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let { onNewLocationReceived(it) }
                        }
                    }
                    try {
                        fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
                    } catch (_: SecurityException) {
                    }
                }
            }

            TrackingMode.POWER_SAVER -> {
                // Strictly avoid GPS & Fused GNSS reuse in Cell-Only mode; use Network coarse fallback only if OpenCellID misses
                val lm = systemLocationManager
                val networkEnabled = try {
                    lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
                } catch (_: Exception) {
                    false
                }
                if (networkEnabled && lm != null) {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            onNewLocationReceived(location)
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    networkLocationListener = listener
                    try {
                        lm.requestLocationUpdates(
                            LocationManager.NETWORK_PROVIDER,
                            mode.intervalMs,
                            mode.minDistanceMeters,
                            listener,
                            Looper.getMainLooper()
                        )
                    } catch (_: SecurityException) {
                    }
                } else {
                    val request = LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, mode.intervalMs)
                        .setMinUpdateDistanceMeters(mode.minDistanceMeters)
                        .setMinUpdateIntervalMillis(mode.intervalMs)
                        .build()
                    locationCallback = object : LocationCallback() {
                        override fun onLocationResult(result: LocationResult) {
                            result.lastLocation?.let { onNewLocationReceived(it) }
                        }
                    }
                    try {
                        fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
                    } catch (_: SecurityException) {
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestProtectionLocationUpdates() {
        stopLocationUpdatesOnly()
        val repo = (application as LocationTrackerApp).repository
        val activeMode = repo.trackingMode.value

        if (activeMode == TrackingMode.POWER_SAVER) {
            // In Cell-Only standby, let cell diagnostics drive updates; register low-power coarse listener only as fallback
            startPeriodicCellDiagnostics()
            val request = LocationRequest.Builder(Priority.PRIORITY_LOW_POWER, 60_000L)
                .setMinUpdateDistanceMeters(50f)
                .setMinUpdateIntervalMillis(60_000L)
                .build()
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val last = result.lastLocation ?: return
                    onNewLocationReceived(last)
                }
            }
            try {
                fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
            } catch (_: SecurityException) {
            }
            return
        }

        val priority = if (activeMode == TrackingMode.HIGH_ACCURACY) {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        } else {
            Priority.PRIORITY_BALANCED_POWER_ACCURACY
        }
        val request = LocationRequest.Builder(priority, 60_000L)
            .setMinUpdateDistanceMeters(25f)
            .setMinUpdateIntervalMillis(30_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val last = result.lastLocation ?: return
                onNewLocationReceived(last)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        } catch (_: SecurityException) {
        }
    }

    private fun onNewLocationReceived(loc: Location) {
        val app = application as LocationTrackerApp
        val repo = app.repository
        val groupRepo = app.groupRepository
        val activeSession = repo.activeSession.value
        val effectiveMode = repo.trackingMode.value
        val latest = repo.currentLocation.value

        // Ignore stale cached locations captured before the most recent mode switch
        if (lastModeSwitchMs > 0L && loc.time > 0L && loc.time < lastModeSwitchMs - 2_000L) {
            return
        }

        // In Balanced mode, suppress raw hardware GPS provider overrides
        if (effectiveMode == TrackingMode.BALANCED && loc.provider == LocationManager.GPS_PROVIDER) {
            return
        }

        // In Cell-Only mode, never allow GPS or high-precision GNSS/Wi-Fi fixes to leak in
        if (effectiveMode == TrackingMode.POWER_SAVER) {
            if (loc.provider == LocationManager.GPS_PROVIDER || loc.accuracy < 35f) {
                return
            }
            if (latest != null && latest.provider == "cellular" && (System.currentTimeMillis() - latest.timestamp) < 35_000L) {
                return
            }
        }

        val point = LocationPoint(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracy = loc.accuracy,
            speed = if (loc.hasSpeed()) loc.speed else 0f,
            altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
            bearing = if (loc.hasBearing()) loc.bearing else 0f,
            provider = loc.provider ?: "fused",
            timestamp = loc.time,
            sessionId = activeSession?.id ?: if (isRecordingSession) "foreground" else "protection"
        )

        serviceScope.launch {
            repo.recordLocation(point)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (activeSession != null && isForegroundStarted) {
                nm.notify(NOTIFICATION_ID, buildStatusNotification(effectiveMode))
            }

            if (groupRepo.getCurrentUserId() != null) {
                val throttleMs = if (isRecordingSession || activeSession != null) 15_000L else 45_000L
                val now = System.currentTimeMillis()
                val latestSync = maxOf(lastCloudSyncMs, groupRepo.lastSyncTimestampMs.value ?: 0L)
                if (now - latestSync >= throttleMs) {
                    lastCloudSyncMs = now
                    groupRepo.syncDeviceAndGroupTelemetry(point, repo.trackingMode.value.name)
                }
            }
        }
    }

    private fun startPeriodicCellDiagnostics() {
        if (cellDiagnosticsJob?.isActive == true) return
        cellDiagnosticsJob = serviceScope.launch {
            val app = application as LocationTrackerApp
            val repo = app.repository
            val groupRepo = app.groupRepository
            while (isActive) {
                val towers = cellManager.readCurrentCellTowers()
                repo.updateCellTowers(towers)

                // Resolve serving mast in background too so POWER_SAVER stays strictly cellular even with UI closed
                if (repo.trackingMode.value == TrackingMode.POWER_SAVER) {
                    val serving = towers.firstOrNull { it.isRegistered } ?: towers.firstOrNull()
                    if (serving != null) {
                        val towerLoc = OpenCellIdClient.resolveCellLocation(
                            cellInfo = serving,
                            apiKey = repo.getSavedOpenCellIdApiKey()
                        )
                        if (towerLoc != null) {
                            val now = System.currentTimeMillis()
                            val towerKey = "${towerLoc.cellId}_${towerLoc.lac}"
                            val cur = repo.currentLocation.value
                            val shouldEmit = cur == null || cur.provider != "cellular" ||
                                (now - cur.timestamp > 25_000L) ||
                                (towerKey != lastBgCellularFallbackKey && (now - lastBgCellularFallbackMs >= 10_000L))
                            if (shouldEmit) {
                                lastBgCellularFallbackKey = towerKey
                                lastBgCellularFallbackMs = now
                                val cellPoint = LocationPoint(
                                    latitude = towerLoc.latitude,
                                    longitude = towerLoc.longitude,
                                    accuracy = towerLoc.rangeMeters.toFloat(),
                                    speed = 0f,
                                    altitude = 0.0,
                                    bearing = 0f,
                                    provider = "cellular",
                                    timestamp = now,
                                    sessionId = repo.activeSession.value?.id ?: if (isRecordingSession) "foreground" else "protection"
                                )
                                repo.recordLocation(cellPoint)
                                if (groupRepo.getCurrentUserId() != null) {
                                    val throttleMs = if (isRecordingSession || repo.activeSession.value != null) 15_000L else 45_000L
                                    val latestSync = maxOf(lastCloudSyncMs, groupRepo.lastSyncTimestampMs.value ?: 0L)
                                    if (now - latestSync >= throttleMs) {
                                        lastCloudSyncMs = now
                                        groupRepo.syncDeviceAndGroupTelemetry(cellPoint, repo.trackingMode.value.name)
                                    }
                                }
                            }
                        }
                    }
                }
                delay(10000L)
            }
        }
    }

    private fun startProtectionHeartbeat() {
        if (protectionHeartbeatJob?.isActive == true) return
        protectionHeartbeatJob = serviceScope.launch {
            val app = application as LocationTrackerApp
            val repo = app.repository
            val groupRepo = app.groupRepository
            while (isActive) {
                delay(90_000L)
                if (!groupRepo.isBackgroundProtectionActiveAndSignedIn()) break
                if (isRecordingSession || repo.activeSession.value != null) continue
                val loc = repo.currentLocation.value ?: continue
                val now = System.currentTimeMillis()
                val latestSync = maxOf(lastCloudSyncMs, groupRepo.lastSyncTimestampMs.value ?: 0L)
                if (now - latestSync >= 85_000L) {
                    lastCloudSyncMs = now
                    groupRepo.syncDeviceAndGroupTelemetry(loc, repo.trackingMode.value.name)
                }
            }
        }
    }

    private fun stopProtectionHeartbeat() {
        protectionHeartbeatJob?.cancel()
        protectionHeartbeatJob = null
    }

    private fun buildNotification(
        title: String,
        content: String,
        isRecordingUi: Boolean,
        isPausedUi: Boolean = false
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val actionIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = if (isRecordingUi) ACTION_STOP else ACTION_STOP_PROTECTION
        }
        val pendingAction = PendingIntent.getService(
            this,
            if (isRecordingUi) 1 else 2,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val actionLabel = if (isRecordingUi) "Stop & Save" else "Pause Protection"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_media_pause, actionLabel, pendingAction)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (isRecordingUi) {
            val toggleIntent = Intent(this, LocationForegroundService::class.java).apply {
                action = if (isPausedUi) ACTION_RESUME else ACTION_PAUSE
            }
            val pendingToggle = PendingIntent.getService(
                this,
                if (isPausedUi) 3 else 4,
                toggleIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                if (isPausedUi) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPausedUi) "Resume" else "Pause",
                pendingToggle
            )
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Real-Time GIS Tracking & Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live location telemetry, recording status, and 24/7 device protection"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopTrackingOrDowngradeInternal() {
        val app = application as LocationTrackerApp
        val repo = app.repository
        val groupRepo = app.groupRepository

        isRecordingSession = false
        releaseWakeLock()
        cellDiagnosticsJob?.cancel()
        cellDiagnosticsJob = null

        serviceScope.launch {
            if (repo.activeSession.value != null) {
                repo.stopCurrentSession()
            }
            if (groupRepo.isBackgroundProtectionActiveAndSignedIn() && hasLocationPermission(this@LocationForegroundService)) {
                isProtectionActive = true
                groupRepo.ensurePersonalDevicesListenerActive()
                startLightweightProtectionMode()
            } else {
                isProtectionActive = false
                stopLocationUpdatesOnly()
                stopForegroundAndSelf()
            }
        }
    }

    private fun stopLocationUpdatesOnly() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
        networkLocationListener?.let { listener ->
            try {
                systemLocationManager?.removeUpdates(listener)
            } catch (_: Exception) {
            }
        }
        networkLocationListener = null
    }

    private fun stopForegroundAndSelf() {
        isServiceActive = false
        releaseWakeLock()
        cellDiagnosticsJob?.cancel()
        cellDiagnosticsJob = null
        stopProtectionHeartbeat()
        val wasForeground = isForegroundStarted
        isForegroundStarted = false
        if (wasForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceActive = false
        stopLocationUpdatesOnly()
        cellDiagnosticsJob?.cancel()
        cellDiagnosticsJob = null
        stopProtectionHeartbeat()
        releaseWakeLock()
        isForegroundStarted = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
