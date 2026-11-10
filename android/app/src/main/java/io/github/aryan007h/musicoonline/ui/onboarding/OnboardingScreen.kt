package io.github.aryan007h.musicoonline.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes

/**
 * First-launch onboarding: pick preferred languages, then at least 3
 * favorite artists — the home feed gets "Because you like …" radio shelves
 * and language pick shelves prepended. Skippable (the plain feed still
 * works), never nagging afterwards.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    artistResults: List<OnlineTrack>,
    artistSearching: Boolean,
    onFinish: (languages: List<String>, artists: List<String>) -> Unit,
    onDismiss: () -> Unit,
    onArtistQuery: (String) -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    var selectedLangs by remember { mutableStateOf(setOf<String>()) }
    var pickedArtists by remember { mutableStateOf(listOf<OnlineTrack>()) }
    var query by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Progress dots + skip
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(2) { i ->
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(width = if (step == i) 20.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (step == i) MaterialTheme.colorScheme.primary else Musico.extras.inkFaint),
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("Skip") }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
            label = "onboardStep",
            modifier = Modifier.weight(1f),
        ) { s ->
            when (s) {
                0 -> Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        "What do you speak in music?",
                        style = Musico.type.display,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Pick languages — we'll surface their picks first",
                        style = Musico.type.body,
                        color = Musico.extras.inkDim,
                    )
                    Spacer(Modifier.height(20.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Languages.forEach { lang ->
                            FilterChip(
                                selected = lang in selectedLangs,
                                onClick = {
                                    selectedLangs = if (lang in selectedLangs) selectedLangs - lang else selectedLangs + lang
                                },
                                label = { Text(lang) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                ),
                            )
                        }
                    }
                }

                else -> Column(Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        "Three artists you love",
                        style = Musico.type.display,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${pickedArtists.size}/3 picked — these seed your radio shelves",
                        style = Musico.type.body,
                        color = Musico.extras.inkDim,
                    )
                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it; onArtistQuery(it) },
                        placeholder = { Text("Search artists…", color = Musico.extras.inkFaint) },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Musico.extras.inkDim) },
                        trailingIcon = {
                            if (artistSearching) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        singleLine = true,
                        shape = MusicoShapes.chip,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onArtistQuery(query) }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    // Picked artists as avatar chips
                    if (pickedArtists.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pickedArtists.forEach { artist ->
                                Surface(
                                    shape = MusicoShapes.chip,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.clickable {
                                        pickedArtists = pickedArtists.filterNot { it.videoId == artist.videoId }
                                    },
                                ) {
                                    Text(
                                        artist.artist.ifEmpty { artist.title },
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(bottom = 20.dp),
                    ) {
                        items(artistResults, key = { it.videoId }) { track ->
                            val already = pickedArtists.any { it.videoId == track.videoId }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MusicoShapes.chip)
                                    .clickable {
                                        if (!already && pickedArtists.size < 6) {
                                            pickedArtists = pickedArtists + track
                                            query = ""
                                            onArtistQuery("")
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Artwork(
                                    url = track.thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    cornerRadius = 14.dp,
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, style = Musico.type.title, color = MaterialTheme.colorScheme.onBackground, maxLines = 1)
                                    Text(track.artist, style = Musico.type.caption, color = Musico.extras.inkDim, maxLines = 1)
                                }
                                if (already) {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Musico.extras.mint, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        // Bottom action
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            val enabled = if (step == 0) true else pickedArtists.size >= 3
            Surface(
                shape = MusicoShapes.chip,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(MusicoShapes.chip)
                    .clickable(enabled = enabled) {
                        if (step == 0) step = 1 else onFinish(selectedLangs.toList(), pickedArtists.map { it.artist.ifEmpty { it.title } })
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        if (step == 0) "Continue" else "Make it mine",
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = if (enabled) MaterialTheme.colorScheme.onPrimary else Musico.extras.inkFaint,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private val Languages = listOf(
    "Hindi", "English", "Punjabi", "Tamil", "Telugu", "Kannada", "Malayalam",
    "Bengali", "Marathi", "Bhojpuri", "Spanish", "Korean", "Arabic", "Japanese",
)
