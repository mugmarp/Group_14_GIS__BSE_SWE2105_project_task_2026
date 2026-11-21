package com.geotrace.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.data.export.ExportFormat
import com.geotrace.data.export.TrackExporter
import com.geotrace.model.CoordinateFormat
import com.geotrace.model.SpeedUnit
import com.geotrace.model.TrackingSession
import com.geotrace.model.TrailWaypoint
import com.geotrace.ui.components.GeoTraceTopBar
import com.geotrace.ui.components.ScreenListContentPadding
import com.geotrace.ui.components.adaptiveContentWidth
import com.geotrace.ui.theme.JetBrainsMonoFontFamily
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.ui.theme.TelemetryHeaderLabelStyle
import com.geotrace.ui.theme.TelemetryScalarStyle
import com.geotrace.viewmodel.TrackerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: TrackerViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onViewTrailOnMap: ((TrackingSession) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onNavigateBack != null) {
        BackHandler { onNavigateBack() }
    }

    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val sessions by viewModel.allSessions.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val exportingSessionId by viewModel.exportingSessionId.collectAsState()

    val hasLiveActiveSession = remember(sessions) { sessions.any { it.isRecording && !it.isPaused } }
    var liveNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(hasLiveActiveSession) {
        while (hasLiveActiveSession) {
            liveNowMs = System.currentTimeMillis()
            delay(1000L)
        }
    }

    var pendingExport by remember { mutableStateOf<TrackerViewModel.ExportPayload?>(null) }
    var sessionToDelete by remember { mutableStateOf<TrackingSession?>(null) }
    var sessionToRename by remember { mutableStateOf<TrackingSession?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var waypointDialogSession by remember { mutableStateOf<TrackingSession?>(null) }
    var sessionWaypoints by remember { mutableStateOf<List<TrailWaypoint>>(emptyList()) }
    var isLoadingWaypoints by remember { mutableStateOf(false) }

    // Native Storage Access Framework (SAF) saver for direct Downloads / device storage saving with true file extension
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val payload = pendingExport
        if (uri != null && payload != null) {
            coroutineScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openOutputStream(uri)
                            ?: throw IOException("Storage provider unavailable")
                        stream.use { out ->
                            out.write(payload.content.toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                    }
                    Toast.makeText(
                        context,
                        "Saved ${payload.suggestedFileName}",
                        Toast.LENGTH_SHORT
                    ).show()
                    pendingExport = null
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Save failed: ${e.localizedMessage ?: "Write error"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun triggerExport(session: TrackingSession, format: ExportFormat) {
        viewModel.exportSession(session, format) { payload ->
            if (payload == null) {
                Toast.makeText(
                    context,
                    "No trackpoints or waypoints recorded in this trail yet.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                pendingExport = payload
            }
        }
    }

    fun openWaypointsDialog(session: TrackingSession) {
        waypointDialogSession = session
        sessionWaypoints = emptyList()
        isLoadingWaypoints = true
        viewModel.loadWaypointsForSession(session.id) { loaded ->
            sessionWaypoints = loaded
            isLoadingWaypoints = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .adaptiveContentWidth()
                .fillMaxSize(),
            contentPadding = ScreenListContentPadding,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                GeoTraceTopBar(
                    title = "Recorded Trails & Telemetry",
                    subtitle = "SQLite tracks with GPX, KML, CSV & GeoJSON exports",
                    onBack = onNavigateBack,
                    backContentDescription = "Back to Live Map"
                )
            }

            if (sessions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Route,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "NO RECORDED SESSIONS YET",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.04.sp,
                                color = colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Start recording on the Live Map screen to capture real outdoor GPS trajectories.",
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            if (onNavigateBack != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onNavigateBack,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.primary,
                                        contentColor = colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open Live Map to Record", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            } else {
                items(sessions, key = { it.id }) { session ->
                    SessionHistoryCard(
                        session = session,
                        liveNowMs = liveNowMs,
                        speedUnit = settings.speedUnit,
                        isExporting = exportingSessionId == session.id,
                        exportBusy = exportingSessionId != null,
                        onViewOnMap = if (onViewTrailOnMap != null) {
                            { onViewTrailOnMap(session) }
                        } else null,
                        onViewWaypoints = if (session.waypointCount > 0) {
                            { openWaypointsDialog(session) }
                        } else null,
                        onExport = { format -> triggerExport(session, format) },
                        onRename = {
                            renameInput = session.name
                            sessionToRename = session
                        },
                        onDeleteRequest = { sessionToDelete = session }
                    )
                }
            }
        }
    }

    // Trail Waypoints Inspection & Management Dialog
    if (waypointDialogSession != null) {
        val targetSession = waypointDialogSession!!
        TrailWaypointsDialog(
            sessionName = targetSession.name,
            waypoints = sessionWaypoints,
            isLoading = isLoadingWaypoints,
            coordinateFormat = settings.coordinateFormat,
            onCopyCoordinates = { wp ->
                val formatted = formatCoordinates(wp.latitude, wp.longitude, settings.coordinateFormat)
                clipboard.setText(AnnotatedString(formatted))
                Toast.makeText(context, "Copied ${wp.name} coordinates", Toast.LENGTH_SHORT).show()
            },
            onDeleteWaypoint = { wp ->
                viewModel.deleteWaypoint(wp.id, targetSession.id) { remaining ->
                    sessionWaypoints = remaining
                    Toast.makeText(context, "Removed ${wp.name}", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { waypointDialogSession = null }
        )
    }

    // Rename Trail Dialog
    if (sessionToRename != null) {
        val target = sessionToRename!!
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = {
                Text("Rename Trail", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Trail Name", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_trail_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameSession(target.id, renameInput)
                        sessionToRename = null
                    },
                    enabled = renameInput.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Save Name", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { sessionToRename = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = colorScheme.surface
        )
    }

    // Delete Confirmation Dialog
    if (sessionToDelete != null) {
        val target = sessionToDelete!!
        val deleteMessage = if (target.isRecording) {
            "\"${target.name}\" is currently recording. Deleting it will stop live tracking and permanently erase ${target.formattedDistance} (${target.pointCount} trackpoints, ${target.waypointCount} waypoints). This cannot be undone."
        } else {
            "Permanently delete \"${target.name}\" (${target.formattedDistance}, ${target.pointCount} trackpoints, ${target.waypointCount} waypoints)? This cannot be undone."
        }
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Text(
                    text = if (target.isRecording) "Stop & Delete Active Trail?" else "Delete Recorded Trail?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = deleteMessage,
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSession(target.id)
                        sessionToDelete = null
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError
                    ),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = if (target.isRecording) "Stop & Delete" else "Delete",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { sessionToDelete = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = colorScheme.surface
        )
    }

    // Export Preview, Save to Device & Share Dialog
    if (pendingExport != null) {
        val payload = pendingExport!!
        val previewScroll = rememberScrollState()
        val previewSnippet = remember(payload) {
            payload.content.take(4000) + if (payload.content.length > 4000) "\n... [preview truncated]" else ""
        }
        val sizeLabel = remember(payload.byteSize) { formatExportSize(payload.byteSize) }

        AlertDialog(
            onDismissRequest = { pendingExport = null },
            title = {
                Column {
                    Text(
                        text = "Export ${payload.format.shortCode}",
                        color = colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = payload.suggestedFileName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontFeatureSettings = "tnum"
                        ),
                        color = colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "${payload.trackpointCount} trackpoints • ${payload.waypointCount} waypoints • ${payload.lineCount} lines • $sizeLabel",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = previewSnippet,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 10.sp,
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            color = colorScheme.primary,
                            modifier = Modifier
                                .verticalScroll(previewScroll)
                                .padding(10.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                saveDocumentLauncher.launch(payload.suggestedFileName)
                            },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("save_export_device_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Save File", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val shared = shareExportFile(context, payload)
                                    if (shared) {
                                        pendingExport = null
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("share_export_btn")
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Share", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { pendingExport = null },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Close", color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = colorScheme.surface
        )
    }
}

@Composable
private fun TrailWaypointsDialog(
    sessionName: String,
    waypoints: List<TrailWaypoint>,
    isLoading: Boolean,
    coordinateFormat: CoordinateFormat,
    onCopyCoordinates: (TrailWaypoint) -> Unit,
    onDeleteWaypoint: (TrailWaypoint) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val timeFormatter = remember { SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Field Waypoints (${waypoints.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
                Text(
                    text = sessionName,
                    fontSize = 11.sp,
                    color = colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
                waypoints.isEmpty() -> {
                    Text(
                        text = "No saved waypoints remain in this trail.",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        waypoints.forEachIndexed { idx, wp ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = customColors.surfaceContainerLow,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "W${idx + 1} • ${wp.name}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (wp.note.isNotBlank()) {
                                            Text(
                                                text = wp.note,
                                                fontSize = 11.sp,
                                                color = colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = formatCoordinates(wp.latitude, wp.longitude, coordinateFormat),
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontSize = 10.sp,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontFeatureSettings = "tnum"
                                            ),
                                            color = colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val timeText = remember(wp.timestamp) { timeFormatter.format(Date(wp.timestamp)) }
                                        Text(
                                            text = "$timeText • Acc ±${wp.accuracy.toInt()}m • Alt ${wp.altitude.toInt()}m",
                                            fontSize = 10.sp,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onCopyCoordinates(wp) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy ${wp.name} coordinates",
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onDeleteWaypoint(wp) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete ${wp.name}",
                                            tint = colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(48.dp)
            ) {
                Text("Close", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = colorScheme.surface
    )
}

@Composable
private fun SessionHistoryCard(
    session: TrackingSession,
    liveNowMs: Long,
    speedUnit: SpeedUnit,
    isExporting: Boolean,
    exportBusy: Boolean,
    onViewOnMap: (() -> Unit)?,
    onViewWaypoints: (() -> Unit)?,
    onExport: (ExportFormat) -> Unit,
    onRename: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val dateStr = remember(session.startTime) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(session.startTime))
    }
    val durationLabel = if (session.isRecording) session.formatDuration(liveNowMs) else session.formattedDuration

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .testTag("session_card_${session.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column {
            // Top Accent Line in Cobalt Secondary (or Error if active)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(if (session.isRecording) colorScheme.error else colorScheme.secondary)
            )

            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = session.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (session.isRecording) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = colorScheme.error.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (session.isPaused) "PAUSED" else "ACTIVE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = dateStr,
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = session.trackingMode.title.substringBefore(" ("),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onRename,
                            modifier = Modifier.size(48.dp).testTag("rename_session_btn")
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Rename Trail",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDeleteRequest,
                            modifier = Modifier.size(48.dp).testTag("delete_session_btn")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Session",
                                tint = colorScheme.error,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tabular Metrics Row (Trackpoints & Waypoints separated cleanly)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(customColors.surfaceContainerLow, RoundedCornerShape(14.dp))
                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        MetricItem("DISTANCE", session.formattedDistance, colorScheme.primary)
                    }
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                    Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                        MetricItem("DURATION", durationLabel, colorScheme.secondary)
                    }
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                    Box(modifier = Modifier.weight(1.1f).padding(start = 6.dp)) {
                        val avgConverted = (session.avgSpeedKmh / 3.6) * speedUnit.multiplier
                        val maxConverted = (session.maxSpeedKmh / 3.6) * speedUnit.multiplier
                        val speedSub = buildString {
                            if (session.maxSpeedKmh > 0.1) {
                                append("Max ${String.format(Locale.US, "%.1f", maxConverted)}")
                            }
                            session.avgPaceMinPerKm.takeIf { it != "--:--" }?.let { pace ->
                                if (isNotEmpty()) append(" • ")
                                append(pace)
                            }
                        }.ifBlank { null }
                        MetricItem(
                            label = "AVG SPEED",
                            value = "${String.format(Locale.US, "%.1f", avgConverted)} ${speedUnit.label}",
                            color = colorScheme.onSurface,
                            subtitle = speedSub
                        )
                    }
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                    Box(modifier = Modifier.weight(0.95f).padding(start = 6.dp)) {
                        val wpSub = if (session.waypointCount > 0) "${session.waypointCount} wpts" else null
                        MetricItem("TRACKPTS", "${session.pointCount}", colorScheme.tertiary, subtitle = wpSub)
                    }
                }

                if (onViewOnMap != null || onViewWaypoints != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (onViewOnMap != null) {
                            OutlinedButton(
                                onClick = onViewOnMap,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = colorScheme.primary.copy(alpha = 0.06f),
                                    contentColor = colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1.25f)
                                    .height(48.dp)
                                    .testTag("view_trail_map_btn")
                            ) {
                                Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (onViewWaypoints != null) "Inspect on Map" else "Inspect Trail on Map",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (onViewWaypoints != null) {
                            OutlinedButton(
                                onClick = onViewWaypoints,
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, colorScheme.tertiary.copy(alpha = 0.45f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = colorScheme.tertiary.copy(alpha = 0.06f),
                                    contentColor = colorScheme.tertiary
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("view_waypoints_btn")
                            ) {
                                Icon(Icons.Default.Flag, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Waypoints (${session.waypointCount})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isExporting) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Building GIS export...",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.primary
                        )
                    }
                } else {
                    // Export Buttons Row (48dp accessible height, real GIS extensions)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ExportFormatButton(
                            label = "GPX",
                            color = colorScheme.primary,
                            enabled = !exportBusy,
                            modifier = Modifier.weight(1f).testTag("export_gpx_btn"),
                            onClick = { onExport(ExportFormat.GPX) }
                        )
                        ExportFormatButton(
                            label = "KML",
                            color = colorScheme.secondary,
                            enabled = !exportBusy,
                            modifier = Modifier.weight(1f).testTag("export_kml_btn"),
                            onClick = { onExport(ExportFormat.KML) }
                        )
                        ExportFormatButton(
                            label = "CSV",
                            color = colorScheme.onSurfaceVariant,
                            enabled = !exportBusy,
                            modifier = Modifier.weight(1f).testTag("export_csv_btn"),
                            onClick = { onExport(ExportFormat.CSV) }
                        )
                        ExportFormatButton(
                            label = "GeoJSON",
                            color = colorScheme.tertiary,
                            enabled = !exportBusy,
                            modifier = Modifier.weight(1.15f).testTag("export_geojson_btn"),
                            onClick = { onExport(ExportFormat.GEOJSON) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportFormatButton(
    label: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        modifier = modifier.height(48.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color, subtitle: String? = null) {
    Column {
        Text(
            text = label,
            style = TelemetryHeaderLabelStyle.copy(fontSize = 8.5.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Text(
            text = value,
            style = TelemetryScalarStyle.copy(fontSize = 12.sp),
            color = color,
            maxLines = 1
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                fontSize = 8.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatExportSize(bytes: Int): String {
    return when {
        bytes >= 1024 * 1024 -> String.format(Locale.US, "%.2f MB", bytes / (1024f * 1024f))
        bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
        else -> "$bytes B"
    }
}

private suspend fun shareExportFile(context: Context, payload: TrackerViewModel.ExportPayload): Boolean {
    return try {
        val uri = withContext(Dispatchers.IO) {
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            TrackExporter.cleanupOldExportCache(exportDir)

            val exportFile = File(exportDir, payload.suggestedFileName).apply {
                writeText(payload.content, Charsets.UTF_8)
            }

            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exportFile
            )
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = payload.format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "GeoTrace ${payload.format.shortCode} - ${payload.suggestedFileName}")
            putExtra(Intent.EXTRA_TITLE, payload.suggestedFileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share ${payload.format.shortCode} (${payload.suggestedFileName})").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Failed to share ${payload.format.shortCode}: ${e.localizedMessage ?: "Unknown error"}",
            Toast.LENGTH_SHORT
        ).show()
        false
    }
}
