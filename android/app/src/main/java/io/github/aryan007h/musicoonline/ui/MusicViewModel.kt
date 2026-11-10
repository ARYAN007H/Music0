package io.github.aryan007h.musicoonline.ui

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aryan007h.musicoonline.core.BrowsePage
import io.github.aryan007h.musicoonline.core.CoreException
import io.github.aryan007h.musicoonline.core.CoreSettings
import io.github.aryan007h.musicoonline.core.EnhancedLyrics
import io.github.aryan007h.musicoonline.core.MusicoCore
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.ResolvedTrack
import io.github.aryan007h.musicoonline.core.SearchResultPage
import io.github.aryan007h.musicoonline.core.ShelfItem
import io.github.aryan007h.musicoonline.core.SuggestionsReply
import io.github.aryan007h.musicoonline.player.PlaybackController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put

/** Local look-and-feel preferences (SharedPreferences, not the Rust core). */
enum class LyricBackdrop(val id: String, val label: String) {
    Black("black", "Midnight"),
    Rain("rain", "Rain"),
    Aurora("aurora", "Aurora"),
    Artwork("artwork", "Glow");

    companion object {
        fun fromId(id: String?): LyricBackdrop = entries.firstOrNull { it.id == id } ?: Black
    }
}

enum class AppTab { HOME, LIBRARY, SEARCH }

enum class AppOverlay { NONE, PLAYER, SETTINGS, PROFILE }

/**
 * App-wide state + every bridge call. One ViewModel for the shell: three
 * tabs (Home / Library / Search) under a profile menu, a mini player that
 * morphs into the full-screen player, and word-synced lyrics.
 */
class MusicViewModel(
    private val playback: PlaybackController,
) : ViewModel() {

    data class UiState(
        val tab: AppTab = AppTab.HOME,
        val overlay: AppOverlay = AppOverlay.NONE,
        val error: String? = null,
        val coreVersion: String? = null,
        val nowPlaying: OnlineTrack? = null,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val resolvingId: String? = null,
        // Queue
        val queue: List<OnlineTrack> = emptyList(),
        val queueIndex: Int = -1,
        val repeatAll: Boolean = false,
        // Lyrics
        val lyrics: EnhancedLyrics? = null,
        val lyricsLoading: Boolean = false,
        // Local prefs
        val lyricBackdrop: LyricBackdrop = LyricBackdrop.Black,
        val displayName: String = "Listener",
        // Likes / recents (on-device library)
        val likedIds: Set<String> = emptySet(),
        val recentlyPlayed: List<OnlineTrack> = emptyList(),
        // Search
        val searchQuery: String = "",
        val searchSubmitted: String = "",
        val searchResults: List<OnlineTrack> = emptyList(),
        val searchContinuation: String? = null,
        val searching: Boolean = false,
        val suggestions: List<String> = emptyList(),
        val recentSearches: List<String> = emptyList(),
        // Home
        val homeLoading: Boolean = false,
        val homeError: String? = null,
        val homeShelves: List<io.github.aryan007h.musicoonline.core.Shelf> = emptyList(),
        // Settings (core-backed)
        val coreSettingsOffline: Boolean = false,
        val coreSettingsAutoplay: Boolean = true,
        val coreSettingsStreamingQuality: String = "normal",
        val coreSettingsCacheMb: Int? = null,
        val settingsSaving: Boolean = false,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    private val json = Json { ignoreUnknownKeys = true }
    private val appContext: Context = playback.appContext

    // Queue bookkeeping (mirrors state.queue / state.queueIndex)
    private var queueTracks: List<OnlineTrack> = emptyList()
    private var queuePosition: Int = -1
    private val resolveMutex = Mutex()
    private var suggestJob: Job? = null

    private val prefs: SharedPreferences by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        viewModelScope.launch {
            runCatching { MusicoCore.ping() }
                .onSuccess { d -> _state.update { it.copy(coreVersion = d["version"]?.toString()?.trim('"')) } }
                .onFailure { e -> _state.update { it.copy(error = "Core: ${e.message}") } }
        }
        viewModelScope.launch { playback.nowPlaying.collect { t -> _state.update { it.copy(nowPlaying = t) } } }
        viewModelScope.launch { playback.isPlaying.collect { p -> _state.update { it.copy(isPlaying = p) } } }
        viewModelScope.launch { playback.isBuffering.collect { b -> _state.update { it.copy(isBuffering = b) } } }
        viewModelScope.launch { playback.positionMs.collect { p -> _state.update { it.copy(positionMs = p) } } }
        viewModelScope.launch { playback.durationMs.collect { d -> _state.update { it.copy(durationMs = d) } } }
        viewModelScope.launch { playback.trackEnded.collect { onTrackEnded() } }
        loadLocalPrefs()
        loadCoreSettings()
        loadHome()
    }

    // ── Local prefs (backdrop, name, likes, recents, searches) ───────────

    private fun loadLocalPrefs() {
        val liked = prefs.getStringSet(PREF_LIKED, emptySet())?.toSet() ?: emptySet()
        val recents = prefs.getString(PREF_RECENTS, null)?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(OnlineTrack.serializer()), raw) }.getOrNull()
        } ?: emptyList()
        val searches = prefs.getString(PREF_SEARCHES, null)?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }.getOrNull()
        } ?: emptyList()
        _state.update {
            it.copy(
                likedIds = liked,
                recentlyPlayed = recents,
                recentSearches = searches,
                lyricBackdrop = LyricBackdrop.fromId(prefs.getString(PREF_BACKDROP, null)),
                displayName = prefs.getString(PREF_NAME, null) ?: it.displayName,
            )
        }
    }

    fun setLyricBackdrop(backdrop: LyricBackdrop) {
        prefs.edit().putString(PREF_BACKDROP, backdrop.id).apply()
        _state.update { it.copy(lyricBackdrop = backdrop) }
    }

    fun setDisplayName(name: String) {
        prefs.edit().putString(PREF_NAME, name).apply()
        _state.update { it.copy(displayName = name) }
    }

    fun toggleLike(track: OnlineTrack) {
        val liked = _state.value.likedIds.toMutableSet()
        val nowLiked = track.videoId !in liked
        if (nowLiked) liked.add(track.videoId) else liked.remove(track.videoId)
        prefs.edit().putStringSet(PREF_LIKED, liked).apply()
        _state.update { it.copy(likedIds = liked) }
    }

    private fun pushRecentlyPlayed(track: OnlineTrack) {
        val rest = _state.value.recentlyPlayed.filterNot { it.videoId == track.videoId }
        val updated = (listOf(track) + rest).take(RECENTS_LIMIT)
        prefs.edit()
            .putString(PREF_RECENTS, json.encodeToString(ListSerializer(OnlineTrack.serializer()), updated))
            .apply()
        _state.update { it.copy(recentlyPlayed = updated) }
    }

    private fun pushRecentSearch(query: String) {
        val rest = _state.value.recentSearches.filterNot { it.equals(query, ignoreCase = true) }
        val updated = (listOf(query) + rest).take(SEARCHES_LIMIT)
        prefs.edit()
            .putString(PREF_SEARCHES, json.encodeToString(ListSerializer(String.serializer()), updated))
            .apply()
        _state.update { it.copy(recentSearches = updated) }
    }

    fun clearRecentSearches() {
        prefs.edit().putString(PREF_SEARCHES, "[]").apply()
        _state.update { it.copy(recentSearches = emptyList()) }
    }

    // ── Core settings ────────────────────────────────────────────────────

    private fun loadCoreSettings() {
        viewModelScope.launch {
            runCatching { MusicoCore.call("settings.get") }.onSuccess { d ->
                runCatching {
                    val s = json.decodeFromJsonElement(CoreSettings.serializer(), d)
                    _state.update {
                        it.copy(
                            coreSettingsOffline = s.offlineMode,
                            coreSettingsAutoplay = s.autoplay,
                            coreSettingsStreamingQuality = s.streamingQuality,
                            coreSettingsCacheMb = s.onlineCacheMb,
                        )
                    }
                }
            }
        }
    }

    /** Shallow-patches the persisted Rust settings with the given field. */
    fun saveCoreSetting(key: String, value: JsonElement) {
        viewModelScope.launch {
            _state.update { it.copy(settingsSaving = true) }
            runCatching {
                MusicoCore.call(
                    "settings.save",
                    buildJsonObject {
                        put("settings", buildJsonObject { put(key, value) })
                    },
                )
            }.onSuccess { _state.update { it.copy(settingsSaving = false) } }
                .onFailure { e -> _state.update { it.copy(settingsSaving = false, error = e.userMessage()) } }
            loadCoreSettings()
        }
    }

    // ── Navigation ───────────────────────────────────────────────────────

    fun selectTab(tab: AppTab) {
        _state.update { it.copy(tab = tab, overlay = AppOverlay.NONE) }
        if (tab == AppTab.HOME && _state.value.homeShelves.isEmpty() && !_state.value.homeLoading) {
            loadHome()
        }
    }

    fun openOverlay(overlay: AppOverlay) = _state.update { it.copy(overlay = overlay) }
    fun closeOverlay() = _state.update { it.copy(overlay = AppOverlay.NONE) }
    fun openPlayer() = _state.update { it.copy(overlay = AppOverlay.PLAYER) }

    // ── Home feed ────────────────────────────────────────────────────────

    fun loadHome() {
        if (_state.value.homeLoading) return
        viewModelScope.launch {
            _state.update { it.copy(homeLoading = true, homeError = null) }
            runCatching {
                val reply = MusicoCore.call("online.home")
                json.decodeFromJsonElement(BrowsePage.serializer(), reply)
            }.onFold(
                onSuccess = { page -> _state.update { it.copy(homeLoading = false, homeShelves = page.shelves) } },
                onFailure = { e -> _state.update { it.copy(homeLoading = false, homeError = e.userMessage()) } },
            )
        }
    }

    /** Opens a shelf item: playable → play in shelf context; browseable → surface via search (detail pages land in phase 3). */
    fun openShelfItem(item: ShelfItem, context: List<ShelfItem>) {
        val track = item.toTrackOrNull() ?: run {
            selectTab(AppTab.SEARCH)
            onQueryChange(item.title)
            search(item.title)
            return
        }
        val queue = context.mapNotNull { it.toTrackOrNull() }
        val index = queue.indexOfFirst { it.videoId == track.videoId }.takeIf { it >= 0 } ?: 0
        playQueue(queue.ifEmpty { listOf(track) }, index)
    }

    // ── Search ───────────────────────────────────────────────────────────

    fun onQueryChange(query: String) {
        _state.update { it.copy(searchQuery = query) }
        suggestJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300)
            runCatching {
                val reply = MusicoCore.call(
                    "online.suggestions",
                    buildJsonObject { put("query", query) },
                )
                json.decodeFromJsonElement(SuggestionsReply.serializer(), reply).suggestions
            }.onSuccess { s -> _state.update { it.copy(suggestions = s) } }
        }
    }

    fun search(query: String = _state.value.searchQuery.trim()) {
        if (query.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(searching = true, searchQuery = query, searchSubmitted = query, error = null) }
            runCatching {
                val reply = MusicoCore.call(
                    "online.search",
                    buildJsonObject { put("query", query) },
                )
                json.decodeFromJsonElement(SearchResultPage.serializer(), reply)
            }.onFold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(
                            searching = false,
                            searchResults = page.tracks,
                            searchContinuation = page.nextContinuation,
                            suggestions = emptyList(),
                        )
                    }
                    pushRecentSearch(query)
                },
                onFailure = { e ->
                    _state.update { it.copy(searching = false, error = e.userMessage()) }
                },
            )
        }
    }

    // ── Playback / queue ─────────────────────────────────────────────────

    /** Plays [queue] at [index] — the entry point for every play gesture. */
    fun playQueue(queue: List<OnlineTrack>, index: Int) {
        if (queue.isEmpty() || index !in queue.indices) return
        queueTracks = queue
        queuePosition = index
        _state.update { it.copy(queue = queue, queueIndex = index) }
        resolveAndPlay(queue[index])
    }

    fun playTrack(track: OnlineTrack, context: List<OnlineTrack> = listOf(track)) {
        val index = context.indexOfFirst { it.videoId == track.videoId }.takeIf { it >= 0 } ?: 0
        playQueue(context.ifEmpty { listOf(track) }, index)
    }

    /** Liked songs — resolved against every track the session has seen. */
    fun playLiked() {
        val known = linkedSetOf<OnlineTrack>()
        known.addAll(_state.value.recentlyPlayed)
        known.addAll(_state.value.searchResults)
        known.addAll(_state.value.queue)
        val likedTracks = known.filter { it.videoId in _state.value.likedIds }
        if (likedTracks.isNotEmpty()) {
            playQueue(likedTracks.toList(), 0)
        } else {
            _state.update { it.copy(error = "Like some songs first — tap the heart on any track") }
        }
    }

    fun next() = advance(1)

    fun previous() {
        // Apple-style: previous restarts the song when >3 s in.
        if (_state.value.positionMs > 3000) {
            viewModelScope.launch { playback.seekTo(0) }
        } else {
            advance(-1)
        }
    }

    private fun advance(delta: Int) {
        val queue = queueTracks
        if (queue.isEmpty()) return
        var target = queuePosition + delta
        if (target >= queue.size) {
            if (!_state.value.repeatAll) {
                viewModelScope.launch { playback.seekTo(0) }
                return
            }
            target = 0
        }
        if (target < 0) target = 0
        queuePosition = target
        _state.update { it.copy(queueIndex = target) }
        resolveAndPlay(queue[target])
    }

    private fun onTrackEnded() {
        if (!_state.value.coreSettingsAutoplay && queuePosition >= queueTracks.size - 1) {
            // Autoplay off + last track: stay put.
            return
        }
        advance(1)
    }

    fun toggleRepeat() = _state.update { it.copy(repeatAll = !it.repeatAll) }

    fun playFromQueue(index: Int) {
        if (index !in queueTracks.indices) return
        queuePosition = index
        _state.update { it.copy(queueIndex = index) }
        resolveAndPlay(queueTracks[index])
    }

    private fun resolveAndPlay(track: OnlineTrack) {
        viewModelScope.launch {
            val ok = resolveMutex.withLock {
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
                }.fold(
                    onSuccess = { resolved ->
                        _state.update { it.copy(resolvingId = null) }
                        playback.playFile(Uri.fromFile(java.io.File(resolved.path)), track)
                        pushRecentlyPlayed(track)
                        fetchLyrics(track)
                        true
                    },
                    onFailure = { e ->
                        _state.update { it.copy(resolvingId = null, error = e.userMessage()) }
                        false
                    },
                )
            }
            if (!ok && queueTracks.size > 1) {
                // Skip a dead resolve (removed video, region-lock) — never strand the queue.
                skipFailedTrack()
            }
        }
    }

    private fun skipFailedTrack() {
        val queue = queueTracks
        if (queue.size <= 1 || queuePosition !in queue.indices) return
        queueTracks = queue.filterIndexed { i, _ -> i != queuePosition }
        queuePosition = queuePosition.coerceAtMost(queueTracks.size - 1)
        _state.update { it.copy(queue = queueTracks, queueIndex = queuePosition) }
        resolveAndPlay(queueTracks[queuePosition])
    }

    fun onTogglePlayback() {
        viewModelScope.launch { playback.togglePlayPause() }
    }

    fun seekTo(ms: Long) {
        viewModelScope.launch { playback.seekTo(ms) }
    }

    fun toggleLikeCurrent() {
        _state.value.nowPlaying?.let { toggleLike(it) }
    }

    fun isLiked(track: OnlineTrack): Boolean = track.videoId in _state.value.likedIds

    // ── Lyrics ───────────────────────────────────────────────────────────

    private fun fetchLyrics(track: OnlineTrack) {
        viewModelScope.launch {
            _state.update { it.copy(lyricsLoading = true, lyrics = null) }
            runCatching {
                val reply = MusicoCore.call(
                    "lyrics.enhanced",
                    buildJsonObject {
                        put("title", track.title)
                        put("artist", track.artist)
                        put("trackId", track.videoId)
                        put("videoId", track.videoId)
                        track.durationSecs?.let { put("durationSecs", it) }
                    },
                )
                json.decodeFromJsonElement(EnhancedLyrics.serializer(), reply)
            }.onFold(
                onSuccess = { lyrics ->
                    // Ignore stale responses (track changed while fetching).
                    if (_state.value.nowPlaying?.videoId == track.videoId) {
                        _state.update {
                            it.copy(lyricsLoading = false, lyrics = lyrics.takeIf { l -> l.lines.isNotEmpty() })
                        }
                    }
                },
                onFailure = {
                    if (_state.value.nowPlaying?.videoId == track.videoId) {
                        _state.update { it.copy(lyricsLoading = false, lyrics = null) }
                    }
                },
            )
        }
    }

    fun refreshLyrics() {
        _state.value.nowPlaying?.let { fetchLyrics(it) }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    private fun Throwable.userMessage(): String = (this as? CoreException)?.message ?: message ?: "unknown error"

    companion object {
        private const val PREFS_NAME = "musico_ui"
        private const val PREF_LIKED = "liked_ids"
        private const val PREF_RECENTS = "recent_tracks"
        private const val PREF_SEARCHES = "recent_searches"
        private const val PREF_BACKDROP = "lyric_backdrop"
        private const val PREF_NAME = "display_name"
        private const val RECENTS_LIMIT = 40
        private const val SEARCHES_LIMIT = 8
    }
}

/** Result-style helper — keep the call sites flat. */
private inline fun <T> Result<T>.onFold(
    onSuccess: (T) -> Unit,
    onFailure: (Throwable) -> Unit,
): Unit = fold(onSuccess, onFailure)

private fun ShelfItem.toTrackOrNull(): OnlineTrack? = videoId?.let { videoId ->
    OnlineTrack(
        videoId = videoId,
        title = title,
        artist = subtitle,
        thumbnail = thumbnail,
        source = "",
    )
}
