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
}
