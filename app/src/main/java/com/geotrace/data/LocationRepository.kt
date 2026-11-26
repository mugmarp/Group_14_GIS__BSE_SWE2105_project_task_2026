package com.geotrace.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.geotrace.model.AppThemeMode
import com.geotrace.model.CellTowerInfo
import com.geotrace.model.CoordinateFormat
import com.geotrace.model.GeofenceAlert
import com.geotrace.model.GeofenceCategory
import com.geotrace.model.GeofenceZone
import com.geotrace.model.LastGaspBeacon
import com.geotrace.model.LocationPoint
import com.geotrace.model.MapTileType
import com.geotrace.model.OfflineMapRegion
import com.geotrace.model.OfflinePlacePoi
import com.geotrace.model.ParkedSpot
import com.geotrace.model.SavedPlace
import com.geotrace.model.SpeedUnit
import com.geotrace.model.TrackingMode
import com.geotrace.model.TrackingSession
import com.geotrace.model.TrailWaypoint
import com.geotrace.model.TripGroup
import org.json.JSONObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

import com.geotrace.data.cache.OfflineCacheDatabase
import com.geotrace.data.cache.OfflineCacheRepository

class LocationRepository(
    private val context: Context,
    private val offlineCacheRepository: OfflineCacheRepository = OfflineCacheRepository(
        OfflineCacheDatabase.getInstance(context)
    )
) {

    private val dbHelper = LocationDbHelper(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val recordingMutex = Mutex()
    private val geofenceMutex = Mutex()

    private val _currentLocation = MutableStateFlow<LocationPoint?>(null)
    val currentLocation: StateFlow<LocationPoint?> = _currentLocation.asStateFlow()

    private val _lastKnownCachedLocation = MutableStateFlow<LocationPoint?>(null)
    val lastKnownCachedLocation: StateFlow<LocationPoint?> = _lastKnownCachedLocation.asStateFlow()

    private val _cachedObservedCells = MutableStateFlow<List<CellTowerInfo>>(emptyList())
    val cachedObservedCells: StateFlow<List<CellTowerInfo>> = _cachedObservedCells.asStateFlow()

    private val _cachedObservedAtMs = MutableStateFlow<Long?>(null)
    val cachedObservedAtMs: StateFlow<Long?> = _cachedObservedAtMs.asStateFlow()

    private val _cachedLocationFixCount = MutableStateFlow(0)
    val cachedLocationFixCount: StateFlow<Int> = _cachedLocationFixCount.asStateFlow()

    private val _cachedCellTowerCount = MutableStateFlow(0)
    val cachedCellTowerCount: StateFlow<Int> = _cachedCellTowerCount.asStateFlow()

    private val _activeSession = MutableStateFlow<TrackingSession?>(null)
    val activeSession: StateFlow<TrackingSession?> = _activeSession.asStateFlow()

    private val _activeTrailPoints = MutableStateFlow<List<LocationPoint>>(emptyList())
    val activeTrailPoints: StateFlow<List<LocationPoint>> = _activeTrailPoints.asStateFlow()

    private val _activeWaypoints = MutableStateFlow<List<TrailWaypoint>>(emptyList())
    val activeWaypoints: StateFlow<List<TrailWaypoint>> = _activeWaypoints.asStateFlow()

    private val _allSessions = MutableStateFlow<List<TrackingSession>>(emptyList())
    val allSessions: StateFlow<List<TrackingSession>> = _allSessions.asStateFlow()

    private val _geofenceZones = MutableStateFlow<List<GeofenceZone>>(emptyList())
    val geofenceZones: StateFlow<List<GeofenceZone>> = _geofenceZones.asStateFlow()

    private val _recentAlerts = MutableStateFlow<List<GeofenceAlert>>(emptyList())
    val recentAlerts: StateFlow<List<GeofenceAlert>> = _recentAlerts.asStateFlow()

    private val _cellTowers = MutableStateFlow<List<CellTowerInfo>>(emptyList())
    val cellTowers: StateFlow<List<CellTowerInfo>> = _cellTowers.asStateFlow()

    private val _offlineMapRegions = MutableStateFlow<List<OfflineMapRegion>>(emptyList())
    val offlineMapRegions: StateFlow<List<OfflineMapRegion>> = _offlineMapRegions.asStateFlow()

    private val _offlinePlacesCount = MutableStateFlow(0)
    val offlinePlacesCount: StateFlow<Int> = _offlinePlacesCount.asStateFlow()

    private val _offlinePlacesCountByRegion = MutableStateFlow<Map<String, Int>>(emptyMap())
    val offlinePlacesCountByRegion: StateFlow<Map<String, Int>> = _offlinePlacesCountByRegion.asStateFlow()

    private val prefs = context.getSharedPreferences("geotrace_prefs", Context.MODE_PRIVATE)

    private val _trackingMode = MutableStateFlow(loadInitialTrackingMode())
    val trackingMode: StateFlow<TrackingMode> = _trackingMode.asStateFlow()

    private val _filteredJitterMeters = MutableStateFlow(0.0)
    val filteredJitterMeters: StateFlow<Double> = _filteredJitterMeters.asStateFlow()

    private val _filteredJitterCount = MutableStateFlow(0)
    val filteredJitterCount: StateFlow<Int> = _filteredJitterCount.asStateFlow()

    private val _parkedSpot = MutableStateFlow(loadParkedSpot())
    val parkedSpot: StateFlow<ParkedSpot?> = _parkedSpot.asStateFlow()

    private val _savedPlaces = MutableStateFlow<List<SavedPlace>>(emptyList())
    val savedPlaces: StateFlow<List<SavedPlace>> = _savedPlaces.asStateFlow()

    private val _trips = MutableStateFlow<List<TripGroup>>(emptyList())
    val trips: StateFlow<List<TripGroup>> = _trips.asStateFlow()

    private val _lastGaspBeacon = MutableStateFlow(loadLastGaspBeacon())
    val lastGaspBeacon: StateFlow<LastGaspBeacon?> = _lastGaspBeacon.asStateFlow()

    private fun loadLastGaspBeacon(): LastGaspBeacon? {
        val raw = prefs.getString("last_gasp_beacon_json", null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val lat = obj.optDouble("latitude", Double.NaN)
            val lon = obj.optDouble("longitude", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite() || (lat == 0.0 && lon == 0.0)) return null
            LastGaspBeacon(
                latitude = lat,
                longitude = lon,
                accuracy = obj.optDouble("accuracy", 0.0).toFloat(),
                altitude = obj.optDouble("altitude", 0.0),
                batteryPercent = obj.optInt("batteryPercent", 0),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                cellSummary = obj.optString("cellSummary")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveLastGaspBeacon(beacon: LastGaspBeacon) {
        val obj = JSONObject().apply {
            put("latitude", beacon.latitude)
            put("longitude", beacon.longitude)
            put("accuracy", beacon.accuracy.toDouble())
            put("altitude", beacon.altitude)
            put("batteryPercent", beacon.batteryPercent)
            put("timestamp", beacon.timestamp)
            put("cellSummary", beacon.cellSummary)
        }
        prefs.edit().putString("last_gasp_beacon_json", obj.toString()).apply()
        _lastGaspBeacon.value = beacon
    }

    fun clearLastGaspBeacon() {
        prefs.edit().remove("last_gasp_beacon_json").apply()
        _lastGaspBeacon.value = null
    }

    private fun loadParkedSpot(): ParkedSpot? {
        val raw = prefs.getString("parked_spot_json", null) ?: return null
        return try {
            val obj = JSONObject(raw)
            val lat = obj.optDouble("latitude", Double.NaN)
            val lon = obj.optDouble("longitude", Double.NaN)
            if (!lat.isFinite() || !lon.isFinite() || (lat == 0.0 && lon == 0.0)) return null
            ParkedSpot(
                name = obj.optString("name").ifBlank { "Parked Vehicle" },
                note = obj.optString("note"),
                latitude = lat,
                longitude = lon,
                savedAtMs = obj.optLong("savedAtMs", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveParkedSpot(spot: ParkedSpot) {
        val obj = JSONObject().apply {
            put("name", spot.name)
            put("note", spot.note)
            put("latitude", spot.latitude)
            put("longitude", spot.longitude)
            put("savedAtMs", spot.savedAtMs)
        }
        prefs.edit().putString("parked_spot_json", obj.toString()).apply()
        _parkedSpot.value = spot
    }

    fun clearParkedSpot() {
        prefs.edit().remove("parked_spot_json").apply()
        _parkedSpot.value = null
    }

    @Volatile
    var geofenceNotificationsEnabled: Boolean = prefs.getBoolean("geofence_notifications_enabled", false)
        private set

    fun setGeofenceNotificationsEnabled(enabled: Boolean) {
        geofenceNotificationsEnabled = enabled
        prefs.edit().putBoolean("geofence_notifications_enabled", enabled).apply()
    }

    fun getSavedSpeedUnit(): SpeedUnit {
        val raw = prefs.getString("speed_unit", null) ?: return SpeedUnit.KMH
        return try { SpeedUnit.valueOf(raw) } catch (_: Exception) { SpeedUnit.KMH }
    }

    fun saveSpeedUnit(unit: SpeedUnit) {
        prefs.edit().putString("speed_unit", unit.name).apply()
    }

    fun getSavedCoordinateFormat(): CoordinateFormat {
        val raw = prefs.getString("coordinate_format", null) ?: return CoordinateFormat.DECIMAL
        return try { CoordinateFormat.valueOf(raw) } catch (_: Exception) { CoordinateFormat.DECIMAL }
    }

    fun saveCoordinateFormat(format: CoordinateFormat) {
        prefs.edit().putString("coordinate_format", format.name).apply()
    }

    fun getSavedShowAccuracyRing(): Boolean = prefs.getBoolean("show_accuracy_ring", true)

    fun saveShowAccuracyRing(show: Boolean) {
        prefs.edit().putBoolean("show_accuracy_ring", show).apply()
    }

    fun getSavedFollowUserLocation(): Boolean = prefs.getBoolean("follow_user_location", true)

    fun saveFollowUserLocation(follow: Boolean) {
        prefs.edit().putBoolean("follow_user_location", follow).apply()
    }

    fun getSavedAutoRotateHeadingUp(): Boolean = prefs.getBoolean("auto_rotate_heading_up", false)

    fun saveAutoRotateHeadingUp(enabled: Boolean) {
        prefs.edit().putBoolean("auto_rotate_heading_up", enabled).apply()
    }

    fun getSavedKeepScreenOn(): Boolean = prefs.getBoolean("keep_screen_on", false)

    fun saveKeepScreenOn(keepOn: Boolean) {
        prefs.edit().putBoolean("keep_screen_on", keepOn).apply()
    }

    fun getSavedThemeMode(): AppThemeMode {
        val raw = prefs.getString("app_theme_mode", null) ?: return AppThemeMode.LIGHT
        return try {
            AppThemeMode.valueOf(raw)
        } catch (_: Exception) {
            AppThemeMode.LIGHT
        }
    }

    fun saveThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("app_theme_mode", mode.name).apply()
    }

    fun getSavedShowCellTowerPin(): Boolean = prefs.getBoolean("show_cell_tower_pin", true)

    fun saveShowCellTowerPin(show: Boolean) {
        prefs.edit().putBoolean("show_cell_tower_pin", show).apply()
    }

    fun getSavedJitterThreshold(): Float = prefs.getFloat("jitter_threshold_meters", 1.5f)

    fun saveJitterThreshold(thresholdMeters: Float) {
        jitterThresholdMeters = thresholdMeters
        prefs.edit().putFloat("jitter_threshold_meters", thresholdMeters).apply()
    }

    fun getSavedOpenCellIdApiKey(): String = prefs.getString("opencellid_api_key", "") ?: ""

    fun saveOpenCellIdApiKey(apiKey: String) {
        prefs.edit().putString("opencellid_api_key", apiKey.trim()).apply()
    }

    fun getSavedMapTileType(): MapTileType {
        val raw = prefs.getString("map_tile_type", null) ?: return MapTileType.STANDARD
        return try {
            MapTileType.valueOf(raw)
        } catch (_: Exception) {
            MapTileType.STANDARD
        }
    }

    fun saveMapTileType(tileType: MapTileType) {
        prefs.edit().putString("map_tile_type", tileType.name).apply()
    }

    fun getSavedGeofenceHysteresis(): Float = prefs.getFloat("geofence_hysteresis_meters", 10f)

    fun saveGeofenceHysteresis(meters: Float) {
        geofenceHysteresisMeters = meters
        prefs.edit().putFloat("geofence_hysteresis_meters", meters).apply()
    }

    fun getSavedGeofenceAccuracyGate(): Boolean = prefs.getBoolean("geofence_accuracy_gate", true)

    fun saveGeofenceAccuracyGate(enabled: Boolean) {
        geofenceAccuracyGateEnabled = enabled
        prefs.edit().putBoolean("geofence_accuracy_gate", enabled).apply()
    }

    private fun loadInitialTrackingMode(): TrackingMode {
        val saved = prefs.getString("selected_tracking_mode", null)
        if (!saved.isNullOrBlank()) {
            try {
                return TrackingMode.valueOf(saved)
            } catch (_: Exception) {}
        }
        return TrackingMode.HIGH_ACCURACY
    }

    @Volatile
    var jitterThresholdMeters: Float = getSavedJitterThreshold()

    @Volatile
    var geofenceHysteresisMeters: Float = getSavedGeofenceHysteresis()

    @Volatile
    var geofenceAccuracyGateEnabled: Boolean = getSavedGeofenceAccuracyGate()

    @Volatile
    private var smoothedSpeedMps: Float = 0f

    @Volatile
    private var stationaryAnchorPoint: LocationPoint? = null

    @Volatile
    private var consecutiveSlowSamples: Int = 0

    @Volatile
    private var resetAnchorOnNextFix: Boolean = false

    @Volatile
    private var resetAnchorOnModeSwitch: Boolean = false

    @Volatile
    var lastModeSwitchElapsedNs: Long = 0L
        private set

    private val initialRestoreDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()

    private val transitionCooldownLock = Any()
    private val lastNotifiedTransitionMs = mutableMapOf<String, Pair<Boolean, Long>>()

    init {
        loadInitialData()
    }

    suspend fun awaitInitialRestore() {
        initialRestoreDeferred.await()
    }

    private fun loadInitialData() {
        scope.launch {
            offlineCacheRepository.latestCachedLocationFlow.collect { latest ->
                if (latest != null) {
                    val live = _currentLocation.value
                    val curCached = _lastKnownCachedLocation.value
                    if (live == null && (curCached == null || latest.timestamp >= curCached.timestamp)) {
                        _lastKnownCachedLocation.value = latest
                    }
                }
            }
        }
        scope.launch {
            offlineCacheRepository.latestObservedCellsFlow.collect { cells ->
                if (_cellTowers.value.isEmpty() && cells.isNotEmpty()) {
                    _cachedObservedCells.value = cells
                }
            }
        }
        scope.launch {
            offlineCacheRepository.latestObservedAtMsFlow.collect { ts ->
                if (_cellTowers.value.isEmpty() && ts != null) {
                    _cachedObservedAtMs.value = ts
                }
            }
        }
        scope.launch {
            offlineCacheRepository.cachedLocationFixCountFlow.collect { count ->
                _cachedLocationFixCount.value = count
            }
        }
        scope.launch {
            offlineCacheRepository.cachedCellTowerCountFlow.collect { count ->
                _cachedCellTowerCount.value = count
            }
        }
        scope.launch {
            try {
                val cachedFixSnap = offlineCacheRepository.getLatestCachedLocationSnapshot()
                if (cachedFixSnap != null && _currentLocation.value == null) {
                    val curCached = _lastKnownCachedLocation.value
                    if (curCached == null || cachedFixSnap.timestamp >= curCached.timestamp) {
                        _lastKnownCachedLocation.value = cachedFixSnap
                    }
                }
                val cachedCellsSnap = offlineCacheRepository.getLatestObservedCellsSnapshot()
                val cachedObservedTs = offlineCacheRepository.getLatestObservedAtMsSnapshot()
                if (cachedCellsSnap.isNotEmpty() && _cellTowers.value.isEmpty() && _cachedObservedCells.value.isEmpty()) {
                    _cachedObservedCells.value = cachedCellsSnap
                    _cachedObservedAtMs.value = cachedObservedTs
                }
                loadGeofences()
                restoreActiveSessionIfAny()
                loadTripsInternal()
                loadSessions()
                loadSavedPlacesInternal()
                loadAlerts()
                loadOfflineMapRegions()
            } finally {
                initialRestoreDeferred.complete(Unit)
            }
        }
    }

    private suspend fun restoreActiveSessionIfAny() = recordingMutex.withLock {
        val db = dbHelper.writableDatabase
        val openSessions = mutableListOf<TrackingSession>()
        val cursor = db.query(
            LocationDbHelper.TABLE_SESSIONS,
            null,
            "is_recording = 1",
            null,
            null,
            null,
            "start_time DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                openSessions.add(readSessionFromCursor(c))
            }
        }
        if (openSessions.isEmpty()) return@withLock

        val latest = openSessions.first()
        // Any older stale orphaned sessions get finalized cleanly to their last recorded point timestamp
        if (openSessions.size > 1) {
            for (orphan in openSessions.drop(1)) {
                finalizeOrphanSession(db, orphan)
            }
        }

        // Restore active session points and waypoints into StateFlows
        val restoredPoints = queryPointsSync(db, latest.id)
        val restoredWaypoints = queryWaypointsSync(db, latest.id)
        // If active session was dead for > 20 minutes (e.g. crashed long ago with 0 foreground service), auto-finalize instead of running forever
        val lastActivityMs = maxOf(
            restoredPoints.lastOrNull()?.timestamp ?: 0L,
            restoredWaypoints.lastOrNull()?.timestamp ?: 0L,
            latest.startTime
        )
        if (System.currentTimeMillis() - lastActivityMs > 20L * 60L * 1000L && !latest.isPaused) {
            finalizeOrphanSession(db, latest, lastPointMs = lastActivityMs)
        } else {
            val synced = latest.copy(
                pointCount = restoredPoints.size.coerceAtLeast(latest.pointCount),
                waypointCount = restoredWaypoints.size.coerceAtLeast(latest.waypointCount)
            )
            _activeSession.value = synced
            _activeTrailPoints.value = restoredPoints
            _activeWaypoints.value = restoredWaypoints
            stationaryAnchorPoint = restoredPoints.lastOrNull()
            resetAnchorOnNextFix = synced.isPaused
        }
    }

    private fun finalizeOrphanSession(
        db: android.database.sqlite.SQLiteDatabase,
        session: TrackingSession,
        lastPointMs: Long? = null
    ) {
        val points = queryPointsSync(db, session.id)
        val waypoints = queryWaypointsSync(db, session.id)
        val lastEventMs = maxOf(
            points.lastOrNull()?.timestamp ?: 0L,
            waypoints.lastOrNull()?.timestamp ?: 0L,
            session.pauseStartedMs ?: 0L,
            session.startTime
        )
        val endMs = (lastPointMs ?: lastEventMs).coerceAtLeast(session.startTime)
        val extraPaused = if (session.isPaused && session.pauseStartedMs != null) {
            (endMs - session.pauseStartedMs).coerceAtLeast(0L)
        } else {
            0L
        }
        val finalPausedAcc = session.pausedAccumulatedMs + extraPaused
        val cv = ContentValues().apply {
            put("is_recording", 0)
            put("is_paused", 0)
            put("paused_accumulated_ms", finalPausedAcc)
            putNull("pause_started_ms")
            put("end_time", endMs)
            put("point_count", points.size.coerceAtLeast(session.pointCount))
            put("waypoint_count", waypoints.size.coerceAtLeast(session.waypointCount))
        }
        db.update(LocationDbHelper.TABLE_SESSIONS, cv, "id = ?", arrayOf(session.id))
    }

    fun setTrackingModeSync(mode: TrackingMode) {
        if (_trackingMode.value != mode) {
            resetAnchorOnModeSwitch = true
            lastModeSwitchElapsedNs = android.os.SystemClock.elapsedRealtimeNanos()
        }
        _trackingMode.value = mode
        prefs.edit().putString("selected_tracking_mode", mode.name).apply()
        val current = _activeSession.value
        if (current != null && current.isRecording && current.trackingMode != mode) {
            _activeSession.value = current.copy(trackingMode = mode)
        }
    }

    suspend fun setTrackingMode(mode: TrackingMode) {
        setTrackingModeSync(mode)
        withContext(Dispatchers.IO) {
            recordingMutex.withLock {
                val session = _activeSession.value
                if (session != null && session.isRecording) {
                    val updated = if (session.trackingMode == mode) session else session.copy(trackingMode = mode)
                    _activeSession.value = updated
                    val cv = ContentValues().apply {
                        put("tracking_mode", mode.name)
                    }
                    dbHelper.writableDatabase.update(LocationDbHelper.TABLE_SESSIONS, cv, "id = ?", arrayOf(session.id))
                    loadSessionsInternal()
                }
            }
        }
    }

    suspend fun startNewSession(
        name: String = "Track ${System.currentTimeMillis() % 10000}",
        mode: TrackingMode = _trackingMode.value,
        tripId: String? = null
    ): TrackingSession = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val existing = _activeSession.value
            if (existing != null) {
                stopCurrentSessionInternal(existing)
            }
            val db = dbHelper.writableDatabase
            val validTripId = resolveExistingTripId(db, tripId)
            val id = UUID.randomUUID().toString()
            val session = TrackingSession(
                id = id,
                name = name.trim().ifBlank { "Field Trail" },
                startTime = System.currentTimeMillis(),
                trackingMode = mode,
                isRecording = true,
                isPaused = false,
                pausedAccumulatedMs = 0L,
                pauseStartedMs = null,
                waypointCount = 0,
                tripId = validTripId
            )

            val cv = ContentValues().apply {
                put("id", session.id)
                put("name", session.name)
                put("start_time", session.startTime)
                put("distance", 0.0)
                put("avg_speed", 0.0)
                put("max_speed", 0.0)
                put("point_count", 0)
                put("waypoint_count", 0)
                put("tracking_mode", session.trackingMode.name)
                put("is_recording", 1)
                put("is_paused", 0)
                put("paused_accumulated_ms", 0L)
                putNull("pause_started_ms")
                if (validTripId != null) {
                    put("trip_id", validTripId)
                } else {
                    putNull("trip_id")
                }
            }
            var finalSession = session
            val inserted = db.insert(LocationDbHelper.TABLE_SESSIONS, null, cv)
            if (inserted == -1L && validTripId != null) {
                cv.putNull("trip_id")
                db.insert(LocationDbHelper.TABLE_SESSIONS, null, cv)
                finalSession = session.copy(tripId = null)
            }

            _activeSession.value = finalSession
            _activeTrailPoints.value = emptyList()
            _activeWaypoints.value = emptyList()
            smoothedSpeedMps = 0f
            stationaryAnchorPoint = null
            consecutiveSlowSamples = 0
            resetAnchorOnNextFix = false
            loadSessionsInternal()
            finalSession
        }
    }

    suspend fun pauseCurrentSession(): TrackingSession? = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val current = _activeSession.value ?: return@withLock null
            if (!current.isRecording || current.isPaused) return@withLock current
            val now = System.currentTimeMillis()
            val updated = current.copy(isPaused = true, pauseStartedMs = now)
            val cv = ContentValues().apply {
                put("is_paused", 1)
                put("pause_started_ms", now)
            }
            dbHelper.writableDatabase.update(LocationDbHelper.TABLE_SESSIONS, cv, "id = ?", arrayOf(current.id))
            _activeSession.value = updated
            loadSessionsInternal()
            updated
        }
    }

    suspend fun resumeCurrentSession(): TrackingSession? = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val current = _activeSession.value ?: return@withLock null
            if (!current.isRecording || !current.isPaused) return@withLock current
            val now = System.currentTimeMillis()
            val extraPaused = current.pauseStartedMs?.let { (now - it).coerceAtLeast(0L) } ?: 0L
            val newAccumulated = current.pausedAccumulatedMs + extraPaused
            val updated = current.copy(
                isPaused = false,
                pausedAccumulatedMs = newAccumulated,
                pauseStartedMs = null
            )
            val cv = ContentValues().apply {
                put("is_paused", 0)
                put("paused_accumulated_ms", newAccumulated)
                putNull("pause_started_ms")
            }
            dbHelper.writableDatabase.update(LocationDbHelper.TABLE_SESSIONS, cv, "id = ?", arrayOf(current.id))
            _activeSession.value = updated
            stationaryAnchorPoint = null
            consecutiveSlowSamples = 0
            resetAnchorOnNextFix = true
            loadSessionsInternal()
            updated
        }
    }

    suspend fun stopCurrentSession(): TrackingSession? = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val current = _activeSession.value ?: return@withLock null
            stopCurrentSessionInternal(current)
        }
    }
    private suspend fun stopCurrentSessionInternal(current: TrackingSession): TrackingSession {
        val endTime = System.currentTimeMillis()
        val finalPausedAcc = current.pausedAccumulatedMs +
            (current.pauseStartedMs?.let { (endTime - it).coerceAtLeast(0L) } ?: 0L)
        val db = dbHelper.writableDatabase

        // Audit points in SQLite database using anchor-based accumulation so walking steps < 1.5m sum accurately
        var dbCount = 0
        var dbDistance = 0.0
        val effectiveDeadband = jitterThresholdMeters.toDouble().coerceAtLeast(0.0)
        val cursor = db.query(
            LocationDbHelper.TABLE_POINTS,
            arrayOf("latitude", "longitude", "provider"),
            "session_id = ?",
            arrayOf(current.id),
            null, null, "timestamp ASC"
        )
        cursor.use { c ->
            var anchorLat: Double? = null
            var anchorLon: Double? = null
            var lastProvider: String? = null
            while (c.moveToNext()) {
                dbCount++
                val lat = c.getDouble(0)
                val lon = c.getDouble(1)
                val provider = c.getString(2)
                if (anchorLat != null && anchorLon != null) {
                    val isProviderSwitch = (lastProvider == "cellular") != (provider == "cellular")
                    if (isProviderSwitch) {
                        anchorLat = lat
                        anchorLon = lon
                    } else {
                        val stepDist = calculateDistanceMeters(anchorLat, anchorLon, lat, lon)
                        if (stepDist in effectiveDeadband..5000.0) {
                            dbDistance += stepDist
                            anchorLat = lat
                            anchorLon = lon
                        }
                    }
                } else {
                    anchorLat = lat
                    anchorLon = lon
                }
                lastProvider = provider
            }
        }

        val finalDistance = if (current.totalDistanceMeters > 0.0) current.totalDistanceMeters else dbDistance
        val finalCount = if (dbCount > 0) dbCount else current.pointCount
        val activeDurationSec = ((endTime - current.startTime - finalPausedAcc) / 1000.0).coerceAtLeast(1.0)
        val finalAvgSpeed = (finalDistance / activeDurationSec) * 3.6
        val finalWaypointCount = queryWaypointsSync(db, current.id).size.coerceAtLeast(current.waypointCount)

        val updated = current.copy(
            endTime = endTime,
            totalDistanceMeters = finalDistance,
            pointCount = finalCount,
            waypointCount = finalWaypointCount,
            avgSpeedKmh = finalAvgSpeed,
            isRecording = false,
            isPaused = false,
            pausedAccumulatedMs = finalPausedAcc,
            pauseStartedMs = null
        )

        val cv = ContentValues().apply {
            put("end_time", endTime)
            put("is_recording", 0)
            put("is_paused", 0)
            put("paused_accumulated_ms", finalPausedAcc)
            putNull("pause_started_ms")
            put("distance", finalDistance)
            put("avg_speed", finalAvgSpeed)
            put("max_speed", updated.maxSpeedKmh)
            put("point_count", finalCount)
            put("waypoint_count", finalWaypointCount)
        }
        db.update(LocationDbHelper.TABLE_SESSIONS, cv, "id = ?", arrayOf(current.id))

        _activeSession.value = null
        _activeTrailPoints.value = emptyList()
        _activeWaypoints.value = emptyList()
        smoothedSpeedMps = 0f
        stationaryAnchorPoint = null
        consecutiveSlowSamples = 0
        resetAnchorOnNextFix = false
        loadSessionsInternal()
        return updated
    }

    suspend fun recordLocation(rawPoint: LocationPoint) = withContext(Dispatchers.IO) {
        // Discard invalid, out-of-range, NaN, and Null-Island glitch coordinates
        if (!rawPoint.latitude.isFinite() || !rawPoint.longitude.isFinite() ||
            rawPoint.latitude !in -90.0..90.0 || rawPoint.longitude !in -180.0..180.0 ||
            (rawPoint.latitude == 0.0 && rawPoint.longitude == 0.0)
        ) {
            return@withContext
        }

        val normalizedTimestamp = rawPoint.timestamp.takeIf { it > 0L } ?: System.currentTimeMillis()
        val sanitizedAccuracy = if (rawPoint.accuracy.isFinite() && rawPoint.accuracy >= 0f) rawPoint.accuracy else 0f
        val sanitizedSpeed = if (rawPoint.speed.isFinite() && rawPoint.speed >= 0f) rawPoint.speed else 0f
        val sanitizedBearing = if (rawPoint.bearing.isFinite()) rawPoint.bearing else 0f
        val sanitizedAltitude = if (rawPoint.altitude.isFinite()) rawPoint.altitude else 0.0
        val safePoint = if (
            normalizedTimestamp != rawPoint.timestamp ||
            sanitizedAccuracy != rawPoint.accuracy ||
            sanitizedSpeed != rawPoint.speed ||
            sanitizedBearing != rawPoint.bearing ||
            sanitizedAltitude != rawPoint.altitude
        ) {
            rawPoint.copy(
                timestamp = normalizedTimestamp,
                accuracy = sanitizedAccuracy,
                speed = sanitizedSpeed,
                bearing = sanitizedBearing,
                altitude = sanitizedAltitude
            )
        } else {
            rawPoint
        }

        val pointForGeofence: LocationPoint? = recordingMutex.withLock {
            val session = _activeSession.value
            val isActivelyRecording = session != null && session.isRecording && !session.isPaused
            val prevPoint = if (isActivelyRecording) {
                _activeTrailPoints.value.lastOrNull() ?: _currentLocation.value
            } else {
                _currentLocation.value
            }

            var additionalDistance = 0.0
            var candidateSpeed = safePoint.speed
            var isProviderSwitch = false
            var forcePostResumePersist = false

            if (isActivelyRecording && resetAnchorOnNextFix) {
                // First fix after resuming from pause: re-anchor without bridging distance moved while paused
                resetAnchorOnNextFix = false
                resetAnchorOnModeSwitch = false
                forcePostResumePersist = true
                additionalDistance = 0.0
                stationaryAnchorPoint = safePoint
                consecutiveSlowSamples = 0
                candidateSpeed = if (candidateSpeed >= 0.45f) candidateSpeed.coerceIn(0f, 55f) else 0f
                smoothedSpeedMps = candidateSpeed
            } else if (prevPoint != null) {
                val consumedModeSwitch = resetAnchorOnModeSwitch
                resetAnchorOnModeSwitch = false
                isProviderSwitch = consumedModeSwitch ||
                    (prevPoint.provider == "cellular") != (safePoint.provider == "cellular") ||
                    prevPoint.provider != safePoint.provider
                if (isProviderSwitch) {
                    // Switching providers or positioning modes must not inflate distance/speed or trigger false teleport rejection
                    additionalDistance = 0.0
                    stationaryAnchorPoint = safePoint
                    consecutiveSlowSamples = 0
                    if (candidateSpeed < 0.45f) {
                        candidateSpeed = 0f
                    }
                    smoothedSpeedMps = candidateSpeed
                } else {
                    if (safePoint.timestamp < prevPoint.timestamp) {
                        return@withLock null
                    }
                    val anchor = stationaryAnchorPoint ?: prevPoint
                    val distFromPrev = calculateDistanceMeters(prevPoint.latitude, prevPoint.longitude, safePoint.latitude, safePoint.longitude)
                    val distFromAnchor = calculateDistanceMeters(anchor.latitude, anchor.longitude, safePoint.latitude, safePoint.longitude)
                    val dtPrevSec = ((safePoint.timestamp - prevPoint.timestamp) / 1000.0).coerceAtLeast(0.1)
                    val dtAnchorSec = ((safePoint.timestamp - anchor.timestamp) / 1000.0).coerceAtLeast(0.1)
                    val impliedSpeedFromPrev = (distFromPrev / dtPrevSec).toFloat()
                    val impliedSpeedFromAnchor = (distFromAnchor / dtAnchorSec).toFloat()

                    // Discard genuine short-delta teleport/multipath spikes before updating UI, DB, or geofences,
                    // while allowing legitimate recovery after long tunnel/signal gaps (> 60s at realistic speed).
                    val isUnphysicalTeleport =
                        (impliedSpeedFromPrev > 65.0f && (distFromPrev > 150.0 || safePoint.accuracy > 40f)) ||
                            (distFromPrev > 5000.0 && dtPrevSec < 90.0)
                    if (isUnphysicalTeleport) {
                        return@withLock null
                    }

                    val isLongGapReanchor = distFromPrev > 5000.0 && dtPrevSec >= 90.0
                    val deadband = jitterThresholdMeters
                    val movedPastDeadband = deadband <= 0f || distFromAnchor >= deadband
                    val dopplerIndicatesMovement = safePoint.speed >= 0.55f // >= ~2.0 km/h GNSS Doppler

                    if (isLongGapReanchor) {
                        // Re-anchor safely after multi-kilometer tunnel/outage gap without fabricating a straight-line jump
                        additionalDistance = 0.0
                        stationaryAnchorPoint = safePoint
                        consecutiveSlowSamples = 0
                        candidateSpeed = if (safePoint.speed >= 0.45f) safePoint.speed.coerceIn(0f, 55f) else 0f
                    } else if (movedPastDeadband) {
                        // Advance anchor and credit full accumulated displacement (no lost 1.1-1.4m walking strides)
                        additionalDistance = distFromAnchor
                        stationaryAnchorPoint = safePoint
                        consecutiveSlowSamples = 0

                        // Recover displacement speed when chip Doppler is missing (Balanced/Cell) or under-reads in pocket
                        candidateSpeed = when {
                            safePoint.provider == "cellular" -> impliedSpeedFromAnchor.coerceIn(0f, 35f)
                            safePoint.speed <= 0.1f && dtAnchorSec >= 0.3 -> impliedSpeedFromAnchor.coerceIn(0f, 45f)
                            safePoint.speed < 0.6f && impliedSpeedFromAnchor >= 0.6f && safePoint.accuracy <= 25f ->
                                impliedSpeedFromAnchor.coerceIn(0f, 45f)
                            safePoint.speed < 0.45f && impliedSpeedFromAnchor < 0.45f -> 0f
                            else -> safePoint.speed
                        }
                    } else if (dopplerIndicatesMovement) {
                        // Moving slowly on a 1s tick (< 1.5m step) with clear GNSS Doppler: preserve velocity & hold anchor
                        // so the next tick accumulates >= deadband without losing distance or crashing EMA to 0.
                        additionalDistance = 0.0
                        consecutiveSlowSamples = 0
                        candidateSpeed = safePoint.speed
                    } else {
                        // Genuine sub-deadband drift while stationary or very slow start
                        if (distFromPrev > 0.05 && (dtAnchorSec >= 2.0 || safePoint.speed < 0.25f)) {
                            _filteredJitterMeters.value += distFromPrev
                            _filteredJitterCount.value += 1
                        }
                        additionalDistance = 0.0
                        consecutiveSlowSamples += 1
                        candidateSpeed = 0f
                    }
                }
            } else {
                stationaryAnchorPoint = safePoint
                consecutiveSlowSamples = 0
                if (candidateSpeed < 0.45f) {
                    candidateSpeed = 0f
                }
            }

            // Smooth EMA velocity display without instant-zero dropouts on a single 1s sub-deadband sample
            smoothedSpeedMps = when {
                isProviderSwitch || forcePostResumePersist -> candidateSpeed
                candidateSpeed <= 0f -> {
                    if (consecutiveSlowSamples >= 3 || smoothedSpeedMps < 0.35f) 0f
                    else 0.55f * smoothedSpeedMps
                }
                smoothedSpeedMps <= 0f -> candidateSpeed
                else -> (0.45f * candidateSpeed) + (0.55f * smoothedSpeedMps)
            }

            val point = safePoint.copy(speed = smoothedSpeedMps)
            _currentLocation.value = point
            _lastKnownCachedLocation.value = point

            if (isActivelyRecording && session != null) {
                val lastSaved = _activeTrailPoints.value.lastOrNull()
                val isFirstPoint = lastSaved == null
                val isTimestampOrdered = lastSaved == null || point.timestamp >= lastSaved.timestamp
                val elapsedSinceLastSavedMs = if (lastSaved != null) (point.timestamp - lastSaved.timestamp) else Long.MAX_VALUE
                val accuracyImproved = lastSaved != null && (lastSaved.accuracy - point.accuracy >= 10f)
                // Deduplicate stationary 1s bloat: persist immediately on movement/switch/first-fix/resume/accuracy jump, else 15s stationary heartbeat
                val shouldPersistTrackpoint = isTimestampOrdered && (
                    isFirstPoint ||
                        forcePostResumePersist ||
                        additionalDistance > 0.0 ||
                        isProviderSwitch ||
                        accuracyImproved ||
                        elapsedSinceLastSavedMs >= 15_000L
                    )

                if (shouldPersistTrackpoint) {
                    val db = dbHelper.writableDatabase
                    val newDistance = session.totalDistanceMeters + additionalDistance
                    val newPointCount = session.pointCount + 1
                    val currentSpeedKmh = point.speedKmh.toDouble()
                    val newMaxSpeed = if (additionalDistance > 0.0) maxOf(session.maxSpeedKmh, currentSpeedKmh) else session.maxSpeedKmh

                    val elapsedSec = session.currentDurationSeconds(point.timestamp).toDouble().coerceAtLeast(1.0)
                    val newAvgSpeed = (newDistance / elapsedSec) * 3.6

                    val cv = ContentValues().apply {
                        put("session_id", session.id)
                        put("latitude", point.latitude)
                        put("longitude", point.longitude)
                        put("accuracy", point.accuracy)
                        put("speed", point.speed)
                        put("altitude", point.altitude)
                        put("bearing", point.bearing)
                        put("provider", point.provider)
                        put("timestamp", point.timestamp)
                    }
                    val updateCv = ContentValues().apply {
                        put("distance", newDistance)
                        put("avg_speed", newAvgSpeed)
                        put("max_speed", newMaxSpeed)
                        put("point_count", newPointCount)
                    }

                    db.beginTransaction()
                    try {
                        db.insert(LocationDbHelper.TABLE_POINTS, null, cv)
                        db.update(LocationDbHelper.TABLE_SESSIONS, updateCv, "id = ?", arrayOf(session.id))
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }

                    _activeTrailPoints.value = _activeTrailPoints.value + point
                    val updatedSession = session.copy(
                        totalDistanceMeters = newDistance,
                        pointCount = newPointCount,
                        maxSpeedKmh = newMaxSpeed,
                        avgSpeedKmh = newAvgSpeed
                    )
                    _activeSession.value = updatedSession
                }
            }
            point
        }

        if (pointForGeofence != null) {
            try {
                offlineCacheRepository.recordCachedLocationFix(pointForGeofence)
            } catch (_: Exception) {
            }
            // Check geofences with dual-radius hysteresis & accuracy gating
            checkGeofences(pointForGeofence.latitude, pointForGeofence.longitude, pointForGeofence.accuracy, pointForGeofence.provider)
        }
    }

    private fun resolveExistingTripId(
        db: android.database.sqlite.SQLiteDatabase,
        tripId: String?
    ): String? {
        val cleaned = tripId?.takeIf { it.isNotBlank() } ?: return null
        return try {
            db.rawQuery("SELECT 1 FROM ${LocationDbHelper.TABLE_TRIPS} WHERE id = ? LIMIT 1", arrayOf(cleaned)).use { c ->
                if (c.moveToFirst()) cleaned else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun deleteUnusedWaypointPhotos(
        db: android.database.sqlite.SQLiteDatabase,
        candidatePaths: Collection<String>
    ) {
        val photoDir = File(context.filesDir, "waypoint_photos")
        val canonicalRoot = try {
            photoDir.canonicalPath
        } catch (_: Exception) {
            photoDir.absolutePath
        }
        for (rawPath in candidatePaths) {
            val trimmed = rawPath.trim()
            if (trimmed.isBlank()) continue
            try {
                val file = File(trimmed)
                if (!file.exists()) continue
                val canonicalFile = file.canonicalPath
                if (!canonicalFile.startsWith(canonicalRoot)) continue
                val stillReferencedInWaypoints = db.rawQuery(
                    "SELECT 1 FROM ${LocationDbHelper.TABLE_WAYPOINTS} WHERE photo_uri = ? LIMIT 1",
                    arrayOf(trimmed)
                ).use { it.moveToFirst() }
                if (stillReferencedInWaypoints) continue
                val stillReferencedInSaved = db.rawQuery(
                    "SELECT 1 FROM ${LocationDbHelper.TABLE_SAVED_PLACES} WHERE photo_uri = ? LIMIT 1",
                    arrayOf(trimmed)
                ).use { it.moveToFirst() }
                if (!stillReferencedInSaved) {
                    file.delete()
                }
            } catch (_: Exception) {
            }
        }
    }

    suspend fun deleteAppPhotoFileIfUnused(path: String) = withContext(Dispatchers.IO) {
        if (path.isBlank()) return@withContext
        deleteUnusedWaypointPhotos(dbHelper.readableDatabase, listOf(path))
    }

    suspend fun copyPhotoToAppStorage(uriString: String): String? = withContext(Dispatchers.IO) {
        if (uriString.isBlank()) return@withContext null
        return@withContext try {
            val uri = Uri.parse(uriString)
            val exifOrientation = try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    ExifInterface(stream).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                } ?: ExifInterface.ORIENTATION_NORMAL
            } catch (_: Exception) {
                ExifInterface.ORIENTATION_NORMAL
            }

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val rawW = boundsOpts.outWidth
            val rawH = boundsOpts.outHeight
            if (rawW <= 0 || rawH <= 0) return@withContext null

            val targetMaxEdge = 1280
            var sampleSize = 1
            while ((rawW / sampleSize) > targetMaxEdge * 2 || (rawH / sampleSize) > targetMaxEdge * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@withContext null

            val orientedBitmap = applyExifOrientation(decoded, exifOrientation)

            val maxDim = max(orientedBitmap.width, orientedBitmap.height)
            val finalBitmap = if (maxDim > targetMaxEdge) {
                val scale = targetMaxEdge.toFloat() / maxDim.toFloat()
                val newW = (orientedBitmap.width * scale).roundToInt().coerceAtLeast(1)
                val newH = (orientedBitmap.height * scale).roundToInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(orientedBitmap, newW, newH, true).also {
                    if (it !== orientedBitmap) orientedBitmap.recycle()
                }
            } else {
                orientedBitmap
            }

            val dir = File(context.filesDir, "waypoint_photos").apply {
                if (!exists()) mkdirs()
            }
            val outFile = File(dir, "${UUID.randomUUID()}.jpg")
            FileOutputStream(outFile).use { fos ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 82, fos)
            }
            finalBitmap.recycle()
            outFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        } catch (_: Exception) {
            bitmap
        }
    }

    suspend fun dropWaypoint(name: String, note: String = "", photoUri: String = ""): TrailWaypoint? = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val session = _activeSession.value ?: return@withLock null
            val loc = _currentLocation.value ?: return@withLock null
            val wp = TrailWaypoint(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                name = name.trim().ifBlank { "Waypoint #${_activeWaypoints.value.size + 1}" },
                note = note.trim(),
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitude = loc.altitude,
                accuracy = loc.accuracy,
                timestamp = System.currentTimeMillis(),
                photoUri = photoUri.trim()
            )
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("id", wp.id)
                put("session_id", wp.sessionId)
                put("name", wp.name)
                put("note", wp.note)
                put("latitude", wp.latitude)
                put("longitude", wp.longitude)
                put("altitude", wp.altitude)
                put("accuracy", wp.accuracy)
                put("timestamp", wp.timestamp)
                put("photo_uri", wp.photoUri)
            }
            db.insert(LocationDbHelper.TABLE_WAYPOINTS, null, cv)

            val newList = _activeWaypoints.value + wp
            _activeWaypoints.value = newList
            val updatedSession = session.copy(waypointCount = newList.size)
            _activeSession.value = updatedSession
            db.update(
                LocationDbHelper.TABLE_SESSIONS,
                ContentValues().apply { put("waypoint_count", newList.size) },
                "id = ?",
                arrayOf(session.id)
            )
            loadSessionsInternal()
            wp
        }
    }

    suspend fun deleteWaypoint(waypointId: String, sessionId: String): List<TrailWaypoint> = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val db = dbHelper.writableDatabase
            val photosToCleanup = mutableListOf<String>()
            db.query(
                LocationDbHelper.TABLE_WAYPOINTS,
                arrayOf("photo_uri"),
                "id = ?",
                arrayOf(waypointId),
                null, null, null
            ).use { c ->
                while (c.moveToNext()) {
                    val p = c.getString(0) ?: ""
                    if (p.isNotBlank()) photosToCleanup.add(p)
                }
            }
            db.delete(LocationDbHelper.TABLE_WAYPOINTS, "id = ?", arrayOf(waypointId))
            deleteUnusedWaypointPhotos(db, photosToCleanup)
            val remaining = queryWaypointsSync(db, sessionId)
            db.update(
                LocationDbHelper.TABLE_SESSIONS,
                ContentValues().apply { put("waypoint_count", remaining.size) },
                "id = ?",
                arrayOf(sessionId)
            )
            val current = _activeSession.value
            if (current != null && current.id == sessionId) {
                _activeWaypoints.value = remaining
                _activeSession.value = current.copy(waypointCount = remaining.size)
            }
            loadSessionsInternal()
            remaining
        }
    }

    suspend fun renameSession(sessionId: String, newName: String) = withContext(Dispatchers.IO) {
        val cleaned = newName.trim()
        if (cleaned.isBlank()) return@withContext
        recordingMutex.withLock {
            val db = dbHelper.writableDatabase
            db.update(
                LocationDbHelper.TABLE_SESSIONS,
                ContentValues().apply { put("name", cleaned) },
                "id = ?",
                arrayOf(sessionId)
            )
            val current = _activeSession.value
            if (current != null && current.id == sessionId) {
                _activeSession.value = current.copy(name = cleaned)
            }
            loadSessionsInternal()
        }
    }

    fun updateCellTowers(towers: List<CellTowerInfo>) {
        _cellTowers.value = towers
        if (towers.isNotEmpty()) {
            val nowMs = System.currentTimeMillis()
            _cachedObservedCells.value = towers
            _cachedObservedAtMs.value = nowMs
            scope.launch {
                try {
                    offlineCacheRepository.saveObservedCellSnapshot(towers, nowMs)
                } catch (_: Exception) {
                }
            }
        }
    }

    suspend fun clearOfflineGpsAndCellCache() = withContext(Dispatchers.IO) {
        _lastKnownCachedLocation.value = _currentLocation.value
        _cachedObservedCells.value = _cellTowers.value
        _cachedObservedAtMs.value = if (_cellTowers.value.isNotEmpty()) System.currentTimeMillis() else null
        offlineCacheRepository.clearAllOfflineCaches()
        com.geotrace.service.OpenCellIdClient.clearCache()
    }

    suspend fun loadSessions() = withContext(Dispatchers.IO) {
        loadSessionsInternal()
    }

    private fun loadSessionsInternal() {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<TrackingSession>()
        val cursor: Cursor = db.query(
            LocationDbHelper.TABLE_SESSIONS,
            null, null, null, null, null,
            "start_time DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(readSessionFromCursor(c))
            }
        }
        _allSessions.value = list
    }

    private fun readSessionFromCursor(c: Cursor): TrackingSession {
        val modeStr = c.getString(c.getColumnIndexOrThrow("tracking_mode"))
        val mode = try {
            TrackingMode.valueOf(modeStr)
        } catch (_: Exception) {
            TrackingMode.HIGH_ACCURACY
        }
        val wpIdx = c.getColumnIndex("waypoint_count")
        val pausedIdx = c.getColumnIndex("is_paused")
        val pausedAccIdx = c.getColumnIndex("paused_accumulated_ms")
        val pauseStartIdx = c.getColumnIndex("pause_started_ms")
        val tripIdx = c.getColumnIndex("trip_id")
        return TrackingSession(
            id = c.getString(c.getColumnIndexOrThrow("id")),
            name = c.getString(c.getColumnIndexOrThrow("name")),
            startTime = c.getLong(c.getColumnIndexOrThrow("start_time")),
            endTime = if (c.isNull(c.getColumnIndexOrThrow("end_time"))) null else c.getLong(c.getColumnIndexOrThrow("end_time")),
            totalDistanceMeters = c.getDouble(c.getColumnIndexOrThrow("distance")),
            avgSpeedKmh = c.getDouble(c.getColumnIndexOrThrow("avg_speed")),
            maxSpeedKmh = c.getDouble(c.getColumnIndexOrThrow("max_speed")),
            pointCount = c.getInt(c.getColumnIndexOrThrow("point_count")),
            waypointCount = if (wpIdx >= 0) c.getInt(wpIdx) else 0,
            trackingMode = mode,
            isRecording = c.getInt(c.getColumnIndexOrThrow("is_recording")) == 1,
            isPaused = pausedIdx >= 0 && c.getInt(pausedIdx) == 1,
            pausedAccumulatedMs = if (pausedAccIdx >= 0) c.getLong(pausedAccIdx) else 0L,
            pauseStartedMs = if (pauseStartIdx >= 0 && !c.isNull(pauseStartIdx)) c.getLong(pauseStartIdx) else null,
            tripId = if (tripIdx >= 0 && !c.isNull(tripIdx)) c.getString(tripIdx) else null
        )
    }

    suspend fun getPointsForSession(sessionId: String): List<LocationPoint> = withContext(Dispatchers.IO) {
        queryPointsSync(dbHelper.readableDatabase, sessionId)
    }

    suspend fun getWaypointsForSession(sessionId: String): List<TrailWaypoint> = withContext(Dispatchers.IO) {
        queryWaypointsSync(dbHelper.readableDatabase, sessionId)
    }

    private fun queryPointsSync(db: android.database.sqlite.SQLiteDatabase, sessionId: String): List<LocationPoint> {
        val list = mutableListOf<LocationPoint>()
        val cursor = db.query(
            LocationDbHelper.TABLE_POINTS,
            null,
            "session_id = ?",
            arrayOf(sessionId),
            null, null,
            "timestamp ASC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    LocationPoint(
                        id = c.getLong(c.getColumnIndexOrThrow("id")),
                        sessionId = c.getString(c.getColumnIndexOrThrow("session_id")),
                        latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
                        longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
                        accuracy = c.getFloat(c.getColumnIndexOrThrow("accuracy")),
                        speed = c.getFloat(c.getColumnIndexOrThrow("speed")),
                        altitude = c.getDouble(c.getColumnIndexOrThrow("altitude")),
                        bearing = c.getFloat(c.getColumnIndexOrThrow("bearing")),
                        provider = c.getString(c.getColumnIndexOrThrow("provider")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        return list
    }

    private fun queryWaypointsSync(db: android.database.sqlite.SQLiteDatabase, sessionId: String): List<TrailWaypoint> {
        val list = mutableListOf<TrailWaypoint>()
        val cursor = db.query(
            LocationDbHelper.TABLE_WAYPOINTS,
            null,
            "session_id = ?",
            arrayOf(sessionId),
            null, null,
            "timestamp ASC"
        )
        cursor.use { c ->
            val photoCol = c.getColumnIndex("photo_uri")
            while (c.moveToNext()) {
                list.add(
                    TrailWaypoint(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        sessionId = c.getString(c.getColumnIndexOrThrow("session_id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        note = c.getString(c.getColumnIndexOrThrow("note")),
                        latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
                        longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
                        altitude = c.getDouble(c.getColumnIndexOrThrow("altitude")),
                        accuracy = c.getFloat(c.getColumnIndexOrThrow("accuracy")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                        photoUri = if (photoCol >= 0 && !c.isNull(photoCol)) c.getString(photoCol) else ""
                    )
                )
            }
        }
        return list
    }

    suspend fun deleteSession(sessionId: String): Boolean = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val db = dbHelper.writableDatabase
            val photosToCleanup = mutableListOf<String>()
            db.query(
                LocationDbHelper.TABLE_WAYPOINTS,
                arrayOf("photo_uri"),
                "session_id = ?",
                arrayOf(sessionId),
                null, null, null
            ).use { c ->
                while (c.moveToNext()) {
                    val p = c.getString(0) ?: ""
                    if (p.isNotBlank()) photosToCleanup.add(p)
                }
            }
            db.beginTransaction()
            try {
                db.delete(LocationDbHelper.TABLE_WAYPOINTS, "session_id = ?", arrayOf(sessionId))
                db.delete(LocationDbHelper.TABLE_POINTS, "session_id = ?", arrayOf(sessionId))
                db.delete(LocationDbHelper.TABLE_SESSIONS, "id = ?", arrayOf(sessionId))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            deleteUnusedWaypointPhotos(db, photosToCleanup)
            val wasActive = _activeSession.value?.id == sessionId
            if (wasActive) {
                _activeSession.value = null
                _activeTrailPoints.value = emptyList()
                _activeWaypoints.value = emptyList()
                smoothedSpeedMps = 0f
                stationaryAnchorPoint = null
                consecutiveSlowSamples = 0
                resetAnchorOnNextFix = false
            }
            loadSessionsInternal()
            wasActive
        }
    }

    suspend fun loadGeofences() = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<GeofenceZone>()
        val cursor = db.query(LocationDbHelper.TABLE_GEOFENCES, null, null, null, null, null, "name ASC")
        cursor.use { c ->
            while (c.moveToNext()) {
                val catStr = c.getString(c.getColumnIndexOrThrow("category"))
                val category = try {
                    GeofenceCategory.valueOf(catStr)
                } catch (e: Exception) {
                    GeofenceCategory.SAFE_ZONE
                }
                val isEnabledCol = c.getColumnIndex("is_enabled")
                val isEnabled = if (isEnabledCol >= 0) c.getInt(isEnabledCol) == 1 else false
                val rawIsInside = c.getInt(c.getColumnIndexOrThrow("is_inside")) == 1
                list.add(
                    GeofenceZone(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
                        longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
                        radiusMeters = c.getFloat(c.getColumnIndexOrThrow("radius")),
                        category = category,
                        colorHex = c.getString(c.getColumnIndexOrThrow("color_hex")),
                        alertOnEntry = c.getInt(c.getColumnIndexOrThrow("alert_on_entry")) == 1,
                        alertOnExit = c.getInt(c.getColumnIndexOrThrow("alert_on_exit")) == 1,
                        isInside = if (isEnabled) rawIsInside else false,
                        lastTransitionTime = if (c.isNull(c.getColumnIndexOrThrow("last_transition"))) null else c.getLong(c.getColumnIndexOrThrow("last_transition")),
                        isEnabled = isEnabled
                    )
                )
            }
        }
        if (list.isEmpty()) {
            val defaultZones = buildDefaultSampleZones()
            val writeDb = dbHelper.writableDatabase
            for (z in defaultZones) {
                val cv = ContentValues().apply {
                    put("id", z.id)
                    put("name", z.name)
                    put("latitude", z.latitude)
                    put("longitude", z.longitude)
                    put("radius", z.radiusMeters)
                    put("category", z.category.name)
                    put("color_hex", z.colorHex)
                    put("alert_on_entry", if (z.alertOnEntry) 1 else 0)
                    put("alert_on_exit", if (z.alertOnExit) 1 else 0)
                    put("is_inside", 0)
                    put("is_enabled", if (z.isEnabled) 1 else 0)
                }
                writeDb.insertWithOnConflict(LocationDbHelper.TABLE_GEOFENCES, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
            _geofenceZones.value = defaultZones
        } else {
            val migratedList = migrateLegacySampleCoordinatesIfUntouched(list)
            _geofenceZones.value = migratedList
        }
    }

    private fun migrateLegacySampleCoordinatesIfUntouched(loaded: List<GeofenceZone>): List<GeofenceZone> {
        val legacyCoordsById = mapOf(
            "must_kihumuro_campus" to (-0.5982 to 30.6178),
            "must_kihumuro_library" to (-0.5976 to 30.6185),
            "must_kihumuro_hostels" to (-0.5995 to 30.6155),
            "must_main_town" to (-0.6163 to 30.6568),
            "must_town_library" to (-0.6158 to 30.6575),
            "wandegeya_boda" to (0.3308 to 32.5705)
        )
        val updatedDefaults = buildDefaultSampleZones().associateBy { it.id }
        var writeDb: android.database.sqlite.SQLiteDatabase? = null

        return loaded.map { zone ->
            val legacy = legacyCoordsById[zone.id]
            val target = updatedDefaults[zone.id]
            val isUntouchedLegacy = legacy != null &&
                target != null &&
                kotlin.math.abs(zone.latitude - legacy.first) < 0.0001 &&
                kotlin.math.abs(zone.longitude - legacy.second) < 0.0001

            if (isUntouchedLegacy && target != null) {
                val db = writeDb ?: dbHelper.writableDatabase.also { writeDb = it }
                val cv = ContentValues().apply {
                    put("latitude", target.latitude)
                    put("longitude", target.longitude)
                }
                db.update(
                    LocationDbHelper.TABLE_GEOFENCES,
                    cv,
                    "id = ?",
                    arrayOf(zone.id)
                )
                zone.copy(latitude = target.latitude, longitude = target.longitude)
            } else {
                zone
            }
        }
    }

    private fun buildDefaultSampleZones(): List<GeofenceZone> = listOf(
        GeofenceZone(
            id = "must_kihumuro_campus",
            name = "MUST Kihumuro Campus (FAST)",
            latitude = -0.5947,
            longitude = 30.5998,
            radiusMeters = 350f,
            category = GeofenceCategory.CAMPUS,
            colorHex = "#006948",
            alertOnEntry = true,
            alertOnExit = true
        ),
        GeofenceZone(
            id = "must_kihumuro_library",
            name = "MUST Kihumuro Main Library",
            latitude = -0.5945,
            longitude = 30.6002,
            radiusMeters = 100f,
            category = GeofenceCategory.LIBRARY,
            colorHex = "#0051D5",
            alertOnEntry = true,
            alertOnExit = true
        ),
        GeofenceZone(
            id = "must_kihumuro_hostels",
            name = "MUST Kihumuro Hostels Zone",
            latitude = -0.5955,
            longitude = 30.5980,
            radiusMeters = 250f,
            category = GeofenceCategory.HOSTEL,
            colorHex = "#7C3AED",
            alertOnEntry = true,
            alertOnExit = true
        ),
        GeofenceZone(
            id = "must_main_town",
            name = "MUST Main Town Campus",
            latitude = -0.6155,
            longitude = 30.6564,
            radiusMeters = 300f,
            category = GeofenceCategory.CAMPUS,
            colorHex = "#006948",
            alertOnEntry = true,
            alertOnExit = true
        ),
        GeofenceZone(
            id = "must_town_library",
            name = "MUST Town Campus Library",
            latitude = -0.6152,
            longitude = 30.6572,
            radiusMeters = 90f,
            category = GeofenceCategory.LIBRARY,
            colorHex = "#0051D5",
            alertOnEntry = true,
            alertOnExit = true
        ),
        GeofenceZone(
            id = "must_kakoba_hostels",
            name = "MUST Kakoba Student Hostels",
            latitude = -0.6075,
            longitude = 30.6727,
            radiusMeters = 300f,
            category = GeofenceCategory.HOSTEL,
            colorHex = "#7C3AED",
            alertOnEntry = true,
            alertOnExit = true
        ),
        GeofenceZone(
            id = "wandegeya_boda",
            name = "Wandegeya Boda Boda Stage",
            latitude = 0.3311,
            longitude = 32.5736,
            radiusMeters = 160f,
            category = GeofenceCategory.BODA_HOTSPOT,
            colorHex = "#D97706",
            alertOnEntry = true,
            alertOnExit = true
        )
    )

    suspend fun resetSampleGeofences() = withContext(Dispatchers.IO) {
        geofenceMutex.withLock {
            val defaults = buildDefaultSampleZones()
            val writeDb = dbHelper.writableDatabase
            for (z in defaults) {
                val cv = ContentValues().apply {
                    put("id", z.id)
                    put("name", z.name)
                    put("latitude", z.latitude)
                    put("longitude", z.longitude)
                    put("radius", z.radiusMeters)
                    put("category", z.category.name)
                    put("color_hex", z.colorHex)
                    put("alert_on_entry", if (z.alertOnEntry) 1 else 0)
                    put("alert_on_exit", if (z.alertOnExit) 1 else 0)
                    put("is_inside", 0)
                    put("is_enabled", if (z.isEnabled) 1 else 0)
                }
                writeDb.insertWithOnConflict(LocationDbHelper.TABLE_GEOFENCES, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            }
            loadGeofences()
        }
    }

    suspend fun addGeofence(zone: GeofenceZone) = withContext(Dispatchers.IO) {
        geofenceMutex.withLock {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("id", zone.id)
                put("name", zone.name)
                put("latitude", zone.latitude)
                put("longitude", zone.longitude)
                put("radius", zone.radiusMeters)
                put("category", zone.category.name)
                put("color_hex", zone.colorHex)
                put("alert_on_entry", if (zone.alertOnEntry) 1 else 0)
                put("alert_on_exit", if (zone.alertOnExit) 1 else 0)
                put("is_inside", if (zone.isEnabled && zone.isInside) 1 else 0)
                put("last_transition", zone.lastTransitionTime)
                put("is_enabled", if (zone.isEnabled) 1 else 0)
            }
            db.insertWithOnConflict(LocationDbHelper.TABLE_GEOFENCES, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            loadGeofences()
        }
    }

    suspend fun updateGeofence(zone: GeofenceZone) = withContext(Dispatchers.IO) {
        geofenceMutex.withLock {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("name", zone.name)
                put("latitude", zone.latitude)
                put("longitude", zone.longitude)
                put("radius", zone.radiusMeters)
                put("category", zone.category.name)
                put("color_hex", zone.colorHex)
                put("alert_on_entry", if (zone.alertOnEntry) 1 else 0)
                put("alert_on_exit", if (zone.alertOnExit) 1 else 0)
                put("is_enabled", if (zone.isEnabled) 1 else 0)
                if (!zone.isEnabled) {
                    put("is_inside", 0)
                }
            }
            db.update(LocationDbHelper.TABLE_GEOFENCES, cv, "id = ?", arrayOf(zone.id))
            loadGeofences()
        }
    }

    suspend fun setGeofenceEnabled(id: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        geofenceMutex.withLock {
            val db = dbHelper.writableDatabase
            val cv = ContentValues().apply {
                put("is_enabled", if (enabled) 1 else 0)
                if (!enabled) {
                    put("is_inside", 0)
                }
            }
            db.update(LocationDbHelper.TABLE_GEOFENCES, cv, "id = ?", arrayOf(id))
            loadGeofences()
        }
    }

    suspend fun deleteGeofence(id: String) = withContext(Dispatchers.IO) {
        geofenceMutex.withLock {
            val db = dbHelper.writableDatabase
            db.delete(LocationDbHelper.TABLE_GEOFENCES, "id = ?", arrayOf(id))
            loadGeofences()
        }
    }

    suspend fun clearGeofenceAlerts() = withContext(Dispatchers.IO) {
        geofenceMutex.withLock {
            dbHelper.writableDatabase.delete(LocationDbHelper.TABLE_ALERTS, null, null)
            _recentAlerts.value = emptyList()
        }
    }

    suspend fun handleApiGeofenceTransition(zoneId: String, isNowInside: Boolean) = withContext(Dispatchers.IO) {
        if (!geofenceNotificationsEnabled) return@withContext
        geofenceMutex.withLock {
            if (_geofenceZones.value.isEmpty()) {
                loadGeofences()
            }
            val zone = _geofenceZones.value.firstOrNull { it.id == zoneId || it.name == zoneId } ?: return@withLock
            if (!zone.isEnabled) return@withLock
            val now = System.currentTimeMillis()
            if (shouldSuppressDuplicateTransition(zone.id, isNowInside, now)) return@withLock

            val db = dbHelper.writableDatabase
            val eventType = if (isNowInside) "ENTERED" else "EXITED"
            val shouldAlert = (isNowInside && zone.alertOnEntry) || (!isNowInside && zone.alertOnExit)

            val updatedZones = _geofenceZones.value.map { current ->
                if (current.id == zone.id) {
                    current.copy(isInside = isNowInside, lastTransitionTime = now)
                } else {
                    current
                }
            }
            val zoneCv = ContentValues().apply {
                put("is_inside", if (isNowInside) 1 else 0)
                put("last_transition", now)
            }
            db.update(LocationDbHelper.TABLE_GEOFENCES, zoneCv, "id = ?", arrayOf(zone.id))
            _geofenceZones.value = updatedZones

            if (shouldAlert) {
                val alert = GeofenceAlert(
                    id = UUID.randomUUID().toString(),
                    zoneName = zone.name,
                    eventType = eventType,
                    timestamp = now
                )
                val alertCv = ContentValues().apply {
                    put("id", alert.id)
                    put("zone_name", alert.zoneName)
                    put("event_type", alert.eventType)
                    put("timestamp", alert.timestamp)
                }
                db.insert(LocationDbHelper.TABLE_ALERTS, null, alertCv)
                pruneOldAlerts(db)
                loadAlerts()

                val verb = if (isNowInside) "Entered" else "Exited"
                val notifTitle = "${zone.category.iconEmoji} ${zone.category.label}: $verb"
                val notifContent = "You have $verb ${zone.name}"
                com.geotrace.service.GeofenceBroadcastReceiver.sendNotification(
                    context = context,
                    title = notifTitle,
                    content = notifContent,
                    notificationId = zone.id.hashCode() + (if (isNowInside) 1 else 2)
                )
            }
        }
    }

    private fun shouldSuppressDuplicateTransition(zoneId: String, isNowInside: Boolean, nowMs: Long): Boolean {
        synchronized(transitionCooldownLock) {
            val prev = lastNotifiedTransitionMs[zoneId]
            if (prev != null && prev.first == isNowInside && (nowMs - prev.second) < 15_000L) {
                return true
            }
            lastNotifiedTransitionMs[zoneId] = Pair(isNowInside, nowMs)
            return false
        }
    }

    private suspend fun checkGeofences(
        lat: Double,
        lon: Double,
        accuracyMeters: Float = 10f,
        provider: String = "fused"
    ) = withContext(Dispatchers.IO) {
        geofenceMutex.withLock {
            val zones = _geofenceZones.value
            val updatedZones = mutableListOf<GeofenceZone>()
            val newAlerts = mutableListOf<GeofenceAlert>()
            var hasTransitions = false
            val db = dbHelper.writableDatabase
            val now = System.currentTimeMillis()

            // Dual-radius Schmitt-trigger hysteresis: smaller radius for entry, larger radius for exit
            val delta = geofenceHysteresisMeters.toDouble().coerceAtLeast(0.0)
            val useAccuracyGate = geofenceAccuracyGateEnabled

            for (z in zones) {
                if (!z.isEnabled) {
                    if (z.isInside) {
                        db.update(
                            LocationDbHelper.TABLE_GEOFENCES,
                            ContentValues().apply { put("is_inside", 0) },
                            "id = ?",
                            arrayOf(z.id)
                        )
                        updatedZones.add(z.copy(isInside = false))
                    } else {
                        updatedZones.add(z)
                    }
                    continue
                }
                if (useAccuracyGate) {
                    val maxAllowedAcc = maxOf(35.0, z.radiusMeters * 0.75)
                    if (provider == "cellular" || accuracyMeters > maxAllowedAcc) {
                        updatedZones.add(z)
                        continue
                    }
                }

                val dist = calculateDistanceMeters(lat, lon, z.latitude, z.longitude)
                val entryRadius = (z.radiusMeters - delta).coerceAtLeast(minOf(10.0, z.radiusMeters * 0.5))
                val exitRadius = z.radiusMeters + delta

                val isNowInside = if (z.isInside) {
                    dist <= exitRadius
                } else {
                    dist <= entryRadius
                }

                if (isNowInside != z.isInside) {
                    hasTransitions = true
                    val eventType = if (isNowInside) "ENTERED" else "EXITED"
                    val shouldAlert = (isNowInside && z.alertOnEntry) || (!isNowInside && z.alertOnExit)
                    val notDuplicate = !shouldSuppressDuplicateTransition(z.id, isNowInside, now)

                    if (shouldAlert && notDuplicate) {
                        val alert = GeofenceAlert(
                            id = UUID.randomUUID().toString(),
                            zoneName = z.name,
                            eventType = eventType,
                            timestamp = now
                        )
                        newAlerts.add(alert)
                        val alertCv = ContentValues().apply {
                            put("id", alert.id)
                            put("zone_name", alert.zoneName)
                            put("event_type", alert.eventType)
                            put("timestamp", alert.timestamp)
                        }
                        db.insert(LocationDbHelper.TABLE_ALERTS, null, alertCv)

                        if (geofenceNotificationsEnabled) {
                            val verb = if (eventType == "ENTERED") "Entered" else "Exited"
                            val notifTitle = "${z.category.iconEmoji} ${z.category.label}: $verb"
                            val notifContent = "You have $verb ${z.name}"
                            com.geotrace.service.GeofenceBroadcastReceiver.sendNotification(
                                context = context,
                                title = notifTitle,
                                content = notifContent,
                                notificationId = z.id.hashCode() + (if (isNowInside) 1 else 2)
                            )
                        }
                    }

                    val updatedZone = z.copy(isInside = isNowInside, lastTransitionTime = now)
                    updatedZones.add(updatedZone)

                    val zoneCv = ContentValues().apply {
                        put("is_inside", if (isNowInside) 1 else 0)
                        put("last_transition", updatedZone.lastTransitionTime)
                    }
                    db.update(LocationDbHelper.TABLE_GEOFENCES, zoneCv, "id = ?", arrayOf(z.id))
                } else {
                    updatedZones.add(z)
                }
            }

            if (hasTransitions) {
                _geofenceZones.value = updatedZones
            }
            if (newAlerts.isNotEmpty()) {
                pruneOldAlerts(db)
                loadAlerts()
            }
        }
    }

    private fun pruneOldAlerts(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL(
            "DELETE FROM ${LocationDbHelper.TABLE_ALERTS} WHERE id NOT IN (" +
                "SELECT id FROM ${LocationDbHelper.TABLE_ALERTS} ORDER BY timestamp DESC LIMIT 100)"
        )
    }

    private suspend fun loadAlerts() = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<GeofenceAlert>()
        val cursor = db.query(LocationDbHelper.TABLE_ALERTS, null, null, null, null, null, "timestamp DESC", "20")
        cursor.use { c ->
            while (c.moveToNext()) {
                list.add(
                    GeofenceAlert(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        zoneName = c.getString(c.getColumnIndexOrThrow("zone_name")),
                        eventType = c.getString(c.getColumnIndexOrThrow("event_type")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp"))
                    )
                )
            }
        }
        _recentAlerts.value = list
    }

    suspend fun loadOfflineMapRegions() = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<OfflineMapRegion>()
        val cursor = db.query(
            LocationDbHelper.TABLE_OFFLINE_MAPS,
            null,
            null,
            null,
            null,
            null,
            "downloaded_at DESC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val tileTypeStr = c.getString(c.getColumnIndexOrThrow("tile_type"))
                val tileType = try {
                    MapTileType.valueOf(tileTypeStr)
                } catch (_: Exception) {
                    MapTileType.SATELLITE
                }
                list.add(
                    OfflineMapRegion(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        northLat = c.getDouble(c.getColumnIndexOrThrow("north_lat")),
                        southLat = c.getDouble(c.getColumnIndexOrThrow("south_lat")),
                        eastLng = c.getDouble(c.getColumnIndexOrThrow("east_lng")),
                        westLng = c.getDouble(c.getColumnIndexOrThrow("west_lng")),
                        tileType = tileType,
                        minZoom = c.getInt(c.getColumnIndexOrThrow("min_zoom")),
                        maxZoom = c.getInt(c.getColumnIndexOrThrow("max_zoom")),
                        tileCount = c.getInt(c.getColumnIndexOrThrow("tile_count")),
                        estimatedSizeMb = c.getFloat(c.getColumnIndexOrThrow("estimated_size_mb")),
                        downloadedAtMs = c.getLong(c.getColumnIndexOrThrow("downloaded_at"))
                    )
                )
            }
        }
        _offlineMapRegions.value = list
        refreshOfflinePlacesCountInternal()
    }

    private fun refreshOfflinePlacesCountInternal() {
        try {
            val db = dbHelper.readableDatabase
            db.rawQuery("SELECT COUNT(*) FROM ${LocationDbHelper.TABLE_OFFLINE_PLACES}", null).use { c ->
                if (c.moveToFirst()) {
                    _offlinePlacesCount.value = c.getInt(0)
                }
            }
            val perRegion = HashMap<String, Int>()
            db.rawQuery(
                "SELECT region_id, COUNT(*) FROM ${LocationDbHelper.TABLE_OFFLINE_PLACES} GROUP BY region_id",
                null
            ).use { c ->
                while (c.moveToNext()) {
                    perRegion[c.getString(0)] = c.getInt(1)
                }
            }
            _offlinePlacesCountByRegion.value = perRegion
        } catch (_: Exception) {
            _offlinePlacesCount.value = 0
            _offlinePlacesCountByRegion.value = emptyMap()
        }
    }

    suspend fun saveOfflinePlaces(regionId: String, places: List<OfflinePlacePoi>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            db.delete(LocationDbHelper.TABLE_OFFLINE_PLACES, "region_id = ?", arrayOf(regionId))
            for (poi in places) {
                val cv = ContentValues().apply {
                    put("id", poi.id)
                    put("region_id", poi.regionId)
                    put("name", poi.name)
                    put("category", poi.category)
                    put("subtitle", poi.subtitle)
                    put("latitude", poi.latitude)
                    put("longitude", poi.longitude)
                }
                db.insertWithOnConflict(
                    LocationDbHelper.TABLE_OFFLINE_PLACES,
                    null,
                    cv,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        refreshOfflinePlacesCountInternal()
    }

    private fun escapeSqlLike(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }

    suspend fun searchOfflinePlaces(query: String, limit: Int = 25): List<OfflinePlacePoi> = withContext(Dispatchers.IO) {
        val list = mutableListOf<OfflinePlacePoi>()
        val seenKeys = HashSet<String>()
        val trimmed = query.trim()
        val fetchCap = (limit * 3).coerceAtLeast(45)
        val db = dbHelper.readableDatabase
        val cursor = if (trimmed.isBlank()) {
            db.query(
                LocationDbHelper.TABLE_OFFLINE_PLACES,
                null,
                null,
                null,
                null,
                null,
                "name ASC",
                fetchCap.toString()
            )
        } else {
            val likeArg = "%${escapeSqlLike(trimmed)}%"
            db.query(
                LocationDbHelper.TABLE_OFFLINE_PLACES,
                null,
                "name LIKE ? ESCAPE '\\' COLLATE NOCASE OR category LIKE ? ESCAPE '\\' COLLATE NOCASE OR subtitle LIKE ? ESCAPE '\\' COLLATE NOCASE",
                arrayOf(likeArg, likeArg, likeArg),
                null,
                null,
                "name ASC",
                fetchCap.toString()
            )
        }
        cursor.use { c ->
            while (c.moveToNext() && list.size < limit) {
                val poi = OfflinePlacePoi(
                    id = c.getString(c.getColumnIndexOrThrow("id")),
                    regionId = c.getString(c.getColumnIndexOrThrow("region_id")),
                    name = c.getString(c.getColumnIndexOrThrow("name")),
                    category = c.getString(c.getColumnIndexOrThrow("category")),
                    subtitle = c.getString(c.getColumnIndexOrThrow("subtitle")),
                    latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
                    longitude = c.getDouble(c.getColumnIndexOrThrow("longitude"))
                )
                val key = "${poi.name.trim().lowercase(java.util.Locale.US)}:${
                    String.format(java.util.Locale.US, "%.4f,%.4f", poi.latitude, poi.longitude)
                }"
                if (seenKeys.add(key)) {
                    list.add(poi)
                }
            }
        }
        list
    }

    suspend fun searchSavedWaypoints(query: String, limit: Int = 15): List<TrailWaypoint> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()
        val list = mutableListOf<TrailWaypoint>()
        val db = dbHelper.readableDatabase
        val likeArg = "%${escapeSqlLike(trimmed)}%"
        val cursor = db.query(
            LocationDbHelper.TABLE_WAYPOINTS,
            null,
            "name LIKE ? ESCAPE '\\' COLLATE NOCASE OR note LIKE ? ESCAPE '\\' COLLATE NOCASE",
            arrayOf(likeArg, likeArg),
            null,
            null,
            "timestamp DESC",
            limit.toString()
        )
        cursor.use { c ->
            val photoCol = c.getColumnIndex("photo_uri")
            while (c.moveToNext()) {
                list.add(
                    TrailWaypoint(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        sessionId = c.getString(c.getColumnIndexOrThrow("session_id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        note = c.getString(c.getColumnIndexOrThrow("note")),
                        latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
                        longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
                        altitude = c.getDouble(c.getColumnIndexOrThrow("altitude")),
                        accuracy = c.getFloat(c.getColumnIndexOrThrow("accuracy")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                        photoUri = if (photoCol >= 0 && !c.isNull(photoCol)) c.getString(photoCol) else ""
                    )
                )
            }
        }
        list
    }

    // --- Standalone Saved Places & Multi-Day Trips ---

    private fun loadSavedPlacesInternal() {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<SavedPlace>()
        val cursor = db.query(
            LocationDbHelper.TABLE_SAVED_PLACES,
            null, null, null, null, null,
            "created_at DESC"
        )
        cursor.use { c ->
            val tripIdx = c.getColumnIndex("trip_id")
            val photoIdx = c.getColumnIndex("photo_uri")
            while (c.moveToNext()) {
                list.add(
                    SavedPlace(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        category = c.getString(c.getColumnIndexOrThrow("category")),
                        note = c.getString(c.getColumnIndexOrThrow("note")),
                        latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
                        longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
                        altitude = c.getDouble(c.getColumnIndexOrThrow("altitude")),
                        photoUri = if (photoIdx >= 0 && !c.isNull(photoIdx)) c.getString(photoIdx) else "",
                        tripId = if (tripIdx >= 0 && !c.isNull(tripIdx)) c.getString(tripIdx) else null,
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        _savedPlaces.value = list
    }

    suspend fun savePlace(
        name: String,
        category: String = "Spot",
        note: String = "",
        latitude: Double,
        longitude: Double,
        altitude: Double = 0.0,
        photoUri: String = "",
        tripId: String? = null
    ): SavedPlace = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val db = dbHelper.writableDatabase
            val validTripId = resolveExistingTripId(db, tripId)
            val place = SavedPlace(
                id = UUID.randomUUID().toString(),
                name = name.trim().ifBlank { "Saved Spot" },
                category = category.trim().ifBlank { "Spot" },
                note = note.trim(),
                latitude = latitude,
                longitude = longitude,
                altitude = altitude,
                photoUri = photoUri.trim(),
                tripId = validTripId,
                createdAt = System.currentTimeMillis()
            )
            val cv = ContentValues().apply {
                put("id", place.id)
                put("name", place.name)
                put("category", place.category)
                put("note", place.note)
                put("latitude", place.latitude)
                put("longitude", place.longitude)
                put("altitude", place.altitude)
                put("photo_uri", place.photoUri)
                if (place.tripId != null) put("trip_id", place.tripId) else putNull("trip_id")
                put("created_at", place.createdAt)
            }
            var savedPlace = place
            var rowId = db.insertWithOnConflict(
                LocationDbHelper.TABLE_SAVED_PLACES,
                null,
                cv,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
            )
            if (rowId == -1L && validTripId != null) {
                cv.putNull("trip_id")
                rowId = db.insertWithOnConflict(
                    LocationDbHelper.TABLE_SAVED_PLACES,
                    null,
                    cv,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
                savedPlace = place.copy(tripId = null)
            }
            if (rowId == -1L && savedPlace.photoUri.isNotBlank()) {
                deleteUnusedWaypointPhotos(db, listOf(savedPlace.photoUri))
                savedPlace = savedPlace.copy(photoUri = "")
            }
            loadSavedPlacesInternal()
            savedPlace
        }
    }

    suspend fun updateSavedPlace(
        id: String,
        name: String,
        category: String,
        note: String,
        tripId: String?
    ) = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val db = dbHelper.writableDatabase
            val validTripId = resolveExistingTripId(db, tripId)
            val cv = ContentValues().apply {
                put("name", name.trim().ifBlank { "Saved Spot" })
                put("category", category.trim().ifBlank { "Spot" })
                put("note", note.trim())
                if (validTripId != null) put("trip_id", validTripId) else putNull("trip_id")
            }
            db.update(LocationDbHelper.TABLE_SAVED_PLACES, cv, "id = ?", arrayOf(id))
            loadSavedPlacesInternal()
        }
    }

    suspend fun deleteSavedPlace(id: String) = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val db = dbHelper.writableDatabase
            val photosToCleanup = mutableListOf<String>()
            db.query(
                LocationDbHelper.TABLE_SAVED_PLACES,
                arrayOf("photo_uri"),
                "id = ?",
                arrayOf(id),
                null, null, null
            ).use { c ->
                while (c.moveToNext()) {
                    val p = c.getString(0) ?: ""
                    if (p.isNotBlank()) photosToCleanup.add(p)
                }
            }
            db.delete(LocationDbHelper.TABLE_SAVED_PLACES, "id = ?", arrayOf(id))
            deleteUnusedWaypointPhotos(db, photosToCleanup)
            loadSavedPlacesInternal()
        }
    }

    suspend fun assignPlaceToTrip(placeId: String, tripId: String?) = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val db = dbHelper.writableDatabase
            val validTripId = resolveExistingTripId(db, tripId)
            val cv = ContentValues().apply {
                if (validTripId != null) put("trip_id", validTripId) else putNull("trip_id")
            }
            db.update(LocationDbHelper.TABLE_SAVED_PLACES, cv, "id = ?", arrayOf(placeId))
            loadSavedPlacesInternal()
        }
    }

    suspend fun searchSavedPlaces(query: String, limit: Int = 20): List<SavedPlace> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val list = mutableListOf<SavedPlace>()
        val db = dbHelper.readableDatabase
        val cursor = if (trimmed.isBlank()) {
            db.query(
                LocationDbHelper.TABLE_SAVED_PLACES,
                null, null, null, null, null,
                "created_at DESC",
                limit.toString()
            )
        } else {
            val likeArg = "%${escapeSqlLike(trimmed)}%"
            db.query(
                LocationDbHelper.TABLE_SAVED_PLACES,
                null,
                "name LIKE ? ESCAPE '\\' COLLATE NOCASE OR category LIKE ? ESCAPE '\\' COLLATE NOCASE OR note LIKE ? ESCAPE '\\' COLLATE NOCASE",
                arrayOf(likeArg, likeArg, likeArg),
                null, null,
                "created_at DESC",
                limit.toString()
            )
        }
        cursor.use { c ->
            val tripIdx = c.getColumnIndex("trip_id")
            val photoIdx = c.getColumnIndex("photo_uri")
            while (c.moveToNext()) {
                list.add(
                    SavedPlace(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        category = c.getString(c.getColumnIndexOrThrow("category")),
                        note = c.getString(c.getColumnIndexOrThrow("note")),
                        latitude = c.getDouble(c.getColumnIndexOrThrow("latitude")),
                        longitude = c.getDouble(c.getColumnIndexOrThrow("longitude")),
                        altitude = c.getDouble(c.getColumnIndexOrThrow("altitude")),
                        photoUri = if (photoIdx >= 0 && !c.isNull(photoIdx)) c.getString(photoIdx) else "",
                        tripId = if (tripIdx >= 0 && !c.isNull(tripIdx)) c.getString(tripIdx) else null,
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        list
    }

    private fun loadTripsInternal() {
        val db = dbHelper.readableDatabase
        val list = mutableListOf<TripGroup>()
        val cursor = db.query(
            LocationDbHelper.TABLE_TRIPS,
            null, null, null, null, null,
            "start_date DESC"
        )
        cursor.use { c ->
            val endIdx = c.getColumnIndex("end_date")
            while (c.moveToNext()) {
                list.add(
                    TripGroup(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        name = c.getString(c.getColumnIndexOrThrow("name")),
                        notes = c.getString(c.getColumnIndexOrThrow("notes")),
                        startDate = c.getLong(c.getColumnIndexOrThrow("start_date")),
                        endDate = if (endIdx >= 0 && !c.isNull(endIdx)) c.getLong(endIdx) else null,
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        _trips.value = list
    }

    suspend fun createTrip(name: String, notes: String = ""): TripGroup = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val trip = TripGroup(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Expedition" },
            notes = notes.trim(),
            startDate = now,
            endDate = null,
            createdAt = now
        )
        val cv = ContentValues().apply {
            put("id", trip.id)
            put("name", trip.name)
            put("notes", trip.notes)
            put("start_date", trip.startDate)
            putNull("end_date")
            put("created_at", trip.createdAt)
        }
        dbHelper.writableDatabase.insertWithOnConflict(
            LocationDbHelper.TABLE_TRIPS,
            null,
            cv,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        loadTripsInternal()
        trip
    }

    suspend fun deleteTrip(tripId: String) = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val db = dbHelper.writableDatabase
            db.beginTransaction()
            try {
                val clearCv = ContentValues().apply { putNull("trip_id") }
                db.update(LocationDbHelper.TABLE_SESSIONS, clearCv, "trip_id = ?", arrayOf(tripId))
                db.update(LocationDbHelper.TABLE_SAVED_PLACES, clearCv, "trip_id = ?", arrayOf(tripId))
                db.delete(LocationDbHelper.TABLE_TRIPS, "id = ?", arrayOf(tripId))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            val current = _activeSession.value
            if (current != null && current.tripId == tripId) {
                _activeSession.value = current.copy(tripId = null)
            }
            loadTripsInternal()
            loadSessionsInternal()
            loadSavedPlacesInternal()
        }
    }

    suspend fun assignSessionToTrip(sessionId: String, tripId: String?) = withContext(Dispatchers.IO) {
        recordingMutex.withLock {
            val db = dbHelper.writableDatabase
            val validTripId = resolveExistingTripId(db, tripId)
            val cv = ContentValues().apply {
                if (validTripId != null) put("trip_id", validTripId) else putNull("trip_id")
            }
            db.update(
                LocationDbHelper.TABLE_SESSIONS,
                cv,
                "id = ?",
                arrayOf(sessionId)
            )
            val current = _activeSession.value
            if (current != null && current.id == sessionId) {
                _activeSession.value = current.copy(tripId = validTripId)
            }
            loadSessionsInternal()
        }
    }

    suspend fun saveOfflineMapRegion(region: OfflineMapRegion) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val cv = ContentValues().apply {
            put("id", region.id)
            put("name", region.name)
            put("north_lat", region.northLat)
            put("south_lat", region.southLat)
            put("east_lng", region.eastLng)
            put("west_lng", region.westLng)
            put("tile_type", region.tileType.name)
            put("min_zoom", region.minZoom)
            put("max_zoom", region.maxZoom)
            put("tile_count", region.tileCount)
            put("estimated_size_mb", region.estimatedSizeMb)
            put("downloaded_at", region.downloadedAtMs)
        }
        db.insertWithOnConflict(
            LocationDbHelper.TABLE_OFFLINE_MAPS,
            null,
            cv,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        loadOfflineMapRegions()
    }

    suspend fun deleteOfflineMapRegion(id: String): Pair<OfflineMapRegion?, List<OfflineMapRegion>> = withContext(Dispatchers.IO) {
        val current = _offlineMapRegions.value
        val deleted = current.firstOrNull { it.id == id }
        val db = dbHelper.writableDatabase
        db.delete(LocationDbHelper.TABLE_OFFLINE_PLACES, "region_id = ?", arrayOf(id))
        db.delete(LocationDbHelper.TABLE_OFFLINE_MAPS, "id = ?", arrayOf(id))
        loadOfflineMapRegions()
        deleted to _offlineMapRegions.value
    }

    suspend fun clearAllOfflineMapRegions() = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete(LocationDbHelper.TABLE_OFFLINE_PLACES, null, null)
        db.delete(LocationDbHelper.TABLE_OFFLINE_MAPS, null, null)
        loadOfflineMapRegions()
    }

    fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }
}
