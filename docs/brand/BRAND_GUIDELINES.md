# Music0 Brand & UI Guidelines

**Music0** (`ARYAN007H/Music0`) splits its visual system cleanly into two scopes:
1. **Brand Identity (Icon, Logo Mark & Banner):** **TE Dial-Zero** — flat tactile hardware minimalism.
2. **In-App UI Elements (from `v1.0.1` onward):** **Material 3 Expressive only** — adaptive dark tonal surfaces, expressive rounded geometry, and spring-driven motion.

---

## 1. Brand Assets Scope: Icon, Mark & Banner (TE Dial-Zero)

The TE Dial-Zero specification governs the **app icon**, **logo mark**, and **hero banner** (`docs/brand/`, `android/desktopApp/icons/`, `android/app/src/main/res/`, and `frontend/icons/`).

### Strict Craft Mandate (Icon, Mark & Banner Only)
- **Zero gradients** (`<linearGradient>`, `<radialGradient>` forbidden in brand SVG/icon assets).
- **Zero blur, drop-shadows, or glow filters**.
- **Pure flat solid hex surfaces** with crisp figure-ground separation and 60-30-10 color balance.

### Brand Color Tokens (`palette.json`)
| Token | Hex | Ratio / Role |
|---|---|---|
| `slate_teal` | `#26444D` | **60%** — Primary squircle ground & center hardware hub |
| `obsidian` | `#161618` | **Structural Well** — Recessed dial well & hollow `0` aperture |
| `warm_ivory` | `#EFEAE4` | **30%** — Sculptural disc-`0` ring (`18°..302°`) & center `0` |
| `terracotta_coral` | `#E88D72` | **10%** — Sole warm radial stream wedge (`314°..366°`) |
| `studio_teal` | `#4E7F8A` | **Complement** — Outer radial stream pulse arc (`314°..366°`) |
| `mono_white` | `#F5F5F3` | Monochrome light single-ink mark |
| `mono_dark` | `#161618` | Monochrome dark single-ink mark |

### Radial & Squircle Geometry (`512x512`)
- **Outer Silhouette:** Continuous superellipse squircle (`n = 4.4`) with transparent RGBA corners.
- **Dial Well:** Centered circle `r = 226` in `#161618`.
- **Disc-`0` Ring:** Annular sector `r = 96..182`, `18°..302°` in `#EFEAE4`.
- **Aligned Radial Stream:** Inner `#E88D72` wedge (`r = 96..182`) and outer `#4E7F8A` arc (`r = 194..218`) locked strictly to rays `314°..366°`.
- **Center `0` Hub:** `#26444D` hub (`r = 74`) housing an `#EFEAE4` stadium `0` (`60x96, rx=30`) with a punched `#161618` hollow (`26x54, rx=13`).

### Actual Asset Inventory
- **`docs/brand/`:**
  - [`BRAND_GUIDELINES.md`](BRAND_GUIDELINES.md) — this specification
  - [`palette.json`](palette.json) — Dial-Zero color & geometry tokens
  - [`music0-app-icon.svg`](music0-app-icon.svg) — master app icon SVG
  - [`music0-mark.svg`](music0-mark.svg) — dial-zero mark SVG
  - [`banner.png`](banner.png) — hero banner (`1280×640`)
- **Desktop app icons (`android/desktopApp/icons/`):** `32x32.png`, `128x128.png`, `128x128@2x.png`, `icon.png` (`512×512`), `icon.icns`, `icon.ico`.
- **Android adaptive launcher (`android/app/src/main/res/`):** `mipmap-anydpi-v26/ic_launcher.xml` + vector drawables `drawable/ic_launcher_background.xml`, `drawable/ic_launcher_foreground.xml`, `drawable/ic_launcher_monochrome.xml`.
- **Web/PWA icons (`frontend/icons/` & `frontend/manifest.json`):** `icon.svg`, `icon-192.png`, `icon-512.png`, `apple-touch-icon.png` (`theme_color: #26444D`, `background_color: #161618`).

---

## 2. In-App UI Design Language: Material 3 Expressive Only

From `v1.0.1` onward, **all in-app UI elements** across Android and Desktop Compose Multiplatform (`android/shared/`) use **Material 3 Expressive only**—Dial-Zero's zero-gradient / fixed-teal hardware constraints do not restrict in-app screens, player backdrops, or interactive components.

### Rules for New & Updated UI Elements
- **No legacy Cupertino / neumorphic / web-clone chrome:** Do not introduce iOS-style flat chrome or ad-hoc card styles in Compose. All new screens, dialogs, bottom sheets, navigation rails, and docks must compose from `Theme.kt` and `components/Common.kt`.
- **5-Rung Palette-Tinted Tonal Surface Ladder (`Theme.kt`):**
  - Use `MaterialTheme.colorScheme` (`surfaceContainerLowest` through `surfaceContainerHighest`, `primary`, `primaryContainer`, `secondary`/`accent`, `tertiary`) derived from the user's selected dark palette (`Palettes.kt`: `NEBULA`, `OCEAN`, `EMBER`, `FOREST`, `MONO`).
  - Avoid hard-coded hex colors or nested cards-inside-cards; use tonal container shifts and `4.dp`-aligned `MusicoSpacing` (`4 / 8 / 12 / 16 / 20 / 24 / 28 dp`).
- **Expressive Shape Hierarchy (`MusicoShapes`):**
  - `tile` (`20.dp`), `card` (`24.dp`), `hero` (`28.dp`), `pillDock` (`28.dp`), `cardLarge` (`32.dp`), `chip` (`999.dp` pill), `sheet` (`topStart/topEnd = 36.dp`).
  - Always clamp dynamic animated corners (`coerceAtLeast(0.dp)`) and scales (`safePositiveScale`) so underdamped springs never produce negative corner sizes.
- **Typography (`MusicoTypography`):**
  - Use `display`, `headline`, `titleLarge`, `section`, `overline`, `title`, `body`, `caption`, `tabularCaption` (with `tnum` for durations/counters), and `lyric*` styles—never ad-hoc `fontSize` literals.
- **Atmospheric Washes & Motion:**
  - Use `Modifier.musicoWash(...)` (offscreen cubic alpha feathering to `0.0` before bounds) and `musicoScrimBrush(...)` so gradients never show hard horizontal cutoffs.
  - Pair interactions with `Motion.kt` springs (`pressScale`, `staggerIn`, breathing halos) and matching-geometry shimmer skeletons for loading states.
