package io.github.aryan007h.musicoonline

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.aryan007h.musicoonline.core.MusicoCore
import io.github.aryan007h.musicoonline.platform.AndroidPlatformHost
import io.github.aryan007h.musicoonline.platform.AndroidPlatformPrefs
import io.github.aryan007h.musicoonline.player.PlaybackController
import io.github.aryan007h.musicoonline.ui.AppOverlay
import io.github.aryan007h.musicoonline.ui.MusicViewModel
import io.github.aryan007h.musicoonline.ui.MusicoApp
import io.github.aryan007h.musicoonline.updater.AndroidPlatformUpdater
import io.github.aryan007h.musicoonline.updater.EXTRA_OPEN_UPDATE
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

        // Request high refresh rate (90/120/144Hz) only while the animated full-screen
        // player is active and started; browse/library screens reset to system default (0).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.state
                    .map { it.overlay == AppOverlay.PLAYER }
                    .distinctUntilChanged()
                    .collect { playerActive ->
                        requestHighRefreshRate(enabled = playerActive)
                    }
            }
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

    override fun onStop() {
        // Drop any high-refresh mode preference as soon as the activity leaves the foreground.
        requestHighRefreshRate(enabled = false)
        super.onStop()
    }

    /**
     * Requests the highest refresh-rate display mode matching the active physical
     * resolution while the animated player is open, or resets `preferredDisplayModeId`
     * to `0` otherwise so LTPO panels drop back to power-saving rates on static screens.
     */
    private fun requestHighRefreshRate(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        runCatching {
            val targetModeId = if (enabled) resolveBestHighRefreshModeId() else 0
            val attrs = window.attributes
            if (attrs.preferredDisplayModeId != targetModeId) {
                attrs.preferredDisplayModeId = targetModeId
                window.attributes = attrs
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveBestHighRefreshModeId(): Int {
        val activeDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display ?: windowManager.defaultDisplay
        } else {
            windowManager.defaultDisplay
        }
        val currentMode = activeDisplay?.mode ?: return 0
        val sameResolution = activeDisplay.supportedModes.filter {
            it.physicalWidth == currentMode.physicalWidth &&
                it.physicalHeight == currentMode.physicalHeight
        }
        val bestMode = sameResolution.maxByOrNull { it.refreshRate } ?: return 0
        val minRefresh = sameResolution.minOfOrNull { it.refreshRate } ?: currentMode.refreshRate
        return if (bestMode.refreshRate > minRefresh + 1f) bestMode.modeId else 0
    }
}
