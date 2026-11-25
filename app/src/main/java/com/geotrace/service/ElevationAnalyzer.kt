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
    val distanceToEndMeters: Double,
    val targetLatitude: Double = 0.0,
    val targetLongitude: Double = 0.0
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

        // Guard against a cold-start GPS altitude spike on the very first point
        var workingPoints = elevPoints
        if (workingPoints.size >= 3) {
            val p0 = workingPoints[0]
            val p1 = workingPoints[1]
            val p2 = workingPoints[2]
            val dt01Ms = p1.timestamp - p0.timestamp
            val dt12Ms = p2.timestamp - p1.timestamp
            val pairAgree = abs(p2.altitude - p1.altitude) <= 45.0
            val p0Spike = abs(p0.altitude - p1.altitude) > 90.0 && abs(p0.altitude - p2.altitude) > 90.0
            if (p0.timestamp > 0L && p1.timestamp > 0L && dt01Ms in 400L..6000L && dt12Ms >= 0L && pairAgree && p0Spike) {
                workingPoints = workingPoints.drop(1)
            }
        }

        var minAlt = Double.POSITIVE_INFINITY
        var maxAlt = Double.NEGATIVE_INFINITY
        var ascent = 0.0
        var descent = 0.0

        var lastAcceptedPt = workingPoints.first()
        var anchorAlt = lastAcceptedPt.altitude
        var cumulativeDist = 0.0
        val rawSamples = mutableListOf<ElevationSample>()

        workingPoints.forEachIndexed { idx, pt ->
            val alt = pt.altitude
            if (idx > 0) {
                val stepDist = haversineMeters(
                    lastAcceptedPt.latitude,
                    lastAcceptedPt.longitude,
                    pt.latitude,
                    pt.longitude
                )
                // Only apply time-based vertical spike rejection when real advancing timestamps exist
                val rawDtMs = pt.timestamp - lastAcceptedPt.timestamp
                if (lastAcceptedPt.timestamp > 0L && pt.timestamp > 0L && rawDtMs in 400L..4000L) {
                    val dtSec = rawDtMs / 1000.0
                    val vertSpeed = abs(alt - lastAcceptedPt.altitude) / dtSec
                    if (abs(alt - lastAcceptedPt.altitude) > 90.0 && vertSpeed > 25.0 && stepDist <= 300.0) {
                        return@forEachIndexed
                    }
                }
                if (stepDist <= 5000.0) {
                    cumulativeDist += stepDist
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

            lastAcceptedPt = pt
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
     * Evaluates nearest segment along a route polyline (or reversed trail), cross-track deviation,
     * remaining distance along the polyline, and direct bearing to the next look-ahead route vertex.
     * Pass [previousPointIndex] to keep progress monotonic on out-and-back and figure-8 loops.
     */
    fun evaluateRouteProgress(
        currentLat: Double,
        currentLon: Double,
        routePoints: List<LocationPoint>,
        previousPointIndex: Int? = null
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
                distanceToEndMeters = d,
                targetLatitude = pt.latitude,
                targetLongitude = pt.longitude
            )
        }

        val lastSegIdx = routePoints.lastIndex - 1
        var bestSegIdx = 0
        var bestDistMeters = Double.POSITIVE_INFINITY

        if (previousPointIndex != null && previousPointIndex in 0..lastSegIdx) {
            val winStart = (previousPointIndex - 2).coerceAtLeast(0)
            val winEnd = (previousPointIndex + 25).coerceAtMost(lastSegIdx)
            var localBestIdx: Int = previousPointIndex
            var localBestDist = Double.POSITIVE_INFINITY
            for (i in winStart..winEnd) {
                val a = routePoints[i]
                val b = routePoints[i + 1]
                val segDist = pointToSegmentDistanceMeters(
                    currentLat, currentLon,
                    a.latitude, a.longitude,
                    b.latitude, b.longitude
                )
                if (segDist < localBestDist) {
                    localBestDist = segDist
                    localBestIdx = i
                }
            }
            if (localBestDist <= 75.0) {
                bestSegIdx = localBestIdx
                bestDistMeters = localBestDist
            } else {
                for (i in 0..lastSegIdx) {
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
                if (localBestDist - bestDistMeters < 15.0) {
                    bestSegIdx = localBestIdx
                    bestDistMeters = localBestDist
                }
            }
        } else {
            for (i in 0..lastSegIdx) {
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

        // Pick a look-ahead guide vertex at least 25m ahead so dense trails yield stable turn bearings
        var targetGuideIdx = (bestSegIdx + 1).coerceAtMost(routePoints.lastIndex)
        var lookAheadDist = haversineMeters(
            currentLat,
            currentLon,
            routePoints[targetGuideIdx].latitude,
            routePoints[targetGuideIdx].longitude
        )
        while (targetGuideIdx < routePoints.lastIndex && lookAheadDist < 25.0) {
            val prevPt = routePoints[targetGuideIdx]
            targetGuideIdx++
            val curPt = routePoints[targetGuideIdx]
            val seg = haversineMeters(prevPt.latitude, prevPt.longitude, curPt.latitude, curPt.longitude)
            if (seg <= 5000.0) {
                lookAheadDist += seg
            }
        }

        val guideTarget = routePoints[targetGuideIdx]
        val endPt = routePoints.last()
        return RouteProgressMatch(
            nearestPointIndex = bestSegIdx,
            crossTrackDistanceMeters = bestDistMeters,
            remainingDistanceMeters = remaining,
            bearingToTargetDegrees = initialBearingDegrees(currentLat, currentLon, guideTarget.latitude, guideTarget.longitude),
            distanceToEndMeters = haversineMeters(currentLat, currentLon, endPt.latitude, endPt.longitude),
            targetLatitude = guideTarget.latitude,
            targetLongitude = guideTarget.longitude
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
