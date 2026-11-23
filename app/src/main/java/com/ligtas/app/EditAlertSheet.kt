package com.ligtas.app

import androidx.activity.ComponentActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * Everything the Edit alert sheet shows. Java (MainActivity) fills this in and keeps all the real logic
 * (campus setup, recipient counting, preview text); Compose only draws it and reports taps.
 */
class EditAlertSheetState {
    var title by mutableStateOf("")
    var noteRequired by mutableStateOf(false)

    val areaOptions = mutableStateListOf<String>()
    var areaIndex by mutableIntStateOf(0)
    val collegeOptions = mutableStateListOf<String>()
    var collegeIndex by mutableIntStateOf(0)
    val programOptions = mutableStateListOf<String>()
    var programIndex by mutableIntStateOf(0)
    val yearOptions = mutableStateListOf<String>()
    var yearIndex by mutableIntStateOf(0)

    var onCampus by mutableStateOf(true)
    var onCampusVisible by mutableStateOf(true)
    var hint by mutableStateOf("")

    val instructions = mutableStateListOf<String>()
    var variant by mutableIntStateOf(0)

    var note by mutableStateOf("")
    var noteError by mutableStateOf("")
    var preview by mutableStateOf("")

    /** What the spinners meant in the old code: "" for the first entry ("All colleges", "All programs"). */
    fun area(): String = areaOptions.getOrElse(areaIndex) { "" }
    fun college(): String = if (collegeIndex <= 0) "" else collegeOptions.getOrElse(collegeIndex) { "" }
    fun program(): String = if (programIndex <= 0) "" else programOptions.getOrElse(programIndex) { "" }
    fun yearLevel(): Int = yearIndex.coerceAtLeast(0)
}

interface EditAlertSheetListener {
    fun onCollegeChanged()
    fun onAudienceChanged()   // area, program, year or the on-campus box
    fun onContentChanged()    // instruction or note
    fun onConfirm()
    fun onCancel()
}

object EditAlertSheetBridge {
    /** Shows [state] inside the bottom sheet [dialog] using Compose. */
    @JvmStatic
    fun attach(dialog: BottomSheetDialog, activity: ComponentActivity, state: EditAlertSheetState, listener: EditAlertSheetListener) {
        val host = ComposeView(activity)
        host.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        // a dialog window is a separate view tree: tell Compose which lifecycle it belongs to
        dialog.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(activity)
            it.setViewTreeViewModelStoreOwner(activity)
            it.setViewTreeSavedStateRegistryOwner(activity)
        }
        host.setContent { LigtasTheme { EditAlertSheet(state, listener) } }
        SheetGlass.apply(dialog)
        dialog.setContentView(host)
    }
}

@Composable
fun EditAlertSheet(state: EditAlertSheetState, listener: EditAlertSheetListener) {
    val c = Ligtas.colors
    val facts = EditFacts.parse(state.hint)
    val kind = state.title.substringAfter(": ", "")

    SheetScaffold(
        title = "Edit alert",
        subtitle = kind,
        icon = R.drawable.ic_pencil,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Cancel", BtnKind.GHOST, Modifier.weight(1f)) { listener.onCancel() }
                ActionButton("Save changes", BtnKind.PRIMARY, Modifier.weight(1.3f)) { listener.onConfirm() }
            }
        },
    ) {
        ReachCard(facts)

        Section("Who gets it") {
            GlassCard(padding = 12.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LgDropdown("Affected area", state.areaOptions, state.areaIndex) { state.areaIndex = it; listener.onAudienceChanged() }
                    LgDropdown("College", state.collegeOptions, state.collegeIndex) { state.collegeIndex = it; listener.onCollegeChanged() }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            LgDropdown("Program", state.programOptions, state.programIndex) { state.programIndex = it; listener.onAudienceChanged() }
                        }
                        Box(Modifier.weight(1f)) {
                            LgDropdown("Year level", state.yearOptions, state.yearIndex) { state.yearIndex = it; listener.onAudienceChanged() }
                        }
                    }
                    if (state.onCampusVisible) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp)
                                .toggleable(value = state.onCampus, role = Role.Switch, onValueChange = { state.onCampus = it; listener.onAudienceChanged() }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Only people on campus", color = c.text, style = inter(14, FontWeight.Medium))
                                Text("Skip groups that are online this week", color = c.muted, style = inter(12))
                            }
                            Switch(
                                checked = state.onCampus, onCheckedChange = null,
                                colors = SwitchDefaults.colors(checkedTrackColor = c.accent, checkedThumbColor = c.onAccent,
                                    uncheckedTrackColor = c.field, uncheckedThumbColor = c.muted, uncheckedBorderColor = c.border),
                            )
                        }
                    }
                }
            }
        }

        Section("What to tell them") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.instructions.forEachIndexed { i, text ->
                    ChoiceCard(selected = state.variant == i, title = text) { state.variant = i; listener.onContentChanged() }
                }
                NoteField(state, listener)
            }
        }

        Section("Preview", "exactly what phones show") {
            NotificationPreview(state.preview)
        }
    }
}

/** The number that matters most: how many phones this will reach, with the reasons underneath. */
@Composable
private fun ReachCard(facts: EditFacts) {
    val c = Ligtas.colors
    val bad = facts.nobody
    val tone = if (bad) c.dangerText else c.accent
    var expanded by remember { mutableStateOf(false) }
    GlassCard(
        padding = 16.dp,
        tint = if (bad) c.dangerBg.copy(alpha = 0.55f) else GlassFill,
        borderColor = if (bad) c.dangerBorder else c.border,
        modifier = Modifier.animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.widthIn(min = 56.dp).padding(end = 14.dp), contentAlignment = Alignment.CenterStart) {
                if (facts.reach < 0) CircularProgressIndicator(color = c.accent, strokeWidth = 2.5.dp, modifier = Modifier.size(26.dp))
                else Text("${facts.reach}", color = tone, style = inter(40, FontWeight.SemiBold))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        facts.loading -> "Loading campus setup\u2026"
                        facts.reach < 0 -> "Counting phones\u2026"
                        facts.reach == 1 -> if (facts.estimated) "phone at most" else "phone will be alerted"
                        else -> if (facts.estimated) "phones at most" else "phones will be alerted"
                    },
                    color = c.text, style = inter(15, FontWeight.Medium),
                )
                if (bad) Text("Nobody would get this. Turn off \u201COnly people on campus\u201D to alert everyone.", color = c.dangerText, style = inter(12), modifier = Modifier.padding(top = 3.dp))
            }
        }
        if (facts.week.isNotEmpty() || facts.online.isNotEmpty() || facts.setup.isNotEmpty()) {
            Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (facts.week.isNotEmpty()) InfoChip(facts.week, R.drawable.ic_timer)
                if (facts.online.isNotEmpty()) {
                    InfoChip(
                        "${facts.online.size} online ${if (facts.online.size == 1) "group" else "groups"} skipped",
                        R.drawable.ic_users, color = colorResource(R.color.lg_warn),
                        modifier = Modifier.clickable { expanded = !expanded },
                    )
                }
                if (facts.setup.isNotEmpty()) InfoChip(facts.setup, R.drawable.ic_school)
            }
            if (expanded && facts.online.isNotEmpty()) {
                Text("Online this week: " + facts.online.joinToString("  \u00B7  "), color = c.muted, style = inter(12), modifier = Modifier.padding(top = 10.dp))
            }
        }
        facts.notes.forEach { Text(it, color = c.muted, style = inter(12), modifier = Modifier.padding(top = 8.dp)) }
    }
}

/** What the notification will look like on a phone. */
@Composable
private fun NotificationPreview(text: String) {
    val c = Ligtas.colors
    val title = text.substringBefore("\n")
    val body = text.substringAfter("\n", "")
    GlassCard(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(c.chip), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_shield_check), null, tint = c.accent, modifier = Modifier.size(14.dp))
            }
            Text("Ligtas  \u00B7  now", color = c.muted, style = inter(12), modifier = Modifier.padding(start = 8.dp))
        }
        Text(title, color = c.text, style = inter(15, FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp))
        if (body.isNotEmpty()) Text(body, color = c.muted, style = inter(13).copy(lineHeight = 18.sp), modifier = Modifier.padding(top = 3.dp))
    }
}

/** A field showing its label and current choice; tapping opens the list. */
@Composable
private fun LgDropdown(label: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val c = Ligtas.colors
    var open by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box {
        Row(
            Modifier.fillMaxWidth().onSizeChanged { widthPx = it.width }
                .clip(RoundedCornerShape(16.dp)).background(c.field.copy(alpha = 0.75f))
                .clickable { open = true }.padding(start = 14.dp, end = 12.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = c.muted, style = inter(11))
                Text(options.getOrElse(selectedIndex) { "" }, color = c.text, style = inter(14, FontWeight.Medium), maxLines = 1)
            }
            Icon(painterResource(R.drawable.ic_chevron), null, tint = c.muted, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            modifier = Modifier.width(with(density) { widthPx.toDp() }),
            containerColor = c.field,
            shape = RoundedCornerShape(16.dp),
        ) {
            options.forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = { Text(label, color = if (i == selectedIndex) c.accent else c.text, style = LigtasText.hint) },
                    onClick = { open = false; onSelect(i) },
                )
            }
        }
    }
}

@Composable
private fun NoteField(state: EditAlertSheetState, listener: EditAlertSheetListener) {
    val c = Ligtas.colors
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = state.note,
        onValueChange = {
            if (it.length <= AlertTemplate.NOTE_MAX) {
                state.note = it
                state.noteError = ""
                listener.onContentChanged()
            }
        },
        maxLines = 2,
        textStyle = LigtasText.hint.copy(color = c.text),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth().clip(shape).background(GlassFill)
                    .border(1.dp, if (state.noteError.isEmpty()) c.border else c.dangerBorder, shape)
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(Modifier.weight(1f)) {
                    if (state.note.isEmpty()) {
                        Text(if (state.noteRequired) "Required: add a short note, e.g. Use Exit B" else "Add a short note, e.g. Use Exit B",
                            color = c.muted, style = LigtasText.hint)
                    }
                    inner()
                }
                Text("${state.note.length}/${AlertTemplate.NOTE_MAX}", color = c.muted, style = inter(11), modifier = Modifier.padding(start = 10.dp, top = 3.dp))
            }
        },
    )
    if (state.noteError.isNotEmpty()) Text(state.noteError, color = c.dangerText, style = LigtasText.small)
}
