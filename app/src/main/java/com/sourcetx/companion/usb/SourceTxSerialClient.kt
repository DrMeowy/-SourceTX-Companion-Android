package com.sourcetx.companion.usb

import com.hoho.android.usbserial.driver.UsbSerialPort
import com.sourcetx.companion.protocol.ModelTransferProtocol
import com.sourcetx.companion.protocol.ParseResult
import com.sourcetx.companion.protocol.SourceTxModelBundle
import com.sourcetx.companion.protocol.SourceTxModelEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

data class SourceTxDeviceTransferInfo(
    val protocolVersion: Int,
    val schema: Int,
    val payloadSize: Int,
    var modelCount: Int,
    val activeModel: Int
)

class SourceTxSerialClient(private val port: UsbSerialPort) {
    companion object {
        const val COMMAND_PREFIX = "SOURCETX_XFER:"
        private const val READ_TIMEOUT_MS = 250
        private const val WRITE_TIMEOUT_MS = 3000
    }

    private val readBuffer = ByteArray(131072)
    private val lineAccumulator = StringBuilder()

    suspend fun handshake(timeoutMs: Long = 4000): Result<SourceTxDeviceTransferInfo> =
        withContext(Dispatchers.IO) {
            runCatching {
                drain()
                sendLine("${COMMAND_PREFIX}HELLO")
                val line = readMatchingLine(timeoutMs) { it.startsWith("${COMMAND_PREFIX}READY:") }
                    ?: throw IOException("SourceTX did not respond. Open Model Transfer on the transmitter and try again.")
                val fields = line.substring(COMMAND_PREFIX.length).split(':')
                if (fields.size != 6 || fields[0] != "READY") {
                    throw IOException("The connected device returned an incompatible SourceTX handshake.")
                }
                val info = SourceTxDeviceTransferInfo(
                    protocolVersion = fields[1].toIntOrNull() ?: 0,
                    schema = fields[2].toIntOrNull() ?: 0,
                    payloadSize = fields[3].toIntOrNull() ?: 0,
                    modelCount = fields[4].toIntOrNull() ?: 0,
                    activeModel = fields[5].toIntOrNull() ?: 0
                )
                require(info.protocolVersion >= 1 && info.schema >= 1 && info.payloadSize >= 1 &&
                    info.modelCount in 1..ModelTransferProtocol.MAXIMUM_MODELS &&
                    info.activeModel in 1..info.modelCount) {
                    "The connected device returned invalid model-transfer information."
                }
                info
            }
        }

    suspend fun exportModels(
        info: SourceTxDeviceTransferInfo,
        exportAll: Boolean,
        timeoutMs: Long = if (exportAll) 45_000 else 15_000,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Map<Int, SourceTxModelEnvelope>> = withContext(Dispatchers.IO) {
        runCatching {
            drain()
            val expected = if (exportAll) info.modelCount else 1
            sendLine(
                if (exportAll) "${COMMAND_PREFIX}EXPORT:ALL"
                else "${COMMAND_PREFIX}EXPORT:${info.activeModel}"
            )
            val models = sortedMapOf<Int, SourceTxModelEnvelope>()
            val deadline = System.currentTimeMillis() + timeoutMs
            var completedCount: Int? = null
            while (System.currentTimeMillis() < deadline && completedCount == null) {
                val line = readOneLine(deadline) ?: continue
                when {
                    line.startsWith("${COMMAND_PREFIX}ERR:") ->
                        throw IOException("SourceTX rejected the model export.")
                    line.startsWith("${COMMAND_PREFIX}DONE:EXPORT:") -> {
                        completedCount = line.substringAfterLast(':').toIntOrNull()
                            ?: throw IOException("SourceTX returned an invalid export completion message.")
                    }
                    line.startsWith("${COMMAND_PREFIX}MODEL:") -> {
                        val remainder = line.substring("${COMMAND_PREFIX}MODEL:".length)
                        val separator = remainder.indexOf(':')
                        if (separator <= 0) throw IOException("A model export message was incomplete.")
                        val slot = remainder.substring(0, separator).toIntOrNull()
                            ?: throw IOException("A model export contained an invalid slot number.")
                        if (slot !in 1..info.modelCount || models.containsKey(slot)) {
                            throw IOException("SourceTX returned a repeated or out-of-range model slot.")
                        }
                        val envelopeText = ModelTransferProtocol.MODEL_PREFIX + remainder.substring(separator + 1)
                        when (val parsed = ModelTransferProtocol.parseEnvelope(
                            envelopeText,
                            info.schema,
                            info.payloadSize
                        )) {
                            is ParseResult.Error -> throw IOException("Model slot $slot was damaged during transfer: ${parsed.message}")
                            is ParseResult.Success -> {
                                models[slot] = parsed.data
                                onProgress(models.size, expected)
                            }
                        }
                    }
                }
            }
            if (completedCount != expected || models.size != expected) {
                throw IOException("The backup stopped before every model was received (${models.size} of $expected).")
            }
            if (exportAll && models.keys != (1..info.modelCount).toSet()) {
                throw IOException("The complete backup is missing one or more model slots.")
            }
            if (!exportAll && models.keys.singleOrNull() != info.activeModel) {
                throw IOException("SourceTX returned the wrong active model slot.")
            }
            models
        }
    }

    suspend fun importModel(
        info: SourceTxDeviceTransferInfo,
        targetSlot: Int,
        envelope: SourceTxModelEnvelope,
        timeoutMs: Long = 15_000
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(envelope.schema == info.schema && envelope.payloadSize == info.payloadSize) {
                "This backup was created by an incompatible SourceTX firmware version."
            }
            require(targetSlot in 1..minOf(ModelTransferProtocol.MAXIMUM_MODELS, info.modelCount + 1)) {
                "Choose an existing model slot or the next empty slot."
            }
            drain()
            sendLine("${COMMAND_PREFIX}IMPORT:$targetSlot:${envelope.hex}")
            val expected = "${COMMAND_PREFIX}OK:IMPORT:$targetSlot"
            val line = readMatchingLine(timeoutMs) {
                it == expected || it.startsWith("${COMMAND_PREFIX}ERR:")
            } ?: throw IOException("SourceTX did not confirm the model restore.")
            if (line != expected) throw IOException("SourceTX rejected the model restore to slot $targetSlot.")
            if (targetSlot == info.modelCount + 1) info.modelCount = targetSlot
        }
    }

    suspend fun restoreBundle(
        info: SourceTxDeviceTransferInfo,
        bundle: SourceTxModelBundle,
        envelopes: List<SourceTxModelEnvelope>,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(bundle.protocol <= info.protocolVersion && bundle.schema == info.schema &&
                bundle.payloadSize == info.payloadSize && envelopes.size == bundle.modelCount) {
                "This complete backup is incompatible with the connected SourceTX firmware."
            }
            require(!(info.activeModel > bundle.modelCount && info.modelCount > bundle.modelCount)) {
                "Select a transmitter model within slots 1–${bundle.modelCount}, then try the complete restore again."
            }
            envelopes.forEachIndexed { index, envelope ->
                importModel(info, index + 1, envelope).getOrElse { error ->
                    throw IOException(
                        "Restore stopped at slot ${index + 1} after $index models were restored: ${error.message}",
                        error
                    )
                }
                onProgress(index + 1, envelopes.size)
            }
            if (info.modelCount > bundle.modelCount) setModelCount(info, bundle.modelCount).getOrThrow()
        }
    }

    suspend fun setModelCount(
        info: SourceTxDeviceTransferInfo,
        modelCount: Int,
        timeoutMs: Long = 10_000
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(modelCount in info.activeModel..info.modelCount) { "The requested model count is not safe." }
            drain()
            sendLine("${COMMAND_PREFIX}SET_COUNT:$modelCount")
            val expected = "${COMMAND_PREFIX}OK:SET_COUNT:$modelCount"
            val line = readMatchingLine(timeoutMs) {
                it == expected || it.startsWith("${COMMAND_PREFIX}ERR:")
            }
            if (line != expected) throw IOException("SourceTX did not confirm the restored model list.")
            info.modelCount = modelCount
        }
    }

    private fun sendLine(line: String) {
        port.write("$line\n".toByteArray(Charsets.US_ASCII), WRITE_TIMEOUT_MS)
    }

    private fun drain() {
        lineAccumulator.clear()
        try {
            val buffer = ByteArray(1024)
            while (port.read(buffer, 20) > 0) Unit
        } catch (_: Exception) {}
    }

    private suspend fun readMatchingLine(timeoutMs: Long, predicate: (String) -> Boolean): String? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val line = readOneLine(deadline)
            if (line != null && predicate(line)) return line
            delay(10)
        }
        return null
    }

    private fun readOneLine(deadline: Long): String? {
        while (System.currentTimeMillis() < deadline) {
            val newline = lineAccumulator.indexOf("\n")
            if (newline >= 0) {
                val line = lineAccumulator.substring(0, newline).trim()
                lineAccumulator.delete(0, newline + 1)
                return line
            }
            val count = try { port.read(readBuffer, READ_TIMEOUT_MS) } catch (_: Exception) { 0 }
            if (count > 0) lineAccumulator.append(String(readBuffer, 0, count, Charsets.US_ASCII))
            else break
        }
        return null
    }
}
