package com.sourcetx.companion.usb

import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.min

data class Esp32TargetInfo(
    val chipId: Int,
    val flashId: Long,
    val flashSizeBytes: Int,
    val securityFlags: Long
)

/**
 * ESP32-S3 ROM serial-bootloader client. Every response is status-checked and
 * every completed write is verified on the target with SPI_FLASH_MD5.
 */
class Esp32BootloaderClient(private val port: UsbSerialPort) {
    companion object {
        private const val ESP_FLASH_BEGIN = 0x02
        private const val ESP_FLASH_DATA = 0x03
        private const val ESP_FLASH_END = 0x04
        private const val ESP_SYNC = 0x08
        private const val ESP_WRITE_REG = 0x09
        private const val ESP_READ_REG = 0x0A
        private const val ESP_SPI_SET_PARAMS = 0x0B
        private const val ESP_SPI_ATTACH = 0x0D
        private const val ESP_SPI_FLASH_MD5 = 0x13
        private const val ESP_GET_SECURITY_INFO = 0x14
        private const val ESP32_S3_CHIP_ID = 9
        private const val EXPECTED_FLASH_SIZE = 4 * 1024 * 1024
        private const val FLASH_BLOCK_SIZE = 1024
        private const val CHECKSUM_SEED = 0xEF

        private const val SPI_REG_BASE = 0x60002000L
        private const val SPI_CMD_REG = SPI_REG_BASE + 0x00
        private const val SPI_USR_REG = SPI_REG_BASE + 0x18
        private const val SPI_USR1_REG = SPI_REG_BASE + 0x1C
        private const val SPI_USR2_REG = SPI_REG_BASE + 0x20
        private const val SPI_MOSI_DLEN_REG = SPI_REG_BASE + 0x24
        private const val SPI_MISO_DLEN_REG = SPI_REG_BASE + 0x28
        private const val SPI_W0_REG = SPI_REG_BASE + 0x58
        private const val SPI_CMD_USR = 1L shl 18
        private const val SPI_USR_COMMAND = 1L shl 31
        private const val SPI_USR_MISO = 1L shl 28
        private const val SPI_USR_MOSI = 1L shl 27

        private const val SLIP_END = 0xC0
        private const val SLIP_ESC = 0xDB
        private const val SLIP_ESC_END = 0xDC
        private const val SLIP_ESC_ESC = 0xDD
    }

    private data class CommandResponse(val value: Long, val data: ByteArray)
    private val readBuffer = ByteArray(4096)

    suspend fun preflight(): Result<Esp32TargetInfo> = withContext(Dispatchers.IO) {
        runCatching {
            synchronize()
            val security = command(
                ESP_GET_SECURITY_INFO,
                ByteArray(0),
                responseDataLength = 20,
                timeoutMs = 2_000
            ).data
            val securityBuffer = ByteBuffer.wrap(security).order(ByteOrder.LITTLE_ENDIAN)
            val flags = securityBuffer.int.toLong() and 0xFFFFFFFFL
            securityBuffer.position(12)
            val chipId = securityBuffer.int
            require(chipId == ESP32_S3_CHIP_ID) {
                "Connected chip ID is $chipId, not ESP32-S3."
            }
            require(flags and (1L shl 2) == 0L) {
                "Secure Download Mode is enabled; standard SourceTX flashing is not permitted."
            }

            attachSpiFlash()
            setSpiParameters(EXPECTED_FLASH_SIZE)
            val flashId = runSpiFlashCommand(0x9F, readBits = 24)
            val flashSize = decodeFlashSize(flashId)
            require(flashSize == EXPECTED_FLASH_SIZE) {
                val detected = if (flashSize > 0) "${flashSize / (1024 * 1024)}MB" else "unknown"
                "Connected SPI flash is $detected; this release requires 4MB."
            }
            Esp32TargetInfo(chipId, flashId, flashSize, flags)
        }
    }

    suspend fun eraseEntireFlash(onStatus: (String) -> Unit = {}): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                onStatus("Sending chip erase command...")
                runSpiFlashCommand(0x06)
                runSpiFlashCommand(0xC7)
                val deadline = System.currentTimeMillis() + 180_000L
                do {
                    delay(250)
                    if (runSpiFlashCommand(0x05, readBits = 8) and 0x01L == 0L) {
                        onStatus("Flash erase completed.")
                        return@runCatching
                    }
                } while (System.currentTimeMillis() < deadline)
                throw IOException("Full flash erase timed out.")
            }
        }

    suspend fun writeAndVerify(
        offset: Int,
        data: ByteArray,
        onProgress: (bytesWritten: Int, totalBytes: Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(offset >= 0 && data.isNotEmpty() && offset.toLong() + data.size <= EXPECTED_FLASH_SIZE) {
                "Firmware write is outside the supported 4MB flash range."
            }
            val numBlocks = (data.size + FLASH_BLOCK_SIZE - 1) / FLASH_BLOCK_SIZE
            command(
                ESP_FLASH_BEGIN,
                littleEndianInts(data.size, numBlocks, FLASH_BLOCK_SIZE, offset, 0),
                timeoutMs = 120_000
            )

            var written = 0
            for (sequence in 0 until numBlocks) {
                val sourceOffset = sequence * FLASH_BLOCK_SIZE
                val sourceLength = min(FLASH_BLOCK_SIZE, data.size - sourceOffset)
                val block = ByteArray(FLASH_BLOCK_SIZE) { 0xFF.toByte() }
                data.copyInto(block, 0, sourceOffset, sourceOffset + sourceLength)
                val payload = ByteArray(16 + FLASH_BLOCK_SIZE)
                ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).apply {
                    putInt(FLASH_BLOCK_SIZE)
                    putInt(sequence)
                    putInt(0)
                    putInt(0)
                    put(block)
                }

                var failure: Throwable? = null
                for (attempt in 0..1) {
                    try {
                        command(
                            ESP_FLASH_DATA,
                            payload,
                            checksum = checksum(block),
                            timeoutMs = 5_000
                        )
                        failure = null
                        break
                    } catch (error: Throwable) {
                        failure = error
                        if (attempt == 0) drain()
                    }
                }
                if (failure != null) throw IOException("Flash write failed at block $sequence.", failure)
                written += sourceLength
                onProgress(written, data.size)
            }

            val remoteMd5 = command(
                ESP_SPI_FLASH_MD5,
                littleEndianInts(offset, data.size, 0, 0),
                responseDataLength = 32,
                timeoutMs = 60_000
            ).data.toString(Charsets.US_ASCII).lowercase()
            val localMd5 = MessageDigest.getInstance("MD5")
                .digest(data)
                .joinToString("") { "%02x".format(it) }
            require(remoteMd5 == localMd5) {
                "Flash verification failed: data on the ESP32-S3 does not match the firmware package."
            }
        }
    }

    suspend fun reboot(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            command(ESP_FLASH_END, littleEndianInts(0), timeoutMs = 2_000)
            Unit
        }.recoverCatching {
            // Some USB/JTAG transports disappear before delivering the ROM
            // response. A reset pulse is safe after successful MD5 verification.
            port.dtr = false
            port.rts = true
            delay(100)
            port.rts = false
            port.dtr = false
            Unit
        }
    }

    private suspend fun synchronize() {
        val payload = ByteArray(36) { 0x55.toByte() }.apply {
            this[0] = 0x07
            this[1] = 0x07
            this[2] = 0x12
            this[3] = 0x20
        }

        // 1. Direct Sync: If user already held BOOT while connecting USB,
        // the ESP32-S3 ROM bootloader is already active. Do NOT reset immediately!
        drain()
        for (i in 0 until 6) {
            try {
                command(ESP_SYNC, payload, timeoutMs = 250)
                repeat(7) { readSlipPacket(30) }
                return
            } catch (_: Throwable) {
                delay(30)
            }
        }

        // 2. Hardware auto-reset into download mode (for boards with DTR/RTS EN/IO0 circuit)
        try {
            port.dtr = true  // Assert IO0 LOW (BOOT button state)
            port.rts = true  // Assert EN LOW (Reset active)
            delay(100)
            port.rts = false // Release EN (chip boots into ROM download mode with IO0 LOW)
            delay(100)
            port.dtr = false // Release IO0
            delay(150)
        } catch (_: Exception) {}
        drain()

        // 3. Sync loop with retries
        var lastFailure: Throwable? = null
        for (i in 0 until 25) {
            try {
                command(ESP_SYNC, payload, timeoutMs = 350)
                repeat(7) { readSlipPacket(30) }
                return
            } catch (error: Throwable) {
                lastFailure = error
                delay(40)
            }
        }
        throw IOException(
            "ESP32-S3 bootloader did not respond. Hold BOOT while connecting USB, then try again.",
            lastFailure
        )
    }

    private fun attachSpiFlash() {
        command(ESP_SPI_ATTACH, littleEndianInts(0, 0), timeoutMs = 2_000)
    }

    private fun setSpiParameters(size: Int) {
        command(
            ESP_SPI_SET_PARAMS,
            littleEndianInts(0, size, 64 * 1024, 4 * 1024, 256, 0xFFFF),
            timeoutMs = 2_000
        )
    }

    private fun readRegister(address: Long): Long =
        command(ESP_READ_REG, littleEndianInts(address.toInt()), timeoutMs = 2_000).value

    private fun writeRegister(address: Long, value: Long, mask: Long = 0xFFFFFFFFL, delayUs: Int = 0) {
        command(
            ESP_WRITE_REG,
            littleEndianInts(address.toInt(), value.toInt(), mask.toInt(), delayUs),
            timeoutMs = 2_000
        )
    }

    private fun runSpiFlashCommand(
        spiCommand: Int,
        data: ByteArray = ByteArray(0),
        readBits: Int = 0
    ): Long {
        require(readBits in 0..32 && data.size <= 64) { "Unsupported SPI command size." }
        val oldUsr = readRegister(SPI_USR_REG)
        val oldUsr2 = readRegister(SPI_USR2_REG)
        try {
            if (data.isNotEmpty()) writeRegister(SPI_MOSI_DLEN_REG, data.size * 8L - 1)
            if (readBits > 0) writeRegister(SPI_MISO_DLEN_REG, readBits.toLong() - 1)
            var flags = SPI_USR_COMMAND
            if (data.isNotEmpty()) flags = flags or SPI_USR_MOSI
            if (readBits > 0) flags = flags or SPI_USR_MISO
            writeRegister(SPI_USR1_REG, 0)
            writeRegister(SPI_USR_REG, flags)
            writeRegister(SPI_USR2_REG, (7L shl 28) or (spiCommand.toLong() and 0xFF))

            if (data.isEmpty()) {
                writeRegister(SPI_W0_REG, 0)
            } else {
                val padded = data.copyOf((data.size + 3) / 4 * 4)
                val buffer = ByteBuffer.wrap(padded).order(ByteOrder.LITTLE_ENDIAN)
                var register = SPI_W0_REG
                while (buffer.remaining() >= 4) {
                    writeRegister(register, buffer.int.toLong() and 0xFFFFFFFFL)
                    register += 4
                }
            }
            writeRegister(SPI_CMD_REG, SPI_CMD_USR)
            repeat(50) {
                if (readRegister(SPI_CMD_REG) and SPI_CMD_USR == 0L) return readRegister(SPI_W0_REG)
            }
            throw IOException("SPI flash command 0x${spiCommand.toString(16)} timed out.")
        } finally {
            writeRegister(SPI_USR_REG, oldUsr)
            writeRegister(SPI_USR2_REG, oldUsr2)
        }
    }

    private fun decodeFlashSize(flashId: Long): Int {
        val vendor = (flashId and 0xFF).toInt()
        val sizeCode = if (vendor == 0x1F) ((flashId shr 8) and 0x1F).toInt()
        else ((flashId shr 16) and 0xFF).toInt()
        return if (vendor == 0x1F) {
            mapOf(0x04 to 512 * 1024, 0x05 to 1024 * 1024, 0x06 to 2 * 1024 * 1024,
                0x07 to 4 * 1024 * 1024, 0x08 to 8 * 1024 * 1024, 0x09 to 16 * 1024 * 1024)[sizeCode] ?: 0
        } else {
            mapOf(0x13 to 512 * 1024, 0x14 to 1024 * 1024, 0x15 to 2 * 1024 * 1024,
                0x16 to 4 * 1024 * 1024, 0x17 to 8 * 1024 * 1024, 0x18 to 16 * 1024 * 1024)[sizeCode] ?: 0
        }
    }

    private fun command(
        opcode: Int,
        payload: ByteArray,
        checksum: Int = 0,
        responseDataLength: Int = 0,
        timeoutMs: Long
    ): CommandResponse {
        sendRequest(opcode, payload, checksum)
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val packet = readSlipPacket((deadline - System.currentTimeMillis()).coerceAtLeast(1)) ?: continue
            if (packet.size < 8) continue
            val header = ByteBuffer.wrap(packet, 0, 8).order(ByteOrder.LITTLE_ENDIAN)
            val direction = header.get().toInt() and 0xFF
            val responseOpcode = header.get().toInt() and 0xFF
            val dataLength = header.short.toInt() and 0xFFFF
            val value = header.int.toLong() and 0xFFFFFFFFL
            if (direction != 1 || responseOpcode != opcode) continue
            if (packet.size < 8 + dataLength) throw IOException("ESP32-S3 returned a truncated response.")
            val data = packet.copyOfRange(8, 8 + dataLength)
            if (data.size < responseDataLength + 2) {
                throw IOException("ESP32-S3 returned an incomplete status response for command 0x${opcode.toString(16)}.")
            }
            val status = data[responseDataLength].toInt() and 0xFF
            val reason = data[responseDataLength + 1].toInt() and 0xFF
            if (status != 0) {
                throw IOException("ESP32-S3 rejected command 0x${opcode.toString(16)} (status $status, reason $reason).")
            }
            return CommandResponse(value, data.copyOfRange(0, responseDataLength))
        }
        throw IOException("ESP32-S3 timed out during command 0x${opcode.toString(16)}.")
    }

    private fun sendRequest(opcode: Int, payload: ByteArray, checksum: Int) {
        val raw = ByteArray(8 + payload.size)
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).apply {
            put(0)
            put(opcode.toByte())
            putShort(payload.size.toShort())
            putInt(checksum)
            put(payload)
        }
        val framed = ByteArrayOutputStream(raw.size + 2).apply {
            write(SLIP_END)
            raw.forEach { byte ->
                when (byte.toInt() and 0xFF) {
                    SLIP_END -> {
                        write(SLIP_ESC)
                        write(SLIP_ESC_END)
                    }
                    SLIP_ESC -> {
                        write(SLIP_ESC)
                        write(SLIP_ESC_ESC)
                    }
                    else -> write(byte.toInt() and 0xFF)
                }
            }
            write(SLIP_END)
        }.toByteArray()
        port.write(framed, 5_000)
    }

    private fun readSlipPacket(timeoutMs: Long): ByteArray? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val output = ByteArrayOutputStream()
        var inPacket = false
        var escaped = false
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).coerceIn(1, 100).toInt()
            val count = try { port.read(readBuffer, remaining) } catch (_: Exception) { 0 }
            if (count <= 0) continue
            for (index in 0 until count) {
                val value = readBuffer[index].toInt() and 0xFF
                when {
                    value == SLIP_END -> {
                        if (inPacket && output.size() > 0) return output.toByteArray()
                        inPacket = true
                        escaped = false
                        output.reset()
                    }
                    !inPacket -> Unit
                    escaped -> {
                        when (value) {
                            SLIP_ESC_END -> output.write(SLIP_END)
                            SLIP_ESC_ESC -> output.write(SLIP_ESC)
                            else -> throw IOException("ESP32-S3 returned an invalid SLIP escape sequence.")
                        }
                        escaped = false
                    }
                    value == SLIP_ESC -> escaped = true
                    else -> output.write(value)
                }
            }
        }
        return null
    }

    private fun drain() {
        try { while (port.read(readBuffer, 20) > 0) Unit } catch (_: Exception) {}
    }

    private fun checksum(data: ByteArray): Int =
        data.fold(CHECKSUM_SEED) { value, byte -> value xor (byte.toInt() and 0xFF) }

    private fun littleEndianInts(vararg values: Int): ByteArray =
        ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach(::putInt)
        }.array()
}
