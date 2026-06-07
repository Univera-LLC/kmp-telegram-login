package app.univera.telegramlogin.internal

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import okio.ByteString.Companion.toByteString

/** RFC 7636 PKCE helpers using the S256 challenge method. */
@OptIn(ExperimentalEncodingApi::class)
internal object Pkce {

    private val base64Url: Base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** A high-entropy `code_verifier` (base64url of 32 random bytes). */
    fun createVerifier(): String = base64Url.encode(secureRandomBytes(VERIFIER_BYTES))

    /** The `code_challenge` = BASE64URL(SHA-256(ASCII(verifier))). */
    fun challengeFor(verifier: String): String {
        val digest = verifier.encodeToByteArray().toByteString().sha256().toByteArray()
        return base64Url.encode(digest)
    }

    private const val VERIFIER_BYTES = 32
}
