package io.github.aryan007h.musicoonline.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.aryan007h.musicoonline.player.PlaybackController

/** The ViewModel needs the PlaybackController, which needs the Activity. */
class MusicViewModelFactory(private val playback: PlaybackController) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MusicViewModel(playback) as T
}
