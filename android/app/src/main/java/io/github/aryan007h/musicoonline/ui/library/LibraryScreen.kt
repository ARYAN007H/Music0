package io.github.aryan007h.musicoonline.ui.library

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.ui.components.EmptyState
import io.github.aryan007h.musicoonline.ui.components.TrackRow
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.breathing
import io.github.aryan007h.musicoonline.ui.theme.pressable
import io.github.aryan007h.musicoonline.ui.theme.staggerIn

/**
 * The Library tab: your on-device world — Liked Songs, Recently Played —
 * plus the session queue. Honest about being local: the cloud shelves live
 * on Home, this is YOUR music.
 */
@Composable
fun LibraryScreen(
    recentlyPlayed: List<OnlineTrack>,
    queue: List<OnlineTrack>,
    queueIndex: Int,
    currentTrackId: String?,
    isPlaying: Boolean,
    resolvingId: String?,
    likedIds: Set<String>,
    onPlayLiked: () -> Unit,
    onOpenTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onLikeToggle: (OnlineTrack) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit = {},
) {
    // Liked playback resolves against the session's known tracks (same as the VM).
    val knownLiked = recentlyPlayed.filter { it.videoId in likedIds }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(key = "liked-hero") {
            LikedHero(
                count = likedIds.size,
                canPlay = knownLiked.isNotEmpty(),
                onPlay = onPlayLiked,
            )
        }
        if (recentlyPlayed.isNotEmpty()) {
            item(key = "recents-header") {
                SectionLabel("Recently played")
            }
            itemsIndexedRecent(recentlyPlayed, currentTrackId, isPlaying, resolvingId, likedIds, onOpenTrack, onLikeToggle, onTrackLongClick)
        }
        if (queue.isNotEmpty()) {
            item(key = "queue-header") {
                SectionLabel("Session queue")
            }
            itemsIndexedQueue(queue, queueIndex, currentTrackId, isPlaying, resolvingId, likedIds, onOpenTrack, onLikeToggle, onTrackLongClick)
        }
        if (recentlyPlayed.isEmpty() && queue.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Filled.History,
                    title = "Nothing here yet",
                    subtitle = "Play something from Home and it shows up here",
                )
            }
        }
    }
}

@Composable
private fun LikedHero(count: Int, canPlay: Boolean, onPlay: () -> Unit) {
    Surface(
        shape = MusicoShapes.cardLarge,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .staggerIn(index = 0)
            .padding(vertical = 12.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(MusicoShapes.cardLarge)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF3B2A80),
                            Color(0xFF7C5CFF),
                            Color(0xFF53F2C4),
                        ),
                    ),
                )
                .pressable(onClick = onPlay, enabled = canPlay, pressedScale = 0.97f)
                .padding(22.dp),
        ) {
            Column {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(34.dp)
                        .breathing(max = 1.1f),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Liked Songs",
                    style = Musico.type.display,
                    fontSize = 28.sp,
                    color = Color.White,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (count == 0) "Tap the heart on any track" else "$count song${if (count == 1) "" else "s"}",
                    style = Musico.type.body,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            if (canPlay) {
                Surface(
                    shape = MusicoShapes.chip,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF3B2A80),
                        )
                        Text(
                            "Play",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF3B2A80),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        title.uppercase(),
        style = Musico.type.section,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp, start = 12.dp),
    )
}

// Thin wrappers over LazyListScope.itemsIndexed kept out of the main body for
// readability of the tab content.

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedRecent(
    tracks: List<OnlineTrack>,
    currentTrackId: String?,
    isPlaying: Boolean,
    resolvingId: String?,
    likedIds: Set<String>,
    onOpenTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onLikeToggle: (OnlineTrack) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit,
) {
    itemsIndexedTrack(tracks) { i, track ->
        TrackRow(
            track = track,
            index = null,
            isCurrent = track.videoId == currentTrackId,
            isPlaying = isPlaying,
            isLiked = track.videoId in likedIds,
            resolving = resolvingId == track.videoId,
            onClick = { onOpenTrack(track, tracks) },
            onLikeToggle = { onLikeToggle(track) },
            onLongClick = { onTrackLongClick(track) },
            modifier = Modifier.animateItem(placementSpec = MusicoMotion.spatial()),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedQueue(
    queue: List<OnlineTrack>,
    queueIndex: Int,
    currentTrackId: String?,
    isPlaying: Boolean,
    resolvingId: String?,
    likedIds: Set<String>,
    onOpenTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onLikeToggle: (OnlineTrack) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit,
) {
    itemsIndexedTrack(queue) { i, track ->
        TrackRow(
            track = track,
            index = i + 1,
            isCurrent = track.videoId == currentTrackId,
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

private fun androidx.compose.foundation.lazy.LazyListScope.itemsIndexedTrack(
    tracks: List<OnlineTrack>,
    row: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Int, OnlineTrack) -> Unit,
) {
    items(tracks.size, key = { "${tracks[it].videoId}#$it" }) { i ->
        row(i, tracks[i])
    }
}
