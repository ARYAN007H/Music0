package io.github.aryan007h.musicoonline.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.Shelf
import io.github.aryan007h.musicoonline.core.ShelfItem
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.components.ErrorState
import io.github.aryan007h.musicoonline.ui.components.SectionHeader
import io.github.aryan007h.musicoonline.ui.components.ShelfCarousel
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.pressable
import io.github.aryan007h.musicoonline.ui.theme.shimmer
import io.github.aryan007h.musicoonline.ui.theme.staggerIn
import kotlinx.coroutines.delay

/**
 * Apple Music's home: one big hero carousel up top, then loud titled
 * sections — "Made for you" as a two-row grid of square cards, "Trending"
 * with rank numerals, everything else as cover carousels. Shelves render
 * generically from the core's home feed, so YTM re-layouts don't break us.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    shelves: List<Shelf>,
    loading: Boolean,
    error: String?,
    styleItems: List<ShelfItem>,
    moreLoading: Boolean,
    personalizing: Boolean,
    personalizationError: String?,
    onOpenItem: (ShelfItem, List<ShelfItem>) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    currentTrackId: String?,
) {
    when {
        error != null && shelves.isEmpty() -> ErrorState(message = error, onRetry = onRetry)
        loading && shelves.isEmpty() -> HomeSkeleton()
        else -> Column {
            PersonalizationBanner(personalizing, personalizationError)
            HomeFeed(shelves, styleItems, moreLoading, onOpenItem, onRetry, onLoadMore, currentTrackId)
        }
    }
}

/** Quiet status strip: "making it yours…" while building, notice on partial failure. */
@Composable
private fun PersonalizationBanner(personalizing: Boolean, error: String?) {
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
        error != null -> Text(
            error,
            style = Musico.type.caption,
            color = Musico.extras.coral,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun HomeFeed(
    shelves: List<Shelf>,
    styleItems: List<ShelfItem>,
    moreLoading: Boolean,
    onOpenItem: (ShelfItem, List<ShelfItem>) -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    currentTrackId: String?,
) {
    // Section mapping (Apple Music-ish): the first substantive shelf becomes
    // the hero carousel; the next two playable shelves take the signature
    // grid treatments; the rest render as cover carousels.
    val (heroShelf, gridA, gridB, rest) = remember(shelves) {
        val content = shelves.filter { it.items.isNotEmpty() }
        val hero = content.firstOrNull()
        val playable = content.filter { shelf -> shelf.items.any { it.videoId != null } }
        val heroIsPlayable = hero?.items?.any { it.videoId != null } == true
        val grids = (playable - setOfNotNull(hero)).take(2)
        val used = setOfNotNull(hero) + grids.toSet()
        HomeSections(hero, grids.getOrNull(0), grids.getOrNull(1), content.filterNot { used.contains(it) })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        heroShelf?.let {
            item(key = "hero") {
                HeroCarousel(
                    items = it.items.take(8),
                    onOpenItem = onOpenItem,
                )
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
        // Endless browsing: once the user nears the end, chain charts +
        // the genre style grid (each style card opens a full detail page).
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

/** Fires [onNearEnd] when composed — it only renders near the list's end. */
@Composable
private fun EndDetector(onNearEnd: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onNearEnd() }
    Spacer(Modifier.height(8.dp))
}

private data class HomeSections(
    val hero: Shelf?,
    val gridA: Shelf?,
    val gridB: Shelf?,
    val rest: List<Shelf>,
)

// ── Hero carousel ─────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCarousel(
    items: List<ShelfItem>,
    onOpenItem: (ShelfItem, List<ShelfItem>) -> Unit,
) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size })

    // Auto-advance every 6 s — the quiet showmanship Apple Music has. Pause
    // on user drag (pageCount state respects interaction) by checking
    // isScrollInProgress from a derived read.
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
        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 14.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val item = items[page]
            HeroCard(
                item = item,
                onClick = { onOpenItem(item, items) },
                modifier = Modifier.graphicsLayer {
                    // ColorOS carousel depth: neighbours shrink and dim as
                    // they leave the stage; the current card holds presence.
                    val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                    val abs = kotlin.math.abs(offset).coerceAtMost(1f)
                    val s = 1f - 0.06f * abs
                    scaleX = s
                    scaleY = s
                    alpha = 1f - 0.35f * (kotlin.math.abs(offset) - 1f).coerceIn(0f, 1f)
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
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
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = dotWidth, height = 6.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(item: ShelfItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.55f)
            .clip(MusicoShapes.hero)
            .pressable(onClick = onClick, pressedScale = 0.97f),
    ) {
        Artwork(
            url = item.thumbnail,
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 28.dp,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color(0xCC0A0A12),
                    ),
                ),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
        ) {
            if (item.subtitle.isNotBlank()) {
                Text(
                    item.subtitle.uppercase(),
                    style = Musico.type.section,
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = Musico.extras.mint,
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                item.title,
                style = Musico.type.display,
                fontSize = 26.sp,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
    Column {
        SectionHeader(title)
        // Explicit cell sizes: LazyHorizontalGrid measures cells with an
        // unbounded main axis, so fixed artwork sizes keep rows honest.
        // Two rows of (art 140 + caption ≈ 182) + spacing.
        LazyHorizontalGrid(
            rows = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(386.dp),
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
            .width(172.dp)
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
                    .padding(horizontal = 20.dp, vertical = 8.dp)
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
