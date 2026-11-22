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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
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
 * - "mesh": Living Album-Art Fluid Mesh — 4 Gaussian cover-colored light orbs
 *   gliding on independent Lissajous paths whose drift velocity breathes with
 *   playback (1.0x playing, 0.22x paused — zero phase jumps).
 * - "artwork": Blurred cover bleed with gentle atmospheric drift.
 * - "aurora": Volumetric Nordic boreal curtains with vertical ray striations
 *   and an arctic starfield over deep twilight sky.
 * - "rain": Cinematic rain-on-clear-dark-glass with defocused bokeh, layered
 *   tapered rain streaks, refracting glass water droplets, and sliding rivulets.
 * - "black": Pure OLED midnight dark with whisper-subtle zenith depth.
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
        "rain" -> RainBackdrop(modifier, resolvedAtmosphere, isPlaying)
        "aurora" -> AuroraBackdrop(resolvedAtmosphere, modifier, isPlaying)
        "artwork" -> ArtworkGlowBackdrop(track, resolvedAtmosphere, modifier)
        else -> MidnightBackdrop(modifier)
    }
}

@Composable
private fun rememberPlaybackPhase(isPlaying: Boolean, pausedFactor: Float = 0.24f): Pair<Float, Float> {
    val velocity by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else pausedFactor,
        animationSpec = tween(900),
        label = "backdropVelocity",
    )
    val currentVelocity by rememberUpdatedState(velocity)
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
    return phaseSecs to velocity
}

// ── Midnight OLED ──────────────────────────────────────────────────────────

@Composable
private fun MidnightBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize().background(Color(0xFF040408))) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val r = (max(w, h) * 0.72f).coerceAtLeast(1f)
        drawRect(
            brush = Brush.radialGradient(
                0.00f to Color(0xFF101322).copy(alpha = 0.36f),
                0.35f to Color(0xFF0B0D18).copy(alpha = 0.18f),
                0.70f to Color(0xFF06070D).copy(alpha = 0.05f),
                1.00f to Color.Transparent,
                center = Offset(w * 0.5f, h * 0.12f),
                radius = r,
            ),
        )
    }
}

// ── Living Fluid Mesh: playback-aware cover light canvas ───────────────────

@Composable
fun FluidMeshBackdrop(
    atmosphere: ArtworkAtmosphere,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val (phaseSecs, velocity) = rememberPlaybackPhase(isPlaying = isPlaying, pausedFactor = 0.22f)

    val base = atmosphere.base
    val primary = atmosphere.orbPrimary
    val secondary = atmosphere.orbSecondary
    val glow = atmosphere.glow
    val intensity = 0.86f + 0.14f * velocity

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val diag = max(w, h).coerceAtLeast(1f)
        val t = phaseSecs

        // Deep velvet base with smooth multi-stop transition to OLED midnight
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to base,
                0.32f to lerp(base, Color(0xFF070710), 0.24f),
                0.68f to lerp(base, Color(0xFF05050A), 0.62f),
                1.00f to Color(0xFF05050A),
            ),
        )

        fun gaussianOrb(center: Offset, radius: Float, core: Color, mid: Color, peakAlpha: Float) {
            val safeR = radius.coerceAtLeast(1f)
            val a = peakAlpha.coerceIn(0f, 1f)
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to core.copy(alpha = a),
                    0.26f to core.copy(alpha = a * 0.68f),
                    0.52f to mid.copy(alpha = a * 0.30f),
                    0.78f to mid.copy(alpha = a * 0.06f),
                    1.00f to Color.Transparent,
                    center = center,
                    radius = safeR,
                ),
                radius = safeR,
                center = center,
            )
        }

        // Orb 1: Large primary mass drifting across upper-left
        gaussianOrb(
            center = Offset(
                x = w * (0.26f + 0.22f * sin(t * 0.23f)),
                y = h * (0.24f + 0.15f * cos(t * 0.19f)),
            ),
            radius = diag * (0.68f + 0.05f * sin(t * 0.31f)),
            core = primary,
            mid = primary,
            peakAlpha = 0.62f * intensity,
        )

        // Orb 2: Secondary counter-weight orbiting lower-right
        gaussianOrb(
            center = Offset(
                x = w * (0.76f + 0.20f * cos(t * 0.17f + 1.1f)),
                y = h * (0.68f + 0.16f * sin(t * 0.21f + 0.6f)),
            ),
            radius = diag * (0.64f + 0.05f * cos(t * 0.27f)),
            core = secondary,
            mid = secondary,
            peakAlpha = 0.56f * intensity,
        )

        // Orb 3: Luminous bloom floating across the center lyric zone
        gaussianOrb(
            center = Offset(
                x = w * (0.52f + 0.26f * sin(t * 0.13f + 2.4f)),
                y = h * (0.44f + 0.14f * cos(t * 0.16f + 1.8f)),
            ),
            radius = diag * 0.54f,
            core = glow,
            mid = primary,
            peakAlpha = 0.30f * intensity,
        )

        // Orb 4: Deep lower-left anchor
        gaussianOrb(
            center = Offset(
                x = w * (0.18f + 0.16f * cos(t * 0.14f + 3.8f)),
                y = h * (0.82f + 0.09f * sin(t * 0.18f)),
            ),
            radius = diag * 0.50f,
            core = primary,
            mid = secondary,
            peakAlpha = 0.40f * intensity,
        )

        // Smooth cubic readability scrim — zero banding behind chrome or lyrics
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to Color.Black.copy(alpha = 0.34f),
                0.14f to Color.Black.copy(alpha = 0.18f),
                0.30f to Color.Black.copy(alpha = 0.12f),
                0.62f to Color.Black.copy(alpha = 0.14f),
                0.82f to Color.Black.copy(alpha = 0.30f),
                1.00f to Color.Black.copy(alpha = 0.54f),
            ),
        )
    }
}

// ── Rain on Clear Dark Glass: bokeh + falling rain + glass droplets & rivulets ──

private data class BokehLight(
    val xFrac: Float,
    val yFrac: Float,
    val radiusFrac: Float,
    val hueIndex: Int,
    val alpha: Float,
    val driftSpeed: Float,
    val phase: Float,
)

private data class RainStreak(
    val xFrac: Float,
    val startYFrac: Float,
    val speedFrac: Float,
    val lengthFrac: Float,
    val alpha: Float,
    val strokePx: Float,
    val isForeground: Boolean,
)

private data class GlassDroplet(
    val xFrac: Float,
    val yFrac: Float,
    val radiusFrac: Float,
    val elongation: Float,
    val glintStrength: Float,
    val tintMix: Float,
)

private data class GlassRivulet(
    val xFrac: Float,
    val startYFrac: Float,
    val speedFrac: Float,
    val trailFrac: Float,
    val wobbleAmpFrac: Float,
    val wobbleFreq: Float,
    val phase: Float,
    val headRadiusFrac: Float,
)

@Composable
fun RainBackdrop(
    modifier: Modifier = Modifier,
    atmosphere: ArtworkAtmosphere? = null,
    isPlaying: Boolean = true,
) {
    val (elapsed, _) = rememberPlaybackPhase(isPlaying = isPlaying, pausedFactor = 0.28f)

    val bokehLights = remember {
        val rng = Random(0x7A1E)
        List(12) { idx ->
            BokehLight(
                xFrac = 0.08f + rng.nextFloat() * 0.84f,
                yFrac = 0.10f + rng.nextFloat() * 0.78f,
                radiusFrac = 0.14f + rng.nextFloat() * 0.20f,
                hueIndex = idx % 4,
                alpha = 0.12f + rng.nextFloat() * 0.14f,
                driftSpeed = 0.08f + rng.nextFloat() * 0.10f,
                phase = rng.nextFloat() * 6.283f,
            )
        }
    }

    val streaks = remember {
        val rng = Random(0x51EC)
        List(46) { i ->
            val fg = i >= 26
            RainStreak(
                xFrac = rng.nextFloat(),
                startYFrac = rng.nextFloat(),
                speedFrac = if (fg) 0.58f + rng.nextFloat() * 0.34f else 0.28f + rng.nextFloat() * 0.22f,
                lengthFrac = if (fg) 0.055f + rng.nextFloat() * 0.060f else 0.028f + rng.nextFloat() * 0.036f,
                alpha = if (fg) 0.10f + rng.nextFloat() * 0.12f else 0.05f + rng.nextFloat() * 0.07f,
                strokePx = if (fg) 1.5f + rng.nextFloat() * 1.1f else 0.9f + rng.nextFloat() * 0.6f,
                isForeground = fg,
            )
        }
    }

    val droplets = remember {
        val rng = Random(0xD209)
        List(34) {
            GlassDroplet(
                xFrac = 0.04f + rng.nextFloat() * 0.92f,
                yFrac = 0.05f + rng.nextFloat() * 0.88f,
                radiusFrac = 0.0045f + rng.nextFloat() * 0.0105f,
                elongation = 1.06f + rng.nextFloat() * 0.26f,
                glintStrength = 0.42f + rng.nextFloat() * 0.40f,
                tintMix = rng.nextFloat(),
            )
        }
    }

    val rivulets = remember {
        val rng = Random(0x217F)
        List(7) { idx ->
            GlassRivulet(
                xFrac = (idx + 0.5f) / 7f + (rng.nextFloat() - 0.5f) * 0.08f,
                startYFrac = rng.nextFloat(),
                speedFrac = 0.022f + rng.nextFloat() * 0.026f,
                trailFrac = 0.14f + rng.nextFloat() * 0.18f,
                wobbleAmpFrac = 0.005f + rng.nextFloat() * 0.008f,
                wobbleFreq = 12f + rng.nextFloat() * 10f,
                phase = rng.nextFloat() * 6.283f,
                headRadiusFrac = 0.0075f + rng.nextFloat() * 0.0055f,
            )
        }
    }

    val accentA = atmosphere?.orbPrimary ?: Color(0xFF4A7BFF)
    val accentB = atmosphere?.glow ?: Color(0xFF53E6D8)
    val warmLamp = Color(0xFFE8A45C)
    val coolStreet = Color(0xFF4C8DFF)

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val minDim = min(w, h).coerceAtLeast(1f)
        val diag = max(w, h).coerceAtLeast(1f)

        // 1. Deep dark-glass night backdrop (subtle cool slate-indigo twilight)
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to Color(0xFF040711),
                0.35f to Color(0xFF070E1D),
                0.70f to Color(0xFF060B16),
                1.00f to Color(0xFF04060C),
            ),
        )

        // 2. Softly defocused ambient city & album bokeh lights behind the wet glass
        bokehLights.forEach { b ->
            val color = when (b.hueIndex) {
                0 -> lerp(coolStreet, accentA, 0.55f)
                1 -> lerp(warmLamp, accentB, 0.30f)
                2 -> lerp(accentB, Color(0xFF3FD8C4), 0.50f)
                else -> lerp(accentA, Color(0xFF7B5CFF), 0.45f)
            }
            val cx = w * (b.xFrac + 0.018f * sin(elapsed * b.driftSpeed + b.phase))
            val cy = h * (b.yFrac + 0.014f * cos(elapsed * b.driftSpeed * 0.8f + b.phase))
            val r = (diag * b.radiusFrac).coerceAtLeast(1f)
            val pulse = b.alpha * (0.88f + 0.12f * sin(elapsed * 0.5f + b.phase))
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to color.copy(alpha = pulse),
                    0.35f to color.copy(alpha = pulse * 0.62f),
                    0.68f to color.copy(alpha = pulse * 0.18f),
                    0.90f to color.copy(alpha = pulse * 0.03f),
                    1.00f to Color.Transparent,
                    center = Offset(cx, cy),
                    radius = r,
                ),
                radius = r,
                center = Offset(cx, cy),
            )
        }

        // 3. Soft glass condensation mist near outer perimeter (zero hard edge)
        drawRect(
            brush = Brush.radialGradient(
                0.00f to Color.Transparent,
                0.55f to Color(0xFF8FB4E8).copy(alpha = 0.015f),
                0.85f to Color(0xFF8FB4E8).copy(alpha = 0.045f),
                1.00f to Color(0xFF0A101E).copy(alpha = 0.22f),
                center = Offset(w * 0.5f, h * 0.46f),
                radius = (diag * 0.78f).coerceAtLeast(1f),
            ),
        )

        // 4. Depth-layered falling rain streaks outside the glass (tapered alpha head-to-tail)
        val windSlant = -0.055f
        streaks.forEach { s ->
            val progress = (s.startYFrac + elapsed * s.speedFrac) % 1.15f - 0.12f
            val y0 = progress * h
            val len = s.lengthFrac * h
            val x0 = ((s.xFrac + progress * windSlant + 1f) % 1f) * w
            val x1 = x0 + windSlant * len * 1.4f
            val y1 = y0 + len
            val streakTint = if (s.isForeground) Color(0xFFD8EBFF) else Color(0xFF9AC2EE)
            drawLine(
                brush = Brush.linearGradient(
                    0.00f to Color.Transparent,
                    0.45f to streakTint.copy(alpha = s.alpha * 0.35f),
                    0.85f to streakTint.copy(alpha = s.alpha),
                    1.00f to Color.White.copy(alpha = s.alpha * 0.75f),
                    start = Offset(x0, y0),
                    end = Offset(x1, y1),
                ),
                start = Offset(x0, y0),
                end = Offset(x1, y1),
                strokeWidth = s.strokePx,
                cap = StrokeCap.Round,
            )
        }

        // Helper to draw a realistic refracting 3D water bead on dark glass
        fun drawGlassBead(cx: Float, cy: Float, rx: Float, ry: Float, glint: Float, tint: Color) {
            val safeRx = rx.coerceAtLeast(1.2f)
            val safeRy = ry.coerceAtLeast(1.2f)
            // Soft contact shadow directly underneath the bead
            drawOval(
                color = Color.Black.copy(alpha = 0.34f),
                topLeft = Offset(cx - safeRx * 1.05f, cy - safeRy * 0.85f + safeRy * 0.26f),
                size = Size(safeRx * 2.10f, safeRy * 2.05f),
            )
            // Translucent glass dome catching ambient bokeh tint
            drawOval(
                brush = Brush.radialGradient(
                    0.00f to tint.copy(alpha = 0.20f),
                    0.55f to Color(0xFF142034).copy(alpha = 0.26f),
                    0.88f to Color(0xFF9EC8F5).copy(alpha = 0.16f),
                    1.00f to Color.Transparent,
                    center = Offset(cx, cy + safeRy * 0.18f),
                    radius = (max(safeRx, safeRy) * 1.15f).coerceAtLeast(1f),
                ),
                topLeft = Offset(cx - safeRx, cy - safeRy),
                size = Size(safeRx * 2f, safeRy * 2f),
            )
            // Refracted bright lower caustic crescent (light focused at bead bottom)
            val causticCenter = Offset(cx, cy + safeRy * 0.44f)
            val causticR = (safeRx * 0.88f).coerceAtLeast(1f)
            drawCircle(
                brush = Brush.radialGradient(
                    0.00f to Color.White.copy(alpha = 0.34f * glint),
                    0.45f to tint.copy(alpha = 0.24f * glint),
                    1.00f to Color.Transparent,
                    center = causticCenter,
                    radius = causticR,
                ),
                radius = causticR,
                center = causticCenter,
            )
            // Crisp upper-left specular highlight glint
            val glintR = (safeRx * 0.26f).coerceAtLeast(0.8f)
            drawCircle(
                color = Color.White.copy(alpha = (0.58f * glint).coerceIn(0f, 0.85f)),
                radius = glintR,
                center = Offset(cx - safeRx * 0.32f, cy - safeRy * 0.34f),
            )
        }

        // 5. Clinging static glass water droplets across the pane
        droplets.forEach { d ->
            val cx = d.xFrac * w
            val cy = d.yFrac * h
            val rx = (d.radiusFrac * minDim).coerceIn(1.8f, 9.5f)
            val ry = rx * d.elongation
            val beadTint = lerp(accentB, Color(0xFFAAD4FF), d.tintMix)
            drawGlassBead(cx, cy, rx, ry, d.glintStrength, beadTint)
        }

        // 6. Slow gravity-driven sliding rivulets meandering down the glass
        rivulets.forEach { r ->
            // Gentle stick-slip gravity progression
            val rawY = (r.startYFrac + elapsed * r.speedFrac) % 1.24f - 0.12f
            val stickSlip = 0.006f * sin(rawY * 32f + r.phase)
            val headYFrac = rawY + stickSlip
            val headY = headYFrac * h
            val trailLen = r.trailFrac * h

            // Segmented wet trail fading cleanly to 0 alpha at its upper tail
            val segments = 14
            for (s in 0 until segments) {
                val t0 = s.toFloat() / segments
                val t1 = (s + 1).toFloat() / segments
                val yA = headY - trailLen * (1f - t0)
                val yB = headY - trailLen * (1f - t1)
                if (yB < -12f || yA > h + 12f) continue
                val xA = (r.xFrac + r.wobbleAmpFrac * sin((yA / h) * r.wobbleFreq + r.phase)) * w
                val xB = (r.xFrac + r.wobbleAmpFrac * sin((yB / h) * r.wobbleFreq + r.phase)) * w
                // Smooth cubic fade along the trail (0 at top -> bright near head)
                val segAlpha = (t1 * t1) * 0.24f
                drawLine(
                    color = Color(0xFFC8E2FF).copy(alpha = segAlpha),
                    start = Offset(xA, yA),
                    end = Offset(xB, yB),
                    strokeWidth = (1.1f + t1 * 1.9f),
                    cap = StrokeCap.Round,
                )
            }

            // Glistening heavier lead droplet at the tip of the rivulet
            if (headY in -20f..(h + 20f)) {
                val headX = (r.xFrac + r.wobbleAmpFrac * sin(headYFrac * r.wobbleFreq + r.phase)) * w
                val rx = (r.headRadiusFrac * minDim).coerceIn(3.0f, 8.0f)
                drawGlassBead(headX, headY, rx, rx * 1.38f, 0.88f, lerp(accentB, Color.White, 0.4f))
            }
        }

        // 7. Smooth cubic readability vignette for controls & lyrics (zero cutoff edge)
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to Color.Black.copy(alpha = 0.34f),
                0.16f to Color.Black.copy(alpha = 0.10f),
                0.45f to Color.Transparent,
                0.72f to Color.Black.copy(alpha = 0.14f),
                0.88f to Color.Black.copy(alpha = 0.36f),
                1.00f to Color.Black.copy(alpha = 0.58f),
            ),
        )
    }
}

// ── Aurora Borealis: volumetric Nordic curtains + vertical rays + starfield ──

private data class ArcticStar(
    val xFrac: Float,
    val yFrac: Float,
    val radiusPx: Float,
    val baseAlpha: Float,
    val twinkleSpeed: Float,
    val phase: Float,
)

private data class AuroraCurtainSpec(
    val yCenterFrac: Float,
    val heightFrac: Float,
    val freq1: Float,
    val freq2: Float,
    val speed1: Float,
    val speed2: Float,
    val ampFrac: Float,
    val phaseOffset: Float,
    val rayCount: Int,
    val intensity: Float,
)

@Composable
fun AuroraBackdrop(
    atmosphere: ArtworkAtmosphere? = null,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = true,
) {
    val (t, _) = rememberPlaybackPhase(isPlaying = isPlaying, pausedFactor = 0.28f)

    val stars = remember {
        val rng = Random(0xA020A)
        List(44) {
            ArcticStar(
                xFrac = rng.nextFloat(),
                yFrac = rng.nextFloat() * 0.66f,
                radiusPx = 0.9f + rng.nextFloat() * 1.6f,
                baseAlpha = 0.14f + rng.nextFloat() * 0.38f,
                twinkleSpeed = 0.45f + rng.nextFloat() * 1.15f,
                phase = rng.nextFloat() * 6.283f,
            )
        }
    }

    val curtains = remember {
        listOf(
            // Distant high-altitude violet-teal veil
            AuroraCurtainSpec(
                yCenterFrac = 0.26f,
                heightFrac = 0.44f,
                freq1 = 3.1f,
                freq2 = 6.3f,
                speed1 = 0.20f,
                speed2 = -0.14f,
                ampFrac = 0.055f,
                phaseOffset = 0.4f,
                rayCount = 46,
                intensity = 0.30f,
            ),
            // Primary sweeping boreal emerald-cyan ribbon
            AuroraCurtainSpec(
                yCenterFrac = 0.42f,
                heightFrac = 0.50f,
                freq1 = 4.2f,
                freq2 = 8.7f,
                speed1 = 0.27f,
                speed2 = 0.18f,
                ampFrac = 0.068f,
                phaseOffset = 2.1f,
                rayCount = 54,
                intensity = 0.46f,
            ),
            // Lower luminous foreground fold drifting slowly
            AuroraCurtainSpec(
                yCenterFrac = 0.54f,
                heightFrac = 0.40f,
                freq1 = 3.6f,
                freq2 = 7.4f,
                speed1 = -0.22f,
                speed2 = 0.16f,
                ampFrac = 0.052f,
                phaseOffset = 4.3f,
                rayCount = 44,
                intensity = 0.34f,
            ),
        )
    }

    // Authentic boreal palette harmonized subtly (~20%) with active album atmosphere
    val artPrimary = atmosphere?.orbPrimary ?: Musico.extras.primary
    val artGlow = atmosphere?.glow ?: Musico.extras.mint
    val borealEmerald = lerp(Color(0xFF22F2B0), artGlow, 0.20f)
    val borealCyan = lerp(Color(0xFF1BD4D8), artGlow, 0.16f)
    val borealViolet = lerp(Color(0xFF8A5CFF), artPrimary, 0.26f)
    val borealMagenta = lerp(Color(0xFFD154E8), artPrimary, 0.22f)

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val diag = max(w, h).coerceAtLeast(1f)

        // 1. Deep arctic twilight sky gradient
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to Color(0xFF02040D),
                0.28f to Color(0xFF040D1F),
                0.58f to Color(0xFF071728),
                0.82f to Color(0xFF050B16),
                1.00f to Color(0xFF03050B),
            ),
        )

        // 2. Subtle crisp arctic starfield fading smoothly toward the horizon
        stars.forEach { star ->
            val horizonFade = (1f - (star.yFrac / 0.66f)).coerceIn(0f, 1f)
            val twinkle = 0.55f + 0.45f * sin(t * star.twinkleSpeed + star.phase)
            val a = (star.baseAlpha * twinkle * horizonFade).coerceIn(0f, 0.72f)
            drawCircle(
                color = Color(0xFFEAF3FF).copy(alpha = a),
                radius = star.radiusPx,
                center = Offset(star.xFrac * w, star.yFrac * h),
            )
        }

        // 3. Soft atmospheric boreal ambient bloom behind the curtains
        drawCircle(
            brush = Brush.radialGradient(
                0.00f to borealEmerald.copy(alpha = 0.18f),
                0.35f to borealCyan.copy(alpha = 0.10f),
                0.70f to borealViolet.copy(alpha = 0.04f),
                1.00f to Color.Transparent,
                center = Offset(w * (0.48f + 0.10f * sin(t * 0.15f)), h * 0.42f),
                radius = (diag * 0.68f).coerceAtLeast(1f),
            ),
            radius = (diag * 0.68f).coerceAtLeast(1f),
            center = Offset(w * (0.48f + 0.10f * sin(t * 0.15f)), h * 0.42f),
        )

        // 4. Multi-layer volumetric boreal curtains: overlapping feathered vertical rays
        // Each vertical light ray has 0.0 alpha at both top and bottom ends, and adjacent
        // rays overlap softly by ~2.4x step width so they merge into silky glowing curtains
        // with natural vertical magnetic-field striations and zero hard geometric edges.
        curtains.forEachIndexed { layerIdx, spec ->
            val count = spec.rayCount
            val stepX = w / count.toFloat()
            val rayWidth = stepX * 2.55f
            val baseHeight = h * spec.heightFrac

            val lowerColor = when (layerIdx) {
                0 -> borealCyan
                1 -> borealEmerald
                else -> lerp(borealEmerald, borealCyan, 0.45f)
            }
            val midColor = when (layerIdx) {
                0 -> borealViolet
                1 -> borealCyan
                else -> borealViolet
            }
            val upperColor = when (layerIdx) {
                0 -> borealMagenta
                1 -> borealViolet
                else -> borealMagenta
            }

            for (i in 0..count) {
                val u = i.toFloat() / count.toFloat()
                // Smooth edge taper so curtains dissolve naturally at screen sides
                val edgeEnvelope = sin(u * PI.toFloat()).coerceIn(0f, 1f)
                // Dual-harmonic ribbon wave across the sky
                val wave1 = sin(u * spec.freq1 + t * spec.speed1 + spec.phaseOffset)
                val wave2 = cos(u * spec.freq2 + t * spec.speed2 - spec.phaseOffset * 0.6f)
                val ribbonY = h * (spec.yCenterFrac + spec.ampFrac * (wave1 * 0.68f + wave2 * 0.32f))

                // Shimmering vertical ray striation modulation
                val shimmer = 0.42f + 0.58f * (
                    0.5f + 0.5f * sin(u * 21f + t * (0.65f + layerIdx * 0.18f) + spec.phaseOffset) *
                        cos(u * 9.5f - t * 0.32f)
                    )
                val rayHeight = baseHeight * (0.72f + 0.36f * shimmer)
                val rayTop = ribbonY - rayHeight * 0.78f
                val rayBottom = ribbonY + rayHeight * 0.22f
                val alpha = (spec.intensity * edgeEnvelope * shimmer).coerceIn(0f, 0.68f)
                if (alpha < 0.01f) continue

                val xCenter = u * w
                // Soft vertical light column feathered smoothly to 0.0 at top & bottom
                drawRect(
                    brush = Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.18f to upperColor.copy(alpha = alpha * 0.20f),
                        0.44f to midColor.copy(alpha = alpha * 0.44f),
                        0.68f to lowerColor.copy(alpha = alpha * 0.82f),
                        0.80f to Color.White.copy(alpha = alpha * 0.36f),
                        0.90f to lowerColor.copy(alpha = alpha * 0.22f),
                        1.00f to Color.Transparent,
                        startY = rayTop,
                        endY = rayBottom,
                    ),
                    topLeft = Offset(xCenter - rayWidth * 0.5f, rayTop),
                    size = Size(rayWidth, (rayBottom - rayTop).coerceAtLeast(1f)),
                    blendMode = BlendMode.Screen,
                )
            }
        }

        // 5. Smooth cubic readability vignette for controls & lyrics
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to Color.Black.copy(alpha = 0.26f),
                0.18f to Color.Transparent,
                0.62f to Color.Transparent,
                0.80f to Color.Black.copy(alpha = 0.26f),
                0.92f to Color.Black.copy(alpha = 0.48f),
                1.00f to Color.Black.copy(alpha = 0.64f),
            ),
        )
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
    Box(modifier.background(atmosphere?.base ?: Color(0xFF05050A))) {
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
                    .platformBlur(24f),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Black.copy(alpha = 0.44f),
                            0.18f to Color.Black.copy(alpha = 0.34f),
                            0.46f to Color.Black.copy(alpha = 0.44f),
                            0.74f to Color.Black.copy(alpha = 0.60f),
                            0.90f to Color.Black.copy(alpha = 0.74f),
                            1.00f to Color.Black.copy(alpha = 0.84f),
                        ),
                    ),
            )
        } else {
            val lead = atmosphere?.orbPrimary ?: Musico.extras.bg1
            val secondary = atmosphere?.orbSecondary ?: Color(0xFF070710)
            Canvas(Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas
                val r = (max(w, h) * 0.78f).coerceAtLeast(1f)
                drawRect(
                    brush = Brush.radialGradient(
                        0.00f to lead.copy(alpha = 0.34f),
                        0.38f to secondary.copy(alpha = 0.20f),
                        0.74f to secondary.copy(alpha = 0.05f),
                        1.00f to Color.Transparent,
                        center = Offset(w * 0.5f, h * 0.34f),
                        radius = r,
                    ),
                )
            }
        }
    }
}
