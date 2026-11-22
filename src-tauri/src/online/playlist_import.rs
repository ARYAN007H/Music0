//! Minimal v1 playlist importer (T23):
//! - Exported files: Exportify / TuneMyMusic / Soundiiz CSV, Spotify / Music0 JSON, M3U / M3U8.
//! - Public playlist links: `open.spotify.com/playlist/...` (embed `__NEXT_DATA__`)
//!   and `music.apple.com/.../playlist/...` (`application/ld+json` `MusicPlaylist`).
//! - Catalog matcher: scores InnerTube candidates by normalized title, artist, and duration.
//!
//! Zero OAuth tokens required. URL fetching enforces strict HTTPS, exact host allowlist,
//! redirect-target validation, 10 s timeout, and a 4 MiB response body cap.

use std::collections::HashSet;
use std::io::Read;
use std::time::Duration;

use reqwest::redirect::Policy;
use serde::{Deserialize, Serialize};

use super::OnlineTrack;

pub const MAX_IMPORT_BYTES: usize = 4 * 1024 * 1024;
pub const MAX_IMPORT_TRACKS: usize = 500;

const URL_CONNECT_TIMEOUT: Duration = Duration::from_secs(5);
const URL_REQUEST_TIMEOUT: Duration = Duration::from_secs(10);
const USER_AGENT: &str =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct ImportTrack {
    pub title: String,
    pub artist: String,
    pub duration_secs: Option<u32>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ParsedPlaylist {
    pub name: String,
    pub tracks: Vec<ImportTrack>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct UnmatchedTrack {
    pub title: String,
    pub artist: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum PlaylistUrlTarget {
    Spotify { id: String },
    AppleMusic { url: String },
}

// ── File parsing (CSV, JSON, M3U/M3U8) ───────────────────────────────────────

pub fn parse_playlist_file(file_name: &str, content: &str) -> Result<ParsedPlaylist, String> {
    if content.len() > MAX_IMPORT_BYTES {
        return Err("Playlist file is too large (max 4 MB).".into());
    }
    let clean = content.trim_start_matches('\u{FEFF}').trim();
    if clean.is_empty() {
        return Err("The selected playlist file is empty.".into());
    }

    let default_name = derive_name_from_filename(file_name);
    let ext = file_name
        .rsplit('.')
        .next()
        .map(|s| s.trim().to_ascii_lowercase())
        .unwrap_or_default();

    let mut parsed = match ext.as_str() {
        "json" => parse_json_playlist(clean, &default_name)?,
        "m3u" | "m3u8" => parse_m3u_playlist(clean, &default_name)?,
        "csv" => parse_csv_playlist(clean, &default_name)?,
        _ => {
            if clean.starts_with('{') || clean.starts_with('[') {
                parse_json_playlist(clean, &default_name)?
            } else if clean.starts_with("#EXTM3U") || clean.contains("#EXTINF:") {
                parse_m3u_playlist(clean, &default_name)?
            } else {
                parse_csv_playlist(clean, &default_name)?
            }
        }
    };

    parsed.tracks.truncate(MAX_IMPORT_TRACKS);
    if parsed.tracks.is_empty() {
        return Err(
            "No tracks found in that file. Supported formats: Exportify/TuneMyMusic/Soundiiz CSV, Spotify/Music0 JSON, or M3U/M3U8."
                .into(),
        );
    }
    if parsed.name.trim().is_empty() {
        parsed.name = default_name;
    }
    Ok(parsed)
}

fn derive_name_from_filename(file_name: &str) -> String {
    let base = file_name
        .rsplit(['/', '\\'])
        .next()
        .unwrap_or(file_name)
        .trim();
    let stem = match base.rfind('.') {
        Some(idx) if idx > 0 => &base[..idx],
        _ => base,
    };
    let cleaned = stem.replace(['_', '-'], " ").split_whitespace().collect::<Vec<_>>().join(" ");
    if cleaned.is_empty() {
        "Imported Playlist".into()
    } else {
        cleaned.chars().take(120).collect()
    }
}

fn parse_csv_playlist(content: &str, default_name: &str) -> Result<ParsedPlaylist, String> {
    let rows = parse_rfc4180_csv(content);
    if rows.len() < 2 {
        return Err("CSV file needs a header row and at least one track.".into());
    }

    let headers: Vec<String> = rows[0]
        .iter()
        .map(|h| h.trim().trim_start_matches('\u{FEFF}').to_ascii_lowercase())
        .collect();

    let title_col = find_header(
        &headers,
        &[
            "track name",
            "title",
            "track",
            "song name",
            "song",
            "name",
            "track title",
        ],
    )
    .ok_or_else(|| {
        "Couldn't find a track title column in CSV (expected e.g. 'Track Name' or 'title').".to_string()
    })?;

    let artist_col = find_header(
        &headers,
        &[
            "artist name(s)",
            "artist name",
            "artist(s)",
            "artist",
            "artists",
            "track artist",
        ],
    );

    let duration_col = find_header(
        &headers,
        &[
            "duration (ms)",
            "duration_ms",
            "track duration (ms)",
            "duration_secs",
            "duration (s)",
            "duration",
            "length",
        ],
    );
    let duration_is_ms = duration_col
        .and_then(|idx| headers.get(idx))
        .map(|h| h.contains("ms"))
        .unwrap_or(false);

    let mut tracks = Vec::new();
    for row in rows.iter().skip(1) {
        if tracks.len() >= MAX_IMPORT_TRACKS {
            break;
        }
        let title = row.get(title_col).map(|s| s.trim()).unwrap_or("");
        if title.is_empty() {
            continue;
        }
        let artist = artist_col
            .and_then(|idx| row.get(idx))
            .map(|s| clean_artist_list(s))
            .unwrap_or_default();
        let duration_secs = duration_col
            .and_then(|idx| row.get(idx))
            .and_then(|s| parse_duration_value(s, duration_is_ms));

        tracks.push(ImportTrack {
            title: title.to_string(),
            artist,
            duration_secs,
        });
    }

    Ok(ParsedPlaylist {
        name: default_name.to_string(),
        tracks,
    })
}

fn find_header(headers: &[String], candidates: &[&str]) -> Option<usize> {
    for wanted in candidates {
        if let Some(pos) = headers.iter().position(|h| h == wanted) {
            return Some(pos);
        }
    }
    for wanted in candidates {
        if let Some(pos) = headers.iter().position(|h| h.contains(wanted)) {
            return Some(pos);
        }
    }
    None
}

fn clean_artist_list(raw: &str) -> String {
    let trimmed = raw.trim();
    if trimmed.contains(';') {
        trimmed
            .split(';')
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .collect::<Vec<_>>()
            .join(", ")
    } else {
        trimmed.to_string()
    }
}

fn parse_duration_value(raw: &str, prefer_ms: bool) -> Option<u32> {
    let s = raw.trim();
    if s.is_empty() {
        return None;
    }
    if s.contains(':') {
        let parts: Vec<u32> = s.split(':').filter_map(|p| p.trim().parse().ok()).collect();
        return match parts.as_slice() {
            [m, sec] => Some(m.saturating_mul(60).saturating_add(*sec)),
            [h, m, sec] => Some(
                h.saturating_mul(3600)
                    .saturating_add(m.saturating_mul(60))
                    .saturating_add(*sec),
            ),
            _ => None,
        };
    }
    let num: u64 = s.parse().ok()?;
    if num == 0 {
        return None;
    }
    if prefer_ms || num > 36_000 {
        Some((num / 1000).min(u32::MAX as u64) as u32)
    } else {
        Some(num.min(u32::MAX as u64) as u32)
    }
}

fn parse_rfc4180_csv(input: &str) -> Vec<Vec<String>> {
    let mut rows: Vec<Vec<String>> = Vec::new();
    let mut current_row: Vec<String> = Vec::new();
    let mut field = String::new();
    let mut in_quotes = false;
    let mut chars = input.chars().peekable();

    while let Some(c) = chars.next() {
        if in_quotes {
            if c == '"' {
                if chars.peek() == Some(&'"') {
                    chars.next();
                    field.push('"');
                } else {
                    in_quotes = false;
                }
            } else {
                field.push(c);
            }
        } else {
            match c {
                '"' if field.is_empty() => in_quotes = true,
                ',' => {
                    current_row.push(std::mem::take(&mut field));
                }
                '\r' => {
                    if chars.peek() == Some(&'\n') {
                        chars.next();
                    }
                    current_row.push(std::mem::take(&mut field));
                    if current_row.iter().any(|cell| !cell.trim().is_empty()) {
                        rows.push(std::mem::take(&mut current_row));
                    } else {
                        current_row.clear();
                    }
                }
                '\n' => {
                    current_row.push(std::mem::take(&mut field));
                    if current_row.iter().any(|cell| !cell.trim().is_empty()) {
                        rows.push(std::mem::take(&mut current_row));
                    } else {
                        current_row.clear();
                    }
                }
                _ => field.push(c),
            }
        }
    }

    if !field.is_empty() || !current_row.is_empty() {
        current_row.push(field);
        if current_row.iter().any(|cell| !cell.trim().is_empty()) {
            rows.push(current_row);
        }
    }

    rows
}

fn parse_json_playlist(content: &str, default_name: &str) -> Result<ParsedPlaylist, String> {
    let val: serde_json::Value =
        serde_json::from_str(content).map_err(|_| "Invalid JSON playlist file.".to_string())?;

    let mut name = default_name.to_string();
    let mut tracks = Vec::new();

    if let Some(arr) = val.as_array() {
        collect_json_tracks(arr, &mut tracks);
    } else if let Some(obj) = val.as_object() {
        // Spotify data-export envelope: { "playlists": [ { "name": "...", "items": [...] } ] }
        if let Some(first_pl) = obj
            .get("playlists")
            .and_then(|v| v.as_array())
            .and_then(|a| a.first())
        {
            if let Some(n) = first_pl.get("name").and_then(|v| v.as_str()).filter(|s| !s.trim().is_empty()) {
                name = n.trim().to_string();
            }
            if let Some(items) = first_pl
                .get("items")
                .or_else(|| first_pl.get("tracks"))
                .and_then(|v| v.as_array())
            {
                collect_json_tracks(items, &mut tracks);
            }
        } else {
            if let Some(n) = obj
                .get("name")
                .or_else(|| obj.get("title"))
                .and_then(|v| v.as_str())
                .filter(|s| !s.trim().is_empty())
            {
                name = n.trim().to_string();
            }
            if let Some(items) = obj
                .get("tracks")
                .or_else(|| obj.get("items"))
                .and_then(|v| v.as_array())
            {
                collect_json_tracks(items, &mut tracks);
            } else if let Some(items) = obj
                .get("tracks")
                .and_then(|t| t.get("items"))
                .and_then(|v| v.as_array())
            {
                collect_json_tracks(items, &mut tracks);
            }
        }
    }

    Ok(ParsedPlaylist { name, tracks })
}

fn collect_json_tracks(items: &[serde_json::Value], out: &mut Vec<ImportTrack>) {
    for raw_item in items {
        if out.len() >= MAX_IMPORT_TRACKS {
            break;
        }
        let item = raw_item.get("track").unwrap_or(raw_item);
        let Some(obj) = item.as_object() else {
            continue;
        };
        let title = obj
            .get("title")
            .or_else(|| obj.get("trackName"))
            .or_else(|| obj.get("name"))
            .and_then(|v| v.as_str())
            .map(str::trim)
            .unwrap_or("");
        if title.is_empty() {
            continue;
        }
        let artist = extract_json_artist(obj);
        let duration_secs = extract_json_duration(obj);
        out.push(ImportTrack {
            title: title.to_string(),
            artist,
            duration_secs,
        });
    }
}

fn extract_json_artist(obj: &serde_json::Map<String, serde_json::Value>) -> String {
    if let Some(s) = obj
        .get("artist")
        .or_else(|| obj.get("artistName"))
        .or_else(|| obj.get("subtitle"))
        .and_then(|v| v.as_str())
    {
        return clean_artist_list(s);
    }
    if let Some(arr) = obj.get("artists").and_then(|v| v.as_array()) {
        let names: Vec<String> = arr
            .iter()
            .filter_map(|a| {
                a.as_str()
                    .or_else(|| a.get("name").and_then(|n| n.as_str()))
                    .map(|s| s.trim().to_string())
            })
            .filter(|s| !s.is_empty())
            .collect();
        if !names.is_empty() {
            return names.join(", ");
        }
    }
    String::new()
}

fn extract_json_duration(obj: &serde_json::Map<String, serde_json::Value>) -> Option<u32> {
    if let Some(secs) = obj
        .get("durationSecs")
        .or_else(|| obj.get("duration_secs"))
        .and_then(|v| v.as_u64())
    {
        return (secs > 0).then(|| secs.min(u32::MAX as u64) as u32);
    }
    if let Some(ms) = obj
        .get("duration_ms")
        .or_else(|| obj.get("durationMs"))
        .and_then(|v| v.as_u64())
    {
        return (ms >= 1000).then(|| (ms / 1000).min(u32::MAX as u64) as u32);
    }
    if let Some(d) = obj.get("duration") {
        if let Some(n) = d.as_u64() {
            return parse_duration_value(&n.to_string(), false);
        }
        if let Some(s) = d.as_str() {
            return parse_duration_value(s, false).or_else(|| parse_iso8601_duration(s));
        }
    }
    None
}

fn parse_m3u_playlist(content: &str, default_name: &str) -> Result<ParsedPlaylist, String> {
    let mut name = default_name.to_string();
    let mut tracks = Vec::new();
    let mut pending_extinf: Option<(Option<u32>, String, String)> = None;

    for raw_line in content.lines() {
        if tracks.len() >= MAX_IMPORT_TRACKS {
            break;
        }
        let line = raw_line.trim();
        if line.is_empty() {
            continue;
        }
        if let Some(rest) = line.strip_prefix("#PLAYLIST:") {
            let trimmed = rest.trim();
            if !trimmed.is_empty() {
                name = trimmed.to_string();
            }
            continue;
        }
        if let Some(rest) = line.strip_prefix("#EXTINF:") {
            if let Some((dur_raw, label)) = rest.split_once(',') {
                let dur = dur_raw
                    .trim()
                    .split_whitespace()
                    .next()
                    .and_then(|n| n.parse::<i64>().ok())
                    .filter(|&n| n > 0)
                    .map(|n| n.min(u32::MAX as i64) as u32);
                let (artist, title) = split_artist_title(label.trim());
                if !title.is_empty() {
                    pending_extinf = Some((dur, artist, title));
                }
            }
            continue;
        }
        if line.starts_with('#') {
            continue;
        }
        if let Some((dur, artist, title)) = pending_extinf.take() {
            tracks.push(ImportTrack {
                title,
                artist,
                duration_secs: dur,
            });
        } else if !line.starts_with("http://") && !line.starts_with("https://") {
            let stem = derive_name_from_filename(line);
            let (artist, title) = split_artist_title(&stem);
            if !title.is_empty() {
                tracks.push(ImportTrack {
                    title,
                    artist,
                    duration_secs: None,
                });
            }
        }
    }

    if let Some((dur, artist, title)) = pending_extinf.take() {
        if tracks.len() < MAX_IMPORT_TRACKS {
            tracks.push(ImportTrack {
                title,
                artist,
                duration_secs: dur,
            });
        }
    }

    Ok(ParsedPlaylist { name, tracks })
}

fn split_artist_title(label: &str) -> (String, String) {
    for sep in [" - ", " – ", " — "] {
        if let Some((left, right)) = label.split_once(sep) {
            let a = left.trim();
            let t = right.trim();
            if !a.is_empty() && !t.is_empty() {
                return (a.to_string(), t.to_string());
            }
        }
    }
    (String::new(), label.trim().to_string())
}

// ── Public URL import (Spotify embed + Apple Music ld+json) ──────────────────

pub fn classify_playlist_url(raw_url: &str) -> Result<PlaylistUrlTarget, String> {
    let trimmed = raw_url.trim();
    if trimmed.is_empty() {
        return Err("Paste a public Spotify or Apple Music playlist URL.".into());
    }
    let parsed = reqwest::Url::parse(trimmed)
        .map_err(|_| "Enter a valid https:// playlist link from Spotify or Apple Music.".to_string())?;

    if parsed.scheme() != "https" {
        return Err("Only HTTPS playlist links from Spotify or Apple Music are supported.".into());
    }
    if !parsed.username().is_empty() || parsed.password().is_some() || parsed.port().is_some() {
        return Err("Invalid playlist URL.".into());
    }

    let host = parsed
        .host_str()
        .map(|h| h.to_ascii_lowercase())
        .unwrap_or_default();
    let segments: Vec<&str> = parsed
        .path_segments()
        .map(|s| s.filter(|seg| !seg.is_empty()).collect())
        .unwrap_or_default();

    match host.as_str() {
        "open.spotify.com" => {
            let pos = segments.iter().position(|&s| s == "playlist").ok_or_else(|| {
                "That Spotify link doesn't point to a playlist (expected open.spotify.com/playlist/...)."
                    .to_string()
            })?;
            let id = segments.get(pos + 1).copied().unwrap_or("");
            if !(10..=40).contains(&id.len()) || !id.chars().all(|c| c.is_ascii_alphanumeric()) {
                return Err("Invalid Spotify playlist ID in link.".into());
            }
            Ok(PlaylistUrlTarget::Spotify { id: id.to_string() })
        }
        "music.apple.com" => {
            let pos = segments.iter().position(|&s| s == "playlist").ok_or_else(|| {
                "That Apple Music link doesn't point to a playlist (expected music.apple.com/.../playlist/...)."
                    .to_string()
            })?;
            if segments.len() <= pos + 1 {
                return Err("Incomplete Apple Music playlist link.".into());
            }
            if segments
                .iter()
                .any(|s| s.chars().any(|c| c.is_control() || c.is_whitespace()))
            {
                return Err("Invalid Apple Music playlist URL.".into());
            }
            Ok(PlaylistUrlTarget::AppleMusic {
                url: format!("https://music.apple.com/{}", segments.join("/")),
            })
        }
        _ => Err(
            "Only public Spotify (open.spotify.com/playlist/…) and Apple Music (music.apple.com/…/playlist/…) links are supported."
                .into(),
        ),
    }
}

pub fn fetch_public_playlist_url(raw_url: &str) -> Result<ParsedPlaylist, String> {
    let target = classify_playlist_url(raw_url)?;
    let fetch_url = match &target {
        PlaylistUrlTarget::Spotify { id } => format!("https://open.spotify.com/embed/playlist/{id}"),
        PlaylistUrlTarget::AppleMusic { url } => url.clone(),
    };

    let redirect_policy = Policy::custom(|attempt| {
        if attempt.previous().len() >= 3 {
            return attempt.error("Too many redirects while fetching playlist.");
        }
        let u = attempt.url();
        let host_ok = matches!(
            u.host_str().map(|h| h.to_ascii_lowercase()).as_deref(),
            Some("open.spotify.com") | Some("music.apple.com")
        );
        if u.scheme() == "https" && host_ok && u.username().is_empty() && u.password().is_none() && u.port().is_none() {
            attempt.follow()
        } else {
            attempt.error("Blocked redirect to an unsupported host.")
        }
    });

    let client = reqwest::blocking::Client::builder()
        .connect_timeout(URL_CONNECT_TIMEOUT)
        .timeout(URL_REQUEST_TIMEOUT)
        .redirect(redirect_policy)
        .user_agent(USER_AGENT)
        .build()
        .map_err(|e| format!("HTTP client error: {e}"))?;

    let resp = client
        .get(&fetch_url)
        .header("Accept", "text/html,application/xhtml+xml")
        .header("Accept-Language", "en-US,en;q=0.9")
        .send()
        .map_err(|e| {
            if e.is_timeout() {
                "Timed out fetching playlist (10 s). Check your connection and try again.".into()
            } else {
                "Couldn't reach playlist URL. Check your connection and try again.".into()
            }
        })?;

    let status = resp.status();
    if status.as_u16() == 401 || status.as_u16() == 403 || status.as_u16() == 404 {
        return Err("This playlist is private or unavailable.".into());
    }
    if !status.is_success() {
        return Err(format!("Playlist page returned HTTP {}.", status.as_u16()));
    }

    let mut body = Vec::new();
    resp.take(MAX_IMPORT_BYTES as u64)
        .read_to_end(&mut body)
        .map_err(|_| "Failed reading playlist page.".to_string())?;
    let html = String::from_utf8_lossy(&body);

    let mut parsed = match target {
        PlaylistUrlTarget::Spotify { .. } => parse_spotify_embed_html(&html)?,
        PlaylistUrlTarget::AppleMusic { .. } => parse_apple_music_html(&html)?,
    };
    parsed.tracks.truncate(MAX_IMPORT_TRACKS);
    if parsed.tracks.is_empty() {
        return Err("This playlist is empty, private, or unavailable.".into());
    }
    Ok(parsed)
}

pub fn parse_spotify_embed_html(html: &str) -> Result<ParsedPlaylist, String> {
    let json_text = extract_script_by_marker(html, "__NEXT_DATA__").ok_or_else(|| {
        "Couldn't read that Spotify playlist — make sure the link is public.".to_string()
    })?;

    let root: serde_json::Value = serde_json::from_str(json_text).map_err(|_| {
        "Couldn't parse Spotify playlist data — the playlist may be private or unavailable."
            .to_string()
    })?;

    let entity = root
        .pointer("/props/pageProps/state/data/entity")
        .or_else(|| root.pointer("/props/pageProps/state/data/playlist"))
        .ok_or_else(|| "This Spotify playlist is private or unavailable.".to_string())?;

    let name = entity
        .get("name")
        .or_else(|| entity.get("title"))
        .and_then(|v| v.as_str())
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or("Spotify Playlist")
        .to_string();

    let track_list = entity
        .get("trackList")
        .and_then(|v| v.as_array())
        .ok_or_else(|| "This Spotify playlist is private or has no readable tracks.".to_string())?;

    let mut tracks = Vec::with_capacity(track_list.len().min(MAX_IMPORT_TRACKS));
    for item in track_list.iter().take(MAX_IMPORT_TRACKS) {
        let title = item
            .get("title")
            .or_else(|| item.get("name"))
            .and_then(|v| v.as_str())
            .map(str::trim)
            .unwrap_or("");
        if title.is_empty() {
            continue;
        }
        let artist = item
            .get("subtitle")
            .or_else(|| item.get("artist"))
            .and_then(|v| v.as_str())
            .map(|s| s.replace('\u{00A0}', " ").trim().to_string())
            .unwrap_or_default();
        let duration_secs = item
            .get("duration")
            .and_then(|v| v.as_u64())
            .filter(|&ms| ms >= 1000)
            .map(|ms| (ms / 1000).min(u32::MAX as u64) as u32);
        tracks.push(ImportTrack {
            title: title.to_string(),
            artist,
            duration_secs,
        });
    }

    if tracks.is_empty() {
        return Err("This Spotify playlist is private or empty.".into());
    }
    Ok(ParsedPlaylist { name, tracks })
}

pub fn parse_apple_music_html(html: &str) -> Result<ParsedPlaylist, String> {
    let lower = html.to_ascii_lowercase();
    let mut cursor = 0usize;

    while let Some(rel_start) = lower[cursor..].find("<script") {
        let tag_start = cursor + rel_start;
        let Some(rel_open_end) = lower[tag_start..].find('>') else {
            break;
        };
        let open_end = tag_start + rel_open_end;
        let open_tag = &lower[tag_start..open_end];
        let content_start = open_end + 1;
        let Some(rel_close) = lower[content_start..].find("</script>") else {
            break;
        };
        let content_end = content_start + rel_close;
        cursor = content_end + "</script>".len();

        if !open_tag.contains("ld+json") {
            continue;
        }

        let raw_json = html[content_start..content_end].trim();
        let Ok(val) = serde_json::from_str::<serde_json::Value>(raw_json) else {
            continue;
        };

        if let Some(parsed) = extract_ldjson_music_playlist(&val) {
            return Ok(parsed);
        }
    }

    Err("Couldn't read that Apple Music playlist — make sure the playlist is public.".into())
}

fn extract_ldjson_music_playlist(val: &serde_json::Value) -> Option<ParsedPlaylist> {
    if is_music_playlist_type(val) {
        return parse_ldjson_playlist_node(val);
    }
    if let Some(arr) = val.as_array() {
        for node in arr {
            if let Some(p) = extract_ldjson_music_playlist(node) {
                return Some(p);
            }
        }
    }
    if let Some(graph) = val.get("@graph").and_then(|v| v.as_array()) {
        for node in graph {
            if let Some(p) = extract_ldjson_music_playlist(node) {
                return Some(p);
            }
        }
    }
    None
}

fn is_music_playlist_type(val: &serde_json::Value) -> bool {
    match val.get("@type") {
        Some(serde_json::Value::String(s)) => s.eq_ignore_ascii_case("MusicPlaylist"),
        Some(serde_json::Value::Array(arr)) => arr
            .iter()
            .any(|v| v.as_str().is_some_and(|s| s.eq_ignore_ascii_case("MusicPlaylist"))),
        _ => false,
    }
}

fn parse_ldjson_playlist_node(node: &serde_json::Value) -> Option<ParsedPlaylist> {
    let name = node
        .get("name")
        .and_then(|v| v.as_str())
        .map(str::trim)
        .filter(|s| !s.is_empty())
        .unwrap_or("Apple Music Playlist")
        .to_string();

    let raw_tracks = node.get("track").and_then(|v| v.as_array())?;
    let mut tracks = Vec::with_capacity(raw_tracks.len().min(MAX_IMPORT_TRACKS));
    for item in raw_tracks.iter().take(MAX_IMPORT_TRACKS) {
        let title = item
            .get("name")
            .and_then(|v| v.as_str())
            .map(str::trim)
            .unwrap_or("");
        if title.is_empty() {
            continue;
        }
        let artist = extract_ldjson_artist(item.get("byArtist"));
        let duration_secs = item
            .get("duration")
            .and_then(|v| v.as_str())
            .and_then(parse_iso8601_duration);
        tracks.push(ImportTrack {
            title: title.to_string(),
            artist,
            duration_secs,
        });
    }
    if tracks.is_empty() {
        return None;
    }
    Some(ParsedPlaylist { name, tracks })
}

fn extract_ldjson_artist(val: Option<&serde_json::Value>) -> String {
    match val {
        Some(serde_json::Value::String(s)) => s.trim().to_string(),
        Some(serde_json::Value::Object(obj)) => obj
            .get("name")
            .and_then(|v| v.as_str())
            .map(str::trim)
            .unwrap_or("")
            .to_string(),
        Some(serde_json::Value::Array(arr)) => arr
            .iter()
            .filter_map(|v| {
                v.as_str()
                    .or_else(|| v.get("name").and_then(|n| n.as_str()))
                    .map(|s| s.trim().to_string())
            })
            .filter(|s| !s.is_empty())
            .collect::<Vec<_>>()
            .join(", "),
        _ => String::new(),
    }
}

fn extract_script_by_marker<'a>(html: &'a str, marker: &str) -> Option<&'a str> {
    let lower = html.to_ascii_lowercase();
    let needle = marker.to_ascii_lowercase();
    let mut cursor = 0usize;

    while let Some(rel_start) = lower[cursor..].find("<script") {
        let tag_start = cursor + rel_start;
        let rel_open_end = lower[tag_start..].find('>')?;
        let open_end = tag_start + rel_open_end;
        let open_tag = &lower[tag_start..open_end];
        let content_start = open_end + 1;
        let rel_close = lower[content_start..].find("</script>")?;
        let content_end = content_start + rel_close;
        cursor = content_end + "</script>".len();

        if open_tag.contains(&needle) {
            return Some(html[content_start..content_end].trim());
        }
    }
    None
}

pub fn parse_iso8601_duration(raw: &str) -> Option<u32> {
    let s = raw.trim().to_ascii_uppercase();
    let rest = s.strip_prefix("PT")?;
    let mut total: u32 = 0;
    let mut num: u32 = 0;
    let mut seen_digit = false;
    let mut matched_any = false;

    for ch in rest.chars() {
        if let Some(d) = ch.to_digit(10) {
            num = num.saturating_mul(10).saturating_add(d);
            seen_digit = true;
        } else if seen_digit {
            match ch {
                'H' => total = total.saturating_add(num.saturating_mul(3600)),
                'M' => total = total.saturating_add(num.saturating_mul(60)),
                'S' => total = total.saturating_add(num),
                _ => return None,
            }
            num = 0;
            seen_digit = false;
            matched_any = true;
        } else {
            return None;
        }
    }

    (matched_any && total > 0).then_some(total)
}

// ── Catalog candidate matcher ────────────────────────────────────────────────

pub fn pick_best_match(wanted: &ImportTrack, candidates: &[OnlineTrack]) -> Option<OnlineTrack> {
    let wanted_title_norm = normalize_title(&wanted.title);
    let wanted_title_tokens = tokenize(&wanted_title_norm);
    if wanted_title_tokens.is_empty() {
        return None;
    }
    let wanted_artist_tokens = tokenize(&wanted.artist);

    let mut best: Option<(f32, &OnlineTrack)> = None;
    for cand in candidates.iter().take(12) {
        let cand_title_norm = normalize_title(&cand.title);
        let cand_title_tokens = tokenize(&cand_title_norm);
        if cand_title_tokens.is_empty() {
            continue;
        }

        let mut title_score = token_f1(&wanted_title_tokens, &cand_title_tokens);
        if wanted_title_norm == cand_title_norm {
            title_score = 1.0;
        } else if cand_title_norm.contains(&wanted_title_norm)
            || wanted_title_norm.contains(&cand_title_norm)
        {
            title_score = title_score.max(0.82);
        }

        if title_score < 0.35 {
            continue;
        }

        let artist_score = if wanted_artist_tokens.is_empty() {
            if title_score >= 0.70 { 0.75 } else { 0.30 }
        } else {
            let cand_artist_tokens = tokenize(&cand.artist);
            let combined_cand: HashSet<String> = cand_artist_tokens
                .union(&cand_title_tokens)
                .cloned()
                .collect();
            let direct = token_f1(&wanted_artist_tokens, &cand_artist_tokens);
            let recall_in_combined = wanted_artist_tokens
                .iter()
                .filter(|t| combined_cand.contains(*t))
                .count() as f32
                / wanted_artist_tokens.len() as f32;
            direct.max(recall_in_combined * 0.90)
        };

        // If the user gave an artist and zero artist tokens match at all, require
        // a very strong title match before accepting.
        if !wanted_artist_tokens.is_empty() && artist_score < 0.10 && title_score < 0.90 {
            continue;
        }

        let duration_bonus = match (wanted.duration_secs, cand.duration_secs) {
            (Some(w), Some(c)) if w > 0 && c > 0 => {
                let diff = w.abs_diff(c);
                if diff <= 5 {
                    0.10
                } else if diff <= 15 {
                    0.06
                } else if diff <= 30 {
                    0.02
                } else if diff > 90 {
                    -0.12
                } else {
                    0.0
                }
            }
            _ => 0.0,
        };

        let total = title_score * 0.62 + artist_score * 0.28 + duration_bonus;
        if total >= 0.48 && best.map_or(true, |(s, _)| total > s) {
            best = Some((total, cand));
        }
    }

    best.map(|(_, track)| track.clone())
}

fn normalize_title(raw: &str) -> String {
    let mut out = String::with_capacity(raw.len());
    let mut paren_depth: usize = 0;
    let mut bracket_depth: usize = 0;
    for ch in raw.chars() {
        match ch {
            '(' => paren_depth += 1,
            ')' => paren_depth = paren_depth.saturating_sub(1),
            '[' => bracket_depth += 1,
            ']' => bracket_depth = bracket_depth.saturating_sub(1),
            _ if paren_depth == 0 && bracket_depth == 0 => out.push(ch),
            _ => {}
        }
    }
    let trimmed = out.trim();
    let base = if trimmed.is_empty() { raw } else { trimmed };
    tokenize(base).into_iter().collect::<Vec<_>>().join(" ")
}

fn tokenize(raw: &str) -> HashSet<String> {
    raw.to_lowercase()
        .split(|c: char| !c.is_alphanumeric())
        .filter(|t| !t.is_empty())
        .filter(|t| !matches!(*t, "feat" | "ft" | "official" | "video" | "audio" | "lyrics" | "lyric"))
        .map(str::to_string)
        .collect()
}

fn token_f1(a: &HashSet<String>, b: &HashSet<String>) -> f32 {
    if a.is_empty() || b.is_empty() {
        return 0.0;
    }
    let inter = a.intersection(b).count() as f32;
    if inter == 0.0 {
        return 0.0;
    }
    (2.0 * inter) / (a.len() as f32 + b.len() as f32)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn mk_track(id: &str, title: &str, artist: &str, duration_secs: Option<u32>) -> OnlineTrack {
        OnlineTrack {
            video_id: id.into(),
            title: title.into(),
            artist: artist.into(),
            album: String::new(),
            duration_secs,
            thumbnail: None,
            cached: false,
            source: String::new(),
            artist_browse_id: None,
        }
    }

    #[test]
    fn parses_exportify_tunemymusic_and_soundiiz_csv() {
        let exportify = "\u{FEFF}Track URI,Track Name,Artist Name(s),Album Name,Duration (ms)\n\
spotify:track:1,\"Blinding Lights\",\"The Weeknd\",After Hours,200040\n\
spotify:track:2,\"Hello, World (feat. ""Guest"")\",\"Daft Punk; Pharrell Williams\",RAM,245000\n";
        let pl = parse_playlist_file("Late_Night_Drives.csv", exportify).expect("exportify csv");
        assert_eq!(pl.name, "Late Night Drives");
        assert_eq!(pl.tracks.len(), 2);
        assert_eq!(pl.tracks[0].title, "Blinding Lights");
        assert_eq!(pl.tracks[0].artist, "The Weeknd");
        assert_eq!(pl.tracks[0].duration_secs, Some(200));
        assert_eq!(pl.tracks[1].title, "Hello, World (feat. \"Guest\")");
        assert_eq!(pl.tracks[1].artist, "Daft Punk, Pharrell Williams");

        let soundiiz = "title,artist,duration\nStarboy,The Weeknd,3:50\n";
        let pl2 = parse_playlist_file("mix.csv", soundiiz).expect("soundiiz csv");
        assert_eq!(pl2.tracks[0].duration_secs, Some(230));
    }

    #[test]
    fn parses_music0_and_spotify_json() {
        let music0_json = r#"{
            "name": "Gym PRs",
            "tracks": [
                { "title": "Till I Collapse", "artist": "Eminem", "durationSecs": 297 }
            ]
        }"#;
        let pl = parse_playlist_file("gym.json", music0_json).expect("music0 json");
        assert_eq!(pl.name, "Gym PRs");
        assert_eq!(pl.tracks.len(), 1);
        assert_eq!(pl.tracks[0].duration_secs, Some(297));

        let spotify_export = r#"{
            "playlists": [{
                "name": "Roadtrip",
                "items": [{
                    "track": { "trackName": "Midnight City", "artistName": "M83" }
                }]
            }]
        }"#;
        let pl2 = parse_playlist_file("Playlist1.json", spotify_export).expect("spotify json");
        assert_eq!(pl2.name, "Roadtrip");
        assert_eq!(pl2.tracks[0].title, "Midnight City");
        assert_eq!(pl2.tracks[0].artist, "M83");
    }

    #[test]
    fn parses_m3u8_with_playlist_name_and_extinf() {
        let m3u = "#EXTM3U\n#PLAYLIST:Chill Lo-Fi\n#EXTINF:192,Nujabes - Feather\n/music/feather.mp3\n#EXTINF:215,Luv(sic)\n/music/luvsic.mp3\n";
        let pl = parse_playlist_file("chill.m3u8", m3u).expect("m3u8");
        assert_eq!(pl.name, "Chill Lo-Fi");
        assert_eq!(pl.tracks.len(), 2);
        assert_eq!(pl.tracks[0].artist, "Nujabes");
        assert_eq!(pl.tracks[0].title, "Feather");
        assert_eq!(pl.tracks[0].duration_secs, Some(192));
        assert_eq!(pl.tracks[1].artist, "");
        assert_eq!(pl.tracks[1].title, "Luv(sic)");
    }

    #[test]
    fn classifies_allowed_urls_and_rejects_ssrf_attempts() {
        assert_eq!(
            classify_playlist_url("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc")
                .expect("spotify"),
            PlaylistUrlTarget::Spotify {
                id: "37i9dQZF1DXcBWIGoYBM5M".into(),
            }
        );
        assert_eq!(
            classify_playlist_url("https://open.spotify.com/intl-en/playlist/37i9dQZF1DXcBWIGoYBM5M")
                .expect("spotify intl"),
            PlaylistUrlTarget::Spotify {
                id: "37i9dQZF1DXcBWIGoYBM5M".into(),
            }
        );
        assert_eq!(
            classify_playlist_url("https://music.apple.com/us/playlist/todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb?l=en")
                .expect("apple"),
            PlaylistUrlTarget::AppleMusic {
                url: "https://music.apple.com/us/playlist/todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb".into(),
            }
        );

        // SSRF & unsupported hosts must be rejected cleanly.
        assert!(classify_playlist_url("http://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M").is_err());
        assert!(classify_playlist_url("https://127.0.0.1/playlist/37i9dQZF1DXcBWIGoYBM5M").is_err());
        assert!(classify_playlist_url("https://evil.example/open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M").is_err());
        assert!(classify_playlist_url("https://user:pass@open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M").is_err());
        assert!(classify_playlist_url("https://open.spotify.com:8443/playlist/37i9dQZF1DXcBWIGoYBM5M").is_err());
    }

    #[test]
    fn parses_spotify_embed_next_data_and_apple_ldjson_fixtures() {
        let spotify_html = r#"<html><head></head><body>
            <script id="__NEXT_DATA__" type="application/json">
            {"props":{"pageProps":{"state":{"data":{"entity":{
                "name":"Synthwave Nights",
                "trackList":[
                    {"title":"Nightcall","subtitle":"Kavinsky","duration":258000},
                    {"title":"A Real Hero","subtitle":"College, Electric Youth","duration":267000}
                ]
            }}}}}}
            </script></body></html>"#;
        let sp = parse_spotify_embed_html(spotify_html).expect("spotify html");
        assert_eq!(sp.name, "Synthwave Nights");
        assert_eq!(sp.tracks.len(), 2);
        assert_eq!(sp.tracks[0].title, "Nightcall");
        assert_eq!(sp.tracks[0].artist, "Kavinsky");
        assert_eq!(sp.tracks[0].duration_secs, Some(258));

        let apple_html = r#"<html><head>
            <script type="application/ld+json">
            {
                "@context":"https://schema.org",
                "@type":"MusicPlaylist",
                "name":"Midnight Focus",
                "track":[
                    {"@type":"MusicRecording","name":"Experience","byArtist":{"@type":"MusicGroup","name":"Ludovico Einaudi"},"duration":"PT5M15S"},
                    {"@type":"MusicRecording","name":"Time","byArtist":"Hans Zimmer","duration":"PT4M35S"}
                ]
            }
            </script></head></html>"#;
        let ap = parse_apple_music_html(apple_html).expect("apple html");
        assert_eq!(ap.name, "Midnight Focus");
        assert_eq!(ap.tracks.len(), 2);
        assert_eq!(ap.tracks[0].artist, "Ludovico Einaudi");
        assert_eq!(ap.tracks[0].duration_secs, Some(315));
        assert_eq!(ap.tracks[1].artist, "Hans Zimmer");
        assert_eq!(ap.tracks[1].duration_secs, Some(275));
    }

    #[test]
    fn match_scorer_picks_right_track_and_rejects_unrelated_results() {
        let wanted = ImportTrack {
            title: "Blinding Lights".into(),
            artist: "The Weeknd".into(),
            duration_secs: Some(200),
        };
        let candidates = vec![
            mk_track("wrong1", "Save Your Tears", "The Weeknd", Some(215)),
            mk_track("right1", "Blinding Lights (Official Audio)", "The Weeknd", Some(201)),
            mk_track("cover1", "Blinding Lights Piano Cover", "Random Pianist", Some(180)),
        ];
        let picked = pick_best_match(&wanted, &candidates).expect("should match");
        assert_eq!(picked.video_id, "right1");

        let garbage = vec![mk_track("bad1", "Completely Different Song", "Someone Else", Some(200))];
        assert!(pick_best_match(&wanted, &garbage).is_none());
    }
}
