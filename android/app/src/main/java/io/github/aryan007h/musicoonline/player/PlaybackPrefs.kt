package io.github.aryan007h.musicoonline.player

import androidx.media3.exoplayer.ExoPlayer

/**
 * Playback flags that only exist on the ExoPlayer instance (not reachable
 * through a MediaController): skip-silence. Service and UI share the
 * process, so this singleton is the bridge — the service registers its
 * player on create, Settings toggles apply instantly and persist via the
 * ViewModel's SharedPreferences.
 */
object PlaybackPrefs {

    @Volatile
    var skipSilence: Boolean = false
        private set

    private var player: ExoPlayer? = null
    private val listeners = mutableListOf<() -> Unit>()

    /** Called by [PlaybackService] once its ExoPlayer exists. */
    fun attach(player: ExoPlayer) {
        this.player = player
        applyTo(player)
    }

    fun detach() {
        player = null
    }

    fun setSkipSilence(enabled: Boolean) {
        skipSilence = enabled
        player?.let { applyTo(it) }
        listeners.forEach { it() }
    }

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun applyTo(p: ExoPlayer) {
        try {
            p.skipSilenceEnabled = skipSilence
        } catch (_: Exception) {
        }
    }
}
