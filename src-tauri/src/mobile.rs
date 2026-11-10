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
