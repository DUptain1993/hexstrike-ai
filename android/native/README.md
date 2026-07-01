# Native module: proot

The Linux subsystem (running nmap, sqlmap, etc. on-device) needs a `proot` binary. Earlier
versions of this doc described cross-compiling it from source with the Android NDK; that's no
longer necessary, and this directory no longer contains a CMake project.

## What actually happens now

`android/app/build.gradle.kts` registers a `fetchProotBinaries` Gradle task (a `preBuild`
dependency, so it runs before every build automatically) that invokes
[`scripts/fetch-proot.sh`](../scripts/fetch-proot.sh). That script:

1. Downloads `proot`, `libtalloc`, and `libandroid-shmem` as prebuilt `.deb` packages straight
   from **Termux's official APT repository** (`packages.termux.dev`), for each of the app's three
   target ABIs (arm64-v8a, armeabi-v7a, x86_64).
2. Verifies each download against a pinned SHA-256 hash checked into the script itself.
3. Extracts the actual binaries out of the `.deb` (`ar` + `tar`, no root/sudo needed).
4. Uses `patchelf` to retarget proot's dependency on `libtalloc.so.2` to plain `libtalloc.so` —
   the *file's contents* aren't modified in any functional way, just the ELF `DT_NEEDED`/`SONAME`
   strings, so Android's native-lib packaging (which expects `lib*.so` names) has something to
   work with instead of a versioned filename.
5. Drops everything into `app/build/generated/prootLibs/<abi>/`, which is registered as an
   additional `jniLibs` source directory — so it gets packaged into the APK exactly like any other
   native library, and PackageManager extracts it into the app's `nativeLibraryDir` with the
   execute bit set at install time (the same trick Termux's own app uses to work around Android
   10+'s W^X restrictions on app-writable storage).

Requires `curl`, `ar`, `tar`, `sha256sum`, and `patchelf` on the machine running the Gradle build
(all standard on Linux/macOS dev boxes and exactly what's installed on the GitHub Actions
`ubuntu-latest` runner this repo's CI uses — see `.github/workflows/android-build.yml`). If
`patchelf` is missing, the task fails with an actionable error rather than silently producing a
broken build.

## Why prebuilt binaries instead of compiling from source

Compiling proot with the NDK sounds simpler until you look at what it actually links against:
`libtalloc` and `libandroid-shmem`, neither of which ship in the NDK sysroot — cross-compiling
proot for Android means *also* cross-compiling both of those first, from their own separate build
systems. Termux's own package infrastructure has already solved exactly this problem and publishes
the result as ordinary, reproducible, checksummed `.deb` packages. Re-deriving that pipeline with
an NDK toolchain this project doesn't otherwise need would be strictly worse: slower first builds,
a much larger dependency surface (a C++ toolchain, three separate native build systems), and no
material improvement over binaries already built the same way millions of Termux installs run.

Using upstream GNU proot instead of Termux's fork was never on the table regardless — recent
Android kernels' seccomp/SELinux policy blocks several `ptrace`-based syscalls proot depends on,
and Termux's fork carries the patches that make it usable on-device at all.

## Rootfs

This directory and `scripts/fetch-proot.sh` only produce the `proot` binary. The actual Ubuntu
filesystem is *not* bundled in the APK (it's ~150-400MB, and Play Store / F-Droid both discourage
multi-hundred-MB APKs). `data/linux/RootfsDownloader.kt` fetches Ubuntu's official
`ubuntu-base-*-arm64.tar.gz` from `cdimage.ubuntu.com/ubuntu-base/releases/` on first run and
extracts it into app-private storage, exactly like `proot-distro` and similar tools do. No external
app (Termux, UserLAnd, etc.) is required at runtime or build time — everything needed ships either
in the APK or gets fetched by the app itself on first launch.
