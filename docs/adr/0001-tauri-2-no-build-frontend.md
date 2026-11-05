# 0001. Tauri 2 with a no-build, static frontend

- Status: Accepted
- Recorded: 2026-11-05
- Deciders: maintainers

## Context

Musico Online is a desktop and Android music player that must run on Linux,
macOS, Windows, and Android from one codebase. It also ships a browser/PWA
preview and a Node demo server. The UI is large and long-lived: transport,
library, catalog, lyrics, queue, and settings surfaces share state across
several views.

The usual web stack for an app of this size is a framework plus a bundler
(React/Vue/Svelte + Vite). That adds a build graph, a dev server, generated
assets, source maps, and a second dependency ecosystem to keep current. The
project already has a Rust workspace, a Tauri CLI, and a TypeScript engine for
the web preview; a frontend build step would be a third toolchain.

Tauri 2 can serve a directory of static files directly. The webview is a modern
engine on every target (`WebKitGTK`, `WKWebView`, `WebView2`), so the platform
features needed here — ES modules, `fetch`, `localStorage`, CSS custom
properties, `requestAnimationFrame` — are available without transpilation.

## Decision

Use **Tauri 2** as the application shell and ship a **no-build frontend**:

- `src-tauri/tauri.conf.json` sets `frontendDist` to `../frontend`,
  `devUrl` to `http://localhost:1420`, and leaves `beforeDevCommand` and
  `beforeBuildCommand` empty.
- `frontend/index.html` loads classic scripts in a fixed order
  (`bridge.js`, `app.js`, `online.js`, `vinyl.js`, `knob.js`, `seek.js`) plus
  the Preact/htm ES module `core/shell.js`.
- The legacy controller (`app.js`) owns cross-view state and transport; the
  Preact shell renders newer views and subscribes to `core/store.js`. There is
  one source of truth, not two.
- `server.ts` serves the same directory with a demo RPC backend for the web
  preview and PWA. It is not part of the desktop build.
- The frontend is plain JavaScript (the shell uses htm, not JSX), so there is no
  transpiler, bundler, or generated asset directory.
- Asset URLs carry `?v=` stamps. Bumping them is the cache-busting mechanism;
  the webview caches aggressively.

Rust remains the only compiled application language. The TypeScript engine in
`src/engine/` exists for `server.ts` and its tests, not for the Tauri runtime.

## Consequences

Positive:

- A Rust change is the only thing that requires a compile; HTML/CSS/JS edits
  are visible on reload.
- The same `frontend/` directory works in the Tauri webview, a static file
  server, and the PWA. There is no "works in dev, breaks in the bundle" class.
- The dependency surface is small. Tauri, Rust crates, and the web platform are
  the whole stack.
- Debugging is direct: the code the webview runs is the code in the repository.

Negative:

- No type checking, JSX, tree-shaking, or hot module replacement for
  `frontend/`. Discipline replaces compiler errors.
- Load order is load-bearing. A script that expects a global from a later file
  fails at runtime.
- Cache busting is manual. A forgotten `?v=` bump produces a stale UI that is
  hard to diagnose.
- Two UI layers coexist: the legacy controller and the Preact shell. New code
  must not duplicate transport state.
- Large files (`app.js`, `core/shell.js`) are harder to navigate than a
  component tree.
- Browser suites are the main regression net for UI contracts; they are not run
  in CI.

## Alternatives considered

- **React/Vite SPA.** Rejected: a third toolchain, a build artifact to ship,
  and a larger dependency surface for a single-window app. It would also split
  the web preview from the desktop UI unless `server.ts` served the built
  bundle.
- **Svelte/Solid with a compiler.** Same objection as Vite, plus a smaller
  ecosystem for the webview targets.
- **Server-rendered UI.** Rejected: the app is offline-capable and has no
  backend; a server would add a runtime dependency and a privacy surface.
- **Leptos/Yew (Rust UI).** Rejected: a much larger WASM payload, a second
  rendering model alongside the web preview, and weaker DOM/CSS tooling for the
  Apple-Music-style visuals.
- **Tauri 1.** Rejected: Tauri 2 is the current line with mobile support, the
  capability model, and the plugin APIs the app uses (single instance, shell,
  dialog).

## References

- `src-tauri/tauri.conf.json`
- `frontend/index.html` and `frontend/js/`
- `frontend/js/core/shell.js`, `core/store.js`, `core/router.js`
- [docs/frontend.md](../frontend.md)
- [docs/development.md](../development.md#3-running-the-app)
