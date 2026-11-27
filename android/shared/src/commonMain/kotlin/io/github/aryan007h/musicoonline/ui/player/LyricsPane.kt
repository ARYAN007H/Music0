package io.github.aryan007h.musicoonline.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.EnhancedLyrics
import io.github.aryan007h.musicoonline.core.LyricLine
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.platform.isDesktopPlatform
import io.github.aryan007h.musicoonline.platform.shareLyricCard
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.hapticThud
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import io.github.aryan007h.musicoonline.ui.theme.pressable
import io.github.aryan007h.musicoonline.ui.theme.shimmer
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class LyricsDisplayMode(val id: String, val label: String) {
    CINEMA("cinema", "Cinema"),
    KINETIC("kinetic", "Stage");

    companion object {
        fun fromId(id: String?): LyricsDisplayMode =
            entries.firstOrNull { it.id == id } ?: CINEMA
    }
}

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
        var anchorFrameNanos = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (anchorFrameNanos < 0L) anchorFrameNanos = now
                val raw = anchorPos + (now - anchorFrameNanos) / 1_000_000L
                value = if (durationMs > 0) raw.coerceAtMost(durationMs) else raw
            }
        }
    }
    return state.value
}

@Composable
fun LyricsPane(
    track: OnlineTrack?,
    lyrics: EnhancedLyrics?,
    loading: Boolean,
    positionMs: Long,
    durationMs: Long,
    mode: LyricsDisplayMode,
    offsetMs: Long,
    atmosphere: ArtworkAtmosphere,
    pureCinema: Boolean,
    onTogglePureCinema: () -> Unit,
    onModeChange: (LyricsDisplayMode) -> Unit,
    onOffsetChange: (Long) -> Unit,
    onCycleBackdrop: () -> Unit,
    onSeek: (Long) -> Unit,
    onShareFeedback: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val accentColor = MaterialTheme.colorScheme.primary
    val onAccentColor = MaterialTheme.colorScheme.onPrimary

    var showSubLines by remember(lyrics?.trackId) { mutableStateOf(true) }
    var selectingForShare by remember(lyrics?.trackId) { mutableStateOf(false) }
    val selectedIndices = remember(lyrics?.trackId) { mutableStateListOf<Int>() }
    var sharingBusy by remember { mutableStateOf(false) }

    val adjustedPosMs = (positionMs + offsetMs).coerceAtLeast(0L)
    val hasSubLines = lyrics != null && (lyrics.hasRomanized || lyrics.hasTranslation ||
        lyrics.lines.any { !it.romanized.isNullOrBlank() || !it.translation.isNullOrBlank() })

    fun toggleSelectIndex(idx: Int) {
        hapticTick()
        if (idx in selectedIndices) {
            selectedIndices.remove(idx)
            if (selectedIndices.isEmpty()) selectingForShare = false
        } else if (selectedIndices.size < 4) {
            selectedIndices.add(idx)
            selectedIndices.sort()
        } else {
            onShareFeedback("Select up to 4 lyric lines for a card")
        }
    }

    Box(modifier.fillMaxSize()) {
        when {
            loading -> LyricsSkeleton()
            lyrics == null || lyrics.lines.isEmpty() -> LyricsEmpty()
            else -> {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = {
                        fadeIn(tween(MusicoMotion.MED)) togetherWith fadeOut(tween(MusicoMotion.FAST))
                    },
                    label = "lyricsModeSwitch",
                    modifier = Modifier.fillMaxSize(),
                ) { activeMode ->
                    when (activeMode) {
                        LyricsDisplayMode.CINEMA -> FlowingVerseCinema(
                            lyrics = lyrics,
                            adjustedPosMs = adjustedPosMs,
                            durationMs = durationMs,
                            accentColor = accentColor,
                            showSubLines = showSubLines && hasSubLines,
                            selectingForShare = selectingForShare,
                            selectedIndices = selectedIndices,
                            onTapCanvas = {
                                if (!selectingForShare) onTogglePureCinema()
                            },
                            onSeekToTime = { targetLyricMs ->
                                val playerTarget = (targetLyricMs - offsetMs).coerceAtLeast(0L)
                                onSeek(playerTarget)
                            },
                            onLongPressLine = { idx ->
                                hapticThud()
                                if (!selectingForShare) {
                                    selectingForShare = true
                                    selectedIndices.clear()
                                    selectedIndices.add(idx)
                                    val nextText = lyrics.lines.getOrNull(idx + 1)?.text
                                    if (!nextText.isNullOrBlank() && lyrics.lines[idx].text.length < 48) {
                                        selectedIndices.add(idx + 1)
                                    }
                                } else {
                                    toggleSelectIndex(idx)
                                }
                            },
                            onToggleSelectLine = ::toggleSelectIndex,
                        )
                        LyricsDisplayMode.KINETIC -> KineticLyricStage(
                            lyrics = lyrics,
                            adjustedPosMs = adjustedPosMs,
                            accentColor = accentColor,
                            onSeekToTime = { targetLyricMs ->
                                val playerTarget = (targetLyricMs - offsetMs).coerceAtLeast(0L)
                                onSeek(playerTarget)
                            },
                            onTapCanvas = onTogglePureCinema,
                        )
                    }
                }
            }
        }

        // Bottom Share Selection Bar (Light & Dark M3 Expressive floating bar)
        AnimatedVisibility(
            visible = selectingForShare && lyrics != null,
            enter = slideInVertically(MusicoMotion.spatialDefault()) { it } + fadeIn(tween(MusicoMotion.FAST)),
            exit = slideOutVertically(tween(MusicoMotion.MED)) { it } + fadeOut(tween(MusicoMotion.FAST)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Surface(
                shape = MusicoShapes.chip,
                color = if (Musico.extras.isDark) {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLowest
                },
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MusicoShapes.chip),
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .pressable(onClick = {
                                    selectingForShare = false
                                    selectedIndices.clear()
                                }),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Cancel",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "${selectedIndices.size}/4 lines selected",
                            style = Musico.type.tabularCaption,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    val canShare = selectedIndices.isNotEmpty() && !sharingBusy
                    Surface(
                        shape = MusicoShapes.chip,
                        color = if (canShare) accentColor else MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.pressable(
                            enabled = canShare && track != null,
                            onClick = {
                                val t = track ?: return@pressable
                                val picked = selectedIndices.mapNotNull { lyrics?.lines?.getOrNull(it)?.text }
                                if (picked.isEmpty()) return@pressable
                                sharingBusy = true
                                hapticThud()
                                scope.launch {
                                    val res = shareLyricCard(t, picked, atmosphere)
                                    sharingBusy = false
                                    selectingForShare = false
                                    selectedIndices.clear()
                                    res.onSuccess { msg -> onShareFeedback(msg) }
                                        .onFailure { e -> onShareFeedback("Couldn't render lyric card: ${e.message}") }
                                }
                            },
                            pressedScale = 0.94f,
                        ),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = null,
                                tint = if (canShare) onAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                if (sharingBusy) "Rendering…" else if (isDesktopPlatform) "Save Poster" else "Share Card",
                                color = if (canShare) onAccentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }

        // Floating Material 3 Expressive Lyrics Studio Toolbar
        AnimatedVisibility(
            visible = !pureCinema && !selectingForShare && lyrics != null && lyrics.lines.isNotEmpty(),
            enter = fadeIn(tween(MusicoMotion.MED)) + slideInVertically(MusicoMotion.spatialDefault()) { it / 2 },
            exit = fadeOut(tween(MusicoMotion.FAST)) + slideOutVertically(tween(MusicoMotion.FAST)) { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            LyricsStudioToolbar(
                mode = mode,
                offsetMs = offsetMs,
                hasSubLines = hasSubLines,
                showSubLines = showSubLines,
                synced = lyrics?.synced == true,
                accentColor = accentColor,
                onModeToggle = {
                    hapticTick()
                    onModeChange(
                        if (mode == LyricsDisplayMode.CINEMA) LyricsDisplayMode.KINETIC else LyricsDisplayMode.CINEMA,
                    )
                },
                onNudgeOffset = { delta ->
                    hapticTick()
                    onOffsetChange((offsetMs + delta).coerceIn(-5000L, 5000L))
                },
                onResetOffset = {
                    if (offsetMs != 0L) {
                        hapticTick()
                        onOffsetChange(0L)
                    }
                },
                onToggleSubLines = {
                    hapticTick()
                    showSubLines = !showSubLines
                },
                onCycleBackdrop = {
                    hapticTick()
                    onCycleBackdrop()
                },
                onEnterShareMode = {
                    val l = lyrics ?: return@LyricsStudioToolbar
                    hapticThud()
                    if (mode != LyricsDisplayMode.CINEMA) {
                        onModeChange(LyricsDisplayMode.CINEMA)
                    }
                    val active = findActiveLineIndex(l, adjustedPosMs).coerceAtLeast(0)
                    selectingForShare = true
                    selectedIndices.clear()
                    selectedIndices.add(active)
                    if (active + 1 < l.lines.size) selectedIndices.add(active + 1)
                },
            )
        }
    }
}

@Composable
private fun LyricsStudioToolbar(
    mode: LyricsDisplayMode,
    offsetMs: Long,
    hasSubLines: Boolean,
    showSubLines: Boolean,
    synced: Boolean,
    accentColor: Color,
    onModeToggle: () -> Unit,
    onNudgeOffset: (Long) -> Unit,
    onResetOffset: () -> Unit,
    onToggleSubLines: () -> Unit,
    onCycleBackdrop: () -> Unit,
    onEnterShareMode: () -> Unit,
) {
    Surface(
        shape = MusicoShapes.chip,
        color = if (Musico.extras.isDark) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainerLowest
        },
        shadowElevation = if (Musico.extras.isDark) 4.dp else 6.dp,
        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f), MusicoShapes.chip),
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ToolbarPillButton(
                icon = if (mode == LyricsDisplayMode.CINEMA) Icons.Filled.ViewStream else Icons.Filled.ViewAgenda,
                label = mode.label,
                active = true,
                onClick = onModeToggle,
            )

            if (synced) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(MusicoShapes.chip)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .pressable(onClick = { onNudgeOffset(-500L) }, pressedScale = 0.85f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Remove,
                            contentDescription = "Delay lyrics 0.5s",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    val offsetText = when {
                        offsetMs == 0L -> "Sync"
                        offsetMs > 0 -> "+${offsetMs / 1000f}s"
                        else -> "${offsetMs / 1000f}s"
                    }
                    Text(
                        offsetText,
                        style = Musico.type.tabularCaption,
                        fontSize = 11.sp,
                        color = if (offsetMs != 0L) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (offsetMs != 0L) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clickable(onClick = onResetOffset)
                            .padding(horizontal = 4.dp),
                    )
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .pressable(onClick = { onNudgeOffset(500L) }, pressedScale = 0.85f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Advance lyrics 0.5s",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }

            if (hasSubLines) {
                ToolbarIconButton(
                    icon = Icons.Filled.Translate,
                    contentDescription = "Toggle translation",
                    active = showSubLines,
                    onClick = onToggleSubLines,
                )
            }

            ToolbarIconButton(
                icon = Icons.Filled.AutoAwesome,
                contentDescription = "Cycle backdrop",
                active = false,
                onClick = onCycleBackdrop,
            )

            ToolbarIconButton(
                icon = Icons.Filled.Share,
                contentDescription = "Share lyric card",
                active = false,
                onClick = onEnterShareMode,
            )
        }
    }
}

@Composable
private fun ToolbarPillButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(MusicoShapes.chip)
            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .pressable(onClick = onClick, pressedScale = 0.92f)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp),
        )
        Text(
            label,
            color = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ToolbarIconButton(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .pressable(onClick = onClick, pressedScale = 0.88f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
    }
}

@Composable
private fun FlowingVerseCinema(
    lyrics: EnhancedLyrics,
    adjustedPosMs: Long,
    durationMs: Long,
    accentColor: Color,
    showSubLines: Boolean,
    selectingForShare: Boolean,
    selectedIndices: List<Int>,
    onTapCanvas: () -> Unit,
    onSeekToTime: (Long) -> Unit,
    onLongPressLine: (Int) -> Unit,
    onToggleSelectLine: (Int) -> Unit,
) {
    val lines = lyrics.lines
    val isSynced = lyrics.synced && lines.any { it.timeSecs > 0f }
    val activeIdx = remember(adjustedPosMs, lines, isSynced) {
        if (isSynced) findActiveLineIndex(lyrics, adjustedPosMs) else -1
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var userPausedFollow by remember(lyrics.trackId) { mutableStateOf(false) }

    LaunchedEffect(listState.interactionSource) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                userPausedFollow = true
            }
        }
    }

    val interludeProgress: Float? = remember(adjustedPosMs, activeIdx, lines, isSynced) {
        if (!isSynced || lines.isEmpty()) return@remember null
        if (activeIdx < 0) {
            val firstMs = (lines.first().timeSecs * 1000f).toLong()
            if (firstMs >= 3500L && adjustedPosMs < firstMs) {
                (adjustedPosMs.toFloat() / firstMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
            } else {
                null
            }
        } else {
            val cur = lines[activeIdx]
            val next = lines.getOrNull(activeIdx + 1) ?: return@remember null
            val curEndMs = cur.words.lastOrNull()?.endMs
                ?: ((cur.timeSecs * 1000f).toLong() + 3200L)
            val nextStartMs = (next.timeSecs * 1000f).toLong()
            val gap = nextStartMs - curEndMs
            if (gap >= 4200L && adjustedPosMs in (curEndMs + 400L) until nextStartMs) {
                ((adjustedPosMs - curEndMs).toFloat() / gap.coerceAtLeast(1L)).coerceIn(0f, 1f)
            } else {
                null
            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val viewportPx = with(density) { maxHeight.toPx() }
        val centerOffsetPx = -(viewportPx * 0.28f).toInt()
        val topPad = maxHeight * 0.22f
        val bottomPad = maxHeight * 0.42f

        LaunchedEffect(activeIdx, userPausedFollow, selectingForShare) {
            if (isSynced && activeIdx >= 0 && !userPausedFollow && !selectingForShare) {
                listState.animateScrollToItem(index = activeIdx, scrollOffset = centerOffsetPx)
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topPad, bottom = bottomPad, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTapCanvas,
                ),
        ) {
            if (activeIdx < 0 && interludeProgress != null) {
                item(key = "intro-interlude") {
                    InstrumentalInterludeDots(
                        progress = interludeProgress,
                        accentColor = accentColor,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }

            itemsIndexed(
                items = lines,
                key = { i, l -> "${lyrics.trackId}-$i-${l.timeSecs}" },
            ) { index, line ->
                val distance = if (!isSynced) 0 else abs(index - activeIdx)
                val isActive = isSynced && index == activeIdx
                val isPast = isSynced && activeIdx >= 0 && index < activeIdx
                val isSelected = index in selectedIndices

                val nextLineMs = lines.getOrNull(index + 1)?.let { (it.timeSecs * 1000f).toLong() }
                    ?: durationMs.takeIf { it > 0L }
                    ?: ((line.timeSecs * 1000f).toLong() + 4000L)

                CinemaLyricRow(
                    line = line,
                    isActive = isActive,
                    isPast = isPast,
                    isSynced = isSynced,
                    distance = distance,
                    adjustedPosMs = adjustedPosMs,
                    nextLineMs = nextLineMs,
                    accentColor = accentColor,
                    showSubLines = showSubLines,
                    clearFocus = userPausedFollow || selectingForShare || !isSynced,
                    selectingForShare = selectingForShare,
                    isSelected = isSelected,
                    onClick = {
                        if (selectingForShare) {
                            onToggleSelectLine(index)
                        } else if (isSynced) {
                            hapticTick()
                            userPausedFollow = false
                            onSeekToTime((line.timeSecs * 1000f).toLong())
                        } else {
                            onTapCanvas()
                        }
                    },
                    onLongClick = { onLongPressLine(index) },
                )

                if (isActive && interludeProgress != null) {
                    Spacer(Modifier.height(14.dp))
                    InstrumentalInterludeDots(
                        progress = interludeProgress,
                        accentColor = accentColor,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = isSynced && userPausedFollow && !selectingForShare && activeIdx >= 0,
            enter = fadeIn(tween(MusicoMotion.FAST)) + slideInVertically { -it / 2 },
            exit = fadeOut(tween(MusicoMotion.FAST)) + slideOutVertically { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 12.dp),
        ) {
            Surface(
                shape = MusicoShapes.chip,
                color = MaterialTheme.colorScheme.secondaryContainer,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .pressable(
                        onClick = {
                            hapticTick()
                            userPausedFollow = false
                            scope.launch {
                                if (activeIdx >= 0) {
                                    listState.animateScrollToItem(activeIdx, centerOffsetPx)
                                }
                            }
                        },
                        pressedScale = 0.93f,
                    ),
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "Sync to live",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CinemaLyricRow(
    line: LyricLine,
    isActive: Boolean,
    isPast: Boolean,
    isSynced: Boolean,
    distance: Int,
    adjustedPosMs: Long,
    nextLineMs: Long,
    accentColor: Color,
    showSubLines: Boolean,
    clearFocus: Boolean,
    selectingForShare: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val inkDim = MaterialTheme.colorScheme.onSurfaceVariant

    val targetAlpha = when {
        selectingForShare -> if (isSelected) 1f else 0.48f
        !isSynced -> 0.92f
        isActive -> 1f
        clearFocus -> 0.86f
        distance == 1 -> if (isPast) 0.44f else 0.60f
        distance == 2 -> 0.36f
        else -> 0.26f
    }
    val targetScale = when {
        selectingForShare -> if (isSelected) 1.0f else 0.97f
        !isSynced -> 0.97f
        isActive -> 1.0f
        clearFocus -> 0.97f
        distance == 1 -> 0.95f
        else -> 0.92f
    }

    val animAlpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = MusicoMotion.cinema(), label = "verseAlpha")
    val animScale by animateFloatAsState(targetValue = targetScale, animationSpec = MusicoMotion.cinema(), label = "verseScale")
    val activeBgColor by animateColorAsState(
        targetValue = when {
            selectingForShare && isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
            selectingForShare -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f)
            isActive -> if (Musico.extras.isDark) {
                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f)
            }
            else -> Color.Transparent
        },
        animationSpec = tween(MusicoMotion.MED),
        label = "verseCardBg",
    )
    val selectedBorderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else Color.Transparent,
        animationSpec = tween(MusicoMotion.FAST),
        label = "verseSelectBorder",
    )

    val rowShape = RoundedCornerShape(22.dp)
    val containerModifier = Modifier
        .fillMaxWidth()
        .clip(rowShape)
        .background(activeBgColor)
        .then(
            if (selectingForShare) {
                Modifier.border(1.5.dp, selectedBorderColor, rowShape)
            } else {
                Modifier
            },
        )
        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        .padding(
            horizontal = if (isActive || selectingForShare) 16.dp else 10.dp,
            vertical = if (isActive || selectingForShare) 12.dp else 4.dp,
        )

    Row(
        modifier = containerModifier
            .graphicsLayer {
                alpha = animAlpha.coerceIn(0f, 1f)
                val safeScale = animScale.coerceAtLeast(0.01f)
                scaleX = safeScale
                scaleY = safeScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isActive && !selectingForShare) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(accentColor),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            if (isActive && line.words.isNotEmpty() && !selectingForShare) {
                val styledLine = buildAnnotatedString {
                    line.words.forEachIndexed { wi, word ->
                        if (wi > 0) append(" ")
                        val span = (word.endMs - word.startMs).coerceAtLeast(1L)
                        val progress = ((adjustedPosMs - word.startMs).toFloat() / span).coerceIn(0f, 1f)
                        when {
                            adjustedPosMs >= word.endMs -> {
                                withStyle(SpanStyle(color = ink)) { append(word.word) }
                            }
                            adjustedPosMs >= word.startMs -> {
                                val liftedColor = lerpColor(accentColor, ink, progress * 0.5f)
                                withStyle(SpanStyle(color = liftedColor)) { append(word.word) }
                            }
                            else -> {
                                withStyle(SpanStyle(color = ink.copy(alpha = 0.42f))) { append(word.word) }
                            }
                        }
                    }
                }
                Text(text = styledLine, style = Musico.type.lyricCinema, textAlign = TextAlign.Start)
            } else if (isActive && !selectingForShare) {
                val lineStartMs = (line.timeSecs * 1000f).toLong()
                val lineSpan = (nextLineMs - lineStartMs).coerceAtLeast(600L)
                val fill = ((adjustedPosMs - lineStartMs).toFloat() / lineSpan).coerceIn(0f, 1f)
                Text(
                    text = line.text,
                    style = Musico.type.lyricCinema,
                    color = ink,
                    textAlign = TextAlign.Start,
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fill.coerceIn(0.02f, 1f))
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                }
            } else {
                Text(text = line.text, style = Musico.type.lyricCinema, color = ink, textAlign = TextAlign.Start)
            }

            if (showSubLines) {
                val sub = line.translation?.takeIf { it.isNotBlank() }
                    ?: line.romanized?.takeIf { it.isNotBlank() && !it.equals(line.text, ignoreCase = true) }
                if (sub != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = sub,
                        style = Musico.type.lyricSub,
                        color = if (isActive) accentColor else inkDim,
                    )
                }
            }
        }

        if (selectingForShare) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) accentColor else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun InstrumentalInterludeDots(
    progress: Float,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "interludePulse")
    val breath by pulse.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "interludeScale",
    )
    Row(
        modifier = modifier.padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val segStart = i / 3f
            val segFill = ((progress - segStart) * 3f).coerceIn(0f, 1f)
            val activeDot = segFill > 0f && segFill < 1f
            Box(
                Modifier
                    .size(10.dp)
                    .graphicsLayer {
                        val s = if (activeDot) breath else (0.85f + 0.15f * segFill)
                        scaleX = s
                        scaleY = s
                        alpha = 0.25f + 0.75f * segFill
                    }
                    .clip(CircleShape)
                    .background(if (segFill > 0.15f) accentColor else MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }
    }
}

private data class WordTiming(val word: String, val startMs: Long, val endMs: Long)

@Composable
private fun KineticLyricStage(
    lyrics: EnhancedLyrics,
    adjustedPosMs: Long,
    accentColor: Color,
    onSeekToTime: (Long) -> Unit,
    onTapCanvas: () -> Unit,
) {
    val flatWords = remember(lyrics) {
        if (lyrics.hasWordSync) {
            lyrics.lines
                .flatMap { it.words }
                .filter { it.word.isNotBlank() }
                .map { WordTiming(it.word, it.startMs, it.endMs) }
                .sortedBy { it.startMs }
        } else {
            emptyList()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTapCanvas,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (flatWords.isNotEmpty()) {
            KineticWordStage(flatWords, adjustedPosMs, accentColor, onSeekToTime)
        } else {
            KineticLineStage(lyrics, adjustedPosMs, onSeekToTime)
        }
    }
}

@Composable
private fun KineticWordStage(
    words: List<WordTiming>,
    positionMs: Long,
    accentColor: Color,
    onSeek: (Long) -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val currentIdx = remember(positionMs, words) { findWordAt(words, positionMs) }
    val prev = if (currentIdx > 0) words[currentIdx - 1] else null
    val current = currentIdx.takeIf { it >= 0 }?.let { words[it] }
    val next = if (currentIdx >= 0 && currentIdx + 1 < words.size) words[currentIdx + 1] else null
    val effectiveCurrent = current ?: words.firstOrNull()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .padding(bottom = 52.dp),
    ) {
        KineticNeighbor(text = prev?.word ?: " ", onClick = { prev?.let { hapticTick(); onSeek(it.startMs) } })
        Spacer(Modifier.height(22.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.height(128.dp)) {
            effectiveCurrent?.let { word ->
                val fillFraction = wordFill(word, positionMs)
                val wordColor = lerpColor(accentColor, ink, fillFraction)
                AnimatedContent(
                    targetState = word.startMs to word.word,
                    transitionSpec = {
                        (slideInVertically(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            initialOffsetY = { it / 2 },
                        ) + fadeIn(tween(130))) togetherWith (slideOutVertically(
                            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy),
                            targetOffsetY = { -it / 3 },
                        ) + fadeOut(tween(110)))
                    },
                    label = "kineticWord",
                    modifier = Modifier.clickable { hapticTick(); onSeek(word.startMs) },
                ) { (_, wText) ->
                    Text(
                        text = wText,
                        style = Musico.type.lyricCurrent,
                        color = wordColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        KineticNeighbor(text = next?.word ?: " ", onClick = { next?.let { hapticTick(); onSeek(it.startMs) } })
    }
}

@Composable
private fun KineticLineStage(
    lyrics: EnhancedLyrics,
    positionMs: Long,
    onSeek: (Long) -> Unit,
) {
    val currentLineIdx = findActiveLineIndex(lyrics, positionMs)
    val prev = currentLineIdx.let { if (it > 0) lyrics.lines[it - 1] else null }
    val current = currentLineIdx.takeIf { it >= 0 }?.let { lyrics.lines[it] } ?: lyrics.lines.firstOrNull()
    val next = currentLineIdx.let { if (it >= 0 && it + 1 < lyrics.lines.size) lyrics.lines[it + 1] else null }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 30.dp)
            .padding(bottom = 52.dp),
    ) {
        KineticNeighbor(
            text = prev?.text ?: " ",
            onClick = { prev?.let { hapticTick(); onSeek((it.timeSecs * 1000f).toLong()) } },
        )
        Spacer(Modifier.height(18.dp))
        Box(Modifier.height(156.dp), contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = current?.text ?: "♪",
                transitionSpec = {
                    (slideInVertically { it / 3 } + fadeIn()) togetherWith (slideOutVertically { -it / 3 } + fadeOut())
                },
                label = "kineticLine",
            ) { line ->
                Text(
                    text = line,
                    style = Musico.type.lyricCurrent.copy(
                        fontSize = 36.sp,
                        lineHeight = 44.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        KineticNeighbor(
            text = next?.text ?: " ",
            onClick = { next?.let { hapticTick(); onSeek((it.timeSecs * 1000f).toLong()) } },
        )
    }
}

@Composable
private fun KineticNeighbor(text: String, onClick: () -> Unit) {
    AnimatedContent(
        targetState = text,
        transitionSpec = {
            (slideInVertically { it / 4 } + fadeIn()) togetherWith (slideOutVertically { -it / 4 } + fadeOut())
        },
        label = "neighborWord",
        modifier = Modifier.clickable(enabled = text.isNotBlank(), onClick = onClick),
    ) { word ->
        Text(
            text = word,
            style = Musico.type.lyricNeighbor,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun findActiveLineIndex(lyrics: EnhancedLyrics, positionMs: Long): Int {
    val lines = lyrics.lines
    if (lines.isEmpty()) return -1
    var lo = 0
    var hi = lines.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if ((lines[mid].timeSecs * 1000f).toLong() <= positionMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return found
}

private fun wordFill(word: WordTiming, positionMs: Long): Float {
    val span = (word.endMs - word.startMs).coerceAtLeast(1L)
    return ((positionMs - word.startMs).toFloat() / span).coerceIn(0f, 1f)
}

private fun findWordAt(words: List<WordTiming>, positionMs: Long): Int {
    var lo = 0
    var hi = words.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (words[mid].startMs <= positionMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return found
}

private fun lerpColor(start: Color, end: Color, t: Float): Color {
    val f = t.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f,
    )
}

@Composable
private fun LyricsSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
    ) {
        SkeletonVerse(widthFraction = 0.62f, height = 22.dp, alpha = 0.12f)
        Spacer(Modifier.height(22.dp))
        SkeletonVerse(widthFraction = 0.88f, height = 30.dp, alpha = 0.22f)
        Spacer(Modifier.height(10.dp))
        SkeletonVerse(widthFraction = 0.68f, height = 30.dp, alpha = 0.18f)
        Spacer(Modifier.height(22.dp))
        SkeletonVerse(widthFraction = 0.74f, height = 22.dp, alpha = 0.12f)
        Spacer(Modifier.height(18.dp))
        SkeletonVerse(widthFraction = 0.52f, height = 22.dp, alpha = 0.09f)
    }
}

@Composable
private fun SkeletonVerse(widthFraction: Float, height: Dp, alpha: Float) {
    Box(
        Modifier
            .height(height)
            .fillMaxWidth(widthFraction)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
            .shimmer(),
    )
}

@Composable
private fun LyricsEmpty() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 36.dp)) {
            Text("♪", style = Musico.type.display.copy(fontSize = 48.sp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            Spacer(Modifier.height(12.dp))
            Text(
                "No lyrics available yet",
                style = Musico.type.title,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Instrumental or newly released track",
                style = Musico.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
