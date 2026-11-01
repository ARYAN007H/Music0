//! JioSaavn catalog source (issue #54): independent search results + instant
//! 320 kbps direct-URL playback, so music keeps working when the YouTube-side
//! extraction stack is down.
//!
//! # API shape — live-verified 2026-11-01
//!
//! The planned saavn.dev wrapper (`/api/search/songs`, issue #54) is dead:
//! its project is discontinued (2026-06) and the domain no longer resolves
//! (NXDOMAIN via Google + Cloudflare DoH).  This module therefore talks to
//! the NATIVE web API every community wrapper fronts:
//!
//! - search:  `{BASE}?__call=search.getResults&q={q}&n={n}&_format=json&_marker=0&api_version=4&ctx=web6dot0`
//!   → `{ "results": [ { id, type: "song", title, subtitle, image, year,
//!       more_info: { album, duration: "204", "320kbps": "true",
//!       encrypted_media_url, artistMap: { primary_artists: [{ name }] } } } ] }`
//! - details: `{BASE}?__call=song.getDetails&pids={id}&…` → `{ "songs": [ …same song shape… ] }`
//! - media:   `more_info.encrypted_media_url` is base64(DES-ECB(key
//!   `"38346591"`, PKCS7)) → `https://aac.saavncdn.com/…_96.mp4`; the `_96`
//!   segment swaps to `_320` when `"320kbps"` is `"true"`.  Live check:
//!   Believer → HTTP 200, `audio/mp4`, 8.5 MB for 204 s.
//!
//! The "encryption" is a fixed public key shipped in every community client —
//! obfuscation, not a security boundary.  Unofficial upstream: expect shape
//! drift, so every field parses tolerantly (defaults + per-item skip +
//! string/number coercion) and titles arrive HTML-escaped (`&quot;`) —
//! decoded here.  Any failure degrades to "no saavn results" / an honest
//! resolve error; YT search and playback are never touched.
//!
//! `MUSICO_SAAVN_API` overrides the API base URL (proxy / self-hosted shim
//! speaking the same query interface) without a rebuild.
//!
//! Cache files land under stem `saavn:{id}` — the colon cannot appear in a
//! YT id, so [`stream::cached_file`]'s `{key}.` prefix matching can never
//! cross namespaces.  Linux-target app: `:` is a legal filename char here.

use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::{Arc, LazyLock, Mutex};
use std::time::{Duration, Instant};

use serde::Deserialize;

use super::models::OnlineTrack;
use super::stream;

/// Native JioSaavn web API (verified shape in the module docs).  Overridable
/// via `MUSICO_SAAVN_API` — see [`base_from`].
const API_BASE: &str = "https://www.jiosaavn.com/api.php";
/// Shared query params of the web client context (see module docs).
const API_PARAMS: &str = "_format=json&_marker=0&api_version=4&ctx=web6dot0";
/// DES-ECB key (8 ASCII bytes) of the fixed public "encryption".
const MEDIA_KEY: &[u8; 8] = b"38346591";
/// Source tag on every track this module produces (`""` stays YouTube).
pub(crate) const SOURCE: &str = "saavn";
/// Cap on the saavn block merged after the YT results in `online_search`.
pub const SEARCH_MERGE_LIMIT: usize = 10;
/// Metadata lookups are best-effort — 3 s total each (#54).
const API_TIMEOUT: Duration = Duration::from_secs(3);
/// Background resolve budget, mirroring stream.rs's 2 × 120 s total.
const RESOLVE_BUDGET: Duration = Duration::from_secs(240);
/// Interactive resolve budget, mirroring `stream::PLAY_RESOLVE_BUDGET`.
const PLAY_BUDGET: Duration = Duration::from_secs(90);
/// Download hard cap — 3× a full-length 320 kbps track (~10 MB); anything
/// past this is not a song.  Buffering whole-body means the cap is also the
/// worst-case RAM spike per resolve.
const MAX_AUDIO_BYTES: u64 = 32 * 1024 * 1024;

/// Dedicated client: saavn is an independent upstream — its failures must not
/// share connection-pool state with InnerTube traffic.  Per-request timeouts
/// are set on each call (3 s API / budget-bounded download).
static API_CLIENT: LazyLock<reqwest::blocking::Client> = LazyLock::new(|| {
    reqwest::blocking::Client::builder()
        .user_agent(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 \
             (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        )
        .build()
        .unwrap_or_else(|_| reqwest::blocking::Client::new())
});

/// API base with `MUSICO_SAAVN_API` override applied (trailing `/` trimmed
/// so both `https://host/api.php` and `https://host/api.php/` work).
fn api_base() -> String {
    base_from(std::env::var("MUSICO_SAAVN_API").ok().as_deref())
}

/// Pure override precedence — env beats the verified default; blank values
/// fall back to the default (an empty env var must not break playback).
fn base_from(env: Option<&str>) -> String {
    match env.map(str::trim).filter(|v| !v.is_empty()) {
        Some(v) => v.trim_end_matches('/').to_string(),
        None => API_BASE.to_string(),
    }
}

// ---------------------------------------------------------------------------
// Tolerant wire types — every field optional, strings coerce from numbers,
// unknown items are skipped one-by-one instead of failing the whole page.
// ---------------------------------------------------------------------------

fn opt_loose_string<'de, D: serde::Deserializer<'de>>(
    d: D,
) -> Result<Option<String>, D::Error> {
    Ok(match serde_json::Value::deserialize(d)? {
        serde_json::Value::String(s) => Some(s),
        serde_json::Value::Number(n) => Some(n.to_string()),
        _ => None,
    })
}

#[derive(Deserialize, Default)]
struct SearchJson {
    #[serde(default)]
    results: Vec<serde_json::Value>,
}

#[derive(Deserialize, Default)]
struct DetailsJson {
    #[serde(default)]
    songs: Vec<serde_json::Value>,
}

#[derive(Deserialize, Default)]
struct SongJson {
    #[serde(default, deserialize_with = "opt_loose_string")]
    id: Option<String>,
    #[serde(rename = "type", default, deserialize_with = "opt_loose_string")]
    kind: Option<String>,
    #[serde(default, deserialize_with = "opt_loose_string")]
    title: Option<String>,
    #[serde(default, deserialize_with = "opt_loose_string")]
    subtitle: Option<String>,
    #[serde(default, deserialize_with = "opt_loose_string")]
    image: Option<String>,
    #[serde(default)]
    more_info: Option<MoreInfoJson>,
}

#[derive(Deserialize, Default)]
struct MoreInfoJson {
    #[serde(default, deserialize_with = "opt_loose_string")]
    album: Option<String>,
    /// Seconds, arrives as a JSON string ("204") — numeric drift coerced.
    #[serde(default, deserialize_with = "opt_loose_string")]
    duration: Option<String>,
    /// "true"/"false" as a string.
    #[serde(
        default,
        rename = "320kbps",
        deserialize_with = "opt_loose_string"
    )]
    has_320: Option<String>,
    #[serde(
        default,
        rename = "encrypted_media_url",
        deserialize_with = "opt_loose_string"
    )]
    encrypted_media_url: Option<String>,
    #[serde(default, rename = "artistMap")]
    artist_map: Option<ArtistMapJson>,
}

#[derive(Deserialize, Default)]
struct ArtistMapJson {
    #[serde(default, rename = "primary_artists")]
    primary_artists: Vec<ArtistJson>,
}

#[derive(Deserialize, Default)]
struct ArtistJson {
    #[serde(default, deserialize_with = "opt_loose_string")]
    name: Option<String>,
}

// ---------------------------------------------------------------------------
// Search (merged into online_search's first page)
// ---------------------------------------------------------------------------

/// Searches JioSaavn songs.  Best-effort by contract: ANY failure (network,
/// timeout, parse, shape drift) returns an empty vec — saavn is a bonus
/// source merged after the YT results and must never poison them (#54).
pub fn search_songs(query: &str, limit: usize) -> Vec<OnlineTrack> {
    let q = query.trim();
    if q.is_empty() || limit == 0 {
        return Vec::new();
    }
    let url = format!("{}?__call=search.getResults&{API_PARAMS}", api_base());
    let n = limit.to_string();
    let resp = API_CLIENT
        .get(&url)
        .query(&[("q", q), ("n", n.as_str()), ("p", "1")])
        .timeout(API_TIMEOUT)
        .send();
    let body = match resp {
        Ok(r) if r.status().is_success() => r.text().ok(),
        _ => None,
    };
    let Some(body) = body else {
        return Vec::new();
    };
    let parsed: SearchJson = match serde_json::from_str(&body) {
        Ok(p) => p,
        Err(_) => return Vec::new(),
    };
    parsed
        .results
        .iter()
        .filter_map(|v| serde_json::from_value::<SongJson>(v.clone()).ok())
        .filter_map(song_to_track)
        .take(limit)
        .collect()
}

/// Maps one verified-shape song object to an [`OnlineTrack`], badge included.
/// Owns the song (no Clone plumbing) and rejects non-song rows / rows with
/// no usable id or title.
fn song_to_track(s: SongJson) -> Option<OnlineTrack> {
    if s.kind.as_deref().unwrap_or("song") != "song" {
        return None;
    }
    let id = s.id?.trim().to_string();
    if !valid_saavn_id(&id) {
        return None;
    }
    let title = unescape_html(s.title.as_deref().unwrap_or_default().trim());
    if title.is_empty() {
        return None;
    }
    let info = s.more_info.unwrap_or_default();
    // Primary artists first (the real credit); subtitle ("A - B") as fallback.
    let artist = info
        .artist_map
        .unwrap_or_default()
        .primary_artists
        .iter()
        .filter_map(|a| a.name.as_deref())
        .map(|n| unescape_html(n.trim()))
        .filter(|n| !n.is_empty())
        .collect::<Vec<_>>()
        .join(", ");
    let artist = if artist.is_empty() {
        unescape_html(s.subtitle.as_deref().unwrap_or_default())
    } else {
        artist
    };
    let track = OnlineTrack {
        video_id: id,
        title,
        artist,
        album: info
            .album
            .map(|a| unescape_html(a.trim()))
            .unwrap_or_default(),
        duration_secs: info
            .duration
            .as_deref()
            .and_then(|d| d.trim().parse::<u32>().ok()),
        thumbnail: s.image.as_deref().map(upscale_image),
        cached: false,
        source: SOURCE.to_string(),
        artist_browse_id: None,
    };
    Some(badge_cached(track))
}

/// Saavn ids become cache filenames (`saavn:{id}.mp4`), and they arrive both
/// from the untrusted API and verbatim from webview IPC — only a strict
/// charset may touch a path join.  `[A-Za-z0-9_-]` (same class as YT ids)
/// excludes `.`, `/`, and separators, so traversal is impossible.
fn valid_saavn_id(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 64
        && id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-')
}

/// Recomputes the instant-playback badge from the stream cache (`saavn:{id}`
/// namespace — a cached saavn track never crosses a YT cache entry).
fn badge_cached(mut track: OnlineTrack) -> OnlineTrack {
    track.cached = stream::cached_file(&track.song_id()).is_some();
    track
}

// ---------------------------------------------------------------------------
// Resolve — dispatched from stream::resolve_track for `source == "saavn"`
// ---------------------------------------------------------------------------

/// Resolve entry for saavn tracks: cache hit → details → decrypt → direct
/// download → size verify → `.part` atomic rename → prune → cover polish.
/// Same contract as the yt-dlp path, minus yt-dlp (the URL is direct).
pub(crate) fn resolve_track(track: &OnlineTrack, interactive: bool) -> Result<PathBuf, String> {
    let _lock = id_lock(&track.video_id);
    // The round-tripped track is as untrusted as the API row — the id never
    // reaches a path join unchecked (P1 review fix; `song_to_track` gates
    // the search side).
    if !valid_saavn_id(&track.video_id) {
        return Err("saavn resolve: invalid track id".into());
    }
    if let Some(path) = stream::cached_file(&track.song_id()) {
        return Ok(path);
    }
    // Art fetch shadows the download (same rule as stream::resolve_track):
    // spawned up front, joined before the Playing event so the cover is on
    // disk when the frontend looks.
    let cover = stream::CoverJob::spawn(&track.song_id(), track.thumbnail.as_deref());
    let deadline = Instant::now() + if interactive { PLAY_BUDGET } else { RESOLVE_BUDGET };

    let song = song_details(&track.video_id)?;
    let enc = song
        .more_info
        .as_ref()
        .and_then(|m| m.encrypted_media_url.as_deref())
        .map(str::trim)
        .filter(|e| !e.is_empty())
        .ok_or("saavn resolve: no encrypted media url")?;
    let base = decrypt_media_url(enc).ok_or("saavn resolve: media url decrypt failed")?;
    let has_320 = song.more_info.as_ref().and_then(|m| m.has_320.as_deref()) == Some("true");

    let mut last_err = String::new();
    for url in media_candidates(&base, has_320) {
        let remaining = deadline.saturating_duration_since(Instant::now());
        match fetch_audio(&url, remaining).and_then(|bytes| land_audio(track, &bytes)) {
            Ok(path) => {
                cover.join();
                return Ok(path);
            }
            Err(e) => last_err = e,
        }
    }
    Err(last_err)
}

/// Fetches song details by id (the resolve path — the [`OnlineTrack`] the
/// frontend round-trips carries only the id, not the encrypted URL).
fn song_details(id: &str) -> Result<SongJson, String> {
    let url = format!("{}?__call=song.getDetails&{API_PARAMS}", api_base());
    let resp = API_CLIENT
        .get(&url)
        .query(&[("pids", id)])
        .timeout(API_TIMEOUT)
        .send()
        .map_err(|e| format!("saavn details: {e}"))?;
    if !resp.status().is_success() {
        return Err(format!("saavn details: HTTP {}", resp.status()));
    }
    let text = resp.text().map_err(|e| format!("saavn details: {e}"))?;
    let parsed: DetailsJson =
        serde_json::from_str(&text).map_err(|e| format!("saavn details: shape drift ({e})"))?;
    parsed
        .songs
        .iter()
        .filter_map(|v| serde_json::from_value::<SongJson>(v.clone()).ok())
        .find(|s| s.id.as_deref().is_some_and(|sid| sid == id))
        .ok_or_else(|| "saavn details: song missing from response".into())
}

/// Decrypts `encrypted_media_url`: base64 → DES-ECB(`MEDIA_KEY`) with PKCS7
/// unpadding via the `ecb` crate → `https://aac.saavncdn.com/…_96.mp4`
/// (live-verified 2026-11-01).  Defense in depth: the plaintext must be an
/// https aac.saavncdn.com URL — it came from a third-party API and is about
/// to be fetched.
fn decrypt_media_url(enc: &str) -> Option<String> {
    use base64::Engine as _;
    use des::cipher::generic_array::GenericArray;
    use des::cipher::{BlockDecryptMut, KeyInit, block_padding::Pkcs7};

    type DesEcb = ecb::Decryptor<des::Des>;

    let mut buf = base64::engine::general_purpose::STANDARD
        .decode(enc.trim())
        .ok()?;
    if buf.is_empty() || buf.len() % 8 != 0 {
        return None;
    }
    let url = String::from_utf8(
        DesEcb::new(GenericArray::from_slice(MEDIA_KEY))
            .decrypt_padded_vec_mut::<Pkcs7>(&mut buf)
            .ok()?,
    )
    .ok()?;
    (url.starts_with("https://aac.saavncdn.com/") && url.contains("_96.")).then_some(url)
}

/// Bitrate ladder (#54: fall back, never guess) — the rungs `resolve_track`
/// tries, in order.  The `_320` substitution is anchored to the filename
/// suffix (the decrypt allowlist guarantees `_96.` in a `…_96.mp4` URL) so a
/// path segment that merely contains `_96` is never rewritten.  The
/// decrypted `_96` baseline is always a rung: some catalog entries declare
/// 320 but the CDN serves only 96.
fn media_candidates(base: &str, has_320: bool) -> Vec<String> {
    let mut candidates = Vec::new();
    if has_320 {
        candidates.push(base.replace("_96.mp4", "_320.mp4"));
    }
    candidates.push(base.to_string());
    candidates
}

/// Real fetcher: GET the direct CDN URL, size-capped, whole-body buffer
/// (a track is ~10 MB; streaming to disk adds failure modes the buffer
/// doesn't have).
fn fetch_audio(url: &str, timeout: Duration) -> Result<Vec<u8>, String> {
    let resp = API_CLIENT
        .get(url)
        .timeout(timeout)
        .send()
        .map_err(|e| format!("saavn download: {e}"))?;
    if !resp.status().is_success() {
        return Err(format!("saavn download: HTTP {}", resp.status()));
    }
    // A 200 with a non-audio body (bot wall, error page) ≥ MIN_FILE_BYTES
    // would otherwise land as a cached "track" forever — same gate as the
    // Piped/Invidious fast path.
    let ctype = resp
        .headers()
        .get(reqwest::header::CONTENT_TYPE)
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .to_ascii_lowercase();
    if !ctype.starts_with("audio/") {
        return Err(format!("saavn download: content-type `{ctype}` is not audio"));
    }
    // Cap BEFORE reading so a drifted/renamed URL can't buffer gigabytes.
    let len = resp.content_length().unwrap_or(0);
    if len > MAX_AUDIO_BYTES {
        return Err(format!("saavn download: {len} B exceeds the cap"));
    }
    use std::io::Read;
    let mut bytes = Vec::new();
    resp.take(MAX_AUDIO_BYTES)
        .read_to_end(&mut bytes)
        .map_err(|e| format!("saavn download: {e}"))?;
    Ok(bytes)
}

/// Verifies + lands the audio in the stream cache: `MIN_FILE_BYTES` check,
/// `.part` first → atomic rename (a killed resolve never leaves a
/// "complete" file short of bytes), prune.  Cover art is shadowed by
/// [`resolve_track`] (spawned before the download, joined on success).
/// Split from the network path so tests can land fixture bytes hermetically.
fn land_audio(track: &OnlineTrack, bytes: &[u8]) -> Result<PathBuf, String> {
    if (bytes.len() as u64) < stream::MIN_FILE_BYTES {
        return Err(format!(
            "saavn download suspiciously small ({} B)",
            bytes.len()
        ));
    }
    let dir = stream::cache_dir();
    std::fs::create_dir_all(&dir).map_err(|e| format!("cache dir: {e}"))?;
    let final_path = dir.join(format!("{}.mp4", track.song_id()));
    let part = dir.join(format!("{}.part", track.song_id()));
    std::fs::write(&part, bytes).map_err(|e| format!("saavn cache write: {e}"))?;
    std::fs::rename(&part, &final_path).map_err(|e| format!("saavn cache rename: {e}"))?;
    stream::prune_cache_to_cap();
    Ok(final_path)
}

/// Per-id download locks (the saavn twin of stream.rs's `video_lock`).
fn id_lock(id: &str) -> Arc<Mutex<()>> {
    static LOCKS: LazyLock<Mutex<HashMap<String, Arc<Mutex<()>>>>> =
        LazyLock::new(|| Mutex::new(HashMap::new()));
    let mut map = LOCKS.lock().unwrap_or_else(|e| e.into_inner());
    map.entry(id.to_string())
        .or_insert_with(|| Arc::new(Mutex::new(())))
        .clone()
}

// ---------------------------------------------------------------------------
// String helpers
// ---------------------------------------------------------------------------

/// JioSaavn HTML-escapes user-visible strings ("Believer&quot;") — the web
/// client renders them; we must decode.  `&amp;` LAST so escaped entities
/// survive one pass intact.
fn unescape_html(s: &str) -> String {
    s.replace("&quot;", "\"")
        .replace("&#039;", "'")
        .replace("&#x27;", "'")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
}

/// 150×150 search thumbs → 500×500 (the CDN serves any size in the URL).
fn upscale_image(url: &str) -> String {
    url.replace("150x150", "500x500")
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use crate::online::stream::test_support::TestCache;

    /// Real ciphertext captured live 2026-11-01 (Believer — Imagine Dragons),
    /// decrypts to the exact URL HEAD-verified at HTTP 200 / audio/mp4.
    const LIVE_CT: &str = "ID2ieOjCrwfgWvL5sXl4B1ImC5QfbsDy3oAkJDMbq8VkXOZU65DcFdrDhsTWIRT7u/8vMld9DHecVMFCBmD5/Rw7tS9a8Gtq";
    const LIVE_PT_96: &str = "https://aac.saavncdn.com/248/46944eb7b4b31f5b0abf5eb2e1be2d2a_96.mp4";

    #[test]
    fn decrypt_matches_live_verified_url() {
        assert_eq!(decrypt_media_url(LIVE_CT).as_deref(), Some(LIVE_PT_96));
    }

    #[test]
    fn decrypt_rejects_garbage() {
        assert!(decrypt_media_url("").is_none());
        assert!(decrypt_media_url("   ").is_none());
        assert!(decrypt_media_url("!!! not base64 !!!").is_none());
        // 5 bytes — not block-aligned.
        assert!(decrypt_media_url("c2hvcnQ=").is_none());
        // 19 bytes — also not block-aligned.
        assert!(decrypt_media_url("AAAAAAAAAAAAAAAAAAAAAAAAAA==").is_none());
    }

    #[test]
    fn decrypt_allowlist_rejects_non_saavncdn_plaintext() {
        // Valid PKCS7-padded DES-ECB ciphertext of a well-formed but
        // non-saavncdn URL — exercises the URL allowlist branch (the last
        // defense before the app fetches a third-party-chosen URL).
        use base64::Engine as _;
        use des::cipher::generic_array::GenericArray;
        use des::cipher::block_padding::Pkcs7;
        use des::cipher::{BlockEncryptMut, KeyInit};

        type DesEcbEnc = ecb::Encryptor<des::Des>;
        let plaintext = b"https://evil.example.com/a_96.mp4";
        let ct = DesEcbEnc::new(GenericArray::from_slice(MEDIA_KEY))
            .encrypt_padded_vec_mut::<Pkcs7>(plaintext);
        let enc = base64::engine::general_purpose::STANDARD.encode(ct);
        assert!(decrypt_media_url(&enc).is_none(), "allowlist must reject");
    }

    #[test]
    fn ids_outside_the_charset_never_resolve_or_map() {
        assert!(valid_saavn_id("BeXBcbVK"));
        assert!(valid_saavn_id("0W6DtW_N"));
        assert!(!valid_saavn_id(""));
        assert!(!valid_saavn_id("../evil"));
        assert!(!valid_saavn_id("a/b"));
        assert!(!valid_saavn_id("with space"));
        assert!(!valid_saavn_id("a.mp4"));
        // Search rows with hostile ids are dropped, not mapped.
        let row: SongJson = serde_json::from_str(
            r#"{"id":"../evil","type":"song","title":"T"}"#,
        )
        .expect("row parses");
        assert!(song_to_track(row).is_none());
        // And the resolve path rejects them even round-tripped from IPC.
        let bad = OnlineTrack {
            video_id: "../evil".into(),
            title: "T".into(),
            artist: String::new(),
            album: String::new(),
            duration_secs: None,
            thumbnail: None,
            cached: false,
            source: SOURCE.into(),
            artist_browse_id: None,
        };
        assert!(resolve_track(&bad, true).is_err());
    }

    #[test]
    fn env_overrides_base_url() {
        assert_eq!(base_from(Some("http://localhost:8080/")), "http://localhost:8080");
        assert_eq!(base_from(Some("  ")), API_BASE, "blank env = default");
        assert_eq!(base_from(None), API_BASE);
    }

    #[test]
    fn media_ladder_prefers_320_and_always_keeps_the_baseline() {
        let ladder = media_candidates(LIVE_PT_96, true);
        assert_eq!(ladder.len(), 2, "_320 rung + baseline");
        assert!(ladder[0].ends_with("_320.mp4"), "anchored substitution: {}", ladder[0]);
        assert_eq!(ladder[1], LIVE_PT_96);
        // Without a 320 declaration: baseline only.
        assert_eq!(media_candidates(LIVE_PT_96, false), vec![LIVE_PT_96.to_string()]);
    }

    #[test]
    fn search_parse_survives_drift_and_html_entities() {
        // Cache-touching via badge_cached → must hold the cache gate (#48).
        let _tc = TestCache::acquire();
        // Malformed rows (skipped, not fatal), numeric duration (coerced),
        // HTML entities (decoded), null artist name (dropped).
        let body = r#"{"results":[
            {"id":"a1","type":"song","title":"A &amp; B","image":"https://c.saavncdn.com/x/150x150.jpg",
             "more_info":{"duration":204,"album":"X &quot;Y&quot;","320kbps":"true",
                          "encrypted_media_url":"CT",
                          "artistMap":{"primary_artists":[{"name":"P1"},{"name":null}]}}},
            {"id":"r1","type":"radio","title":"mix"},
            {"garbage":true},
            42
        ]}"#;
        let parsed: SearchJson = serde_json::from_str(body).expect("outer parse");
        let tracks: Vec<OnlineTrack> = parsed
            .results
            .iter()
            .filter_map(|v| serde_json::from_value::<SongJson>(v.clone()).ok())
            .filter_map(song_to_track)
            .collect();
        assert_eq!(tracks.len(), 1, "only the valid song row survives");
        let t = &tracks[0];
        assert_eq!(t.video_id, "a1");
        assert_eq!(t.title, "A & B");
        assert_eq!(t.album, "X \"Y\"");
        assert_eq!(t.artist, "P1");
        assert_eq!(t.duration_secs, Some(204));
        assert_eq!(t.source, "saavn");
        assert_eq!(
            t.thumbnail.as_deref(),
            Some("https://c.saavncdn.com/x/500x500.jpg")
        );
        assert_eq!(t.song_id(), "saavn:a1");
    }

    #[test]
    fn resolve_lands_verified_file_in_cache() {
        let _tc = TestCache::acquire();
        let track = OnlineTrack {
            video_id: "BeXBcbVK".into(),
            title: "Believer".into(),
            artist: "Imagine Dragons".into(),
            album: String::new(),
            duration_secs: Some(204),
            thumbnail: None,
            cached: false,
            source: SOURCE.into(),
            artist_browse_id: None,
        };
        // Below MIN_FILE_BYTES → honest error, nothing on disk.
        assert!(land_audio(&track, &[0u8; 1024]).is_err());
        // Fixture bytes, no network: lands under the saavn: namespace.
        let path = land_audio(&track, &[0xA5u8; 64 * 1024]).expect("resolve lands");
        assert!(
            path.file_name().and_then(|n| n.to_str()) == Some("saavn:BeXBcbVK.mp4"),
            "wrong cache stem: {path:?}"
        );
        assert_eq!(stream::cached_file(&track.song_id()), Some(path.clone()));
        // No .part residue after the atomic rename.
        assert!(!stream::cache_dir().join("saavn:BeXBcbVK.part").exists());
    }

    /// Live end-to-end: search → resolve → playable file on disk.  Opt-in:
    ///   MUSICO_LIVE_TESTS=1 cargo test -p musico-online live_saavn -- --ignored --nocapture
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_saavn_search_and_resolve() {
        let _tc = TestCache::acquire();
        let tracks = search_songs("believer imagine dragons", SEARCH_MERGE_LIMIT);
        assert!(!tracks.is_empty(), "saavn search returned nothing");
        assert!(tracks.iter().all(|t| t.source == SOURCE), "source tag missing");
        let first = tracks.first().expect("checked non-empty");
        let path = resolve_track(first, true).expect("resolve");
        let size = std::fs::metadata(&path).map(|m| m.len()).unwrap_or(0);
        assert!(size >= 1024 * 1024, "320 kbps track suspiciously small: {size} B");
        println!(
            "saavn resolved {} → {} B ({} s metadata)",
            path.display(),
            size,
            first.duration_secs.unwrap_or(0)
        );
        let _ = std::fs::remove_file(&path);
    }
}
