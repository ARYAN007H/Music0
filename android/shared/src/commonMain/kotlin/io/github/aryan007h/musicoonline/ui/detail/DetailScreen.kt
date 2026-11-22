package io.github.aryan007h.musicoonline.ui.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.BrowsePage
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.ShelfItem
import io.github.aryan007h.musicoonline.ui.MusicViewModel
import io.github.aryan007h.musicoonline.ui.components.AmbientTopWash
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.components.DetailPageSkeleton
import io.github.aryan007h.musicoonline.ui.components.musicoScrimBrush
import io.github.aryan007h.musicoonline.ui.components.ErrorState
import io.github.aryan007h.musicoonline.ui.components.ShelfCarousel
import io.github.aryan007h.musicoonline.ui.components.TrackRow
import io.github.aryan007h.musicoonline.ui.components.musicoTopInset
import io.github.aryan007h.musicoonline.ui.player.rememberArtworkAtmosphere
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.hapticThud
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import io.github.aryan007h.musicoonline.ui.theme.pressable
import io.github.aryan007h.musicoonline.ui.theme.staggerIn

/**
 * Album / playlist / artist / genre page with cover-extracted AmbientTopWash
 * and a collapsing parallax hero header that fades smoothly into a sticky bar.
 */
@Composable
fun DetailScreen(
    entry: MusicViewModel.DetailPageState,
    currentTrackId: String?,
    isPlaying: Boolean,
    likedIds: Set<String>,
    isSavedInLibrary: Boolean = false,
    onToggleSaveLibrary: () -> Unit = {},
    onAddAllToPlaylist: () -> Unit = {},
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onQueueAll: () -> Unit,
    onSaveOffline: () -> Unit,
    onPlayTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onLikeToggle: (OnlineTrack) -> Unit,
    onOpenItem: (ShelfItem, List<ShelfItem>) -> Unit,
    onRetry: (() -> Unit)? = null,
    onTrackLongClick: (OnlineTrack) -> Unit = {},
) {
    val page = entry.page
    val heroArt = page?.header?.thumbnail
        ?: page?.shelves?.firstNotNullOfOrNull { shelf -> shelf.items.firstNotNullOfOrNull { it.thumbnail } }
    val atmosphere = rememberArtworkAtmosphere(heroArt)
    val hasPlayableTracks = page?.shelves.orEmpty().any { s -> s.items.any { it.videoId != null } }
    val listState = rememberLazyListState()

    val stickyAlpha by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 280f).coerceIn(0f, 1f)
        }
    }

    Box(Modifier.fillMaxSize()) {
        AmbientTopWash(
            atmosphere = atmosphere,
            height = 360.dp,
            intensity = 0.34f,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        Column(
            Modifier
                .fillMaxSize()
                .musicoTopInset()
                .navigationBarsPadding(),
        ) {
            // Sticky bar that gains a frosted tint + full title opacity on scroll
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = stickyAlpha * 0.90f))
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
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .alpha(0.45f + stickyAlpha * 0.55f),
                )
                if (hasPlayableTracks) {
                    IconButton(onClick = { hapticTick(); onAddAllToPlaylist() }) {
                        Icon(
                            Icons.Filled.PlaylistAdd,
                            contentDescription = "Add all to playlist",
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
                IconButton(onClick = { hapticTick(); onToggleSaveLibrary() }) {
                    Icon(
                        if (isSavedInLibrary) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                        contentDescription = if (isSavedInLibrary) "Remove from library" else "Save to library",
                        tint = if (isSavedInLibrary) Musico.extras.mint else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            AnimatedContent(
                targetState = Triple(entry.loading, entry.error, page),
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                label = "detailBody",
            ) { (loading, error, p) ->
                when {
                    loading -> DetailPageSkeleton()
                    error != null -> ErrorState(message = error, onRetry = onRetry)
                    p == null -> Box(Modifier.fillMaxSize())
                    else -> DetailBody(
                        entry = entry,
                        page = p,
                        heroArt = heroArt,
                        listState = listState,
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
}

@Composable
private fun DetailBody(
    entry: MusicViewModel.DetailPageState,
    page: BrowsePage,
    heroArt: String?,
    listState: androidx.compose.foundation.lazy.LazyListState,
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
    val subtitle = header?.subtitle
        ?: page.shelves.firstOrNull()?.title?.uppercase() ?: ""

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item(key = "hero") {
            val scrollOffset = if (listState.firstVisibleItemIndex == 0) {
                listState.firstVisibleItemScrollOffset.toFloat()
            } else 400f
            val parallaxShift = (scrollOffset * 0.24f).coerceAtMost(96f)
            val heroScale = (1f - (scrollOffset / 1800f)).coerceIn(0.92f, 1f)

            Column(Modifier.fillMaxWidth().staggerIn(index = 0)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .graphicsLayer {
                            translationY = parallaxShift
                            scaleX = heroScale
                            scaleY = heroScale
                        },
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1.45f)
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
                                .background(musicoScrimBrush(Color(0xFF0A0A12), maxAlpha = 0.86f, startFraction = 0.22f)),
                        )
                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(20.dp),
                        ) {
                            if (subtitle.isNotBlank()) {
                                Text(
                                    subtitle.uppercase(),
                                    style = Musico.type.overline,
                                    color = Musico.extras.mint,
                                    maxLines = 2,
                                )
                                Spacer(Modifier.height(4.dp))
                            }
                            Text(
                                entry.title,
                                style = Musico.type.headline,
                                color = Color.White,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

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
                        onClick = { hapticThud(); onPlay() },
                        modifier = Modifier.weight(1f),
                    )
                    DetailAction(
                        icon = Icons.Filled.Queue,
                        label = "Queue all",
                        onClick = { hapticTick(); onQueueAll() },
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
                        onClick = { hapticTick(); onSaveOffline() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        val tracks = page.shelves.flatMap { it.items }.mapNotNull { it.toTrackCompat() }
        if (tracks.isNotEmpty()) {
            item(key = "tracks-header") {
                Text(
                    "${tracks.size} SONG${if (tracks.size == 1) "" else "S"}",
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
                    modifier = Modifier
                        .animateItem(placementSpec = MusicoMotion.spatial())
                        .padding(horizontal = 16.dp),
                )
            }
        }

        page.shelves.drop(1).forEachIndexed { idx, shelf ->
            if (shelf.items.isEmpty()) return@forEachIndexed
            item(key = "shelf-$idx-${shelf.title}") {
                ShelfCarousel(
                    shelfTitle = shelf.title.ifBlank { "More" },
                    items = shelf.items,
                    onItemClick = onOpenItem,
                    cardWidth = 150.dp,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

private fun detailCount(entry: MusicViewModel.DetailPageState): Int =
    entry.page?.shelves.orEmpty().flatMap { it.items }.count { it.videoId != null }

private fun ShelfItem.toTrackCompat(): OnlineTrack? = videoId?.let {
    OnlineTrack(videoId = it, title = title, artist = subtitle, thumbnail = thumbnail)
}

private fun LazyListScope.itemsIndexedTracks(
    tracks: List<OnlineTrack>,
    row: @Composable LazyItemScope.(Int, OnlineTrack) -> Unit,
) {
    items(tracks.size, key = { "${tracks[it].videoId}#$it" }) { i ->
        row(i, tracks[i])
    }
}

@Composable
private fun DetailAction(
    icon: ImageVector?,
    label: String,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        shape = MusicoShapes.chip,
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.pressable(
            onClick = onClick,
            enabled = !busy,
            pressedScale = 0.94f,
        ),
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
