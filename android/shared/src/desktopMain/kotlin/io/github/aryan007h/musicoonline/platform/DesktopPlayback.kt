package io.github.aryan007h.musicoonline.platform

import io.github.aryan007h.musicoonline.core.MusicoCore
import io.github.aryan007h.musicoonline.core.OnlineTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Desktop JVM [PlatformPlayback] backed by the native Rust `musico_playback`
 * engine (symphonia + rubato + cpal + 10-band EQ) exposed over [MusicoCore] JNI.
 */
class DesktopPlayback : PlatformPlayback {

    private val _nowPlaying = MutableStateFlow<OnlineTrack?>(null)
    override val nowPlaying: StateFlow<OnlineTrack?> = _nowPlaying

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs

    private val _isBuffering = MutableStateFlow(false)
    override val isBuffering: StateFlow<Boolean> = _isBuffering

    private val _volume = MutableStateFlow(1f)
    override val volume: StateFlow<Float> = _volume

    private val _trackEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    override val trackEnded: SharedFlow<Unit> = _trackEnded

    private val _gaplessAdvanced = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val gaplessAdvanced: SharedFlow<String> = _gaplessAdvanced

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollJob: Job? = null
    private var stagedNextTrack: OnlineTrack? = null

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                if (MusicoCore.isLoaded) {
                    runCatching {
                        val st = MusicoCore.call("player.state")
                        val playing = st["playing"]?.jsonPrimitive?.booleanOrNull ?: false
                        val buffering = st["buffering"]?.jsonPrimitive?.booleanOrNull ?: false
                        val posMs = st["positionMs"]?.jsonPrimitive?.longOrNull ?: 0L
                        val durMs = st["durationMs"]?.jsonPrimitive?.longOrNull ?: 0L
                        val vol = st["volume"]?.jsonPrimitive?.floatOrNull
                        val ended = st["ended"]?.jsonPrimitive?.booleanOrNull ?: false
                        val endedSongId = st["endedSongId"]?.jsonPrimitive?.contentOrNull
                        val currentSongId = st["currentSongId"]?.jsonPrimitive?.contentOrNull

                        _isPlaying.value = playing
                        _isBuffering.value = buffering
                        if (posMs >= 0L) _positionMs.value = posMs
                        if (durMs > 0L) _durationMs.value = durMs
                        if (vol != null) _volume.value = vol.coerceIn(0f, 1f)

                        val staged = stagedNextTrack
                        if (staged != null && currentSongId == staged.videoId && _nowPlaying.value?.videoId != staged.videoId) {
                            _nowPlaying.value = staged
                            stagedNextTrack = null
                            _positionMs.value = 0L
                            _gaplessAdvanced.tryEmit(staged.videoId)
                        } else if (ended && (endedSongId == null || endedSongId == _nowPlaying.value?.videoId)) {
                            _trackEnded.tryEmit(Unit)
                        }
                    }
                }
                delay(if (_isPlaying.value || _isBuffering.value) POLL_ACTIVE_MS else POLL_IDLE_MS)
            }
        }
    }

    override suspend fun playFile(filePath: String, track: OnlineTrack) {
        stagedNextTrack = null
        _positionMs.value = 0L
        _durationMs.value = (track.durationSecs ?: 0L) * 1000L
        _nowPlaying.value = track
        _isBuffering.value = true
        try {
            MusicoCore.call(
                "player.play_file",
                buildJsonObject {
                    put("id", track.videoId)
                    put("path", filePath)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album)
                    track.durationSecs?.let { put("durationSecs", it.toDouble()) }
                },
            )
            _isPlaying.value = true
            PlatformEqualizer.reapplyToEngine()
            startPolling()
        } catch (t: Throwable) {
            _isBuffering.value = false
            throw t
        }
    }

    override suspend fun stageNextFile(filePath: String, track: OnlineTrack) {
        if (stagedNextTrack?.videoId == track.videoId) return
        stagedNextTrack = track
        runCatching {
            MusicoCore.call(
                "player.preload_next",
                buildJsonObject {
                    put("id", track.videoId)
                    put("path", filePath)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album)
                    track.durationSecs?.let { put("durationSecs", it.toDouble()) }
                },
            )
        }
    }

    override suspend fun clearStagedNext() {
        stagedNextTrack = null
    }

    override suspend fun togglePlayPause() {
        val reply = runCatching { MusicoCore.call("player.toggle") }.getOrNull()
        reply?.get("playing")?.jsonPrimitive?.booleanOrNull?.let { _isPlaying.value = it }
    }

    override suspend fun seekTo(ms: Long) {
        val target = ms.coerceAtLeast(0L)
        _positionMs.value = target
        runCatching {
            MusicoCore.call("player.seek", buildJsonObject { put("positionMs", target) })
        }
    }

    override suspend fun setSpeed(speed: Float) {
        // Desktop Rust engine plays at 1.0x studio rate; flag is retained in prefs.
    }

    override suspend fun setSkipSilence(enabled: Boolean) {
        // Handled natively on Android ExoPlayer; preference persisted cross-platform.
    }

    override suspend fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _volume.value = clamped
        runCatching {
            MusicoCore.call("player.set_volume", buildJsonObject { put("volume", clamped.toDouble()) })
        }
    }

    override fun release() {
        pollJob?.cancel()
        pollJob = null
        if (MusicoCore.isLoaded) {
            runCatching {
                runBlocking {
                    withTimeoutOrNull(300L) {
                        MusicoCore.call("player.stop")
                    }
                }
            }
        }
        scope.cancel()
    }

    private companion object {
        const val POLL_ACTIVE_MS = 150L
        const val POLL_IDLE_MS = 500L
    }
}
