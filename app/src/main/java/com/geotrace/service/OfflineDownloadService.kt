package com.geotrace.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.geotrace.LocationTrackerApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Data-sync foreground service that keeps offline tile downloads and Overpass POI
 * indexing alive when the user navigates away from the Offline Maps dialog or app UI.
 */
class OfflineDownloadService : Service() {

    companion object {
        const val CHANNEL_ID = "offline_maps_channel"
        const val FOREGROUND_NOTIFICATION_ID = 4102

        fun start(context: Context): Boolean {
            return try {
                val intent = Intent(context, OfflineDownloadService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(context, intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (_: Exception) {
                false
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, OfflineDownloadService::class.java))
            } catch (_: Exception) {}
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null
    private var graceStopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? LocationTrackerApp
        val progress = app?.offlineMapManager?.downloadProgress?.value
        val indexingIds = app?.poiIndexingRegionIds?.value ?: emptySet()
        val roadIndexingIds = app?.offlineRoutingPackManager?.indexingRegionIds?.value ?: emptySet()
        val initialNotification = if (progress?.isDownloading == true) {
            buildProgressNotification(
                regionName = progress.regionName.ifBlank { "Offline map" },
                completed = progress.completedTiles,
                total = progress.totalTiles
            )
        } else if (roadIndexingIds.isNotEmpty()) {
            buildRoadIndexingNotification(roadIndexingIds.size)
        } else if (indexingIds.isNotEmpty()) {
            buildPoiIndexingNotification(indexingIds.size)
        } else {
            buildProgressNotification(
                regionName = "Offline map",
                completed = 0,
                total = 0
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                initialNotification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, initialNotification)
        }

        if (app == null) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        if (observerJob == null) {
            observerJob = serviceScope.launch {
                var sawActiveWork = app.offlineMapManager.downloadProgress.value.isDownloading ||
                    app.poiIndexingRegionIds.value.isNotEmpty() ||
                    app.offlineRoutingPackManager.indexingRegionIds.value.isNotEmpty()
                kotlinx.coroutines.flow.combine(
                    app.offlineMapManager.downloadProgress,
                    app.poiIndexingRegionIds,
                    app.offlineRoutingPackManager.indexingRegionIds
                ) { state, poiIds, roadIds ->
                    Triple(state, poiIds, roadIds)
                }.collect { (state, poiIds, roadIds) ->
                    val activeNow = state.isDownloading || poiIds.isNotEmpty() || roadIds.isNotEmpty()
                    if (activeNow) {
                        sawActiveWork = true
                        graceStopJob?.cancel()
                        graceStopJob = null
                        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                        val notification = when {
                            state.isDownloading -> buildProgressNotification(
                                regionName = state.regionName.ifBlank { "Offline map" },
                                completed = state.completedTiles,
                                total = state.totalTiles
                            )
                            roadIds.isNotEmpty() -> buildRoadIndexingNotification(roadIds.size)
                            else -> buildPoiIndexingNotification(poiIds.size)
                        }
                        nm?.notify(FOREGROUND_NOTIFICATION_ID, notification)
                    } else if (sawActiveWork) {
                        graceStopJob?.cancel()
                        graceStopJob = null
                        stopSelfSafely()
                    } else if (graceStopJob == null) {
                        graceStopJob = serviceScope.launch {
                            kotlinx.coroutines.delay(500L)
                            val stillIdle = !app.offlineMapManager.downloadProgress.value.isDownloading &&
                                app.poiIndexingRegionIds.value.isEmpty() &&
                                app.offlineRoutingPackManager.indexingRegionIds.value.isEmpty()
                            if (stillIdle) {
                                stopSelfSafely()
                            }
                        }
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun stopSelfSafely() {
        graceStopJob?.cancel()
        graceStopJob = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {}
        stopSelf()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Offline Map Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Progress and completion status for offline map caching"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildProgressNotification(regionName: String, completed: Int, total: Int): Notification {
        val pct = if (total > 0) ((completed * 100) / total).coerceIn(0, 100) else 0
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading offline map ($pct%)")
            .setContentText("$regionName • $completed / $total tiles")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(1), completed.coerceAtLeast(0), total <= 0)
            .build()
    }

    private fun buildPoiIndexingNotification(activeCount: Int): Notification {
        val text = if (activeCount > 1) {
            "Caching searchable landmarks for $activeCount offline regions"
        } else {
            "Caching searchable landmarks & shelters for offline use"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Indexing offline POIs…")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
    }

    private fun buildRoadIndexingNotification(activeCount: Int): Notification {
        val text = if (activeCount > 1) {
            "Building offline road routing graphs for $activeCount regions"
        } else {
            "Compiling offline OSM road network for turn-by-turn routing"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Building offline road pack…")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .build()
    }

    override fun onDestroy() {
        graceStopJob?.cancel()
        graceStopJob = null
        observerJob?.cancel()
        observerJob = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
