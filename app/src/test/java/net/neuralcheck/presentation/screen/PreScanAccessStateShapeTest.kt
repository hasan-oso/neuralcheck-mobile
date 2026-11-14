package net.neuralcheck.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The pre-scan screen answers every state the gate can end in - A-SUB wave 1, item 5. The screen's `LaunchedEffect(accessState)` is a `when` with an `else -> Unit`: a state the view model
 * gained and the screen did not name would fall into that `else` and the scan would NEVER start (a silent dead button). This pins, from the source text with comments removed (the technique of
 * `SessionScreensSourceShapeTest`), that every terminal state named in `PreScanViewModel.AccessState` has its own branch, and that `AllowedUnverified` starts the scan exactly like `Allowed`.
 * What the state IS and what it prints is `PreScanGateUnverifiedTest` (the real path).
 */
class PreScanAccessStateShapeTest {

    private val mainDir: File = listOf("app/src/main/java", "../app/src/main/java")
        .map { File(it) }
        .firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private fun source(path: String): String = squash(stripComments(File(mainDir, "net/neuralcheck/$path").readText()))

    private val viewModel by lazy { source("presentation/viewmodel/PreScanViewModel.kt") }
    private val screen by lazy { source("presentation/screen/PreScanScreen.kt") }

    @Test
    fun `every state the gate ends in is named by the screen - Idle and Checking are the only ones that fall to else`() {
        val body = viewModel.substringAfter("sealed class AccessState {").substringBefore("sealed interface Unverified")
        val states = Regex("""(?:data object|data class) (\w+)""").findAll(body).map { it.groupValues[1] }.toList()
        assertEquals(listOf("Idle", "Checking", "Allowed", "AllowedUnverified", "NeedsLogin", "NoScansRemaining"), states)
        val effect = screen.substringAfter("LaunchedEffect(accessState) { when (accessState) {").substringBefore("else -> Unit")
        for (state in states - setOf("Idle", "Checking")) {
            assertTrue("PreScanScreen's gate has no branch for $state - the scan would never start", effect.contains("PreScanViewModel.AccessState.$state"))
        }
    }

    @Test
    fun `AllowedUnverified starts the scan exactly like Allowed - one branch, no new text`() {
        assertEquals(
            "one shared branch",
            1,
            Regex("""is PreScanViewModel\.AccessState\.Allowed, is PreScanViewModel\.AccessState\.AllowedUnverified -> \{ viewModel\.consumeAccessState\(\) navController\.navigate\("active_scan/""").findAll(screen).count()
        )
    }
}
