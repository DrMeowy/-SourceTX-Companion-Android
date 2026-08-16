package com.sourcetx.companion.firmware

import com.sourcetx.companion.protocol.BoardTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class FirmwareRepository(
    private val companionVersion: String
) {
    companion object {
        const val STABLE_UPDATE_MANIFEST_URL =
            "https://github.com/DrMeowy/SourceTX-Updates/releases/latest/download/stable.json"
        const val STABLE_UPDATE_MANIFEST_SIGNATURE_URL = "$STABLE_UPDATE_MANIFEST_URL.sig"

        private const val PRODUCT = "SourceTX"
        private const val HARDWARE_ID = "sourcetx-s3-st7796-ft6x36"
        private const val RELEASE_PREFIX =
            "https://github.com/DrMeowy/SourceTX-Updates/releases/download/"
        private const val MAX_MANIFEST_BYTES = 64 * 1024
        private const val MAX_SIGNATURE_BYTES = 256
        private const val MAX_FIRMWARE_BYTES = 4 * 1024 * 1024
        private val VERSION_PATTERN = Regex("^[0-9]+\\.[0-9]+(?:\\.[0-9]+)?$")
        private val SHA256_PATTERN = Regex("^[0-9a-fA-F]{64}$")
        private val ALLOWED_DOWNLOAD_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
            "github-releases.githubusercontent.com"
        )
    }

    suspend fun acquireFactory(
        board: BoardTarget,
        onDownloadProgress: (Int) -> Unit = {}
    ): Result<VerifiedFirmwarePackage> = withContext(Dispatchers.IO) {
        runCatching {
            requireTrustedBoard(board)
            val manifestUrl = board.factoryManifestUrl
                ?: throw IOException("Factory manifest is not configured for this board.")
            val signatureUrl = board.factoryManifestSignatureUrl
                ?: throw IOException("Factory manifest signature is not configured for this board.")
            acquire(
                type = FirmwarePackageType.FACTORY,
                manifestUrl = manifestUrl,
                manifestSignatureUrl = signatureUrl,
                board = board,
                onDownloadProgress = onDownloadProgress
            )
        }
    }

    suspend fun acquireApplication(
        board: BoardTarget,
        onDownloadProgress: (Int) -> Unit = {}
    ): Result<VerifiedFirmwarePackage> = withContext(Dispatchers.IO) {
        runCatching {
            requireTrustedBoard(board)
            acquire(
                type = FirmwarePackageType.APPLICATION,
                manifestUrl = STABLE_UPDATE_MANIFEST_URL,
                manifestSignatureUrl = STABLE_UPDATE_MANIFEST_SIGNATURE_URL,
                board = board,
                onDownloadProgress = onDownloadProgress
            )
        }
    }

    private fun acquire(
        type: FirmwarePackageType,
        manifestUrl: String,
        manifestSignatureUrl: String,
        board: BoardTarget,
        onDownloadProgress: (Int) -> Unit
    ): VerifiedFirmwarePackage {
        val manifestBytes = downloadBytes(manifestUrl, MAX_MANIFEST_BYTES)
        val manifestSignature = downloadBytes(manifestSignatureUrl, MAX_SIGNATURE_BYTES)
        require(FirmwareSecurity.verifyEcdsaSha256(manifestBytes, manifestSignature)) {
            "The release manifest signature could not be verified."
        }

        val manifest = parseManifest(type, manifestBytes)
        validateManifest(type, manifest, board)
        val image = downloadBytes(
            manifest.imageUrl,
            MAX_FIRMWARE_BYTES,
            manifest.size,
            onDownloadProgress
        )
        require(image.size.toLong() == manifest.size) {
            "Firmware size mismatch: expected ${manifest.size}, received ${image.size} bytes."
        }
        val digest = FirmwareSecurity.sha256Hex(image)
        require(FirmwareSecurity.fixedTimeEqualsHex(manifest.sha256, digest)) {
            "Firmware SHA-256 verification failed."
        }
        val imageSignature = downloadBytes(manifest.signatureUrl, MAX_SIGNATURE_BYTES)
        require(FirmwareSecurity.verifyEcdsaSha256(image, imageSignature)) {
            "Firmware signature could not be verified."
        }
        FirmwareImageValidator.validate(type, image).getOrThrow()
        onDownloadProgress(100)
        return VerifiedFirmwarePackage(manifest, image)
    }

    private fun parseManifest(
        type: FirmwarePackageType,
        bytes: ByteArray
    ): FirmwareReleaseManifest {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        fun requiredString(name: String): String {
            if (!root.has(name) || root.isNull(name)) throw IOException("Release manifest is missing '$name'.")
            return root.getString(name).trim().also {
                if (it.isEmpty()) throw IOException("Release manifest field '$name' is empty.")
            }
        }
        fun requiredInt(name: String): Int {
            if (!root.has(name)) throw IOException("Release manifest is missing '$name'.")
            return root.getInt(name)
        }
        fun requiredLong(name: String): Long {
            if (!root.has(name)) throw IOException("Release manifest is missing '$name'.")
            return root.getLong(name)
        }

        val imageField = if (type == FirmwarePackageType.FACTORY) "factory_url" else "firmware_url"
        return FirmwareReleaseManifest(
            schema = requiredInt("schema"),
            product = requiredString("product"),
            hardware = requiredString("hardware"),
            channel = requiredString("channel"),
            version = requiredString("version"),
            size = requiredLong("size"),
            sha256 = requiredString("sha256"),
            imageUrl = requiredString(imageField),
            signatureUrl = requiredString("signature_url"),
            releaseUrl = requiredString("release_url"),
            chip = if (type == FirmwarePackageType.FACTORY) requiredString("chip") else null,
            flashSize = if (type == FirmwarePackageType.FACTORY) requiredString("flash_size") else null,
            flashMode = if (type == FirmwarePackageType.FACTORY) requiredString("flash_mode") else null,
            flashFrequency = if (type == FirmwarePackageType.FACTORY) requiredString("flash_frequency") else null,
            flashOffset = if (type == FirmwarePackageType.FACTORY) requiredString("flash_offset") else null,
            minimumCompanionVersion = if (type == FirmwarePackageType.FACTORY) {
                requiredString("minimum_companion_version")
            } else null
        )
    }

    private fun validateManifest(
        type: FirmwarePackageType,
        manifest: FirmwareReleaseManifest,
        board: BoardTarget
    ) {
        val expectedSchema = if (type == FirmwarePackageType.FACTORY) 1 else 2
        require(manifest.schema == expectedSchema && manifest.product == PRODUCT &&
            manifest.hardware == HARDWARE_ID && manifest.channel == "stable") {
            "Release manifest identity, channel, or schema is unsupported."
        }
        require(VERSION_PATTERN.matches(manifest.version) && SHA256_PATTERN.matches(manifest.sha256)) {
            "Release manifest version or digest is invalid."
        }
        require(manifest.size in (64L * 1024L)..MAX_FIRMWARE_BYTES.toLong()) {
            "Release manifest firmware size is invalid."
        }

        if (type == FirmwarePackageType.FACTORY) {
            require(manifest.chip.equals(board.chip, true) &&
                manifest.flashSize.equals(board.flashSize, true) &&
                manifest.flashMode.equals(board.flashMode, true) &&
                manifest.flashFrequency.equals(board.flashFreq, true) &&
                manifest.flashOffset == "0x0000") {
                "Factory release does not match the selected hardware and flash layout."
            }
            require(compareVersions(companionVersion, manifest.minimumCompanionVersion.orEmpty()) >= 0) {
                "This release requires Companion ${manifest.minimumCompanionVersion} or newer."
            }
        }

        val releaseBase = "${RELEASE_PREFIX}v${manifest.version}/"
        require(manifest.imageUrl.startsWith(releaseBase) &&
            manifest.signatureUrl == manifest.imageUrl + ".sig" &&
            manifest.releaseUrl == "https://github.com/DrMeowy/SourceTX-Updates/releases/tag/v${manifest.version}") {
            "Release assets are outside the trusted SourceTX feed."
        }
        validateInitialUrl(manifest.imageUrl)
        validateInitialUrl(manifest.signatureUrl)
    }

    private fun requireTrustedBoard(board: BoardTarget) {
        require(board.enabled && board.id == "esp32s3-4mb" && board.hardwareId == HARDWARE_ID &&
            board.chip.equals("esp32s3", true) && board.flashSize.equals("4MB", true) &&
            board.flashMode.equals("dio", true) && board.flashFreq.equals("80m", true)) {
            "Only the official SourceTX ESP32-S3 4MB reference board is supported."
        }
    }

    private fun downloadBytes(
        urlText: String,
        maximumBytes: Int,
        expectedBytes: Long? = null,
        onProgress: (Int) -> Unit = {}
    ): ByteArray {
        validateInitialUrl(urlText)
        var current = URL(urlText)
        repeat(6) { redirectCount ->
            require(current.protocol.equals("https", true) && current.host.lowercase() in ALLOWED_DOWNLOAD_HOSTS) {
                "Download redirected outside the trusted SourceTX hosts."
            }
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "SourceTX-Companion-Android/$companionVersion")
                setRequestProperty("Accept", "application/octet-stream, application/json")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    if (redirectCount == 5) throw IOException("Download exceeded the redirect limit.")
                    val location = connection.getHeaderField("Location")
                        ?: throw IOException("Download redirect did not include a destination.")
                    current = URL(current, location)
                    return@repeat
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    throw IOException("SourceTX download returned HTTP $code.")
                }
                val contentLength = connection.contentLengthLong
                if (contentLength > maximumBytes || (expectedBytes != null && contentLength > 0 && contentLength != expectedBytes)) {
                    throw IOException("Downloaded file size does not match the signed release manifest.")
                }
                val output = ByteArrayOutputStream(
                    if (contentLength in 1..maximumBytes.toLong()) contentLength.toInt() else 8192
                )
                connection.inputStream.use { input ->
                    val buffer = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        if (total > maximumBytes) throw IOException("Downloaded file exceeds its safety limit.")
                        output.write(buffer, 0, read)
                        if (expectedBytes != null && expectedBytes > 0) {
                            onProgress(((total * 100L) / expectedBytes).toInt().coerceIn(0, 100))
                        }
                    }
                }
                val result = output.toByteArray()
                if (expectedBytes != null && result.size.toLong() != expectedBytes) {
                    throw IOException("Downloaded file ended before the signed size was received.")
                }
                return result
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("Download could not be completed safely.")
    }

    private fun validateInitialUrl(urlText: String) {
        val url = URL(urlText)
        require(url.protocol.equals("https", true) && url.host.equals("github.com", true)) {
            "SourceTX release URLs must use HTTPS on github.com."
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        require(VERSION_PATTERN.matches(left) && VERSION_PATTERN.matches(right)) {
            "Companion version requirement is invalid."
        }
        val leftParts = left.split('.').map(String::toInt)
        val rightParts = right.split('.').map(String::toInt)
        for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
            val difference = leftParts.getOrElse(index) { 0 }.compareTo(rightParts.getOrElse(index) { 0 })
            if (difference != 0) return difference
        }
        return 0
    }
}
