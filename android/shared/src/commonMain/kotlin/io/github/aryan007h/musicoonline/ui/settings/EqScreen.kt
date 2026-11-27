package io.github.aryan007h.musicoonline.ui.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.platform.PlatformEqualizer
import io.github.aryan007h.musicoonline.ui.components.DesktopScrollableChipRow
import io.github.aryan007h.musicoonline.ui.components.ExpressiveFilterChip
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSegmentedColumn
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSegmentedItem
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSlider
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSwitch
import io.github.aryan007h.musicoonline.ui.components.musicoTopInset
import io.github.aryan007h.musicoonline.ui.components.musicoWash
import io.github.aryan007h.musicoonline.ui.theme.ExpressiveShapes
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.hapticThud
import io.github.aryan007h.musicoonline.ui.theme.hapticTick
import io.github.aryan007h.musicoonline.ui.theme.pressable
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure math & preset definitions for the Material 3 Expressive Equalizer Studio,
 * unit-tested in `SharedCoreTests`.
 */
object EqCurveMath {
    data class EqPreset(val name: String, val curve: List<Int>)

    val PRESETS: List<EqPreset> = listOf(
        EqPreset("Flat", listOf(0, 0, 0, 0, 0)),
        EqPreset("Bass", listOf(650, 400, 50, 0, 50)),
        EqPreset("Vocal", listOf(-250, -50, 350, 300, 150)),
        EqPreset("Rock", listOf(450, 200, -150, 250, 400)),
        EqPreset("Electronic", listOf(550, 200, -50, 250, 450)),
        EqPreset("Warm", listOf(350, 200, 50, -50, -150)),
        EqPreset("Acoustic", listOf(200, 150, 100, 250, 200)),
        EqPreset("Late Night", listOf(250, 100, 50, -100, -250)),
    )

    fun levelToNormalized(levelMb: Int, range: IntRange): Float {
        val span = (range.last - range.first).toFloat()
        if (span <= 0f) return 0.5f
        return ((levelMb - range.first) / span).coerceIn(0f, 1f)
    }

    fun levelToBipolar(levelMb: Int, range: IntRange): Float {
        val maxAbs = maxOf(abs(range.first), abs(range.last)).coerceAtLeast(1)
        return (levelMb.toFloat() / maxAbs).coerceIn(-1f, 1f)
    }

    /** Maps a vertical fraction (0f = bottom/min, 1f = top/max) into snapped 0.5 dB (50 mB) steps. */
    fun fractionToMillibels(fractionFromBottom: Float, range: IntRange, stepMb: Int = 50): Int {
        val clamped = if (fractionFromBottom.isNaN()) 0.5f else fractionFromBottom.coerceIn(0f, 1f)
        val raw = range.first + (range.last - range.first) * clamped
        val step = stepMb.coerceAtLeast(1)
        val snapped = (raw / step).roundToInt() * step
        return snapped.coerceIn(range.first, range.last)
    }

    fun formatDb(levelMb: Int): String {
        val tenths = (levelMb / 100.0 * 10.0).roundToInt()
        val sign = when {
            tenths > 0 -> "+"
            tenths < 0 -> "-"
            else -> ""
        }
        val absTenths = abs(tenths)
        val whole = absTenths / 10
        val frac = absTenths % 10
        return if (frac == 0) "$sign$whole" else "$sign$whole.$frac"
    }
}

/**
 * Production-grade Material 3 Expressive Equalizer Studio backed by [PlatformEqualizer]:
 * - Interactive live cubic-Bezier Frequency-Response Curve visualizer with `0 dB` reference line
 * - Custom vertical M3E capsule faders with bipolar center-out fill, crisp horizontal pill handles,
 *   drag sculpting, and double-tap to snap back to 0 dB (zero clipped rotated sliders!)
 * - 8 musical presets + Reset pill + thick M3E Bass Boost slider + M3E Check-thumb toggle
 */
@Composable
fun EqScreen(onBack: () -> Unit) {
    var eq by remember { mutableStateOf(PlatformEqualizer.currentState()) }
    var bound by remember { mutableStateOf(PlatformEqualizer.isAttached()) }
    val listener: () -> Unit = remember {
        {
            eq = PlatformEqualizer.currentState()
            bound = PlatformEqualizer.isAttached()
        }
    }

    DisposableEffect(Unit) {
        PlatformEqualizer.addListener(listener)
        eq = PlatformEqualizer.currentState()
        bound = PlatformEqualizer.isAttached()
        onDispose { PlatformEqualizer.removeListener(listener) }
    }

    val isDark = Musico.extras.isDark
    val cardBg = if (isDark) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLowest
    }
    val controlsEnabled = bound && eq.enabled

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.TopCenter)
                .musicoWash(
                    primary = Musico.extras.primary,
                    secondary = Musico.extras.support,
                    glow = Musico.extras.mint,
                    intensity = if (isDark) 0.20f else 0.14f,
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .musicoTopInset()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // ── M3E Studio Top Bar ───────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 940.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = if (!isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) else null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .pressable(onClick = { hapticTick(); onBack() }, pressedScale = 0.90f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        "Equalizer",
                        style = Musico.type.display,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "${eq.bandCount} bands · ${eq.bandFreqLabels.firstOrNull() ?: ""} – ${eq.bandFreqLabels.lastOrNull() ?: ""}" +
                            if (eq.presetName.isNotBlank()) " · ${eq.presetName}" else "",
                        style = Musico.type.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // Tonal Reset pill button
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .pressable(
                            onClick = {
                                hapticThud()
                                PlatformEqualizer.applyPresetGain(listOf(0, 0, 0, 0, 0), "Flat")
                                PlatformEqualizer.setBassBoost(0)
                            },
                            pressedScale = 0.94f,
                        ),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Reset equalizer",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            "Reset",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                ExpressiveSwitch(
                    checked = eq.enabled,
                    onCheckedChange = { PlatformEqualizer.setEnabled(it) },
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .widthIn(max = 940.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 120.dp),
            ) {
                if (!bound) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(ExpressiveShapes.Cookie8)
                                    .background(MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Filled.GraphicEq,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Text(
                                "Start playing any track to activate live hardware & DSP sculpting",
                                style = Musico.type.caption,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }

                // ── Presets Strip ────────────────────────────────────────
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Sound profiles",
                        style = Musico.type.section,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (eq.presetName.equals("Custom", ignoreCase = true)) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                "Custom curve",
                                style = Musico.type.caption,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                DesktopScrollableChipRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    edgeFadeColor = MaterialTheme.colorScheme.background,
                ) {
                    EqCurveMath.PRESETS.forEach { preset ->
                        ExpressiveFilterChip(
                            label = preset.name,
                            selected = eq.presetName.equals(preset.name, ignoreCase = true),
                            onClick = { PlatformEqualizer.applyPresetGain(preset.curve, preset.name) },
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // ── Main Segmented Studio Cards ──────────────────────────
                ExpressiveSegmentedColumn(
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    // Card 0: Live Frequency-Response Curve + Custom Vertical M3E Faders
                    ExpressiveSegmentedItem(index = 0, count = 2, containerColor = cardBg) {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 18.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Filled.Equalizer,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                    Column {
                                        Text(
                                            "Frequency curve",
                                            style = Musico.type.title,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            "${eq.bandRange.first / 100} dB to +${eq.bandRange.last / 100} dB · Double-tap a band to zero",
                                            style = Musico.type.caption,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (controlsEnabled) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                ) {
                                    Text(
                                        if (controlsEnabled) "LIVE" else if (!eq.enabled) "BYPASSED" else "STANDBY",
                                        style = Musico.type.overline,
                                        fontSize = 10.sp,
                                        color = if (controlsEnabled) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))

                            // Interactive Smooth Cubic-Bezier Frequency Curve Canvas
                            EqResponseCurveVisualizer(
                                bandLevels = eq.bandLevels,
                                range = eq.bandRange,
                                enabled = controlsEnabled,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(124.dp),
                            )

                            Spacer(Modifier.height(20.dp))

                            // Native Custom Vertical M3E Faders (never clips on 5-band Android or 10-band Desktop)
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                val minColWidth = 58.dp
                                val neededWidth = minColWidth * eq.bandLevels.size.coerceAtLeast(1)
                                val scrollable = neededWidth > maxWidth
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(if (scrollable) Modifier.horizontalScroll(rememberScrollState()) else Modifier),
                                    horizontalArrangement = if (scrollable) {
                                        Arrangement.spacedBy(8.dp)
                                    } else {
                                        Arrangement.SpaceEvenly
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    eq.bandLevels.forEachIndexed { index, level ->
                                        ExpressiveVerticalFader(
                                            valueMb = level,
                                            range = eq.bandRange,
                                            freqLabel = eq.bandFreqLabels.getOrElse(index) { "B${index + 1}" },
                                            enabled = controlsEnabled,
                                            onValueChange = { next ->
                                                PlatformEqualizer.setBandLevel(index, next)
                                            },
                                            modifier = if (scrollable) {
                                                Modifier.width(56.dp)
                                            } else {
                                                Modifier.weight(1f)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Card 1: Thick M3E Bass Boost
                    ExpressiveSegmentedItem(index = 1, count = 2, containerColor = cardBg) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Filled.Tune,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "Bass boost",
                                        style = Musico.type.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        "Sub-bass warmth and low-end punch",
                                        style = Musico.type.caption,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = if (eq.bassBoost > 0 && controlsEnabled) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                    },
                                ) {
                                    Text(
                                        "${(eq.bassBoost * 100) / 1000}%",
                                        style = Musico.type.tabularCaption,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (eq.bassBoost > 0 && controlsEnabled) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            ExpressiveSlider(
                                value = eq.bassBoost.toFloat(),
                                onValueChange = { PlatformEqualizer.setBassBoost(it.roundToInt()) },
                                valueRange = 0f..1000f,
                                enabled = controlsEnabled,
                                trackHeight = 14.dp,
                                thumbHeight = 30.dp,
                                barHeight = 38.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Smooth cubic-Bezier Frequency-Response Curve visualizer with 0 dB center reference line,
 * tonal gradient fill, and spring-animated band nodes.
 */
@Composable
private fun EqResponseCurveVisualizer(
    bandLevels: List<Int>,
    range: IntRange,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val primary = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val surfaceBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
    val zeroLineColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    val nodeFill = MaterialTheme.colorScheme.surface

    val animatedNorms = bandLevels.mapIndexed { idx, level ->
        animateFloatAsState(
            targetValue = EqCurveMath.levelToNormalized(level, range),
            animationSpec = MusicoMotion.spatialFast(),
            label = "eqCurveNode$idx",
        )
    }
    val linePath = remember { Path() }
    val fillPath = remember { Path() }
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = surfaceBg,
        modifier = modifier,
    ) {
        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                val zeroNorm = EqCurveMath.levelToNormalized(0, range)
                val zeroY = h * (1f - zeroNorm)

                // Top / bottom subtle grid lines
                drawLine(
                    color = gridColor,
                    start = Offset(0f, h * 0.12f),
                    end = Offset(w, h * 0.12f),
                    strokeWidth = 1.dp.toPx(),
                )
                drawLine(
                    color = gridColor,
                    start = Offset(0f, h * 0.88f),
                    end = Offset(w, h * 0.88f),
                    strokeWidth = 1.dp.toPx(),
                )
                // 0 dB center dashed reference line
                drawLine(
                    color = zeroLineColor,
                    start = Offset(0f, zeroY),
                    end = Offset(w, zeroY),
                    strokeWidth = 1.3.dp.toPx(),
                    pathEffect = dashEffect,
                )

                val count = animatedNorms.size
                if (count == 0) return@Canvas

                val padY = 8.dp.toPx()
                val usableH = (h - padY * 2f).coerceAtLeast(1f)
                val pts = List(count) { i ->
                    val x = if (count == 1) w / 2f else (i.toFloat() / (count - 1)) * w
                    val y = padY + (1f - animatedNorms[i].value) * usableH
                    Offset(x, y)
                }

                linePath.reset()
                fillPath.reset()
                linePath.moveTo(pts.first().x, pts.first().y)
                fillPath.moveTo(pts.first().x, zeroY)
                fillPath.lineTo(pts.first().x, pts.first().y)

                for (i in 0 until pts.size - 1) {
                    val p0 = pts[i]
                    val p1 = pts[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    linePath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                    fillPath.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                }
                fillPath.lineTo(pts.last().x, zeroY)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.30f),
                            primary.copy(alpha = 0.06f),
                            primary.copy(alpha = 0.24f),
                        ),
                    ),
                )
                drawPath(
                    path = linePath,
                    color = primary,
                    style = Stroke(
                        width = 3.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )

                val outerR = 5.5.dp.toPx()
                val innerR = 2.6.dp.toPx()
                pts.forEach { pt ->
                    drawCircle(color = primary, radius = outerR, center = pt)
                    drawCircle(color = nodeFill, radius = innerR, center = pt)
                }
            }

            // Crisp corner dB scale labels
            Text(
                "+${range.last / 100} dB",
                style = Musico.type.tabularCaption,
                fontSize = 9.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 6.dp, end = 12.dp),
            )
            Text(
                "0 dB",
                style = Musico.type.tabularCaption,
                fontSize = 9.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
            )
            Text(
                "${range.first / 100} dB",
                style = Musico.type.tabularCaption,
                fontSize = 9.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 6.dp, end = 12.dp),
            )
        }
    }
}

/**
 * Native Material 3 Expressive Vertical EQ Band Fader:
 * - Thick `20.dp` vertical rounded capsule track (zero rotated-slider clipping!)
 * - Bipolar center-out tonal fill expanding up (boost) or down (cut) from `0 dB`
 * - Tactile horizontal pill thumb handle that widens smoothly while dragging
 * - Tap anywhere to jump, drag smoothly to sculpt, double-tap to reset band to `0 dB`
 */
@Composable
private fun ExpressiveVerticalFader(
    valueMb: Int,
    range: IntRange,
    freqLabel: String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestRange by rememberUpdatedState(range)

    val norm = EqCurveMath.levelToNormalized(valueMb, range)
    val zeroNorm = EqCurveMath.levelToNormalized(0, range)

    val animatedNorm by animateFloatAsState(
        targetValue = norm,
        animationSpec = if (dragging) MusicoMotion.effectsFast() else MusicoMotion.spatialFast(),
        label = "eqBandNorm",
    )
    val thumbWidthScale by animateFloatAsState(
        targetValue = if (dragging) 1.16f else 1f,
        animationSpec = MusicoMotion.spatialFast(),
        label = "eqThumbScale",
    )

    val activeColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    }
    val trackBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val zeroTickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val thumbHighlight = MaterialTheme.colorScheme.onPrimary

    val dbText = EqCurveMath.formatDb(valueMb)
    val isNonZero = abs(valueMb) >= 50

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top dB readout pill
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (isNonZero && enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
            Text(
                text = dbText,
                style = Musico.type.tabularCaption,
                fontSize = 11.sp,
                fontWeight = if (isNonZero) FontWeight.ExtraBold else FontWeight.SemiBold,
                color = if (isNonZero && enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(min = 42.dp)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        // Vertical interactive M3E capsule track
        Canvas(
            modifier = Modifier
                .width(46.dp)
                .height(190.dp)
                .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            hapticThud()
                            latestOnChange(0.coerceIn(latestRange.first, latestRange.last))
                        },
                        onTap = { offset ->
                            val frac = 1f - (offset.y / size.height.coerceAtLeast(1))
                            hapticTick()
                            latestOnChange(EqCurveMath.fractionToMillibels(frac, latestRange))
                        },
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            dragging = true
                            val frac = 1f - (offset.y / size.height.coerceAtLeast(1))
                            latestOnChange(EqCurveMath.fractionToMillibels(frac, latestRange))
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val frac = 1f - (change.position.y / size.height.coerceAtLeast(1))
                            latestOnChange(EqCurveMath.fractionToMillibels(frac, latestRange))
                        },
                        onDragEnd = {
                            dragging = false
                            hapticTick()
                        },
                        onDragCancel = {
                            dragging = false
                        },
                    )
                },
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val padY = 12.dp.toPx()
            val usableH = (h - padY * 2f).coerceAtLeast(1f)
            val trackW = 18.dp.toPx()
            val trackCorner = CornerRadius(trackW / 2f, trackW / 2f)

            val zeroY = padY + (1f - zeroNorm) * usableH
            val thumbY = padY + (1f - animatedNorm) * usableH

            // Full capsule background track
            drawRoundRect(
                color = trackBg,
                topLeft = Offset(cx - trackW / 2f, padY),
                size = Size(trackW, usableH),
                cornerRadius = trackCorner,
            )

            // Subtle 0 dB center notch across track
            drawLine(
                color = zeroTickColor,
                start = Offset(cx - trackW / 2f - 4.dp.toPx(), zeroY),
                end = Offset(cx + trackW / 2f + 4.dp.toPx(), zeroY),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Bipolar active tonal fill expanding from 0 dB toward thumbY
            val fillTop = minOf(zeroY, thumbY)
            val fillBottom = maxOf(zeroY, thumbY)
            val fillH = (fillBottom - fillTop).coerceAtLeast(0f)
            if (fillH > 1.5f) {
                drawRoundRect(
                    color = activeColor.copy(alpha = 0.88f),
                    topLeft = Offset(cx - trackW / 2f, fillTop),
                    size = Size(trackW, fillH),
                    cornerRadius = trackCorner,
                )
            }

            // Crisp M3E horizontal pill handle thumb
            val handleW = 32.dp.toPx() * thumbWidthScale
            val handleH = (if (dragging) 11.dp else 9.dp).toPx()
            drawRoundRect(
                color = activeColor,
                topLeft = Offset(cx - handleW / 2f, thumbY - handleH / 2f),
                size = Size(handleW, handleH),
                cornerRadius = CornerRadius(handleH / 2f, handleH / 2f),
            )
            // Tactile center grip line inside thumb
            drawLine(
                color = thumbHighlight.copy(alpha = 0.85f),
                start = Offset(cx - handleW * 0.22f, thumbY),
                end = Offset(cx + handleW * 0.22f, thumbY),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        Spacer(Modifier.height(10.dp))

        Text(
            text = freqLabel,
            style = Musico.type.caption,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
