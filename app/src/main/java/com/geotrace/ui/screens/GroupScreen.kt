package com.geotrace.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.geotrace.model.Group
import com.geotrace.model.LocationPoint
import com.geotrace.model.MapFocusTarget
import com.geotrace.model.MemberLocation
import com.geotrace.model.PersonalDevice
import com.geotrace.model.SpeedUnit
import com.geotrace.ui.screens.devices.AccountProfileStrip
import com.geotrace.ui.screens.devices.ActiveGroupHeroCard
import com.geotrace.ui.screens.devices.AuthGateCard
import com.geotrace.ui.screens.devices.BackgroundLocationSettingsDialog
import com.geotrace.ui.screens.devices.CreateGroupDialog
import com.geotrace.ui.screens.devices.DeleteGroupDialog
import com.geotrace.ui.screens.devices.DevicesSubTab
import com.geotrace.ui.screens.devices.DevicesSubTabBar
import com.geotrace.ui.screens.devices.GhostModeBanner
import com.geotrace.ui.screens.devices.GroupRowCard
import com.geotrace.ui.screens.devices.JoinGroupDialog
import com.geotrace.ui.screens.devices.LeaveGroupConfirmDialog
import com.geotrace.ui.screens.devices.MemberCard
import com.geotrace.ui.screens.devices.P2PPeerCard
import com.geotrace.ui.screens.devices.P2PRadarControlCard
import com.geotrace.ui.screens.devices.PersonalDeviceCard
import com.geotrace.ui.screens.devices.RemoveDeviceDialog
import com.geotrace.ui.screens.devices.RemoveGroupMemberDialog
import com.geotrace.ui.screens.devices.RenameDeviceDialog
import com.geotrace.ui.screens.devices.SignOutConfirmDialog
import com.geotrace.ui.screens.devices.ThisDeviceCompactCard
import com.geotrace.ui.screens.devices.hasBackgroundLocationPermission
import com.geotrace.ui.screens.devices.openAppLocationSettings
import com.geotrace.ui.screens.devices.performGoogleSignIn
import com.geotrace.ui.screens.devices.performSignOut
import com.geotrace.ui.screens.devices.sortGroupMembers
import com.geotrace.ui.screens.devices.sortPersonalDevices
import com.geotrace.ui.screens.devices.sortUserGroups
import com.geotrace.viewmodel.GroupViewModel
import kotlinx.coroutines.delay

@Composable
fun GroupScreen(
    groupViewModel: GroupViewModel,
    currentLocation: LocationPoint?,
    speedUnit: SpeedUnit = SpeedUnit.KMH,
    onLocateOnMap: (MapFocusTarget) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()

    val currentUser by groupViewModel.currentUser.collectAsState()
    val userGroups by groupViewModel.userGroups.collectAsState()
    val activeGroup by groupViewModel.activeGroup.collectAsState()
    val groupMembers by groupViewModel.groupMembers.collectAsState()
    val personalDevices by groupViewModel.personalDevices.collectAsState()
    val nearbyPeers by groupViewModel.nearbyPeers.collectAsState()
    val p2pMeshState by groupViewModel.p2pMeshState.collectAsState()
    val p2pRingCooldownsMs by groupViewModel.p2pRingCooldownsMs.collectAsState()
    val offlineCallsign by groupViewModel.offlineCallsign.collectAsState()
    val isGhostMode by groupViewModel.isGhostMode.collectAsState()
    val isBackgroundProtectionEnabled by groupViewModel.isBackgroundProtectionEnabled.collectAsState()
    val isP2PEnabled by groupViewModel.isP2PEnabled.collectAsState()
    val isSyncing by groupViewModel.isSyncing.collectAsState()
    val lastSyncTimestampMs by groupViewModel.lastSyncTimestampMs.collectAsState()
    val statusMessage by groupViewModel.statusMessage.collectAsState()
    val errorMessage by groupViewModel.errorMessage.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(DevicesSubTab.GROUPS) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showCallsignDialog by remember { mutableStateOf(false) }
    var showSignOutConfirmDialog by remember { mutableStateOf(false) }
    var showBgSettingsDialog by remember { mutableStateOf(false) }
    var groupPendingDelete by remember { mutableStateOf<Group?>(null) }
    var groupPendingLeave by remember { mutableStateOf<Group?>(null) }
    var devicePendingRemoval by remember { mutableStateOf<PersonalDevice?>(null) }
    var memberPendingRemoval by remember { mutableStateOf<MemberLocation?>(null) }
    var newGroupName by remember { mutableStateOf("") }
    var inviteCodeInput by remember { mutableStateOf("") }
    var callsignInput by remember(offlineCallsign) { mutableStateOf(offlineCallsign) }
    var isAuthSigningIn by remember { mutableStateOf(false) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    val bgLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted && !hasBackgroundLocationPermission(context)) {
            showBgSettingsDialog = true
        }
    }

    val protectionPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val fineOrCoarse = (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) ||
            (grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true)
        if (fineOrCoarse) {
            groupViewModel.toggleBackgroundProtection(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission(context)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    showBgSettingsDialog = true
                } else {
                    bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
            }
        } else {
            Toast.makeText(
                context,
                "Location permission is required for 24/7 Background Device Protection.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val radioPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.all { it }) {
            groupViewModel.toggleP2PMesh()
        } else {
            Toast.makeText(
                context,
                "Location & Bluetooth permissions are required for the Off-Grid P2P Device Finder.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestP2PMeshWithPermissions = {
        if (!isP2PEnabled) {
            val missing = buildList {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    add(Manifest.permission.ACCESS_FINE_LOCATION)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        add(Manifest.permission.BLUETOOTH_SCAN)
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                        add(Manifest.permission.BLUETOOTH_ADVERTISE)
                    }
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) {
                        add(Manifest.permission.NEARBY_WIFI_DEVICES)
                    }
                }
            }
            if (missing.isNotEmpty()) {
                radioPermissionsLauncher.launch(missing.toTypedArray())
            } else {
                groupViewModel.toggleP2PMesh()
            }
        } else {
            groupViewModel.toggleP2PMesh()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(4_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            groupViewModel.clearStatusMessage()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            groupViewModel.clearErrorMessage()
        }
    }

    val otherPersonalDevices = remember(personalDevices, groupViewModel.currentDeviceId, nowMs) {
        sortPersonalDevices(
            personalDevices.filter { it.deviceId != groupViewModel.currentDeviceId && it.id != groupViewModel.currentDeviceId },
            nowMs
        )
    }
    val personalDeviceById = remember(otherPersonalDevices) {
        otherPersonalDevices.associateBy { it.deviceId.ifBlank { it.id } }
    }
    val sortedMembers = remember(groupMembers, currentUser?.uid, groupViewModel.currentDeviceId, nowMs) {
        sortGroupMembers(groupMembers, currentUser?.uid, groupViewModel.currentDeviceId, nowMs)
    }
    val sortedGroups = remember(userGroups, activeGroup?.id, currentUser?.uid) {
        sortUserGroups(userGroups, activeGroup?.id, currentUser?.uid)
    }
    val hasBgPermission = hasBackgroundLocationPermission(context)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = "Devices & Tracking Groups",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground
                )
                Text(
                    text = "Shared cloud groups, personal device fleet & off-grid P2P radar",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        if (isGhostMode) {
            item {
                GhostModeBanner(onResumeClick = { groupViewModel.toggleGhostMode() })
            }
        }

        item {
            ThisDeviceCompactCard(
                offlineCallsign = offlineCallsign,
                currentUser = currentUser,
                lastSyncTimestampMs = lastSyncTimestampMs,
                nowMs = nowMs,
                isGhostMode = isGhostMode,
                isBackgroundProtectionEnabled = isBackgroundProtectionEnabled,
                hasBgPermission = hasBgPermission,
                isP2PEnabled = isP2PEnabled,
                isSyncing = isSyncing,
                onEditCallsign = {
                    callsignInput = offlineCallsign
                    showCallsignDialog = true
                },
                onSyncNow = { groupViewModel.syncNow() },
                onToggleGhostMode = { groupViewModel.toggleGhostMode() },
                onToggleBackgroundProtection = { enable ->
                    if (!enable) {
                        groupViewModel.toggleBackgroundProtection(false)
                    } else {
                        val needed = mutableListOf<String>()
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            needed.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        if (needed.isNotEmpty()) {
                            protectionPermissionsLauncher.launch(needed.toTypedArray())
                        } else {
                            groupViewModel.toggleBackgroundProtection(true)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBgPermission) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    showBgSettingsDialog = true
                                } else {
                                    bgLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                }
                            }
                        }
                    }
                },
                onOpenBgLocationSettings = { showBgSettingsDialog = true }
            )
        }

        item {
            DevicesSubTabBar(
                selectedTab = selectedTab,
                groupsCount = userGroups.size,
                fleetCount = otherPersonalDevices.size,
                p2pCount = nearbyPeers.size,
                onSelectTab = { selectedTab = it }
            )
        }

        when (selectedTab) {
            DevicesSubTab.GROUPS -> {
                if (currentUser == null) {
                    item {
                        AuthGateCard(
                            isSigningIn = isAuthSigningIn,
                            onSignInClick = {
                                isAuthSigningIn = true
                                performGoogleSignIn(
                                    context = context,
                                    scope = scope,
                                    onSuccess = { isAuthSigningIn = false },
                                    onError = { err ->
                                        isAuthSigningIn = false
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            onJumpToP2PClick = { selectedTab = DevicesSubTab.P2P_RADAR }
                        )
                    }
                } else {
                    item {
                        ActiveGroupHeroCard(
                            activeGroup = activeGroup,
                            groupMembers = sortedMembers,
                            currentUserUid = currentUser?.uid,
                            nowMs = nowMs,
                            onCopyCode = { code ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("GeoTrace Invite Code", code))
                                Toast.makeText(context, "Code copied: $code", Toast.LENGTH_SHORT).show()
                            },
                            onShareCode = { grp ->
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Join my GeoTrace Tracking Group '${grp.name}' with Invite Code: ${grp.inviteCode}"
                                    )
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share Invite Code"))
                            },
                            onLeaveClick = { activeGroup?.let { groupPendingLeave = it } },
                            onDeleteClick = { activeGroup?.let { groupPendingDelete = it } }
                        )
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { showCreateDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("create_group_btn")
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Group", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            OutlinedButton(
                                onClick = { showJoinDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, colorScheme.primary),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("join_group_btn")
                            ) {
                                Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Join Code", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    if (activeGroup != null) {
                        item {
                            Text(
                                text = "DEVICES IN THIS GROUP (${sortedMembers.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.05.sp,
                                color = colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        if (sortedMembers.isEmpty()) {
                            item {
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                    border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (isGhostMode) {
                                            "Ghost Mode is hiding your device. Other members sharing live coordinates appear here."
                                        } else {
                                            "Awaiting device location broadcasts. Share invite code '${activeGroup!!.inviteCode}' with other devices."
                                        },
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                        } else {
                            items(sortedMembers, key = { it.id.ifBlank { "${it.userId}_${it.deviceId}" } }) { member ->
                                val isSelfDevice = member.userId == currentUser?.uid &&
                                    (member.deviceId.isBlank() || member.deviceId == groupViewModel.currentDeviceId)
                                val isGroupCreator = activeGroup?.createdBy == currentUser?.uid
                                val canRemoveMember = !isSelfDevice && (isGroupCreator || member.userId == currentUser?.uid)
                                val matchingOwn = if (!isSelfDevice && member.userId == currentUser?.uid && member.deviceId.isNotBlank()) {
                                    personalDeviceById[member.deviceId]
                                } else null

                                MemberCard(
                                    member = member,
                                    userLocation = currentLocation,
                                    isSelf = isSelfDevice,
                                    isGroupOwner = member.userId == activeGroup?.createdBy,
                                    canRemove = canRemoveMember,
                                    matchingOwnDevice = matchingOwn,
                                    nowMs = nowMs,
                                    speedUnit = speedUnit,
                                    onLocateClick = {
                                        onLocateOnMap(
                                            MapFocusTarget(
                                                id = member.id.ifBlank { member.userId },
                                                label = member.displayName,
                                                latitude = member.latitude,
                                                longitude = member.longitude,
                                                subtitle = "${member.gait} • ${member.batteryPercent}%"
                                            )
                                        )
                                    },
                                    onRingOwnDeviceClick = { ownDev -> groupViewModel.ringPersonalDevice(ownDev) },
                                    onRemoveClick = { memberPendingRemoval = member }
                                )
                            }
                        }
                    }

                    if (sortedGroups.isNotEmpty()) {
                        item {
                            Text(
                                text = "YOUR TRACKING GROUPS (${sortedGroups.size})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.05.sp,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        items(sortedGroups, key = { it.id }) { grp ->
                            val isOwner = grp.createdBy == currentUser?.uid
                            GroupRowCard(
                                group = grp,
                                isActive = grp.id == activeGroup?.id,
                                isOwner = isOwner,
                                onSelect = { groupViewModel.selectGroup(grp) },
                                onDeleteOrLeave = {
                                    if (isOwner && grp.memberIds.size <= 1) {
                                        groupPendingDelete = grp
                                    } else if (isOwner) {
                                        groupPendingDelete = grp
                                    } else {
                                        groupPendingLeave = grp
                                    }
                                }
                            )
                        }
                    }
                }
            }

            DevicesSubTab.FLEET -> {
                if (currentUser == null) {
                    item {
                        AuthGateCard(
                            isSigningIn = isAuthSigningIn,
                            onSignInClick = {
                                isAuthSigningIn = true
                                performGoogleSignIn(
                                    context = context,
                                    scope = scope,
                                    onSuccess = { isAuthSigningIn = false },
                                    onError = { err ->
                                        isAuthSigningIn = false
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            onJumpToP2PClick = { selectedTab = DevicesSubTab.P2P_RADAR }
                        )
                    }
                } else {
                    item {
                        AccountProfileStrip(
                            currentUser = currentUser!!,
                            onRequestSignOut = { showSignOutConfirmDialog = true }
                        )
                    }

                    item {
                        Text(
                            text = "MY OTHER DEVICES (${otherPersonalDevices.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.05.sp,
                            color = colorScheme.secondary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (otherPersonalDevices.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Devices,
                                        contentDescription = "Linked Devices",
                                        tint = colorScheme.secondary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Sign into GeoTrace with the same Google account on another phone or tablet to track it here and ring it remotely when misplaced.",
                                        fontSize = 12.sp,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(otherPersonalDevices, key = { it.deviceId.ifBlank { it.id } }) { device ->
                            PersonalDeviceCard(
                                device = device,
                                userLocation = currentLocation,
                                nowMs = nowMs,
                                onLocateClick = {
                                    onLocateOnMap(
                                        MapFocusTarget(
                                            id = device.deviceId.ifBlank { device.id },
                                            label = device.deviceName.ifBlank { device.deviceModel },
                                            latitude = device.latitude,
                                            longitude = device.longitude,
                                            subtitle = "${device.deviceModel} • ${device.batteryPercent}%"
                                        )
                                    )
                                },
                                onRingClick = { groupViewModel.ringPersonalDevice(device) },
                                onCancelRingClick = { groupViewModel.cancelRingOnPersonalDevice(device) },
                                onRemoveClick = { devicePendingRemoval = device }
                            )
                        }
                    }
                }
            }

            DevicesSubTab.P2P_RADAR -> {
                item {
                    P2PRadarControlCard(
                        isP2PEnabled = isP2PEnabled,
                        p2pMeshState = p2pMeshState,
                        nearbyPeerCount = nearbyPeers.size,
                        onToggleP2P = requestP2PMeshWithPermissions,
                        onRescanP2P = { groupViewModel.rescanP2PMesh() }
                    )
                }

                if (isP2PEnabled) {
                    item {
                        Text(
                            text = "NEARBY P2P PEERS VIA BLUETOOTH / WI-FI (${nearbyPeers.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.05.sp,
                            color = colorScheme.tertiary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (nearbyPeers.isEmpty()) {
                        item {
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val hintText = if (p2pMeshState.discoveredCount > 0) {
                                    "Detected ${p2pMeshState.discoveredNames.joinToString(", ")}. Handshaking Nearby P2P channel to exchange GPS coordinates..."
                                } else {
                                    "Scanning for nearby devices with GeoTrace P2P Finder ON (50-200m). Both phones need Bluetooth + Location enabled."
                                }
                                Text(
                                    text = hintText,
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(nearbyPeers, key = { it.endpointId }) { peer ->
                            P2PPeerCard(
                                peer = peer,
                                userLocation = currentLocation,
                                lastRingSentAtMs = p2pRingCooldownsMs[peer.endpointId] ?: 0L,
                                nowMs = nowMs,
                                speedUnit = speedUnit,
                                onLocatePeer = {
                                    onLocateOnMap(
                                        MapFocusTarget(
                                            id = peer.endpointId,
                                            label = peer.displayName,
                                            latitude = peer.latitude,
                                            longitude = peer.longitude,
                                            subtitle = "Nearby P2P • ${peer.batteryPercent}%"
                                        )
                                    )
                                },
                                onRingPeer = { groupViewModel.ringP2PPeer(peer.endpointId, peer.displayName) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateGroupDialog(
            groupName = newGroupName,
            onGroupNameChange = { newGroupName = it },
            onConfirm = {
                if (newGroupName.isNotBlank()) {
                    groupViewModel.createGroup(newGroupName)
                    showCreateDialog = false
                    newGroupName = ""
                }
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    if (showJoinDialog) {
        JoinGroupDialog(
            inviteCode = inviteCodeInput,
            onInviteCodeChange = { inviteCodeInput = it },
            onConfirm = {
                if (inviteCodeInput.isNotBlank()) {
                    groupViewModel.joinGroupByCode(inviteCodeInput)
                    showJoinDialog = false
                    inviteCodeInput = ""
                }
            },
            onDismiss = { showJoinDialog = false }
        )
    }

    if (showCallsignDialog) {
        RenameDeviceDialog(
            callsign = callsignInput,
            onCallsignChange = { callsignInput = it },
            onConfirm = {
                if (callsignInput.isNotBlank()) {
                    groupViewModel.updateOfflineCallsign(callsignInput)
                    showCallsignDialog = false
                }
            },
            onDismiss = { showCallsignDialog = false }
        )
    }

    devicePendingRemoval?.let { dev ->
        RemoveDeviceDialog(
            device = dev,
            onConfirm = {
                groupViewModel.removePersonalDevice(dev)
                devicePendingRemoval = null
            },
            onDismiss = { devicePendingRemoval = null }
        )
    }

    groupPendingDelete?.let { grp ->
        DeleteGroupDialog(
            group = grp,
            onConfirm = {
                groupViewModel.deleteGroup(grp)
                groupPendingDelete = null
            },
            onDismiss = { groupPendingDelete = null }
        )
    }

    groupPendingLeave?.let { grp ->
        LeaveGroupConfirmDialog(
            group = grp,
            currentUserUid = currentUser?.uid,
            onConfirm = {
                groupViewModel.leaveGroup(grp)
                groupPendingLeave = null
            },
            onDismiss = { groupPendingLeave = null }
        )
    }

    if (memberPendingRemoval != null && activeGroup != null) {
        val target = memberPendingRemoval!!
        RemoveGroupMemberDialog(
            target = target,
            groupName = activeGroup!!.name,
            isOwnStale = target.userId == currentUser?.uid,
            onConfirm = {
                groupViewModel.removeGroupMemberDevice(target)
                memberPendingRemoval = null
            },
            onDismiss = { memberPendingRemoval = null }
        )
    }

    if (showSignOutConfirmDialog) {
        SignOutConfirmDialog(
            isBackgroundProtectionEnabled = isBackgroundProtectionEnabled,
            onConfirm = {
                showSignOutConfirmDialog = false
                performSignOut(context, scope)
            },
            onDismiss = { showSignOutConfirmDialog = false }
        )
    }

    if (showBgSettingsDialog) {
        BackgroundLocationSettingsDialog(
            onOpenSettings = {
                showBgSettingsDialog = false
                openAppLocationSettings(context)
            },
            onDismiss = { showBgSettingsDialog = false }
        )
    }
}
