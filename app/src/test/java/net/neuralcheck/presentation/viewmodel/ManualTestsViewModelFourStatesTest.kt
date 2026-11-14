package net.neuralcheck.presentation.viewmodel

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.local.db.converter.MeasurementsJson
import net.neuralcheck.data.local.db.entity.FunctionalResultEntity
import net.neuralcheck.data.repository.ScanRepository
import net.neuralcheck.data.repository.TestResultRepository
import net.neuralcheck.domain.evaluation.EvaluationSubmissionStatusHolder
import net.neuralcheck.domain.manual.ManualTestVisibility
import net.neuralcheck.domain.model.ManualStatuses
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * fieldsVersion 69 (S-15, N-17): what [ManualTestsViewModel] writes for the four
 * manual answers, and that a test the model does not have gets NO row.
 *
 * The REAL view model over an in-memory [TestResultRepository] (the Room DAO
 * is the only layer replaced; the view model's own logic is the thing under test).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManualTestsViewModelFourStatesTest {

    private class MemoryResults : TestResultRepository {
        val rows = LinkedHashMap<String, FunctionalResultEntity>()
        override suspend fun saveResults(results: List<FunctionalResultEntity>) { results.forEach { rows[it.testId] = it } }
        override suspend fun upsertResults(results: List<FunctionalResultEntity>) { results.forEach { rows[it.testId] = it } }
        override suspend fun getResults(scanId: String) = rows.values.toList()
        override suspend fun getResultsByCategory(scanId: String, category: String) =
            rows.values.filter { it.categoryName == category }
    }

    @Before
    fun setUp() { Dispatchers.setMain(UnconfinedTestDispatcher()) }

    @After
    fun tearDown() { Dispatchers.resetMain() }

    private fun viewModel(repo: TestResultRepository) = ManualTestsViewModel(
        context = mock<Context>(),
        testResultRepository = repo,
        scanRepository = mock<ScanRepository>(),
        evaluationSubmissionStatusHolder = EvaluationSubmissionStatusHolder()
    )

    @Test
    fun `each of the four answers is stored as itself, and only pass and fail carry a score`() = runTest {
        val repo = MemoryResults()
        val vm = viewModel(repo)
        vm.setResult("scan", "M-01", ManualStatuses.PASS)
        vm.setResult("scan", "M-02", ManualStatuses.FAIL)
        vm.setResult("scan", "M-04", ManualStatuses.NOT_TESTED)
        vm.setResult("scan", "M-08", ManualStatuses.UNAVAILABLE)

        assertEquals(ManualStatuses.ALL, repo.rows.values.map { it.status })
        assertEquals(100f, repo.rows["M-01"]!!.score)
        assertEquals(0f, repo.rows["M-02"]!!.score)
        assertNull(repo.rows["M-04"]!!.score)
        assertNull("a part the device does not have is ignored by scoring, never a 0", repo.rows["M-08"]!!.score)
        assertEquals("manual", repo.rows["M-08"]!!.notes)
    }

    @Test
    fun `unavailable is kept as unavailable by commitAndContinue - it is an answer, not an unanswered test`() = runTest {
        val repo = MemoryResults()
        val vm = viewModel(repo)
        vm.setResult("scan", "M-08", ManualStatuses.UNAVAILABLE)
        var done = false
        vm.commitAndContinue("scan", listOf("M-01", "M-08")) { done = true }

        assertTrue(done)
        assertEquals(ManualStatuses.UNAVAILABLE, repo.rows["M-08"]!!.status)
        assertEquals("the unanswered one is filled as not_tested", ManualStatuses.NOT_TESTED, repo.rows["M-01"]!!.status)
    }

    // ── fieldsVersion 69 (N-19): the raw of a run rides with the answer it produced ──

    @Test
    fun `the raw of a run is stored in the row's own measurements column with the answer`() = runTest {
        val repo = MemoryResults()
        val vm = viewModel(repo)
        val raw = mapOf<String, Any?>("touchedCells" to 120, "totalCells" to 120, "endReason" to "auto_complete")
        vm.setResult("scan", "M-02", ManualStatuses.PASS, raw)

        val row = repo.rows["M-02"]!!
        assertEquals(ManualStatuses.PASS, row.status)
        assertNull("rawValue stays display text: the raw is not mixed into it", row.rawValue)
        val back = MeasurementsJson.decode(row.measurements)!!
        assertEquals(120L, back["touchedCells"])
        assertEquals("auto_complete", back["endReason"])
    }

    @Test
    fun `an answer without a run - a chip tap - leaves the column NULL on a row that has no raw, and the latest write replaces the raw there`() = runTest {
        val repo = MemoryResults()
        val vm = viewModel(repo)
        vm.setResult("scan", "M-01", ManualStatuses.PASS)
        assertNull("no run, no compound raw", repo.rows["M-01"]!!.measurements)
        vm.setResult("scan", "M-01", ManualStatuses.FAIL, mapOf<String, Any?>("k" to 1))
        vm.setResult("scan", "M-01", ManualStatuses.PASS)
        assertEquals(ManualStatuses.PASS, repo.rows["M-01"]!!.status)
        assertNull("on every row but M-02 the raw belongs to the answer written with it: the latest write replaces both", repo.rows["M-01"]!!.measurements)
    }

    @Test
    fun `E21 item 3 - a chip tap on M-02 writes the answer only and keeps the stored raw, and a new run replaces it (REPLACES the test that pinned the erasure)`() = runTest {
        val repo = MemoryResults()
        val vm = viewModel(repo)
        vm.setResult("scan", "M-02", ManualStatuses.PASS, mapOf<String, Any?>("touchedCells" to 120))
        val raw = repo.rows["M-02"]!!.measurements
        vm.setResult("scan", "M-02", ManualStatuses.FAIL)
        assertEquals(ManualStatuses.FAIL, repo.rows["M-02"]!!.status)
        assertEquals("the raw of the run stays: the row says the last answer, the raw says how that run ended", raw, repo.rows["M-02"]!!.measurements)
        vm.setResult("scan", "M-02", ManualStatuses.PASS, mapOf<String, Any?>("touchedCells" to 7))
        assertEquals("a new run's raw replaces the old one - two runs are never kept side by side", 7L, MeasurementsJson.decode(repo.rows["M-02"]!!.measurements)!!["touchedCells"])
    }

    @Test
    fun `a test the model does not have gets no row - it is left out of the ids commitAndContinue fills`() = runTest {
        val repo = MemoryResults()
        val vm = viewModel(repo)
        val shown = listOf("M-01", "M-02", "M-14", "M-15")
        val hidden = listOf("M-14", "M-15")
        vm.commitAndContinue("scan", ManualTestVisibility.idsToAnswer(shown, hidden)) { }

        assertEquals(setOf("M-01", "M-02"), repo.rows.keys)
        assertTrue("no not_tested row for a hidden test", hidden.none { it in repo.rows })
    }
}
