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
import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import net.neuralcheck.presentation.viewmodel.PurposeFailure
import net.neuralcheck.presentation.viewmodel.PurposeSelection
import net.neuralcheck.presentation.viewmodel.PurposeUi
import net.neuralcheck.presentation.viewmodel.typeRequested
import net.neuralcheck.presentation.viewmodel.accepted
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
 * The chips and the list of fault types, laid out on different screens - order of 2026-10-22 (the technique of `ManualUnavailableLineLayoutTest`: the real composable in the app's theme, the screen the
 * qualifiers build, bounds read back). The labels are the APPROVED texts (rows ص1 to ص8 and ص21 of `TEXTS-FOR-APPROVAL-MAINTENANCE-PAIRS-2026-10-22.md`, approved by Hasan on 2026-10-22, Arabic and Turkish), read from
 * the resources of the locale each test sets ([purposeChipLabelsOf]). The screen pads its content by 24 dp on each side (`GenerateSessionScreen`): so does the host here.
 *
 * What is asserted on 360 dp and 411 dp, in Arabic (rtl) and Turkish (ltr), for each of the chips and the five types: everything stays inside the padded width; the three chips are side by side and do not
 * intersect; the list of types starts below the chips; the five types are stacked in order, none intersecting another, each as wide as the content; and the direction really applied (the first chip is at the START
 * edge). What it cannot see (named, not hidden): the theme's font is a downloadable Google font, with no provider here, so the text is measured in the platform fallback; a wrap on a real phone stays a device check.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SessionPurposeChipsLayoutTest {

    /** As `SessionPurposeChipsTest.RegisterComposeActivity`: the release variant's manifest does not declare `ComponentActivity`. */
    class RegisterComposeActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }

    @get:Rule(order = 0) val registerActivity = RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    /** The APPROVED texts (Hasan, 2026-10-22), as the screen reads them: from the resources of the locale this test's `@Config` sets. REPLACES the test's own copy of the proposals (rule 23, rule 24). */
    private fun labelsFor(@Suppress("UNUSED_PARAMETER") arabic: Boolean) = purposeChipLabelsOf(ApplicationProvider.getApplicationContext<Application>().resources)

    /** The shop with BOTH parts (Q31). REPLACES `PurposeUi.Offered()`, which offered the maintenance chips for `purposes` alone (rule 23). */
    private val offeredBoth = PurposeUi.Offered(maintenanceOffered = true)

    private var offered by mutableStateOf(offeredBoth)
    private var locked by mutableStateOf(false)

    private fun show(arabic: Boolean) {
        rule.setContent {
            NeuralCheckTheme {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
                    SessionPurposeChips(offered = offered, locked = locked, labels = labelsFor(arabic), onSelect = {}, onSelectType = {})
                }
            }
        }
        rule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = rule.onNodeWithTag(tag).getBoundsInRoot()

    private fun intersects(a: DpRect, b: DpRect): Boolean =
        a.left.value < b.right.value - EPS && b.left.value < a.right.value - EPS && a.top.value < b.bottom.value - EPS && b.top.value < a.bottom.value - EPS

    private fun assertLayout(rtl: Boolean, screenWidthDp: Int) {
        show(arabic = rtl)
        val states = listOf(
            "awaiting the type of «before»" to offeredBoth.typeRequested(ScanPurposeChoice.MAINTENANCE_BEFORE),
            "awaiting the type of «after»" to offeredBoth.typeRequested(ScanPurposeChoice.MAINTENANCE_AFTER),
            "accepted «after» with a type" to offeredBoth.accepted(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_AFTER, DefectType.MOTHERBOARD)),
            "accepted normal" to offeredBoth.accepted(PurposeSelection.Normal),
            "refused: the part is not enabled (ص21)" to offeredBoth.copy(failure = PurposeFailure.PartNotEnabled)
        )
        for ((stateName, state) in states) {
            for (isLocked in listOf(false, true)) {
                offered = state
                locked = isLocked
                rule.waitForIdle()
                val at = "$stateName, locked=$isLocked, ${screenWidthDp}dp, ${if (rtl) "ar (rtl)" else "tr (ltr)"}"
                val root = rule.onRoot().getBoundsInRoot()
                assertEquals("$at: the screen the qualifiers built is not the one asked for (the root is $root)", screenWidthDp.toFloat(), root.width.value, EPS)
                val left = PAD
                val right = screenWidthDp - PAD
                val chips = ScanPurposeChoice.values().map { it to bounds(purposeChipTag(it)) }
                for ((choice, b) in chips) {
                    assertTrue("$at: the ${choice.wire} chip $b leaves the padded width [$left, $right]", b.left.value >= left - EPS && b.right.value <= right + EPS)
                }
                // three chips side by side: no intersection, one row (same top)
                for (i in chips.indices) for (j in i + 1 until chips.size) {
                    assertTrue("$at: the chips ${chips[i].first.wire} and ${chips[j].first.wire} intersect", !intersects(chips[i].second, chips[j].second))
                }
                // one row: the chips are centred in their touch target, so their tops differ with the height of the label - their vertical ranges overlap
                assertTrue("$at: the three chips are one row", chips.maxOf { it.second.top.value } < chips.minOf { it.second.bottom.value } - EPS)
                // the direction really applied: the first chip («عادي» / Normal) is at the START edge of the row
                val first = chips.first().second
                val last = chips.last().second
                if (rtl) assertTrue("$at: rtl - the first chip $first is not right of the last $last", first.left.value > last.left.value)
                else assertTrue("$at: ltr - the first chip $first is not left of the last $last", first.left.value < last.left.value)

                if (state.failure is PurposeFailure.PartNotEnabled) {
                    val note = bounds(PURPOSE_PART_NOT_ENABLED_TAG)
                    assertTrue("$at: the ص21 text $note leaves the padded width [$left, $right]", note.left.value >= left - EPS && note.right.value <= right + EPS)
                    assertTrue("$at: the ص21 text $note is not below the chips (bottom ${chips.maxOf { it.second.bottom.value }})", note.top.value >= chips.maxOf { it.second.bottom.value } - EPS)
                } else {
                    rule.onNodeWithTag(PURPOSE_PART_NOT_ENABLED_TAG).assertDoesNotExist()
                }
                val listShown = state.typeListFor() != null
                if (!listShown) {
                    rule.onNodeWithTag(PURPOSE_TYPES_TAG).assertDoesNotExist()
                    continue
                }
                val types = DefectType.values().map { it to bounds(purposeTypeTag(it)) }
                val rowBottom = chips.maxOf { it.second.bottom.value }
                var previousBottom = rowBottom
                for ((type, b) in types) {
                    assertTrue("$at: the ${type.wire} type $b leaves the padded width [$left, $right]", b.left.value >= left - EPS && b.right.value <= right + EPS)
                    assertTrue("$at: the ${type.wire} type $b is not below the previous element (bottom $previousBottom)", b.top.value >= previousBottom - EPS)
                    assertTrue("$at: the ${type.wire} type is not as wide as the content: $b", b.width.value >= right - left - EPS)
                    previousBottom = b.bottom.value
                }
                println("LAYOUT-PROOF $at: chips row bottom=$rowBottom, types from ${types.first().second.top.value} to ${types.last().second.bottom.value}")
            }
        }
    }

    @Test
    @Config(qualifiers = "ar-ldrtl-w360dp-h800dp-xxhdpi")
    fun `arabic on 360 dp - the chips and the five types stay inside the padded width, one row of chips, the types stacked below`() = assertLayout(rtl = true, screenWidthDp = 360)

    @Test
    @Config(qualifiers = "ar-ldrtl-w411dp-h800dp-xxhdpi")
    fun `arabic on 411 dp - the chips and the five types stay inside the padded width, one row of chips, the types stacked below`() = assertLayout(rtl = true, screenWidthDp = 411)

    @Test
    @Config(qualifiers = "tr-w360dp-h800dp-xxhdpi")
    fun `turkish on 360 dp - the chips and the five types stay inside the padded width, one row of chips, the types stacked below`() = assertLayout(rtl = false, screenWidthDp = 360)

    @Test
    @Config(qualifiers = "tr-w411dp-h800dp-xxhdpi")
    fun `turkish on 411 dp - the chips and the five types stay inside the padded width, one row of chips, the types stacked below`() = assertLayout(rtl = false, screenWidthDp = 411)

    private companion object {
        const val PAD = 24f
        const val EPS = 0.5f
    }
}
