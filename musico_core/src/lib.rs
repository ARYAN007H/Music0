//! # Musico Core — Pure-Rust JNI Engine for Compose Multiplatform
//!
//! Shared backend for Android (`:app`) and Desktop JVM (`:desktopApp`)
//! exposed through the JNI bridge in [`mobile`]. Zero Tauri or WebView
//! dependencies.

pub mod config;
pub mod cover;
pub mod identity;
pub mod lyrics;
pub mod mobile;
pub mod online;
