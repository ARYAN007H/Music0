package io.github.aryan007h.musicoonline.player

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import io.github.aryan007h.musicoonline.platform.EqSnapshot

/**
 * Android hardware audiofx Equalizer + BassBoost attached to ExoPlayer's audio session.
 */
object MobileEqualizer {

    private const val TAG = "Music0EQ"

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var attached = false
    private var currentSessionId = 0
    private var prefs: SharedPreferences? = null
    private var prefsLoaded = false

    private var enabled = false
    private var bandCount = 5
    private var bandLevels = IntArray(5)
    private var bandMinMb = -1500
    private var bandMaxMb = 1500
    private var bandFreqLabels = listOf("60Hz", "230Hz", "910Hz", "3.6k", "14k")
    private var bassBoostStrength = 0
    private var presetName: String? = null

    private val listeners = mutableListOf<() -> Unit>()

    @Synchronized
    fun attach(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
        if (!prefsLoaded) {
            prefs?.let { loadFrom(it) }
            prefsLoaded = true
            Log.i(
                TAG,
                "init: loaded prefs enabled=$enabled bands=${bandLevels.toList()} bass=$bassBoostStrength preset=$presetName",
            )
        }
    }

    @Synchronized
    fun onAudioSessionId(sessionId: Int) {
        Log.i(TAG, "onAudioSessionId: incoming=$sessionId previous=$currentSessionId hasEq=${equalizer != null}")
        if (sessionId <= 0) {
            releaseEffects()
            currentSessionId = 0
            attached = false
            notifyListeners()
            return
        }
        if (sessionId == currentSessionId && equalizer != null) {
            val hasControl = runCatching { equalizer?.hasControl() == true }.getOrDefault(false)
            if (hasControl) {
                Log.i(TAG, "onAudioSessionId: reusing healthy session=$sessionId; reapplying state")
                applyToEffects(reason = "same-session-reapply")
                notifyListeners()
                return
            }
            Log.w(TAG, "onAudioSessionId: lost control on session=$sessionId; recreating AudioFX")
        }

        releaseEffects()
        currentSessionId = sessionId
        val created = createEqualizer(sessionId)
        if (created == null) {
            attached = false
            Log.w(TAG, "onAudioSessionId: failed to create Equalizer for session=$sessionId")
            notifyListeners()
            return
        }

        equalizer = created
        attached = true
        bassBoost = runCatching { BassBoost(1, sessionId) }
            .recoverCatching { BassBoost(0, sessionId) }
            .onFailure { e -> Log.w(TAG, "BassBoost unavailable on session=$sessionId: ${e.message}") }
            .getOrNull()

        runCatching {
            val hwBands = created.numberOfBands.toInt().coerceAtLeast(1)
            bandCount = hwBands
            if (bandLevels.size != bandCount) {
                bandLevels = IntArray(bandCount) { i -> bandLevels.getOrElse(i) { 0 } }
            }
            val range = created.bandLevelRange
            bandMinMb = range[0].toInt()
            bandMaxMb = range[1].toInt()
            for (i in bandLevels.indices) {
                bandLevels[i] = bandLevels[i].coerceIn(bandMinMb, bandMaxMb)
            }
            bandFreqLabels = (0 until hwBands).map { i ->
                val hz = created.getCenterFreq(i.toShort()) / 1000
                if (hz >= 1000) "${hz / 1000}k" else "${hz}Hz"
            }
            Log.i(
                TAG,
                "attached session=$sessionId bands=$bandCount range=[$bandMinMb..$bandMaxMb]mB freqs=$bandFreqLabels hasControl=${created.hasControl()} bassSupported=${bassBoost?.strengthSupported}",
            )
        }.onFailure { e ->
            Log.w(TAG, "failed reading Equalizer capabilities: ${e.message}")
        }

        applyToEffects(reason = "attach")
        notifyListeners()
    }

    /**
     * Re-verifies AudioFX attachment and re-pushes gains on track transitions or
     * playback resume so OEM audio HALs cannot silently drop EQ state.
     */
    @Synchronized
    fun ensureApplied(sessionId: Int, reason: String = "ensure") {
        if (sessionId <= 0) return
        val eq = equalizer
        val healthy = eq != null &&
            sessionId == currentSessionId &&
            runCatching { eq.hasControl() }.getOrDefault(false)
        if (!healthy) {
            Log.i(TAG, "ensureApplied($reason): re-attaching session=$sessionId (prev=$currentSessionId)")
            onAudioSessionId(sessionId)
        } else {
            applyToEffects(reason = reason)
        }
    }

    private fun createEqualizer(sessionId: Int): Equalizer? =
        runCatching { Equalizer(1, sessionId) }
            .recoverCatching { Equalizer(0, sessionId) }
            .onFailure { e -> Log.w(TAG, "Equalizer(session=$sessionId) threw: ${e.message}") }
            .getOrNull()

    fun isAttached(): Boolean = attached && equalizer != null

    @Synchronized
    fun detach() {
        Log.i(TAG, "detach: releasing session=$currentSessionId")
        releaseEffects()
        currentSessionId = 0
        attached = false
        notifyListeners()
    }

    private fun releaseEffects() {
        runCatching { equalizer?.release() }
        equalizer = null
        runCatching { bassBoost?.release() }
        bassBoost = null
    }

    @Synchronized
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

    @Synchronized
    fun setEnabled(value: Boolean) {
        enabled = value
        persist()
        applyToEffects(reason = "setEnabled($value)")
        notifyListeners()
    }

    @Synchronized
    fun setBandLevel(index: Int, milliBel: Int) {
        if (index !in bandLevels.indices) return
        bandLevels[index] = milliBel.coerceIn(bandMinMb, bandMaxMb)
        presetName = null
        persist()
        applyToEffects(reason = "setBand($index=${bandLevels[index]})")
        notifyListeners()
    }

    @Synchronized
    fun setBassBoost(strength: Int) {
        bassBoostStrength = strength.coerceIn(0, 1000)
        persist()
        applyToEffects(reason = "setBass($bassBoostStrength)")
        notifyListeners()
    }

    @Synchronized
    fun applyPresetGain(curve: List<Int>, name: String) {
        val out = IntArray(bandCount) { i ->
            curve.getOrElse(i) { 0 }.coerceIn(bandMinMb, bandMaxMb)
        }
        bandLevels = out
        presetName = name
        if (out.any { it != 0 } && !enabled) {
            enabled = true
        }
        persist()
        applyToEffects(reason = "preset($name)")
        notifyListeners()
    }

    @Synchronized
    fun addListener(l: () -> Unit) {
        if (l !in listeners) listeners.add(l)
    }

    @Synchronized
    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }

    private fun notifyListeners() {
        val snapshot = synchronized(this) { listeners.toList() }
        snapshot.forEach { it() }
    }

    private fun applyToEffects(reason: String) {
        equalizer?.let { eq ->
            try {
                // Apply bands both before and after enabling so devices that ignore
                // setBandLevel while disabled (or reset on enable) honor every band.
                if (enabled) {
                    val count = minOf(bandLevels.size, eq.numberOfBands.toInt())
                    for (i in 0 until count) {
                        eq.setBandLevel(i.toShort(), bandLevels[i].toShort())
                    }
                }
                val enableStatus = eq.setEnabled(enabled)
                if (enabled) {
                    val count = minOf(bandLevels.size, eq.numberOfBands.toInt())
                    for (i in 0 until count) {
                        eq.setBandLevel(i.toShort(), bandLevels[i].toShort())
                    }
                }
                val readback = (0 until eq.numberOfBands.toInt()).map { i ->
                    eq.getBandLevel(i.toShort()).toInt()
                }
                Log.i(
                    TAG,
                    "apply($reason): session=$currentSessionId reqEnabled=$enabled hwEnabled=${eq.enabled} status=$enableStatus hasControl=${eq.hasControl()} req=${bandLevels.toList()} hwReadback=$readback",
                )
            } catch (e: Exception) {
                Log.w(TAG, "apply($reason): Equalizer failed on session=$currentSessionId: ${e.message}")
            }
        } ?: Log.i(TAG, "apply($reason): deferred (no active audio session yet)")

        bassBoost?.let { bb ->
            try {
                val wantBass = enabled && bassBoostStrength > 0
                val status = bb.setEnabled(wantBass)
                if (bb.strengthSupported) {
                    bb.setStrength(if (wantBass) bassBoostStrength.toShort() else 0)
                }
                Log.i(
                    TAG,
                    "apply($reason): bass req=$bassBoostStrength wantOn=$wantBass hwOn=${bb.enabled} status=$status rounded=${bb.roundedStrength}",
                )
            } catch (e: Exception) {
                Log.w(TAG, "apply($reason): BassBoost failed: ${e.message}")
            }
        }
    }

    private fun loadFrom(p: SharedPreferences) {
        val levels = p.getString(KEY_LEVELS, null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull() }
            ?.toIntArray()
            ?.takeIf { it.isNotEmpty() }
            ?: IntArray(5) { 0 }
        enabled = p.getBoolean(KEY_ENABLED, false)
        bandLevels = levels
        bassBoostStrength = p.getInt(KEY_BASS, 0).coerceIn(0, 1000)
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
