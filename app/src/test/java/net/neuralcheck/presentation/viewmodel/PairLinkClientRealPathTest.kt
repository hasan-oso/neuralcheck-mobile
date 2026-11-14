package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.neuralcheck.data.remote.PairCallsNotApprovedException
import net.neuralcheck.data.remote.PairLinkClient
import net.neuralcheck.data.remote.PairLinkClient.ConfirmOutcome
import net.neuralcheck.data.remote.PairLinkClient.ListOutcome
import net.neuralcheck.domain.pair.OpenBeforesRead
import net.neuralcheck.domain.pair.PairRefusal
import net.neuralcheck.util.EvalCardLog
import net.neuralcheck.util.LogRedaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The maintenance link's two calls through the REAL path - order of 2026-10-22 (rules 30, 37, 22, 21, 36). Real: [PairLinkClient], `FirebaseAnalysisService.listOpenMaintenanceBefores` and
 * `confirmMaintenanceAfterLink` (the callable names, the request maps, the Gson tree of the answer), the readers, the log. Replaced: only the HTTP round trip and auth (the harness) and - declared - the callables'
 * exit gate, opened by [RealJoinPathHarness.openPairCalls] for every test but ONE (`the real gate is closed`, which runs the real, closed one).
 *
 * THE ANSWERS ARE WRITTEN FROM THE CONTRACT, NOT PRODUCED BY THE SERVER: the server's half (S4 and S5 of the same order) is not deployed and was not run from here. Each is spelled key by key as A3 (2) and (3)
 * of `app/src/test/resources/contract/protocol-contract-A2-A3-maintenance-pairs-neuralcheck-backend-c289a2d.md` spell them (a copy, rule 29). The refusal bodies' status and message are made up and vary on
 * purpose: the app reads `details.reasonCode` only (rule 21).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PairLinkClientRealPathTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun withClient(signedIn: Boolean = true, gateOpen: Boolean = true, check: RealJoinPathHarness.(PairLinkClient) -> Unit) {
        RealJoinPathHarness(signedInAtStart = signedIn, viewModelContext = context).use { h ->
            if (gateOpen) h.openPairCalls() else h.closePairCalls()
            h.check(PairLinkClient(h.service))
        }
    }

    private val mappedName = hashMapOf<String, Any?>(
        "state" to "mapped", "model" to "SM-G975F", "names" to hashMapOf<String, Any?>("ar" to "سامسونج جالاكسي S10+", "tr" to "Samsung Galaxy S10+", "en" to "Samsung Galaxy S10+"), "reason" to null
    )
    private val unmappedName = hashMapOf<String, Any?>("state" to "name_unmapped", "model" to "XYZ-1", "names" to null, "reason" to "MODEL_NOT_IN_MAP")

    private fun candidate(id: String, name: Any? = mappedName, at: Any? = 1_760_000_000_000L) =
        hashMapOf<String, Any?>("beforeScanId" to id, "deviceName" to name, "scannedAtMs" to at)

    /** A3 (2): `{validityHours, candidates}`. */
    private fun listAnswer(vararg candidates: Any?, hours: Any? = 72): HashMap<String, Any?> = hashMapOf("validityHours" to hours, "candidates" to candidates.toList())

    /** A3 (3): `{sessionId, beforeScanId, linkMethod: "manual"}`. */
    private fun confirmAnswer(session: String = "sess-1", before: String = "scan-1", method: String? = "manual"): HashMap<String, Any?> =
        hashMapOf("sessionId" to session, "beforeScanId" to before, "linkMethod" to method)

    private fun refusal(code: String, status: String = "FAILED_PRECONDITION") =
        SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody(status, "made-up message", code))

    private fun list(client: PairLinkClient) = runBlocking { client.listOpen("sess-1") }

    private fun confirm(client: PairLinkClient, before: String = "scan-1") = runBlocking { client.confirm("sess-1", before) }

    private fun listLine(outcome: String, detail: String, unknown: List<String> = emptyList()) = EvalCardLog.pairLinkLine("list", "sess-1", outcome, detail, unknown)

    private fun confirmLine(outcome: String, detail: String) = EvalCardLog.pairLinkLine("confirm", "sess-1", outcome, detail, emptyList())

    // ---- the list ----

    @Test
    fun `the list sends the session id and nothing else - and a candidate list that reads is the answer, with its line`() = withClient { client ->
        pairListResponse = listAnswer(candidate("a"), candidate("b", unmappedName, 1_760_000_100_000L))
        val outcome = list(client)
        assertEquals(1, callableCalls("listOpenMaintenanceBefores"))
        assertEquals("the contract's request: {sessionId} - no scan, no purpose, no type", listOf<Any?>(linkedMapOf("sessionId" to "sess-1")), pairListRequests.toList())
        val read = (outcome as ListOutcome.Listed).read
        assertEquals(72, read.validityHours)
        assertEquals(listOf("a", "b"), read.candidates.map { it.beforeScanId })
        assertTrue(logLines.toString(), listLine("listed", "n2_v72") in logLines)
    }

    @Test
    fun `no candidate is a read answer - the none-open state - and a JSON null survives the real pipeline as null, not as an absent key`() = withClient { client ->
        pairListResponse = listAnswer(hours = 24)
        assertEquals(OpenBeforesRead.Read(24, emptyList(), emptyList(), emptyList()), (list(client) as ListOutcome.Listed).read)
        assertTrue(logLines.toString(), listLine("listed", "n0_v24") in logLines)
        // the unmapped name's `names: null` and `reason: null` of a mapped one reach the reader as JSON nulls, kept by the one Gson that keeps nulls
        pairListResponse = listAnswer(candidate("a", unmappedName), candidate("b", mappedName))
        val read = (list(client) as ListOutcome.Listed).read
        assertEquals(2, read.candidates.size)
    }

    @Test
    fun `keys of the answer that are none of the contract's are printed in the line - including scannedAt, the order's name for the time`() = withClient { client ->
        pairListResponse = hashMapOf<String, Any?>(
            "validityHours" to 72, "zeta" to 1, "candidates" to listOf(hashMapOf<String, Any?>("beforeScanId" to "a", "deviceName" to mappedName, "scannedAtMs" to 5L, "scannedAt" to 5L))
        )
        val outcome = list(client) as ListOutcome.Listed
        assertEquals(listOf("zeta"), outcome.read.unknownKeys)
        assertEquals(listOf("scannedAt"), outcome.read.candidateUnknownKeys)
        assertTrue(logLines.toString(), listLine("listed", "n1_v72", listOf("zeta", "scannedAt")) in logLines)
    }

    @Test
    fun `an answer that does not read is an unreadable answer with its own line - never a list`() = withClient { client ->
        val cases = listOf<Pair<Any?, String>>(
            hashMapOf<String, Any?>() to "validity_hours_absent",
            null to "absent",
            "an answer that is a text" to "not_an_object_string",
            listAnswer(hours = "72") to "validity_hours_not_a_number_string",
            listAnswer(hours = 72.5) to "validity_hours_not_whole",
            hashMapOf<String, Any?>("validityHours" to 72) to "candidates_absent",
            listAnswer(hashMapOf<String, Any?>("beforeScanId" to "a", "deviceName" to mappedName, "scannedAt" to 5L)) to "candidate_0_scanned_at_ms_absent",
            listAnswer(candidate("a"), candidate("a")) to "duplicate_before_scan_id",
            listAnswer(candidate("a", at = "yesterday")) to "candidate_0_scanned_at_ms_not_a_number_string",
            listAnswer(candidate("a", name = hashMapOf<String, Any?>("state" to "renamed"))) to "candidate_0_device_name_state_unrecognized"
        )
        for ((answer, why) in cases) {
            pairListResponse = answer
            assertEquals("$answer", ListOutcome.UnreadableAnswer(why), list(client))
            assertTrue(logLines.toString(), listLine("unreadable_answer", why) in logLines)
        }
    }

    @Test
    fun `every refusal of the contract is read as itself, for any Functions status - the code is the text, not the exception - and the line carries it`() = withClient { client ->
        for (r in PairRefusal.values()) {
            for (status in listOf("FAILED_PRECONDITION", "PERMISSION_DENIED", "INVALID_ARGUMENT", "UNAUTHENTICATED", "NOT_FOUND")) {
                pairListFailure = refusal(r.wire, status)
                assertEquals("${r.wire} / $status", ListOutcome.Refused(r.wire, r), list(client))
            }
            assertTrue(logLines.toString(), listLine("refused", r.wire) in logLines)
        }
        // the session contract's own codes are refusals the server named (known there), none of the seventeen; and a code the app has no case for keeps its text - in the line
        for (shared in listOf("SESSION_NOT_PARTICIPANT", "SESSION_EXPIRED", "SESSION_EXPIRES_AT_INVALID", "SESSION_STATUS_UNKNOWN")) {
            pairListFailure = refusal(shared, "PERMISSION_DENIED")
            assertEquals(shared, ListOutcome.Refused(shared, null), list(client))
            assertTrue(logLines.toString(), listLine("refused", shared) in logLines)
        }
        pairListFailure = refusal("PAIR_BRAND_NEW")
        assertEquals(ListOutcome.Refused("PAIR_BRAND_NEW", null), list(client))
        assertTrue(logLines.toString(), listLine("refused", "PAIR_BRAND_NEW") in logLines)
    }

    @Test
    fun `a disconnect, a plain failure and a signed-out phone are not-sent - never a list, even when the text of the failure says a code`() {
        withClient { client ->
            for ((label, failure) in SessionWireFixtures.offlineFailures() + ("a plain exception" to RuntimeException("BEFORE_EXPIRED"))) {
                pairListFailure = failure
                assertEquals(label, ListOutcome.NotSent, list(client))
            }
            assertTrue(logLines.toString(), listLine("not_sent", "-") in logLines)
        }
        // signed out: the local gate of the service stops it before the SDK, and that is not a refusal of the server
        withClient(signedIn = false) { client ->
            assertEquals(ListOutcome.NotSent, list(client))
            assertEquals("nothing was sent", 0, callableCalls("listOpenMaintenanceBefores"))
        }
    }

    // ---- the confirmation ----

    @Test
    fun `the confirmation sends the session id and the scan id and nothing else - and the echo with linkMethod manual is a confirmation`() = withClient { client ->
        pairConfirmResponse = confirmAnswer()
        assertEquals(ConfirmOutcome.Confirmed, confirm(client))
        assertEquals(1, callableCalls("confirmMaintenanceAfterLink"))
        assertEquals(
            "the contract's request: {sessionId, beforeScanId} - no method, no type, no time, no name",
            listOf<Any?>(linkedMapOf("sessionId" to "sess-1", "beforeScanId" to "scan-1")),
            pairConfirmRequests.toList()
        )
        assertTrue(logLines.toString(), confirmLine("confirmed", "-") in logLines)
    }

    @Test
    fun `an answer that is not the confirmation of this request is an unreadable answer - another session, another scan, a method that is not manual`() = withClient { client ->
        val cases = listOf<Pair<Any?, String>>(
            confirmAnswer(session = "sess-2") to "session_id_differs",
            confirmAnswer(before = "scan-2") to "before_scan_id_differs",
            confirmAnswer(method = "auto_confirmed") to "link_method_not_manual",
            confirmAnswer(method = null) to "link_method_absent",
            hashMapOf<String, Any?>() to "session_id_missing",
            null to "absent",
            "ok" to "not_an_object_string"
        )
        for ((answer, why) in cases) {
            pairConfirmResponse = answer
            assertEquals("$answer", ConfirmOutcome.UnreadableAnswer(why), confirm(client))
            assertTrue(logLines.toString(), confirmLine("unreadable_answer", why) in logLines)
        }
    }

    @Test
    fun `every refusal of the confirmation is read as itself - the six that name a scan and the ten of the call - and an unnamed one keeps its text`() = withClient { client ->
        for (r in PairRefusal.values()) {
            pairConfirmFailure = refusal(r.wire)
            assertEquals(r.wire, ConfirmOutcome.Refused(r.wire, r), confirm(client))
            assertTrue(logLines.toString(), confirmLine("refused", r.wire) in logLines)
        }
        pairConfirmFailure = refusal("BEFORE_BRAND_NEW")
        assertEquals(ConfirmOutcome.Refused("BEFORE_BRAND_NEW", null), confirm(client))
        pairConfirmFailure = refusal("SESSION_EXPIRED", "FAILED_PRECONDITION")
        assertEquals(ConfirmOutcome.Refused("SESSION_EXPIRED", null), confirm(client))
    }

    @Test
    fun `a disconnect and a plain failure on the confirmation are not-sent`() = withClient { client ->
        for ((label, failure) in SessionWireFixtures.offlineFailures() + ("a plain exception" to RuntimeException("BEFORE_ALREADY_PAIRED"))) {
            pairConfirmFailure = failure
            assertEquals(label, ConfirmOutcome.NotSent, confirm(client))
        }
        assertTrue(logLines.toString(), confirmLine("not_sent", "-") in logLines)
    }

    // ---- what the line holds ----

    @Test
    fun `the session id is hashed in the line - never printed raw - and no scan id, no device name, no model and no time is printed`() = withClient { client ->
        pairListResponse = listAnswer(candidate("secret-scan-1"), candidate("secret-scan-2", unmappedName))
        list(client)
        pairConfirmResponse = confirmAnswer(before = "secret-scan-1")
        confirm(client, "secret-scan-1")
        val lines = logLines.filter { it.startsWith("pair-link ") }
        assertEquals(2, lines.size)
        for (line in lines) {
            assertTrue(line, !line.contains("sess-1") && !line.contains("secret-scan") && !line.contains("SM-G975F") && !line.contains("XYZ-1") && !line.contains("1760000000000"))
            assertTrue(line, line.contains("session=${LogRedaction.hashId("sess-1")}"))
        }
        assertEquals("pair-link call=list session=${LogRedaction.hashId("sess-1")} outcome=listed detail=n2_v72 unknownKeys=-", lines[0])
    }

    // ---- the exit gate ----

    /**
     * Rule 23: REPLACES `the real gate is closed - while PAIR_LIST_TEXTS_APPROVED is false neither call leaves the phone` (it pinned the guard false, and the gate with it). Hasan approved the texts on 2026-10-22 and the
     * guard is true; the REAL gate - the service as built, nothing opened by the test - reads it, so the two calls leave the phone for a signed-in owner.
     */
    @Test
    fun `the real gate is open - the guard is true, so with nothing opened by the test both calls leave the phone through the real service and are printed`() {
        assertTrue("the guard of the surface is true since the approval of 2026-10-22", net.neuralcheck.presentation.screen.PAIR_LIST_TEXTS_APPROVED)
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.pairListResponse = listAnswer(candidate("scan-1"))
            h.pairConfirmResponse = confirmAnswer()
            assertTrue("the service says it is open - nobody opened it", h.service.pairCallsOpen())
            val client = PairLinkClient(h.service)
            assertTrue(list(client) is ListOutcome.Listed)
            assertEquals(ConfirmOutcome.Confirmed, confirm(client))
            assertEquals(1, h.callableCalls("listOpenMaintenanceBefores"))
            assertEquals(1, h.callableCalls("confirmMaintenanceAfterLink"))
            assertEquals(2, h.logLines.count { it.startsWith("pair-link ") })
        }
    }

    @Test
    fun `a shut gate sends nothing and prints nothing - and the other guards' gates are other gates, opening them opens nothing here`() {
        withClient(gateOpen = false) { client ->
            assertEquals(ListOutcome.GuardClosed, list(client))
            assertEquals(ConfirmOutcome.GuardClosed, confirm(client))
            assertEquals("no callable call at all", 0, events.count { it.startsWith("callable:") })
            assertTrue(logLines.toString(), logLines.none { it.startsWith("pair-link ") })
            assertTrue("the service says it is closed", !service.pairCallsOpen())
            // straight at the service
            val listFailure = runCatching { runBlocking { service.listOpenMaintenanceBefores("sess-1") } }.exceptionOrNull()
            assertTrue("$listFailure", listFailure is PairCallsNotApprovedException)
            val confirmFailure = runCatching { runBlocking { service.confirmMaintenanceAfterLink("sess-1", "scan-1") } }.exceptionOrNull()
            assertTrue("$confirmFailure", confirmFailure is PairCallsNotApprovedException)
            assertEquals(0, events.count { it.startsWith("callable:") })
        }
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.closePairCalls()
            h.openFormCalls()
            h.openRepeatCalls()
            h.openConsentCalls()
            assertNull(runCatching { runBlocking { h.service.listOpenMaintenanceBefores("s") } }.getOrNull())
            assertNull(runCatching { runBlocking { h.service.confirmMaintenanceAfterLink("s", "b") } }.getOrNull())
            assertEquals(0, h.events.count { it.startsWith("callable:") })
        }
    }

    @Test
    fun `the gate comes before the auth - a signed-out phone with a closed gate gets the gate's answer, not an auth failure`() = withClient(signedIn = false, gateOpen = false) { client ->
        assertEquals(ListOutcome.GuardClosed, list(client))
        assertEquals(ConfirmOutcome.GuardClosed, confirm(client))
    }
}
