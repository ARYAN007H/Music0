package io.github.aryan007h.musicoonline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.aryan007h.musicoonline.core.MusicoCore
import io.github.aryan007h.musicoonline.player.PlaybackController
import io.github.aryan007h.musicoonline.ui.MusicScreen
import io.github.aryan007h.musicoonline.ui.MusicViewModel
import io.github.aryan007h.musicoonline.ui.MusicViewModelFactory
import io.github.aryan007h.musicoonline.ui.theme.MusicoTheme

/**
 * Phase-1 native UI (#111): one screen — search the catalog through the
 * Rust core and play through Media3. MusicoCore.init MUST precede the
 * first bridge call; the ViewModel only starts work once composition
 * begins, which is after onCreate, so initializing here is sufficient.
 */
class MainActivity : ComponentActivity() {

    private lateinit var playback: PlaybackController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MusicoCore.init(this)
        playback = PlaybackController(this)
        setContent {
            MusicoTheme {
                val vm: MusicViewModel = viewModel(factory = MusicViewModelFactory(playback))
                MusicScreen(vm)
            }
        }
    }

    override fun onDestroy() {
        playback.release()
        super.onDestroy()
    }
}
