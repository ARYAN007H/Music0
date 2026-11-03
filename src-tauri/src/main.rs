//! # Musico — Desktop Binary Entry Point
//!
//! Delegates directly to `musico_online_lib::run()` so Desktop (`musico-online`)
//! and Android APK (`libmusico_online_lib.so`) share 100% of their setup and IPC
//! command handlers.

// Prevents console window on Windows in release.
#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

fn main() {
    musico_online_lib::run();
}
