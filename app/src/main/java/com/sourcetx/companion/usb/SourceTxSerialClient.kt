package com.sourcetx.companion.usb

import com.hoho.android.usbserial.driver.UsbSerialPort
import com.sourcetx.companion.protocol.ModelTransferProtocol
import com.sourcetx.companion.protocol.ParseResult
import com.sourcetx.companion.protocol.SourceTxModelEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

data class SourceTxDeviceTransferInfo(
    val protocolVersion: Int,
    val schema: Int,
    val payloadSize: Int,
    val modelCount: Int,
    val activeModel: Int
)

class SourceTxSerialClient(private val port: UsbSerialPort) {

    companion object {
        const val COMMAND_PREFIX = "SOURCETX_XFER:"
        const val READ_TIMEOUT_MS = 250
        const val WRITE_TIMEOUT_MS = 3000
    }

    private val readBuffer = ByteArray(131072)
    private val lineAccumulator = StringBuilder()

    suspend fun handshake(timeoutMs: Long = 3000): Result<SourceTxDeviceTransferInfo> = withContext(Dispatchers.IO) {
        try {
            drain()
            sendLine("${COMMAND_PREFIX}HELLO")
            val line = readMatchingLine(timeoutMs) { it.startsWith("${COMMAND_PREFIX}READY:") }
                ?: return@withContext Result.failure(IOException("The transmitter did not respond to the handshake."))

            val fields = line.substring(COMMAND_PREFIX.length).split(":")
            if (fields.size != 6 || fields[0] != "READY") {
                return@withContext Result.failure(IOException("Incompatible handshake response from transmitter."))
            }

            val protocol = fields[1].toIntOrNull() ?: 0
            val schema = fields[2].toIntOrNull() ?: 0
            val payloadSize = fields[3].toIntOrNull() ?: 0
            val modelCount = fields[4].toIntOrNull() ?: 0
            val activeModel = fields[5].toIntOrNull() ?: 0

            if (protocol < 1 || schema < 1 || payloadSize < 1 || modelCount < 1 ||
                modelCount > ModelTransferProtocol.MAXIMUM_MODELS || activeModel < 1 || activeModel > modelCount) {
                return@withContext Result.failure(IOException("Invalid device transfer parameters returned."))
            }

            Result.success(
                SourceTxDeviceTransferInfo(
                    protocolVersion = protocol,
                    schema = schema,
                    payloadSize = payloadSize,
                    modelCount = modelCount,
                    activeModel = activeModel
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportModels(
        info: SourceTxDeviceTransferInfo,
        exportAll: Boolean,
        timeoutMs: Long = 15000,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Map<Int, SourceTxModelEnvelope>> = withContext(Dispatchers.IO) {
        try {
            drain()
            val command = if (exportAll) "${COMMAND_PREFIX}EXPORT:ALL" else "${COMMAND_PREFIX}EXPORT:${info.activeModel}"
            sendLine(command)

            val models = mutableMapOf<Int, SourceTxModelEnvelope>()
            val deadline = System.currentTimeMillis() + timeoutMs
            var done = false
            val expectedTotal = if (exportAll) info.modelCount else 1

            while (System.currentTimeMillis() < deadline && !done) {
                val line = readOneLine(deadline) ?: continue
                if (line.startsWith("${COMMAND_PREFIX}ERR:")) {
                    return@withContext Result.failure(IOException("Transmitter returned an export error: $line"))
                }
                if (line.startsWith("${COMMAND_PREFIX}DONE:EXPORT:")) {
                    done = true
                    break
                }
                if (!line.startsWith("${COMMAND_PREFIX}MODEL:")) {
                    continue
                }

                val remainder = line.substring("${COMMAND_PREFIX}MODEL:".length)
                val separator = remainder.indexOf(':')
                if (separator <= 0) continue

                val slot = remainder.substring(0, separator).toIntOrNull() ?: continue
                val envelopeText = "${ModelTransferProtocol.MODEL_PREFIX}${remainder.substring(separator + 1)}"

                when (val parseResult = ModelTransferProtocol.parseEnvelope(envelopeText, info.schema, info.payloadSize)) {
                    is ParseResult.Success -> {
                        models[slot] = parseResult.data
                        onProgress(models.size, expectedTotal)
                    }
                    is ParseResult.Error -> {
                        return@withContext Result.failure(IOException("Validation failed for slot $slot: ${parseResult.message}"))
                    }
                }
            }

            if (!done || models.isEmpty()) {
                return@withContext Result.failure(IOException("Export timed out or returned incomplete model data."))
            }

            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreModel(
        targetSlot: Int,
        envelope: SourceTxModelEnvelope,
        timeoutMs: Long = 5000
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            drain()
            // 1. PREPARE
            sendLine("${COMMAND_PREFIX}RESTORE:PREPARE:${envelope.schema}:${envelope.payloadSize}:$targetSlot")
            val prepLine = readMatchingLine(timeoutMs) {
                it.startsWith("${COMMAND_PREFIX}OK:RESTORE:PREPARE") || it.startsWith("${COMMAND_PREFIX}ERR:")
            } ?: return@withContext Result.failure(IOException("Transmitter timed out waiting for restore preparation."))

            if (prepLine.startsWith("${COMMAND_PREFIX}ERR:")) {
                return@withContext Result.failure(IOException("Transmitter rejected restore prepare: $prepLine"))
            }

            // 2. PUT
            sendLine("${COMMAND_PREFIX}RESTORE:PUT:$targetSlot:${envelope.hex}")
            val putLine = readMatchingLine(timeoutMs) {
                it.startsWith("${COMMAND_PREFIX}OK:RESTORE:PUT") || it.startsWith("${COMMAND_PREFIX}ERR:")
            } ?: return@withContext Result.failure(IOException("Transmitter timed out receiving model payload."))

            if (putLine.startsWith("${COMMAND_PREFIX}ERR:")) {
                return@withContext Result.failure(IOException("Transmitter rejected model payload: $putLine"))
            }

            // 3. FINALIZE
            sendLine("${COMMAND_PREFIX}RESTORE:FINALIZE")
            val finLine = readMatchingLine(timeoutMs) {
                it.startsWith("${COMMAND_PREFIX}OK:RESTORE:FINALIZE") || it.startsWith("${COMMAND_PREFIX}ERR:")
            } ?: return@withContext Result.failure(IOException("Transmitter timed out during restore finalization."))

            if (finLine.startsWith("${COMMAND_PREFIX}ERR:")) {
                return@withContext Result.failure(IOException("Restore finalization failed: $finLine"))
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sendLine(line: String) {
        val bytes = "$line\n".toByteArray(Charsets.US_ASCII)
        port.write(bytes, WRITE_TIMEOUT_MS)
    }

    private fun drain() {
        lineAccumulator.clear()
        try {
            val buf = ByteArray(1024)
            while (port.read(buf, 20) > 0) {}
        } catch (_: Exception) {}
    }

    private suspend fun readMatchingLine(
        timeoutMs: Long,
        predicate: (String) -> Boolean
    ): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val line = readOneLine(deadline)
            if (line != null && predicate(line)) {
                return line
            }
            delay(10)
        }
        return null
    }

    private fun readOneLine(deadline: Long): String? {
        while (System.currentTimeMillis() < deadline) {
            val newlineIndex = lineAccumulator.indexOf('\n')
            if (newlineIndex >= 0) {
                val line = lineAccumulator.substring(0, newlineIndex).trim()
                lineAccumulator.delete(0, newlineIndex + 1)
                return line
            }

            val readLen = try {
                port.read(readBuffer, READ_TIMEOUT_MS)
            } catch (_: Exception) {
                0
            }

            if (readLen > 0) {
                lineAccumulator.append(String(readBuffer, 0, readLen, Charsets.US_ASCII))
            } else {
                break
            }
        }
        return null
    }
}
