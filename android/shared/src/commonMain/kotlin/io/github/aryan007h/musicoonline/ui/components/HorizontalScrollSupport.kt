package io.github.aryan007h.musicoonline.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import io.github.aryan007h.musicoonline.platform.isDesktopPlatform
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class HorizontalWheelAction {
    IGNORE,
    SCROLL_HORIZONTAL,
}

data class WheelScrollDecision(
    val action: HorizontalWheelAction,
    val deltaPx: Float,
    val smooth: Boolean,
)

data class GridScrollbarThumb(
    val fraction: Float,
    val progress: Float,
)

/**
 * Pure, platform-agnostic math and gesture policies for desktop horizontal
 * scrolling (Shift+Wheel, 2-finger trackpad axis lock, mouse-drag inertia,
 * carousel page stepping, and grid scrollbar geometry).
 */
object HorizontalScrollMath {
    private const val EPS = 1e-3f
    private const val AXIS_LOCK_RATIO = 0.85f
    private const val DISCRETE_WHEEL_THRESHOLD = 0.75f
    private const val FLING_MIN_VELOCITY_PX_PER_SEC = 160f

    fun decideHorizontalWheel(
        deltaX: Float,
        deltaY: Float,
        isShiftPressed: Boolean,
        wheelStepPx: Float,
    ): WheelScrollDecision {
        val safeStep = wheelStepPx.coerceAtLeast(1f)
        val absX = abs(deltaX)
        val absY = abs(deltaY)

        // Shift + vertical wheel (especially on Linux Wayland/X11 where AWT keeps
        // wheel rotation on the Y axis even when Shift is held).
        if (isShiftPressed && absY > EPS && absX < EPS) {
            return WheelScrollDecision(
                action = HorizontalWheelAction.SCROLL_HORIZONTAL,
                deltaPx = deltaY * safeStep,
                smooth = true,
            )
        }

        // Intentional horizontal scroll (tilt-wheel, Shift+Wheel mapped to X, or
        // horizontally-dominant 2-finger touchpad swipe).
        if (absX > EPS && (isShiftPressed || absX >= absY * AXIS_LOCK_RATIO)) {
            val smooth = isShiftPressed || absX >= DISCRETE_WHEEL_THRESHOLD
            return WheelScrollDecision(
                action = HorizontalWheelAction.SCROLL_HORIZONTAL,
                deltaPx = deltaX * safeStep,
                smooth = smooth,
            )
        }

        // Purely vertical or vertically-dominant scroll passes straight through
        // to the enclosing LazyColumn / vertical container.
        return WheelScrollDecision(
            action = HorizontalWheelAction.IGNORE,
            deltaPx = 0f,
            smooth = false,
        )
    }

    fun computePageStepPx(
        viewportWidthPx: Float,
        fraction: Float = 0.78f,
        minStepPx: Float = 240f,
    ): Float {
        if (viewportWidthPx <= 0f) return minStepPx
        return (viewportWidthPx * fraction.coerceIn(0.25f, 1f)).coerceAtLeast(minStepPx)
    }

    fun computeDragFlingPx(
        velocityPxPerSec: Float,
        viewportWidthPx: Float,
    ): Float {
        if (abs(velocityPxPerSec) < FLING_MIN_VELOCITY_PX_PER_SEC) return 0f
        val maxGlide = (if (viewportWidthPx > 0f) viewportWidthPx else 960f) * 1.15f
        // Dragging pointer left (negative velocity) scrolls content forward (positive offset).
        return (-velocityPxPerSec * 0.22f).coerceIn(-maxGlide, maxGlide)
    }

    fun computePagerSnapTarget(
        currentPage: Int,
        pageCount: Int,
        dragTotalDxPx: Float,
        velocityPxPerSec: Float,
        viewportWidthPx: Float,
    ): Int {
        if (pageCount <= 1) return 0
        val clamped = currentPage.coerceIn(0, pageCount - 1)
        val threshold = maxOf(64f, viewportWidthPx * 0.22f)
        return when {
            velocityPxPerSec <= -320f || dragTotalDxPx <= -threshold ->
                (clamped + 1).coerceAtMost(pageCount - 1)
            velocityPxPerSec >= 320f || dragTotalDxPx >= threshold ->
                (clamped - 1).coerceAtLeast(0)
            else -> clamped
        }
    }

    fun computeWheelPagerStep(
        deltaX: Float,
        deltaY: Float,
        isShiftPressed: Boolean,
    ): Int {
        val absX = abs(deltaX)
        val absY = abs(deltaY)
        val axisDelta = when {
            isShiftPressed && absY > 0.05f && absX < 0.05f -> deltaY
            absX > 0.08f && (isShiftPressed || absX >= absY * AXIS_LOCK_RATIO) -> deltaX
            else -> 0f
        }
        return when {
            axisDelta > 0f -> 1
            axisDelta < 0f -> -1
            else -> 0
        }
    }

    fun computeGridScrollbarThumb(
        totalItems: Int,
        rowCount: Int,
        firstVisibleColumn: Int,
        firstColumnHiddenRatio: Float,
        visibleColumns: Float,
    ): GridScrollbarThumb {
        val safeRows = rowCount.coerceAtLeast(1)
        val totalCols = ((totalItems + safeRows - 1) / safeRows).coerceAtLeast(1)
        val clampedVisible = visibleColumns.coerceIn(0.5f, totalCols.toFloat())
        val fraction = (clampedVisible / totalCols.toFloat()).coerceIn(0.14f, 1f)
        val maxScrollCols = (totalCols.toFloat() - clampedVisible).coerceAtLeast(0f)
        if (maxScrollCols <= 0.01f) {
            return GridScrollbarThumb(fraction = 1f, progress = 0f)
        }
        val exactCol = (firstVisibleColumn.coerceAtLeast(0) + firstColumnHiddenRatio.coerceIn(0f, 1f))
        val progress = (exactCol / maxScrollCols).coerceIn(0f, 1f)
        return GridScrollbarThumb(fraction = fraction, progress = progress)
    }
}

/**
 * Adds native Desktop horizontal scrolling to any [ScrollableState]
 * (`LazyListState`, `LazyGridState`, `ScrollState`):
 * - Left-mouse click-and-drag with smooth release inertia (Compose 1.7 disables
 *   mouse drag in `ScrollableNode`, while still preserving clean card clicks
 *   below touch slop).
 * - Shift + vertical mouse wheel (critical on Linux Wayland/X11 where AWT
 *   keeps wheel deltas on Y).
 * - Smooth, axis-locked horizontal touchpad / tilt-wheel scrolling without
 *   hijacking vertical page scroll.
 *
 * No-op on Android so native touch physics remain untouched.
 */
fun Modifier.desktopHorizontalScroll(state: ScrollableState): Modifier =
    if (!isDesktopPlatform) {
        this
    } else {
        composed {
            val scope = rememberCoroutineScope()
            val density = LocalDensity.current
            val touchSlopPx = remember(density) { with(density) { 8.dp.toPx() } }
            val wheelStepPx = remember(density) { with(density) { 96.dp.toPx() } }
            var viewportWidthPx by remember { mutableFloatStateOf(0f) }

            val animState = remember(state) {
                object {
                    var activeJob: Job? = null
                    var pendingWheelPx: Float = 0f
                }
            }

            fun cancelActiveAnimation() {
                animState.activeJob?.cancel()
                animState.activeJob = null
                animState.pendingWheelPx = 0f
            }

            fun scheduleSmoothWheel(deltaPx: Float) {
                animState.pendingWheelPx += deltaPx
                animState.activeJob?.cancel()
                val target = animState.pendingWheelPx
                animState.activeJob = scope.launch {
                    state.scroll(MutatePriority.UserInput) {
                        var previous = 0f
                        animate(
                            initialValue = 0f,
                            targetValue = target,
                            animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                        ) { value, _ ->
                            val step = value - previous
                            previous = value
                            scrollBy(step)
                            animState.pendingWheelPx -= step
                        }
                    }
                    if (abs(animState.pendingWheelPx) < 1f) {
                        animState.pendingWheelPx = 0f
                    }
                }
            }

            this
                .onSizeChanged { viewportWidthPx = it.width.toFloat() }
                // 1) Shift+Wheel & horizontal touchpad/tilt-wheel handler.
                .pointerInput(state, wheelStepPx) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type != PointerEventType.Scroll) continue
                            val delta = event.changes.fold(Offset.Zero) { acc, change ->
                                acc + change.scrollDelta
                            }
                            val decision = HorizontalScrollMath.decideHorizontalWheel(
                                deltaX = delta.x,
                                deltaY = delta.y,
                                isShiftPressed = event.keyboardModifiers.isShiftPressed,
                                wheelStepPx = wheelStepPx,
                            )
                            if (decision.action == HorizontalWheelAction.SCROLL_HORIZONTAL) {
                                event.changes.forEach { it.consume() }
                                if (decision.smooth) {
                                    scheduleSmoothWheel(decision.deltaPx)
                                } else {
                                    cancelActiveAnimation()
                                    state.dispatchRawDelta(decision.deltaPx)
                                }
                            }
                        }
                    }
                }
                // 2) Mouse primary-button drag-to-scroll with inertia.
                .pointerInput(state, touchSlopPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        if (down.type != PointerType.Mouse) return@awaitEachGesture
                        if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture

                        var lastPos = down.position
                        var lastTimeMs = down.uptimeMillis
                        var totalDx = 0f
                        var totalDy = 0f
                        var dragging = false
                        var velocityPxPerSec = 0f

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val nowPos = change.position
                            val dx = nowPos.x - lastPos.x
                            val dy = nowPos.y - lastPos.y
                            val dtMs = (change.uptimeMillis - lastTimeMs).coerceAtLeast(1L)

                            if (!change.pressed) {
                                if (dragging) {
                                    // Consume mouse-up in Initial so child clickable cards do not fire.
                                    change.consume()
                                    val glidePx = HorizontalScrollMath.computeDragFlingPx(
                                        velocityPxPerSec = velocityPxPerSec,
                                        viewportWidthPx = viewportWidthPx,
                                    )
                                    if (abs(glidePx) >= 12f) {
                                        animState.activeJob?.cancel()
                                        animState.activeJob = scope.launch {
                                            state.animateScrollBy(
                                                value = glidePx,
                                                animationSpec = tween(
                                                    durationMillis = 320,
                                                    easing = FastOutSlowInEasing,
                                                ),
                                            )
                                        }
                                    }
                                }
                                break
                            }

                            totalDx += dx
                            totalDy += dy

                            if (!dragging) {
                                if (abs(totalDy) > touchSlopPx && abs(totalDy) > abs(totalDx)) {
                                    // Vertical gesture — leave untouched.
                                    break
                                }
                                if (abs(totalDx) > touchSlopPx && abs(totalDx) >= abs(totalDy)) {
                                    dragging = true
                                    cancelActiveAnimation()
                                    change.consume()
                                    state.dispatchRawDelta(-totalDx)
                                }
                            } else {
                                change.consume()
                                if (abs(dx) > 0.01f) {
                                    state.dispatchRawDelta(-dx)
                                    val instVel = (dx / dtMs.toFloat()) * 1000f
                                    velocityPxPerSec = velocityPxPerSec * 0.55f + instVel * 0.45f
                                }
                            }

                            lastPos = nowPos
                            lastTimeMs = change.uptimeMillis
                        }
                    }
                }
        }
    }

/**
 * Adds desktop mouse-drag (with clean page snap on release) and Shift+Wheel /
 * horizontal wheel page stepping to any [PagerState] (`HorizontalPager`).
 * No-op on Android.
 */
fun Modifier.desktopHorizontalPagerScroll(pagerState: PagerState): Modifier =
    if (!isDesktopPlatform) {
        this
    } else {
        composed {
            val scope = rememberCoroutineScope()
            val density = LocalDensity.current
            val touchSlopPx = remember(density) { with(density) { 8.dp.toPx() } }
            var viewportWidthPx by remember { mutableFloatStateOf(0f) }
            val wheelGate = remember(pagerState) {
                object {
                    var lastStepMs: Long = 0L
                }
            }

            this
                .onSizeChanged { viewportWidthPx = it.width.toFloat() }
                .pointerInput(pagerState) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.type != PointerEventType.Scroll) continue
                            val delta = event.changes.fold(Offset.Zero) { acc, c -> acc + c.scrollDelta }
                            val step = HorizontalScrollMath.computeWheelPagerStep(
                                deltaX = delta.x,
                                deltaY = delta.y,
                                isShiftPressed = event.keyboardModifiers.isShiftPressed,
                            )
                            if (step != 0 && pagerState.pageCount > 1) {
                                event.changes.forEach { it.consume() }
                                val nowMs = event.changes.firstOrNull()?.uptimeMillis ?: 0L
                                if (nowMs - wheelGate.lastStepMs >= 240L) {
                                    wheelGate.lastStepMs = nowMs
                                    val target = (pagerState.currentPage + step)
                                        .coerceIn(0, pagerState.pageCount - 1)
                                    if (target != pagerState.currentPage) {
                                        scope.launch { pagerState.animateScrollToPage(target) }
                                    }
                                }
                            }
                        }
                    }
                }
                .pointerInput(pagerState, touchSlopPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        if (down.type != PointerType.Mouse) return@awaitEachGesture
                        if (!currentEvent.buttons.isPrimaryPressed) return@awaitEachGesture

                        var lastPos = down.position
                        var lastTimeMs = down.uptimeMillis
                        var totalDx = 0f
                        var totalDy = 0f
                        var dragging = false
                        var velocityPxPerSec = 0f
                        val startPage = pagerState.currentPage

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val nowPos = change.position
                            val dx = nowPos.x - lastPos.x
                            val dy = nowPos.y - lastPos.y
                            val dtMs = (change.uptimeMillis - lastTimeMs).coerceAtLeast(1L)

                            if (!change.pressed) {
                                if (dragging) {
                                    change.consume()
                                    val target = HorizontalScrollMath.computePagerSnapTarget(
                                        currentPage = startPage,
                                        pageCount = pagerState.pageCount,
                                        dragTotalDxPx = totalDx,
                                        velocityPxPerSec = velocityPxPerSec,
                                        viewportWidthPx = viewportWidthPx,
                                    )
                                    scope.launch { pagerState.animateScrollToPage(target) }
                                }
                                break
                            }

                            totalDx += dx
                            totalDy += dy

                            if (!dragging) {
                                if (abs(totalDy) > touchSlopPx && abs(totalDy) > abs(totalDx)) break
                                if (abs(totalDx) > touchSlopPx && abs(totalDx) >= abs(totalDy)) {
                                    dragging = true
                                    change.consume()
                                    pagerState.dispatchRawDelta(-totalDx)
                                }
                            } else {
                                change.consume()
                                if (abs(dx) > 0.01f) {
                                    pagerState.dispatchRawDelta(-dx)
                                    val instVel = (dx / dtMs.toFloat()) * 1000f
                                    velocityPxPerSec = velocityPxPerSec * 0.55f + instVel * 0.45f
                                }
                            }

                            lastPos = nowPos
                            lastTimeMs = change.uptimeMillis
                        }
                    }
                }
        }
    }

/**
 * Clean Material 3 Expressive prev/next shelf scroll buttons rendered in section
 * headers on Desktop whenever the shelf has horizontal overflow.
 * Renders nothing on Android.
 */
@Composable
fun ShelfScrollButtons(
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    onScrollBackward: () -> Unit,
    onScrollForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isDesktopPlatform) return
    if (!canScrollBackward && !canScrollForward) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShelfNavCircleButton(
            enabled = canScrollBackward,
            contentDescription = "Scroll left",
            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            onClick = onScrollBackward,
        )
        ShelfNavCircleButton(
            enabled = canScrollForward,
            contentDescription = "Scroll right",
            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            onClick = onScrollForward,
        )
    }
}

@Composable
private fun ShelfNavCircleButton(
    enabled: Boolean,
    contentDescription: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.28f,
        animationSpec = tween(durationMillis = MusicoMotion.FAST),
        label = "shelfNavAlpha",
    )
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .size(30.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .then(
                if (enabled) {
                    Modifier
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Horizontally scrollable chip/palette strip with Desktop mouse drag,
 * Shift+Wheel, and sleek edge scroll chevrons whenever content overflows.
 */
@Composable
fun DesktopScrollableChipRow(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp),
    edgeFadeColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable RowScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val stepPx = remember(density) { with(density) { 200.dp.toPx() } }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .desktopHorizontalScroll(scrollState)
                .horizontalScroll(scrollState)
                .padding(contentPadding),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )

        if (isDesktopPlatform) {
            AnimatedVisibility(
                visible = scrollState.canScrollBackward,
                enter = fadeIn(tween(MusicoMotion.FAST)),
                exit = fadeOut(tween(MusicoMotion.FAST)),
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(40.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(edgeFadeColor, edgeFadeColor.copy(alpha = 0.85f), Color.Transparent),
                            ),
                        ),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                scope.launch {
                                    scrollState.animateScrollBy(
                                        -stepPx,
                                        tween(240, easing = MusicoMotion.emphasized),
                                    )
                                }
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Scroll left",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = scrollState.canScrollForward,
                enter = fadeIn(tween(MusicoMotion.FAST)),
                exit = fadeOut(tween(MusicoMotion.FAST)),
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(40.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, edgeFadeColor.copy(alpha = 0.85f), edgeFadeColor),
                            ),
                        ),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(26.dp)
                            .clip(CircleShape)
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable {
                                scope.launch {
                                    scrollState.animateScrollBy(
                                        stepPx,
                                        tween(240, easing = MusicoMotion.emphasized),
                                    )
                                }
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Scroll right",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
