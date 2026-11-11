//! Synced (karaoke) lyrics via LRCLIB.net (issue #61) — a free, keyless,
//! community-run lyrics database.  This is the single highest-impact player
//! upgrade per docs/ux-review.md: YouTube Music only exposes unsynced text,
//! LRCLIB has timestamped LRC for most catalog.
//!
//! # API shape — live-verified 2026-11-01
//!
//! - `GET {BASE}/api/get?artist_name=…&track_name=…&duration=204`
//!   → exact match: `{ id, trackName, artistName, albumName, duration
//!   (number), instrumental (bool), plainLyrics, syncedLyrics }` — one
//!   object, HTTP 404 when there is no match.
//! - `GET {BASE}/api/search?artist_name=…&track_name=…`
//!   → array of the same record shape.  ⚠️ Durations DRIFT across entries
//!   (Believer: 233/289/205 vs the real 204) — the search fallback MUST
//!   filter to a ±2 s window and take the nearest, else the wrong take
//!   (radio edit, live, remaster) plays over the song.
//! - `syncedLyrics` is standard LRC: `[mm:ss.xx]text` (sometimes several
//!   stamps per line), `[offset:+ms]` global shift, `[ar:]`-style metadata
//!   tags, blank instrumental gaps.  Verified: Believer →
//!   `[00:07.82]First things first, …`.
//!
//! Unofficial/free upstream: be polite (real UA), tolerate drift, and fail
//! to `None` on ANY error — the frontend falls back to the unsynced YTM
//! text (#61 accept: clean fallback).  `MUSICO_LRCLIB_API` overrides the
//! base URL.  No network happens offline: the command is ensure_online
//! gated like its siblings.

use std::sync::LazyLock;
use std::time::Duration;

use serde::{Deserialize, Serialize};

/// LRCLIB API base default — `MUSICO_LRCLIB_API` overrides it (proxy/mirror).
const API_BASE: &str = "https://lrclib.net";
/// Metadata lookups are best-effort; 5 s covers the occasionally slow CDN.
const API_TIMEOUT: Duration = Duration::from_secs(5);
/// A search candidate's duration must be within this window of the song.
const DURATION_WINDOW_SECS: f64 = 2.0;
/// Requests identify the app per LRCLIB's etiquette (no key, community API).
const USER_AGENT: &str = concat!("Musico/", env!("CARGO_PKG_VERSION"), " (github.com/ARYAN007H/Music0)");

/// One timed lyric line for the karaoke highlighter.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LyricLine {
    pub time_secs: f32,
    pub text: String,
}

/// What `online_synced_lyrics` returns: timed lines when LRCLIB had synced
/// LRC, otherwise the plain full text (rendered untimed), else `None`.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SyncedLyricsDto {
    pub synced: Vec<LyricLine>,
    pub plain: Option<String>,
}

static CLIENT: LazyLock<reqwest::blocking::Client> = LazyLock::new(|| {
    reqwest::blocking::Client::builder()
        .user_agent(USER_AGENT)
        .connect_timeout(Duration::from_secs(3))
        .timeout(API_TIMEOUT)
        .build()
        .unwrap_or_else(|_| reqwest::blocking::Client::new())
});

/// Effective API base — `MUSICO_LRCLIB_API` overrides the default
/// (proxy/mirror testing), matching the source fast path's env overrides.
fn api_base() -> String {
    std::env::var("MUSICO_LRCLIB_API")
        .ok()
        .map(|v| v.trim().trim_end_matches('/').to_string())
        .filter(|v| !v.is_empty())
        .unwrap_or_else(|| API_BASE.to_string())
}

/// Tolerant LRCLIB record — every field optional so shape drift degrades to
/// "no match", never a parse failure.
#[derive(Deserialize, Default)]
struct LrcRecord {
    #[serde(default)]
    duration: Option<f64>,
    #[serde(default)]
    instrumental: Option<bool>,
    #[serde(default, rename = "plainLyrics")]
    plain_lyrics: Option<String>,
    #[serde(default, rename = "syncedLyrics")]
    synced_lyrics: Option<String>,
    #[serde(default, rename = "artistName")]
    artist_name: Option<String>,
    #[serde(default, rename = "trackName")]
    track_name: Option<String>,
}

/// Fetches synced lyrics, matching by artist + title + duration (#61).
/// Ladder: exact `get` (with duration) → `search` filtered to the ±2 s
/// window (nearest duration wins).  `None` = no match / network error /
/// drift — the frontend falls back to the unsynced YTM text.
pub fn fetch_synced_lyrics(
    artist: &str,
    title: &str,
    duration_secs: Option<u32>,
) -> Option<SyncedLyricsDto> {
    let (artist, title) = (artist.trim(), title.trim());
    if artist.is_empty() || title.is_empty() {
        return None;
    }

    // Rung 1: exact match with the known duration (the verified best path).
    if let Some(dur) = duration_secs {
        let rec = api_get(&[
            ("artist_name", artist),
            ("track_name", title),
            ("duration", &dur.to_string()),
        ])?;
        if rec.instrumental.unwrap_or(false) {
            // The exact take is instrumental — same-title candidates within
            // the window would be different recordings whose lyrics would
            // be foreign.  Stop here (also saves the redundant re-query —
            // #79 review P2).
            return None;
        }
        if let Some(dto) = record_to_dto(&rec) {
            return Some(dto);
        }
    }

    // Rung 2: search, filtered to the duration window, nearest first.  The
    // live pool contains radio edits and live takes hours away — a blind
    // first result would put the wrong lyrics over the song.
    let records = api_search(artist, title)?;
    let candidates = pick_candidates(&records, duration_secs);
    for r in candidates {
        // When the duration is unknown (shelf-built tracks), the window
        // filter can't discriminate — without a similarity gate the
        // SHORTEST same-titled song worldwide would win and the lyric
        // stage would sing a different song entirely.
        if duration_secs.is_none() && !plausibly_same_song(artist, title, r) {
            continue;
        }
        if let Some(dto) = record_to_dto(r) {
            return Some(dto);
        }
    }
    None
}

/// Word-set similarity gate for duration-less LRCLIB matches: the title
/// word set must overlap in both directions' favor (containment either
/// way — tolerates "(Official Video)" suffixes) and the artists must
/// share at least one word. Pure and unit-testable.
fn plausibly_same_song(artist: &str, title: &str, rec: &LrcRecord) -> bool {
    let words = |s: &str| -> Vec<String> {
        s.to_lowercase()
            .split(|c: char| !c.is_alphanumeric())
            .filter(|w| !w.is_empty())
            .map(str::to_string)
            .collect()
    };
    let title_w = words(title);
    let rec_title_w = words(rec.track_name.as_deref().unwrap_or(""));
    let title_ok = !title_w.is_empty()
        && !rec_title_w.is_empty()
        && (rec_title_w.starts_with(&title_w) || title_w.starts_with(&rec_title_w));
    if !title_ok {
        return false;
    }
    let artist_w = words(artist);
    let rec_artist_w = words(rec.artist_name.as_deref().unwrap_or(""));
    if artist_w.is_empty() || rec_artist_w.is_empty() {
        // No artist on one side: title match alone is too weak to trust.
        return false;
    }
    artist_w.iter().any(|w| rec_artist_w.contains(w))
        || rec_artist_w.iter().any(|w| artist_w.contains(w))
}

/// Search candidates filtered to the ±2 s duration window, nearest duration
/// first.  Pure so the gate is unit-testable without network (#79 review
/// P1): the live pool drifts (Believer 233/289/205 vs the real 204), and a
/// candidate with UNKNOWN duration only qualifies when the song's duration
/// is unknown too (#79 review P2) — otherwise it could still win on a pool
/// of bad matches.
fn pick_candidates<'a>(
    records: &'a [LrcRecord],
    duration_secs: Option<u32>,
) -> Vec<&'a LrcRecord> {
    let mut candidates: Vec<&LrcRecord> = records
        .iter()
        .filter(|r| match (duration_secs, r.duration) {
            (Some(dur), Some(d)) => (d - dur as f64).abs() <= DURATION_WINDOW_SECS,
            (Some(_), None) => false,
            (None, _) => true,
        })
        .collect();
    candidates.sort_by(|a, b| {
        let target = duration_secs.unwrap_or(0) as f64;
        let ka = a.duration.map_or(f64::MAX, |d| (d - target).abs());
        let kb = b.duration.map_or(f64::MAX, |d| (d - target).abs());
        ka.partial_cmp(&kb).unwrap_or(std::cmp::Ordering::Equal)
    });
    candidates
}

/// GET /api/get — single record or 404.
fn api_get(params: &[(&str, &str)]) -> Option<LrcRecord> {
    let resp = CLIENT
        .get(format!("{}/api/get", api_base()))
        .query(params)
        .send()
        .ok()?;
    if !resp.status().is_success() {
        return None;
    }
    resp.json::<LrcRecord>().ok()
}

/// GET /api/search — list of records (possibly empty).
fn api_search(artist: &str, title: &str) -> Option<Vec<LrcRecord>> {
    let resp = CLIENT
        .get(format!("{}/api/search", api_base()))
        .query(&[("artist_name", artist), ("track_name", title)])
        .send()
        .ok()?;
    if !resp.status().is_success() {
        return None;
    }
    resp.json::<Vec<LrcRecord>>().ok()
}

/// Maps a record to the IPC DTO: synced LRC parsed when present, else the
/// plain text.  Instrumental hits produce `None` — there is nothing to
/// show, and the frontend should try its unsynced fallback.
fn record_to_dto(rec: &LrcRecord) -> Option<SyncedLyricsDto> {
    if rec.instrumental.unwrap_or(false) {
        return None;
    }
    let synced = rec
        .synced_lyrics
        .as_deref()
        .map(parse_lrc)
        .unwrap_or_default();
    if !synced.is_empty() {
        return Some(SyncedLyricsDto { synced, plain: rec.plain_lyrics.clone() });
    }
    match rec.plain_lyrics.as_deref() {
        Some(p) if !p.trim().is_empty() => {
            Some(SyncedLyricsDto { synced: Vec::new(), plain: Some(p.to_string()) })
        }
        _ => None,
    }
}

/// Parses LRC text into sorted timed lines: `[mm:ss.xx]text`, several
/// stamps per line, `[offset:±ms]` global shift (positive = lyrics shown
/// earlier), `[ar:]`-style metadata and blank lines skipped.  Malformed
/// lines are dropped — a partially-timed file still highlights.
fn parse_lrc(text: &str) -> Vec<LyricLine> {
    let mut offset_ms: f32 = 0.0;
    let mut out: Vec<LyricLine> = Vec::new();
    for raw in text.lines() {
        let line = raw.trim();
        if !line.starts_with('[') {
            continue;
        }
        let mut stamps: Vec<f32> = Vec::new();
        let mut rest = line;
        while let Some(close) = rest.find(']') {
            let tag = &rest[1..close];
            let after = &rest[close + 1..];
            if let Some(ms) = tag
                .strip_prefix("offset:")
                .and_then(|v| v.trim().parse::<f32>().ok())
            {
                offset_ms = ms / 1000.0;
                rest = after;
                continue;
            }
            match parse_stamp(tag) {
                Some(t) => {
                    stamps.push(t);
                    rest = after;
                }
                // First non-timestamp bracket (metadata tag, or a bracket
                // inside the lyric text) — the remaining text belongs to
                // whatever stamps we already collected.
                None => break,
            }
        }
        if stamps.is_empty() {
            continue;
        }
        let text = strip_word_tags(rest).trim().to_string();
        if text.is_empty() {
            continue; // instrumental gap markers
        }
        for t in stamps {
            out.push(LyricLine {
                time_secs: (t - offset_ms).max(0.0),
                text: text.clone(),
            });
        }
    }
    out.sort_by(|a, b| a.time_secs.partial_cmp(&b.time_secs).unwrap_or(std::cmp::Ordering::Equal));
    out
}

/// `[mm:ss]`, `[mm:ss.xx]`, `[mm:ss.xxx]` — the variants LRC files use.
fn parse_stamp(tag: &str) -> Option<f32> {
    let (mm, rest) = tag.split_once(':')?;
    let minutes: f32 = mm.trim().parse().ok()?;
    let seconds: f32 = rest.trim().parse().ok()?;
    let total = minutes * 60.0 + seconds;
    (total >= 0.0 && minutes >= 0.0 && seconds < 60.0).then_some(total)
}

/// Strips enhanced word-level tags `<mm:ss.xx>` (A2 extension) that LRCLIB
/// occasionally carries inside lyric text — they would render literally
/// (#79 review P2).  Non-stamp `<…>` sequences pass through untouched.
fn strip_word_tags(text: &str) -> String {
    if !text.contains('<') {
        return text.to_string();
    }
    let mut out = String::with_capacity(text.len());
    let mut rest = text;
    while let Some(rel_open) = rest.find('<') {
        match rest[rel_open..].find('>') {
            Some(rel_close)
                if parse_stamp(&rest[rel_open + 1..rel_open + rel_close]).is_some() =>
            {
                out.push_str(&rest[..rel_open]);
                rest = &rest[rel_open + rel_close + 1..];
            }
            // Not a stamp (or unterminated) — keep the literal '<'.
            _ => {
                out.push_str(&rest[..rel_open + 1]);
                rest = &rest[rel_open + 1..];
            }
        }
    }
    out.push_str(rest);
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn plausibly_same_song_gates_durationless_matches() {
        let rec = |artist: &str, track: &str| LrcRecord {
            artist_name: Some(artist.into()),
            track_name: Some(track.into()),
            ..Default::default()
        };
        // Same song, suffixed title → accepted.
        assert!(plausibly_same_song(
            "Hardy Sandhu",
            "Backbone",
            &rec("Hardy Sandhu", "Backbone (Official Video) Jaani B Praak"),
        ));
        // A foreign song that merely shares the title → rejected.
        assert!(!plausibly_same_song(
            "Hardy Sandhu",
            "Backbone",
            &rec("Backbone Band", "Backbone"),
        ));
        // Same title, different artist → rejected.
        assert!(!plausibly_same_song(
            "Arijit Singh",
            "Tum Hi Ho",
            &rec("Someone Else", "Tum Hi Ho"),
        ));
    }

    #[test]
    fn parses_stamps_metadata_and_multi_stamp_lines() {
        let lrc = "[ar:Artist]\n[al:Album]\n[00:07.82]First line\n[00:12.15]Second\n[00:20][01:02]Chorus\nnot lrc\n[00:30]";
        let lines = parse_lrc(lrc);
        let texts: Vec<_> = lines.iter().map(|l| l.text.as_str()).collect();
        assert_eq!(texts, ["First line", "Second", "Chorus", "Chorus"]);
        assert!((lines[0].time_secs - 7.82).abs() < 0.001);
        // The chorus line's two stamps stay in time order after the sort.
        assert!((lines[2].time_secs - 20.0).abs() < 0.001);
        assert!((lines[3].time_secs - 62.0).abs() < 0.001);
    }

    #[test]
    fn offset_tag_shifts_every_line() {
        let lrc = "[offset:+500]\n[00:10.00]early\n[offset:-1000]\n[00:20.00]late";
        // [offset:+500] shows lyrics 0.5 s sooner: 10.00 → 9.50.  A second
        // tag replaces it: 20.00 - (-1.0) → 21.00.
        let lines = parse_lrc(lrc);
        assert!((lines[0].time_secs - 9.5).abs() < 0.001, "{:?}", lines[0]);
        assert!((lines[1].time_secs - 21.0).abs() < 0.001, "{:?}", lines[1]);
    }

    #[test]
    fn malformed_stamps_and_out_of_range_seconds_are_dropped() {
        assert!(parse_lrc("[xx:yy]nope").is_empty());
        assert!(parse_lrc("[00:99.00]seconds out of range").is_empty());
        assert!(parse_lrc("[-1:00.00]negative").is_empty());
        assert!(parse_lrc("[]orphan").is_empty());
    }

    #[test]
    fn candidate_pick_filters_window_and_prefers_nearest() {
        let recs = vec![
            LrcRecord { duration: Some(233.0), plain_lyrics: Some("far".into()), ..Default::default() },
            LrcRecord { duration: Some(289.0), plain_lyrics: Some("farther".into()), ..Default::default() },
            LrcRecord { duration: Some(205.0), plain_lyrics: Some("near".into()), ..Default::default() },
            LrcRecord { duration: None, plain_lyrics: Some("unknown".into()), ..Default::default() },
        ];
        // The REAL pick path (#79 review P1): only 205 sits within ±2 s of
        // 204 — the drifted 233/289 pool entries (radio edit, live) must
        // not win, and an unknown-duration candidate is dropped while the
        // song duration is known (#79 review P2).
        let picked = pick_candidates(&recs, Some(204));
        assert_eq!(picked.len(), 1);
        assert_eq!(picked[0].plain_lyrics.as_deref(), Some("near"));
        // No song duration: everything qualifies, nearest known duration
        // first, unknown last.
        let fuzzy = pick_candidates(&recs, None);
        let order: Vec<_> = fuzzy.iter().map(|r| r.plain_lyrics.as_deref().unwrap()).collect();
        assert_eq!(order, ["near", "far", "farther", "unknown"]);
    }

    #[test]
    fn enhanced_word_tags_are_stripped() {
        let lines = parse_lrc("[00:10.00]Word<00:10.42>tags<00:10.80>gone");
        assert_eq!(lines[0].text, "Wordtagsgone");
        // A literal '<' that is not a stamp survives.
        let lines = parse_lrc("[00:10.00]five < six");
        assert_eq!(lines[0].text, "five < six");
    }

    /// Live end-to-end: a mainstream song must return synced lines.  Opt-in:
    ///   MUSICO_LIVE_TESTS=1 cargo test -p musico-online live_lrclib -- --ignored --nocapture
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_lrclib_synced_believer() {
        let dto = fetch_synced_lyrics("Imagine Dragons", "Believer", Some(204))
            .expect("LRCLIB has Believer");
        assert!(dto.synced.len() >= 10, "suspiciously few lines: {}", dto.synced.len());
        assert!((dto.synced[0].time_secs - 7.82).abs() < 2.0, "first line at {:?}", dto.synced[0]);
        assert!(dto.synced[0].text.contains("First things first"));
        println!("lrclib: {} synced lines, plain {}", dto.synced.len(), dto.plain.is_some());
        // No-duration path still finds it through the search rung.
        let fuzzy = fetch_synced_lyrics("Imagine Dragons", "Believer", None).expect("search rung");
        assert!(!fuzzy.synced.is_empty());
    }
}
