# Release

Music0 ships from Git tags. Pushing a tag that starts with `v` runs
`.github/workflows/release.yml`, builds all four Compose Multiplatform targets
(Linux, macOS, Windows, and Android) backed by `musico_core`, and publishes a
GitHub Release with checksums.

## 1. Versioning

The app uses Semantic Versioning (`v1.0.0`, `v1.0.1`, …). Tags without a hyphen
publish as a full **Latest Release** on GitHub (picked up automatically by the
in-app updater via `/releases/latest`). Tags with a hyphen (e.g. `v1.1.0-rc1`)
publish as a GitHub prerelease.

> Note: macOS (`dmg`) and Windows (`msi`) `jpackage` installers require a
> strictly numeric `MAJOR.MINOR.PATCH` version with `MAJOR >= 1`.

Keep versions synchronized across these files:

| File | Field |
|---|---|
| `musico_core/Cargo.toml` | `[package] version` |
| `android/app/build.gradle.kts` | `musicoVersionCode`, `musicoVersionName` |
| `android/desktopApp/build.gradle.kts` | `musicoVersionName` default |
| `android/shared/src/desktopMain/.../DesktopPlatform.kt` | `DesktopPlatformUpdater.currentVersion()` |
| `package.json` | `version` |

## 2. Pre-release checklist

- [ ] `git status` is clean; no untracked build outputs or native binaries in `android/desktopApp/resources/`.
- [ ] CI is green (`.github/workflows/ci.yml`: `rust-core`, `compose-multiplatform`, `web-engine`).
- [ ] `cargo test --workspace --exclude musico_playback` passes.
- [ ] `gradle -p android :app:compileDebugKotlin :desktopApp:compileKotlin :shared:desktopTest` passes.
- [ ] `npm test` and `npm run build` pass.
- [ ] `CHANGELOG.md` has a dated section for this version.
- [ ] Versions bumped in all five files (§1).
- [ ] `workflow_dispatch` release run is green on the release branch before tagging.

## 3. Cut the release

```sh
# 1. Commit release bump & changelog on main
git add -A
git commit -m "chore(release): v1.0.1"
git push origin main

# 2. Tag and push to trigger .github/workflows/release.yml
git tag -a v1.0.1 -m "Music0 v1.0.1 — Compose Multiplatform (Android, Linux, macOS, Windows)"
git push origin v1.0.1
```

## 4. Verify without tagging

`release.yml` supports `workflow_dispatch` so all four platform builds can be
verified on any branch without creating a GitHub Release (`publish` only runs on
`refs/tags/v*`):

```sh
gh workflow run release.yml --ref main
gh run watch
```

## 5. What the workflow builds

| Job | Runner | Artifacts |
|---|---|---|
| `linux-desktop` | `ubuntu-latest` | `music0-linux-x86_64.deb`, `music0-linux-x86_64.rpm`, `music0-linux-x86_64.AppImage`, `music0-linux-x86_64.flatpak`, `music0-linux-x86_64.tar.gz` |
| `macos-desktop` | `macos-latest` | `Music0-macos-arm64.dmg`, `Music0-macos-arm64.app.tar.gz` |
| `windows-desktop` | `windows-latest` | `Music0-windows-x86_64.msi`, `Music0-windows-x86_64.zip` |
| `android-native` | `ubuntu-latest` | Signed `musico-online-native-aarch64.apk` |
| `publish` | `ubuntu-latest` | GitHub Release with all artifacts + `SHA256SUMS.txt` |

Each desktop job compiles `musico_core` as a release `cdylib`
(`libmusico_online_lib.{so,dylib,dll}`), stages it into
`android/desktopApp/resources/<os>/`, and packages the Compose Multiplatform
desktop distribution with `appResourcesRootDir` so the native library loads
automatically at launch.
