//! JNI bridge for the Compose Multiplatform app (Android + Desktop JVM).
//!
//! Both Android (`:app`) and Desktop (`:desktopApp`) talk to the same pure-Rust
//! core through one blocking JSON-in/JSON-out entry point: search, resolve,
//! stream-while-downloading, settings, sled-backed playlists/library, playlist
//! import, lyrics, and the 7-signal adaptive recommender. Audio uses Media3 /
//! ExoPlayer on Android and `musico_playback` (Symphonia + CPAL + 10-band DSP)
//! on Desktop.
//!
//! Contract (`MusicoCore.kt` mirrors it):
//! - `init(filesDir, cacheDir)` MUST run first so the app-private data/cache
//!   dirs are exported as the XDG variables that `dirs`/`identity.rs` resolve
//!   against.
//! - `invoke(cmd, argsJson) -> resultJson` with `{"ok":true,"data":…}` /
//!   `{"ok":false,"error":"…"}`. Panics never cross FFI.

use std::collections::{HashMap, HashSet};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{mpsc, Mutex, OnceLock};

use jni::objects::{JClass, JString};
use jni::JNIEnv;
use musico_recommender::library::CatalogEntry;
use musico_recommender::user_playlists::PlaylistTrack;
use musico_recommender::{LikeState, MusicRecommender};

static MOBILE_INITIALIZED: AtomicBool = AtomicBool::new(false);

/// Serializes the settings read-merge-save cycle: two overlapping toggles
/// from `Dispatchers.IO` must not lose a write (review P2-7).
static SETTINGS_LOCK: Mutex<()> = Mutex::new(());

/// Shared sled-backed recommender + playlists + library store.
/// Lazily initialized after `initNative` exports XDG paths.
static RECOMMENDER: OnceLock<Mutex<MusicRecommender>> = OnceLock::new();

/// Desktop JVM audio playback engine (Android uses Media3/ExoPlayer instead).
#[cfg(not(target_os = "android"))]
static DESKTOP_PLAYER: OnceLock<Mutex<musico_playback::PlaybackEngine>> = OnceLock::new();
#[cfg(not(target_os = "android"))]
static DESKTOP_PLAYER_INIT: Mutex<()> = Mutex::new(());

#[cfg(not(target_os = "android"))]
fn desktop_player() -> Result<&'static Mutex<musico_playback::PlaybackEngine>, String> {
    if let Some(m) = DESKTOP_PLAYER.get() {
        return Ok(m);
    }
    let _init_guard = DESKTOP_PLAYER_INIT
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner);
    if let Some(m) = DESKTOP_PLAYER.get() {
        return Ok(m);
    }
    let engine = musico_playback::PlaybackEngine::new()
        .map_err(|e| format!("desktop audio init failed: {e}"))?;
    let buf_kb = crate::config::AppSettings::load().instant_play_buffer_kb;
    let _ = engine.set_instant_buffer_kb(buf_kb);
    let _ = DESKTOP_PLAYER.set(Mutex::new(engine));
    DESKTOP_PLAYER
        .get()
        .ok_or_else(|| "desktop audio engine unavailable".to_string())
}

/// Background acoustic-indexing queue: keeps audio decoding + FFT completely
/// off the JNI/UI critical path while feeding the 30-dim feature space.
struct IndexJob {
    video_id: String,
    file_path: String,
    title: String,
    artist: String,
    album: String,
    duration_secs: Option<u32>,
}

static INDEX_TX: OnceLock<Mutex<mpsc::Sender<IndexJob>>> = OnceLock::new();

#[derive(Clone, Default)]
struct ImportProgress {
    active: bool,
    name: String,
    current: usize,
    total: usize,
}

static IMPORT_ACTIVE: AtomicBool = AtomicBool::new(false);
static IMPORT_CANCELLED: AtomicBool = AtomicBool::new(false);
static IMPORT_PROGRESS: OnceLock<Mutex<ImportProgress>> = OnceLock::new();

fn import_progress_lock() -> &'static Mutex<ImportProgress> {
    IMPORT_PROGRESS.get_or_init(|| Mutex::new(ImportProgress::default()))
}

struct ImportRunGuard;
impl Drop for ImportRunGuard {
    fn drop(&mut self) {
        if let Ok(mut p) = import_progress_lock().lock() {
            p.active = false;
        }
        IMPORT_CANCELLED.store(false, Ordering::SeqCst);
        IMPORT_ACTIVE.store(false, Ordering::SeqCst);
    }
}

const TREE_TRACK_META: &str = "mobile:track_meta";
const TREE_VID_TO_SONG: &str = "mobile:vid_to_song";
const TREE_SONG_TO_VID: &str = "mobile:song_to_vid";
const TREE_ENRICH_ATTEMPTED: &str = "mobile:enrich_attempted";

fn set_env_var(key: &str, value: &str) {
    // Edition 2021: `set_var` is safe; single-threaded init contract anyway.
    std::env::set_var(key, value);
}

fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

/// Lazily opens the shared `MusicRecommender` sled DB at `<data_dir>/db`.
fn recommender() -> Result<&'static Mutex<MusicRecommender>, String> {
    if let Some(m) = RECOMMENDER.get() {
        return Ok(m);
    }
    let db_dir = crate::identity::data_dir()
        .ok_or_else(|| "mobile core: data dir unavailable".to_string())?
        .join("db");
    let _ = std::fs::create_dir_all(&db_dir);
    let rec = MusicRecommender::new(&db_dir.to_string_lossy())
        .map_err(|e| format!("recommender open failed: {e}"))?;
    let _ = RECOMMENDER.set(Mutex::new(rec));
    start_indexer_worker();
    RECOMMENDER
        .get()
        .ok_or_else(|| "recommender unavailable".to_string())
}

fn with_recommender<T>(f: impl FnOnce(&mut MusicRecommender) -> Result<T, String>) -> Result<T, String> {
    let mutex = recommender()?;
    let mut guard = mutex.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
    f(&mut guard)
}

/// Spawns a single low-priority background thread that decodes & extracts
/// acoustic features for cached tracks and registers them in `MusicRecommender`.
#[cfg(all(target_os = "linux", target_env = "gnu"))]
fn trim_native_memory() {
    extern "C" {
        fn malloc_trim(pad: usize) -> std::ffi::c_int;
    }
    // SAFETY: glibc `malloc_trim(0)` is thread-safe and returns unused arena
    // pages back to the OS without invalidating live allocations.
    unsafe {
        let _ = malloc_trim(0);
    }
}

#[cfg(not(all(target_os = "linux", target_env = "gnu")))]
fn trim_native_memory() {}

fn start_indexer_worker() {
    if INDEX_TX.get().is_some() {
        return;
    }
    let (tx, rx) = mpsc::channel::<IndexJob>();
    if INDEX_TX.set(Mutex::new(tx)).is_err() {
        return;
    }
    let _ = std::thread::Builder::new()
        .name("musico-mobile-indexer".into())
        .spawn(move || {
            while let Ok(job) = rx.recv() {
                // Skip if already indexed for this video_id.
                let already = with_recommender(|rec| {
                    let tree = rec
                        .db()
                        .open_tree(TREE_VID_TO_SONG)
                        .map_err(|e| e.to_string())?;
                    Ok(tree.contains_key(job.video_id.as_bytes()).unwrap_or(false))
                })
                .unwrap_or(false);
                if already {
                    continue;
                }
                match musico_recommender::extractor::analyze_file(&job.file_path) {
                    Ok(mut analysis) => {
                        if !job.title.is_empty() {
                            analysis.title = job.title.clone();
                        }
                        if !job.artist.is_empty() {
                            analysis.artist = job.artist.clone();
                        }
                        if !job.album.is_empty() {
                            analysis.album = job.album.clone();
                        }
                        if let Some(d) = job.duration_secs {
                            if d > 0 {
                                analysis.duration_secs = d;
                            }
                        }
                        let _ = with_recommender(|rec| {
                            let record = rec
                                .index_from_result(&job.file_path, analysis)
                                .map_err(|e| e.to_string())?;
                            if let Ok(v2s) = rec.db().open_tree(TREE_VID_TO_SONG) {
                                let _ = v2s.insert(job.video_id.as_bytes(), record.id.as_bytes());
                            }
                            if let Ok(s2v) = rec.db().open_tree(TREE_SONG_TO_VID) {
                                let _ = s2v.insert(record.id.as_bytes(), job.video_id.as_bytes());
                            }
                            Ok(())
                        });
                    }
                    Err(e) => {
                        log::debug!("mobile indexer: skip {} ({e})", job.video_id);
                    }
                }
                trim_native_memory();
            }
        });
}

fn enqueue_background_index(track: &crate::online::OnlineTrack, file_path: &str) {
    start_indexer_worker();
    if let Some(tx_lock) = INDEX_TX.get() {
        if let Ok(tx) = tx_lock.lock() {
            let _ = tx.send(IndexJob {
                video_id: track.video_id.clone(),
                file_path: file_path.to_string(),
                title: track.title.clone(),
                artist: track.artist.clone(),
                album: track.album.clone(),
                duration_secs: track.duration_secs,
            });
        }
    }
}

/// Persists rich `OnlineTrack` metadata inside sled so Likes, Downloads, and
/// Smart Mixes always have complete title/artist/thumbnail/duration fields.
fn remember_track_meta(rec: &MusicRecommender, track: &crate::online::OnlineTrack) {
    if track.video_id.is_empty() || track.title.is_empty() {
        return;
    }
    if let Ok(tree) = rec.db().open_tree(TREE_TRACK_META) {
        if let Ok(bytes) = serde_json::to_vec(track) {
            let _ = tree.insert(track.video_id.as_bytes(), bytes);
        }
    }
}

fn load_track_meta(rec: &MusicRecommender, video_id: &str) -> Option<crate::online::OnlineTrack> {
    let tree = rec.db().open_tree(TREE_TRACK_META).ok()?;
    let bytes = tree.get(video_id.as_bytes()).ok()??;
    serde_json::from_slice(&bytes).ok()
}

fn lookup_song_id(rec: &MusicRecommender, video_id: &str) -> String {
    rec.db()
        .open_tree(TREE_VID_TO_SONG)
        .ok()
        .and_then(|t| t.get(video_id.as_bytes()).ok().flatten())
        .and_then(|b| String::from_utf8(b.to_vec()).ok())
        .unwrap_or_else(|| video_id.to_string())
}

fn lookup_video_id(rec: &MusicRecommender, song_id: &str) -> Option<String> {
    rec.db()
        .open_tree(TREE_SONG_TO_VID)
        .ok()
        .and_then(|t| t.get(song_id.as_bytes()).ok().flatten())
        .and_then(|b| String::from_utf8(b.to_vec()).ok())
}

/// Called from `MusicoCore.init(filesDir, cacheDir)` before any other entry.
#[no_mangle]
pub extern "system" fn Java_io_github_aryan007h_musicoonline_core_MusicoCore_initNative(
    mut env: JNIEnv,
    _class: JClass,
    files_dir: JString,
    cache_dir: JString,
) {
    if MOBILE_INITIALIZED.load(Ordering::Acquire) {
        return;
    }
    let Ok(files) = env.get_string(&files_dir) else {
        return;
    };
    let Ok(cache) = env.get_string(&cache_dir) else {
        return;
    };
    let files = String::from(files);
    let cache = String::from(cache);
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        init_core(&files, &cache);
        MOBILE_INITIALIZED.store(true, Ordering::Release);
        log::info!("mobile core: initialized files={files} cache={cache}");
    }));
    if result.is_err() {
        log::warn!("mobile core: init panicked (suppressed — continuing degraded)");
    }
}

fn init_core(files: &str, cache: &str) {
    let _ = std::fs::create_dir_all(files);
    let _ = std::fs::create_dir_all(cache);
    set_env_var("HOME", files);
    set_env_var("XDG_DATA_HOME", files);
    set_env_var("XDG_CONFIG_HOME", files);
    set_env_var("XDG_CACHE_HOME", cache);
    let mut level = log::LevelFilter::Info;
    if let Ok(spec) = std::env::var("RUST_LOG") {
        if spec.contains("debug") {
            level = log::LevelFilter::Debug;
        }
    }
    #[cfg(target_os = "android")]
    {
        android_logger::init_once(
            android_logger::Config::default()
                .with_max_level(level)
                .with_tag("musico-native"),
        );
    }
    #[cfg(not(target_os = "android"))]
    {
        let mut builder = env_logger::Builder::new();
        builder.filter_level(level);
        if std::env::var("RUST_LOG").is_ok() {
            builder.parse_default_env();
        }
        let _ = builder.try_init();
    }
    // Apply persisted stream-cache cap before any downloads/prunes happen.
    let settings = crate::config::AppSettings::load();
    let saved_cap_mb = settings.online_cache_mb.unwrap_or(0);
    crate::online::stream::set_cache_cap_override_mb(u64::from(saved_cap_mb));
    // Warm the recommender DB early so first Library/Home calls are instant.
    if let Err(e) = recommender() {
        log::warn!("mobile core: recommender bootstrap deferred: {e}");
    }
    // Pre-warm loopback stream server and upstream TLS/visitor caches off-thread
    // so the very first track tap resolves in ~1-2 s instead of cold-starting TLS.
    crate::online::stream_server::prewarm();
    // Pre-warm the Desktop CPAL audio engine off-thread so the first click never
    // pays device-open latency (harmless no-op on headless test runners).
    #[cfg(not(target_os = "android"))]
    {
        let _ = std::thread::Builder::new()
            .name("musico-audio-prewarm".into())
            .spawn(|| {
                let _ = desktop_player();
            });
    }
    if !settings.offline_mode {
        crate::online::sources::prewarm_async();
        let _ = std::thread::Builder::new()
            .name("musico-saavn-prewarm".into())
            .spawn(crate::online::saavn::prewarm_client);
    }
}

/// Single source of truth for reported app version across About, Desktop Rail,
/// `ping`, and `app.update.check` (matches Android `versionName = "1.0.2-cmp"`).
fn app_display_version() -> &'static str {
    match option_env!("MUSICO_VERSION_OVERRIDE") {
        Some(v) if !v.trim().is_empty() => v.trim(),
        _ => concat!(env!("CARGO_PKG_VERSION"), "-cmp"),
    }
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
        Err(_) => {
            let _ = env.throw_new("java/lang/OutOfMemoryError", "musico native: OOM building reply");
            unsafe { JString::from_raw(std::ptr::null_mut()) }
        }
    }
}

/// Command table. Every handler is AppHandle-free by construction.
fn dispatch(cmd: &str, args_json: &str) -> Result<serde_json::Value, String> {
    let args: serde_json::Value =
        serde_json::from_str(args_json).unwrap_or(serde_json::Value::Null);

    match cmd {
        "ping" => Ok(serde_json::json!({
            "version": app_display_version(),
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
            let page = crate::online::search(&query, videos.unwrap_or(false), continuation.as_deref())?;
            serde_json::to_value(page).map_err(|e| format!("search serialize: {e}"))
        }

        // Offline-first stream resolution: if the track is already cached on
        // disk, return it immediately even in Airplane Mode, and queue
        // background acoustic feature extraction for the recommender.
        // When `allowStream` is true (interactive mobile playback), opens a
        // local loopback HTTP/1.1 range stream over `.part` so audio starts in
        // ~1–2 s instead of waiting for the full download.
        "track.resolve" => {
            let t0 = std::time::Instant::now();
            let track = online_track_from_args(&args)?;
            let _ = with_recommender(|rec| {
                remember_track_meta(rec, &track);
                Ok(())
            });
            let cached = crate::online::cached_file_for(&track.song_id())
                .or_else(|| crate::online::cached_file_for(&track.video_id));
            let (path_str, mode) = if let Some(hit) = cached {
                crate::online::write_track_sidecar(&hit, &track);
                let s = hit.display().to_string();
                enqueue_background_index(&track, &s);
                (s, "cached")
            } else {
                ensure_online()?;
                let background = args
                    .get("background")
                    .and_then(|v| v.as_bool())
                    .unwrap_or(false);
                let allow_stream = args
                    .get("allowStream")
                    .and_then(|v| v.as_bool())
                    .unwrap_or(false);
                if !background && allow_stream {
                    let index_track = track.clone();
                    let resolved = crate::online::resolve_track_streaming(&track, move |final_path| {
                        let p = final_path.display().to_string();
                        enqueue_background_index(&index_track, &p);
                    })?;
                    let is_http = resolved.starts_with("http://") || resolved.starts_with("https://");
                    if !is_http {
                        enqueue_background_index(&track, &resolved);
                    }
                    (resolved, if is_http { "stream" } else { "file" })
                } else {
                    let path = crate::online::resolve_track(&track, !background)?;
                    let s = path.display().to_string();
                    enqueue_background_index(&track, &s);
                    (s, if background { "bg_file" } else { "file" })
                }
            };
            log::info!(
                "track.resolve {} mode={mode} total_ms={}",
                track.video_id,
                t0.elapsed().as_millis()
            );
            Ok(serde_json::json!({
                "path": path_str,
                "songId": track.song_id(),
                "mode": mode,
                "resolveMs": t0.elapsed().as_millis() as u64,
            }))
        }

        // ── Browse surfaces ──────────────────────────────────────────────
        "online.home" => {
            ensure_online()?;
            serde_json::to_value(crate::online::home_feed()?).map_err(|e| format!("home serialize: {e}"))
        }

        "online.genres" => {
            ensure_online()?;
            Ok(serde_json::json!({
                "items": crate::online::genres()?,
            }))
        }

        "online.browse" => {
            ensure_online()?;
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

        // ── User Playlists (sled-backed CRUD) ────────────────────────────
        "playlist.list" => with_recommender(|rec| {
            let list = rec.playlist_list().map_err(|e| e.to_string())?;
            Ok(serde_json::json!({ "playlists": list }))
        }),

        "playlist.create" => {
            let name = args
                .get("name")
                .and_then(|v| v.as_str())
                .ok_or("missing playlist name")?;
            with_recommender(|rec| {
                let pl = rec.playlist_create(name).map_err(|e| e.to_string())?;
                serde_json::to_value(pl).map_err(|e| format!("playlist serialize: {e}"))
            })
        }

        "playlist.rename" => {
            let id = args.get("id").and_then(|v| v.as_str()).ok_or("missing id")?;
            let name = args.get("name").and_then(|v| v.as_str()).ok_or("missing name")?;
            with_recommender(|rec| {
                rec.playlist_rename(id, name).map_err(|e| e.to_string())?;
                Ok(serde_json::json!({ "renamed": true }))
            })
        }

        "playlist.delete" => {
            let id = args.get("id").and_then(|v| v.as_str()).ok_or("missing id")?;
            with_recommender(|rec| {
                rec.playlist_delete(id).map_err(|e| e.to_string())?;
                Ok(serde_json::json!({ "deleted": true }))
            })
        }

        "playlist.add_track" => {
            let id = args.get("id").and_then(|v| v.as_str()).ok_or("missing id")?;
            let track_val = args.get("track").ok_or("missing track")?;
            let online_track = online_track_from_args(track_val)?;
            let pl_track = playlist_track_from_online(&online_track);
            with_recommender(|rec| {
                remember_track_meta(rec, &online_track);
                let count = rec
                    .playlist_add_track(id, pl_track)
                    .map_err(|e| e.to_string())?;
                Ok(serde_json::json!({ "count": count }))
            })
        }

        "playlist.remove_track" => {
            let id = args.get("id").and_then(|v| v.as_str()).ok_or("missing id")?;
            let index = args
                .get("index")
                .and_then(|v| v.as_u64())
                .ok_or("missing index")? as usize;
            with_recommender(|rec| {
                rec.playlist_remove_track(id, index).map_err(|e| e.to_string())?;
                Ok(serde_json::json!({ "removed": true }))
            })
        }

        "playlist.move_track" => {
            let id = args.get("id").and_then(|v| v.as_str()).ok_or("missing id")?;
            let from = args
                .get("from")
                .and_then(|v| v.as_u64())
                .ok_or("missing from")? as usize;
            let to = args
                .get("to")
                .and_then(|v| v.as_u64())
                .ok_or("missing to")? as usize;
            with_recommender(|rec| {
                rec.playlist_move_track(id, from, to).map_err(|e| e.to_string())?;
                Ok(serde_json::json!({ "moved": true }))
            })
        }

        // ── Playlist Import (T23: URL + CSV/JSON/M3U, single-flight cancelable) ─
        "library.playlist.import.status" => {
            let snap = import_progress_lock()
                .lock()
                .map(|p| p.clone())
                .unwrap_or_default();
            Ok(serde_json::json!({
                "active": snap.active,
                "name": snap.name,
                "current": snap.current,
                "total": snap.total,
            }))
        }

        "library.playlist.import.cancel" => {
            IMPORT_CANCELLED.store(true, Ordering::SeqCst);
            Ok(serde_json::json!({ "cancelling": true }))
        }

        "library.playlist.import" => {
            ensure_online()?;
            if IMPORT_ACTIVE
                .compare_exchange(false, true, Ordering::SeqCst, Ordering::SeqCst)
                .is_err()
            {
                return Err("Another playlist import is already in progress.".into());
            }
            IMPORT_CANCELLED.store(false, Ordering::SeqCst);
            let _guard = ImportRunGuard;

            let mode = args
                .get("mode")
                .and_then(|v| v.as_str())
                .ok_or("missing mode")?;

            if let Ok(mut p) = import_progress_lock().lock() {
                p.active = true;
                p.name = if mode == "url" {
                    "Fetching playlist…".into()
                } else {
                    "Reading file…".into()
                };
                p.current = 0;
                p.total = 0;
            }

            let parsed = match mode {
                "url" => {
                    let url = args
                        .get("url")
                        .and_then(|v| v.as_str())
                        .ok_or("missing url")?;
                    crate::online::playlist_import::fetch_public_playlist_url(url)?
                }
                "file" => {
                    let file_name = args
                        .get("fileName")
                        .and_then(|v| v.as_str())
                        .unwrap_or("playlist.csv");
                    let content = args
                        .get("content")
                        .and_then(|v| v.as_str())
                        .ok_or("missing content")?;
                    crate::online::playlist_import::parse_playlist_file(file_name, content)?
                }
                _ => return Err("unsupported import mode (expected 'url' or 'file')".into()),
            };

            let total = parsed.tracks.len();
            if let Ok(mut p) = import_progress_lock().lock() {
                p.active = true;
                p.name = parsed.name.clone();
                p.current = 0;
                p.total = total;
            }

            let mut matched_tracks: Vec<crate::online::OnlineTrack> = Vec::new();
            let mut unmatched: Vec<crate::online::playlist_import::UnmatchedTrack> = Vec::new();
            let mut cancelled = false;

            for (idx, wanted) in parsed.tracks.iter().enumerate() {
                if IMPORT_CANCELLED.load(Ordering::SeqCst) {
                    cancelled = true;
                    break;
                }
                if let Ok(mut p) = import_progress_lock().lock() {
                    p.current = idx + 1;
                }

                let query = if wanted.artist.is_empty() {
                    wanted.title.clone()
                } else {
                    format!("{} {}", wanted.title, wanted.artist)
                };

                let matched = crate::online::search(&query, false, None)
                    .ok()
                    .and_then(|page| {
                        crate::online::playlist_import::pick_best_match(wanted, &page.tracks)
                    });

                if let Some(track) = matched {
                    matched_tracks.push(track);
                } else {
                    unmatched.push(crate::online::playlist_import::UnmatchedTrack {
                        title: wanted.title.clone(),
                        artist: wanted.artist.clone(),
                    });
                }

                if idx + 1 < total && !IMPORT_CANCELLED.load(Ordering::SeqCst) {
                    std::thread::sleep(std::time::Duration::from_millis(200));
                }
            }

            let playlist_id = if matched_tracks.is_empty() {
                String::new()
            } else {
                with_recommender(|rec| {
                    let pl = rec
                        .playlist_create(&parsed.name)
                        .map_err(|e| e.to_string())?;
                    for track in &matched_tracks {
                        remember_track_meta(rec, track);
                        let pl_track = playlist_track_from_online(track);
                        let _ = rec.playlist_add_track(&pl.id, pl_track);
                    }
                    Ok(pl.id)
                })?
            };

            Ok(serde_json::json!({
                "playlistId": playlist_id,
                "name": parsed.name,
                "total": total,
                "matched": matched_tracks.len(),
                "unmatched": unmatched,
                "cancelled": cancelled,
            }))
        }

        // ── Catalog Library & Metadata-Complete Likes ────────────────────
        "library.put" => {
            let kind = args.get("kind").and_then(|v| v.as_str()).ok_or("missing kind")?;
            let id = args.get("id").and_then(|v| v.as_str()).ok_or("missing id")?;
            let title = args.get("title").and_then(|v| v.as_str()).ok_or("missing title")?;
            let entry = CatalogEntry {
                id: id.to_string(),
                kind: kind.to_string(),
                title: title.to_string(),
                subtitle: args.get("subtitle").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
                thumbnail: args.get("thumbnail").and_then(|v| v.as_str()).map(str::to_string),
                browse_id: args.get("browseId").and_then(|v| v.as_str()).map(str::to_string),
                params: args.get("params").and_then(|v| v.as_str()).map(str::to_string),
                video_id: args.get("videoId").and_then(|v| v.as_str()).map(str::to_string),
                added_at: now_ms(),
            };
            with_recommender(|rec| {
                rec.library_put(&entry).map_err(|e| e.to_string())?;
                Ok(serde_json::json!({ "saved": true }))
            })
        }

        "library.remove" => {
            let kind = args.get("kind").and_then(|v| v.as_str()).ok_or("missing kind")?;
            let id = args.get("id").and_then(|v| v.as_str()).ok_or("missing id")?;
            with_recommender(|rec| {
                rec.library_remove(kind, id).map_err(|e| e.to_string())?;
                Ok(serde_json::json!({ "removed": true }))
            })
        }

        "library.list" => {
            let kind = args.get("kind").and_then(|v| v.as_str()).ok_or("missing kind")?;
            with_recommender(|rec| {
                let entries = rec.library_list(kind).map_err(|e| e.to_string())?;
                Ok(serde_json::json!({ "entries": entries }))
            })
        }

        "library.likes.set" => {
            let track_val = args.get("track").ok_or("missing track")?;
            let track = online_track_from_args(track_val)?;
            let liked = args.get("liked").and_then(|v| v.as_bool()).unwrap_or(true);
            with_recommender(|rec| {
                remember_track_meta(rec, &track);
                let song_id = lookup_song_id(rec, &track.video_id);
                let like_state = if liked { LikeState::Liked } else { LikeState::Neutral };
                let _ = rec.set_like(&track.video_id, like_state);
                if song_id != track.video_id {
                    let _ = rec.set_like(&song_id, like_state);
                }
                if liked {
                    let entry = CatalogEntry {
                        id: track.video_id.clone(),
                        kind: "track".into(),
                        title: if track.title.is_empty() { track.video_id.clone() } else { track.title.clone() },
                        subtitle: track.artist.clone(),
                        thumbnail: track.thumbnail.clone(),
                        browse_id: track.artist_browse_id.clone(),
                        params: None,
                        video_id: Some(track.video_id.clone()),
                        added_at: now_ms(),
                    };
                    rec.library_put(&entry).map_err(|e| e.to_string())?;
                } else {
                    let _ = rec.library_remove("track", &track.video_id);
                }
                Ok(serde_json::json!({ "liked": liked }))
            })
        }

        "library.likes.list" => with_recommender(|rec| {
            let entries = rec.library_list("track").map_err(|e| e.to_string())?;
            let mut tracks: Vec<crate::online::OnlineTrack> = Vec::with_capacity(entries.len());
            let mut ids: Vec<String> = Vec::with_capacity(entries.len());
            for e in entries {
                let vid = e.video_id.unwrap_or_else(|| e.id.clone());
                ids.push(vid.clone());
                if let Some(mut meta) = load_track_meta(rec, &vid) {
                    meta.cached = crate::online::cached_file_for(&meta.song_id()).is_some()
                        || crate::online::cached_file_for(&meta.video_id).is_some();
                    tracks.push(meta);
                } else {
                    let cached = crate::online::cached_file_for(&vid).is_some();
                    tracks.push(crate::online::OnlineTrack {
                        video_id: vid,
                        title: e.title,
                        artist: e.subtitle,
                        album: String::new(),
                        duration_secs: None,
                        thumbnail: e.thumbnail,
                        cached,
                        source: String::new(),
                        artist_browse_id: e.browse_id,
                    });
                }
            }
            Ok(serde_json::json!({
                "tracks": tracks,
                "ids": ids,
            }))
        }),

        // Returns every audio file currently sitting in the offline stream cache,
        // enriched with display metadata (sled → sidecar `.meta.json` → playlists
        // → offline stem fallback) so Offline Downloads works in Airplane Mode.
        "library.downloads.list" => with_recommender(|rec| {
            let cached = crate::online::list_cached_files();
            let mut playlist_meta: HashMap<String, crate::online::OnlineTrack> = HashMap::new();
            if let Ok(pls) = rec.playlist_list() {
                for p in pls {
                    for t in p.tracks {
                        playlist_meta.entry(t.id.clone()).or_insert_with(|| online_track_from_playlist(&t));
                    }
                }
            }
            let mut total_bytes: u64 = 0;
            let mut downloads: Vec<serde_json::Value> = Vec::with_capacity(cached.len());
            for (stem, path, size_bytes) in cached {
                total_bytes = total_bytes.saturating_add(size_bytes);
                let (video_id, source) = parse_cache_stem(&stem);
                let mut track = load_track_meta(rec, &video_id)
                    .or_else(|| {
                        let sidecar = crate::online::read_track_sidecar(&path)?;
                        remember_track_meta(rec, &sidecar);
                        Some(sidecar)
                    })
                    .or_else(|| {
                        let pl = playlist_meta.get(&video_id).cloned()?;
                        remember_track_meta(rec, &pl);
                        crate::online::write_track_sidecar(&path, &pl);
                        Some(pl)
                    })
                    .unwrap_or_else(|| crate::online::OnlineTrack {
                        video_id: video_id.clone(),
                        title: video_id.clone(),
                        artist: "Offline track".into(),
                        album: String::new(),
                        duration_secs: None,
                        thumbnail: None,
                        cached: true,
                        source: source.clone(),
                        artist_browse_id: None,
                    });
                track.cached = true;
                downloads.push(serde_json::json!({
                    "track": track,
                    "sizeBytes": size_bytes,
                    "filePath": path.display().to_string(),
                }));
            }
            Ok(serde_json::json!({
                "downloads": downloads,
                "totalBytes": total_bytes,
            }))
        }),

        // One-time rate-limited online enrichment for legacy cached audio files
        // that lack sled and sidecar metadata (T12). Skips offline mode strictly.
        "library.downloads.enrich" => {
            ensure_online()?;
            const MAX_ENRICH_PER_PASS: usize = 15;
            let cached = crate::online::list_cached_files();
            let pending: Vec<(String, String, std::path::PathBuf)> = with_recommender(|rec| {
                let attempted = rec
                    .db()
                    .open_tree(TREE_ENRICH_ATTEMPTED)
                    .map_err(|e| e.to_string())?;
                let mut out = Vec::new();
                for (stem, path, _) in cached {
                    let (video_id, source) = parse_cache_stem(&stem);
                    if load_track_meta(rec, &video_id).is_some() {
                        continue;
                    }
                    if let Some(sidecar) = crate::online::read_track_sidecar(&path) {
                        remember_track_meta(rec, &sidecar);
                        continue;
                    }
                    if attempted.contains_key(video_id.as_bytes()).unwrap_or(false) {
                        continue;
                    }
                    out.push((video_id, source, path));
                }
                Ok(out)
            })?;

            let total_pending = pending.len();
            let mut enriched = 0usize;
            for (idx, (video_id, source, path)) in pending.into_iter().take(MAX_ENRICH_PER_PASS).enumerate() {
                if idx > 0 {
                    std::thread::sleep(std::time::Duration::from_millis(250));
                }
                if crate::config::AppSettings::load().offline_mode {
                    break;
                }
                let fetched = crate::online::fetch_track_metadata(&video_id, &source);
                let _ = with_recommender(|rec| {
                    if let Ok(attempted) = rec.db().open_tree(TREE_ENRICH_ATTEMPTED) {
                        let _ = attempted.insert(video_id.as_bytes(), b"1");
                    }
                    if let Some(ref meta) = fetched {
                        remember_track_meta(rec, meta);
                        crate::online::write_track_sidecar(&path, meta);
                        enriched += 1;
                    }
                    Ok(())
                });
            }
            let remaining = total_pending.saturating_sub(MAX_ENRICH_PER_PASS);
            Ok(serde_json::json!({
                "enriched": enriched,
                "remaining": remaining,
            }))
        }

        // One-time idempotent migration from legacy SharedPreferences likes/recents.
        "library.migrate_legacy" => with_recommender(|rec| {
            let mut migrated_likes = 0usize;
            let existing: HashSet<String> = rec
                .library_list("track")
                .unwrap_or_default()
                .into_iter()
                .map(|e| e.id)
                .collect();
            if let Some(arr) = args.get("likedTracks").and_then(|v| v.as_array()) {
                for item in arr {
                    if let Ok(track) = online_track_from_args(item) {
                        remember_track_meta(rec, &track);
                        let _ = rec.set_like(&track.video_id, LikeState::Liked);
                        if !existing.contains(&track.video_id) {
                            let entry = CatalogEntry {
                                id: track.video_id.clone(),
                                kind: "track".into(),
                                title: if track.title.is_empty() { track.video_id.clone() } else { track.title.clone() },
                                subtitle: track.artist.clone(),
                                thumbnail: track.thumbnail.clone(),
                                browse_id: track.artist_browse_id.clone(),
                                params: None,
                                video_id: Some(track.video_id.clone()),
                                added_at: now_ms(),
                            };
                            if rec.library_put(&entry).is_ok() {
                                migrated_likes += 1;
                            }
                        }
                    }
                }
            }
            if let Some(arr) = args.get("recentTracks").and_then(|v| v.as_array()) {
                for item in arr {
                    if let Ok(track) = online_track_from_args(item) {
                        remember_track_meta(rec, &track);
                    }
                }
            }
            Ok(serde_json::json!({ "migratedLikes": migrated_likes }))
        }),

        // ── Hybrid Recommender & Smart Mixes ─────────────────────────────
        "rec.on_song_changed" => {
            let video_id = args.get("videoId").and_then(|v| v.as_str()).unwrap_or_default();
            with_recommender(|rec| {
                if let Some(track_val) = args.get("track") {
                    if let Ok(t) = online_track_from_args(track_val) {
                        remember_track_meta(rec, &t);
                    }
                }
                if !video_id.is_empty() {
                    let song_id = lookup_song_id(rec, video_id);
                    let _ = rec.on_song_changed(&song_id);
                    if song_id != video_id {
                        let _ = rec.on_song_changed(video_id);
                    }
                }
                Ok(serde_json::json!({ "recorded": true }))
            })
        }

        "rec.on_song_ended" => {
            let video_id = args.get("videoId").and_then(|v| v.as_str()).unwrap_or_default();
            let artist = args.get("artist").and_then(|v| v.as_str()).unwrap_or_default();
            let listened_secs = args.get("listenedSecs").and_then(|v| v.as_u64()).unwrap_or(0) as u32;
            let duration_secs = args.get("durationSecs").and_then(|v| v.as_u64()).unwrap_or(0) as u32;
            with_recommender(|rec| {
                if !video_id.is_empty() && duration_secs > 0 {
                    let song_id = lookup_song_id(rec, video_id);
                    let had_record = rec.get_song_by_id(&song_id).ok().flatten().is_some();
                    let _ = rec.on_song_ended(&song_id, listened_secs, duration_secs);
                    // When the track hasn't finished acoustic indexing yet,
                    // still teach the artist affinity graph immediately.
                    if !had_record && !artist.is_empty() {
                        let _ = rec.bump_artist_listen(artist, listened_secs, duration_secs);
                    }
                }
                Ok(serde_json::json!({ "recorded": true }))
            })
        }

        "rec.get_recommendations" => {
            let current_vid = args.get("currentVideoId").and_then(|v| v.as_str()).unwrap_or_default();
            let top_n = args.get("topN").and_then(|v| v.as_u64()).unwrap_or(15).clamp(1, 50) as usize;
            with_recommender(|rec| {
                let current_song_id = if current_vid.is_empty() {
                    String::new()
                } else {
                    lookup_song_id(rec, current_vid)
                };
                let recs = rec
                    .get_recommendations(&current_song_id, top_n)
                    .map_err(|e| e.to_string())?;
                let items: Vec<serde_json::Value> = recs
                    .into_iter()
                    .filter_map(|r| {
                        let vid = lookup_video_id(rec, &r.record.id)?;
                        let track = load_track_meta(rec, &vid).unwrap_or_else(|| crate::online::OnlineTrack {
                            video_id: vid,
                            title: r.record.title.clone(),
                            artist: r.record.artist.clone(),
                            album: r.record.album.clone(),
                            duration_secs: Some(r.record.duration_secs),
                            thumbnail: None,
                            cached: true,
                            source: String::new(),
                            artist_browse_id: None,
                        });
                        Some(serde_json::json!({
                            "track": track,
                            "score": r.final_score,
                            "signal": r.dominant_signal,
                        }))
                    })
                    .collect();
                Ok(serde_json::json!({ "recommendations": items }))
            })
        }

        // Hybrid re-ranker: takes online candidates (InnerTube radio/home/search)
        // and scores them with Rust artist affinity, dislikes, likes, session
        // history, cache presence, and MMR artist diversity.
        "rec.rerank_candidates" => {
            let current_vid = args.get("currentVideoId").and_then(|v| v.as_str()).unwrap_or_default();
            let top_n = args.get("topN").and_then(|v| v.as_u64()).unwrap_or(20).clamp(1, 60) as usize;
            let raw_candidates = args
                .get("candidates")
                .and_then(|v| v.as_array())
                .ok_or("missing candidates array")?;
            let candidates: Vec<crate::online::OnlineTrack> = raw_candidates
                .iter()
                .filter_map(|v| online_track_from_args(v).ok())
                .collect();

            with_recommender(|rec| {
                let liked_set: HashSet<String> = rec.liked_ids().unwrap_or_default().into_iter().collect();
                let session_history: HashSet<String> = rec
                    .current_session()
                    .song_history
                    .iter()
                    .cloned()
                    .collect();

                let mut scored: Vec<(f32, crate::online::OnlineTrack)> = Vec::new();
                let mut seen_ids: HashSet<String> = HashSet::new();
                if !current_vid.is_empty() {
                    seen_ids.insert(current_vid.to_string());
                }

                for (idx, mut c) in candidates.into_iter().enumerate() {
                    if !seen_ids.insert(c.video_id.clone()) {
                        continue;
                    }
                    let song_id = lookup_song_id(rec, &c.video_id);
                    let like_state = rec
                        .get_like(&c.video_id)
                        .or_else(|_| rec.get_like(&song_id))
                        .unwrap_or(LikeState::Neutral);
                    if like_state == LikeState::Disliked {
                        continue;
                    }
                    let is_cached = crate::online::cached_file_for(&c.song_id()).is_some()
                        || crate::online::cached_file_for(&c.video_id).is_some();
                    c.cached = is_cached;

                    // Base relevance from upstream catalog rank (smooth decay).
                    let rank_score = 1.0 / (1.0 + (idx as f32) * 0.08);
                    let artist_score = rec.artist_affinity(&c.artist);
                    let liked_bonus = if liked_set.contains(&c.video_id) || liked_set.contains(&song_id) {
                        0.16
                    } else {
                        0.0
                    };
                    let cached_bonus = if is_cached { 0.06 } else { 0.0 };
                    let recent_penalty = if session_history.contains(&c.video_id) || session_history.contains(&song_id) {
                        -0.45
                    } else {
                        0.0
                    };
                    let total = 0.45 * rank_score + 0.45 * artist_score + liked_bonus + cached_bonus + recent_penalty;
                    scored.push((total, c));
                }

                scored.sort_by(|a, b| b.0.partial_cmp(&a.0).unwrap_or(std::cmp::Ordering::Equal));

                // MMR-inspired artist diversity pass: prevent any single artist
                // from dominating consecutive queue positions.
                let mut selected: Vec<crate::online::OnlineTrack> = Vec::with_capacity(top_n);
                let mut artist_counts: HashMap<String, usize> = HashMap::new();
                let mut last_artist = String::new();
                let mut remaining = scored;

                while selected.len() < top_n && !remaining.is_empty() {
                    let mut best_i = 0usize;
                    let mut best_adj = f32::NEG_INFINITY;
                    for (i, (base, cand)) in remaining.iter().enumerate() {
                        let key = cand.artist.trim().to_lowercase();
                        let count = if key.is_empty() { 0 } else { *artist_counts.get(&key).unwrap_or(&0) };
                        let repeat_pen = if !key.is_empty() && key == last_artist { 0.35 } else { 0.0 };
                        let cap_pen = (count as f32) * 0.14;
                        let adj = *base - repeat_pen - cap_pen;
                        if adj > best_adj {
                            best_adj = adj;
                            best_i = i;
                        }
                    }
                    let (_, picked) = remaining.remove(best_i);
                    let key = picked.artist.trim().to_lowercase();
                    if !key.is_empty() {
                        *artist_counts.entry(key.clone()).or_insert(0) += 1;
                        last_artist = key;
                    }
                    selected.push(picked);
                }

                Ok(serde_json::json!({ "tracks": selected }))
            })
        }

        // Auto-updating Smart Mixes generated directly from the Rust taste graph
        // and local cache.
        "rec.smart_mixes" => {
            let local_hour = args.get("localHour").and_then(|v| v.as_u64()).unwrap_or(12) as u32;
            with_recommender(|rec| {
                let mut mixes: Vec<serde_json::Value> = Vec::new();
                let stats = rec.get_stats().unwrap_or_default();
                let top_artists = rec.top_artists(6);

                // 1. Daily Discovery — 7-signal recommender picks across indexed tracks.
                let discovery_tracks: Vec<crate::online::OnlineTrack> = rec
                    .get_recommendations("", 20)
                    .unwrap_or_default()
                    .into_iter()
                    .filter_map(|r| {
                        let vid = lookup_video_id(rec, &r.record.id)?;
                        load_track_meta(rec, &vid)
                    })
                    .collect();
                if !discovery_tracks.is_empty() {
                    mixes.push(serde_json::json!({
                        "id": "mix-daily-discovery",
                        "title": "Daily Discovery",
                        "subtitle": "Tuned to your taste profile & audio DNA",
                        "accent": "primary",
                        "tracks": discovery_tracks,
                    }));
                }

                // 2. On Repeat — most played songs from event history.
                let mut repeat_tracks: Vec<crate::online::OnlineTrack> = Vec::new();
                for (song_id, _plays) in stats.top_songs.iter().take(20) {
                    let vid = lookup_video_id(rec, song_id).unwrap_or_else(|| song_id.clone());
                    if let Some(meta) = load_track_meta(rec, &vid) {
                        repeat_tracks.push(meta);
                    }
                }
                if !repeat_tracks.is_empty() {
                    mixes.push(serde_json::json!({
                        "id": "mix-on-repeat",
                        "title": "On Repeat",
                        "subtitle": "The songs you keep coming back to",
                        "accent": "coral",
                        "tracks": repeat_tracks,
                    }));
                }

                // 3. Time-of-day Vibe mix using local hour & acoustic energy/tempo.
                let (vibe_title, vibe_sub) = match local_hour {
                    5..=10 => ("Morning Focus", "Crisp tempo and clear melodies to start the day"),
                    11..=16 => ("Afternoon Drive", "Upbeat groove matched to your midday rhythm"),
                    17..=21 => ("Evening Unwind", "Warm, balanced textures as the day winds down"),
                    _ => ("Late Night Drift", "Deep, atmospheric cuts for after-hours listening"),
                };
                let all_songs = rec.get_all_songs().unwrap_or_default();
                if all_songs.len() >= 3 {
                    let target_energy = match local_hour {
                        5..=10 => 0.45_f32,
                        11..=16 => 0.72_f32,
                        17..=21 => 0.52_f32,
                        _ => 0.30_f32,
                    };
                    let mut scored: Vec<(f32, crate::online::OnlineTrack)> = all_songs
                        .iter()
                        .filter_map(|s| {
                            let vid = lookup_video_id(rec, &s.id)?;
                            let meta = load_track_meta(rec, &vid)?;
                            let dist = (s.feature_vector.rms_energy - target_energy).abs();
                            let affinity = rec.artist_affinity(&s.artist);
                            Some(((1.0 - dist) * 0.65 + affinity * 0.35, meta))
                        })
                        .collect();
                    scored.sort_by(|a, b| b.0.partial_cmp(&a.0).unwrap_or(std::cmp::Ordering::Equal));
                    let vibe_tracks: Vec<crate::online::OnlineTrack> =
                        scored.into_iter().take(18).map(|(_, t)| t).collect();
                    if !vibe_tracks.is_empty() {
                        mixes.push(serde_json::json!({
                            "id": "mix-daypart-vibe",
                            "title": vibe_title,
                            "subtitle": vibe_sub,
                            "accent": "mint",
                            "tracks": vibe_tracks,
                        }));
                    }
                }

                // 4. Offline Ready mix — instantly playable cached tracks.
                let cached = crate::online::list_cached_files();
                let offline_tracks: Vec<crate::online::OnlineTrack> = cached
                    .into_iter()
                    .take(25)
                    .filter_map(|(stem, _, _)| {
                        let (vid, _) = parse_cache_stem(&stem);
                        let mut t = load_track_meta(rec, &vid)?;
                        t.cached = true;
                        Some(t)
                    })
                    .collect();
                if !offline_tracks.is_empty() {
                    mixes.push(serde_json::json!({
                        "id": "mix-offline-ready",
                        "title": "Offline Ready",
                        "subtitle": "Cached on device — zero buffering, works anywhere",
                        "accent": "support",
                        "tracks": offline_tracks,
                    }));
                }

                let artist_seeds: Vec<String> = top_artists.into_iter().map(|(a, _, _)| a).collect();
                Ok(serde_json::json!({
                    "mixes": mixes,
                    "topArtists": artist_seeds,
                }))
            })
        }

        "rec.stats" => with_recommender(|rec| {
            let stats = rec.get_stats().map_err(|e| e.to_string())?;
            let top_artists: Vec<serde_json::Value> = rec
                .top_artists(8)
                .into_iter()
                .map(|(name, affinity, plays)| {
                    serde_json::json!({ "artist": name, "affinity": affinity, "plays": plays })
                })
                .collect();
            let weights = rec.get_weights().unwrap_or_default();
            Ok(serde_json::json!({
                "totalPlays": stats.total_plays,
                "totalListenedSecs": stats.total_listened_secs,
                "totalSkips": stats.total_skips,
                "streakDays": stats.streak_days,
                "avgListenRatio": stats.avg_listen_ratio,
                "topArtists": top_artists,
                "exploration": weights.exploration,
            }))
        }),

        // ── Word-synced lyrics ───────────────────────────────────────────
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

            if let Some(lrc) = crate::online::fetch_synced_lyrics(&artist, &title, duration) {
                if !lrc.synced.is_empty() {
                    let pairs: Vec<(f32, String)> = lrc
                        .synced
                        .into_iter()
                        .map(|l| (l.time_secs, l.text))
                        .collect();
                    let mut dto = crate::lyrics::build_enhanced_lines_from_timed(&pairs, dur_f32);
                    dto.track_id = track_id;
                    dto.source = "LRCLIB".into();
                    return serde_json::to_value(dto).map_err(|e| format!("lyrics serialize: {e}"));
                }
                if let Some(plain) = lrc.plain {
                    return Ok(plain_lines(&plain, track_id, "LRCLIB"));
                }
            }

            if !video_id.is_empty() {
                if let Some(text) = crate::online::fetch_lyrics(&video_id) {
                    return Ok(plain_lines(&text, track_id, "YouTube Music"));
                }
            }

            Ok(no_lyrics())
        }

        // ── Settings write-back ──────────────────────────────────────────
        "settings.save" => {
            let patch = args
                .get("settings")
                .and_then(|v| v.as_object())
                .ok_or("missing settings object")?;
            let _guard = SETTINGS_LOCK
                .lock()
                .unwrap_or_else(std::sync::PoisonError::into_inner);
            let current = serde_json::to_value(crate::config::AppSettings::load())
                .map_err(|e| format!("settings serialize: {e}"))?;
            let mut merged = current;
            for (k, v) in patch {
                merged[k] = v.clone();
            }
            let settings: crate::config::AppSettings =
                serde_json::from_value(merged).map_err(|e| format!("invalid settings: {e}"))?;
            settings.save()?;
            crate::online::stream::set_cache_cap_override_mb(u64::from(
                settings.online_cache_mb.unwrap_or(0),
            ));
            log::info!("mobile core: settings saved");
            serde_json::to_value(settings).map_err(|e| format!("settings serialize: {e}"))
        }

        // ── In-app updater ───────────────────────────────────────────────
        "app.update.check" => {
            const RELEASES_API: &str = "https://api.github.com/repos/ARYAN007H/Music0/releases/latest";
            let current = app_display_version();
            let client = crate::online::client::shared_client()
                .ok_or_else(|| "updater client unavailable".to_string())?;
            let reply: serde_json::Value = client
                .get(RELEASES_API)
                .header(reqwest::header::USER_AGENT, format!("Music0/{current}"))
                .timeout(std::time::Duration::from_secs(15))
                .send()
                .map_err(|e| format!("release check: {e}"))?
                .error_for_status()
                .map_err(|e| format!("release check: {e}"))?
                .json()
                .map_err(|e| format!("release parse: {e}"))?;

            let latest_tag = reply["tag_name"].as_str().unwrap_or_default().trim();
            let latest = latest_tag.trim_start_matches('v').to_string();
            let asset = reply["assets"].as_array().and_then(|assets| {
                assets.iter().find(|a| {
                    a["name"]
                        .as_str()
                        .map(|n| {
                            #[cfg(target_os = "android")]
                            {
                                n.starts_with("musico-online-native") && n.ends_with(".apk")
                            }
                            #[cfg(target_os = "macos")]
                            {
                                n.ends_with(".dmg")
                            }
                            #[cfg(target_os = "windows")]
                            {
                                n.ends_with(".msi") || n.ends_with(".zip")
                            }
                            #[cfg(all(
                                not(target_os = "android"),
                                not(target_os = "macos"),
                                not(target_os = "windows")
                            ))]
                            {
                                n.ends_with(".deb") || n.ends_with(".tar.gz")
                            }
                        })
                        .unwrap_or(false)
                })
            });
            let (asset_url, asset_size, asset_name, asset_digest) = match asset {
                Some(a) => (
                    a["browser_download_url"].as_str().unwrap_or_default().to_string(),
                    a["size"].as_u64().unwrap_or(0),
                    a["name"].as_str().unwrap_or_default().to_string(),
                    a["digest"].as_str().unwrap_or_default().to_string(),
                ),
                None => {
                    #[cfg(not(target_os = "android"))]
                    let fallback_url = reply["html_url"].as_str().unwrap_or_default().to_string();
                    #[cfg(target_os = "android")]
                    let fallback_url = String::new();
                    (fallback_url, 0, String::new(), String::new())
                }
            };
            let update_available =
                !latest.is_empty() && !asset_url.is_empty() && version_newer(current, &latest);

            Ok(serde_json::json!({
                "current": current,
                "latest": latest,
                "updateAvailable": update_available,
                "assetUrl": asset_url,
                "assetName": asset_name,
                "assetSizeBytes": asset_size,
                "assetDigest": asset_digest,
                "notes": reply["body"]
                    .as_str()
                    .unwrap_or_default()
                    .lines()
                    .take(12)
                    .map(|l| l.chars().take(200).collect::<String>())
                    .collect::<Vec<_>>()
                    .join("\n"),
            }))
        }

        // ── Stream cache management ──────────────────────────────────────
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

        // ── Desktop JVM audio engine (musico_playback over JNI) ──────────
        "player.play_file" => desktop_play_file(&args),
        "player.preload_next" => desktop_preload_next(&args),
        "player.toggle" => desktop_toggle(),
        "player.pause" => desktop_simple_cmd(|e| e.pause()),
        "player.resume" => desktop_simple_cmd(|e| e.resume()),
        "player.stop" => {
            let res = desktop_simple_cmd(|e| e.stop());
            trim_native_memory();
            res
        }
        "player.seek" => desktop_seek(&args),
        "player.set_volume" => desktop_set_volume(&args),
        "player.set_eq" => desktop_set_eq(&args),
        "player.state" => desktop_state(),

        "system.trim_memory" => {
            trim_native_memory();
            Ok(serde_json::json!({ "trimmed": true }))
        }

        other => Err(format!("unknown command `{other}`")),
    }
}

#[cfg(not(target_os = "android"))]
fn song_info_from_args(args: &serde_json::Value) -> Result<musico_playback::SongInfo, String> {
    let file_path = args
        .get("path")
        .or_else(|| args.get("filePath"))
        .and_then(|v| v.as_str())
        .ok_or("missing path")?
        .to_string();
    Ok(musico_playback::SongInfo {
        id: args.get("id").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
        file_path,
        title: args.get("title").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
        artist: args.get("artist").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
        album: args.get("album").and_then(|v| v.as_str()).unwrap_or_default().to_string(),
        duration_secs: args
            .get("durationSecs")
            .and_then(|v| v.as_f64())
            .unwrap_or(0.0) as f32,
        cover_art: None,
    })
}

#[cfg(not(target_os = "android"))]
fn desktop_play_file(args: &serde_json::Value) -> Result<serde_json::Value, String> {
    let song = song_info_from_args(args)?;
    let mutex = desktop_player()?;
    let guard = mutex.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
    guard.play(song).map_err(|e| e.to_string())?;
    drop(guard);
    trim_native_memory();
    Ok(serde_json::json!({ "playing": true }))
}

#[cfg(not(target_os = "android"))]
fn desktop_preload_next(args: &serde_json::Value) -> Result<serde_json::Value, String> {
    let song = song_info_from_args(args)?;
    let mutex = desktop_player()?;
    let guard = mutex.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
    guard.preload_next(song).map_err(|e| e.to_string())?;
    Ok(serde_json::json!({ "preloaded": true }))
}

#[cfg(not(target_os = "android"))]
fn desktop_toggle() -> Result<serde_json::Value, String> {
    let mutex = desktop_player()?;
    let guard = mutex.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
    let st = guard.state();
    match st.status {
        musico_playback::PlaybackStatus::Playing | musico_playback::PlaybackStatus::Buffering => {
            guard.pause().map_err(|e| e.to_string())?;
            Ok(serde_json::json!({ "playing": false }))
        }
        musico_playback::PlaybackStatus::Paused => {
            guard.resume().map_err(|e| e.to_string())?;
            Ok(serde_json::json!({ "playing": true }))
        }
        musico_playback::PlaybackStatus::Stopped => Ok(serde_json::json!({ "playing": false })),
    }
}

#[cfg(not(target_os = "android"))]
fn desktop_simple_cmd(
    f: impl FnOnce(&musico_playback::PlaybackEngine) -> Result<(), musico_playback::PlaybackError>,
) -> Result<serde_json::Value, String> {
    let mutex = desktop_player()?;
    let guard = mutex.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
    f(&guard).map_err(|e| e.to_string())?;
    Ok(serde_json::json!({ "ok": true }))
}

#[cfg(not(target_os = "android"))]
fn desktop_seek(args: &serde_json::Value) -> Result<serde_json::Value, String> {
    let ms = args.get("positionMs").and_then(|v| v.as_f64()).ok_or("missing positionMs")?;
    let secs = (ms / 1000.0).max(0.0) as f32;
    desktop_simple_cmd(|e| e.seek(secs))
}

#[cfg(not(target_os = "android"))]
fn desktop_set_volume(args: &serde_json::Value) -> Result<serde_json::Value, String> {
    let vol = args.get("volume").and_then(|v| v.as_f64()).ok_or("missing volume")? as f32;
    desktop_simple_cmd(|e| e.set_volume(vol.clamp(0.0, 1.0)))
}

#[cfg(not(target_os = "android"))]
fn desktop_set_eq(args: &serde_json::Value) -> Result<serde_json::Value, String> {
    let enabled = args.get("enabled").and_then(|v| v.as_bool()).unwrap_or(false);
    let mut gains = [0.0f32; 10];
    if let Some(arr) = args.get("gainsDb").and_then(|v| v.as_array()) {
        for (i, v) in arr.iter().take(10).enumerate() {
            gains[i] = v.as_f64().unwrap_or(0.0).clamp(-12.0, 12.0) as f32;
        }
    }
    log::info!("mobile core: desktop EQ enabled={enabled} gains={gains:?}");
    desktop_simple_cmd(|e| e.set_eq(enabled, gains))
}

#[cfg(not(target_os = "android"))]
fn desktop_state() -> Result<serde_json::Value, String> {
    let Some(mutex) = DESKTOP_PLAYER.get() else {
        return Ok(serde_json::json!({
            "status": "stopped",
            "playing": false,
            "buffering": false,
            "positionMs": 0,
            "durationMs": 0,
            "volume": 1.0,
            "muted": false,
            "ended": false,
            "endedSongId": null,
            "currentSongId": null,
            "error": null,
        }));
    };
    let guard = mutex.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
    let events = guard.poll_events();
    let mut ended = false;
    let mut ended_song_id: Option<String> = None;
    let mut last_error: Option<String> = None;
    for ev in events {
        match ev {
            musico_playback::PlaybackEvent::SongEnded { song_id, .. } => {
                ended = true;
                ended_song_id = Some(song_id);
            }
            musico_playback::PlaybackEvent::Error(msg) => {
                last_error = Some(msg);
            }
            _ => {}
        }
    }
    let st = guard.state();
    let status_str = match st.status {
        musico_playback::PlaybackStatus::Playing => "playing",
        musico_playback::PlaybackStatus::Paused => "paused",
        musico_playback::PlaybackStatus::Buffering => "buffering",
        musico_playback::PlaybackStatus::Stopped => "stopped",
    };
    Ok(serde_json::json!({
        "status": status_str,
        "playing": st.status == musico_playback::PlaybackStatus::Playing,
        "buffering": st.status == musico_playback::PlaybackStatus::Buffering,
        "positionMs": (st.position_secs.max(0.0) * 1000.0) as i64,
        "durationMs": (st.duration_secs.max(0.0) * 1000.0) as i64,
        "volume": st.volume,
        "muted": st.muted,
        "ended": ended,
        "endedSongId": ended_song_id,
        "currentSongId": st.current_song.as_ref().map(|s| s.id.clone()),
        "error": last_error,
    }))
}

#[cfg(target_os = "android")]
fn desktop_play_file(_args: &serde_json::Value) -> Result<serde_json::Value, String> {
    Err("player.play_file is desktop-only; Android uses Media3".into())
}
#[cfg(target_os = "android")]
fn desktop_preload_next(_args: &serde_json::Value) -> Result<serde_json::Value, String> {
    Err("player.preload_next is desktop-only; Android uses Media3".into())
}
#[cfg(target_os = "android")]
fn desktop_toggle() -> Result<serde_json::Value, String> {
    Err("player.toggle is desktop-only; Android uses Media3".into())
}
#[cfg(target_os = "android")]
fn desktop_simple_cmd(
    _f: impl FnOnce(&musico_playback::PlaybackEngine) -> Result<(), musico_playback::PlaybackError>,
) -> Result<serde_json::Value, String> {
    Err("desktop player command unavailable on Android".into())
}
#[cfg(target_os = "android")]
fn desktop_seek(_args: &serde_json::Value) -> Result<serde_json::Value, String> {
    Err("player.seek is desktop-only; Android uses Media3".into())
}
#[cfg(target_os = "android")]
fn desktop_set_volume(_args: &serde_json::Value) -> Result<serde_json::Value, String> {
    Err("player.set_volume is desktop-only; Android uses Media3".into())
}
#[cfg(target_os = "android")]
fn desktop_set_eq(_args: &serde_json::Value) -> Result<serde_json::Value, String> {
    Err("player.set_eq is desktop-only; Android uses Media3".into())
}
#[cfg(target_os = "android")]
fn desktop_state() -> Result<serde_json::Value, String> {
    Err("player.state is desktop-only; Android uses Media3".into())
}

fn ensure_online() -> Result<(), String> {
    let settings = crate::config::AppSettings::load();
    if settings.offline_mode {
        return Err("Offline — turn Airplane Mode off to reach YouTube Music".into());
    }
    Ok(())
}

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

fn parse_cache_stem(stem: &str) -> (String, String) {
    if let Some(rest) = stem.strip_prefix("saavn_").or_else(|| stem.strip_prefix("saavn:")) {
        (rest.to_string(), "saavn".to_string())
    } else if let Some(rest) = stem.strip_prefix("yt_").or_else(|| stem.strip_prefix("yt:")) {
        (rest.to_string(), String::new())
    } else {
        (stem.to_string(), String::new())
    }
}

fn playlist_track_from_online(t: &crate::online::OnlineTrack) -> PlaylistTrack {
    let source = match t.source.as_str() {
        "saavn" => "saavn".to_string(),
        "local" => "local".to_string(),
        _ => "yt".to_string(),
    };
    PlaylistTrack {
        source,
        id: t.video_id.clone(),
        title: if t.title.is_empty() { t.video_id.clone() } else { t.title.clone() },
        artist: t.artist.clone(),
        album: t.album.clone(),
        duration_secs: t.duration_secs,
        thumbnail: t.thumbnail.clone(),
        file_path: None,
    }
}

fn online_track_from_playlist(t: &PlaylistTrack) -> crate::online::OnlineTrack {
    crate::online::OnlineTrack {
        video_id: t.id.clone(),
        title: t.title.clone(),
        artist: t.artist.clone(),
        album: t.album.clone(),
        duration_secs: t.duration_secs,
        thumbnail: t.thumbnail.clone(),
        cached: false,
        source: if t.source == "saavn" { "saavn".into() } else { String::new() },
        artist_browse_id: None,
    }
}

/// Builds an [`crate::online::OnlineTrack`] from loose JNI args: accepts both
/// camelCase (`videoId`, `durationSecs`, `artistBrowseId`) and snake_case
/// (`video_id`, `duration_secs`, `artist_browse_id`).
fn online_track_from_args(args: &serde_json::Value) -> Result<crate::online::OnlineTrack, String> {
    use crate::online::OnlineTrack;
    let video_id = args
        .get("videoId")
        .or_else(|| args.get("video_id"))
        .and_then(|v| v.as_str())
        .ok_or("missing videoId")?
        .to_string();
    if video_id.is_empty() || video_id.len() > 64 {
        return Err("invalid videoId".into());
    }
    if !video_id
        .chars()
        .all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_')
    {
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
        duration_secs: args
            .get("durationSecs")
            .or_else(|| args.get("duration_secs"))
            .and_then(|v| v.as_u64())
            .map(|v| v as u32),
        thumbnail: args.get("thumbnail").and_then(|v| v.as_str()).map(str::to_string),
        cached: false,
        source: s("source"),
        artist_browse_id: args
            .get("artistBrowseId")
            .or_else(|| args.get("artist_browse_id"))
            .and_then(|v| v.as_str())
            .map(str::to_string),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn ensure_test_core() {
        static TEST_INIT: OnceLock<()> = OnceLock::new();
        TEST_INIT.get_or_init(|| {
            let base = std::env::temp_dir().join(format!(
                "musico_mobile_test_{}",
                std::process::id()
            ));
            let files = base.join("files");
            let cache = base.join("cache");
            init_core(&files.to_string_lossy(), &cache.to_string_lossy());
            MOBILE_INITIALIZED.store(true, Ordering::Release);
        });
    }

    #[test]
    fn ping_and_unknown_command_dispatch() {
        ensure_test_core();
        let pong = dispatch("ping", "{}").expect("ping should succeed");
        assert_eq!(pong["core"], "rust-mobile");
        assert_eq!(pong["initialized"], true);
        assert_eq!(pong["version"], app_display_version());
        assert!(dispatch("does.not.exist", "{}").is_err());
    }

    #[test]
    fn playlist_crud_and_reorder_roundtrip() {
        ensure_test_core();
        let created = dispatch("playlist.create", r#"{"name":"CMP Test Mix"}"#)
            .expect("create playlist");
        let id = created["id"].as_str().expect("playlist id").to_string();

        for (vid, title) in [("vidAAA111", "Alpha"), ("vidBBB222", "Beta"), ("vidCCC333", "Gamma")] {
            let args = serde_json::json!({
                "id": id,
                "track": {
                    "videoId": vid,
                    "title": title,
                    "artist": "Test Artist",
                    "durationSecs": 200
                }
            });
            dispatch("playlist.add_track", &args.to_string()).expect("add track");
        }

        // Move index 0 ("Alpha") down past index 1 ("Beta") -> target 2 in Rust insert semantics.
        let move_args = serde_json::json!({ "id": id, "from": 0, "to": 2 });
        dispatch("playlist.move_track", &move_args.to_string()).expect("move track");

        let list = dispatch("playlist.list", "{}").expect("list playlists");
        let pls = list["playlists"].as_array().expect("playlists array");
        let found = pls.iter().find(|p| p["id"] == id).expect("created playlist present");
        let tracks = found["tracks"].as_array().expect("tracks array");
        assert_eq!(tracks.len(), 3);
        assert_eq!(tracks[0]["id"], "vidBBB222");
        assert_eq!(tracks[1]["id"], "vidAAA111");

        dispatch("playlist.rename", &serde_json::json!({ "id": id, "name": "Renamed Mix" }).to_string())
            .expect("rename");
        dispatch("playlist.remove_track", &serde_json::json!({ "id": id, "index": 0 }).to_string())
            .expect("remove track");
        dispatch("playlist.delete", &serde_json::json!({ "id": id }).to_string())
            .expect("delete");
    }

    #[test]
    fn library_likes_and_recommender_rerank() {
        ensure_test_core();
        let fav = serde_json::json!({
            "videoId": "favTrack01",
            "title": "Midnight Pulse",
            "artist": "Aurora Synth",
            "durationSecs": 210
        });
        dispatch(
            "library.likes.set",
            &serde_json::json!({ "track": fav, "liked": true }).to_string(),
        )
        .expect("like track");

        let likes = dispatch("library.likes.list", "{}").expect("list likes");
        let ids = likes["ids"].as_array().expect("ids");
        assert!(ids.iter().any(|v| v == "favTrack01"));

        // Feed listening telemetry + verify MMR diversity re-ranking.
        dispatch(
            "rec.on_song_ended",
            &serde_json::json!({
                "videoId": "favTrack01",
                "artist": "Aurora Synth",
                "listenedSecs": 200,
                "durationSecs": 210
            })
            .to_string(),
        )
        .expect("record listen");

        let reranked = dispatch(
            "rec.rerank_candidates",
            &serde_json::json!({
                "currentVideoId": "curr0001",
                "topN": 4,
                "candidates": [
                    { "videoId": "c1", "title": "One", "artist": "Same Artist" },
                    { "videoId": "c2", "title": "Two", "artist": "Same Artist" },
                    { "videoId": "favTrack01", "title": "Midnight Pulse", "artist": "Aurora Synth" },
                    { "videoId": "c4", "title": "Four", "artist": "Fresh Artist" }
                ]
            })
            .to_string(),
        )
        .expect("rerank");
        let out = reranked["tracks"].as_array().expect("reranked tracks");
        assert_eq!(out.len(), 4);
        assert_eq!(out[0]["video_id"], "favTrack01");

        let stats = dispatch("rec.stats", "{}").expect("stats");
        assert!(stats["totalPlays"].is_number());
    }

    #[test]
    fn version_newer_compares_semver() {
        assert!(version_newer("0.9.5", "0.9.6"));
        assert!(version_newer("0.9.6-cmp", "0.9.7"));
        assert!(!version_newer("0.9.6-cmp", "0.9.6"));
        assert!(version_newer("0.9.5", "1.0.0"));
        assert!(!version_newer("1.0.0", "0.9.9"));
        assert!(!version_newer("0.9.5", "0.9.5"));
    }

    #[test]
    fn downloads_list_uses_sidecar_and_falls_back_to_stem_offline() {
        ensure_test_core();
        let _tc = crate::online::stream::test_support::TestCache::acquire();
        let dir = crate::online::stream::cache_dir();
        std::fs::create_dir_all(&dir).expect("cache dir");

        let with_sidecar = dir.join("sidecar01.m4a");
        let legacy_stem = dir.join("sj342ms.m4a");
        std::fs::write(&with_sidecar, vec![0x11u8; 16 * 1024]).expect("write audio 1");
        std::fs::write(&legacy_stem, vec![0x22u8; 16 * 1024]).expect("write audio 2");

        crate::online::write_track_sidecar(
            &with_sidecar,
            &crate::online::OnlineTrack {
                video_id: "sidecar01".into(),
                title: "Neon Horizon".into(),
                artist: "Pulsewave".into(),
                album: String::new(),
                duration_secs: Some(198),
                thumbnail: None,
                cached: true,
                source: String::new(),
                artist_browse_id: None,
            },
        );

        let reply = dispatch("library.downloads.list", "{}").expect("downloads list");
        let list = reply["downloads"].as_array().expect("downloads array");

        let enriched_row = list
            .iter()
            .find(|r| r["track"]["video_id"] == "sidecar01")
            .expect("sidecar row present");
        assert_eq!(enriched_row["track"]["title"], "Neon Horizon");
        assert_eq!(enriched_row["track"]["artist"], "Pulsewave");

        let legacy_row = list
            .iter()
            .find(|r| r["track"]["video_id"] == "sj342ms")
            .expect("legacy stem row present");
        assert_eq!(legacy_row["track"]["title"], "sj342ms");
        assert_eq!(legacy_row["track"]["artist"], "Offline track");

        let _ = std::fs::remove_file(&with_sidecar);
        let _ = std::fs::remove_file(with_sidecar.with_extension("meta.json"));
        let _ = std::fs::remove_file(&legacy_stem);
    }

    #[test]
    fn playlist_import_status_cancel_and_validation_dispatch() {
        ensure_test_core();
        let status = dispatch("library.playlist.import.status", "{}").expect("status");
        assert_eq!(status["active"], false);

        let cancel = dispatch("library.playlist.import.cancel", "{}").expect("cancel");
        assert_eq!(cancel["cancelling"], true);

        // Unsupported mode and disallowed URLs must fail cleanly without panicking.
        assert!(dispatch("library.playlist.import", r#"{"mode":"bogus"}"#).is_err());
        assert!(dispatch(
            "library.playlist.import",
            r#"{"mode":"url","url":"http://127.0.0.1/playlist/1234567890"}"#
        )
        .is_err());
        assert!(dispatch(
            "library.playlist.import",
            r#"{"mode":"file","fileName":"empty.csv","content":""}"#
        )
        .is_err());
    }
}
