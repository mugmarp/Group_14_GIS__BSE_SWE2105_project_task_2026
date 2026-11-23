package com.geotrace.ui.screens.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.model.Group
import com.geotrace.model.MemberLocation
import com.geotrace.model.PersonalDevice

@Composable
fun CreateGroupDialog(
    groupName: String,
    onGroupNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var hasInteracted by rememberSaveable { mutableStateOf(false) }
    val isMissingName = hasInteracted && groupName.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Create Tracking Group", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Give your tracking group a recognizable name. A unique 6-character invitation code will be generated.",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = groupName,
                    onValueChange = {
                        hasInteracted = true
                        onGroupNameChange(it.take(50))
                    },
                    label = { Text("Group Name (e.g. Field Ops or Home Devices)") },
                    isError = isMissingName,
                    supportingText = {
                        Text(
                            text = if (isMissingName) "Group name is required." else "${groupName.length}/50 characters",
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (groupName.isNotBlank()) onConfirm() else hasInteracted = true
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("group_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = groupName.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier.testTag("confirm_create_group_btn")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = CircleShape) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun JoinGroupDialog(
    inviteCode: String,
    onInviteCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val trimmedLength = inviteCode.trim().length
    val isTooShort = inviteCode.isNotEmpty() && trimmedLength < 4
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Join Group with Code", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Enter the 6-character code provided by the group creator (e.g. GRP-7K2).",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = inviteCode,
                    onValueChange = { onInviteCodeChange(it.uppercase().take(12)) },
                    label = { Text("Invitation Code") },
                    isError = isTooShort,
                    supportingText = {
                        Text(
                            text = if (isTooShort) {
                                "Enter at least 4 characters ($trimmedLength/4+)"
                            } else {
                                "6-character invite code from group creator"
                            },
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (trimmedLength >= 4) onConfirm()
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("invite_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = trimmedLength >= 4,
                shape = CircleShape,
                modifier = Modifier.testTag("confirm_join_group_btn")
            ) {
                Text("Join")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = CircleShape) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenameDeviceDialog(
    callsign: String,
    onCallsignChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var hasInteracted by rememberSaveable { mutableStateOf(false) }
    val isMissingName = hasInteracted && callsign.isBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Rename This Device", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Set a recognizable device label shown in your My Fleet list, shared groups, and off-grid P2P device finder.",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = callsign,
                    onValueChange = {
                        hasInteracted = true
                        onCallsignChange(it.take(24))
                    },
                    label = { Text("Device Name (e.g. Spare Pixel or Study Tablet)") },
                    isError = isMissingName,
                    supportingText = {
                        Text(
                            text = if (isMissingName) "Device name cannot be empty." else "${callsign.length}/24 characters",
                            fontSize = 11.sp
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (callsign.isNotBlank()) onConfirm() else hasInteracted = true
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("callsign_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = callsign.isNotBlank(),
                shape = CircleShape,
                modifier = Modifier.testTag("confirm_callsign_btn")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = CircleShape) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RemoveDeviceDialog(
    device: PersonalDevice,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Remove Linked Device?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Remove '${device.deviceName.ifBlank { device.deviceModel }}' from My Fleet? If GeoTrace is still signed in on that phone, it will re-register automatically when opened again.",
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                modifier = Modifier.testTag("confirm_remove_device_btn")
            ) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = CircleShape) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RemoveGroupMemberDialog(
    target: MemberLocation,
    groupName: String,
    isOwnStale: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                if (isOwnStale) "Remove Stale Device?" else "Remove From Group?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                if (isOwnStale) {
                    "Remove old device entry '${target.displayName}' from '$groupName'?"
                } else {
                    "Remove '${target.displayName}' (${target.deviceModel.ifBlank { "Device" }}) from '$groupName'?"
                },
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                modifier = Modifier.testTag("confirm_remove_group_member_btn")
            ) {
                Text("Remove")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = CircleShape) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteGroupDialog(
    group: Group,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Delete '${group.name}'?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "This permanently deletes the tracking group '${group.name}' and its invite code '${group.inviteCode}' for all members.",
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                modifier = Modifier.testTag("confirm_delete_group_btn")
            ) {
                Text("Delete Group")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = CircleShape) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun LeaveGroupConfirmDialog(
    group: Group,
    currentUserUid: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isSoleMember = group.memberIds.size <= 1
    val isCreatorLeavingOthers = !isSoleMember && currentUserUid != null && group.createdBy == currentUserUid
    val body = when {
        isSoleMember -> "You are the last member in '${group.name}'. Leaving will permanently delete this group and its invite code."
        isCreatorLeavingOthers -> "You created '${group.name}'. Leaving will remove this device's coordinates and automatically transfer group ownership to another member."
        else -> "Leave '${group.name}' and remove this device's live location from its map? You can rejoin later with code '${group.inviteCode}'."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(
                if (isSoleMember) "Leave & Delete Group?" else "Leave '${group.name}'?",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(body, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                modifier = Modifier.testTag("confirm_leave_group_btn")
            ) {
                Text(if (isSoleMember) "Leave & Delete" else "Leave Group")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = CircleShape) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SignOutConfirmDialog(
    isBackgroundProtectionEnabled: Boolean,
    onConfirm: (unlinkCurrentDevice: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var unlinkDevice by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Sign Out of GeoTrace?", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    if (isBackgroundProtectionEnabled) {
                        "Signing out pauses cloud group sync, My Fleet tracking, and 24/7 Remote Ring until you sign back in. Off-Grid P2P Radar continues working offline."
                    } else {
                        "Sign out of your Google account? Off-Grid P2P Radar remains available without an account."
                    },
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { unlinkDevice = !unlinkDevice }
                        .testTag("sign_out_unlink_device_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = unlinkDevice,
                        onCheckedChange = { unlinkDevice = it }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Also unlink this device from My Fleet & Group pins",
                        fontSize = 12.sp,
                        color = colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(unlinkDevice) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error),
                modifier = Modifier.testTag("confirm_sign_out_btn")
            ) {
                Text("Sign Out")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = CircleShape) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun BackgroundLocationSettingsDialog(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Enable 'Allow All the Time'", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Android requires background location permission to be enabled in system settings so 24/7 Protection and reboot auto-start stay active when GeoTrace is closed.\n\nOpen App Settings -> Permissions -> Location -> Allow all the time.",
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                shape = CircleShape,
                modifier = Modifier.testTag("open_bg_location_settings_btn")
            ) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = CircleShape) {
                Text("Later")
            }
        }
    )
}

@Composable
fun ProtectionSignInRequiredDialog(
    isSigningIn: Boolean,
    onSignInClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = { if (!isSigningIn) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        title = { Text("Sign In for 24/7 Protection", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "24/7 Background Protection syncs this device with your private fleet and listens for Remote Ring alerts.\n\nSign in with Google to activate it. Map viewing, trail recording, geofences, and Off-Grid P2P continue working without an account.",
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onSignInClick,
                enabled = !isSigningIn,
                shape = CircleShape,
                modifier = Modifier.testTag("protection_sign_in_btn")
            ) {
                Text(if (isSigningIn) "Signing in..." else "Sign in with Google")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSigningIn,
                shape = CircleShape
            ) {
                Text("Not Now")
            }
        }
    )
}
