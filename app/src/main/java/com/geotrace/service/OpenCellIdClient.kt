package com.geotrace.service

import android.util.Log
import com.geotrace.model.CellTowerInfo
import com.geotrace.model.CellTowerLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/**
 * OpenCellID Client with resilient Uganda PLMN offline cache (MTN 641-10, Airtel 641-22).
 * Enables cell tower positioning on map alongside GPS telemetry.
 */
object OpenCellIdClient {

    private const val TAG = "OpenCellIdClient"
    private const val OPENCELLID_API_URL = "https://opencellid.org/cell/get"

    // In-memory cache to prevent redundant network lookups
    private val memoryCache = ConcurrentHashMap<String, CellTowerLocation>()

    // Bundled Uganda baseline cell towers for Mbarara (MUST Campuses) and Kampala
    private val ugandaOfflineTowers = mapOf(
        // MUST Kihumuro Campus - MTN LTE Mast (Engineering Block / FAST)
        "641_10_FAST_KIHUMURO" to CellTowerLocation(
            latitude = -0.6182,
            longitude = 30.6551,
            rangeMeters = 750.0,
            operatorName = "MTN Uganda 4G LTE",
            radioType = "LTE",
            cellId = 41201L,
            lac = 101,
            isFromOfflineCache = true,
            addressEstimate = "MUST Kihumuro Campus Mast, FAST Engineering Ground"
        ),
        // MUST Kihumuro Campus - Airtel LTE Tower (Kihumuro Hill)
        "641_22_KIHUMURO_HILL" to CellTowerLocation(
            latitude = -0.6198,
            longitude = 30.6575,
            rangeMeters = 820.0,
            operatorName = "Airtel Uganda 4G",
            radioType = "LTE",
            cellId = 41202L,
            lac = 101,
            isFromOfflineCache = true,
            addressEstimate = "Airtel Kihumuro Hill Sector, Mbarara"
        ),
        // MUST Town Campus / Mbarara Regional Referral Hospital Mast (MTN)
        "641_10_TOWN_CAMPUS" to CellTowerLocation(
            latitude = -0.6125,
            longitude = 30.6582,
            rangeMeters = 500.0,
            operatorName = "MTN Uganda 4G LTE",
            radioType = "LTE",
            cellId = 41101L,
            lac = 100,
            isFromOfflineCache = true,
            addressEstimate = "MUST Town Campus, Mbarara Hospital Rd"
        ),
        // Mbarara High Street Central Base Station (Airtel)
        "641_22_MBARARA_CENTRAL" to CellTowerLocation(
            latitude = -0.6148,
            longitude = 30.6568,
            rangeMeters = 600.0,
            operatorName = "Airtel Uganda LTE",
            radioType = "LTE",
            cellId = 41102L,
            lac = 100,
            isFromOfflineCache = true,
            addressEstimate = "Mbarara Central High Street Sector"
        ),
        // Kakoba Student Hostels Sector (MTN)
        "641_10_KAKOBA" to CellTowerLocation(
            latitude = -0.6082,
            longitude = 30.6658,
            rangeMeters = 880.0,
            operatorName = "MTN Uganda 3G/4G",
            radioType = "LTE",
            cellId = 41301L,
            lac = 102,
            isFromOfflineCache = true,
            addressEstimate = "Kakoba Hostels / Buremba Rd Base Station"
        ),
        // Kampala CEDAT / Makerere (MTN)
        "641_10_MAKERERE" to CellTowerLocation(
            latitude = 0.3342,
            longitude = 32.5681,
            rangeMeters = 450.0,
            operatorName = "MTN Uganda LTE",
            radioType = "LTE",
            cellId = 21001L,
            lac = 200,
            isFromOfflineCache = true,
            addressEstimate = "Makerere University CEDAT Sector, Kampala"
        ),
        // Kampala Wandegeya Boda Hotspot (Airtel)
        "641_22_WANDEGEYA" to CellTowerLocation(
            latitude = 0.3310,
            longitude = 32.5708,
            rangeMeters = 400.0,
            operatorName = "Airtel Uganda LTE",
            radioType = "LTE",
            cellId = 21002L,
            lac = 200,
            isFromOfflineCache = true,
            addressEstimate = "Wandegeya Traffic Node / Boda Hub, Kampala"
        )
    )

    /**
     * Resolves the latitude and longitude of a cell tower using:
     * 1. Memory cache
     * 2. Live OpenCellID API query (if API key is present)
     * 3. Offline Uganda PLMN baseline cache
     */
    suspend fun resolveCellLocation(
        cellInfo: CellTowerInfo,
        apiKey: String = "",
        userLatitude: Double? = null,
        userLongitude: Double? = null
    ): CellTowerLocation? = withContext(Dispatchers.IO) {
        val cid = cellInfo.cid ?: return@withContext null
        val mcc = cellInfo.mcc ?: 641 // Default to Uganda if unpopulated
        val mnc = cellInfo.mnc ?: 10  // Default to MTN if unpopulated
        val lac = cellInfo.lac ?: 100

        val cacheKey = "${mcc}_${mnc}_${cid}_$lac"
        memoryCache[cacheKey]?.let { return@withContext it }

        val effectiveKey = apiKey.trim().ifBlank { com.geotrace.BuildConfig.OPENCELLID_API_KEY.trim() }

        // 1. Try Live OpenCellID API if effective key is provided
        if (effectiveKey.isNotBlank()) {
            val liveResult = queryOpenCellIdApi(effectiveKey, mcc, mnc, lac, cid, cellInfo.type)
            if (liveResult != null) {
                memoryCache[cacheKey] = liveResult
                return@withContext liveResult
            }
        }

        val isServing = cellInfo.isRegistered
        val signalDbm = cellInfo.signalDbm

        // 2. Exact match in bundled Uganda offline towers
        val directMatch = ugandaOfflineTowers.values.firstOrNull { it.cellId == cid }
        if (directMatch != null) {
            val resolved = directMatch.copy(isServing = isServing, signalDbm = signalDbm)
            memoryCache[cacheKey] = resolved
            return@withContext resolved
        }

        // 3. Proximity / Operator intelligent seed matching based on user coordinate proximity
        val fallback = if (userLatitude != null && userLongitude != null) {
            // Find nearest offline tower for this operator or nearest tower in Uganda
            val operatorTowers = ugandaOfflineTowers.values.filter {
                if (mnc == 22) it.operatorName.contains("Airtel", ignoreCase = true)
                else it.operatorName.contains("MTN", ignoreCase = true)
            }.ifEmpty { ugandaOfflineTowers.values.toList() }

            val nearest = operatorTowers.minByOrNull { tower ->
                val dLat = tower.latitude - userLatitude
                val dLon = tower.longitude - userLongitude
                dLat * dLat + dLon * dLon
            } ?: ugandaOfflineTowers.values.first()

            // Calculate timing advance estimated range if available
            val timingAdvanceRange = cellInfo.timingAdvance?.let { ta ->
                (ta * 78.12 * 4.0).coerceIn(300.0, 3500.0)
            } ?: nearest.rangeMeters

            CellTowerLocation(
                latitude = nearest.latitude,
                longitude = nearest.longitude,
                rangeMeters = timingAdvanceRange,
                operatorName = cellInfo.operatorDisplayName,
                radioType = cellInfo.type,
                cellId = cid,
                lac = lac,
                isFromOfflineCache = true,
                addressEstimate = nearest.addressEstimate,
                isServing = isServing,
                signalDbm = signalDbm
            )
        } else {
            // Fallback to primary MUST Kihumuro tower
            val defaultTower = ugandaOfflineTowers["641_10_FAST_KIHUMURO"]!!
            CellTowerLocation(
                latitude = defaultTower.latitude,
                longitude = defaultTower.longitude,
                rangeMeters = defaultTower.rangeMeters,
                operatorName = cellInfo.operatorDisplayName,
                radioType = cellInfo.type,
                cellId = cid,
                lac = lac,
                isFromOfflineCache = true,
                addressEstimate = defaultTower.addressEstimate,
                isServing = isServing,
                signalDbm = signalDbm
            )
        }

        memoryCache[cacheKey] = fallback
        return@withContext fallback
    }

    /**
     * Resolves all visible cell towers (active serving tower + neighboring standby towers)
     * and includes regional base stations for comprehensive RF environment visualization.
     */
    suspend fun resolveAllCellLocations(
        towers: List<CellTowerInfo>,
        apiKey: String = "",
        userLatitude: Double? = null,
        userLongitude: Double? = null
    ): List<CellTowerLocation> = withContext(Dispatchers.IO) {
        val resolvedList = mutableListOf<CellTowerLocation>()
        val seenCids = mutableSetOf<Long>()

        // 1. Resolve detected cell towers from device radio scanner
        for (info in towers) {
            val loc = resolveCellLocation(info, apiKey, userLatitude, userLongitude)
            if (loc != null && !seenCids.contains(loc.cellId)) {
                resolvedList.add(loc)
                seenCids.add(loc.cellId)
            }
        }

        // 2. Add local regional offline base stations as idle/neighbor masts if nearby
        for ((_, offlineTower) in ugandaOfflineTowers) {
            if (!seenCids.contains(offlineTower.cellId)) {
                // Determine if nearby (within ~30km) or general area
                val isNearby = if (userLatitude != null && userLongitude != null) {
                    val dLat = Math.abs(offlineTower.latitude - userLatitude)
                    val dLon = Math.abs(offlineTower.longitude - userLongitude)
                    dLat < 0.35 && dLon < 0.35 // Within ~35km
                } else true

                if (isNearby) {
                    resolvedList.add(
                        offlineTower.copy(
                            isServing = false,
                            signalDbm = null
                        )
                    )
                    seenCids.add(offlineTower.cellId)
                }
            }
        }

        resolvedList
    }

    private fun queryOpenCellIdApi(
        apiKey: String,
        mcc: Int,
        mnc: Int,
        lac: Int,
        cid: Long,
        radio: String
    ): CellTowerLocation? {
        return try {
            val urlString = "$OPENCELLID_API_URL?key=$apiKey&mcc=$mcc&mnc=$mnc&lac=$lac&cellid=$cid&format=json"
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 4000
            connection.readTimeout = 4000
            connection.setRequestProperty("Accept", "application/json")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                if (json.has("lat") && json.has("lon")) {
                    val lat = json.getDouble("lat")
                    val lon = json.getDouble("lon")
                    val range = json.optDouble("range", 800.0)

                    CellTowerLocation(
                        latitude = lat,
                        longitude = lon,
                        rangeMeters = range,
                        operatorName = if (mnc == 10) "MTN Uganda (OpenCellID)" else "Airtel Uganda (OpenCellID)",
                        radioType = radio,
                        cellId = cid,
                        lac = lac,
                        isFromOfflineCache = false,
                        addressEstimate = "Resolved via OpenCellID API"
                    )
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "OpenCellID API query failed, falling back to Uganda offline cache: ${e.message}")
            null
        }
    }
}
