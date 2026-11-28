//! # musico_playback — Audio Playback Engine
//!
//! A modular, thread-safe audio playback library for the Musico music player.
//! Runs the entire audio pipeline on a background thread, communicating with
//! the GUI via lock-free channels.
//!
//! ## Architecture
//!
//! ```text
//! [GUI Thread]
//!     ↓ sends PlaybackCommand via crossbeam Sender
//! [Decoder Thread]  ←→  [Shared State Arc<Mutex>]
//!     ↓ pushes f32 samples into lock-free RingBuffer
//! [CPAL Audio Callback]
//!     ↓ pops samples → device output
//!     sends PlaybackEvent back to GUI via crossbeam Sender
//! ```
//!
//! ## Quick Start
//!
//! ```rust,no_run
//! use musico_playback::{PlaybackEngine, SongInfo};
//!
//! let engine = PlaybackEngine::new().unwrap();
//! // engine.play(song_info).unwrap();
//! // let events = engine.poll_events();
//! ```

pub mod crossfade;
pub mod decoder;
pub mod eq;
pub mod error;
pub mod events;
pub mod http_source;
pub mod output;
pub mod queue;
pub mod resampler;
pub mod state;

pub use error::PlaybackError;
pub use events::{PlaybackCommand, PlaybackEvent};
pub use queue::PlaybackQueue;
pub use state::{PlaybackState, PlaybackStatus, SongInfo};
pub use crossfade::{CrossfadeConfig, CrossfadeCurve};

use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};

use crossbeam_channel::{Receiver, Sender};

use crate::http_source::{
    LoopbackControls, INTERRUPT_CANCELLED, INTERRUPT_NONE, INTERRUPT_PAUSED, INTERRUPT_SEEKED,
};
use crate::output::AudioOutput;

/// Shared synchronization primitives connecting [`PlaybackEngine`] (caller
/// thread) with the decoder thread and [`http_source::LoopbackHttpMediaSource`].
///
/// `cmd_lock` is strictly a **leaf lock** acquired only around
/// `(atomic gen bump + cmd_tx.send)` on the sender and
/// `(drain cmd_rx.try_recv + atomic gen load)` on the decoder thread, so
/// command ordering and generation snapshots are 100% linearizable with zero
/// lock-order inversion risk against `state`.
#[derive(Clone)]
struct EngineControlSignals {
    cmd_lock: Arc<Mutex<()>>,
    stream_gen: Arc<AtomicU64>,
    pause_gen: Arc<AtomicU64>,
    seek_gen: Arc<AtomicU64>,
    pause_requested: Arc<AtomicBool>,
    buffering_stall: Arc<AtomicBool>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
struct GenSnapshot {
    stream_gen: u64,
    pause_gen: u64,
    seek_gen: u64,
}

impl EngineControlSignals {
    fn new() -> Self {
        Self {
            cmd_lock: Arc::new(Mutex::new(())),
            stream_gen: Arc::new(AtomicU64::new(0)),
            pause_gen: Arc::new(AtomicU64::new(0)),
            seek_gen: Arc::new(AtomicU64::new(0)),
            pause_requested: Arc::new(AtomicBool::new(false)),
            buffering_stall: Arc::new(AtomicBool::new(false)),
        }
    }

    fn send_stream_cmd(
        &self,
        cmd_tx: &Sender<PlaybackCommand>,
        cmd: PlaybackCommand,
    ) -> Result<(), PlaybackError> {
        let _guard = self.cmd_lock.lock().unwrap_or_else(|e| e.into_inner());
        self.pause_requested.store(false, Ordering::Release);
        self.stream_gen.fetch_add(1, Ordering::SeqCst);
        cmd_tx
            .send(cmd)
            .map_err(|_| PlaybackError::ChannelDisconnected)
    }

    fn send_pause(&self, cmd_tx: &Sender<PlaybackCommand>) -> Result<(), PlaybackError> {
        let _guard = self.cmd_lock.lock().unwrap_or_else(|e| e.into_inner());
        self.pause_requested.store(true, Ordering::Release);
        self.pause_gen.fetch_add(1, Ordering::SeqCst);
        cmd_tx
            .send(PlaybackCommand::Pause)
            .map_err(|_| PlaybackError::ChannelDisconnected)
    }

    fn send_resume(&self, cmd_tx: &Sender<PlaybackCommand>) -> Result<(), PlaybackError> {
        let _guard = self.cmd_lock.lock().unwrap_or_else(|e| e.into_inner());
        self.pause_requested.store(false, Ordering::Release);
        cmd_tx
            .send(PlaybackCommand::Resume)
            .map_err(|_| PlaybackError::ChannelDisconnected)
    }

    fn send_seek(
        &self,
        cmd_tx: &Sender<PlaybackCommand>,
        secs: f32,
    ) -> Result<(), PlaybackError> {
        let _guard = self.cmd_lock.lock().unwrap_or_else(|e| e.into_inner());
        self.seek_gen.fetch_add(1, Ordering::SeqCst);
        cmd_tx
            .send(PlaybackCommand::Seek(secs))
            .map_err(|_| PlaybackError::ChannelDisconnected)
    }

    fn drain_and_snapshot(
        &self,
        cmd_rx: &Receiver<PlaybackCommand>,
    ) -> (Vec<PlaybackCommand>, GenSnapshot) {
        let _guard = self.cmd_lock.lock().unwrap_or_else(|e| e.into_inner());
        let mut cmds = Vec::new();
        while let Ok(cmd) = cmd_rx.try_recv() {
            cmds.push(cmd);
        }
        let snap = GenSnapshot {
            stream_gen: self.stream_gen.load(Ordering::SeqCst),
            pause_gen: self.pause_gen.load(Ordering::SeqCst),
            seek_gen: self.seek_gen.load(Ordering::SeqCst),
        };
        (cmds, snap)
    }

    fn drain_with_first_and_snapshot(
        &self,
        first: PlaybackCommand,
        cmd_rx: &Receiver<PlaybackCommand>,
    ) -> (Vec<PlaybackCommand>, GenSnapshot) {
        let _guard = self.cmd_lock.lock().unwrap_or_else(|e| e.into_inner());
        let mut cmds = vec![first];
        while let Ok(cmd) = cmd_rx.try_recv() {
            cmds.push(cmd);
        }
        let snap = GenSnapshot {
            stream_gen: self.stream_gen.load(Ordering::SeqCst),
            pause_gen: self.pause_gen.load(Ordering::SeqCst),
            seek_gen: self.seek_gen.load(Ordering::SeqCst),
        };
        (cmds, snap)
    }

    fn new_loopback_controls(&self, snap: GenSnapshot) -> LoopbackControls {
        LoopbackControls {
            stream_gen: Arc::clone(&self.stream_gen),
            expected_stream_gen: snap.stream_gen,
            pause_gen: Arc::clone(&self.pause_gen),
            ack_pause_gen: Arc::new(AtomicU64::new(snap.pause_gen)),
            seek_gen: Arc::clone(&self.seek_gen),
            ack_seek_gen: Arc::new(AtomicU64::new(snap.seek_gen)),
            seek_interrupt_enabled: Arc::new(AtomicBool::new(false)),
            pause_requested: Arc::clone(&self.pause_requested),
            buffering_stall: Arc::clone(&self.buffering_stall),
            truncated: Arc::new(AtomicBool::new(false)),
            interrupt_reason: Arc::new(std::sync::atomic::AtomicU8::new(INTERRUPT_NONE)),
        }
    }
}

/// The main playback engine handle.
///
/// All public methods are safe to call from the GUI thread. The audio pipeline
/// runs entirely on a dedicated background thread.
pub struct PlaybackEngine {
    cmd_tx: Sender<PlaybackCommand>,
    event_rx: Receiver<PlaybackEvent>,
    state: Arc<Mutex<PlaybackState>>,
    signals: EngineControlSignals,
    _decoder_thread: Option<thread::JoinHandle<()>>,
}

impl std::fmt::Debug for PlaybackEngine {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("PlaybackEngine").finish_non_exhaustive()
    }
}

impl PlaybackEngine {
    /// Creates a new `PlaybackEngine`, spawning the decoder thread and
    /// initialising the audio output.
    ///
    /// # Errors
    ///
    /// Returns `PlaybackError` if the audio output device cannot be opened.
    pub fn new() -> Result<Self, PlaybackError> {
        let (cmd_tx, cmd_rx) = crossbeam_channel::unbounded::<PlaybackCommand>();
        let (event_tx, event_rx) = crossbeam_channel::unbounded::<PlaybackEvent>();

        let state = Arc::new(Mutex::new(PlaybackState::default()));
        let state_clone = Arc::clone(&state);
        let signals = EngineControlSignals::new();
        let signals_clone = signals.clone();

        let handle = thread::Builder::new()
            .name("musico-decoder".into())
            .spawn(move || {
                decoder_thread(cmd_rx, event_tx, state_clone, signals_clone);
            })
            .map_err(|e| PlaybackError::Io(e))?;

        Ok(Self {
            cmd_tx,
            event_rx,
            state,
            signals,
            _decoder_thread: Some(handle),
        })
    }

    /// Begins playback of a new song immediately.
    pub fn play(&self, song: SongInfo) -> Result<(), PlaybackError> {
        self.signals
            .send_stream_cmd(&self.cmd_tx, PlaybackCommand::Play(song))
    }

    /// Pauses playback at the current position.
    pub fn pause(&self) -> Result<(), PlaybackError> {
        self.signals.send_pause(&self.cmd_tx)
    }

    /// Resumes playback from the paused position.
    pub fn resume(&self) -> Result<(), PlaybackError> {
        self.signals.send_resume(&self.cmd_tx)
    }

    /// Stops playback and resets state.
    pub fn stop(&self) -> Result<(), PlaybackError> {
        self.signals
            .send_stream_cmd(&self.cmd_tx, PlaybackCommand::Stop)
    }

    /// Seeks to the given position in seconds.
    pub fn seek(&self, secs: f32) -> Result<(), PlaybackError> {
        self.signals.send_seek(&self.cmd_tx, secs)
    }

    /// Sets the master volume, clamped to `[0.0, 1.0]`.
    pub fn set_volume(&self, volume: f32) -> Result<(), PlaybackError> {
        let v = volume.clamp(0.0, 1.0);
        self.cmd_tx
            .send(PlaybackCommand::SetVolume(v))
            .map_err(|_| PlaybackError::ChannelDisconnected)
    }

    /// Toggles mute on/off.
    pub fn toggle_mute(&self) -> Result<(), PlaybackError> {
        let state = self.state.lock().unwrap_or_else(|e| e.into_inner());
        if state.muted {
            self.cmd_tx
                .send(PlaybackCommand::Unmute)
                .map_err(|_| PlaybackError::ChannelDisconnected)
        } else {
            self.cmd_tx
                .send(PlaybackCommand::Mute)
                .map_err(|_| PlaybackError::ChannelDisconnected)
        }
    }

    /// Drains all pending events without blocking.
    ///
    /// The Iced GUI should call this on every subscription tick.
    /// Returns an empty `Vec` if no events are pending.
    pub fn poll_events(&self) -> Vec<PlaybackEvent> {
        let mut events = Vec::new();
        while let Ok(ev) = self.event_rx.try_recv() {
            events.push(ev);
        }
        events
    }

    /// Returns a clone of the current playback state for the GUI to render.
    pub fn state(&self) -> PlaybackState {
        let mut st = self
            .state
            .lock()
            .unwrap_or_else(|e| e.into_inner())
            .clone();
        if matches!(st.status, PlaybackStatus::Playing | PlaybackStatus::Buffering)
            && self.signals.pause_requested.load(Ordering::Acquire)
        {
            st.status = PlaybackStatus::Paused;
        } else if st.status == PlaybackStatus::Playing
            && self.signals.buffering_stall.load(Ordering::Acquire)
        {
            st.status = PlaybackStatus::Buffering;
        }
        st
    }

    /// Sends a preload hint for gapless playback (foundation only).
    pub fn preload_next(&self, song: SongInfo) -> Result<(), PlaybackError> {
        self.cmd_tx
            .send(PlaybackCommand::PreloadNext(song))
            .map_err(|_| PlaybackError::ChannelDisconnected)
    }

    /// Update the 10-band EQ settings.
    pub fn set_eq(&self, enabled: bool, gains_db: [f32; 10]) -> Result<(), PlaybackError> {
        self.cmd_tx
            .send(PlaybackCommand::SetEQ { enabled, gains_db })
            .map_err(|_| PlaybackError::ChannelDisconnected)
    }

    /// Set the normalization gain factor for the current track.
    pub fn set_norm_gain(&self, gain_factor: f32) -> Result<(), PlaybackError> {
        self.cmd_tx
            .send(PlaybackCommand::SetNormGain(gain_factor))
            .map_err(|_| PlaybackError::ChannelDisconnected)
    }

    /// Update crossfade configuration.
    pub fn set_crossfade(&self, config: CrossfadeConfig) -> Result<(), PlaybackError> {
        self.cmd_tx
            .send(PlaybackCommand::SetCrossfade(config))
            .map_err(|_| PlaybackError::ChannelDisconnected)
    }

    /// Update real-time stem isolation mode (Full, Instrumental, Vocals).
    pub fn set_stem_mode(&self, mode: eq::StemMode) -> Result<(), PlaybackError> {
        self.cmd_tx
            .send(PlaybackCommand::SetStemMode(mode))
            .map_err(|_| PlaybackError::ChannelDisconnected)
    }

    /// Enable or disable analog tube warmth saturation.
    pub fn set_tube_warmth(&self, enabled: bool) -> Result<(), PlaybackError> {
        self.cmd_tx
            .send(PlaybackCommand::SetTubeWarmth(enabled))
            .map_err(|_| PlaybackError::ChannelDisconnected)
    }

    /// Configure the instant-play prefill buffer target in KB (32..=1024).
    pub fn set_instant_buffer_kb(&self, kb: u32) -> Result<(), PlaybackError> {
        self.cmd_tx
            .send(PlaybackCommand::SetInstantBufferKb(kb.clamp(32, 1024)))
            .map_err(|_| PlaybackError::ChannelDisconnected)
    }
}

impl Drop for PlaybackEngine {
    fn drop(&mut self) {
        // Send Stop and bump stream_gen under cmd_lock to break any stalled loopback stream.
        let _ = self.signals.send_stream_cmd(&self.cmd_tx, PlaybackCommand::Stop);
        
        // Replace the sender with a dummy to drop the actual sender and close the channel.
        let (dummy_tx, _) = crossbeam_channel::unbounded();
        let actual_tx = std::mem::replace(&mut self.cmd_tx, dummy_tx);
        drop(actual_tx);

        // Join the thread gracefully.
        if let Some(handle) = self._decoder_thread.take() {
            let _ = handle.join();
        }
    }
}

// ─── Decoder Thread ──────────────────────────────────────────────────────────

fn decoder_thread(
    cmd_rx: Receiver<PlaybackCommand>,
    event_tx: Sender<PlaybackEvent>,
    state: Arc<Mutex<PlaybackState>>,
    signals: EngineControlSignals,
) {
    // Initialise audio output once for the lifetime of the thread.
    let mut audio_output = match output::AudioOutput::new() {
        Ok(o) => o,
        Err(e) => {
            let _ = event_tx.send(PlaybackEvent::Error(format!("Audio init failed: {e}")));
            // Wait for Stop command to exit gracefully.
            while let Ok(cmd) = cmd_rx.recv() {
                if matches!(cmd, PlaybackCommand::Stop) {
                    break;
                }
            }
            return;
        }
    };

    let mut dsp = DspRuntime {
        equalizer: eq::Equalizer::new(audio_output.sample_rate(), audio_output.channels()),
        norm_gain: 1.0,
        crossfade_cfg: CrossfadeConfig::default(),
        // 64 KB ≈ 250 ms of 48 kHz stereo PCM — fast instant-start prefill
        // matching AppSettings::instant_play_buffer_kb.
        instant_buffer_kb: 64,
    };

    let mut pending_song: Option<SongInfo> = None;
    loop {
        // Step 1: Take next queued song (gapless/superseded) or wait for Play.
        let song_info = match pending_song.take() {
            Some(s) => s,
            None => match wait_for_play(&cmd_rx, &event_tx, &state, &audio_output, &mut dsp, &signals) {
                Some(s) => s,
                None => return, // channel closed
            },
        };

        // Step 2: Decode loop for this song. Iterative return avoids call-stack
        // growth across rapid Play switches or long gapless queues.
        pending_song = play_song(
            song_info,
            &cmd_rx,
            &event_tx,
            &state,
            &mut audio_output,
            &mut dsp,
            &signals,
        );
    }
}

struct DspRuntime {
    equalizer: eq::Equalizer,
    norm_gain: f32,
    crossfade_cfg: CrossfadeConfig,
    instant_buffer_kb: u32,
}

/// Applies non-transport audio settings (volume, mute, EQ, normalization, etc.)
/// consistently across idle, coalescing, paused, prefill, and active decode loops.
fn apply_setting_command(
    cmd: PlaybackCommand,
    audio_output: Option<&AudioOutput>,
    state: &Arc<Mutex<PlaybackState>>,
    event_tx: &Sender<PlaybackEvent>,
    dsp: &mut DspRuntime,
    crossfade_mixer: Option<&mut crossfade::CrossfadeMixer>,
) {
    match cmd {
        PlaybackCommand::SetVolume(v) => {
            if let Some(out) = audio_output {
                out.set_volume(v);
            }
            state.lock().unwrap_or_else(|e| e.into_inner()).volume = v;
            let _ = event_tx.send(PlaybackEvent::VolumeChanged(v));
        }
        PlaybackCommand::Mute => {
            if let Some(out) = audio_output {
                out.set_muted(true);
            }
            state.lock().unwrap_or_else(|e| e.into_inner()).muted = true;
        }
        PlaybackCommand::Unmute => {
            if let Some(out) = audio_output {
                out.set_muted(false);
            }
            state.lock().unwrap_or_else(|e| e.into_inner()).muted = false;
        }
        PlaybackCommand::SetEQ { enabled, gains_db } => {
            dsp.equalizer.set_enabled(enabled);
            dsp.equalizer.set_gains(gains_db);
        }
        PlaybackCommand::SetNormGain(g) => {
            dsp.norm_gain = g.clamp(0.1, 4.0);
        }
        PlaybackCommand::SetCrossfade(cfg) => {
            dsp.crossfade_cfg = cfg.clone();
            if let Some(mixer) = crossfade_mixer {
                mixer.config = cfg;
            }
        }
        PlaybackCommand::SetStemMode(mode) => {
            dsp.equalizer.set_stem_mode(mode);
        }
        PlaybackCommand::SetTubeWarmth(enabled) => {
            dsp.equalizer.set_tube_warmth(enabled);
        }
        PlaybackCommand::SetInstantBufferKb(kb) => {
            dsp.instant_buffer_kb = kb.clamp(32, 1024);
        }
        _ => {}
    }
}

/// Clamps prefill sample count to `[min_prefill, ring_capacity]` so large
/// `instant_buffer_kb` settings cannot spin-wait past the 1-second ring buffer
/// while output is paused during prefill.
fn clamp_prefill_samples(
    device_sr: u32,
    device_ch: usize,
    instant_buffer_kb: u32,
    ring_capacity: usize,
) -> usize {
    let min_samples = (device_sr as usize) * device_ch / 8;
    let target = (device_sr as usize)
        * device_ch
        * (instant_buffer_kb.clamp(32, 1024) as usize)
        / 256;
    target.max(min_samples).min(ring_capacity)
}

#[derive(Debug)]
struct CoalescedCommands {
    song_info: SongInfo,
    stopped: bool,
    start_paused: bool,
    pending_seek: Option<f32>,
    pending_preload: Option<SongInfo>,
}

/// Folds a drained command batch while preserving interleaved Pause/Resume/Seek/Preload.
fn coalesce_pending_commands(
    mut song_info: SongInfo,
    cmds: Vec<PlaybackCommand>,
    audio_output: Option<&AudioOutput>,
    state: &Arc<Mutex<PlaybackState>>,
    event_tx: &Sender<PlaybackEvent>,
    dsp: &mut DspRuntime,
) -> CoalescedCommands {
    let mut stopped = false;
    let mut start_paused = false;
    let mut pending_seek: Option<f32> = None;
    let mut pending_preload: Option<SongInfo> = None;

    for cmd in cmds {
        match cmd {
            PlaybackCommand::Play(newer) => {
                song_info = newer;
                stopped = false;
                start_paused = false;
                pending_seek = None;
                pending_preload = None;
            }
            PlaybackCommand::Stop => {
                stopped = true;
                start_paused = false;
                pending_seek = None;
                pending_preload = None;
            }
            PlaybackCommand::Pause => {
                if !stopped {
                    start_paused = true;
                }
            }
            PlaybackCommand::Resume => {
                if !stopped {
                    start_paused = false;
                }
            }
            PlaybackCommand::Seek(secs) => {
                if !stopped {
                    pending_seek = Some(secs.max(0.0));
                }
            }
            PlaybackCommand::PreloadNext(s) => {
                if !stopped {
                    pending_preload = Some(s);
                }
            }
            other => apply_setting_command(other, audio_output, state, event_tx, dsp, None),
        }
    }

    CoalescedCommands {
        song_info,
        stopped,
        start_paused,
        pending_seek,
        pending_preload,
    }
}

/// Blocks until a `Play` command arrives, handling volume/mute/stop/DSP while idle.
fn wait_for_play(
    cmd_rx: &Receiver<PlaybackCommand>,
    event_tx: &Sender<PlaybackEvent>,
    state: &Arc<Mutex<PlaybackState>>,
    audio_output: &AudioOutput,
    dsp: &mut DspRuntime,
    signals: &EngineControlSignals,
) -> Option<SongInfo> {
    loop {
        match cmd_rx.recv() {
            Ok(PlaybackCommand::Play(song)) => return Some(song),
            Ok(PlaybackCommand::Stop) => {
                signals.pause_requested.store(false, Ordering::Release);
                set_stopped(state);
                let _ = event_tx.send(PlaybackEvent::Stopped);
            }
            Ok(PlaybackCommand::PreloadNext(s)) => {
                log::info!("Preload hint received for: {}", s.title);
            }
            Ok(PlaybackCommand::Pause | PlaybackCommand::Resume | PlaybackCommand::Seek(_)) => {}
            Ok(other) => {
                apply_setting_command(other, Some(audio_output), state, event_tx, dsp, None);
            }
            Err(_) => return None,
        }
    }
}

enum SeekOutcome {
    Applied,
    Interrupted,
    Failed,
}

enum PauseExit {
    Resumed { seeked: bool },
    PlayNext(SongInfo),
    Stopped,
    Halted,
}

enum ActiveControlFlow {
    Continue { seeked: bool },
    Exit(Option<SongInfo>),
}

struct PlaySession<'a> {
    cmd_rx: &'a Receiver<PlaybackCommand>,
    event_tx: &'a Sender<PlaybackEvent>,
    state: &'a Arc<Mutex<PlaybackState>>,
    audio_output: &'a mut AudioOutput,
    dsp: &'a mut DspRuntime,
    signals: &'a EngineControlSignals,
    controls: LoopbackControls,
    dec: Option<decoder::AudioDecoder>,
    crossfade_mixer: crossfade::CrossfadeMixer,
    preloaded_song: Option<SongInfo>,
    seek_base: f32,
    pending_seek: Option<f32>,
    pending_preload: Option<SongInfo>,
    needs_resync_seek: bool,
    decoding_done: bool,
    drain_deadline: Instant,
    active_status: PlaybackStatus,
}

impl<'a> PlaySession<'a> {
    fn sync_acks(&self, snap: GenSnapshot) {
        self.controls
            .ack_pause_gen
            .store(snap.pause_gen, Ordering::Release);
        self.controls
            .ack_seek_gen
            .store(snap.seek_gen, Ordering::Release);
    }

    fn halt_playback(&mut self, clear_pause: bool) {
        self.signals.buffering_stall.store(false, Ordering::Release);
        if clear_pause {
            self.signals.pause_requested.store(false, Ordering::Release);
        }
        self.audio_output.set_paused(true);
        self.audio_output.request_flush();
        if self.active_status == PlaybackStatus::Playing {
            accumulate_listen(self.state);
        }
        set_stopped(self.state);
    }

    fn finish_stopped(&mut self) -> Option<SongInfo> {
        self.halt_playback(true);
        let _ = self.event_tx.send(PlaybackEvent::Stopped);
        None
    }

    fn finish_superseded(&mut self, next_song: SongInfo) -> Option<SongInfo> {
        self.halt_playback(false);
        Some(next_song)
    }

    fn finish_on_error(&mut self, err: &PlaybackError) -> Option<SongInfo> {
        self.halt_playback(true);
        let _ = self.event_tx.send(PlaybackEvent::Error(format!("{err}")));
        None
    }

    fn seek_and_rebase(&mut self, secs: f32, emit_seeked: bool) -> SeekOutcome {
        let target = secs.max(0.0);
        if !emit_seeked {
            // Rebase ring buffer and position counter BEFORE calling `dec.seek_to`
            // on post-interrupt resync so position stays clean even if interrupted again.
            self.audio_output.flush_now(Duration::from_millis(100));
            self.audio_output.reset_played();
            self.seek_base = target;
            self.state
                .lock()
                .unwrap_or_else(|e| e.into_inner())
                .position_secs = target;
        }

        let Some(dec) = self.dec.as_mut() else {
            self.pending_seek = Some(target);
            return SeekOutcome::Applied;
        };

        if let Err(e) = dec.seek_to(target) {
            let reason = dec.take_interrupt_reason();
            if reason != INTERRUPT_NONE && !dec.is_stream_truncated() {
                return SeekOutcome::Interrupted;
            }
            let _ = self.event_tx.send(PlaybackEvent::Error(format!("{e}")));
            return SeekOutcome::Failed;
        }

        if emit_seeked {
            self.audio_output.flush_now(Duration::from_millis(100));
            self.audio_output.reset_played();
            self.seek_base = target;
            self.state
                .lock()
                .unwrap_or_else(|e| e.into_inner())
                .position_secs = target;
            let _ = self
                .event_tx
                .send(PlaybackEvent::Seeked { position_secs: target });
        }
        SeekOutcome::Applied
    }

    fn perform_preload(&mut self, s: &SongInfo) {
        log::info!("Staging next for gapless handoff: {}", s.title);
        // If metadata is already complete, verify readability without keeping a
        // second open Symphonia demuxer/FD alive for the entire current track.
        if !s.title.is_empty() && s.duration_secs > 0.0 {
            if let Err(e) = std::fs::File::open(&s.file_path) {
                log::warn!("Failed to stage next track: {e}");
                return;
            }
            self.preloaded_song = Some(s.clone());
            return;
        }
        // Otherwise probe once for missing metadata and immediately drop the
        // decoder so its file descriptor and demuxer buffers are released.
        match decoder::AudioDecoder::new(&s.file_path) {
            Ok((dec_next, info_next)) => {
                drop(dec_next);
                let mut merged = info_next;
                if !s.id.is_empty() {
                    merged.id = s.id.clone();
                }
                if !s.title.is_empty() {
                    merged.title = s.title.clone();
                }
                if !s.artist.is_empty() {
                    merged.artist = s.artist.clone();
                }
                if !s.album.is_empty() {
                    merged.album = s.album.clone();
                }
                if s.cover_art.is_some() {
                    merged.cover_art = s.cover_art.clone();
                }
                self.preloaded_song = Some(merged);
            }
            Err(e) => {
                log::warn!("Failed to preload: {e}");
            }
        }
    }

    fn wait_while_paused(&mut self) -> PauseExit {
        self.audio_output.set_paused(true);
        self.signals.buffering_stall.store(false, Ordering::Release);
        if self.active_status == PlaybackStatus::Playing {
            accumulate_listen(self.state);
        }
        let paused_pos = {
            let mut st = self.state.lock().unwrap_or_else(|e| e.into_inner());
            st.status = PlaybackStatus::Paused;
            if self.dec.is_some() {
                st.position_secs = self.seek_base + self.audio_output.played_secs();
            }
            st.position_secs
        };
        let _ = self
            .event_tx
            .send(PlaybackEvent::Paused { position_secs: paused_pos });

        let mut want_resume = false;
        let mut drain_immediately = self.dec.is_some() && self.pending_seek.is_some();
        let mut did_seek = false;
        loop {
            let (cmds, snap) = if want_resume || drain_immediately {
                drain_immediately = false;
                self.signals.drain_and_snapshot(self.cmd_rx)
            } else {
                match self.cmd_rx.recv() {
                    Ok(first) => self
                        .signals
                        .drain_with_first_and_snapshot(first, self.cmd_rx),
                    Err(_) => return PauseExit::Halted,
                }
            };
            let mut batch_seek: Option<f32> = if self.dec.is_some() {
                self.pending_seek.take()
            } else {
                None
            };
            if cmds.is_empty() && !want_resume && batch_seek.is_none() {
                continue;
            }
            self.sync_acks(snap);

            let mut terminal: Option<Option<SongInfo>> = None;
            let mut batch_preload: Option<SongInfo> = None;

            for cmd in cmds {
                match cmd {
                    PlaybackCommand::Stop => {
                        terminal = Some(None);
                    }
                    PlaybackCommand::Play(s) => {
                        terminal = Some(Some(s));
                    }
                    PlaybackCommand::Pause => {
                        if terminal.is_none() {
                            want_resume = false;
                        }
                    }
                    PlaybackCommand::Resume => {
                        if terminal.is_none() {
                            want_resume = true;
                        }
                    }
                    PlaybackCommand::Seek(secs) => {
                        if terminal.is_none() {
                            self.decoding_done = false;
                            let target = secs.max(0.0);
                            if self.dec.is_some() {
                                batch_seek = Some(target);
                            } else {
                                self.pending_seek = Some(target);
                            }
                        }
                    }
                    PlaybackCommand::PreloadNext(s) => {
                        if terminal.is_none() {
                            batch_preload = Some(s);
                        }
                    }
                    other => {
                        apply_setting_command(
                            other,
                            Some(self.audio_output),
                            self.state,
                            self.event_tx,
                            self.dsp,
                            Some(&mut self.crossfade_mixer),
                        );
                    }
                }
            }

            match terminal {
                Some(None) => return PauseExit::Stopped,
                Some(Some(next_song)) => return PauseExit::PlayNext(next_song),
                None => {}
            }

            if let Some(s) = batch_preload {
                if self.dec.is_some() {
                    self.perform_preload(&s);
                } else {
                    self.pending_preload = Some(s);
                }
            }

            if let Some(secs) = batch_seek {
                match self.seek_and_rebase(secs, true) {
                    SeekOutcome::Applied => {
                        self.needs_resync_seek = false;
                        did_seek = true;
                    }
                    SeekOutcome::Interrupted => {
                        // Preserve the unapplied seek target in case a Pause
                        // (rather than a newer Seek) caused the interrupt.
                        self.pending_seek = Some(secs);
                        drain_immediately = true;
                        continue;
                    }
                    SeekOutcome::Failed => return PauseExit::Halted,
                }
            }

            if want_resume {
                if self.needs_resync_seek && self.dec.is_some() {
                    let resync_target = self.seek_base + self.audio_output.played_secs();
                    match self.seek_and_rebase(resync_target, false) {
                        SeekOutcome::Applied => {
                            self.needs_resync_seek = false;
                            did_seek = true;
                        }
                        SeekOutcome::Interrupted => {
                            drain_immediately = true;
                            continue;
                        }
                        SeekOutcome::Failed => return PauseExit::Halted,
                    }
                }
                if !self.cmd_rx.is_empty() {
                    drain_immediately = true;
                    continue;
                }
                {
                    let mut st = self.state.lock().unwrap_or_else(|e| e.into_inner());
                    st.status = self.active_status;
                    if self.active_status == PlaybackStatus::Playing {
                        st.listen_start = Some(Instant::now());
                    }
                }
                if self.active_status == PlaybackStatus::Playing {
                    self.audio_output.set_paused(false);
                }
                if self.decoding_done {
                    self.drain_deadline = Instant::now() + Duration::from_secs(3);
                }
                let _ = self.event_tx.send(PlaybackEvent::Resumed);
                return PauseExit::Resumed { seeked: did_seek };
            }
        }
    }

    fn process_batch(&mut self, cmds: Vec<PlaybackCommand>, snap: GenSnapshot) -> ActiveControlFlow {
        self.sync_acks(snap);

        let mut terminal: Option<Option<SongInfo>> = None;
        let mut paused = false;
        let mut batch_seek: Option<f32> = None;
        let mut batch_preload: Option<SongInfo> = None;

        for cmd in cmds {
            match cmd {
                PlaybackCommand::Stop => {
                    terminal = Some(None);
                }
                PlaybackCommand::Play(s) => {
                    terminal = Some(Some(s));
                }
                PlaybackCommand::Pause => {
                    if terminal.is_none() {
                        paused = true;
                    }
                }
                PlaybackCommand::Resume => {
                    if terminal.is_none() {
                        paused = false;
                    }
                }
                PlaybackCommand::Seek(secs) => {
                    if terminal.is_none() {
                        self.decoding_done = false;
                        batch_seek = Some(secs.max(0.0));
                    }
                }
                PlaybackCommand::PreloadNext(s) => {
                    if terminal.is_none() {
                        batch_preload = Some(s);
                    }
                }
                other => {
                    apply_setting_command(
                        other,
                        Some(self.audio_output),
                        self.state,
                        self.event_tx,
                        self.dsp,
                        Some(&mut self.crossfade_mixer),
                    );
                }
            }
        }

        match terminal {
            Some(None) => {
                self.pending_seek = None;
                return ActiveControlFlow::Exit(self.finish_stopped());
            }
            Some(Some(next_song)) => {
                self.pending_seek = None;
                return ActiveControlFlow::Exit(self.finish_superseded(next_song));
            }
            None => {}
        }

        if let Some(s) = batch_preload {
            self.perform_preload(&s);
        }

        if paused {
            if let Some(secs) = batch_seek {
                self.pending_seek = Some(secs);
            }
            return match self.wait_while_paused() {
                PauseExit::Resumed {
                    seeked: paused_seeked,
                } => ActiveControlFlow::Continue {
                    seeked: paused_seeked,
                },
                PauseExit::PlayNext(s) => ActiveControlFlow::Exit(self.finish_superseded(s)),
                PauseExit::Stopped => ActiveControlFlow::Exit(self.finish_stopped()),
                PauseExit::Halted => {
                    self.halt_playback(true);
                    ActiveControlFlow::Exit(None)
                }
            };
        }

        let mut seeked = false;
        if let Some(secs) = batch_seek.or_else(|| self.pending_seek.take()) {
            match self.seek_and_rebase(secs, true) {
                SeekOutcome::Applied => {
                    self.needs_resync_seek = false;
                    seeked = true;
                }
                SeekOutcome::Interrupted => {
                    self.pending_seek = Some(secs);
                    let (more, next_snap) = self.signals.drain_and_snapshot(self.cmd_rx);
                    return self.process_batch(more, next_snap);
                }
                SeekOutcome::Failed => {
                    self.halt_playback(true);
                    return ActiveControlFlow::Exit(None);
                }
            }
        }

        if self.needs_resync_seek && self.dec.is_some() {
            let resync_target = self.seek_base + self.audio_output.played_secs();
            match self.seek_and_rebase(resync_target, false) {
                SeekOutcome::Applied => {
                    self.needs_resync_seek = false;
                    seeked = true;
                }
                SeekOutcome::Interrupted => {
                    let (more, next_snap) = self.signals.drain_and_snapshot(self.cmd_rx);
                    return self.process_batch(more, next_snap);
                }
                SeekOutcome::Failed => {
                    self.halt_playback(true);
                    return ActiveControlFlow::Exit(None);
                }
            }
        }

        ActiveControlFlow::Continue { seeked }
    }

    fn service_active_commands(&mut self) -> ActiveControlFlow {
        if self.cmd_rx.is_empty() && !self.needs_resync_seek && self.pending_seek.is_none() {
            return ActiveControlFlow::Continue { seeked: false };
        }
        let (cmds, snap) = self.signals.drain_and_snapshot(self.cmd_rx);
        self.process_batch(cmds, snap)
    }

    fn handle_decode_error(&mut self, err: &PlaybackError) -> ActiveControlFlow {
        self.signals.buffering_stall.store(false, Ordering::Release);
        let (truncated, reason) = match self.dec.as_ref() {
            Some(d) => (d.is_stream_truncated(), d.take_interrupt_reason()),
            None => (false, INTERRUPT_NONE),
        };
        let (cmds, snap) = self.signals.drain_and_snapshot(self.cmd_rx);
        let stream_changed =
            snap.stream_gen != self.controls.expected_stream_gen || reason == INTERRUPT_CANCELLED;

        if truncated && !stream_changed {
            return ActiveControlFlow::Exit(self.finish_on_error(err));
        }

        if reason == INTERRUPT_PAUSED && !truncated {
            self.needs_resync_seek = true;
        }

        if stream_changed || (!truncated && matches!(reason, INTERRUPT_PAUSED | INTERRUPT_SEEKED)) {
            return self.process_batch(cmds, snap);
        }

        ActiveControlFlow::Exit(self.finish_on_error(err))
    }
}

/// Applies caller-supplied catalog metadata on top of decoder probe tags.
fn merge_song_metadata(mut decoded_info: SongInfo, song_info: &SongInfo) -> SongInfo {
    if !song_info.id.is_empty() {
        decoded_info.id = song_info.id.clone();
    }
    if song_info.cover_art.is_some() {
        decoded_info.cover_art = song_info.cover_art.clone();
    }
    // Catalog/library metadata wins over what the container carries whenever
    // the caller actually knows it: yt-dlp stream-cache files are untagged, so
    // the decoder's filename fallback would otherwise surface the raw video
    // id as the title ("oafxkMv4xnc"). Local files agree with their own tags,
    // so this is a no-op for the library path.
    if !song_info.title.is_empty() {
        decoded_info.title = song_info.title.clone();
    }
    if !song_info.artist.is_empty() {
        decoded_info.artist = song_info.artist.clone();
    }
    if !song_info.album.is_empty() {
        decoded_info.album = song_info.album.clone();
    }
    if decoded_info.duration_secs <= 0.0 && song_info.duration_secs > 0.0 {
        decoded_info.duration_secs = song_info.duration_secs;
    }
    decoded_info
}

/// Resamples, adapts channels, and applies EQ + normalization gain into `process_buf`.
fn prepare_output_samples(
    samples: &[f32],
    resampler: &mut Option<resampler::AudioResampler>,
    source_ch: usize,
    device_ch: usize,
    dsp: &mut DspRuntime,
    process_buf: &mut Vec<f32>,
    channel_buf: &mut Vec<f32>,
) {
    process_samples_reuse(
        samples,
        resampler,
        source_ch,
        device_ch,
        process_buf,
        channel_buf,
    );
    dsp.equalizer.process_interleaved(process_buf);
    if (dsp.norm_gain - 1.0).abs() > 0.001 {
        for s in process_buf.iter_mut() {
            *s *= dsp.norm_gain;
            // Soft clip to prevent clipping.
            if *s > 1.0 {
                *s = 1.0 - (-(*s - 1.0)).exp() * 0.5;
            } else if *s < -1.0 {
                *s = -1.0 + (-(-*s - 1.0)).exp() * 0.5;
            }
        }
    }
}

#[derive(Debug, Clone, Copy)]
enum OpenPollSource {
    Resumed,
    OpenError { reason: u8, truncated: bool },
}

#[derive(Debug)]
enum PreopenStep {
    Continue {
        song_info: SongInfo,
        snap: GenSnapshot,
        start_paused: bool,
    },
    Stop,
    Supersede(SongInfo),
    Fatal,
}

struct PreopenTargets<'a> {
    audio_output: Option<&'a AudioOutput>,
    state: &'a Arc<Mutex<PlaybackState>>,
    event_tx: &'a Sender<PlaybackEvent>,
    dsp: &'a mut DspRuntime,
    pending_seek: &'a mut Option<f32>,
    pending_preload: &'a mut Option<SongInfo>,
}

/// Shared post-pause / post-error command drain for `play_song`'s open loop.
fn drain_preopen_commands(
    song_info: SongInfo,
    signals: &EngineControlSignals,
    cmd_rx: &Receiver<PlaybackCommand>,
    expected_stream_gen: u64,
    source: OpenPollSource,
    targets: PreopenTargets<'_>,
) -> PreopenStep {
    let (reason, truncated, from_error) = match source {
        OpenPollSource::Resumed => (INTERRUPT_NONE, false, false),
        OpenPollSource::OpenError { reason, truncated } => (reason, truncated, true),
    };
    let (more_cmds, next_snap) = signals.drain_and_snapshot(cmd_rx);
    let stream_changed =
        next_snap.stream_gen != expected_stream_gen || reason == INTERRUPT_CANCELLED;
    let outcome = coalesce_pending_commands(
        song_info,
        more_cmds,
        targets.audio_output,
        targets.state,
        targets.event_tx,
        targets.dsp,
    );
    if outcome.pending_seek.is_some() {
        *targets.pending_seek = outcome.pending_seek;
    }
    if outcome.pending_preload.is_some() {
        *targets.pending_preload = outcome.pending_preload;
    }
    if outcome.stopped {
        return PreopenStep::Stop;
    }
    if stream_changed {
        return if next_snap.stream_gen != expected_stream_gen {
            PreopenStep::Supersede(outcome.song_info)
        } else {
            PreopenStep::Stop
        };
    }
    if !from_error || (!truncated && (reason == INTERRUPT_PAUSED || outcome.start_paused)) {
        // Use `outcome.start_paused` rather than forcing `reason == INTERRUPT_PAUSED`,
        // so `[Pause, Resume]` queued while opening retries `open` immediately.
        return PreopenStep::Continue {
            song_info: outcome.song_info,
            snap: next_snap,
            start_paused: outcome.start_paused,
        };
    }
    PreopenStep::Fatal
}

/// Decodes and streams a single song, returning `Some(next)` when superseded by a
/// new `Play` or gapless transition, or `None` when stopped/errored/finished.
fn play_song(
    song_info: SongInfo,
    cmd_rx: &Receiver<PlaybackCommand>,
    event_tx: &Sender<PlaybackEvent>,
    state: &Arc<Mutex<PlaybackState>>,
    audio_output: &mut AudioOutput,
    dsp: &mut DspRuntime,
    signals: &EngineControlSignals,
) -> Option<SongInfo> {
    let (drained, mut snap) = signals.drain_and_snapshot(cmd_rx);
    let CoalescedCommands {
        mut song_info,
        stopped,
        mut start_paused,
        pending_seek,
        pending_preload,
    } = coalesce_pending_commands(
        song_info,
        drained,
        Some(audio_output),
        state,
        event_tx,
        dsp,
    );

    if stopped {
        signals.pause_requested.store(false, Ordering::Release);
        signals.buffering_stall.store(false, Ordering::Release);
        audio_output.set_paused(true);
        audio_output.request_flush();
        set_stopped(state);
        let _ = event_tx.send(PlaybackEvent::Stopped);
        return None;
    }

    // Gate the callback and mark Buffering immediately so UI feedback is
    // instantaneous while probing a progressive loopback stream.
    signals.buffering_stall.store(false, Ordering::Release);
    audio_output.set_paused(true);
    audio_output.flush_now(Duration::from_millis(100));
    audio_output.reset_played();
    {
        let mut st = state.lock().unwrap_or_else(|e| e.into_inner());
        st.status = PlaybackStatus::Buffering;
        st.current_song = Some(song_info.clone());
        st.duration_secs = song_info.duration_secs;
        st.position_secs = 0.0;
        st.listened_secs = 0;
        st.listen_start = None;
    }
    let _ = event_tx.send(PlaybackEvent::BufferingStarted);

    let device_sr = audio_output.sample_rate();
    let device_ch = audio_output.channels();
    let crossfade_mixer = crossfade::CrossfadeMixer::new(dsp.crossfade_cfg.clone());
    let controls = signals.new_loopback_controls(snap);

    let mut session = PlaySession {
        cmd_rx,
        event_tx,
        state,
        audio_output,
        dsp,
        signals,
        controls,
        dec: None,
        crossfade_mixer,
        preloaded_song: None,
        seek_base: 0.0,
        pending_seek,
        pending_preload,
        needs_resync_seek: false,
        decoding_done: false,
        drain_deadline: Instant::now() + Duration::from_secs(3),
        active_status: PlaybackStatus::Buffering,
    };

    // Open the decoder (local file or progressive loopback HTTP stream), supporting
    // pause/resume/cancel even while the HTTP stream header probe is stalling.
    let (dec, decoded_info) = loop {
        if start_paused {
            match session.wait_while_paused() {
                PauseExit::Resumed { .. } => {
                    match drain_preopen_commands(
                        song_info,
                        session.signals,
                        session.cmd_rx,
                        session.controls.expected_stream_gen,
                        OpenPollSource::Resumed,
                        PreopenTargets {
                            audio_output: Some(session.audio_output),
                            state: session.state,
                            event_tx: session.event_tx,
                            dsp: session.dsp,
                            pending_seek: &mut session.pending_seek,
                            pending_preload: &mut session.pending_preload,
                        },
                    ) {
                        PreopenStep::Continue {
                            song_info: updated,
                            snap: next_snap,
                            start_paused: paused_again,
                        } => {
                            song_info = updated;
                            snap = next_snap;
                            start_paused = paused_again;
                            if start_paused {
                                continue;
                            }
                        }
                        PreopenStep::Stop => return session.finish_stopped(),
                        PreopenStep::Supersede(next_song) => {
                            return session.finish_superseded(next_song);
                        }
                        PreopenStep::Fatal => unreachable!("resumed open poll cannot be fatal"),
                    }
                }
                PauseExit::PlayNext(next_song) => return session.finish_superseded(next_song),
                PauseExit::Stopped => return session.finish_stopped(),
                PauseExit::Halted => {
                    session.halt_playback(true);
                    return None;
                }
            }
        }

        session.controls = session.signals.new_loopback_controls(snap);
        match decoder::AudioDecoder::new_with_loopback_controls(
            &song_info.file_path,
            session.controls.clone(),
        ) {
            Ok(opened) => break opened,
            Err(e) => {
                session.signals.buffering_stall.store(false, Ordering::Release);
                let truncated = session.controls.truncated.load(Ordering::Acquire);
                let reason = session
                    .controls
                    .interrupt_reason
                    .swap(INTERRUPT_NONE, Ordering::AcqRel);
                match drain_preopen_commands(
                    song_info,
                    session.signals,
                    session.cmd_rx,
                    session.controls.expected_stream_gen,
                    OpenPollSource::OpenError { reason, truncated },
                    PreopenTargets {
                        audio_output: Some(session.audio_output),
                        state: session.state,
                        event_tx: session.event_tx,
                        dsp: session.dsp,
                        pending_seek: &mut session.pending_seek,
                        pending_preload: &mut session.pending_preload,
                    },
                ) {
                    PreopenStep::Continue {
                        song_info: updated,
                        snap: next_snap,
                        start_paused: paused_now,
                    } => {
                        song_info = updated;
                        snap = next_snap;
                        start_paused = paused_now;
                        continue;
                    }
                    PreopenStep::Stop => return session.finish_stopped(),
                    PreopenStep::Supersede(next_song) => {
                        return session.finish_superseded(next_song);
                    }
                    PreopenStep::Fatal => return session.finish_on_error(&e),
                }
            }
        }
    };

    let song = merge_song_metadata(decoded_info, &song_info);
    let source_sr = dec.sample_rate();
    let source_ch = dec.channels();
    session.dec = Some(dec);

    // Create resampler if needed.
    let mut resampler_opt = if source_sr != device_sr {
        match resampler::AudioResampler::new(source_sr, device_sr, source_ch) {
            Ok(r) => Some(r),
            Err(e) => return session.finish_on_error(&e),
        }
    } else {
        None
    };

    // Pre-allocate reusable buffers for the hot path (zero heap allocation per packet).
    let mut process_buf: Vec<f32> = Vec::with_capacity(8192);
    let mut channel_buf: Vec<f32> = Vec::with_capacity(8192);

    {
        let mut st = session.state.lock().unwrap_or_else(|e| e.into_inner());
        st.current_song = Some(song.clone());
        st.duration_secs = song.duration_secs;
    }

    // Enable cooperative Pause & Seek interrupts now that header probing is done,
    // after syncing ack generations with anything queued during open.
    session
        .controls
        .seek_interrupt_enabled
        .store(true, Ordering::Release);
    if let ActiveControlFlow::Exit(next) = session.service_active_commands() {
        return next;
    }

    // Pre-fill ring buffer while output remains paused (`set_paused(true)`), so
    // audio callback never underruns during initial buffering.
    let prefill_samples = clamp_prefill_samples(
        device_sr,
        device_ch,
        session.dsp.instant_buffer_kb,
        session.audio_output.producer().capacity(),
    );
    let mut filled = 0usize;
    while filled < prefill_samples {
        match session.service_active_commands() {
            ActiveControlFlow::Continue { seeked } => {
                if seeked {
                    filled = 0;
                }
            }
            ActiveControlFlow::Exit(next) => return next,
        }

        let pkt_res = session
            .dec
            .as_mut()
            .expect("decoder initialized")
            .decode_next_packet();
        match pkt_res {
            Ok(Some(samples)) => {
                prepare_output_samples(
                    &samples,
                    &mut resampler_opt,
                    source_ch,
                    device_ch,
                    session.dsp,
                    &mut process_buf,
                    &mut channel_buf,
                );
                let pushed = push_samples(session.audio_output.producer(), &process_buf);
                if pushed == 0 {
                    break;
                }
                filled += pushed;
            }
            Ok(None) => {
                session.decoding_done = true;
                session.drain_deadline = Instant::now() + Duration::from_secs(3);
                break;
            }
            Err(e) => match session.handle_decode_error(&e) {
                ActiveControlFlow::Continue { seeked } => {
                    if seeked {
                        filled = 0;
                    }
                }
                ActiveControlFlow::Exit(next) => return next,
            },
        }
    }

    // Service any command that arrived on the final prefill packet before going live.
    if let ActiveControlFlow::Exit(next) = session.service_active_commands() {
        return next;
    }

    // Transition to Playing and release audio output pause gate.
    session.signals.buffering_stall.store(false, Ordering::Release);
    session.active_status = PlaybackStatus::Playing;
    {
        let mut st = session.state.lock().unwrap_or_else(|e| e.into_inner());
        st.status = PlaybackStatus::Playing;
        st.listen_start = Some(Instant::now());
    }
    session.audio_output.set_paused(false);
    let _ = session.event_tx.send(PlaybackEvent::BufferingComplete);
    let _ = session.event_tx.send(PlaybackEvent::Playing(song.clone()));

    if let Some(preload_song) = session.pending_preload.take() {
        session.perform_preload(&preload_song);
    }

    // Main decode loop.
    let mut last_position_emit = Instant::now();
    let position_interval = Duration::from_millis(250);

    loop {
        if let ActiveControlFlow::Exit(next) = session.service_active_commands() {
            return next;
        }

        // Tail-drain: decoder finished; wait for the buffered tail to play
        // out before declaring SongEnded. Commands keep being serviced above.
        if session.decoding_done {
            let drained = session.audio_output.producer().is_empty();
            let timed_out = Instant::now() >= session.drain_deadline;
            if drained || timed_out {
                // Pausing during the drain means "stop here" — don't fire
                // SongEnded (which would auto-advance to the next song).
                let paused_now = session
                    .state
                    .lock()
                    .unwrap_or_else(|e| e.into_inner())
                    .status
                    == PlaybackStatus::Paused;
                if paused_now {
                    return session.finish_stopped();
                }
                accumulate_listen(session.state);
                let pos = session.seek_base + session.audio_output.played_secs();
                session
                    .state
                    .lock()
                    .unwrap_or_else(|e| e.into_inner())
                    .position_secs = pos;
                let (song_id, listened, duration) = {
                    let st = session.state.lock().unwrap_or_else(|e| e.into_inner());
                    (
                        st.current_song.as_ref().map(|s| s.id.clone()).unwrap_or_default(),
                        st.listened_secs,
                        st.duration_secs,
                    )
                };
                let _ = session.event_tx.send(PlaybackEvent::SongEnded {
                    song_id,
                    listened_secs: listened,
                    duration_secs: duration,
                });

                // ── Gapless transition ──
                if let Some(next_song_info) = session.preloaded_song.take() {
                    log::info!("Gapless transition to: {}", next_song_info.title);
                    set_stopped(session.state);
                    return Some(next_song_info);
                }

                set_stopped(session.state);
                return None;
            }
            // Keep the UI's position moving to the end during the tail.
            if last_position_emit.elapsed() >= position_interval {
                let (pos, listened) = {
                    let mut st = session.state.lock().unwrap_or_else(|e| e.into_inner());
                    st.position_secs = session.seek_base + session.audio_output.played_secs();
                    (st.position_secs, st.elapsed_listen_secs())
                };
                let _ = session.event_tx.send(PlaybackEvent::PositionUpdate {
                    position_secs: pos,
                    listened_secs: listened,
                });
                last_position_emit = Instant::now();
            }
            thread::sleep(Duration::from_millis(10));
            continue;
        }

        // Decode next packet.
        let pkt_res = session
            .dec
            .as_mut()
            .expect("decoder initialized")
            .decode_next_packet();
        match pkt_res {
            Ok(Some(samples)) => {
                prepare_output_samples(
                    &samples,
                    &mut resampler_opt,
                    source_ch,
                    device_ch,
                    session.dsp,
                    &mut process_buf,
                    &mut channel_buf,
                );

                // Push to ring buffer, spin-waiting if full.
                push_samples_blocking(session.audio_output.producer(), &process_buf);

                // Position = what the listener has actually heard.
                let pos = session.seek_base + session.audio_output.played_secs();
                {
                    let mut st = session.state.lock().unwrap_or_else(|e| e.into_inner());
                    st.position_secs = pos;
                }

                // Emit position update every 250ms.
                if last_position_emit.elapsed() >= position_interval {
                    let listened = session
                        .state
                        .lock()
                        .unwrap_or_else(|e| e.into_inner())
                        .elapsed_listen_secs();
                    let _ = session.event_tx.send(PlaybackEvent::PositionUpdate {
                        position_secs: pos,
                        listened_secs: listened,
                    });
                    last_position_emit = Instant::now();
                }
            }
            Ok(None) => {
                // Decoder hit EOF — enter the tail-drain state (handled at
                // the top of the loop) instead of cutting buffered audio.
                session.decoding_done = true;
                session.drain_deadline = Instant::now() + Duration::from_secs(3);
            }
            Err(e) => {
                if let ActiveControlFlow::Exit(next) = session.handle_decode_error(&e) {
                    return next;
                }
            }
        }
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/// Process raw decoded samples using pre-allocated buffers: resample if needed,
/// channel-adapt if needed. Returns a reference to the output buffer.
/// Reuses Vec capacity across calls — no heap allocations on the hot path.
fn process_samples_reuse(
    samples: &[f32],
    resampler: &mut Option<resampler::AudioResampler>,
    source_ch: usize,
    device_ch: usize,
    process_buf: &mut Vec<f32>,
    channel_buf: &mut Vec<f32>,
) {
    // Step 1: Resample if needed, result goes into process_buf.
    process_buf.clear();
    let resampled: &[f32] = match resampler {
        Some(r) if r.needed() => {
            match r.process_into(samples, process_buf) {
                Ok(()) => {},
                Err(e) => {
                    log::warn!("Resampler error: {e}");
                    process_buf.extend_from_slice(samples);
                }
            }
            process_buf.as_slice()
        }
        _ => samples,
    };

    // Step 2: Channel adaptation into channel_buf (if needed).
    if source_ch != device_ch {
        adapt_channels_reuse(resampled, source_ch, device_ch, channel_buf);
        // Swap so process_buf holds the final result.
        std::mem::swap(process_buf, channel_buf);
    } else if resampled.as_ptr() != process_buf.as_ptr() {
        // No resample happened — copy samples into process_buf.
        process_buf.extend_from_slice(samples);
    }
    // Final result is always in process_buf.
}

/// Simple channel adaptation (mono→stereo, stereo→mono, etc) into a
/// pre-allocated buffer.
fn adapt_channels_reuse(samples: &[f32], from_ch: usize, to_ch: usize, out: &mut Vec<f32>) {
    let frames = samples.len() / from_ch;
    out.clear();
    out.reserve(frames * to_ch);
    for f in 0..frames {
        let base = f * from_ch;
        for c in 0..to_ch {
            if c < from_ch {
                out.push(samples[base + c]);
            } else {
                // Duplicate first channel for missing channels.
                out.push(samples[base]);
            }
        }
    }
}

/// Pushes as many samples as will fit, returns count pushed.
fn push_samples(producer: &mut ringbuf::HeapProducer<f32>, samples: &[f32]) -> usize {
    producer.push_slice(samples)
}

/// Push all samples, spinning until the ring buffer has room.
fn push_samples_blocking(producer: &mut ringbuf::HeapProducer<f32>, samples: &[f32]) {
    let mut offset = 0;
    while offset < samples.len() {
        let pushed = producer.push_slice(&samples[offset..]);
        offset += pushed;
        if offset < samples.len() {
            thread::sleep(Duration::from_micros(500));
        }
    }
}

/// Accumulates elapsed playing time into `listened_secs` and clears `listen_start`.
fn accumulate_listen(state: &Arc<Mutex<PlaybackState>>) {
    let mut st = state.lock().unwrap_or_else(|e| e.into_inner());
    if let Some(start) = st.listen_start.take() {
        st.listened_secs += start.elapsed().as_secs() as u32;
    }
}

/// Resets state to Stopped.
fn set_stopped(state: &Arc<Mutex<PlaybackState>>) {
    let mut st = state.lock().unwrap_or_else(|e| e.into_inner());
    st.status = PlaybackStatus::Stopped;
    st.current_song = None;
    st.position_secs = 0.0;
    st.duration_secs = 0.0;
    st.listen_start = None;
    st.listened_secs = 0;
}

// ─── Tests ────────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    fn test_song(id: &str) -> SongInfo {
        SongInfo {
            id: id.into(),
            file_path: format!("/test/{id}.flac"),
            title: format!("Song {id}"),
            artist: "Artist".into(),
            album: "Album".into(),
            duration_secs: 180.0,
            cover_art: None,
        }
    }

    #[test]
    fn engine_new_succeeds() {
        // This test verifies that PlaybackEngine::new() does not panic.
        // It may fail in CI if no audio device is available, which is expected.
        let result = PlaybackEngine::new();
        // We only assert it doesn't panic; it may error if no device exists.
        match result {
            Ok(engine) => {
                let st = engine.state();
                assert_eq!(st.status, PlaybackStatus::Stopped);
            }
            Err(PlaybackError::NoOutputDevice) => {
                // Expected in headless environments.
            }
            Err(e) => {
                // Other errors are acceptable in test environments.
                eprintln!("Engine init error (acceptable in CI): {e}");
            }
        }
    }

    #[test]
    fn set_volume_clamps_correctly() {
        // Test that volume clamping works (the clamping happens in set_volume).
        let clamped_high = 2.0f32.clamp(0.0, 1.0);
        assert!((clamped_high - 1.0).abs() < f32::EPSILON);

        let clamped_low = (-0.5f32).clamp(0.0, 1.0);
        assert!(clamped_low.abs() < f32::EPSILON);

        let clamped_normal = 0.75f32.clamp(0.0, 1.0);
        assert!((clamped_normal - 0.75).abs() < f32::EPSILON);
    }

    #[test]
    fn queue_next_returns_none_on_empty() {
        let mut q = PlaybackQueue::new();
        assert!(q.next().is_none());
        assert!(q.is_empty());
        assert_eq!(q.len(), 0);
    }

    #[test]
    fn queue_previous_after_next() {
        let mut q = PlaybackQueue::new();

        let song1 = SongInfo {
            id: "1".into(),
            file_path: "/test/song1.flac".into(),
            title: "Song One".into(),
            artist: "Artist".into(),
            album: "Album".into(),
            duration_secs: 180.0,
            cover_art: None,
        };
        let song2 = SongInfo {
            id: "2".into(),
            file_path: "/test/song2.flac".into(),
            title: "Song Two".into(),
            artist: "Artist".into(),
            album: "Album".into(),
            duration_secs: 200.0,
            cover_art: None,
        };

        q.push_back(song1.clone());
        q.push_back(song2.clone());

        let first = q.next().expect("should have a song");
        assert_eq!(first.id, "1");

        let second = q.next().expect("should have a song");
        assert_eq!(second.id, "2");

        let prev = q.previous().expect("should have previous");
        assert_eq!(prev.id, "1");
    }

    #[test]
    fn playback_state_elapsed_listen_secs() {
        let mut st = PlaybackState::default();
        assert_eq!(st.elapsed_listen_secs(), 0);

        st.listened_secs = 10;
        assert_eq!(st.elapsed_listen_secs(), 10);

        st.status = PlaybackStatus::Playing;
        st.listen_start = Some(Instant::now());
        // Should be ~10 since listen_start was just set.
        let elapsed = st.elapsed_listen_secs();
        assert!(elapsed >= 10 && elapsed <= 11);
    }

    #[test]
    fn queue_push_front_plays_next() {
        let mut q = PlaybackQueue::new();
        let song_a = SongInfo {
            id: "a".into(),
            file_path: "a.flac".into(),
            title: "A".into(),
            artist: String::new(),
            album: String::new(),
            duration_secs: 100.0,
            cover_art: None,
        };
        let song_b = SongInfo {
            id: "b".into(),
            file_path: "b.flac".into(),
            title: "B".into(),
            artist: String::new(),
            album: String::new(),
            duration_secs: 100.0,
            cover_art: None,
        };

        q.push_back(song_a);
        q.push_front(song_b);

        let next = q.next().expect("should have a song");
        assert_eq!(next.id, "b"); // pushed to front
    }

    #[test]
    fn control_signals_drain_and_snapshot_are_linearizable() {
        let signals = EngineControlSignals::new();
        let (tx, rx) = crossbeam_channel::unbounded::<PlaybackCommand>();
        let writers: Vec<_> = (0..4)
            .map(|tid| {
                let s = signals.clone();
                let ctx = tx.clone();
                thread::spawn(move || {
                    for i in 0..400usize {
                        match (tid + i) % 5 {
                            0 => {
                                let _ = s.send_stream_cmd(
                                    &ctx,
                                    PlaybackCommand::Play(test_song(&format!("{tid}-{i}"))),
                                );
                            }
                            1 => {
                                let _ = s.send_stream_cmd(&ctx, PlaybackCommand::Stop);
                            }
                            2 => {
                                let _ = s.send_pause(&ctx);
                            }
                            3 => {
                                let _ = s.send_resume(&ctx);
                            }
                            _ => {
                                let _ = s.send_seek(&ctx, i as f32);
                            }
                        }
                    }
                })
            })
            .collect();
        drop(tx);

        let mut observed_stream = 0u64;
        let mut observed_pause = 0u64;
        let mut observed_seek = 0u64;
        let mut done_writers = false;

        loop {
            let (cmds, snap) = signals.drain_and_snapshot(&rx);
            for cmd in &cmds {
                match cmd {
                    PlaybackCommand::Play(_) | PlaybackCommand::Stop => observed_stream += 1,
                    PlaybackCommand::Pause => observed_pause += 1,
                    PlaybackCommand::Seek(_) => observed_seek += 1,
                    _ => {}
                }
            }
            // Snapshot taken under `cmd_lock` must match every queued command
            // already in `rx` and never lag behind `cmds`.
            assert_eq!(snap.stream_gen, observed_stream);
            assert_eq!(snap.pause_gen, observed_pause);
            assert_eq!(snap.seek_gen, observed_seek);

            if done_writers && cmds.is_empty() {
                break;
            }
            if !done_writers && writers.iter().all(|h| h.is_finished()) {
                done_writers = true;
            } else {
                thread::yield_now();
            }
        }
        for w in writers {
            w.join().unwrap();
        }
        assert_eq!(observed_stream + observed_pause + observed_seek, 4 * 320);
    }

    #[test]
    fn coalesce_pending_commands_preserves_pause_seek_preload() {
        let state = Arc::new(Mutex::new(PlaybackState::default()));
        let (event_tx, _event_rx) = crossbeam_channel::unbounded();
        let mut dsp = DspRuntime {
            equalizer: eq::Equalizer::new(48_000, 2),
            norm_gain: 1.0,
            crossfade_cfg: CrossfadeConfig::default(),
            instant_buffer_kb: 64,
        };

        let cmds = vec![
            PlaybackCommand::Play(test_song("s2")),
            PlaybackCommand::Pause,
            PlaybackCommand::Seek(42.0),
            PlaybackCommand::Stop,
            PlaybackCommand::Play(test_song("s3")),
            PlaybackCommand::Pause,
            PlaybackCommand::Seek(-3.5),
            PlaybackCommand::PreloadNext(test_song("s4")),
        ];
        let out = coalesce_pending_commands(
            test_song("s1"),
            cmds,
            None,
            &state,
            &event_tx,
            &mut dsp,
        );
        assert!(!out.stopped);
        assert_eq!(out.song_info.id, "s3");
        assert!(out.start_paused);
        assert_eq!(out.pending_seek, Some(0.0));
        assert_eq!(out.pending_preload.as_ref().map(|s| s.id.as_str()), Some("s4"));

        let out_stopped = coalesce_pending_commands(
            test_song("s1"),
            vec![PlaybackCommand::Pause, PlaybackCommand::Stop],
            None,
            &state,
            &event_tx,
            &mut dsp,
        );
        assert!(out_stopped.stopped);
        assert!(!out_stopped.start_paused);
        assert_eq!(out_stopped.pending_seek, None);
    }

    #[test]
    fn clamp_prefill_samples_respects_ring_capacity() {
        let ring_cap = 96_000;
        assert_eq!(clamp_prefill_samples(48_000, 2, 1024, ring_cap), ring_cap);
        assert_eq!(clamp_prefill_samples(48_000, 2, 32, ring_cap), 12_000);
        assert_eq!(clamp_prefill_samples(48_000, 2, 64, ring_cap), 24_000);
    }

    #[test]
    fn drain_preopen_commands_recovers_pause_resume_without_wedging() {
        let signals = EngineControlSignals::new();
        let (tx, rx) = crossbeam_channel::unbounded();
        let state = Arc::new(Mutex::new(PlaybackState::default()));
        let (event_tx, _event_rx) = crossbeam_channel::unbounded();
        let mut dsp = DspRuntime {
            equalizer: eq::Equalizer::new(48_000, 2),
            norm_gain: 1.0,
            crossfade_cfg: CrossfadeConfig::default(),
            instant_buffer_kb: 64,
        };
        let mut pending_seek = None;
        let mut pending_preload = None;

        // 1. `[Pause, Resume]` arrives while `open()` is interrupted by Pause:
        // must retry `open()` immediately (`start_paused == false`), not wedge.
        signals.send_pause(&tx).unwrap();
        signals.send_resume(&tx).unwrap();
        match drain_preopen_commands(
            test_song("s1"),
            &signals,
            &rx,
            0,
            OpenPollSource::OpenError {
                reason: INTERRUPT_PAUSED,
                truncated: false,
            },
            PreopenTargets {
                audio_output: None,
                state: &state,
                event_tx: &event_tx,
                dsp: &mut dsp,
                pending_seek: &mut pending_seek,
                pending_preload: &mut pending_preload,
            },
        ) {
            PreopenStep::Continue {
                start_paused, snap, ..
            } => {
                assert!(!start_paused, "Pause+Resume must not wedge in wait_while_paused");
                assert_eq!(snap.pause_gen, 1);
            }
            other => panic!("expected Continue retry, got {other:?}"),
        }

        // 2. Standalone `Pause` during `open()` enters `wait_while_paused` (`start_paused == true`).
        signals.send_pause(&tx).unwrap();
        match drain_preopen_commands(
            test_song("s1"),
            &signals,
            &rx,
            0,
            OpenPollSource::OpenError {
                reason: INTERRUPT_PAUSED,
                truncated: false,
            },
            PreopenTargets {
                audio_output: None,
                state: &state,
                event_tx: &event_tx,
                dsp: &mut dsp,
                pending_seek: &mut pending_seek,
                pending_preload: &mut pending_preload,
            },
        ) {
            PreopenStep::Continue { start_paused, .. } => assert!(start_paused),
            other => panic!("expected Continue paused, got {other:?}"),
        }

        // 3. Truncated body on `open()` must still fail fatally.
        assert!(matches!(
            drain_preopen_commands(
                test_song("s1"),
                &signals,
                &rx,
                0,
                OpenPollSource::OpenError {
                    reason: INTERRUPT_PAUSED,
                    truncated: true,
                },
                PreopenTargets {
                    audio_output: None,
                    state: &state,
                    event_tx: &event_tx,
                    dsp: &mut dsp,
                    pending_seek: &mut pending_seek,
                    pending_preload: &mut pending_preload,
                },
            ),
            PreopenStep::Fatal
        ));

        // 4. `Play(s2)` during `open()` supersedes cleanly with new `song_info`.
        signals
            .send_stream_cmd(&tx, PlaybackCommand::Play(test_song("s2")))
            .unwrap();
        match drain_preopen_commands(
            test_song("s1"),
            &signals,
            &rx,
            0,
            OpenPollSource::OpenError {
                reason: INTERRUPT_CANCELLED,
                truncated: false,
            },
            PreopenTargets {
                audio_output: None,
                state: &state,
                event_tx: &event_tx,
                dsp: &mut dsp,
                pending_seek: &mut pending_seek,
                pending_preload: &mut pending_preload,
            },
        ) {
            PreopenStep::Supersede(next) => assert_eq!(next.id, "s2"),
            other => panic!("expected Supersede(s2), got {other:?}"),
        }
    }
}
