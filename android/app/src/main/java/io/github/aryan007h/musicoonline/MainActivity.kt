package io.github.aryan007h.musicoonline

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.aryan007h.musicoonline.core.MusicoCore
import io.github.aryan007h.musicoonline.platform.AndroidPlatformHost
import io.github.aryan007h.musicoonline.platform.AndroidPlatformPrefs
import io.github.aryan007h.musicoonline.player.PlaybackController
import io.github.aryan007h.musicoonline.ui.MusicViewModel
import io.github.aryan007h.musicoonline.ui.MusicoApp
import io.github.aryan007h.musicoonline.updater.AndroidPlatformUpdater
import io.github.aryan007h.musicoonline.updater.EXTRA_OPEN_UPDATE

/**
 * Retains the shared KMP [MusicViewModel] and [PlaybackController] safely
 * across Android configuration changes (rotations, foldables, dark-mode switches).
 */
class AndroidMusicViewModelHolder(appContext: Context) : ViewModel() {
    val playback: PlaybackController = PlaybackController(appContext)
    val vm: MusicViewModel = MusicViewModel(
        playback = playback,
        prefs = AndroidPlatformPrefs(appContext),
        updater = AndroidPlatformUpdater(appContext),
    )

    override fun onCleared() {
        vm.clear()
        playback.release()
        super.onCleared()
    }
}

private class AndroidHolderFactory(private val appContext: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AndroidMusicViewModelHolder::class.java)) {
            return AndroidMusicViewModelHolder(appContext) as T
        }
        error("Unknown ViewModel class: $modelClass")
    }
}

/**
 * Thin Android shell launching the shared Compose Multiplatform [MusicoApp].
 */
class MainActivity : ComponentActivity() {

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* silent denial keeps the in-app banner as the notification channel */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        AndroidPlatformHost.init(applicationContext)
        MusicoCore.init(filesDir.absolutePath, cacheDir.absolutePath)

        // Release notifications (Android 13+ runtime permission).
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val holder = ViewModelProvider(this, AndroidHolderFactory(applicationContext))[AndroidMusicViewModelHolder::class.java]
        val vm = holder.vm

        if (intent?.getBooleanExtra(EXTRA_OPEN_UPDATE, false) == true) {
            intent.removeExtra(EXTRA_OPEN_UPDATE)
            vm.openFromUpdateNotification()
        }

        setContent {
            MusicoApp(
                vm = vm,
                onDarkResolved = { isDark ->
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.isAppearanceLightStatusBars = !isDark
                    controller.isAppearanceLightNavigationBars = !isDark
                },
            )
        }
    }
}
