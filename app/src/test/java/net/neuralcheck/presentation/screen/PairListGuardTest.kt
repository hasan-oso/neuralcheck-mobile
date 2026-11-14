package net.neuralcheck.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The guard of the «after maintenance» pair list and where its literals live - order of 2026-10-22. Source-shape, with comments removed (the technique of `RepeatScanGuardTest`).
 *
 * Why (2026-10-22, after Hasan's approval of rows ص1-ص21): the texts of the list, its empty state, the confirmation, the name of a phone with none and the six refusals (rows ص9 to ص20: two exist, ten were pending)
 * are approved and written to both resource files, so [PAIR_LIST_TEXTS_APPROVED] is TRUE: [pairLabels] reads them, the screen's one call to the section is behind the guard, the labels and the list being on the screen,
 * and the two callables leave the phone through the gate that reads the guard (rule 35). THIS TEST FAILS ON PURPOSE the day a slot is added without an approved text while the guard is true; it is replaced then, in the same
 * change (rule 23), not deleted by whoever finds it red. (The server's half, S4 and S5 of the same order, is not deployed: an APK with this guard must not reach a shop before it is.)
 *
 * Rule 24: each wire literal of this feature is spelled in ONE main file.
 */
class PairListGuardTest {

    private val mainDir: File = listOf("app/src/main", "../app/src/main").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private fun code(path: String): String = squash(stripComments(File(mainDir, "java/net/neuralcheck/$path").readText()))

    private fun mainKotlin(): List<File> = File(mainDir, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /**
     * Rule 23: REPLACES `the guard is false, the labels are none, and the screen calls the section once ...` (it pinned the guard false and `pairLabels()` null), `the guard may be true only when no text is pending - ten
     * pending and the two existing` (it pinned ten pending texts) and `no resource holds a text of the pair list that is not approved` (it pinned the texts' ABSENCE from both resource files). Hasan approved rows
     * ص1-ص21 on 2026-10-22: the guard is true, every slot has its text, and `MaintenancePairApprovedTextsTest` pins each text, in both languages, to the approval.
     */
    @Test
    fun `the guard is true, the labels are read from the resources behind it, and the screen calls the section once, behind the guard, labels that exist and a list that is on the screen`() {
        assertTrue("the pair list's texts are approved (Hasan, 2026-10-22) and written to strings.xml in both languages", PAIR_LIST_TEXTS_APPROVED)
        val surface = code("presentation/screen/PairListSurface.kt")
        assertTrue("the labels are null while the guard is false, and are read from the resources of the phone's language otherwise", surface.contains("internal fun pairLabels(): PairLabels? { if (!PAIR_LIST_TEXTS_APPROVED) return null LocalConfiguration.current return pairLabelsOf(LocalContext.current.resources) }"))
        val screen = code("presentation/screen/GenerateSessionScreen.kt")
        assertEquals("the section is called once", 1, Regex("""\bPairListSection\(""").findAll(screen).count())
        assertTrue(
            "behind the guard, labels that exist and a list that is on the screen",
            Regex("""if \(PAIR_LIST_TEXTS_APPROVED && pairLabels != null && pairListVisible\(state\.phase, state\.pairs\)\) \{ [^}]*PairListSection\(""").containsMatchIn(screen)
        )
        assertEquals("the labels are read once, from the one function", 1, Regex("""\bpairLabels\(\)""").findAll(screen).count())
        assertTrue("the two taps go to the two functions of the view model that decide", screen.contains("onSelectPair = { viewModel.onPairCandidate(it) }") && screen.contains("onConfirmPair = { viewModel.onPairConfirm() }"))
    }

    @Test
    fun `the guard may be true only when no text is pending - the list has twelve slots, ten approved and the two existing ones, and each slot names its resource`() {
        assertTrue("PAIR_LIST_TEXTS_APPROVED implies nothing pending", !PAIR_LIST_TEXTS_APPROVED || PairTexts.pending.isEmpty())
        assertTrue("no text is pending: the guard is true because of this, not in spite of it", PairTexts.pending.isEmpty())
        // the proposal's rows ص9 to ص19 (eleven) and ص20, the one this builder proposed for a phone with no name
        assertEquals(11 + 1, PairTexts.all.size)
        assertEquals(setOf(PairTextSlot.LINKED, PairTextSlot.CONFIRM), PairTexts.all.filter { it.approval == PairTextApproval.EXISTING_APPROVED }.toSet())
        assertEquals(PairTexts.all.size - 2, PairTexts.all.count { it.approval == PairTextApproval.APPROVED })
        assertEquals(setOf("ص9", "ص10", "ص11", "ص12", "ص13", "ص14", "ص15", "ص16", "ص17", "ص18", "ص19", "ص20"), PairTexts.all.map { it.code }.toSet())
        assertEquals("every text is approved, existing or pending", PairTexts.all.size, PairTexts.all.count { it.approval != PairTextApproval.PENDING } + PairTexts.pending.size)
        assertEquals("one resource per slot", PairTexts.all.size, PairTexts.all.map { it.resource }.toSet().size)
    }

    @Test
    fun `each wire literal of the feature is spelled in one main file`() {
        val sources = mainKotlin().associateWith { stripComments(it.readText()) }
        val literals = listOf(
            // (`deviceName` is also a key of the analysis' answer, a different thing; `mapped` and `name_unmapped` are `MarketingNameWire`'s, the app's one source of them - PairLink.kt reads them from there)
            "listOpenMaintenanceBefores", "confirmMaintenanceAfterLink", "beforeScanId", "scannedAtMs", "validityHours", "candidates", "linkMethod",
            "BEFORE_NOT_FOUND", "BEFORE_NOT_OWNED", "BEFORE_WRONG_PURPOSE", "BEFORE_WRONG_DEFECT_TYPE", "BEFORE_ALREADY_PAIRED", "BEFORE_EXPIRED",
            "PAIR_NOT_AUTHENTICATED", "PAIR_SESSION_ID_REQUIRED", "PAIR_SESSION_NOT_AFTER", "PAIR_SESSION_NOT_ACTIVE", "PAIR_SESSION_DEFECT_TYPE_ABSENT",
            "PAIR_PART_NOT_ENABLED", "PAIR_VALIDITY_SETTING_ABSENT", "PAIR_BEFORE_SCAN_ID_REQUIRED", "PAIR_BEFORE_UNREADABLE", "PAIR_SCAN_CAP_EXCEEDED", "PAIR_OWNER_MISMATCH"
        )
        for (literal in literals) {
            val holders = sources.filter { it.value.contains("\"$literal\"") }.keys.map { it.name }
            assertEquals("\"$literal\" is spelled in $holders", listOf("PairLink.kt"), holders)
        }
    }

    @Test
    fun `the two callables leave the phone through one gate - the gate, then the auth, then the request - and nobody else asks for them`() {
        val service = code("data/remote/FirebaseAnalysisService.kt")
        for (callable in listOf("PairLinkWire.LIST_CALLABLE", "PairLinkWire.CONFIRM_CALLABLE")) {
            assertEquals("$callable: the gate comes first, then the auth", 1, Regex("""requirePairCallsApproved\(${Regex.escape(callable)}\) requireAuth\(\)""").findAll(service).count())
            assertEquals("$callable is requested in one place", 1, Regex("""getHttpsCallable\(${Regex.escape(callable)}\)""").findAll(service).count())
        }
        assertTrue("the gate reads the guard of the surface", service.contains("internal var pairCallsApproved: () -> Boolean = { PAIR_LIST_TEXTS_APPROVED }"))
        assertTrue("and throws when it is closed", service.contains("if (!pairCallsApproved()) throw PairCallsNotApprovedException(callable)"))
        // the answers keep the server's nulls (the unmapped name's names: null is a JSON null for the reader, not an absent key) - two, one per call, in this feature's own section
        val pairSection = service.substringAfter("suspend fun listOpenMaintenanceBefores(").substringBefore("suspend fun joinScanSession(")
        assertEquals(2, Regex("""gsonKeepingNulls\.toJsonTree\(result\.data\)""").findAll(pairSection).count())
        // the repeat scan's section is counted between its two markers (RepeatScanGuardTest): this feature's functions are not inside it
        val repeatSection = service.substringAfter("suspend fun getRepeatScanOffer(").substringBefore("internal var consentCallsApproved")
        assertFalse(repeatSection.contains("listOpenMaintenanceBefores") || repeatSection.contains("confirmMaintenanceAfterLink"))
        val askers = mainKotlin().filter { f ->
            val s = stripComments(f.readText())
            s.contains("PairLinkWire.LIST_CALLABLE") || s.contains("PairLinkWire.CONFIRM_CALLABLE")
        }.map { it.name }
        assertEquals("only the service names the callables to the SDK", listOf("FirebaseAnalysisService.kt"), askers)
        val callers = mainKotlin().filter { f ->
            val s = stripComments(f.readText())
            s.contains(".listOpenMaintenanceBefores(") || s.contains(".confirmMaintenanceAfterLink(")
        }.map { it.name }.sorted()
        assertEquals("the one caller of the two service calls is the client", listOf("PairLinkClient.kt"), callers)
        val clients = mainKotlin().filter { stripComments(it.readText()).contains("PairLinkClient(") && it.name != "PairLinkClient.kt" }.map { it.name }
        assertEquals("the client is built in the view models' file only", listOf("ViewModels.kt"), clients)
        assertEquals("one construction: the owner's view model", 1, Regex("""\bPairLinkClient\(""").findAll(code("presentation/viewmodel/ViewModels.kt")).count())
    }

    @Test
    fun `the list is asked for only after the server accepted a purpose, from one place - and a late answer for another asking is dropped`() {
        val vm = code("presentation/viewmodel/ViewModels.kt")
        assertEquals("asked from one place", 1, Regex("""\bloadPairs\(sessionId\)""").findAll(vm).count())
        assertEquals("the purpose the server accepted decides - the call comes after the accepted state", 1, Regex("""current\.accepted\(selection\)\) else s \} afterPurposeAccepted\(sessionId, selection\)""").findAll(vm).count())
        val after = vm.substringAfter("private fun afterPurposeAccepted(").substringBefore("private fun resetPairs(")
        assertTrue("only a maintenance_after purpose asks for it", after.contains("selection is PurposeSelection.Maintenance && selection.choice == ScanPurposeChoice.MAINTENANCE_AFTER"))
        assertTrue("every other accepted purpose takes the list away", after.contains("else resetPairs(sessionId)"))
        val load = vm.substringAfter("private fun loadPairs(").substringBefore("fun onPairCandidate(")
        assertTrue("a late answer is dropped by session and by asking", load.contains("if (s.sessionId == sessionId && generation == pairsGeneration)"))
        val confirm = vm.substringAfter("fun onPairConfirm() {").substringBefore("private fun attachListener(")
        assertTrue("the confirmation's answer is dropped the same way", confirm.contains("if (s.sessionId != sessionId || generation != pairsGeneration) s"))
        assertTrue("a new code is a new asking", vm.contains("_state.value = State(phase = Phase.CREATING) pairsGeneration++ repeatAskedFor = null viewModelScope.launch {"))
    }
}
