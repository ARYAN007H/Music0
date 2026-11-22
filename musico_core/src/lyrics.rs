//! # Musico — Synced & Enhanced Lyrics (LRC + Word-Level Timing)
//!
//! Parses sidecar `.lrc` files and builds enhanced word-synced lyric DTOs
//! consumed by the Compose Multiplatform Lyrics Studio.

use std::path::Path;

use serde::Serialize;

/// A single timestamped lyric line.
#[derive(Debug, Clone, Serialize)]
pub struct LrcLine {
    pub time_secs: f32,
    pub text: String,
}

#[derive(Debug, Clone, Serialize)]
pub struct LyricsDto {
    pub synced: bool,
    pub lines: Vec<LrcLine>,
}

#[derive(Debug, Clone, Serialize)]
pub struct LyricWordDto {
    pub word: String,
    pub start_ms: u64,
    pub end_ms: u64,
}

#[derive(Debug, Clone, Serialize)]
pub struct EnhancedLyricLineDto {
    pub time_secs: f32,
    pub text: String,
    pub words: Vec<LyricWordDto>,
    pub romanized: Option<String>,
    pub translation: Option<String>,
}

#[derive(Debug, Clone, Serialize)]
pub struct EnhancedLyricsDto {
    pub track_id: String,
    pub synced: bool,
    pub source: String,
    pub has_word_sync: bool,
    pub has_romanized: bool,
    pub has_translation: bool,
    pub lines: Vec<EnhancedLyricLineDto>,
}

/// Splits a lyric line text that may contain inline `primary | romanized | translation`
/// and synthesizes word-level timestamps across `[start_secs, end_secs]`.
pub fn build_enhanced_lines_from_timed(
    raw_lines: &[(f32, String)],
    total_dur_secs: f32,
) -> EnhancedLyricsDto {
    let mut lines = Vec::with_capacity(raw_lines.len());
    let mut has_romanized = false;
    let mut has_translation = false;

    for (idx, (t, raw_text)) in raw_lines.iter().enumerate() {
        let next_t = raw_lines
            .get(idx + 1)
            .map(|(nt, _)| *nt)
            .unwrap_or_else(|| (t + 4.0).max(total_dur_secs));
        let span_ms = ((next_t - t).clamp(0.6, 6.0) * 1000.0) as u64;
        let start_base_ms = (*t * 1000.0).max(0.0) as u64;

        let (primary, romanized, translation) = split_lyric_annotations(raw_text);
        if romanized.is_some() {
            has_romanized = true;
        }
        if translation.is_some() {
            has_translation = true;
        }

        let tokens: Vec<&str> = primary.split_whitespace().collect();
        let word_count = tokens.len().max(1) as u64;
        let step_ms = (span_ms / word_count).max(60);
        let words = tokens
            .into_iter()
            .enumerate()
            .map(|(w_idx, w)| {
                let ws = start_base_ms + (w_idx as u64) * step_ms;
                let we = (ws + step_ms).min(start_base_ms + span_ms);
                LyricWordDto {
                    word: w.to_string(),
                    start_ms: ws,
                    end_ms: we.max(ws + 40),
                }
            })
            .collect();

        lines.push(EnhancedLyricLineDto {
            time_secs: *t,
            text: primary,
            words,
            romanized,
            translation,
        });
    }

    EnhancedLyricsDto {
        track_id: String::new(),
        synced: true,
        source: String::new(),
        has_word_sync: true,
        has_romanized,
        has_translation,
        lines,
    }
}

fn split_lyric_annotations(raw: &str) -> (String, Option<String>, Option<String>) {
    let trimmed = raw.trim();
    // Support optional `Primary | Romanized | Translation` convention in LRC files
    if let Some((left, right)) = trimmed.split_once(" | ") {
        let right = right.trim();
        if let Some((rom, trans)) = right.split_once(" | ") {
            return (
                left.trim().to_string(),
                Some(rom.trim().to_string()).filter(|s| !s.is_empty()),
                Some(trans.trim().to_string()).filter(|s| !s.is_empty()),
            );
        }
        return (
            left.trim().to_string(),
            Some(right.to_string()).filter(|s| !s.is_empty()),
            None,
        );
    }
    (trimmed.to_string(), None, None)
}

/// Parse an LRC string into timed lines (ignores `[ar:…]` metadata tags).
pub fn parse_lrc(content: &str) -> Vec<LrcLine> {
    let mut lines = Vec::new();
    for raw_line in content.lines() {
        let raw_line = raw_line.trim();
        if raw_line.is_empty() {
            continue;
        }
        // Pattern: [mm:ss.xx] text — multiple leading timestamps are allowed.
        let mut rest = raw_line;
        let mut times: Vec<f32> = Vec::new();
        loop {
            let Some(start) = rest.find('[') else { break };
            let Some(end_off) = rest[start..].find(']') else { break };
            let end = start + end_off;
            let tag = &rest[start + 1..end];
            match parse_timestamp(tag) {
                Some(t) => times.push(t),
                None => {
                    // Not a timestamp — either metadata or plain text.
                    if times.is_empty() {
                        break; // metadata tag line like [ar:Artist]
                    }
                }
            }
            rest = rest[end + 1..].trim();
            if times.is_empty() {
                break;
            }
        }
        let text = rest.trim().to_string();
        if text.is_empty() {
            continue;
        }
        for t in times {
            lines.push(LrcLine {
                time_secs: t,
                text: text.clone(),
            });
        }
    }
    lines.sort_by(|a, b| {
        a.time_secs
            .partial_cmp(&b.time_secs)
            .unwrap_or(std::cmp::Ordering::Equal)
    });
    lines
}

/// Parse "mm:ss.xx" / "m:ss" into seconds. Multi-minute forms not needed.
fn parse_timestamp(s: &str) -> Option<f32> {
    let parts: Vec<&str> = s.split(':').collect();
    if parts.len() != 2 {
        return None;
    }
    let minutes: f32 = parts[0].parse().ok()?;
    let seconds: f32 = parts[1].parse().ok()?;
    Some(minutes * 60.0 + seconds)
}

/// Find and parse the sidecar .lrc next to an audio file.
/// Returns None when there is no lyrics file at all.
pub fn load_sidecar(audio_path: &str) -> Option<LyricsDto> {
    let lrc_path = Path::new(audio_path).with_extension("lrc");
    let content = std::fs::read_to_string(lrc_path).ok()?;
    if content.trim().is_empty() {
        return None;
    }
    let lines = parse_lrc(&content);
    if !lines.is_empty() {
        return Some(LyricsDto {
            synced: true,
            lines,
        });
    }
    // Unsynced plain-text lyrics
    Some(LyricsDto {
        synced: false,
        lines: content
            .lines()
            .filter(|l| !l.trim().is_empty())
            .map(|l| LrcLine {
                time_secs: 0.0,
                text: l.trim().to_string(),
            })
            .collect(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_lrc_with_metadata() {
        let content = "[ar:Test Artist]\n[00:12.34] Hello world\n[01:00] Second line\n";
        let lines = parse_lrc(content);
        assert_eq!(lines.len(), 2);
        assert!((lines[0].time_secs - 12.34).abs() < 0.01);
        assert_eq!(lines[0].text, "Hello world");
        assert!((lines[1].time_secs - 60.0).abs() < 0.01);
    }

    #[test]
    fn duplicate_timestamps_expand() {
        let content = "[00:05.00][01:05.00] Chorus\n";
        let lines = parse_lrc(content);
        assert_eq!(lines.len(), 2);
        assert_eq!(lines[0].text, "Chorus");
    }

    #[test]
    fn unsynced_fallback() {
        let tmp = std::env::temp_dir().join("musico-test-unsynced.txt");
        std::fs::write(&tmp, "just some words\non lines\n").unwrap();
        let lrc = tmp.with_extension("lrc");
        std::fs::write(&lrc, "plain line one\nplain line two\n").unwrap();
        let dto = load_sidecar(&tmp.to_string_lossy()).unwrap();
        assert!(!dto.synced);
        assert_eq!(dto.lines.len(), 2);
        let _ = std::fs::remove_file(lrc);
    }

    #[test]
    fn enhanced_lyrics_synthesizes_words_and_annotations() {
        let raw = vec![
            (1.0, "Hello world | Haro warudo | Greeting".to_string()),
            (3.0, "Second line".to_string()),
        ];
        let dto = build_enhanced_lines_from_timed(&raw, 10.0);
        assert!(dto.synced);
        assert!(dto.has_word_sync);
        assert!(dto.has_romanized);
        assert!(dto.has_translation);
        assert_eq!(dto.lines.len(), 2);
        assert_eq!(dto.lines[0].text, "Hello world");
        assert_eq!(dto.lines[0].romanized.as_deref(), Some("Haro warudo"));
        assert_eq!(dto.lines[0].translation.as_deref(), Some("Greeting"));
        assert_eq!(dto.lines[0].words.len(), 2);
        assert_eq!(dto.lines[0].words[0].word, "Hello");
    }
}
