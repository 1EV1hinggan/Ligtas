package com.ligtas.app

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Building blocks for the redesigned sheets. The idea behind all of them: show the thing that matters
 * (who gets alerted, how many answered, is the phone ready) first and big; keep the explaining text small and
 * out of the way; keep the buttons always visible at the bottom.
 */

internal fun inter(size: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = InterFamily, fontWeight = weight, fontSize = size.sp)

/** A glass bottom sheet: handle, header, scrolling content, and a footer that never scrolls away. */
@Composable
internal fun SheetScaffold(
    title: String,
    subtitle: String = "",
    icon: Int? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Ligtas.colors
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(sheetFill()).border(1.dp, GlassEdge, shape)) {
        Box(
            Modifier.padding(top = 12.dp).align(Alignment.CenterHorizontally)
                .width(40.dp).height(4.dp).clip(LigtasShapes.pill).background(Color.White.copy(alpha = 0.25f)),
        )
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.chip), contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), null, tint = c.accent, modifier = Modifier.size(24.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = if (icon != null) 14.dp else 0.dp)) {
                Text(title, color = c.text, style = inter(20, FontWeight.SemiBold), maxLines = 2)
                if (subtitle.isNotEmpty()) Text(subtitle, color = c.muted, style = inter(13), modifier = Modifier.padding(top = 2.dp))
            }
        }
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            content()
            Box(Modifier.height(4.dp))
        }
        if (footer != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.08f)))
            Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                footer()
            }
        } else {
            Box(Modifier.height(20.dp))
        }
    }
}

/** A small, quiet caption above a group of things. */
@Composable
internal fun SectionLabel(text: String, trailing: String = "") {
    val c = Ligtas.colors
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), color = c.muted, modifier = Modifier.weight(1f),
            style = inter(11, FontWeight.SemiBold).copy(letterSpacing = 1.2.sp))
        if (trailing.isNotEmpty()) Text(trailing, color = c.muted, style = inter(12))
    }
}

@Composable
internal fun Section(title: String, trailing: String = "", content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(title, trailing)
        content()
    }
}

@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    tint: Color = GlassFill,
    borderColor: Color = Ligtas.colors.border,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(tint).border(1.dp, borderColor, shape).padding(padding), content = content)
}

/** A small rounded label, optionally with an icon. */
@Composable
internal fun InfoChip(text: String, icon: Int? = null, color: Color = Ligtas.colors.muted, bg: Color = GlassFill, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(LigtasShapes.pill).background(bg).border(1.dp, Ligtas.colors.border, LigtasShapes.pill).padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(14.dp).padding(end = 0.dp))
        Text(text, color = color, style = inter(12, FontWeight.Medium), maxLines = 1, modifier = Modifier.padding(start = if (icon != null) 6.dp else 0.dp))
    }
}

internal enum class BtnKind { PRIMARY, GHOST, DANGER, OK, WARN }

@Composable
internal fun ActionButton(text: String, kind: BtnKind, modifier: Modifier = Modifier, enabled: Boolean = true, shine: Boolean = false, onClick: () -> Unit) {
    val c = Ligtas.colors
    val ok = colorResource(R.color.lg_ok)
    val warn = colorResource(R.color.lg_warn)
    val bg: Color; val fg: Color; val line: Color?
    when (kind) {
        BtnKind.PRIMARY -> { bg = c.accent; fg = c.onAccent; line = null }
        BtnKind.DANGER -> { bg = c.danger; fg = Color.White; line = null }
        BtnKind.OK -> { bg = colorResource(R.color.lg_ok_bg); fg = ok; line = ok.copy(alpha = 0.35f) }
        BtnKind.WARN -> { bg = GlassFill; fg = warn; line = warn.copy(alpha = 0.35f) }
        BtnKind.GHOST -> { bg = GlassFill; fg = c.text; line = c.border }
    }
    Box(
        modifier.height(52.dp).bounceClick(enabled = enabled, onClick = onClick)
            .clip(LigtasShapes.pill).background(bg)
            .then(if (shine) Modifier.glint() else Modifier)
            .then(if (line != null) Modifier.border(1.dp, line, LigtasShapes.pill) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg.copy(alpha = if (enabled) 1f else 0.5f), style = inter(15, FontWeight.SemiBold), maxLines = 1)
    }
}

/** Two to four choices in one pill, with a thumb that slides to the chosen one. */
@Composable
internal fun SegmentedControl(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val c = Ligtas.colors
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(50.dp).clip(LigtasShapes.pill).background(GlassFill)
            .border(1.dp, c.border, LigtasShapes.pill).padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val x by animateDpAsState(segment * selected.coerceAtLeast(0), spring(0.8f, Spring.StiffnessMediumLow), label = "segment")
        Box(Modifier.offset(x = x).width(segment).fillMaxHeight().clip(LigtasShapes.pill).background(c.accent))
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton,
                    ) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = if (i == selected) c.onAccent else c.text, style = inter(13, FontWeight.SemiBold), maxLines = 1)
                }
            }
        }
    }
}

/** A tappable card for one option out of several: icon, title, small explanation, and a check when chosen. */
@Composable
internal fun ChoiceCard(
    selected: Boolean,
    title: String,
    subtitle: String = "",
    icon: Int? = null,
    iconTint: Color = Ligtas.colors.accent,
    onClick: () -> Unit,
) {
    val c = Ligtas.colors
    val shape = RoundedCornerShape(18.dp)
    val line = if (selected) c.selectedBorder else c.border
    Row(
        Modifier.fillMaxWidth().bounceClick(onClick = onClick).semantics { this.selected = selected }
            .clip(shape).background(if (selected) c.selectedBg.copy(alpha = 0.72f) else GlassFill)
            .border(if (selected) 1.5.dp else 1.dp, line, shape).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(c.chip), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = iconTint, modifier = Modifier.size(21.dp))
            }
        }
        Column(Modifier.weight(1f).padding(start = if (icon != null) 12.dp else 0.dp, end = 10.dp)) {
            Text(title, color = c.text, style = inter(14, FontWeight.Medium))
            if (subtitle.isNotEmpty()) Text(subtitle, color = c.muted, style = inter(12), modifier = Modifier.padding(top = 2.dp))
        }
        val fill by animateFloatAsState(if (selected) 1f else 0f, tween(180), label = "choiceCheck")
        Box(Modifier.size(22.dp).clip(CircleShape).border(1.5.dp, if (selected) c.accent else c.muted.copy(alpha = 0.6f), CircleShape), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp * fill).clip(CircleShape).background(c.accent))
        }
    }
}

/** A circular gauge that fills to [progress] (0..1), with anything you like in the middle. */
@Composable
internal fun RingGauge(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier.size(104.dp),
    stroke: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700, easing = FastOutSlowInEasing), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val s = stroke.toPx()
            val topLeft = Offset(s / 2f, s / 2f)
            val arc = Size(size.width - s, size.height - s)
            drawArc(Color.White.copy(alpha = 0.08f), 0f, 360f, false, topLeft, arc, style = Stroke(s))
            if (p > 0f) drawArc(color, -90f, 360f * p, false, topLeft, arc, style = Stroke(s, cap = StrokeCap.Round))
        }
        content()
    }
}

/** "Delivered  1 / 2" with a thin bar under it. */
@Composable
internal fun StatBar(label: String, value: Int, total: Int, color: Color) {
    val c = Ligtas.colors
    val fraction by animateFloatAsState(if (total > 0) (value.toFloat() / total).coerceIn(0f, 1f) else 0f, tween(600, easing = FastOutSlowInEasing), label = "bar")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(label, color = c.muted, style = inter(12), modifier = Modifier.weight(1f))
            Text("$value", color = c.text, style = inter(15, FontWeight.SemiBold))
            Text(" / $total", color = c.muted, style = inter(12), modifier = Modifier.padding(bottom = 1.dp))
        }
        Box(Modifier.padding(top = 5.dp).fillMaxWidth().height(6.dp).clip(LigtasShapes.pill).background(Color.White.copy(alpha = 0.08f))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(LigtasShapes.pill).background(color))
        }
    }
}

/** A round badge with a person's first letter. */
@Composable
internal fun Avatar(name: String, size: Dp = 36.dp) {
    val c = Ligtas.colors
    Box(Modifier.size(size).clip(CircleShape).background(c.chip), contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "?", color = c.accent, style = inter(14, FontWeight.SemiBold))
    }
}

// ---------------------------------------------------------------------------------------------
// Reading the summary text that the Java side already produces (so the Java code stays untouched)
// ---------------------------------------------------------------------------------------------

/** The slide-to-send card's preview text, split into the parts worth showing separately. */
internal class ConfirmFacts(
    val message: String,
    val to: String,
    val phones: Int,           // -1 while still counting
    val counting: Boolean,
    val notSignedIn: Int,
    val nobody: Boolean,
    val skipping: List<String>,
    val note: String,
) {
    companion object {
        fun parse(preview: String): ConfirmFacts {
            val cut = preview.lastIndexOf("\n\nTo: ")
            val message = (if (cut >= 0) preview.substring(0, cut) else preview).trim()
            val summary = if (cut >= 0) preview.substring(cut + 2) else ""
            var to = ""; var phones = -1; var counting = false; var notSignedIn = 0
            var nobody = false; var skipping = emptyList<String>(); val note = StringBuilder()
            for (line in summary.lines()) {
                when {
                    line.startsWith("To: ") -> to = line.removePrefix("To: ")
                    line.contains("(counting") -> counting = true
                    line.startsWith("About ") -> {
                        Regex("About (\\d+) phone").find(line)?.let { phones = it.groupValues[1].toInt() }
                        Regex("\\((\\d+) in this group not signed in\\)").find(line)?.let { notSignedIn = it.groupValues[1].toInt() }
                    }
                    line.startsWith("WARNING") -> nobody = true
                    line.startsWith("Skipping online this week: ") ->
                        skipping = line.removePrefix("Skipping online this week: ").split(", ").filter { it.isNotBlank() }
                    line.isNotBlank() -> { if (note.isNotEmpty()) note.append(' '); note.append(line.trim()) }
                }
            }
            return ConfirmFacts(message, to, phones, counting, notSignedIn, nobody, skipping, note.toString())
        }
    }
}

/** The Edit sheet's hint text, split into the reach number, the week, the groups that are online, and leftovers. */
internal class EditFacts(
    val loading: Boolean,
    val reach: Int,            // -1 until counted
    val estimated: Boolean,
    val nobody: Boolean,
    val week: String,
    val online: List<String>,
    val setup: String,
    val notes: List<String>,
) {
    companion object {
        fun parse(hint: String): EditFacts {
            var loading = false; var reach = -1; var estimated = false; var nobody = false
            var week = ""; var online = emptyList<String>(); var setup = ""; val notes = mutableListOf<String>()
            for (line in hint.lines()) {
                when {
                    line.isBlank() -> {}
                    line.startsWith("Loading the campus setup") -> loading = true
                    line.startsWith("Counting phones") -> {}
                    line.startsWith("Week ") -> {
                        val m = Regex("^Week (\\d+) \\((ODD|EVEN)\\)\\. Online now: (.*)$").find(line)
                        if (m != null) {
                            week = "Week ${m.groupValues[1]} · ${m.groupValues[2]}"
                            val groups = m.groupValues[3].trim()
                            online = if (groups == "none") emptyList() else groups.split(", ").filter { it.isNotBlank() }
                        } else notes.add(line)
                    }
                    line.startsWith("Reaches about") || line.startsWith("Reaches at most about") -> {
                        estimated = line.contains("at most")
                        nobody = line.contains("NOBODY")
                        Regex("(\\d+) phone").find(line)?.let { reach = it.groupValues[1].toInt() }
                    }
                    line.startsWith("Using: ") -> setup = line.removePrefix("Using: ").replace(Regex("\\s\\([^)]*\\)"), "")
                    else -> notes.add(line)
                }
            }
            return EditFacts(loading, reach, estimated, nobody, week, online, setup, notes)
        }
    }
}
