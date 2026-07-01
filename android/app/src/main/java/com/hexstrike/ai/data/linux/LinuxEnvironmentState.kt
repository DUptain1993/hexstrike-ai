package com.hexstrike.ai.data.linux

sealed interface LinuxEnvironmentState {
    data object Unavailable : LinuxEnvironmentState // proot binary missing (native/proot not built)
    data object NotInstalled : LinuxEnvironmentState
    data class Downloading(val bytesRead: Long, val totalBytes: Long) : LinuxEnvironmentState
    data object Extracting : LinuxEnvironmentState
    data object Configuring : LinuxEnvironmentState
    data object Ready : LinuxEnvironmentState
    data class Error(val message: String) : LinuxEnvironmentState
}
