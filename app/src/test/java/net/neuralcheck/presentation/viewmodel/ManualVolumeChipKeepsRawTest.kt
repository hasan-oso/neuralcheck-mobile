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
import net.neuralcheck.domain.manual.ManualRowActions
import net.neuralcheck.domain.manual.TouchGridRecorder
import net.neuralcheck.domain.manual.VolumeKeyRecorder
import net.neuralcheck.domain.model.ManualStatuses
import net.neuralcheck.util.ManualAnswerLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * The user's decision of 2026-10-21 (after the device round on the APK of f1a38be) for the two volume rows: «M-09 وM-10 (رفع وخفض الصوت): «يعمل» لا يفتح
 * الحوار، كما M-02. قرار M7(ب) يسري عليهما» (the user's words), and the order's own line that follows from it: «نقرات الشرائح الأربع (E21-3) على M-09/M-10 تكتب جوابها
 * وتُبقي الخامّ المخزَّن إن وُجد كما M-02». This
 * REPLACES the behaviour of user approval E22 of 2026-10-15 («يعمل» opens the dialog), which the tests of commit 7d38eb2 pinned and which are rewritten
 * (`ManualChipActionsTest`, `ManualRowActionsTest`) — rule 23.
 *
 * Proved over the REAL path, as `ManualM02WorksTapKeepsRawTest` does for M-02 — [ManualRowActions] (the row's chip and start-button exits) into the REAL
 * [ManualTestsViewModel.setResult] over an in-memory [TestResultRepository] (the Room DAO is the only layer replaced) — and with the raws the REAL
 * [VolumeKeyRecorder] produces from scripted key events (rule 37: no stand-in for the producer), for EVERY value that can reach the row (rule 30):
 *  - every way a volume run can end ([VolumeKeyRecorder.EndReasons] — the detection by key event AND by level change, the technician's «يعمل», «لا يعمل»,
 *    «لم أختبر», and the dismissal) times every chip a tap can write ([ManualStatuses.ALL]: works, not works, not tested, unavailable), on M-09 and on M-10:
 *    the tap writes its answer and the stored raw stays byte for byte;
 *  - the tap on a fresh row stores `pass` with the column NULL and opens nothing; a tap on a row with no raw invents none;
 *  - a new run replaces the raw with its own; «ابدأ الاختبار» still opens the dialog and writes nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManualVolumeChipKeepsRawTest {

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

    /** One way a volume run can end: what the real recorder is fed, and the end reason (and detection source) it is asked to write. */
    private class Run(val name: String, val endReason: String, val detectionSource: String?, val keys: Boolean)

    private val runs = listOf(
        Run("detected by the key event", VolumeKeyRecorder.EndReasons.AUTO_DETECTED, VolumeKeyRecorder.DetectionSources.KEY_EVENT, keys = true),
        Run("detected by the level moving", VolumeKeyRecorder.EndReasons.AUTO_DETECTED, VolumeKeyRecorder.DetectionSources.VOLUME_LEVEL_CHANGE, keys = false),
        Run("claimed working", VolumeKeyRecorder.EndReasons.USER_MARKED_WORKING, null, keys = false),
        Run("marked not working", VolumeKeyRecorder.EndReasons.USER_MARKED_NOT_WORKING, null, keys = true),
        Run("marked not tested", VolumeKeyRecorder.EndReasons.USER_MARKED_NOT_TESTED, null, keys = false),
        Run("dismissed after a key event", VolumeKeyRecorder.EndReasons.DISMISSED, null, keys = true)
    )

    /** The raw the REAL [VolumeKeyRecorder] writes for [run] on [testId] over scripted key events and a scripted clock. */
    private fun rawOf(testId: String, run: Run, windowMs: Long = 2_900L): Map<String, Any?> {
        val open = 9_000_000L
        var uptime = open
        val rec = VolumeKeyRecorder(VolumeKeyRecorder.expectedKeyCodeFor(testId), { uptime })
        if (run.keys) {
            val key = VolumeKeyRecorder.expectedKeyCodeFor(testId)
            rec.onKeyEvent(key, true, open + 2_310, 0, 5, 257)
            rec.onKeyEvent(key, false, open + 2_410, 0, 5, 257)
        }
        uptime = open + windowMs
        return rec.measurements(run.endReason, run.detectionSource, VolumeKeyRecorder.VolumeLevels(15, 7, 8))
    }

    /** What the dialog hands up for [run]: the answer the run records and its raw (a dismissal records the answer the test already holds). */
    private fun answerOf(run: Run, raw: Map<String, Any?>, held: String?): Pair<String, Map<String, Any?>> =
        VolumeKeyRecorder.statusFor(run.endReason)?.let { it to raw }
            ?: checkNotNull(TouchGridRecorder.recordOnDismiss(held, raw)) { "a dismissal after a key event records the run" }

    private val volumeRows = listOf(VolumeKeyRecorder.TEST_ID_UP, VolumeKeyRecorder.TEST_ID_DOWN)

    @Test
    fun `the works chip on a fresh M-09 and M-10 stores pass with the column NULL and opens nothing`() = runTest {
        for (id in volumeRows) {
            val repo = MemoryResults()
            val row = Wired(id, viewModel(repo))
            row.actions.onChip(ManualStatuses.PASS)

            val stored = repo.rows[id]!!
            assertEquals(id, ManualStatuses.PASS, stored.status)
            assertEquals(id, 100f, stored.score)
            assertNull("$id: a tap is not a run: no raw", stored.measurements)
            assertTrue("$id: the tap opens nothing", row.dialog.isEmpty())
        }
    }

    @Test
    fun `EVERY chip tap on M-09 and M-10 writes its answer and keeps the stored raw - every way a volume run ends times every answer a chip writes`() = runTest {
        for (id in volumeRows) for (run in runs) for (tap in ManualStatuses.ALL) {
            val repo = MemoryResults()
            val row = Wired(id, viewModel(repo))
            val (held, raw) = answerOf(run, rawOf(id, run), held = ManualStatuses.FAIL)
            row.actions.onDialogAnswer(held, raw)
            val rawBefore = repo.rows[id]!!.measurements
            assertNotNull("$id / ${run.name}: the run's raw is stored", rawBefore)
            assertEquals("$id / ${run.name}: the raw says how the run ended", run.endReason, MeasurementsJson.decode(rawBefore)!![VolumeKeyRecorder.Keys.END_REASON])

            row.actions.onChip(tap)

            assertEquals("$id / ${run.name} then $tap: the answer", tap, repo.rows[id]!!.status)
            assertEquals("$id / ${run.name} then $tap: the raw is untouched, byte for byte", rawBefore, repo.rows[id]!!.measurements)
            assertTrue("$id / ${run.name} then $tap: opens nothing (only the run's own close is a call)", row.dialog == listOf<String?>(null))
        }
    }

    @Test
    fun `taps in a row on M-09 and M-10 never bring the raw back or lose it - pass then fail then unavailable then not-tested then pass, one raw throughout`() = runTest {
        for (id in volumeRows) {
            val repo = MemoryResults()
            val row = Wired(id, viewModel(repo))
            val run = runs.first()
            row.actions.onDialogAnswer(VolumeKeyRecorder.statusFor(run.endReason)!!, rawOf(id, run))
            val rawBefore = repo.rows[id]!!.measurements
            for (tap in listOf(ManualStatuses.FAIL, ManualStatuses.UNAVAILABLE, ManualStatuses.NOT_TESTED, ManualStatuses.PASS)) {
                row.actions.onChip(tap)
                assertEquals("$id $tap", tap, repo.rows[id]!!.status)
                assertEquals("$id $tap", rawBefore, repo.rows[id]!!.measurements)
            }
        }
    }

    @Test
    fun `a chip tap on an M-09 or M-10 that holds no raw stores none - nothing is invented, whatever the answer`() = runTest {
        for (id in volumeRows) for (tap in ManualStatuses.ALL) {
            val repo = MemoryResults()
            val row = Wired(id, viewModel(repo))
            row.actions.onChip(tap)
            assertEquals("$id $tap", tap, repo.rows[id]!!.status)
            assertNull("$id $tap", repo.rows[id]!!.measurements)
        }
    }

    @Test
    fun `a new volume run over a stored answer replaces the raw with its own - a run is always the newest word`() = runTest {
        for (id in volumeRows) {
            val repo = MemoryResults()
            val row = Wired(id, viewModel(repo))
            row.actions.onDialogAnswer(ManualStatuses.PASS, rawOf(id, runs[0]))
            val first = repo.rows[id]!!.measurements
            val second = rawOf(id, runs[3], windowMs = 7_001L)
            row.actions.onDialogAnswer(VolumeKeyRecorder.statusFor(runs[3].endReason)!!, second)
            assertEquals(id, ManualStatuses.FAIL, repo.rows[id]!!.status)
            assertEquals(id, 7_001L, MeasurementsJson.decode(repo.rows[id]!!.measurements)!![VolumeKeyRecorder.Keys.WINDOW_MS])
            assertTrue("$id: it is not the first run's raw", repo.rows[id]!!.measurements != first)
        }
    }

    @Test
    fun `the start button on M-09 and M-10 still opens the dialog and writes nothing`() = runTest {
        for (id in volumeRows) {
            val repo = MemoryResults()
            val row = Wired(id, viewModel(repo))
            row.actions.onStartTest()
            assertEquals(id, listOf<String?>(id), row.dialog)
            assertTrue("$id: opening the dialog is not an answer", repo.rows.isEmpty())
        }
    }

    @Test
    fun `an M-09 or M-10 chip tap reads the store once - a dialog answer with its own raw does not`() = runTest {
        for (id in volumeRows) {
            val repo = MemoryResults()
            val row = Wired(id, viewModel(repo))
            for ((n, tap) in ManualStatuses.ALL.withIndex()) {
                row.actions.onChip(tap)
                assertEquals("$id tap $tap", n + 1, repo.reads)
            }
            val before = repo.reads
            row.actions.onDialogAnswer(ManualStatuses.PASS, rawOf(id, runs[0]))
            assertEquals("$id: a run writes its own raw: nothing to look up", before, repo.reads)
        }
    }

    @Test
    fun `a dismissed volume run after a key event keeps working through the same path - the answer the test holds, with the run's raw, then a chip tap keeps that raw`() = runTest {
        for (id in volumeRows) {
            val repo = MemoryResults()
            val row = Wired(id, viewModel(repo))
            row.actions.onChip(ManualStatuses.FAIL)                         // the technician's answer is held: fail, no raw
            row.actions.onRunDismissed(ManualStatuses.FAIL, rawOf(id, runs.last()))
            val rawBefore = repo.rows[id]!!.measurements
            assertEquals(id, ManualStatuses.FAIL, repo.rows[id]!!.status)
            assertEquals(id, VolumeKeyRecorder.EndReasons.DISMISSED, MeasurementsJson.decode(rawBefore)!![VolumeKeyRecorder.Keys.END_REASON])
            row.actions.onChip(ManualStatuses.PASS)
            assertEquals(id, ManualStatuses.PASS, repo.rows[id]!!.status)
            assertEquals(id, rawBefore, repo.rows[id]!!.measurements)
        }
    }
}
