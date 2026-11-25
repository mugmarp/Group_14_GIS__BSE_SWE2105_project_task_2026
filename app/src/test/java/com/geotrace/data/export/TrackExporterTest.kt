package com.geotrace.data.export

import com.geotrace.model.LocationPoint
import com.geotrace.model.TrackingMode
import com.geotrace.model.TrackingSession
import com.geotrace.model.TrailWaypoint
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackExporterTest {

    private val baseSession = TrackingSession(
        id = "sess-1",
        name = "Mbarara Survey & <Campus>",
        startTime = 1_700_000_000_000L,
        endTime = 1_700_000_600_000L,
        totalDistanceMeters = 1420.5,
        avgSpeedKmh = 8.5,
        maxSpeedKmh = 14.2,
        pointCount = 3,
        waypointCount = 1,
        trackingMode = TrackingMode.HIGH_ACCURACY
    )

    private val samplePoints = listOf(
        LocationPoint(
            id = 1L,
            latitude = -0.5947,
            longitude = 30.6545,
            accuracy = 4.2f,
            speed = 2.1f,
            altitude = 1430.0,
            bearing = 90f,
            provider = "gps",
            timestamp = 1_700_000_010_000L,
            sessionId = "sess-1"
        ),
        LocationPoint(
            id = 2L,
            latitude = -0.5950,
            longitude = 30.6552,
            accuracy = 3.8f,
            speed = 2.4f,
            altitude = 1432.5,
            bearing = 95f,
            provider = "gps",
            timestamp = 1_700_000_020_000L,
            sessionId = "sess-1"
        )
    )

    private val sampleWaypoints = listOf(
        TrailWaypoint(
            id = "wp-1",
            sessionId = "sess-1",
            name = "=Gate & Lab",
            note = "Check \"Signal\"",
            latitude = -0.5948,
            longitude = 30.6549,
            altitude = 1431.0,
            accuracy = 3.5f,
            timestamp = 1_700_000_015_000L
        )
    )

    @Test
    fun `buildSafeFileName sanitizes special characters and preserves extension`() {
        val gpxName = TrackExporter.buildSafeFileName("Campus / Trail: #1!", ExportFormat.GPX, 1_700_000_000_000L)
        assertTrue(gpxName.endsWith(".gpx"))
        assertFalse(gpxName.contains("/"))
        assertFalse(gpxName.contains(":"))

        val fallback = TrackExporter.buildSafeFileName("   ///   ", ExportFormat.GEOJSON, 1_700_000_000_000L)
        assertTrue(fallback.startsWith("geotrace_trail_"))
        assertTrue(fallback.endsWith(".geojson"))
    }

    @Test
    fun `toGpx escapes XML entities filters invalid coords and segments large jumps`() {
        val jumpPoint = LocationPoint(
            id = 3L,
            latitude = 0.3476, // Kampala jump (>100km from Mbarara)
            longitude = 32.5825,
            accuracy = 5.0f,
            timestamp = 1_700_000_100_000L,
            sessionId = "sess-1"
        )
        val invalidZero = LocationPoint(
            id = 4L,
            latitude = 0.0,
            longitude = 0.0,
            accuracy = 10f,
            timestamp = 1_700_000_110_000L,
            sessionId = "sess-1"
        )

        val gpx = TrackExporter.toGpx(baseSession, samplePoints + jumpPoint + invalidZero, sampleWaypoints)

        assertTrue(gpx.contains("<name>Mbarara Survey &amp; &lt;Campus&gt;</name>"))
        assertTrue(gpx.contains("<desc>Check &quot;Signal&quot;</desc>"))
        assertFalse(gpx.contains("""lat="0.0" lon="0.0""""))
        // Expect two separate <trkseg> blocks due to >5000m jump
        assertEquals(2, Regex("<trkseg>").findAll(gpx).count())
    }

    @Test
    fun `toCsv neutralizes spreadsheet formula injection prefixes`() {
        val csv = TrackExporter.toCsv(baseSession, samplePoints, sampleWaypoints)
        val lines = csv.lines().filter { it.isNotBlank() }

        assertTrue(lines.first().startsWith("RecordType,Label,Note,SessionName"))
        val waypointRow = lines[1]
        assertTrue(waypointRow.startsWith("WAYPOINT,\"'=Gate & Lab\","))
        assertTrue(waypointRow.contains("\"Check \"\"Signal\"\"\""))
    }

    @Test
    fun `toGeoJson outputs valid RFC 7946 LineString and MultiLineString with single point fallback`() {
        val singleOnly = TrackExporter.toGeoJson(baseSession, listOf(samplePoints.first()), sampleWaypoints)
        val singleRoot = JSONObject(singleOnly)
        assertEquals("FeatureCollection", singleRoot.getString("type"))
        val singleFeatures = singleRoot.getJSONArray("features")
        assertEquals(2, singleFeatures.length())
        assertEquals("Point", singleFeatures.getJSONObject(0).getJSONObject("geometry").getString("type"))
        assertEquals("track_point", singleFeatures.getJSONObject(0).getJSONObject("properties").getString("featureType"))

        val multi = TrackExporter.toGeoJson(baseSession, samplePoints, sampleWaypoints)
        val multiRoot = JSONObject(multi)
        val multiFeatures = multiRoot.getJSONArray("features")
        assertEquals("LineString", multiFeatures.getJSONObject(0).getJSONObject("geometry").getString("type"))
        assertEquals("waypoint", multiFeatures.getJSONObject(1).getJSONObject("properties").getString("featureType"))
    }

    @Test
    fun `toKml produces gx MultiTrack when track has spatial gap`() {
        val jumpPoint = samplePoints.last().copy(id = 99L, latitude = 0.3476, longitude = 32.5825)
        val kml = TrackExporter.toKml(baseSession, samplePoints + jumpPoint, sampleWaypoints)

        assertTrue(kml.contains("<Folder>"))
        assertTrue(kml.contains("<gx:MultiTrack>"))
    }

    @Test
    fun `buildEmergencyShareMessage includes battery altitude cell tower and geo URI`() {
        val tower = com.geotrace.model.CellTowerInfo(
            type = "LTE",
            mcc = 641,
            mnc = 10,
            cid = 123456L,
            lac = 4010,
            signalDbm = -87,
            signalLevel = 3,
            isRegistered = true,
            operatorName = "MTN Uganda"
        )
        val msg = TrackExporter.buildEmergencyShareMessage(
            point = samplePoints.first(),
            cellTower = tower,
            batteryPercent = 14,
            altitudeMeters = 1430.0
        )

        assertTrue(msg.contains("-0.59470, 30.65450"))
        assertTrue(msg.contains("Altitude: 1430 m"))
        assertTrue(msg.contains("Battery: 14%"))
        assertTrue(msg.contains("Nav URI: geo:-0.59470,30.65450?q=-0.59470,30.65450"))
        assertTrue(msg.contains("Live Map: https://maps.google.com/?q=-0.59470,30.65450"))
        assertTrue(msg.contains("MTN Uganda"))
        assertTrue(msg.contains("-87 dBm"))
    }

    @Test
    fun `buildEmergencyShareMessage handles null point and omits battery when null`() {
        val waiting = TrackExporter.buildEmergencyShareMessage(point = null, cellTower = null)
        assertTrue(waiting.contains("awaiting GPS lock"))

        val noExtras = TrackExporter.buildEmergencyShareMessage(
            point = samplePoints.first().copy(altitude = 0.0),
            cellTower = null
        )
        assertFalse(noExtras.contains("Battery:"))
        assertFalse(noExtras.contains("Altitude:"))
        assertTrue(noExtras.contains("geo:-0.59470,30.65450"))
    }

    @Test
    fun `exports include tripId and sanitized photo filename without leaking internal filesystem path`() {
        val secretDirPath = "/data/user/0/com.geotrace.secret/files/photos/field_camp_01.jpg"
        val tripSession = baseSession.copy(tripId = "trip-rwenzori-2026")
        val photoWaypoints = listOf(
            sampleWaypoints.first().copy(photoUri = secretDirPath)
        )

        val gpx = TrackExporter.toGpx(tripSession, samplePoints, photoWaypoints)
        assertTrue(gpx.contains("<geotrace:trip_id>trip-rwenzori-2026</geotrace:trip_id>"))
        assertTrue(gpx.contains("<geotrace:photo>field_camp_01.jpg</geotrace:photo>"))
        assertFalse(gpx.contains("/data/user/0/"))

        val kml = TrackExporter.toKml(tripSession, samplePoints, photoWaypoints)
        assertTrue(kml.contains("""<Data name="tripId"><value>trip-rwenzori-2026</value></Data>"""))
        assertTrue(kml.contains("""<Data name="photoFile"><value>field_camp_01.jpg</value></Data>"""))
        assertFalse(kml.contains("/data/user/0/"))

        val geoJson = TrackExporter.toGeoJson(tripSession, samplePoints, photoWaypoints)
        val root = JSONObject(geoJson)
        val features = root.getJSONArray("features")
        assertEquals("trip-rwenzori-2026", features.getJSONObject(0).getJSONObject("properties").getString("tripId"))
        assertEquals("field_camp_01.jpg", features.getJSONObject(1).getJSONObject("properties").getString("photoFile"))
        assertFalse(geoJson.contains("/data/user/0/"))
    }
}
