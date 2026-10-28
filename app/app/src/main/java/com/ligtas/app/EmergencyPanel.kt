package com.ligtas.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** One emergency type. The ORDER must match AlertTemplate.all() in the Java code (the tile index is the template index). */
private class TileSpec(val icon: Int, val label: Int, val sub: Int? = null)

private val TILES = listOf(
    TileSpec(R.drawable.ic_activity, R.string.emergency_earthquake),
    TileSpec(R.drawable.ic_gun, R.string.emergency_shooter),
    TileSpec(R.drawable.ic_flame, R.string.emergency_fire),
    TileSpec(R.drawable.ic_bomb, R.string.emergency_bomb),
    TileSpec(R.drawable.ic_flask, R.string.emergency_chemical),
    TileSpec(R.drawable.ic_ellipsis, R.string.emergency_others, R.string.emergency_others_sub),
)

/**
 * The emergency-type grid plus the red "Alert everyone" button.
 * Stateless: it shows [selected] (-1 = nothing) and reports taps. The Java code decides what a tap means.
 */
@Composable
fun EmergencyPanel(
    selected: Int,
    onSelect: (Int) -> Unit,
    onAlertEveryone: () -> Unit,
    modifier: Modifier = Modifier,
    statusEnabled: Boolean = false,
    editEnabled: Boolean = false,
    edited: Boolean = false,
    onStatus: () -> Unit = {},
    onEdit: () -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TILES.chunked(2).forEachIndexed { row, pair ->
            if (row > 0) Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEachIndexed { col, spec ->
                    val index = row * 2 + col
                    EmergencyTile(
                        spec = spec,
                        selected = selected == index,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        AlertEveryoneButton(onClick = onAlertEveryone)
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ActionPill(
                icon = R.drawable.ic_chart,
                label = stringResource(R.string.status_label),
                enabled = statusEnabled,
                onClick = onStatus,
                modifier = Modifier.weight(1f),
            )
            ActionPill(
                icon = R.drawable.ic_pencil,
                label = stringResource(if (edited) R.string.edited_label else R.string.edit_label),
                enabled = editEnabled,
                onClick = onEdit,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** The Status and Edit pills: faded and untappable while [enabled] is false, with the same press dip as the tiles. */
@Composable
private fun ActionPill(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Ligtas.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(160), label = "pillAlpha")
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pillPress",
    )

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha },
        shape = LigtasShapes.pill,
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun EmergencyTile(
    spec: TileSpec,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Ligtas.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Animations: the tint, ring and icon change over 160 ms; the whole tile dips slightly while a finger is on it.
    // The tile is frosted glass: a blurred copy of the moving background, a faint white tint and a light edge.
    val tint by animateColorAsState(
        if (selected) colors.selectedBg.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.055f),
        tween(160), label = "tileTint",
    )
    val ring by animateColorAsState(if (selected) colors.selectedBorder else Color.White.copy(alpha = 0.16f), tween(160), label = "tileRing")
    val ringWidth by animateDpAsState(if (selected) 2.dp else 1.dp, tween(160), label = "tileRingWidth")
    val iconScale by animateFloatAsState(if (selected) 1.35f else 1f, tween(160), label = "tileIcon")
    val pressScale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tilePress",
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .height(118.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .semantics { this.selected = selected },
        shape = LigtasShapes.tile,
        color = Color.Transparent,
        border = BorderStroke(ringWidth, if (selected) SolidColor(ring) else Brush.linearGradient(
            listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f)))),
        interactionSource = interaction,
    ) {
        Box(Modifier.fillMaxSize()) {
            GlassBackdrop(Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(tint))
            // a soft light edge at the top, like light catching the glass
            Box(Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.Transparent), endY = 160f)))
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(LigtasShapes.chip).background(colors.chip.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(spec.icon),
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale },
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(spec.label),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (spec.sub != null) {
                    Text(
                        text = stringResource(spec.sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertEveryoneButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Ligtas.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // Press feedback: the button sinks to 96% and darkens a little while held.
    val scale by animateFloatAsState(
        if (pressed) 0.96f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "alertPress",
    )
    val fill by animateColorAsState(
        if (pressed) colors.danger.copy(alpha = 0.85f) else colors.danger,
        tween(100),
        label = "alertFill",
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale },
        shape = LigtasShapes.alertButton,
        color = fill,
        border = BorderStroke(1.dp, colors.dangerBorder),
        interactionSource = interaction,
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_siren),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(25.dp),
                )
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = "Alert everyone",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                )
                Text(
                    text = "Tap to send an emergency alert",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090F1C, widthDp = 380)
@Composable
private fun EmergencyPanelPreview() {
    LigtasTheme {
        EmergencyPanel(
            selected = 2, onSelect = {}, onAlertEveryone = {},
            statusEnabled = false, editEnabled = true,
            modifier = Modifier.padding(24.dp),
        )
    }
}
