package com.geotrace.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        shape = CircleShape,
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
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
    modifier: Modifier = Modifier,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(customColors.surfaceContainerLow, CircleShape)
                    .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f), CircleShape)
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
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) colorScheme.primary.copy(alpha = 0.10f) else customColors.surfaceContainerLow,
                border = BorderStroke(
                    if (isSelected) 1.5.dp else 1.dp,
                    if (isSelected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(20.dp))
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
                                .clip(RoundedCornerShape(11.dp))
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

private fun MapTileType.compactLabel(): String = when (this) {
    MapTileType.STANDARD -> "OSM Streets"
    MapTileType.SATELLITE -> "Satellite"
    MapTileType.HYBRID -> "Sat + Labels"
    MapTileType.TOPO -> "Topo Relief"
    MapTileType.HUMANITARIAN -> "Humanitarian"
}

@Composable
fun TileLayerPickerGrid(
    selectedTileType: MapTileType,
    onSelectTileType: (MapTileType) -> Unit,
    offlineCoveredTypes: Set<MapTileType> = emptySet(),
    modifier: Modifier = Modifier
) {
    val rows = MapTileType.entries.chunked(3)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        rows.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { tileType ->
                    BasemapSwatchTile(
                        tileType = tileType,
                        isSelected = selectedTileType == tileType,
                        isOfflineCovered = tileType in offlineCoveredTypes,
                        onClick = { onSelectTileType(tileType) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BasemapSwatchTile(
    tileType: MapTileType,
    isSelected: Boolean,
    isOfflineCovered: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val customColors = LocalCustomColors.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(vertical = 2.dp)
            .testTag("tile_layer_${tileType.name.lowercase()}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(customColors.surfaceContainerLow)
                .border(
                    width = if (isSelected) 2.5.dp else 1.dp,
                    color = if (isSelected) colorScheme.primary else colorScheme.outlineVariant.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            BasemapMiniPreview(
                tileType = tileType,
                modifier = Modifier.fillMaxSize()
            )
            if (isOfflineCovered) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.tertiaryContainer,
                    border = BorderStroke(1.dp, colorScheme.tertiary.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                ) {
                    Text(
                        text = "OFFLINE",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.4.sp,
                        color = colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (isSelected) colorScheme.primary
                        else colorScheme.surface.copy(alpha = 0.88f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tileType.icon(),
                    contentDescription = null,
                    tint = if (isSelected) colorScheme.onPrimary else colorScheme.onSurface,
                    modifier = Modifier.size(13.dp)
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Active layer",
                        tint = colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = tileType.compactLabel(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) colorScheme.primary else colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BasemapMiniPreview(
    tileType: MapTileType,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        when (tileType) {
            MapTileType.STANDARD -> {
                drawRect(Color(0xFFEAF1EE))
                // Park & water blocks
                drawRoundRect(
                    color = Color(0xFFC7E4C8),
                    topLeft = Offset(w * 0.08f, h * 0.12f),
                    size = Size(w * 0.34f, h * 0.36f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
                drawPath(
                    path = Path().apply {
                        moveTo(0f, h * 0.72f)
                        quadraticTo(w * 0.45f, h * 0.56f, w, h * 0.68f)
                        lineTo(w, h)
                        lineTo(0f, h)
                        close()
                    },
                    color = Color(0xFFB5DAF2)
                )
                // Arterial roads
                drawLine(Color.White, Offset(0f, h * 0.34f), Offset(w, h * 0.22f), strokeWidth = 5.5f)
                drawLine(Color.White, Offset(w * 0.56f, 0f), Offset(w * 0.48f, h), strokeWidth = 5.5f)
                drawLine(Color(0xFFF6C873), Offset(0f, h * 0.58f), Offset(w, h * 0.42f), strokeWidth = 4.5f)
            }
            MapTileType.SATELLITE -> {
                drawRect(Color(0xFF283C31))
                drawPath(
                    path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(w * 0.58f, 0f)
                        lineTo(w * 0.42f, h * 0.62f)
                        lineTo(0f, h * 0.48f)
                        close()
                    },
                    color = Color(0xFF395240)
                )
                drawPath(
                    path = Path().apply {
                        moveTo(w * 0.52f, h * 0.45f)
                        lineTo(w, h * 0.32f)
                        lineTo(w, h)
                        lineTo(w * 0.36f, h)
                        close()
                    },
                    color = Color(0xFF4F664C)
                )
                drawLine(Color(0xFF7B8575), Offset(0f, h * 0.68f), Offset(w, h * 0.26f), strokeWidth = 4.5f)
                drawLine(Color(0xFF1F2F28), Offset(w * 0.2f, 0f), Offset(w * 0.72f, h), strokeWidth = 3f)
            }
            MapTileType.HYBRID -> {
                drawRect(Color(0xFF2B4138))
                drawPath(
                    path = Path().apply {
                        moveTo(0f, h * 0.2f)
                        lineTo(w * 0.65f, 0f)
                        lineTo(w * 0.48f, h * 0.7f)
                        lineTo(0f, h * 0.6f)
                        close()
                    },
                    color = Color(0xFF3D594A)
                )
                // Overlay bright vector roads + label pill
                drawLine(Color(0xFFFFD56B), Offset(0f, h * 0.62f), Offset(w, h * 0.30f), strokeWidth = 4.5f)
                drawLine(Color(0xFFE6F0EC), Offset(w * 0.34f, 0f), Offset(w * 0.62f, h), strokeWidth = 3f)
                drawRoundRect(
                    color = Color(0xE614201C),
                    topLeft = Offset(w * 0.48f, h * 0.16f),
                    size = Size(w * 0.40f, h * 0.18f),
                    cornerRadius = CornerRadius(6f, 6f)
                )
            }
            MapTileType.TOPO -> {
                drawRect(Color(0xFFF3EFE4))
                val contourStroke = Stroke(width = 2.4f, cap = StrokeCap.Round)
                drawPath(
                    path = Path().apply {
                        moveTo(0f, h * 0.28f)
                        cubicTo(w * 0.35f, h * 0.08f, w * 0.65f, h * 0.45f, w, h * 0.22f)
                    },
                    color = Color(0xFFB3946B),
                    style = contourStroke
                )
                drawPath(
                    path = Path().apply {
                        moveTo(0f, h * 0.54f)
                        cubicTo(w * 0.35f, h * 0.34f, w * 0.65f, h * 0.70f, w, h * 0.48f)
                    },
                    color = Color(0xFF9C7B52),
                    style = contourStroke
                )
                drawPath(
                    path = Path().apply {
                        moveTo(0f, h * 0.80f)
                        cubicTo(w * 0.38f, h * 0.60f, w * 0.68f, h * 0.90f, w, h * 0.72f)
                    },
                    color = Color(0xFF7CA47B),
                    style = contourStroke
                )
            }
            MapTileType.HUMANITARIAN -> {
                drawRect(Color(0xFFF8F1E7))
                // High-contrast urban building footprints & emergency corridors
                drawRoundRect(
                    color = Color(0xFFE4D5C3),
                    topLeft = Offset(w * 0.10f, h * 0.14f),
                    size = Size(w * 0.26f, h * 0.28f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawRoundRect(
                    color = Color(0xFFE4D5C3),
                    topLeft = Offset(w * 0.60f, h * 0.52f),
                    size = Size(w * 0.28f, h * 0.30f),
                    cornerRadius = CornerRadius(4f, 4f)
                )
                drawLine(Color(0xFFD9644A), Offset(0f, h * 0.46f), Offset(w, h * 0.34f), strokeWidth = 5f)
                drawLine(Color(0xFFFFFFFF), Offset(w * 0.46f, 0f), Offset(w * 0.42f, h), strokeWidth = 5f)
                drawLine(Color(0xFFB98E68), Offset(w * 0.46f, 0f), Offset(w * 0.42f, h), strokeWidth = 1.8f)
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
        shape = RoundedCornerShape(16.dp),
        color = if (checked) colorScheme.primary.copy(alpha = 0.08f) else customColors.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (checked) colorScheme.primary.copy(alpha = 0.4f) else colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
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
                        .size(36.dp)
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
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
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
        shape = RoundedCornerShape(16.dp),
        color = if (checked) colorScheme.primary.copy(alpha = 0.08f) else customColors.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (checked) colorScheme.primary.copy(alpha = 0.4f) else colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
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
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (checked) colorScheme.primary.copy(alpha = 0.14f)
                            else colorScheme.surface
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (checked) colorScheme.primary else colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = description,
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

