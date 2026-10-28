package com.ligtas.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * The shared "feel" of Ligtas: frosted-glass surfaces, bouncy presses and a gentle entrance.
 * Every screen uses these four pieces so the whole app moves the same way.
 */

/** A frosted-glass surface: blurred moving background, a faint tint, a light top edge and a soft glass border. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    tint: Color = GlassTint,
    borderWidth: Dp = 1.dp,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .border(borderWidth, GlassRim, shape),
        contentAlignment = contentAlignment,
    ) {
        GlassLayers(Modifier.matchParentSize(), tint)
        content()
    }
}

/** Press feedback for any tappable thing: it sinks and springs back, with a light haptic tick on tap. */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val fire by rememberUpdatedState(onClick)
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (pressed && enabled) 0.94f else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow), label = "bounce",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .pointerInput(enabled) {
            detectTapGestures(
                onPress = { pressed = true; tryAwaitRelease(); pressed = false },
                onTap = { if (enabled) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); fire() } },
            )
        }
        .semantics {
            role = Role.Button
            onClick(action = { if (enabled) fire(); true })
        }
}

/** Fades and rises into place once, [index] x 55 ms after the screen appears (a cascade when the indexes count up). */
fun Modifier.enter(index: Int, rise: Dp = 22.dp): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
    }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * rise.toPx()
    }
}

/** A soft light that glides across a button now and then. Apply AFTER clip() so it stays inside the shape. */
fun Modifier.glint(periodMillis: Int = 4200): Modifier = composed {
    val t by rememberInfiniteTransition(label = "glint").animateFloat(
        0f, 1f, infiniteRepeatable(tween(periodMillis, easing = LinearEasing)), label = "glintT",
    )
    this.drawWithContent {
        drawContent()
        val active = 0.3f   // the sweep takes the first 30% of each period; the rest is calm
        if (t < active) {
            val g = t / active
            val x = -0.3f * size.width + g * 1.6f * size.width
            drawRect(Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.24f * sin(PI.toFloat() * g)), Color.Transparent),
                start = Offset(x - 0.25f * size.width, 0f), end = Offset(x + 0.25f * size.width, size.height * 0.7f),
            ))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Glass for bottom sheets
// ---------------------------------------------------------------------------------------------

/** The faint white tint used for cards and buttons that sit on glass. */
internal val GlassFill = Color.White.copy(alpha = 0.06f)

/** A light edge that is brighter at the top, like light catching the rim of a glass sheet. */
internal val GlassEdge: Brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f)))

object SheetGlass {
    /** True when this phone can really blur what is behind a window (Android 12+ with blur switched on). */
    @JvmStatic
    fun blurAvailable(context: android.content.Context): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return false
        val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager ?: return false
        return wm.isCrossWindowBlurEnabled
    }

    /** Blurs the screen behind a bottom sheet (and eases the dimming), so the sheet reads as frosted glass. */
    @JvmStatic
    fun apply(dialog: com.google.android.material.bottomsheet.BottomSheetDialog) {
        val window = dialog.window ?: return
        if (!blurAvailable(dialog.context)) return
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        val attrs = window.attributes
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) attrs.blurBehindRadius = 50
        window.attributes = attrs
        window.setDimAmount(0.30f)
    }
}

/** The sheet's own fill: see-through where the phone can blur behind it, nearly solid where it cannot. */
@Composable
internal fun sheetFill(): Color {
    val context = androidx.compose.ui.platform.LocalContext.current
    val blur = remember { SheetGlass.blurAvailable(context) }
    return Color(0xFF0C1424).copy(alpha = if (blur) 0.68f else 0.95f)
}

// ---------------------------------------------------------------------------------------------
// Polished glass: cool tint, soft sheen, depth, frosted grain, and a rim that catches light
// ---------------------------------------------------------------------------------------------

/** A cool, barely-there tint (pure white looked like plastic). */
internal val GlassTint = Color(0xFFBFD4FF).copy(alpha = 0.05f)

/** The rim of a glass pane: bright where light hits the top-left, a faint echo at the bottom-right, dark between. */
internal val GlassRim: Brush = Brush.linearGradient(
    0f to Color.White.copy(alpha = 0.42f),
    0.5f to Color.White.copy(alpha = 0.04f),
    1f to Color.White.copy(alpha = 0.16f),
)

/** Fine static noise, tiled. Real frosted glass is never perfectly smooth, and this is what stops it looking like plastic. */
private val GrainBrush: Brush by lazy {
    val n = 96
    val rnd = java.util.Random(7)
    val px = IntArray(n * n) { val v = rnd.nextInt(256); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
    val bmp = android.graphics.Bitmap.createBitmap(px, n, n, android.graphics.Bitmap.Config.ARGB_8888)
    ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

/** Everything that makes a pane frosted, as layers: blurred backdrop, tint, sheen, depth shading, grain. */
@Composable
internal fun GlassLayers(modifier: Modifier = Modifier, tint: Color = GlassTint, blur: Dp = 40.dp) {
    Box(modifier) {
        GlassBackdrop(Modifier.matchParentSize(), blur)
        Box(Modifier.matchParentSize().background(tint))
        Box(Modifier.matchParentSize().drawBehind {
            // a wide, soft sheen from the top-left, not a hard bar
            drawRect(Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.12f), 0.45f to Color.Transparent,
                start = Offset.Zero, end = Offset(size.width, size.height),
            ))
            // depth: the lower half sinks slightly
            drawRect(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.16f)))
            // frosted grain
            drawRect(GrainBrush, alpha = 0.06f)
        })
    }
}
