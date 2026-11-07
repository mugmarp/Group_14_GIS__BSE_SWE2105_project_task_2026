package com.geotrace.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.geotrace.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val releaseName: String,
    val body: String,
    val publishedAt: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSizeBytes: Long,
    val isNewer: Boolean
)

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    object Checking : UpdateStatus()
    data class UpdateAvailable(val info: AppReleaseInfo) : UpdateStatus()
    object UpToDate : UpdateStatus()
    data class Downloading(val progressPercent: Int) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

/**
 * AppUpdateManager handles checking for official releases on GitHub,
 * comparing Semantic Versioning (e.g. v1.1.0 vs current BuildConfig.VERSION_NAME),
 * downloading APK assets using Android DownloadManager, and initiating secure package installation via FileProvider.
 */
class AppUpdateManager(private val context: Context) {

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    companion object {
        const val GITHUB_REPO = "mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026"
        private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    }

    suspend fun checkForUpdates(): UpdateStatus = withContext(Dispatchers.IO) {
        _updateStatus.value = UpdateStatus.Checking
        try {
            val url = URL(API_URL)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "GeoTrace-Android-${BuildConfig.VERSION_NAME}")
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val responseStr = reader.use { it.readText() }
                connection.disconnect()

                val json = JSONObject(responseStr)
                val tagName = json.optString("tag_name", "v1.0.0")
                val releaseName = json.optString("name", tagName)
                val body = json.optString("body", "Routine bug fixes and telemetry engine optimizations.")
                val publishedAt = json.optString("published_at", "")

                var apkDownloadUrl = ""
                var apkFileName = "GeoTrace-release.apk"
                var apkSizeBytes = 0L

                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val name = asset.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkDownloadUrl = asset.optString("browser_download_url", "")
                            apkFileName = name
                            apkSizeBytes = asset.optLong("size", 0L)
                            // Prefer release over debug if multiple APK assets exist
                            if (name.contains("release", ignoreCase = true)) {
                                break
                            }
                        }
                    }
                }

                if (apkDownloadUrl.isEmpty()) {
                    // Fallback to releases web page or tag download if assets haven't populated yet
                    apkDownloadUrl = "https://github.com/$GITHUB_REPO/releases/download/$tagName/app-release.apk"
                }

                val currentVersion = BuildConfig.VERSION_NAME
                val isNewer = isVersionNewer(tagName, currentVersion)

                val info = AppReleaseInfo(
                    tagName = tagName,
                    releaseName = releaseName,
                    body = body,
                    publishedAt = publishedAt,
                    apkDownloadUrl = apkDownloadUrl,
                    apkFileName = apkFileName,
                    apkSizeBytes = apkSizeBytes,
                    isNewer = isNewer
                )

                val result = if (isNewer) {
                    UpdateStatus.UpdateAvailable(info)
                } else {
                    UpdateStatus.UpToDate
                }
                _updateStatus.value = result
                result
            } else {
                val errMsg = "HTTP ${connection.responseCode}: ${connection.responseMessage}"
                connection.disconnect()
                val errStatus = UpdateStatus.Error(errMsg)
                _updateStatus.value = errStatus
                errStatus
            }
        } catch (e: Exception) {
            val errStatus = UpdateStatus.Error(e.message ?: "Failed to check GitHub releases")
            _updateStatus.value = errStatus
            errStatus
        }
    }

    /**
     * Downloads the APK file and triggers the install intent when download completes.
     */
    fun startDownload(info: AppReleaseInfo, onDownloadStarted: () -> Unit = {}) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val destinationFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "GeoTrace_${info.tagName.replace(".", "_")}.apk"
            )

            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(info.apkDownloadUrl)).apply {
                setTitle("Downloading GeoTrace ${info.tagName}")
                setDescription("Official SWE2105 GIS GeoTrace Update")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
                setMimeType("application/vnd.android.package-archive")
            }

            val downloadId = downloadManager.enqueue(request)
            _updateStatus.value = UpdateStatus.Downloading(0)
            onDownloadStarted()

            // Register broadcast receiver to detect download completion
            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(recvContext: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        try {
                            recvContext?.unregisterReceiver(this)
                        } catch (_: Exception) {}

                        if (destinationFile.exists() && destinationFile.length() > 0) {
                            _updateStatus.value = UpdateStatus.ReadyToInstall(destinationFile)
                            installApk(destinationFile)
                        } else {
                            _updateStatus.value = UpdateStatus.Error("Downloaded APK file is empty or corrupted")
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            _updateStatus.value = UpdateStatus.Error("Failed to initiate download: ${e.message}")
        }
    }

    /**
     * Installs the downloaded APK via Intent.ACTION_VIEW and FileProvider.
     */
    fun installApk(file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            _updateStatus.value = UpdateStatus.Error("Failed to launch package installer: ${e.message}")
        }
    }

    fun resetStatus() {
        _updateStatus.value = UpdateStatus.Idle
    }

    /**
     * Compare semantic versions. Example: "v1.2.0" vs "1.1.0"
     */
    private fun isVersionNewer(remoteTag: String, localVersion: String): Boolean {
        val cleanRemote = remoteTag.removePrefix("v").trim()
        val cleanLocal = localVersion.removePrefix("v").trim()

        val remoteParts = cleanRemote.split(".").mapNotNull { it.toIntOrNull() }
        val localParts = cleanLocal.split(".").mapNotNull { it.toIntOrNull() }

        val length = maxOf(remoteParts.size, localParts.size)
        for (i in 0 until length) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}
