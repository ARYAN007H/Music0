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
//! Like the rest of `online`, this module is kept in this standalone
//! repository so the offline player can be built and shipped without it.

use std::collections::HashMap;
use std::io::{Read, Write};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, LazyLock, Mutex};
use std::time::{Duration, Instant};

use serde::Deserialize;

/// Whole fast-path budget — metadata + download across the whole pool.
/// Spent BEFORE the yt-dlp budgets begin (see module header).
pub(crate) const FAST_PATH_BUDGET: Duration = Duration::from_secs(10);
/// Total budget for one metadata request (InnerTube `/player` or Piped `/streams` JSON).
/// Healthy endpoints answer in 150–600 ms; 2.2 s catches hung instances fast.
const METADATA_TIMEOUT: Duration = Duration::from_millis(2_200);
/// Connect budget for the dedicated source client — a black-holed instance
/// must fail fast so the pool moves on.
const CONNECT_TIMEOUT: Duration = Duration::from_millis(1_500);
/// Per-read idle timeout for live streaming HTTP bodies (bounds hung headers
/// and stalled chunks without cutting off long tracks on slow internet).
const STREAM_READ_TIMEOUT: Duration = Duration::from_secs(15);
/// Delay before hedging backup sources when the primary source hasn't opened yet.
const STREAM_HEDGE_DELAY: Duration = Duration::from_millis(400);
/// Maximum backup sources launched per hedge wave.
const STREAM_HEDGE_BATCH: usize = 3;
/// Upper bound on one downloaded track — a 5-minute 128 kbps m4a is ~5 MB;
/// anything beyond this cap is not audio we asked for.
pub(crate) const MAX_SOURCE_BYTES: u64 = 64 * 1024 * 1024;
/// Metadata responses are small; anything larger is not the API we asked for.
const MAX_METADATA_BYTES: u64 = 2 * 1024 * 1024;

/// Default highest audio bitrate we accept from an instance. itag 140
/// (~128 kbps AAC) is the target; a little headroom keeps variant bitrates
/// usable. Streams above this are video-derived or mislabeled — slower
/// downloads for the same audio. The `high`/`lossless` tiers raise this to
/// [`HIGH_TIER_AUDIO_BITRATE`].
const MAX_AUDIO_BITRATE: u64 = 192_000;

/// Fast-path ceiling unlocked by the `high`/`lossless` quality tiers. This is
/// the itag-141 class (256 kbps audio-only AAC) — the same rung the yt-dlp
/// selector already picks on those tiers, so the fast path now keeps pace
/// instead of silently capping quality at 192k.
const HIGH_TIER_AUDIO_BITRATE: u64 = 256_000;

/// Effective fast-path bitrate ceiling for the active quality tier.
/// Mirrors `stream.rs::yt_dlp_format_selector`: the download tier wins when
/// explicitly set, else the streaming tier. Loads settings directly — the
/// same pattern, called once per resolve on a worker thread.
fn fast_path_bitrate_cap() -> u64 {
    let settings = crate::config::AppSettings::load();
    tier_cap(&settings.download_quality, &settings.streaming_quality)
}

/// Pure tier → ceiling mapping (`high`/`lossless` unlock 256k), so the
/// policy is unit-testable without touching process settings.
fn tier_cap(download_quality: &str, streaming_quality: &str) -> u64 {
    let high = |tier: &str| tier == "high" || tier == "lossless";
    if high(download_quality) || high(streaming_quality) {
        HIGH_TIER_AUDIO_BITRATE
    } else {
        MAX_AUDIO_BITRATE
    }
}

// ---------------------------------------------------------------------------
// Stream loudness (YouTube audioConfig → normalization)
// ---------------------------------------------------------------------------

/// YouTube's own playback loudness reference (LUFS). The InnerTube player
/// response reports each track's measured loudness against this level; the
/// normalization hook converts it to a static gain toward the user's target.
/// (Same reference the Metrolist/InnerTune clients use.)
const YT_REFERENCE_LUFS: f32 = -7.0;

/// Session-scoped per-track loudness captured from InnerTube-direct player
/// responses (`playerConfig.audioConfig`), keyed by video id. Consumed by the
/// event bridge's normalization hook so online tracks get the same
/// loudness-match treatment local files get from scan-computed ReplayGain.
/// Tracks resolved through Piped/Invidious/yt-dlp/Saavn carry no loudness
/// metadata and simply stay at unity gain.
static STREAM_LOUDNESS: LazyLock<Mutex<HashMap<String, f32>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

/// Loudness map bound — a long session with heavy prefetching is the only way
/// to approach this; the map clears wholesale when full (same policy as the
/// search cache).
const STREAM_LOUDNESS_CAP: usize = 4096;

/// Records a track's measured LUFS. `loudness_db` is YouTube's delta above
/// the reference level; `perceptual_db`, when present, is the measured LUFS
/// directly and wins.
fn store_stream_loudness(video_id: &str, loudness_db: Option<f64>, perceptual_db: Option<f64>) {
    let measured = match perceptual_db {
        Some(p) => p as f32,
        // No perceptual value: reconstruct the measured loudness from the
        // delta and the reference level.
        None => match loudness_db {
            Some(l) => (l as f32) + YT_REFERENCE_LUFS,
            None => return,
        },
    };
    let mut map = STREAM_LOUDNESS.lock().unwrap_or_else(|e| e.into_inner());
    if map.len() >= STREAM_LOUDNESS_CAP && !map.contains_key(video_id) {
        map.clear();
    }
    map.insert(video_id.to_string(), measured);
}

/// The measured LUFS for a previously-resolved track, if it was captured.
pub fn stream_loudness(video_id: &str) -> Option<f32> {
    STREAM_LOUDNESS
        .lock()
        .unwrap_or_else(|e| e.into_inner())
        .get(video_id)
        .copied()
}

/// Pulls `playerConfig.audioConfig.{loudnessDb,perceptualLoudnessDb}` out of a
/// player response and records it. Best-effort: absent fields (or a shape
/// change) simply leave the track at unity gain.
fn capture_stream_loudness(body: &[u8], video_id: &str) {
    let Ok(val) = serde_json::from_slice::<serde_json::Value>(body) else {
        return;
    };
    let Some(config) = val.pointer("/playerConfig/audioConfig") else {
        return;
    };
    store_stream_loudness(
        video_id,
        config.get("loudnessDb").and_then(|v| v.as_f64()),
        config
            .get("perceptualLoudnessDb")
            .and_then(|v| v.as_f64()),
    );
}

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
/// Kill-switch for the InnerTube-direct source: unset or any non-blank value
/// keeps it enabled (the default); a blank value disables it (hermetic tests).
const ENV_INNERTUBE_DIRECT: &str = "MUSICO_INNERTUBE_DIRECT";

/// Dedicated HTTP client for source fetches. Separate from the InnerTube
/// client (`client::http_client`) on purpose: these are untrusted third-party
/// hosts with a much tighter timeout policy — the shared client's 20 s total
/// budget would let one dead instance eat half the fast-path budget.
static SOURCE_CLIENT: LazyLock<Option<reqwest::blocking::Client>> = LazyLock::new(|| {
    reqwest::blocking::Client::builder()
        .user_agent(super::client::USER_AGENT)
        .connect_timeout(CONNECT_TIMEOUT)
        .tcp_nodelay(true)
        .pool_max_idle_per_host(4)
        .pool_idle_timeout(Duration::from_secs(60))
        .build()
        .ok()
});

/// Dedicated streaming HTTP client with a tight connect timeout (2 s) and a
/// 15 s idle read/send wait (`blocking::ClientBuilder::timeout` bounds each
/// `Response::read` chunk without imposing a whole-request total deadline on
/// streaming GETs that omit `RequestBuilder::timeout`).
static STREAM_CLIENT: LazyLock<Option<reqwest::blocking::Client>> = LazyLock::new(|| {
    reqwest::blocking::Client::builder()
        .user_agent(super::client::USER_AGENT)
        .connect_timeout(CONNECT_TIMEOUT)
        .timeout(STREAM_READ_TIMEOUT)
        .tcp_nodelay(true)
        .pool_max_idle_per_host(4)
        .pool_idle_timeout(Duration::from_secs(60))
        .build()
        .ok()
});

/// Exposes the shared streaming HTTP client for desktop fallback direct-URL opens.
#[cfg(not(target_os = "android"))]
pub(crate) fn stream_http_client() -> Option<&'static reqwest::blocking::Client> {
    STREAM_CLIENT.as_ref()
}

// ---------------------------------------------------------------------------
// Pool construction
// ---------------------------------------------------------------------------

/// One configured source instance.
#[derive(Debug, Clone, PartialEq)]
struct SourceSpec {
    host: String,
    kind: SourceKind,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum SourceKind {
    Piped,
    Invidious,
    InnerTubeDirect,
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

/// Builds the failover pool: the InnerTube-direct multi-client director first
/// (first-party endpoints, no third-party instance to bot-wall — the
/// Metrolist-derived approach; see [`InnerTubeDirectSource`]), then the
/// Piped/Invidious instance pools as failover for when YouTube's player API
/// itself refuses us, dynamically ordered by EWMA health score. JioSaavn is
/// dispatched per-id upstream of this pool and stays the catalog backup.
fn ordered_source_specs() -> Vec<SourceSpec> {
    let mut specs = Vec::new();
    let piped_env = std::env::var(ENV_PIPED).ok();
    let inv_env = std::env::var(ENV_INVIDIOUS).ok();
    let it_env = std::env::var(ENV_INNERTUBE_DIRECT).ok();
    // Director first, pinned: unlike the instance pools it has no third-party
    // ecosystem to churn, so its place in the order is not a judgment call.
    // A blank MUSICO_INNERTUBE_DIRECT disables it (hermetic tests).
    let director_enabled = !it_env.as_deref().is_some_and(|s| s.trim().is_empty());
    let pools_explicitly_blanked = piped_env.as_deref().is_some_and(|s| s.trim().is_empty())
        && inv_env.as_deref().is_some_and(|s| s.trim().is_empty())
        && !director_enabled;
    if !pools_explicitly_blanked {
        if director_enabled {
            specs.push(SourceSpec { host: InnerTubeDirectSource::NAME.to_string(), kind: SourceKind::InnerTubeDirect });
        }
        for host in instances_from_env(piped_env.as_deref(), DEFAULT_PIPED) {
            specs.push(SourceSpec { host, kind: SourceKind::Piped });
        }
        for host in instances_from_env(inv_env.as_deref(), DEFAULT_INVIDIOUS) {
            specs.push(SourceSpec { host, kind: SourceKind::Invidious });
        }
    }
    // Pin the director ahead of whatever the instance pools' history says:
    // a proven-healthy instance still depends on a third party staying alive.
    let (direct, mut rest): (Vec<_>, Vec<_>) = specs
        .into_iter()
        .partition(|s| s.kind == SourceKind::InnerTubeDirect);
    sort_specs_by_health(&mut rest);
    direct.into_iter().chain(rest).collect()
}

pub(crate) fn default_pool() -> Vec<Box<dyn AudioSource>> {
    ordered_source_specs()
        .into_iter()
        .map(SourceSpec::into_source)
        .collect()
}

fn default_pool_arc() -> Vec<Arc<dyn AudioSource>> {
    ordered_source_specs()
        .into_iter()
        .map(|s| Arc::from(s.into_source()))
        .collect()
}

#[derive(Clone, Debug)]
struct InstanceHealth {
    ewma_ms: f64,
    success_rate: f64,
    failures: u32,
    demoted_until: Option<Instant>,
}

static HEALTH_BOARD: LazyLock< std::sync::Mutex<std::collections::HashMap<String, InstanceHealth>> > =
    LazyLock::new(|| std::sync::Mutex::new(std::collections::HashMap::new()));

pub(crate) fn record_instance_outcome(host: &str, ok: bool, elapsed: Duration) {
    let key = host.split_once(':').map(|(_, h)| h).unwrap_or(host);
    let Ok(mut map) = HEALTH_BOARD.lock() else { return };
    let entry = map.entry(key.to_string()).or_insert(InstanceHealth {
        ewma_ms: 400.0,
        success_rate: 1.0,
        failures: 0,
        demoted_until: None,
    });
    let ms = elapsed.as_millis() as f64;
    if ok {
        entry.ewma_ms = 0.7 * entry.ewma_ms + 0.3 * ms;
        entry.success_rate = 0.7 * entry.success_rate + 0.3 * 1.0;
        entry.failures = 0;
        entry.demoted_until = None;
    } else {
        entry.ewma_ms = 0.7 * entry.ewma_ms + 0.3 * ms.max(2500.0);
        entry.success_rate *= 0.7;
        entry.failures += 1;
        if entry.failures >= 2 {
            entry.demoted_until = Some(Instant::now() + Duration::from_secs(15 * 60));
        }
    }
}

fn sort_specs_by_health(specs: &mut [SourceSpec]) {
    let Ok(map) = HEALTH_BOARD.lock() else { return };
    let now = Instant::now();
    specs.sort_by(|a, b| {
        let ha = map.get(&a.host);
        let hb = map.get(&b.host);
        let dem_a = ha.and_then(|h| h.demoted_until).is_some_and(|t| t > now);
        let dem_b = hb.and_then(|h| h.demoted_until).is_some_and(|t| t > now);
        if dem_a != dem_b {
            return dem_a.cmp(&dem_b);
        }
        let score_a = ha.map(|h| h.ewma_ms / h.success_rate.max(0.1)).unwrap_or(400.0);
        let score_b = hb.map(|h| h.ewma_ms / h.success_rate.max(0.1)).unwrap_or(400.0);
        score_a.partial_cmp(&score_b).unwrap_or(std::cmp::Ordering::Equal)
    });
}

impl SourceSpec {
    fn into_source(self) -> Box<dyn AudioSource> {
        match self.kind {
            SourceKind::Piped => Box::new(PipedSource { host: self.host }),
            SourceKind::Invidious => Box::new(InvidiousSource { host: self.host }),
            SourceKind::InnerTubeDirect => Box::new(InnerTubeDirectSource),
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
pub(crate) type SourceBody = Box<dyn Read + Send>;

/// Open audio stream with HTTP response metadata needed by the local range server.
pub(crate) struct OpenedAudioStream {
    pub reader: SourceBody,
    pub content_length: Option<u64>,
    pub content_type: String,
    pub source_name: String,
}

/// Validates an HTTP audio stream response, enforcing HTTP success, audio MIME
/// gating (`application/octet-stream` allowed only when requested), and an
/// up-front `max_bytes` cap on declared `Content-Length`.
pub(crate) fn validate_audio_response(
    resp: reqwest::blocking::Response,
    max_bytes: u64,
    allow_octet_stream: bool,
    source_name: String,
    err_prefix: &str,
) -> Result<OpenedAudioStream, String> {
    if !resp.status().is_success() {
        return Err(format!("{err_prefix}: HTTP {}", resp.status()));
    }
    // Bot-walled instances can answer 200 with an HTML challenge page, which
    // must never reach the cache or range server.
    let ctype = resp
        .headers()
        .get(reqwest::header::CONTENT_TYPE)
        .and_then(|v| v.to_str().ok())
        .unwrap_or("")
        .to_ascii_lowercase();
    let is_audio = ctype.starts_with("audio/");
    let is_octet = allow_octet_stream && ctype.starts_with("application/octet-stream");
    if !is_audio && !is_octet {
        return Err(format!(
            "{err_prefix}: content-type `{ctype}` is not audio"
        ));
    }
    let content_length = resp.content_length();
    if let Some(len) = content_length {
        if len > max_bytes {
            return Err(format!(
                "{err_prefix}: content-length {len} B exceeds {max_bytes} B cap"
            ));
        }
    }
    let content_type = if is_audio {
        let parsed = ctype.split(';').next().unwrap_or("audio/mp4").trim();
        if parsed.is_empty() {
            "audio/mp4".into()
        } else {
            parsed.to_string()
        }
    } else {
        "audio/mp4".into()
    };
    Ok(OpenedAudioStream {
        reader: Box::new(resp),
        content_length,
        content_type,
        source_name,
    })
}

/// Issues a streaming (`STREAM_CLIENT`) or deadline-bounded (`SOURCE_CLIENT`)
/// audio GET request and validates it via [`validate_audio_response`].
fn send_and_validate_audio(
    url: &str,
    user_agent: Option<&str>,
    deadline: Instant,
    streaming: bool,
    allow_octet_stream: bool,
    source_name: String,
    err_prefix: &str,
) -> Result<OpenedAudioStream, String> {
    let budget = remaining(deadline)?;
    let resp = if streaming {
        let sclient = STREAM_CLIENT
            .as_ref()
            .ok_or_else(|| "stream HTTP client unavailable".to_string())?;
        let mut req = sclient.get(url);
        if let Some(ua) = user_agent {
            req = req.header(reqwest::header::USER_AGENT, ua);
        }
        // Intentionally omit RequestBuilder::timeout so reqwest::blocking keeps
        // STREAM_CLIENT's 15 s per-read timeout without cutting off long tracks.
        req.send().map_err(|e| format!("{err_prefix}: {e}"))?
    } else {
        let client = SOURCE_CLIENT
            .as_ref()
            .ok_or_else(|| "source HTTP client unavailable".to_string())?;
        let mut req = client.get(url).timeout(budget);
        if let Some(ua) = user_agent {
            req = req.header(reqwest::header::USER_AGENT, ua);
        }
        req.send().map_err(|e| format!("{err_prefix}: {e}"))?
    };
    validate_audio_response(
        resp,
        MAX_SOURCE_BYTES,
        allow_octet_stream,
        source_name,
        err_prefix,
    )
}

/// A single stream source (one instance). `open` must respect `deadline` for
/// its network phases; the download itself is deadline-checked chunk-by-chunk
/// by [`try_sources`], so implementations only need bounded metadata/open
/// behavior.
pub(crate) trait AudioSource: Send + Sync {
    fn name(&self) -> &str;
    /// Returns a reader over raw audio (m4a) bytes, or a failure reason.
    fn open(&self, video_id: &str, deadline: Instant) -> Result<SourceBody, String>;

    /// Opens the stream for progressive stream-while-download playback, preserving
    /// `Content-Length` / `Content-Type` and using a generous body-read timeout.
    fn open_stream(&self, video_id: &str, deadline: Instant) -> Result<OpenedAudioStream, String> {
        let reader = self.open(video_id, deadline)?;
        Ok(OpenedAudioStream {
            reader,
            content_length: None,
            content_type: "audio/mp4".into(),
            source_name: self.name().to_string(),
        })
    }
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

/// Subset of the Piped `/streams` response we consume. The stream array is
/// kept as raw values and parsed per element (`parse_audio_streams`) so one
/// malformed entry (or a null/missing array from a degraded instance) yields
/// fewer candidates instead of failing the whole metadata parse.
#[derive(Deserialize)]
struct PipedStreams {
    #[serde(default, rename = "audioStreams")]
    audio_streams: Option<Vec<serde_json::Value>>,
}

#[derive(Clone, Deserialize)]
struct PipedAudio {
    #[serde(default)]
    url: String,
    #[serde(default)]
    bitrate: Option<u64>,
    #[serde(default, rename = "mimeType")]
    mime_type: Option<String>,
}

/// Element-tolerant bridge: drops malformed entries instead of failing the
/// instance (a degraded response costs one skipped candidate, not one
/// wasted source attempt).
fn parse_audio_streams(meta: &PipedStreams) -> Vec<PipedAudio> {
    meta.audio_streams
        .as_deref()
        .unwrap_or(&[])
        .iter()
        .filter_map(|v| serde_json::from_value(v.clone()).ok())
        .collect()
}

/// Selects the best downloadable m4a from a Piped `audioStreams` array:
/// `audio/mp4` only, bitrate ≤ `cap`, highest first. Pure — exercised against
/// embedded fixtures including junk input.
fn select_best_m4a_within(streams: &[PipedAudio], cap: u64) -> Option<String> {
    streams
        .iter()
        .filter(|s| {
            s.mime_type
                .as_deref()
                .is_some_and(|m| m.starts_with("audio/mp4"))
        })
        .filter(|s| s.bitrate.is_some_and(|b| b > 0 && b <= cap))
        .filter(|s| s.url.starts_with("https://"))
        .max_by_key(|s| s.bitrate.unwrap_or(0))
        .map(|s| s.url.clone())
}

/// Selects the best playable direct audio stream (`audio/mp4` preferred, then
/// `audio/webm` Opus, then uncapped audio-only up to 512 kbps) so tracks that
/// only expose Opus or slightly higher bitrates still play seamlessly on
/// ExoPlayer (Android) and Symphonia (Desktop).
fn select_best_audio_within(streams: &[PipedAudio], cap: u64) -> Option<String> {
    if let Some(m4a) = select_best_m4a_within(streams, cap) {
        return Some(m4a);
    }
    if let Some(webm) = streams
        .iter()
        .filter(|s| {
            s.mime_type
                .as_deref()
                .is_some_and(|m| m.starts_with("audio/webm"))
        })
        .filter(|s| s.bitrate.is_some_and(|b| b > 0 && b <= cap))
        .filter(|s| s.url.starts_with("https://"))
        .max_by_key(|s| s.bitrate.unwrap_or(0))
        .map(|s| s.url.clone())
    {
        return Some(webm);
    }
    // Last resort: accept any audio/mp4 or audio/webm stream up to 512 kbps
    // (or missing bitrate field) rather than dropping the song entirely.
    streams
        .iter()
        .filter(|s| {
            s.mime_type
                .as_deref()
                .is_some_and(|m| m.starts_with("audio/mp4") || m.starts_with("audio/webm"))
        })
        .filter(|s| s.bitrate.unwrap_or(128_000) <= 512_000)
        .filter(|s| s.url.starts_with("https://"))
        .min_by_key(|s| s.bitrate.unwrap_or(128_000).abs_diff(cap))
        .map(|s| s.url.clone())
}

/// The default-cap variant — the unit-test target for the fixture suite.
#[cfg(test)]
fn select_best_m4a(streams: &[PipedAudio]) -> Option<String> {
    select_best_m4a_within(streams, MAX_AUDIO_BITRATE)
}

impl PipedSource {
    fn open_inner(
        &self,
        video_id: &str,
        deadline: Instant,
        streaming: bool,
    ) -> Result<OpenedAudioStream, String> {
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
        let cap = fast_path_bitrate_cap();
        let url = select_best_audio_within(&parse_audio_streams(&parsed), cap)
            .ok_or_else(|| {
                format!(
                    "piped {}: no usable audio stream ≤{}k",
                    self.host,
                    cap / 1000
                )
            })?;

        let err_prefix = format!("piped {}: download", self.host);
        send_and_validate_audio(
            &url,
            None,
            deadline,
            streaming,
            false,
            self.host.clone(),
            &err_prefix,
        )
    }
}

impl AudioSource for PipedSource {
    fn name(&self) -> &str {
        &self.host
    }

    fn open(&self, video_id: &str, deadline: Instant) -> Result<SourceBody, String> {
        self.open_inner(video_id, deadline, false).map(|o| o.reader)
    }

    fn open_stream(&self, video_id: &str, deadline: Instant) -> Result<OpenedAudioStream, String> {
        self.open_inner(video_id, deadline, true)
    }
}

/// Invidious: `GET /latest_version?id={id}&itag=140&local=true` streams the
/// audio through the instance proxy directly. The `/api/v1/videos` JSON's
/// googlevideo URLs are IP-locked to the instance — deliberately unused.
struct InvidiousSource {
    host: String,
}

impl InvidiousSource {
    fn open_inner(
        &self,
        video_id: &str,
        deadline: Instant,
        streaming: bool,
    ) -> Result<OpenedAudioStream, String> {
        let url = format!(
            "https://{}/latest_version?id={video_id}&itag=140&local=true",
            self.host
        );
        let err_prefix = format!("invidious {}", self.host);
        send_and_validate_audio(
            &url,
            None,
            deadline,
            streaming,
            false,
            self.host.clone(),
            &err_prefix,
        )
    }
}

impl AudioSource for InvidiousSource {
    fn name(&self) -> &str {
        &self.host
    }

    fn open(&self, video_id: &str, deadline: Instant) -> Result<SourceBody, String> {
        self.open_inner(video_id, deadline, false).map(|o| o.reader)
    }

    fn open_stream(&self, video_id: &str, deadline: Instant) -> Result<OpenedAudioStream, String> {
        self.open_inner(video_id, deadline, true)
    }
}

// ---------------------------------------------------------------------------
// InnerTube-direct multi-client director (primary source)
// ---------------------------------------------------------------------------

/// Pure-Rust InnerTube stream director — the primary resolve source, tried
/// before the instance pools. Implements the public InnerTube `/player`
/// protocol directly against YouTube's first-party endpoints, rotating
/// through a catalog of tokenless client profiles with playability-driven
/// failover, a visitor-data bootstrap, and per-profile health demotion.
///
/// The approach (and the client-profile values/benchmarks) follow the public
/// Metrolist playback matrix (MetrolistGroup/innertubex, GPL-3) — protocol
/// observations only; this is an independent MIT implementation sharing no
/// code with it, the same way yt-dlp and NewPipe independently implement the
/// same API. Why a director at all: as of the November 2026 catalog data the
/// ANDROID_VR family (our previous single client) resolves the player but
/// YouTube 403s sustained media ranges after the first 512 KiB, and the
/// third-party Piped/Invidious ecosystem bot-walls by the minute — the
/// first-party endpoints are the only path with no intermediary to churn.
struct InnerTubeDirectSource;

/// One tokenless, direct-transport InnerTube client profile.
#[derive(Debug, Clone, Copy, PartialEq)]
struct ItClientProfile {
    /// Diagnostic id (also the per-profile health key).
    id: &'static str,
    client_name: &'static str,
    client_version: &'static str,
    /// `X-YouTube-Client-Name` (numeric API client id).
    client_id: &'static str,
    user_agent: &'static str,
    os_name: Option<&'static str>,
    os_version: Option<&'static str>,
    device_make: Option<&'static str>,
    device_model: Option<&'static str>,
    android_sdk_version: Option<&'static str>,
    platform: Option<&'static str>,
    /// Mirror the UA into `context.client.userAgent` (device-profiled clients).
    ua_in_context: bool,
    /// The profile serves the media-player (`music.youtube.com`) endpoint —
    /// that endpoint omits `videoCheckOk` from the payload.
    music_player_endpoint: bool,
    /// Skip the profile when the visitor-data bootstrap failed (the catalog
    /// benchmark only observed direct audio from anonymous sessions carrying
    /// a visitor id).
    needs_visitor: bool,
}

/// Ordered failover catalog. Values are the public benchmark observations;
/// the ecosystem moves, so profiles demote only after repeated transport
/// failures and the instance pools remain behind this source as failover.
const IT_CLIENTS: &[ItClientProfile] = &[
    ItClientProfile {
        id: "VISIONOS_0_1",
        client_name: "VISIONOS",
        client_version: "0.1",
        client_id: "101",
        user_agent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
        os_name: Some("VISION_OS"),
        os_version: Some("1.3"),
        device_make: Some("Apple"),
        device_model: Some("RealityDevice14,1"),
        android_sdk_version: None,
        platform: Some("MOBILE"),
        ua_in_context: false,
        music_player_endpoint: true,
        needs_visitor: true,
    },
    ItClientProfile {
        id: "VISIONOS_1_02",
        client_name: "VISIONOS",
        client_version: "1.02",
        client_id: "101",
        user_agent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        os_name: Some("visionOS"),
        os_version: Some("26.5.23O471"),
        device_make: Some("Apple"),
        device_model: Some("RealityDevice17,1"),
        android_sdk_version: None,
        platform: None,
        ua_in_context: false,
        music_player_endpoint: true,
        needs_visitor: false,
    },
    ItClientProfile {
        id: "IOS_20",
        client_name: "IOS",
        client_version: "20.10.4",
        client_id: "5",
        user_agent: "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
        os_name: Some("iPhone"),
        os_version: Some("18.3.2.22D82"),
        device_make: Some("Apple"),
        device_model: Some("iPhone16,2"),
        android_sdk_version: None,
        platform: Some("MOBILE"),
        ua_in_context: false,
        music_player_endpoint: false,
        needs_visitor: false,
    },
    ItClientProfile {
        id: "ANDROID_MUSIC",
        client_name: "ANDROID_MUSIC",
        client_version: "8.05.51",
        client_id: "21",
        user_agent: "com.google.android.apps.youtube.music/8.05.51 (Linux; U; Android 14; en_US; Pixel 8 Pro Build/AP2A.240805.005) gzip",
        os_name: Some("Android"),
        os_version: Some("14"),
        device_make: Some("Google"),
        device_model: Some("Pixel 8 Pro"),
        android_sdk_version: Some("34"),
        platform: Some("MOBILE"),
        ua_in_context: false,
        music_player_endpoint: true,
        needs_visitor: false,
    },
    ItClientProfile {
        id: "TV_EMBEDDED",
        client_name: "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
        client_version: "2.0",
        client_id: "85",
        user_agent: "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
        os_name: None,
        os_version: None,
        device_make: None,
        device_model: None,
        android_sdk_version: None,
        platform: Some("TV"),
        ua_in_context: false,
        music_player_endpoint: false,
        needs_visitor: false,
    },
    ItClientProfile {
        id: "WEB_REMIX",
        client_name: "WEB_REMIX",
        client_version: "1.20260707.12.00",
        client_id: "67",
        user_agent: "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0",
        os_name: None,
        os_version: None,
        device_make: None,
        device_model: None,
        android_sdk_version: None,
        platform: None,
        ua_in_context: false,
        music_player_endpoint: false,
        needs_visitor: false,
    },
];

/// A demoted profile is skipped for 90 s after `IT_DEMOTE_THRESHOLD`
/// consecutive transport/HTTP failures; per-video playability errors never
/// demote a client profile, and any success immediately rehabilitates it.
const IT_DEMOTE: Duration = Duration::from_secs(90);
const IT_DEMOTE_THRESHOLD: u32 = 3;

#[derive(Default, Clone, Copy)]
struct ItClientHealth {
    consecutive_failures: u32,
    demoted_until: Option<Instant>,
}

#[allow(clippy::incompatible_msrv)] // file-wide pattern; MSRV raises with the 1.80 bump
static IT_HEALTH: LazyLock<std::sync::Mutex<std::collections::HashMap<&'static str, ItClientHealth>>> =
    LazyLock::new(|| std::sync::Mutex::new(std::collections::HashMap::new()));

fn it_demoted(id: &str) -> bool {
    let Ok(map) = IT_HEALTH.lock() else { return false };
    map.get(id)
        .and_then(|h| h.demoted_until)
        .is_some_and(|until| until > Instant::now())
}

fn it_record_transport_failure(id: &'static str) {
    if let Ok(mut map) = IT_HEALTH.lock() {
        let entry = map.entry(id).or_default();
        entry.consecutive_failures = entry.consecutive_failures.saturating_add(1);
        if entry.consecutive_failures >= IT_DEMOTE_THRESHOLD {
            entry.demoted_until = Some(Instant::now() + IT_DEMOTE);
        }
    }
}

/// Last-working InnerTube client profile id — tried first on subsequent
/// tracks to avoid wasting RTTs on degraded profiles.
#[allow(clippy::incompatible_msrv)]
static LAST_GOOD_IT_CLIENT: LazyLock<Mutex<Option<&'static str>>> =
    LazyLock::new(|| Mutex::new(None));

fn last_good_it_client() -> Option<&'static str> {
    LAST_GOOD_IT_CLIENT.lock().ok().and_then(|g| *g)
}

fn remember_good_it_client(id: &'static str) {
    if let Ok(mut g) = LAST_GOOD_IT_CLIENT.lock() {
        *g = Some(id);
    }
}

/// Orders InnerTube client profiles for minimal cold-start latency:
/// 1. Last-working profile first (when known).
/// 2. When `visitorData` is NOT warm yet (cold start right after launch),
///    visitor-free profiles (`VISIONOS_1_02`, `WEB_REMIX`) run BEFORE
///    visitor-gated profiles so playback never stalls 4–9 s fetching `sw.js_data`
///    + homepage HTML on the first click.
fn push_unique_it_client(
    out: &mut Vec<&'static ItClientProfile>,
    p: &'static ItClientProfile,
) {
    if !out.iter().any(|existing| existing.id == p.id) {
        out.push(p);
    }
}

fn ordered_it_clients(
    has_warm_visitor: bool,
    last_good: Option<&'static str>,
) -> Vec<&'static ItClientProfile> {
    let mut out: Vec<&'static ItClientProfile> = Vec::with_capacity(IT_CLIENTS.len());
    if let Some(good_id) = last_good {
        if let Some(p) = IT_CLIENTS.iter().find(|p| p.id == good_id) {
            if has_warm_visitor || !p.needs_visitor {
                out.push(p);
            }
        }
    }
    if has_warm_visitor {
        for p in IT_CLIENTS {
            push_unique_it_client(&mut out, p);
        }
    } else {
        for p in IT_CLIENTS.iter().filter(|p| !p.needs_visitor) {
            push_unique_it_client(&mut out, p);
        }
        for p in IT_CLIENTS.iter().filter(|p| p.needs_visitor) {
            push_unique_it_client(&mut out, p);
        }
    }
    out
}

fn it_rehab(id: &str) {
    if let Ok(mut map) = IT_HEALTH.lock() {
        map.remove(id);
    }
}

/// Bootstrap visitor data (YouTube's anonymous session id), cached
/// process-wide. `https://www.youtube.com/sw.js_data` serves it as JSON;
/// the music-homepage HTML carries it as a fallback. Values look like
/// URL-safe base64 with `%3D` padding and survive the plain-string scan
/// below; the 4 KB cap plus the quoted-value scan keep untrusted HTML from
/// being over-read (mirrors MAX_METADATA_BYTES discipline).
#[allow(clippy::incompatible_msrv)] // file-wide pattern; MSRV raises with the 1.80 bump
static VISITOR_CACHE: LazyLock<std::sync::Mutex<VisitorCache>> =
    LazyLock::new(|| std::sync::Mutex::new(None));
const VISITOR_TTL: Duration = Duration::from_secs(30 * 60);
const VISITOR_FAIL_COOLDOWN: Duration = Duration::from_secs(30);
const VISITOR_DATA_URL: &str = "https://www.youtube.com/sw.js_data";
const MUSIC_HOME_URL: &str = "https://music.youtube.com/";
const UA_WEB: &str = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0";

/// Extracts a visitor id from `sw.js_data` JSON or homepage HTML: the value
/// of the `VISITOR_DATA` / `visitorData` key, JSON-escape-processed.
fn extract_visitor_data(text: &str) -> Option<String> {
    for key in ["\"VISITOR_DATA\":\"", "\"visitorData\":\""] {
        let Some(start) = text.find(key) else { continue };
        let rest = &text[start + key.len()..];
        // Find the closing quote, honoring backslash escapes so an escaped
        // `\"` inside the value cannot terminate the scan early.
        let bytes = rest.as_bytes();
        let mut end = 0;
        while end < bytes.len() {
            match bytes[end] {
                b'\\' if end + 1 < bytes.len() => end += 2,
                b'"' => break,
                _ => end += 1,
            }
        }
        if end >= bytes.len() {
            continue;
        }
        let raw = &rest[..end];
        if raw.is_empty() {
            continue;
        }
        // Minimal JSON string unescape — visitor ids are base64/pct-encoded
        // ASCII, so only the two-char escapes can appear.
        let unescaped = raw
            .replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
        return Some(unescaped);
    }
    None
}

/// Cached visitor bootstrap state: `Ok((visitor id, fetched at))` while
/// fresh, `Err(cooldown deadline)` after both probes failed.
type VisitorCache = Option<Result<(String, Instant), Instant>>;

/// Pre-warms the dedicated HTTP/2 source/stream clients and InnerTube
/// `visitorData` token in a background thread at app launch so the first track
/// play skips TLS initialization and visitor bootstrap latency.
pub(crate) fn prewarm_async() {
    let _ = std::thread::Builder::new()
        .name("musico-sources-prewarm".into())
        .spawn(|| {
            if crate::config::AppSettings::load().offline_mode {
                return;
            }
            let _ = LazyLock::force(&STREAM_CLIENT);
            if let Some(client) = SOURCE_CLIENT.as_ref() {
                let _ = visitor_data_bounded(client, Instant::now() + Duration::from_secs(6), true);
            }
        });
}

/// Instant lock-only check for an already-warm `visitorData` token.
fn cached_visitor_data() -> Option<String> {
    let cache = VISITOR_CACHE.lock().ok()?;
    match &*cache {
        Some(Ok((vd, at))) if at.elapsed() < VISITOR_TTL => Some(vd.clone()),
        _ => None,
    }
}

/// Cached visitor bootstrap; `None` when probes failed recently.
/// `allow_html_fallback = false` keeps the hot interactive play path fast
/// (never spending 5 s downloading the full YouTube Music HTML shell).
fn visitor_data_bounded(
    client: &reqwest::blocking::Client,
    deadline: Instant,
    allow_html_fallback: bool,
) -> Option<String> {
    {
        let cache = VISITOR_CACHE.lock().ok()?;
        match &*cache {
            Some(Ok((vd, at))) if at.elapsed() < VISITOR_TTL => return Some(vd.clone()),
            Some(Err(cooled)) if *cooled > Instant::now() => return None,
            _ => {}
        }
    }
    let fetched = (|| {
        let left = remaining(deadline).ok()?;
        let sw_budget = left.min(if allow_html_fallback {
            Duration::from_secs(4)
        } else {
            Duration::from_millis(2_000)
        });
        // 1) Service-worker data endpoint (JSON, small).
        if let Ok(resp) = client
            .get(VISITOR_DATA_URL)
            .header(reqwest::header::USER_AGENT, UA_WEB)
            .header(reqwest::header::ACCEPT, "application/json,text/plain,*/*")
            .timeout(sw_budget)
            .send()
        {
            if resp.status().is_success() {
                if let Ok(bytes) = read_capped(resp, 1024 * 1024) {
                    if let Some(vd) = extract_visitor_data(&String::from_utf8_lossy(&bytes)) {
                        return Some(vd);
                    }
                }
            }
        }
        if !allow_html_fallback {
            return None;
        }
        let html_budget = remaining(deadline).ok()?.min(Duration::from_secs(5));
        // 2) Music homepage HTML fallback (background prewarm only).
        if let Ok(resp) = client
            .get(MUSIC_HOME_URL)
            .header(reqwest::header::USER_AGENT, UA_WEB)
            .header(
                reqwest::header::ACCEPT,
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            )
            .timeout(html_budget)
            .send()
        {
            if resp.status().is_success() {
                if let Ok(bytes) = read_capped(resp, MAX_METADATA_BYTES) {
                    if let Some(vd) = extract_visitor_data(&String::from_utf8_lossy(&bytes)) {
                        return Some(vd);
                    }
                }
            }
        }
        None
    })();
    let mut cache = VISITOR_CACHE.lock().ok()?;
    match &fetched {
        Some(vd) => *cache = Some(Ok((vd.clone(), Instant::now()))),
        None if allow_html_fallback => *cache = Some(Err(Instant::now() + VISITOR_FAIL_COOLDOWN)),
        None => {}
    }
    fetched
}

/// Builds the `/player` payload for one profile. The music-player endpoint
/// omits `videoCheckOk`; device-profiled fields land only when declared.
fn build_player_payload(
    p: &ItClientProfile,
    video_id: &str,
    visitor: Option<&str>,
) -> serde_json::Value {
    let mut c = serde_json::json!({
        "clientName": p.client_name,
        "clientVersion": p.client_version,
        "hl": "en",
        "gl": "US",
    });
    if let Some(v) = visitor {
        c["visitorData"] = serde_json::json!(v);
    }
    if p.ua_in_context {
        c["userAgent"] = serde_json::json!(p.user_agent);
    }
    for (key, val) in [
        ("osName", p.os_name),
        ("osVersion", p.os_version),
        ("deviceMake", p.device_make),
        ("deviceModel", p.device_model),
        ("androidSdkVersion", p.android_sdk_version),
        ("platform", p.platform),
    ] {
        if let Some(v) = val {
            c[key] = serde_json::json!(v);
        }
    }
    let mut context = serde_json::json!({ "client": c });
    if p.client_name == "TVHTML5_SIMPLY_EMBEDDED_PLAYER" {
        context["thirdParty"] = serde_json::json!({
            "embedUrl": "https://www.youtube.com/",
        });
    }
    let mut body = serde_json::json!({
        "context": context,
        "videoId": video_id,
        "contentCheckOk": true,
        "racyCheckOk": true,
    });
    if !p.music_player_endpoint {
        body["videoCheckOk"] = serde_json::json!(true);
    }
    body
}

/// `playabilityStatus.status` of a player response (`None` when absent —
/// a body without it is not the API we asked for).
fn playability_status(body: &[u8]) -> Option<String> {
    let val = serde_json::from_slice::<serde_json::Value>(body).ok()?;
    val.get("playabilityStatus")?
        .get("status")?
        .as_str()
        .map(str::to_string)
}

/// A stream URL carrying an `n` query parameter needs YouTube's JS
/// n-transform before it will serve — treat those as not playback-ready.
fn has_n_param(url: &str) -> bool {
    let lower = url.to_ascii_lowercase();
    lower.contains("?n=") || lower.contains("&n=")
}

/// Filters parsed candidates down to playback-ready direct URLs.
fn playback_ready(candidates: Vec<PipedAudio>) -> Vec<PipedAudio> {
    candidates.into_iter().filter(|c| !has_n_param(&c.url)).collect()
}

fn parse_innertube_player_streams(body: &[u8]) -> Vec<PipedAudio> {
    let Ok(val) = serde_json::from_slice::<serde_json::Value>(body) else {
        return Vec::new();
    };
    let Some(sd) = val.get("streamingData") else {
        return Vec::new();
    };
    let mut out = Vec::new();
    for key in ["adaptiveFormats", "formats"] {
        if let Some(arr) = sd.get(key).and_then(|v| v.as_array()) {
            for item in arr {
                let Some(url) = item.get("url").and_then(|v| v.as_str()) else {
                    continue;
                };
                let mime_type = item
                    .get("mimeType")
                    .and_then(|v| v.as_str())
                    .map(str::to_string);
                let bitrate = item
                    .get("bitrate")
                    .and_then(|v| v.as_u64())
                    .or_else(|| item.get("averageBitrate").and_then(|v| v.as_u64()));
                out.push(PipedAudio {
                    url: url.to_string(),
                    bitrate,
                    mime_type,
                });
            }
        }
    }
    out
}

impl InnerTubeDirectSource {
    const NAME: &'static str = "innertube-direct";

    fn try_profile(
        p: &'static ItClientProfile,
        video_id: &str,
        warm_visitor: Option<String>,
        deadline: Instant,
        streaming: bool,
    ) -> Result<OpenedAudioStream, String> {
        let client = SOURCE_CLIENT
            .as_ref()
            .ok_or_else(|| "source HTTP client unavailable".to_string())?;
        if remaining(deadline).is_err() {
            return Err(format!("{}: fast-path deadline exhausted", p.id));
        }
        let visitor = if p.needs_visitor {
            warm_visitor.or_else(|| visitor_data_bounded(client, deadline, false))
        } else {
            warm_visitor
        };
        if p.needs_visitor && visitor.is_none() {
            return Err(format!("{}: visitor data unavailable", p.id));
        }
        let payload = build_player_payload(p, video_id, visitor.as_deref());
        let (endpoint, origin, referer) = if p.music_player_endpoint {
            (
                "https://music.youtube.com/youtubei/v1/player?prettyPrint=false",
                "https://music.youtube.com",
                "https://music.youtube.com/",
            )
        } else {
            (
                "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
                "https://www.youtube.com",
                "https://www.youtube.com/",
            )
        };

        let meta_budget = remaining(deadline)?.min(METADATA_TIMEOUT);
        let mut req = client
            .post(endpoint)
            .header(reqwest::header::USER_AGENT, p.user_agent)
            .header(reqwest::header::CONTENT_TYPE, "application/json")
            .header("X-Goog-Api-Format-Version", "1")
            .header("X-YouTube-Client-Name", p.client_id)
            .header("X-YouTube-Client-Version", p.client_version)
            .header(reqwest::header::ORIGIN, origin)
            .header("X-Origin", origin)
            .header(reqwest::header::REFERER, referer)
            .header(reqwest::header::ACCEPT_LANGUAGE, "en-US,en;q=0.9")
            .timeout(meta_budget);
        if let Some(v) = &visitor {
            req = req.header("X-Goog-Visitor-Id", v);
        }
        let meta = match req.json(&payload).send() {
            Ok(r) => r,
            Err(e) => {
                it_record_transport_failure(p.id);
                return Err(format!("{}: player request: {e}", p.id));
            }
        };
        if !meta.status().is_success() {
            let status = meta.status();
            if status.as_u16() == 403 || status.as_u16() == 429 || status.is_server_error() {
                it_record_transport_failure(p.id);
            }
            return Err(format!("{}: HTTP {}", p.id, status));
        }
        let bytes = match read_capped(meta, MAX_METADATA_BYTES) {
            Ok(b) => b,
            Err(e) => {
                it_record_transport_failure(p.id);
                return Err(format!("{}: body: {e}", p.id));
            }
        };
        match playability_status(&bytes).as_deref() {
            Some("OK") | Some("LIVE_STREAM_OFFLINE") => {}
            other => {
                // Per-video playability errors (age-gate, region lock, etc.)
                // must NEVER demote a healthy client profile for other tracks.
                return Err(format!(
                    "{}: playability {}",
                    p.id,
                    other.unwrap_or("response missing playabilityStatus")
                ));
            }
        }
        let all_streams = parse_innertube_player_streams(&bytes);
        let ready = playback_ready(all_streams.clone());
        // Capture the track's loudness metadata for the normalization hook
        // before anything can fail below.
        capture_stream_loudness(&bytes, video_id);
        let cap = fast_path_bitrate_cap();
        let url = select_best_audio_within(&ready, cap)
            .or_else(|| select_best_audio_within(&all_streams, cap))
            .ok_or_else(|| {
                format!(
                    "{}: no direct audio stream (cipher-only formats)",
                    p.id
                )
            })?;

        let err_prefix = format!("{}: download", p.id);
        match send_and_validate_audio(
            &url,
            Some(p.user_agent),
            deadline,
            streaming,
            true,
            format!("{}:{}", Self::NAME, p.id),
            &err_prefix,
        ) {
            Ok(opened) => {
                it_rehab(p.id);
                remember_good_it_client(p.id);
                log::info!("innertube-direct: {} serving {video_id}", p.id);
                Ok(opened)
            }
            Err(e) => {
                if e.contains("HTTP 403") || e.contains("HTTP 429") || e.contains("HTTP 5") {
                    it_record_transport_failure(p.id);
                }
                Err(e)
            }
        }
    }

    fn open_inner(
        &self,
        video_id: &str,
        deadline: Instant,
        streaming: bool,
    ) -> Result<OpenedAudioStream, String> {
        let mut last_err = String::from("innertube-direct:");

        let warm_visitor = cached_visitor_data();
        let all_profiles = ordered_it_clients(warm_visitor.is_some(), last_good_it_client());
        let mut active: Vec<&'static ItClientProfile> = all_profiles
            .iter()
            .copied()
            .filter(|p| !it_demoted(p.id))
            .collect();
        // If every profile happened to hit temporary cooldown, still try all
        // profiles in catalog order instead of returning empty-handed.
        if active.is_empty() {
            active = all_profiles;
        }

        // Race the top 2 eligible profiles in parallel so a stalled or
        // cipher-only primary client never adds a full RTT before backup wins.
        let race_count = active.len().min(2);
        if race_count >= 2 && remaining(deadline).is_ok() {
            let (tx, rx) = std::sync::mpsc::channel::<Result<OpenedAudioStream, String>>();
            for &profile in &active[..race_count] {
                let tx_c = tx.clone();
                let vid = video_id.to_string();
                let v_clone = warm_visitor.clone();
                let _ = std::thread::Builder::new()
                    .name(format!("musico-it-{}", profile.id))
                    .spawn(move || {
                        let res = Self::try_profile(profile, &vid, v_clone, deadline, streaming);
                        let _ = tx_c.send(res);
                    });
            }
            drop(tx);
            for _ in 0..race_count {
                let Ok(wait) = remaining(deadline) else {
                    return Err(format!("{last_err}; fast-path deadline exhausted"));
                };
                match rx.recv_timeout(wait) {
                    Ok(Ok(opened)) => return Ok(opened),
                    Ok(Err(e)) => last_err = format!("{last_err}; {e}"),
                    Err(_) => break,
                }
            }
        } else if let Some(&first) = active.first() {
            match Self::try_profile(first, video_id, warm_visitor.clone(), deadline, streaming) {
                Ok(opened) => return Ok(opened),
                Err(e) => last_err = format!("{last_err}; {e}"),
            }
        }

        // Walk any remaining profiles sequentially if the top-2 race didn't hit.
        for &p in active.iter().skip(race_count.max(1)) {
            if remaining(deadline).is_err() {
                return Err(format!("{last_err}; fast-path deadline exhausted"));
            }
            match Self::try_profile(p, video_id, warm_visitor.clone(), deadline, streaming) {
                Ok(opened) => return Ok(opened),
                Err(e) => last_err = format!("{last_err}; {e}"),
            }
        }
        Err(last_err)
    }
}

impl AudioSource for InnerTubeDirectSource {
    fn name(&self) -> &str {
        Self::NAME
    }

    fn open(&self, video_id: &str, deadline: Instant) -> Result<SourceBody, String> {
        self.open_inner(video_id, deadline, false).map(|o| o.reader)
    }

    fn open_stream(&self, video_id: &str, deadline: Instant) -> Result<OpenedAudioStream, String> {
        self.open_inner(video_id, deadline, true)
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
            // Append, don't overwrite: the concrete per-instance reasons
            // above are the debuggable part.
            last_err =
                format!("{last_err}; fast-path deadline exhausted before all sources were tried");
            break;
        }
        let attempt_start = Instant::now();
        match source.open(video_id, deadline) {
            Ok(body) => match stream_to_file(body, &part, &final_path, deadline) {
                Ok(bytes) => {
                    record_instance_outcome(source.name(), true, attempt_start.elapsed());
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
                    record_instance_outcome(source.name(), false, attempt_start.elapsed());
                    log::debug!("fast path: {} download failed: {e}", source.name());
                    last_err = e;
                }
            },
            Err(e) => {
                record_instance_outcome(source.name(), false, attempt_start.elapsed());
                log::debug!("fast path: {} unavailable: {e}", source.name());
                last_err = e;
            }
        }
    }
    let _ = std::fs::remove_file(&part);
    Err(last_err)
}

/// Production entry: the default pool against the shared fast-path budget.
pub(crate) fn try_fast_path(video_id: &str) -> Result<PathBuf, String> {
    try_sources(&default_pool(), video_id, FAST_PATH_BUDGET)
}

/// Hedged concurrent stream opener: starts the primary source immediately at
/// `t = 0`, and if it fails early or hasn't returned an open stream within
/// `hedge_delay`, races up to [`STREAM_HEDGE_BATCH`] backup sources in parallel.
/// Returns the FIRST working [`OpenedAudioStream`] immediately without waiting
/// for slower/stalled sources to time out.
pub(crate) fn try_sources_open_stream_hedged(
    sources: Vec<Arc<dyn AudioSource>>,
    video_id: &str,
    budget: Duration,
    hedge_delay: Duration,
) -> Result<OpenedAudioStream, String> {
    if !valid_video_id(video_id) {
        return Err(format!("invalid video id `{video_id}`"));
    }
    if sources.is_empty() {
        return Err("no sources configured".into());
    }

    let deadline = Instant::now() + budget;
    let won = Arc::new(AtomicBool::new(false));
    let (tx, rx) = std::sync::mpsc::channel::<Result<OpenedAudioStream, String>>();

    let spawn_one = |src: Arc<dyn AudioSource>,
                     vid: String,
                     tx: std::sync::mpsc::Sender<Result<OpenedAudioStream, String>>,
                     won: Arc<AtomicBool>| {
        let _ = std::thread::Builder::new()
            .name("musico-stream-open".into())
            .spawn(move || {
                let t0 = Instant::now();
                let res = src.open_stream(&vid, deadline);
                let ok = res.is_ok();
                if !won.load(Ordering::Acquire) {
                    record_instance_outcome(src.name(), ok, t0.elapsed());
                    if let Err(ref e) = res {
                        log::debug!("fast path stream: {} unavailable: {e}", src.name());
                    }
                    let _ = tx.send(res);
                }
            });
    };

    let mut next_idx = 0usize;
    let mut in_flight = 0usize;
    let mut last_err = String::from("no sources configured");

    let launch_wave = |next_idx: &mut usize, in_flight: &mut usize, count: usize| {
        let wave_end = (*next_idx + count).min(sources.len());
        while *next_idx < wave_end {
            spawn_one(
                Arc::clone(&sources[*next_idx]),
                video_id.to_string(),
                tx.clone(),
                Arc::clone(&won),
            );
            *next_idx += 1;
            *in_flight += 1;
        }
    };

    // Launch primary source (InnerTube Direct) immediately.
    launch_wave(&mut next_idx, &mut in_flight, 1);
    let mut next_hedge_at = Instant::now() + hedge_delay;

    loop {
        let now = Instant::now();
        if now >= deadline {
            won.store(true, Ordering::Release);
            return Err(format!(
                "{last_err}; fast-path deadline exhausted before a stream opened"
            ));
        }

        let rem = deadline.saturating_duration_since(now);
        let wait = if next_idx < sources.len() {
            rem.min(next_hedge_at.saturating_duration_since(now))
        } else {
            rem
        };

        match rx.recv_timeout(wait) {
            Ok(Ok(opened)) => {
                won.store(true, Ordering::Release);
                return Ok(opened);
            }
            Ok(Err(e)) => {
                in_flight = in_flight.saturating_sub(1);
                last_err = e;
                // All in-flight sources failed early: launch next wave right away.
                // If others are still running, preserve `next_hedge_at` unchanged.
                if in_flight == 0 && next_idx < sources.len() {
                    launch_wave(&mut next_idx, &mut in_flight, STREAM_HEDGE_BATCH);
                    next_hedge_at = Instant::now() + hedge_delay;
                } else if in_flight == 0 {
                    won.store(true, Ordering::Release);
                    return Err(last_err);
                }
            }
            Err(std::sync::mpsc::RecvTimeoutError::Timeout) => {
                if next_idx < sources.len() && Instant::now() < deadline {
                    launch_wave(&mut next_idx, &mut in_flight, STREAM_HEDGE_BATCH);
                    next_hedge_at = Instant::now() + hedge_delay;
                } else if Instant::now() >= deadline || in_flight == 0 {
                    won.store(true, Ordering::Release);
                    return Err(format!(
                        "{last_err}; fast-path deadline exhausted before a stream opened"
                    ));
                }
            }
            Err(std::sync::mpsc::RecvTimeoutError::Disconnected) => {
                won.store(true, Ordering::Release);
                return Err(last_err);
            }
        }
    }
}

/// Production streaming open: hedged race across the default source pool,
/// returning the first open audio stream for progressive loopback playback.
pub(crate) fn try_fast_path_open_stream(video_id: &str) -> Result<OpenedAudioStream, String> {
    try_sources_open_stream_hedged(
        default_pool_arc(),
        video_id,
        FAST_PATH_BUDGET,
        STREAM_HEDGE_DELAY,
    )
}

/// Parses `videoDetails` from an InnerTube `/player` response into an
/// [`super::models::OnlineTrack`] for one-time enrichment of legacy cache stems.
pub(crate) fn parse_video_details_meta(
    bytes: &[u8],
    video_id: &str,
) -> Option<super::models::OnlineTrack> {
    let val: serde_json::Value = serde_json::from_slice(bytes).ok()?;
    let details = val.get("videoDetails")?;
    let title = details.get("title")?.as_str()?.trim().to_string();
    if title.is_empty() || title == video_id {
        return None;
    }
    let raw_author = details
        .get("author")
        .and_then(|v| v.as_str())
        .unwrap_or_default()
        .trim();
    let artist = raw_author
        .strip_suffix(" - Topic")
        .unwrap_or(raw_author)
        .trim()
        .to_string();
    let duration_secs = details
        .get("lengthSeconds")
        .and_then(|v| {
            v.as_u64()
                .or_else(|| v.as_str().and_then(|s| s.trim().parse::<u64>().ok()))
        })
        .filter(|&d| d > 0)
        .map(|d| d as u32);
    let thumbnail = details
        .pointer("/thumbnail/thumbnails")
        .and_then(|v| v.as_array())
        .and_then(|arr| arr.last())
        .and_then(|t| t.get("url"))
        .and_then(|u| u.as_str())
        .map(str::to_string);
    Some(super::models::OnlineTrack {
        video_id: video_id.to_string(),
        title,
        artist,
        album: String::new(),
        duration_secs,
        thumbnail,
        cached: true,
        source: String::new(),
        artist_browse_id: details
            .get("channelId")
            .and_then(|v| v.as_str())
            .map(str::to_string),
    })
}

/// Best-effort single-request metadata enrichment for a legacy YouTube video id
/// whose audio file predates sidecar/sled persistence. Tries InnerTube `/player`
/// `videoDetails` first, falling back to YouTube oEmbed.
pub(crate) fn fetch_video_metadata(video_id: &str) -> Option<super::models::OnlineTrack> {
    if video_id.is_empty()
        || video_id.len() > 64
        || !video_id
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
    {
        return None;
    }
    let client = SOURCE_CLIENT.as_ref()?;
    let budget = Duration::from_secs(5);

    if let Some(p) = IT_CLIENTS.last() {
        let payload = build_player_payload(p, video_id, None);
        if let Ok(resp) = client
            .post("https://music.youtube.com/youtubei/v1/player?prettyPrint=false")
            .header(reqwest::header::USER_AGENT, p.user_agent)
            .header(reqwest::header::CONTENT_TYPE, "application/json")
            .header("X-YouTube-Client-Name", p.client_id)
            .header("X-YouTube-Client-Version", p.client_version)
            .header(reqwest::header::ORIGIN, "https://music.youtube.com")
            .timeout(budget)
            .json(&payload)
            .send()
        {
            if resp.status().is_success() {
                if let Ok(bytes) = read_capped(resp, MAX_METADATA_BYTES) {
                    if let Some(track) = parse_video_details_meta(&bytes, video_id) {
                        return Some(track);
                    }
                }
            }
        }
    }

    // Lightweight oEmbed fallback (returns title, author_name, thumbnail_url).
    let oembed_url = format!(
        "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v={video_id}&format=json"
    );
    let resp = client.get(&oembed_url).timeout(budget).send().ok()?;
    if !resp.status().is_success() {
        return None;
    }
    let bytes = read_capped(resp, 128 * 1024).ok()?;
    let v: serde_json::Value = serde_json::from_slice(&bytes).ok()?;
    let title = v.get("title")?.as_str()?.trim().to_string();
    if title.is_empty() || title == video_id {
        return None;
    }
    let raw_author = v
        .get("author_name")
        .and_then(|a| a.as_str())
        .unwrap_or_default()
        .trim();
    let artist = raw_author
        .strip_suffix(" - Topic")
        .unwrap_or(raw_author)
        .trim()
        .to_string();
    let thumbnail = v
        .get("thumbnail_url")
        .and_then(|t| t.as_str())
        .map(str::to_string);
    Some(super::models::OnlineTrack {
        video_id: video_id.to_string(),
        title,
        artist,
        album: String::new(),
        duration_secs: None,
        thumbnail,
        cached: true,
        source: String::new(),
        artist_browse_id: None,
    })
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
    // Force the bytes to disk BEFORE the rename publishes the file: without
    // this, a crash between rename and OS flush leaves a truncated file that
    // `cached_file`'s existence check would trust forever.
    file.sync_all().map_err(|e| format!("sync: {e}"))?;
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
        let meta: PipedStreams = serde_json::from_str(json).expect("fixture parses");
        parse_audio_streams(&meta)
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

    #[test]
    fn per_element_tolerance_drops_malformed_entries() {
        // Null/missing array → zero candidates (parse succeeds; a wrong-typed
        // field like "audioStreams":"oops" honestly fails the instance —
        // Option only maps null, and that's failover's job).
        for body in [r#"{"audioStreams":null}"#, r#"{}"#] {
            let meta: PipedStreams = serde_json::from_str(body).expect("null/absent array parses");
            assert!(parse_audio_streams(&meta).is_empty());
        }
        // Within a well-formed array, junk entries are dropped, not fatal.
        let meta: PipedStreams = serde_json::from_str(
            r#"{"audioStreams":[
                {"url":"https://a/good","bitrate":128000,"mimeType":"audio/mp4"},
                {"url":null,"bitrate":128000,"mimeType":"audio/mp4"},
                {"bitrate":"128000","mimeType":"audio/mp4"},
                17
            ]}"#,
        )
        .expect("array parses");
        let parsed = parse_audio_streams(&meta);
        assert_eq!(parsed.len(), 1, "only the well-formed entry survives");
        assert_eq!(select_best_m4a(&parsed).as_deref(), Some("https://a/good"));
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

    /// 12 KB payloads — pass MIN_FILE_BYTES (10 KB). Distinct bytes so the
    /// test proves WHICH source won, not just that one did.
    const GOOD_AUDIO_A: &[u8] = &[0x61; 12 * 1024];
    const GOOD_AUDIO_B: &[u8] = &[0x62; 12 * 1024];

    #[test]
    fn first_success_wins_in_pool_order() {
        let _tc = crate::online::stream::test_support::TestCache::acquire();
        let sources: Vec<Box<dyn AudioSource>> = vec![
            MockSource::failing("dead", "down"),
            MockSource::ok("first-good", GOOD_AUDIO_B),
            MockSource::ok("never-reached", GOOD_AUDIO_A),
        ];
        let path = try_sources(&sources, "mockvid00001", Duration::from_secs(5))
            .expect("second source succeeds");
        assert!(path.ends_with("mockvid00001.m4a"));
        // The winner is the SECOND source: its distinct bytes are on disk,
        // so neither the failing first nor the never-reached third won.
        let bytes = std::fs::read(&path).unwrap();
        assert_eq!(bytes, GOOD_AUDIO_B, "pool order: first success wins");
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
        assert!(
            t0.elapsed() < Duration::from_millis(1500),
            "bail was prompt: {:?}",
            t0.elapsed()
        );
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
                try_sources(&[MockSource::ok("x", GOOD_AUDIO_A)], bad, Duration::from_secs(1))
                    .is_err()
            );
        }
        // Validation precedes any filesystem access — nothing was created.
        assert!(
            std::fs::read_dir(crate::online::stream::cache_dir())
                .map(|entries| entries.count())
                .unwrap_or(0)
                == 0,
            "cache dir untouched by invalid ids"
        );
    }

    #[test]
    fn health_board_demotes_failing_instance_and_promotes_fast_one() {
        let _tc = crate::online::stream::test_support::TestCache::acquire();
        // Run two attempts where `piped:bad.example` fails twice and `piped:good.example` succeeds.
        for vid in ["mockvid00010", "mockvid00011"] {
            let sources: Vec<Box<dyn AudioSource>> = vec![
                MockSource::failing("piped:bad.example", "502 Bad Gateway"),
                MockSource::ok("piped:good.example", GOOD_AUDIO_A),
            ];
            try_sources(&sources, vid, Duration::from_secs(5)).expect("good source succeeds");
        }
        let mut specs = vec![
            SourceSpec { host: "bad.example".into(), kind: SourceKind::Piped },
            SourceSpec { host: "good.example".into(), kind: SourceKind::Piped },
        ];
        sort_specs_by_health(&mut specs);
        assert_eq!(specs[0].host, "good.example", "healthy host must sort ahead of demoted failing host");
        assert_eq!(specs[1].host, "bad.example");
    }

    #[test]
    fn parses_innertube_android_vr_adaptive_formats_for_best_m4a() {
        let body = br#"{
            "streamingData": {
                "adaptiveFormats": [
                    {
                        "itag": 251,
                        "url": "https://rr1---sn-abc.googlevideo.com/videoplayback?itag=251",
                        "mimeType": "audio/webm; codecs=\"opus\"",
                        "bitrate": 160000
                    },
                    {
                        "itag": 140,
                        "url": "https://rr1---sn-abc.googlevideo.com/videoplayback?itag=140",
                        "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                        "bitrate": 130482
                    },
                    {
                        "itag": 139,
                        "url": "https://rr1---sn-abc.googlevideo.com/videoplayback?itag=139",
                        "mimeType": "audio/mp4; codecs=\"mp4a.40.5\"",
                        "bitrate": 49800
                    }
                ]
            }
        }"#;
        let parsed = parse_innertube_player_streams(body);
        assert_eq!(parsed.len(), 3);
        assert_eq!(
            select_best_m4a(&parsed).as_deref(),
            Some("https://rr1---sn-abc.googlevideo.com/videoplayback?itag=140")
        );
    }

    // -- InnerTube-direct director (pure helpers) -----------------------------

    fn pa(url: &str, bitrate: Option<u64>) -> PipedAudio {
        PipedAudio {
            url: url.to_string(),
            bitrate,
            mime_type: Some("audio/mp4; codecs=\"mp4a.40.2\"".to_string()),
        }
    }

    #[test]
    fn visitor_data_extracts_from_service_worker_json() {
        let text = r#"({"VISITOR_DATA":"CgtAbc%3D%3Dg","Path":"/sw.js_data"};"#;
        assert_eq!(extract_visitor_data(text).as_deref(), Some("CgtAbc%3D%3Dg"));
    }

    #[test]
    fn visitor_data_extracts_lowercase_key_and_unescapes() {
        let text = r#"{"visitorData":"Cg\/abc\"def"}"#;
        assert_eq!(extract_visitor_data(text).as_deref(), Some(r#"Cg/abc"def"#));
    }

    #[test]
    fn visitor_data_absent_or_empty_is_none() {
        assert_eq!(extract_visitor_data("<html>nope</html>"), None);
        assert_eq!(extract_visitor_data("\"VISITOR_DATA\":\"\""), None);
    }

    #[test]
    fn tier_cap_unlocks_256k_only_on_high_tiers() {
        assert_eq!(tier_cap("high", "low"), HIGH_TIER_AUDIO_BITRATE);
        assert_eq!(tier_cap("lossless", "normal"), HIGH_TIER_AUDIO_BITRATE);
        assert_eq!(tier_cap("low", "high"), HIGH_TIER_AUDIO_BITRATE);
        assert_eq!(tier_cap("low", "normal"), MAX_AUDIO_BITRATE);
        assert_eq!(tier_cap("normal", "low"), MAX_AUDIO_BITRATE);
        // Unknown tiers stay conservative.
        assert_eq!(tier_cap("", ""), MAX_AUDIO_BITRATE);
    }

    #[test]
    fn high_tier_selector_accepts_256k_stream() {
        let streams = vec![
            pa("https://a/140", Some(128_000)),
            pa("https://a/256", Some(256_000)),
        ];
        assert_eq!(
            select_best_m4a_within(&streams, HIGH_TIER_AUDIO_BITRATE).as_deref(),
            Some("https://a/256")
        );
        // The default cap still rejects it (video-derived guard).
        assert_eq!(select_best_m4a(&streams).as_deref(), Some("https://a/140"));
    }

    #[test]
    fn stream_loudness_roundtrip() {
        // Unique ids keep these tests independent of the shared static and of
        // parallel tests.
        store_stream_loudness("loud-roundtrip-perceptual", Some(2.0), Some(-13.5));
        assert_eq!(stream_loudness("loud-roundtrip-perceptual"), Some(-13.5));

        // Without a perceptual value the measured loudness is reconstructed
        // from the delta above the YouTube reference level (-7 LUFS).
        store_stream_loudness("loud-roundtrip-delta", Some(-2.5), None);
        assert_eq!(stream_loudness("loud-roundtrip-delta"), Some(-9.5));

        // Nothing captured → None (Piped/yt-dlp/Saavn resolves).
        assert_eq!(stream_loudness("loud-roundtrip-unknown"), None);
    }

    #[test]
    fn capture_stream_loudness_reads_audio_config() {
        let body = br#"{
            "playabilityStatus": {"status": "OK"},
            "playerConfig": {
                "audioConfig": {
                    "loudnessDb": 1.25,
                    "perceptualLoudnessDb": -11.75
                }
            }
        }"#;
        capture_stream_loudness(body, "loud-capture-perceptual");
        assert_eq!(stream_loudness("loud-capture-perceptual"), Some(-11.75));

        // Delta-only shape (no perceptual field).
        let body = br#"{
            "playerConfig": {"audioConfig": {"loudnessDb": 0.5}}
        }"#;
        capture_stream_loudness(body, "loud-capture-delta");
        assert_eq!(stream_loudness("loud-capture-delta"), Some(-6.5));

        // Missing audioConfig or a shape change leaves nothing behind.
        capture_stream_loudness(br#"{"playabilityStatus":{}}"#, "loud-capture-absent");
        assert_eq!(stream_loudness("loud-capture-absent"), None);
    }

    #[test]
    fn n_param_urls_are_not_playback_ready() {
        let cands = vec![
            pa("https://googlevideo/videoplayback?n=abc&id=1", Some(128_000)),
            pa("https://googlevideo/videoplayback?id=2", Some(64_000)),
        ];
        let ready = playback_ready(cands);
        assert_eq!(ready.len(), 1);
        assert!(ready[0].url.contains("id=2"));
    }

    #[test]
    fn payload_shapes_match_endpoint_profiles() {
        let visionos = &IT_CLIENTS[0];
        assert_eq!(visionos.id, "VISIONOS_0_1");
        assert!(visionos.music_player_endpoint && visionos.needs_visitor);
        let body = build_player_payload(visionos, "vid12345678", Some("VD%3D"));
        let c = &body["context"]["client"];
        assert_eq!(c["clientName"], "VISIONOS");
        assert_eq!(c["osName"], "VISION_OS");
        assert_eq!(c["visitorData"], "VD%3D");
        assert!(body.get("videoCheckOk").is_none());
        assert!(c.get("userAgent").is_none());

        let remix = IT_CLIENTS.iter().find(|p| p.id == "WEB_REMIX").unwrap();
        let body = build_player_payload(remix, "vid12345678", None);
        assert_eq!(body["videoCheckOk"], serde_json::json!(true));
        assert!(body["context"]["client"].get("osName").is_none());
        assert!(body["context"]["client"].get("visitorData").is_none());
    }

    #[test]
    fn playability_status_reads_ok_and_garbage() {
        assert_eq!(
            playability_status(br#"{"playabilityStatus":{"status":"OK"}}"#).as_deref(),
            Some("OK")
        );
        assert_eq!(playability_status(b"not json"), None);
        assert_eq!(playability_status(br#"{"other":1}"#), None);
    }

    #[test]
    fn director_catalog_profiles_are_well_formed() {
        assert!(IT_CLIENTS.len() >= 2);
        for (i, p) in IT_CLIENTS.iter().enumerate() {
            assert!(!p.client_name.is_empty() && !p.client_version.is_empty());
            if i > 0 {
                assert_ne!(IT_CLIENTS[i - 1].id, p.id, "duplicate profile id");
            }
        }
        // First profile is the visitor-backed visionOS probe per the catalog.
        assert!(IT_CLIENTS[0].needs_visitor);
        // The broken-after-512KiB VR family must stay out of the catalog.
        assert!(IT_CLIENTS.iter().all(|p| !p.client_name.contains("ANDROID_VR")));
    }

    // -- live (opt-in) --------------------------------------------------------

    /// MUSICO_LIVE_TESTS=1 cargo test -p musico_core live_innertube_direct -- --ignored --nocapture
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_innertube_direct_resolve() {
        let _tc = crate::online::stream::test_support::TestCache::acquire();
        let t0 = Instant::now();
        match InnerTubeDirectSource.open("dQw4w9WgXcQ", Instant::now() + Duration::from_secs(25)) {
            Ok(_) => println!("DIRECT resolve: {:?}", t0.elapsed()),
            Err(e) => panic!("innertube-direct failed (catalog churn?): {e}"),
        }
    }

    /// MUSICO_LIVE_TESTS=1 cargo test -p musico_core live_piped -- --ignored --nocapture
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

    /// MUSICO_LIVE_TESTS=1 cargo test -p musico_core live_invidious -- --ignored --nocapture
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

    #[test]
    fn parse_video_details_meta_extracts_title_artist_and_duration() {
        let body = br#"{
            "videoDetails": {
                "videoId": "sj342ms",
                "title": "Midnight City",
                "author": "M83 - Topic",
                "lengthSeconds": "243",
                "channelId": "UC123",
                "thumbnail": {
                    "thumbnails": [
                        {"url": "https://i.ytimg.com/vi/sj342ms/default.jpg"},
                        {"url": "https://i.ytimg.com/vi/sj342ms/hqdefault.jpg"}
                    ]
                }
            }
        }"#;
        let meta = parse_video_details_meta(body, "sj342ms").expect("parses videoDetails");
        assert_eq!(meta.video_id, "sj342ms");
        assert_eq!(meta.title, "Midnight City");
        assert_eq!(meta.artist, "M83");
        assert_eq!(meta.duration_secs, Some(243));
        assert_eq!(
            meta.thumbnail.as_deref(),
            Some("https://i.ytimg.com/vi/sj342ms/hqdefault.jpg")
        );
        assert_eq!(meta.artist_browse_id.as_deref(), Some("UC123"));
    }

    #[test]
    fn hedged_stream_race_beats_stalled_primary_source() {
        // Primary stalls 2.0 s, backup responds in 20 ms -> hedge launches
        // backup after 120 ms and wins in well under 600 ms.
        let sources: Vec<Arc<dyn AudioSource>> = vec![
            Arc::new(MockSource {
                name: "stalled-primary".into(),
                delay: Duration::from_millis(2_000),
                result: Ok(GOOD_AUDIO_A.to_vec()),
            }),
            Arc::new(MockSource {
                name: "fast-backup".into(),
                delay: Duration::from_millis(20),
                result: Ok(GOOD_AUDIO_B.to_vec()),
            }),
        ];
        let t0 = Instant::now();
        let opened = try_sources_open_stream_hedged(
            sources,
            "hedgevid001",
            Duration::from_secs(4),
            Duration::from_millis(120),
        )
        .expect("backup must win the hedged race");
        assert_eq!(opened.source_name, "fast-backup");
        assert!(
            t0.elapsed() < Duration::from_millis(800),
            "hedged backup must not wait for 2s primary stall: {:?}",
            t0.elapsed()
        );
    }

    #[test]
    fn hedged_stream_race_fails_over_immediately_on_primary_error() {
        // Primary fails immediately -> backup launches without waiting out
        // the 2-second hedge delay.
        let sources: Vec<Arc<dyn AudioSource>> = vec![
            Arc::new(MockSource {
                name: "dead-primary".into(),
                delay: Duration::from_millis(5),
                result: Err("502 Bad Gateway".into()),
            }),
            Arc::new(MockSource {
                name: "healthy-backup".into(),
                delay: Duration::from_millis(10),
                result: Ok(GOOD_AUDIO_B.to_vec()),
            }),
        ];
        let t0 = Instant::now();
        let opened = try_sources_open_stream_hedged(
            sources,
            "hedgevid002",
            Duration::from_secs(4),
            Duration::from_secs(2),
        )
        .expect("backup must succeed immediately after primary error");
        assert_eq!(opened.source_name, "healthy-backup");
        assert!(
            t0.elapsed() < Duration::from_millis(400),
            "error failover must not sleep for hedge delay: {:?}",
            t0.elapsed()
        );
    }

    #[test]
    fn ordered_it_clients_prioritizes_last_good_and_visitor_free_on_cold_start() {
        // Cold start (no warm visitorData): visitor-free profile leads so we
        // never block waiting on sw.js_data / homepage HTML.
        let cold = ordered_it_clients(false, None);
        assert!(!cold[0].needs_visitor, "cold start must try visitor-free profile first");
        assert_eq!(cold[0].id, "VISIONOS_1_02");

        // Warm visitorData: catalog order applies (VISIONOS_0_1 first).
        let warm = ordered_it_clients(true, None);
        assert_eq!(warm[0].id, "VISIONOS_0_1");

        // Remembered working profile always leads when eligible.
        let remembered = ordered_it_clients(true, Some("WEB_REMIX"));
        assert_eq!(remembered[0].id, "WEB_REMIX");
    }

    #[cfg(not(target_os = "android"))]
    #[test]
    fn stream_http_client_builds() {
        assert!(stream_http_client().is_some());
    }

    #[test]
    fn validate_audio_response_enforces_mime_and_max_bytes() {
        use std::io::{Read, Write};
        use std::net::TcpListener;

        let listener = TcpListener::bind("127.0.0.1:0").expect("bind loopback");
        let addr = listener.local_addr().unwrap();
        let handle = std::thread::spawn(move || {
            let responses = [
                "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 4\r\nConnection: close\r\n\r\nhtml",
                "HTTP/1.1 200 OK\r\nContent-Type: audio/mp4\r\nContent-Length: 128\r\nConnection: close\r\n\r\n",
                "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: 4\r\nConnection: close\r\n\r\ndata",
                "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: 4\r\nConnection: close\r\n\r\ndata",
            ];
            for raw in responses {
                if let Ok((mut stream, _)) = listener.accept() {
                    let mut req_buf = [0u8; 512];
                    let _ = stream.read(&mut req_buf);
                    let _ = stream.write_all(raw.as_bytes());
                }
            }
        });

        let client = SOURCE_CLIENT.as_ref().expect("SOURCE_CLIENT");
        let url = format!("http://{addr}/track");

        let r_html = client.get(&url).timeout(Duration::from_secs(2)).send().unwrap();
        assert!(validate_audio_response(r_html, 64, false, "test".into(), "test").is_err());

        let r_big = client.get(&url).timeout(Duration::from_secs(2)).send().unwrap();
        let err_big = validate_audio_response(r_big, 64, false, "test".into(), "test")
            .err()
            .expect("oversized Content-Length must be rejected");
        assert!(err_big.contains("exceeds 64 B cap"), "{err_big}");

        let r_oct_deny = client.get(&url).timeout(Duration::from_secs(2)).send().unwrap();
        assert!(validate_audio_response(r_oct_deny, 64, false, "test".into(), "test").is_err());

        let r_oct_ok = client.get(&url).timeout(Duration::from_secs(2)).send().unwrap();
        let opened = validate_audio_response(r_oct_ok, 64, true, "test".into(), "test").unwrap();
        assert_eq!(opened.content_type, "audio/mp4");

        handle.join().unwrap();
    }

    #[test]
    fn hedged_stream_race_keeps_hedge_schedule_on_backup_error() {
        // Wave 1 starts at 400 ms; backup-1 errs after 350 ms (~750 ms) while
        // primary & backup-2 are still stalled. Wave 2 (healthy) must still
        // launch on the 800 ms schedule instead of resetting the 400 ms timer.
        let sources: Vec<Arc<dyn AudioSource>> = vec![
            Arc::new(MockSource {
                name: "stalled-primary".into(),
                delay: Duration::from_millis(3_000),
                result: Ok(GOOD_AUDIO_A.to_vec()),
            }),
            Arc::new(MockSource {
                name: "early-error-backup".into(),
                delay: Duration::from_millis(350),
                result: Err("503 unavailable".into()),
            }),
            Arc::new(MockSource {
                name: "stalled-backup-2".into(),
                delay: Duration::from_millis(3_000),
                result: Ok(GOOD_AUDIO_A.to_vec()),
            }),
            Arc::new(MockSource {
                name: "wave2-winner".into(),
                delay: Duration::from_millis(10),
                result: Ok(GOOD_AUDIO_B.to_vec()),
            }),
        ];
        let t0 = Instant::now();
        let opened = try_sources_open_stream_hedged(
            sources,
            "hedgevid003",
            Duration::from_secs(4),
            Duration::from_millis(400),
        )
        .expect("wave 2 must open stream on original hedge schedule");
        assert_eq!(opened.source_name, "wave2-winner");
        assert!(
            t0.elapsed() < Duration::from_millis(1_050),
            "early backup error must not reset hedge timer: {:?}",
            t0.elapsed()
        );
    }

    #[test]
    fn select_best_audio_prefers_m4a_and_falls_back_to_opus_webm() {
        let only_opus = vec![PipedAudio {
            url: "https://rr1---sn.googlevideo.com/videoplayback?itag=251".into(),
            bitrate: Some(160_000),
            mime_type: Some("audio/webm; codecs=\"opus\"".into()),
        }];
        assert_eq!(
            select_best_audio_within(&only_opus, MAX_AUDIO_BITRATE).as_deref(),
            Some("https://rr1---sn.googlevideo.com/videoplayback?itag=251")
        );
        let mut mixed = only_opus.clone();
        mixed.push(pa(
            "https://rr1---sn.googlevideo.com/videoplayback?itag=140",
            Some(130_000),
        ));
        assert_eq!(
            select_best_audio_within(&mixed, MAX_AUDIO_BITRATE).as_deref(),
            Some("https://rr1---sn.googlevideo.com/videoplayback?itag=140")
        );
    }

    #[test]
    fn it_demotion_requires_consecutive_transport_failures() {
        let id = "TEST_PROFILE_DEMOTE";
        it_rehab(id);
        assert!(!it_demoted(id));
        it_record_transport_failure(id);
        assert!(!it_demoted(id), "single failure must not blacklist a profile");
        it_record_transport_failure(id);
        assert!(!it_demoted(id), "two failures below threshold must not blacklist");
        it_record_transport_failure(id);
        assert!(it_demoted(id), "three consecutive transport failures demote");
        it_rehab(id);
        assert!(!it_demoted(id), "rehab clears demotion");
    }

    #[test]
    fn tv_embedded_payload_includes_third_party_embed_url() {
        let tv = IT_CLIENTS
            .iter()
            .find(|p| p.id == "TV_EMBEDDED")
            .expect("TV_EMBEDDED profile present");
        let body = build_player_payload(tv, "vid12345678", None);
        assert_eq!(
            body["context"]["thirdParty"]["embedUrl"],
            serde_json::json!("https://www.youtube.com/")
        );
    }
}
