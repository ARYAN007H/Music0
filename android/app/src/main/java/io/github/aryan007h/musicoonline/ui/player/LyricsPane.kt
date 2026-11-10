package io.github.aryan007h.musicoonline.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.EnhancedLyrics
import io.github.aryan007h.musicoonline.ui.theme.Musico


/**
 * Word-by-word karaoke pane. Strictly three words on screen: the one being
 * sung (huge, fill-animated), and its small previous/next neighbors —
 * exactly the full-screen-lyrics spec. Word timing comes from the core's
 * LRCLIB synthesis (EnhancedLyrics.words); tap a word to seek there.
 *
 * When only line-level timing exists, the same three-slot presentation
 * degrades to lines (current line big, neighbors small) — never fake words.
 */

// ── Smooth position: interpolate between the controller's 200 ms polls on
//    the frame clock, so word transitions are frame-accurate. ──────────────

@Composable
fun rememberSmoothPositionMs(
    polledPositionMs: Long,
    isPlaying: Boolean,
    durationMs: Long,
): Long {
    val state = produceState(initialValue = polledPositionMs, polledPositionMs, isPlaying, durationMs) {
        if (!isPlaying || polledPositionMs <= 0) {
            value = polledPositionMs
            return@produceState
        }
        val anchorPos = polledPositionMs
        val anchorNanos = System.nanoTime()
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                value = (anchorPos + (now - anchorNanos) / 1_000_000L).coerceAtMost(durationMs)
            }
        }
    }
    return state.value
}

private data class WordTiming(
    val word: String,
    val startMs: Long,
    val endMs: Long,
)

private data class LyricSlot(
    val lines: List<String>, // fallback: whole lines
    val words: List<WordTiming>,
    val synced: Boolean,
)

private fun EnhancedLyrics.toSlot(): LyricSlot {
    val flatWords = lines.flatMap { line -> line.words }
    return if (hasWordSync && flatWords.isNotEmpty()) {
        LyricSlot(
            lines = lines.map { it.text },
            words = flatWords
                .filter { it.word.isNotBlank() }
                .map { WordTiming(it.word, it.startMs, it.endMs) }
                .sortedBy { it.startMs },
            synced = true,
        )
    } else {
        LyricSlot(
            lines = lines.map { it.text },
            words = emptyList(),
            synced = synced,
        )
    }
}

@Composable
fun LyricsPane(
    lyrics: EnhancedLyrics?,
    loading: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            loading -> LyricsSkeleton()
            lyrics == null -> LyricsEmpty()
            else -> SyncedLyricStage(lyrics, positionMs, onSeek)
        }
    }
}

// ── The three-slot karaoke stage ──────────────────────────────────────────

@Composable
private fun SyncedLyricStage(
    lyrics: EnhancedLyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
) {
    val slot = remember(lyrics) { lyrics.toSlot() }
    if (slot.words.isNotEmpty()) {
        WordStage(slot.words, positionMs, onSeek)
    } else {
        LineStage(slot.lines, lyrics, positionMs, onSeek)
    }
}

@Composable
private fun WordStage(
    words: List<WordTiming>,
    positionMs: Long,
    onSeek: (Long) -> Unit,
) {
    // Recomputed per composition (cheap binary search at frame rate) —
    // derivedStateOf would cache over the plain positionMs parameter and
    // freeze the index.
    val currentIdx = remember(positionMs) { findWordAt(words, positionMs) }

    val prev = currentIdx.let { if (it > 0) words[it - 1] else null }
    val current = currentIdx.takeIf { it >= 0 }?.let { words[it] }
    val next = currentIdx.let { if (it >= 0 && it + 1 < words.size) words[it + 1] else null }
    // Before the first word: show the upcoming one as current-in-waiting.
    val effectiveCurrent = current ?: next

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
    ) {
        NeighborWord(
            text = prev?.word ?: " ",
            onClick = { prev?.let { onSeek(it.startMs) } },
        )
        Spacer(Modifier.height(18.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(120.dp)) {
            effectiveCurrent?.let { word ->
                val fillFraction = wordFill(word, positionMs)
                CurrentWord(
                    text = word.word,
                    fillFraction = fillFraction,
                    onClick = { onSeek(word.startMs) },
                    key = word.startMs,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        NeighborWord(
            text = next?.word ?: " ",
            onClick = { next?.let { onSeek(it.startMs) } },
        )
    }
}

private fun wordFill(word: WordTiming, positionMs: Long): Float {
    val span = (word.endMs - word.startMs).coerceAtLeast(1)
    return ((positionMs - word.startMs).toFloat() / span).coerceIn(0f, 1f)
}

/** Last word whose start time has passed — the one being sung right now. */
private fun findWordAt(words: List<WordTiming>, positionMs: Long): Int {
    var lo = 0
    var hi = words.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (words[mid].startMs <= positionMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return found
}

@Composable
private fun CurrentWord(
    text: String,
    fillFraction: Float,
    onClick: () -> Unit,
    key: Long,
) {
    val sung = Musico.extras.mint
    val unsung = Color(0xFF58586E)

    val brush = Brush.horizontalGradient(
        0.0f to sung,
        fillFraction to sung,
        fillFraction to unsung,
        1.0f to unsung,
    )
    AnimatedContent(
        targetState = key to text,
        transitionSpec = {
            (
                slideInVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    initialOffsetY = { it / 2 },
                ) + fadeIn()
                ) togetherWith (
                slideOutVertically(
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
                    targetOffsetY = { -it / 3 },
                ) + fadeOut()
                )
        },
        label = "currentWord",
        modifier = Modifier.clickable(onClick = onClick),
    ) { (_, word) ->
        androidx.compose.material3.Text(
            text = word,
            style = Musico.type.lyricCurrent.copy(brush = brush),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun NeighborWord(text: String, onClick: () -> Unit) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically { it / 4 } + fadeIn()) togetherWith
                (slideOutVertically { -it / 4 } + fadeOut())
        },
        label = "neighborWord",
        modifier = Modifier.clickable(enabled = text.isNotBlank(), onClick = onClick),
    ) { word ->
        Text(
            text = word,
            style = Musico.type.lyricNeighbor,
            color = Color(0xFF8B8BA6),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Line-granularity fallback (unsynced or line-synced lyrics) ────────────

@Composable
private fun LineStage(
    lines: List<String>,
    lyrics: EnhancedLyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
) {
    val synced = lyrics.synced && lyrics.lines.any { it.timeSecs > 0f }
    val currentLineIdx = if (!synced) {
        -1
    } else {
        var found = -1
        lyrics.lines.forEachIndexed { i, line ->
            if (line.timeSecs * 1000f <= positionMs) found = i
        }
        found
    }
    val prev = currentLineIdx.let { if (it > 0) lines[it - 1] else null }
    val current = currentLineIdx.takeIf { it >= 0 }?.let { lines[it] }
    val next = currentLineIdx.let { if (it >= 0 && it + 1 < lines.size) lines[it + 1] else null }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        NeighborWord(text = prev ?: " ", onClick = {})
        Spacer(Modifier.height(14.dp))
        Box(Modifier.height(140.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = current ?: next ?: "♪",
                transitionSpec = {
                    (slideInVertically { it / 3 } + fadeIn()) togetherWith
                        (slideOutVertically { -it / 3 } + fadeOut())
                },
                label = "currentLine",
            ) { line ->
                Text(
                    text = line,
                    style = Musico.type.lyricCurrent.copy(fontSize = 38.sp, lineHeight = 46.sp),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        NeighborWord(text = next ?: " ", onClick = {})
    }
}

// ── Skeleton + empty ──────────────────────────────────────────────────────

@Composable
private fun LyricsSkeleton() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SkeletonBar(width = 140.dp)
        Box(
            Modifier
                .height(64.dp)
                .fillMaxWidth(0.7f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1E1E2E)),
        )
        Box(
            Modifier
                .height(64.dp)
                .fillMaxWidth(0.55f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF262638)),
        )
        SkeletonBar(width = 110.dp)
    }
}

@Composable
private fun SkeletonBar(width: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .height(10.dp)
            .width(width)
            .clip(CircleShape)
            .background(Color(0xFF1E1E2E)),
    )
}

@Composable
private fun LyricsEmpty() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "♪",
            style = Musico.type.display.copy(fontSize = 44.sp),
            color = Color(0xFF3A3A50),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "No lyrics found for this one",
            style = Musico.type.body,
            color = Color(0xFF6E6E88),
            textAlign = TextAlign.Center,
        )
    }
}
