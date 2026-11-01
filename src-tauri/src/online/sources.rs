//! Direct-URL stream sources — the Piped/Invidious fast path (#53).
//!
//! YouTube rate-limits extraction per IP, so `yt-dlp` regularly hangs and
//! playback fails. This module resolves the SAME YouTube catalog through
//! public Piped/Invidious API instances instead: their stream URLs are
//! fetched in ~0.2–2 s and point at instance proxies reachable from any IP —
//! no extraction API call ever leaves our IP, no child process is spawned.
//!
//! Placement: [`super::stream::resolve_with`] runs this fast path after the
//! cache check and BEFORE the yt-dlp attempts, so the fast-path budget
//! (~10 s wall clock, [`FAST_PATH_BUDGET`]) is spent before — not inside —
//! the yt-dlp budgets that govern the interactive/background attempts
//! (PR #52 semantics untouched). On ANY fast-path failure the yt-dlp loop
//! runs unchanged, so a dead instance ecosystem costs ≤ [`FAST_PATH_BUDGET`]
//! and nothing else.
//!
//! Failover policy: instances are tried sequentially in pool order; a
//! source attempt = metadata fetch (connect 2 s client-wide, total 5 s per
//! request, [`METADATA_TIMEOUT`]) + streamed download bounded by the shared
//! [`Instant`] deadline. First success wins; every failure logs at `debug!`
//! with its reason. The source list ([`AudioSource`]) and the failover
//! driver ([`try_sources`]) are separated so order and deadline logic are
//! unit-testable without network.
//!
//! Trust model: instance responses are untrusted input. The metadata body is
//! size-capped, download URLs must be https, the download response must
//! declare an `audio/*` content type, and the file is size-capped
//! ([`MAX_SOURCE_BYTES`]) and verified ([`MIN_FILE_BYTES`]) before it can be
//! mistaken for a cached track. Instances that bot-wall or return HTML fail
//! these checks in milliseconds and failover continues.
//!
//! Like the rest of `online`, this is ToS-gray-area scraping code: it lives
//! in this standalone repo, never in the public Musico (offline) release
//! repo.

use std::io::{Read, Write};
use std::path::PathBuf;
use std::sync::LazyLock;
use std::time::{Duration, Instant};

use serde::Deserialize;

/// Whole fast-path budget — metadata + download across the whole pool.
/// Spent BEFORE the yt-dlp budgets begin (see module header).
pub(crate) const FAST_PATH_BUDGET: Duration = Duration::from_secs(10);
/// Total budget for one metadata request (Piped `/streams` JSON). Invidious
/// opens with the same cap (its endpoint returns the bytes directly).
const METADATA_TIMEOUT: Duration = Duration::from_secs(5);
/// Connect budget for the dedicated source client — a black-holed instance
/// must fail fast so the pool moves on.
const CONNECT_TIMEOUT: Duration = Duration::from_secs(2);
/// Upper bound on one downloaded track — a 5-minute 128 kbps m4a is ~5 MB;
/// anything beyond this cap is not audio we asked for.
const MAX_SOURCE_BYTES: u64 = 64 * 1024 * 1024;
/// Metadata responses are small; anything larger is not the API we asked for.
const MAX_METADATA_BYTES: u64 = 2 * 1024 * 1024;

/// Highest audio bitrate we accept from an instance. itag 140 (~128 kbps AAC)
/// is the target; a little headroom keeps variant bitrates usable. Streams
/// above this are video-derived or mislabeled — slower downloads for the same
/// audio.
const MAX_AUDIO_BITRATE: u64 = 192_000;

/// Default Piped API instances (verified alive 2026-11-01; the ecosystem
/// churns — runtime failover absorbs outages, env overrides below let users
/// pin their own, e.g. a self-hosted instance).
const DEFAULT_PIPED: &[&str] = &[
    "pipedapi.ducks.party",
    "pipedapi.private.coffee",
    "pipedapi.kavin.rocks",
    "pipedapi.adminforge.de",
    "pipedapi.drgns.space",
    "pipedapi.reallyaweso.me",
];

/// Default Invidious instances. Note the codebase never uses their
/// googlevideo URLs (IP-locked to the instance) — only the `latest_version`
/// proxy endpoint with `local=true`, which streams the bytes through the
/// instance itself.
const DEFAULT_INVIDIOUS: &[&str] = &[
    "inv.nadeko.net",
    "invidious.nerdvpn.de",
    "yewtu.be",
    "invidious.f5.si",
];

const ENV_PIPED: &str = "MUSICO_PIPED_INSTANCES";
const ENV_INVIDIOUS: &str = "MUSICO_INVIDIOUS_INSTANCES";

/// Dedicated HTTP client for source fetches. Separate from the InnerTube
/// client (`client::http_client`) on purpose: these are untrusted third-party
/// hosts with a much tighter timeout policy — the shared client's 20 s total
/// budget would let one dead instance eat half the fast-path budget.
static SOURCE_CLIENT: LazyLock<Option<reqwest::blocking::Client>> = LazyLock::new(|| {
    reqwest::blocking::Client::builder()
        .user_agent(super::client::USER_AGENT)
        .connect_timeout(CONNECT_TIMEOUT)
        .tcp_nodelay(true)
        .pool_max_idle_per_host(2)
        .pool_idle_timeout(Duration::from_secs(30))
        .build()
        .ok()
});

// ---------------------------------------------------------------------------
// Pool construction
// ---------------------------------------------------------------------------

/// One configured source instance.
#[derive(Debug, Clone, PartialEq)]
pub(crate) struct SourceSpec {
    pub host: String,
    pub kind: SourceKind,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum SourceKind {
    Piped,
    Invidious,
}

/// Resolves the pool from env overrides (comma-separated hosts; an empty
/// value disables that kind — tests use this) falling back to the defaults.
/// Pure so tests never touch process env.
fn instances_from_env(env_value: Option<&str>, defaults: &[&str]) -> Vec<String> {
    match env_value {
        None => defaults.iter().map(|s| s.to_string()).collect(),
        Some(raw) => raw
            .split(',')
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .map(str::to_string)
            .collect(),
    }
}

/// Builds the failover pool: Piped instances first (richer metadata, direct
/// proxied URLs), then Invidious. Production entry point.
pub(crate) fn default_pool() -> Vec<Box<dyn AudioSource>> {
    let mut specs = Vec::new();
    for host in instances_from_env(std::env::var(ENV_PIPED).ok().as_deref(), DEFAULT_PIPED) {
        specs.push(SourceSpec { host, kind: SourceKind::Piped });
    }
    for host in instances_from_env(std::env::var(ENV_INVIDIOUS).ok().as_deref(), DEFAULT_INVIDIOUS) {
        specs.push(SourceSpec { host, kind: SourceKind::Invidious });
    }
    specs.into_iter().map(|s| s.into_source()).collect()
}

impl SourceSpec {
    fn into_source(self) -> Box<dyn AudioSource> {
        match self.kind {
            SourceKind::Piped => Box::new(PipedSource { host: self.host }),
            SourceKind::Invidious => Box::new(InvidiousSource { host: self.host }),
        }
    }
}

/// A YouTube video id that is safe to embed in URLs and cache filenames —
/// real ids are `[A-Za-z0-9_-]{11}`; we accept a little length slack.
fn valid_video_id(video_id: &str) -> bool {
    !video_id.is_empty()
        && video_id.len() <= 32
        && video_id
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'_' || b == b'-')
}

// ---------------------------------------------------------------------------
// Sources
// ---------------------------------------------------------------------------

/// One open audio stream, ready to be streamed to disk by [`try_sources`].
type SourceBody = Box<dyn Read + Send>;

/// A single stream source (one instance). `open` must respect `deadline` for
/// its network phases; the download itself is deadline-checked chunk-by-chunk
/// by [`try_sources`], so implementations only need bounded metadata/open
/// behavior.
pub(crate) trait AudioSource: Send + Sync {
    fn name(&self) -> &str;
    /// Returns a reader over raw audio (m4a) bytes, or a failure reason.
    fn open(&self, video_id: &str, deadline: Instant) -> Result<SourceBody, String>;
}

/// Remaining time until `deadline` (errors once expired).
fn remaining(deadline: Instant) -> Result<Duration, String> {
    let now = Instant::now();
    if now >= deadline {
        Err("fast-path deadline exhausted".into())
    } else {
        Ok(deadline - now)
    }
}

/// Piped: `GET /streams/{id}` → pick the best `audio/mp4` stream ≤
/// [`MAX_AUDIO_BITRATE`] → GET its (instance-proxied) URL.
struct PipedSource {
    host: String,
}

/// Subset of the Piped `/streams` response we consume. Field names are the
/// instance API's camelCase; everything is optional so degraded/broken
/// responses parse and yield zero candidates instead of failing loudly.
#[derive(Deserialize)]
struct PipedStreams {
    #[serde(default, rename = "audioStreams")]
    audio_streams: Vec<PipedAudio>,
}

#[derive(Deserialize)]
struct PipedAudio {
    #[serde(default)]
    url: String,
    #[serde(default)]
    bitrate: Option<u64>,
    #[serde(default, rename = "mimeType")]
    mime_type: Option<String>,
}

/// Selects the best downloadable m4a from a Piped `audioStreams` array:
/// `audio/mp4` only, bitrate ≤ [`MAX_AUDIO_BITRATE`], highest first. Pure —
/// exercised against embedded fixtures including junk input.
fn select_best_m4a(streams: &[PipedAudio]) -> Option<String> {
    streams
        .iter()
        .filter(|s| {
            s.mime_type
                .as_deref()
                .is_some_and(|m| m.starts_with("audio/mp4"))
        })
        .filter(|s| s.bitrate.is_some_and(|b| b > 0 && b <= MAX_AUDIO_BITRATE))
        .filter(|s| s.url.starts_with("https://"))
        .max_by_key(|s| s.bitrate.unwrap_or(0))
        .map(|s| s.url.clone())
}

impl AudioSource for PipedSource {
    fn name(&self) -> &str {
        &self.host
    }

    fn open(&self, video_id: &str, deadline: Instant) -> Result<SourceBody, String> {
        let client = SOURCE_CLIENT
            .as_ref()
            .ok_or_else(|| "source HTTP client unavailable".to_string())?;
        let meta_budget = remaining(deadline)?.min(METADATA_TIMEOUT);

        let meta = client
            .get(format!("https://{}/streams/{video_id}", self.host))
            .timeout(meta_budget)
            .send()
            .map_err(|e| format!("piped {}: metadata request: {e}", self.host))?;
        if !meta.status().is_success() {
            return Err(format!("piped {}: HTTP {}", self.host, meta.status()));
        }
        let bytes = read_capped(meta, MAX_METADATA_BYTES)
            .map_err(|e| format!("piped {}: metadata body: {e}", self.host))?;
        let parsed: PipedStreams = serde_json::from_slice(&bytes)
            .map_err(|e| format!("piped {}: metadata parse: {e}", self.host))?;
        let url = select_best_m4a(&parsed.audio_streams)
            .ok_or_else(|| format!("piped {}: no usable audio/mp4 stream ≤192k", self.host))?;

        let dl_budget = remaining(deadline)?;
        let resp = client
            .get(url)
            .timeout(dl_budget)
            .send()
            .map_err(|e| format!("piped {}: download request: {e}", self.host))?;
        if !resp.status().is_success() {
            return Err(format!("piped {}: download HTTP {}", self.host, resp.status()));
        }
        // The proxied URL must actually be audio — bot-walled instances answer
        // 200 with an HTML challenge page, which must not reach the cache.
        let ctype = resp
            .headers()
            .get(reqwest::header::CONTENT_TYPE)
            .and_then(|v| v.to_str().ok())
            .unwrap_or("")
            .to_ascii_lowercase();
        if !ctype.starts_with("audio/") {
            return Err(format!("piped {}: download content-type `{ctype}` is not audio", self.host));
        }
        Ok(Box::new(resp))
    }
}

/// Invidious: `GET /latest_version?id={id}&itag=140&local=true` streams the
/// audio through the instance proxy directly. The `/api/v1/videos` JSON's
/// googlevideo URLs are IP-locked to the instance — deliberately unused.
struct InvidiousSource {
    host: String,
}

impl AudioSource for InvidiousSource {
    fn name(&self) -> &str {
        &self.host
    }

    fn open(&self, video_id: &str, deadline: Instant) -> Result<SourceBody, String> {
        let client = SOURCE_CLIENT
            .as_ref()
            .ok_or_else(|| "source HTTP client unavailable".to_string())?;
        let budget = remaining(deadline)?;

        let resp = client
            .get(format!(
                "https://{}/latest_version?id={video_id}&itag=140&local=true",
                self.host
            ))
            .timeout(budget)
            .send()
            .map_err(|e| format!("invidious {}: request: {e}", self.host))?;
        if !resp.status().is_success() {
            return Err(format!("invidious {}: HTTP {}", self.host, resp.status()));
        }
        let ctype = resp
            .headers()
            .get(reqwest::header::CONTENT_TYPE)
            .and_then(|v| v.to_str().ok())
            .unwrap_or("")
            .to_ascii_lowercase();
        // Bot walls answer 200 with text/html — content-type is the gate.
        if !ctype.starts_with("audio/") {
            return Err(format!(
                "invidious {}: content-type `{ctype}` is not audio",
                self.host
            ));
        }
        Ok(Box::new(resp))
    }
}

/// Reads a response body fully, capped — oversized bodies (an HTML wall, a
/// misbehaving instance) fail instead of buffering without bound.
fn read_capped(
    resp: reqwest::blocking::Response,
    cap: u64,
) -> Result<Vec<u8>, String> {
    let mut body = Vec::new();
    resp.take(cap + 1)
        .read_to_end(&mut body)
        .map_err(|e| format!("read: {e}"))?;
    if body.len() as u64 > cap {
        return Err(format!("body exceeds {cap} B cap"));
    }
    Ok(body)
}

// ---------------------------------------------------------------------------
// Failover driver
// ---------------------------------------------------------------------------

/// Tries each source in order under one shared deadline; streams the first
/// success into the cache as `{video_id}.m4a` (atomic via `.part` rename) and
/// returns its path. Every failure is logged at `debug!` with its reason; the
/// returned error is the last one seen. Unit-tested with mock sources — no
/// network.
pub(crate) fn try_sources(
    sources: &[Box<dyn AudioSource>],
    video_id: &str,
    budget: Duration,
) -> Result<PathBuf, String> {
    if !valid_video_id(video_id) {
        return Err(format!("invalid video id `{video_id}`"));
    }
    let deadline = Instant::now() + budget;
    let dir = super::stream::cache_dir();
    std::fs::create_dir_all(&dir).map_err(|e| format!("cache dir: {e}"))?;
    let part = dir.join(format!("{video_id}.m4a.part"));
    let final_path = dir.join(format!("{video_id}.m4a"));

    let mut last_err = String::from("no sources configured");
    for source in sources {
        if remaining(deadline).is_err() {
            last_err = "fast-path deadline exhausted before all sources were tried".into();
            break;
        }
        match source.open(video_id, deadline) {
            Ok(body) => match stream_to_file(body, &part, &final_path, deadline) {
                Ok(bytes) => {
                    log::info!(
                        "fast path: {} served {video_id} ({} KB)",
                        source.name(),
                        bytes / 1024
                    );
                    // Warm the cache index so the next lookup is a hash hit.
                    let _ = super::stream::cached_file(video_id);
                    return Ok(final_path);
                }
                Err(e) => {
                    log::debug!("fast path: {} download failed: {e}", source.name());
                    last_err = e;
                }
            },
            Err(e) => {
                log::debug!("fast path: {} unavailable: {e}", source.name());
                last_err = e;
            }
        }
        let _ = std::fs::remove_file(&part);
    }
    let _ = std::fs::remove_file(&part);
    Err(last_err)
}

/// Production entry: the default pool against the shared fast-path budget.
pub(crate) fn try_fast_path(video_id: &str) -> Result<std::path::PathBuf, String> {
    try_sources(&default_pool(), video_id, FAST_PATH_BUDGET)
}

/// Streams `body` into `part`, then atomically renames onto `final_path`.
/// Enforces the shared deadline (checked per chunk — a stalled proxy must not
/// outlive the budget), the 64 MB cap, and the minimum-size verify. Never
/// leaves a partial file looking cached: failures remove the `.part`.
fn stream_to_file(
    mut body: SourceBody,
    part: &std::path::Path,
    final_path: &std::path::Path,
    deadline: Instant,
) -> Result<u64, String> {
    let mut file = std::fs::File::create(part).map_err(|e| format!("create .part: {e}"))?;
    let mut buf = [0u8; 64 * 1024];
    let mut total: u64 = 0;
    loop {
        if Instant::now() >= deadline {
            return Err("download exceeded the fast-path deadline".into());
        }
        let n = body
            .read(&mut buf)
            .map_err(|e| format!("stream read: {e}"))?;
        if n == 0 {
            break;
        }
        total += n as u64;
        if total > MAX_SOURCE_BYTES {
            return Err(format!("stream exceeds the {MAX_SOURCE_BYTES} B cap"));
        }
        file.write_all(&buf[..n]).map_err(|e| format!("write: {e}"))?;
    }
    file.flush().map_err(|e| format!("flush: {e}"))?;
    drop(file);
    if total < super::stream::MIN_FILE_BYTES {
        return Err(format!("downloaded file suspiciously small ({total} B)"));
    }
    std::fs::rename(part, final_path).map_err(|e| format!("rename: {e}"))?;
    Ok(total)
}

// ---------------------------------------------------------------------------
// Tests — hermetic (no network). Cache-touching tests go through TestCache
// (stream::test_support) so the real user cache is unreachable (#48).
// ---------------------------------------------------------------------------

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Cursor;

    // -- stream selection (fixtures, incl. junk) ---------------------------

    fn parse_streams(json: &str) -> Vec<PipedAudio> {
        serde_json::from_str::<PipedStreams>(json)
            .expect("fixture parses")
            .audio_streams
    }

    #[test]
    fn selects_highest_m4a_within_cap() {
        let streams = parse_streams(
            r#"{"audioStreams":[
                {"url":"https://a/48","bitrate":48000,"mimeType":"audio/mp4; codec=\"mp4a.40.2\"","quality":"48 kbps"},
                {"url":"https://a/129","bitrate":129611,"mimeType":"audio/mp4; codec=\"mp4a.40.2\"","quality":"128 kbps"},
                {"url":"https://a/192","bitrate":192000,"mimeType":"audio/mp4","quality":"192 kbps"},
                {"url":"https://a/256","bitrate":256000,"mimeType":"audio/mp4","quality":"256 kbps"},
                {"url":"https://a/webm","bitrate":160000,"mimeType":"audio/webm; codecs=\"opus\"","quality":"160 kbps"}
            ]}"#,
        );
        assert_eq!(select_best_m4a(&streams).as_deref(), Some("https://a/192"));
    }

    #[test]
    fn rejects_webm_missing_fields_and_insecure_urls() {
        let streams = parse_streams(
            r#"{"audioStreams":[
                {"url":"http://insecure/x","bitrate":128000,"mimeType":"audio/mp4"},
                {"url":"https://a/nobitrate","mimeType":"audio/mp4"},
                {"bitrate":128000,"mimeType":"audio/mp4"},
                {"url":"https://a/zero","bitrate":0,"mimeType":"audio/mp4"}
            ]}"#,
        );
        assert_eq!(select_best_m4a(&streams), None);
    }

    #[test]
    fn no_m4a_only_webm_yields_none() {
        let streams = parse_streams(
            r#"{"audioStreams":[{"url":"https://a/x","bitrate":128000,"mimeType":"audio/webm"}]}"#,
        );
        assert_eq!(select_best_m4a(&streams), None);
    }

    #[test]
    fn empty_and_degraded_bodies_yield_none() {
        assert_eq!(select_best_m4a(&parse_streams(r#"{"audioStreams":[]}"#)), None);
        // A degraded instance answering `{}` must parse to zero candidates.
        assert_eq!(select_best_m4a(&parse_streams(r#"{}"#)), None);
    }

    // -- pool construction --------------------------------------------------

    #[test]
    fn env_parsing_defaults_overrides_and_disable() {
        assert_eq!(instances_from_env(None, &["a", "b"]).len(), 2);
        assert_eq!(
            instances_from_env(Some(" x.com , y.com ,, "), &["d"]),
            vec!["x.com", "y.com"]
        );
        assert!(instances_from_env(Some("  "), &["d"]).is_empty(), "empty env disables the kind");
    }

    // -- failover: order, first-success-wins, deadline -----------------------

    struct MockSource {
        name: String,
        delay: Duration,
        result: Result<Vec<u8>, String>,
    }

    impl MockSource {
        fn ok(name: &str, data: &'static [u8]) -> Box<dyn AudioSource> {
            Box::new(Self { name: name.into(), delay: Duration::ZERO, result: Ok(data.to_vec()) })
        }
        fn failing(name: &str, err: &str) -> Box<dyn AudioSource> {
            Box::new(Self { name: name.into(), delay: Duration::ZERO, result: Err(err.into()) })
        }
        fn slow(name: &str, delay: Duration) -> Box<dyn AudioSource> {
            Box::new(Self { name: name.into(), delay, result: Ok(vec![0u8; 16]) })
        }
    }

    impl AudioSource for MockSource {
        fn name(&self) -> &str {
            &self.name
        }
        fn open(&self, _video_id: &str, deadline: Instant) -> Result<SourceBody, String> {
            if remaining(deadline).is_err() {
                return Err("deadline".into());
            }
            std::thread::sleep(self.delay.min(remaining(deadline)?));
            self.result.clone().map(|v| Box::new(Cursor::new(v)) as SourceBody)
        }
    }

    /// 12 KB payload — passes MIN_FILE_BYTES (10 KB).
    const GOOD_AUDIO: &[u8] = &[0x61; 12 * 1024];

    #[test]
    fn first_success_wins_in_pool_order() {
        let _tc = crate::online::stream::test_support::TestCache::acquire();
        let sources: Vec<Box<dyn AudioSource>> = vec![
            MockSource::failing("dead", "down"),
            MockSource::ok("first-good", GOOD_AUDIO),
            MockSource::ok("never-reached", GOOD_AUDIO),
        ];
        let path = try_sources(&sources, "mockvid00001", Duration::from_secs(5))
            .expect("second source succeeds");
        assert!(path.ends_with("mockvid00001.m4a"));
        let meta = std::fs::metadata(&path).unwrap();
        assert_eq!(meta.len() as usize, GOOD_AUDIO.len());
        // `.part` consumed by the atomic rename.
        assert!(!path.with_extension("m4a.part").exists());
    }

    #[test]
    fn all_failures_surface_the_last_error() {
        let _tc = crate::online::stream::test_support::TestCache::acquire();
        let sources: Vec<Box<dyn AudioSource>> = vec![
            MockSource::failing("one", "first reason"),
            MockSource::failing("two", "second reason"),
        ];
        let err = try_sources(&sources, "mockvid00002", Duration::from_secs(5))
            .expect_err("all sources fail");
        assert!(err.contains("second reason"), "last error surfaces: {err}");
    }

    #[test]
    fn deadline_exhaustion_stops_the_pool() {
        let _tc = crate::online::stream::test_support::TestCache::acquire();
        // 300 ms per source × 4 sources ≫ the 400 ms budget: the pool must
        // bail on the deadline, not run the whole list.
        let sources: Vec<Box<dyn AudioSource>> = vec![
            MockSource::slow("s1", Duration::from_millis(300)),
            MockSource::slow("s2", Duration::from_millis(300)),
            MockSource::slow("s3", Duration::from_millis(300)),
            MockSource::slow("s4", Duration::from_millis(300)),
        ];
        let t0 = Instant::now();
        let err = try_sources(&sources, "mockvid00003", Duration::from_millis(400))
            .expect_err("budget exhausts");
        assert!(err.contains("deadline"), "deadline error surfaces: {err}");
        assert!(t0.elapsed() < Duration::from_millis(900), "bail was prompt: {:?}", t0.elapsed());
    }

    #[test]
    fn tiny_download_is_rejected_and_part_removed() {
        let _tc = crate::online::stream::test_support::TestCache::acquire();
        let sources: Vec<Box<dyn AudioSource>> = vec![MockSource::ok("tiny", b"short")];
        let err = try_sources(&sources, "mockvid00004", Duration::from_secs(5))
            .expect_err("below MIN_FILE_BYTES");
        assert!(err.contains("suspiciously small"), "{err}");
        let dir = crate::online::stream::cache_dir();
        assert!(!dir.join("mockvid00004.m4a").exists());
        assert!(!dir.join("mockvid00004.m4a.part").exists());
    }

    #[test]
    fn invalid_video_ids_never_touch_the_cache() {
        let _tc = crate::online::stream::test_support::TestCache::acquire();
        for bad in ["", "../evil", "a/b", "has space", "über"] {
            assert!(
                try_sources(&[MockSource::ok("x", GOOD_AUDIO)], bad, Duration::from_secs(1)).is_err()
            );
        }
    }

    // -- live (opt-in) --------------------------------------------------------

    /// MUSICO_LIVE_TESTS=1 cargo test -p musico-online live_piped -- --ignored --nocapture
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_piped_resolve() {
        let _tc = crate::online::stream::test_support::TestCache::acquire();
        let piped: Vec<Box<dyn AudioSource>> = instances_from_env(
            std::env::var(ENV_PIPED).ok().as_deref(),
            DEFAULT_PIPED,
        )
        .into_iter()
        .map(|host| Box::new(PipedSource { host }) as Box<dyn AudioSource>)
        .collect();
        let t0 = Instant::now();
        match try_sources(&piped, "dQw4w9WgXcQ", Duration::from_secs(20)) {
            Ok(path) => println!("PIPED resolve: {:?} → {}", t0.elapsed(), path.display()),
            Err(e) => panic!("piped fast path failed (instance ecosystem down?): {e}"),
        }
    }

    /// MUSICO_LIVE_TESTS=1 cargo test -p musico-online live_invidious -- --ignored --nocapture
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_invidious_resolve() {
        let _tc = crate::online::stream::test_support::TestCache::acquire();
        let inv: Vec<Box<dyn AudioSource>> = instances_from_env(
            std::env::var(ENV_INVIDIOUS).ok().as_deref(),
            DEFAULT_INVIDIOUS,
        )
        .into_iter()
        .map(|host| Box::new(InvidiousSource { host }) as Box<dyn AudioSource>)
        .collect();
        let t0 = Instant::now();
        match try_sources(&inv, "dQw4w9WgXcQ", Duration::from_secs(20)) {
            Ok(path) => println!("INVIDIOUS resolve: {:?} → {}", t0.elapsed(), path.display()),
            Err(e) => panic!("invidious fast path failed (instance ecosystem down?): {e}"),
        }
    }
}
