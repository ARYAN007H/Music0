package io.github.aryan007h.musicoonline.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs mirroring the Rust core's serde shapes (src-tauri/src/online/
 * models.rs, snake_case on the wire). Shared verbatim across Android and
 * Desktop JVM through Compose Multiplatform.
 */
@Serializable
data class OnlineTrack(
    @SerialName("video_id") val videoId: String,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    @SerialName("duration_secs") val durationSecs: Long? = null,
    val thumbnail: String? = null,
    val cached: Boolean = false,
    val source: String = "",
    @SerialName("artist_browse_id") val artistBrowseId: String? = null,
)

@Serializable
data class SearchResultPage(
    val tracks: List<OnlineTrack> = emptyList(),
    @SerialName("next_continuation") val nextContinuation: String? = null,
)

@Serializable
data class ResolvedTrack(
    val path: String,
    val songId: String,
)

@Serializable(with = ItemKindDeserializer::class)
enum class ItemKind { SONG, VIDEO, ALBUM, ARTIST, PLAYLIST, UNKNOWN }

object ItemKindDeserializer : kotlinx.serialization.KSerializer<ItemKind> {
    override val descriptor: kotlinx.serialization.descriptors.SerialDescriptor =
        kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
            "ItemKind",
            kotlinx.serialization.descriptors.PrimitiveKind.STRING,
        )

    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): ItemKind =
        runCatching {
            when (decoder.decodeString().lowercase()) {
                "song", "video" -> ItemKind.SONG
                "album", "single", "ep" -> ItemKind.ALBUM
                "artist", "profile" -> ItemKind.ARTIST
                "playlist", "genre" -> ItemKind.PLAYLIST
                else -> ItemKind.UNKNOWN
            }
        }.getOrDefault(ItemKind.UNKNOWN)

    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: ItemKind) {
        encoder.encodeString(value.name.lowercase())
    }
}

@Serializable
data class ShelfItem(
    val kind: ItemKind = ItemKind.SONG,
    val title: String = "",
    val subtitle: String = "",
    val thumbnail: String? = null,
    @SerialName("video_id") val videoId: String? = null,
    @SerialName("browse_id") val browseId: String? = null,
    val params: String? = null,
    @SerialName("playlist_id") val playlistId: String? = null,
    @SerialName("duration_secs") val durationSecs: Long? = null,
) {
    val isPlayable: Boolean get() = videoId != null
}

@Serializable
data class Shelf(
    val title: String = "",
    val items: List<ShelfItem> = emptyList(),
)

@Serializable
data class HeaderMeta(
    val title: String = "",
    val subtitle: String = "",
    val thumbnail: String? = null,
)

@Serializable
data class BrowsePage(
    val title: String = "",
    val shelves: List<Shelf> = emptyList(),
    @SerialName("next_continuation") val nextContinuation: String? = null,
    val header: HeaderMeta? = null,
)

@Serializable
data class SuggestionsReply(
    val suggestions: List<String> = emptyList(),
)

@Serializable
data class GenresReply(
    val items: List<ShelfItem> = emptyList(),
)

@Serializable
data class RelatedReply(
    val tracks: List<OnlineTrack> = emptyList(),
)

// ── Word-synced lyrics (mirror commands.rs EnhancedLyricsDto) ───────────────

@Serializable
data class LyricWord(
    val word: String = "",
    @SerialName("start_ms") val startMs: Long = 0,
    @SerialName("end_ms") val endMs: Long = 0,
)

@Serializable
data class LyricLine(
    @SerialName("time_secs") val timeSecs: Float = 0f,
    val text: String = "",
    val words: List<LyricWord> = emptyList(),
    val romanized: String? = null,
    val translation: String? = null,
)

@Serializable
data class EnhancedLyrics(
    @SerialName("track_id") val trackId: String = "",
    val synced: Boolean = false,
    val source: String = "",
    @SerialName("has_word_sync") val hasWordSync: Boolean = false,
    @SerialName("has_romanized") val hasRomanized: Boolean = false,
    @SerialName("has_translation") val hasTranslation: Boolean = false,
    val lines: List<LyricLine> = emptyList(),
)

// ── App settings (mirror config.rs AppSettings) ────────────────────────────

@Serializable
data class CoreSettings(
    val theme: String = "dark",
    val autoplay: Boolean = true,
    @SerialName("offline_mode") val offlineMode: Boolean = false,
    @SerialName("online_cache_mb") val onlineCacheMb: Int? = null,
    @SerialName("streaming_quality") val streamingQuality: String = "normal",
    @SerialName("download_quality") val downloadQuality: String = "high",
    @SerialName("gapless_playback") val gaplessPlayback: Boolean = true,
    @SerialName("predictive_prefetch") val predictivePrefetch: Boolean = true,
)

// ── User playlists (mirror musico_recommender::user_playlists) ─────────────

@Serializable
data class PlaylistTrack(
    val source: String = "yt",
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    @SerialName("duration_secs") val durationSecs: Long? = null,
    val thumbnail: String? = null,
    @SerialName("file_path") val filePath: String? = null,
) {
    fun toOnlineTrack(): OnlineTrack = OnlineTrack(
        videoId = id,
        title = title,
        artist = artist,
        album = album,
        durationSecs = durationSecs,
        thumbnail = thumbnail,
        cached = filePath != null,
        source = if (source == "yt") "" else source,
    )
}

@Serializable
data class UserPlaylist(
    val id: String = "",
    val name: String = "",
    @SerialName("created_at") val createdAt: Long = 0,
    @SerialName("modified_at") val modifiedAt: Long = 0,
    val tracks: List<PlaylistTrack> = emptyList(),
)

@Serializable
data class PlaylistsReply(
    val playlists: List<UserPlaylist> = emptyList(),
)

// ── Saved catalog library & metadata-complete likes / downloads ────────────

@Serializable
data class CatalogEntry(
    val id: String = "",
    val kind: String = "",
    val title: String = "",
    val subtitle: String = "",
    val thumbnail: String? = null,
    @SerialName("browse_id") val browseId: String? = null,
    val params: String? = null,
    @SerialName("video_id") val videoId: String? = null,
    @SerialName("added_at") val addedAt: Long = 0,
)

@Serializable
data class LibraryListReply(
    val entries: List<CatalogEntry> = emptyList(),
)

@Serializable
data class LikesListReply(
    val tracks: List<OnlineTrack> = emptyList(),
    val ids: List<String> = emptyList(),
)

@Serializable
data class DownloadedTrack(
    val track: OnlineTrack,
    val sizeBytes: Long = 0,
    val filePath: String = "",
)

@Serializable
data class DownloadsListReply(
    val downloads: List<DownloadedTrack> = emptyList(),
    val totalBytes: Long = 0,
)

// ── Hybrid recommender & Smart Mixes (mirror src-tauri/src/mobile.rs) ──────

@Serializable
data class SmartMix(
    val id: String = "",
    val title: String = "",
    val subtitle: String = "",
    val accent: String = "primary",
    val tracks: List<OnlineTrack> = emptyList(),
)

@Serializable
data class SmartMixesReply(
    val mixes: List<SmartMix> = emptyList(),
    val topArtists: List<String> = emptyList(),
)

@Serializable
data class RecommendationItem(
    val track: OnlineTrack,
    val score: Float = 0f,
    val signal: String = "",
)

@Serializable
data class RecommendationsReply(
    val recommendations: List<RecommendationItem> = emptyList(),
)

@Serializable
data class RerankReply(
    val tracks: List<OnlineTrack> = emptyList(),
)

@Serializable
data class ArtistAffinityStat(
    val artist: String = "",
    val affinity: Float = 0f,
    val plays: Int = 0,
)

@Serializable
data class ListeningStatsReply(
    val totalPlays: Int = 0,
    val totalListenedSecs: Long = 0,
    val totalSkips: Int = 0,
    val streakDays: Int = 0,
    val avgListenRatio: Float = 0f,
    val topArtists: List<ArtistAffinityStat> = emptyList(),
    val exploration: Float = 0.2f,
)

// ── App updater info (shared between Android APK & Desktop release checks) ─

@Serializable
data class UpdateInfo(
    val current: String,
    val latest: String,
    val updateAvailable: Boolean,
    val assetUrl: String = "",
    val assetName: String = "",
    val assetSizeBytes: Long = 0,
    val assetDigest: String? = null,
    val notes: String = "",
)
