package com.sourcetx.companion.usb

import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Native implementation of Espressif ESP32-S3 ROM Bootloader SLIP protocol over USB CDC-ACM.
 * Performs chip preflight, SPI flash attach, block erase, and streaming flash writes.
 */
class Esp32BootloaderClient(private val port: UsbSerialPort) {

    companion object {
        const val ESP_SYNC = 0x08
        const val ESP_READ_REG = 0x0A
        const val ESP_WRITE_REG = 0x09
        const val ESP_SPI_ATTACH = 0x0D
        const val ESP_SPI_SET_PARAMS = 0x0B
        const val ESP_FLASH_BEGIN = 0x02
        const val ESP_FLASH_DATA = 0x03
        const val ESP_FLASH_END = 0x04

        const val SLIP_END = 0xC0.toByte()
        const val SLIP_ESC = 0xDB.toByte()
        const val SLIP_ESC_END = 0xDC.toByte()
        const val SLIP_ESC_ESC = 0xDD.toByte()

        const val FLASH_BLOCK_SIZE = 1024
        const val ESP32S3_MAGIC_REG = 0x60000078L
    }

    private val readBuffer = ByteArray(4096)

    /**
     * Attempts to synchronize with the ESP32-S3 ROM bootloader.
     */
    suspend fun sync(retries: Int = 10): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            drain()
            // Toggle DTR/RTS to trigger ROM bootloader if standard reset circuitry exists
            port.dtr = false
            port.rts = true
            delay(100)
            port.dtr = true
            port.rts = false
            delay(100)
            port.dtr = false
            port.rts = false
            delay(150)
            drain()

            // 36-byte sync packet payload: 0x07, 0x07, 0x12, 0x20 followed by 32 bytes of 0x55
            val syncPayload = ByteArray(36) { 0x55.toByte() }
            syncPayload[0] = 0x07
            syncPayload[1] = 0x07
            syncPayload[2] = 0x12
            syncPayload[3] = 0x20

            for (attempt in 1..retries) {
                sendPacket(ESP_SYNC, syncPayload, checksum = 0)
                val response = readPacket(300)
                if (response != null && response.size >= 8 && response[0] == 0x01.toByte() && response[1] == ESP_SYNC.toByte()) {
                    // Drain remaining sync echo responses
                    for (i in 0..6) {
                        readPacket(50)
                    }
                    return@withContext Result.success(Unit)
                }
                delay(50)
            }
            Result.failure(IOException("Failed to synchronize with ESP32-S3 ROM bootloader. Make sure device is connected."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads a register value from target MCU to identify ESP32-S3 silicon.
     */
    suspend fun readRegister(regAddress: Long): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val payload = ByteArray(4)
            ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN).putInt(regAddress.toInt())
            sendPacket(ESP_READ_REG, payload, checksum = 0)

            val resp = readPacket(1000)
                ?: return@withContext Result.failure(IOException("Register read timed out."))

            if (resp.size >= 8 && resp[0] == 0x01.toByte() && resp[1] == ESP_READ_REG.toByte()) {
                val value = ByteBuffer.wrap(resp, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                return@withContext Result.success(value)
            }
            Result.failure(IOException("Invalid register read response from target."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Attaches SPI flash and sets DIO 80MHz flash parameters.
     */
    suspend fun attachSpiFlash(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // SPI Attach: 8 zero bytes
            val attachPayload = ByteArray(8) { 0 }
            sendPacket(ESP_SPI_ATTACH, attachPayload, checksum = 0)
            val attachResp = readPacket(1000)
            if (attachResp == null || attachResp.size < 4 || attachResp[1] != ESP_SPI_ATTACH.toByte()) {
                return@withContext Result.failure(IOException("SPI flash attach failed."))
            }

            // SPI Set Parameters (Flash Size: 4MB = 0, Flash Mode: DIO = 0, Flash Freq: 80M = 0x0F)
            val paramBuf = ByteArray(24)
            val buf = ByteBuffer.wrap(paramBuf).order(ByteOrder.LITTLE_ENDIAN)
            buf.putInt(0) // total size 4MB
            buf.putInt(4 * 1024 * 1024)
            buf.putInt(64 * 1024)
            buf.putInt(4 * 1024)
            buf.putInt(256)
            buf.putInt(0xFFFF)

            sendPacket(ESP_SPI_SET_PARAMS, paramBuf, checksum = 0)
            readPacket(1000)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Flashes binary data to specified flash offset with live progress reporting.
     */
    suspend fun flashBinary(
        offset: Int,
        data: ByteArray,
        onProgress: (bytesWritten: Int, totalBytes: Int) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val numBlocks = (data.size + FLASH_BLOCK_SIZE - 1) / FLASH_BLOCK_SIZE
            val eraseSize = numBlocks * FLASH_BLOCK_SIZE

            // FLASH BEGIN (offset, eraseSize, blockSize, numBlocks)
            val beginPayload = ByteArray(16)
            val beginBuf = ByteBuffer.wrap(beginPayload).order(ByteOrder.LITTLE_ENDIAN)
            beginBuf.putInt(eraseSize)
            beginBuf.putInt(numBlocks)
            beginBuf.putInt(FLASH_BLOCK_SIZE)
            beginBuf.putInt(offset)

            sendPacket(ESP_FLASH_BEGIN, beginPayload, checksum = 0)
            val beginResp = readPacket(12000) // Erasing flash sectors can take up to 10 seconds
            if (beginResp == null || beginResp.size < 4 || beginResp[1] != ESP_FLASH_BEGIN.toByte()) {
                return@withContext Result.failure(IOException("Flash begin/erase rejected by target."))
            }

            var bytesWritten = 0
            for (blockIndex in 0 until numBlocks) {
                val blockStart = blockIndex * FLASH_BLOCK_SIZE
                val blockLength = kotlin.math.min(FLASH_BLOCK_SIZE, data.size - blockStart)
                val blockData = ByteArray(FLASH_BLOCK_SIZE) { 0xFF.toByte() }
                System.arraycopy(data, blockStart, blockData, 0, blockLength)

                // Checksum calculation: sum of all bytes in blockData
                var checksum = 0xEF
                for (b in blockData) {
                    checksum = checksum xor (b.toInt() and 0xFF)
                }

                // Header: size, seq, 0, 0 (16 bytes) + blockData
                val packetPayload = ByteArray(16 + FLASH_BLOCK_SIZE)
                val pBuf = ByteBuffer.wrap(packetPayload).order(ByteOrder.LITTLE_ENDIAN)
                pBuf.putInt(FLASH_BLOCK_SIZE)
                pBuf.putInt(blockIndex)
                pBuf.putInt(0)
                pBuf.putInt(0)
                pBuf.put(blockData)

                sendPacket(ESP_FLASH_DATA, packetPayload, checksum)
                val dataResp = readPacket(3000)
                if (dataResp == null || dataResp.size < 4 || dataResp[1] != ESP_FLASH_DATA.toByte()) {
                    return@withContext Result.failure(IOException("Flash block write failed at block $blockIndex."))
                }

                bytesWritten += blockLength
                onProgress(bytesWritten, data.size)
            }

            // FLASH END
            val endPayload = ByteArray(4)
            ByteBuffer.wrap(endPayload).order(ByteOrder.LITTLE_ENDIAN).putInt(1) // 1 = reboot to normal app
            sendPacket(ESP_FLASH_END, endPayload, checksum = 0)
            readPacket(1000)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sendPacket(op: Int, data: ByteArray, checksum: Int) {
        val out = ByteArrayOutputStream()
        out.write(SLIP_END.toInt())

        // Header: Direction (0x00), Opcode (1 byte), Size (2 bytes), Checksum (4 bytes)
        val header = ByteArray(8)
        val hBuf = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        hBuf.put(0x00.toByte()) // 0 = Request to target
        hBuf.put(op.toByte())
        hBuf.putShort(data.size.toShort())
        hBuf.putInt(checksum)

        writeSlipEscaped(out, header)
        writeSlipEscaped(out, data)

        out.write(SLIP_END.toInt())
        val packet = out.toByteArray()
        port.write(packet, 3000)
    }

    private fun writeSlipEscaped(out: ByteArrayOutputStream, bytes: ByteArray) {
        for (b in bytes) {
            when (b) {
                SLIP_END -> {
                    out.write(SLIP_ESC.toInt())
                    out.write(SLIP_ESC_END.toInt())
                }
                SLIP_ESC -> {
                    out.write(SLIP_ESC.toInt())
                    out.write(SLIP_ESC_ESC.toInt())
                }
                else -> out.write(b.toInt() and 0xFF)
            }
        }
    }

    private fun readPacket(timeoutMs: Long): ByteArray? {
        val deadline = System.currentTimeMillis() + timeoutMs
        val out = ByteArrayOutputStream()
        var inPacket = false
        var isEscaped = false

        while (System.currentTimeMillis() < deadline) {
            val len = try {
                port.read(readBuffer, 50)
            } catch (_: Exception) { 0 }

            if (len > 0) {
                for (i in 0 until len) {
                    val b = readBuffer[i]
                    if (b == SLIP_END) {
                        if (inPacket && out.size() > 0) {
                            return out.toByteArray()
                        }
                        inPacket = true
                        out.reset()
                        isEscaped = false
                    } else if (inPacket) {
                        if (isEscaped) {
                            when (b) {
                                SLIP_ESC_END -> out.write(SLIP_END.toInt())
                                SLIP_ESC_ESC -> out.write(SLIP_ESC.toInt())
                                else -> out.write(b.toInt() and 0xFF)
                            }
                            isEscaped = false
                        } else if (b == SLIP_ESC) {
                            isEscaped = true
                        } else {
                            out.write(b.toInt() and 0xFF)
                        }
                    }
                }
            }
        }
        return if (out.size() > 0) out.toByteArray() else null
    }

    private fun drain() {
        try {
            val buf = ByteArray(1024)
            while (port.read(buf, 20) > 0) {}
        } catch (_: Exception) {}
    }
}
