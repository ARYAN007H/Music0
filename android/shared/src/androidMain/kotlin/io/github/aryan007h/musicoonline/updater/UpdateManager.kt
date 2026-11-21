package io.github.aryan007h.musicoonline.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import io.github.aryan007h.musicoonline.core.MusicoCore
import io.github.aryan007h.musicoonline.core.UpdateInfo
import io.github.aryan007h.musicoonline.platform.PlatformUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** MainActivity extra: launched from the update notification -> start the download. */
const val EXTRA_OPEN_UPDATE = "musico.open_update"

class AndroidPlatformUpdater(private val context: Context) : PlatformUpdater {
    private val appContext = context.applicationContext

    override fun currentVersion(): String = UpdateManager.currentVersion(appContext)

    override suspend fun check(): UpdateInfo = UpdateManager.check()

    override fun notifyUpdateAvailable(info: UpdateInfo) {
        UpdateManager.notifyUpdateAvailable(appContext, info)
    }

    override suspend fun downloadAndInstall(
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        val apk = UpdateManager.download(appContext, info, onProgress)
        UpdateManager.promptInstall(appContext, apk)
    }
}

object UpdateManager {

    private val json = Json { ignoreUnknownKeys = true }
    const val UPDATE_DIR = "updates"
    const val APK_NAME = "musico-update.apk"
    private const val MIN_APK_BYTES = 1_000_000
    private const val CHANNEL_UPDATES = "app_updates"
    private const val NOTIF_ID_UPDATE = 41

    suspend fun check(): UpdateInfo = withContext(Dispatchers.IO) {
        val reply = MusicoCore.call("app.update.check")
        json.decodeFromJsonElement(UpdateInfo.serializer(), reply)
    }

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
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            putExtra(EXTRA_OPEN_UPDATE, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        } ?: return

        val pi = PendingIntent.getActivity(
            context,
            0,
            open,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val smallIcon = context.applicationInfo.icon.takeIf { it != 0 } ?: android.R.drawable.stat_sys_download_done
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(smallIcon)
            .setContentTitle("Music0 ${info.latest} available")
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(text + info.notes.takeIf { it.isNotBlank() }?.let { "\n\n$it" }.orEmpty()),
            )
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIF_ID_UPDATE, notification)
        } catch (_: SecurityException) {
        }
    }

    fun currentVersion(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")

    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, UPDATE_DIR).apply { mkdirs() }
        val part = File(dir, "$APK_NAME.part")
        val target = File(dir, APK_NAME)

        if (target.exists() && verifyApk(target, info)) return@withContext target
        target.delete()

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

    private fun verifyApk(file: File, info: UpdateInfo): Boolean {
        if (info.assetSizeBytes > 0 && file.length() != info.assetSizeBytes) return false
        val expectedSha = info.assetDigest
            ?.removePrefix("sha256:")
            ?.lowercase()
            ?.takeIf { it.length == 64 && it.all { c -> c.isDigit() || c in 'a'..'f' } }
            ?: return info.assetSizeBytes > 0
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) } == expectedSha
    }

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
