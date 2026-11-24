package com.geotrace.service

import com.geotrace.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ElevationAnalyzerTest {

    private fun pt(
        lat: Double,
        lon: Double,
        alt: Double,
        timestamp: Long
    ) = LocationPoint(
        latitude = lat,
        longitude = lon,
        accuracy = 5f,
        altitude = alt,
        timestamp = timestamp,
        sessionId = "elev-test"
    )

    @Test
    fun `analyze filters small multipath jitter and rejects impossible vertical spikes`() {
        val points = listOf(
            pt(0.0010, 32.0000, 1200.0, 1_000L),
            // +1.5m and -1.8m jitter under canopy: inside +-4m deadband -> ignored
            pt(0.0012, 32.0000, 1201.5, 5_000L),
            pt(0.0014, 32.0000, 1199.7, 10_000L),
            // Impossible GPS spike (+150m in 2s) -> dropped completely
            pt(0.0015, 32.0000, 1350.0, 12_000L),
            // Real steady climb +25m -> counted
            pt(0.0020, 32.0000, 1225.0, 30_000L),
            // Real steady descent -10m -> counted
            pt(0.0025, 32.0000, 1215.0, 50_000L)
        )

        val summary = ElevationAnalyzer.analyze(points, verticalHysteresisMeters = 4.0)

        assertTrue(summary.hasValidElevation)
        assertEquals(25.0, summary.totalAscentMeters, 0.01)
        assertEquals(10.0, summary.totalDescentMeters, 0.01)
        assertEquals(1199.7, summary.minAltitudeMeters, 0.01)
        assertEquals(1225.0, summary.maxAltitudeMeters, 0.01)
        assertEquals(15.0, summary.netGainMeters, 0.01)
        assertEquals(5, summary.profileSamples.size)
    }

    @Test
    fun `analyze marks all-zero altitude tracks as unavailable`() {
        val flat2dPoints = listOf(
            pt(0.3476, 32.5825, 0.0, 1_000L),
            pt(0.3480, 32.5830, 0.0, 2_000L)
        )
        val summary = ElevationAnalyzer.analyze(flat2dPoints)
        assertFalse(summary.hasValidElevation)
        assertTrue(summary.profileSamples.isEmpty())
    }

    @Test
    fun `evaluateRouteProgress computes accurate cross-track distance and remaining distance`() {
        // Straight north-south path from (0.0, 32.0) to (0.02, 32.0) (~2.22km)
        val route = listOf(
            pt(0.000, 32.0, 1200.0, 1_000L),
            pt(0.010, 32.0, 1210.0, 2_000L),
            pt(0.020, 32.0, 1220.0, 3_000L)
        )

        // Position near midpoint (0.005, ~111m east of trail: 0.001 deg lon at equator ~111m)
        val match = ElevationAnalyzer.evaluateRouteProgress(
            currentLat = 0.005,
            currentLon = 32.001,
            routePoints = route
        )

        assertNotNull(match)
        assertEquals(0, match!!.nearestPointIndex)
        assertTrue("Cross-track expected ~111m, got ${match.crossTrackDistanceMeters}", match.crossTrackDistanceMeters in 105.0..118.0)
        // Remaining from (0.005, 32.001) to end ~1.67km
        assertTrue("Remaining expected ~1600-1750m, got ${match.remainingDistanceMeters}", match.remainingDistanceMeters in 1600.0..1750.0)
        // Bearing toward segment target (0.010, 32.0) from (0.005, 32.001) should be slightly west of north (~348 deg)
        assertTrue("Expected NNW bearing, got ${match.bearingToTargetDegrees}", match.bearingToTargetDegrees in 340f..359f)
    }
}
