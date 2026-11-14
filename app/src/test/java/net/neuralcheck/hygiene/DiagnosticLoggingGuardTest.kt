package net.neuralcheck.hygiene

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Build-breaking guard: temporary field-diagnosis logging must never reach
 * a shippable tree.
 *
 * ── Why a test and not a comment or a git hook ──
 *
 * A "DO NOT MERGE" line in a commit body is documentation: `git merge` does
 * not read it. A git hook is local, is never cloned, and `pre-merge-commit`
 * does not fire at all on a fast-forward merge — the exact case where a
 * diagnostic branch slips in unnoticed. A test ships WITH the repository,
 * runs in the suite we execute before every merge and push, and fails the
 * build rather than printing a warning somebody scrolls past.
 *
 * Mechanism: if a diagnostic branch is merged, its logging lands in
 * `app/src/main` and this test turns red on the merged tree.
 *
 * ── If you are deliberately ON a diagnostic branch ──
 *
 * This test failing is the CORRECT outcome: it is the tree telling you it
 * is not shippable. Use the branch, read the logs, then throw the branch
 * away — do not "fix" this test to make the suite green.
 *
 * Known diagnostic branch kept on purpose: `diag/eval-card-visibility`
 * (tag NC.EvalDiag) — instrumentation for "the technician evaluation card
 * does not appear on an entitled QR session". Root cause found (the
 * joinScanSession function was not deployed); the branch is retained as a
 * ready-made probe if a similar field report recurs.
 */
class DiagnosticLoggingGuardTest {

    /** Log tags that only ever exist to diagnose a live incident. */
    private val forbiddenTags = listOf(
        "NC.EvalDiag",
        "EVAL-DIAG",
        "NC.Diag"
    )

    private val mainSourceRoot: File = listOf(
        "app/src/main/java",
        "../app/src/main/java"
    ).map(::File).firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun kotlinSources(): List<File> =
        mainSourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `the guard can see the sources it is meant to protect`() {
        // Without this, a path change would make every assertion below pass
        // vacuously and the guard would silently stop guarding.
        val sources = kotlinSources()
        assertTrue(
            "expected to find main Kotlin sources under ${mainSourceRoot.path}",
            sources.size > 50
        )
    }

    @Test
    fun `no diagnostic log tag survives in shippable sources`() {
        val offenders = mutableListOf<String>()
        for (file in kotlinSources()) {
            val text = file.readText()
            for (tag in forbiddenTags) {
                // Skip this guard's own declaration of the tags.
                if (file.name == "DiagnosticLoggingGuardTest.kt") continue
                if (text.contains(tag)) {
                    val line = text.lineSequence()
                        .withIndex()
                        .firstOrNull { it.value.contains(tag) }
                        ?.let { it.index + 1 } ?: 0
                    offenders += "${file.relativeTo(mainSourceRoot).path}:$line  ($tag)"
                }
            }
        }
        assertTrue(
            buildString {
                appendLine("Temporary diagnostic logging found in app/src/main:")
                offenders.forEach { appendLine("  - $it") }
                appendLine()
                appendLine("This tree is NOT shippable. If a diagnostic branch was merged,")
                appendLine("revert that merge. If you are intentionally working ON the")
                appendLine("diagnostic branch, this failure is expected — use the branch,")
                appendLine("then discard it. Do not weaken this guard to go green.")
            },
            offenders.isEmpty()
        )
    }

    @Test
    fun `a stack trace is never built just to log a call site`() {
        // The eval-diag probe built `Throwable("...")` inside
        // ScanSessionHolder.clear() on every call purely to capture a call
        // site. fillInStackTrace on a hot path is a real cost, and it is a
        // reliable fingerprint of diagnostic code that escaped.
        //
        // Matched per FILE, not per line: the real call spreads the Log
        // invocation and the Throwable argument across four lines, so a
        // same-line check passed vacuously when this guard was first
        // written (caught by running it against a deliberately merged
        // diagnostic branch — see `guard detects a constructed Throwable`).
        // A constructed-but-not-thrown Throwable is the signal; `throw
        // Throwable(...)` is ordinary control flow and is allowed.
        val offenders = kotlinSources()
            .filter { it.name != "DiagnosticLoggingGuardTest.kt" }
            .filter { constructsUnthrownThrowable(stripComments(it.readText())) }
            .map { it.relativeTo(mainSourceRoot).path }

        assertTrue(
            "Throwable constructed without being thrown (diagnostic " +
                "call-site capture) in: $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun `guard detects a constructed Throwable across multiple lines`() {
        // Locks the bug this guard shipped with on its first draft.
        val multiLine = """
            android.util.Log.i(
                "SOME.TAG",
                "message",
                Throwable("call site")
            )
        """.trimIndent()
        assertTrue(constructsUnthrownThrowable(multiLine))

        // And does not fire on ordinary throwing or on a comment.
        assertTrue(!constructsUnthrownThrowable("""throw Throwable("boom")"""))
        assertTrue(!constructsUnthrownThrowable(stripComments("""// Throwable("x")""")))
    }

    private fun stripComments(source: String): String = source
        .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
        .lines()
        .joinToString(" ") { it.substringBefore("//") }

    /** True when `Throwable("` appears without an immediately preceding
     *  `throw`, i.e. the object is built to be READ rather than raised. */
    private fun constructsUnthrownThrowable(code: String): Boolean =
        Regex("""Throwable\s*\(\s*"""").findAll(code).any { match ->
            val before = code.substring(0, match.range.first).trimEnd()
            !before.endsWith("throw")
        }
}
