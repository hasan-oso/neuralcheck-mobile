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
import net.neuralcheck.data.repository.withStoredRawKept
import net.neuralcheck.domain.evaluation.EvaluationSubmissionStatusHolder
import net.neuralcheck.domain.manual.ManualRowActions
import net.neuralcheck.domain.model.ManualStatuses
import net.neuralcheck.util.ManualAnswerLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import java.io.File

/**
 * M7(ب), user decision 2026-10-18: «تأشير «يعمل» لا يفتح الاختبار تلقائياً، بل يؤشّر الفنّي بلا فتحه» — and the raw it must not erase.
 *
 * A tap on «يعمل» of M-02 writes `pass` with no run. Writing it used to be impossible (the tap opened the grid), so the rule «the latest
 * write for a test replaces both the answer and its raw» never met a finished grid run's raw being overwritten by a tap. Now it can: this
 * file proves, over the REAL path — [ManualRowActions] (the row's chip and start-button exits) into the REAL [ManualTestsViewModel.setResult] over an
 * in-memory [TestResultRepository] (the Room DAO is the only layer replaced) — that:
 *  - the tap on a fresh M-02 stores `pass` with the column NULL, and opens nothing;
 *  - the tap on an M-02 that already holds a finished grid run (`pass` + raw) keeps that raw, byte for byte;
 *  - every chip tap, over any stored answer — including a tap that changes the answer, or one after a partial run (`fail` + raw) — stores its
 *    answer and keeps the raw (E21 item 3, approved 2026-10-15; M7(ب) had kept it for a works tap over a stored `pass` only);
 *  - a new grid run over a stored `pass` replaces the raw with its own (a run is always the newest word);
 *  - «ابدأ الاختبار» still opens the grid and writes nothing;
 *  - no other row's write reads the store or changes;
 *  - the report editor's writer (`ReportViewModel.saveManualResults`) goes through the same rule (static scan; the view model itself is not
 *    constructible here without the scoring stack, and the rule's body is `withStoredRawKept`, tested below on the same repository).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManualM02WorksTapKeepsRawTest {

    private class MemoryResults : TestResultRepository {
        val rows = LinkedHashMap<String, FunctionalResultEntity>()
        var reads = 0
        override suspend fun saveResults(results: List<FunctionalResultEntity>) { results.forEach { rows[it.testId] = it } }
        override suspend fun upsertResults(results: List<FunctionalResultEntity>) { results.forEach { rows[it.testId] = it } }
        override suspend fun getResults(scanId: String): List<FunctionalResultEntity> { reads++; return rows.values.toList() }
        override suspend fun getResultsByCategory(scanId: String, category: String) = rows.values.filter { it.categoryName == category }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        ManualAnswerLog.sink = { }
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

    /** A row of [testId] wired to the real view model's writer, as `ManualTestsScreen` wires it; records the dialog calls. */
    private class Wired(testId: String, vm: ManualTestsViewModel) {
        val dialog = mutableListOf<String?>()
        val actions = ManualRowActions(
            testId = testId,
            write = { id, status, m -> vm.setResult("scan", id, status, m) },
            setActiveDialog = { dialog += it }
        )
    }

    private val finishedGrid = mapOf<String, Any?>(
        "gridCols" to 8, "gridRows" to 15, "totalCells" to 120, "touchedCells" to 120, "elapsedMs" to 8946, "endReason" to "auto_complete"
    )
    private val partialGrid = mapOf<String, Any?>(
        "gridCols" to 8, "gridRows" to 15, "totalCells" to 120, "touchedCells" to 61, "elapsedMs" to 15432, "endReason" to "user_marked_not_working"
    )

    @Test
    fun `works tap on a fresh M-02 stores pass with the column NULL and opens nothing`() = runTest {
        val repo = MemoryResults()
        val row = Wired("M-02", viewModel(repo))
        row.actions.onChip(ManualStatuses.PASS)

        val stored = repo.rows["M-02"]!!
        assertEquals(ManualStatuses.PASS, stored.status)
        assertEquals(100f, stored.score)
        assertNull("a tap is not a run: no raw", stored.measurements)
        assertTrue("the tap opens nothing", row.dialog.isEmpty())
    }

    @Test
    fun `works tap on an M-02 that holds a finished grid run keeps that raw byte for byte - the answer is the same`() = runTest {
        val repo = MemoryResults()
        val row = Wired("M-02", viewModel(repo))
        row.actions.onDialogAnswer(ManualStatuses.PASS, finishedGrid)   // the grid ran to the end: pass + its raw
        val rawBefore = repo.rows["M-02"]!!.measurements
        assertTrue("the finished run's raw is stored", rawBefore != null && MeasurementsJson.decode(rawBefore)!!["touchedCells"] == 120L)

        row.actions.onChip(ManualStatuses.PASS)                           // the technician taps «يعمل» again

        val after = repo.rows["M-02"]!!
        assertEquals(ManualStatuses.PASS, after.status)
        assertEquals("the stored raw is untouched", rawBefore, after.measurements)
    }

    @Test
    fun `works tap twice on a finished run keeps the raw both times`() = runTest {
        val repo = MemoryResults()
        val row = Wired("M-02", viewModel(repo))
        row.actions.onDialogAnswer(ManualStatuses.PASS, finishedGrid)
        val rawBefore = repo.rows["M-02"]!!.measurements
        row.actions.onChip(ManualStatuses.PASS)
        row.actions.onChip(ManualStatuses.PASS)
        assertEquals(rawBefore, repo.rows["M-02"]!!.measurements)
    }

    @Test
    fun `works tap after the technician marked not-working after a partial run stores pass WITH the partial run's raw - E21 item 3, the raw says how that run ended, the row says the last answer`() = runTest {
        val repo = MemoryResults()
        val row = Wired("M-02", viewModel(repo))
        row.actions.onDialogAnswer(ManualStatuses.FAIL, partialGrid)      // 61 cells, then «لا يعمل»: fail + the partial run's raw
        val rawBefore = repo.rows["M-02"]!!.measurements
        assertTrue(rawBefore != null)

        row.actions.onChip(ManualStatuses.PASS)

        assertEquals(ManualStatuses.PASS, repo.rows["M-02"]!!.status)
        assertEquals("the raw stays byte for byte; its endReason still says user_marked_not_working", rawBefore, repo.rows["M-02"]!!.measurements)
        assertEquals("user_marked_not_working", MeasurementsJson.decode(repo.rows["M-02"]!!.measurements)!!["endReason"])
    }

    @Test
    fun `EVERY chip tap on M-02 writes its answer and keeps the stored raw - every answer a run can leave times every answer a chip writes`() = runTest {
        val runAnswers = listOf(
            ManualStatuses.PASS to finishedGrid,
            ManualStatuses.FAIL to partialGrid,
            ManualStatuses.NOT_TESTED to partialGrid + ("endReason" to "user_marked_not_tested")
        )
        for ((held, raw) in runAnswers) for (tap in ManualStatuses.ALL) {
            val repo = MemoryResults()
            val row = Wired("M-02", viewModel(repo))
            row.actions.onDialogAnswer(held, raw)
            val rawBefore = repo.rows["M-02"]!!.measurements
            assertTrue("$held: the run's raw is stored", rawBefore != null)

            row.actions.onChip(tap)

            assertEquals("$held then $tap: the answer", tap, repo.rows["M-02"]!!.status)
            assertEquals("$held then $tap: the raw is untouched", rawBefore, repo.rows["M-02"]!!.measurements)
        }
    }

    @Test
    fun `taps in a row on M-02 never bring the raw back or lose it - pass then fail then unavailable then not-tested then pass, one raw throughout`() = runTest {
        val repo = MemoryResults()
        val row = Wired("M-02", viewModel(repo))
        row.actions.onDialogAnswer(ManualStatuses.PASS, finishedGrid)
        val rawBefore = repo.rows["M-02"]!!.measurements
        for (tap in listOf(ManualStatuses.FAIL, ManualStatuses.UNAVAILABLE, ManualStatuses.NOT_TESTED, ManualStatuses.PASS)) {
            row.actions.onChip(tap)
            assertEquals(tap, repo.rows["M-02"]!!.status)
            assertEquals(tap, rawBefore, repo.rows["M-02"]!!.measurements)
        }
    }

    @Test
    fun `a chip tap on an M-02 that holds no raw stores none - nothing is invented, whatever the answer`() = runTest {
        for (tap in ManualStatuses.ALL) {
            val repo = MemoryResults()
            val row = Wired("M-02", viewModel(repo))
            row.actions.onChip(tap)
            assertEquals(tap, repo.rows["M-02"]!!.status)
            assertNull(tap, repo.rows["M-02"]!!.measurements)
        }
    }

    @Test
    fun `a new grid run over a stored pass replaces the raw with its own`() = runTest {
        val repo = MemoryResults()
        val row = Wired("M-02", viewModel(repo))
        row.actions.onDialogAnswer(ManualStatuses.PASS, finishedGrid)
        val second = finishedGrid + ("elapsedMs" to 7001)
        row.actions.onDialogAnswer(ManualStatuses.PASS, second)
        assertEquals(7001L, MeasurementsJson.decode(repo.rows["M-02"]!!.measurements)!!["elapsedMs"])
    }

    @Test
    fun `the start button on M-02 still opens the grid and writes nothing`() = runTest {
        val repo = MemoryResults()
        val row = Wired("M-02", viewModel(repo))
        row.actions.onStartTest()
        assertEquals(listOf<String?>("M-02"), row.dialog)
        assertTrue("opening the grid is not an answer", repo.rows.isEmpty())
    }

    @Test
    fun `no other row's write reads the store, and its answer and column are what they were`() = runTest {
        val repo = MemoryResults()
        val vm = viewModel(repo)
        for (id in listOf("M-01", "M-04", "M-08", "M-12", "M-14", "M-15")) for (status in ManualStatuses.ALL) {
            Wired(id, vm).actions.onChip(status)
            assertEquals("$id/$status", status, repo.rows[id]!!.status)
            assertNull("$id/$status", repo.rows[id]!!.measurements)
        }
        assertEquals("only an M-02 pass tap reads the store", 0, repo.reads)
    }

    @Test
    fun `an M-02 chip tap reads the store once - a dialog answer with its own raw does not`() = runTest {
        val repo = MemoryResults()
        val row = Wired("M-02", viewModel(repo))
        for ((n, tap) in ManualStatuses.ALL.withIndex()) {
            row.actions.onChip(tap)
            assertEquals("tap $tap", n + 1, repo.reads)
        }
        val before = repo.reads
        row.actions.onDialogAnswer(ManualStatuses.PASS, finishedGrid)
        assertEquals("a run writes its own raw: nothing to look up", before, repo.reads)
    }

    // ---- the report editor's writer: the same rule, through the same function ----

    private fun entity(testId: String, status: String, measurements: String?) = FunctionalResultEntity(
        scanId = "scan", testId = testId, componentName = testId, categoryName = "manual", status = status, score = null,
        rawValue = null, rawValueUnit = null, notes = "manual", timestamp = 1L, measurements = measurements
    )

    @Test
    fun `withStoredRawKept carries the stored raw onto an M-02 entity with none, for every answer - the entity the report editor builds`() = runTest {
        for (held in listOf(ManualStatuses.PASS, ManualStatuses.FAIL)) for (tap in ManualStatuses.ALL) {
            val repo = MemoryResults()
            val raw = MeasurementsJson.encode(finishedGrid)
            repo.upsertResults(listOf(entity("M-02", held, raw)))

            val written = repo.withStoredRawKept(entity("M-02", tap, null))

            assertEquals("$held then $tap", raw, written.measurements)
            assertEquals("nothing else about the entity changes", entity("M-02", tap, raw), written)
        }
    }

    @Test
    fun `withStoredRawKept leaves every other entity as it is, with no read`() = runTest {
        val repo = MemoryResults()
        val raw = MeasurementsJson.encode(finishedGrid)
        repo.upsertResults(listOf(entity("M-02", ManualStatuses.PASS, raw)))
        val others = listOf(
            entity("M-02", ManualStatuses.PASS, MeasurementsJson.encode(partialGrid)), // a run's own raw
            entity("M-08", ManualStatuses.PASS, null),                       // another row
            entity("B-01", ManualStatuses.PASS, null)
        )
        for (e in others) assertEquals(e, repo.withStoredRawKept(e))
        assertEquals("none of them reads the store", 0, repo.reads)
    }

    @Test
    fun `both manual-answer writers apply the rule - the post-scan view model and the report editor's saveManualResults`() {
        val dir = listOf("app/src/main/java", "../app/src/main/java")
            .map { File(it, "net/neuralcheck/presentation/viewmodel") }
            .firstOrNull { it.isDirectory } ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")
        fun strip(s: String) = s.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") }
        val manual = strip(File(dir, "ManualTestsViewModel.kt").readText())
        val report = strip(File(dir, "ViewModels.kt").readText())

        val setResult = manual.substringAfter("fun setResult(").substringBefore("fun commitAndContinue(")
        assertTrue("setResult upserts through withStoredRawKept", Regex("""upsertResults\(\s*listOf\(\s*testResultRepository\.withStoredRawKept\(""").containsMatchIn(setResult))
        val save = report.substringAfter("fun saveManualResults(").substringBefore("class TechnicianViewModel")
        assertTrue("saveManualResults upserts through withStoredRawKept", Regex("""upsertResults\(\s*results\.map \{ testResultRepository\.withStoredRawKept\(it\) \}\s*\)""").containsMatchIn(save))
    }
}
