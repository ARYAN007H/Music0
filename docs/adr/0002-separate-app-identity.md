# 0002. A separate `musico-online` app identity

- Status: Accepted (amended for Compose Multiplatform packaging)
- Recorded: 2026-11-05
- Deciders: maintainers

## Context

The repository is a clean-room rebuild of an earlier Musico desktop app. The
earlier app used the `musico` namespace for its XDG directories, sled database,
cover cache, settings, and D-Bus name.

Users may want to run both builds side by side: the older offline player and
the online build. If both used `~/.local/share/musico`, they would open the same
sled database with incompatible schemas, fight over the same settings file, and
overwrite each other's covers. Sled also takes a file lock, so the second
process would fail to open the DB rather than degrade gracefully.

The rebuild also changed the song id scheme: the old app used UUID-era ids,
while the current code uses a stable hash of the file path so library ids,
play events, and indexed records agree. Sharing a database across that change
would require an in-place migration and would still leave the two apps
competing for writes.

## Decision

Use a dedicated identity namespace, defined once in
`src-tauri/src/identity.rs`:

```text
DATA_SUBDIR  = "musico-online"
DBUS_NAME    = "musico-online"
MPRIS_NAME   = "org.mpris.MediaPlayer2.musico-online"
DISPLAY_NAME = "Musico Online"
```

All storage paths derive from it:

- data: `<dirs::data_dir>/musico-online/`
- cache: `<dirs::cache_dir>/musico-online/`
- config: `<dirs::config_dir>/musico-online/`

The Tauri identifier is `io.github.aryan007h.Music0` — a GitHub-namespaced
id, required for the Flathub submission, that does not claim a domain the
project does not own. The window title and desktop entry display name are
"Musico Online". No code path resolves a bare `musico` directory.

The storage namespace is deliberately independent of the Tauri identifier
(`musico-online`, not the dotted id), so changing the identifier for packaging
does not move any user data.

The separate namespace also makes the one-time `db/.stable-ids` migration
safe: it can clear and rebuild only this app's index without touching the older
install.

## Consequences

Positive:

- Both apps can run concurrently without sharing a sled lock, settings, or
  cover files.
- The rebuild can change persisted types and id schemes without a compatibility
  bridge to the old app.
- Uninstalling one app does not delete the other's data.
- The MPRIS name is unambiguous when both players are installed.
- A clean reset is a single directory removal, documented in
  [data-storage.md](../data-storage.md#6-resetting-local-state).

Negative:

- No automatic migration from the older app. Users must reconfigure the music
  folder, rescan, and re-create likes/playlists in the new app.
- Documentation and support must be explicit about which app owns which path;
  a user pasting `~/.local/share/musico` is looking at the wrong install.
- Two namespaces consume disk if both apps are used.
- Any future rename is a breaking data migration, not a cosmetic change.

## Alternatives considered

- **Reuse `musico` and migrate in place.** Rejected: incompatible sled schemas
  and id schemes, a hard file lock, and the risk of corrupting a working older
  install. A migration would also need to handle downgrades.
- **Reuse `musico` with a version subdirectory** (`musico/v2`). Rejected: still
  collides on the settings file and D-Bus name, and gives the false impression
  that the two apps share state.
- **Ask the user to choose a data directory at first run.** Rejected: a
  support burden and an easy way to point two installs at one DB.
- **Use the Tauri identifier as the directory name** (`dev.musico.online`).
  Rejected: dotted directory names are awkward on Windows and do not match the
  D-Bus/desktop-entry convention.

## References

- `musico_core/src/identity.rs` (moved from `src-tauri/src/identity.rs` in `v1.0.0`)
- `musico_core/src/config.rs` (`settings_path`)
- `musico_core/src/mobile.rs` (`init_core`, `recommender`)
- `musico_core/src/cover.rs` (`cover_path`)
- `musico_core/src/online/stream.rs` (`cache_dir`)
- `android/shared/src/desktopMain/kotlin/io/github/aryan007h/musicoonline/platform/DesktopPlatform.kt`
- [docs/data-storage.md](../data-storage.md)

## Amendments (v1.0.0–v1.0.1)

- `src-tauri/` was retired in `v1.0.0`; `identity.rs` and `config.rs` now live in `musico_core/src/`, and `db/.stable-ids` migration was removed alongside the local folder scanner.
- Packaging identifiers split cleanly from the `musico-online` storage namespace:
  - Flatpak / Linux desktop ID: `io.github.aryan007h.Music0` (`flatpak/io.github.aryan007h.Music0.yml`), display name `Music0`.
  - Android `applicationId` and macOS `bundleID`: `io.github.aryan007h.musicoonline`.
- Because `DesktopPlatformHost` / `MainActivity` resolve app-private/XDG `musico-online` directories before calling `MusicoCore.init()` and `mobile.rs` exports `XDG_*` to those paths while `identity.rs` joins `DATA_SUBDIR` again, Rust currently stores `db/`, `settings.json`, `online/`, and `covers/` in a nested `musico-online/musico-online/` subdirectory (see [docs/data-storage.md](../data-storage.md)).
