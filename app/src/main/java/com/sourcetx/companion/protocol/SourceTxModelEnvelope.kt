package com.sourcetx.companion.protocol

/**
 * Represents a validated SOURCETX_MODEL envelope containing ASCII hex and decoded binary payload.
 */
data class SourceTxModelEnvelope(
    val text: String,
    val hex: String,
    val magic: Long,
    val schema: Int,
    val payloadSize: Int,
    val payload: ByteArray,
    val checksum: Long,
    val modelName: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SourceTxModelEnvelope

        if (text != other.text) return false
        if (magic != other.magic) return false
        if (schema != other.schema) return false
        if (payloadSize != other.payloadSize) return false
        if (!payload.contentEquals(other.payload)) return false
        if (checksum != other.checksum) return false
        if (modelName != other.modelName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + magic.hashCode()
        result = 31 * result + schema
        result = 31 * result + payloadSize
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + checksum.hashCode()
        result = 31 * result + modelName.hashCode()
        return result
    }
}
