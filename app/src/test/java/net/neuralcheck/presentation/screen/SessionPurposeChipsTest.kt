package net.neuralcheck.presentation.screen

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import net.neuralcheck.presentation.viewmodel.PurposeFailure
import net.neuralcheck.presentation.viewmodel.PurposeSelection
import net.neuralcheck.presentation.viewmodel.PurposeUi
import net.neuralcheck.presentation.viewmodel.accepted
import net.neuralcheck.presentation.viewmodel.failed
import net.neuralcheck.presentation.viewmodel.started
import net.neuralcheck.presentation.viewmodel.typeRequested
import org.junit.Assert.assertEquals
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
 * The chips' surface, rendered - A-P6-03 (ف١). The composable is drawn by Compose with the app's theme and its semantics are read back; the labels are the TEST's own (the chips' real texts are not
 * approved, so none exists in a resource: [PURPOSE_CHIPS_TEXTS_APPROVED] was false and the screen drew nothing; they are approved now and `MaintenanceCycleScreenTextsTest` draws the real ones). What is asserted is what the user's state asks of the surface: one chip per
 * [ScanPurposeChoice], the highlighted one selected, every chip reporting its own tap (the highlighted one too: the view model sends «normal» over «normal» on purpose), all chips disabled while locked or
 * while a call is out, and the locked note only while locked. Rule 30: every choice, every highlighted state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class SessionPurposeChipsTest {

    /** As `ManualUnavailableLineLayoutTest.RegisterComposeActivity`: the release variant's manifest does not declare `ComponentActivity`. */
    class RegisterComposeActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }

    @get:Rule(order = 0) val registerActivity = RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    private val labels = PurposeChipLabels(
        title = "T-title", normal = "T-normal", maintenanceBefore = "T-before", maintenanceAfter = "T-after", lockedNote = "T-locked",
        typeTitle = "T-type-title", typeHint = "T-type-hint", typeTitleAfter = "T-type-title-after", typeHintAfter = "T-type-hint-after",
        battery = "T-battery", chargingPort = "T-port", thermal = "T-thermal", motherboard = "T-board", software = "T-software",
        partNotEnabled = "T-part-not-enabled"
    )

    /** The selection a chip of [choice] makes once its fault type is chosen (the type is not what these tests are about). */
    private fun selectionOf(choice: ScanPurposeChoice): PurposeSelection =
        if (choice.needsDefectType) PurposeSelection.Maintenance(choice, DefectType.BATTERY) else PurposeSelection.Normal

    /** The shop with BOTH parts (Q31): the three chips and the type list. REPLACES `PurposeUi.Offered()`, which offered them for `purposes` alone (rule 23). */
    private val offeredBoth = PurposeUi.Offered(maintenanceOffered = true)

    private var offered by mutableStateOf(offeredBoth)
    private var locked by mutableStateOf(false)
    private val taps = mutableListOf<ScanPurposeChoice>()
    private val typeTaps = mutableListOf<DefectType>()

    private fun show() {
        rule.setContent {
            NeuralCheckTheme {
                SessionPurposeChips(offered = offered, locked = locked, labels = labels, onSelect = { taps.add(it) }, onSelectType = { typeTaps.add(it) })
            }
        }
        rule.waitForIdle()
    }

    private fun update(newOffered: PurposeUi.Offered = offered, newLocked: Boolean = locked) {
        offered = newOffered
        locked = newLocked
        rule.waitForIdle()
    }

    private fun chip(choice: ScanPurposeChoice) = rule.onNodeWithTag(purposeChipTag(choice))

    @Test
    fun `the chips are one per choice, the creation default highlights normal, and nothing else is selected`() {
        show()
        rule.onNodeWithTag(PURPOSE_CHIPS_TAG).assertExists()
        chip(ScanPurposeChoice.NORMAL).assertIsSelected()
        chip(ScanPurposeChoice.MAINTENANCE_BEFORE).assertIsNotSelected()
        chip(ScanPurposeChoice.MAINTENANCE_AFTER).assertIsNotSelected()
        for (choice in ScanPurposeChoice.values()) chip(choice).assertIsEnabled()
        rule.onNodeWithTag(PURPOSE_LOCKED_NOTE_TAG).assertDoesNotExist()
    }

    @Test
    fun `every confirmed choice is the one highlighted, and only it`() {
        show()
        for (choice in ScanPurposeChoice.values()) {
            update(offeredBoth.accepted(selectionOf(choice)))
            for (other in ScanPurposeChoice.values()) {
                if (other == choice) chip(other).assertIsSelected() else chip(other).assertIsNotSelected()
            }
        }
    }

    @Test
    fun `a tap on any chip reports that chip - the highlighted one included`() {
        show()
        for (choice in ScanPurposeChoice.values()) chip(choice).performClick()
        assertEquals(ScanPurposeChoice.values().toList(), taps)
        taps.clear()
        update(offeredBoth.accepted(selectionOf(ScanPurposeChoice.MAINTENANCE_AFTER)))
        chip(ScanPurposeChoice.MAINTENANCE_AFTER).performClick()
        assertEquals("a chosen chip tapped again is reported, not swallowed", listOf(ScanPurposeChoice.MAINTENANCE_AFTER), taps)
    }

    @Test
    fun `locked - every chip is disabled, a tap reports nothing, and the locked note shows`() {
        show()
        update(newLocked = true)
        for (choice in ScanPurposeChoice.values()) {
            chip(choice).assertIsNotEnabled()
            chip(choice).performClick()
        }
        assertEquals(emptyList<ScanPurposeChoice>(), taps)
        rule.onNodeWithTag(PURPOSE_LOCKED_NOTE_TAG).assertExists()
        // unlocking brings the chips back and the note goes
        update(newLocked = false)
        chip(ScanPurposeChoice.MAINTENANCE_BEFORE).assertIsEnabled()
        rule.onNodeWithTag(PURPOSE_LOCKED_NOTE_TAG).assertDoesNotExist()
    }

    private fun typeChip(type: DefectType) = rule.onNodeWithTag(purposeTypeTag(type))

    @Test
    fun `no list of fault types until a maintenance chip is tapped or accepted - not at creation, not for a chosen normal`() {
        show()
        rule.onNodeWithTag(PURPOSE_TYPES_TAG).assertDoesNotExist()
        update(offeredBoth.accepted(PurposeSelection.Normal))
        rule.onNodeWithTag(PURPOSE_TYPES_TAG).assertDoesNotExist()
        for (type in DefectType.values()) typeChip(type).assertDoesNotExist()
    }

    @Test
    fun `a maintenance chip awaiting its type shows ITS OWN title and hint and the five types - none highlighted, all enabled - and the asked chip is highlighted at once`() {
        show()
        for (choice in listOf(ScanPurposeChoice.MAINTENANCE_BEFORE, ScanPurposeChoice.MAINTENANCE_AFTER)) {
            val other = if (choice == ScanPurposeChoice.MAINTENANCE_AFTER) ScanPurposeChoice.MAINTENANCE_BEFORE else ScanPurposeChoice.MAINTENANCE_AFTER
            update(offeredBoth.typeRequested(choice))
            rule.onNodeWithTag(PURPOSE_TYPES_TAG).assertExists()
            rule.onNodeWithText(labels.typeTitleFor(choice)).assertExists()
            rule.onNodeWithText(labels.typeHintFor(choice)).assertExists()
            // order of 2026-10-28: the two screens do not share the title and hint - the other chip's wording is not drawn
            rule.onNodeWithText(labels.typeTitleFor(other)).assertDoesNotExist()
            rule.onNodeWithText(labels.typeHintFor(other)).assertDoesNotExist()
            for (type in DefectType.values()) {
                typeChip(type).assertIsNotSelected()
                typeChip(type).assertIsEnabled()
                rule.onNodeWithText(labels.of(type)).assertExists()
            }
            // order of 2026-10-28 (REPLACES the old «the chip itself is not highlighted by being asked: the server has not said yes»): the tap is shown at once; nothing is sent by it, and the confirmed purpose is untouched
            chip(choice).assertIsSelected()
            chip(ScanPurposeChoice.NORMAL).assertIsNotSelected()
            chip(other).assertIsNotSelected()
            rule.onAllNodesWithTag(PURPOSE_PENDING_TAG).assertCountEquals(0)
        }
    }

    @Test
    fun `every type reports its own tap and no other - the one accepted too`() {
        show()
        update(offeredBoth.typeRequested(ScanPurposeChoice.MAINTENANCE_AFTER))
        for (type in DefectType.values()) typeChip(type).performClick()
        assertEquals(DefectType.values().toList(), typeTaps)
        assertEquals("a type reports no chip tap", emptyList<ScanPurposeChoice>(), taps)
        typeTaps.clear()
        update(offeredBoth.accepted(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_AFTER, DefectType.THERMAL)))
        typeChip(DefectType.THERMAL).performClick()
        assertEquals("the accepted type tapped again is reported, not swallowed", listOf(DefectType.THERMAL), typeTaps)
    }

    @Test
    fun `an accepted maintenance purpose keeps the list with its own type highlighted - and only that one, for every choice and type`() {
        show()
        for (choice in listOf(ScanPurposeChoice.MAINTENANCE_BEFORE, ScanPurposeChoice.MAINTENANCE_AFTER)) {
            for (type in DefectType.values()) {
                update(offeredBoth.accepted(PurposeSelection.Maintenance(choice, type)))
                rule.onNodeWithTag(PURPOSE_TYPES_TAG).assertExists()
                for (other in DefectType.values()) {
                    if (other == type) typeChip(other).assertIsSelected() else typeChip(other).assertIsNotSelected()
                }
                chip(choice).assertIsSelected()
            }
        }
    }

    @Test
    fun `the list of the other maintenance chip has no type highlighted - the accepted type belongs to the accepted chip - and the row shows the chip that was asked, not the accepted one`() {
        show()
        update(offeredBoth.accepted(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_AFTER, DefectType.BATTERY)).typeRequested(ScanPurposeChoice.MAINTENANCE_BEFORE))
        for (type in DefectType.values()) typeChip(type).assertIsNotSelected()
        // order of 2026-10-28 (REPLACES chip(AFTER).assertIsSelected()): the technician asked «قبل الصيانة», so that is the chip the row shows while its type is awaited
        chip(ScanPurposeChoice.MAINTENANCE_BEFORE).assertIsSelected()
        chip(ScanPurposeChoice.MAINTENANCE_AFTER).assertIsNotSelected()
    }

    @Test
    fun `locked or with a call out - every type is disabled, a tap reports nothing, the type in flight is highlighted with its spinner, and a locked row shows only the confirmed chip`() {
        show()
        update(offeredBoth.typeRequested(ScanPurposeChoice.MAINTENANCE_BEFORE), newLocked = true)
        for (type in DefectType.values()) {
            typeChip(type).assertIsNotEnabled()
            typeChip(type).performClick()
        }
        assertEquals(emptyList<DefectType>(), typeTaps)
        // the customer's phone joined while a type was awaited: the server never took «قبل الصيانة», so the row shows the confirmed one (the creation default) and not the asked chip
        chip(ScanPurposeChoice.NORMAL).assertIsSelected()
        chip(ScanPurposeChoice.MAINTENANCE_BEFORE).assertIsNotSelected()
        update(offeredBoth.typeRequested(ScanPurposeChoice.MAINTENANCE_BEFORE).started(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_BEFORE, DefectType.SOFTWARE)), newLocked = false)
        for (type in DefectType.values()) typeChip(type).assertIsNotEnabled()
        // order of 2026-10-28 (REPLACES typeChip(SOFTWARE).assertIsNotSelected()): the type in flight is highlighted at once, with one spinner on it
        typeChip(DefectType.SOFTWARE).assertIsSelected()
        for (type in DefectType.values().filter { it != DefectType.SOFTWARE }) typeChip(type).assertIsNotSelected()
        rule.onAllNodesWithTag(PURPOSE_PENDING_TAG).assertCountEquals(1)
    }

    // ---- Q31 (2026-10-22): the maintenance chips and the types are drawn for BOTH parts only ----

    @Test
    fun `a shop with purposes alone gets the normal chip and nothing else - the two maintenance chips are not in the tree`() {
        show()
        update(PurposeUi.Offered())
        rule.onNodeWithTag(PURPOSE_CHIPS_TAG).assertExists()
        chip(ScanPurposeChoice.NORMAL).assertExists()
        chip(ScanPurposeChoice.NORMAL).assertIsSelected()
        chip(ScanPurposeChoice.NORMAL).assertIsEnabled()
        chip(ScanPurposeChoice.MAINTENANCE_BEFORE).assertDoesNotExist()
        chip(ScanPurposeChoice.MAINTENANCE_AFTER).assertDoesNotExist()
        rule.onNodeWithText(labels.maintenanceBefore).assertDoesNotExist()
        rule.onNodeWithText(labels.maintenanceAfter).assertDoesNotExist()
        // a tap on the one chip is reported
        chip(ScanPurposeChoice.NORMAL).performClick()
        assertEquals(listOf(ScanPurposeChoice.NORMAL), taps)
    }

    @Test
    fun `a shop with purposes alone shows no type list - even for a hand-built state that awaits or holds a maintenance purpose`() {
        show()
        update(PurposeUi.Offered().accepted(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_AFTER, DefectType.THERMAL)))
        rule.onNodeWithTag(PURPOSE_TYPES_TAG).assertDoesNotExist()
        for (type in DefectType.values()) typeChip(type).assertDoesNotExist()
        chip(ScanPurposeChoice.MAINTENANCE_AFTER).assertDoesNotExist()
        update(PurposeUi.Offered().copy(awaitingType = ScanPurposeChoice.MAINTENANCE_BEFORE))
        rule.onNodeWithTag(PURPOSE_TYPES_TAG).assertDoesNotExist()
    }

    @Test
    fun `the part-not-enabled text shows only after that refusal - with the chips still enabled - and no other failure shows it`() {
        show()
        rule.onNodeWithTag(PURPOSE_PART_NOT_ENABLED_TAG).assertDoesNotExist()
        update(offeredBoth.copy(failure = PurposeFailure.PartNotEnabled))
        rule.onNodeWithTag(PURPOSE_PART_NOT_ENABLED_TAG).assertExists()
        rule.onNodeWithText(labels.partNotEnabled).assertExists()
        for (choice in ScanPurposeChoice.values()) chip(choice).assertIsEnabled()
        rule.onNodeWithTag(PURPOSE_LOCKED_NOTE_TAG).assertDoesNotExist()
        for (other in listOf(PurposeFailure.Failed, PurposeFailure.NotPending, PurposeFailure.Refused("SHOP_NO_CREDIT"), null)) {
            update(offeredBoth.copy(failure = other))
            rule.onNodeWithTag(PURPOSE_PART_NOT_ENABLED_TAG).assertDoesNotExist()
        }
    }

    @Test
    fun `while a call is out every chip is disabled, the chip asked and its type are highlighted at once with ONE spinner, and the confirmed chip is not`() {
        show()
        update(offeredBoth.typeRequested(ScanPurposeChoice.MAINTENANCE_BEFORE).started(selectionOf(ScanPurposeChoice.MAINTENANCE_BEFORE)))
        for (choice in ScanPurposeChoice.values()) chip(choice).assertIsNotEnabled()
        // order of 2026-10-28 (REPLACES «the chip in flight is not highlighted»): the row answers the tap at once; the server's answer (cold: 4-5 s) only confirms it
        chip(ScanPurposeChoice.MAINTENANCE_BEFORE).assertIsSelected()
        chip(ScanPurposeChoice.NORMAL).assertIsNotSelected()
        typeChip(DefectType.BATTERY).assertIsSelected()
        rule.onAllNodesWithTag(PURPOSE_PENDING_TAG).assertCountEquals(1)
        rule.onNodeWithTag(PURPOSE_LOCKED_NOTE_TAG).assertDoesNotExist()
    }

    @Test
    fun `a normal call out is highlighted at once with its own spinner - and the accepted maintenance chip it replaces is not`() {
        show()
        update(offeredBoth.accepted(selectionOf(ScanPurposeChoice.MAINTENANCE_AFTER)).started(PurposeSelection.Normal))
        chip(ScanPurposeChoice.NORMAL).assertIsSelected()
        chip(ScanPurposeChoice.MAINTENANCE_AFTER).assertIsNotSelected()
        rule.onAllNodesWithTag(PURPOSE_PENDING_TAG).assertCountEquals(1)
    }

    @Test
    fun `a call that did not end in an acceptance leaves the row on the confirmed chip - nothing the server never took stays highlighted, and no spinner is left`() {
        show()
        val asked = offeredBoth.typeRequested(ScanPurposeChoice.MAINTENANCE_AFTER).started(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_AFTER, DefectType.THERMAL))
        update(asked.failed(RuntimeException("timeout")))
        chip(ScanPurposeChoice.NORMAL).assertIsSelected()
        chip(ScanPurposeChoice.MAINTENANCE_AFTER).assertIsNotSelected()
        for (type in DefectType.values()) typeChip(type).assertIsNotSelected()
        rule.onAllNodesWithTag(PURPOSE_PENDING_TAG).assertCountEquals(0)
        // and the technician can ask again: the types are enabled
        for (type in DefectType.values()) typeChip(type).assertIsEnabled()
    }
}
