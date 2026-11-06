# Security policy

Musico Online is a local-first desktop music player. It has no accounts, no
cloud backend, and no telemetry, but it does parse untrusted network responses,
write downloaded files to disk, and spawn `yt-dlp`. This document explains what
is in scope, how to report a problem, and what protections already exist.

## Supported versions

| Version | Supported |
|---|---|
| **0.7.x** (`v0.7.0-online`, latest) | ✅ Security fixes |
| **0.6.x** | ⚠️ Critical fixes at the maintainer's discretion |
| **< 0.6** | ❌ Please upgrade |

The project is pre-1.0 and moves quickly. Reports against the latest release
and `main` are the most useful.

## Reporting a vulnerability

**Do not open a public issue for a security problem.**

Use GitHub's private vulnerability reporting:

<https://github.com/ARYAN007H/Music0/security/advisories/new>

If that link is unavailable, contact the maintainer
[@ARYAN007H](https://github.com/ARYAN007H) privately through GitHub and ask for
a secure channel. Do not include exploit details in a public issue, pull
request, or discussion.

Please include:

- affected version (`v0.7.0-online`, commit hash, or `main`);
- operating system and install method (deb/rpm/AppImage/dmg/msi/bare binary);
- a description of the impact and the attacker's starting position;
- reproduction steps or a minimal proof of concept;
- relevant logs with personal paths, IP addresses, and tokens redacted;
- whether the issue also affects the web/PWA preview.

### What to expect

- **Acknowledgement:** best effort within 3 business days.
- **Triage:** a determination of severity and whether the report is in scope.
- **Fix:** coordinated with you; the goal is a patch release, not a promise of
  a deadline.
- **Credit:** you will be credited in the advisory unless you ask to remain
  anonymous.
- **No bounty:** this is a personal open-source project and does not run a paid
  bounty program.

Please give the maintainer a reasonable opportunity to fix the issue before
public disclosure (90 days is a good default; active exploitation may justify
earlier disclosure).

## Scope

### In scope

- Arbitrary file read/write or path traversal through catalog IDs, cover names,
  download paths, or IPC arguments.
- Command injection or argument injection into the `yt-dlp` child process.
- SSRF or forced requests to non-allowlisted hosts through catalog, cover, or
  stream URLs.
- Cache poisoning: a crafted response or ID that makes one track resolve to
  another track's file, or makes a partial download look complete.
- Memory safety, panics, or resource exhaustion reachable from a malicious
  network response (for example: unbounded body, infinite loop, decompression
  bomb).
- Tauri IPC privilege escalation from the webview, or a webview escape that can
  read/write outside the app's data directories.
- Vulnerabilities in bundled dependencies that are reachable in normal use.
- Supply-chain issues in the release workflows or published artifacts.

### Out of scope

- **Terms of service and legal exposure** for streaming from public sources.
  That is a product/legal discussion, not a vulnerability report.
- **Availability of YouTube Music, Piped, Invidious, JioSaavn, or LRCLIB.**
  Outages and bot walls are expected; report them as bugs, not security issues.
- **Unsigned macOS/Windows builds.** Gatekeeper/SmartScreen warnings are a
  known distribution limitation documented in the README.
- **A local attacker who can already read or write your home directory.** They
  can modify the binary, the database, or the music files directly.
- **Physical access / unlocked session.**
- **The web preview (`server.ts`) being reachable on a LAN.** It is a
  development/demo server that binds `0.0.0.0` by design; it is not intended
  for untrusted networks. Reports about exposing it are still welcome, but the
  documented fix is "don't expose it".
- **Upstream codec bugs** in Symphonia, CPAL, or the OS audio stack. Report
  those upstream; if Musico crashes, we still want the reproduction.

## Threat model

### Assets

- The user's music library (read access).
- The app's data directories: sled database, settings, caches, logs.
- The user's listening history, likes, playlists, and learned taste profile.
- Integrity of downloaded audio files presented as a given track.
- The user's network identity (the app contacts third parties directly).

### Trust boundaries

| Component | Trust | Notes |
|---|---|---|
| Local binary and bundled frontend | Trusted | Shipped together; the webview loads local assets |
| Local audio files | Trusted content, untrusted parser input | Symphonia handles malformed files; report crashes |
| InnerTube responses | **Untrusted** | Parsed in `online/parse.rs` |
| Piped / Invidious / InnerTube Direct responses | **Untrusted** | URLs, content types, sizes validated |
| JioSaavn API/CDN | **Untrusted** | IDs charset-gated; media URL decrypted then allowlisted |
| LRCLIB responses | **Untrusted** | Parsed into lyric lines; no code execution |
| Thumbnails | **Untrusted** | https + host allowlist + 5 MB cap |
| `yt-dlp` output | **Untrusted** | File existence and minimum size verified; duration probed for metadata |
| Tauri IPC arguments | Semi-trusted | The local frontend is trusted, but IDs still flow through path joins |
| `server.ts` web preview | **Not hardened** | Dev/demo only; no auth, binds `0.0.0.0` |

### Protections in place

| Area | Mitigation | Where |
|---|---|---|
| Thumbnail URLs | https-only; hosts must be `ytimg.com`/`*.ytimg.com` or `saavncdn.com`/`*.saavncdn.com`; 5 MB body cap | `online/stream.rs` (`is_safe_art_url`, `cache_cover_art`) |
| Cover filenames | Sanitizes song IDs to `[A-Za-z0-9:_-]`, preventing traversal | `scanner.rs` (`sanitize_cover_stem`) |
| Stream cache lookup | Exact `{videoId}.` prefix, never a fuzzy/prefix match | `online/stream.rs` (`cached_file`) |
| Video/audio IDs | Charset and length gates before URL or path use | `online/sources.rs`, `online/saavn.rs` |
| Piped/Invidious downloads | https only, `audio/*` content type, 64 MB cap, 2 MB metadata cap, ≥ 10 KB verify, `.part` → `fsync` → atomic rename | `online/sources.rs` |
| yt-dlp | Spawned with `Command` arguments (no shell), constant base URL, `--no-playlist`, nulled stdout/stderr, timeout kill + reap, process-wide cap of 3 | `online/stream.rs` |
| Cache pruning | mtime LRU, hysteresis target, stale `.part`/`.ytdl` sweep after 24 h | `online/stream.rs` |
| Concurrency | Per-video locks; InnerTube cap of 2; coalesced identical requests | `online/stream.rs`, `online/client.rs` |
| Airplane Mode | Network commands fast-fail; speculative work is skipped | `commands.rs`, `event_bridge.rs` |
| Second instance | Single-instance plugin focuses the existing window before DB/audio access | `lib.rs` |
| SQL/NoSQL injection | No SQL; sled keys are typed/binary; no user-supplied query language | `musico_recommender` |
| IPC error handling | Rejections are logged; the frontend receives `null`, not a silent partial state | `frontend/js/bridge.js` |

### Known limitations

- **No code signing.** macOS and Windows artifacts are unsigned; verify the
  `SHA256SUMS.txt` from the release and build from source if that matters to
  you.
- **Public proxy instances are untrusted infrastructure.** They can see your
  IP and the tracks you request, and can return malicious bytes. The size,
  type, and URL gates reduce the attack surface but cannot make a third-party
  proxy trustworthy.
- **`yt-dlp` is an external dependency.** Its own security posture and update
  cadence are outside this project's control.
- **The app can read any file you point the music folder at.** That is the
  intended feature; it does not sandbox the folder.
- **The webview has `csp: null` in `tauri.conf.json`.** The frontend is local,
  but a future remote-content feature would need a real CSP.

## Privacy

- **No accounts, analytics, telemetry, or crash upload.**
- Search queries, video IDs, and artist/title metadata are sent to the catalog
  sources you use (YouTube Music, Piped/Invidious instances, JioSaavn, LRCLIB).
  Those services see your IP address and a desktop browser user agent.
- Listening history, likes, playlists, and taste vectors stay in the local sled
  database. They are never uploaded by this project.
- Logs live in `~/.local/share/musico-online/musico.log` and may include search
  queries, track titles, and error text. Treat logs as private when attaching
  them to an issue.
- `MUSICO_SAAVN_API` and `MUSICO_LRCLIB_API` let you point at a proxy/mirror;
  that proxy then sees the same requests.

## Secure development practices

- Network, file, and child-process boundaries are reviewed with the same care
  as the feature itself.
- New sources must keep the trust gates (https, content type, size cap, atomic
  rename, minimum-size verify) and add hermetic failover tests.
- New parsing code belongs in `online/parse.rs` and must tolerate malformed
  items without panicking.
- Run the opt-in live tests before a release:
  `cargo test -p musico-online live_ -- --ignored --nocapture`.
- Dependency review: `cargo tree`, `cargo audit` (if installed), and a quick
  check of new crates' maintenance/license status are expected for new
  dependencies.
- Release artifacts are built by GitHub Actions and published with
  `SHA256SUMS.txt`; the publish job runs only for tags.

## Safe harbor

Security research conducted in good faith under this policy is welcome. Please:

- test only against your own installation and data;
- do not access, modify, or exfiltrate another person's data;
- do not run denial-of-service tests against public services or third-party
  instances;
- do not publicly disclose the issue before a fix is available;
- comply with applicable law.

The maintainer will not pursue legal action for good-faith research that
follows these rules.
