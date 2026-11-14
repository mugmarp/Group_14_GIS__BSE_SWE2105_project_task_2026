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
import android.os.Build
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
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val ACTION_START_PROTECTION = "ACTION_START_PROTECTION"
        const val ACTION_STOP_PROTECTION = "ACTION_STOP_PROTECTION"
        const val EXTRA_MODE = "EXTRA_MODE"

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

        fun updateMode(context: Context, mode: TrackingMode) {
            if (!hasLocationPermission(context)) return
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

        fun startProtectionMode(context: Context): Boolean {
            if (!hasLocationPermission(context)) return false
            if (Firebase.auth.currentUser == null) return false
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START_PROTECTION
            }
            return safeStartForegroundService(context, intent)
        }

        fun stopProtectionMode(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP_PROTECTION
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

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cellManager = CellularDiagnosticManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as LocationTrackerApp
        val repo = app.repository
        val groupRepo = app.groupRepository

        when (intent?.action) {
            ACTION_START -> {
                val modeStr = intent.getStringExtra(EXTRA_MODE) ?: repo.trackingMode.value.name
                val mode = parseMode(modeStr)
                currentMode = mode
                isRecordingSession = true
                isProtectionActive = groupRepo.isBackgroundProtectionActiveAndSignedIn()
                startForegroundTracking(mode)
            }
            ACTION_UPDATE_MODE -> {
                val modeStr = intent.getStringExtra(EXTRA_MODE) ?: repo.trackingMode.value.name
                val mode = parseMode(modeStr)
                currentMode = mode
                if (isRecordingSession || repo.activeSession.value != null) {
                    ensureForeground(buildStatusNotification(mode))
                    requestLocationUpdates(mode)
                }
            }
            ACTION_START_PROTECTION -> {
                if (!groupRepo.isBackgroundProtectionActiveAndSignedIn() || !hasLocationPermission(this)) {
                    if (!isRecordingSession && repo.activeSession.value == null) {
                        stopForegroundAndSelf()
                        return START_NOT_STICKY
                    }
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
                groupRepo.setBackgroundProtectionEnabled(false)
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

        ensureForeground(buildStatusNotification(currentMode))
        releaseWakeLock()
        cellDiagnosticsJob?.cancel()
        cellDiagnosticsJob = null
        requestProtectionLocationUpdates()
        startProtectionHeartbeat()

        // Seed immediate device visibility using existing fix or lastLocation
        serviceScope.launch {
            val existing = repo.currentLocation.value
            if (existing != null) {
                lastCloudSyncMs = System.currentTimeMillis()
                groupRepo.syncDeviceAndGroupTelemetry(existing, repo.trackingMode.value.name)
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
                                lastCloudSyncMs = System.currentTimeMillis()
                                groupRepo.syncDeviceAndGroupTelemetry(point, repo.trackingMode.value.name)
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
            return if (calibratedPoint != null && activeSession != null) {
                val speedKmh = calibratedPoint.speedKmh
                val gait = calibratedPoint.gaitLabel
                val distFormatted = activeSession.formattedDistance
                val title = "Tracking (${mode.title}): ${activeSession.name}"
                val content = "Dist: $distFormatted | Speed: ${String.format(Locale.US, "%.1f", speedKmh)} km/h ($gait) | Acc: ±${calibratedPoint.accuracy.toInt()}m"
                buildNotification(title, content, isRecordingUi = true)
            } else {
                buildNotification(
                    title = "GeoTrace Active (${mode.title})",
                    content = "Acquiring live location telemetry...",
                    isRecordingUi = true
                )
            }
        }

        val callsign = groupRepo.offlineCallsign.value
        val privacyTag = if (groupRepo.isGhostMode.value) "Ghost Mode (My Devices only)" else "Fleet & Remote Ring ready"
        return buildNotification(
            title = "GeoTrace 24/7 Protection • $callsign",
            content = "$privacyTag • Locatable even when closed",
            isRecordingUi = false
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
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

        val priority = when (mode) {
            TrackingMode.HIGH_ACCURACY -> Priority.PRIORITY_HIGH_ACCURACY
            TrackingMode.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            TrackingMode.POWER_SAVER -> Priority.PRIORITY_LOW_POWER
        }

        val request = LocationRequest.Builder(priority, mode.intervalMs)
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

    @SuppressLint("MissingPermission")
    private fun requestProtectionLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 60_000L)
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
        val effectiveMode = if (isRecordingSession || activeSession != null) currentMode else TrackingMode.BALANCED
        val latest = repo.currentLocation.value

        // In Cell-Only mode, if a recent cellular mast fix exists (< 35s), ignore Wi-Fi/fused coarse pings
        if ((isRecordingSession || activeSession != null) &&
            effectiveMode == TrackingMode.POWER_SAVER &&
            latest != null &&
            latest.provider == "cellular" &&
            (System.currentTimeMillis() - latest.timestamp) < 35_000L
        ) {
            return
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
                if (now - lastCloudSyncMs >= throttleMs) {
                    lastCloudSyncMs = now
                    groupRepo.syncDeviceAndGroupTelemetry(point, repo.trackingMode.value.name)
                }
            }
        }
    }

    private fun startPeriodicCellDiagnostics() {
        if (cellDiagnosticsJob?.isActive == true) return
        cellDiagnosticsJob = serviceScope.launch {
            val repo = (application as LocationTrackerApp).repository
            while (isActive) {
                val towers = cellManager.readCurrentCellTowers()
                repo.updateCellTowers(towers)
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
                if (now - lastCloudSyncMs >= 85_000L) {
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

    private fun buildNotification(title: String, content: String, isRecordingUi: Boolean): Notification {
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
        val actionLabel = if (isRecordingUi) "Stop Recording" else "Pause Protection"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_media_pause, actionLabel, pendingAction)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
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
    }

    private fun stopForegroundAndSelf() {
        releaseWakeLock()
        cellDiagnosticsJob?.cancel()
        cellDiagnosticsJob = null
        stopProtectionHeartbeat()
        isForegroundStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
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
