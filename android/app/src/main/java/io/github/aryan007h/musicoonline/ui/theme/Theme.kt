package io.github.aryan007h.musicoonline.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ── Palette: "Nebula" — deep space surfaces, one vivid violet accent, a
//    mint counterweight. Dark-first: the app is designed dark and merely
//    tolerates light. ──────────────────────────────────────────────────────

object MusicoColors {
    // Dark surfaces (three steps, so depth reads without elevation shadows)
    val bg0 = Color(0xFF0A0A12) // deepest — screen background
    val bg1 = Color(0xFF12121C) // cards, sheets
    val bg2 = Color(0xFF1C1C2A) // raised chips, controls

    // Accents
    val violet = Color(0xFF7C5CFF)
    val violetBright = Color(0xFFA78BFF)
    val mint = Color(0xFF53F2C4)
    val coral = Color(0xFFFF6E7A)
    val amber = Color(0xFFFFC24B)

    // Text
    val ink = Color(0xFFF2F1F7)
    val inkDim = Color(0xFFA8A6B8)
    val inkFaint = Color(0xFF6E6C80)

    // Light surfaces
    val lightBg0 = Color(0xFFF7F6FB)
    val lightBg1 = Color(0xFFFFFFFF)
    val lightInk = Color(0xFF191826)
    val lightInkDim = Color(0xFF5D5B70)
}

// ── Motion: one shared spring vocabulary — Material 3 Expressive reads as
//    "playful but physical" when every animation uses the same family. ─────

object MusicoMotion {
    /** The expressive spring: bouncy enough to feel alive, settles fast. */
    fun <T> expressive() = spring<T>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Snappier variant for small elements (icons, chips). */
    fun <T> snappy() = spring<T>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** Gentle default for fades and containers. */
    fun <T> gentle() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /** Emphasized easing from the M3 spec, for tweens where springs overshoot. */
    val emphasizedEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    const val tweenMs = 350
}

// ── Shapes: big radii everywhere; cards nearly pill out. ─────────────────

object MusicoShapes {
    val card = RoundedCornerShape(24.dp)
    val cardLarge = RoundedCornerShape(32.dp)
    val chip = RoundedCornerShape(999.dp) // full pill
    val sheet = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    val hero = RoundedCornerShape(28.dp)
}

/** Extra expressive tokens not covered by the M3 color scheme. */
data class MusicoExtras(
    val bg0: Color,
    val bg1: Color,
    val bg2: Color,
    val inkDim: Color,
    val inkFaint: Color,
    val mint: Color,
    val coral: Color,
    val amber: Color,
    val cardRadius: Dp = 24.dp,
    val cardLargeRadius: Dp = 32.dp,
)

val LocalMusicoExtras = staticCompositionLocalOf {
    MusicoExtras(
        bg0 = MusicoColors.bg0,
        bg1 = MusicoColors.bg1,
        bg2 = MusicoColors.bg2,
        inkDim = MusicoColors.inkDim,
        inkFaint = MusicoColors.inkFaint,
        mint = MusicoColors.mint,
        coral = MusicoColors.coral,
        amber = MusicoColors.amber,
    )
}

// ── Typography: one family, strong weight contrast, tight display sizes ──

data class MusicoTypography(
    val display: TextStyle,   // "Music0" brand, player titles
    val section: TextStyle,   // MADE FOR YOU — loud uppercase
    val title: TextStyle,     // card titles
    val body: TextStyle,
    val caption: TextStyle,
    val lyricCurrent: TextStyle, // the big karaoke word
    val lyricNeighbor: TextStyle,
)

val LocalMusicoTypography = staticCompositionLocalOf { DefaultTypography }

private val DefaultTypography = MusicoTypography(
        display = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 34.sp,
            letterSpacing = (-0.5).sp,
        ),
        section = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            letterSpacing = 2.5.sp,
        ),
        title = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            letterSpacing = 0.1.sp,
        ),
        body = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        caption = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 0.3.sp,
        ),
        lyricCurrent = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 56.sp,
            lineHeight = 64.sp,
            letterSpacing = (-1).sp,
        ),
        lyricNeighbor = TextStyle(
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            letterSpacing = (-0.2).sp,
        ),
)

/** Accessor sugar: `Musico.type.section`, `Musico.extras.mint`, … */
data class MusicoTokens(
    val extras: MusicoExtras,
    val type: MusicoTypography,
)

// Plain defaults (NOT reading other CompositionLocals — their .value is
// composable-restricted and a staticCompositionLocalOf factory is not one).
private val DefaultExtras = MusicoExtras(
    bg0 = MusicoColors.bg0,
    bg1 = MusicoColors.bg1,
    bg2 = MusicoColors.bg2,
    inkDim = MusicoColors.inkDim,
    inkFaint = MusicoColors.inkFaint,
    mint = MusicoColors.mint,
    coral = MusicoColors.coral,
    amber = MusicoColors.amber,
)

val LocalMusicoTokens = staticCompositionLocalOf {
    MusicoTokens(extras = DefaultExtras, type = DefaultTypography)
}

val Musico: MusicoTokens
    @Composable get() = LocalMusicoTokens.current

private val DarkScheme = darkColorScheme(
    primary = MusicoColors.violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B2A80),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = MusicoColors.mint,
    onSecondary = Color(0xFF05291D),
    secondaryContainer = Color(0xFF1E4C3E),
    onSecondaryContainer = Color(0xFFC9F7E6),
    tertiary = MusicoColors.coral,
    onTertiary = Color(0xFF3A0A10),
    background = MusicoColors.bg0,
    onBackground = MusicoColors.ink,
    surface = MusicoColors.bg0,
    onSurface = MusicoColors.ink,
    surfaceVariant = MusicoColors.bg2,
    onSurfaceVariant = MusicoColors.inkDim,
    surfaceContainer = MusicoColors.bg1,
    surfaceContainerHigh = MusicoColors.bg2,
    surfaceContainerHighest = Color(0xFF262638),
    outline = Color(0xFF3A3850),
    outlineVariant = Color(0xFF2A2840),
    error = MusicoColors.coral,
    onError = Color(0xFF3A0A10),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B3DF6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF1E6E56),
    onSecondary = Color.White,
    tertiary = Color(0xFFB3261E),
    background = MusicoColors.lightBg0,
    onBackground = MusicoColors.lightInk,
    surface = MusicoColors.lightBg0,
    onSurface = MusicoColors.lightInk,
    surfaceVariant = Color(0xFFE8E6F2),
    onSurfaceVariant = MusicoColors.lightInkDim,
    surfaceContainer = MusicoColors.lightBg1,
    surfaceContainerHigh = Color(0xFFF0EEF8),
    outline = Color(0xFFCAC8D8),
    outlineVariant = Color(0xFFDEDCeA),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/**
 * The app theme. The music experience is designed dark-first (lyrics and
 * player genuinely only work on dark), so light mode keeps the same layout
 * with an inverted surface treatment.
 */
@Composable
fun MusicoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) DarkScheme else LightScheme
    val extras = if (dark) {
        MusicoExtras(
            bg0 = MusicoColors.bg0,
            bg1 = MusicoColors.bg1,
            bg2 = MusicoColors.bg2,
            inkDim = MusicoColors.inkDim,
            inkFaint = MusicoColors.inkFaint,
            mint = MusicoColors.mint,
            coral = MusicoColors.coral,
            amber = MusicoColors.amber,
        )
    } else {
        MusicoExtras(
            bg0 = MusicoColors.lightBg0,
            bg1 = MusicoColors.lightBg1,
            bg2 = Color(0xFFEDEBF6),
            inkDim = MusicoColors.lightInkDim,
            inkFaint = Color(0xFF8B899C),
            mint = Color(0xFF1E6E56),
            coral = Color(0xFFB3261E),
            amber = Color(0xFF9A6B00),
        )
    }
    val tokens = MusicoTokens(extras = extras, type = LocalMusicoTypography.current)
    androidx.compose.runtime.CompositionLocalProvider(
        LocalMusicoTokens provides tokens,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}
