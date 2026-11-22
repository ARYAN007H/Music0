package io.github.aryan007h.musicoonline.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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

    private var controller: MediaController? = null
    private val obtainMutex = Mutex()
    private var pollJob: Job? = null
    private var stagedNextTrack: OnlineTrack? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        // Pre-bind PlaybackService on app launch so the first track tap doesn't
        // pay Media3 service-connection startup latency.
        scope.launch {
            runCatching { obtain() }
        }
    }

    private suspend fun obtain(): MediaController {
        controller?.let { return it }
        return obtainMutex.withLock {
            controller ?: withContext(Dispatchers.Main) {
                val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
                MediaController.Builder(context, token).buildAsync().await().also { c ->
                    controller = c
                    c.addListener(object : Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            _isPlaying.value = isPlaying
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            _isBuffering.value = playbackState == Player.STATE_BUFFERING
                            if (playbackState == Player.STATE_ENDED) {
                                _trackEnded.tryEmit(Unit)
                            }
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

    override suspend fun playFile(filePath: String, track: OnlineTrack) {
        val c = obtain()
        stagedNextTrack = null
        _positionMs.value = 0
        _durationMs.value = 0
        c.setMediaItem(buildMediaItem(filePath, track))
        c.prepare()
        c.play()
        _nowPlaying.value = track
    }

    override suspend fun stageNextFile(filePath: String, track: OnlineTrack) {
        val c = obtain()
        if (c.mediaItemCount == 0) return
        if (stagedNextTrack?.videoId == track.videoId && c.mediaItemCount >= 2) return
        while (c.mediaItemCount > 1) {
            c.removeMediaItem(c.mediaItemCount - 1)
        }
        stagedNextTrack = track
        c.addMediaItem(buildMediaItem(filePath, track))
    }

    override suspend fun clearStagedNext() {
        val c = controller ?: return
        stagedNextTrack = null
        while (c.mediaItemCount > 1) {
            c.removeMediaItem(c.mediaItemCount - 1)
        }
    }

    override suspend fun togglePlayPause() {
        val c = obtain()
        if (c.isPlaying) c.pause() else c.play()
    }

    override suspend fun setSkipSilence(enabled: Boolean) {
        PlaybackPrefs.setSkipSilence(enabled)
    }

    override suspend fun setSpeed(speed: Float) {
        obtain().setPlaybackSpeed(speed.coerceIn(0.5f, 2f))
    }

    override suspend fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _volume.value = clamped
        controller?.volume = clamped
    }

    override suspend fun seekTo(ms: Long) {
        val c = obtain()
        val duration = c.duration
        val target = if (duration > 0) ms.coerceIn(0, duration) else ms.coerceAtLeast(0)
        c.seekTo(target)
        _positionMs.value = target
    }

    override fun release() {
        scope.cancel()
        pollJob = null
        controller?.release()
        controller = null
    }

    private companion object {
        const val POLL_INTERVAL_MS = 200L
    }
}
