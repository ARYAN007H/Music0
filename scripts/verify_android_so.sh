#!/usr/bin/env bash
# Verifies an Android arm64 ELF shared library and its companion jniLibs directory:
# 1. Valid ELF64 little-endian AArch64 shared object.
# 2. All DT_NEEDED dependencies are either Android NDK system libs or present in --jni-dir.
# 3. Zero unresolved C++ runtime symbols unless libc++_shared.so is both DT_NEEDED and present.
# 4. Zero unexpected unversioned strong undefined symbols.
# 5. All PT_LOAD segments are >= 16 KB aligned (Android 15+ compatibility).
# 6. Optionally confirms MusicoCore JNI entrypoints are exported (--check-musico-jni).
set -euo pipefail

SO=""
JNI_DIR=""
CHECK_MUSICO_JNI=0

while [ "$#" -gt 0 ]; do
  case "$1" in
    --so)
      SO="${2:-}"
      shift 2
      ;;
    --jni-dir)
      JNI_DIR="${2:-}"
      shift 2
      ;;
    --check-musico-jni)
      CHECK_MUSICO_JNI=1
      shift
      ;;
    -h|--help)
      sed -n '2,9p' "$0"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 2
      ;;
  esac
done

if [ -z "$SO" ]; then
  echo "Usage: $0 --so <path/to/lib.so> [--jni-dir <dir>] [--check-musico-jni]" >&2
  exit 2
fi

if [ ! -s "$SO" ]; then
  echo "ERROR: shared library missing or empty: $SO" >&2
  exit 1
fi

if [ -z "$JNI_DIR" ]; then
  JNI_DIR="$(dirname "$SO")"
fi

for tool in readelf nm; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "ERROR: required tool not found in PATH: $tool" >&2
    exit 1
  fi
done

HEADER="$(readelf -h "$SO")"
for expected in "Class:.*ELF64" "Data:.*little endian" "Type:.*DYN" "Machine:.*AArch64"; do
  if ! grep -Eq "$expected" <<<"$HEADER"; then
    echo "ERROR: $SO failed ELF header check ($expected)" >&2
    echo "$HEADER" >&2
    exit 1
  fi
done

is_ndk_system_lib() {
  case "$1" in
    libc.so|libm.so|libdl.so|liblog.so|libz.so|libandroid.so|libOpenSLES.so|libaaudio.so|libmediandk.so|libjnigraphics.so|libvulkan.so)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

is_allowed_unversioned_ndk_sym() {
  case "$1" in
    __android_log_write|__android_log_buf_write|__android_log_print|__android_log_vprint|__android_log_assert|__android_log_is_loggable|android_get_device_api_level|android_set_abort_message)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

NEEDED_LIBS="$(readelf -d "$SO" | sed -n 's/.*(NEEDED).*\[\(.*\)\].*/\1/p')"
if [ -z "$NEEDED_LIBS" ]; then
  echo "ERROR: no DT_NEEDED entries found in $SO" >&2
  exit 1
fi

HAS_LIBCXX_NEEDED=0
while IFS= read -r lib; do
  [ -n "$lib" ] || continue
  if [ "$lib" = "libc++_shared.so" ]; then
    HAS_LIBCXX_NEEDED=1
  fi
  if is_ndk_system_lib "$lib"; then
    continue
  fi
  if [ ! -f "$JNI_DIR/$lib" ]; then
    echo "ERROR: DT_NEEDED library '$lib' of $SO is missing from $JNI_DIR" >&2
    exit 1
  fi
  if [ "$lib" = "libc++_shared.so" ]; then
    size="$(stat -c%s "$JNI_DIR/$lib")"
    if [ "$size" -le 100000 ]; then
      echo "ERROR: $JNI_DIR/$lib is too small ($size bytes) to be a real libc++_shared.so" >&2
      exit 1
    fi
  fi
done <<<"$NEEDED_LIBS"

UNDEF_SYMS="$(nm -D -u "$SO")"
while IFS= read -r line; do
  line="${line#"${line%%[![:space:]]*}"}"
  [ -n "$line" ] || continue
  kind="${line%% *}"
  sym="${line#* }"

  if [ "$HAS_LIBCXX_NEEDED" -eq 0 ]; then
    case "$sym" in
      __gxx_*|_Z*)
        echo "ERROR: $SO references C++ symbol '$sym' without DT_NEEDED libc++_shared.so" >&2
        exit 1
        ;;
      __cxa_finalize@LIBC*|__cxa_atexit@LIBC*)
        ;;
      __cxa_*)
        echo "ERROR: $SO references C++ ABI symbol '$sym' without DT_NEEDED libc++_shared.so" >&2
        exit 1
        ;;
    esac
  fi

  # Weak undefined symbols (w) such as getrandom or _ITM_* are optional at runtime.
  if [ "$kind" != "U" ]; then
    continue
  fi

  case "$sym" in
    *@LIBC|*@LIBC_*|*@LIBM|*@LIBM_*|*@LIBDL|*@LIBDL_*|*@LIBLOG|*@LIBLOG_*)
      continue
      ;;
  esac

  if is_allowed_unversioned_ndk_sym "$sym"; then
    continue
  fi

  if [ "$HAS_LIBCXX_NEEDED" -eq 1 ]; then
    case "$sym" in
      __gxx_*|__cxa_*|_Z*|__emutls_*)
        continue
        ;;
    esac
  fi

  echo "ERROR: $SO has unexpected unresolved dynamic symbol '$sym'" >&2
  exit 1
done <<<"$UNDEF_SYMS"

LOAD_ALIGNS="$(readelf -l "$SO" | awk '/^  LOAD /{getline; print $NF}')"
if [ -z "$LOAD_ALIGNS" ]; then
  echo "ERROR: no PT_LOAD segments found in $SO" >&2
  exit 1
fi
while IFS= read -r align; do
  [ -n "$align" ] || continue
  align_hex="${align#0x}"
  if [ $(( 16#$align_hex )) -lt $(( 0x4000 )) ]; then
    echo "ERROR: $SO PT_LOAD alignment $align is < 0x4000 (16 KB Android requirement)" >&2
    exit 1
  fi
done <<<"$LOAD_ALIGNS"

if [ "$CHECK_MUSICO_JNI" -eq 1 ]; then
  DEFINED_SYMS="$(nm -D --defined-only "$SO")"
  for fn in \
    Java_io_github_aryan007h_musicoonline_core_MusicoCore_initNative \
    Java_io_github_aryan007h_musicoonline_core_MusicoCore_invoke; do
    if ! grep -Eq " [TW] ${fn}$" <<<"$DEFINED_SYMS"; then
      echo "ERROR: $SO is missing exported JNI symbol: $fn" >&2
      exit 1
    fi
  done
fi

echo "OK: $SO verified (NEEDED=$(echo "$NEEDED_LIBS" | tr '\n' ' ' | sed 's/ $//'))"
