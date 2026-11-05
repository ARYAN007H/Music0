# Release

Musico Online ships from Git tags. Pushing a tag that starts with `v` runs
`.github/workflows/release.yml`, builds three desktop platforms, and publishes
a GitHub release. There is no separate deploy step and no server component.

## 1. Versioning

The app uses Semantic Versioning with an `-online` prerelease suffix:

```text
v0.7.0-online
  │ │ │ └── prerelease channel marker
  │ │ └──── patch
  │ └────── minor
  └──────── major
```

Because the tag contains a hyphen, **every `-online` tag is published as a
GitHub prerelease**. That is intentional: the suffix marks the clean-room
online build, while `generate_release_notes` still produces a changelog. If a
stable (non-prerelease) release is ever needed, the tag must not contain a
hyphen.

The desktop version lives in three files and must match:

| File | Field |
|---|---|
| `src-tauri/Cargo.toml` | `[package] version` |
| `src-tauri/tauri.conf.json` | `version` |
| `package.json` | `version` |

`Cargo.lock` also records the app version; `cargo build` updates it. Do not
hand-edit the lock file.

Release history at the time of writing:

| Tag | Date |
|---|---|
| `v0.5.0-online` | 2026-10-29 |
| `v0.6.0-online` | 2026-11-02 |
| `v0.6.1-online` | 2026-11-02 |
| `v0.7.0-online` | 2026-11-05 |

## 2. Pre-release checklist

Run this from a clean `main` checkout. A release is cut from a commit that has
already passed review and CI.

- [ ] `git status` is clean; no untracked build output or local caches.
- [ ] CI is green on the commit being tagged
      (`.github/workflows/ci.yml`: Linux test job).
- [ ] `cargo test --workspace --exclude musico_playback` passes locally.
- [ ] `cargo test -p musico_playback` passes on a machine with an audio device
      (or the environmental failure is understood and noted).
- [ ] Live network gates pass: `cargo test -p musico-online live_ -- --ignored
      --nocapture`. Some tests need `yt-dlp`; give them several minutes.
- [ ] `npm test` and `npm run build` pass.
- [ ] Browser suites and the contrast gate pass for UI-changing releases:
      preview on 8143, contrast gate on 8145. See
      [testing.md](testing.md#4-browser-suites).
- [ ] `CHANGELOG.md` has a dated section for this version and the entries are
      user-facing, not commit-log noise.
- [ ] Version bumped in all three files (§1) and committed.
- [ ] Release notes call out unsigned macOS/Windows artifacts and the
      Android-local-build limitation.
- [ ] `workflow_dispatch` release run is green on the release branch before
      tagging (§4). This is how cross-platform compile breaks are caught
      without burning a tag.

## 3. Cut the release

```sh
# 1. Bump versions
$EDITOR src-tauri/Cargo.toml src-tauri/tauri.conf.json package.json

# 2. Update the changelog with a dated section
$EDITOR CHANGELOG.md

# 3. Prove the tree still builds and tests
cargo check --workspace
cargo test --workspace --exclude musico_playback

# 4. Commit and merge through the normal PR flow
git add src-tauri/Cargo.toml src-tauri/tauri.conf.json package.json CHANGELOG.md Cargo.lock
git commit -m "chore(release): v0.7.0-online"
git push

# 5. Tag the merge commit on main
git checkout main
git pull --ff-only
git tag -a v0.7.0-online -m "Musico Online v0.7.0"
git push origin v0.7.0-online
```

Use an annotated tag. Never move or force-push a published tag; cut a new patch
release instead.

## 4. Verify the workflow without tagging

`release.yml` also has `workflow_dispatch`. A dispatch run builds and tests
every platform job but skips the `publish` job, because `publish` runs only when
`github.ref` starts with `refs/tags/`.

```sh
gh workflow run release.yml --ref release/v0.7.0-online
gh run watch
```

Use this before merging a release branch that touches platform `cfg` code,
dependencies, or packaging. A dispatch run leaves no GitHub release behind.

## 5. What the workflow builds

### Jobs

| Job | Runner | Command | Artifacts |
|---|---|---|---|
| `linux` | `ubuntu-latest` | `tauri build --bundles deb,rpm,appimage` | `.deb`, `.rpm`, `.AppImage`, raw `musico-online-linux-x86_64` + `.tar.gz` |
| `macos` | `macos-latest` | `tauri build --target universal-apple-darwin --bundles dmg,app` | universal `.dmg`, `Musico Online.app` in a `.tar.gz` |
| `windows` | `windows-latest` | `tauri build --bundles msi,nsis` | `.msi`, NSIS `.exe`, standalone `musico-online-windows-x86_64.exe` |
| `publish` | `ubuntu-latest` | Download all `dist-*`, sha256sum, `softprops/action-gh-release` | GitHub release with every artifact + `SHA256SUMS.txt` |

The workflow runs the Rust tests on each platform before building:

```sh
cargo test --workspace --exclude musico_playback
```

This has two purposes beyond testing: the macOS job natively type-checks
`cfg(target_os = "macos")` code (menus, media controls), and the Windows job
does the same for `cfg(target_os = "windows")` code (drawn chrome, SMTC).
Playback e2e is excluded on all three because CI runners have no audio device.

### Linux notes

- `APPIMAGE_EXTRACT_AND_RUN=1` lets `linuxdeploy` run without FUSE on the
  runner.
- System packages match [development.md](development.md#linux-system-packages).
- The raw binary is packaged separately so users on unsupported distros can run
  it without an installer.

### macOS notes

- Both `aarch64-apple-darwin` and `x86_64-apple-darwin` targets are installed;
  the output is a universal binary.
- Builds are **unsigned**: there is no Apple Developer identity in CI.
  Gatekeeper blocks the first launch. The release body documents
  right-click → **Open**, or `xattr -cr "/Applications/Musico Online.app"`.
- The app uses overlay traffic lights and a native App/Edit/View/Window menu.
  The Edit menu is required for ⌘C/⌘V/⌘A in WKWebView.

### Windows notes

- Builds are **unsigned**: SmartScreen warns on first run. There is no
  code-signing certificate in CI.
- The native title bar is removed at runtime and min/max/close are drawn by the
  frontend. A brief native-title-bar flash during launch is a known cosmetic
  limitation.
- Both the MSI and NSIS installers are produced; the standalone `.exe` is for
  portable use.
- The build step runs under bash: the Windows-default PowerShell splits the
  `--bundles msi,nsis` argument into two and the Tauri CLI rejects it.

### Android

Android is **not built by CI**. Build locally:

```sh
cargo tauri android init
cargo tauri android build --apk
```

The APK uses the pure-Rust resolvers (Piped, Invidious, InnerTube Direct,
JioSaavn) because it cannot spawn `yt-dlp`. It is not published as a release
asset.

## 6. Publish job details

The `publish` job:

1. Downloads every artifact with the `dist-*` prefix into one directory.
2. Writes `SHA256SUMS.txt` over all files.
3. Creates the release named `Musico Online <tag>`.
4. Sets `prerelease` from `contains(github.ref_name, '-')`.
5. Uploads every file, failing if a declared file is missing.
6. Generates release notes and prepends the standard body describing platform
   artifacts, signing status, Android, and the PWA.

The release body is defined inline in the workflow. Keep it in sync with the
actual artifact names when packaging changes.

## 7. Post-release verification

- [ ] The GitHub release exists and is marked prerelease.
- [ ] Every expected artifact is attached and `SHA256SUMS.txt` is present.
- [ ] Download the Linux AppImage and raw binary; confirm they launch.
- [ ] On macOS, confirm the universal DMG opens after the documented Gatekeeper
      workaround.
- [ ] On Windows, confirm the MSI/NSIS installer runs and the drawn window
      controls work.
- [ ] `CHANGELOG.md` on `main` matches the published notes.
- [ ] The tag points at the intended merge commit.

## 8. Hotfixes

For a regression in a published release:

1. Branch from the release tag (`fix/v0.7.1-online`), not from a later `main`
   that contains unrelated work.
2. Make the minimal fix, add a regression test, and update the changelog under
   a new dated section.
3. Bump the patch version in all three files.
4. Run the full local gate plus a `workflow_dispatch` verification.
5. Tag `v0.7.1-online` and push.
6. Merge the fix back to `main`.

Do not delete a bad release silently. Publish the patch and, if necessary, mark
the previous release as superseded in its notes.

## 9. Release cadence and scope

The project releases when a coherent set of user-visible changes is ready, not
on a fixed calendar. A release should be independently installable and
rollback-safe: no half-migrated sled schema, no dependency on an unreleased
server, and no artifact that only works on the maintainer's machine.

Release-blocking classes:

- a data migration that cannot read an existing database;
- a platform that no longer compiles in the release workflow;
- a resolver change that breaks the live gates without a documented fallback;
- a security fix that is not in the release.

Non-blocking, documented limitations belong in the release notes and
`README.md`, not in a silent delay.

## 10. Where to go next

- [CHANGELOG.md](../CHANGELOG.md) — release history and entry format.
- [testing.md](testing.md) — the gates referenced above.
- [development.md](development.md#3-running-the-app) — local bundle builds.
- [troubleshooting.md](troubleshooting.md#9-release-and-packaging) — artifact
  and signing problems.
