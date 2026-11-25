package com.geotrace.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.geotrace.data.export.ExportFormat
import com.geotrace.data.export.TrackExporter
import com.geotrace.data.export.TrackImporter
import com.geotrace.model.CoordinateFormat
import com.geotrace.model.SavedPlace
import com.geotrace.model.SearchPlaceResult
import com.geotrace.model.SearchResultSource
import com.geotrace.model.SpeedUnit
import com.geotrace.model.TrackingMode
import com.geotrace.model.TrackingSession
import com.geotrace.model.TrailWaypoint
import com.geotrace.model.TripGroup
import com.geotrace.service.ElevationSummary
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
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val sessions by viewModel.allSessions.collectAsState()
    val trips by viewModel.trips.collectAsState()
    val savedPlaces by viewModel.savedPlaces.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val exportingSessionId by viewModel.exportingSessionId.collectAsState()

    var historyQuery by rememberSaveable { mutableStateOf("") }
    var selectedModeFilter by rememberSaveable { mutableStateOf<TrackingMode?>(null) }
    var selectedTripFilterId by rememberSaveable { mutableStateOf<String?>(null) }

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
    var sessionToAssignTrip by remember { mutableStateOf<TrackingSession?>(null) }
    var renameInput by rememberSaveable { mutableStateOf("") }
    var showCreateTripDialog by rememberSaveable { mutableStateOf(false) }
    var newTripNameInput by rememberSaveable { mutableStateOf("") }
    var newTripNotesInput by rememberSaveable { mutableStateOf("") }
    var tripToDelete by remember { mutableStateOf<TripGroup?>(null) }
    var waypointDialogSession by remember { mutableStateOf<TrackingSession?>(null) }
    var sessionWaypoints by remember { mutableStateOf<List<TrailWaypoint>>(emptyList()) }
    var isLoadingWaypoints by remember { mutableStateOf(false) }
    var expandedPhotoPath by rememberSaveable { mutableStateOf<String?>(null) }
    var elevationDialogSession by remember { mutableStateOf<TrackingSession?>(null) }
    var elevationSummary by remember { mutableStateOf<ElevationSummary?>(null) }
    var isLoadingElevation by remember { mutableStateOf(false) }
    var showSavedPlacesDialog by rememberSaveable { mutableStateOf(false) }
    var savedPlacesTripFilterId by rememberSaveable { mutableStateOf<String?>(null) }
    var placeToAssignTrip by remember { mutableStateOf<SavedPlace?>(null) }
    var placeToEdit by remember { mutableStateOf<SavedPlace?>(null) }

    LaunchedEffect(trips) {
        if (selectedTripFilterId != null && selectedTripFilterId != "__UNASSIGNED__" && trips.none { it.id == selectedTripFilterId }) {
            selectedTripFilterId = null
        }
        if (savedPlacesTripFilterId != null && savedPlacesTripFilterId != "__UNASSIGNED__" && trips.none { it.id == savedPlacesTripFilterId }) {
            savedPlacesTripFilterId = null
        }
    }

    val tripNameById = remember(trips) { trips.associate { it.id to it.name } }

    val filteredSessions = remember(sessions, historyQuery, selectedModeFilter, selectedTripFilterId) {
        val q = historyQuery.trim()
        sessions.filter { s ->
            val modeMatches = selectedModeFilter == null || s.trackingMode == selectedModeFilter
            val tripMatches = when (selectedTripFilterId) {
                null -> true
                "__UNASSIGNED__" -> s.tripId.isNullOrBlank()
                else -> s.tripId == selectedTripFilterId
            }
            if (!modeMatches || !tripMatches) {
                false
            } else if (q.isEmpty()) {
                true
            } else {
                s.name.contains(q, ignoreCase = true) ||
                    s.trackingMode.title.contains(q, ignoreCase = true)
            }
        }
    }

    // Native Storage Access Framework (SAF) saver with format-specific MIME type & suggested filename
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = remember { CreateExportDocumentContract() }
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

    val importRouteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val (displayName, content) = withContext(Dispatchers.IO) {
                        var fileName = "Imported Route"
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            if (c.moveToFirst()) {
                                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0) fileName = c.getString(idx) ?: fileName
                            }
                        }
                        val text = context.contentResolver.openInputStream(uri)?.use {
                            TrackImporter.readBoundedUtf8(it)
                        } ?: ""
                        fileName to text
                    }
                    val ext = displayName.substringAfterLast('.', "")
                    val baseName = displayName.substringBeforeLast('.').ifBlank { displayName }
                    viewModel.importRouteFromContent(content, baseName, ext) { res ->
                        res.onSuccess { route ->
                            Toast.makeText(
                                context,
                                "Loaded ${route.name} (${route.formattedDistance}) onto Map",
                                Toast.LENGTH_SHORT
                            ).show()
                            onNavigateBack?.invoke()
                        }.onFailure { err ->
                            Toast.makeText(
                                context,
                                err.message ?: "Could not import route file.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "Import failed: ${e.localizedMessage ?: "Read error"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
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

    fun openElevationDialog(session: TrackingSession) {
        elevationDialogSession = session
        elevationSummary = null
        isLoadingElevation = true
        viewModel.loadElevationSummaryForSession(session.id) { summary ->
            elevationSummary = summary
            isLoadingElevation = false
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
                    subtitle = "SQLite tracks, elevation profiles, GPX/KML/GeoJSON import & export",
                    onBack = onNavigateBack,
                    backContentDescription = "Back to Live Map"
                )
            }

            item(key = "import_external_route_card") {
                OutlinedButton(
                    onClick = { importRouteLauncher.launch(arrayOf("*/*")) },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colorScheme.tertiary.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colorScheme.tertiary.copy(alpha = 0.08f),
                        contentColor = colorScheme.tertiary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("history_import_route_btn")
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Import External Route (GPX / KML / GeoJSON)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            item(key = "saved_places_manager_entry") {
                OutlinedButton(
                    onClick = {
                        savedPlacesTripFilterId = selectedTripFilterId
                        showSavedPlacesDialog = true
                    },
                    shape = CircleShape,
                    border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colorScheme.primary.copy(alpha = 0.07f),
                        contentColor = colorScheme.primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("open_saved_places_btn")
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Saved Places & Bookmarks (${savedPlaces.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Multi-Day Expedition / Trip Groups Strip
            item(key = "trip_groups_strip") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tripChipColors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.secondary.copy(alpha = 0.16f),
                            selectedLabelColor = colorScheme.secondary
                        )
                        FilterChip(
                            selected = selectedTripFilterId == null,
                            onClick = { selectedTripFilterId = null },
                            leadingIcon = {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                            },
                            label = { Text("All Trips", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                            shape = CircleShape,
                            colors = tripChipColors,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("trip_filter_all")
                        )
                        for (trip in trips) {
                            val tripSessionCount = sessions.count { it.tripId == trip.id }
                            FilterChip(
                                selected = selectedTripFilterId == trip.id,
                                onClick = {
                                    selectedTripFilterId = if (selectedTripFilterId == trip.id) null else trip.id
                                },
                                label = {
                                    Text("${trip.name} ($tripSessionCount)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                },
                                shape = CircleShape,
                                colors = tripChipColors,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("trip_filter_${trip.id}")
                            )
                        }
                        if (trips.isNotEmpty()) {
                            val unassignedCount = sessions.count { it.tripId.isNullOrBlank() }
                            FilterChip(
                                selected = selectedTripFilterId == "__UNASSIGNED__",
                                onClick = {
                                    selectedTripFilterId = if (selectedTripFilterId == "__UNASSIGNED__") null else "__UNASSIGNED__"
                                },
                                label = { Text("No Trip ($unassignedCount)", fontSize = 11.sp) },
                                shape = CircleShape,
                                colors = tripChipColors,
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                newTripNameInput = ""
                                newTripNotesInput = ""
                                showCreateTripDialog = true
                            },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("create_trip_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Trip", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    val selectedTrip = trips.firstOrNull { it.id == selectedTripFilterId }
                    if (selectedTrip != null) {
                        val tripSessions = sessions.filter { it.tripId == selectedTrip.id }
                        val tripPlacesCount = savedPlaces.count { it.tripId == selectedTrip.id }
                        val tripDistMeters = tripSessions.sumOf { it.totalDistanceMeters }
                        val tripDurSec = tripSessions.sumOf { it.currentDurationSeconds(liveNowMs) }
                        val tripDistLabel = if (tripDistMeters >= 1000.0) {
                            String.format(Locale.US, "%.2f km", tripDistMeters / 1000.0)
                        } else {
                            String.format(Locale.US, "%.0f m", tripDistMeters)
                        }
                        val tripDurLabel = run {
                            val h = tripDurSec / 3600
                            val m = (tripDurSec % 3600) / 60
                            if (h > 0) "${h}h ${m}m" else "${m}m"
                        }
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("selected_trip_summary_card"),
                            shape = RoundedCornerShape(22.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.secondaryContainer.copy(alpha = 0.25f)),
                            border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.45f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = selectedTrip.name,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = colorScheme.onSurface
                                        )
                                        if (selectedTrip.notes.isNotBlank()) {
                                            Text(
                                                text = selectedTrip.notes,
                                                fontSize = 11.sp,
                                                color = colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { tripToDelete = selectedTrip },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Trip Group",
                                            tint = colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MetricItem("DAYS / TRAILS", "${tripSessions.size}", colorScheme.primary)
                                    MetricItem("TRIP DIST", tripDistLabel, colorScheme.secondary)
                                    MetricItem("TIME", tripDurLabel, colorScheme.onSurface)
                                    Box(
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable {
                                                savedPlacesTripFilterId = selectedTrip.id
                                                showSavedPlacesDialog = true
                                            }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                            .testTag("trip_bookmarks_metric_btn"),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        MetricItem("BOOKMARKS", "$tripPlacesCount", colorScheme.tertiary, subtitle = "Manage")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (sessions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
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
                                    shape = CircleShape,
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
                // Aggregate Telemetry Totals & Search/Filter Controls
                item(key = "history_summary_and_filters") {
                    val totalDistMeters = remember(sessions) { sessions.sumOf { it.totalDistanceMeters } }
                    val totalPoints = remember(sessions) { sessions.sumOf { it.pointCount } }
                    val totalDurationSec = sessions.sumOf { it.currentDurationSeconds(liveNowMs) }
                    val totalDistText = remember(totalDistMeters) {
                        if (totalDistMeters >= 1000.0) {
                            String.format(Locale.US, "%.2f km", totalDistMeters / 1000.0)
                        } else {
                            String.format(Locale.US, "%.0f m", totalDistMeters)
                        }
                    }
                    val totalTimeText = remember(totalDurationSec) {
                        val h = totalDurationSec / 3600
                        val m = (totalDurationSec % 3600) / 60
                        if (h > 0) "${h}h ${m}m" else "${m}m ${totalDurationSec % 60}s"
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            border = BorderStroke(1.dp, colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(0.85f)) {
                                    MetricItem("TRAILS", "${sessions.size}", colorScheme.primary)
                                }
                                Box(modifier = Modifier.width(1.dp).height(28.dp).background(colorScheme.outlineVariant.copy(alpha = 0.45f)))
                                Box(modifier = Modifier.weight(1.1f).padding(start = 8.dp)) {
                                    MetricItem("TOTAL DIST", totalDistText, colorScheme.secondary)
                                }
                                Box(modifier = Modifier.width(1.dp).height(28.dp).background(colorScheme.outlineVariant.copy(alpha = 0.45f)))
                                Box(modifier = Modifier.weight(1.05f).padding(start = 8.dp)) {
                                    MetricItem("TOTAL TIME", totalTimeText, colorScheme.onSurface)
                                }
                                Box(modifier = Modifier.width(1.dp).height(28.dp).background(colorScheme.outlineVariant.copy(alpha = 0.45f)))
                                Box(modifier = Modifier.weight(0.95f).padding(start = 8.dp)) {
                                    MetricItem("TRACKPTS", "$totalPoints", colorScheme.tertiary)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = historyQuery,
                            onValueChange = { historyQuery = it },
                            placeholder = { Text("Search trails by name or mode...", fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = if (historyQuery.isNotEmpty()) {
                                {
                                    IconButton(onClick = { historyQuery = "" }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear trail search",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            } else null,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = { keyboardController?.hide() }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("history_search_input")
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val chipColors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorScheme.primary.copy(alpha = 0.16f),
                                selectedLabelColor = colorScheme.primary
                            )
                            FilterChip(
                                selected = selectedModeFilter == null,
                                onClick = { selectedModeFilter = null },
                                label = { Text("All (${sessions.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                shape = CircleShape,
                                colors = chipColors,
                                modifier = Modifier.heightIn(min = 44.dp)
                            )
                            FilterChip(
                                selected = selectedModeFilter == TrackingMode.HIGH_ACCURACY,
                                onClick = {
                                    selectedModeFilter = if (selectedModeFilter == TrackingMode.HIGH_ACCURACY) null else TrackingMode.HIGH_ACCURACY
                                },
                                label = { Text("GPS", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                shape = CircleShape,
                                colors = chipColors,
                                modifier = Modifier.heightIn(min = 44.dp)
                            )
                            FilterChip(
                                selected = selectedModeFilter == TrackingMode.BALANCED,
                                onClick = {
                                    selectedModeFilter = if (selectedModeFilter == TrackingMode.BALANCED) null else TrackingMode.BALANCED
                                },
                                label = { Text("Balanced", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                shape = CircleShape,
                                colors = chipColors,
                                modifier = Modifier.heightIn(min = 44.dp)
                            )
                            FilterChip(
                                selected = selectedModeFilter == TrackingMode.POWER_SAVER,
                                onClick = {
                                    selectedModeFilter = if (selectedModeFilter == TrackingMode.POWER_SAVER) null else TrackingMode.POWER_SAVER
                                },
                                label = { Text("Cell Only", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                shape = CircleShape,
                                colors = chipColors,
                                modifier = Modifier.heightIn(min = 44.dp)
                            )
                        }
                    }
                }

                if (filteredSessions.isEmpty()) {
                    item(key = "history_empty_filter") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            border = BorderStroke(1.dp, colorScheme.outlineVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No matching trails",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Try a different search keyword or reset the mode filter.",
                                    fontSize = 12.sp,
                                    color = colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedButton(
                                    onClick = {
                                        historyQuery = ""
                                        selectedModeFilter = null
                                        selectedTripFilterId = null
                                    },
                                    shape = CircleShape,
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("Clear Filters", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    items(filteredSessions, key = { it.id }) { session ->
                        SessionHistoryCard(
                            session = session,
                            tripName = session.tripId?.let { tripNameById[it] },
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
                            onViewElevation = { openElevationDialog(session) },
                            onAssignTrip = { sessionToAssignTrip = session },
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
    }

    // Elevation Profile & Ascent/Descent Dialog
    if (elevationDialogSession != null) {
        val targetSession = elevationDialogSession!!
        TrailElevationDialog(
            session = targetSession,
            summary = elevationSummary,
            isLoading = isLoadingElevation,
            onDismiss = { elevationDialogSession = null }
        )
    }

    // Trail Waypoints Inspection & Management Dialog
    if (waypointDialogSession != null) {
        val targetSession = waypointDialogSession!!
        TrailWaypointsDialog(
            sessionName = targetSession.name,
            waypoints = sessionWaypoints,
            isLoading = isLoadingWaypoints,
            coordinateFormat = settings.coordinateFormat,
            onOpenPhoto = { path -> expandedPhotoPath = path },
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

    // Full-Screen Waypoint / Saved Place Photo Viewer Dialog
    if (expandedPhotoPath != null) {
        val rawPath = expandedPhotoPath!!
        val photoFile = File(rawPath)
        val photoAvailable = !rawPath.startsWith("/") || photoFile.exists()
        Dialog(
            onDismissRequest = { expandedPhotoPath = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.scrim.copy(alpha = 0.92f)),
                color = colorScheme.scrim.copy(alpha = 0.92f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (photoAvailable) {
                        AsyncImage(
                            model = if (rawPath.startsWith("/")) photoFile else rawPath,
                            contentDescription = "Full screen waypoint photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        )
                    } else {
                        Text(
                            text = "Photo file is no longer available on device.",
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    IconButton(
                        onClick = { expandedPhotoPath = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                            .size(48.dp)
                            .background(colorScheme.surface.copy(alpha = 0.85f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close photo preview",
                            tint = colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    // Create New Trip Dialog
    if (showCreateTripDialog) {
        AlertDialog(
            onDismissRequest = { showCreateTripDialog = false },
            title = { Text("New Multi-Day Trip", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTripNameInput,
                        onValueChange = { newTripNameInput = it },
                        label = { Text("Trip Name", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Rwenzori 4-Day Trek", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_trip_name_input")
                    )
                    OutlinedTextField(
                        value = newTripNotesInput,
                        onValueChange = { newTripNotesInput = it },
                        label = { Text("Itinerary Notes (Optional)", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createTrip(newTripNameInput, newTripNotesInput) { trip ->
                            selectedTripFilterId = trip.id
                            Toast.makeText(context, "Created trip \"${trip.name}\"", Toast.LENGTH_SHORT).show()
                        }
                        showCreateTripDialog = false
                    },
                    enabled = newTripNameInput.isNotBlank(),
                    shape = CircleShape,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_create_trip_btn")
                ) {
                    Text("Create Trip", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateTripDialog = false },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = colorScheme.surface
        )
    }

    // Delete Trip Confirmation Dialog (keeps trails intact)
    if (tripToDelete != null) {
        val targetTrip = tripToDelete!!
        AlertDialog(
            onDismissRequest = { tripToDelete = null },
            title = { Text("Delete Trip Group?", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Text(
                    text = "Remove trip folder \"${targetTrip.name}\"? Recorded trails and saved places inside it will remain safe in your history.",
                    fontSize = 13.sp,
                    color = colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedTripFilterId == targetTrip.id) selectedTripFilterId = null
                        viewModel.deleteTrip(targetTrip.id)
                        tripToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.error, contentColor = colorScheme.onError),
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Remove Folder", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { tripToDelete = null },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = colorScheme.surface
        )
    }

    // Assign Session to Trip Dialog
    if (sessionToAssignTrip != null) {
        val targetSession = sessionToAssignTrip!!
        AlertDialog(
            onDismissRequest = { sessionToAssignTrip = null },
            title = {
                Column {
                    Text("Assign Trail to Trip", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = targetSession.name,
                        fontSize = 11.sp,
                        color = colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = targetSession.tripId == null,
                        onClick = {
                            viewModel.assignSessionToTrip(targetSession.id, null)
                            sessionToAssignTrip = null
                        },
                        label = { Text("No Trip (Standalone Trail)", fontSize = 12.sp) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )
                    for (trip in trips) {
                        FilterChip(
                            selected = targetSession.tripId == trip.id,
                            onClick = {
                                viewModel.assignSessionToTrip(targetSession.id, trip.id)
                                sessionToAssignTrip = null
                            },
                            label = { Text(trip.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            sessionToAssignTrip = null
                            newTripNameInput = ""
                            newTripNotesInput = ""
                            showCreateTripDialog = true
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create New Trip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { sessionToAssignTrip = null },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Close", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = colorScheme.surface
        )
    }

    // Rename Trail Dialog
    if (sessionToRename != null) {
        val target = sessionToRename!!
        var renameTouched by remember(target.id) { mutableStateOf(false) }
        val isRenameMissing = renameTouched && renameInput.isBlank()
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = {
                Text("Rename Trail", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = {
                        renameTouched = true
                        renameInput = it
                    },
                    label = { Text("Trail Name", fontSize = 12.sp) },
                    isError = isRenameMissing,
                    supportingText = {
                        Text(
                            text = if (isRenameMissing) "Trail name cannot be empty." else "Saved locally in trail history",
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
                            if (renameInput.isNotBlank()) {
                                viewModel.renameSession(target.id, renameInput)
                                sessionToRename = null
                            } else {
                                renameTouched = true
                            }
                        }
                    ),
                    shape = RoundedCornerShape(16.dp),
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
                    shape = CircleShape,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Save Name", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { sessionToRename = null },
                    shape = CircleShape,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(28.dp),
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
                    shape = CircleShape,
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
                    shape = CircleShape,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(28.dp),
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
                        shape = RoundedCornerShape(16.dp),
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
                                saveDocumentLauncher.launch(payload)
                            },
                            shape = CircleShape,
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
                            shape = CircleShape,
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
                    shape = CircleShape,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Close", color = colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = colorScheme.surface
        )
    }

    // Saved Places Manager Dialog
    if (showSavedPlacesDialog) {
        SavedPlacesManagerDialog(
            savedPlaces = savedPlaces,
            trips = trips,
            tripNameById = tripNameById,
            selectedTripFilterId = savedPlacesTripFilterId,
            coordinateFormat = settings.coordinateFormat,
            onTripFilterSelected = { savedPlacesTripFilterId = it },
            onShowOnMap = { place ->
                val subtitle = listOfNotNull(
                    place.category.takeIf { it.isNotBlank() },
                    place.note.takeIf { it.isNotBlank() },
                    "Saved Place"
                ).joinToString(" • ")
                viewModel.selectSearchResult(
                    SearchPlaceResult(
                        id = "saved_${place.id}",
                        name = place.name,
                        subtitle = subtitle,
                        latitude = place.latitude,
                        longitude = place.longitude,
                        source = SearchResultSource.SAVED_PLACE,
                        photoUri = place.photoUri
                    ),
                    saveRecent = false
                )
                showSavedPlacesDialog = false
                onNavigateBack?.invoke()
            },
            onAssignTrip = { place -> placeToAssignTrip = place },
            onEditPlace = { place -> placeToEdit = place },
            onOpenPhoto = { path -> expandedPhotoPath = path },
            onDeletePlace = { place ->
                viewModel.deleteSavedPlace(place.id)
                Toast.makeText(context, "Removed \"${place.name}\"", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSavedPlacesDialog = false }
        )
    }

    // Assign Saved Place to Trip Dialog
    if (placeToAssignTrip != null) {
        val targetPlace = placeToAssignTrip!!
        AlertDialog(
            onDismissRequest = { placeToAssignTrip = null },
            title = {
                Column {
                    Text("Assign Place to Trip", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        text = targetPlace.name,
                        fontSize = 11.sp,
                        color = colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = targetPlace.tripId == null,
                        onClick = {
                            viewModel.assignPlaceToTrip(targetPlace.id, null)
                            placeToAssignTrip = null
                        },
                        label = { Text("Standalone (No Trip)", fontSize = 12.sp) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    )
                    for (trip in trips) {
                        FilterChip(
                            selected = targetPlace.tripId == trip.id,
                            onClick = {
                                viewModel.assignPlaceToTrip(targetPlace.id, trip.id)
                                placeToAssignTrip = null
                            },
                            label = { Text(trip.name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            placeToAssignTrip = null
                            newTripNameInput = ""
                            newTripNotesInput = ""
                            showCreateTripDialog = true
                        },
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create New Trip", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { placeToAssignTrip = null },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Close", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = colorScheme.surface
        )
    }

    // Edit Saved Place Dialog
    if (placeToEdit != null) {
        val targetPlace = placeToEdit!!
        val categories = remember { listOf("Spot", "Lodge", "Camp", "Trailhead", "Viewpoint", "Water", "Medical") }
        var editName by remember(targetPlace.id) { mutableStateOf(targetPlace.name) }
        var editCategory by remember(targetPlace.id) { mutableStateOf(targetPlace.category.ifBlank { "Spot" }) }
        var editNote by remember(targetPlace.id) { mutableStateOf(targetPlace.note) }
        var editTripId by remember(targetPlace.id) { mutableStateOf(targetPlace.tripId) }

        AlertDialog(
            onDismissRequest = { placeToEdit = null },
            title = {
                Text("Edit Saved Place", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .testTag("edit_saved_place_dialog"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Place Name", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_place_name_input")
                    )
                    Text(
                        text = "Category",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (cat in categories) {
                            FilterChip(
                                selected = editCategory.equals(cat, ignoreCase = true),
                                onClick = { editCategory = cat },
                                label = { Text(cat, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                shape = CircleShape,
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = editNote,
                        onValueChange = { editNote = it },
                        label = { Text("Field Note (Optional)", fontSize = 12.sp) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("edit_place_note_input")
                    )
                    if (trips.isNotEmpty()) {
                        Text(
                            text = "Trip",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = editTripId == null,
                                onClick = { editTripId = null },
                                label = { Text("Standalone", fontSize = 11.sp) },
                                shape = CircleShape,
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                            for (trip in trips) {
                                FilterChip(
                                    selected = editTripId == trip.id,
                                    onClick = { editTripId = trip.id },
                                    label = { Text(trip.name, fontSize = 11.sp, maxLines = 1) },
                                    shape = CircleShape,
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateSavedPlace(
                            id = targetPlace.id,
                            name = editName,
                            category = editCategory,
                            note = editNote,
                            tripId = editTripId
                        )
                        Toast.makeText(context, "Updated saved place", Toast.LENGTH_SHORT).show()
                        placeToEdit = null
                    },
                    shape = CircleShape,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("confirm_edit_saved_place_btn")
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { placeToEdit = null },
                    shape = CircleShape,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            },
            shape = RoundedCornerShape(28.dp),
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
    onOpenPhoto: (String) -> Unit,
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
                                shape = RoundedCornerShape(16.dp),
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
                                    if (wp.photoUri.isNotBlank()) {
                                        val wpPhotoAvailable = !wp.photoUri.startsWith("/") || File(wp.photoUri).exists()
                                        if (wpPhotoAvailable) {
                                            AsyncImage(
                                                model = if (wp.photoUri.startsWith("/")) File(wp.photoUri) else wp.photoUri,
                                                contentDescription = "${wp.name} field photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(52.dp)
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                                    .clickable { onOpenPhoto(wp.photoUri) }
                                            )
                                        } else {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = colorScheme.surface,
                                                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                                modifier = Modifier.size(52.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                                    Text(
                                                        text = "Photo\nMissing",
                                                        fontSize = 8.5.sp,
                                                        lineHeight = 10.sp,
                                                        color = colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
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
                shape = CircleShape,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Close", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = colorScheme.surface
    )
}

@Composable
private fun SessionHistoryCard(
    session: TrackingSession,
    tripName: String?,
    liveNowMs: Long,
    speedUnit: SpeedUnit,
    isExporting: Boolean,
    exportBusy: Boolean,
    onViewOnMap: (() -> Unit)?,
    onViewWaypoints: (() -> Unit)?,
    onViewElevation: () -> Unit,
    onAssignTrip: () -> Unit,
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
            .clip(RoundedCornerShape(24.dp))
            .testTag("session_card_${session.id}"),
        shape = RoundedCornerShape(24.dp),
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
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
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clip(CircleShape)
                                    .clickable { onAssignTrip() }
                                    .testTag("assign_trip_badge_${session.id}")
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (tripName != null) colorScheme.secondary.copy(alpha = 0.14f) else customColors.surfaceContainerLow,
                                    border = BorderStroke(1.dp, if (tripName != null) colorScheme.secondary.copy(alpha = 0.4f) else colorScheme.outlineVariant)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = "Assign to Trip",
                                            tint = if (tripName != null) colorScheme.secondary else colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = tripName ?: "+ Trip",
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (tripName != null) colorScheme.secondary else colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
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
                        .background(customColors.surfaceContainerLow, RoundedCornerShape(16.dp))
                        .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
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

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onViewOnMap != null) {
                        OutlinedButton(
                            onClick = onViewOnMap,
                            shape = CircleShape,
                            border = BorderStroke(1.dp, colorScheme.primary.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = colorScheme.primary.copy(alpha = 0.06f),
                                contentColor = colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .height(48.dp)
                                .testTag("view_trail_map_btn")
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Map",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = onViewElevation,
                        shape = CircleShape,
                        border = BorderStroke(1.dp, colorScheme.secondary.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = colorScheme.secondary.copy(alpha = 0.06f),
                            contentColor = colorScheme.secondary
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(48.dp)
                            .testTag("view_elevation_btn")
                    ) {
                        Icon(Icons.Default.Terrain, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Elevation",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (onViewWaypoints != null) {
                        OutlinedButton(
                            onClick = onViewWaypoints,
                            shape = CircleShape,
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
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${session.waypointCount}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
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
        shape = CircleShape,
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

private class CreateExportDocumentContract : ActivityResultContract<TrackerViewModel.ExportPayload, Uri?>() {
    override fun createIntent(context: Context, input: TrackerViewModel.ExportPayload): Intent {
        return Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = input.format.mimeType
            putExtra(Intent.EXTRA_TITLE, input.suggestedFileName)
        }
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        return if (resultCode == Activity.RESULT_OK) intent?.data else null
    }
}

@Composable
private fun TrailElevationDialog(
    session: TrackingSession,
    summary: ElevationSummary?,
    isLoading: Boolean,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Elevation Profile",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = colorScheme.onSurface
                )
                Text(
                    text = "${session.name} • ${session.formattedDistance}",
                    fontSize = 11.sp,
                    color = colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            when {
                isLoading || summary == null -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(28.dp))
                    }
                }
                !summary.hasValidElevation || summary.profileSamples.size < 2 -> {
                    Text(
                        text = "No vertical altitude telemetry was captured for this trail (e.g., 2D network/cellular-only points).",
                        fontSize = 12.sp,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(customColors.surfaceContainerLow, RoundedCornerShape(16.dp))
                                .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MetricItem("ASCENT", "+${summary.totalAscentMeters.toInt()} m", colorScheme.tertiary)
                            MetricItem("DESCENT", "-${summary.totalDescentMeters.toInt()} m", colorScheme.error)
                            MetricItem("MIN ALT", "${summary.minAltitudeMeters.toInt()} m", colorScheme.onSurfaceVariant)
                            MetricItem("MAX ALT", "${summary.maxAltitudeMeters.toInt()} m", colorScheme.primary)
                        }

                        val samples = summary.profileSamples
                        val minAlt = summary.minAltitudeMeters
                        val maxAlt = summary.maxAltitudeMeters
                        val rawSpan = maxAlt - minAlt
                        val altRange = rawSpan.coerceAtLeast(10.0)
                        val baselineAlt = if (rawSpan < 10.0) {
                            ((minAlt + maxAlt) * 0.5) - (altRange * 0.5)
                        } else {
                            minAlt
                        }
                        val topAlt = baselineAlt + altRange
                        val midAlt = baselineAlt + (altRange * 0.5)
                        val maxDist = samples.last().distanceMeters.coerceAtLeast(1.0)
                        fun formatChartDist(meters: Double): String = if (meters >= 1000.0) {
                            String.format(Locale.US, "%.1fkm", meters / 1000.0)
                        } else {
                            "${meters.toInt()}m"
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = customColors.surfaceContainerLow,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(156.dp)
                            ) {
                                val lineColor = colorScheme.primary
                                val fillTop = colorScheme.primary.copy(alpha = 0.28f)
                                val fillBottom = colorScheme.primary.copy(alpha = 0.02f)
                                val gridColor = colorScheme.outlineVariant.copy(alpha = 0.45f)

                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .height(136.dp)
                                            .padding(end = 6.dp),
                                        verticalArrangement = Arrangement.SpaceBetween,
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        Text(
                                            text = "${topAlt.toInt()}m",
                                            fontSize = 9.sp,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "${midAlt.toInt()}m",
                                            fontSize = 9.sp,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "${baselineAlt.toInt()}m",
                                            fontSize = 9.sp,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Canvas(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(136.dp)
                                            .padding(vertical = 4.dp)
                                    ) {
                                        // Horizontal reference lines
                                        for (step in 1..3) {
                                            val y = size.height * (step / 4f)
                                            drawLine(
                                                color = gridColor,
                                                start = Offset(0f, y),
                                                end = Offset(size.width, y),
                                                strokeWidth = 1f
                                            )
                                        }

                                        val linePath = Path()
                                        val fillPath = Path()
                                        samples.forEachIndexed { idx, s ->
                                            val x = ((s.distanceMeters / maxDist).toFloat().coerceIn(0f, 1f)) * size.width
                                            val normY = ((s.altitudeMeters - baselineAlt) / altRange).toFloat().coerceIn(0f, 1f)
                                            val y = size.height - normY * size.height
                                            if (idx == 0) {
                                                linePath.moveTo(x, y)
                                                fillPath.moveTo(x, size.height)
                                                fillPath.lineTo(x, y)
                                            } else {
                                                linePath.lineTo(x, y)
                                                fillPath.lineTo(x, y)
                                            }
                                            if (idx == samples.lastIndex) {
                                                fillPath.lineTo(x, size.height)
                                                fillPath.close()
                                            }
                                        }

                                        drawPath(
                                            path = fillPath,
                                            brush = Brush.verticalGradient(listOf(fillTop, fillBottom))
                                        )
                                        drawPath(
                                            path = linePath,
                                            color = lineColor,
                                            style = Stroke(
                                                width = 4f,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "0m",
                                    fontSize = 9.5.sp,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatChartDist(maxDist * 0.5),
                                    fontSize = 9.5.sp,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatChartDist(maxDist),
                                    fontSize = 9.5.sp,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "Vertical jitter filtered (±4m hysteresis) across ${summary.profileSamples.size} sampled terrain intervals.",
                            fontSize = 10.5.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = CircleShape,
                modifier = Modifier.height(48.dp)
            ) {
                Text("Close", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = colorScheme.surface
    )
}

@Composable
private fun SavedPlacesManagerDialog(
    savedPlaces: List<SavedPlace>,
    trips: List<TripGroup>,
    tripNameById: Map<String, String>,
    selectedTripFilterId: String?,
    coordinateFormat: CoordinateFormat,
    onTripFilterSelected: (String?) -> Unit,
    onShowOnMap: (SavedPlace) -> Unit,
    onAssignTrip: (SavedPlace) -> Unit,
    onEditPlace: (SavedPlace) -> Unit,
    onOpenPhoto: (String) -> Unit,
    onDeletePlace: (SavedPlace) -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val filteredPlaces = remember(savedPlaces, selectedTripFilterId) {
        when (selectedTripFilterId) {
            null -> savedPlaces
            "__UNASSIGNED__" -> savedPlaces.filter { it.tripId.isNullOrBlank() }
            else -> savedPlaces.filter { it.tripId == selectedTripFilterId }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .adaptiveContentWidth()
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(28.dp),
            color = colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Saved Places (${filteredPlaces.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = "Offline bookmarks, trailheads, lodges & camps",
                            fontSize = 11.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("close_saved_places_dialog_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Saved Places",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (trips.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = selectedTripFilterId == null,
                            onClick = { onTripFilterSelected(null) },
                            label = { Text("All (${savedPlaces.size})", fontSize = 11.sp) },
                            shape = CircleShape,
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                        for (trip in trips) {
                            val count = savedPlaces.count { it.tripId == trip.id }
                            FilterChip(
                                selected = selectedTripFilterId == trip.id,
                                onClick = {
                                    onTripFilterSelected(if (selectedTripFilterId == trip.id) null else trip.id)
                                },
                                label = { Text("${trip.name} ($count)", fontSize = 11.sp, maxLines = 1) },
                                shape = CircleShape,
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                        val standaloneCount = savedPlaces.count { it.tripId.isNullOrBlank() }
                        FilterChip(
                            selected = selectedTripFilterId == "__UNASSIGNED__",
                            onClick = {
                                onTripFilterSelected(if (selectedTripFilterId == "__UNASSIGNED__") null else "__UNASSIGNED__")
                            },
                            label = { Text("Standalone ($standaloneCount)", fontSize = 11.sp) },
                            shape = CircleShape,
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                if (filteredPlaces.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = customColors.surfaceContainerLow,
                        border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bookmark,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No saved places in this view",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurface
                            )
                            Text(
                                text = "Long-press anywhere on the map or tap Save Place on any pin to bookmark a spot offline.",
                                fontSize = 11.sp,
                                color = colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (place in filteredPlaces) {
                            val assignedTripName = place.tripId?.let { tripNameById[it] }
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = customColors.surfaceContainerLow,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("saved_place_row_${place.id}")
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (place.photoUri.isNotBlank()) {
                                            val photoAvailable = !place.photoUri.startsWith("/") || File(place.photoUri).exists()
                                            if (photoAvailable) {
                                                AsyncImage(
                                                    model = if (place.photoUri.startsWith("/")) File(place.photoUri) else place.photoUri,
                                                    contentDescription = "${place.name} photo",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(52.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .border(1.dp, colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                                                        .clickable { onOpenPhoto(place.photoUri) }
                                                )
                                            } else {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = colorScheme.surface,
                                                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                                    modifier = Modifier.size(52.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(4.dp)) {
                                                        Text(
                                                            text = "Photo\nMissing",
                                                            fontSize = 8.5.sp,
                                                            lineHeight = 10.sp,
                                                            color = colorScheme.onSurfaceVariant,
                                                            textAlign = TextAlign.Center
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = colorScheme.primary.copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = place.category.ifBlank { "Spot" },
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (!assignedTripName.isNullOrBlank()) {
                                                    Surface(
                                                        shape = CircleShape,
                                                        color = colorScheme.secondary.copy(alpha = 0.14f)
                                                    ) {
                                                        Text(
                                                            text = assignedTripName,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = colorScheme.secondary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = place.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (place.note.isNotBlank()) {
                                                Text(
                                                    text = place.note,
                                                    fontSize = 11.sp,
                                                    color = colorScheme.onSurfaceVariant,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                text = formatCoordinates(place.latitude, place.longitude, coordinateFormat),
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontSize = 10.sp,
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontFeatureSettings = "tnum"
                                                ),
                                                color = colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedButton(
                                            onClick = { onShowOnMap(place) },
                                            shape = CircleShape,
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("saved_place_map_btn_${place.id}")
                                        ) {
                                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Map", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        OutlinedButton(
                                            onClick = { onAssignTrip(place) },
                                            shape = CircleShape,
                                            contentPadding = PaddingValues(horizontal = 10.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .heightIn(min = 48.dp)
                                                .testTag("saved_place_trip_btn_${place.id}")
                                        ) {
                                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = if (place.tripId.isNullOrBlank()) "Assign Trip" else "Move Trip",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1
                                            )
                                        }
                                        IconButton(
                                            onClick = { onEditPlace(place) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("saved_place_edit_btn_${place.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Edit ${place.name}",
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = { onDeletePlace(place) },
                                            modifier = Modifier
                                                .size(48.dp)
                                                .testTag("saved_place_delete_btn_${place.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Delete ${place.name}",
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
            }
        }
    }
}

