package net.neuralcheck.presentation.screen

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Order 2026-09-30 (أمر «مسار الشريك، والنصوص، وإصلاح الخادم»، البند ٢):
 * ScanPermissionsGate() must live in PreScanScreen.kt (the one composable
 * both the Home-launched scan and the joined-session scan route through),
 * NOT in HomeScreen.kt (which a joined session never composes at all —
 * JoinSessionScreen.kt navigates straight to "pre_scan/general/none" on
 * success and never touches "home").
 *
 * ── القاعدة ٣٧: what this test actually exercises ──
 * This is a STATIC SOURCE SCAN, not an executed navigation test — it reads
 * the .kt files as text and checks for the exact route strings and call
 * sites, the same technique this repo already uses in
 * hygiene/DiagnosticLoggingGuardTest.kt. It does NOT run NavHost, does NOT
 * compose any screen, and does NOT click through JoinSessionScreen or Home
 * in a running app. It cannot catch a route string that is technically
 * present but unreachable, or a gate call that is present but dead code.
 * When this was written the unit-test setup lacked Robolectric / androidx.compose.ui.test
 * dependency, so an executed-navigation test was not possible under
 * testLaunchDebugUnitTest (both are test dependencies since 3284cca; no navigation test exists) — flagged as a known gap in the task report
 * rather than silently declared "tested".
 */
class ScanPermissionsGatePlacementTest {

    private val mainSourceRoot: File = listOf(
        "app/src/main/java",
        "../app/src/main/java"
    ).map(::File).firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun sourceText(relativePath: String): String {
        val file = File(mainSourceRoot, relativePath)
        assertTrue("expected to find $relativePath under $mainSourceRoot", file.isFile)
        return file.readText()
    }

    @Test
    fun `PreScanScreen calls ScanPermissionsGate exactly once`() {
        val text = sourceText("net/neuralcheck/presentation/screen/PreScanScreen.kt")
        val callSites = Regex("""(?<!fun )ScanPermissionsGate\s*\(\s*\)""").findAll(text).count()
        assertTrue(
            "expected PreScanScreen.kt to call ScanPermissionsGate() exactly once as a side effect, found $callSites",
            callSites == 1
        )
        assertTrue(
            "expected PreScanScreen.kt to also define ScanPermissionsGate itself",
            text.contains("private fun ScanPermissionsGate()")
        )
    }

    @Test
    fun `HomeScreen no longer calls or defines ScanPermissionsGate (no duplicate request)`() {
        val text = sourceText("net/neuralcheck/presentation/screen/HomeScreen.kt")
        // A `//` comment MENTIONING "ScanPermissionsGate()" (e.g. pointing
        // to where it moved) must not fail this — only an actual call site
        // (a line that is not a comment) counts as a duplicate request.
        val nonCommentLines = text.lineSequence()
            .map { it.substringBefore("//") }
            .joinToString("\n")
        assertFalse(
            "HomeScreen.kt must not call ScanPermissionsGate() outside a comment — " +
                "PreScanScreen is the single call site",
            nonCommentLines.contains("ScanPermissionsGate()")
        )
        assertFalse(
            "HomeScreen.kt must not define ScanPermissionsGate — it was moved to PreScanScreen.kt",
            nonCommentLines.contains("fun ScanPermissionsGate")
        )
    }

    @Test
    fun `HomeScreen's scan-start CTA routes to pre_scan`() {
        val text = sourceText("net/neuralcheck/presentation/screen/HomeScreen.kt")
        assertTrue(
            "expected Home's start-scan action to navigate into the pre_scan route",
            text.contains("""navController.navigate("pre_scan/general/none")""")
        )
    }

    @Test
    fun `JoinSessionScreen's successful join routes to pre_scan too - through the consent route function, which says pre_scan while the consent screen is off - not to home`() {
        val text = sourceText("net/neuralcheck/presentation/screen/JoinSessionScreen.kt")
        // A-P6-12 (rule 23: this test proved the join navigates to the literal route; since the consent screen the join goes through `afterJoinRoute`, and `ConsentGuardTest` pins both join sites)
        assertTrue(
            "expected a resolved join-session to navigate by the one routing function, with the consent guard",
            text.contains("""navController.navigate(afterJoinRoute(CONSENT_TEXTS_APPROVED, consentPart))""")
        )
        // ق15 (rule 23: REPLACES `afterJoinRoute(CONSENT_TEXTS_APPROVED)`): the route also depends on the shop's part - and for every shop but one with the part on, and for every answer that did not carry it,
        // the function leads into the pre_scan route, as the join always did
        for (part in listOf(net.neuralcheck.domain.consent.ConsentPartRead.NotEnabled, net.neuralcheck.domain.consent.ConsentPartRead.Absent)) {
            assertTrue("part=$part leads into the pre_scan route", net.neuralcheck.presentation.navigation.afterJoinRoute(true, part) == "pre_scan/general/none")
        }
        // Rule 23: REPLACES `while the consent screen is off that function leads into the pre_scan route` (it pinned the consent guard false): the guard is true since 2026-10-23, and a shop that has the part
        // is led to the consent screen, which leaves for pre_scan only when the server recorded the consent or said there is none to record
        assertTrue(
            "and with the consent screen live, a shop that has the part is led to the consent route",
            CONSENT_TEXTS_APPROVED && net.neuralcheck.presentation.navigation.afterJoinRoute(CONSENT_TEXTS_APPROVED, net.neuralcheck.domain.consent.ConsentPartRead.Enabled) == "consent"
        )
        assertFalse(
            "a joined session must not route through home — that would make the " +
                "HomeScreen-only gate placement (the pre-order-٢ bug) look correct " +
                "again by accident",
            text.contains("""navController.navigate("home")""")
        )
    }

    @Test
    fun `both scan-start routes registered in the nav graph point at PreScanScreen`() {
        val text = sourceText("net/neuralcheck/presentation/navigation/NeuralCheckNavigation.kt")
        assertTrue(
            "expected the pre_scan route to compose PreScanScreen",
            Regex("""route\s*=\s*"pre_scan/\{scanType}/\{linkedScanId}"[\s\S]{0,600}PreScanScreen\(""")
                .containsMatchIn(text)
        )
    }
}
