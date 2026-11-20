package com.geotrace.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationCity
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.geotrace.model.MapTileType
import com.geotrace.ui.theme.LocalCustomColors

val ScreenListContentPadding = PaddingValues(
    start = 16.dp,
    top = 12.dp,
    end = 16.dp,
    bottom = 28.dp
)

fun Modifier.adaptiveContentWidth(): Modifier = this.widthIn(max = 640.dp)

enum class StatusBadgeTone {
    NOMINAL,
    ACTIVE,
    WARNING,
    ERROR,
    NEUTRAL
}

@Composable
fun GeoTraceStatusBadge(
    text: String,
    tone: StatusBadgeTone,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    val (bg, fg, border) = when (tone) {
        StatusBadgeTone.NOMINAL -> Triple(
            customColors.statusNominalBg,
            customColors.statusNominalText,
            customColors.statusNominalBorder
        )
        StatusBadgeTone.ACTIVE -> Triple(
            customColors.statusActiveBg,
            customColors.statusActiveText,
            customColors.statusActiveBorder
        )
        StatusBadgeTone.WARNING -> Triple(
            customColors.statusWarningBg,
            customColors.statusWarningText,
            customColors.statusWarningBorder
        )
        StatusBadgeTone.ERROR -> Triple(
            colorScheme.errorContainer,
            colorScheme.onErrorContainer,
            colorScheme.error.copy(alpha = 0.45f)
        )
        StatusBadgeTone.NEUTRAL -> Triple(
            customColors.surfaceContainerLow,
            colorScheme.onSurfaceVariant,
            colorScheme.outlineVariant.copy(alpha = 0.65f)
        )
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun GeoTraceTopBar(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = "Back",
    backTestTag: String? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .background(customColors.surfaceContainerLow, RoundedCornerShape(12.dp))
                    .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
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
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(1.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
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

        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = trailingContent
            )
        }
    }
}

fun MapTileType.icon(): ImageVector = when (this) {
    MapTileType.STANDARD -> Icons.Default.Map
    MapTileType.SATELLITE -> Icons.Default.SatelliteAlt
    MapTileType.HYBRID -> Icons.Default.Layers
    MapTileType.TOPO -> Icons.Default.Terrain
    MapTileType.HUMANITARIAN -> Icons.Default.LocationCity
}

@Composable
fun TileLayerPickerList(
    selectedTileType: MapTileType,
    onSelectTileType: (MapTileType) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MapTileType.entries.forEach { tileType ->
            val isSelected = selectedTileType == tileType
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) colorScheme.primary.copy(alpha = 0.10f) else customColors.surfaceContainerLow,
                border = BorderStroke(
                    if (isSelected) 1.5.dp else 1.dp,
                    if (isSelected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectTileType(tileType) }
                    .testTag("tile_layer_${tileType.name.lowercase()}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (isSelected) colorScheme.primary.copy(alpha = 0.14f)
                                    else colorScheme.surface
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = tileType.icon(),
                                contentDescription = null,
                                tint = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = tileType.label,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isSelected) colorScheme.primary else colorScheme.onSurface
                            )
                            Text(
                                text = tileType.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (isSelected) "Active layer" else "Select layer",
                        tint = if (isSelected) colorScheme.primary else colorScheme.outline,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HeadingUpToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String? = null,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (checked) colorScheme.primary.copy(alpha = 0.08f) else customColors.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (checked) colorScheme.primary.copy(alpha = 0.4f) else colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (checked) colorScheme.primary.copy(alpha = 0.14f)
                            else colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = if (checked) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Heading-Up Map Rotation",
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "Auto-rotates map with compass & travel bearing (2-finger twist always active)",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
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
