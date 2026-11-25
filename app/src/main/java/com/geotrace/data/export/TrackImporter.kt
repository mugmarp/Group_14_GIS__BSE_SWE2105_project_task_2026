package com.geotrace.data.export

import com.geotrace.model.LocationPoint
import com.geotrace.model.TrailWaypoint
import com.geotrace.service.ElevationAnalyzer
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.text.SimpleDateFormat
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ImportedRoute(
    val id: String,
    val name: String,
    val sourceFormat: String,
    val points: List<LocationPoint>,
    val waypoints: List<TrailWaypoint>,
    val totalDistanceMeters: Double,
    val elevationGainMeters: Double,
    val elevationLossMeters: Double,
    val minAltitudeMeters: Double,
    val maxAltitudeMeters: Double
) {
    val formattedDistance: String
        get() = if (totalDistanceMeters >= 1000.0) {
            String.format(Locale.US, "%.2f km", totalDistanceMeters / 1000.0)
        } else {
            String.format(Locale.US, "%.0f m", totalDistanceMeters)
        }
}

object TrackImporter {

    const val MAX_IMPORT_BYTES = 12 * 1024 * 1024

    fun readBoundedUtf8(inputStream: java.io.InputStream, maxBytes: Int = MAX_IMPORT_BYTES): String {
        val buffer = ByteArray(16 * 1024)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = inputStream.read(buffer)
            if (read <= 0) break
            total += read
            if (total > maxBytes) {
                throw IllegalArgumentException("Route file exceeds ${maxBytes / (1024 * 1024)} MB size limit.")
            }
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    fun parse(
        rawContent: String,
        fallbackName: String = "Imported Route",
        hintExtension: String? = null
    ): Result<ImportedRoute> {
        if (rawContent.length > MAX_IMPORT_BYTES) {
            return Result.failure(IllegalArgumentException("Route file exceeds ${MAX_IMPORT_BYTES / (1024 * 1024)} MB size limit."))
        }
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("The selected file is empty."))
        }

        val ext = hintExtension?.lowercase(Locale.US)?.trimStart('.')
        return try {
            val route = when {
                ext == "geojson" || ext == "json" || trimmed.startsWith("{") -> {
                    parseGeoJson(trimmed, fallbackName)
                }
                ext == "kml" || trimmed.contains("<kml", ignoreCase = true) -> {
                    parseKml(trimmed, fallbackName)
                }
                ext == "gpx" || trimmed.contains("<gpx", ignoreCase = true) || trimmed.startsWith("<") -> {
                    parseGpx(trimmed, fallbackName)
                }
                else -> {
                    return Result.failure(
                        IllegalArgumentException("Unsupported route format. Select a .gpx, .kml, or .geojson file.")
                    )
                }
            }
            if (route.points.isEmpty() && route.waypoints.isEmpty()) {
                Result.failure(IllegalArgumentException("No valid coordinates found in the selected file."))
            } else {
                Result.success(route)
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException(e.message ?: "Failed to parse route file.")
            )
        }
    }

    fun buildRoute(
        id: String = UUID.randomUUID().toString(),
        name: String,
        sourceFormat: String,
        points: List<LocationPoint>,
        waypoints: List<TrailWaypoint> = emptyList()
    ): ImportedRoute {
        val validPoints = points.filter { isValidCoordinate(it.latitude, it.longitude) }
        val validWaypoints = waypoints.filter { isValidCoordinate(it.latitude, it.longitude) }
        val distanceMeters = computePolylineDistanceMeters(validPoints)
        val elevation = ElevationAnalyzer.analyze(validPoints)
        return ImportedRoute(
            id = id,
            name = name.trim().ifBlank { "Imported Route" },
            sourceFormat = sourceFormat,
            points = validPoints,
            waypoints = validWaypoints,
            totalDistanceMeters = distanceMeters,
            elevationGainMeters = elevation.totalAscentMeters,
            elevationLossMeters = elevation.totalDescentMeters,
            minAltitudeMeters = elevation.minAltitudeMeters,
            maxAltitudeMeters = elevation.maxAltitudeMeters
        )
    }

    private fun parseXmlDocument(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            try {
                setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            } catch (_: Exception) {
            }
            try {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            } catch (_: Exception) {
            }
            try {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            } catch (_: Exception) {
            }
            try {
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            } catch (_: Exception) {
            }
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
    }

    private fun childElements(parent: Element): List<Element> {
        val result = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && node is Element) {
                result.add(node)
            }
        }
        return result
    }

    private fun firstDirectChildText(parent: Element, vararg tagNames: String): String {
        val targets = tagNames.map { it.lowercase(Locale.US) }.toSet()
        for (child in childElements(parent)) {
            if (child.tagName.lowercase(Locale.US) in targets) {
                return child.textContent?.trim().orEmpty()
            }
        }
        return ""
    }

    private fun findElementsByTagLowercase(root: Element, targetTag: String): List<Element> {
        val out = mutableListOf<Element>()
        val target = targetTag.lowercase(Locale.US)
        fun walk(el: Element) {
            if (el.tagName.lowercase(Locale.US) == target) {
                out.add(el)
            }
            for (child in childElements(el)) {
                walk(child)
            }
        }
        walk(root)
        return out
    }

    private fun parseGpx(xml: String, fallbackName: String): ImportedRoute {
        val routeId = UUID.randomUUID().toString()
        val root = parseXmlDocument(xml).documentElement

        val metadataName = findElementsByTagLowercase(root, "metadata")
            .firstOrNull()
            ?.let { firstDirectChildText(it, "name") }
            ?.takeIf { it.isNotBlank() }

        val trackName = (findElementsByTagLowercase(root, "trk") + findElementsByTagLowercase(root, "rte"))
            .firstNotNullOfOrNull { firstDirectChildText(it, "name").takeIf { name -> name.isNotBlank() } }

        val trackPoints = mutableListOf<LocationPoint>()
        val routePoints = mutableListOf<LocationPoint>()
        val waypoints = mutableListOf<TrailWaypoint>()

        fun parsePointElement(el: Element): LocationPoint? {
            val lat = el.getAttribute("lat").toDoubleOrNull() ?: Double.NaN
            val lon = el.getAttribute("lon").toDoubleOrNull() ?: Double.NaN
            if (!isValidCoordinate(lat, lon)) return null
            val ele = firstDirectChildText(el, "ele").toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
            val timeMs = parseIsoTimestampMs(firstDirectChildText(el, "time")) ?: 0L
            return LocationPoint(
                latitude = lat,
                longitude = lon,
                accuracy = 5f,
                altitude = ele,
                provider = "gpx",
                timestamp = timeMs,
                sessionId = routeId
            )
        }

        for (el in findElementsByTagLowercase(root, "trkpt")) {
            parsePointElement(el)?.let { trackPoints.add(it) }
        }
        for (el in findElementsByTagLowercase(root, "rtept")) {
            parsePointElement(el)?.let { routePoints.add(it) }
        }
        for (el in findElementsByTagLowercase(root, "wpt")) {
            val lat = el.getAttribute("lat").toDoubleOrNull() ?: Double.NaN
            val lon = el.getAttribute("lon").toDoubleOrNull() ?: Double.NaN
            if (!isValidCoordinate(lat, lon)) continue
            val ele = firstDirectChildText(el, "ele").toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
            val timeMs = parseIsoTimestampMs(firstDirectChildText(el, "time")) ?: System.currentTimeMillis()
            val name = firstDirectChildText(el, "name")
            val desc = firstDirectChildText(el, "desc", "cmt")
            waypoints.add(
                TrailWaypoint(
                    id = UUID.randomUUID().toString(),
                    sessionId = routeId,
                    name = name.ifBlank { "Waypoint #${waypoints.size + 1}" },
                    note = desc,
                    latitude = lat,
                    longitude = lon,
                    altitude = ele,
                    accuracy = 5f,
                    timestamp = timeMs
                )
            )
        }

        val resolvedPoints = if (trackPoints.isNotEmpty()) trackPoints else routePoints
        val resolvedName = trackName ?: metadataName ?: fallbackName
        return buildRoute(
            id = routeId,
            name = resolvedName,
            sourceFormat = "GPX",
            points = resolvedPoints,
            waypoints = waypoints
        )
    }

    private fun parseKml(xml: String, fallbackName: String): ImportedRoute {
        val routeId = UUID.randomUUID().toString()
        val root = parseXmlDocument(xml).documentElement

        var docName = (findElementsByTagLowercase(root, "document") + findElementsByTagLowercase(root, "folder"))
            .firstNotNullOfOrNull { firstDirectChildText(it, "name").takeIf { n -> n.isNotBlank() } }

        val trackPoints = mutableListOf<LocationPoint>()
        val waypoints = mutableListOf<TrailWaypoint>()

        for (placemark in findElementsByTagLowercase(root, "placemark")) {
            val placemarkName = firstDirectChildText(placemark, "name")
            val placemarkDesc = firstDirectChildText(placemark, "description")

            val pointEls = findElementsByTagLowercase(placemark, "point")
            for (pointEl in pointEls) {
                val coordsText = findElementsByTagLowercase(pointEl, "coordinates")
                    .firstOrNull()?.textContent?.trim().orEmpty()
                val pt = parseKmlCoordinateTuples(coordsText, routeId).firstOrNull()
                if (pt != null) {
                    waypoints.add(
                        TrailWaypoint(
                            id = UUID.randomUUID().toString(),
                            sessionId = routeId,
                            name = placemarkName.ifBlank { "Waypoint #${waypoints.size + 1}" },
                            note = placemarkDesc,
                            latitude = pt.latitude,
                            longitude = pt.longitude,
                            altitude = pt.altitude,
                            accuracy = 5f
                        )
                    )
                }
            }

            val placemarkLinePoints = mutableListOf<LocationPoint>()
            val lineStrings = findElementsByTagLowercase(placemark, "linestring")
            for (ls in lineStrings) {
                for (coordEl in findElementsByTagLowercase(ls, "coordinates")) {
                    val parsed = parseKmlCoordinateTuples(coordEl.textContent?.trim().orEmpty(), routeId)
                    if (parsed.isNotEmpty()) {
                        placemarkLinePoints.addAll(parsed)
                    }
                }
            }

            val placemarkGxPoints = mutableListOf<LocationPoint>()
            val gxCoordEls = findElementsByTagLowercase(placemark, "gx:coord")
            for (gxEl in gxCoordEls) {
                val parts = gxEl.textContent?.trim().orEmpty().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val lon = parts[0].toDoubleOrNull() ?: Double.NaN
                    val lat = parts[1].toDoubleOrNull() ?: Double.NaN
                    val alt = parts.getOrNull(2)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
                    if (isValidCoordinate(lat, lon)) {
                        placemarkGxPoints.add(
                            LocationPoint(
                                latitude = lat,
                                longitude = lon,
                                accuracy = 5f,
                                altitude = alt,
                                provider = "kml",
                                timestamp = 0L,
                                sessionId = routeId
                            )
                        )
                    }
                }
            }

            if (placemarkLinePoints.isNotEmpty()) {
                trackPoints.addAll(placemarkLinePoints)
            } else if (placemarkGxPoints.isNotEmpty()) {
                trackPoints.addAll(placemarkGxPoints)
            }

            if ((placemarkLinePoints.isNotEmpty() || placemarkGxPoints.isNotEmpty()) &&
                docName.isNullOrBlank() && placemarkName.isNotBlank()
            ) {
                docName = placemarkName
            }
        }

        return buildRoute(
            id = routeId,
            name = docName?.takeIf { it.isNotBlank() } ?: fallbackName,
            sourceFormat = "KML",
            points = trackPoints,
            waypoints = waypoints
        )
    }

    private fun parseKmlCoordinateTuples(raw: String, routeId: String): List<LocationPoint> {
        if (raw.isBlank()) return emptyList()
        val out = mutableListOf<LocationPoint>()
        val tokens = raw.split(Regex("\\s+"))
        for (token in tokens) {
            val comps = token.trim().split(',')
            if (comps.size >= 2) {
                val lon = comps[0].toDoubleOrNull() ?: Double.NaN
                val lat = comps[1].toDoubleOrNull() ?: Double.NaN
                val alt = comps.getOrNull(2)?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
                if (isValidCoordinate(lat, lon)) {
                    out.add(
                        LocationPoint(
                            latitude = lat,
                            longitude = lon,
                            accuracy = 5f,
                            altitude = alt,
                            provider = "kml",
                            timestamp = 0L,
                            sessionId = routeId
                        )
                    )
                }
            }
        }
        return out
    }

    private fun parseGeoJson(rawJson: String, fallbackName: String): ImportedRoute {
        val routeId = UUID.randomUUID().toString()
        val root = JSONObject(rawJson)
        val type = root.optString("type")
        val points = mutableListOf<LocationPoint>()
        val waypoints = mutableListOf<TrailWaypoint>()
        var detectedName: String? = root.optString("name").takeIf { it.isNotBlank() }

        fun consumeGeometry(geom: JSONObject?, props: JSONObject?) {
            if (geom == null) return
            val geomType = geom.optString("type")
            val coords = geom.optJSONArray("coordinates") ?: return
            val propName = props?.optString("name")?.takeIf { it.isNotBlank() }
            if (detectedName == null && propName != null && geomType != "Point") {
                detectedName = propName
            }

            when (geomType) {
                "LineString" -> {
                    extractCoordinateArray(coords, routeId, points)
                }
                "MultiLineString" -> {
                    for (i in 0 until coords.length()) {
                        val line = coords.optJSONArray(i) ?: continue
                        extractCoordinateArray(line, routeId, points)
                    }
                }
                "Point" -> {
                    val lon = coords.optDouble(0, Double.NaN)
                    val lat = coords.optDouble(1, Double.NaN)
                    val alt = coords.optDouble(2, 0.0).takeIf { it.isFinite() } ?: 0.0
                    if (isValidCoordinate(lat, lon)) {
                        val featureType = props?.optString("featureType") ?: ""
                        if (featureType == "track_point") {
                            points.add(
                                LocationPoint(
                                    latitude = lat,
                                    longitude = lon,
                                    accuracy = 5f,
                                    altitude = alt,
                                    provider = "geojson",
                                    timestamp = 0L,
                                    sessionId = routeId
                                )
                            )
                        } else {
                            waypoints.add(
                                TrailWaypoint(
                                    id = UUID.randomUUID().toString(),
                                    sessionId = routeId,
                                    name = propName ?: "Waypoint #${waypoints.size + 1}",
                                    note = props?.optString("note") ?: "",
                                    latitude = lat,
                                    longitude = lon,
                                    altitude = alt,
                                    accuracy = 5f
                                )
                            )
                        }
                    }
                }
            }
        }

        when (type) {
            "FeatureCollection" -> {
                val features = root.optJSONArray("features") ?: JSONArray()
                for (i in 0 until features.length()) {
                    val feature = features.optJSONObject(i) ?: continue
                    consumeGeometry(feature.optJSONObject("geometry"), feature.optJSONObject("properties"))
                }
            }
            "Feature" -> {
                consumeGeometry(root.optJSONObject("geometry"), root.optJSONObject("properties"))
            }
            "LineString", "MultiLineString", "Point" -> {
                consumeGeometry(root, null)
            }
        }

        return buildRoute(
            id = routeId,
            name = detectedName ?: fallbackName,
            sourceFormat = "GeoJSON",
            points = points,
            waypoints = waypoints
        )
    }

    private fun extractCoordinateArray(coords: JSONArray, routeId: String, out: MutableList<LocationPoint>) {
        for (i in 0 until coords.length()) {
            val pair = coords.optJSONArray(i) ?: continue
            val lon = pair.optDouble(0, Double.NaN)
            val lat = pair.optDouble(1, Double.NaN)
            val alt = pair.optDouble(2, 0.0).takeIf { it.isFinite() } ?: 0.0
            if (isValidCoordinate(lat, lon)) {
                out.add(
                    LocationPoint(
                        latitude = lat,
                        longitude = lon,
                        accuracy = 5f,
                        altitude = alt,
                        provider = "geojson",
                        timestamp = 0L,
                        sessionId = routeId
                    )
                )
            }
        }
    }


    private fun parseIsoTimestampMs(raw: String): Long? {
        if (raw.isBlank()) return null
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val parsed = sdf.parse(raw)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0 &&
            (lat != 0.0 || lon != 0.0)
    }

    fun computePolylineDistanceMeters(points: List<LocationPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val step = haversineMeters(prev.latitude, prev.longitude, cur.latitude, cur.longitude)
            if (step <= 5000.0) {
                total += step
            }
        }
        return total
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return r * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }
}
