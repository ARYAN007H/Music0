package io.github.aryan007h.musicoonline.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.platform.PlatformPlayback
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
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android [PlatformPlayback] backed by Media3 [MediaController] + [PlaybackService].
 * Supports true 0 ms gapless next-track staging and hardware audio focus.
 */
class PlaybackController(private val context: Context) : PlatformPlayback {

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

    private val _playbackError = MutableSharedFlow<String>(extraBufferCapacity = 4)
    override val playbackError: SharedFlow<String> = _playbackError

    private var controller: MediaController? = null
    @Volatile
    private var released = false
    private val obtainMutex = Mutex()
    private var pollJob: Job? = null
    private var stagedNextTrack: OnlineTrack? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        // Pre-bind PlaybackService on app launch so the first track tap doesn't
        // pay Media3 service-connection startup latency.
        scope.launch {
            runCatching { obtain() }
        }
    }

    private suspend fun obtain(): MediaController {
        check(!released) { "PlaybackController has been released" }
        controller?.let { return it }
        return obtainMutex.withLock {
            check(!released) { "PlaybackController has been released" }
            controller ?: withContext(Dispatchers.Main.immediate) {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                MediaController.Builder(context, token).buildAsync().await().also { c ->
                    if (released) {
                        c.release()
                        error("PlaybackController released during bind")
                    }
                    controller = c
                    c.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isPlaying.value = isPlaying
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            _isBuffering.value = playbackState == Player.STATE_BUFFERING
                            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
                                _isPlaying.value = false
                            }
                            if (playbackState == Player.STATE_ENDED) {
                                _trackEnded.tryEmit(Unit)
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            Log.w(TAG, "ExoPlayer error (${error.errorCodeName}): ${error.message}", error)
                            _isBuffering.value = false
                            _isPlaying.value = false
                            _playbackError.tryEmit(
                                error.localizedMessage ?: error.message ?: "Playback error (${error.errorCodeName})",
                            )
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            _positionMs.value = 0
                            _durationMs.value = 0
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && mediaItem != null) {
                                val transitionedId = mediaItem.mediaId
                                val staged = stagedNextTrack
                                if (staged != null && staged.videoId == transitionedId) {
                                    _nowPlaying.value = staged
                                    stagedNextTrack = null
                                    if (c.currentMediaItemIndex > 0) {
                                        c.removeMediaItem(0)
                                    }
                                    _gaplessAdvanced.tryEmit(transitionedId)
                                }
                            }
                        }
                    })
                    startPolling()
                }
            }
        }
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    if (c.playbackState == Player.STATE_READY || c.playbackState == Player.STATE_BUFFERING) {
                        _positionMs.value = c.currentPosition.coerceAtLeast(0)
                        val d = c.duration
                        if (d > 0) _durationMs.value = d
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun buildMediaItem(filePath: String, track: OnlineTrack): MediaItem {
        val uri = if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            Uri.parse(filePath)
        } else {
            Uri.fromFile(File(filePath))
        }
        val metaBuilder = MediaMetadata.Builder()
            .setTitle(track.title.ifEmpty { "Music0" })
            .setArtist(track.artist.ifEmpty { null })
            .setAlbumTitle(track.album.ifEmpty { null })
        track.thumbnail?.takeIf { it.isNotBlank() }?.let { thumb ->
            runCatching { metaBuilder.setArtworkUri(Uri.parse(thumb)) }
        }
        return MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.videoId)
            .setMediaMetadata(metaBuilder.build())
            .build()
    }

    override suspend fun playFile(filePath: String, track: OnlineTrack) = withContext(Dispatchers.Main.immediate) {
        val c = obtain()
        stagedNextTrack = null
        _positionMs.value = 0
        _durationMs.value = 0
        c.setMediaItem(buildMediaItem(filePath, track))
        c.prepare()
        c.play()
        _nowPlaying.value = track
    }

    override suspend fun stageNextFile(filePath: String, track: OnlineTrack) = withContext(Dispatchers.Main.immediate) {
        val c = obtain()
        if (c.mediaItemCount == 0) return@withContext
        if (stagedNextTrack?.videoId == track.videoId && c.mediaItemCount >= 2) return@withContext
        while (c.mediaItemCount > 1) {
            c.removeMediaItem(c.mediaItemCount - 1)
        }
        stagedNextTrack = track
        c.addMediaItem(buildMediaItem(filePath, track))
    }

    override suspend fun clearStagedNext() = withContext(Dispatchers.Main.immediate) {
        val c = controller ?: return@withContext
        stagedNextTrack = null
        while (c.mediaItemCount > 1) {
            c.removeMediaItem(c.mediaItemCount - 1)
        }
    }

    override suspend fun togglePlayPause() = withContext(Dispatchers.Main.immediate) {
        val c = obtain()
        if (c.isPlaying) c.pause() else c.play()
    }

    override suspend fun setSkipSilence(enabled: Boolean) {
        PlaybackPrefs.setSkipSilence(enabled)
    }

    override suspend fun setSpeed(speed: Float) = withContext(Dispatchers.Main.immediate) {
        obtain().setPlaybackSpeed(speed.coerceIn(0.5f, 2f))
    }

    override suspend fun setVolume(volume: Float) = withContext(Dispatchers.Main.immediate) {
        val clamped = volume.coerceIn(0f, 1f)
        _volume.value = clamped
        controller?.volume = clamped
    }

    override suspend fun seekTo(ms: Long) = withContext(Dispatchers.Main.immediate) {
        val c = obtain()
        val duration = c.duration
        val target = if (duration > 0) ms.coerceIn(0, duration) else ms.coerceAtLeast(0)
        c.seekTo(target)
        _positionMs.value = target
    }

    override fun release() {
        released = true
        scope.cancel()
        pollJob = null
        val c = controller
        controller = null
        if (c != null) {
            if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                c.release()
            } else {
                android.os.Handler(android.os.Looper.getMainLooper()).post { c.release() }
            }
        }
    }

    private companion object {
        const val TAG = "Music0Playback"
        const val POLL_INTERVAL_MS = 200L
    }
}
