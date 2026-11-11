# handout.md — session handoff (written 2026-11-09, agent-1)

Read this FIRST. It is the complete bridge from the previous session, which died
with a broken shell mid-investigation.

## 0. Environment facts (check these before anything)

- The repo moved: the old `/home/aryan/Documents/projects/Musico-online` was DELETED
  (~25 GB of target dirs went with it). The fresh clone is **`/home/aryan/Documents/projects/Music0`**
  (with a ZERO). All old worktrees (`~/.cache/musico-worktrees/*`) and cargo target
  dirs are gone — everything rebuilds from scratch. Expect long first builds.
- The previous session's shell died with `spawn /usr/bin/bash ENOENT` (anchored to the
  deleted folder). **First command you run: `echo alive`.** If bash is dead again,
  tell Aryan — do not burn turns retrying.
- Aryan's Android phone is **connected via adb** (that's why he's here). Repo git
  state: fresh clone of `main`, version **0.8.2** in `src-tauri/Cargo.toml`.
- Frontend suite protocol is unchanged: server on port **8143**, kill stale listeners
  BY PID (never `pkill -f`), curl-verify the served tree BEFORE running suites,
  symlink repo-root `node_modules` into any worktree (fresh clone needs `npm install`
  in the repo root first — `package.json` exists for the TS engine + tauri CLI).

## 1. PRIMARY TASK — Android app closes on open (UNRESOLVED)

Aryan installed the published APK (**v0.8.1-online**, from the Nov 6 release —
signed, zipaligned, `apksigner verify` passed, android job green on CI). It installs
but **closes immediately on open**. Phone is connected — get ground truth:

```bash
adb logcat -c
adb shell monkey -p io.github.aryan007h.musicoonline -c android.intent.category.LAUNCHER 1
sleep 4
adb logcat -d | grep -E "FATAL|AndroidRuntime|UnsatisfiedLink|SIGABRT|SIGSEGV|panic|musicoonline" | tail -60
```

Interpretation:
- `UnsatisfiedLinkError ... libc++_shared.so` → packaging bug: the APK is missing the
  C++ runtime. The workflow (`.github/workflows/release.yml`, `android` job) hunts a
  `>100k` arm64 `libc++_shared.so` across NDKs into `jniLibs/arm64-v8a/` — if a stub
  or wrong copy got packed, fix the hunt/copy and rebuild.
- `SIGABRT` / Rust `panic` in `musico_online_lib` → native abort at boot; the message
  names the line. Fix in Rust.
- `AndroidRuntime FATAL` → Kotlin/MainActivity init failure — the generated project
  (`tauri android init` per CI run) vs `tauri.android.conf.json` identifier
  (`io.github.aryan007h.musicoonline`) is the area to inspect.

### Static review already done (do NOT redo it)

- `src-tauri/src/lib.rs` `run()`: fully defensive — `single_instance` cfg-gated off
  Android, `PlaybackEngine::new().ok()`, `init_recommender()` returns Option, all
  paths in `identity.rs` are Option-safe, `env_logger` try_init. Desktop-only deps
  (`rfd`, `single-instance`, `souvlaki`, `zbus`) are correctly target-gated in
  `Cargo.toml`. reqwest is rustls (no openssl).
- `state.rs` / `identity.rs` / the whole `setup()` closure: no unwraps reachable on
  Android. Scanner gated on settings; MPRIS/media_controls compiled out.
- Conclusion: the Rust boot path does NOT obviously crash. The remaining suspects,
  ranked: (1) `UnsatisfiedLinkError` — a `.so` missing from the APK (libc++_shared,
  or another DT_NEEDED of `libmusico_online_lib.so`); (2) native abort from
  cpal/oboe or a constructor; (3) Kotlin-side init failure; (4) WebView/asset
  protocol failure on the device.
- **Separate real bug (not the crash)**: `musico_playback/Cargo.toml` has
  `cpal = { features = ["wasm-bindgen"] }` — **no `oboe` feature**, so on Android
  cpal uses the Dummy host = zero audio devices = the engine degrades to None
  (app runs, no sound). The release.yml comment claims "cpal → oboe", but the
  feature is not enabled. After the crash is fixed, add the oboe feature (and the
  JNI wiring it needs) or the app will be a silent browser on the phone.

## 2. Secondary tasks (queued, in priority order)

1. **Doc kit rewrite** — architecture.md + agents.md were drafted in the previous
   session but the worktree they lived in was deleted (uncommitted → lost). The full
   text survives in the previous conversation's history: `docs/architecture.md`
   (crate map, playback pipeline, online stack, recommender signals, persistence
   table, security/ACL notes, testing/CI, honest constraints) and `agents.md` (the
   agent-team playbook). Rewrite them into this clone, then CONTRIBUTING.md,
   SECURITY.md, CODE_OF_CONDUCT.md, CHANGELOG.md (real history: 0.5.0, 0.6.0, 0.6.1,
   0.7.0, 0.8.0, 0.8.1), and a README docs table. Land as one docs PR.
2. **cpal oboe feature** (above — pairs with the Android work).
3. **Toast honesty**: when yt-dlp exits 1, the toast says "stream unavailable or
   region-locked" — it's usually transient (Piped bot-walls come and go by the
   minute; Aryan hit it once, retry worked). Change the message to suggest retrying
   + updating yt-dlp. `src-tauri/src/online/stream.rs` (~line 493 area).
4. **TS engine decision (Aryan's call, still pending)**: `server.ts` + `src/engine/*.ts`
   serve only the AI Studio preview via bridge.js `/api/invoke/*` fallbacks. Adopt
   or move to a branch.
5. **agentchat.md**: read the current hub in this clone — other agents (argon et al.)
   landed 0.8.0/0.8.1/0.8.2 work after the last session; catch up on their log
   entries and the current rules before touching anything.

## 3. Repo facts quick-sheet

- App id: desktop `dev.musico.online`, Android `io.github.aryan007h.musicoonline`
  (lowercase — Java namespaces; the desktop keeps case for Flatpak).
- Data dirs: `~/.local/share/musico-online` (settings, covers, log), `~/.cache/musico-online/online`
  (stream cache, LRU), sled DB under the data dir.
- Gates: `cargo test --workspace` (~176+ tests), 9 Playwright suites
  (`scripts/browser_*_test.mjs`), `scripts/gen_test_tone.sh` before engine e2e,
  `cargo clippy` no NEW warnings. Frontend changes ⇒ bump the `?v=` stamp in
  `frontend/index.html` AND `sw.js` `CACHE_NAME` (currently 0.6.34+ — check).
- Landing rule: issue → branch → PR → CI → independent review → merge. Direct pushes
  to main are reverted on sight. Android release APKs come from the tag workflow
  (keystore secrets on GitHub); NEVER build unsigned "will not install" APKs for
  Aryan's phone.
- Releases so far: v0.5.0 → v0.6.0 → v0.6.1 → v0.7.0 (all three desktop platforms) →
  v0.8.0 → **v0.8.1 (latest; includes the Android APK)**. main is at 0.8.2,
  unreleased.
- A Reddit contributors post was drafted in the prior session (the agent-team hook,
  good-first-issues #27/#37/#41/#85/#48) — Aryan posted/will post it; expect
  first-time human PRs.

## 4. How to work this session

1. `echo alive` — confirm shell.
2. `git -C /home/aryan/Documents/projects/Music0 log --oneline -10` — catch up.
3. Run the adb block from §1, get the stack, fix, PR, and push a fixed APK to the
   phone (`adb install -r dist/musico-online-android-aarch64.apk`) + verify launch
   with the same monkey command.
4. Log a DONE entry in `agentchat.md` (append-only, 25-entry retention, union-merge
   on conflicts — never delete another agent's entry).
