package com.geotrace.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.geotrace.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Plays a loud alarm-stream chime + pulse vibration on this device when triggered
 * remotely (via Firestore or offline P2P CMD_RING) to help locate misplaced phones or tablets.
 */
object FindDeviceAlarmManager {

    private const val TAG = "FindDeviceAlarm"
    private const val CHANNEL_ID = "geotrace_find_alarm_channel"
    private const val NOTIFICATION_ID = 4040
    private const val AUTO_STOP_MS = 45_000L

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var autoStopJob: Job? = null

    private val _isRinging = MutableStateFlow(false)
    val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

    private val _ringCallerName = MutableStateFlow<String?>(null)
    val ringCallerName: StateFlow<String?> = _ringCallerName.asStateFlow()

    var onAlarmStoppedByUser: (() -> Unit)? = null

    fun startRinging(context: Context, callerName: String) {
        val appCtx = context.applicationContext
        _ringCallerName.value = callerName.ifBlank { "GeoTrace Device Finder" }

        if (_isRinging.value && mediaPlayer != null) {
            postAlarmNotification(appCtx, _ringCallerName.value!!)
            return
        }

        stopInternal(appCtx, notifyCallback = false)
        _isRinging.value = true
        _ringCallerName.value = callerName.ifBlank { "GeoTrace Device Finder" }

        createAlarmChannel(appCtx)
        postAlarmNotification(appCtx, _ringCallerName.value!!)

        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            if (alarmUri != null) {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(appCtx, alarmUri)
                    isLooping = true
                    setVolume(1.0f, 1.0f)
                    prepare()
                    start()
                }
                mediaPlayer = player
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play find-device alarm audio: ${e.message}", e)
        }

        try {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = appCtx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appCtx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator = vib
            val pattern = longArrayOf(0, 500, 250, 500, 250)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(pattern, 0)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val attrs = VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build()
                    vib?.vibrate(effect, attrs)
                } else {
                    vib?.vibrate(effect)
                }
            } else {
                @Suppress("DEPRECATION")
                vib?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Vibration unavailable: ${e.message}")
        }

        autoStopJob?.cancel()
        autoStopJob = scope.launch {
            delay(AUTO_STOP_MS)
            stopRinging(appCtx)
        }
    }

    fun stopRinging(context: Context) {
        stopInternal(context.applicationContext, notifyCallback = true)
    }

    private fun stopInternal(appCtx: Context, notifyCallback: Boolean) {
        autoStopJob?.cancel()
        autoStopJob = null

        try {
            mediaPlayer?.let { mp ->
                if (mp.isPlaying) mp.stop()
                mp.release()
            }
        } catch (_: Exception) {
        } finally {
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        } finally {
            vibrator = null
        }

        try {
            val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}

        _isRinging.value = false
        _ringCallerName.value = null
        if (notifyCallback) {
            onAlarmStoppedByUser?.invoke()
        }
    }

    private fun postAlarmNotification(context: Context, caller: String) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            context,
            40,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, StopAlarmReceiver::class.java)
        val pendingStop = PendingIntent.getBroadcast(
            context,
            41,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("GeoTrace Find Alert • Ringing")
            .setContentText("Triggered by $caller • Tap Stop Alarm to silence")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_media_pause, "Stop Alarm", pendingStop)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun createAlarmChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Find Device Alarm",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority ring alerts to locate misplaced devices"
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    class StopAlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            stopRinging(context)
        }
    }
}
