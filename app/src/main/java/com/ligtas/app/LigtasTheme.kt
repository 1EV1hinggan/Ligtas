package com.ligtas.app

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Ligtas design system for Compose. Every color is read from res/values/ligtas_colors.xml,
 * so the XML screens and the Compose screens can never drift apart: change a color there and both change.
 */
@Immutable
class LigtasColors(
    val bg: Color,
    val surface: Color,
    val border: Color,
    val chip: Color,
    val field: Color,
    val selectedBg: Color,
    val selectedBorder: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val dangerBorder: Color,
    val dangerText: Color,
    val dangerBg: Color,
    val dangerLine: Color,
)

private val LocalLigtasColors = staticCompositionLocalOf<LigtasColors> {
    error("LigtasTheme is missing: wrap your screen in LigtasTheme { }")
}

/** Inter, the same font the XML screens use. */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_semibold, FontWeight.Bold),
)

/** Only the text styles Ligtas actually uses; add more here as more screens move over. */
private val LigtasTypography = Typography(
    titleLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelLarge = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyMedium = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp),
)

/** Text styles for the screen header (the Typography above covers the tiles and buttons). */
object LigtasText {
    val brand = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 24.sp)
    val greeting = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 26.sp)
    val section = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 20.sp)
    val hint = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp)
    val small = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp)
    val confirmTitle = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
    val body = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 20.sp)
    val label = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
}

/** Corner radii from the Figma design. */
object LigtasShapes {
    val tile = RoundedCornerShape(27.dp)
    val chip = RoundedCornerShape(12.dp)
    val alertButton = RoundedCornerShape(41.dp)
    val pill = RoundedCornerShape(100.dp)
    val menuButton = RoundedCornerShape(19.dp)
}

object Ligtas {
    val colors: LigtasColors
        @Composable get() = LocalLigtasColors.current
}

@Composable
fun LigtasTheme(content: @Composable () -> Unit) {
    val c = LigtasColors(
        bg = colorResource(R.color.lg_bg),
        surface = colorResource(R.color.lg_surface),
        border = colorResource(R.color.lg_border),
        chip = colorResource(R.color.lg_chip),
        field = colorResource(R.color.lg_field),
        selectedBg = colorResource(R.color.lg_sel_bg),
        selectedBorder = colorResource(R.color.lg_sel_border),
        text = colorResource(R.color.lg_text),
        muted = colorResource(R.color.lg_muted),
        accent = colorResource(R.color.lg_accent),
        onAccent = colorResource(R.color.lg_on_accent),
        danger = colorResource(R.color.lg_danger),
        dangerBorder = colorResource(R.color.lg_danger_border),
        dangerText = colorResource(R.color.lg_danger_text),
        dangerBg = colorResource(R.color.lg_danger_bg),
        dangerLine = colorResource(R.color.lg_danger_line),
    )
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = c.accent,
            onPrimary = c.onAccent,
            background = c.bg,
            onBackground = c.text,
            surface = c.surface,
            onSurface = c.text,
            error = c.danger,
        ),
        typography = LigtasTypography,
        shapes = Shapes(
            small = LigtasShapes.chip,
            large = LigtasShapes.tile,
        ),
    ) {
        CompositionLocalProvider(LocalLigtasColors provides c, content = content)
    }
}
