package io.github.aryan007h.musicoonline.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.DownloadedTrack
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.SmartMix
import io.github.aryan007h.musicoonline.core.UserPlaylist
import io.github.aryan007h.musicoonline.ui.components.AmbientTopWash
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.components.EmptyState
import io.github.aryan007h.musicoonline.ui.components.TrackRow
import io.github.aryan007h.musicoonline.ui.components.musicoTopInset
import io.github.aryan007h.musicoonline.ui.player.rememberArtworkAtmosphere
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.hapticThud
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import io.github.aryan007h.musicoonline.ui.theme.pressable

/** Which local/smart collection is currently open in [PlaylistDetailScreen]. */
sealed interface LibraryCollectionRef {
    data class Playlist(val id: String) : LibraryCollectionRef
    data object LikedSongs : LibraryCollectionRef
    data object OfflineDownloads : LibraryCollectionRef
    data class Mix(val id: String) : LibraryCollectionRef
}

/**
 * Full-screen workspace for User Playlists, Liked Songs, Offline Downloads,
 * and Smart Mixes. Pure KMP + cover-extracted AmbientTopWash.
 */
@Composable
fun PlaylistDetailScreen(
    ref: LibraryCollectionRef,
    playlists: List<UserPlaylist>,
    likedTracks: List<OnlineTrack>,
    downloads: List<DownloadedTrack>,
    downloadsTotalBytes: Long,
    smartMixes: List<SmartMix>,
    recentTracks: List<OnlineTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    resolvingId: String?,
    likedIds: Set<String>,
    downloadingDone: Int,
    downloadingTotal: Int,
    onBack: () -> Unit,
    onPlayTracks: (List<OnlineTrack>, Int) -> Unit,
    onShuffleTracks: (List<OnlineTrack>) -> Unit,
    onDownloadAll: (List<OnlineTrack>) -> Unit,
    onLikeToggle: (OnlineTrack) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit,
    onRenamePlaylist: (playlistId: String, newName: String) -> Unit,
    onDeletePlaylist: (playlistId: String) -> Unit,
    onAddTrackToPlaylist: (playlistId: String, track: OnlineTrack) -> Unit,
    onRemoveTrackFromPlaylist: (playlistId: String, index: Int) -> Unit,
    onMoveTrackInPlaylist: (playlistId: String, from: Int, to: Int) -> Unit,
    onSaveMixAsPlaylist: (name: String, tracks: List<OnlineTrack>) -> Unit,
) {
    val playlist = (ref as? LibraryCollectionRef.Playlist)?.let { r ->
        playlists.firstOrNull { it.id == r.id }
    }
    val smartMix = (ref as? LibraryCollectionRef.Mix)?.let { r ->
        smartMixes.firstOrNull { it.id == r.id }
    }

    val title = when (ref) {
        is LibraryCollectionRef.Playlist -> playlist?.name ?: "Playlist"
        LibraryCollectionRef.LikedSongs -> "Liked Songs"
        LibraryCollectionRef.OfflineDownloads -> "Offline Downloads"
        is LibraryCollectionRef.Mix -> smartMix?.title ?: "Smart Mix"
    }
    val subtitle = when (ref) {
        is LibraryCollectionRef.Playlist -> "Custom playlist"
        LibraryCollectionRef.LikedSongs -> "Your saved favorites"
        LibraryCollectionRef.OfflineDownloads -> "Ready to play without signal"
        is LibraryCollectionRef.Mix -> smartMix?.subtitle ?: "Auto-updating mix"
    }
    val allTracks: List<OnlineTrack> = remember(ref, playlist, likedTracks, downloads, smartMix) {
        when (ref) {
            is LibraryCollectionRef.Playlist -> playlist?.tracks?.map { it.toOnlineTrack() }.orEmpty()
            LibraryCollectionRef.LikedSongs -> likedTracks
            LibraryCollectionRef.OfflineDownloads -> downloads.map { it.track }
            is LibraryCollectionRef.Mix -> smartMix?.tracks.orEmpty()
        }
    }
    val sizeByVideoId = remember(downloads) {
        downloads.associate { it.track.videoId to it.sizeBytes }
    }
    val coverAtmosphere = rememberArtworkAtmosphere(allTracks.firstNotNullOfOrNull { it.thumbnail })

    var filterQuery by remember(ref) { mutableStateOf("") }
    var showSearchBar by remember(ref) { mutableStateOf(false) }
    var reorderMode by remember(ref) { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showAddSongsSheet by remember { mutableStateOf(false) }

    val filteredTracks = remember(allTracks, filterQuery) {
        val q = filterQuery.trim()
        if (q.isEmpty()) {
            allTracks
        } else {
            allTracks.filter {
                it.title.contains(q, ignoreCase = true) ||
                    it.artist.contains(q, ignoreCase = true) ||
                    it.album.contains(q, ignoreCase = true)
            }
        }
    }

    val isEditablePlaylist = ref is LibraryCollectionRef.Playlist && playlist != null
    val isDownloading = downloadingTotal > 0

    val metaLine = remember(allTracks, ref, downloadsTotalBytes) {
        buildList {
            add("${allTracks.size} song${if (allTracks.size == 1) "" else "s"}")
            formatDurationSummary(allTracks)?.let { add(it) }
            if (ref is LibraryCollectionRef.OfflineDownloads && downloadsTotalBytes > 0L) {
                add(formatBytesHuman(downloadsTotalBytes))
            }
        }.joinToString(" · ")
    }

    Box(Modifier.fillMaxSize()) {
        AmbientTopWash(
            atmosphere = coverAtmosphere,
            height = 320.dp,
            intensity = 0.30f,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Column(
            Modifier
                .fillMaxSize()
                .musicoTopInset()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text(
                    title,
                    style = Musico.type.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (allTracks.isNotEmpty()) {
                    IconButton(onClick = {
                        hapticTick()
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) filterQuery = ""
                    }) {
                        Icon(
                            if (showSearchBar) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = "Filter songs",
                            tint = if (showSearchBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                if (isEditablePlaylist) {
                    IconButton(onClick = {
                        hapticTick()
                        reorderMode = !reorderMode
                        if (reorderMode) {
                            showSearchBar = false
                            filterQuery = ""
                        }
                    }) {
                        Icon(
                            Icons.Filled.SwapVert,
                            contentDescription = "Reorder tracks",
                            tint = if (reorderMode) Musico.extras.mint else MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "Playlist options",
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Add songs") },
                                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showAddSongsSheet = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Rename playlist") },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    showRenameDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete playlist", color = Musico.extras.coral) },
                                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = Musico.extras.coral) },
                                onClick = {
                                    menuExpanded = false
                                    showDeleteConfirm = true
                                },
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showSearchBar) {
                OutlinedTextField(
                    value = filterQuery,
                    onValueChange = { filterQuery = it },
                    placeholder = { Text("Filter in $title…") },
                    singleLine = true,
                    shape = MusicoShapes.chip,
                    trailingIcon = {
                        if (filterQuery.isNotEmpty()) {
                            IconButton(onClick = { filterQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            AnimatedVisibility(visible = isDownloading) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Downloading offline…",
                            style = Musico.type.caption,
                            color = Musico.extras.mint,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "$downloadingDone / $downloadingTotal",
                            style = Musico.type.tabularCaption,
                            color = Musico.extras.inkDim,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (downloadingDone.toFloat() / downloadingTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        color = Musico.extras.mint,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(CircleShape),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item(key = "collection-hero") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (ref) {
                                LibraryCollectionRef.LikedSongs -> {
                                    Box(
                                        Modifier
                                            .size(116.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
                                    }
                                }
                                LibraryCollectionRef.OfflineDownloads -> {
                                    Box(
                                        Modifier
                                            .size(116.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.DownloadDone, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(44.dp))
                                    }
                                }
                                else -> {
                                    PlaylistMosaicArtwork(
                                        thumbnails = allTracks.map { it.thumbnail },
                                        modifier = Modifier.size(116.dp),
                                        cornerRadius = 24.dp,
                                    )
                                }
                            }

                            Spacer(Modifier.width(18.dp))

                            Column(Modifier.weight(1f)) {
                                Text(
                                    title,
                                    style = Musico.type.display.copy(fontSize = 24.sp),
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    subtitle,
                                    style = Musico.type.caption,
                                    color = Musico.extras.inkDim,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    metaLine,
                                    style = Musico.type.tabularCaption,
                                    color = Musico.extras.mint,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Spacer(Modifier.height(18.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CollectionActionPill(
                                icon = Icons.Filled.PlayArrow,
                                label = "Play",
                                filled = true,
                                enabled = allTracks.isNotEmpty(),
                                onClick = { hapticThud(); onPlayTracks(allTracks, 0) },
                                modifier = Modifier.weight(1f),
                            )
                            CollectionActionPill(
                                icon = Icons.Filled.Shuffle,
                                label = "Shuffle",
                                enabled = allTracks.isNotEmpty(),
                                onClick = { hapticThud(); onShuffleTracks(allTracks) },
                                modifier = Modifier.weight(1f),
                            )
                            when (ref) {
                                is LibraryCollectionRef.Playlist -> {
                                    CollectionActionPill(
                                        icon = if (isDownloading) null else Icons.Filled.Download,
                                        label = if (isDownloading) "$downloadingDone/$downloadingTotal" else "Offline",
                                        busy = isDownloading,
                                        enabled = allTracks.isNotEmpty() && !isDownloading,
                                        onClick = { hapticTick(); onDownloadAll(allTracks) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                is LibraryCollectionRef.Mix -> {
                                    CollectionActionPill(
                                        icon = Icons.Filled.PlaylistAdd,
                                        label = "Save",
                                        enabled = allTracks.isNotEmpty(),
                                        onClick = {
                                            hapticThud()
                                            onSaveMixAsPlaylist(title, allTracks)
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                LibraryCollectionRef.LikedSongs -> {
                                    CollectionActionPill(
                                        icon = if (isDownloading) null else Icons.Filled.Download,
                                        label = if (isDownloading) "$downloadingDone/$downloadingTotal" else "Offline",
                                        busy = isDownloading,
                                        enabled = allTracks.isNotEmpty() && !isDownloading,
                                        onClick = { hapticTick(); onDownloadAll(allTracks) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                LibraryCollectionRef.OfflineDownloads -> Unit
                            }
                        }

                        if (isEditablePlaylist) {
                            Spacer(Modifier.height(10.dp))
                            Surface(
                                shape = MusicoShapes.chip,
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable(onClick = { hapticTick(); showAddSongsSheet = true }, pressedScale = 0.98f),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Add songs to this playlist",
                                        style = Musico.type.title,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }

                        if (reorderMode && isEditablePlaylist) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Musico.extras.mint.copy(alpha = 0.14f))
                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Reorder mode — move or remove tracks",
                                    style = Musico.type.caption,
                                    color = Musico.extras.mint,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "Done",
                                    style = Musico.type.caption,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.pressable(onClick = { reorderMode = false }),
                                )
                            }
                        }
                    }
                }

                if (filteredTracks.isEmpty()) {
                    item(key = "collection-empty") {
                        EmptyState(
                            icon = if (ref is LibraryCollectionRef.OfflineDownloads) Icons.Filled.Download else Icons.Filled.PlaylistAdd,
                            title = if (filterQuery.isNotBlank()) "No matching songs" else "No songs yet",
                            subtitle = when {
                                filterQuery.isNotBlank() -> "Try a different filter term"
                                isEditablePlaylist -> "Tap Add songs above or long-press any track anywhere in the app"
                                ref is LibraryCollectionRef.LikedSongs -> "Tap the heart on any song to build your favorites"
                                ref is LibraryCollectionRef.OfflineDownloads -> "Save tracks or playlists offline to listen anywhere"
                                else -> "Listen to a few tracks and this mix will fill automatically"
                            },
                        )
                    }
                } else if (reorderMode && isEditablePlaylist && playlist != null) {
                    itemsIndexed(
                        items = allTracks,
                        key = { idx, t -> "edit-${t.videoId}-$idx" },
                    ) { idx, track ->
                        EditablePlaylistTrackRow(
                            track = track,
                            index = idx,
                            total = allTracks.size,
                            onMoveUp = {
                                if (idx > 0) {
                                    hapticTick()
                                    onMoveTrackInPlaylist(playlist.id, idx, idx - 1)
                                }
                            },
                            onMoveDown = {
                                if (idx + 1 < allTracks.size) {
                                    hapticTick()
                                    onMoveTrackInPlaylist(playlist.id, idx, idx + 1)
                                }
                            },
                            onRemove = {
                                hapticThud()
                                onRemoveTrackFromPlaylist(playlist.id, idx)
                            },
                            modifier = Modifier
                                .animateItem(placementSpec = MusicoMotion.spatial())
                                .padding(horizontal = 16.dp),
                        )
                    }
                } else {
                    itemsIndexed(
                        items = filteredTracks,
                        key = { idx, t -> "col-${t.videoId}-$idx" },
                    ) { idx, track ->
                        val sizeBytes = sizeByVideoId[track.videoId]
                        Column(
                            Modifier
                                .animateItem(placementSpec = MusicoMotion.spatial())
                                .padding(horizontal = 14.dp),
                        ) {
                            TrackRow(
                                track = if (ref is LibraryCollectionRef.OfflineDownloads && sizeBytes != null) {
                                    val sizeTag = formatBytesHuman(sizeBytes)
                                    val baseArtist = track.artist.ifEmpty { "Offline track" }
                                    track.copy(artist = "$baseArtist · $sizeTag")
                                } else {
                                    track
                                },
                                index = idx + 1,
                                isCurrent = track.videoId == currentTrackId,
                                isPlaying = isPlaying,
                                isLiked = track.videoId in likedIds,
                                resolving = resolvingId == track.videoId,
                                onClick = { onPlayTracks(filteredTracks, idx) },
                                onLikeToggle = { onLikeToggle(track) },
                                onLongClick = { onTrackLongClick(track) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog && playlist != null) {
        CreateOrRenamePlaylistDialog(
            title = "Rename playlist",
            confirmLabel = "Save",
            initialName = playlist.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName -> onRenamePlaylist(playlist.id, newName) },
        )
    }

    if (showDeleteConfirm && playlist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete “${playlist.name}”?", fontWeight = FontWeight.Bold) },
            text = { Text("This removes the playlist from your library. Your liked songs and cached downloads stay untouched.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePlaylist(playlist.id)
                        onBack()
                    },
                ) {
                    Text("Delete", color = Musico.extras.coral, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showAddSongsSheet && playlist != null) {
        val candidatePool = remember(likedTracks, downloads, recentTracks) {
            val seen = LinkedHashMap<String, OnlineTrack>()
            (likedTracks + downloads.map { it.track } + recentTracks).forEach { t ->
                if (t.videoId.isNotBlank() && t.videoId !in seen) seen[t.videoId] = t
            }
            seen.values.toList()
        }
        QuickAddSongsSheet(
            playlistName = playlist.name,
            candidates = candidatePool,
            existingIds = playlist.tracks.map { it.id }.toSet(),
            onDismiss = { showAddSongsSheet = false },
            onAdd = { track -> onAddTrackToPlaylist(playlist.id, track) },
        )
    }
}

@Composable
private fun EditablePlaylistTrackRow(
    track: OnlineTrack,
    index: Int,
    total: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                url = track.thumbnail,
                contentDescription = track.title,
                modifier = Modifier.size(44.dp),
                cornerRadius = 11.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title.ifEmpty { "Untitled" },
                    style = Musico.type.title,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist.ifEmpty { "Unknown artist" },
                    style = Musico.type.caption,
                    color = Musico.extras.inkDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.ArrowUpward,
                    contentDescription = "Move up",
                    tint = if (index > 0) MaterialTheme.colorScheme.onBackground else Musico.extras.inkFaint.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onMoveDown, enabled = index + 1 < total, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.ArrowDownward,
                    contentDescription = "Move down",
                    tint = if (index + 1 < total) MaterialTheme.colorScheme.onBackground else Musico.extras.inkFaint.copy(alpha = 0.35f),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Remove from playlist",
                    tint = Musico.extras.coral,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAddSongsSheet(
    playlistName: String,
    candidates: List<OnlineTrack>,
    existingIds: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (OnlineTrack) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(candidates, query) {
        val q = query.trim()
        if (q.isEmpty()) candidates
        else candidates.filter { it.title.contains(q, true) || it.artist.contains(q, true) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MusicoShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text(
                "Add to $playlistName",
                style = Musico.type.title,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Filter liked, downloads & recents…") },
                singleLine = true,
                shape = MusicoShapes.chip,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            if (shown.isEmpty()) {
                Text(
                    "Play or like songs first, or long-press any song in Search/Home to add it here.",
                    style = Musico.type.caption,
                    color = Musico.extras.inkDim,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(shown, key = { i, t -> "pick-${t.videoId}-$i" }) { _, track ->
                        val added = track.videoId in existingIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .pressable(
                                    onClick = {
                                        hapticTick()
                                        onAdd(track)
                                    },
                                    pressedScale = 0.98f,
                                )
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Artwork(
                                url = track.thumbnail,
                                contentDescription = track.title,
                                modifier = Modifier.size(44.dp),
                                cornerRadius = 10.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    track.title.ifEmpty { "Untitled" },
                                    style = Musico.type.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    track.artist.ifEmpty { "Unknown artist" },
                                    style = Musico.type.caption,
                                    color = Musico.extras.inkDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = if (added) Musico.extras.mint.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Filled.Add,
                                        contentDescription = "Add track",
                                        tint = if (added) Musico.extras.mint else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionActionPill(
    icon: ImageVector?,
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    busy: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        shape = MusicoShapes.chip,
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.pressable(onClick = onClick, enabled = enabled && !busy, pressedScale = 0.94f),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            when {
                busy -> CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                icon != null -> Icon(
                    icon,
                    contentDescription = null,
                    tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                color = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
            )
        }
    }
}
