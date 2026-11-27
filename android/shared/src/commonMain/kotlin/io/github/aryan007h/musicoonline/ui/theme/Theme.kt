package io.github.aryan007h.musicoonline.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object MusicoSpacing {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val section: Dp = 28.dp
}

object MusicoShapes {
    val tile = RoundedCornerShape(20.dp)
    val card = RoundedCornerShape(24.dp)
    val hero = RoundedCornerShape(28.dp)
    val pillDock = RoundedCornerShape(28.dp)
    val cardLarge = RoundedCornerShape(32.dp)
    val chip = RoundedCornerShape(999.dp)
    val sheet = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)
}

data class MusicoExtras(
    val bg0: Color,
    val bg1: Color,
    val bg2: Color,
    val inkDim: Color,
    val inkFaint: Color,
    val primary: Color,
    val mint: Color,
    val onMint: Color,
    val coral: Color,
    val amber: Color,
    val support: Color,
    val cardRadius: Dp = 24.dp,
    val cardLargeRadius: Dp = 32.dp,
)

data class MusicoTypography(
    val display: TextStyle,
    val headline: TextStyle,
    val titleLarge: TextStyle,
    val section: TextStyle,
    val overline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val tabularCaption: TextStyle,
    val lyricCurrent: TextStyle,
    val lyricNeighbor: TextStyle,
    val lyricCinema: TextStyle,
    val lyricSub: TextStyle,
)

private val DefaultTypography = MusicoTypography(
    display = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    ),
    headline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.2).sp,
    ),
    section = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 2.0.sp,
    ),
    overline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.6.sp,
    ),
    title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
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
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
    ),
    tabularCaption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = "tnum",
    ),
    lyricCurrent = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 54.sp,
        lineHeight = 62.sp,
        letterSpacing = (-1.1).sp,
    ),
    lyricNeighbor = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.2).sp,
    ),
    lyricCinema = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.4).sp,
    ),
    lyricSub = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    ),
)

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

internal fun Palette.toDarkScheme() = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = accent,
    onSecondary = onAccent,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = accent,
    onTertiary = onAccent,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = bg0,
    onBackground = ink,
    surface = bg0,
    onSurface = ink,
    surfaceVariant = bg2,
    onSurfaceVariant = inkDim,
    surfaceContainerLowest = bg0,
    surfaceContainerLow = lerp(bg0, bg1, 0.58f),
    surfaceContainer = bg1,
    surfaceContainerHigh = bg2,
    surfaceContainerHighest = lerp(bg2, Color.White, 0.07f),
    outline = inkFaint,
    outlineVariant = lerp(bg2, inkFaint, 0.32f),
    error = pop,
    onError = Color(0xFF3E070D),
)

internal fun paletteLightScheme(p: Palette) = lightColorScheme(
    primary = lerp(p.primary, Color.Black, 0.28f),
    onPrimary = Color.White,
    primaryContainer = lerp(p.primary, Color.White, 0.80f),
    onPrimaryContainer = lerp(p.primary, Color.Black, 0.62f),
    secondary = lerp(p.accent, Color.Black, 0.32f),
    onSecondary = Color.White,
    secondaryContainer = lerp(p.primary, Color.White, 0.88f),
    onSecondaryContainer = Color(0xFF1D1B2A),
    tertiary = lerp(p.support, Color.Black, 0.24f),
    onTertiary = Color.White,
    tertiaryContainer = lerp(p.accent, Color.White, 0.82f),
    onTertiaryContainer = Color(0xFF102A22),
    background = Color(0xFFF7F6FB),
    onBackground = Color(0xFF191826),
    surface = Color(0xFFF7F6FB),
    onSurface = Color(0xFF191826),
    surfaceVariant = Color(0xFFE8E6F2),
    onSurfaceVariant = Color(0xFF5D5B70),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F8FD),
    surfaceContainer = Color(0xFFF3F1FA),
    surfaceContainerHigh = Color(0xFFECEAF6),
    surfaceContainerHighest = Color(0xFFE5E2F1),
    outline = Color(0xFFCAC8D8),
    outlineVariant = Color(0xFFDEDCEA),
    error = Color(0xFFB3261E),
    onError = Color.White,
)

@Composable
fun MusicoTheme(
    palette: Palette = Palette.NEBULA,
    forceDark: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dark = forceDark || androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = if (dark) palette.toDarkScheme() else paletteLightScheme(palette)
    val extras = if (dark) {
        palette.toExtras()
    } else {
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
