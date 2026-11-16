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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.data.export.ExportFormat
import com.geotrace.data.export.TrackExporter
import com.geotrace.model.SpeedUnit
import com.geotrace.model.TrackingSession
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.TrackerViewModel
import java.io.File
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
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val sessions by viewModel.allSessions.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var pendingExport by remember { mutableStateOf<TrackerViewModel.ExportPayload?>(null) }
    var sessionToDelete by remember { mutableStateOf<TrackingSession?>(null) }
    var sessionToRename by remember { mutableStateOf<TrackingSession?>(null) }
    var renameInput by remember { mutableStateOf("") }

    // Native Storage Access Framework (SAF) saver for direct Downloads / device storage saving with true file extension
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val payload = pendingExport
        if (uri != null && payload != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(payload.content.toByteArray(Charsets.UTF_8))
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Live Map",
                                tint = colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Column {
                        Text(
                            text = "Recorded Trails & Telemetry",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground
                        )
                        Text(
                            text = "SQLite tracks with GPX, KML, CSV & GeoJSON exports",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (sessions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
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
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (onNavigateBack != null) {
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onNavigateBack,
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colorScheme.primary,
                                        contentColor = colorScheme.onPrimary
                                    )
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
                        speedUnit = settings.speedUnit,
                        onViewOnMap = if (onViewTrailOnMap != null) {
                            { onViewTrailOnMap(session) }
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
                    shape = RoundedCornerShape(8.dp),
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
                    enabled = renameInput.isNotBlank()
                ) {
                    Text("Save Name", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text("Cancel", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = colorScheme.surface
        )
    }

    // Delete Confirmation Dialog
    if (sessionToDelete != null) {
        val target = sessionToDelete!!
        AlertDialog(
            onDismissRequest = { sessionToDelete = null },
            title = {
                Text("Delete Recorded Trail?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Text(
                    text = "Permanently delete \"${target.name}\" (${target.formattedDistance}, ${target.pointCount} trackpoints)? This cannot be undone.",
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToDelete = null }) {
                    Text("Cancel", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = colorScheme.surface
        )
    }

    // Export Preview, Save to Device & Share Dialog
    if (pendingExport != null) {
        val payload = pendingExport!!
        val previewScroll = rememberScrollState()
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
                        color = colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = "${payload.trackpointCount} trackpoints • ${payload.waypointCount} waypoints • ${payload.content.lines().size} lines",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = payload.content.take(4000) + if (payload.content.length > 4000) "\n... [preview truncated]" else "",
                            color = colorScheme.primary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
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
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("save_export_device_btn")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("Save File", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1)
                        }

                        Button(
                            onClick = {
                                shareExportFile(context, payload)
                                pendingExport = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.primary,
                                contentColor = colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
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
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = colorScheme.surface
        )
    }
}

@Composable
private fun SessionHistoryCard(
    session: TrackingSession,
    speedUnit: SpeedUnit,
    onViewOnMap: (() -> Unit)?,
    onExport: (ExportFormat) -> Unit,
    onRename: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .testTag("session_card_${session.id}"),
        shape = RoundedCornerShape(16.dp),
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
                    Column(modifier = Modifier.weight(1f)) {
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
                                    shape = RoundedCornerShape(4.dp),
                                    color = colorScheme.error.copy(alpha = 0.14f)
                                ) {
                                    Text(
                                        text = if (session.isPaused) "PAUSED" else "ACTIVE",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(session.startTime))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = dateStr,
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = colorScheme.primary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = session.trackingMode.title.substringBefore(" ("),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onRename,
                            modifier = Modifier.size(44.dp).testTag("rename_session_btn")
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
                            modifier = Modifier.size(44.dp).testTag("delete_session_btn")
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
                        .background(customColors.surfaceContainerLow, RoundedCornerShape(10.dp))
                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        MetricItem("DISTANCE", session.formattedDistance, colorScheme.primary)
                    }
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                    Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                        MetricItem("DURATION", session.formattedDuration, colorScheme.secondary)
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

                if (onViewOnMap != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onViewOnMap,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = colorScheme.primary.copy(alpha = 0.06f),
                            contentColor = colorScheme.primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(42.dp)
                            .testTag("view_trail_map_btn")
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Inspect Trail on Map", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Export Buttons Row (44dp accessible height, real GIS extensions)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ExportFormatButton(
                        label = "GPX",
                        color = colorScheme.primary,
                        modifier = Modifier.weight(1f).testTag("export_gpx_btn"),
                        onClick = { onExport(ExportFormat.GPX) }
                    )
                    ExportFormatButton(
                        label = "KML",
                        color = colorScheme.secondary,
                        modifier = Modifier.weight(1f).testTag("export_kml_btn"),
                        onClick = { onExport(ExportFormat.KML) }
                    )
                    ExportFormatButton(
                        label = "CSV",
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).testTag("export_csv_btn"),
                        onClick = { onExport(ExportFormat.CSV) }
                    )
                    ExportFormatButton(
                        label = "GeoJSON",
                        color = colorScheme.tertiary,
                        modifier = Modifier.weight(1.15f).testTag("export_geojson_btn"),
                        onClick = { onExport(ExportFormat.GEOJSON) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportFormatButton(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        modifier = modifier.height(44.dp)
    ) {
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color, subtitle: String? = null) {
    Column {
        Text(
            text = label,
            fontSize = 8.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.04.sp,
            maxLines = 1
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
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

private fun shareExportFile(context: Context, payload: TrackerViewModel.ExportPayload) {
    try {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        TrackExporter.cleanupOldExportCache(exportDir)

        val exportFile = File(exportDir, payload.suggestedFileName).apply {
            writeText(payload.content)
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile
        )

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
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "Failed to share ${payload.format.shortCode}: ${e.localizedMessage ?: "Unknown error"}",
            Toast.LENGTH_SHORT
        ).show()
    }
}
