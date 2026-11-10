package io.github.aryan007h.musicoonline.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aryan007h.musicoonline.core.CoreException
import io.github.aryan007h.musicoonline.core.MusicoCore
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.ResolvedTrack
import io.github.aryan007h.musicoonline.core.SearchResultPage
import io.github.aryan007h.musicoonline.player.PlaybackController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Phase-1 state: search → tap → resolve (Rust core, cached file) →
 * ExoPlayer. Everything else (library, queue, playlists, lyrics) lands in
 * phase 2 (#111).
 */
class MusicViewModel(
    private val playback: PlaybackController,
) : ViewModel() {

    data class UiState(
        val query: String = "",
        val searching: Boolean = false,
        val error: String? = null,
        val results: List<OnlineTrack> = emptyList(),
        val continuation: String? = null,
        val resolvingId: String? = null,
        val nowPlaying: OnlineTrack? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val playerExpanded: Boolean = false,
        val coreVersion: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val json = Json { ignoreUnknownKeys = true }

    init {
        viewModelScope.launch {
            runCatching { MusicoCore.ping() }
                .onSuccess { d -> _state.update { it.copy(coreVersion = d["version"]?.toString()?.trim('"')) } }
                .onFailure { e -> _state.update { it.copy(error = "Core: ${e.message}") } }
        }
        viewModelScope.launch {
            playback.nowPlaying.collect { track -> _state.update { it.copy(nowPlaying = track) } }
        }
        viewModelScope.launch {
            playback.isPlaying.collect { playing -> _state.update { it.copy(isPlaying = playing) } }
        }
        viewModelScope.launch {
            playback.positionMs.collect { pos -> _state.update { it.copy(positionMs = pos) } }
        }
        viewModelScope.launch {
            playback.durationMs.collect { dur -> _state.update { it.copy(durationMs = dur) } }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(searching = true, error = null) }
            runCatching {
                val reply = MusicoCore.call(
                    "online.search",
                    buildJsonObject { put("query", query) },
                )
                json.decodeFromJsonElement(SearchResultPage.serializer(), reply)
            }.onFold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(searching = false, results = page.tracks, continuation = page.nextContinuation)
                    }
                },
                onFailure = { e ->
                    _state.update { it.copy(searching = false, error = e.userMessage()) }
                },
            )
        }
    }

    fun play(track: OnlineTrack) {
        if (_state.value.resolvingId != null) return
        // Tapping the current track toggles playback instead of re-resolving.
        if (_state.value.nowPlaying?.videoId == track.videoId) {
            onTogglePlayback()
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(resolvingId = track.videoId, error = null) }
            runCatching {
                val reply = MusicoCore.call(
                    "track.resolve",
                    buildJsonObject {
                        put("videoId", track.videoId)
                        put("title", track.title)
                        put("artist", track.artist)
                        put("album", track.album)
                        track.durationSecs?.let { put("durationSecs", it) }
                        track.thumbnail?.let { put("thumbnail", it) }
                        put("source", track.source)
                        track.artistBrowseId?.let { put("artistBrowseId", it) }
                    },
                )
                json.decodeFromJsonElement(ResolvedTrack.serializer(), reply)
            }.onFold(
                onSuccess = { resolved: ResolvedTrack ->
                    _state.update { it.copy(resolvingId = null, nowPlaying = track) }
                    playback.playFile(Uri.fromFile(java.io.File(resolved.path)), track)
                },
                onFailure = { e ->
                    _state.update { it.copy(resolvingId = null, error = e.userMessage()) }
                },
            )
        }
    }

    /** Non-suspend for Compose click handlers. */
    fun onTogglePlayback() {
        viewModelScope.launch { playback.togglePlayPause() }
    }

    fun seekTo(ms: Long) {
        viewModelScope.launch { playback.seekTo(ms) }
    }

    fun setPlayerExpanded(expanded: Boolean) {
        _state.update { it.copy(playerExpanded = expanded) }
    }

    private fun Throwable.userMessage(): String = (this as? CoreException)?.message ?: message ?: "unknown error"
}

/** Result-style helper — keep the call sites flat. */
private inline fun <T> Result<T>.onFold(
    onSuccess: (T) -> Unit,
    onFailure: (Throwable) -> Unit,
): Unit = fold(onSuccess, onFailure)
