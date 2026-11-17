package com.geotrace.ui.screens.devices

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.model.Group
import com.geotrace.model.MemberLocation
import com.geotrace.model.P2PMeshState
import com.geotrace.ui.theme.LocalCustomColors
import com.google.firebase.auth.FirebaseUser

@Composable
fun GhostModeBanner(
    onResumeClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = customColors.statusWarningBg,
        border = BorderStroke(1.dp, customColors.statusWarningBorder),
        modifier = Modifier.fillMaxWidth().testTag("ghost_mode_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    Icons.Default.VisibilityOff,
                    contentDescription = "Ghost Mode Active",
                    tint = customColors.warning,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Ghost Mode Active",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = customColors.statusWarningText
                    )
                    Text(
                        text = "Hidden from shared groups & P2P peers (My Fleet stays active).",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
            TextButton(
                onClick = onResumeClick,
                modifier = Modifier.testTag("resume_sharing_btn")
            ) {
                Text("Resume", fontWeight = FontWeight.Bold, color = customColors.statusWarningText)
            }
        }
    }
}

@Composable
fun ThisDeviceCompactCard(
    offlineCallsign: String,
    currentUser: FirebaseUser?,
    lastSyncTimestampMs: Long?,
    nowMs: Long,
    isGhostMode: Boolean,
    isBackgroundProtectionEnabled: Boolean,
    isProtectionServiceRunning: Boolean,
    isProtectionPaused: Boolean,
    hasBgPermission: Boolean,
    hasNotificationPermission: Boolean,
    isP2PEnabled: Boolean,
    isSyncing: Boolean,
    onEditCallsign: () -> Unit,
    onSyncNow: () -> Unit,
    onToggleGhostMode: () -> Unit,
    onToggleBackgroundProtection: (Boolean) -> Unit,
    onPauseBackgroundProtection: () -> Unit,
    onResumeBackgroundProtection: () -> Unit,
    onOpenBgLocationSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    val syncLabel = if (lastSyncTimestampMs != null) {
        val (ageText, _) = formatFreshness(lastSyncTimestampMs, nowMs, staleAfterMs = 90_000L)
        "Synced $ageText"
    } else if (currentUser != null) {
        "Cloud ready"
    } else {
        "Offline mode"
    }

    val badgeLabel = when {
        isGhostMode -> "GHOST"
        isProtectionPaused -> "PAUSED"
        isBackgroundProtectionEnabled && currentUser != null && isProtectionServiceRunning -> "24/7"
        isBackgroundProtectionEnabled && currentUser != null -> "ARMED"
        else -> "STANDBY"
    }
    val badgeBg = when {
        isGhostMode || isProtectionPaused || (isBackgroundProtectionEnabled && currentUser != null && !isProtectionServiceRunning) ->
            customColors.statusWarningBg
        else -> customColors.statusNominalBg
    }
    val badgeTextColor = when {
        isGhostMode || isProtectionPaused || (isBackgroundProtectionEnabled && currentUser != null && !isProtectionServiceRunning) ->
            customColors.statusWarningText
        else -> customColors.statusNominalText
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("this_device_controls_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = offlineCallsign,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        StatusChipBadge(
                            label = badgeLabel,
                            bg = badgeBg,
                            textColor = badgeTextColor
                        )
                    }
                    Text(
                        text = "${android.os.Build.MODEL} • $syncLabel",
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onEditCallsign,
                        modifier = Modifier.size(48.dp).testTag("edit_callsign_btn")
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename This Device",
                            tint = colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (currentUser != null || isP2PEnabled) {
                        IconButton(
                            onClick = onSyncNow,
                            enabled = !isSyncing,
                            modifier = Modifier.size(48.dp).testTag("sync_now_btn")
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = "Sync Live Telemetry Now",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(48.dp).testTag("toggle_device_controls_btn")
                    ) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Hide Device Controls" else "Show Device Controls",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                if (isGhostMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = if (isGhostMode) customColors.warning else colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Ghost Mode (Hide from Groups & P2P)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = if (isGhostMode) "Group coords cleared • My Fleet stays active" else "Sharing live coordinates",
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isGhostMode,
                            onCheckedChange = { onToggleGhostMode() },
                            colors = SwitchDefaults.colors(checkedThumbColor = customColors.warning),
                            modifier = Modifier.testTag("ghost_mode_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (isBackgroundProtectionEnabled) colorScheme.tertiary else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "24/7 Background Protection",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = when {
                                        !isBackgroundProtectionEnabled -> "Only syncs & rings while app is open"
                                        currentUser == null -> "Armed • Sign in to activate"
                                        isProtectionPaused -> "Paused • Background location sync stopped until resumed"
                                        !isProtectionServiceRunning -> "Service standby • Tap Resume or check Location permission"
                                        !hasNotificationPermission -> "Running • Allow Notifications for persistent status bar badge"
                                        !hasBgPermission -> "Active • Grant 'Allow all the time' location for reboot auto-start"
                                        else -> "Active • Persistent notification, sync & Remote Ring running"
                                    },
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = isBackgroundProtectionEnabled,
                            onCheckedChange = onToggleBackgroundProtection,
                            colors = SwitchDefaults.colors(checkedThumbColor = colorScheme.tertiary),
                            modifier = Modifier.testTag("background_protection_switch")
                        )
                    }

                    if (isBackgroundProtectionEnabled && currentUser != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isProtectionPaused || !isProtectionServiceRunning) {
                                FilledTonalButton(
                                    onClick = onResumeBackgroundProtection,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("resume_protection_btn")
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (isProtectionPaused) "Resume 24/7" else "Start 24/7 Service",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            } else {
                                OutlinedButton(
                                    onClick = onPauseBackgroundProtection,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp).testTag("pause_protection_btn")
                                ) {
                                    Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Pause 24/7", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    if (isBackgroundProtectionEnabled && currentUser != null && !hasNotificationPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenNotificationSettings,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("fix_notif_permission_btn")
                        ) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Allow Notifications for Status Bar Badge", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (isBackgroundProtectionEnabled && currentUser != null && !hasBgPermission) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onOpenBgLocationSettings,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("fix_bg_permission_btn")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Allow Background Location in Settings", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DevicesSubTabBar(
    selectedTab: DevicesSubTab,
    groupsCount: Int,
    fleetCount: Int,
    p2pCount: Int,
    onSelectTab: (DevicesSubTab) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = customColors.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().testTag("devices_subtab_bar")
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DevicesSubTab.entries.forEach { tab ->
                val count = when (tab) {
                    DevicesSubTab.GROUPS -> groupsCount
                    DevicesSubTab.FLEET -> fleetCount
                    DevicesSubTab.P2P_RADAR -> p2pCount
                }
                val isSelected = selectedTab == tab
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) colorScheme.primary else colorScheme.surface.copy(alpha = 0.0f),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectTab(tab) }
                        .testTag(tab.testTag)
                ) {
                    Text(
                        text = if (count > 0) "${tab.label} ($count)" else tab.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(vertical = 11.dp, horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveGroupHeroCard(
    activeGroup: Group?,
    groupMembers: List<MemberLocation>,
    currentUserUid: String?,
    nowMs: Long,
    onCopyCode: (String) -> Unit,
    onShareCode: (Group) -> Unit,
    onLeaveClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val successColor = colorScheme.tertiary

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("active_group_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (activeGroup != null) successColor else customColors.warning)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (activeGroup != null) "ACTIVE GROUP" else "NO GROUP SELECTED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.05.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    if (activeGroup != null && activeGroup.createdBy == currentUserUid) {
                        Spacer(modifier = Modifier.width(6.dp))
                        StatusChipBadge(
                            label = "OWNER",
                            bg = customColors.statusNominalBg,
                            textColor = customColors.statusNominalText
                        )
                    }
                }

                if (activeGroup != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = onLeaveClick,
                            modifier = Modifier.testTag("leave_group_btn")
                        ) {
                            Text("Leave", color = colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (activeGroup.createdBy == currentUserUid) {
                            IconButton(
                                onClick = onDeleteClick,
                                modifier = Modifier.size(48.dp).testTag("delete_group_btn")
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete Group",
                                    tint = colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (activeGroup != null) {
                Text(
                    text = activeGroup.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = customColors.surfaceContainerLow,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "GROUP INVITE CODE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.04.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = activeGroup.inviteCode,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.primary
                            )
                        }

                        Row {
                            IconButton(
                                onClick = { onCopyCode(activeGroup.inviteCode) },
                                modifier = Modifier.size(48.dp).testTag("copy_code_btn")
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy Invite Code",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { onShareCode(activeGroup) },
                                modifier = Modifier.size(48.dp).testTag("share_code_btn")
                            ) {
                                Icon(
                                    Icons.Default.Share,
                                    contentDescription = "Share Invite Code",
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                val accountCount = activeGroup.memberIds.size
                val liveCount = groupMembers.count { m ->
                    val ts = m.lastUpdated?.toDate()?.time
                    ts != null && (nowMs - ts) <= 120_000L
                }
                val offlineCount = (groupMembers.size - liveCount).coerceAtLeast(0)
                val accountLabel = if (accountCount == 1) "1 account" else "$accountCount accounts"
                val deviceSummary = if (offlineCount > 0) {
                    "$liveCount live • $offlineCount offline"
                } else {
                    "$liveCount live device(s)"
                }
                Text(
                    text = "$accountLabel • $deviceSummary",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Join or create a tracking group to coordinate teammates, field equipment, or household devices.",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun P2PRadarControlCard(
    isP2PEnabled: Boolean,
    p2pMeshState: P2PMeshState,
    nearbyPeerCount: Int,
    onToggleP2P: () -> Unit,
    onRescanP2P: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val successColor = colorScheme.tertiary

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(
            1.dp,
            if (isP2PEnabled) successColor.copy(alpha = 0.7f) else colorScheme.outlineVariant
        ),
        modifier = Modifier.fillMaxWidth().testTag("p2p_mesh_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isP2PEnabled) successColor.copy(alpha = 0.14f)
                                else customColors.surfaceContainerLow
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.WifiTethering,
                            contentDescription = "Off-Grid P2P Mesh",
                            tint = if (isP2PEnabled) successColor else colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Off-Grid P2P Device Finder",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = if (isP2PEnabled) p2pMeshState.statusText else "No internet required • BLE & Wi-Fi Direct (50-200m)",
                            fontSize = 11.sp,
                            color = if (p2pMeshState.errorText != null) colorScheme.error else colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isP2PEnabled,
                    onCheckedChange = { onToggleP2P() },
                    colors = SwitchDefaults.colors(checkedThumbColor = successColor),
                    modifier = Modifier.testTag("p2p_mesh_switch")
                )
            }

            if (isP2PEnabled) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (p2pMeshState.isAdvertising) successColor.copy(alpha = 0.12f) else colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = if (p2pMeshState.isAdvertising) "Radio: Advertising" else "Radio: Starting",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (p2pMeshState.isAdvertising) successColor else colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Links: ${p2pMeshState.connectedCount}/${p2pMeshState.discoveredCount} • Peers: $nearbyPeerCount",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = onRescanP2P,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("p2p_rescan_btn")
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Rescan Nearby P2P Peers",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rescan Nearby P2P Mesh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (p2pMeshState.errorText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = p2pMeshState.errorText,
                                fontSize = 11.sp,
                                color = colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AuthGateCard(
    isSigningIn: Boolean,
    onSignInClick: () -> Unit,
    onJumpToP2PClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("auth_gate_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Sign in required",
                    tint = colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Cloud Sync & Group Sharing",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Text(
                text = "Sign in with Google to track all your phones and tablets on one map, ring misplaced devices remotely, and share tracking groups.",
                fontSize = 12.sp,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = onSignInClick,
                enabled = !isSigningIn,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("google_sign_in_btn")
            ) {
                if (isSigningIn) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign in with Google", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            TextButton(onClick = onJumpToP2PClick) {
                Text("Offline? Use P2P Radar without signing in", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun AccountProfileStrip(
    currentUser: FirebaseUser,
    onRequestSignOut: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val displayTitle = currentUser.displayName?.takeIf { it.isNotBlank() }
        ?: currentUser.email?.takeIf { it.isNotBlank() }
        ?: "Field Operator"

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = customColors.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayTitle.firstOrNull()?.uppercase() ?: "U",
                        color = colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = displayTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = currentUser.email?.takeIf { it.isNotBlank() } ?: currentUser.uid.take(12),
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onRequestSignOut,
                modifier = Modifier.size(48.dp).testTag("sign_out_btn")
            ) {
                Icon(
                    Icons.Default.ExitToApp,
                    contentDescription = "Sign Out",
                    tint = colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
