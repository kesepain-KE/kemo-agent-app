package com.kesepain.kemoapp.update

sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState
    data object Checking : AppUpdateUiState
    data class Available(val release: GitHubRelease) : AppUpdateUiState
    data class UpToDate(val release: GitHubRelease) : AppUpdateUiState
    data class ReleaseWithoutApk(val release: GitHubRelease) : AppUpdateUiState
    data object NoPublishedRelease : AppUpdateUiState
    data object Failed : AppUpdateUiState
    data class Downloading(val release: GitHubRelease, val progress: Int) : AppUpdateUiState
    data class Downloaded(val release: GitHubRelease, val filePath: String) : AppUpdateUiState
    data class DownloadFailed(val release: GitHubRelease) : AppUpdateUiState
}

data class AppAboutUiState(
    val avatarBytes: ByteArray? = null,
    val avatarLoading: Boolean = false,
    val update: AppUpdateUiState = AppUpdateUiState.Idle,
)
