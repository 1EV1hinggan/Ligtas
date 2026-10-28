package com.ligtas.app

import androidx.activity.ComponentActivity
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.delay

/** Everything the Alert status sheet shows. Java keeps the live Firestore watchers and just pushes numbers in here. */
class AlertStatusState {
    var title by mutableStateOf("Alert status")
    var message by mutableStateOf("")
    var meta by mutableStateOf("")
    var expected by mutableIntStateOf(0)
    var delivered by mutableIntStateOf(0)
    var acknowledged by mutableIntStateOf(0)
    var average by mutableStateOf("")
    var pending by mutableStateOf("Loading...")
    var rooms by mutableStateOf("No room reports yet.")
    /** The all-clear / false-alarm buttons only show while the alert is still active. */
    var canResolve by mutableStateOf(false)

    // Re-alerting people who have not answered (filled by EscalationFeed / StatusController)
    /** False for big audiences: only counts are kept, so people cannot be re-alerted one by one. */
    var escNamed by mutableStateOf(true)
    var escAutoMin by mutableIntStateOf(0)          // minutes until the automatic re-alert, 0 = off
    var escRounds by mutableIntStateOf(0)           // re-alerts sent so far
    var escLastAt by mutableLongStateOf(0L)
    var escLastCount by mutableIntStateOf(0)
    var escSentAt by mutableLongStateOf(0L)
    var escBusy by mutableStateOf(false)
    var escNote by mutableStateOf("")
    var escNoteError by mutableStateOf(false)
}

interface AlertStatusListener {
    fun onAllClear()
    fun onFalseAlarm()
    fun onClose()
    fun onEscalate()
    fun onAutoChange(minutes: Int)
    fun onAutoDue()
}

object AlertStatusBridge {
    @JvmStatic
    fun attach(dialog: BottomSheetDialog, activity: ComponentActivity, state: AlertStatusState, listener: AlertStatusListener) {
        val host = ComposeView(activity)
        host.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        dialog.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(activity)
            it.setViewTreeViewModelStoreOwner(activity)
            it.setViewTreeSavedStateRegistryOwner(activity)
        }
        host.setContent { LigtasTheme { AlertStatusSheet(state, listener) } }
        SheetGlass.apply(dialog)
        dialog.setContentView(host)
    }
}

/** "Sent October 16 at 10:36 PM by name" + "To: ..." + "Still active" / "Ended: all clear at ..." */
private class StatusFacts(val when_: String, val by: String, val to: String, val ended: String, val endedKind: Int) {
    companion object {
        fun parse(meta: String): StatusFacts {
            var whenText = ""; var by = ""; var to = ""; var ended = ""; var kind = 0
            for (line in meta.lines()) {
                when {
                    line.startsWith("Sent") -> {
                        val i = line.lastIndexOf(" by ")
                        if (line.startsWith("Sent by ")) by = line.removePrefix("Sent by ")
                        else if (i >= 0) { whenText = line.substring(0, i); by = line.substring(i + 4) } else whenText = line
                    }
                    line.startsWith("To: ") -> to = line.removePrefix("To: ")
                    line.startsWith("Ended") -> {
                        ended = line.removePrefix("Ended: ")
                        kind = if (line.contains("false alarm", true)) 2 else 1
                    }
                }
            }
            return StatusFacts(whenText, by, to, ended, kind)
        }
    }
}

@Composable
fun AlertStatusSheet(state: AlertStatusState, listener: AlertStatusListener) {
    val c = Ligtas.colors
    val ok = colorResource(R.color.lg_ok)
    val warn = colorResource(R.color.lg_warn)
    val facts = StatusFacts.parse(state.meta)
    val ended = facts.endedKind != 0 || !state.canResolve && state.meta.isNotEmpty() && facts.ended.isNotEmpty()

    SheetScaffold(
        title = state.title.ifEmpty { "Alert status" },
        icon = R.drawable.ic_siren,
        footer = {
            if (state.canResolve) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton("Send all-clear", BtnKind.OK, Modifier.weight(1f)) { listener.onAllClear() }
                    ActionButton("False alarm", BtnKind.WARN, Modifier.weight(1f)) { listener.onFalseAlarm() }
                }
            }
            ActionButton("Close", BtnKind.GHOST, Modifier.fillMaxWidth()) { listener.onClose() }
        },
    ) {
        // state + who/when, then what was said
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatePill(
                    text = when {
                        facts.endedKind == 1 -> "ENDED \u00B7 ALL CLEAR"
                        facts.endedKind == 2 -> "ENDED \u00B7 FALSE ALARM"
                        else -> "ACTIVE"
                    },
                    color = when (facts.endedKind) { 1 -> ok; 2 -> warn; else -> c.dangerText },
                    pulsing = !ended && state.canResolve,
                )
                if (facts.ended.contains(" at ")) Text(facts.ended.substringAfter(" at "), color = c.muted, style = inter(12))
            }
            if (facts.when_.isNotEmpty() || facts.by.isNotEmpty()) {
                MetaLine(R.drawable.ic_timer, listOf(facts.when_.removePrefix("Sent ").ifEmpty { "" }, if (facts.by.isNotEmpty()) "by ${facts.by}" else "").filter { it.isNotEmpty() }.joinToString("  \u00B7  "))
            }
            if (facts.to.isNotEmpty()) MetaLine(R.drawable.ic_school, facts.to)
            if (state.message.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 2.dp).height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(LigtasShapes.pill).background(c.danger.copy(alpha = 0.7f)))
                    Text(state.message, color = c.muted, style = inter(13).copy(lineHeight = 18.sp), modifier = Modifier.padding(start = 12.dp))
                }
            }
        }

        // the answer rate, big
        GlassCard {
            if (state.expected == 0) {
                Text("No registered recipients yet.", color = c.muted, style = inter(14))
            } else {
                val pct = (state.acknowledged * 100f / state.expected).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RingGauge(state.acknowledged.toFloat() / state.expected, ok, Modifier.size(104.dp)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$pct%", color = c.text, style = inter(24, FontWeight.SemiBold))
                            Text("answered", color = c.muted, style = inter(11))
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        StatBar("Delivered", state.delivered, state.expected, c.accent)
                        StatBar("Acknowledged", state.acknowledged, state.expected, ok)
                    }
                }
                if (state.average.isNotEmpty()) {
                    Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.07f)))
                    Text(
                        state.average.removePrefix("Average response time: ").let { if (it.startsWith("waiting")) "Average response: $it" else "Average response  $it" },
                        color = c.muted, style = inter(12), modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }

        EscalationSection(state, listener)
        PendingSection(state)
        RoomsSection(state)
    }
}

@Composable
private fun StatePill(text: String, color: Color, pulsing: Boolean) {
    val pulse by rememberInfiniteTransition(label = "statePulse").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "statePulseValue",
    )
    Row(
        Modifier.clip(LigtasShapes.pill).background(color.copy(alpha = 0.14f)).border(1.dp, color.copy(alpha = 0.4f), LigtasShapes.pill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color.copy(alpha = if (pulsing) pulse else 1f)))
        Text(text, color = color, style = inter(12, FontWeight.SemiBold).copy(letterSpacing = 0.8.sp), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun MetaLine(icon: Int, text: String) {
    val c = Ligtas.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(icon), null, tint = c.muted, modifier = Modifier.size(16.dp))
        Text(text, color = c.text, style = inter(13, FontWeight.Medium), modifier = Modifier.padding(start = 8.dp))
    }
}

/** Who has not answered yet, one person per row. */
@Composable
private fun PendingSection(state: AlertStatusState) {
    val c = Ligtas.colors
    val ok = colorResource(R.color.lg_ok)
    val lines = state.pending.lines().filter { it.isNotBlank() }
    val message = lines.size == 1 && listOf("Everyone", "No registered", "Loading", "Live status").any { lines[0].startsWith(it) }
    Section("Still waiting for", if (!message && state.expected > 0) "${lines.size}" else "") {
        if (message) {
            val done = lines[0].startsWith("Everyone")
            GlassCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (done) Icon(painterResource(R.drawable.ic_circle_check), null, tint = ok, modifier = Modifier.size(20.dp))
                    Text(lines[0], color = if (done) ok else c.muted, style = inter(13), modifier = Modifier.padding(start = if (done) 10.dp else 0.dp))
                }
            }
        } else {
            GlassCard(padding = 4.dp) {
                lines.forEachIndexed { i, line ->
                    val name = line.substringBefore("  (").trim()
                    val tag = if (line.contains("  (")) line.substringAfter("  (").removeSuffix(")") else ""
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(name, 34.dp)
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(name, color = c.text, style = inter(14, FontWeight.Medium), maxLines = 1)
                        }
                        if (tag.isNotEmpty()) InfoChip(tag, color = colorResource(R.color.lg_warn))
                    }
                    if (i < lines.lastIndex) Box(Modifier.padding(start = 56.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                }
            }
        }
    }
}

/** Room reports from teachers: a summary line, then one row per room. */
@Composable
private fun RoomsSection(state: AlertStatusState) {
    val c = Ligtas.colors
    val empty = state.rooms.startsWith("No room reports") || state.rooms.startsWith("Room reports unavailable")
    val blocks = state.rooms.split("\n\n").filter { it.isNotBlank() }
    Section("Rooms reported by teachers") {
        if (empty || blocks.isEmpty()) {
            GlassCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_school), null, tint = c.muted, modifier = Modifier.size(20.dp))
                    Text(state.rooms.ifEmpty { "No room reports yet." }, color = c.muted, style = inter(13), modifier = Modifier.padding(start = 10.dp))
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(blocks[0], color = c.text, style = inter(13, FontWeight.SemiBold))
                blocks.drop(1).forEach { report ->
                    GlassCard(padding = 12.dp) { Text(report, color = c.text, style = inter(13)) }
                }
            }
        }
    }
}

private const val MAX_AUTO_ROUNDS = 3
private val AUTO_CHOICES = intArrayOf(0, 2, 5, 10)

/**
 * Re-alert people who have not answered: a countdown to the automatic re-alert, a timer you can change,
 * the manual button, and what has been sent so far. Hidden once an alert has ended (unless it was re-alerted).
 */
@Composable
private fun EscalationSection(state: AlertStatusState, listener: AlertStatusListener) {
    val c = Ligtas.colors
    val ok = colorResource(R.color.lg_ok)
    val warn = colorResource(R.color.lg_warn)
    val active = state.canResolve
    if (!active && state.escRounds == 0) return
    val waiting = (state.expected - state.acknowledged).coerceAtLeast(0)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { while (active) { delay(1000); now = System.currentTimeMillis() } }

    val auto = state.escAutoMin
    val base = if (state.escRounds == 0) state.escSentAt else state.escLastAt
    val due = if (active && state.escNamed && auto > 0 && state.escRounds < MAX_AUTO_ROUNDS && base > 0) base + auto * 60_000L else 0L
    // This phone also fires the automatic round when its timer runs out while the sheet is open
    // (the server checks it really is due, and a server-side timer may have sent it already).
    LaunchedEffect(due, state.escRounds) {
        if (due > 0) {
            delay((due + 8_000L - System.currentTimeMillis()).coerceAtLeast(0))
            listener.onAutoDue()
        }
    }
    val remaining = (due - now).coerceAtLeast(0)
    val overdue = due > 0 && now - due > 90_000

    Section("Re-alert non-responders", if (state.escRounds > 0) "${state.escRounds} sent" else "") {
        GlassCard(tint = if (waiting > 0 && active) warn.copy(alpha = 0.06f) else GlassFill, borderColor = if (waiting > 0 && active) warn.copy(alpha = 0.30f) else c.border) {
            when {
                !state.escNamed -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_users), null, tint = c.muted, modifier = Modifier.size(20.dp))
                    Text(
                        "This audience is too big to list by name, so people cannot be re-alerted one by one.",
                        color = c.muted, style = inter(13).copy(lineHeight = 18.sp), modifier = Modifier.padding(start = 10.dp),
                    )
                }
                active && waiting == 0 && state.expected > 0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_circle_check), null, tint = ok, modifier = Modifier.size(20.dp))
                    Text("Everyone has answered. Nobody to re-alert.", color = ok, style = inter(13), modifier = Modifier.padding(start = 10.dp))
                }
                active -> {
                    // countdown ring + the people count
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val progress = if (due > 0 && auto > 0) 1f - remaining / (auto * 60_000f) else 0f
                        RingGauge(progress, if (overdue) warn else c.accent, Modifier.size(84.dp), stroke = 8.dp) {
                            if (due > 0) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    val sec = (remaining / 1000).toInt()
                                    Text("%d:%02d".format(sec / 60, sec % 60), color = c.text, style = inter(18, FontWeight.SemiBold))
                                    Text(if (overdue) "due" else "next", color = c.muted, style = inter(10))
                                }
                            } else {
                                Icon(painterResource(R.drawable.ic_timer), null, tint = c.muted, modifier = Modifier.size(26.dp))
                            }
                        }
                        Column(Modifier.weight(1f).padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "$waiting ${if (waiting == 1) "person has" else "people have"} not answered",
                                color = c.text, style = inter(15, FontWeight.SemiBold),
                            )
                            Text(
                                when {
                                    state.escRounds >= MAX_AUTO_ROUNDS && auto > 0 -> "Automatic re-alerts are used up. You can still send one by hand."
                                    auto == 0 -> "Automatic re-alert is off."
                                    overdue -> "The automatic re-alert is late. Tap the button below."
                                    else -> "Re-alerts them automatically when the timer reaches zero."
                                },
                                color = if (overdue) warn else c.muted, style = inter(12).copy(lineHeight = 16.sp),
                            )
                        }
                    }
                    Text("Automatic re-alert after", color = c.muted, style = inter(11, FontWeight.SemiBold).copy(letterSpacing = 1.0.sp), modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                    SegmentedControl(
                        AUTO_CHOICES.map { if (it == 0) "Off" else "$it min" },
                        AUTO_CHOICES.indexOf(auto).let { if (it < 0) 0 else it },
                    ) { listener.onAutoChange(AUTO_CHOICES[it]) }
                    ActionButton(
                        if (state.escBusy) "Sending..." else "Re-alert $waiting ${if (waiting == 1) "person" else "people"} now",
                        BtnKind.WARN, Modifier.fillMaxWidth().padding(top = 14.dp), enabled = !state.escBusy && waiting > 0,
                    ) { listener.onEscalate() }
                }
                else -> Text("This alert has ended.", color = c.muted, style = inter(13))
            }

            if (state.escNote.isNotEmpty()) {
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (state.escNoteError) warn else ok))
                    Text(state.escNote, color = if (state.escNoteError) warn else ok, style = inter(12).copy(lineHeight = 16.sp), modifier = Modifier.padding(start = 8.dp))
                }
            }
            if (state.escRounds > 0 && state.escLastAt > 0) {
                val ago = ((now - state.escLastAt) / 60_000).coerceAtLeast(0)
                Text(
                    "Last re-alert ${if (ago == 0L) "just now" else "$ago min ago"}  \u00B7  ${state.escLastCount} ${if (state.escLastCount == 1) "person" else "people"}",
                    color = c.muted, style = inter(12), modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
