package io.github.aryan007h.musicoonline.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes

/** One row in the long-press sheet. */
private data class TrackAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
)

/**
 * Long-press context sheet for a track: play next, add to queue, save
 * offline, favorite. M3 modal bottom sheet over the current screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionSheet(
    track: OnlineTrack,
    isLiked: Boolean,
    saving: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onSaveOffline: () -> Unit,
    onToggleLike: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = MusicoShapes.sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(bottom = 28.dp)) {
            // Track header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Artwork(
                    url = track.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    cornerRadius = 12.dp,
                )
                Spacer(Modifier.width(14.dp))
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
            }

            Spacer(Modifier.height(6.dp))
            SheetAction(Icons.Filled.PlaylistAdd, "Play next") { onPlayNext(); onDismiss() }
            SheetAction(Icons.Filled.Queue, "Add to queue") { onAddToQueue(); onDismiss() }
            SheetAction(
                Icons.Filled.Download,
                if (saving) "Saving offline…" else "Save offline",
            ) { onSaveOffline(); onDismiss() }
            SheetAction(
                if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (isLiked) "Remove from liked" else "Save to liked",
            ) { onToggleLike(); onDismiss() }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        Text(
            label,
            style = Musico.type.body,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
