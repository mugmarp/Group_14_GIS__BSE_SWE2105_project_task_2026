package com.geotrace.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.geotrace.LocationTrackerApp
import com.geotrace.MainActivity
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "GeofenceReceiver"
const val GEOFENCE_CHANNEL_ID = "geotrace_geofence_channel"

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("geotrace_prefs", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("geofence_notifications_enabled", false)

        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val protectionEnabled = prefs.getBoolean("background_protection_enabled", true)
            if (!isEnabled && !protectionEnabled) return
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as? LocationTrackerApp
                    if (isEnabled && app != null) {
                        app.repository.loadGeofences()
                        val zones = app.repository.geofenceZones.value
                        GeofenceManager(context.applicationContext).syncGeofences(zones, enabled = true)
                    }
                    if (protectionEnabled &&
                        com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null &&
                        LocationForegroundService.hasLocationPermission(context.applicationContext) &&
                        LocationForegroundService.hasBackgroundLocationPermission(context.applicationContext)
                    ) {
                        LocationForegroundService.startProtectionMode(context.applicationContext)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore services on boot: ${e.message}")
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        if (!isEnabled) {
            Log.d(TAG, "Geofence event ignored because notifications are toggled OFF.")
            return
        }

        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, "Geofencing error code: ${geofencingEvent.errorCode} - $errorMessage")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        if (geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER &&
            geofenceTransition != Geofence.GEOFENCE_TRANSITION_EXIT
        ) {
            return
        }

        val isNowInside = geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: emptyList()
        if (triggeringGeofences.isEmpty()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? LocationTrackerApp
                if (app != null) {
                    for (geofence in triggeringGeofences) {
                        app.repository.handleApiGeofenceTransition(
                            zoneId = geofence.requestId,
                            isNowInside = isNowInside
                        )
                    }
                } else {
                    val transitionVerb = if (isNowInside) "Entered" else "Exited"
                    for (geofence in triggeringGeofences) {
                        sendNotification(
                            context = context,
                            title = "📍 Academic Geofence Alert: $transitionVerb",
                            content = "You have $transitionVerb ${geofence.requestId}",
                            notificationId = geofence.requestId.hashCode() + geofenceTransition
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process Geofencing API event: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_GEOFENCE_EVENT = "com.geotrace.ACTION_GEOFENCE_EVENT"

        fun createGeofenceNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    GEOFENCE_CHANNEL_ID,
                    "Academic Geofence & Safe Zone Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Sends perimeter alerts when entering or exiting academic campuses, hostels, and safe zones"
                    enableVibration(true)
                    setShowBadge(true)
                }
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }

        fun sendNotification(context: Context, title: String, content: String, notificationId: Int = 2001) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    Log.w(TAG, "Skipping geofence notification: POST_NOTIFICATIONS permission not granted.")
                    return
                }
            }

            createGeofenceNotificationChannel(context)

            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(context, GEOFENCE_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, notification)
        }
    }
}
