package io.github.aryan007h.musicoonline.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import io.github.aryan007h.musicoonline.core.OnlineTrack

/**
 * Pixel-native Material 3 Expressive full-screen player backdrops.
 *
 * Strictly flat solid tonal surfaces with smooth color transitions — zero
 * blurs, zero radial/linear gradients, and zero animated glow orbs.
 */
@Composable
fun PlayerBackdrop(
    backdrop: String,
    track: OnlineTrack?,
    modifier: Modifier = Modifier,
    atmosphere: ArtworkAtmosphere? = null,
    isPlaying: Boolean = true,
) {
    val resolvedAtmosphere = atmosphere ?: rememberArtworkAtmosphere(track?.thumbnail)
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceLow = MaterialTheme.colorScheme.surfaceContainerLow
    val surfaceMid = MaterialTheme.colorScheme.surfaceContainer
    val bgColor = MaterialTheme.colorScheme.background

    val targetColor = when (backdrop) {
        "black" -> Color(0xFF0A0A0E)
        "aurora" -> surfaceLow
        "rain" -> surfaceMid
        "artwork" -> lerp(surfaceLow, resolvedAtmosphere.base, 0.34f)
        else -> lerp(bgColor, resolvedAtmosphere.base, 0.24f)
    }

    val animatedColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 480),
        label = "m3ePlayerBackdropColor",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedColor),
    )
}

@Composable
fun FluidMeshBackdrop(
    atmosphere: ArtworkAtmosphere,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
) {
    PlayerBackdrop(
        backdrop = "mesh",
        track = null,
        modifier = modifier,
        atmosphere = atmosphere,
        isPlaying = isPlaying,
    )
}
