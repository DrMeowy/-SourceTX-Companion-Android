package com.sourcetx.companion.firmware

import java.nio.ByteBuffer
import java.nio.ByteOrder

object FirmwareImageValidator {
    private const val ESP_IMAGE_MAGIC = 0xE9
    private const val ESP32_S3_IMAGE_CHIP_ID = 9
    private const val APP_DESCRIPTOR_MAGIC = 0xABCD5432.toInt()
    private const val MINIMUM_IMAGE_SIZE = 64 * 1024
    private const val MAXIMUM_IMAGE_SIZE = 4 * 1024 * 1024

    fun validate(type: FirmwarePackageType, image: ByteArray): Result<Unit> = runCatching {
        require(image.size in MINIMUM_IMAGE_SIZE..MAXIMUM_IMAGE_SIZE) {
            "Firmware image size is outside the supported 64 KiB–4 MiB range."
        }
        require(image[0].toInt() and 0xFF == ESP_IMAGE_MAGIC) {
            "Firmware does not contain a valid ESP image header."
        }
        require(readU16(image, 12) == ESP32_S3_IMAGE_CHIP_ID) {
            "Firmware was not built for ESP32-S3."
        }

        when (type) {
            FirmwarePackageType.FACTORY -> {
                require(image.size > 0x10070) { "Factory image is incomplete." }
                require(image[0x8000] == 0xAA.toByte() && image[0x8001] == 0x50.toByte()) {
                    "Factory image does not contain a partition table at 0x8000."
                }
                require(readI32(image, 0x10020) == APP_DESCRIPTOR_MAGIC) {
                    "Factory image does not contain a SourceTX application at 0x10000."
                }
            }
            FirmwarePackageType.APPLICATION -> {
                require(readI32(image, 0x20) == APP_DESCRIPTOR_MAGIC) {
                    "Update package is not a valid ESP-IDF application image."
                }
                require(!(image.size > 0x8001 && image[0x8000] == 0xAA.toByte() && image[0x8001] == 0x50.toByte())) {
                    "A factory image cannot be installed as an application-only update."
                }
            }
        }
    }

    private fun readU16(data: ByteArray, offset: Int): Int {
        require(offset >= 0 && offset + 2 <= data.size) { "Firmware header is truncated." }
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF
    }

    private fun readI32(data: ByteArray, offset: Int): Int {
        require(offset >= 0 && offset + 4 <= data.size) { "Firmware image is truncated." }
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int
    }
}
