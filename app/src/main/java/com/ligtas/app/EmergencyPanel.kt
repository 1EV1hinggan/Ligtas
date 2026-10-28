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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

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
                        dimmed = selected >= 0 && selected != index,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f).enter(5 + index),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        AlertEveryoneButton(onClick = onAlertEveryone, modifier = Modifier.enter(11))
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth().enter(12),
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

/** The Status and Edit pills: frosted glass, faded and untappable while [enabled] is false, with the shared bounce. */
@Composable
private fun ActionPill(
    icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Ligtas.colors
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, tween(160), label = "pillAlpha")
    GlassPanel(
        modifier = modifier.height(52.dp).graphicsLayer { this.alpha = alpha }.bounceClick(enabled = enabled, onClick = onClick),
        shape = LigtasShapes.pill,
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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

/**
 * One emergency tile, with a full set of motion:
 *  - press: the tile sinks and tilts away from your finger, like pushing a glass key;
 *  - tap: a shockwave (two rings and a soft wave) spreads from the exact spot you touched, plus a light haptic tick;
 *  - select: the icon pops with a bounce and a wiggle, a glint sweeps across the glass, and a glowing edge breathes;
 *  - focus: the other tiles step back (smaller and dimmer) so the chosen one stands out.
 */
@Composable
private fun EmergencyTile(
    spec: TileSpec,
    selected: Boolean,
    dimmed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Ligtas.colors
    val haptic = LocalHapticFeedback.current
    val fire = onClick   // (the semantics block below has its own onClick, so keep a clearly named copy)

    var pressed by remember { mutableStateOf(false) }
    var touch by remember { mutableStateOf(Offset.Unspecified) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var taps by remember { mutableIntStateOf(0) }

    // One-shot animations (1 = finished)
    val shock = remember { Animatable(1f) }
    val glint = remember { Animatable(1f) }
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(taps) {
        if (taps > 0) { shock.snapTo(0f); shock.animateTo(1f, tween(750, easing = FastOutSlowInEasing)) }
    }
    LaunchedEffect(selected) {
        if (selected) {
            launch { glint.snapTo(0f); glint.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
            wiggle.snapTo(0f)
            wiggle.animateTo(0f, keyframes {
                durationMillis = 560
                -18f at 90; 13f at 210; -7f at 330; 3f at 440; 0f at 560
            })
        }
    }

    // Sink and tilt while a finger is down; the tilt leans away from where you press.
    val bouncy = spring<Float>(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow)
    val scale by animateFloatAsState(
        when { pressed -> 0.92f; dimmed -> 0.955f; else -> 1f }, bouncy, label = "tileScale",
    )
    val nx = if (pressed && touch.isSpecified && box.width > 0) (touch.x / box.width - 0.5f) * 2f else 0f
    val ny = if (pressed && touch.isSpecified && box.height > 0) (touch.y / box.height - 0.5f) * 2f else 0f
    val tiltY by animateFloatAsState(nx * 7f, bouncy, label = "tileTiltY")
    val tiltX by animateFloatAsState(-ny * 7f, bouncy, label = "tileTiltX")
    val fade by animateFloatAsState(if (dimmed) 0.62f else 1f, tween(260), label = "tileFade")

    val tint by animateColorAsState(
        if (selected) colors.selectedBg.copy(alpha = 0.62f) else GlassTint.copy(alpha = if (pressed) 0.13f else 0.05f),
        tween(160), label = "tileTint",
    )
    val iconScale by animateFloatAsState(if (selected) 1.4f else 1f, spring(0.35f, Spring.StiffnessLow), label = "tileIcon")
    val labelColor by animateColorAsState(if (selected) Color.White else colors.text, tween(200), label = "tileLabel")
    val glow by animateFloatAsState(if (selected) 1f else 0f, tween(320), label = "tileGlow")
    val pulse by rememberInfiniteTransition(label = "tilePulse").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tilePulseValue",
    )
    val borderBrush: Brush = if (selected) SolidColor(colors.accent.copy(alpha = 0.55f + 0.4f * pulse))
        else GlassRim

    Box(
        modifier = modifier
            .height(118.dp)
            .graphicsLayer {
                scaleX = scale; scaleY = scale
                rotationX = tiltX; rotationY = tiltY
                cameraDistance = 14f * density
                alpha = fade
            }
            .clip(LigtasShapes.tile)
            .border(if (selected) 2.dp else 1.dp, borderBrush, LigtasShapes.tile)
            .onSizeChanged { box = it }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset -> touch = offset; pressed = true; tryAwaitRelease(); pressed = false },
                    onTap = { offset ->
                        touch = offset
                        taps++
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        fire()
                    },
                )
            }
            .semantics {
                this.selected = selected
                role = Role.Button
                onClick { fire(); true }
            },
    ) {
        GlassLayers(Modifier.matchParentSize(), tint)
        // shockwave, glint and breathing edge, drawn above the glass and below the text
        Box(Modifier.matchParentSize().drawBehind {
            val accent = colors.accent
            val w = size.width
            val h = size.height

            val p = shock.value
            if (p < 1f) {
                val c = if (touch.isSpecified) touch else Offset(w / 2f, h / 2f)
                val maxR = hypot(w, h)
                val r = p * maxR
                val a = 1f - p
                drawCircle(
                    Brush.radialGradient(0.5f to Color.Transparent, 1f to accent.copy(alpha = 0.30f * a), center = c, radius = r.coerceAtLeast(1f)),
                    radius = r, center = c,
                )
                drawCircle(accent.copy(alpha = 0.65f * a), radius = r, center = c, style = Stroke(2.8.dp.toPx() * (1f - 0.6f * p)))
                val p2 = ((p - 0.16f) / 0.84f).coerceIn(0f, 1f)
                if (p2 > 0f) {
                    drawCircle(Color.White.copy(alpha = 0.30f * (1f - p2)), radius = p2 * maxR * 0.8f, center = c,
                        style = Stroke(1.6.dp.toPx()))
                }
            }

            val g = glint.value
            if (g < 1f) {
                val x = -0.4f * w + g * 1.8f * w
                val strength = sin(PI.toFloat() * g)
                drawRect(Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.30f * strength), Color.Transparent),
                    start = Offset(x - 0.28f * w, 0f), end = Offset(x + 0.28f * w, h * 0.65f),
                ))
            }

            if (glow > 0f) {
                val level = glow * pulse
                for ((width, alpha) in listOf(12f to 0.06f, 6f to 0.10f, 2.5f to 0.18f)) {
                    val s = width.dp.toPx()
                    drawRoundRect(
                        accent.copy(alpha = alpha * level * 2f),
                        topLeft = Offset(s / 2f, s / 2f), size = Size(w - s, h - s),
                        cornerRadius = CornerRadius(27.dp.toPx()), style = Stroke(s),
                    )
                }
            }
        })
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(LigtasShapes.chip).background(colors.chip.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(spec.icon),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(24.dp).graphicsLayer {
                        scaleX = iconScale; scaleY = iconScale; rotationZ = wiggle.value
                    },
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(spec.label),
                style = MaterialTheme.typography.titleMedium,
                color = labelColor,
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

@Composable
private fun AlertEveryoneButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Ligtas.colors
    // the siren icon breathes, so the button looks ready to be pressed
    val siren by rememberInfiniteTransition(label = "siren").animateFloat(
        1f, 1.12f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sirenScale",
    )
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
            modifier = Modifier.fillMaxSize().glint().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(46.dp).graphicsLayer { scaleX = siren; scaleY = siren }.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
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
