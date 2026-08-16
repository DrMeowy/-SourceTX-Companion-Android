package com.sourcetx.companion.updater

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AppReleaseInfo(
    val tagName: String,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val apkDownloadUrl: String,
    val apkFileName: String,
    val apkSize: Long,
    val apkSha256: String,
    val isNewer: Boolean
)

object AppUpdateManager {
    private const val REPOSITORY = "DrMeowy/-SourceTX-Companion-Android"
    private const val GITHUB_API_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"
    private const val MAX_API_BYTES = 1024 * 1024
    private const val MAX_APK_BYTES = 100L * 1024L * 1024L
    private val VERSION_PATTERN = Regex("^[0-9]+\\.[0-9]+(?:\\.[0-9]+)?$")
    private val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")
    private val ALLOWED_HOSTS = setOf(
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "github-releases.githubusercontent.com"
    )

    suspend fun checkForUpdates(currentVersionName: String): Result<AppReleaseInfo?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val release = JSONObject(downloadSmallText(GITHUB_API_URL, MAX_API_BYTES))
                if (release.optBoolean("draft", true) || release.optBoolean("prerelease", true)) return@runCatching null
                val tag = release.getString("tag_name")
                val version = tag.removePrefix("v").removePrefix("V")
                require(VERSION_PATTERN.matches(version)) { "Latest app release has an invalid version." }
                val assets = release.getJSONArray("assets")
                val expectedName = "SourceTX-Companion-v$version.apk"
                var apk: JSONObject? = null
                var digestAsset: JSONObject? = null
                for (index in 0 until assets.length()) {
                    val asset = assets.getJSONObject(index)
                    when (asset.optString("name")) {
                        expectedName -> apk = asset
                        "$expectedName.sha256" -> digestAsset = asset
                    }
                }
                val apkAsset = apk ?: throw IOException("The latest release does not contain the expected signed APK.")
                val apkSize = apkAsset.getLong("size")
                require(apkSize in 1..MAX_APK_BYTES) { "The release APK size is invalid." }
                val expectedPrefix = "https://github.com/$REPOSITORY/releases/download/v$version/"
                val apkUrl = apkAsset.getString("browser_download_url")
                require(apkUrl == "$expectedPrefix$expectedName") { "The APK is outside the trusted release location." }

                val apiDigest = apkAsset.optString("digest", "")
                    .removePrefix("sha256:")
                    .takeIf(SHA256_PATTERN::matches)
                val digest = apiDigest ?: run {
                    val digestInfo = digestAsset
                        ?: throw IOException("The release is missing $expectedName.sha256.")
                    val digestUrl = digestInfo.getString("browser_download_url")
                    require(digestUrl == "$expectedPrefix$expectedName.sha256") {
                        "The APK checksum is outside the trusted release location."
                    }
                    downloadSmallText(digestUrl, 4096)
                        .trim()
                        .split(Regex("\\s+"))
                        .firstOrNull()
                        ?.takeIf(SHA256_PATTERN::matches)
                        ?: throw IOException("The release APK checksum file is invalid.")
                }
                AppReleaseInfo(
                    tagName = tag,
                    versionName = version,
                    releaseTitle = release.optString("name", tag),
                    releaseNotes = release.optString("body", ""),
                    apkDownloadUrl = apkUrl,
                    apkFileName = expectedName,
                    apkSize = apkSize,
                    apkSha256 = digest.lowercase(),
                    isNewer = compareVersions(version, currentVersionName) > 0
                )
            }
        }

    suspend fun downloadAndInstallApk(
        context: Context,
        release: AppReleaseInfo,
        onProgress: (percent: Int, bytesRead: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(release.isNewer && VERSION_PATTERN.matches(release.versionName) &&
                SHA256_PATTERN.matches(release.apkSha256) && release.apkSize in 1..MAX_APK_BYTES) {
                "The selected app release is not a valid update."
            }
            val expectedUrl = "https://github.com/$REPOSITORY/releases/download/v${release.versionName}/${release.apkFileName}"
            require(release.apkDownloadUrl == expectedUrl) { "The app update URL is not trusted." }

            val directory = File(context.cacheDir, "updates").apply { mkdirs() }
            directory.listFiles()?.filter { it.extension.equals("apk", true) }?.forEach { it.delete() }
            val destination = File(directory, release.apkFileName)
            downloadFile(release.apkDownloadUrl, destination, release.apkSize, onProgress)
            val actualDigest = sha256Hex(destination)
            require(MessageDigest.isEqual(
                release.apkSha256.toByteArray(Charsets.US_ASCII),
                actualDigest.toByteArray(Charsets.US_ASCII)
            )) { "The downloaded APK failed SHA-256 verification." }
            verifyApkIdentityAndSigner(context, destination, release.versionName)

            if (!context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                throw IOException("Allow SourceTX Companion to install updates, then tap Download & Install again.")
            }
            launchInstaller(context, destination)
            destination
        }
    }

    private fun verifyApkIdentityAndSigner(context: Context, apk: File, expectedVersion: String) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
        val archive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apk.path, flags)
        } ?: throw IOException("Android could not inspect the downloaded APK.")
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, flags)
        }
        require(archive.packageName == context.packageName && archive.versionName == expectedVersion) {
            "The downloaded APK has the wrong application identity or version."
        }
        val archiveSigners = signingCertificates(archive)
        val installedSigners = signingCertificates(installed)
        require(archiveSigners.isNotEmpty() && archiveSigners == installedSigners) {
            "The downloaded APK is not signed by the same key as this Companion installation."
        }
    }

    private fun signingCertificates(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners
            else signingInfo.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return signatures.orEmpty().map { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun downloadSmallText(url: String, maximumBytes: Int): String {
        val bytes = downloadBytes(url, maximumBytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun downloadBytes(url: String, maximumBytes: Int): ByteArray {
        var current = URL(url)
        repeat(6) { redirects ->
            validateUrl(current)
            val connection = open(current)
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    if (redirects == 5) throw IOException("App update exceeded the redirect limit.")
                    current = URL(current, connection.getHeaderField("Location")
                        ?: throw IOException("App update redirect was incomplete."))
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) throw IOException("App update server returned HTTP $code.")
                val output = ByteArrayOutputStream()
                connection.inputStream.use { input ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maximumBytes) throw IOException("App update response exceeded its safety limit.")
                        output.write(buffer, 0, read)
                    }
                }
                return output.toByteArray()
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("App update download could not be completed.")
    }

    private fun downloadFile(
        url: String,
        destination: File,
        expectedBytes: Long,
        onProgress: (Int, Long, Long) -> Unit
    ) {
        var current = URL(url)
        repeat(6) { redirects ->
            validateUrl(current)
            val connection = open(current)
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    if (redirects == 5) throw IOException("APK download exceeded the redirect limit.")
                    current = URL(current, connection.getHeaderField("Location")
                        ?: throw IOException("APK download redirect was incomplete."))
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) throw IOException("APK download returned HTTP $code.")
                if (connection.contentLengthLong > 0 && connection.contentLengthLong != expectedBytes) {
                    throw IOException("APK size does not match the GitHub release metadata.")
                }
                var total = 0L
                connection.inputStream.use { input ->
                    FileOutputStream(destination).use { output ->
                        val buffer = ByteArray(16 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > expectedBytes || total > MAX_APK_BYTES) {
                                throw IOException("APK download exceeded the expected size.")
                            }
                            output.write(buffer, 0, read)
                            onProgress(((total * 100L) / expectedBytes).toInt(), total, expectedBytes)
                        }
                    }
                }
                if (total != expectedBytes) throw IOException("APK download ended before all bytes were received.")
                onProgress(100, total, expectedBytes)
                return
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("APK download could not be completed.")
    }

    private fun open(url: URL): HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        instanceFollowRedirects = false
        connectTimeout = 10_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "SourceTX-Companion-Android")
        setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream")
    }

    private fun validateUrl(url: URL) {
        require(url.protocol.equals("https", true) && url.host.lowercase() in ALLOWED_HOSTS) {
            "App update redirected outside trusted GitHub hosts."
        }
    }

    private fun sha256Hex(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun launchInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
        })
    }

    private fun compareVersions(left: String, right: String): Int {
        require(VERSION_PATTERN.matches(left) && VERSION_PATTERN.matches(right)) { "App version is invalid." }
        val leftParts = left.split('.').map(String::toInt)
        val rightParts = right.split('.').map(String::toInt)
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val comparison = leftParts.getOrElse(index) { 0 }.compareTo(rightParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison
        }
        return 0
    }
}
