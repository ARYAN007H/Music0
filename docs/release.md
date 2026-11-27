# Release

Music0 ships from Git tags. Pushing a tag that starts with `v` runs `.github/workflows/release.yml`, builds all four Compose Multiplatform targets (Linux, macOS, Windows, and Android) backed by `musico_core`, and publishes a GitHub Release with `SHA256SUMS.txt`.

## 1. Versioning

The app uses Semantic Versioning (`v1.0.0`, `v1.0.1`, …). Tags without a hyphen publish as a full **Latest Release** on GitHub (picked up automatically by the in-app updater via `/releases/latest`). Tags with a hyphen (e.g. `v1.1.0-rc1`) publish as a GitHub prerelease.

- Android and `musico_core::mobile::app_display_version()` append `-cmp` to the display version (`1.0.1-cmp`), while `release.yml` strips any `v` prefix and `-...` suffix for desktop package installers (`1.0.1`).
- macOS (`dmg`) and Windows (`msi`) `jpackage` installers require a strictly numeric `MAJOR.MINOR.PATCH` version with `MAJOR >= 1`.

Keep versions synchronized across these files (and check the fallback defaults in `.github/workflows/release.yml`):

| File | Field |
|---|---|
| `musico_core/Cargo.toml` | `[package] version` |
| `android/app/build.gradle.kts` | `musicoVersionCode`, `musicoVersionName` |
| `android/desktopApp/build.gradle.kts` | `musicoVersionName` default |
| `android/shared/src/desktopMain/.../DesktopPlatform.kt` | `DesktopPlatformUpdater.currentVersion()` |
| `package.json` | `version` |
| `flatpak/io.github.aryan007h.Music0.metainfo.xml` | `<releases>` top entry (`version`, `date`) |

## 2. Pre-release checklist

- [ ] `git status` is clean; no untracked build outputs or native binaries in `android/desktopApp/resources/` or `android/app/src/main/jniLibs/arm64-v8a/`.
- [ ] CI is green (`.github/workflows/ci.yml`: `rust-core`, `compose-multiplatform`, `web-engine`).
- [ ] `cargo test --workspace --exclude musico_playback` passes.
- [ ] `gradle -p android :app:compileDebugKotlin :desktopApp:compileKotlin :shared:desktopTest` passes.
- [ ] `npm test` and `npm run build` pass.
- [ ] `CHANGELOG.md` and `flatpak/io.github.aryan007h.Music0.metainfo.xml` have a dated section/entry for this version.
- [ ] Versions bumped in all synced files (§1), including `.github/workflows/release.yml` fallback defaults if applicable.
- [ ] `workflow_dispatch` release run is green on the release branch before tagging.

## 3. Cut the release

```sh
# 1. Commit release bump & changelog on main
git add -A
git commit -m "chore(release): bump version to v1.0.1"
git push origin main

# 2. Tag and push to trigger .github/workflows/release.yml
git tag -a v1.0.1 -m "Music0 v1.0.1 — Compose Multiplatform (Android, Linux, macOS, Windows)"
git push origin v1.0.1
```

## 4. Verify without tagging

`release.yml` supports `workflow_dispatch` so all four platform builds can be verified on any branch without creating a GitHub Release (`publish` only runs on `refs/tags/*`). Optional inputs `version_code` and `version_name` override `MUSICO_VERSION_OVERRIDE` and Android Gradle properties for updater testing:

```sh
gh workflow run release.yml --ref main
# or test updater downgrade simulation:
# gh workflow run release.yml --ref main -f version_code=1000000 -f version_name=1.0.0-cmp
gh run watch
```

For Linux PR previews without cutting a release, `.github/workflows/desktop-core-so.yml` (`Desktop CMP bundle (PR preview)`) also runs on `workflow_dispatch` and on PRs from `gemini/*`, `fix/*`, or `feat/*` branches, uploading `musico-desktop-test.zip` and `music0-linux-x86_64.AppImage`.

## 5. What the workflow builds

| Job | Runner | Artifacts |
|---|---|---|
| `linux-desktop` | `ubuntu-latest` | `music0-linux-x86_64.deb`, `music0-linux-x86_64.rpm`, `music0-linux-x86_64.AppImage`, `music0-linux-x86_64.flatpak`, `music0-linux-x86_64.tar.gz` |
| `macos-desktop` | `macos-latest` | `Music0-macos-arm64.dmg`, `Music0-macos-arm64.app.tar.gz` |
| `windows-desktop` | `windows-latest` | `Music0-windows-x86_64.msi`, `Music0-windows-x86_64.zip` |
| `android-native` | `ubuntu-latest` | Signed `musico-online-native-aarch64.apk` |
| `publish` | `ubuntu-latest` | GitHub Release with all 10 artifacts + `SHA256SUMS.txt` |

Each desktop job compiles `musico_core` as a release `cdylib` (`libmusico_online_lib.so` on Linux, `libmusico_online_lib.dylib` on macOS, `musico_online_lib.dll` on Windows), stages it into `android/desktopApp/resources/<os>/`, and packages the Compose Multiplatform desktop distribution with `appResourcesRootDir` so the native library loads automatically at launch.

### Local builds

- **Linux `.AppImage` / `.flatpak`:** see [docs/flatpak.md](flatpak.md#build--install-locally) (`scripts/package_appimage.sh` and `scripts/package_flatpak.sh`).
- **Desktop installers (`.deb` / `.rpm` / `.dmg` / `.msi`):** stage the release `musico_core` library under `android/desktopApp/resources/<os>/`, then run `gradle -p android :desktopApp:packageReleaseDistributionForCurrentOS` (or `:desktopApp:packageDeb`, `:desktopApp:packageRpm`, etc.).
- **Android APK:** build `libmusico_online_lib.so` via `cargo ndk -t arm64-v8a -o ../android/app/src/main/jniLibs build --release --lib` (plus NDK `libc++_shared.so`), then run `gradle -p android :app:assembleRelease` or `:app:assembleDebug`.

### Signing & required secrets

- **Android signing (`android-native`):** Requires four GitHub Actions repository secrets:
  - `ANDROID_KEYSTORE_B64` (base64-encoded release `.keystore`)
  - `ANDROID_KEYSTORE_PASSWORD`
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`
  After assembling, `release.yml` verifies the APK with `apksigner verify`.
- **Desktop signing limitation:** Linux, macOS, and Windows desktop packages are currently **unsigned** (no Apple Developer ID notarization or Windows Authenticode signature). Users rely on `SHA256SUMS.txt` ([verify-downloads.md](verify-downloads.md)).

### Rollback

- **Before publishing:** Fix on the branch, delete any prematurely pushed tag (`git push --delete origin vX.Y.Z && git tag -d vX.Y.Z`), and re-tag after `workflow_dispatch` is green.
- **After publishing a broken release:** Mark the faulty GitHub Release as a **Pre-release** (or delete it) so `/releases/latest` and the in-app `app.update.check` updater immediately revert to the previous healthy release, then cut a new patch tag (`vX.Y.(Z+1)`) rather than moving an existing tag.
