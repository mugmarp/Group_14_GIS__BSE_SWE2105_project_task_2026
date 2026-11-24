package com.geotrace.service

import com.geotrace.model.LocationPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class ElevationSample(
    val distanceMeters: Double,
    val altitudeMeters: Double
)

data class ElevationSummary(
    val hasValidElevation: Boolean,
    val minAltitudeMeters: Double,
    val maxAltitudeMeters: Double,
    val totalAscentMeters: Double,
    val totalDescentMeters: Double,
    val startAltitudeMeters: Double,
    val endAltitudeMeters: Double,
    val profileSamples: List<ElevationSample>
) {
    val netGainMeters: Double get() = endAltitudeMeters - startAltitudeMeters
}

data class RouteProgressMatch(
    val nearestPointIndex: Int,
    val crossTrackDistanceMeters: Double,
    val remainingDistanceMeters: Double,
    val bearingToTargetDegrees: Float,
    val distanceToEndMeters: Double
)

object ElevationAnalyzer {

    /**
     * Computes filtered ascent/descent & chart samples using a vertical hysteresis deadband
     * to reject GPS multipath altitude jitter under tree canopy or cliffs.
     */
    fun analyze(
        points: List<LocationPoint>,
        verticalHysteresisMeters: Double = 4.0,
        maxProfileSamples: Int = 72
    ): ElevationSummary {
        val elevPoints = points.filter {
            it.altitude.isFinite() && it.altitude in -500.0..9000.0 &&
                (it.altitude != 0.0 || it.latitude != 0.0)
        }
        // Treat all-zero altitudes (e.g. imported 2D tracks) as unavailable
        val nonZeroAltCount = elevPoints.count { abs(it.altitude) > 0.01 }
        if (elevPoints.isEmpty() || nonZeroAltCount == 0) {
            return ElevationSummary(
                hasValidElevation = false,
                minAltitudeMeters = 0.0,
                maxAltitudeMeters = 0.0,
                totalAscentMeters = 0.0,
                totalDescentMeters = 0.0,
                startAltitudeMeters = 0.0,
                endAltitudeMeters = 0.0,
                profileSamples = emptyList()
            )
        }

        var minAlt = Double.POSITIVE_INFINITY
        var maxAlt = Double.NEGATIVE_INFINITY
        var ascent = 0.0
        var descent = 0.0

        var anchorAlt = elevPoints.first().altitude
        var lastValidAlt = anchorAlt
        var lastValidTimeMs = elevPoints.first().timestamp
        var cumulativeDist = 0.0
        val rawSamples = mutableListOf<ElevationSample>()

        elevPoints.forEachIndexed { idx, pt ->
            val alt = pt.altitude
            if (idx > 0) {
                val prev = elevPoints[idx - 1]
                val stepDist = haversineMeters(prev.latitude, prev.longitude, pt.latitude, pt.longitude)
                if (stepDist <= 5000.0) {
                    cumulativeDist += stepDist
                }
                // Reject extreme unphysical vertical GPS spikes (>90m in <= 3 seconds)
                val dtSec = ((pt.timestamp - lastValidTimeMs) / 1000.0).coerceAtLeast(1.0)
                val vertSpeed = abs(alt - lastValidAlt) / dtSec
                if (dtSec <= 4.0 && abs(alt - lastValidAlt) > 90.0 && vertSpeed > 25.0) {
                    return@forEachIndexed
                }
            }

            if (alt < minAlt) minAlt = alt
            if (alt > maxAlt) maxAlt = alt

            val deltaFromAnchor = alt - anchorAlt
            if (deltaFromAnchor >= verticalHysteresisMeters) {
                ascent += deltaFromAnchor
                anchorAlt = alt
            } else if (deltaFromAnchor <= -verticalHysteresisMeters) {
                descent += abs(deltaFromAnchor)
                anchorAlt = alt
            }

            lastValidAlt = alt
            lastValidTimeMs = pt.timestamp
            rawSamples.add(ElevationSample(distanceMeters = cumulativeDist, altitudeMeters = alt))
        }

        val sampled = downsample(rawSamples, maxProfileSamples)
        return ElevationSummary(
            hasValidElevation = true,
            minAltitudeMeters = if (minAlt.isFinite()) minAlt else 0.0,
            maxAltitudeMeters = if (maxAlt.isFinite()) maxAlt else 0.0,
            totalAscentMeters = ascent,
            totalDescentMeters = descent,
            startAltitudeMeters = rawSamples.firstOrNull()?.altitudeMeters ?: 0.0,
            endAltitudeMeters = rawSamples.lastOrNull()?.altitudeMeters ?: 0.0,
            profileSamples = sampled
        )
    }

    /**
     * Evaluates nearest point along a route polyline (or reversed trail), cross-track deviation,
     * remaining distance along the polyline, and direct bearing to the route destination.
     */
    fun evaluateRouteProgress(
        currentLat: Double,
        currentLon: Double,
        routePoints: List<LocationPoint>
    ): RouteProgressMatch? {
        if (routePoints.isEmpty()) return null
        if (routePoints.size == 1) {
            val pt = routePoints.first()
            val d = haversineMeters(currentLat, currentLon, pt.latitude, pt.longitude)
            val b = initialBearingDegrees(currentLat, currentLon, pt.latitude, pt.longitude)
            return RouteProgressMatch(
                nearestPointIndex = 0,
                crossTrackDistanceMeters = d,
                remainingDistanceMeters = d,
                bearingToTargetDegrees = b,
                distanceToEndMeters = d
            )
        }

        var bestSegIdx = 0
        var bestDistMeters = Double.POSITIVE_INFINITY
        for (i in 0 until routePoints.size - 1) {
            val a = routePoints[i]
            val b = routePoints[i + 1]
            val segDist = pointToSegmentDistanceMeters(
                currentLat, currentLon,
                a.latitude, a.longitude,
                b.latitude, b.longitude
            )
            if (segDist < bestDistMeters) {
                bestDistMeters = segDist
                bestSegIdx = i
            }
        }

        // Remaining route distance from the snapped segment forward to the end
        val nextVertex = routePoints[(bestSegIdx + 1).coerceAtMost(routePoints.lastIndex)]
        var remaining = haversineMeters(currentLat, currentLon, nextVertex.latitude, nextVertex.longitude)
        for (i in (bestSegIdx + 1) until routePoints.lastIndex) {
            val p1 = routePoints[i]
            val p2 = routePoints[i + 1]
            val step = haversineMeters(p1.latitude, p1.longitude, p2.latitude, p2.longitude)
            if (step <= 5000.0) {
                remaining += step
            }
        }

        val targetGuideIdx = (bestSegIdx + 1).coerceAtMost(routePoints.lastIndex)
        val guideTarget = routePoints[targetGuideIdx]
        val endPt = routePoints.last()
        return RouteProgressMatch(
            nearestPointIndex = bestSegIdx,
            crossTrackDistanceMeters = bestDistMeters,
            remainingDistanceMeters = remaining,
            bearingToTargetDegrees = initialBearingDegrees(currentLat, currentLon, guideTarget.latitude, guideTarget.longitude),
            distanceToEndMeters = haversineMeters(currentLat, currentLon, endPt.latitude, endPt.longitude)
        )
    }

    fun initialBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x)).toFloat()
        return ((deg % 360f) + 360f) % 360f
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return r * 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    }

    private fun pointToSegmentDistanceMeters(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): Double {
        // Local tangent plane projection (meters) accurate for trail/road segment matching
        val refLatRad = Math.toRadians((aLat + bLat) * 0.5)
        val metersPerDegLat = 111132.92
        val metersPerDegLon = (111412.84 * cos(refLatRad)).coerceAtLeast(1.0)

        val px = (pLon - aLon) * metersPerDegLon
        val py = (pLat - aLat) * metersPerDegLat
        val bx = (bLon - aLon) * metersPerDegLon
        val by = (bLat - aLat) * metersPerDegLat

        val segLenSq = bx * bx + by * by
        if (segLenSq < 1e-6) {
            return sqrt(px * px + py * py)
        }
        val t = ((px * bx + py * by) / segLenSq).coerceIn(0.0, 1.0)
        val projX = t * bx
        val projY = t * by
        val dx = px - projX
        val dy = py - projY
        return sqrt(dx * dx + dy * dy)
    }

    private fun downsample(samples: List<ElevationSample>, maxPoints: Int): List<ElevationSample> {
        if (samples.size <= maxPoints || maxPoints < 2) return samples
        val result = ArrayList<ElevationSample>(maxPoints)
        val lastIdx = samples.lastIndex
        for (i in 0 until maxPoints) {
            val mappedIdx = ((i.toDouble() / (maxPoints - 1)) * lastIdx).toInt().coerceIn(0, lastIdx)
            result.add(samples[mappedIdx])
        }
        return result
    }
}
