package com.geotrace.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Route
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.model.TrackingSession
import com.geotrace.ui.theme.LocalCustomColors
import com.geotrace.viewmodel.TrackerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun HistoryScreen(
    viewModel: TrackerViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (onNavigateBack != null) {
        BackHandler { onNavigateBack() }
    }

    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val sessions by viewModel.allSessions.collectAsState()

    var exportContent by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(Type, Text)

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
                        IconButton(onClick = onNavigateBack, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Live Map",
                                tint = colorScheme.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Column {
                        Text(
                            text = "Recorded Trails & Telemetry",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onBackground
                        )
                        Text(
                            text = "Persisted SQLite tracks with GPX, KML & CSV exports",
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
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
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
                items(sessions) { session ->
                    SessionHistoryCard(
                        session = session,
                        onExportGpx = {
                            viewModel.exportGpx(session) { gpx ->
                                exportContent = Pair("GPX (GPS Exchange Format)", gpx)
                            }
                        },
                        onExportKml = {
                            viewModel.exportKml(session) { kml ->
                                exportContent = Pair("KML (Google Earth)", kml)
                            }
                        },
                        onExportCsv = {
                            viewModel.exportCsv(session) { csv ->
                                exportContent = Pair("CSV (Raw Telemetry)", csv)
                            }
                        },
                        onDelete = { viewModel.deleteSession(session.id) }
                    )
                }
            }
        }
    }

    // Export Preview & Share Dialog (Level 3 Elevation)
    if (exportContent != null) {
        val (type, content) = exportContent!!
        AlertDialog(
            onDismissRequest = { exportContent = null },
            title = {
                Text(
                    text = "Export: $type",
                    color = colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "File snippet preview (${content.lines().size} lines):",
                        color = colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = customColors.surfaceContainerLow,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = content.take(1500) + if (content.length > 1500) "\n... [truncated]" else "",
                            color = colorScheme.primary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        shareExportFile(context, type, content)
                        exportContent = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.primary,
                        contentColor = colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("share_export_btn")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share / Save File", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { exportContent = null },
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
    onExportGpx: () -> Unit,
    onExportKml: () -> Unit,
    onExportCsv: () -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_card_${session.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column {
            // Top Accent Line in Cobalt Secondary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(colorScheme.secondary)
            )

            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = session.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurface
                        )
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

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Session", tint = colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Tabular Metrics Row (Responsive Weighted Recessed Well)
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
                    Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                        MetricItem(
                            label = "AVG SPEED",
                            value = "${String.format(Locale.US, "%.1f", session.avgSpeedKmh)} km/h",
                            color = colorScheme.onSurface,
                            subtitle = session.avgPaceMinPerKm.takeIf { it != "--:--" }
                        )
                    }
                    Box(modifier = Modifier.width(1.dp).height(30.dp).background(colorScheme.outlineVariant.copy(alpha = 0.4f)))
                    Box(modifier = Modifier.weight(1f).padding(start = 6.dp)) {
                        MetricItem("POINTS", "${session.pointCount}", colorScheme.tertiary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Export Buttons (Hairline Bordered Tactical Buttons)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onExportGpx,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.primary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f).height(38.dp).testTag("export_gpx_btn")
                    ) {
                        Text("GPX", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onExportKml,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.secondary),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f).height(38.dp).testTag("export_kml_btn")
                    ) {
                        Text("KML", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = onExportCsv,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.onSurfaceVariant),
                        border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant),
                        modifier = Modifier.weight(1f).height(38.dp).testTag("export_csv_btn")
                    ) {
                        Text("CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color, subtitle: String? = null) {
    Column {
        Text(
            text = label,
            fontSize = 9.sp,
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
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

private fun shareExportFile(context: Context, type: String, content: String) {
    try {
        val (extension, mimeType) = when (type.uppercase()) {
            "GPX" -> "gpx" to "application/gpx+xml"
            "KML" -> "kml" to "application/vnd.google-earth.kml+xml"
            "CSV" -> "csv" to "text/csv"
            else -> "txt" to "text/plain"
        }

        val exportDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "geotrace_${type.lowercase()}_$timeStamp.$extension"
        val exportFile = java.io.File(exportDir, fileName).apply {
            writeText(content)
        }

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            exportFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            this.type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "GeoTrace $type Export - $fileName")
            putExtra(Intent.EXTRA_TITLE, "GeoTrace $type Track ($fileName)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share $type Export").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        android.widget.Toast.makeText(
            context,
            "Failed to share $type file: ${e.localizedMessage ?: "Unknown error"}",
            android.widget.Toast.LENGTH_SHORT
        ).show()
    }
}
