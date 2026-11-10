package io.github.aryan007h.musicoonline.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Nebula fallback palette (the desktop accent) for devices without Material You (API < 31). */
private val LightColors = lightColorScheme(
    primary = Color(0xFF6C4DF6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFCBBEFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    background = Color(0xFF101018),
    onBackground = Color(0xFFE4E1EC),
    surface = Color(0xFF101018),
    onSurface = Color(0xFFE4E1EC),
    surfaceVariant = Color(0xFF232330),
    onSurfaceVariant = Color(0xFFC8C5D4),
    outline = Color(0xFF928F9E),
)

/** Material You when available, otherwise the nebula palette, matching the desktop. */
@Composable
fun MusicoTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (dark) {
        DarkColors
    } else {
        LightColors
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
