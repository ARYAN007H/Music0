package io.github.aryan007h.musicoonline.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * The shell. Structure per the spec: big "Music0" brand + profile avatar
 * (context menu → Profile / Settings), tab content, a mini player, and the
 * Material 3 expressive pill island at the bottom with exactly three icons
 * — Home, Library, Search. No persistent top search bar; search opens as
 * its own tab from the pill. The full-screen player (with the lyric stage
 * and backdrops) is an overlay above everything.
 */
@Composable
fun MusicoApp(vm: MusicViewModel) {
    val state by vm.state.collectAsState()
    var sheetTrack by remember { mutableStateOf<io.github.aryan007h.musicoonline.core.OnlineTrack?>(null) }

    // Runtime errors are transient toasts, not pinned banners.
    androidx.compose.runtime.LaunchedEffect(state.error) {
        if (state.error != null) {
            kotlinx.coroutines.delay(4000)
            vm.dismissError()
        }
    }

    io.github.aryan007h.musicoonline.ui.theme.MusicoTheme(palette = state.palette) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
        Column(Modifier.fillMaxSize()) {
            // Update-available banner (dismissable; once per download)
            AnimatedVisibility(
                visible = state.updateInfo?.updateAvailable == true &&
                    !state.updateDismissed && state.overlay == AppOverlay.NONE,
                enter = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180)) { -it } + fadeOut(tween(160)),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MusicoShapes.chip,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .fillMaxWidth()
                        .clickable(onClick = vm::downloadAndInstallUpdate),
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Music0 ${state.updateInfo?.latest} available — tap to update",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "Dismiss",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.clickable(onClick = vm::dismissUpdateBanner),
                        )
                    }
                }
            }
            TopBar(
                displayName = state.displayName,
                coreError = state.error,
                onOpenProfile = { vm.openOverlay(AppOverlay.PROFILE) },
                onOpenSettings = { vm.openOverlay(AppOverlay.SETTINGS) },
            )

            AnimatedContent(
                targetState = state.tab,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 24 }) togetherWith
                        (fadeOut(tween(140)))
                },
                label = "tabContent",
                modifier = Modifier.weight(1f),
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

            // Mini player + pill island live above the bottom inset.
            MiniPlayer(state, onExpand = vm::openPlayer, onToggle = vm::onTogglePlayback)
            PillNavBar(
                current = state.tab,
                onSelect = vm::selectTab,
            )
        }

        // ── Overlays ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = state.overlay == AppOverlay.PLAYER,
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(280)) { it } + fadeOut(tween(180)),
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
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
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
            enter = slideInVertically(tween(260)) { it / 5 } + fadeIn(tween(200)),
            exit = fadeOut(tween(160)),
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
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
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
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(200)),
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
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
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
        }
    }
}

internal fun toCoreBool(v: Boolean) =
    kotlinx.serialization.json.JsonPrimitive(v)

// ── Top bar: brand + avatar with context menu ─────────────────────────────

@Composable
private fun TopBar(
    displayName: String,
    coreError: String?,
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
            Column(Modifier.weight(1f)) {
                Text(
                    "Music0",
                    style = Musico.type.display,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                if (coreError != null) {
                    Text(
                        coreError,
                        style = Musico.type.caption,
                        color = Musico.extras.coral,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(onClick = { menuOpen = true }),
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
    onExpand: () -> Unit,
    onToggle: () -> Unit,
) {
    val track = state.nowPlaying ?: return
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
            .clickable(onClick = onExpand),
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
                        .clip(CircleShape)
                        .clickable(onClick = onToggle),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = Color(0xFF05291D),
                            modifier = Modifier.size(26.dp),
                        )
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

// ── The M3-expressive pill island: exactly three destinations ─────────────

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
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                destinations.forEach { dest ->
                    PillItem(
                        dest = dest,
                        selected = current == dest.tab,
                        onClick = { onSelect(dest.tab) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PillItem(dest: PillDest, selected: Boolean, onClick: () -> Unit) {
    val labelWidth by animateDpAsState(
        targetValue = if (selected) 52.dp else 0.dp,
        animationSpec = MusicoMotion.expressive(),
        label = "pillLabel",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.15f else 1f,
        animationSpec = MusicoMotion.snappy(),
        label = "pillIconScale",
    )
    Surface(
        shape = MusicoShapes.chip,
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        modifier = Modifier
            .clip(MusicoShapes.chip)
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                dest.icon,
                contentDescription = dest.label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else Musico.extras.inkDim,
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
