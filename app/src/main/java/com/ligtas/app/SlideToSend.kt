package com.ligtas.app

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.key

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val TrackHeight = 68.dp
private val TrackInset = 4.dp
private val ThumbSize = 60.dp

/**
 * The "slide to send" control.
 *
 * Rules that make an accidental send very hard:
 *  1. Only the round THUMB can be dragged. The track itself has no click or drag handler,
 *     so tapping anywhere on the track (including the far end) does nothing.
 *  2. The thumb always starts at the left. It only follows the finger; it never jumps.
 *  3. The alert is sent only if the finger is lifted while the thumb is at the very end.
 *     Let go earlier and the thumb springs back to the start.
 */
@Composable
fun SlideToSend(
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Slide to send alert",
) {
    val colors = Ligtas.colors
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var offset by remember { mutableFloatStateOf(0f) }   // thumb position in px, 0 = start
    var done by remember { mutableStateOf(false) }       // true once sent: blocks a second send
    var returnJob by remember { mutableStateOf<Job?>(null) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(TrackHeight)
            .clip(LigtasShapes.pill)
            .background(colors.dangerBg)
            .border(BorderStroke(1.dp, colors.dangerLine), LigtasShapes.pill)
            .semantics {
                contentDescription = label
                customActions = listOf(
                    CustomAccessibilityAction("Send alert now") {
                        if (!done) { done = true; onConfirmed() }
                        true
                    }
                )
            },
    ) {
        val maxOffset = with(density) { (maxWidth - TrackInset * 2 - ThumbSize).toPx() }.coerceAtLeast(1f)
        val progress = (offset / maxOffset).coerceIn(0f, 1f)
        val armed by remember { derivedStateOf { offset >= maxOffset - 1f } }

        // buzz once when the thumb reaches the end, so the user feels "now let go"
        LaunchedEffect(armed) { if (armed && !done) haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

        // red fill that grows behind the thumb
        Box(
            Modifier
                .padding(TrackInset)
                .height(ThumbSize)
                .width(ThumbSize + with(density) { offset.toDp() })
                .clip(LigtasShapes.pill)
                .background(colors.danger.copy(alpha = 0.28f))
        )

        // label: fades as the thumb moves, changes to "Release to send" at the end
        Text(
            text = if (armed) "Release to send" else label,
            color = colors.text,
            fontFamily = InterFamily,
            fontSize = 16.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(start = 56.dp, end = 40.dp)
                .alpha(if (armed) 1f else (1f - progress * 1.4f).coerceIn(0f, 1f)),
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevrons_right),
            contentDescription = null,
            tint = colors.dangerText,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 18.dp)
                .size(22.dp)
                .alpha((1f - progress * 1.6f).coerceIn(0f, 1f)),
        )

        // the thumb: the ONLY part that reacts to touch
        val dragState = rememberDraggableState { delta ->
            if (!done) offset = (offset + delta).coerceIn(0f, maxOffset)
        }
        Box(
            modifier = Modifier
                .padding(TrackInset)
                .offset { IntOffset(offset.roundToInt(), 0) }
                .size(ThumbSize)
                .clip(CircleShape)
                .background(colors.danger)
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = !done,
                    onDragStarted = { returnJob?.cancel() },
                    onDragStopped = {
                        if (!done && offset >= maxOffset - 1f) {
                            done = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirmed()
                        } else if (!done) {
                            // not at the end: spring back to the start, nothing is sent
                            returnJob = scope.launch {
                                animate(
                                    initialValue = offset,
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                ) { value, _ -> offset = value }
                            }
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_right),
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Full-screen "Send this alert?" card: dark scrim, the alert preview, the slider and Cancel.
 * [session] changes every time the card opens, which resets the slider to the start.
 */
@Composable
fun ConfirmAlertOverlay(
    session: Int,
    iconRes: Int,
    title: String,
    preview: String,
    onConfirmed: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = Ligtas.colors
    val enter = remember(session) { Animatable(0f) }
    LaunchedEffect(session) { enter.animateTo(1f, tween(180)) }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg.copy(alpha = 0.70f * enter.value))
            // swallow taps on the scrim so nothing underneath is touched
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            // frosted glass over the moving background
            GlassPanel(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .alpha(enter.value)
                    .scale(0.94f + 0.06f * enter.value)
                    .pointerInput(Unit) { detectTapGestures { } },
                shape = RoundedCornerShape(28.dp),
                tint = colors.surface.copy(alpha = 0.55f),
            ) {
                val facts = remember(preview) { ConfirmFacts.parse(preview) }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // what is about to happen
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(colors.dangerBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(iconRes), null, tint = colors.dangerText, modifier = Modifier.size(28.dp))
                        }
                        Column(Modifier.padding(start = 14.dp)) {
                            Text("CONFIRM ALERT", color = colors.dangerText, style = inter(11, FontWeight.SemiBold).copy(letterSpacing = 1.2.sp))
                            Text(title.ifBlank { stringResource(R.string.slide_title) }, color = colors.text, style = inter(20, FontWeight.SemiBold))
                        }
                    }
                    // the message and the reach scroll; the slider below is always visible
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            Box(Modifier.width(3.dp).fillMaxHeight().clip(LigtasShapes.pill).background(colors.danger.copy(alpha = 0.75f)))
                            Text(facts.message, color = colors.text, style = inter(14).copy(lineHeight = 20.sp), modifier = Modifier.padding(start = 12.dp))
                        }
                        ReachSummary(facts)
                    }
                    key(session) { SlideToSend(onConfirmed = onConfirmed) }
                    ActionButton(stringResource(R.string.slide_cancel), BtnKind.GHOST, Modifier.fillMaxWidth()) { onCancel() }
                }
            }
        }
    }
}

/** Who will be alerted: one big number, where it goes, and anything that needs a second look. */
@Composable
private fun ReachSummary(f: ConfirmFacts) {
    val colors = Ligtas.colors
    val warn = colorResource(R.color.lg_warn)
    val bad = f.nobody || (!f.counting && f.phones == 0)
    var expanded by remember { mutableStateOf(false) }
    GlassCard(
        padding = 16.dp,
        tint = if (bad) colors.dangerBg.copy(alpha = 0.55f) else GlassFill,
        borderColor = if (bad) colors.dangerBorder else colors.border,
        modifier = Modifier.animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.widthIn(min = 52.dp).padding(end = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (f.phones < 0) CircularProgressIndicator(color = colors.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                else Text("${f.phones}", color = if (bad) colors.dangerText else colors.text, style = inter(38, FontWeight.SemiBold))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        f.phones < 0 -> "Counting phones\u2026"
                        f.phones == 1 -> "phone will be alerted"
                        else -> "phones will be alerted"
                    },
                    color = colors.text, style = inter(15, FontWeight.Medium),
                )
                if (f.notSignedIn > 0) {
                    Text("${f.notSignedIn} more in this group ${if (f.notSignedIn == 1) "has" else "have"} not signed in", color = colors.muted,
                        style = inter(12), modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
        if (bad) {
            Text("Nobody would receive this alert. Go back, edit it, and turn off \u201COnly people on campus\u201D.",
                color = colors.dangerText, style = inter(12), modifier = Modifier.padding(top = 10.dp))
        }
        Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (f.to.isNotEmpty()) InfoChip(f.to, R.drawable.ic_school)
            if (f.skipping.isNotEmpty()) {
                InfoChip(
                    "${f.skipping.size} online ${if (f.skipping.size == 1) "group" else "groups"} skipped", R.drawable.ic_users,
                    color = warn, modifier = Modifier.clickable { expanded = !expanded },
                )
            }
        }
        if (expanded && f.skipping.isNotEmpty()) {
            Text("Online this week: " + f.skipping.joinToString("  \u00B7  "), color = colors.muted, style = inter(12), modifier = Modifier.padding(top = 10.dp))
        }
        if (f.note.isNotEmpty()) Text(f.note, color = colors.muted, style = inter(12), modifier = Modifier.padding(top = 8.dp))
    }
}
