# 0004. A local-only recommender in its own crate

- Status: Accepted
- Recorded: 2026-11-05
- Deciders: maintainers

## Context

The player needs recommendations: Home picks, Smart Radio, autoplay, artist and
cluster affinity, context-aware discovery, and taste statistics. The product
constraints are unusual:

- single-user, no account system, no cloud backend, no telemetry;
- must work offline and on the first run after a scan;
- listening history, likes, and playlists are private and stay on the machine;
- the same engine must build for desktop and Android without Tauri or a
  network dependency;
- a recommendation pass must not block the audio event loop or the UI.

A cloud recommender would contradict the privacy model and require a service.
Running a model in the app crate would couple taste logic to Tauri and make it
hard to test. A general-purpose database (SQLite/Postgres) would add a
dependency and a query layer for data that is a handful of key-value trees.

The data is naturally local and modest: audio feature vectors per song
(~0.5 KB in memory per record), play events, per-song/artist/cluster scores,
session state, likes, and context buckets.

## Decision

Implement the recommender as a **separate Rust crate** (`musico_recommender`)
with **no Tauri and no network dependency**, backed by **sled** and **bincode**.

### Storage

`MusicRecommender` opens one sled database with named trees:
`songs`, `events`, `sessions`, `scores`, `likes`, `artists`, `context`,
`clusters`, plus the app-facing `lib:album`, `lib:artist`, `lib:track`,
`lib:playlists`, `playlists`, and `downloads` trees.

- Each domain gets its own tree so scans never cross domains.
- Persisted structs use bincode and are **frozen**; adding a field breaks
  decoding because bincode is positional. New behavior gets a new tree/key
  namespace instead.
- `open_tree` creates missing trees, so adding a domain is not a migration.
- A one-time `db/.stable-ids` marker clears the pre-stable-id index when the id
  scheme changed.

### Runtime

- On open, the engine builds an in-memory `SongCache` of records, weighted
  vectors, cluster assignments, and like states. Scoring then does zero sled
  reads.
- Signals are combined with adaptive weights (similarity, affinity, profile,
  session, artist, cluster, context, discovery, exploration) and diversified
  with MMR.
- Session state and a skip streak live in the `sessions` tree; play events feed
  per-song, per-artist, per-cluster, per-context, and long-term profile scores.
- Feature extraction runs in the scanner's background indexer, not on the UI
  thread.
- The crate is pure Rust; the Tauri app wraps it in
  `Arc<Mutex<MusicRecommender>>` and never exposes sled handles to the webview.

### Isolation

- No HTTP client, no analytics, no remote model.
- Tests open a temporary directory; they never touch the real DB.
- If the DB cannot open, the app starts with `recommender = None` and the UI
  degrades to deterministic fallbacks.

## Consequences

Positive:

- Privacy is structural: there is no code path that can upload taste data.
- Recommendations work offline and on a fresh install after indexing.
- The crate is testable without Tauri, audio devices, or the network.
- The in-memory cache keeps a recommendation pass fast enough for autoplay and
  the 50 ms event loop.
- sled's tree isolation and atomic batches fit the write patterns (event
  append, score update, session save).
- Android builds reuse the same engine.

Negative:

- No cross-device sync. Reinstalling or deleting `db/` loses likes, playlists,
  history, and learned weights.
- bincode's frozen layouts make schema evolution expensive; new fields require
  a new tree/key or an explicit migration.
- The whole library is held in memory (~0.5 KB per song). A 10k-track library is
  a few megabytes; a 1M-track library is not a target.
- Quality is bounded by local history. A new user gets generic/discovery-heavy
  picks until enough events accumulate.
- No server-side model updates or collaborative filtering across users.
- Play events have no automatic retention in v0.7.0; the tree grows with
  listening history.

## Alternatives considered

- **Cloud recommendation service.** Rejected: violates the no-account,
  no-telemetry model, adds cost and a network dependency, and breaks offline
  use.
- **SQLite.** Rejected: a second storage dependency and SQL schema/migrations
  for data that is naturally key-value. sled's embedded, pure-Rust model and
  atomic batches fit better.
- **JSON files.** Rejected: no atomic multi-key updates, no efficient range
  scans, and rewriting the whole library on every event does not scale.
- **Put the engine in `src-tauri`.** Rejected: couples taste logic to Tauri,
  makes tests pull in the app shell, and prevents reuse on Android without the
  desktop surface.
- **A pre-trained model shipped with the app.** Rejected: adds a large binary
  artifact, a model runtime, and a training pipeline the project cannot
  maintain; the feature-vector approach is explainable and small.
- **Use an external API (Last.fm/Spotify).** Rejected: requires credentials and
  network, and sends listening data to a third party.

## References

- `musico_recommender/src/models.rs` (trees, frozen types)
- `musico_recommender/src/recommender.rs`
- `musico_recommender/src/lib.rs` (`MusicRecommender::new`)
- `musico_recommender/src/library.rs`, `user_playlists.rs`, `batch.rs`
- `src-tauri/src/state.rs` (`init_recommender`, `AppState`)
- [docs/data-storage.md](../data-storage.md)
- [ARCHITECTURE.md](../../ARCHITECTURE.md)
