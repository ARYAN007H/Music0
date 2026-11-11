package io.github.aryan007h.musicoonline.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import io.github.aryan007h.musicoonline.ui.theme.Palette
import io.github.aryan007h.musicoonline.ui.theme.pressable

/**
 * Settings, redesigned: every row is a control or a door. Groups —
 * Appearance (theme palette + lyric backdrop), Playback (quality chips,
 * airplane, autoplay, Equalizer), Storage (live cache usage, cap slider,
 * clear), About. Core-backed values round-trip through settings.save;
 * look-and-feel prefs stay local.
 */
@Composable
fun SettingsScreen(
    coreVersion: String?,
    lyricBackdrop: LyricBackdrop,
    palette: Palette,
    offlineMode: Boolean,
    autoplay: Boolean,
    streamingQuality: String,
    cacheMb: Int?,
    cacheBytes: Long?,
    settingsSaving: Boolean,
    onBack: () -> Unit,
    onBackdropChange: (LyricBackdrop) -> Unit,
    onPaletteChange: (Palette) -> Unit,
    skipSilence: Boolean,
    playbackSpeed: Float,
    onOfflineChange: (Boolean) -> Unit,
    onAutoplayChange: (Boolean) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onQualityChange: (String) -> Unit,
    onCacheCapChange: (Int) -> Unit,
    onClearCache: () -> Unit,
    onRefreshCache: () -> Unit,
    onOpenEq: () -> Unit,
    updateInfo: io.github.aryan007h.musicoonline.updater.UpdateInfo?,
    updateChecking: Boolean,
    updateDownloading: Boolean,
    updateDownloadedMb: Long,
    updateTotalMb: Long,
    onCheckUpdates: () -> Unit,
    onUpdateNow: () -> Unit,
) {
    LaunchedEffect(Unit) { onRefreshCache() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
                }
                Text("Settings", style = Musico.type.display, color = MaterialTheme.colorScheme.onBackground)
            }
        }

        // ── Appearance ───────────────────────────────────────────────────
        item(key = "group-appearance") {
            SettingsGroup(title = "Appearance") {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingIcon(Icons.Filled.Palette)
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Theme", style = Musico.type.title, color = MaterialTheme.colorScheme.onBackground)
                        Text(palette.blurb, style = Musico.type.caption, color = Musico.extras.inkDim)
                    }
                }
                PalettePicker(current = palette, onPick = onPaletteChange)
            }
        }

        item(key = "group-lyrics") {
            SettingsGroup(title = "Now Playing") {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingIcon(Icons.Filled.Lyrics)
                    Spacer(Modifier.size(14.dp))
                    Column {
                        Text("Lyrics backdrop", style = Musico.type.title, color = MaterialTheme.colorScheme.onBackground)
                        Text("The full-screen lyric stage's background", style = Musico.type.caption, color = Musico.extras.inkDim)
                    }
                }
                BackdropChooser(
                    current = lyricBackdrop,
                    onPick = onBackdropChange,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
        }

        // ── Playback ─────────────────────────────────────────────────────
        item(key = "group-playback") {
            SettingsGroup(title = "Playback") {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingIcon(Icons.AutoMirrored.Filled.VolumeUp)
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Streaming quality", style = Musico.type.title, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            if (settingsSaving) "Saving…" else "Applies to new resolutions",
                            style = Musico.type.caption,
                            color = Musico.extras.inkDim,
                        )
                    }
                }
                QualityChips(
                    current = streamingQuality,
                    enabled = !settingsSaving,
                    onPick = onQualityChange,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )

                SwitchSettingRow(
                    icon = Icons.Filled.AirplanemodeActive,
                    title = "Airplane Mode",
                    subtitle = "Keep the core offline — cached music only",
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
                SwitchSettingRow(
                    icon = Icons.Filled.SkipNext,
                    title = "Skip silence",
                    subtitle = "Jump over quiet stretches automatically",
                    checked = skipSilence,
                    onToggle = onSkipSilenceChange,
                )
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingIcon(Icons.Filled.Speed)
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Playback speed", style = Musico.type.title, color = MaterialTheme.colorScheme.onBackground)
                        Text("Applies to everything you play", style = Musico.type.caption, color = Musico.extras.inkDim)
                    }
                }
                SpeedChips(current = playbackSpeed, onPick = onSpeedChange, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))

                // The door into the equalizer.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(onClick = onOpenEq, pressedScale = 0.98f)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingIcon(Icons.Filled.Equalizer)
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Equalizer", style = Musico.type.title, color = MaterialTheme.colorScheme.onBackground)
                        Text("Band controls, presets, bass boost", style = Musico.type.caption, color = Musico.extras.inkDim)
                    }
                    Text(
                        "OPEN",
                        style = Musico.type.caption,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ── Storage ──────────────────────────────────────────────────────
        item(key = "group-storage") {
            SettingsGroup(title = "Storage") {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingIcon(Icons.Filled.Storage)
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Stream cache", style = Musico.type.title, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            cacheBytes?.let { used ->
                                "${used / (1024 * 1024)} MB used · cap ${cacheMb ?: 1024} MB"
                            } ?: "Cap ${cacheMb ?: 1024} MB",
                            style = Musico.type.caption,
                            color = Musico.extras.inkDim,
                        )
                    }
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Slider(
                        value = (cacheMb ?: 1024).toFloat(),
                        onValueChange = { onCacheCapChange(((it / 256).toInt()) * 256) },
                        valueRange = 512f..5120f,
                        enabled = !settingsSaving,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = Musico.extras.mint,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                    Text(
                        "Cap: ${cacheMb ?: 1024} MB",
                        style = Musico.type.caption,
                        color = Musico.extras.inkDim,
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressable(onClick = onClearCache, pressedScale = 0.98f)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingIcon(Icons.Filled.DeleteSweep)
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Clear cache", style = Musico.type.title, color = MaterialTheme.colorScheme.onBackground)
                        Text("Frees every cached stream", style = Musico.type.caption, color = Musico.extras.inkDim)
                    }
                }
            }
        }

        // ── App updates ──────────────────────────────────────────────────
        item(key = "group-updates") {
            SettingsGroup(title = "App Updates") {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingIcon(Icons.Filled.SystemUpdateAlt)
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Current version", style = Musico.type.title, color = MaterialTheme.colorScheme.onBackground)
                        Text(
                            updateInfo?.current ?: "checking…",
                            style = Musico.type.caption,
                            color = Musico.extras.inkDim,
                        )
                    }
                }
                when {
                    updateDownloading -> Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            progress = { if (updateTotalMb > 0) updateDownloadedMb.toFloat() / updateTotalMb else 0f },
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(14.dp))
                        Text(
                            "Downloading $updateDownloadedMb / $updateTotalMb MB",
                            style = Musico.type.body,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                    updateInfo?.updateAvailable == true -> {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .pressable(onClick = onUpdateNow, pressedScale = 0.98f)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SettingIcon(Icons.Filled.CloudDownload)
                            Spacer(Modifier.size(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "Update to ${updateInfo.latest}",
                                    style = Musico.type.title,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "${updateInfo.assetSizeBytes / (1024 * 1024)} MB — taps straight into the installer",
                                    style = Musico.type.caption,
                                    color = Musico.extras.inkDim,
                                )
                            }
                        }
                        if (updateInfo.notes.isNotBlank()) {
                            Text(
                                updateInfo.notes,
                                style = Musico.type.caption,
                                color = Musico.extras.inkDim,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                    else -> Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable(onClick = onCheckUpdates, enabled = !updateChecking, pressedScale = 0.98f)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SettingIcon(Icons.Filled.Sync)
                        Spacer(Modifier.size(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (updateChecking) "Checking…" else "Check for updates",
                                style = Musico.type.title,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                if (updateInfo?.updateAvailable == false) "You're up to date" else "Checks GitHub releases",
                                style = Musico.type.caption,
                                color = Musico.extras.inkDim,
                            )
                        }
                    }
                }
            }
        }

        // ── About ────────────────────────────────────────────────────────
        item(key = "group-about") {
            SettingsGroup(title = "About") {
                InfoSettingRow(
                    icon = Icons.Filled.Palette,
                    title = "Music0",
                    subtitle = "Native Kotlin over the Musico Rust core" +
                        (coreVersion?.let { " • v$it" } ?: ""),
                )
            }
        }
    }
}

// ── Palette picker: cards with live color swatches ────────────────────────

@Composable
private fun PalettePicker(current: Palette, onPick: (Palette) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Palette.ALL.forEach { p ->
            val selected = p.id == current.id
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .pressable(onClick = { onPick(p) }, pressedScale = 0.93f)
                    .padding(4.dp),
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Swatch(p.bg2)
                        Swatch(p.primary)
                        Swatch(p.accent)
                        Swatch(p.pop)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        p.label,
                        style = Musico.type.title,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color),
    )
}

// ── Quality chips ────────────────────────────────────────────────────────

private val QualityOptions = listOf("low", "normal", "high", "lossless")

@Composable
private fun QualityChips(current: String, enabled: Boolean, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QualityOptions.forEach { q ->
            FilterChipPlain(
                label = q.replaceFirstChar { it.uppercase() },
                selected = current.equals(q, ignoreCase = true),
                enabled = enabled,
                onClick = { onPick(q) },
            )
        }
    }
}

@Composable
private fun FilterChipPlain(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        shape = MusicoShapes.chip,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.pressable(onClick = onClick, enabled = enabled, pressedScale = 0.9f),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

private val SpeedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
private fun SpeedChips(current: Float, onPick: (Float) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SpeedOptions.forEach { speed ->
            FilterChipPlain(
                label = if (speed == 1f) "1×" else "${speed}×",
                selected = current == speed,
                enabled = true,
                onClick = { onPick(speed) },
            )
        }
    }
}

// ── Group scaffolding ────────────────────────────────────────────────────

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

@Composable
private fun SwitchSettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
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
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Musico.extras.mint,
                checkedThumbColor = Musico.extras.onMint,
            ),
        )
    }
}

@Composable
private fun InfoSettingRow(icon: ImageVector, title: String, subtitle: String) {
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
    }
}

// ── Backdrop chooser (fixed layout: full width, no wrap) ─────────────────

@Composable
private fun BackdropChooser(current: LyricBackdrop, onPick: (LyricBackdrop) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
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
