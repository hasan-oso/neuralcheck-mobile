package net.neuralcheck.domain.model

import com.google.gson.Gson
import net.neuralcheck.data.remote.model.AiCategoryScore
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Whether an `aiAnalysisJson`/similar Room-cached row written before a
 * field existed crashes when rehydrated and used the way
 * [AiAnalysisResult.isComplete] / `categoryScoresFromCachedAi`
 * (ViewModels.kt) actually use those fields — resolved empirically, by
 * running real Gson deserialization against real JSON strings (never a
 * Kotlin-constructor-built object, which never goes through Gson's
 * instantiation path and would prove nothing), not by reasoning about
 * Gson's internals.
 *
 * Mechanism, as CONFIRMED by this file's own test runs (not assumed):
 * this project's Gson constructs a class via `Unsafe`/Objenesis whenever
 * that class has no accessible no-arg Java constructor — which, for a
 * Kotlin data class, requires EVERY constructor parameter to carry a
 * default (Kotlin does not synthesize a no-arg overload just because
 * some parameters default; `@JvmOverloads`' no-arg overload is reachable
 * only when ALL parameters default). `Unsafe` allocation skips the
 * constructor entirely, so a JSON key absent from the source leaves that
 * field truly null (or 0/false for a primitive) — for a class where every
 * parameter defaults, Gson's fallback still ends up calling a real
 * constructor and Kotlin's declared defaults come through correctly.
 * Confirmed by running this file BEFORE any fix:
 *   - AiAnalysisResult, AiCategoryScore (data/remote/model) — every
 *     constructor parameter already had a default: NO crash, fields
 *     came back as their Kotlin defaults, not null.
 *   - domain CategoryScore (ScoringResult.kt) — six parameters (name,
 *     nameAr, score, grade, passCount, failCount, totalCount minus the
 *     one with a default) had NO default: a JSON fixture omitting
 *     name/grade threw a real NullPointerException on `.isNotBlank()`
 *     inside this test.
 * Fixed by giving `CategoryScore` `@JvmOverloads` + a default on every
 * parameter (ScoringResult.kt) — this file's
 * `domain CategoryScore ...` test below now asserts the SAME scenario
 * that used to throw now returns the declared defaults instead.
 *
 * AiAnalysisResponse: checked by design, not by field-level test — every
 * field on this DTO is already nullable (see its class doc: this exact
 * mechanism is WHY), so there is no non-null reference field to expose
 * the risk in the first place. Empirically confirmed anyway that the
 * class as a whole deserializes an empty object without throwing.
 *
 * ScoringResult, AxisScore, IndividualScore: NOT checked by test — grep
 * of app/src/main for `fromJson` targeting any of the three found zero
 * call sites; none of them is ever a Gson deserialization target in this
 * codebase, so the mechanism cannot manifest for them regardless of
 * their field shapes.
 */
class AiAnalysisResultGsonNullBypassTest {

    private val gson = Gson()

    // ---- AiAnalysisResult: JSON missing every non-null reference field ----

    @Test
    fun `AiAnalysisResult deserialized from JSON missing all fields does not NPE when isComplete reads it`() {
        // The exact scenario: an old aiAnalysisJson row, or one written by
        // a future version of this class that drops a field this version
        // still declares. isComplete() is exactly what ReportViewModel
        // calls on a rehydrated cache row (ViewModels.kt's aiHasContent).
        val rehydrated = gson.fromJson("{}", AiAnalysisResult::class.java)
        assertEquals(false, rehydrated.isComplete())
    }

    @Test
    fun `AiAnalysisResult string fields come back as their Kotlin defaults, not null, when absent from JSON`() {
        val rehydrated = gson.fromJson("""{"grade":"B"}""", AiAnalysisResult::class.java)
        assertEquals("", rehydrated.summary)
        assertEquals("", rehydrated.details)
        assertEquals("", rehydrated.verdict)
    }

    @Test
    fun `AiAnalysisResult list and map fields come back empty, not null, when absent from JSON`() {
        val rehydrated = gson.fromJson("""{"grade":"B"}""", AiAnalysisResult::class.java)
        assertEquals(emptyList<Any>(), rehydrated.issues)
        assertEquals(emptyList<Any>(), rehydrated.predictedProblems)
        assertEquals(emptyMap<String, String>(), rehydrated.categoryNotes)
    }

    // ---- AiCategoryScore: nested inside AiAnalysisResult.categoryScores,
    // deserialized the SAME reflective way when the outer object is ----

    @Test
    fun `AiCategoryScore nested in AiAnalysisResult gets its Kotlin defaults when the category JSON omits grade and note`() {
        val json = """{"categoryScores":{"gpu":{"score":80}}}"""
        val rehydrated = gson.fromJson(json, AiAnalysisResult::class.java)
        val gpu = rehydrated.categoryScores!!.getValue("gpu")
        assertEquals("?", gpu.grade)
        assertEquals("", gpu.note)
    }

    @Test
    fun `AiCategoryScore deserialized standalone gets its Kotlin defaults when the JSON is nearly empty`() {
        val cat = gson.fromJson("""{"score":80}""", AiCategoryScore::class.java)
        assertEquals("?", cat.grade)
        assertEquals("", cat.note)
    }

    // ---- AiAnalysisResponse: every field nullable by design; confirm the
    // class as a whole still deserializes an empty object without throwing ----

    @Test
    fun `AiAnalysisResponse deserialized from an empty JSON object does not throw`() {
        val r = gson.fromJson("{}", net.neuralcheck.data.remote.model.AiAnalysisResponse::class.java)
        assertEquals(null, r.summary)
    }

    // ---- domain CategoryScore (ScoringResult.kt) — the ONE class this
    // file proved broken, now fixed with @JvmOverloads + defaults ----

    @Test
    fun `domain CategoryScore gets its Kotlin defaults, not a NullPointerException, when required fields are missing from JSON`() {
        // Before the @JvmOverloads fix, this exact fixture threw a real
        // NullPointerException on `cat.name.isNotBlank()` — CategoryScore
        // had six required (no-default) constructor parameters, so Gson
        // had no accessible no-arg constructor and fell back to Unsafe,
        // which skips the constructor (and every Kotlin default) entirely.
        val cat = gson.fromJson(
            """{"score":80,"passCount":4,"totalCount":5}""",
            net.neuralcheck.domain.scoring.CategoryScore::class.java
        )
        assertEquals("", cat.name)
        assertEquals("", cat.nameAr)
        assertEquals("?", cat.grade)
        assertEquals("", cat.note)
        assertEquals(0, cat.failCount)
        // Fields actually present in the JSON still deserialize correctly
        // — the fix only changes what happens to ABSENT keys.
        assertEquals(80f, cat.score, 0.001f)
        assertEquals(4, cat.passCount)
        assertEquals(5, cat.totalCount)
    }
}
