package io.github.aryan007h.musicoonline.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.platform.platformBlur
import io.github.aryan007h.musicoonline.ui.theme.Musico
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen-player backdrops.
 * - "mesh": Living Album-Art Fluid Mesh — 4 organic cover-colored light orbs
 *   gliding on independent Lissajous paths whose drift velocity breathes with
 *   playback (1.0x playing, 0.22x paused — zero phase jumps).
 * - "artwork": Blurred cover bleed with gentle atmospheric drift.
 * - "aurora": Slow-drifting gradient veils.
 * - "rain": Layered falling drops + mist.
 * - "black": Pure OLED midnight black.
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
    when (backdrop) {
        "mesh" -> FluidMeshBackdrop(resolvedAtmosphere, isPlaying, modifier)
        "rain" -> RainBackdrop(modifier)
        "aurora" -> AuroraBackdrop(resolvedAtmosphere, modifier)
        "artwork" -> ArtworkGlowBackdrop(track, resolvedAtmosphere, modifier)
        else -> Box(modifier.background(Color.Black))
    }
}

// ── Living Fluid Mesh: playback-aware cover light canvas ───────────────────

@Composable
fun FluidMeshBackdrop(
    atmosphere: ArtworkAtmosphere,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val velocity by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.22f,
        animationSpec = tween(900),
        label = "meshVelocity",
    )
    val currentVelocity by rememberUpdatedState(velocity)

    // Smooth integrated phase clock: changing playback velocity slows or
    // accelerates orbital drift continuously with zero jumps.
    var phaseSecs by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                lastNanos = now
                phaseSecs += dt * currentVelocity
            }
        }
    }

    val base = atmosphere.base
    val primary = atmosphere.orbPrimary
    val secondary = atmosphere.orbSecondary
    val glow = atmosphere.glow
    val intensity = 0.86f + 0.14f * velocity

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val diag = max(w, h)
        val t = phaseSecs

        // Deep velvet base
        drawRect(
            brush = Brush.verticalGradient(
                0f to base,
                0.55f to Color(
                    red = base.red * 0.65f,
                    green = base.green * 0.65f,
                    blue = base.blue * 0.65f,
                    alpha = 1f,
                ),
                1f to Color(0xFF05050A),
            ),
        )

        // Orb 1: Large primary mass drifting across upper-left
        val c1 = Offset(
            x = w * (0.26f + 0.24f * sin(t * 0.23f)),
            y = h * (0.24f + 0.16f * cos(t * 0.19f)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to primary.copy(alpha = 0.64f * intensity),
                0.5f to primary.copy(alpha = 0.24f * intensity),
                1f to Color.Transparent,
                center = c1,
                radius = diag * (0.64f + 0.05f * sin(t * 0.31f)),
            ),
            radius = diag * 0.72f,
            center = c1,
        )

        // Orb 2: Secondary counter-weight orbiting lower-right
        val c2 = Offset(
            x = w * (0.76f + 0.22f * cos(t * 0.17f + 1.1f)),
            y = h * (0.68f + 0.18f * sin(t * 0.21f + 0.6f)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to secondary.copy(alpha = 0.58f * intensity),
                0.52f to secondary.copy(alpha = 0.21f * intensity),
                1f to Color.Transparent,
                center = c2,
                radius = diag * (0.60f + 0.06f * cos(t * 0.27f)),
            ),
            radius = diag * 0.68f,
            center = c2,
        )

        // Orb 3: Luminous bloom floating across the center lyric zone
        val c3 = Offset(
            x = w * (0.52f + 0.28f * sin(t * 0.13f + 2.4f)),
            y = h * (0.44f + 0.14f * cos(t * 0.16f + 1.8f)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to glow.copy(alpha = 0.30f * intensity),
                0.48f to primary.copy(alpha = 0.12f * intensity),
                1f to Color.Transparent,
                center = c3,
                radius = diag * 0.50f,
            ),
            radius = diag * 0.55f,
            center = c3,
        )

        // Orb 4: Deep lower-left anchor
        val c4 = Offset(
            x = w * (0.18f + 0.18f * cos(t * 0.14f + 3.8f)),
            y = h * (0.82f + 0.10f * sin(t * 0.18f)),
        )
        drawCircle(
            brush = Brush.radialGradient(
                0f to primary.copy(alpha = 0.42f * intensity),
                0.55f to secondary.copy(alpha = 0.14f * intensity),
                1f to Color.Transparent,
                center = c4,
                radius = diag * 0.48f,
            ),
            radius = diag * 0.52f,
            center = c4,
        )

        // Calibrated readability scrim
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.32f),
                0.22f to Color.Black.copy(alpha = 0.16f),
                0.72f to Color.Black.copy(alpha = 0.20f),
                1f to Color.Black.copy(alpha = 0.52f),
            ),
        )
    }
}

// ── Rain ──────────────────────────────────────────────────────────────────

private data class Drop(
    val xFrac: Float,
    val startYFrac: Float,
    val speedFrac: Float,
    val lengthFrac: Float,
    val alpha: Float,
    val drift: Float,
)

@Composable
fun RainBackdrop(modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black)) {
        val drops = remember {
            val rng = Random(0x51EC)
            List(120) {
                Drop(
                    xFrac = rng.nextFloat(),
                    startYFrac = rng.nextFloat(),
                    speedFrac = 0.55f + rng.nextFloat() * 0.75f,
                    lengthFrac = 0.03f + rng.nextFloat() * 0.09f,
                    alpha = 0.08f + rng.nextFloat() * 0.22f,
                    drift = -0.06f + rng.nextFloat() * 0.12f,
                )
            }
        }
        var elapsed by remember { mutableFloatStateOf(0f) }
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    elapsed = (now - start) / 1_000_000_000f
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drops.forEachIndexed { i, d ->
                val depth = i / drops.size.toFloat()
                val y = ((d.startYFrac + elapsed * d.speedFrac) % 1f) * h
                val x = ((d.xFrac + elapsed * d.drift) % 1f) * w
                val len = d.lengthFrac * h * (0.6f + depth * 0.8f)
                val slant = d.drift * 120f
                val stroke = 0.8f + depth * 1.8f
                drawLine(
                    color = Color.White.copy(alpha = d.alpha),
                    start = Offset(x, y),
                    end = Offset(x + slant, y + len),
                    strokeWidth = stroke,
                )
            }
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0x1E6C5CFF),
                    startY = h * 0.80f,
                    endY = h,
                ),
                topLeft = Offset(0f, h * 0.80f),
                size = Size(w, h * 0.20f),
            )
        }
    }
}

// ── Aurora ────────────────────────────────────────────────────────────────

@Composable
fun AuroraBackdrop(
    atmosphere: ArtworkAtmosphere? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(Color.Black)) {
        val frame = rememberInfiniteTransition(label = "aurora")
        val phase by frame.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Reverse),
            label = "phase",
        )
        val phase2 by frame.animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(tween(19000, easing = LinearEasing), RepeatMode.Reverse),
            label = "phase2",
        )

        val veilLead = atmosphere?.orbPrimary ?: Musico.extras.primary
        val veilAccent = atmosphere?.glow ?: Musico.extras.mint
        val veilSupport = atmosphere?.orbSecondary ?: Musico.extras.support
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            fun veil(color: Color, cx: Float, cy: Float, radiusX: Float, radiusY: Float, rotation: Float, alpha: Float) {
                val path = Path()
                path.moveTo(cx - radiusX, cy)
                path.quadraticTo(cx, cy - radiusY, cx + radiusX, cy)
                path.quadraticTo(cx, cy + radiusY, cx - radiusX, cy)
                path.close()
                rotate(rotation, pivot = Offset(cx, cy)) {
                    drawPath(path, color.copy(alpha = alpha))
                }
            }
            veil(veilLead, w * (0.3f + 0.12f * sin(phase * 2f * PI.toFloat())), h * 0.32f, w * 0.58f, h * 0.17f, -18f, 0.32f)
            veil(veilAccent, w * (0.7f + 0.1f * cos(phase2 * 2f * PI.toFloat())), h * 0.5f, w * 0.52f, h * 0.14f, 14f, 0.24f)
            veil(veilSupport, w * (0.5f + 0.14f * sin(phase2 * 1.4f * PI.toFloat())), h * 0.72f, w * 0.62f, h * 0.16f, -8f, 0.28f)
            drawRect(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.50f),
                    center = Offset(w / 2f, h / 2f),
                    radius = min(w, h) * 0.9f,
                ),
            )
        }
    }
}

// ── Artwork Glow ──────────────────────────────────────────────────────────

@Composable
fun ArtworkGlowBackdrop(
    track: OnlineTrack?,
    atmosphere: ArtworkAtmosphere? = null,
    modifier: Modifier = Modifier,
) {
    val drift = rememberInfiniteTransition(label = "artDrift")
    val scale by drift.animateFloat(
        initialValue = 1.12f,
        targetValue = 1.24f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Reverse),
        label = "artDriftScale",
    )
    Box(modifier.background(atmosphere?.base ?: Color.Black)) {
        if (track?.thumbnail != null) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .platformBlur(22f),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.42f),
                            0.5f to Color.Black.copy(alpha = 0.54f),
                            1f to Color.Black.copy(alpha = 0.74f),
                        ),
                    ),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(atmosphere?.orbPrimary?.copy(alpha = 0.35f) ?: Musico.extras.bg1, Color.Black)),
                    ),
            )
        }
    }
}
