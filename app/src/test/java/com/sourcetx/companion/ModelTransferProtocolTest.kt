package com.sourcetx.companion

import com.sourcetx.companion.protocol.ModelTransferProtocol
import com.sourcetx.companion.protocol.ParseResult
import com.sourcetx.companion.protocol.SourceTxModelBundle
import com.sourcetx.companion.protocol.SourceTxModelBundleEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTransferProtocolTest {
    private val goldenEnvelope =
        "SOURCETX_MODEL:4D585453150010000000000000000000000000000000000048C478F6"

    @Test
    fun fnvMatchesWindowsCompanionGoldenVector() {
        assertEquals(
            0xF678C448L,
            ModelTransferProtocol.calculateFnv1a(ByteArray(16), 0x5354584DL, 21, 16)
        )
    }

    @Test
    fun parsesKnownSourceTxEnvelope() {
        val result = ModelTransferProtocol.parseEnvelope(goldenEnvelope, 21, 16)
        assertTrue(result is ParseResult.Success)
        val envelope = (result as ParseResult.Success).data
        assertEquals(21, envelope.schema)
        assertEquals(16, envelope.payloadSize)
        assertEquals(0xF678C448L, envelope.checksum)
    }

    @Test
    fun bundleChecksumMatchesWindowsCompanionGoldenVector() {
        val bundle = SourceTxModelBundle(
            format = ModelTransferProtocol.BUNDLE_FORMAT,
            version = 1,
            protocol = 1,
            schema = 21,
            payloadSize = 16,
            modelCount = 1,
            activeModel = 1,
            createdUtc = "2026-08-16T00:00:00.000Z",
            models = listOf(SourceTxModelBundleEntry(1, "Unnamed Model", goldenEnvelope))
        )
        assertEquals(
            "e2380f6f135f1f5759a8775b9bc323be2b89eacee4a13e2a46fbabe89adacf05",
            ModelTransferProtocol.calculateBundleChecksum(bundle)
        )
    }

    @Test
    fun serializesAndParsesWindowsPascalCaseBundle() {
        val envelope = (ModelTransferProtocol.parseEnvelope(goldenEnvelope) as ParseResult.Success).data
        val created = ModelTransferProtocol.createBundle(mapOf(1 to envelope), 1, 1)
        assertTrue(created is ParseResult.Success)
        val json = ModelTransferProtocol.serializeBundle((created as ParseResult.Success).data)
        assertTrue(json.contains("\"PayloadSize\":16"))
        assertTrue(json.contains("\"ChecksumSha256\""))
        assertTrue(!json.contains("payload_size"))
        assertTrue(ModelTransferProtocol.parseBundle(json) is ParseResult.Success)
    }

    @Test
    fun rejectsBundleWithoutRequiredChecksum() {
        val json = """{
            "Format":"SOURCETX_MODEL_BUNDLE","Version":1,"Protocol":1,
            "Schema":21,"PayloadSize":16,"ModelCount":1,"ActiveModel":1,
            "CreatedUtc":"2026-08-16T00:00:00Z","Models":[
              {"Slot":1,"Name":"Model","Envelope":"$goldenEnvelope"}
            ]
        }""".trimIndent()
        assertTrue(ModelTransferProtocol.parseBundle(json) is ParseResult.Error)
    }

    @Test
    fun rejectsRepeatedBundleSlots() {
        val entries = listOf(
            SourceTxModelBundleEntry(1, "One", goldenEnvelope),
            SourceTxModelBundleEntry(1, "Duplicate", goldenEnvelope)
        )
        val bundle = SourceTxModelBundle(
            ModelTransferProtocol.BUNDLE_FORMAT, 1, 1, 21, 16, 2, 1,
            "2026-08-16T00:00:00Z", models = entries
        )
        bundle.checksumSha256 = ModelTransferProtocol.calculateBundleChecksum(bundle)
        assertTrue(ModelTransferProtocol.parseBundle(ModelTransferProtocol.serializeBundle(bundle)) is ParseResult.Error)
    }
}
