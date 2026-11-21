package io.github.aryan007h.musicoonline.player

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Media3 playback service: owns ExoPlayer and the system media session
 * (lockscreen/notification controls, audio focus, Bluetooth/headset handling).
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        MobileEqualizer.attach(this)
        PlaybackPrefs.attach(player)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                MobileEqualizer.onAudioSessionId(audioSessionId)
            }
        })
        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        MobileEqualizer.detach()
        PlaybackPrefs.detach()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
