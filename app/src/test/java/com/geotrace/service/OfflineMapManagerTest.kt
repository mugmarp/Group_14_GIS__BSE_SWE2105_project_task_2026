package com.geotrace.service

import com.geotrace.model.LocationPoint
import com.geotrace.model.MapTileType
import com.geotrace.model.OfflineMapRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.osmdroid.util.MapTileIndex

class OfflineMapManagerTest {

    private fun region(
        id: String,
        tileType: MapTileType,
        north: Double,
        south: Double,
        east: Double,
        west: Double,
        minZoom: Int = 12,
        maxZoom: Int = 14
    ) = OfflineMapRegion(
        id = id,
        name = id,
        northLat = north,
        southLat = south,
        eastLng = east,
        westLng = west,
        tileType = tileType,
        minZoom = minZoom,
        maxZoom = maxZoom,
        tileCount = 10,
        estimatedSizeMb = 0.3f
    )

    @Test
    fun offlineMapRegion_centerLngHandlesAntimeridianWrap() {
        val normal = region("n", MapTileType.SATELLITE, 1.0, 0.0, 31.0, 30.0)
        assertEquals(30.5, normal.centerLng, 0.0001)

        val wrappedWest = region("w", MapTileType.SATELLITE, 1.0, -1.0, east = -176.0, west = 178.0)
        assertEquals(-179.0, wrappedWest.centerLng, 0.0001)

        val wrappedExact = region("e", MapTileType.SATELLITE, 1.0, -1.0, east = -179.0, west = 179.0)
        assertEquals(180.0, wrappedExact.centerLng, 0.0001)
    }

    @Test
    fun maxZoomFor_capsTopoAt17AndClampsCorridorEstimates() {
        assertEquals(17, OfflineMapManager.maxZoomFor(MapTileType.TOPO))
        assertEquals(19, OfflineMapManager.maxZoomFor(MapTileType.SATELLITE))
        assertEquals(19, OfflineMapManager.maxZoomFor(MapTileType.HYBRID))

        val route = listOf(
            LocationPoint(latitude = -0.616, longitude = 30.650, accuracy = 5f, speed = 1f, altitude = 1400.0, bearing = 0f, provider = "gps", timestamp = 1L, sessionId = "s1"),
            LocationPoint(latitude = -0.610, longitude = 30.658, accuracy = 5f, speed = 1f, altitude = 1410.0, bearing = 45f, provider = "gps", timestamp = 2L, sessionId = "s1")
        )
        val (tilesAt19, _, _) = OfflineMapManager.estimateCorridorTilesAndMb(route, 1.0, MapTileType.TOPO, 15, 19)
        val (tilesAt17, _, _) = OfflineMapManager.estimateCorridorTilesAndMb(route, 1.0, MapTileType.TOPO, 15, 17)
        assertEquals(tilesAt17, tilesAt19)
        assertTrue(tilesAt17 > 0)
    }

    @Test
    fun coveredTileTypesForViewport_includesHybridSatelliteAndAntimeridian() {
        val hybridKampala = region("hyb", MapTileType.HYBRID, 0.40, 0.25, 32.65, 32.50)
        val fijiDateLine = region("fiji", MapTileType.TOPO, -16.0, -17.0, east = -179.0, west = 179.0)

        val inKampala = OfflineMapManager.coveredTileTypesForViewport(
            regions = listOf(hybridKampala, fijiDateLine),
            viewNorth = 0.36,
            viewSouth = 0.30,
            viewEast = 32.60,
            viewWest = 32.55
        )
        assertEquals(setOf(MapTileType.HYBRID, MapTileType.SATELLITE), inKampala)

        val emptyOcean = OfflineMapManager.coveredTileTypesForViewport(
            regions = listOf(hybridKampala, fijiDateLine),
            viewNorth = 10.0,
            viewSouth = 9.0,
            viewEast = 10.0,
            viewWest = 9.0
        )
        assertTrue(emptyOcean.isEmpty())

        val eastSideDateLine = OfflineMapManager.coveredTileTypesForViewport(
            regions = listOf(fijiDateLine),
            viewNorth = -16.2,
            viewSouth = -16.8,
            viewEast = -179.2,
            viewWest = -179.8
        )
        assertEquals(setOf(MapTileType.TOPO), eastSideDateLine)
    }

    @Test
    fun collectExclusiveTileIndicesForRegion_preservesSharedProviderTilesAndSplitsAntimeridian() {
        val targetHybrid = region("t", MapTileType.HYBRID, 0.36, 0.30, 32.60, 32.54, minZoom = 12, maxZoom = 12)
        val sharedSatellite = region("s", MapTileType.SATELLITE, 0.38, 0.28, 32.62, 32.52, minZoom = 12, maxZoom = 12)

        // Exact overlap with SATELLITE: EsriWorldImagery base tiles are shared -> preserved (baseTileIndices empty),
        // while EsriReferenceLabels overlay tiles belong exclusively to HYBRID -> queued for deletion.
        val sharedPlan = OfflineMapManager.collectExclusiveTileIndicesForRegion(
            target = targetHybrid,
            remainingRegions = listOf(sharedSatellite)
        )
        assertTrue(sharedPlan.baseTileIndices.isEmpty())
        assertTrue(sharedPlan.overlayTileIndices.isNotEmpty())

        // Antimeridian crossing region must include tiles from both x == 4095 (east of +179) and x == 0 (west of -179) at Z12
        val wrapped = region("wrap", MapTileType.TOPO, 1.0, 0.5, east = -179.5, west = 179.5, minZoom = 12, maxZoom = 12)
        val wrapPlan = OfflineMapManager.collectExclusiveTileIndicesForRegion(
            target = wrapped,
            remainingRegions = emptyList()
        )
        assertTrue(wrapPlan.overlayTileIndices.isEmpty())
        val xs = wrapPlan.baseTileIndices.map { MapTileIndex.getX(it) }.toSet()
        assertTrue(xs.contains(4095))
        assertTrue(xs.contains(0))
        assertFalse(xs.contains(2048))
    }

    @Test
    fun corridorRegion_usesStripBoxesForCoverageAndDeletion() {
        val corridor = OfflineMapRegion(
            id = "corr",
            name = "Diagonal Trail",
            northLat = 1.00,
            southLat = 0.00,
            eastLng = 31.00,
            westLng = 30.00,
            tileType = MapTileType.TOPO,
            minZoom = 12,
            maxZoom = 12,
            tileCount = 4,
            estimatedSizeMb = 0.2f,
            isCorridor = true,
            corridorBoxes = listOf(
                com.geotrace.model.OfflineCorridorBox(north = 0.15, south = 0.00, east = 30.15, west = 30.00),
                com.geotrace.model.OfflineCorridorBox(north = 1.00, south = 0.85, east = 31.00, west = 30.85)
            )
        )

        // Top-left corner (0.95, 30.05) is inside outer bbox but outside strip sub-boxes -> NOT covered
        val cornerCoverage = OfflineMapManager.coveredTileTypesForViewport(
            regions = listOf(corridor),
            viewNorth = 0.98,
            viewSouth = 0.90,
            viewEast = 30.10,
            viewWest = 30.02
        )
        assertTrue(cornerCoverage.isEmpty())

        // On-strip sub-box -> covered
        val stripCoverage = OfflineMapManager.coveredTileTypesForViewport(
            regions = listOf(corridor),
            viewNorth = 0.12,
            viewSouth = 0.05,
            viewEast = 30.10,
            viewWest = 30.02
        )
        assertEquals(setOf(MapTileType.TOPO), stripCoverage)

        // Deletion plan must stay compact around the two strip boxes (far fewer tiles than whole 1x1 deg square)
        val plan = OfflineMapManager.collectExclusiveTileIndicesForRegion(
            target = corridor,
            remainingRegions = emptyList()
        )
        assertTrue(plan.baseTileIndices.isNotEmpty())
        assertTrue(plan.baseTileIndices.size < 30)
    }
}
