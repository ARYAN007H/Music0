package io.github.aryan007h.musicoonline.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.player.MobileEqualizer
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes

/**
 * The equalizer. Real DSP: Android audiofx attached to the ExoPlayer audio
 * session (see [MobileEqualizer]) — bands, bass boost, gain-curve presets.
 */
@Composable
fun EqScreen(onBack: () -> Unit) {
    var eq by remember { mutableStateOf(MobileEqualizer.currentState().snapshot()) }
    var bound by remember { mutableStateOf(MobileEqualizer.isAttached()) }
    val listener: () -> Unit = {
        eq = MobileEqualizer.currentState().snapshot()
        bound = MobileEqualizer.isAttached()
    }

    androidx.compose.runtime.LaunchedEffect(Unit) { MobileEqualizer.addListener(listener) }
    DisposableEffect(Unit) {
        onDispose { MobileEqualizer.removeListener(listener) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .androidx_statusbar(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onBackground)
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
                onCheckedChange = { MobileEqualizer.setEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Musico.extras.mint,
                    checkedThumbColor = Musico.extras.onMint,
                ),
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        if (!bound) {
            Text(
                "Controls go live the moment you play a song",
                style = Musico.type.caption,
                color = Musico.extras.inkFaint,
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
            // ── Presets ──────────────────────────────────────────────────
            Text(
                "PRESETS",
                style = Musico.type.section,
                fontSize = 11.sp,
                color = Musico.extras.inkDim,
                modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 8.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EqPresets.forEach { preset ->
                    FilterChip(
                        selected = eq.presetName == preset.name,
                        onClick = { MobileEqualizer.applyPresetGain(preset.curve, preset.name) },
                        label = { Text(preset.name, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    )
                }
            }

            // ── Vertical band sliders ────────────────────────────────────
            Surface(
                shape = MusicoShapes.cardLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
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
                            onValueChange = { MobileEqualizer.setBandLevel(i, it) },
                        )
                    }
                }
            }

            // ── Bass boost ───────────────────────────────────────────────
            Surface(
                shape = MusicoShapes.cardLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Bass boost",
                            style = Musico.type.title,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(eq.bassBoost * 100) / 1000}%",
                            style = Musico.type.caption,
                            color = Musico.extras.mint,
                        )
                    }
                    Slider(
                        value = eq.bassBoost.toFloat(),
                        onValueChange = { MobileEqualizer.setBassBoost(it.toInt()) },
                        valueRange = 0f..1000f,
                        enabled = bound && eq.enabled,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = Musico.extras.mint,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }
        }
    }
}

private fun MobileEqualizer.EqState.snapshot(): MobileEqualizer.EqState = copy(bandLevels = bandLevels.clone())

/** Gain curves in millibels (1 dB = 100), tuned for 5-band audiofx chains. */
private data class EqPreset(val name: String, val curve: List<Int>)

private val EqPresets = listOf(
    EqPreset("Flat", listOf(0, 0, 0, 0, 0)),
    EqPreset("Bass", listOf(600, 350, 0, 0, 0)),
    EqPreset("Vocal", listOf(-250, 0, 300, 250, 100)),
    EqPreset("Rock", listOf(400, 200, -150, 200, 350)),
    EqPreset("Electronic", listOf(500, 150, 0, 200, 400)),
    EqPreset("Warm", listOf(300, 150, 50, 0, 150)),
)

/** A vertical slider via the rotation trick, with its Hz label under it. */
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
                    activeTrackColor = Musico.extras.mint,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
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
            fontSize = 10.sp,
            color = Musico.extras.inkDim,
        )
        Text(
            "${value / 100}",
            fontSize = 10.sp,
            color = if (enabled) Musico.extras.mint else Musico.extras.inkFaint,
        )
    }
}

private fun Modifier.androidx_statusbar(): Modifier =
    this.then(Modifier.statusBarsPadding())
