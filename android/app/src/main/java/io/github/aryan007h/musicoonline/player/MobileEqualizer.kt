package io.github.aryan007h.musicoonline.player

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer

/**
 * Mobile EQ: the Rust engine's DSP chain (set_eq / AutoEQ / normalization)
 * sits on the cpal playback engine, which is deliberately NOT initialized
 * on mobile — audio here is Media3/ExoPlayer playing resolved local files.
 * So this is a REAL equalizer at the right place: Android's audiofx stack
 * attached to the ExoPlayer audio session, wired by [PlaybackService] on
 * every session id change and driven by Settings through this singleton
 * (service and UI share the process).
 *
 * State persists via [EqPrefs]; apply() is idempotent and safe to call
 * before the player exists (deferred until attach).
 */
object MobileEqualizer {

    /** UI-facing snapshot — band levels in millibels, -1500..1500. */
    data class EqState(
        var enabled: Boolean,
        var bandCount: Int,
        var bandLevels: IntArray,
        var bandRange: IntRange,
        var bandFreqLabels: List<String>,
        var bassBoost: Int, // 0..1000 strength
        var presetName: String?,
    )

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null

    // ── Persistence (SharedPreferences; created lazily with the app context) ──

    private var prefs: android.content.SharedPreferences? = null
    private val state = EqState(
        enabled = false,
        bandCount = 0,
        bandLevels = IntArray(0),
        bandRange = -1500..1500,
        bandFreqLabels = emptyList(),
        bassBoost = 0,
        presetName = null,
    )

    fun attach(context: android.content.Context) {
        if (prefs == null) {
            prefs = context.getSharedPreferences(EqPrefs.FILE, android.content.Context.MODE_PRIVATE)
        }
    }

    /** Called by [PlaybackService] whenever ExoPlayer reports a session id. */
    fun onAudioSessionId(sessionId: Int) {
        val p = prefs ?: return
        releaseEffects()
        if (sessionId <= 0) return
        equalizer = try {
            Equalizer(0, sessionId).also { eq ->
                bassBoost = try {
                    BassBoost(0, sessionId)
                } catch (_: Exception) {
                    null
                }
                val saved = EqPrefs.load(p)
                state.enabled = saved.enabled
                state.bandCount = eq.numberOfBands.toInt()
                state.bandLevels = saved.bandLevels.clone()
                state.bandRange = eq.bandLevelRange.let { it[0]..it[1] }
                state.bandFreqLabels = (0 until eq.numberOfBands).map { i ->
                    // getCenterFreq is in milli-Hz; ladder 60Hz … 14kHz.
                    val hz = eq.getCenterFreq(i.toShort()) / 1000
                    if (hz >= 1000) "${hz / 1000}k" else "${hz}Hz"
                }
                state.bassBoost = saved.bassBoost
                state.presetName = saved.presetName
                applyToEffects()
            }
        } catch (_: Exception) {
            // Device without the effect (rare) — the UI shows a disabled state.
            null
        }
        notifyListeners()
    }

    fun detach() {
        releaseEffects()
    }

    private fun releaseEffects() {
        equalizer?.release(); equalizer = null
        bassBoost?.release(); bassBoost = null
    }

    // ── UI API ───────────────────────────────────────────────────────────

    fun currentState(): EqState = state

    fun setEnabled(enabled: Boolean) {
        state.enabled = enabled
        persist()
        applyToEffects()
        notifyListeners()
    }

    fun setBandLevel(index: Int, milliBel: Int) {
        if (index !in state.bandLevels.indices) return
        state.bandLevels[index] = milliBel.coerceIn(state.bandRange)
        state.presetName = null // custom curve
        persist()
        applyToEffects()
        notifyListeners()
    }

    fun setBassBoost(strength: Int) {
        state.bassBoost = strength.coerceIn(0, 1000)
        persist()
        applyToEffects()
        notifyListeners()
    }

    /** Applies a preset's center gains to our bands (device preset list varies). */
    fun applyPresetGain(curve: List<Int>, presetName: String) {
        state.bandLevels = curve.take(state.bandCount).map { it.coerceIn(state.bandRange) }.toIntArray()
        state.presetName = presetName
        persist()
        applyToEffects()
        notifyListeners()
    }

    private fun applyToEffects() {
        equalizer?.let { eq ->
            try {
                eq.enabled = state.enabled
                if (state.enabled) {
                    state.bandLevels.forEachIndexed { i, lvl ->
                        if (i < eq.numberOfBands) eq.setBandLevel(i.toShort(), lvl.toShort())
                    }
                }
            } catch (_: Exception) {
                // Effect detached mid-apply (session died) — next attach fixes it.
            }
        }
        bassBoost?.let { bb ->
            try {
                bb.enabled = state.enabled && state.bassBoost > 0
                bb.setStrength(state.bassBoost.toShort())
            } catch (_: Exception) {
            }
        }
    }

    // ── Change notification (same process → simple listener list) ────────

    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(l: () -> Unit) {
        listeners.add(l)
    }

    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun notifyListeners() {
        listeners.forEach { it() }
    }

    private fun persist() {
        EqPrefs.save(prefs ?: return, state)
    }
}

private object EqPrefs {
    const val FILE = "musico_eq"

    fun load(p: android.content.SharedPreferences): EqStateSnapshot {
        val levels = p.getString(KEY_LEVELS, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toIntArray()
            ?: IntArray(5) { 0 }
        return EqStateSnapshot(
            enabled = p.getBoolean(KEY_ENABLED, false),
            bandLevels = levels,
            bassBoost = p.getInt(KEY_BASS, 0),
            presetName = p.getString(KEY_PRESET, null),
        )
    }

    fun save(p: android.content.SharedPreferences, s: MobileEqualizer.EqState) {
        p.edit()
            .putBoolean(KEY_ENABLED, s.enabled)
            .putString(KEY_LEVELS, s.bandLevels.joinToString(","))
            .putInt(KEY_BASS, s.bassBoost)
            .putString(KEY_PRESET, s.presetName)
            .apply()
    }

    private const val KEY_ENABLED = "enabled"
    private const val KEY_LEVELS = "band_levels"
    private const val KEY_BASS = "bass_boost"
    private const val KEY_PRESET = "preset_name"
}

private data class EqStateSnapshot(
    val enabled: Boolean,
    val bandLevels: IntArray,
    val bassBoost: Int,
    val presetName: String?,
)
