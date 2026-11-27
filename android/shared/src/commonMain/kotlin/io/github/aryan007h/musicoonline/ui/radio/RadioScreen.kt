package io.github.aryan007h.musicoonline.ui.radio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aryan007h.musicoonline.core.ShelfItem
import io.github.aryan007h.musicoonline.ui.components.AmbientTopWash
import io.github.aryan007h.musicoonline.ui.components.ErrorState
import io.github.aryan007h.musicoonline.ui.components.musicoTopInset
import io.github.aryan007h.musicoonline.ui.player.ArtworkAtmosphere
import io.github.aryan007h.musicoonline.ui.theme.ExpressiveShapes
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.pressable
import io.github.aryan007h.musicoonline.ui.theme.shimmer
import io.github.aryan007h.musicoonline.ui.theme.staggerIn
import kotlin.math.abs

/**
 * Material 3 Expressive Radio tab: adaptive mood & genre station wall
 * with signature geometric scalloped / clover / burst corner emblems across Light & Dark mode.
 */
@Composable
fun RadioScreen(
    atmosphere: ArtworkAtmosphere,
    styles: List<ShelfItem>,
    loading: Boolean,
    error: String?,
    onOpenStyle: (ShelfItem) -> Unit,
    onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AmbientTopWash(
            atmosphere = atmosphere,
            height = 260.dp,
            intensity = 0.22f,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Column(
            Modifier
                .fillMaxSize()
                .musicoTopInset(),
        ) {
            Text(
                "RADIO",
                style = Musico.type.overline,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 22.dp, top = 16.dp),
            )
            Text(
                "Ambient, moods & stations",
                style = Musico.type.display,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 22.dp, top = 4.dp, bottom = 10.dp),
            )

            when {
                error != null && styles.isEmpty() -> ErrorState(message = error, onRetry = onRetry)
                loading && styles.isEmpty() -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 148.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(10, key = { "radio-skel-$it" }) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1.25f)
                                .clip(MusicoShapes.tile)
                                .shimmer(),
                        )
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 148.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(styles, key = { i, it -> "r$i-${it.params}-${it.title}" }) { i, style ->
                        RadioCard(
                            style = style,
                            index = i,
                            onClick = { onOpenStyle(style) },
                            modifier = Modifier.staggerIn(index = i),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RadioCard(
    style: ShelfItem,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val seed = abs(style.title.hashCode()) % 4
    val isDark = Musico.extras.isDark
    val (bg, fg) = when (seed) {
        0 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        1 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        2 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> (if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest) to MaterialTheme.colorScheme.onSurface
    }
    val emblemShape = ExpressiveShapes.radioEmblem(index)

    Surface(
        shape = MusicoShapes.card,
        color = bg,
        border = if (!isDark && seed == 3) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        } else {
            null
        },
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.25f)
            .pressable(onClick = onClick, pressedScale = 0.94f),
    ) {
        Box(Modifier.fillMaxSize()) {
            // Signature M3E geometric corner emblem
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 12.dp, y = 12.dp)
                    .size(72.dp)
                    .clip(emblemShape)
                    .background(fg.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Radio,
                    contentDescription = null,
                    tint = fg.copy(alpha = 0.42f),
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 16.dp, end = 34.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    style.title,
                    style = Musico.type.titleLarge,
                    color = fg,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (style.subtitle.isNotBlank()) {
                    Text(
                        style.subtitle,
                        style = Musico.type.caption,
                        color = fg.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
