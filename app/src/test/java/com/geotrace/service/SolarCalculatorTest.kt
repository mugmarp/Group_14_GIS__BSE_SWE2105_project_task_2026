package com.geotrace.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SolarCalculatorTest {

    private fun utcTimeMs(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    @Test
    fun `kampala equinox sunrise and sunset occur at expected local EAT times`() {
        val kampalaTz = TimeZone.getTimeZone("Africa/Kampala") // UTC+3
        // Noon EAT (09:00 UTC) on March 20, 2026 (equinox)
        val middayUtcMs = utcTimeMs(2026, 3, 20, 9, 0)

        val info = SolarCalculator.calculateDaylight(
            latitude = 0.3476,
            longitude = 32.5825,
            nowMs = middayUtcMs,
            timeZone = kampalaTz
        )

        assertNull(info.polarCondition)
        assertTrue(info.isDaylight)
        assertFalse(info.isLowLightSoon)
        assertNotNull(info.sunriseTimeMs)
        assertNotNull(info.sunsetTimeMs)

        val sunriseCal = Calendar.getInstance(kampalaTz).apply { timeInMillis = info.sunriseTimeMs!! }
        val sunsetCal = Calendar.getInstance(kampalaTz).apply { timeInMillis = info.sunsetTimeMs!! }

        val sunriseHourMin = sunriseCal.get(Calendar.HOUR_OF_DAY) * 60 + sunriseCal.get(Calendar.MINUTE)
        val sunsetHourMin = sunsetCal.get(Calendar.HOUR_OF_DAY) * 60 + sunsetCal.get(Calendar.MINUTE)

        // Kampala solar sunrise/sunset on equinox ~06:50 / ~18:57 EAT
        assertTrue("Sunrise expected 06:35..07:05 EAT, got $sunriseHourMin", sunriseHourMin in 395..425)
        assertTrue("Sunset expected 18:40..19:15 EAT, got $sunsetHourMin", sunsetHourMin in 1120..1155)
        assertTrue(info.formatHudBadge(kampalaTz).startsWith("Sunset "))
    }

    @Test
    fun `isLowLightSoon triggers within 60 minutes before sunset`() {
        val kampalaTz = TimeZone.getTimeZone("Africa/Kampala")
        // 15:30 UTC = 18:30 EAT (~25 min before ~18:57 EAT sunset)
        val duskApproachUtcMs = utcTimeMs(2026, 3, 20, 15, 30)

        val info = SolarCalculator.calculateDaylight(
            latitude = 0.3476,
            longitude = 32.5825,
            nowMs = duskApproachUtcMs,
            timeZone = kampalaTz
        )

        assertTrue(info.isDaylight)
        assertTrue(info.isLowLightSoon)
        assertNotNull(info.minutesUntilTransition)
        assertTrue(info.minutesUntilTransition!! in 1L..60L)
    }

    @Test
    fun `nighttime after sunset targets tomorrow sunrise`() {
        val kampalaTz = TimeZone.getTimeZone("Africa/Kampala")
        // 20:00 UTC = 23:00 EAT (nighttime)
        val nightUtcMs = utcTimeMs(2026, 3, 20, 20, 0)

        val info = SolarCalculator.calculateDaylight(
            latitude = 0.3476,
            longitude = 32.5825,
            nowMs = nightUtcMs,
            timeZone = kampalaTz
        )

        assertFalse(info.isDaylight)
        assertFalse(info.isLowLightSoon)
        assertNotNull(info.sunriseTimeMs)
        assertTrue(info.sunriseTimeMs!! > nightUtcMs)
        assertTrue(info.formatHudBadge(kampalaTz).startsWith("Sunrise "))
    }

    @Test
    fun `polar latitudes report polar day or polar night gracefully`() {
        val utcTz = TimeZone.getTimeZone("UTC")
        // Tromso/Svalbard summer solstice: 78 N on June 21
        val summerSolsticeMs = utcTimeMs(2026, 6, 21, 12, 0)
        val polarDay = SolarCalculator.calculateDaylight(78.22, 15.63, summerSolsticeMs, utcTz)
        assertEquals("24h Daylight", polarDay.polarCondition)
        assertTrue(polarDay.isDaylight)
        assertEquals("24h Daylight", polarDay.formatHudBadge(utcTz))

        // Winter solstice at 78 N on December 21
        val winterSolsticeMs = utcTimeMs(2026, 12, 21, 12, 0)
        val polarNight = SolarCalculator.calculateDaylight(78.22, 15.63, winterSolsticeMs, utcTz)
        assertEquals("Polar Night", polarNight.polarCondition)
        assertFalse(polarNight.isDaylight)
    }

    @Test
    fun `sydney sunrise crosses UTC day boundary minus one cleanly`() {
        val sydneyTz = TimeZone.getTimeZone("Australia/Sydney") // UTC+11 in March
        // 05:30 AEDT on March 20, 2026 = 18:30 UTC on March 19
        val preDawnUtcMs = utcTimeMs(2026, 3, 19, 18, 30)
        val preDawnInfo = SolarCalculator.calculateDaylight(
            latitude = -33.8688,
            longitude = 151.2093,
            nowMs = preDawnUtcMs,
            timeZone = sydneyTz
        )
        assertFalse(preDawnInfo.isDaylight)
        assertNotNull(preDawnInfo.sunriseTimeMs)
        val sunriseLocal = Calendar.getInstance(sydneyTz).apply { timeInMillis = preDawnInfo.sunriseTimeMs!! }
        val sunriseMins = sunriseLocal.get(Calendar.HOUR_OF_DAY) * 60 + sunriseLocal.get(Calendar.MINUTE)
        assertTrue("Sydney sunrise expected ~06:40..07:15 AEDT, got $sunriseMins", sunriseMins in 400..435)
        assertTrue("Sunrise should be 60..120m away from 05:30 AEDT", preDawnInfo.minutesUntilTransition!! in 60L..120L)
    }

    @Test
    fun `san francisco sunset crosses UTC day boundary plus one cleanly`() {
        val laTz = TimeZone.getTimeZone("America/Los_Angeles") // UTC-7 in June (PDT)
        // 12:00 PDT on June 20, 2026 = 19:00 UTC on June 20
        val middayUtcMs = utcTimeMs(2026, 6, 20, 19, 0)
        val info = SolarCalculator.calculateDaylight(
            latitude = 37.7749,
            longitude = -122.4194,
            nowMs = middayUtcMs,
            timeZone = laTz
        )
        assertTrue(info.isDaylight)
        assertNotNull(info.sunsetTimeMs)
        val sunsetLocal = Calendar.getInstance(laTz).apply { timeInMillis = info.sunsetTimeMs!! }
        val sunsetMins = sunsetLocal.get(Calendar.HOUR_OF_DAY) * 60 + sunsetLocal.get(Calendar.MINUTE)
        assertTrue("SF solstice sunset expected ~20:20..20:50 PDT, got $sunsetMins", sunsetMins in 1220..1250)
        assertTrue("Sunset should be ~490..540m from noon", info.minutesUntilTransition!! in 490L..540L)
    }
}
