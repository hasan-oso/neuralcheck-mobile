package net.neuralcheck.domain.manual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What no unit test of [MicTestRecordings] can see: that the microphone dialog and the application start-up CALL it, and that nothing else spells the file name —
 * batch 2, item 5 (E28), pinned from the source text with comments removed (the technique `ManualRowSourceShapeTest` and `EvalCardLogSourceShapeTest` use).
 * Also pins that the two stale statements about the removed M-07 are gone from the comments they lived in (those are read from the raw text).
 */
class MicRecordingsSourceShapeTest {

    private val mainDir: File = listOf("app/src/main/java", "../app/src/main/java")
        .map { File(it, "net/neuralcheck") }
        .firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun raw(path: String): String = File(mainDir, path).readText()

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private fun code(path: String): String = squash(stripComments(raw(path)))

    @Test
    fun `the recording's name is spelled in exactly one place in main`() {
        val files = mainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue(files.size > 100)
        val spelling = files.filter { stripComments(it.readText()).contains("mic_test_") }.map { it.name }
        assertEquals(listOf("MicTestRecordings.kt"), spelling)
    }

    @Test
    fun `the dialog creates its file and deletes it through MicTestRecordings, and a failed delete is no longer swallowed`() {
        val dialogs = code("presentation/screen/ManualTestDialogs.kt")
        assertEquals(1, Regex("""MicTestRecordings\.newFile\(context\.cacheDir, System\.currentTimeMillis\(\)\)""").findAll(dialogs).count())
        assertEquals(1, Regex("""MicTestRecordings\.deleteAndLog\(file, log = \{ Log\.w\(MicTestRecordings\.LOG_TAG, it\) \}\)""").findAll(dialogs).count())
        assertFalse("the swallowing delete is gone", Regex("""file\.delete\(\)""").containsMatchIn(dialogs))
        assertFalse("no direct File(cacheDir, ...) for the recording", Regex("""(?<![A-Za-z])File\(context\.cacheDir""").containsMatchIn(dialogs))
    }

    @Test
    fun `the application sweeps the recordings at process start, on a background thread, and prints a failure of the sweep itself with its message text`() {
        val app = code("NeuralCheckApp.kt")
        assertEquals(1, Regex("""MicTestRecordings\.sweep\(cacheDir, log = \{ Log\.i\(MicTestRecordings\.LOG_TAG, it\) \}\)""").findAll(app).count())
        val sweepBlock = app.substringAfter("Thread({ runCatching { MicTestRecordings.sweep(").substringBefore(".start()")
        assertTrue("on its own daemon thread", sweepBlock.contains("\"NeuralCheck-MicSweep\"") && sweepBlock.contains("isDaemon = true"))
        assertTrue("a crash of the sweep prints its message text", sweepBlock.contains("mic-sweep-crashed message="))
    }

    @Test
    fun `the two stale statements about the removed M-07 are corrected`() {
        val reportScreen = raw("presentation/screen/ReportScreen.kt")
        val dialogs = raw("presentation/screen/ManualTestDialogs.kt")
        assertFalse("ReportScreen: M-07 is not a chip question", reportScreen.contains("M-07 (secondary mic) is now a yes/no chip question"))
        assertFalse("ManualTestDialogs: M-07 is not part of the microphone dialog", dialogs.contains("// M-06 / M-07 — Microphone test."))
        assertFalse("ManualTestDialogs: nothing passes CAMCORDER", dialogs.contains("Caller picks\n// audioSource: MIC (primary) or CAMCORDER (secondary)") || dialogs.contains("Caller picks\r\n// audioSource: MIC (primary) or CAMCORDER (secondary)"))
        assertFalse("ManualTestDialogs: the catalog comment's claim", dialogs.contains("Now a yes/no chip question"))
        // the facts those comments now rest on: no M-07 row, no M-07 dialog route
        assertFalse(code("presentation/screen/ReportScreen.kt").contains("\"M-07\""))
        assertFalse(code("presentation/screen/ManualTestDialogs.kt").contains("\"M-07\""))
    }
}
