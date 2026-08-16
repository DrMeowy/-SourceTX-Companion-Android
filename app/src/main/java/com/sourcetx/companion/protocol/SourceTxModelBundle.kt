package com.sourcetx.companion.protocol

/**
 * An individual model slot entry in a .stxb bundle.
 */
data class SourceTxModelBundleEntry(
    val slot: Int,
    val name: String,
    val envelope: String
)

/**
 * A complete multi-model backup bundle (.stxb).
 */
data class SourceTxModelBundle(
    val format: String,
    val version: Int,
    val protocol: Int,
    val schema: Int,
    val payloadSize: Int,
    val modelCount: Int,
    val activeModel: Int,
    val createdUtc: String,
    var checksumSha256: String = "",
    val models: List<SourceTxModelBundleEntry>
)
