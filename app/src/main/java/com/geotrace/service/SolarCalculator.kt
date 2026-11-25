package com.geotrace.service

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

data class SolarDaylightInfo(
    val sunriseTimeMs: Long?,
    val sunsetTimeMs: Long?,
    val isDaylight: Boolean,
    val minutesUntilTransition: Long?,
    val isLowLightSoon: Boolean,
    val polarCondition: String? = null
) {
    fun formatHudBadge(timeZone: TimeZone = TimeZone.getDefault()): String {
        if (polarCondition != null) return polarCondition
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault()).apply {
            this.timeZone = timeZone
        }
        val targetMs = if (isDaylight) sunsetTimeMs else sunriseTimeMs
        val label = if (isDaylight) "Sunset" else "Sunrise"
        val clockStr = targetMs?.let { fmt.format(Date(it)) } ?: "--:--"
        val remMin = minutesUntilTransition
        return if (remMin != null && remMin >= 0L) {
            val h = remMin / 60L
            val m = remMin % 60L
            val countdown = if (h > 0L) "${h}h ${m}m" else "${m}m"
            "$label $clockStr · $countdown"
        } else {
            "$label $clockStr"
        }
    }
}

/**
 * Pure astronomical sunrise/sunset calculator based on the NOAA Solar Calculation algorithm.
 * Works 100% offline for any latitude/longitude and local timezone.
 */
object SolarCalculator {

    // Official civil sunrise/sunset solar zenith (90 deg 50 min = 90.833 deg)
    private const val OFFICIAL_ZENITH_DEG = 90.833333

    fun calculateDaylight(
        latitude: Double,
        longitude: Double,
        nowMs: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): SolarDaylightInfo {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            latitude !in -89.9..89.9 || longitude !in -180.0..180.0
        ) {
            return SolarDaylightInfo(null, null, true, null, false)
        }

        val cal = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMs
        }
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val todaySunrise = computeEventUtcMs(year, month, day, latitude, longitude, isSunrise = true)
        val todaySunset = computeEventUtcMs(year, month, day, latitude, longitude, isSunrise = false)

        if (todaySunrise == null || todaySunset == null) {
            val polarLabel = if ((latitude > 0 && month in 4..9) || (latitude < 0 && (month >= 10 || month <= 3))) {
                "24h Daylight"
            } else {
                "Polar Night"
            }
            return SolarDaylightInfo(
                sunriseTimeMs = todaySunrise,
                sunsetTimeMs = todaySunset,
                isDaylight = polarLabel == "24h Daylight",
                minutesUntilTransition = null,
                isLowLightSoon = false,
                polarCondition = polarLabel
            )
        }

        return when {
            nowMs < todaySunrise -> {
                val mins = ((todaySunrise - nowMs) / 60_000L).coerceAtLeast(0L)
                SolarDaylightInfo(
                    sunriseTimeMs = todaySunrise,
                    sunsetTimeMs = todaySunset,
                    isDaylight = false,
                    minutesUntilTransition = mins,
                    isLowLightSoon = false
                )
            }
            nowMs <= todaySunset -> {
                val mins = ((todaySunset - nowMs) / 60_000L).coerceAtLeast(0L)
                SolarDaylightInfo(
                    sunriseTimeMs = todaySunrise,
                    sunsetTimeMs = todaySunset,
                    isDaylight = true,
                    minutesUntilTransition = mins,
                    isLowLightSoon = mins <= 60L
                )
            }
            else -> {
                // Past today's sunset: compute tomorrow's sunrise
                val nextCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, 1) }
                val nextSunrise = computeEventUtcMs(
                    nextCal.get(Calendar.YEAR),
                    nextCal.get(Calendar.MONTH) + 1,
                    nextCal.get(Calendar.DAY_OF_MONTH),
                    latitude,
                    longitude,
                    isSunrise = true
                )
                val mins = nextSunrise?.let { ((it - nowMs) / 60_000L).coerceAtLeast(0L) }
                SolarDaylightInfo(
                    sunriseTimeMs = nextSunrise,
                    sunsetTimeMs = todaySunset,
                    isDaylight = false,
                    minutesUntilTransition = mins,
                    isLowLightSoon = false
                )
            }
        }
    }

    private fun computeEventUtcMs(
        year: Int,
        month: Int,
        day: Int,
        latitude: Double,
        longitude: Double,
        isSunrise: Boolean
    ): Long? {
        val n1 = floor(275.0 * month / 9.0)
        val n2 = floor((month + 9.0) / 12.0)
        val n3 = 1.0 + floor((year - 4.0 * floor(year / 4.0) + 2.0) / 3.0)
        val dayOfYear = n1 - (n2 * n3) + day - 30.0

        val lngHour = longitude / 15.0
        val approxTime = dayOfYear + ((if (isSunrise) 6.0 else 18.0) - lngHour) / 24.0

        val meanAnomaly = 0.9856 * approxTime - 3.289
        var sunTrueLng = meanAnomaly +
            1.916 * sin(Math.toRadians(meanAnomaly)) +
            0.020 * sin(Math.toRadians(2.0 * meanAnomaly)) +
            282.634
        sunTrueLng = normalizeDegrees(sunTrueLng)

        var rightAscension = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(sunTrueLng))))
        rightAscension = normalizeDegrees(rightAscension)
        val lQuadrant = floor(sunTrueLng / 90.0) * 90.0
        val raQuadrant = floor(rightAscension / 90.0) * 90.0
        rightAscension = (rightAscension + (lQuadrant - raQuadrant)) / 15.0

        val sinDec = 0.39782 * sin(Math.toRadians(sunTrueLng))
        val cosDec = cos(asin(sinDec))

        val cosH = (cos(Math.toRadians(OFFICIAL_ZENITH_DEG)) - sinDec * sin(Math.toRadians(latitude))) /
            (cosDec * cos(Math.toRadians(latitude)))

        if (cosH > 1.0 || cosH < -1.0) {
            return null // Polar day or polar night
        }

        val hDeg = if (isSunrise) {
            360.0 - Math.toDegrees(acos(cosH))
        } else {
            Math.toDegrees(acos(cosH))
        }
        val hHours = hDeg / 15.0

        val rawLocalMeanTime = hHours + rightAscension - 0.06571 * approxTime - 6.622
        val localMeanTime = ((rawLocalMeanTime % 24.0) + 24.0) % 24.0
        val rawUtcHours = localMeanTime - lngHour
        val utcDayOffset = floor(rawUtcHours / 24.0).toInt()
        val utcHours = rawUtcHours - (utcDayOffset * 24.0)

        val midnightUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_MONTH, utcDayOffset)
        }.timeInMillis

        return midnightUtc + (utcHours * 3_600_000.0).toLong()
    }

    private fun normalizeDegrees(deg: Double): Double {
        val m = deg % 360.0
        return if (m < 0.0) m + 360.0 else m
    }
}
