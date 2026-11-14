package net.neuralcheck.presentation.screen

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.width
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.R
import net.neuralcheck.domain.manual.ManualUnavailableOffer
import net.neuralcheck.domain.model.ManualStatuses
import net.neuralcheck.presentation.theme.NeuralCheckTheme
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

/**
 * The layout test of the «unavailable» line (batch 2, item 1; completes 678d967, which left the layout un-tested).
 *
 * User decision 2026-10-15 (relayed by the coordinator): «اختبار التخطيط: Robolectric في السويت بشرطيك (التبعيات، والقياس الحقيقي للنصّ على
 * ويندوز)». The two conditions, and what proves each:
 *  1. The dependencies resolve: Robolectric, `ui-test-junit4` (test), `ui-test-manifest` (debug), `isIncludeAndroidResources` — this class
 *     compiles and runs against them.
 *  2. The text measurement is REAL on this host: `@GraphicsMode(NATIVE)` (Skia and HarfBuzz through Robolectric's native runtime; the
 *     default LEGACY mode measures nothing). [measurementIsReal] is the proof that stays in the suite — two texts of different length give widths
 *     in a sane ratio, and a long Arabic line wraps on 360 dp and does not wrap on 411 dp. A host where the measurement were a stub fails THAT
 *     test, loudly, before any layout assertion is trusted.
 *
 * What it renders: the REAL `ManualTestRow` (the composable the report screen builds) inside the app's own theme, for each offered row, at 360 dp
 * and 411 dp, in Arabic (the default resource set, right-to-left) and Turkish (`values-tr`, left-to-right). It asserts what the user's decision
 * asks of the line (`ManualUnavailableLineSpec`): at least [ManualUnavailableLineSpec.MIN_GAP_FROM_CHIPS_DP] below the chip row, outside every chip,
 * lower than a chip, and `selected` in the semantics when it is the row's answer.
 *
 * What it cannot see (named, not hidden): the theme's Tajawal font is a downloadable Google font — there is no provider here, so the text is measured
 * in the platform fallback font, not in Tajawal. The structural assertions (gap, no overlap, height, selected) do not depend on glyph widths; the
 * wrapping of the label on a real phone does, and stays a station-2 device check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35]) // = the app's targetSdk; Robolectric 4.14.1 runs SDK 35 on Java 21 (the JDK of the build command), and fails loudly on an older JDK
class ManualUnavailableLineLayoutTest {

    /**
     * Runs BEFORE the compose rule starts its activity (rule order 0): registers `ComponentActivity` with the package manager when the merged
     * manifest does not declare it. The DEBUG manifest declares it (`ui-test-manifest`); the RELEASE variant's does not, and the release unit tests
     * run the same classes — `addActivityIfNotPresent` makes both variants behave alike.
     */
    class RegisterComposeActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager)
                .addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }

    @get:Rule(order = 0) val registerActivity = RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int): String = rule.activity.getString(id)

    /** What the one composition shows. `setContent` runs once per test (the rule refuses a second call), so a row and an answer are swapped through state. */
    private var shown by mutableStateOf<Pair<String, String?>>("" to null)

    private fun showRow(testId: String, status: String?) {
        shown = testId to status
        rule.waitForIdle()
    }

    private fun setRowContent() {
        rule.setContent {
            NeuralCheckTheme {
                val (testId, status) = shown
                key(testId, status) {
                    val item = manualTestItems.first { it.id == testId }
                    ManualTestRow(item = item, currentStatus = status, onResult = { _, _, _ -> })
                }
            }
        }
    }

    private data class Measured(val chips: DpRect, val line: DpRect, val works: DpRect, val notWorks: DpRect, val notTested: DpRect, val root: DpRect)

    private fun measure(): Measured = Measured(
        chips = rule.onNodeWithTag(ManualUnavailableLineSpec.TAG_CHIPS).getBoundsInRoot(),
        line = rule.onNodeWithTag(ManualUnavailableLineSpec.TAG_LINE).getBoundsInRoot(),
        works = rule.onNodeWithText(string(R.string.manual_works)).getBoundsInRoot(),
        notWorks = rule.onNodeWithText(string(R.string.manual_not_works)).getBoundsInRoot(),
        notTested = rule.onNodeWithText(string(R.string.manual_not_tested_btn)).getBoundsInRoot(),
        root = rule.onRoot().getBoundsInRoot()
    )

    /** The rows the user decision names, taken from the one list (not retyped here). */
    private val offeredRows: List<String> get() = ManualUnavailableOffer.TEST_IDS

    private fun assertLayout(rtl: Boolean, screenWidthDp: Int) {
        shown = offeredRows.first() to null
        setRowContent()
        for (testId in offeredRows) {
            for (status in listOf<String?>(null, ManualStatuses.UNAVAILABLE)) {
                val at = "row $testId, answer ${status ?: "none"}, ${screenWidthDp}dp, ${if (rtl) "ar (rtl)" else "tr (ltr)"}"
                showRow(testId, status)
                val m = measure()
                assertEquals("$at: the screen the qualifiers built is not the one asked for (the root is ${m.root})", screenWidthDp.toFloat(), m.root.width.value, EPS)

                // 1. the line is at least the decided gap below the chip row ("حدّ السطر العلوي ≥ حدّ الصفّ السفلي + 16dp")
                val gap = m.line.top.value - m.chips.bottom.value
                assertTrue(
                    "$at: the line's top (${m.line.top.value}) is ${gap}dp below the chip row's bottom (${m.chips.bottom.value}); " +
                        "the decision is at least ${ManualUnavailableLineSpec.MIN_GAP_FROM_CHIPS_DP}dp",
                    gap >= ManualUnavailableLineSpec.MIN_GAP_FROM_CHIPS_DP - EPS
                )

                println("LAYOUT-PROOF $at: chips bottom=${m.chips.bottom.value} line top=${m.line.top.value} gap=${gap} line height=${m.line.height.value} chip height=${m.works.height.value} chip row height=${m.chips.height.value} line width=${m.line.width.value} screen=${m.root.width.value}")

                // 2. none of the three chips intersects the line
                for ((name, chip) in listOf("works" to m.works, "not-works" to m.notWorks, "not-tested" to m.notTested)) {
                    assertTrue("$at: the $name chip $chip intersects the line ${m.line}", !intersects(chip, m.line))
                }

                // 3. the line is lower than a chip ("أصغر")
                assertTrue(
                    "$at: the line (${m.line.height.value}dp) is not lower than a chip (${m.works.height.value}dp)",
                    m.line.height.value < m.works.height.value
                )

                // 4. it stays inside the screen and is not wider than it
                assertTrue("$at: the line ${m.line} leaves the ${screenWidthDp}dp screen", m.line.left.value >= -EPS && m.line.right.value <= screenWidthDp + EPS)

                // 5. the layout direction really applied: the first chip sits at the START edge of the chip row
                if (rtl) {
                    assertTrue("$at: rtl - the first chip ${m.works} is not at the right edge of ${m.chips}", m.chips.right.value - m.works.right.value <= EPS)
                } else {
                    assertTrue("$at: ltr - the first chip ${m.works} is not at the left edge of ${m.chips}", m.works.left.value - m.chips.left.value <= EPS)
                }

                // 6. `selected` is in the semantics exactly when the row's answer is unavailable
                val line = rule.onNodeWithTag(ManualUnavailableLineSpec.TAG_LINE)
                if (status == ManualStatuses.UNAVAILABLE) line.assertIsSelected() else line.assertIsNotSelected()
            }
        }
    }

    private fun intersects(a: DpRect, b: DpRect): Boolean =
        a.left.value < b.right.value - EPS && b.left.value < a.right.value - EPS &&
            a.top.value < b.bottom.value - EPS && b.top.value < a.bottom.value - EPS

    @Test
    @Config(qualifiers = "ar-ldrtl-w360dp-h800dp-xxhdpi")
    fun `arabic on 360 dp - the line is under the chip row by the decided gap, clear of the three chips, lower than a chip, selected in the semantics`() =
        assertLayout(rtl = true, screenWidthDp = 360)

    @Test
    @Config(qualifiers = "ar-ldrtl-w411dp-h800dp-xxhdpi")
    fun `arabic on 411 dp - the line is under the chip row by the decided gap, clear of the three chips, lower than a chip, selected in the semantics`() =
        assertLayout(rtl = true, screenWidthDp = 411)

    @Test
    @Config(qualifiers = "tr-w360dp-h800dp-xxhdpi")
    fun `turkish on 360 dp - the line is under the chip row by the decided gap, clear of the three chips, lower than a chip, selected in the semantics`() =
        assertLayout(rtl = false, screenWidthDp = 360)

    @Test
    @Config(qualifiers = "tr-w411dp-h800dp-xxhdpi")
    fun `turkish on 411 dp - the line is under the chip row by the decided gap, clear of the three chips, lower than a chip, selected in the semantics`() =
        assertLayout(rtl = false, screenWidthDp = 411)

    /**
     * Condition 2 of the decision, kept in the suite: the text measurement is real on this host.
     *  (a) Latin: 1, 10 and 20 letters give widths in the ratios a proportional font gives (10:1 within 8..12, 20:10 within 1.8..2.2) — a stub
     *      that returned a constant or zero fails.
     *  (b) Arabic really shapes: four joined «ب» are narrower than four isolated ones, and «لا» is narrower than «ل» + «ا» (a ligature) — a host
     *      that drew a missing-glyph box per character would give exactly 4x and exactly the sum.
     *  (c) a long Arabic line, built word by word from the approved text until it is wider than 365 dp on one line, wraps to more than one line on
     *      360 dp and stays on one line on 411 dp; the line counts come from the text layout itself (`onTextLayout`).
     * Size and density: the screen is 411 dp wide at xxhdpi, the texts are measured at the line's own size (labelSmall, 11 sp).
     */
    @Test
    @Config(qualifiers = "ar-ldrtl-w411dp-h900dp-xxhdpi")
    fun measurementIsReal() {
        val words = string(R.string.manual_mark_unavailable).split(" ").filter { it.isNotBlank() }
        val oneLine = mutableMapOf<String, Float>()
        val lines = mutableMapOf<String, Int>()
        val fontSize = 11.sp

        // (c) the candidates: prefixes of the approved sentence repeated; the first whose single-line width passes 365 dp is the long line.
        // Built up front and measured in one composition.
        val pool = generateSequence { words }.flatten().take(60).toList()
        val growTags = (1..pool.size).map { "cand-$it" }
        val grown = (1..pool.size).map { pool.take(it).joinToString(" ") }
        val latin = listOf("a" to "a", "a10" to "a".repeat(10), "a20" to "a".repeat(20))
        val arabic = listOf(
            "beh1" to "ب", "beh4" to "بببب", "lam" to "ل", "alef" to "ا", "lamalef" to "لا",
            "beh-isolated-x4" to "ب ب ب ب"
        )
        var wrapPhase by mutableStateOf<String?>(null)  // null: measure every text on one line; else the long text, wrapped in two boxes
        rule.setContent {
            val wrapText = wrapPhase
            Column {
                if (wrapText == null) {
                    (latin + arabic + growTags.zip(grown)).forEach { (tag, text) ->
                        Text(text, fontSize = fontSize, softWrap = false, modifier = Modifier.testTag("w-$tag"))
                    }
                } else {
                    Text(wrapText, fontSize = fontSize, modifier = Modifier.width(360.dp).testTag("c360"), onTextLayout = { lines["c360"] = it.lineCount })
                    Text(wrapText, fontSize = fontSize, modifier = Modifier.width(411.dp).testTag("c411"), onTextLayout = { lines["c411"] = it.lineCount })
                }
            }
        }
        rule.waitForIdle()
        for ((tag, _) in latin + arabic + growTags.zip(grown)) {
            oneLine[tag] = rule.onNodeWithTag("w-$tag").getBoundsInRoot().width.value
        }

        // (a)
        val w1 = oneLine.getValue("a"); val w10 = oneLine.getValue("a10"); val w20 = oneLine.getValue("a20")
        assertTrue("1 letter measured ${w1}dp", w1 > 0f)
        assertTrue("10 letters ($w10) vs 1 letter ($w1): ratio ${w10 / w1}", w10 / w1 in 8f..12f)
        assertTrue("20 letters ($w20) vs 10 letters ($w10): ratio ${w20 / w10}", w20 / w10 in 1.8f..2.2f)

        // (b)
        val beh1 = oneLine.getValue("beh1"); val beh4 = oneLine.getValue("beh4")
        assertTrue("four joined beh (${beh4}dp) are not narrower than four times an isolated one (${4 * beh1}dp): no shaping", beh4 < 4 * beh1 - 1f)
        val lam = oneLine.getValue("lam"); val alef = oneLine.getValue("alef"); val lamalef = oneLine.getValue("lamalef")
        assertTrue("lam-alef (${lamalef}dp) is not narrower than lam (${lam}dp) + alef (${alef}dp): no ligature", lamalef < lam + alef - 1f)

        // (c)
        val wrapIndex = growTags.indices.first { oneLine.getValue(growTags[it]) > 365f }
        val text = grown[wrapIndex]
        val single = oneLine.getValue(growTags[wrapIndex])
        assertTrue("the candidate (${single}dp on one line) is not narrower than 411 dp - the words are too wide to land between the two screens", single < 405f)
        // the same text in a 360 dp box and in a 411 dp box
        wrapPhase = text
        rule.waitForIdle()
        assertTrue("the Arabic line (${single}dp wide on one line) did not wrap on 360 dp: ${lines["c360"]} line(s)", (lines["c360"] ?: 0) > 1)
        assertEquals("the Arabic line (${single}dp wide on one line) wrapped on 411 dp", 1, lines["c411"])
        println("MEASUREMENT-PROOF latin: 1=${w1} 10=${w10} 20=${w20} (10/1=${w10 / w1}, 20/10=${w20 / w10}) ; arabic: beh1=$beh1 beh4=$beh4 (4x=${4 * beh1}) lam=$lam alef=$alef lamalef=$lamalef; wrap: ${wrapIndex + 1} words, one line=${single}dp, lines at 360dp=${lines["c360"]}, at 411dp=${lines["c411"]}")
    }

    private companion object {
        /** Compose lays out in whole pixels; at xxhdpi (3 px per dp) a bound is exact to a third of a dp. */
        const val EPS = 0.51f
    }
}
