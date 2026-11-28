package com.geotrace.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.geotrace.LocationTrackerApp
import com.geotrace.MainActivity
import com.geotrace.model.LastGaspBeacon
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class LocationForegroundService : Service() {

    companion object {
        private const val TAG = "LocationFgService"
        private const val LEGACY_CHANNEL_ID = "gis_tracker_channel"
        const val CHANNEL_ID = "geotrace_protection_channel_v2"
        const val BATTERY_SAFETY_CHANNEL_ID = "geotrace_battery_safety_channel"
        const val NOTIFICATION_ID = 1001
        const val BATTERY_SAFETY_NOTIFICATION_ID = 4199

        const val ACTION_START = "ACTION_START_TRACKING"
        const val ACTION_START_PAUSED = "ACTION_START_PAUSED_TRACKING"
        const val ACTION_UPDATE_MODE = "ACTION_UPDATE_MODE"
        const val ACTION_PAUSE = "ACTION_PAUSE_TRACKING"
        const val ACTION_RESUME = "ACTION_RESUME_TRACKING"
        const val ACTION_STOP = "ACTION_STOP_TRACKING"
        const val ACTION_START_PROTECTION = "ACTION_START_PROTECTION"
        const val ACTION_PAUSE_PROTECTION = "ACTION_PAUSE_PROTECTION"
        const val ACTION_RESUME_PROTECTION = "ACTION_RESUME_PROTECTION"
        const val ACTION_REFRESH_PROTECTION = "ACTION_REFRESH_PROTECTION"
        const val ACTION_STOP_PROTECTION = "ACTION_STOP_PROTECTION"
        const val ACTION_START_NAV = "ACTION_START_NAV"
        const val ACTION_STOP_NAV = "ACTION_STOP_NAV"
        const val ACTION_REFRESH_NAV = "ACTION_REFRESH_NAV"
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_PERSIST_DISABLE = "EXTRA_PERSIST_DISABLE"
        const val EXTRA_FORCE_RESUME = "EXTRA_FORCE_RESUME"
        const val EXTRA_STARTED_AS_FOREGROUND = "EXTRA_STARTED_AS_FOREGROUND"
        const val EXTRA_FROM_NOTIFICATION = "EXTRA_FROM_NOTIFICATION"

        private val _isProtectionRunningFlow = MutableStateFlow(false)
        val isProtectionRunningFlow: StateFlow<Boolean> = _isProtectionRunningFlow.asStateFlow()

        private val _isProtectionPausedFlow = MutableStateFlow(false)
        val isProtectionPausedFlow: StateFlow<Boolean> = _isProtectionPausedFlow.asStateFlow()

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

        fun hasNotificationPermission(context: Context): Boolean {
            val postGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
            return postGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        }

        fun startService(context: Context, mode: TrackingMode) {
            if (!hasLocationPermission(context)) return
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MODE, mode.name)
            }
            safeStartForegroundService(context, intent)
        }

        fun startServicePaused(context: Context, mode: TrackingMode) {
            if (!hasLocationPermission(context)) return
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START_PAUSED
                putExtra(EXTRA_MODE, mode.name)
            }
            safeStartForegroundService(context, intent)
        }

        @Volatile
        var isServiceActive: Boolean = false
            private set

        @Volatile
        var isCellDiagnosticsActive: Boolean = false
            private set

        data class BatterySafetyDecision(
            val resetState: Boolean = false,
            val shouldCancelNotification: Boolean = false,
            val captureBeaconThreshold: Int? = null,
            val notifyThreshold: Int? = null,
            val nextTriggeredBeaconThreshold: Int = 100,
            val nextNotifiedThreshold: Int = 100
        )

        /**
         * Pure JVM threshold state machine for Low-Battery Last-Gasp beacon capture (15% / 10% / 5%)
         * and user safety notifications (12% / 10% / 5%). Decoupled so dropping from 15% to 12%
         * reliably notifies without being masked by the 15% beacon capture bucket.
         */
        fun computeBatterySafetyDecision(
            pct: Int,
            isCharging: Boolean,
            lastTriggeredBeaconThreshold: Int,
            lastSafetyNotifiedThreshold: Int
        ): BatterySafetyDecision {
            if (isCharging || pct > 20) {
                return BatterySafetyDecision(
                    resetState = true,
                    shouldCancelNotification = isCharging && pct > 20,
                    nextTriggeredBeaconThreshold = 100,
                    nextNotifiedThreshold = 100
                )
            }
            val beaconBucket = when {
                pct <= 5 -> 5
                pct <= 10 -> 10
                pct <= 15 -> 15
                else -> null
            }
            val notifyBucket = when {
                pct <= 5 -> 5
                pct <= 10 -> 10
                pct <= 12 -> 12
                else -> null
            }
            val captureBeaconThreshold = if (beaconBucket != null && beaconBucket < lastTriggeredBeaconThreshold) {
                beaconBucket
            } else null
            val notifyThreshold = if (notifyBucket != null && notifyBucket < lastSafetyNotifiedThreshold) {
                notifyBucket
            } else null
            return BatterySafetyDecision(
                captureBeaconThreshold = captureBeaconThreshold,
                notifyThreshold = notifyThreshold,
                nextTriggeredBeaconThreshold = captureBeaconThreshold ?: lastTriggeredBeaconThreshold,
                nextNotifiedThreshold = notifyThreshold ?: lastSafetyNotifiedThreshold
            )
        }

        fun updateMode(context: Context, mode: TrackingMode) {
            if (!isServiceActive || !hasLocationPermission(context)) return
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_UPDATE_MODE
                putExtra(EXTRA_MODE, mode.name)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                safeStartForegroundService(context, intent)
            }
        }

        fun stopTrackingOrDowngrade(context: Context) {
            if (!isServiceActive) return
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

        fun startProtectionMode(context: Context, forceResume: Boolean = false): Boolean {
            if (!hasLocationPermission(context)) return false
            if (Firebase.auth.currentUser == null) return false
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START_PROTECTION
                putExtra(EXTRA_FORCE_RESUME, forceResume)
            }
            return safeStartForegroundService(context, intent)
        }

        fun pauseProtectionMode(context: Context) {
            if (!isServiceActive) return
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_PAUSE_PROTECTION
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun resumeProtectionMode(context: Context): Boolean {
            if (!hasLocationPermission(context)) return false
            if (Firebase.auth.currentUser == null) return false
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_RESUME_PROTECTION
            }
            return safeStartForegroundService(context, intent)
        }

        fun refreshProtectionNotification(context: Context) {
            if (!isServiceActive) return
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_REFRESH_PROTECTION
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun stopProtectionMode(context: Context, disablePreference: Boolean = true) {
            if (!isServiceActive) {
                if (disablePreference) {
                    (context.applicationContext as? LocationTrackerApp)
                        ?.groupRepository
                        ?.setBackgroundProtectionEnabled(false)
                }
                _isProtectionPausedFlow.value = false
                _isProtectionRunningFlow.value = false
                return
            }
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP_PROTECTION
                putExtra(EXTRA_PERSIST_DISABLE, disablePreference)
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
                // Service wasn't running or app is in background stop window
                if (disablePreference) {
                    (context.applicationContext as? LocationTrackerApp)
                        ?.groupRepository
                        ?.setBackgroundProtectionEnabled(false)
                }
            }
        }

        fun startNavigationMode(context: Context): Boolean {
            if (!hasLocationPermission(context)) return false
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_START_NAV
            }
            return safeStartForegroundService(context, intent)
        }

        fun stopNavigationMode(context: Context) {
            if (!isServiceActive) return
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_STOP_NAV
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
            }
        }

        fun refreshNavigationNotification(context: Context) {
            if (!isServiceActive) return
            val intent = Intent(context, LocationForegroundService::class.java).apply {
                action = ACTION_REFRESH_NAV
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {
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
    private var quickFixCancellation: com.google.android.gms.tasks.CancellationTokenSource? = null
    private var osQuickFixCancellation: android.os.CancellationSignal? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var cellDiagnosticsJob: Job? = null
    private var protectionHeartbeatJob: Job? = null
    private var currentMode: TrackingMode = TrackingMode.HIGH_ACCURACY
    private var isForegroundStarted: Boolean = false
    private var isRecordingSession: Boolean = false
    private var isProtectionActive: Boolean = false
    private var isProtectionPaused: Boolean = false
    private var isNavigating: Boolean = false
    private var latestStartId: Int = 0
    @Volatile
    private var lastCloudSyncMs: Long = 0L
    @Volatile
    private var lastNotificationUpdateMs: Long = 0L
    @Volatile
    private var lastModeSwitchMs: Long = 0L
    @Volatile
    private var lastModeSwitchElapsedNs: Long = 0L
    private var lastBgCellularFallbackKey: String? = null
    private var lastBgCellularFallbackMs: Long = 0L
    private var batteryReceiver: BroadcastReceiver? = null
    @Volatile
    private var batteryReceiverRegistered = false
    @Volatile
    private var lastTriggeredBatteryThreshold = 100
    @Volatile
    private var lastSafetyNotifiedThreshold = 100
    @Volatile
    private var pendingLowBatteryBeaconPct: Int? = null

    private fun syncProtectionRuntimeState() {
        _isProtectionPausedFlow.value = isProtectionPaused
        _isProtectionRunningFlow.value = isForegroundStarted && ((isProtectionActive && !isProtectionPaused) || isRecordingSession)
    }

    override fun onCreate() {
        super.onCreate()
        isServiceActive = true
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        systemLocationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        cellManager = CellularDiagnosticManager(this)
        createNotificationChannel()
        registerBatteryReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
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
                lastModeSwitchElapsedNs = android.os.SystemClock.elapsedRealtimeNanos()
                isRecordingSession = true
                isProtectionActive = groupRepo.isBackgroundProtectionActiveAndSignedIn()
                startForegroundTracking(mode)
            }
            ACTION_START_PAUSED -> {
                val modeStr = intent.getStringExtra(EXTRA_MODE) ?: repo.trackingMode.value.name
                val mode = parseMode(modeStr)
                currentMode = mode
                isRecordingSession = true
                isProtectionActive = groupRepo.isBackgroundProtectionActiveAndSignedIn()
                releaseWakeLock()
                stopCellDiagnostics()
                stopLocationUpdatesOnly()
                ensureForeground(buildStatusNotification(mode))
                if (isProtectionActive && !isProtectionPaused && hasLocationPermission(this)) {
                    groupRepo.ensurePersonalDevicesListenerActive()
                    requestProtectionLocationUpdates()
                    startProtectionHeartbeat()
                }
            }
            ACTION_UPDATE_MODE -> {
                val modeStr = intent.getStringExtra(EXTRA_MODE) ?: repo.trackingMode.value.name
                val mode = parseMode(modeStr)
                currentMode = mode
                lastModeSwitchMs = System.currentTimeMillis()
                lastModeSwitchElapsedNs = android.os.SystemClock.elapsedRealtimeNanos()
                repo.setTrackingModeSync(mode)
                serviceScope.launch {
                    repo.setTrackingMode(mode)
                }
                if (mode == TrackingMode.POWER_SAVER) {
                    try {
                        (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                            ?.cancel(BATTERY_SAFETY_NOTIFICATION_ID)
                    } catch (_: Exception) {}
                }
                val isSessionPaused = repo.activeSession.value?.isPaused == true
                if (isRecordingSession || repo.activeSession.value != null) {
                    ensureForeground(buildStatusNotification(mode))
                    if (!isSessionPaused) {
                        requestLocationUpdates(mode, forceImmediateFix = true)
                        startPeriodicCellDiagnostics(restart = true)
                    } else if (isNavigating) {
                        requestLocationUpdates(TrackingMode.HIGH_ACCURACY, forceImmediateFix = true)
                    } else if (isProtectionActive && !isProtectionPaused && hasLocationPermission(this)) {
                        requestProtectionLocationUpdates(modeChanged = true)
                    }
                    syncCurrentModeTelemetry(mode)
                } else if (isNavigating && hasLocationPermission(this)) {
                    ensureForeground(buildStatusNotification(mode))
                    requestLocationUpdates(TrackingMode.HIGH_ACCURACY, forceImmediateFix = true)
                    stopCellDiagnostics()
                    syncCurrentModeTelemetry(mode)
                } else if (isProtectionActive || (groupRepo.isBackgroundProtectionActiveAndSignedIn() && hasLocationPermission(this))) {
                    isProtectionActive = true
                    ensureForeground(buildStatusNotification(mode))
                    if (!isProtectionPaused) {
                        requestProtectionLocationUpdates(modeChanged = true)
                    } else {
                        stopCellDiagnostics()
                        stopLocationUpdatesOnly()
                    }
                    syncCurrentModeTelemetry(mode)
                } else {
                    if (startedAsForeground && !isForegroundStarted && hasLocationPermission(this)) {
                        ensureForeground(buildStatusNotification(mode))
                    }
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                }
            }
            ACTION_START_PROTECTION -> {
                val forceResume = intent.getBooleanExtra(EXTRA_FORCE_RESUME, false)
                if (!groupRepo.isBackgroundProtectionActiveAndSignedIn() || !hasLocationPermission(this)) {
                    if (!isRecordingSession && repo.activeSession.value == null && !isNavigating) {
                        if (startedAsForeground && !isForegroundStarted && hasLocationPermission(this)) {
                            ensureForeground(buildStatusNotification(currentMode))
                        }
                        stopForegroundAndSelf()
                        return START_NOT_STICKY
                    }
                    ensureForeground(buildStatusNotification(currentMode))
                    return START_STICKY
                }
                if (forceResume) {
                    isProtectionPaused = false
                }
                isProtectionActive = true
                groupRepo.ensurePersonalDevicesListenerActive()
                if (!isRecordingSession && repo.activeSession.value == null && !isNavigating) {
                    if (isProtectionPaused) {
                        stopLocationUpdatesOnly()
                        stopCellDiagnostics()
                        stopProtectionHeartbeat()
                        ensureForeground(buildStatusNotification(currentMode))
                    } else {
                        startLightweightProtectionMode()
                    }
                } else {
                    ensureForeground(buildStatusNotification(currentMode))
                }
            }
            ACTION_PAUSE_PROTECTION -> {
                isProtectionPaused = true
                stopProtectionHeartbeat()
                if (!isRecordingSession && repo.activeSession.value == null && !isNavigating) {
                    releaseWakeLock()
                    stopCellDiagnostics()
                    stopLocationUpdatesOnly()
                    if (groupRepo.isBackgroundProtectionActiveAndSignedIn() && hasLocationPermission(this)) {
                        isProtectionActive = true
                        ensureForeground(buildStatusNotification(currentMode))
                    } else {
                        stopForegroundAndSelf()
                        return START_NOT_STICKY
                    }
                } else {
                    ensureForeground(buildStatusNotification(currentMode))
                }
            }
            ACTION_RESUME_PROTECTION -> {
                isProtectionPaused = false
                if (!groupRepo.isBackgroundProtectionActiveAndSignedIn() || !hasLocationPermission(this)) {
                    if (!isRecordingSession && repo.activeSession.value == null && !isNavigating) {
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
                if (!isRecordingSession && repo.activeSession.value == null && !isNavigating) {
                    startLightweightProtectionMode()
                } else if (repo.activeSession.value?.isPaused == true && !isNavigating) {
                    ensureForeground(buildStatusNotification(currentMode))
                    requestProtectionLocationUpdates()
                    startProtectionHeartbeat()
                } else {
                    ensureForeground(buildStatusNotification(currentMode))
                }
            }
            ACTION_REFRESH_PROTECTION -> {
                if (isForegroundStarted) {
                    ensureForeground(buildStatusNotification(currentMode))
                } else if (groupRepo.isBackgroundProtectionActiveAndSignedIn() && hasLocationPermission(this)) {
                    isProtectionActive = true
                    if (isProtectionPaused) {
                        ensureForeground(buildStatusNotification(currentMode))
                    } else {
                        startLightweightProtectionMode()
                    }
                }
            }
            ACTION_STOP_PROTECTION -> {
                val persistDisable = intent.getBooleanExtra(EXTRA_PERSIST_DISABLE, true)
                if (persistDisable) {
                    groupRepo.setBackgroundProtectionEnabled(false)
                }
                isProtectionPaused = false
                isProtectionActive = false
                protectionHeartbeatJob?.cancel()
                protectionHeartbeatJob = null
                if (!isRecordingSession && repo.activeSession.value == null && !isNavigating) {
                    stopLocationUpdatesOnly()
                    stopForegroundAndSelf()
                    return START_NOT_STICKY
                } else {
                    ensureForeground(buildStatusNotification(currentMode))
                }
            }
            ACTION_START_NAV -> {
                if (!hasLocationPermission(this)) {
                    if (!isRecordingSession && repo.activeSession.value == null && !isProtectionActive) {
                        if (startedAsForeground && !isForegroundStarted) {
                            // Cannot run without location permission
                        }
                        stopForegroundAndSelf()
                        return START_NOT_STICKY
                    }
                    return START_STICKY
                }
                isNavigating = true
                currentMode = repo.trackingMode.value
                isProtectionActive = groupRepo.isBackgroundProtectionActiveAndSignedIn()
                acquireWakeLock()
                if (!ensureForeground(buildStatusNotification(currentMode))) {
                    isNavigating = false
                    return START_NOT_STICKY
                }
                val isActivelyRecordingUnpaused = isRecordingSession && repo.activeSession.value?.isPaused != true
                if (!isActivelyRecordingUnpaused || currentMode != TrackingMode.HIGH_ACCURACY) {
                    requestLocationUpdates(TrackingMode.HIGH_ACCURACY, forceImmediateFix = true)
                    stopCellDiagnostics()
                    stopProtectionHeartbeat()
                }
            }
            ACTION_REFRESH_NAV -> {
                if (isNavigating && isForegroundStarted) {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NOTIFICATION_ID, buildStatusNotification(currentMode))
                    lastNotificationUpdateMs = System.currentTimeMillis()
                }
            }
            ACTION_STOP_NAV -> {
                val fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)
                isNavigating = false
                if (fromNotification) {
                    repo.requestStopNavigationFromService()
                    serviceScope.launch {
                        repo.clearActiveNavRouteFlag()
                    }
                } else {
                    repo.updateActiveNavNotificationText(null)
                }
                val activeSess = repo.activeSession.value
                when {
                    isRecordingSession || activeSess != null -> {
                        if (activeSess?.isPaused == true) {
                            releaseWakeLock()
                            stopCellDiagnostics()
                            stopLocationUpdatesOnly()
                            if (isProtectionActive && !isProtectionPaused && hasLocationPermission(this)) {
                                requestProtectionLocationUpdates()
                                startProtectionHeartbeat()
                            }
                        } else if (hasLocationPermission(this)) {
                            requestLocationUpdates(currentMode, forceImmediateFix = true)
                            startPeriodicCellDiagnostics(restart = true)
                        }
                        ensureForeground(buildStatusNotification(currentMode))
                    }
                    groupRepo.isBackgroundProtectionActiveAndSignedIn() && hasLocationPermission(this) -> {
                        isProtectionActive = true
                        if (isProtectionPaused) {
                            releaseWakeLock()
                            stopLocationUpdatesOnly()
                            ensureForeground(buildStatusNotification(currentMode))
                        } else {
                            startLightweightProtectionMode()
                        }
                    }
                    else -> {
                        stopLocationUpdatesOnly()
                        stopForegroundAndSelf()
                        return START_NOT_STICKY
                    }
                }
            }
            ACTION_PAUSE -> {
                // Keep WakeLock and updates running if turn-by-turn navigation is active
                if (!isNavigating) {
                    releaseWakeLock()
                    stopCellDiagnostics()
                    stopLocationUpdatesOnly()
                }
                serviceScope.launch {
                    repo.pauseCurrentSession()
                    ensureForeground(buildStatusNotification(currentMode))
                    if (!isNavigating && groupRepo.isBackgroundProtectionActiveAndSignedIn() && !isProtectionPaused && hasLocationPermission(this@LocationForegroundService)) {
                        isProtectionActive = true
                        requestProtectionLocationUpdates()
                        startProtectionHeartbeat()
                    }
                }
            }
            ACTION_RESUME -> {
                // Satisfy startForegroundService contract synchronously before DB I/O
                if (!ensureForeground(buildStatusNotification(currentMode))) {
                    return START_NOT_STICKY
                }
                serviceScope.launch {
                    val resumed = repo.resumeCurrentSession()
                    if (resumed != null) {
                        isRecordingSession = true
                        currentMode = repo.trackingMode.value
                        lastModeSwitchMs = System.currentTimeMillis()
                        lastModeSwitchElapsedNs = android.os.SystemClock.elapsedRealtimeNanos()
                        startForegroundTracking(currentMode)
                    } else if (isNavigating && hasLocationPermission(this@LocationForegroundService)) {
                        acquireWakeLock()
                        ensureForeground(buildStatusNotification(currentMode))
                        requestLocationUpdates(TrackingMode.HIGH_ACCURACY, forceImmediateFix = true)
                    } else if (groupRepo.isBackgroundProtectionActiveAndSignedIn() && hasLocationPermission(this@LocationForegroundService)) {
                        isRecordingSession = false
                        isProtectionActive = true
                        if (isProtectionPaused) {
                            ensureForeground(buildStatusNotification(currentMode))
                        } else {
                            startLightweightProtectionMode()
                        }
                    } else {
                        isRecordingSession = false
                        stopForegroundAndSelf()
                    }
                }
            }
            ACTION_STOP -> {
                stopTrackingOrDowngradeInternal()
            }
            null -> {
                // Restarted by system (START_STICKY) after process kill: satisfy foreground immediately, then await DB restore
                if (hasLocationPermission(this)) {
                    ensureForeground(buildStatusNotification(repo.trackingMode.value))
                }
                serviceScope.launch {
                    repo.awaitInitialRestore()
                    val restored = repo.activeSession.value
                    val activeNav = repo.getActiveNavRoute()
                    if (activeNav != null) {
                        isNavigating = true
                    }
                    if (restored != null) {
                        isRecordingSession = true
                        currentMode = restored.trackingMode
                        isProtectionActive = groupRepo.isBackgroundProtectionActiveAndSignedIn()
                        if (restored.isPaused) {
                            if (isNavigating && hasLocationPermission(this@LocationForegroundService)) {
                                acquireWakeLock()
                                ensureForeground(buildStatusNotification(currentMode))
                                requestLocationUpdates(TrackingMode.HIGH_ACCURACY, forceImmediateFix = true)
                            } else {
                                releaseWakeLock()
                                stopCellDiagnostics()
                                stopLocationUpdatesOnly()
                                ensureForeground(buildStatusNotification(currentMode))
                                if (isProtectionActive && !isProtectionPaused && hasLocationPermission(this@LocationForegroundService)) {
                                    requestProtectionLocationUpdates()
                                    startProtectionHeartbeat()
                                }
                            }
                        } else {
                            startForegroundTracking(currentMode)
                            if (isNavigating && currentMode != TrackingMode.HIGH_ACCURACY && hasLocationPermission(this@LocationForegroundService)) {
                                requestLocationUpdates(TrackingMode.HIGH_ACCURACY, forceImmediateFix = true)
                            }
                        }
                    } else if (isNavigating && hasLocationPermission(this@LocationForegroundService)) {
                        acquireWakeLock()
                        ensureForeground(buildStatusNotification(currentMode))
                        requestLocationUpdates(TrackingMode.HIGH_ACCURACY, forceImmediateFix = true)
                    } else if (groupRepo.isBackgroundProtectionActiveAndSignedIn() && hasLocationPermission(this@LocationForegroundService)) {
                        isProtectionActive = true
                        groupRepo.ensurePersonalDevicesListenerActive()
                        if (isProtectionPaused) {
                            ensureForeground(buildStatusNotification(currentMode))
                        } else {
                            startLightweightProtectionMode()
                        }
                    } else {
                        stopForegroundAndSelf()
                    }
                }
            }
        }
        syncProtectionRuntimeState()
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
        releaseWakeLock()
        if (!ensureForeground(buildStatusNotification(mode))) return
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
        if (!ensureForeground(buildStatusNotification(currentMode))) return
        releaseWakeLock()
        stopCellDiagnostics()
        if (currentMode == TrackingMode.POWER_SAVER) {
            startPeriodicCellDiagnostics()
        }
        requestProtectionLocationUpdates()
        startProtectionHeartbeat()

        // Seed immediate device visibility using existing fix, mode-compliant lastLocation, or Room cached fix
        serviceScope.launch {
            repo.awaitInitialRestore()
            val latestRepoSync = groupRepo.lastSyncTimestampMs.value ?: 0L
            val existing = repo.currentLocation.value
            if (existing != null) {
                val now = System.currentTimeMillis()
                if (now - maxOf(lastCloudSyncMs, latestRepoSync) >= 10_000L) {
                    lastCloudSyncMs = now
                    groupRepo.syncDeviceAndGroupTelemetry(existing, repo.trackingMode.value.name)
                }
            } else if (repo.trackingMode.value == TrackingMode.POWER_SAVER) {
                // Cell-Only mode prohibits cached GNSS/Fused fixes; rely on Room fallback or network fallback
                syncCachedProtectionFallback(repo, groupRepo)
                if (repo.lastKnownCachedLocation.value == null) {
                    requestNetworkLastKnownServiceFallback(TrackingMode.POWER_SAVER)
                }
            } else {
                try {
                    fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                        val activeMode = repo.trackingMode.value
                        val modeBlocksFix = activeMode == TrackingMode.POWER_SAVER ||
                            (activeMode == TrackingMode.BALANCED && loc?.provider == LocationManager.GPS_PROVIDER)
                        if (loc != null && !modeBlocksFix) {
                            val normalizedTime = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
                            val point = LocationPoint(
                                latitude = loc.latitude,
                                longitude = loc.longitude,
                                accuracy = loc.accuracy,
                                speed = if (loc.hasSpeed()) loc.speed else 0f,
                                altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
                                bearing = if (loc.hasBearing()) loc.bearing else 0f,
                                provider = loc.provider ?: "fused",
                                timestamp = normalizedTime,
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
                        } else {
                            if (loc != null && activeMode == TrackingMode.BALANCED && loc.provider == LocationManager.GPS_PROVIDER) {
                                requestNetworkLastKnownServiceFallback(activeMode)
                            }
                            serviceScope.launch {
                                syncCachedProtectionFallback(repo, groupRepo)
                            }
                        }
                    }.addOnFailureListener {
                        serviceScope.launch {
                            syncCachedProtectionFallback(repo, groupRepo)
                        }
                    }
                } catch (_: Exception) {
                    syncCachedProtectionFallback(repo, groupRepo)
                }
            }
        }
    }

    private suspend fun syncCachedProtectionFallback(
        repo: com.geotrace.data.LocationRepository,
        groupRepo: com.geotrace.data.GroupRepository
    ) {
        val cached = repo.lastKnownCachedLocation.value ?: return
        val now = System.currentTimeMillis()
        val latest = maxOf(lastCloudSyncMs, groupRepo.lastSyncTimestampMs.value ?: 0L)
        if (now - latest >= 10_000L) {
            lastCloudSyncMs = now
            val tagged = if (cached.provider.startsWith("cached_")) cached else cached.copy(
                provider = "cached_${cached.provider}"
            )
            groupRepo.syncDeviceAndGroupTelemetry(tagged, repo.trackingMode.value.name)
        }
    }

    private fun ensureForeground(notification: Notification): Boolean {
        if (!isForegroundStarted) {
            return try {
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
                lastNotificationUpdateMs = System.currentTimeMillis()
                syncProtectionRuntimeState()
                true
            } catch (e: Exception) {
                Log.e(TAG, "startForeground failed: ${e.message}")
                isForegroundStarted = false
                isServiceActive = false
                isProtectionActive = false
                isProtectionPaused = false
                syncProtectionRuntimeState()
                if (latestStartId > 0) stopSelf(latestStartId) else stopSelf()
                false
            }
        } else {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, notification)
                lastNotificationUpdateMs = System.currentTimeMillis()
                syncProtectionRuntimeState()
            } catch (e: Exception) {
                Log.w(TAG, "notify failed: ${e.message}")
            }
            return true
        }
    }

    private fun buildStatusNotification(mode: TrackingMode): Notification {
        val app = application as LocationTrackerApp
        val repo = app.repository
        val groupRepo = app.groupRepository
        val activeSession = repo.activeSession.value
        val calibratedPoint = repo.currentLocation.value

        if (isNavigating) {
            val navSummary = repo.activeNavNotificationText.value ?: "Turn-by-turn guidance active"
            val isRec = activeSession != null || isRecordingSession
            val title = if (isRec && activeSession != null) {
                "Nav + Track • ${activeSession.formattedDistance}"
            } else {
                "GeoTrace Navigation"
            }
            return buildNotification(
                title = title,
                content = navSummary,
                isRecordingUi = isRec,
                isPausedUi = activeSession?.isPaused == true,
                isNavigatingUi = true
            )
        }

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

        val modeTag = when (mode) {
            TrackingMode.HIGH_ACCURACY -> "GNSS 1s"
            TrackingMode.BALANCED -> "Fused 5s"
            TrackingMode.POWER_SAVER -> "Cell Only"
        }
        val callsign = groupRepo.offlineCallsign.value
        if (isProtectionPaused) {
            return buildNotification(
                title = "24/7 Protection Paused • $callsign",
                content = "$modeTag • Background sync & remote ring paused • Tap Resume",
                isRecordingUi = false,
                isPausedUi = true
            )
        }
        val privacyTag = if (groupRepo.isGhostMode.value) "Ghost Mode (My Devices)" else "Fleet & Remote Ring ready"
        return buildNotification(
            title = "GeoTrace 24/7 ($modeTag) • $callsign",
            content = "$privacyTag • Locatable even when closed",
            isRecordingUi = false,
            isPausedUi = false
        )
    }

    private fun syncCurrentModeTelemetry(mode: TrackingMode) {
        val app = application as LocationTrackerApp
        val repo = app.repository
        val groupRepo = app.groupRepository
        if (groupRepo.getCurrentUserId() == null) return
        val point = repo.currentLocation.value ?: return
        serviceScope.launch {
            lastCloudSyncMs = System.currentTimeMillis()
            groupRepo.syncDeviceAndGroupTelemetry(point, mode.name)
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
    private fun requestImmediateServiceFix(
        mode: TrackingMode,
        allowBalancedFallback: Boolean = true
    ) {
        if (!hasLocationPermission(this)) return
        val repo = (application as LocationTrackerApp).repository
        if (mode == TrackingMode.POWER_SAVER) {
            val cur = repo.currentLocation.value
            if (cur != null && cur.provider == "cellular" && (System.currentTimeMillis() - cur.timestamp) < 25_000L) {
                return
            }
            val lm = systemLocationManager
            val networkEnabled = try {
                lm?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            } catch (_: Exception) {
                false
            }
            if (networkEnabled && lm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    quickFixCancellation?.cancel()
                    quickFixCancellation = null
                    osQuickFixCancellation?.cancel()
                    val osCancel = android.os.CancellationSignal()
                    osQuickFixCancellation = osCancel
                    lm.getCurrentLocation(
                        LocationManager.NETWORK_PROVIDER,
                        osCancel,
                        mainExecutor
                    ) { netLoc ->
                        if (repo.trackingMode.value != TrackingMode.POWER_SAVER) return@getCurrentLocation
                        if (netLoc != null) {
                            onNewLocationReceived(netLoc, bypassStaleModeCheck = true)
                        } else {
                            requestNetworkLastKnownServiceFallback(mode)
                        }
                    }
                    return
                } catch (_: SecurityException) {
                } catch (_: Exception) {
                }
            }
        }

        val priority = when {
            mode == TrackingMode.HIGH_ACCURACY && allowBalancedFallback -> Priority.PRIORITY_HIGH_ACCURACY
            mode == TrackingMode.HIGH_ACCURACY -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            mode == TrackingMode.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
            else -> Priority.PRIORITY_LOW_POWER
        }
        try {
            quickFixCancellation?.cancel()
            osQuickFixCancellation?.cancel()
            osQuickFixCancellation = null
            val cancellation = com.google.android.gms.tasks.CancellationTokenSource()
            quickFixCancellation = cancellation
            val currentRequest = com.google.android.gms.location.CurrentLocationRequest.Builder()
                .setPriority(priority)
                .setMaxUpdateAgeMillis(0L)
                .setDurationMillis(if (mode == TrackingMode.HIGH_ACCURACY) 6_000L else 8_000L)
                .build()
            fusedLocationClient.getCurrentLocation(currentRequest, cancellation.token)
                .addOnSuccessListener { loc ->
                    if (repo.trackingMode.value != mode) return@addOnSuccessListener
                    if (loc == null) {
                        if (mode == TrackingMode.HIGH_ACCURACY && allowBalancedFallback) {
                            requestImmediateServiceFix(
                                mode = TrackingMode.HIGH_ACCURACY,
                                allowBalancedFallback = false
                            )
                        } else {
                            requestNetworkLastKnownServiceFallback(mode)
                        }
                        return@addOnSuccessListener
                    }
                    if (mode == TrackingMode.BALANCED && loc.provider == LocationManager.GPS_PROVIDER) {
                        requestNetworkLastKnownServiceFallback(mode)
                        return@addOnSuccessListener
                    }
                    if (mode == TrackingMode.POWER_SAVER && loc.provider == LocationManager.GPS_PROVIDER) {
                        requestNetworkLastKnownServiceFallback(mode)
                        return@addOnSuccessListener
                    }
                    onNewLocationReceived(loc, bypassStaleModeCheck = true)
                }
                .addOnFailureListener {
                    if (repo.trackingMode.value == mode) {
                        requestNetworkLastKnownServiceFallback(mode)
                    }
                }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestNetworkLastKnownServiceFallback(mode: TrackingMode) {
        if (!hasLocationPermission(this)) return
        val repo = (application as LocationTrackerApp).repository
        if (repo.trackingMode.value != mode) return
        if (mode == TrackingMode.POWER_SAVER) {
            val cur = repo.currentLocation.value
            if (cur != null && cur.provider == "cellular" && (System.currentTimeMillis() - cur.timestamp) < 25_000L) {
                return
            }
        }
        try {
            val netLoc = systemLocationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (netLoc != null && repo.trackingMode.value == mode) {
                onNewLocationReceived(netLoc, bypassStaleModeCheck = true)
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestLocationUpdates(mode: TrackingMode, forceImmediateFix: Boolean = false) {
        stopLocationUpdatesOnly()

        if (forceImmediateFix) {
            requestImmediateServiceFix(mode)
        }

        when (mode) {
            TrackingMode.HIGH_ACCURACY -> {
                val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, mode.intervalMs)
                    .setMinUpdateDistanceMeters(mode.minDistanceMeters)
                    .setMinUpdateIntervalMillis(mode.intervalMs)
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
                // Prefer Play Services Fused Balanced (Wi-Fi/Cell/sensor fusion), falling back to NETWORK_PROVIDER
                val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, mode.intervalMs)
                    .setMinUpdateDistanceMeters(mode.minDistanceMeters)
                    .setMinUpdateIntervalMillis(mode.intervalMs)
                    .build()
                val callback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        result.lastLocation?.let { onNewLocationReceived(it) }
                    }
                }
                locationCallback = callback
                try {
                    fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                } catch (_: SecurityException) {
                    locationCallback = null
                } catch (_: Exception) {
                    locationCallback = null
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
    private fun requestProtectionLocationUpdates(modeChanged: Boolean = false) {
        stopLocationUpdatesOnly()
        val repo = (application as LocationTrackerApp).repository
        val activeMode = repo.trackingMode.value

        if (activeMode == TrackingMode.POWER_SAVER) {
            if (modeChanged) {
                lastBgCellularFallbackKey = null
                lastBgCellularFallbackMs = 0L
            }
            // In Cell-Only standby, let cell diagnostics drive updates; register low-power coarse listener only as fallback
            startPeriodicCellDiagnostics(restart = modeChanged)
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

        // Stop background cell diagnostics loop outside POWER_SAVER protection so ViewModel polling is never starved
        stopCellDiagnostics()
        if (modeChanged && repo.currentLocation.value?.provider == "cellular") {
            requestImmediateServiceFix(activeMode)
        }

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

    private fun onNewLocationReceived(loc: Location, bypassStaleModeCheck: Boolean = false) {
        val app = application as LocationTrackerApp
        val repo = app.repository
        val groupRepo = app.groupRepository
        val activeSession = repo.activeSession.value
        val effectiveMode = repo.trackingMode.value
        val latest = repo.currentLocation.value

        // Ignore stale cached locations captured before the most recent mode switch using monotonic clock first
        val switchElapsedNs = maxOf(lastModeSwitchElapsedNs, repo.lastModeSwitchElapsedNs)
        if (!bypassStaleModeCheck && switchElapsedNs > 0L) {
            if (loc.elapsedRealtimeNanos > 0L) {
                if (loc.elapsedRealtimeNanos < switchElapsedNs - 2_000_000_000L) {
                    return
                }
            } else if (lastModeSwitchMs > 0L && loc.time > 0L && loc.time < lastModeSwitchMs - 2_000L) {
                return
            }
        }

        // In Balanced mode (outside active turn-by-turn nav), suppress raw hardware GPS provider overrides
        if (!isNavigating && effectiveMode == TrackingMode.BALANCED && loc.provider == LocationManager.GPS_PROVIDER) {
            return
        }

        // In Cell-Only mode (outside active turn-by-turn nav), never allow hardware GPS leaks, and defer to fresh OpenCellID mast coordinates
        if (!isNavigating && effectiveMode == TrackingMode.POWER_SAVER) {
            if (loc.provider == LocationManager.GPS_PROVIDER) {
                return
            }
            if (latest != null && latest.provider == "cellular" && (System.currentTimeMillis() - latest.timestamp) < 35_000L) {
                return
            }
        }

        val normalizedTime = loc.time.takeIf { it > 0L } ?: System.currentTimeMillis()
        val point = LocationPoint(
            latitude = loc.latitude,
            longitude = loc.longitude,
            accuracy = loc.accuracy,
            speed = if (loc.hasSpeed()) loc.speed else 0f,
            altitude = if (loc.hasAltitude()) loc.altitude else 0.0,
            bearing = if (loc.hasBearing()) loc.bearing else 0f,
            provider = loc.provider ?: "fused",
            timestamp = normalizedTime,
            sessionId = activeSession?.id ?: if (isRecordingSession) "foreground" else "protection"
        )

        serviceScope.launch {
            repo.recordLocation(point)
            val pendingPct = pendingLowBatteryBeaconPct
            if (pendingPct != null) {
                pendingLowBatteryBeaconPct = null
                persistLastGaspBeacon(repo, groupRepo, point, pendingPct)
            }
            val nowMs = System.currentTimeMillis()
            if (((activeSession != null && !activeSession.isPaused) || isNavigating) && isForegroundStarted &&
                (nowMs - lastNotificationUpdateMs >= 2_500L)
            ) {
                lastNotificationUpdateMs = nowMs
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID, buildStatusNotification(effectiveMode))
            }

            if (groupRepo.getCurrentUserId() != null) {
                val throttleMs = if (isRecordingSession || activeSession != null) 15_000L else 45_000L
                val latestSync = maxOf(lastCloudSyncMs, groupRepo.lastSyncTimestampMs.value ?: 0L)
                if (nowMs - latestSync >= throttleMs) {
                    lastCloudSyncMs = nowMs
                    groupRepo.syncDeviceAndGroupTelemetry(point, repo.trackingMode.value.name)
                }
            }
        }
    }

    private fun stopCellDiagnostics() {
        isCellDiagnosticsActive = false
        cellDiagnosticsJob?.cancel()
        cellDiagnosticsJob = null
    }

    private fun startPeriodicCellDiagnostics(restart: Boolean = false) {
        if (restart) {
            stopCellDiagnostics()
        } else if (cellDiagnosticsJob?.isActive == true) {
            return
        }
        isCellDiagnosticsActive = true
        cellDiagnosticsJob = serviceScope.launch {
            val app = application as LocationTrackerApp
            val repo = app.repository
            val groupRepo = app.groupRepository
            var forceFreshRead = restart
            var fallbackNeededOnMiss = restart
            var cycleIndex = 0
            while (isActive) {
                val modeNow = repo.trackingMode.value
                val isActivelyRecording = isRecordingSession || repo.activeSession.value != null
                val shouldRunFreshModemScan = forceFreshRead ||
                    (modeNow == TrackingMode.POWER_SAVER && cycleIndex % 3 == 0)
                forceFreshRead = false
                var towers = if (shouldRunFreshModemScan) {
                    cellManager.requestFreshCellTowers()
                } else {
                    cellManager.readCurrentCellTowers()
                }
                if (towers.isEmpty() && !shouldRunFreshModemScan) {
                    towers = cellManager.requestFreshCellTowers()
                }
                cycleIndex++
                repo.updateCellTowers(towers)

                // Resolve serving mast in background too so POWER_SAVER stays strictly cellular even with UI closed
                if (modeNow == TrackingMode.POWER_SAVER) {
                    val serving = towers.firstOrNull { it.isRegistered } ?: towers.firstOrNull()
                    val towerLoc = if (serving != null) {
                        OpenCellIdClient.resolveCellLocation(
                            cellInfo = serving,
                            apiKey = repo.getSavedOpenCellIdApiKey()
                        )
                    } else {
                        null
                    }
                    if (towerLoc != null) {
                        fallbackNeededOnMiss = false
                        val now = System.currentTimeMillis()
                        val towerKey = "${towerLoc.cellId}_${towerLoc.lac}"
                        val cur = repo.currentLocation.value
                        val shouldEmit = cur == null || cur.provider != "cellular" ||
                            (now - cur.timestamp > 45_000L) ||
                            (towerKey != lastBgCellularFallbackKey && (now - lastBgCellularFallbackMs >= 15_000L))
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
                                val throttleMs = if (isActivelyRecording) 15_000L else 45_000L
                                val latestSync = maxOf(lastCloudSyncMs, groupRepo.lastSyncTimestampMs.value ?: 0L)
                                if (now - latestSync >= throttleMs) {
                                    lastCloudSyncMs = now
                                    groupRepo.syncDeviceAndGroupTelemetry(cellPoint, modeNow.name)
                                }
                            }
                        }
                    } else if (fallbackNeededOnMiss) {
                        fallbackNeededOnMiss = false
                        if (!isProtectionPaused || isRecordingSession) {
                            requestImmediateServiceFix(TrackingMode.POWER_SAVER)
                        }
                    }
                }
                val delayMs = when {
                    modeNow == TrackingMode.POWER_SAVER && isActivelyRecording -> 20_000L
                    modeNow == TrackingMode.POWER_SAVER -> 45_000L
                    else -> 30_000L
                }
                delay(delayMs)
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
                delay(120_000L)
                if (!groupRepo.isBackgroundProtectionActiveAndSignedIn()) break
                if (isRecordingSession || repo.activeSession.value != null) continue
                val loc = repo.currentLocation.value ?: continue
                val now = System.currentTimeMillis()
                val latestSync = maxOf(lastCloudSyncMs, groupRepo.lastSyncTimestampMs.value ?: 0L)
                if (now - latestSync >= 115_000L) {
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
        isPausedUi: Boolean = false,
        isNavigatingUi: Boolean = false
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingOpen)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (isNavigatingUi) NotificationCompat.CATEGORY_NAVIGATION else NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (isNavigatingUi) {
            val stopNavIntent = Intent(this, LocationForegroundService::class.java).apply {
                action = ACTION_STOP_NAV
                putExtra(EXTRA_FROM_NOTIFICATION, true)
            }
            val pendingStopNav = PendingIntent.getService(
                this,
                7,
                stopNavIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Nav", pendingStopNav)
        }

        if (isRecordingUi) {
            val stopIntent = Intent(this, LocationForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            val pendingStop = PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Stop & Save", pendingStop)

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
        } else if (!isNavigatingUi) {
            val toggleProtectionIntent = Intent(this, LocationForegroundService::class.java).apply {
                action = if (isPausedUi) ACTION_RESUME_PROTECTION else ACTION_PAUSE_PROTECTION
            }
            val pendingToggleProtection = PendingIntent.getService(
                this,
                if (isPausedUi) 5 else 6,
                toggleProtectionIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                if (isPausedUi) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause,
                if (isPausedUi) "Resume" else "Pause",
                pendingToggleProtection
            )

            val turnOffIntent = Intent(this, LocationForegroundService::class.java).apply {
                action = ACTION_STOP_PROTECTION
                putExtra(EXTRA_PERSIST_DISABLE, true)
            }
            val pendingTurnOff = PendingIntent.getService(
                this,
                2,
                turnOffIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Turn Off", pendingTurnOff)
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            try {
                nm.deleteNotificationChannel(LEGACY_CHANNEL_ID)
            } catch (_: Exception) {
            }
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Real-Time GIS Tracking & Protection",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows live location telemetry, recording status, and 24/7 device protection"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)

            if (nm.getNotificationChannel(BATTERY_SAFETY_CHANNEL_ID) == null) {
                val safetyChannel = NotificationChannel(
                    BATTERY_SAFETY_CHANNEL_ID,
                    "Low-Battery Safety Beacon",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts when battery drops critically low and Last-Gasp location beacon is saved"
                    enableVibration(true)
                }
                nm.createNotificationChannel(safetyChannel)
            }
        }
    }

    private fun registerBatteryReceiver() {
        if (batteryReceiverRegistered) return
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != Intent.ACTION_BATTERY_CHANGED) return
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                if (level < 0) return
                val pct = ((level * 100f) / scale.toFloat()).toInt().coerceIn(0, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
                val isCharging = plugged != 0 ||
                    status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                evaluateLowBattery(pct, isCharging)
            }
        }
        batteryReceiver = receiver
        try {
            val sticky = registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryReceiverRegistered = true
            sticky?.let { receiver.onReceive(this, it) }
        } catch (_: Exception) {
            batteryReceiverRegistered = false
        }
    }

    private fun persistLastGaspBeacon(
        repo: com.geotrace.data.LocationRepository,
        groupRepo: com.geotrace.data.GroupRepository,
        point: LocationPoint,
        pct: Int
    ) {
        val servingCell = repo.cellTowers.value.firstOrNull { it.isRegistered }
            ?: repo.cellTowers.value.firstOrNull()
            ?: repo.cachedObservedCells.value.firstOrNull { it.isRegistered }
            ?: repo.cachedObservedCells.value.firstOrNull()
        val cellSummary = servingCell?.let {
            "${it.operatorDisplayName} (CID ${it.cid ?: "N/A"}, LAC ${it.lac ?: "N/A"})"
        } ?: ""
        repo.saveLastGaspBeacon(
            LastGaspBeacon(
                latitude = point.latitude,
                longitude = point.longitude,
                accuracy = point.accuracy,
                altitude = point.altitude,
                batteryPercent = pct,
                timestamp = System.currentTimeMillis(),
                cellSummary = cellSummary
            )
        )
        serviceScope.launch {
            groupRepo.syncDeviceAndGroupTelemetry(
                location = point,
                trackingModeName = currentMode.name,
                force = true,
                includeP2PBroadcast = true
            )
        }
    }

    private fun evaluateLowBattery(pct: Int, isCharging: Boolean) {
        val decision = computeBatterySafetyDecision(
            pct = pct,
            isCharging = isCharging,
            lastTriggeredBeaconThreshold = lastTriggeredBatteryThreshold,
            lastSafetyNotifiedThreshold = lastSafetyNotifiedThreshold
        )
        if (decision.resetState) {
            pendingLowBatteryBeaconPct = null
            lastTriggeredBatteryThreshold = decision.nextTriggeredBeaconThreshold
            lastSafetyNotifiedThreshold = decision.nextNotifiedThreshold
            if (decision.shouldCancelNotification) {
                try {
                    (getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager)
                        ?.cancel(BATTERY_SAFETY_NOTIFICATION_ID)
                } catch (_: Exception) {}
            }
            return
        }

        lastTriggeredBatteryThreshold = decision.nextTriggeredBeaconThreshold
        lastSafetyNotifiedThreshold = decision.nextNotifiedThreshold

        val app = application as LocationTrackerApp
        val repo = app.repository
        val groupRepo = app.groupRepository
        val livePoint = repo.currentLocation.value
        val cachedPoint = repo.lastKnownCachedLocation.value
        val fallbackPoint = livePoint ?: cachedPoint

        if (decision.captureBeaconThreshold != null) {
            if (livePoint != null) {
                pendingLowBatteryBeaconPct = null
                persistLastGaspBeacon(repo, groupRepo, livePoint, pct)
            } else if (cachedPoint != null) {
                // Immediately persist Room cached fix as safety net, while keeping pending pct so a live GNSS fix upgrades it
                pendingLowBatteryBeaconPct = pct
                val taggedCached = if (cachedPoint.provider.startsWith("cached_")) {
                    cachedPoint
                } else {
                    cachedPoint.copy(provider = "cached_${cachedPoint.provider}")
                }
                persistLastGaspBeacon(repo, groupRepo, taggedCached, pct)
            } else {
                pendingLowBatteryBeaconPct = pct
            }
        }

        if (decision.notifyThreshold != null && hasNotificationPermission(this)) {
            postLowBatterySafetyNotification(pct, hasPoint = fallbackPoint != null)
        }
    }

    private fun postLowBatterySafetyNotification(pct: Int, hasPoint: Boolean) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this, 4199, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (hasPoint) {
            "Low Battery ($pct%) • Beacon Saved"
        } else {
            "Low Battery ($pct%) • Critical"
        }
        val body = if (hasPoint) {
            "Last-Gasp location beacon saved at $pct%. Switch to Power Saver to extend range."
        } else {
            "Battery at $pct% (awaiting GPS fix for beacon). Switch to Power Saver to preserve range."
        }
        val builder = NotificationCompat.Builder(this, BATTERY_SAFETY_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingOpen)
            .setAutoCancel(true)

        if (currentMode != TrackingMode.POWER_SAVER) {
            val powerSaverIntent = Intent(this, LocationForegroundService::class.java).apply {
                action = ACTION_UPDATE_MODE
                putExtra(EXTRA_MODE, TrackingMode.POWER_SAVER.name)
                putExtra(EXTRA_STARTED_AS_FOREGROUND, true)
            }
            val pendingPowerSaver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                PendingIntent.getForegroundService(
                    this, 4200, powerSaverIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                PendingIntent.getService(
                    this, 4200, powerSaverIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            builder.addAction(
                android.R.drawable.ic_lock_idle_low_battery,
                "Power Saver",
                pendingPowerSaver
            )
        }
        nm.notify(BATTERY_SAFETY_NOTIFICATION_ID, builder.build())
    }

    private fun stopTrackingOrDowngradeInternal() {
        val app = application as LocationTrackerApp
        val repo = app.repository
        val groupRepo = app.groupRepository

        isRecordingSession = false
        if (!isNavigating) {
            releaseWakeLock()
        }
        stopCellDiagnostics()

        serviceScope.launch {
            if (repo.activeSession.value != null) {
                repo.stopCurrentSession()
            }
            if (isNavigating && hasLocationPermission(this@LocationForegroundService)) {
                acquireWakeLock()
                ensureForeground(buildStatusNotification(currentMode))
                requestLocationUpdates(TrackingMode.HIGH_ACCURACY, forceImmediateFix = true)
            } else if (groupRepo.isBackgroundProtectionActiveAndSignedIn() && hasLocationPermission(this@LocationForegroundService)) {
                isProtectionActive = true
                groupRepo.ensurePersonalDevicesListenerActive()
                if (isProtectionPaused) {
                    stopLocationUpdatesOnly()
                    stopProtectionHeartbeat()
                    ensureForeground(buildStatusNotification(currentMode))
                } else {
                    startLightweightProtectionMode()
                }
            } else {
                isProtectionActive = false
                isProtectionPaused = false
                stopLocationUpdatesOnly()
                stopForegroundAndSelf()
            }
        }
    }

    private fun stopLocationUpdatesOnly() {
        quickFixCancellation?.cancel()
        quickFixCancellation = null
        osQuickFixCancellation?.cancel()
        osQuickFixCancellation = null
        locationCallback?.let { cb ->
            try {
                fusedLocationClient.removeLocationUpdates(cb)
            } catch (_: Exception) {
            }
        }
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
        isProtectionActive = false
        isProtectionPaused = false
        isNavigating = false
        releaseWakeLock()
        stopCellDiagnostics()
        stopProtectionHeartbeat()
        val wasForeground = isForegroundStarted
        isForegroundStarted = false
        syncProtectionRuntimeState()
        if (wasForeground) {
            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }
        }
        if (latestStartId > 0) {
            stopSelfResult(latestStartId)
        } else {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (batteryReceiverRegistered) {
            try {
                batteryReceiver?.let { unregisterReceiver(it) }
            } catch (_: Exception) {}
            batteryReceiverRegistered = false
        }
        isServiceActive = false
        isProtectionActive = false
        isProtectionPaused = false
        isNavigating = false
        stopLocationUpdatesOnly()
        stopCellDiagnostics()
        stopProtectionHeartbeat()
        releaseWakeLock()
        isForegroundStarted = false
        syncProtectionRuntimeState()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
