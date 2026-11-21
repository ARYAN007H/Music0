package io.github.aryan007h.musicoonline.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import io.github.aryan007h.musicoonline.ui.theme.pressable
import io.github.aryan007h.musicoonline.ui.theme.staggerIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackActionSheet(
    track: OnlineTrack,
    isLiked: Boolean,
    saving: Boolean,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
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
            SheetAction(Icons.Filled.PlaylistAdd, "Add to playlist", index = 0) {
                onDismiss()
                onAddToPlaylist()
            }
            SheetAction(Icons.Filled.PlayArrow, "Play next", index = 1) { onPlayNext(); onDismiss() }
            SheetAction(Icons.Filled.Queue, "Add to queue", index = 2) { onAddToQueue(); onDismiss() }
            SheetAction(
                Icons.Filled.Download,
                if (saving) "Saving offline…" else "Save offline",
                index = 3,
            ) { onSaveOffline(); onDismiss() }
            SheetAction(
                if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (isLiked) "Remove from liked" else "Save to liked",
                index = 4,
            ) { onToggleLike(); onDismiss() }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, index: Int = 0, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .staggerIn(index = index, drop = 10.dp)
            .pressable(onClick = onClick, pressedScale = 0.97f)
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
