package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.PairLinkClient
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.files.AppRequestPayloadGoldenTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * What the SDK receives from the real service is EXACTLY the golden request samples of the maintenance link - order of 2026-10-22 (rules 37, 39, 41). `MaintenancePairRequestTest` makes the samples with the real
 * builders; this test closes the loop the other way: the owner's view model and `PairLinkClient` run through the real `FirebaseAnalysisService` (nothing replaced but the HTTP round trip, auth and - declared - the
 * pair callables' exit gate), the harness keeps the argument the SDK mock was called with, and that argument, written the way the samples are written, is compared with the committed file byte for byte. So a
 * sample cannot describe a request the app does not send, and a change in what the app sends cannot pass with the samples left behind.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MaintenancePairRequestRealPathTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val gson = GsonBuilder().setPrettyPrinting().serializeNulls().disableHtmlEscaping().create()

    private fun sample(name: String): String = String(File(AppRequestPayloadGoldenTest.derivedDir, "maintenance-pair-request-$name.json").readBytes(), Charsets.UTF_8)

    private fun written(request: Any?): String = gson.toJson(request) + "\n"

    private fun withOwner(sessionId: String, check: (RealJoinPathHarness, GenerateSessionViewModel) -> Unit) {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.createResponse = hashMapOf<String, Any?>("sessionId" to sessionId, "code" to "NC-1234", "expiresInMinutes" to 60, "protocolParts" to hashMapOf<String, Any?>("purposes" to true, "batteryBeforeAfter" to true))
            h.openPairCalls()
            check(h, GenerateSessionViewModel(context, h.service, h.canceller))
        }
    }

    @Test
    fun `setSessionPurpose for a maintenance purpose with its type reaches the SDK exactly as its two samples say`() {
        withOwner("sess-golden-1") { h, vm ->
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_BEFORE)
            vm.onDefectType(DefectType.BATTERY)
            assertEquals(written(h.purposeRequests.single()), sample("set-session-purpose-maintenance-before-battery"))
        }
        withOwner("sess-golden-2") { h, vm ->
            vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_AFTER)
            vm.onDefectType(DefectType.CHARGING_PORT)
            assertEquals(written(h.purposeRequests.single()), sample("set-session-purpose-maintenance-after-charging-port"))
        }
    }

    @Test
    fun `setSessionPurpose for normal reaches the SDK with no defectType key at all - exactly as its sample says`() {
        withOwner("sess-golden-3") { h, vm ->
            vm.onPurposeChip(ScanPurposeChoice.NORMAL)
            assertEquals(written(h.purposeRequests.single()), sample("set-session-purpose-normal-no-defect-type"))
        }
    }

    @Test
    fun `the list and the confirmation reach the SDK exactly as their samples say`() {
        withOwner("sess-golden-2") { h, _ ->
            val client = PairLinkClient(h.service)
            runBlocking { client.listOpen("sess-golden-2") }
            assertEquals(written(h.pairListRequests.single()), sample("list-open-maintenance-befores"))
            runBlocking { client.confirm("sess-golden-2", "scan-golden-before-1") }
            assertEquals(written(h.pairConfirmRequests.single()), sample("confirm-maintenance-after-link"))
        }
    }
}
