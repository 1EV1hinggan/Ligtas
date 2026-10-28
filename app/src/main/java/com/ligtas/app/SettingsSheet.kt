package com.ligtas.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog

/** What the Settings sheet shows besides the language (which lives in [Lang]). Java fills it in. */
class SettingsState {
    var email by mutableStateOf("")
    var role by mutableStateOf("")
    /** Things that stop alerts from reaching this phone properly. */
    var problems by mutableIntStateOf(0)
    var critical by mutableStateOf(false)
    /** Name of the Do Not Disturb mode that is on right now, or "". */
    var dnd by mutableStateOf("")
}

interface SettingsListener {
    fun onLanguage(taglish: Boolean)
    fun onReadiness()
    fun onSignOut()
    fun onClose()
}

object SettingsBridge {
    @JvmStatic
    fun attach(dialog: BottomSheetDialog, activity: ComponentActivity, state: SettingsState, listener: SettingsListener) {
        val host = ComposeView(activity)
        host.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        dialog.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(activity)
            it.setViewTreeViewModelStoreOwner(activity)
            it.setViewTreeSavedStateRegistryOwner(activity)
        }
        host.setContent { LigtasTheme { SettingsSheet(state, listener) } }
        SheetGlass.apply(dialog)
        dialog.setContentView(host)
    }
}

@Composable
fun SettingsSheet(state: SettingsState, listener: SettingsListener) {
    val c = Ligtas.colors
    val ok = colorResource(R.color.lg_ok)
    val warn = colorResource(R.color.lg_warn)

    SheetScaffold(
        title = "Settings",
        icon = R.drawable.ic_shield_check,
        footer = { ActionButton(t("Close", "Isara"), BtnKind.GHOST, Modifier.fillMaxWidth()) { listener.onClose() } },
    ) {
        // ---- language ----
        Section(t("Language", "Wika")) {
            SegmentedControl(listOf("English", "Taglish"), if (Lang.taglish) 1 else 0) { listener.onLanguage(it == 1) }
            Text(
                t("Alerts, notifications and messages on this phone will use this language.",
                    "Gagamitin ang language na ito sa alerts, notifications at messages sa phone na ito."),
                color = c.muted, style = inter(12).copy(lineHeight = 17.sp), modifier = Modifier.padding(top = 10.dp),
            )
            GlassCard(Modifier.padding(top = 12.dp), padding = 14.dp) {
                Text(t("PREVIEW", "PREVIEW"), color = c.muted, style = inter(10, FontWeight.SemiBold).copy(letterSpacing = 1.1.sp))
                Row(Modifier.padding(top = 8.dp).fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(Modifier.width(3.dp).fillMaxHeight().clip(LigtasShapes.pill).background(c.danger.copy(alpha = 0.7f)))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(t("FIRE ALERT", "SUNOG ALERT"), color = c.text, style = inter(14, FontWeight.SemiBold))
                        Text(
                            t("Fire reported. Evacuate now through the nearest safe exit. Do not use elevators.",
                                "May sunog. Lumabas na agad sa pinakamalapit na safe exit. Huwag gumamit ng elevator."),
                            color = c.muted, style = inter(13).copy(lineHeight = 18.sp), modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        // ---- alert readiness ----
        Section(t("Alert readiness", "Alert readiness")) {
            val allSet = state.problems == 0 && state.dnd.isEmpty()
            val tone = when { allSet -> ok; state.critical -> c.dangerText; else -> warn }
            GlassCard(
                Modifier.bounceClick(onClick = listener::onReadiness), padding = 14.dp,
                tint = if (allSet) GlassFill else tone.copy(alpha = 0.07f),
                borderColor = if (allSet) c.border else tone.copy(alpha = 0.35f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(c.chip), contentAlignment = Alignment.Center) {
                        Icon(painterResource(if (allSet) R.drawable.ic_circle_check else R.drawable.ic_alert_triangle), null, tint = tone, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(
                            when {
                                state.problems > 0 -> t("${state.problems} ${if (state.problems == 1) "thing needs" else "things need"} attention",
                                    "May ${state.problems} na kailangang ayusin")
                                state.dnd.isNotEmpty() -> t("Do Not Disturb is ON (${state.dnd})", "Naka-ON ang Do Not Disturb (${state.dnd})")
                                else -> t("This phone is ready for alerts", "Handa na ang phone na ito para sa alerts")
                            },
                            color = c.text, style = inter(14, FontWeight.SemiBold),
                        )
                        Text(
                            if (allSet) t("Tap to review the checklist", "I-tap para makita ang checklist")
                            else t("Tap to fix", "I-tap para ayusin"),
                            color = c.muted, style = inter(12), modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    Icon(painterResource(R.drawable.ic_chevron), null, tint = c.muted, modifier = Modifier.size(20.dp))
                }
            }
        }

        // ---- account ----
        Section("Account") {
            GlassCard(padding = 14.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(state.email, 40.dp)
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(state.email, color = c.text, style = inter(14, FontWeight.Medium), maxLines = 1)
                        if (state.role.isNotEmpty()) Text(state.role, color = c.muted, style = inter(12), modifier = Modifier.padding(top = 2.dp))
                    }
                }
                ActionButton(t("Sign out", "Mag-sign out"), BtnKind.GHOST, Modifier.padding(top = 14.dp).fillMaxWidth()) { listener.onSignOut() }
            }
        }
    }
}
