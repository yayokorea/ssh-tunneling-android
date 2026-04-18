package com.yayo.sshtunneling.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import com.yayo.sshtunneling.R
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GitHubReleaseUpdater(private val context: Context) {
    suspend fun fetchAvailableUpdate(): AppUpdateInfo? = withContext(Dispatchers.IO) {
        val manifestJson = request(context.getString(R.string.github_latest_manifest_url))
        val updateInfo = parseManifest(manifestJson)
        val currentVersionCode = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0)
        )

        updateInfo.takeIf { it.versionCode > currentVersionCode }
    }

    suspend fun downloadAndLaunchInstaller(updateInfo: AppUpdateInfo) = withContext(Dispatchers.IO) {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        updatesDir.listFiles().orEmpty().forEach { existing ->
            if (existing.name != "update-${updateInfo.versionCode}.apk") {
                existing.delete()
            }
        }

        val apkFile = File(updatesDir, "update-${updateInfo.versionCode}.apk")
        val digest = MessageDigest.getInstance("SHA-256")

        request(updateInfo.apkUrl) { connection ->
            apkFile.outputStream().buffered().use { output ->
                DigestInputStream(connection.inputStream.buffered(), digest).use { input ->
                    input.copyTo(output)
                }
            }
        }

        val actualSha256 = digest.digest().toHexString()
        if (!actualSha256.equals(updateInfo.sha256, ignoreCase = true)) {
            apkFile.delete()
            error(context.getString(R.string.update_error_hash_mismatch))
        }

        launchInstaller(apkFile)
    }

    private fun launchInstaller(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(installIntent)
    }

    private fun parseManifest(rawJson: String): AppUpdateInfo {
        val root = JSONObject(rawJson)
        val asset = root.optJSONObject("asset")
        val versionName = root.optString("versionName").ifBlank { root.optString("version") }
        val versionCode = root.optLong("versionCode")
        val apkUrl = root.optString("apkUrl").ifBlank { asset?.optString("url").orEmpty() }
        val sha256 = root.optString("sha256").ifBlank { asset?.optString("sha256").orEmpty() }

        require(versionName.isNotBlank()) { "Missing versionName in manifest.json" }
        require(versionCode > 0) { "Missing versionCode in manifest.json" }
        require(apkUrl.isNotBlank()) { "Missing apkUrl in manifest.json" }
        require(sha256.isNotBlank()) { "Missing sha256 in manifest.json" }

        return AppUpdateInfo(
            versionName = versionName,
            versionCode = versionCode,
            tag = root.optString("tag"),
            apkUrl = apkUrl,
            sha256 = sha256,
            releaseNotesUrl = root.optString("releaseNotesUrl").takeIf { it.isNotBlank() },
            notes = root.optString("notes").takeIf { it.isNotBlank() },
            releasedAt = root.optString("releasedAt").takeIf { it.isNotBlank() },
        )
    }

    private fun request(url: String): String {
        return request(url) { connection ->
            connection.inputStream.bufferedReader().use { it.readText() }
        }
    }

    private fun <T> request(url: String, block: (HttpURLConnection) -> T): T {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: "unknown"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "SSH Tunneling Android/$versionName")
        }

        return try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                error("HTTP $responseCode for $url")
            }
            block(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
