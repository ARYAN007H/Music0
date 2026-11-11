package io.github.aryan007h.musicoonline.ui.theme

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

// ── Motion lives in Motion.kt (the one MusicoMotion vocabulary). ──────────

// ── Shapes: big radii everywhere; cards nearly pill out. ─────────────────

object MusicoShapes {
    val card = RoundedCornerShape(24.dp)
    val cardLarge = RoundedCornerShape(32.dp)
    val chip = RoundedCornerShape(999.dp) // full pill
    val sheet = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
    val hero = RoundedCornerShape(28.dp)
}

/** Extra expressive tokens not covered by the M3 color scheme — palette-driven. */
data class MusicoExtras(
    val bg0: Color,
    val bg1: Color,
    val bg2: Color,
    val inkDim: Color,
    val inkFaint: Color,
    val primary: Color,
    val mint: Color, // counterpoint accent (playing EQ, positive fills)
    val onMint: Color,
    val coral: Color, // warm pop (likes, errors)
    val amber: Color,
    val support: Color,
    val cardRadius: Dp = 24.dp,
    val cardLargeRadius: Dp = 32.dp,
)

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

private val DefaultExtras = Palette.NEBULA.toExtras()

private val DefaultTokens = MusicoTokens(extras = DefaultExtras, type = DefaultTypography)

val LocalMusicoTokens = staticCompositionLocalOf { DefaultTokens }

val Musico: MusicoTokens
    @Composable get() = LocalMusicoTokens.current

private fun Palette.toExtras(): MusicoExtras = MusicoExtras(
    bg0 = bg0,
    bg1 = bg1,
    bg2 = bg2,
    inkDim = inkDim,
    inkFaint = inkFaint,
    primary = primary,
    mint = accent,
    onMint = onAccent,
    coral = pop,
    amber = accent,
    support = support,
)

private fun Palette.toDarkScheme() = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = accent,
    onSecondary = onAccent,
    secondaryContainer = primaryContainer,
    onSecondaryContainer = onPrimaryContainer,
    tertiary = pop,
    onTertiary = onPrimary,
    background = bg0,
    onBackground = ink,
    surface = bg0,
    onSurface = ink,
    surfaceVariant = bg2,
    onSurfaceVariant = inkDim,
    surfaceContainer = bg1,
    surfaceContainerHigh = bg2,
    surfaceContainerHighest = bg2,
    outline = inkFaint,
    outlineVariant = bg2,
    error = pop,
    onError = onPrimary,
)

/**
 * Light mode keeps its own fixed neutral treatment (the app is designed
 * dark-first); the chosen palette only contributes the primary accent so
 * switching themes still reads in light mode.
 */
private fun paletteLightScheme(p: Palette) = lightColorScheme(
    primary = p.primary,
    onPrimary = p.onPrimary,
    primaryContainer = p.primaryContainer,
    onPrimaryContainer = p.onPrimaryContainer,
    secondary = p.accent,
    onSecondary = p.onAccent,
    tertiary = p.pop,
    background = Color(0xFFF7F6FB),
    onBackground = Color(0xFF191826),
    surface = Color(0xFFF7F6FB),
    onSurface = Color(0xFF191826),
    surfaceVariant = Color(0xFFE8E6F2),
    onSurfaceVariant = Color(0xFF5D5B70),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0EEF8),
    outline = Color(0xFFCAC8D8),
    outlineVariant = Color(0xFFDEDCEA),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

/**
 * The app theme, parameterized by the user-selected [Palette]. Dark-first:
 * the player/lyrics genuinely only work on dark, so the palette always
 * drives the dark scheme and light mode is a neutral fallback.
 */
@Composable
fun MusicoTheme(palette: Palette = Palette.NEBULA, content: @Composable () -> Unit) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = if (dark) palette.toDarkScheme() else paletteLightScheme(palette)
    val extras = if (dark) {
        palette.toExtras()
    } else {
        // Light extras: surfaces flip, accents keep identity.
        MusicoExtras(
            bg0 = Color(0xFFF7F6FB),
            bg1 = Color.White,
            bg2 = Color(0xFFEDEBF6),
            inkDim = Color(0xFF5D5B70),
            inkFaint = Color(0xFF8B899C),
            primary = palette.primary,
            mint = palette.accent,
            onMint = palette.onAccent,
            coral = Color(0xFFB3261E),
            amber = Color(0xFF9A6B00),
            support = palette.support,
        )
    }
    val tokens = MusicoTokens(extras = extras, type = DefaultTypography)
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
