package io.github.aryan007h.musicoonline.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.ui.theme.Musico
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure descriptor for the five Material 3 Expressive player backdrops.
 * Every backdrop is strictly zero-blur and zero-gradient — built from crisp
 * Google Pixel M3E geometric vector shapes and stepped solid tonal surfaces
 * that adapt cleanly to both Light Mode and Dark Mode.
 */
object PlayerBackdropSpecs {
    val ALL_IDS = listOf("mesh", "artwork", "aurora", "rain", "black")

    fun normalize(id: String?): String = when (id) {
        "mesh", "artwork", "aurora", "rain", "black" -> id
        else -> "mesh"
    }

    fun animatesMotion(id: String): Boolean = normalize(id) != "black"
}

/**
 * Pixel-native Material 3 Expressive full-screen player backdrops (Light & Dark adaptive).
 *
 * Strictly ZERO blurs and ZERO smeared gradients:
 * - `mesh`    ("Fluid"):  Rotating M3E scalloped cookie, clover & pill geometry
 * - `artwork` ("Glow"):   Concentric studio vinyl rings & rotating tonal arcs
 * - `aurora`  ("Aurora"): Layered stepped sinusoidal vector ribbons
 * - `rain`    ("Rain"):   Calm falling M3E rounded-capsule rhythm bars
 * - `black`   ("Dark"):   Deep OLED studio black (Dark) / crisp editorial paper (Light)
 */
@Composable
fun PlayerBackdrop(
    backdrop: String,
    track: OnlineTrack?,
    modifier: Modifier = Modifier,
    atmosphere: ArtworkAtmosphere? = null,
    isPlaying: Boolean = true,
) {
    val mode = PlayerBackdropSpecs.normalize(backdrop)
    val resolved = atmosphere ?: rememberArtworkAtmosphere(track?.thumbnail)
    val isDark = Musico.extras.isDark
    val scheme = MaterialTheme.colorScheme

    val targetBase = if (isDark) {
        when (mode) {
            "black" -> Color(0xFF07070B)
            "artwork" -> lerp(scheme.surfaceContainerLow, resolved.base, 0.50f)
            "aurora" -> lerp(Color(0xFF0B0D18), resolved.base, 0.40f)
            "rain" -> lerp(scheme.surface, resolved.base, 0.34f)
            else -> lerp(scheme.surfaceContainerLow, resolved.base, 0.44f)
        }
    } else {
        // Light Mode: soft airy palette-harmonized pastel surface (like M3E lavender showcase)
        when (mode) {
            "black" -> scheme.surface
            "artwork" -> lerp(scheme.surface, resolved.orbPrimary, 0.08f)
            "aurora" -> lerp(scheme.surfaceContainerLow, resolved.orbSecondary, 0.08f)
            "rain" -> lerp(scheme.surface, resolved.orbPrimary, 0.06f)
            else -> lerp(scheme.surface, resolved.orbPrimary, 0.07f)
        }
    }

    val baseColor by animateColorAsState(
        targetValue = targetBase,
        animationSpec = tween(durationMillis = 520),
        label = "m3eBackdropBase",
    )

    val targetTone1 = if (isDark) {
        lerp(baseColor, resolved.orbPrimary, 0.24f)
    } else {
        lerp(scheme.primaryContainer, resolved.orbPrimary, 0.14f).copy(alpha = 0.52f)
    }
    val targetTone2 = if (isDark) {
        lerp(baseColor, resolved.orbSecondary, 0.20f)
    } else {
        lerp(scheme.secondaryContainer, resolved.orbSecondary, 0.14f).copy(alpha = 0.56f)
    }
    val targetToneAccent = if (isDark) {
        lerp(baseColor, resolved.glow, 0.16f)
    } else {
        lerp(scheme.tertiaryContainer, resolved.glow, 0.16f).copy(alpha = 0.55f)
    }

    val tonePrimary by animateColorAsState(
        targetValue = targetTone1,
        animationSpec = tween(durationMillis = 520),
        label = "m3eBackdropTone1",
    )
    val toneSecondary by animateColorAsState(
        targetValue = targetTone2,
        animationSpec = tween(durationMillis = 520),
        label = "m3eBackdropTone2",
    )
    val toneAccent by animateColorAsState(
        targetValue = targetToneAccent,
        animationSpec = tween(durationMillis = 520),
        label = "m3eBackdropAccent",
    )

    val phaseState = remember { mutableFloatStateOf(0f) }
    val shouldAnimate = isPlaying && PlayerBackdropSpecs.animatesMotion(mode)
    LaunchedEffect(shouldAnimate, mode) {
        if (!shouldAnimate) return@LaunchedEffect
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dtSecs = ((now - lastNanos).coerceIn(0L, 100_000_000L)) / 1_000_000_000f
                    phaseState.floatValue = (phaseState.floatValue + dtSecs) % 3600f
                }
                lastNanos = now
            }
        }
    }

    val pathA = remember { Path() }
    val pathB = remember { Path() }
    val pathC = remember { Path() }
    val studioAccent = if (isDark) resolved.glow else scheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = phaseState.floatValue
            when (mode) {
                "mesh" -> drawExpressiveShapesBackdrop(
                    t = t,
                    tonePrimary = tonePrimary,
                    toneSecondary = toneSecondary,
                    toneAccent = toneAccent,
                    pathA = pathA,
                    pathB = pathB,
                )
                "artwork" -> drawVinylRingsBackdrop(
                    t = t,
                    tonePrimary = tonePrimary,
                    toneSecondary = toneSecondary,
                    toneAccent = toneAccent,
                )
                "aurora" -> drawHarmonicRibbonsBackdrop(
                    t = t,
                    tonePrimary = tonePrimary,
                    toneSecondary = toneSecondary,
                    toneAccent = toneAccent,
                    pathA = pathA,
                    pathB = pathB,
                    pathC = pathC,
                )
                "rain" -> drawRhythmCapsulesBackdrop(
                    t = t,
                    tonePrimary = tonePrimary,
                    toneSecondary = toneSecondary,
                    toneAccent = toneAccent,
                )
                "black" -> drawStudioBackdrop(
                    accent = studioAccent,
                )
            }
        }
    }
}

private fun DrawScope.drawExpressiveShapesBackdrop(
    t: Float,
    tonePrimary: Color,
    toneSecondary: Color,
    toneAccent: Color,
    pathA: Path,
    pathB: Path,
) {
    val minDim = min(size.width, size.height)

    // Top-right scalloped 8-lobe M3E cookie
    val cookieCenter = Offset(size.width * 0.86f, size.height * 0.16f)
    val cookieRadius = minDim * 0.40f
    buildScallopedPath(
        path = pathA,
        cx = cookieCenter.x,
        cy = cookieCenter.y,
        baseRadius = cookieRadius,
        lobeAmplitude = cookieRadius * 0.11f,
        lobes = 8,
        angleOffset = t * 0.14f,
    )
    drawPath(path = pathA, color = tonePrimary)

    // Bottom-left 4-lobe clover
    val cloverCenter = Offset(size.width * 0.12f, size.height * 0.84f)
    val cloverRadius = minDim * 0.44f
    buildScallopedPath(
        path = pathB,
        cx = cloverCenter.x,
        cy = cloverCenter.y,
        baseRadius = cloverRadius,
        lobeAmplitude = cloverRadius * 0.16f,
        lobes = 4,
        angleOffset = -t * 0.10f + 0.4f,
    )
    drawPath(path = pathB, color = toneSecondary)

    // Mid-canvas tilted M3E capsule pill accent (solid flat fill)
    val pillW = minDim * 0.50f
    val pillH = minDim * 0.14f
    val pillCenter = Offset(size.width * 0.66f, size.height * 0.68f)
    rotate(degrees = -18f + sin(t * 0.35f) * 4f, pivot = pillCenter) {
        drawRoundRect(
            color = toneAccent,
            topLeft = Offset(pillCenter.x - pillW / 2f, pillCenter.y - pillH / 2f),
            size = Size(pillW, pillH),
            cornerRadius = CornerRadius(pillH / 2f, pillH / 2f),
        )
    }
}

private fun DrawScope.drawVinylRingsBackdrop(
    t: Float,
    tonePrimary: Color,
    toneSecondary: Color,
    toneAccent: Color,
) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.46f
    val maxR = min(size.width, size.height) * 0.72f

    drawCircle(
        color = tonePrimary.copy(alpha = tonePrimary.alpha * 0.65f),
        radius = maxR * 0.52f,
        center = Offset(cx, cy),
    )
    drawCircle(
        color = toneSecondary.copy(alpha = toneSecondary.alpha * 0.55f),
        radius = maxR * 0.30f,
        center = Offset(cx, cy),
    )

    val ringFactors = floatArrayOf(0.42f, 0.58f, 0.74f, 0.90f, 1.06f)
    ringFactors.forEachIndexed { idx, factor ->
        val r = maxR * factor
        drawCircle(
            color = if (idx % 2 == 0) toneAccent.copy(alpha = 0.48f) else tonePrimary.copy(alpha = 0.52f),
            radius = r,
            center = Offset(cx, cy),
            style = Stroke(width = if (idx == 1) 2.5f else 1.5f),
        )
    }

    val arcR1 = maxR * 0.58f
    drawArc(
        color = toneAccent,
        startAngle = (t * 16f) % 360f,
        sweepAngle = 72f,
        useCenter = false,
        topLeft = Offset(cx - arcR1, cy - arcR1),
        size = Size(arcR1 * 2f, arcR1 * 2f),
        style = Stroke(width = 7f, cap = StrokeCap.Round),
    )
    val arcR2 = maxR * 0.74f
    drawArc(
        color = toneSecondary,
        startAngle = (180f - t * 11f) % 360f,
        sweepAngle = 96f,
        useCenter = false,
        topLeft = Offset(cx - arcR2, cy - arcR2),
        size = Size(arcR2 * 2f, arcR2 * 2f),
        style = Stroke(width = 5f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawHarmonicRibbonsBackdrop(
    t: Float,
    tonePrimary: Color,
    toneSecondary: Color,
    toneAccent: Color,
    pathA: Path,
    pathB: Path,
    pathC: Path,
) {
    drawWaveBand(
        path = pathA,
        baseY = size.height * 0.56f,
        amplitude = size.height * 0.055f,
        cycles = 1.4f,
        phase = t * 0.55f,
        color = toneSecondary.copy(alpha = toneSecondary.alpha * 0.75f),
    )
    drawWaveBand(
        path = pathB,
        baseY = size.height * 0.70f,
        amplitude = size.height * 0.048f,
        cycles = 1.9f,
        phase = -t * 0.72f + 1.4f,
        color = tonePrimary.copy(alpha = tonePrimary.alpha * 0.85f),
    )
    drawWaveBand(
        path = pathC,
        baseY = size.height * 0.83f,
        amplitude = size.height * 0.036f,
        cycles = 2.4f,
        phase = t * 0.90f + 2.8f,
        color = toneAccent.copy(alpha = toneAccent.alpha * 0.78f),
    )
}

private fun DrawScope.drawWaveBand(
    path: Path,
    baseY: Float,
    amplitude: Float,
    cycles: Float,
    phase: Float,
    color: Color,
) {
    path.reset()
    val steps = 36
    val w = size.width
    val h = size.height
    for (i in 0..steps) {
        val frac = i.toFloat() / steps
        val x = w * frac
        val y = baseY + sin(frac * cycles * 2.0 * PI + phase).toFloat() * amplitude
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.lineTo(w, h)
    path.lineTo(0f, h)
    path.close()
    drawPath(path = path, color = color)
}

private fun DrawScope.drawRhythmCapsulesBackdrop(
    t: Float,
    tonePrimary: Color,
    toneSecondary: Color,
    toneAccent: Color,
) {
    val columns = 12
    val w = size.width
    val h = size.height
    val colSpacing = w / (columns + 1)

    for (i in 0 until columns) {
        val seed = (i * 37 + 13) % 100
        val speed = 28f + (seed % 5) * 14f
        val barWidth = (6f + (seed % 4) * 3f)
        val barHeight = 46f + (seed % 6) * 26f
        val wrapSpan = h + barHeight * 2f
        val initialY = ((seed * 23f) % wrapSpan)
        val y = ((initialY + t * speed) % wrapSpan) - barHeight
        val x = colSpacing * (i + 1) - barWidth / 2f

        val color = when (i % 3) {
            0 -> tonePrimary
            1 -> toneSecondary
            else -> toneAccent
        }
        drawRoundRect(
            color = color.copy(alpha = color.alpha * 0.80f),
            topLeft = Offset(x, y),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
        )
    }
}

private fun DrawScope.drawStudioBackdrop(accent: Color) {
    val inset = 28f
    val arm = 22f
    val stroke = 1.5f
    val lineColor = accent.copy(alpha = 0.16f)
    val w = size.width
    val h = size.height

    drawLine(lineColor, Offset(inset, inset), Offset(inset + arm, inset), stroke, StrokeCap.Round)
    drawLine(lineColor, Offset(inset, inset), Offset(inset, inset + arm), stroke, StrokeCap.Round)
    drawLine(lineColor, Offset(w - inset, inset), Offset(w - inset - arm, inset), stroke, StrokeCap.Round)
    drawLine(lineColor, Offset(w - inset, inset), Offset(w - inset, inset + arm), stroke, StrokeCap.Round)
    drawLine(lineColor, Offset(inset, h - inset), Offset(inset + arm, h - inset), stroke, StrokeCap.Round)
    drawLine(lineColor, Offset(inset, h - inset), Offset(inset, h - inset - arm), stroke, StrokeCap.Round)
    drawLine(lineColor, Offset(w - inset, h - inset), Offset(w - inset - arm, h - inset), stroke, StrokeCap.Round)
    drawLine(lineColor, Offset(w - inset, h - inset), Offset(w - inset, h - inset - arm), stroke, StrokeCap.Round)
}

private fun buildScallopedPath(
    path: Path,
    cx: Float,
    cy: Float,
    baseRadius: Float,
    lobeAmplitude: Float,
    lobes: Int,
    angleOffset: Float,
) {
    path.reset()
    val segments = 96
    for (i in 0..segments) {
        val theta = (i.toFloat() / segments) * (2.0 * PI).toFloat()
        val r = baseRadius + lobeAmplitude * cos(lobes * (theta + angleOffset))
        val x = cx + r * cos(theta)
        val y = cy + r * sin(theta)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
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
