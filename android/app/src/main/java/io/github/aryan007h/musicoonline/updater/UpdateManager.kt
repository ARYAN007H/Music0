package io.github.aryan007h.musicoonline.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import io.github.aryan007h.musicoonline.MainActivity
import io.github.aryan007h.musicoonline.R
import io.github.aryan007h.musicoonline.core.MusicoCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** MainActivity extra: launched from the update notification → start the download. */
const val EXTRA_OPEN_UPDATE = "musico.open_update"

/**
 * In-app updater for a sideloaded app: check GitHub releases (through the
 * Rust bridge — same HTTP stack + logging as the resolvers), download the
 * native APK with progress, verify it against the release's published
 * sha256 digest, then hand the file to the system installer via
 * FileProvider. Silent installs are not possible without system
 * privileges — the user confirms the standard install prompt, like every
 * sideloaded app's updater.
 */
@kotlinx.serialization.Serializable
data class UpdateInfo(
    val current: String,
    val latest: String,
    val updateAvailable: Boolean,
    val assetUrl: String,
    val assetName: String,
    val assetSizeBytes: Long,
    val assetDigest: String? = null, // "sha256:…" as published by the GitHub releases API
    val notes: String,
)

object UpdateManager {

    private val json = Json { ignoreUnknownKeys = true }
    const val UPDATE_DIR = "updates"
    const val APK_NAME = "musico-update.apk"
    private const val MIN_APK_BYTES = 1_000_000
    private const val CHANNEL_UPDATES = "app_updates"
    private const val NOTIF_ID_UPDATE = 41

    /** Latest-release check through the core. Throws on network/shape errors. */
    suspend fun check(): UpdateInfo = withContext(Dispatchers.IO) {
        val reply = MusicoCore.call("app.update.check")
        json.decodeFromJsonElement(UpdateInfo.serializer(), reply)
    }

    /**
     * System notification for a newly discovered release — so the user
     * knows even without wandering into Settings. Tap opens the app, which
     * then starts the download (MainActivity consumes [EXTRA_OPEN_UPDATE]).
     * Skips silently when notifications are disabled (MainActivity asks
     * for the runtime permission once).
     */
    fun notifyUpdateAvailable(context: Context, info: UpdateInfo) {
        if (info.assetUrl.isEmpty()) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_UPDATES, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New Music0 releases available for download"
                },
            )
        }

        val text = "Music0 ${info.latest} is available — tap to download and install."
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_OPEN_UPDATE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Music0 ${info.latest} available")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text + info.notes.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty()))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIF_ID_UPDATE, notification)
        } catch (_: SecurityException) {
            // A revoked permission raced the enabled-check — the in-app
            // banner still carries the update.
        }
    }

    /** Installed version from PackageManager ("0.9.1-native"). */
    fun currentVersion(context: Context): String =
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName ?: "unknown"

    /**
     * Streams the release APK into filesDir/updates with progress, then
     * verifies integrity before anything reaches the installer: a size
     * ceiling while streaming, the published sha256 digest afterwards
     * (assets without a digest fall back to an exact size match). The
     * download is cancellable for real — [ensureActive] between chunks
     * aborts the stream as soon as the calling coroutine is cancelled.
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, UPDATE_DIR).apply { mkdirs() }
        val part = File(dir, "$APK_NAME.part")
        val target = File(dir, APK_NAME)
        target.delete() // no stale payload survives a fresh download

        val expectedSha = info.assetDigest
            ?.removePrefix("sha256:")
            ?.lowercase()
            ?.takeIf { it.length == 64 && it.all { c -> c.isDigit() || c in 'a'..'f' } }
        val digest = expectedSha?.let { MessageDigest.getInstance("SHA-256") }

        val conn = URL(info.assetUrl).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Music0-Android/${info.current}")
            conn.connect()
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.assetSizeBytes
            conn.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var downloaded = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        digest?.update(buf, 0, n)
                        // The manifest states exactly how big the artifact
                        // is — a larger stream is not the release APK.
                        if (info.assetSizeBytes > 0 && downloaded > info.assetSizeBytes) {
                            throw IllegalStateException(
                                "download exceeded the release asset size (${info.assetSizeBytes} B)",
                            )
                        }
                        onProgress(downloaded, total)
                    }
                    out.flush()
                }
            }
            if (part.length() < MIN_APK_BYTES) {
                throw IllegalStateException("downloaded APK suspiciously small (${part.length()} B)")
            }
            if (digest != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (actual != expectedSha) {
                    part.delete()
                    throw IllegalStateException("APK digest mismatch — the download is not the published release")
                }
            } else if (info.assetSizeBytes > 0 && part.length() != info.assetSizeBytes) {
                part.delete()
                throw IllegalStateException("APK size ${part.length()} B != release asset ${info.assetSizeBytes} B")
            }
            if (!part.renameTo(target)) {
                part.copyTo(target, overwrite = true)
                part.delete()
            }
            target
        } finally {
            conn.disconnect()
        }
    }

    /** Hands the downloaded APK to the system package installer. */
    fun promptInstall(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apk,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            throw IllegalStateException("No package installer available on this device")
        }
    }
}
