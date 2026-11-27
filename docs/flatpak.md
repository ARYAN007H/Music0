# Flatpak & AppImage packaging

The Linux desktop distribution manifests live in [`flatpak/`](../flatpak), shared by `scripts/package_appimage.sh` and `scripts/package_flatpak.sh` to produce `music0-linux-x86_64.AppImage` and `music0-linux-x86_64.flatpak` from the Compose Multiplatform `:desktopApp:createDistributable` app image.

| File | Purpose |
|---|---|
| `io.github.aryan007h.Music0.yml` | Flatpak manifest: Freedesktop runtime, sandbox permissions, bundled `yt-dlp` (`yt-dlp-ejs`) and `deno`, and staged Compose Desktop tree |
| `io.github.aryan007h.Music0.metainfo.xml` | AppStream metadata shown in software centres |
| `io.github.aryan007h.Music0.desktop` | Desktop entry (`Exec=music0 %U`, icons, `StartupWMClass`) |

## App identity & storage paths

The Linux desktop app ID is `io.github.aryan007h.Music0` (Flathub's GitHub-namespace convention).

The **storage namespace** remains `musico-online` (`musico_core::identity::DATA_SUBDIR`; see [ADR-0002](adr/0002-separate-app-identity.md)). Because `DesktopPlatformHost` appends `musico-online` to `XDG_DATA_HOME` / `XDG_CACHE_HOME` before calling `MusicoCore.init()`, `mobile.rs` exports `XDG_DATA_HOME` and `XDG_CONFIG_HOME` to `dataDir` and `XDG_CACHE_HOME` to `cacheDir`, and `identity.rs` joins `musico-online` again:

| Artifact | Outside Flatpak | Inside Flatpak (`~/.var/app/io.github.aryan007h.Music0/`) |
|---|---|---|
| UI prefs (`ui_prefs.json`) | `~/.local/share/musico-online/ui_prefs.json` | `.../data/musico-online/ui_prefs.json` |
| Rust `db/` & `settings.json` | `~/.local/share/musico-online/musico-online/{db/,settings.json}` | `.../data/musico-online/musico-online/{db/,settings.json}` |
| Rust caches (`online/`, `covers/`) | `~/.cache/musico-online/musico-online/{online/,covers/}` | `.../cache/musico-online/musico-online/{online/,covers/}` |

(`settings.json` resides inside the data directory above rather than `~/.config/musico-online/`.)

## Build & install locally

Prerequisites: Rust stable (MSRV 1.78), Java 17, Gradle 8.10.2, `libasound2-dev`, `flatpak`, and `flatpak-builder`.

```sh
# 1. Build the Rust JNI core and stage it into Desktop resources
cargo build --release -p musico_core --lib
mkdir -p android/desktopApp/resources/linux
cp target/release/libmusico_online_lib.so android/desktopApp/resources/linux/

# 2. Build the Compose Multiplatform distributable tree
gradle -p android :desktopApp:createDistributable

# 3. Package AppImage and/or Flatpak bundle
bash scripts/package_appimage.sh
# On Ubuntu 24.04+, allow unprivileged userns for flatpak-builder's bubblewrap sandbox:
sudo sysctl -w kernel.apparmor_restrict_unprivileged_userns=0 || true
bash scripts/package_flatpak.sh

# 4. Install and run the single-file Flatpak bundle
flatpak install --user dist/music0-linux-x86_64.flatpak
flatpak run io.github.aryan007h.Music0
```

`scripts/package_flatpak.sh` stages the prebuilt `createDistributable` directory together with `flatpak/` and `android/desktopApp/icons/` in a temporary workspace, adds the `--user` Flathub remote if absent, invokes `flatpak-builder --user --force-clean --disable-rofiles-fuse`, and exports a self-contained `dist/music0-linux-x86_64.flatpak` with `--runtime-repo` set so end-user installs automatically fetch `org.freedesktop.Platform//25.08`.

## Sandbox behaviour

| Area | Behaviour |
|---|---|
| Network | `--share=network`; catalog and stream resolvers need it |
| Display | `--socket=x11`, `--socket=wayland`, `--share=ipc`, `--device=dri` (`--socket=x11` is explicit because Java AWT/Skiko requires X11/XWayland even on Wayland sessions) |
| Audio | `--socket=pulseaudio`; `musico_playback` (CPAL) talks to ALSA, routed to PipeWire/PulseAudio by the Freedesktop runtime |
| Library folder | `--filesystem=xdg-music:ro` is declared in the manifest for legacy/future parity (Compose Multiplatform currently has no local music folder scanner) |
| Cache & DB | Written under the sandboxed XDG directories above |
| Media keys / MPRIS | `--own-name=org.mpris.MediaPlayer2.musico-online` is declared in the manifest (MPRIS D-Bus controls are not yet wired in Compose Desktop) |
| Notifications | `--talk-name=org.freedesktop.Notifications` |
| `yt-dlp` | Bundled inside the sandbox at `/app/bin/yt-dlp` (with `yt-dlp-ejs`) |
| `deno` | Bundled inside the sandbox at `/app/bin/deno` |

### Why `yt-dlp` and `deno` are bundled

`musico_core` shells out to `yt-dlp` as a last-resort fallback on Desktop (`open_yt_dlp_stream` / `run_yt_dlp`). A Flatpak cannot see the host's `PATH`, and modern `yt-dlp` disables external EJS downloads by default while expecting `deno` as its JS runtime (`--js-runtimes`). The manifest builds the `yt-dlp-extra` zipapp offline from the pinned upstream source archive plus the pinned `yt-dlp-ejs` wheel using Python, `make`, and `zip` from `org.freedesktop.Sdk//25.08`, installs it to `/app/bin/yt-dlp`, and installs the matching pinned `deno` release binary to `/app/bin/deno`.

- Current bundled `yt-dlp` version: **2026.10.26**
- Current bundled `yt-dlp-ejs` version: **0.8.0**
- Current bundled `deno` version: **2.9.8**
