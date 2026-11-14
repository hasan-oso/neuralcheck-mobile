package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.neuralcheck.data.remote.ConsentCallsNotApprovedException
import net.neuralcheck.data.remote.ScanConsentClient
import net.neuralcheck.data.remote.ScanConsentClient.RecordOutcome
import net.neuralcheck.domain.consent.ConsentLocale
import net.neuralcheck.domain.consent.ConsentRefusal
import net.neuralcheck.util.EvalCardLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The consent screen's one call through the REAL path - A-P6-12 (rules 30, 37, 22, 21, 36). Real: [ScanConsentClient], `FirebaseAnalysisService.recordScanConsent` (the callable name, the request map, the Gson
 * tree of the answer), the reader, the log. Replaced: only the HTTP round trip and auth (the harness) and - declared - the consent callable's exit gate: opened by [RealJoinPathHarness.openConsentCalls] for most
 * tests, shut by [RealJoinPathHarness.closeConsentCalls] for the one that shows a shut gate, and left as built for ONE (`the real gate is open`: the guard is true since 2026-10-23).
 *
 * THE ANSWERS ARE WRITTEN FROM THE CONTRACT, NOT PRODUCED BY THE SERVER: the server's half (`5ab5eaf`, inside `5361116`) is not deployed and was not run from here. Each is spelled key by key as A8 (1) and (5)
 * of `evidence/run-2026-10-18/protocol-contract-A8-A11-A12-neuralcheck-backend-5361116.md` spell them (a copy, rule 29). The refusal bodies' status and message are made up and vary on purpose: the app reads
 * `details.reasonCode` only (rule 21).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ScanConsentClientRealPathTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** [gateOpen]: `true` opens the gate (a test says what it relies on), `false` SHUTS it (the real one reads the guard, which is true), `null` leaves the REAL gate as the service was built (rule 37). */
    private fun withClient(signedIn: Boolean = true, gateOpen: Boolean? = true, check: RealJoinPathHarness.(ScanConsentClient) -> Unit) {
        RealJoinPathHarness(signedInAtStart = signedIn, viewModelContext = context).use { h ->
            when (gateOpen) {
                true -> h.openConsentCalls()
                false -> h.closeConsentCalls()
                null -> Unit
            }
            h.check(ScanConsentClient(h.service))
        }
    }

    /** A8 (1): the five keys of the answer. */
    private fun recordedAnswer(textVersion: Any? = 2, locale: String? = "ar", alreadyRecorded: Boolean? = false, session: String = "sess-1"): HashMap<String, Any?> =
        hashMapOf("sessionId" to session, "state" to "accepted", "textVersion" to textVersion, "locale" to locale, "alreadyRecorded" to alreadyRecorded)

    private fun refusal(code: String, status: String = "FAILED_PRECONDITION") =
        SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody(status, "made-up message", code))

    private fun record(client: ScanConsentClient, locale: ConsentLocale = ConsentLocale.AR) = runBlocking { client.record("sess-1", locale) }

    @Test
    fun `the call sends the three keys and nothing else - the version the app shows, the language it was drawn in - and a recorded consent is a recorded consent`() = withClient { client ->
        consentResponse = recordedAnswer()
        val outcome = record(client)
        assertEquals(1, callableCalls("recordScanConsent"))
        assertEquals(
            "the contract's request: {sessionId, textVersion, locale} - no identifier, no text, no time",
            listOf<Any?>(hashMapOf<String, Any?>("sessionId" to "sess-1", "textVersion" to 2, "locale" to "ar")),
            consentRequests.toList()
        )
        assertEquals(RecordOutcome.Recorded(storedTextVersion = 2, storedLocale = ConsentLocale.AR, alreadyRecorded = false, differs = false), outcome)
        assertTrue(logLines.toString(), EvalCardLog.consentRecordLine("recorded", "new", "sess-1") in logLines)
    }

    @Test
    fun `the Turkish screen records Turkish - the language is the caller's, never chosen here`() = withClient { client ->
        consentResponse = recordedAnswer(locale = "tr")
        assertEquals(RecordOutcome.Recorded(2, ConsentLocale.TR, false, differs = false), record(client, ConsentLocale.TR))
        assertEquals(listOf<Any?>(hashMapOf<String, Any?>("sessionId" to "sess-1", "textVersion" to 2, "locale" to "tr")), consentRequests.toList())
    }

    @Test
    fun `a second call answers the first one's stored values - it is recorded, it says it differs, and the line says so`() = withClient { client ->
        consentResponse = recordedAnswer(textVersion = 1, locale = "tr", alreadyRecorded = true)
        val outcome = record(client, ConsentLocale.AR)
        assertEquals(RecordOutcome.Recorded(1, ConsentLocale.TR, true, differs = true), outcome)
        assertTrue(logLines.toString(), EvalCardLog.consentRecordLine("recorded", "already_differs", "sess-1") in logLines)
        // and the same values, already recorded
        consentResponse = recordedAnswer(alreadyRecorded = true)
        assertEquals(RecordOutcome.Recorded(2, ConsentLocale.AR, true, differs = false), record(client))
        assertTrue(logLines.toString(), EvalCardLog.consentRecordLine("recorded", "already", "sess-1") in logLines)
    }

    @Test
    fun `an answer without the flag is still a recorded consent, its flag kept absent - and the server's JSON null survives the real pipeline as absent, not as false`() = withClient { client ->
        consentResponse = recordedAnswer(alreadyRecorded = null).also { it.remove("alreadyRecorded") }
        assertEquals(RecordOutcome.Recorded(2, ConsentLocale.AR, null, differs = false), record(client))
        consentResponse = recordedAnswer(alreadyRecorded = null)
        assertEquals(RecordOutcome.Recorded(2, ConsentLocale.AR, null, differs = false), record(client))
        assertTrue(logLines.toString(), EvalCardLog.consentRecordLine("recorded", "flag_absent", "sess-1") in logLines)
    }

    @Test
    fun `an answer that is not a recorded consent is an unreadable answer with its own line - never a consent`() = withClient { client ->
        val cases = listOf<Pair<Any?, String>>(
            recordedAnswer(session = "sess-2") to "session_id_differs",
            hashMapOf<String, Any?>() to "session_id_missing",
            null to "absent",
            "an answer that is a text" to "not_an_object_string",
            recordedAnswer().also { it["state"] = "declined" } to "state_not_accepted",
            recordedAnswer().also { it.remove("state") } to "state_absent",
            recordedAnswer(textVersion = null) to "text_version_absent",
            recordedAnswer(locale = "en") to "locale_unrecognized",
            recordedAnswer().also { it["alreadyRecorded"] = "yes" } to "already_recorded_not_a_boolean_string"
        )
        for ((answer, why) in cases) {
            consentResponse = answer
            assertEquals("$answer", RecordOutcome.UnreadableAnswer(why), record(client))
            assertTrue(logLines.toString(), EvalCardLog.consentRecordLine("unreadable_answer", why, "sess-1") in logLines)
        }
    }

    @Test
    fun `every refusal of the contract is read as itself, for any Functions status - the code is the text, not the exception`() = withClient { client ->
        for (r in ConsentRefusal.values()) {
            for (status in listOf("FAILED_PRECONDITION", "PERMISSION_DENIED", "INVALID_ARGUMENT", "UNAUTHENTICATED")) {
                consentFailure = refusal(r.wire, status)
                assertEquals("${r.wire} / $status", RecordOutcome.Refused(r.wire, r), record(client))
            }
        }
        // the four the session contract already names are refusals too (known there), and a code the app has no case for keeps its text but is none of the eight
        for (shared in listOf("SESSION_NOT_PARTICIPANT", "SESSION_EXPIRED", "SESSION_EXPIRES_AT_INVALID", "SESSION_STATUS_UNKNOWN")) {
            consentFailure = refusal(shared, "PERMISSION_DENIED")
            assertEquals(shared, RecordOutcome.Refused(shared, null), record(client))
        }
        consentFailure = refusal("CONSENT_BRAND_NEW")
        assertEquals(RecordOutcome.Refused("CONSENT_BRAND_NEW", null), record(client))
        assertTrue(logLines.toString(), EvalCardLog.consentRecordLine("refused", "CONSENT_BRAND_NEW", "sess-1") in logLines)
        assertTrue(logLines.toString(), EvalCardLog.consentRecordLine("refused", "CONSENT_SESSION_NOT_SCANNING", "sess-1") in logLines)
    }

    @Test
    fun `a disconnect, a plain failure and a signed-out phone are not-sent or a refusal the server named - never a recorded consent, even when the text of the failure says one`() {
        withClient { client ->
            for ((label, failure) in SessionWireFixtures.offlineFailures() + ("a plain exception" to RuntimeException("CONSENT_SESSION_NOT_SCANNING"))) {
                consentFailure = failure
                assertEquals(label, RecordOutcome.NotSent, record(client))
            }
            assertTrue(logLines.toString(), EvalCardLog.consentRecordLine("not_sent", "-", "sess-1") in logLines)
        }
        // a phone with no identity is NOT stopped here: the call is made and the server's own CONSENT_NOT_AUTHENTICATED is the answer that is read (no local auth gate, like the cancel)
        withClient(signedIn = false) { client ->
            consentFailure = refusal("CONSENT_NOT_AUTHENTICATED", "UNAUTHENTICATED")
            assertEquals(RecordOutcome.Refused("CONSENT_NOT_AUTHENTICATED", ConsentRefusal.NOT_AUTHENTICATED), record(client))
            assertEquals("the call was made", 1, callableCalls("recordScanConsent"))
        }
    }

    @Test
    fun `the session id is hashed in the line - never printed raw - and neither the version, the language nor any text is printed`() = withClient { client ->
        consentResponse = recordedAnswer()
        record(client)
        val line = logLines.single { it.startsWith("consent-record ") }
        assertTrue(line, !line.contains("sess-1") && !line.contains("textVersion") && !line.contains("locale") && !line.contains("ar"))
        assertEquals("consent-record outcome=recorded detail=new session=${net.neuralcheck.util.LogRedaction.hashId("sess-1")}", line)
    }

    /**
     * Rule 23: REPLACES `the real gate is closed - while CONSENT_TEXTS_APPROVED is false the call does not leave the phone, and nothing is printed` (it pinned the guard false, and the real gate with it). The guard
     * is TRUE since 2026-10-23; the REAL gate - the service as built, nothing opened or shut by the test - reads it, so the call leaves the phone through the real service with the version of the text and is printed.
     */
    @Test
    fun `the real gate is open - the guard is true, so with nothing opened by the test the call leaves the phone through the real service and is printed`() = withClient(gateOpen = null) { client ->
        assertTrue("the guard of the screen is true since 2026-10-23", net.neuralcheck.presentation.screen.CONSENT_TEXTS_APPROVED)
        assertTrue("the service says it is open - nobody opened it", service.consentCallsOpen())
        consentResponse = recordedAnswer()
        assertEquals(RecordOutcome.Recorded(storedTextVersion = 2, storedLocale = ConsentLocale.AR, alreadyRecorded = false, differs = false), record(client))
        assertEquals(1, callableCalls("recordScanConsent"))
        assertEquals("the version of the text is the app's own constant: 2", listOf<Any?>(hashMapOf<String, Any?>("sessionId" to "sess-1", "textVersion" to 2, "locale" to "ar")), consentRequests.toList())
        assertTrue(logLines.toString(), EvalCardLog.consentRecordLine("recorded", "new", "sess-1") in logLines)
    }

    @Test
    fun `a shut gate - the call does not leave the phone, and nothing is printed`() {
        withClient(gateOpen = false) { client ->
            // nothing replaced but the gate's state: the real service
            assertEquals(RecordOutcome.GuardClosed, record(client))
            assertEquals("no callable call at all", 0, events.count { it.startsWith("callable:") })
            assertTrue(logLines.toString(), logLines.none { it.startsWith("consent-record ") })
            assertTrue("the service says it is closed", !service.consentCallsOpen())
            // straight at the service
            val failure = runCatching { runBlocking { service.recordScanConsent("sess-1", 1, ConsentLocale.AR) } }.exceptionOrNull()
            assertTrue("$failure", failure is ConsentCallsNotApprovedException)
            assertEquals(0, events.count { it.startsWith("callable:") })
        }
        // and the other guards' gates are other gates: opening them opens nothing here (this one is shut)
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.closeConsentCalls()
            h.openFormCalls()
            h.openRepeatCalls()
            assertNull(runCatching { runBlocking { h.service.recordScanConsent("s", 1, ConsentLocale.AR) } }.getOrNull())
            assertEquals(0, h.events.count { it.startsWith("callable:") })
        }
    }
}
