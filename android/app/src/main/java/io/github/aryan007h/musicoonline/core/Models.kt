package io.github.aryan007h.musicoonline.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTOs mirroring the Rust core's serde shapes (src-tauri/src/online/
 * models.rs, snake_case on the wire). The Tauri frontend sends tracks back
 * verbatim over IPC — this app does the same over JNI, so resolve stays
 * source-aware ("" = YouTube, "saavn" = JioSaavn).
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

// ── Browse surfaces (Apple-Music-style home) ────────────────────────────────
// Mirror src-tauri/src/online/models.rs. Items carry their own navigation
// endpoints: videoId = playable song, browseId = detail page (album/artist/
// playlist), playlistId = radio context.

/**
 * Tolerant on purpose: the Rust side can add kinds (or add new ones we
 * haven't mapped) without killing the whole shelf decode — unmapped
 * strings degrade to UNKNOWN instead of throwing. Mirror of
 * src-tauri/src/online/models.rs ItemKind (snake_case on the wire).
 */
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

// ── App settings (mirror config.rs AppSettings — snake_case serde fields;
//    only the fields the mobile Settings page reads/writes are declared) ────

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
