package com.geotrace.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingModelsTest {

    @Test
    fun `TrackingSession duration subtracts paused duration and formats hh mm ss when needed`() {
        val active = TrackingSession(
            id = "s1",
            name = "Field Walk",
            startTime = 1_000_000L,
            endTime = null,
            pausedAccumulatedMs = 15_000L,
            isRecording = true,
            isPaused = false
        )
        assertEquals(60L, active.currentDurationSeconds(nowMs = 1_075_000L))
        assertEquals("01:00", active.formatDuration(nowMs = 1_075_000L))

        val longSession = TrackingSession(
            id = "s2",
            name = "Survey",
            startTime = 0L,
            endTime = 3_665_000L,
            pausedAccumulatedMs = 0L
        )
        assertEquals("01:01:05", longSession.formattedDuration)
    }

    @Test
    fun `TrackingSession formats distance in meters and kilometers accurately`() {
        val shortSession = TrackingSession(id = "a", name = "Short", startTime = 0L, totalDistanceMeters = 842.4)
        val longSession = TrackingSession(id = "b", name = "Long", startTime = 0L, totalDistanceMeters = 2450.0)

        assertEquals("842 m", shortSession.formattedDistance)
        assertEquals("2.45 km", longSession.formattedDistance)
    }

    @Test
    fun `LocationPoint classifies gait labels across speed thresholds`() {
        fun pointWithKmh(kmh: Float) = LocationPoint(
            latitude = -0.59,
            longitude = 30.65,
            accuracy = 4f,
            speed = kmh / 3.6f,
            sessionId = "s"
        )

        assertEquals("Stationary", pointWithKmh(0.4f).gaitLabel)
        assertEquals("Slow Walk", pointWithKmh(1.8f).gaitLabel)
        assertEquals("Walking", pointWithKmh(4.2f).gaitLabel)
        assertEquals("Brisk Walk", pointWithKmh(7.0f).gaitLabel)
        assertEquals("Running", pointWithKmh(12.0f).gaitLabel)
        assertEquals("In Transit", pointWithKmh(35.0f).gaitLabel)
        assertEquals("--:--", pointWithKmh(0.5f).paceMinPerKm)
    }

    @Test
    fun `CellTowerInfo resolves East African PLMN operators and country names`() {
        val mtn = CellTowerInfo(
            type = "LTE",
            mcc = 641,
            mnc = 10,
            cid = 12345L,
            lac = 400,
            signalDbm = -82,
            signalLevel = 3,
            isRegistered = true,
            operatorName = null
        )
        val airtel = mtn.copy(mnc = 22)
        val kenya = mtn.copy(mcc = 639, mnc = 2, operatorName = "Safaricom")

        assertEquals("MTN Uganda (PLMN 64110)", mtn.operatorDisplayName)
        assertEquals("Uganda", mtn.countryDisplayName)
        assertEquals("Airtel Uganda (PLMN 64122)", airtel.operatorDisplayName)
        assertEquals("Safaricom", kenya.operatorDisplayName)
        assertEquals("Kenya", kenya.countryDisplayName)
    }
}
