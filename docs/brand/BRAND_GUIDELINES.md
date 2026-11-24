# Music0 Brand Guidelines — TE Dial-Zero

**Music0** is an online music streaming player (`ARYAN007H/Music0`). Its visual identity pairs Dieter Rams / Teenage Engineering tactile hardware minimalism with Music0's Material 3 Expressive surface palette.

## Strict Craft Mandate
- **Zero gradients** (`<linearGradient>`, `<radialGradient>` forbidden).
- **Zero blur, drop-shadows, or glow filters**.
- **Pure flat solid hex surfaces** with crisp figure-ground separation and 60-30-10 color balance.

## Color Theory & Tokens
| Token | Hex | Ratio / Role |
|---|---|---|
| `slate_teal` | `#26444D` | **60%** — Primary squircle ground & center hardware hub |
| `obsidian` | `#161618` | **Structural Well** — Recessed dial well & hollow `0` aperture |
| `warm_ivory` | `#EFEAE4` | **30%** — Sculptural disc-`0` ring (`18°..302°`) & center `0` |
| `terracotta_coral` | `#E88D72` | **10%** — Sole warm radial stream wedge (`314°..366°`) & wordmark `0` |
| `studio_teal` | `#4E7F8A` | **Complement** — Outer radial stream pulse arc (`314°..366°`) |
| `mono_white` | `#F5F5F3` | Monochrome light single-ink mark |
| `mono_dark` | `#161618` | Monochrome dark single-ink mark |

## Radial & Squircle Geometry (`512x512`)
- **Outer Silhouette**: Continuous superellipse squircle (`n = 4.4`) with transparent RGBA corners.
- **Dial Well**: Centered circle `r = 226` in `#161618`.
- **Disc-`0` Ring**: Annular sector `r = 96..182`, `18°..302°` in `#EFEAE4`.
- **Aligned Radial Stream**: Inner `#E88D72` wedge (`r = 96..182`) and outer `#4E7F8A` arc (`r = 194..218`) locked strictly to rays `314°..366°`.
- **Center `0` Hub**: `#26444D` hub (`r = 74`) housing an `#EFEAE4` stadium `0` (`60x96, rx=30`) with a punched `#161618` hollow (`26x54, rx=13`).

## Typography
- **Display Wordmark**: `Inter Display` ExtraBold (`800`), tight tracking (`-4` to `-5`), `Music` in `#EFEAE4` (dark bg) or `#161618` (light bg) with `0` in `#E88D72`.
- **Editorial Subtitle**: `JetBrains Mono` Bold uppercase (`ONLINE MUSIC STREAMING`), wide tracking.

## Asset Inventory
- `svg/`: 10 source SVGs (`music0-app-icon`, `music0-circle-icon`, `music0-adaptive-bg`, `music0-adaptive-fg`, `music0-mark`, `music0-mark-mono-white`, `music0-mark-mono-dark`, `music0-wordmark-dark`, `music0-wordmark-light`, `music0-hero-banner`)
- `app-icon/`: PNGs `16..1024` + multi-size `music0.ico` (`16..256`)
- `logo-mark/`: Bare dial-zero mark (`16..512`) + true-cutout mono white & dark (`16..128`)
- `wordmark/`: Dark-bg and light-bg lockups (`256w`, `512w`, `1024w`)
- `android/`: `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/{ic_launcher,ic_launcher_round}.png` + `adaptive-108dp/ic_launcher_{background,foreground}_{density}.png`
- `banners/`: `music0-hero-banner-1280x640.png`
