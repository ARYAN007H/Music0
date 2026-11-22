package io.github.aryan007h.musicoonline.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aryan007h.musicoonline.platform.HapticKind
import io.github.aryan007h.musicoonline.platform.performHaptic

object MusicoMotion {

    fun <T> expressive(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun <T> snappy(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> gentle(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

    fun <T> spatial(): SpringSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = 380f,
    )

    fun <T> cinema(): SpringSpec<T> = spring(
        dampingRatio = 0.92f,
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
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
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

fun Modifier.shimmer(highlight: Color = Color.White.copy(alpha = 0.30f)): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing)),
        label = "shimmerProgress",
    )
    drawBehind {
        if (size.width <= 0f || size.height <= 0f) return@drawBehind
        val x = (progress * 2f - 0.6f) * size.width
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, highlight, Color.Transparent),
                start = Offset(x - size.width * 0.25f, 0f),
                end = Offset(x + size.width * 0.25f, size.height),
            ),
        )
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
        animationSpec = tween(300, easing = MusicoMotion.decelerate),
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
