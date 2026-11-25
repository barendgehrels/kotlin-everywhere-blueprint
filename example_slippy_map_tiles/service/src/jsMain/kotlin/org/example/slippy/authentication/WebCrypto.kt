package org.example.slippy.authentication

import kotlinx.coroutines.await
import kotlin.js.Promise

// WebCrypto, only the two calls the token check needs.

external interface SigningKey

private external interface SubtleCrypto {
    fun importKey(
        format: String, keyData: dynamic, algorithm: dynamic, extractable: Boolean, keyUsages: Array<String>,
    ): Promise<SigningKey>

    fun verify(algorithm: String, key: SigningKey, signature: ByteArray, data: ByteArray): Promise<Boolean>
}

private external interface Crypto {
    val subtle: SubtleCrypto
}

private external val crypto: Crypto

private const val RS256 = "RSASSA-PKCS1-v1_5"

/** Imports one of Google's JWKs (a plain JS object) as an RS256 verification key. */
suspend fun importRs256Key(jwk: dynamic): SigningKey {
    val algorithm: dynamic = js("({})")
    algorithm.name = RS256
    algorithm.hash = "SHA-256"
    return crypto.subtle.importKey("jwk", jwk, algorithm, false, arrayOf("verify")).await()
}

suspend fun verifyRs256(key: SigningKey, signature: ByteArray, data: ByteArray): Boolean =
    crypto.subtle.verify(RS256, key, signature, data).await()
