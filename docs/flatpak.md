# Flatpak & AppImage packaging

The Linux desktop distribution manifests live in [`flatpak/`](../flatpak),
shared by `scripts/package_appimage.sh` and `scripts/package_flatpak.sh` to
produce `music0-linux-x86_64.AppImage` and `music0-linux-x86_64.flatpak` from
the Compose Multiplatform `:desktopApp:createDistributable` app image.

| File | Purpose |
|---|---|
| `io.github.aryan007h.Music0.yml` | Flatpak manifest: Freedesktop runtime, sandbox permissions, bundled `yt-dlp` (`yt-dlp-ejs`) and `deno`, and staged Compose Desktop tree |
| `io.github.aryan007h.Music0.metainfo.xml` | AppStream metadata shown in software centres |
| `io.github.aryan007h.Music0.desktop` | Desktop entry (`Exec=music0 %U`, icons, `StartupWMClass`) |

## App identity

The Linux desktop app id is `io.github.aryan007h.Music0`. It follows Flathub's
GitHub-namespace convention instead of claiming a domain the project does not
own.

The **storage namespace did not change**. See
[ADR 0002](adr/0002-separate-app-identity.md): data, cache, and config paths
derive from `musico_core::identity::DATA_SUBDIR` (`musico-online`), not from
the package id. `DesktopPlatformHost` respects `XDG_DATA_HOME` and
`XDG_CACHE_HOME` before initializing `MusicoCore`, so inside the Flatpak sandbox
storage automatically resolves under `~/.var/app/io.github.aryan007h.Music0/`:

| Path | Outside Flatpak | Inside Flatpak |
|---|---|---|
| Data | `~/.local/share/musico-online` | `~/.var/app/io.github.aryan007h.Music0/data/musico-online` |
| Cache | `~/.cache/musico-online` | `~/.var/app/io.github.aryan007h.Music0/cache/musico-online` |
| Config | `~/.config/musico-online` | `~/.var/app/io.github.aryan007h.Music0/config/musico-online` |

## Build & install locally

Prerequisites: Rust stable (MSRV 1.78), Java 17, Gradle 8.10.2, `libasound2-dev`,
`flatpak`, and `flatpak-builder`.

```sh
# 1. Build the Rust JNI core and stage it into Desktop resources
cargo build --release -p musico_core --lib
mkdir -p android/desktopApp/resources/linux
cp target/release/libmusico_online_lib.so android/desktopApp/resources/linux/

# 2. Build the Compose Multiplatform distributable tree
gradle -p android :desktopApp:createDistributable

# 3. Package AppImage and/or Flatpak bundle
bash scripts/package_appimage.sh
bash scripts/package_flatpak.sh

# 4. Install and run the single-file Flatpak bundle
flatpak install --user dist/music0-linux-x86_64.flatpak
flatpak run io.github.aryan007h.Music0
```

`scripts/package_flatpak.sh` stages the prebuilt `createDistributable` directory
together with `flatpak/` and `android/desktopApp/icons/` in a temporary
workspace, adds the `--user` Flathub remote if absent, invokes
`flatpak-builder --user --force-clean --disable-rofiles-fuse`, and exports a
self-contained `dist/music0-linux-x86_64.flatpak` with `--runtime-repo` set so
end-user installs automatically fetch `org.freedesktop.Platform//25.08`.

## Sandbox behaviour

| Area | Behaviour |
|---|---|
| Network | `--share=network`; catalog and stream resolvers need it |
| Display | `--socket=x11`, `--socket=wayland`, `--share=ipc`, `--device=dri` (`--socket=x11` is kept explicit because Java AWT/Skiko requires X11/XWayland even on Wayland sessions) |
| Audio | `--socket=pulseaudio`; `musico_playback` (CPAL) talks to ALSA, which the Freedesktop runtime routes to PipeWire/PulseAudio |
| Library folder | `--filesystem=xdg-music:ro` by default (`~/Music`). Grant additional folders via Flatseal if needed |
| Cache & DB | Written under the sandboxed XDG directories above |
| Media keys / MPRIS | Owns `org.mpris.MediaPlayer2.musico-online`; Flatpak grants the app-id D-Bus name automatically |
| Notifications | `--talk-name=org.freedesktop.Notifications` |
| `yt-dlp` | Bundled inside the sandbox at `/app/bin/yt-dlp` (with `yt-dlp-ejs`) |
| `deno` | Bundled inside the sandbox at `/app/bin/deno` |

### Why `yt-dlp` and `deno` are bundled

`resolve_stream()` shells out to `yt-dlp`. A Flatpak cannot see the host's
`PATH`, so an app expecting a system `yt-dlp` or JavaScript runtime would lose
its fallback resolver (`yt-dlp` disables external EJS downloads by default
and defaults `--js-runtimes` to `deno`). The manifest builds the `yt-dlp-extra`
zipapp offline from the pinned upstream source archive plus the pinned
`yt-dlp-ejs` wheel using Python, `make`, and `zip` from
`org.freedesktop.Sdk//25.08`, installs it to `/app/bin/yt-dlp`, and installs the
matching pinned `deno` release binary to `/app/bin/deno`.

- Current bundled `yt-dlp` version: **2026.10.26**
- Current bundled `yt-dlp-ejs` version: **0.8.0**
- Current bundled `deno` version: **2.9.8**
