# 0006. Progressive playback via a loopback HTTP/1.1 range server

- Status: Accepted
- Recorded: 2026-11-27
- Deciders: maintainers

## Context

Under [ADR-0003](0003-tiered-stream-resolution.md), interactive track resolution (`resolve_track`) downloaded the entire audio stream to a `.part` file and atomically renamed it before returning a local file path to the player. On cold starts or slower connections, users waited 5–10+ seconds for the whole track to download before the first frame of audio played.

Both production audio engines—Android's Media3 `ExoPlayer` and Desktop's Rust `musico_playback` (Symphonia)—can seek and decode progressively over HTTP/1.1 byte ranges, whereas upstream sources (InnerTube Direct, Piped, Invidious, JioSaavn, and Desktop `yt-dlp -g`) yield transient HTTP streams that need header injection, retry/hedging, and concurrent disk caching.

## Decision

Introduce an in-process **loopback HTTP/1.1 range server** (`musico_core/src/online/stream_server.rs`) and a matching **Desktop loopback `MediaSource`** (`musico_playback/src/http_source.rs`) for interactive playback (`allowStream = true`):

- **Loopback-only binding & auth:** Binds strictly to `127.0.0.1:0` (ephemeral port) and serves `/stream/<token>` where `<token>` is a 128-bit (32-hex) CSPRNG secret from `rand::rngs::OsRng`.
- **Single-pump write-through cache:** A background pump streams upstream bytes into `{stem}.{seq}.part`, wakes waiting HTTP range readers as bytes arrive, and atomically commits the completed file (plus `{stem}.meta.json` sidecar) to the stream cache on EOF.
- **Low startup gate:** Interactive playback unblocks as soon as `INITIAL_READY_BYTES = 16 KiB` (`INITIAL_READY_TIMEOUT = 6 s`) are buffered, paired with hedged direct-source opening (`900 ms` hedge delay, batch size 2) and parallel JioSaavn-vs-Direct racing.
- **Desktop progressive Symphonia source:** `LoopbackHttpMediaSource` validates URLs strictly to `http://127.0.0.1:<port>/...` (rejecting non-loopback hosts and CRLF header injection), supports `Read + Seek`, and feeds Symphonia's `64 KiB` `MediaSourceStream` buffer.
- **Whole-file fallback:** Background pre-warming and offline downloads (`allowStream = false`) continue to use whole-file resolution; `yt-dlp` on Desktop attempts `yt-dlp -g` progressive streaming first and falls back to whole-file `run_yt_dlp`.

## Consequences

Positive:

- Interactive cold-start click-to-audio latency drops from full-track download time to first-chunk arrival (~`16 KiB` gate + `500 ms` Media3 `DefaultLoadControl` / `64 KiB` Desktop prefill).
- Tracks played to completion still land atomically in the disk cache without downloading twice.
- Android Media3 and Desktop Symphonia share one Rust streaming/caching pipeline.

Negative:

- Adds an in-process TCP listener on `127.0.0.1` and concurrent `.part` file / `Range` state management.
- If the upstream stream aborts mid-track, playback stalls cleanly rather than silently publishing a partial cache hit.
- Non-seekable or header-less streams must still fall back to whole-file download when range/stream opening fails.

## Alternatives considered

- **Pass upstream CDN URLs directly to ExoPlayer / Symphonia.** Rejected: loses concurrent disk caching, Rust header/proxy/hedging logic, and uniform Desktop/Android failover.
- **Custom JNI byte-buffer streaming callback into Kotlin/ExoPlayer.** Rejected: requires a custom Media3 `DataSource` + JNI per-chunk allocations and solves nothing for Desktop Symphonia.

## References

- `musico_core/src/online/stream_server.rs`
- `musico_core/src/online/stream.rs` (`resolve_track_streaming`, `open_yt_dlp_stream`)
- `musico_core/src/online/sources.rs` (`try_sources_open_stream_hedged`)
- `musico_playback/src/http_source.rs` (`LoopbackHttpMediaSource`)
- [docs/online-stack.md](../online-stack.md)
- [ADR-0003](0003-tiered-stream-resolution.md)
