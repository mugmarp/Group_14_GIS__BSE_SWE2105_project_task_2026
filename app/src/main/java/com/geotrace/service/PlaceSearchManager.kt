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

            // 2. Check geo: URI or URL query/at-coordinates (?q=lat,lon or @lat,lon)
            val urlOrGeoRegex = Regex("""(?:q=|query=|loc:|geo:|@)(-?\d{1,3}(?:\.\d+)?)\s*,\s*(-?\d{1,3}(?:\.\d+)?)""")
            urlOrGeoRegex.find(text)?.let { m ->
                val lat = m.groupValues[1].toDoubleOrNull()
                val lon = m.groupValues[2].toDoubleOrNull()
                if (lat != null && lon != null && isValidLatLon(lat, lon)) {
                    return buildCoordinateResult(lat, lon, "Map Link / Geo URI")
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

        /**
         * Parses an Overpass API JSON response string into tourism/survival-prioritized [OfflinePlacePoi] records.
         * Extracted cleanly for offline unit testing without network calls.
         */
        fun parseOverpassElementsJson(
            responseText: String,
            regionId: String,
            regionName: String,
            seenDedup: MutableSet<String> = HashSet(),
            maxResults: Int = 250
        ): List<OfflinePlacePoi> {
            val elements = JSONObject(responseText).optJSONArray("elements") ?: return emptyList()
            val scored = mutableListOf<Pair<Int, OfflinePlacePoi>>()

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
                if (!seenDedup.add(dedup)) continue

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
                scored.add(priority to poi)
            }

            return scored
                .sortedWith(compareByDescending<Pair<Int, OfflinePlacePoi>> { it.first }.thenBy { it.second.name })
                .take(maxResults)
                .map { it.second }
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

    /**
     * Fetches tourism-prioritized POIs (lodging, camps, water, fuel, medical, viewpoints, transit)
     * plus named landmarks inside an offline region's bounding box from OpenStreetMap Overpass API.
     */
    suspend fun fetchRegionPoisForOfflineCache(region: OfflineMapRegion): List<OfflinePlacePoi> = withContext(Dispatchers.IO) {
        val s = region.southLat.coerceIn(-85.0, 85.0)
        val w = region.westLng.coerceIn(-180.0, 180.0)
        val n = region.northLat.coerceIn(-85.0, 85.0)
        val e = region.eastLng.coerceIn(-180.0, 180.0)
        if (n <= s || e <= w) return@withContext emptyList()

        // Guard against huge continental bounding boxes timing out Overpass
        val latSpan = n - s
        val lonSpan = e - w
        val queryBox = if (latSpan > 0.6 || lonSpan > 0.6) {
            val cLat = (n + s) / 2.0
            val cLon = (e + w) / 2.0
            val half = 0.25
            Triple(cLat - half, cLon - half, cLat + half) to (cLon + half)
        } else {
            Triple(s, w, n) to e
        }
        val (swn, eastClamp) = queryBox
        return@withContext executeOverpassQueryForBox(
            south = swn.first,
            west = swn.second,
            north = swn.third,
            east = eastClamp,
            regionId = region.id,
            regionName = region.name,
            seenDedup = HashSet(),
            maxResults = 250
        )
    }

    /**
     * Fetches tourism and survival POIs along an expedition/trail corridor strip (e.g. ±1.5 km buffer),
     * chunked across sampled corridor segments so long trails remain fast and within Overpass rate limits.
     */
    suspend fun fetchCorridorPoisForOfflineCache(
        points: List<LocationPoint>,
        regionId: String,
        regionName: String,
        halfWidthKm: Double = 1.5
    ): List<OfflinePlacePoi> = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext emptyList()
        val rawBoxes = OfflineMapManager.computeCorridorBoxes(
            points = points,
            halfWidthKm = halfWidthKm,
            spacingMeters = 2500.0
        )
        if (rawBoxes.isEmpty()) return@withContext emptyList()

        // Merge down to at most 4 evenly distributed corridor windows so Overpass stays responsive
        val stride = ((rawBoxes.size + 3) / 4).coerceAtLeast(1)
        val mergedWindows = rawBoxes.chunked(stride).map { chunk ->
            OfflineMapManager.unionBoundingBox(chunk)!!
        }

        val combined = mutableListOf<OfflinePlacePoi>()
        val seenDedup = HashSet<String>()
        for (window in mergedWindows) {
            if (combined.size >= 300) break
            val batch = executeOverpassQueryForBox(
                south = window.south,
                west = window.west,
                north = window.north,
                east = window.east,
                regionId = regionId,
                regionName = regionName,
                seenDedup = seenDedup,
                maxResults = 150
            )
            combined.addAll(batch)
        }
        combined.take(300)
    }

    private fun executeOverpassQueryForBox(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
        regionId: String,
        regionName: String,
        seenDedup: MutableSet<String>,
        maxResults: Int
    ): List<OfflinePlacePoi> {
        val bboxStr = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", south, west, north, east)
        val overpassQuery = """
            [out:json][timeout:12];
            (
              nwr["tourism"~"camp_site|alpine_hut|wilderness_hut|hotel|guest_house|hostel|chalet|motel|viewpoint|attraction|information|museum|picnic_site"]($bboxStr);
              nwr["amenity"~"drinking_water|water_point|fuel|hospital|clinic|pharmacy|doctors|police|shelter|restaurant|cafe|atm|bank|toilets|bus_station|parking|ranger_station"]($bboxStr);
              node["natural"~"spring|peak|waterfall"]($bboxStr);
              node["railway"="station"]($bboxStr);
              node["aeroway"="aerodrome"]($bboxStr);
              node["name"]($bboxStr);
            );
            out center 250;
        """.trimIndent()

        var conn: HttpURLConnection? = null
        return try {
            val url = URL("https://overpass-api.de/api/interpreter")
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
            if (conn.responseCode !in 200..299) {
                Log.w(TAG, "Overpass POI pre-cache HTTP ${conn.responseCode}")
                return emptyList()
            }
            val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            parseOverpassElementsJson(
                responseText = responseText,
                regionId = regionId,
                regionName = regionName,
                seenDedup = seenDedup,
                maxResults = maxResults
            )
        } catch (e: Exception) {
            Log.w(TAG, "Overpass POI pre-cache skipped: ${e.message}")
            emptyList()
        } finally {
            conn?.disconnect()
        }
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
                        bboxWest = obj.optDouble("w").takeIf { it.isFinite() }
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
