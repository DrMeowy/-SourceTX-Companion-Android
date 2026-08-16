package com.sourcetx.companion.viewmodel

import com.sourcetx.companion.protocol.SourceTxModelBundle
import com.sourcetx.companion.protocol.SourceTxModelEnvelope

data class PreparedModelBackup(
    val content: String,
    val fileName: String,
    val models: Map<Int, SourceTxModelEnvelope>,
    val isCompleteBundle: Boolean
)

sealed class LoadedModelBackup {
    abstract val fileName: String

    data class Single(
        override val fileName: String,
        val envelope: SourceTxModelEnvelope
    ) : LoadedModelBackup()

    data class Complete(
        override val fileName: String,
        val bundle: SourceTxModelBundle,
        val envelopes: List<SourceTxModelEnvelope>
    ) : LoadedModelBackup()
}
