package com.geotrace.service

import com.geotrace.model.CellTowerInfo
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OpenCellIdClientCacheTest {

    private class FakePersistentCellStore : OpenCellIdClient.CellTowerCacheStore {
        val rows = linkedMapOf<String, OpenCellIdClient.PersistentCachedTower>()

        private fun key(mcc: Int, mnc: Int, lac: Int, cid: Long) = "${mcc}_${mnc}_${lac}_$cid"

        override suspend fun getResolvedTower(
            mcc: Int,
            mnc: Int,
            lac: Int,
            cid: Long
        ): OpenCellIdClient.PersistentCachedTower? = rows[key(mcc, mnc, lac, cid)]

        override suspend fun putResolvedTower(entry: OpenCellIdClient.PersistentCachedTower) {
            rows[key(entry.mcc, entry.mnc, entry.lac, entry.cid)] = entry
        }

        override suspend fun touchResolvedTower(
            mcc: Int,
            mnc: Int,
            lac: Int,
            cid: Long,
            seenAtMs: Long
        ) {
            val k = key(mcc, mnc, lac, cid)
            rows[k]?.let { rows[k] = it.copy(lastSeenAtMs = seenAtMs) }
        }

        override suspend fun clearResolvedTowers() {
            rows.clear()
        }
    }

    private lateinit var store: FakePersistentCellStore

    private fun sampleCell(
        cid: Long = 123456L,
        timingAdvance: Int? = null,
        isRegistered: Boolean = true
    ) = CellTowerInfo(
        type = "4G LTE",
        mcc = 641,
        mnc = 10,
        cid = cid,
        lac = 4020,
        signalDbm = -84,
        signalLevel = 3,
        isRegistered = isRegistered,
        operatorName = "MTN Uganda",
        timingAdvance = timingAdvance
    )

    @Before
    fun setUp() {
        store = FakePersistentCellStore()
        OpenCellIdClient.attachPersistentStore(store)
        OpenCellIdClient.clearMemoryCacheOnlyForTests()
        OpenCellIdClient.apiFetcherOverride = null
    }

    @After
    fun tearDown() {
        OpenCellIdClient.apiFetcherOverride = null
        OpenCellIdClient.clearMemoryCacheOnlyForTests()
        OpenCellIdClient.attachPersistentStore(null)
    }

    @Test
    fun resolveCellLocation_persistsToStore_andSurvivesProcessRestartOffline() = runBlocking {
        var apiCalls = 0
        OpenCellIdClient.apiFetcherOverride = { _, _, _, _, _, _ ->
            apiCalls++
            OpenCellIdClient.ApiFetchOutcome(
                latitude = -0.6167,
                longitude = 30.6500,
                apiRangeMeters = 1850.0,
                isSuccess = true
            )
        }

        val initial = OpenCellIdClient.resolveCellLocation(
            cellInfo = sampleCell(timingAdvance = 1),
            apiKey = "test-key"
        )
        assertNotNull(initial)
        assertFalse(initial!!.isFromOfflineCache)
        assertEquals(1, apiCalls)
        assertEquals(1, store.rows.size)
        // Raw API range (1850.0) must be stored in Room, not the TA-clamped range (~234.36m)
        assertEquals(1850.0, store.rows.values.first().apiRangeMeters, 0.01)

        // Simulate app process death & offline connectivity
        OpenCellIdClient.clearMemoryCacheOnlyForTests()
        OpenCellIdClient.apiFetcherOverride = { _, _, _, _, _, _ ->
            apiCalls++
            OpenCellIdClient.ApiFetchOutcome(
                isSuccess = false,
                errorState = OpenCellIdLookupState.NETWORK_ERROR,
                errorMessage = "Offline"
            )
        }

        // Resolve again offline without Timing Advance -> should restore raw 1850m from persistent store
        val offlineWithoutTa = OpenCellIdClient.resolveCellLocation(
            cellInfo = sampleCell(timingAdvance = null),
            apiKey = "" // even blank API key works when cached in Room
        )
        assertNotNull(offlineWithoutTa)
        assertTrue(offlineWithoutTa!!.isFromOfflineCache)
        assertEquals(-0.6167, offlineWithoutTa.latitude, 0.0001)
        assertEquals(30.6500, offlineWithoutTa.longitude, 0.0001)
        assertEquals(1850.0, offlineWithoutTa.rangeMeters, 0.01)
        assertEquals(1, apiCalls) // No network call made
    }

    @Test
    fun resolveAllCellLocations_returnsPersistentCachedTowersWithoutApiKey() = runBlocking {
        store.putResolvedTower(
            OpenCellIdClient.PersistentCachedTower(
                mcc = 641,
                mnc = 10,
                lac = 4020,
                cid = 1001L,
                latitude = 0.3476,
                longitude = 32.5825,
                apiRangeMeters = 1200.0,
                operatorName = "MTN Uganda",
                radioType = "4G LTE",
                addressEstimate = "Cached Kampala Mast",
                resolvedAtMs = 1_700_000_000_000L,
                lastSeenAtMs = 1_700_000_000_000L
            )
        )

        val resolved = OpenCellIdClient.resolveAllCellLocations(
            towers = listOf(sampleCell(cid = 1001L, isRegistered = true)),
            apiKey = ""
        )
        assertEquals(1, resolved.size)
        assertTrue(resolved.first().isFromOfflineCache)
        assertEquals(1001L, resolved.first().cellId)
        assertEquals(OpenCellIdLookupState.RESOLVED, OpenCellIdClient.lookupStatus.value.state)
    }

    @Test
    fun networkError_isNeverPersistedToPersistentStore() = runBlocking {
        OpenCellIdClient.apiFetcherOverride = { _, _, _, _, _, _ ->
            OpenCellIdClient.ApiFetchOutcome(
                isSuccess = false,
                errorState = OpenCellIdLookupState.NETWORK_ERROR,
                errorMessage = "Timed out"
            )
        }

        val result = OpenCellIdClient.resolveCellLocation(
            cellInfo = sampleCell(cid = 9999L),
            apiKey = "test-key"
        )
        assertNull(result)
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun clearCache_clearsBothMemoryAndPersistentStore() = runBlocking {
        OpenCellIdClient.apiFetcherOverride = { _, _, _, _, _, _ ->
            OpenCellIdClient.ApiFetchOutcome(
                latitude = 0.3136,
                longitude = 32.5811,
                apiRangeMeters = 900.0,
                isSuccess = true
            )
        }

        assertNotNull(OpenCellIdClient.resolveCellLocation(sampleCell(), apiKey = "test-key"))
        assertEquals(1, store.rows.size)

        OpenCellIdClient.clearCache()
        assertTrue(store.rows.isEmpty())
        assertEquals(OpenCellIdLookupState.IDLE, OpenCellIdClient.lookupStatus.value.state)
    }
}
