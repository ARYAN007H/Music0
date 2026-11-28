#!/usr/bin/env bash
set -euo pipefail

# Packages a Linux Compose Multiplatform `createDistributable` directory into a
# standalone x86_64 AppImage without requiring FUSE on CI runners.

APPIMAGETOOL_VERSION="1.9.1"
APPIMAGETOOL_URL="https://github.com/AppImage/appimagetool/releases/download/${APPIMAGETOOL_VERSION}/appimagetool-x86_64.AppImage"
APPIMAGETOOL_SHA256="ed4ce84f0d9caff66f50bcca6ff6f35aae54ce8135408b3fa33abfc3cb384eb0"

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/.." && pwd)"

APP_DIR=""
OUTPUT_PATH="${REPO_ROOT}/dist/music0-linux-x86_64.AppImage"
DESKTOP_FILE="${REPO_ROOT}/flatpak/io.github.aryan007h.Music0.desktop"
METAINFO_FILE="${REPO_ROOT}/flatpak/io.github.aryan007h.Music0.metainfo.xml"
ICONS_DIR="${REPO_ROOT}/android/desktopApp/icons"
CACHE_DIR="${XDG_CACHE_HOME:-${HOME}/.cache}/music0-build-tools"

usage() {
  cat <<'EOF'
Usage: scripts/package_appimage.sh [options]

Options:
  --app-dir PATH      Path to Compose `createDistributable` app directory
  --output PATH       Output AppImage file (default: dist/music0-linux-x86_64.AppImage)
  --desktop PATH      Desktop entry file (default: flatpak/io.github.aryan007h.Music0.desktop)
  --metainfo PATH     AppStream metainfo XML (default: flatpak/io.github.aryan007h.Music0.metainfo.xml)
  --icons-dir PATH    Directory containing icon.png, 32x32.png, 128x128.png, 128x128@2x.png
  --cache-dir PATH    Tool cache directory for pinned appimagetool
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
    --desktop)
      [ "$#" -ge 2 ] || { echo "Missing value for --desktop" >&2; exit 1; }
      DESKTOP_FILE="$2"; shift 2 ;;
    --metainfo)
      [ "$#" -ge 2 ] || { echo "Missing value for --metainfo" >&2; exit 1; }
      METAINFO_FILE="$2"; shift 2 ;;
    --icons-dir)
      [ "$#" -ge 2 ] || { echo "Missing value for --icons-dir" >&2; exit 1; }
      ICONS_DIR="$2"; shift 2 ;;
    --cache-dir)
      [ "$#" -ge 2 ] || { echo "Missing value for --cache-dir" >&2; exit 1; }
      CACHE_DIR="$2"; shift 2 ;;
    -h|--help)
      usage; exit 0 ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 1 ;;
  esac
done

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
LAUNCHER_NAME="$(basename -- "${LAUNCHERS[0]}")"

for req_file in \
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

verify_tool_sha256() {
  local file_path="$1"
  local expected="$2"
  echo "${expected}  ${file_path}" | sha256sum -c - >/dev/null 2>&1
}

TMP_TOOL=""
STAGE_DIR=""
cleanup() {
  [ -n "$TMP_TOOL" ] && rm -f "$TMP_TOOL" || true
  [ -n "$STAGE_DIR" ] && rm -rf "$STAGE_DIR" || true
}
trap cleanup EXIT

mkdir -p "$CACHE_DIR"
APPIMAGETOOL="${CACHE_DIR}/appimagetool-${APPIMAGETOOL_VERSION}-x86_64.AppImage"
if [ ! -f "$APPIMAGETOOL" ] || ! verify_tool_sha256 "$APPIMAGETOOL" "$APPIMAGETOOL_SHA256"; then
  rm -f "$APPIMAGETOOL"
  TMP_TOOL="${APPIMAGETOOL}.tmp.$$"
  echo "Downloading appimagetool ${APPIMAGETOOL_VERSION}..."
  curl -fsSLo "$TMP_TOOL" "$APPIMAGETOOL_URL"
  verify_tool_sha256 "$TMP_TOOL" "$APPIMAGETOOL_SHA256" || {
    echo "SHA-256 verification failed for downloaded appimagetool" >&2
    exit 1
  }
  chmod 0755 "$TMP_TOOL"
  mv -f "$TMP_TOOL" "$APPIMAGETOOL"
  TMP_TOOL=""
fi

STAGE_DIR="$(mktemp -d)"

APPDIR_PATH="${STAGE_DIR}/Music0.AppDir"
mkdir -p \
  "${APPDIR_PATH}/usr/lib/music0" \
  "${APPDIR_PATH}/usr/bin" \
  "${APPDIR_PATH}/usr/share/applications" \
  "${APPDIR_PATH}/usr/share/metainfo" \
  "${APPDIR_PATH}/usr/share/icons/hicolor/32x32/apps" \
  "${APPDIR_PATH}/usr/share/icons/hicolor/128x128/apps" \
  "${APPDIR_PATH}/usr/share/icons/hicolor/256x256/apps" \
  "${APPDIR_PATH}/usr/share/icons/hicolor/512x512/apps"

# Keep jpackage's bin/ and lib/ sibling layout intact under usr/lib/music0/ so
# /proc/self/exe resolution finds lib/app/*.cfg and bundled native libs.
cp -a "${APP_DIR}/." "${APPDIR_PATH}/usr/lib/music0/"

cat > "${APPDIR_PATH}/usr/bin/music0" <<EOF
#!/usr/bin/env bash
set -euo pipefail
export MALLOC_ARENA_MAX="\${MALLOC_ARENA_MAX:-2}"
SELF_DIR="\$(dirname "\$(readlink -f "\$0")")"
exec "\${SELF_DIR}/../lib/music0/bin/${LAUNCHER_NAME}" "\$@"
EOF
chmod 0755 "${APPDIR_PATH}/usr/bin/music0"

cat > "${APPDIR_PATH}/AppRun" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail
HERE="$(dirname "$(readlink -f "$0")")"
export APPDIR="$HERE"
export PATH="${HERE}/usr/bin:${PATH}"
export MALLOC_ARENA_MAX="${MALLOC_ARENA_MAX:-2}"
export XDG_DATA_DIRS="${HERE}/usr/share:${XDG_DATA_DIRS:-/usr/local/share:/usr/share}"
exec "${HERE}/usr/bin/music0" "$@"
EOF
chmod 0755 "${APPDIR_PATH}/AppRun"

install -Dm644 "$DESKTOP_FILE" "${APPDIR_PATH}/io.github.aryan007h.Music0.desktop"
install -Dm644 "$DESKTOP_FILE" "${APPDIR_PATH}/usr/share/applications/io.github.aryan007h.Music0.desktop"
install -Dm644 "$METAINFO_FILE" "${APPDIR_PATH}/usr/share/metainfo/io.github.aryan007h.Music0.metainfo.xml"

install -Dm644 "${ICONS_DIR}/32x32.png" "${APPDIR_PATH}/usr/share/icons/hicolor/32x32/apps/io.github.aryan007h.Music0.png"
install -Dm644 "${ICONS_DIR}/128x128.png" "${APPDIR_PATH}/usr/share/icons/hicolor/128x128/apps/io.github.aryan007h.Music0.png"
install -Dm644 "${ICONS_DIR}/128x128@2x.png" "${APPDIR_PATH}/usr/share/icons/hicolor/256x256/apps/io.github.aryan007h.Music0.png"
install -Dm644 "${ICONS_DIR}/icon.png" "${APPDIR_PATH}/usr/share/icons/hicolor/512x512/apps/io.github.aryan007h.Music0.png"
install -Dm644 "${ICONS_DIR}/icon.png" "${APPDIR_PATH}/io.github.aryan007h.Music0.png"
ln -sf "io.github.aryan007h.Music0.png" "${APPDIR_PATH}/.DirIcon"

mkdir -p "$(dirname -- "$OUTPUT_PATH")"
OUTPUT_DIR="$(cd -- "$(dirname -- "$OUTPUT_PATH")" && pwd)"
OUTPUT_ABS="${OUTPUT_DIR}/$(basename -- "$OUTPUT_PATH")"
TMP_OUT="${STAGE_DIR}/out.AppImage"

ARCH=x86_64 "$APPIMAGETOOL" --appimage-extract-and-run "$APPDIR_PATH" "$TMP_OUT"
[ -s "$TMP_OUT" ] || { echo "appimagetool failed to produce non-empty AppImage" >&2; exit 1; }
chmod 0755 "$TMP_OUT"
mv -f "$TMP_OUT" "$OUTPUT_ABS"

echo "Built AppImage: $OUTPUT_ABS ($(du -h "$OUTPUT_ABS" | cut -f1))"
