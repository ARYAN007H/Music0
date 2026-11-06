# Flatpak packaging

The Linux Flatpak build lives in [`flatpak/`](../flatpak). It is the same code
as the `.deb`/`.rpm`/`.AppImage` builds, packaged for the Flathub sandbox.

| File | Purpose |
|---|---|
| `io.github.aryan007h.Music0.yml` | The manifest: runtime, permissions, modules, sources |
| `io.github.aryan007h.Music0.metainfo.xml` | AppStream metadata shown in software centres |
| `io.github.aryan007h.Music0.desktop` | Desktop entry and `StartupWMClass` |
| `cargo-sources.json` | Vendored crates.io sources for the offline build |

## App identity

The Flatpak app id is `io.github.aryan007h.Music0`. It follows Flathub's
GitHub-namespace convention instead of claiming a domain the project does not
own, and it matches the Tauri identifier in `src-tauri/tauri.conf.json`.

The **storage namespace did not change**. See
[ADR 0002](adr/0002-separate-app-identity.md): data, cache and config paths
derive from `identity.rs` (`musico-online`), not from the Tauri identifier.

Inside the sandbox those directories appear under the app's private prefix:

| Path | Outside Flatpak | Inside Flatpak |
|---|---|---|
| Data | `~/.local/share/musico-online` | `~/.var/app/io.github.aryan007h.Music0/data/musico-online` |
| Cache | `~/.cache/musico-online` | `~/.var/app/io.github.aryan007h.Music0/cache/musico-online` |
| Config | `~/.config/musico-online` | `~/.var/app/io.github.aryan007h.Music0/config/musico-online` |

## Build locally

Prerequisites: `flatpak` and `flatpak-builder`, with the Flathub remote
configured. The manifest pins `org.gnome.Platform//51` and the Rust SDK
extension; `flatpak-builder` installs them on first use.

```sh
# one-time: the runtimes the manifest asks for
flatpak install flathub org.gnome.Platform//51 org.gnome.Sdk//51 \
  org.freedesktop.Sdk.Extension.rust-stable

# build and install the result for the current user
flatpak-builder --user --install --force-clean \
  --state-dir=build/.flatpak \
  build io.github.aryan007h.Music0.yml

flatpak run io.github.aryan007h.Music0
```

To test a working tree instead of the release tag, point a copy of the manifest
at the checkout:

```yaml
sources:
  - type: dir
    path: /absolute/path/to/Music0
  - cargo-sources.json
```

The offline build needs `cargo-sources.json`. Regenerate it whenever
`Cargo.lock` changes:

```sh
python3 flatpak-cargo-generator.py Cargo.lock -o flatpak/cargo-sources.json
```

[`flatpak-cargo-generator.py`](https://github.com/flatpak/flatpak-builder-tools/tree/master/cargo)
is not vendored in this repository; fetch it from `flatpak-builder-tools`.

## Sandbox behaviour

| Area | Behaviour |
|---|---|
| Network | `--share=network`; the catalog and resolvers need it |
| Audio | `--socket=pulseaudio`; `musico_playback` talks to ALSA, which the runtime routes to PipeWire/PulseAudio |
| Library folder | `--filesystem=xdg-music:ro` by default. Add more with Flatseal, or keep music under `~/Music` |
| Downloads | Written to the app-private cache, so `--filesystem=xdg-download` covers explicit exports only |
| Media keys / MPRIS | Owns `org.mpris.MediaPlayer2.musico-online` and the app-id name used by the single-instance plugin |
| `yt-dlp` | Bundled inside the sandbox at `/app/bin/yt-dlp` |

### Why `yt-dlp` is bundled

`resolve_stream()` shells out to `yt-dlp`. A Flatpak cannot see the host's
`PATH`, so an app that expects a system `yt-dlp` silently loses its last-resort
resolver, batch downloads and retry/resume. The manifest installs the pinned
upstream release into `/app`, which puts it on the sandbox `PATH`. The trade-off
is that the bundled copy only updates when a new build is released.

Bump both the URL and the `sha256` in the `yt-dlp` module, then update the
version note here:

- Current bundled version: **2026.10.26**

## Release checklist

1. Bump the version in `src-tauri/Cargo.toml`, `src-tauri/tauri.conf.json` and
   `package.json` (see `CHANGELOG.md`).
2. `cargo update -p musico-online` so `Cargo.lock` records the new version.
3. Regenerate `flatpak/cargo-sources.json`.
4. Add a `<release>` entry to the metainfo file.
5. Point the manifest's `tag:` at the new release tag.
6. Build locally before opening the Flathub update PR.

## Submitting to Flathub

The first submission is a pull request to
[`flathub/flathub`](https://github.com/flathub/flathub) adding the manifest to
the repository root. After it merges, Flathub creates
`flathub/io.github.aryan007h.Music0`, and subsequent releases are updates
to that repository.
