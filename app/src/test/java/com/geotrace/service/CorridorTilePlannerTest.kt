package com.geotrace.service

import com.geotrace.model.LocationPoint
import com.geotrace.model.MapTileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorridorTilePlannerTest {

    private fun pt(lat: Double, lon: Double, ts: Long = 1_700_000_000_000L): LocationPoint {
        return LocationPoint(
            latitude = lat,
            longitude = lon,
            accuracy = 5f,
            timestamp = ts,
            sessionId = "corridor-test"
        )
    }

    @Test
    fun `computeCorridorBoxes handles empty single and multi point trails`() {
        assertTrue(OfflineMapManager.computeCorridorBoxes(emptyList()).isEmpty())

        val single = OfflineMapManager.computeCorridorBoxes(listOf(pt(-0.5947, 30.6545)), halfWidthKm = 1.5)
        assertEquals(1, single.size)
        assertTrue(single.first().north > -0.5947)
        assertTrue(single.first().south < -0.5947)
        assertTrue(single.first().east > 30.6545)
        assertTrue(single.first().west < 30.6545)
    }

    @Test
    fun `estimateCorridorTilesAndMb uses fewer tiles along diagonal route than full bounding box`() {
        // ~15 km diagonal route from southwest to northeast
        val routePoints = (0..15).map { step ->
            pt(
                lat = 0.2000 + step * 0.008,
                lon = 32.4000 + step * 0.008,
                ts = 1_700_000_000_000L + step * 60_000L
            )
        }
        val (corridorTiles, corridorMb, boxes) = OfflineMapManager.estimateCorridorTilesAndMb(
            points = routePoints,
            halfWidthKm = 1.0,
            tileType = MapTileType.HUMANITARIAN,
            minZoom = 12,
            maxZoom = 15
        )
        assertTrue(boxes.size >= 2)
        assertTrue(corridorTiles > 0)
        assertTrue(corridorMb > 0f)

        // Compare against naive full union rectangle at zoom 15
        val union = OfflineMapManager.unionBoundingBox(boxes)!!
        val fullUnionEstimate = OfflineMapManager.estimateCorridorTilesAndMb(
            points = listOf(
                pt(union.north, union.west),
                pt(union.north, union.east),
                pt(union.south, union.west),
                pt(union.south, union.east)
            ),
            halfWidthKm = 5.0,
            tileType = MapTileType.HUMANITARIAN,
            minZoom = 12,
            maxZoom = 15
        ).first
        assertTrue(
            "Corridor ($corridorTiles) should stay smaller than wide bounding polygon ($fullUnionEstimate)",
            corridorTiles < fullUnionEstimate
        )
    }

    @Test
    fun `estimateCorridorTilesAndMb doubles tile count for Hybrid imagery plus label overlay`() {
        val trail = listOf(
            pt(-0.5947, 30.6545),
            pt(-0.6050, 30.6650)
        )
        val (satTiles, _, _) = OfflineMapManager.estimateCorridorTilesAndMb(
            points = trail,
            halfWidthKm = 1.5,
            tileType = MapTileType.SATELLITE,
            minZoom = 13,
            maxZoom = 15
        )
        val (hybridTiles, _, _) = OfflineMapManager.estimateCorridorTilesAndMb(
            points = trail,
            halfWidthKm = 1.5,
            tileType = MapTileType.HYBRID,
            minZoom = 13,
            maxZoom = 15
        )
        assertTrue(satTiles > 0)
        assertEquals(satTiles * 2, hybridTiles)
    }
}
