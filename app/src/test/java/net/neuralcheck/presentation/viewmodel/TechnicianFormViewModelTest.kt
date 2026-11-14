package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.TaskCompletionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.domain.form.FormRequestBuild
import net.neuralcheck.domain.form.FunctionalRow
import net.neuralcheck.domain.form.GradeAnswer
import net.neuralcheck.domain.form.GradeCategory
import net.neuralcheck.domain.form.PromptAction
import net.neuralcheck.domain.form.PromptState
import net.neuralcheck.domain.form.RowAnswer
import net.neuralcheck.domain.form.SlotClosedReason
import net.neuralcheck.domain.form.SubmitBlock
import net.neuralcheck.domain.form.Symptom
import net.neuralcheck.domain.form.TechnicianFormRequest
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel.ConfigUi
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel.FormFailure
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel.Submission
import net.neuralcheck.data.remote.FormCallsNotApprovedException
import net.neuralcheck.util.EvalCardLog
import net.neuralcheck.util.InMemorySharedPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The technician's form through the REAL path - A-P6-08. Rules 30 and 37.
 *
 * Real: [TechnicianFormViewModel], `FirebaseAnalysisService.getTechnicianFormConfig` / `submitTechnicianForm` (the callable names, the request map, the Gson parse of the answer), the config reader, the
 * form logic and the request builder, the SDK's own parse of a refusal body ([SessionWireFixtures.exceptionFromWire]). Replaced: only the HTTP round trip and auth (the harness) and - declared - the form's
 * exit gate, opened by [RealJoinPathHarness.openFormCalls] for most tests, shut by [RealJoinPathHarness.closeFormCalls] for the one that shows a shut gate, and left as built for ONE (`the real gate is open`,
 * rule 37: the guard is true since 2026-10-23). The screen is not drawn here - see `TechnicianFormSurfaceTest` for what is drawn with the test's own labels and `FormLiveScreenShapeTest` with the real ones.
 *
 * The config answers are the server's two returns as its handler spells them (technicianForm.js:312 and :325-336 in `evidence/run-2026-10-18/technician-form-server-wire-neuralcheck-backend-05275ba.md`,
 * neuralcheck-backend@05275ba - written out key by key: the handler cannot run here). NOT verified against the server: the success answer of the submit (the harness answers an empty object; the server's is
 * `{sessionId, slot, formState}` and the app reads none of it), and the value of `settingsVersion`. The refusal bodies' status and message are made up and vary on purpose: the app reads
 * `details.reasonCode` only (rule 21).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TechnicianFormViewModelTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun technicians(vararg ids: String): List<Map<String, Any?>> = ids.map { hashMapOf<String, Any?>("technicianId" to it, "displayName" to "name-of-$it") }

    private fun slot(n: Int, state: String, drawn: Boolean? = null): HashMap<String, Any?> =
        hashMapOf<String, Any?>("slot" to n, "formState" to state).also { if (drawn != null) it["drawn"] = drawn }

    /** technicianForm.js:325-336 - the shop has the form on. A part given as null is left out of the answer. */
    private fun configAnswer(
        order: List<String>? = listOf("charging_slow", "battery_swollen"),
        techs: List<Map<String, Any?>>? = technicians("t1", "t2"),
        slots: List<Map<String, Any?>>? = listOf(slot(1, "not_submitted"), slot(2, "not_submitted", drawn = false))
    ): HashMap<String, Any?> =
        hashMapOf<String, Any?>("sessionId" to "sess-1", "formContractVersion" to 2, "enabled" to true, "settingsVersion" to "x").also {
            if (order != null) it["symptomOrder"] = order
            if (techs != null) it["technicians"] = techs
            if (slots != null) it["slots"] = slots
        }

    /** technicianForm.js:312 - the shop has the form off. */
    private fun notEnabledAnswer(): HashMap<String, Any?> = hashMapOf<String, Any?>("sessionId" to "sess-1", "formContractVersion" to 2, "enabled" to false, "formState" to "not_enabled")

    private class Rig(val h: RealJoinPathHarness, val vm: TechnicianFormViewModel, val store: TechnicianChoiceStore) {
        var clock = 10_000L
        val form get() = vm.state.value.form!!
    }

    private fun withForm(
        config: Any? = configAnswer(), store: TechnicianChoiceStore = TechnicianChoiceStore(InMemorySharedPreferences()), open: Boolean = true, gateOpen: Boolean? = true, check: Rig.() -> Unit
    ) {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            // gateOpen: true opens the gate (a test says what it relies on), false SHUTS it (the real one reads the guard, which is true), null leaves the REAL gate as the service was built (rule 37)
            when (gateOpen) {
                true -> h.openFormCalls()
                false -> h.closeFormCalls()
                null -> Unit
            }
            h.techConfigResponse = config
            val vm = TechnicianFormViewModel(h.service, store)
            val rig = Rig(h, vm, store)
            vm.nowMs = { rig.clock }
            if (open) vm.open("sess-1", 1)
            rig.check()
        }
    }

    private fun refusal(code: String, status: String = "FAILED_PRECONDITION") =
        SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody(status, "made-up message", code))

    /** A form that can be sent: a technician and one judgement. */
    private fun Rig.fill() {
        vm.chooseTechnician("t1")
        clock += 1_000
        vm.grade(GradeCategory.OVERALL, GradeAnswer.GOOD)
    }

    // ---- the configuration ----

    @Test
    fun `opening loads the configuration and opens the form with the order it names and nobody chosen`() = withForm {
        val ready = vm.state.value.config as ConfigUi.Ready
        assertEquals(listOf("t1", "t2"), ready.technicians.map { it.id })
        assertEquals(listOf("name-of-t1", "name-of-t2"), ready.technicians.map { it.displayName })
        assertEquals(1, h.callableCalls("getTechnicianFormConfig"))
        assertEquals("the server wants {sessionId} (technicianForm.js:150-157) - a call without it is refused FORM_SESSION_ID_REQUIRED", listOf<Any?>(hashMapOf<String, Any?>("sessionId" to "sess-1")), h.techConfigRequests.toList())
        assertTrue(h.logLines.toString(), EvalCardLog.technicianFormConfigLine("object", "server", 2, "offered") in h.logLines)
        assertEquals(10_000L, form.openedAtMs)
        assertNull("nobody is chosen for the technician", form.technicianId)
        assertEquals(listOf(Symptom.CHARGING_SLOW, Symptom.BATTERY_SWOLLEN) + Symptom.TWELVE.filter { it != Symptom.CHARGING_SLOW && it != Symptom.BATTERY_SWOLLEN }, form.symptomOrderShown)
        assertEquals("sess-1", form.sessionId)
        assertEquals(1, form.slot)
    }

    @Test
    fun `the technician this phone chose last is preselected only if the list still has him`() {
        val store = TechnicianChoiceStore(InMemorySharedPreferences())
        store.remember("t2")
        withForm(store = store) { assertEquals("t2", form.technicianId) }
        store.remember("gone")
        withForm(store = store) { assertNull("an id the list does not have is not a choice", form.technicianId) }
    }

    @Test
    fun `an enabled shop whose answer names nobody usable offers no form - NoTechnicians, every way it can be`() {
        // replaces "an answer that names no technician offers no form - every way it can be" (rule 23): a missing key or a non-object answer is no longer "no technicians" - it is unreadable (below)
        val lists = listOf(
            emptyList<Map<String, Any?>>(),
            listOf(hashMapOf<String, Any?>("technicianId" to "t", "displayName" to "x", "active" to false)),
            listOf(hashMapOf<String, Any?>("technicianId" to "t", "displayName" to null))
        )
        for (techs in lists) {
            withForm(config = configAnswer(techs = techs)) {
                assertEquals("technicians $techs", ConfigUi.NoTechnicians, vm.state.value.config)
                assertNull(vm.state.value.form)
                assertTrue(h.logLines.toString(), EvalCardLog.technicianFormConfigLine("object", "-", 0, "no_technicians") in h.logLines)
                vm.grade(GradeCategory.BATTERY, GradeAnswer.GOOD)
                vm.submit()
                assertEquals("nothing leaves the phone", 0, h.callableCalls("submitTechnicianForm"))
            }
        }
    }

    @Test
    fun `a shop with the form off is NotEnabled - a state of its own, not no technicians - and no form is offered`() = withForm(config = notEnabledAnswer()) {
        assertEquals(ConfigUi.NotEnabled, vm.state.value.config)
        assertTrue("a different state from NoTechnicians", vm.state.value.config != ConfigUi.NoTechnicians)
        assertNull(vm.state.value.form)
        assertTrue(h.logLines.toString(), EvalCardLog.technicianFormConfigLine("object", "-", 0, "not_enabled") in h.logLines)
        vm.grade(GradeCategory.BATTERY, GradeAnswer.GOOD)
        vm.submit()
        assertEquals("nothing leaves the phone", 0, h.callableCalls("submitTechnicianForm"))
        vm.retryConfig()
        assertEquals("a shop that has the form off is not asked again by a retry", 1, h.callableCalls("getTechnicianFormConfig"))
    }

    @Test
    fun `a slot already submitted is closed - the form is not offered again, and the log says why`() {
        withForm(config = configAnswer(slots = listOf(slot(1, "submitted"), slot(2, "not_submitted", drawn = false)))) {
            assertEquals(ConfigUi.SlotClosed(SlotClosedReason.SUBMITTED), vm.state.value.config)
            assertNull(vm.state.value.form)
            assertTrue(h.logLines.toString(), EvalCardLog.technicianFormConfigLine("object", "-", 0, "slot_submitted") in h.logLines)
        }
        // the second slot: closed until the double evaluation drew it
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.openFormCalls()
            h.techConfigResponse = configAnswer()
            val vm = TechnicianFormViewModel(h.service, TechnicianChoiceStore(InMemorySharedPreferences()))
            vm.open("sess-1", 2)
            assertEquals(ConfigUi.SlotClosed(SlotClosedReason.NOT_DRAWN), vm.state.value.config)
            h.techConfigResponse = configAnswer(slots = listOf(slot(1, "submitted"), slot(2, "not_submitted", drawn = true)))
            vm.open("sess-2", 2)
            val ready = vm.state.value.config as ConfigUi.Ready
            assertEquals(listOf("t1", "t2"), ready.technicians.map { it.id })
            assertEquals("the form opened is the second slot's", 2, vm.state.value.form!!.slot)
        }
    }

    @Test
    fun `an answer the app cannot read is Unreadable - never NotEnabled and never NoTechnicians - and asking again with a good answer opens the form`() {
        val enabledAsText = configAnswer().also { it["enabled"] = "yes" }
        val otherVersion = configAnswer().also { it["formContractVersion"] = 3 }
        val unreadable = listOf<Pair<Any?, String>>(
            null to "absent",
            hashMapOf<String, Any?>() to "contract_version_absent",
            "not an object" to "not_an_object_string",
            configAnswer(techs = null) to "technicians_absent",
            configAnswer(slots = null) to "slots_absent",
            enabledAsText to "enabled_not_a_boolean_string",
            otherVersion to "contract_version_differs",
            hashMapOf<String, Any?>("sessionId" to "sess-1", "formContractVersion" to 2, "enabled" to false) to "not_enabled_no_form_state"
        )
        for ((answer, why) in unreadable) {
            withForm(config = answer) {
                assertEquals("answer $answer", ConfigUi.Unreadable(why), vm.state.value.config)
                assertNull(vm.state.value.form)
                assertTrue(h.logLines.toString(), h.logLines.any { it.startsWith("technician-form-config ") && it.endsWith(" offer=unreadable_$why") })
                h.techConfigResponse = configAnswer()
                vm.retryConfig()
                assertTrue("answer $answer", vm.state.value.config is ConfigUi.Ready)
                assertEquals(2, h.callableCalls("getTechnicianFormConfig"))
            }
        }
    }

    /**
     * Rule 23: REPLACES `the real gate is closed - while TECH_FORM_TEXTS_APPROVED is false nothing leaves the phone, not the configuration and not the submit` (it pinned the guard false, and the real gate with
     * it). The guard is TRUE since 2026-10-23; the REAL gate - the service as built, nothing opened or shut by the test - reads it, so the configuration is asked for through the real service and the form opens.
     */
    @Test
    fun `the real gate is open - the guard is true, so with nothing opened by the test the configuration leaves the phone through the real service and the form opens`() {
        assertTrue("the guard of the surface is true since 2026-10-23", net.neuralcheck.presentation.screen.TECH_FORM_TEXTS_APPROVED)
        withForm(gateOpen = null) {
            assertTrue("nobody opened the gate", h.service.formCallsOpen())
            assertTrue("the configuration was asked for and the form is ready", vm.state.value.config is ConfigUi.Ready)
            assertEquals(1, h.callableCalls("getTechnicianFormConfig"))
        }
    }

    @Test
    fun `a shut gate - nothing leaves the phone, not the configuration and not the submit`() {
        withForm(gateOpen = false) {
            // nothing replaced but the gate's state: the real service, the real view model asked the way the screen asks it
            assertEquals("the configuration was asked for and the gate answered", ConfigUi.Failed, vm.state.value.config)
            assertTrue(h.logLines.toString(), EvalCardLog.technicianFormEventLine("config-failed", "sess-1", "absent") in h.logLines)
            assertEquals("no call of any callable", 0, h.events.count { it.startsWith("callable:") })
            // the submit and the configuration, straight at the service
            val submit = runCatching { kotlinx.coroutines.runBlocking { h.service.submitTechnicianForm(emptyMap()) } }.exceptionOrNull()
            assertTrue("$submit", submit is FormCallsNotApprovedException)
            val config = runCatching { kotlinx.coroutines.runBlocking { h.service.getTechnicianFormConfig("sess-1") } }.exceptionOrNull()
            assertTrue("$config", config is FormCallsNotApprovedException)
            assertEquals(0, h.events.count { it.startsWith("callable:") })
        }
    }

    @Test
    fun `a failed configuration call is a failure, and asking again with a good answer opens the form`() {
        for ((label, failure) in SessionWireFixtures.offlineFailures()) {
            withForm(open = false) {
                h.techConfigFailure = failure
                vm.open("sess-1", 1)
                assertEquals(label, ConfigUi.Failed, vm.state.value.config)
                assertNull(vm.state.value.form)
                h.techConfigFailure = null
                vm.retryConfig()
                assertTrue(label, vm.state.value.config is ConfigUi.Ready)
                assertEquals(label, 2, h.callableCalls("getTechnicianFormConfig"))
            }
        }
        withForm { vm.retryConfig(); assertEquals("retry only after a failure", 1, h.callableCalls("getTechnicianFormConfig")) }
    }

    @Test
    fun `opening the form that is open keeps what was filled in, and another session starts a new one`() = withForm {
        vm.chooseTechnician("t1")
        vm.grade(GradeCategory.BATTERY, GradeAnswer.FAIR)
        vm.open("sess-1", 1)
        assertEquals(GradeAnswer.FAIR, form.grades[GradeCategory.BATTERY])
        assertEquals(1, h.callableCalls("getTechnicianFormConfig"))
        vm.open("sess-2", 1)
        assertEquals("sess-2", form.sessionId)
        assertTrue("a new form: nothing answered", form.grades.isEmpty())
        assertEquals(2, h.callableCalls("getTechnicianFormConfig"))
    }

    // ---- the taps ----

    @Test
    fun `each tap is stamped with the device clock and recorded, and choosing a technician is not a judgement`() = withForm {
        clock = 20_000
        vm.chooseTechnician("t1")
        assertNull(form.firstTapAtMs)
        clock = 21_000
        vm.grade(GradeCategory.BATTERY, GradeAnswer.GOOD)
        clock = 22_500
        vm.functional(FunctionalRow.CAMERA, RowAnswer.FAULTY)
        clock = 23_000
        vm.toggleSymptom(Symptom.CHARGING_SLOW)
        assertEquals(21_000L, form.firstTapAtMs)
        assertEquals(listOf("battery" to 21_000L, "camera" to 22_500L, "symptoms" to 23_000L), form.perAnswerAt.map { it.item to it.atMs })
        assertEquals("t1", form.technicianId)
    }

    @Test
    fun `submit with nobody chosen, a missing mandatory note or nothing answered does nothing but say why - and the next tap clears it`() = withForm {
        vm.grade(GradeCategory.BATTERY, GradeAnswer.WEAK)
        vm.submit()
        assertEquals(SubmitBlock.NO_TECHNICIAN, vm.state.value.blocked)
        vm.chooseTechnician("t1")
        assertNull("a tap clears the reason", vm.state.value.blocked)
        vm.submit()
        assertEquals(SubmitBlock.NOTE_REQUIRED, vm.state.value.blocked)
        vm.note("swollen")
        assertNull(vm.state.value.blocked)
        assertEquals("no call for any of them", 0, h.callableCalls("submitTechnicianForm"))
    }

    @Test
    fun `nothing answered is blocked too`() = withForm {
        vm.chooseTechnician("t1")
        vm.submit()
        assertEquals(SubmitBlock.NO_ANSWERS, vm.state.value.blocked)
        assertEquals(0, h.callableCalls("submitTechnicianForm"))
    }

    // ---- the contradiction prompt ----

    private fun Rig.contradicting() {
        vm.chooseTechnician("t1")
        clock += 1_000; vm.grade(GradeCategory.BATTERY, GradeAnswer.WEAK)
        clock += 1_000; vm.grade(GradeCategory.OVERALL, GradeAnswer.GOOD)
        clock += 1_000; vm.note("back cover raised")
    }

    @Test
    fun `a contradicting form opens the prompt instead of sending, and while it is open every tap is ignored`() = withForm {
        contradicting()
        vm.submit()
        assertTrue(vm.state.value.promptOpen)
        assertEquals(0, h.callableCalls("submitTechnicianForm"))
        assertTrue(form.prompt is PromptState.Shown)
        vm.grade(GradeCategory.THERMAL, GradeAnswer.EXCELLENT)
        assertNull("ignored while the prompt is open", form.grades[GradeCategory.THERMAL])
        vm.submit()
        assertEquals("a second submit while the prompt is open does nothing", 0, h.callableCalls("submitTechnicianForm"))
    }

    @Test
    fun `confirming sends the form with the action and the answers before and after`() = withForm {
        contradicting()
        vm.submit()
        vm.confirmPrompt()
        assertEquals(1, h.callableCalls("submitTechnicianForm"))
        val request = h.techSubmitRequests.single() as Map<*, *>
        assertEquals("confirmed_send", request["promptAction"])
        assertEquals(request["answersBeforePrompt"], request["answersAfterPrompt"])
        assertEquals(Submission.Submitted, vm.state.value.submission)
        assertEquals(false, vm.state.value.promptOpen)
    }

    @Test
    fun `reviewing goes back to the form with nothing sent - and the second submit goes out without a second prompt`() = withForm {
        contradicting()
        vm.submit()
        vm.revisePrompt()
        assertEquals(false, vm.state.value.promptOpen)
        assertEquals(0, h.callableCalls("submitTechnicianForm"))
        assertEquals(PromptAction.REVISE, (form.prompt as PromptState.Shown).action)
        clock += 5_000
        vm.grade(GradeCategory.OVERALL, GradeAnswer.FAIR)
        vm.submit()
        assertEquals(1, h.callableCalls("submitTechnicianForm"))
        val request = h.techSubmitRequests.single() as Map<*, *>
        assertEquals("will_review", request["promptAction"])
        assertEquals(mapOf("battery" to "weak", "performance" to null, "thermal" to null, "overall" to "good"), request["answersBeforePrompt"])
        assertEquals(mapOf("battery" to "weak", "performance" to null, "thermal" to null, "overall" to "fair"), request["answersAfterPrompt"])
    }

    // ---- the send ----

    @Test
    fun `a sent form is final - it is the builder's request, the technician is remembered, and every later tap and submit is ignored`() = withForm {
        fill()
        clock += 3_000
        val expected = (TechnicianFormRequest.build(form, submittedAtMs = clock) as FormRequestBuild.Built).request
        vm.submit()
        assertEquals(1, h.callableCalls("submitTechnicianForm"))
        assertEquals("exactly what the real builder makes of the form", expected, h.techSubmitRequests.single())
        assertEquals(Submission.Submitted, vm.state.value.submission)
        assertEquals("t1", store.last())
        assertTrue(h.logLines.toString(), EvalCardLog.technicianFormEventLine("submitted", "sess-1", "slot1") in h.logLines)
        vm.grade(GradeCategory.BATTERY, GradeAnswer.WEAK)
        vm.note("late")
        vm.chooseTechnician("t2")
        vm.submit()
        assertNull("locked", form.grades[GradeCategory.BATTERY])
        assertEquals("", form.observedNote)
        assertEquals("t1", form.technicianId)
        assertEquals("no second send", 1, h.callableCalls("submitTechnicianForm"))
    }

    @Test
    fun `while the call is out the form is locked and a second press does not send twice`() = withForm {
        fill()
        val hold = TaskCompletionSource<com.google.firebase.functions.HttpsCallableResult>()
        h.techSubmitHold = hold
        vm.submit()
        assertEquals(Submission.Sending, vm.state.value.submission)
        vm.submit()
        vm.grade(GradeCategory.BATTERY, GradeAnswer.WEAK)
        assertEquals(1, h.callableCalls("submitTechnicianForm"))
        assertNull(form.grades[GradeCategory.BATTERY])
        hold.setResult(h.callableResultOf(hashMapOf<String, Any?>()))
        assertEquals(Submission.Submitted, vm.state.value.submission)
    }

    @Test
    fun `a refusal is classified by its reasonCode - the same-technician one is named, any other FORM code is a refusal with its token, and the form stays as it was`() {
        val cases = listOf(
            "FORM_SLOT_SAME_TECHNICIAN" to FormFailure.SameTechnician,
            "FORM_NOTE_TOO_LONG" to FormFailure.Refused("FORM_NOTE_TOO_LONG"),
            "FORM_ALREADY_SUBMITTED" to FormFailure.Refused("FORM_ALREADY_SUBMITTED"),
            "BRAND_NEW_REFUSAL" to FormFailure.Refused("unrecognized"),
            "SHOP_NO_CREDIT" to FormFailure.Refused("SHOP_NO_CREDIT")
        )
        for ((code, expected) in cases) {
            for (status in listOf("FAILED_PRECONDITION", "PERMISSION_DENIED", "INVALID_ARGUMENT")) {
                withForm {
                    fill()
                    val before = form
                    h.techSubmitFailure = refusal(code, status)
                    vm.submit()
                    assertEquals("$code / $status", Submission.Editing(expected), vm.state.value.submission)
                    assertTrue(h.logLines.toString(), EvalCardLog.technicianFormEventLine("submit-failed", "sess-1", code) in h.logLines)
                    assertEquals("the form is exactly as it was", before, form)
                    // and the technician can send again
                    h.techSubmitFailure = null
                    vm.submit()
                    assertEquals("$code / $status", Submission.Submitted, vm.state.value.submission)
                    assertEquals(2, h.callableCalls("submitTechnicianForm"))
                }
            }
        }
    }

    @Test
    fun `a disconnect and a plain failure are not-sent failures, never a refusal`() {
        for ((label, failure) in SessionWireFixtures.offlineFailures() + ("a plain exception" to RuntimeException("FORM_SLOT_SAME_TECHNICIAN"))) {
            withForm {
                fill()
                h.techSubmitFailure = failure
                vm.submit()
                assertEquals(label, Submission.Editing(FormFailure.NotSent), vm.state.value.submission)
            }
        }
    }

    @Test
    fun `a form that cannot be built is not sent - nothing leaves the phone`() = withForm {
        fill()
        // a state built outside the input's limit (the input itself refuses it) - the builder refuses it too
        vm.note("x")
        val tooLong = "x".repeat(201)
        val field = TechnicianFormViewModel::class.java.getDeclaredField("_state").also { it.isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(vm) as kotlinx.coroutines.flow.MutableStateFlow<TechnicianFormViewModel.UiState>
        flow.value = flow.value.copy(form = flow.value.form!!.copy(observedNote = tooLong))
        vm.submit()
        assertEquals(Submission.Editing(FormFailure.Unbuildable), vm.state.value.submission)
        assertEquals(0, h.callableCalls("submitTechnicianForm"))
    }
}
