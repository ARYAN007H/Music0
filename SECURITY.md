# Security policy

Music0 is a streaming-capable, local-first Android and Desktop music player. It has no accounts, no cloud backend, and no telemetry, but it does parse untrusted network responses, run a loopback HTTP/1.1 range server for progressive playback, import external playlists, verify Android APK updates, write downloaded files to disk, and spawn `yt-dlp` on Desktop. This document explains what is in scope, how to report a problem, and what protections already exist.

## Supported versions

| Version | Supported |
|---|---|
| **1.0.x** (`v1.0.2`, latest) | ✅ Security fixes |
| **0.9.x** | ⚠️ Critical fixes at the maintainer's discretion |
| **< 0.9** | ❌ Please upgrade |

Reports against the latest release (`v1.0.2`) and `main` are the most useful.

## Reporting a vulnerability

**Do not open a public issue for a security problem.**

Use GitHub's private vulnerability reporting:

<https://github.com/ARYAN007H/Music0/security/advisories/new>

If that link is unavailable, contact the maintainer
[@ARYAN007H](https://github.com/ARYAN007H) privately through GitHub and ask for
a secure channel. Do not include exploit details in a public issue, pull
request, or discussion.

Please include:

- affected version (`v1.0.2`, commit hash, or `main`);
- operating system and install method (Android APK, Flatpak, AppImage, deb, rpm, tar.gz, dmg, msi, zip, or source build);
- a description of the impact and the attacker's starting position;
- reproduction steps or a minimal proof of concept;
- relevant logs with personal paths, IP addresses, and tokens redacted.

### What to expect

- **Acknowledgement:** best effort within 3 business days.
- **Triage:** a determination of severity and whether the report is in scope.
- **Fix:** coordinated with you; the goal is a patch release, not a promise of a deadline.
- **Credit:** you will be credited in the advisory unless you ask to remain anonymous.
- **No bounty:** this is a personal open-source project and does not run a paid bounty program.

Please give the maintainer a reasonable opportunity to fix the issue before public disclosure (90 days is a good default; active exploitation may justify earlier disclosure).

## Scope

### In scope

- Arbitrary file read/write or path traversal through catalog IDs, cover names, download paths, playlist import files, or JNI arguments (`musico_core/src/mobile.rs`).
- Command injection or argument injection into Desktop `yt-dlp` / `deno` child processes.
- SSRF or forced requests to non-allowlisted hosts through catalog, cover, stream URLs, playlist URL import (`playlist_import.rs`), or loopback `LoopbackHttpMediaSource`.
- Loopback HTTP/1.1 range server (`stream_server.rs`) token predictability, range overflow, or unauthorized host binding.
- Cache poisoning: a crafted response or ID that makes one track resolve to another track's file, or makes a partial download look complete.
- In-app Android APK updater (`UpdateManager.kt`) digest bypass, downgrade, or arbitrary install target issues.
- Memory safety, panics crossing JNI, or resource exhaustion reachable from a malicious network response or playlist file.
- Vulnerabilities in bundled dependencies (`yt-dlp`, `yt-dlp-ejs`, `deno` in Flatpak, Rust crates, Kotlin libraries) that are reachable in normal use.
- Supply-chain issues in the release workflows or published artifacts.

### Out of scope

- **Terms of service and legal exposure** for streaming from public sources. That is a product/legal discussion, not a vulnerability report.
- **Availability of YouTube Music, Piped, Invidious, JioSaavn, or LRCLIB.** Outages and bot walls are expected; report them as bugs, not security issues.
- **Unsigned macOS/Windows builds.** Gatekeeper/SmartScreen warnings are a known distribution limitation documented in [README.md](README.md) and [docs/verify-downloads.md](docs/verify-downloads.md).
- **A local attacker who can already read or write your home directory / app-private storage.** They can modify the binary, the database, or cached files directly.
- **Physical access / unlocked session.**
- **Upstream codec bugs** in Symphonia, Media3/ExoPlayer, CPAL, or the OS audio stack. Report those upstream; if Music0 crashes, we still want the reproduction.

## Threat model

### Assets

- The app's data directories: `sled` database, `settings.json`, `ui_prefs.json`, and stream/cover caches.
- The user's listening history, likes, playlists, and learned taste profile.
- Integrity of downloaded audio files and downloaded Android update APKs.
- The user's network identity (the app contacts third parties directly).

### Trust boundaries

| Component | Trust | Notes |
|---|---|---|
| Compose Multiplatform UI & Rust core | Trusted | Compiled into the app (`:app` / `:desktopApp` + `libmusico_online_lib`) |
| JNI bridge (`musico_core/src/mobile.rs`) | Boundary | Validates IDs (`1..=64` `[A-Za-z0-9_-]`), catches Rust panics, handles OOM |
| Loopback stream server (`stream_server.rs`) & `LoopbackHttpMediaSource` | Local boundary | Binds `127.0.0.1:0`, requires 128-bit `OsRng` token, rejects non-loopback hosts & CRLF |
| InnerTube responses | **Untrusted** | Parsed in `musico_core/src/online/parse.rs` |
| Piped / Invidious / InnerTube Direct responses | **Untrusted** | Stream URLs, host allowlists (`StreamHostPolicy`), redirects (max 5 hops), content types, and sizes validated in `musico_core/src/online/sources.rs` |
| JioSaavn API/CDN | **Untrusted** | IDs charset-gated; decrypted `aac.saavncdn.com` media URLs & `MEDIA_CLIENT`/`API_CLIENT` redirects (max 3 hops) validated in `online/saavn.rs` |
| Playlist URL / file import | **Untrusted** | Strict HTTPS allowlist (`open.spotify.com`, `music.apple.com`), 4 MiB / 500-track cap |
| LRCLIB responses | **Untrusted** | Parsed into lyric lines; no code execution |
| Thumbnails | **Untrusted** | HTTPS + host allowlist + 5 MB cap |
| `yt-dlp` output (Desktop) | **Untrusted** | File existence and minimum size verified; duration probed |
| GitHub release update check | **Untrusted** | Android `UpdateManager.kt` verifies SHA-256 digest & size ceiling before install |

### Protections in place

| Area | Mitigation | Where |
|---|---|---|
| JNI boundary | `catch_unwind` prevents Rust panics crossing JNI; OOM Java exception fallback; strict `videoId` charset check | `musico_core/src/mobile.rs` |
| Loopback HTTP server | Binds strictly `127.0.0.1:0`; 128-bit (32-hex) `OsRng` session tokens; header count cap (`64`) | `musico_core/src/online/stream_server.rs` |
| Desktop loopback client | Strictly rejects any URL other than `http://127.0.0.1:<port>/...` and rejects CR/LF/control chars | `musico_playback/src/http_source.rs` |
| Playlist import | Strict HTTPS + exact host allowlist (`open.spotify.com`, `music.apple.com`), redirect validation, 10 s timeout, 4 MiB / 500-track limit | `musico_core/src/online/playlist_import.rs` |
| Thumbnail URLs | HTTPS-only; hosts must be `ytimg.com`/`*.ytimg.com` or `saavncdn.com`/`*.saavncdn.com`; 5 MB body cap | `musico_core/src/online/stream.rs` (`is_safe_art_url`, `cache_cover_art`) |
| Cover filenames | Sanitizes song IDs to `[A-Za-z0-9:_-]`, preventing traversal | `musico_core/src/cover.rs` (`sanitize_cover_stem`) |
| Stream cache lookup | Exact `{videoId}.` / `{songId}.` match, never fuzzy prefix match | `musico_core/src/online/stream.rs` (`cached_file`) |
| Direct-URL downloads & redirects | `parse_safe_https_url` (no userinfo/non-443 ports/IP literals/private TLDs), `StreamHostPolicy` (`*.googlevideo.com`, `self.host`/safe sibling), `Policy::custom` redirect checks (max 5 hops in `sources.rs`, max 3 hops in `saavn.rs`), `audio/*` MIME, 64 MB / 32 MB caps, 2 MB metadata cap, ≥ 10 KB verify, `.part` → atomic rename | `musico_core/src/online/sources.rs`, `saavn.rs`, `stream_server.rs` |
| `yt-dlp` (Desktop) | Spawned with `Command` arguments (no shell), constant base URL, `--no-playlist`, nulled stdout/stderr, timeout kill + reap, cap of 3 | `musico_core/src/online/stream.rs` |
| Cache pruning | mtime LRU, 80% hysteresis target, stale `.part`/`.ytdl`/`.tmp` sweep after 24 h | `musico_core/src/online/stream.rs` |
| Android updater | Verifies release asset SHA-256 digest (`sha256:`) and enforces download size ceiling before launching package installer | `android/shared/src/androidMain/.../updater/UpdateManager.kt` |
| Airplane Mode | `ensure_online()` fast-fails network commands while allowing cached playback | `musico_core/src/mobile.rs` |
| SQL/NoSQL injection | No SQL; sled keys are typed/binary or JSON values; no user-supplied query language | `musico_recommender` |

### Known limitations

- **Unsigned Desktop installers.** macOS and Windows artifacts are unsigned; verify `SHA256SUMS.txt` from the release ([docs/verify-downloads.md](docs/verify-downloads.md)) or build from source. Android APKs are signed in CI using `ANDROID_KEYSTORE_B64`.
- **No Desktop single-instance guard yet.** Compose Desktop does not yet prevent a second launch from opening simultaneously; the second process hits sled's file lock and runs without DB access.
- **Public proxy instances are untrusted infrastructure.** Piped and Invidious instances can see your IP and requested video IDs. Size, content-type, and URL checks bound the response surface, not proxy privacy.
- **`yt-dlp` and `deno` are external dependencies** (bundled inside Flatpak; user-installed elsewhere on Desktop). Their security posture and update cadence are upstream-managed.

## Privacy

- **No accounts, analytics, telemetry, or crash upload.**
- Search queries, video IDs, and artist/title metadata are sent to the catalog sources you use (YouTube Music, Piped/Invidious instances, JioSaavn, LRCLIB) and playlist URL hosts when importing (`open.spotify.com`, `music.apple.com`).
- Listening history, likes, playlists, and taste vectors stay in the local `sled` database. They are never uploaded by this project.
- Desktop logs write to `stderr` (`env_logger`) and Android logs write to Logcat (`musico-native`); logs may contain search queries, track titles, or URLs—redact sensitive details before attaching them to a public issue.
- `MUSICO_SAAVN_API` and `MUSICO_LRCLIB_API` let you point at a proxy/mirror; that proxy then sees the same requests.

## Secure development practices

- Network, file, JNI, and child-process boundaries are reviewed with the same care as the feature itself.
- New sources must keep the trust gates (HTTPS, content type, size cap, atomic rename, minimum-size verify) and add hermetic failover tests.
- New parsing code belongs in `musico_core/src/online/parse.rs` and must tolerate malformed items without panicking.
- Run the opt-in live tests before a release:
  `cargo test -p musico_core live_ -- --ignored --nocapture`.
- Dependency review: `cargo tree`, `cargo audit` (if installed), and a quick check of new crates' maintenance/license status are expected for new dependencies.
- Release artifacts are built by GitHub Actions and published with `SHA256SUMS.txt`; the publish job runs only for tags.

## Safe harbor

Security research conducted in good faith under this policy is welcome. Please:

- test only against your own installation and data;
- do not access, modify, or exfiltrate another person's data;
- do not run denial-of-service tests against public services or third-party instances;
- do not publicly disclose the issue before a fix is available;
- comply with applicable law.

The maintainer will not pursue legal action for good-faith research that follows these rules.
