package com.geotrace.data.export

import com.geotrace.model.CellTowerInfo
import com.geotrace.model.LocationPoint
import com.geotrace.model.TrackingSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object TrackExporter {

    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun toGpx(session: TrackingSession, points: List<LocationPoint>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<gpx version="1.1" creator="GeoTrace SWE2105" xmlns="http://www.topografix.com/GPX/1/1">""").append("\n")
        sb.append("  <metadata>\n")
        sb.append("    <name>${escapeXml(session.name)}</name>\n")
        sb.append("    <desc>Recorded via GeoTrace Mobile App - Mode: ${session.trackingMode.name}</desc>\n")
        sb.append("    <time>${iso8601Format.format(Date(session.startTime))}</time>\n")
        sb.append("  </metadata>\n")
        sb.append("  <trk>\n")
        sb.append("    <name>${escapeXml(session.name)}</name>\n")
        sb.append("    <trkseg>\n")

        for (p in points) {
            sb.append("""      <trkpt lat="${p.latitude}" lon="${p.longitude}">""").append("\n")
            sb.append("        <ele>${p.altitude}</ele>\n")
            sb.append("        <time>${iso8601Format.format(Date(p.timestamp))}</time>\n")
            sb.append("        <speed>${p.speed}</speed>\n")
            sb.append("        <extensions>\n")
            sb.append("          <accuracy>${p.accuracy}</accuracy>\n")
            sb.append("          <provider>${p.provider}</provider>\n")
            sb.append("        </extensions>\n")
            sb.append("      </trkpt>\n")
        }

        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>")
        return sb.toString()
    }

    fun toKml(session: TrackingSession, points: List<LocationPoint>): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append("\n")
        sb.append("""<kml xmlns="http://www.opengis.net/kml/2.2">""").append("\n")
        sb.append("  <Document>\n")
        sb.append("    <name>${escapeXml(session.name)}</name>\n")
        sb.append("    <description>GeoTrace Path - Distance: ${session.formattedDistance}, Duration: ${session.formattedDuration}</description>\n")
        sb.append("    <Style id=\"trackStyle\">\n")
        sb.append("      <LineStyle>\n")
        sb.append("        <color>ff00e5ff</color>\n") // Cyan (aabbggrr)
        sb.append("        <width>4</width>\n")
        sb.append("      </LineStyle>\n")
        sb.append("    </Style>\n")
        sb.append("    <Placemark>\n")
        sb.append("      <name>GPS Trail</name>\n")
        sb.append("      <styleUrl>#trackStyle</styleUrl>\n")
        sb.append("      <LineString>\n")
        sb.append("        <extrude>1</extrude>\n")
        sb.append("        <tessellate>1</tessellate>\n")
        sb.append("        <coordinates>\n")

        val coordStrings = points.joinToString(" ") { p ->
            "${p.longitude},${p.latitude},${p.altitude}"
        }
        sb.append("          ").append(coordStrings).append("\n")
        sb.append("        </coordinates>\n")
        sb.append("      </LineString>\n")
        sb.append("    </Placemark>\n")
        sb.append("  </Document>\n")
        sb.append("</kml>")
        return sb.toString()
    }

    fun toCsv(session: TrackingSession, points: List<LocationPoint>): String {
        val sb = StringBuilder()
        sb.append("Timestamp,ISO_Time,Latitude,Longitude,Altitude_m,Speed_mps,Speed_kmh,Accuracy_m,Bearing,Provider\n")
        for (p in points) {
            sb.append("${p.timestamp},")
            sb.append("${iso8601Format.format(Date(p.timestamp))},")
            sb.append("${p.latitude},")
            sb.append("${p.longitude},")
            sb.append("${p.altitude},")
            sb.append("${p.speed},")
            sb.append("${p.speedKmh},")
            sb.append("${p.accuracy},")
            sb.append("${p.bearing},")
            sb.append("${p.provider}\n")
        }
        return sb.toString()
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
📍 Position: ${String.format("%.5f", point.latitude)}, ${String.format("%.5f", point.longitude)}
🎯 Accuracy: ±${String.format("%.1f", point.accuracy)} meters
⚡ Speed: ${String.format("%.1f", point.speedKmh)} km/h
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
}
