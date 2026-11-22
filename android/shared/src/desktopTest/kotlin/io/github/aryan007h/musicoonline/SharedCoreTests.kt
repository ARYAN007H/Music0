package io.github.aryan007h.musicoonline

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.aryan007h.musicoonline.core.CoreException
import io.github.aryan007h.musicoonline.core.MusicoCoreCodec
import io.github.aryan007h.musicoonline.platform.DesktopPlatformPrefs
import io.github.aryan007h.musicoonline.platform.PlatformEqualizer
import io.github.aryan007h.musicoonline.platform.mapFiveBandsToTenDb
import io.github.aryan007h.musicoonline.ui.LyricBackdrop
import io.github.aryan007h.musicoonline.ui.ResolveFailureAction
import io.github.aryan007h.musicoonline.ui.ResolveFailurePolicy
import io.github.aryan007h.musicoonline.ui.library.formatDownloadsSummary
import io.github.aryan007h.musicoonline.ui.library.formatSongCount
import io.github.aryan007h.musicoonline.ui.library.formatTrackCount
import io.github.aryan007h.musicoonline.ui.player.ArtworkAtmosphere
import io.github.aryan007h.musicoonline.ui.player.ArtworkPaletteCache
import io.github.aryan007h.musicoonline.ui.player.formatTime
import io.github.aryan007h.musicoonline.ui.settings.snapCacheCapMb
import io.github.aryan007h.musicoonline.ui.theme.Palette
import io.github.aryan007h.musicoonline.ui.theme.safeCornerDp
import io.github.aryan007h.musicoonline.ui.theme.safePositiveScale
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SharedCoreTests {

    @Test
    fun codecParsesSuccessAndErrorEnvelopes() {
        val ok = MusicoCoreCodec.parseReply("""{"ok":true,"data":{"core":"rust-mobile","version":"0.9.6"}}""")
        assertTrue(ok is JsonObject)
        assertEquals("rust-mobile", ok["core"]?.jsonPrimitive?.content)

        val err = assertFailsWith<CoreException> {
            MusicoCoreCodec.parseReply("""{"ok":false,"error":"track unavailable"}""")
        }
        assertEquals("track unavailable", err.message)
    }

    @Test
    fun desktopPrefsRoundTripsAllTypesAcrossRestarts() {
        val tempDir = createTempDirectory("musico_prefs_test").toFile()
        val file = File(tempDir, "ui_prefs.json")
        try {
            val writer = DesktopPlatformPrefs(file)
            writer.putString("display_name", "Aryan")
            writer.putBoolean("skip_silence", true)
            writer.putFloat("playback_speed", 1.25f)
            writer.putInt("lyrics_mode", 1)
            writer.putLong("offset_ms", -250L)

            val reader = DesktopPlatformPrefs(file)
            assertEquals("Aryan", reader.getString("display_name", null))
            assertEquals(true, reader.getBoolean("skip_silence", false))
            assertEquals(1.25f, reader.getFloat("playback_speed", 1f))
            assertEquals(1, reader.getInt("lyrics_mode", 0))
            assertEquals(-250L, reader.getLong("offset_ms", 0L))

            writer.putString("display_name", null)
            assertNull(DesktopPlatformPrefs(file).getString("display_name", null))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun artworkFallbackAtmosphereIsDeterministicAndAccentTinted() {
        val accent = Color(0xFF7C6BC4)
        val fallback = ArtworkAtmosphere(
            base = Color(0xFF0A0A14),
            orbPrimary = accent,
            orbSecondary = accent,
            glow = accent,
        )
        // No decodable pixels → the fallback passes through untouched, deterministically.
        assertEquals(fallback, ArtworkPaletteCache.quantizePixels(IntArray(0), fallback))

        // A solid-accent thumbnail quantizes to a deterministic, opaque palette.
        val pixels = IntArray(64) { 0xFF7C6BC4.toInt() }
        val fromAccent = ArtworkPaletteCache.quantizePixels(pixels, fallback)
        val fromAccent2 = ArtworkPaletteCache.quantizePixels(pixels, fallback)
        assertEquals(fromAccent, fromAccent2)
        assertNotEquals(Color.Transparent, fromAccent.base)
        assertNotEquals(Color.Transparent, fromAccent.orbPrimary)
    }

    @Test
    fun formatTimeAndEnumLookupsHandleEdgeCases() {
        assertEquals("0:00", formatTime(0L))
        assertEquals("0:00", formatTime(-5000L))
        assertEquals("3:05", formatTime(185_000L))
        assertEquals("60:00", formatTime(3_600_000L))

        assertEquals(LyricBackdrop.Mesh, LyricBackdrop.fromId("unknown"))
        assertEquals(LyricBackdrop.Aurora, LyricBackdrop.fromId("aurora"))
        assertEquals(Palette.NEBULA, Palette.fromId("does-not-exist"))
    }

    @Test
    fun resolveFailurePolicyNeverAutoSkipsUserTappedTracks() {
        assertEquals(
            ResolveFailureAction.SHOW_RETRY_TOAST,
            ResolveFailurePolicy.decide(
                userInitiated = true,
                isOffline = false,
                hasMultipleInQueue = true,
                consecutiveAutoSkips = 0,
            ),
        )
        assertEquals(
            ResolveFailureAction.AUTO_SKIP,
            ResolveFailurePolicy.decide(
                userInitiated = false,
                isOffline = false,
                hasMultipleInQueue = true,
                consecutiveAutoSkips = 0,
            ),
        )
        assertEquals(
            ResolveFailureAction.STOP_AUTO_SKIP,
            ResolveFailurePolicy.decide(
                userInitiated = false,
                isOffline = false,
                hasMultipleInQueue = true,
                consecutiveAutoSkips = 3,
                maxAutoSkips = 3,
            ),
        )
        assertEquals(
            ResolveFailureAction.SHOW_RETRY_TOAST,
            ResolveFailurePolicy.decide(
                userInitiated = false,
                isOffline = true,
                hasMultipleInQueue = true,
                consecutiveAutoSkips = 0,
            ),
        )
    }

    @Test
    fun streamCacheCapSnapsTo256MbStepsAndClampsRange() {
        assertEquals(512, snapCacheCapMb(0f))
        assertEquals(512, snapCacheCapMb(600f))
        assertEquals(768, snapCacheCapMb(641f))
        assertEquals(1024, snapCacheCapMb(1024f))
        assertEquals(2048, snapCacheCapMb(2100f))
        assertEquals(5120, snapCacheCapMb(9999f))
    }

    @Test
    fun desktopEqualizerMapsFiveBandsToTenAndPersistsAcrossRestarts() {
        val ten = mapFiveBandsToTenDb(listOf(600, 300, 0, -300, 600), bassBoostStrength = 500)
        assertEquals(10, ten.size)
        // Band 0 = 6.0 dB + 3.0 dB bass boost = 9.0 dB
        assertEquals(9.0, ten[0], 1e-6)
        assertEquals(3.0, ten[3], 1e-6)
        assertEquals(0.0, ten[5], 1e-6)
        assertEquals(-3.0, ten[7], 1e-6)
        assertEquals(6.0, ten[9], 1e-6)

        // Clamping extreme input values keeps every output band within [-12, +12] dB.
        val clamped = mapFiveBandsToTenDb(listOf(2500, -2500, 0, 0, 0), bassBoostStrength = 2000)
        assertTrue(clamped.all { it in -12.0..12.0 })

        val tempDir = createTempDirectory("musico_eq_test").toFile()
        val file = File(tempDir, "eq_prefs.json")
        try {
            val prefs1 = DesktopPlatformPrefs(file)
            PlatformEqualizer.init(prefs1)
            PlatformEqualizer.setEnabled(false)
            PlatformEqualizer.applyPresetGain(listOf(500, 200, 0, 100, 400), "Bass Boost")
            PlatformEqualizer.setBassBoost(600)

            // Non-flat preset auto-enables EQ and persists to disk.
            val afterApply = PlatformEqualizer.currentState()
            assertTrue(afterApply.enabled)
            assertEquals("Bass Boost", afterApply.presetName)
            assertEquals(600, afterApply.bassBoost)
            assertEquals(listOf(500, 200, 0, 100, 400), afterApply.bandLevels)

            // Re-initializing from a fresh prefs instance restores the exact state.
            val prefs2 = DesktopPlatformPrefs(file)
            PlatformEqualizer.init(prefs2)
            val restored = PlatformEqualizer.currentState()
            assertTrue(restored.enabled)
            assertEquals("Bass Boost", restored.presetName)
            assertEquals(600, restored.bassBoost)
            assertEquals(listOf(500, 200, 0, 100, 400), restored.bandLevels)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun copyFormattersPluralizeTrackSongAndDownloadCounts() {
        assertEquals("0 tracks", formatTrackCount(0))
        assertEquals("1 track", formatTrackCount(1))
        assertEquals("6 tracks", formatTrackCount(6))

        assertEquals("0 songs", formatSongCount(0))
        assertEquals("1 song", formatSongCount(1))
        assertEquals("30 songs", formatSongCount(30))

        assertEquals("No cached tracks", formatDownloadsSummary(0, 0L))
        assertEquals("1 song", formatDownloadsSummary(1, 0L))
        assertEquals("30 songs · 307 MB", formatDownloadsSummary(30, 307L * 1024L * 1024L))
    }

    @Test
    fun motionGeometryHelpersClampNegativeSpringOvershoot() {
        assertEquals(0.dp, safeCornerDp((-0.06f).dp))
        assertEquals(0.dp, safeCornerDp((-24).dp))
        assertEquals(18.dp, safeCornerDp(18.dp))

        assertEquals(0.01f, safePositiveScale(-0.5f), 1e-6f)
        assertEquals(0.01f, safePositiveScale(0f), 1e-6f)
        assertEquals(0.96f, safePositiveScale(0.96f), 1e-6f)
    }
}
