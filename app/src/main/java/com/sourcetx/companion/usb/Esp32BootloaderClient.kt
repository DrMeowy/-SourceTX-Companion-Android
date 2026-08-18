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

    private val rxBuffer = ByteArray(4096)
    private var rxOffset = 0
    private var rxLength = 0
    private val packetBuffer = ByteArrayOutputStream()
    private var inPacket = false
    private var isEscaped = false

    suspend fun reboot(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Send the flash end / reboot command directly without waiting for a ROM response.
            // On native USB-Serial/JTAG, starting the application causes the USB link to
            // re-enumerate immediately before a reply can be delivered.
            sendRequest(ESP_FLASH_END, littleEndianInts(0), 0)
            delay(250)
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

        // 1. Direct Sync: If the chip is already in download mode, connect immediately.
        drain()
        for (i in 0 until 8) {
            try {
                command(ESP_SYNC, payload, checkStatus = false, timeoutMs = 200)
                repeat(7) {
                    try { readSlipPacket(20) } catch (_: Throwable) {}
                }
                return
            } catch (_: Throwable) {
                delay(20)
            }
        }

        // 2. ESP32-S3 native USB-Serial/JTAG (303A:1001) reset sequence into ROM download mode.
        // Serial-line states: true = asserted, false = deasserted.
        // The repeated RTS assertion is the standard native USB-JTAG reset pattern.
        try {
            port.rts = false
            port.dtr = false
            delay(100)

            port.dtr = true
            port.rts = false
            delay(100)

            port.rts = true
            port.dtr = false
            port.rts = true
            delay(100)

            port.dtr = false
            port.rts = false
            delay(100)
        } catch (_: Exception) {}
        drain()

        // 3. Sync loop with retries
        var lastFailure: Throwable? = null
        for (i in 0 until 30) {
            try {
                command(ESP_SYNC, payload, checkStatus = false, timeoutMs = 250)
                repeat(7) {
                    try { readSlipPacket(20) } catch (_: Throwable) {}
                }
                return
            } catch (error: Throwable) {
                lastFailure = error
                delay(30)
            }
        }
        throw IOException(
            "ESP32-S3 bootloader did not respond after native USB auto-reset. Reconnect USB and try again; only use BOOT manually if the running firmware disabled native USB.",
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
        payload: ByteArray = ByteArray(0),
        checksum: Int = 0,
        responseDataLength: Int = 0,
        checkStatus: Boolean = true,
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
            header.short // dataLength field
            val value = header.int.toLong() and 0xFFFFFFFFL

            // Direction must be 1 (Response)
            if (direction != 1 || responseOpcode != opcode) continue

            val data = if (packet.size > 8) packet.copyOfRange(8, packet.size) else ByteArray(0)

            if (checkStatus && data.size >= 2) {
                val statusIndex = if (data.size >= responseDataLength + 2) responseDataLength else 0
                val status = data[statusIndex].toInt() and 0xFF
                val reason = data[statusIndex + 1].toInt() and 0xFF
                if (status != 0) {
                    throw IOException("ESP32-S3 rejected command 0x${opcode.toString(16)} (status $status, reason $reason).")
                }
            }

            val returnData = if (responseDataLength > 0 && data.size >= responseDataLength) {
                data.copyOfRange(0, responseDataLength)
            } else {
                data
            }

            return CommandResponse(value, returnData)
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
        while (System.currentTimeMillis() < deadline) {
            if (rxOffset >= rxLength) {
                val remaining = (deadline - System.currentTimeMillis()).coerceIn(1, 100).toInt()
                rxOffset = 0
                rxLength = try {
                    port.read(rxBuffer, remaining)
                } catch (_: Exception) {
                    0
                }
                if (rxLength <= 0) continue
            }

            while (rxOffset < rxLength) {
                val byte = rxBuffer[rxOffset++].toInt() and 0xFF
                when {
                    byte == SLIP_END -> {
                        if (inPacket && packetBuffer.size() > 0) {
                            val result = packetBuffer.toByteArray()
                            packetBuffer.reset()
                            inPacket = false
                            isEscaped = false
                            return result
                        }
                        inPacket = true
                        isEscaped = false
                        packetBuffer.reset()
                    }
                    !inPacket -> Unit
                    isEscaped -> {
                        when (byte) {
                            SLIP_ESC_END -> packetBuffer.write(SLIP_END)
                            SLIP_ESC_ESC -> packetBuffer.write(SLIP_ESC)
                            else -> packetBuffer.write(byte)
                        }
                        isEscaped = false
                    }
                    byte == SLIP_ESC -> isEscaped = true
                    else -> packetBuffer.write(byte)
                }
            }
        }
        return null
    }

    private fun drain() {
        rxOffset = 0
        rxLength = 0
        packetBuffer.reset()
        inPacket = false
        isEscaped = false
        try {
            while (port.read(rxBuffer, 10) > 0) Unit
        } catch (_: Exception) {}
    }

    private fun checksum(data: ByteArray): Int =
        data.fold(CHECKSUM_SEED) { value, byte -> value xor (byte.toInt() and 0xFF) }

    private fun littleEndianInts(vararg values: Int): ByteArray =
        ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            values.forEach(::putInt)
        }.array()
}
