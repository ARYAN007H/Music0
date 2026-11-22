package io.github.aryan007h.musicoonline

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.aryan007h.musicoonline.core.CoreException
import io.github.aryan007h.musicoonline.core.MusicoCoreCodec
import io.github.aryan007h.musicoonline.core.PlaylistImportResult
import io.github.aryan007h.musicoonline.core.PlaylistImportStatus
import io.github.aryan007h.musicoonline.core.ShelfItem
import io.github.aryan007h.musicoonline.platform.DesktopPlatformPrefs
import io.github.aryan007h.musicoonline.platform.PlatformEqualizer
import io.github.aryan007h.musicoonline.platform.mapFiveBandsToTenDb
import io.github.aryan007h.musicoonline.ui.LyricBackdrop
import io.github.aryan007h.musicoonline.ui.ResolveFailureAction
import io.github.aryan007h.musicoonline.ui.ResolveFailurePolicy
import io.github.aryan007h.musicoonline.ui.components.HorizontalScrollMath
import io.github.aryan007h.musicoonline.ui.components.HorizontalWheelAction
import io.github.aryan007h.musicoonline.ui.resolveLanguageGenreStyle
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
import kotlinx.serialization.json.Json
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
        val ok = MusicoCoreCodec.parseReply("""{"ok":true,"data":{"core":"rust-mobile","version":"1.0.0"}}""")
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

    @Test
    fun resolveLanguageGenreStyleMapsRegionalLanguagesToGenreCards() {
        val styles = listOf(
            ShelfItem(title = "Pop", browseId = "FEmusic_moods_and_genres_category", params = "pop"),
            ShelfItem(title = "K-Pop", browseId = "FEmusic_moods_and_genres_category", params = "kpop"),
            ShelfItem(title = "Hindi", browseId = "FEmusic_moods_and_genres_category", params = "hindi"),
            ShelfItem(title = "Unbrowsable English", browseId = null),
        )
        assertEquals("Pop", resolveLanguageGenreStyle("English", styles)?.title)
        assertEquals("K-Pop", resolveLanguageGenreStyle("Korean", styles)?.title)
        assertEquals("Hindi", resolveLanguageGenreStyle("Hindi", styles)?.title)
        assertNull(resolveLanguageGenreStyle("Esperanto", styles))
    }

    @Test
    fun playlistImportDtosDecodeWirePayload() {
        val json = Json { ignoreUnknownKeys = true }
        val res = json.decodeFromString(
            PlaylistImportResult.serializer(),
            """
            {
              "playlistId": "pl_123",
              "name": "Late Night Drives",
              "total": 10,
              "matched": 8,
              "unmatched": [
                {"title": "Rare Demo", "artist": "Indie Band"}
              ],
              "cancelled": false
            }
            """.trimIndent(),
        )
        assertEquals("pl_123", res.playlistId)
        assertEquals("Late Night Drives", res.name)
        assertEquals(10, res.total)
        assertEquals(8, res.matched)
        assertEquals(1, res.unmatched.size)
        assertEquals("Rare Demo", res.unmatched.first().title)

        val status = json.decodeFromString(
            PlaylistImportStatus.serializer(),
            """{"active":true,"name":"Late Night Drives","current":4,"total":10}""",
        )
        assertTrue(status.active)
        assertEquals(4, status.current)
        assertEquals(10, status.total)
    }

    @Test
    fun desktopHorizontalWheelConvertsShiftWheelAndPreservesVerticalScroll() {
        // Plain vertical wheel passes straight through to parent LazyColumn.
        val vertical = HorizontalScrollMath.decideHorizontalWheel(
            deltaX = 0f,
            deltaY = 1f,
            isShiftPressed = false,
            wheelStepPx = 96f,
        )
        assertEquals(HorizontalWheelAction.IGNORE, vertical.action)
        assertEquals(0f, vertical.deltaPx, 1e-4f)

        // Vertically-dominant diagonal touchpad scroll also passes through.
        val mostlyVertical = HorizontalScrollMath.decideHorizontalWheel(
            deltaX = 0.2f,
            deltaY = 1.5f,
            isShiftPressed = false,
            wheelStepPx = 96f,
        )
        assertEquals(HorizontalWheelAction.IGNORE, mostlyVertical.action)

        // Shift + vertical wheel (Linux Wayland/X11) converts to smooth horizontal scroll.
        val shiftWheel = HorizontalScrollMath.decideHorizontalWheel(
            deltaX = 0f,
            deltaY = 1f,
            isShiftPressed = true,
            wheelStepPx = 96f,
        )
        assertEquals(HorizontalWheelAction.SCROLL_HORIZONTAL, shiftWheel.action)
        assertEquals(96f, shiftWheel.deltaPx, 1e-4f)
        assertTrue(shiftWheel.smooth)

        // Fine 2-finger horizontal touchpad swipe maps directly (non-smooth 1:1 tracking).
        val trackpad = HorizontalScrollMath.decideHorizontalWheel(
            deltaX = -0.35f,
            deltaY = 0.05f,
            isShiftPressed = false,
            wheelStepPx = 96f,
        )
        assertEquals(HorizontalWheelAction.SCROLL_HORIZONTAL, trackpad.action)
        assertEquals(-33.6f, trackpad.deltaPx, 1e-3f)
        assertTrue(!trackpad.smooth)
    }

    @Test
    fun desktopHorizontalScrollGeometryAndPagerSnapAreBounded() {
        assertEquals(240f, HorizontalScrollMath.computePageStepPx(0f), 1e-4f)
        assertEquals(780f, HorizontalScrollMath.computePageStepPx(1000f, fraction = 0.78f), 1e-4f)

        // Below velocity deadzone -> zero fling.
        assertEquals(0f, HorizontalScrollMath.computeDragFlingPx(120f, 1000f), 1e-4f)
        // Dragging mouse left (negative velocity) glides content forward (positive scroll).
        assertTrue(HorizontalScrollMath.computeDragFlingPx(-800f, 1000f) > 0f)
        // Clamped to viewport * 1.15.
        assertEquals(1150f, HorizontalScrollMath.computeDragFlingPx(-20_000f, 1000f), 1e-4f)

        // Pager snapping respects drag threshold, flick velocity, and bounds.
        assertEquals(1, HorizontalScrollMath.computePagerSnapTarget(0, 4, -260f, 0f, 800f))
        assertEquals(0, HorizontalScrollMath.computePagerSnapTarget(0, 4, -40f, 0f, 800f))
        assertEquals(0, HorizontalScrollMath.computePagerSnapTarget(0, 4, 300f, 500f, 800f))
        assertEquals(3, HorizontalScrollMath.computePagerSnapTarget(3, 4, -300f, -600f, 800f))

        // Wheel pager stepping handles Shift+Wheel and ignores plain vertical wheel.
        assertEquals(0, HorizontalScrollMath.computeWheelPagerStep(0f, 1f, isShiftPressed = false))
        assertEquals(1, HorizontalScrollMath.computeWheelPagerStep(0f, 1f, isShiftPressed = true))
        assertEquals(-1, HorizontalScrollMath.computeWheelPagerStep(-1f, 0f, isShiftPressed = false))

        // Grid scrollbar thumb geometry stays bounded in [0, 1].
        val thumbStart = HorizontalScrollMath.computeGridScrollbarThumb(
            totalItems = 20,
            rowCount = 2,
            firstVisibleColumn = 0,
            firstColumnHiddenRatio = 0f,
            visibleColumns = 4f,
        )
        assertEquals(0.4f, thumbStart.fraction, 1e-4f)
        assertEquals(0f, thumbStart.progress, 1e-4f)

        val thumbEnd = HorizontalScrollMath.computeGridScrollbarThumb(
            totalItems = 20,
            rowCount = 2,
            firstVisibleColumn = 6,
            firstColumnHiddenRatio = 0f,
            visibleColumns = 4f,
        )
        assertEquals(1f, thumbEnd.progress, 1e-4f)
    }
}
