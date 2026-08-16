package com.sourcetx.companion.protocol

import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min

sealed class ParseResult<out T> {
    data class Success<T>(val data: T) : ParseResult<T>()
    data class Error(val message: String) : ParseResult<Nothing>()
}

object ModelTransferProtocol {
    const val TRANSFER_MAGIC: Long = 0x5354584DL
    const val PUBLIC_SCHEMA_VERSION: Int = 21
    const val MODEL_PREFIX: String = "SOURCETX_MODEL:"
    const val BUNDLE_FORMAT: String = "SOURCETX_MODEL_BUNDLE"
    const val BUNDLE_VERSION: Int = 1
    const val MAXIMUM_MODELS: Int = 20

    /**
     * Calculates FNV-1a checksum over payload, magic, and version/payloadSize header fields.
     */
    fun calculateFnv1a(
        payload: ByteArray,
        magic: Long,
        version: Int,
        payloadSize: Int
    ): Long {
        var hash: Long = 2166136261L and 0xFFFFFFFFL
        val prime: Long = 16777619L and 0xFFFFFFFFL

        for (b in payload) {
            val unsignedByte = (b.toInt() and 0xFF).toLong()
            hash = (hash xor unsignedByte) and 0xFFFFFFFFL
            hash = (hash * prime) and 0xFFFFFFFFL
        }

        // XOR magic (4 bytes little-endian as uint32)
        val magicUint = magic and 0xFFFFFFFFL
        hash = (hash xor magicUint) and 0xFFFFFFFFL
        hash = (hash * prime) and 0xFFFFFFFFL

        // XOR ((version << 16) | payloadSize)
        val metaUint = (((version and 0xFFFF).toLong() shl 16) or ((payloadSize and 0xFFFF).toLong())) and 0xFFFFFFFFL
        hash = (hash xor metaUint) and 0xFFFFFFFFL

        return hash and 0xFFFFFFFFL
    }

    /**
     * Parses an incoming SOURCETX_MODEL: ASCII hex envelope.
     */
    fun parseEnvelope(
        text: String?,
        expectedSchema: Int = 0,
        expectedPayloadSize: Int = 0
    ): ParseResult<SourceTxModelEnvelope> {
        if (text.isNullOrBlank()) {
            return ParseResult.Error("The model file is empty.")
        }

        val content = text.trim()
        if (!content.startsWith(MODEL_PREFIX, ignoreCase = true)) {
            return ParseResult.Error("This is not a recognized SourceTX model backup.")
        }

        val hex = content.substring(MODEL_PREFIX.length).trim()
        if (hex.length < 24 || hex.length > 131094 || (hex.length % 2) != 0) {
            return ParseResult.Error("The backup has an invalid size and may be damaged.")
        }

        for (ch in hex) {
            val validHex = (ch in '0'..'9') || (ch in 'A'..'F') || (ch in 'a'..'f')
            if (!validHex) {
                return ParseResult.Error("The backup contains invalid data and may be damaged.")
            }
        }

        val bytes = ByteArray(hex.length / 2)
        for (i in bytes.indices) {
            bytes[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }

        if (bytes.size < 12) {
            return ParseResult.Error("The backup is incomplete.")
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = buffer.int.toLong() and 0xFFFFFFFFL
        val schema = buffer.short.toInt() and 0xFFFF
        val payloadSize = buffer.short.toInt() and 0xFFFF

        if (magic != TRANSFER_MAGIC) {
            return ParseResult.Error("This is not a compatible SourceTX model backup.")
        }

        if (bytes.size != 12 + payloadSize) {
            return ParseResult.Error("The backup is incomplete or damaged.")
        }

        if (payloadSize == 0) {
            return ParseResult.Error("The backup contains no model data and cannot be restored.")
        }

        if (expectedSchema > 0 && schema != expectedSchema) {
            return ParseResult.Error("The backup was created by an incompatible SourceTX firmware version ($schema vs expected $expectedSchema).")
        }

        if (expectedPayloadSize > 0 && payloadSize != expectedPayloadSize) {
            return ParseResult.Error("The backup does not match the connected transmitter firmware payload size ($payloadSize vs $expectedPayloadSize).")
        }

        val payload = ByteArray(payloadSize)
        System.arraycopy(bytes, 8, payload, 0, payloadSize)

        val storedChecksum = ByteBuffer.wrap(bytes, bytes.size - 4, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL

        val calculatedChecksum = calculateFnv1a(payload, magic, schema, payloadSize)
        if (storedChecksum != calculatedChecksum) {
            return ParseResult.Error("The backup failed its integrity check (Checksum mismatch: 0x${storedChecksum.toString(16)} vs 0x${calculatedChecksum.toString(16)}).")
        }

        val rawNameBytes = ByteArray(min(16, payload.size))
        System.arraycopy(payload, 0, rawNameBytes, 0, rawNameBytes.size)
        val rawName = String(rawNameBytes, Charsets.US_ASCII).trim { it == '\u0000' || it.isWhitespace() }
        val modelName = if (rawName.isBlank()) "Unnamed Model" else rawName

        return ParseResult.Success(
            SourceTxModelEnvelope(
                text = "$MODEL_PREFIX${hex.uppercase(Locale.ROOT)}",
                hex = hex.uppercase(Locale.ROOT),
                magic = magic,
                schema = schema,
                payloadSize = payloadSize,
                payload = payload,
                checksum = storedChecksum,
                modelName = modelName
            )
        )
    }

    /**
     * Creates a complete SourceTX model bundle from a list of slots and envelopes.
     */
    fun createBundle(
        models: Map<Int, SourceTxModelEnvelope>,
        activeModel: Int,
        protocolVersion: Int
    ): ParseResult<SourceTxModelBundle> {
        if (models.isEmpty()) {
            return ParseResult.Error("No models were provided for backup.")
        }

        val entries = models.entries.sortedBy { it.key }.map {
            SourceTxModelBundleEntry(
                slot = it.key,
                name = it.value.modelName,
                envelope = it.value.text
            )
        }

        val firstEnvelope = models.values.first()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        val bundle = SourceTxModelBundle(
            format = BUNDLE_FORMAT,
            version = BUNDLE_VERSION,
            protocol = protocolVersion,
            schema = firstEnvelope.schema,
            payloadSize = firstEnvelope.payloadSize,
            modelCount = entries.size,
            activeModel = activeModel,
            createdUtc = dateFormat.format(Date()),
            models = entries
        )
        bundle.checksumSha256 = calculateBundleChecksum(bundle)
        return ParseResult.Success(bundle)
    }

    /**
     * Calculates SHA-256 digest over normalized slot entries in the bundle.
     */
    fun calculateBundleChecksum(bundle: SourceTxModelBundle): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val builder = StringBuilder()
        builder.append("${bundle.format}|${bundle.version}|${bundle.schema}|${bundle.payloadSize}|${bundle.modelCount}\n")
        for (entry in bundle.models.sortedBy { it.slot }) {
            builder.append("${entry.slot}:${entry.name}:${entry.envelope.trim()}\n")
        }
        val hashBytes = digest.digest(builder.toString().toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Serializes a bundle to a JSON string.
     */
    fun serializeBundle(bundle: SourceTxModelBundle): String {
        val root = JSONObject()
        root.put("format", bundle.format)
        root.put("version", bundle.version)
        root.put("protocol", bundle.protocol)
        root.put("schema", bundle.schema)
        root.put("payload_size", bundle.payloadSize)
        root.put("model_count", bundle.modelCount)
        root.put("active_model", bundle.activeModel)
        root.put("created_utc", bundle.createdUtc)
        root.put("checksum_sha256", bundle.checksumSha256)

        val modelsArray = JSONArray()
        for (entry in bundle.models) {
            val entryObj = JSONObject()
            entryObj.put("slot", entry.slot)
            entryObj.put("name", entry.name)
            entryObj.put("envelope", entry.envelope)
            modelsArray.put(entryObj)
        }
        root.put("models", modelsArray)
        return root.toString(2)
    }

    /**
     * Deserializes and validates a JSON model bundle.
     */
    fun parseBundle(json: String): ParseResult<Pair<SourceTxModelBundle, List<SourceTxModelEnvelope>>> {
        if (json.isBlank()) {
            return ParseResult.Error("The bundle file is empty.")
        }

        try {
            val root = JSONObject(json)
            val format = root.optString("format", "")
            val version = root.optInt("version", 0)
            val protocol = root.optInt("protocol", 0)
            val schema = root.optInt("schema", 0)
            val payloadSize = root.optInt("payload_size", 0)
            val modelCount = root.optInt("model_count", 0)
            val activeModel = root.optInt("active_model", 1)
            val createdUtc = root.optString("created_utc", "")
            val checksumSha256 = root.optString("checksum_sha256", "")
            val modelsArray = root.optJSONArray("models")

            if (format != BUNDLE_FORMAT || version != BUNDLE_VERSION || modelsArray == null) {
                return ParseResult.Error("This is not a supported SourceTX model bundle.")
            }

            if (modelCount < 1 || modelCount > MAXIMUM_MODELS || modelsArray.length() != modelCount) {
                return ParseResult.Error("The bundle model count is invalid or does not match contents.")
            }

            val entries = mutableListOf<SourceTxModelBundleEntry>()
            val envelopes = mutableListOf<SourceTxModelEnvelope>()
            val seenSlots = mutableSetOf<Int>()

            for (i in 0 until modelsArray.length()) {
                val item = modelsArray.getJSONObject(i)
                val slot = item.getInt("slot")
                val name = item.getString("name")
                val envelopeText = item.getString("envelope")

                if (slot < 1 || slot > MAXIMUM_MODELS || !seenSlots.add(slot)) {
                    return ParseResult.Error("The bundle contains duplicate or out-of-range model slots.")
                }

                when (val envResult = parseEnvelope(envelopeText, schema, payloadSize)) {
                    is ParseResult.Error -> return ParseResult.Error("Bundle slot $slot error: ${envResult.message}")
                    is ParseResult.Success -> {
                        entries.add(SourceTxModelBundleEntry(slot, name, envelopeText))
                        envelopes.add(envResult.data)
                    }
                }
            }

            val bundle = SourceTxModelBundle(
                format = format,
                version = version,
                protocol = protocol,
                schema = schema,
                payloadSize = payloadSize,
                modelCount = modelCount,
                activeModel = activeModel,
                createdUtc = createdUtc,
                checksumSha256 = checksumSha256,
                models = entries
            )

            if (checksumSha256.isNotBlank()) {
                val calculated = calculateBundleChecksum(bundle)
                if (!calculated.equals(checksumSha256, ignoreCase = true)) {
                    return ParseResult.Error("The bundle failed SHA-256 integrity verification.")
                }
            }

            return ParseResult.Success(Pair(bundle, envelopes))
        } catch (ex: Exception) {
            return ParseResult.Error("Failed to parse bundle JSON: ${ex.localizedMessage}")
        }
    }
}
