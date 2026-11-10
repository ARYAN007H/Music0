package io.github.aryan007h.musicoonline.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes

/**
 * Profile: who's listening, a couple of session stats, and quick links.
 * Light on purpose — identity stays on-device.
 */
@Composable
fun ProfileScreen(
    displayName: String,
    likedCount: Int,
    playedCount: Int,
    coreVersion: String?,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(displayName) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Text("Profile", style = Musico.type.display, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        item(key = "identity") {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF7C5CFF),
                                    Color(0xFF53F2C4),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        displayName.trim().take(1).uppercase().ifEmpty { "M" },
                        style = Musico.type.display.copy(fontSize = 44.sp),
                        color = Color.White,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    displayName,
                    style = Musico.type.display.copy(fontSize = 26.sp),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                TextButton(onClick = { editing = true; draft = displayName }) {
                    Text("Edit name", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item(key = "stats") {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StatCard(
                    icon = Icons.Filled.Favorite,
                    value = "$likedCount",
                    label = "Liked",
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    icon = Icons.Filled.History,
                    value = "$playedCount",
                    label = "Played",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "links") {
            Surface(
                shape = MusicoShapes.cardLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    LinkRow(
                        icon = Icons.Filled.Settings,
                        title = "Settings",
                        subtitle = "Appearance, playback, backdrops",
                        onClick = onOpenSettings,
                    )
                    LinkRow(
                        icon = Icons.Filled.History,
                        title = "About the engine",
                        subtitle = "Musico Rust core" + (coreVersion?.let { " • v$it" } ?: ""),
                        onClick = {},
                    )
                }
            }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Your name") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(24) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        onRename(draft.trim().ifEmpty { "Listener" })
                        editing = false
                    }),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(draft.trim().ifEmpty { "Listener" })
                    editing = false
                }) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MusicoShapes.cardLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = Musico.extras.mint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(value, style = Musico.type.display.copy(fontSize = 26.sp), color = MaterialTheme.colorScheme.onBackground)
            Text(label, style = Musico.type.caption, color = Musico.extras.inkDim)
        }
    }
}

@Composable
private fun LinkRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MusicoShapes.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(40.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(9.dp))
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Musico.type.title, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = Musico.type.caption, color = Musico.extras.inkDim)
        }
    }
}
