package io.github.aryan007h.musicoonline.ui.settings

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aryan007h.musicoonline.core.UpdateInfo
import io.github.aryan007h.musicoonline.platform.isDesktopPlatform
import io.github.aryan007h.musicoonline.ui.LyricBackdrop
import io.github.aryan007h.musicoonline.ui.components.ConnectedButtonOption
import io.github.aryan007h.musicoonline.ui.components.DesktopScrollableChipRow
import io.github.aryan007h.musicoonline.ui.components.ExpressiveConnectedButtonGroup
import io.github.aryan007h.musicoonline.ui.components.ExpressiveFilterChip
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSegmentedColumn
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSegmentedItem
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSlider
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSwitch
import io.github.aryan007h.musicoonline.ui.components.musicoTopInset
import io.github.aryan007h.musicoonline.ui.components.musicoWash
import io.github.aryan007h.musicoonline.ui.theme.ExpressiveShapes
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.Palette
import io.github.aryan007h.musicoonline.ui.theme.ThemeMode
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import io.github.aryan007h.musicoonline.ui.theme.pressScale
import kotlin.math.roundToInt

/**
 * Unified Material 3 Expressive Settings screen across Android & Desktop:
 * - Segmented list cards (24dp outer / 6dp inner corners, 3dp gaps)
 * - Connected button group for System / Light / Dark Theme Mode
 * - Live Light/Dark color palette swatches with scalloped Cookie8 active badge
 * - Shape-morphing ExpressiveFilterChip pickers for Backdrops, Quality, and Speed
 */
@Composable
fun SettingsScreen(
    coreVersion: String?,
    lyricBackdrop: LyricBackdrop,
    palette: Palette,
    themeMode: ThemeMode,
    offlineMode: Boolean,
    autoplay: Boolean,
    streamingQuality: String,
    cacheMb: Int?,
    cacheBytes: Long?,
    settingsSaving: Boolean,
    onBack: () -> Unit,
    onBackdropChange: (LyricBackdrop) -> Unit,
    onPaletteChange: (Palette) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
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
    onImportPlaylist: () -> Unit = {},
    updateInfo: UpdateInfo?,
    updateChecking: Boolean,
    updateDownloading: Boolean,
    updateDownloadedMb: Long,
    updateTotalMb: Long,
    onCheckUpdates: () -> Unit,
    onUpdateNow: () -> Unit,
) {
    LaunchedEffect(Unit) { onRefreshCache() }
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
                    intensity = if (isDark) 0.20f else 0.14f,
                ),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .musicoTopInset()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
                    Text(
                        "Settings",
                        style = Musico.type.display,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }

            item(key = "group-appearance") {
                SettingsSection(title = "Appearance") {
                    ExpressiveSegmentedColumn {
                        ExpressiveSegmentedItem(index = 0, count = 2, containerColor = cardBg) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SettingIcon(Icons.Filled.Contrast)
                                    Spacer(Modifier.size(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Theme mode",
                                            style = Musico.type.title,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            when (themeMode) {
                                                ThemeMode.SYSTEM -> "Follows device light and dark theme"
                                                ThemeMode.LIGHT -> "Bright expressive tonal surfaces"
                                                ThemeMode.DARK -> "Deep low-glare studio surfaces"
                                            },
                                            style = Musico.type.caption,
                                            color = Musico.extras.inkDim,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                ThemeModeSelector(
                                    current = themeMode,
                                    onSelect = onThemeModeChange,
                                )
                            }
                        }

                        ExpressiveSegmentedItem(index = 1, count = 2, containerColor = cardBg) {
                            Column(Modifier.padding(vertical = 12.dp)) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingIcon(Icons.Filled.Palette)
                                    Spacer(Modifier.size(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Color palette",
                                            style = Musico.type.title,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            "${palette.label} · ${palette.blurb}",
                                            style = Musico.type.caption,
                                            color = Musico.extras.inkDim,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                PalettePicker(
                                    current = palette,
                                    isDark = isDark,
                                    edgeFadeColor = cardBg,
                                    onPick = onPaletteChange,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "group-lyrics") {
                SettingsSection(title = "Now Playing") {
                    ExpressiveSegmentedColumn {
                        ExpressiveSegmentedItem(index = 0, count = 1, containerColor = cardBg) {
                            Column(Modifier.padding(vertical = 14.dp)) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingIcon(Icons.Filled.Lyrics)
                                    Spacer(Modifier.size(14.dp))
                                    Column {
                                        Text(
                                            "Lyrics backdrop",
                                            style = Musico.type.title,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            "Geometric artwork stage behind Now Playing & Lyrics",
                                            style = Musico.type.caption,
                                            color = Musico.extras.inkDim,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                BackdropChooser(
                                    current = lyricBackdrop,
                                    edgeFadeColor = cardBg,
                                    onPick = onBackdropChange,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
            }

            item(key = "group-playback") {
                val itemCount = if (isDesktopPlatform) 4 else 6
                var idx = 0
                SettingsSection(title = "Playback") {
                    ExpressiveSegmentedColumn {
                        ExpressiveSegmentedItem(index = idx++, count = itemCount, containerColor = cardBg) {
                            Column(Modifier.padding(vertical = 14.dp)) {
                                Row(
                                    Modifier.padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingIcon(Icons.AutoMirrored.Filled.VolumeUp)
                                    Spacer(Modifier.size(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Streaming quality",
                                            style = Musico.type.title,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            if (settingsSaving) "Saving…" else "Applies to new resolutions",
                                            style = Musico.type.caption,
                                            color = Musico.extras.inkDim,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                QualityChips(
                                    current = streamingQuality,
                                    enabled = !settingsSaving,
                                    edgeFadeColor = cardBg,
                                    onPick = onQualityChange,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }

                        ExpressiveSegmentedItem(index = idx++, count = itemCount, containerColor = cardBg) {
                            SwitchSettingRow(
                                icon = Icons.Filled.AirplanemodeActive,
                                title = "Airplane Mode",
                                subtitle = "Keep the core offline — cached music only",
                                checked = offlineMode,
                                enabled = !settingsSaving,
                                onToggle = onOfflineChange,
                            )
                        }

                        ExpressiveSegmentedItem(index = idx++, count = itemCount, containerColor = cardBg) {
                            SwitchSettingRow(
                                icon = Icons.Filled.GraphicEq,
                                title = "Autoplay",
                                subtitle = "Keep the music going when the queue runs dry",
                                checked = autoplay,
                                enabled = !settingsSaving,
                                onToggle = onAutoplayChange,
                            )
                        }

                        if (!isDesktopPlatform) {
                            ExpressiveSegmentedItem(index = idx++, count = itemCount, containerColor = cardBg) {
                                SwitchSettingRow(
                                    icon = Icons.Filled.SkipNext,
                                    title = "Skip silence",
                                    subtitle = "Jump over quiet stretches automatically",
                                    checked = skipSilence,
                                    onToggle = onSkipSilenceChange,
                                )
                            }

                            ExpressiveSegmentedItem(index = idx++, count = itemCount, containerColor = cardBg) {
                                Column(Modifier.padding(vertical = 14.dp)) {
                                    Row(
                                        Modifier.padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        SettingIcon(Icons.Filled.Speed)
                                        Spacer(Modifier.size(14.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                "Playback speed",
                                                style = Musico.type.title,
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                "Applies to everything you play",
                                                style = Musico.type.caption,
                                                color = Musico.extras.inkDim,
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    SpeedChips(
                                        current = playbackSpeed,
                                        edgeFadeColor = cardBg,
                                        onPick = onSpeedChange,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                }
                            }
                        }

                        ExpressiveSegmentedItem(
                            index = idx,
                            count = itemCount,
                            containerColor = cardBg,
                            onClick = onOpenEq,
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SettingIcon(Icons.Filled.Equalizer)
                                Spacer(Modifier.size(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Equalizer",
                                        style = Musico.type.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        if (isDesktopPlatform) "Live Rust DSP bands, presets & bass boost"
                                        else "Band controls, presets, bass boost",
                                        style = Musico.type.caption,
                                        color = Musico.extras.inkDim,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                ) {
                                    Text(
                                        "Open",
                                        style = Musico.type.caption,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "group-library") {
                SettingsSection(title = "Library") {
                    ExpressiveSegmentedColumn {
                        ExpressiveSegmentedItem(
                            index = 0,
                            count = 1,
                            containerColor = cardBg,
                            onClick = onImportPlaylist,
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SettingIcon(Icons.Filled.PlaylistAdd)
                                Spacer(Modifier.size(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Import playlist",
                                        style = Musico.type.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        "Spotify or Apple Music link, or CSV / JSON / M3U file",
                                        style = Musico.type.caption,
                                        color = Musico.extras.inkDim,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        "Import",
                                        style = Musico.type.caption,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "group-storage") {
                val persistedCapMb = (cacheMb ?: 1024).coerceIn(CACHE_CAP_MIN_MB, CACHE_CAP_MAX_MB)
                var dragging by remember { mutableStateOf(false) }
                var dragMb by remember { mutableFloatStateOf(persistedCapMb.toFloat()) }
                LaunchedEffect(persistedCapMb) {
                    if (!dragging) dragMb = persistedCapMb.toFloat()
                }
                val displayedCapMb = if (dragging) snapCacheCapMb(dragMb) else persistedCapMb

                SettingsSection(title = "Storage") {
                    ExpressiveSegmentedColumn {
                        ExpressiveSegmentedItem(index = 0, count = 2, containerColor = cardBg) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    SettingIcon(Icons.Filled.Storage)
                                    Spacer(Modifier.size(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "Stream cache",
                                            style = Musico.type.title,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            cacheBytes?.let { used ->
                                                "${used / (1024 * 1024)} MB used · cap $displayedCapMb MB"
                                            } ?: "Cap $displayedCapMb MB",
                                            style = Musico.type.caption,
                                            color = Musico.extras.inkDim,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                ExpressiveSlider(
                                    value = dragMb.coerceIn(CACHE_CAP_MIN_MB.toFloat(), CACHE_CAP_MAX_MB.toFloat()),
                                    onValueChange = { raw ->
                                        dragging = true
                                        dragMb = raw
                                    },
                                    onValueChangeFinished = {
                                        val snapped = snapCacheCapMb(dragMb)
                                        dragMb = snapped.toFloat()
                                        dragging = false
                                        if (snapped != persistedCapMb) {
                                            onCacheCapChange(snapped)
                                        }
                                    },
                                    valueRange = CACHE_CAP_MIN_MB.toFloat()..CACHE_CAP_MAX_MB.toFloat(),
                                    enabled = !settingsSaving || dragging,
                                    trackHeight = 14.dp,
                                    thumbHeight = 30.dp,
                                    barHeight = 38.dp,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Cache cap: $displayedCapMb MB",
                                    style = Musico.type.tabularCaption,
                                    color = Musico.extras.inkDim,
                                )
                            }
                        }

                        ExpressiveSegmentedItem(
                            index = 1,
                            count = 2,
                            containerColor = cardBg,
                            onClick = onClearCache,
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SettingIcon(Icons.Filled.DeleteSweep)
                                Spacer(Modifier.size(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Clear cache",
                                        style = Musico.type.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        "Frees every cached stream",
                                        style = Musico.type.caption,
                                        color = Musico.extras.inkDim,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "group-updates") {
                SettingsSection(title = "App Updates") {
                    ExpressiveSegmentedColumn {
                        ExpressiveSegmentedItem(index = 0, count = 2, containerColor = cardBg) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                SettingIcon(Icons.Filled.SystemUpdateAlt)
                                Spacer(Modifier.size(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Current version",
                                        style = Musico.type.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        updateInfo?.current ?: "checking…",
                                        style = Musico.type.caption,
                                        color = Musico.extras.inkDim,
                                    )
                                }
                            }
                        }

                        when {
                            updateDownloading -> ExpressiveSegmentedItem(index = 1, count = 2, containerColor = cardBg) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            updateInfo?.updateAvailable == true -> ExpressiveSegmentedItem(
                                index = 1,
                                count = 2,
                                containerColor = cardBg,
                                onClick = onUpdateNow,
                            ) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                                if (isDesktopPlatform) "Open GitHub release page"
                                                else "${updateInfo.assetSizeBytes / (1024 * 1024)} MB — taps straight into the installer",
                                                style = Musico.type.caption,
                                                color = Musico.extras.inkDim,
                                            )
                                        }
                                    }
                                    if (updateInfo.notes.isNotBlank()) {
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            updateInfo.notes,
                                            style = Musico.type.caption,
                                            color = Musico.extras.inkDim,
                                        )
                                    }
                                }
                            }
                            else -> ExpressiveSegmentedItem(
                                index = 1,
                                count = 2,
                                containerColor = cardBg,
                                onClick = if (!updateChecking) onCheckUpdates else null,
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    SettingIcon(Icons.Filled.Sync)
                                    Spacer(Modifier.size(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (updateChecking) "Checking…" else "Check for updates",
                                            style = Musico.type.title,
                                            color = MaterialTheme.colorScheme.onSurface,
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
                }
            }

            item(key = "group-about") {
                SettingsSection(title = "About") {
                    ExpressiveSegmentedColumn {
                        ExpressiveSegmentedItem(index = 0, count = 1, containerColor = cardBg) {
                            InfoSettingRow(
                                icon = Icons.Filled.Palette,
                                title = "Music0",
                                subtitle = (if (isDesktopPlatform) "Desktop" else "Android") +
                                    (coreVersion?.let { " · v$it" } ?: ""),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeModeSelector(
    current: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = remember {
        listOf(
            ConnectedButtonOption(ThemeMode.SYSTEM, "System", Icons.Filled.BrightnessAuto),
            ConnectedButtonOption(ThemeMode.LIGHT, "Light", Icons.Filled.LightMode),
            ConnectedButtonOption(ThemeMode.DARK, "Dark", Icons.Filled.DarkMode),
        )
    }
    ExpressiveConnectedButtonGroup(
        options = options,
        selected = current,
        onSelect = onSelect,
        modifier = modifier,
        height = 44.dp,
        emphasized = true,
        unselectedColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun PalettePicker(
    current: Palette,
    isDark: Boolean,
    edgeFadeColor: Color,
    onPick: (Palette) -> Unit,
) {
    DesktopScrollableChipRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        edgeFadeColor = edgeFadeColor,
    ) {
        Palette.ALL.forEach { p ->
            val selected = p.id == current.id
            val previewSurface = if (isDark) p.bg2 else p.light.surfaceContainer
            val previewPrimary = if (isDark) p.primary else p.light.primary
            val previewSecondary = if (isDark) p.accent else p.light.secondary
            val previewTertiary = if (isDark) p.pop else p.light.tertiary

            val cardShape = RoundedCornerShape(22.dp)
            val cardSource = remember(p.id) { MutableInteractionSource() }
            val cardColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
            Box(
                modifier = Modifier
                    .pressScale(cardSource, pressedScale = 0.94f)
                    .clip(cardShape)
                    .background(cardColor)
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, MaterialTheme.colorScheme.primary, cardShape)
                        } else {
                            Modifier
                        },
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(
                        interactionSource = cardSource,
                        indication = LocalIndication.current,
                    ) {
                        hapticTick()
                        onPick(p)
                    },
            ) {
                Box(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Swatch(previewSurface)
                            Swatch(previewPrimary)
                            Swatch(previewSecondary)
                            Swatch(previewTertiary)
                            if (selected) {
                                Spacer(Modifier.size(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(ExpressiveShapes.Cookie8)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            p.label,
                            style = Musico.type.title,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = if (selected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
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

private val QualityOptions = listOf("low", "normal", "high", "lossless")

@Composable
private fun QualityChips(
    current: String,
    enabled: Boolean,
    edgeFadeColor: Color,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopScrollableChipRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        edgeFadeColor = edgeFadeColor,
    ) {
        QualityOptions.forEach { q ->
            ExpressiveFilterChip(
                label = q.replaceFirstChar { it.uppercase() },
                selected = current.equals(q, ignoreCase = true),
                enabled = enabled,
                onClick = { onPick(q) },
            )
        }
    }
}

private val SpeedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)

@Composable
private fun SpeedChips(
    current: Float,
    edgeFadeColor: Color,
    onPick: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopScrollableChipRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        edgeFadeColor = edgeFadeColor,
    ) {
        SpeedOptions.forEach { speed ->
            ExpressiveFilterChip(
                label = if (speed == 1f) "1×" else "${speed}×",
                selected = current == speed,
                onClick = { onPick(speed) },
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = Musico.type.section,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Musico.type.title, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = Musico.type.caption, color = Musico.extras.inkDim)
        }
        Spacer(Modifier.size(12.dp))
        ExpressiveSwitch(
            checked = checked,
            onCheckedChange = onToggle,
            enabled = enabled,
        )
    }
}

@Composable
private fun InfoSettingRow(icon: ImageVector, title: String, subtitle: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingIcon(icon)
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = Musico.type.title, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = Musico.type.caption, color = Musico.extras.inkDim)
        }
    }
}

@Composable
private fun BackdropChooser(
    current: LyricBackdrop,
    edgeFadeColor: Color,
    onPick: (LyricBackdrop) -> Unit,
    modifier: Modifier = Modifier,
) {
    DesktopScrollableChipRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        edgeFadeColor = edgeFadeColor,
    ) {
        LyricBackdrop.entries.forEach { backdrop ->
            ExpressiveFilterChip(
                label = backdrop.label,
                selected = current == backdrop,
                onClick = { onPick(backdrop) },
            )
        }
    }
}

internal const val CACHE_CAP_MIN_MB = 512
internal const val CACHE_CAP_MAX_MB = 5120
internal const val CACHE_CAP_STEP_MB = 256

internal fun snapCacheCapMb(rawMb: Float): Int =
    ((rawMb / CACHE_CAP_STEP_MB).roundToInt() * CACHE_CAP_STEP_MB)
        .coerceIn(CACHE_CAP_MIN_MB, CACHE_CAP_MAX_MB)
