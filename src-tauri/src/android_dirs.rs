//! Android storage bootstrap — makes `dirs`/`identity.rs` work on Android.
//!
//! On Android an app process has no `$HOME`, and `dirs-sys`' android fallback
//! returns `None` unconditionally, so every `identity::*_dir()` caller gets
//! `None`: the recommender DB, settings, cover cache, stream cache and the
//! log file all silently die (issue #108).
//!
//! tao's android glue runs `ndk_context::initialize_android_context`
//! (JavaVM + Activity) BEFORE the Rust entry point executes, so from `run()`
//! we can JNI-call `getFilesDir()`/`getCacheDir()` on that context and export
//! the XDG variables that `dirs`' unix code path (the one compiled on
//! android) resolves against. Everything downstream — `identity.rs`
//! included — then behaves exactly as on the desktop, with no changes to its
//! callers.
//!
//! Paths (private, no permissions needed, wiped on uninstall):
//! - `XDG_DATA_HOME` / `XDG_CONFIG_HOME` → `<files>` → `…/files/musico-online`
//! - `XDG_CACHE_HOME`                    → `<cache>` → `…/cache/musico-online`
//! - `HOME`                              → `<files>` (covers `audio_dir`'s
//!   `$HOME/Music` fallback and anything else that reads it)

use jni::objects::{JObject, JString, JValue};

/// Reads the app-private files/cache dirs via JNI and exports the XDG
/// variables. Failure-tolerant by contract: on any problem we log and leave
/// the environment untouched (the app then behaves as before — degraded, not
/// crashed).
pub fn bootstrap() {
    if let Err(e) = try_bootstrap() {
        log::warn!("android dirs: storage bootstrap failed: {e}");
    }
}

fn try_bootstrap() -> Result<(), String> {
    // `android_context()` panics when the glue never ran; boot must survive.
    let ctx = std::panic::catch_unwind(ndk_context::android_context)
        .map_err(|_| "ndk context not initialized (glue did not run)".to_string())?;

    // attach_current_thread_permanently is a no-op when the thread is already
    // attached (tao attached this thread before calling run()) and keeps the
    // attachment for the thread's lifetime — `run()` continues on it.
    let vm = unsafe { jni::JavaVM::from_raw(ctx.vm().cast()) }
        .map_err(|e| format!("JavaVM::from_raw: {e}"))?;
    let mut env = vm
        .attach_current_thread_permanently()
        .map_err(|e| format!("JNI attach: {e}"))?;

    // Borrow the stored Activity as a Context — non-owning; the glue keeps
    // the GlobalRef alive for the process lifetime.
    let activity = unsafe { JObject::from_raw(ctx.context().cast()) };

    let files_dir = context_path(&mut env, &activity, "getFilesDir")?;
    let cache_dir = context_path(&mut env, &activity, "getCacheDir")?;

    std::env::set_var("HOME", &files_dir);
    std::env::set_var("XDG_DATA_HOME", &files_dir);
    std::env::set_var("XDG_CONFIG_HOME", &files_dir);
    std::env::set_var("XDG_CACHE_HOME", &cache_dir);
    log::info!("android dirs: data/config={files_dir} cache={cache_dir}");
    Ok(())
}

/// Calls `<Context>.<method>()` (returning `java.io.File`) and returns its
/// absolute path as a Rust string.
fn context_path(env: &mut jni::JNIEnv, context: &JObject, method: &str) -> Result<String, String> {
    let sig = "()Ljava/io/File;";
    let file = env
        .call_method(context, method, sig, &[] as &[JValue])
        .and_then(|v| v.l())
        .map_err(|e| format!("{method}{sig}: {e}"))?;
    if file.is_null() {
        return Err(format!("{method}() returned null"));
    }

    let sig = "()Ljava/lang/String;";
    let path: JString = env
        .call_method(&file, "getAbsolutePath", sig, &[] as &[JValue])
        .and_then(|v| v.l())
        .map_err(|e| format!("getAbsolutePath{sig}: {e}"))?
        .into();

    env.get_string(&path)
        .map(|s| String::from(s))
        .map_err(|e| format!("reading path string: {e}"))
}
