package com.ligtas.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetDialog

// ---------------------------------------------------------------------------------------------
// PROMPT: replaces the plain system dialogs (sign out, send anyway, all-clear, reminders, incoming alert)
// ---------------------------------------------------------------------------------------------

object PromptKind {
    const val PRIMARY = 0   // blue
    const val DANGER = 1    // red
    const val GHOST = 2     // outlined
}

interface PromptListener {
    fun onChoice(index: Int)
}

@Composable
fun PromptSheet(title: String, message: String, labels: List<String>, kinds: List<Int>, listener: PromptListener) {
    val c = Ligtas.colors
    SheetFrame(title, "") {
        if (message.isNotEmpty()) {
            Text(message, color = c.muted, style = LigtasText.body, modifier = Modifier.padding(top = 10.dp).fillMaxWidth())
        }
        Column(Modifier.padding(top = 20.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            labels.forEachIndexed { i, label ->
                val kind = kinds.getOrElse(i) { PromptKind.GHOST }
                val (bg, fg) = when (kind) {
                    PromptKind.DANGER -> c.danger to Color.White
                    PromptKind.PRIMARY -> c.accent to c.onAccent
                    else -> GlassFill to c.text
                }
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(LigtasShapes.pill).background(bg)
                        .then(if (kind == PromptKind.GHOST) Modifier.border(1.dp, c.border, LigtasShapes.pill) else Modifier)
                        .clickable { listener.onChoice(i) }.padding(horizontal = 20.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = fg, style = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp))
                }
            }
        }
    }
}

object PromptBridge {
    @JvmStatic
    fun attach(dialog: BottomSheetDialog, activity: ComponentActivity, title: String, message: String,
               labels: Array<String>, kinds: IntArray, listener: PromptListener) =
        SheetHost.attach(dialog, activity) { PromptSheet(title, message, labels.toList(), kinds.toList(), listener) }
}

// ---------------------------------------------------------------------------------------------
// SETUP SHEET: "Let alerts reach you" (permissions and battery settings)
// ---------------------------------------------------------------------------------------------

class SetupRow(val title: String, val why: String, val ok: Boolean, val button: String, val needsConfirm: Boolean)

class SetupSheetState {
    val rows = mutableStateListOf<SetupRow>()
}

interface SetupSheetListener {
    fun onFix(index: Int)
    fun onConfirmed(index: Int)   // "I have done this" (autostart cannot be detected)
    fun onDone()
}

@Composable
fun SetupSheet(state: SetupSheetState, listener: SetupSheetListener) {
    val c = Ligtas.colors
    val ok = colorResource(R.color.lg_ok)
    val warn = colorResource(R.color.lg_warn)
    val total = state.rows.size
    val ready = state.rows.count { it.ok }
    val allSet = total > 0 && ready == total
    // what still needs doing comes first; what is already on folds into one quiet line each
    val todo = state.rows.withIndex().filter { !it.value.ok }
    val done = state.rows.withIndex().filter { it.value.ok }

    SheetScaffold(
        title = "Let alerts reach you",
        subtitle = "Your phone must let emergency alerts through",
        icon = R.drawable.ic_shield_check,
        footer = { ActionButton("Done", BtnKind.PRIMARY, Modifier.fillMaxWidth()) { listener.onDone() } },
    ) {
        // the one thing to read: is this phone ready?
        GlassCard(
            tint = if (allSet) ok.copy(alpha = 0.08f) else warn.copy(alpha = 0.07f),
            borderColor = if (allSet) ok.copy(alpha = 0.35f) else warn.copy(alpha = 0.35f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RingGauge(if (total == 0) 0f else ready.toFloat() / total, if (allSet) ok else warn, Modifier.size(72.dp), stroke = 8.dp) {
                    Text("$ready/$total", color = c.text, style = inter(16, FontWeight.SemiBold))
                }
                Column(Modifier.weight(1f).padding(start = 16.dp)) {
                    Text(if (allSet) "Ready for emergencies" else "${todo.size} ${if (todo.size == 1) "thing needs" else "things need"} attention",
                        color = c.text, style = inter(16, FontWeight.SemiBold))
                    Text(if (allSet) "Alerts will reach you, even when your phone is silent or locked."
                        else "Alerts could be late or missed until these are on.",
                        color = c.muted, style = inter(12), modifier = Modifier.padding(top = 3.dp))
                }
            }
        }

        if (todo.isNotEmpty()) {
            Section("Turn these on") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    todo.forEach { (i, row) ->
                        GlassCard(padding = 14.dp, borderColor = warn.copy(alpha = 0.3f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(painterResource(R.drawable.ic_alert_triangle), null, tint = warn, modifier = Modifier.size(20.dp))
                                Text(row.title, color = c.text, style = inter(15, FontWeight.SemiBold), modifier = Modifier.weight(1f).padding(start = 10.dp))
                            }
                            Text(row.why, color = c.muted, style = inter(12).copy(lineHeight = 17.sp), modifier = Modifier.padding(top = 6.dp))
                            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                ActionButton(row.button, BtnKind.PRIMARY, Modifier.height(44.dp).widthIn(min = 120.dp)) { listener.onFix(i) }
                                if (row.needsConfirm) {
                                    Text("I have done this", color = c.accent, style = inter(13, FontWeight.SemiBold),
                                        modifier = Modifier.clickable { listener.onConfirmed(i) }.padding(vertical = 8.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (done.isNotEmpty()) {
            Section(if (todo.isEmpty()) "All on" else "Already on", "${done.size}") {
                GlassCard(padding = 4.dp) {
                    done.forEachIndexed { n, (_, row) ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_circle_check), null, tint = ok, modifier = Modifier.size(20.dp))
                            Text(row.title, color = c.text, style = inter(14), modifier = Modifier.weight(1f).padding(start = 12.dp))
                        }
                        if (n < done.lastIndex) Box(Modifier.padding(start = 44.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
                    }
                }
            }
        }
    }
}

object SetupSheetBridge {
    @JvmStatic
    fun attach(dialog: BottomSheetDialog, activity: ComponentActivity, state: SetupSheetState, listener: SetupSheetListener) =
        SheetHost.attach(dialog, activity) { SetupSheet(state, listener) }
}
