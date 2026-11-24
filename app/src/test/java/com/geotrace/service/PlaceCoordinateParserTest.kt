package com.geotrace.service

import com.geotrace.model.CoordinateFormat
import com.geotrace.model.SearchResultSource
import com.geotrace.ui.screens.formatCoordinates
import com.geotrace.ui.screens.formatCopyableCoordinates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceCoordinateParserTest {

    @Test
    fun `parseDirectCoordinates parses decimal degrees and hemispheres offline`() {
        val mbarara = PlaceSearchManager.parseDirectCoordinates("-0.59470, 30.65450")
        assertNotNull(mbarara)
        assertEquals(-0.59470, mbarara!!.latitude, 1e-5)
        assertEquals(30.65450, mbarara.longitude, 1e-5)
        assertEquals(SearchResultSource.COORDINATE, mbarara.source)

        val labeled = PlaceSearchManager.parseDirectCoordinates("0.5947° S, 30.6545° E")
        assertNotNull(labeled)
        assertEquals(-0.5947, labeled!!.latitude, 1e-5)
        assertEquals(30.6545, labeled.longitude, 1e-5)
    }

    @Test
    fun `parseDirectCoordinates parses DMS and Google Maps or geo URIs`() {
        val dms = PlaceSearchManager.parseDirectCoordinates("""0°19'57.4"N, 32°34'06.6"E""")
        assertNotNull(dms)
        assertEquals(0.33261, dms!!.latitude, 1e-4)
        assertEquals(32.5685, dms.longitude, 1e-4)

        val mapsUrl = PlaceSearchManager.parseDirectCoordinates("https://maps.google.com/?q=-0.61234,30.65890")
        assertNotNull(mapsUrl)
        assertEquals(-0.61234, mapsUrl!!.latitude, 1e-5)
        assertEquals(30.65890, mapsUrl.longitude, 1e-5)

        val geoUri = PlaceSearchManager.parseDirectCoordinates("geo:0.34760,32.58250")
        assertNotNull(geoUri)
        assertEquals(0.34760, geoUri!!.latitude, 1e-5)
        assertEquals(32.58250, geoUri.longitude, 1e-5)
    }

    @Test
    fun `parseDirectCoordinates rejects invalid coordinates and out of range DMS`() {
        assertNull(PlaceSearchManager.parseDirectCoordinates("Mbarara Main Library"))
        assertNull(PlaceSearchManager.parseDirectCoordinates("95.0, 30.0"))
        assertNull(PlaceSearchManager.parseDirectCoordinates("""0°65'10.0"N, 32°10'10.0"E"""))
    }

    @Test
    fun `formatCoordinates and formatCopyableCoordinates respect coordinate format`() {
        val dec = formatCoordinates(-0.5947, 30.6545, CoordinateFormat.DECIMAL)
        val copyDec = formatCopyableCoordinates(-0.5947, 30.6545, CoordinateFormat.DECIMAL)
        val dms = formatCoordinates(-0.5947, 30.6545, CoordinateFormat.DMS)

        assertEquals("-0.59470°, 30.65450°", dec)
        assertEquals("-0.594700, 30.654500", copyDec)
        assertTrue(dms.contains("S") && dms.contains("E"))
    }
}
