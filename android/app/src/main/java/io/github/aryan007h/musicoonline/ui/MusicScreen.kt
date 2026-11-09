package io.github.aryan007h.musicoonline.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.aryan007h.musicoonline.core.OnlineTrack

/**
 * Phase-1 native screen (#111): search bar → results → tap to play, with a
 * compact now-playing bar. Deliberately plain Material3 — visual identity
 * (the Apple-Music-class look) is a later phase; correctness first.
 */
@Composable
fun MusicScreen(vm: MusicViewModel) {
    val state by vm.state.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Musico", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.weight(1f))
                state.coreVersion?.let {
                    Text("core $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Songs, artists, albums…") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                )
                Spacer(Modifier.size(8.dp))
                IconButton(onClick = vm::search, enabled = !state.searching) {
                    if (state.searching) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = "Search")
                    }
                }
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.results, key = { it.videoId + it.source }) { track ->
                    TrackRow(
                        track = track,
                        resolving = state.resolvingId == track.videoId,
                        onClick = { vm.play(track) },
                    )
                }
            }

            state.nowPlaying?.let { playing ->
                NowPlayingBar(
                    track = playing,
                    isPlaying = state.isPlaying,
                    onToggle = vm::togglePlayPause,
                )
            }
        }
    }
}

@Composable
private fun TrackRow(track: OnlineTrack, resolving: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            track.thumbnail?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } ?: Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title.ifEmpty { track.videoId }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · ")
                        .ifEmpty { if (track.source == "saavn") "JioSaavn" else "YouTube Music" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (resolving) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
private fun NowPlayingBar(track: OnlineTrack, isPlaying: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title.ifEmpty { track.videoId }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onToggle) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
            }
        }
    }
}
