package io.github.aryan007h.musicoonline.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.ui.LyricBackdrop
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes

/**
 * Well-organised settings: four groups — Appearance, Now Playing, Playback,
 * About. Core-backed settings round-trip through `settings.save`; look &
 * feel prefs stay local.
 */
@Composable
fun SettingsScreen(
    coreVersion: String?,
    lyricBackdrop: LyricBackdrop,
    offlineMode: Boolean,
    autoplay: Boolean,
    streamingQuality: String,
    cacheMb: Int?,
    settingsSaving: Boolean,
    onBack: () -> Unit,
    onBackdropChange: (LyricBackdrop) -> Unit,
    onOfflineChange: (Boolean) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
) {
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
                Text(
                    "Settings",
                    style = Musico.type.display,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // ── Now Playing: lyric backdrop choice ───────────────────────────
        item(key = "group-lyrics") {
            SettingsGroup(title = "Now Playing") {
                SettingRow(
                    icon = Icons.Filled.Lyrics,
                    title = "Lyrics backdrop",
                    subtitle = "The full-screen lyric view's animated background",
                ) {
                    BackdropChooser(
                        current = lyricBackdrop,
                        onPick = onBackdropChange,
                    )
                }
            }
        }

        // ── Playback ─────────────────────────────────────────────────────
        item(key = "group-playback") {
            SettingsGroup(title = "Playback") {
                SwitchSettingRow(
                    icon = Icons.Filled.AirplanemodeActive,
                    title = "Airplane Mode",
                    subtitle = "Keep the core offline — cached and local music only",
                    checked = offlineMode,
                    enabled = !settingsSaving,
                    onToggle = onOfflineChange,
                )
                SwitchSettingRow(
                    icon = Icons.Filled.GraphicEq,
                    title = "Autoplay",
                    subtitle = "Keep the music going when the queue runs dry",
                    checked = autoplay,
                    enabled = !settingsSaving,
                    onToggle = onAutoplayChange,
                )
                InfoSettingRow(
                    icon = Icons.Filled.Speed,
                    title = "Streaming quality",
                    subtitle = streamingQuality.replaceFirstChar { it.uppercase() },
                )
            }
        }

        // ── Storage ──────────────────────────────────────────────────────
        item(key = "group-storage") {
            SettingsGroup(title = "Storage") {
                InfoSettingRow(
                    icon = Icons.Filled.Storage,
                    title = "Stream cache cap",
                    subtitle = cacheMb?.let { "$it MB" } ?: "Default (1 GB)",
                )
            }
        }

        // ── About ────────────────────────────────────────────────────────
        item(key = "group-about") {
            SettingsGroup(title = "About") {
                InfoSettingRow(
                    icon = Icons.Filled.Palette,
                    title = "Music0",
                    subtitle = "Native Kotlin app over the Musico Rust core" +
                        (coreVersion?.let { " • v$it" } ?: ""),
                )
            }
        }
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title.uppercase(),
            style = Musico.type.section,
            color = Musico.extras.inkDim,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )
        Surface(
            shape = MusicoShapes.cardLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 6.dp)) { content() }
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Musico.type.title, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = Musico.type.caption, color = Musico.extras.inkDim)
        }
        Spacer(Modifier.size(12.dp))
        trailing()
    }
}

@Composable
private fun SwitchSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    SettingRow(icon = icon, title = title, subtitle = subtitle) {
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Musico.extras.mint,
                checkedThumbColor = Color(0xFF05291D),
            ),
        )
    }
}

@Composable
private fun InfoSettingRow(icon: ImageVector, title: String, subtitle: String) {
    SettingRow(icon = icon, title = title, subtitle = subtitle) {}
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(9.dp),
        )
    }
}

/** The four backdrop choices as expressive segmented buttons. */
@Composable
private fun BackdropChooser(current: LyricBackdrop, onPick: (LyricBackdrop) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            LyricBackdrop.entries.forEachIndexed { index, backdrop ->
                SegmentedButton(
                    selected = current == backdrop,
                    onClick = { onPick(backdrop) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = LyricBackdrop.entries.size),
                    label = {
                        Text(
                            backdrop.label,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            fontWeight = if (current == backdrop) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                )
            }
        }
    }
}
