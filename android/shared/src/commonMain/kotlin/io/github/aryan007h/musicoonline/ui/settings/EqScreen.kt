package io.github.aryan007h.musicoonline.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.aryan007h.musicoonline.platform.PlatformEqualizer
import io.github.aryan007h.musicoonline.ui.components.DesktopScrollableChipRow
import io.github.aryan007h.musicoonline.ui.components.ExpressiveFilterChip
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSegmentedColumn
import io.github.aryan007h.musicoonline.ui.components.ExpressiveSegmentedItem
import io.github.aryan007h.musicoonline.ui.components.musicoTopInset
import io.github.aryan007h.musicoonline.ui.components.musicoWash
import io.github.aryan007h.musicoonline.ui.theme.Musico

/**
 * Cross-platform Material 3 Expressive Equalizer UI backed by [PlatformEqualizer]:
 * - Android: Android AudioFX Equalizer + BassBoost bound to Media3 audio session
 * - Desktop: Live 10-band Rust DSP (`musico_playback::eq`) over JNI
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

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
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
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Column {
                    Text("Equalizer", style = Musico.type.display, color = MaterialTheme.colorScheme.onBackground)
                    Text(
                        "${eq.bandCount} bands · ${eq.bandFreqLabels.firstOrNull() ?: ""} – ${eq.bandFreqLabels.lastOrNull() ?: ""}",
                        style = Musico.type.caption,
                        color = Musico.extras.inkDim,
                    )
                }
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = eq.enabled,
                    onCheckedChange = { PlatformEqualizer.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.padding(end = 12.dp),
                )
            }

            if (!bound) {
                Text(
                    "Controls go live the moment you play a song",
                    style = Musico.type.caption,
                    color = Musico.extras.inkDim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 40.dp),
            ) {
                Text(
                    "Presets",
                    style = Musico.type.section,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 10.dp),
                )
                DesktopScrollableChipRow(
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    edgeFadeColor = MaterialTheme.colorScheme.background,
                ) {
                    EqPresets.forEach { preset ->
                        ExpressiveFilterChip(
                            label = preset.name,
                            selected = eq.presetName == preset.name,
                            onClick = { PlatformEqualizer.applyPresetGain(preset.curve, preset.name) },
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                ExpressiveSegmentedColumn(
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    ExpressiveSegmentedItem(index = 0, count = 2, containerColor = cardBg) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 22.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            eq.bandLevels.forEachIndexed { i, level ->
                                BandSlider(
                                    value = level,
                                    range = eq.bandRange,
                                    label = eq.bandFreqLabels.getOrElse(i) { "" },
                                    enabled = bound && eq.enabled,
                                    onValueChange = { PlatformEqualizer.setBandLevel(i, it) },
                                )
                            }
                        }
                    }

                    ExpressiveSegmentedItem(index = 1, count = 2, containerColor = cardBg) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Bass boost",
                                    style = Musico.type.title,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${(eq.bassBoost * 100) / 1000}%",
                                    style = Musico.type.tabularCaption,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Slider(
                                value = eq.bassBoost.toFloat(),
                                onValueChange = { PlatformEqualizer.setBassBoost(it.toInt()) },
                                valueRange = 0f..1000f,
                                enabled = bound && eq.enabled,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class EqPreset(val name: String, val curve: List<Int>)

private val EqPresets = listOf(
    EqPreset("Flat", listOf(0, 0, 0, 0, 0)),
    EqPreset("Bass", listOf(600, 350, 0, 0, 0)),
    EqPreset("Vocal", listOf(-250, 0, 300, 250, 100)),
    EqPreset("Rock", listOf(400, 200, -150, 200, 350)),
    EqPreset("Electronic", listOf(500, 150, 0, 200, 400)),
    EqPreset("Warm", listOf(300, 150, 50, 0, 150)),
)

@Composable
private fun BandSlider(
    value: Int,
    range: IntRange,
    label: String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(46.dp)
                .height(170.dp),
            contentAlignment = Alignment.Center,
        ) {
            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toInt()) },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
                modifier = Modifier
                    .graphicsLayer {
                        rotationZ = 270f
                        transformOrigin = TransformOrigin(0.5f, 0.5f)
                    }
                    .width(170.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = Musico.type.caption,
            color = Musico.extras.inkDim,
        )
        Text(
            "${value / 100}",
            style = Musico.type.tabularCaption,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.primary else Musico.extras.inkFaint,
        )
    }
}
