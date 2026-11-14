package net.neuralcheck.presentation.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Order 2026-09-26 (ملحق القرار ١٠٤ البند ٢). Exercises [SingleFlightGuard]
 * directly — the real class `ReportViewModel.loadScan` now uses
 * (ViewModels.kt, `loadGuard`), not a copy — because a literal
 * `ReportViewModel.loadScan()` end-to-end test is structurally blocked in
 * this repo: `ReportViewModel`'s constructor requires a real
 * `FirebaseAnalysisService`, whose `functions: FirebaseFunctions =
 * Firebase.functions` field initializer runs eagerly at construction and
 * throws with no `FirebaseApp` present in a plain JVM unit test; the class
 * is declared `final` with no `mock-maker-inline` Mockito config in this
 * repo (confirmed absent — `find . -iname
 * "org.mockito.plugins.MockMaker"` found nothing), so mocking around it
 * is not available either. Disclosed rather than silently substituted —
 * same category of limitation as ملحق القرار ١٠٤ البند ١'s "no
 * ReportViewModel test harness exists yet" note.
 *
 * "نقرتان متتاليتان تبدآن تحميلاً واحداً" is proven at the level this IS
 * testable: [tryStart] returns true exactly once per in-flight window,
 * including under REAL concurrent coroutines racing for it (not just two
 * sequential calls on one thread, which would trivially pass even without
 * the guard doing anything).
 *
 * On "the first load's result must not be replaced" (the verifier's added
 * concern, from `AiScoringEngine.kt:263-309` writing an ERROR
 * `ScoringResult` for a rejected concurrent second call): this follows as
 * a structural COROLLARY of what is proven here, not a separately
 * dynamically-tested claim — if at most one guarded body can ever be
 * running at a time (proven by the concurrency test below), there is by
 * construction no second `loadScan` body left alive to write a
 * conflicting `_scoringResult`/Room row over the first's. Stated
 * explicitly rather than left implicit.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SingleFlightGuardTest {

    @Test
    fun `tryStart succeeds when nothing is running`() {
        val guard = SingleFlightGuard()
        assertTrue(guard.tryStart())
    }

    @Test
    fun `a second tryStart while the first has not finished is rejected`() {
        val guard = SingleFlightGuard()
        assertTrue("first call must be admitted", guard.tryStart())
        assertFalse(
            "second call must be ignored while the first is still in flight — " +
                "this is the exact 'two clicks, one load' requirement",
            guard.tryStart()
        )
    }

    @Test
    fun `after finish, a new tryStart succeeds again`() {
        val guard = SingleFlightGuard()
        assertTrue(guard.tryStart())
        guard.finish()
        assertTrue(
            "releasing the guard must allow a genuinely NEW load (e.g. a " +
                "later, deliberate retry after the first one completed)",
            guard.tryStart()
        )
    }

    @Test
    fun `finish is idempotent — calling it twice does not corrupt state`() {
        val guard = SingleFlightGuard()
        assertTrue(guard.tryStart())
        guard.finish()
        guard.finish()
        assertTrue(guard.tryStart())
    }

    /**
     * The real "two rapid clicks" scenario: two coroutines both attempt
     * `tryStart` before either has a chance to call `finish` — mirrors
     * `loadScan`'s actual shape (`tryStart` gate, `viewModelScope.launch`
     * body, `finish` in the body's `finally`). Uses a
     * [StandardTestDispatcher] (both coroutines queued, neither runs until
     * `advanceUntilIdle`/explicit resume) plus a [CompletableDeferred] gate
     * so BOTH launches reach their `tryStart()` call before either
     * resumes past it — a real race window, not just two sequential
     * calls on one thread that would pass trivially even with a
     * do-nothing guard.
     */
    @Test
    fun `two coroutines racing tryStart before either finishes — exactly one wins, exactly one load proceeds`() = runTest {
        val guard = SingleFlightGuard()
        val gate = CompletableDeferred<Unit>()
        val results = mutableListOf<Boolean>()
        val loadsThatActuallyRan = java.util.concurrent.atomic.AtomicInteger(0)

        suspend fun simulateLoadScan() {
            if (!guard.tryStart()) {
                synchronized(results) { results.add(false) }
                return
            }
            synchronized(results) { results.add(true) }
            try {
                gate.await() // both coroutines park here until released together
                loadsThatActuallyRan.incrementAndGet()
            } finally {
                guard.finish()
            }
        }

        val job1 = launch { simulateLoadScan() }
        val job2 = launch { simulateLoadScan() }
        // Let both coroutines run up to their suspension point (gate.await())
        // — job1 must have already called tryStart() (and, if admitted,
        // reached gate.await()) before job2 gets scheduled, since this
        // dispatcher runs queued coroutines to their next suspension point
        // one at a time, not truly in parallel — this IS the real race
        // loadScan faces from two near-simultaneous button taps on the
        // Main-immediate dispatcher, not a weaker approximation of it.
        testScheduler.runCurrent()

        assertEquals(
            "exactly one of the two racing calls must have been admitted",
            1,
            results.count { it }
        )
        assertEquals(
            "exactly one of the two racing calls must have been rejected",
            1,
            results.count { !it }
        )

        gate.complete(Unit)
        job1.join()
        job2.join()

        assertEquals(
            "exactly one simulated load must have actually run its body — " +
                "the second click never got a chance to overwrite anything " +
                "the first one produces",
            1,
            loadsThatActuallyRan.get()
        )
        assertTrue(
            "after both finish, the guard must be free again for a genuinely new load",
            guard.tryStart()
        )
    }
}
