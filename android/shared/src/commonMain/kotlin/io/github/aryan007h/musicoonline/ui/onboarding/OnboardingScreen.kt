package io.github.aryan007h.musicoonline.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.ui.components.Artwork
import io.github.aryan007h.musicoonline.ui.components.ExpressiveFilterChip
import io.github.aryan007h.musicoonline.ui.components.musicoTopInset
import io.github.aryan007h.musicoonline.ui.components.musicoWash
import io.github.aryan007h.musicoonline.ui.theme.ExpressiveShapes
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoMotion
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes
import io.github.aryan007h.musicoonline.ui.theme.breathing
import io.github.aryan007h.musicoonline.ui.theme.pressable

/**
 * First-launch onboarding: pick preferred languages, then 3 favorite artists.
 * Centered cleanly with a max width on desktop screens.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    artistResults: List<OnlineTrack>,
    artistSearching: Boolean,
    onFinish: (languages: List<String>, artists: List<String>) -> Unit,
    onDismiss: () -> Unit,
    onArtistQuery: (String) -> Unit,
    onImportPlaylist: () -> Unit = {},
) {
    var step by remember { mutableStateOf(0) }
    var selectedLangs by remember { mutableStateOf(setOf<String>()) }
    var pickedArtists by remember { mutableStateOf(listOf<OnlineTrack>()) }
    var query by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .align(Alignment.TopCenter)
                .musicoWash(
                    primary = Musico.extras.primary,
                    secondary = Musico.extras.support,
                    glow = Musico.extras.mint,
                    intensity = 0.24f,
                ),
        )
        Column(
            Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .musicoTopInset()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(2) { i ->
                    val dotWidth by animateDpAsState(
                        targetValue = if (step == i) 20.dp else 8.dp,
                        animationSpec = MusicoMotion.expressive(),
                        label = "onboardDot$i",
                    )
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .size(width = dotWidth.coerceAtLeast(0.dp), height = 8.dp)
                            .clip(CircleShape)
                            .background(if (step == i) MaterialTheme.colorScheme.primary else Musico.extras.inkFaint),
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Skip") }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(MusicoMotion.spatial()) { if (forward) it / 3 else -it / 3 } +
                        fadeIn(tween(MusicoMotion.MED))) togetherWith
                        (slideOutHorizontally(
                            tween(MusicoMotion.MED, easing = MusicoMotion.accelerate),
                        ) { if (forward) -it / 4 else it / 4 } +
                            fadeOut(tween(MusicoMotion.FAST)))
                },
                label = "onboardStep",
                modifier = Modifier.weight(1f),
            ) { s ->
                when (s) {
                    0 -> Column(Modifier.padding(horizontal = 24.dp)) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(ExpressiveShapes.Cookie8)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
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
                                ExpressiveFilterChip(
                                    label = lang,
                                    selected = lang in selectedLangs,
                                    onClick = {
                                        selectedLangs = if (lang in selectedLangs) selectedLangs - lang else selectedLangs + lang
                                    },
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
                        if (pickedArtists.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
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
                            itemsIndexed(artistResults, key = { idx, t -> "oa-${t.videoId}-$idx" }) { _, track ->
                                val already = pickedArtists.any { it.videoId == track.videoId }
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .pressable(
                                            onClick = {
                                                if (!already && pickedArtists.size < 6) {
                                                    pickedArtists = pickedArtists + track
                                                    query = ""
                                                    onArtistQuery("")
                                                }
                                            },
                                            pressedScale = 0.97f,
                                        )
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

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val enabled = if (step == 0) true else pickedArtists.size >= 3
                Surface(
                    shape = MusicoShapes.chip,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .then(if (enabled) Modifier.breathing(max = 1.015f) else Modifier)
                        .pressable(
                            onClick = {
                                if (step == 0) step = 1
                                else onFinish(selectedLangs.toList(), pickedArtists.map { it.artist.ifEmpty { it.title } })
                            },
                            enabled = enabled,
                            pressedScale = 0.97f,
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            if (step == 0) "Continue" else "Make it mine",
                            fontWeight = FontWeight.Bold,
                            color = if (enabled) MaterialTheme.colorScheme.onPrimary else Musico.extras.inkFaint,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                TextButton(onClick = onImportPlaylist) {
                    Text(
                        "Import a playlist",
                        style = Musico.type.caption,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
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
