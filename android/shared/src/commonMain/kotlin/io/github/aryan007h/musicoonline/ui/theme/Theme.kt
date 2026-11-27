package io.github.aryan007h.musicoonline.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * User-selectable appearance mode following Material 3 Expressive adaptive theming.
 */
enum class ThemeMode(val id: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    fun resolveDark(systemInDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemInDarkTheme
        LIGHT -> false
        DARK -> true
    }

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

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
    val squircle = RoundedCornerShape(22.dp)
    val card = RoundedCornerShape(24.dp)
    val hero = RoundedCornerShape(28.dp)
    val pillDock = RoundedCornerShape(28.dp)
    val cardLarge = RoundedCornerShape(32.dp)
    val chip = RoundedCornerShape(999.dp)
    val pill = RoundedCornerShape(999.dp)
    val sheet = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp)

    // Material 3 Expressive segmented list item shapes (outer 24dp / inner 6dp)
    val segmentedTop = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
    val segmentedMiddle = RoundedCornerShape(6.dp)
    val segmentedBottom = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    val segmentedSingle = RoundedCornerShape(24.dp)
}

private val ExpressiveMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

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
    val isDark: Boolean = true,
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
        fontWeight = FontWeight.Black,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.8).sp,
    ),
    headline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.3).sp,
    ),
    section = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 18.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.25).sp,
    ),
    overline = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp,
    ),
    title = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp,
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
        letterSpacing = 0.2.sp,
    ),
    tabularCaption = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.15.sp,
        fontFeatureSettings = "tnum",
    ),
    lyricCurrent = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Black,
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

private val ExpressiveMaterialTypography = Typography(
    displayLarge = DefaultTypography.display,
    headlineLarge = DefaultTypography.headline,
    headlineMedium = DefaultTypography.headline.copy(fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = DefaultTypography.titleLarge,
    titleMedium = DefaultTypography.title,
    bodyLarge = DefaultTypography.body.copy(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = DefaultTypography.body,
    bodySmall = DefaultTypography.caption,
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = DefaultTypography.caption.copy(fontWeight = FontWeight.SemiBold),
    labelSmall = DefaultTypography.overline,
)

data class MusicoTokens(
    val extras: MusicoExtras,
    val type: MusicoTypography,
)

private val DefaultExtras = Palette.NEBULA.toExtras(isDark = true)

private val DefaultTokens = MusicoTokens(extras = DefaultExtras, type = DefaultTypography)

val LocalMusicoTokens = staticCompositionLocalOf { DefaultTokens }

val Musico: MusicoTokens
    @Composable get() = LocalMusicoTokens.current

internal fun Palette.toExtras(isDark: Boolean): MusicoExtras = if (isDark) {
    MusicoExtras(
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
        isDark = true,
    )
} else {
    MusicoExtras(
        bg0 = light.background,
        bg1 = light.surfaceContainerLowest,
        bg2 = light.surfaceContainerHigh,
        inkDim = light.inkDim,
        inkFaint = light.inkFaint,
        primary = light.primary,
        mint = light.accent,
        onMint = light.onAccent,
        coral = light.pop,
        amber = light.tertiary,
        support = light.support,
        isDark = false,
    )
}

internal fun Palette.toDarkScheme(): ColorScheme = darkColorScheme(
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
    surfaceContainerHighest = lerp(bg2, Color.White, 0.08f),
    outline = inkFaint,
    outlineVariant = lerp(bg2, inkFaint, 0.34f),
    error = pop,
    onError = Color(0xFF3E070D),
)

internal fun paletteLightScheme(p: Palette): ColorScheme = lightColorScheme(
    primary = p.light.primary,
    onPrimary = p.light.onPrimary,
    primaryContainer = p.light.primaryContainer,
    onPrimaryContainer = p.light.onPrimaryContainer,
    secondary = p.light.secondary,
    onSecondary = p.light.onSecondary,
    secondaryContainer = p.light.secondaryContainer,
    onSecondaryContainer = p.light.onSecondaryContainer,
    tertiary = p.light.tertiary,
    onTertiary = p.light.onTertiary,
    tertiaryContainer = p.light.tertiaryContainer,
    onTertiaryContainer = p.light.onTertiaryContainer,
    background = p.light.background,
    onBackground = p.light.onBackground,
    surface = p.light.background,
    onSurface = p.light.onBackground,
    surfaceVariant = p.light.surfaceVariant,
    onSurfaceVariant = p.light.onSurfaceVariant,
    surfaceContainerLowest = p.light.surfaceContainerLowest,
    surfaceContainerLow = p.light.surfaceContainerLow,
    surfaceContainer = p.light.surfaceContainer,
    surfaceContainerHigh = p.light.surfaceContainerHigh,
    surfaceContainerHighest = p.light.surfaceContainerHighest,
    outline = p.light.outline,
    outlineVariant = p.light.outlineVariant,
    error = p.light.pop,
    onError = Color.White,
)

@Composable
fun MusicoTheme(
    palette: Palette = Palette.NEBULA,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onDarkResolved: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = themeMode.resolveDark(systemDark)
    val scheme = if (dark) palette.toDarkScheme() else paletteLightScheme(palette)
    val extras = palette.toExtras(isDark = dark)
    val tokens = MusicoTokens(extras = extras, type = DefaultTypography)

    SideEffect {
        onDarkResolved?.invoke(dark)
    }

    CompositionLocalProvider(
        LocalMusicoTokens provides tokens,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = ExpressiveMaterialTypography,
            shapes = ExpressiveMaterialShapes,
            content = content,
        )
    }
}
