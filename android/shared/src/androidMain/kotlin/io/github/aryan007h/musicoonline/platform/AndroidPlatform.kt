package io.github.aryan007h.musicoonline.platform

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.core.content.FileProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.crossfade
import coil3.size.Precision
import coil3.toBitmap
import okio.Path.Companion.toOkioPath
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.player.MobileEqualizer
import io.github.aryan007h.musicoonline.ui.player.ArtworkAtmosphere
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

object AndroidPlatformHost {
    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val imageCacheDir = File(app.cacheDir, "coil_images").apply { mkdirs() }
        SingletonImageLoader.setSafe { ctx ->
            ImageLoader.Builder(ctx)
                .crossfade(true)
                .memoryCache {
                    MemoryCache.Builder()
                        .maxSizePercent(ctx, 0.14)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(imageCacheDir.toOkioPath())
                        .maxSizeBytes(50L * 1024L * 1024L)
                        .build()
                }
                .build()
        }
    }

    fun requireContext(): Context =
        appContext ?: error("AndroidPlatformHost.init(context) must be called before using platform services")

    fun contextOrNull(): Context? = appContext
}

class AndroidPlatformPrefs(context: Context, name: String = "musico_ui") : PlatformPrefs {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String, default: String?): String? = prefs.getString(key, default)
    override fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun getFloat(key: String, default: Float): Float = prefs.getFloat(key, default)
    override fun putFloat(key: String, value: Float) {
        prefs.edit().putFloat(key, value).apply()
    }

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }
}

actual object PlatformEqualizer {
    actual fun isAttached(): Boolean = MobileEqualizer.isAttached()
    actual fun currentState(): EqSnapshot = MobileEqualizer.currentState()
    actual fun setEnabled(enabled: Boolean) = MobileEqualizer.setEnabled(enabled)
    actual fun setBandLevel(index: Int, milliBel: Int) = MobileEqualizer.setBandLevel(index, milliBel)
    actual fun setBassBoost(strength: Int) = MobileEqualizer.setBassBoost(strength)
    actual fun applyPresetGain(curve: List<Int>, presetName: String) = MobileEqualizer.applyPresetGain(curve, presetName)
    actual fun addListener(listener: () -> Unit) = MobileEqualizer.addListener(listener)
    actual fun removeListener(listener: () -> Unit) = MobileEqualizer.removeListener(listener)
}

actual val isDesktopPlatform: Boolean = false

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun currentLocalHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

actual fun formatDouble(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals.coerceIn(0, 6)}f", value)

actual fun performHaptic(kind: HapticKind) {
    val ctx = AndroidPlatformHost.contextOrNull() ?: return
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val effect = when (kind) {
                HapticKind.TAP -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                HapticKind.TICK -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                HapticKind.CONFIRM -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                HapticKind.LONG_PRESS -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                HapticKind.REJECT -> VibrationEffect.createOneShot(24L, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        } else {
            val ms = when (kind) {
                HapticKind.TICK -> 8L
                HapticKind.TAP -> 14L
                HapticKind.CONFIRM -> 22L
                HapticKind.LONG_PRESS -> 30L
                HapticKind.REJECT -> 26L
            }
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}

actual fun showPlatformToast(message: String) {
    val ctx = AndroidPlatformHost.contextOrNull() ?: return
    Handler(Looper.getMainLooper()).post {
        Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show()
    }
}

actual fun openExternalUrl(url: String) {
    val ctx = AndroidPlatformHost.contextOrNull() ?: return
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun Modifier.platformBlur(radiusDp: Float): Modifier {
    if (radiusDp <= 0.1f || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val radiusPx = with(LocalDensity.current) { radiusDp.coerceIn(0f, 24f) * density }
    return this.graphicsLayer {
        renderEffect = RenderEffect
            .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.DECAL)
            .asComposeRenderEffect()
    }
}

actual suspend fun decodeArtworkSamplePixels(url: String): IntArray? {
    val ctx = AndroidPlatformHost.contextOrNull() ?: return null
    return withContext(Dispatchers.IO) {
        runCatching {
            val req = ImageRequest.Builder(ctx)
                .data(url)
                .size(48, 48)
                .build()
            val result = SingletonImageLoader.get(ctx).execute(req)
            val bitmap = (result as? SuccessResult)?.image?.toBitmap(48, 48) ?: return@withContext null
            val swBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            }
            val pixels = IntArray(swBitmap.width * swBitmap.height)
            swBitmap.getPixels(pixels, 0, swBitmap.width, 0, 0, swBitmap.width, swBitmap.height)
            if (swBitmap !== bitmap) swBitmap.recycle()
            pixels
        }.getOrNull()
    }
}

actual suspend fun prefetchArtworkImage(url: String) {
    val ctx = AndroidPlatformHost.contextOrNull() ?: return
    runCatching {
        val req = ImageRequest.Builder(ctx)
            .data(url)
            .size(384, 384)
            .precision(Precision.INEXACT)
            .build()
        SingletonImageLoader.get(ctx).enqueue(req)
    }
}

actual suspend fun shareLyricCard(
    track: OnlineTrack,
    lines: List<String>,
    atmosphere: ArtworkAtmosphere,
): Result<String> = runCatching {
    val ctx = AndroidPlatformHost.requireContext()
    val cleanLines = lines.map { it.trim() }.filter { it.isNotEmpty() }.take(4)
    require(cleanLines.isNotEmpty()) { "Select at least one lyric line to share" }

    val file = withContext(Dispatchers.IO) {
        val coverBitmap = runCatching {
            track.thumbnail?.takeIf { it.isNotBlank() }?.let { url ->
                val req = ImageRequest.Builder(ctx).data(url).size(256, 256).build()
                val res = SingletonImageLoader.get(ctx).execute(req)
                val bmp = (res as? SuccessResult)?.image?.toBitmap(256, 256)
                if (bmp != null && bmp.config == Bitmap.Config.HARDWARE) {
                    bmp.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bmp
                }
            }
        }.getOrNull()

        val poster = renderAndroidPoster(track, cleanLines, atmosphere, coverBitmap)
        val dir = File(ctx.cacheDir, "lyric_cards").apply { mkdirs() }
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(8)?.forEach { it.delete() }
        val outFile = File(dir, "music0_lyric_${System.currentTimeMillis()}.png")
        FileOutputStream(outFile).use { fos ->
            poster.compress(Bitmap.CompressFormat.PNG, 96, fos)
        }
        poster.recycle()
        outFile
    }

    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(
            Intent.EXTRA_TEXT,
            "“${cleanLines.joinToString(" / ")}” — ${track.title} by ${track.artist.ifEmpty { "Unknown artist" }}",
        )
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(shareIntent, "Share lyric card").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    ctx.startActivity(chooser)
    "Shared lyric poster"
}

private fun renderAndroidPoster(
    track: OnlineTrack,
    lines: List<String>,
    atmosphere: ArtworkAtmosphere,
    coverBitmap: Bitmap?,
): Bitmap {
    val width = 1080
    val height = 1350
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val baseArgb = atmosphere.base.toArgb()
    val primaryArgb = atmosphere.orbPrimary.toArgb()
    val secondaryArgb = atmosphere.orbSecondary.toArgb()
    val glowArgb = atmosphere.glow.toArgb()

    fun withAlpha(argb: Int, alpha: Int): Int = AndroidColor.argb(
        alpha.coerceIn(0, 255),
        AndroidColor.red(argb),
        AndroidColor.green(argb),
        AndroidColor.blue(argb),
    )

    fun darken(argb: Int, factor: Float): Int = AndroidColor.rgb(
        (AndroidColor.red(argb) * factor).toInt().coerceIn(0, 255),
        (AndroidColor.green(argb) * factor).toInt().coerceIn(0, 255),
        (AndroidColor.blue(argb) * factor).toInt().coerceIn(0, 255),
    )

    fun drawOrb(cx: Float, cy: Float, radius: Float, centerColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(centerColor, withAlpha(centerColor, 0)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawCircle(cx, cy, radius, paint)
    }

    canvas.drawRect(
        0f, 0f, width.toFloat(), height.toFloat(),
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(baseArgb, darken(baseArgb, 0.55f), 0xFF06060C.toInt()),
                floatArrayOf(0f, 0.65f, 1f),
                Shader.TileMode.CLAMP,
            )
        },
    )

    drawOrb(width * 0.18f, height * 0.22f, width * 0.85f, withAlpha(primaryArgb, 165))
    drawOrb(width * 0.86f, height * 0.74f, width * 0.80f, withAlpha(secondaryArgb, 150))
    drawOrb(width * 0.54f, height * 0.48f, width * 0.62f, withAlpha(glowArgb, 75))

    canvas.drawRect(
        0f, 0f, width.toFloat(), height.toFloat(),
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(0x30000000, 0x18000000, 0x66000000),
                floatArrayOf(0f, 0.5f, 1f),
                Shader.TileMode.CLAMP,
            )
        },
    )

    val pad = 84f
    val coverSize = 132f
    val headerTop = 96f
    val coverRect = RectF(pad, headerTop, pad + coverSize, headerTop + coverSize)
    if (coverBitmap != null) {
        val shader = BitmapShader(coverBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val matrix = Matrix()
        val scale = maxOf(coverRect.width() / coverBitmap.width, coverRect.height() / coverBitmap.height)
        matrix.setScale(scale, scale)
        matrix.postTranslate(
            coverRect.left + (coverRect.width() - coverBitmap.width * scale) / 2f,
            coverRect.top + (coverRect.height() - coverBitmap.height * scale) / 2f,
        )
        shader.setLocalMatrix(matrix)
        canvas.drawRoundRect(coverRect, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader })
    } else {
        canvas.drawRoundRect(coverRect, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(primaryArgb, 180) })
    }

    val textLeft = pad + coverSize + 34f
    val headerTextWidth = (width - pad - textLeft).toInt().coerceAtLeast(100)
    val titleText = track.title.ifEmpty { "Untitled" }
    val titleLayout = StaticLayout.Builder
        .obtain(
            titleText, 0, titleText.length,
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.WHITE
                textSize = 40f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            },
            headerTextWidth,
        )
        .setMaxLines(2)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .build()

    val artistText = track.artist.ifEmpty { track.album }.ifEmpty { "Unknown artist" }
    val artistLayout = StaticLayout.Builder
        .obtain(
            artistText, 0, artistText.length,
            TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = AndroidColor.argb(185, 255, 255, 255)
                textSize = 30f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            },
            headerTextWidth,
        )
        .setMaxLines(1)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .build()

    val headerBlockHeight = titleLayout.height + 8f + artistLayout.height
    canvas.save()
    canvas.translate(textLeft, headerTop + (coverSize - headerBlockHeight) / 2f)
    titleLayout.draw(canvas)
    canvas.translate(0f, titleLayout.height + 8f)
    artistLayout.draw(canvas)
    canvas.restore()

    val totalChars = lines.sumOf { it.length }
    val lyricFontSize = when {
        lines.size == 1 && totalChars < 34 -> 78f
        lines.size <= 2 && totalChars < 75 -> 66f
        lines.size <= 3 && totalChars < 120 -> 58f
        else -> 50f
    }
    val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = lyricFontSize
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        setShadowLayer(22f, 0f, 6f, withAlpha(glowArgb, 95))
    }
    val bodyWidth = (width - pad * 2).toInt()
    val lineGap = 32f
    val layouts = lines.map { line ->
        StaticLayout.Builder
            .obtain(line, 0, line.length, lyricPaint, bodyWidth)
            .setLineSpacing(10f, 1f)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build()
    }
    val totalLyricsHeight = layouts.sumOf { it.height } + (layouts.size - 1).coerceAtLeast(0) * lineGap
    val availableTop = headerTop + coverSize + 72f
    val availableBottom = height - 170f
    var cursorY = availableTop + ((availableBottom - availableTop) - totalLyricsHeight).coerceAtLeast(0f) / 2f

    canvas.drawRoundRect(
        RectF(pad - 28f, cursorY + 8f, pad - 20f, cursorY + totalLyricsHeight - 8f),
        4f, 4f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = withAlpha(glowArgb, 210) },
    )
    layouts.forEach { layout ->
        canvas.save()
        canvas.translate(pad, cursorY)
        layout.draw(canvas)
        canvas.restore()
        cursorY += layout.height + lineGap
    }

    canvas.drawText(
        "MUSIC0",
        pad,
        height - 84f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(145, 255, 255, 255)
            textSize = 26f
            letterSpacing = 0.24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        },
    )
    return bmp
}

private const val MAX_PLAYLIST_IMPORT_BYTES = 4 * 1024 * 1024

@Composable
actual fun rememberPlaylistFilePicker(
    onPicked: (fileName: String, content: String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val ctx = AndroidPlatformHost.requireContext()
                    val resolver = ctx.contentResolver
                    val displayName = resolver.query(
                        uri,
                        arrayOf(OpenableColumns.DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: "playlist.csv")

                    val rawBytes = resolver.openInputStream(uri)?.use { input ->
                        val out = ByteArrayOutputStream()
                        val buf = ByteArray(16 * 1024)
                        var total = 0
                        while (true) {
                            val read = input.read(buf)
                            if (read <= 0) break
                            total += read
                            if (total > MAX_PLAYLIST_IMPORT_BYTES) {
                                error("Playlist file is too large (max 4 MB).")
                            }
                            out.write(buf, 0, read)
                        }
                        out.toByteArray()
                    } ?: error("Couldn't open the selected file.")

                    displayName to rawBytes.decodeToString()
                }
            }
            result.fold(
                onSuccess = { (name, text) -> onPicked(name, text) },
                onFailure = { e -> onError(e.message ?: "Couldn't read playlist file.") },
            )
        }
    }
    return remember(launcher) {
        {
            runCatching {
                launcher.launch(
                    arrayOf(
                        "text/*",
                        "application/json",
                        "audio/x-mpegurl",
                        "application/vnd.apple.mpegurl",
                        "*/*",
                    ),
                )
            }.onFailure { e ->
                onError(e.message ?: "Couldn't open file picker.")
            }
        }
    }
}

@Composable
actual fun PlatformHorizontalScrollbar(
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier,
) {
    // Android uses native touch scrolling without persistent desktop scrollbars.
}

@Composable
actual fun PlatformHorizontalGridScrollbar(
    state: androidx.compose.foundation.lazy.grid.LazyGridState,
    modifier: Modifier,
) {
    // Android uses native touch scrolling without persistent desktop scrollbars.
}


