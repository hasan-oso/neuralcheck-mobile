package net.neuralcheck.data.remote

import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Date

/**
 * Order 2026-09-26 (القرار ١٠٢ البند ١، القاعدة 41). Proves
 * [extractCreatedAtMs] against the REAL shape `createdAt` arrives in on
 * the Android Firestore SDK's `DocumentSnapshot.getData()` map —
 * `com.google.firebase.Timestamp` — not the `{_seconds, _nanoseconds}` map
 * a Node-side Admin SDK serializer writes into a hand-authored JSON test
 * fixture (that shape is what `app/src/test/resources/contract/v6-reports-document-sample.json`
 * carries — a fixture, not what this function actually reads at runtime).
 *
 * A comprehensive multi-case (analyzedAt present / absent+createdAt /
 * both absent) golden-sample test of the FULL `fetchExistingReport` ->
 * `loadFromRemoteFallback` path is queued separately (القرار ١٠٢ البند ٤)
 * — this file only proves this one extraction function in isolation.
 */
class ExtractCreatedAtMsTest {

    @Test
    fun `a real Timestamp converts to the same epoch millis as its own toDate()`() {
        val ts = Timestamp(Date(1789884505000L))
        val data = mapOf("createdAt" to ts)
        assertEquals(1789884505000L, extractCreatedAtMs(data))
    }

    @Test
    fun `absent createdAt key returns null, not a fabricated 0`() {
        assertNull(extractCreatedAtMs(emptyMap()))
    }

    @Test
    fun `a non-Timestamp value (the Node-fixture's own shape) returns null, not a wrong guess`() {
        // The exact {_seconds, _nanoseconds} shape
        // v6-reports-document-sample.json:1490-1491 carries — proves this
        // function does NOT silently accept that shape as if it were a
        // Timestamp (it isn't one on the real Android read path).
        val nodeShapedMap = mapOf("_seconds" to 1789884505L, "_nanoseconds" to 0)
        assertNull(extractCreatedAtMs(mapOf("createdAt" to nodeShapedMap)))
    }

    @Test
    fun `a plain Number (analyzedAt's own shape) at the createdAt key is not misread as a Timestamp`() {
        assertNull(extractCreatedAtMs(mapOf("createdAt" to 1789884505000L)))
    }
}
