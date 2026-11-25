#!/usr/bin/env bash
set -euo pipefail

# Packages a Linux Compose Multiplatform `createDistributable` directory into a
# single-file x86_64 `.flatpak` bundle via flatpak-builder.

APP_ID="io.github.aryan007h.Music0"
FLATHUB_REPO_URL="https://dl.flathub.org/repo/flathub.flatpakrepo"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

APP_DIR=""
OUTPUT_PATH="${REPO_ROOT}/dist/music0-linux-x86_64.flatpak"
MANIFEST_FILE="${REPO_ROOT}/flatpak/${APP_ID}.yml"
DESKTOP_FILE="${REPO_ROOT}/flatpak/${APP_ID}.desktop"
METAINFO_FILE="${REPO_ROOT}/flatpak/${APP_ID}.metainfo.xml"
ICONS_DIR="${REPO_ROOT}/android/desktopApp/icons"
STATE_DIR=""

usage() {
  cat <<'EOF'
Usage: scripts/package_flatpak.sh [options]

Options:
  --app-dir PATH      Path to Compose `createDistributable` app directory
  --output PATH       Output .flatpak bundle (default: dist/music0-linux-x86_64.flatpak)
  --manifest PATH     Flatpak manifest YAML (default: flatpak/io.github.aryan007h.Music0.yml)
  --desktop PATH      Desktop entry file (default: flatpak/io.github.aryan007h.Music0.desktop)
  --metainfo PATH     AppStream metainfo XML (default: flatpak/io.github.aryan007h.Music0.metainfo.xml)
  --icons-dir PATH    Directory containing icon.png, 32x32.png, 128x128.png, 128x128@2x.png
  --state-dir PATH    Optional persistent flatpak-builder state directory
  -h, --help          Show this help message
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --app-dir)
      [ "$#" -ge 2 ] || { echo "Missing value for --app-dir" >&2; exit 1; }
      APP_DIR="$2"; shift 2 ;;
    --output)
      [ "$#" -ge 2 ] || { echo "Missing value for --output" >&2; exit 1; }
      OUTPUT_PATH="$2"; shift 2 ;;
    --manifest)
      [ "$#" -ge 2 ] || { echo "Missing value for --manifest" >&2; exit 1; }
      MANIFEST_FILE="$2"; shift 2 ;;
    --desktop)
      [ "$#" -ge 2 ] || { echo "Missing value for --desktop" >&2; exit 1; }
      DESKTOP_FILE="$2"; shift 2 ;;
    --metainfo)
      [ "$#" -ge 2 ] || { echo "Missing value for --metainfo" >&2; exit 1; }
      METAINFO_FILE="$2"; shift 2 ;;
    --icons-dir)
      [ "$#" -ge 2 ] || { echo "Missing value for --icons-dir" >&2; exit 1; }
      ICONS_DIR="$2"; shift 2 ;;
    --state-dir)
      [ "$#" -ge 2 ] || { echo "Missing value for --state-dir" >&2; exit 1; }
      STATE_DIR="$2"; shift 2 ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1 ;;
  esac
done

command -v flatpak >/dev/null 2>&1 || { echo "flatpak is required" >&2; exit 1; }
command -v flatpak-builder >/dev/null 2>&1 || { echo "flatpak-builder is required" >&2; exit 1; }

if [ -z "$APP_DIR" ]; then
  APP_PARENT="${REPO_ROOT}/android/desktopApp/build/compose/binaries/main/app"
  [ -d "$APP_PARENT" ] || { echo "Compose binaries directory not found: $APP_PARENT" >&2; exit 1; }
  mapfile -t FOUND_DIRS < <(find "$APP_PARENT" -mindepth 1 -maxdepth 1 -type d | sort)
  if [ "${#FOUND_DIRS[@]}" -ne 1 ]; then
    echo "Expected exactly 1 distributable directory in $APP_PARENT, found ${#FOUND_DIRS[@]}" >&2
    exit 1
  fi
  APP_DIR="${FOUND_DIRS[0]}"
fi

APP_DIR="$(cd -- "$APP_DIR" && pwd)"
[ -d "${APP_DIR}/bin" ] || { echo "Missing bin/ in app directory: $APP_DIR" >&2; exit 1; }
[ -d "${APP_DIR}/lib" ] || { echo "Missing lib/ in app directory: $APP_DIR" >&2; exit 1; }

mapfile -t LAUNCHERS < <(find "${APP_DIR}/bin" -mindepth 1 -maxdepth 1 -type f -perm -0100 | sort)
if [ "${#LAUNCHERS[@]}" -ne 1 ]; then
  echo "Expected exactly 1 executable launcher in ${APP_DIR}/bin, found ${#LAUNCHERS[@]}" >&2
  exit 1
fi

for req_file in \
  "$MANIFEST_FILE" \
  "$DESKTOP_FILE" \
  "$METAINFO_FILE" \
  "${ICONS_DIR}/32x32.png" \
  "${ICONS_DIR}/128x128.png" \
  "${ICONS_DIR}/128x128@2x.png" \
  "${ICONS_DIR}/icon.png"; do
  [ -f "$req_file" ] || { echo "Required packaging file not found: $req_file" >&2; exit 1; }
done

if command -v desktop-file-validate >/dev/null 2>&1; then
  desktop-file-validate "$DESKTOP_FILE"
fi
if command -v appstreamcli >/dev/null 2>&1; then
  appstreamcli validate --no-net "$METAINFO_FILE"
fi

WORK_DIR="$(mktemp -d)"
cleanup() {
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

mkdir -p \
  "${WORK_DIR}/manifest/staging/dist-app" \
  "${WORK_DIR}/manifest/staging/icons" \
  "${WORK_DIR}/build" \
  "${WORK_DIR}/repo"

cp "$MANIFEST_FILE" "${WORK_DIR}/manifest/${APP_ID}.yml"
cp -a "${APP_DIR}/." "${WORK_DIR}/manifest/staging/dist-app/"
cp "$DESKTOP_FILE" "${WORK_DIR}/manifest/staging/${APP_ID}.desktop"
cp "$METAINFO_FILE" "${WORK_DIR}/manifest/staging/${APP_ID}.metainfo.xml"
cp "${ICONS_DIR}/32x32.png" "${ICONS_DIR}/128x128.png" "${ICONS_DIR}/128x128@2x.png" "${ICONS_DIR}/icon.png" \
  "${WORK_DIR}/manifest/staging/icons/"

flatpak remote-add --user --if-not-exists flathub "$FLATHUB_REPO_URL"

BUILDER_ARGS=(
  --user
  --force-clean
  --disable-rofiles-fuse
  --install-deps-from=flathub
  --repo="${WORK_DIR}/repo"
)
if [ -n "$STATE_DIR" ]; then
  mkdir -p "$STATE_DIR"
  BUILDER_ARGS+=(--state-dir="$STATE_DIR")
else
  BUILDER_ARGS+=(--state-dir="${WORK_DIR}/state")
fi

flatpak-builder "${BUILDER_ARGS[@]}" "${WORK_DIR}/build" "${WORK_DIR}/manifest/${APP_ID}.yml"

mkdir -p "$(dirname -- "$OUTPUT_PATH")"
OUTPUT_DIR="$(cd -- "$(dirname -- "$OUTPUT_PATH")" && pwd)"
OUTPUT_ABS="${OUTPUT_DIR}/$(basename -- "$OUTPUT_PATH")"
TMP_OUT="${WORK_DIR}/out.flatpak"

flatpak build-bundle \
  --runtime-repo="$FLATHUB_REPO_URL" \
  "${WORK_DIR}/repo" \
  "$TMP_OUT" \
  "$APP_ID"

[ -s "$TMP_OUT" ] || { echo "flatpak build-bundle failed to produce non-empty bundle" >&2; exit 1; }
mv -f "$TMP_OUT" "$OUTPUT_ABS"

echo "Built Flatpak bundle: $OUTPUT_ABS ($(du -h "$OUTPUT_ABS" | cut -f1))"
