package com.geotrace.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackImporterTest {

    @Test
    fun `parse reads GPX 1_1 track points waypoints and elevation`() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="GeoTrace">
              <metadata><name>Rwenzori Foothills</name></metadata>
              <wpt lat="0.3480" lon="30.0010">
                <ele>1820.0</ele>
                <name>Water Spring</name>
                <desc>Clean mountain stream</desc>
              </wpt>
              <trk>
                <name>Central Ridge Trail</name>
                <trkseg>
                  <trkpt lat="0.3476" lon="30.0000">
                    <ele>1800.0</ele>
                    <time>2026-03-20T08:00:00Z</time>
                  </trkpt>
                  <trkpt lat="0.3490" lon="30.0020">
                    <ele>1845.0</ele>
                    <time>2026-03-20T08:10:00Z</time>
                  </trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val result = TrackImporter.parse(gpx, fallbackName = "Fallback", hintExtension = "gpx")
        assertTrue(result.isSuccess)
        val route = result.getOrThrow()
        assertEquals("Central Ridge Trail", route.name)
        assertEquals("GPX", route.sourceFormat)
        assertEquals(2, route.points.size)
        assertEquals(1, route.waypoints.size)
        assertEquals("Water Spring", route.waypoints.first().name)
        assertEquals(45.0, route.elevationGainMeters, 0.1)
        assertTrue(route.totalDistanceMeters > 200.0)
    }

    @Test
    fun `parse reads KML LineString coordinates and Point placemark`() {
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2">
              <Document>
                <name>Lake Victoria Loop</name>
                <Placemark>
                  <name>Dock Pier</name>
                  <description>Ferry landing</description>
                  <Point><coordinates>32.5825,0.3476,1135.0</coordinates></Point>
                </Placemark>
                <Placemark>
                  <name>Shoreline Path</name>
                  <LineString>
                    <coordinates>32.5825,0.3476,1135.0 32.5850,0.3490,1142.0</coordinates>
                  </LineString>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val result = TrackImporter.parse(kml, hintExtension = "kml")
        assertTrue(result.isSuccess)
        val route = result.getOrThrow()
        assertEquals("Lake Victoria Loop", route.name)
        assertEquals("KML", route.sourceFormat)
        assertEquals(2, route.points.size)
        assertEquals(1, route.waypoints.size)
        assertEquals("Dock Pier", route.waypoints.first().name)
        assertEquals(1135.0, route.minAltitudeMeters, 0.1)
    }

    @Test
    fun `parse reads KML gx Track coordinates`() {
        val kmlGx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
              <Document>
                <Placemark>
                  <name>GX Ridge</name>
                  <gx:Track>
                    <gx:coord>30.6545 -0.5947 1430.0</gx:coord>
                    <gx:coord>30.6560 -0.5960 1445.0</gx:coord>
                  </gx:Track>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val result = TrackImporter.parse(kmlGx)
        assertTrue(result.isSuccess)
        val route = result.getOrThrow()
        assertEquals(2, route.points.size)
        assertEquals(15.0, route.elevationGainMeters, 0.1)
    }

    @Test
    fun `parse reads GeoJSON FeatureCollection with LineString and Point`() {
        val geoJson = """
            {
              "type": "FeatureCollection",
              "name": "Safari Track",
              "features": [
                {
                  "type": "Feature",
                  "properties": { "name": "Queen Elizabeth Drive" },
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [30.0000, -0.1000, 950.0],
                      [30.0050, -0.1020, 965.0]
                    ]
                  }
                },
                {
                  "type": "Feature",
                  "properties": { "name": "Lookout Point", "note": "Good wildlife view" },
                  "geometry": {
                    "type": "Point",
                    "coordinates": [30.0025, -0.1010, 958.0]
                  }
                }
              ]
            }
        """.trimIndent()

        val result = TrackImporter.parse(geoJson, hintExtension = "geojson")
        assertTrue(result.isSuccess)
        val route = result.getOrThrow()
        assertEquals("Safari Track", route.name)
        assertEquals("GeoJSON", route.sourceFormat)
        assertEquals(2, route.points.size)
        assertEquals(1, route.waypoints.size)
        assertEquals("Lookout Point", route.waypoints.first().name)
    }

    @Test
    fun `parse fails gracefully on empty or invalid content`() {
        assertTrue(TrackImporter.parse("   ").isFailure)
        assertTrue(TrackImporter.parse("not a route file", hintExtension = "txt").isFailure)
        assertTrue(TrackImporter.parse("<gpx version=\"1.1\"></gpx>").isFailure)
    }

    @Test
    fun `parse reads mixed KML LineString gx Track and multiple Point elements with zero timestamp when omitted`() {
        val kml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <kml xmlns="http://www.opengis.net/kml/2.2" xmlns:gx="http://www.google.com/kml/ext/2.2">
              <Document>
                <Placemark>
                  <name>Dual Markers</name>
                  <MultiGeometry>
                    <Point><coordinates>30.000,0.000,1100.0</coordinates></Point>
                    <Point><coordinates>30.002,0.001,1115.0</coordinates></Point>
                  </MultiGeometry>
                </Placemark>
                <Placemark>
                  <LineString>
                    <coordinates>30.000,0.000,1100.0 30.001,0.001,1110.0</coordinates>
                  </LineString>
                </Placemark>
                <Placemark>
                  <gx:Track>
                    <gx:coord>30.001 0.001 1110.0</gx:coord>
                    <gx:coord>30.002 0.002 1125.0</gx:coord>
                  </gx:Track>
                </Placemark>
              </Document>
            </kml>
        """.trimIndent()

        val route = TrackImporter.parse(kml, hintExtension = "kml").getOrThrow()
        assertEquals(2, route.waypoints.size)
        assertEquals(4, route.points.size)
        assertTrue(route.points.all { it.timestamp == 0L })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `readBoundedUtf8 rejects streams larger than maxBytes`() {
        val stream = "abcdef".byteInputStream(Charsets.UTF_8)
        TrackImporter.readBoundedUtf8(stream, maxBytes = 4)
    }
}
