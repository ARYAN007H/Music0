//! JNI bridge for the native Kotlin Android app (#111).
//!
//! The Compose UI talks to the SAME Rust core the desktop Tauri app uses,
//! through one blocking JSON-in/JSON-out entry point. No Tauri runtime here:
//! the bridge only calls AppHandle-free core functions (search, resolve,
//! settings). Audio is Media3/ExoPlayer on the Kotlin side — it plays the
//! cached files this core produces, so the cpal engine is deliberately NOT
//! initialized on mobile.
//!
//! Contract (`MusicoCore.kt` mirrors it):
//! - `init(filesDir, cacheDir)` MUST run first: the native app has no tao
//!   glue, so nothing initializes `ndk_context` — the dirs the core needs
//!   are passed in explicitly and exported as the XDG variables that
//!   `dirs`/`identity.rs` resolve against (same mechanism as
//!   [`crate::android_dirs`]).
//! - `invoke(cmd, argsJson) -> resultJson` with `{"ok":true,"data":…}` /
//!   `{"ok":false,"error":"…"}`. Panics never cross FFI.

use std::sync::atomic::{AtomicBool, Ordering};

use jni::objects::{JClass, JString};
use jni::JNIEnv;

static MOBILE_INITIALIZED: AtomicBool = AtomicBool::new(false);

fn set_env_var(key: &str, value: &str) {
    // Edition 2021: `set_var` is safe; single-threaded init contract anyway.
    std::env::set_var(key, value);
}

/// Called from `MusicoCore.init(filesDir, cacheDir)` before any other entry.
#[no_mangle]
pub extern "system" fn Java_io_github_aryan007h_musicoonline_core_MusicoCore_initNative(
    mut env: JNIEnv,
    _class: JClass,
    files_dir: JString,
    cache_dir: JString,
) {
    let Ok(files) = env.get_string(&files_dir) else {
        return;
    };
    let Ok(cache) = env.get_string(&cache_dir) else {
        return;
    };
    let files = String::from(files);
    let cache = String::from(cache);
    let _ = std::fs::create_dir_all(&files);
    let _ = std::fs::create_dir_all(&cache);
    set_env_var("HOME", &files);
    set_env_var("XDG_DATA_HOME", &files);
    set_env_var("XDG_CONFIG_HOME", &files);
    set_env_var("XDG_CACHE_HOME", &cache);
    // Logcat is the only debugging surface on a device (the files dir is
    // not readable over adb without root) — mirror everything there.
    // RUST_LOG is settable via `adb shell am start --es RUST_LOG debug`-style
    // wrappers only when the launcher passes it; keep Info as the default.
    let _ = std::env::var("RUST_LOG"); // parsed below when present
    let mut level = log::LevelFilter::Info;
    if let Ok(spec) = std::env::var("RUST_LOG") {
        if spec.contains("debug") {
            level = log::LevelFilter::Debug;
        }
    }
    android_logger::init_once(
        android_logger::Config::default()
            .with_max_level(level)
            .with_tag("musico-native"),
    );
    MOBILE_INITIALIZED.store(true, Ordering::Release);
    log::info!("mobile core: initialized files={files} cache={cache}");
}

/// The one IPC entry: `MusicoCore.invoke(cmd, argsJson)`.
#[no_mangle]
pub extern "system" fn Java_io_github_aryan007h_musicoonline_core_MusicoCore_invoke<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    cmd: JString<'local>,
    args: JString<'local>,
) -> JString<'local> {
    let outcome = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let cmd = env.get_string(&cmd).map(String::from).unwrap_or_default();
        let args = env.get_string(&args).map(String::from).unwrap_or_default();
        dispatch(&cmd, &args)
    }))
    .unwrap_or_else(|_| Err("mobile core: dispatcher panicked (bug — see musico.log)".into()));

    let reply = match outcome {
        Ok(data) => serde_json::json!({ "ok": true, "data": data }),
        Err(e) => {
            log::warn!("mobile core: dispatch failed: {e}");
            serde_json::json!({ "ok": false, "error": e })
        }
    };
    match env.new_string(reply.to_string()) {
        Ok(s) => s,
        // Only possible on OOM; still must return a valid local ref.
        Err(_) => env
            .new_string(r#"{"ok":false,"error":"out of memory"}"#)
            .unwrap_or_else(|_| unsafe { JString::from_raw(std::ptr::null_mut()) }),
    }
}

/// Command table. Every handler is AppHandle-free by construction — that is
/// the whole point of this bridge (see the module doc).
fn dispatch(cmd: &str, args_json: &str) -> Result<serde_json::Value, String> {
    let args: serde_json::Value =
        serde_json::from_str(args_json).unwrap_or(serde_json::Value::Null);

    match cmd {
        "ping" => Ok(serde_json::json!({
            "version": env!("CARGO_PKG_VERSION"),
            "core": "rust-mobile",
            "initialized": MOBILE_INITIALIZED.load(Ordering::Acquire),
        })),

        "settings.get" => {
            let settings = crate::config::AppSettings::load();
            serde_json::to_value(settings).map_err(|e| format!("settings serialize: {e}"))
        }

        "online.search" => {
            ensure_online()?;
            let query = args
                .get("query")
                .and_then(|v| v.as_str())
                .ok_or("missing query")?
                .to_string();
            let videos = args.get("videos").and_then(|v| v.as_bool());
            let continuation = args
                .get("continuation")
                .and_then(|v| v.as_str())
                .map(str::to_string);
            // Same engine the Tauri command runs (sync; the Kotlin side calls
            // us from Dispatchers.IO).
            let page = crate::online::search(&query, videos.unwrap_or(false), continuation.as_deref())?;
            serde_json::to_value(page).map_err(|e| format!("search serialize: {e}"))
        }

        "track.resolve" => {
            ensure_online()?;
            let track = online_track_from_args(&args)?;
            let path = crate::online::resolve_track(&track, true)?;
            Ok(serde_json::json!({
                "path": path.display().to_string(),
                "songId": track.song_id(),
            }))
        }

        // ── Browse surfaces (Apple-Music-style home) ──────────────────────
        // Same engines the desktop commands run; spawn_blocking is not needed
        // because the Kotlin caller is already on Dispatchers.IO.

        "online.home" => {
            ensure_online()?;
            serde_json::to_value(crate::online::home_feed()?).map_err(|e| format!("home serialize: {e}"))
        }

        "online.genres" => {
            ensure_online()?;
            // Wrapped in an object: the bridge contract is object-shaped
            // data (MusicoCore.call unwraps `data` as a JsonObject).
            Ok(serde_json::json!({
                "items": crate::online::genres()?,
            }))
        }

        "online.browse" => {
            ensure_online()?;
            // Continuation form: {"continuation": token} — the next slice of
            // a browse feed (home infinite scroll). No browseId involved.
            if let Some(token) = args.get("continuation").and_then(|v| v.as_str()) {
                serde_json::to_value(crate::online::browse_continuation(token)?)
                    .map_err(|e| format!("browse continuation serialize: {e}"))
            } else {
                let browse_id = args
                    .get("browseId")
                    .and_then(|v| v.as_str())
                    .ok_or("missing browseId")?;
                let params = args.get("params").and_then(|v| v.as_str());
                serde_json::to_value(crate::online::browse(browse_id, params)?)
                    .map_err(|e| format!("browse serialize: {e}"))
            }
        }

        // Related tracks for a song (radio seeding / Up Next). Wrapped in
        // an object: the bridge contract is object-shaped data — a bare
        // JsonArray would collapse to {} in MusicoCore.call.
        "online.related" => {
            ensure_online()?;
            let video_id = args
                .get("videoId")
                .and_then(|v| v.as_str())
                .ok_or("missing videoId")?;
            let tracks = crate::online::radio_tracks(video_id)?;
            Ok(serde_json::json!({ "tracks": tracks }))
        }

        "online.suggestions" => {
            ensure_online()?;
            let query = args
                .get("query")
                .and_then(|v| v.as_str())
                .ok_or("missing query")?;
            Ok(serde_json::json!({
                "suggestions": crate::online::search_suggestions(query),
            }))
        }

        // ── Word-synced lyrics ────────────────────────────────────────────
        // Desktop parity: LRCLIB synced (word timings synthesized across line
        // spans by `build_enhanced_lines_from_timed`) → LRCLIB plain → YTM
        // InnerTube plain. `{"synced":false,"lines":[]}` = no lyrics found —
        // never an error, the player just hides the lyrics pane.
        "lyrics.enhanced" => {
            ensure_online()?;
            let s = |k: &str| {
                args.get(k)
                    .and_then(|v| v.as_str())
                    .unwrap_or_default()
                    .to_string()
            };
            let title = s("title");
            let artist = s("artist");
            let track_id = s("trackId");
            let video_id = s("videoId");
            let duration = args
                .get("durationSecs")
                .and_then(|v| v.as_u64())
                .map(|v| v as u32);
            let dur_f32 = duration.unwrap_or(200) as f32;

            if title.trim().is_empty() {
                return Ok(no_lyrics());
            }

            // 1. LRCLIB synced lines → word-level timing (desktop path).
            if let Some(lrc) = crate::online::fetch_synced_lyrics(&artist, &title, duration) {
                if !lrc.synced.is_empty() {
                    let pairs: Vec<(f32, String)> = lrc
                        .synced
                        .into_iter()
                        .map(|l| (l.time_secs, l.text))
                        .collect();
                    let mut dto = crate::commands::build_enhanced_lines_from_timed(&pairs, dur_f32);
                    dto.track_id = track_id;
                    dto.source = "LRCLIB".into();
                    return serde_json::to_value(dto).map_err(|e| format!("lyrics serialize: {e}"));
                }
                if let Some(plain) = lrc.plain {
                    return Ok(plain_lines(&plain, track_id, "LRCLIB"));
                }
            }

            // 2. YTM InnerTube plain-text fallback (needs the video id).
            if !video_id.is_empty() {
                if let Some(text) = crate::online::fetch_lyrics(&video_id) {
                    return Ok(plain_lines(&text, track_id, "YouTube Music"));
                }
            }

            Ok(no_lyrics())
        }

        // ── Settings write-back ───────────────────────────────────────────
        // Patch semantics: `{"settings": {…partial…}}` is shallow-merged over
        // the loaded AppSettings, then validated by a full re-parse and saved.

        "settings.save" => {
            let patch = args
                .get("settings")
                .and_then(|v| v.as_object())
                .ok_or("missing settings object")?;
            let current = serde_json::to_value(crate::config::AppSettings::load())
                .map_err(|e| format!("settings serialize: {e}"))?;
            let mut merged = current;
            for (k, v) in patch {
                merged[k] = v.clone();
            }
            let settings: crate::config::AppSettings =
                serde_json::from_value(merged).map_err(|e| format!("invalid settings: {e}"))?;
            settings.save()?;
            log::info!("mobile core: settings saved");
            serde_json::to_value(settings).map_err(|e| format!("settings serialize: {e}"))
        }

        // ── In-app updater ────────────────────────────────────────────────
        // The native app is sideloaded from GitHub releases; it checks the
        // latest release over the shared reqwest stack and (if newer) points
        // the Kotlin side at the native APK asset for download. Kotlin owns
        // the download+install hand-off (progress callbacks + system
        // installer intent) — this command is check-only.

        "app.update.check" => {
            const RELEASES_API: &str = "https://api.github.com/repos/ARYAN007H/Music0/releases/latest";
            let current = env!("CARGO_PKG_VERSION"); // e.g. "0.9.1" — matches the app version at build time
            let client = reqwest::blocking::Client::builder()
                .user_agent(format!("Music0-Android/{current}"))
                .connect_timeout(std::time::Duration::from_secs(5))
                .timeout(std::time::Duration::from_secs(15))
                .build()
                .map_err(|e| format!("updater client: {e}"))?;
            let reply: serde_json::Value = client
                .get(RELEASES_API)
                .send()
                .map_err(|e| format!("release check: {e}"))?
                .error_for_status()
                .map_err(|e| format!("release check: {e}"))?
                .json()
                .map_err(|e| format!("release parse: {e}"))?;

            let latest_tag = reply["tag_name"].as_str().unwrap_or_default().trim();
            let latest = latest_tag.trim_start_matches('v').to_string();
            // The native asset — NOT the retired Tauri APK.
            let asset = reply["assets"]
                .as_array()
                .and_then(|assets| {
                    assets
                        .iter()
                        .find(|a| {
                            a["name"].as_str()
                                .map(|n| n.starts_with("musico-online-native") && n.ends_with(".apk"))
                                .unwrap_or(false)
                        })
                });
            let (asset_url, asset_size, asset_name) = match asset {
                Some(a) => (
                    a["browser_download_url"].as_str().unwrap_or_default().to_string(),
                    a["size"].as_u64().unwrap_or(0),
                    a["name"].as_str().unwrap_or_default().to_string(),
                ),
                None => (String::new(), 0, String::new()),
            };
            let update_available = !latest.is_empty()
                && !asset_url.is_empty()
                && version_newer(current, &latest);

            serde_json::json!({
                "current": current,
                "latest": latest,
                "updateAvailable": update_available,
                "assetUrl": asset_url,
                "assetName": asset_name,
                "assetSizeBytes": asset_size,
                "notes": reply["body"].as_str().unwrap_or_default().lines().take(12).collect::<Vec<_>>().join("\n"),
            })
        }

        // ── Stream cache management ───────────────────────────────────────

        "online.cache.usage" => {
            let settings = crate::config::AppSettings::load();
            Ok(serde_json::json!({
                "bytes": crate::online::cache_usage_bytes(),
                "capMb": settings.online_cache_mb,
            }))
        }

        "online.cache.clear" => {
            let freed = crate::online::clear_cache()?;
            log::info!("mobile core: stream cache cleared ({freed} bytes freed)");
            Ok(serde_json::json!({ "freedBytes": freed }))
        }

        other => Err(format!("unknown command `{other}`")),
    }
}

/// Airplane-mode gate, mirroring `commands::ensure_online` without the
/// Tauri state (mobile reads the persisted settings directly).
fn ensure_online() -> Result<(), String> {
    let settings = crate::config::AppSettings::load();
    if settings.offline_mode {
        return Err("Offline — turn Airplane Mode off to reach YouTube Music".into());
    }
    Ok(())
}

/// Triple-wise semver comparison for `a > b` ("0.9.1" > "0.9.0"). Suffixes
/// like `-native` are stripped on both sides; missing components are 0.
fn version_newer(current: &str, latest: &str) -> bool {
    fn triple(v: &str) -> (u32, u32, u32) {
        let core = v.trim().trim_start_matches('v').split(['-', '+']).next().unwrap_or("");
        let mut it = core.split('.');
        let n = |s: Option<&str>| s.and_then(|x| x.trim().parse::<u32>().ok()).unwrap_or(0);
        let (a, b, c) = (it.next(), it.next(), it.next());
        (n(a), n(b), n(c))
    }
    triple(latest) > triple(current)
}

/// The "no lyrics exist" reply the Kotlin side treats as an empty pane.
fn no_lyrics() -> serde_json::Value {
    serde_json::json!({
        "track_id": "",
        "synced": false,
        "source": "",
        "has_word_sync": false,
        "has_romanized": false,
        "has_translation": false,
        "lines": [],
    })
}

/// Unsynced plain text → [`crate::commands::EnhancedLyricsDto`]-shaped JSON
/// (one line per row, no word timings).
fn plain_lines(text: &str, track_id: String, source: &str) -> serde_json::Value {
    let lines: Vec<serde_json::Value> = text
        .lines()
        .map(str::trim)
        .filter(|l| !l.is_empty())
        .map(|l| {
            serde_json::json!({
                "time_secs": 0.0,
                "text": l,
                "words": [],
                "romanized": null,
                "translation": null,
            })
        })
        .collect();
    serde_json::json!({
        "track_id": track_id,
        "synced": false,
        "source": source,
        "has_word_sync": false,
        "has_romanized": false,
        "has_translation": false,
        "lines": lines,
    })
}

/// Builds an [`crate::online::OnlineTrack`] from loose JNI args: `videoId`
/// plus optional title/artist/album/duration/thumbnail/source. Only the id
/// and source namespace affect resolution; metadata fields default.
fn online_track_from_args(args: &serde_json::Value) -> Result<crate::online::OnlineTrack, String> {
    use crate::online::OnlineTrack;
    let video_id = args
        .get("videoId")
        .and_then(|v| v.as_str())
        .ok_or("missing videoId")?
        .to_string();
    if video_id.is_empty() || video_id.len() > 32 {
        return Err("invalid videoId".into());
    }
    let s = |k: &str| {
        args.get(k)
            .and_then(|v| v.as_str())
            .map(str::to_string)
            .unwrap_or_default()
    };
    Ok(OnlineTrack {
        video_id,
        title: s("title"),
        artist: s("artist"),
        album: s("album"),
        duration_secs: args.get("durationSecs").and_then(|v| v.as_u64()).map(|v| v as u32),
        thumbnail: args.get("thumbnail").and_then(|v| v.as_str()).map(str::to_string),
        cached: false,
        source: s("source"),
        artist_browse_id: args
            .get("artistBrowseId")
            .and_then(|v| v.as_str())
            .map(str::to_string),
    })
}
