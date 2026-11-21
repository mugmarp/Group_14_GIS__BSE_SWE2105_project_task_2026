package com.geotrace.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.geotrace.model.SearchPlaceResult
import com.geotrace.model.SearchResultSource
import com.geotrace.ui.theme.JetBrainsMonoFontFamily
import com.geotrace.ui.theme.LocalCustomColors
import java.util.Locale

@Composable
fun PlaceSearchDialog(
    query: String,
    results: List<SearchPlaceResult>,
    isSearchingOnline: Boolean,
    isNetworkOnline: Boolean,
    searchError: String? = null,
    offlinePlacesCount: Int,
    offlineRegionsCount: Int,
    onQueryChange: (String) -> Unit,
    onSelectResult: (SearchPlaceResult) -> Unit,
    onClearRecents: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(80L)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f))
                .clickable(onClick = onDismiss)
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = colorScheme.surface,
                shadowElevation = 10.dp,
                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .clickable(enabled = false) {}
                    .testTag("place_search_dialog")
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Search Input Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("search_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Search",
                                tint = colorScheme.onSurface
                            )
                        }

                        OutlinedTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            placeholder = {
                                Text(
                                    text = "Search place, campus, DD/DMS coords...",
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    keyboardController?.hide()
                                    results.firstOrNull()?.let { onSelectResult(it) }
                                }
                            ),
                            trailingIcon = {
                                if (query.isNotEmpty()) {
                                    IconButton(
                                        onClick = { onQueryChange("") },
                                        modifier = Modifier.testTag("search_clear_btn")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear Search",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colorScheme.primary,
                                unfocusedBorderColor = colorScheme.outlineVariant.copy(alpha = 0.6f),
                                focusedContainerColor = customColors.surfaceContainerLow,
                                unfocusedContainerColor = customColors.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .focusRequester(focusRequester)
                                .testTag("place_search_input")
                        )
                    }

                    // Connectivity & Offline Index Capability Status Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (isNetworkOnline && searchError == null) Icons.Default.CloudQueue else Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = if (isNetworkOnline && searchError == null) colorScheme.tertiary else customColors.warning,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    searchError != null -> searchError
                                    isNetworkOnline -> "Online OSM + Offline Index ($offlinePlacesCount POIs)"
                                    else -> "Offline Mode • $offlinePlacesCount cached POIs • Coords & Zones"
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isNetworkOnline && searchError == null) colorScheme.tertiary else customColors.warning,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (results.any { it.source == SearchResultSource.RECENT }) {
                            TextButton(
                                onClick = onClearRecents,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.heightIn(min = 36.dp)
                            ) {
                                Text("Clear Recents", fontSize = 10.sp)
                            }
                        }
                    }

                    if (isSearchingOnline) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                        )
                    } else {
                        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }

                    // Results List
                    if (results.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = when {
                                    query.isBlank() -> "Type any place name, address, Safe Zone, or paste coordinates (e.g. -0.6155, 30.6564)."
                                    isSearchingOnline -> "Searching OpenStreetMap & local cache..."
                                    !isNetworkOnline && offlineRegionsCount == 0 -> "No local match offline. Download an Offline Map Region while online to pre-cache POIs, or enter DD/DMS coordinates."
                                    !isNetworkOnline -> "No match in Safe Zones or $offlinePlacesCount cached POIs. Connect to search wider OpenStreetMap."
                                    else -> "No matching places or coordinates found for \"$query\"."
                                },
                                fontSize = 12.sp,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            contentPadding = PaddingValues(vertical = 6.dp)
                        ) {
                            itemsIndexed(
                                items = results,
                                key = { index, it -> "${index}_${it.source.name}_${it.id}" }
                            ) { _, place ->
                                SearchResultRow(
                                    place = place,
                                    onClick = {
                                        keyboardController?.hide()
                                        onSelectResult(place)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    place: SearchPlaceResult,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val (icon, tintColor) = when (place.source) {
        SearchResultSource.COORDINATE -> Icons.Default.MyLocation to colorScheme.primary
        SearchResultSource.SAFE_ZONE -> Icons.Default.Shield to colorScheme.tertiary
        SearchResultSource.WAYPOINT -> Icons.Default.Flag to customColors.warning
        SearchResultSource.OFFLINE_REGION -> Icons.Default.Map to colorScheme.secondary
        SearchResultSource.OFFLINE_POI -> Icons.Default.Bookmark to colorScheme.tertiary
        SearchResultSource.DEVICE_OR_PEER -> Icons.Default.Devices to colorScheme.secondary
        SearchResultSource.RECENT -> Icons.Default.History to colorScheme.onSurfaceVariant
        SearchResultSource.ONLINE_OSM -> Icons.Default.Place to colorScheme.primary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("search_result_${place.source.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(tintColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = place.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = tintColor.copy(alpha = 0.13f)
                ) {
                    Text(
                        text = place.source.badge,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = tintColor,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            if (place.subtitle.isNotBlank()) {
                Text(
                    text = place.subtitle,
                    fontSize = 11.sp,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        place.distanceMeters?.let { dist ->
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (dist < 1000.0) "${dist.toInt()}m" else String.format(Locale.US, "%.1fkm", dist / 1000.0),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "tnum"
                ),
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}
