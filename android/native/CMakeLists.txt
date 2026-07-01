cmake_minimum_required(VERSION 3.22.1)
project(hexstrike_native C)

# ---------------------------------------------------------------------------
# hexstrike_native
#
# A tiny always-built stub library. Its only purpose is to give the Android
# Gradle Plugin's externalNativeBuild a valid CMake project on a fresh
# checkout, so `./gradlew assembleDebug` succeeds even before the proot
# sources below have been fetched.
# ---------------------------------------------------------------------------
add_library(hexstrike_native SHARED stub.c)

# ---------------------------------------------------------------------------
# proot (optional, cross-compiled from source)
#
# HexStrike AI's Linux subsystem (see data/linux/ProotManager.kt) shells out
# to a `proot` binary to chroot into the downloaded Ubuntu rootfs without
# requiring root. Vanilla upstream proot has known issues on stock Android
# kernels (seccomp/SELinux restrict some ptrace operations), so this project
# builds against Termux's Android-patched proot fork instead of the upstream
# GNU proot tree.
#
# This repository does not vendor that source (it pulls in talloc and a
# large autotools build you should review yourself before compiling into
# something you ship). To enable it:
#
#   git clone https://github.com/termux/proot android/native/proot
#   cd android/native/proot && git checkout v5.4.0   # pick a tagged release
#
# Once native/proot/Makefile exists, re-running a Gradle build will cross
# compile it for every ABI in defaultConfig.ndk.abiFilters and drop the
# result at app/src/main/jniLibs/<abi>/libproot.so — named libproot.so (not
# `proot`) so the OS extracts it from the APK with the execute bit set, the
# same trick Termux itself uses to stay compliant with Android 10+'s W^X
# restrictions on app-writable directories.
#
# See native/README.md for the full explanation and troubleshooting.
# ---------------------------------------------------------------------------
set(PROOT_SRC_DIR "${CMAKE_CURRENT_SOURCE_DIR}/proot")
set(JNILIBS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../app/src/main/jniLibs/${ANDROID_ABI}")

if(EXISTS "${PROOT_SRC_DIR}/Makefile" OR EXISTS "${PROOT_SRC_DIR}/GNUmakefile")
    include(ExternalProject)

    file(MAKE_DIRECTORY "${JNILIBS_DIR}")

    ExternalProject_Add(
        proot_build
        SOURCE_DIR "${PROOT_SRC_DIR}"
        CONFIGURE_COMMAND ""
        BUILD_IN_SOURCE 1
        BUILD_COMMAND ${CMAKE_COMMAND} -E env
            CC=${CMAKE_C_COMPILER}
            AR=${CMAKE_AR}
            RANLIB=${CMAKE_RANLIB}
            CFLAGS=-fPIE\ -DANDROID
            LDFLAGS=-pie
            make -C src loader.elf loader-m32.elf proot V=1
        INSTALL_COMMAND ${CMAKE_COMMAND} -E copy
            "${PROOT_SRC_DIR}/src/proot"
            "${JNILIBS_DIR}/libproot.so"
        BUILD_BYPRODUCTS "${JNILIBS_DIR}/libproot.so"
    )

    add_custom_target(proot_for_${ANDROID_ABI} ALL DEPENDS proot_build)
else()
    message(STATUS "hexstrike_native: native/proot not found, skipping proot cross-build. "
                    "Chat features still work; see native/README.md to enable the Linux subsystem.")
endif()
