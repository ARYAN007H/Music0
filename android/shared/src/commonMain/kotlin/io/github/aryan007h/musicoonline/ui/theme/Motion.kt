package io.github.aryan007h.musicoonline.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aryan007h.musicoonline.platform.HapticKind
import io.github.aryan007h.musicoonline.platform.performHaptic

/**
 * Material 3 Expressive physics-based motion tokens:
 * - **Spatial springs** (`spatialFast`, `spatialDefault`, `spatialSlow`) carry playful
 *   underdamped bounce for layout, shape morphing, scale, and position.
 * - **Effects springs** (`effectsFast`, `effectsDefault`, `effectsSlow`) are critically
 *   damped (no overshoot) for color, alpha, and tonal elevation shifts.
 */
object MusicoMotion {

    // ── Official Material 3 Expressive Spatial Springs (bouncy) ──────────

    fun <T> spatialFast(): SpringSpec<T> = spring(
        dampingRatio = 0.62f,
        stiffness = 780f,
    )

    fun <T> spatialDefault(): SpringSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = 380f,
    )

    fun <T> spatialSlow(): SpringSpec<T> = spring(
        dampingRatio = 0.80f,
        stiffness = 200f,
    )

    // ── Official Material 3 Expressive Effects Springs (no overshoot) ────

    fun <T> effectsFast(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 1400f,
    )

    fun <T> effectsDefault(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 650f,
    )

    fun <T> effectsSlow(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 300f,
    )

    // ── Legacy / Semantic Aliases ────────────────────────────────────────

    fun <T> expressive(): SpringSpec<T> = spring(
        dampingRatio = 0.65f,
        stiffness = 440f,
    )

    fun <T> snappy(): SpringSpec<T> = spatialFast()

    fun <T> gentle(): SpringSpec<T> = effectsDefault()

    fun <T> spatial(): SpringSpec<T> = spatialDefault()

    fun <T> cinema(): SpringSpec<T> = spring(
        dampingRatio = 0.90f,
        stiffness = 220f,
    )

    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    val easeOutBack: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    const val FAST = 150
    const val MED = 240
    const val SLOW = 350
    const val SPATIAL = 450
}

fun Modifier.pressScale(
    source: MutableInteractionSource,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MusicoMotion.spatialFast(),
        label = "pressScale",
    )
    graphicsLayer {
        val safe = safePositiveScale(scale)
        scaleX = safe
        scaleY = safe
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.pressable(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val clickModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
        )
    } else {
        Modifier.clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
    }
    this
        .pressScale(source, pressedScale)
        .then(clickModifier)
}

/**
 * Adaptive Light/Dark skeleton shimmer: defaults to theme-aware `onSurface` alpha
 * so loading placeholders remain crisp on both light lavender and dark obsidian surfaces.
 */
fun Modifier.shimmer(highlight: Color = Color.Unspecified): Modifier = composed {
    val resolvedHighlight = if (highlight == Color.Unspecified) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = if (Musico.extras.isDark) 0.13f else 0.085f)
    } else {
        highlight
    }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val pulse by transition.animateFloat(
        initialValue = 0.20f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shimmerPulse",
    )
    drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind
        drawRect(color = resolvedHighlight.copy(alpha = resolvedHighlight.alpha * pulse))
    }
}

fun Modifier.breathing(
    min: Float = 1f,
    max: Float = 1.045f,
    durationMs: Int = 1900,
): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "breathing")
    val scale by transition.animateFloat(
        initialValue = min,
        targetValue = max,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathingScale",
    )
    graphicsLayer {
        val safe = safePositiveScale(scale)
        scaleX = safe
        scaleY = safe
    }
}

fun Modifier.staggerIn(
    index: Int,
    stepMs: Long = 38,
    drop: Dp = 14.dp,
): Modifier = composed {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index.coerceAtMost(9) * stepMs)
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(300, easing = MusicoMotion.decelerate),
        label = "staggerAlpha",
    )
    val y by animateDpAsState(
        targetValue = if (shown) 0.dp else drop,
        animationSpec = MusicoMotion.spatialDefault(),
        label = "staggerY",
    )
    graphicsLayer {
        this.alpha = alpha.coerceIn(0f, 1f)
        translationY = y.toPx()
    }
}

/** Clamps spring-animated corner radii so Compose never sees a negative CornerSize. */
fun safeCornerDp(radius: Dp): Dp = radius.coerceAtLeast(0.dp)

/** Clamps spring-animated scale factors strictly positive to prevent inverted geometry. */
fun safePositiveScale(scale: Float): Float = scale.coerceAtLeast(0.01f)

// ── Cross-platform Haptics ────────────────────────────────────────────────

fun hapticTick() = performHaptic(HapticKind.TICK)

fun hapticThud() = performHaptic(HapticKind.TAP)

fun hapticConfirm() = performHaptic(HapticKind.CONFIRM)

fun hapticLongPress() = performHaptic(HapticKind.LONG_PRESS)
