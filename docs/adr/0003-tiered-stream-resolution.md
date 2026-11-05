# 0003. Tiered, local-first stream resolution

- Status: Accepted
- Recorded: 2026-11-05
- Deciders: maintainers

## Context

The app plays YouTube Music catalog tracks and JioSaavn tracks without a
backend. YouTube extraction is the fragile part:

- `yt-dlp` is reliable when it works, but it spawns a process, needs frequent
  updates, can hang on a rate-limited IP, and does not exist on Android.
- Public Piped/Invidious instances can return proxied audio URLs in
  ~0.2–2 seconds without extraction leaving the user's IP. The instance
  ecosystem churns, so any single host is unreliable.
- JioSaavn exposes direct AAC URLs (96/160/320 kbps) after a DES-encrypted URL
  handshake. When a track matches, this is the fastest and highest-quality path,
  and it works without `yt-dlp`.
- A first click must either start audio or fail honestly. An unbounded retry
  loop that pins "resolving" for minutes is worse than an error.

The app also has to work on Android, where no child process is available, and
on desktops where the user may not have installed `yt-dlp`.

## Decision

Resolve streams through an explicit ladder, from cheapest/most reliable to most
expensive, with a total budget at every step.

### Dispatch

`OnlineTrack.source` selects the path before any network work:

- `""` or `"yt"` → YouTube ladder;
- `"saavn"` → JioSaavn direct path;
- anything else → immediate error.

Unknown source strings never fall through to yt-dlp with a foreign id.

### YouTube ladder

1. **Exact cache hit.** `<videoId>.` prefix in the stream cache. No process, no
   network.
2. **Spawn the cover fetch** so art lands before the `Playing` event.
3. **JioSaavn cross-source match.** Search `"<title> <artist>"`, accept the
   first result where titles contain each other, an artist token matches, and
   durations differ by ≤ 4 s. This turns many YouTube plays into a direct CDN
   download in a few hundred milliseconds.
4. **Direct-URL fast path.** Piped → Invidious → InnerTube Direct under one
   **10 s** shared wall-clock budget. No child process; works without yt-dlp;
   every failure logs and falls through.
5. **yt-dlp.** Interactive: 1 attempt, **90 s total including slot wait**.
   Background: 2 attempts × **120 s**, 600 ms apart. A process-wide semaphore
   caps concurrent children at **3**.

### Publication and bounds

- Every download writes `.part`, verifies a minimum size (10 KB), then renames
  atomically. A killed process cannot publish a truncated file as a cache hit.
- Source bodies are capped (64 MB fast path, 32 MB Saavn) and size-checked.
- Per-video/per-id locks serialize duplicate resolves; the second caller finds
  the fresh cache entry.
- The stream cache is LRU-pruned to a configurable cap (1 GB default, 80%
  target), with 24-hour sidecar sweeping.

The ordering is deliberate: the fast path spends its budget **before** the
yt-dlp budgets start, so a dead instance ecosystem costs ≤ 10 s and nothing
else. yt-dlp remains the honest last resort.

## Consequences

Positive:

- Common tracks resolve instantly from cache or Saavn; many others resolve in
  seconds from a proxy, without spawning a process.
- Desktop works without `yt-dlp` when the direct sources are alive; Android
  works with no process at all.
- Every failure is bounded and returns a user-readable reason.
- The cache is safe to kill at any point: sidecars are swept, and only verified
  files are visible.
- Interactive latency has a worst-case bound the UI can explain.

Negative:

- Five code paths to test and maintain, with different failure modes.
- Cross-source matching is a heuristic. A wrong take can play, and there is no
  per-track override in v0.7.0.
- Public instance churn causes periodic breakage that no test can fully
  prevent; live tests are opt-in, not CI.
- A wrong or slow instance can consume part of the 10 s budget before a good
  one is tried (mitigated by EWMA health ordering and 15-minute demotion).
- Android cannot use yt-dlp, so a track with no direct source is unplayable
  there.
- Quality is source-bounded: `lossless` means "best available", not a lossless
  master.

## Alternatives considered

- **yt-dlp only.** Rejected: too slow, fails on rate-limited IPs, requires a
  binary, and does not exist on Android.
- **Piped/Invidious only.** Rejected: instance churn makes it strictly less
  reliable than yt-dlp over time, and some tracks are missing from proxies.
- **A self-hosted extraction backend.** Rejected: the project is local-first,
  has no server, and a proxy would centralize rate limits, cost, and legal
  exposure.
- **Embedded YouTube extractor in Rust.** Rejected: a moving target with a
  large maintenance surface; the pure-Rust InnerTube Direct source covers the
  no-binary case without owning full extraction.
- **Unbounded retries with a spinner.** Rejected: it hides failure and makes
  the app feel broken. A bounded, honest error is better.
- **Try yt-dlp first, then proxies.** Rejected: yt-dlp can hang for the whole
  budget on exactly the IPs where proxies would have succeeded instantly.

## References

- `src-tauri/src/online/stream.rs`
- `src-tauri/src/online/sources.rs`
- `src-tauri/src/online/saavn.rs`
- [docs/online-stack.md](../online-stack.md)
- [docs/troubleshooting.md](../troubleshooting.md#5-online-problems)
