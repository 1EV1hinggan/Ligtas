package com.ligtas.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.ViewModel

/** Colour of a headline or status line. Java picks one; Compose turns it into a theme colour. */
object Tone {
    const val NEUTRAL = 0
    const val DANGER = 1
    const val OK = 2
    const val WARN = 3
}

/**
 * Everything the student / teacher home screen shows. MainActivity.java keeps all the logic
 * (last alert store, roster, permissions) and only pushes finished values in here.
 */
class MemberViewModel : ViewModel() {
    var greeting by mutableStateOf("Hi")
    var roleLine by mutableStateOf("")

    // status card
    var headline by mutableStateOf("No active alerts")
    var headlineTone by mutableIntStateOf(Tone.NEUTRAL)
    var warningIcon by mutableStateOf(false)   // triangle instead of shield
    var sub by mutableStateOf("You are registered and ready to receive campus alerts.")

    // last alert panel
    var hasLast by mutableStateOf(false)
    var lastTitle by mutableStateOf("")
    var lastBody by mutableStateOf("")
    var lastTime by mutableStateOf("")
    var showAcknowledge by mutableStateOf(false)
    var showAckedBadge by mutableStateOf(false)

    // teacher only
    var showRollCall by mutableStateOf(false)
    var rollCallText by mutableStateOf("Account for my room")

    // alert readiness
    var readiness by mutableStateOf("")
    var readinessTone by mutableIntStateOf(Tone.NEUTRAL)
}

interface MemberListener {
    fun onProfile()
    fun onSignOut()
    fun onAcknowledge()
    fun onRollCall()
    fun onReadiness()
}

object MemberHomeBridge {
    @JvmStatic
    fun attach(view: ComposeView, vm: MemberViewModel, listener: MemberListener) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent { LigtasTheme { MemberHome(vm, listener) } }
    }
}

@Composable
private fun toneColor(tone: Int): Color = when (tone) {
    Tone.DANGER -> Ligtas.colors.dangerText
    Tone.OK -> colorResource(R.color.lg_ok)
    Tone.WARN -> colorResource(R.color.lg_warn)
    else -> Ligtas.colors.text
}

@Composable
fun MemberHome(vm: MemberViewModel, listener: MemberListener) {
    val c = Ligtas.colors
    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 16.dp)) {
        // header: spacer | shield + Ligtas | profile
        Row(Modifier.fillMaxWidth().height(48.dp).enter(0), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_shield_check), null, tint = c.accent, modifier = Modifier.size(25.dp))
                Text(stringResource(R.string.app_name), style = LigtasText.brand, color = c.text, modifier = Modifier.padding(start = 8.dp))
            }
            HeaderButton(
                icon = R.drawable.ic_user, tint = c.accent, shape = CircleShape, fill = c.chip,
                borderWidth = 1.dp, description = "Settings", onClick = listener::onProfile,
            )
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // who you are
            Column(Modifier.enter(1)) {
                Text(vm.greeting, style = LigtasText.greeting, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val parts = vm.roleLine.split("  \u2022  ").filter { it.isNotBlank() }
                if (parts.isNotEmpty()) {
                    Row(Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        parts.forEachIndexed { i, p -> InfoChip(p, if (i == 0) R.drawable.ic_user else null, color = if (i == 0) c.accent else c.muted) }
                    }
                }
            }

            StatusHero(vm, Modifier.enter(2))

            AnimatedVisibility(vm.hasLast, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                LastAlertCard(vm, listener)
            }
            AnimatedVisibility(vm.showRollCall, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                RoomCard(vm, listener)
            }
            // Only when something needs fixing: the full checklist now lives in Settings
            if (vm.readiness.isNotEmpty() && !readinessIsFine(vm)) ReadinessCard(vm, listener, Modifier.enter(4))

            Text(t("Receive-only: only administrators can send alerts.", "Receive-only: administrators lang ang puwedeng magpadala ng alerts."), color = c.muted,
                style = LigtasText.small.copy(fontStyle = FontStyle.Italic), textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            Text(
                t("Sign out", "Mag-sign out"), color = c.dangerText, style = TextStyle(fontFamily = InterFamily, fontSize = 15.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = listener::onSignOut).padding(12.dp),
            )
        }
    }
}

/** The one thing to read at a glance: are you safe, or is there an alert? The rings pulse slowly, and fast during an alert. */
@Composable
private fun StatusHero(vm: MemberViewModel, modifier: Modifier = Modifier) {
    val c = Ligtas.colors
    val tone = toneColor(vm.headlineTone)
    val ok = colorResource(R.color.lg_ok)
    val warn = colorResource(R.color.lg_warn)
    val danger = vm.headlineTone == Tone.DANGER
    val headColor by animateColorAsState(tone, tween(250), label = "heroColor")
    val ringColor = when (vm.headlineTone) { Tone.DANGER -> c.dangerText; Tone.OK -> ok; Tone.WARN -> warn; else -> c.accent }
    val t by rememberInfiniteTransition(label = "hero").animateFloat(
        0f, 1f, infiniteRepeatable(tween(if (danger) 1300 else 3600, easing = LinearEasing)), label = "heroT",
    )
    GlassCard(
        modifier = modifier,
        padding = 20.dp,
        tint = when (vm.headlineTone) { Tone.DANGER -> c.dangerBg.copy(alpha = 0.55f); Tone.OK -> ok.copy(alpha = 0.08f); Tone.WARN -> warn.copy(alpha = 0.08f); else -> GlassFill },
        borderColor = when (vm.headlineTone) { Tone.DANGER -> c.dangerBorder; Tone.OK -> ok.copy(alpha = 0.35f); Tone.WARN -> warn.copy(alpha = 0.35f); else -> c.border },
    ) {
        Column(Modifier.fillMaxWidth().animateContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    for (k in 0..1) {
                        val p = (t + k * 0.5f) % 1f
                        val r = (44f + 26f * p).dp.toPx()
                        drawCircle(ringColor.copy(alpha = 0.32f * (1f - p)), radius = r, style = Stroke(1.6.dp.toPx()))
                    }
                }
                Box(Modifier.size(84.dp).clip(CircleShape).background(c.chip).border(1.dp, ringColor.copy(alpha = 0.5f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (vm.warningIcon) R.drawable.ic_alert_triangle else R.drawable.ic_shield_check),
                        null, tint = headColor, modifier = Modifier.size(40.dp),
                    )
                }
            }
            Text(vm.headline, color = headColor, style = inter(22, FontWeight.SemiBold), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            Text(vm.sub, color = c.muted, style = inter(13).copy(lineHeight = 18.sp), textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp).fillMaxWidth())
        }
    }
}

/** The alert itself, like the notification you got: what it says, when, and the Acknowledge button. */
@Composable
private fun LastAlertCard(vm: MemberViewModel, listener: MemberListener) {
    val c = Ligtas.colors
    val ok = colorResource(R.color.lg_ok)
    val chips = vm.lastTime.split("  \u2022  ", "\n").filter { it.isNotBlank() }
    GlassCard(modifier = Modifier.animateContentSize()) {
        Text(t("LAST ALERT", "HULING ALERT"), color = c.muted, style = inter(11, FontWeight.SemiBold).copy(letterSpacing = 1.2.sp))
        Text(vm.lastTitle, color = c.text, style = inter(18, FontWeight.SemiBold), modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 10.dp).fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().clip(LigtasShapes.pill).background(c.danger.copy(alpha = 0.7f)))
            Text(vm.lastBody, color = c.text, style = inter(14).copy(lineHeight = 20.sp), modifier = Modifier.padding(start = 12.dp))
        }
        if (chips.isNotEmpty()) {
            Row(Modifier.padding(top = 12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEachIndexed { i, s -> InfoChip(s.trim(), if (i == 0) R.drawable.ic_timer else null) }
            }
        }
        AnimatedVisibility(vm.showAcknowledge, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            ActionButton(t("Acknowledge", "I-acknowledge"), BtnKind.DANGER, Modifier.padding(top = 16.dp).fillMaxWidth(), shine = true) { listener.onAcknowledge() }
        }
        if (vm.showAckedBadge) InfoChip(t("Acknowledged", "Na-acknowledge"), R.drawable.ic_circle_check, color = ok, modifier = Modifier.padding(top = 14.dp))
    }
}

/** Teachers only: report your room. Looks like a call to action until you have reported, then like a confirmation. */
@Composable
private fun RoomCard(vm: MemberViewModel, listener: MemberListener) {
    val c = Ligtas.colors
    val ok = colorResource(R.color.lg_ok)
    val warn = colorResource(R.color.lg_warn)
    val reported = vm.rollCallText.startsWith("Reported:")
    val lines = vm.rollCallText.removePrefix("Reported: ").lines()
    val tone = if (reported) ok else warn
    GlassCard(
        modifier = Modifier.bounceClick(onClick = listener::onRollCall),
        tint = tone.copy(alpha = 0.08f), borderColor = tone.copy(alpha = 0.4f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(c.chip), contentAlignment = Alignment.Center) {
                Icon(painterResource(if (reported) R.drawable.ic_circle_check else R.drawable.ic_school), null, tint = tone, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(if (reported) t("Room reported", "Na-report na ang room") else t("Account for my room", "I-account ang room ko"), color = c.text, style = inter(15, FontWeight.SemiBold))
                Text(
                    if (reported) lines.firstOrNull().orEmpty() else t("Tell the administrators if everyone is safe", "Sabihin sa admins kung ligtas ang lahat"),
                    color = c.muted, style = inter(12), modifier = Modifier.padding(top = 2.dp),
                )
                if (reported && lines.size > 1) Text(lines[1], color = tone, style = inter(12, FontWeight.Medium), modifier = Modifier.padding(top = 2.dp))
            }
            Icon(painterResource(R.drawable.ic_chevron), null, tint = c.muted, modifier = Modifier.size(20.dp))
        }
    }
}

/** True when nothing needs fixing (the one-line "all set" text the Java side produces, with no warning under it). */
private fun readinessIsFine(vm: MemberViewModel): Boolean {
    val body = vm.readiness.removePrefix("Alert readiness: ").lines()
    return body.firstOrNull().orEmpty().startsWith("all set") && body.drop(1).isEmpty()
}

/** The Java side always produces English in a fixed shape; here it is shown in the chosen language. */
private fun readinessHeadline(first: String, allSet: Boolean): String {
    if (allSet) return t("This phone is ready for alerts", "Handa na ang phone na ito para sa alerts")
    val n = Regex("\\d+").find(first)?.value
    return if (Lang.taglish && n != null) "May $n na kailangang ayusin. I-tap para ayusin." else first.replaceFirstChar { it.uppercase() }
}

private fun readinessExtra(extra: String): String {
    if (!Lang.taglish || !extra.startsWith("Do Not Disturb is ON")) return extra
    val mode = extra.substringAfter("(").substringBefore(")")
    return "Naka-ON ang Do Not Disturb ($mode). Baka hindi pa rin lumabas o ma-silent ang alerts sa ibang phone."
}

/** Is this phone set up to receive alerts? One line when yes; what to fix when not. Tapping opens the setup sheet. */
@Composable
private fun ReadinessCard(vm: MemberViewModel, listener: MemberListener, modifier: Modifier = Modifier) {
    val c = Ligtas.colors
    val ok = colorResource(R.color.lg_ok)
    val warn = colorResource(R.color.lg_warn)
    val body = vm.readiness.removePrefix("Alert readiness: ").lines()
    val first = body.firstOrNull().orEmpty()
    val extra = body.drop(1).joinToString(" ")
    val allSet = first.startsWith("all set")
    val tone = when (vm.readinessTone) { Tone.OK -> ok; Tone.DANGER -> c.dangerText; else -> warn }
    GlassCard(
        modifier = modifier.bounceClick(onClick = listener::onReadiness),
        tint = if (allSet && extra.isEmpty()) GlassFill else tone.copy(alpha = 0.07f),
        borderColor = if (allSet && extra.isEmpty()) c.border else tone.copy(alpha = 0.35f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(c.chip), contentAlignment = Alignment.Center) {
                Icon(painterResource(if (allSet && extra.isEmpty()) R.drawable.ic_circle_check else R.drawable.ic_alert_triangle), null, tint = tone, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text("ALERT READINESS", color = c.muted, style = inter(10, FontWeight.SemiBold).copy(letterSpacing = 1.1.sp))
                Text(
                    readinessHeadline(first, allSet),
                    color = c.text, style = inter(14, FontWeight.SemiBold), modifier = Modifier.padding(top = 2.dp),
                )
                if (extra.isNotEmpty()) Text(readinessExtra(extra), color = c.muted, style = inter(12), modifier = Modifier.padding(top = 2.dp))
            }
            Icon(painterResource(R.drawable.ic_chevron), null, tint = c.muted, modifier = Modifier.size(20.dp))
        }
        if (!allSet) {
            Text(
                t("Xiaomi, Oppo, Vivo: also set this app's battery saver to No restrictions and turn on Autostart, or alerts may arrive late.",
                    "Xiaomi, Oppo, Vivo: i-set din ang battery saver ng app na ito sa No restrictions at i-on ang Autostart, o baka late dumating ang alerts."),
                color = c.muted, style = inter(11).copy(lineHeight = 15.sp), modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}
