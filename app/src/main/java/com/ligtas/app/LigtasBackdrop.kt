package com.ligtas.app

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The living background: a quiet "alert broadcast" that loops forever.
 *  - soft signal rings spread out from the top-right corner (an alert going out from the control point),
 *  - small dots (phones on campus) glow for a moment when a ring reaches them,
 *  - three dim blue orbs drift slowly so the glass tiles have something to frost.
 * Everything is low contrast on purpose; it should be felt, not watched.
 */

/** Where the full-screen backdrop sits in the window, so a glass tile can draw the same picture behind itself. */
private object BackdropGeometry {
    var origin by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(Size.Zero)
}

private const val RING_PERIOD = 9f      // seconds for one ring to travel across the screen
private const val RING_COUNT = 3

// Dots as fractions of the screen: where "people" are. Fixed, so the picture never jumps.
private val DOTS = listOf(
    0.14f to 0.12f, 0.46f to 0.20f, 0.78f to 0.31f, 0.24f to 0.38f, 0.62f to 0.47f,
    0.10f to 0.58f, 0.88f to 0.60f, 0.40f to 0.69f, 0.70f to 0.79f, 0.20f to 0.88f, 0.55f to 0.93f,
)

/** One shared clock (frame time, in seconds). Frozen when the user has turned system animations off. */
@Composable
private fun rememberBackdropSeconds(): State<Float> {
    val context = androidx.compose.ui.platform.LocalContext.current
    val animationsOn = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
    return produceState(initialValue = 4f, animationsOn) {
        if (!animationsOn) return@produceState
        while (true) androidx.compose.runtime.withFrameNanos { value = it / 1_000_000_000f }
    }
}

/** Draws the whole scene into a w x h area whose top-left is the current origin. */
private fun DrawScope.drawScene(t: Float, w: Float, h: Float, accent: Color, bg: Color) {
    if (w <= 0f || h <= 0f) return
    val density = this.density
    val reach = max(w, h) * 1.05f
    val src = Offset(w * 0.85f, h * 0.04f)   // same corner the old static glow came from

    // base + breathing glow at the source
    drawRect(bg, size = Size(w, h))
    val breathe = 0.86f + 0.14f * sin(t * 2f * PI.toFloat() / 12f)
    drawRect(
        Brush.radialGradient(
            0f to Color(0xFF1D3E6E).copy(alpha = 0.50f * breathe),
            1f to Color(0xFF1D3E6E).copy(alpha = 0f),
            center = src, radius = h * 0.85f,
        ),
        size = Size(w, h),
    )

    // slow drifting orbs
    fun orb(cx: Float, cy: Float, ax: Float, ay: Float, period: Float, phase: Float, r: Float, a: Float, c: Color) {
        val ang = (t / period + phase) * 2f * PI.toFloat()
        val center = Offset(w * (cx + ax * cos(ang)), h * (cy + ay * sin(ang * 0.8f)))
        drawCircle(Brush.radialGradient(0f to c.copy(alpha = a), 1f to c.copy(alpha = 0f), center = center, radius = r), radius = r, center = center)
    }
    orb(0.20f, 0.30f, 0.10f, 0.06f, 26f, 0.00f, w * 0.55f, 0.27f, Color(0xFF2F63B8))
    orb(0.80f, 0.62f, 0.10f, 0.05f, 31f, 0.35f, w * 0.60f, 0.22f, Color(0xFF2A8F9E))
    orb(0.35f, 0.92f, 0.12f, 0.03f, 23f, 0.70f, w * 0.50f, 0.19f, Color(0xFF3A4FA8))

    // rings
    val ringWidth = 1.2f * density
    val band = 0.07f * reach
    for (i in 0 until RING_COUNT) {
        val p = ((t / RING_PERIOD) + i.toFloat() / RING_COUNT) % 1f
        val radius = p * reach
        val fade = (1f - p)
        val alpha = 0.16f * fade * fade * minOf(1f, p * 8f)   // fades in at birth, out as it spreads
        drawCircle(accent.copy(alpha = alpha), radius = radius, center = src, style = Stroke(ringWidth))
        // dots lit by this ring
        for ((fx, fy) in DOTS) {
            val d = (Offset(w * fx, h * fy) - src).getDistance()
            val k = 1f - minOf(1f, kotlin.math.abs(d - radius) / band)
            if (k > 0f) {
                val c = Offset(w * fx, h * fy)
                val glow = k * k * fade
                drawCircle(accent.copy(alpha = 0.22f * glow), radius = 11f * density * (0.6f + 0.4f * k), center = c)
            }
        }
    }
    // dots at rest
    for ((fx, fy) in DOTS) drawCircle(accent.copy(alpha = 0.16f), radius = 1.8f * density, center = Offset(w * fx, h * fy))
}

/** The full-screen background (also paints the base navy, so it replaces the old static drawable). */
@Composable
fun LigtasBackdrop(modifier: Modifier = Modifier) {
    val colors = Ligtas.colors
    val seconds = rememberBackdropSeconds()
    Canvas(
        modifier
            .fillMaxSize()
            .onGloballyPositioned {
                BackdropGeometry.origin = it.positionInWindow()
                BackdropGeometry.size = Size(it.size.width.toFloat(), it.size.height.toFloat())
            },
    ) {
        drawScene(seconds.value, size.width, size.height, colors.accent, colors.bg)
    }
}

/**
 * Frosted glass: a blurred copy of the backdrop drawn behind this area, in the right place.
 * Put it first inside a clipped container, then add the tint and content on top.
 * Blur needs Android 12 (API 31) or newer; on older phones the glass is simply clearer.
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier, blur: Dp = 40.dp) {
    val colors = Ligtas.colors
    val seconds = rememberBackdropSeconds()
    var pos by remember { mutableStateOf(Offset.Zero) }
    Box(modifier.onGloballyPositioned { pos = it.positionInWindow() }) {
        Box(
            Modifier
                .fillMaxSize()
                .blur(blur, BlurredEdgeTreatment.Unbounded)
                .drawBehind {
                    val s = BackdropGeometry.size
                    val o = BackdropGeometry.origin
                    translate(left = o.x - pos.x, top = o.y - pos.y) {
                        drawScene(seconds.value, s.width, s.height, colors.accent, colors.bg)
                    }
                },
        )
    }
}

/** Adds the animated backdrop behind a screen. Called from LigtasUi.applyChrome, so every screen gets it. */
object BackdropBridge {
    private const val TAG = "ligtas_backdrop"

    @JvmStatic
    fun attach(activity: ComponentActivity) {
        val content = activity.findViewById<ViewGroup>(android.R.id.content) as? FrameLayout ?: return
        if (content.findViewWithTag<View>(TAG) != null) return
        // The backdrop paints the navy base itself, so the screen's own static background must not cover it.
        for (i in 0 until content.childCount) content.getChildAt(i).background = null
        val view = ComposeView(activity as Context).apply {
            tag = TAG
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { LigtasTheme { LigtasBackdrop() } }
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        content.addView(view, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
}
