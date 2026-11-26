package com.geotrace.data.cache

import com.geotrace.model.CellTowerInfo
import com.geotrace.model.LocationPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfflineCacheRepositoryTest {

    private class FakeCellTowerCacheDao : CellTowerCacheDao {
        private val rows = linkedMapOf<String, CachedCellTowerEntity>()
        private val flow = MutableStateFlow<List<CachedCellTowerEntity>>(emptyList())

        private fun key(mcc: Int, mnc: Int, lac: Int, cid: Long) = "${mcc}_${mnc}_${lac}_$cid"

        private fun publish() {
            flow.value = rows.values.sortedByDescending { it.lastSeenAtMs }
        }

        override suspend fun findTower(mcc: Int, mnc: Int, lac: Int, cid: Long): CachedCellTowerEntity? =
            rows[key(mcc, mnc, lac, cid)]

        override fun observeAllTowers(): Flow<List<CachedCellTowerEntity>> = flow

        override fun observeTowerCount(): Flow<Int> = flow.map { it.size }

        override suspend fun getTowerCount(): Int = rows.size

        override suspend fun upsertTower(entity: CachedCellTowerEntity) {
            rows[key(entity.mcc, entity.mnc, entity.lac, entity.cid)] = entity
            publish()
        }

        override suspend fun touchTower(mcc: Int, mnc: Int, lac: Int, cid: Long, seenAtMs: Long) {
            val k = key(mcc, mnc, lac, cid)
            rows[k]?.let {
                rows[k] = it.copy(lastSeenAtMs = seenAtMs)
                publish()
            }
        }

        override suspend fun pruneOldestTowers(maxKeep: Int) {
            val keep = rows.values.sortedByDescending { it.lastSeenAtMs }.take(maxKeep)
            rows.clear()
            keep.forEach { rows[key(it.mcc, it.mnc, it.lac, it.cid)] = it }
            publish()
        }

        override suspend fun clearAll() {
            rows.clear()
            publish()
        }
    }

    private class FakeObservedCellDao : ObservedCellDao {
        private val rows = mutableListOf<CachedObservedCellEntity>()
        private val flow = MutableStateFlow<List<CachedObservedCellEntity>>(emptyList())
        var replaceCalls = 0

        private fun publish() {
            flow.value = rows.sortedWith(
                compareByDescending<CachedObservedCellEntity> { it.isRegistered }
                    .thenByDescending { it.observedAtMs }
            )
        }

        override fun observeLatestCells(): Flow<List<CachedObservedCellEntity>> = flow

        override suspend fun getLatestCells(): List<CachedObservedCellEntity> = flow.value

        override fun observeLatestObservedAtMs(): Flow<Long?> =
            flow.map { list -> list.maxOfOrNull { it.observedAtMs } }

        override suspend fun getLatestObservedAtMs(): Long? = rows.maxOfOrNull { it.observedAtMs }

        override suspend fun insertAll(cells: List<CachedObservedCellEntity>) {
            rows.addAll(cells)
            publish()
        }

        override suspend fun clearAll() {
            rows.clear()
            publish()
        }

        override suspend fun replaceLatestSnapshot(cells: List<CachedObservedCellEntity>) {
            replaceCalls++
            clearAll()
            if (cells.isNotEmpty()) insertAll(cells)
        }
    }

    private class FakeLocationFixCacheDao : LocationFixCacheDao {
        private var nextId = 1L
        val rows = mutableListOf<CachedLocationFixEntity>()
        private val flow = MutableStateFlow<List<CachedLocationFixEntity>>(emptyList())

        private fun publish() {
            flow.value = rows.sortedByDescending { it.timestamp }
        }

        override fun observeLatestFix(): Flow<CachedLocationFixEntity?> = flow.map { it.firstOrNull() }

        override suspend fun getLatestFix(): CachedLocationFixEntity? = flow.value.firstOrNull()

        override fun observeRecentFixes(limit: Int): Flow<List<CachedLocationFixEntity>> =
            flow.map { it.take(limit) }

        override fun observeFixCount(): Flow<Int> = flow.map { it.size }

        override suspend fun getFixCount(): Int = rows.size

        override suspend fun insertFix(entity: CachedLocationFixEntity) {
            val assigned = if (entity.id == 0L) entity.copy(id = nextId++) else entity
            rows.removeAll { it.id == assigned.id }
            rows.add(assigned)
            publish()
        }

        override suspend fun pruneOldestFixes(maxKeep: Int) {
            val keep = rows.sortedByDescending { it.timestamp }.take(maxKeep)
            rows.clear()
            rows.addAll(keep)
            publish()
        }

        override suspend fun clearAll() {
            rows.clear()
            publish()
        }
    }

    private lateinit var cellDao: FakeCellTowerCacheDao
    private lateinit var observedDao: FakeObservedCellDao
    private lateinit var fixDao: FakeLocationFixCacheDao
    private lateinit var repository: OfflineCacheRepository

    @Before
    fun setUp() {
        cellDao = FakeCellTowerCacheDao()
        observedDao = FakeObservedCellDao()
        fixDao = FakeLocationFixCacheDao()
        repository = OfflineCacheRepository(cellDao, observedDao, fixDao)
    }

    private fun fix(
        lat: Double = 0.3476,
        lon: Double = 32.5825,
        accuracy: Float = 8f,
        provider: String = "gps",
        timestamp: Long = 1_700_000_000_000L
    ) = LocationPoint(
        latitude = lat,
        longitude = lon,
        accuracy = accuracy,
        speed = 1.2f,
        altitude = 1180.0,
        bearing = 90f,
        provider = provider,
        timestamp = timestamp,
        sessionId = "s1"
    )

    @Test
    fun recordCachedLocationFix_throttlesByIntervalDistanceAndUpgrades() = runBlocking {
        val t0 = 1_700_000_000_000L
        repository.recordCachedLocationFix(fix(provider = "cellular", accuracy = 150f, timestamp = t0))
        assertEquals(1, fixDao.getFixCount())

        // Within 15s and 25m without accuracy upgrade -> skipped
        repository.recordCachedLocationFix(
            fix(lat = 0.34765, lon = 32.58255, provider = "cellular", accuracy = 140f, timestamp = t0 + 5_000L)
        )
        assertEquals(1, fixDao.getFixCount())

        // Cell -> GNSS upgrade -> persisted immediately
        repository.recordCachedLocationFix(
            fix(provider = "gps", accuracy = 25f, timestamp = t0 + 6_000L)
        )
        assertEquals(2, fixDao.getFixCount())

        // >= 15m accuracy improvement on GNSS -> persisted immediately
        repository.recordCachedLocationFix(
            fix(provider = "gps", accuracy = 5f, timestamp = t0 + 8_000L)
        )
        assertEquals(3, fixDao.getFixCount())

        // >= 15s interval elapsed -> persisted
        repository.recordCachedLocationFix(
            fix(provider = "gps", accuracy = 5f, timestamp = t0 + 25_000L)
        )
        assertEquals(4, fixDao.getFixCount())

        // >= 25m distance moved within 1s -> persisted
        repository.recordCachedLocationFix(
            fix(lat = 0.3481, lon = 32.5825, provider = "gps", accuracy = 5f, timestamp = t0 + 26_000L)
        )
        assertEquals(5, fixDao.getFixCount())
    }

    @Test
    fun recordCachedLocationFix_rejectsInvalidZeroAndLastGasp() = runBlocking {
        repository.recordCachedLocationFix(fix(lat = 0.0, lon = 0.0), force = true)
        repository.recordCachedLocationFix(fix(lat = Double.NaN, lon = 32.5), force = true)
        repository.recordCachedLocationFix(fix(lat = 95.0, lon = 32.5), force = true)
        repository.recordCachedLocationFix(fix(lat = 0.34, lon = 185.0), force = true)
        repository.recordCachedLocationFix(fix(lat = 0.34, lon = 32.5, provider = "last_gasp"), force = true)

        assertEquals(0, fixDao.getFixCount())
        assertNull(repository.getLatestCachedLocationSnapshot())
    }

    @Test
    fun recordCachedLocationFix_prunesInBatchesAtMaxCap() = runBlocking {
        val baseTs = 1_700_000_000_000L
        // Batch size is 10; inserting 110 forces prune at 110 down to MAX_CACHED_LOCATION_FIXES (100)
        repeat(110) { idx ->
            repository.recordCachedLocationFix(
                fix(lat = 0.34 + idx * 0.001, lon = 32.58, timestamp = baseTs + idx * 20_000L)
            )
        }
        assertEquals(OfflineCacheRepository.MAX_CACHED_LOCATION_FIXES, fixDao.getFixCount())
        val latest = repository.getLatestCachedLocationSnapshot()
        assertNotNull(latest)
        assertEquals(baseTs + 109 * 20_000L, latest!!.timestamp)
    }

    @Test
    fun saveObservedCellSnapshot_deduplicatesWithinIntervalAndTracksTaEarfcn() = runBlocking {
        val t0 = 1_700_000_000_000L
        val cellA = CellTowerInfo(
            type = "4G LTE",
            mcc = 641,
            mnc = 10,
            cid = 5001L,
            lac = 4020,
            signalDbm = -82,
            signalLevel = 3,
            isRegistered = true,
            operatorName = "MTN",
            timingAdvance = 2,
            pci = 101,
            earfcn = 1850
        )

        repository.saveObservedCellSnapshot(listOf(cellA), observedAtMs = t0)
        assertEquals(1, observedDao.replaceCalls)

        // Identical radio signature within 20s -> skipped
        repository.saveObservedCellSnapshot(listOf(cellA), observedAtMs = t0 + 5_000L)
        assertEquals(1, observedDao.replaceCalls)

        // Changed Timing Advance + EARFCN within 20s -> stored
        val cellB = cellA.copy(timingAdvance = 4, earfcn = 3100)
        repository.saveObservedCellSnapshot(listOf(cellB), observedAtMs = t0 + 8_000L)
        assertEquals(2, observedDao.replaceCalls)

        val restored = repository.getLatestObservedCellsSnapshot()
        assertEquals(1, restored.size)
        assertEquals(4, restored.first().timingAdvance)
        assertEquals(3100, restored.first().earfcn)
        assertEquals(t0 + 8_000L, repository.getLatestObservedAtMsSnapshot())
    }

    @Test
    fun clearAllOfflineCaches_clearsAllStoresAndResetsThrottle() = runBlocking {
        val sample = fix(timestamp = 1_700_000_000_000L)
        repository.recordCachedLocationFix(sample)
        repository.saveObservedCellSnapshot(
            listOf(CellTowerInfo("4G LTE", 641, 10, 1L, 2, -80, 3, true, operatorName = "MTN Uganda")),
            observedAtMs = 1_700_000_000_000L
        )
        assertTrue(fixDao.getFixCount() == 1)

        repository.clearAllOfflineCaches()
        assertEquals(0, fixDao.getFixCount())
        assertEquals(0, cellDao.getTowerCount())
        assertTrue(repository.getLatestObservedCellsSnapshot().isEmpty())

        // Re-inserting exact same fix after clear should succeed without throttle blocking
        repository.recordCachedLocationFix(sample)
        assertEquals(1, fixDao.getFixCount())
    }

    @Test
    fun recordCachedLocationFix_preservesRecentGnssOverCoarseCellular() = runBlocking {
        val t0 = 1_700_000_000_000L
        repository.recordCachedLocationFix(fix(lat = 0.3476, lon = 32.5825, provider = "gps", accuracy = 5f, timestamp = t0))
        assertEquals(1, fixDao.getFixCount())

        // Coarse cellular fallback 800m away arrives 30s later -> must NOT overwrite recent GNSS cache fix
        repository.recordCachedLocationFix(
            fix(lat = 0.3550, lon = 32.5825, provider = "cellular", accuracy = 850f, timestamp = t0 + 30_000L)
        )
        assertEquals(1, fixDao.getFixCount())
        assertEquals("gps", repository.getLatestCachedLocationSnapshot()?.provider)
    }
}
