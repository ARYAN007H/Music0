package io.github.aryan007h.musicoonline

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aryan007h.musicoonline.core.CatalogEntry
import io.github.aryan007h.musicoonline.core.CoreException
import io.github.aryan007h.musicoonline.core.DownloadedTrack
import io.github.aryan007h.musicoonline.core.ItemKind
import io.github.aryan007h.musicoonline.core.ListeningStatsReply
import io.github.aryan007h.musicoonline.core.MusicoCoreCodec
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.PlaylistImportResult
import io.github.aryan007h.musicoonline.core.PlaylistImportStatus
import io.github.aryan007h.musicoonline.core.ResolvedTrack
import io.github.aryan007h.musicoonline.core.Shelf
import io.github.aryan007h.musicoonline.core.ShelfItem
import io.github.aryan007h.musicoonline.core.SmartMix
import io.github.aryan007h.musicoonline.platform.DesktopPlatformPrefs
import io.github.aryan007h.musicoonline.platform.PlatformEqualizer
import io.github.aryan007h.musicoonline.platform.mapFiveBandsToTenDb
import io.github.aryan007h.musicoonline.ui.DesktopLayoutMetrics
import io.github.aryan007h.musicoonline.ui.LibrarySection
import io.github.aryan007h.musicoonline.ui.LyricBackdrop
import io.github.aryan007h.musicoonline.ui.ResolveFailureAction
import io.github.aryan007h.musicoonline.ui.ResolveFailurePolicy
import io.github.aryan007h.musicoonline.ui.StreamResolvePolicy
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSeekMath
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSliderMath
import io.github.aryan007h.musicoonline.ui.components.HorizontalScrollMath
import io.github.aryan007h.musicoonline.ui.components.HorizontalWheelAction
import io.github.aryan007h.musicoonline.ui.home.HomeFeaturedCurator
import io.github.aryan007h.musicoonline.ui.home.HomeLayoutMetrics
import io.github.aryan007h.musicoonline.ui.home.HomeMoodFilter
import io.github.aryan007h.musicoonline.ui.resolveLanguageGenreStyle
import io.github.aryan007h.musicoonline.ui.library.DownloadsPartition
import io.github.aryan007h.musicoonline.ui.library.LibraryRecommendationCurator
import io.github.aryan007h.musicoonline.ui.library.formatDownloadsSummary
import io.github.aryan007h.musicoonline.ui.library.formatSongCount
import io.github.aryan007h.musicoonline.ui.library.formatTrackCount
import io.github.aryan007h.musicoonline.ui.player.ArtworkAtmosphere
import io.github.aryan007h.musicoonline.ui.player.ArtworkPaletteCache
import io.github.aryan007h.musicoonline.ui.player.PlayerBackdropSpecs
import io.github.aryan007h.musicoonline.ui.player.formatTime
import io.github.aryan007h.musicoonline.ui.settings.EqCurveMath
import io.github.aryan007h.musicoonline.ui.settings.snapCacheCapMb
import androidx.compose.ui.graphics.luminance
import io.github.aryan007h.musicoonline.ui.theme.ExpressiveArchShape
import io.github.aryan007h.musicoonline.ui.theme.ExpressiveScallopShape
import io.github.aryan007h.musicoonline.ui.theme.ExpressiveSegmentedMath
import io.github.aryan007h.musicoonline.ui.theme.ExpressiveShapes
import io.github.aryan007h.musicoonline.ui.theme.Palette
import io.github.aryan007h.musicoonline.ui.theme.ThemeMode
import io.github.aryan007h.musicoonline.ui.theme.paletteLightScheme
import io.github.aryan007h.musicoonline.ui.theme.safeCornerDp
import io.github.aryan007h.musicoonline.ui.theme.safePositiveScale
import io.github.aryan007h.musicoonline.ui.theme.toDarkScheme
import io.github.aryan007h.musicoonline.ui.theme.toExtras
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

    @Test
    fun streamResolvePolicyEnablesProgressiveStreamingForInteractivePlaybackOnly() {
        assertTrue(StreamResolvePolicy.allowProgressiveStream(background = false))
        assertTrue(!StreamResolvePolicy.allowProgressiveStream(background = true))

        val json = Json { ignoreUnknownKeys = true }
        val streamReply = json.decodeFromString(
            ResolvedTrack.serializer(),
            """{"path":"http://127.0.0.1:45123/stream/abc","songId":"yt:xyz","mode":"stream","resolveMs":340}""",
        )
        assertEquals("http://127.0.0.1:45123/stream/abc", streamReply.path)
        assertEquals("stream", streamReply.mode)
        assertEquals(340L, streamReply.resolveMs)

        // Backwards compatible when older core payloads omit mode/resolveMs.
        val legacyReply = json.decodeFromString(
            ResolvedTrack.serializer(),
            """{"path":"/tmp/cached.m4a","songId":"yt:xyz"}""",
        )
        assertEquals("", legacyReply.mode)
        assertEquals(0L, legacyReply.resolveMs)
    }

    @Test
    fun expressiveSeekMathStopsWaveWhenPausedAndHoldsPendingSeekWithoutSnapback() {
        assertTrue(ExpressiveSeekMath.shouldAnimateWave(isPlaying = true, enabled = true, dragging = false))
        assertTrue(!ExpressiveSeekMath.shouldAnimateWave(isPlaying = false, enabled = true, dragging = false))
        assertTrue(!ExpressiveSeekMath.shouldAnimateWave(isPlaying = true, enabled = true, dragging = true))
        assertTrue(!ExpressiveSeekMath.shouldAnimateWave(isPlaying = true, enabled = false, dragging = false))

        assertEquals(0f, ExpressiveSeekMath.clampFraction(Float.NaN), 1e-6f)
        assertEquals(1f, ExpressiveSeekMath.clampFraction(1.4f), 1e-6f)

        // Pending seek fraction holds the released position until player progress settles.
        assertEquals(
            0.72f,
            ExpressiveSeekMath.resolveDisplayedFraction(
                progress = 0.25f,
                dragging = false,
                dragFraction = 0.72f,
                pendingSeekFraction = 0.72f,
            ),
            1e-5f,
        )
        assertTrue(ExpressiveSeekMath.isPendingSeekSettled(progress = 0.71f, pendingSeekFraction = 0.72f))
        assertTrue(!ExpressiveSeekMath.isPendingSeekSettled(progress = 0.25f, pendingSeekFraction = 0.72f))
    }

    @Test
    fun responsiveMetricsProtectCompactPhonesAnd840dpDesktopWindows() {
        // 360-412dp phones use the compact HeroCard layout so text/CTA never clip.
        assertTrue(HomeLayoutMetrics.isCompactHero(360f))
        assertTrue(HomeLayoutMetrics.isCompactHero(412f))
        assertTrue(!HomeLayoutMetrics.isCompactHero(600f))

        // Right above 840dp, rail narrows to 224dp and volume slider hides below 740dp bar width.
        assertEquals(224f, DesktopLayoutMetrics.railWidthDp(840f), 1e-4f)
        assertEquals(256f, DesktopLayoutMetrics.railWidthDp(1280f), 1e-4f)
        assertTrue(!DesktopLayoutMetrics.showInlineVolumeSlider(580f))
        assertTrue(DesktopLayoutMetrics.showInlineVolumeSlider(800f))
    }

    @Test
    fun allPalettesDefineDistinctSecondaryAndTertiaryContainers() {
        Palette.ALL.forEach { palette ->
            val dark = palette.toDarkScheme()
            val light = paletteLightScheme(palette)
            assertNotEquals(dark.primaryContainer, dark.secondaryContainer, "${palette.id} dark secondaryContainer")
            assertNotEquals(dark.primaryContainer, dark.tertiaryContainer, "${palette.id} dark tertiaryContainer")
            assertNotEquals(Color(0xFF633B48), dark.tertiaryContainer, "${palette.id} must not use M3 default maroon")
            assertNotEquals(light.primaryContainer, light.tertiaryContainer, "${palette.id} light tertiaryContainer")
        }
    }

    @Test
    fun homeMoodFilterAndBackdropSpecsCoverAllOptions() {
        val chillShelf = Shelf(title = "Late Night Lo-Fi Chill", items = listOf(ShelfItem(title = "Rainy Beats")))
        val gymShelf = Shelf(title = "Workout Power Hits", items = listOf(ShelfItem(title = "Sprint")))
        val all = listOf(chillShelf, gymShelf)

        assertEquals(2, HomeMoodFilter.filterShelves(all, "All").size)
        assertEquals(listOf(chillShelf), HomeMoodFilter.filterShelves(all, "Chill"))
        assertEquals(listOf(gymShelf), HomeMoodFilter.filterShelves(all, "Workout"))
        assertEquals(emptyList(), HomeMoodFilter.filterShelves(all, "Focus"))

        assertEquals(5, PlayerBackdropSpecs.ALL_IDS.size)
        assertTrue(PlayerBackdropSpecs.animatesMotion("mesh"))
        assertTrue(PlayerBackdropSpecs.animatesMotion("artwork"))
        assertTrue(PlayerBackdropSpecs.animatesMotion("aurora"))
        assertTrue(PlayerBackdropSpecs.animatesMotion("rain"))
        assertTrue(!PlayerBackdropSpecs.animatesMotion("black"))
        assertEquals("mesh", PlayerBackdropSpecs.normalize("unknown"))

        assertEquals(7, LibrarySection.entries.size)
        assertTrue(LibrarySection.ARTISTS in LibrarySection.entries)
    }

    @Test
    fun themeModeLookupAndResolutionCoverSystemLightAndDark() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromId("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromId("dark"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId("unknown"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId(null))

        assertTrue(ThemeMode.SYSTEM.resolveDark(systemInDarkTheme = true))
        assertTrue(!ThemeMode.SYSTEM.resolveDark(systemInDarkTheme = false))
        assertTrue(!ThemeMode.LIGHT.resolveDark(systemInDarkTheme = true))
        assertTrue(!ThemeMode.LIGHT.resolveDark(systemInDarkTheme = false))
        assertTrue(ThemeMode.DARK.resolveDark(systemInDarkTheme = true))
        assertTrue(ThemeMode.DARK.resolveDark(systemInDarkTheme = false))
    }

    @Test
    fun allPalettesProvideDistinctBespokeLightAndDarkSchemesAndExtras() {
        val lightBackgrounds = mutableSetOf<Color>()
        Palette.ALL.forEach { palette ->
            val light = paletteLightScheme(palette)
            val dark = palette.toDarkScheme()

            assertTrue(light.background.luminance() > 0.80f, "${palette.id} light background must be bright")
            assertTrue(dark.background.luminance() < 0.15f, "${palette.id} dark background must be dark")
            assertTrue(light.onBackground.luminance() < 0.15f, "${palette.id} light ink must be high contrast")
            assertTrue(dark.onBackground.luminance() > 0.70f, "${palette.id} dark ink must be high contrast")
            assertNotEquals(light.surfaceContainerLowest, light.surfaceContainerHigh, "${palette.id} tonal ladder")
            lightBackgrounds += light.background

            val darkExtras = palette.toExtras(isDark = true)
            val lightExtras = palette.toExtras(isDark = false)
            assertTrue(darkExtras.isDark)
            assertTrue(!lightExtras.isDark)
            assertNotEquals(darkExtras.bg0, lightExtras.bg0)
        }
        assertEquals(Palette.ALL.size, lightBackgrounds.size, "Each palette should have a distinct light canvas tint")
    }

    @Test
    fun expressiveSegmentedMathComputesVerticalConnectedAndScallopGeometry() {
        // Single item gets full outer corners on all 4 sides.
        val single = ExpressiveSegmentedMath.verticalListCorners(index = 0, count = 1)
        assertEquals(24f, single.topStartDp, 1e-4f)
        assertEquals(24f, single.bottomEndDp, 1e-4f)

        // Multi-item vertical segmented list: outer top, inner middle, outer bottom.
        val top = ExpressiveSegmentedMath.verticalListCorners(index = 0, count = 3)
        val mid = ExpressiveSegmentedMath.verticalListCorners(index = 1, count = 3)
        val bot = ExpressiveSegmentedMath.verticalListCorners(index = 2, count = 3)
        assertEquals(24f, top.topStartDp, 1e-4f)
        assertEquals(6f, top.bottomStartDp, 1e-4f)
        assertEquals(6f, mid.topStartDp, 1e-4f)
        assertEquals(6f, mid.bottomEndDp, 1e-4f)
        assertEquals(6f, bot.topStartDp, 1e-4f)
        assertEquals(24f, bot.bottomEndDp, 1e-4f)

        // Connected button group: selected morphs to full pill, unselected stays connected.
        val selectedMid = ExpressiveSegmentedMath.connectedButtonCorners(index = 1, count = 3, selected = true)
        assertEquals(24f, selectedMid.topStartDp, 1e-4f)
        assertEquals(24f, selectedMid.bottomEndDp, 1e-4f)

        val leading = ExpressiveSegmentedMath.connectedButtonCorners(index = 0, count = 3, selected = false)
        assertEquals(22f, leading.topStartDp, 1e-4f)
        assertEquals(8f, leading.topEndDp, 1e-4f)

        val trailing = ExpressiveSegmentedMath.connectedButtonCorners(index = 2, count = 3, selected = false)
        assertEquals(8f, trailing.topStartDp, 1e-4f)
        assertEquals(22f, trailing.topEndDp, 1e-4f)

        // Scallop radius factor remains strictly bounded in [1 - amp, 1].
        val amp = 0.10f
        for (deg in 0..360 step 15) {
            val rad = deg * kotlin.math.PI / 180.0
            val factor = ExpressiveSegmentedMath.scallopRadiusFactor(rad, lobes = 8, amplitude = amp)
            assertTrue(factor in (1f - amp - 1e-4f)..(1f + 1e-4f), "Factor $factor out of bounds at $deg deg")
        }
    }

    @Test
    fun expressiveSliderMathConvertsRangesAndClampsNaN() {
        val range = 512f..5120f
        assertEquals(0f, ExpressiveSliderMath.valueToFraction(512f, range), 1e-5f)
        assertEquals(1f, ExpressiveSliderMath.valueToFraction(5120f, range), 1e-5f)
        assertEquals(0f, ExpressiveSliderMath.valueToFraction(Float.NaN, range), 1e-5f)
        assertEquals(512f, ExpressiveSliderMath.clampToRange(Float.NaN, range), 1e-5f)
        assertEquals(2816f, ExpressiveSliderMath.fractionToValue(0.5f, range), 1e-3f)
        assertEquals(5120f, ExpressiveSliderMath.fractionToValue(1.5f, range), 1e-3f)
    }

    @Test
    fun eqCurveMathFormatsDbMapsBandsAndSnapsSteps() {
        val range = -1200..1200
        assertEquals(0.5f, EqCurveMath.levelToNormalized(0, range), 1e-5f)
        assertEquals(1.0f, EqCurveMath.levelToNormalized(1200, range), 1e-5f)
        assertEquals(0.0f, EqCurveMath.levelToNormalized(-1200, range), 1e-5f)
        assertEquals(0.5f, EqCurveMath.levelToBipolar(600, range), 1e-5f)
        assertEquals(-0.5f, EqCurveMath.levelToBipolar(-600, range), 1e-5f)

        assertEquals(0, EqCurveMath.fractionToMillibels(0.5f, range))
        assertEquals(1200, EqCurveMath.fractionToMillibels(1.2f, range))
        assertEquals(-1200, EqCurveMath.fractionToMillibels(-0.3f, range))
        assertEquals(0, EqCurveMath.fractionToMillibels(Float.NaN, range))

        assertEquals("0", EqCurveMath.formatDb(0))
        assertEquals("+6", EqCurveMath.formatDb(600))
        assertEquals("-2.5", EqCurveMath.formatDb(-250))
        assertEquals(8, EqCurveMath.PRESETS.size)
    }

    @Test
    fun homeFeaturedCuratorPersonalizesAndRefreshesAcrossLaunches() {
        assertEquals(5_000L, HomeFeaturedCurator.HERO_AUTO_SLIDE_MS)
        assertEquals(3, HomeLayoutMetrics.quickPickColumns(900f))
        assertEquals(2, HomeLayoutMetrics.quickPickColumns(420f))
        assertTrue(HomeLayoutMetrics.showHeroUpNextPreview(840f))
        assertTrue(!HomeLayoutMetrics.showHeroUpNextPreview(500f))

        val genericShelf = Shelf(
            title = "Global Charts",
            items = (1..10).map { i ->
                ShelfItem(title = "Chart Song $i", subtitle = "Generic Artist $i", videoId = "vid_g_$i", thumbnail = "https://img/$i")
            },
        )
        val favShelf = Shelf(
            title = "Made for You",
            items = listOf(
                ShelfItem(title = "Blinding Lights", subtitle = "The Weeknd", videoId = "vid_wknd", thumbnail = "https://img/w"),
                ShelfItem(title = "Cruel Summer", subtitle = "Taylor Swift", videoId = "vid_ts", thumbnail = "https://img/t"),
            ),
        )
        val liked = listOf(OnlineTrack(videoId = "l1", title = "Save Your Tears", artist = "The Weeknd"))
        val saved = listOf(CatalogEntry(id = "art1", title = "Taylor Swift", kind = "artist"))

        val launchA = HomeFeaturedCurator.selectFeaturedItems(
            shelves = listOf(genericShelf, favShelf),
            likedTracks = liked,
            savedArtists = saved,
            topArtistNames = listOf("The Weeknd"),
            sessionSeed = 1001L,
            maxItems = 6,
        )
        val launchB = HomeFeaturedCurator.selectFeaturedItems(
            shelves = listOf(genericShelf, favShelf),
            likedTracks = liked,
            savedArtists = saved,
            topArtistNames = listOf("The Weeknd"),
            sessionSeed = 999_888_777L,
            maxItems = 6,
        )

        assertEquals(6, launchA.size)
        assertEquals(6, launchB.size)
        // Personalized favorites must be surfaced in the featured lineup.
        assertTrue(launchA.any { it.videoId == "vid_wknd" })
        assertTrue(launchA.any { it.videoId == "vid_ts" })
        // Distinct app launch seeds refresh the spotlight ordering.
        assertNotEquals(launchA.map { it.videoId }, launchB.map { it.videoId })
        assertEquals("FOR YOUR TASTE", HomeFeaturedCurator.badgeLabel(favShelf.items.first(), setOf("the weeknd")))
    }

    @Test
    fun homeShelfOrderingRefreshesEntireFeedWhilePreservingRankedCharts() {
        val chartShelf = Shelf(
            title = "Top Charts Global",
            items = listOf(
                ShelfItem(title = "Rank 1", videoId = "c1"),
                ShelfItem(title = "Rank 2", videoId = "c2"),
                ShelfItem(title = "Rank 3", videoId = "c3"),
                ShelfItem(title = "Rank 4", videoId = "c4"),
            ),
        )
        val picksShelf = Shelf(
            title = "Quick picks for you",
            items = (1..8).map { i -> ShelfItem(title = "Pick $i", videoId = "p$i") },
        )
        val moodShelf = Shelf(
            title = "Fresh Indie",
            items = (1..8).map { i -> ShelfItem(title = "Indie $i", videoId = "i$i") },
        )
        val chillShelf = Shelf(
            title = "Late Night Chill",
            items = (1..8).map { i -> ShelfItem(title = "Chill $i", videoId = "ch$i") },
        )
        val input = listOf(chartShelf, picksShelf, moodShelf, chillShelf)

        val orderedA = HomeFeaturedCurator.orderShelvesForSession(input, sessionSeed = 111L)
        val orderedB = HomeFeaturedCurator.orderShelvesForSession(input, sessionSeed = 9_999_999L)

        // Ranked charts keep exact 1..4 order across refreshes.
        val chartA = orderedA.first { it.title == "Top Charts Global" }
        val chartB = orderedB.first { it.title == "Top Charts Global" }
        assertEquals(listOf("c1", "c2", "c3", "c4"), chartA.items.map { it.videoId })
        assertEquals(listOf("c1", "c2", "c3", "c4"), chartB.items.map { it.videoId })

        // Personalized shelves stay grouped ahead of general shelves.
        assertEquals(setOf("Quick picks for you", "Fresh Indie"), orderedA.take(2).map { it.title }.toSet())
        assertEquals(setOf("Top Charts Global", "Late Night Chill"), orderedA.drop(2).map { it.title }.toSet())

        // Across distinct refresh seeds, non-ranked shelf item ordering or general shelf order refreshes.
        val sigA = orderedA.map { it.title to it.items.map { item -> item.videoId } }
        val sigB = orderedB.map { it.title to it.items.map { item -> item.videoId } }
        assertNotEquals(sigA, sigB)
    }

    @Test
    fun downloadsPartitionSplitsUserDownloadedFromAutoCachedTracks() {
        val dl1 = DownloadedTrack(
            track = OnlineTrack(videoId = "v1", title = "Saved Offline", artist = "Artist A"),
            sizeBytes = 4_000_000L,
        )
        val dl2 = DownloadedTrack(
            track = OnlineTrack(videoId = "v2", title = "Stream Cached", artist = "Artist B"),
            sizeBytes = 2_500_000L,
        )
        val dl3 = DownloadedTrack(
            track = OnlineTrack(videoId = "v3", title = "Also Downloaded", artist = "Artist C"),
            sizeBytes = 3_500_000L,
        )

        val res = DownloadsPartition.partition(
            downloads = listOf(dl1, dl2, dl3),
            userDownloadedIds = setOf("v1", "v3"),
        )
        assertEquals(listOf("v1", "v3"), res.userDownloaded.map { it.track.videoId })
        assertEquals(listOf("v2"), res.cachedOnly.map { it.track.videoId })
        assertEquals(7_500_000L, res.userDownloadedBytes)
        assertEquals(2_500_000L, res.cachedOnlyBytes)
    }

    @Test
    fun libraryRecommendationCuratorSurfacesRecommendedArtistsAndAlbumsExcludingSaved() {
        val savedArtists = listOf(CatalogEntry(id = "UC_SAVED", kind = "artist", title = "The Weeknd", browseId = "UC_SAVED"))
        val savedAlbums = listOf(CatalogEntry(id = "MPRE_SAVED", kind = "album", title = "After Hours", browseId = "MPRE_SAVED"))

        val shelves = listOf(
            Shelf(
                title = "Recommended Artists",
                items = listOf(
                    ShelfItem(title = "The Weeknd", kind = ItemKind.ARTIST, browseId = "UC_SAVED"),
                    ShelfItem(title = "Daft Punk", kind = ItemKind.ARTIST, browseId = "UC_DAFT", thumbnail = "https://img/daft"),
                ),
            ),
            Shelf(
                title = "New Albums & Releases",
                items = listOf(
                    ShelfItem(title = "After Hours", kind = ItemKind.ALBUM, browseId = "MPRE_SAVED"),
                    ShelfItem(title = "Random Access Memories", kind = ItemKind.ALBUM, subtitle = "Daft Punk", browseId = "MPRE_RAM"),
                ),
            ),
        )
        val liked = listOf(
            OnlineTrack(videoId = "t1", title = "Starboy", artist = "The Weeknd feat. Daft Punk", album = "Starboy"),
            OnlineTrack(videoId = "t2", title = "Midnight City", artist = "M83", album = "Hurry Up, We're Dreaming"),
        )
        val mixes = listOf(
            SmartMix(
                id = "mix1",
                title = "Daily Mix",
                tracks = listOf(OnlineTrack(videoId = "t3", title = "Breathe", artist = "Tame Impala", album = "Currents")),
            ),
        )

        val recArtists = LibraryRecommendationCurator.recommendArtists(
            savedArtists = savedArtists,
            homeShelves = shelves,
            topArtistNames = listOf("The Weeknd", "Frank Ocean"),
            likedTracks = liked,
            recentlyPlayed = emptyList(),
            smartMixes = mixes,
        )
        assertTrue(recArtists.none { it.title.equals("The Weeknd", ignoreCase = true) })
        assertEquals("Daft Punk", recArtists.first().title)
        assertEquals("UC_DAFT", recArtists.first().browseId)
        assertTrue(recArtists.any { it.title == "Frank Ocean" })
        assertTrue(recArtists.any { it.title == "M83" })
        assertTrue(recArtists.any { it.title == "Tame Impala" })

        val recAlbums = LibraryRecommendationCurator.recommendAlbums(
            savedAlbums = savedAlbums,
            homeShelves = shelves,
            likedTracks = liked,
            recentlyPlayed = emptyList(),
            smartMixes = mixes,
        )
        assertTrue(recAlbums.none { it.title.equals("After Hours", ignoreCase = true) })
        assertEquals("Random Access Memories", recAlbums.first().title)
        assertEquals("MPRE_RAM", recAlbums.first().browseId)
        assertTrue(recAlbums.any { it.title == "Starboy" })
        assertTrue(recAlbums.any { it.title == "Currents" })
    }

    @Test
    fun expressiveShapesAndMotionGuardsHandleDegenerateGeometrySafely() {
        val density = Density(2f)
        val degenerateSizes = listOf(
            Size.Zero,
            Size(-10f, 54f),
            Size(54f, 0f),
            Size(Float.NaN, 54f),
            Size(Float.POSITIVE_INFINITY, 54f),
        )
        for (sz in degenerateSizes) {
            val scallop = ExpressiveShapes.Cookie10.createOutline(sz, LayoutDirection.Ltr, density)
            assertEquals(Outline.Rectangle(Rect.Zero), scallop, "Scallop must return Rect.Zero outline for $sz")
            val arch = ExpressiveShapes.Arch.createOutline(sz, LayoutDirection.Ltr, density)
            assertEquals(Outline.Rectangle(Rect.Zero), arch, "Arch must return Rect.Zero outline for $sz")
        }

        val validArch = ExpressiveShapes.Arch.createOutline(Size(120f, 160f), LayoutDirection.Ltr, density)
        assertTrue(validArch is Outline.Rounded, "Valid Arch size should produce Outline.Rounded")
        assertEquals(ExpressiveScallopShape(lobes = 10, amplitude = 0.07f), ExpressiveShapes.Cookie10)
        assertEquals(ExpressiveArchShape(18.dp), ExpressiveShapes.Arch)

        assertEquals(0.dp, safeCornerDp((-4f).dp))
        assertEquals(0.dp, safeCornerDp(Float.NaN.dp))
        assertEquals(0.dp, safeCornerDp(Float.POSITIVE_INFINITY.dp))
        assertEquals(12.dp, safeCornerDp(12.dp))

        assertEquals(0.01f, safePositiveScale(-0.5f), 1e-5f)
        assertEquals(1f, safePositiveScale(Float.NaN), 1e-5f)
        assertEquals(1f, safePositiveScale(Float.POSITIVE_INFINITY), 1e-5f)
        assertEquals(0.90f, safePositiveScale(0.90f), 1e-5f)
    }

    @Test
    fun listeningStatsReplyDecodesEmptyAndPopulatedPayloads() {
        val codec = Json { ignoreUnknownKeys = true }
        val empty = codec.decodeFromString(ListeningStatsReply.serializer(), "{}")
        assertEquals(0, empty.totalPlays)
        assertEquals(0L, empty.totalListenedSecs)
        assertTrue(empty.topArtists.isEmpty())

        val full = codec.decodeFromString(
            ListeningStatsReply.serializer(),
            """
            {
              "totalPlays": 42,
              "totalListenedSecs": 7200,
              "streakDays": 5,
              "topArtists": [
                {"artist": "Arijit Singh", "affinity": 0.91, "plays": 19}
              ]
            }
            """.trimIndent(),
        )
        assertEquals(42, full.totalPlays)
        assertEquals(7200L, full.totalListenedSecs)
        assertEquals(5, full.streakDays)
        assertEquals("Arijit Singh", full.topArtists.single().artist)
    }
}
