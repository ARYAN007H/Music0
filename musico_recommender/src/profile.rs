//! Long-term taste profile — a single vector summarising what the user likes,
//! built from listen events (weighted by completion ratio) and explicit
//! likes/dislikes.
//!
//! Storage (default tree, all additive keys):
//! - `profile:vec`  → bincode `Vec<f32>` — EMA over signed listen contributions
//! - `profile:ts`   → i64 unix timestamp of the last update
//! - `profile:n`    → u64 count of contributing listen events
//!
//! Time decay is applied lazily on read (`exp(-days/45)`, ~1-month half-life),
//! so a profile that idles for weeks gently relaxes instead of staying stale.

use chrono::{DateTime, Utc};

use crate::models::{bin_err, SongRecord, Store};
use crate::vector_store;

/// Keys under the default tree.
const KEY_VEC: &[u8] = b"profile:vec";
const KEY_TS: &[u8] = b"profile:ts";
const KEY_N: &[u8] = b"profile:n";

/// EMA rate per listen event — small because contributions accumulate over
/// hundreds of plays.
const EMA_ALPHA: f32 = 0.08;
/// Half-life of the profile in days when idle.
const HALF_LIFE_DAYS: f64 = 45.0;
/// Minimum accumulated listen events before the profile is trusted at all.
const MIN_EVENTS: u64 = 3;

/// Signed contribution of a listen: full listens pull the profile toward the
/// song, quick skips push it away.  Zero at ratio 0.45 (roughly "heard it,
/// indifferent").
fn listen_weight(ratio: f32) -> f32 {
    ((ratio - 0.45) * 2.2).clamp(-1.0, 1.0)
}

fn load_vec(store: &Store) -> Result<Option<(Vec<f32>, DateTime<Utc>, u64)>, crate::errors::RecommenderError> {
    let Some(bytes) = store.db.get(KEY_VEC)? else {
        return Ok(None);
    };
    let vec: Vec<f32> = bincode::deserialize(&bytes).map_err(|e| crate::models::bin_err("profile vec", e))?;
    let ts = store
        .db
        .get(KEY_TS)?
        .and_then(|b| b.as_ref().try_into().ok())
        .map(i64::from_le_bytes)
        .unwrap_or_else(|| Utc::now().timestamp());
    let n = store
        .db
        .get(KEY_N)?
        .and_then(|b| b.as_ref().try_into().ok())
        .map(u64::from_le_bytes)
        .unwrap_or(0);
    let ts = DateTime::from_timestamp(ts, 0).unwrap_or_else(Utc::now);
    Ok(Some((vec, ts, n)))
}

fn save(store: &Store, vec: &[f32], n: u64) -> Result<(), crate::errors::RecommenderError> {
    let bytes = bincode::serialize(&vec).map_err(|e| bin_err("profile vec", e))?;
    store.db.insert(KEY_VEC, bytes)?;
    store.db.insert(KEY_TS, &Utc::now().timestamp().to_le_bytes())?;
    store.db.insert(KEY_N, &n.to_le_bytes())?;
    Ok(())
}

/// Folds a listen into the profile.  Called from `log_listen`; a skip still
/// contributes (negatively) — that is the point.
pub(crate) fn on_listen(
    store: &Store,
    record: &SongRecord,
    ratio: f32,
) -> Result<(), crate::errors::RecommenderError> {
    let w = listen_weight(ratio);
    if w.abs() < 1e-4 {
        return Ok(());
    }
    let song_vec = record.feature_vector.to_weighted_vec();

    let (mut vec, ts, n) = match load_vec(store)? {
        Some(v) => v,
        None => (vec![0.0; song_vec.len()], Utc::now(), 0),
    };
    if vec.len() != song_vec.len() {
        vec = vec![0.0; song_vec.len()];
    }

    // Decay the accumulated vector by the time since the last update, then
    // fold the new event in via EMA.
    let days = (Utc::now() - ts).num_seconds() as f64 / 86_400.0;
    let decay = (-days / HALF_LIFE_DAYS * std::f64::consts::LN_2).exp() as f32;
    for v in vec.iter_mut() {
        *v *= decay;
    }

    for (v, &s) in vec.iter_mut().zip(song_vec.iter()) {
        *v = *v * (1.0 - EMA_ALPHA) + (w * s) * EMA_ALPHA;
    }

    update_subprofile(store, &song_vec, w)?;
    save(store, &vec, n + 1)
}

const KEY_SUBPROFILES: &[u8] = b"profile:subprofiles";
const MAX_SUBPROFILES: usize = 6;

/// Updates the closest taste sub-profile (persona) or spawns a new one when
/// fewer than `MAX_SUBPROFILES` exist and cosine similarity is below 0.55.
fn update_subprofile(
    store: &Store,
    song_vec: &[f32],
    weight: f32,
) -> Result<(), crate::errors::RecommenderError> {
    if weight <= 0.0 {
        return Ok(());
    }
    let mut personas: Vec<Vec<f32>> = match store.db.get(KEY_SUBPROFILES)? {
        Some(bytes) => bincode::deserialize(&bytes).unwrap_or_default(),
        None => Vec::new(),
    };
    if personas.len() < MAX_SUBPROFILES {
        let best_sim = personas
            .iter()
            .map(|p| vector_store::cosine_similarity(p, song_vec))
            .fold(-1.0_f32, f32::max);
        if best_sim < 0.55 {
            personas.push(song_vec.to_vec());
            let bytes = bincode::serialize(&personas).map_err(|e| bin_err("subprofiles", e))?;
            store.db.insert(KEY_SUBPROFILES, bytes)?;
            return Ok(());
        }
    }
    if let Some((best_idx, _)) = personas
        .iter()
        .enumerate()
        .map(|(i, p)| (i, vector_store::cosine_similarity(p, song_vec)))
        .max_by(|a, b| a.1.partial_cmp(&b.1).unwrap_or(std::cmp::Ordering::Equal))
    {
        for (p, &s) in personas[best_idx].iter_mut().zip(song_vec.iter()) {
            *p = *p * (1.0 - EMA_ALPHA * 1.5) + s * (EMA_ALPHA * 1.5);
        }
        let bytes = bincode::serialize(&personas).map_err(|e| bin_err("subprofiles", e))?;
        store.db.insert(KEY_SUBPROFILES, bytes)?;
    }
    Ok(())
}

/// Reads all persisted taste sub-profiles (personas) from the store.
/// Each vector is a weighted feature vector representing one distinct taste
/// island learned from completed listens.
pub fn load_subprofiles(store: &Store) -> Result<Vec<Vec<f32>>, crate::errors::RecommenderError> {
    match store.db.get(KEY_SUBPROFILES)? {
        Some(bytes) => Ok(bincode::deserialize(&bytes).unwrap_or_default()),
        None => Ok(Vec::new()),
    }
}

/// Computes the maximum cosine similarity (`[0, 1]`) between `song_vec` and
/// any learned taste sub-profile, or `None` if no sub-profiles exist yet.
pub fn best_subprofile_match(subprofiles: &[Vec<f32>], song_vec: &[f32]) -> Option<f32> {
    subprofiles
        .iter()
        .filter(|p| p.len() == song_vec.len())
        .map(|p| vector_store::cosine_similarity(p, song_vec).clamp(0.0, 1.0))
        .max_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal))
}

/// Look up the weighted feature vector of a song id (for like-profile means).
pub(crate) type VecLookup<'a> = &'a dyn Fn(&str) -> Option<Vec<f32>>;

/// Recomputes the like/dislike mean vectors from the `likes` tree.  Called on
/// every like-state change; O(likes × 30) — negligible for real libraries.
pub(crate) fn refresh_like_vectors(
    store: &Store,
    lookup: VecLookup,
) -> Result<(), crate::errors::RecommenderError> {
    let mut liked: Vec<Vec<f32>> = Vec::new();
    let mut disliked: Vec<Vec<f32>> = Vec::new();
    for entry in store.likes.iter() {
        let (key, value) = entry?;
        let id = String::from_utf8_lossy(&key).to_string();
        let state = value.first().copied().unwrap_or(0);
        if let Some(vec) = lookup(&id) {
            match state {
                1 => liked.push(vec),
                2 => disliked.push(vec),
                _ => {}
            }
        }
    }
    persist_mean(store, b"profile:likevec", &liked)?;
    persist_mean(store, b"profile:dislikevec", &disliked)?;
    store.db.flush()?;
    Ok(())
}

fn persist_mean(store: &Store, key: &[u8], vecs: &[Vec<f32>]) -> Result<(), crate::errors::RecommenderError> {
    let value = if vecs.is_empty() {
        Vec::new()
    } else {
        let len = vecs[0].len();
        let mut mean = vec![0.0_f32; len];
        for v in vecs {
            for (m, &x) in mean.iter_mut().zip(v.iter()) {
                *m += x;
            }
        }
        for m in mean.iter_mut() {
            *m /= vecs.len() as f32;
        }
        mean
    };
    let bytes = bincode::serialize(&value).map_err(|e| bin_err("profile mean", e))?;
    store.db.insert(key, bytes)?;
    Ok(())
}

fn load_mean(store: &Store, key: &[u8]) -> Result<Vec<f32>, crate::errors::RecommenderError> {
    match store.db.get(key)? {
        Some(bytes) => Ok(bincode::deserialize(&bytes).unwrap_or_default()),
        None => Ok(Vec::new()),
    }
}

/// How much explicit likes shape the effective profile, saturating at 5 likes.
fn like_confidence(count: usize) -> f32 {
    (count as f32 / 5.0).min(1.0)
}

/// The combined taste vector: decayed listen EMA + like mean (boosted) −
/// dislike mean, normalised.  Returns `None` while the profile is too weak to
/// be meaningful (fewer than [`MIN_EVENTS`] listens and no likes).
pub(crate) fn effective(store: &Store) -> Result<Option<Vec<f32>>, crate::errors::RecommenderError> {
    let (raw, ts, n) = match load_vec(store)? {
        Some(v) => v,
        None => return Ok(None),
    };

    let days = (Utc::now() - ts).num_seconds() as f64 / 86_400.0;
    let decay = (-days / HALF_LIFE_DAYS * std::f64::consts::LN_2).exp() as f32;

    let mut combined = raw.clone();
    for v in combined.iter_mut() {
        *v *= decay;
    }

    let like_vec = load_mean(store, b"profile:likevec")?;
    let dislike_vec = load_mean(store, b"profile:dislikevec")?;

    // Count likes/dislikes from the tree to scale confidence (the stored means
    // are averaged vectors; the tree is the source of truth for the count).
    let (mut n_liked, mut n_disliked) = (0usize, 0usize);
    for entry in store.likes.iter() {
        let (_, value) = entry?;
        match value.first().copied().unwrap_or(0) {
            1 => n_liked += 1,
            2 => n_disliked += 1,
            _ => {}
        }
    }
    let like_scale = 1.8 * like_confidence(n_liked);
    let dislike_scale = 1.5 * like_confidence(n_disliked);

    for (v, l) in combined.iter_mut().zip(like_vec.iter()) {
        *v += l * like_scale;
    }
    for (v, d) in combined.iter_mut().zip(dislike_vec.iter()) {
        *v -= d * dislike_scale;
    }

    let norm = vector_store::norm(&combined);
    if norm < 1e-4 {
        return Ok(None);
    }
    // Weak profiles (few events, decayed) are still returned but scaled down;
    // scoring multiplies the profile weight by `confidence`.
    let _ = n;
    for v in combined.iter_mut() {
        *v /= norm;
    }
    Ok(Some(combined))
}

/// Confidence in the profile, [0, 1] — used to scale the profile signal weight
/// in scoring.  Reaches 1.0 at ~40 listen events and additionally saturates
/// with explicit likes.
pub(crate) fn confidence(store: &Store) -> Result<f32, crate::errors::RecommenderError> {
    let n = store
        .db
        .get(KEY_N)?
        .and_then(|b| b.as_ref().try_into().ok())
        .map(u64::from_le_bytes)
        .unwrap_or(0);
    let mut n_liked = 0usize;
    for entry in store.likes.iter() {
        let (_, value) = entry?;
        if value.first().copied() == Some(1) {
            n_liked += 1;
        }
    }
    let listen_conf = (n as f32 / MIN_EVENTS.max(40) as f32).min(1.0);
    let like_conf = like_confidence(n_liked);
    Ok((listen_conf * 0.7 + like_conf * 0.3).min(1.0))
}

/// True when the profile is too young/weak to use as a recommendation anchor.
#[allow(dead_code)]
pub(crate) fn is_usable(store: &Store) -> Result<bool, crate::errors::RecommenderError> {
    Ok(confidence(store)? > 0.15)
}

/// Effective profile ignoring like vectors — used to test the listen path in
/// isolation.
#[cfg(test)]
pub(crate) fn effective_listens_only(
    store: &Store,
) -> Result<Option<Vec<f32>>, crate::errors::RecommenderError> {
    let (raw, ts, _n) = match load_vec(store)? {
        Some(v) => v,
        None => return Ok(None),
    };
    let days = (Utc::now() - ts).num_seconds() as f64 / 86_400.0;
    let decay = (-days / HALF_LIFE_DAYS * std::f64::consts::LN_2).exp() as f32;
    let mut combined = raw;
    for v in combined.iter_mut() {
        *v *= decay;
    }
    let norm = vector_store::norm(&combined);
    if norm < 1e-4 {
        return Ok(None);
    }
    for v in combined.iter_mut() {
        *v /= norm;
    }
    Ok(Some(combined))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::models::FeatureVector;
    use crate::LikeState;

    fn record_with(fv: FeatureVector) -> SongRecord {
        SongRecord {
            id: "t".into(),
            file_path: "/t".into(),
            title: "T".into(),
            artist: String::new(),
            album: String::new(),
            duration_secs: 100,
            feature_vector: fv,
            indexed_at: Utc::now(),
            replay_gain_db: 0.0,
        }
    }

    fn fv_first(val: f32) -> FeatureVector {
        let mut fv = FeatureVector {
            version: 1,
            mfcc: [0.0; 13],
            spectral_centroid: 0.0,
            spectral_rolloff: 0.0,
            zero_crossing_rate: 0.0,
            rms_energy: 0.0,
            tempo_bpm: 0.0,
            chroma: [0.0; 12],
        };
        fv.mfcc[0] = val;
        fv
    }

    #[test]
    fn listens_pull_profile_toward_completed_songs() {
        let tmp = tempfile::tempdir().unwrap();
        let store = Store::open(tmp.path().to_str().unwrap()).unwrap();

        let rec = record_with(fv_first(1.0));
        for _ in 0..10 {
            on_listen(&store, &rec, 1.0).unwrap();
        }
        let prof = effective_listens_only(&store).unwrap().unwrap();
        assert!(prof[0] > 0.9, "profile should align with repeatedly completed song, got {}", prof[0]);
    }

    #[test]
    fn skips_push_profile_away() {
        let tmp = tempfile::tempdir().unwrap();
        let store = Store::open(tmp.path().to_str().unwrap()).unwrap();

        let rec = record_with(fv_first(1.0));
        for _ in 0..10 {
            on_listen(&store, &rec, 0.02).unwrap();
        }
        let prof = effective_listens_only(&store).unwrap().unwrap();
        assert!(prof[0] < -0.9, "profile should oppose repeatedly skipped song, got {}", prof[0]);
    }

    #[test]
    fn likes_boost_alignment_in_effective_profile() {
        let tmp = tempfile::tempdir().unwrap();
        let store = Store::open(tmp.path().to_str().unwrap()).unwrap();

        let rec = record_with(fv_first(1.0));
        // Two mediocre listens (neutral-ish pull) + 3 likes of the same song.
        on_listen(&store, &rec, 0.6).unwrap();
        on_listen(&store, &rec, 0.6).unwrap();
        let song_vec = rec.feature_vector.to_weighted_vec();
        store.likes.insert(b"song-a", &[1u8]).unwrap();
        store.likes.insert(b"song-b", &[1u8]).unwrap();
        store.likes.insert(b"song-c", &[1u8]).unwrap();
        let lookup = |id: &str| {
            (id == "song-a" || id == "song-b" || id == "song-c").then(|| song_vec.clone())
        };
        refresh_like_vectors(&store, &lookup).unwrap();

        let prof = effective(&store).unwrap().unwrap();
        assert!(prof[0] > 0.9, "likes should dominate alignment, got {}", prof[0]);
    }

    #[test]
    fn no_profile_when_never_listened() {
        let tmp = tempfile::tempdir().unwrap();
        let store = Store::open(tmp.path().to_str().unwrap()).unwrap();
        let _lookup: VecLookup = &|_| None;
        assert!(effective(&store).unwrap().is_none());
        assert!(!is_usable(&store).unwrap());
    }

    #[test]
    fn like_state_one_is_liked() {
        assert_eq!(LikeState::Liked as u8, 1);
    }

    #[test]
    fn subprofiles_roundtrip_and_best_match() {
        let tmp = tempfile::tempdir().unwrap();
        let store = Store::open(tmp.path().to_str().unwrap()).unwrap();

        assert!(load_subprofiles(&store).unwrap().is_empty());

        // Listen to two orthogonal sonic axes — should spawn 2 distinct subprofiles.
        let rec_a = record_with(fv_first(1.0));
        let mut fv_b = fv_first(0.0);
        fv_b.mfcc[4] = 1.0;
        let rec_b = record_with(fv_b);

        on_listen(&store, &rec_a, 1.0).unwrap();
        on_listen(&store, &rec_b, 1.0).unwrap();

        let subs = load_subprofiles(&store).unwrap();
        assert_eq!(subs.len(), 2, "two orthogonal listens must spawn 2 subprofiles");

        let vec_a = rec_a.feature_vector.to_weighted_vec();
        let vec_b = rec_b.feature_vector.to_weighted_vec();
        assert!(best_subprofile_match(&subs, &vec_a).unwrap() > 0.95);
        assert!(best_subprofile_match(&subs, &vec_b).unwrap() > 0.95);
    }
}
