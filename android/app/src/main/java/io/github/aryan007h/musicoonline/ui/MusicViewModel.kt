package io.github.aryan007h.musicoonline.ui

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
    Black("black", "Dark"),
    Rain("rain", "Rain"),
    Aurora("aurora", "Aurora"),
    Artwork("artwork", "Glow");

    companion object {
        fun fromId(id: String?): LyricBackdrop = entries.firstOrNull { it.id == id } ?: Black
    }
}

enum class AppTab { HOME, RADIO, LIBRARY, SEARCH }

enum class AppOverlay { NONE, PLAYER, SETTINGS, PROFILE, DETAIL, EQ, ONBOARD }

/**
 * App-wide state + every bridge call. One ViewModel for the shell: three
 * tabs (Home / Library / Search) under a profile menu, a mini player that
 * morphs into the full-screen player, and word-synced lyrics.
 */
class MusicViewModel(
    private val playback: PlaybackController,
) : ViewModel() {

    /** One pushed detail page (album/playlist/artist/genre from a card tap). */
    data class DetailPageState(
        val browseId: String,
        val params: String?,
        val title: String,
        val page: BrowsePage? = null,
        val loading: Boolean = false,
        val error: String? = null,
        val saving: Boolean = false,
        val saved: Int = 0,
    )

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
        val lyricBackdrop: LyricBackdrop = LyricBackdrop.Artwork,
        val palette: io.github.aryan007h.musicoonline.ui.theme.Palette = io.github.aryan007h.musicoonline.ui.theme.Palette.NEBULA,
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
        // Endless browsing: real continuation paging, then a genre chain
        val homeContinuation: String? = null,
        val moreLoading: Boolean = false,
        val moreExhausted: Boolean = false,
        val styleItems: List<io.github.aryan007h.musicoonline.core.ShelfItem> = emptyList(),
        val styleQueue: List<io.github.aryan007h.musicoonline.core.ShelfItem> = emptyList(),
        val chartsAppended: Boolean = false,
        // Radio tab: the full mood/genre station grid
        val radioStyles: List<io.github.aryan007h.musicoonline.core.ShelfItem> = emptyList(),
        val radioLoading: Boolean = false,
        // Onboarding + personalization
        val onboardingDone: Boolean = true,
        val personalizing: Boolean = false,
        val personalizedShelves: List<io.github.aryan007h.musicoonline.core.Shelf> = emptyList(),
        val personalizationError: String? = null,
        // Long-press actions
        val savingTrackId: String? = null,
        // Onboarding artist search
        val artistResults: List<OnlineTrack> = emptyList(),
        val artistSearching: Boolean = false,
        val savedLanguages: List<String> = emptyList(),
        val savedArtists: List<String> = emptyList(),
        // Player niceties (local prefs)
        val skipSilence: Boolean = false,
        val playbackSpeed: Float = 1f,
        // Detail pages (album / playlist / artist / genre) — a nav stack
        val details: List<DetailPageState> = emptyList(),
        // Save-offline progress
        val savingTotal: Int = 0,
        val savingDone: Int = 0,
        // Stream cache (Settings)
        val cacheBytes: Long? = null,
        val cacheCleared: Long? = null,
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
        if (!_state.value.onboardingDone) {
            _state.update { it.copy(overlay = AppOverlay.ONBOARD) }
        } else {
            // Returning user with picks: make sure the personalized shelves
            // are always on home, even if a previous run's personalization
            // failed mid-way.
            val st = _state.value
            if ((st.savedLanguages.isNotEmpty() || st.savedArtists.isNotEmpty()) &&
                st.personalizedShelves.isEmpty()
            ) {
                personalizeHome(st.savedLanguages, st.savedArtists)
            }
        }
        // Player niceties apply the moment the controller binds.
        io.github.aryan007h.musicoonline.player.PlaybackPrefs.setSkipSilence(_state.value.skipSilence)
        viewModelScope.launch {
            playback.setSkipSilence(_state.value.skipSilence)
            playback.setSpeed(_state.value.playbackSpeed)
        }
    }

    // ── Onboarding → personalized home ───────────────────────────────────

    fun finishOnboarding(languages: List<String>, artists: List<String>) {
        prefs.edit()
            .putBoolean(PREF_ONBOARDED, true)
            .putString(PREF_LANGS, json.encodeToString(ListSerializer(String.serializer()), languages))
            .putString(PREF_ARTISTS, json.encodeToString(ListSerializer(String.serializer()), artists))
            .apply()
        _state.update { it.copy(onboardingDone = true, overlay = AppOverlay.NONE) }
        if (languages.isNotEmpty() || artists.isNotEmpty()) {
            personalizeHome(languages, artists)
        }
    }

    /**
     * Client-side personalization on top of the core's engine, all through
     * live-verified endpoints: each picked artist seeds a radio shelf
     * ("Because you like …"), each picked language matches a genre style
     * card and surfaces its first playable shelf ("… picks").
     *
     * Failures never vanish: whatever seeds fail are reported, and the
     * shelves that DID build always land on home (separate from the feed).
     */
    private fun personalizeHome(languages: List<String>, artists: List<String>) {
        Log.i(TAG, "personalize: start langs=$languages artists=$artists")
        if (personalizeJob?.isActive == true) return
        personalizeJob = viewModelScope.launch {
            _state.update { it.copy(personalizing = true, personalizationError = null) }
            val shelves = mutableListOf<io.github.aryan007h.musicoonline.core.Shelf>()
            val failures = mutableListOf<String>()

            // Artist radio shelves (max 4). Seed fallback: try the first
            // few search hits as radio seeds — artist-name matching is a
            // heuristic and one dead seed must not kill the shelf.
            artists.take(4).forEach { artist ->
                var done = false
                Log.i(TAG, "personalize: artist=$artist searching")
                runCatching {
                    val page = json.decodeFromJsonElement(
                        SearchResultPage.serializer(),
                        MusicoCore.call("online.search", buildJsonObject { put("query", artist) }),
                    )
                    val candidates = page.tracks.take(4)
                    for (seed in candidates) {
                        val radio = runCatching {
                            json.decodeFromJsonElement(
                                io.github.aryan007h.musicoonline.core.RelatedReply.serializer(),
                                MusicoCore.call("online.related", buildJsonObject { put("videoId", seed.videoId) }),
                            )
                        }.onFailure { Log.w(TAG, "personalize: radio seed failed: " + it.message) }
                        .getOrNull()?.tracks.orEmpty()
                        if (radio.size >= 4) {
                            shelves += io.github.aryan007h.musicoonline.core.Shelf(
                                title = "Because you like $artist",
                                items = radio.take(14).map { t ->
                                    io.github.aryan007h.musicoonline.core.ShelfItem(
                                        kind = io.github.aryan007h.musicoonline.core.ItemKind.SONG,
                                        title = t.title,
                                        subtitle = t.artist,
                                        thumbnail = t.thumbnail,
                                        videoId = t.videoId,
                                    )
                                },
                            )
                            done = true
                            break
                        }
                    }
                }
                Log.i(TAG, "personalize: artist=$artist built=" + done + " shelvesSoFar=" + shelves.size)
                if (!done) failures += artist
            }

            // Language picks: match style cards by name, surface their songs.
            languages.take(3).forEach { lang ->
                Log.i(TAG, "personalize: lang=" + lang + " starting")
                runCatching {
                    val styles = json.decodeFromJsonElement(
                        io.github.aryan007h.musicoonline.core.GenresReply.serializer(),
                        MusicoCore.call("online.genres"),
                    ).items
                    val style = styles.firstOrNull { s -> s.title.contains(lang, ignoreCase = true) }
                    if (style?.browseId == null) {
                        failures += lang
                        return@forEach
                    }
                    val page = json.decodeFromJsonElement(
                        BrowsePage.serializer(),
                        MusicoCore.call(
                            "online.browse",
                            buildJsonObject {
                                put("browseId", style.browseId)
                                style.params?.let { put("params", it) }
                            },
                        ),
                    )
                    val songShelf = page.shelves.firstOrNull { shelf -> shelf.items.any { it.videoId != null } }
                    if (songShelf != null) {
                        shelves += io.github.aryan007h.musicoonline.core.Shelf(
                            title = "$lang picks",
                            items = songShelf.items.take(14),
                        )
                    } else {
                        failures += lang
                    }
                }
            }

            prefs.edit()
                .putString(
                    PREF_PERSONAL_SHELVES,
                    json.encodeToString(
                        ListSerializer(io.github.aryan007h.musicoonline.core.Shelf.serializer()),
                        shelves,
                    ),
                )
                .apply()
            _state.update {
                it.copy(
                    personalizing = false,
                    personalizedShelves = shelves,
                    personalizationError = if (failures.isNotEmpty()) {
                        "Couldn't build shelves for: ${failures.joinToString()}"
                    } else null,
                )
            }
            if (shelves.isEmpty() && failures.isNotEmpty()) {
                _state.update { it.copy(error = "Personalization failed — check Airplane Mode") }
            }
        }
    }

    private var personalizeJob: kotlinx.coroutines.Job? = null

    // ── Endless browsing: real continuation paging, then a genre chain ──

    fun loadMoreHome() {
        if (_state.value.moreLoading) return
        viewModelScope.launch {
            _state.update { it.copy(moreLoading = true) }

            // 1. Real pagination: the home feed's own continuation token —
            // when YTM provides one, page until it stops.
            val cont = _state.value.homeContinuation
            if (cont != null) {
                runCatching {
                    json.decodeFromJsonElement(
                        BrowsePage.serializer(),
                        MusicoCore.call("online.browse", buildJsonObject { put("continuation", cont) }),
                    )
                }.onFold(
                    onSuccess = { page ->
                        _state.update {
                            val existing = it.homeShelves.map { s -> s.title }.toSet()
                            val fresh = page.shelves.filter { s -> s.title !in existing && s.items.isNotEmpty() }
                            it.copy(
                                homeShelves = it.homeShelves + fresh,
                                homeContinuation = page.nextContinuation,
                                moreLoading = false,
                            )
                        }
                    },
                    onFailure = { _state.update { it.copy(moreLoading = false) } },
                )
                return@launch
            }

            // 2. No continuation support on this feed → the endless genre
            // chain: each scroll-to-end browses the next style card's page
            // and appends its shelves. ~50 styles × shelves each = home
            // genuinely never runs dry. Charts get appended once, first.
            val st = _state.value
            if (!st.chartsAppended) {
                val charts = runCatching {
                    json.decodeFromJsonElement(
                        BrowsePage.serializer(),
                        MusicoCore.call("online.browse", buildJsonObject { put("browseId", "FEmusic_charts") }),
                    )
                }.getOrNull()
                val styles = runCatching {
                    json.decodeFromJsonElement(
                        io.github.aryan007h.musicoonline.core.GenresReply.serializer(),
                        MusicoCore.call("online.genres"),
                    )
                }.getOrNull()

                _state.update {
                    val existing = it.homeShelves.map { s -> s.title }.toSet()
                    val chartShelves = (charts?.shelves ?: emptyList()).filter { s -> s.title !in existing && s.items.isNotEmpty() }
                    it.copy(
                        homeShelves = it.homeShelves + chartShelves,
                        styleItems = styles?.items ?: emptyList(),
                        styleQueue = styles?.items?.shuffled()?.filter { s -> s.browseId != null } ?: emptyList(),
                        chartsAppended = true,
                        moreLoading = false,
                    )
                }
                return@launch
            }

            // 3. Next style page(s) into the feed.
            val queue = st.styleQueue
            if (queue.isEmpty()) {
                _state.update { it.copy(moreLoading = false, moreExhausted = true) }
                return@launch
            }
            val batch = queue.take(2)
            val fresh = mutableListOf<io.github.aryan007h.musicoonline.core.Shelf>()
            batch.forEach { style ->
                runCatching {
                    val page = json.decodeFromJsonElement(
                        BrowsePage.serializer(),
                        MusicoCore.call(
                            "online.browse",
                            buildJsonObject {
                                put("browseId", style.browseId ?: return@runCatching)
                                style.params?.let { put("params", it) }
                            },
                        ),
                    )
                    page.shelves.filter { shelf -> shelf.items.any { it.videoId != null } }.forEach { shelf ->
                        val title = style.title + " · " + shelf.title
                        fresh += shelf.copy(title = title)
                    }
                }
            }
            _state.update {
                val existing = it.homeShelves.map { s -> s.title }.toSet()
                val add = fresh.filter { s -> s.title !in existing && s.items.isNotEmpty() }
                it.copy(
                    homeShelves = it.homeShelves + add,
                    styleQueue = queue.drop(batch.size),
                    moreLoading = false,
                    moreExhausted = queue.size <= batch.size && add.isEmpty(),
                )
            }
        }
    }

    fun dismissOnboarding() {
        prefs.edit().putBoolean(PREF_ONBOARDED, true).apply()
        _state.update { it.copy(onboardingDone = true, overlay = AppOverlay.NONE) }
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
                palette = io.github.aryan007h.musicoonline.ui.theme.Palette.fromId(prefs.getString(PREF_PALETTE, null)),
                displayName = prefs.getString(PREF_NAME, null) ?: it.displayName,
                onboardingDone = prefs.getBoolean(PREF_ONBOARDED, false),
                savedLanguages = prefs.getString(PREF_LANGS, null)?.let {
                    runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull()
                } ?: emptyList(),
                savedArtists = prefs.getString(PREF_ARTISTS, null)?.let {
                    runCatching { json.decodeFromString(ListSerializer(String.serializer()), it) }.getOrNull()
                } ?: emptyList(),
                personalizedShelves = prefs.getString(PREF_PERSONAL_SHELVES, null)?.let {
                    runCatching {
                        json.decodeFromString(
                            ListSerializer(io.github.aryan007h.musicoonline.core.Shelf.serializer()),
                            it,
                        )
                    }.getOrNull()
                } ?: emptyList(),
                skipSilence = prefs.getBoolean(PREF_SKIP_SILENCE, false),
                playbackSpeed = prefs.getFloat(PREF_SPEED, 1f),
            )
        }
    }

    fun setLyricBackdrop(backdrop: LyricBackdrop) {
        prefs.edit().putString(PREF_BACKDROP, backdrop.id).apply()
        _state.update { it.copy(lyricBackdrop = backdrop) }
    }

    fun setPalette(palette: io.github.aryan007h.musicoonline.ui.theme.Palette) {
        prefs.edit().putString(PREF_PALETTE, palette.id).apply()
        _state.update { it.copy(palette = palette) }
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

    // ── Stream cache (Settings) ──────────────────────────────────────────

    fun refreshCacheUsage() {
        viewModelScope.launch {
            runCatching {
                val reply = MusicoCore.call("online.cache.usage")
                reply["bytes"]?.toString()?.trim('"')?.toLongOrNull()
            }.onSuccess { bytes -> _state.update { it.copy(cacheBytes = bytes) } }
        }
    }

    fun clearStreamCache() {
        viewModelScope.launch {
            runCatching {
                val reply = MusicoCore.call("online.cache.clear")
                reply["freedBytes"]?.toString()?.trim('"')?.toLongOrNull()
            }.onSuccess { freed ->
                _state.update { it.copy(cacheCleared = freed, cacheBytes = 0L, error = "Cleared cache — ${(freed ?: 0) / (1024 * 1024)} MB freed") }
            }
        }
    }

    fun setCacheCap(mb: Int) {
        saveCoreSetting("online_cache_mb", kotlinx.serialization.json.JsonPrimitive(mb))
    }

    fun setStreamingQuality(quality: String) {
        saveCoreSetting("streaming_quality", kotlinx.serialization.json.JsonPrimitive(quality))
    }

    // ── Navigation ───────────────────────────────────────────────────────

    fun selectTab(tab: AppTab) {
        _state.update { it.copy(tab = tab, overlay = AppOverlay.NONE) }
        when (tab) {
            AppTab.HOME -> if (_state.value.homeShelves.isEmpty() && !_state.value.homeLoading) loadHome()
            AppTab.RADIO -> if (_state.value.radioStyles.isEmpty() && !_state.value.radioLoading) loadRadio()
            else -> Unit
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
                onSuccess = { page ->
                    _state.update {
                        it.copy(
                            homeLoading = false,
                            homeShelves = page.shelves,
                            homeContinuation = page.nextContinuation,
                            moreExhausted = false,
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(homeLoading = false, homeError = e.userMessage()) } },
            )
        }
    }

    /** Opens a shelf item: browseable → detail page; playable → play in shelf context. */
    fun openShelfItem(item: ShelfItem, context: List<ShelfItem>) {
        val browseId = item.browseId
        if (browseId != null) {
            openDetail(browseId, item.params, item.title)
            return
        }
        val track = item.toTrackOrNull() ?: return
        val queue = context.mapNotNull { it.toTrackOrNull() }
        val index = queue.indexOfFirst { it.videoId == track.videoId }.takeIf { it >= 0 } ?: 0
        playQueue(queue.ifEmpty { listOf(track) }, index)
    }

    // ── Detail pages (album / playlist / artist / genre) ────────────────

    fun openDetail(browseId: String, params: String?, title: String) {
        val entry = DetailPageState(browseId = browseId, params = params, title = title, loading = true)
        _state.update { it.copy(details = it.details + entry, overlay = AppOverlay.DETAIL) }
        viewModelScope.launch {
            runCatching {
                val reply = MusicoCore.call(
                    "online.browse",
                    buildJsonObject {
                        put("browseId", browseId)
                        params?.let { put("params", it) }
                    },
                )
                json.decodeFromJsonElement(BrowsePage.serializer(), reply)
            }.onFold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(details = it.details.map { d ->
                            if (d.browseId == browseId && d.params == params) d.copy(page = page, loading = false)
                            else d
                        })
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(details = it.details.map { d ->
                            if (d.browseId == browseId && d.params == params) d.copy(error = e.userMessage(), loading = false)
                            else d
                        })
                    }
                },
            )
        }
    }

    fun popDetail() {
        _state.update {
            val rest = it.details.dropLast(1)
            it.copy(details = rest, overlay = if (rest.isEmpty()) AppOverlay.NONE else AppOverlay.DETAIL)
        }
    }

    /** Every playable track on the current detail page, flattened in page order. */
    fun detailTracks(entry: DetailPageState): List<OnlineTrack> =
        entry.page?.shelves.orEmpty().flatMap { it.items }.mapNotNull { it.toTrackOrNull() }

    fun playDetail(entry: DetailPageState) {
        val tracks = detailTracks(entry)
        if (tracks.isNotEmpty()) playQueue(tracks, 0)
    }

    fun queueDetail(entry: DetailPageState) {
        val tracks = detailTracks(entry)
        if (tracks.isEmpty()) return
        if (queueTracks.isEmpty()) {
            playQueue(tracks, 0)
            return
        }
        queueTracks = queueTracks + tracks
        _state.update { it.copy(queue = queueTracks, error = "Added ${tracks.size} songs to the queue") }
    }

    /** Saves a detail page's tracks offline: sequential resolve → stream cache. */
    fun saveDetailOffline(entry: DetailPageState) {
        val tracks = detailTracks(entry)
        if (tracks.isEmpty()) return
        val browseId = entry.browseId
        viewModelScope.launch {
            tracks.forEachIndexed { i, track ->
                _state.update {
                    it.copy(details = it.details.map { d ->
                        if (d.browseId == browseId) d.copy(saving = true, saved = i)
                        else d
                    }, savingTotal = tracks.size, savingDone = i)
                }
                runCatching {
                    MusicoCore.call("track.resolve", resolveArgs(track))
                }
            }
            _state.update {
                it.copy(
                    savingTotal = 0,
                    savingDone = 0,
                    details = it.details.map { d ->
                        if (d.browseId == browseId) d.copy(saving = false, saved = tracks.size)
                        else d
                    },
                    error = "Saved ${tracks.size} songs offline",
                )
            }
        }
    }

    private fun resolveArgs(track: OnlineTrack) = buildJsonObject {
        put("videoId", track.videoId)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album)
        track.durationSecs?.let { put("durationSecs", it) }
        track.thumbnail?.let { put("thumbnail", it) }
        put("source", track.source)
        track.artistBrowseId?.let { put("artistBrowseId", it) }
    }

    // ── Radio tab: the full mood/genre station grid ──────────────────────

    fun loadRadio() {
        if (_state.value.radioLoading) return
        viewModelScope.launch {
            _state.update { it.copy(radioLoading = true) }
            runCatching {
                json.decodeFromJsonElement(
                    io.github.aryan007h.musicoonline.core.GenresReply.serializer(),
                    MusicoCore.call("online.genres"),
                )
            }.onFold(
                onSuccess = { styles -> _state.update { it.copy(radioLoading = false, radioStyles = styles.items) } },
                onFailure = { e -> _state.update { it.copy(radioLoading = false, error = e.userMessage()) } },
            )
        }
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

    private var artistSearchJob: Job? = null

    /** Debounced artist search for onboarding picks. */
    fun searchArtists(query: String) {
        artistSearchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(artistResults = emptyList()) }
            return
        }
        artistSearchJob = viewModelScope.launch {
            delay(350)
            _state.update { it.copy(artistSearching = true) }
            runCatching {
                json.decodeFromJsonElement(
                    SearchResultPage.serializer(),
                    MusicoCore.call("online.search", buildJsonObject { put("query", query) }),
                )
            }.onFold(
                onSuccess = { page -> _state.update { it.copy(artistSearching = false, artistResults = page.tracks.take(12)) } },
                onFailure = { _state.update { it.copy(artistSearching = false) } },
            )
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
                    val reply = MusicoCore.call("track.resolve", resolveArgs(track))
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

    /**
     * Warms the next queue entries in the background while the current
     * track plays — the single biggest perceived-latency win (the next tap
     * resolves from cache instead of the 10 s+ fast-path pool). Background
     * budget is generous (120 s/attempt), failures are silent by design,
     * and it never holds the interactive resolve lock.
     */
    private var prefetchJob: Job? = null

    private fun prefetchUpcoming() {
        if (!_state.value.coreSettingsAutoplay) return
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val upcoming = queueTracks.drop(queuePosition + 1).take(2)
            for (track in upcoming) {
                if (_state.value.nowPlaying?.videoId != queueTracks.getOrNull(queuePosition)?.videoId) {
                    return@launch // the user moved on — stop warming old queue
                }
                runCatching { MusicoCore.call("track.resolve", resolveArgs(track)) }
            }
        }
    }

    private fun skipFailedTrack() {
        val failed = queueTracks.getOrNull(queuePosition)
        val queue = queueTracks
        if (queue.size <= 1 || queuePosition !in queue.indices) return
        queueTracks = queue.filterIndexed { i, _ -> i != queuePosition }
        queuePosition = queuePosition.coerceAtMost(queueTracks.size - 1)
        _state.update {
            it.copy(
                queue = queueTracks,
                queueIndex = queuePosition,
                error = "Couldn't play \"${failed?.title?.ifEmpty { "track" }.orEmpty()}\" — skipped to the next song",
            )
        }
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

    // ── Long-press actions ───────────────────────────────────────────────

    /** Insert right after the current track — "Play next". */
    fun playNext(track: OnlineTrack) {
        if (queueTracks.isEmpty() || _state.value.nowPlaying == null) {
            playTrack(track)
            return
        }
        val at = queuePosition + 1
        queueTracks = queueTracks.toMutableList().apply { add(at, track) }
        _state.update { it.copy(queue = queueTracks, error = "Playing next: ${track.title.ifEmpty { "track" }}") }
    }

    /** Append at the tail — "Add to queue". */
    fun enqueue(track: OnlineTrack) {
        if (queueTracks.isEmpty() || _state.value.nowPlaying == null) {
            playTrack(track)
            return
        }
        queueTracks = queueTracks + track
        _state.update { it.copy(queue = queueTracks, error = "Added to queue") }
    }

    /** Resolve now → the stream cache holds the file (offline-ready). */
    fun saveTrackOffline(track: OnlineTrack) {
        viewModelScope.launch {
            _state.update { it.copy(savingTrackId = track.videoId) }
            runCatching { MusicoCore.call("track.resolve", resolveArgs(track)) }
                .onSuccess { _state.update { it.copy(savingTrackId = null, error = "Saved offline: ${track.title.ifEmpty { "track" }}") } }
                .onFailure { e -> _state.update { it.copy(savingTrackId = null, error = e.userMessage()) } }
        }
    }

    // ── Player niceties ──────────────────────────────────────────────────

    fun setSkipSilence(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_SKIP_SILENCE, enabled).apply()
        _state.update { it.copy(skipSilence = enabled) }
        viewModelScope.launch { playback.setSkipSilence(enabled) }
    }

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(PREF_SPEED, speed).apply()
        _state.update { it.copy(playbackSpeed = speed) }
        viewModelScope.launch { playback.setSpeed(speed) }
    }

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
        private const val TAG = "Music0UI"
        private const val PREFS_NAME = "musico_ui"
        private const val PREF_LIKED = "liked_ids"
        private const val PREF_RECENTS = "recent_tracks"
        private const val PREF_SEARCHES = "recent_searches"
        private const val PREF_BACKDROP = "lyric_backdrop"
        private const val PREF_PALETTE = "palette_id"
        private const val PREF_ONBOARDED = "onboarding_done"
        private const val PREF_LANGS = "onboarding_languages"
        private const val PREF_ARTISTS = "onboarding_artists"
        private const val PREF_SKIP_SILENCE = "skip_silence"
        private const val PREF_SPEED = "playback_speed"
        private const val PREF_PERSONAL_SHELVES = "personalized_shelves"
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
        durationSecs = durationSecs,
        thumbnail = thumbnail,
        source = "",
    )
}
