package net.neuralcheck.presentation.viewmodel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.repository.ScanRepository
import net.neuralcheck.data.repository.TestResultRepository
import net.neuralcheck.domain.evaluation.EvaluationSubmissionStatusHolder
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Order 2026-09-30 («ملحق — النصّ، ورسالة التقييم، والبروتوكول»):
 * [ManualTestsViewModel.observeEvaluationFailure] — the scanId-filtering
 * layer between [EvaluationSubmissionStatusHolder] and the line
 * ManualTestsScreen renders.
 *
 * ── ما يفحصه هذا الاختبار بدقّة (القاعدة ٣٧) ──
 * Constructs the REAL [ManualTestsViewModel] with a REAL
 * [EvaluationSubmissionStatusHolder] (no dependencies of its own) —
 * [Context]/[TestResultRepository]/[ScanRepository] are Mockito mocks that
 * `observeEvaluationFailure` never touches at all (it only reads the
 * injected holder), so they are unstubbed placeholders, not meaningfully
 * exercised here.
 *
 * This repo has NO existing precedent for unit-testing a `@HiltViewModel`'s
 * `viewModelScope`-launched coroutine directly (the established pattern —
 * `joinSessionErrorMessage`, `evaluationSubmissionErrorMessage`, etc. — is
 * to extract the interesting logic into a plain top-level function and test
 * THAT instead). `observeEvaluationFailure`'s own logic (an equality check,
 * `failure.scanId == scanId`) is too trivial to extract usefully, so this
 * test exercises the ViewModel directly via `Dispatchers.setMain` +
 * `UnconfinedTestDispatcher` (`kotlinx-coroutines-test`, already a
 * dependency) — the standard technique for this, not exotic, but new to
 * this codebase; flagged here rather than silently assumed to work.
 */
class ManualTestsViewModelEvaluationFailureTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(holder: EvaluationSubmissionStatusHolder) = ManualTestsViewModel(
        context = mock<Context>(),
        testResultRepository = mock<TestResultRepository>(),
        scanRepository = mock<ScanRepository>(),
        evaluationSubmissionStatusHolder = holder
    )

    @Test
    fun `a failure for the observed scanId sets the message`() = runTest {
        val holder = EvaluationSubmissionStatusHolder()
        val vm = viewModel(holder)

        vm.observeEvaluationFailure("scan-1")
        holder.publishFailure("scan-1", "تعذّر إرسال التقييم")

        assertEquals("تعذّر إرسال التقييم", vm.evaluationFailureMessage.value)
    }

    @Test
    fun `a failure for a DIFFERENT scanId is ignored`() = runTest {
        // Guards against one screen showing another scan's failure — e.g.
        // a stale event from a scan the user abandoned earlier.
        val holder = EvaluationSubmissionStatusHolder()
        val vm = viewModel(holder)

        vm.observeEvaluationFailure("scan-1")
        holder.publishFailure("scan-OTHER", "تعذّر إرسال التقييم")

        assertNull(vm.evaluationFailureMessage.value)
    }

    @Test
    fun `no failure published leaves the message null - no success line exists`() = runTest {
        val holder = EvaluationSubmissionStatusHolder()
        val vm = viewModel(holder)

        vm.observeEvaluationFailure("scan-1")

        assertNull(vm.evaluationFailureMessage.value)
    }
}
