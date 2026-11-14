package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.R
import net.neuralcheck.data.remote.ScanSession
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel.Phase
import net.neuralcheck.data.remote.AnalysisRequestSerializer
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
 * A session that could not be created is a named failure, not an empty code chip — batch 2, item 4 (rule 22; the user's decision of 2026-10-15: «رمز الجلسة
 * الفارغ في الشريحة (القاعدة 22): في دفعة التطبيق التالية … الرمز الفارغ أو الغائب حالة صريحة: إنشاء جلسة فشل. لا شريحة رمز فارغة»; the candidate text
 * `session_error_generic` «تعذّر إنشاء الجلسة»).
 *
 * Before: `ScanSession.code` and `sessionId` defaulted to `""` (and so did the owner screen's state), so an answer without them drew a WAITING screen with an empty
 * code chip and an empty QR. Rule 23: no earlier test pinned that (`git grep -n "ScanSession(" -- app/src/test` held only the lifetime tests), so none is replaced.
 *
 * Real: the owner's view model, `FirebaseAnalysisService.createScanSession`, the DTO's Gson parse, the string resources. Replaced: the HTTP round trip, auth, Firestore (the harness).
 * Arabic and Turkish run the same tests; the text shown is compared with the resource of the existing «could not create the session» string.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class OwnerSessionCreateBase {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun createdWith(answer: Any?, check: (RealJoinPathHarness, GenerateSessionViewModel) -> Unit) {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.createResponse = answer
            check(h, GenerateSessionViewModel(context, h.service, h.canceller))
        }
    }

    private fun assertNamedFailure(h: RealJoinPathHarness, vm: GenerateSessionViewModel, sessionId: String, code: String) {
        val state = vm.state.value
        assertEquals(Phase.ERROR, state.phase)
        assertEquals("the text the user named for a session that could not be created", context.getString(R.string.session_error_generic), state.error)
        assertNull("no session id on the screen", state.sessionId)
        assertNull("no code on the screen - never an empty one", state.code)
        assertTrue("no listener was attached to anything", h.snapshotListeners.isEmpty())
        assertEquals(
            "the log says which of the two the answer lacked",
            "session-create-failed sessionId=$sessionId code=$code", h.logLines.single { it.startsWith("session-create-failed") }
        )
    }

    @Test
    fun `a normal answer creates the session - id, code and the lifetime the server named`() {
        createdWith(hashMapOf<String, Any?>("sessionId" to "sess-1", "code" to "NC-1234", "expiresInMinutes" to 60)) { h, vm ->
            assertEquals(Phase.WAITING, vm.state.value.phase)
            assertEquals("sess-1", vm.state.value.sessionId)
            assertEquals("NC-1234", vm.state.value.code)
            assertEquals(60, vm.state.value.expiresInMinutes)
            assertEquals(setOf("sess-1"), h.snapshotListeners.keys.toSet())
            assertTrue(h.logLines.none { it.startsWith("session-create-failed") })
        }
    }

    @Test
    fun `the DTO's other spelling of the session id still creates the session`() {
        createdWith(hashMapOf<String, Any?>("session_id" to "sess-9", "code" to "NC-9999")) { _, vm ->
            assertEquals(Phase.WAITING, vm.state.value.phase)
            assertEquals("sess-9", vm.state.value.sessionId)
            assertNull("no lifetime named: no countdown, not a default", vm.state.value.expiresInMinutes)
        }
    }

    @Test
    fun `an answer with no code is a failed creation, not a session with an empty chip`() {
        createdWith(hashMapOf<String, Any?>("sessionId" to "sess-1", "expiresInMinutes" to 60)) { h, vm -> assertNamedFailure(h, vm, "present", "absent") }
    }

    @Test
    fun `an answer with no session id is a failed creation`() {
        createdWith(hashMapOf<String, Any?>("code" to "NC-1234", "expiresInMinutes" to 60)) { h, vm -> assertNamedFailure(h, vm, "absent", "present") }
    }

    @Test
    fun `an answer with neither is a failed creation, and so is an answer with no data`() {
        createdWith(hashMapOf<String, Any?>("expiresInMinutes" to 60)) { h, vm -> assertNamedFailure(h, vm, "absent", "absent") }
        createdWith(null) { h, vm -> assertNamedFailure(h, vm, "absent", "absent") }
    }

    @Test
    fun `a blank or null session id or code counts as absent`() {
        createdWith(hashMapOf<String, Any?>("sessionId" to "sess-1", "code" to "")) { h, vm -> assertNamedFailure(h, vm, "present", "absent") }
        createdWith(hashMapOf<String, Any?>("sessionId" to "  ", "code" to "NC-1234")) { h, vm -> assertNamedFailure(h, vm, "absent", "present") }
        createdWith(hashMapOf<String, Any?>("sessionId" to null, "code" to null)) { h, vm -> assertNamedFailure(h, vm, "absent", "absent") }
    }

    @Test
    fun `a failed creation can be retried and a good answer then creates the session`() {
        createdWith(hashMapOf<String, Any?>("sessionId" to "sess-1")) { h, vm ->
            assertEquals(Phase.ERROR, vm.state.value.phase)
            h.createResponse = hashMapOf<String, Any?>("sessionId" to "sess-2", "code" to "NC-5678", "expiresInMinutes" to 60)
            vm.create()
            assertEquals(Phase.WAITING, vm.state.value.phase)
            assertEquals("sess-2", vm.state.value.sessionId)
            assertEquals("NC-5678", vm.state.value.code)
            assertNull(vm.state.value.error)
        }
    }

    @Test
    fun `the DTO has no default that fills an absence - a missing id or code stays null`() {
        val gson = AnalysisRequestSerializer.gson
        val empty = gson.fromJson("{}", ScanSession::class.java)
        assertNull(empty.sessionId)
        assertNull(empty.code)
        assertNull(empty.expiresInMinutes)
        val nulls = gson.fromJson("""{"sessionId":null,"code":null}""", ScanSession::class.java)
        assertNull(nulls.sessionId)
        assertNull(nulls.code)
        assertEquals("NC-1", gson.fromJson("""{"session_id":"s","code":"NC-1"}""", ScanSession::class.java).code)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OwnerSessionCreateArabicTest : OwnerSessionCreateBase()

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "tr")
class OwnerSessionCreateTurkishTest : OwnerSessionCreateBase()
