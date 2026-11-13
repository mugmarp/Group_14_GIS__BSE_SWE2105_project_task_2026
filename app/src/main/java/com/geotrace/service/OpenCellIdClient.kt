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
 * OpenCellID Client that resolves real cell tower coordinates strictly from the OpenCellID API
 * using real radio identifiers read from TelephonyManager (no synthetic fallback coordinates).
 */
object OpenCellIdClient {

    private const val TAG = "OpenCellIdClient"
    private const val OPENCELLID_API_URL = "https://opencellid.org/cell/get"

    // In-memory cache to prevent redundant network lookups within the session
    private val memoryCache = ConcurrentHashMap<String, CellTowerLocation>()

    /**
     * Resolves latitude/longitude of a physical cell tower strictly via the OpenCellID API
     * when a valid API key and real MCC/MNC/CID/LAC identifiers are available.
     * Returns null if unresolved or if no API key is configured.
     */
    suspend fun resolveCellLocation(
        cellInfo: CellTowerInfo,
        apiKey: String = "",
        @Suppress("UNUSED_PARAMETER") userLatitude: Double? = null,
        @Suppress("UNUSED_PARAMETER") userLongitude: Double? = null
    ): CellTowerLocation? = withContext(Dispatchers.IO) {
        val cid = cellInfo.cid ?: return@withContext null
        val mcc = cellInfo.mcc ?: return@withContext null
        val mnc = cellInfo.mnc ?: return@withContext null
        val lac = cellInfo.lac ?: return@withContext null

        val effectiveKey = apiKey.trim().ifBlank { com.geotrace.BuildConfig.OPENCELLID_API_KEY.trim() }
        if (effectiveKey.isBlank()) {
            return@withContext null
        }

        val cacheKey = "${mcc}_${mnc}_${cid}_$lac"
        memoryCache[cacheKey]?.let { cached ->
            return@withContext cached.copy(
                isServing = cellInfo.isRegistered,
                signalDbm = cellInfo.signalDbm
            )
        }

        val liveResult = queryOpenCellIdApi(
            apiKey = effectiveKey,
            mcc = mcc,
            mnc = mnc,
            lac = lac,
            cid = cid,
            cellInfo = cellInfo
        )
        if (liveResult != null) {
            memoryCache[cacheKey] = liveResult
        }
        liveResult
    }

    /**
     * Resolves all visible cell towers reported by TelephonyManager.
     * Never injects synthetic or placeholder towers.
     */
    suspend fun resolveAllCellLocations(
        towers: List<CellTowerInfo>,
        apiKey: String = "",
        userLatitude: Double? = null,
        userLongitude: Double? = null
    ): List<CellTowerLocation> = withContext(Dispatchers.IO) {
        val resolvedList = mutableListOf<CellTowerLocation>()
        val seenCids = mutableSetOf<Long>()

        for (info in towers) {
            val loc = resolveCellLocation(info, apiKey, userLatitude, userLongitude)
            if (loc != null && seenCids.add(loc.cellId)) {
                resolvedList.add(loc)
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
        cellInfo: CellTowerInfo
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
                    val apiRange = json.optDouble("range", 800.0)
                    val taRange = cellInfo.timingAdvance?.let { ta ->
                        (ta * 78.12 * 4.0).coerceIn(150.0, 3500.0)
                    } ?: apiRange

                    CellTowerLocation(
                        latitude = lat,
                        longitude = lon,
                        rangeMeters = taRange,
                        operatorName = cellInfo.operatorDisplayName,
                        radioType = cellInfo.type,
                        cellId = cid,
                        lac = lac,
                        isFromOfflineCache = false,
                        addressEstimate = "Resolved via OpenCellID (PLMN $mcc-$mnc)",
                        isServing = cellInfo.isRegistered,
                        signalDbm = cellInfo.signalDbm
                    )
                } else null
            } else {
                Log.w(TAG, "OpenCellID HTTP ${connection.responseCode} for CID=$cid")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenCellID API query failed: ${e.message}")
            null
        }
    }
}
