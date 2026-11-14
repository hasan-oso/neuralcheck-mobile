package net.neuralcheck.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Batch 1.0.12, item 3 (user decision 2026-10-13): what can be pinned about the `NeuralCheck.ManualAnswer` lines from the
 * source text — a STATIC SCAN with comments removed, the technique `EvalCardLogSourceShapeTest` and `DiagnosticLoggingGuardTest`
 * use; it runs nothing. The lines' content is `ManualAnswerLogTest`; each exit's line is `ManualRowActionsTest` and
 * `ManualTestsViewModelAnswerLogTest`. This file pins the things a runtime test cannot see:
 *
 *  1. ONE tag literal in all of `app/src/main` (rule 24), in `ManualAnswerLog.kt`.
 *  2. WHERE the lines are called from — an allowlist of two files, none a composable: the row prints through
 *     `ManualRowActions`, the auto-fill through `ManualTestsViewModel`.
 *  3. The WRITERS: a manual answer row is built (`notes = "manual"`) in exactly three places, each named here. A fourth writer
 *     fails this test until it has its own line and a source, or the order's owner is told (order: "if a writer falls outside
 *     the three sources, report before inventing one").
 *  4. `ManualAnswerLog` prints through exactly one `Log.i` and never an `Enum.name`, a class name or a stack trace (R8 renames
 *     classes in the release build, so those would print letters).
 *  5. R8: nothing strips `android.util.Log` — pinned for the whole app by `EvalCardLogSourceShapeTest`; the 1.0.12 build block
 *     (the coordinator's) checks that this tag survives in the merged configuration.
 */
class ManualAnswerLogSourceShapeTest {

    private val mainSourceRoot: File = listOf("app/src/main/java", "../app/src/main/java")
        .map(::File)
        .firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private val sources: Map<String, String> by lazy {
        mainSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(mainSourceRoot).invariantSeparatorsPath to stripComments(it.readText()) }
    }

    private fun source(relative: String): String =
        sources[relative] ?: error("expected to find $relative under ${mainSourceRoot.path}")

    private val logPath = "net/neuralcheck/util/ManualAnswerLog.kt"

    @Test
    fun `the tag literal appears once in all of main, in ManualAnswerLog`() {
        val hits = sources.filter { it.value.contains("\"NeuralCheck.ManualAnswer\"") }.keys
        assertEquals(setOf(logPath), hits)
        assertEquals(1, Regex(Regex.escape("\"NeuralCheck.ManualAnswer\"")).findAll(source(logPath)).count())
    }

    @Test
    fun `ManualAnswerLog is called from exactly two files, none a composable`() {
        val callers = sources.filter { (path, text) ->
            path != logPath && Regex("""\bManualAnswerLog\b""").containsMatchIn(text)
        }.keys
        assertEquals(
            setOf(
                "net/neuralcheck/domain/manual/ManualRowActions.kt",
                "net/neuralcheck/presentation/viewmodel/ManualTestsViewModel.kt"
            ),
            callers
        )
    }

    @Test
    fun `a manual answer row is built in exactly three places - the report's editor and the view model's two`() {
        val writers = sources.mapValues { (_, text) -> Regex("""\bnotes\s*=\s*"manual"""").findAll(text).count() }.filterValues { it > 0 }
        assertEquals(
            mapOf(
                "net/neuralcheck/presentation/screen/ReportScreen.kt" to 1,
                "net/neuralcheck/presentation/viewmodel/ManualTestsViewModel.kt" to 2
            ),
            writers
        )
    }

    @Test
    fun `the view model's auto-fill prints its line after the rows are written, once per row`() {
        val vm = source("net/neuralcheck/presentation/viewmodel/ManualTestsViewModel.kt")
        val body = vm.substringAfter("fun commitAndContinue(")
        val write = body.indexOf("testResultRepository.upsertResults(entries)")
        val line = body.indexOf("ManualAnswerLog.answerWritten(")
        assertTrue("found both", write >= 0 && line >= 0)
        assertTrue("the line follows the write", line > write)
        assertEquals("one print site in the view model", 1, Regex("""ManualAnswerLog\.answerWritten\(""").findAll(vm).count())
        assertTrue(
            "the auto-fill line names its own source, auto_fill",
            Regex("""ManualAnswerLog\.WriteSource\.AUTO_FILL""").containsMatchIn(vm)
        )
        assertFalse("setResult prints nothing: the row that called it did", vm.substringAfter("fun setResult(").substringBefore("fun commitAndContinue(").contains("ManualAnswerLog"))
    }

    @Test
    fun `ManualAnswerLog prints through one Log_i, with no Enum name and no stack trace`() {
        val text = source(logPath)
        assertEquals("one Log call", 1, Regex("""\bLog\.""").findAll(text).count())
        assertTrue(Regex("""Log\.i\(TAG, it\)""").containsMatchIn(text))
        assertEquals("no `.name` at all: the wire strings are explicit", 0, Regex("""\.name\b""").findAll(text).count())
        assertFalse("no stack trace", Regex("""printStackTrace|getStackTrace|stackTrace|Throwable\(""").containsMatchIn(text))
        assertFalse("no class names in the lines", Regex("""\bsimpleName\b|javaClass""").containsMatchIn(text))
    }
}
