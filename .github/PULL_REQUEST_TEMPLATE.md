<!--
Thanks for contributing. Keep this description focused on what changed and why.
The checklist mirrors CONTRIBUTING.md § Definition of done; delete lines that
genuinely do not apply and say so in the description.
-->

## Summary

<!-- One or two sentences: what does this PR change? -->

## Why

<!-- The problem, user impact, or issue this addresses. Link the issue. -->

Closes #

## What changed

<!--
A short, reviewable list. Call out:
- new or changed JNI commands (`musico_core/src/mobile.rs`);
- Compose Multiplatform UI/ViewModel changes (`android/shared/`);
- persisted data or schema changes;
- resolver, cache, or settings behavior;
- version bumps across the 6 synced manifests (if releasing);
- web-preview `frontend/` asset version bumps (if touched).
-->

-

## How it was tested

<!-- Commands and results, not just "it works". Include platform and device. -->

```sh
cargo test --workspace --exclude musico_playback
cargo build --release -p musico_core --lib
gradle -p android :app:compileDebugKotlin :desktopApp:compileKotlin :shared:desktopTest --console=plain
npm test && npm run build
```

- [ ] Rust suite & `musico_core` release `cdylib` build pass (or excluded/failed tests are explained).
- [ ] Compose Multiplatform Android/Desktop compile and `:shared:desktopTest` pass.
- [ ] New behavior has a hermetic test.
- [ ] `npm test` / `npm run build` pass if `src/engine/` or `server.ts` changed.
- [ ] Compose Multiplatform change exercised on Desktop/Android (or web-preview change exercised in a browser).

## Screenshots / recordings

<!-- Required for visible UI changes. Before/after if applicable. -->

## Platform impact

<!-- Check every target this touches; note anything that could not be built. -->

- [ ] Linux
- [ ] macOS
- [ ] Windows
- [ ] Android
- [ ] Web preview (`server.ts` / `frontend/`) only
- [ ] No platform-specific code

## Risk and follow-ups

<!--
Mention cache/DB migrations, security-sensitive boundaries, new network or
disk work, and anything intentionally left out of scope.
-->

## Checklist

- [ ] The change does what the linked issue/PR says, and nothing unrelated.
- [ ] `?v=` asset versions were bumped if `frontend/` changed.
- [ ] Network/disk work is off the UI thread and bounded.
- [ ] Error paths return a user-visible message; no new `unwrap()` on runtime data.
- [ ] Security-sensitive input (URLs, ids, filenames, downloaded bytes) is validated at the boundary.
- [ ] Docs are updated when behavior, setup, JNI contract, or architecture changes.
- [ ] `CHANGELOG.md` has an entry for user-visible changes.
- [ ] I have read [CONTRIBUTING.md](https://github.com/ARYAN007H/Music0/blob/main/CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](https://github.com/ARYAN007H/Music0/blob/main/CODE_OF_CONDUCT.md).
