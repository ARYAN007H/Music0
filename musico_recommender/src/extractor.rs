//! Audio feature extraction — single-pass decode + metadata + feature computation.
//!
//! Decodes only the first 60 seconds of audio (sufficient for statistical
//! convergence of all features).  Mel filterbank and Hamming window are
//! computed once via `LazyLock`.

use std::f32::consts::PI;
use std::path::Path;
use std::sync::LazyLock;

use rustfft::num_complex::Complex;
use rustfft::FftPlanner;
use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::DecoderOptions;
use symphonia::core::formats::FormatOptions;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;

use crate::errors::RecommenderError;
use crate::models::{AnalysisResult, FeatureVector};

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const TARGET_SR: f32 = 22050.0;
const PRE_EMPH: f32 = 0.97;
const FRAME_LEN: usize = 551;
const HOP_LEN: usize = 221;
const N_MELS: usize = 26;
const N_MFCC: usize = 13;
const MEL_LOW: f32 = 0.0;
const MEL_HIGH: f32 = 8000.0;
const FFT_SIZE: usize = 1024;
/// Maximum samples to decode (60 s at 22 050 Hz).
const MAX_ANALYSIS_SAMPLES: usize = 60 * 22050;

// ---------------------------------------------------------------------------
// Static precomputed tables (LazyLock — computed once, zero cost thereafter)
// ---------------------------------------------------------------------------

static MEL_FILTERS: LazyLock<Vec<Vec<f32>>> = LazyLock::new(|| build_mel_filterbank(FFT_SIZE / 2 + 1));
static HAMMING: LazyLock<Vec<f32>> = LazyLock::new(|| {
    (0..FRAME_LEN)
        .map(|i| 0.54 - 0.46 * (2.0 * PI * i as f32 / (FRAME_LEN as f32 - 1.0)).cos())
        .collect()
});
static BIN_TO_CHROMA: LazyLock<Vec<Option<usize>>> = LazyLock::new(|| {
    let n_bins = FFT_SIZE / 2 + 1;
    (0..n_bins)
        .map(|k| {
            if k == 0 { return None; }
            let freq = k as f32 * TARGET_SR / FFT_SIZE as f32;
            if freq < 20.0 || freq > TARGET_SR / 2.0 { return None; }
            let midi = 12.0 * (freq / 440.0).log2() + 69.0;
            Some(((midi.round() as i32) % 12).rem_euclid(12) as usize)
        })
        .collect()
});

// ---------------------------------------------------------------------------
// Public API — single-pass analysis
// ---------------------------------------------------------------------------

/// Analyses an audio file in a single pass: probes for metadata, decodes the
/// first 60 seconds to PCM, and extracts all features.
///
/// Returns an [`AnalysisResult`] containing the feature vector, duration, and
/// metadata tags.
pub fn analyze_file(path: &str) -> Result<AnalysisResult, RecommenderError> {
    let file_path = Path::new(path);
    let fallback_title = file_path
        .file_stem()
        .map(|s| s.to_string_lossy().into_owned())
        .unwrap_or_else(|| "Unknown".into());

    let file = std::fs::File::open(path).map_err(RecommenderError::IoError)?;
    let mss = MediaSourceStream::new(Box::new(file), Default::default());

    let mut hint = Hint::new();
    if let Some(ext) = file_path.extension() {
        hint.with_extension(&ext.to_string_lossy());
    }

    let mut probed = symphonia::default::get_probe()
        .format(
            &hint,
            mss,
            &FormatOptions::default(),
            &MetadataOptions::default(),
        )
        .map_err(|e| RecommenderError::DecodeError(format!("Probe failed: {e}")))?;

    // ---- Metadata (from the same probe, no second file open) ----
    let mut title = String::new();
    let mut artist = String::new();
    let mut album = String::new();

    if let Some(metadata) = probed.metadata.get() {
        if let Some(rev) = metadata.current() {
            for tag in rev.tags() {
                if let Some(std_key) = &tag.std_key {
                    use symphonia::core::meta::StandardTagKey::*;
                    match std_key {
                        TrackTitle => title = tag.value.to_string(),
                        Artist => artist = tag.value.to_string(),
                        Album => album = tag.value.to_string(),
                        _ => {}
                    }
                }
            }
        }
    }

    // Also check format-level metadata (some containers store tags there).
    if title.is_empty() || artist.is_empty() || album.is_empty() {
        let fmt_meta = probed.format.metadata();
        if let Some(rev) = fmt_meta.current() {
            for tag in rev.tags() {
                if let Some(std_key) = &tag.std_key {
                    use symphonia::core::meta::StandardTagKey::*;
                    match std_key {
                        TrackTitle if title.is_empty() => title = tag.value.to_string(),
                        Artist if artist.is_empty() => artist = tag.value.to_string(),
                        Album if album.is_empty() => album = tag.value.to_string(),
                        _ => {}
                    }
                }
            }
        }
    }

    if title.is_empty() {
        title = fallback_title;
    }

    // ---- Decode first 60 s to mono PCM ----
    let format = &mut probed.format;

    let track = format
        .tracks()
        .iter()
        .find(|t| t.codec_params.codec != symphonia::core::codecs::CODEC_TYPE_NULL)
        .ok_or_else(|| RecommenderError::DecodeError("No audio track found".into()))?;

    let track_id = track.id;
    let codec_params = track.codec_params.clone();
    let source_sr = codec_params.sample_rate.unwrap_or(44100) as f32;
    let n_channels = codec_params.channels.map(|c| c.count()).unwrap_or(2) as f32;

    // Compute full duration from codec params if available.
    let full_duration_secs = codec_params
        .n_frames
        .map(|n| (n as f32 / source_sr) as u32)
        .unwrap_or(0);

    let mut decoder = symphonia::default::get_codecs()
        .make(&codec_params, &DecoderOptions::default())
        .map_err(|e| RecommenderError::DecodeError(format!("Codec init failed: {e}")))?;

    // Target number of source samples corresponding to 60 s at source SR.
    let max_source_samples = (60.0 * source_sr) as usize;
    let mut all_samples: Vec<f32> = Vec::with_capacity(max_source_samples);

    loop {
        if all_samples.len() >= max_source_samples {
            break;
        }

        let packet = match format.next_packet() {
            Ok(p) => p,
            Err(symphonia::core::errors::Error::IoError(ref e))
                if e.kind() == std::io::ErrorKind::UnexpectedEof =>
            {
                break;
            }
            Err(_) => break,
        };

        if packet.track_id() != track_id {
            continue;
        }

        let decoded = match decoder.decode(&packet) {
            Ok(d) => d,
            Err(_) => continue,
        };

        let spec = *decoded.spec();
        let num_frames = decoded.capacity();
        let mut sample_buf = SampleBuffer::<f32>::new(num_frames as u64, spec);
        sample_buf.copy_interleaved_ref(decoded);

        let ch = spec.channels.count() as usize;
        for frame_samples in sample_buf.samples().chunks(ch) {
            let mono: f32 = frame_samples.iter().sum::<f32>() / n_channels;
            all_samples.push(mono);
        }
    }

    // Resample to TARGET_SR if necessary.
    if (source_sr - TARGET_SR).abs() > 1.0 {
        all_samples = resample_linear(&all_samples, source_sr, TARGET_SR);
    }

    // Cap at MAX_ANALYSIS_SAMPLES after resampling.
    all_samples.truncate(MAX_ANALYSIS_SAMPLES);

    if all_samples.len() < FRAME_LEN {
        return Err(RecommenderError::DecodeError("Audio too short for analysis".into()));
    }

    // Use decoded length for duration if codec didn't provide n_frames.
    let duration_secs = if full_duration_secs > 0 {
        full_duration_secs
    } else {
        (all_samples.len() as f32 / TARGET_SR) as u32
    };

    // ---- Feature extraction (streaming single-pass, zero per-frame Vec churn) ----
    let rms_energy = compute_rms(&all_samples);
    let zero_crossing_rate = compute_zcr_pre_emphasised(&all_samples);
    let (mfcc, spectral_centroid, spectral_rolloff, tempo_bpm, chroma) =
        extract_spectral_features_streaming(&all_samples)?;

    let mut fv = FeatureVector {
        version: FeatureVector::CURRENT_VERSION,
        mfcc,
        spectral_centroid,
        spectral_rolloff,
        zero_crossing_rate,
        rms_energy,
        tempo_bpm,
        chroma,
    };
    normalise_feature_vector(&mut fv);

    // Compute RMS in dB for ReplayGain normalization.
    let rms_db = if rms_energy > 1e-10 {
        20.0 * rms_energy.log10()
    } else {
        -96.0 // silence floor
    };

    Ok(AnalysisResult { feature_vector: fv, duration_secs, title, artist, album, rms_db })
}

// ---------------------------------------------------------------------------
// Resampling
// ---------------------------------------------------------------------------

fn resample_linear(input: &[f32], src_sr: f32, dst_sr: f32) -> Vec<f32> {
    let ratio = src_sr / dst_sr;
    let out_len = (input.len() as f32 / ratio) as usize;
    let mut output = Vec::with_capacity(out_len);
    for i in 0..out_len {
        let src_idx = i as f32 * ratio;
        let idx0 = src_idx as usize;
        let frac = src_idx - idx0 as f32;
        let s0 = input[idx0.min(input.len() - 1)];
        let s1 = input[(idx0 + 1).min(input.len() - 1)];
        output.push(s0 + frac * (s1 - s0));
    }
    output
}

// ---------------------------------------------------------------------------
// Pre-emphasis, framing, windowing
// ---------------------------------------------------------------------------

#[inline]
fn emphasised_sample(samples: &[f32], idx: usize) -> f32 {
    if idx == 0 {
        samples[0]
    } else {
        samples[idx] - PRE_EMPH * samples[idx - 1]
    }
}

fn compute_zcr_pre_emphasised(samples: &[f32]) -> f32 {
    if samples.len() < 2 {
        return 0.0;
    }
    let mut prev = samples[0];
    let mut crossings: usize = 0;
    for i in 1..samples.len() {
        let cur = samples[i] - PRE_EMPH * samples[i - 1];
        if (prev >= 0.0) != (cur >= 0.0) {
            crossings += 1;
        }
        prev = cur;
    }
    crossings as f32 / (samples.len() - 1) as f32
}

/// Streams windowed FFT frames through reusable scratch buffers instead of
/// allocating ~24,000 temporary `Vec`s per song, keeping identical feature math.
fn extract_spectral_features_streaming(
    samples: &[f32],
) -> Result<([f32; N_MFCC], f32, f32, f32, [f32; 12]), RecommenderError> {
    let n_frames = if samples.len() >= FRAME_LEN {
        (samples.len() - FRAME_LEN) / HOP_LEN + 1
    } else {
        0
    };
    if n_frames == 0 {
        return Err(RecommenderError::DecodeError("No frames produced".into()));
    }

    let window = &*HAMMING;
    let filters = &*MEL_FILTERS;
    let bin_to_chroma = &*BIN_TO_CHROMA;
    let n_bins = FFT_SIZE / 2 + 1;
    let inv_fft_size = 1.0 / FFT_SIZE as f32;

    let mut planner = FftPlanner::<f32>::new();
    let fft = planner.plan_fft_forward(FFT_SIZE);

    let mut fft_buf = vec![Complex::new(0.0_f32, 0.0_f32); FFT_SIZE];
    let mut power_buf = vec![0.0_f32; n_bins];
    let mut prev_power_buf = vec![0.0_f32; n_bins];
    let mut mel_buf = [0.0_f32; N_MELS];
    let mut onset_env = Vec::with_capacity(n_frames);

    let mut mfcc_avg = [0.0_f32; N_MFCC];
    let mut centroid_total = 0.0_f64;
    let mut rolloff_total = 0.0_f64;
    let mut chroma = [0.0_f32; 12];

    let mut start = 0;
    for frame_idx in 0..n_frames {
        for (i, &w) in window.iter().enumerate() {
            fft_buf[i] = Complex::new(emphasised_sample(samples, start + i) * w, 0.0);
        }
        for c in &mut fft_buf[FRAME_LEN..FFT_SIZE] {
            *c = Complex::new(0.0, 0.0);
        }
        fft.process(&mut fft_buf);

        let mut energy_total = 0.0_f64;
        let mut weighted_sum = 0.0_f64;
        for k in 0..n_bins {
            let p = fft_buf[k].norm_sqr() * inv_fft_size;
            power_buf[k] = p;
            let pd = p as f64;
            energy_total += pd;
            weighted_sum += (k as f64) * pd;
            if let Some(ci) = bin_to_chroma[k] {
                chroma[ci] += p;
            }
        }

        if energy_total >= 1e-12 {
            centroid_total += weighted_sum / energy_total;
        }

        let target = energy_total * 0.85_f64;
        let mut cumulative = 0.0_f64;
        let mut rolloff_bin = 0usize;
        for (k, &p) in power_buf.iter().enumerate() {
            cumulative += p as f64;
            if cumulative >= target {
                rolloff_bin = k;
                break;
            }
        }
        rolloff_total += rolloff_bin as f64;

        for (m, filt) in filters.iter().enumerate() {
            let energy: f32 = power_buf.iter().zip(filt.iter()).map(|(s, f)| s * f).sum();
            mel_buf[m] = (energy.max(1e-10)).ln();
        }
        let n_mels_f = N_MELS as f32;
        for i in 0..N_MFCC {
            let mut coeff = 0.0_f32;
            for (j, &mel_val) in mel_buf.iter().enumerate() {
                coeff += mel_val * (PI * (i as f32) * (j as f32 + 0.5) / n_mels_f).cos();
            }
            mfcc_avg[i] += coeff;
        }

        if frame_idx == 0 {
            onset_env.push(0.0_f32);
        } else {
            let flux: f32 = power_buf
                .iter()
                .zip(prev_power_buf.iter())
                .map(|(&cur, &prev)| (cur - prev).max(0.0))
                .sum();
            onset_env.push(flux);
        }

        std::mem::swap(&mut power_buf, &mut prev_power_buf);
        start += HOP_LEN;
    }

    let nf_f32 = n_frames as f32;
    let nf_f64 = n_frames as f64;
    for v in mfcc_avg.iter_mut() {
        *v /= nf_f32;
    }
    let spectral_centroid = (centroid_total / nf_f64) as f32;
    let spectral_rolloff = (rolloff_total / nf_f64) as f32;
    let tempo_bpm = estimate_tempo_from_onset(&onset_env);

    for c in chroma.iter_mut() {
        *c /= nf_f32;
    }
    let chroma_sum: f32 = chroma.iter().sum();
    if chroma_sum > 1e-10 {
        for c in chroma.iter_mut() {
            *c /= chroma_sum;
        }
    }

    Ok((mfcc_avg, spectral_centroid, spectral_rolloff, tempo_bpm, chroma))
}

// ---------------------------------------------------------------------------
// Mel filterbank + MFCC
// ---------------------------------------------------------------------------

fn hz_to_mel(hz: f32) -> f32 { 2595.0 * (1.0 + hz / 700.0).log10() }
fn mel_to_hz(mel: f32) -> f32 { 700.0 * (10.0_f32.powf(mel / 2595.0) - 1.0) }

fn build_mel_filterbank(n_fft_bins: usize) -> Vec<Vec<f32>> {
    let mel_low = hz_to_mel(MEL_LOW);
    let mel_high = hz_to_mel(MEL_HIGH);
    let mel_points: Vec<f32> = (0..N_MELS + 2)
        .map(|i| mel_low + (mel_high - mel_low) * i as f32 / (N_MELS + 1) as f32)
        .collect();
    let hz_points: Vec<f32> = mel_points.iter().map(|&m| mel_to_hz(m)).collect();
    let bin_points: Vec<usize> = hz_points
        .iter()
        .map(|&h| ((h / (TARGET_SR / 2.0)) * (n_fft_bins - 1) as f32) as usize)
        .collect();

    let mut filters = Vec::with_capacity(N_MELS);
    for m in 0..N_MELS {
        let mut filt = vec![0.0_f32; n_fft_bins];
        let start = bin_points[m];
        let center = bin_points[m + 1];
        let end = bin_points[m + 2];
        for k in start..center {
            if center > start { filt[k] = (k - start) as f32 / (center - start) as f32; }
        }
        for k in center..end {
            if end > center { filt[k] = (end - k) as f32 / (end - center) as f32; }
        }
        filters.push(filt);
    }
    filters
}

fn compute_rms(samples: &[f32]) -> f32 {
    let sum_sq: f64 = samples.iter().map(|&s| (s as f64) * (s as f64)).sum();
    (sum_sq / samples.len() as f64).sqrt() as f32
}

// ---------------------------------------------------------------------------
// Tempo estimation
// ---------------------------------------------------------------------------

fn estimate_tempo_from_onset(onset_env: &[f32]) -> f32 {
    let max_lag = onset_env.len().min(((TARGET_SR / HOP_LEN as f32) * 60.0 / 60.0) as usize + 1);
    let min_lag = ((TARGET_SR / HOP_LEN as f32) * 60.0 / 200.0) as usize;

    if max_lag <= min_lag || max_lag > onset_env.len() { return 120.0; }

    let mut best_lag = min_lag;
    let mut best_val = f32::NEG_INFINITY;
    for lag in min_lag..max_lag {
        let mut sum = 0.0_f32;
        for i in 0..onset_env.len() - lag { sum += onset_env[i] * onset_env[i + lag]; }
        if sum > best_val {
            best_val = sum;
            best_lag = lag;
        }
    }
    if best_lag == 0 { return 120.0; }

    let bpm = 60.0 * (TARGET_SR / HOP_LEN as f32) / best_lag as f32;
    bpm.clamp(60.0, 200.0)
}

// ---------------------------------------------------------------------------
// Normalisation
// ---------------------------------------------------------------------------

fn normalise_feature_vector(fv: &mut FeatureVector) {
    for c in fv.mfcc.iter_mut() { *c = ((*c + 50.0) / 100.0).clamp(0.0, 1.0); }
    fv.spectral_centroid = (fv.spectral_centroid / (FFT_SIZE as f32 / 2.0)).clamp(0.0, 1.0);
    fv.spectral_rolloff = (fv.spectral_rolloff / (FFT_SIZE as f32 / 2.0)).clamp(0.0, 1.0);
    fv.zero_crossing_rate = fv.zero_crossing_rate.clamp(0.0, 1.0);
    fv.rms_energy = fv.rms_energy.clamp(0.0, 1.0);
    fv.tempo_bpm = ((fv.tempo_bpm - 60.0) / 140.0).clamp(0.0, 1.0);
    for c in fv.chroma.iter_mut() { *c = c.clamp(0.0, 1.0); }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn streaming_feature_extractor_produces_finite_pitch_accurate_features() {
        // 2 seconds of a 440 Hz (A4 -> pitch class 9) sine wave at TARGET_SR.
        let n = (TARGET_SR as usize) * 2;
        let samples: Vec<f32> = (0..n)
            .map(|i| (2.0 * PI * 440.0 * i as f32 / TARGET_SR).sin() * 0.5)
            .collect();

        let (mfcc, centroid, rolloff, tempo, chroma) =
            extract_spectral_features_streaming(&samples).expect("streaming features");
        let zcr = compute_zcr_pre_emphasised(&samples);

        assert!(mfcc.iter().all(|v| v.is_finite()));
        assert!(centroid.is_finite() && centroid > 0.0);
        assert!(rolloff.is_finite() && rolloff > 0.0);
        assert!((60.0..=200.0).contains(&tempo));
        assert!(zcr > 0.0 && zcr < 1.0);
        // Pitch class 9 (A) should dominate the 440 Hz tone's chroma distribution.
        let dominant_chroma = chroma
            .iter()
            .enumerate()
            .max_by(|a, b| a.1.partial_cmp(b.1).unwrap())
            .map(|(i, _)| i)
            .unwrap();
        assert_eq!(dominant_chroma, 9, "expected A (9) chroma peak, got {chroma:?}");
    }
}
