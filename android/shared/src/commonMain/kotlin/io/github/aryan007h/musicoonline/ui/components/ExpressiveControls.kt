package io.github.aryan007h.musicoonline.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlin.math.PI
import kotlin.math.sin

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
 * - Active track renders a smooth traveling sine wave while playing and
 *   flattens cleanly when paused or while scrubbing.
 * - Inactive track is a crisp flat rounded bar with a subtle gap around the
 *   vertical M3E rounded-pill thumb handle.
 * - Supports both tap-to-seek and smooth horizontal drag scrubbing.
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
    var dragFraction by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    val shown = (if (dragging) dragFraction else progress).coerceIn(0f, 1f)

    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isPlaying && !dragging && enabled) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "m3eWaveAmp",
    )

    val phaseTransition = rememberInfiniteTransition(label = "m3eWavePhase")
    val phase by phaseTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "m3eWavePhaseVal",
    )

    val effectivePhase = if (isPlaying && !dragging) phase else 0f

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                    hapticTick()
                    onSeek(frac)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        onDragProgress?.invoke(dragFraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
                        onDragProgress?.invoke(dragFraction)
                    },
                    onDragEnd = {
                        val finalFrac = dragFraction
                        dragging = false
                        onDragProgress?.invoke(null)
                        hapticTick()
                        onSeek(finalFrac)
                    },
                    onDragCancel = {
                        dragging = false
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
            val ampPx = waveAmplitude.toPx() * animatedAmplitude
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
                val path = Path()
                var x = pad
                var first = true
                while (x <= activeEnd) {
                    val distFromStart = x - pad
                    val distFromEnd = activeEnd - x
                    // Smooth taper near both ends so the wave joins cleanly without sharp kinks
                    val endFade = (minOf(distFromStart, distFromEnd) / 10f).coerceIn(0f, 1f)
                    val y = cy + sin((distFromStart / wlPx) * 2.0 * PI - effectivePhase).toFloat() * ampPx * endFade
                    if (first) {
                        path.moveTo(x, y)
                        first = false
                    } else {
                        path.lineTo(x, y)
                    }
                    x += stepPx
                }
                path.lineTo(activeEnd, cy)
                drawPath(
                    path = path,
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
