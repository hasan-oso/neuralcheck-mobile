package net.neuralcheck.presentation.viewmodel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.local.db.entity.FunctionalResultEntity
import net.neuralcheck.data.repository.ScanRepository
import net.neuralcheck.data.repository.TestResultRepository
import net.neuralcheck.domain.evaluation.EvaluationSubmissionStatusHolder
import net.neuralcheck.domain.model.ManualStatuses
import net.neuralcheck.util.ManualAnswerLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * Batch 1.0.12, item 3 (user decision 2026-10-13): the `auto_fill` line — `commitAndContinue` writes every test nobody
 * answered as `not_tested`, and each of those rows owes one `NeuralCheck.ManualAnswer` line. The REAL view model over an
 * in-memory [TestResultRepository] (the Room DAO is the only layer replaced), the lines read through
 * [ManualAnswerLog.sink].
 *
 * Also pinned: `setResult` itself prints NOTHING — the row that calls it printed the line with its own source (chip /
 * dialog), and a second line from the writer would count the same answer twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManualTestsViewModelAnswerLogTest {

    private class MemoryResults : TestResultRepository {
        val rows = LinkedHashMap<String, FunctionalResultEntity>()
        override suspend fun saveResults(results: List<FunctionalResultEntity>) { results.forEach { rows[it.testId] = it } }
        override suspend fun upsertResults(results: List<FunctionalResultEntity>) { results.forEach { rows[it.testId] = it } }
        override suspend fun getResults(scanId: String) = rows.values.toList()
        override suspend fun getResultsByCategory(scanId: String, category: String) =
            rows.values.filter { it.categoryName == category }
    }

    private val lines = mutableListOf<String>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ManualAnswerLog.sink = { lines += it }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        ManualAnswerLog.resetSink()
    }

    private fun viewModel(repo: TestResultRepository) = ManualTestsViewModel(
        context = mock<Context>(),
        testResultRepository = repo,
        scanRepository = mock<ScanRepository>(),
        evaluationSubmissionStatusHolder = EvaluationSubmissionStatusHolder()
    )

    @Test
    fun `each unanswered test is filled as not_tested and prints one auto_fill line - and an answered one prints none`() = runTest {
        val repo = MemoryResults()
        val vm = viewModel(repo)
        vm.setResult("scan-0001", "M-04", ManualStatuses.PASS)
        lines.clear() // setResult printed nothing (pinned below); this makes the count below about commitAndContinue alone

        var done = false
        vm.commitAndContinue("scan-0001", listOf("M-01", "M-04", "M-08", "M-15")) { done = true }

        assertTrue(done)
        assertEquals(
            listOf(
                "manual-answer testId=M-01 status=not_tested rawPresent=false source=auto_fill",
                "manual-answer testId=M-08 status=not_tested rawPresent=false source=auto_fill",
                "manual-answer testId=M-15 status=not_tested rawPresent=false source=auto_fill"
            ),
            lines
        )
        assertEquals("the rows are what the lines say", setOf("M-01", "M-04", "M-08", "M-15"), repo.rows.keys)
        assertEquals(ManualStatuses.PASS, repo.rows["M-04"]!!.status)
        assertTrue(listOf("M-01", "M-08", "M-15").all { repo.rows[it]!!.status == ManualStatuses.NOT_TESTED })
    }

    @Test
    fun `an unavailable answer is an answer - commitAndContinue prints no line for it`() = runTest {
        val repo = MemoryResults()
        val vm = viewModel(repo)
        vm.setResult("scan-0001", "M-08", ManualStatuses.UNAVAILABLE)
        lines.clear()
        vm.commitAndContinue("scan-0001", listOf("M-08")) { }
        assertTrue(lines.isEmpty())
        assertEquals(ManualStatuses.UNAVAILABLE, repo.rows["M-08"]!!.status)
    }

    @Test
    fun `nothing unanswered, nothing printed`() = runTest {
        val repo = MemoryResults()
        val vm = viewModel(repo)
        vm.commitAndContinue("scan-0001", emptyList()) { }
        assertTrue(lines.isEmpty())
    }

    @Test
    fun `setResult prints nothing - the row printed the line with its own source`() = runTest {
        val repo = MemoryResults()
        val vm = viewModel(repo)
        vm.setResult("scan-0001", "M-02", ManualStatuses.PASS, mapOf<String, Any?>("touchedCells" to 120))
        vm.setResult("scan-0001", "M-01", ManualStatuses.FAIL)
        assertTrue("a line from the writer would count one answer twice: $lines", lines.isEmpty())
    }

    @Test
    fun `no line carries the scan id`() = runTest {
        val repo = MemoryResults()
        val vm = viewModel(repo)
        vm.commitAndContinue("scan-0001", listOf("M-01", "M-02")) { }
        assertEquals(2, lines.size)
        lines.forEach { assertFalse("`$it` carries the scan id", it.contains("scan-0001") || it.contains("scan")) }
    }
}
