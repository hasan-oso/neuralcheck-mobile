package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.PurposeRequestRejectedException
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel.Phase
import net.neuralcheck.util.EvalCardLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The owner's purpose chips through the REAL path - A-P6-03 (غرض الفحص). Rules 30 and 37.
 *
 * Real: the owner's view model, `FirebaseAnalysisService.createScanSession` and `setSessionPurpose` (the callable name, the request map, the Gson parse of the answer into `ScanSession`),
 * `ProtocolPartsRead`, the state functions of `SessionPurposeUi.kt`, the session watcher and the SDK's own parse of a refusal body ([SessionWireFixtures.exceptionFromWire]). Replaced: only the HTTP
 * round trip, auth and the Firestore listener (the harness) - and the screen: the chips' texts are not approved, so what is asserted is the STATE the screen would draw and the calls that leave the phone.
 *
 * The request is `{sessionId, purpose, defectType?}` as the contract's A2 addendum of 2026-10-22 names it (copy @f3c1bab); what is NOT verified against the server is the success answer's shape - the harness answers an
 * empty object. The refusal bodies' status and message are made up and vary on purpose: the app reads `details.reasonCode` only (rule 21).
 *
 * 2026-10-22 (rule 23): the two battery chips became «قبل الصيانة» / «بعد الصيانة» and each carries a fault type, so the tests that tapped `BATTERY_*` tap a maintenance choice WITH a type ([typeFor]); the
 * assertions that pinned `{sessionId, purpose}` for those pin `{sessionId, purpose, defectType}`, the log line gained the type, and three tests are new (a maintenance purpose with no type and a normal with one
 * leave nothing the phone; the service refuses the pair itself).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnerSessionPurposeTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** The fault type a tap of [choice] carries in these tests: a maintenance choice has one (the type is not what most of them are about), «normal» has none. */
    private fun typeFor(choice: ScanPurposeChoice): DefectType? = if (choice.needsDefectType) DefectType.BATTERY else null

    private fun selectionOf(choice: ScanPurposeChoice): PurposeSelection =
        if (choice.needsDefectType) PurposeSelection.Maintenance(choice, DefectType.BATTERY) else PurposeSelection.Normal

    private fun answer(parts: Any?, session: String = "sess-1", code: String = "NC-1234"): HashMap<String, Any?> =
        hashMapOf<String, Any?>("sessionId" to session, "code" to code, "expiresInMinutes" to 60).also { if (parts != NO_KEY) it["protocolParts"] = parts }

    private fun withOwner(createAnswer: Any?, check: (RealJoinPathHarness, GenerateSessionViewModel) -> Unit) {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.createResponse = createAnswer
            check(h, GenerateSessionViewModel(context, h.service, h.canceller))
        }
    }

    private fun offered(vm: GenerateSessionViewModel) = vm.state.value.purposes as PurposeUi.Offered

    /**
     * The shop with BOTH parts (Q31, 2026-10-22): the three chips and the fault types. REPLACES `purposesOn` (`{purposes: true}`), which offered the maintenance chips for `purposes` alone - the rule the server
     * now refuses (`PURPOSE_PART_NOT_ENABLED`; rule 23).
     */
    private val bothPartsOn: HashMap<String, Any?> get() = answer(hashMapOf<String, Any?>("purposes" to true, "batteryBeforeAfter" to true))

    /** `purposes` alone: «عادي» only. */
    private val purposesAlone: HashMap<String, Any?> get() = answer(hashMapOf<String, Any?>("purposes" to true))

    private fun refusalNotPending(status: String = "FAILED_PRECONDITION") =
        SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody(status, "made-up message", "PURPOSE_SESSION_NOT_PENDING"))

    // ---- what the create's answer makes of the chips ----

    @Test
    fun `a shop with both parts gets the three chips, normal highlighted by the contract's creation default, and the log says what was read`() {
        withOwner(bothPartsOn) { h, vm ->
            assertEquals(Phase.WAITING, vm.state.value.phase)
            assertEquals(PurposeUi.Offered(maintenanceOffered = true), vm.state.value.purposes)
            assertEquals(ScanPurposeChoice.values().toList(), offered(vm).choices())
            assertEquals(ScanPurposeChoice.NORMAL, offered(vm).highlighted())
            assertTrue(h.logLines.toString(), EvalCardLog.sessionProtocolPartsLine("object", "enabled", "enabled", emptyList()) in h.logLines)
            assertEquals("no call is made by creating", 0, h.callableCalls("setSessionPurpose"))
        }
    }

    @Test
    fun `every other answer offers no chips and says why - a missing key, a JSON null, a part that is off, absent, or of another shape`() {
        val cases = listOf(
            NO_KEY to Triple(PurposeUi.NotOffered.Why.PARTS_ABSENT, "absent", "none"),
            null to Triple(PurposeUi.NotOffered.Why.PARTS_ABSENT, "absent", "none"),
            "yes" to Triple(PurposeUi.NotOffered.Why.PARTS_NOT_AN_OBJECT, "not_an_object_string", "none"),
            listOf(true) to Triple(PurposeUi.NotOffered.Why.PARTS_NOT_AN_OBJECT, "not_an_object_array", "none"),
            hashMapOf<String, Any?>("purposes" to false) to Triple(PurposeUi.NotOffered.Why.PURPOSES_DISABLED, "object", "disabled"),
            hashMapOf<String, Any?>() to Triple(PurposeUi.NotOffered.Why.PURPOSES_NOT_LISTED, "object", "not_listed"),
            hashMapOf<String, Any?>("consent" to true) to Triple(PurposeUi.NotOffered.Why.PURPOSES_NOT_LISTED, "object", "not_listed"),
            hashMapOf<String, Any?>("purposes" to "true") to Triple(PurposeUi.NotOffered.Why.PURPOSES_UNRECOGNIZED, "object", "unrecognized_string"),
            hashMapOf<String, Any?>("purposes" to hashMapOf<String, Any?>("enabled" to true)) to Triple(PurposeUi.NotOffered.Why.PURPOSES_UNRECOGNIZED, "object", "unrecognized_object")
        )
        for ((parts, expected) in cases) {
            withOwner(answer(parts)) { h, vm ->
                val (why, shape, purposes) = expected
                assertEquals("protocolParts=$parts", PurposeUi.NotOffered(why), vm.state.value.purposes)
                assertEquals(Phase.WAITING, vm.state.value.phase)
                val battery = if (shape == "object") "not_listed" else "none" // none of these answers lists the second part
                assertTrue("protocolParts=$parts: ${h.logLines}", EvalCardLog.sessionProtocolPartsLine(shape, purposes, battery, emptyList()) in h.logLines)
            }
        }
    }

    /** Q31: `batteryPairs` is the part's OLD name - an unknown key, not the second part: the shop has `purposes` alone, so «normal» only. REPLACES an assertion that had the maintenance chips (rule 23). */
    @Test
    fun `keys of protocolParts that are none of the six are named in the log line - and the old name of the second part does not offer the maintenance chips`() {
        withOwner(answer(hashMapOf<String, Any?>("purposes" to true, "batteryPairs" to true))) { h, vm ->
            assertEquals(PurposeUi.Offered(), vm.state.value.purposes)
            assertEquals(listOf(ScanPurposeChoice.NORMAL), offered(vm).choices())
            assertTrue(h.logLines.toString(), EvalCardLog.sessionProtocolPartsLine("object", "enabled", "not_listed", listOf("batteryPairs")) in h.logLines)
        }
    }

    // ---- Q31 (2026-10-22): the maintenance chips need `purposes` AND `batteryBeforeAfter`; «normal» needs `purposes` alone ----

    @Test
    fun `every combination of the two parts through the real create - the maintenance chips are drawn for both enabled and for nothing else - and the line names both readings`() {
        val missing = Any()
        val readings: List<Triple<Any?, String, Boolean>> = listOf(
            Triple(true, "enabled", true), Triple(false, "disabled", false), Triple("true", "unrecognized_string", false), Triple(missing, "not_listed", false)
        )
        for ((purposes, purposesToken, purposesOn) in readings) {
            for ((battery, batteryToken, batteryOn) in readings) {
                val parts = hashMapOf<String, Any?>().also {
                    if (purposes !== missing) it["purposes"] = purposes
                    if (battery !== missing) it["batteryBeforeAfter"] = battery
                }
                withOwner(answer(parts)) { h, vm ->
                    val at = "protocolParts=$parts"
                    assertTrue("$at: ${h.logLines}", EvalCardLog.sessionProtocolPartsLine("object", purposesToken, batteryToken, emptyList()) in h.logLines)
                    if (purposesOn) {
                        assertEquals(at, PurposeUi.Offered(maintenanceOffered = batteryOn), vm.state.value.purposes)
                        assertEquals(at, if (batteryOn) ScanPurposeChoice.values().toList() else listOf(ScanPurposeChoice.NORMAL), offered(vm).choices())
                    } else {
                        assertTrue(at, vm.state.value.purposes is PurposeUi.NotOffered)
                    }
                }
            }
        }
    }

    @Test
    fun `a shop with purposes alone gets the normal chip only - a maintenance chip, a type and a direct send leave nothing the phone, each with the named reason - and normal is sent`() {
        withOwner(purposesAlone) { h, vm ->
            assertEquals(PurposeUi.Offered(), vm.state.value.purposes)
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_BEFORE)
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_AFTER)
            vm.onDefectType(DefectType.BATTERY)
            vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_BEFORE, DefectType.BATTERY)
            assertEquals("nothing leaves the phone", 0, h.callableCalls("setSessionPurpose"))
            assertEquals("each of the four is logged by name", 4, h.logLines.count { it == EvalCardLog.sessionPurposeIgnoredLine("maintenance_not_offered") })
            assertEquals(null, offered(vm).awaitingType)
            assertEquals(null, offered(vm).typeListFor())
            assertEquals(PurposeConfirmed.AtCreation, offered(vm).confirmed)
            // «normal» still goes to the server: it needs `purposes` alone
            vm.onPurposeChip(ScanPurposeChoice.NORMAL)
            assertEquals(mapOf("sessionId" to "sess-1", "purpose" to "normal"), h.purposeRequests.single())
            assertEquals(PurposeConfirmed.Chosen(PurposeSelection.Normal), offered(vm).confirmed)
        }
    }

    @Test
    fun `the server's part-not-enabled refusal is read from its reasonCode - its own state, the chips stay usable, the line carries the code, the next tap is sent - for every status`() {
        for (status in listOf("FAILED_PRECONDITION", "PERMISSION_DENIED", "INTERNAL")) {
            withOwner(bothPartsOn) { h, vm ->
                h.purposeFailure = SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody(status, "made-up message", "PURPOSE_PART_NOT_ENABLED"))
                vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_BEFORE, DefectType.BATTERY)
                assertEquals(status, PurposeFailure.PartNotEnabled, offered(vm).failure)
                assertEquals(status, PurposeConfirmed.AtCreation, offered(vm).confirmed)
                assertTrue("$status: ${h.logLines}", EvalCardLog.sessionPurposeSetLine("sess-1", "maintenance_before", "BATTERY", "refused", "PURPOSE_PART_NOT_ENABLED", "-") in h.logLines)
                assertTrue(status, !purposeChipsLocked(vm.state.value.phase, offered(vm)))
                h.purposeFailure = null
                vm.onPurposeChip(ScanPurposeChoice.NORMAL)
                assertEquals(status, 2, h.callableCalls("setSessionPurpose"))
                assertEquals("$status: a new call clears the refusal", null, offered(vm).failure)
            }
        }
    }

    // ---- a tap ----

    @Test
    fun `a tap on the highlighted chip is sent - a chosen normal must reach the server - and the accepted choice is confirmed`() {
        withOwner(bothPartsOn) { h, vm ->
            vm.selectPurpose(ScanPurposeChoice.NORMAL, typeFor(ScanPurposeChoice.NORMAL))
            assertEquals(1, h.callableCalls("setSessionPurpose"))
            assertEquals(mapOf("sessionId" to "sess-1", "purpose" to "normal"), h.purposeRequests.single())
            assertEquals(PurposeConfirmed.Chosen(selectionOf(ScanPurposeChoice.NORMAL)), offered(vm).confirmed)
            assertEquals(null, offered(vm).inFlight)
            assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeSetLine("sess-1", "normal", "-", "accepted", "-", "-") in h.logLines)
        }
    }

    @Test
    fun `every chip is sent with its own wire value, one call each, and each acceptance moves the highlight`() {
        withOwner(bothPartsOn) { h, vm ->
            for ((i, choice) in ScanPurposeChoice.values().reversed().withIndex()) {
                vm.selectPurpose(choice, typeFor(choice))
                assertEquals(i + 1, h.purposeRequests.size)
                val expected = linkedMapOf<String, Any>("sessionId" to "sess-1", "purpose" to choice.wire).also { request -> typeFor(choice)?.let { request["defectType"] = it.wire } }
                assertEquals(expected, h.purposeRequests.last())
                assertEquals(choice, offered(vm).highlighted())
            }
            assertEquals(listOf("maintenance_after", "maintenance_before", "normal"), h.purposeRequests.map { (it as Map<*, *>)["purpose"] })
        }
    }

    @Test
    fun `the success answer's keys are logged - the contract does not name its shape`() {
        withOwner(bothPartsOn) { h, vm ->
            h.purposeResponse = hashMapOf<String, Any?>("sessionId" to "sess-1", "purpose" to "maintenance_before")
            vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_BEFORE, typeFor(ScanPurposeChoice.MAINTENANCE_BEFORE))
            assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeSetLine("sess-1", "maintenance_before", "BATTERY", "accepted", "-", "purpose.sessionId") in h.logLines)
            h.purposeResponse = null
            vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_AFTER, typeFor(ScanPurposeChoice.MAINTENANCE_AFTER))
            assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeSetLine("sess-1", "maintenance_after", "BATTERY", "accepted", "-", "not_an_object") in h.logLines)
        }
    }

    @Test
    fun `a tap while a call is out is ignored and the call is not repeated - and the held call, answered, confirms its own choice`() {
        withOwner(bothPartsOn) { h, vm ->
            val hold = TaskCompletionSource<com.google.firebase.functions.HttpsCallableResult>()
            h.purposeHold = hold
            vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_BEFORE, typeFor(ScanPurposeChoice.MAINTENANCE_BEFORE))
            assertEquals(selectionOf(ScanPurposeChoice.MAINTENANCE_BEFORE), offered(vm).inFlight)
            assertEquals("not highlighted until the server says yes", ScanPurposeChoice.NORMAL, offered(vm).highlighted())
            vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_AFTER, typeFor(ScanPurposeChoice.MAINTENANCE_AFTER))
            assertEquals(1, h.purposeRequests.size)
            assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeIgnoredLine("in_flight") in h.logLines)
            hold.setResult(h.callableResultOf(hashMapOf<String, Any?>()))
            assertEquals(PurposeConfirmed.Chosen(selectionOf(ScanPurposeChoice.MAINTENANCE_BEFORE)), offered(vm).confirmed)
            assertEquals(null, offered(vm).inFlight)
        }
    }

    @Test
    fun `a shop without the chips ignores a tap - nothing leaves the phone`() {
        withOwner(answer(hashMapOf<String, Any?>("purposes" to false))) { h, vm ->
            vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_BEFORE, typeFor(ScanPurposeChoice.MAINTENANCE_BEFORE))
            assertEquals(0, h.callableCalls("setSessionPurpose"))
            assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeIgnoredLine("not_offered") in h.logLines)
        }
    }

    // ---- a maintenance purpose and its fault type go together, or nothing leaves the phone (contract A2 addendum of 2026-10-22) ----

    @Test
    fun `a maintenance purpose is sent with each of the five fault types - the request carries defectType in the contract's spelling, and the line carries it too`() {
        for (choice in listOf(ScanPurposeChoice.MAINTENANCE_BEFORE, ScanPurposeChoice.MAINTENANCE_AFTER)) {
            for (type in DefectType.values()) {
                withOwner(bothPartsOn) { h, vm ->
                    vm.selectPurpose(choice, type)
                    assertEquals("${choice.wire}/${type.wire}", mapOf("sessionId" to "sess-1", "purpose" to choice.wire, "defectType" to type.wire), h.purposeRequests.single())
                    assertEquals(PurposeConfirmed.Chosen(PurposeSelection.Maintenance(choice, type)), offered(vm).confirmed)
                    assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeSetLine("sess-1", choice.wire, type.wire, "accepted", "-", "-") in h.logLines)
                }
            }
        }
    }

    @Test
    fun `a maintenance purpose with no fault type, and normal with one, are not sent - nothing leaves the phone, the state is untouched and the line says why`() {
        withOwner(bothPartsOn) { h, vm ->
            for (choice in listOf(ScanPurposeChoice.MAINTENANCE_BEFORE, ScanPurposeChoice.MAINTENANCE_AFTER)) {
                vm.selectPurpose(choice, null)
                assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeIgnoredLine("defect_type_missing") in h.logLines)
            }
            vm.selectPurpose(ScanPurposeChoice.NORMAL, DefectType.THERMAL)
            assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeIgnoredLine("defect_type_not_applicable") in h.logLines)
            assertEquals("no call at all", 0, h.callableCalls("setSessionPurpose"))
            assertEquals(PurposeUi.Offered(maintenanceOffered = true), vm.state.value.purposes)
        }
    }

    @Test
    fun `the service refuses the pair itself, before the call - a wrong pair never reaches the SDK even if a caller sends it`() {
        withOwner(bothPartsOn) { h, _ ->
            val missing = runCatching { kotlinx.coroutines.runBlocking { h.service.setSessionPurpose("sess-1", ScanPurposeChoice.MAINTENANCE_AFTER, null) } }.exceptionOrNull()
            assertTrue("$missing", missing is PurposeRequestRejectedException && missing.reason == net.neuralcheck.data.remote.PurposeRequestRejection.DEFECT_TYPE_MISSING)
            val notApplicable = runCatching { kotlinx.coroutines.runBlocking { h.service.setSessionPurpose("sess-1", ScanPurposeChoice.NORMAL, DefectType.SOFTWARE) } }.exceptionOrNull()
            assertTrue("$notApplicable", notApplicable is PurposeRequestRejectedException && notApplicable.reason == net.neuralcheck.data.remote.PurposeRequestRejection.DEFECT_TYPE_NOT_APPLICABLE)
            assertEquals(0, h.callableCalls("setSessionPurpose"))
        }
    }

    // ---- the chips of the screen: a maintenance chip opens its types, a type sends (order of 2026-10-22) ----

    @Test
    fun `a tap on a maintenance chip sends nothing - it opens the five types - and a tap on a type sends the purpose WITH it`() {
        for (choice in listOf(ScanPurposeChoice.MAINTENANCE_BEFORE, ScanPurposeChoice.MAINTENANCE_AFTER)) {
            for (type in DefectType.values()) {
                withOwner(bothPartsOn) { h, vm ->
                    vm.onPurposeChip(choice)
                    assertEquals("${choice.wire}: the chip alone sends nothing", 0, h.callableCalls("setSessionPurpose"))
                    assertEquals(choice, offered(vm).awaitingType)
                    assertEquals(choice, offered(vm).typeListFor())
                    assertEquals("nothing is accepted yet: the creation default stands", PurposeConfirmed.AtCreation, offered(vm).confirmed)
                    vm.onDefectType(type)
                    assertEquals(1, h.callableCalls("setSessionPurpose"))
                    assertEquals("${choice.wire}/${type.wire}", mapOf("sessionId" to "sess-1", "purpose" to choice.wire, "defectType" to type.wire), h.purposeRequests.single())
                    assertEquals(PurposeConfirmed.Chosen(PurposeSelection.Maintenance(choice, type)), offered(vm).confirmed)
                    assertEquals(null, offered(vm).awaitingType)
                    assertEquals("the list stays, with the accepted type", type, offered(vm).highlightedType())
                }
            }
        }
    }

    @Test
    fun `a type tapped while no chip awaits one is ignored - nothing leaves the phone and no default type is invented`() {
        withOwner(bothPartsOn) { h, vm ->
            vm.onDefectType(DefectType.BATTERY)
            assertEquals(0, h.callableCalls("setSessionPurpose"))
            assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeIgnoredLine("no_purpose_awaiting_type") in h.logLines)
            // after «normal» was chosen no chip awaits a type either
            vm.onPurposeChip(ScanPurposeChoice.NORMAL)
            assertEquals(1, h.callableCalls("setSessionPurpose"))
            vm.onDefectType(DefectType.THERMAL)
            assertEquals("still one call: the type had no chip", 1, h.callableCalls("setSessionPurpose"))
            assertEquals(mapOf("sessionId" to "sess-1", "purpose" to "normal"), h.purposeRequests.single())
        }
    }

    @Test
    fun `a tap on normal while a maintenance chip awaits its type sends normal alone - the wait is over`() {
        withOwner(bothPartsOn) { h, vm ->
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_AFTER)
            vm.onPurposeChip(ScanPurposeChoice.NORMAL)
            assertEquals(mapOf("sessionId" to "sess-1", "purpose" to "normal"), h.purposeRequests.single())
            assertEquals(null, offered(vm).awaitingType)
            assertEquals(null, offered(vm).typeListFor())
            vm.onDefectType(DefectType.SOFTWARE)
            assertEquals("the type finds no chip awaiting it", 1, h.callableCalls("setSessionPurpose"))
        }
    }

    @Test
    fun `the other maintenance chip tapped while one awaits - the wait moves, and the type goes with the chip it was tapped for`() {
        withOwner(bothPartsOn) { h, vm ->
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_BEFORE)
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_AFTER)
            assertEquals(ScanPurposeChoice.MAINTENANCE_AFTER, offered(vm).awaitingType)
            vm.onDefectType(DefectType.CHARGING_PORT)
            assertEquals(mapOf("sessionId" to "sess-1", "purpose" to "maintenance_after", "defectType" to "CHARGING_PORT"), h.purposeRequests.single())
        }
    }

    @Test
    fun `changing the type of an accepted maintenance purpose takes ONE tap on another type - the list stays, the server hears every choice, and the chip tapped again sends nothing`() {
        for (choice in listOf(ScanPurposeChoice.MAINTENANCE_BEFORE, ScanPurposeChoice.MAINTENANCE_AFTER)) {
            withOwner(bothPartsOn) { h, vm ->
                vm.onPurposeChip(choice)
                vm.onDefectType(DefectType.BATTERY)
                assertEquals("accepted: nothing awaits a type", null, offered(vm).awaitingType)
                assertEquals("the list stays on the screen", choice, offered(vm).typeListFor())
                // the technician chose the wrong type: ONE tap on another (order of 2026-10-28; it was ignored with no_purpose_awaiting_type)
                vm.onDefectType(DefectType.THERMAL)
                assertEquals(choice.wire, 2, h.callableCalls("setSessionPurpose"))
                assertEquals(PurposeConfirmed.Chosen(PurposeSelection.Maintenance(choice, DefectType.THERMAL)), offered(vm).confirmed)
                assertEquals(DefectType.THERMAL, offered(vm).highlightedType())
                // and again, back to the first
                vm.onDefectType(DefectType.BATTERY)
                assertEquals(3, h.callableCalls("setSessionPurpose"))
                assertEquals(PurposeConfirmed.Chosen(PurposeSelection.Maintenance(choice, DefectType.BATTERY)), offered(vm).confirmed)
                assertEquals(
                    choice.wire,
                    listOf("BATTERY", "THERMAL", "BATTERY"),
                    h.purposeRequests.map { (it as Map<*, *>)["defectType"] }
                )
                assertEquals(listOf(choice.wire), h.purposeRequests.map { (it as Map<*, *>)["purpose"] }.distinct())
                // the chip tapped again opens its list and sends nothing; the accepted type is still the highlighted one while the technician looks
                vm.onPurposeChip(choice)
                assertEquals("asking again sends nothing", 3, h.callableCalls("setSessionPurpose"))
                assertEquals(DefectType.BATTERY, offered(vm).highlightedType())
            }
        }
        // the other maintenance chip after an accepted one: it sends nothing alone, and its type goes with IT
        withOwner(bothPartsOn) { h, vm ->
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_BEFORE)
            vm.onDefectType(DefectType.BATTERY)
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_AFTER)
            assertEquals(1, h.callableCalls("setSessionPurpose"))
            vm.onDefectType(DefectType.THERMAL)
            assertEquals(
                listOf("maintenance_before" to "BATTERY", "maintenance_after" to "THERMAL"),
                h.purposeRequests.map { (it as Map<*, *>)["purpose"] to (it as Map<*, *>)["defectType"] }
            )
            assertEquals(PurposeConfirmed.Chosen(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_AFTER, DefectType.THERMAL)), offered(vm).confirmed)
        }
    }

    @Test
    fun `a call that fails keeps the chip awaiting its type - the next tap on a type is sent, and the accepted purpose stays what it was`() {
        withOwner(bothPartsOn) { h, vm ->
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_BEFORE)
            h.purposeFailure = SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody("PERMISSION_DENIED", "made-up", "SHOP_NO_CREDIT"))
            vm.onDefectType(DefectType.MOTHERBOARD)
            assertEquals(PurposeFailure.Refused("SHOP_NO_CREDIT"), offered(vm).failure)
            assertEquals(ScanPurposeChoice.MAINTENANCE_BEFORE, offered(vm).awaitingType)
            assertEquals(PurposeConfirmed.AtCreation, offered(vm).confirmed)
            h.purposeFailure = null
            vm.onDefectType(DefectType.MOTHERBOARD)
            assertEquals(2, h.callableCalls("setSessionPurpose"))
            assertEquals(PurposeConfirmed.Chosen(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_BEFORE, DefectType.MOTHERBOARD)), offered(vm).confirmed)
        }
    }

    @Test
    fun `after the customer's phone joins neither a chip nor a type sends - and a refused choice by the not-pending lock locks both for good`() {
        withOwner(bothPartsOn) { h, vm ->
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_AFTER)
            h.deliverSnapshot("sess-1", fields = mapOf("status" to "scanning"))
            vm.onDefectType(DefectType.BATTERY)
            vm.onPurposeChip(ScanPurposeChoice.NORMAL)
            assertEquals(0, h.callableCalls("setSessionPurpose"))
            assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeIgnoredLine("not_pending") in h.logLines)
        }
        withOwner(bothPartsOn) { h, vm ->
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_AFTER)
            h.purposeFailure = refusalNotPending()
            vm.onDefectType(DefectType.BATTERY)
            assertEquals(PurposeFailure.NotPending, offered(vm).failure)
            h.purposeFailure = null
            vm.onDefectType(DefectType.THERMAL)
            vm.onPurposeChip(ScanPurposeChoice.NORMAL)
            assertEquals("locked: no second call", 1, h.callableCalls("setSessionPurpose"))
            assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeIgnoredLine("locked") in h.logLines)
        }
    }

    @Test
    fun `a new code resets the wait for a type - and a shop without the chips ignores both taps`() {
        withOwner(bothPartsOn) { h, vm ->
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_BEFORE)
            h.createResponse = bothPartsOn.also { it["sessionId"] = "sess-2"; it["code"] = "NC-5678" }
            vm.create()
            assertEquals(PurposeUi.Offered(maintenanceOffered = true), vm.state.value.purposes)
            vm.onDefectType(DefectType.BATTERY)
            assertEquals(0, h.callableCalls("setSessionPurpose"))
        }
        withOwner(answer(hashMapOf<String, Any?>("purposes" to false))) { h, vm ->
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_BEFORE)
            vm.onDefectType(DefectType.BATTERY)
            assertEquals(0, h.callableCalls("setSessionPurpose"))
            assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeIgnoredLine("not_offered") in h.logLines)
        }
    }

    // ---- the session is pending only until the customer's phone joins ----

    @Test
    fun `after the customer's phone joins the owner's screen is scanning and a tap is ignored - for every status the listener can deliver`() {
        for (status in listOf("scanning", "completed", "expired", "cancelled")) {
            withOwner(bothPartsOn) { h, vm ->
                h.deliverSnapshot("sess-1", fields = mapOf("status" to status))
                assertTrue("status $status leaves WAITING", vm.state.value.phase != Phase.WAITING)
                vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_BEFORE, typeFor(ScanPurposeChoice.MAINTENANCE_BEFORE))
                assertEquals("status $status", 0, h.callableCalls("setSessionPurpose"))
                assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeIgnoredLine("not_pending") in h.logLines)
                assertTrue("the chips are locked for the surface", purposeChipsLocked(vm.state.value.phase, offered(vm)))
            }
        }
    }

    @Test
    fun `a pending status keeps the chips usable`() {
        withOwner(bothPartsOn) { h, vm ->
            h.deliverSnapshot("sess-1", fields = mapOf("status" to "pending"))
            assertEquals(Phase.WAITING, vm.state.value.phase)
            vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_AFTER, typeFor(ScanPurposeChoice.MAINTENANCE_AFTER))
            assertEquals(1, h.callableCalls("setSessionPurpose"))
        }
    }

    @Test
    fun `a join that happened between the tap and the call - the not-pending refusal - leaves the confirmed purpose, locks the chips for good, and any later tap is ignored`() {
        for (status in listOf("FAILED_PRECONDITION", "PERMISSION_DENIED", "ABORTED")) {
            withOwner(bothPartsOn) { h, vm ->
                h.purposeFailure = refusalNotPending(status)
                vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_BEFORE, typeFor(ScanPurposeChoice.MAINTENANCE_BEFORE))
                assertEquals("status $status: the server did not accept, so the app still holds the creation default", PurposeConfirmed.AtCreation, offered(vm).confirmed)
                assertEquals(PurposeFailure.NotPending, offered(vm).failure)
                assertEquals(null, offered(vm).inFlight)
                assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeSetLine("sess-1", "maintenance_before", "BATTERY", "refused", "PURPOSE_SESSION_NOT_PENDING", "-") in h.logLines)
                assertEquals("the screen is still WAITING (the listener has not said otherwise) and the chips are locked", Phase.WAITING, vm.state.value.phase)
                assertTrue(purposeChipsLocked(vm.state.value.phase, offered(vm)))
                h.purposeFailure = null
                vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_AFTER, typeFor(ScanPurposeChoice.MAINTENANCE_AFTER))
                assertEquals("locked: no second call", 1, h.callableCalls("setSessionPurpose"))
                assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeIgnoredLine("locked") in h.logLines)
            }
        }
    }

    // ---- the other ends of a call ----

    @Test
    fun `a refusal with another reasonCode leaves the chips usable - the next tap is sent`() {
        withOwner(bothPartsOn) { h, vm ->
            h.purposeFailure = SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody("PERMISSION_DENIED", "made-up", "SHOP_NO_CREDIT"))
            vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_BEFORE, typeFor(ScanPurposeChoice.MAINTENANCE_BEFORE))
            assertEquals(PurposeFailure.Refused("SHOP_NO_CREDIT"), offered(vm).failure)
            assertEquals(PurposeConfirmed.AtCreation, offered(vm).confirmed)
            assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeSetLine("sess-1", "maintenance_before", "BATTERY", "refused", "SHOP_NO_CREDIT", "-") in h.logLines)
            h.purposeFailure = null
            vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_BEFORE, typeFor(ScanPurposeChoice.MAINTENANCE_BEFORE))
            assertEquals(2, h.callableCalls("setSessionPurpose"))
            assertEquals(PurposeConfirmed.Chosen(selectionOf(ScanPurposeChoice.MAINTENANCE_BEFORE)), offered(vm).confirmed)
            assertEquals("a new call clears the earlier failure", null, offered(vm).failure)
        }
    }

    @Test
    fun `a refusal with an unknown reasonCode is a refusal, not the lock and not a disconnect`() {
        withOwner(bothPartsOn) { h, vm ->
            h.purposeFailure = SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody("INTERNAL", "made-up", "PURPOSE_BRAND_NEW"))
            vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_AFTER, typeFor(ScanPurposeChoice.MAINTENANCE_AFTER))
            assertEquals(PurposeFailure.Refused("unrecognized"), offered(vm).failure)
            assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeSetLine("sess-1", "maintenance_after", "BATTERY", "refused", "unrecognized", "-") in h.logLines)
        }
    }

    @Test
    fun `a disconnect and a plain failure are failures with no reasonCode - the chips stay usable and the confirmed purpose is untouched`() {
        for ((label, failure) in SessionWireFixtures.offlineFailures() + ("a plain exception" to RuntimeException("PURPOSE_SESSION_NOT_PENDING"))) {
            withOwner(bothPartsOn) { h, vm ->
                h.purposeFailure = failure
                vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_BEFORE, typeFor(ScanPurposeChoice.MAINTENANCE_BEFORE))
                assertEquals(label, PurposeFailure.Failed, offered(vm).failure)
                assertEquals(label, PurposeConfirmed.AtCreation, offered(vm).confirmed)
                assertTrue("$label: ${h.logLines}", EvalCardLog.sessionPurposeSetLine("sess-1", "maintenance_before", "BATTERY", "failed", "absent", "-") in h.logLines)
                assertEquals(label, Phase.WAITING, vm.state.value.phase)
                assertTrue(label, !purposeChipsLocked(vm.state.value.phase, offered(vm)))
            }
        }
    }

    // ---- a new code ----

    @Test
    fun `a new code resets the chips for the new session, and a late answer for the old one does not touch them`() {
        withOwner(bothPartsOn) { h, vm ->
            val hold = TaskCompletionSource<com.google.firebase.functions.HttpsCallableResult>()
            h.purposeHold = hold
            vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_BEFORE, typeFor(ScanPurposeChoice.MAINTENANCE_BEFORE))
            h.purposeHold = null
            h.createResponse = bothPartsOn.also { it["sessionId"] = "sess-2"; it["code"] = "NC-5678" }
            vm.create()
            assertEquals("sess-2", vm.state.value.sessionId)
            assertEquals(PurposeUi.Offered(maintenanceOffered = true), vm.state.value.purposes)
            hold.setResult(h.callableResultOf(hashMapOf<String, Any?>()))
            assertEquals("the answer named sess-1: sess-2's chips are as created", PurposeUi.Offered(maintenanceOffered = true), vm.state.value.purposes)
            vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_AFTER, typeFor(ScanPurposeChoice.MAINTENANCE_AFTER))
            assertEquals("sess-2", (h.purposeRequests.last() as Map<*, *>)["sessionId"])
        }
    }

    @Test
    fun `a failed creation has no chips and a tap goes nowhere`() {
        withOwner(hashMapOf<String, Any?>("sessionId" to "sess-1", "protocolParts" to hashMapOf<String, Any?>("purposes" to true))) { h, vm ->
            assertEquals(Phase.ERROR, vm.state.value.phase)
            assertEquals("a failed creation reads no parts", PurposeUi.NotRead, vm.state.value.purposes)
            vm.selectPurpose(ScanPurposeChoice.MAINTENANCE_BEFORE, typeFor(ScanPurposeChoice.MAINTENANCE_BEFORE))
            assertEquals(0, h.callableCalls("setSessionPurpose"))
        }
    }

    private companion object {
        /** A marker: the answer carries NO `protocolParts` key at all (not a JSON null). */
        val NO_KEY = Any()
    }
}
