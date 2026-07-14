package com.vulnrbot.app.data.linux

sealed interface LinuxEnvironmentState {
    /** Probing root + chroot availability (both shell out to `su`, so this is transient). */
    data object CheckingRoot : LinuxEnvironmentState

    /** `su` isn't available or the app hasn't been granted root by the superuser manager. */
    data object NeedsRoot : LinuxEnvironmentState

    /** Root works, but no usable chroot was found at [path] (no `<path>/bin/bash`). */
    data class ChrootNotFound(val path: String) : LinuxEnvironmentState

    /** Running one-time baseline setup (apt update + core packages) inside the existing chroot. */
    data object Preparing : LinuxEnvironmentState

    data object Ready : LinuxEnvironmentState
    data class Error(val message: String) : LinuxEnvironmentState
}
