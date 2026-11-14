package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.domain.form.FormRequestBuild
import net.neuralcheck.domain.form.GradeAnswer
import net.neuralcheck.domain.form.GradeCategory
import net.neuralcheck.domain.form.SlotClosedReason
import net.neuralcheck.domain.form.Symptom
import net.neuralcheck.domain.form.TechnicianFormRequest
import net.neuralcheck.domain.form.openForm
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel.ConfigUi
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel.FormFailure
import net.neuralcheck.presentation.viewmodel.TechnicianFormViewModel.Submission
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
 * The double evaluation on the technician's phone - A-P6-10, the APP half (PLAN-06 section 10-b item 6, lines 1300-1309; the screen is row ف٩, line 951). Rules 22, 30, 37.
 *
 * What the plan says (quoted): «الخادم يختار عند إنشاء الجلسة بنسبة `doubleEvalRate`، ويخزّن السحب. والاستمارة الثانية `slot: 2`: يرفضها الخادم إن كان `technicianId` هو الأوّل (رفض مسمّى). ولا يُعيد الخادم
 * استمارةً لأيّ عميل، فـ«لا يرى الأوّل» بالقواعد. وعلى الجهاز نفسه تُفرَّغ الشاشة بعد الإرسال.» and «قائمة أسماء من المشرف، يُختار منها بلمسة. ويُحفظ آخر اختيار للجهاز [اجتهاد]». The server's half
 * (S-P6-10, `fd730ac`, NOT deployed) writes the draw and the technician's phone learns it from `getTechnicianFormConfig`: `slots[1].drawn` (contract A7 (2)). THE ANSWERS ARE WRITTEN FROM THE CONTRACT, NOT PRODUCED
 * BY THE SERVER. The evaluation is BLIND: the technician does not see the result before his evaluation is sent (a fixed rule of the interface); what is asserted here is the other half - the second technician
 * does not see the first one's answers: after the first form is sent the screen is a NEW form, nothing of the first in it (rule 22: nothing carried over, not even the technician the phone remembers).
 *
 * Real: [TechnicianFormViewModel], `FirebaseAnalysisService.getTechnicianFormConfig` / `submitTechnicianForm`, the reader, the form logic and the request builder, the log. Replaced: only the HTTP round trip and
 * auth (the harness) and - declared - the form's exit gate (opened by the harness), as in `TechnicianFormViewModelTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TechnicianFormSecondEvaluationTest {

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

    private fun slot(n: Int, state: String, drawn: Any? = null, withDrawn: Boolean = drawn != null): HashMap<String, Any?> =
        hashMapOf<String, Any?>("slot" to n, "formState" to state).also { if (withDrawn) it["drawn"] = drawn }

    /** technicianForm.js:325-336 with `drawn` on the second slot (A7 (2)). */
    private fun configAnswer(
        slots: List<Map<String, Any?>>? = listOf(slot(1, "not_submitted"), slot(2, "not_submitted", drawn = true)),
        techs: List<Map<String, Any?>> = technicians("t1", "t2")
    ): HashMap<String, Any?> =
        hashMapOf<String, Any?>("sessionId" to "sess-1", "formContractVersion" to 2, "enabled" to true, "settingsVersion" to 3, "symptomOrder" to listOf("charging_slow", "battery_swollen"), "technicians" to techs)
            .also { if (slots != null) it["slots"] = slots }

    private class Rig(val h: RealJoinPathHarness, val vm: TechnicianFormViewModel, val store: TechnicianChoiceStore) {
        var clock = 10_000L
        val form get() = vm.state.value.form!!
    }

    private fun withForm(config: Any? = configAnswer(), store: TechnicianChoiceStore = TechnicianChoiceStore(InMemorySharedPreferences()), open: Boolean = true, check: Rig.() -> Unit) {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.openFormCalls()
            h.techConfigResponse = config
            val vm = TechnicianFormViewModel(h.service, store)
            val rig = Rig(h, vm, store)
            vm.nowMs = { rig.clock }
            if (open) vm.open("sess-1")
            rig.check()
        }
    }

    private fun refusal(code: String) = SessionWireFixtures.exceptionFromWire(SessionWireFixtures.wireBody("FAILED_PRECONDITION", "made-up message", code))

    /** The first technician's form, sent. */
    private fun Rig.sendFirst(technician: String = "t1") {
        vm.chooseTechnician(technician)
        clock += 1_000
        vm.grade(GradeCategory.BATTERY, GradeAnswer.FAIR)
        clock += 1_000
        vm.grade(GradeCategory.OVERALL, GradeAnswer.GOOD)
        clock += 1_000
        vm.toggleSymptom(Symptom.CHARGING_SLOW)
        clock += 1_000
        vm.note("back cover raised")
        clock += 2_000
        vm.submit()
    }

    private val orderShown = listOf(Symptom.CHARGING_SLOW, Symptom.BATTERY_SWOLLEN) + Symptom.TWELVE.filter { it != Symptom.CHARGING_SLOW && it != Symptom.BATTERY_SWOLLEN }

    // ---- the second form after the first ----

    @Test
    fun `after the first form is sent the screen is the second form - new, empty, nobody chosen - and no second configuration call was made`() = withForm {
        sendFirst("t1")
        assertEquals("the first form was sent once", 1, h.callableCalls("submitTechnicianForm"))
        assertEquals("slot 1", 1, (h.techSubmitRequests.single() as Map<*, *>)["slot"])
        // the screen: a NEW form of the second slot, exactly as a form is opened - nothing of the first in it
        assertEquals(openForm("sess-1", 2, orderShown, clock), form)
        assertNull("nobody is chosen: not the technician of the first form, not the one the phone remembers", form.technicianId)
        assertTrue(form.grades.isEmpty() && form.functional.isEmpty() && form.defects.isEmpty() && form.symptoms.isEmpty() && form.perAnswerAt.isEmpty() && form.observedNote.isEmpty())
        assertNull(form.firstTapAtMs)
        assertEquals(Submission.Editing(), vm.state.value.submission)
        assertEquals(null, vm.state.value.blocked)
        assertEquals(false, vm.state.value.promptOpen)
        val ready = vm.state.value.config as ConfigUi.Ready
        assertEquals(listOf("t1", "t2"), ready.technicians.map { it.id })
        assertEquals("the answer already read says whether the second form is drawn: no second call", 1, h.callableCalls("getTechnicianFormConfig"))
        assertTrue(h.logLines.toString(), EvalCardLog.technicianFormEventLine("second-evaluation", "sess-1", "offered") in h.logLines)
        assertTrue(h.logLines.toString(), EvalCardLog.technicianFormEventLine("submitted", "sess-1", "slot1") in h.logLines)
    }

    @Test
    fun `the second technician's form is sent as slot 2 with his own answers - the request is the builder's, and holds nothing of the first`() = withForm {
        sendFirst("t1")
        val firstRequest = h.techSubmitRequests.single() as Map<*, *>
        clock += 5_000
        vm.chooseTechnician("t2")
        clock += 1_000
        vm.grade(GradeCategory.THERMAL, GradeAnswer.EXCELLENT)
        clock += 3_000
        val expected = (TechnicianFormRequest.build(form, submittedAtMs = clock) as FormRequestBuild.Built).request
        vm.submit()
        assertEquals(2, h.callableCalls("submitTechnicianForm"))
        val secondRequest = h.techSubmitRequests[1] as Map<*, *>
        assertEquals("exactly what the real builder makes of the second form", expected, secondRequest)
        assertEquals(2, secondRequest["slot"])
        assertEquals("t2", secondRequest["technicianId"])
        assertEquals(mapOf("battery" to null, "performance" to null, "thermal" to "excellent", "overall" to null), secondRequest["grades"])
        assertEquals("the first form carried a note and a symptom", "back cover raised", firstRequest["observedNote"])
        assertEquals(listOf("charging_slow"), firstRequest["symptoms"])
        assertNull("none of the first one's note is in the second", secondRequest["observedNote"])
        assertEquals("nor its symptoms", emptyList<String>(), secondRequest["symptoms"])
        assertEquals(Submission.Submitted, vm.state.value.submission)
        assertEquals("no third form: the second is the last", 2, h.callableCalls("submitTechnicianForm"))
        assertEquals(1, h.callableCalls("getTechnicianFormConfig"))
    }

    @Test
    fun `the phone remembers the technician of the FIRST form only - the second one is by another technician and does not replace it`() = withForm {
        sendFirst("t1")
        assertEquals("t1", store.last())
        vm.chooseTechnician("t2")
        vm.grade(GradeCategory.OVERALL, GradeAnswer.GOOD)
        vm.submit()
        assertEquals(Submission.Submitted, vm.state.value.submission)
        assertEquals("the habitual technician of this phone is still the first", "t1", store.last())
    }

    @Test
    fun `the same technician for the second form is refused by the server - the named failure, the form kept, and the other technician can send`() = withForm {
        sendFirst("t1")
        vm.chooseTechnician("t1")
        vm.grade(GradeCategory.OVERALL, GradeAnswer.GOOD)
        h.techSubmitFailure = refusal("FORM_SLOT_SAME_TECHNICIAN")
        vm.submit()
        assertEquals(Submission.Editing(FormFailure.SameTechnician), vm.state.value.submission)
        assertEquals(2, form.slot)
        vm.chooseTechnician("t2")
        h.techSubmitFailure = null
        vm.submit()
        assertEquals(Submission.Submitted, vm.state.value.submission)
        assertEquals("t2", (h.techSubmitRequests.last() as Map<*, *>)["technicianId"])
    }

    @Test
    fun `a session the draw did not pick has one form - after it is sent the screen says sent, as before, and asks for no second`() {
        val notDrawn = configAnswer(slots = listOf(slot(1, "not_submitted"), slot(2, "not_submitted", drawn = false)))
        withForm(config = notDrawn) {
            sendFirst("t1")
            assertEquals(Submission.Submitted, vm.state.value.submission)
            assertEquals("still the first form", 1, form.slot)
            assertEquals(1, h.callableCalls("submitTechnicianForm"))
            assertTrue(h.logLines.toString(), h.logLines.none { it.contains("event=second-evaluation") })
        }
    }

    @Test
    fun `a draw that cannot be read is a named state - never a second form and never none - and a good answer on retry opens the second form`() {
        // every shape of the second slot's `drawn` the app cannot use, each its own reason (rule 22)
        val cases = listOf(
            slot(2, "not_submitted") to "drawn_absent",
            slot(2, "not_submitted", drawn = "yes") to "drawn_not_a_boolean_string",
            slot(2, "not_submitted", drawn = 1) to "drawn_not_a_boolean_number"
        )
        for ((second, why) in cases) {
            withForm(config = configAnswer(slots = listOf(slot(1, "not_submitted"), second))) {
                sendFirst("t1")
                assertEquals("$why", ConfigUi.Unreadable(why), vm.state.value.config)
                assertNull("the first one's form is gone from the screen", vm.state.value.form)
                assertTrue(h.logLines.toString(), EvalCardLog.technicianFormEventLine("second-evaluation", "sess-1", "unreadable_$why") in h.logLines)
                // asking again, now with a good answer: the first slot is sent, the second is drawn - the second form opens
                h.techConfigResponse = configAnswer(slots = listOf(slot(1, "submitted"), slot(2, "not_submitted", drawn = true)))
                vm.retryConfig()
                assertEquals("$why", 2, form.slot)
                assertEquals(2, h.callableCalls("getTechnicianFormConfig"))
            }
        }
    }

    // ---- opening a session whose first form is already sent ----

    @Test
    fun `a screen that opens after the first form was sent goes straight to the second - and preselects nobody, whoever the phone remembers`() {
        val store = TechnicianChoiceStore(InMemorySharedPreferences()).also { it.remember("t1") }
        val answer = configAnswer(slots = listOf(slot(1, "submitted"), slot(2, "not_submitted", drawn = true)))
        withForm(config = answer, store = store) {
            assertEquals(2, form.slot)
            assertNull(form.technicianId)
            assertEquals(1, h.callableCalls("getTechnicianFormConfig"))
            assertTrue(h.logLines.toString(), EvalCardLog.technicianFormConfigLine("object", "server", 2, "offered") in h.logLines)
        }
    }

    @Test
    fun `both forms sent, or the second not drawn after the first - the slot is closed, as before, whichever way the answer says it`() {
        val sent = ConfigUi.SlotClosed(SlotClosedReason.SUBMITTED)
        for (answer in listOf(
            configAnswer(slots = listOf(slot(1, "submitted"), slot(2, "submitted", drawn = true))),
            configAnswer(slots = listOf(slot(1, "submitted"), slot(2, "not_submitted", drawn = false))),
            configAnswer(slots = listOf(slot(1, "submitted"), slot(2, "submitted", drawn = false)))
        )) {
            withForm(config = answer) {
                assertEquals("$answer", sent, vm.state.value.config)
                assertNull(vm.state.value.form)
            }
        }
    }

    @Test
    fun `opening the session again while the second form is being filled keeps it - the screen being built twice does not wipe what was typed`() = withForm {
        sendFirst("t1")
        vm.chooseTechnician("t2")
        vm.grade(GradeCategory.BATTERY, GradeAnswer.WEAK)
        vm.open("sess-1")
        assertEquals(2, form.slot)
        assertEquals("t2", form.technicianId)
        assertEquals(GradeAnswer.WEAK, form.grades[GradeCategory.BATTERY])
        assertEquals("no new call", 1, h.callableCalls("getTechnicianFormConfig"))
        // another session is a new form
        vm.open("sess-2")
        assertEquals("sess-2", form.sessionId)
        assertEquals(1, form.slot)
    }

    @Test
    fun `asking for the second slot by number is exactly that slot - the first one's progression is for the first slot's open only`() {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.openFormCalls()
            h.techConfigResponse = configAnswer(slots = listOf(slot(1, "not_submitted"), slot(2, "not_submitted", drawn = true)))
            val vm = TechnicianFormViewModel(h.service, TechnicianChoiceStore(InMemorySharedPreferences()))
            vm.open("sess-1", 2)
            assertEquals(2, vm.state.value.form!!.slot)
            assertNull(vm.state.value.form!!.technicianId)
        }
    }
}
