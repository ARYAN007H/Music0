package io.github.aryan007h.musicoonline.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import io.github.aryan007h.musicoonline.core.MusicoCore
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.UpdateInfo
import io.github.aryan007h.musicoonline.ui.player.ArtworkAtmosphere
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.awt.Color as AwtColor
import java.awt.Desktop
import java.awt.Font
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.LocalTime
import java.util.Locale
import javax.imageio.ImageIO

object DesktopPlatformHost {
    private val _toasts = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toasts: SharedFlow<String> = _toasts

    fun emitToast(message: String) {
        _toasts.tryEmit(message)
    }

    fun dataDir(): File {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = System.getProperty("user.home") ?: "."
        val base = when {
            os.contains("mac") || os.contains("darwin") -> File(home, "Library/Application Support/musico-online")
            os.contains("win") -> {
                val appData = System.getenv("APPDATA") ?: File(home, "AppData/Roaming").absolutePath
                File(appData, "musico-online")
            }
            else -> {
                val xdg = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                    ?: File(home, ".local/share").absolutePath
                File(xdg, "musico-online")
            }
        }
        return base.apply { mkdirs() }
    }

    fun cacheDir(): File {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = System.getProperty("user.home") ?: "."
        val base = when {
            os.contains("mac") || os.contains("darwin") -> File(home, "Library/Caches/musico-online")
            os.contains("win") -> {
                val localAppData = System.getenv("LOCALAPPDATA") ?: File(home, "AppData/Local").absolutePath
                File(localAppData, "musico-online/cache")
            }
            else -> {
                val xdg = System.getenv("XDG_CACHE_HOME")?.takeIf { it.isNotBlank() }
                    ?: File(home, ".cache").absolutePath
                File(xdg, "musico-online")
            }
        }
        return base.apply { mkdirs() }
    }
}

class DesktopPlatformUpdater : PlatformUpdater {
    private val json = Json { ignoreUnknownKeys = true }

    override fun currentVersion(): String = "1.0.0-desktop"

    override suspend fun check(): UpdateInfo = withContext(Dispatchers.IO) {
        val reply = MusicoCore.call("app.update.check")
        json.decodeFromJsonElement(UpdateInfo.serializer(), reply)
    }

    override fun notifyUpdateAvailable(info: UpdateInfo) {
        DesktopPlatformHost.emitToast("Music0 ${info.latest} is available")
    }

    override suspend fun downloadAndInstall(
        info: UpdateInfo,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        val targetUrl = info.assetUrl.ifBlank { "https://github.com/ARYAN007H/Music0/releases/latest" }
        openExternalUrl(targetUrl)
        DesktopPlatformHost.emitToast("Opened release page in your browser")
    }
}

/**
 * Desktop Equalizer bridging the shared 5-band UI to the Rust 10-band biquad
 * DSP equalizer (`player.set_eq`), with serialized JNI updates and persistence
 * via [PlatformPrefs].
 */
actual object PlatformEqualizer {
    private const val KEY_ENABLED = "eq_enabled"
    private const val KEY_LEVELS = "eq_band_levels"
    private const val KEY_BASS = "eq_bass_boost"
    private const val KEY_PRESET = "eq_preset_name"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingPushes = Channel<Pair<Boolean, List<Double>>>(Channel.CONFLATED)
    private val listeners = mutableListOf<() -> Unit>()

    private var prefs: PlatformPrefs? = null
    private var enabled = false
    private var bandLevels = MutableList(5) { 0 }
    private var bassBoost = 0
    private var presetName: String? = null
    private val labels = listOf("60Hz", "230Hz", "910Hz", "3.6k", "14k")

    init {
        // Single serialized consumer so rapid slider drags never overtake each other
        // over JNI and always settle on the exact latest gains.
        scope.launch {
            for ((isOn, tenBand) in pendingPushes) {
                if (!MusicoCore.isLoaded) continue
                runCatching {
                    MusicoCore.call(
                        "player.set_eq",
                        buildJsonObject {
                            put("enabled", isOn)
                            putJsonArray("gainsDb") {
                                tenBand.forEach { add(it) }
                            }
                        },
                    )
                }
            }
        }
    }

    @Synchronized
    fun init(platformPrefs: PlatformPrefs) {
        prefs = platformPrefs
        enabled = platformPrefs.getBoolean(KEY_ENABLED, false)
        val saved = platformPrefs.getString(KEY_LEVELS, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull()?.coerceIn(-1200, 1200) }
        bandLevels = MutableList(5) { i -> saved?.getOrNull(i) ?: 0 }
        bassBoost = platformPrefs.getInt(KEY_BASS, 0).coerceIn(0, 1000)
        presetName = platformPrefs.getString(KEY_PRESET, null)
        notifyListeners()
        if (enabled) {
            pushToRust()
        }
    }

    @Synchronized
    fun reapplyToEngine() {
        pushToRust()
    }

    actual fun isAttached(): Boolean = MusicoCore.isLoaded

    @Synchronized
    actual fun currentState(): EqSnapshot = EqSnapshot(
        enabled = enabled,
        bandCount = 5,
        bandLevels = bandLevels.toList(),
        bandMinMb = -1200,
        bandMaxMb = 1200,
        bandFreqLabels = labels,
        bassBoost = bassBoost,
        presetName = presetName,
    )

    @Synchronized
    actual fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        persist()
        pushToRust()
        notifyListeners()
    }

    @Synchronized
    actual fun setBandLevel(index: Int, milliBel: Int) {
        if (index !in bandLevels.indices) return
        bandLevels[index] = milliBel.coerceIn(-1200, 1200)
        presetName = null
        persist()
        pushToRust()
        notifyListeners()
    }

    @Synchronized
    actual fun setBassBoost(strength: Int) {
        bassBoost = strength.coerceIn(0, 1000)
        persist()
        pushToRust()
        notifyListeners()
    }

    @Synchronized
    actual fun applyPresetGain(curve: List<Int>, presetName: String) {
        for (i in 0 until 5) {
            bandLevels[i] = curve.getOrElse(i) { 0 }.coerceIn(-1200, 1200)
        }
        this.presetName = presetName
        if (bandLevels.any { it != 0 } && !enabled) {
            enabled = true
        }
        persist()
        pushToRust()
        notifyListeners()
    }

    @Synchronized
    actual fun addListener(listener: () -> Unit) {
        if (listener !in listeners) listeners.add(listener)
    }

    @Synchronized
    actual fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        val snapshot = synchronized(this) { listeners.toList() }
        snapshot.forEach { it() }
    }

    private fun persist() {
        val p = prefs ?: return
        p.putBoolean(KEY_ENABLED, enabled)
        p.putString(KEY_LEVELS, bandLevels.joinToString(","))
        p.putInt(KEY_BASS, bassBoost)
        p.putString(KEY_PRESET, presetName)
    }

    private fun pushToRust() {
        if (!MusicoCore.isLoaded) return
        val tenBand = mapFiveBandsToTenDb(bandLevels, bassBoost)
        pendingPushes.trySend(enabled to tenBand)
    }
}

internal fun mapFiveBandsToTenDb(bandLevelsMb: List<Int>, bassBoostStrength: Int): List<Double> {
    val b = List(5) { i -> (bandLevelsMb.getOrElse(i) { 0 }.coerceIn(-1200, 1200)) / 100.0 }
    val bassDb = (bassBoostStrength.coerceIn(0, 1000) / 1000.0) * 6.0
    return listOf(
        (b[0] + bassDb).coerceIn(-12.0, 12.0),
        (b[0] + bassDb * 0.7).coerceIn(-12.0, 12.0),
        ((b[0] + b[1]) * 0.5 + bassDb * 0.3).coerceIn(-12.0, 12.0),
        b[1],
        (b[1] + b[2]) * 0.5,
        b[2],
        (b[2] + b[3]) * 0.5,
        b[3],
        (b[3] + b[4]) * 0.5,
        b[4],
    )
}

actual val isDesktopPlatform: Boolean = true

actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual fun currentLocalHour(): Int = LocalTime.now().hour

actual fun formatDouble(value: Double, decimals: Int): String =
    String.format(Locale.US, "%.${decimals.coerceIn(0, 6)}f", value)

actual fun performHaptic(kind: HapticKind) {
    // Desktop has no tactile motor; visual springs carry the feedback.
}

actual fun showPlatformToast(message: String) {
    DesktopPlatformHost.emitToast(message)
}

actual fun openExternalUrl(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Back navigation on Desktop is handled via Esc key shortcut and top-bar Back buttons.
}

@Composable
actual fun Modifier.platformBlur(radiusDp: Float): Modifier =
    if (radiusDp <= 0.1f) this else this.blur(radiusDp.coerceIn(0f, 24f).dp)

actual suspend fun decodeArtworkSamplePixels(url: String): IntArray? = withContext(Dispatchers.IO) {
    runCatching {
        val img = loadRemoteBufferedImage(url) ?: return@withContext null
        val targetW = 48
        val targetH = 48
        val scaled = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_ARGB)
        val g = scaled.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.drawImage(img, 0, 0, targetW, targetH, null)
        g.dispose()
        val pixels = IntArray(targetW * targetH)
        scaled.getRGB(0, 0, targetW, targetH, pixels, 0, targetW)
        pixels
    }.getOrNull()
}

actual suspend fun prefetchArtworkImage(url: String) {
    runCatching {
        val req = ImageRequest.Builder(PlatformContext.INSTANCE).data(url).build()
        SingletonImageLoader.get(PlatformContext.INSTANCE).enqueue(req)
    }
}

actual suspend fun shareLyricCard(
    track: OnlineTrack,
    lines: List<String>,
    atmosphere: ArtworkAtmosphere,
): Result<String> = runCatching {
    val cleanLines = lines.map { it.trim() }.filter { it.isNotEmpty() }.take(4)
    require(cleanLines.isNotEmpty()) { "Select at least one lyric line to save" }

    val outFile = withContext(Dispatchers.IO) {
        val cover = track.thumbnail?.takeIf { it.isNotBlank() }?.let { loadRemoteBufferedImage(it) }
        val poster = renderDesktopPoster(track, cleanLines, atmosphere, cover)
        val home = System.getProperty("user.home") ?: "."
        val dir = File(home, "Pictures/Music0").apply { mkdirs() }
        val file = File(dir, "music0_lyric_${System.currentTimeMillis()}.png")
        ImageIO.write(poster, "png", file)
        file
    }
    DesktopPlatformHost.emitToast("Saved lyric poster to ${outFile.parentFile?.name}/${outFile.name}")
    "Saved to ${outFile.absolutePath}"
}

private fun loadRemoteBufferedImage(url: String): BufferedImage? {
    val conn = URL(url).openConnection() as? HttpURLConnection ?: return null
    return try {
        conn.connectTimeout = 5_000
        conn.readTimeout = 8_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Music0-Desktop/1.0")
        conn.inputStream.use { ImageIO.read(it) }
    } finally {
        conn.disconnect()
    }
}

private fun renderDesktopPoster(
    track: OnlineTrack,
    lines: List<String>,
    atmosphere: ArtworkAtmosphere,
    cover: BufferedImage?,
): BufferedImage {
    val width = 1080
    val height = 1350
    val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = img.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)

    fun toAwt(argb: Int, alpha: Int = 255): AwtColor = AwtColor(
        (argb ushr 16) and 0xFF,
        (argb ushr 8) and 0xFF,
        argb and 0xFF,
        alpha.coerceIn(0, 255),
    )

    val baseArgb = atmosphere.base.toArgb()
    val primaryArgb = atmosphere.orbPrimary.toArgb()
    val secondaryArgb = atmosphere.orbSecondary.toArgb()
    val glowArgb = atmosphere.glow.toArgb()

    // Base vertical velvet gradient
    g.paint = GradientPaint(0f, 0f, toAwt(baseArgb), 0f, height.toFloat(), AwtColor(6, 6, 12))
    g.fillRect(0, 0, width, height)

    fun drawOrb(cx: Float, cy: Float, r: Float, argb: Int, alpha: Int) {
        g.paint = RadialGradientPaint(
            Point2D.Float(cx, cy),
            r,
            floatArrayOf(0f, 1f),
            arrayOf(toAwt(argb, alpha), toAwt(argb, 0)),
        )
        g.fillOval((cx - r).toInt(), (cy - r).toInt(), (r * 2).toInt(), (r * 2).toInt())
    }

    drawOrb(width * 0.18f, height * 0.22f, width * 0.78f, primaryArgb, 160)
    drawOrb(width * 0.86f, height * 0.74f, width * 0.74f, secondaryArgb, 145)
    drawOrb(width * 0.54f, height * 0.48f, width * 0.58f, glowArgb, 75)

    // Header: rounded cover + track metadata
    val pad = 84
    val coverSize = 132
    val headerTop = 96
    val clipShape = RoundRectangle2D.Float(pad.toFloat(), headerTop.toFloat(), coverSize.toFloat(), coverSize.toFloat(), 28f, 28f)
    if (cover != null) {
        val oldClip = g.clip
        g.clip = clipShape
        g.drawImage(cover, pad, headerTop, coverSize, coverSize, null)
        g.clip = oldClip
    } else {
        g.color = toAwt(primaryArgb, 180)
        g.fill(clipShape)
    }

    val textLeft = pad + coverSize + 34
    g.color = AwtColor.WHITE
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 38)
    g.drawString(track.title.ifEmpty { "Untitled" }.take(34), textLeft, headerTop + 56)

    g.color = AwtColor(255, 255, 255, 190)
    g.font = Font(Font.SANS_SERIF, Font.PLAIN, 28)
    g.drawString(track.artist.ifEmpty { "Unknown artist" }.take(42), textLeft, headerTop + 102)

    // Centerpiece wrapped lyrics
    val totalChars = lines.sumOf { it.length }
    val fontSize = when {
        lines.size == 1 && totalChars < 34 -> 72
        lines.size <= 2 && totalChars < 75 -> 62
        lines.size <= 3 && totalChars < 120 -> 54
        else -> 46
    }
    g.font = Font(Font.SANS_SERIF, Font.BOLD, fontSize)
    val fm = g.fontMetrics
    val maxLineWidth = width - pad * 2
    val wrapped = mutableListOf<String>()
    for (line in lines) {
        val words = line.split(" ")
        var current = ""
        for (w in words) {
            val candidate = if (current.isEmpty()) w else "$current $w"
            if (fm.stringWidth(candidate) <= maxLineWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) wrapped.add(current)
                current = w
            }
        }
        if (current.isNotEmpty()) wrapped.add(current)
    }

    val lineHeight = (fm.height * 1.18f).toInt()
    val totalHeight = wrapped.size * lineHeight
    val startY = (headerTop + coverSize + 72) + ((height - 170 - (headerTop + coverSize + 72) - totalHeight).coerceAtLeast(0) / 2)

    // Luminous left accent bar
    g.color = toAwt(glowArgb, 215)
    g.fillRoundRect(pad - 28, startY, 8, (totalHeight - 12).coerceAtLeast(32), 4, 4)

    g.color = AwtColor.WHITE
    var cursorY = startY + fm.ascent
    for (wLine in wrapped) {
        g.drawString(wLine, pad, cursorY)
        cursorY += lineHeight
    }

    // Minimal wordmark footer
    g.color = AwtColor(255, 255, 255, 150)
    g.font = Font(Font.SANS_SERIF, Font.BOLD, 24)
    g.drawString("M U S I C 0", pad, height - 84)

    g.dispose()
    return img
}
