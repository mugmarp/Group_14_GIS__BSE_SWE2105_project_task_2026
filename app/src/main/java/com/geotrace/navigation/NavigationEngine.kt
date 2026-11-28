package com.geotrace.navigation

import com.geotrace.model.LocationPoint
import com.geotrace.service.ElevationAnalyzer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Deterministic turn-by-turn navigation state machine:
 * - Forward-biased, heading-aware segment projection (road snapping)
 * - Monotonic maneuver progression & distance-to-next-turn countdown
 * - Hysteresis off-route detector with reroute cooldown guard
 * - Arrival detection & speed-adaptive ETA
 */
class NavigationEngine {

    companion object {
        const val SNAP_TOLERANCE_METERS = 28.0
        const val OFF_ROUTE_ENTER_METERS = 45.0
        const val OFF_ROUTE_EXIT_METERS = 30.0
        const val REQUIRED_CONSECUTIVE_OFF_ROUTE_FIXES = 4
        const val REQUIRED_OFF_ROUTE_DWELL_MS = 5_000L
        const val REROUTE_COOLDOWN_MS = 12_000L
        const val ARRIVAL_THRESHOLD_METERS = 28.0
    }

    private var activeRoute: NavRoute? = null
    private var cumulativeDistToPoint: DoubleArray = DoubleArray(0)
    private var lastMatchedSegIndex: Int = 0
    private var lastMatchedStepIndex: Int = 0
    private var offRouteFixCount: Int = 0
    private var offRouteStartedMs: Long = 0L
    private var lastRerouteRequestedMs: Long = 0L
    private var isOffRouteLatched: Boolean = false
    private var hasArrivedLatched: Boolean = false

    fun setRoute(route: NavRoute, resetRerouteCooldown: Boolean = false) {
        activeRoute = route
        cumulativeDistToPoint = buildCumulativeDistances(route.points)
        lastMatchedSegIndex = 0
        lastMatchedStepIndex = 0
        offRouteFixCount = 0
        offRouteStartedMs = 0L
        isOffRouteLatched = false
        hasArrivedLatched = false
        if (resetRerouteCooldown) {
            lastRerouteRequestedMs = 0L
        }
    }

    fun clear() {
        activeRoute = null
        cumulativeDistToPoint = DoubleArray(0)
        lastMatchedSegIndex = 0
        lastMatchedStepIndex = 0
        offRouteFixCount = 0
        offRouteStartedMs = 0L
        isOffRouteLatched = false
        hasArrivedLatched = false
    }

    fun markRerouteTriggered(nowMs: Long = System.currentTimeMillis()) {
        lastRerouteRequestedMs = nowMs
    }

    fun canTriggerReroute(nowMs: Long = System.currentTimeMillis()): Boolean {
        return (nowMs - lastRerouteRequestedMs) >= REROUTE_COOLDOWN_MS
    }

    data class EngineTickOutput(
        val state: ActiveNavigationState,
        val shouldRequestReroute: Boolean
    )

    fun onLocationUpdate(
        location: LocationPoint,
        voiceMode: VoiceGuidanceMode,
        isRerouting: Boolean = false,
        isOfflineRerouteBlocked: Boolean = false,
        rerouteStatusNote: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): EngineTickOutput? {
        val route = activeRoute ?: return null
        val pts = route.points
        if (pts.size < 2) return null

        val lastSegIdx = pts.lastIndex - 1
        val hasMovingBearing = location.speed >= 1.8f && location.bearing.isFinite()

        // 1. Forward-biased, distance-aware search window (>=80m back, >=600m forward, min [-3, +40] segments)
        val anchorDist = cumulativeDistToPoint.getOrElse(lastMatchedSegIndex) { 0.0 }
        var winStart = (lastMatchedSegIndex - 3).coerceAtLeast(0)
        while (winStart > 0 && (anchorDist - cumulativeDistToPoint[winStart]) < 80.0) {
            winStart--
        }
        var winEnd = (lastMatchedSegIndex + 40).coerceAtMost(lastSegIdx)
        while (winEnd < lastSegIdx && (cumulativeDistToPoint.getOrElse(winEnd) { anchorDist } - anchorDist) < 600.0) {
            winEnd++
        }
        val localMatch = findBestSegment(
            lat = location.latitude,
            lon = location.longitude,
            userBearing = if (hasMovingBearing) location.bearing else null,
            points = pts,
            startSeg = winStart,
            endSeg = winEnd
        )

        val chosenMatch = if (localMatch.distanceMeters <= 60.0) {
            localMatch
        } else {
            val globalMatch = findBestSegment(
                lat = location.latitude,
                lon = location.longitude,
                userBearing = if (hasMovingBearing) location.bearing else null,
                points = pts,
                startSeg = 0,
                endSeg = lastSegIdx
            )
            if (localMatch.distanceMeters - globalMatch.distanceMeters < 18.0) {
                localMatch
            } else {
                globalMatch
            }
        }

        lastMatchedSegIndex = chosenMatch.segIndex

        // 2. Remaining distance along polyline from snapped projection to destination
        val totalPolyDist = cumulativeDistToPoint.lastOrNull() ?: route.totalDistanceMeters
        val distToSegStart = cumulativeDistToPoint.getOrElse(chosenMatch.segIndex) { 0.0 }
        val segLen = (cumulativeDistToPoint.getOrElse(chosenMatch.segIndex + 1) { distToSegStart } - distToSegStart)
            .coerceAtLeast(0.0)
        val coveredDistance = distToSegStart + segLen * chosenMatch.fractionOnSeg
        val remainingMeters = (totalPolyDist - coveredDistance).coerceAtLeast(0.0)

        // 3. Determine active step & next maneuver step
        val steps = route.steps
        var stepIdx = lastMatchedStepIndex.coerceIn(0, (steps.lastIndex).coerceAtLeast(0))
        if (steps.isNotEmpty()) {
            // Segment segIndex runs from point[segIndex] to point[segIndex + 1], so a step owns [beginPointIndex, endPointIndex)
            val candidateIdx = steps.indexOfFirst { st ->
                chosenMatch.segIndex >= st.beginPointIndex && chosenMatch.segIndex < st.endPointIndex
            }.let { found ->
                if (found >= 0) found
                else steps.indexOfLast { st -> chosenMatch.segIndex >= st.beginPointIndex }
            }
            if (candidateIdx >= 0) {
                stepIdx = if (candidateIdx >= stepIdx || chosenMatch.distanceMeters <= 25.0) {
                    candidateIdx
                } else {
                    stepIdx
                }
            }
            lastMatchedStepIndex = stepIdx
        }

        val currentStep = steps.getOrNull(stepIdx)
        val nextStep = steps.getOrNull(stepIdx + 1)

        // Distance from current snapped position to the maneuver point at the end of currentStep
        val maneuverPointIdx = (currentStep?.endPointIndex ?: pts.lastIndex).coerceIn(0, pts.lastIndex)
        val distToTurnMeters = (cumulativeDistToPoint.getOrElse(maneuverPointIdx) { coveredDistance } - coveredDistance)
            .coerceAtLeast(0.0)

        // 4. Adaptive remaining duration (per-step road-class durations blended with live speed)
        val baseRatio = if (totalPolyDist > 10.0) (remainingMeters / totalPolyDist).coerceIn(0.0, 1.0) else 0.0
        val plannedRemSec = if (currentStep != null && currentStep.durationSeconds > 0.0) {
            val stepStartDist = cumulativeDistToPoint.getOrElse(currentStep.beginPointIndex.coerceIn(0, pts.lastIndex)) { coveredDistance }
            val stepEndDist = cumulativeDistToPoint.getOrElse(currentStep.endPointIndex.coerceIn(0, pts.lastIndex)) { coveredDistance }
            val stepSpanDist = (stepEndDist - stepStartDist).coerceAtLeast(1.0)
            val stepRemFrac = ((stepEndDist - coveredDistance) / stepSpanDist).coerceIn(0.0, 1.0)
            var sumSec = currentStep.durationSeconds * stepRemFrac
            for (i in (stepIdx + 1) until steps.size) {
                sumSec += steps[i].durationSeconds.coerceAtLeast(0.0)
            }
            sumSec
        } else {
            route.totalDurationSeconds * baseRatio
        }
        val blendedRemSec = if (location.speed >= 2.0f) {
            val liveSpeedEtaSec = remainingMeters / location.speed.toDouble()
            0.65 * plannedRemSec + 0.35 * liveSpeedEtaSec
        } else {
            max(plannedRemSec, remainingMeters / route.profile.fallbackSpeedMps)
        }

        // 5. Arrival detection
        val endPt = pts.last()
        val straightToEnd = ElevationAnalyzer.haversineMeters(
            location.latitude,
            location.longitude,
            endPt.latitude,
            endPt.longitude
        )
        if (!hasArrivedLatched &&
            (straightToEnd <= ARRIVAL_THRESHOLD_METERS ||
                (remainingMeters <= ARRIVAL_THRESHOLD_METERS && chosenMatch.segIndex >= (lastSegIdx - 2).coerceAtLeast(0)))
        ) {
            hasArrivedLatched = true
        }

        // 6. Hysteresis off-route detection (ignore coarse cellular fallback or >40m GPS spikes)
        val reliableForOffRoute = location.provider != "cellular" &&
            (location.accuracy <= 0f || location.accuracy <= 40f)
        var shouldRequestReroute = false
        if (hasArrivedLatched) {
            offRouteFixCount = 0
            offRouteStartedMs = 0L
            isOffRouteLatched = false
        } else if (reliableForOffRoute) {
            if (chosenMatch.distanceMeters > OFF_ROUTE_ENTER_METERS) {
                if (offRouteFixCount == 0) {
                    offRouteStartedMs = nowMs
                }
                offRouteFixCount++
                val dwellMs = nowMs - offRouteStartedMs
                if (offRouteFixCount >= REQUIRED_CONSECUTIVE_OFF_ROUTE_FIXES ||
                    (offRouteFixCount >= 2 && dwellMs >= REQUIRED_OFF_ROUTE_DWELL_MS)
                ) {
                    isOffRouteLatched = true
                    if (!isRerouting && canTriggerReroute(nowMs)) {
                        shouldRequestReroute = true
                    }
                }
            } else if (chosenMatch.distanceMeters <= OFF_ROUTE_EXIT_METERS) {
                offRouteFixCount = 0
                offRouteStartedMs = 0L
                isOffRouteLatched = false
            }
        }

        val isSnapped = chosenMatch.distanceMeters <= SNAP_TOLERANCE_METERS && !isOffRouteLatched
        val snappedLat = if (isSnapped) chosenMatch.projLat else location.latitude
        val snappedLon = if (isSnapped) chosenMatch.projLon else location.longitude
        val snappedBearing = if (isSnapped) {
            chosenMatch.segmentBearingDeg
        } else if (location.bearing.isFinite() && location.speed >= 1.5f) {
            location.bearing
        } else {
            chosenMatch.segmentBearingDeg
        }

        val backBearing = if (isOffRouteLatched) {
            ElevationAnalyzer.initialBearingDegrees(
                location.latitude,
                location.longitude,
                chosenMatch.projLat,
                chosenMatch.projLon
            )
        } else {
            null
        }

        val state = ActiveNavigationState(
            route = route,
            currentStepIndex = stepIdx,
            currentStep = currentStep,
            nextStep = nextStep,
            distanceToNextTurnMeters = distToTurnMeters,
            remainingDistanceMeters = remainingMeters,
            remainingDurationSeconds = blendedRemSec,
            etaEpochMs = nowMs + (blendedRemSec * 1000.0).toLong(),
            nearestPointIndex = chosenMatch.segIndex,
            crossTrackDistanceMeters = chosenMatch.distanceMeters,
            snappedLatitude = snappedLat,
            snappedLongitude = snappedLon,
            snappedBearingDegrees = snappedBearing,
            isSnappedToRoad = isSnapped,
            isOffRoute = isOffRouteLatched,
            isRerouting = isRerouting,
            isOfflineRerouteBlocked = isOfflineRerouteBlocked,
            rerouteStatusNote = rerouteStatusNote,
            hasArrived = hasArrivedLatched,
            voiceMode = voiceMode,
            headingBackToRouteDegrees = backBearing
        )
        return EngineTickOutput(
            state = state,
            shouldRequestReroute = shouldRequestReroute
        )
    }

    private data class SegmentMatch(
        val segIndex: Int,
        val distanceMeters: Double,
        val scoreMeters: Double,
        val fractionOnSeg: Double,
        val projLat: Double,
        val projLon: Double,
        val segmentBearingDeg: Float
    )

    private fun findBestSegment(
        lat: Double,
        lon: Double,
        userBearing: Float?,
        points: List<LocationPoint>,
        startSeg: Int,
        endSeg: Int
    ): SegmentMatch {
        var best: SegmentMatch? = null
        for (i in startSeg..endSeg) {
            val a = points[i]
            val b = points[i + 1]
            val proj = projectOntoSegment(lat, lon, a.latitude, a.longitude, b.latitude, b.longitude)
            val segBearing = ElevationAnalyzer.initialBearingDegrees(a.latitude, a.longitude, b.latitude, b.longitude)
            val headingPenalty = if (userBearing != null) {
                val delta = abs(((segBearing - userBearing + 540f) % 360f) - 180f)
                if (delta > 110f) 28.0 else 0.0
            } else {
                0.0
            }
            val score = proj.distanceMeters + headingPenalty
            if (best == null || score < best.scoreMeters) {
                best = SegmentMatch(
                    segIndex = i,
                    distanceMeters = proj.distanceMeters,
                    scoreMeters = score,
                    fractionOnSeg = proj.fraction,
                    projLat = proj.projLat,
                    projLon = proj.projLon,
                    segmentBearingDeg = segBearing
                )
            }
        }
        return best!!
    }

    private data class SegmentProjection(
        val distanceMeters: Double,
        val fraction: Double,
        val projLat: Double,
        val projLon: Double
    )

    private fun projectOntoSegment(
        pLat: Double, pLon: Double,
        aLat: Double, aLon: Double,
        bLat: Double, bLon: Double
    ): SegmentProjection {
        val refLatRad = Math.toRadians((aLat + bLat) * 0.5)
        val metersPerDegLat = 111132.92
        val metersPerDegLon = (111412.84 * cos(refLatRad)).coerceAtLeast(1.0)

        val px = (pLon - aLon) * metersPerDegLon
        val py = (pLat - aLat) * metersPerDegLat
        val bx = (bLon - aLon) * metersPerDegLon
        val by = (bLat - aLat) * metersPerDegLat

        val segLenSq = bx * bx + by * by
        if (segLenSq < 1e-6) {
            return SegmentProjection(
                distanceMeters = sqrt(px * px + py * py),
                fraction = 0.0,
                projLat = aLat,
                projLon = aLon
            )
        }
        val t = ((px * bx + py * by) / segLenSq).coerceIn(0.0, 1.0)
        val projX = t * bx
        val projY = t * by
        val dx = px - projX
        val dy = py - projY
        return SegmentProjection(
            distanceMeters = sqrt(dx * dx + dy * dy),
            fraction = t,
            projLat = aLat + t * (bLat - aLat),
            projLon = aLon + t * (bLon - aLon)
        )
    }

    private fun buildCumulativeDistances(points: List<LocationPoint>): DoubleArray {
        if (points.isEmpty()) return DoubleArray(0)
        val arr = DoubleArray(points.size)
        arr[0] = 0.0
        for (i in 1 until points.size) {
            val prev = points[i - 1]
            val cur = points[i]
            val d = ElevationAnalyzer.haversineMeters(prev.latitude, prev.longitude, cur.latitude, cur.longitude)
            arr[i] = arr[i - 1] + if (d.isFinite()) d else 0.0
        }
        return arr
    }
}
