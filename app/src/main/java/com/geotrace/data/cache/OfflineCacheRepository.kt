package com.geotrace.data.cache

import com.geotrace.model.CellTowerInfo
import com.geotrace.model.LocationPoint
import com.geotrace.service.OpenCellIdClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class OfflineCacheRepository(
    private val cellDao: CellTowerCacheDao,
    private val observedCellDao: ObservedCellDao,
    private val locationFixDao: LocationFixCacheDao
) : OpenCellIdClient.CellTowerCacheStore {

    constructor(database: OfflineCacheDatabase) : this(
        database.cellTowerCacheDao(),
        database.observedCellDao(),
        database.locationFixCacheDao()
    )

    companion object {
        const val MAX_CACHED_CELL_TOWERS = 2000
        const val MAX_CACHED_LOCATION_FIXES = 100
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

    suspend fun clearAllOfflineCaches() = cacheMutex.withLock {
        lastPersistedFix = null
        lastObservedSnapshotSignature = null
        lastObservedSnapshotMs = 0L
        locationInsertsSincePrune = 0
        towerUpsertsSincePrune = 0
        cellDao.clearAll()
        observedCellDao.clearAll()
        locationFixDao.clearAll()
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
}
