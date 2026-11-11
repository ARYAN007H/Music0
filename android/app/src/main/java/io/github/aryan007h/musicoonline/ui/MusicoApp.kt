package io.github.aryan007h.musicoonline.ui

import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.components.EqualizerBars
import io.github.aryan007h.musicoonline.ui.home.HomeScreen
import io.github.aryan007h.musicoonline.ui.library.LibraryScreen
import io.github.aryan007h.musicoonline.ui.player.NowPlayingScreen
import io.github.aryan007h.musicoonline.ui.player.PlayerBackdrop
import io.github.aryan007h.musicoonline.ui.player.rememberSmoothPositionMs
import io.github.aryan007h.musicoonline.ui.profile.ProfileScreen
import io.github.aryan007h.musicoonline.ui.search.SearchScreen
import io.github.aryan007h.musicoonline.ui.settings.SettingsScreen
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.pressable
import io.github.aryan007h.musicoonline.ui.theme.thud
import io.github.aryan007h.musicoonline.ui.theme.tick

/**
 * The shell. Structure per the spec: big "Music0" brand + profile avatar
 * (context menu → Profile / Settings), tab content, a mini player, and the
 * Material 3 expressive pill island at the bottom — Home, Radio, Library,
 * Search. The full-screen player is an overlay that the shell RECEDES
 * behind (Apple modal style); detail pages PUSH in from the right while the
 * shell parallaxes left; settings/profile sheet up from the bottom.
 *
 * Back navigation mirrors the same stack: overlays pop first, then non-home
 * tabs fall back to Home, and only a second back on Home leaves the app.
 */
@Composable
fun MusicoApp(vm: MusicViewModel) {
    val state by vm.state.collectAsState()
    var sheetTrack by remember { mutableStateOf<io.github.aryan007h.musicoonline.core.OnlineTrack?>(null) }
    val view = LocalView.current
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    // Runtime errors are transient bottom toasts, not pinned banners. Keyed
    // on the monotonic seq, not the text: identical consecutive messages
    // (e.g. two "Added to queue") must each get their own 4 s window.
    androidx.compose.runtime.LaunchedEffect(state.errorSeq) {
        if (state.error != null) {
            kotlinx.coroutines.delay(4000)
            vm.dismissError()
        }
    }

    // ── Back navigation: the stack unwinds before the app ever dies. ─────
    // Later registrations win, so priority = bottom of this list first:
    // action sheet → overlays → tab reset → double-back exit.
    var lastBackAt by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = state.overlay == AppOverlay.NONE && state.tab == AppTab.HOME) {
        val now = SystemClock.uptimeMillis()
        if (now - lastBackAt < 2200) {
            activity?.finish()
        } else {
            lastBackAt = now
            view.tick()
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
    BackHandler(enabled = state.overlay == AppOverlay.NONE && state.tab != AppTab.HOME) {
        view.tick()
        vm.selectTab(AppTab.HOME)
    }
    BackHandler(enabled = state.overlay == AppOverlay.ONBOARD) { vm.dismissOnboarding() }
    BackHandler(enabled = state.overlay == AppOverlay.PLAYER) { vm.closeOverlay() }
    BackHandler(
        enabled = state.overlay == AppOverlay.SETTINGS || state.overlay == AppOverlay.PROFILE,
    ) { vm.closeOverlay() }
    BackHandler(enabled = state.overlay == AppOverlay.EQ) { vm.openOverlay(AppOverlay.SETTINGS) }
    BackHandler(enabled = state.overlay == AppOverlay.DETAIL) { vm.popDetail() }
    BackHandler(enabled = sheetTrack != null) { sheetTrack = null }

    io.github.aryan007h.musicoonline.ui.theme.MusicoTheme(palette = state.palette) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // ── The shell: everything behind the overlays. It RECEDES when
            //    the player opens (scale down + rounded corners + dim, the
            //    iOS modal move) and parallax-slides left when a detail
            //    page pushes in from the right. ──────────────────────────
            val playerOpen = state.overlay == AppOverlay.PLAYER
            val detailOpen = state.overlay == AppOverlay.DETAIL
            val shellScale by animateFloatAsState(
                targetValue = if (playerOpen) 0.92f else 1f,
                animationSpec = MusicoMotion.spatial(),
                label = "shellScale",
            )
            val shellRadius by animateDpAsState(
                targetValue = if (playerOpen) 34.dp else 0.dp,
                animationSpec = MusicoMotion.spatial(),
                label = "shellRadius",
            )
            val shellShift by animateDpAsState(
                targetValue = if (detailOpen) (-36).dp else 0.dp,
                animationSpec = MusicoMotion.spatial(),
                label = "shellShift",
            )
            val shellDim by animateFloatAsState(
                targetValue = if (playerOpen) 0.45f else if (detailOpen) 0.20f else 0f,
                animationSpec = tween(MusicoMotion.MED),
                label = "shellDim",
            )

            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = shellScale
                        scaleY = shellScale
                        translationX = shellShift.toPx()
                    }
                    .clip(RoundedCornerShape(shellRadius)),
            ) {
                Column(Modifier.fillMaxSize()) {
                    TopBar(
                        displayName = state.displayName,
                        onOpenProfile = { vm.openOverlay(AppOverlay.PROFILE) },
                        onOpenSettings = { vm.openOverlay(AppOverlay.SETTINGS) },
                    )

                    // Tabs glide directionally: the feed the user is leaving
                    // eases out opposite the one arriving — ColorOS-style
                    // depth without a full 3D effect.
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
                        // Fills everything under the top bar — the mini
                        // player and dock overlay it, so shelves scroll
                        // BEHIND the pills.
                        modifier = Modifier.fillMaxSize(),
                    ) { tab ->
                        when (tab) {
                            AppTab.HOME -> HomeScreen(
                                shelves = state.personalizedShelves + state.homeShelves,
                                loading = state.homeLoading,
                                error = state.homeError,
                                styleItems = state.styleItems,
                                moreLoading = state.moreLoading,
                                personalizing = state.personalizing,
                                personalizationError = state.personalizationError,
                                onOpenItem = vm::openShelfItem,
                                onRetry = vm::loadHome,
                                onLoadMore = vm::loadMoreHome,
                                currentTrackId = state.nowPlaying?.videoId,
                            )
                            AppTab.RADIO -> io.github.aryan007h.musicoonline.ui.radio.RadioScreen(
                                styles = state.radioStyles,
                                loading = state.radioLoading,
                                error = state.error,
                                onOpenStyle = { style -> vm.openShelfItem(style, emptyList()) },
                                onRetry = vm::loadRadio,
                            )
                            AppTab.LIBRARY -> LibraryScreen(
                                recentlyPlayed = state.recentlyPlayed,
                                queue = state.queue,
                                queueIndex = state.queueIndex,
                                currentTrackId = state.nowPlaying?.videoId,
                                isPlaying = state.isPlaying,
                                resolvingId = state.resolvingId,
                                likedIds = state.likedIds,
                                onPlayLiked = vm::playLiked,
                                onOpenTrack = vm::playTrack,
                                onLikeToggle = vm::toggleLike,
                                onTrackLongClick = { sheetTrack = it },
                            )
                            AppTab.SEARCH -> SearchScreen(
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
                                onTrackLongClick = { sheetTrack = it },
                            )
                        }
                    }
                }

                // Mini player + pill island FLOAT over the content (no
                // reserved band): the tab feed scrolls behind them, and the
                // screens' bottom content padding (140–150 dp) keeps the
                // last rows reachable above them. Nothing here paints a
                // background — only the pill surfaces themselves are opaque.
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                ) {
                    // The mini player rises into place with a spring the
                    // first time a track starts; it never pops in.
                    var lastTrack by remember { mutableStateOf<OnlineTrack?>(null) }
                    state.nowPlaying?.let { lastTrack = it }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.nowPlaying != null,
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
                                onExpand = vm::openPlayer,
                                onToggle = vm::onTogglePlayback,
                            )
                        }
                    }
                    PillNavBar(
                        current = state.tab,
                        onSelect = { tab -> view.tick(); vm.selectTab(tab) },
                    )
                }
            }

            // The dim veil between the receding shell and the overlay —
            // what makes the shell read as "pushed back", not "covered".
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = shellDim)),
            )

            // ── Update-available popup: floats OVER the feed (never pushes
            //    the page down), docked just below the status bar, dropped
            //    in with a springy overshoot. Tap = download; live MB
            //    progress replaces the label while downloading. ──────────
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
                    .statusBarsPadding()
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

            // ── Overlays ─────────────────────────────────────────────────
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
                    queue = state.queue,
                    queueIndex = state.queueIndex,
                    liked = state.nowPlaying?.videoId in state.likedIds,
                    onCollapse = vm::closeOverlay,
                    onTogglePlay = vm::onTogglePlayback,
                    onNext = vm::next,
                    onPrevious = vm::previous,
                    onSeek = vm::seekTo,
                    onLikeToggle = vm::toggleLikeCurrent,
                    onRepeatToggle = vm::toggleRepeat,
                    repeatAll = state.repeatAll,
                    onPlayFromQueue = vm::playFromQueue,
                )
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
                        onOfflineChange = { v -> vm.saveCoreSetting("offline_mode", toCoreBool(v)) },
                        onAutoplayChange = { v -> vm.saveCoreSetting("autoplay", toCoreBool(v)) },
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
                        io.github.aryan007h.musicoonline.ui.detail.DetailScreen(
                            entry = topDetail,
                            currentTrackId = state.nowPlaying?.videoId,
                            isPlaying = state.isPlaying,
                            likedIds = state.likedIds,
                            onBack = vm::popDetail,
                            onPlay = { vm.playDetail(topDetail) },
                            onQueueAll = { vm.queueDetail(topDetail) },
                            onSaveOffline = { vm.saveDetailOffline(topDetail) },
                            onPlayTrack = vm::playTrack,
                            onLikeToggle = vm::toggleLike,
                            onOpenItem = vm::openShelfItem,
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
                    io.github.aryan007h.musicoonline.ui.settings.EqScreen(
                        onBack = { vm.openOverlay(AppOverlay.SETTINGS) },
                    )
                }
            }
            // Long-press track action sheet (any screen).
            sheetTrack?.let { track ->
                io.github.aryan007h.musicoonline.ui.components.TrackActionSheet(
                    track = track,
                    isLiked = track.videoId in state.likedIds,
                    saving = state.savingTrackId == track.videoId,
                    onDismiss = { sheetTrack = null },
                    onPlayNext = { vm.playNext(track) },
                    onAddToQueue = { vm.enqueue(track) },
                    onSaveOffline = { vm.saveTrackOffline(track) },
                    onToggleLike = { vm.toggleLike(track) },
                )
            }
            // First-run onboarding.
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
                io.github.aryan007h.musicoonline.ui.onboarding.OnboardingScreen(
                    artistResults = state.artistResults,
                    artistSearching = state.artistSearching,
                    onFinish = vm::finishOnboarding,
                    onDismiss = vm::dismissOnboarding,
                    onArtistQuery = vm::searchArtists,
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
                        coreVersion = state.coreVersion,
                        onBack = vm::closeOverlay,
                        onRename = vm::setDisplayName,
                        onOpenSettings = { vm.openOverlay(AppOverlay.SETTINGS) },
                    )
                }
            }

            // ── Bottom toast: confirmations + errors rise from above the
            //    dock with a spring, then melt away. ─────────────────────
            val toastLift = if (state.nowPlaying != null) 152.dp else 92.dp
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
                    Text(
                        state.error.orEmpty(),
                        style = Musico.type.body,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }
}

internal fun toCoreBool(v: Boolean) =
    kotlinx.serialization.json.JsonPrimitive(v)

// ── Top bar: brand + avatar with context menu ─────────────────────────────

@Composable
private fun TopBar(
    displayName: String,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.statusBarsPadding()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Music0",
                style = Musico.type.display,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            Box {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(42.dp)
                        .pressable(onClick = { menuOpen = true }, pressedScale = 0.88f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            displayName.trim().take(1).uppercase().ifEmpty { "M" },
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    shape = MusicoShapes.card,
                ) {
                    DropdownMenuItem(
                        text = { Text("Profile") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        onClick = { menuOpen = false; onOpenProfile() },
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = { menuOpen = false; onOpenSettings() },
                    )
                }
            }
        }
    }
}

// ── Mini player: morphs the now-playing state into a tap-away pill ────────

@Composable
private fun MiniPlayer(
    state: MusicViewModel.UiState,
    track: OnlineTrack,
    onExpand: () -> Unit,
    onToggle: () -> Unit,
) {
    val view = LocalView.current
    // Frame-interpolated position, scoped here so the whole shell doesn't
    // recompose at display refresh rate — only the progress line does.
    val smoothPosition = rememberSmoothPositionMs(
        polledPositionMs = state.positionMs,
        isPlaying = state.isPlaying,
        durationMs = state.durationMs,
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MusicoShapes.chip,
        shadowElevation = 8.dp,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .pressable(onClick = onExpand, pressedScale = 0.985f),
    ) {
        Column {
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
                    EqualizerBars(playing = false, modifier = Modifier.height(16.dp))
                } else {
                    EqualizerBars(playing = state.isPlaying, modifier = Modifier.height(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Surface(
                    shape = CircleShape,
                    color = Musico.extras.mint,
                    modifier = Modifier
                        .size(42.dp)
                        .pressable(onClick = { view.thud(); onToggle() }, pressedScale = 0.86f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Play ⇄ pause as a pop-morph, not a hard swap.
                        AnimatedContent(
                            targetState = state.isPlaying,
                            transitionSpec = {
                                (androidx.compose.animation.scaleIn(
                                    initialScale = 0.55f,
                                    animationSpec = MusicoMotion.snappy(),
                                ) + fadeIn(tween(MusicoMotion.FAST))) togetherWith
                                    (androidx.compose.animation.scaleOut(
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
            // Thin live progress line hugging the pill's bottom.
            val fraction = if (state.durationMs > 0) {
                (smoothPosition.toFloat() / state.durationMs).coerceIn(0f, 1f)
            } else 0f
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .background(Musico.extras.mint, RoundedCornerShape(2.dp)),
            )
        }
    }
}

// ── The M3-expressive pill island: a sliding selection blob ───────────────

private data class PillDest(
    val tab: AppTab,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
)

@Composable
private fun PillNavBar(current: AppTab, onSelect: (AppTab) -> Unit) {
    val destinations = remember {
        listOf(
            PillDest(AppTab.HOME, Icons.Filled.Home, "Home"),
            PillDest(AppTab.RADIO, Icons.Filled.Radio, "Radio"),
            PillDest(AppTab.LIBRARY, Icons.Filled.LibraryMusic, "Library"),
            PillDest(AppTab.SEARCH, Icons.Filled.Search, "Search"),
        )
    }
    // Live bounds of every item, reported from layout. The blob's leading
    // and trailing edges each spring to their target — the stretch between
    // them is what makes the selection read as fluid, not teleported.
    val itemBounds = remember { mutableStateMapOf<AppTab, Rect>() }
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
                val sel = itemBounds[current]
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
                            selected = current == dest.tab,
                            onClick = { onSelect(dest.tab) },
                            onBounds = { rect -> itemBounds[dest.tab] = rect },
                        )
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
                    .scale(iconScale),
            )
            AnimatedVisibility(visible = selected) {
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.width(labelWidth)) {
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
