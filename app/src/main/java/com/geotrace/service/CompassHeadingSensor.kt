package com.geotrace.service

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs

data class CompassReading(
    val azimuthDegrees: Float? = null,
    val isLowAccuracy: Boolean = false,
    val declinationDegrees: Float = 0f
)

fun normalizeHeadingDegrees(degrees: Float): Float {
    if (!degrees.isFinite()) return 0f
    val mod = ((degrees % 360f) + 360f) % 360f
    return if (mod >= 360f) 0f else mod
}

fun shortestAngleDeltaDegrees(fromDeg: Float, toDeg: Float): Float {
    if (!fromDeg.isFinite() || !toDeg.isFinite()) return 0f
    val raw = normalizeHeadingDegrees(toDeg) - normalizeHeadingDegrees(fromDeg)
    return when {
        raw >= 180f -> raw - 360f
        raw < -180f -> raw + 360f
        else -> raw
    }
}

fun formatCompassCardinal(degrees: Float): String {
    val norm = normalizeHeadingDegrees(degrees)
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = (((norm + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)
    return dirs[idx]
}

private fun currentDisplayRotation(context: Context): Int {
    return try {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        dm?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0
    } catch (_: Exception) {
        Surface.ROTATION_0
    }
}

private fun remapRotationMatrix(inR: FloatArray, outR: FloatArray, displayRotation: Int) {
    when (displayRotation) {
        Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
            inR,
            SensorManager.AXIS_Y,
            SensorManager.AXIS_MINUS_X,
            outR
        )
        Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
            inR,
            SensorManager.AXIS_MINUS_X,
            SensorManager.AXIS_MINUS_Y,
            outR
        )
        Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
            inR,
            SensorManager.AXIS_MINUS_Y,
            SensorManager.AXIS_X,
            outR
        )
        else -> System.arraycopy(inR, 0, outR, 0, 9)
    }
}

@Composable
fun rememberCompassReading(
    enabled: Boolean = true,
    highPrecision: Boolean = true,
    latitude: Double? = null,
    longitude: Double? = null,
    altitudeMeters: Double = 0.0
): State<CompassReading> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val readingState = remember { mutableStateOf(CompassReading()) }

    val declination = remember(latitude, longitude, altitudeMeters) {
        if (latitude != null && longitude != null &&
            latitude.isFinite() && longitude.isFinite() &&
            (latitude != 0.0 || longitude != 0.0)
        ) {
            try {
                GeomagneticField(
                    latitude.toFloat(),
                    longitude.toFloat(),
                    altitudeMeters.toFloat().coerceIn(-500f, 10000f),
                    System.currentTimeMillis()
                ).declination.takeIf { it.isFinite() } ?: 0f
            } catch (_: Exception) {
                0f
            }
        } else {
            0f
        }
    }
    val currentDeclination by rememberUpdatedState(declination)

    DisposableEffect(context, lifecycleOwner, enabled, highPrecision) {
        if (!enabled) {
            readingState.value = CompassReading()
            return@DisposableEffect onDispose {}
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        if (sensorManager == null) {
            return@DisposableEffect onDispose {}
        }

        val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
        val accelSensor = if (rotationSensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) else null
        val magSensor = if (rotationSensor == null) sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) else null

        if (rotationSensor == null && (accelSensor == null || magSensor == null)) {
            return@DisposableEffect onDispose {}
        }

        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        val lastAccel = FloatArray(3)
        val lastMag = FloatArray(3)
        var hasAccel = false
        var hasMag = false
        var smoothedHeading: Float? = null
        var lastEmittedHeading: Float? = null
        var lastEmitTimeMs = 0L
        var lowAccuracyFlag = false

        fun emitAzimuthRadians(azimuthRad: Float) {
            val magneticDeg = normalizeHeadingDegrees(Math.toDegrees(azimuthRad.toDouble()).toFloat())
            val decl = currentDeclination
            val trueNorthDeg = normalizeHeadingDegrees(magneticDeg + decl)

            val nextSmoothed = if (smoothedHeading == null) {
                trueNorthDeg
            } else {
                val delta = shortestAngleDeltaDegrees(smoothedHeading!!, trueNorthDeg)
                normalizeHeadingDegrees(smoothedHeading!! + 0.18f * delta)
            }
            smoothedHeading = nextSmoothed

            val minEmitIntervalMs = if (highPrecision) 50L else 200L
            val minHeadingDeltaDeg = if (highPrecision) 1.5f else 3.0f
            val now = SystemClock.elapsedRealtime()
            val prevEmitted = lastEmittedHeading
            if (prevEmitted == null ||
                (now - lastEmitTimeMs >= minEmitIntervalMs && abs(shortestAngleDeltaDegrees(prevEmitted, nextSmoothed)) >= minHeadingDeltaDeg)
            ) {
                lastEmittedHeading = nextSmoothed
                lastEmitTimeMs = now
                readingState.value = CompassReading(
                    azimuthDegrees = nextSmoothed,
                    isLowAccuracy = lowAccuracyFlag,
                    declinationDegrees = decl
                )
            }
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR,
                    Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        remapRotationMatrix(rotationMatrix, remappedMatrix, currentDisplayRotation(context))
                        SensorManager.getOrientation(remappedMatrix, orientationAngles)
                        emitAzimuthRadians(orientationAngles[0])
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        System.arraycopy(event.values, 0, lastAccel, 0, 3)
                        hasAccel = true
                        if (hasMag && SensorManager.getRotationMatrix(rotationMatrix, null, lastAccel, lastMag)) {
                            remapRotationMatrix(rotationMatrix, remappedMatrix, currentDisplayRotation(context))
                            SensorManager.getOrientation(remappedMatrix, orientationAngles)
                            emitAzimuthRadians(orientationAngles[0])
                        }
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        System.arraycopy(event.values, 0, lastMag, 0, 3)
                        hasMag = true
                        if (hasAccel && SensorManager.getRotationMatrix(rotationMatrix, null, lastAccel, lastMag)) {
                            remapRotationMatrix(rotationMatrix, remappedMatrix, currentDisplayRotation(context))
                            SensorManager.getOrientation(remappedMatrix, orientationAngles)
                            emitAzimuthRadians(orientationAngles[0])
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                val type = sensor?.type
                if (type == Sensor.TYPE_MAGNETIC_FIELD ||
                    type == Sensor.TYPE_ROTATION_VECTOR ||
                    type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
                ) {
                    val isLow = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                        accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
                    if (lowAccuracyFlag != isLow) {
                        lowAccuracyFlag = isLow
                        readingState.value = readingState.value.copy(isLowAccuracy = isLow)
                    }
                }
            }
        }

        var isRegistered = false
        val sensorDelay = if (highPrecision) SensorManager.SENSOR_DELAY_UI else SensorManager.SENSOR_DELAY_NORMAL
        fun registerSensors() {
            if (isRegistered) return
            if (rotationSensor != null) {
                isRegistered = sensorManager.registerListener(listener, rotationSensor, sensorDelay)
            } else if (accelSensor != null && magSensor != null) {
                val okA = sensorManager.registerListener(listener, accelSensor, sensorDelay)
                val okM = sensorManager.registerListener(listener, magSensor, sensorDelay)
                if (okA && okM) {
                    isRegistered = true
                } else {
                    sensorManager.unregisterListener(listener)
                    isRegistered = false
                }
            }
        }

        fun unregisterSensors() {
            if (!isRegistered) return
            sensorManager.unregisterListener(listener)
            isRegistered = false
        }

        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            registerSensors()
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> registerSensors()
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_DESTROY -> unregisterSensors()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregisterSensors()
        }
    }

    return readingState
}

/**
 * Lifecycle-aware, display-rotation-compensated hardware compass azimuth in [0, 360) degrees.
 * Returns null if no orientation/magnetometer sensor is available or before first valid sample.
 */
@Composable
fun rememberCompassHeadingDegrees(
    enabled: Boolean = true,
    highPrecision: Boolean = true
): State<Float?> {
    val reading = rememberCompassReading(enabled = enabled, highPrecision = highPrecision)
    return remember(reading) {
        derivedStateOf { reading.value.azimuthDegrees }
    }
}
