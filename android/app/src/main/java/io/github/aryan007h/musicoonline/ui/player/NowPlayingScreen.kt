package io.github.aryan007h.musicoonline.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.EnhancedLyrics
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import kotlinx.coroutines.launch

/**
 * The full-screen player. Three swipeable pages — artwork, the word-by-word
 * lyric stage, and the Up Next queue — over a customizable animated
 * backdrop. Controls follow M3 expressive: big squishy play FAB, springy
 * secondary buttons.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    track: OnlineTrack?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    lyrics: EnhancedLyrics?,
    lyricsLoading: Boolean,
    queue: List<OnlineTrack>,
    queueIndex: Int,
    liked: Boolean,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLikeToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    repeatAll: Boolean,
    onPlayFromQueue: (Int) -> Unit,
) {
    val track = track ?: return
    // Frame-interpolated position — scoped to this overlay so the shell
    // behind it doesn't recompose at refresh rate while it's open.
    val smoothPositionMs = rememberSmoothPositionMs(
        polledPositionMs = positionMs,
        isPlaying = isPlaying,
        durationMs = durationMs,
    )

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onCollapse) {
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = "Collapse player",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp),
                )
            }
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "NOW PLAYING",
                    fontSize = 11.sp,
                    letterSpacing = 3.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Text(
                    track.title.ifEmpty { "Untitled" },
                    color = Color.White,
                    style = Musico.type.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.basicMarquee(),
                )
            }
            IconButton(onClick = onLikeToggle) {
                Icon(
                    if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (liked) "Unlike" else "Like",
                    tint = if (liked) Musico.extras.coral else Color.White,
                )
            }
        }

        // ── Pages: artwork / lyrics / up next ────────────────────────────
        val pagerState = rememberPagerState(pageCount = { 3 })
        val scope = rememberCoroutineScope()

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            when (page) {
                0 -> ArtworkPage(track, isBuffering, onTogglePlay)
                1 -> LyricsPage(
                    lyrics = lyrics,
                    loading = lyricsLoading,
                    smoothPositionMs = smoothPositionMs,
                    durationMs = durationMs,
                    onSeek = onSeek,
                )
                2 -> QueuePage(queue, queueIndex, onPlayFromQueue)
            }
        }

        // ── Page dots ────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(3) { i ->
                val active = pagerState.currentPage == i
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (active) 16.dp else 5.dp, height = 5.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                active -> Color.White
                                i == 1 -> Musico.extras.mint.copy(alpha = 0.55f)
                                else -> Color.White.copy(alpha = 0.28f)
                            },
                        ),
                )
            }
        }

        // ── Seek bar ─────────────────────────────────────────────────────
        SeekBar(
            positionMs = smoothPositionMs,
            durationMs = durationMs,
            onSeek = onSeek,
        )

        // ── Transport ────────────────────────────────────────────────────
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onRepeatToggle) {
                Icon(
                    Icons.Filled.Repeat,
                    contentDescription = if (repeatAll) "Repeat on" else "Repeat off",
                    tint = if (repeatAll) Musico.extras.mint else Color.White.copy(alpha = 0.75f),
                )
            }
            TransportButton(onClick = onPrevious, size = 38.dp) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
            BigPlayButton(isPlaying = isPlaying, isBuffering = isBuffering, onClick = onTogglePlay)
            TransportButton(onClick = onNext, size = 38.dp) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp),
                )
            }
            IconButton(onClick = {
                scope.launch { pagerState.animateScrollToPage(1) }
            }) {
                Icon(
                    Icons.Filled.Lyrics,
                    contentDescription = "Show lyrics",
                    tint = Color.White.copy(alpha = 0.75f),
                )
            }
        }
    }
}

// ── Pages ────────────────────────────────────────────────────────────────

@Composable
private fun ArtworkPage(track: OnlineTrack, isBuffering: Boolean, onTogglePlay: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 34.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Artwork(
                    url = track.thumbnail,
                    contentDescription = track.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f),
                    cornerRadius = Musico.extras.cardLargeRadius,
                )
                if (isBuffering) {
                    Box(
                        Modifier.matchParentSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            color = Musico.extras.mint,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                track.title.ifEmpty { "Untitled" },
                color = Color.White,
                style = Musico.type.display.copy(fontSize = 24.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                track.artist.ifEmpty { track.album }.ifEmpty { "Unknown artist" },
                color = Color.White.copy(alpha = 0.65f),
                style = Musico.type.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LyricsPage(
    lyrics: EnhancedLyrics?,
    loading: Boolean,
    smoothPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
) {
    LyricsPane(
        lyrics = lyrics,
        loading = loading,
        positionMs = smoothPositionMs,
        durationMs = durationMs,
        onSeek = onSeek,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun QueuePage(queue: List<OnlineTrack>, queueIndex: Int, onPlayFromQueue: (Int) -> Unit) {
    if (queue.isEmpty()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Up next", style = Musico.type.display, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text(
                "Radio queue is empty — play something!",
                color = Color.White.copy(alpha = 0.6f),
                style = Musico.type.body,
            )
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        Text(
            "UP NEXT",
            style = Musico.type.section,
            color = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 34.dp, vertical = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(queue.size, key = { "q-${queue[it].videoId}-$it" }) { i ->
                val item = queue[i]
                val isCurrent = i == queueIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MusicoShapes.chip)
                        .background(if (isCurrent) Color.White.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { onPlayFromQueue(i) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(
                        url = item.thumbnail,
                        contentDescription = item.title,
                        modifier = Modifier.size(44.dp),
                        cornerRadius = 12.dp,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title.ifEmpty { "Untitled" },
                            color = if (isCurrent) Musico.extras.mint else Color.White,
                            style = Musico.type.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.artist.ifEmpty { "Unknown artist" },
                            color = Color.White.copy(alpha = 0.55f),
                            style = Musico.type.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isCurrent) {
                        Text("PLAYING", fontSize = 9.sp, letterSpacing = 1.5.sp, color = Musico.extras.mint, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ── Controls ─────────────────────────────────────────────────────────────

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableFloatStateOf(Float.NaN) }
    val posFraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = if (dragging.isNaN()) posFraction else dragging

    Column(Modifier.padding(horizontal = 28.dp)) {
        Slider(
            value = shown,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                if (!dragging.isNaN() && durationMs > 0) {
                    onSeek((dragging * durationMs).toLong())
                }
                dragging = Float.NaN
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Musico.extras.mint,
                inactiveTrackColor = Color.White.copy(alpha = 0.22f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(if (dragging.isNaN()) positionMs else (shown * durationMs).toLong()), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
            Text(formatTime(durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
        }
    }
}

/** The big squishy M3-expressive play FAB. */
@Composable
private fun BigPlayButton(isPlaying: Boolean, isBuffering: Boolean, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = MusicoMotion.snappy(),
        label = "playScale",
    )
    Box(
        Modifier
            .size(78.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White)
            .clickable {
                pressed = true
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                color = Color(0xFF3B2A80),
                modifier = Modifier.size(34.dp),
                strokeWidth = 3.dp,
            )
        } else {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = { (fadeIn(tween(120)) togetherWith fadeOut(tween(80))) },
                label = "playIcon",
            ) { playing ->
                Icon(
                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = Color(0xFF12121C),
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
    // Reset the squish on the next frame after press.
    androidx.compose.runtime.LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(120)
            pressed = false
        }
    }
}

@Composable
private fun TransportButton(onClick: () -> Unit, size: androidx.compose.ui.unit.Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size * 1.6f)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private fun formatTime(ms: Long): String {
    val totalSecs = ms / 1000
    val m = totalSecs / 60
    val s = totalSecs % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
