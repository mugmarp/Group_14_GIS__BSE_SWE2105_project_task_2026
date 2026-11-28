package com.geotrace.navigation

import com.geotrace.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationEngineAndParserTest {

    @Test
    fun polylineCodec_decodesValhallaPrecision6() {
        // Encode (0.0, 0.0) & (0.000100, 0.000100) at 1e6: delta=100 -> zig=200 -> 0b00110_01000 -> "gE"
        val encoded = "??gEgE"
        val decoded = PolylineCodec.decode(encoded, precision = 6)
        assertEquals(2, decoded.size)
        assertEquals(0.0, decoded[0].first, 1e-6)
        assertEquals(0.0, decoded[0].second, 1e-6)
        assertEquals(0.0001, decoded[1].first, 1e-6)
        assertEquals(0.0001, decoded[1].second, 1e-6)
    }

    @Test
    fun parseOpenRouteServiceGeoJson_extractsPointsStepsAndSummary() {
        val sampleOrsJson = """
            {
              "type": "FeatureCollection",
              "features": [
                {
                  "type": "Feature",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [30.6545, -0.6167, 1430.0],
                      [30.6555, -0.6167, 1431.0],
                      [30.6555, -0.6157, 1432.0]
                    ]
                  },
                  "properties": {
                    "summary": {
                      "distance": 222.5,
                      "duration": 24.0
                    },
                    "segments": [
                      {
                        "steps": [
                          {
                            "distance": 111.2,
                            "duration": 12.0,
                            "type": 11,
                            "instruction": "Head east on Campus Drive",
                            "name": "Campus Drive",
                            "way_points": [0, 1]
                          },
                          {
                            "distance": 111.3,
                            "duration": 12.0,
                            "type": 0,
                            "instruction": "Turn left onto Main Road",
                            "name": "Main Road",
                            "way_points": [1, 2]
                          }
                        ]
                      }
                    ]
                  }
                }
              ]
            }
        """.trimIndent()

        val routes = RoutingJsonParsers.parseOpenRouteServiceGeoJson(
            rawJson = sampleOrsJson,
            originLat = -0.6167,
            originLon = 30.6545,
            destinationName = "Main Gate",
            destinationLat = -0.6157,
            destinationLon = 30.6555,
            profile = NavProfile.DRIVING
        )

        assertEquals(1, routes.size)
        val r = routes.first()
        assertEquals(NavRouteSource.ONLINE_ORS, r.source)
        assertEquals(3, r.points.size)
        assertEquals(-0.6167, r.points[0].latitude, 1e-4)
        assertEquals(30.6545, r.points[0].longitude, 1e-4)
        assertEquals(222.5, r.totalDistanceMeters, 0.1)
        assertEquals(24.0, r.totalDurationSeconds, 0.1)
        assertTrue(r.steps.size >= 2)
        assertEquals(NavManeuverType.START, r.steps[0].maneuverType)
        assertEquals(NavManeuverType.LEFT, r.steps[1].maneuverType)
        assertEquals("Main Road", r.steps[1].streetName)
    }

    @Test
    fun parseValhallaResponse_extractsPrecision6PolylineAndSteps() {
        // Points: (0.0, 0.0) -> (0.001000, 0.000000) -> (0.001000, 0.001000)
        // Encode delta 1000 at 1e6: 2000 = 0b00001_11110_10000 -> chunks 16+32='o', 30+32='}', 1='@' => "o}@"
        // Encode delta 0: "?" -> P0=(?,?), P1=(o}@,?), P2=(?,o}@)
        val shape = "??o}@??o}@"
        val pts = PolylineCodec.decode(shape, precision = 6)
        assertEquals(3, pts.size)
        assertEquals(0.001, pts[1].first, 1e-6)
        assertEquals(0.001, pts[2].second, 1e-6)

        val valhallaJson = """
            {
              "trip": {
                "summary": {
                  "length": 0.222,
                  "time": 26
                },
                "legs": [
                  {
                    "shape": "$shape",
                    "maneuvers": [
                      {
                        "type": 1,
                        "instruction": "Drive north on Loop Rd.",
                        "verbal_pre_transition_instruction": "Drive north on Loop Rd.",
                        "street_names": ["Loop Rd"],
                        "length": 0.111,
                        "time": 13,
                        "begin_shape_index": 0,
                        "end_shape_index": 1
                      },
                      {
                        "type": 10,
                        "instruction": "Turn right onto Station Way.",
                        "verbal_pre_transition_instruction": "Turn right onto Station Way.",
                        "street_names": ["Station Way"],
                        "length": 0.111,
                        "time": 13,
                        "begin_shape_index": 1,
                        "end_shape_index": 2
                      },
                      {
                        "type": 4,
                        "instruction": "Arrive at Station.",
                        "length": 0.0,
                        "time": 0,
                        "begin_shape_index": 2,
                        "end_shape_index": 2
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()

        val routes = RoutingJsonParsers.parseValhallaResponse(
            rawJson = valhallaJson,
            originLat = 0.0,
            originLon = 0.0,
            destinationName = "Station",
            destinationLat = 0.001,
            destinationLon = 0.001,
            profile = NavProfile.DRIVING
        )

        assertEquals(1, routes.size)
        val route = routes.first()
        assertEquals(NavRouteSource.ONLINE_VALHALLA, route.source)
        assertEquals(3, route.points.size)
        assertEquals(222.0, route.totalDistanceMeters, 0.5)
        assertEquals(26.0, route.totalDurationSeconds, 0.5)
        assertEquals(3, route.steps.size)
        assertEquals(NavManeuverType.START, route.steps[0].maneuverType)
        assertEquals(NavManeuverType.RIGHT, route.steps[1].maneuverType)
        assertEquals(NavManeuverType.ARRIVE, route.steps[2].maneuverType)
        assertEquals("Station Way", route.steps[1].streetName)
    }

    @Test
    fun navigationEngine_snapsProgressesHysteresisOffRouteCooldownAndArrives() {
        // Build L-shaped route: (0.0, 0.0) -> (0.0, 0.004) East ~445m, then -> (0.003, 0.004) North ~333m
        val pts = listOf(
            LocationPoint(latitude = 0.0, longitude = 0.0, accuracy = 5f, sessionId = "test_route"),
            LocationPoint(latitude = 0.0, longitude = 0.002, accuracy = 5f, sessionId = "test_route"),
            LocationPoint(latitude = 0.0, longitude = 0.004, accuracy = 5f, sessionId = "test_route"),
            LocationPoint(latitude = 0.0015, longitude = 0.004, accuracy = 5f, sessionId = "test_route"),
            LocationPoint(latitude = 0.003, longitude = 0.004, accuracy = 5f, sessionId = "test_route")
        )
        val steps = listOf(
            NavStep(
                index = 0,
                maneuverType = NavManeuverType.START,
                instruction = "Head east toward Turn Corner",
                verbalPrompt = "Head east",
                streetName = "First Ave",
                distanceMeters = 445.0,
                durationSeconds = 40.0,
                beginPointIndex = 0,
                endPointIndex = 2
            ),
            NavStep(
                index = 1,
                maneuverType = NavManeuverType.LEFT,
                instruction = "Turn left onto Second St",
                verbalPrompt = "Turn left onto Second Street",
                streetName = "Second St",
                distanceMeters = 333.0,
                durationSeconds = 30.0,
                beginPointIndex = 2,
                endPointIndex = 4
            )
        )
        val route = NavRoute(
            id = "test_route",
            profile = NavProfile.DRIVING,
            originLat = 0.0,
            originLon = 0.0,
            destinationName = "Dest",
            destinationLat = 0.003,
            destinationLon = 0.004,
            points = pts,
            steps = steps,
            totalDistanceMeters = 778.0,
            totalDurationSeconds = 70.0,
            source = NavRouteSource.ONLINE_ORS
        )

        val engine = NavigationEngine()
        engine.setRoute(route, resetRerouteCooldown = true)

        // 1. Slightly off road by ~11m north while heading East (90 deg): should snap onto lat ~0.0
        val tick1 = engine.onLocationUpdate(
            location = LocationPoint(latitude = 0.0001, longitude = 0.001, accuracy = 5f, speed = 10f, bearing = 90f, sessionId = "nav"),
            voiceMode = VoiceGuidanceMode.VOICE_ON,
            nowMs = 1_000L
        )
        assertNotNull(tick1)
        assertTrue(tick1!!.state.isSnappedToRoad)
        assertFalse(tick1.state.isOffRoute)
        assertFalse(tick1.shouldRequestReroute)
        assertEquals(0.0, tick1.state.snappedLatitude, 1e-4)
        assertEquals(0, tick1.state.currentStepIndex)

        // 2. Progress past the corner onto leg 2 (stepIndex = 1)
        val tick2 = engine.onLocationUpdate(
            location = LocationPoint(latitude = 0.0008, longitude = 0.00405, accuracy = 5f, speed = 10f, bearing = 0f, sessionId = "nav"),
            voiceMode = VoiceGuidanceMode.VOICE_ON,
            nowMs = 5_000L
        )
        assertNotNull(tick2)
        assertTrue(tick2!!.state.isSnappedToRoad)
        assertEquals(1, tick2.state.currentStepIndex)

        // 3. Off-route hysteresis: ~90m east (lon = 0.0048) for 3 quick fixes (<5s) must NOT latch reroute
        for (i in 1..3) {
            val t = engine.onLocationUpdate(
                location = LocationPoint(latitude = 0.0008, longitude = 0.0048, accuracy = 6f, speed = 8f, bearing = 90f, sessionId = "nav"),
                voiceMode = VoiceGuidanceMode.VOICE_ON,
                nowMs = 10_000L + i * 500L
            )!!
            assertFalse("Fix $i should not immediately latch off-route", t.shouldRequestReroute)
        }

        // 4. 4th consecutive off-route fix latches off-route AND triggers reroute
        val tick4 = engine.onLocationUpdate(
            location = LocationPoint(latitude = 0.0008, longitude = 0.0048, accuracy = 6f, speed = 8f, bearing = 90f, sessionId = "nav"),
            voiceMode = VoiceGuidanceMode.VOICE_ON,
            nowMs = 12_000L
        )!!
        assertTrue(tick4.state.isOffRoute)
        assertTrue(tick4.shouldRequestReroute)

        // 5. Mark reroute triggered & verify 12s cooldown suppresses immediate re-trigger
        engine.markRerouteTriggered(nowMs = 12_000L)
        val tickCooldown = engine.onLocationUpdate(
            location = LocationPoint(latitude = 0.0008, longitude = 0.0049, accuracy = 6f, speed = 8f, bearing = 90f, sessionId = "nav"),
            voiceMode = VoiceGuidanceMode.VOICE_ON,
            nowMs = 18_000L
        )!!
        assertTrue(tickCooldown.state.isOffRoute)
        assertFalse(tickCooldown.shouldRequestReroute)

        // 6. Returning mid-route within 30m clears off-route latch before arrival
        val tickRejoined = engine.onLocationUpdate(
            location = LocationPoint(latitude = 0.0018, longitude = 0.0040, accuracy = 5f, speed = 8f, bearing = 0f, sessionId = "nav"),
            voiceMode = VoiceGuidanceMode.VOICE_ON,
            nowMs = 22_000L
        )!!
        assertFalse(tickRejoined.state.isOffRoute)
        assertFalse(tickRejoined.state.hasArrived)

        // 7. Reaching <=28m of destination detects arrival
        val tickArrived = engine.onLocationUpdate(
            location = LocationPoint(latitude = 0.0029, longitude = 0.0040, accuracy = 5f, speed = 4f, bearing = 0f, sessionId = "nav"),
            voiceMode = VoiceGuidanceMode.VOICE_ON,
            nowMs = 25_000L
        )!!
        assertFalse(tickArrived.state.isOffRoute)
        assertTrue(tickArrived.state.hasArrived)
    }

    @Test
    fun navigationEngine_usesDistanceAwareWindowOverDensePolylineSegments() {
        // Construct a dense U-turn route within 600m total distance (~373m to target) over >65 segments:
        // - Seg 0 goes South ~167m along lon=0.00065 (passing ~39m east of the northbound return point)
        // - Seg 1 goes West ~39m to lon=0.00030
        // - Segs 2..76 head North in ~2.4m steps (75 dense segments, total ~183m) past (0.0020, 0.00030).
        // A fixed [+40] window from seg 0 stops around seg 40 (~100m up the return leg), sees seg 0 at 39m
        // (<=60m so global fallback never triggers), and stays stuck at seg 0.
        // The distance-aware (>=600m) window reaches seg ~70 (~373m) directly with <2m cross-track error.
        val pts = mutableListOf<LocationPoint>()
        pts.add(LocationPoint(latitude = 0.0020, longitude = 0.00065, accuracy = 5f, sessionId = "dense"))
        pts.add(LocationPoint(latitude = 0.0005, longitude = 0.00065, accuracy = 5f, sessionId = "dense"))
        pts.add(LocationPoint(latitude = 0.0005, longitude = 0.00030, accuracy = 5f, sessionId = "dense"))
        for (i in 1..75) {
            pts.add(
                LocationPoint(
                    latitude = 0.0005 + i * 0.000022,
                    longitude = 0.00030,
                    accuracy = 5f,
                    sessionId = "dense"
                )
            )
        }
        val route = NavRoute(
            id = "dense_route",
            profile = NavProfile.DRIVING,
            originLat = pts.first().latitude,
            originLon = pts.first().longitude,
            destinationName = "North End",
            destinationLat = pts.last().latitude,
            destinationLon = pts.last().longitude,
            points = pts,
            steps = listOf(
                NavStep(
                    index = 0,
                    maneuverType = NavManeuverType.START,
                    instruction = "Follow dense road",
                    verbalPrompt = "Follow dense road",
                    streetName = "Dense Rd",
                    distanceMeters = 390.0,
                    durationSeconds = 39.0,
                    beginPointIndex = 0,
                    endPointIndex = pts.lastIndex
                )
            ),
            totalDistanceMeters = 390.0,
            totalDurationSeconds = 39.0,
            source = NavRouteSource.ONLINE_ORS
        )

        val engine = NavigationEngine()
        engine.setRoute(route)

        // Initial lock near seg 0
        val t0 = engine.onLocationUpdate(
            LocationPoint(latitude = 0.00195, longitude = 0.00065, accuracy = 5f, speed = 10f, bearing = 180f, sessionId = "nav"),
            VoiceGuidanceMode.MUTED,
            nowMs = 1_000L
        )!!
        assertEquals(0, t0.state.nearestPointIndex)

        // Jump onto dense northbound leg (~373m along route, >60 segments ahead, 39m west of seg 0)
        val t1 = engine.onLocationUpdate(
            LocationPoint(latitude = 0.00200, longitude = 0.00030, accuracy = 5f, speed = 10f, bearing = 0f, sessionId = "nav"),
            VoiceGuidanceMode.MUTED,
            nowMs = 5_000L
        )!!
        assertTrue(
            "Expected distance-aware window to advance onto dense leg (>40 segments ahead), got ${t1.state.nearestPointIndex}",
            t1.state.nearestPointIndex >= 60
        )
        assertTrue(t1.state.crossTrackDistanceMeters < 2.0)
    }

    @Test
    fun navigationEngine_usesPerStepDurationWeightsForEta() {
        // Step 0: 500m in 50s (fast highway); Step 1: 500m in 200s (slow congested street). Total = 250s.
        val pts = listOf(
            LocationPoint(latitude = 0.0, longitude = 0.0, accuracy = 5f, sessionId = "eta"),
            LocationPoint(latitude = 0.0, longitude = 0.0044915, accuracy = 5f, sessionId = "eta"),
            LocationPoint(latitude = 0.0, longitude = 0.0089830, accuracy = 5f, sessionId = "eta")
        )
        val route = NavRoute(
            id = "eta_route",
            profile = NavProfile.DRIVING,
            originLat = 0.0,
            originLon = 0.0,
            destinationName = "Center",
            destinationLat = 0.0,
            destinationLon = 0.0089830,
            points = pts,
            steps = listOf(
                NavStep(0, NavManeuverType.START, "Fast highway", "Fast highway", "Hwy 1", 500.0, 50.0, 0, 1),
                NavStep(1, NavManeuverType.STRAIGHT, "Slow street", "Slow street", "Main St", 500.0, 200.0, 1, 2)
            ),
            totalDistanceMeters = 1000.0,
            totalDurationSeconds = 250.0,
            source = NavRouteSource.ONLINE_ORS
        )
        val engine = NavigationEngine()
        engine.setRoute(route)

        // Halfway along fast step (lon = 0.00224575, stopped/slow speed < 2 m/s so speed blend doesn't mask plannedRemSec)
        // Remaining time should be ~25s (half of fast step) + 200s (full slow step) = ~225s (NOT uniform 0.75 * 250 = 187.5s)
        val tick = engine.onLocationUpdate(
            LocationPoint(latitude = 0.0, longitude = 0.00224575, accuracy = 5f, speed = 0.5f, bearing = 90f, sessionId = "nav"),
            VoiceGuidanceMode.MUTED,
            nowMs = 1_000L
        )!!
        assertEquals(225.0, tick.state.remainingDurationSeconds, 5.0)
    }
}
