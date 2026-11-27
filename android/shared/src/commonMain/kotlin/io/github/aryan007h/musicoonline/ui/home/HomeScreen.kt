package io.github.aryan007h.musicoonline.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
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
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.hapticThud
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import io.github.aryan007h.musicoonline.ui.theme.pressable
import io.github.aryan007h.musicoonline.ui.theme.shimmer
import io.github.aryan007h.musicoonline.ui.theme.staggerIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Editorial Home Feed with viscerally alive Apple Music + Spotify craft:
 * - AmbientTopWash radiating the active track's cover atmosphere behind the header
 * - Dynamic time-of-day greeting
 * - Spotify-style 2-column Quick-Pick Instant-Resume Grid (Liked Songs, Playlists,
 *   Smart Mixes, Recently Played — jump back in with 1 tap)
 * - Auto-advancing Hero Carousel, Smart Mixes, Signature 2-row Grids, Cover Carousels
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
                    likedCount = likedCount,
                    downloadsCount = downloadsCount,
                    styleItems = styleItems,
                    moreLoading = moreLoading,
                    onOpenItem = onOpenItem,
                    onOpenCollection = onOpenCollection,
                    onOpenTrack = onOpenTrack,
                    onOpenCatalogEntry = onOpenCatalogEntry,
                    onStartMood = onStartMood,
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

/** Pure responsive breakpoints & mood-filter logic for HomeScreen unit tests. */
object HomeLayoutMetrics {
    fun isCompactHero(cardWidthDp: Float): Boolean = cardWidthDp < 440f
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
    likedCount: Int,
    downloadsCount: Int,
    styleItems: List<ShelfItem>,
    moreLoading: Boolean,
    onOpenItem: (ShelfItem, List<ShelfItem>) -> Unit,
    onOpenCollection: (LibraryCollectionRef) -> Unit,
    onOpenTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onOpenCatalogEntry: (CatalogEntry) -> Unit,
    onStartMood: (String) -> Unit,
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

    val (heroShelf, gridA, gridB, rest) = remember(filteredShelves) {
        val hero = filteredShelves.firstOrNull()
        val playable = filteredShelves.filter { shelf -> shelf.items.any { it.videoId != null } }
        val grids = (playable - setOfNotNull(hero)).take(2)
        val used = setOfNotNull(hero) + grids.toSet()
        HomeSections(hero, grids.getOrNull(0), grids.getOrNull(1), filteredShelves.filterNot { used.contains(it) })
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
        contentPadding = PaddingValues(bottom = 148.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Dynamic Greeting Header ─────────────────────────────────────
        item(key = "home-greeting") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 2.dp),
            ) {
                Text(
                    greeting.uppercase(),
                    style = Musico.type.section,
                    fontSize = 11.sp,
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
        }

        // ── M3E Mood & Vibe Filter Chips ────────────────────────────────
        item(key = "home-mood-chips") {
            DesktopScrollableChipRow(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                edgeFadeColor = MaterialTheme.colorScheme.background,
            ) {
                HOME_MOOD_CHIPS.forEach { mood ->
                    val active = selectedMood == mood
                    Surface(
                        shape = RoundedCornerShape(if (active) 12.dp else 20.dp),
                        color = if (active) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.pressable(
                            onClick = {
                                hapticTick()
                                selectedMood = mood
                            },
                            pressedScale = 0.94f,
                        ),
                    ) {
                        Text(
                            mood,
                            fontSize = 13.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            color = if (active) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
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
                                .size(48.dp)
                                .clip(RoundedCornerShape(16.dp))
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

        // ── M3E 2-Column Quick-Pick Instant-Resume Grid ─────────────────
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

        heroShelf?.let {
            item(key = "hero") {
                HeroCarousel(
                    items = it.items.take(8),
                    onOpenItem = onOpenItem,
                )
            }
        }

        // ── Favorite Artists Circular Row ───────────────────────────────
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
                        items(savedArtists.take(12), key = { "hart-${it.id}" }) { artist ->
                            Column(
                                modifier = Modifier
                                    .width(104.dp)
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
                                    cornerRadius = 52.dp,
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
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        entries.chunked(2).forEachIndexed { rowIdx, pair ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEachIndexed { colIdx, entry ->
                    QuickPickCard(
                        entry = entry,
                        isCurrent = entry is QuickPickEntry.TrackPick && entry.track.videoId == currentTrackId,
                        isPlaying = isPlaying,
                        onClick = {
                            hapticThud()
                            onClick(entry)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .staggerIn(index = rowIdx * 2 + colIdx),
                    )
                }
                if (pair.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun QuickPickCard(
    entry: QuickPickEntry,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrent) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = modifier
            .height(58.dp)
            .pressable(onClick = onClick, pressedScale = 0.96f),
    ) {
        Row(
            Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (entry) {
                is QuickPickEntry.Liked -> Box(
                    Modifier
                        .size(58.dp)
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
                        .size(58.dp)
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
                    modifier = Modifier.size(58.dp),
                    cornerRadius = 0.dp,
                )
                is QuickPickEntry.MixPick -> PlaylistMosaicArtwork(
                    thumbnails = entry.mix.tracks.map { it.thumbnail },
                    modifier = Modifier.size(58.dp),
                    cornerRadius = 0.dp,
                )
                is QuickPickEntry.TrackPick -> Box(Modifier.size(58.dp)) {
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
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
            ) {
                Text(
                    entry.title,
                    style = Musico.type.title,
                    fontSize = 13.sp,
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
                    fontSize = 11.sp,
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

private data class HomeSections(
    val hero: Shelf?,
    val gridA: Shelf?,
    val gridB: Shelf?,
    val rest: List<Shelf>,
)

@Composable
private fun HomeSmartMixCard(mix: SmartMix, onClick: () -> Unit) {
    Surface(
        shape = MusicoShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
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

// ── Hero carousel ─────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCarousel(
    items: List<ShelfItem>,
    onOpenItem: (ShelfItem, List<ShelfItem>) -> Unit,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })
    val scope = rememberCoroutineScope()

    val userScrolling by remember { derivedStateOf { pagerState.isScrollInProgress } }
    LaunchedEffect(userScrolling, items.size) {
        while (items.size > 1) {
            delay(6000)
            if (!userScrolling) {
                pagerState.animateScrollToPage((pagerState.currentPage + 1) % items.size)
            }
        }
    }

    Column {
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
                HeroCard(
                    item = item,
                    onClick = { onOpenItem(item, items) },
                    modifier = Modifier.graphicsLayer {
                        val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                        val abs = kotlin.math.abs(offset).coerceAtMost(1f)
                        val s = 1f - 0.06f * abs
                        scaleX = s
                        scaleY = s
                        alpha = 1f - 0.35f * (kotlin.math.abs(offset) - 1f).coerceIn(0f, 1f)
                    },
                )
            }

            if (isDesktopPlatform && items.size > 1) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xAA0C0D16),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 32.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            val prev = (pagerState.currentPage - 1 + items.size) % items.size
                            scope.launch { pagerState.animateScrollToPage(prev) }
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous slide",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xAA0C0D16),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 32.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            val next = (pagerState.currentPage + 1) % items.size
                            scope.launch { pagerState.animateScrollToPage(next) }
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next slide",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(items.size) { i ->
                val active = pagerState.currentPage == i
                val dotWidth by animateDpAsState(
                    targetValue = if (active) 18.dp else 6.dp,
                    animationSpec = MusicoMotion.expressive(),
                    label = "heroDotW$i",
                )
                val dotColor by animateColorAsState(
                    targetValue = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Musico.extras.inkFaint
                    },
                    animationSpec = MusicoMotion.gentle(),
                    label = "heroDotC$i",
                )
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable {
                            scope.launch { pagerState.animateScrollToPage(i) }
                        }
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = dotWidth.coerceAtLeast(0.dp), height = 6.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroCard(item: ShelfItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = HomeLayoutMetrics.isCompactHero(maxWidth.value)
        val artworkSize = if (compact) 104.dp else 176.dp
        val cardPadding = if (compact) 14.dp else 18.dp
        val contentGap = if (compact) 14.dp else 18.dp

        Surface(
            shape = MusicoShapes.hero,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (compact) Modifier.heightIn(min = 148.dp) else Modifier.height(212.dp))
                .pressable(onClick = onClick, pressedScale = 0.97f),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(cardPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(contentGap),
            ) {
                Artwork(
                    url = item.thumbnail,
                    contentDescription = item.title,
                    modifier = Modifier.size(artworkSize),
                    cornerRadius = if (compact) 18.dp else 22.dp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
                ) {
                    Column {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    "FEATURED",
                                    style = Musico.type.overline,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }
                        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
                        Text(
                            item.title,
                            style = if (compact) {
                                Musico.type.titleLarge.copy(fontSize = 19.sp, lineHeight = 23.sp)
                            } else {
                                Musico.type.headline
                            },
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (item.subtitle.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            Text(
                                item.subtitle,
                                style = Musico.type.caption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (compact) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Row(
                            Modifier.padding(
                                horizontal = if (compact) 12.dp else 16.dp,
                                vertical = if (compact) 8.dp else 10.dp,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                            )
                            Text(
                                "Play now",
                                fontWeight = FontWeight.Bold,
                                fontSize = if (compact) 12.sp else 13.sp,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                softWrap = false,
                            )
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
        contentPadding = PaddingValues(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Box(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .fillMaxWidth()
                    .aspectRatio(1.55f)
                    .clip(MusicoShapes.hero)
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
