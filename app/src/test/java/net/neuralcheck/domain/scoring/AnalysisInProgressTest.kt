package net.neuralcheck.domain.scoring

import com.google.firebase.functions.FirebaseFunctionsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.IOException

/**
 * fieldsVersion 69 (S-26, contract docs/FV68_CONTRACT.md section 7-h (d)): «انتظر» instead of the error
 * card when the server says it is still processing this scan, and the session id kept for the retry.
 *
 * What a unit test CAN reach: the classifier and the session decision, both extracted from
 * [AiScoringEngine.calculateScore] (A110 precedent — the engine's own constructor needs a real
 * FirebaseAnalysisService, which a JVM test cannot build). What it cannot: that the server really answers
 * `ALREADY_EXISTS` for "in progress" — منقول، غير متحقَّق من هذا المستودع (neuralcheck-backend).
 */
class AnalysisInProgressTest {

    private fun functionsException(code: FirebaseFunctionsException.Code, cause: Throwable? = null): FirebaseFunctionsException =
        mock<FirebaseFunctionsException>().also {
            whenever(it.code).thenReturn(code)
            if (cause != null) whenever(it.cause).thenReturn(cause)
        }

    @Test
    fun `ALREADY_EXISTS from the server is "still processing"`() {
        assertTrue(isAnalysisInProgress(functionsException(FirebaseFunctionsException.Code.ALREADY_EXISTS)))
    }

    @Test
    fun `ALREADY_EXISTS built locally over a network exception is not a server answer`() {
        assertFalse(isAnalysisInProgress(functionsException(FirebaseFunctionsException.Code.ALREADY_EXISTS, cause = IOException("socket reset"))))
    }

    @Test
    fun `no other Functions code is "still processing" - every value, not the one from a sample (rule 30)`() {
        for (code in FirebaseFunctionsException.Code.values().filter { it != FirebaseFunctionsException.Code.ALREADY_EXISTS }) {
            assertFalse("$code must not read as \"still processing\"", isAnalysisInProgress(functionsException(code)))
        }
    }

    @Test
    fun `a failure that is not a Functions answer is never this state - the code decides, not words in a message`() {
        assertFalse(isAnalysisInProgress(IOException("ALREADY_EXISTS")))
        assertFalse(isAnalysisInProgress(IllegalStateException("already-exists: قيد المعالجة")))
        assertFalse(isAnalysisInProgress(RuntimeException()))
    }

    // ───────────────────────── the session id ─────────────────────────
    //
    // Order 2026-10-14 («Record the session at scan start»): replaces the three tests that pinned the old rule,
    // "the session id is consumed after a success and after every other failure - as before (a known limit)" (rule 23).
    // Two decisions now, both made by the engine after an analysis:
    //  - the HOLDER (the device's "session joined last") is emptied when the analysis used a session, the answer is
    //    final, and the holder STILL holds that session;
    //  - the scan's own RECORD (ScanSessionRecords) is removed only by a success — a failure keeps it, so a retry
    //    after a rejection still carries its session.
    // The engine itself cannot be built on the JVM here (A110 precedent above); AiScoringEngineSessionTest runs the
    // real decisions through the engine's own call sites with only the network boundary replaced.

    private fun result(error: String? = null, inProgress: Boolean = false, succeeded: Boolean = false) = ScoringResult(
        overallScore = 0f, overallGrade = "?", gradeDescription = "", axisScores = emptyMap(),
        categoryScores = emptyMap(), individualScores = emptyMap(), criticalFailures = emptyList(),
        antiTamperPenalty = 0f, isIncomplete = false, scoringMethod = "ai_v1",
        aiAnalysis = if (succeeded) mock<net.neuralcheck.domain.model.AiAnalysisResult>() else null,
        error = error, analysisInProgress = inProgress
    )

    @Test
    fun `the holder is KEPT when the server is still processing - the retry is for the same scan`() {
        assertFalse(shouldClearSessionAfterAnalysis("session-1", "session-1", result(inProgress = true)))
    }

    @Test
    fun `the holder is emptied after a success and after every other failure when it still holds the session the analysis used - as before (a known limit)`() {
        assertTrue(shouldClearSessionAfterAnalysis("session-1", "session-1", result(succeeded = true)))
        assertTrue(shouldClearSessionAfterAnalysis("session-1", "session-1", result(error = "network")))
    }

    @Test
    fun `a session joined after the scan started is not emptied by the earlier scan's analysis`() {
        // scan under session-1, session-2 joined meanwhile: the analysis of the first must leave the second alone
        assertFalse(shouldClearSessionAfterAnalysis("session-1", "session-2", result(succeeded = true)))
        assertFalse(shouldClearSessionAfterAnalysis("session-1", "session-2", result(error = "network")))
    }

    @Test
    fun `an empty holder has nothing to empty`() {
        assertFalse(shouldClearSessionAfterAnalysis("session-1", null, result(succeeded = true)))
        assertFalse(shouldClearSessionAfterAnalysis("session-1", "", result(error = "network")))
    }

    @Test
    fun `with no session used there is nothing to clear - whatever the holder holds now`() {
        assertFalse(shouldClearSessionAfterAnalysis(null, null, result()))
        assertFalse(shouldClearSessionAfterAnalysis("", "", result()))
        assertFalse(shouldClearSessionAfterAnalysis("  ", "  ", result(inProgress = true)))
        assertFalse("a personal scan does not empty a session joined later", shouldClearSessionAfterAnalysis(null, "session-2", result(succeeded = true)))
    }

    @Test
    fun `the scan's record is removed by a success and kept by every failure - a retry after a rejection keeps its session`() {
        assertTrue(shouldConsumeRecordAfterAnalysis("session-1", result(succeeded = true)))

        assertFalse("an error answer", shouldConsumeRecordAfterAnalysis("session-1", result(error = "الجلسة منتهية")))
        assertFalse("a failure with no error text at all is still not a success", shouldConsumeRecordAfterAnalysis("session-1", result()))
        assertFalse("still processing", shouldConsumeRecordAfterAnalysis("session-1", result(inProgress = true)))
    }

    @Test
    fun `with no session used there is no record to remove`() {
        assertFalse(shouldConsumeRecordAfterAnalysis(null, result(succeeded = true)))
        assertFalse(shouldConsumeRecordAfterAnalysis("", result(succeeded = true)))
        assertFalse(shouldConsumeRecordAfterAnalysis(" ", result(succeeded = true)))
    }

    @Test
    fun `a result is not "in progress" unless it says so - and an in-progress result carries no error text`() {
        assertEquals(false, result().analysisInProgress)
        val waiting = result(inProgress = true)
        assertTrue(waiting.analysisInProgress)
        assertEquals("«انتظر» is not an error: the error card must not show", null, waiting.error)
    }
}
