package com.sourcetx.companion.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSize: Long,
    val isNewer: Boolean
)

object AppUpdateManager {

    private const val GITHUB_API_URL =
        "https://api.github.com/repos/DrMeowy/-SourceTX-Companion-Android/releases"

    suspend fun checkForUpdates(currentVersionName: String): Result<AppReleaseInfo?> =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(GITHUB_API_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "SourceTX-Companion-Android")
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 8000
                conn.readTimeout = 12000

                if (conn.responseCode != 200) {
                    return@withContext Result.failure(Exception("GitHub API returned status ${conn.responseCode}"))
                }

                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val releases = JSONArray(responseText)

                if (releases.length() == 0) {
                    return@withContext Result.success(null)
                }

                val latestRelease = releases.getJSONObject(0)
                val tagName = latestRelease.optString("tag_name", "")
                val releaseTitle = latestRelease.optString("name", tagName)
                val releaseNotes = latestRelease.optString("body", "")
                val assets = latestRelease.optJSONArray("assets") ?: JSONArray()

                var apkDownloadUrl = ""
                var apkFileName = "SourceTX-Companion.apk"
                var apkSize = 0L

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = asset.optString("browser_download_url", "")
                        apkFileName = name
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }

                if (apkDownloadUrl.isEmpty()) {
                    return@withContext Result.success(null)
                }

                val cleanTag = tagName.removePrefix("v").removePrefix("V")
                val isNewer = isVersionNewer(cleanTag, currentVersionName)

                val info = AppReleaseInfo(
                    tagName = tagName,
                    versionName = cleanTag,
                    releaseTitle = releaseTitle,
                    releaseNotes = releaseNotes,
                    apkDownloadUrl = apkDownloadUrl,
                    apkFileName = apkFileName,
                    apkSize = apkSize,
                    isNewer = isNewer
                )

                Result.success(info)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        fileName: String,
        onProgress: (percent: Int, bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val url = URL(downloadUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "SourceTX-Companion-Android")
            conn.instanceFollowRedirects = true
            conn.connectTimeout = 10000
            conn.readTimeout = 30000

            // Handle standard HTTP 301/302 redirects
            var redirectUrl = downloadUrl
            var code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM || code == HttpURLConnection.HTTP_MOVED_TEMP || code == 307 || code == 308) {
                redirectUrl = conn.getHeaderField("Location")
            }

            val finalConn = URL(redirectUrl).openConnection() as HttpURLConnection
            finalConn.requestMethod = "GET"
            finalConn.setRequestProperty("User-Agent", "SourceTX-Companion-Android")
            finalConn.connectTimeout = 10000
            finalConn.readTimeout = 30000

            val totalBytes = finalConn.contentLength.toLong()
            val destDir = File(context.cacheDir, "updates")
            if (!destDir.exists()) destDir.mkdirs()

            val destFile = File(destDir, fileName)
            if (destFile.exists()) destFile.delete()

            finalConn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val pct = if (totalBytes > 0) ((totalRead * 100) / totalBytes).toInt() else 0
                        onProgress(pct, totalRead, totalBytes)
                    }
                }
            }

            // Launch Native Android Package Installer
            launchInstaller(context, destFile)

            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun launchInstaller(context: Context, apkFile: File) {
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }

        context.startActivity(intent)
    }

    private fun isVersionNewer(remote: String, local: String): Boolean {
        if (remote == local) return false
        val rParts = remote.split("-", ".").mapNotNull { it.toIntOrNull() }
        val lParts = local.split("-", ".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return remote.contains("preview", ignoreCase = true) || remote != local
    }
}
