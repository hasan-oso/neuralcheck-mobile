package net.neuralcheck.presentation.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the graph, the report and the login view model do that no unit test can run (composables and a NavController) - pinned from the source text with comments removed, the technique
 * `SessionScreensSourceShapeTest` uses. A-SUB wave 1, user decision 1 «P1». The behaviour behind them is run by `AnonymousNavigationTest` (a real NavHost) and `AnonymousRoutingTest`
 * (the decisions); this file proves the graph APPLIES them, and that `FirebaseUser.isAnonymous` is read in exactly one place.
 */
class AnonymousRoutingSourceShapeTest {

    private val mainDir: File = listOf("app/src/main/java", "../app/src/main/java")
        .map { File(it) }
        .firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private val allMain: Map<String, String> by lazy {
        mainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(mainDir).path.replace('\\', '/') to stripComments(it.readText()) }
    }

    private fun source(path: String): String = squash(allMain.getValue("net/neuralcheck/$path"))

    private val graph by lazy { source("presentation/navigation/NeuralCheckNavigation.kt") }

    @Test
    fun `the graph's start destination is the decision's, and the Firebase user is read through AuthManager kindOf`() {
        assertEquals(
            "one start decision",
            1,
            Regex("""val startDestination = startDestinationFor\(BuildConfig\.REQUIRE_SUBSCRIPTION, AuthManager\.kindOf\(Firebase\.auth\.currentUser\)\)""").findAll(graph).count()
        )
        assertFalse("the old inline decision is gone", graph.contains("""if (Firebase.auth.currentUser != null) "home" else "login""""))
    }

    /** `composable("route") {` or `composable(route = "route", ...) {`: each route pattern of the graph with the text of its body. */
    private fun routeBodies(): Map<String, String> {
        val out = linkedMapOf<String, String>()
        val starts = Regex("""composable\(\s*(?:route = )?"([^"]+)"""").findAll(graph).toList()
        for ((i, m) in starts.withIndex()) {
            val end = if (i + 1 < starts.size) starts[i + 1].range.first else graph.length
            out[m.groupValues[1]] = graph.substring(m.range.first, end)
        }
        return out
    }

    @Test
    fun `every route of the closed set is wrapped in ClosedToAnonymous, and no other route is`() {
        val bodies = routeBodies()
        assertTrue("found the graph's routes: ${bodies.keys}", bodies.size >= 20)
        for (route in ROUTES_CLOSED_TO_ANONYMOUS) {
            val body = bodies[route] ?: error("the closed set names a route the graph does not have: $route")
            assertEquals("$route is wrapped once", 1, Regex("""ClosedToAnonymous\(navController, authKindNow\(\)\)""").findAll(body).count())
        }
        for ((route, body) in bodies) {
            if (route !in ROUTES_CLOSED_TO_ANONYMOUS) assertFalse("$route is not in the closed set and must not be wrapped", body.contains("ClosedToAnonymous("))
        }
        assertEquals("the graph wraps exactly the closed set", ROUTES_CLOSED_TO_ANONYMOUS.size, Regex("""ClosedToAnonymous\(navController""").findAll(graph).count())
    }

    @Test
    fun `the kind is read when each entry is composed, from the one reading, with the datacollect flavor unrestricted`() {
        assertTrue(
            graph.contains("""private fun authKindNow(): AuthKind = effectiveAuthKind(BuildConfig.REQUIRE_SUBSCRIPTION, AuthManager.kindOf(Firebase.auth.currentUser))""")
        )
    }

    @Test
    fun `the report's new scan button is the routing's decision, and Firebase isAnonymous is read in one file only`() {
        val report = source("presentation/screen/ReportScreen.kt")
        assertEquals(1, Regex("""navController\.startNewScan\(AuthManager\.kindOf\(Firebase\.auth\.currentUser\)\)""").findAll(report).count())
        assertFalse("the report no longer pushes home itself", Regex("""navController\.navigate\("home"\)""").containsMatchIn(report))

        val readers = allMain.filter { (_, text) -> Regex("""\.isAnonymous\b""").containsMatchIn(text) }.keys
        assertEquals(
            "the Firebase flag is read by AuthManager alone; everyone else asks AuthManager (rule 24)",
            setOf("net/neuralcheck/data/auth/AuthManager.kt", "net/neuralcheck/presentation/viewmodel/LoginViewModel.kt"),
            readers
        )
        // LoginViewModel's read is AuthManager's property, not the Firebase flag
        assertEquals(1, Regex("""authManager\.isLoggedIn && !authManager\.isAnonymous""").findAll(source("presentation/viewmodel/LoginViewModel.kt")).count())
        assertEquals("AuthManager reads the Firebase flag once", 1, Regex("""user\.isAnonymous""").findAll(source("data/auth/AuthManager.kt")).count())
    }
}
