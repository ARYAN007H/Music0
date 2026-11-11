package io.github.aryan007h.musicoonline.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import io.github.aryan007h.musicoonline.core.MusicoCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * In-app updater for a sideloaded app: check GitHub releases (through the
 * Rust bridge — same HTTP stack + logging as the resolvers), download the
 * native APK with progress, then hand the file to the system installer via
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
    val notes: String,
)

object UpdateManager {

    private val json = Json { ignoreUnknownKeys = true }
    const val UPDATE_DIR = "updates"
    const val APK_NAME = "musico-update.apk"

    /** Latest-release check through the core. Throws on network/shape errors. */
    suspend fun check(): UpdateInfo = withContext(Dispatchers.IO) {
        val reply = MusicoCore.call("app.update.check")
        json.decodeFromJsonElement(UpdateInfo.serializer(), reply)
    }

    /** Installed version from PackageManager ("0.9.1-native"). */
    fun currentVersion(context: Context): String =
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName ?: "unknown"

    /**
     * Streams the release APK into filesDir/updates with progress. No
     * third-party HTTP client — plain HttpsURLConnection, cancellable by
     * cancelling the calling coroutine (the connection aborts on close).
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, UPDATE_DIR).apply { mkdirs() }
        val part = File(dir, "$APK_NAME.part")
        val target = File(dir, APK_NAME)

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
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        onProgress(downloaded, total)
                    }
                    out.flush()
                }
            }
            if (part.length() < 1_000_000) {
                throw IllegalStateException("downloaded APK suspiciously small (${part.length()} B)")
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
        context.startActivity(intent)
    }

    /** Cached downloaded APK from an earlier session, if it completed. */
    fun downloadedApk(context: Context): File? {
        val f = File(File(context.filesDir, UPDATE_DIR), APK_NAME)
        return f.takeIf { it.exists() && it.length() > 1_000_000 }
    }
}
