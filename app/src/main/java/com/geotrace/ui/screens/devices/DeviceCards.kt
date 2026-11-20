package com.geotrace.ui.screens.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.model.Group
import com.geotrace.model.LocationPoint
import com.geotrace.model.MemberLocation
import com.geotrace.model.P2PPeer
import com.geotrace.model.PersonalDevice
import com.geotrace.model.SpeedUnit
import com.geotrace.ui.theme.LocalCustomColors
import java.util.Locale

@Composable
fun MemberCard(
    member: MemberLocation,
    userLocation: LocationPoint?,
    isSelf: Boolean,
    isGroupOwner: Boolean,
    canRemove: Boolean,
    matchingOwnDevice: PersonalDevice?,
    nowMs: Long,
    speedUnit: SpeedUnit = SpeedUnit.KMH,
    onLocateClick: () -> Unit,
    onRingOwnDeviceClick: ((PersonalDevice) -> Unit)?,
    onRemoveClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val successColor = colorScheme.tertiary

    val hasValidCoords = member.latitude != 0.0 || member.longitude != 0.0
    val distanceText = if (isSelf) {
        "Current Device"
    } else {
        formatDistanceBearing(
            userLocation = userLocation,
            targetLat = member.latitude,
            targetLng = member.longitude,
            distanceMeters = if (userLocation != null && hasValidCoords) {
                member.distanceToMeters(userLocation.latitude, userLocation.longitude)
            } else 0f,
            bearingDeg = if (userLocation != null && hasValidCoords) {
                member.bearingFrom(userLocation.latitude, userLocation.longitude)
            } else 0f
        )
    }

    val updatedMs = member.lastUpdated?.toDate()?.time
    val (freshnessText, isStale) = formatFreshness(updatedMs, nowMs, staleAfterMs = 90_000L)

    val parsedAvatarColor = try {
        Color(android.graphics.Color.parseColor(member.avatarColor))
    } catch (_: Exception) {
        colorScheme.primary
    }

    val speedStr = String.format(Locale.US, "%.1f", member.speed * speedUnit.multiplier)
    val motionSummary = if (isStale) {
        "$distanceText • Last known $speedStr ${speedUnit.label}"
    } else {
        "$distanceText • ${member.gait} ($speedStr ${speedUnit.label})"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelf) customColors.surfaceContainerLow else colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isSelf) colorScheme.primary.copy(alpha = 0.4f) else colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier.fillMaxWidth().testTag("member_card_${member.id.ifBlank { member.userId }}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(parsedAvatarColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.displayName.take(2).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = member.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isSelf) {
                        StatusChipBadge(
                            label = "THIS DEVICE",
                            bg = colorScheme.primary.copy(alpha = 0.12f),
                            textColor = colorScheme.primary
                        )
                    } else if (matchingOwnDevice != null) {
                        StatusChipBadge(
                            label = "MY FLEET",
                            bg = colorScheme.secondary.copy(alpha = 0.12f),
                            textColor = colorScheme.secondary
                        )
                    } else if (isGroupOwner) {
                        StatusChipBadge(
                            label = "OWNER",
                            bg = customColors.statusNominalBg,
                            textColor = customColors.statusNominalText
                        )
                    }
                }

                if (!isSelf && member.deviceModel.isNotBlank()) {
                    Text(
                        text = member.deviceModel,
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = motionSummary,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val lowBattery = member.batteryPercent <= 20
                    Icon(
                        imageVector = resolveBatteryIcon(member.isCharging, member.batteryPercent),
                        contentDescription = if (member.isCharging) "Charging" else "Battery level",
                        tint = if (lowBattery) colorScheme.error else successColor,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${member.batteryPercent}% • ${formatTrackingModeLabel(member.trackingMode)} • ",
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = freshnessText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isStale) customColors.warning else successColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            if (matchingOwnDevice != null && onRingOwnDeviceClick != null) {
                IconButton(
                    onClick = { onRingOwnDeviceClick(matchingOwnDevice) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("ring_member_own_${member.deviceId.ifBlank { member.id }}")
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        contentDescription = "Ring ${member.displayName}",
                        tint = colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (!isSelf && hasValidCoords) {
                IconButton(
                    onClick = onLocateClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("locate_member_${member.id.ifBlank { member.userId }}")
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Locate ${member.displayName} on Map",
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (canRemove) {
                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("remove_member_${member.id.ifBlank { member.userId }}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove ${member.displayName} from group",
                        tint = colorScheme.error,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PersonalDeviceCard(
    device: PersonalDevice,
    userLocation: LocationPoint?,
    nowMs: Long,
    onLocateClick: () -> Unit,
    onRingClick: () -> Unit,
    onCancelRingClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val fleetAccent = colorScheme.secondary
    val successColor = colorScheme.tertiary
    val hasValidCoords = device.latitude != 0.0 || device.longitude != 0.0

    val distanceText = formatDistanceBearing(
        userLocation = userLocation,
        targetLat = device.latitude,
        targetLng = device.longitude,
        distanceMeters = if (userLocation != null && hasValidCoords) {
            device.distanceToMeters(userLocation.latitude, userLocation.longitude)
        } else 0f,
        bearingDeg = if (userLocation != null && hasValidCoords) {
            device.bearingFrom(userLocation.latitude, userLocation.longitude)
        } else 0f,
        fallbackText = "Position syncing..."
    )

    val updatedMs = device.lastUpdated?.toDate()?.time
    val (freshnessText, isStale) = formatFreshness(updatedMs, nowMs, staleAfterMs = 90_000L)
    val isRingPending = device.ringRequestedAt > 0L && (nowMs - device.ringRequestedAt) in -5_000L..60_000L

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, fleetAccent.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth().testTag("personal_device_card_${device.deviceId}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(fleetAccent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Devices,
                        contentDescription = "Linked Device",
                        tint = fleetAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = device.deviceName.ifBlank { device.deviceModel.ifBlank { "GeoTrace Device" } },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${device.deviceModel} • $distanceText",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        val lowBattery = device.batteryPercent <= 20
                        Icon(
                            imageVector = resolveBatteryIcon(device.isCharging, device.batteryPercent),
                            contentDescription = if (device.isCharging) "Charging" else "Battery level",
                            tint = if (lowBattery) colorScheme.error else successColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${device.batteryPercent}% • ${formatTrackingModeLabel(device.trackingMode)} • ",
                            fontSize = 10.sp,
                            color = colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = freshnessText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isStale) customColors.warning else successColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("remove_device_${device.deviceId}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove ${device.deviceName}",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onLocateClick,
                    enabled = hasValidCoords,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("locate_device_${device.deviceId}")
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Locate on Map",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Locate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (isRingPending) {
                    Button(
                        onClick = onCancelRingClick,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = customColors.warning),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cancel_ring_${device.deviceId}")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel Pending Ring",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancel Ring", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onRingClick,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("ring_device_${device.deviceId}")
                    ) {
                        Icon(
                            Icons.Default.VolumeUp,
                            contentDescription = "Ring Device",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Ring", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun P2PPeerCard(
    peer: P2PPeer,
    userLocation: LocationPoint?,
    lastRingSentAtMs: Long,
    nowMs: Long,
    speedUnit: SpeedUnit = SpeedUnit.KMH,
    onLocatePeer: () -> Unit,
    onRingPeer: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val successColor = colorScheme.tertiary

    val distanceText = if (peer.hasValidCoordinates) {
        formatDistanceBearing(
            userLocation = userLocation,
            targetLat = peer.latitude,
            targetLng = peer.longitude,
            distanceMeters = if (userLocation != null) peer.distanceToMeters(userLocation.latitude, userLocation.longitude) else 0f,
            bearingDeg = if (userLocation != null) peer.bearingFrom(userLocation.latitude, userLocation.longitude) else 0f,
            fallbackText = "GPS pending"
        )
    } else {
        "GPS pending"
    }

    val (ageText, isStale) = formatFreshness(peer.lastSeenTimestamp, nowMs, staleAfterMs = 30_000L)
    val isRingCoolingDown = lastRingSentAtMs > 0L && (nowMs - lastRingSentAtMs) in 0L..8_000L

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isStale) customColors.warning.copy(alpha = 0.6f) else successColor.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth().testTag("p2p_peer_card_${peer.endpointId}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isStale) customColors.statusWarningBg else successColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.NearMe,
                        contentDescription = "P2P Peer",
                        tint = if (isStale) customColors.warning else successColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = peer.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = if (peer.hasValidCoordinates) {
                            "$distanceText • ${String.format(Locale.US, "%.1f", peer.speed * speedUnit.multiplier)} ${speedUnit.label}"
                        } else {
                            "Connected • GPS pending"
                        },
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Battery: ${peer.batteryPercent}% • Direct BLE/Wi-Fi • $ageText",
                        fontSize = 10.sp,
                        color = if (isStale) customColors.warning else successColor,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onLocatePeer,
                    enabled = peer.hasValidCoordinates,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("locate_p2p_${peer.endpointId}")
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Locate Peer on Map",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Locate", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                FilledTonalButton(
                    onClick = onRingPeer,
                    enabled = !isRingCoolingDown,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("ring_p2p_${peer.endpointId}")
                ) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = "Ring Peer Device",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (isRingCoolingDown) "Ringing..." else "Ring",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun GroupRowCard(
    group: Group,
    isActive: Boolean,
    isOwner: Boolean,
    onSelect: () -> Unit,
    onDeleteOrLeave: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val count = group.memberIds.size

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) colorScheme.primaryContainer.copy(alpha = 0.22f) else colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (isActive) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
            .testTag("group_card_${group.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = group.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isOwner) {
                        StatusChipBadge(
                            label = "OWNER",
                            bg = customColors.statusNominalBg,
                            textColor = customColors.statusNominalText
                        )
                    }
                }
                Text(
                    text = "Code: ${group.inviteCode} • $count ${if (count == 1) "Account" else "Accounts"}",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.primary
                    ) {
                        Text(
                            text = "ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onSelect,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text("Select", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                IconButton(
                    onClick = onDeleteOrLeave,
                    modifier = Modifier.size(48.dp).testTag("group_row_action_${group.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = if (isOwner) "Delete ${group.name}" else "Leave ${group.name}",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PendingMemberCard(
    memberUid: String,
    isSelf: Boolean,
    isGroupOwner: Boolean,
    isGhostMode: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val title = when {
        isSelf -> "This Device"
        isGroupOwner -> "Group Owner (${memberUid.take(6)}…)"
        else -> "Joined Member (${memberUid.take(6)}…)"
    }
    val subtitle = when {
        isSelf && isGhostMode -> "Ghost Mode active — coordinates hidden from this group"
        isSelf -> "Joined • Acquiring initial GPS/Wi-Fi fix to publish…"
        else -> "Joined group • Waiting for device's first GPS/Wi-Fi broadcast"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = customColors.surfaceContainerLow),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_member_card_$memberUid")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colorScheme.outlineVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Joined Group Member",
                    tint = colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    StatusChipBadge(
                        label = if (isSelf && isGhostMode) "HIDDEN" else "JOINED",
                        bg = customColors.statusWarningBg,
                        textColor = customColors.statusWarningText
                    )
                    if (isGroupOwner) {
                        StatusChipBadge(
                            label = "OWNER",
                            bg = customColors.statusNominalBg,
                            textColor = customColors.statusNominalText
                        )
                    }
                }
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun StatusChipBadge(
    label: String,
    bg: Color,
    textColor: Color
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04.sp,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
