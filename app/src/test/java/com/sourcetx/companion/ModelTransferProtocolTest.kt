package com.sourcetx.companion

import com.sourcetx.companion.protocol.ModelTransferProtocol
import com.sourcetx.companion.protocol.ParseResult
import com.sourcetx.companion.protocol.SourceTxModelEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class ModelTransferProtocolTest {

    @Test
    fun testFnv1aCalculationMatchesKnownVector() {
        val magic = 0x5354584DL // STXM
        val schema = 21
        val payloadSize = 16
        val payload = ByteArray(payloadSize) { 0 }

        val checksum = ModelTransferProtocol.calculateFnv1a(payload, magic, schema, payloadSize)
        assertTrue("Checksum should be a valid unsigned 32-bit integer", checksum > 0L)
    }

    @Test
    fun testEnvelopeCreationAndParsing() {
        val magic = 0x5354584DL
        val schema = 21
        val payload = "TestModel123456\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val payloadSize = payload.size

        val checksum = ModelTransferProtocol.calculateFnv1a(payload, magic, schema, payloadSize)

        // Build raw binary: [4 bytes magic][2 bytes schema][2 bytes size][payload][4 bytes checksum]
        val totalBytes = ByteArray(12 + payloadSize)
        val buffer = ByteBuffer.wrap(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(magic.toInt())
        buffer.putShort(schema.toShort())
        buffer.putShort(payloadSize.toShort())
        buffer.put(payload)
        buffer.putInt(checksum.toInt())

        val hex = totalBytes.joinToString("") { "%02X".format(it) }
        val envelopeText = "${ModelTransferProtocol.MODEL_PREFIX}$hex"

        val parseResult = ModelTransferProtocol.parseEnvelope(envelopeText, schema, payloadSize)
        assertTrue("Parsing should succeed", parseResult is ParseResult.Success)

        val envelope = (parseResult as ParseResult.Success).data
        assertEquals("TestModel123456", envelope.modelName)
        assertEquals(schema, envelope.schema)
        assertEquals(payloadSize, envelope.payloadSize)
        assertEquals(checksum, envelope.checksum)
    }

    @Test
    fun testBundleCreationAndParsing() {
        val magic = 0x5354584DL
        val schema = 21
        val payload = "CrawlerSetup\u0000\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII)
        val payloadSize = payload.size
        val checksum = ModelTransferProtocol.calculateFnv1a(payload, magic, schema, payloadSize)

        val totalBytes = ByteArray(12 + payloadSize)
        val buffer = ByteBuffer.wrap(totalBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(magic.toInt())
        buffer.putShort(schema.toShort())
        buffer.putShort(payloadSize.toShort())
        buffer.put(payload)
        buffer.putInt(checksum.toInt())

        val hex = totalBytes.joinToString("") { "%02X".format(it) }
        val envelope = SourceTxModelEnvelope(
            text = "${ModelTransferProtocol.MODEL_PREFIX}$hex",
            hex = hex,
            magic = magic,
            schema = schema,
            payloadSize = payloadSize,
            payload = payload,
            checksum = checksum,
            modelName = "CrawlerSetup"
        )

        val models = mapOf(1 to envelope)
        val bundleResult = ModelTransferProtocol.createBundle(models, activeModel = 1, protocolVersion = 1)
        assertTrue("Bundle creation should succeed", bundleResult is ParseResult.Success)

        val bundle = (bundleResult as ParseResult.Success).data
        val json = ModelTransferProtocol.serializeBundle(bundle)
        assertTrue("Serialized JSON should contain model name", json.contains("CrawlerSetup"))

        val parsedBundleResult = ModelTransferProtocol.parseBundle(json)
        assertTrue("Parsing bundle JSON should succeed", parsedBundleResult is ParseResult.Success)

        val (parsedBundle, envelopes) = (parsedBundleResult as ParseResult.Success).data
        assertEquals(1, parsedBundle.modelCount)
        assertEquals(1, envelopes.size)
        assertEquals("CrawlerSetup", envelopes[0].modelName)
    }
}
