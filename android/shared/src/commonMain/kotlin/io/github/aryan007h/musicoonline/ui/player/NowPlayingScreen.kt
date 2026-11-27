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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
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
import io.github.aryan007h.musicoonline.ui.components.ConnectedButtonOption
import io.github.aryan007h.musicoonline.ui.components.ExpressiveActionPill
import io.github.aryan007h.musicoonline.ui.components.ExpressiveConnectedButtonGroup
import io.github.aryan007h.musicoonline.ui.components.ExpressiveLiveWavySeekBar
import io.github.aryan007h.musicoonline.ui.components.ExpressivePlayPauseButton
import io.github.aryan007h.musicoonline.ui.components.ExpressiveTransportButton
import io.github.aryan007h.musicoonline.ui.components.desktopHorizontalPagerScroll
import io.github.aryan007h.musicoonline.ui.components.musicoTopInset
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import io.github.aryan007h.musicoonline.ui.theme.pressable
import io.github.aryan007h.musicoonline.ui.theme.safeCornerDp
import io.github.aryan007h.musicoonline.ui.theme.safePositiveScale
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Full-Screen Material 3 Expressive Now Playing Studio (Light & Dark adaptive):
 * - Follows Google's Material 3 Expressive Media Player blueprint:
 *   inline-timestamped wavy seekbar, chunky tonal squircle Prev/Next buttons flanking
 *   a wider morphing primary Play/Pause squircle/pill, elevated secondary pill action
 *   row, and tactile bottom contextual chips (`Style`, `Lyrics`, `Queue`).
 * - Compact (< 840dp): 3-page HorizontalPager (Player, Lyrics Studio, Up Next) with
 *   1:1 vertical drag-to-collapse.
 * - Widescreen (>= 840dp): Split Stage with expressive player on the left and live
 *   Lyrics Studio / Up Next Queue switched via an M3E Connected Button Group on the right.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NowPlayingScreen(
    track: OnlineTrack?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMsFlow: StateFlow<Long>,
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
    var pureCinema by remember { mutableStateOf(false) }

    val sharedLyricsPane = remember {
        movableContentOf<LyricsSlotState> { s ->
            LiveLyricsPane(
                track = s.track,
                lyrics = s.lyrics,
                loading = s.loading,
                positionMsFlow = s.positionMsFlow,
                isPlaying = s.isPlaying,
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
                onPlayFromQueue = onPlayFromQueue,
                onRemoveFromQueue = onRemoveFromQueue,
            )
        }
    }

    val lyricsSlotState = LyricsSlotState(
        track = activeTrack,
        lyrics = lyrics,
        loading = lyricsLoading,
        positionMsFlow = positionMsFlow,
        isPlaying = isPlaying,
        durationMs = durationMs,
        mode = lyricsMode,
        offsetMs = lyricOffsetMs,
        atmosphere = atmosphere,
        pureCinema = pureCinema,
    )
    val queueSlotState = QueueSlotState(
        queue = queue,
        queueIndex = queueIndex,
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWidescreen = maxWidth >= 840.dp && maxHeight >= 520.dp
        val isShortWindow = maxHeight < 640.dp
        if (isWidescreen) {
            WidescreenSplitNowPlaying(
                activeTrack = activeTrack,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                positionMsFlow = positionMsFlow,
                durationMs = durationMs,
                atmosphere = atmosphere,
                queueSize = queue.size,
                liked = liked,
                repeatAll = repeatAll,
                initialShowQueue = initialPage == 2,
                isShortWindow = isShortWindow,
                onCollapse = onCollapse,
                onTogglePlay = onTogglePlay,
                onNext = onNext,
                onPrevious = onPrevious,
                onSeek = onSeek,
                onLikeToggle = onLikeToggle,
                onRepeatToggle = onRepeatToggle,
                onAddToPlaylist = onAddToPlaylist,
                onCycleBackdrop = onCycleBackdrop,
                lyricsContent = { sharedLyricsPane(lyricsSlotState) },
                queueContent = { sharedQueuePane(queueSlotState) },
            )
        } else {
            CompactPagerNowPlaying(
                activeTrack = activeTrack,
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                positionMsFlow = positionMsFlow,
                durationMs = durationMs,
                atmosphere = atmosphere,
                queueSize = queue.size,
                liked = liked,
                repeatAll = repeatAll,
                initialPage = initialPage,
                isShortWindow = isShortWindow,
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
                onCycleBackdrop = onCycleBackdrop,
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
    val positionMsFlow: StateFlow<Long>,
    val isPlaying: Boolean,
    val durationMs: Long,
    val mode: LyricsDisplayMode,
    val offsetMs: Long,
    val atmosphere: ArtworkAtmosphere,
    val pureCinema: Boolean,
)

private data class QueueSlotState(
    val queue: List<OnlineTrack>,
    val queueIndex: Int,
)

// ── Widescreen Split Stage (Desktop / Tablet >= 840dp) ────────────────────

@Composable
private fun WidescreenSplitNowPlaying(
    activeTrack: OnlineTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMsFlow: StateFlow<Long>,
    durationMs: Long,
    atmosphere: ArtworkAtmosphere,
    queueSize: Int,
    liked: Boolean,
    repeatAll: Boolean,
    initialShowQueue: Boolean,
    isShortWindow: Boolean,
    onCollapse: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onLikeToggle: () -> Unit,
    onRepeatToggle: () -> Unit,
    onAddToPlaylist: (OnlineTrack) -> Unit,
    onCycleBackdrop: () -> Unit,
    lyricsContent: @Composable () -> Unit,
    queueContent: @Composable () -> Unit,
) {
    var rightTabQueue by remember { mutableStateOf(initialShowQueue) }

    Column(
        Modifier
            .fillMaxSize()
            .musicoTopInset()
            .navigationBarsPadding()
            .padding(horizontal = 36.dp, vertical = if (isShortWindow) 12.dp else 20.dp),
    ) {
        // Minimal M3 Expressive top bar
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ContextualBottomChip(
                icon = Icons.Filled.ExpandMore,
                label = "Back",
                active = false,
                onClick = onCollapse,
            )

            // M3E Connected Button Group switching Lyrics Studio <-> Up Next
            ExpressiveConnectedButtonGroup(
                options = listOf(
                    ConnectedButtonOption(false, "Lyrics Studio", Icons.Filled.Lyrics),
                    ConnectedButtonOption(true, "Up Next · $queueSize", Icons.Filled.QueueMusic),
                ),
                selected = rightTabQueue,
                onSelect = { rightTabQueue = it },
                height = 42.dp,
                emphasized = false,
                unselectedColor = if (Musico.extras.isDark) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                },
                modifier = Modifier.width(308.dp),
            )
        }

        Spacer(Modifier.height(if (isShortWindow) 8.dp else 14.dp))

        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Stage: Height-aware Expressive Artwork + Title + Inline Wavy Seek + M3E Transport Cluster + Pill Row
            BoxWithConstraints(
                modifier = Modifier
                    .weight(0.46f)
                    .widthIn(max = 440.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                val deckBudget = if (isShortWindow) 270.dp else 326.dp
                val artSide = minOf(maxWidth * 0.78f, (maxHeight - deckBudget).coerceAtLeast(124.dp), 330.dp)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    LivingBreathingArtwork(
                        track = activeTrack,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        atmosphere = atmosphere,
                        onTogglePlay = onTogglePlay,
                        modifier = Modifier.size(artSide),
                    )
                    Spacer(Modifier.height(if (isShortWindow) 12.dp else 20.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.96f)
                            .padding(horizontal = 4.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            activeTrack.title.ifEmpty { "Untitled" },
                            color = MaterialTheme.colorScheme.onSurface,
                            style = Musico.type.headline.copy(fontSize = if (isShortWindow) 20.sp else 24.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            activeTrack.artist.ifEmpty { activeTrack.album }.ifEmpty { "Unknown artist" },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = Musico.type.body,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(if (isShortWindow) 10.dp else 16.dp))
                    ExpressiveInlineSeekBar(
                        positionMsFlow = positionMsFlow,
                        durationMs = durationMs,
                        isPlaying = isPlaying,
                        onSeek = onSeek,
                        compact = isShortWindow,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(if (isShortWindow) 10.dp else 16.dp))
                    ExpressiveTransportCluster(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onPrevious = onPrevious,
                        onTogglePlay = onTogglePlay,
                        onNext = onNext,
                        compact = isShortWindow,
                    )
                    Spacer(Modifier.height(if (isShortWindow) 10.dp else 14.dp))
                    ExpressiveSecondaryActionRow(
                        liked = liked,
                        repeatAll = repeatAll,
                        onRepeatToggle = onRepeatToggle,
                        onLikeToggle = onLikeToggle,
                        onAddToPlaylist = { onAddToPlaylist(activeTrack) },
                        compact = isShortWindow,
                        modifier = Modifier.fillMaxWidth(0.90f),
                    )
                    Spacer(Modifier.height(if (isShortWindow) 10.dp else 14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(0.90f),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ContextualBottomChip(
                            icon = Icons.Filled.AutoAwesome,
                            label = "Backdrop",
                            active = false,
                            onClick = onCycleBackdrop,
                        )
                        ContextualBottomChip(
                            icon = if (rightTabQueue) Icons.Filled.Lyrics else Icons.Filled.QueueMusic,
                            label = if (rightTabQueue) "Lyrics" else "Queue",
                            active = true,
                            onClick = { rightTabQueue = !rightTabQueue },
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

// ── Compact Phone Pager (< 840dp) ─────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactPagerNowPlaying(
    activeTrack: OnlineTrack,
    isPlaying: Boolean,
    isBuffering: Boolean,
    positionMsFlow: StateFlow<Long>,
    durationMs: Long,
    atmosphere: ArtworkAtmosphere,
    queueSize: Int,
    liked: Boolean,
    repeatAll: Boolean,
    initialPage: Int,
    isShortWindow: Boolean,
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
    onCycleBackdrop: () -> Unit,
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
        animationSpec = MusicoMotion.spatialDefault(),
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
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)),
                    )
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (Musico.extras.isDark) {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        },
                        border = if (Musico.extras.isDark) {
                            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
                        } else {
                            null
                        },
                        modifier = Modifier
                            .size(if (isShortWindow) 36.dp else 40.dp)
                            .pressable(onClick = onCollapse, pressedScale = 0.90f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.ExpandMore,
                                contentDescription = "Collapse player",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(if (isShortWindow) 22.dp else 24.dp),
                            )
                        }
                    }

                    Column(
                        Modifier
                            .weight(1f)
                            .padding(horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            when (pagerState.currentPage) {
                                1 -> "LYRICS STUDIO"
                                2 -> "UP NEXT · $queueSize"
                                else -> "NOW PLAYING"
                            },
                            style = Musico.type.overline,
                            letterSpacing = 1.8.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (pagerState.currentPage != 0) {
                            Text(
                                activeTrack.title.ifEmpty { "Untitled" },
                                color = MaterialTheme.colorScheme.onSurface,
                                style = Musico.type.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(),
                            )
                        }
                    }

                    // Page dots inline on the right for clean balance
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(3) { i ->
                            val active = pagerState.currentPage == i
                            val dotW by animateDpAsState(
                                targetValue = if (active) 16.dp else 6.dp,
                                animationSpec = MusicoMotion.spatialFast(),
                                label = "npTopDot$i",
                            )
                            val dotC by animateColorAsState(
                                targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                animationSpec = MusicoMotion.effectsFast(),
                                label = "npTopDotC$i",
                            )
                            Box(
                                Modifier
                                    .size(width = safeCornerDp(dotW), height = 6.dp)
                                    .clip(CircleShape)
                                    .background(dotC)
                                    .clickable {
                                        hapticTick()
                                        scope.launch { pagerState.animateScrollToPage(i) }
                                    },
                            )
                        }
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
                0 -> ArtworkPage(
                    track = activeTrack,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    atmosphere = atmosphere,
                    isShortWindow = isShortWindow,
                    onTogglePlay = onTogglePlay,
                )
                1 -> lyricsContent()
                2 -> queueContent()
            }
        }

        AnimatedVisibility(
            visible = !pureCinema,
            enter = fadeIn(tween(MusicoMotion.MED)),
            exit = fadeOut(tween(MusicoMotion.FAST)),
        ) {
            val deckSpacer = if (isShortWindow) 8.dp else 14.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (isShortWindow) 18.dp else 22.dp, vertical = if (isShortWindow) 6.dp else 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 1. Inline-timestamped M3 Expressive Wavy Seekbar (120 FPS draw-phase)
                ExpressiveInlineSeekBar(
                    positionMsFlow = positionMsFlow,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    onSeek = onSeek,
                    compact = isShortWindow,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(deckSpacer))

                // 2. M3 Expressive Mixed-Geometry Transport Cluster (Squircle Prev · Wide Morphing Play/Pause · Squircle Next)
                ExpressiveTransportCluster(
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    onPrevious = onPrevious,
                    onTogglePlay = onTogglePlay,
                    onNext = onNext,
                    compact = isShortWindow,
                )

                Spacer(Modifier.height(deckSpacer))

                // 3. Elevated Secondary Pill Action Row (Repeat circle · Like stadium pill · Playlist stadium pill)
                ExpressiveSecondaryActionRow(
                    liked = liked,
                    repeatAll = repeatAll,
                    onRepeatToggle = onRepeatToggle,
                    onLikeToggle = onLikeToggle,
                    onAddToPlaylist = { onAddToPlaylist(activeTrack) },
                    compact = isShortWindow,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(deckSpacer))

                // 4. Bottom Contextual Tonal Chips (Backdrop · Lyrics · Queue)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContextualBottomChip(
                        icon = Icons.Filled.AutoAwesome,
                        label = "Backdrop",
                        active = false,
                        onClick = onCycleBackdrop,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ContextualBottomChip(
                            icon = Icons.Filled.Lyrics,
                            label = "Lyrics",
                            active = pagerState.currentPage == 1,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(if (pagerState.currentPage == 1) 0 else 1)
                                }
                            },
                        )
                        ContextualBottomChip(
                            icon = Icons.Filled.QueueMusic,
                            label = "Queue",
                            active = pagerState.currentPage == 2,
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(if (pagerState.currentPage == 2) 0 else 2)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

// ── M3 Expressive Shared Player Sub-Components ────────────────────────────

@Composable
private fun ExpressiveTransportCluster(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPrevious: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    compact: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExpressiveTransportButton(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = "Previous",
            onClick = onPrevious,
            width = if (compact) 68.dp else 80.dp,
            height = if (compact) 56.dp else 70.dp,
            iconSize = if (compact) 26.dp else 32.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        ExpressivePlayPauseButton(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            onClick = onTogglePlay,
            width = if (compact) 88.dp else 104.dp,
            height = if (compact) 60.dp else 74.dp,
            containerColorPaused = MaterialTheme.colorScheme.primary,
            containerColorPlaying = MaterialTheme.colorScheme.primary,
            contentColorPaused = MaterialTheme.colorScheme.onPrimary,
            contentColorPlaying = MaterialTheme.colorScheme.onPrimary,
        )
        ExpressiveTransportButton(
            icon = Icons.Filled.SkipNext,
            contentDescription = "Next",
            onClick = onNext,
            width = if (compact) 68.dp else 80.dp,
            height = if (compact) 56.dp else 70.dp,
            iconSize = if (compact) 26.dp else 32.dp,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun ExpressiveSecondaryActionRow(
    liked: Boolean,
    repeatAll: Boolean,
    onRepeatToggle: () -> Unit,
    onLikeToggle: () -> Unit,
    onAddToPlaylist: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val pillHeight = if (compact) 42.dp else 48.dp
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExpressiveActionPill(
            icon = Icons.Filled.Repeat,
            contentDescription = if (repeatAll) "Repeat on" else "Repeat off",
            onClick = onRepeatToggle,
            active = repeatAll,
            height = pillHeight,
            modifier = Modifier.width(if (compact) 44.dp else 50.dp),
        )
        ExpressiveActionPill(
            icon = if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
            contentDescription = if (liked) "Remove from liked" else "Like song",
            label = if (liked) "Liked" else "Like",
            onClick = onLikeToggle,
            active = liked,
            height = pillHeight,
            containerColor = if (liked) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else if (Musico.extras.isDark) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
            contentColor = if (liked) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.weight(1f),
        )
        ExpressiveActionPill(
            icon = Icons.Filled.PlaylistAdd,
            contentDescription = "Add to playlist",
            label = "Save",
            onClick = onAddToPlaylist,
            active = false,
            height = pillHeight,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ContextualBottomChip(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val isDark = Musico.extras.isDark
    val bg by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = MusicoMotion.effectsFast(),
        label = "ctxChipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MusicoMotion.effectsFast(),
        label = "ctxChipFg",
    )
    val corner by animateDpAsState(
        targetValue = if (active) 20.dp else 14.dp,
        animationSpec = MusicoMotion.spatialFast(),
        label = "ctxChipCorner",
    )
    Surface(
        shape = RoundedCornerShape(safeCornerDp(corner).coerceAtLeast(8.dp)),
        color = bg,
        border = if (isDark) {
            BorderStroke(
                1.dp,
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f),
            )
        } else {
            null
        },
        modifier = Modifier
            .pointerHoverIcon(PointerIcon.Hand)
            .pressable(onClick = { hapticTick(); onClick() }, pressedScale = 0.94f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                color = fg,
            )
        }
    }
}

/**
 * Inline-timestamped Material 3 Expressive wavy seekbar matching Google's M3E
 * media control layout (`1:12  ~~~~|--------  3:45`), rendered at native 120 FPS
 * strictly in the Canvas draw phase with zero parent recompositions.
 */
@Composable
private fun ExpressiveInlineSeekBar(
    positionMsFlow: StateFlow<Long>,
    durationMs: Long,
    isPlaying: Boolean,
    onSeek: (Long) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    ExpressiveLiveWavySeekBar(
        positionMsFlow = positionMsFlow,
        durationMs = durationMs,
        isPlaying = isPlaying,
        enabled = durationMs > 0L,
        onSeekMs = onSeek,
        showInlineTimestamps = true,
        timestampFontSize = if (compact) 11.5.sp else 12.5.sp,
        timestampMinWidth = if (compact) 36.dp else 40.dp,
        activeColor = MaterialTheme.colorScheme.primary,
        inactiveColor = MaterialTheme.colorScheme.outlineVariant,
        thumbColor = MaterialTheme.colorScheme.primary,
        trackHeight = if (compact) 5.dp else 6.dp,
        waveAmplitude = if (compact) 3.6.dp else 4.4.dp,
        waveLength = 26.dp,
        thumbHeight = if (compact) 24.dp else 28.dp,
        barHeight = if (compact) 32.dp else 38.dp,
        modifier = modifier,
    )
}

// ── Living Breathing Artwork ──────────────────────────────────────────────

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
        targetValue = if (isPlaying) 1.0f else 0.93f,
        animationSpec = MusicoMotion.spatialDefault(),
        label = "livingCoverScale",
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Surface(
            shape = MusicoShapes.cardLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = if (Musico.extras.isDark) 10.dp else 8.dp,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val safeBreath = safePositiveScale(breathScale)
                    scaleX = safeBreath
                    scaleY = safeBreath
                },
        ) {
            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        (fadeIn(tween(MusicoMotion.SLOW, easing = MusicoMotion.decelerate)) +
                            scaleIn(initialScale = 0.92f, animationSpec = MusicoMotion.spatialDefault())) togetherWith
                            (fadeOut(tween(MusicoMotion.MED)) +
                                scaleOut(targetScale = 1.04f, animationSpec = tween(MusicoMotion.MED, easing = MusicoMotion.accelerate)))
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
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                            modifier = Modifier.size(64.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(34.dp),
                                    strokeWidth = 3.dp,
                                )
                            }
                        }
                    }
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
    isShortWindow: Boolean,
    onTogglePlay: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = if (isShortWindow) 20.dp else 28.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        // When desktop window is shrunk small or height is tight, switch to a bespoke
        // horizontal Mini-Studio row so artwork, title, and artist stay crisp and never clip.
        val useMiniStudioRow = isShortWindow || maxHeight < 290.dp
        if (useMiniStudioRow) {
            val artSize = minOf(
                (maxHeight - 14.dp).coerceAtLeast(84.dp),
                maxWidth * 0.42f,
                168.dp,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LivingBreathingArtwork(
                    track = track,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    atmosphere = atmosphere,
                    onTogglePlay = onTogglePlay,
                    modifier = Modifier.size(artSize),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center,
                ) {
                    AnimatedContent(
                        targetState = track,
                        transitionSpec = {
                            (fadeIn(tween(MusicoMotion.MED, easing = MusicoMotion.decelerate)) +
                                slideInVertically(MusicoMotion.spatialDefault()) { it / 3 }) togetherWith fadeOut(tween(MusicoMotion.FAST))
                        },
                        label = "npMiniTitleMorph",
                    ) { current ->
                        Text(
                            current.title.ifEmpty { "Untitled" },
                            color = MaterialTheme.colorScheme.onSurface,
                            style = Musico.type.headline.copy(fontSize = 20.sp),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        track.artist.ifEmpty { track.album }.ifEmpty { "Unknown artist" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = Musico.type.body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (track.album.isNotBlank() && !track.album.equals(track.artist, ignoreCase = true)) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            track.album,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                            style = Musico.type.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            val artSize = minOf(
                maxWidth * 0.90f,
                (maxHeight - 108.dp).coerceAtLeast(136.dp),
                356.dp,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                LivingBreathingArtwork(
                    track = track,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    atmosphere = atmosphere,
                    onTogglePlay = onTogglePlay,
                    modifier = Modifier.size(artSize),
                )
                Spacer(Modifier.height(18.dp))
                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        (fadeIn(tween(MusicoMotion.MED, easing = MusicoMotion.decelerate)) +
                            slideInVertically(MusicoMotion.spatialDefault()) { it / 3 }) togetherWith fadeOut(tween(MusicoMotion.FAST))
                    },
                    label = "npTitleMorph",
                ) { current ->
                    Text(
                        current.title.ifEmpty { "Untitled" },
                        color = MaterialTheme.colorScheme.onSurface,
                        style = Musico.type.headline.copy(fontSize = 23.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    track.artist.ifEmpty { track.album }.ifEmpty { "Unknown artist" },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = Musico.type.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QueuePage(
    queue: List<OnlineTrack>,
    queueIndex: Int,
    onPlayFromQueue: (Int) -> Unit,
    onRemoveFromQueue: (Int) -> Unit,
) {
    if (queue.isEmpty()) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Up next", style = Musico.type.display, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(8.dp))
            Text("Queue is empty — play something!", color = MaterialTheme.colorScheme.onSurfaceVariant, style = Musico.type.body)
        }
        return
    }
    Column(Modifier.fillMaxSize().padding(vertical = 4.dp)) {
        Text(
            "Up next · ${queue.size} tracks",
            style = Musico.type.section,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(queue.size, key = { "q-${queue[it].videoId}-$it" }) { i ->
                val item = queue[i]
                val isCurrent = i == queueIndex
                val rowBg by animateColorAsState(
                    targetValue = if (isCurrent) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else if (Musico.extras.isDark) {
                        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.85f)
                    },
                    animationSpec = MusicoMotion.effectsDefault(),
                    label = "queueBg$i",
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
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
                            color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                            style = Musico.type.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            item.artist.ifEmpty { "Unknown artist" },
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = Musico.type.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (isCurrent) {
                        Surface(
                            shape = MusicoShapes.pill,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Text(
                                "PLAYING",
                                fontSize = 9.sp,
                                letterSpacing = 1.2.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { hapticTick(); onRemoveFromQueue(i) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Remove from queue",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val totalSecs = (ms / 1000).coerceAtLeast(0)
    val m = totalSecs / 60
    val s = totalSecs % 60
    return "$m:${s.toString().padStart(2, '0')}"
}
