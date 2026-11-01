//! User playlists (#60): local, sled-backed, ordered track lists.  One
//! bincode [`UserPlaylist`] per playlist in the `lib:playlists` tree, keyed
//! by a generated `pl-…` id.  Tracks carry everything needed to play them
//! after a restart — source namespace (`yt` / `saavn` resolve through the
//! online pipeline, `local` plays `file_path` directly) plus display
//! metadata — so a playlist is self-contained with no catalog lookups.
//! Duplicates are allowed (real playlists have repeats); the track cap
//! keeps one bad client from growing a playlist without bound.

use serde::{Deserialize, Serialize};

use crate::errors::RecommenderError;
use crate::models::Store;

/// Per-playlist track cap — 1000 rows is far past any real playlist and
/// bounds re-write cost on every mutation (the playlist is stored whole).
const MAX_TRACKS: usize = 1000;
const TREE: &str = "lib:playlists";

/// One track in a user playlist.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct PlaylistTrack {
    /// Playback namespace: "yt" | "saavn" | "local".
    pub source: String,
    /// "yt"/"saavn": the catalog id; "local": the library song id.
    pub id: String,
    pub title: String,
    #[serde(default)]
    pub artist: String,
    #[serde(default)]
    pub album: String,
    #[serde(default)]
    pub duration_secs: Option<u32>,
    #[serde(default)]
    pub thumbnail: Option<String>,
    /// Local songs only — the absolute path the engine plays.
    #[serde(default)]
    pub file_path: Option<String>,
}

/// A user-created playlist.
#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
pub struct UserPlaylist {
    pub id: String,
    pub name: String,
    pub created_at: i64,
    pub modified_at: i64,
    pub tracks: Vec<PlaylistTrack>,
}

fn now_ms() -> i64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}

/// Monotonic suffix so two playlists created in the same millisecond (a
/// double-click, scripted setup) still get distinct ids.
static ID_SEQ: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);

fn new_id() -> String {
    let seq = ID_SEQ.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
    format!("pl-{:x}-{:x}", now_ms(), seq)
}

fn load(store: &Store, id: &str) -> Result<UserPlaylist, RecommenderError> {
    let tree = store.db.open_tree(TREE)?;
    let bytes = tree
        .get(id.as_bytes())?
        .ok_or_else(|| RecommenderError::Invalid(format!("playlist {id} not found")))?;
    bincode::deserialize(&bytes)
        .map_err(|e| RecommenderError::Serialization(format!("playlist {id}: {e}")))
}

fn save(store: &Store, playlist: &UserPlaylist) -> Result<(), RecommenderError> {
    let tree = store.db.open_tree(TREE)?;
    let bytes = bincode::serialize(playlist)
        .map_err(|e| RecommenderError::Serialization(e.to_string()))?;
    tree.insert(playlist.id.as_bytes(), bytes)
        .map_err(RecommenderError::DbError)?;
    Ok(())
}

fn validate_name(name: &str) -> Result<&str, RecommenderError> {
    let name = name.trim();
    if name.is_empty() || name.len() > 200 {
        return Err(RecommenderError::Invalid(
            "playlist name must be 1-200 chars".into(),
        ));
    }
    Ok(name)
}

fn validate_track(track: &PlaylistTrack) -> Result<(), RecommenderError> {
    if !matches!(track.source.as_str(), "yt" | "saavn" | "local") {
        return Err(RecommenderError::Invalid(format!(
            "unknown track source: {}",
            track.source
        )));
    }
    if track.id.is_empty() || track.title.is_empty() {
        return Err(RecommenderError::Invalid(
            "playlist track needs id and title".into(),
        ));
    }
    if track.source == "local" && track.file_path.as_deref().unwrap_or("").is_empty() {
        return Err(RecommenderError::Invalid(
            "local playlist track needs file_path".into(),
        ));
    }
    // Length caps bound sled growth from a hostile client — ids and paths
    // are the only fields that can legitimately be long.
    const MAX_ID: usize = 256;
    const MAX_TEXT: usize = 500;
    if track.id.len() > MAX_ID || track.title.len() > MAX_TEXT {
        return Err(RecommenderError::Invalid("playlist track id/title too long".into()));
    }
    if track.artist.len() > MAX_TEXT || track.album.len() > MAX_TEXT {
        return Err(RecommenderError::Invalid("playlist track metadata too long".into()));
    }
    if track.file_path.as_deref().is_some_and(|p| p.len() > 1024) {
        return Err(RecommenderError::Invalid("playlist track file_path too long".into()));
    }
    Ok(())
}

/// Creates an empty playlist (trimmed name, fresh timestamps).
pub(crate) fn create(store: &Store, name: &str) -> Result<UserPlaylist, RecommenderError> {
    let name = validate_name(name)?.to_string();
    let t = now_ms();
    let playlist = UserPlaylist {
        id: new_id(),
        name,
        created_at: t,
        modified_at: t,
        tracks: Vec::new(),
    };
    save(store, &playlist)?;
    Ok(playlist)
}

/// All playlists, most-recently-modified first (the view's natural order).
pub(crate) fn list(store: &Store) -> Result<Vec<UserPlaylist>, RecommenderError> {
    let tree = store.db.open_tree(TREE)?;
    let mut out = Vec::new();
    for pair in tree.iter() {
        let (_, value) = pair.map_err(RecommenderError::DbError)?;
        match bincode::deserialize::<UserPlaylist>(&value) {
            Ok(p) => out.push(p),
            Err(e) => log::warn!("playlists: dropping undecodable entry: {e}"),
        }
    }
    out.sort_by(|a, b| b.modified_at.cmp(&a.modified_at).then(a.name.cmp(&b.name)));
    Ok(out)
}

pub(crate) fn rename(store: &Store, id: &str, name: &str) -> Result<(), RecommenderError> {
    let name = validate_name(name)?.to_string();
    let mut playlist = load(store, id)?;
    playlist.name = name;
    playlist.modified_at = now_ms();
    save(store, &playlist)
}

pub(crate) fn delete(store: &Store, id: &str) -> Result<(), RecommenderError> {
    let tree = store.db.open_tree(TREE)?;
    let existed = tree.remove(id.as_bytes())?.is_some();
    if !existed {
        return Err(RecommenderError::Invalid(format!("playlist {id} not found")));
    }
    Ok(())
}

/// Appends a track; returns the playlist's new track count.
pub(crate) fn add_track(
    store: &Store,
    id: &str,
    track: PlaylistTrack,
) -> Result<usize, RecommenderError> {
    validate_track(&track)?;
    let mut playlist = load(store, id)?;
    if playlist.tracks.len() >= MAX_TRACKS {
        return Err(RecommenderError::Invalid(format!(
            "playlist is full ({MAX_TRACKS} tracks)"
        )));
    }
    playlist.tracks.push(track);
    playlist.modified_at = now_ms();
    let count = playlist.tracks.len();
    save(store, &playlist)?;
    Ok(count)
}

/// Removes the track at `index`; later rows shift up.
pub(crate) fn remove_track(store: &Store, id: &str, index: usize) -> Result<(), RecommenderError> {
    let mut playlist = load(store, id)?;
    if index >= playlist.tracks.len() {
        return Err(RecommenderError::Invalid(format!(
            "track index {index} out of range"
        )));
    }
    playlist.tracks.remove(index);
    playlist.modified_at = now_ms();
    save(store, &playlist)
}

/// Moves a track from `from` to `to` (both indices into the CURRENT order —
/// the standard drag-and-drop contract: remove at `from`, insert at `to`).
/// `to == len` is the natural "drop at the very end" call: after the removal
/// the list is one shorter, so the target slot shifts down by one — without
/// that adjustment `Vec::insert` would panic the command thread (#71 review).
pub(crate) fn move_track(
    store: &Store,
    id: &str,
    from: usize,
    to: usize,
) -> Result<(), RecommenderError> {
    let mut playlist = load(store, id)?;
    let len = playlist.tracks.len();
    if from >= len || to > len || from == to {
        return Err(RecommenderError::Invalid(format!(
            "move {from}→{to} out of range ({len})"
        )));
    }
    let track = playlist.tracks.remove(from);
    let slot = if to > from { to - 1 } else { to };
    playlist.tracks.insert(slot, track);
    playlist.modified_at = now_ms();
    save(store, &playlist)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tmp_store() -> (tempfile::TempDir, Store) {
        let dir = tempfile::tempdir().expect("tempdir");
        let store = Store::open(dir.path().to_str().unwrap()).expect("open tmp store");
        (dir, store)
    }

    fn track(id: &str, source: &str) -> PlaylistTrack {
        PlaylistTrack {
            source: source.into(),
            id: id.into(),
            title: format!("Song {id}"),
            artist: "Artist".into(),
            album: String::new(),
            duration_secs: Some(200),
            thumbnail: None,
            file_path: (source == "local").then(|| format!("/music/{id}.flac")),
        }
    }

    #[test]
    fn create_rename_delete_roundtrip() {
        let (_dir, store) = tmp_store();
        let p = create(&store, "  Road Trip  ").unwrap();
        assert!(p.id.starts_with("pl-"));
        assert_eq!(p.name, "Road Trip", "name is trimmed");
        assert!(p.tracks.is_empty());

        rename(&store, &p.id, "Desert Drive").unwrap();
        assert_eq!(list(&store).unwrap()[0].name, "Desert Drive");

        delete(&store, &p.id).unwrap();
        assert!(list(&store).unwrap().is_empty());
        assert!(delete(&store, &p.id).is_err(), "double delete errors");
    }

    #[test]
    fn add_remove_move_track_ordering() {
        let (_dir, store) = tmp_store();
        let p = create(&store, "Mix").unwrap();
        assert_eq!(add_track(&store, &p.id, track("a", "yt")).unwrap(), 1);
        add_track(&store, &p.id, track("b", "saavn")).unwrap();
        add_track(&store, &p.id, track("c", "local")).unwrap();

        let got = load(&store, &p.id).unwrap();
        let ids: Vec<&str> = got.tracks.iter().map(|t| t.id.as_str()).collect();
        assert_eq!(ids, vec!["a", "b", "c"]);

        // Drag b to the front.
        move_track(&store, &p.id, 1, 0).unwrap();
        let moved = load(&store, &p.id).unwrap();
        let ids: Vec<&str> = moved.tracks.iter().map(|t| t.id.as_str()).collect();
        assert_eq!(ids, vec!["b", "a", "c"]);

        remove_track(&store, &p.id, 0).unwrap();
        let pruned = load(&store, &p.id).unwrap();
        let ids: Vec<&str> = pruned.tracks.iter().map(|t| t.id.as_str()).collect();
        assert_eq!(ids, vec!["a", "c"]);

        // Drag-to-end: `to == len` in ORIGINAL indices must not panic
        // (#71 review P1) — a moves from the front to after the last row.
        move_track(&store, &p.id, 0, 2).unwrap();
        let dragged = load(&store, &p.id).unwrap();
        let ids: Vec<&str> = dragged.tracks.iter().map(|t| t.id.as_str()).collect();
        assert_eq!(ids, vec!["c", "a"]);

        // Out-of-range ops fail honestly without mutating.
        assert!(remove_track(&store, &p.id, 9).is_err());
        assert!(move_track(&store, &p.id, 0, 9).is_err());
        assert_eq!(load(&store, &p.id).unwrap().tracks.len(), 2);
    }

    #[test]
    fn validation_rejects_bad_input() {
        let (_dir, store) = tmp_store();
        let p = create(&store, "Mix").unwrap();
        assert!(create(&store, "   ").is_err(), "blank name");
        assert!(create(&store, &"x".repeat(201)).is_err(), "overlong name");
        let bad = PlaylistTrack {
            source: "spotify".into(),
            id: "x".into(),
            title: "t".into(),
            artist: String::new(),
            album: String::new(),
            duration_secs: None,
            thumbnail: None,
            file_path: None,
        };
        assert!(add_track(&store, &p.id, bad).is_err(), "unknown source");
        let mut no_path = track("l", "local");
        no_path.file_path = None;
        assert!(add_track(&store, &p.id, no_path).is_err(), "local needs file_path");
        assert!(add_track(&store, "pl-missing", track("a", "yt")).is_err());
    }

    #[test]
    fn duplicate_tracks_are_allowed_and_capped() {
        let (_dir, store) = tmp_store();
        let p = create(&store, "Repeat").unwrap();
        for _ in 0..3 {
            add_track(&store, &p.id, track("same", "yt")).unwrap();
        }
        assert_eq!(load(&store, &p.id).unwrap().tracks.len(), 3);

        // Cap: fill to MAX_TRACKS and expect the next add to fail.
        let (_, big) = tmp_store();
        let bp = create(&big, "Big").unwrap();
        for i in 0..MAX_TRACKS {
            add_track(&big, &bp.id, track(&format!("t{i}"), "yt")).unwrap();
        }
        assert!(add_track(&big, &bp.id, track("overflow", "yt")).is_err());
        assert_eq!(load(&big, &bp.id).unwrap().tracks.len(), MAX_TRACKS);
    }

    #[test]
    fn list_orders_by_modified_desc() {
        let (_dir, store) = tmp_store();
        let a = create(&store, "Alpha").unwrap();
        let b = create(&store, "Beta").unwrap();
        // Touch Alpha last.
        rename(&store, &a.id, "Alpha 2").unwrap();
        let names: Vec<String> = list(&store).unwrap().into_iter().map(|p| p.name).collect();
        assert_eq!(names.first().map(String::as_str), Some("Alpha 2"));
        assert!(names.contains(&"Beta".to_string()));
        assert!(b.id.starts_with("pl-"));
    }
}
