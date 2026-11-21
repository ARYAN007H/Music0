package io.github.aryan007h.musicoonline

import androidx.compose.ui.graphics.Color
import io.github.aryan007h.musicoonline.core.CoreException
import io.github.aryan007h.musicoonline.core.MusicoCoreCodec
import io.github.aryan007h.musicoonline.platform.DesktopPlatformPrefs
import io.github.aryan007h.musicoonline.ui.LyricBackdrop
import io.github.aryan007h.musicoonline.ui.player.ArtworkAtmosphere
import io.github.aryan007h.musicoonline.ui.player.ArtworkPaletteCache
import io.github.aryan007h.musicoonline.ui.player.formatTime
import io.github.aryan007h.musicoonline.ui.theme.Palette
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
}
