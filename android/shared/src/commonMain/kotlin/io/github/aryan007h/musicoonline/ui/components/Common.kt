package io.github.aryan007h.musicoonline.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.LocalPlatformContext
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.ShelfItem
import io.github.aryan007h.musicoonline.platform.PlatformHorizontalScrollbar
import io.github.aryan007h.musicoonline.ui.player.ArtworkAtmosphere
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.breathing
import io.github.aryan007h.musicoonline.ui.theme.hapticConfirm
import io.github.aryan007h.musicoonline.ui.theme.pressable
import io.github.aryan007h.musicoonline.ui.theme.shimmer
import io.github.aryan007h.musicoonline.ui.theme.staggerIn
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// ── Unified Edge-to-Edge Top Inset (status bar ∪ display cutout, top only) ──

@Composable
fun Modifier.musicoTopInset(): Modifier = this.windowInsetsPadding(
    WindowInsets.statusBars
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top),
)

// ── Ambient Top Wash: seamless cover-extracted atmospheric halo ─────────────

/**
 * Reusable seamless atmospheric top wash.
 *
 * Renders multi-stop radial blooms + a soft top ambient tint inside an
 * offscreen compositing buffer, then multiplies the entire wash with a
 * cubic-eased vertical `BlendMode.DstIn` alpha mask so the wash reaches
 * alpha `0.0f` smoothly BEFORE its geometric bottom edge on every aspect
 * ratio (portrait phones through ultrawide desktop windows) and all palettes.
 */
fun Modifier.musicoWash(
    primary: Color,
    secondary: Color = primary,
    glow: Color = primary,
    intensity: Float = 0.24f,
): Modifier = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawBehind {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@drawBehind
        val safeIntensity = intensity.coerceIn(0f, 0.85f)
        // Soft top-down base tint so wide windows don't leave dark side corners.
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to primary.copy(alpha = safeIntensity * 0.52f),
                0.28f to secondary.copy(alpha = safeIntensity * 0.30f),
                0.62f to secondary.copy(alpha = safeIntensity * 0.08f),
                0.88f to Color.Transparent,
                1.00f to Color.Transparent,
            ),
        )
        // Primary left-biased bloom, height-coupled so it fades cleanly vertically.
        val rPrimary = (minOf(w * 0.88f, h * 1.45f)).coerceAtLeast(h * 0.78f).coerceAtLeast(1f)
        drawRect(
            brush = Brush.radialGradient(
                0.00f to primary.copy(alpha = safeIntensity * 0.92f),
                0.28f to primary.copy(alpha = safeIntensity * 0.60f),
                0.56f to secondary.copy(alpha = safeIntensity * 0.22f),
                0.80f to secondary.copy(alpha = safeIntensity * 0.04f),
                1.00f to Color.Transparent,
                center = Offset(w * 0.22f, h * 0.02f),
                radius = rPrimary,
            ),
        )
        // Secondary warm/accent glow on the opposite side for organic depth.
        val rGlow = (minOf(w * 0.72f, h * 1.18f)).coerceAtLeast(h * 0.68f).coerceAtLeast(1f)
        drawRect(
            brush = Brush.radialGradient(
                0.00f to glow.copy(alpha = safeIntensity * 0.50f),
                0.32f to glow.copy(alpha = safeIntensity * 0.26f),
                0.64f to glow.copy(alpha = safeIntensity * 0.06f),
                1.00f to Color.Transparent,
                center = Offset(w * 0.82f, h * 0.14f),
                radius = rGlow,
            ),
        )
        // Multiplicative DstIn feather: guarantees mathematically smooth 0.0 alpha
        // before the bottom boundary — zero visible horizontal seam at any size.
        drawRect(
            brush = Brush.verticalGradient(
                0.00f to Color.Black,
                0.32f to Color.Black.copy(alpha = 0.88f),
                0.58f to Color.Black.copy(alpha = 0.46f),
                0.78f to Color.Black.copy(alpha = 0.14f),
                0.91f to Color.Black.copy(alpha = 0.02f),
                0.98f to Color.Transparent,
                1.00f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/**
 * Cubic-eased vertical legibility scrim for artwork cards, heroes, and headers.
 * Eliminates the harsh mid-card horizontal band produced by naive 2-stop fades.
 */
fun musicoScrimBrush(
    color: Color,
    maxAlpha: Float = 0.86f,
    startFraction: Float = 0.24f,
): Brush {
    val s0 = startFraction.coerceIn(0f, 0.70f)
    val span = (1f - s0).coerceAtLeast(0.01f)
    val peak = maxAlpha.coerceIn(0f, 1f)
    return Brush.verticalGradient(
        0.00f to Color.Transparent,
        s0 to Color.Transparent,
        (s0 + span * 0.25f) to color.copy(alpha = peak * 0.08f),
        (s0 + span * 0.52f) to color.copy(alpha = peak * 0.34f),
        (s0 + span * 0.78f) to color.copy(alpha = peak * 0.72f),
        1.00f to color.copy(alpha = peak),
    )
}

@Composable
fun AmbientTopWash(
    atmosphere: ArtworkAtmosphere,
    modifier: Modifier = Modifier,
    height: Dp = 380.dp,
    intensity: Float = 0.24f,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .musicoWash(
                primary = atmosphere.orbPrimary,
                secondary = atmosphere.orbSecondary,
                glow = atmosphere.glow,
                intensity = intensity,
            ),
    )
}

// ── Artwork ────────────────────────────────────────────────────────────────

@Composable
fun Artwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Musico.extras.cardRadius,
    placeholderIcon: ImageVector = Icons.Filled.MusicNote,
) {
    val platformContext = LocalPlatformContext.current
    val request = remember(url, platformContext) {
        ImageRequest.Builder(platformContext)
            .data(url)
            .crossfade(220)
            .build()
    }
    val safeCorner = cornerRadius.coerceAtLeast(0.dp)
    SubcomposeAsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(safeCorner)),
        loading = { ArtworkPlaceholder(safeCorner, placeholderIcon) },
        error = { ArtworkPlaceholder(safeCorner, placeholderIcon) },
    )
}

@Composable
private fun ArtworkPlaceholder(cornerRadius: Dp, icon: ImageVector) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius.coerceAtLeast(0.dp)))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ),
            )
            .shimmer(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Musico.extras.inkFaint,
            modifier = Modifier.size(28.dp),
        )
    }
}

// ── Section header ─────────────────────────────────────────────────────────

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title.uppercase(),
            style = Musico.type.section,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

// ── Equalizer bars ─────────────────────────────────────────────────────────

@Composable
fun EqualizerBars(
    playing: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Musico.extras.mint,
    barCount: Int = 4,
) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            repeat(barCount) { i ->
                val bounce = if (playing) {
                    val transition = rememberInfiniteTransition(label = "eq$i")
                    transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 520 + i * 130,
                                delayMillis = i * 110,
                                easing = FastOutSlowInEasing,
                            ),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "bar$i",
                    ).value
                } else {
                    0.45f
                }
                val targetHeight = if (playing) 4.dp + 10.dp * bounce else 3.dp
                val height by animateDpAsState(
                    targetValue = targetHeight,
                    animationSpec = MusicoMotion.snappy(),
                    label = "barH$i",
                )
                Box(
                    Modifier
                        .width(3.dp)
                        .height(height.coerceAtLeast(1.dp))
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (playing) color else Musico.extras.inkFaint)
                        .alpha(if (playing) 1f else 0.4f),
                )
            }
        }
    }
}

// ── Track rows with active thumbnail EQ overlay ────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: OnlineTrack,
    index: Int?,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isLiked: Boolean,
    resolving: Boolean,
    onClick: () -> Unit,
    onLikeToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val bg by animateColorAsState(
        targetValue = if (isCurrent) {
            Musico.extras.primary.copy(alpha = 0.14f)
        } else {
            Color.Transparent
        },
        animationSpec = MusicoMotion.gentle(),
        label = "rowBg",
    )
    Surface(
        color = bg,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .pressable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    pressedScale = 0.985f,
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            index?.let {
                Text(
                    "$it",
                    style = Musico.type.tabularCaption,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isCurrent) Musico.extras.mint else Musico.extras.inkFaint,
                    modifier = Modifier.width(30.dp),
                )
            }
            // Cover thumbnail with live Equalizer overlay when active
            Box(Modifier.size(52.dp)) {
                Artwork(
                    url = track.thumbnail,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 13.dp,
                )
                if (isCurrent) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color.Black.copy(alpha = 0.48f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (resolving) {
                            PulsingDot()
                        } else {
                            EqualizerBars(
                                playing = isPlaying,
                                color = Musico.extras.mint,
                                barCount = 3,
                                modifier = Modifier.height(16.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title.ifEmpty { "Untitled" },
                    style = Musico.type.title,
                    color = if (isCurrent) Musico.extras.mint else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
                val durationStr = track.durationSecs?.takeIf { it > 0 }?.let { secs ->
                    "${secs / 60}:${(secs % 60).toString().padStart(2, '0')}"
                }
                val artistLine = buildString {
                    append(track.artist.ifEmpty { track.album }.ifEmpty { "Unknown artist" })
                    if (durationStr != null) append(" · $durationStr")
                }
                Text(
                    artistLine,
                    style = Musico.type.tabularCaption,
                    color = Musico.extras.inkDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (resolving && !isCurrent) {
                PulsingDot()
                Spacer(Modifier.width(8.dp))
            }
            LikeButton(liked = isLiked, onToggle = onLikeToggle, size = 22.dp)
        }
    }
}

// ── 6-Spark Heart-Burst Celebration LikeButton ─────────────────────────────

@Composable
fun LikeButton(
    liked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 26.dp,
) {
    val color by animateColorAsState(
        targetValue = if (liked) Musico.extras.coral else Musico.extras.inkFaint,
        animationSpec = MusicoMotion.snappy(),
        label = "likeColor",
    )
    val burst = remember { Animatable(1f) }
    val ring = remember { Animatable(0f) }

    LaunchedEffect(liked) {
        if (liked) {
            burst.snapTo(0.45f)
            launch {
                burst.animateTo(
                    1f,
                    spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
                )
            }
            ring.snapTo(0f)
            ring.animateTo(1f, tween(480, easing = MusicoMotion.decelerate))
        } else {
            burst.snapTo(1f)
            ring.snapTo(0f)
        }
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        val ringColor = Musico.extras.coral
        val sparkColor = Musico.extras.mint
        val p = ring.value
        if (p > 0.01f && p < 0.99f) {
            Canvas(Modifier.size(size * 2.5f)) {
                val maxR = this.size.minDimension / 2f
                if (maxR <= 0f) return@Canvas
                val cx = this.size.width / 2f
                val cy = this.size.height / 2f
                // Expanding shockwave ring
                drawCircle(
                    color = ringColor,
                    radius = (maxR * 0.72f * p).coerceAtLeast(0.5f),
                    alpha = ((1f - p) * 0.55f).coerceIn(0f, 1f),
                    style = Stroke(width = (2.dp.toPx() * (1f - p * 0.65f)).coerceAtLeast(0.5f)),
                )
                // 6 radiating celebration sparks
                repeat(6) { i ->
                    val angle = (i * 60.0 - 30.0) * PI / 180.0
                    val dist = maxR * (0.36f + 0.62f * p)
                    val sx = cx + (cos(angle) * dist).toFloat()
                    val sy = cy + (sin(angle) * dist).toFloat()
                    val dotRadius = (2.4.dp.toPx()) * (1f - p * 0.75f)
                    drawCircle(
                        color = if (i % 2 == 0) ringColor else sparkColor,
                        radius = dotRadius.coerceAtLeast(0.5f),
                        center = Offset(sx, sy),
                        alpha = (1f - p).coerceIn(0f, 1f),
                    )
                }
            }
        }
        Icon(
            imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (liked) "Unlike" else "Like",
            tint = color,
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val safeBurst = burst.value.coerceAtLeast(0.01f)
                    scaleX = safeBurst
                    scaleY = safeBurst
                }
                .clickable {
                    hapticConfirm()
                    onToggle()
                },
        )
    }
}

@Composable
fun PulsingDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val scale by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseScale",
    )
    Box(
        modifier
            .size(10.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Musico.extras.primary),
    )
}

// ── Shelf cards & Carousels ────────────────────────────────────────────────

@Composable
fun ShelfCard(
    item: ShelfItem,
    modifier: Modifier = Modifier,
    width: Dp = 160.dp,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .width(width)
            .pressable(onClick = onClick, pressedScale = 0.95f),
    ) {
        Artwork(
            url = item.thumbnail,
            contentDescription = item.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = Musico.extras.cardRadius,
            placeholderIcon = if (item.videoId != null) Icons.Filled.MusicNote else Icons.Filled.Album,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            item.title,
            style = Musico.type.title,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp,
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                item.subtitle,
                style = Musico.type.caption,
                color = Musico.extras.inkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun ShelfCarousel(
    shelfTitle: String,
    items: List<ShelfItem>,
    onItemClick: (ShelfItem, List<ShelfItem>) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 160.dp,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier) {
        SectionHeader(
            title = shelfTitle,
            trailing = {
                ShelfScrollButtons(
                    canScrollBackward = listState.canScrollBackward,
                    canScrollForward = listState.canScrollForward,
                    onScrollBackward = {
                        val step = HorizontalScrollMath.computePageStepPx(viewportWidthPx)
                        scope.launch {
                            listState.animateScrollBy(-step, tween(320, easing = MusicoMotion.emphasized))
                        }
                    },
                    onScrollForward = {
                        val step = HorizontalScrollMath.computePageStepPx(viewportWidthPx)
                        scope.launch {
                            listState.animateScrollBy(step, tween(320, easing = MusicoMotion.emphasized))
                        }
                    },
                )
            },
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { viewportWidthPx = it.width.toFloat() }
                .desktopHorizontalScroll(listState),
        ) {
            itemsIndexed(items) { i, item ->
                ShelfCard(
                    item = item,
                    width = cardWidth,
                    onClick = { onItemClick(item, items) },
                    modifier = Modifier.staggerIn(index = i),
                )
            }
        }
        PlatformHorizontalScrollbar(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp),
        )
    }
}

// ── States ────────────────────────────────────────────────────────────────

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    StateShell(modifier.staggerIn(index = 0)) {
        Icon(
            Icons.Filled.GraphicEq,
            contentDescription = null,
            tint = Musico.extras.coral,
            modifier = Modifier
                .size(40.dp)
                .breathing(max = 1.08f),
        )
        Spacer(Modifier.height(14.dp))
        Text("Something skipped a beat", style = Musico.type.title, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = Musico.type.body,
            color = Musico.extras.inkDim,
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onRetry,
                shape = MusicoShapes.chip,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.pressable(onClick = onRetry, pressedScale = 0.94f),
            ) {
                Text("Try again", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
) {
    StateShell(modifier.staggerIn(index = 0)) {
        Icon(
            icon,
            contentDescription = null,
            tint = Musico.extras.inkFaint,
            modifier = Modifier
                .size(40.dp)
                .breathing(),
        )
        Spacer(Modifier.height(14.dp))
        Text(title, style = Musico.type.title, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = Musico.type.body, color = Musico.extras.inkDim, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StateShell(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun MiniPlayFab(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (playing) 1f else 0f,
        animationSpec = MusicoMotion.expressive(),
        label = "fabScale",
    )
    Surface(
        shape = CircleShape,
        color = Musico.extras.mint,
        shadowElevation = 6.dp,
        modifier = modifier
            .size(44.dp)
            .scale(scale.coerceAtLeast(0f))
            .pressable(onClick = onClick, pressedScale = 0.86f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = playing,
                transitionSpec = {
                    (scaleIn(initialScale = 0.55f, animationSpec = MusicoMotion.snappy()) +
                        fadeIn(tween(MusicoMotion.FAST))) togetherWith
                        (scaleOut(targetScale = 0.55f, animationSpec = tween(MusicoMotion.FAST)) +
                            fadeOut(tween(MusicoMotion.FAST)))
                },
                label = "fabIcon",
            ) { isPlaying ->
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color(0xFF05291D),
                    modifier = Modifier.size(26.dp),
                )
            }
        }
    }
}

// ── Matching-Geometry Shimmer Skeletons ──────────────────────────────────

@Composable
fun TrackRowSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .shimmer(),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(0.68f)
                    .height(14.dp)
                    .clip(MusicoShapes.chip)
                    .shimmer(),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.42f)
                    .height(11.dp)
                    .clip(MusicoShapes.chip)
                    .shimmer(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .size(width = 32.dp, height = 11.dp)
                .clip(MusicoShapes.chip)
                .shimmer(),
        )
    }
}

@Composable
fun TrackListSkeleton(count: Int = 7, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { TrackRowSkeleton() }
    }
}

@Composable
fun DetailPageSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MusicoShapes.hero)
                .shimmer(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier
                    .size(width = 112.dp, height = 44.dp)
                    .clip(MusicoShapes.chip)
                    .shimmer(),
            )
            Box(
                Modifier
                    .size(width = 96.dp, height = 44.dp)
                    .clip(MusicoShapes.chip)
                    .shimmer(),
            )
            Box(
                Modifier
                    .size(width = 96.dp, height = 44.dp)
                    .clip(MusicoShapes.chip)
                    .shimmer(),
            )
        }
        TrackListSkeleton(count = 5)
    }
}

