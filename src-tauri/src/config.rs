//! # Musico — Settings Persistence
//!
//! Loads/saves user preferences to `~/.config/musico-online/settings.json`.
//! All fields have serde defaults so old/partial config files stay loadable.

use std::path::PathBuf;

use serde::{Deserialize, Serialize};

const SETTINGS_FILE: &str = "settings.json";

/// Persisted user settings.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppSettings {
    #[serde(default)]
    pub music_folder: Option<String>,
    #[serde(default = "default_volume")]
    pub volume: f32,
    #[serde(default = "default_palette")]
    pub palette: String,
    #[serde(default = "default_true")]
    pub art_tint: bool,
    #[serde(default = "default_true")]
    pub smart_recs: bool,
    #[serde(default)]
    pub eq_enabled: bool,
    #[serde(default = "default_eq_preset")]
    pub eq_preset: String,
    #[serde(default = "default_norm")]
    pub normalization: String,
    #[serde(default)]
    pub crossfade_enabled: bool,
    #[serde(default = "default_crossfade_duration")]
    pub crossfade_duration: f32,
    #[serde(default = "default_view_mode")]
    pub library_view_mode: String,
    /// 0=off, 1=shuffle, 2=smart radio
    #[serde(default)]
    pub shuffle_mode: u8,
    /// 0=off, 1=repeat one, 2=repeat all
    #[serde(default)]
    pub repeat_mode: u8,
    /// UI theme: "light" | "dark"
    #[serde(default = "default_theme")]
    pub theme: String,
    /// UI design language (skin): "ios" (Cupertino default) | "m3e"
    /// (Material 3 Expressive). Orthogonal to theme/palette — the M3E skin
    /// re-skins every surface while light/dark and the accent palette keep
    /// working under it.
    #[serde(default = "default_skin")]
    pub skin: String,
    /// Keep the music going when the queue runs dry (radio/related picks).
    /// Apple Music calls this Autoplay; default on.
    #[serde(default = "default_true")]
    pub autoplay: bool,
    /// Storage-settings slider: stream-cache cap in MB.  None = built-in
    /// default (1 GB).  The MUSICO_ONLINE_CACHE_MB env override still wins.
    #[serde(default)]
    pub online_cache_mb: Option<u32>,
    /// Airplane mode (C4.3): online features fast-fail; downloads queue.
    #[serde(default)]
    pub offline_mode: bool,
    /// Streaming audio quality: "low" (96kbps) | "normal" (160kbps) | "high" (320kbps) | "lossless"
    #[serde(default = "default_streaming_quality")]
    pub streaming_quality: String,
    /// Offline download quality: "normal" (160kbps) | "high" (320kbps) | "lossless"
    #[serde(default = "default_download_quality")]
    pub download_quality: String,
    /// Output sample rate in Hz: 44100 | 48000 | 96000
    #[serde(default = "default_sample_rate")]
    pub audio_sample_rate: u32,
    /// Gapless playback transition enabled
    #[serde(default = "default_true")]
    pub gapless_playback: bool,
    /// Predictive prefetching of upcoming queue & high-probability recommended tracks
    #[serde(default = "default_true")]
    pub predictive_prefetch: bool,
    /// Number of upcoming/recommended tracks to pre-warm (1..=5)
    #[serde(default = "default_prefetch_count")]
    pub prefetch_count: u8,
    /// Initial progressive stream buffer threshold in KB before starting playback
    #[serde(default = "default_instant_buffer_kb")]
    pub instant_play_buffer_kb: u32,
    /// Beat-Aligned Auto-DJ phrase & downbeat crossfade sync
    #[serde(default = "default_true")]
    pub autodj_beat_sync: bool,
    /// Real-time stem separation mode: "full" | "instrumental" | "vocals"
    #[serde(default = "default_stem_mode")]
    pub stem_mode: String,
    /// EBU R128 loudness normalization enabled
    #[serde(default = "default_true")]
    pub loudness_norm_enabled: bool,
    /// Target loudness in LUFS (-14.0 streaming, -16.0 audiophile, -11.0 club)
    #[serde(default = "default_target_lufs")]
    pub target_lufs: f32,
    /// Headphone AutoEQ profile ID
    #[serde(default = "default_autoeq_profile")]
    pub autoeq_profile: String,
    /// Analog tube saturation warmth enabled
    #[serde(default)]
    pub tube_warmth: bool,
    /// Show romanized pronunciation overlay in synced lyrics
    #[serde(default = "default_true")]
    pub lyrics_romanize: bool,
    /// Show English translation overlay in synced lyrics
    #[serde(default = "default_true")]
    pub lyrics_translate: bool,
}

fn default_volume() -> f32 { 0.68 }
fn default_palette() -> String { "nebula".into() }
fn default_true() -> bool { true }
fn default_eq_preset() -> String { "Flat".into() }
fn default_norm() -> String { "Off".into() }
fn default_crossfade_duration() -> f32 { 3.0 }
fn default_view_mode() -> String { "list".into() }
fn default_theme() -> String { "light".into() }
fn default_skin() -> String { "ios".into() }
fn default_streaming_quality() -> String { "high".into() }
fn default_download_quality() -> String { "high".into() }
fn default_sample_rate() -> u32 { 48000 }
fn default_prefetch_count() -> u8 { 3 }
fn default_instant_buffer_kb() -> u32 { 64 }
fn default_stem_mode() -> String { "full".into() }
fn default_target_lufs() -> f32 { -14.0 }
fn default_autoeq_profile() -> String { "flat_studio".into() }

impl Default for AppSettings {
    fn default() -> Self {
        Self {
            music_folder: None,
            volume: default_volume(),
            palette: default_palette(),
            art_tint: true,
            smart_recs: true,
            eq_enabled: false,
            eq_preset: default_eq_preset(),
            normalization: default_norm(),
            crossfade_enabled: false,
            crossfade_duration: default_crossfade_duration(),
            library_view_mode: default_view_mode(),
            shuffle_mode: 0,
            repeat_mode: 0,
            theme: default_theme(),
            skin: default_skin(),
            autoplay: true,
            online_cache_mb: None,
            offline_mode: false,
            streaming_quality: default_streaming_quality(),
            download_quality: default_download_quality(),
            audio_sample_rate: default_sample_rate(),
            gapless_playback: true,
            predictive_prefetch: true,
            prefetch_count: default_prefetch_count(),
            instant_play_buffer_kb: default_instant_buffer_kb(),
            autodj_beat_sync: true,
            stem_mode: default_stem_mode(),
            loudness_norm_enabled: true,
            target_lufs: default_target_lufs(),
            autoeq_profile: default_autoeq_profile(),
            tube_warmth: false,
            lyrics_romanize: true,
            lyrics_translate: true,
        }
    }
}

fn settings_path() -> Option<PathBuf> {
    crate::identity::config_dir().map(|d| d.join(SETTINGS_FILE))
}

impl AppSettings {
    /// Loads settings from disk; missing file → defaults.
    pub fn load() -> Self {
        let Some(path) = settings_path() else { return Self::default() };
        match std::fs::read_to_string(&path) {
            Ok(json) => serde_json::from_str(&json).unwrap_or_else(|e| {
                log::warn!("Corrupt settings file, using defaults: {e}");
                Self::default()
            }),
            Err(_) => Self::default(),
        }
    }

    /// Persists settings; creates the config dir if needed.
    pub fn save(&self) -> Result<(), String> {
        let Some(path) = settings_path() else { return Err("no config dir".into()) };
        if let Some(parent) = path.parent() {
            std::fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        let json = serde_json::to_string_pretty(self).map_err(|e| e.to_string())?;
        std::fs::write(&path, json).map_err(|e| e.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn autoplay_defaults_on_and_survives_partial_json() {
        // Old settings file without the key → autoplay defaults on.
        let partial = r#"{"volume": 0.5, "palette": "nebula"}"#;
        let s: AppSettings = serde_json::from_str(partial).unwrap();
        assert!(s.autoplay, "autoplay must default on");

        let full = AppSettings::default();
        assert!(full.autoplay);
        let json = serde_json::to_string(&full).unwrap();
        let back: AppSettings = serde_json::from_str(&json).unwrap();
        assert!(back.autoplay);

        // Explicit off round-trips.
        let off = r#"{"autoplay": false}"#;
        let s: AppSettings = serde_json::from_str(off).unwrap();
        assert!(!s.autoplay);
    }
}
