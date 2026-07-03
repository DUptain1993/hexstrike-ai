#!/usr/bin/env bash
# Fetches proot + its two runtime deps (libtalloc, libandroid-shmem) as prebuilt binaries from
# Termux's official APT repository and stages them so Gradle can package them as ordinary Android
# native libraries. See android/native/README.md for why this replaces compiling proot from
# source with the Android NDK.
#
# Usage: fetch-proot.sh <output-dir> [cache-dir]
set -euo pipefail

OUT_DIR="${1:?usage: fetch-proot.sh <output-dir> [cache-dir]}"
CACHE_DIR="${2:-${TMPDIR:-/tmp}/vulnrbot-proot-cache}"
BASE_URL="https://packages.termux.dev/apt/termux-main/pool/main"

PROOT_VERSION="5.1.107.81"
TALLOC_VERSION="2.4.3"
SHMEM_VERSION="0.7"

declare -A PROOT_SHA=(
  [aarch64]=6a7847d6cd9783711de6fa86512433180cd7916174dc5657151d41ef4551b241
  [arm]=f7a4f617bfa7afa90b8f3941bb1f1e378ed8c4df9150d133caee608d9dfb0422
  [x86_64]=e8c2843ecee01375fd8a33d90f3660553c5a5b41a3f9b9063cc06c90620b3ca8
)
declare -A TALLOC_SHA=(
  [aarch64]=ac81ad623d74c209718b9f3acb2dd702cc8a88c431e820d212229910b4db29da
  [arm]=cd56f87007e487c8025fac2df2a27b2bc58102344040a527eaa6fa7527d18f9b
  [x86_64]=7ca2eaae2e53b28228a01301bc410b62845403d6317c25b8e0a7f40681de0628
)
declare -A SHMEM_SHA=(
  [aarch64]=0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6
  [arm]=5832fd11dca9be2a288dd8fbc2b2799b289c812c7a8764f1f8234c425aa64ce5
  [x86_64]=ffa9e4c87467b158b148d0ff92dda796aa038276c2075af3269cdcdb06f25797
)
declare -A ANDROID_ABI=( [aarch64]=arm64-v8a [arm]=armeabi-v7a [x86_64]=x86_64 )

for bin in curl ar tar sha256sum patchelf; do
  if ! command -v "$bin" >/dev/null 2>&1; then
    echo "fetch-proot.sh: required tool '$bin' not found on PATH." >&2
    echo "On Debian/Ubuntu: sudo apt install curl binutils tar coreutils patchelf" >&2
    echo "On macOS: brew install curl gnu-tar coreutils patchelf" >&2
    exit 1
  fi
done

mkdir -p "$CACHE_DIR" "$OUT_DIR"

download_verify() {
  local url="$1" dest="$2" expected_sha256="$3"
  if [ -f "$dest" ] && echo "$expected_sha256  $dest" | sha256sum -c - >/dev/null 2>&1; then
    return 0
  fi
  echo "fetch-proot.sh: downloading $(basename "$dest")"
  curl -fsSL "$url" -o "$dest"
  if ! echo "$expected_sha256  $dest" | sha256sum -c -; then
    echo "fetch-proot.sh: checksum mismatch for $dest — refusing to use it." >&2
    rm -f "$dest"
    exit 1
  fi
}

extract_deb() {
  local deb="$1" dest_dir="$2"
  rm -rf "$dest_dir"
  mkdir -p "$dest_dir"
  ar x "$deb" --output="$dest_dir"
  mkdir -p "$dest_dir/data"
  local data_archive
  data_archive=$(find "$dest_dir" -maxdepth 1 -name 'data.tar.*' | head -1)
  tar -xf "$data_archive" -C "$dest_dir/data"
}

for arch in aarch64 arm x86_64; do
  abi="${ANDROID_ABI[$arch]}"
  work="$CACHE_DIR/build-$arch"
  mkdir -p "$OUT_DIR/$abi"

  download_verify "$BASE_URL/p/proot/proot_${PROOT_VERSION}_${arch}.deb" "$CACHE_DIR/proot_$arch.deb" "${PROOT_SHA[$arch]}"
  download_verify "$BASE_URL/libt/libtalloc/libtalloc_${TALLOC_VERSION}_${arch}.deb" "$CACHE_DIR/libtalloc_$arch.deb" "${TALLOC_SHA[$arch]}"
  download_verify "$BASE_URL/liba/libandroid-shmem/libandroid-shmem_${SHMEM_VERSION}_${arch}.deb" "$CACHE_DIR/libandroid-shmem_$arch.deb" "${SHMEM_SHA[$arch]}"

  extract_deb "$CACHE_DIR/proot_$arch.deb" "$work/proot"
  extract_deb "$CACHE_DIR/libtalloc_$arch.deb" "$work/libtalloc"
  extract_deb "$CACHE_DIR/libandroid-shmem_$arch.deb" "$work/libandroid-shmem"

  usr="$work/proot/data/data/data/com.termux/files/usr"
  talloc_usr="$work/libtalloc/data/data/data/com.termux/files/usr"
  shmem_usr="$work/libandroid-shmem/data/data/data/com.termux/files/usr"

  # Named lib*.so (not the upstream "proot"/"loader" names) so Android's PackageManager treats
  # them as native libraries: it extracts anything under lib/<abi>/ in the APK into the app's
  # nativeLibraryDir with the execute bit set, which is otherwise unavailable to app-writable
  # storage under Android 10+'s W^X policy. See native/README.md.
  cp "$usr/bin/proot" "$OUT_DIR/$abi/libproot.so"
  cp "$usr/libexec/proot/loader" "$OUT_DIR/$abi/libproot_loader.so"
  if [ -f "$usr/libexec/proot/loader32" ]; then
    cp "$usr/libexec/proot/loader32" "$OUT_DIR/$abi/libproot_loader32.so"
  fi

  talloc_lib=$(find "$talloc_usr/lib" -maxdepth 1 -name 'libtalloc.so.*' | head -1)
  cp "$talloc_lib" "$OUT_DIR/$abi/libtalloc.so"
  cp "$shmem_usr/lib/libandroid-shmem.so" "$OUT_DIR/$abi/libandroid-shmem.so"

  chmod u+w "$OUT_DIR/$abi/libproot.so" "$OUT_DIR/$abi/libtalloc.so"

  # proot links against the talloc SONAME "libtalloc.so.2"; renaming the file wouldn't be enough
  # since the dynamic linker resolves DT_NEEDED by exact name, not by our chosen filename — so
  # patch both the SONAME record and proot's reference to it to plain "libtalloc.so" instead of
  # trying to preserve a versioned filename Android's native-lib packaging isn't built around.
  patchelf --set-soname libtalloc.so "$OUT_DIR/$abi/libtalloc.so"
  patchelf --replace-needed libtalloc.so.2 libtalloc.so "$OUT_DIR/$abi/libproot.so"

  echo "fetch-proot.sh: staged proot ${PROOT_VERSION} for $abi"
done
