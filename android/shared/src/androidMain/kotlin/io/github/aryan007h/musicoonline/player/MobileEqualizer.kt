package io.github.aryan007h.musicoonline.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import io.github.aryan007h.musicoonline.platform.EqSnapshot

/**
 * Android hardware audiofx Equalizer + BassBoost attached to ExoPlayer's audio session.
 */
object MobileEqualizer {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var attached = false
    private var prefs: SharedPreferences? = null

    private var enabled = false
    private var bandCount = 5
    private var bandLevels = IntArray(5)
    private var bandMinMb = -1500
    private var bandMaxMb = 1500
    private var bandFreqLabels = listOf("60Hz", "230Hz", "910Hz", "3.6k", "14k")
    private var bassBoostStrength = 0
    private var presetName: String? = null

    private val listeners = mutableListOf<() -> Unit>()

    fun attach(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            prefs?.let { loadFrom(it) }
        }
    }

    fun onAudioSessionId(sessionId: Int) {
        val p = prefs ?: return
        releaseEffects()
        if (sessionId <= 0) return
        attached = true
        equalizer = try {
            Equalizer(0, sessionId).also { eq ->
                bassBoost = try {
                    BassBoost(0, sessionId)
                } catch (_: Exception) {
                    null
                }
                loadFrom(p)
                bandCount = eq.numberOfBands.toInt()
                if (bandLevels.size < bandCount) {
                    bandLevels = IntArray(bandCount) { i -> bandLevels.getOrElse(i) { 0 } }
                }
                bandMinMb = eq.bandLevelRange[0].toInt()
                bandMaxMb = eq.bandLevelRange[1].toInt()
                bandFreqLabels = (0 until eq.numberOfBands).map { i ->
                    val hz = eq.getCenterFreq(i.toShort()) / 1000
                    if (hz >= 1000) "${hz / 1000}k" else "${hz}Hz"
                }
                applyToEffects()
            }
        } catch (_: Exception) {
            null
        }
        notifyListeners()
    }

    fun isAttached(): Boolean = attached

    fun detach() {
        releaseEffects()
        attached = false
        notifyListeners()
    }

    private fun releaseEffects() {
        equalizer?.release(); equalizer = null
        bassBoost?.release(); bassBoost = null
    }

    fun currentState(): EqSnapshot = EqSnapshot(
        enabled = enabled,
        bandCount = bandCount,
        bandLevels = bandLevels.toList(),
        bandMinMb = bandMinMb,
        bandMaxMb = bandMaxMb,
        bandFreqLabels = bandFreqLabels,
        bassBoost = bassBoostStrength,
        presetName = presetName,
    )

    fun setEnabled(value: Boolean) {
        enabled = value
        persist()
        applyToEffects()
        notifyListeners()
    }

    fun setBandLevel(index: Int, milliBel: Int) {
        if (index !in bandLevels.indices) return
        bandLevels[index] = milliBel.coerceIn(bandMinMb, bandMaxMb)
        presetName = null
        persist()
        applyToEffects()
        notifyListeners()
    }

    fun setBassBoost(strength: Int) {
        bassBoostStrength = strength.coerceIn(0, 1000)
        persist()
        applyToEffects()
        notifyListeners()
    }

    fun applyPresetGain(curve: List<Int>, name: String) {
        val out = IntArray(bandCount) { i ->
            curve.getOrElse(i) { 0 }.coerceIn(bandMinMb, bandMaxMb)
        }
        bandLevels = out
        presetName = name
        persist()
        applyToEffects()
        notifyListeners()
    }

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it() }
    }

    private fun applyToEffects() {
        equalizer?.let { eq ->
            try {
                eq.enabled = enabled
                if (enabled) {
                    bandLevels.forEachIndexed { i, lvl ->
                        if (i < eq.numberOfBands) eq.setBandLevel(i.toShort(), lvl.toShort())
                    }
                }
            } catch (_: Exception) {
            }
        }
        bassBoost?.let { bb ->
            try {
                bb.enabled = enabled && bassBoostStrength > 0
                bb.setStrength(bassBoostStrength.toShort())
            } catch (_: Exception) {
            }
        }
    }

    private fun loadFrom(p: SharedPreferences) {
        val levels = p.getString(KEY_LEVELS, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toIntArray()
            ?: IntArray(5) { 0 }
        enabled = p.getBoolean(KEY_ENABLED, false)
        bandLevels = levels
        bassBoostStrength = p.getInt(KEY_BASS, 0)
        presetName = p.getString(KEY_PRESET, null)
    }

    private fun persist() {
        val p = prefs ?: return
        p.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_LEVELS, bandLevels.joinToString(","))
            .putInt(KEY_BASS, bassBoostStrength)
            .putString(KEY_PRESET, presetName)
            .apply()
    }

    private const val FILE = "musico_eq"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LEVELS = "band_levels"
    private const val KEY_BASS = "bass_boost"
    private const val KEY_PRESET = "preset_name"
}
