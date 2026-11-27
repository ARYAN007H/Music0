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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.platform.PlatformBackHandler
import io.github.aryan007h.musicoonline.platform.currentTimeMillis
import io.github.aryan007h.musicoonline.platform.showPlatformToast
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.components.EqualizerBars
import io.github.aryan007h.musicoonline.ui.components.ExpressivePlayPauseButton
import io.github.aryan007h.musicoonline.ui.components.ExpressiveWavySeekBar
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
import io.github.aryan007h.musicoonline.ui.theme.ExpressiveShapes
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
    onDarkResolved: ((Boolean) -> Unit)? = null,
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

    MusicoTheme(
        palette = state.palette,
        themeMode = state.themeMode,
        onDarkResolved = onDarkResolved,
    ) {
        val activeAtmosphere = rememberArtworkAtmosphere(state.nowPlaying?.thumbnail)

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            val windowMaxWidth = maxWidth
            val isWidescreen = windowMaxWidth >= 840.dp

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
                    val railWidth = DesktopLayoutMetrics.railWidthDp(windowMaxWidth.value).dp
                    Row(Modifier.fillMaxSize()) {
                        val isDark = Musico.extras.isDark
                        DesktopNavigationRail(
                            state = state,
                            railWidth = railWidth,
                            isDark = isDark,
                            onSelectTab = { hapticTick(); vm.selectTab(it) },
                            onOpenMadeForYou = { hapticTick(); vm.openMadeForYou() },
                            onOpenLibrarySection = { hapticTick(); vm.openLibrarySection(it) },
                            onOpenCollection = { hapticTick(); vm.openCollection(it) },
                            onOpenQueue = { hapticTick(); vm.openPlayer(2) },
                            onImportPlaylist = { hapticTick(); showImportPlaylistDialog = true },
                            onNewPlaylist = { hapticTick(); showCreatePlaylistDialog = true },
                            onToggleTheme = { hapticTick(); vm.toggleLightDark(isDark) },
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
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
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
                        themeMode = state.themeMode,
                        offlineMode = state.coreSettingsOffline,
                        autoplay = state.coreSettingsAutoplay,
                        streamingQuality = state.coreSettingsStreamingQuality,
                        cacheMb = state.coreSettingsCacheMb,
                        cacheBytes = state.cacheBytes,
                        settingsSaving = state.settingsSaving,
                        onBack = vm::closeOverlay,
                        onBackdropChange = vm::setLyricBackdrop,
                        onPaletteChange = vm::setPalette,
                        onThemeModeChange = vm::setThemeMode,
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
                        themeMode = state.themeMode,
                        onThemeModeChange = vm::setThemeMode,
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
                    color = MaterialTheme.colorScheme.inverseSurface,
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
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        state.undoActionLabel?.let { label ->
                            Text(
                                label.uppercase(),
                                style = Musico.type.caption,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.inversePrimary,
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
                savedArtists = state.libraryArtists,
                onOpenCatalogEntry = vm::openCatalogEntry,
                onStartMood = { mood ->
                    val styleMatch = state.radioStyles.firstOrNull {
                        it.title.contains(mood, ignoreCase = true)
                    } ?: state.styleItems.firstOrNull {
                        it.title.contains(mood, ignoreCase = true)
                    }
                    if (styleMatch != null) {
                        vm.openShelfItem(styleMatch, emptyList())
                    } else {
                        val query = "$mood music mix"
                        vm.onQueryChange(query)
                        vm.search(query)
                        vm.selectTab(AppTab.SEARCH)
                    }
                },
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
                selectedSection = state.librarySection,
                onSelectSection = vm::openLibrarySection,
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

/**
 * Pure responsive metrics for the widescreen Desktop/Tablet shell so 840–960dp
 * windows never crush the docked player bar or volume slider.
 */
object DesktopLayoutMetrics {
    fun railWidthDp(windowWidthDp: Float): Float = if (windowWidthDp >= 1080f) 256f else 224f
    fun showInlineVolumeSlider(barWidthDp: Float): Boolean = barWidthDp >= 740f
    fun showInlineLikeButton(barWidthDp: Float): Boolean = barWidthDp >= 640f
    fun showSecondaryTransportExtras(barWidthDp: Float): Boolean = barWidthDp >= 640f
}

// ── Desktop Left Navigation Drawer (Native Material 3 Expressive) ─────────

@Composable
private fun DesktopNavigationRail(
    state: MusicViewModel.UiState,
    railWidth: Dp = 256.dp,
    isDark: Boolean,
    onSelectTab: (AppTab) -> Unit,
    onOpenMadeForYou: () -> Unit,
    onOpenLibrarySection: (LibrarySection) -> Unit,
    onOpenCollection: (LibraryCollectionRef) -> Unit,
    onOpenQueue: () -> Unit,
    onImportPlaylist: () -> Unit,
    onNewPlaylist: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .width(railWidth)
            .fillMaxHeight(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            // Brand wordmark with M3 Expressive Cookie8 scalloped badge
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(ExpressiveShapes.Cookie8)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "M",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Column {
                    Text(
                        "Music0",
                        style = Musico.type.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        state.coreVersion?.let { "v$it" } ?: "Expressive",
                        style = Musico.type.caption,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Scrollable navigation drawer body so all sections stay reachable on any height
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item(key = "sec-discover") {
                    RailSectionHeader("DISCOVER")
                }
                item(key = "nav-home") {
                    RailNavItem(
                        icon = Icons.Filled.Home,
                        label = "Home",
                        active = state.overlay == AppOverlay.NONE && state.tab == AppTab.HOME,
                        onClick = { onSelectTab(AppTab.HOME) },
                    )
                }
                item(key = "nav-made-for-you") {
                    val activeMix = state.overlay == AppOverlay.COLLECTION && state.activeCollection is LibraryCollectionRef.Mix
                    RailNavItem(
                        icon = Icons.Filled.AutoAwesome,
                        label = "Made for You",
                        badge = state.smartMixes.size.takeIf { it > 0 }?.toString(),
                        active = activeMix,
                        onClick = onOpenMadeForYou,
                    )
                }
                item(key = "nav-radio") {
                    RailNavItem(
                        icon = Icons.Filled.Radio,
                        label = "Radio",
                        active = state.overlay == AppOverlay.NONE && state.tab == AppTab.RADIO,
                        onClick = { onSelectTab(AppTab.RADIO) },
                    )
                }
                item(key = "nav-search") {
                    RailNavItem(
                        icon = Icons.Filled.Search,
                        label = "Search",
                        active = state.overlay == AppOverlay.NONE && state.tab == AppTab.SEARCH,
                        onClick = { onSelectTab(AppTab.SEARCH) },
                    )
                }

                item(key = "div-library") {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Spacer(Modifier.height(10.dp))
                    RailSectionHeader("YOUR LIBRARY")
                }
                item(key = "nav-library-all") {
                    RailNavItem(
                        icon = Icons.Filled.LibraryMusic,
                        label = "Library Hub",
                        active = state.overlay == AppOverlay.NONE && state.tab == AppTab.LIBRARY && state.librarySection == LibrarySection.ALL,
                        onClick = { onOpenLibrarySection(LibrarySection.ALL) },
                    )
                }
                item(key = "nav-artists") {
                    RailNavItem(
                        icon = Icons.Filled.Person,
                        label = "Artists",
                        badge = state.libraryArtists.size.takeIf { it > 0 }?.toString(),
                        active = state.overlay == AppOverlay.NONE && state.tab == AppTab.LIBRARY && state.librarySection == LibrarySection.ARTISTS,
                        onClick = { onOpenLibrarySection(LibrarySection.ARTISTS) },
                    )
                }
                item(key = "nav-albums") {
                    RailNavItem(
                        icon = Icons.Filled.Album,
                        label = "Albums",
                        badge = state.libraryAlbums.size.takeIf { it > 0 }?.toString(),
                        active = state.overlay == AppOverlay.NONE && state.tab == AppTab.LIBRARY && state.librarySection == LibrarySection.ALBUMS,
                        onClick = { onOpenLibrarySection(LibrarySection.ALBUMS) },
                    )
                }
                item(key = "nav-recent") {
                    RailNavItem(
                        icon = Icons.Filled.History,
                        label = "Recently Played",
                        badge = state.recentlyPlayed.size.takeIf { it > 0 }?.toString(),
                        active = state.overlay == AppOverlay.NONE && state.tab == AppTab.LIBRARY && state.librarySection == LibrarySection.RECENT,
                        onClick = { onOpenLibrarySection(LibrarySection.RECENT) },
                    )
                }
                item(key = "nav-liked") {
                    RailNavItem(
                        icon = Icons.Filled.Favorite,
                        label = "Liked Songs",
                        badge = state.likedIds.size.takeIf { it > 0 }?.toString(),
                        active = state.overlay == AppOverlay.COLLECTION && state.activeCollection == LibraryCollectionRef.LikedSongs,
                        accentTint = Musico.extras.coral,
                        onClick = { onOpenCollection(LibraryCollectionRef.LikedSongs) },
                    )
                }
                item(key = "nav-downloads") {
                    RailNavItem(
                        icon = Icons.Filled.DownloadDone,
                        label = "Downloads",
                        badge = state.downloads.size.takeIf { it > 0 }?.toString(),
                        active = state.overlay == AppOverlay.COLLECTION && state.activeCollection == LibraryCollectionRef.OfflineDownloads,
                        accentTint = MaterialTheme.colorScheme.tertiary,
                        onClick = { onOpenCollection(LibraryCollectionRef.OfflineDownloads) },
                    )
                }
                item(key = "nav-queue") {
                    RailNavItem(
                        icon = Icons.Filled.QueueMusic,
                        label = "Up Next",
                        badge = state.queue.size.takeIf { it > 0 }?.toString(),
                        active = state.overlay == AppOverlay.PLAYER && state.playerInitialPage == 2,
                        onClick = onOpenQueue,
                    )
                }

                if (state.smartMixes.isNotEmpty()) {
                    item(key = "div-mixes") {
                        Spacer(Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        Spacer(Modifier.height(10.dp))
                        RailSectionHeader("SMART MIXES")
                    }
                    items(state.smartMixes.take(4), key = { "rail-mix-${it.id}" }) { mix ->
                        val active = state.overlay == AppOverlay.COLLECTION &&
                            (state.activeCollection as? LibraryCollectionRef.Mix)?.id == mix.id
                        RailNavItem(
                            icon = Icons.Filled.AutoAwesome,
                            label = mix.title,
                            badge = "${mix.tracks.size}",
                            active = active,
                            onClick = { onOpenCollection(LibraryCollectionRef.Mix(mix.id)) },
                        )
                    }
                }

                item(key = "div-playlists") {
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    Spacer(Modifier.height(8.dp))
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = onImportPlaylist, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Filled.FileDownload,
                                    contentDescription = "Import playlist",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                            IconButton(onClick = onNewPlaylist, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = "New playlist",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }

                items(state.playlists, key = { "rail-pl-${it.id}" }) { pl ->
                    val active = state.overlay == AppOverlay.COLLECTION &&
                        (state.activeCollection as? LibraryCollectionRef.Playlist)?.id == pl.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MusicoShapes.chip)
                            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
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
                                color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                formatSongCount(pl.tracks.size),
                                style = Musico.type.tabularCaption,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Spacer(Modifier.height(10.dp))

            // Footer: Profile, Quick Theme Toggle, & Settings
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
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = ExpressiveShapes.Cookie10,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp),
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
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggleTheme, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (isDark) "Switch to Light Mode" else "Switch to Dark Mode",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(onClick = onOpenSettings, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RailSectionHeader(title: String) {
    Text(
        title,
        style = Musico.type.overline,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun RailNavItem(
    icon: ImageVector,
    label: String,
    active: Boolean,
    badge: String? = null,
    accentTint: Color? = null,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = MusicoMotion.gentle(),
        label = "railBg",
    )
    val contentTint by animateColorAsState(
        targetValue = when {
            active -> MaterialTheme.colorScheme.onSecondaryContainer
            accentTint != null -> accentTint
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MusicoMotion.gentle(),
        label = "railTint",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
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
            color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (!badge.isNullOrBlank()) {
            Text(
                badge,
                style = Musico.type.tabularCaption,
                fontSize = 11.sp,
                color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Desktop Docked Native Material 3 Expressive Player Bar ────────────────

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
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val seekFraction = if (state.durationMs > 0L) {
        (smoothPosition.toFloat() / state.durationMs).coerceIn(0f, 1f)
    } else 0f
    val shownSeek = dragFraction ?: seekFraction

    val isDark = Musico.extras.isDark
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.45f else 0.7f)),
        shadowElevation = if (isDark) 8.dp else 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val barWidthDp = maxWidth.value
            val showVolumeSlider = DesktopLayoutMetrics.showInlineVolumeSlider(barWidthDp)
            val showInlineLike = DesktopLayoutMetrics.showInlineLikeButton(barWidthDp)
            val showExtras = DesktopLayoutMetrics.showSecondaryTransportExtras(barWidthDp)

            if (state.resolvingId != null) {
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .height(3.dp)
                        .clip(CircleShape)
                        .align(Alignment.TopCenter),
                )
            }

            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (showVolumeSlider) 18.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (showVolumeSlider) 16.dp else 10.dp),
            ) {
                // Left: Track Info + Like
                Row(
                    Modifier
                        .weight(if (showVolumeSlider) 0.30f else 0.34f)
                        .clip(RoundedCornerShape(16.dp))
                        .pressable(onClick = onExpandPlayer, enabled = track != null, pressedScale = 0.98f)
                        .padding(vertical = 6.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (track != null) {
                        Artwork(
                            url = track.thumbnail,
                            contentDescription = track.title,
                            modifier = Modifier.size(if (showVolumeSlider) 52.dp else 46.dp),
                            cornerRadius = 14.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title.ifEmpty { "Untitled" },
                                style = Musico.type.title,
                                fontSize = if (showVolumeSlider) 15.sp else 14.sp,
                                color = MaterialTheme.colorScheme.onSurface,
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (showInlineLike) {
                            Spacer(Modifier.width(6.dp))
                            LikeButton(
                                liked = track.videoId in state.likedIds,
                                onToggle = onLikeToggle,
                                size = 20.dp,
                            )
                        }
                    } else {
                        Text(
                            "Pick any song to listen",
                            style = Musico.type.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Center: Native M3E Morphing Play/Pause + Expressive Wavy Seek Bar
                Column(
                    Modifier.weight(if (showVolumeSlider) 0.42f else 0.46f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (showExtras) 10.dp else 6.dp),
                    ) {
                        if (showExtras) {
                            IconButton(onClick = { hapticTick(); onToggleRepeat() }, modifier = Modifier.size(30.dp)) {
                                Icon(
                                    Icons.Filled.Repeat,
                                    contentDescription = "Repeat",
                                    tint = if (state.repeatAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        IconButton(onClick = { hapticThud(); onPrevious() }, enabled = track != null, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        ExpressivePlayPauseButton(
                            isPlaying = state.isPlaying,
                            isBuffering = state.isBuffering || state.resolvingId != null,
                            enabled = track != null,
                            onClick = onTogglePlay,
                            size = 42.dp,
                        )
                        IconButton(onClick = { hapticThud(); onNext() }, enabled = track != null, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = MaterialTheme.colorScheme.onSurface)
                        }
                        if (showExtras) {
                            if (track != null) {
                                EqualizerBars(playing = state.isPlaying, color = MaterialTheme.colorScheme.primary, modifier = Modifier.height(14.dp))
                            } else {
                                Spacer(Modifier.width(16.dp))
                            }
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            formatTime(if (dragFraction == null) smoothPosition else (shownSeek * state.durationMs).toLong()),
                            style = Musico.type.tabularCaption,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        ExpressiveWavySeekBar(
                            progress = seekFraction,
                            isPlaying = state.isPlaying,
                            enabled = track != null && state.durationMs > 0L,
                            activeColor = MaterialTheme.colorScheme.primary,
                            inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            thumbColor = MaterialTheme.colorScheme.primary,
                            trackHeight = 3.5.dp,
                            waveAmplitude = 2.6.dp,
                            waveLength = 20.dp,
                            thumbHeight = 16.dp,
                            onDragProgress = { dragFraction = it },
                            onSeek = { frac ->
                                if (state.durationMs > 0L) {
                                    onSeek((frac * state.durationMs).toLong())
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp),
                        )
                        Text(
                            formatTime(state.durationMs),
                            style = Musico.type.tabularCaption,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Right: Lyrics Studio + Queue + Mute (+ spacious Volume Slider when >= 740dp)
                Row(
                    modifier = if (showVolumeSlider) Modifier.weight(0.28f) else Modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    IconButton(
                        onClick = onOpenLyrics,
                        enabled = track != null,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Filled.Lyrics,
                            contentDescription = "Open Lyrics Studio",
                            tint = if (track != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    IconButton(
                        onClick = onOpenQueue,
                        enabled = track != null,
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            Icons.Filled.QueueMusic,
                            contentDescription = "Open Queue",
                            tint = if (track != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.size(19.dp),
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
                        modifier = Modifier.size(34.dp),
                    ) {
                        Icon(
                            if (state.volume <= 0.01f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    if (showVolumeSlider) {
                        Slider(
                            value = state.volume,
                            onValueChange = onVolumeChange,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ),
                            modifier = Modifier.width(88.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Compact Native Material 3 Expressive Swipeable Mini Player ────────────

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

    val isDark = Musico.extras.isDark
    Surface(
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.45f else 0.7f)),
        shadowElevation = if (isDark) 6.dp else 3.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
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
        Column {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    url = track.thumbnail,
                    contentDescription = track.title,
                    modifier = Modifier.size(44.dp),
                    cornerRadius = 12.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title.ifEmpty { "Untitled" },
                        style = Musico.type.title,
                        color = MaterialTheme.colorScheme.onSurface,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (state.resolvingId != null || state.isBuffering) {
                    EqualizerBars(playing = false, color = MaterialTheme.colorScheme.primary, modifier = Modifier.height(16.dp))
                } else {
                    EqualizerBars(playing = state.isPlaying, color = MaterialTheme.colorScheme.primary, modifier = Modifier.height(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                ExpressivePlayPauseButton(
                    isPlaying = state.isPlaying,
                    isBuffering = state.isBuffering || state.resolvingId != null,
                    onClick = onToggle,
                    size = 42.dp,
                )
            }
            if (state.resolvingId != null) {
                LinearProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
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
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
                    )
                }
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
    val itemBounds = remember { mutableStateMapOf<AppTab, Rect>() }
    val density = LocalDensity.current
    val isDark = Musico.extras.isDark
    val barBg = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    // Authentic M3 Expressive Floating Toolbar + Detached Expressive Profile Button
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = MusicoShapes.pill,
            color = barBg,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.4f else 0.7f)),
            shadowElevation = if (isDark) 10.dp else 5.dp,
        ) {
            Box(Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                val sel = if (!profileActive) itemBounds[current] else null
                if (sel != null) {
                    val blobLeft by animateDpAsState(
                        targetValue = with(density) { sel.left.toDp() },
                        animationSpec = MusicoMotion.spatialFast(),
                        label = "blobLeft",
                    )
                    val blobRight by animateDpAsState(
                        targetValue = with(density) { sel.right.toDp() },
                        animationSpec = MusicoMotion.spatialFast(),
                        label = "blobRight",
                    )
                    val blobTop by animateDpAsState(
                        targetValue = with(density) { sel.top.toDp() },
                        animationSpec = MusicoMotion.effectsFast(),
                        label = "blobTop",
                    )
                    val blobBottom by animateDpAsState(
                        targetValue = with(density) { sel.bottom.toDp() },
                        animationSpec = MusicoMotion.effectsFast(),
                        label = "blobBottom",
                    )
                    Box(
                        Modifier
                            .offset(x = blobLeft, y = blobTop)
                            .width((blobRight - blobLeft).coerceAtLeast(0.dp))
                            .height((blobBottom - blobTop).coerceAtLeast(0.dp))
                            .clip(MusicoShapes.pill)
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
                }
            }
        }

        // Detached M3 Expressive Cookie10 Profile Button beside the floating toolbar
        Surface(
            shape = ExpressiveShapes.Cookie10,
            color = if (profileActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            shadowElevation = if (isDark) 8.dp else 4.dp,
            modifier = Modifier
                .size(54.dp)
                .clip(ExpressiveShapes.Cookie10)
                .pressable(onClick = onProfile, pressedScale = 0.90f),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    displayName.trim().take(1).uppercase().ifEmpty { "M" },
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
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
