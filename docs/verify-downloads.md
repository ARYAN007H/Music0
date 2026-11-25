# Verifying your Music0 download

Every Music0 release ships with the tools to prove the file you downloaded is
the file we published. Nothing here requires trusting us — only math.

## 1. Check the SHA-256 checksum

Every release publishes a `SHA256SUMS.txt` containing the SHA-256 hash of each
artifact. Download it from the same release page as your installer.

**Linux / macOS:**

```sh
sha256sum -c SHA256SUMS.txt           # from the folder with your downloads
# macOS: shasum -a 256 -c SHA256SUMS.txt
```

**Windows (PowerShell):**

```powershell
Get-FileHash .\musico-online-native-aarch64.apk -Algorithm SHA256
# compare against the value in SHA256SUMS.txt
```

**Android:** any hash-checker app from F-Droid/Play can compute SHA-256 for a
downloaded file — compare against `SHA256SUMS.txt`.

If the hash matches, the file is bit-for-bit what CI published.

## 2. Check it on VirusTotal

The latest release APK is uploaded to [VirusTotal](https://www.virustotal.com)
after every release. For **v1.0.0**:

- Scan result: **0/66 vendors flagged** — <https://www.virustotal.com/gui/file/69f15b35d987f962e2af4dc9ad987a058b0349ea1ae44a88704a8fca7bc1f013>
- SHA-256: `69f15b35d987f962e2af4dc9ad987a058b0349ea1ae44a88704a8fca7bc1f013`

For other releases, upload the artifact to VirusTotal or search by its
SHA-256 from `SHA256SUMS.txt`.

## 3. Check the signing certificate

All Android APKs since v0.9.5 are signed with the same key:

```
Certificate SHA-256: 0cd961a9c2ac9f6655040975e672739d79901fd2862c31e8f6508e85b4f12353
Subject: CN=Musico Online, OU=Media, O=ARYAN007H
```

Advanced users can confirm:

```sh
apksigner verify --print-certs musico-online-native-aarch64.apk
```

An update signed with this key installs in place over a previous Music0
install, keeping your local data (likes, playlists, downloads).

## 4. About the Google Play Protect warning

When installing or updating on Android, Play Protect may show **"Harmful app
blocked — This app may be harmful."** This is a **false positive**: Play
Protect flags any APK it has not seen before, especially apps that update
themselves outside the Play Store. Every self-distributed FOSS Android app
(NewPipe, etc.) gets the same dialog.

**Tap "Install anyway."** The build is safe — verify it yourself with steps
1–3 above.

A false-positive appeal was submitted to Google (with the VirusTotal 0/66
result) so the warning clears as the app accumulates install reputation.
Should the verdict ever change, this page will say so.

## 5. Reporting problems

If a downloaded file's hash does **not** match `SHA256SUMS.txt`, do not
install it — open an issue at
<https://github.com/ARYAN007H/Music0/issues> with the download link and the
hash you got.
