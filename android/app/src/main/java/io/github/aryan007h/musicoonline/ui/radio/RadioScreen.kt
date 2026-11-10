package io.github.aryan007h.musicoonline.ui.radio

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.ShelfItem
import io.github.aryan007h.musicoonline.ui.components.ErrorState
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes

/**
 * The Radio tab: YouTube Music's mood/genre station universe — every card
 * opens a detail page of station playlists that play end-to-end. Ambient,
 * focus, workout, language packs — the whole grid.
 */
@Composable
fun RadioScreen(
    styles: List<ShelfItem>,
    loading: Boolean,
    error: String?,
    onOpenStyle: (ShelfItem) -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            "RADIO",
            style = Musico.type.section,
            color = Musico.extras.mint,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp),
        )
        Text(
            "Ambient, moods & stations",
            style = Musico.type.display.copy(fontSize = 26.sp),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, bottom = 4.dp),
        )

        when {
            error != null && styles.isEmpty() -> ErrorState(message = error, onRetry = onRetry)
            loading && styles.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 150.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(styles, key = { i, it -> "r$i-${it.params}-${it.title}" }) { _, style ->
                    RadioCard(style = style, onClick = { onOpenStyle(style) })
                }
            }
        }
    }
}

/**
 * Mood cards: the artwork blurred behind a solid color wash with the title
 * — the classic station-tile look. Colors rotate through the palette's
 * support hues so the grid reads like Apple Music's mood wall.
 */
@Composable
private fun RadioCard(style: ShelfItem, onClick: () -> Unit) {
    // Deterministic hue from the title so each card keeps its color between
    // recompositions.
    val seed = style.title.hashCode()
    val hueShift = (seed % 100) / 100f
    val wash = blend(Musico.extras.primary, Musico.extras.support, hueShift)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.25f)
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(wash.copy(alpha = 0.55f), Musico.extras.bg2),
                    ),
                ),
        )
        Text(
            style.title,
            style = Musico.type.display.copy(fontSize = 20.sp, lineHeight = 24.sp),
            color = Color.White,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp),
        )
        if (style.subtitle.isNotBlank()) {
            Text(
                style.subtitle,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                maxLines = 1,
            )
        }
    }
}

/** Linear blend between two colors in sRGB space. */
private fun blend(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)
