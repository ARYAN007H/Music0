//! Symphonia-based audio decoder pipeline.
//!
//! Opens audio files, extracts metadata + cover art, and produces
//! interleaved f32 PCM samples one packet at a time.

use crate::error::PlaybackError;
use crate::http_source::{LoopbackControls, LoopbackHttpMediaSource, INTERRUPT_NONE};
use crate::state::SongInfo;

use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::{DecoderOptions, CODEC_TYPE_NULL};
use symphonia::core::formats::{FormatOptions, FormatReader, SeekMode, SeekTo};
use symphonia::core::io::{MediaSourceStream, MediaSourceStreamOptions};
use symphonia::core::meta::{MetadataOptions, StandardTagKey};
use symphonia::core::probe::Hint;
use symphonia::core::units::Time;

use std::fs::File;
use std::sync::atomic::{AtomicBool, AtomicU64, AtomicU8, Ordering};
use std::sync::Arc;

/// A streaming audio decoder backed by Symphonia.
///
/// Decodes one packet at a time — never loads the entire file into memory.
pub struct AudioDecoder {
    format_reader: Box<dyn FormatReader>,
    decoder: Box<dyn symphonia::core::codecs::Decoder>,
    track_id: u32,
    sample_rate: u32,
    channels: usize,
    total_frames: Option<u64>,
    stream_truncated: Arc<AtomicBool>,
    interrupt_reason: Arc<AtomicU8>,
}

impl AudioDecoder {
    /// Opens an audio file (or local loopback HTTP stream URL) and returns a
    /// ready-to-decode `AudioDecoder` along with the extracted `SongInfo`.
    ///
    /// # Errors
    ///
    /// Returns `PlaybackError::DecodeFailed` if the source cannot be opened,
    /// probed, or if no supported audio track is found.
    pub fn new(file_path: &str) -> Result<(Self, SongInfo), PlaybackError> {
        Self::new_with_controls(
            file_path,
            Arc::new(AtomicU64::new(0)),
            0,
            Arc::new(AtomicBool::new(false)),
        )
    }

    /// Opens a local file or loopback HTTP stream with cooperative cancellation
    /// (`stream_gen` vs `expected_gen`) and live mid-stream buffering telemetry.
    pub fn new_with_controls(
        file_path: &str,
        stream_gen: Arc<AtomicU64>,
        expected_gen: u64,
        buffering_stall: Arc<AtomicBool>,
    ) -> Result<(Self, SongInfo), PlaybackError> {
        Self::new_with_loopback_controls(
            file_path,
            LoopbackControls::basic(stream_gen, expected_gen, buffering_stall),
        )
    }

    /// Opens a local file or loopback HTTP stream with full cooperative
    /// cancellation, pause/seek interrupt, buffering, and truncation controls.
    pub fn new_with_loopback_controls(
        file_path: &str,
        controls: LoopbackControls,
    ) -> Result<(Self, SongInfo), PlaybackError> {
        let mut hint = Hint::new();
        let stream_truncated = Arc::clone(&controls.truncated);
        let interrupt_reason = Arc::clone(&controls.interrupt_reason);
        let mss = if LoopbackHttpMediaSource::is_loopback_url(file_path) {
            let src = LoopbackHttpMediaSource::open_with_controls(file_path, controls).map_err(
                |e| PlaybackError::DecodeFailed {
                    path: file_path.to_string(),
                    reason: format!("cannot open loopback stream: {e}"),
                },
            )?;
            if let Some(mime) = src.content_type() {
                hint.mime_type(mime);
            }
            if let Some(ext) = src.extension_hint() {
                hint.with_extension(ext);
            }
            // Symphonia requires `buffer_len` to be a power of 2 strictly > 32 KiB
            // (its MAX_BLOCK_LEN), while `MediaSourceStream` starts its first
            // read at MIN_BLOCK_LEN = 1 KiB — so 64 KiB probes immediately once
            // stream_server's 16 KiB INITIAL_READY_BYTES arrive.
            MediaSourceStream::new(
                Box::new(src),
                MediaSourceStreamOptions {
                    buffer_len: 64 * 1024,
                },
            )
        } else {
            let file = File::open(file_path).map_err(|e| PlaybackError::DecodeFailed {
                path: file_path.to_string(),
                reason: format!("cannot open file: {e}"),
            })?;
            if let Some(ext) = std::path::Path::new(file_path)
                .extension()
                .and_then(|e| e.to_str())
            {
                hint.with_extension(ext);
            }
            MediaSourceStream::new(Box::new(file), Default::default())
        };

        let format_opts = FormatOptions {
            enable_gapless: true,
            ..Default::default()
        };
        let metadata_opts = MetadataOptions::default();

        let probed = symphonia::default::get_probe()
            .format(&hint, mss, &format_opts, &metadata_opts)
            .map_err(|e| PlaybackError::DecodeFailed {
                path: file_path.to_string(),
                reason: format!("probe failed: {e}"),
            })?;

        let mut format_reader = probed.format;

        // Select the first audio track that has a supported codec.
        let track = format_reader
            .tracks()
            .iter()
            .find(|t| t.codec_params.codec != CODEC_TYPE_NULL)
            .ok_or_else(|| PlaybackError::DecodeFailed {
                path: file_path.to_string(),
                reason: "no supported audio track found".into(),
            })?;

        let track_id = track.id;

        let sample_rate = track
            .codec_params
            .sample_rate
            .ok_or_else(|| PlaybackError::DecodeFailed {
                path: file_path.to_string(),
                reason: "sample rate unknown".into(),
            })?;

        let channels = track
            .codec_params
            .channels
            .map(|ch| ch.count())
            .unwrap_or(2);

        let total_frames = track.codec_params.n_frames;

        let duration_secs = match total_frames {
            Some(n) if sample_rate > 0 => n as f32 / sample_rate as f32,
            _ => {
                // Fallback: try time_base * n_frames
                track
                    .codec_params
                    .time_base
                    .and_then(|tb| {
                        total_frames.map(|n| {
                            let t = tb.calc_time(n);
                            t.seconds as f32 + t.frac as f32
                        })
                    })
                    .unwrap_or(0.0)
            }
        };

        let decoder_opts = DecoderOptions::default();
        let decoder = symphonia::default::get_codecs()
            .make(&track.codec_params, &decoder_opts)
            .map_err(|e| PlaybackError::DecodeFailed {
                path: file_path.to_string(),
                reason: format!("codec init failed: {e}"),
            })?;

        // Extract metadata.
        let mut title = String::new();
        let mut artist = String::new();
        let mut album = String::new();
        let mut cover_art: Option<Vec<u8>> = None;

        // Check metadata from probe result (container-level).
        if let Some(md) = format_reader.metadata().current() {
            Self::extract_tags(md, &mut title, &mut artist, &mut album);
            Self::extract_visuals(md, &mut cover_art);
        }

        // Fallback: filename as title.
        if title.is_empty() {
            title = std::path::Path::new(file_path)
                .file_stem()
                .and_then(|s| s.to_str())
                .unwrap_or("Unknown")
                .to_string();
        }

        let song_info = SongInfo {
            id: String::new(), // Caller should set this from musico_recommender
            file_path: file_path.to_string(),
            title,
            artist,
            album,
            duration_secs,
            cover_art,
        };

        let dec = Self {
            format_reader,
            decoder,
            track_id,
            sample_rate,
            channels,
            total_frames,
            stream_truncated,
            interrupt_reason,
        };

        Ok((dec, song_info))
    }

    /// Returns `true` if the backing loopback HTTP stream closed prematurely
    /// before delivering its declared `Content-Length`.
    pub fn is_stream_truncated(&self) -> bool {
        self.stream_truncated.load(Ordering::Acquire)
    }

    /// Reads and clears the atomic loopback interrupt reason code.
    pub fn take_interrupt_reason(&self) -> u8 {
        self.interrupt_reason.swap(INTERRUPT_NONE, Ordering::AcqRel)
    }

    /// Decodes the next packet and returns interleaved f32 samples.
    ///
    /// Returns `Ok(None)` when the stream is exhausted (song ended).
    /// Never decodes the entire file at once — one packet per call.
    pub fn decode_next_packet(&mut self) -> Result<Option<Vec<f32>>, PlaybackError> {
        loop {
            let packet = match self.format_reader.next_packet() {
                Ok(p) => p,
                Err(symphonia::core::errors::Error::IoError(ref e))
                    if e.kind() == std::io::ErrorKind::UnexpectedEof
                        && !self.is_stream_truncated() =>
                {
                    return Ok(None); // Clean end of stream
                }
                Err(symphonia::core::errors::Error::ResetRequired) => {
                    // Seek or format change: reset the decoder.
                    self.decoder.reset();
                    continue;
                }
                Err(e) => {
                    return Err(PlaybackError::DecodeFailed {
                        path: String::new(),
                        reason: format!("packet read error: {e}"),
                    });
                }
            };

            // Skip packets from other tracks.
            if packet.track_id() != self.track_id {
                continue;
            }

            match self.decoder.decode(&packet) {
                Ok(decoded) => {
                    let spec = *decoded.spec();
                    let num_frames = decoded.capacity();

                    let mut sample_buf = SampleBuffer::<f32>::new(num_frames as u64, spec);
                    sample_buf.copy_interleaved_ref(decoded);

                    return Ok(Some(sample_buf.samples().to_vec()));
                }
                Err(symphonia::core::errors::Error::DecodeError(msg)) => {
                    // Non-fatal: skip corrupted packet.
                    log::warn!("decode error (skipping packet): {msg}");
                    continue;
                }
                Err(e) => {
                    return Err(PlaybackError::DecodeFailed {
                        path: String::new(),
                        reason: format!("decode error: {e}"),
                    });
                }
            }
        }
    }

    /// Seeks to the given position in seconds.
    ///
    /// Uses accurate seeking for precise position.
    pub fn seek_to(&mut self, secs: f32) -> Result<(), PlaybackError> {
        let secs = secs.max(0.0);
        let seek_to = SeekTo::Time {
            time: Time::new(secs as u64, (secs.fract()) as f64),
            track_id: Some(self.track_id),
        };

        self.format_reader
            .seek(SeekMode::Accurate, seek_to)
            .map_err(|e| PlaybackError::DecodeFailed {
                path: String::new(),
                reason: format!("seek failed: {e}"),
            })?;

        // Reset decoder state after seek.
        self.decoder.reset();

        Ok(())
    }

    /// Returns the source audio sample rate in Hz.
    pub fn sample_rate(&self) -> u32 {
        self.sample_rate
    }

    /// Returns the number of audio channels.
    pub fn channels(&self) -> usize {
        self.channels
    }

    /// Returns the total number of frames, if known.
    pub fn total_frames(&self) -> Option<u64> {
        self.total_frames
    }

    // ── private helpers ──────────────────────────────────────────────

    fn extract_tags(
        md: &symphonia::core::meta::MetadataRevision,
        title: &mut String,
        artist: &mut String,
        album: &mut String,
    ) {
        for tag in md.tags() {
            if let Some(key) = tag.std_key {
                match key {
                    StandardTagKey::TrackTitle => {
                        *title = tag.value.to_string();
                    }
                    StandardTagKey::Artist | StandardTagKey::AlbumArtist => {
                        if artist.is_empty() {
                            *artist = tag.value.to_string();
                        }
                    }
                    StandardTagKey::Album => {
                        *album = tag.value.to_string();
                    }
                    _ => {}
                }
            }
        }
    }

    fn extract_visuals(
        md: &symphonia::core::meta::MetadataRevision,
        cover_art: &mut Option<Vec<u8>>,
    ) {
        // Prefer front cover, but accept anything (first visual wins).
        // Was a for+break loop — clippy ≥1.99 denies it as never_loop.
        if let Some(visual) = md.visuals().first() {
            *cover_art = Some(visual.data.to_vec());
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::{Read, Write};
    use std::net::TcpListener;

    fn make_pcm_wav(pcm_bytes: usize) -> Vec<u8> {
        let total_data = pcm_bytes as u32;
        let riff_size = 36u32 + total_data;
        let mut out = Vec::with_capacity(44 + pcm_bytes);
        out.extend_from_slice(b"RIFF");
        out.extend_from_slice(&riff_size.to_le_bytes());
        out.extend_from_slice(b"WAVEfmt ");
        out.extend_from_slice(&16u32.to_le_bytes());
        out.extend_from_slice(&1u16.to_le_bytes()); // PCM
        out.extend_from_slice(&1u16.to_le_bytes()); // mono
        out.extend_from_slice(&44100u32.to_le_bytes());
        out.extend_from_slice(&88200u32.to_le_bytes());
        out.extend_from_slice(&2u16.to_le_bytes());
        out.extend_from_slice(&16u16.to_le_bytes());
        out.extend_from_slice(b"data");
        out.extend_from_slice(&total_data.to_le_bytes());
        out.resize(44 + pcm_bytes, 0);
        out
    }

    #[test]
    fn loopback_wav_complete_returns_ok_none_while_truncated_errors() {
        let full_wav = make_pcm_wav(64 * 1024);
        let full_len = full_wav.len();
        let half_wav = full_wav[..24 * 1024].to_vec();

        // 1) Complete WAV stream hits Ok(None) cleanly.
        let listener_ok = TcpListener::bind("127.0.0.1:0").expect("bind complete srv");
        let port_ok = listener_ok.local_addr().unwrap().port();
        let wav_srv = full_wav.clone();
        let srv_ok = std::thread::spawn(move || {
            let (mut sock, _) = listener_ok.accept().expect("accept");
            let mut req = [0u8; 512];
            let _ = sock.read(&mut req);
            let hdr = format!(
                "HTTP/1.1 200 OK\r\nContent-Length: {full_len}\r\nContent-Type: audio/wav\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n"
            );
            sock.write_all(hdr.as_bytes()).unwrap();
            sock.write_all(&wav_srv).unwrap();
            sock.flush().unwrap();
        });

        let url_ok = format!("http://127.0.0.1:{port_ok}/stream/wavok");
        let (mut dec_ok, _) = AudioDecoder::new_with_loopback_controls(
            &url_ok,
            LoopbackControls::basic(
                Arc::new(AtomicU64::new(1)),
                1,
                Arc::new(AtomicBool::new(false)),
            ),
        )
        .expect("open full wav");
        let mut packets_ok = 0usize;
        while let Some(_) = dec_ok.decode_next_packet().expect("complete wav decodes cleanly") {
            packets_ok += 1;
        }
        assert!(packets_ok > 0);
        assert!(!dec_ok.is_stream_truncated());
        srv_ok.join().unwrap();

        // 2) Truncated WAV stream (Content-Length = full_len, closes after 24 KiB)
        // must fail with DecodeFailed instead of returning Ok(None) clean EOF.
        let listener_tr = TcpListener::bind("127.0.0.1:0").expect("bind trunc srv");
        let port_tr = listener_tr.local_addr().unwrap().port();
        let srv_tr = std::thread::spawn(move || {
            let (mut sock, _) = listener_tr.accept().expect("accept");
            let mut req = [0u8; 512];
            let _ = sock.read(&mut req);
            let hdr = format!(
                "HTTP/1.1 200 OK\r\nContent-Length: {full_len}\r\nContent-Type: audio/wav\r\nAccept-Ranges: bytes\r\nConnection: close\r\n\r\n"
            );
            sock.write_all(hdr.as_bytes()).unwrap();
            sock.write_all(&half_wav).unwrap();
            sock.flush().unwrap();
        });

        let url_tr = format!("http://127.0.0.1:{port_tr}/stream/wavtrunc");
        let (mut dec_tr, _) = AudioDecoder::new_with_loopback_controls(
            &url_tr,
            LoopbackControls::basic(
                Arc::new(AtomicU64::new(1)),
                1,
                Arc::new(AtomicBool::new(false)),
            ),
        )
        .expect("open truncated wav header");
        let mut saw_err = false;
        loop {
            match dec_tr.decode_next_packet() {
                Ok(Some(_)) => {}
                Ok(None) => panic!("truncated loopback stream must NOT return clean EOF Ok(None)"),
                Err(_) => {
                    saw_err = true;
                    break;
                }
            }
        }
        assert!(saw_err);
        assert!(dec_tr.is_stream_truncated());
        srv_tr.join().unwrap();
    }
}
