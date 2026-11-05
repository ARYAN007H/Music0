# Frontend

The frontend is a **no-build** application: `frontend/index.html` loads plain
classic scripts and one ES module, and the system webview renders it directly.
There is no bundler, transpiler, framework build, or generated asset pipeline.
This is a deliberate constraint, not an unfinished migration.

## 1. Files and load order

`frontend/index.html` loads assets in a fixed order. The order matters: each
classic script expects the globals created by the previous one.

| Order | Asset | Role |
|---|---|---|
| 1 | `js/bridge.js` | Tauri IPC wrapper + mock fallback (`window.Bridge`) |
| 2 | `js/app.js` | Legacy controller: state, transport, library, settings (`window.App`) |
| 3 | `js/core/shell.js` (module) | Preact/htm shell: sidebar, top bar, Home/detail/genre/radio/playlist views |
| 4 | `js/online.js` | Online search/browse UI, online playback, status badges (`window.Online`) |
| 5 | `js/vinyl.js` | Fullscreen vinyl artwork |
| 6 | `js/knob.js` | Volume knob |
| 7 | `js/seek.js` | Seek bar interactions |

`core/shell.js` imports `core/store.js` and `core/router.js` as ES modules.
Those imports carry their own `?v=` stamps; bump them too when you change the
module graph, or the webview can keep a stale module instance.

There are two cooperating UI layers:

- **Legacy controller (`app.js`)** owns cross-view state and transport. It
  publishes snapshots to the store and exposes `window.App`.
- **Preact shell (`core/shell.js`)** renders the Apple Music-style chrome and
  newer views (Home, detail pages, genres, radio, Made for You, playlists,
  favorites). It subscribes to the store and delegates actions to
  `window.App` / `window.Online`, so there is still one source of truth.

Do not introduce a second copy of transport state in a component. Read it from
the store; call the controller to change it.

## 2. IPC bridge

`frontend/js/bridge.js` resolves the Tauri invoke function in this order:

1. `window.__TAURI_INTERNALS__.invoke` — the raw IPC shim, always injected.
2. `window.__TAURI__.core.invoke` — the `withGlobalTauri` bundle.
3. `window.__TAURI__.invoke` — legacy/compat shape.

If none exists, the app runs in **preview mode** and `mockCommand()` answers
from an in-memory demo catalog. This is what makes `frontend/index.html` usable
from any static server and what the browser suites exercise.

```js
const songs = await Bridge.invoke('get_library');
Bridge.listen('playback-event', (event) => {
  const payload = event.payload ?? event;
  // ...
});
```

Important behavior:

- `Bridge.invoke()` catches IPC rejections, logs them, and returns `null`. UI
  code must treat `null` as "unavailable", not as an empty successful result.
- `Bridge.listen()` returns an unsubscribe function. In Tauri, a missing
  `core:event` permission makes the registration reject; the bridge logs it
  loudly and returns a no-op instead of silently dropping events. The
  capability file (`src-tauri/capabilities/default.json`) must keep
  `core:default` and the window permissions the drawn Windows chrome uses.
- Commands use **camelCase argument names** from JS even though Rust parameters
  are `snake_case` (Tauri maps them). For example `songId` for `song_id`,
  `positionSecs` for `position_secs`.
- Event payloads are Tauri's `{ event, payload }` envelope; legacy handlers
  also accept the bare payload.

See [ipc.md](ipc.md) for the command/event catalog.

## 3. Store and router

### Store

`core/store.js` is a tiny observable `Map`:

```js
MusicoStore.set('libRev', (MusicoStore.get('libRev') || 0) + 1);
const unsubscribe = MusicoStore.subscribe((key, value) => { /* ... */ });
```

- `set` skips notification when the value is `Object.is`-equal.
- Listeners are isolated: one throwing listener does not stop the others.
- `snapshotOf(key, fallback)` is the Preact `useSyncExternalStore`-friendly
  accessor.

The store is a cache and a message bus, not a database. Durable state lives in
Rust (settings, sled, files). If the app restarts, the store is empty and the
controller repopulates it.

### Router

`core/router.js` is a hash router with a real history stack:

- `#/browse`, `#/search`, `#/library`, `#/settings`, and so on.
- Detail routes are `#/browse/<base64url>` where the payload is
  `{ browseId, params, title }`. The router deliberately does not
  percent-decode these.
- `navigate(view)`, `back()`, `forward()`, `canGoBack()`, `canGoForward()`.
- `init(onRoute)` normalizes the URL and emits the current route immediately.

Rendering is still owned by the app: the route callback in `app.js` activates
the matching `.view` section and tells the store `activeNav`. The shell's
sidebar and top bar subscribe to that key.

### View lifecycle

- Views are `<section id="view-…" class="view">` blocks in `index.html`; the
  active one gets `.active`.
- Navigating does not destroy a view. Components re-render from store data.
- Expensive data loads on first activation and is cached in the store (for
  example the genre grid and the home feed).
- Returning to a view should not refetch unless the user explicitly refreshes
  or the cache is invalid.

## 4. Online UI

`frontend/js/online.js` owns:

- the debounced search input and typeahead popover;
- Songs/Videos filtering, continuation ("Load more"), and cached badges;
- click-to-play rows and per-row add-to-queue;
- `online-status` (`resolving` / `playing` / `error` / `queued-next` / `radio`)
  and `download-state` (`resolving` / `done` / `failed`) listeners;
- the online track map used when a queue row is replayed later.

`core/shell.js` renders catalog cards, detail pages, context menus, and the
radio/playlist/favorites surfaces. It uses the same `OnlineTrack` fields the
Rust side returns. Keep the source namespace (`source: "saavn"`, video id) on
the object when round-tripping it back through IPC; the backend uses it to
dispatch resolution.

## 5. CSS architecture

Styles are loaded in layers; later files are allowed to override earlier ones.
The order in `index.html` is:

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
native.css          macOS/Windows chrome + platform typography (last)
```

Conventions:

- **Use tokens.** Colors, spacing, radii, and shadows come from
  `css/tokens.css`. A hard-coded color will fail the contrast gate or look
  wrong in another palette.
- **Theme:** `data-theme="light" | "dark"` on `<html>`.
- **Palette:** `data-palette="music" | "nebula" | "sakura" | "aurora" |
  "ocean" | "ember" | "mono"`. The default is `nebula` (`AppSettings::default`,
  `frontend/index.html`, and the selected Settings swatch). `music` exists in
  `tokens.css` but has no Settings swatch; it is only reachable by writing the
  value directly.
- **Platform classes:** `is-mac` and `is-windows` are set pre-paint by an
  inline script in `index.html`. `native.css` keys the native chrome and SF Pro
  stack off them. Linux gets neither class and keeps the system title bar.
- **Responsive:** the full sidebar/top bar layout is for wide windows; below
  the compact breakpoint the shell switches to the mobile/bottom-nav layout.
  Test at a narrow width when changing layout.

## 6. Cache busting

The WebKitGTK/WKWebView/WebView2 caches aggressively. Asset URLs carry a
version query (`?v=…`) for this reason. When you change anything under
`frontend/`:

1. Bump every `?v=` in `frontend/index.html` (stylesheet links and script
   tags).
2. Bump the `?v=` on `core/store.js` and `core/router.js` imports in
   `core/shell.js` if you changed the module graph.
3. Reload the app; if a stale script still appears, close and reopen the app
   (the webview may keep a process-level cache).

Do not use the app version as the asset stamp unless you bump it on every UI
change; the two version streams serve different purposes.

## 7. PWA and mobile web

- `frontend/manifest.json` declares the standalone display, icons, theme, and
  start URL.
- `frontend/sw.js` caches the shell and serves a cached fallback; it bypasses
  `/api/` so the web preview's RPC is never cached.
- The service worker is registered only when **not** running under Tauri
  (`!window.__TAURI_INTERNALS__`), so the desktop app never serves a stale
  shell from a service worker.
- `MediaSession` handlers map play/pause/previous/next/seek to the same
  transport methods as the UI, giving lock-screen controls in the PWA.
- The web preview is a demo. Real playback, local scanning, and the sled store
  exist only in the Tauri runtime.

## 8. Accessibility and keyboard

- Interactive non-button elements carry `role`, `tabindex`, and Enter/Space
  handlers. Rows and cards are keyboard-activatable.
- Toggles expose `aria-checked`; the like button exposes `aria-pressed`;
  navigation uses `aria-current`.
- Focus rings must remain visible; do not remove `:focus-visible` styles.
- Global shortcuts (ignored while typing in an input/textarea/select):
  - `Space` play/pause
  - `N` / `P` next / previous
  - `S` / `R` cycle shuffle / repeat
  - `↑` / `↓` volume
  - `/` open Search and focus the field
  - `L` like/unlike
  - `Q` toggle Playing Next
  - `Cmd/Ctrl+K` command palette
  - `Escape` close overlays/fullscreen player
- The contrast gate (`scripts/contrast_gate.mjs`) checks 4.5:1 text contrast
  across every palette and theme. Run it after token or surface changes.

## 9. Common pitfalls

| Symptom | Likely cause |
|---|---|
| `Bridge.invoke` returns `null` for everything | Running in a plain browser without the mock path, or a command rejected; check the console |
| Events never arrive | Missing `core:event` permission, or `listen` registration rejected |
| Old JS keeps running | `?v=` stamps not bumped, or the webview cached the module |
| Store module evaluated twice | `store.js?v=…` imported with two different query strings (each URL is a separate module instance) |
| A view renders twice | Both the legacy controller and the Preact shell mounted the same root |
| Layout breaks only on macOS/Windows | `is-mac` / `is-windows` pre-paint classes or `native.css` ordering |
| Contrast gate fails one palette | Hard-coded color instead of a token |

## 10. Where to go next

- [testing.md](testing.md#4-browser-suites) — run and extend the browser suites.
- [ipc.md](ipc.md) — command names, arguments, and event payloads.
- [development.md](development.md) — run the desktop and web runtimes.
- [ARCHITECTURE.md](../ARCHITECTURE.md) — how the frontend fits the whole system.
