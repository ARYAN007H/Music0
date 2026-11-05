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
- new or changed IPC commands/events;
- persisted data or schema changes;
- resolver, cache, or settings behavior;
- frontend asset version bumps.
-->

-

## How it was tested

<!-- Commands and results, not just "it works". Include platform and device. -->

```sh
cargo test --workspace --exclude musico_playback
npm run build
npm test
```

- [ ] Rust suite passes (or the excluded/failed tests are explained).
- [ ] New behavior has a hermetic test.
- [ ] `npm run build` / `npm test` pass if `src/engine/` or `server.ts` changed.
- [ ] Frontend change exercised in a browser or the Tauri webview.

## Screenshots / recordings

<!-- Required for visible UI changes. Before/after if applicable. -->

## Platform impact

<!-- Check every target this touches; note anything that could not be built. -->

- [ ] Linux
- [ ] macOS
- [ ] Windows
- [ ] Android
- [ ] Web preview (`server.ts`) only
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
- [ ] Docs are updated when behavior, setup, IPC, or architecture changes.
- [ ] `CHANGELOG.md` has an entry for user-visible changes.
- [ ] I have read [CONTRIBUTING.md](https://github.com/ARYAN007H/Musico-online/blob/main/CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](https://github.com/ARYAN007H/Musico-online/blob/main/CODE_OF_CONDUCT.md).
