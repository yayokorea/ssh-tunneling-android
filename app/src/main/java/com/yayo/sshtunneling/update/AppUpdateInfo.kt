package com.yayo.sshtunneling.update

data class AppUpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val tag: String,
    val apkUrl: String,
    val sha256: String,
    val releaseNotesUrl: String?,
    val notes: String?,
    val releasedAt: String?,
)

data class AppUpdateState(
    val availableUpdate: AppUpdateInfo? = null,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgressPercent: Int? = null,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
)
