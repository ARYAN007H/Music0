package io.github.aryan007h.musicoonline.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed as lazyListItemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.CatalogEntry
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.Shelf
import io.github.aryan007h.musicoonline.core.ShelfItem
import io.github.aryan007h.musicoonline.core.SmartMix
import io.github.aryan007h.musicoonline.core.UserPlaylist
import io.github.aryan007h.musicoonline.platform.PlatformHorizontalGridScrollbar
import io.github.aryan007h.musicoonline.platform.PlatformHorizontalScrollbar
import io.github.aryan007h.musicoonline.platform.currentLocalHour
import io.github.aryan007h.musicoonline.platform.isDesktopPlatform
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.components.DesktopScrollableChipRow
import io.github.aryan007h.musicoonline.ui.components.EqualizerBars
import io.github.aryan007h.musicoonline.ui.components.ErrorState
import io.github.aryan007h.musicoonline.ui.components.ExpressiveFilterChip
import io.github.aryan007h.musicoonline.ui.components.HorizontalScrollMath
import io.github.aryan007h.musicoonline.ui.components.SectionHeader
import io.github.aryan007h.musicoonline.ui.components.ShelfCarousel
import io.github.aryan007h.musicoonline.ui.components.ShelfScrollButtons
import io.github.aryan007h.musicoonline.ui.components.desktopHorizontalPagerScroll
import io.github.aryan007h.musicoonline.ui.components.desktopHorizontalScroll
import io.github.aryan007h.musicoonline.ui.components.musicoTopInset
import io.github.aryan007h.musicoonline.ui.library.LibraryCollectionRef
import io.github.aryan007h.musicoonline.ui.library.PlaylistMosaicArtwork
import io.github.aryan007h.musicoonline.ui.library.formatSongCount
import io.github.aryan007h.musicoonline.ui.library.formatTrackCount
import io.github.aryan007h.musicoonline.ui.player.ArtworkAtmosphere
import io.github.aryan007h.musicoonline.ui.theme.ExpressiveShapes
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.hapticThud
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import io.github.aryan007h.musicoonline.ui.theme.pressable
import io.github.aryan007h.musicoonline.ui.theme.shimmer
import io.github.aryan007h.musicoonline.ui.theme.staggerIn
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Editorial Material 3 Expressive Home Feed across Android & Desktop:
 * - Dynamic time-of-day greeting with tactile "Refresh spotlight" M3E pill
 * - Responsive Quick-Pick Grid (2 columns on phone, 3 columns on widescreen desktop)
 * - Auto-sliding 5-second Featured Spotlight Carousel with 120Hz draw-phase progress pill,
 *   hover/drag pause, widescreen "Up Next in Spotlight" preview card, and per-launch
 *   preference-scored curation so every app open surfaces fresh tailored banners
 * - Favorite Artists scalloped frames, Smart Mixes, Signature 2-row Grids, Cover Carousels
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    displayName: String,
    atmosphere: ArtworkAtmosphere,
    shelves: List<Shelf>,
    smartMixes: List<SmartMix> = emptyList(),
    playlists: List<UserPlaylist> = emptyList(),
    recentlyPlayed: List<OnlineTrack> = emptyList(),
    savedArtists: List<CatalogEntry> = emptyList(),
    likedTracks: List<OnlineTrack> = emptyList(),
    topArtistNames: List<String> = emptyList(),
    savedLanguages: List<String> = emptyList(),
    homeSessionSeed: Long = 0L,
    likedCount: Int = 0,
    downloadsCount: Int = 0,
    loading: Boolean,
    error: String?,
    styleItems: List<ShelfItem>,
    moreLoading: Boolean,
    personalizing: Boolean,
    personalizationError: String?,
    onOpenItem: (ShelfItem, List<ShelfItem>) -> Unit,
    onOpenCollection: (LibraryCollectionRef) -> Unit = {},
    onOpenTrack: (OnlineTrack, List<OnlineTrack>) -> Unit = { _, _ -> },
    onOpenCatalogEntry: (CatalogEntry) -> Unit = {},
    onStartMood: (String) -> Unit = {},
    onRefreshSpotlight: () -> Unit = {},
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onRetryPersonalization: (() -> Unit)? = null,
    onDismissPersonalizationError: (() -> Unit)? = null,
    currentTrackId: String?,
    isPlaying: Boolean = false,
) {
    Box(Modifier.fillMaxSize().musicoTopInset()) {
        when {
            error != null && shelves.isEmpty() && smartMixes.isEmpty() -> ErrorState(message = error, onRetry = onRetry)
            loading && shelves.isEmpty() && smartMixes.isEmpty() -> HomeSkeleton()
            else -> Column {
                PersonalizationBanner(
                    personalizing = personalizing,
                    error = personalizationError,
                    onRetry = onRetryPersonalization,
                    onDismiss = onDismissPersonalizationError,
                )
                HomeFeed(
                    displayName = displayName,
                    shelves = shelves,
                    smartMixes = smartMixes,
                    playlists = playlists,
                    recentlyPlayed = recentlyPlayed,
                    savedArtists = savedArtists,
                    likedTracks = likedTracks,
                    topArtistNames = topArtistNames,
                    savedLanguages = savedLanguages,
                    homeSessionSeed = homeSessionSeed,
                    likedCount = likedCount,
                    downloadsCount = downloadsCount,
                    styleItems = styleItems,
                    moreLoading = moreLoading,
                    onOpenItem = onOpenItem,
                    onOpenCollection = onOpenCollection,
                    onOpenTrack = onOpenTrack,
                    onOpenCatalogEntry = onOpenCatalogEntry,
                    onStartMood = onStartMood,
                    onRefreshSpotlight = onRefreshSpotlight,
                    onLoadMore = onLoadMore,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                )
            }
        }
    }
}

@Composable
private fun PersonalizationBanner(
    personalizing: Boolean,
    error: String?,
    onRetry: (() -> Unit)?,
    onDismiss: (() -> Unit)?,
) {
    when {
        personalizing -> Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Making your feed yours…",
                style = Musico.type.caption,
                color = Musico.extras.inkDim,
            )
        }
        error != null -> Surface(
            shape = MusicoShapes.tile,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    error,
                    style = Musico.type.caption,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onRetry != null) {
                    Text(
                        "Retry",
                        style = Musico.type.caption,
                        fontWeight = FontWeight.Bold,
                        color = Musico.extras.mint,
                        modifier = Modifier.pressable(onClick = onRetry, pressedScale = 0.92f),
                    )
                }
                if (onDismiss != null) {
                    Text(
                        "Dismiss",
                        style = Musico.type.caption,
                        color = Musico.extras.inkDim,
                        modifier = Modifier.pressable(onClick = onDismiss, pressedScale = 0.92f),
                    )
                }
            }
        }
    }
}

private sealed interface QuickPickEntry {
    val key: String
    val title: String
    val subtitle: String

    data class Liked(val count: Int) : QuickPickEntry {
        override val key = "qp-liked"
        override val title = "Liked Songs"
        override val subtitle = formatSongCount(count)
    }

    data class Downloads(val count: Int) : QuickPickEntry {
        override val key = "qp-dl"
        override val title = "Downloads"
        override val subtitle = "$count offline"
    }

    data class PlaylistPick(val playlist: UserPlaylist) : QuickPickEntry {
        override val key = "qp-pl-${playlist.id}"
        override val title = playlist.name
        override val subtitle = formatSongCount(playlist.tracks.size)
    }

    data class MixPick(val mix: SmartMix) : QuickPickEntry {
        override val key = "qp-mix-${mix.id}"
        override val title = mix.title
        override val subtitle = "Smart Mix"
    }

    data class TrackPick(val track: OnlineTrack) : QuickPickEntry {
        override val key = "qp-trk-${track.videoId}"
        override val title = track.title.ifEmpty { "Untitled" }
        override val subtitle = track.artist.ifEmpty { "Recently played" }
    }
}

private val HOME_MOOD_CHIPS = listOf("All", "Made for You", "Chill", "Energize", "Focus", "Workout")

/** Pure responsive breakpoints & layout metrics for HomeScreen unit tests. */
object HomeLayoutMetrics {
    fun isCompactHero(cardWidthDp: Float): Boolean = cardWidthDp < 440f
    fun showHeroUpNextPreview(cardWidthDp: Float): Boolean = cardWidthDp >= 760f
    fun quickPickColumns(widthDp: Float): Int = if (widthDp >= 760f) 3 else 2
}

/**
 * Pure preference-aware featured banner curator:
 * - Scores items across shelves using user's top artists, saved artists, liked songs,
 *   recently played tracks, and preferred languages.
 * - Deterministically refreshes & rotates the banner sequence using `sessionSeed` so
 *   every app launch surfaces a fresh, personalized spotlight lineup.
 */
object HomeFeaturedCurator {
    const val HERO_AUTO_SLIDE_MS: Long = 5_000L

    private val PERSONALIZED_SHELF_HINTS = listOf(
        "made for", "because you", "for you", "discover", "pick", "fresh", "mix", "recommend", "listening",
    )

    fun selectFeaturedItems(
        shelves: List<Shelf>,
        likedTracks: List<OnlineTrack> = emptyList(),
        recentlyPlayed: List<OnlineTrack> = emptyList(),
        savedArtists: List<CatalogEntry> = emptyList(),
        topArtistNames: List<String> = emptyList(),
        savedLanguages: List<String> = emptyList(),
        sessionSeed: Long = 0L,
        maxItems: Int = 8,
    ): List<ShelfItem> {
        if (shelves.isEmpty() || maxItems <= 0) return emptyList()

        val topArtists = topArtistNames.mapNotNull { normalizeToken(it) }.toSet()
        val favArtists = savedArtists.mapNotNull { normalizeToken(it.title) }.toSet()
        val likedArtists = likedTracks.mapNotNull { normalizeToken(it.artist) }.toSet()
        val recentArtists = recentlyPlayed.take(12).mapNotNull { normalizeToken(it.artist) }.toSet()
        val languages = savedLanguages.mapNotNull { normalizeToken(it) }.toSet()

        val seenKeys = LinkedHashSet<String>()
        data class Scored(val item: ShelfItem, val score: Int, val preferenceScore: Int, val orderIndex: Int)
        val candidates = ArrayList<Scored>()

        shelves.forEachIndexed { shelfIdx, shelf ->
            val shelfLower = shelf.title.lowercase()
            val isPersonalShelf = PERSONALIZED_SHELF_HINTS.any { shelfLower.contains(it) }
            shelf.items.forEachIndexed { itemIdx, item ->
                val dedupeKey = item.videoId?.takeIf { it.isNotBlank() }
                    ?: item.browseId?.takeIf { it.isNotBlank() }
                    ?: item.title.lowercase().trim()
                if (dedupeKey.isBlank() || !seenKeys.add(dedupeKey)) return@forEachIndexed

                val haystack = "${item.title} ${item.subtitle} ${shelf.title}".lowercase()
                var baseScore = 0
                if (shelfIdx == 0) baseScore += 16
                if (isPersonalShelf) baseScore += 24
                if (item.videoId != null) baseScore += 10
                if (!item.thumbnail.isNullOrBlank()) baseScore += 8

                var preferenceScore = 0
                if (topArtists.any { haystack.contains(it) }) preferenceScore += 42
                if (favArtists.any { haystack.contains(it) }) preferenceScore += 36
                if (likedArtists.any { haystack.contains(it) }) preferenceScore += 26
                if (recentArtists.any { haystack.contains(it) }) preferenceScore += 18
                if (languages.any { haystack.contains(it) }) preferenceScore += 14

                // Deterministic per-session jitter so each app open refreshes banner order
                val jitter = ((mixHash(sessionSeed, dedupeKey) ushr 1) % 23L).toInt()
                val totalScore = baseScore + preferenceScore + jitter - (itemIdx.coerceAtMost(8))

                candidates += Scored(
                    item = item,
                    score = totalScore,
                    preferenceScore = preferenceScore,
                    orderIndex = candidates.size,
                )
            }
        }

        if (candidates.isEmpty()) return emptyList()

        val ranked = candidates.sortedWith(
            compareByDescending<Scored> { it.score }.thenBy { it.orderIndex },
        )

        // Keep top preference-matched favorites in the spotlight, while rotating discovery
        // slots from a wider candidate pool so every launch refreshes the banners.
        val personalized = ranked.filter { it.preferenceScore > 0 }.take(maxItems).map { it.item }
        val remainingSlots = (maxItems - personalized.size).coerceAtLeast(0)
        val discovery = if (remainingSlots > 0) {
            val discoveryPool = ranked
                .filter { it.preferenceScore == 0 }
                .take(maxItems * 2)
                .map { it.item }
            if (discoveryPool.size <= remainingSlots) {
                discoveryPool
            } else {
                val offset = ((mixHash(sessionSeed, "rotate-discovery") ushr 1) % discoveryPool.size.toLong()).toInt()
                buildList(remainingSlots) {
                    for (i in 0 until remainingSlots) {
                        add(discoveryPool[(offset + i) % discoveryPool.size])
                    }
                }
            }
        } else {
            emptyList()
        }

        val combined = (personalized + discovery).take(maxItems)
        if (combined.size <= 1) return combined

        val rotation = ((mixHash(sessionSeed, "rotate-spotlight") ushr 1) % combined.size.toLong()).toInt()
        return buildList(combined.size) {
            for (i in combined.indices) {
                add(combined[(rotation + i) % combined.size])
            }
        }
    }

    fun badgeLabel(item: ShelfItem, preferredTokens: Set<String>): String {
        val haystack = "${item.title} ${item.subtitle}".lowercase()
        return when {
            preferredTokens.isNotEmpty() && preferredTokens.any { haystack.contains(it) } -> "FOR YOUR TASTE"
            item.videoId != null -> "SPOTLIGHT PICK"
            else -> "FEATURED"
        }
    }

    private fun normalizeToken(raw: String): String? {
        val cleaned = raw.trim().lowercase()
        return cleaned.takeIf { it.length >= 3 }
    }

    private fun mixHash(seed: Long, key: String): Long {
        var h = seed xor -0x61c8864680b583ebL
        for (c in key) {
            h = (h * 31L) xor c.code.toLong()
        }
        h = h xor (h ushr 33)
        h *= -0xae502812aa7333L
        h = h xor (h ushr 33)
        return h
    }
}

object HomeMoodFilter {
    fun keywordsFor(mood: String): List<String> = when (mood) {
        "Made for You" -> listOf("made for", "because you", "mix", "discover", "repeat", "pick", "for you", "recommend")
        "Chill" -> listOf("chill", "lofi", "lo-fi", "calm", "ambient", "acoustic", "relax", "soft", "late night", "unplugged", "indie")
        "Energize" -> listOf("energize", "energy", "party", "dance", "upbeat", "pop", "hits", "trending", "electronic", "club")
        "Focus" -> listOf("focus", "study", "instrumental", "piano", "classical", "ambient", "deep work", "coding", "concentration")
        "Workout" -> listOf("workout", "gym", "run", "running", "hype", "hip-hop", "hip hop", "rap", "pump", "cardio", "power")
        else -> emptyList()
    }

    /** Returns shelves matching the selected mood; empty list when none match so UI renders an honest Mood Station card. */
    fun filterShelves(shelves: List<Shelf>, mood: String): List<Shelf> {
        val content = shelves.filter { it.items.isNotEmpty() }
        if (mood == "All") return content
        val keywords = keywordsFor(mood)
        if (keywords.isEmpty()) return content
        return content.filter { shelf ->
            val titleLower = shelf.title.lowercase()
            keywords.any { kw -> titleLower.contains(kw) } ||
                shelf.items.any { item ->
                    val t = item.title.lowercase()
                    val s = item.subtitle.lowercase()
                    keywords.any { kw -> t.contains(kw) || s.contains(kw) }
                }
        }
    }
}

@Composable
private fun HomeFeed(
    displayName: String,
    shelves: List<Shelf>,
    smartMixes: List<SmartMix>,
    playlists: List<UserPlaylist>,
    recentlyPlayed: List<OnlineTrack>,
    savedArtists: List<CatalogEntry>,
    likedTracks: List<OnlineTrack>,
    topArtistNames: List<String>,
    savedLanguages: List<String>,
    homeSessionSeed: Long,
    likedCount: Int,
    downloadsCount: Int,
    styleItems: List<ShelfItem>,
    moreLoading: Boolean,
    onOpenItem: (ShelfItem, List<ShelfItem>) -> Unit,
    onOpenCollection: (LibraryCollectionRef) -> Unit,
    onOpenTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onOpenCatalogEntry: (CatalogEntry) -> Unit,
    onStartMood: (String) -> Unit,
    onRefreshSpotlight: () -> Unit,
    onLoadMore: () -> Unit,
    currentTrackId: String?,
    isPlaying: Boolean,
) {
    var selectedMood by remember { mutableStateOf("All") }

    val matchedShelves = remember(shelves, selectedMood) {
        HomeMoodFilter.filterShelves(shelves, selectedMood)
    }
    val nonEmptyShelves = remember(shelves) { shelves.filter { it.items.isNotEmpty() } }
    val filteredShelves = remember(matchedShelves, nonEmptyShelves, selectedMood) {
        when {
            selectedMood == "All" -> nonEmptyShelves
            matchedShelves.isNotEmpty() -> matchedShelves
            selectedMood == "Made for You" -> nonEmptyShelves.take(3)
            else -> emptyList()
        }
    }

    val preferredTokens = remember(topArtistNames, savedArtists, likedTracks) {
        buildSet {
            topArtistNames.forEach { n -> n.trim().lowercase().takeIf { it.length >= 3 }?.let(::add) }
            savedArtists.forEach { a -> a.title.trim().lowercase().takeIf { it.length >= 3 }?.let(::add) }
            likedTracks.take(20).forEach { t -> t.artist.trim().lowercase().takeIf { it.length >= 3 }?.let(::add) }
        }
    }

    val curatedHeroItems = remember(
        filteredShelves,
        likedTracks,
        recentlyPlayed,
        savedArtists,
        topArtistNames,
        savedLanguages,
        homeSessionSeed,
    ) {
        HomeFeaturedCurator.selectFeaturedItems(
            shelves = filteredShelves,
            likedTracks = likedTracks,
            recentlyPlayed = recentlyPlayed,
            savedArtists = savedArtists,
            topArtistNames = topArtistNames,
            savedLanguages = savedLanguages,
            sessionSeed = homeSessionSeed,
            maxItems = 8,
        )
    }

    val (gridA, gridB, rest) = remember(filteredShelves) {
        val playable = filteredShelves.filter { shelf -> shelf.items.any { it.videoId != null } }
        val grids = playable.take(2)
        val used = grids.toSet()
        HomeShelfGroups(grids.getOrNull(0), grids.getOrNull(1), filteredShelves.filterNot { used.contains(it) })
    }

    val quickPicks = remember(likedCount, downloadsCount, playlists, smartMixes, recentlyPlayed) {
        buildList<QuickPickEntry> {
            if (likedCount > 0) add(QuickPickEntry.Liked(likedCount))
            if (downloadsCount > 0) add(QuickPickEntry.Downloads(downloadsCount))
            playlists.take(2).forEach { add(QuickPickEntry.PlaylistPick(it)) }
            smartMixes.take(2).forEach { add(QuickPickEntry.MixPick(it)) }
            recentlyPlayed.take(6).forEach { add(QuickPickEntry.TrackPick(it)) }
        }.take(6)
    }

    val greeting = remember {
        when (currentLocalHour()) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Late night listening"
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 164.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // ── Dynamic Greeting Header + Refresh Spotlight Pill ─────────────
        item(key = "home-greeting") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        greeting.uppercase(),
                        style = Musico.type.overline,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (displayName.equals("Listener", ignoreCase = true)) {
                            "Jump back in"
                        } else {
                            "Welcome back, $displayName"
                        },
                        style = Musico.type.display.copy(fontSize = 30.sp),
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (curatedHeroItems.size > 1) {
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .pointerHoverIcon(PointerIcon.Hand)
                            .pressable(
                                onClick = {
                                    hapticTick()
                                    onRefreshSpotlight()
                                },
                                pressedScale = 0.93f,
                            ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Refresh spotlight picks",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                "Refresh picks",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }

        // ── M3E Shape-Morphing Mood & Vibe Filter Chips ─────────────────
        item(key = "home-mood-chips") {
            DesktopScrollableChipRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                edgeFadeColor = MaterialTheme.colorScheme.background,
            ) {
                HOME_MOOD_CHIPS.forEach { mood ->
                    ExpressiveFilterChip(
                        label = mood,
                        selected = selectedMood == mood,
                        usePrimaryWhenSelected = false,
                        onClick = { selectedMood = mood },
                    )
                }
            }
        }

        // ── M3E Mood Station Launch Card when a specific vibe is active ──
        if (selectedMood != "All" && selectedMood != "Made for You") {
            item(key = "home-mood-station-$selectedMood") {
                Surface(
                    shape = MusicoShapes.hero,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .pressable(
                            onClick = {
                                hapticThud()
                                onStartMood(selectedMood)
                            },
                            pressedScale = 0.97f,
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            Modifier
                                .size(50.dp)
                                .clip(ExpressiveShapes.Cookie8)
                                .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Radio,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            Text(
                                "$selectedMood Station",
                                style = Musico.type.titleLarge,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                if (matchedShelves.isNotEmpty()) {
                                    "${matchedShelves.size} matching shelves · Tap to tune live radio"
                                } else {
                                    "Instant $selectedMood mix tailored for right now"
                                },
                                style = Musico.type.caption,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.82f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = "Play $selectedMood",
                                    tint = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    "Tune in",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Responsive M3E Quick-Pick Grid (2 cols phone, 3 cols desktop) ──
        if (quickPicks.size >= 2 && (selectedMood == "All" || selectedMood == "Made for You")) {
            item(key = "home-quick-picks") {
                QuickPickGrid(
                    entries = quickPicks,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    onClick = { entry ->
                        when (entry) {
                            is QuickPickEntry.Liked -> onOpenCollection(LibraryCollectionRef.LikedSongs)
                            is QuickPickEntry.Downloads -> onOpenCollection(LibraryCollectionRef.OfflineDownloads)
                            is QuickPickEntry.PlaylistPick -> onOpenCollection(LibraryCollectionRef.Playlist(entry.playlist.id))
                            is QuickPickEntry.MixPick -> onOpenCollection(LibraryCollectionRef.Mix(entry.mix.id))
                            is QuickPickEntry.TrackPick -> onOpenTrack(entry.track, recentlyPlayed)
                        }
                    },
                )
            }
        }

        // ── Auto-Sliding 5-Second Personalized M3E Editorial Spotlight ────
        if (curatedHeroItems.isNotEmpty()) {
            item(key = "hero-$homeSessionSeed") {
                HeroCarousel(
                    items = curatedHeroItems,
                    preferredTokens = preferredTokens,
                    onOpenItem = onOpenItem,
                )
            }
        }

        // ── Favorite Artists Scalloped Row ──────────────────────────────
        if (savedArtists.isNotEmpty()) {
            item(key = "home-favorite-artists") {
                val artistState = rememberLazyListState()
                val artistScope = rememberCoroutineScope()
                var artistViewportPx by remember { mutableFloatStateOf(0f) }
                Column {
                    SectionHeader(
                        title = "Favorite artists",
                        trailing = {
                            ShelfScrollButtons(
                                canScrollBackward = artistState.canScrollBackward,
                                canScrollForward = artistState.canScrollForward,
                                onScrollBackward = {
                                    val step = HorizontalScrollMath.computePageStepPx(artistViewportPx)
                                    artistScope.launch {
                                        artistState.animateScrollBy(-step, tween(320, easing = MusicoMotion.emphasized))
                                    }
                                },
                                onScrollForward = {
                                    val step = HorizontalScrollMath.computePageStepPx(artistViewportPx)
                                    artistScope.launch {
                                        artistState.animateScrollBy(step, tween(320, easing = MusicoMotion.emphasized))
                                    }
                                },
                            )
                        },
                    )
                    LazyRow(
                        state = artistState,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { artistViewportPx = it.width.toFloat() }
                            .desktopHorizontalScroll(artistState),
                    ) {
                        lazyListItemsIndexed(savedArtists.take(12), key = { _, it -> "hart-${it.id}" }) { idx, artist ->
                            Column(
                                modifier = Modifier
                                    .width(106.dp)
                                    .pressable(
                                        onClick = { hapticTick(); onOpenCatalogEntry(artist) },
                                        pressedScale = 0.94f,
                                    ),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Artwork(
                                    url = artist.thumbnail,
                                    contentDescription = artist.title,
                                    modifier = Modifier.size(104.dp),
                                    shape = ExpressiveShapes.artistFrame(idx),
                                    placeholderIcon = Icons.Filled.Person,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    artist.title,
                                    style = Musico.type.title,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (smartMixes.isNotEmpty()) {
            item(key = "home-smart-mixes") {
                val mixListState = rememberLazyListState()
                val mixScope = rememberCoroutineScope()
                var mixViewportPx by remember { mutableFloatStateOf(0f) }
                Column {
                    SectionHeader(
                        title = "Made for you",
                        trailing = {
                            ShelfScrollButtons(
                                canScrollBackward = mixListState.canScrollBackward,
                                canScrollForward = mixListState.canScrollForward,
                                onScrollBackward = {
                                    val step = HorizontalScrollMath.computePageStepPx(mixViewportPx)
                                    mixScope.launch {
                                        mixListState.animateScrollBy(-step, tween(320, easing = MusicoMotion.emphasized))
                                    }
                                },
                                onScrollForward = {
                                    val step = HorizontalScrollMath.computePageStepPx(mixViewportPx)
                                    mixScope.launch {
                                        mixListState.animateScrollBy(step, tween(320, easing = MusicoMotion.emphasized))
                                    }
                                },
                            )
                        },
                    )
                    LazyRow(
                        state = mixListState,
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { mixViewportPx = it.width.toFloat() }
                            .desktopHorizontalScroll(mixListState),
                    ) {
                        items(smartMixes, key = { "hmix-${it.id}" }) { mix ->
                            HomeSmartMixCard(
                                mix = mix,
                                onClick = {
                                    hapticTick()
                                    onOpenCollection(LibraryCollectionRef.Mix(mix.id))
                                },
                            )
                        }
                    }
                    PlatformHorizontalScrollbar(
                        state = mixListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 8.dp),
                    )
                }
            }
        }
        gridA?.let { shelf ->
            item(key = "gridA-${shelf.title}") {
                GridSection(
                    title = shelf.title.ifBlank { "Made for you" },
                    items = shelf.items,
                    numbered = false,
                    onOpenItem = onOpenItem,
                    currentTrackId = currentTrackId,
                )
            }
        }
        gridB?.let { shelf ->
            item(key = "gridB-${shelf.title}") {
                GridSection(
                    title = shelf.title.ifBlank { "Trending" },
                    items = shelf.items,
                    numbered = true,
                    onOpenItem = onOpenItem,
                    currentTrackId = currentTrackId,
                )
            }
        }
        items(count = rest.size, key = { "shelf-${rest[it].title}-${it}" }) { i ->
            val shelf = rest[i]
            ShelfCarousel(
                shelfTitle = shelf.title.ifBlank { "More for you" },
                items = shelf.items,
                onItemClick = onOpenItem,
            )
        }
        item(key = "styles") {
            if (styleItems.isNotEmpty()) {
                ShelfCarousel(
                    shelfTitle = "Browse styles",
                    items = styleItems,
                    onItemClick = onOpenItem,
                    cardWidth = 120.dp,
                )
            } else if (moreLoading) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 22.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        item(key = "end-trigger") {
            EndDetector(onNearEnd = onLoadMore)
        }
    }
}

@Composable
private fun QuickPickGrid(
    entries: List<QuickPickEntry>,
    currentTrackId: String?,
    isPlaying: Boolean,
    onClick: (QuickPickEntry) -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        val cols = HomeLayoutMetrics.quickPickColumns(maxWidth.value)
        val tileHeight = if (cols >= 3) 64.dp else 58.dp
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            entries.chunked(cols).forEachIndexed { rowIdx, rowEntries ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowEntries.forEachIndexed { colIdx, entry ->
                        QuickPickCard(
                            entry = entry,
                            tileHeight = tileHeight,
                            isCurrent = entry is QuickPickEntry.TrackPick && entry.track.videoId == currentTrackId,
                            isPlaying = isPlaying,
                            onClick = {
                                hapticThud()
                                onClick(entry)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .staggerIn(index = rowIdx * cols + colIdx),
                        )
                    }
                    repeat((cols - rowEntries.size).coerceAtLeast(0)) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPickCard(
    entry: QuickPickEntry,
    tileHeight: androidx.compose.ui.unit.Dp,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = Musico.extras.isDark
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = when {
            isCurrent -> MaterialTheme.colorScheme.secondaryContainer
            isDark -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainerLowest
        },
        border = if (!isDark && !isCurrent) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        } else {
            null
        },
        modifier = modifier
            .height(tileHeight)
            .pressable(onClick = onClick, pressedScale = 0.96f),
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (entry) {
                is QuickPickEntry.Liked -> Box(
                    Modifier
                        .size(tileHeight)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                is QuickPickEntry.Downloads -> Box(
                    Modifier
                        .size(tileHeight)
                        .background(MaterialTheme.colorScheme.tertiaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.DownloadDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
                is QuickPickEntry.PlaylistPick -> PlaylistMosaicArtwork(
                    thumbnails = entry.playlist.tracks.map { it.thumbnail },
                    modifier = Modifier.size(tileHeight),
                    cornerRadius = 0.dp,
                )
                is QuickPickEntry.MixPick -> PlaylistMosaicArtwork(
                    thumbnails = entry.mix.tracks.map { it.thumbnail },
                    modifier = Modifier.size(tileHeight),
                    cornerRadius = 0.dp,
                )
                is QuickPickEntry.TrackPick -> Box(Modifier.size(tileHeight)) {
                    Artwork(
                        url = entry.track.thumbnail,
                        contentDescription = entry.title,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 0.dp,
                    )
                    if (isCurrent) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            EqualizerBars(playing = isPlaying, color = Musico.extras.mint, modifier = Modifier.height(14.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    entry.title,
                    style = Musico.type.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    entry.subtitle,
                    style = Musico.type.tabularCaption,
                    fontSize = 11.5.sp,
                    color = if (isCurrent) {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EndDetector(onNearEnd: () -> Unit) {
    LaunchedEffect(Unit) { onNearEnd() }
    Spacer(Modifier.height(8.dp))
}

private data class HomeShelfGroups(
    val gridA: Shelf?,
    val gridB: Shelf?,
    val rest: List<Shelf>,
)

@Composable
private fun HomeSmartMixCard(mix: SmartMix, onClick: () -> Unit) {
    val isDark = Musico.extras.isDark
    Surface(
        shape = MusicoShapes.card,
        color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
        border = if (!isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) else null,
        modifier = Modifier
            .width(164.dp)
            .pressable(onClick = onClick, pressedScale = 0.95f),
    ) {
        Column {
            PlaylistMosaicArtwork(
                thumbnails = mix.tracks.map { it.thumbnail },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(148.dp),
                cornerRadius = 0.dp,
            )
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    mix.title,
                    style = Musico.type.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${formatTrackCount(mix.tracks.size)} · ${mix.subtitle}",
                    style = Musico.type.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ── Auto-Sliding 5-Second Personalized M3E Editorial Spotlight ────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCarousel(
    items: List<ShelfItem>,
    preferredTokens: Set<String>,
    onOpenItem: (ShelfItem, List<ShelfItem>) -> Unit,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()

    var pointerHovered by remember { mutableStateOf(false) }
    val userScrolling by remember { derivedStateOf { pagerState.isScrollInProgress } }
    val timerPaused = pointerHovered || userScrolling

    // Draw-phase 120 FPS auto-slide timer state (zero recompositions while counting!)
    val slideProgressState = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pagerState.currentPage, items.size) {
        slideProgressState.floatValue = 0f
    }

    LaunchedEffect(pagerState.currentPage, timerPaused, items.size) {
        if (items.size <= 1 || timerPaused) return@LaunchedEffect
        val totalNanos = HomeFeaturedCurator.HERO_AUTO_SLIDE_MS * 1_000_000L
        var elapsedNanos = (slideProgressState.floatValue.coerceIn(0f, 1f) * totalNanos).toLong()
        var lastNanos = 0L
        while (elapsedNanos < totalNanos) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    elapsedNanos += (now - lastNanos).coerceIn(0L, 100_000_000L)
                }
                lastNanos = now
                slideProgressState.floatValue = (elapsedNanos.toFloat() / totalNanos.toFloat()).coerceIn(0f, 1f)
            }
        }
        val nextPage = (pagerState.currentPage + 1) % items.size
        slideProgressState.floatValue = 0f
        pagerState.animateScrollToPage(
            page = nextPage,
            animationSpec = tween(durationMillis = 560, easing = MusicoMotion.emphasized),
        )
    }

    Column(
        modifier = Modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    when (event.type) {
                        PointerEventType.Enter -> pointerHovered = true
                        PointerEventType.Exit -> pointerHovered = false
                        else -> Unit
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxWidth()) {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .desktopHorizontalPagerScroll(pagerState),
            ) { page ->
                val item = items[page]
                val nextIndex = (page + 1) % items.size
                val nextItem = if (items.size > 1) items[nextIndex] else null
                val badge = remember(item, preferredTokens) {
                    HomeFeaturedCurator.badgeLabel(item, preferredTokens)
                }
                HeroCard(
                    item = item,
                    nextItem = nextItem,
                    badgeLabel = badge,
                    slideNumber = page + 1,
                    slideCount = items.size,
                    onClick = { onOpenItem(item, items) },
                    onNextPreviewClick = {
                        hapticTick()
                        scope.launch {
                            pagerState.animateScrollToPage(
                                page = nextIndex,
                                animationSpec = tween(durationMillis = 520, easing = MusicoMotion.emphasized),
                            )
                        }
                    },
                    modifier = Modifier.graphicsLayer {
                        val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val absOff = abs(offset).coerceAtMost(1f)
                        val s = 1f - 0.045f * absOff
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.30f * (abs(offset) - 1f).coerceIn(0f, 1f)
                    },
                )
            }

            if (isDesktopPlatform && items.size > 1) {
                val arrowBg = if (Musico.extras.isDark) {
                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.96f)
                }
                val arrowBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Surface(
                    shape = CircleShape,
                    color = arrowBg,
                    border = arrowBorder,
                    shadowElevation = 5.dp,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 28.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            hapticTick()
                            val prev = (pagerState.currentPage - 1 + items.size) % items.size
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = prev,
                                    animationSpec = tween(durationMillis = 480, easing = MusicoMotion.emphasized),
                                )
                            }
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous slide",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = arrowBg,
                    border = arrowBorder,
                    shadowElevation = 5.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 28.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            hapticTick()
                            val next = (pagerState.currentPage + 1) % items.size
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = next,
                                    animationSpec = tween(durationMillis = 480, easing = MusicoMotion.emphasized),
                                )
                            }
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next slide",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── M3E Animated 5-Second Countdown Indicator Strip ──────────────
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(items.size) { i ->
                val active = pagerState.currentPage == i
                val dotWidth by animateDpAsState(
                    targetValue = if (active) 36.dp else 7.dp,
                    animationSpec = MusicoMotion.spatialFast(),
                    label = "heroDotW$i",
                )
                val dotBgColor by animateColorAsState(
                    targetValue = if (active) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        Musico.extras.inkFaint.copy(alpha = 0.55f)
                    },
                    animationSpec = MusicoMotion.effectsFast(),
                    label = "heroDotC$i",
                )
                val activeFill = MaterialTheme.colorScheme.primary
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            hapticTick()
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    page = i,
                                    animationSpec = tween(durationMillis = 480, easing = MusicoMotion.emphasized),
                                )
                            }
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (active && items.size > 1) {
                        HeroProgressPill(
                            progressState = slideProgressState,
                            trackColor = dotBgColor,
                            fillColor = activeFill,
                            modifier = Modifier.size(width = dotWidth.coerceAtLeast(8.dp), height = 7.dp),
                        )
                    } else {
                        Box(
                            Modifier
                                .size(width = dotWidth.coerceAtLeast(4.dp), height = 7.dp)
                                .clip(CircleShape)
                                .background(if (active) activeFill else dotBgColor),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroProgressPill(
    progressState: FloatState,
    trackColor: Color,
    fillColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val r = size.height / 2f
        val corner = CornerRadius(r, r)
        drawRoundRect(
            color = trackColor,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = corner,
        )
        val fillW = (size.width * progressState.floatValue.coerceIn(0f, 1f)).coerceAtLeast(size.height)
        drawRoundRect(
            color = fillColor,
            topLeft = Offset.Zero,
            size = Size(fillW.coerceAtMost(size.width), size.height),
            cornerRadius = corner,
        )
    }
}

@Composable
private fun HeroCard(
    item: ShelfItem,
    nextItem: ShelfItem?,
    badgeLabel: String,
    slideNumber: Int,
    slideCount: Int,
    onClick: () -> Unit,
    onNextPreviewClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = HomeLayoutMetrics.isCompactHero(maxWidth.value)
        val showUpNext = HomeLayoutMetrics.showHeroUpNextPreview(maxWidth.value) && nextItem != null
        val artworkSize = when {
            compact -> 108.dp
            showUpNext -> 196.dp
            else -> 176.dp
        }
        val cardPadding = if (compact) 14.dp else 22.dp
        val contentGap = if (compact) 14.dp else 22.dp
        val isDark = Musico.extras.isDark

        val cardColor = if (isDark) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        }
        val decorativeTint = MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.08f else 0.06f)

        Surface(
            shape = RoundedCornerShape(if (compact) 26.dp else 32.dp),
            color = cardColor,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.42f else 0.60f),
            ),
            shadowElevation = if (isDark) 0.dp else 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    when {
                        compact -> Modifier.heightIn(min = 152.dp)
                        showUpNext -> Modifier.height(242.dp)
                        else -> Modifier.height(220.dp)
                    },
                )
                .pressable(onClick = onClick, pressedScale = 0.98f),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Subtle M3 Expressive geometric SoftBurst12 backdrop accent
                if (!compact) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .offset(x = 36.dp, y = (-18).dp)
                            .size(230.dp)
                            .clip(ExpressiveShapes.SoftBurst12)
                            .background(decorativeTint),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(cardPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(contentGap),
                ) {
                    Artwork(
                        url = item.thumbnail,
                        contentDescription = item.title,
                        modifier = Modifier.size(artworkSize),
                        cornerRadius = if (compact) 18.dp else 24.dp,
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Icon(
                                            Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            badgeLabel,
                                            style = Musico.type.overline,
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                                if (!compact && slideCount > 1) {
                                    Text(
                                        "$slideNumber / $slideCount",
                                        style = Musico.type.tabularCaption,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                            Text(
                                item.title,
                                style = when {
                                    compact -> Musico.type.titleLarge.copy(fontSize = 19.sp, lineHeight = 23.sp)
                                    showUpNext -> Musico.type.headline.copy(fontSize = 27.sp, lineHeight = 32.sp)
                                    else -> Musico.type.headline
                                },
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.subtitle.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    item.subtitle,
                                    style = if (compact) Musico.type.caption else Musico.type.body,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = if (compact) 1 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primary,
                            ) {
                                Row(
                                    Modifier.padding(
                                        horizontal = if (compact) 14.dp else 20.dp,
                                        vertical = if (compact) 9.dp else 12.dp,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(if (compact) 18.dp else 22.dp),
                                    )
                                    Text(
                                        "Play now",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = if (compact) 12.5.sp else 14.sp,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }

                    // Widescreen right-side "Up Next in Spotlight" preview card (eliminates dead whitespace!)
                    if (showUpNext && nextItem != null) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = if (isDark) {
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.86f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                            modifier = Modifier
                                .width(236.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(onClick = onNextPreviewClick),
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        "UP NEXT",
                                        style = Musico.type.overline,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Icon(
                                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Jump to next spotlight",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Artwork(
                                        url = nextItem.thumbnail,
                                        contentDescription = nextItem.title,
                                        modifier = Modifier.size(60.dp),
                                        cornerRadius = 16.dp,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            nextItem.title,
                                            style = Musico.type.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        if (nextItem.subtitle.isNotBlank()) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                nextItem.subtitle,
                                                style = Musico.type.caption,
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                Text(
                                    "Auto-slides every 5s",
                                    style = Musico.type.caption,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Signature grid sections (Made for you / Trending) ─────────────────────

@Composable
private fun GridSection(
    title: String,
    items: List<ShelfItem>,
    numbered: Boolean,
    onOpenItem: (ShelfItem, List<ShelfItem>) -> Unit,
    currentTrackId: String?,
) {
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    var viewportWidthPx by remember { mutableFloatStateOf(0f) }

    Column {
        SectionHeader(
            title = title,
            trailing = {
                ShelfScrollButtons(
                    canScrollBackward = gridState.canScrollBackward,
                    canScrollForward = gridState.canScrollForward,
                    onScrollBackward = {
                        val step = HorizontalScrollMath.computePageStepPx(viewportWidthPx)
                        scope.launch {
                            gridState.animateScrollBy(-step, tween(320, easing = MusicoMotion.emphasized))
                        }
                    },
                    onScrollForward = {
                        val step = HorizontalScrollMath.computePageStepPx(viewportWidthPx)
                        scope.launch {
                            gridState.animateScrollBy(step, tween(320, easing = MusicoMotion.emphasized))
                        }
                    },
                )
            },
        )
        LazyHorizontalGrid(
            state = gridState,
            rows = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(416.dp)
                .onSizeChanged { viewportWidthPx = it.width.toFloat() }
                .desktopHorizontalScroll(gridState),
        ) {
            itemsIndexed(items, key = { idx, it -> "g$idx-${it.videoId}-${it.browseId}-${it.params}-${it.title}" }) { idx, item ->
                GridCard(
                    item = item,
                    rank = if (numbered) idx + 1 else null,
                    isCurrent = item.videoId != null && item.videoId == currentTrackId,
                    onClick = { onOpenItem(item, items) },
                    modifier = Modifier.staggerIn(index = idx),
                )
            }
        }
        PlatformHorizontalGridScrollbar(
            state = gridState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 6.dp),
        )
    }
}

@Composable
private fun GridCard(
    item: ShelfItem,
    rank: Int?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(if (rank != null) 188.dp else 156.dp)
            .clip(MusicoShapes.card)
            .pressable(onClick = onClick, pressedScale = 0.95f)
            .padding(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            rank?.let {
                Text(
                    "$it",
                    style = Musico.type.display,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = if (it <= 3) Musico.extras.mint else Musico.extras.inkFaint,
                    modifier = Modifier.width(36.dp),
                )
            }
            Artwork(
                url = item.thumbnail,
                contentDescription = item.title,
                modifier = Modifier.size(136.dp),
                cornerRadius = 22.dp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            style = Musico.type.title,
            color = if (isCurrent) Musico.extras.mint else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                item.subtitle,
                style = Musico.type.caption,
                color = Musico.extras.inkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Skeleton: keeps the layout rhythm while the feed loads ────────────────

@Composable
private fun HomeSkeleton() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 160.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Box(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .shimmer(),
            )
        }
        item { SectionHeader("Made for you") }
        item { SkeletonRow(tall = true) }
        item { SectionHeader("Trending") }
        item { SkeletonRow(tall = true) }
        item { SectionHeader("Fresh music") }
        item { SkeletonRow(tall = false) }
    }
}

@Composable
private fun SkeletonRow(tall: Boolean) {
    val size = if (tall) 150.dp else 120.dp
    Row(
        Modifier.padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(3) {
            Box(
                Modifier
                    .size(size)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .shimmer(),
            )
        }
    }
}
