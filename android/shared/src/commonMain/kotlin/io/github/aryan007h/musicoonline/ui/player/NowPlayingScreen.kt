package io.github.aryan007h.musicoonline.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.EnhancedLyrics
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.components.ExpressivePlayPauseButton
import io.github.aryan007h.musicoonline.ui.components.ExpressiveWavySeekBar
import io.github.aryan007h.musicoonline.ui.components.LikeButton
import io.github.aryan007h.musicoonline.ui.components.desktopHorizontalPagerScroll
import io.github.aryan007h.musicoonline.ui.components.musicoTopInset
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.hapticThud
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import io.github.aryan007h.musicoonline.ui.theme.pressable
import kotlinx.coroutines.launch

/**
 * Full-Screen Now Playing Studio:
 * - On compact screens (< 840dp): 3-page HorizontalPager (Living Breathing Artwork,
 *   Full-Screen Lyrics Studio, Up Next Queue) with 1:1 vertical drag-to-collapse.
 * - On Desktop / Tablet (>= 840dp): Apple-Music-style Widescreen Split Stage with
 *   breathing cover art + transport on the left and live Flowing Verse Cinema (or
 *   Up Next Queue toggle) side-by-side on the right!
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
    lyricsMode: LyricsDisplayMode,
    lyricOffsetMs: Long,
    atmosphere: ArtworkAtmosphere,
    queue: List<OnlineTrack>,
    queueIndex: Int,
    liked: Boolean,
    repeatAll: Boolean,
    initialPage: Int = 0,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLikeToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onAddToPlaylist: (OnlineTrack) -> Unit,
    onLyricsModeChange: (LyricsDisplayMode) -> Unit,
    onLyricOffsetChange: (Long) -> Unit,
    onCycleBackdrop: () -> Unit,
    onShareFeedback: (String) -> Unit,
    onPlayFromQueue: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
    onDragProgressChange: (Float) -> Unit = {},
) {
    val activeTrack = track ?: return
    val smoothPositionMs = rememberSmoothPositionMs(
        polledPositionMs = positionMs,
        isPlaying = isPlaying,
        durationMs = durationMs,
    )
    var pureCinema by remember { mutableStateOf(false) }

    // Keep LyricsPane and QueuePage state alive when resizing across 840dp
    // (mirrors MainTabContent in MusicoApp). Compose's movableContentOf only
    // supports up to 4 parameters, so pass compact state holders.
    val sharedLyricsPane = remember {
        movableContentOf<LyricsSlotState> { s ->
            LyricsPane(
                track = s.track,
                lyrics = s.lyrics,
                loading = s.loading,
                positionMs = s.positionMs,
                durationMs = s.durationMs,
                mode = s.mode,
                offsetMs = s.offsetMs,
                atmosphere = s.atmosphere,
                pureCinema = s.pureCinema,
                onTogglePureCinema = { pureCinema = !pureCinema },
                onModeChange = onLyricsModeChange,
                onOffsetChange = onLyricOffsetChange,
                onCycleBackdrop = onCycleBackdrop,
                onSeek = onSeek,
                onShareFeedback = onShareFeedback,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    val sharedQueuePane = remember {
        movableContentOf<QueueSlotState> { s ->
            QueuePage(
                queue = s.queue,
                queueIndex = s.queueIndex,
                glowColor = s.glowColor,
                onPlayFromQueue = onPlayFromQueue,
                onRemoveFromQueue = onRemoveFromQueue,
            )
        }
    }

    val lyricsSlotState = LyricsSlotState(
        track = activeTrack,
        lyrics = lyrics,
        loading = lyricsLoading,
        positionMs = smoothPositionMs,
        durationMs = durationMs,
        mode = lyricsMode,
        offsetMs = lyricOffsetMs,
        atmosphere = atmosphere,
        pureCinema = pureCinema,
    )
    val queueSlotState = QueueSlotState(
        queue = queue,
        queueIndex = queueIndex,
        glowColor = atmosphere.glow,
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            WidescreenSplitNowPlaying(
                activeTrack = activeTrack,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                smoothPositionMs = smoothPositionMs,
                durationMs = durationMs,
                atmosphere = atmosphere,
                queueSize = queue.size,
                liked = liked,
                repeatAll = repeatAll,
                initialShowQueue = initialPage == 2,
                onCollapse = onCollapse,
                onTogglePlay = onTogglePlay,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onLikeToggle = onLikeToggle,
                onRepeatToggle = onRepeatToggle,
                onAddToPlaylist = onAddToPlaylist,
                lyricsContent = { sharedLyricsPane(lyricsSlotState) },
                queueContent = { sharedQueuePane(queueSlotState) },
            )
        } else {
            CompactPagerNowPlaying(
                activeTrack = activeTrack,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                smoothPositionMs = smoothPositionMs,
                durationMs = durationMs,
                atmosphere = atmosphere,
                liked = liked,
                repeatAll = repeatAll,
                initialPage = initialPage,
                pureCinema = pureCinema,
                onPureCinemaChange = { pureCinema = it },
                onCollapse = onCollapse,
                onTogglePlay = onTogglePlay,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onLikeToggle = onLikeToggle,
                onRepeatToggle = onRepeatToggle,
                onAddToPlaylist = onAddToPlaylist,
                onDragProgressChange = onDragProgressChange,
                lyricsContent = { sharedLyricsPane(lyricsSlotState) },
                queueContent = { sharedQueuePane(queueSlotState) },
            )
        }
    }
}

private data class LyricsSlotState(
    val track: OnlineTrack,
    val lyrics: EnhancedLyrics?,
    val loading: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val mode: LyricsDisplayMode,
    val offsetMs: Long,
    val atmosphere: ArtworkAtmosphere,
    val pureCinema: Boolean,
)

private data class QueueSlotState(
    val queue: List<OnlineTrack>,
    val queueIndex: Int,
    val glowColor: Color,
)

// ── Widescreen Split Stage (Desktop / Tablet >= 840dp) ────────────────────

@Composable
private fun WidescreenSplitNowPlaying(
    activeTrack: OnlineTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    smoothPositionMs: Long,
    durationMs: Long,
    atmosphere: ArtworkAtmosphere,
    queueSize: Int,
    liked: Boolean,
    repeatAll: Boolean,
    initialShowQueue: Boolean,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLikeToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onAddToPlaylist: (OnlineTrack) -> Unit,
    lyricsContent: @Composable () -> Unit,
    queueContent: @Composable () -> Unit,
) {
    var rightTabQueue by remember { mutableStateOf(initialShowQueue) }

    Column(
        Modifier
            .fillMaxSize()
            .musicoTopInset()
            .navigationBarsPadding()
            .padding(horizontal = 36.dp, vertical = 20.dp),
    ) {
        // Minimal studio top bar
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Surface(
                shape = MusicoShapes.chip,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.pressable(onClick = onCollapse, pressedScale = 0.94f),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.ExpandMore, contentDescription = "Collapse", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp))
                    Text("Back", color = MaterialTheme.colorScheme.onSurface, style = Musico.type.caption, fontWeight = FontWeight.SemiBold)
                }
            }

            // Right-pane switcher: Lyrics Studio ⇄ Up Next (native M3E tonal segmented container)
            Surface(
                shape = MusicoShapes.chip,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SplitStageTab(
                        icon = Icons.Filled.Lyrics,
                        label = "Lyrics Studio",
                        selected = !rightTabQueue,
                        accent = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { rightTabQueue = false },
                    )
                    SplitStageTab(
                        icon = Icons.Filled.QueueMusic,
                        label = "Up Next · $queueSize",
                        selected = rightTabQueue,
                        accent = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = { rightTabQueue = true },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Stage: Living Breathing Artwork + Track Meta + Seek + Transport
            Column(
                modifier = Modifier
                    .weight(0.46f)
                    .widthIn(max = 460.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                LivingBreathingArtwork(
                    track = activeTrack,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    atmosphere = atmosphere,
                    onTogglePlay = onTogglePlay,
                    modifier = Modifier
                        .fillMaxWidth(0.84f)
                        .aspectRatio(1f),
                )
                Spacer(Modifier.height(26.dp))
                Row(
                    Modifier.fillMaxWidth(0.92f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            activeTrack.title.ifEmpty { "Untitled" },
                            color = Color.White,
                            style = Musico.type.display.copy(fontSize = 24.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                        Text(
                            activeTrack.artist.ifEmpty { activeTrack.album }.ifEmpty { "Unknown artist" },
                            color = Color.White.copy(alpha = 0.70f),
                            style = Musico.type.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { hapticTick(); onAddToPlaylist(activeTrack) }) {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to playlist", tint = Color.White.copy(alpha = 0.85f))
                    }
                    Spacer(Modifier.width(4.dp))
                    LikeButton(liked = liked, onToggle = onLikeToggle, size = 24.dp)
                }
                Spacer(Modifier.height(14.dp))
                SeekBar(
                    positionMs = smoothPositionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onSeek = onSeek,
                    horizontalPadding = 16.dp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth(0.92f)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { hapticTick(); onRepeatToggle() }) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = "Repeat",
                            tint = if (repeatAll) atmosphere.glow else Color.White.copy(alpha = 0.65f),
                        )
                    }
                    TransportButton(onClick = onPrevious, size = 34.dp) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    BigPlayButton(isPlaying = isPlaying, isBuffering = isBuffering, onClick = onTogglePlay, size = 68.dp)
                    TransportButton(onClick = onNext, size = 34.dp) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    IconButton(onClick = { rightTabQueue = !rightTabQueue }) {
                        Icon(
                            if (rightTabQueue) Icons.Filled.Lyrics else Icons.Filled.QueueMusic,
                            contentDescription = "Toggle queue",
                            tint = atmosphere.glow,
                        )
                    }
                }
            }

            // Right Stage: Live Flowing Verse Cinema or Interactive Up Next Queue
            Box(
                Modifier
                    .weight(0.54f)
                    .fillMaxHeight(),
            ) {
                AnimatedContent(
                    targetState = rightTabQueue,
                    transitionSpec = { fadeIn(tween(MusicoMotion.MED)) togetherWith fadeOut(tween(MusicoMotion.FAST)) },
                    label = "splitRightStage",
                ) { showQueue ->
                    if (showQueue) {
                        queueContent()
                    } else {
                        lyricsContent()
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitStageTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(MusicoShapes.chip)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .pressable(onClick = onClick, pressedScale = 0.94f)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Text(
            label,
            style = Musico.type.tabularCaption,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

// ── Compact Phone Pager (< 840dp) ─────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactPagerNowPlaying(
    activeTrack: OnlineTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    smoothPositionMs: Long,
    durationMs: Long,
    atmosphere: ArtworkAtmosphere,
    liked: Boolean,
    repeatAll: Boolean,
    initialPage: Int,
    pureCinema: Boolean,
    onPureCinemaChange: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLikeToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onAddToPlaylist: (OnlineTrack) -> Unit,
    onDragProgressChange: (Float) -> Unit,
    lyricsContent: @Composable () -> Unit,
    queueContent: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, 2), pageCount = { 3 })

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != 1 && pureCinema) {
            onPureCinemaChange(false)
        }
    }

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val collapseThresholdPx = with(density) { 150.dp.toPx() }
    val animDragOffset by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = MusicoMotion.spatial(),
        label = "npDragOffset",
    )

    Column(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = animDragOffset.coerceAtLeast(0f) }
            .musicoTopInset()
            .navigationBarsPadding(),
    ) {
        AnimatedVisibility(
            visible = !pureCinema,
            enter = fadeIn(tween(MusicoMotion.MED)),
            exit = fadeOut(tween(MusicoMotion.FAST)),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                val next = (dragOffsetPx + dragAmount).coerceAtLeast(0f)
                                dragOffsetPx = next
                                onDragProgressChange((next / (collapseThresholdPx * 2f)).coerceIn(0f, 1f))
                            },
                            onDragEnd = {
                                if (dragOffsetPx >= collapseThresholdPx) {
                                    dragOffsetPx = 0f
                                    onDragProgressChange(0f)
                                    onCollapse()
                                } else {
                                    dragOffsetPx = 0f
                                    onDragProgressChange(0f)
                                }
                            },
                            onDragCancel = {
                                dragOffsetPx = 0f
                                onDragProgressChange(0f)
                            },
                        )
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .width(38.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.28f)),
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Filled.ExpandMore, contentDescription = "Collapse player", tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            when (pagerState.currentPage) {
                                1 -> "LYRICS"
                                2 -> "UP NEXT"
                                else -> "NOW PLAYING"
                            },
                            fontSize = 10.sp,
                            letterSpacing = 3.sp,
                            fontWeight = FontWeight.Bold,
                            color = atmosphere.glow.copy(alpha = 0.85f),
                        )
                        Text(
                            activeTrack.title.ifEmpty { "Untitled" },
                            color = Color.White,
                            style = Musico.type.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                    }
                    IconButton(onClick = { hapticTick(); onAddToPlaylist(activeTrack) }) {
                        Icon(Icons.Filled.PlaylistAdd, contentDescription = "Add to playlist", tint = Color.White.copy(alpha = 0.88f))
                    }
                    Box(Modifier.padding(end = 8.dp)) {
                        LikeButton(liked = liked, onToggle = onLikeToggle, size = 24.dp)
                    }
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .desktopHorizontalPagerScroll(pagerState),
        ) { page ->
            when (page) {
                0 -> ArtworkPage(activeTrack, isPlaying, isBuffering, atmosphere, onTogglePlay)
                1 -> lyricsContent()
                2 -> queueContent()
            }
        }

        AnimatedVisibility(
            visible = !pureCinema,
            enter = fadeIn(tween(MusicoMotion.MED)),
            exit = fadeOut(tween(MusicoMotion.FAST)),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) { i ->
                        val active = pagerState.currentPage == i
                        val dotWidth by animateDpAsState(
                            targetValue = if (active) 18.dp else 6.dp,
                            animationSpec = MusicoMotion.expressive(),
                            label = "npDotW$i",
                        )
                        val dotColor by animateColorAsState(
                            targetValue = when {
                                active -> Color.White
                                i == 1 -> atmosphere.glow.copy(alpha = 0.65f)
                                else -> Color.White.copy(alpha = 0.28f)
                            },
                            animationSpec = MusicoMotion.gentle(),
                            label = "npDotC$i",
                        )
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable {
                                    hapticTick()
                                    scope.launch { pagerState.animateScrollToPage(i) }
                                }
                                .padding(horizontal = 5.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(width = dotWidth.coerceAtLeast(0.dp), height = 5.dp)
                                    .clip(CircleShape)
                                    .background(dotColor),
                            )
                        }
                    }
                }

                SeekBar(
                    positionMs = smoothPositionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    activeColor = MaterialTheme.colorScheme.primary,
                    onSeek = onSeek,
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val repeatScale by animateFloatAsState(
                        targetValue = if (repeatAll) 1.12f else 1f,
                        animationSpec = MusicoMotion.snappy(),
                        label = "npRepeatScale",
                    )
                    IconButton(onClick = { hapticTick(); onRepeatToggle() }) {
                        Icon(
                            Icons.Filled.Repeat,
                            contentDescription = if (repeatAll) "Repeat on" else "Repeat off",
                            tint = if (repeatAll) atmosphere.glow else Color.White.copy(alpha = 0.72f),
                            modifier = Modifier.scale(repeatScale.coerceAtLeast(0.01f)),
                        )
                    }
                    TransportButton(onClick = onPrevious, size = 38.dp) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    BigPlayButton(isPlaying = isPlaying, isBuffering = isBuffering, onClick = onTogglePlay)
                    TransportButton(onClick = onNext, size = 38.dp) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                    val onLyricsPage = pagerState.currentPage == 1
                    IconButton(onClick = {
                        hapticTick()
                        scope.launch { pagerState.animateScrollToPage(if (onLyricsPage) 0 else 1) }
                    }) {
                        Icon(
                            Icons.Filled.Lyrics,
                            contentDescription = if (onLyricsPage) "Show artwork" else "Show lyrics",
                            tint = if (onLyricsPage) atmosphere.glow else Color.White.copy(alpha = 0.78f),
                        )
                    }
                }
            }
        }
    }
}

// ── Living Breathing Artwork (Apple Music spring scale + cover glow halo) ──

@Composable
private fun LivingBreathingArtwork(
    track: OnlineTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    atmosphere: ArtworkAtmosphere,
    onTogglePlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val breathScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.92f,
        animationSpec = MusicoMotion.expressive(),
        label = "livingCoverScale",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        // Pure Pixel Material 3 Expressive artwork frame — crisp tonal container,
        // zero radial glow aura or blurred halo.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val safeBreath = breathScale.coerceAtLeast(0.01f)
                    scaleX = safeBreath
                    scaleY = safeBreath
                }
                .clip(MusicoShapes.cardLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            AnimatedContent(
                targetState = track,
                transitionSpec = {
                    (fadeIn(tween(MusicoMotion.SLOW, easing = MusicoMotion.decelerate)) +
                        scaleIn(initialScale = 0.92f, animationSpec = MusicoMotion.spatial())) togetherWith
                        (fadeOut(tween(MusicoMotion.MED)) +
                            scaleOut(targetScale = 1.05f, animationSpec = tween(MusicoMotion.MED, easing = MusicoMotion.accelerate)))
                },
                label = "artworkMorph",
            ) { current ->
                Artwork(
                    url = current.thumbnail,
                    contentDescription = current.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onTogglePlay,
                        ),
                    cornerRadius = Musico.extras.cardLargeRadius,
                )
            }
            if (isBuffering) {
                Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Musico.extras.mint, modifier = Modifier.size(56.dp))
                }
            }
        }
    }
}

@Composable
private fun ArtworkPage(
    track: OnlineTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    atmosphere: ArtworkAtmosphere,
    onTogglePlay: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            LivingBreathingArtwork(
                track = track,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                atmosphere = atmosphere,
                onTogglePlay = onTogglePlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Spacer(Modifier.height(22.dp))
            AnimatedContent(
                targetState = track,
                transitionSpec = {
                    (fadeIn(tween(MusicoMotion.MED, easing = MusicoMotion.decelerate)) +
                        slideInVertically(MusicoMotion.spatial()) { it / 3 }) togetherWith fadeOut(tween(MusicoMotion.FAST))
                },
                label = "npTitleMorph",
            ) { current ->
                Text(
                    current.title.ifEmpty { "Untitled" },
                    color = Color.White,
                    style = Musico.type.display.copy(fontSize = 24.sp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                track.artist.ifEmpty { track.album }.ifEmpty { "Unknown artist" },
                color = Color.White.copy(alpha = 0.68f),
                style = Musico.type.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun QueuePage(
    queue: List<OnlineTrack>,
    queueIndex: Int,
    glowColor: Color,
    onPlayFromQueue: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
) {
    if (queue.isEmpty()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Up next", style = Musico.type.display, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("Queue is empty — play something!", color = Color.White.copy(alpha = 0.6f), style = Musico.type.body)
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        Text(
            "UP NEXT · ${queue.size} TRACKS",
            style = Musico.type.tabularCaption,
            letterSpacing = 2.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.62f),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(queue.size, key = { "q-${queue[it].videoId}-$it" }) { i ->
                val item = queue[i]
                val isCurrent = i == queueIndex
                val rowBg by animateColorAsState(
                    targetValue = if (isCurrent) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                    animationSpec = MusicoMotion.gentle(),
                    label = "queueBg$i",
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(MusicoShapes.chip)
                        .background(rowBg)
                        .pressable(onClick = { hapticTick(); onPlayFromQueue(i) }, pressedScale = 0.98f)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Artwork(url = item.thumbnail, contentDescription = item.title, modifier = Modifier.size(44.dp), cornerRadius = 12.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title.ifEmpty { "Untitled" },
                            color = if (isCurrent) glowColor else Color.White,
                            style = Musico.type.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.artist.ifEmpty { "Unknown artist" },
                            color = Color.White.copy(alpha = 0.58f),
                            style = Musico.type.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isCurrent) {
                        Text(
                            "PLAYING",
                            fontSize = 9.sp,
                            letterSpacing = 1.5.sp,
                            color = glowColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    } else {
                        IconButton(
                            onClick = { hapticTick(); onRemoveFromQueue(i) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove from queue", tint = Color.White.copy(alpha = 0.48f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    activeColor: Color,
    onSeek: (Long) -> Unit,
    horizontalPadding: Dp = 28.dp,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val posFraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shown = dragFraction ?: posFraction

    Column(Modifier.padding(horizontal = horizontalPadding)) {
        ExpressiveWavySeekBar(
            progress = posFraction,
            isPlaying = isPlaying,
            enabled = durationMs > 0L,
            activeColor = activeColor,
            inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            thumbColor = activeColor,
            onDragProgress = { dragFraction = it },
            onSeek = { frac ->
                if (durationMs > 0L) {
                    onSeek((frac * durationMs).toLong())
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatTime(if (dragFraction == null) positionMs else (shown * durationMs).toLong()),
                style = Musico.type.tabularCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
            Text(
                formatTime(durationMs),
                style = Musico.type.tabularCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun BigPlayButton(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onClick: () -> Unit,
    size: Dp = 76.dp,
) {
    ExpressivePlayPauseButton(
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        onClick = onClick,
        size = size,
        containerColorPaused = MaterialTheme.colorScheme.primaryContainer,
        containerColorPlaying = MaterialTheme.colorScheme.primary,
        contentColorPaused = MaterialTheme.colorScheme.onPrimaryContainer,
        contentColorPlaying = MaterialTheme.colorScheme.onPrimary,
    )
}

@Composable
private fun TransportButton(onClick: () -> Unit, size: Dp, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size * 1.6f)
            .clip(CircleShape)
            .pressable(onClick = onClick, pressedScale = 0.84f)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

fun formatTime(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val m = totalSecs / 60
    val s = totalSecs % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
