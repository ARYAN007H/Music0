package io.github.aryan007h.musicoonline.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.aryan007h.musicoonline.core.MusicoCore
import io.github.aryan007h.musicoonline.platform.DesktopPlatformHost
import io.github.aryan007h.musicoonline.platform.DesktopPlatformPrefs
import io.github.aryan007h.musicoonline.platform.DesktopPlatformUpdater
import io.github.aryan007h.musicoonline.platform.DesktopPlayback
import io.github.aryan007h.musicoonline.platform.PlatformEqualizer
import io.github.aryan007h.musicoonline.ui.AppOverlay
import io.github.aryan007h.musicoonline.ui.AppTab
import io.github.aryan007h.musicoonline.ui.MusicViewModel
import io.github.aryan007h.musicoonline.ui.MusicoApp
import kotlinx.coroutines.delay
import java.awt.Dimension
import java.io.File

/**
 * Desktop JVM entry point for Music0 — Expressive Material 3 Compose Multiplatform.
 *
 * Shares 100% of UI, ViewModel, Lyrics Studio, Library/Playlists, and Rust JNI
 * core with Android while presenting an adaptive Desktop Left Navigation Rail,
 * Docked Player Bar, keyboard shortcuts, and low-RAM G1GC runtime.
 */
fun main() = application {
    val dataDir = DesktopPlatformHost.dataDir()
    val cacheDir = DesktopPlatformHost.cacheDir()

    MusicoCore.init(dataDir.absolutePath, cacheDir.absolutePath)

    val playback = remember { DesktopPlayback() }
    val prefs = remember {
        DesktopPlatformPrefs(File(dataDir, "ui_prefs.json")).also {
            PlatformEqualizer.init(it)
        }
    }
    val updater = remember { DesktopPlatformUpdater() }
    val vm = remember { MusicViewModel(playback = playback, prefs = prefs, updater = updater) }

    val state by vm.state.collectAsState()
    val windowState = rememberWindowState(
        size = DpSize(1280.dp, 820.dp),
        position = WindowPosition.Aligned(Alignment.Center),
    )

    val windowTitle = remember(state.nowPlaying?.title, state.nowPlaying?.artist, state.isPlaying) {
        val track = state.nowPlaying
        if (track != null && track.title.isNotBlank()) {
            val prefix = if (state.isPlaying) "▶ " else ""
            val artistPart = track.artist.takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
            "$prefix${track.title}$artistPart · Music0"
        } else {
            "Music0"
        }
    }

    Window(
        onCloseRequest = {
            vm.clear()
            playback.release()
            exitApplication()
        },
        state = windowState,
        title = windowTitle,
        // Tunnel phase: ONLY global Search jump shortcuts (Ctrl+K / Ctrl+F) so focused
        // OutlinedTextFields (Search, Rename, Onboarding) keep Ctrl+Arrows, Space, Escape, etc.
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) return@Window false
            val mod = event.isCtrlPressed || event.isMetaPressed
            when {
                mod && (event.key == Key.K || event.key == Key.F) -> {
                    vm.closeOverlay()
                    vm.selectTab(AppTab.SEARCH)
                    true
                }
                else -> false
            }
        },
        // Bubble phase: fires only when no focused text input / dialog consumed the key.
        onKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) return@Window false
            val current = vm.state.value
            val mod = event.isCtrlPressed || event.isMetaPressed
            when {
                event.key == Key.Escape -> {
                    when (current.overlay) {
                        AppOverlay.DETAIL -> { vm.popDetail(); true }
                        AppOverlay.COLLECTION -> { vm.closeCollection(); true }
                        AppOverlay.PLAYER, AppOverlay.SETTINGS, AppOverlay.PROFILE, AppOverlay.EQ -> {
                            vm.closeOverlay(); true
                        }
                        else -> false
                    }
                }
                mod && event.key == Key.One -> { vm.closeOverlay(); vm.selectTab(AppTab.HOME); true }
                mod && event.key == Key.Two -> { vm.closeOverlay(); vm.selectTab(AppTab.RADIO); true }
                mod && event.key == Key.Three -> { vm.closeOverlay(); vm.selectTab(AppTab.LIBRARY); true }
                mod && event.key == Key.Four -> { vm.closeOverlay(); vm.selectTab(AppTab.SEARCH); true }
                mod && event.key == Key.L -> {
                    if (current.overlay == AppOverlay.PLAYER) vm.closeOverlay()
                    else if (current.nowPlaying != null) vm.openPlayer(initialPage = 1)
                    true
                }
                (mod || (!event.isAltPressed && !event.isShiftPressed)) && event.key == Key.Spacebar -> {
                    if (current.nowPlaying != null) {
                        vm.onTogglePlayback()
                        true
                    } else false
                }
                mod && event.key == Key.DirectionRight -> {
                    vm.next()
                    true
                }
                mod && event.key == Key.DirectionLeft -> {
                    vm.previous()
                    true
                }
                mod && event.key == Key.DirectionUp -> {
                    vm.setVolume((current.volume + 0.08f).coerceIn(0f, 1f))
                    true
                }
                mod && event.key == Key.DirectionDown -> {
                    vm.setVolume((current.volume - 0.08f).coerceIn(0f, 1f))
                    true
                }
                else -> false
            }
        },
    ) {
        LaunchedEffect(Unit) {
            window.minimumSize = Dimension(520, 560)
        }

        var toastMessage by remember { mutableStateOf<String?>(null) }
        var showCoreWarning by remember { mutableStateOf(!MusicoCore.isLoaded) }
        var isDarkTheme by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            DesktopPlatformHost.toasts.collect { msg ->
                toastMessage = msg
                delay(2800)
                if (toastMessage == msg) toastMessage = null
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            MusicoApp(
                vm = vm,
                onDarkResolved = { isDarkTheme = it },
            )

            // Actionable banner when the native Rust library hasn't been compiled yet
            AnimatedVisibility(
                visible = showCoreWarning && !MusicoCore.isLoaded,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = 24.dp, end = 24.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = if (isDarkTheme) Color(0xFF2B1C24) else Color(0xFFFFEDE9),
                    tonalElevation = 8.dp,
                    shadowElevation = 10.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = if (isDarkTheme) Color(0xFFFFB4AB) else Color(0xFFB3261E),
                        )
                        Text(
                            text = MusicoCore.loadError
                                ?: "Build native Rust core once: cargo build --release -p musico_core --lib",
                            color = if (isDarkTheme) Color(0xFFFFDAD6) else Color(0xFF410E0B),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { showCoreWarning = false }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Dismiss",
                                tint = if (isDarkTheme) Color(0xFFFFDAD6) else Color(0xFF410E0B),
                            )
                        }
                    }
                }
            }

            // Desktop floating M3 Expressive toast
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isDarkTheme) Color(0xFF242135).copy(alpha = 0.96f) else Color(0xFF1C1A24).copy(alpha = 0.94f),
                    shadowElevation = 12.dp,
                ) {
                    Text(
                        text = toastMessage.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 11.dp),
                    )
                }
            }
        }
    }
}
