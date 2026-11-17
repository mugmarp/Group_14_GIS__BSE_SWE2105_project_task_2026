package com.geotrace.ui.screens.devices

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import com.geotrace.model.Group
import com.geotrace.model.LocationPoint
import com.geotrace.model.MemberLocation
import com.geotrace.model.PersonalDevice
import java.util.Locale

enum class DevicesSubTab(val label: String, val testTag: String) {
    GROUPS("Groups", "devices_tab_groups"),
    FLEET("My Fleet", "devices_tab_fleet"),
    P2P_RADAR("P2P Radar", "devices_tab_p2p")
}

fun hasBackgroundLocationPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

fun openAppLocationSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun openAppNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    } else {
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        openAppLocationSettings(context)
    }
}

fun resolveBatteryIcon(isCharging: Boolean, batteryPercent: Int): ImageVector {
    return when {
        isCharging -> Icons.Default.Bolt
        batteryPercent <= 20 -> Icons.Default.BatteryAlert
        else -> Icons.Default.BatteryFull
    }
}

fun sortGroupMembers(
    members: List<MemberLocation>,
    currentUserUid: String?,
    currentDeviceId: String,
    nowMs: Long
): List<MemberLocation> {
    return members.sortedWith(
        compareByDescending<MemberLocation> { member ->
            member.userId == currentUserUid &&
                (member.deviceId.isBlank() || member.deviceId == currentDeviceId)
        }
            .thenByDescending { member ->
                val ts = member.lastUpdated?.toDate()?.time
                ts != null && (nowMs - ts) <= 90_000L
            }
            .thenByDescending { member ->
                member.lastUpdated?.toDate()?.time ?: 0L
            }
            .thenBy { it.displayName.lowercase() }
    )
}

fun sortPersonalDevices(devices: List<PersonalDevice>, nowMs: Long): List<PersonalDevice> {
    return devices.sortedWith(
        compareByDescending<PersonalDevice> { dev ->
            val ts = dev.lastUpdated?.toDate()?.time
            ts != null && (nowMs - ts) <= 90_000L
        }
            .thenByDescending { dev ->
                dev.lastUpdated?.toDate()?.time ?: 0L
            }
            .thenBy { (it.deviceName.ifBlank { it.deviceModel }).lowercase() }
    )
}

fun sortUserGroups(
    groups: List<Group>,
    activeGroupId: String?,
    currentUserUid: String?
): List<Group> {
    return groups.sortedWith(
        compareByDescending<Group> { it.id == activeGroupId }
            .thenByDescending { currentUserUid != null && it.createdBy == currentUserUid }
            .thenBy { it.name.lowercase() }
    )
}

fun formatTrackingModeLabel(rawMode: String): String {
    return when (rawMode.uppercase()) {
        "HIGH_ACCURACY" -> "GPS High"
        "BALANCED" -> "Balanced"
        "POWER_SAVER" -> "Cell Only"
        else -> rawMode.replace('_', ' ').lowercase()
            .replaceFirstChar { it.uppercase() }
    }
}

fun formatFreshness(timestampMs: Long?, nowMs: Long, staleAfterMs: Long): Pair<String, Boolean> {
    if (timestampMs == null || timestampMs <= 0L) return "Syncing" to false
    val ageMs = (nowMs - timestampMs).coerceAtLeast(0L)
    val ageSec = ageMs / 1000L
    val label = when {
        ageSec < 8L -> "Live"
        ageSec < 60L -> "${ageSec}s ago"
        ageSec < 3600L -> "${ageSec / 60L}m ago"
        ageSec < 86_400L -> "Offline • ${ageSec / 3600L}h ago"
        else -> "Offline • ${ageSec / 86_400L}d ago"
    }
    return label to (ageMs > staleAfterMs)
}

fun toCompassCardinal(bearingDegrees: Float): String {
    val normalized = ((bearingDegrees % 360f) + 360f) % 360f
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = ((normalized + 22.5f) / 45f).toInt() % 8
    return dirs[idx]
}

fun formatDistanceBearing(
    userLocation: LocationPoint?,
    targetLat: Double,
    targetLng: Double,
    distanceMeters: Float,
    bearingDeg: Float,
    fallbackText: String = "--"
): String {
    if (userLocation == null || (targetLat == 0.0 && targetLng == 0.0)) return fallbackText
    val compass = toCompassCardinal(bearingDeg)
    val distPart = if (distanceMeters < 1000f) {
        "${distanceMeters.toInt()} m"
    } else {
        String.format(Locale.US, "%.1f km", distanceMeters / 1000.0)
    }
    return "$distPart $compass"
}
