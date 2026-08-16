package com.sourcetx.companion.firmware

import android.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object FirmwareSecurity {
    private const val PUBLIC_KEY_PEM = """
        -----BEGIN PUBLIC KEY-----
        MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEmeyz/UyEd597cKsYeiR6dl92YAAe
        mmH+O+ZY8Yz7NQKVRTYmS5DpJaNYdxnThRPEw2F2ie1yVvr7oXTaHJYrgw==
        -----END PUBLIC KEY-----
    """

    private val publicKey by lazy {
        val encoded = PUBLIC_KEY_PEM
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .filterNot(Char::isWhitespace)
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    fun verifyEcdsaSha256(content: ByteArray, derSignature: ByteArray): Boolean {
        if (derSignature.isEmpty() || derSignature.size > 256) return false
        return Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(content)
            verify(derSignature)
        }
    }

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun sha256Hex(data: ByteArray): String =
        sha256(data).joinToString("") { "%02x".format(it) }

    fun fixedTimeEqualsHex(expected: String, actual: String): Boolean {
        val expectedBytes = decodeHex(expected) ?: return false
        val actualBytes = decodeHex(actual) ?: return false
        return MessageDigest.isEqual(expectedBytes, actualBytes)
    }

    private fun decodeHex(value: String): ByteArray? {
        if (value.length != 64 || value.any { it.digitToIntOrNull(16) == null }) return null
        return ByteArray(value.length / 2) { index ->
            value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
