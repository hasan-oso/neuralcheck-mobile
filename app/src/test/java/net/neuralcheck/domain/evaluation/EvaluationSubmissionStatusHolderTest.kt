package net.neuralcheck.domain.evaluation

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Order 2026-09-30 («ملحق — النصّ، ورسالة التقييم، والبروتوكول»): pure
 * pub-sub semantics of [EvaluationSubmissionStatusHolder] — the mechanism
 * that carries the confirm-at-scan-completion submission's failure from
 * ActiveScanViewModel to ManualTestsViewModel.
 *
 * This is a REAL instance of the holder (it has no dependencies to mock) and
 * real [kotlinx.coroutines.flow.SharedFlow] semantics — not a stand-in. It
 * does NOT exercise [EvaluationSubmitter] (see
 * EvaluationSubmitOnScanCompleteTest for that) or ManualTestsViewModel's
 * scanId-filtering (see ManualTestsViewModelEvaluationFailureTest).
 */
class EvaluationSubmissionStatusHolderTest {

    @Test
    fun `a live collector receives a published failure`() = runTest {
        val holder = EvaluationSubmissionStatusHolder()
        val collected = async { withTimeoutOrNull(1_000) { holder.failures.first() } }
        yield() // let the collector attach before publishing
        holder.publishFailure("scan-1", "تعذّر إرسال التقييم")

        val result = collected.await()
        assertEquals(EvaluationSubmissionStatusHolder.ScanFailure("scan-1", "تعذّر إرسال التقييم"), result)
    }

    @Test
    fun `replay is 0 - a subscriber that starts AFTER publishing never sees it`() = runTest {
        // This is the exact mechanism behind the order's own requirement:
        // "وإن وصلت النتيجة بعد مغادرة الشاشة: لا شيء يُعرض" — a collector
        // that starts listening (or restarts, e.g. a fresh ManualTestsViewModel
        // instance) after the event already fired gets nothing, by design.
        val holder = EvaluationSubmissionStatusHolder()
        holder.publishFailure("scan-1", "تعذّر إرسال التقييم")

        val result = withTimeoutOrNull(200) { holder.failures.first() }

        assertNull("a late subscriber must not receive a replayed value", result)
    }
}
