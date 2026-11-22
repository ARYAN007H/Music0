package io.github.aryan007h.musicoonline.ui

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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.platform.PlatformBackHandler
import io.github.aryan007h.musicoonline.platform.currentTimeMillis
import io.github.aryan007h.musicoonline.platform.showPlatformToast
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.components.EqualizerBars
import io.github.aryan007h.musicoonline.ui.components.LikeButton
import io.github.aryan007h.musicoonline.ui.components.TrackActionSheet
import io.github.aryan007h.musicoonline.ui.components.musicoTopInset
import io.github.aryan007h.musicoonline.ui.detail.DetailScreen
import io.github.aryan007h.musicoonline.ui.home.HomeScreen
import io.github.aryan007h.musicoonline.ui.library.AddToPlaylistSheet
import io.github.aryan007h.musicoonline.ui.library.CreateOrRenamePlaylistDialog
import io.github.aryan007h.musicoonline.ui.library.ImportPlaylistDialog
import io.github.aryan007h.musicoonline.ui.library.LibraryCollectionRef
import io.github.aryan007h.musicoonline.ui.library.LibraryScreen
import io.github.aryan007h.musicoonline.ui.library.PlaylistDetailScreen
import io.github.aryan007h.musicoonline.ui.library.PlaylistMosaicArtwork
import io.github.aryan007h.musicoonline.ui.library.formatSongCount
import io.github.aryan007h.musicoonline.ui.onboarding.OnboardingScreen
import io.github.aryan007h.musicoonline.ui.player.ArtworkAtmosphere
import io.github.aryan007h.musicoonline.ui.player.NowPlayingScreen
import io.github.aryan007h.musicoonline.ui.player.PlayerBackdrop
import io.github.aryan007h.musicoonline.ui.player.formatTime
import io.github.aryan007h.musicoonline.ui.player.rememberArtworkAtmosphere
import io.github.aryan007h.musicoonline.ui.player.rememberSmoothPositionMs
import io.github.aryan007h.musicoonline.ui.profile.ProfileScreen
import io.github.aryan007h.musicoonline.ui.radio.RadioScreen
import io.github.aryan007h.musicoonline.ui.search.SearchScreen
import io.github.aryan007h.musicoonline.ui.settings.EqScreen
import io.github.aryan007h.musicoonline.ui.settings.SettingsScreen
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.MusicoTheme
import io.github.aryan007h.musicoonline.ui.theme.hapticThud
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import io.github.aryan007h.musicoonline.ui.theme.pressable
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive

/**
 * Adaptive Material 3 Expressive shell shared across Android & Desktop JVM:
 * - Compact (< 840dp): Floating Pill Island + Cover-Tinted Glass Mini Player
 *   with swipe next/prev + 1:1 Receding Shell modal physics.
 * - Widescreen (>= 840dp): Left M3 Expressive Sidebar Rail (tabs + playlists +
 *   Liked/Downloads + Profile/Settings) + Docked Cover-Tinted Bottom Player Bar
 *   (seek, volume, quick Lyrics Studio & Queue buttons) + Split-Stage Now Playing.
 */
@Composable
fun MusicoApp(
    vm: MusicViewModel,
    onExitApp: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    var sheetTrack by remember { mutableStateOf<OnlineTrack?>(null) }
    var playlistTargetTracks by remember { mutableStateOf<List<OnlineTrack>?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showImportPlaylistDialog by remember { mutableStateOf(false) }
    var playerDragProgress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.errorSeq) {
        if (state.error != null) {
            delay(if (state.undoActionLabel != null) 6500 else 4000)
            vm.dismissError()
        }
    }

    var lastBackAt by remember { mutableLongStateOf(0L) }
    PlatformBackHandler(enabled = state.overlay == AppOverlay.NONE && state.tab == AppTab.HOME) {
        val now = currentTimeMillis()
        if (now - lastBackAt < 2200L) {
            onExitApp()
        } else {
            lastBackAt = now
            hapticTick()
            showPlatformToast("Press back again to exit")
        }
    }
    PlatformBackHandler(enabled = state.overlay == AppOverlay.NONE && state.tab != AppTab.HOME) {
        hapticTick()
        vm.selectTab(AppTab.HOME)
    }
    PlatformBackHandler(enabled = state.overlay == AppOverlay.ONBOARD) { vm.dismissOnboarding() }
    PlatformBackHandler(enabled = state.overlay == AppOverlay.PLAYER) {
        playerDragProgress = 0f
        vm.closeOverlay()
    }
    PlatformBackHandler(enabled = state.overlay == AppOverlay.SETTINGS || state.overlay == AppOverlay.PROFILE) {
        vm.closeOverlay()
    }
    PlatformBackHandler(enabled = state.overlay == AppOverlay.EQ) { vm.openOverlay(AppOverlay.SETTINGS) }
    PlatformBackHandler(enabled = state.overlay == AppOverlay.COLLECTION) { vm.closeCollection() }
    PlatformBackHandler(enabled = state.overlay == AppOverlay.DETAIL) { vm.popDetail() }
    PlatformBackHandler(enabled = sheetTrack != null) { sheetTrack = null }
    PlatformBackHandler(enabled = playlistTargetTracks != null) { playlistTargetTracks = null }

    MusicoTheme(palette = state.palette) {
        val activeAtmosphere = rememberArtworkAtmosphere(state.nowPlaying?.thumbnail)

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val isWidescreen = maxWidth >= 840.dp

            val playerOpen = state.overlay == AppOverlay.PLAYER
            val pushOpen = state.overlay == AppOverlay.DETAIL || state.overlay == AppOverlay.COLLECTION
            val effectivePlayerOpen = if (playerOpen) (1f - playerDragProgress).coerceIn(0f, 1f) else 0f

            val shellScale by animateFloatAsState(
                targetValue = if (isWidescreen) 1f - 0.025f * effectivePlayerOpen else 1f - 0.08f * effectivePlayerOpen,
                animationSpec = MusicoMotion.spatial(),
                label = "shellScale",
            )
            val shellRadius by animateDpAsState(
                targetValue = if (isWidescreen) (18f * effectivePlayerOpen).dp else (34f * effectivePlayerOpen).dp,
                animationSpec = MusicoMotion.spatial(),
                label = "shellRadius",
            )
            val shellShift by animateDpAsState(
                targetValue = if (pushOpen && !isWidescreen) (-36).dp else 0.dp,
                animationSpec = MusicoMotion.spatial(),
                label = "shellShift",
            )
            val shellDim by animateFloatAsState(
                targetValue = when {
                    playerOpen -> 0.45f * effectivePlayerOpen
                    pushOpen && !isWidescreen -> 0.20f
                    else -> 0f
                },
                animationSpec = tween(MusicoMotion.MED),
                label = "shellDim",
            )

            val mainTabs = remember(vm) {
                movableContentOf<MusicViewModel.UiState, ArtworkAtmosphere> { currentState, currentAtmosphere ->
                    MainTabContent(
                        state = currentState,
                        atmosphere = currentAtmosphere,
                        vm = vm,
                        onTrackLongClick = { sheetTrack = it },
                    )
                }
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val safeScale = shellScale.coerceAtLeast(0.01f)
                        val safeRadius = shellRadius.coerceAtLeast(0.dp)
                        scaleX = safeScale
                        scaleY = safeScale
                        translationX = shellShift.toPx()
                        if (safeRadius > 0.dp) {
                            shape = RoundedCornerShape(safeRadius)
                            clip = true
                        } else {
                            clip = false
                        }
                    },
            ) {
                if (isWidescreen) {
                    // ── Widescreen Desktop / Tablet Shell ────────────────────────
                    Row(Modifier.fillMaxSize()) {
                        DesktopNavigationRail(
                            state = state,
                            onSelectTab = { hapticTick(); vm.selectTab(it) },
                            onOpenCollection = { hapticTick(); vm.openCollection(it) },
                            onNewPlaylist = { hapticTick(); showCreatePlaylistDialog = true },
                            onOpenProfile = { hapticTick(); vm.openOverlay(AppOverlay.PROFILE) },
                            onOpenSettings = { hapticTick(); vm.openOverlay(AppOverlay.SETTINGS) },
                        )
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            mainTabs(state, activeAtmosphere)
                            DesktopDockedPlayerBar(
                                state = state,
                                atmosphere = activeAtmosphere,
                                onExpandPlayer = { vm.openPlayer(0) },
                                onOpenLyrics = { vm.openPlayer(1) },
                                onOpenQueue = { vm.openPlayer(2) },
                                onTogglePlay = vm::onTogglePlayback,
                                onNext = vm::next,
                                onPrevious = vm::previous,
                                onSeek = vm::seekTo,
                                onToggleRepeat = vm::toggleRepeat,
                                onLikeToggle = vm::toggleLikeCurrent,
                                onVolumeChange = vm::setVolume,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 24.dp, vertical = 14.dp)
                                    .widthIn(max = 1040.dp),
                            )
                        }
                    }
                } else {
                    // ── Compact Phone Shell ──────────────────────────────────────
                    // Backgrounds & AmbientTopWash bleed edge-to-edge behind the
                    // status bar/cutout; each screen applies `musicoTopInset()`
                    // to its foreground content so top-edge behavior is identical.
                    Column(Modifier.fillMaxSize()) {
                        mainTabs(state, activeAtmosphere)
                    }

                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                    ) {
                        var lastTrack by remember { mutableStateOf<OnlineTrack?>(null) }
                        val activeMiniTrack = state.resolvingTrack ?: state.nowPlaying
                        activeMiniTrack?.let { lastTrack = it }
                        AnimatedVisibility(
                            visible = activeMiniTrack != null,
                            enter = slideInVertically(MusicoMotion.spatial()) { it } +
                                fadeIn(tween(MusicoMotion.MED)),
                            exit = slideOutVertically(
                                tween(MusicoMotion.SLOW, easing = MusicoMotion.accelerate),
                            ) { it } + fadeOut(tween(MusicoMotion.FAST)),
                        ) {
                            lastTrack?.let { track ->
                                MiniPlayer(
                                    state = state,
                                    track = track,
                                    atmosphere = activeAtmosphere,
                                    onExpand = { if (state.nowPlaying != null) vm.openPlayer(0) },
                                    onToggle = vm::onTogglePlayback,
                                    onNext = vm::next,
                                    onPrevious = vm::previous,
                                )
                            }
                        }
                        PillNavBar(
                            current = state.tab,
                            onSelect = { tab -> hapticTick(); vm.selectTab(tab) },
                            profileActive = state.overlay == AppOverlay.PROFILE,
                            onProfile = { hapticTick(); vm.openOverlay(AppOverlay.PROFILE) },
                            displayName = state.displayName,
                        )
                    }
                }
            }

            // Receding dim veil
            if (shellDim > 0.005f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = shellDim.coerceIn(0f, 1f))),
                )
            }

            // Update-available popup
            AnimatedVisibility(
                visible = state.updateInfo?.updateAvailable == true &&
                    !state.updateDismissed && state.overlay == AppOverlay.NONE,
                enter = slideInVertically(MusicoMotion.expressive()) { -it } +
                    fadeIn(tween(MusicoMotion.MED)),
                exit = slideOutVertically(
                    tween(MusicoMotion.SLOW, easing = MusicoMotion.accelerate),
                ) { -it } + fadeOut(tween(MusicoMotion.FAST)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .musicoTopInset()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MusicoShapes.chip,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressable(onClick = vm::downloadAndInstallUpdate, pressedScale = 0.97f),
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val label = if (state.updateDownloading) {
                            "Downloading Music0 ${state.updateInfo?.latest}… " +
                                "${state.updateDownloadedMb}/${state.updateTotalMb} MB"
                        } else {
                            "Music0 ${state.updateInfo?.latest} available — tap to update"
                        }
                        Text(
                            label,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        if (!state.updateDownloading) {
                            Text(
                                "Dismiss",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.pressable(onClick = vm::dismissUpdateBanner),
                            )
                        }
                    }
                }
            }

            // ── Full-Screen Overlays ─────────────────────────────────────
            AnimatedVisibility(
                visible = state.overlay == AppOverlay.PLAYER,
                enter = slideInVertically(MusicoMotion.spatial()) { it } +
                    fadeIn(tween(MusicoMotion.MED)),
                exit = slideOutVertically(
                    tween(MusicoMotion.SLOW, easing = MusicoMotion.accelerate),
                ) { it } + fadeOut(tween(MusicoMotion.FAST)),
                modifier = Modifier.fillMaxSize(),
            ) {
                PlayerBackdrop(
                    backdrop = state.lyricBackdrop.id,
                    track = state.nowPlaying,
                    atmosphere = activeAtmosphere,
                    isPlaying = state.isPlaying,
                    modifier = Modifier.fillMaxSize(),
                )
                NowPlayingScreen(
                    track = state.nowPlaying,
                    isPlaying = state.isPlaying,
                    isBuffering = state.isBuffering,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    lyrics = state.lyrics,
                    lyricsLoading = state.lyricsLoading,
                    lyricsMode = state.lyricsMode,
                    lyricOffsetMs = state.lyricOffsetMs,
                    atmosphere = activeAtmosphere,
                    queue = state.queue,
                    queueIndex = state.queueIndex,
                    liked = state.nowPlaying?.videoId in state.likedIds,
                    repeatAll = state.repeatAll,
                    initialPage = state.playerInitialPage,
                    onCollapse = {
                        playerDragProgress = 0f
                        vm.closeOverlay()
                    },
                    onTogglePlay = vm::onTogglePlayback,
                    onNext = vm::next,
                    onPrevious = vm::previous,
                    onSeek = vm::seekTo,
                    onLikeToggle = vm::toggleLikeCurrent,
                    onRepeatToggle = vm::toggleRepeat,
                    onAddToPlaylist = { track -> playlistTargetTracks = listOf(track) },
                    onLyricsModeChange = vm::setLyricsMode,
                    onLyricOffsetChange = vm::setLyricOffsetForCurrent,
                    onCycleBackdrop = vm::cycleLyricBackdrop,
                    onShareFeedback = vm::showToast,
                    onPlayFromQueue = vm::playFromQueue,
                    onRemoveFromQueue = vm::removeFromQueue,
                    onDragProgressChange = { progress -> playerDragProgress = progress },
                )
            }

            AnimatedVisibility(
                visible = state.overlay == AppOverlay.COLLECTION,
                enter = slideInHorizontally(MusicoMotion.spatial()) { it } +
                    fadeIn(tween(MusicoMotion.MED)),
                exit = slideOutHorizontally(
                    tween(MusicoMotion.SLOW, easing = MusicoMotion.accelerate),
                ) { it } + fadeOut(tween(MusicoMotion.FAST)),
                modifier = Modifier.fillMaxSize(),
            ) {
                val colRef = state.activeCollection
                if (colRef != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        PlaylistDetailScreen(
                            ref = colRef,
                            playlists = state.playlists,
                            likedTracks = state.likedTracks,
                            downloads = state.downloads,
                            downloadsTotalBytes = state.downloadsTotalBytes,
                            smartMixes = state.smartMixes,
                            recentTracks = state.recentlyPlayed,
                            currentTrackId = state.nowPlaying?.videoId,
                            isPlaying = state.isPlaying,
                            resolvingId = state.resolvingId,
                            likedIds = state.likedIds,
                            downloadingDone = state.savingDone,
                            downloadingTotal = state.savingTotal,
                            onBack = vm::closeCollection,
                            onPlayTracks = vm::playQueue,
                            onShuffleTracks = vm::playShuffled,
                            onDownloadAll = vm::downloadTracksOffline,
                            onLikeToggle = vm::toggleLike,
                            onTrackLongClick = { sheetTrack = it },
                            onRenamePlaylist = vm::renamePlaylist,
                            onDeletePlaylist = vm::deletePlaylist,
                            onAddTrackToPlaylist = { pid, track -> vm.addTracksToPlaylist(pid, listOf(track)) },
                            onRemoveTrackFromPlaylist = vm::removeTrackFromPlaylist,
                            onMoveTrackInPlaylist = vm::moveTrackInPlaylist,
                            onSaveMixAsPlaylist = vm::createPlaylistAndAdd,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = state.overlay == AppOverlay.SETTINGS,
                enter = slideInVertically(MusicoMotion.spatial()) { it / 3 } +
                    fadeIn(tween(MusicoMotion.MED)),
                exit = slideOutVertically(
                    tween(MusicoMotion.SLOW, easing = MusicoMotion.accelerate),
                ) { it / 3 } + fadeOut(tween(MusicoMotion.FAST)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    SettingsScreen(
                        coreVersion = state.coreVersion,
                        lyricBackdrop = state.lyricBackdrop,
                        palette = state.palette,
                        offlineMode = state.coreSettingsOffline,
                        autoplay = state.coreSettingsAutoplay,
                        streamingQuality = state.coreSettingsStreamingQuality,
                        cacheMb = state.coreSettingsCacheMb,
                        cacheBytes = state.cacheBytes,
                        settingsSaving = state.settingsSaving,
                        onBack = vm::closeOverlay,
                        onBackdropChange = vm::setLyricBackdrop,
                        onPaletteChange = vm::setPalette,
                        skipSilence = state.skipSilence,
                        playbackSpeed = state.playbackSpeed,
                        onOfflineChange = { v -> vm.saveCoreSetting("offline_mode", JsonPrimitive(v)) },
                        onAutoplayChange = { v -> vm.saveCoreSetting("autoplay", JsonPrimitive(v)) },
                        onSkipSilenceChange = vm::setSkipSilence,
                        onSpeedChange = vm::setPlaybackSpeed,
                        updateInfo = state.updateInfo,
                        updateChecking = state.updateChecking,
                        updateDownloading = state.updateDownloading,
                        updateDownloadedMb = state.updateDownloadedMb,
                        updateTotalMb = state.updateTotalMb,
                        onCheckUpdates = vm::checkForUpdate,
                        onUpdateNow = vm::downloadAndInstallUpdate,
                        onQualityChange = vm::setStreamingQuality,
                        onCacheCapChange = vm::setCacheCap,
                        onClearCache = vm::clearStreamCache,
                        onRefreshCache = vm::refreshCacheUsage,
                        onOpenEq = { vm.openOverlay(AppOverlay.EQ) },
                        onImportPlaylist = {
                            vm.dismissImportState()
                            showImportPlaylistDialog = true
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = state.overlay == AppOverlay.DETAIL,
                enter = slideInHorizontally(MusicoMotion.spatial()) { it } +
                    fadeIn(tween(MusicoMotion.MED)),
                exit = slideOutHorizontally(
                    tween(MusicoMotion.SLOW, easing = MusicoMotion.accelerate),
                ) { it } + fadeOut(tween(MusicoMotion.FAST)),
                modifier = Modifier.fillMaxSize(),
            ) {
                val topDetail = state.details.lastOrNull()
                if (topDetail != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        DetailScreen(
                            entry = topDetail,
                            currentTrackId = state.nowPlaying?.videoId,
                            isPlaying = state.isPlaying,
                            likedIds = state.likedIds,
                            isSavedInLibrary = vm.isDetailSavedInLibrary(topDetail.browseId),
                            onToggleSaveLibrary = { vm.toggleSaveDetailToLibrary(topDetail) },
                            onAddAllToPlaylist = {
                                val tracks = vm.detailTracks(topDetail)
                                if (tracks.isNotEmpty()) playlistTargetTracks = tracks
                            },
                            onBack = vm::popDetail,
                            onPlay = { vm.playDetail(topDetail) },
                            onQueueAll = { vm.queueDetail(topDetail) },
                            onSaveOffline = { vm.saveDetailOffline(topDetail) },
                            onPlayTrack = vm::playTrack,
                            onLikeToggle = vm::toggleLike,
                            onOpenItem = vm::openShelfItem,
                            onRetry = vm::retryTopDetail,
                            onTrackLongClick = { sheetTrack = it },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = state.overlay == AppOverlay.EQ,
                enter = slideInHorizontally(MusicoMotion.spatial()) { it / 4 } +
                    fadeIn(tween(MusicoMotion.MED)),
                exit = slideOutHorizontally(
                    tween(MusicoMotion.MED, easing = MusicoMotion.accelerate),
                ) { it / 4 } + fadeOut(tween(MusicoMotion.FAST)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    EqScreen(onBack = { vm.openOverlay(AppOverlay.SETTINGS) })
                }
            }

            sheetTrack?.let { track ->
                TrackActionSheet(
                    track = track,
                    isLiked = track.videoId in state.likedIds,
                    saving = state.savingTrackId == track.videoId,
                    onDismiss = { sheetTrack = null },
                    onAddToPlaylist = { playlistTargetTracks = listOf(track) },
                    onPlayNext = { vm.playNext(track) },
                    onAddToQueue = { vm.enqueue(track) },
                    onSaveOffline = { vm.saveTrackOffline(track) },
                    onToggleLike = { vm.toggleLike(track) },
                )
            }

            playlistTargetTracks?.let { targets ->
                AddToPlaylistSheet(
                    tracks = targets,
                    playlists = state.playlists,
                    onDismiss = { playlistTargetTracks = null },
                    onAddToPlaylist = vm::addTracksToPlaylist,
                    onCreateAndAdd = vm::createPlaylistAndAdd,
                )
            }

            if (showCreatePlaylistDialog) {
                CreateOrRenamePlaylistDialog(
                    title = "New playlist",
                    confirmLabel = "Create",
                    onDismiss = { showCreatePlaylistDialog = false },
                    onConfirm = { name -> vm.createPlaylist(name) },
                )
            }

            if (showImportPlaylistDialog || state.importingPlaylist) {
                ImportPlaylistDialog(
                    importing = state.importingPlaylist,
                    progress = state.importProgress,
                    result = state.importResult,
                    error = state.importError,
                    onImportUrl = vm::importPlaylistFromUrl,
                    onImportFile = vm::importPlaylistFromFile,
                    onFileError = vm::reportImportError,
                    onCancelImport = vm::cancelPlaylistImport,
                    onOpenPlaylist = { playlistId ->
                        vm.dismissImportState()
                        showImportPlaylistDialog = false
                        vm.openCollection(LibraryCollectionRef.Playlist(playlistId))
                    },
                    onDismiss = {
                        vm.dismissImportState()
                        showImportPlaylistDialog = false
                    },
                )
            }

            AnimatedVisibility(
                visible = state.overlay == AppOverlay.ONBOARD,
                enter = fadeIn(tween(MusicoMotion.SLOW)) +
                    scaleIn(
                        initialScale = 1.045f,
                        animationSpec = tween(MusicoMotion.SLOW, easing = MusicoMotion.emphasized),
                    ),
                exit = fadeOut(tween(MusicoMotion.MED)),
                modifier = Modifier.fillMaxSize(),
            ) {
                OnboardingScreen(
                    artistResults = state.artistResults,
                    artistSearching = state.artistSearching,
                    onFinish = vm::finishOnboarding,
                    onDismiss = vm::dismissOnboarding,
                    onArtistQuery = vm::searchArtists,
                    onImportPlaylist = {
                        vm.dismissImportState()
                        showImportPlaylistDialog = true
                    },
                )
            }

            AnimatedVisibility(
                visible = state.overlay == AppOverlay.PROFILE,
                enter = slideInVertically(MusicoMotion.spatial()) { it / 3 } +
                    fadeIn(tween(MusicoMotion.MED)),
                exit = slideOutVertically(
                    tween(MusicoMotion.SLOW, easing = MusicoMotion.accelerate),
                ) { it / 3 } + fadeOut(tween(MusicoMotion.FAST)),
                modifier = Modifier.fillMaxSize(),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    ProfileScreen(
                        displayName = state.displayName,
                        likedCount = state.likedIds.size,
                        playedCount = state.recentlyPlayed.size,
                        playlistsCount = state.playlists.size,
                        listeningStats = state.listeningStats,
                        coreVersion = state.coreVersion,
                        onBack = vm::closeOverlay,
                        onRename = vm::setDisplayName,
                        onOpenSettings = { vm.openOverlay(AppOverlay.SETTINGS) },
                    )
                }
            }

            // ── Bottom Toast with Optional Action (Undo / Retry) ─────────
            val toastLift = when {
                isWidescreen -> 100.dp
                (state.nowPlaying != null || state.resolvingTrack != null) && state.overlay == AppOverlay.NONE -> 152.dp
                else -> 92.dp
            }
            AnimatedVisibility(
                visible = state.error != null,
                enter = slideInVertically(MusicoMotion.spatial()) { it } +
                    fadeIn(tween(MusicoMotion.FAST)),
                exit = slideOutVertically(
                    tween(MusicoMotion.MED, easing = MusicoMotion.accelerate),
                ) { it } + fadeOut(tween(MusicoMotion.FAST)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = toastLift),
            ) {
                Surface(
                    shape = MusicoShapes.chip,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 10.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(
                            state.error.orEmpty(),
                            style = Musico.type.body,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        state.undoActionLabel?.let { label ->
                            Text(
                                label.uppercase(),
                                style = Musico.type.caption,
                                fontWeight = FontWeight.ExtraBold,
                                color = Musico.extras.mint,
                                modifier = Modifier.pressable(
                                    onClick = { hapticTick(); vm.undoLastAction() },
                                    pressedScale = 0.9f,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainTabContent(
    state: MusicViewModel.UiState,
    atmosphere: ArtworkAtmosphere,
    vm: MusicViewModel,
    onTrackLongClick: (OnlineTrack) -> Unit,
) {
    AnimatedContent(
        targetState = state.tab,
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val enter = slideInHorizontally(MusicoMotion.spatial()) {
                if (forward) it / 6 else -it / 6
            } + fadeIn(tween(MusicoMotion.MED, easing = MusicoMotion.decelerate))
            val exit = slideOutHorizontally(
                tween(MusicoMotion.MED, easing = MusicoMotion.accelerate),
            ) {
                if (forward) -it / 8 else it / 8
            } + fadeOut(tween(MusicoMotion.FAST))
            enter togetherWith exit
        },
        label = "tabContent",
        modifier = Modifier.fillMaxSize(),
    ) { tab ->
        when (tab) {
            AppTab.HOME -> HomeScreen(
                displayName = state.displayName,
                atmosphere = atmosphere,
                shelves = state.personalizedShelves + state.homeShelves,
                smartMixes = state.smartMixes,
                playlists = state.playlists,
                recentlyPlayed = state.recentlyPlayed,
                likedCount = state.likedIds.size,
                downloadsCount = state.downloads.size,
                loading = state.homeLoading,
                error = state.homeError,
                styleItems = state.styleItems,
                moreLoading = state.moreLoading,
                personalizing = state.personalizing,
                personalizationError = state.personalizationError,
                onOpenItem = vm::openShelfItem,
                onOpenCollection = vm::openCollection,
                onOpenTrack = vm::playTrack,
                onRetry = vm::loadHome,
                onLoadMore = vm::loadMoreHome,
                onRetryPersonalization = vm::retryPersonalization,
                onDismissPersonalizationError = vm::dismissPersonalizationError,
                currentTrackId = state.nowPlaying?.videoId,
                isPlaying = state.isPlaying,
            )
            AppTab.RADIO -> RadioScreen(
                atmosphere = atmosphere,
                styles = state.radioStyles,
                loading = state.radioLoading,
                error = state.error,
                onOpenStyle = { style -> vm.openShelfItem(style, emptyList()) },
                onRetry = vm::loadRadio,
            )
            AppTab.LIBRARY -> LibraryScreen(
                atmosphere = atmosphere,
                playlists = state.playlists,
                likedTracks = state.likedTracks,
                downloads = state.downloads,
                downloadsTotalBytes = state.downloadsTotalBytes,
                savedAlbums = state.libraryAlbums,
                savedArtists = state.libraryArtists,
                smartMixes = state.smartMixes,
                recentlyPlayed = state.recentlyPlayed,
                queue = state.queue,
                queueIndex = state.queueIndex,
                currentTrackId = state.nowPlaying?.videoId,
                isPlaying = state.isPlaying,
                resolvingId = state.resolvingId,
                likedIds = state.likedIds,
                onOpenCollection = vm::openCollection,
                onCreatePlaylist = vm::createPlaylist,
                onPlayTracks = vm::playQueue,
                onOpenCatalogEntry = vm::openCatalogEntry,
                onOpenTrack = vm::playTrack,
                onLikeToggle = vm::toggleLike,
                onTrackLongClick = onTrackLongClick,
            )
            AppTab.SEARCH -> SearchScreen(
                atmosphere = atmosphere,
                query = state.searchQuery,
                submittedQuery = state.searchSubmitted,
                searching = state.searching,
                results = state.searchResults,
                suggestions = state.suggestions,
                recentSearches = state.recentSearches,
                currentTrackId = state.nowPlaying?.videoId,
                isPlaying = state.isPlaying,
                resolvingId = state.resolvingId,
                likedIds = state.likedIds,
                onQueryChange = vm::onQueryChange,
                onSubmit = vm::search,
                onSuggestionClick = { s -> vm.onQueryChange(s); vm.search(s) },
                onClearRecents = vm::clearRecentSearches,
                onOpenTrack = vm::playTrack,
                onLikeToggle = vm::toggleLike,
                onTrackLongClick = onTrackLongClick,
            )
        }
    }
}

// ── Desktop Left Navigation Sidebar (Widescreen >= 840dp) ─────────────────

@Composable
private fun DesktopNavigationRail(
    state: MusicViewModel.UiState,
    onSelectTab: (AppTab) -> Unit,
    onOpenCollection: (LibraryCollectionRef) -> Unit,
    onNewPlaylist: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = Modifier
            .width(248.dp)
            .fillMaxHeight(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 18.dp),
        ) {
            // Brand wordmark
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            Brush.linearGradient(listOf(Musico.extras.primary, Musico.extras.mint)),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("M", fontWeight = FontWeight.Black, fontSize = 18.sp, color = Color.White)
                }
                Column {
                    Text(
                        "Music0",
                        style = Musico.type.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        state.coreVersion?.let { "v$it" } ?: "Desktop",
                        style = Musico.type.caption,
                        color = Musico.extras.mint,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // Primary navigation destinations
            val navItems = listOf(
                Triple(AppTab.HOME, Icons.Filled.Home, "Home"),
                Triple(AppTab.RADIO, Icons.Filled.Radio, "Radio"),
                Triple(AppTab.LIBRARY, Icons.Filled.LibraryMusic, "Library"),
                Triple(AppTab.SEARCH, Icons.Filled.Search, "Search"),
            )
            navItems.forEach { (tab, icon, label) ->
                val active = state.overlay == AppOverlay.NONE && state.tab == tab
                RailNavItem(
                    icon = icon,
                    label = label,
                    active = active,
                    onClick = { onSelectTab(tab) },
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(14.dp))

            // Quick Library shortcuts
            RailNavItem(
                icon = Icons.Filled.Favorite,
                label = "Liked Songs · ${state.likedIds.size}",
                active = state.overlay == AppOverlay.COLLECTION && state.activeCollection == LibraryCollectionRef.LikedSongs,
                accentTint = Musico.extras.coral,
                onClick = { onOpenCollection(LibraryCollectionRef.LikedSongs) },
            )
            RailNavItem(
                icon = Icons.Filled.DownloadDone,
                label = "Downloads · ${state.downloads.size}",
                active = state.overlay == AppOverlay.COLLECTION && state.activeCollection == LibraryCollectionRef.OfflineDownloads,
                accentTint = Musico.extras.mint,
                onClick = { onOpenCollection(LibraryCollectionRef.OfflineDownloads) },
            )

            Spacer(Modifier.height(14.dp))

            // Playlists header + "+ New"
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "PLAYLISTS",
                    style = Musico.type.overline,
                    color = Musico.extras.inkDim,
                )
                IconButton(onClick = onNewPlaylist, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = "New playlist", tint = Musico.extras.mint, modifier = Modifier.size(18.dp))
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(state.playlists, key = { "rail-pl-${it.id}" }) { pl ->
                    val active = state.overlay == AppOverlay.COLLECTION &&
                        (state.activeCollection as? LibraryCollectionRef.Playlist)?.id == pl.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f) else Color.Transparent)
                            .pressable(onClick = { onOpenCollection(LibraryCollectionRef.Playlist(pl.id)) }, pressedScale = 0.97f)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlaylistMosaicArtwork(
                            thumbnails = pl.tracks.map { it.thumbnail },
                            modifier = Modifier.size(32.dp),
                            cornerRadius = 8.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                pl.name,
                                style = Musico.type.body,
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatSongCount(pl.tracks.size),
                                style = Musico.type.tabularCaption,
                                fontSize = 10.sp,
                                color = Musico.extras.inkDim,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(Modifier.height(10.dp))

            // Footer: Profile & Settings
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(MusicoShapes.chip)
                        .pressable(onClick = onOpenProfile, pressedScale = 0.96f)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(30.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                state.displayName.trim().take(1).uppercase().ifEmpty { "M" },
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        state.displayName,
                        style = Musico.type.title,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Musico.extras.inkDim)
                }
            }
        }
    }
}

@Composable
private fun RailNavItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    accentTint: Color? = null,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent,
        animationSpec = MusicoMotion.gentle(),
        label = "railBg",
    )
    val contentTint by animateColorAsState(
        targetValue = when {
            active -> MaterialTheme.colorScheme.primary
            accentTint != null -> accentTint
            else -> Musico.extras.inkDim
        },
        animationSpec = MusicoMotion.gentle(),
        label = "railTint",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(MusicoShapes.chip)
            .background(bg)
            .pressable(onClick = onClick, pressedScale = 0.97f)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = contentTint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = Musico.type.title,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            color = if (active) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── Desktop Docked Cover-Tinted Player Bar (Widescreen >= 840dp) ──────────

@Composable
private fun DesktopDockedPlayerBar(
    state: MusicViewModel.UiState,
    atmosphere: ArtworkAtmosphere,
    onExpandPlayer: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenQueue: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleRepeat: () -> Unit,
    onLikeToggle: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.resolvingTrack ?: state.nowPlaying
    val smoothPosition = rememberSmoothPositionMs(
        polledPositionMs = state.positionMs,
        isPlaying = state.isPlaying,
        durationMs = state.durationMs,
    )
    var draggingSeek by remember { mutableFloatStateOf(Float.NaN) }
    val seekFraction = if (state.durationMs > 0L) {
        (smoothPosition.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else 0f
    val shownSeek = if (draggingSeek.isNaN()) seekFraction else draggingSeek

    val barTint by animateColorAsState(
        targetValue = if (track != null) atmosphere.orbPrimary.copy(alpha = 0.20f) else Color.Transparent,
        animationSpec = tween(700),
        label = "dockTint",
    )
    val dockBorderTint by animateColorAsState(
        targetValue = if (track != null) {
            atmosphere.glow.copy(alpha = 0.24f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
        },
        animationSpec = tween(700),
        label = "dockBorderTint",
    )
    Surface(
        shape = MusicoShapes.pillDock,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        border = BorderStroke(1.dp, dockBorderTint),
        shadowElevation = 20.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(76.dp),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0.00f to barTint,
                        0.38f to barTint.copy(alpha = barTint.alpha * 0.35f),
                        0.72f to Color.Transparent,
                        1.00f to barTint.copy(alpha = barTint.alpha * 0.22f),
                    ),
                ),
        ) {
            // Inset indeterminate resolve progress along the top pill curve
            if (state.resolvingId != null) {
                LinearProgressIndicator(
                    color = atmosphere.glow,
                    trackColor = atmosphere.glow.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(2.5.dp)
                        .clip(CircleShape)
                        .align(Alignment.TopCenter),
                )
            }

            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Left: Track Info + Like
                Row(
                    Modifier
                        .weight(0.30f)
                        .clip(RoundedCornerShape(14.dp))
                        .pressable(onClick = onExpandPlayer, enabled = track != null, pressedScale = 0.98f)
                        .padding(vertical = 6.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (track != null) {
                        Artwork(
                            url = track.thumbnail,
                            contentDescription = track.title,
                            modifier = Modifier.size(54.dp),
                            cornerRadius = 14.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title.ifEmpty { "Untitled" },
                                style = Musico.type.title,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(),
                            )
                            Text(
                                when {
                                    state.resolvingId == track.videoId -> "Resolving…"
                                    state.isBuffering -> "Buffering…"
                                    else -> track.artist.ifEmpty { "Unknown artist" }
                                },
                                style = Musico.type.caption,
                                color = Musico.extras.inkDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        LikeButton(
                            liked = track.videoId in state.likedIds,
                            onToggle = onLikeToggle,
                            size = 20.dp,
                        )
                    } else {
                        Text(
                            "Pick any song to start listening",
                            style = Musico.type.caption,
                            color = Musico.extras.inkDim,
                        )
                    }
                }

                // Center: Transport + Scrubbable Seek Bar
                Column(
                    Modifier.weight(0.42f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconButton(onClick = { hapticTick(); onToggleRepeat() }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Filled.Repeat,
                                contentDescription = "Repeat",
                                tint = if (state.repeatAll) Musico.extras.mint else Musico.extras.inkDim,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        IconButton(onClick = { hapticThud(); onPrevious() }, enabled = track != null, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        Surface(
                            shape = CircleShape,
                            color = Musico.extras.mint,
                            modifier = Modifier
                                .size(40.dp)
                                .pressable(onClick = { hapticThud(); onTogglePlay() }, enabled = track != null, pressedScale = 0.88f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                                    tint = Color(0xFF05291D),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        IconButton(onClick = { hapticThud(); onNext() }, enabled = track != null, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onBackground)
                        }
                        if (track != null) {
                            EqualizerBars(playing = state.isPlaying, color = Musico.extras.mint, modifier = Modifier.height(14.dp))
                        } else {
                            Spacer(Modifier.width(16.dp))
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            formatTime(if (draggingSeek.isNaN()) smoothPosition else (shownSeek * state.durationMs).toLong()),
                            style = Musico.type.tabularCaption,
                            fontSize = 10.sp,
                            color = Musico.extras.inkDim,
                        )
                        Slider(
                            value = shownSeek,
                            onValueChange = { draggingSeek = it },
                            onValueChangeFinished = {
                                if (!draggingSeek.isNaN() && state.durationMs > 0L) {
                                    onSeek((draggingSeek * state.durationMs).toLong())
                                }
                                draggingSeek = Float.NaN
                            },
                            enabled = track != null && state.durationMs > 0L,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.onBackground,
                                activeTrackColor = atmosphere.glow,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(18.dp),
                        )
                        Text(
                            formatTime(state.durationMs),
                            style = Musico.type.tabularCaption,
                            fontSize = 10.sp,
                            color = Musico.extras.inkDim,
                        )
                    }
                }

                // Right: Lyrics Studio + Queue + Volume
                Row(
                    Modifier.weight(0.28f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = onOpenLyrics,
                        enabled = track != null,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.Lyrics,
                            contentDescription = "Open Lyrics Studio",
                            tint = if (track != null) atmosphere.glow else Musico.extras.inkFaint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = onOpenQueue,
                        enabled = track != null,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            Icons.Filled.QueueMusic,
                            contentDescription = "Open Queue",
                            tint = if (track != null) MaterialTheme.colorScheme.onBackground else Musico.extras.inkFaint,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    var lastNonZeroVol by remember { mutableFloatStateOf(0.8f) }
                    IconButton(
                        onClick = {
                            if (state.volume > 0.01f) {
                                lastNonZeroVol = state.volume
                                onVolumeChange(0f)
                            } else {
                                onVolumeChange(lastNonZeroVol.coerceAtLeast(0.2f))
                            }
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            if (state.volume <= 0.01f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute",
                            tint = Musico.extras.inkDim,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Slider(
                        value = state.volume,
                        onValueChange = onVolumeChange,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = Musico.extras.mint,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .widthIn(min = 56.dp, max = 104.dp),
                    )
                }
            }
        }
    }
}

// ── Compact Swipeable Cover-Tinted Glass Mini Player ──────────────────────

@Composable
private fun MiniPlayer(
    state: MusicViewModel.UiState,
    track: OnlineTrack,
    atmosphere: ArtworkAtmosphere,
    onExpand: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 68.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    val animDragX by animateFloatAsState(
        targetValue = dragX,
        animationSpec = MusicoMotion.snappy(),
        label = "miniSwipeX",
    )

    val smoothPosition = rememberSmoothPositionMs(
        polledPositionMs = state.positionMs,
        isPlaying = state.isPlaying,
        durationMs = state.durationMs,
    )
    val coverTint by animateColorAsState(
        targetValue = atmosphere.orbPrimary.copy(alpha = 0.24f),
        animationSpec = tween(700),
        label = "miniCoverTint",
    )

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MusicoShapes.chip,
        shadowElevation = 10.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .border(1.dp, atmosphere.glow.copy(alpha = 0.18f), MusicoShapes.chip)
            .graphicsLayer {
                translationX = animDragX.coerceIn(-swipeThresholdPx * 1.15f, swipeThresholdPx * 1.15f)
            }
            .pointerInput(track.videoId) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        dragX = (dragX + dragAmount).coerceIn(-swipeThresholdPx * 1.3f, swipeThresholdPx * 1.3f)
                    },
                    onDragEnd = {
                        when {
                            dragX <= -swipeThresholdPx -> {
                                hapticThud()
                                onNext()
                            }
                            dragX >= swipeThresholdPx -> {
                                hapticThud()
                                onPrevious()
                            }
                        }
                        dragX = 0f
                    },
                    onDragCancel = { dragX = 0f },
                )
            }
            .pressable(onClick = onExpand, pressedScale = 0.985f),
    ) {
        Column(
            Modifier.background(
                Brush.horizontalGradient(
                    0.00f to coverTint,
                    0.36f to coverTint.copy(alpha = coverTint.alpha * 0.48f),
                    0.72f to coverTint.copy(alpha = coverTint.alpha * 0.10f),
                    1.00f to Color.Transparent,
                ),
            ),
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    url = track.thumbnail,
                    contentDescription = track.title,
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 14.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title.ifEmpty { "Untitled" },
                        style = Musico.type.title,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        when {
                            state.resolvingId == track.videoId -> "Resolving…"
                            state.isBuffering -> "Buffering…"
                            else -> track.artist.ifEmpty { "Unknown artist" }
                        },
                        style = Musico.type.caption,
                        color = Musico.extras.inkDim,
                        maxLines = 1,
                    )
                }
                if (state.resolvingId != null || state.isBuffering) {
                    EqualizerBars(playing = false, color = atmosphere.glow, modifier = Modifier.height(16.dp))
                } else {
                    EqualizerBars(playing = state.isPlaying, color = atmosphere.glow, modifier = Modifier.height(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Surface(
                    shape = CircleShape,
                    color = Musico.extras.mint,
                    modifier = Modifier
                        .size(42.dp)
                        .pressable(onClick = { hapticThud(); onToggle() }, pressedScale = 0.86f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = state.isPlaying,
                            transitionSpec = {
                                (scaleIn(
                                    initialScale = 0.55f,
                                    animationSpec = MusicoMotion.snappy(),
                                ) + fadeIn(tween(MusicoMotion.FAST))) togetherWith
                                    (scaleOut(
                                        targetScale = 0.55f,
                                        animationSpec = tween(MusicoMotion.FAST),
                                    ) + fadeOut(tween(MusicoMotion.FAST)))
                            },
                            label = "miniPlayIcon",
                        ) { playing ->
                            Icon(
                                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playing) "Pause" else "Play",
                                tint = Color(0xFF05291D),
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }
                }
            }
            if (state.resolvingId != null) {
                LinearProgressIndicator(
                    color = atmosphere.glow,
                    trackColor = atmosphere.glow.copy(alpha = 0.16f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                )
            } else {
                val fraction = if (state.durationMs > 0) {
                    (smoothPosition.toFloat() / state.durationMs).coerceIn(0f, 1f)
                } else 0f
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(3.dp)
                        .background(atmosphere.glow, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

// ── Compact M3-Expressive Floating Pill Island ────────────────────────────

private data class PillDest(
    val tab: AppTab,
    val icon: ImageVector,
    val label: String,
)

@Composable
private fun PillNavBar(
    current: AppTab,
    onSelect: (AppTab) -> Unit,
    profileActive: Boolean,
    onProfile: () -> Unit,
    displayName: String,
) {
    val destinations = remember {
        listOf(
            PillDest(AppTab.HOME, Icons.Filled.Home, "Home"),
            PillDest(AppTab.RADIO, Icons.Filled.Radio, "Radio"),
            PillDest(AppTab.LIBRARY, Icons.Filled.LibraryMusic, "Library"),
            PillDest(AppTab.SEARCH, Icons.Filled.Search, "Search"),
        )
    }
    val itemBounds = remember { mutableStateMapOf<Any, Rect>() }
    val profileKey = "profile"
    val density = LocalDensity.current

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MusicoShapes.chip,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 12.dp,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                val sel = if (profileActive) itemBounds[profileKey] else itemBounds[current]
                if (sel != null) {
                    val blobLeft by animateDpAsState(
                        targetValue = with(density) { sel.left.toDp() },
                        animationSpec = MusicoMotion.expressive(),
                        label = "blobLeft",
                    )
                    val blobRight by animateDpAsState(
                        targetValue = with(density) { sel.right.toDp() },
                        animationSpec = MusicoMotion.expressive(),
                        label = "blobRight",
                    )
                    val blobTop by animateDpAsState(
                        targetValue = with(density) { sel.top.toDp() },
                        animationSpec = MusicoMotion.gentle(),
                        label = "blobTop",
                    )
                    val blobBottom by animateDpAsState(
                        targetValue = with(density) { sel.bottom.toDp() },
                        animationSpec = MusicoMotion.gentle(),
                        label = "blobBottom",
                    )
                    Box(
                        Modifier
                            .offset(x = blobLeft, y = blobTop)
                            .width((blobRight - blobLeft).coerceAtLeast(0.dp))
                            .height((blobBottom - blobTop).coerceAtLeast(0.dp))
                            .clip(MusicoShapes.chip)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    destinations.forEach { dest ->
                        PillItem(
                            dest = dest,
                            selected = current == dest.tab && !profileActive,
                            onClick = { onSelect(dest.tab) },
                            onBounds = { rect -> itemBounds[dest.tab] = rect },
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    Box(
                        Modifier
                            .onGloballyPositioned { coords -> itemBounds[profileKey] = coords.boundsInParent() }
                            .clip(MusicoShapes.chip)
                            .pressable(onClick = onProfile, pressedScale = 0.9f)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (profileActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            modifier = Modifier.size(30.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    displayName.trim().take(1).uppercase().ifEmpty { "M" },
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 13.sp,
                                    color = if (profileActive) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    },
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
private fun PillItem(
    dest: PillDest,
    selected: Boolean,
    onClick: () -> Unit,
    onBounds: (Rect) -> Unit,
) {
    val labelWidth by animateDpAsState(
        targetValue = if (selected) 52.dp else 0.dp,
        animationSpec = MusicoMotion.expressive(),
        label = "pillLabel",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.14f else 1f,
        animationSpec = MusicoMotion.snappy(),
        label = "pillIconScale",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            Musico.extras.inkDim
        },
        animationSpec = MusicoMotion.gentle(),
        label = "pillTint",
    )
    Box(
        Modifier
            .onGloballyPositioned { onBounds(it.boundsInParent()) }
            .clip(MusicoShapes.chip)
            .pressable(onClick = onClick, pressedScale = 0.92f),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                dest.icon,
                contentDescription = dest.label,
                tint = tint,
                modifier = Modifier
                    .size(24.dp)
                    .scale(iconScale.coerceAtLeast(0.01f)),
            )
            AnimatedVisibility(visible = selected) {
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.width(labelWidth.coerceAtLeast(0.dp))) {
                if (selected) {
                    Text(
                        dest.label,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
