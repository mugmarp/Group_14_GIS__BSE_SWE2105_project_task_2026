package com.geotrace.data.export

import com.geotrace.model.CellTowerInfo
import com.geotrace.model.LocationPoint
import com.geotrace.model.TrackingSession
import com.geotrace.model.TrailWaypoint
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class ExportFormat(
    val shortCode: String,
    val displayName: String,
    val extension: String,
    val mimeType: String
) {
    GPX("GPX", "GPX (GPS Exchange Format)", "gpx", "application/gpx+xml"),
    KML("KML", "KML (Google Earth)", "kml", "application/vnd.google-earth.kml+xml"),
    CSV("CSV", "CSV (Raw Telemetry)", "csv", "text/csv"),
    GEOJSON("GeoJSON", "GeoJSON (GIS Vector)", "geojson", "application/geo+json")
}

object TrackExporter {

    private val isoFormatThreadLocal = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat {
            return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
    }

    private fun formatIsoUtc(timestampMs: Long): String {
        val safeTs = timestampMs.takeIf { it > 0L } ?: System.currentTimeMillis()
        return isoFormatThreadLocal.get()?.format(Date(safeTs)) ?: ""
    }

    private fun Double.finiteOr(fallback: Double = 0.0): Double = if (isFinite()) this else fallback

    private fun Float.finiteOr(fallback: Float = 0f): Float = if (isFinite()) this else fallback

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return lat.isFinite() && lon.isFinite() &&
            lat in -90.0..90.0 && lon in -180.0..180.0 &&
            (lat != 0.0 || lon != 0.0)
    }

    fun buildSafeFileName(sessionName: String, format: ExportFormat, nowMs: Long = System.currentTimeMillis()): String {
        val sanitizedName = sessionName
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_', '.')
            .take(40)
            .ifBlank { "geotrace_trail" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(nowMs))
        return "${sanitizedName}_${stamp}.${format.extension}"
    }

    fun cleanupOldExportCache(exportDir: File, maxAgeMs: Long = 24L * 60L * 60L * 1000L) {
        if (!exportDir.exists() || !exportDir.isDirectory) return
        val cutoff = System.currentTimeMillis() - maxAgeMs
        exportDir.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }

    fun export(
        session: TrackingSession,
        points: List<LocationPoint>,
        waypoints: List<TrailWaypoint>,
        format: ExportFormat
    ): String {
        val validPoints = points.filter { isValidCoordinate(it.latitude, it.longitude) }
        val validWaypoints = waypoints.filter { isValidCoordinate(it.latitude, it.longitude) }
        return when (format) {
            ExportFormat.GPX -> toGpx(session, validPoints, validWaypoints)
            ExportFormat.KML -> toKml(session, validPoints, validWaypoints)
            ExportFormat.CSV -> toCsv(session, validPoints, validWaypoints)
            ExportFormat.GEOJSON -> toGeoJson(session, validPoints, validWaypoints)
        }
    }

    fun toGpx(
        session: TrackingSession,
        points: List<LocationPoint>,
        waypoints: List<TrailWaypoint> = emptyList()
    ): String {
        val validPoints = points.filter { isValidCoordinate(it.latitude, it.longitude) }
        val validWaypoints = waypoints.filter { isValidCoordinate(it.latitude, it.longitude) }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append(
            """<gpx version="1.1" creator="GeoTrace GIS" """ +
                """xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:geotrace="https://geotrace.local/xmlschemas/Telemetry/v1">"""
        ).append('\n')
        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(session.name)}</name>\n")
        sb.append(
            "    <desc>${escapeXml("Distance: ${session.formattedDistance} | Duration: ${session.formattedDuration} | Mode: ${session.trackingMode.name}")}</desc>\n"
        )
        sb.append("    <time>${formatIsoUtc(session.startTime)}</time>\n")
        session.tripId?.takeIf { it.isNotBlank() }?.let { tripId ->
            sb.append("    <extensions>\n")
            sb.append("      <geotrace:trip_id>${escapeXml(tripId)}</geotrace:trip_id>\n")
            sb.append("    </extensions>\n")
        }
        sb.append("  </metadata>\n")

        for (w in validWaypoints) {
            val photoFileName = sanitizePhotoFileName(w.photoUri)
            sb.append("""  <wpt lat="${w.latitude}" lon="${w.longitude}">""").append('\n')
            sb.append("    <ele>${w.altitude.finiteOr()}</ele>\n")
            sb.append("    <time>${formatIsoUtc(w.timestamp)}</time>\n")
            sb.append("    <name>${escapeXml(w.name)}</name>\n")
            if (w.note.isNotBlank()) {
                sb.append("    <desc>${escapeXml(w.note)}</desc>\n")
            }
            sb.append("    <sym>Flag</sym>\n")
            sb.append("    <extensions>\n")
            sb.append("      <geotrace:accuracy>${w.accuracy.finiteOr()}</geotrace:accuracy>\n")
            if (photoFileName != null) {
                sb.append("      <geotrace:photo>${escapeXml(photoFileName)}</geotrace:photo>\n")
            }
            sb.append("    </extensions>\n")
            sb.append("  </wpt>\n")
        }

        if (validPoints.isNotEmpty()) {
            sb.append("  <trk>\n")
            sb.append("    <name>${escapeXml(session.name)}</name>\n")
            sb.append("    <type>${escapeXml(session.trackingMode.name)}</type>\n")
            val segments = segmentTrackPoints(validPoints)
            for (segment in segments) {
                sb.append("    <trkseg>\n")
                for (p in segment) {
                    sb.append("""      <trkpt lat="${p.latitude}" lon="${p.longitude}">""").append('\n')
                    sb.append("        <ele>${p.altitude.finiteOr()}</ele>\n")
                    sb.append("        <time>${formatIsoUtc(p.timestamp)}</time>\n")
                    sb.append("        <extensions>\n")
                    sb.append("          <geotrace:speed_mps>${p.speed.finiteOr()}</geotrace:speed_mps>\n")
                    sb.append("          <geotrace:accuracy_m>${p.accuracy.finiteOr()}</geotrace:accuracy_m>\n")
                    sb.append("          <geotrace:bearing>${p.bearing.finiteOr()}</geotrace:bearing>\n")
                    sb.append("          <geotrace:provider>${escapeXml(p.provider)}</geotrace:provider>\n")
                    sb.append("        </extensions>\n")
                    sb.append("      </trkpt>\n")
                }
                sb.append("    </trkseg>\n")
            }
            sb.append("  </trk>\n")
        }

        sb.append("</gpx>")
        return sb.toString()
    }

    fun toKml(
        session: TrackingSession,
        points: List<LocationPoint>,
        waypoints: List<TrailWaypoint> = emptyList()
    ): String {
        val validPoints = points.filter { isValidCoordinate(it.latitude, it.longitude) }
        val validWaypoints = waypoints.filter { isValidCoordinate(it.latitude, it.longitude) }
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">""").append('\n')
        sb.append("  <Document>\n")
        sb.append("    <name>${escapeXml(session.name)}</name>\n")
        sb.append(
            "    <description>${escapeXml("GeoTrace Trail - Distance: ${session.formattedDistance}, Duration: ${session.formattedDuration}, Trackpoints: ${validPoints.size}, Waypoints: ${validWaypoints.size}")}</description>\n"
        )
        session.tripId?.takeIf { it.isNotBlank() }?.let { tripId ->
            sb.append("    <ExtendedData><Data name=\"tripId\"><value>${escapeXml(tripId)}</value></Data></ExtendedData>\n")
        }
        sb.append("    <Style id=\"trackStyle\">\n")
        sb.append("      <LineStyle>\n")
        sb.append("        <color>ff946100</color>\n")
        sb.append("        <width>4</width>\n")
        sb.append("      </LineStyle>\n")
        sb.append("    </Style>\n")
        sb.append("    <Style id=\"waypointStyle\">\n")
        sb.append("      <IconStyle>\n")
        sb.append("        <color>ff0677d9</color>\n")
        sb.append("        <scale>1.1</scale>\n")
        sb.append("      </IconStyle>\n")
        sb.append("    </Style>\n")

        if (validWaypoints.isNotEmpty()) {
            sb.append("    <Folder>\n")
            sb.append("      <name>Waypoints</name>\n")
            for (w in validWaypoints) {
                val photoFileName = sanitizePhotoFileName(w.photoUri)
                sb.append("      <Placemark>\n")
                sb.append("        <name>${escapeXml(w.name)}</name>\n")
                val desc = buildString {
                    if (w.note.isNotBlank()) append(w.note).append(" | ")
                    append("Acc: ±${w.accuracy.finiteOr().toInt()}m | Time: ${formatIsoUtc(w.timestamp)}")
                }
                sb.append("        <description>${escapeXml(desc)}</description>\n")
                sb.append("        <TimeStamp><when>${formatIsoUtc(w.timestamp)}</when></TimeStamp>\n")
                if (photoFileName != null) {
                    sb.append("        <ExtendedData><Data name=\"photoFile\"><value>${escapeXml(photoFileName)}</value></Data></ExtendedData>\n")
                }
                sb.append("        <styleUrl>#waypointStyle</styleUrl>\n")
                sb.append("        <Point><coordinates>${w.longitude},${w.latitude},${w.altitude.finiteOr()}</coordinates></Point>\n")
                sb.append("      </Placemark>\n")
            }
            sb.append("    </Folder>\n")
        }

        val segments = segmentTrackPoints(validPoints)
        if (segments.isNotEmpty()) {
            sb.append("    <Placemark>\n")
            sb.append("      <name>${escapeXml(session.name)}</name>\n")
            sb.append("      <styleUrl>#trackStyle</styleUrl>\n")
            if (segments.size == 1) {
                appendGxTrack(sb, segments.first(), indent = "      ")
            } else {
                sb.append("      <gx:MultiTrack>\n")
                for (segment in segments) {
                    appendGxTrack(sb, segment, indent = "        ")
                }
                sb.append("      </gx:MultiTrack>\n")
            }
            sb.append("    </Placemark>\n")
        }

        sb.append("  </Document>\n")
        sb.append("</kml>")
        return sb.toString()
    }

    private fun appendGxTrack(sb: StringBuilder, segment: List<LocationPoint>, indent: String) {
        sb.append("${indent}<gx:Track>\n")
        sb.append("${indent}  <altitudeMode>clampToGround</altitudeMode>\n")
        for (p in segment) {
            sb.append("${indent}  <when>${formatIsoUtc(p.timestamp)}</when>\n")
        }
        for (p in segment) {
            sb.append("${indent}  <gx:coord>${p.longitude} ${p.latitude} ${p.altitude.finiteOr()}</gx:coord>\n")
        }
        sb.append("${indent}</gx:Track>\n")
    }

    fun toCsv(
        session: TrackingSession,
        points: List<LocationPoint>,
        waypoints: List<TrailWaypoint> = emptyList()
    ): String {
        val validPoints = points.filter { isValidCoordinate(it.latitude, it.longitude) }
        val validWaypoints = waypoints.filter { isValidCoordinate(it.latitude, it.longitude) }
        val sb = StringBuilder()
        sb.append("RecordType,Label,Note,SessionName,Timestamp,ISO_Time,Latitude,Longitude,Altitude_m,Speed_mps,Speed_kmh,Accuracy_m,Bearing,Provider\n")
        val escapedSession = escapeCsv(session.name)
        for (w in validWaypoints) {
            sb.append("WAYPOINT,")
            sb.append("${escapeCsv(w.name)},")
            sb.append("${escapeCsv(w.note)},")
            sb.append("$escapedSession,")
            sb.append("${w.timestamp},")
            sb.append("${formatIsoUtc(w.timestamp)},")
            sb.append("${w.latitude},")
            sb.append("${w.longitude},")
            sb.append("${w.altitude.finiteOr()},")
            sb.append("0.0,0.0,")
            sb.append("${w.accuracy.finiteOr()},")
            sb.append("0.0,")
            sb.append("manual_pin\n")
        }
        for (p in validPoints) {
            sb.append("TRACKPOINT,")
            sb.append(",")
            sb.append(",")
            sb.append("$escapedSession,")
            sb.append("${p.timestamp},")
            sb.append("${formatIsoUtc(p.timestamp)},")
            sb.append("${p.latitude},")
            sb.append("${p.longitude},")
            sb.append("${p.altitude.finiteOr()},")
            sb.append("${p.speed.finiteOr()},")
            sb.append("${p.speedKmh.finiteOr()},")
            sb.append("${p.accuracy.finiteOr()},")
            sb.append("${p.bearing.finiteOr()},")
            sb.append("${escapeCsv(p.provider)}\n")
        }
        return sb.toString()
    }

    fun toGeoJson(
        session: TrackingSession,
        points: List<LocationPoint>,
        waypoints: List<TrailWaypoint> = emptyList()
    ): String {
        val validPoints = points.filter { isValidCoordinate(it.latitude, it.longitude) }
        val validWaypoints = waypoints.filter { isValidCoordinate(it.latitude, it.longitude) }
        val features = JSONArray()
        val allSegments = segmentTrackPoints(validPoints)
        val lineSegments = allSegments.filter { it.size >= 2 }
        val singlePointSegments = allSegments.filter { it.size == 1 }.flatten()

        fun buildTrackProperties(pointCount: Int): JSONObject = JSONObject().apply {
            put("featureType", "track")
            put("sessionId", session.id)
            session.tripId?.takeIf { it.isNotBlank() }?.let { put("tripId", it) }
            put("name", session.name)
            put("trackingMode", session.trackingMode.name)
            put("startTimeUtc", formatIsoUtc(session.startTime))
            session.endTime?.let { put("endTimeUtc", formatIsoUtc(it)) }
            put("distanceMeters", session.totalDistanceMeters.finiteOr())
            put("durationSeconds", session.durationSeconds)
            put("avgSpeedKmh", session.avgSpeedKmh.finiteOr())
            put("maxSpeedKmh", session.maxSpeedKmh.finiteOr())
            put("trackpointCount", pointCount)
        }

        // RFC 7946 requires LineString (and each MultiLineString part) to have >= 2 positions
        if (lineSegments.isNotEmpty()) {
            val geometry = if (lineSegments.size == 1) {
                val coords = JSONArray().apply {
                    lineSegments.first().forEach { p ->
                        put(JSONArray().apply {
                            put(p.longitude)
                            put(p.latitude)
                            put(p.altitude.finiteOr())
                        })
                    }
                }
                JSONObject().apply {
                    put("type", "LineString")
                    put("coordinates", coords)
                }
            } else {
                val multiCoords = JSONArray().apply {
                    lineSegments.forEach { seg ->
                        put(JSONArray().apply {
                            seg.forEach { p ->
                                put(JSONArray().apply {
                                    put(p.longitude)
                                    put(p.latitude)
                                    put(p.altitude.finiteOr())
                                })
                            }
                        })
                    }
                }
                JSONObject().apply {
                    put("type", "MultiLineString")
                    put("coordinates", multiCoords)
                }
            }
            features.put(JSONObject().apply {
                put("type", "Feature")
                put("geometry", geometry)
                put("properties", buildTrackProperties(validPoints.size))
            })
        }

        // Preserve any isolated 1-point track segment as a valid RFC 7946 Point feature
        for (p in singlePointSegments) {
            val geom = JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().apply {
                    put(p.longitude)
                    put(p.latitude)
                    put(p.altitude.finiteOr())
                })
            }
            val props = buildTrackProperties(1).apply {
                put("featureType", "track_point")
                put("timestampUtc", formatIsoUtc(p.timestamp))
                put("accuracyMeters", p.accuracy.finiteOr().toDouble())
                put("provider", p.provider)
            }
            features.put(JSONObject().apply {
                put("type", "Feature")
                put("geometry", geom)
                put("properties", props)
            })
        }

        for (w in validWaypoints) {
            val geom = JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().apply {
                    put(w.longitude)
                    put(w.latitude)
                    put(w.altitude.finiteOr())
                })
            }
            val props = JSONObject().apply {
                put("featureType", "waypoint")
                put("id", w.id)
                put("name", w.name)
                if (w.note.isNotBlank()) put("note", w.note)
                sanitizePhotoFileName(w.photoUri)?.let { put("photoFile", it) }
                put("accuracyMeters", w.accuracy.finiteOr().toDouble())
                put("timestampUtc", formatIsoUtc(w.timestamp))
            }
            features.put(JSONObject().apply {
                put("type", "Feature")
                put("geometry", geom)
                put("properties", props)
            })
        }

        val root = JSONObject().apply {
            put("type", "FeatureCollection")
            put("features", features)
        }
        return root.toString(2)
    }

    private fun segmentTrackPoints(points: List<LocationPoint>, maxJumpMeters: Double = 5000.0): List<List<LocationPoint>> {
        if (points.isEmpty()) return emptyList()
        val segments = mutableListOf<MutableList<LocationPoint>>()
        var current = mutableListOf<LocationPoint>()
        for (p in points) {
            if (current.isEmpty()) {
                current.add(p)
            } else {
                val prev = current.last()
                val gap = haversineMeters(prev.latitude, prev.longitude, p.latitude, p.longitude)
                if (gap > maxJumpMeters) {
                    segments.add(current)
                    current = mutableListOf(p)
                } else {
                    current.add(p)
                }
            }
        }
        if (current.isNotEmpty()) {
            segments.add(current)
        }
        return segments
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

    fun buildEmergencyShareMessage(
        point: LocationPoint?,
        cellTower: CellTowerInfo?,
        batteryPercent: Int? = null,
        altitudeMeters: Double? = null
    ): String {
        val batteryLine = batteryPercent?.let {
            "\n🔋 Battery: $it%"
        } ?: ""
        val cellDetails = if (cellTower != null) {
            val sigLabel = cellTower.signalDbm?.let { "$it dBm" } ?: "N/A"
            "\n📡 Cell Tower: ${cellTower.operatorDisplayName} | CID: ${cellTower.cid ?: "N/A"}, LAC/TAC: ${cellTower.lac ?: "N/A"}, Signal: $sigLabel"
        } else ""

        if (point == null) {
            return "🚨 *GIS TRACKER EMERGENCY ALERT*\n📍 Position: Location unavailable (awaiting GPS lock)$batteryLine$cellDetails"
        }

        val latStr = String.format(Locale.US, "%.5f", point.latitude)
        val lonStr = String.format(Locale.US, "%.5f", point.longitude)
        val mapsUrl = "https://maps.google.com/?q=$latStr,$lonStr"
        val geoUri = "geo:$latStr,$lonStr?q=$latStr,$lonStr"
        val timeStr = SimpleDateFormat("HH:mm:ss dd-MMM-yyyy", Locale.getDefault()).format(Date(point.timestamp))

        val resolvedAltitude = altitudeMeters ?: point.altitude.takeIf { it != 0.0 }
        val altLine = resolvedAltitude?.let {
            "\n⛰️ Altitude: ${it.toInt()} m"
        } ?: ""
        val fixSourceTag = when (point.provider) {
            "last_gasp" -> " (Last-Gasp Beacon)"
            "cellular" -> " (Approx. Cell Mast)"
            else -> ""
        }

        return """
🚨 *GIS TRACKER EMERGENCY ALERT*
📍 Position: $latStr, $lonStr$fixSourceTag
🎯 Accuracy: ±${String.format(Locale.US, "%.1f", point.accuracy)} meters
⚡ Speed: ${String.format(Locale.US, "%.1f", point.speedKmh)} km/h$altLine$batteryLine
⏰ Time: $timeStr$cellDetails
🧭 Nav URI: $geoUri
🔗 Live Map: $mapsUrl
        """.trimIndent()
    }

    private fun escapeXml(input: String): String {
        return buildString(input.length) {
            for (ch in input) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> if (ch == '\t' || ch == '\n' || ch == '\r' || ch.code >= 0x20) append(ch)
                }
            }
        }
    }

    private fun escapeCsv(input: String): String {
        val cleaned = input.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        val safeValue = if (cleaned.isNotEmpty() && cleaned[0] in charArrayOf('=', '+', '-', '@', '\t')) {
            "'$cleaned"
        } else {
            cleaned
        }
        val needsQuotes = safeValue.contains(',') || safeValue.contains('"') || safeValue != cleaned
        return if (!needsQuotes) safeValue else "\"${safeValue.replace("\"", "\"\"")}\""
    }

    private fun sanitizePhotoFileName(photoUri: String): String? {
        val raw = photoUri.trim()
        if (raw.isEmpty()) return null
        val candidate = File(raw.substringBefore('?').substringBefore('#')).name.trim()
        if (candidate.isEmpty() || candidate == "." || candidate == "..") return null
        return candidate
    }
}
