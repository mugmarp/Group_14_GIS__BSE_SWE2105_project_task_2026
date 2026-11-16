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
        return isoFormatThreadLocal.get()?.format(Date(timestampMs)) ?: ""
    }

    fun buildSafeFileName(sessionName: String, format: ExportFormat, nowMs: Long = System.currentTimeMillis()): String {
        val sanitizedName = sessionName
            .trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
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
        return when (format) {
            ExportFormat.GPX -> toGpx(session, points, waypoints)
            ExportFormat.KML -> toKml(session, points, waypoints)
            ExportFormat.CSV -> toCsv(session, points, waypoints)
            ExportFormat.GEOJSON -> toGeoJson(session, points, waypoints)
        }
    }

    fun toGpx(
        session: TrackingSession,
        points: List<LocationPoint>,
        waypoints: List<TrailWaypoint> = emptyList()
    ): String {
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
        sb.append("  </metadata>\n")

        for (w in waypoints) {
            sb.append("""  <wpt lat="${w.latitude}" lon="${w.longitude}">""").append('\n')
            sb.append("    <ele>${w.altitude}</ele>\n")
            sb.append("    <time>${formatIsoUtc(w.timestamp)}</time>\n")
            sb.append("    <name>${escapeXml(w.name)}</name>\n")
            if (w.note.isNotBlank()) {
                sb.append("    <desc>${escapeXml(w.note)}</desc>\n")
            }
            sb.append("    <sym>Flag</sym>\n")
            sb.append("    <extensions>\n")
            sb.append("      <geotrace:accuracy>${w.accuracy}</geotrace:accuracy>\n")
            sb.append("    </extensions>\n")
            sb.append("  </wpt>\n")
        }

        if (points.isNotEmpty()) {
            sb.append("  <trk>\n")
            sb.append("    <name>${escapeXml(session.name)}</name>\n")
            sb.append("    <type>${escapeXml(session.trackingMode.name)}</type>\n")
            val segments = segmentTrackPoints(points)
            for (segment in segments) {
                sb.append("    <trkseg>\n")
                for (p in segment) {
                    sb.append("""      <trkpt lat="${p.latitude}" lon="${p.longitude}">""").append('\n')
                    sb.append("        <ele>${p.altitude}</ele>\n")
                    sb.append("        <time>${formatIsoUtc(p.timestamp)}</time>\n")
                    sb.append("        <extensions>\n")
                    sb.append("          <geotrace:speed_mps>${p.speed}</geotrace:speed_mps>\n")
                    sb.append("          <geotrace:accuracy_m>${p.accuracy}</geotrace:accuracy_m>\n")
                    sb.append("          <geotrace:bearing>${p.bearing}</geotrace:bearing>\n")
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
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
        sb.append("""<kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">""").append('\n')
        sb.append("  <Document>\n")
        sb.append("    <name>${escapeXml(session.name)}</name>\n")
        sb.append(
            "    <description>${escapeXml("GeoTrace Trail - Distance: ${session.formattedDistance}, Duration: ${session.formattedDuration}, Trackpoints: ${points.size}, Waypoints: ${waypoints.size}")}</description>\n"
        )
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

        if (waypoints.isNotEmpty()) {
            sb.append("    <Folder>\n")
            sb.append("      <name>Waypoints</name>\n")
            for (w in waypoints) {
                sb.append("      <Placemark>\n")
                sb.append("        <name>${escapeXml(w.name)}</name>\n")
                val desc = buildString {
                    if (w.note.isNotBlank()) append(w.note).append(" | ")
                    append("Acc: ±${w.accuracy.toInt()}m | Time: ${formatIsoUtc(w.timestamp)}")
                }
                sb.append("        <description>${escapeXml(desc)}</description>\n")
                sb.append("        <TimeStamp><when>${formatIsoUtc(w.timestamp)}</when></TimeStamp>\n")
                sb.append("        <styleUrl>#waypointStyle</styleUrl>\n")
                sb.append("        <Point><coordinates>${w.longitude},${w.latitude},${w.altitude}</coordinates></Point>\n")
                sb.append("      </Placemark>\n")
            }
            sb.append("    </Folder>\n")
        }

        val segments = segmentTrackPoints(points)
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
            sb.append("${indent}  <gx:coord>${p.longitude} ${p.latitude} ${p.altitude}</gx:coord>\n")
        }
        sb.append("${indent}</gx:Track>\n")
    }

    fun toCsv(
        session: TrackingSession,
        points: List<LocationPoint>,
        waypoints: List<TrailWaypoint> = emptyList()
    ): String {
        val sb = StringBuilder()
        sb.append("RecordType,Label,Note,SessionName,Timestamp,ISO_Time,Latitude,Longitude,Altitude_m,Speed_mps,Speed_kmh,Accuracy_m,Bearing,Provider\n")
        val escapedSession = escapeCsv(session.name)
        for (w in waypoints) {
            sb.append("WAYPOINT,")
            sb.append("${escapeCsv(w.name)},")
            sb.append("${escapeCsv(w.note)},")
            sb.append("$escapedSession,")
            sb.append("${w.timestamp},")
            sb.append("${formatIsoUtc(w.timestamp)},")
            sb.append("${w.latitude},")
            sb.append("${w.longitude},")
            sb.append("${w.altitude},")
            sb.append("0.0,0.0,")
            sb.append("${w.accuracy},")
            sb.append("0.0,")
            sb.append("manual_pin\n")
        }
        for (p in points) {
            sb.append("TRACKPOINT,")
            sb.append(",")
            sb.append(",")
            sb.append("$escapedSession,")
            sb.append("${p.timestamp},")
            sb.append("${formatIsoUtc(p.timestamp)},")
            sb.append("${p.latitude},")
            sb.append("${p.longitude},")
            sb.append("${p.altitude},")
            sb.append("${p.speed},")
            sb.append("${p.speedKmh},")
            sb.append("${p.accuracy},")
            sb.append("${p.bearing},")
            sb.append("${escapeCsv(p.provider)}\n")
        }
        return sb.toString()
    }

    fun toGeoJson(
        session: TrackingSession,
        points: List<LocationPoint>,
        waypoints: List<TrailWaypoint> = emptyList()
    ): String {
        val features = JSONArray()
        val segments = segmentTrackPoints(points)
        if (segments.isNotEmpty()) {
            val geometry = if (segments.size == 1) {
                val coords = JSONArray().apply {
                    segments.first().forEach { p ->
                        put(JSONArray().apply {
                            put(p.longitude)
                            put(p.latitude)
                            put(p.altitude)
                        })
                    }
                }
                JSONObject().apply {
                    put("type", "LineString")
                    put("coordinates", coords)
                }
            } else {
                val multiCoords = JSONArray().apply {
                    segments.forEach { seg ->
                        put(JSONArray().apply {
                            seg.forEach { p ->
                                put(JSONArray().apply {
                                    put(p.longitude)
                                    put(p.latitude)
                                    put(p.altitude)
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
            val props = JSONObject().apply {
                put("featureType", "track")
                put("sessionId", session.id)
                put("name", session.name)
                put("trackingMode", session.trackingMode.name)
                put("startTimeUtc", formatIsoUtc(session.startTime))
                session.endTime?.let { put("endTimeUtc", formatIsoUtc(it)) }
                put("distanceMeters", session.totalDistanceMeters)
                put("durationSeconds", session.durationSeconds)
                put("avgSpeedKmh", session.avgSpeedKmh)
                put("maxSpeedKmh", session.maxSpeedKmh)
                put("trackpointCount", points.size)
            }
            features.put(JSONObject().apply {
                put("type", "Feature")
                put("geometry", geometry)
                put("properties", props)
            })
        }

        for (w in waypoints) {
            val geom = JSONObject().apply {
                put("type", "Point")
                put("coordinates", JSONArray().apply {
                    put(w.longitude)
                    put(w.latitude)
                    put(w.altitude)
                })
            }
            val props = JSONObject().apply {
                put("featureType", "waypoint")
                put("id", w.id)
                put("name", w.name)
                if (w.note.isNotBlank()) put("note", w.note)
                put("accuracyMeters", w.accuracy.toDouble())
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
        cellTower: CellTowerInfo?
    ): String {
        if (point == null) return "Emergency Alert: Location tracking active, awaiting GPS lock."

        val mapsUrl = "https://maps.google.com/?q=${point.latitude},${point.longitude}"
        val timeStr = SimpleDateFormat("HH:mm:ss dd-MMM-yyyy", Locale.getDefault()).format(Date(point.timestamp))

        val cellDetails = if (cellTower != null) {
            "\n📡 Cell Tower: ${cellTower.operatorDisplayName} | CID: ${cellTower.cid ?: "N/A"}, LAC/TAC: ${cellTower.lac ?: "N/A"}, Signal: ${cellTower.signalDbm ?: 0} dBm"
        } else ""

        return """
🚨 *GIS TRACKER EMERGENCY ALERT*
📍 Position: ${String.format(Locale.US, "%.5f", point.latitude)}, ${String.format(Locale.US, "%.5f", point.longitude)}
🎯 Accuracy: ±${String.format(Locale.US, "%.1f", point.accuracy)} meters
⚡ Speed: ${String.format(Locale.US, "%.1f", point.speedKmh)} km/h
⏰ Time: $timeStr$cellDetails
🔗 Live Map: $mapsUrl
        """.trimIndent()
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun escapeCsv(input: String): String {
        val needsQuotes = input.contains(',') || input.contains('"') || input.contains('\n') || input.contains('\r')
        if (!needsQuotes) return input
        return "\"${input.replace("\"", "\"\"")}\""
    }
}
