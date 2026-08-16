package com.sourcetx.companion.firmware

data class FirmwareReleaseManifest(
    val schema: Int,
    val product: String,
    val hardware: String,
    val channel: String,
    val version: String,
    val size: Long,
    val sha256: String,
    val imageUrl: String,
    val signatureUrl: String,
    val releaseUrl: String,
    val chip: String? = null,
    val flashSize: String? = null,
    val flashMode: String? = null,
    val flashFrequency: String? = null,
    val flashOffset: String? = null,
    val minimumCompanionVersion: String? = null
)

data class VerifiedFirmwarePackage(
    val manifest: FirmwareReleaseManifest,
    val image: ByteArray
)

enum class FirmwarePackageType {
    FACTORY,
    APPLICATION
}
