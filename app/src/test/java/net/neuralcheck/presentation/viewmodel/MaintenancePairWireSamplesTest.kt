package net.neuralcheck.presentation.viewmodel

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.PairLinkClient
import net.neuralcheck.data.remote.PairLinkClient.ConfirmOutcome
import net.neuralcheck.data.remote.PairLinkClient.ListOutcome
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.data.remote.SessionErrorCode
import net.neuralcheck.data.remote.purposeRequestRejection
import net.neuralcheck.domain.pair.CandidateName
import net.neuralcheck.domain.pair.ConfirmAnswerRead
import net.neuralcheck.domain.pair.DeviceNameRead
import net.neuralcheck.domain.pair.OpenBeforesRead
import net.neuralcheck.domain.pair.PairFailureKind
import net.neuralcheck.domain.pair.PairRefusal
import net.neuralcheck.domain.pair.failureKindOf
import net.neuralcheck.domain.pair.nameFor
import net.neuralcheck.domain.pair.PairLinkWire
import net.neuralcheck.domain.consent.ConsentLocale
import net.neuralcheck.domain.consent.ConsentLocaleRead
import net.neuralcheck.domain.repeat.TextRead
import net.neuralcheck.util.EvalCardLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.MessageDigest
import java.time.Instant

/**
 * The server's golden REPLY samples of the maintenance link, read by the app's readers - order of 2026-10-22, coordinator's message after the server's S7 (rules 22, 25, 29, 30, 37, 41).
 *
 * `app/src/test/resources/contract/maintenance-pair-wire-samples-neuralcheck-backend-b0a0f66.json` is a byte-for-byte copy of neuralcheck-backend `functions/test/fixtures/golden/maintenance-pair-wire-samples-2026-10-22.json`
 * at b0a0f66 - and byte-identical at f3c1bab and at c289a2d (re-copied and compared by sha256 and cmp on 2026-10-23) (decision Q36 added a server-only field, `linkModelCheck`, that no reply carries; the app's writer compared `git show f3c1bab:<path>` with this file by sha256) (54 samples: 16 of `listOpenMaintenanceBefores`, 28 of `confirmMaintenanceAfterLink`, 10 of `setSessionPurpose` - 9 refusals and 1 success; sha256 in `contract/README.md`; the 50 of b76c791 are unchanged and
 * the 4 new ones are the three `PURPOSE_PART_NOT_ENABLED` refusals and the success of `normal` for a shop with no parts, decision Q31). The server made them from its REAL functions behind its REAL
 * `onCall` wrapper over an in-memory database and a frozen clock (the file's `_provenance` says what is real and what is stubbed; nothing was run on Firestore). Each sample's `body` is the wire body: `{result}` for an
 * answer, `{error: {status, message, details: {reasonCode}}}` for a refusal - and a refusal is handed to the Firebase SDK's own parser ([SessionWireFixtures.exceptionFromWire]) exactly as the server wrote it. Its `request`
 * field is what the SERVER's generator built to run the function: NOT a sample of this app.
 *
 * What is asserted over ALL of them, through the real service and client: every answer reads, with the values of its own seed (the hours, each candidate's id, its time as the contract's `scannedAtMs`, its name state);
 * every refusal is the code it carries, for any status, and goes to the text of that code; the requests the app builds for the answers' calls equal the ones the server's samples ran; and a key that is gone, null, or of
 * another shape in an answer makes it UNREADABLE - never a default (the contract's `validityHours`, a candidate's `beforeScanId` / `scannedAtMs` / `deviceName`, the confirmation's three keys).
 * Nothing here edits a sample: a difference between a reader and a sample is reported, not patched into the file.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MaintenancePairWireSamplesTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val fileBytes: ByteArray = javaClass.classLoader!!.getResourceAsStream("contract/maintenance-pair-wire-samples-neuralcheck-backend-b0a0f66.json")?.readBytes()
        ?: error("the samples are not on the test classpath - expected at app/src/test/resources/contract/")

    private val root: JsonObject = JsonParser.parseString(String(fileBytes, Charsets.UTF_8)).asJsonObject

    private class Sample(val id: String, val function: String, val request: JsonObject, val seed: JsonObject, val httpStatus: Int, val body: JsonObject) {
        val result: JsonElement? get() = body.get("result")
        val error: JsonObject? get() = body.getAsJsonObject("error")?.takeIf { body.has("error") }
        val reasonCode: String? get() = error?.getAsJsonObject("details")?.get("reasonCode")?.asString
        val data: JsonObject get() = request.getAsJsonObject("data")
    }

    private val samples: List<Sample> = root.getAsJsonArray("samples").map {
        val o = it.asJsonObject
        Sample(o.get("id").asString, o.get("function").asString, o.getAsJsonObject("request"), o.getAsJsonObject("seed"), o.get("httpStatus").asInt, o.getAsJsonObject("body"))
    }

    private fun of(function: String) = samples.filter { it.function == function }

    private val plain = Gson()

    private fun toPlain(element: JsonElement?): Any? = plain.fromJson(element ?: JsonNull.INSTANCE, Any::class.java)

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun withClient(check: RealJoinPathHarness.(PairLinkClient) -> Unit) {
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.openPairCalls()
            h.check(PairLinkClient(h.service))
        }
    }

    private fun refusalOf(sample: Sample) = SessionWireFixtures.exceptionFromWire(sample.body.toString())

    private fun millisOf(timestamp: JsonElement?): Long = Instant.parse(timestamp!!.asJsonObject.get("\$firestoreTimestamp").asString).toEpochMilli()

    private val named = PairRefusal.values().map { it.wire }.toSet() + SessionErrorCode.values().map { it.wire }.toSet()

    // ---- the file ----

    @Test
    fun `the file is the server's - its sha256 is the one the README records, it holds 54 samples with unique ids, 16 and 28 and 10, and each body is a result or an error`() {
        assertEquals("d0ad3015c8a845fbf682f8a38919e61388ce82e2fef02120a1b77b9a38cc9422", sha256(fileBytes))
        assertEquals(54, samples.size)
        assertEquals(54, samples.map { it.id }.toSet().size)
        assertEquals(16, of("listOpenMaintenanceBefores").size)
        assertEquals(28, of("confirmMaintenanceAfterLink").size)
        assertEquals(10, of("setSessionPurpose").size)
        for (s in samples) {
            assertTrue("${s.id}: a result or an error, not both", s.body.has("result") != s.body.has("error"))
            s.error?.let { e ->
                assertTrue("${s.id}: status, message and details.reasonCode", e.has("status") && e.has("message") && s.reasonCode != null)
                assertTrue("${s.id}: an error is not a 200", s.httpStatus in 400..499)
            } ?: assertEquals("${s.id}: a result is a 200", 200, s.httpStatus)
        }
        assertTrue("no carriage return in the file: it is -text and LF", !String(fileBytes, Charsets.UTF_8).contains('\r'))
    }

    @Test
    fun `every reasonCode the samples carry is one the app names - none falls to an unrecognized code`() {
        val unnamed = samples.mapNotNull { it.reasonCode }.toSet() - named
        assertEquals("a code of the server's own samples the app has no case for: $unnamed", emptySet<String>(), unnamed)
    }

    // ---- listOpenMaintenanceBefores ----

    @Test
    fun `every answer of the list reads - with the hours, the ids in the order sent, the contract's time and the name state of its own seed - and no key is unknown`() = withClient { client ->
        val answers = of("listOpenMaintenanceBefores").filter { it.result != null }
        assertEquals("four at 0b89db1, seven since S8", 7, answers.size)
        for (s in answers) {
            pairListResponse = toPlain(s.result)
            val outcome = runBlocking { client.listOpen(s.data.get("sessionId").asString) }
            val read = (outcome as? ListOutcome.Listed)?.read ?: error("${s.id}: the answer did not read: $outcome")
            val result = s.result!!.asJsonObject
            // the seed of one S8 sample (`cap-is-per-shop`) is a summary of 501 documents of another shop that the file does not list: no settings document to read, the answer is read as it is and its hours are the base seed's, 72
            val settings = s.seed.getAsJsonObject("protocolSettings/current")
            assertEquals(s.id, settings?.get("openBeforeValidityHours")?.asInt ?: 72, read.validityHours)
            assertEquals(s.id, result.getAsJsonArray("candidates").map { it.asJsonObject.get("beforeScanId").asString }, read.candidates.map { it.beforeScanId })
            assertTrue("${s.id}: no unknown key at the top ${read.unknownKeys} or in a candidate ${read.candidateUnknownKeys}", read.unknownKeys.isEmpty() && read.candidateUnknownKeys.isEmpty())
            for (c in read.candidates) {
                val before = s.seed.getAsJsonObject("scanProtocol/${c.beforeScanId}")
                if (before != null) {
                    assertEquals("${s.id}/${c.beforeScanId}: scannedAtMs is the seed's pair.openedAt in milliseconds", millisOf(before.getAsJsonObject("pair").get("openedAt")), c.scannedAtMs)
                    // S8: what the server lists belongs to the caller's shop - the seed's pair.ownerId is the authenticated uid (the app lists exactly what it is sent)
                    assertEquals("${s.id}/${c.beforeScanId}: pair.ownerId", s.request.getAsJsonObject("authenticatedAs").get("uid").asString, before.getAsJsonObject("pair").get("ownerId").asString)
                }
            }
            // the screen's state for it
            val ui = pairListFor(outcome)
            if (read.candidates.isEmpty()) assertEquals(s.id, PairListUi.Empty(read.validityHours), ui)
            else assertEquals(s.id, PairListUi.Choosing(PairListing(read.validityHours, read.candidates), null, PairConfirmUi.Idle), ui)
        }
    }

    @Test
    fun `the names of the answers are what the server resolved - mapped in each language, or the phone's own model with the server's reason - and nothing is made up`() = withClient { client ->
        fun listed(id: String): OpenBeforesRead.Read {
            val s = samples.single { it.id == id }
            pairListResponse = toPlain(s.result)
            return (runBlocking { client.listOpen(s.data.get("sessionId").asString) } as ListOutcome.Listed).read
        }
        val two = listed("list.success.two-candidates").candidates
        val mapped = two[0].deviceName as DeviceNameRead.Mapped
        assertEquals(TextRead.Text("SM-A135F"), mapped.model)
        for (locale in ConsentLocale.values()) {
            assertTrue("a mapped name in $locale", nameFor(two[0].deviceName, ConsentLocaleRead.Known(locale)) is CandidateName.Marketing)
        }
        val unmapped = two[1].deviceName as DeviceNameRead.Unmapped
        assertEquals(TextRead.Text("XT2201-2"), unmapped.model)
        assertEquals(TextRead.Text("MODEL_NOT_IN_MAP"), unmapped.reason)
        assertEquals(CandidateName.Model("XT2201-2", "unmapped"), nameFor(two[1].deviceName, ConsentLocaleRead.Known(ConsentLocale.AR)))
        val noMap = listed("list.name-unmapped.map-absent").candidates.single().deviceName as DeviceNameRead.Unmapped
        assertEquals(TextRead.Text("MAP_ABSENT"), noMap.reason)
        assertEquals(CandidateName.Model("SM-A135F", "unmapped"), nameFor(noMap, ConsentLocaleRead.Known(ConsentLocale.TR)))
    }

    @Test
    fun `every refusal of the list is read as the code the server's body carries, for the status it carries - and goes to the text of that code`() = withClient { client ->
        val refusals = of("listOpenMaintenanceBefores").filter { it.error != null }
        assertEquals(9, refusals.size)
        for (s in refusals) {
            pairListFailure = refusalOf(s)
            val code = s.reasonCode!!
            val outcome = runBlocking { client.listOpen(s.data.get("sessionId")?.asString ?: "") }
            assertEquals(s.id, ListOutcome.Refused(code, PairRefusal.ofWire(code)), outcome)
            val ui = pairListFor(outcome)
            assertEquals(s.id, PairListUi.LoadFailed(PairFailure(failureKindOf(code), code)), ui)
            val expectedKind = if (code == "PAIR_PART_NOT_ENABLED" || code == "PAIR_VALIDITY_SETTING_ABSENT") PairFailureKind.NOT_ENABLED else PairFailureKind.GENERAL
            assertEquals("${s.id}: the failure text", expectedKind, (ui as PairListUi.LoadFailed).failure.kind)
            assertTrue(logLines.toString(), EvalCardLog.pairLinkLine("list", s.data.get("sessionId")?.asString ?: "", "refused", code, emptyList()) in logLines)
        }
    }

    @Test
    fun `the request the app builds for the list is the one the server's samples ran - the session id alone`() {
        for (s in of("listOpenMaintenanceBefores").filter { it.data.has("sessionId") }) {
            assertEquals(s.id, toPlain(s.data), PairLinkWire.listRequest(s.data.get("sessionId").asString))
        }
    }

    // ---- confirmMaintenanceAfterLink ----

    @Test
    fun `every confirmation reads as a confirmation of ITS request - and the request the app builds is the one the server's sample ran`() = withClient { client ->
        val answers = of("confirmMaintenanceAfterLink").filter { it.result != null }
        assertEquals(3, answers.size)
        for (s in answers) {
            val session = s.data.get("sessionId").asString
            val before = s.data.get("beforeScanId").asString
            assertEquals(s.id, toPlain(s.data), PairLinkWire.confirmRequest(session, before))
            pairConfirmResponse = toPlain(s.result)
            assertEquals(s.id, ConfirmOutcome.Confirmed, runBlocking { client.confirm(session, before) })
            assertEquals(s.id, ConfirmAnswerRead.Confirmed, ConfirmAnswerRead.of(s.result, session, before))
        }
    }

    @Test
    fun `every refusal of the confirmation is read as the code the server's body carries, for the status it carries - and goes to the text of that code`() = withClient { client ->
        val refusals = of("confirmMaintenanceAfterLink").filter { it.error != null }
        assertEquals("twenty-two at 0b89db1, twenty-five since S8", 25, refusals.size)
        val kinds = mapOf(
            "BEFORE_NOT_FOUND" to PairFailureKind.NOT_AVAILABLE, "BEFORE_NOT_OWNED" to PairFailureKind.NOT_AVAILABLE, "BEFORE_WRONG_PURPOSE" to PairFailureKind.NOT_AVAILABLE,
            "BEFORE_ALREADY_PAIRED" to PairFailureKind.ALREADY_PAIRED, "BEFORE_EXPIRED" to PairFailureKind.EXPIRED, "BEFORE_WRONG_DEFECT_TYPE" to PairFailureKind.WRONG_DEFECT_TYPE,
            "PAIR_PART_NOT_ENABLED" to PairFailureKind.NOT_ENABLED, "PAIR_VALIDITY_SETTING_ABSENT" to PairFailureKind.NOT_ENABLED,
            "PAIR_OWNER_MISMATCH" to PairFailureKind.NOT_AVAILABLE
        )
        for (s in refusals) {
            pairConfirmFailure = refusalOf(s)
            val code = s.reasonCode!!
            val session = s.data.get("sessionId")?.asString ?: ""
            val before = s.data.get("beforeScanId")?.asString ?: ""
            val outcome = runBlocking { client.confirm(session, before) }
            assertEquals(s.id, ConfirmOutcome.Refused(code, PairRefusal.ofWire(code)), outcome)
            assertEquals("${s.id}: the failure text of $code", PairFailure(kinds[code] ?: PairFailureKind.GENERAL, code), pairConfirmFailureFor(outcome))
            assertTrue(logLines.toString(), EvalCardLog.pairLinkLine("confirm", session, "refused", code, emptyList()) in logLines)
        }
    }

    // ---- S8: the owner of the pair is the server's business, not the wire's ----

    @Test
    fun `the six samples of S8 read as they say - listed as sent, owner mismatch is an unavailable scan, absent owner is unreadable`() = withClient { client ->
        fun listedIds(id: String): List<String> {
            val s = samples.single { it.id == id }
            pairListResponse = toPlain(s.result)
            return (runBlocking { client.listOpen(s.data.get("sessionId").asString) } as ListOutcome.Listed).read.candidates.map { it.beforeScanId }
        }
        // the server dropped the mismatched and the owner-less pairs from the list and counts the cap per shop: the app shows what it got, nothing more and nothing less
        assertEquals(listOf("before-good"), listedIds("list.success.owner-mismatch-excluded"))
        assertEquals(listOf("before-good"), listedIds("list.success.pair-without-owner-not-listed"))
        assertEquals(listOf("mine-1"), listedIds("list.success.cap-is-per-shop"))
        for (id in listOf("confirm.refused.owner-mismatch.report-differs", "confirm.refused.owner-mismatch.pair-differs")) {
            val s = samples.single { it.id == id }
            pairConfirmFailure = refusalOf(s)
            val outcome = runBlocking { client.confirm(s.data.get("sessionId").asString, s.data.get("beforeScanId").asString) }
            assertEquals(id, ConfirmOutcome.Refused("PAIR_OWNER_MISMATCH", PairRefusal.OWNER_MISMATCH), outcome)
            // the text of an unavailable scan (the proposal's row 14), the code in the line - no text of its own
            assertEquals(id, PairFailure(PairFailureKind.NOT_AVAILABLE, "PAIR_OWNER_MISMATCH"), pairConfirmFailureFor(outcome))
            assertTrue(logLines.toString(), EvalCardLog.pairLinkLine("confirm", s.data.get("sessionId").asString, "refused", "PAIR_OWNER_MISMATCH", emptyList()) in logLines)
        }
        val absent = samples.single { it.id == "confirm.refused.before-pair-owner-absent" }
        pairConfirmFailure = refusalOf(absent)
        val outcome = runBlocking { client.confirm(absent.data.get("sessionId").asString, absent.data.get("beforeScanId").asString) }
        assertEquals(ConfirmOutcome.Refused("PAIR_BEFORE_UNREADABLE", PairRefusal.BEFORE_UNREADABLE), outcome)
        assertEquals(PairFailure(PairFailureKind.GENERAL, "PAIR_BEFORE_UNREADABLE"), pairConfirmFailureFor(outcome))
    }

    @Test
    fun `no reply carries ownerId - it is the server's field, not the wire's - and the pair list never reads it`() {
        fun keys(e: JsonElement, into: MutableSet<String>) {
            when {
                e.isJsonObject -> e.asJsonObject.entrySet().forEach { (k, v) -> into += k; keys(v, into) }
                e.isJsonArray -> e.asJsonArray.forEach { keys(it, into) }
            }
        }
        val all = mutableSetOf<String>()
        for (s in samples) keys(s.body, all)
        assertTrue("a reply key named ownerId: $all", "ownerId" !in all)
        assertTrue("a reply key named linkModelCheck (Q36: the server's field, not the wire's): $all", "linkModelCheck" !in all)
        assertTrue("the replies' keys are the contract's: $all", all.containsAll(listOf("validityHours", "candidates", "beforeScanId", "deviceName", "scannedAtMs", "linkMethod", "reasonCode")))
        // and nothing of the pair list's reading names it: the session document's own `ownerId` (the owner of the session, read elsewhere) is another thing
        val main = listOf("app/src/main/java", "../app/src/main/java").map { java.io.File(it) }.first { it.isDirectory }
        val pairFiles = setOf("PairLink.kt", "PairLinkClient.kt", "PairListUi.kt", "PairListSurface.kt")
        val readers = main.walkTopDown().filter { it.isFile && it.name in pairFiles }.filter { f ->
            f.readText().replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") }.contains("ownerId")
        }.map { it.name }.toList()
        assertEquals("a file of the pair list reads ownerId", emptyList<String>(), readers)
        assertEquals("the four files exist", pairFiles, main.walkTopDown().filter { it.isFile && it.name in pairFiles }.map { it.name }.toSet())
    }

    // ---- setSessionPurpose ----

    private val partRefusals = setOf(
        "setSessionPurpose.refused.part-not-enabled.purposes",
        "setSessionPurpose.refused.part-not-enabled.battery-before-after",
        "setSessionPurpose.refused.part-not-enabled.owner-absent"
    )

    private fun createAnswerWith(parts: Any?): HashMap<String, Any?> =
        hashMapOf<String, Any?>("sessionId" to "sess-1", "code" to "NC-1234", "expiresInMinutes" to 60).also { if (parts != null) it["protocolParts"] = parts }

    /** The parts of the shop the SERVER's sample ran for: the `protocolParts` of its seeded owner document, or none when the sample has no owner document. */
    private fun shopPartsOf(s: Sample): Any? = s.seed.getAsJsonObject("users/owner-sample")?.get("protocolParts")?.let { toPlain(it) }

    /**
     * Rule 23: REPLACES `every refusal of setSessionPurpose in the samples is read as the code it carries` (six samples, a create with `purposes` alone and a maintenance tap): since Q31 a shop with `purposes` alone has no
     * maintenance chip, so the refusals are read for a shop with BOTH parts; there are nine now, and the new code is its own state.
     */
    @Test
    fun `every refusal of setSessionPurpose in the samples is read as the code it carries - through the owner's view model, the chips stay usable and the line says which - the new one is its own state`() {
        val refusals = of("setSessionPurpose").filter { it.error != null }
        assertEquals(9, refusals.size)
        for (s in refusals) {
            RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
                h.createResponse = createAnswerWith(hashMapOf<String, Any?>("purposes" to true, "batteryBeforeAfter" to true))
                val vm = GenerateSessionViewModel(context, h.service, h.canceller)
                h.purposeFailure = refusalOf(s)
                vm.onPurposeChip(ScanPurposeChoice.MAINTENANCE_BEFORE)
                vm.onDefectType(DefectType.BATTERY)
                val offered = vm.state.value.purposes as PurposeUi.Offered
                val expected = if (s.reasonCode == SessionErrorCode.PURPOSE_PART_NOT_ENABLED.wire) PurposeFailure.PartNotEnabled else PurposeFailure.Refused(s.reasonCode!!)
                assertEquals(s.id, expected, offered.failure)
                assertEquals("a refusal does not lock the chips and changes nothing the app knows", PurposeConfirmed.AtCreation, offered.confirmed)
                assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeSetLine("sess-1", "maintenance_before", "BATTERY", "refused", s.reasonCode!!, "-") in h.logLines)
            }
        }
        assertEquals(
            setOf("DEFECT_TYPE_REQUIRED", "DEFECT_TYPE_INVALID", "DEFECT_TYPE_NOT_APPLICABLE", "SET_PURPOSE_INVALID", "PURPOSE_PART_NOT_ENABLED"),
            refusals.map { it.reasonCode!! }.toSet()
        )
        assertEquals(partRefusals, refusals.filter { it.reasonCode == "PURPOSE_PART_NOT_ENABLED" }.map { it.id }.toSet())
    }

    /**
     * Rule 23: REPLACES `the app cannot build a request any of the six refusals answered` (it pinned that EVERY refused request is inexpressible): the three `PURPOSE_PART_NOT_ENABLED` refusals answer requests the app CAN
     * build - a valid purpose with a valid type; what keeps them from the server is the shop's parts, which the SAME samples seed. So the six refusals of the request itself stay inexpressible, and for the three of the parts
     * the app, given the parts the server's sample was run with, does not send the request (the chip is not drawn; a tap that arrives anyway is ignored by name; nothing leaves the phone).
     */
    @Test
    fun `the app cannot build a request the six refusals of the request itself answered - and does not send the three the shop's parts refused, for the parts the server seeded`() {
        for (s in of("setSessionPurpose").filter { it.error != null && it.id !in partRefusals }) {
            val purpose = s.data.get("purpose")?.takeIf { it.isJsonPrimitive }?.asString
            val choice = ScanPurposeChoice.values().firstOrNull { it.wire == purpose }
            val typeElement = s.data.get("defectType")
            val inexpressible = when {
                choice == null -> true // not a purpose the app can choose (battery_before)
                s.data.has("defectType") && (typeElement == null || typeElement.isJsonNull || DefectType.values().none { it.wire == typeElement.asString }) -> true // a key the app never sends with a value it has no case for
                else -> {
                    val type = typeElement?.takeIf { it.isJsonPrimitive }?.asString?.let { w -> DefectType.values().firstOrNull { it.wire == w } }
                    purposeRequestRejection(choice, type) != null // a combination the builder refuses locally
                }
            }
            assertTrue("${s.id}: the app could send what the server refused: ${s.data}", inexpressible)
        }
        // the three of the parts: valid requests, never sent for the shop of the sample
        val expectedReason = mapOf(
            "setSessionPurpose.refused.part-not-enabled.purposes" to ("not_offered" to PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PURPOSES_NOT_LISTED)), // protocolParts {batteryBeforeAfter: true}: no `purposes`
            "setSessionPurpose.refused.part-not-enabled.battery-before-after" to ("maintenance_not_offered" to PurposeUi.Offered(maintenanceOffered = false)), // {purposes: true}: «normal» only
            "setSessionPurpose.refused.part-not-enabled.owner-absent" to ("not_offered" to PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PARTS_ABSENT)) // no owner document: the create answer carries no parts
        )
        assertEquals(partRefusals, expectedReason.keys)
        for (id in partRefusals) {
            val s = samples.single { it.id == id }
            val choice = ScanPurposeChoice.values().single { it.wire == s.data.get("purpose").asString }
            val type = DefectType.values().single { it.wire == s.data.get("defectType").asString }
            assertEquals("$id: the app CAN build this request", null, purposeRequestRejection(choice, type))
            RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
                h.createResponse = createAnswerWith(shopPartsOf(s))
                val vm = GenerateSessionViewModel(context, h.service, h.canceller)
                val (reason, state) = expectedReason.getValue(id)
                assertEquals(id, state, vm.state.value.purposes)
                vm.onPurposeChip(choice)
                vm.onDefectType(type)
                vm.selectPurpose(choice, type)
                assertEquals("$id: nothing leaves the phone for the shop the server refused", 0, h.callableCalls("setSessionPurpose"))
                assertTrue("$id: ${h.logLines}", EvalCardLog.sessionPurposeIgnoredLine(reason) in h.logLines)
            }
        }
    }

    @Test
    fun `the success sample of normal is read - the accepted purpose is confirmed and the line names the keys the server answered`() {
        val s = samples.single { it.id == "setSessionPurpose.success.normal-without-parts" }
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            // the server accepted `normal` for a shop with no parts at all; the app never sends it for such a shop (no chips), so the shop here has `purposes`
            h.createResponse = createAnswerWith(hashMapOf<String, Any?>("purposes" to true))
            val vm = GenerateSessionViewModel(context, h.service, h.canceller)
            h.purposeResponse = toPlain(s.result)
            vm.onPurposeChip(ScanPurposeChoice.NORMAL)
            assertEquals(mapOf("sessionId" to "sess-1", "purpose" to "normal"), h.purposeRequests.single())
            assertEquals(PurposeConfirmed.Chosen(PurposeSelection.Normal), (vm.state.value.purposes as PurposeUi.Offered).confirmed)
            val keys = s.result!!.asJsonObject.keySet().sorted().joinToString(".")
            assertEquals("purpose.purposeSource.sessionId", keys)
            assertTrue(h.logLines.toString(), EvalCardLog.sessionPurposeSetLine("sess-1", "normal", "-", "accepted", "-", keys) in h.logLines)
        }
        // what the shop of that sample (no parts) is offered by the app: nothing - and a tap on «normal» is ignored by name
        RealJoinPathHarness(signedInAtStart = true, viewModelContext = context).use { h ->
            h.createResponse = createAnswerWith(shopPartsOf(s))
            val vm = GenerateSessionViewModel(context, h.service, h.canceller)
            assertEquals(PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PARTS_ABSENT), vm.state.value.purposes)
            vm.onPurposeChip(ScanPurposeChoice.NORMAL)
            assertEquals(0, h.callableCalls("setSessionPurpose"))
        }
    }

    // ---- no default for a key that is not there ----

    private fun copyOf(e: JsonElement): JsonElement = JsonParser.parseString(e.toString())

    /** Every way the reader's REQUIRED keys of a list answer can be gone: removed, null, or of another kind. Each must make the answer unreadable. */
    private fun brokenLists(result: JsonElement): List<Pair<String, JsonElement>> {
        val out = mutableListOf<Pair<String, JsonElement>>()
        fun variant(label: String, mutate: (JsonObject) -> Unit) {
            val copy = copyOf(result).asJsonObject
            mutate(copy)
            out += label to copy
        }
        for (key in listOf("validityHours", "candidates")) {
            variant("$key removed") { it.remove(key) }
            variant("$key null") { it.add(key, JsonNull.INSTANCE) }
            variant("$key a text") { it.add(key, JsonPrimitive("x")) }
        }
        variant("validityHours as a text") { it.add("validityHours", JsonPrimitive("72")) }
        variant("validityHours fractional") { it.add("validityHours", JsonPrimitive(72.5)) }
        val count = result.asJsonObject.getAsJsonArray("candidates").size()
        for (i in 0 until count) {
            val mapped = result.asJsonObject.getAsJsonArray("candidates")[i].asJsonObject.getAsJsonObject("deviceName").get("state").asString == "mapped"
            fun inCandidate(label: String, mutate: (JsonObject) -> Unit) = variant("candidate $i $label") { mutate(it.getAsJsonArray("candidates")[i].asJsonObject) }
            for (key in listOf("beforeScanId", "scannedAtMs", "deviceName")) {
                inCandidate("$key removed") { it.remove(key) }
                inCandidate("$key null") { it.add(key, JsonNull.INSTANCE) }
            }
            inCandidate("scannedAtMs as a text") { it.add("scannedAtMs", JsonPrimitive("1761123600000")) }
            inCandidate("scannedAtMs renamed scannedAt") { it.add("scannedAt", it.remove("scannedAtMs")) }
            inCandidate("deviceName.state removed") { it.getAsJsonObject("deviceName").remove("state") }
            inCandidate("deviceName.state unknown") { it.getAsJsonObject("deviceName").add("state", JsonPrimitive("renamed")) }
            if (mapped) {
                inCandidate("deviceName.names removed") { it.getAsJsonObject("deviceName").remove("names") }
                inCandidate("deviceName.names null") { it.getAsJsonObject("deviceName").add("names", JsonNull.INSTANCE) }
            }
        }
        return out
    }

    @Test
    fun `a key of the list's answer that is gone, null or of another shape makes the whole answer unreadable - never a default - for every answer in the samples`() {
        var checked = 0
        for (s in of("listOpenMaintenanceBefores").filter { it.result != null }) {
            assertTrue("${s.id}: the unbroken answer reads", OpenBeforesRead.of(s.result) is OpenBeforesRead.Read)
            for ((label, broken) in brokenLists(s.result!!)) {
                val read = OpenBeforesRead.of(broken)
                assertTrue("${s.id}: $label read as an answer: $read", read is OpenBeforesRead.Unreadable)
                checked++
            }
        }
        assertTrue("the grid is not empty: $checked variants", checked > 40)
    }

    @Test
    fun `a key of the confirmation that is gone, null or another one makes it unreadable - for every answer in the samples`() {
        for (s in of("confirmMaintenanceAfterLink").filter { it.result != null }) {
            val session = s.data.get("sessionId").asString
            val before = s.data.get("beforeScanId").asString
            for (key in listOf("sessionId", "beforeScanId", "linkMethod")) {
                for ((label, value) in listOf<Pair<String, JsonElement?>>("removed" to null, "null" to JsonNull.INSTANCE, "a number" to JsonPrimitive(7), "other text" to JsonPrimitive("other"))) {
                    val copy = copyOf(s.result!!).asJsonObject
                    if (value == null) copy.remove(key) else copy.add(key, value)
                    assertTrue("${s.id}: $key $label read as a confirmation", ConfirmAnswerRead.of(copy, session, before) is ConfirmAnswerRead.Unreadable)
                }
            }
            // the same answer for another request is not this request's confirmation
            assertTrue(ConfirmAnswerRead.of(s.result, "other-session", before) is ConfirmAnswerRead.Unreadable)
            assertTrue(ConfirmAnswerRead.of(s.result, session, "other-before") is ConfirmAnswerRead.Unreadable)
        }
    }

    @Test
    fun `a name key that is gone stays gone - the reader says absent and the row shows no name, it does not borrow one`() {
        val s = samples.single { it.id == "list.name-unmapped.map-absent" }
        val copy = copyOf(s.result!!).asJsonObject
        copy.getAsJsonArray("candidates")[0].asJsonObject.getAsJsonObject("deviceName").remove("model")
        val read = OpenBeforesRead.of(copy) as OpenBeforesRead.Read
        val name = read.candidates.single().deviceName as DeviceNameRead.Unmapped
        assertEquals(TextRead.Absent, name.model)
        assertEquals(CandidateName.NoName("unmapped_no_model"), nameFor(name, ConsentLocaleRead.Known(ConsentLocale.AR)))
        assertNotNull(read.candidates.single().beforeScanId)
    }
}
