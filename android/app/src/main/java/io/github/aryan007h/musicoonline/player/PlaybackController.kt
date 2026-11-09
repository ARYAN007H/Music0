package io.github.aryan007h.musicoonline.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.aryan007h.musicoonline.core.OnlineTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/**
 * UI-side handle on the [PlaybackService]'s ExoPlayer, via a Media3
 * MediaController. Keeps the service alive while a track is loaded and
 * exposes the two pieces of state the UI needs.
 */
class PlaybackController(private val context: Context) {

    private val _nowPlaying = MutableStateFlow<OnlineTrack?>(null)
    val nowPlaying: StateFlow<OnlineTrack?> = _nowPlaying

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private var controller: MediaController? = null

    private suspend fun obtain(): MediaController =
        controller ?: withContext(Dispatchers.Main) {
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            MediaController.Builder(context, token).buildAsync().await().also {
                controller = it
                it.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }
                })
            }
        }

    suspend fun playFile(uri: Uri, track: OnlineTrack) {
        val c = obtain()
        val metadata = MediaMetadata.Builder()
            .setTitle(track.title.ifEmpty { "Musico Online" })
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

    fun release() {
        controller?.release()
        controller = null
    }
}
