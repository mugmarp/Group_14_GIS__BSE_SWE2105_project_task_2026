package com.example.gistracker.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
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
import com.example.gistracker.model.TrackingSession
import com.example.gistracker.ui.theme.CardSurface
import com.example.gistracker.ui.theme.DangerRed
import com.example.gistracker.ui.theme.DarkNavy
import com.example.gistracker.ui.theme.ElectricCyan
import com.example.gistracker.ui.theme.SafetyAmber
import com.example.gistracker.viewmodel.TrackerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: TrackerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessions by viewModel.allSessions.collectAsState()

    var exportDialogSession by remember { mutableStateOf<TrackingSession?>(null) }
    var exportContent by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(Type, Text)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkNavy)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Column {
                    Text(
                        text = "Recorded Trails & Exports",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Stored GPX/KML trails for analysis, GIS mapping & audit",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            if (sessions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CardSurface)
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
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No recorded sessions yet",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Start recording on the Map screen or toggle simulation to log trail telemetry.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
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
                                exportContent = Pair("KML (Google Earth Format)", kml)
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

    // Export Preview & Share Dialog
    if (exportContent != null) {
        val (type, content) = exportContent!!
        AlertDialog(
            onDismissRequest = { exportContent = null },
            title = {
                Text(
                    text = "Export: $type",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "File snippet preview (${content.lines().size} lines):",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = DarkNavy
                    ) {
                        Text(
                            text = content.take(1500) + if (content.length > 1500) "\n... [truncated]" else "",
                            color = ElectricCyan,
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
                        shareExportText(context, type, content)
                        exportContent = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                    modifier = Modifier.testTag("share_export_btn")
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = DarkNavy, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share / Save File", color = DarkNavy, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { exportContent = null }) {
                    Text("Close", color = Color.LightGray)
                }
            },
            containerColor = CardSurface
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("session_card_${session.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF273552))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = session.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(session.startTime))
                    Text(
                        text = "$dateStr | ${session.trackingMode.name}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Session", tint = DangerRed)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem("DISTANCE", session.formattedDistance, ElectricCyan)
                MetricItem("DURATION", session.formattedDuration, SafetyAmber)
                MetricItem("AVG SPEED", "${String.format("%.1f", session.avgSpeedKmh)} km/h", Color.White)
                MetricItem("POINTS", "${session.pointCount}", Color(0xFFB388FF))
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Export Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExportGpx,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("export_gpx_btn")
                ) {
                    Text("GPX", fontSize = 11.sp, color = ElectricCyan, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onExportKml,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("export_kml_btn")
                ) {
                    Text("KML", fontSize = 11.sp, color = SafetyAmber, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onExportCsv,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).testTag("export_csv_btn")
                ) {
                    Text("CSV", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, color: Color) {
    Column {
        Text(text = label, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(
            text = value,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

private fun shareExportText(context: Context, type: String, content: String) {
    val intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, content)
        putExtra(Intent.EXTRA_SUBJECT, "GIS Tracker Export - $type")
        putExtra(Intent.EXTRA_TITLE, "GIS Tracker Export")
        this.type = "text/plain"
    }
    val chooser = Intent.createChooser(intent, "Share $type Export")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
