package com.geotrace.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class LocationForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "gis_tracker_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_UPDATE_MODE = "ACTION_UPDATE_MODE"
        const val ACTION_START_BEACON = "ACTION_START_BEACON"
        const val ACTION_STOP_BEACON = "ACTION_STOP_BEACON"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val EXTRA_MODE = "EXTRA_MODE"

        fun startService(context: Context, mode: TrackingMode) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun updateMode(context: Context, mode: TrackingMode) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_UPDATE_MODE
                putExtra(EXTRA_MODE, mode.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun startBeaconService(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START_BEACON
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopBeaconService(context: Context) {
            val repo = (context.applicationContext as? LocationTrackerApp)?.repository
            if (repo?.activeSession?.value != null) {
                // Keep foreground service running for active trail, just clear beacon flag via intent
                val intent = Intent(context, LocationForegroundService::class.java).apply {
                    action = ACTION_STOP_BEACON
                }
                try {
                    context.startService(intent)
                } catch (_: Exception) {}
            } else {
                context.stopService(Intent(context, LocationForegroundService::class.java))
            }
        }

        fun stopService(context: Context) {
            val repo = (context.applicationContext as? LocationTrackerApp)?.repository
            if (repo?.backgroundBeaconEnabled?.value == true) {
                // Transition smoothly to background device beacon if enabled
                startBeaconService(context)
            } else {
                context.stopService(Intent(context, LocationForegroundService::class.java))
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var cellManager: CellularDiagnosticManager
    private var locationCallback: LocationCallback? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var cellDiagnosticsJob: kotlinx.coroutines.Job? = null
    private var currentMode: TrackingMode = TrackingMode.HIGH_ACCURACY
    private var isBeaconActive: Boolean = false
    private var isForegroundStarted: Boolean = false

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cellManager = CellularDiagnosticManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repo = (application as LocationTrackerApp).repository
        isBeaconActive = repo.backgroundBeaconEnabled.value

        when (intent?.action) {
            ACTION_START -> {
                val modeStr = intent.getStringExtra(EXTRA_MODE) ?: repo.trackingMode.value.name
                val mode = parseMode(modeStr)
                currentMode = mode
                startForegroundTracking(mode)
            }
            ACTION_UPDATE_MODE -> {
                val modeStr = intent.getStringExtra(EXTRA_MODE) ?: repo.trackingMode.value.name
                val mode = parseMode(modeStr)
                currentMode = mode
                ensureForeground(buildStatusNotification(mode))
                requestLocationUpdates(mode)
            }
            ACTION_START_BEACON -> {
                isBeaconActive = true
                val activeSession = repo.activeSession.value
                currentMode = activeSession?.trackingMode ?: repo.trackingMode.value
                if (activeSession != null) {
                    ensureForeground(buildStatusNotification(currentMode))
                } else {
                    ensureForeground(
                        buildNotification(
                            title = "GeoTrace Device Beacon Active",
                            content = "Keeping device discoverable & reachable for remote Find/Ring",
                            isBeaconOnly = true
                        )
                    )
                    acquireWakeLock()
                    requestLocationUpdates(TrackingMode.BALANCED)
                    startPeriodicCellDiagnostics()
                }
            }
            ACTION_STOP_BEACON -> {
                isBeaconActive = false
                if (repo.activeSession.value == null) {
                    stopTrackingService(stopActiveSession = false)
                } else {
                    ensureForeground(buildStatusNotification(currentMode))
                }
            }
            ACTION_STOP -> {
                if (isBeaconActive) {
                    serviceScope.launch {
                        repo.stopCurrentSession()
                    }
                    ensureForeground(
                        buildNotification(
                            title = "GeoTrace Device Beacon Active",
                            content = "Trail stopped • Device remains discoverable for Find/Ring",
                            isBeaconOnly = true
                        )
                    )
                    requestLocationUpdates(TrackingMode.BALANCED)
                } else {
                    stopTrackingService(stopActiveSession = repo.activeSession.value != null)
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
        ensureForeground(buildStatusNotification(mode))
        acquireWakeLock()
        requestLocationUpdates(mode)
        startPeriodicCellDiagnostics()
    }

    private fun ensureForeground(notification: Notification) {
        if (!isForegroundStarted) {
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
        } else {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildStatusNotification(mode: TrackingMode): Notification {
        val repo = (application as LocationTrackerApp).repository
        val activeSession = repo.activeSession.value
        val calibratedPoint = repo.currentLocation.value
        return if (calibratedPoint != null && activeSession != null) {
            val speedKmh = calibratedPoint.speedKmh
            val gait = calibratedPoint.gaitLabel
            val distFormatted = activeSession.formattedDistance
            val title = "Tracking (${mode.title}): ${activeSession.name}"
            val content = "Dist: $distFormatted | Speed: ${String.format(Locale.US, "%.1f", speedKmh)} km/h ($gait) | Acc: ±${calibratedPoint.accuracy.toInt()}m"
            buildNotification(title, content, isBeaconOnly = false)
        } else {
            buildNotification(
                title = "GeoTrace Active (${mode.title})",
                content = "Acquiring live location telemetry...",
                isBeaconOnly = false
            )
        }
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
                    onNewLocationReceived(loc, mode)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        } catch (_: SecurityException) {
        }
    }

    private fun onNewLocationReceived(loc: Location, mode: TrackingMode) {
        val repo = (application as LocationTrackerApp).repository
        val activeSession = repo.activeSession.value

        val point = LocationPoint(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracy = loc.accuracy,
            speed = if (loc.hasSpeed()) loc.speed else 0f,
            altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
            bearing = if (loc.hasBearing()) loc.bearing else 0f,
            provider = loc.provider ?: "fused",
            timestamp = loc.time,
            sessionId = activeSession?.id ?: "beacon"
        )

        serviceScope.launch {
            repo.recordLocation(point)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (activeSession != null) {
                nm.notify(NOTIFICATION_ID, buildStatusNotification(mode))
            } else if (isBeaconActive) {
                nm.notify(
                    NOTIFICATION_ID,
                    buildNotification(
                        title = "GeoTrace Device Beacon Active",
                        content = "Live fix ±${loc.accuracy.toInt()}m • Device reachable for Find/Ring",
                        isBeaconOnly = true
                    )
                )
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

    private fun buildNotification(title: String, content: String, isBeaconOnly: Boolean): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopAction = if (isBeaconOnly) ACTION_STOP_BEACON else ACTION_STOP
        val stopLabel = if (isBeaconOnly) "Turn Off Beacon" else "Stop Tracking"
        val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = stopAction
        }
        val pendingStop = PendingIntent.getService(
            this, if (isBeaconOnly) 2 else 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_media_pause, stopLabel, pendingStop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Real-Time GIS Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live location tracking and background device beacon status"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopTrackingService(stopActiveSession: Boolean) {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        cellDiagnosticsJob?.cancel()
        cellDiagnosticsJob = null
        releaseWakeLock()
        isForegroundStarted = false
        if (stopActiveSession) {
            serviceScope.launch {
                val repo = (application as LocationTrackerApp).repository
                repo.stopCurrentSession()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        cellDiagnosticsJob?.cancel()
        cellDiagnosticsJob = null
        releaseWakeLock()
        isForegroundStarted = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
