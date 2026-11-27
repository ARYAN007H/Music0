# Frontend & UI Layer

Music0's UI for both **Android** and **Desktop** (Linux, macOS, Windows) is built in **Kotlin Compose Multiplatform** (`android/shared/`). Tauri and WebViews were retired in `v1.0.0` ([ADR-0005](adr/0005-compose-multiplatform-and-jni-core.md)), and the legacy web/PWA preview was retired post-`v1.0.1`.

---

## 1. Shell, Navigation & State (`android/shared/`)

All shipped Android (`:app`) and Desktop (`:desktopApp`) interfaces live under `android/shared/src/commonMain/kotlin/io/github/aryan007h/musicoonline/`:

- **Shell & adaptive layout (`ui/MusicoApp.kt`):** Uses an `840.dp` width breakpoint:
  - **Compact (`< 840.dp`):** Bottom capsule navigation bar + swipeable mini-player.
  - **Expanded (`≥ 840.dp`):** Sidebar navigation rail (`224.dp`–`256.dp`) + `98.dp` floating player dock + split-stage Now Playing.
- **State management (`ui/MusicViewModel.kt`):** Holds unidirectional `StateFlow` state, calls Rust over JNI via [`MusicoCore.kt`](../android/shared/src/commonMain/kotlin/io/github/aryan007h/musicoonline/core/MusicoCore.kt) on `Dispatchers.IO`, and persists UI preferences in `PlatformPrefs`. See [docs/ipc.md](ipc.md).
- **Primary tabs (`AppTab`):** `HomeScreen`, `RadioScreen`, `LibraryScreen`, `SearchScreen`.
- **Overlays & sheets (`AppOverlay`):** `NowPlayingScreen` (`LyricsPane`, `Backdrops`), `SettingsScreen`, `ProfileScreen`, `DetailScreen`, `PlaylistDetailScreen`, `EqScreen`, `OnboardingScreen`, `PlaylistSheets`.
- **Player energy gating:** While `overlay == AppOverlay.PLAYER`, `DesktopDockedPlayerBar` and `MiniPlayer` leave composition via `AnimatedVisibility` (`state.overlay != AppOverlay.PLAYER`) so background frame loops (`MiniPlayerLiveProgressBar`, `EqualizerBars`, `basicMarquee`) do not tick behind the full-screen player; on Android (`MainActivity.kt`), high refresh rate mode is gated via `lifecycleScope` + `repeatOnLifecycle(STARTED)` on `overlay == AppOverlay.PLAYER` (reset in `onStop()` and deduplicating `preferredDisplayModeId` writes).

---

## 2. Material 3 Expressive Theme & Light/Dark `ThemeMode`

- **Theme modes (`ui/theme/Theme.kt`):** `ThemeMode` supports `SYSTEM` (`"system"`), `LIGHT` (`"light"`), and `DARK` (`"dark"`), resolved against `isSystemInDarkTheme()` and persisted in `PlatformPrefs` under key `"theme_mode"` (`PREF_THEME_MODE` in `MusicViewModel.kt`). Users switch modes via `ThemeModeSelector` in `SettingsScreen.kt` and `ProfileScreen.kt` (plus the header toggle).
- **5 Palettes (`ui/theme/Palettes.kt`):** `NEBULA`, `OCEAN`, `EMBER`, `FOREST`, and `MONO`. Each palette defines both a dark scheme and bespoke `LightPaletteTokens` with a 5-rung tonal container surface ladder (`surfaceContainerLowest` through `surfaceContainerHighest`), high-contrast ink tokens, and distinct `primaryContainer` / `secondaryContainer` / `tertiaryContainer` tones.
- **Solid M3E tonal surfaces:** Screens use solid stepped tonal surfaces from `MaterialTheme.colorScheme`, `4.dp`-aligned `MusicoSpacing`, `MusicoTypography` (with `tnum` tabular numerals), and `MusicoShapes` (`tile`, `squircle`, `card`, `hero`, `pillDock`, `cardLarge`, `chip`/`pill`, `sheet`, plus `24.dp`/`6.dp` segmented list cards).

---

## 3. Zero-Blur / Zero-Gradient Geometric Backdrops (`Backdrops.kt`)

All five Now Playing & Lyrics Studio backdrops in `ui/player/Backdrops.kt` (`PlayerBackdropSpecs`) are **strictly zero-blur and zero-gradient**, constructed from crisp geometric vector shapes and stepped solid M3E tonal surfaces that adapt to both Light and Dark modes and pause frame-driven motion when `isPlaying == false`:

| ID | Label | Visual construction |
|---|---|---|
| `mesh` | Fluid | Rotating M3E scalloped cookie, clover, and pill vector geometry |
| `artwork` | Glow | Concentric studio vinyl rings and rotating tonal arcs |
| `aurora` | Aurora | Layered stepped sinusoidal vector ribbons |
| `rain` | Rain | Calm falling M3E rounded-capsule rhythm bars |
| `black` | Dark | Deep OLED studio black (Dark) / crisp paper surface (Light), static (`animatesMotion = false`) |

`ArtworkPaletteCache` (`ui/player/Backdrops.kt`) extracts deterministic artwork atmosphere tones from track thumbnails to harmonize backdrop surfaces without blurs.

---

## 4. Expressive Controls & Shapes

- **`ui/components/ExpressiveControls.kt`:** Provides custom Material 3 Expressive interactive primitives and testable math helpers (`ExpressiveSeekMath`, `ExpressiveSliderMath`):
  - `ExpressiveSwitch` (thumb check glyph), `ExpressiveSlider`, `ExpressivePlayPauseButton`, `ExpressiveTransportButton`, `ExpressiveActionPill`
  - `ExpressiveConnectedButtonGroup` (selected segment morphs to full pill), `ExpressiveFilterChip`
  - `ExpressiveSegmentedItem` and `ExpressiveSegmentedColumn` (Android 16 / Pixel Settings-style `24.dp` outer / `6.dp` inner connected list cards)
  - `ExpressiveWavySeekBar` and `ExpressiveLiveWavySeekBar` (animated wave when playing and not dragging; pending-seek hold without snapback)
- **`ui/theme/ExpressiveShapes.kt`:** Provides `ExpressiveScallopShape`, `ExpressiveArchShape`, `ExpressiveSegmentedMath` (`verticalListCorners`, `connectedButtonCorners`, `scallopRadiusFactor`), and `ExpressiveShapes` presets (`Cookie8`, `Cookie10`, `SoftBurst12`, `Clover4`, `Flower6`, `Arch`) used on hero marks, avatars, artist frames, and radio emblems.
- **Motion safety (`ui/theme/Motion.kt`):** Spring animations clamp corner radii with `safeCornerDp` (`>= 0.dp`) and scales with `safePositiveScale` so underdamped springs never produce negative geometry.

---

## 5. Where to go next

- [brand/BRAND_GUIDELINES.md](brand/BRAND_GUIDELINES.md) — TE Dial-Zero brand marks and M3E rules.
- [ipc.md](ipc.md) — production JNI command catalog (`MusicoCore.kt` ↔ `musico_core/src/mobile.rs`).
- [development.md](development.md) — build and run Compose Multiplatform Desktop and Android.
- [ARCHITECTURE.md](../ARCHITECTURE.md) — full system architecture.
