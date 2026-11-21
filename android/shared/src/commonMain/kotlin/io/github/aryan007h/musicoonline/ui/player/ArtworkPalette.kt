package io.github.aryan007h.musicoonline.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import io.github.aryan007h.musicoonline.platform.decodeArtworkSamplePixels
import io.github.aryan007h.musicoonline.ui.theme.Musico
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Four atmospheric colors extracted from the active track's cover art.
 * Luminance is strictly calibrated so pure-white lyrics always maintain
 * strong readability over the living mesh backdrop.
 */
@Immutable
data class ArtworkAtmosphere(
    val base: Color,
    val orbPrimary: Color,
    val orbSecondary: Color,
    val glow: Color,
)

/**
 * Cross-platform 18-hue-bin HSV cover color quantizer + bounded LRU cache.
 * Runs identically on Android and Desktop JVM using pure Kotlin math.
 */
object ArtworkPaletteCache {
    private const val MAX_ENTRIES = 64
    private val cache = LinkedHashMap<String, ArtworkAtmosphere>(MAX_ENTRIES, 0.75f, true)

    fun get(url: String): ArtworkAtmosphere? = synchronizedLock { cache[url] }

    private fun put(url: String, value: ArtworkAtmosphere) = synchronizedLock {
        cache[url] = value
        if (cache.size > MAX_ENTRIES) {
            val eldest = cache.keys.firstOrNull()
            if (eldest != null) cache.remove(eldest)
        }
    }

    suspend fun extract(url: String?, fallback: ArtworkAtmosphere): ArtworkAtmosphere {
        if (url.isNullOrBlank()) return fallback
        get(url)?.let { return it }
        val pixels = runCatching { decodeArtworkSamplePixels(url) }.getOrNull() ?: return fallback
        return quantizePixels(pixels, fallback).also { put(url, it) }
    }

    fun quantizePixels(argbPixels: IntArray, fallback: ArtworkAtmosphere): ArtworkAtmosphere {
        if (argbPixels.isEmpty()) return fallback

        val binCount = 18
        val binWeight = FloatArray(binCount)
        val binR = FloatArray(binCount)
        val binG = FloatArray(binCount)
        val binB = FloatArray(binCount)
        val hsv = FloatArray(3)

        for (px in argbPixels) {
            val a = (px ushr 24) and 0xFF
            if (a < 128) continue
            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF
            rgbToHsv(r, g, b, hsv)
            val sat = hsv[1]
            val v = hsv[2]
            if (v in 0.12f..0.96f) {
                val weight = 0.25f + sat * sat * 2.2f + (1f - abs(v - 0.55f)) * 0.6f
                val bin = ((hsv[0] / 360f) * binCount).toInt().coerceIn(0, binCount - 1)
                binWeight[bin] += weight
                binR[bin] += r * weight
                binG[bin] += g * weight
                binB[bin] += b * weight
            }
        }

        val ranked = (0 until binCount)
            .filter { binWeight[it] > 0.5f }
            .sortedByDescending { binWeight[it] }

        if (ranked.isEmpty()) return fallback

        val bestIdx = ranked.first()
        val secondIdx = ranked.firstOrNull { idx ->
            val dist = minOf(abs(idx - bestIdx), binCount - abs(idx - bestIdx))
            dist >= 3
        } ?: ranked.getOrElse(1) { bestIdx }

        val pHsv = FloatArray(3)
        val sHsv = FloatArray(3)
        val wt1 = binWeight[bestIdx].coerceAtLeast(1f)
        rgbToHsv(
            (binR[bestIdx] / wt1).toInt().coerceIn(0, 255),
            (binG[bestIdx] / wt1).toInt().coerceIn(0, 255),
            (binB[bestIdx] / wt1).toInt().coerceIn(0, 255),
            pHsv,
        )
        val wt2 = binWeight[secondIdx].coerceAtLeast(1f)
        rgbToHsv(
            (binR[secondIdx] / wt2).toInt().coerceIn(0, 255),
            (binG[secondIdx] / wt2).toInt().coerceIn(0, 255),
            (binB[secondIdx] / wt2).toInt().coerceIn(0, 255),
            sHsv,
        )

        val baseColor = hsvToColor(pHsv[0], (pHsv[1] * 0.72f).coerceIn(0.28f, 0.72f), 0.10f)
        val orbPrimary = hsvToColor(pHsv[0], pHsv[1].coerceIn(0.45f, 0.88f), pHsv[2].coerceIn(0.42f, 0.68f))
        val secHue = if (secondIdx == bestIdx) (pHsv[0] + 42f) % 360f else sHsv[0]
        val orbSecondary = hsvToColor(secHue, sHsv[1].coerceIn(0.38f, 0.82f), sHsv[2].coerceIn(0.36f, 0.62f))
        val glow = hsvToColor(pHsv[0], pHsv[1].coerceIn(0.28f, 0.65f), 0.96f)

        return ArtworkAtmosphere(
            base = baseColor,
            orbPrimary = orbPrimary,
            orbSecondary = orbSecondary,
            glow = glow,
        )
    }

    private inline fun <T> synchronizedLock(block: () -> T): T = synchronized(cache) { block() }

    private fun rgbToHsv(r: Int, g: Int, b: Int, out: FloatArray) {
        val rf = r / 255f
        val gf = g / 255f
        val bf = b / 255f
        val cMax = max(rf, max(gf, bf))
        val cMin = min(rf, min(gf, bf))
        val delta = cMax - cMin

        val h = when {
            delta < 1e-5f -> 0f
            cMax == rf -> 60f * (((gf - bf) / delta) % 6f)
            cMax == gf -> 60f * (((bf - rf) / delta) + 2f)
            else -> 60f * (((rf - gf) / delta) + 4f)
        }.let { if (it < 0f) it + 360f else it }

        val s = if (cMax < 1e-5f) 0f else delta / cMax
        out[0] = h
        out[1] = s
        out[2] = cMax
    }

    private fun hsvToColor(h: Float, s: Float, v: Float): Color {
        val hue = ((h % 360f) + 360f) % 360f
        val c = v * s
        val x = c * (1f - abs((hue / 60f) % 2f - 1f))
        val m = v - c
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            red = (r1 + m).coerceIn(0f, 1f),
            green = (g1 + m).coerceIn(0f, 1f),
            blue = (b1 + m).coerceIn(0f, 1f),
            alpha = 1f,
        )
    }
}

/**
 * Smoothly cross-dissolved cover atmosphere: whenever [artworkUrl] changes,
 * the four colors melt across 950 ms so the whole app breathes as one.
 */
@Composable
fun rememberArtworkAtmosphere(artworkUrl: String?): ArtworkAtmosphere {
    val extras = Musico.extras
    val fallback = remember(extras) {
        ArtworkAtmosphere(
            base = Color(0xFF0A0A14),
            orbPrimary = extras.primary,
            orbSecondary = extras.support,
            glow = extras.mint,
        )
    }
    var target by remember(artworkUrl) {
        mutableStateOf(artworkUrl?.let { ArtworkPaletteCache.get(it) } ?: fallback)
    }

    LaunchedEffect(artworkUrl, fallback) {
        target = ArtworkPaletteCache.extract(artworkUrl, fallback)
    }

    val base by animateColorAsState(target.base, tween(950), label = "artBase")
    val orbPrimary by animateColorAsState(target.orbPrimary, tween(950), label = "artOrb1")
    val orbSecondary by animateColorAsState(target.orbSecondary, tween(950), label = "artOrb2")
    val glow by animateColorAsState(target.glow, tween(950), label = "artGlow")

    return ArtworkAtmosphere(
        base = base,
        orbPrimary = orbPrimary,
        orbSecondary = orbSecondary,
        glow = glow,
    )
}
