# 0002. A separate `musico-online` app identity

- Status: Accepted
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

The Tauri identifier is `dev.musico.online`. The window title and desktop entry
display name are "Musico Online". No code path resolves a bare `musico`
directory.

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
  [data-storage.md](../data-storage.md#9-reset).

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

- `src-tauri/src/identity.rs`
- `src-tauri/src/config.rs` (`settings_path`)
- `src-tauri/src/lib.rs` (`init_recommender`, log path)
- `src-tauri/src/scanner.rs` (`cover_path`, `cache_file`)
- `src-tauri/src/online/stream.rs` (`cache_dir`)
- [docs/data-storage.md](../data-storage.md)
