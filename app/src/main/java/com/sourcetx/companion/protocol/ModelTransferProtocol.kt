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
    const val PUBLIC_SCHEMA_VERSION = 21
    const val MODEL_PREFIX = "SOURCETX_MODEL:"
    const val BUNDLE_FORMAT = "SOURCETX_MODEL_BUNDLE"
    const val BUNDLE_VERSION = 1
    const val MAXIMUM_MODELS = 20

    fun calculateFnv1a(payload: ByteArray, magic: Long, version: Int, payloadSize: Int): Long {
        var hash = 2166136261L and 0xFFFFFFFFL
        for (byte in payload) {
            hash = (hash xor (byte.toInt() and 0xFF).toLong()) and 0xFFFFFFFFL
            hash = (hash * 16777619L) and 0xFFFFFFFFL
        }
        hash = (hash xor (magic and 0xFFFFFFFFL)) and 0xFFFFFFFFL
        hash = (hash * 16777619L) and 0xFFFFFFFFL
        val metadata = ((version and 0xFFFF).toLong() shl 16) or (payloadSize and 0xFFFF).toLong()
        return (hash xor metadata) and 0xFFFFFFFFL
    }

    fun parseEnvelope(
        text: String?,
        expectedSchema: Int = 0,
        expectedPayloadSize: Int = 0
    ): ParseResult<SourceTxModelEnvelope> {
        if (text.isNullOrBlank()) return ParseResult.Error("The model backup is empty.")
        val content = text.trim()
        if (!content.startsWith(MODEL_PREFIX, ignoreCase = true)) {
            return ParseResult.Error("This is not a recognized SourceTX model backup.")
        }
        val hex = content.substring(MODEL_PREFIX.length).trim()
        if (hex.length < 24 || hex.length > 131094 || hex.length % 2 != 0 ||
            hex.any { it.digitToIntOrNull(16) == null }) {
            return ParseResult.Error("The model backup contains invalid or damaged data.")
        }
        val bytes = try {
            ByteArray(hex.length / 2) { index ->
                hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        } catch (_: Exception) {
            return ParseResult.Error("The model backup contains invalid hexadecimal data.")
        }
        if (bytes.size < 12) return ParseResult.Error("The model backup is incomplete.")

        val header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = header.int.toLong() and 0xFFFFFFFFL
        val schema = header.short.toInt() and 0xFFFF
        val payloadSize = header.short.toInt() and 0xFFFF
        if (magic != TRANSFER_MAGIC) return ParseResult.Error("This is not a compatible SourceTX model backup.")
        if (payloadSize == 0 || bytes.size != 12 + payloadSize) {
            return ParseResult.Error("The model backup is incomplete or damaged.")
        }
        if (expectedSchema > 0 && schema != expectedSchema) {
            return ParseResult.Error("This backup uses model schema $schema; the transmitter requires schema $expectedSchema.")
        }
        if (expectedPayloadSize > 0 && payloadSize != expectedPayloadSize) {
            return ParseResult.Error("This backup has a $payloadSize-byte model; the transmitter requires $expectedPayloadSize bytes.")
        }

        val payload = bytes.copyOfRange(8, 8 + payloadSize)
        val storedChecksum = ByteBuffer.wrap(bytes, bytes.size - 4, 4)
            .order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
        val calculatedChecksum = calculateFnv1a(payload, magic, schema, payloadSize)
        if (storedChecksum != calculatedChecksum) {
            return ParseResult.Error("The model backup failed its integrity check and may be damaged or modified.")
        }
        val rawName = String(payload.copyOfRange(0, min(16, payload.size)), Charsets.US_ASCII)
            .trim { it == '\u0000' || it.isWhitespace() }
        val normalizedHex = hex.uppercase(Locale.ROOT)
        return ParseResult.Success(
            SourceTxModelEnvelope(
                text = "$MODEL_PREFIX$normalizedHex",
                hex = normalizedHex,
                magic = magic,
                schema = schema,
                payloadSize = payloadSize,
                payload = payload,
                checksum = storedChecksum,
                modelName = rawName.ifBlank { "Unnamed Model" }
            )
        )
    }

    fun createBundle(
        models: Map<Int, SourceTxModelEnvelope>,
        activeModel: Int,
        protocolVersion: Int
    ): ParseResult<SourceTxModelBundle> {
        if (models.isEmpty() || models.size > MAXIMUM_MODELS) {
            return ParseResult.Error("No valid models were provided for the complete backup.")
        }
        val expectedSlots = (1..models.size).toSet()
        if (models.keys != expectedSlots || activeModel !in expectedSlots || protocolVersion < 1) {
            return ParseResult.Error("The transmitter returned an incomplete or invalid model list.")
        }
        val first = models.getValue(1)
        if (models.values.any { it.schema != first.schema || it.payloadSize != first.payloadSize }) {
            return ParseResult.Error("The exported models do not use one compatible storage schema.")
        }
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val bundle = SourceTxModelBundle(
            format = BUNDLE_FORMAT,
            version = BUNDLE_VERSION,
            protocol = protocolVersion,
            schema = first.schema,
            payloadSize = first.payloadSize,
            modelCount = models.size,
            activeModel = activeModel,
            createdUtc = timestamp,
            models = models.entries.sortedBy { it.key }.map { (slot, envelope) ->
                SourceTxModelBundleEntry(slot, envelope.modelName, envelope.text)
            }
        )
        bundle.checksumSha256 = calculateBundleChecksum(bundle)
        return ParseResult.Success(bundle)
    }

    /** Canonical form intentionally matches the Windows Companion byte-for-byte. */
    fun calculateBundleChecksum(bundle: SourceTxModelBundle): String {
        val canonical = buildString {
            append(bundle.format).append('|')
            append(bundle.version).append('|')
            append(bundle.protocol).append('|')
            append(bundle.schema).append('|')
            append(bundle.payloadSize).append('|')
            append(bundle.modelCount).append('|')
            append(bundle.activeModel).append('|')
            bundle.models.sortedBy { it.slot }.forEach { entry ->
                append(entry.slot).append(':')
                append(entry.envelope.trim().uppercase(Locale.ROOT)).append('|')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    /** Writes the public Windows-compatible PascalCase .stxb schema. */
    fun serializeBundle(bundle: SourceTxModelBundle): String = JSONObject().apply {
        put("Format", bundle.format)
        put("Version", bundle.version)
        put("Protocol", bundle.protocol)
        put("Schema", bundle.schema)
        put("PayloadSize", bundle.payloadSize)
        put("ModelCount", bundle.modelCount)
        put("ActiveModel", bundle.activeModel)
        put("CreatedUtc", bundle.createdUtc)
        put("ChecksumSha256", bundle.checksumSha256)
        put("Models", JSONArray().apply {
            bundle.models.sortedBy { it.slot }.forEach { entry ->
                put(JSONObject().apply {
                    put("Slot", entry.slot)
                    put("Name", entry.name)
                    put("Envelope", entry.envelope)
                })
            }
        })
    }.toString()

    fun parseBundle(json: String): ParseResult<Pair<SourceTxModelBundle, List<SourceTxModelEnvelope>>> {
        if (json.isBlank()) return ParseResult.Error("The complete backup is empty.")
        return try {
            val root = JSONObject(json)
            fun string(vararg names: String): String = names.firstNotNullOfOrNull { name ->
                if (root.has(name) && !root.isNull(name)) root.getString(name) else null
            }.orEmpty()
            fun integer(vararg names: String): Int = names.firstNotNullOfOrNull { name ->
                if (root.has(name) && !root.isNull(name)) root.optInt(name) else null
            } ?: 0
            fun array(vararg names: String): JSONArray? = names.firstNotNullOfOrNull { root.optJSONArray(it) }

            val format = string("Format", "format")
            val version = integer("Version", "version")
            val protocol = integer("Protocol", "protocol")
            val schema = integer("Schema", "schema")
            val payloadSize = integer("PayloadSize", "payload_size")
            val modelCount = integer("ModelCount", "model_count")
            val activeModel = integer("ActiveModel", "active_model")
            val createdUtc = string("CreatedUtc", "created_utc")
            val checksum = string("ChecksumSha256", "checksum_sha256")
            val modelsArray = array("Models", "models")
                ?: return ParseResult.Error("This is not a supported SourceTX complete backup.")

            if (format != BUNDLE_FORMAT || version != BUNDLE_VERSION || protocol < 1 ||
                schema < 1 || payloadSize < 1 || modelCount !in 1..MAXIMUM_MODELS ||
                activeModel !in 1..modelCount || modelsArray.length() != modelCount ||
                !Regex("^[0-9a-fA-F]{64}$").matches(checksum)) {
                return ParseResult.Error("The complete backup contains invalid model information.")
            }

            val entries = mutableListOf<SourceTxModelBundleEntry>()
            val envelopes = mutableListOf<SourceTxModelEnvelope>()
            val seenSlots = mutableSetOf<Int>()
            for (index in 0 until modelsArray.length()) {
                val item = modelsArray.optJSONObject(index)
                    ?: return ParseResult.Error("The complete backup contains a damaged model entry.")
                fun entryString(vararg names: String): String = names.firstNotNullOfOrNull { name ->
                    if (item.has(name) && !item.isNull(name)) item.getString(name) else null
                }.orEmpty()
                fun entryInt(vararg names: String): Int = names.firstNotNullOfOrNull { name ->
                    if (item.has(name) && !item.isNull(name)) item.optInt(name) else null
                } ?: 0
                val slot = entryInt("Slot", "slot")
                val envelopeText = entryString("Envelope", "envelope")
                if (slot !in 1..modelCount || !seenSlots.add(slot)) {
                    return ParseResult.Error("The complete backup contains repeated or out-of-range model slots.")
                }
                when (val parsed = parseEnvelope(envelopeText, schema, payloadSize)) {
                    is ParseResult.Error -> return ParseResult.Error("Slot $slot: ${parsed.message}")
                    is ParseResult.Success -> {
                        val suppliedName = entryString("Name", "name")
                        entries += SourceTxModelBundleEntry(slot, suppliedName.ifBlank { parsed.data.modelName }, parsed.data.text)
                        envelopes += parsed.data
                    }
                }
            }
            if (seenSlots != (1..modelCount).toSet()) {
                return ParseResult.Error("The complete backup is missing one or more model slots.")
            }
            val orderedEntries = entries.sortedBy { it.slot }
            val orderedEnvelopes = entries.zip(envelopes).sortedBy { it.first.slot }.map { it.second }
            val bundle = SourceTxModelBundle(
                format, version, protocol, schema, payloadSize, modelCount,
                activeModel, createdUtc, checksum, orderedEntries
            )
            if (!MessageDigest.isEqual(
                    checksum.lowercase(Locale.ROOT).toByteArray(Charsets.US_ASCII),
                    calculateBundleChecksum(bundle).toByteArray(Charsets.US_ASCII)
                )) {
                return ParseResult.Error("The complete backup failed its SHA-256 integrity check.")
            }
            ParseResult.Success(bundle to orderedEnvelopes)
        } catch (_: Exception) {
            ParseResult.Error("This complete backup is damaged or is not valid SourceTX data.")
        }
    }
}
