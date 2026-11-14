package net.neuralcheck.data.remote

import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.presentation.viewmodel.RealJoinPathHarness
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [CustomerSessionWatch] — the customer device follows the session its scan started in and learns that the owner cancelled (batch 2, item 2, decision D5).
 * The question the order asked: when does the listener start, when is it removed, what happens if the network drops, and that it does not keep the service alive.
 *
 * Real: [CustomerSessionWatch], [SessionDocWatcher], [ScanSessionRecords] (in-memory preferences), [ScanSessionHolder], and the real join that fills the holder.
 * Replaced: the Firestore network (the harness's static `FirebaseFirestore.getInstance()` mock: a registered listener is kept and the test delivers a snapshot to it
 * the way the SDK does; the snapshot object is a mock, everything that reads it is real), the HTTP round trip and the auth object.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CustomerSessionWatchTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun join(h: RealJoinPathHarness, id: String) {
        h.joinResponse = hashMapOf<String, Any?>("sessionId" to id, "ownerId" to "owner-1", "status" to "scanning", "evaluationEnabled" to true)
        h.joinViewModel().join("NC-1234")
    }

    private class Rig(val h: RealJoinPathHarness) {
        val cancelledCalls = mutableListOf<CancelReason.Read>()
        val watch = CustomerSessionWatch(SessionDocWatcher(), h.holder, h.records) { cancelledCalls.add(it) }
    }

    private inline fun withRig(body: (Rig) -> Unit) = RealJoinPathHarness().use { body(Rig(it)) }

    @Test
    fun `it starts for a scan that started in a session, on that session's document - not before, and not for a personal scan`() = withRig { r ->
        assertFalse("nothing is read before the scan starts", r.h.snapshotListeners.containsKey("sess-1"))
        assertFalse("a scan with no recorded session is a personal scan: nothing starts", r.watch.start("scan-personal"))
        assertTrue(r.h.snapshotListeners.isEmpty())

        join(r.h, "sess-1")
        r.h.startScan("scan-1")
        assertTrue("the join alone starts no read", r.h.snapshotListeners.isEmpty())
        assertTrue(r.watch.start("scan-1"))
        assertTrue(r.watch.isWatching())
        assertEquals(setOf("sess-1"), r.h.snapshotListeners.keys.toSet())
        assertTrue(r.h.logLines.any { it.startsWith("session-watch-start scan=#") })
        assertFalse("a second start for the same watch does nothing", r.watch.start("scan-1"))
    }

    @Test
    fun `it reads the session the scan STARTED in, not the one the holder holds later`() = withRig { r ->
        join(r.h, "sess-A")
        r.h.startScan("scan-1")
        join(r.h, "sess-B") // joined after the scan started
        assertTrue(r.watch.start("scan-1"))
        assertEquals(setOf("sess-A"), r.h.snapshotListeners.keys.toSet())
    }

    @Test
    fun `a snapshot that is not a cancellation changes nothing - every status, an unknown one and a missing document are named in the log`() = withRig { r ->
        join(r.h, "sess-1")
        r.h.startScan("scan-1")
        r.watch.start("scan-1")
        for (status in listOf("pending", "scanning", "completed", "expired")) {
            r.h.deliverSnapshot("sess-1", fields = mapOf("status" to status))
        }
        r.h.deliverSnapshot("sess-1", fields = mapOf("status" to "joined")) // a value nobody writes: unknown, not any of the five
        r.h.deliverSnapshot("sess-1", fields = mapOf("status" to 5))        // not a text: absent
        r.h.deliverSnapshot("sess-1", exists = false)                       // the document is gone
        assertTrue(r.cancelledCalls.isEmpty())
        assertTrue(r.watch.isWatching())
        assertEquals("sess-1", r.h.holder.get())
        val events = r.h.logLines.filter { it.startsWith("session-watch-event ") }
        assertEquals(7, events.size)
        assertTrue(events.any { it.contains(" doc=present status=unknown statusRaw=joined ") })
        assertTrue(events.any { it.contains(" doc=present status=absent ") })
        assertTrue(events.any { it.contains(" doc=missing status=absent ") })
        assertTrue(events.any { it.contains(" status=scanning ") })
    }

    @Test
    fun `a cancelled document ends the watch, empties the holder of that session, removes the scan's record and tells the caller once`() = withRig { r ->
        join(r.h, "sess-1")
        r.h.startScan("scan-1")
        r.watch.start("scan-1")
        r.h.deliverSnapshot("sess-1", fields = mapOf("status" to "cancelled", "cancelReason" to "cancelled_by_owner"))
        assertEquals(listOf<CancelReason.Read>(CancelReason.Read.Known(CancelReason.CANCELLED_BY_OWNER)), r.cancelledCalls)
        assertFalse(r.watch.isWatching())
        assertEquals("the read was removed", listOf("sess-1"), r.h.removedListenerIds.toList())
        assertNull("the holder held that session: emptied", r.h.holder.getInfo())
        assertNull("the scan will not be analysed: its record is gone", r.h.records.sessionFor("scan-1", ScanSessionRecords.Use.ANALYSIS))
        assertTrue(r.h.logLines.any { it.startsWith("holder-clear reason=cancelled-by-owner hadInfo=true ") })
        // a late second snapshot of the removed listener reaches nobody
        r.h.deliverSnapshot("sess-1", fields = mapOf("status" to "cancelled", "cancelReason" to "cancelled_by_owner"))
        assertEquals(1, r.cancelledCalls.size)
    }

    @Test
    fun `a cancellation does not empty the holder of a session joined meanwhile`() = withRig { r ->
        join(r.h, "sess-A")
        r.h.startScan("scan-1")
        r.watch.start("scan-1")
        join(r.h, "sess-B")
        r.h.deliverSnapshot("sess-A", fields = mapOf("status" to "cancelled", "cancelReason" to "cancelled_by_owner"))
        assertEquals(1, r.cancelledCalls.size)
        assertEquals("the holder's session B is not touched", "sess-B", r.h.holder.get())
        assertTrue(r.h.logLines.any { it.startsWith("holder-clear-skipped reason=cancelled-by-owner ") })
    }

    @Test
    fun `a cancellation this device made itself is not the owner's - ignored, the leave flow ends the scan`() = withRig { r ->
        join(r.h, "sess-1")
        r.h.startScan("scan-1")
        r.watch.start("scan-1")
        for (own in listOf("left_by_customer", "consent_declined")) {
            r.h.deliverSnapshot("sess-1", fields = mapOf("status" to "cancelled", "cancelReason" to own))
        }
        assertTrue(r.cancelledCalls.isEmpty())
        assertTrue("still reading", r.watch.isWatching())
        assertEquals("sess-1", r.h.holder.get())
    }

    @Test
    fun `a cancelled document with an unknown or absent cancelReason is treated as cancelled - the session is cancelled whoever did it`() {
        for (fields in listOf(
            mapOf<String, Any?>("status" to "cancelled", "cancelReason" to "something_new"),
            mapOf<String, Any?>("status" to "cancelled")
        )) {
            withRig { r ->
                join(r.h, "sess-1")
                r.h.startScan("scan-1")
                r.watch.start("scan-1")
                r.h.deliverSnapshot("sess-1", fields = fields)
                assertEquals(1, r.cancelledCalls.size)
                assertFalse(r.watch.isWatching())
            }
        }
    }

    @Test
    fun `an error that ends the read is named, the watch is removed, and nothing else happens - the scan carries on`() = withRig { r ->
        join(r.h, "sess-1")
        r.h.startScan("scan-1")
        r.watch.start("scan-1")
        r.h.deliverSnapshotError("sess-1", FirebaseFirestoreException("Missing or insufficient permissions.", FirebaseFirestoreException.Code.PERMISSION_DENIED))
        assertTrue(r.cancelledCalls.isEmpty())
        assertFalse(r.watch.isWatching())
        assertEquals(listOf("sess-1"), r.h.removedListenerIds.toList())
        assertEquals("the session is untouched", "sess-1", r.h.holder.get())
        assertTrue(r.h.logLines.any { it.startsWith("session-watch-error session=#") && it.endsWith(" code=permission-denied") })
    }

    @Test
    fun `stop removes the read, twice is harmless, and nothing is delivered after it`() = withRig { r ->
        join(r.h, "sess-1")
        r.h.startScan("scan-1")
        r.watch.start("scan-1")
        r.watch.stop()
        r.watch.stop()
        assertFalse(r.watch.isWatching())
        assertEquals("removed once", listOf("sess-1"), r.h.removedListenerIds.toList())
        r.h.deliverSnapshot("sess-1", fields = mapOf("status" to "cancelled", "cancelReason" to "cancelled_by_owner"))
        assertTrue("a snapshot after stop reaches nobody", r.cancelledCalls.isEmpty())
        assertEquals("sess-1", r.h.holder.get())
    }
}
