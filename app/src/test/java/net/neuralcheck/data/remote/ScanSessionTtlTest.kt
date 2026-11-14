package net.neuralcheck.data.remote

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Order 2026-10-15 (batch item 3; rule 22: "no default value fills an absence on a path that reaches a displayed text").
 *
 * [ScanSession.expiresInMinutes] defaulted to 15, and `GenerateSessionViewModel.State.expiresInMinutes` too, so an answer
 * of `createScanSession` that did not carry the lifetime drew a "15:00" countdown on the owner's screen - while the
 * server's lifetime is 60 minutes (relayed: createScanSession.js:29, not verified from this repository). Now the lifetime
 * is `Int?`: absent stays absent, the view model says so in the log, and the screen draws a countdown only for a lifetime
 * the server named.
 *
 * What is run here: the DTO through Gson, the way the service builds it from the callable's answer (the real class; the
 * service itself needs a FirebaseApp and cannot be built on the JVM). What is NOT run: the screen. `GenerateSessionScreen`
 * is a composable (no test renders its countdown, though Robolectric and a Compose test rule exist since 3284cca), so that it draws no countdown for a null lifetime is pinned as text, comments
 * removed - it proves the branch is there, not what a phone draws.
 *
 * Rule 23: no earlier test pinned the default (`git grep -n "expiresInMinutes" -- app/src/test` was empty).
 */
class ScanSessionTtlTest {

    private val gson = Gson()

    @Test
    fun `an answer that carries the lifetime keeps it - under each key name the DTO accepts`() {
        assertEquals(60, gson.fromJson("""{"sessionId":"s","code":"NC-1","expiresInMinutes":60}""", ScanSession::class.java).expiresInMinutes)
        assertEquals(45, gson.fromJson("""{"session_id":"s","code":"NC-1","expires_in_minutes":45}""", ScanSession::class.java).expiresInMinutes)
        assertEquals(30, gson.fromJson("""{"sessionId":"s","code":"NC-1","expiresIn":30}""", ScanSession::class.java).expiresInMinutes)
    }

    @Test
    fun `an answer that does not carry the lifetime leaves it ABSENT - not 15`() {
        val parsed = gson.fromJson("""{"sessionId":"s","code":"NC-1"}""", ScanSession::class.java)
        assertNull("no default fills the absence", parsed.expiresInMinutes)
        assertEquals("the other fields are untouched", "NC-1", parsed.code)

        assertNull(
            "a JSON null is absent too",
            gson.fromJson("""{"sessionId":"s","code":"NC-1","expiresInMinutes":null}""", ScanSession::class.java).expiresInMinutes
        )
    }

    // ---- the call sites, as text ----

    private val mainSourceRoot: File = listOf("app/src/main/java", "../app/src/main/java")
        .map(::File).firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private fun source(relative: String): String =
        squash(stripComments(File(mainSourceRoot, relative).readText()))

    @Test
    fun `no 15 default is left on the lifetime - the view model state and the screen draw a countdown only for a named lifetime`() {
        val dto = source("net/neuralcheck/data/remote/FirebaseAnalysisService.kt")
        assertTrue(dto.contains("val expiresInMinutes: Int? = null"))
        assertFalse("the DTO default is gone", Regex("""expiresInMinutes: Int = 15""").containsMatchIn(dto))

        val viewModels = source("net/neuralcheck/presentation/viewmodel/ViewModels.kt")
        assertTrue(viewModels.contains("val expiresInMinutes: Int? = null,"))
        assertFalse("the state default is gone", Regex("""expiresInMinutes: Int = 15""").containsMatchIn(viewModels))
        assertTrue(
            "an absent lifetime is declared in the log",
            viewModels.contains("if (session.expiresInMinutes == null) { Log.w(\"NeuralCheck\", \"createScanSession: the answer carried no expiresInMinutes - no countdown is shown\") }")
        )

        val screen = source("net/neuralcheck/presentation/screen/GenerateSessionScreen.kt")
        assertEquals("one countdown CALL (the definition takes `totalMinutes: Int`)", 1, Regex("""\bSessionCountdown\(totalMinutes = """).findAll(screen).count())
        assertTrue(
            "the countdown is drawn only for a named lifetime",
            screen.contains("state.expiresInMinutes?.let { totalMinutes -> Spacer(Modifier.height(14.dp)) SessionCountdown(totalMinutes = totalMinutes) }")
        )
    }
}
