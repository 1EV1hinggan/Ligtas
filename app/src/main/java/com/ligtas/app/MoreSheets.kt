package com.ligtas.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog

// ---------------------------------------------------------------------------------------------
// Shared pieces for the smaller sheets (Suspend classes, Account for my room)
// ---------------------------------------------------------------------------------------------

object SheetHost {
    /** Puts any Compose content inside a bottom sheet dialog. */
    @JvmStatic
    fun attach(dialog: BottomSheetDialog, activity: ComponentActivity, content: @Composable () -> Unit) {
        val host = ComposeView(activity)
        host.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        dialog.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(activity)
            it.setViewTreeViewModelStoreOwner(activity)
            it.setViewTreeSavedStateRegistryOwner(activity)
        }
        host.setContent { LigtasTheme { content() } }
        SheetGlass.apply(dialog)
        dialog.setContentView(host)
    }
}

private val SheetBg = Color(0xFF0C1424)
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

@Composable
internal fun SheetFrame(title: String, subtitle: String, content: @Composable () -> Unit) {
    val c = Ligtas.colors
    Column(
        Modifier.fillMaxWidth().clip(SheetShape).background(sheetFill()).border(1.dp, GlassEdge, SheetShape)
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(LigtasShapes.pill).background(c.border))
        Text(title, color = c.text, modifier = Modifier.padding(top = 16.dp),
            style = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp))
        if (subtitle.isNotEmpty()) Text(subtitle, color = c.muted, style = LigtasText.hint, modifier = Modifier.padding(top = 4.dp))
        content()
    }
}

@Composable
private fun Label(text: String, topDp: Int = 14) {
    Text(text, color = Ligtas.colors.muted, style = LigtasText.small, modifier = Modifier.padding(top = topDp.dp, bottom = 4.dp))
}

@Composable
private fun ChoiceRow(selected: Boolean, title: String, subtitle: String = "", onClick: () -> Unit) {
    val c = Ligtas.colors
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.padding(top = 8.dp).fillMaxWidth().clip(shape)
            .background(if (selected) c.selectedBg.copy(alpha = 0.7f) else GlassFill)
            .border(1.dp, if (selected) c.selectedBorder else c.border, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = c.accent, unselectedColor = c.muted),
            modifier = Modifier.padding(10.dp))
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, color = c.text, style = LigtasText.hint)
            if (subtitle.isNotEmpty()) Text(subtitle, color = c.muted, style = LigtasText.small)
        }
    }
}

@Composable
private fun SheetField(
    value: String, onChange: (String) -> Unit, hint: String, error: String = "",
    maxLength: Int, maxLines: Int = 1, number: Boolean = false,
) {
    val c = Ligtas.colors
    val shape = RoundedCornerShape(16.dp)
    BasicTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength && (!number || it.all(Char::isDigit))) onChange(it) },
        maxLines = maxLines,
        singleLine = maxLines == 1,
        textStyle = LigtasText.hint.copy(color = c.text),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = if (number) KeyboardCapitalization.None else KeyboardCapitalization.Sentences,
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
        ),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier.fillMaxWidth().clip(shape).background(c.field)
                    .border(1.dp, if (error.isEmpty()) Color.Transparent else c.dangerBorder, shape).padding(14.dp),
            ) {
                if (value.isEmpty()) Text(hint, color = c.muted, style = LigtasText.hint)
                inner()
            }
        },
    )
    if (error.isNotEmpty()) Text(error, color = c.dangerText, style = LigtasText.small, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun SheetDropdown(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val c = Ligtas.colors
    var open by remember { mutableStateOf(false) }
    var widthPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    Box {
        Row(
            Modifier.fillMaxWidth().height(52.dp).onSizeChanged { widthPx = it.width }
                .clip(RoundedCornerShape(22.dp)).background(c.field).clickable { open = true }
                .padding(start = 16.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(options.getOrElse(selectedIndex) { "" }, color = c.text, style = LigtasText.hint, maxLines = 1, modifier = Modifier.weight(1f))
            Icon(painterResource(R.drawable.ic_chevron), null, tint = c.muted, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(
            expanded = open, onDismissRequest = { open = false },
            modifier = Modifier.width(with(density) { widthPx.toDp() }),
            containerColor = c.field, shape = RoundedCornerShape(16.dp),
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
private fun ButtonRow(cancel: String, confirm: String, onCancel: () -> Unit, onConfirm: () -> Unit) {
    val c = Ligtas.colors
    val label = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            Modifier.weight(1f).height(52.dp).clip(LigtasShapes.pill).background(GlassFill)
                .border(1.dp, c.border, LigtasShapes.pill).clickable(onClick = onCancel),
            contentAlignment = Alignment.Center,
        ) { Text(cancel, color = c.text, style = label) }
        Box(
            Modifier.weight(1f).height(52.dp).clip(LigtasShapes.pill).background(c.accent).clickable(onClick = onConfirm),
            contentAlignment = Alignment.Center,
        ) { Text(confirm, color = c.onAccent, style = label) }
    }
}

// ---------------------------------------------------------------------------------------------
// Admin: Suspend classes
// ---------------------------------------------------------------------------------------------

class SuspendState {
    val whoLabels = mutableStateListOf<String>()
    var whoIndex by mutableIntStateOf(0)
    /** 0 = Today, 1 = Tomorrow, 2 = Today and tomorrow */
    var whenIndex by mutableIntStateOf(0)
    /** 0 = suspended / online, 1 = no classes (holiday) */
    var modeIndex by mutableIntStateOf(0)
}

interface SuspendListener {
    fun onApply()
    fun onCancel()
}

@Composable
fun SuspendSheet(state: SuspendState, listener: SuspendListener) {
    val c = Ligtas.colors
    val warn = colorResource(R.color.lg_warn)
    val who = state.whoLabels.getOrElse(state.whoIndex) { "" }
    val whenText = listOf("today", "tomorrow", "today and tomorrow")[state.whenIndex.coerceIn(0, 2)]
    SheetScaffold(
        title = "Suspend classes",
        subtitle = "A one-tap calendar rule, for example for a typhoon",
        icon = R.drawable.ic_calendar_x,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Cancel", BtnKind.GHOST, Modifier.weight(1f)) { listener.onCancel() }
                ActionButton("Apply", BtnKind.PRIMARY, Modifier.weight(1f)) { listener.onApply() }
            }
        },
    ) {
        Section("Applies to") { SheetDropdown(state.whoLabels, state.whoIndex) { state.whoIndex = it } }
        Section("When") { SegmentedControl(listOf("Today", "Tomorrow", "Both days"), state.whenIndex) { state.whenIndex = it } }
        Section("What happens") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceCard(state.modeIndex == 0, "Classes suspended / online", "On-site-only alerts skip them", R.drawable.ic_school) { state.modeIndex = 0 }
                ChoiceCard(state.modeIndex == 1, "No classes, holiday", "Still alerted: people may be on campus", R.drawable.ic_calendar_x) { state.modeIndex = 1 }
            }
        }
        // a plain-language summary of exactly what Apply will do
        GlassCard(padding = 14.dp, tint = warn.copy(alpha = 0.08f), borderColor = warn.copy(alpha = 0.35f)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(painterResource(R.drawable.ic_alert_triangle), null, tint = warn, modifier = Modifier.size(18.dp))
                Text(
                    if (state.modeIndex == 0) "$who: classes suspended or online $whenText. Alerts meant for people on campus will skip these groups."
                    else "$who: no classes $whenText. Everyone is still alerted, because people may be on campus.",
                    color = c.text, style = inter(13).copy(lineHeight = 18.sp), modifier = Modifier.padding(start = 10.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Teacher: Account for my room
// ---------------------------------------------------------------------------------------------

class RoomReportState {
    var room by mutableStateOf("")
    var roomError by mutableStateOf("")
    val statusLabels = mutableStateListOf<String>()
    /** -1 until one of the three is picked */
    var statusIndex by mutableIntStateOf(-1)
    var statusError by mutableStateOf("")
    var count by mutableStateOf("")
    var note by mutableStateOf("")
}

interface RoomReportListener {
    fun onSend()
    fun onCancel()
}

@Composable
fun RoomReportSheet(state: RoomReportState, listener: RoomReportListener) {
    val c = Ligtas.colors
    val tints = listOf(colorResource(R.color.lg_ok), colorResource(R.color.lg_warn), c.dangerText)
    val icons = listOf(R.drawable.ic_circle_check, R.drawable.ic_alert_triangle, R.drawable.ic_siren)
    SheetScaffold(
        title = "Account for my room",
        subtitle = "Tell the administrators about your room. You can send an update if things change.",
        icon = R.drawable.ic_school,
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton("Cancel", BtnKind.GHOST, Modifier.weight(1f)) { listener.onCancel() }
                ActionButton("Send report", BtnKind.PRIMARY, Modifier.weight(1.3f)) { listener.onSend() }
            }
        },
    ) {
        Section("Is everyone accounted for?") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.statusLabels.forEachIndexed { i, t ->
                    ChoiceCard(state.statusIndex == i, t, icon = icons.getOrElse(i) { R.drawable.ic_circle_check }, iconTint = tints.getOrElse(i) { c.accent }) {
                        state.statusIndex = i; state.statusError = ""
                    }
                }
                if (state.statusError.isNotEmpty()) Text(state.statusError, color = c.dangerText, style = LigtasText.small)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1.4f)) {
                SectionLabel("Room")
                SheetField(state.room, { state.room = it; state.roomError = "" }, "e.g. CICS 204", state.roomError, maxLength = 40)
            }
            Column(Modifier.weight(1f)) {
                SectionLabel("Students", "optional")
                SheetField(state.count, { state.count = it }, "e.g. 32", maxLength = 3, number = true)
            }
        }
        Section("Note for the administrators", "optional") {
            SheetField(state.note, { state.note = it }, "Anything they should know", maxLength = 200, maxLines = 3)
        }
    }
}

object SuspendBridge {
    @JvmStatic
    fun attach(dialog: BottomSheetDialog, activity: ComponentActivity, state: SuspendState, listener: SuspendListener) =
        SheetHost.attach(dialog, activity) { SuspendSheet(state, listener) }
}

object RoomReportBridge {
    @JvmStatic
    fun attach(dialog: BottomSheetDialog, activity: ComponentActivity, state: RoomReportState, listener: RoomReportListener) =
        SheetHost.attach(dialog, activity) { RoomReportSheet(state, listener) }
}
