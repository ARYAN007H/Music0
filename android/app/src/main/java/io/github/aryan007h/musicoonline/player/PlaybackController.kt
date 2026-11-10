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
import kotlinx.coroutines.withContext

/**
 * UI-side handle on the [PlaybackService]'s ExoPlayer, via a Media3
 * MediaController. Keeps the service alive while a track is loaded and
 * exposes the state the UI renders: now playing, playing flag, and the
 * position/duration pair for the seek bars.
 *
 * Media3 has no position callback, so position is polled — at 200 ms, the
 * compromise between word-sync smoothness (the lyric pane interpolates
 * between these samples with the frame clock) and battery. The queue
 * itself lives in the ViewModel: resolving a track is a core call per
 * item, so each advance is resolve-then-[playFile] orchestrated above.
 */
class PlaybackController(private val context: Context) {

    /** Application context for callers that need it (prefs, artwork cache keys). */
    val appContext: Context = context.applicationContext

    private val _nowPlaying = MutableStateFlow<OnlineTrack?>(null)
    val nowPlaying: StateFlow<OnlineTrack?> = _nowPlaying

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    /** One event per natural track end (NOT manual skips) — the queue advances on this. */
    private val _trackEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val trackEnded: SharedFlow<Unit> = _trackEnded

    private var controller: MediaController? = null
    private var pollJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private suspend fun obtain(): MediaController =
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
                    }
                })
                startPolling()
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

    suspend fun playFile(uri: Uri, track: OnlineTrack) {
        val c = obtain()
        _positionMs.value = 0
        _durationMs.value = 0
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title.ifEmpty { "Music0" })
            .setArtist(track.artist.ifEmpty { null })
            .setAlbumTitle(track.album.ifEmpty { null })
            .build()
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.videoId)
            .setMediaMetadata(metadata)
            .build()
        c.setMediaItem(item)
        c.prepare()
        c.play()
        _nowPlaying.value = track
    }

    suspend fun togglePlayPause() {
        val c = obtain()
        if (c.isPlaying) c.pause() else c.play()
    }

    suspend fun seekTo(ms: Long) {
        val c = obtain()
        val duration = c.duration
        val target = if (duration > 0) ms.coerceIn(0, duration) else ms.coerceAtLeast(0)
        c.seekTo(target)
        _positionMs.value = target
    }

    fun release() {
        scope.cancel()
        pollJob = null
        controller?.release()
        controller = null
    }

    private companion object {
        /** Word-sync interpolates between these samples; 200 ms is smooth enough and cheap. */
        const val POLL_INTERVAL_MS = 200L
    }
}
