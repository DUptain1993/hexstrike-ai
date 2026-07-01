# Native module: proot

This CMake project is wired into `app/build.gradle.kts` via
`externalNativeBuild`. On a fresh checkout it only builds a no-op stub
library so the app compiles and the Venice AI chat features work
immediately. The Linux subsystem (running nmap, sqlmap, etc. on-device) needs
an actual `proot` binary, which this repo does not vendor.

## Why not just commit a proot binary?

Two reasons:

1. **Supply chain hygiene.** A prebuilt ELF binary checked into git can't be
   reviewed the way source can. You should compile it yourself (or from a
   build you trust and can reproduce) rather than trust a binary blob from
   a chat assistant.
2. **Vanilla proot doesn't work well on Android.** Recent Android kernels
   restrict several `ptrace`-based syscalls proot depends on via seccomp and
   SELinux. [Termux's proot fork](https://github.com/termux/proot) carries
   the patches that make it usable on-device; upstream GNU proot from your
   distro's package manager will fail in subtle ways.

## One-time setup

```bash
cd android/native
git clone https://github.com/termux/proot proot
cd proot
git checkout v5.4.0   # or whatever the latest tagged release is
git submodule update --init --recursive   # pulls in bundled talloc
```

Then just build the app normally from Android Studio (or
`./gradlew assembleDebug`). `native/CMakeLists.txt` detects `native/proot`
and cross-compiles it for every ABI listed in
`app/build.gradle.kts`'s `defaultConfig.ndk.abiFilters`
(arm64-v8a, armeabi-v7a, x86_64), dropping the result at
`app/src/main/jniLibs/<abi>/libproot.so`.

It's named `libproot.so`, not `proot`, deliberately: Android 10+ enforces
W^X on files under app-writable directories, so a plain executable extracted
into internal storage at runtime can't be marked executable. Files under
`jniLibs/`, however, are extracted by PackageManager into the app's
`nativeLibraryDir` with the execute bit already set — the same workaround
Termux itself uses. `ProotManager.kt` looks up
`applicationInfo.nativeLibraryDir + "/libproot.so"` and execs that path
directly; it is never treated as a JNI library loaded with `System.loadLibrary`.

## If you'd rather not compile native code at all

Leave `native/proot` absent. The app still builds and runs — Settings shows
the Linux environment as unavailable, onboarding lets you pick "chat-only
mode", and the Venice AI assistant works normally, it just can't execute
security tools locally. You can flip this on later without any other code
changes.

## Rootfs

`native/` only produces the proot binary. The actual Ubuntu filesystem is
*not* bundled in the APK (it's ~150-400MB, and Play Store / F-Droid both
discourage multi-hundred-MB APKs). `data/linux/RootfsDownloader.kt` fetches
Ubuntu's official `ubuntu-base-*-arm64.tar.gz` from
`cdimage.ubuntu.com/ubuntu-base/releases/` on first run and extracts it into
app-private storage, exactly like `proot-distro` and similar tools do. No
external app (Termux, UserLAnd, etc.) is required at runtime — this is what
makes the result "standalone."
