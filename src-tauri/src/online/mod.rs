//! # Musico Online — InnerTube (YT Music) catalog + yt-dlp stream resolution
//!
//! Module map (ROADMAP Phase 0 — each concern lives in exactly one file):
//! - [`client`]  — HTTP, InnerTube auth/version, retry & throttle choke point
//! - [`models`]  — data types crossing the IPC boundary
//! - [`parse`]   — ALL response→model parsing (the only drift-sensitive file)
//! - [`search`]  — search + continuations + TTL result cache
//! - [`radio`]   — related tracks via the `next` endpoint
//! - [`saavn`]   — JioSaavn catalog: independent search + 320 kbps direct URLs
//! - [`stream`]  — yt-dlp resolve/download/verify/prune + duration/cover polish
//! - [`sources`] — Piped/Invidious direct-URL fast path (tried before yt-dlp)
//!
//! Search and radio talk to YouTube Music's InnerTube API with the `WEB_REMIX`
//! client identity (the same technique Metrolist/InnerTune use).  The client
//! version is **auto-refreshed** from the music.youtube.com homepage (24 h TTL)
//! so response-shape drift doesn't come with a frozen version stamp.
//! Playback resolves a stream via the system `yt-dlp` binary, downloads the
//! AAC (m4a) variant into a size-capped disk cache, probes the real duration
//! and thumbnail into the cover cache, and hands the file to the local
//! playback engine — the audio path stays the proven offline decoder stack.
//!
//! Robustness inventory:
//! - one retry on transient search/next failures; 5-min TTL search cache
//! - per-video download locks (no duplicate concurrent downloads)
//! - downloaded files verified (exists, > 10 KB) and duration-probed
//! - cache pruning (LRU by mtime, default 1 GB, `MUSICO_ONLINE_CACHE_MB` env override)
//! - radio picks avoid the last 12 played online tracks (static ring buffer)
//!
//! The online layer lives in this standalone repository so the offline
//! player stays independent of third-party catalog code.

pub mod catalog;
pub mod client;
pub mod download;
pub mod lrclib;
pub mod lyrics;
pub mod models;
pub mod parse;
pub mod radio;
pub mod saavn;
pub mod search;
pub mod sources;
pub mod stream;

pub use catalog::{browse, browse_continuation, genres, home_feed};
pub use lrclib::{fetch_synced_lyrics, SyncedLyricsDto};
pub use lyrics::fetch_lyrics;
pub use models::{BrowsePage, OnlineTrack, SearchResultPage, ShelfItem};
pub use radio::{radio_tracks, remember_radio_play};
pub use saavn::{search_songs, SEARCH_MERGE_LIMIT};
pub use search::{search, search_suggestions};
pub use stream::{
    cache_usage_bytes, clear_cache, prune_cache_to_cap, resolve_stream, resolve_track, to_song_info,
    yt_dlp_available,
};
#[allow(unused_imports)]
use stream::cached_file;

#[cfg(test)]
mod live_tests {
    use super::*;

    /// End-to-end live check against YouTube Music.  Opt-in (hits the network):
    ///   MUSICO_LIVE_TESTS=1 cargo test -p musico-online live_ -- --ignored --nocapture
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_search_and_radio() {
        let page = search("daft punk one more time", false, None).expect("search");
        assert!(!page.tracks.is_empty(), "live search returned 0 results");
        let first = &page.tracks[0];
        assert!(!first.video_id.is_empty() && !first.title.is_empty());
        println!("live search: {} results, cont={:?}", page.tracks.len(), page.next_continuation.is_some());

        // Repeat search must be instant (in-memory TTL cache; C6-style
        // "cached typeahead" budget).  Also proves cache-key normalization.
        let t = std::time::Instant::now();
        let again = search("  DAFT   punk ONE MORE TIME ", false, None).expect("cached search");
        assert!(t.elapsed() < std::time::Duration::from_millis(100),
            "cached search took {:?}", t.elapsed());
        assert_eq!(again.tracks.len(), page.tracks.len(), "variant query hit the same cache entry");
        println!("repeat (case/whitespace variant) search: {:?} — cache hit", t.elapsed());

        if let Some(tok) = &page.next_continuation {
            let page2 = search("daft punk one more time", false, Some(tok)).expect("continuation");
            println!("continuation: {} more results", page2.tracks.len());
        }

        let radio = radio_tracks(&first.video_id).expect("radio");
        assert!(radio.len() >= 3, "radio returned {} tracks", radio.len());
        assert!(radio.iter().all(|t| t.video_id != first.video_id), "seed excluded");
        println!("radio: {} related tracks", radio.len());
    }

    /// Live reproduction of the "lyrics are a mix of english and chinese"
    /// bug: Backbone (Hardy Sandhu) with NO duration — the pre-gate code
    /// handed the shortest same-titled song worldwide to the lyric stage.
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_lyrics_durationless_no_foreign_song() {
        // Shelf-built track: duration unknown.
        let dto = crate::online::fetch_synced_lyrics("Hardy Sandhu", "Backbone", None);
        if let Some(d) = dto {
            let text = d
                .synced
                .first()
                .map(|l| l.text.clone())
                .or(d.plain.clone())
                .unwrap_or_default();
            println!("durationless match first line: {text}");
            // Chinese characters would mean a foreign song won.
            let foreign = text.chars().any(|c| ('\u{4E00}'..='\u{9FFF}').contains(&c));
            assert!(!foreign, "foreign-language lyrics leaked into a Hindi song");
        } else {
            println!("durationless: no plausible match — falls back to YTM plain (correct)");
        }
        // With the real duration, the exact path still works.
        let dto = crate::online::fetch_synced_lyrics("Hardy Sandhu", "Backbone", Some(199));
        println!("with duration: {}", if dto.is_some() { "matched" } else { "none" });
    }

    /// Live updater check: the real GitHub releases endpoint, the real
    /// asset-selection and version comparison the in-app updater runs.
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_update_check() {
        // Mirrors mobile.rs's dispatch arm (the command lives behind the
        // android-gated module; the logic under test is this same chain).
        let client = reqwest::blocking::Client::builder()
            .user_agent("Music0-Android/0.9.1")
            .timeout(std::time::Duration::from_secs(15))
            .build()
            .expect("client");
        let reply: serde_json::Value = client
            .get("https://api.github.com/repos/ARYAN007H/Music0/releases/latest")
            .send()
            .expect("fetch")
            .error_for_status()
            .expect("status")
            .json()
            .expect("json");
        let tag = reply["tag_name"].as_str().expect("tag_name");
        let asset = reply["assets"].as_array().expect("assets").iter().find(|a| {
            a["name"].as_str().map(|n| n.starts_with("musico-online-native") && n.ends_with(".apk")).unwrap_or(false)
        });
        assert!(tag.starts_with('v'), "tag {tag} not v-prefixed");
        assert!(asset.is_some(), "no musico-online-native APK asset on the latest release");
        println!("latest={tag} asset_url={}", asset.unwrap()["browser_download_url"]);
    }

    /// Live end-to-end personalization pipeline — exactly what the Kotlin
    /// onboarding drives: artist search → radio seed → related tracks, and
    /// language style-card match → genre browse. Verifies the app's
    /// "Because you like …" and "… picks" shelves CAN build on the wire.
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_personalization_pipeline() {
        for artist in ["Arijit Singh", "Travis Scott", "The Weeknd"] {
            let page = search(artist, false, None).expect("artist search");
            assert!(!page.tracks.is_empty(), "search '{artist}' empty");
            let mut built = false;
            for seed in page.tracks.iter().take(4) {
                let radio = radio_tracks(&seed.video_id).expect("radio");
                if radio.len() >= 4 {
                    println!(
                        "because you like {artist}: {} tracks (seed {})",
                        radio.len(),
                        seed.title
                    );
                    built = true;
                    break;
                }
            }
            assert!(built, "no radio shelf seeded for {artist} from top-4 search hits");
        }

        let styles = genres().expect("genres");
        let hindi = styles
            .iter()
            .find(|s| s.title.contains("Hindi"))
            .expect("Hindi style card exists");
        let page = browse(hindi.browse_id.as_deref().expect("style browseId"), hindi.params.as_deref())
            .expect("language browse");
        let song_shelf = page
            .shelves
            .iter()
            .find(|s| s.items.iter().any(|i| i.video_id.is_some()))
            .expect("Hindi page has a playable shelf");
        println!("hindi picks: '{}' {} items", song_shelf.title, song_shelf.items.len());

        // Home continuation: report whether the section list paginates at
        // all (drives the app's infinite-scroll strategy).
        let home = home_feed().expect("home");
        println!(
            "home: {} shelves, next_continuation={:?}",
            home.shelves.len(),
            home.next_continuation
        );
    }

    /// Live resolve of a non-saavn-matched track (Japanese title, the
    /// J-POP shelf class): the JioSaavn cross-source fast path misses these,
    /// so playback depends on the InnerTube-direct director. Prints the
    /// per-profile fast-path outcome — this is the exact wire behavior the
    /// phone sees for the "not able to play" bug report.
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_resolve_non_saavn_track() {
        let page = search("Shinunoga E-Wa", false, None).expect("search");
        let track = page.tracks.first().expect("search hit").clone();
        println!(
            "target: {} — {} ({})",
            track.title, track.artist, track.video_id
        );
        match super::sources::try_fast_path(&track.video_id) {
            Ok(path) => println!(
                "fast path SERVED: {} ({} KB)",
                path.display(),
                std::fs::metadata(&path).map(|m| m.len() / 1024).unwrap_or(0)
            ),
            Err(e) => println!("fast path FAILED: {e}"),
        }
    }

    /// Live album detail page: header + playable tracklist (C2.2 path).
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_album_detail() {
        let page = browse("MPREb_bKCCaVsjXZ8", None).expect("album browse");
        let hdr = page.header.expect("album header parses");
        assert!(!hdr.title.is_empty());
        let playable: usize = page.shelves.iter().map(|s| s.items.iter().filter(|i| i.video_id.is_some()).count()).sum();
        assert!(playable >= 5, "album page returned {playable} playable tracks");
        println!("album: {:?} | {} playable tracks | {} shelves", hdr.title, playable, page.shelves.len());
    }

    /// Live genre grid: the Browse tab categories.
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_genres() {
        let items = genres().expect("genres");
        assert!(items.len() >= 50, "genres returned {} categories (ROADMAP C2.4 gate)", items.len());
        assert!(items.iter().all(|i| i.browse_id.is_some() && !i.title.is_empty()));
        println!("genres: {} categories — first: {:?}", items.len(), items[0].title);
    }

    /// Live lyrics: the InnerTube two-step lyrics flow must return real text
    /// for a mainstream song (unsynced, Musixmatch-backed).
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_lyrics_fetch() {
        let lyrics = fetch_lyrics("dQw4w9WgXcQ").expect("lyrics for a mainstream song");
        assert!(lyrics.len() > 120, "lyrics suspiciously short: {} chars", lyrics.len());
        println!("lyrics: {} chars — first line: {:?}",
            lyrics.len(), lyrics.lines().next().unwrap_or(""));
    }

    /// Live home feed: the Listen Now shelves (editorial + personalized).
    #[test]
    #[ignore = "network test — run with MUSICO_LIVE_TESTS=1"]
    fn live_home_feed() {
        let page = home_feed().expect("home feed");
        assert!(page.shelves.len() >= 2, "home returned {} shelves", page.shelves.len());
        let items: usize = page.shelves.iter().map(|s| s.items.len()).sum();
        assert!(items >= 10, "home returned only {items} items");
        println!("home: {} shelves, {items} items — first: {:?}", page.shelves.len(), page.shelves[0].title);
    }

    /// C4.2 gate: a 15-track batch downloads unattended with ≤2 concurrent
    /// resolves, all tracks landing done in the persistent batch record.
    #[test]
    #[ignore = "network + yt-dlp heavy — run with MUSICO_LIVE_TESTS=1"]
    fn live_batch_download_15_tracks() {
        // Seed from SEARCH, not radio: radio shuffles and can return only
        // multi-hour mixes (0 candidates in the duration window), while the
        // songs chip reliably returns normal-length tracks with durations.
        let page = search("classic rock hits", false, None).expect("search");
        let picked: Vec<(String, String)> = page
            .tracks
            .iter()
            .filter(|t| t.duration_secs.is_some_and(|d| (60..=600).contains(&d)))
            .take(15)
            .map(|t| (t.video_id.clone(), t.title.clone()))
            .collect();
        assert!(picked.len() >= 10, "need at least 10 normal-length tracks, got {}", picked.len());

        let dir = tempfile::tempdir().expect("tempdir");
        let store = musico_recommender::models::Store::open(dir.path().to_str().unwrap())
            .expect("store");
        let id = "batch-live-15";
        musico_recommender::batch::create(&store, id, "Live gate", &picked)
            .expect("create batch");

        let max_concurrent = std::sync::Arc::new(std::sync::atomic::AtomicUsize::new(0));
        let in_flight = std::sync::Arc::new(std::sync::atomic::AtomicUsize::new(0));
        let mc = max_concurrent.clone();
        let inf = in_flight.clone();
        let resolve = move |vid: &str| {
            inf.fetch_add(1, std::sync::atomic::Ordering::SeqCst);
            mc.fetch_max(inf.load(std::sync::atomic::Ordering::SeqCst), std::sync::atomic::Ordering::SeqCst);
            let r = resolve_stream(vid, std::time::Duration::from_secs(240)).map(|_| ());
            inf.fetch_sub(1, std::sync::atomic::Ordering::SeqCst);
            r
        };
        let job = musico_recommender::batch::run(&store, id, &resolve, &|_| {})
            .expect("batch run")
            .expect("job");

        // Clean the cache FIRST — an assert must not skip cleanup and leave
        // residue for the other live tests.
        for vid in picked.iter().map(|(v, _)| v) {
            if let Some(p) = cached_file(vid) {
                let _ = std::fs::remove_file(p);
            }
        }

        println!(
            "batch: {}/{} done, {} failed, max concurrent {}",
            job.done_count(),
            job.tracks.len(),
            job.failed_ids().len(),
            max_concurrent.load(std::sync::atomic::Ordering::SeqCst),
        );
        assert!(
            max_concurrent.load(std::sync::atomic::Ordering::SeqCst) <= musico_recommender::batch::BATCH_CONCURRENCY,
            "concurrency cap violated"
        );
        assert_eq!(job.done_count(), job.tracks.len(), "all tracks must download");
    }

    /// C3.2 gate: advancing into an already-cached online track must take
    /// < 1 s — the pre-resolve worker turns the advance into exactly this
    /// cache hit.  Downloads one real track, then times the cache-hit path.
    #[test]
    #[ignore = "network + yt-dlp test — run with MUSICO_LIVE_TESTS=1"]
    fn live_cached_resolve_under_a_second() {
        let tracks = radio_tracks("dQw4w9WgXcQ").expect("radio tracks");
        // Duration-bounded pick: radio shuffles and can return multi-hour
        // mixes — resolve a normal-length track or the gate hangs ~480 s.
        let vid = tracks
            .iter()
            .find(|t| t.duration_secs.is_some_and(|d| (60..=600).contains(&d)))
            .or_else(|| tracks.first())
            .expect("at least one related track")
            .video_id
            .clone();
        let t0 = std::time::Instant::now();
        resolve_stream(&vid, std::time::Duration::from_secs(240)).expect("first resolve");
        let cold = t0.elapsed();
        let t1 = std::time::Instant::now();
        resolve_stream(&vid, std::time::Duration::from_secs(240)).expect("cached resolve");
        let warm = t1.elapsed();
        println!("cold resolve {cold:?}, cached {warm:?}");
        assert!(
            warm < std::time::Duration::from_secs(1),
            "cached resolve took {warm:?} — C3.2 gate is < 1 s"
        );
        // Leave no residue: the unit prune test assumes a clean cache dir.
        let path = cached_file(&vid);
        if let Some(p) = path {
            let _ = std::fs::remove_file(p);
        }
    }
}
