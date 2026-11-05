# Musico Online documentation

This directory holds the deep dives behind [ARCHITECTURE.md](../ARCHITECTURE.md).
The root files are the front door; these documents are the working reference
for contributors, reviewers, and anyone debugging the runtime.

## Start here

| If you are… | Read |
|---|---|
| New to the repository | [CONTRIBUTING.md](../CONTRIBUTING.md) → [development.md](development.md) → [testing.md](testing.md) |
| Reviewing a change | [ARCHITECTURE.md](../ARCHITECTURE.md) → [ipc.md](ipc.md) → the relevant subsystem doc |
| Working on the UI | [frontend.md](frontend.md) → [testing.md](testing.md#4-browser-suites) |
| Working on catalog/streaming | [online-stack.md](online-stack.md) → [data-storage.md](data-storage.md) |
| Cutting a release | [release.md](release.md) → [CHANGELOG.md](../CHANGELOG.md) |
| Reporting a bug | [troubleshooting.md](troubleshooting.md) → [SECURITY.md](../SECURITY.md) for vulnerabilities |
| Looking for the plan | [ROADMAP.md](../ROADMAP.md) |

## Documents

| Document | What it covers |
|---|---|
| [ARCHITECTURE.md](../ARCHITECTURE.md) | System overview, design principles, process/thread model, crate boundaries, core flows, failure model, performance budgets |
| [development.md](development.md) | Prerequisites, desktop/web/Android development, environment variables, logs, common code tasks, platform notes |
| [testing.md](testing.md) | Test inventory and counts, hermetic Rust tests, live tests, TypeScript tests, browser suites, contrast gate, CI policy |
| [frontend.md](frontend.md) | No-build frontend, script order, Bridge, store/router, Preact shell, CSS layers, cache busting, PWA, accessibility |
| [online-stack.md](online-stack.md) | InnerTube, JioSaavn, Piped/Invidious, yt-dlp, stream ladder, search/catalog caches, radio, lyrics, offline behavior |
| [data-storage.md](data-storage.md) | XDG paths, sled trees, settings schema, cache layout, pruning, migrations, backup/reset |
| [ipc.md](ipc.md) | Command contract, full command catalog, payload shapes, events, error handling, adding a command |
| [release.md](release.md) | Versioning, tag scheme, release workflow, artifacts, signing limitations, local builds, rollback |
| [troubleshooting.md](troubleshooting.md) | Symptom-driven fixes for startup, playback, catalog, storage, platform, and test-environment problems |
| [adr/README.md](adr/README.md) | Architecture decision record index and template |
| [ux-review.md](ux-review.md) | Historical UX review that seeded the lyrics work; not current documentation |

## Architecture decision records

| ADR | Decision |
|---|---|
| [0001](adr/0001-tauri-2-no-build-frontend.md) | Tauri 2 with a static, no-build frontend |
| [0002](adr/0002-separate-app-identity.md) | A separate `musico-online` app identity and data namespace |
| [0003](adr/0003-tiered-stream-resolution.md) | Tiered stream resolution: cache → direct sources → yt-dlp |
| [0004](adr/0004-local-first-recommender.md) | A local-first recommender with no cloud profile |

## Documentation conventions

- **Verify against code.** Commands, paths, event names, and counts in these
  documents are checked against the source tree at the release they describe.
  If the code changes, update the doc in the same pull request.
- **Use exact paths.** Prefer `src-tauri/src/online/stream.rs` over "the
  online module". Paths are part of the contract.
- **Separate the runtimes.** The Tauri app (`src-tauri/`) and the web/PWA
  preview (`server.ts` + `src/engine/`) share frontend assets but not backend
  behavior. Say which one a statement applies to.
- **Keep commands copy-pasteable.** Every shell block assumes the repository
  root unless it says otherwise. Use `PORT=…` only where the runtime reads it.
- **Record irreversible choices as ADRs.** A decision that would be expensive
  to reverse (a platform, a storage format, an identity, a resolution ladder)
  gets an ADR rather than a paragraph in a guide.
- **No secrets and no private data.** Examples use placeholder paths, ids, and
  keys. Never paste a real token, cookie, or machine-specific path.

## Where things live

```text
.
├── ARCHITECTURE.md        System design (root)
├── CONTRIBUTING.md        Setup, workflow, standards (root)
├── SECURITY.md            Threat model and reporting (root)
├── CHANGELOG.md           Release history (root)
├── ROADMAP.md             Plan of record (root)
├── docs/
│   ├── README.md          This index
│   ├── development.md
│   ├── testing.md
│   ├── frontend.md
│   ├── online-stack.md
│   ├── data-storage.md
│   ├── ipc.md
│   ├── release.md
│   ├── troubleshooting.md
│   └── adr/
├── src-tauri/             Tauri app crate (Rust)
├── musico_playback/       Audio engine crate (Rust)
├── musico_recommender/    Recommender crate (Rust)
├── frontend/              No-build UI + PWA assets
├── src/engine/            TypeScript engine for the web preview
├── scripts/               Browser suites + contrast gate
└── .github/               CI/release workflows + issue/PR templates
```

## Keeping docs current

A documentation change is part of the feature, not a follow-up. The
[definition of done](../CONTRIBUTING.md#definition-of-done) requires it. Use
this checklist when behavior changes:

- [ ] Root README still describes the feature truthfully.
- [ ] `ARCHITECTURE.md` updated if a boundary, thread, or flow changed.
- [ ] The relevant deep dive updated (paths, commands, events, env vars).
- [ ] `CHANGELOG.md` has a user-visible entry.
- [ ] Cross-links resolve; no link points at a deleted section.
- [ ] Counts (tests, commands, trees, files) were re-checked, not copied.
