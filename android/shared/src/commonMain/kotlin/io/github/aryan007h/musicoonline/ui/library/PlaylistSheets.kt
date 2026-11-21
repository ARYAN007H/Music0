package io.github.aryan007h.musicoonline.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.UserPlaylist
import io.github.aryan007h.musicoonline.platform.formatDouble
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.hapticThud
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import io.github.aryan007h.musicoonline.ui.theme.pressable

/**
 * Dynamic 2×2 cover mosaic for playlists & smart mixes.
 * - 4+ unique thumbnails → crisp 2×2 grid
 * - 1..3 thumbnails → full-bleed first cover
 * - 0 thumbnails → rich palette gradient + note glyph
 */
@Composable
fun PlaylistMosaicArtwork(
    thumbnails: List<String?>,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    accentStart: Color = Musico.extras.primary,
    accentEnd: Color = Musico.extras.support,
) {
    val distinct = remember(thumbnails) {
        thumbnails.filterNotNull().filter { it.isNotBlank() }.distinct()
    }
    val shape = RoundedCornerShape(cornerRadius.coerceAtLeast(0.dp))
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(Brush.linearGradient(listOf(accentStart.copy(alpha = 0.85f), accentEnd.copy(alpha = 0.85f)))),
        contentAlignment = Alignment.Center,
    ) {
        when {
            distinct.size >= 4 -> {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        MosaicCell(distinct[0], Modifier.weight(1f).fillMaxHeight())
                        MosaicCell(distinct[1], Modifier.weight(1f).fillMaxHeight())
                    }
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        MosaicCell(distinct[2], Modifier.weight(1f).fillMaxHeight())
                        MosaicCell(distinct[3], Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
            distinct.isNotEmpty() -> {
                AsyncImage(
                    model = distinct.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                Icon(
                    Icons.Filled.LibraryMusic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
private fun MosaicCell(url: String, modifier: Modifier) {
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}

/** Validated dialog for creating or renaming a user playlist. */
@Composable
fun CreateOrRenamePlaylistDialog(
    title: String,
    confirmLabel: String,
    initialName: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var draft by remember(initialName) { mutableStateOf(initialName) }
    val valid = draft.trim().isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(120) },
                placeholder = { Text("Late night drives, Gym PRs…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (valid) {
                            onConfirm(draft.trim())
                            onDismiss()
                        }
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onConfirm(draft.trim())
                    onDismiss()
                },
            ) {
                Text(confirmLabel, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/**
 * Modal bottom sheet to add a track (or batch of tracks) to any existing
 * playlist, or create a brand-new playlist and add immediately in one flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    tracks: List<OnlineTrack>,
    playlists: List<UserPlaylist>,
    onDismiss: () -> Unit,
    onAddToPlaylist: (playlistId: String, tracks: List<OnlineTrack>) -> Unit,
    onCreateAndAdd: (name: String, tracks: List<OnlineTrack>) -> Unit,
) {
    if (tracks.isEmpty()) return
    var showCreateDialog by remember { mutableStateOf(false) }
    val primaryTrack = tracks.first()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MusicoShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(bottom = 28.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    url = primaryTrack.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.size(46.dp),
                    cornerRadius = 12.dp,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (tracks.size == 1) "Add to playlist" else "Add ${tracks.size} songs to playlist",
                        style = Musico.type.title,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (tracks.size == 1) {
                            "${primaryTrack.title.ifEmpty { "Untitled" }} · ${primaryTrack.artist.ifEmpty { "Unknown artist" }}"
                        } else {
                            "${primaryTrack.title} and ${tracks.size - 1} more"
                        },
                        style = Musico.type.caption,
                        color = Musico.extras.inkDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .pressable(
                        onClick = {
                            hapticTick()
                            showCreateDialog = true
                        },
                        pressedScale = 0.98f,
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "New playlist",
                        style = Musico.type.title,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Create and add right away",
                        style = Musico.type.caption,
                        color = Musico.extras.inkDim,
                    )
                }
            }

            if (playlists.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((playlists.size.coerceAtMost(5) * 68).dp),
                ) {
                    items(playlists, key = { it.id }) { pl ->
                        val trackIdsInPlaylist = remember(pl.tracks) { pl.tracks.map { it.id }.toSet() }
                        val alreadyPresent = tracks.size == 1 && primaryTrack.videoId in trackIdsInPlaylist
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressable(
                                    onClick = {
                                        hapticThud()
                                        onAddToPlaylist(pl.id, tracks)
                                        onDismiss()
                                    },
                                    pressedScale = 0.98f,
                                )
                                .padding(horizontal = 24.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PlaylistMosaicArtwork(
                                thumbnails = pl.tracks.map { it.thumbnail },
                                modifier = Modifier.size(48.dp),
                                cornerRadius = 12.dp,
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    pl.name,
                                    style = Musico.type.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    "${pl.tracks.size} song${if (pl.tracks.size == 1) "" else "s"}",
                                    style = Musico.type.tabularCaption,
                                    color = Musico.extras.inkDim,
                                )
                            }
                            if (alreadyPresent) {
                                Box(
                                    Modifier
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Musico.extras.mint.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Already in playlist",
                                        tint = Musico.extras.mint,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Musico.extras.inkFaint, modifier = Modifier.size(18.dp))
                        Text(
                            "No playlists yet — create your first above",
                            style = Musico.type.caption,
                            color = Musico.extras.inkDim,
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateOrRenamePlaylistDialog(
            title = "New playlist",
            confirmLabel = "Create & Add",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreateAndAdd(name, tracks)
                onDismiss()
            },
        )
    }
}

// ── Shared metadata formatters (tabular, unboxed, KMP-pure) ───────────────

fun formatBytesHuman(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return when {
        mb >= 1024.0 -> "${formatDouble(mb / 1024.0, 1)} GB"
        mb >= 10.0 -> "${formatDouble(mb, 0)} MB"
        else -> "${formatDouble(mb, 1)} MB"
    }
}

fun formatDurationSummary(tracks: List<OnlineTrack>): String? {
    val totalSecs = tracks.mapNotNull { it.durationSecs }.sum()
    if (totalSecs <= 0L) return null
    val hours = totalSecs / 3600
    val mins = (totalSecs % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${mins}m"
        mins > 0 -> "$mins min"
        else -> "${totalSecs}s"
    }
}

fun formatTrackCount(count: Int): String = "$count ${if (count == 1) "track" else "tracks"}"

fun formatSongCount(count: Int): String = "$count ${if (count == 1) "song" else "songs"}"

fun formatDownloadsSummary(count: Int, totalBytes: Long): String = when {
    count <= 0 -> "No cached tracks"
    totalBytes > 0L -> "${formatSongCount(count)} · ${formatBytesHuman(totalBytes)}"
    else -> formatSongCount(count)
}

