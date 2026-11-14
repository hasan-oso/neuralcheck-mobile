package net.neuralcheck.hardware.gps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Batch 1.0.12 (second part), rule 24: a literal on which a distinction stands has ONE exported source, not a copy per file.
 *
 * `locationUpdatesState` is `requested` | `request_failed:<text>` | `not_requested:gnss_callback_registration_failed`
 * (docs/FV68_CONTRACT.md section 7-b, item 7). The third value was a literal inside `GpsTests.testSatellites` while its two siblings
 * were constants of [L02Measurements] — a copy that a rename of one could leave behind. It is a constant now, and this scan (a STATIC
 * SOURCE SCAN of `app/src/main` with comments removed, the technique `EvalCardLogSourceShapeTest` uses) keeps every distinctive
 * L-02 state literal in the one file that declares it. The short common words (`registered`, `requested`, `measured`) are not scanned: they
 * occur in unrelated strings; the three that are unmistakable are.
 *
 * Behaviour is untouched: the wire value is pinned by `GpsTestsL02Test` (`a failed callback registration does not start the engine…`),
 * which still spells the literal on purpose — a test must say what goes on the wire independently of the constant it checks.
 */
class L02StateLiteralsGuardTest {

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

    private val declaringFile = "net/neuralcheck/hardware/gps/GnssObservation.kt"

    /** The distinctive literals, each with the constant that declares it. */
    private val literals = mapOf(
        "not_requested:gnss_callback_registration_failed" to L02Measurements.UPDATES_NOT_REQUESTED_CALLBACK_REGISTRATION_FAILED,
        "absent:no_first_fix_in_window" to L02Measurements.TTFF_ABSENT_NO_FIRST_FIX,
        "registration_failed:" to L02Measurements.CALLBACK_REGISTRATION_FAILED_PREFIX,
        "request_failed:" to L02Measurements.UPDATES_REQUEST_FAILED_PREFIX
    )

    @Test
    fun `each distinctive L-02 state literal is spelled once in all of main, in the file that declares it`() {
        assertTrue("the guard sees the sources", sources.size > 50 && declaringFile in sources)
        for ((literal, constant) in literals) {
            assertEquals("the constant is the literal", literal, constant)
            val quoted = "\"" + literal + "\""
            val hits = sources.filter { it.value.contains(quoted) }.mapValues { (_, text) -> Regex(Regex.escape(quoted)).findAll(text).count() }
            assertEquals(
                "`$literal` must be spelled once, in $declaringFile — everything else names the L02Measurements constant (rule 24). Found: $hits",
                mapOf(declaringFile to 1), hits
            )
        }
    }

    @Test
    fun `GpsTests names the constant for the registration-failed state`() {
        val gps = sources.getValue("net/neuralcheck/hardware/gps/GpsTests.kt")
        assertTrue(gps.contains("L02Measurements.UPDATES_NOT_REQUESTED_CALLBACK_REGISTRATION_FAILED"))
    }

    @Test
    fun `the value is the one the contract lists`() {
        assertEquals("not_requested:gnss_callback_registration_failed", L02Measurements.UPDATES_NOT_REQUESTED_CALLBACK_REGISTRATION_FAILED)
    }
}
