package io.github.aryan007h.musicoonline.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.ShelfItem
import io.github.aryan007h.musicoonline.ui.MusicViewModel
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.components.ErrorState
import io.github.aryan007h.musicoonline.ui.components.TrackRow
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes

/**
 * Album / playlist / artist / genre page: hero header (art, title,
 * subtitle), action row (Play · Queue all · Save offline), the flattened
 * track list, then the page's remaining shelves (more-from-artist, etc.).
 */
@Composable
fun DetailScreen(
    entry: MusicViewModel.DetailPageState,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedIds: Set<String>,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onQueueAll: () -> Unit,
    onSaveOffline: () -> Unit,
    onPlayTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onLikeToggle: (OnlineTrack) -> Unit,
    onOpenItem: (ShelfItem, List<ShelfItem>) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit = {},
) {
    val page = entry.page
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPaddingCompat(),
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
                entry.title,
                style = Musico.type.title,
                color = Musico.extras.inkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        AnimatedContent(
            targetState = Triple(entry.loading, entry.error, page),
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
            label = "detailBody",
        ) { (loading, error, p) ->
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                error != null -> ErrorState(message = error, onRetry = null)
                p == null -> Box(Modifier.fillMaxSize())
                else -> DetailBody(
                    entry = entry,
                    page = p,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    likedIds = likedIds,
                    onPlay = onPlay,
                    onQueueAll = onQueueAll,
                    onSaveOffline = onSaveOffline,
                    onPlayTrack = onPlayTrack,
                    onLikeToggle = onLikeToggle,
                    onOpenItem = onOpenItem,
                    onTrackLongClick = onTrackLongClick,
                )
            }
        }
    }
}

@Composable
private fun DetailBody(
    entry: MusicViewModel.DetailPageState,
    page: io.github.aryan007h.musicoonline.core.BrowsePage,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedIds: Set<String>,
    onPlay: () -> Unit,
    onQueueAll: () -> Unit,
    onSaveOffline: () -> Unit,
    onPlayTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onLikeToggle: (OnlineTrack) -> Unit,
    onOpenItem: (ShelfItem, List<ShelfItem>) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit,
) {
    val header = page.header
    val heroArt = header?.thumbnail
        ?: page.shelves.firstNotNullOfOrNull { shelf -> shelf.items.firstNotNullOfOrNull { it.thumbnail } }
    val subtitle = header?.subtitle
        ?: page.shelves.firstOrNull()?.title?.uppercase() ?: ""

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 60.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "hero") {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.4f)
                            .clip(MusicoShapes.hero),
                    ) {
                        Artwork(
                            url = heroArt,
                            contentDescription = entry.title,
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 28.dp,
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        1f to Color(0xCC0A0A12),
                                    ),
                                ),
                        )
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(18.dp),
                        ) {
                            if (subtitle.isNotBlank()) {
                                Text(
                                    subtitle.uppercase(),
                                    style = Musico.type.section,
                                    fontSize = 11.sp,
                                    color = Musico.extras.mint,
                                    maxLines = 2,
                                )
                            }
                            Text(
                                entry.title,
                                style = Musico.type.display.copy(fontSize = 28.sp),
                                color = Color.White,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Action row: Play / Queue all / Save offline
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DetailAction(
                        icon = Icons.Filled.PlayArrow,
                        label = "Play",
                        filled = true,
                        onClick = onPlay,
                        modifier = Modifier.weight(1f),
                    )
                    DetailAction(
                        icon = Icons.Filled.Queue,
                        label = "Queue all",
                        onClick = onQueueAll,
                        modifier = Modifier.weight(1f),
                    )
                    DetailAction(
                        icon = if (entry.saving) null else if (entry.saved > 0) Icons.Filled.DownloadDone else Icons.Filled.Download,
                        label = when {
                            entry.saving -> "${entry.saved}/${detailCount(entry)}"
                            entry.saved > 0 -> "Saved"
                            else -> "Save"
                        },
                        busy = entry.saving,
                        onClick = onSaveOffline,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        val tracks = page.shelves.flatMap { it.items }.mapNotNull { it.toTrackCompat() }
        if (tracks.isNotEmpty()) {
            item(key = "tracks-header") {
                Text(
                    "${tracks.size} SONGS",
                    style = Musico.type.section,
                    fontSize = 11.sp,
                    color = Musico.extras.inkDim,
                    modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            itemsIndexedTracks(tracks) { i, track ->
                TrackRow(
                    track = track,
                    index = i + 1,
                    isCurrent = track.videoId == currentTrackId,
                    isPlaying = isPlaying,
                    isLiked = track.videoId in likedIds,
                    resolving = false,
                    onClick = { onPlayTrack(track, tracks) },
                    onLikeToggle = { onLikeToggle(track) },
                    onLongClick = { onTrackLongClick(track) },
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        // Remaining shelves below the track list (more from artist, etc.).
        page.shelves.drop(1).forEachIndexed { idx, shelf ->
            if (shelf.items.isEmpty()) return@forEachIndexed
            item(key = "shelf-$idx-${shelf.title}") {
                Text(
                    shelf.title.uppercase(),
                    style = Musico.type.section,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 32.dp, top = 20.dp, bottom = 6.dp),
                )
            }
            item(key = "shelfrow-$idx") {
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(shelf.items, key = { it.videoId ?: it.browseId ?: "${it.title}#${it.hashCode()}" }) { item ->
                        io.github.aryan007h.musicoonline.ui.components.ShelfCard(
                            item = item,
                            width = 150.dp,
                            onClick = { onOpenItem(item, shelf.items) },
                        )
                    }
                }
            }
        }
    }
}

private fun detailCount(entry: MusicViewModel.DetailPageState): Int =
    entry.page?.shelves.orEmpty().flatMap { it.items }.count { it.videoId != null }

private fun ShelfItem.toTrackCompat(): OnlineTrack? = videoId?.let {
    OnlineTrack(videoId = it, title = title, artist = subtitle, thumbnail = thumbnail)
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedTracks(
    tracks: List<OnlineTrack>,
    row: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Int, OnlineTrack) -> Unit,
) {
    items(tracks.size, key = { "${tracks[it].videoId}#$it" }) { i ->
        row(i, tracks[i])
    }
}

@Composable
private fun DetailAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = MusicoShapes.chip,
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
            .clip(MusicoShapes.chip)
            .clickable(enabled = !busy, onClick = onClick),
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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

/** Detail pages are full overlays: pad for the status bar themselves. */
private fun Modifier.statusBarsPaddingCompat(): Modifier =
    this.then(Modifier.statusBarsPadding())
