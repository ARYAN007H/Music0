package io.github.aryan007h.musicoonline.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.hapticThud
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Pure math & state-settlement helpers for [ExpressiveWavySeekBar], decoupled
 * from Compose for deterministic unit testing in `SharedCoreTests`.
 */
object ExpressiveSeekMath {
    fun clampFraction(value: Float): Float =
        if (value.isNaN()) 0f else value.coerceIn(0f, 1f)

    fun shouldAnimateWave(isPlaying: Boolean, enabled: Boolean, dragging: Boolean): Boolean =
        isPlaying && enabled && !dragging

    fun resolveDisplayedFraction(
        progress: Float,
        dragging: Boolean,
        dragFraction: Float,
        pendingSeekFraction: Float?,
    ): Float = clampFraction(
        when {
            dragging -> dragFraction
            pendingSeekFraction != null -> pendingSeekFraction
            else -> progress
        },
    )

    fun isPendingSeekSettled(progress: Float, pendingSeekFraction: Float, tolerance: Float = 0.025f): Boolean =
        abs(clampFraction(progress) - clampFraction(pendingSeekFraction)) <= tolerance
}

/**
 * Native Material 3 Expressive morphing Play / Pause button.
 *
 * Follows Google Pixel Media Player M3E shape morphing:
 * - Paused: full circular container (`size / 2` corner radius)
 * - Playing: expressive rounded squircle (`size * 0.28f` corner radius)
 * - Solid flat tonal M3 colors (zero gradients, zero glow shadows)
 */
@Composable
fun ExpressivePlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBuffering: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 68.dp,
    containerColorPaused: Color = MaterialTheme.colorScheme.primaryContainer,
    containerColorPlaying: Color = MaterialTheme.colorScheme.primary,
    contentColorPaused: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    contentColorPlaying: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()

    val cornerRadius by animateDpAsState(
        targetValue = when {
            pressed -> size * 0.24f
            isPlaying -> size * 0.28f
            else -> size * 0.50f
        },
        animationSpec = MusicoMotion.expressive(),
        label = "m3ePlayCorner",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = MusicoMotion.snappy(),
        label = "m3ePlayScale",
    )
    val bgColor by animateColorAsState(
        targetValue = if (isPlaying) containerColorPlaying else containerColorPaused,
        animationSpec = MusicoMotion.gentle(),
        label = "m3ePlayBg",
    )
    val iconColor by animateColorAsState(
        targetValue = if (isPlaying) contentColorPlaying else contentColorPaused,
        animationSpec = MusicoMotion.gentle(),
        label = "m3ePlayIcon",
    )

    val shape = RoundedCornerShape(cornerRadius.coerceAtLeast(8.dp))

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                val safe = scale.coerceAtLeast(0.01f)
                scaleX = safe
                scaleY = safe
            }
            .clip(shape)
            .background(if (enabled) bgColor else MaterialTheme.colorScheme.surfaceContainerHighest)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = LocalIndication.current,
            ) {
                hapticThud()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                color = iconColor,
                modifier = Modifier.size(size * 0.44f),
                strokeWidth = 2.8.dp,
            )
        } else {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (scaleIn(initialScale = 0.65f, animationSpec = MusicoMotion.snappy()) +
                        fadeIn(tween(MusicoMotion.FAST))) togetherWith
                        (scaleOut(targetScale = 0.65f, animationSpec = tween(MusicoMotion.FAST)) +
                            fadeOut(tween(MusicoMotion.FAST)))
                },
                label = "m3ePlayGlyph",
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = if (enabled) iconColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size * 0.50f),
                )
            }
        }
    }
}

/**
 * Native Material 3 Expressive wavy seekbar (Android Pixel Media Player style).
 *
 * - Active track renders a smooth traveling sine wave ONLY while playing,
 *   enabled, and not scrubbing; wave phase is read exclusively in the draw
 *   phase so zero composable recompositions occur at 60–120 Hz.
 * - Reuses a single `Path` across frames (zero per-frame heap allocations).
 * - Holds the user's released scrub position until player playback settles so
 *   there is no brief snap-back on `onDragEnd`.
 * - Safely resets drag state if `enabled` flips false mid-gesture.
 */
@Composable
fun ExpressiveWavySeekBar(
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackHeight: Dp = 4.dp,
    waveAmplitude: Dp = 3.2.dp,
    waveLength: Dp = 24.dp,
    thumbHeight: Dp = 20.dp,
    onDragProgress: ((Float?) -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(ExpressiveSeekMath.clampFraction(progress)) }
    var pendingSeekFraction by remember { mutableStateOf<Float?>(null) }

    // Never allow drag state to get stuck if `enabled` flips false mid-gesture.
    LaunchedEffect(enabled) {
        if (!enabled) {
            dragging = false
            pendingSeekFraction = null
            onDragProgress?.invoke(null)
        }
    }

    // Clear pending seek settlement once player position catches up or after a short timeout.
    LaunchedEffect(progress, pendingSeekFraction) {
        val target = pendingSeekFraction ?: return@LaunchedEffect
        if (ExpressiveSeekMath.isPendingSeekSettled(progress, target)) {
            pendingSeekFraction = null
            onDragProgress?.invoke(null)
        }
    }
    LaunchedEffect(pendingSeekFraction) {
        if (pendingSeekFraction != null) {
            delay(480L)
            pendingSeekFraction = null
            onDragProgress?.invoke(null)
        }
    }

    val shown = ExpressiveSeekMath.resolveDisplayedFraction(
        progress = progress,
        dragging = dragging,
        dragFraction = dragFraction,
        pendingSeekFraction = pendingSeekFraction,
    )

    val shouldAnimate = ExpressiveSeekMath.shouldAnimateWave(
        isPlaying = isPlaying,
        enabled = enabled,
        dragging = dragging,
    )
    val animatedAmplitudeState = animateFloatAsState(
        targetValue = if (shouldAnimate) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "m3eWaveAmp",
    )

    // Frame-driven phase clock that ONLY runs while playing & enabled & not scrubbing,
    // and is read strictly inside Canvas draw scope (zero recompositions per frame).
    val phaseState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(shouldAnimate) {
        if (!shouldAnimate) return@LaunchedEffect
        var lastNanos = 0L
        val radiansPerSec = (2.0 * PI / 1.5).toFloat()
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos).coerceIn(0L, 100_000_000L)) / 1_000_000_000f
                    phaseState.floatValue = (phaseState.floatValue + dt * radiansPerSec) % (2.0 * PI).toFloat()
                }
                lastNanos = now
            }
        }
    }

    // Reused Path object to prevent per-frame heap allocations during playback.
    val wavePath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val frac = ExpressiveSeekMath.clampFraction(offset.x / size.width.coerceAtLeast(1))
                    pendingSeekFraction = frac
                    onDragProgress?.invoke(frac)
                    hapticTick()
                    onSeek(frac)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        pendingSeekFraction = null
                        dragFraction = ExpressiveSeekMath.clampFraction(offset.x / size.width.coerceAtLeast(1))
                        onDragProgress?.invoke(dragFraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = ExpressiveSeekMath.clampFraction(change.position.x / size.width.coerceAtLeast(1))
                        onDragProgress?.invoke(dragFraction)
                    },
                    onDragEnd = {
                        val finalFrac = dragFraction
                        dragging = false
                        pendingSeekFraction = finalFrac
                        onDragProgress?.invoke(finalFrac)
                        hapticTick()
                        onSeek(finalFrac)
                    },
                    onDragCancel = {
                        dragging = false
                        pendingSeekFraction = null
                        onDragProgress?.invoke(null)
                    },
                )
            },
    ) {
        val w = size.width
        val cy = size.height / 2f
        if (w <= 0f) return@Canvas

        val strokePx = trackHeight.toPx()
        val thumbW = (if (dragging) 5.dp else 4.dp).toPx()
        val thumbH = (if (dragging) thumbHeight + 4.dp else thumbHeight).toPx()
        val thumbGap = 6.dp.toPx()
        val pad = thumbW / 2f + 2.dp.toPx()
        val usableW = (w - pad * 2f).coerceAtLeast(1f)
        val thumbX = pad + usableW * shown

        // Inactive flat rounded track to the right of the thumb gap
        val inactiveStart = (thumbX + thumbGap).coerceAtMost(w - pad)
        if (w - pad - inactiveStart > 1f) {
            drawLine(
                color = inactiveColor,
                start = Offset(inactiveStart, cy),
                end = Offset(w - pad, cy),
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
            )
        }

        // Active wavy (or flat when paused) track to the left of the thumb gap
        val activeEnd = (thumbX - thumbGap).coerceAtLeast(pad)
        val activeLen = activeEnd - pad
        if (activeLen > 1f) {
            val ampPx = waveAmplitude.toPx() * animatedAmplitudeState.value
            if (ampPx < 0.25f) {
                drawLine(
                    color = activeColor,
                    start = Offset(pad, cy),
                    end = Offset(activeEnd, cy),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round,
                )
            } else {
                val wlPx = waveLength.toPx().coerceAtLeast(8f)
                val stepPx = 3f
                val currentPhase = phaseState.floatValue
                wavePath.reset()
                var x = pad
                var first = true
                while (x <= activeEnd) {
                    val distFromStart = x - pad
                    val distFromEnd = activeEnd - x
                    val endFade = (minOf(distFromStart, distFromEnd) / 10f).coerceIn(0f, 1f)
                    val y = cy + sin((distFromStart / wlPx) * 2.0 * PI - currentPhase).toFloat() * ampPx * endFade
                    if (first) {
                        wavePath.moveTo(x, y)
                        first = false
                    } else {
                        wavePath.lineTo(x, y)
                    }
                    x += stepPx
                }
                wavePath.lineTo(activeEnd, cy)
                drawPath(
                    path = wavePath,
                    color = activeColor,
                    style = Stroke(
                        width = strokePx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        // Authentic Material 3 Expressive vertical pill handle thumb
        drawRoundRect(
            color = if (enabled) thumbColor else inactiveColor,
            topLeft = Offset(thumbX - thumbW / 2f, cy - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f),
        )
    }
}
