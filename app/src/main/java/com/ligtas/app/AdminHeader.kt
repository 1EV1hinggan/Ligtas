package com.ligtas.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Shape

/**
 * Top of the admin screen: menu button, "Ligtas" with the shield, profile button, then the greeting
 * and the section title. Stateless: the greeting comes in, taps go out.
 */
@Composable
fun AdminHeader(
    greeting: String,
    onMenu: () -> Unit,
    onProfile: () -> Unit,
    modifier: Modifier = Modifier,
    menuOpen: Boolean = false,
    onMenuDismiss: () -> Unit = {},
    onReadiness: () -> Unit = {},
    onSuspend: () -> Unit = {},
    onConsole: () -> Unit = {},
) {
    val colors = Ligtas.colors
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).enter(0),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                HeaderButton(
                    icon = R.drawable.ic_menu,
                    tint = colors.text,
                    shape = LigtasShapes.menuButton,
                    borderWidth = 2.dp,
                    description = "Menu",
                    onClick = onMenu,
                )
                // the menu opens right under the button, so it needs no position maths
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = onMenuDismiss,
                    modifier = Modifier.width(240.dp),
                    containerColor = colors.field,
                    shape = RoundedCornerShape(22.dp),
                ) {
                    MenuRow(R.drawable.ic_shield_check, "Alert readiness") { onMenuDismiss(); onReadiness() }
                    MenuRow(R.drawable.ic_calendar_x, "Suspend classes\u2026") { onMenuDismiss(); onSuspend() }
                    MenuRow(R.drawable.ic_chart, "Campus console") { onMenuDismiss(); onConsole() }
                }
            }
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_shield_check),
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(25.dp),
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = LigtasText.brand,
                    color = colors.text,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            HeaderButton(
                icon = R.drawable.ic_user,
                tint = colors.accent,
                shape = CircleShape,
                fill = colors.chip,
                borderWidth = 1.dp,
                description = "Profile (tap to sign out)",
                onClick = onProfile,
            )
        }
        Text(
            text = greeting.ifBlank { stringResource(R.string.greeting_default) },
            style = LigtasText.greeting,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 28.dp).enter(1),
        )
        Text(
            text = "Help starts with the right alert.",
            style = LigtasText.hint,
            color = colors.muted,
            modifier = Modifier.padding(top = 8.dp).enter(2),
        )
        Text(
            text = stringResource(R.string.title_select_emergency),
            style = LigtasText.section,
            color = colors.text,
            modifier = Modifier.padding(top = 28.dp).enter(3),
        )
        Text(
            text = "Choose the emergency you need to report.",
            style = LigtasText.hint,
            color = colors.muted,
            modifier = Modifier.padding(top = 8.dp).enter(4),
        )
    }
}

@Composable
private fun MenuRow(icon: Int, label: String, onClick: () -> Unit) {
    val colors = Ligtas.colors
    DropdownMenuItem(
        text = {
            Text(label, color = colors.text, style = androidx.compose.ui.text.TextStyle(
                fontFamily = InterFamily, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 15.sp))
        },
        leadingIcon = {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(colors.chip), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, tint = colors.accent, modifier = Modifier.size(20.dp))
            }
        },
        onClick = onClick,
        modifier = Modifier.height(56.dp),
    )
}

/** A 48 dp round-cornered button holding one icon. */
@Composable
internal fun HeaderButton(
    icon: Int,
    tint: androidx.compose.ui.graphics.Color,
    shape: Shape,
    borderWidth: androidx.compose.ui.unit.Dp,
    description: String,
    onClick: () -> Unit,
    fill: androidx.compose.ui.graphics.Color = Ligtas.colors.surface,
) {
    GlassPanel(
        modifier = Modifier.size(48.dp).bounceClick(onClick = onClick).semantics { contentDescription = description },
        shape = shape,
        tint = if (fill == Ligtas.colors.surface) androidx.compose.ui.graphics.Color.White.copy(alpha = 0.055f) else fill.copy(alpha = 0.55f),
        borderWidth = borderWidth,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(24.dp),
        )
    }
}

object AdminHeaderBridge {
    /** Puts the Compose header inside a ComposeView in the XML layout. */
    @JvmStatic
    fun attach(view: ComposeView, viewModel: EmergencyViewModel, listener: EmergencyListener) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            LigtasTheme {
                AdminHeader(
                    greeting = viewModel.greeting,
                    onMenu = { viewModel.menuOpen = true },
                    onProfile = { listener.onProfile() },
                    menuOpen = viewModel.menuOpen,
                    onMenuDismiss = { viewModel.menuOpen = false },
                    onReadiness = { listener.onReadiness() },
                    onSuspend = { listener.onSuspend() },
                    onConsole = { listener.onConsole() },
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF090F1C, widthDp = 380)
@Composable
private fun AdminHeaderPreview() {
    LigtasTheme {
        AdminHeader(greeting = "Hi, Aeron", onMenu = {}, onProfile = {}, modifier = Modifier.padding(24.dp))
    }
}
