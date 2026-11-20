package com.geotrace.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.model.MapTileType
import com.geotrace.ui.theme.LocalCustomColors

/**
 * Shared responsive header bar across all secondary/root pages so back buttons,
 * titles, subtitles, breadcrumbs, and trailing actions stay visually cohesive.
 */
@Composable
fun GeoTraceScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = "Back",
    backTestTag: String? = null,
    trailingActions: (@Composable RowScope.() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .background(customColors.surfaceContainerLow, RoundedCornerShape(12.dp))
                    .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.65f), RoundedCornerShape(12.dp))
                    .then(if (backTestTag != null) Modifier.testTag(backTestTag) else Modifier)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backContentDescription,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            if (!eyebrow.isNullOrBlank()) {
                Text(
                    text = eyebrow.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = title,
                style = if (onBack == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                color = colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (trailingActions != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = trailingActions
            )
        }
    }
}

fun mapTileTypeIcon(tileType: MapTileType): ImageVector = when (tileType) {
    MapTileType.STANDARD -> Icons.Default.Map
    MapTileType.SATELLITE -> Icons.Default.SatelliteAlt
    MapTileType.HYBRID -> Icons.Default.Layers
    MapTileType.TOPO -> Icons.Default.Terrain
    MapTileType.HUMANITARIAN -> Icons.Default.Explore
}

/**
 * Single-source basemap option row shared between the Map Layer bottom sheet
 * and Settings -> Map Layers & Offline Cache so labels, icons, and selection states match.
 */
@Composable
fun SharedBasemapOptionRow(
    tileType: MapTileType,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) colorScheme.primary.copy(alpha = 0.11f) else customColors.surfaceContainerLow,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(
                            color = if (isSelected) colorScheme.primary.copy(alpha = 0.14f) else colorScheme.surface,
                            shape = RoundedCornerShape(9.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = mapTileTypeIcon(tileType),
                        contentDescription = null,
                        tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = tileType.label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isSelected) colorScheme.primary else colorScheme.onSurface
                    )
                    Text(
                        text = tileType.description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Active layer" else "Select ${tileType.label}",
                tint = if (isSelected) colorScheme.primary else colorScheme.outlineVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Single-source map overlay toggle row used in the Layer bottom sheet.
 */
@Composable
fun SharedMapOverlayToggleRow(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) colorScheme.primary.copy(alpha = 0.08f) else customColors.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (checked) colorScheme.primary.copy(alpha = 0.35f) else colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (checked) colorScheme.primary else colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colorScheme.onPrimary,
                    checkedTrackColor = colorScheme.primary,
                    uncheckedThumbColor = colorScheme.outline,
                    uncheckedTrackColor = colorScheme.surfaceVariant
                )
            )
        }
    }
}
