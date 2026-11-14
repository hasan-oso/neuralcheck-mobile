package net.neuralcheck.presentation.screen

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.presentation.theme.NeuralCheckTheme
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel
import net.neuralcheck.presentation.viewmodel.RealJoinPathHarness
import net.neuralcheck.presentation.viewmodel.SessionWireFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
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
 * The maintenance before/after cycle on the owner's waiting screen, as the screen shows it - Hasan's approval of 2026-10-22 (rules 13, 22, 37, 41). What is drawn is [PurposeAndPairSections], the very composable
 * `GenerateSessionScreen` calls, for the state the REAL owner's view model makes from the create's answer, through the real service and its real gates (NOTHING is opened by the test: the guards are true and the
 * exit gate reads them), with the labels the screen reads from the resources of the phone's language - Arabic and Turkish, each. The expected texts are [ApprovedMaintenanceTexts]: the approval's, not the code's.
 *
 * (a) a shop with neither part - the answer says both off, or carries no `protocolParts` - gets no chips and no list, and nothing it is asked leaves the phone; a shop with `purposes` alone gets «عادي» only and no list;
 * (b) a shop with both parts gets the chips with their approved texts, the fault-type list with its title, its hint and its five types, the locked note once the customer's phone joined, and - for an «after» purpose - the
 * pair list: its prompt, its rows, its button, and the linked text; (c) the refusal `PURPOSE_PART_NOT_ENABLED` shows ص21; (d) a refusal code nobody named shows ص18, on the list's load and on the confirmation.
 * What it cannot see (named, not hidden): a real phone's layout and right-to-left drawing, and the length of the texts in a chip or a row on a small screen (a device check); the theme's font is the platform fallback here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MaintenanceCycleScreenTextsTest {

    /** As `SessionPurposeChipsTest.RegisterComposeActivity`: the release variant's manifest does not declare `ComponentActivity`. */
    class RegisterComposeActivity : TestWatcher() {
        override fun starting(description: Description) {
            val app = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(app.packageManager).addActivityIfNotPresent(ComponentName(app.packageName, ComponentActivity::class.java.name))
        }
    }

    @get:Rule(order = 0) val registerActivity = RegisterComposeActivity()
    @get:Rule(order = 1) val rule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun t(code: String, tr: Boolean) = ApprovedMaintenanceTexts.text(code, tr)

    private fun answer(parts: Any?, withKey: Boolean = true): HashMap<String, Any?> =
        hashMapOf<String, Any?>("sessionId" to "sess-1", "code" to "NC-1234", "expiresInMinutes" to 60).also { if (withKey) it["protocolParts"] = parts }

    private val bothParts: HashMap<String, Any?> get() = answer(hashMapOf<String, Any?>("purposes" to true, "batteryBeforeAfter" to true))

    private val unmappedName = hashMapOf<String, Any?>("state" to "name_unmapped", "model" to "XYZ-1", "names" to null, "reason" to "MODEL_NOT_IN_MAP")

    private val listWithOne: HashMap<String, Any?> = hashMapOf(
        "validityHours" to 72,
        "candidates" to listOf(hashMapOf<String, Any?>("beforeScanId" to "scan-1", "deviceName" to unmappedName, "scannedAtMs" to 1_760_000_000_000L))
    )

    private val confirmed: HashMap<String, Any?> = hashMapOf("sessionId" to "sess-1", "beforeScanId" to "scan-1", "linkMethod" to "manual")

    private fun refusal(code: String, status: String = "FAILED_PRECONDITION") =
        SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody(status, "made-up message", code))

    /** The real view model, the real service and gates, and the section the screen draws - [check] is given the harness and the view model after the first composition. */
    private fun screen(createAnswer: Any?, check: (RealJoinPathHarness, GenerateSessionViewModel) -> Unit) {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.createResponse = createAnswer
            val vm = GenerateSessionViewModel(context, h.service, h.canceller)
            rule.setContent {
                val state by vm.state.collectAsState()
                NeuralCheckTheme {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
                        PurposeAndPairSections(
                            state = state,
                            onSelectPurpose = { vm.onPurposeChip(it) },
                            onSelectDefectType = { vm.onDefectType(it) },
                            onSelectPair = { vm.onPairCandidate(it) },
                            onConfirmPair = { vm.onPairConfirm() }
                        )
                    }
                }
            }
            rule.waitForIdle()
            check(h, vm)
        }
    }

    private fun tag(tag: String) = rule.onNodeWithTag(tag)

    private fun shown(text: String) = rule.onNodeWithText(text).assertExists()

    private fun notShown(text: String) = rule.onAllNodesWithText(text).assertCountEquals(0)

    private fun textOf(tag: String): String =
        rule.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } ?: error("no text on $tag")

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    private val chipCodes = listOf("ص1", "ص2", "ص3", "ص4")
    private val typeCodes = mapOf(
        DefectType.BATTERY to "ص6-1", DefectType.CHARGING_PORT to "ص6-2", DefectType.THERMAL to "ص6-3", DefectType.MOTHERBOARD to "ص6-4", DefectType.SOFTWARE to "ص6-5"
    )

    // ---- (a) a shop without the parts ----

    private fun noChipsFor(createAnswer: Any?, tr: Boolean) = screen(createAnswer) { h, vm ->
        tag(PURPOSE_CHIPS_TAG).assertDoesNotExist()
        tag(PAIR_LIST_TAG).assertDoesNotExist()
        for (choice in ScanPurposeChoice.values()) tag(purposeChipTag(choice)).assertDoesNotExist()
        for (row in ApprovedMaintenanceTexts.rows.filter { !it.existing }) notShown(t(row.code, tr))
        // what the screen cannot draw it cannot tap - and what is asked by any other means leaves nothing the phone
        vm.onPurposeChip(ScanPurposeChoice.NORMAL)
        vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_AFTER)
        vm.onDefectType(DefectType.BATTERY)
        rule.waitForIdle()
        assertEquals(0, h.callableCalls("setSessionPurpose"))
        assertEquals(0, h.callableCalls("listOpenMaintenanceBefores"))
        assertEquals(0, h.callableCalls("confirmMaintenanceAfterLink"))
        tag(PURPOSE_CHIPS_TAG).assertDoesNotExist()
        tag(PAIR_LIST_TAG).assertDoesNotExist()
    }

    private fun bothPartsOffAr() = noChipsFor(answer(hashMapOf<String, Any?>("purposes" to false, "batteryBeforeAfter" to false)), tr = false)
    private fun bothPartsOffTr() = noChipsFor(answer(hashMapOf<String, Any?>("purposes" to false, "batteryBeforeAfter" to false)), tr = true)

    @Test
    @Config(qualifiers = "ar-ldrtl-w411dp-h800dp-xxhdpi")
    fun `arabic - a shop with both parts off gets no chips and no list, and nothing it is asked leaves the phone`() = bothPartsOffAr()

    @Test
    @Config(qualifiers = "tr-w411dp-h800dp-xxhdpi")
    fun `turkish - a shop with both parts off gets no chips and no list, and nothing it is asked leaves the phone`() = bothPartsOffTr()

    @Test
    @Config(qualifiers = "ar-ldrtl-w411dp-h800dp-xxhdpi")
    fun `arabic - an answer with no protocolParts at all gets no chips and no list`() = noChipsFor(answer(null, withKey = false), tr = false)

    @Test
    @Config(qualifiers = "tr-w411dp-h800dp-xxhdpi")
    fun `turkish - an answer with no protocolParts at all gets no chips and no list`() = noChipsFor(answer(null, withKey = false), tr = true)

    private fun purposesAlone(tr: Boolean) = screen(answer(hashMapOf<String, Any?>("purposes" to true))) { h, vm ->
        tag(PURPOSE_CHIPS_TAG).assertExists()
        shown(t("ص1", tr))
        shown(t("ص2", tr))
        tag(purposeChipTag(ScanPurposeChoice.MAINTENANCE_BEFORE)).assertDoesNotExist()
        tag(purposeChipTag(ScanPurposeChoice.MAINTENANCE_AFTER)).assertDoesNotExist()
        notShown(t("ص3", tr))
        notShown(t("ص4", tr))
        notShown(t("ص7", tr))
        tag(PAIR_LIST_TAG).assertDoesNotExist()
        // «normal» is sent; a maintenance purpose is not, whoever asks
        vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_BEFORE)
        vm.onDefectType(DefectType.BATTERY)
        assertEquals(0, h.callableCalls("setSessionPurpose"))
        tap(purposeChipTag(ScanPurposeChoice.NORMAL))
        assertEquals(mapOf("sessionId" to "sess-1", "purpose" to "normal"), h.purposeRequests.single())
        tag(PAIR_LIST_TAG).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "ar-ldrtl-w411dp-h800dp-xxhdpi")
    fun `arabic - a shop with purposes alone gets the normal chip only`() = purposesAlone(tr = false)

    @Test
    @Config(qualifiers = "tr-w411dp-h800dp-xxhdpi")
    fun `turkish - a shop with purposes alone gets the normal chip only`() = purposesAlone(tr = true)

    // ---- (b) a shop with both parts ----

    private fun bothPartsBefore(tr: Boolean) = screen(bothParts) { h, vm ->
        tag(PURPOSE_CHIPS_TAG).assertExists()
        for (code in chipCodes) shown(t(code, tr))
        tag(PURPOSE_TYPES_TAG).assertDoesNotExist()
        notShown(t("ص5", tr))
        // a maintenance chip opens the five types - with the approved title, hint and names - and sends nothing
        tap(purposeChipTag(ScanPurposeChoice.MAINTENANCE_BEFORE))
        assertEquals(0, h.callableCalls("setSessionPurpose"))
        tag(PURPOSE_TYPES_TAG).assertExists()
        shown(t("ص7", tr))
        shown(t("ص8", tr))
        for ((type, code) in typeCodes) {
            shown(t(code, tr))
            tag(purposeTypeTag(type)).assertIsEnabled()
        }
        // a type sends the purpose with it
        tap(purposeTypeTag(DefectType.THERMAL))
        assertEquals(mapOf("sessionId" to "sess-1", "purpose" to "maintenance_before", "defectType" to "THERMAL"), h.purposeRequests.single())
        tag(PAIR_LIST_TAG).assertDoesNotExist()
        // the customer's phone joins: the chips are locked and the locked note is shown
        h.deliverSnapshot("sess-1", fields = mapOf("status" to "scanning"))
        rule.waitForIdle()
        tag(PURPOSE_LOCKED_NOTE_TAG).assertExists()
        shown(t("ص5", tr))
    }

    @Test
    @Config(qualifiers = "ar-ldrtl-w411dp-h800dp-xxhdpi")
    fun `arabic - a shop with both parts gets the chips, the five fault types with their approved texts, and the locked note`() = bothPartsBefore(tr = false)

    @Test
    @Config(qualifiers = "tr-w411dp-h800dp-xxhdpi")
    fun `turkish - a shop with both parts gets the chips, the five fault types with their approved texts, and the locked note`() = bothPartsBefore(tr = true)

    /** The row's text is ص10 with the name, a date and an hour: the name and the pattern are checked, the date and the hour are the real formatter's. */
    private fun rowPattern(tr: Boolean): Regex {
        val template = t("ص10", tr)
        val parts = template.split("%1\$s", "%2\$s", "%3\$s")
        assertEquals(4, parts.size)
        return Regex(Regex.escape(parts[0]) + Regex.escape("XYZ-1") + Regex.escape(parts[1]) + "(.+)" + Regex.escape(parts[2]) + "(.+)" + Regex.escape(parts[3]))
    }

    private fun afterAndLink(tr: Boolean) = screen(bothParts) { h, vm ->
        h.pairListResponse = listWithOne
        h.pairConfirmResponse = confirmed
        tap(purposeChipTag(ScanPurposeChoice.MAINTENANCE_AFTER))
        tap(purposeTypeTag(DefectType.BATTERY))
        assertEquals(mapOf("sessionId" to "sess-1", "purpose" to "maintenance_after", "defectType" to "BATTERY"), h.purposeRequests.single())
        // the list: the prompt, the row, the button - the approved texts
        tag(PAIR_LIST_TAG).assertExists()
        shown(t("ص9", tr))
        val row = textOf(pairTag("row", "0"))
        val match = rowPattern(tr).matchEntire(row)
        assertTrue("the row «$row» is not ص10 with the name, a date and an hour (${t("ص10", tr)})", match != null)
        assertTrue("the digits of the row are Latin: «$row»", row.none { it in '٠'..'٩' || it in '۰'..'۹' })
        assertEquals(t("ص13", tr), textOf(pairTag("confirm")))
        // a row is chosen and confirmed: the linked text (ص12) with the date of the chosen scan
        tap(pairTag("row", "0"))
        tap(pairTag("confirm"))
        assertEquals(1, h.callableCalls("listOpenMaintenanceBefores"))
        assertEquals(1, h.callableCalls("confirmMaintenanceAfterLink"))
        val linked = textOf(pairTag("linked"))
        val template = t("ص12", tr).split("%1\$s")
        assertTrue("the linked text «$linked» is not ص12 (${t("ص12", tr)})", linked.startsWith(template[0]) && linked.endsWith(template[1]) && linked.length > template[0].length + template[1].length)
    }

    @Test
    @Config(qualifiers = "ar-ldrtl-w411dp-h800dp-xxhdpi")
    fun `arabic - an after purpose opens the pair list with its approved prompt, row and button, and a confirmation shows the linked text`() = afterAndLink(tr = false)

    @Test
    @Config(qualifiers = "tr-w411dp-h800dp-xxhdpi")
    fun `turkish - an after purpose opens the pair list with its approved prompt, row and button, and a confirmation shows the linked text`() = afterAndLink(tr = true)

    private fun emptyListScenario(tr: Boolean) = screen(bothParts) { h, _ ->
        h.pairListResponse = hashMapOf("validityHours" to 72, "candidates" to emptyList<Any?>())
        tap(purposeChipTag(ScanPurposeChoice.MAINTENANCE_AFTER))
        tap(purposeTypeTag(DefectType.SOFTWARE))
        assertEquals(t("ص11", tr).replace("%1\$d", "72"), textOf(pairTag("empty")))
    }

    @Test
    @Config(qualifiers = "ar-ldrtl-w411dp-h800dp-xxhdpi")
    fun `arabic - an empty list shows its approved text with the hours`() = emptyListScenario(tr = false)

    @Test
    @Config(qualifiers = "tr-w411dp-h800dp-xxhdpi")
    fun `turkish - an empty list shows its approved text with the hours`() = emptyListScenario(tr = true)

    // ---- (c) the refusal of a purpose the shop's parts do not allow ----

    private fun partNotEnabled(tr: Boolean) = screen(bothParts) { h, _ ->
        h.purposeFailure = refusal("PURPOSE_PART_NOT_ENABLED")
        tap(purposeChipTag(ScanPurposeChoice.MAINTENANCE_BEFORE))
        tap(purposeTypeTag(DefectType.BATTERY))
        assertEquals(1, h.callableCalls("setSessionPurpose"))
        tag(PURPOSE_PART_NOT_ENABLED_TAG).assertExists()
        assertEquals(t("ص21", tr), textOf(PURPOSE_PART_NOT_ENABLED_TAG))
        // the chips stay usable
        for (choice in ScanPurposeChoice.values()) tag(purposeChipTag(choice)).assertIsEnabled()
    }

    @Test
    @Config(qualifiers = "ar-ldrtl-w411dp-h800dp-xxhdpi")
    fun `arabic - the refusal PURPOSE_PART_NOT_ENABLED shows its approved text`() = partNotEnabled(tr = false)

    @Test
    @Config(qualifiers = "tr-w411dp-h800dp-xxhdpi")
    fun `turkish - the refusal PURPOSE_PART_NOT_ENABLED shows its approved text`() = partNotEnabled(tr = true)

    // ---- (d) a refusal code nobody named ----

    private fun unnamedRefusal(tr: Boolean) {
        // on the confirmation
        screen(bothParts) { h, _ ->
            h.pairListResponse = listWithOne
            h.pairConfirmFailure = refusal("PAIR_NOBODY_NAMED_THIS")
            tap(purposeChipTag(ScanPurposeChoice.MAINTENANCE_AFTER))
            tap(purposeTypeTag(DefectType.BATTERY))
            tap(pairTag("row", "0"))
            tap(pairTag("confirm"))
            assertEquals(t("ص18", tr), textOf(pairTag("confirmFailure")))
        }
    }

    private fun unnamedRefusalOnLoad(tr: Boolean) = screen(bothParts) { h, _ ->
        h.pairListFailure = refusal("PAIR_NOBODY_NAMED_THIS")
        tap(purposeChipTag(ScanPurposeChoice.MAINTENANCE_AFTER))
        tap(purposeTypeTag(DefectType.BATTERY))
        assertEquals(t("ص18", tr), textOf(pairTag("failure")))
    }

    @Test
    @Config(qualifiers = "ar-ldrtl-w411dp-h800dp-xxhdpi")
    fun `arabic - an unnamed refusal code of the confirmation shows the general failure text`() = unnamedRefusal(tr = false)

    @Test
    @Config(qualifiers = "tr-w411dp-h800dp-xxhdpi")
    fun `turkish - an unnamed refusal code of the confirmation shows the general failure text`() = unnamedRefusal(tr = true)

    @Test
    @Config(qualifiers = "ar-ldrtl-w411dp-h800dp-xxhdpi")
    fun `arabic - an unnamed refusal code on the list's load shows the general failure text`() = unnamedRefusalOnLoad(tr = false)

    @Test
    @Config(qualifiers = "tr-w411dp-h800dp-xxhdpi")
    fun `turkish - an unnamed refusal code on the list's load shows the general failure text`() = unnamedRefusalOnLoad(tr = true)
}
