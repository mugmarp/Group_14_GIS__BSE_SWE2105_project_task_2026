package com.geotrace.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BatterySafetyDecisionTest {

    @Test
    fun `decoupled beacon and notification thresholds trigger 15pc beacon then 12pc warning`() {
        var beaconThresh = 100
        var notifyThresh = 100

        // Above 15%: no action
        val d20 = LocationForegroundService.computeBatterySafetyDecision(
            pct = 20,
            isCharging = false,
            lastTriggeredBeaconThreshold = beaconThresh,
            lastSafetyNotifiedThreshold = notifyThresh
        )
        assertNull(d20.captureBeaconThreshold)
        assertNull(d20.notifyThreshold)

        // Hits 15%: captures beacon silently, does NOT notify yet
        val d15 = LocationForegroundService.computeBatterySafetyDecision(
            pct = 15,
            isCharging = false,
            lastTriggeredBeaconThreshold = beaconThresh,
            lastSafetyNotifiedThreshold = notifyThresh
        )
        assertEquals(15, d15.captureBeaconThreshold)
        assertNull(d15.notifyThreshold)
        beaconThresh = d15.nextTriggeredBeaconThreshold
        notifyThresh = d15.nextNotifiedThreshold

        // Drops to 12%: safety notification MUST fire without being masked by the 15% beacon bucket
        val d12 = LocationForegroundService.computeBatterySafetyDecision(
            pct = 12,
            isCharging = false,
            lastTriggeredBeaconThreshold = beaconThresh,
            lastSafetyNotifiedThreshold = notifyThresh
        )
        assertNull(d12.captureBeaconThreshold)
        assertEquals(12, d12.notifyThreshold)
        beaconThresh = d12.nextTriggeredBeaconThreshold
        notifyThresh = d12.nextNotifiedThreshold

        // Drops to 10%: both beacon and notification advance
        val d10 = LocationForegroundService.computeBatterySafetyDecision(
            pct = 10,
            isCharging = false,
            lastTriggeredBeaconThreshold = beaconThresh,
            lastSafetyNotifiedThreshold = notifyThresh
        )
        assertEquals(10, d10.captureBeaconThreshold)
        assertEquals(10, d10.notifyThreshold)
        beaconThresh = d10.nextTriggeredBeaconThreshold
        notifyThresh = d10.nextNotifiedThreshold

        // Drops to 5%: critical threshold
        val d5 = LocationForegroundService.computeBatterySafetyDecision(
            pct = 5,
            isCharging = false,
            lastTriggeredBeaconThreshold = beaconThresh,
            lastSafetyNotifiedThreshold = notifyThresh
        )
        assertEquals(5, d5.captureBeaconThreshold)
        assertEquals(5, d5.notifyThreshold)
        beaconThresh = d5.nextTriggeredBeaconThreshold
        notifyThresh = d5.nextNotifiedThreshold

        // Repeated reading at 5%: idempotent
        val d5Again = LocationForegroundService.computeBatterySafetyDecision(
            pct = 5,
            isCharging = false,
            lastTriggeredBeaconThreshold = beaconThresh,
            lastSafetyNotifiedThreshold = notifyThresh
        )
        assertNull(d5Again.captureBeaconThreshold)
        assertNull(d5Again.notifyThreshold)

        // Charging > 20%: resets thresholds and cancels notification
        val dCharging = LocationForegroundService.computeBatterySafetyDecision(
            pct = 25,
            isCharging = true,
            lastTriggeredBeaconThreshold = beaconThresh,
            lastSafetyNotifiedThreshold = notifyThresh
        )
        assertTrue(dCharging.resetState)
        assertTrue(dCharging.shouldCancelNotification)
        assertEquals(100, dCharging.nextTriggeredBeaconThreshold)
        assertEquals(100, dCharging.nextNotifiedThreshold)
    }

    @Test
    fun `unplugged above 20pc resets state without cancelling notification flag`() {
        val decision = LocationForegroundService.computeBatterySafetyDecision(
            pct = 25,
            isCharging = false,
            lastTriggeredBeaconThreshold = 10,
            lastSafetyNotifiedThreshold = 10
        )
        assertTrue(decision.resetState)
        assertFalse(decision.shouldCancelNotification)
        assertEquals(100, decision.nextTriggeredBeaconThreshold)
        assertEquals(100, decision.nextNotifiedThreshold)
    }
}
