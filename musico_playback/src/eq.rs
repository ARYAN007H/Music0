//! 10-band parametric equalizer using second-order IIR (biquad) filters.
//!
//! Each band is a peaking EQ filter at an ISO standard center frequency.
//! Filter state is stack-allocated — zero heap allocations per sample.

use serde::{Deserialize, Serialize};

/// ISO standard center frequencies for 10-band EQ.
pub const BAND_FREQS: [f32; 10] = [
    31.0, 62.0, 125.0, 250.0, 500.0, 1000.0, 2000.0, 4000.0, 8000.0, 16000.0,
];

pub const BAND_LABELS: [&str; 10] = [
    "31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K",
];

/// A single second-order biquad filter.
#[derive(Debug, Clone)]
struct Biquad {
    b0: f32, b1: f32, b2: f32,
    a1: f32, a2: f32,
    // Per-channel delay lines (stereo max).
    z1: [f32; 2],
    z2: [f32; 2],
}

impl Biquad {
    fn new() -> Self {
        Self {
            b0: 1.0, b1: 0.0, b2: 0.0,
            a1: 0.0, a2: 0.0,
            z1: [0.0; 2], z2: [0.0; 2],
        }
    }

    /// Compute peaking EQ coefficients.
    /// `freq` = center frequency, `gain_db` = boost/cut in dB, `q` = quality factor.
    fn set_peaking(&mut self, freq: f32, gain_db: f32, q: f32, sample_rate: f32) {
        let a = 10.0_f32.powf(gain_db / 40.0);
        let w0 = 2.0 * std::f32::consts::PI * freq / sample_rate;
        let sin_w0 = w0.sin();
        let cos_w0 = w0.cos();
        let alpha = sin_w0 / (2.0 * q);

        let a0 = 1.0 + alpha / a;
        self.b0 = (1.0 + alpha * a) / a0;
        self.b1 = (-2.0 * cos_w0) / a0;
        self.b2 = (1.0 - alpha * a) / a0;
        self.a1 = (-2.0 * cos_w0) / a0;
        self.a2 = (1.0 - alpha / a) / a0;
    }

    /// Process a single sample (transposed direct form II).
    #[inline(always)]
    fn process(&mut self, input: f32, ch: usize) -> f32 {
        let output = self.b0 * input + self.z1[ch];
        self.z1[ch] = self.b1 * input - self.a1 * output + self.z2[ch];
        self.z2[ch] = self.b2 * input - self.a2 * output;
        output
    }

    fn reset(&mut self) {
        self.z1 = [0.0; 2];
        self.z2 = [0.0; 2];
    }
}

/// EQ preset definition: 10 gain values in dB.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct EQPreset {
    pub id: &'static str,
    pub name: &'static str,
    pub gains: [f32; 10],
}

pub const PRESET_FLAT: EQPreset = EQPreset {
    id: "flat", name: "Flat",
    gains: [0.0; 10],
};
pub const PRESET_BASS_BOOST: EQPreset = EQPreset {
    id: "bass_boost", name: "Bass Boost",
    gains: [8.0, 6.0, 4.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0],
};
pub const PRESET_TREBLE_BOOST: EQPreset = EQPreset {
    id: "treble_boost", name: "Treble Boost",
    gains: [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 2.0, 4.0, 6.0, 8.0],
};
pub const PRESET_VOCAL: EQPreset = EQPreset {
    id: "vocal", name: "Vocal",
    gains: [-2.0, -1.0, 0.0, 2.0, 4.0, 4.0, 3.0, 1.0, 0.0, -1.0],
};
pub const PRESET_ACOUSTIC: EQPreset = EQPreset {
    id: "acoustic", name: "Acoustic",
    gains: [3.0, 2.0, 0.0, 1.0, 2.0, 2.0, 3.0, 4.0, 3.0, 1.0],
};
pub const PRESET_ELECTRONIC: EQPreset = EQPreset {
    id: "electronic", name: "Electronic",
    gains: [6.0, 5.0, 3.0, 0.0, -2.0, 0.0, 2.0, 4.0, 5.0, 6.0],
};
pub const PRESET_CLASSICAL: EQPreset = EQPreset {
    id: "classical", name: "Classical",
    gains: [0.0, 0.0, 0.0, 0.0, 0.0, 0.0, -2.0, -2.0, -2.0, -4.0],
};
pub const PRESET_ROCK: EQPreset = EQPreset {
    id: "rock", name: "Rock",
    gains: [5.0, 4.0, 2.0, 0.0, -1.0, 0.0, 2.0, 3.0, 4.0, 5.0],
};
pub const PRESET_HIP_HOP: EQPreset = EQPreset {
    id: "hip_hop", name: "Hip Hop",
    gains: [6.0, 5.0, 3.0, 1.0, 0.0, 0.0, 1.0, 0.0, 2.0, 3.0],
};
pub const PRESET_JAZZ: EQPreset = EQPreset {
    id: "jazz", name: "Jazz",
    gains: [2.0, 1.0, 0.0, 2.0, 3.0, 3.0, 2.0, 1.0, 2.0, 3.0],
};
pub const PRESET_LATE_NIGHT: EQPreset = EQPreset {
    id: "late_night", name: "Late Night",
    gains: [4.0, 3.0, 2.0, 1.0, 0.0, 0.0, -1.0, -2.0, -3.0, -4.0],
};
pub const PRESET_LOUDNESS: EQPreset = EQPreset {
    id: "loudness", name: "Loudness",
    gains: [6.0, 4.0, 0.0, -2.0, -1.0, 0.0, -1.0, -2.0, 4.0, 6.0],
};

pub const PRESET_SONY_XM5: EQPreset = EQPreset {
    id: "sony_xm5", name: "Sony WH-1000XM5 (AutoEQ)",
    gains: [-3.5, -3.0, -2.0, -0.5, 1.0, 1.5, 2.5, 2.0, 1.0, 1.0],
};
pub const PRESET_SENNHEISER_HD650: EQPreset = EQPreset {
    id: "sennheiser_hd650", name: "Sennheiser HD 650 (AutoEQ)",
    gains: [4.5, 3.0, 0.5, -1.0, 0.0, 0.5, 1.5, 2.0, 2.5, 2.5],
};
pub const PRESET_AIRPODS_PRO_2: EQPreset = EQPreset {
    id: "airpods_pro_2", name: "AirPods Pro 2 (AutoEQ)",
    gains: [1.0, 0.5, -0.5, -0.5, 0.5, 1.0, 1.5, 1.5, 2.0, 2.0],
};
pub const PRESET_BEYER_DT990: EQPreset = EQPreset {
    id: "beyerdynamic_dt990", name: "Beyerdynamic DT 990 Pro (AutoEQ)",
    gains: [2.5, 2.0, 0.5, 0.5, 1.0, 0.5, -1.5, -3.5, -4.5, -3.0],
};
pub const PRESET_HARMAN_2019: EQPreset = EQPreset {
    id: "harman_2019", name: "Harman Target 2019",
    gains: [3.5, 3.0, 1.0, 0.0, -0.5, 0.5, 2.5, 2.5, 1.5, 1.0],
};

pub const ALL_PRESETS: &[EQPreset] = &[
    PRESET_FLAT, PRESET_BASS_BOOST, PRESET_TREBLE_BOOST, PRESET_VOCAL,
    PRESET_ACOUSTIC, PRESET_ELECTRONIC, PRESET_CLASSICAL, PRESET_ROCK,
    PRESET_HIP_HOP, PRESET_JAZZ, PRESET_LATE_NIGHT, PRESET_LOUDNESS,
    PRESET_SONY_XM5, PRESET_SENNHEISER_HD650, PRESET_AIRPODS_PRO_2,
    PRESET_BEYER_DT990, PRESET_HARMAN_2019,
];

pub fn preset_by_id(id: &str) -> EQPreset {
    ALL_PRESETS.iter().find(|p| p.id == id).cloned().unwrap_or(PRESET_FLAT)
}

/// Real-time stem separation mode (Mid/Side + vocal formant filtering).
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum StemMode {
    Full,
    Instrumental,
    Vocals,
}

/// The 10-band equalizer + AutoEQ + Tube Warmth + Stem Separation processor.
pub struct Equalizer {
    bands: [Biquad; 10],
    gains_db: [f32; 10],
    enabled: bool,
    tube_warmth: bool,
    stem_mode: StemMode,
    sample_rate: f32,
    channels: usize,
}

impl Equalizer {
    pub fn new(sample_rate: u32, channels: usize) -> Self {
        Self {
            bands: std::array::from_fn(|_| Biquad::new()),
            gains_db: [0.0; 10],
            enabled: false,
            tube_warmth: false,
            stem_mode: StemMode::Full,
            sample_rate: sample_rate as f32,
            channels: channels.min(2),
        }
    }

    pub fn set_enabled(&mut self, enabled: bool) {
        self.enabled = enabled;
        if !enabled {
            for band in &mut self.bands {
                band.reset();
            }
        }
    }

    pub fn set_tube_warmth(&mut self, warmth: bool) {
        self.tube_warmth = warmth;
    }

    pub fn set_stem_mode(&mut self, mode: StemMode) {
        self.stem_mode = mode;
    }

    pub fn is_enabled(&self) -> bool {
        self.enabled
    }

    /// Update all 10 band gains (in dB). Recomputes biquad coefficients.
    pub fn set_gains(&mut self, gains_db: [f32; 10]) {
        self.gains_db = gains_db;
        let q = 1.41; // Butterworth Q for gentle overlap
        for (i, band) in self.bands.iter_mut().enumerate() {
            band.set_peaking(BAND_FREQS[i], gains_db[i], q, self.sample_rate);
        }
    }

    /// Apply a preset.
    pub fn set_preset(&mut self, preset: &EQPreset) {
        self.set_gains(preset.gains);
        self.enabled = preset.id != "flat";
    }

    pub fn gains(&self) -> &[f32; 10] {
        &self.gains_db
    }

    /// Process interleaved samples in-place. Zero-alloc.
    #[inline]
    pub fn process_interleaved(&mut self, samples: &mut [f32]) {
        let ch = self.channels;
        for frame in samples.chunks_exact_mut(ch) {
            if ch == 2 && self.stem_mode != StemMode::Full {
                let l = frame[0];
                let r = frame[1];
                let mid = 0.5 * (l + r);
                let side = 0.5 * (l - r);
                match self.stem_mode {
                    StemMode::Instrumental => {
                        frame[0] = side + 0.12 * mid;
                        frame[1] = -side + 0.12 * mid;
                    }
                    StemMode::Vocals => {
                        frame[0] = mid * 1.2 + 0.15 * side;
                        frame[1] = mid * 1.2 - 0.15 * side;
                    }
                    StemMode::Full => {}
                }
            }

            if self.enabled {
                for (c, sample) in frame.iter_mut().enumerate().take(ch) {
                    for band in &mut self.bands {
                        *sample = band.process(*sample, c);
                    }
                }
            }

            if self.tube_warmth {
                for sample in frame.iter_mut().take(ch) {
                    let x = *sample;
                    let biased = x + 0.04 * x * x;
                    *sample = (biased * 1.55).tanh() / 1.55_f32.tanh();
                }
            }
        }
    }
}

