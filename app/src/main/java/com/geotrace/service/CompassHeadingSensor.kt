package com.geotrace.service

import android.content.Context
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs

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

/**
 * Lifecycle-aware, display-rotation-compensated hardware compass azimuth in [0, 360) degrees.
 * Returns null if no orientation/magnetometer sensor is available or before first valid sample.
 */
@Composable
fun rememberCompassHeadingDegrees(enabled: Boolean = true): State<Float?> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val headingState = remember { mutableStateOf<Float?>(null) }

    DisposableEffect(context, lifecycleOwner, enabled) {
        if (!enabled) {
            headingState.value = null
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

        fun emitAzimuthRadians(azimuthRad: Float) {
            val rawDeg = normalizeHeadingDegrees(Math.toDegrees(azimuthRad.toDouble()).toFloat())
            val nextSmoothed = if (smoothedHeading == null) {
                rawDeg
            } else {
                val delta = shortestAngleDeltaDegrees(smoothedHeading!!, rawDeg)
                normalizeHeadingDegrees(smoothedHeading!! + 0.18f * delta)
            }
            smoothedHeading = nextSmoothed

            val now = SystemClock.elapsedRealtime()
            val prevEmitted = lastEmittedHeading
            if (prevEmitted == null ||
                (now - lastEmitTimeMs >= 50L && abs(shortestAngleDeltaDegrees(prevEmitted, nextSmoothed)) >= 1.5f)
            ) {
                lastEmittedHeading = nextSmoothed
                lastEmitTimeMs = now
                headingState.value = nextSmoothed
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

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        var isRegistered = false
        fun registerSensors() {
            if (isRegistered) return
            if (rotationSensor != null) {
                isRegistered = sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_UI)
            } else if (accelSensor != null && magSensor != null) {
                val okA = sensorManager.registerListener(listener, accelSensor, SensorManager.SENSOR_DELAY_UI)
                val okM = sensorManager.registerListener(listener, magSensor, SensorManager.SENSOR_DELAY_UI)
                isRegistered = okA && okM
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

    return headingState
}
