package io.github.aryan007h.musicoonline.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.UpdateInfo
import io.github.aryan007h.musicoonline.ui.player.ArtworkAtmosphere
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class HapticKind {
    TAP,
    TICK,
    CONFIRM,
    LONG_PRESS,
    REJECT,
}

/**
 * Cross-platform playback backend:
 * - Android: Media3 ExoPlayer + MediaSessionService (lockscreen, Bluetooth, audio focus)
 * - Desktop JVM: Rust `musico_playback::PlaybackEngine` over the shared JNI bridge
 */
interface PlatformPlayback {
    val nowPlaying: StateFlow<OnlineTrack?>
    val isPlaying: StateFlow<Boolean>
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val isBuffering: StateFlow<Boolean>
    val volume: StateFlow<Float>
    val trackEnded: SharedFlow<Unit>
    val gaplessAdvanced: SharedFlow<String>
    val playbackError: SharedFlow<String>

    suspend fun playFile(filePath: String, track: OnlineTrack)
    suspend fun stageNextFile(filePath: String, track: OnlineTrack)
    suspend fun clearStagedNext()
    suspend fun togglePlayPause()
    suspend fun seekTo(ms: Long)
    suspend fun setSpeed(speed: Float)
    suspend fun setSkipSilence(enabled: Boolean)
    suspend fun setVolume(volume: Float)
    fun release()
}

/** Lightweight key-value UI preferences (SharedPreferences on Android, JSON file on Desktop). */
interface PlatformPrefs {
    fun getString(key: String, default: String? = null): String?
    fun putString(key: String, value: String?)
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun getFloat(key: String, default: Float = 0f): Float
    fun putFloat(key: String, value: Float)
    fun getInt(key: String, default: Int = 0): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, default: Long = 0L): Long
    fun putLong(key: String, value: Long)
}

/** Cross-platform 5-band Equalizer snapshot (-1500..1500 mB). */
data class EqSnapshot(
    val enabled: Boolean = false,
    val bandCount: Int = 5,
    val bandLevels: List<Int> = listOf(0, 0, 0, 0, 0),
    val bandMinMb: Int = -1500,
    val bandMaxMb: Int = 1500,
    val bandFreqLabels: List<String> = listOf("60Hz", "230Hz", "910Hz", "3.6k", "14k"),
    val bassBoost: Int = 0,
    val presetName: String? = null,
) {
    val bandRange: IntRange get() = bandMinMb..bandMaxMb
}

expect object PlatformEqualizer {
    fun isAttached(): Boolean
    fun currentState(): EqSnapshot
    fun setEnabled(enabled: Boolean)
    fun setBandLevel(index: Int, milliBel: Int)
    fun setBassBoost(strength: Int)
    fun applyPresetGain(curve: List<Int>, presetName: String)
    fun addListener(listener: () -> Unit)
    fun removeListener(listener: () -> Unit)
}

/** Cross-platform update checker + platform-specific installer / browser opener. */
interface PlatformUpdater {
    fun currentVersion(): String
    suspend fun check(): UpdateInfo
    fun notifyUpdateAvailable(info: UpdateInfo)
    suspend fun downloadAndInstall(
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    )
}

expect val isDesktopPlatform: Boolean

expect fun currentTimeMillis(): Long

expect fun currentLocalHour(): Int

expect fun formatDouble(value: Double, decimals: Int = 1): String

expect fun performHaptic(kind: HapticKind)

expect fun showPlatformToast(message: String)

expect fun openExternalUrl(url: String)

@Composable
expect fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit)

@Composable
expect fun Modifier.platformBlur(radiusDp: Float): Modifier

expect suspend fun decodeArtworkSamplePixels(url: String): IntArray?

expect suspend fun prefetchArtworkImage(url: String)

expect suspend fun shareLyricCard(
    track: OnlineTrack,
    lines: List<String>,
    atmosphere: ArtworkAtmosphere,
): Result<String>

@Composable
expect fun rememberPlaylistFilePicker(
    onPicked: (fileName: String, content: String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit

@Composable
expect fun PlatformHorizontalScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
)

@Composable
expect fun PlatformHorizontalGridScrollbar(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier = Modifier,
)


