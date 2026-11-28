package com.geotrace.data.cache

import com.geotrace.model.CellTowerInfo
import com.geotrace.model.LocationPoint
import com.geotrace.navigation.NavManeuverType
import com.geotrace.navigation.NavProfile
import com.geotrace.navigation.NavRoute
import com.geotrace.navigation.NavRouteSource
import com.geotrace.navigation.NavStep
import com.geotrace.service.OpenCellIdClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class OfflineCacheRepository(
    private val cellDao: CellTowerCacheDao,
    private val observedCellDao: ObservedCellDao,
    private val locationFixDao: LocationFixCacheDao,
    private val navRouteDao: NavRouteCacheDao? = null,
    private val routingPackDao: OfflineRoutingPackDao? = null
) : OpenCellIdClient.CellTowerCacheStore {

    constructor(database: OfflineCacheDatabase) : this(
        database.cellTowerCacheDao(),
        database.observedCellDao(),
        database.locationFixCacheDao(),
        database.navRouteCacheDao(),
        database.routingPackDao()
    )

    companion object {
        const val MAX_CACHED_CELL_TOWERS = 2000
        const val MAX_CACHED_LOCATION_FIXES = 100
        const val MAX_CACHED_NAV_ROUTES = 15
        private const val LOCATION_PERSIST_MIN_INTERVAL_MS = 15_000L
        private const val LOCATION_PERSIST_MIN_DISTANCE_METERS = 25.0
        private const val OBSERVED_CELLS_MIN_INTERVAL_MS = 20_000L
        private const val LOCATION_PRUNE_BATCH = 10
        private const val TOWER_PRUNE_BATCH = 25
    }

    private val cacheMutex = Mutex()

    private var lastPersistedFix: LocationPoint? = null
    private var lastObservedSnapshotSignature: String? = null
    private var lastObservedSnapshotMs: Long = 0L
    private var locationInsertsSincePrune: Int = 0
    private var towerUpsertsSincePrune: Int = 0

    val latestCachedLocationFlow: Flow<LocationPoint?> = locationFixDao.observeLatestFix().map { entity ->
        entity?.toDomain()
    }

    val recentCachedFixesFlow: Flow<List<LocationPoint>> = locationFixDao.observeRecentFixes(MAX_CACHED_LOCATION_FIXES).map { list ->
        list.map { it.toDomain() }
    }

    val cachedLocationFixCountFlow: Flow<Int> = locationFixDao.observeFixCount()

    val cachedCellTowerCountFlow: Flow<Int> = cellDao.observeTowerCount()

    val latestObservedCellsFlow: Flow<List<CellTowerInfo>> = observedCellDao.observeLatestCells().map { list ->
        list.map { it.toDomain() }
    }

    val latestObservedAtMsFlow: Flow<Long?> = observedCellDao.observeLatestObservedAtMs()

    override suspend fun getResolvedTower(
        mcc: Int,
        mnc: Int,
        lac: Int,
        cid: Long
    ): OpenCellIdClient.PersistentCachedTower? {
        val row = cellDao.findTower(mcc, mnc, lac, cid) ?: return null
        return OpenCellIdClient.PersistentCachedTower(
            mcc = row.mcc,
            mnc = row.mnc,
            lac = row.lac,
            cid = row.cid,
            latitude = row.latitude,
            longitude = row.longitude,
            apiRangeMeters = row.apiRangeMeters,
            operatorName = row.operatorName,
            radioType = row.radioType,
            addressEstimate = row.addressEstimate,
            resolvedAtMs = row.resolvedAtMs,
            lastSeenAtMs = row.lastSeenAtMs
        )
    }

    override suspend fun putResolvedTower(entry: OpenCellIdClient.PersistentCachedTower) {
        val shouldPrune = cacheMutex.withLock {
            cellDao.upsertTower(
                CachedCellTowerEntity(
                    mcc = entry.mcc,
                    mnc = entry.mnc,
                    lac = entry.lac,
                    cid = entry.cid,
                    latitude = entry.latitude,
                    longitude = entry.longitude,
                    apiRangeMeters = entry.apiRangeMeters,
                    operatorName = entry.operatorName,
                    radioType = entry.radioType,
                    addressEstimate = entry.addressEstimate,
                    resolvedAtMs = entry.resolvedAtMs,
                    lastSeenAtMs = entry.lastSeenAtMs
                )
            )
            towerUpsertsSincePrune++
            towerUpsertsSincePrune >= TOWER_PRUNE_BATCH
        }
        if (shouldPrune) {
            cacheMutex.withLock {
                if (cellDao.getTowerCount() > MAX_CACHED_CELL_TOWERS) {
                    cellDao.pruneOldestTowers(MAX_CACHED_CELL_TOWERS)
                }
                towerUpsertsSincePrune = 0
            }
        }
    }

    override suspend fun touchResolvedTower(
        mcc: Int,
        mnc: Int,
        lac: Int,
        cid: Long,
        seenAtMs: Long
    ) {
        cellDao.touchTower(mcc, mnc, lac, cid, seenAtMs)
    }

    override suspend fun clearResolvedTowers() {
        cacheMutex.withLock {
            towerUpsertsSincePrune = 0
            cellDao.clearAll()
        }
    }

    suspend fun getLatestCachedLocationSnapshot(): LocationPoint? = cacheMutex.withLock {
        locationFixDao.getLatestFix()?.toDomain()?.also { snap ->
            val current = lastPersistedFix
            if (current == null || snap.timestamp > current.timestamp) {
                lastPersistedFix = snap
            }
        }
    }

    suspend fun getLatestObservedCellsSnapshot(): List<CellTowerInfo> {
        return observedCellDao.getLatestCells().map { it.toDomain() }
    }

    suspend fun getLatestObservedAtMsSnapshot(): Long? {
        return observedCellDao.getLatestObservedAtMs()
    }

    suspend fun recordCachedLocationFix(point: LocationPoint, force: Boolean = false) = cacheMutex.withLock {
        if (!point.latitude.isFinite() || !point.longitude.isFinite() ||
            point.latitude !in -90.0..90.0 || point.longitude !in -180.0..180.0 ||
            (point.latitude == 0.0 && point.longitude == 0.0) ||
            point.provider == "last_gasp"
        ) {
            return@withLock
        }

        val prev = lastPersistedFix
        if (!force && prev != null && prev.provider != "cellular" && point.provider == "cellular") {
            val dtFromGnssMs = (point.timestamp - prev.timestamp).coerceAtLeast(0L)
            val muchCoarser = prev.accuracy > 0f && point.accuracy > prev.accuracy * 3f
            if (dtFromGnssMs < 10 * 60_000L || (muchCoarser && dtFromGnssMs < 30 * 60_000L)) {
                return@withLock
            }
        }
        val shouldPersist = if (force || prev == null) {
            true
        } else {
            val dtMs = (point.timestamp - prev.timestamp).coerceAtLeast(0L)
            val distM = haversineMeters(prev.latitude, prev.longitude, point.latitude, point.longitude)
            val upgradedFromCellToGnss = prev.provider == "cellular" && point.provider != "cellular"
            val accuracyMuchBetter = (prev.accuracy - point.accuracy) >= 15f && point.accuracy > 0f
            dtMs >= LOCATION_PERSIST_MIN_INTERVAL_MS ||
                distM >= LOCATION_PERSIST_MIN_DISTANCE_METERS ||
                upgradedFromCellToGnss ||
                accuracyMuchBetter
        }

        if (!shouldPersist) return@withLock

        locationFixDao.insertFix(
            CachedLocationFixEntity(
                latitude = point.latitude,
                longitude = point.longitude,
                accuracy = point.accuracy,
                speed = point.speed,
                altitude = point.altitude,
                bearing = point.bearing,
                provider = point.provider,
                timestamp = point.timestamp,
                sessionId = point.sessionId
            )
        )
        lastPersistedFix = point
        locationInsertsSincePrune++
        if (locationInsertsSincePrune >= LOCATION_PRUNE_BATCH) {
            if (locationFixDao.getFixCount() > MAX_CACHED_LOCATION_FIXES) {
                locationFixDao.pruneOldestFixes(MAX_CACHED_LOCATION_FIXES)
            }
            locationInsertsSincePrune = 0
        }
    }

    suspend fun saveObservedCellSnapshot(
        towers: List<CellTowerInfo>,
        observedAtMs: Long = System.currentTimeMillis()
    ) = cacheMutex.withLock {
        if (towers.isEmpty()) return@withLock
        val signature = towers.joinToString("|") { cell ->
            "${cell.type}:${cell.mcc}:${cell.mnc}:${cell.lac}:${cell.cid}:${cell.pci}:${cell.earfcn}:${cell.timingAdvance}:${cell.isRegistered}:${cell.signalDbm}"
        }
        val prevSig = lastObservedSnapshotSignature
        if (signature == prevSig && (observedAtMs - lastObservedSnapshotMs) < OBSERVED_CELLS_MIN_INTERVAL_MS) {
            return@withLock
        }

        val seenCounts = HashMap<String, Int>(towers.size)
        val entities = towers.map { cell ->
            val baseKey = "${cell.type}_${cell.mcc ?: -1}_${cell.mnc ?: -1}_${cell.lac ?: -1}_${cell.cid ?: -1}_${cell.pci ?: -1}_${cell.earfcn ?: -1}"
            val occurrence = (seenCounts[baseKey] ?: 0) + 1
            seenCounts[baseKey] = occurrence
            val stableKey = if (occurrence == 1) baseKey else "${baseKey}#$occurrence"
            CachedObservedCellEntity(
                cellKey = stableKey,
                radioType = cell.type,
                mcc = cell.mcc,
                mnc = cell.mnc,
                cid = cell.cid,
                lac = cell.lac,
                signalDbm = cell.signalDbm,
                signalLevel = cell.signalLevel,
                isRegistered = cell.isRegistered,
                operatorName = cell.operatorName,
                timingAdvance = cell.timingAdvance,
                estimatedDistanceKm = cell.estimatedDistanceKm,
                pci = cell.pci,
                earfcn = cell.earfcn,
                observedAtMs = observedAtMs
            )
        }
        observedCellDao.replaceLatestSnapshot(entities)
        lastObservedSnapshotSignature = signature
        lastObservedSnapshotMs = observedAtMs
    }

    suspend fun clearLocationFixCache() = cacheMutex.withLock {
        lastPersistedFix = null
        locationInsertsSincePrune = 0
        locationFixDao.clearAll()
    }

    suspend fun saveNavRoute(route: NavRoute, isActive: Boolean) = cacheMutex.withLock {
        val dao = navRouteDao ?: return@withLock
        if (isActive) {
            dao.clearActiveFlags()
        }
        dao.upsertRoute(
            CachedNavRouteEntity(
                id = route.id,
                destinationName = route.destinationName,
                destinationLat = route.destinationLat,
                destinationLon = route.destinationLon,
                profile = route.profile.name,
                source = route.source.name,
                totalDistanceMeters = route.totalDistanceMeters,
                totalDurationSeconds = route.totalDurationSeconds,
                isActive = isActive,
                routeJson = serializeNavRoute(route),
                updatedAtMs = System.currentTimeMillis()
            )
        )
        dao.pruneOldestRoutes(MAX_CACHED_NAV_ROUTES)
    }

    suspend fun getActiveNavRoute(): NavRoute? = cacheMutex.withLock {
        val dao = navRouteDao ?: return@withLock null
        dao.getActiveRoute()?.let { deserializeNavRoute(it.routeJson) }
    }

    suspend fun clearActiveNavRouteFlag() = cacheMutex.withLock {
        navRouteDao?.clearActiveFlags()
    }

    suspend fun findCachedNavRouteNear(
        originLat: Double,
        originLon: Double,
        destinationLat: Double,
        destinationLon: Double,
        profile: NavProfile
    ): NavRoute? = cacheMutex.withLock {
        val dao = navRouteDao ?: return@withLock null
        val rows = dao.getRecentRoutes(MAX_CACHED_NAV_ROUTES)
        for (row in rows) {
            if (row.profile != profile.name) continue
            if (haversineMeters(row.destinationLat, row.destinationLon, destinationLat, destinationLon) > 150.0) {
                continue
            }
            val parsed = deserializeNavRoute(row.routeJson) ?: continue
            val nearOrigin = haversineMeters(parsed.originLat, parsed.originLon, originLat, originLon) <= 1500.0
            val nearPolyline = nearOrigin || parsed.points.any { pt ->
                haversineMeters(pt.latitude, pt.longitude, originLat, originLon) <= 1500.0
            }
            if (nearPolyline) {
                return@withLock parsed.copy(source = NavRouteSource.CACHED_OFFLINE)
            }
        }
        null
    }

    fun observeRecentCachedNavRoutes(): Flow<List<NavRoute>> {
        val dao = navRouteDao ?: return flowOf(emptyList())
        return dao.observeRecentRoutes(MAX_CACHED_NAV_ROUTES).map { rows ->
            rows.mapNotNull { deserializeNavRoute(it.routeJson) }
        }
    }

    fun observeOfflineRoutingPacks(): Flow<List<OfflineRoutingPackEntity>> {
        val dao = routingPackDao ?: return flowOf(emptyList())
        return dao.observeAllPacks()
    }

    suspend fun getOfflineRoutingPacksSnapshot(): List<OfflineRoutingPackEntity> {
        return routingPackDao?.getAllPacks() ?: emptyList()
    }

    suspend fun getOfflineRoutingPackForRegion(regionId: String): OfflineRoutingPackEntity? {
        return routingPackDao?.getPackForRegion(regionId)
    }

    suspend fun saveOfflineRoutingPack(pack: OfflineRoutingPackEntity) = cacheMutex.withLock {
        routingPackDao?.upsertPack(pack)
    }

    suspend fun deleteOfflineRoutingPack(regionId: String) = cacheMutex.withLock {
        routingPackDao?.deletePack(regionId)
    }

    suspend fun clearAllOfflineRoutingPacks() = cacheMutex.withLock {
        routingPackDao?.clearAll()
    }

    suspend fun clearAllOfflineCaches() = cacheMutex.withLock {
        lastPersistedFix = null
        lastObservedSnapshotSignature = null
        lastObservedSnapshotMs = 0L
        locationInsertsSincePrune = 0
        towerUpsertsSincePrune = 0
        cellDao.clearAll()
        observedCellDao.clearAll()
        locationFixDao.clearAll()
        navRouteDao?.clearAll()
    }

    private fun CachedLocationFixEntity.toDomain(): LocationPoint = LocationPoint(
        id = id,
        latitude = latitude,
        longitude = longitude,
        accuracy = accuracy,
        speed = speed,
        altitude = altitude,
        bearing = bearing,
        provider = provider,
        timestamp = timestamp,
        sessionId = sessionId
    )

    private fun CachedObservedCellEntity.toDomain(): CellTowerInfo = CellTowerInfo(
        type = radioType,
        mcc = mcc,
        mnc = mnc,
        cid = cid,
        lac = lac,
        signalDbm = signalDbm,
        signalLevel = signalLevel,
        isRegistered = isRegistered,
        operatorName = operatorName,
        timingAdvance = timingAdvance,
        estimatedDistanceKm = estimatedDistanceKm,
        pci = pci,
        earfcn = earfcn
    )

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    private fun serializeNavRoute(route: NavRoute): String {
        val ptsArr = JSONArray()
        for (p in route.points) {
            ptsArr.put(
                JSONObject().apply {
                    put("lat", p.latitude)
                    put("lon", p.longitude)
                    put("alt", p.altitude)
                }
            )
        }
        val stepsArr = JSONArray()
        for (s in route.steps) {
            stepsArr.put(
                JSONObject().apply {
                    put("index", s.index)
                    put("type", s.maneuverType.name)
                    put("instruction", s.instruction)
                    put("verbal", s.verbalPrompt)
                    put("street", s.streetName)
                    put("dist", s.distanceMeters)
                    put("dur", s.durationSeconds)
                    put("bIdx", s.beginPointIndex)
                    put("eIdx", s.endPointIndex)
                    s.roundaboutExit?.let { put("exit", it) }
                }
            )
        }
        return JSONObject().apply {
            put("id", route.id)
            put("profile", route.profile.name)
            put("oLat", route.originLat)
            put("oLon", route.originLon)
            put("dName", route.destinationName)
            put("dLat", route.destinationLat)
            put("dLon", route.destinationLon)
            put("dist", route.totalDistanceMeters)
            put("dur", route.totalDurationSeconds)
            put("source", route.source.name)
            put("createdAt", route.createdAtMs)
            put("points", ptsArr)
            put("steps", stepsArr)
        }.toString()
    }

    private fun deserializeNavRoute(raw: String): NavRoute? {
        return try {
            val obj = JSONObject(raw)
            val id = obj.optString("id", "")
            if (id.isBlank()) return null
            val profile = NavProfile.entries.firstOrNull { it.name == obj.optString("profile") } ?: NavProfile.DRIVING
            val source = NavRouteSource.entries.firstOrNull { it.name == obj.optString("source") } ?: NavRouteSource.CACHED_OFFLINE
            val ptsArr = obj.optJSONArray("points") ?: return null
            val points = ArrayList<LocationPoint>(ptsArr.length())
            for (i in 0 until ptsArr.length()) {
                val pt = ptsArr.optJSONObject(i) ?: continue
                val lat = pt.optDouble("lat", Double.NaN)
                val lon = pt.optDouble("lon", Double.NaN)
                if (lat.isFinite() && lon.isFinite()) {
                    points.add(
                        LocationPoint(
                            latitude = lat,
                            longitude = lon,
                            accuracy = 5f,
                            altitude = pt.optDouble("alt", 0.0),
                            provider = "route",
                            timestamp = obj.optLong("createdAt", System.currentTimeMillis()),
                            sessionId = id
                        )
                    )
                }
            }
            if (points.size < 2) return null

            val stepsArr = obj.optJSONArray("steps") ?: JSONArray()
            val steps = ArrayList<NavStep>(stepsArr.length())
            for (i in 0 until stepsArr.length()) {
                val st = stepsArr.optJSONObject(i) ?: continue
                val mType = NavManeuverType.entries.firstOrNull { it.name == st.optString("type") }
                    ?: NavManeuverType.STRAIGHT
                steps.add(
                    NavStep(
                        index = st.optInt("index", i),
                        maneuverType = mType,
                        instruction = st.optString("instruction", ""),
                        verbalPrompt = st.optString("verbal", ""),
                        streetName = st.optString("street", ""),
                        distanceMeters = st.optDouble("dist", 0.0),
                        durationSeconds = st.optDouble("dur", 0.0),
                        beginPointIndex = st.optInt("bIdx", 0).coerceIn(0, points.lastIndex),
                        endPointIndex = st.optInt("eIdx", 0).coerceIn(0, points.lastIndex),
                        roundaboutExit = if (st.has("exit")) st.optInt("exit") else null
                    )
                )
            }

            NavRoute(
                id = id,
                profile = profile,
                originLat = obj.optDouble("oLat", points.first().latitude),
                originLon = obj.optDouble("oLon", points.first().longitude),
                destinationName = obj.optString("dName", "Destination"),
                destinationLat = obj.optDouble("dLat", points.last().latitude),
                destinationLon = obj.optDouble("dLon", points.last().longitude),
                points = points,
                steps = steps,
                totalDistanceMeters = obj.optDouble("dist", 0.0),
                totalDurationSeconds = obj.optDouble("dur", 0.0),
                source = source,
                createdAtMs = obj.optLong("createdAt", System.currentTimeMillis())
            )
        } catch (_: Exception) {
            null
        }
    }
}
