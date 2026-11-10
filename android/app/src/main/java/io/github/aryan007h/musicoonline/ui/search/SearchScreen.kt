package io.github.aryan007h.musicoonline.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.ui.components.EmptyState
import io.github.aryan007h.musicoonline.ui.components.TrackRow
import io.github.aryan007h.musicoonline.ui.theme.Musico
import io.github.aryan007h.musicoonline.ui.theme.MusicoShapes

/**
 * The Search tab — opens only from the pill's search icon, keyboard up.
 * Suggestions as type-ahead chips, recents when empty, numbered results.
 */
@Composable
fun SearchScreen(
    query: String,
    submittedQuery: String,
    searching: Boolean,
    results: List<OnlineTrack>,
    suggestions: List<String>,
    recentSearches: List<String>,
    currentTrackId: String?,
    isPlaying: Boolean,
    resolvingId: String?,
    likedIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onSuggestionClick: (String) -> Unit,
    onClearRecents: () -> Unit,
    onOpenTrack: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onLikeToggle: (OnlineTrack) -> Unit,
    onTrackLongClick: (OnlineTrack) -> Unit = {},
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(Modifier.fillMaxSize()) {
        SearchField(
            query = query,
            searching = searching,
            onQueryChange = onQueryChange,
            onSubmit = onSubmit,
            focusRequester = focusRequester,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val typingSuggestions = query.isNotBlank() && suggestions.isNotEmpty() && query != submittedQuery

            if (query.isBlank()) {
                if (recentSearches.isNotEmpty()) {
                    item(key = "recents-header") {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Recent searches",
                                style = Musico.type.section,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = onClearRecents) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Clear recent searches",
                                    tint = Musico.extras.inkFaint,
                                )
                            }
                        }
                    }
                    items(recentSearches.size, key = { "recent-$it" }) { i ->
                        SuggestionRow(
                            icon = Icons.Filled.History,
                            text = recentSearches[i],
                            onClick = { onSubmit(recentSearches[i]) },
                        )
                    }
                } else {
                    item(key = "empty-search") {
                        EmptyState(
                            icon = Icons.Filled.Search,
                            title = "Search the catalog",
                            subtitle = "Songs, albums, artists — straight from the core's YouTube Music engine",
                        )
                    }
                }
            } else if (typingSuggestions) {
                item(key = "suggestions-header") {
                    Text(
                        "Suggestions",
                        style = Musico.type.section,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp, start = 12.dp, bottom = 4.dp),
                    )
                }
                items(suggestions.size, key = { "sugg-$it" }) { i ->
                    SuggestionRow(
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        text = suggestions[i],
                        onClick = { onSuggestionClick(suggestions[i]) },
                    )
                }
            } else if (searching && results.isEmpty()) {
                item(key = "loading") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (results.isNotEmpty()) {
                item(key = "results-header") {
                    Text(
                        "${results.size} result${if (results.size == 1) "" else "s"}",
                        style = Musico.type.section,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 8.dp, start = 12.dp, bottom = 4.dp),
                    )
                }
                items(results.size, key = { "res-${results[it].videoId}" }) { i ->
                    val track = results[i]
                    TrackRow(
                        track = track,
                        index = i + 1,
                        isCurrent = track.videoId == currentTrackId,
                        isPlaying = isPlaying,
                        isLiked = track.videoId in likedIds,
                        resolving = resolvingId == track.videoId,
                        onClick = { onOpenTrack(track, results) },
                        onLikeToggle = { onLikeToggle(track) },
                        onLongClick = { onTrackLongClick(track) },
                    )
                }
            } else if (!searching && query.isNotBlank()) {
                item(key = "no-results") {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = "No matches",
                        subtitle = "Try fewer words, or check the spelling",
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    focusRequester: FocusRequester,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text("Songs, artists, albums…", color = Musico.extras.inkFaint)
        },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Musico.extras.inkDim)
        },
        trailingIcon = {
            when {
                searching -> CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
                query.isNotEmpty() -> IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = Musico.extras.inkDim)
                }
            }
        },
        singleLine = true,
        shape = MusicoShapes.chip,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit(query) }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .focusRequester(focusRequester),
    )
}

@Composable
private fun SuggestionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Musico.extras.inkDim, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = Musico.type.body, color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Medium)
    }
}
