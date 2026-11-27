package io.github.aryan007h.musicoonline.ui.library

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Download
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.CatalogEntry
import io.github.aryan007h.musicoonline.core.DownloadedTrack
import io.github.aryan007h.musicoonline.core.ItemKind
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.Shelf
import io.github.aryan007h.musicoonline.core.SmartMix
import io.github.aryan007h.musicoonline.core.UserPlaylist
import io.github.aryan007h.musicoonline.platform.PlatformHorizontalScrollbar
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.components.ConnectedButtonOption
import io.github.aryan007h.musicoonline.ui.components.DesktopScrollableChipRow
import io.github.aryan007h.musicoonline.ui.components.EmptyState
import io.github.aryan007h.musicoonline.ui.components.ExpressiveConnectedButtonGroup
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

enum class DownloadsTab(val label: String) {
    DOWNLOADED("Downloaded"),
    CACHED("Cached"),
}

data class PartitionedDownloads(
    val userDownloaded: List<DownloadedTrack>,
    val cachedOnly: List<DownloadedTrack>,
    val userDownloadedBytes: Long,
    val cachedOnlyBytes: Long,
)

/**
 * Pure helper splitting local audio files between explicitly user-downloaded tracks
 * and automatically stream-cached tracks.
 */
object DownloadsPartition {
    fun partition(
        downloads: List<DownloadedTrack>,
        userDownloadedIds: Set<String>,
    ): PartitionedDownloads {
        val userList = ArrayList<DownloadedTrack>()
        val cachedList = ArrayList<DownloadedTrack>()
        var userBytes = 0L
        var cachedBytes = 0L
        for (item in downloads) {
            if (item.track.videoId in userDownloadedIds) {
                userList += item
                userBytes += item.sizeBytes.coerceAtLeast(0L)
            } else {
                cachedList += item
                cachedBytes += item.sizeBytes.coerceAtLeast(0L)
            }
        }
        return PartitionedDownloads(
            userDownloaded = userList,
            cachedOnly = cachedList,
            userDownloadedBytes = userBytes,
            cachedOnlyBytes = cachedBytes,
        )
    }
}

/**
 * Pure recommendation curator powering Library Artists & Albums pages so users always
 * see relevant artists & albums below their followed/saved collections.
 */
object LibraryRecommendationCurator {
    private val NON_ARTIST_LABELS = setOf(
        "unknown artist", "offline track", "various artists", "song", "video", "music", "single",
    )

    fun recommendArtists(
        savedArtists: List<CatalogEntry>,
        homeShelves: List<Shelf>,
        topArtistNames: List<String>,
        likedTracks: List<OnlineTrack>,
        recentlyPlayed: List<OnlineTrack>,
        smartMixes: List<SmartMix>,
        maxItems: Int = 18,
    ): List<CatalogEntry> {
        if (maxItems <= 0) return emptyList()
        val excludedNames = savedArtists.map { it.title.trim().lowercase() }.toMutableSet()
        val excludedIds = savedArtists.mapNotNull { it.browseId?.takeIf { b -> b.isNotBlank() } ?: it.id.takeIf { id -> id.isNotBlank() } }.toMutableSet()
        val out = ArrayList<CatalogEntry>()

        fun tryAdd(entry: CatalogEntry) {
            if (out.size >= maxItems) return
            val nameKey = entry.title.trim().lowercase()
            if (nameKey.length < 2 || nameKey in NON_ARTIST_LABELS) return
            if (nameKey in excludedNames) return
            val bid = entry.browseId?.takeIf { it.isNotBlank() }
            if (bid != null && bid in excludedIds) return
            excludedNames += nameKey
            if (bid != null) excludedIds += bid
            out += entry
        }

        // 1. Real catalog artist cards from Home shelves (carry real browseIds + photos)
        for (shelf in homeShelves) {
            val shelfHintsArtist = shelf.title.contains("artist", ignoreCase = true)
            for (item in shelf.items) {
                if (item.kind == ItemKind.ARTIST || (shelfHintsArtist && !item.browseId.isNullOrBlank())) {
                    val cleanName = item.title.trim()
                    if (cleanName.isNotBlank()) {
                        tryAdd(
                            CatalogEntry(
                                id = item.browseId ?: "rec-artist-${cleanName.lowercase()}",
                                kind = "artist",
                                title = cleanName,
                                subtitle = item.subtitle.ifBlank { "Recommended artist" },
                                thumbnail = item.thumbnail,
                                browseId = item.browseId,
                                params = item.params,
                            ),
                        )
                    }
                }
            }
        }

        val allTracks = likedTracks + recentlyPlayed + smartMixes.flatMap { it.tracks }
        val trackByArtistLower = HashMap<String, OnlineTrack>()
        for (t in allTracks) {
            val primary = extractPrimaryArtist(t.artist) ?: continue
            val key = primary.lowercase()
            val prev = trackByArtistLower[key]
            if (prev == null || (prev.thumbnail.isNullOrBlank() && !t.thumbnail.isNullOrBlank()) || (prev.artistBrowseId.isNullOrBlank() && !t.artistBrowseId.isNullOrBlank())) {
                trackByArtistLower[key] = t
            }
        }

        // 2. Top listened artists from listening stats
        for (artist in topArtistNames) {
            val primary = extractPrimaryArtist(artist) ?: continue
            val match = trackByArtistLower[primary.lowercase()]
            tryAdd(
                CatalogEntry(
                    id = match?.artistBrowseId ?: "rec-artist-${primary.lowercase()}",
                    kind = "artist",
                    title = primary,
                    subtitle = "Top in your listening",
                    thumbnail = match?.thumbnail,
                    browseId = match?.artistBrowseId,
                ),
            )
        }

        // 3. Artists from liked songs, recent plays & smart mixes
        for (t in allTracks) {
            val primary = extractPrimaryArtist(t.artist) ?: continue
            tryAdd(
                CatalogEntry(
                    id = t.artistBrowseId ?: "rec-artist-${primary.lowercase()}",
                    kind = "artist",
                    title = primary,
                    subtitle = "Based on your taste",
                    thumbnail = t.thumbnail,
                    browseId = t.artistBrowseId,
                ),
            )
        }

        // 4. Fallback artists inferred from Home shelf track subtitles
        for (shelf in homeShelves) {
            for (item in shelf.items) {
                if (item.videoId == null) continue
                val primary = extractPrimaryArtist(item.subtitle) ?: continue
                tryAdd(
                    CatalogEntry(
                        id = "rec-artist-${primary.lowercase()}",
                        kind = "artist",
                        title = primary,
                        subtitle = "Discover on Music0",
                        thumbnail = item.thumbnail,
                    ),
                )
            }
        }

        return out
    }

    fun recommendAlbums(
        savedAlbums: List<CatalogEntry>,
        homeShelves: List<Shelf>,
        likedTracks: List<OnlineTrack>,
        recentlyPlayed: List<OnlineTrack>,
        smartMixes: List<SmartMix>,
        maxItems: Int = 18,
    ): List<CatalogEntry> {
        if (maxItems <= 0) return emptyList()
        val excludedNames = savedAlbums.map { it.title.trim().lowercase() }.toMutableSet()
        val excludedIds = savedAlbums.mapNotNull { it.browseId?.takeIf { b -> b.isNotBlank() } ?: it.id.takeIf { id -> id.isNotBlank() } }.toMutableSet()
        val out = ArrayList<CatalogEntry>()

        fun tryAdd(entry: CatalogEntry) {
            if (out.size >= maxItems) return
            val nameKey = entry.title.trim().lowercase()
            if (nameKey.length < 2) return
            if (nameKey in excludedNames) return
            val bid = entry.browseId?.takeIf { it.isNotBlank() }
            if (bid != null && bid in excludedIds) return
            excludedNames += nameKey
            if (bid != null) excludedIds += bid
            out += entry
        }

        // 1. Real catalog albums from Home shelves (carry real album browseIds + artwork)
        for (shelf in homeShelves) {
            val shelfHintsAlbum = shelf.title.contains("album", ignoreCase = true) ||
                shelf.title.contains("release", ignoreCase = true) ||
                shelf.title.contains("single", ignoreCase = true)
            for (item in shelf.items) {
                if (item.kind == ItemKind.ALBUM || (shelfHintsAlbum && !item.browseId.isNullOrBlank() && item.kind != ItemKind.ARTIST)) {
                    val cleanTitle = item.title.trim()
                    if (cleanTitle.isNotBlank()) {
                        tryAdd(
                            CatalogEntry(
                                id = item.browseId ?: "rec-album-${cleanTitle.lowercase()}",
                                kind = "album",
                                title = cleanTitle,
                                subtitle = item.subtitle.ifBlank { "Recommended album" },
                                thumbnail = item.thumbnail,
                                browseId = item.browseId,
                                params = item.params,
                            ),
                        )
                    }
                }
            }
        }

        // 2. Albums from user's liked songs, recent plays & smart mixes
        val allTracks = likedTracks + recentlyPlayed + smartMixes.flatMap { it.tracks }
        for (t in allTracks) {
            val albumName = t.album.trim()
            if (albumName.length < 2) continue
            tryAdd(
                CatalogEntry(
                    id = "rec-album-${albumName.lowercase()}",
                    kind = "album",
                    title = albumName,
                    subtitle = t.artist.ifBlank { "From your library" },
                    thumbnail = t.thumbnail,
                ),
            )
        }

        // 3. Fallback featured collections/playlists with browseId from Home shelves so Albums tab is never empty
        for (shelf in homeShelves) {
            for (item in shelf.items) {
                if (!item.browseId.isNullOrBlank() && item.kind != ItemKind.ARTIST && item.title.isNotBlank()) {
                    tryAdd(
                        CatalogEntry(
                            id = item.browseId,
                            kind = "album",
                            title = item.title.trim(),
                            subtitle = item.subtitle.ifBlank { shelf.title.ifBlank { "Recommended" } },
                            thumbnail = item.thumbnail,
                            browseId = item.browseId,
                            params = item.params,
                        ),
                    )
                }
            }
        }

        return out
    }

    private fun extractPrimaryArtist(raw: String): String? {
        val cleaned = raw
            .substringBefore(" · ")
            .substringBefore(" • ")
            .substringBefore(",")
            .substringBefore("&")
            .substringBefore(" feat.")
            .substringBefore(" ft.")
            .trim()
        if (cleaned.length < 2) return null
        if (cleaned.lowercase() in NON_ARTIST_LABELS) return null
        if (cleaned.all { it.isDigit() || it == ':' || it == ' ' }) return null
        return cleaned
    }
}

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
    userDownloadedIds: Set<String> = emptySet(),
    savedAlbums: List<CatalogEntry>,
    savedArtists: List<CatalogEntry>,
    homeShelves: List<Shelf> = emptyList(),
    topArtistNames: List<String> = emptyList(),
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
    onToggleSaveCatalogEntry: (CatalogEntry) -> Unit = {},
    onPromoteCachedToDownloaded: (List<OnlineTrack>) -> Unit = {},
    onOpenTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onLikeToggle: (OnlineTrack) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit = {},
    selectedSection: LibrarySection = LibrarySection.ALL,
    onSelectSection: (LibrarySection) -> Unit = {},
) {
    var localFilter by remember(selectedSection) { mutableStateOf(selectedSection) }
    val filter = localFilter
    var downloadsTab by remember { mutableStateOf(DownloadsTab.DOWNLOADED) }
    var showCreateDialog by remember { mutableStateOf(false) }

    val partitionedDownloads = remember(downloads, userDownloadedIds) {
        DownloadsPartition.partition(downloads, userDownloadedIds)
    }
    val recommendedArtists = remember(savedArtists, homeShelves, topArtistNames, likedTracks, recentlyPlayed, smartMixes) {
        LibraryRecommendationCurator.recommendArtists(
            savedArtists = savedArtists,
            homeShelves = homeShelves,
            topArtistNames = topArtistNames,
            likedTracks = likedTracks,
            recentlyPlayed = recentlyPlayed,
            smartMixes = smartMixes,
        )
    }
    val recommendedAlbums = remember(savedAlbums, homeShelves, likedTracks, recentlyPlayed, smartMixes) {
        LibraryRecommendationCurator.recommendAlbums(
            savedAlbums = savedAlbums,
            homeShelves = homeShelves,
            likedTracks = likedTracks,
            recentlyPlayed = recentlyPlayed,
            smartMixes = smartMixes,
        )
    }

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
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.34f else 0.45f)),
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
                item(key = "dl-tab-bar") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                    ) {
                        ExpressiveConnectedButtonGroup(
                            options = listOf(
                                ConnectedButtonOption(
                                    DownloadsTab.DOWNLOADED,
                                    "Downloaded (${partitionedDownloads.userDownloaded.size})",
                                    Icons.Filled.DownloadDone,
                                ),
                                ConnectedButtonOption(
                                    DownloadsTab.CACHED,
                                    "Cached (${partitionedDownloads.cachedOnly.size})",
                                    Icons.Filled.Cached,
                                ),
                            ),
                            selected = downloadsTab,
                            onSelect = { downloadsTab = it },
                            height = 44.dp,
                        )
                    }
                }
                val activeList = if (downloadsTab == DownloadsTab.DOWNLOADED) {
                    partitionedDownloads.userDownloaded
                } else {
                    partitionedDownloads.cachedOnly
                }
                val activeBytes = if (downloadsTab == DownloadsTab.DOWNLOADED) {
                    partitionedDownloads.userDownloadedBytes
                } else {
                    partitionedDownloads.cachedOnlyBytes
                }
                if (downloadsTab == DownloadsTab.CACHED && activeList.isNotEmpty()) {
                    item(key = "dl-cached-banner") {
                        val isDark = Musico.extras.isDark
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.38f else 0.48f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 2.dp),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Auto-cached from recent streams",
                                        style = Musico.type.title,
                                        fontSize = 13.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        "${formatDownloadsSummary(activeList.size, activeBytes)} · Ready for offline replay",
                                        style = Musico.type.caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .pressable(
                                            onClick = {
                                                hapticThud()
                                                onPromoteCachedToDownloaded(activeList.map { it.track })
                                                downloadsTab = DownloadsTab.DOWNLOADED
                                            },
                                            pressedScale = 0.94f,
                                        ),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Download,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Text(
                                            "Save all",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (activeList.isNotEmpty()) {
                    item(key = "dl-list-header-${downloadsTab.name}") {
                        SectionLabel(
                            if (downloadsTab == DownloadsTab.DOWNLOADED) {
                                "Your downloads · ${formatBytesHuman(activeBytes)}"
                            } else {
                                "Cached songs · ${formatBytesHuman(activeBytes)}"
                            },
                        )
                    }
                    val dlTracks = activeList.map { it.track }
                    itemsIndexed(activeList, key = { i, d -> "dl-${downloadsTab.name}-${d.track.videoId}-$i" }) { _, dl ->
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
                    item(key = "dl-empty-${downloadsTab.name}") {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            EmptyState(
                                icon = if (downloadsTab == DownloadsTab.DOWNLOADED) Icons.Filled.DownloadDone else Icons.Filled.Cached,
                                title = if (downloadsTab == DownloadsTab.DOWNLOADED) {
                                    "No downloaded songs yet"
                                } else {
                                    "No cached songs yet"
                                },
                                subtitle = if (downloadsTab == DownloadsTab.DOWNLOADED) {
                                    "Long-press any song or tap Offline inside a playlist to download it permanently"
                                } else {
                                    "Songs you stream will automatically appear here for instant replay"
                                },
                            )
                            if (downloadsTab == DownloadsTab.DOWNLOADED && partitionedDownloads.cachedOnly.isNotEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(999.dp))
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .pressable(
                                            onClick = {
                                                hapticTick()
                                                downloadsTab = DownloadsTab.CACHED
                                            },
                                            pressedScale = 0.94f,
                                        ),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.Cached,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(17.dp),
                                        )
                                        Text(
                                            "View ${partitionedDownloads.cachedOnly.size} cached songs",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if ((filter == LibrarySection.ALL && savedAlbums.isNotEmpty()) || filter == LibrarySection.ALBUMS) {
                if (savedAlbums.isNotEmpty()) {
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
                } else if (filter == LibrarySection.ALBUMS && recommendedAlbums.isEmpty()) {
                    item(key = "albums-header") { SectionLabel("Saved albums") }
                    item(key = "albums-empty") {
                        EmptyState(
                            icon = Icons.Filled.Album,
                            title = "No saved albums yet",
                            subtitle = "Listen to music or browse Home to discover albums you can save here",
                        )
                    }
                } else if (filter == LibrarySection.ALBUMS) {
                    item(key = "albums-hint") {
                        LibraryHintStrip(
                            icon = Icons.Filled.Album,
                            message = "Albums you save will appear pinned at the top here",
                        )
                    }
                }

                if (filter == LibrarySection.ALBUMS && recommendedAlbums.isNotEmpty()) {
                    item(key = "rec-albums-header") { SectionLabel("Recommended albums") }
                    item(key = "rec-albums-grid") {
                        RecommendedCatalogGrid(
                            entries = recommendedAlbums,
                            circular = false,
                            actionLabel = "Save",
                            onOpen = onOpenCatalogEntry,
                            onAction = onToggleSaveCatalogEntry,
                        )
                    }
                }
            }

            if ((filter == LibrarySection.ALL && savedArtists.isNotEmpty()) || filter == LibrarySection.ARTISTS) {
                if (savedArtists.isNotEmpty()) {
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
                } else if (filter == LibrarySection.ARTISTS && recommendedArtists.isEmpty()) {
                    item(key = "artists-header") { SectionLabel("Followed artists") }
                    item(key = "artists-empty") {
                        EmptyState(
                            icon = Icons.Filled.Person,
                            title = "No followed artists yet",
                            subtitle = "Listen to a few tracks or explore Home to see recommended artists here",
                        )
                    }
                } else if (filter == LibrarySection.ARTISTS) {
                    item(key = "artists-hint") {
                        LibraryHintStrip(
                            icon = Icons.Filled.AutoAwesome,
                            message = "Follow artists below to pin them at the top of your library",
                        )
                    }
                }

                if (filter == LibrarySection.ARTISTS && recommendedArtists.isNotEmpty()) {
                    item(key = "rec-artists-header") { SectionLabel("Recommended artists") }
                    item(key = "rec-artists-grid") {
                        RecommendedCatalogGrid(
                            entries = recommendedArtists,
                            circular = true,
                            actionLabel = "Follow",
                            onOpen = onOpenCatalogEntry,
                            onAction = onToggleSaveCatalogEntry,
                        )
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.34f else 0.45f)),
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

@Composable
private fun LibraryHintStrip(
    icon: ImageVector,
    message: String,
) {
    val isDark = Musico.extras.isDark
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.34f else 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                message,
                style = Musico.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecommendedCatalogGrid(
    entries: List<CatalogEntry>,
    circular: Boolean,
    actionLabel: String,
    onOpen: (CatalogEntry) -> Unit,
    onAction: (CatalogEntry) -> Unit,
) {
    val isDark = Musico.extras.isDark
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        val cols = when {
            maxWidth >= 920.dp -> 5
            maxWidth >= 680.dp -> 4
            maxWidth >= 460.dp -> 3
            else -> 2
        }
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            entries.chunked(cols).forEachIndexed { rowIdx, rowItems ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEachIndexed { colIdx, entry ->
                        val globalIdx = rowIdx * cols + colIdx
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (isDark) {
                                MaterialTheme.colorScheme.surfaceContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLowest
                            },
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.34f else 0.48f),
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .staggerIn(index = globalIdx)
                                .pressable(onClick = { onOpen(entry) }, pressedScale = 0.96f),
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = if (circular) Alignment.CenterHorizontally else Alignment.Start,
                            ) {
                                Artwork(
                                    url = entry.thumbnail,
                                    contentDescription = entry.title,
                                    modifier = Modifier.size(if (circular) 104.dp else 116.dp),
                                    cornerRadius = if (circular) 52.dp else 18.dp,
                                    shape = if (circular) ExpressiveShapes.artistFrame(globalIdx) else null,
                                    placeholderIcon = if (circular) Icons.Filled.Person else Icons.Filled.Album,
                                )
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    entry.title,
                                    style = Musico.type.title,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = if (circular) TextAlign.Center else TextAlign.Start,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    entry.subtitle.ifBlank { if (circular) "Artist" else "Album" },
                                    style = Musico.type.caption,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = if (circular) TextAlign.Center else TextAlign.Start,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(10.dp))
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(999.dp))
                                        .pointerHoverIcon(PointerIcon.Hand)
                                        .pressable(
                                            onClick = {
                                                hapticTick()
                                                onAction(entry)
                                            },
                                            pressedScale = 0.93f,
                                        ),
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.Add,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            actionLabel,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    repeat((cols - rowItems.size).coerceAtLeast(0)) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
