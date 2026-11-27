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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    val bgColor by animateColorAsState(
        targetValue = if (isPlaying) containerColorPlaying else containerColorPaused,
        animationSpec = MusicoMotion.effectsDefault(),
        label = "m3ePlayBg",
    )
    val iconColor by animateColorAsState(
        targetValue = if (isPlaying) contentColorPlaying else contentColorPaused,
        animationSpec = MusicoMotion.effectsDefault(),
        label = "m3ePlayIcon",
    )

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
    width: Dp = 74.dp,
    height: Dp = 68.dp,
    iconSize: Dp = 28.dp,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue = if (pressed) 16.dp else 24.dp,
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
    val bg by animateColorAsState(
        targetValue = containerColor,
        animationSpec = MusicoMotion.effectsFast(),
        label = "m3ePillBg",
    )
    val fg by animateColorAsState(
        targetValue = contentColor,
        animationSpec = MusicoMotion.effectsFast(),
        label = "m3ePillFg",
    )
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
    unselectedColor: Color = if (Musico.extras.isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    },
) {
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
                isSelected && emphasized -> MaterialTheme.colorScheme.primary
                isSelected -> MaterialTheme.colorScheme.secondaryContainer
                else -> unselectedColor
            }
            val targetFg = when {
                isSelected && emphasized -> MaterialTheme.colorScheme.onPrimary
                isSelected -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val bg by animateColorAsState(targetBg, MusicoMotion.effectsFast(), label = "cbBg$index")
            val fg by animateColorAsState(targetFg, MusicoMotion.effectsFast(), label = "cbFg$index")

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

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .graphicsLayer {
                        val safe = safePositiveScale(scale)
                        scaleX = safe
                        scaleY = safe
                    }
                    .clip(shape)
                    .background(bg)
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
                contentAlignment = Alignment.Center,
            ) {
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

/**
 * Material 3 Expressive shape-morphing filter chip:
 * - Unselected: soft `12dp` rounded rectangle on tonal container
 * - Selected: spring-morphs into a full stadium pill (`999dp`) in `primary` / `secondaryContainer`
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
    val corner by animateDpAsState(
        targetValue = if (selected) 20.dp else 11.dp,
        animationSpec = MusicoMotion.spatialFast(),
        label = "m3eChipCorner",
    )
    val bg by animateColorAsState(
        targetValue = when {
            selected && usePrimaryWhenSelected -> MaterialTheme.colorScheme.primary
            selected -> MaterialTheme.colorScheme.secondaryContainer
            Musico.extras.isDark -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        },
        animationSpec = MusicoMotion.effectsFast(),
        label = "m3eChipBg",
    )
    val fg by animateColorAsState(
        targetValue = when {
            selected && usePrimaryWhenSelected -> MaterialTheme.colorScheme.onPrimary
            selected -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MusicoMotion.effectsFast(),
        label = "m3eChipFg",
    )
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
        border = if (!selected && !Musico.extras.isDark) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        } else {
            null
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
    val shape = remember(index, count) {
        ExpressiveSegmentedMath.verticalListCorners(index, count).toRoundedCornerShape()
    }
    Surface(
        shape = shape,
        color = containerColor,
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
    inactiveColor: Color = MaterialTheme.colorScheme.outlineVariant,
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
                    val endFade = (min(distFromStart, distFromEnd) / 10f).coerceIn(0f, 1f)
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
