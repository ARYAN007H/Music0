package io.github.aryan007h.musicoonline.player

import androidx.media3.exoplayer.ExoPlayer

object PlaybackPrefs {

    @Volatile
    var skipSilence: Boolean = false
        private set

    private var player: ExoPlayer? = null
    private val listeners = mutableListOf<() -> Unit>()

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
