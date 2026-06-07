package app.univera.telegramlogin

import app.univera.telegramlogin.internal.Pkce
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PkceTest {

    /** RFC 7636, Appendix B — known verifier → challenge vector (validates SHA-256 + base64url). */
    @Test
    fun challenge_matchesRfc7636Vector() {
        val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", Pkce.challengeFor(verifier))
    }

    @Test
    fun verifier_isUrlSafeAndUnpadded() {
        val verifier = Pkce.createVerifier()
        assertTrue(verifier.isNotEmpty())
        assertTrue(verifier.none { it == '+' || it == '/' || it == '=' }, "verifier must be base64url, unpadded")
    }

    @Test
    fun verifier_isRandomPerCall() {
        assertTrue(Pkce.createVerifier() != Pkce.createVerifier())
    }
}
