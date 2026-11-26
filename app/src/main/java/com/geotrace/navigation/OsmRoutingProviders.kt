package com.geotrace.navigation

import android.content.Context
import com.geotrace.BuildConfig
import com.geotrace.model.LocationPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

interface RoutingProvider {
    suspend fun computeRoutes(
        originLat: Double,
        originLon: Double,
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        profile: NavProfile,
        includeAlternatives: Boolean = true
    ): Result<List<NavRoute>>
}

object PolylineCodec {
    /**
     * Decodes a Google/Valhalla encoded polyline with configurable decimal precision (1e6 for Valhalla, 1e5 for Google/OSRM).
     */
    fun decode(encoded: String, precision: Int = 6): List<Pair<Double, Double>> {
        if (encoded.isEmpty()) return emptyList()
        val factor = Math.pow(10.0, precision.toDouble())
        val coordinates = ArrayList<Pair<Double, Double>>(encoded.length / 4)
        var index = 0
        var lat = 0
        var lon = 0
        val len = encoded.length

        while (index < len) {
            var shift = 0
            var result = 0
            var b: Int
            do {
                if (index >= len) return coordinates
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lat += dLat

            shift = 0
            result = 0
            do {
                if (index >= len) return coordinates
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLon = if ((result and 1) != 0) (result shr 1).inv() else (result shr 1)
            lon += dLon

            coordinates.add((lat / factor) to (lon / factor))
        }
        return coordinates
    }
}

object RoutingJsonParsers {

    fun parseOpenRouteServiceGeoJson(
        rawJson: String,
        originLat: Double,
        originLon: Double,
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        profile: NavProfile
    ): List<NavRoute> {
        val root = JSONObject(rawJson)
        val features = root.optJSONArray("features") ?: return emptyList()
        val nowMs = System.currentTimeMillis()
        val routes = ArrayList<NavRoute>(features.length())

        for (fIdx in 0 until features.length()) {
            val feature = features.optJSONObject(fIdx) ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue
            val coords = geometry.optJSONArray("coordinates") ?: continue
            val routeId = "ors_${nowMs}_${fIdx}_${UUID.randomUUID().toString().take(6)}"

            val points = ArrayList<LocationPoint>(coords.length())
            for (i in 0 until coords.length()) {
                val tuple = coords.optJSONArray(i) ?: continue
                if (tuple.length() < 2) continue
                val lon = tuple.optDouble(0, Double.NaN)
                val lat = tuple.optDouble(1, Double.NaN)
                val alt = if (tuple.length() >= 3) tuple.optDouble(2, 0.0).takeIf { it.isFinite() } ?: 0.0 else 0.0
                if (lat.isFinite() && lon.isFinite()) {
                    points.add(
                        LocationPoint(
                            latitude = lat,
                            longitude = lon,
                            accuracy = 5f,
                            altitude = alt,
                            provider = "route",
                            timestamp = nowMs,
                            sessionId = routeId
                        )
                    )
                }
            }
            if (points.size < 2) continue

            val props = feature.optJSONObject("properties")
            val summary = props?.optJSONObject("summary")
            val totalDist = summary?.optDouble("distance", 0.0)?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            val totalDur = summary?.optDouble("duration", 0.0)?.takeIf { it.isFinite() && it > 0.0 } ?: 0.0
            val segments = props?.optJSONArray("segments")
            val steps = mutableListOf<NavStep>()

            if (segments != null) {
                var runningStepIdx = 0
                for (sIdx in 0 until segments.length()) {
                    val seg = segments.optJSONObject(sIdx) ?: continue
                    val rawSteps = seg.optJSONArray("steps") ?: continue
                    for (j in 0 until rawSteps.length()) {
                        val st = rawSteps.optJSONObject(j) ?: continue
                        val typeCode = st.optInt("type", 6)
                        val maneuver = mapOrsType(typeCode)
                        val instruction = st.optString("instruction", "").trim()
                            .ifBlank { defaultInstructionFor(maneuver, st.optString("name", "")) }
                        val streetName = st.optString("name", "").trim().let {
                            if (it == "-" || it.equals("unknown", ignoreCase = true)) "" else it
                        }
                        val distM = st.optDouble("distance", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                        val durS = st.optDouble("duration", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                        val wayPts = st.optJSONArray("way_points")
                        val startIdx = (wayPts?.optInt(0, 0) ?: 0).coerceIn(0, points.lastIndex)
                        val endIdx = (wayPts?.optInt(1, startIdx) ?: startIdx).coerceIn(startIdx, points.lastIndex)
                        val exitNum = if (st.has("exit_number") && !st.isNull("exit_number")) {
                            st.optInt("exit_number", 0).takeIf { it > 0 }
                        } else {
                            null
                        }
                        steps.add(
                            NavStep(
                                index = runningStepIdx++,
                                maneuverType = maneuver,
                                instruction = instruction,
                                verbalPrompt = instruction,
                                streetName = streetName,
                                distanceMeters = distM,
                                durationSeconds = durS,
                                beginPointIndex = startIdx,
                                endPointIndex = endIdx,
                                roundaboutExit = exitNum
                            )
                        )
                    }
                }
            }

            routes.add(
                NavRoute(
                    id = routeId,
                    profile = profile,
                    originLat = originLat,
                    originLon = originLon,
                    destinationName = destinationName,
                    destinationLat = destinationLat,
                    destinationLon = destinationLon,
                    points = points,
                    steps = ensureArriveStep(steps, points.lastIndex, destinationName),
                    totalDistanceMeters = if (totalDist > 0.0) totalDist else steps.sumOf { it.distanceMeters },
                    totalDurationSeconds = if (totalDur > 0.0) totalDur else steps.sumOf { it.durationSeconds },
                    source = NavRouteSource.ONLINE_ORS,
                    createdAtMs = nowMs
                )
            )
        }
        return routes
    }

    fun parseValhallaResponse(
        rawJson: String,
        originLat: Double,
        originLon: Double,
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        profile: NavProfile
    ): List<NavRoute> {
        val root = JSONObject(rawJson)
        val nowMs = System.currentTimeMillis()
        val out = mutableListOf<NavRoute>()

        root.optJSONObject("trip")?.let { tripObj ->
            parseValhallaTrip(
                trip = tripObj,
                originLat = originLat,
                originLon = originLon,
                destinationName = destinationName,
                destinationLat = destinationLat,
                destinationLon = destinationLon,
                profile = profile,
                nowMs = nowMs,
                suffix = "0"
            )?.let { out.add(it) }
        }

        val alternates = root.optJSONArray("alternates")
        if (alternates != null) {
            for (i in 0 until alternates.length()) {
                val altTrip = alternates.optJSONObject(i)?.optJSONObject("trip") ?: continue
                parseValhallaTrip(
                    trip = altTrip,
                    originLat = originLat,
                    originLon = originLon,
                    destinationName = destinationName,
                    destinationLat = destinationLat,
                    destinationLon = destinationLon,
                    profile = profile,
                    nowMs = nowMs,
                    suffix = "alt_${i + 1}"
                )?.let { out.add(it) }
            }
        }
        return out
    }

    private fun parseValhallaTrip(
        trip: JSONObject,
        originLat: Double,
        originLon: Double,
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        profile: NavProfile,
        nowMs: Long,
        suffix: String
    ): NavRoute? {
        val legs = trip.optJSONArray("legs") ?: return null
        val leg = legs.optJSONObject(0) ?: return null
        val shape = leg.optString("shape", "")
        val coords = PolylineCodec.decode(shape, precision = 6)
        if (coords.size < 2) return null

        val routeId = "valhalla_${nowMs}_${suffix}_${UUID.randomUUID().toString().take(6)}"
        val points = coords.map { (lat, lon) ->
            LocationPoint(
                latitude = lat,
                longitude = lon,
                accuracy = 5f,
                provider = "route",
                timestamp = nowMs,
                sessionId = routeId
            )
        }

        val tripSummary = trip.optJSONObject("summary") ?: leg.optJSONObject("summary")
        // Valhalla directions_options units="kilometers" returns length in km
        val totalDistM = (tripSummary?.optDouble("length", 0.0) ?: 0.0) * 1000.0
        val totalDurS = tripSummary?.optDouble("time", 0.0) ?: 0.0

        val maneuversJson = leg.optJSONArray("maneuvers")
        val steps = mutableListOf<NavStep>()
        if (maneuversJson != null) {
            for (i in 0 until maneuversJson.length()) {
                val m = maneuversJson.optJSONObject(i) ?: continue
                val typeCode = m.optInt("type", 8)
                val maneuver = mapValhallaType(typeCode)
                val streetNames = m.optJSONArray("street_names")
                val streetName = streetNames?.optString(0, "")?.trim().orEmpty()
                val instruction = m.optString("instruction", "").trim()
                    .ifBlank { defaultInstructionFor(maneuver, streetName) }
                val verbalPre = m.optString("verbal_pre_transition_instruction", "").trim()
                    .ifBlank { instruction }
                val distM = (m.optDouble("length", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0) * 1000.0
                val durS = m.optDouble("time", 0.0).takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
                val beginIdx = m.optInt("begin_shape_index", 0).coerceIn(0, points.lastIndex)
                val endIdx = m.optInt("end_shape_index", beginIdx).coerceIn(beginIdx, points.lastIndex)
                val exitCount = if (m.has("roundabout_exit_count") && !m.isNull("roundabout_exit_count")) {
                    m.optInt("roundabout_exit_count", 0).takeIf { it > 0 }
                } else {
                    null
                }
                steps.add(
                    NavStep(
                        index = i,
                        maneuverType = maneuver,
                        instruction = instruction,
                        verbalPrompt = verbalPre,
                        streetName = streetName,
                        distanceMeters = distM,
                        durationSeconds = durS,
                        beginPointIndex = beginIdx,
                        endPointIndex = endIdx,
                        roundaboutExit = exitCount
                    )
                )
            }
        }

        return NavRoute(
            id = routeId,
            profile = profile,
            originLat = originLat,
            originLon = originLon,
            destinationName = destinationName,
            destinationLat = destinationLat,
            destinationLon = destinationLon,
            points = points,
            steps = ensureArriveStep(steps, points.lastIndex, destinationName),
            totalDistanceMeters = if (totalDistM > 0.0) totalDistM else steps.sumOf { it.distanceMeters },
            totalDurationSeconds = if (totalDurS > 0.0) totalDurS else steps.sumOf { it.durationSeconds },
            source = NavRouteSource.ONLINE_VALHALLA,
            createdAtMs = nowMs
        )
    }

    private fun ensureArriveStep(
        rawSteps: List<NavStep>,
        lastPointIndex: Int,
        destinationName: String
    ): List<NavStep> {
        if (rawSteps.isEmpty()) {
            return listOf(
                NavStep(
                    index = 0,
                    maneuverType = NavManeuverType.ARRIVE,
                    instruction = "Arrive at $destinationName",
                    verbalPrompt = "You have arrived at $destinationName",
                    streetName = destinationName,
                    distanceMeters = 0.0,
                    durationSeconds = 0.0,
                    beginPointIndex = 0,
                    endPointIndex = lastPointIndex
                )
            )
        }
        val last = rawSteps.last()
        return if (last.maneuverType == NavManeuverType.ARRIVE) {
            rawSteps
        } else {
            rawSteps + NavStep(
                index = rawSteps.size,
                maneuverType = NavManeuverType.ARRIVE,
                instruction = "Arrive at $destinationName",
                verbalPrompt = "You have arrived at $destinationName",
                streetName = destinationName,
                distanceMeters = 0.0,
                durationSeconds = 0.0,
                beginPointIndex = last.endPointIndex,
                endPointIndex = lastPointIndex
            )
        }
    }

    private fun mapOrsType(type: Int): NavManeuverType = when (type) {
        0 -> NavManeuverType.LEFT
        1 -> NavManeuverType.RIGHT
        2 -> NavManeuverType.SHARP_LEFT
        3 -> NavManeuverType.SHARP_RIGHT
        4 -> NavManeuverType.SLIGHT_LEFT
        5 -> NavManeuverType.SLIGHT_RIGHT
        6, 12 -> NavManeuverType.STRAIGHT
        7 -> NavManeuverType.ROUNDABOUT_ENTER
        8 -> NavManeuverType.ROUNDABOUT_EXIT
        9 -> NavManeuverType.UTURN
        10 -> NavManeuverType.ARRIVE
        11 -> NavManeuverType.START
        13 -> NavManeuverType.MERGE_KEEP
        else -> NavManeuverType.STRAIGHT
    }

    private fun mapValhallaType(type: Int): NavManeuverType = when (type) {
        1, 2, 3 -> NavManeuverType.START
        4, 5, 6 -> NavManeuverType.ARRIVE
        7, 8, 17, 22 -> NavManeuverType.STRAIGHT
        9, 23 -> NavManeuverType.SLIGHT_RIGHT
        10, 18, 20 -> NavManeuverType.RIGHT
        11 -> NavManeuverType.SHARP_RIGHT
        12, 13 -> NavManeuverType.UTURN
        14 -> NavManeuverType.SHARP_LEFT
        15, 19, 21 -> NavManeuverType.LEFT
        16, 24 -> NavManeuverType.SLIGHT_LEFT
        25 -> NavManeuverType.MERGE_KEEP
        26 -> NavManeuverType.ROUNDABOUT_ENTER
        27 -> NavManeuverType.ROUNDABOUT_EXIT
        else -> NavManeuverType.STRAIGHT
    }

    private fun defaultInstructionFor(type: NavManeuverType, street: String): String {
        val onto = if (street.isNotBlank()) " onto $street" else ""
        return when (type) {
            NavManeuverType.START -> "Proceed$onto"
            NavManeuverType.STRAIGHT -> "Continue straight$onto"
            NavManeuverType.SLIGHT_LEFT -> "Keep slight left$onto"
            NavManeuverType.LEFT -> "Turn left$onto"
            NavManeuverType.SHARP_LEFT -> "Turn sharp left$onto"
            NavManeuverType.SLIGHT_RIGHT -> "Keep slight right$onto"
            NavManeuverType.RIGHT -> "Turn right$onto"
            NavManeuverType.SHARP_RIGHT -> "Turn sharp right$onto"
            NavManeuverType.UTURN -> "Make a U-turn$onto"
            NavManeuverType.ROUNDABOUT_ENTER -> "Enter roundabout$onto"
            NavManeuverType.ROUNDABOUT_EXIT -> "Exit roundabout$onto"
            NavManeuverType.MERGE_KEEP -> "Merge$onto"
            NavManeuverType.ARRIVE -> "Arrive at destination"
        }
    }
}

class OpenRouteServiceProvider(
    private val apiKeyProvider: () -> String = { BuildConfig.ORS_API_KEY }
) : RoutingProvider {

    companion object {
        private const val BASE_URL = "https://api.openrouteservice.org/v2/directions"
        private const val USER_AGENT = "GeoTrace-GIS-FieldApp/${BuildConfig.VERSION_NAME} (Android)"
    }

    override suspend fun computeRoutes(
        originLat: Double,
        originLon: Double,
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        profile: NavProfile,
        includeAlternatives: Boolean
    ): Result<List<NavRoute>> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(IllegalStateException("ORS_API_KEY is not configured"))
        }

        val firstAttempt = executeOrsRequest(
            apiKey = apiKey,
            originLat = originLat,
            originLon = originLon,
            destinationName = destinationName,
            destinationLat = destinationLat,
            destinationLon = destinationLon,
            profile = profile,
            withAlternatives = includeAlternatives
        )
        if (firstAttempt.isSuccess) return@withContext firstAttempt

        // ORS rejects alternative_routes on very long distances (>100 km); retry cleanly without alternatives
        if (includeAlternatives) {
            return@withContext executeOrsRequest(
                apiKey = apiKey,
                originLat = originLat,
                originLon = originLon,
                destinationName = destinationName,
                destinationLat = destinationLat,
                destinationLon = destinationLon,
                profile = profile,
                withAlternatives = false
            )
        }
        firstAttempt
    }

    private fun executeOrsRequest(
        apiKey: String,
        originLat: Double,
        originLon: Double,
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        profile: NavProfile,
        withAlternatives: Boolean
    ): Result<List<NavRoute>> {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL("$BASE_URL/${profile.orsProfile}/geojson")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Authorization", apiKey)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json, application/geo+json")
                setRequestProperty("User-Agent", USER_AGENT)
            }

            val payload = JSONObject().apply {
                put(
                    "coordinates",
                    JSONArray().apply {
                        put(JSONArray().put(originLon).put(originLat))
                        put(JSONArray().put(destinationLon).put(destinationLat))
                    }
                )
                put("instructions", true)
                put("instructions_format", "text")
                put("maneuvers", true)
                put("units", "m")
                if (withAlternatives) {
                    put(
                        "alternative_routes",
                        JSONObject().apply {
                            put("target_count", 2)
                            put("weight_factor", 1.4)
                            put("share_factor", 0.6)
                        }
                    )
                }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } }.orEmpty()
            if (code !in 200..299) {
                return Result.failure(IllegalStateException("ORS HTTP $code: ${body.take(180)}"))
            }

            val parsed = RoutingJsonParsers.parseOpenRouteServiceGeoJson(
                rawJson = body,
                originLat = originLat,
                originLon = originLon,
                destinationName = destinationName,
                destinationLat = destinationLat,
                destinationLon = destinationLon,
                profile = profile
            )
            if (parsed.isEmpty()) {
                Result.failure(IllegalStateException("No road route found between origin and destination"))
            } else {
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }
}

class ValhallaPublicProvider(context: Context) : RoutingProvider {

    companion object {
        private const val ENDPOINT = "https://valhalla1.openstreetmap.de/route"
        private const val PREFS_NAME = "geotrace_nav_prefs"
        private const val KEY_CLIENT_ID = "valhalla_client_id"
        private const val MIN_REQUEST_INTERVAL_MS = 1250L
    }

    private val clientId: String = run {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.getString(KEY_CLIENT_ID, null)?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_CLIENT_ID, it).apply()
        }
    }
    private val lastCallMs = AtomicLong(0L)

    override suspend fun computeRoutes(
        originLat: Double,
        originLon: Double,
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        profile: NavProfile,
        includeAlternatives: Boolean
    ): Result<List<NavRoute>> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val elapsed = now - lastCallMs.get()
        if (elapsed in 0 until MIN_REQUEST_INTERVAL_MS) {
            kotlinx.coroutines.delay(MIN_REQUEST_INTERVAL_MS - elapsed)
        }
        lastCallMs.set(System.currentTimeMillis())

        var conn: HttpURLConnection? = null
        try {
            val url = URL(ENDPOINT)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 16_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "GeoTrace-GIS-FieldApp/${BuildConfig.VERSION_NAME} (Android)")
                setRequestProperty("X-Client-Id", "geotrace-$clientId")
            }

            val payload = JSONObject().apply {
                put(
                    "locations",
                    JSONArray().apply {
                        put(JSONObject().put("lat", originLat).put("lon", originLon))
                        put(JSONObject().put("lat", destinationLat).put("lon", destinationLon))
                    }
                )
                put("costing", profile.valhallaCosting)
                put(
                    "directions_options",
                    JSONObject().apply {
                        put("units", "kilometers")
                        put("language", "en-US")
                    }
                )
                if (includeAlternatives) {
                    put("alternates", 1)
                }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.let { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() } }.orEmpty()
            if (code == 429) {
                return@withContext Result.failure(IllegalStateException("Public routing server busy (rate limited). Try again in a few seconds."))
            }
            if (code !in 200..299) {
                return@withContext Result.failure(IllegalStateException("Valhalla HTTP $code: ${body.take(180)}"))
            }

            val parsed = RoutingJsonParsers.parseValhallaResponse(
                rawJson = body,
                originLat = originLat,
                originLon = originLon,
                destinationName = destinationName,
                destinationLat = destinationLat,
                destinationLon = destinationLon,
                profile = profile
            )
            if (parsed.isEmpty()) {
                Result.failure(IllegalStateException("No routable road path found near those coordinates"))
            } else {
                Result.success(parsed)
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }
}

class HybridPhase1RoutingProvider(
    context: Context,
    private val orsKeyProvider: () -> String = { BuildConfig.ORS_API_KEY }
) : RoutingProvider {

    private val orsProvider = OpenRouteServiceProvider(orsKeyProvider)
    private val valhallaProvider = ValhallaPublicProvider(context.applicationContext)

    override suspend fun computeRoutes(
        originLat: Double,
        originLon: Double,
        destinationName: String,
        destinationLat: Double,
        destinationLon: Double,
        profile: NavProfile,
        includeAlternatives: Boolean
    ): Result<List<NavRoute>> {
        val hasOrsKey = orsKeyProvider().trim().isNotBlank()
        if (hasOrsKey) {
            val orsResult = orsProvider.computeRoutes(
                originLat = originLat,
                originLon = originLon,
                destinationName = destinationName,
                destinationLat = destinationLat,
                destinationLon = destinationLon,
                profile = profile,
                includeAlternatives = includeAlternatives
            )
            if (orsResult.isSuccess) return orsResult
        }
        return valhallaProvider.computeRoutes(
            originLat = originLat,
            originLon = originLon,
            destinationName = destinationName,
            destinationLat = destinationLat,
            destinationLon = destinationLon,
            profile = profile,
            includeAlternatives = includeAlternatives
        )
    }
}
