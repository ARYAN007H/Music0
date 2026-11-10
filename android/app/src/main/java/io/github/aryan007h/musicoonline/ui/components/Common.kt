package io.github.aryan007h.musicoonline.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.ShelfItem
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes

// ── Artwork ────────────────────────────────────────────────────────────────

/** Artwork with crossfade + a tinted placeholder that keeps layout rhythm while loading. */
@Composable
fun Artwork(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = Musico.extras.cardRadius,
    placeholderIcon: ImageVector = Icons.Filled.MusicNote,
) {
    SubcomposeAsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
        loading = { ArtworkPlaceholder(cornerRadius, placeholderIcon) },
        error = { ArtworkPlaceholder(cornerRadius, placeholderIcon) },
    )
}

@Composable
private fun ArtworkPlaceholder(cornerRadius: Dp, icon: ImageVector) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ),
            ),
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

// ── Section header: the loud uppercase Apple-Music-style label ────────────

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
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            title.uppercase(),
            style = Musico.type.section,
            color = MaterialTheme.colorScheme.onBackground,
        )
        trailing?.invoke()
    }
}

// ── Equalizer bars: the "this row is playing" tell ─────────────────────────

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
                val transition = rememberInfiniteTransition(label = "eq$i")
                val bounce by transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 420 + i * 120, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "bar$i",
                )
                val targetHeight = if (playing) 4.dp + 10.dp * bounce else 3.dp
                val height by animateDpAsState(
                    targetValue = targetHeight,
                    animationSpec = MusicoMotion.snappy(),
                    label = "barH$i",
                )
                Box(
                    Modifier
                        .width(3.dp)
                        .height(height)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (playing) color else Musico.extras.inkFaint)
                        .alpha(if (playing) 1f else 0.4f),
                )
            }
        }
    }
}

// ── Track rows (search results, queue, lists) ──────────────────────────────

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
) {
    val bg by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        animationSpec = MusicoMotion.gentle(),
        label = "rowBg",
    )
    Surface(
        color = bg,
        shape = MusicoShapes.chip,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            index?.let {
                Text(
                    "$it",
                    style = Musico.type.title,
                    fontWeight = FontWeight.ExtraBold,
                    color = Musico.extras.inkFaint,
                    modifier = Modifier.width(34.dp),
                )
            }
            Artwork(
                url = track.thumbnail,
                contentDescription = track.title,
                modifier = Modifier.size(52.dp),
                cornerRadius = 14.dp,
            )
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
                Text(
                    track.artist.ifEmpty { track.album }.ifEmpty { "Unknown artist" },
                    style = Musico.type.caption,
                    color = Musico.extras.inkDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            if (resolving) {
                PulsingDot()
            } else if (isCurrent) {
                EqualizerBars(playing = isPlaying, modifier = Modifier.height(16.dp))
            }
            Spacer(Modifier.width(6.dp))
            LikeButton(liked = isLiked, onToggle = onLikeToggle, size = 22.dp)
        }
    }
}

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
    val scale by animateFloatAsState(
        targetValue = if (liked) 1.08f else 1f,
        animationSpec = MusicoMotion.snappy(),
        label = "likeScale",
    )
    Icon(
        imageVector = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        contentDescription = if (liked) "Unlike" else "Like",
        tint = color,
        modifier = modifier
            .size(size)
            .scale(scale)
            .clickable(onClick = onToggle),
    )
}

/** A tiny breathing dot used as the "resolving…" indicator. */
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
            .background(io.github.aryan007h.musicoonline.ui.theme.MusicoColors.violetBright),
    )
}

// ── Shelf cards (album/playlist/artist covers, song squares) ──────────────

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
            .clickable(onClick = onClick),
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

/** Horizontal shelf of [ShelfCard]s with the section header. */
@Composable
fun ShelfCarousel(
    shelfTitle: String,
    items: List<ShelfItem>,
    onItemClick: (ShelfItem, List<ShelfItem>) -> Unit,
    modifier: Modifier = Modifier,
    cardWidth: Dp = 160.dp,
) {
    Column(modifier = modifier) {
        SectionHeader(shelfTitle)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(items, key = { it.videoId ?: it.browseId ?: it.title }) { item ->
                ShelfCard(
                    item = item,
                    width = cardWidth,
                    onClick = { onItemClick(item, items) },
                )
            }
        }
    }
}

// ── States ────────────────────────────────────────────────────────────────

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    StateShell(modifier) {
        Icon(
            Icons.Filled.GraphicEq,
            contentDescription = null,
            tint = Musico.extras.coral,
            modifier = Modifier.size(40.dp),
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
    StateShell(modifier) {
        Icon(icon, contentDescription = null, tint = Musico.extras.inkFaint, modifier = Modifier.size(40.dp))
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

// ── Play FAB used on grid cards ───────────────────────────────────────────

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
            .scale(scale)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = Color(0xFF05291D),
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
