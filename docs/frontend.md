# Frontend & UI Layers

> **Important (v1.0.0+):** Music0's **production UI** for both **Android** and **Desktop** is built in **Kotlin Compose Multiplatform** (`android/shared/`), not `frontend/`. Tauri and the system WebView were retired in `v1.0.0` ([ADR-0005](adr/0005-compose-multiplatform-and-jni-core.md)).
>
> The static `frontend/` directory documented in §§2–8 below remains in the repository solely for the unreleased **Node/Express web & PWA preview** (`npm start` → `server.ts`).

---

## 1. Production UI: Compose Multiplatform (`android/shared/`)

All shipped Android (`:app`) and Desktop (`:desktopApp`) interfaces live under `android/shared/src/commonMain/kotlin/io/github/aryan007h/musicoonline/`:

- **Shell & state:** `ui/MusicoApp.kt` (responsive `840.dp` breakpoint: compact capsule nav + mini-player vs. Desktop rail + `76.dp` floating squircle dock) and `ui/MusicViewModel.kt`.
- **Primary tabs (`AppTab`):** `HomeScreen`, `RadioScreen`, `LibraryScreen`, `SearchScreen`.
- **Overlays & sheets (`AppOverlay`):** `NowPlayingScreen` (`LyricsPane`, `Backdrops`), `SettingsScreen`, `ProfileScreen`, `DetailScreen`, `PlaylistDetailScreen`, `EqScreen`, `OnboardingScreen`, `PlaylistSheets`.
- **Design language:** **Material 3 Expressive** (`ui/theme/Theme.kt`, `Palettes.kt`, `Motion.kt`, `components/Common.kt`) with 5 dark palettes (`NEBULA`, `OCEAN`, `EMBER`, `FOREST`, `MONO`) and `Modifier.musicoWash(...)` gradient feathering.
- **Backend bridge:** Talks directly to Rust `musico_core/src/mobile.rs` via JNI ([`MusicoCore.kt`](../android/shared/src/commonMain/kotlin/io/github/aryan007h/musicoonline/core/MusicoCore.kt)), not `frontend/js/bridge.js`. See [docs/ipc.md](ipc.md).

---

## 2. Web / PWA Preview (`frontend/` + `server.ts`)

`frontend/` is a **no-build** browser application served by `server.ts` (`npm run dev`). `frontend/index.html` loads plain classic scripts and ES modules without a bundler or transpiler.

### Files and load order

| Order | Asset | Role |
|---|---|---|
| 1 | `js/bridge.js` | Mock + Express HTTP RPC wrapper (`POST /api/invoke/:cmd`, `GET /api/audio/:trackId`) |
| 2 | `js/app.js` | Legacy controller: state, transport, library, settings (`window.App`) |
| 3 | `js/core/shell.js` (module) | Preact/htm shell (`vendor/htm-preact.js`): Home, detail, genre, radio, playlist, lab, jam views |
| 4 | `js/online.js` | Online search/browse UI, online playback, status badges (`window.Online`) |
| 5 | `js/vinyl.js` | Fullscreen vinyl artwork |
| 6 | `js/knob.js` | Volume knob |
| 7 | `js/seek.js` | Seek bar interactions |

`core/shell.js` imports `core/store.js` and `core/router.js` as ES modules (`?v=` query strings must stay in sync when editing the module graph).

---

## 3. Store and router (web preview)

- **Store (`core/store.js`):** Observable `Map` (`MusicoStore.set` / `subscribe` / `snapshotOf`). Durable state in the web preview lives in `localStorage` (`musico-theme`, `musico-settings`) and `server.ts` memory.
- **Router (`core/router.js`):** Hash router (`#/browse`, `#/search`, `#/library`, `#/settings`, `#/browse/<base64url>`).

---

## 4. Web preview features

Alongside catalog browsing, search, and playlists, the web preview exercises experimental/demo features backed by `src/engine/` and `server.ts`, including **Sonic Lab**, **Auto-DJ**, **Taste DNA**, **Stem Mode**, **Sonic Bridge**, **Time Capsule**, **Jam Sessions**, **Word-synced lyrics**, and the **Command Palette**.

---

## 5. CSS architecture (web preview)

`frontend/index.html` loads **16** CSS layers in order:

```text
tokens.css          design tokens, palettes, themes
neumorphic.css      base surfaces
animations.css      shared keyframes
layout.css          app shell, sidebar, content grid
now-playing.css     player, vinyl, lyrics
library.css         library lists/grids
queue.css           queue views
settings.css        settings cards
online.css          browse/search/detail/catalog UI
topbar.css          persistent transport LCD
queue-panel.css     Playing Next overlay
am-clean.css        Apple Music cleanup pass
liquid.css          Liquid Glass materials
apple.css           Apple-specific polish
native.css          macOS/Windows chrome styles
m3-expressive.css   Material 3 Expressive skin (loaded last)
```

Conventions:
- **Theme:** `data-theme="light" | "dark"` on `<html>`.
- **Skin:** `frontend/index.html` sets `data-skin="m3e"` pre-paint (switchable in the Themes settings tab).
- **Palette:** `data-palette="music" | "nebula" | "sakura" | "aurora" | "ocean" | "ember" | "mono"` (default `nebula`; `music` is defined in `tokens.css` without a Settings swatch).

---

## 6. Cache busting (web preview)

When changing files under `frontend/`:
1. Bump `?v=` query strings in `frontend/index.html` (and module imports in `core/shell.js`).
2. Bump `CACHE_NAME` in `frontend/sw.js` if offline shell caching should refresh.

---

## 7. PWA and mobile web

- `frontend/manifest.json` declares standalone display, Dial-Zero icons (`#26444D` theme, `#161618` background), and start URL.
- `frontend/sw.js` caches the web shell and bypasses `/api/` routes.
- `MediaSession` handlers map play/pause/previous/next/seek to browser transport controls.

---

## 8. Accessibility and keyboard (web preview)

- Interactive non-button elements carry `role`, `tabindex`, and Enter/Space handlers; toggles use `aria-checked`; like buttons use `aria-pressed`.
- Global shortcuts (ignored while typing): `Space` (play/pause), `←` / `→` (seek ±5 s), `N` / `P` (next/previous), `S` / `R` (shuffle/repeat), `↑` / `↓` (volume), `/` (search), `L` (like), `Q` (Playing Next), `Cmd/Ctrl+K` (command palette), `Escape` (close overlays).
- `scripts/contrast_gate.mjs` enforces 4.5:1 contrast across the 6 selectable palettes × 2 themes.

---

## 9. Where to go next

- [testing.md](testing.md#5-browser-suites-web-preview-only) — run the web preview browser suites and contrast gate.
- [ipc.md](ipc.md) — production JNI commands (and Appendix A for web preview routes).
- [development.md](development.md) — run Compose Multiplatform Desktop/Android or `npm run dev`.
- [ARCHITECTURE.md](../ARCHITECTURE.md) — full production architecture.
