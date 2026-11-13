package com.geotrace.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
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
    data class UpToDate(val latestTag: String? = null) : UpdateStatus()
    object NoReleasesPublished : UpdateStatus()
    data class Downloading(val progressPercent: Int) : UpdateStatus()
    data class ReadyToInstall(val apkFile: File) : UpdateStatus()
    data class InstallPermissionRequired(val apkFile: File) : UpdateStatus()
    data class Error(val message: String) : UpdateStatus()
}

/**
 * AppUpdateManager checks GitHub Releases for newer GeoTrace APKs,
 * compares semantic versions against BuildConfig.VERSION_NAME,
 * downloads release APKs via Android DownloadManager, verifies unknown-app-sources permission,
 * and launches secure package installation via FileProvider.
 */
class AppUpdateManager(private val context: Context) {

    private val _updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    companion object {
        const val GITHUB_REPO = "mugmarp/Group_14_GIS__BSE_SWE2105_project_task_2026"
        const val RELEASES_WEB_URL = "https://github.com/$GITHUB_REPO/releases"
        private const val API_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    }

    suspend fun checkForUpdates(): UpdateStatus = withContext(Dispatchers.IO) {
        _updateStatus.value = UpdateStatus.Checking
        var connection: HttpURLConnection? = null
        try {
            val url = URL(API_URL)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "GeoTrace-Android-${BuildConfig.VERSION_NAME}")
            }

            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_OK) {
                val responseStr = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                val json = JSONObject(responseStr)
                val tagName = json.optString("tag_name", "v1.0.0")
                val releaseName = json.optString("name", tagName)
                val body = json.optString("body", "Routine bug fixes and telemetry engine optimizations.")
                val publishedAt = json.optString("published_at", "")

                var apkDownloadUrl = ""
                var apkFileName = "geotrace-release.apk"
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
                            if (name.contains("release", ignoreCase = true)) {
                                break
                            }
                        }
                    }
                }

                if (apkDownloadUrl.isEmpty()) {
                    apkDownloadUrl = "https://github.com/$GITHUB_REPO/releases/download/$tagName/geotrace-release.apk"
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
                    UpdateStatus.UpToDate(tagName)
                }
                _updateStatus.value = result
                result
            } else if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                _updateStatus.value = UpdateStatus.NoReleasesPublished
                UpdateStatus.NoReleasesPublished
            } else {
                val errMsg = when (code) {
                    403 -> "GitHub API rate limit reached (60/hr). Open GitHub Releases in browser to check manually."
                    else -> "GitHub responded with HTTP $code (${connection.responseMessage})."
                }
                val errStatus = UpdateStatus.Error(errMsg)
                _updateStatus.value = errStatus
                errStatus
            }
        } catch (e: Exception) {
            val errStatus = UpdateStatus.Error(e.message ?: "Network error while checking GitHub releases")
            _updateStatus.value = errStatus
            errStatus
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Downloads the release APK via DownloadManager and prompts installation when complete.
     */
    fun startDownload(info: AppReleaseInfo, onDownloadStarted: () -> Unit = {}) {
        try {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val sanitizedTag = info.tagName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val destinationFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "GeoTrace_$sanitizedTag.apk"
            )

            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val request = DownloadManager.Request(Uri.parse(info.apkDownloadUrl)).apply {
                setTitle("Downloading GeoTrace ${info.tagName}")
                setDescription("Official GitHub Release APK")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationUri(Uri.fromFile(destinationFile))
                setMimeType("application/vnd.android.package-archive")
            }

            val downloadId = downloadManager.enqueue(request)
            _updateStatus.value = UpdateStatus.Downloading(0)
            onDownloadStarted()

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            val appContext = context.applicationContext
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(recvContext: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id != downloadId) return

                    try {
                        appContext.unregisterReceiver(this)
                    } catch (_: Exception) {}

                    val query = DownloadManager.Query().setFilterById(downloadId)
                    var succeeded = false
                    var reasonCode = -1
                    downloadManager.query(query)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val statusIdx = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                            val reasonIdx = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            if (statusIdx >= 0) {
                                succeeded = cursor.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL
                            }
                            if (reasonIdx >= 0) {
                                reasonCode = cursor.getInt(reasonIdx)
                            }
                        }
                    }

                    if (succeeded && destinationFile.exists() && destinationFile.length() > 0) {
                        _updateStatus.value = UpdateStatus.ReadyToInstall(destinationFile)
                        installApk(destinationFile)
                    } else {
                        _updateStatus.value = UpdateStatus.Error(
                            "APK download did not complete (reason $reasonCode). Try opening GitHub Releases in browser."
                        )
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                appContext.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            _updateStatus.value = UpdateStatus.Error("Failed to initiate download: ${e.message}")
        }
    }

    /**
     * Checks Install Unknown Apps permission on Android 8+ before launching FileProvider package installer.
     */
    fun installApk(file: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                _updateStatus.value = UpdateStatus.InstallPermissionRequired(file)
                return
            }

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

    fun openInstallPermissionSettings() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        } catch (e: Exception) {
            _updateStatus.value = UpdateStatus.Error("Unable to open Install Unknown Apps settings: ${e.message}")
        }
    }

    fun openReleasesPage() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_WEB_URL)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _updateStatus.value = UpdateStatus.Error("No browser found to open GitHub Releases.")
        }
    }

    fun resetStatus() {
        _updateStatus.value = UpdateStatus.Idle
    }

    /**
     * Compare semantic versions cleanly (e.g. "v1.2.0" vs "1.0.0").
     */
    private fun isVersionNewer(remoteTag: String, localVersion: String): Boolean {
        val cleanRemote = remoteTag.removePrefix("v").substringBefore("-").trim()
        val cleanLocal = localVersion.removePrefix("v").substringBefore("-").trim()

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
