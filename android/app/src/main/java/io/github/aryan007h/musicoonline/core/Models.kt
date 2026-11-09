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
