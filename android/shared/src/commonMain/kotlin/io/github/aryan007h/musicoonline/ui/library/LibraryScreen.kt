package io.github.aryan007h.musicoonline.ui.library

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.CatalogEntry
import io.github.aryan007h.musicoonline.core.DownloadedTrack
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.SmartMix
import io.github.aryan007h.musicoonline.core.UserPlaylist
import io.github.aryan007h.musicoonline.platform.PlatformHorizontalScrollbar
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.components.DesktopScrollableChipRow
import io.github.aryan007h.musicoonline.ui.components.EmptyState
import io.github.aryan007h.musicoonline.ui.components.ExpressiveFilterChip
import io.github.aryan007h.musicoonline.ui.components.HorizontalScrollMath
import io.github.aryan007h.musicoonline.ui.components.ShelfScrollButtons
import io.github.aryan007h.musicoonline.ui.components.TrackRow
import io.github.aryan007h.musicoonline.ui.components.desktopHorizontalScroll
import io.github.aryan007h.musicoonline.ui.components.musicoTopInset
import kotlinx.coroutines.launch
import io.github.aryan007h.musicoonline.ui.LibrarySection
import io.github.aryan007h.musicoonline.ui.player.ArtworkAtmosphere
import io.github.aryan007h.musicoonline.ui.theme.ExpressiveShapes
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.breathing
import io.github.aryan007h.musicoonline.ui.theme.hapticThud
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import io.github.aryan007h.musicoonline.ui.theme.pressable
import io.github.aryan007h.musicoonline.ui.theme.staggerIn

/**
 * Full on-device Library Hub (shared across Android & Desktop):
 * - AmbientTopWash radiating the active cover's color halo
 * - Pinned Liked Songs & Offline Downloads hero cards
 * - Auto-updating Rust Smart Mixes carousel
 * - User Playlists with 2×2 artwork mosaics & instant creation
 * - Saved Albums & Followed Artists
 * - Recently Played & Session Queue
 */
@Composable
fun LibraryScreen(
    atmosphere: ArtworkAtmosphere,
    playlists: List<UserPlaylist>,
    likedTracks: List<OnlineTrack>,
    downloads: List<DownloadedTrack>,
    downloadsTotalBytes: Long,
    savedAlbums: List<CatalogEntry>,
    savedArtists: List<CatalogEntry>,
    smartMixes: List<SmartMix>,
    recentlyPlayed: List<OnlineTrack>,
    queue: List<OnlineTrack>,
    queueIndex: Int,
    currentTrackId: String?,
    isPlaying: Boolean,
    resolvingId: String?,
    likedIds: Set<String>,
    onOpenCollection: (LibraryCollectionRef) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onPlayTracks: (List<OnlineTrack>, Int) -> Unit,
    onOpenCatalogEntry: (CatalogEntry) -> Unit,
    onOpenTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onLikeToggle: (OnlineTrack) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit = {},
    selectedSection: LibrarySection = LibrarySection.ALL,
    onSelectSection: (LibrarySection) -> Unit = {},
) {
    var localFilter by remember(selectedSection) { mutableStateOf(selectedSection) }
    val filter = localFilter
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .musicoTopInset(),
            contentPadding = PaddingValues(bottom = 148.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "lib-header") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 22.dp, end = 20.dp, top = 12.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Library",
                        style = Musico.type.display.copy(fontSize = 32.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Surface(
                        shape = MusicoShapes.chip,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.pressable(
                            onClick = { hapticTick(); showCreateDialog = true },
                            pressedScale = 0.93f,
                        ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "New",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            item(key = "lib-filters") {
                DesktopScrollableChipRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    edgeFadeColor = MaterialTheme.colorScheme.background,
                ) {
                    LibrarySection.entries.forEach { f ->
                        ExpressiveFilterChip(
                            label = f.label,
                            selected = filter == f,
                            usePrimaryWhenSelected = false,
                            onClick = {
                                localFilter = f
                                onSelectSection(f)
                            },
                        )
                    }
                }
            }

            if (filter == LibrarySection.ALL || filter == LibrarySection.LIKED || filter == LibrarySection.DOWNLOADS) {
                item(key = "pinned-heroes") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (filter != LibrarySection.DOWNLOADS) {
                            PinnedHeroTile(
                                title = "Liked Songs",
                                meta = formatSongCount(likedIds.size),
                                icon = Icons.Filled.Favorite,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                canPlay = likedTracks.isNotEmpty(),
                                onOpen = { onOpenCollection(LibraryCollectionRef.LikedSongs) },
                                onQuickPlay = { onPlayTracks(likedTracks, 0) },
                                modifier = Modifier
                                    .weight(1f)
                                    .staggerIn(index = 0),
                            )
                        }
                        if (filter != LibrarySection.LIKED) {
                            PinnedHeroTile(
                                title = "Downloads",
                                meta = formatDownloadsSummary(downloads.size, downloadsTotalBytes),
                                icon = Icons.Filled.DownloadDone,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                canPlay = downloads.isNotEmpty(),
                                onOpen = { onOpenCollection(LibraryCollectionRef.OfflineDownloads) },
                                onQuickPlay = { onPlayTracks(downloads.map { it.track }, 0) },
                                modifier = Modifier
                                    .weight(1f)
                                    .staggerIn(index = 1),
                            )
                        }
                    }
                }
            }

            if (filter == LibrarySection.ALL && smartMixes.isNotEmpty()) {
                item(key = "smart-mixes-section") {
                    val mixState = rememberLazyListState()
                    val mixScope = rememberCoroutineScope()
                    var mixViewportPx by remember { mutableFloatStateOf(0f) }
                    Column {
                        SectionLabel(
                            title = "Smart mixes",
                            trailing = {
                                ShelfScrollButtons(
                                    canScrollBackward = mixState.canScrollBackward,
                                    canScrollForward = mixState.canScrollForward,
                                    onScrollBackward = {
                                        val step = HorizontalScrollMath.computePageStepPx(mixViewportPx)
                                        mixScope.launch {
                                            mixState.animateScrollBy(-step, tween(320, easing = MusicoMotion.emphasized))
                                        }
                                    },
                                    onScrollForward = {
                                        val step = HorizontalScrollMath.computePageStepPx(mixViewportPx)
                                        mixScope.launch {
                                            mixState.animateScrollBy(step, tween(320, easing = MusicoMotion.emphasized))
                                        }
                                    },
                                )
                            },
                        )
                        LazyRow(
                            state = mixState,
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .onSizeChanged { mixViewportPx = it.width.toFloat() }
                                .desktopHorizontalScroll(mixState),
                        ) {
                            items(smartMixes, key = { it.id }) { mix ->
                                SmartMixCard(
                                    mix = mix,
                                    onClick = { onOpenCollection(LibraryCollectionRef.Mix(mix.id)) },
                                )
                            }
                        }
                        PlatformHorizontalScrollbar(
                            state = mixState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 20.dp, end = 20.dp, top = 8.dp),
                        )
                    }
                }
            }

            if (filter == LibrarySection.ALL || filter == LibrarySection.PLAYLISTS) {
                item(key = "playlists-header") {
                    SectionLabel("Your playlists")
                }
                if (playlists.isEmpty()) {
                    item(key = "playlists-empty-cta") {
                        val isDark = Musico.extras.isDark
                        Surface(
                            shape = MusicoShapes.card,
                            color = if (isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = if (!isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .pressable(onClick = { hapticTick(); showCreateDialog = true }, pressedScale = 0.98f),
                        ) {
                            Row(
                                Modifier.padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Create your first playlist",
                                        style = Musico.type.title,
                                        color = MaterialTheme.colorScheme.onBackground,
                                    )
                                    Text(
                                        "Collect songs, reorder them, and save them offline",
                                        style = Musico.type.caption,
                                        color = Musico.extras.inkDim,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(playlists, key = { "pl-${it.id}" }) { pl ->
                        val plTracks = remember(pl.tracks) { pl.tracks.map { it.toOnlineTrack() } }
                        val meta = remember(plTracks) {
                            buildList {
                                add("${plTracks.size} song${if (plTracks.size == 1) "" else "s"}")
                                formatDurationSummary(plTracks)?.let { add(it) }
                            }.joinToString(" · ")
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .pressable(
                                    onClick = { onOpenCollection(LibraryCollectionRef.Playlist(pl.id)) },
                                    pressedScale = 0.98f,
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlaylistMosaicArtwork(
                                thumbnails = pl.tracks.map { it.thumbnail },
                                modifier = Modifier.size(62.dp),
                                cornerRadius = 16.dp,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    pl.name,
                                    style = Musico.type.title,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    meta,
                                    style = Musico.type.tabularCaption,
                                    color = Musico.extras.inkDim,
                                )
                            }
                            if (plTracks.isNotEmpty()) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .size(38.dp)
                                        .pressable(
                                            onClick = { hapticThud(); onPlayTracks(plTracks, 0) },
                                            pressedScale = 0.88f,
                                        ),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = "Play ${pl.name}",
                                            tint = Musico.extras.mint,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (filter == LibrarySection.LIKED && likedTracks.isNotEmpty()) {
                item(key = "liked-list-header") { SectionLabel("Liked tracks") }
                itemsIndexed(likedTracks, key = { i, t -> "lk-${t.videoId}-$i" }) { _, track ->
                    Box(Modifier.padding(horizontal = 14.dp)) {
                        TrackRow(
                            track = track,
                            index = null,
                            isCurrent = track.videoId == currentTrackId,
                            isPlaying = isPlaying,
                            isLiked = track.videoId in likedIds,
                            resolving = resolvingId == track.videoId,
                            onClick = { onOpenTrack(track, likedTracks) },
                            onLikeToggle = { onLikeToggle(track) },
                            onLongClick = { onTrackLongClick(track) },
                        )
                    }
                }
            }
            if (filter == LibrarySection.DOWNLOADS) {
                if (downloads.isNotEmpty()) {
                    item(key = "dl-list-header") { SectionLabel("On this device") }
                    val dlTracks = downloads.map { it.track }
                    itemsIndexed(downloads, key = { i, d -> "dl-${d.track.videoId}-$i" }) { _, dl ->
                        val tagged = dl.track.copy(
                            artist = "${dl.track.artist.ifEmpty { "Offline track" }} · ${formatBytesHuman(dl.sizeBytes)}",
                        )
                        Box(Modifier.padding(horizontal = 14.dp)) {
                            TrackRow(
                                track = tagged,
                                index = null,
                                isCurrent = dl.track.videoId == currentTrackId,
                                isPlaying = isPlaying,
                                isLiked = dl.track.videoId in likedIds,
                                resolving = resolvingId == dl.track.videoId,
                                onClick = { onOpenTrack(dl.track, dlTracks) },
                                onLikeToggle = { onLikeToggle(dl.track) },
                                onLongClick = { onTrackLongClick(dl.track) },
                            )
                        }
                    }
                } else {
                    item(key = "dl-empty") {
                        EmptyState(
                            icon = Icons.Filled.DownloadDone,
                            title = "No offline tracks yet",
                            subtitle = "Long-press any song or tap Offline inside a playlist to cache it for Airplane Mode",
                        )
                    }
                }
            }

            if ((filter == LibrarySection.ALL && savedAlbums.isNotEmpty()) || filter == LibrarySection.ALBUMS) {
                if (savedAlbums.isEmpty()) {
                    item(key = "albums-header") { SectionLabel("Saved albums") }
                    item(key = "albums-empty") {
                        EmptyState(
                            icon = Icons.Filled.Album,
                            title = "No saved albums",
                            subtitle = "Open any album and tap Save to keep it here",
                        )
                    }
                } else {
                    item(key = "albums-section") {
                        val albumsState = rememberLazyListState()
                        val albumsScope = rememberCoroutineScope()
                        var albumsViewportPx by remember { mutableFloatStateOf(0f) }
                        Column {
                            SectionLabel(
                                title = "Saved albums",
                                trailing = {
                                    ShelfScrollButtons(
                                        canScrollBackward = albumsState.canScrollBackward,
                                        canScrollForward = albumsState.canScrollForward,
                                        onScrollBackward = {
                                            val step = HorizontalScrollMath.computePageStepPx(albumsViewportPx)
                                            albumsScope.launch {
                                                albumsState.animateScrollBy(-step, tween(320, easing = MusicoMotion.emphasized))
                                            }
                                        },
                                        onScrollForward = {
                                            val step = HorizontalScrollMath.computePageStepPx(albumsViewportPx)
                                            albumsScope.launch {
                                                albumsState.animateScrollBy(step, tween(320, easing = MusicoMotion.emphasized))
                                            }
                                        },
                                    )
                                },
                            )
                            LazyRow(
                                state = albumsState,
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { albumsViewportPx = it.width.toFloat() }
                                    .desktopHorizontalScroll(albumsState),
                            ) {
                                items(savedAlbums, key = { "alb-${it.id}" }) { entry ->
                                    CatalogEntryCard(
                                        entry = entry,
                                        circular = false,
                                        onClick = { onOpenCatalogEntry(entry) },
                                    )
                                }
                            }
                            PlatformHorizontalScrollbar(
                                state = albumsState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 8.dp),
                            )
                        }
                    }
                }
            }

            if ((filter == LibrarySection.ALL && savedArtists.isNotEmpty()) || filter == LibrarySection.ARTISTS) {
                if (savedArtists.isEmpty()) {
                    item(key = "artists-header") { SectionLabel("Followed artists") }
                    item(key = "artists-empty") {
                        EmptyState(
                            icon = Icons.Filled.Person,
                            title = "No followed artists",
                            subtitle = "Open an artist page and tap Follow to keep them close",
                        )
                    }
                } else {
                    item(key = "artists-section") {
                        val artistsState = rememberLazyListState()
                        val artistsScope = rememberCoroutineScope()
                        var artistsViewportPx by remember { mutableFloatStateOf(0f) }
                        Column {
                            SectionLabel(
                                title = "Followed artists",
                                trailing = {
                                    ShelfScrollButtons(
                                        canScrollBackward = artistsState.canScrollBackward,
                                        canScrollForward = artistsState.canScrollForward,
                                        onScrollBackward = {
                                            val step = HorizontalScrollMath.computePageStepPx(artistsViewportPx)
                                            artistsScope.launch {
                                                artistsState.animateScrollBy(-step, tween(320, easing = MusicoMotion.emphasized))
                                            }
                                        },
                                        onScrollForward = {
                                            val step = HorizontalScrollMath.computePageStepPx(artistsViewportPx)
                                            artistsScope.launch {
                                                artistsState.animateScrollBy(step, tween(320, easing = MusicoMotion.emphasized))
                                            }
                                        },
                                    )
                                },
                            )
                            LazyRow(
                                state = artistsState,
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onSizeChanged { artistsViewportPx = it.width.toFloat() }
                                    .desktopHorizontalScroll(artistsState),
                            ) {
                                itemsIndexed(savedArtists, key = { _, it -> "art-${it.id}" }) { idx, entry ->
                                    CatalogEntryCard(
                                        entry = entry,
                                        circular = true,
                                        artistIndex = idx,
                                        onClick = { onOpenCatalogEntry(entry) },
                                    )
                                }
                            }
                            PlatformHorizontalScrollbar(
                                state = artistsState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 8.dp),
                            )
                        }
                    }
                }
            }

            if (filter == LibrarySection.ALL || filter == LibrarySection.RECENT) {
                if (recentlyPlayed.isNotEmpty()) {
                    item(key = "recents-header") { SectionLabel("Recently played") }
                    itemsIndexed(recentlyPlayed.take(if (filter == LibrarySection.RECENT) 40 else 16), key = { i, t -> "rec-${t.videoId}-$i" }) { _, track ->
                        Box(Modifier.padding(horizontal = 14.dp)) {
                            TrackRow(
                                track = track,
                                index = null,
                                isCurrent = track.videoId == currentTrackId,
                                isPlaying = isPlaying,
                                isLiked = track.videoId in likedIds,
                                resolving = resolvingId == track.videoId,
                                onClick = { onOpenTrack(track, recentlyPlayed) },
                                onLikeToggle = { onLikeToggle(track) },
                                onLongClick = { onTrackLongClick(track) },
                                modifier = Modifier.animateItem(placementSpec = MusicoMotion.spatial()),
                            )
                        }
                    }
                } else if (filter == LibrarySection.RECENT) {
                    item(key = "recents-empty") {
                        EmptyState(
                            icon = Icons.Filled.History,
                            title = "Nothing played yet",
                            subtitle = "Songs you listen to will appear here for instant replay",
                        )
                    }
                }
                if (filter == LibrarySection.ALL && queue.isNotEmpty()) {
                    item(key = "queue-header") { SectionLabel("Session queue") }
                    itemsIndexed(queue, key = { i, t -> "q-${t.videoId}-$i" }) { i, track ->
                        Box(Modifier.padding(horizontal = 14.dp)) {
                            TrackRow(
                                track = track,
                                index = i + 1,
                                isCurrent = i == queueIndex || track.videoId == currentTrackId,
                                isPlaying = isPlaying,
                                isLiked = track.videoId in likedIds,
                                resolving = resolvingId == track.videoId,
                                onClick = { onOpenTrack(track, queue) },
                                onLikeToggle = { onLikeToggle(track) },
                                onLongClick = { onTrackLongClick(track) },
                                modifier = Modifier.animateItem(placementSpec = MusicoMotion.spatial()),
                            )
                        }
                    }
                }
                if (filter == LibrarySection.ALL && recentlyPlayed.isEmpty() && queue.isEmpty() && playlists.isEmpty() && likedTracks.isEmpty() && downloads.isEmpty()) {
                    item(key = "lib-all-empty") {
                        EmptyState(
                            icon = Icons.Filled.History,
                            title = "Your music lives here",
                            subtitle = "Play songs from Home, like favorites, or create a playlist above",
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateOrRenamePlaylistDialog(
            title = "New playlist",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name -> onCreatePlaylist(name) },
        )
    }
}

@Composable
private fun PinnedHeroTile(
    title: String,
    meta: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    canPlay: Boolean,
    onOpen: () -> Unit,
    onQuickPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(142.dp)
            .clip(MusicoShapes.card)
            .background(containerColor)
            .pressable(onClick = onOpen, pressedScale = 0.96f)
            .padding(16.dp),
    ) {
        Column(Modifier.align(Alignment.TopStart)) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier
                    .size(26.dp)
                    .breathing(max = 1.07f),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                title,
                style = Musico.type.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                meta,
                style = Musico.type.tabularCaption,
                color = contentColor.copy(alpha = 0.82f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canPlay) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .pressable(
                        onClick = { hapticThud(); onQuickPlay() },
                        pressedScale = 0.86f,
                    ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Play $title",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SmartMixCard(
    mix: SmartMix,
    onClick: () -> Unit,
) {
    val isDark = Musico.extras.isDark
    Surface(
        shape = MusicoShapes.card,
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (!isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) else null,
        modifier = Modifier
            .width(164.dp)
            .pressable(onClick = onClick, pressedScale = 0.96f),
    ) {
        Column {
            PlaylistMosaicArtwork(
                thumbnails = mix.tracks.map { it.thumbnail },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                cornerRadius = 0.dp,
            )
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    mix.title,
                    style = Musico.type.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatTrackCount(mix.tracks.size)} · ${mix.subtitle}",
                    style = Musico.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun CatalogEntryCard(
    entry: CatalogEntry,
    circular: Boolean,
    artistIndex: Int = 0,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(126.dp)
            .pressable(onClick = onClick, pressedScale = 0.95f),
        horizontalAlignment = if (circular) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Artwork(
            url = entry.thumbnail,
            contentDescription = entry.title,
            modifier = Modifier.size(126.dp),
            cornerRadius = if (circular) 63.dp else 20.dp,
            shape = if (circular) ExpressiveShapes.artistFrame(artistIndex) else null,
            placeholderIcon = if (circular) Icons.Filled.Person else Icons.Filled.Album,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            entry.title,
            style = Musico.type.title,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (entry.subtitle.isNotBlank()) {
            Text(
                entry.subtitle,
                style = Musico.type.caption,
                color = Musico.extras.inkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionLabel(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 6.dp, start = 22.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            title,
            style = Musico.type.section,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}
