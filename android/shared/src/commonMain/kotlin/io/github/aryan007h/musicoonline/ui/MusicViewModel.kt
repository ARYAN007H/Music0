package io.github.aryan007h.musicoonline.ui

import androidx.compose.ui.graphics.Color
import io.github.aryan007h.musicoonline.core.BrowsePage
import io.github.aryan007h.musicoonline.core.CatalogEntry
import io.github.aryan007h.musicoonline.core.CoreException
import io.github.aryan007h.musicoonline.core.CoreSettings
import io.github.aryan007h.musicoonline.core.DownloadedTrack
import io.github.aryan007h.musicoonline.core.DownloadsListReply
import io.github.aryan007h.musicoonline.core.EnhancedLyrics
import io.github.aryan007h.musicoonline.core.GenresReply
import io.github.aryan007h.musicoonline.core.ItemKind
import io.github.aryan007h.musicoonline.core.LibraryListReply
import io.github.aryan007h.musicoonline.core.LikesListReply
import io.github.aryan007h.musicoonline.core.ListeningStatsReply
import io.github.aryan007h.musicoonline.core.MusicoCore
import io.github.aryan007h.musicoonline.core.OnlineTrack
import io.github.aryan007h.musicoonline.core.PlaylistsReply
import io.github.aryan007h.musicoonline.core.RelatedReply
import io.github.aryan007h.musicoonline.core.RerankReply
import io.github.aryan007h.musicoonline.core.ResolvedTrack
import io.github.aryan007h.musicoonline.core.SearchResultPage
import io.github.aryan007h.musicoonline.core.Shelf
import io.github.aryan007h.musicoonline.core.ShelfItem
import io.github.aryan007h.musicoonline.core.SmartMix
import io.github.aryan007h.musicoonline.core.SmartMixesReply
import io.github.aryan007h.musicoonline.core.SuggestionsReply
import io.github.aryan007h.musicoonline.core.UpdateInfo
import io.github.aryan007h.musicoonline.core.UserPlaylist
import io.github.aryan007h.musicoonline.platform.PlatformPlayback
import io.github.aryan007h.musicoonline.platform.PlatformPrefs
import io.github.aryan007h.musicoonline.platform.PlatformUpdater
import io.github.aryan007h.musicoonline.platform.currentLocalHour
import io.github.aryan007h.musicoonline.platform.currentTimeMillis
import io.github.aryan007h.musicoonline.platform.prefetchArtworkImage
import io.github.aryan007h.musicoonline.ui.library.LibraryCollectionRef
import io.github.aryan007h.musicoonline.ui.player.ArtworkAtmosphere
import io.github.aryan007h.musicoonline.ui.player.ArtworkPaletteCache
import io.github.aryan007h.musicoonline.ui.player.LyricsDisplayMode
import io.github.aryan007h.musicoonline.ui.theme.Palette
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Local look-and-feel preferences (PlatformPrefs, not the Rust core). */
enum class LyricBackdrop(val id: String, val label: String) {
    Mesh("mesh", "Fluid"),
    Artwork("artwork", "Glow"),
    Aurora("aurora", "Aurora"),
    Rain("rain", "Rain"),
    Black("black", "Dark");

    companion object {
        fun fromId(id: String?): LyricBackdrop = entries.firstOrNull { it.id == id } ?: Mesh
    }
}

enum class AppTab { HOME, RADIO, LIBRARY, SEARCH }

enum class AppOverlay { NONE, PLAYER, SETTINGS, PROFILE, DETAIL, COLLECTION, EQ, ONBOARD }

/**
 * Shared Compose Multiplatform ViewModel driving both Android & Desktop JVM:
 * - Full sled-backed Library (user playlists CRUD/reorder, metadata-complete
 *   Liked Songs, Offline Downloads, Saved Albums/Artists, Smart Mixes)
 * - Closed-loop Hybrid Recommender (listen/skip telemetry, MMR queue re-ranking,
 *   daypart Smart Mixes, taste profile stats)
 * - 2-tier predictive prefetch (gapless N+1 audio staging + LRU lyrics cache
 *   + Coil 3 artwork & palette warm-up)
 * - Full-Screen Lyrics Studio state (Cinema/Kinetic mode, per-track sync offset)
 * - Cross-platform Volume, Speed, Skip Silence & Updater integration
 */
class MusicViewModel(
    private val playback: PlatformPlayback,
    private val prefs: PlatformPrefs,
    private val updater: PlatformUpdater,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        val playerInitialPage: Int = 0,
        val error: String? = null,
        val errorSeq: Long = 0,
        val undoActionLabel: String? = null,
        val coreVersion: String? = null,
        val nowPlaying: OnlineTrack? = null,
        val isPlaying: Boolean = false,
        val isBuffering: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val volume: Float = 1f,
        val resolvingId: String? = null,
        // Queue
        val queue: List<OnlineTrack> = emptyList(),
        val queueIndex: Int = -1,
        val repeatAll: Boolean = false,
        // Lyrics Studio
        val lyrics: EnhancedLyrics? = null,
        val lyricsLoading: Boolean = false,
        val lyricsMode: LyricsDisplayMode = LyricsDisplayMode.CINEMA,
        val lyricOffsetMs: Long = 0L,
        // Local prefs
        val lyricBackdrop: LyricBackdrop = LyricBackdrop.Mesh,
        val palette: Palette = Palette.NEBULA,
        val displayName: String = "Listener",
        // Full sled-backed Library & Smart Mixes
        val playlists: List<UserPlaylist> = emptyList(),
        val likedIds: Set<String> = emptySet(),
        val likedTracks: List<OnlineTrack> = emptyList(),
        val downloads: List<DownloadedTrack> = emptyList(),
        val downloadsTotalBytes: Long = 0L,
        val libraryAlbums: List<CatalogEntry> = emptyList(),
        val libraryArtists: List<CatalogEntry> = emptyList(),
        val smartMixes: List<SmartMix> = emptyList(),
        val listeningStats: ListeningStatsReply? = null,
        val activeCollection: LibraryCollectionRef? = null,
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
        val homeShelves: List<Shelf> = emptyList(),
        val homeContinuation: String? = null,
        val moreLoading: Boolean = false,
        val moreExhausted: Boolean = false,
        val styleItems: List<ShelfItem> = emptyList(),
        val styleQueue: List<ShelfItem> = emptyList(),
        val chartsAppended: Boolean = false,
        // Radio tab
        val radioStyles: List<ShelfItem> = emptyList(),
        val radioLoading: Boolean = false,
        // Onboarding + personalization
        val onboardingDone: Boolean = true,
        val personalizing: Boolean = false,
        val personalizedShelves: List<Shelf> = emptyList(),
        val personalizationError: String? = null,
        val savingTrackId: String? = null,
        val artistResults: List<OnlineTrack> = emptyList(),
        val artistSearching: Boolean = false,
        val savedLanguages: List<String> = emptyList(),
        val savedArtists: List<String> = emptyList(),
        // Player niceties
        val skipSilence: Boolean = false,
        val playbackSpeed: Float = 1f,
        // In-app updater
        val updateInfo: UpdateInfo? = null,
        val updateChecking: Boolean = false,
        val updateDownloading: Boolean = false,
        val updateDownloadedMb: Long = 0,
        val updateTotalMb: Long = 0,
        val updateDismissed: Boolean = false,
        // Detail pages nav stack
        val details: List<DetailPageState> = emptyList(),
        // Batch offline download progress
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

    // Queue bookkeeping (mirrors state.queue / state.queueIndex)
    private var queueTracks: List<OnlineTrack> = emptyList()
    private var queuePosition: Int = -1

    // Playhead race protection
    private var playGeneration: Long = 0L
    private var resolveJob: Job? = null
    private var consecutiveAutoSkips = 0
    private var suggestJob: Job? = null
    private var autoplayExtendJob: Job? = null
    private var prefetchJob: Job? = null
    private var personalizeJob: Job? = null
    private var artistSearchJob: Job? = null

    // Listen telemetry for closed-loop Rust recommender
    private var telemetryTrack: OnlineTrack? = null
    private var telemetryStartMs: Long = 0L

    // Pure-Kotlin bounded LRU cache for instant lyrics & per-track timing offsets
    private val lyricCache = LinkedHashMap<String, EnhancedLyrics>(40, 0.75f, true)
    private val lyricOffsets = mutableMapOf<String, Long>()

    // Undo buffer (e.g. accidental playlist track removal)
    private var pendingUndoAction: (() -> Unit)? = null
    private var openUpdateRequest = false

    init {
        scope.launch {
            runCatching { MusicoCore.ping() }
                .onSuccess { d -> _state.update { it.copy(coreVersion = d["version"]?.toString()?.trim('"')) } }
                .onFailure { e -> fail("Core: ${e.message}") }
        }
        scope.launch { playback.nowPlaying.collect { t -> _state.update { it.copy(nowPlaying = t) } } }
        scope.launch { playback.isPlaying.collect { p -> _state.update { it.copy(isPlaying = p) } } }
        scope.launch { playback.isBuffering.collect { b -> _state.update { it.copy(isBuffering = b) } } }
        scope.launch { playback.positionMs.collect { p -> _state.update { it.copy(positionMs = p) } } }
        scope.launch { playback.durationMs.collect { d -> _state.update { it.copy(durationMs = d) } } }
        scope.launch { playback.volume.collect { v -> _state.update { it.copy(volume = v) } } }
        scope.launch { playback.trackEnded.collect { onTrackEnded() } }
        scope.launch { playback.gaplessAdvanced.collect { vid -> onGaplessTransition(vid) } }

        loadLocalPrefs()
        loadCoreSettings()
        migrateAndHydrateLibrary()
        loadHome()

        if (!_state.value.onboardingDone) {
            _state.update { it.copy(overlay = AppOverlay.ONBOARD) }
        } else {
            val st = _state.value
            if ((st.savedLanguages.isNotEmpty() || st.savedArtists.isNotEmpty()) &&
                st.personalizedShelves.isEmpty()
            ) {
                personalizeHome(st.savedLanguages, st.savedArtists)
            }
        }
        checkForUpdate()
        scope.launch {
            playback.setSkipSilence(_state.value.skipSilence)
            playback.setSpeed(_state.value.playbackSpeed)
            playback.setVolume(_state.value.volume)
        }
    }

    fun clear() {
        flushTelemetry(completedNaturally = false)
        scope.cancel()
    }

    // ── Legacy Migration & Full Sled Library Hydration ───────────────────

    private fun migrateAndHydrateLibrary() {
        scope.launch(Dispatchers.IO) {
            if (!prefs.getBoolean(PREF_SLED_MIGRATED, false)) {
                val legacyIds = _state.value.likedIds
                val recents = _state.value.recentlyPlayed
                val recentById = recents.associateBy { it.videoId }
                val legacyLikedTracks = legacyIds.map { id ->
                    recentById[id] ?: OnlineTrack(videoId = id, title = id)
                }
                runCatching {
                    MusicoCore.call(
                        "library.migrate_legacy",
                        buildJsonObject {
                            put(
                                "likedTracks",
                                buildJsonArray { legacyLikedTracks.forEach { add(trackToJson(it)) } },
                            )
                            put(
                                "recentTracks",
                                buildJsonArray { recents.forEach { add(trackToJson(it)) } },
                            )
                        },
                    )
                }.onSuccess {
                    prefs.putBoolean(PREF_SLED_MIGRATED, true)
                }
            }
            refreshLibraryAllInternal()
        }
    }

    fun refreshLibraryAll() {
        scope.launch(Dispatchers.IO) { refreshLibraryAllInternal() }
    }

    private suspend fun refreshLibraryAllInternal() {
        refreshPlaylistsInternal()
        refreshLikesInternal()
        refreshDownloadsInternal()
        refreshCatalogLibraryInternal()
        refreshSmartMixesInternal()
        refreshListeningStatsInternal()
    }

    private suspend fun refreshPlaylistsInternal() {
        runCatching {
            val reply = MusicoCore.call("playlist.list")
            json.decodeFromJsonElement(PlaylistsReply.serializer(), reply).playlists
        }.onSuccess { pls ->
            _state.update { it.copy(playlists = pls) }
        }
    }

    private suspend fun refreshLikesInternal() {
        runCatching {
            val reply = MusicoCore.call("library.likes.list")
            json.decodeFromJsonElement(LikesListReply.serializer(), reply)
        }.onSuccess { likes ->
            val mergedIds = likes.ids.toSet()
            saveStringListPref(PREF_LIKED_JSON, mergedIds.toList())
            _state.update { it.copy(likedIds = mergedIds, likedTracks = likes.tracks) }
        }
    }

    private suspend fun refreshDownloadsInternal() {
        runCatching {
            val reply = MusicoCore.call("library.downloads.list")
            json.decodeFromJsonElement(DownloadsListReply.serializer(), reply)
        }.onSuccess { dl ->
            _state.update {
                it.copy(
                    downloads = dl.downloads,
                    downloadsTotalBytes = dl.totalBytes,
                )
            }
        }
    }

    private suspend fun refreshCatalogLibraryInternal() {
        runCatching {
            val albums = json.decodeFromJsonElement(
                LibraryListReply.serializer(),
                MusicoCore.call("library.list", buildJsonObject { put("kind", "album") }),
            ).entries
            val artists = json.decodeFromJsonElement(
                LibraryListReply.serializer(),
                MusicoCore.call("library.list", buildJsonObject { put("kind", "artist") }),
            ).entries
            albums to artists
        }.onSuccess { (albums, artists) ->
            _state.update { it.copy(libraryAlbums = albums, libraryArtists = artists) }
        }
    }

    private suspend fun refreshSmartMixesInternal() {
        val hour = currentLocalHour()
        runCatching {
            val reply = MusicoCore.call(
                "rec.smart_mixes",
                buildJsonObject { put("localHour", hour) },
            )
            json.decodeFromJsonElement(SmartMixesReply.serializer(), reply)
        }.onSuccess { res ->
            _state.update { it.copy(smartMixes = res.mixes) }
        }
    }

    private suspend fun refreshListeningStatsInternal() {
        runCatching {
            val reply = MusicoCore.call("rec.stats")
            json.decodeFromJsonElement(ListeningStatsReply.serializer(), reply)
        }.onSuccess { stats ->
            _state.update { it.copy(listeningStats = stats) }
        }
    }

    // ── Playlists CRUD & Collection Navigation ───────────────────────────

    fun openCollection(ref: LibraryCollectionRef) {
        _state.update { it.copy(activeCollection = ref, overlay = AppOverlay.COLLECTION) }
        if (ref is LibraryCollectionRef.OfflineDownloads) {
            scope.launch(Dispatchers.IO) { refreshDownloadsInternal() }
        } else if (ref is LibraryCollectionRef.Mix) {
            scope.launch(Dispatchers.IO) { refreshSmartMixesInternal() }
        }
    }

    fun closeCollection() {
        _state.update { it.copy(activeCollection = null, overlay = AppOverlay.NONE) }
    }

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                MusicoCore.call("playlist.create", buildJsonObject { put("name", trimmed) })
            }.onSuccess {
                refreshPlaylistsInternal()
                fail("Created playlist “$trimmed”")
            }.onFailure { e -> fail(e.userMessage()) }
        }
    }

    fun createPlaylistAndAdd(name: String, tracks: List<OnlineTrack>) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                val created = json.decodeFromJsonElement(
                    UserPlaylist.serializer(),
                    MusicoCore.call("playlist.create", buildJsonObject { put("name", trimmed) }),
                )
                tracks.forEach { track ->
                    MusicoCore.call(
                        "playlist.add_track",
                        buildJsonObject {
                            put("id", created.id)
                            put("track", trackToJson(track))
                        },
                    )
                }
                created
            }.onSuccess { created ->
                refreshPlaylistsInternal()
                fail("Saved ${tracks.size} song${if (tracks.size == 1) "" else "s"} to “${created.name}”")
            }.onFailure { e -> fail(e.userMessage()) }
        }
    }

    fun renamePlaylist(id: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            runCatching {
                MusicoCore.call(
                    "playlist.rename",
                    buildJsonObject {
                        put("id", id)
                        put("name", trimmed)
                    },
                )
            }.onSuccess {
                refreshPlaylistsInternal()
                fail("Renamed playlist to “$trimmed”")
            }.onFailure { e -> fail(e.userMessage()) }
        }
    }

    fun deletePlaylist(id: String) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                MusicoCore.call("playlist.delete", buildJsonObject { put("id", id) })
            }.onSuccess {
                refreshPlaylistsInternal()
                fail("Playlist deleted")
            }.onFailure { e -> fail(e.userMessage()) }
        }
    }

    fun addTracksToPlaylist(playlistId: String, tracks: List<OnlineTrack>) {
        if (tracks.isEmpty()) return
        val targetName = _state.value.playlists.firstOrNull { it.id == playlistId }?.name ?: "playlist"
        scope.launch(Dispatchers.IO) {
            runCatching {
                tracks.forEach { track ->
                    MusicoCore.call(
                        "playlist.add_track",
                        buildJsonObject {
                            put("id", playlistId)
                            put("track", trackToJson(track))
                        },
                    )
                }
            }.onSuccess {
                refreshPlaylistsInternal()
                fail(
                    if (tracks.size == 1) "Added to “$targetName”"
                    else "Added ${tracks.size} songs to “$targetName”",
                )
            }.onFailure { e -> fail(e.userMessage()) }
        }
    }

    fun removeTrackFromPlaylist(playlistId: String, index: Int) {
        val removedTrack = _state.value.playlists
            .firstOrNull { it.id == playlistId }
            ?.tracks
            ?.getOrNull(index)
            ?.toOnlineTrack()

        scope.launch(Dispatchers.IO) {
            runCatching {
                MusicoCore.call(
                    "playlist.remove_track",
                    buildJsonObject {
                        put("id", playlistId)
                        put("index", index)
                    },
                )
            }.onSuccess {
                refreshPlaylistsInternal()
                if (removedTrack != null) {
                    pendingUndoAction = {
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                MusicoCore.call(
                                    "playlist.add_track",
                                    buildJsonObject {
                                        put("id", playlistId)
                                        put("track", trackToJson(removedTrack))
                                    },
                                )
                                val currentCount = _state.value.playlists
                                    .firstOrNull { it.id == playlistId }?.tracks?.size ?: 0
                                if (index in 0 until currentCount) {
                                    MusicoCore.call(
                                        "playlist.move_track",
                                        buildJsonObject {
                                            put("id", playlistId)
                                            put("from", currentCount)
                                            put("to", index)
                                        },
                                    )
                                }
                            }.onSuccess { refreshPlaylistsInternal() }
                        }
                    }
                    showUndoableMessage("Removed “${removedTrack.title.ifEmpty { "track" }}”", "Undo")
                }
            }.onFailure { e -> fail(e.userMessage()) }
        }
    }

    fun moveTrackInPlaylist(playlistId: String, from: Int, to: Int) {
        if (from == to) return
        _state.update { st ->
            st.copy(
                playlists = st.playlists.map { pl ->
                    if (pl.id != playlistId) pl
                    else if (from !in pl.tracks.indices || to !in pl.tracks.indices) pl
                    else {
                        val copy = pl.tracks.toMutableList()
                        val item = copy.removeAt(from)
                        copy.add(to, item)
                        pl.copy(tracks = copy)
                    }
                },
            )
        }
        val rustTo = if (to > from) to + 1 else to
        scope.launch(Dispatchers.IO) {
            runCatching {
                MusicoCore.call(
                    "playlist.move_track",
                    buildJsonObject {
                        put("id", playlistId)
                        put("from", from)
                        put("to", rustTo)
                    },
                )
            }.onFailure {
                refreshPlaylistsInternal()
            }
        }
    }

    fun undoLastAction() {
        val action = pendingUndoAction ?: return
        pendingUndoAction = null
        _state.update { it.copy(error = null, undoActionLabel = null) }
        action()
    }

    // ── Catalog Library (Save Album / Follow Artist) ─────────────────────

    fun isDetailSavedInLibrary(browseId: String): Boolean =
        _state.value.libraryAlbums.any { it.id == browseId || it.browseId == browseId } ||
            _state.value.libraryArtists.any { it.id == browseId || it.browseId == browseId }

    fun toggleSaveDetailToLibrary(entry: DetailPageState) {
        val isArtist = entry.browseId.startsWith("UC") || entry.browseId.startsWith("MPLA")
        val kind = if (isArtist) "artist" else "album"
        val currentlySaved = isDetailSavedInLibrary(entry.browseId)

        scope.launch(Dispatchers.IO) {
            runCatching {
                if (currentlySaved) {
                    MusicoCore.call(
                        "library.remove",
                        buildJsonObject {
                            put("kind", kind)
                            put("id", entry.browseId)
                        },
                    )
                } else {
                    val thumb = entry.page?.header?.thumbnail
                        ?: entry.page?.shelves?.firstNotNullOfOrNull { s -> s.items.firstNotNullOfOrNull { it.thumbnail } }
                    val sub = entry.page?.header?.subtitle.orEmpty()
                    MusicoCore.call(
                        "library.put",
                        buildJsonObject {
                            put("kind", kind)
                            put("id", entry.browseId)
                            put("title", entry.title.ifBlank { entry.browseId })
                            put("subtitle", sub)
                            thumb?.let { put("thumbnail", it) }
                            put("browseId", entry.browseId)
                            entry.params?.let { put("params", it) }
                        },
                    )
                }
            }.onSuccess {
                refreshCatalogLibraryInternal()
                fail(
                    if (currentlySaved) "Removed from Library"
                    else if (isArtist) "Following ${entry.title}"
                    else "Saved ${entry.title} to Library",
                )
            }.onFailure { e -> fail(e.userMessage()) }
        }
    }

    fun openCatalogEntry(entry: CatalogEntry) {
        val bid = entry.browseId ?: entry.id
        openDetail(bid, entry.params, entry.title)
    }

    /** Batch-download any collection of tracks into the offline stream cache. */
    fun downloadTracksOffline(tracks: List<OnlineTrack>) {
        if (tracks.isEmpty() || _state.value.savingTotal > 0) return
        scope.launch(Dispatchers.IO) {
            var completed = 0
            tracks.forEachIndexed { idx, track ->
                _state.update { it.copy(savingTotal = tracks.size, savingDone = idx) }
                runCatching {
                    MusicoCore.call("track.resolve", resolveArgs(track, background = true))
                }.onSuccess { completed += 1 }
            }
            _state.update { it.copy(savingTotal = 0, savingDone = 0) }
            refreshDownloadsInternal()
            refreshSmartMixesInternal()
            fail("Saved $completed/${tracks.size} songs for offline playback")
        }
    }

    // ── Onboarding → personalized home ───────────────────────────────────

    fun finishOnboarding(languages: List<String>, artists: List<String>) {
        prefs.putBoolean(PREF_ONBOARDED, true)
        saveStringListPref(PREF_LANGS, languages)
        saveStringListPref(PREF_ARTISTS, artists)
        _state.update {
            it.copy(
                onboardingDone = true,
                savedLanguages = languages,
                savedArtists = artists,
                overlay = AppOverlay.NONE,
            )
        }
        if (languages.isNotEmpty() || artists.isNotEmpty()) {
            personalizeHome(languages, artists)
        }
    }

    private fun personalizeHome(languages: List<String>, artists: List<String>) {
        if (personalizeJob?.isActive == true) return
        personalizeJob = scope.launch {
            _state.update { it.copy(personalizing = true, personalizationError = null) }
            val shelves = mutableListOf<Shelf>()
            val failures = mutableListOf<String>()

            artists.take(4).forEach { artist ->
                var done = false
                runCatching {
                    val page = json.decodeFromJsonElement(
                        SearchResultPage.serializer(),
                        MusicoCore.call("online.search", buildJsonObject { put("query", artist) }),
                    )
                    for (seed in page.tracks.take(4)) {
                        val radio = runCatching {
                            json.decodeFromJsonElement(
                                RelatedReply.serializer(),
                                MusicoCore.call("online.related", buildJsonObject { put("videoId", seed.videoId) }),
                            )
                        }.getOrNull()?.tracks.orEmpty()
                        if (radio.size >= 4) {
                            shelves += Shelf(
                                title = "Because you like $artist",
                                items = radio.take(14).map { t ->
                                    ShelfItem(
                                        kind = ItemKind.SONG,
                                        title = t.title,
                                        subtitle = t.artist,
                                        thumbnail = t.thumbnail,
                                        videoId = t.videoId,
                                        durationSecs = t.durationSecs,
                                    )
                                },
                            )
                            done = true
                            break
                        }
                    }
                }
                if (!done) failures += artist
            }

            languages.take(3).forEach { lang ->
                runCatching {
                    val styles = json.decodeFromJsonElement(
                        GenresReply.serializer(),
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
                        shelves += Shelf(
                            title = "$lang picks",
                            items = songShelf.items.take(14),
                        )
                    } else {
                        failures += lang
                    }
                }
            }

            prefs.putString(
                PREF_PERSONAL_SHELVES,
                json.encodeToString(ListSerializer(Shelf.serializer()), shelves),
            )
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
                fail("Personalization failed — check Airplane Mode")
            }
        }
    }

    // ── Endless browsing: real continuation paging, then a genre chain ──

    private val curatedStyles = setOf(
        "Hindi", "Punjabi", "Tamil", "Telugu", "Malayalam", "Kannada", "Bengali", "Bhojpuri", "Gujarati", "Haryanvi", "Marathi",
        "Pop", "Indian pop", "Indie & alternative", "K-Pop", "J-Pop", "Latin", "Country & Americana", "Reggae & caribbean", "R&B & soul", "African", "Arabic", "Dance & electronic", "Rock",
        "Classical", "Carnatic classical", "Hindustani classical", "Decades", "Ghazal/sufi", "Devotional", "Folk & acoustic",
        "Hip-hop", "Desi hip-hop", "Metal", "Jazz", "Romance", "Party", "Chill", "Feel good", "Sad",
    )

    private fun styleAllowed(title: String): Boolean {
        val key = title.trim().lowercase()
        return curatedStyles.any { it.lowercase() == key }
    }

    private val kidsBlocklist = listOf("family", "kids", "kid ", "children", "nursery", "baby", "cocomelon", "cartoon", "lullaby", "rhyme")

    private fun styleNotKids(title: String): Boolean {
        val key = " ${title.trim().lowercase()} "
        return kidsBlocklist.none { key.contains(it.trim()) }
    }

    fun loadMoreHome() {
        if (_state.value.moreLoading) return
        scope.launch {
            _state.update { it.copy(moreLoading = true) }

            val cont = _state.value.homeContinuation
            if (cont != null) {
                runCatching {
                    json.decodeFromJsonElement(
                        BrowsePage.serializer(),
                        MusicoCore.call("online.browse", buildJsonObject { put("continuation", cont) }),
                    )
                }.fold(
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
                        GenresReply.serializer(),
                        MusicoCore.call("online.genres"),
                    )
                }.getOrNull()

                _state.update {
                    val existing = it.homeShelves.map { s -> s.title }.toSet()
                    val chartShelves = (charts?.shelves ?: emptyList()).filter { s -> s.title !in existing && s.items.isNotEmpty() }
                    val curated = (styles?.items ?: emptyList()).filter { s -> styleAllowed(s.title) }
                    it.copy(
                        homeShelves = it.homeShelves + chartShelves,
                        styleItems = curated,
                        styleQueue = curated.shuffled().filter { s -> s.browseId != null },
                        chartsAppended = true,
                        moreLoading = false,
                    )
                }
                return@launch
            }

            val queue = st.styleQueue
            if (queue.isEmpty()) {
                _state.update { it.copy(moreLoading = false, moreExhausted = true) }
                return@launch
            }
            val batch = queue.take(2)
            val fresh = mutableListOf<Shelf>()
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
        prefs.putBoolean(PREF_ONBOARDED, true)
        _state.update { it.copy(onboardingDone = true, overlay = AppOverlay.NONE) }
    }

    // ── Local prefs & Stale-While-Revalidate Home Cache ──────────────────

    private fun loadStringListPref(key: String): List<String> =
        prefs.getString(key, null)?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(String.serializer()), raw) }.getOrNull()
        } ?: emptyList()

    private fun saveStringListPref(key: String, values: List<String>) {
        prefs.putString(key, json.encodeToString(ListSerializer(String.serializer()), values))
    }

    private fun loadLocalPrefs() {
        val liked = loadStringListPref(PREF_LIKED_JSON).toSet()
        val recents = prefs.getString(PREF_RECENTS, null)?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(OnlineTrack.serializer()), raw) }.getOrNull()
        } ?: emptyList()
        val searches = loadStringListPref(PREF_SEARCHES)
        val cachedHome = prefs.getString(PREF_HOME_CACHE, null)?.let { raw ->
            runCatching { json.decodeFromString(ListSerializer(Shelf.serializer()), raw) }.getOrNull()
        } ?: emptyList()
        prefs.getString(PREF_LYRIC_OFFSETS, null)?.let { raw ->
            runCatching {
                json.decodeFromString(MapSerializer(String.serializer(), Long.serializer()), raw)
            }.getOrNull()?.let { map -> lyricOffsets.putAll(map) }
        }

        val savedVolume = prefs.getFloat(PREF_VOLUME, 1f).coerceIn(0f, 1f)
        val savedSpeed = prefs.getFloat(PREF_SPEED, 1f).let { if (it <= 0f) 1f else it }

        _state.update {
            it.copy(
                likedIds = liked,
                recentlyPlayed = recents,
                recentSearches = searches,
                homeShelves = cachedHome,
                lyricBackdrop = LyricBackdrop.fromId(prefs.getString(PREF_BACKDROP, null)),
                lyricsMode = LyricsDisplayMode.fromId(prefs.getString(PREF_LYRICS_MODE, null)),
                palette = Palette.fromId(prefs.getString(PREF_PALETTE, null)),
                displayName = prefs.getString(PREF_NAME, null) ?: it.displayName,
                onboardingDone = prefs.getBoolean(PREF_ONBOARDED, false),
                savedLanguages = loadStringListPref(PREF_LANGS),
                savedArtists = loadStringListPref(PREF_ARTISTS),
                personalizedShelves = prefs.getString(PREF_PERSONAL_SHELVES, null)?.let { raw ->
                    runCatching { json.decodeFromString(ListSerializer(Shelf.serializer()), raw) }.getOrNull()
                } ?: emptyList(),
                skipSilence = prefs.getBoolean(PREF_SKIP_SILENCE, false),
                playbackSpeed = savedSpeed,
                volume = savedVolume,
            )
        }
    }

    fun setLyricBackdrop(backdrop: LyricBackdrop) {
        prefs.putString(PREF_BACKDROP, backdrop.id)
        _state.update { it.copy(lyricBackdrop = backdrop) }
    }

    fun cycleLyricBackdrop() {
        val entries = LyricBackdrop.entries
        val next = entries[(entries.indexOf(_state.value.lyricBackdrop) + 1) % entries.size]
        setLyricBackdrop(next)
        fail("Backdrop: ${next.label}")
    }

    fun setLyricsMode(mode: LyricsDisplayMode) {
        prefs.putString(PREF_LYRICS_MODE, mode.id)
        _state.update { it.copy(lyricsMode = mode) }
    }

    fun setLyricOffsetForCurrent(offsetMs: Long) {
        val clamped = offsetMs.coerceIn(-5000L, 5000L)
        _state.update { it.copy(lyricOffsetMs = clamped) }
        val vid = _state.value.nowPlaying?.videoId ?: return
        if (clamped == 0L) lyricOffsets.remove(vid) else lyricOffsets[vid] = clamped
        while (lyricOffsets.size > 100) {
            val firstKey = lyricOffsets.keys.firstOrNull() ?: break
            lyricOffsets.remove(firstKey)
        }
        prefs.putString(
            PREF_LYRIC_OFFSETS,
            json.encodeToString(MapSerializer(String.serializer(), Long.serializer()), lyricOffsets),
        )
    }

    fun setPalette(palette: Palette) {
        prefs.putString(PREF_PALETTE, palette.id)
        _state.update { it.copy(palette = palette) }
    }

    fun setDisplayName(name: String) {
        prefs.putString(PREF_NAME, name)
        _state.update { it.copy(displayName = name) }
    }

    fun toggleLike(track: OnlineTrack) {
        val liked = _state.value.likedIds.toMutableSet()
        val nowLiked = track.videoId !in liked
        if (nowLiked) liked.add(track.videoId) else liked.remove(track.videoId)
        val optimisticTracks = if (nowLiked) {
            listOf(track) + _state.value.likedTracks.filterNot { it.videoId == track.videoId }
        } else {
            _state.value.likedTracks.filterNot { it.videoId == track.videoId }
        }
        saveStringListPref(PREF_LIKED_JSON, liked.toList())
        _state.update { it.copy(likedIds = liked, likedTracks = optimisticTracks) }

        scope.launch(Dispatchers.IO) {
            runCatching {
                MusicoCore.call(
                    "library.likes.set",
                    buildJsonObject {
                        put("track", trackToJson(track))
                        put("liked", nowLiked)
                    },
                )
            }.onSuccess {
                refreshLikesInternal()
                refreshSmartMixesInternal()
            }
        }
    }

    private fun pushRecentlyPlayed(track: OnlineTrack) {
        val rest = _state.value.recentlyPlayed.filterNot { it.videoId == track.videoId }
        val updated = (listOf(track) + rest).take(RECENTS_LIMIT)
        prefs.putString(PREF_RECENTS, json.encodeToString(ListSerializer(OnlineTrack.serializer()), updated))
        _state.update { it.copy(recentlyPlayed = updated) }
    }

    private fun pushRecentSearch(query: String) {
        val rest = _state.value.recentSearches.filterNot { it.equals(query, ignoreCase = true) }
        val updated = (listOf(query) + rest).take(SEARCHES_LIMIT)
        saveStringListPref(PREF_SEARCHES, updated)
        _state.update { it.copy(recentSearches = updated) }
    }

    fun clearRecentSearches() {
        saveStringListPref(PREF_SEARCHES, emptyList())
        _state.update { it.copy(recentSearches = emptyList()) }
    }

    // ── Core settings ────────────────────────────────────────────────────

    private fun loadCoreSettings() {
        scope.launch {
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

    fun saveCoreSetting(key: String, value: JsonElement) {
        scope.launch {
            _state.update { it.copy(settingsSaving = true) }
            runCatching {
                MusicoCore.call(
                    "settings.save",
                    buildJsonObject {
                        put("settings", buildJsonObject { put(key, value) })
                    },
                )
            }.onSuccess { _state.update { it.copy(settingsSaving = false) } }
                .onFailure { e ->
                    _state.update { it.copy(settingsSaving = false) }
                    fail(e.userMessage())
                }
            loadCoreSettings()
        }
    }

    // ── Stream cache (Settings) ──────────────────────────────────────────

    fun refreshCacheUsage() {
        scope.launch {
            runCatching {
                val reply = MusicoCore.call("online.cache.usage")
                reply["bytes"]?.toString()?.trim('"')?.toLongOrNull()
            }.onSuccess { bytes -> _state.update { it.copy(cacheBytes = bytes) } }
        }
    }

    fun clearStreamCache() {
        scope.launch {
            runCatching {
                val reply = MusicoCore.call("online.cache.clear")
                reply["freedBytes"]?.toString()?.trim('"')?.toLongOrNull()
            }.onSuccess { freed ->
                _state.update { it.copy(cacheCleared = freed, cacheBytes = 0L) }
                scope.launch(Dispatchers.IO) {
                    refreshDownloadsInternal()
                    refreshSmartMixesInternal()
                }
                fail("Cleared cache — ${(freed ?: 0) / (1024 * 1024)} MB freed")
            }
        }
    }

    fun setCacheCap(mb: Int) = saveCoreSetting("online_cache_mb", JsonPrimitive(mb))

    fun setStreamingQuality(quality: String) = saveCoreSetting("streaming_quality", JsonPrimitive(quality))

    // ── Navigation ───────────────────────────────────────────────────────

    fun selectTab(tab: AppTab) {
        _state.update { it.copy(tab = tab, overlay = AppOverlay.NONE) }
        when (tab) {
            AppTab.HOME -> if (_state.value.homeShelves.isEmpty() && !_state.value.homeLoading) loadHome()
            AppTab.RADIO -> if (_state.value.radioStyles.isEmpty() && !_state.value.radioLoading) loadRadio()
            AppTab.LIBRARY -> refreshLibraryAll()
            else -> Unit
        }
    }

    fun openOverlay(overlay: AppOverlay) {
        _state.update { it.copy(overlay = overlay) }
        if (overlay == AppOverlay.PROFILE) {
            scope.launch(Dispatchers.IO) { refreshListeningStatsInternal() }
        }
    }

    fun closeOverlay() = _state.update { it.copy(overlay = AppOverlay.NONE) }

    fun openPlayer(initialPage: Int = 0) {
        if (_state.value.nowPlaying == null) return
        _state.update { it.copy(overlay = AppOverlay.PLAYER, playerInitialPage = initialPage.coerceIn(0, 2)) }
    }

    // ── Home feed (Stale-While-Revalidate instant first paint) ───────────

    fun loadHome() {
        if (_state.value.homeLoading) return
        scope.launch {
            _state.update { it.copy(homeLoading = true, homeError = null) }
            runCatching {
                val reply = MusicoCore.call("online.home")
                json.decodeFromJsonElement(BrowsePage.serializer(), reply)
            }.fold(
                onSuccess = { page ->
                    if (page.shelves.isNotEmpty()) {
                        prefs.putString(
                            PREF_HOME_CACHE,
                            json.encodeToString(ListSerializer(Shelf.serializer()), page.shelves.take(10)),
                        )
                    }
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
        scope.launch {
            runCatching {
                val reply = MusicoCore.call(
                    "online.browse",
                    buildJsonObject {
                        put("browseId", browseId)
                        params?.let { put("params", it) }
                    },
                )
                json.decodeFromJsonElement(BrowsePage.serializer(), reply)
            }.fold(
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
        _state.update { it.copy(queue = queueTracks) }
        fail("Added ${tracks.size} songs to the queue")
    }

    fun saveDetailOffline(entry: DetailPageState) {
        val tracks = detailTracks(entry)
        if (tracks.isEmpty()) return
        val browseId = entry.browseId
        scope.launch {
            tracks.forEachIndexed { i, track ->
                _state.update {
                    it.copy(
                        details = it.details.map { d ->
                            if (d.browseId == browseId) d.copy(saving = true, saved = i)
                            else d
                        },
                        savingTotal = tracks.size,
                        savingDone = i,
                    )
                }
                runCatching {
                    MusicoCore.call("track.resolve", resolveArgs(track, background = true))
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
                )
            }
            scope.launch(Dispatchers.IO) {
                refreshDownloadsInternal()
                refreshSmartMixesInternal()
            }
            fail("Saved ${tracks.size} songs offline")
        }
    }

    private fun resolveArgs(track: OnlineTrack, background: Boolean = false) = buildJsonObject {
        put("videoId", track.videoId)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album)
        track.durationSecs?.let { put("durationSecs", it) }
        track.thumbnail?.let { put("thumbnail", it) }
        put("source", track.source)
        track.artistBrowseId?.let { put("artistBrowseId", it) }
        if (background) put("background", true)
    }

    private fun trackToJson(track: OnlineTrack) = buildJsonObject {
        put("videoId", track.videoId)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album)
        track.durationSecs?.let { put("durationSecs", it) }
        track.thumbnail?.let { put("thumbnail", it) }
        put("source", track.source)
        track.artistBrowseId?.let { put("artistBrowseId", it) }
    }

    // ── Radio tab ────────────────────────────────────────────────────────

    fun loadRadio() {
        if (_state.value.radioLoading) return
        scope.launch {
            _state.update { it.copy(radioLoading = true) }
            runCatching {
                json.decodeFromJsonElement(
                    GenresReply.serializer(),
                    MusicoCore.call("online.genres"),
                )
            }.fold(
                onSuccess = { styles ->
                    _state.update { it.copy(radioLoading = false, radioStyles = styles.items.filter { s -> styleNotKids(s.title) }) }
                },
                onFailure = { e ->
                    _state.update { it.copy(radioLoading = false) }
                    fail(e.userMessage())
                },
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
        suggestJob = scope.launch {
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

    fun searchArtists(query: String) {
        artistSearchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(artistResults = emptyList()) }
            return
        }
        artistSearchJob = scope.launch {
            delay(350)
            _state.update { it.copy(artistSearching = true) }
            runCatching {
                json.decodeFromJsonElement(
                    SearchResultPage.serializer(),
                    MusicoCore.call("online.search", buildJsonObject { put("query", query) }),
                )
            }.fold(
                onSuccess = { page -> _state.update { it.copy(artistSearching = false, artistResults = page.tracks.take(12)) } },
                onFailure = { _state.update { it.copy(artistSearching = false) } },
            )
        }
    }

    fun search(query: String = _state.value.searchQuery.trim()) {
        if (query.isEmpty()) return
        scope.launch {
            _state.update { it.copy(searching = true, searchQuery = query, searchSubmitted = query, error = null) }
            runCatching {
                val reply = MusicoCore.call(
                    "online.search",
                    buildJsonObject { put("query", query) },
                )
                json.decodeFromJsonElement(SearchResultPage.serializer(), reply)
            }.fold(
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
                    _state.update { it.copy(searching = false) }
                    fail(e.userMessage())
                },
            )
        }
    }

    // ── Playback, Closed-Loop Recommender & Gapless Queue ────────────────

    fun playQueue(queue: List<OnlineTrack>, index: Int) {
        if (queue.isEmpty() || index !in queue.indices) return
        flushTelemetry(completedNaturally = false)
        queueTracks = queue
        queuePosition = index
        _state.update { it.copy(queue = queue, queueIndex = index) }
        resolveAndPlay(queue[index])
    }

    fun playShuffled(tracks: List<OnlineTrack>) {
        if (tracks.isEmpty()) return
        playQueue(tracks.shuffled(), 0)
    }

    fun playTrack(track: OnlineTrack, context: List<OnlineTrack> = listOf(track)) {
        val index = context.indexOfFirst { it.videoId == track.videoId }.takeIf { it >= 0 } ?: 0
        playQueue(context.ifEmpty { listOf(track) }, index)
    }

    fun next() {
        flushTelemetry(completedNaturally = false)
        advance(1)
    }

    fun previous() {
        if (_state.value.positionMs > 3000) {
            scope.launch { playback.seekTo(0) }
        } else {
            flushTelemetry(completedNaturally = false)
            advance(-1)
        }
    }

    private fun advance(delta: Int) {
        val queue = queueTracks
        if (queue.isEmpty()) return
        var target = queuePosition + delta
        if (target >= queue.size) {
            if (!_state.value.repeatAll) {
                scope.launch { playback.seekTo(0) }
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
        flushTelemetry(completedNaturally = true)
        if (!_state.value.coreSettingsAutoplay && queuePosition >= queueTracks.size - 1) {
            return
        }
        advance(1)
    }

    private fun onGaplessTransition(videoId: String) {
        val nextIdx = queuePosition + 1
        if (nextIdx !in queueTracks.indices || queueTracks[nextIdx].videoId != videoId) {
            return
        }
        flushTelemetry(completedNaturally = true)
        queuePosition = nextIdx
        val nextTrack = queueTracks[nextIdx]
        _state.update {
            it.copy(
                queueIndex = nextIdx,
                resolvingId = null,
                lyricOffsetMs = lyricOffsets[nextTrack.videoId] ?: 0L,
            )
        }
        onTrackActivated(nextTrack)
    }

    fun toggleRepeat() = _state.update { it.copy(repeatAll = !it.repeatAll) }

    fun playFromQueue(index: Int) {
        if (index !in queueTracks.indices) return
        flushTelemetry(completedNaturally = false)
        queuePosition = index
        _state.update { it.copy(queueIndex = index) }
        resolveAndPlay(queueTracks[index])
    }

    fun removeFromQueue(index: Int) {
        if (index !in queueTracks.indices || index == queuePosition) return
        val mutable = queueTracks.toMutableList()
        mutable.removeAt(index)
        if (index < queuePosition) {
            queuePosition -= 1
        }
        queueTracks = mutable
        _state.update { it.copy(queue = queueTracks, queueIndex = queuePosition) }
        if (index == queuePosition + 1) {
            scope.launch { playback.clearStagedNext() }
            prefetchUpcoming()
        }
    }

    private fun resolveAndPlay(track: OnlineTrack) {
        playGeneration += 1L
        val gen = playGeneration
        resolveJob?.cancel()
        resolveJob = scope.launch {
            _state.update {
                it.copy(
                    resolvingId = track.videoId,
                    error = null,
                    lyricOffsetMs = lyricOffsets[track.videoId] ?: 0L,
                )
            }
            runCatching {
                val reply = MusicoCore.call("track.resolve", resolveArgs(track))
                json.decodeFromJsonElement(ResolvedTrack.serializer(), reply)
            }.fold(
                onSuccess = { resolved ->
                    if (!isActive || playGeneration != gen) return@fold
                    consecutiveAutoSkips = 0
                    _state.update { it.copy(resolvingId = null) }
                    playback.playFile(resolved.path, track)
                    onTrackActivated(track)
                },
                onFailure = { e ->
                    if (e is CancellationException) return@fold
                    if (!isActive || playGeneration != gen) return@fold
                    _state.update { it.copy(resolvingId = null) }
                    fail(e.userMessage())
                    maybeSkipFailed(track, e)
                },
            )
        }
    }

    private fun onTrackActivated(track: OnlineTrack) {
        telemetryTrack = track
        telemetryStartMs = currentTimeMillis()
        pushRecentlyPlayed(track)
        fetchLyrics(track)
        recordSongChanged(track)
        maybeExtendQueueWithRecommender()
        prefetchUpcoming()
    }

    private fun recordSongChanged(track: OnlineTrack) {
        scope.launch(Dispatchers.IO) {
            runCatching {
                MusicoCore.call(
                    "rec.on_song_changed",
                    buildJsonObject {
                        put("videoId", track.videoId)
                        put("track", trackToJson(track))
                    },
                )
            }
        }
    }

    private fun flushTelemetry(completedNaturally: Boolean) {
        val track = telemetryTrack ?: return
        val startedAt = telemetryStartMs
        telemetryTrack = null
        if (startedAt <= 0L) return

        val elapsedSecs = ((currentTimeMillis() - startedAt) / 1000L).coerceAtLeast(0L)
        val durSecs = (track.durationSecs ?: (_state.value.durationMs / 1000L)).coerceAtLeast(1L)
        val listenedSecs = if (completedNaturally) durSecs else elapsedSecs.coerceAtMost(durSecs)
        if (listenedSecs < 2L) return

        scope.launch(Dispatchers.IO) {
            runCatching {
                MusicoCore.call(
                    "rec.on_song_ended",
                    buildJsonObject {
                        put("videoId", track.videoId)
                        put("artist", track.artist)
                        put("listenedSecs", listenedSecs)
                        put("durationSecs", durSecs)
                    },
                )
            }.onSuccess {
                refreshSmartMixesInternal()
                refreshDownloadsInternal()
            }
        }
    }

    private fun maybeExtendQueueWithRecommender() {
        if (!_state.value.coreSettingsAutoplay || _state.value.coreSettingsOffline) return
        if (queueTracks.isEmpty() || queuePosition < queueTracks.size - 2) return
        if (autoplayExtendJob?.isActive == true) return
        val seed = queueTracks.lastOrNull() ?: return

        autoplayExtendJob = scope.launch(Dispatchers.IO) {
            val related = runCatching {
                json.decodeFromJsonElement(
                    RelatedReply.serializer(),
                    MusicoCore.call("online.related", buildJsonObject { put("videoId", seed.videoId) }),
                ).tracks
            }.getOrNull().orEmpty()
            if (related.isEmpty()) return@launch

            val existingIds = queueTracks.map { it.videoId }.toSet()
            val freshCandidates = related.filter { it.videoId !in existingIds }
            if (freshCandidates.isEmpty()) return@launch

            val ranked = runCatching {
                val reply = MusicoCore.call(
                    "rec.rerank_candidates",
                    buildJsonObject {
                        put("currentVideoId", seed.videoId)
                        put("topN", 12)
                        put(
                            "candidates",
                            buildJsonArray { freshCandidates.forEach { add(trackToJson(it)) } },
                        )
                    },
                )
                json.decodeFromJsonElement(RerankReply.serializer(), reply).tracks
            }.getOrElse { freshCandidates.take(10) }

            if (ranked.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    val stillIds = queueTracks.map { it.videoId }.toSet()
                    val toAppend = ranked.filter { it.videoId !in stillIds }
                    if (toAppend.isNotEmpty()) {
                        queueTracks = queueTracks + toAppend
                        _state.update { it.copy(queue = queueTracks) }
                        prefetchUpcoming()
                    }
                }
            }
        }
    }

    private fun maybeSkipFailed(failed: OnlineTrack, error: Throwable) {
        val message = error.userMessage()
        if (_state.value.coreSettingsOffline || message.contains("Offline", ignoreCase = true)) return
        if (queueTracks.size <= 1 || queuePosition !in queueTracks.indices) return
        if (queueTracks.getOrNull(queuePosition)?.videoId != failed.videoId) return
        if (consecutiveAutoSkips >= MAX_AUTO_SKIPS) {
            fail("Several songs in a row couldn't play — auto-skip stopped")
            return
        }
        consecutiveAutoSkips += 1
        skipFailedTrack(failed)
    }

    private fun prefetchUpcoming() {
        val currentId = queueTracks.getOrNull(queuePosition)?.videoId ?: return
        val upcoming = queueTracks.drop(queuePosition + 1).take(2)
        if (upcoming.isEmpty()) return

        prefetchJob?.cancel()
        prefetchJob = scope.launch(Dispatchers.IO) {
            upcoming.forEachIndexed { idx, track ->
                if (_state.value.nowPlaying?.videoId != currentId) return@launch

                if (idx == 0 && !track.thumbnail.isNullOrBlank()) {
                    launch {
                        runCatching {
                            prefetchArtworkImage(track.thumbnail)
                            ArtworkPaletteCache.extract(
                                track.thumbnail,
                                ArtworkAtmosphere(
                                    base = Color(0xFF0A0A14),
                                    orbPrimary = Color(0xFF7C5CFF),
                                    orbSecondary = Color(0xFF3B2A80),
                                    glow = Color(0xFF53F2C4),
                                ),
                            )
                        }
                    }
                    if (getCachedLyrics(track.videoId) == null && !_state.value.coreSettingsOffline) {
                        launch { fetchLyricsIntoCache(track) }
                    }
                }

                val resolved = runCatching {
                    val reply = MusicoCore.call("track.resolve", resolveArgs(track, background = true))
                    json.decodeFromJsonElement(ResolvedTrack.serializer(), reply)
                }.getOrNull()

                if (idx == 0 && resolved != null && _state.value.nowPlaying?.videoId == currentId) {
                    withContext(Dispatchers.Main) {
                        runCatching {
                            playback.stageNextFile(resolved.path, track)
                        }
                    }
                }
            }
        }
    }

    private fun skipFailedTrack(failed: OnlineTrack) {
        val queue = queueTracks
        if (queue.size <= 1 || queuePosition !in queue.indices) return
        queueTracks = queue.filterIndexed { i, _ -> i != queuePosition }
        queuePosition = queuePosition.coerceAtMost(queueTracks.size - 1)
        _state.update { it.copy(queue = queueTracks, queueIndex = queuePosition) }
        fail("Couldn't play “${failed.title.ifEmpty { "track" }}” — skipped to the next song")
        resolveAndPlay(queueTracks[queuePosition])
    }

    fun onTogglePlayback() {
        scope.launch { playback.togglePlayPause() }
    }

    fun seekTo(ms: Long) {
        scope.launch { playback.seekTo(ms.coerceAtLeast(0L)) }
    }

    fun seekBy(deltaMs: Long) {
        val dur = _state.value.durationMs
        val target = (_state.value.positionMs + deltaMs).coerceIn(0L, dur.coerceAtLeast(0L))
        seekTo(target)
    }

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        prefs.putFloat(PREF_VOLUME, clamped)
        _state.update { it.copy(volume = clamped) }
        scope.launch { playback.setVolume(clamped) }
    }

    fun toggleLikeCurrent() {
        _state.value.nowPlaying?.let { toggleLike(it) }
    }

    fun isLiked(track: OnlineTrack): Boolean = track.videoId in _state.value.likedIds

    // ── Long-press actions ───────────────────────────────────────────────

    fun playNext(track: OnlineTrack) {
        if (queueTracks.isEmpty() || _state.value.nowPlaying == null) {
            playTrack(track)
            return
        }
        val at = queuePosition + 1
        queueTracks = queueTracks.toMutableList().apply { add(at, track) }
        _state.update { it.copy(queue = queueTracks) }
        scope.launch { playback.clearStagedNext() }
        prefetchUpcoming()
        fail("Playing next: ${track.title.ifEmpty { "track" }}")
    }

    fun enqueue(track: OnlineTrack) {
        if (queueTracks.isEmpty() || _state.value.nowPlaying == null) {
            playTrack(track)
            return
        }
        queueTracks = queueTracks + track
        _state.update { it.copy(queue = queueTracks) }
        fail("Added to queue")
    }

    fun saveTrackOffline(track: OnlineTrack) {
        scope.launch {
            _state.update { it.copy(savingTrackId = track.videoId) }
            runCatching { MusicoCore.call("track.resolve", resolveArgs(track, background = true)) }
                .onSuccess {
                    _state.update { it.copy(savingTrackId = null) }
                    scope.launch(Dispatchers.IO) {
                        refreshDownloadsInternal()
                        refreshSmartMixesInternal()
                    }
                    fail("Saved offline: ${track.title.ifEmpty { "track" }}")
                }
                .onFailure { e ->
                    _state.update { it.copy(savingTrackId = null) }
                    fail(e.userMessage())
                }
        }
    }

    // ── Player niceties ──────────────────────────────────────────────────

    fun setSkipSilence(enabled: Boolean) {
        prefs.putBoolean(PREF_SKIP_SILENCE, enabled)
        _state.update { it.copy(skipSilence = enabled) }
        scope.launch { playback.setSkipSilence(enabled) }
    }

    fun setPlaybackSpeed(speed: Float) {
        prefs.putFloat(PREF_SPEED, speed)
        _state.update { it.copy(playbackSpeed = speed) }
        scope.launch { playback.setSpeed(speed) }
    }

    // ── Lyrics (LRU-cached for instant display on gapless/prefetched tracks) ──

    private fun getCachedLyrics(videoId: String): EnhancedLyrics? = synchronized(lyricCache) {
        lyricCache[videoId]
    }

    private fun putCachedLyrics(videoId: String, lyrics: EnhancedLyrics) = synchronized(lyricCache) {
        lyricCache[videoId] = lyrics
        while (lyricCache.size > 40) {
            val eldest = lyricCache.keys.firstOrNull() ?: break
            lyricCache.remove(eldest)
        }
    }

    private suspend fun fetchLyricsIntoCache(track: OnlineTrack): EnhancedLyrics? {
        return runCatching {
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
        }.getOrNull()?.takeIf { it.lines.isNotEmpty() }?.also {
            putCachedLyrics(track.videoId, it)
        }
    }

    private fun fetchLyrics(track: OnlineTrack) {
        getCachedLyrics(track.videoId)?.let { cached ->
            _state.update { it.copy(lyricsLoading = false, lyrics = cached) }
            return
        }
        scope.launch {
            _state.update { it.copy(lyricsLoading = true, lyrics = null) }
            val fetched = withContext(Dispatchers.IO) { fetchLyricsIntoCache(track) }
            if (_state.value.nowPlaying?.videoId == track.videoId) {
                _state.update { it.copy(lyricsLoading = false, lyrics = fetched) }
            }
        }
    }

    fun refreshLyrics() {
        _state.value.nowPlaying?.let {
            synchronized(lyricCache) { lyricCache.remove(it.videoId) }
            fetchLyrics(it)
        }
    }

    // ── In-app updater ───────────────────────────────────────────────────

    fun checkForUpdate() {
        if (_state.value.updateChecking || _state.value.updateDownloading) return
        scope.launch {
            _state.update { it.copy(updateChecking = true) }
            runCatching { updater.check() }.fold(
                onSuccess = { info ->
                    _state.update {
                        it.copy(updateChecking = false, updateInfo = info, updateDismissed = false)
                    }
                    announceUpdate(info)
                    tryStartRequestedDownload()
                },
                onFailure = {
                    _state.update { it.copy(updateChecking = false) }
                },
            )
        }
    }

    private fun announceUpdate(info: UpdateInfo) {
        if (!info.updateAvailable) return
        if (prefs.getString(PREF_NOTIFIED_UPDATE, null) == info.latest) return
        prefs.putString(PREF_NOTIFIED_UPDATE, info.latest)
        updater.notifyUpdateAvailable(info)
    }

    fun openFromUpdateNotification() {
        openUpdateRequest = true
        tryStartRequestedDownload()
    }

    private fun tryStartRequestedDownload() {
        if (!openUpdateRequest) return
        val info = _state.value.updateInfo ?: return
        openUpdateRequest = false
        if (info.updateAvailable) downloadAndInstallUpdate()
    }

    fun dismissUpdateBanner() = _state.update { it.copy(updateDismissed = true) }

    fun downloadAndInstallUpdate() {
        val info = _state.value.updateInfo ?: return
        if (_state.value.updateDownloading) return
        scope.launch {
            _state.update { it.copy(updateDownloading = true) }
            runCatching {
                updater.downloadAndInstall(info) { done, total ->
                    _state.update {
                        it.copy(
                            updateDownloadedMb = done / (1024 * 1024),
                            updateTotalMb = total / (1024 * 1024),
                        )
                    }
                }
            }.fold(
                onSuccess = {
                    _state.update { it.copy(updateDownloading = false) }
                },
                onFailure = { e ->
                    _state.update { it.copy(updateDownloading = false) }
                    fail("Update failed: ${e.message}")
                },
            )
        }
    }

    fun showToast(message: String) = fail(message)

    fun dismissError() {
        pendingUndoAction = null
        _state.update { it.copy(error = null, undoActionLabel = null) }
    }

    private fun showUndoableMessage(message: String, actionLabel: String) =
        _state.update { it.copy(error = message, undoActionLabel = actionLabel, errorSeq = it.errorSeq + 1) }

    private fun fail(message: String) {
        pendingUndoAction = null
        _state.update { it.copy(error = message, undoActionLabel = null, errorSeq = it.errorSeq + 1) }
    }

    private fun Throwable.userMessage(): String = (this as? CoreException)?.message ?: message ?: "unknown error"

    companion object {
        private const val PREF_LIKED_JSON = "liked_ids_json"
        private const val PREF_RECENTS = "recent_tracks"
        private const val PREF_SEARCHES = "recent_searches"
        private const val PREF_BACKDROP = "lyric_backdrop"
        private const val PREF_LYRICS_MODE = "lyrics_display_mode"
        private const val PREF_LYRIC_OFFSETS = "lyric_offsets_map"
        private const val PREF_PALETTE = "palette_id"
        private const val PREF_ONBOARDED = "onboarding_done"
        private const val PREF_LANGS = "onboarding_languages"
        private const val PREF_ARTISTS = "onboarding_artists"
        private const val PREF_SKIP_SILENCE = "skip_silence"
        private const val PREF_SPEED = "playback_speed"
        private const val PREF_VOLUME = "playback_volume"
        private const val PREF_PERSONAL_SHELVES = "personalized_shelves"
        private const val PREF_HOME_CACHE = "home_shelves_cache"
        private const val PREF_SLED_MIGRATED = "sled_library_migrated_v1"
        private const val PREF_NOTIFIED_UPDATE = "notified_update_version"
        private const val PREF_NAME = "display_name"
        private const val RECENTS_LIMIT = 40
        private const val SEARCHES_LIMIT = 8
        private const val MAX_AUTO_SKIPS = 3
    }
}

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
