package com.geotrace.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.geotrace.R
import com.geotrace.model.Group
import com.geotrace.model.LocationPoint
import com.geotrace.model.MemberLocation
import com.geotrace.model.P2PPeer
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.GroupViewModel
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun GroupScreen(
    groupViewModel: GroupViewModel,
    currentLocation: LocationPoint?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val successColor = colorScheme.tertiary
    val scope = rememberCoroutineScope()

    val currentUser by groupViewModel.currentUser.collectAsState()
    val userGroups by groupViewModel.userGroups.collectAsState()
    val activeGroup by groupViewModel.activeGroup.collectAsState()
    val groupMembers by groupViewModel.groupMembers.collectAsState()
    val nearbyPeers by groupViewModel.nearbyPeers.collectAsState()
    val p2pMeshState by groupViewModel.p2pMeshState.collectAsState()
    val offlineCallsign by groupViewModel.offlineCallsign.collectAsState()
    val isGhostMode by groupViewModel.isGhostMode.collectAsState()
    val isP2PEnabled by groupViewModel.isP2PEnabled.collectAsState()
    val isActionLoading by groupViewModel.isActionLoading.collectAsState()
    val statusMessage by groupViewModel.statusMessage.collectAsState()
    val errorMessage by groupViewModel.errorMessage.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var showCallsignDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var inviteCodeInput by remember { mutableStateOf("") }
    var callsignInput by remember(offlineCallsign) { mutableStateOf(offlineCallsign) }
    var isAuthSigningIn by remember { mutableStateOf(false) }

    // Nearby Connections + Location runtime permissions launcher (Android 8 - 15)
    val nearbyPermissionsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        if (allGranted) {
            groupViewModel.toggleP2PMesh()
        } else {
            Toast.makeText(
                context,
                "Location, Bluetooth & Nearby permissions are required for off-grid P2P discovery.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val requestP2PMeshWithPermissions = {
        if (!isP2PEnabled) {
            val permissionsNeeded = mutableListOf<String>()
            // Required on Android 10-12 for Nearby Connections discovery and across all versions for GPS coordinate exchange
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(android.Manifest.permission.BLUETOOTH_SCAN)
                }
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_ADVERTISE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
                }
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(android.Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.NEARBY_WIFI_DEVICES) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    permissionsNeeded.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
                }
            }

            if (permissionsNeeded.isNotEmpty()) {
                nearbyPermissionsLauncher.launch(permissionsNeeded.toTypedArray())
            } else {
                groupViewModel.toggleP2PMesh()
            }
        } else {
            groupViewModel.toggleP2PMesh()
        }
    }

    // Toast feedback
    statusMessage?.let { msg ->
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        groupViewModel.clearStatusMessage()
    }
    errorMessage?.let { err ->
        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
        groupViewModel.clearErrorMessage()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Screen Header
        item {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text(
                    text = "Family Circles & Off-Grid Finder",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onBackground
                )
                Text(
                    text = "Real-time cloud sync when online & zero-internet P2P device radar when off-grid",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        // Top-Level Offline P2P Device Finder Card (Works WITHOUT Sign-In or Internet)
        item {
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
                            onCheckedChange = { requestP2PMeshWithPermissions() },
                            colors = SwitchDefaults.colors(checkedThumbColor = successColor),
                            modifier = Modifier.testTag("p2p_mesh_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Offline Callsign / Device Identity Row
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "DEVICE CALLSIGN (BROADCASTED OFFLINE)",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.04.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: offlineCallsign,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                            }
                            if (currentUser == null) {
                                TextButton(
                                    onClick = {
                                        callsignInput = offlineCallsign
                                        showCallsignDialog = true
                                    },
                                    modifier = Modifier.testTag("edit_callsign_btn")
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit Callsign",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rename", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Diagnostics Pills when P2P active
                    if (isP2PEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.primary.copy(alpha = 0.1f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Links: ${p2pMeshState.connectedCount}/${p2pMeshState.discoveredCount} • Peers: ${nearbyPeers.size}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
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
                                        text = p2pMeshState.errorText!!,
                                        fontSize = 11.sp,
                                        color = colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        // Allow Ghost Mode privacy control offline even if no cloud group exists
                        if (activeGroup == null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isGhostMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = if (isGhostMode) Color(0xFFD97706) else colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isGhostMode) "Ghost Mode ON (Receive Only)" else "Share My GPS with P2P Peers",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = colorScheme.onSurface
                                    )
                                }
                                Switch(
                                    checked = isGhostMode,
                                    onCheckedChange = { groupViewModel.toggleGhostMode() },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD97706)),
                                    modifier = Modifier.testTag("offline_ghost_mode_switch")
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top-Level Nearby P2P Peers Section (Always visible when P2P active — NO sign-in required!)
        if (isP2PEnabled) {
            item {
                Text(
                    text = "NEARBY P2P PEERS VIA BLUETOOTH / WI-FI (${nearbyPeers.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.05.sp,
                    color = successColor,
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
                items(nearbyPeers) { peer ->
                    P2PPeerCard(peer = peer, userLocation = currentLocation)
                }
            }
        }

        // Authentication Card / Profile Section
        item {
            if (currentUser == null) {
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
                                .background(colorScheme.primaryContainer),
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
                            text = "Cloud Multi-User Sync Required",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Sign in securely with Google to create family circles, share your live coordinates, and see your team on the map.",
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                isAuthSigningIn = true
                                performGoogleSignIn(
                                    context = context,
                                    onSuccess = { isAuthSigningIn = false },
                                    onError = { err ->
                                        isAuthSigningIn = false
                                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            enabled = !isAuthSigningIn,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("google_sign_in_btn")
                        ) {
                            if (isAuthSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sign in with Google", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                // User Profile Header Pill
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                val initial = (currentUser?.displayName ?: currentUser?.email ?: "U")
                                    .firstOrNull()?.uppercase() ?: "U"
                                Text(
                                    text = initial,
                                    color = colorScheme.onPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = currentUser?.displayName ?: "Field Operator",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = currentUser?.email ?: currentUser?.uid?.take(12) ?: "",
                                    fontSize = 11.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                performSignOut(context)
                            },
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
        }

        if (currentUser != null) {
            // Active Group Card
            item {
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (activeGroup != null) successColor else customColors.warning)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (activeGroup != null) "ACTIVE TRACKING CIRCLE" else "STANDBY (NO CIRCLE SELECTED)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.05.sp,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }

                            if (activeGroup != null) {
                                TextButton(
                                    onClick = { groupViewModel.leaveActiveGroup() },
                                    modifier = Modifier.testTag("leave_group_btn")
                                ) {
                                    Text("Leave", color = colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (activeGroup != null) {
                            Text(
                                text = activeGroup!!.name,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Invite Code Pill
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = customColors.surfaceContainerLow,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "INVITATION CODE",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.04.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = activeGroup!!.inviteCode,
                                            fontSize = 16.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.primary
                                        )
                                    }

                                    Row {
                                        IconButton(
                                            onClick = {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("GeoTrace Invite Code", activeGroup!!.inviteCode))
                                                Toast.makeText(context, "Code copied: ${activeGroup!!.inviteCode}", Toast.LENGTH_SHORT).show()
                                            },
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
                                            onClick = {
                                                val sendIntent = android.content.Intent().apply {
                                                    action = android.content.Intent.ACTION_SEND
                                                    putExtra(
                                                        android.content.Intent.EXTRA_TEXT,
                                                        "Join my GeoTrace Tracking Circle '${activeGroup!!.name}' with Invite Code: ${activeGroup!!.inviteCode}"
                                                    )
                                                    type = "text/plain"
                                                }
                                                context.startActivity(android.content.Intent.createChooser(sendIntent, "Share Invite Code"))
                                            },
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

                            Spacer(modifier = Modifier.height(12.dp))

                            // Ghost Mode Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (isGhostMode) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = if (isGhostMode) Color(0xFFD97706) else colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Ghost Mode (Freeze Location)",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (isGhostMode) "Location paused; members see last known point" else "Actively streaming live coordinates",
                                            fontSize = 11.sp,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Switch(
                                    checked = isGhostMode,
                                    onCheckedChange = { groupViewModel.toggleGhostMode() },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFD97706)),
                                    modifier = Modifier.testTag("ghost_mode_switch")
                                )
                            }
                        } else {
                            Text(
                                text = "Join or create a circle to start tracking your family or field team.",
                                fontSize = 13.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Action Buttons: Create New Circle & Join with Code
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
                            .testTag("create_circle_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Circle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { showJoinDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("join_circle_btn")
                    ) {
                        Icon(Icons.Default.GroupAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Join Code", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Live Members in Active Circle
            if (activeGroup != null) {
                item {
                    Text(
                        text = "MEMBERS IN THIS CIRCLE (${groupMembers.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.05.sp,
                        color = colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (groupMembers.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Awaiting member location broadcasts. Share the invite code '${activeGroup!!.inviteCode}' with other devices.",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(groupMembers) { member ->
                        MemberCard(
                            member = member,
                            userLocation = currentLocation,
                            isSelf = member.userId == currentUser?.uid
                        )
                    }
                }
            }

            // My Saved Circles List
            item {
                Text(
                    text = "MY CIRCLES (${userGroups.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.05.sp,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            items(userGroups) { grp ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (grp.id == activeGroup?.id) colorScheme.primaryContainer.copy(alpha = 0.25f) else colorScheme.surface
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (grp.id == activeGroup?.id) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { groupViewModel.selectGroup(grp) }
                        .testTag("group_card_${grp.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = grp.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Code: ${grp.inviteCode} • ${grp.memberIds.size} Members",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }

                        if (grp.id == activeGroup?.id) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "SELECTED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { groupViewModel.selectGroup(grp) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("Select", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Group Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create Family / Team Circle", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Give your circle a recognizable name. A unique 6-character invitation code will be generated.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
                        label = { Text("Circle Name (e.g. Vicram Family)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("circle_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupName.isNotBlank()) {
                            groupViewModel.createGroup(newGroupName)
                            showCreateDialog = false
                            newGroupName = ""
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_circle_btn")
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Join Group Dialog
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join Circle with Code", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Enter the 6-character code provided by the circle creator (e.g. FAM-7K2).",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = inviteCodeInput,
                        onValueChange = { inviteCodeInput = it.uppercase() },
                        label = { Text("Invitation Code") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("invite_code_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inviteCodeInput.isNotBlank()) {
                            groupViewModel.joinGroupByCode(inviteCodeInput)
                            showJoinDialog = false
                            inviteCodeInput = ""
                        }
                    },
                    modifier = Modifier.testTag("confirm_join_circle_btn")
                ) {
                    Text("Join")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Edit Offline Callsign Dialog
    if (showCallsignDialog) {
        AlertDialog(
            onDismissRequest = { showCallsignDialog = false },
            title = { Text("Offline P2P Callsign", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Set a recognizable device name broadcasted over Bluetooth/Wi-Fi when searching off-grid.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = callsignInput,
                        onValueChange = { callsignInput = it.take(24) },
                        label = { Text("Device Callsign") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("callsign_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (callsignInput.isNotBlank()) {
                            groupViewModel.updateOfflineCallsign(callsignInput)
                            showCallsignDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_callsign_btn")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCallsignDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MemberCard(
    member: MemberLocation,
    userLocation: LocationPoint?,
    isSelf: Boolean
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val distanceMeters = if (userLocation != null) {
        member.distanceToMeters(userLocation.latitude, userLocation.longitude)
    } else null

    val distanceText = when {
        isSelf -> "You (Current Device)"
        distanceMeters == null -> "--"
        distanceMeters < 1000 -> "${distanceMeters.toInt()} m away"
        else -> String.format(Locale.US, "%.1f km away", distanceMeters / 1000.0)
    }

    val parsedAvatarColor = try {
        Color(android.graphics.Color.parseColor(member.avatarColor))
    } catch (e: Exception) {
        colorScheme.primary
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth().testTag("member_card_${member.userId}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Initial Avatar Circle
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface
                    )
                    if (isSelf) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(YOU)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.primary
                        )
                    }
                }

                Text(
                    text = "$distanceText • ${member.gait} (${String.format(Locale.US, "%.1f", member.speed * 3.6f)} km/h)",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    val successColor = colorScheme.tertiary
                    Icon(
                        Icons.Default.BatteryChargingFull,
                        contentDescription = "Battery",
                        tint = if (member.batteryPercent > 20) successColor else Color(0xFFDC2626),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${member.batteryPercent}% • ${member.trackingMode}",
                        fontSize = 10.sp,
                        color = colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun P2PPeerCard(
    peer: P2PPeer,
    userLocation: LocationPoint?
) {
    val colorScheme = MaterialTheme.colorScheme
    val successColor = colorScheme.tertiary

    val distanceText = if (userLocation != null) {
        val distanceMeters = peer.distanceToMeters(userLocation.latitude, userLocation.longitude)
        val bearingDeg = peer.bearingFrom(userLocation.latitude, userLocation.longitude)
        val compass = toCompassCardinal(bearingDeg)
        val distPart = if (distanceMeters < 1000) "${distanceMeters.toInt()}m" else String.format(Locale.US, "%.1f km", distanceMeters / 1000f)
        "$distPart $compass"
    } else {
        "GPS pending"
    }

    val ageSec = ((System.currentTimeMillis() - peer.lastSeenTimestamp).coerceAtLeast(0L) / 1000L)
    val ageText = when {
        ageSec < 5 -> "Live"
        ageSec < 60 -> "${ageSec}s ago"
        else -> "${ageSec / 60}m ago"
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, successColor.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().testTag("p2p_peer_card_${peer.endpointId}")
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(successColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.NearMe,
                    contentDescription = "P2P Peer",
                    tint = successColor,
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
                    text = "$distanceText • ${String.format(Locale.US, "%.1f", peer.speed * 3.6f)} km/h",
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Battery: ${peer.batteryPercent}% • Direct BLE/Wi-Fi • $ageText",
                    fontSize = 10.sp,
                    color = successColor,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun toCompassCardinal(bearingDegrees: Float): String {
    val normalized = ((bearingDegrees % 360f) + 360f) % 360f
    val dirs = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val idx = ((normalized + 22.5f) / 45f).toInt() % 8
    return dirs[idx]
}

// Credential Manager Google Sign-In Implementation
private fun performGoogleSignIn(
    context: Context,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val activity = context as? Activity ?: return
    val credentialManager = CredentialManager.create(context)
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (e: Exception) {
        onError("Google Client ID not found. Ensure google-services.json is present.")
        return
    }

    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        try {
            val result = credentialManager.getCredential(activity, request)
            val credential = result.credential
            if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                Firebase.auth.signInWithCredential(authCredential).await()
                onSuccess()
            } else {
                onError("Unexpected credential response type.")
            }
        } catch (e: GetCredentialCancellationException) {
            onError("Sign-in was cancelled.")
        } catch (e: Exception) {
            Log.e("GroupScreen", "Google Sign-In failed", e)
            onError(e.localizedMessage ?: "Google sign-in failed.")
        }
    }
}

private fun performSignOut(context: Context) {
    val credentialManager = CredentialManager.create(context)
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w("GroupScreen", "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
            }
        }
    }
}
