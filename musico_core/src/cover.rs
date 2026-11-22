//! Cover-art cache path resolution with path-traversal protection.

use std::path::PathBuf;

/// Cover filenames embed the song id verbatim — a hostile id (a videoId
/// carrying `/` or `..` from a malformed catalog response) must not traverse
/// out of the covers dir. Legit ids are path hashes (hex) or `yt:` / `saavn:`
/// ids ([A-Za-z0-9_-]); anything else is flattened.
fn sanitize_cover_stem(id: &str) -> String {
    id.chars()
        .map(|c| {
            if c.is_ascii_alphanumeric() || matches!(c, '-' | '_' | ':') {
                c
            } else {
                '_'
            }
        })
        .collect()
}

/// Disk path for a song's cached cover thumbnail (`~/.cache/musico-online/covers/{id}.jpg`).
pub fn cover_path(song_id: &str) -> PathBuf {
    dirs::cache_dir()
        .unwrap_or_else(std::env::temp_dir)
        .join(crate::identity::DATA_SUBDIR)
        .join("covers")
        .join(format!("{}.jpg", sanitize_cover_stem(song_id)))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cover_stem_cannot_traverse() {
        // Legit ids pass through untouched.
        assert_eq!(sanitize_cover_stem("yt:dQw4w9WgXcQ"), "yt:dQw4w9WgXcQ");
        assert_eq!(sanitize_cover_stem("a1b2c3d4e5f6"), "a1b2c3d4e5f6");
        // Hostile ids flatten: no separators, no dot segments survive.
        for hostile in ["../../etc/passwd", "a/../../b", "..", ".", "x/y/z"] {
            let stem = sanitize_cover_stem(hostile);
            assert!(!stem.contains('/') && !stem.contains('.'), "{hostile} → {stem}");
            let path = cover_path(hostile);
            assert_eq!(
                path.parent().and_then(|p| p.file_name()),
                Some(std::ffi::OsStr::new("covers"))
            );
        }
    }
}
