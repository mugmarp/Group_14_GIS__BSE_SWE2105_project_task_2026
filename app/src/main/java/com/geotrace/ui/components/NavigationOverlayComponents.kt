package com.geotrace.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.model.OfflineDownloadProgress
import com.geotrace.navigation.ActiveNavigationState
import com.geotrace.navigation.NavManeuverType
import com.geotrace.navigation.NavPreviewState
import com.geotrace.navigation.NavProfile
import com.geotrace.navigation.NavRoute
import com.geotrace.navigation.NavRouteSource
import com.geotrace.navigation.VoiceGuidanceMode
import com.geotrace.navigation.formatNavDistance
import com.geotrace.navigation.formatNavDuration
import com.geotrace.ui.theme.JetBrainsMonoFontFamily
import com.geotrace.ui.theme.LocalCustomColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun maneuverIcon(type: NavManeuverType): ImageVector = when (type) {
    NavManeuverType.START -> Icons.Default.Navigation
    NavManeuverType.STRAIGHT -> Icons.Default.Straight
    NavManeuverType.SLIGHT_LEFT -> Icons.Default.TurnSlightLeft
    NavManeuverType.LEFT -> Icons.Default.TurnLeft
    NavManeuverType.SHARP_LEFT -> Icons.Default.TurnSharpLeft
    NavManeuverType.SLIGHT_RIGHT -> Icons.Default.TurnSlightRight
    NavManeuverType.RIGHT -> Icons.Default.TurnRight
    NavManeuverType.SHARP_RIGHT -> Icons.Default.TurnSharpRight
    NavManeuverType.UTURN -> Icons.Default.UTurnLeft
    NavManeuverType.ROUNDABOUT_ENTER,
    NavManeuverType.ROUNDABOUT_EXIT -> Icons.Default.RoundaboutRight
    NavManeuverType.MERGE_KEEP -> Icons.AutoMirrored.Filled.MergeType
    NavManeuverType.ARRIVE -> Icons.Default.Flag
}

private fun profileIcon(profile: NavProfile): ImageVector = when (profile) {
    NavProfile.DRIVING -> Icons.Default.DirectionsCar
    NavProfile.MOTORCYCLE -> Icons.Default.TwoWheeler
    NavProfile.CYCLING -> Icons.AutoMirrored.Filled.DirectionsBike
    NavProfile.WALKING -> Icons.AutoMirrored.Filled.DirectionsWalk
}

@Composable
fun RoutePreviewCard(
    previewState: NavPreviewState,
    offlineProgress: OfflineDownloadProgress,
    isNetworkOnline: Boolean,
    onSelectProfile: (NavProfile) -> Unit,
    onSelectRoute: (Int) -> Unit,
    onStartNavigation: (NavRoute) -> Unit,
    onCacheCorridor: (NavRoute) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (previewState is NavPreviewState.Idle) return
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    val destName = when (previewState) {
        is NavPreviewState.Loading -> previewState.destinationName
        is NavPreviewState.Ready -> previewState.destinationName
        is NavPreviewState.Error -> previewState.destinationName
        NavPreviewState.Idle -> ""
    }
    val activeProfile = when (previewState) {
        is NavPreviewState.Loading -> previewState.profile
        is NavPreviewState.Ready -> previewState.profile
        is NavPreviewState.Error -> previewState.profile
        NavPreviewState.Idle -> NavProfile.DRIVING
    }

    Surface(
        modifier = modifier
            .adaptiveContentWidth()
            .fillMaxWidth()
            .testTag("route_preview_card"),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DIRECTIONS TO",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.05.sp,
                        color = colorScheme.primary
                    )
                    Text(
                        text = destName.ifBlank { "Destination" },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("nav_preview_close_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Directions Preview",
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }

            // Mode chips (Drive / Moto / Cycle / Walk)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavProfile.entries.forEach { profile ->
                    val selected = profile == activeProfile
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectProfile(profile) },
                        label = {
                            Text(
                                text = profile.label,
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = profileIcon(profile),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colorScheme.primaryContainer,
                            selectedLabelColor = colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("nav_profile_${profile.name.lowercase(Locale.US)}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (previewState) {
                is NavPreviewState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Calculating ${previewState.profile.label.lowercase(Locale.US)} road route...",
                            fontSize = 13.sp,
                            color = colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                is NavPreviewState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colorScheme.errorContainer.copy(alpha = 0.55f).compositeOver(colorScheme.surface),
                        border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = previewState.message,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onRetry,
                                    shape = CircleShape,
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("nav_retry_btn")
                                ) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                if (previewState.cachedFallbackRoute != null) {
                                    Button(
                                        onClick = { onStartNavigation(previewState.cachedFallbackRoute) },
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("nav_use_cached_btn")
                                    ) {
                                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Use Offline Route (${previewState.cachedFallbackRoute.formattedDuration})",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is NavPreviewState.Ready -> {
                    val selectedRoute = previewState.selectedRoute
                    if (previewState.routes.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            previewState.routes.forEachIndexed { idx, route ->
                                val isSel = idx == previewState.selectedIndex
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSel) {
                                        colorScheme.primaryContainer.copy(alpha = 0.55f).compositeOver(colorScheme.surface)
                                    } else {
                                        customColors.surfaceContainerLow
                                    },
                                    border = BorderStroke(
                                        width = if (isSel) 1.5.dp else 1.dp,
                                        color = if (isSel) colorScheme.primary else colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .widthIn(min = 140.dp)
                                        .heightIn(min = 56.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { onSelectRoute(idx) }
                                        .testTag("nav_alt_route_$idx")
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                        Text(
                                            text = route.formattedDuration,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSel) colorScheme.primary else colorScheme.onSurface
                                        )
                                        Text(
                                            text = "${route.formattedDistance} • ${if (idx == 0) "Fastest" else "Alt ${idx + 1}"}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (route.source == NavRouteSource.OFFLINE_OSM_GRAPH) {
                                                Icon(
                                                    imageVector = Icons.Default.OfflinePin,
                                                    contentDescription = null,
                                                    tint = colorScheme.tertiary,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                            Text(
                                                text = route.source.label,
                                                fontSize = 9.5.sp,
                                                fontWeight = if (route.source == NavRouteSource.OFFLINE_OSM_GRAPH) FontWeight.Bold else FontWeight.Normal,
                                                color = if (route.source == NavRouteSource.OFFLINE_OSM_GRAPH) colorScheme.tertiary else colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (offlineProgress.isDownloading) {
                        val frac = if (offlineProgress.totalTiles > 0) {
                            (offlineProgress.completedTiles.toFloat() / offlineProgress.totalTiles.toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                        )
                        Text(
                            text = "Caching corridor tiles (${offlineProgress.completedTiles}/${offlineProgress.totalTiles})",
                            fontSize = 10.sp,
                            color = colorScheme.primary,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }

                    if (selectedRoute != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isNetworkOnline) {
                                FilledTonalButton(
                                    onClick = { onCacheCorridor(selectedRoute) },
                                    shape = CircleShape,
                                    enabled = !offlineProgress.isDownloading,
                                    contentPadding = PaddingValues(horizontal = 14.dp),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("nav_cache_corridor_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (offlineProgress.isDownloading) "Caching..." else "Cache Corridor",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Button(
                                onClick = { onStartNavigation(selectedRoute) },
                                shape = CircleShape,
                                contentPadding = PaddingValues(horizontal = 18.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("nav_start_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Navigation,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Start",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                NavPreviewState.Idle -> Unit
            }
        }
    }
}

@Composable
fun TopTurnManeuverBanner(
    navState: ActiveNavigationState,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val upcomingStep = navState.nextStep ?: navState.currentStep
    val thenStep = if (navState.nextStep != null) navState.route.steps.getOrNull(navState.currentStepIndex + 2) else null
    val maneuver = upcomingStep?.maneuverType ?: NavManeuverType.STRAIGHT

    val containerColor = when {
        navState.hasArrived -> colorScheme.tertiaryContainer
        navState.isOffRoute -> customColors.statusWarningBg.copy(alpha = 0.94f).compositeOver(colorScheme.surface)
        else -> colorScheme.primaryContainer.copy(alpha = 0.94f).compositeOver(colorScheme.surface)
    }
    val contentColor = when {
        navState.hasArrived -> colorScheme.onTertiaryContainer
        navState.isOffRoute -> customColors.statusWarningText
        else -> colorScheme.onPrimaryContainer
    }
    val borderColor = when {
        navState.hasArrived -> colorScheme.tertiary
        navState.isOffRoute -> customColors.warning
        else -> colorScheme.primary
    }

    Column(
        modifier = modifier
            .adaptiveContentWidth()
            .fillMaxWidth()
            .testTag("top_turn_maneuver_banner")
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = containerColor,
            shadowElevation = 6.dp,
            border = BorderStroke(1.5.dp, borderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(borderColor.copy(alpha = 0.16f))
                            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                navState.hasArrived -> Icons.Default.Flag
                                navState.isOffRoute -> Icons.Default.Warning
                                else -> maneuverIcon(maneuver)
                            },
                            contentDescription = upcomingStep?.instruction ?: "Next Turn",
                            tint = borderColor,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                navState.hasArrived -> "Arrived"
                                navState.isRerouting -> "Rerouting..."
                                navState.isOffRoute -> "Off route (${navState.crossTrackDistanceMeters.toInt()}m)"
                                else -> formatNavDistance(navState.distanceToNextTurnMeters)
                            },
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 21.sp,
                                fontFeatureSettings = "tnum"
                            ),
                            color = contentColor,
                            maxLines = 1
                        )
                        Text(
                            text = when {
                                navState.hasArrived -> "You have arrived at ${navState.route.destinationName}"
                                navState.rerouteStatusNote != null -> navState.rerouteStatusNote
                                else -> upcomingStep?.instruction?.ifBlank { "Continue on route" } ?: "Follow highlighted route"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        val street = upcomingStep?.streetName.orEmpty()
                        if (!navState.hasArrived && street.isNotBlank() &&
                            upcomingStep?.instruction?.contains(street, ignoreCase = true) != true
                        ) {
                            Text(
                                text = street,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = contentColor.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (navState.hasArrived) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onStopNavigation,
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colorScheme.tertiary,
                                contentColor = colorScheme.onTertiary
                            ),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("nav_arrived_done_btn")
                        ) {
                            Text("Done", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (!navState.hasArrived && !navState.isOffRoute && thenStep != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colorScheme.surface.copy(alpha = 0.78f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Then",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = maneuverIcon(thenStep.maneuverType),
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = thenStep.instruction,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BottomNavTripBar(
    navState: ActiveNavigationState,
    voiceMode: VoiceGuidanceMode,
    isNetworkOnline: Boolean,
    onCycleVoiceMode: () -> Unit,
    onTriggerReroute: () -> Unit,
    onCacheCorridor: (NavRoute) -> Unit,
    onStopNavigation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val etaFormatted = remember(navState.etaEpochMs / 60_000L) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(navState.etaEpochMs))
    }

    Surface(
        modifier = modifier
            .adaptiveContentWidth()
            .fillMaxWidth()
            .testTag("bottom_nav_trip_bar"),
        shape = RoundedCornerShape(24.dp),
        color = colorScheme.surface,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (navState.hasArrived) "Arrived" else formatNavDuration(navState.remainingDurationSeconds),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (navState.hasArrived) colorScheme.tertiary else colorScheme.primary,
                    maxLines = 1
                )
                Text(
                    text = "${formatNavDistance(navState.remainingDistanceMeters)} • ETA $etaFormatted",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontFeatureSettings = "tnum"
                    ),
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onCycleVoiceMode,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("nav_voice_mode_btn")
            ) {
                Icon(
                    imageVector = when (voiceMode) {
                        VoiceGuidanceMode.VOICE_ON -> Icons.AutoMirrored.Filled.VolumeUp
                        VoiceGuidanceMode.ALERTS_ONLY -> Icons.Default.NotificationsActive
                        VoiceGuidanceMode.MUTED -> Icons.AutoMirrored.Filled.VolumeOff
                    },
                    contentDescription = "Voice guidance: ${voiceMode.label}",
                    tint = when (voiceMode) {
                        VoiceGuidanceMode.VOICE_ON -> colorScheme.primary
                        VoiceGuidanceMode.ALERTS_ONLY -> customColors.warning
                        VoiceGuidanceMode.MUTED -> colorScheme.onSurfaceVariant
                    }
                )
            }

            if (navState.isOffRoute && (isNetworkOnline || !navState.isOfflineRerouteBlocked)) {
                IconButton(
                    onClick = onTriggerReroute,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("nav_reroute_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.AltRoute,
                        contentDescription = "Recalculate Route",
                        tint = customColors.warning
                    )
                }
            } else if (isNetworkOnline && !navState.hasArrived) {
                IconButton(
                    onClick = { onCacheCorridor(navState.route) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("nav_corridor_quick_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Cache Route Corridor Offline",
                        tint = colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Button(
                onClick = onStopNavigation,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.error,
                    contentColor = colorScheme.onError
                ),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("nav_end_btn")
            ) {
                Text("End", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
