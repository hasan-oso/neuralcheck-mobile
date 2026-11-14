package net.neuralcheck.presentation.viewmodel

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.R
import net.neuralcheck.data.remote.SessionStatus
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel.Phase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The owner's listener reads the five statuses the server writes — batch 2, item 3 (user decision 2026-10-15: «ومستمع صاحب المحلّ يقرأ الحالات الخمس التي
 * يكتبها الخادم»; the five and the fact that nobody writes `waiting`, `joined`, `in_progress` or `done` are the backend diagnosis at `bbb90a1`, answer 9, relayed,
 * not verified from this repository). Rules 22 and 25: what is not one of the five is a NAMED state that reaches the log and moves nothing; it is never read as one of them.
 *
 * Rule 23: no test pinned the old listener (`git grep -n "in_progress\|joined\|\"done\"" -- app/src/test` was empty before this change), so none is replaced; the
 * eight values it used to read are the cases below that must now do nothing.
 *
 * Real: [GenerateSessionViewModel], `SessionDocWatcher`, [net.neuralcheck.data.remote.FirebaseAnalysisService]'s `createScanSession`, the status reading. Replaced: the HTTP
 * round trip, the auth object and the Firestore listener (the harness; the snapshot is a mock, everything that reads it is real).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OwnerSessionListenerTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context = mock<Context>().also {
        whenever(it.getString(R.string.error_generic)).thenReturn("generic-text")
    }

    private inline fun withOwner(body: (RealJoinPathHarness, GenerateSessionViewModel) -> Unit) {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            val vm = GenerateSessionViewModel(context, h.service, h.canceller)
            assertEquals("the session was created and the owner is waiting", Phase.WAITING, vm.state.value.phase)
            body(h, vm)
        }
    }

    @Test
    fun `each of the five statuses the server writes shows its own phase`() {
        val expected = mapOf(
            "pending" to Phase.WAITING,
            "scanning" to Phase.SCANNING,
            "completed" to Phase.COMPLETED,
            "expired" to Phase.EXPIRED,
            "cancelled" to Phase.CANCELLED
        )
        assertEquals("exactly the five", SessionStatus.values().map { it.wire }.toSet(), expected.keys)
        for ((wire, phase) in expected) {
            withOwner { h, vm ->
                h.deliverSnapshot("sess-1", fields = mapOf("status" to wire))
                assertEquals(wire, phase, vm.state.value.phase)
            }
        }
    }

    @Test
    fun `ownerPhaseFor covers every status and gives each its own phase`() {
        val phases = SessionStatus.values().map { ownerPhaseFor(it) }
        assertEquals("five statuses, five different phases", 5, phases.toSet().size)
        assertTrue("none of them is the creating or the error phase", phases.none { it == Phase.CREATING || it == Phase.ERROR })
    }

    @Test
    fun `the four values nobody writes - waiting, joined, in_progress, done - move nothing and are named in the log`() {
        for (notWritten in listOf("waiting", "joined", "in_progress", "done")) {
            withOwner { h, vm ->
                h.deliverSnapshot("sess-1", fields = mapOf("status" to notWritten))
                assertEquals("$notWritten is not read as a phase", Phase.WAITING, vm.state.value.phase)
                assertTrue(
                    "$notWritten is named",
                    h.logLines.any { it.startsWith("session-watch-event ") && it.contains(" doc=present status=unknown statusRaw=$notWritten ") }
                )
            }
        }
    }

    @Test
    fun `a status is matched exactly - no lower-casing - so Scanning and PENDING are unknown, not scanning and pending`() {
        for (variant in listOf("Scanning", "PENDING", "Completed", " scanning")) {
            withOwner { h, vm ->
                h.deliverSnapshot("sess-1", fields = mapOf("status" to "scanning"))
                assertEquals(Phase.SCANNING, vm.state.value.phase)
                h.deliverSnapshot("sess-1", fields = mapOf("status" to variant))
                assertEquals("'$variant' moves nothing: the phase stays what the last known status made it", Phase.SCANNING, vm.state.value.phase)
                assertTrue(h.logLines.any { it.contains(" status=unknown statusRaw=${net.neuralcheck.util.EvalCardLog.token(variant)} ") })
            }
        }
    }

    @Test
    fun `a status that is missing, null or not a text, and a missing document, move nothing and are named`() {
        withOwner { h, vm ->
            h.deliverSnapshot("sess-1", fields = emptyMap())
            h.deliverSnapshot("sess-1", fields = mapOf("status" to null))
            h.deliverSnapshot("sess-1", fields = mapOf("status" to 7))
            h.deliverSnapshot("sess-1", exists = false)
            assertEquals(Phase.WAITING, vm.state.value.phase)
            val events = h.logLines.filter { it.startsWith("session-watch-event ") }
            assertEquals(4, events.size)
            assertEquals(3, events.count { it.contains(" doc=present status=absent ") })
            assertEquals(1, events.count { it.contains(" doc=missing status=absent ") })
        }
    }

    @Test
    fun `the phases are exactly these - there is no JOINED phase, because no status could produce it`() {
        assertEquals(
            listOf("CREATING", "WAITING", "SCANNING", "COMPLETED", "EXPIRED", "CANCELLED", "ERROR"),
            Phase.values().map { it.name }
        )
    }

    @Test
    fun `completion remembers the scan the server wrote, and a later snapshot without it keeps it`() {
        withOwner { h, vm ->
            h.deliverSnapshot("sess-1", fields = mapOf("status" to "completed", "scanId" to "scan-9"))
            assertEquals(Phase.COMPLETED, vm.state.value.phase)
            assertEquals("scan-9", vm.state.value.completedScanId)
            h.deliverSnapshot("sess-1", fields = mapOf("status" to "completed"))
            assertEquals("scan-9", vm.state.value.completedScanId)
        }
    }

    @Test
    fun `the listener is on the session document, a new code replaces it, and clearing the view model removes it`() {
        withOwner { h, vm ->
            assertEquals(setOf("sess-1"), h.snapshotListeners.keys.toSet())
            h.createResponse = hashMapOf<String, Any?>("sessionId" to "sess-2", "code" to "NC-5678", "expiresInMinutes" to 60)
            vm.create()
            assertEquals("the first listener was removed when the new code replaced it", listOf("sess-1"), h.removedListenerIds.toList())
            assertEquals(setOf("sess-1", "sess-2"), h.snapshotListeners.keys.toSet())
            // a late snapshot of the first session reaches nobody
            h.deliverSnapshot("sess-1", fields = mapOf("status" to "completed"))
            assertEquals(Phase.WAITING, vm.state.value.phase)
            val onCleared = GenerateSessionViewModel::class.java.getDeclaredMethod("onCleared").also { it.isAccessible = true }
            onCleared.invoke(vm)
            assertEquals(listOf("sess-1", "sess-2"), h.removedListenerIds.toList())
        }
    }

    @Test
    fun `a read error ends in the error phase with the existing generic text, and the log names the code`() {
        withOwner { h, vm ->
            h.deliverSnapshotError(
                "sess-1",
                FirebaseFirestoreException("Missing or insufficient permissions.", FirebaseFirestoreException.Code.PERMISSION_DENIED)
            )
            assertEquals(Phase.ERROR, vm.state.value.phase)
            assertEquals("generic-text", vm.state.value.error)
            assertTrue(h.logLines.any { it.startsWith("session-watch-error session=#") && it.endsWith(" code=permission-denied") })
        }
    }

    @Test
    fun `the owner screen after the listener keeps the session id it was created with`() {
        withOwner { h, vm ->
            h.deliverSnapshot("sess-1", fields = mapOf("status" to "scanning"))
            assertEquals("sess-1", vm.state.value.sessionId)
            assertNull(vm.state.value.error)
        }
    }
}
