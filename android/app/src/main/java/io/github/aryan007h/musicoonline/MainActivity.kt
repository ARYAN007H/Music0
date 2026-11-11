package io.github.aryan007h.musicoonline

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import io.github.aryan007h.musicoonline.core.MusicoCore
import io.github.aryan007h.musicoonline.player.PlaybackController
import io.github.aryan007h.musicoonline.ui.MusicoApp
import io.github.aryan007h.musicoonline.ui.MusicViewModel
import io.github.aryan007h.musicoonline.ui.MusicViewModelFactory
import io.github.aryan007h.musicoonline.updater.EXTRA_OPEN_UPDATE

/**
 * Native app shell (#111 phase 2): Home / Library / Search tabs, mini
 * player + full-screen player with word-synced lyrics, profile/settings.
 * MusicoCore.init MUST precede the first bridge call; the ViewModel only
 * starts work once composition begins, which is after onCreate, so
 * initializing here is sufficient.
 */
class MainActivity : ComponentActivity() {

    private lateinit var playback: PlaybackController

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* silent denial just means the in-app banner stays the only channel */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        MusicoCore.init(this)
        playback = PlaybackController(this)

        // Release notifications (Android 13+ runtime permission): ask once,
        // on first launch — repeat launches no-op via the system.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val factory = MusicViewModelFactory(playback)
        val vm = ViewModelProvider(this, factory)[MusicViewModel::class.java]
        // Launched from the update notification → start the download as
        // soon as the launch check lands. Consume the extra so rotations
        // don't re-trigger it.
        if (intent?.getBooleanExtra(EXTRA_OPEN_UPDATE, false) == true) {
            intent.removeExtra(EXTRA_OPEN_UPDATE)
            vm.openFromUpdateNotification()
        }
        setContent {
            MusicoApp(vm)
        }
    }

    override fun onDestroy() {
        playback.release()
        super.onDestroy()
    }
}
