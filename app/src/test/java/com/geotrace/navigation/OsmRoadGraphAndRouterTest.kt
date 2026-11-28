package com.geotrace.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class OsmRoadGraphAndRouterTest {

    private fun buildOverpassFixtureJson(): String {
        // Intersection graph:
        // Node 1 (0.0000, 0.0000) --(node 10 curve at 0.0005,0.0001)--> Node 2 (0.0010, 0.0000) [Main St, residential]
        // Node 2 (0.0010, 0.0000) --> Node 3 (0.0010, 0.0020) [Oak Ave, secondary, oneway=yes eastbound]
        // Node 3 (0.0010, 0.0020) --> Node 4 (0.0020, 0.0020) [Pine Rd, tertiary]
        // Parallel alt path: Node 2 -> Node 5 (0.0020, 0.0000) [Bypass Blvd, primary] -> Node 4 (0.0020, 0.0020) [Bypass Blvd, primary]
        // Walking shortcut footway: Node 1 (0.0000, 0.0000) <-> Node 4 (0.0020, 0.0020) [Park Path, footway]
        return """
        {
          "elements": [
            {"type":"node","id":1,"lat":0.0000,"lon":0.0000},
            {"type":"node","id":10,"lat":0.0005,"lon":0.0001},
            {"type":"node","id":2,"lat":0.0010,"lon":0.0000},
            {"type":"node","id":3,"lat":0.0010,"lon":0.0020},
            {"type":"node","id":4,"lat":0.0020,"lon":0.0020},
            {"type":"node","id":5,"lat":0.0020,"lon":0.0000},

            {"type":"way","id":101,"nodes":[1,10,2],"tags":{"highway":"residential","name":"Main St"}},
            {"type":"way","id":102,"nodes":[2,3],"tags":{"highway":"secondary","name":"Oak Ave","oneway":"yes","maxspeed":"50"}},
            {"type":"way","id":103,"nodes":[3,4],"tags":{"highway":"tertiary","name":"Pine Rd"}},
            {"type":"way","id":104,"nodes":[2,5,4],"tags":{"highway":"primary","name":"Bypass Blvd","maxspeed":"60"}},
            {"type":"way","id":105,"nodes":[1,4],"tags":{"highway":"footway","name":"Park Trail","surface":"gravel"}}
          ]
        }
        """.trimIndent()
    }

    @Test
    fun parseAndBinaryRoundtrip_preservesTopologyCurvesAndTags() {
        val parsed = OsmRoadGraphCodec.parseOverpassHighwayJson(buildOverpassFixtureJson())
        // Nodes 10 (Main St) and 5 (Bypass Blvd) are degree-2 interior shape nodes compressed into interiorLats/Lons!
        assertEquals(4, parsed.vertices.size)
        assertTrue(parsed.edges.isNotEmpty())

        val curvedEdge = parsed.edges.firstOrNull { it.streetName == "Main St" && it.interiorLats.isNotEmpty() }
        assertNotNull("Expected compressed curve interior point on Main St", curvedEdge)
        assertEquals(1, curvedEdge!!.interiorLats.size)
        assertEquals(0.0005, curvedEdge.interiorLats[0], 1e-6)
        val bypassEdge = parsed.edges.firstOrNull { it.streetName == "Bypass Blvd" && it.interiorLats.isNotEmpty() }
        assertNotNull("Expected compressed corner point (Node 5) on Bypass Blvd", bypassEdge)

        val baos = ByteArrayOutputStream()
        OsmRoadGraphCodec.writeBinary(parsed, baos)
        val bytes = baos.toByteArray()
        assertTrue("Binary pack should be non-empty", bytes.size > 32)

        val reloaded = OsmRoadGraphCodec.readBinary(ByteArrayInputStream(bytes))
        assertEquals(parsed.vertices.size, reloaded.vertices.size)
        assertEquals(parsed.edges.size, reloaded.edges.size)
        for (i in parsed.edges.indices) {
            assertEquals(parsed.edges[i].streetName, reloaded.edges[i].streetName)
            assertEquals(parsed.edges[i].roadClass, reloaded.edges[i].roadClass)
            assertEquals(parsed.edges[i].flags, reloaded.edges[i].flags)
            assertEquals(parsed.edges[i].maxSpeedKmh, reloaded.edges[i].maxSpeedKmh)
            assertEquals(parsed.edges[i].interiorLats.size, reloaded.edges[i].interiorLats.size)
        }
    }

    @Test
    fun router_respectsDrivingVsWalkingAndExtractsRightTurnManeuver() {
        val graph = OsmRoadGraphCodec.parseOverpassHighwayJson(buildOverpassFixtureJson())

        // Driving from near Node 1 up Main St (northbound ~0 deg), turning RIGHT onto Oak Ave (eastbound ~90 deg) -> Node 3 (0.0010, 0.0020)
        val driveRes = LocalOsmGraphRouter.route(
            graph = graph,
            originLat = 0.0001,
            originLon = 0.0000,
            destinationName = "Oak Station",
            destinationLat = 0.0010,
            destinationLon = 0.0019,
            profile = NavProfile.DRIVING,
            includeAlternatives = false
        )
        assertTrue(driveRes.isSuccess)
        val driveRoute = driveRes.getOrThrow().first()
        assertEquals(NavRouteSource.OFFLINE_OSM_GRAPH, driveRoute.source)
        assertTrue(driveRoute.steps.any { it.maneuverType == NavManeuverType.START })
        assertTrue(driveRoute.steps.any { it.maneuverType == NavManeuverType.RIGHT && it.streetName == "Oak Ave" })
        assertEquals(NavManeuverType.ARRIVE, driveRoute.steps.last().maneuverType)

        // Driving AGAINST Oak Ave oneway=yes (Node 3 -> Node 2) must route via Bypass Blvd instead!
        val reverseOneWayDrive = LocalOsmGraphRouter.route(
            graph = graph,
            originLat = 0.0010,
            originLon = 0.0019,
            destinationName = "Main Junction",
            destinationLat = 0.0010,
            destinationLon = 0.0001,
            profile = NavProfile.DRIVING,
            includeAlternatives = false
        ).getOrThrow().first()
        assertTrue(
            "Driving must detour via Pine Rd / Bypass Blvd instead of violating oneway=yes on Oak Ave",
            reverseOneWayDrive.steps.any { it.streetName == "Bypass Blvd" || it.streetName == "Pine Rd" }
        )

        // Walking from Node 1 -> Node 4 takes the shorter Park Trail footway directly
        val walkRoute = LocalOsmGraphRouter.route(
            graph = graph,
            originLat = 0.00005,
            originLon = 0.00005,
            destinationName = "Pine Corner",
            destinationLat = 0.00195,
            destinationLon = 0.00195,
            profile = NavProfile.WALKING,
            includeAlternatives = false
        ).getOrThrow().first()
        assertTrue(
            "Walking should take Park Trail footway shortcut",
            walkRoute.steps.any { it.streetName == "Park Trail" }
        )
    }

    @Test
    fun router_generatesAlternativeRouteWhenDistinctCorridorExists() {
        val graph = OsmRoadGraphCodec.parseOverpassHighwayJson(buildOverpassFixtureJson())
        // Node 2 -> Node 4 has two routes: Oak Ave -> Pine Rd vs Bypass Blvd
        val result = LocalOsmGraphRouter.route(
            graph = graph,
            originLat = 0.0010,
            originLon = 0.0001,
            destinationName = "Node 4",
            destinationLat = 0.00195,
            destinationLon = 0.00195,
            profile = NavProfile.DRIVING,
            includeAlternatives = true
        )
        assertTrue(result.isSuccess)
        val routes = result.getOrThrow()
        assertTrue("Expected primary + distinct alternative route, got ${routes.size}", routes.size >= 2)
    }

    @Test
    fun mergeGraphs_stitchesAdjacentPacksAcrossSharedOsmNodeId() {
        val packWestJson = """
        {
          "elements": [
            {"type":"node","id":1001,"lat":1.0000,"lon":1.0000},
            {"type":"node","id":1002,"lat":1.0000,"lon":1.0020},
            {"type":"way","id":2001,"nodes":[1001,1002],"tags":{"highway":"primary","name":"West Highway"}}
          ]
        }
        """.trimIndent()
        val packEastJson = """
        {
          "elements": [
            {"type":"node","id":1002,"lat":1.0000,"lon":1.0020},
            {"type":"node","id":1003,"lat":1.0020,"lon":1.0020},
            {"type":"way","id":2002,"nodes":[1002,1003],"tags":{"highway":"primary","name":"East Highway"}}
          ]
        }
        """.trimIndent()

        val gWest = OsmRoadGraphCodec.parseOverpassHighwayJson(packWestJson)
        val gEast = OsmRoadGraphCodec.parseOverpassHighwayJson(packEastJson)
        val stitched = OsmRoadGraphCodec.mergeGraphs(listOf(gWest, gEast))

        assertEquals(3, stitched.vertices.size)
        val crossBorder = LocalOsmGraphRouter.route(
            graph = stitched,
            originLat = 1.0000,
            originLon = 1.0001,
            destinationName = "East Terminal",
            destinationLat = 1.0019,
            destinationLon = 1.0020,
            profile = NavProfile.DRIVING,
            includeAlternatives = false
        )
        assertTrue("Route should stitch seamlessly across shared node 1002", crossBorder.isSuccess)
        val route = crossBorder.getOrThrow().first()
        assertTrue(route.steps.any { it.maneuverType == NavManeuverType.LEFT && it.streetName == "East Highway" })

        // Verify NavigationEngine consumes offline graph steps cleanly without step-index mismatch
        val engine = NavigationEngine()
        engine.setRoute(route)
        val tick = engine.onLocationUpdate(
            com.geotrace.model.LocationPoint(
                latitude = 1.0000,
                longitude = 1.0002,
                accuracy = 4f,
                speed = 10f,
                bearing = 90f,
                timestamp = 1000L,
                sessionId = "test"
            ),
            VoiceGuidanceMode.VOICE_ON
        )
        assertNotNull(tick)
        assertEquals(0, tick!!.state.currentStepIndex)
        assertEquals(NavManeuverType.LEFT, tick.state.nextStep?.maneuverType)
    }

    @Test
    fun mergeGraphs_preservesParallelEdgesWithSameWayIdAndEndpoints() {
        // Simulate a horseshoe/loop split where way 9001 produces two distinct edges between vertex 1 and 2
        // (e.g., short 50m chord vs longer 220m curved loop) and merge across two packs.
        val v1 = RoadVertex(1L, 0.0, 0.0)
        val v2 = RoadVertex(2L, 0.0, 0.0005)
        val chordFwd = RoadEdge(
            fromVertex = 0,
            toVertex = 1,
            wayOsmId = 9001L,
            streetName = "Loop Crescent",
            roadClass = RoadClass.RESIDENTIAL,
            maxSpeedKmh = 30,
            distanceMeters = 55f,
            flags = OsmRoadFlags.CAR or OsmRoadFlags.WALK,
            interiorLats = DoubleArray(0),
            interiorLons = DoubleArray(0)
        )
        val loopFwd = RoadEdge(
            fromVertex = 0,
            toVertex = 1,
            wayOsmId = 9001L,
            streetName = "Loop Crescent",
            roadClass = RoadClass.RESIDENTIAL,
            maxSpeedKmh = 30,
            distanceMeters = 240f,
            flags = OsmRoadFlags.CAR or OsmRoadFlags.WALK,
            interiorLats = doubleArrayOf(0.001, 0.001),
            interiorLons = doubleArrayOf(0.0, 0.0005)
        )

        val g1 = OsmRoadGraphData(listOf(v1, v2), listOf(chordFwd))
        val g2 = OsmRoadGraphData(listOf(v1, v2), listOf(loopFwd))
        val gDuplicate = OsmRoadGraphData(listOf(v1, v2), listOf(chordFwd))

        val merged = OsmRoadGraphCodec.mergeGraphs(listOf(g1, g2, gDuplicate))
        assertEquals(2, merged.vertices.size)
        assertEquals("Distinct loop and chord edges on same way must both survive merge", 2, merged.edges.size)
    }

    @Test
    fun buildCorridorQueryWindows_splitsLongDiagonalCorridorIntoTightWindows() {
        val boxes = (0 until 20).map { i ->
            val lat = 10.0 + i * 0.015
            val lon = 20.0 + i * 0.015
            com.geotrace.service.OfflineMapManager.CorridorTileBox(
                north = lat + 0.008,
                south = lat - 0.008,
                east = lon + 0.008,
                west = lon - 0.008
            )
        }
        val windows = OfflineRoutingPackManager.buildCorridorQueryWindows(boxes)
        assertTrue("Long diagonal corridor should be split into >= 5 tight windows, got ${windows.size}", windows.size >= 5)
        for (win in windows) {
            val latSpan = win.north - win.south
            val lonSpan = win.east - win.west
            assertTrue("Expected tight window latSpan <= 0.12, got $latSpan", latSpan <= 0.12)
            assertTrue("Expected tight window lonSpan <= 0.12, got $lonSpan", lonSpan <= 0.12)
        }
    }
}
