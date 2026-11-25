package com.geotrace.service

import android.util.Log
import com.geotrace.model.CellTowerInfo
import com.geotrace.model.CellTowerLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

enum class OpenCellIdLookupState {
    IDLE,
    NO_API_KEY,
    RESOLVED,
    NOT_FOUND,
    AUTH_ERROR,
    RATE_LIMITED,
    NETWORK_ERROR
}

data class OpenCellIdStatus(
    val state: OpenCellIdLookupState = OpenCellIdLookupState.IDLE,
    val message: String = "Awaiting cellular read",
    val resolvedCount: Int = 0,
    val queriedCount: Int = 0,
    val updatedAtMs: Long = 0L
)

/**
 * OpenCellID Client that resolves real cell tower coordinates strictly from the OpenCellID API
 * using real radio identifiers read from TelephonyManager (no synthetic fallback coordinates).
 */
object OpenCellIdClient {

    interface CellTowerCacheStore {
        suspend fun getResolvedTower(mcc: Int, mnc: Int, lac: Int, cid: Long): PersistentCachedTower?
        suspend fun putResolvedTower(entry: PersistentCachedTower)
        suspend fun touchResolvedTower(mcc: Int, mnc: Int, lac: Int, cid: Long, seenAtMs: Long)
        suspend fun clearResolvedTowers()
    }

    data class PersistentCachedTower(
        val mcc: Int,
        val mnc: Int,
        val lac: Int,
        val cid: Long,
        val latitude: Double,
        val longitude: Double,
        val apiRangeMeters: Double,
        val operatorName: String,
        val radioType: String,
        val addressEstimate: String?,
        val resolvedAtMs: Long,
        val lastSeenAtMs: Long
    )

    private const val TAG = "OpenCellIdClient"
    private const val OPENCELLID_API_URL = "https://opencellid.org/cell/get"
    private const val NEGATIVE_NOT_FOUND_TTL_MS = 10L * 60L * 1000L // 10 minutes
    private const val NEGATIVE_ERROR_TTL_MS = 2L * 60L * 1000L // 2 minutes
    private const val ROOM_TOUCH_THROTTLE_MS = 5L * 60L * 1000L // 5 minutes
    private const val MAX_BATCH_LOOKUPS = 6

    private data class CachedSuccess(
        val location: CellTowerLocation,
        val apiRangeMeters: Double
    )

    private data class NegativeEntry(
        val state: OpenCellIdLookupState,
        val message: String,
        val expiresAtMs: Long
    )

    data class ApiFetchOutcome(
        val latitude: Double = Double.NaN,
        val longitude: Double = Double.NaN,
        val apiRangeMeters: Double = 800.0,
        val addressEstimate: String? = null,
        val isSuccess: Boolean = false,
        val errorState: OpenCellIdLookupState = OpenCellIdLookupState.NETWORK_ERROR,
        val errorMessage: String = ""
    )

    private data class QueryResult(
        val success: CachedSuccess? = null,
        val errorState: OpenCellIdLookupState = OpenCellIdLookupState.NETWORK_ERROR,
        val errorMessage: String = ""
    )

    // In-memory positive & negative caches keyed by PLMN/CID/LAC
    private val memoryCache = ConcurrentHashMap<String, CachedSuccess>()
    private val negativeCache = ConcurrentHashMap<String, NegativeEntry>()
    @Volatile
    private var persistentStore: CellTowerCacheStore? = null
    @Volatile
    private var lastKeyFingerprint: Int = 0
    @Volatile
    internal var apiFetcherOverride: ((apiKey: String, mcc: Int, mnc: Int, lac: Int, cid: Long, cellInfo: CellTowerInfo) -> ApiFetchOutcome)? = null

    private val _lookupStatus = MutableStateFlow(OpenCellIdStatus())
    val lookupStatus: StateFlow<OpenCellIdStatus> = _lookupStatus.asStateFlow()

    fun attachPersistentStore(store: CellTowerCacheStore?) {
        persistentStore = store
    }

    internal fun clearMemoryCacheOnlyForTests() {
        memoryCache.clear()
        negativeCache.clear()
        lastKeyFingerprint = 0
        _lookupStatus.value = OpenCellIdStatus()
    }

    suspend fun clearCache() {
        memoryCache.clear()
        negativeCache.clear()
        try {
            persistentStore?.clearResolvedTowers()
        } catch (e: Exception) {
            Log.w(TAG, "Failed clearing persistent cell tower cache: ${e.message}")
        }
        _lookupStatus.value = OpenCellIdStatus(
            state = OpenCellIdLookupState.IDLE,
            message = "OpenCellID cache cleared",
            updatedAtMs = System.currentTimeMillis()
        )
    }

    private fun computeEffectiveRange(apiRange: Double, cellInfo: CellTowerInfo): Double {
        val ta = cellInfo.timingAdvance
        return if (ta != null && ta >= 0) {
            ((ta + 1) * 78.12 * 1.5).coerceIn(100.0, 3500.0)
        } else {
            apiRange.coerceIn(100.0, 25_000.0)
        }
    }

    private fun isValidCoordinates(lat: Double, lon: Double): Boolean {
        return lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0 &&
            (lat != 0.0 || lon != 0.0)
    }

    /**
     * Resolves latitude/longitude of a physical cell tower via RAM -> Room persistent offline cache -> OpenCellID API.
     * Never injects synthetic fallback coordinates.
     */
    suspend fun resolveCellLocation(
        cellInfo: CellTowerInfo,
        apiKey: String = "",
        @Suppress("UNUSED_PARAMETER") userLatitude: Double? = null,
        @Suppress("UNUSED_PARAMETER") userLongitude: Double? = null,
        updateStatus: Boolean = true
    ): CellTowerLocation? = withContext(Dispatchers.IO) {
        val cid = cellInfo.cid ?: return@withContext null
        val mcc = cellInfo.mcc ?: return@withContext null
        val mnc = cellInfo.mnc ?: return@withContext null
        val lac = cellInfo.lac ?: return@withContext null

        val cacheKey = "${mcc}_${mnc}_${cid}_$lac"
        val now = System.currentTimeMillis()

        // 1. Check hot in-memory cache first (works completely offline)
        memoryCache[cacheKey]?.let { cached ->
            val resolved = cached.location.copy(
                rangeMeters = computeEffectiveRange(cached.apiRangeMeters, cellInfo),
                operatorName = cellInfo.operatorDisplayName.ifBlank { cached.location.operatorName },
                radioType = cellInfo.type.ifBlank { cached.location.radioType },
                isServing = cellInfo.isRegistered,
                signalDbm = cellInfo.signalDbm,
                isFromOfflineCache = true
            )
            if (updateStatus && cellInfo.isRegistered) {
                _lookupStatus.value = OpenCellIdStatus(
                    state = OpenCellIdLookupState.RESOLVED,
                    message = "Serving mast CID $cid resolved (offline cache)",
                    resolvedCount = 1,
                    queriedCount = 1,
                    updatedAtMs = now
                )
            }
            return@withContext resolved
        }

        // 2. Check durable Room persistent cache (survives process restarts & connectivity loss)
        val store = persistentStore
        if (store != null) {
            val persisted = try {
                store.getResolvedTower(mcc, mnc, lac, cid)
            } catch (e: Exception) {
                Log.w(TAG, "Persistent cell lookup failed: ${e.message}")
                null
            }
            if (persisted != null && isValidCoordinates(persisted.latitude, persisted.longitude)) {
                val baseLocation = CellTowerLocation(
                    latitude = persisted.latitude,
                    longitude = persisted.longitude,
                    rangeMeters = computeEffectiveRange(persisted.apiRangeMeters, cellInfo),
                    operatorName = cellInfo.operatorDisplayName.ifBlank { persisted.operatorName },
                    radioType = cellInfo.type.ifBlank { persisted.radioType },
                    cellId = cid,
                    lac = lac,
                    isFromOfflineCache = true,
                    addressEstimate = persisted.addressEstimate ?: "Resolved via OpenCellID (PLMN $mcc-$mnc)",
                    isServing = cellInfo.isRegistered,
                    signalDbm = cellInfo.signalDbm
                )
                memoryCache[cacheKey] = CachedSuccess(baseLocation, persisted.apiRangeMeters)
                negativeCache.remove(cacheKey)
                if (now - persisted.lastSeenAtMs >= ROOM_TOUCH_THROTTLE_MS) {
                    try {
                        store.touchResolvedTower(mcc, mnc, lac, cid, now)
                    } catch (_: Exception) {
                    }
                }
                if (updateStatus && cellInfo.isRegistered) {
                    _lookupStatus.value = OpenCellIdStatus(
                        state = OpenCellIdLookupState.RESOLVED,
                        message = "Serving mast CID $cid resolved (offline Room cache)",
                        resolvedCount = 1,
                        queriedCount = 1,
                        updatedAtMs = now
                    )
                }
                return@withContext baseLocation
            }
        }

        // 3. Online OpenCellID API resolution requires an API key
        val effectiveKey = apiKey.trim().ifBlank { com.geotrace.BuildConfig.OPENCELLID_API_KEY.trim() }
        if (effectiveKey.isBlank()) {
            if (updateStatus) {
                _lookupStatus.value = OpenCellIdStatus(
                    state = OpenCellIdLookupState.NO_API_KEY,
                    message = "Local fallback (no CI key in preview build; override in Settings)",
                    updatedAtMs = now
                )
            }
            return@withContext null
        }

        val keyFp = effectiveKey.hashCode()
        if (lastKeyFingerprint != keyFp) {
            lastKeyFingerprint = keyFp
            negativeCache.clear()
        }

        negativeCache[cacheKey]?.let { neg ->
            if (now < neg.expiresAtMs) {
                if (updateStatus && cellInfo.isRegistered) {
                    _lookupStatus.value = OpenCellIdStatus(
                        state = neg.state,
                        message = neg.message,
                        updatedAtMs = now
                    )
                }
                return@withContext null
            } else {
                negativeCache.remove(cacheKey)
            }
        }

        val result = executeQuery(
            apiKey = effectiveKey,
            mcc = mcc,
            mnc = mnc,
            lac = lac,
            cid = cid,
            cellInfo = cellInfo
        )

        if (result.success != null) {
            memoryCache[cacheKey] = result.success
            negativeCache.remove(cacheKey)
            val resolvedNow = System.currentTimeMillis()
            if (store != null) {
                try {
                    store.putResolvedTower(
                        PersistentCachedTower(
                            mcc = mcc,
                            mnc = mnc,
                            lac = lac,
                            cid = cid,
                            latitude = result.success.location.latitude,
                            longitude = result.success.location.longitude,
                            apiRangeMeters = result.success.apiRangeMeters,
                            operatorName = result.success.location.operatorName,
                            radioType = result.success.location.radioType,
                            addressEstimate = result.success.location.addressEstimate,
                            resolvedAtMs = resolvedNow,
                            lastSeenAtMs = resolvedNow
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Failed persisting cell tower to Room: ${e.message}")
                }
            }
            val resolved = result.success.location.copy(
                rangeMeters = computeEffectiveRange(result.success.apiRangeMeters, cellInfo),
                isServing = cellInfo.isRegistered,
                signalDbm = cellInfo.signalDbm,
                isFromOfflineCache = false
            )
            if (updateStatus && cellInfo.isRegistered) {
                _lookupStatus.value = OpenCellIdStatus(
                    state = OpenCellIdLookupState.RESOLVED,
                    message = "Serving mast CID $cid resolved via OpenCellID",
                    resolvedCount = 1,
                    queriedCount = 1,
                    updatedAtMs = resolvedNow
                )
            }
            resolved
        } else {
            val ttl = if (result.errorState == OpenCellIdLookupState.NOT_FOUND ||
                result.errorState == OpenCellIdLookupState.AUTH_ERROR
            ) {
                NEGATIVE_NOT_FOUND_TTL_MS
            } else {
                NEGATIVE_ERROR_TTL_MS
            }
            negativeCache[cacheKey] = NegativeEntry(
                state = result.errorState,
                message = result.errorMessage,
                expiresAtMs = System.currentTimeMillis() + ttl
            )
            if (updateStatus && cellInfo.isRegistered) {
                _lookupStatus.value = OpenCellIdStatus(
                    state = result.errorState,
                    message = result.errorMessage,
                    updatedAtMs = System.currentTimeMillis()
                )
            }
            null
        }
    }

    /**
     * Resolves visible cell towers reported by TelephonyManager in bounded parallel batch.
     * Returns Room/RAM-cached towers even when offline or without an API key; never injects synthetic towers.
     */
    suspend fun resolveAllCellLocations(
        towers: List<CellTowerInfo>,
        apiKey: String = "",
        userLatitude: Double? = null,
        userLongitude: Double? = null
    ): List<CellTowerLocation> = withContext(Dispatchers.IO) {
        if (towers.isEmpty()) return@withContext emptyList()

        val effectiveKey = apiKey.trim().ifBlank { com.geotrace.BuildConfig.OPENCELLID_API_KEY.trim() }
        val candidates = towers
            .filter { it.cid != null && it.mcc != null && it.mnc != null && it.lac != null }
            .distinctBy { "${it.mcc}_${it.mnc}_${it.cid}_${it.lac}" }
            .take(MAX_BATCH_LOOKUPS)

        if (candidates.isEmpty()) {
            _lookupStatus.value = OpenCellIdStatus(
                state = OpenCellIdLookupState.NOT_FOUND,
                message = "Current sector lacks full MCC/MNC/LAC/CID identifiers",
                updatedAtMs = System.currentTimeMillis()
            )
            return@withContext emptyList()
        }

        val resolved = coroutineScope {
            candidates.map { info ->
                async {
                    resolveCellLocation(
                        cellInfo = info,
                        apiKey = effectiveKey,
                        userLatitude = userLatitude,
                        userLongitude = userLongitude,
                        updateStatus = info.isRegistered
                    )
                }
            }.awaitAll().filterNotNull()
        }

        val seenCids = mutableSetOf<Long>()
        val deduped = resolved.filter { seenCids.add(it.cellId) }
        if (deduped.isNotEmpty()) {
            val allCached = deduped.all { it.isFromOfflineCache }
            _lookupStatus.value = OpenCellIdStatus(
                state = OpenCellIdLookupState.RESOLVED,
                message = if (allCached) {
                    "Resolved ${deduped.size}/${candidates.size} radio mast(s) from offline cache"
                } else {
                    "Resolved ${deduped.size}/${candidates.size} radio mast(s) via OpenCellID"
                },
                resolvedCount = deduped.size,
                queriedCount = candidates.size,
                updatedAtMs = System.currentTimeMillis()
            )
        } else if (effectiveKey.isBlank()) {
            _lookupStatus.value = OpenCellIdStatus(
                state = OpenCellIdLookupState.NO_API_KEY,
                message = "Local fallback (no CI key in preview build; override in Settings)",
                updatedAtMs = System.currentTimeMillis()
            )
        }
        deduped
    }

    private fun executeQuery(
        apiKey: String,
        mcc: Int,
        mnc: Int,
        lac: Int,
        cid: Long,
        cellInfo: CellTowerInfo
    ): QueryResult {
        val override = apiFetcherOverride
        if (override != null) {
            val outcome = override(apiKey, mcc, mnc, lac, cid, cellInfo)
            return if (outcome.isSuccess && isValidCoordinates(outcome.latitude, outcome.longitude)) {
                val safeRange = outcome.apiRangeMeters.takeIf { it.isFinite() && it > 0.0 } ?: 800.0
                val loc = CellTowerLocation(
                    latitude = outcome.latitude,
                    longitude = outcome.longitude,
                    rangeMeters = computeEffectiveRange(safeRange, cellInfo),
                    operatorName = cellInfo.operatorDisplayName,
                    radioType = cellInfo.type,
                    cellId = cid,
                    lac = lac,
                    isFromOfflineCache = false,
                    addressEstimate = outcome.addressEstimate ?: "Resolved via OpenCellID (PLMN $mcc-$mnc)",
                    isServing = cellInfo.isRegistered,
                    signalDbm = cellInfo.signalDbm
                )
                QueryResult(success = CachedSuccess(loc, safeRange))
            } else {
                QueryResult(errorState = outcome.errorState, errorMessage = outcome.errorMessage)
            }
        }
        return queryOpenCellIdApi(apiKey, mcc, mnc, lac, cid, cellInfo)
    }

    private fun queryOpenCellIdApi(
        apiKey: String,
        mcc: Int,
        mnc: Int,
        lac: Int,
        cid: Long,
        cellInfo: CellTowerInfo
    ): QueryResult {
        var connection: HttpURLConnection? = null
        return try {
            val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
            val urlString = "$OPENCELLID_API_URL?key=$encodedKey&mcc=$mcc&mnc=$mnc&lac=$lac&cellid=$cid&format=json"
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("Accept", "application/json")
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.let {
                BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
            }.orEmpty()

            if (code == 200 && body.isNotBlank()) {
                val json = JSONObject(body)
                if (json.has("lat") && json.has("lon")) {
                    val lat = json.optDouble("lat", Double.NaN)
                    val lon = json.optDouble("lon", Double.NaN)
                    val validCoords = lat.isFinite() && lon.isFinite() &&
                        lat in -90.0..90.0 && lon in -180.0..180.0 &&
                        (lat != 0.0 || lon != 0.0)
                    if (!validCoords) {
                        return QueryResult(
                            errorState = OpenCellIdLookupState.NOT_FOUND,
                            errorMessage = "OpenCellID returned invalid coordinates for CID $cid"
                        )
                    }
                    val apiRange = json.optDouble("range", 800.0).takeIf { it.isFinite() && it > 0.0 } ?: 800.0
                    val loc = CellTowerLocation(
                        latitude = lat,
                        longitude = lon,
                        rangeMeters = computeEffectiveRange(apiRange, cellInfo),
                        operatorName = cellInfo.operatorDisplayName,
                        radioType = cellInfo.type,
                        cellId = cid,
                        lac = lac,
                        isFromOfflineCache = false,
                        addressEstimate = "Resolved via OpenCellID (PLMN $mcc-$mnc)",
                        isServing = cellInfo.isRegistered,
                        signalDbm = cellInfo.signalDbm
                    )
                    QueryResult(success = CachedSuccess(loc, apiRange))
                } else {
                    val apiErr = json.optString("error", "").ifBlank { "Cell CID $cid not indexed in OpenCellID" }
                    val errCode = json.optInt("code", -1)
                    val state = when (errCode) {
                        2 -> OpenCellIdLookupState.AUTH_ERROR
                        3, 4 -> OpenCellIdLookupState.RATE_LIMITED
                        else -> OpenCellIdLookupState.NOT_FOUND
                    }
                    QueryResult(errorState = state, errorMessage = apiErr)
                }
            } else {
                Log.w(TAG, "OpenCellID HTTP $code for CID=$cid")
                val state = when (code) {
                    401, 403 -> OpenCellIdLookupState.AUTH_ERROR
                    404 -> OpenCellIdLookupState.NOT_FOUND
                    429 -> OpenCellIdLookupState.RATE_LIMITED
                    else -> OpenCellIdLookupState.NETWORK_ERROR
                }
                val msg = when (state) {
                    OpenCellIdLookupState.AUTH_ERROR -> "OpenCellID rejected API key (HTTP $code)"
                    OpenCellIdLookupState.NOT_FOUND -> "Cell CID $cid not found in OpenCellID"
                    OpenCellIdLookupState.RATE_LIMITED -> "OpenCellID rate limit reached (HTTP 429)"
                    else -> "OpenCellID unavailable (HTTP $code)"
                }
                QueryResult(errorState = state, errorMessage = msg)
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenCellID API query failed: ${e.message}")
            QueryResult(
                errorState = OpenCellIdLookupState.NETWORK_ERROR,
                errorMessage = "OpenCellID lookup offline or timed out"
            )
        } finally {
            connection?.disconnect()
        }
    }
}
