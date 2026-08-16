package com.sourcetx.companion

import com.sourcetx.companion.firmware.FirmwareImageValidator
import com.sourcetx.companion.firmware.FirmwarePackageType
import com.sourcetx.companion.firmware.FirmwareSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class FirmwareValidationTest {
    @Test
    fun sha256MatchesKnownVector() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            FirmwareSecurity.sha256Hex("abc".toByteArray())
        )
    }

    @Test
    fun acceptsStructurallyValidEsp32S3AppImage() {
        val image = image(128 * 1024)
        writeInt(image, 0x20, 0xABCD5432.toInt())
        assertTrue(FirmwareImageValidator.validate(FirmwarePackageType.APPLICATION, image).isSuccess)
    }

    @Test
    fun acceptsStructurallyValidFactoryImage() {
        val image = image(256 * 1024)
        image[0x8000] = 0xAA.toByte()
        image[0x8001] = 0x50.toByte()
        writeInt(image, 0x10020, 0xABCD5432.toInt())
        assertTrue(FirmwareImageValidator.validate(FirmwarePackageType.FACTORY, image).isSuccess)
    }

    @Test
    fun rejectsWrongChipAndWrongImageType() {
        val wrongChip = image(128 * 1024).apply {
            this[12] = 2
            writeInt(this, 0x20, 0xABCD5432.toInt())
        }
        assertTrue(FirmwareImageValidator.validate(FirmwarePackageType.APPLICATION, wrongChip).isFailure)

        val factory = image(256 * 1024).apply {
            this[0x8000] = 0xAA.toByte()
            this[0x8001] = 0x50.toByte()
            writeInt(this, 0x20, 0xABCD5432.toInt())
        }
        assertTrue(FirmwareImageValidator.validate(FirmwarePackageType.APPLICATION, factory).isFailure)
    }

    private fun image(size: Int) = ByteArray(size).apply {
        this[0] = 0xE9.toByte()
        this[12] = 9
        this[13] = 0
    }

    private fun writeInt(data: ByteArray, offset: Int, value: Int) {
        ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(value)
    }
}
