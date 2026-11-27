# Music0 documentation

This directory holds the deep dives behind [ARCHITECTURE.md](../ARCHITECTURE.md).
The root files are the front door; these documents are the working reference
for contributors, reviewers, and anyone debugging the runtime.

## Start here

| If you are… | Read |
|---|---|
| New to the repository | [CONTRIBUTING.md](../CONTRIBUTING.md) → [development.md](development.md) → [testing.md](testing.md) |
| Reviewing a change | [ARCHITECTURE.md](../ARCHITECTURE.md) → [ipc.md](ipc.md) → [online-stack.md](online-stack.md) → [data-storage.md](data-storage.md) |
| Working on UI & themes | [frontend.md](frontend.md) → [brand/BRAND_GUIDELINES.md](brand/BRAND_GUIDELINES.md) |
| Working on catalog/streaming | [online-stack.md](online-stack.md) → [data-storage.md](data-storage.md) |
| Verifying downloads or packaging | [verify-downloads.md](verify-downloads.md) → [flatpak.md](flatpak.md) |
| Cutting a release | [release.md](release.md) → [CHANGELOG.md](../CHANGELOG.md) |
| Reporting a bug | [troubleshooting.md](troubleshooting.md) → [SECURITY.md](../SECURITY.md) for vulnerabilities |
| Looking for the plan | [ROADMAP.md](../ROADMAP.md) |

## Documents

| Document | What it covers |
|---|---|
| [ARCHITECTURE.md](../ARCHITECTURE.md) | System overview, CMP + JNI design, threads, crate boundaries, core flows, CI/packaging |
| [development.md](development.md) | Prerequisites, Compose Multiplatform and Android development, env vars, logs, common tasks |
| [testing.md](testing.md) | Test layers, hermetic Rust tests, Compose Multiplatform checks, 19 live tests |
| [ipc.md](ipc.md) | Production JNI bridge (`musico_core/src/mobile.rs`, 48 commands) |
| [frontend.md](frontend.md) | Compose Multiplatform UI architecture, Material 3 Expressive design system, Light/Dark `ThemeMode`, controls, and shapes |
| [online-stack.md](online-stack.md) | InnerTube, JioSaavn, `InnerTubeDirectSource`, Piped/Invidious, loopback `stream_server.rs`, `playlist_import.rs`, `yt-dlp`, LRCLIB |
| [data-storage.md](data-storage.md) | Desktop/Android paths, `settings.json`, `ui_prefs.json`, sled trees (`mobile:*`), stream/cover caches, reset |
| [release.md](release.md) | Versioning, tag scheme, `release.yml`, `desktop-core-so.yml`, artifacts, signing/secrets, local builds, rollback |
| [flatpak.md](flatpak.md) | Linux AppImage & Flatpak packaging (`io.github.aryan007h.Music0`), sandbox paths, bundled `yt-dlp` + `deno` |
| [verify-downloads.md](verify-downloads.md) | `SHA256SUMS.txt` verification, VirusTotal, Android APK signing fingerprint, Play Protect guidance |
| [troubleshooting.md](troubleshooting.md) | Symptom-driven fixes for startup, audio, CMP Desktop/Android, online resolvers, storage, CI, and packaging |
| [brand/BRAND_GUIDELINES.md](brand/BRAND_GUIDELINES.md) | TE Dial-Zero spec for icon/mark/banner + Material 3 Expressive rules for all in-app UI |
| [adr/README.md](adr/README.md) | Architecture decision record index and template |

## Architecture decision records

| ADR | Decision | Status |
|---|---|---|
| [0001](adr/0001-tauri-2-no-build-frontend.md) | Tauri 2 with a static, no-build frontend | Superseded by [ADR-0005](adr/0005-compose-multiplatform-and-jni-core.md) |
| [0002](adr/0002-separate-app-identity.md) | A separate `musico-online` app identity and data namespace | Accepted (amended) |
| [0003](adr/0003-tiered-stream-resolution.md) | Tiered stream resolution: cache → direct sources → yt-dlp | Accepted (amended by [ADR-0006](adr/0006-loopback-progressive-streaming.md)) |
| [0004](adr/0004-local-first-recommender.md) | A local-first recommender with no cloud profile | Accepted (amended) |
| [0005](adr/0005-compose-multiplatform-and-jni-core.md) | Compose Multiplatform UI over a pure-Rust JNI core | Accepted |
| [0006](adr/0006-loopback-progressive-streaming.md) | Progressive playback via a loopback HTTP/1.1 range server | Accepted |

## Documentation conventions

- **Verify against code.** Commands, paths, event names, and counts in these documents are checked against the source tree at the release they describe.
- **Use exact paths.** Prefer `musico_core/src/online/stream.rs` over "the online module".
- **Keep commands copy-pasteable.** Every shell block assumes the repository root unless it says otherwise.
- **Record irreversible choices as ADRs.** Durable architectural choices get an ADR in `docs/adr/`.
- **No secrets and no private data.** Never paste real tokens, cookies, or personal machine paths.

## Where things live

```text
.
├── ARCHITECTURE.md        System design (root)
├── CONTRIBUTING.md        Setup, workflow, standards (root)
├── SECURITY.md            Threat model and reporting (root)
├── CHANGELOG.md           Release history (root)
├── ROADMAP.md             Post-v1.0.1 roadmap (root)
├── docs/
│   ├── README.md          This index
│   ├── development.md
│   ├── testing.md
│   ├── ipc.md
│   ├── frontend.md
│   ├── online-stack.md
│   ├── data-storage.md
│   ├── release.md
│   ├── flatpak.md
│   ├── verify-downloads.md
│   ├── troubleshooting.md
│   ├── brand/             BRAND_GUIDELINES.md, palette.json, SVGs, banner.png
│   ├── screenshots/       12 current CMP UI screenshots
│   └── adr/               ADRs 0001–0006
├── android/               Compose Multiplatform app (:shared, :app, :desktopApp)
├── musico_core/           Pure-Rust JNI core (mobile.rs, cover.rs, lyrics.rs, config.rs, identity.rs, online/)
├── musico_playback/       Audio engine crate (Rust, including http_source.rs, resampler.rs, eq.rs)
├── musico_recommender/    Recommender crate (Rust, 19 modules)
├── flatpak/               Linux desktop entry, AppStream metainfo, Flatpak manifest
├── scripts/               package_appimage.sh, package_flatpak.sh, gen_test_tone.sh
└── .github/               CI/release workflows + issue/PR templates
```

## Keeping docs current

A documentation change is part of the feature, not a follow-up. The [definition of done](../CONTRIBUTING.md#definition-of-done) requires it:

- [ ] Root README still describes the feature truthfully.
- [ ] `ARCHITECTURE.md` updated if a boundary, thread, or flow changed.
- [ ] The relevant deep dive updated (paths, JNI commands, env vars).
- [ ] `CHANGELOG.md` has a user-visible entry.
- [ ] Cross-links resolve; no link points at a deleted section.
- [ ] Counts (tests, commands, trees, files) were re-checked, not copied.
