package io.github.aryan007h.musicoonline.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one motion vocabulary every surface in the app speaks. Apple reads as
 * "physical — mass with intent", ColorOS 17 as "playful — springs that
 * overshoot a beat and settle". The family below is tuned between the two:
 * springs for anything a finger touches (they push back), curated beziers
 * for ambient choreography (they carry, never bounce).
 *
 * Rules of the house:
 * - Touch feedback = squish. Every tappable thing scales down ~4–8% and
 *   springs back with a soft overshoot on release.
 * - Entrances decelerate, exits accelerate. Nothing pops in at full speed
 *   or fades out slowly.
 * - Big surfaces move with [spatial]; small icons with [snappy]; ambient
 *   loops (breathing, shimmer, EQ) never use springs.
 */
object MusicoMotion {

    // ── The spring family ────────────────────────────────────────────────

    /** Hero morphs: mini player → full player, nav pill blob, FAB pops. */
    fun <T> expressive(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** Small elements: icons, chips, hearts. Quick, one visible bounce. */
    fun <T> snappy(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    /** No-bounce default for fades, colors and containers. */
    fun <T> gentle(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /** Large surfaces crossing the screen (player modal, detail push). */
    fun <T> spatial(): SpringSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = 380f,
    )

    // ── Curated beziers (for tweens where springs would ring) ────────────

    /** M3 emphasized — the default "carried by the system" curve. */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Entrances: leaves the gate fast, settles gently. */
    val decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Exits: pulls away gently, leaves fast. */
    val accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** One-shot overshoot for things that must feel like a rubber ball. */
    val easeOutBack: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    // ── Duration tokens ──────────────────────────────────────────────────

    const val FAST = 150
    const val MED = 240
    const val SLOW = 350
    const val SPATIAL = 450
}

// ── Touch: the squish ─────────────────────────────────────────────────────

/**
 * Scales the node down while [source] reports a press, springs back with a
 * soft overshoot on release. Pair with the same source you hand to your
 * clickable — or just use [pressable] which wires both.
 */
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
        scaleX = scale
        scaleY = scale
    }
}

/**
 * One-stop touchable: press-squish + ripple + (optional) long press in a
 * single modifier. Drop-in replacement for `.clickable { … }`.
 */
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

// ── Ambient loops ─────────────────────────────────────────────────────────

/**
 * Skeleton shimmer: a soft diagonal highlight sweeping across whatever the
 * node already paints. Apply over a solid `.background(surfaceVariant)`.
 */
fun Modifier.shimmer(highlight: Color = Color.White.copy(alpha = 0.30f)): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing)),
        label = "shimmerProgress",
    )
    drawBehind {
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

/**
 * Quiet idle pulse — empty-state icons, "tap me" affordances. Too slow to
 * notice consciously; just enough to read as alive.
 */
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
        scaleX = scale
        scaleY = scale
    }
}

/**
 * ColorOS-style cascade: children of a fresh screen arrive top-to-bottom,
 * each [stepMs] after the previous, dropping in with a decelerating fade.
 * Index is capped so long lists don't queue a second-long entrance.
 */
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
        this.alpha = alpha
        translationY = y.toPx()
    }
}

// ── Haptics: the invisible half of every interaction ──────────────────────

/** Light tick for selections, likes, tab switches — ColorOS-style textures. */
fun android.view.View?.tick() {
    this?.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
}

/** A touch firmer, for confirmations (play/pause, submit). */
fun android.view.View?.thud() {
    this?.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
}
