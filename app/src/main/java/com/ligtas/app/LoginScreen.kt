package com.ligtas.app

import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.material.bottomsheet.BottomSheetDialog

/** What the login screen shows. LoginActivity.java keeps the Google sign-in and roster check. */
class LoginViewModel : ViewModel() {
    var busy by mutableStateOf(false)
    var status by mutableStateOf("")
    var isError by mutableStateOf(false)
}

interface LoginListener {
    fun onSignIn()
}

object LoginBridge {
    @JvmStatic
    fun attach(view: ComposeView, vm: LoginViewModel, listener: LoginListener) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent { LigtasTheme { LoginScreen(vm, listener) } }
    }
}

@Composable
fun LoginScreen(vm: LoginViewModel, listener: LoginListener) {
    val c = Ligtas.colors
    // the emblem eases in once
    val intro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { intro.animateTo(1f, tween(450)) }
    // the emblem floats very slowly, like a beacon
    val floatY by rememberInfiniteTransition(label = "emblem").animateFloat(
        -6f, 6f, infiniteRepeatable(tween(3400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "emblemY",
    )

    Column(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 40.dp)) {
        Column(
            Modifier.weight(1f).fillMaxWidth().alpha(intro.value).scale(0.92f + 0.08f * intro.value),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GlassPanel(
                Modifier.size(144.dp).graphicsLayer { translationY = floatY * density },
                RoundedCornerShape(48.dp), contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(108.dp).clip(CircleShape).background(c.chip), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_shield_check), "Ligtas logo", tint = c.accent, modifier = Modifier.size(64.dp))
                }
            }
            Row(Modifier.padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_shield_check), null, tint = c.accent, modifier = Modifier.size(32.dp))
                Text(
                    stringResource(R.string.app_name), color = c.text, modifier = Modifier.padding(start = 8.dp),
                    style = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 32.sp),
                )
            }
            Text(
                "Sign in with your school account", color = c.muted, textAlign = TextAlign.Center,
                style = TextStyle(fontFamily = InterFamily, fontSize = 16.sp), modifier = Modifier.padding(top = 20.dp).fillMaxWidth(),
            )
        }

        // status line: grey while working, red on a problem
        Row(
            Modifier.fillMaxWidth().padding(bottom = 14.dp).semantics { liveRegion = LiveRegionMode.Polite },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(vm.busy && vm.status.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.padding(end = 10.dp).size(16.dp))
            }
            Text(
                vm.status, textAlign = TextAlign.Center,
                color = if (vm.isError) Color(0xFFFF8A82) else c.muted,
                style = TextStyle(fontFamily = InterFamily, fontSize = 14.sp),
                modifier = Modifier.weight(1f, fill = false),
            )
        }

        Box(
            Modifier.fillMaxWidth().height(56.dp).enter(3).alpha(if (vm.busy) 0.5f else 1f)
                .bounceClick(enabled = !vm.busy, onClick = listener::onSignIn)
                .clip(LigtasShapes.pill).background(c.accent).glint(),
            contentAlignment = Alignment.Center,
        ) {
            Text("Sign in with school account", color = c.onAccent,
                style = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Privacy notice (shown once, must be agreed to)
// ---------------------------------------------------------------------------------------------

interface ConsentListener {
    fun onAgree()
    fun onDecline()
}

object ConsentBridge {
    @JvmStatic
    fun attach(dialog: BottomSheetDialog, activity: ComponentActivity, title: String, text: String, listener: ConsentListener) {
        val host = ComposeView(activity)
        host.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        dialog.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(activity)
            it.setViewTreeViewModelStoreOwner(activity)
            it.setViewTreeSavedStateRegistryOwner(activity)
        }
        host.setContent { LigtasTheme { ConsentSheet(title, text, listener) } }
        SheetGlass.apply(dialog)
        dialog.setContentView(host)
    }
}

@Composable
fun ConsentSheet(title: String, text: String, listener: ConsentListener) {
    val c = Ligtas.colors
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(sheetFill()).border(1.dp, GlassEdge, shape)
            .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).width(40.dp).height(4.dp).clip(LigtasShapes.pill).background(c.border))
        Text(title, color = c.text, modifier = Modifier.padding(top = 16.dp),
            style = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp))
        // the notice scrolls; the buttons below always stay visible
        Column(Modifier.weight(1f, fill = false).padding(top = 12.dp).verticalScroll(rememberScrollState())) {
            Text(text, color = c.text, style = LigtasText.body)
        }
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.weight(1f).height(52.dp).clip(LigtasShapes.pill).background(GlassFill)
                    .border(1.dp, c.border, LigtasShapes.pill).clickable(onClick = listener::onDecline),
                contentAlignment = Alignment.Center,
            ) { Text("Decline", color = c.text, style = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)) }
            Box(
                Modifier.weight(1f).height(52.dp).clip(LigtasShapes.pill).background(c.accent).clickable(onClick = listener::onAgree),
                contentAlignment = Alignment.Center,
            ) { Text("I agree", color = c.onAccent, style = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)) }
        }
    }
}
