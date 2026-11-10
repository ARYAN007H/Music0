package io.github.aryan007h.musicoonline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.aryan007h.musicoonline.core.MusicoCore
import io.github.aryan007h.musicoonline.player.PlaybackController
import io.github.aryan007h.musicoonline.ui.MusicoApp
import io.github.aryan007h.musicoonline.ui.MusicViewModel
import io.github.aryan007h.musicoonline.ui.MusicViewModelFactory
import io.github.aryan007h.musicoonline.ui.theme.MusicoTheme

/**
 * Native app shell (#111 phase 2): Home / Library / Search tabs, mini
 * player + full-screen player with word-synced lyrics, profile/settings.
 * MusicoCore.init MUST precede the first bridge call; the ViewModel only
 * starts work once composition begins, which is after onCreate, so
 * initializing here is sufficient.
 */
class MainActivity : ComponentActivity() {

    private lateinit var playback: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MusicoCore.init(this)
        playback = PlaybackController(this)
        setContent {
            MusicoTheme {
                val vm: MusicViewModel = viewModel(factory = MusicViewModelFactory(playback))
                MusicoApp(vm)
            }
        }
    }

    override fun onDestroy() {
        playback.release()
        super.onDestroy()
    }
}
