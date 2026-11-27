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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.ui.theme.ExpressiveSegmentedMath
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.hapticThud
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import io.github.aryan007h.musicoonline.ui.theme.safeCornerDp
import io.github.aryan007h.musicoonline.ui.theme.safePositiveScale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
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
 * Pure range-conversion math for [ExpressiveSlider], unit-tested in `SharedCoreTests`.
 */
object ExpressiveSliderMath {
    fun clampToRange(value: Float, range: ClosedFloatingPointRange<Float>): Float {
        if (value.isNaN()) return range.start
        return value.coerceIn(range.start, range.endInclusive)
    }

    fun valueToFraction(value: Float, range: ClosedFloatingPointRange<Float>): Float {
        val span = range.endInclusive - range.start
        if (span <= 0f || value.isNaN()) return 0f
        return ((value - range.start) / span).coerceIn(0f, 1f)
    }

    fun fractionToValue(fraction: Float, range: ClosedFloatingPointRange<Float>): Float {
        val clamped = ExpressiveSeekMath.clampFraction(fraction)
        return range.start + (range.endInclusive - range.start) * clamped
    }
}

/**
 * Official Material 3 Expressive toggle switch with check glyph inside the active thumb.
 */
@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = {
            hapticTick()
            onCheckedChange(it)
        },
        enabled = enabled,
        modifier = modifier,
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            null
        },
        colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            checkedIconColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}

/**
 * Official Material 3 Expressive thick track slider (Android 16 / Pixel M3E spec):
 * - Thick rounded pill active & inactive tracks with clean thumb cutout gap
 * - Crisp end stop-dot on the inactive track
 * - Vertical pill handle thumb that expands smoothly while dragging
 * - Zero stock Material thin-line slider styling
 */
@Composable
fun ExpressiveSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackHeight: Dp = 14.dp,
    thumbHeight: Dp = 30.dp,
    barHeight: Dp = 36.dp,
) {
    var dragging by remember { mutableStateOf(false) }
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val latestRange by rememberUpdatedState(valueRange)

    LaunchedEffect(enabled) {
        if (!enabled) dragging = false
    }

    val fraction = ExpressiveSliderMath.valueToFraction(value, valueRange)
    val thumbWidthDp by animateDpAsState(
        targetValue = if (dragging) 6.5.dp else 5.dp,
        animationSpec = MusicoMotion.spatialFast(),
        label = "m3eSliderThumbW",
    )
    val thumbHeightDp by animateDpAsState(
        targetValue = if (dragging) thumbHeight + 4.dp else thumbHeight,
        animationSpec = MusicoMotion.spatialFast(),
        label = "m3eSliderThumbH",
    )

    val effectiveActive = if (enabled) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val effectiveInactive = if (enabled) inactiveColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val effectiveThumb = if (enabled) thumbColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val frac = ExpressiveSeekMath.clampFraction(offset.x / size.width.coerceAtLeast(1))
                    val next = ExpressiveSliderMath.fractionToValue(frac, latestRange)
                    hapticTick()
                    latestOnChange(next)
                    latestOnFinished?.invoke()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        val frac = ExpressiveSeekMath.clampFraction(offset.x / size.width.coerceAtLeast(1))
                        latestOnChange(ExpressiveSliderMath.fractionToValue(frac, latestRange))
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val frac = ExpressiveSeekMath.clampFraction(change.position.x / size.width.coerceAtLeast(1))
                        latestOnChange(ExpressiveSliderMath.fractionToValue(frac, latestRange))
                    },
                    onDragEnd = {
                        dragging = false
                        hapticTick()
                        latestOnFinished?.invoke()
                    },
                    onDragCancel = {
                        dragging = false
                        latestOnFinished?.invoke()
                    },
                )
            },
    ) {
        val w = size.width
        val cy = size.height / 2f
        if (w <= 0f) return@Canvas

        val trackH = trackHeight.toPx()
        val trackCorner = CornerRadius(trackH / 2f, trackH / 2f)
        val thumbW = thumbWidthDp.toPx()
        val thumbH = thumbHeightDp.toPx()
        val thumbGap = 6.dp.toPx()
        val pad = thumbW / 2f + 2.dp.toPx()
        val usableW = (w - pad * 2f).coerceAtLeast(1f)
        val thumbX = pad + usableW * fraction

        // Active thick pill track to the left of the thumb cutout
        val activeEnd = (thumbX - thumbGap).coerceAtLeast(pad)
        val activeW = activeEnd - pad
        if (activeW > 1f) {
            drawRoundRect(
                color = effectiveActive,
                topLeft = Offset(pad, cy - trackH / 2f),
                size = Size(activeW, trackH),
                cornerRadius = trackCorner,
            )
        }

        // Inactive thick pill track to the right of the thumb cutout
        val inactiveStart = (thumbX + thumbGap).coerceAtMost(w - pad)
        val inactiveW = (w - pad) - inactiveStart
        if (inactiveW > 1f) {
            drawRoundRect(
                color = effectiveInactive,
                topLeft = Offset(inactiveStart, cy - trackH / 2f),
                size = Size(inactiveW, trackH),
                cornerRadius = trackCorner,
            )
            // Signature M3E end stop-dot
            if (inactiveW > trackH) {
                val dotRadius = (trackH * 0.16f).coerceIn(1.8.dp.toPx(), 2.8.dp.toPx())
                drawCircle(
                    color = effectiveActive.copy(alpha = 0.55f),
                    radius = dotRadius,
                    center = Offset((w - pad) - trackH / 2f, cy),
                )
            }
        }

        // Authentic M3E vertical pill thumb handle
        drawRoundRect(
            color = effectiveThumb,
            topLeft = Offset(thumbX - thumbW / 2f, cy - thumbH / 2f),
            size = Size(thumbW, thumbH),
            cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f),
        )
    }
}

/**
 * Native Material 3 Expressive morphing Play / Pause button.
 *
 * Follows Google's Material 3 Expressive Media Player shape morphing:
 * - Paused: full pill / circular container (`minDim / 2` corner radius)
 * - Playing: expressive rounded squircle (`minDim * 0.30f` corner radius)
 * - Pressed: playful spatial spring squish (`minDim * 0.22f`)
 * - Solid flat tonal M3 colors in both Light and Dark mode.
 */
@Composable
fun ExpressivePlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isBuffering: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 68.dp,
    width: Dp = size,
    height: Dp = size,
    containerColorPaused: Color = MaterialTheme.colorScheme.primaryContainer,
    containerColorPlaying: Color = MaterialTheme.colorScheme.primary,
    contentColorPaused: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    contentColorPlaying: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val minDim = if (width < height) width else height

    val cornerRadius by animateDpAsState(
        targetValue = when {
            pressed -> minDim * 0.22f
            isPlaying -> minDim * 0.30f
            else -> minDim * 0.50f
        },
        animationSpec = MusicoMotion.spatialFast(),
        label = "m3ePlayCorner",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = MusicoMotion.spatialFast(),
        label = "m3ePlayScale",
    )
    val bgColor by key(Musico.extras.isDark) {
        animateColorAsState(
            targetValue = if (isPlaying) containerColorPlaying else containerColorPaused,
            animationSpec = MusicoMotion.effectsDefault(),
            label = "m3ePlayBg",
        )
    }
    val iconColor by key(Musico.extras.isDark) {
        animateColorAsState(
            targetValue = if (isPlaying) contentColorPlaying else contentColorPaused,
            animationSpec = MusicoMotion.effectsDefault(),
            label = "m3ePlayIcon",
        )
    }

    val shape = RoundedCornerShape(safeCornerDp(cornerRadius).coerceAtLeast(8.dp))

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .graphicsLayer {
                val safe = safePositiveScale(scale)
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
                modifier = Modifier.size(minDim * 0.42f),
                strokeWidth = 2.8.dp,
            )
        } else {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (scaleIn(initialScale = 0.65f, animationSpec = MusicoMotion.spatialFast()) +
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
                    modifier = Modifier.size(minDim * 0.48f),
                )
            }
        }
    }
}

/**
 * Chunky Material 3 Expressive tonal squircle transport button (Previous / Next / Skip).
 * Pairs beside [ExpressivePlayPauseButton] to create the signature M3E mixed-geometry
 * transport cluster (soft tonal squircles flanking the bold primary play/pause centerpiece).
 */
@Composable
fun ExpressiveTransportButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    width: Dp = 76.dp,
    height: Dp = 70.dp,
    iconSize: Dp = 28.dp,
    restingCorner: Dp = 24.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) (restingCorner * 0.62f).coerceAtLeast(10.dp) else restingCorner,
        animationSpec = MusicoMotion.spatialFast(),
        label = "m3eTransportCorner",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = MusicoMotion.spatialFast(),
        label = "m3eTransportScale",
    )
    val shape = RoundedCornerShape(safeCornerDp(corner).coerceAtLeast(8.dp))

    Box(
        modifier = modifier
            .size(width = width, height = height)
            .graphicsLayer {
                val safe = safePositiveScale(scale)
                scaleX = safe
                scaleY = safe
            }
            .clip(shape)
            .background(if (enabled) containerColor else MaterialTheme.colorScheme.surfaceContainerHigh)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = LocalIndication.current,
            ) {
                hapticTick()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) contentColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Elevated Material 3 Expressive pill / circle secondary action button (crisp white
 * `surfaceContainerLowest` in Light mode, rich `surfaceContainerHigh` in Dark mode)
 * with spring morphing when active or pressed.
 */
@Composable
fun ExpressiveActionPill(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    active: Boolean = false,
    height: Dp = 48.dp,
    containerColor: Color = if (active) {
        MaterialTheme.colorScheme.secondaryContainer
    } else if (Musico.extras.isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    },
    contentColor: Color = if (active) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    },
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = when {
            pressed -> 14.dp
            active -> 16.dp
            else -> height / 2
        },
        animationSpec = MusicoMotion.spatialFast(),
        label = "m3ePillCorner",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = MusicoMotion.spatialFast(),
        label = "m3ePillScale",
    )
    val bg by key(Musico.extras.isDark) {
        animateColorAsState(
            targetValue = containerColor,
            animationSpec = MusicoMotion.effectsFast(),
            label = "m3ePillBg",
        )
    }
    val fg by key(Musico.extras.isDark) {
        animateColorAsState(
            targetValue = contentColor,
            animationSpec = MusicoMotion.effectsFast(),
            label = "m3ePillFg",
        )
    }
    val shape = RoundedCornerShape(safeCornerDp(corner).coerceAtLeast(8.dp))

    Surface(
        shape = shape,
        color = bg,
        shadowElevation = if (!Musico.extras.isDark && !active) 1.dp else 0.dp,
        border = if (!Musico.extras.isDark && !active) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        } else {
            null
        },
        modifier = modifier
            .height(height)
            .graphicsLayer {
                val safe = safePositiveScale(scale)
                scaleX = safe
                scaleY = safe
            }
            .clip(shape)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(
                interactionSource = source,
                indication = LocalIndication.current,
            ) {
                hapticTick()
                onClick()
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (label != null) 16.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = fg,
                modifier = Modifier.size(20.dp),
            )
            if (label != null) {
                Spacer(Modifier.width(7.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Official Material 3 Expressive Connected Button Group:
 * - Outer segments carry full pill outer corners; adjacent inner corners stay tight (8dp)
 *   separated by crisp 3dp gaps.
 * - Selecting a segment smoothly morphs all 4 of its corners into a full pill (`24dp`)
 *   with bouncy spatial spring physics and rich `primary` or `secondaryContainer` contrast.
 */
data class ConnectedButtonOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
)

@Composable
fun <T> ExpressiveConnectedButtonGroup(
    options: List<ConnectedButtonOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    enabled: Boolean = true,
    emphasized: Boolean = true,
    unselectedColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val isDark = Musico.extras.isDark
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = option.value == selected
            val corners = ExpressiveSegmentedMath.connectedButtonCorners(
                index = index,
                count = options.size,
                selected = isSelected,
            )
            val ts by animateDpAsState(corners.topStartDp.dp, MusicoMotion.spatialFast(), label = "cbTS$index")
            val te by animateDpAsState(corners.topEndDp.dp, MusicoMotion.spatialFast(), label = "cbTE$index")
            val be by animateDpAsState(corners.bottomEndDp.dp, MusicoMotion.spatialFast(), label = "cbBE$index")
            val bs by animateDpAsState(corners.bottomStartDp.dp, MusicoMotion.spatialFast(), label = "cbBS$index")

            val targetBg = when {
                isSelected && emphasized && isDark -> MaterialTheme.colorScheme.primaryContainer
                isSelected && emphasized -> MaterialTheme.colorScheme.primary
                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                else -> unselectedColor
            }
            val targetFg = when {
                isSelected && emphasized && isDark -> MaterialTheme.colorScheme.onPrimaryContainer
                isSelected && emphasized -> MaterialTheme.colorScheme.onPrimary
                isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val bg by key(isDark) {
                animateColorAsState(targetBg, MusicoMotion.effectsFast(), label = "cbBg$index")
            }
            val fg by key(isDark) {
                animateColorAsState(targetFg, MusicoMotion.effectsFast(), label = "cbFg$index")
            }

            val shape = RoundedCornerShape(
                topStart = safeCornerDp(ts),
                topEnd = safeCornerDp(te),
                bottomEnd = safeCornerDp(be),
                bottomStart = safeCornerDp(bs),
            )
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.96f else 1f,
                animationSpec = MusicoMotion.spatialFast(),
                label = "cbScale$index",
            )
            val segBorder = when {
                isDark && isSelected && emphasized -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
                isDark && !isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f))
                else -> null
            }

            Surface(
                shape = shape,
                color = bg,
                border = segBorder,
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .graphicsLayer {
                        val safe = safePositiveScale(scale)
                        scaleX = safe
                        scaleY = safe
                    }
                    .clip(shape)
                    .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                    .clickable(
                        enabled = enabled,
                        interactionSource = source,
                        indication = LocalIndication.current,
                    ) {
                        if (!isSelected) {
                            hapticTick()
                            onSelect(option.value)
                        }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                    option.icon?.let { vec ->
                        Icon(
                            imageVector = vec,
                            contentDescription = null,
                            tint = fg,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = fg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                }
            }
        }
    }
}

/**
 * Material 3 Expressive shape-morphing filter chip:
 * - Unselected: soft `11dp` rounded rectangle on tonal container
 * - Selected: spring-morphs into a full stadium pill (`20dp`) in `primary` / `secondaryContainer`
 * - Keyed on Light/Dark polarity so theme toggles never bleed stale opposite-theme colors.
 */
@Composable
fun ExpressiveFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    usePrimaryWhenSelected: Boolean = true,
) {
    val isDark = Musico.extras.isDark
    val corner by animateDpAsState(
        targetValue = if (selected) 20.dp else 11.dp,
        animationSpec = MusicoMotion.spatialFast(),
        label = "m3eChipCorner",
    )
    val bg by key(isDark) {
        animateColorAsState(
            targetValue = when {
                selected && usePrimaryWhenSelected && isDark -> MaterialTheme.colorScheme.primaryContainer
                selected && usePrimaryWhenSelected -> MaterialTheme.colorScheme.primary
                selected -> MaterialTheme.colorScheme.secondaryContainer
                isDark -> MaterialTheme.colorScheme.surfaceContainerHigh
                else -> MaterialTheme.colorScheme.surfaceContainerLowest
            },
            animationSpec = MusicoMotion.effectsFast(),
            label = "m3eChipBg",
        )
    }
    val fg by key(isDark) {
        animateColorAsState(
            targetValue = when {
                selected && usePrimaryWhenSelected && isDark -> MaterialTheme.colorScheme.onPrimaryContainer
                selected && usePrimaryWhenSelected -> MaterialTheme.colorScheme.onPrimary
                selected -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            animationSpec = MusicoMotion.effectsFast(),
            label = "m3eChipFg",
        )
    }
    val shape = RoundedCornerShape(safeCornerDp(corner).coerceAtLeast(6.dp))
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = MusicoMotion.spatialFast(),
        label = "m3eChipScale",
    )

    Surface(
        shape = shape,
        color = bg,
        border = when {
            isDark && selected && usePrimaryWhenSelected ->
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
            isDark && !selected ->
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.38f))
            !selected && !isDark ->
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            else -> null
        },
        modifier = modifier
            .height(36.dp)
            .graphicsLayer {
                val safe = safePositiveScale(scale)
                scaleX = safe
                scaleY = safe
            }
            .clip(shape)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(
                enabled = enabled,
                interactionSource = source,
                indication = LocalIndication.current,
            ) {
                hapticTick()
                onClick()
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            leadingIcon?.let {
                Icon(it, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = fg,
                maxLines = 1,
            )
        }
    }
}

/**
 * Official Material 3 Expressive Segmented List Card item (Android 16 / Pixel Settings style):
 * Each row sits on its own crisp tonal container (`surfaceContainerLowest` in Light mode,
 * `surfaceContainer` in Dark mode) with `24dp` outer corners, `6dp` inner corners,
 * and `3dp` gaps between rows.
 */
@Composable
fun ExpressiveSegmentedItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    containerColor: Color = if (Musico.extras.isDark) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    },
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val isDark = Musico.extras.isDark
    val shape = remember(index, count) {
        ExpressiveSegmentedMath.verticalListCorners(index, count).toRoundedCornerShape()
    }
    Surface(
        shape = shape,
        color = containerColor,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.34f else 0.42f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            hapticTick()
                            onClick()
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        content()
    }
}

/**
 * Container stacking [ExpressiveSegmentedItem] cards with the crisp M3 Expressive 3dp gap.
 */
@Composable
fun ExpressiveSegmentedColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        content()
    }
}

/**
 * Native Material 3 Expressive wavy seekbar (Android 16 / Pixel Media Player style).
 *
 * - Bold, thick M3E proportions (`5.5.dp` wavy track, `4.0.dp` amplitude, `26.dp` tall
 *   vertical pill thumb, crisp end stop-dot on the inactive track).
 * - Active track renders a smooth traveling sine wave ONLY while playing, enabled, and
 *   not scrubbing; wave phase is read exclusively in the draw phase so zero composable
 *   recompositions occur at 120 Hz.
 * - Reuses a single `Path` across frames (zero per-frame heap allocations).
 */
@Composable
fun ExpressiveWavySeekBar(
    progress: Float,
    isPlaying: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackHeight: Dp = 5.5.dp,
    waveAmplitude: Dp = 4.0.dp,
    waveLength: Dp = 26.dp,
    thumbHeight: Dp = 26.dp,
    barHeight: Dp = 36.dp,
    onDragProgress: ((Float?) -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(ExpressiveSeekMath.clampFraction(progress)) }
    var pendingSeekFraction by remember { mutableStateOf<Float?>(null) }

    val latestOnSeek by rememberUpdatedState(onSeek)
    val latestOnDragProgress by rememberUpdatedState(onDragProgress)

    LaunchedEffect(enabled) {
        if (!enabled) {
            dragging = false
            pendingSeekFraction = null
            latestOnDragProgress?.invoke(null)
        }
    }

    LaunchedEffect(progress, pendingSeekFraction) {
        val target = pendingSeekFraction ?: return@LaunchedEffect
        if (ExpressiveSeekMath.isPendingSeekSettled(progress, target)) {
            pendingSeekFraction = null
            latestOnDragProgress?.invoke(null)
        }
    }
    LaunchedEffect(pendingSeekFraction) {
        if (pendingSeekFraction != null) {
            delay(480L)
            pendingSeekFraction = null
            latestOnDragProgress?.invoke(null)
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

    val phaseState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(shouldAnimate) {
        if (!shouldAnimate) return@LaunchedEffect
        var lastNanos = 0L
        val radiansPerSec = (2.0 * PI / 1.45).toFloat()
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

    val wavePath = remember { Path() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val frac = ExpressiveSeekMath.clampFraction(offset.x / size.width.coerceAtLeast(1))
                    pendingSeekFraction = frac
                    latestOnDragProgress?.invoke(frac)
                    hapticTick()
                    latestOnSeek(frac)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        pendingSeekFraction = null
                        dragFraction = ExpressiveSeekMath.clampFraction(offset.x / size.width.coerceAtLeast(1))
                        latestOnDragProgress?.invoke(dragFraction)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = ExpressiveSeekMath.clampFraction(change.position.x / size.width.coerceAtLeast(1))
                        latestOnDragProgress?.invoke(dragFraction)
                    },
                    onDragEnd = {
                        val finalFrac = dragFraction
                        dragging = false
                        pendingSeekFraction = finalFrac
                        latestOnDragProgress?.invoke(finalFrac)
                        hapticTick()
                        latestOnSeek(finalFrac)
                    },
                    onDragCancel = {
                        dragging = false
                        pendingSeekFraction = null
                        latestOnDragProgress?.invoke(null)
                    },
                )
            },
    ) {
        drawWavySeekBar(
            shown = shown,
            dragging = dragging,
            enabled = enabled,
            activeColor = activeColor,
            inactiveColor = inactiveColor,
            thumbColor = thumbColor,
            trackHeightPx = trackHeight.toPx(),
            waveAmplitudePx = waveAmplitude.toPx() * animatedAmplitudeState.value,
            waveLengthPx = waveLength.toPx(),
            thumbHeightPx = thumbHeight.toPx(),
            phase = phaseState.floatValue,
            wavePath = wavePath,
        )
    }
}

/**
 * True 120 FPS zero-recomposition live M3E wavy seekbar wired directly to `positionMsFlow`.
 *
 * - Smoothly interpolates playback progress AND animates the traveling wave strictly inside
 *   the `Canvas` draw phase at native display refresh rate (60/90/120/144 Hz).
 * - Neither the parent screen (`NowPlayingScreen`, `DesktopDockedPlayerBar`) nor this row
 *   recomposes on playback ticks; only the left timestamp text updates once per whole second.
 */
@Composable
fun ExpressiveLiveWavySeekBar(
    positionMsFlow: StateFlow<Long>,
    durationMs: Long,
    isPlaying: Boolean,
    onSeekMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = durationMs > 0L,
    showInlineTimestamps: Boolean = true,
    timestampFontSize: TextUnit = 12.sp,
    timestampMinWidth: Dp = 38.dp,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant,
    thumbColor: Color = MaterialTheme.colorScheme.primary,
    trackHeight: Dp = 6.dp,
    waveAmplitude: Dp = 4.2.dp,
    waveLength: Dp = 26.dp,
    thumbHeight: Dp = 26.dp,
    barHeight: Dp = 36.dp,
) {
    val anchor = remember { SeekAnchorState() }
    val liveFractionState = remember { mutableFloatStateOf(0f) }
    val phaseState = remember { mutableFloatStateOf(0f) }
    val displayedSecState = remember { mutableLongStateOf(0L) }

    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var pendingSeekFraction by remember { mutableStateOf<Float?>(null) }

    val latestDurationMs by rememberUpdatedState(durationMs)
    val latestDragging by rememberUpdatedState(dragging)
    val latestDragFraction by rememberUpdatedState(dragFraction)
    val latestPending by rememberUpdatedState(pendingSeekFraction)
    val latestOnSeekMs by rememberUpdatedState(onSeekMs)

    LaunchedEffect(enabled) {
        if (!enabled) {
            dragging = false
            pendingSeekFraction = null
        }
    }

    LaunchedEffect(pendingSeekFraction) {
        if (pendingSeekFraction != null) {
            delay(480L)
            pendingSeekFraction = null
        }
    }

    // Collect polled position from playback engine without recomposing the screen.
    LaunchedEffect(positionMsFlow, durationMs) {
        positionMsFlow.collect { posMs ->
            anchor.polledPosMs = posMs.coerceAtLeast(0L)
            anchor.anchorNanos = -1L
            val dur = latestDurationMs
            val polledFrac = if (dur > 0L) (posMs.toFloat() / dur).coerceIn(0f, 1f) else 0f
            val pending = latestPending
            if (pending != null && ExpressiveSeekMath.isPendingSeekSettled(polledFrac, pending)) {
                pendingSeekFraction = null
            }
            if (!latestDragging && latestPending == null) {
                liveFractionState.floatValue = polledFrac
                val sec = (posMs / 1000L).coerceAtLeast(0L)
                if (displayedSecState.longValue != sec) {
                    displayedSecState.longValue = sec
                }
            }
        }
    }

    val shouldAnimateWave = ExpressiveSeekMath.shouldAnimateWave(
        isPlaying = isPlaying,
        enabled = enabled,
        dragging = dragging,
    )
    val animatedAmplitudeState = animateFloatAsState(
        targetValue = if (shouldAnimateWave) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "m3eLiveWaveAmp",
    )

    // Single 120Hz draw-phase frame clock: updates wave phase & interpolated progress
    // strictly inside MutableFloatState read ONLY by Canvas draw scope.
    LaunchedEffect(isPlaying, enabled, durationMs) {
        if (!isPlaying || !enabled || durationMs <= 0L) return@LaunchedEffect
        var lastNanos = 0L
        val radiansPerSec = (2.0 * PI / 1.45).toFloat()
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos).coerceIn(0L, 100_000_000L)) / 1_000_000_000f
                    if (!latestDragging) {
                        phaseState.floatValue = (phaseState.floatValue + dt * radiansPerSec) % (2.0 * PI).toFloat()
                    }
                }
                lastNanos = now

                if (anchor.anchorNanos < 0L) anchor.anchorNanos = now
                val dur = latestDurationMs
                if (dur > 0L) {
                    val elapsedMs = ((now - anchor.anchorNanos) / 1_000_000L).coerceIn(0L, 2_500L)
                    val estMs = (anchor.polledPosMs + elapsedMs).coerceAtMost(dur)
                    val estFrac = (estMs.toFloat() / dur).coerceIn(0f, 1f)
                    val displayedFrac = ExpressiveSeekMath.resolveDisplayedFraction(
                        progress = estFrac,
                        dragging = latestDragging,
                        dragFraction = latestDragFraction,
                        pendingSeekFraction = latestPending,
                    )
                    liveFractionState.floatValue = displayedFrac
                    val shownMs = if (latestDragging || latestPending != null) {
                        (displayedFrac * dur).toLong()
                    } else {
                        estMs
                    }
                    val sec = (shownMs / 1000L).coerceAtLeast(0L)
                    if (displayedSecState.longValue != sec) {
                        displayedSecState.longValue = sec
                    }
                }
            }
        }
    }

    val wavePath = remember { Path() }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showInlineTimestamps) {
            SeekElapsedLabel(
                secState = displayedSecState,
                fontSize = timestampFontSize,
                minWidth = timestampMinWidth,
            )
        }
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(barHeight)
                .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures { offset ->
                        val frac = ExpressiveSeekMath.clampFraction(offset.x / size.width.coerceAtLeast(1))
                        pendingSeekFraction = frac
                        liveFractionState.floatValue = frac
                        val dur = latestDurationMs
                        if (dur > 0L) {
                            displayedSecState.longValue = ((frac * dur).toLong() / 1000L).coerceAtLeast(0L)
                            hapticTick()
                            latestOnSeekMs((frac * dur).toLong())
                        }
                    }
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            pendingSeekFraction = null
                            val frac = ExpressiveSeekMath.clampFraction(offset.x / size.width.coerceAtLeast(1))
                            dragFraction = frac
                            liveFractionState.floatValue = frac
                            val dur = latestDurationMs
                            if (dur > 0L) {
                                displayedSecState.longValue = ((frac * dur).toLong() / 1000L).coerceAtLeast(0L)
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val frac = ExpressiveSeekMath.clampFraction(change.position.x / size.width.coerceAtLeast(1))
                            dragFraction = frac
                            liveFractionState.floatValue = frac
                            val dur = latestDurationMs
                            if (dur > 0L) {
                                displayedSecState.longValue = ((frac * dur).toLong() / 1000L).coerceAtLeast(0L)
                            }
                        },
                        onDragEnd = {
                            val finalFrac = dragFraction
                            dragging = false
                            pendingSeekFraction = finalFrac
                            liveFractionState.floatValue = finalFrac
                            val dur = latestDurationMs
                            if (dur > 0L) {
                                displayedSecState.longValue = ((finalFrac * dur).toLong() / 1000L).coerceAtLeast(0L)
                                hapticTick()
                                latestOnSeekMs((finalFrac * dur).toLong())
                            }
                        },
                        onDragCancel = {
                            dragging = false
                            pendingSeekFraction = null
                        },
                    )
                },
        ) {
            val shown = ExpressiveSeekMath.resolveDisplayedFraction(
                progress = liveFractionState.floatValue,
                dragging = dragging,
                dragFraction = dragFraction,
                pendingSeekFraction = pendingSeekFraction,
            )
            drawWavySeekBar(
                shown = shown,
                dragging = dragging,
                enabled = enabled,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                thumbColor = thumbColor,
                trackHeightPx = trackHeight.toPx(),
                waveAmplitudePx = waveAmplitude.toPx() * animatedAmplitudeState.value,
                waveLengthPx = waveLength.toPx(),
                thumbHeightPx = thumbHeight.toPx(),
                phase = phaseState.floatValue,
                wavePath = wavePath,
            )
        }
        if (showInlineTimestamps) {
            Text(
                text = formatSeekSeconds((durationMs / 1000L).coerceAtLeast(0L)),
                style = Musico.type.tabularCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = timestampFontSize,
                textAlign = TextAlign.End,
                modifier = Modifier.widthIn(min = timestampMinWidth),
            )
        }
    }
}

private class SeekAnchorState {
    var polledPosMs: Long = 0L
    var anchorNanos: Long = -1L
}

@Composable
private fun SeekElapsedLabel(
    secState: LongState,
    fontSize: TextUnit,
    minWidth: Dp,
) {
    Text(
        text = formatSeekSeconds(secState.longValue),
        style = Musico.type.tabularCaption,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = fontSize,
        modifier = Modifier.widthIn(min = minWidth),
    )
}

private fun formatSeekSeconds(totalSecs: Long): String {
    val safe = totalSecs.coerceAtLeast(0L)
    val m = safe / 60L
    val s = safe % 60L
    return "$m:${s.toString().padStart(2, '0')}"
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWavySeekBar(
    shown: Float,
    dragging: Boolean,
    enabled: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    thumbColor: Color,
    trackHeightPx: Float,
    waveAmplitudePx: Float,
    waveLengthPx: Float,
    thumbHeightPx: Float,
    phase: Float,
    wavePath: Path,
) {
    val w = size.width
    val cy = size.height / 2f
    if (w <= 0f) return

    val thumbW = (if (dragging) 6.5.dp else 5.dp).toPx()
    val thumbH = if (dragging) thumbHeightPx + 4.dp.toPx() else thumbHeightPx
    val thumbGap = 6.5.dp.toPx()
    val pad = thumbW / 2f + 2.dp.toPx()
    val usableW = (w - pad * 2f).coerceAtLeast(1f)
    val thumbX = pad + usableW * shown

    // Inactive flat rounded track to the right of the thumb gap
    val inactiveStart = (thumbX + thumbGap).coerceAtMost(w - pad)
    val inactiveLen = (w - pad) - inactiveStart
    if (inactiveLen > 1f) {
        drawLine(
            color = inactiveColor,
            start = Offset(inactiveStart, cy),
            end = Offset(w - pad, cy),
            strokeWidth = trackHeightPx,
            cap = StrokeCap.Round,
        )
        // Official M3E subtle end stop-dot
        if (inactiveLen > trackHeightPx * 2.5f) {
            drawCircle(
                color = activeColor.copy(alpha = 0.45f),
                radius = (trackHeightPx * 0.32f).coerceAtLeast(1.8.dp.toPx()),
                center = Offset(w - pad, cy),
            )
        }
    }

    // Active wavy (or flat when paused) track to the left of the thumb gap
    val activeEnd = (thumbX - thumbGap).coerceAtLeast(pad)
    val activeLen = activeEnd - pad
    if (activeLen > 1f) {
        if (waveAmplitudePx < 0.25f) {
            drawLine(
                color = activeColor,
                start = Offset(pad, cy),
                end = Offset(activeEnd, cy),
                strokeWidth = trackHeightPx,
                cap = StrokeCap.Round,
            )
        } else {
            val wlPx = waveLengthPx.coerceAtLeast(8f)
            val stepPx = 3f
            wavePath.reset()
            var x = pad
            var first = true
            while (x <= activeEnd) {
                val distFromStart = x - pad
                val distFromEnd = activeEnd - x
                val endFade = (min(distFromStart, distFromEnd) / 12f).coerceIn(0f, 1f)
                val y = cy + sin((distFromStart / wlPx) * 2.0 * PI - phase).toFloat() * waveAmplitudePx * endFade
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
                    width = trackHeightPx,
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
