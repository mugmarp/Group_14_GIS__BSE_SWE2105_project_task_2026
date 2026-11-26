package com.geotrace.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.geotrace.model.LocationPoint
import com.geotrace.model.OfflineMapRegion
import com.geotrace.model.OfflinePlacePoi
import com.geotrace.model.SearchPlaceResult
import com.geotrace.model.SearchResultSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.BoundingBox
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.UUID

/**
 * Multi-layer location & place search engine:
 * 1. Offline instant parser for Decimal Degrees, DMS, geo: URIs, and Google Maps links.
 * 2. Offline recent-search cache & Overpass POI extractor for downloaded offline regions.
 * 3. Viewport-biased online geocoding via Photon (primary autocomplete) + OSM Nominatim fallback.
 * All search outputs resolve to WGS84 coordinates so they work across all 5 osmdroid tile sources.
 */
class PlaceSearchManager(private val context: Context) {

    companion object {
        private const val TAG = "PlaceSearchManager"
        private const val PREFS_NAME = "geotrace_search_prefs"
        private const val KEY_RECENTS = "recent_places_json"
        private const val MAX_RECENTS = 8
        private const val USER_AGENT = "GeoTrace-GIS-FieldApp/1.0 (Android; Educational Telemetry)"

        /**
         * Parses raw Decimal Degrees, DMS coordinates, `geo:lat,lon` URIs, or Google Maps links
         * completely offline in <1ms.
         */
        fun parseDirectCoordinates(rawInput: String): SearchPlaceResult? {
            val text = rawInput.trim()
            if (text.isBlank()) return null

            // 1. Check DMS format e.g. 0°19'57.4"N, 32°34'06.6"E or 0° 19' 57.4" S 30° 39' 16.2" W
            val dmsRegex = Regex(
                """([+-]?\d{1,3})\s*[°d]\s*(\d{1,2})\s*['′m]\s*(\d{1,2}(?:\.\d+)?)\s*(?:["″s])?\s*([NSns])\s*[,;\s]+\s*([+-]?\d{1,3})\s*[°d]\s*(\d{1,2})\s*['′m]\s*(\d{1,2}(?:\.\d+)?)\s*(?:["″s])?\s*([EWew])"""
            )
            dmsRegex.find(text)?.let { m ->
                val latDeg = m.groupValues[1].toDoubleOrNull() ?: return null
                val latMin = m.groupValues[2].toDoubleOrNull() ?: return null
                val latSec = m.groupValues[3].toDoubleOrNull() ?: return null
                val latHem = m.groupValues[4].uppercase(Locale.US)
                val lonDeg = m.groupValues[5].toDoubleOrNull() ?: return null
                val lonMin = m.groupValues[6].toDoubleOrNull() ?: return null
                val lonSec = m.groupValues[7].toDoubleOrNull() ?: return null
                val lonHem = m.groupValues[8].uppercase(Locale.US)

                if (latMin >= 60.0 || latSec >= 60.0 || lonMin >= 60.0 || lonSec >= 60.0) return null
                var lat = kotlin.math.abs(latDeg) + latMin / 60.0 + latSec / 3600.0
                if (latHem == "S") lat = -lat
                var lon = kotlin.math.abs(lonDeg) + lonMin / 60.0 + lonSec / 3600.0
                if (lonHem == "W") lon = -lon

                if (isValidLatLon(lat, lon)) {
                    return buildCoordinateResult(lat, lon, "DMS Coordinate")
                }
            }

            // 2. Check explicit query/coordinate params (?q=lat,lon, &query=lat,lon, loc:lat,lon, @lat,lon)
            //    BEFORE bare geo:lat,lon so Android's `geo:0,0?q=-0.5947,30.6545(Camp)` extracts the destination instead of 0,0.
            val urlParamOrAtRegex = Regex("""(?:(?:\b(?:q|query)=)|loc:|@)(-?\d{1,3}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)""")
            urlParamOrAtRegex.find(text)?.let { m ->
                val lat = m.groupValues[1].toDoubleOrNull()
                val lon = m.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null && isValidLatLon(lat, lon)) {
                    return buildCoordinateResult(lat, lon, "Map Link / Geo URI")
                }
            }

            // 2b. Fallback bare geo:lat,lon URI (ignore geo:0,0 placeholder)
            val bareGeoRegex = Regex("""\bgeo:(-?\d{1,3}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)""")
            bareGeoRegex.find(text)?.let { m ->
                val lat = m.groupValues[1].toDoubleOrNull()
                val lon = m.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null && isValidLatLon(lat, lon) && (lat != 0.0 || lon != 0.0)) {
                    return buildCoordinateResult(lat, lon, "Geo URI")
                }
            }

            // 3. Check Decimal Degrees with optional degree symbols or N/S/E/W suffixes
            val ddRegex = Regex(
                """^\s*([+-]?\d{1,2}(?:\.\d+)?)\s*°?\s*([NSns])?\s*[,;\s]+\s*([+-]?\d{1,3}(?:\.\d+)?)\s*°?\s*([EWew])?\s*$"""
            )
            ddRegex.matchEntire(text)?.let { m ->
                var lat = m.groupValues[1].toDoubleOrNull() ?: return@let
                val latHem = m.groupValues[2].uppercase(Locale.US)
                var lon = m.groupValues[3].toDoubleOrNull() ?: return@let
                val lonHem = m.groupValues[4].uppercase(Locale.US)

                if (latHem == "S") lat = -kotlin.math.abs(lat) else if (latHem == "N") lat = kotlin.math.abs(lat)
                if (lonHem == "W") lon = -kotlin.math.abs(lon) else if (lonHem == "E") lon = kotlin.math.abs(lon)

                if (isValidLatLon(lat, lon)) {
                    return buildCoordinateResult(lat, lon, "Decimal Coordinates")
                }
            }

            return null
        }

        private fun buildCoordinateResult(lat: Double, lon: Double, label: String): SearchPlaceResult {
            val dd = String.format(Locale.US, "%.5f°, %.5f°", lat, lon)
            return SearchPlaceResult(
                id = "coord_${String.format(Locale.US, "%.6f_%.6f", lat, lon)}",
                name = dd,
                subtitle = "$label • Jump directly to coordinate",
                latitude = lat,
                longitude = lon,
                source = SearchResultSource.COORDINATE
            )
        }

        private fun isValidLatLon(lat: Double, lon: Double): Boolean {
            return lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0
        }

        private val SURVIVAL_FALLBACK_NAMES = mapOf(
            "drinking_water" to "Drinking Water",
            "water_point" to "Water Point",
            "spring" to "Natural Spring",
            "fuel" to "Fuel Station",
            "hospital" to "Hospital",
            "clinic" to "Medical Clinic",
            "pharmacy" to "Pharmacy",
            "doctors" to "Medical Post",
            "police" to "Police Post",
            "ranger_station" to "Ranger Station",
            "shelter" to "Emergency Shelter",
            "alpine_hut" to "Alpine Hut",
            "wilderness_hut" to "Wilderness Hut",
            "camp_site" to "Campsite",
            "viewpoint" to "Viewpoint",
            "information" to "Visitor Information",
            "toilets" to "Public Toilets",
            "waterfall" to "Waterfall",
            "peak" to "Peak",
            "bus_station" to "Bus Station",
            "aerodrome" to "Airfield / Airstrip",
            "atm" to "ATM",
            "parking" to "Trailhead / Parking"
        )

        /**
         * Pure JVM helper that maps Overpass element tags to (resolvedName, formattedCategory, priorityScore).
         * Higher priority scores surface medical/water/fuel/lodging/tourism essentials before generic roads/buildings.
         */
        fun categorizeAndScoreOverpassElement(tags: JSONObject): Triple<String?, String, Int> {
            val amenity = tags.optString("amenity").trim()
            val tourism = tags.optString("tourism").trim()
            val naturalTag = tags.optString("natural").trim()
            val railway = tags.optString("railway").trim()
            val aeroway = tags.optString("aeroway").trim()
            val place = tags.optString("place").trim()
            val shop = tags.optString("shop").trim()
            val building = tags.optString("building").trim().takeIf { it != "yes" } ?: ""
            val highway = tags.optString("highway").trim()

            val rawCategoryKey = amenity.ifBlank { tourism }
                .ifBlank { naturalTag }
                .ifBlank { if (railway == "station") "train_station" else "" }
                .ifBlank { aeroway }
                .ifBlank { place }
                .ifBlank { shop }
                .ifBlank { building }
                .ifBlank { if (highway.isNotBlank()) "road" else "place" }

            val priority = when {
                amenity in setOf("hospital", "clinic", "pharmacy", "doctors", "drinking_water", "water_point", "police", "ranger_station") -> 100
                naturalTag == "spring" -> 100
                amenity in setOf("fuel", "shelter") -> 90
                tourism in setOf("camp_site", "alpine_hut", "wilderness_hut", "hotel", "guest_house", "hostel", "chalet", "motel") -> 85
                tourism in setOf("viewpoint", "attraction", "information", "museum", "picnic_site") || naturalTag in setOf("waterfall", "peak") -> 75
                railway == "station" || aeroway == "aerodrome" || amenity in setOf("bus_station", "atm", "bank", "restaurant", "cafe", "toilets", "parking") -> 60
                place.isNotBlank() || shop.isNotBlank() -> 40
                else -> 20
            }

            val explicitName = tags.optString("name").trim()
            val fallbackName = SURVIVAL_FALLBACK_NAMES[amenity]
                ?: SURVIVAL_FALLBACK_NAMES[tourism]
                ?: SURVIVAL_FALLBACK_NAMES[naturalTag]
                ?: SURVIVAL_FALLBACK_NAMES[aeroway]
                ?: if (railway == "station") "Train Station" else null

            val resolvedName = explicitName.ifBlank { fallbackName ?: "" }.ifBlank { null }
            val formattedCategory = rawCategoryKey
                .replace('_', ' ')
                .replaceFirstChar { it.uppercase() }
            return Triple(resolvedName, formattedCategory, priority)
        }

        data class OverpassFetchOutcome(
            val pois: List<OfflinePlacePoi>,
            val succeeded: Boolean
        )

        private fun haversineMetersStatic(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
            return r * 2.0 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1.0 - a))
        }

        /**
         * Parses an Overpass API JSON response string into tourism/survival-prioritized [OfflinePlacePoi] records.
         * Collects and sorts ALL valid elements by priority before deduplicating so higher-priority records
         * always beat generic nodes at the same coordinate. Extracted cleanly for offline unit testing.
         */
        fun parseOverpassElementsJson(
            responseText: String,
            regionId: String,
            regionName: String,
            seenDedup: MutableSet<String> = HashSet(),
            maxResults: Int = 250
        ): List<OfflinePlacePoi> {
            val elements = try {
                JSONObject(responseText).optJSONArray("elements")
            } catch (_: Exception) {
                null
            } ?: return emptyList()

            val scored = mutableListOf<Triple<Int, String, OfflinePlacePoi>>()

            for (i in 0 until elements.length()) {
                val el = elements.optJSONObject(i) ?: continue
                val tags = el.optJSONObject("tags") ?: continue
                val (resolvedName, category, priority) = categorizeAndScoreOverpassElement(tags)
                val name = resolvedName ?: continue

                val centerObj = el.optJSONObject("center")
                val lat = centerObj?.optDouble("lat", Double.NaN) ?: el.optDouble("lat", Double.NaN)
                val lon = centerObj?.optDouble("lon", Double.NaN) ?: el.optDouble("lon", Double.NaN)
                if (!isValidLatLon(lat, lon)) continue

                val dedup = "${name.lowercase(Locale.US)}:${String.format(Locale.US, "%.4f,%.4f", lat, lon)}"

                val street = tags.optString("addr:street")
                val city = tags.optString("addr:city")
                val sub = listOfNotNull(
                    category.takeIf { it.isNotBlank() },
                    street.takeIf { it.isNotBlank() },
                    city.takeIf { it.isNotBlank() },
                    "Cached in $regionName"
                ).joinToString(" • ")

                val poi = OfflinePlacePoi(
                    id = "${regionId}_${el.optString("type")}_${el.optLong("id", UUID.randomUUID().mostSignificantBits)}",
                    regionId = regionId,
                    name = name,
                    category = category,
                    subtitle = sub,
                    latitude = lat,
                    longitude = lon
                )
                scored.add(Triple(priority, dedup, poi))
            }

            val sorted = scored.sortedWith(
                compareByDescending<Triple<Int, String, OfflinePlacePoi>> { it.first }
                    .thenBy { it.third.name }
            )
            val result = ArrayList<OfflinePlacePoi>(minOf(maxResults, sorted.size))
            for ((_, dedupKey, poi) in sorted) {
                if (seenDedup.add(dedupKey)) {
                    result.add(poi)
                    if (result.size >= maxResults) break
                }
            }
            return result
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isNetworkConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val notSuspended = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
            } else {
                true
            }
            hasInternet && notSuspended
        } catch (_: Exception) {
            false
        }
    }

    fun parseDirectCoordinates(rawInput: String): SearchPlaceResult? = Companion.parseDirectCoordinates(rawInput)

    data class OnlineSearchOutcome(
        val results: List<SearchPlaceResult>,
        val errorMessage: String? = null
    )

    suspend fun searchOnline(
        query: String,
        biasLat: Double?,
        biasLon: Double?,
        viewBox: BoundingBox?
    ): OnlineSearchOutcome = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext OnlineSearchOutcome(emptyList())

        var photonFailed = false
        val photonResults = try {
            queryPhoton(trimmed, biasLat, biasLon)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Photon search failed: ${e.message}")
            photonFailed = true
            null
        }
        if (photonResults != null && photonResults.isNotEmpty()) {
            return@withContext OnlineSearchOutcome(photonResults)
        }

        return@withContext try {
            val nomResults = queryNominatim(trimmed, viewBox)
            if (nomResults != null) {
                OnlineSearchOutcome(nomResults)
            } else {
                OnlineSearchOutcome(
                    results = emptyList(),
                    errorMessage = if (photonFailed || photonResults == null) {
                        "Online geocoder unreachable — showing local & cached results"
                    } else null
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Nominatim search failed: ${e.message}")
            OnlineSearchOutcome(
                results = emptyList(),
                errorMessage = "Online geocoder unavailable — showing local & cached results"
            )
        }
    }

    private fun queryPhoton(
        query: String,
        biasLat: Double?,
        biasLon: Double?
    ): List<SearchPlaceResult>? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val biasParams = if (biasLat != null && biasLon != null) {
            String.format(Locale.US, "&lat=%.5f&lon=%.5f", biasLat, biasLon)
        } else ""
        val urlStr = "https://photon.komoot.io/api/?q=$encoded&limit=8&lang=en$biasParams"
        val body = httpGet(urlStr, timeoutMs = 6000) ?: return null

        val root = JSONObject(body)
        val features = root.optJSONArray("features") ?: return emptyList()
        val results = mutableListOf<SearchPlaceResult>()
        val seenKeys = HashSet<String>()

        for (i in 0 until features.length()) {
            val feat = features.optJSONObject(i) ?: continue
            val geom = feat.optJSONObject("geometry") ?: continue
            val coords = geom.optJSONArray("coordinates") ?: continue
            if (coords.length() < 2) continue
            val lon = coords.optDouble(0, Double.NaN)
            val lat = coords.optDouble(1, Double.NaN)
            if (!isValidLatLon(lat, lon)) continue

            val props = feat.optJSONObject("properties") ?: JSONObject()
            val osmType = props.optString("osm_type", "")
            val osmId = props.optLong("osm_id", 0L)
            val primaryName = props.optString("name").ifBlank {
                props.optString("street").ifBlank { props.optString("city") }
            }.ifBlank { continue }

            val parts = listOfNotNull(
                props.optString("osm_value").takeIf { it.isNotBlank() }?.replace('_', ' ')?.replaceFirstChar { it.uppercase() },
                props.optString("street").takeIf { it.isNotBlank() && it != primaryName },
                props.optString("district").takeIf { it.isNotBlank() && it != primaryName },
                props.optString("city").takeIf { it.isNotBlank() && it != primaryName },
                props.optString("state").takeIf { it.isNotBlank() && it != primaryName },
                props.optString("country").takeIf { it.isNotBlank() && it != primaryName }
            ).distinct()

            val dedupKey = "${primaryName.lowercase(Locale.US)}:${String.format(Locale.US, "%.3f,%.3f", lat, lon)}"
            if (!seenKeys.add(dedupKey)) continue

            val extent = props.optJSONArray("extent") // [west, north, east, south]
            val west = extent?.optDouble(0)?.takeIf { it.isFinite() }
            val north = extent?.optDouble(1)?.takeIf { it.isFinite() }
            val east = extent?.optDouble(2)?.takeIf { it.isFinite() }
            val south = extent?.optDouble(3)?.takeIf { it.isFinite() }

            results.add(
                SearchPlaceResult(
                    id = "osm_${osmType}_$osmId",
                    name = primaryName,
                    subtitle = parts.joinToString(" • ").ifBlank { String.format(Locale.US, "%.5f°, %.5f°", lat, lon) },
                    latitude = lat,
                    longitude = lon,
                    source = SearchResultSource.ONLINE_OSM,
                    bboxNorth = north,
                    bboxSouth = south,
                    bboxEast = east,
                    bboxWest = west
                )
            )
        }
        return results
    }

    private fun queryNominatim(
        query: String,
        viewBox: BoundingBox?
    ): List<SearchPlaceResult>? {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val viewBoxParam = viewBox?.let { box ->
            String.format(
                Locale.US,
                "&viewbox=%.5f,%.5f,%.5f,%.5f",
                box.lonWest,
                box.latNorth,
                box.lonEast,
                box.latSouth
            )
        } ?: ""
        val urlStr = "https://nominatim.openstreetmap.org/search?format=jsonv2&addressdetails=1&limit=8&q=$encoded$viewBoxParam"
        val body = httpGet(urlStr, timeoutMs = 7000) ?: return null

        val arr = JSONArray(body)
        val results = mutableListOf<SearchPlaceResult>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val lat = item.optString("lat").toDoubleOrNull() ?: continue
            val lon = item.optString("lon").toDoubleOrNull() ?: continue
            if (!isValidLatLon(lat, lon)) continue

            val displayName = item.optString("display_name")
            val shortName = item.optString("name").ifBlank {
                displayName.substringBefore(",").trim()
            }.ifBlank { continue }
            val subtitle = displayName.substringAfter(",", "").trim().ifBlank {
                item.optString("type").replace('_', ' ').replaceFirstChar { it.uppercase() }
            }

            val bb = item.optJSONArray("boundingbox") // [south, north, west, east]
            val south = bb?.optString(0)?.toDoubleOrNull()
            val north = bb?.optString(1)?.toDoubleOrNull()
            val west = bb?.optString(2)?.toDoubleOrNull()
            val east = bb?.optString(3)?.toDoubleOrNull()

            results.add(
                SearchPlaceResult(
                    id = "nom_${item.optLong("place_id", i.toLong())}",
                    name = shortName,
                    subtitle = subtitle,
                    latitude = lat,
                    longitude = lon,
                    source = SearchResultSource.ONLINE_OSM,
                    bboxNorth = north,
                    bboxSouth = south,
                    bboxEast = east,
                    bboxWest = west
                )
            )
        }
        return results
    }

    private fun lonSpanDegrees(west: Double, east: Double): Double {
        return if (west <= east) east - west else (180.0 - west) + (east + 180.0)
    }

    suspend fun fetchRegionPoisForOfflineCache(region: OfflineMapRegion): List<OfflinePlacePoi> =
        fetchRegionPoisForOfflineCacheWithStatus(region).pois

    /**
     * Fetches tourism-prioritized POIs (lodging, camps, water, fuel, medical, viewpoints, transit)
     * plus named landmarks inside an offline region's bounding box from OpenStreetMap Overpass API.
     */
    suspend fun fetchRegionPoisForOfflineCacheWithStatus(region: OfflineMapRegion): OverpassFetchOutcome = withContext(Dispatchers.IO) {
        val s = region.southLat.coerceIn(-85.0, 85.0)
        val w = OfflineMapManager.normalizeLongitude(region.westLng)
        val n = region.northLat.coerceIn(-85.0, 85.0)
        val e = OfflineMapManager.normalizeLongitude(region.eastLng)
        if (n <= s || e == w) return@withContext OverpassFetchOutcome(emptyList(), succeeded = false)

        // Cap spans > 1.8 deg around region center while preserving antimeridian wrap
        val totalLatSpan = (n - s).coerceAtLeast(0.01)
        val totalLonSpan = lonSpanDegrees(w, e).coerceAtLeast(0.01)
        val boundedBox = if (totalLatSpan > 1.8 || totalLonSpan > 1.8) {
            val cLat = ((n + s) / 2.0).coerceIn(-84.5, 84.5)
            val cLon = if (w <= e) (w + e) / 2.0 else OfflineMapManager.normalizeLongitude(w + totalLonSpan / 2.0)
            val halfLat = minOf(0.85, totalLatSpan / 2.0)
            val halfLon = minOf(0.85, totalLonSpan / 2.0)
            OfflineMapManager.CorridorTileBox(
                north = (cLat + halfLat).coerceAtMost(85.0),
                south = (cLat - halfLat).coerceAtLeast(-85.0),
                east = OfflineMapManager.normalizeLongitude(cLon + halfLon),
                west = OfflineMapManager.normalizeLongitude(cLon - halfLon)
            )
        } else {
            OfflineMapManager.CorridorTileBox(north = n, south = s, east = e, west = w)
        }

        // Split antimeridian first, then subdivide large boxes (>0.6 deg span) into 2x2 or 3x3 tiles
        val baseRects = OfflineMapManager.splitBoxAtAntimeridian(boundedBox)
        val queryBoxes = mutableListOf<DoubleArray>()
        for (rect in baseRects) {
            val rS = rect.south
            val rW = rect.west
            val rN = rect.north
            val rE = rect.east
            val latSpan = (rN - rS).coerceAtLeast(0.01)
            val lonSpan = (rE - rW).coerceAtLeast(0.01)
            val latDiv = when {
                latSpan > 1.2 -> 3
                latSpan > 0.6 -> 2
                else -> 1
            }
            val lonDiv = when {
                lonSpan > 1.2 -> 3
                lonSpan > 0.6 -> 2
                else -> 1
            }
            val latSlices = (0 until latDiv).map { i ->
                (rS + (latSpan * i) / latDiv) to (rS + (latSpan * (i + 1)) / latDiv)
            }
            val lonSlices = (0 until lonDiv).map { j ->
                (rW + (lonSpan * j) / lonDiv) to (rW + (lonSpan * (j + 1)) / lonDiv)
            }
            for ((subS, subN) in latSlices) {
                for ((subW, subE) in lonSlices) {
                    queryBoxes.add(doubleArrayOf(subS, subW, subN, subE))
                }
            }
        }

        val allPriority = mutableListOf<OfflinePlacePoi>()
        val allLandmarks = mutableListOf<OfflinePlacePoi>()
        val seenDedup = HashSet<String>()
        var anySucceeded = false
        for ((idx, box) in queryBoxes.take(9).withIndex()) {
            if (idx >= 3) {
                delay(250L)
            }
            val outcome = executeOverpassQueryForBox(
                south = box[0],
                west = box[1],
                north = box[2],
                east = box[3],
                regionId = region.id,
                regionName = region.name,
                seenDedup = seenDedup,
                maxPriorityResults = 160,
                maxLandmarkResults = 80
            )
            if (outcome.succeeded) anySucceeded = true
            allPriority.addAll(outcome.priorityPois)
            allLandmarks.addAll(outcome.landmarkPois)
        }
        // Always retain priority survival/tourism POIs from all tiles before filling remaining slots with landmarks
        val combined = (allPriority.take(250) + allLandmarks).take(250)
        OverpassFetchOutcome(combined, succeeded = anySucceeded)
    }

    suspend fun fetchCorridorPoisForOfflineCache(
        points: List<LocationPoint>,
        regionId: String,
        regionName: String,
        halfWidthKm: Double = 1.5
    ): List<OfflinePlacePoi> = fetchCorridorPoisForOfflineCacheWithStatus(
        points = points,
        regionId = regionId,
        regionName = regionName,
        halfWidthKm = halfWidthKm
    ).pois

    /**
     * Fetches tourism and survival POIs along an expedition/trail corridor strip (e.g. ±1.5 km buffer),
     * balanced across corridor windows and filtered strictly by point-to-segment distance to the polyline.
     */
    suspend fun fetchCorridorPoisForOfflineCacheWithStatus(
        points: List<LocationPoint>,
        regionId: String,
        regionName: String,
        halfWidthKm: Double = 1.5
    ): OverpassFetchOutcome = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext OverpassFetchOutcome(emptyList(), succeeded = false)
        val rawBoxes = OfflineMapManager.computeCorridorBoxes(
            points = points,
            halfWidthKm = halfWidthKm,
            spacingMeters = maxOf(1500.0, halfWidthKm * 1000.0 * 1.4)
        )
        if (rawBoxes.isEmpty()) return@withContext OverpassFetchOutcome(emptyList(), succeeded = false)

        // Group into up to 4 sequential windows preserving antimeridian bounds
        val stride = ((rawBoxes.size + 3) / 4).coerceAtLeast(1)
        val mergedWindows = rawBoxes.chunked(stride).mapNotNull { chunk ->
            OfflineMapManager.unionBoundingBox(chunk)
        }

        // Sample route points every ~150m for accurate segment-distance corridor filtering
        val sampledRoute = mutableListOf<LocationPoint>()
        var lastRef: LocationPoint? = null
        for (p in points) {
            if (lastRef == null || haversineMetersStatic(lastRef.latitude, lastRef.longitude, p.latitude, p.longitude) >= 150.0) {
                sampledRoute.add(p)
                lastRef = p
            }
        }
        if (sampledRoute.lastOrNull() !== points.last()) sampledRoute.add(points.last())
        val maxAllowedMeters = (halfWidthKm * 1000.0 * 1.4).coerceAtLeast(1500.0)

        fun isWithinCorridor(poi: OfflinePlacePoi): Boolean {
            if (sampledRoute.size == 1) {
                val pt = sampledRoute.first()
                return haversineMetersStatic(pt.latitude, pt.longitude, poi.latitude, poi.longitude) <= maxAllowedMeters
            }
            for (i in 0 until sampledRoute.lastIndex) {
                val a = sampledRoute[i]
                val b = sampledRoute[i + 1]
                // Quick reject segments separated by huge offline gaps
                if (haversineMetersStatic(a.latitude, a.longitude, b.latitude, b.longitude) > 50_000.0) continue
                val d = pointToSegmentMetersStatic(poi.latitude, poi.longitude, a.latitude, a.longitude, b.latitude, b.longitude)
                if (d <= maxAllowedMeters) return true
            }
            return false
        }

        val allPriority = mutableListOf<OfflinePlacePoi>()
        val allLandmarks = mutableListOf<OfflinePlacePoi>()
        val seenDedup = HashSet<String>()
        var anySucceeded = false
        for (window in mergedWindows) {
            val wBoxes = OfflineMapManager.splitBoxAtAntimeridian(window)
            for (wb in wBoxes) {
                val outcome = executeOverpassQueryForBox(
                    south = wb.south,
                    west = wb.west,
                    north = wb.north,
                    east = wb.east,
                    regionId = regionId,
                    regionName = regionName,
                    seenDedup = seenDedup,
                    maxPriorityResults = 110,
                    maxLandmarkResults = 50
                )
                if (outcome.succeeded) anySucceeded = true
                allPriority.addAll(outcome.priorityPois.filter(::isWithinCorridor))
                allLandmarks.addAll(outcome.landmarkPois.filter(::isWithinCorridor))
            }
        }
        val combined = (allPriority.take(300) + allLandmarks).take(300)
        OverpassFetchOutcome(combined, succeeded = anySucceeded)
    }

    private fun pointToSegmentMetersStatic(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Double {
        val refLatRad = Math.toRadians((aLat + bLat) * 0.5)
        val metersPerDegLat = 111132.92
        val metersPerDegLon = (111412.84 * kotlin.math.cos(refLatRad)).coerceAtLeast(1.0)
        val px = (pLon - aLon) * metersPerDegLon
        val py = (pLat - aLat) * metersPerDegLat
        val bx = (bLon - aLon) * metersPerDegLon
        val by = (bLat - aLat) * metersPerDegLat
        val segLenSq = bx * bx + by * by
        if (segLenSq < 1e-6) return kotlin.math.sqrt(px * px + py * py)
        val t = ((px * bx + py * by) / segLenSq).coerceIn(0.0, 1.0)
        val dx = px - t * bx
        val dy = py - t * by
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private data class SplitOverpassBoxOutcome(
        val priorityPois: List<OfflinePlacePoi>,
        val landmarkPois: List<OfflinePlacePoi>,
        val succeeded: Boolean
    )

    private suspend fun executeOverpassQueryForBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        regionId: String,
        regionName: String,
        seenDedup: MutableSet<String>,
        maxPriorityResults: Int,
        maxLandmarkResults: Int
    ): SplitOverpassBoxOutcome {
        val bboxStr = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", south, west, north, east)
        // Primary query focuses strictly on high-priority survival, medical, water, lodging, tourism & transit POIs
        val priorityQuery = """
            [out:json][timeout:12];
            (
              nwr["tourism"~"camp_site|alpine_hut|wilderness_hut|hotel|guest_house|hostel|chalet|motel|viewpoint|attraction|information|museum|picnic_site"]($bboxStr);
              nwr["amenity"~"drinking_water|water_point|fuel|hospital|clinic|pharmacy|doctors|police|shelter|restaurant|cafe|atm|bank|toilets|bus_station|parking|ranger_station"]($bboxStr);
              nwr["natural"~"spring|peak|waterfall"]($bboxStr);
              nwr["railway"="station"]($bboxStr);
              nwr["aeroway"="aerodrome"]($bboxStr);
            );
            out center 250;
        """.trimIndent()

        val primaryRaw = postOverpassWithMirrorFallback(priorityQuery)
            ?: return SplitOverpassBoxOutcome(emptyList(), emptyList(), succeeded = false)

        val prioritized = parseOverpassElementsJson(
            responseText = primaryRaw,
            regionId = regionId,
            regionName = regionName,
            seenDedup = seenDedup,
            maxResults = maxPriorityResults
        )

        if (maxLandmarkResults <= 0) {
            return SplitOverpassBoxOutcome(prioritized, emptyList(), succeeded = true)
        }

        // Secondary light query only fills remaining slots with settlements, supply shops & named natural/historic landmarks
        val landmarksQuery = """
            [out:json][timeout:10];
            (
              nwr["place"~"city|town|village|hamlet|suburb"]($bboxStr);
              nwr["shop"]["name"]($bboxStr);
              node["historic"]["name"]($bboxStr);
              node["natural"~"saddle|cave_entrance|rock|glacier"]["name"]($bboxStr);
              node["highway"~"bus_stop|trailhead|pass"]["name"]($bboxStr);
            );
            out center $maxLandmarkResults;
        """.trimIndent()

        val secondaryRaw = postOverpassWithMirrorFallback(landmarksQuery)
        val landmarks = if (secondaryRaw != null) {
            parseOverpassElementsJson(
                responseText = secondaryRaw,
                regionId = regionId,
                regionName = regionName,
                seenDedup = seenDedup,
                maxResults = maxLandmarkResults
            )
        } else emptyList()

        return SplitOverpassBoxOutcome(prioritized, landmarks, succeeded = true)
    }

    private suspend fun postOverpassWithMirrorFallback(overpassQuery: String): String? {
        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )
        var shouldBackoff = false
        for (endpoint in endpoints) {
            if (shouldBackoff) {
                delay(900L)
                shouldBackoff = false
            }
            var conn: HttpURLConnection? = null
            try {
                val url = URL(endpoint)
                conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 13000
                    doOutput = true
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }
                OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                    writer.write("data=" + URLEncoder.encode(overpassQuery, "UTF-8"))
                    writer.flush()
                }
                val code = conn.responseCode
                if (code in 200..299) {
                    return BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                } else {
                    Log.w(TAG, "Overpass mirror $endpoint returned HTTP $code")
                    if (code == 429 || code == 503 || code == 504) {
                        shouldBackoff = true
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                Log.w(TAG, "Overpass mirror $endpoint failed: ${e.message}")
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    fun loadRecentSearches(): List<SearchPlaceResult> {
        return try {
            val raw = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
            val arr = JSONArray(raw)
            val list = mutableListOf<SearchPlaceResult>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val lat = obj.optDouble("lat", Double.NaN)
                val lon = obj.optDouble("lon", Double.NaN)
                if (!isValidLatLon(lat, lon)) continue
                list.add(
                    SearchPlaceResult(
                        id = obj.optString("id"),
                        name = obj.optString("name"),
                        subtitle = obj.optString("subtitle"),
                        latitude = lat,
                        longitude = lon,
                        source = SearchResultSource.RECENT,
                        bboxNorth = obj.optDouble("n").takeIf { it.isFinite() },
                        bboxSouth = obj.optDouble("s").takeIf { it.isFinite() },
                        bboxEast = obj.optDouble("e").takeIf { it.isFinite() },
                        bboxWest = obj.optDouble("w").takeIf { it.isFinite() },
                        photoUri = obj.optString("photoUri", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRecentSearch(place: SearchPlaceResult) {
        try {
            val current = loadRecentSearches().filterNot {
                it.name.equals(place.name, ignoreCase = true) &&
                    kotlin.math.abs(it.latitude - place.latitude) < 0.0005 &&
                    kotlin.math.abs(it.longitude - place.longitude) < 0.0005
            }
            val updated = (listOf(place.copy(source = SearchResultSource.RECENT)) + current).take(MAX_RECENTS)
            val arr = JSONArray()
            updated.forEach { item ->
                arr.put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("name", item.name)
                        put("subtitle", item.subtitle)
                        put("lat", item.latitude)
                        put("lon", item.longitude)
                        item.bboxNorth?.let { put("n", it) }
                        item.bboxSouth?.let { put("s", it) }
                        item.bboxEast?.let { put("e", it) }
                        item.bboxWest?.let { put("w", it) }
                        if (item.photoUri.isNotBlank()) put("photoUri", item.photoUri)
                    }
                )
            }
            prefs.edit().putString(KEY_RECENTS, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun clearRecentSearches() {
        prefs.edit().remove(KEY_RECENTS).apply()
    }

    private fun httpGet(urlStr: String, timeoutMs: Int): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlStr)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            } else {
                Log.w(TAG, "HTTP ${conn.responseCode} for $urlStr")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search request failed: ${e.message}")
            null
        } finally {
            conn?.disconnect()
        }
    }
}
