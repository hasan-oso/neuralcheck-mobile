package net.neuralcheck.presentation.screen

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.domain.consent.ConsentLocale
import net.neuralcheck.domain.consent.ConsentLocaleRead
import net.neuralcheck.domain.pair.DeviceNameRead
import net.neuralcheck.domain.pair.PairCandidate
import net.neuralcheck.domain.pair.PairFailureKind
import net.neuralcheck.domain.repeat.TextRead
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import net.neuralcheck.presentation.viewmodel.PairConfirmUi
import net.neuralcheck.presentation.viewmodel.PairFailure
import net.neuralcheck.presentation.viewmodel.PairListUi
import net.neuralcheck.presentation.viewmodel.PairListing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.ZoneId
import java.util.Locale

/**
 * The pair list laid out on different screens - order of 2026-10-22 (the technique of `ManualUnavailableLineLayoutTest`: the real composable in the app's theme, the screen the qualifiers build, bounds read
 * back). The labels are the APPROVED texts (rows ص9 to ص20 of `TEXTS-FOR-APPROVAL-MAINTENANCE-PAIRS-2026-10-22.md`, approved by Hasan on 2026-10-22, Arabic and Turkish), read from the resources of the locale each
 * test sets ([pairLabelsOf]), with the longest marketing names. The screen pads its content by 24 dp on each side (`GenerateSessionScreen`): so does the host here.
 *
 * What is asserted on 360 dp and 411 dp, in Arabic (rtl) and Turkish (ltr), in every state that draws something: everything stays inside the padded width; the rows are stacked in the order given, none intersecting
 * another, each as wide as the content; the prompt is above the first row and the confirm button below the last, as wide as the content. What it cannot see (named, not hidden): the theme's font is a downloadable
 * Google font with no provider here, so the text is measured in the platform fallback; a wrap on a real phone stays a device check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PairListLayoutTest {

    /** As `PairListSurfaceTest.RegisterComposeActivity`. */
    class RegisterComposeActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }

    @get:Rule(order = 0) val registerActivity = RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    /** The APPROVED texts (Hasan, 2026-10-22), as the screen reads them: from the resources of the locale this test's `@Config` sets. REPLACES the test's own copy of the proposals (rule 23, rule 24). */
    private fun labelsFor(@Suppress("UNUSED_PARAMETER") arabic: Boolean): PairLabels = pairLabelsOf(ApplicationProvider.getApplicationContext<Application>().resources)

    private fun candidate(handle: String, longName: String, at: Long) =
        PairCandidate(handle, DeviceNameRead.Mapped(TextRead.Text(longName), TextRead.Text(longName), TextRead.Text(longName), TextRead.Text("SM-X")), at)

    private val listing = PairListing(
        72,
        listOf(
            candidate("h1", "سامسونج جالاكسي إس ٢١ ألترا فايف جي — ١٢ جيجابايت", 1_760_000_000_000L),
            candidate("h2", "Samsung Galaxy S21 Ultra 5G (SM-G998B/DS) 12 GB", 1_760_054_400_000L),
            candidate("h3", "Xiaomi Redmi Note 11 Pro+ 5G India Edition", 1_760_100_000_000L)
        )
    )

    private var ui by mutableStateOf<PairListUi>(PairListUi.NotShown)

    private fun show(arabic: Boolean) {
        val environment = PairRowEnvironment(ConsentLocaleRead.Known(if (arabic) ConsentLocale.AR else ConsentLocale.TR), ZoneId.of("Europe/Istanbul"), if (arabic) Locale("ar") else Locale("tr"))
        rule.setContent {
            NeuralCheckTheme {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    PairListSection(ui = ui, labels = labelsFor(arabic), actions = PairListActions(onSelect = {}, onConfirm = {}), environment = environment)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun bounds(vararg parts: String): DpRect = rule.onNodeWithTag(pairTag(*parts)).getBoundsInRoot()

    private fun intersects(a: DpRect, b: DpRect): Boolean =
        a.left.value < b.right.value - EPS && b.left.value < a.right.value - EPS && a.top.value < b.bottom.value - EPS && b.top.value < a.bottom.value - EPS

    private fun assertLayout(rtl: Boolean, screenWidthDp: Int) {
        show(arabic = rtl)
        val failure = PairFailure(PairFailureKind.WRONG_DEFECT_TYPE, "BEFORE_WRONG_DEFECT_TYPE")
        val states = listOf(
            "empty" to PairListUi.Empty(72),
            "load failed" to PairListUi.LoadFailed(failure),
            "choosing, nothing selected" to PairListUi.Choosing(listing, null, PairConfirmUi.Idle),
            "choosing, one selected" to PairListUi.Choosing(listing, "h2", PairConfirmUi.Idle),
            "choosing, confirm in flight" to PairListUi.Choosing(listing, "h2", PairConfirmUi.InFlight("h2")),
            "choosing, confirm refused" to PairListUi.Choosing(listing, "h2", PairConfirmUi.Failed(failure)),
            "linked" to PairListUi.Linked(listing, listing.candidates[1])
        )
        val left = PAD
        val right = screenWidthDp - PAD
        for ((name, state) in states) {
            ui = state
            rule.waitForIdle()
            val at = "$name, ${screenWidthDp}dp, ${if (rtl) "ar (rtl)" else "tr (ltr)"}"
            val root = rule.onRoot().getBoundsInRoot()
            assertEquals("$at: the screen the qualifiers built is not the one asked for (the root is $root)", screenWidthDp.toFloat(), root.width.value, EPS)
            val drawn = mutableListOf<Pair<String, DpRect>>()
            for (parts in listOf(listOf("empty"), listOf("failure"), listOf("prompt"), listOf("linked"), listOf("confirmFailure"), listOf("confirm"))) {
                if (rule.onAllNodesWithTagCount(pairTag(*parts.toTypedArray())) > 0) drawn += parts.joinToString("_") to bounds(*parts.toTypedArray())
            }
            val rows = (0..2).filter { rule.onAllNodesWithTagCount(pairTag("row", it.toString())) > 0 }.map { "row_$it" to bounds("row", it.toString()) }
            for ((tagName, b) in drawn + rows) {
                assertTrue("$at: $tagName $b leaves the padded width [$left, $right]", b.left.value >= left - EPS && b.right.value <= right + EPS)
            }
            // rows: stacked in order, none intersecting another, each as wide as the content
            for ((tagName, b) in rows) assertTrue("$at: $tagName is not as wide as the content: $b", b.width.value >= right - left - EPS)
            for (i in 0 until rows.size - 1) {
                assertTrue("$at: ${rows[i].first} and ${rows[i + 1].first} intersect or are out of order", rows[i + 1].second.top.value >= rows[i].second.bottom.value - EPS)
            }
            // the prompt above the first row; the confirm button below the last, as wide as the content
            if (rows.isNotEmpty() && rule.onAllNodesWithTagCount(pairTag("prompt")) > 0) {
                assertTrue("$at: the prompt is not above the first row", bounds("prompt").bottom.value <= rows.first().second.top.value + EPS)
            }
            if (rows.isNotEmpty() && rule.onAllNodesWithTagCount(pairTag("confirm")) > 0) {
                val confirm = bounds("confirm")
                assertTrue("$at: the confirm button is not below the last row", confirm.top.value >= rows.last().second.bottom.value - EPS)
                assertTrue("$at: the confirm button is not as wide as the content: $confirm", confirm.width.value >= right - left - EPS)
                for ((tagName, b) in rows) assertTrue("$at: the button intersects $tagName", !intersects(confirm, b))
            }
            println("LAYOUT-PROOF $at: drawn=${(drawn + rows).map { it.first }} width=${root.width.value}")
        }
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTagCount(tag: String): Int =
        onAllNodes(androidx.compose.ui.test.hasTestTag(tag)).fetchSemanticsNodes().size

    @Test
    @Config(qualifiers = "ar-ldrtl-w360dp-h800dp-xxhdpi")
    fun `arabic on 360 dp - every state stays inside the padded width, the rows are stacked, the button is below them`() = assertLayout(rtl = true, screenWidthDp = 360)

    @Test
    @Config(qualifiers = "ar-ldrtl-w411dp-h800dp-xxhdpi")
    fun `arabic on 411 dp - every state stays inside the padded width, the rows are stacked, the button is below them`() = assertLayout(rtl = true, screenWidthDp = 411)

    @Test
    @Config(qualifiers = "tr-w360dp-h800dp-xxhdpi")
    fun `turkish on 360 dp - every state stays inside the padded width, the rows are stacked, the button is below them`() = assertLayout(rtl = false, screenWidthDp = 360)

    @Test
    @Config(qualifiers = "tr-w411dp-h800dp-xxhdpi")
    fun `turkish on 411 dp - every state stays inside the padded width, the rows are stacked, the button is below them`() = assertLayout(rtl = false, screenWidthDp = 411)

    private companion object {
        const val PAD = 24f
        const val EPS = 0.5f
    }
}
