package com.geotrace.service

import com.geotrace.data.export.TrackExporter
import com.geotrace.model.CellTowerInfo
import com.geotrace.model.LocationPoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmergencySosFallbackTest {

    private fun sampleCell() = CellTowerInfo(
        type = "4G LTE",
        mcc = 641,
        mnc = 10,
        cid = 987654L,
        lac = 4021,
        signalDbm = -88,
        signalLevel = 3,
        isRegistered = true,
        operatorName = "MTN Uganda"
    )

    private fun samplePoint(provider: String) = LocationPoint(
        latitude = -0.61670,
        longitude = 30.65000,
        accuracy = 12.5f,
        speed = 2.0f,
        altitude = 1380.0,
        bearing = 45f,
        provider = provider,
        timestamp = 1_700_000_000_000L,
        sessionId = "s1"
    )

    @Test
    fun buildEmergencyShareMessage_formatsLiveGnssAndFallbacksAccurately() {
        val cell = sampleCell()

        val gnssMsg = TrackExporter.buildEmergencyShareMessage(
            point = samplePoint("gps"),
            cellTower = cell,
            batteryPercent = 74
        )
        assertTrue(gnssMsg.contains("-0.61670, 30.65000"))
        assertFalse(gnssMsg.contains("Last-Gasp"))
        assertFalse(gnssMsg.contains("Approx. Cell Mast"))
        assertTrue(gnssMsg.contains("Battery: 74%"))
        assertTrue(gnssMsg.contains("CID: 987654"))
        assertTrue(gnssMsg.contains("Altitude: 1380 m"))

        val lastGaspMsg = TrackExporter.buildEmergencyShareMessage(
            point = samplePoint("last_gasp"),
            cellTower = cell,
            batteryPercent = 9
        )
        assertTrue(lastGaspMsg.contains("(Last-Gasp Beacon)"))
        assertTrue(lastGaspMsg.contains("CID: 987654"))

        val cellularMsg = TrackExporter.buildEmergencyShareMessage(
            point = samplePoint("cellular"),
            cellTower = cell,
            batteryPercent = 41
        )
        assertTrue(cellularMsg.contains("(Approx. Cell Mast)"))

        val cachedGnssMsg = TrackExporter.buildEmergencyShareMessage(
            point = samplePoint("cached_gps"),
            cellTower = cell,
            batteryPercent = 27
        )
        assertTrue(cachedGnssMsg.contains("(Cached Offline Fix)"))

        val cachedCellMsg = TrackExporter.buildEmergencyShareMessage(
            point = samplePoint("cached_cellular"),
            cellTower = cell,
            batteryPercent = 22
        )
        assertTrue(cachedCellMsg.contains("(Cached Cell Mast)"))

        val cellOnlyMsg = TrackExporter.buildEmergencyShareMessage(
            point = null,
            cellTower = cell,
            batteryPercent = 18
        )
        assertTrue(cellOnlyMsg.contains("Location unavailable (awaiting GPS lock)"))
        assertTrue(cellOnlyMsg.contains("Battery: 18%"))
        assertTrue(cellOnlyMsg.contains("MTN Uganda"))
        assertTrue(cellOnlyMsg.contains("CID: 987654"))
    }
}
