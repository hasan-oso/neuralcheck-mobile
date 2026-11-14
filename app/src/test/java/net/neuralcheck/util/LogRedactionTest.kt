package net.neuralcheck.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Order 2026-10-10 (user decision, permanent named log lines): [LogRedaction.hashId] is the app's ONE hashing
 * function and its format is the SERVER's (relayed from neuralcheck-backend `functions/src/utils/logRedaction.js:46-57`,
 * not read here): `sha256` over the UTF-8 bytes, the first 12 hex characters, prefixed `#`; absent or empty ->
 * `missing`; not a string -> `invalid-type`.
 *
 * The expected prefixes below were computed OUTSIDE the code under test, with Node's `crypto`
 * (`createHash('sha256').update(s, 'utf8').digest('hex').slice(0, 12)`), so the test does not agree with the
 * implementation by construction. `abc` is also the FIPS 180 test vector (`ba7816bf 8f01cfea …`).
 *
 * What it does not prove: that the server's rule matches this one on a value neither side tested (the whitespace-only
 * string, which this function hashes — see the assumption in [LogRedaction]'s doc).
 */
class LogRedactionTest {

    @Test
    fun `a fixed text hashes to its known sha256 prefix (the FIPS 180 vector for abc)`() {
        assertEquals("#ba7816bf8f01", LogRedaction.hashId("abc"))
    }

    @Test
    fun `the ids the real-path tests use have the prefixes Node computed`() {
        assertEquals("#abe633f3a47a", LogRedaction.hashId("sess-1"))
        assertEquals("#391887cbcf92", LogRedaction.hashId("owner-1"))
        assertEquals("#9bfcd2b216cf", LogRedaction.hashId("anon-uid-1"))
        assertEquals("#d6b64f1f202b", LogRedaction.hashId("scan-0001"))
        assertEquals("#86f27ee56a50", LogRedaction.hashId("NC-AB3F7K"))
    }

    @Test
    fun `the bytes hashed are UTF-8 (an Arabic text has the prefix Node computed)`() {
        assertEquals("#b96305d62caf", LogRedaction.hashId("جلسة"))
    }

    @Test
    fun `the shape is a hash sign and twelve lowercase hex characters, deterministic and distinct per input`() {
        val shape = Regex("^#[0-9a-f]{12}$")
        val inputs = listOf("a", "b", "sess-1", "a much longer identifier ".repeat(20), "0", " ")
        val hashes = inputs.map { LogRedaction.hashId(it) }
        hashes.forEach { assertTrue("$it is #<12 hex>", shape.matches(it)) }
        assertEquals("deterministic", hashes, inputs.map { LogRedaction.hashId(it) })
        assertEquals("distinct inputs gave distinct hashes here", hashes.size, hashes.toSet().size)
    }

    @Test
    fun `an absent or empty value is missing, not hashed`() {
        assertEquals("missing", LogRedaction.hashId(null))
        assertEquals("missing", LogRedaction.hashId(""))
    }

    @Test
    fun `a value that is not a string is invalid-type`() {
        assertEquals("invalid-type", LogRedaction.hashId(123))
        assertEquals("invalid-type", LogRedaction.hashId(1.5))
        assertEquals("invalid-type", LogRedaction.hashId(true))
        assertEquals("invalid-type", LogRedaction.hashId(listOf("sess-1")))
        assertEquals("invalid-type", LogRedaction.hashId(mapOf("sessionId" to "sess-1")))
        assertNotEquals("the text of a non-string is never hashed", LogRedaction.hashId("123"), LogRedaction.hashId(123))
    }
}
