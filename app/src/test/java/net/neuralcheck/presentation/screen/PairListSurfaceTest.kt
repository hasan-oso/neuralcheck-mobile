package net.neuralcheck.presentation.screen

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
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
import org.junit.Assert.assertFalse
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
 * The pair list drawn - order of 2026-10-22. The REAL composable in the app's theme; the labels are the TEST's own (one distinct text per slot, the arguments written in) because the texts are not approved
 * ([PAIR_LIST_TEXTS_APPROVED] was false and nothing drew them in the app; the texts are approved now and `MaintenanceCycleScreenTextsTest` draws the real ones). What is asserted: each state draws what it should and nothing of another; every row reports ITS candidate's handle on a tap and the
 * button its own tap; the button is enabled only with a choice and no call out; no text is drawn for the loading bar; the failure text is the one of the failure's kind; a candidate's handle (`beforeScanId`) is
 * NOWHERE in the semantics - not as text, not as a description, not as a tag; a row is the name, the date and the hour in the environment it is given, and a phone with no name gets its own text, not an invented
 * one; and the slots the surface asks for across the states are EXACTLY [PairTexts.all] (no text the list misses, no entry the surface never uses).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class PairListSurfaceTest {

    /** As `RepeatScanSurfaceTest.RegisterComposeActivity`. */
    class RegisterComposeActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }

    @get:Rule(order = 0) val registerActivity = RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val requested = linkedSetOf<PairTextSlot>()
    private val labels: PairLabels = { text ->
        requested.add(text.slot)
        when (text) {
            PairText.PickPrompt -> "T-prompt"
            is PairText.Row -> "T-row[${text.name}|${text.date}|${text.time.replace('\u202f', ' ')}]"
            is PairText.Empty -> "T-empty[${text.validityHours}]"
            is PairText.Linked -> "T-linked[${text.date}]"
            PairText.Confirm -> "T-confirm"
            PairText.NoDeviceName -> "T-noname"
            is PairText.Failure -> "T-fail[${text.kind.token}]"
        }
    }

    private val selects = mutableListOf<String>()
    private var confirms = 0
    private val actions = PairListActions(onSelect = { selects += it }, onConfirm = { confirms++ })

    /** UTC, US English, the screen drawn in Arabic: what a row's date and hour read like here. 1760000000000 ms = 2025-10-09 08:53:20 UTC. */
    private val environment = PairRowEnvironment(ConsentLocaleRead.Known(ConsentLocale.AR), ZoneId.of("UTC"), Locale.US)

    private fun mapped(ar: String = "اسم عربي", tr: String = "Turkish Name") =
        DeviceNameRead.Mapped(TextRead.Text(ar), TextRead.Text(tr), TextRead.Text("English Name"), TextRead.Text("SM-X"))

    private fun candidate(handle: String, name: DeviceNameRead = mapped(), at: Long = 1_760_000_000_000L) = PairCandidate(handle, name, at)

    private val a = candidate("SECRET-HANDLE-A")
    private val b = candidate("SECRET-HANDLE-B", DeviceNameRead.Unmapped(TextRead.Text("XYZ-1"), TextRead.Text("MODEL_NOT_IN_MAP")), 1_760_054_400_000L)
    private val c = candidate("SECRET-HANDLE-C", DeviceNameRead.Unmapped(TextRead.Absent, TextRead.Text("MODEL_ABSENT")), 1_760_100_000_000L)
    private val listing = PairListing(72, listOf(a, b, c))
    private val choosing = PairListUi.Choosing(listing, selected = null, confirm = PairConfirmUi.Idle)

    private var ui by mutableStateOf<PairListUi>(PairListUi.NotShown)

    private fun show() {
        rule.setContent {
            NeuralCheckTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    PairListSection(ui = ui, labels = labels, actions = actions, environment = environment)
                }
            }
        }
        rule.waitForIdle()
    }

    private fun update(next: PairListUi) {
        ui = next
        rule.waitForIdle()
    }

    private fun exists(vararg parts: String): Boolean = rule.onAllNodes(SemanticsMatcher("tag ${pairTag(*parts)}") {
        it.config.getOrNull(SemanticsProperties.TestTag) == pairTag(*parts)
    }).fetchSemanticsNodes().isNotEmpty()

    private fun tag(vararg parts: String) = rule.onNodeWithTag(pairTag(*parts))

    /** Every string the semantics tree holds - texts, descriptions and tags - merged and unmerged. */
    private fun everySemanticString(): List<String> {
        val out = mutableListOf<String>()
        fun walk(node: SemanticsNode) {
            node.config.getOrNull(SemanticsProperties.Text)?.forEach { out += it.text }
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.forEach { out += it }
            node.config.getOrNull(SemanticsProperties.TestTag)?.let { out += it }
            node.children.forEach { walk(it) }
        }
        walk(rule.onRoot(useUnmergedTree = true).fetchSemanticsNode())
        return out
    }

    private val rowOfA = "T-row[اسم عربي|Oct 9, 2025|8:53 AM]"

    @Test
    fun `not shown draws nothing but the empty container - no bar, no text, no button`() {
        show()
        for (part in listOf("loading", "failure", "empty", "prompt", "confirm", "linked")) assertFalse(part, exists(part))
        assertEquals(emptySet<PairTextSlot>(), requested)
    }

    @Test
    fun `loading is a bar with no text at all - nothing is asked of the labels`() {
        show()
        update(PairListUi.Loading)
        assertTrue(exists("loading"))
        assertEquals(emptySet<PairTextSlot>(), requested)
        assertFalse(exists("empty"))
    }

    @Test
    fun `a failed load shows the text of its kind and nothing else - one test per kind`() {
        show()
        for (kind in PairFailureKind.values()) {
            update(PairListUi.LoadFailed(PairFailure(kind, "code")))
            tag("failure").assertTextEquals("T-fail[${kind.token}]")
            assertFalse("a failure draws no list and no button", exists("confirm") || exists("row", "0"))
        }
        assertEquals("one slot per kind, six", 6, requested.size)
    }

    @Test
    fun `the empty state shows the hours the server named - and no list`() {
        show()
        update(PairListUi.Empty(48))
        tag("empty").assertTextEquals("T-empty[48]")
        assertEquals(setOf(PairTextSlot.EMPTY), requested)
        assertFalse(exists("row", "0") || exists("confirm") || exists("prompt"))
    }

    @Test
    fun `the list shows the prompt, one row per candidate in the order given, and the confirm button disabled until a row is chosen`() {
        show()
        update(choosing)
        tag("prompt").assertTextEquals("T-prompt")
        tag("row", "0").assertTextEquals(rowOfA)
        assertTrue(exists("row", "1") && exists("row", "2") && !exists("row", "3"))
        tag("confirm").assertTextEquals("T-confirm")
        tag("confirm").assertIsNotEnabled()
        for (i in 0..2) { tag("row", i.toString()).assertIsEnabled(); tag("row", i.toString()).assertIsNotSelected() }
        update(choosing.copy(selected = b.beforeScanId))
        tag("row", "1").assertIsSelected()
        tag("row", "0").assertIsNotSelected()
        tag("confirm").assertIsEnabled()
    }

    @Test
    fun `a row is the name, the date and the hour - the marketing name in the screen's language, the model string as the phone reported it, or the text for no name - never one made up`() {
        show()
        update(choosing)
        tag("row", "0").assertTextEquals(rowOfA)
        tag("row", "1").assertTextEquals("T-row[XYZ-1|Oct 10, 2025|12:00 AM]")
        // a phone with neither: the text of its own, which is a slot of the list
        tag("row", "2").assertTextEquals("T-row[T-noname|Oct 10, 2025|12:40 PM]")
        assertTrue(PairTextSlot.NO_DEVICE_NAME in requested)
    }

    @Test
    fun `every row reports ITS candidate's handle on a tap, and the button reports its own tap - and nothing else`() {
        show()
        update(choosing.copy(selected = a.beforeScanId))
        tag("row", "2").performClick()
        tag("row", "0").performClick()
        tag("row", "1").performClick()
        assertEquals(listOf(c.beforeScanId, a.beforeScanId, b.beforeScanId), selects)
        assertEquals(0, confirms)
        tag("confirm").performClick()
        assertEquals(1, confirms)
        assertEquals("the button reports no row", 3, selects.size)
    }

    @Test
    fun `a confirmation in flight shows its bar and disables the rows and the button - a refused one shows its text and leaves the choice`() {
        show()
        update(choosing.copy(selected = a.beforeScanId, confirm = PairConfirmUi.InFlight(a.beforeScanId)))
        assertTrue(exists("confirmProgress"))
        for (i in 0..2) tag("row", i.toString()).assertIsNotEnabled()
        tag("confirm").assertIsNotEnabled()
        tag("row", "0").assertIsSelected()
        update(choosing.copy(selected = a.beforeScanId, confirm = PairConfirmUi.Failed(PairFailure(PairFailureKind.ALREADY_PAIRED, "BEFORE_ALREADY_PAIRED"))))
        assertFalse(exists("confirmProgress"))
        tag("confirmFailure").assertTextEquals("T-fail[already_paired]")
        tag("row", "0").assertIsSelected()
        tag("confirm").assertIsEnabled()
        for (kind in PairFailureKind.values()) {
            update(choosing.copy(selected = a.beforeScanId, confirm = PairConfirmUi.Failed(PairFailure(kind, "x"))))
            tag("confirmFailure").assertTextEquals("T-fail[${kind.token}]")
        }
    }

    @Test
    fun `linked shows its text with the date of the scan chosen, keeps the rows so the choice can be made again, and has no confirm button until a new choice`() {
        show()
        update(PairListUi.Linked(listing, b))
        tag("linked").assertTextEquals("T-linked[Oct 10, 2025]")
        tag("row", "1").assertIsSelected()
        for (i in 0..2) tag("row", i.toString()).assertIsEnabled()
        assertFalse(exists("confirm"))
        tag("row", "0").performClick()
        assertEquals(listOf(a.beforeScanId), selects)
    }

    @Test
    fun `no candidate handle is anywhere in the semantics - not a text, not a description, not a tag - in any state`() {
        show()
        val states = listOf(choosing, choosing.copy(selected = a.beforeScanId, confirm = PairConfirmUi.InFlight(a.beforeScanId)), PairListUi.Linked(listing, c))
        for (state in states) {
            update(state)
            val strings = everySemanticString()
            assertTrue("something is drawn", strings.isNotEmpty())
            for (s in strings) assertFalse("a handle in the semantics: «$s»", s.contains("SECRET-HANDLE"))
        }
    }

    @Test
    fun `the slots the surface asks for are exactly the slots of the list - no text the list misses, no entry the surface never uses`() {
        show()
        for (kind in PairFailureKind.values()) update(PairListUi.LoadFailed(PairFailure(kind, "c")))
        update(PairListUi.Empty(72))
        update(choosing)
        update(choosing.copy(selected = a.beforeScanId))
        update(PairListUi.Linked(listing, a))
        assertEquals(PairTexts.all.toSet(), requested)
        assertEquals("twelve slots: the rows ص9 to ص19 of the proposal and ص20, the one this builder proposed and the coordinator numbered", 12, PairTexts.all.size)
    }
}
