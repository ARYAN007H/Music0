package io.github.aryan007h.musicoonline.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.ui.theme.Musico
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Full-screen-player backdrops. Midnight is the honest default (black);
 * Rain and Aurora are hand-rolled Canvas scenes; Artwork Glow tints the
 * screen from the current cover.
 */
@Composable
fun PlayerBackdrop(backdrop: String, track: OnlineTrack?, modifier: Modifier = Modifier) {
    when (backdrop) {
        "rain" -> RainBackdrop(modifier)
        "aurora" -> AuroraBackdrop(modifier)
        "artwork" -> ArtworkGlowBackdrop(track, modifier)
        else -> Box(modifier.background(Color.Black))
    }
}

// ── Midnight (pure black — the lyrics-first choice) ───────────────────────

// ── Rain: layered falling drops + faint streaks. Deterministic per drop
//    (seeded), so recomposition doesn't re-randomize the weather. ──────────

private data class Drop(
    val xFrac: Float,
    val startYFrac: Float,
    val speedFrac: Float, // screen heights per second
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
        // Monotonic elapsed seconds (never wraps) — a looping tween would
        // teleport every drop when its cycle restarts.
        var elapsed by mutableFloatStateOf(0f)
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
            // Depth: far drops are thin+dim, near drops thicker+slanted.
            drops.forEachIndexed { i, d ->
                val depth = i / drops.size.toFloat()
                val y = ((d.startYFrac + elapsed * d.speedFrac) % 1f) * h
                val x = ((d.xFrac + elapsed * d.drift) % 1f) * w
                val len = d.lengthFrac * h * (0.6f + depth * 0.8f)
                val slant = d.drift * 120f
                val stroke = (0.8f + depth * 1.8f)
                drawLine(
                    color = Color.White.copy(alpha = d.alpha),
                    start = Offset(x, y),
                    end = Offset(x + slant, y + len),
                    strokeWidth = stroke,
                )
            }
            // Ground mist at the bottom edge.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color(0x146C5CFF),
                    startY = h * 0.82f,
                    endY = h,
                ),
                topLeft = Offset(0f, h * 0.82f),
                size = androidx.compose.ui.geometry.Size(w, h * 0.18f),
            )
        }
    }
}

// ── Aurora: slow-drifting gradient veils, calm and huge ───────────────────

@Composable
fun AuroraBackdrop(modifier: Modifier = Modifier) {
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

        // Palette-driven veil colors, read in composition (the draw lambda
        // below is not a composable scope).
        val veilLead = Musico.extras.primary
        val veilAccent = Musico.extras.mint
        val veilSupport = Musico.extras.support
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
            veil(
                veilLead,
                cx = w * (0.3f + 0.12f * sin(phase * 2f * PI.toFloat())),
                cy = h * 0.32f,
                radiusX = w * 0.55f,
                radiusY = h * 0.16f,
                rotation = -18f,
                alpha = 0.30f,
            )
            veil(
                veilAccent,
                cx = w * (0.7f + 0.1f * cos(phase2 * 2f * PI.toFloat())),
                cy = h * 0.5f,
                radiusX = w * 0.5f,
                radiusY = h * 0.13f,
                rotation = 14f,
                alpha = 0.24f,
            )
            veil(
                veilSupport,
                cx = w * (0.5f + 0.14f * sin(phase2 * 1.4f * PI.toFloat())),
                cy = h * 0.72f,
                radiusX = w * 0.6f,
                radiusY = h * 0.15f,
                rotation = -8f,
                alpha = 0.26f,
            )
            // Vignette to keep the words readable.
            drawRect(
                brush = Brush.radialGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.45f),
                    center = Offset(w / 2f, h / 2f),
                    radius = min(w, h) * 0.9f,
                ),
            )
        }
    }
}

// ── Artwork Glow: blurred cover bleed, the "immersive" Apple look ─────────

@Composable
fun ArtworkGlowBackdrop(track: OnlineTrack?, modifier: Modifier = Modifier) {
    Box(modifier.background(Color.Black)) {
        if (track?.thumbnail != null) {
            AsyncImage(
                model = track.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blurOnModernDevices(radiusPx = 64f),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(listOf(Musico.extras.bg1, Color.Black)),
                    ),
            )
        }
    }
}

/**
 * Real gaussian blur via RenderEffect on API 31+; below that the unblurred
 * image under a dark scrim still reads fine (checked at runtime, not lint).
 */
private fun Modifier.blurOnModernDevices(radiusPx: Float): Modifier = this.then(
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        Modifier.graphicsLayer {
            renderEffect = android.graphics.RenderEffect
                .createBlurEffect(radiusPx, radiusPx, android.graphics.Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        }
    } else {
        Modifier
    },
)
