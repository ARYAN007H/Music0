package io.github.aryan007h.musicoonline.ui.profile

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.ListeningStatsReply
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSegmentedColumn
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSegmentedItem
import io.github.aryan007h.musicoonline.ui.components.musicoTopInset
import io.github.aryan007h.musicoonline.ui.components.musicoWash
import io.github.aryan007h.musicoonline.ui.settings.ThemeModeSelector
import io.github.aryan007h.musicoonline.ui.theme.ExpressiveShapes
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.ThemeMode

/**
 * Material 3 Expressive Profile: scalloped Cookie10 identity badge, tonal stats,
 * quick System/Light/Dark appearance switcher, Rust taste profile, and segmented links.
 */
@Composable
fun ProfileScreen(
    displayName: String,
    likedCount: Int,
    playedCount: Int,
    playlistsCount: Int = 0,
    listeningStats: ListeningStatsReply? = null,
    coreVersion: String?,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(displayName) }

    val isDark = Musico.extras.isDark
    val cardBg = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.TopCenter)
                .musicoWash(
                    primary = Musico.extras.primary,
                    secondary = Musico.extras.support,
                    glow = Musico.extras.mint,
                    intensity = if (isDark) 0.22f else 0.14f,
                ),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .musicoTopInset()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(key = "header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
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
                            .size(116.dp)
                            .clip(ExpressiveShapes.Cookie10)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            displayName.trim().take(1).uppercase().ifEmpty { "M" },
                            style = Musico.type.display.copy(fontSize = 46.sp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        displayName,
                        style = Musico.type.headline,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    TextButton(onClick = { editing = true; draft = displayName }) {
                        Text(
                            "Edit name",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item(key = "stats") {
                val totalPlays = listeningStats?.totalPlays?.takeIf { it > 0 } ?: playedCount
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(
                        icon = Icons.Filled.Favorite,
                        value = "$likedCount",
                        label = "Liked",
                        containerColor = cardBg,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Filled.LibraryMusic,
                        value = "$playlistsCount",
                        label = "Playlists",
                        containerColor = cardBg,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        icon = Icons.Filled.History,
                        value = "$totalPlays",
                        label = "Plays",
                        containerColor = cardBg,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            item(key = "quick-appearance") {
                Surface(
                    shape = MusicoShapes.cardLarge,
                    color = cardBg,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "Appearance",
                            style = Musico.type.title,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        ThemeModeSelector(
                            current = themeMode,
                            onSelect = onThemeModeChange,
                        )
                    }
                }
            }

            val topArtists = listeningStats?.topArtists.orEmpty()
            if (topArtists.isNotEmpty()) {
                item(key = "taste-profile") {
                    Surface(
                        shape = MusicoShapes.cardLarge,
                        color = cardBg,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "Taste profile",
                                style = Musico.type.section,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            val mins = ((listeningStats?.totalListenedSecs ?: 0L) / 60L).coerceAtLeast(0L)
                            val streak = listeningStats?.streakDays ?: 0
                            Text(
                                buildList {
                                    if (mins > 0) add("$mins min listened")
                                    if (streak > 0) add("$streak-day streak")
                                    add("${topArtists.size} top artists")
                                }.joinToString(" · "),
                                style = Musico.type.tabularCaption,
                                color = Musico.extras.inkDim,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                topArtists.take(5).joinToString(" · ") { it.artist },
                                style = Musico.type.title,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            item(key = "links") {
                ExpressiveSegmentedColumn {
                    ExpressiveSegmentedItem(
                        index = 0,
                        count = 2,
                        containerColor = cardBg,
                        onClick = onOpenSettings,
                    ) {
                        LinkRowContent(
                            icon = Icons.Filled.Settings,
                            title = "Settings",
                            subtitle = "Appearance, palettes, playback, backdrops, equalizer",
                        )
                    }
                    ExpressiveSegmentedItem(
                        index = 1,
                        count = 2,
                        containerColor = cardBg,
                    ) {
                        LinkRowContent(
                            icon = Icons.Filled.History,
                            title = "About Music0",
                            subtitle = coreVersion?.let { "Version $it" } ?: "Music0",
                        )
                    }
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
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MusicoShapes.cardLarge,
        color = containerColor,
        modifier = modifier,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = Musico.type.headline, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = Musico.type.caption, color = Musico.extras.inkDim)
        }
    }
}

@Composable
private fun LinkRowContent(icon: ImageVector, title: String, subtitle: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Musico.type.title, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = Musico.type.caption, color = Musico.extras.inkDim)
        }
    }
}
