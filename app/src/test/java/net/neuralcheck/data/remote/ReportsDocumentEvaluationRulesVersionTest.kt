package net.neuralcheck.data.remote

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.InputStreamReader

/**
 * Batch 1.0.12, second part (rule 39, rule 25): the server added ONE root key to the `reports/{scanId}` document — `evaluationRulesVersion` — in
 * `neuralcheck-backend@c2e5e49` (`92ec041`), and the six committed document samples were re-copied byte for byte
 * (`app/src/test/resources/contract/README.md`, the 2026-10-13 section).
 *
 * The user's decision (2026-10-13, relayed in the order, not verifiable from this repository): the app does NOT display it. So the app has no
 * reader for it, and this test says so instead of leaving it silent:
 *  1. each of the six samples carries the key at the root, as a non-blank string — the copy is the new one (this fails on the 6fa408d copies);
 *  2. nothing in `app/src/main` mentions `evaluationRulesVersion` (a static source scan, comments removed) — "unread" is declared with its source, and a
 *     reader added later is a decision that has to change this test.
 * No test here pins the VALUE: the server rewrites it with its rules, and a second copy of it would drift (rule 28).
 */
class ReportsDocumentEvaluationRulesVersionTest {

    private val samples = listOf(
        "v7-reports-document-sample.json",
        "v7-reports-document-sample-honor.json",
        "v7-reports-document-sample-security-downgrade.json",
        "v7-reports-document-sample-cpu-c06-downgrade.json",
        "v7-reports-document-sample-zero-measured.json",
        "v7-reports-document-sample-zero-measured-battery-harness-error.json"
    )

    @Test
    fun `all six document samples carry evaluationRulesVersion at the root, as a non-blank string`() {
        for (name in samples) {
            val stream = javaClass.classLoader!!.getResourceAsStream("contract/$name") ?: error("contract/$name not found on the test classpath")
            val root = InputStreamReader(stream, Charsets.UTF_8).use { JsonParser.parseReader(it) }.asJsonObject
            assertTrue("$name has no root key evaluationRulesVersion", root.has("evaluationRulesVersion"))
            val value = root.get("evaluationRulesVersion")
            assertTrue("$name: evaluationRulesVersion is not a string", value.isJsonPrimitive && value.asJsonPrimitive.isString)
            assertFalse("$name: evaluationRulesVersion is blank", value.asString.isBlank())
        }
    }

    @Test
    fun `the app reads it nowhere - declared unread by the user's decision, no reader was added`() {
        val mainRoot = listOf("app/src/main/java", "../app/src/main/java").map(::File).firstOrNull { it.isDirectory }
            ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")
        val readers = mainRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.filter { file ->
            val code = file.readText()
                .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
                .lines().joinToString("\n") { it.substringBefore("//") }
            code.contains("evaluationRulesVersion")
        }.map { it.relativeTo(mainRoot).invariantSeparatorsPath }.toList()
        assertEquals("evaluationRulesVersion is unread by decision (2026-10-13: not displayed); a reader is a new decision", emptyList<String>(), readers)
    }
}
