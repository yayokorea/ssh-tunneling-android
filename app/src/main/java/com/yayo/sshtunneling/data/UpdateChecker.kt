package com.yayo.sshtunneling.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val releaseNote: String
)

class UpdateChecker(private val context: Context) {

    companion object {
        private const val GITHUB_API_URL = "https://api.github.com/repos/%s/%s/releases/latest"
        private const val OWNER = "yayoiken"
        private const val REPO = "ssh_tunneling"
    }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(String.format(GITHUB_API_URL, OWNER, REPO))
            val connection = url.openConnection() as HttpURLConnection
            connection.requestProperty = "Accept", "application/vnd.github+json"
            connection.requestProperty = "X-GitHub-Api-Version", "2022-11-28"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.connect()

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                parseReleaseJson(response)
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseReleaseJson(json: String): UpdateInfo? {
        val versionName = extractJsonValue(json, "tag_name")?.removePrefix("v") ?: return null
        val body = extractJsonValue(json, "body") ?: ""
        
        val assets = extractJsonArray(json, "assets")
        val apkAsset = assets?.find { it.contains("\"name\":\"") && it.contains(".apk") }
        val downloadUrl = extractJsonValue(apkAsset ?: "", "browser_download_url") ?: return null

        return UpdateInfo(
            versionName = versionName,
            versionCode = versionName.replace(".", "").toLongOrNull() ?: 0,
            downloadUrl = downloadUrl,
            releaseNote = body
        )
    }

    private fun extractJsonValue(json: String, key: String): String? {
        val regex = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun extractJsonArray(json: String, key: String): List<String>? {
        val regex = "\"$key\"\\s*:\\s*\\[(.*?)\\]".toRegex()
        val match = regex.find(json) ?: return null
        val content = match.groupValues[1]
        return content.split("},{").map { "{$it}" }
    }

    fun getCurrentVersion(): Pair<String, Long> {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        
        val versionName = packageInfo.versionName ?: "1.0"
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
        
        return Pair(versionName, versionCode)
    }

    suspend fun downloadAndInstall(downloadUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 30000
            connection.readTimeout = 30000
            connection.connect()

            if (connection.responseCode == 200) {
                val fileName = "ssh_tunneling_update.apk"
                val apkFile = File(context.cacheDir, fileName)
                
                connection.inputStream.use { input ->
                    FileOutputStream(apkFile).use { output ->
                        input.copyTo(output)
                    }
                }

                installApk(apkFile)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(intent)
    }
}