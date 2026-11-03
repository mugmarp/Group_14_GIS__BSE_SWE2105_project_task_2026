package com.example.gistracker.service

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
import androidx.core.app.NotificationCompat
import com.example.gistracker.LocationTrackerApp
import com.example.gistracker.MainActivity
import com.example.gistracker.model.LocationPoint
import com.example.gistracker.model.TrackingMode
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

class LocationForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "gis_tracker_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "ACTION_START_TRACKING"
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

        fun stopService(context: Context) {
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var cellManager: CellularDiagnosticManager
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cellManager = CellularDiagnosticManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val modeStr = intent.getStringExtra(EXTRA_MODE) ?: TrackingMode.HIGH_ACCURACY.name
                val mode = try {
                    TrackingMode.valueOf(modeStr)
                } catch (e: Exception) {
                    TrackingMode.HIGH_ACCURACY
                }
                startForegroundTracking(mode)
            }
            ACTION_STOP -> {
                stopTrackingService()
            }
        }
        return START_STICKY
    }

    private fun startForegroundTracking(mode: TrackingMode) {
        val initialNotification = buildNotification("GIS Tracker Active", "Acquiring real-time GPS & cell fix...", mode)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                initialNotification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, initialNotification)
        }

        requestLocationUpdates(mode)
        startPeriodicCellDiagnostics()
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(mode: TrackingMode) {
        // Remove prior callback if any
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
        } catch (e: SecurityException) {
            // Handled in activity permission flow
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
            sessionId = activeSession?.id ?: "ad_hoc"
        )

        serviceScope.launch {
            repo.recordLocation(point)

            val speedKmh = point.speedKmh
            val distFormatted = activeSession?.formattedDistance ?: "0 m"
            val title = "Tracking: ${activeSession?.name ?: "Live Location"}"
            val content = "Dist: $distFormatted | Speed: ${String.format("%.1f", speedKmh)} km/h | Acc: ±${loc.accuracy.toInt()}m"

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(NOTIFICATION_ID, buildNotification(title, content, mode))
        }
    }

    private fun startPeriodicCellDiagnostics() {
        serviceScope.launch {
            val repo = (application as LocationTrackerApp).repository
            while (isActive) {
                val towers = cellManager.readCurrentCellTowers()
                repo.updateCellTowers(towers)
                delay(10000L) // Refresh cell towers every 10 seconds
            }
        }
    }

    private fun buildNotification(title: String, content: String, mode: TrackingMode): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_media_pause, "Stop Tracking", pendingStop)
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
                description = "Shows live location tracking progress and notifications"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun stopTrackingService() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        serviceScope.launch {
            val repo = (application as LocationTrackerApp).repository
            repo.stopCurrentSession()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
