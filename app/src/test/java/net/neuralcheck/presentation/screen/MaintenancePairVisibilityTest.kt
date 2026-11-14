package net.neuralcheck.presentation.screen

import net.neuralcheck.data.remote.model.AiAnalysisResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * «قبل/بعد صيانة» is for the technician and the administrator's panel ALONE (decision 4 of Hasan, order of 2026-10-22; `WIRE-SPEC-MAINTENANCE-PAIRS-2026-10-22.md` section 3-d): not on the scanned phone, not in a report,
 * not in the PDF, not in the analysis' answer. `scanSessions.purpose` is READABLE by the phone that joined (the server's rules let the joined device read the session document), so what keeps the purpose, the
 * fault type and the link off the customer's screens is that no code the customer's phone runs reads them. This test fails the day one does - source-shape, with comments removed (the technique of
 * `ImmersiveFullScreenDialogTest`'s "no weight" check), plus the SHAPE of [AiAnalysisResponse] read by reflection.
 *
 * What is checked, and what is not:
 *  - the feature's own names (the purposes, the fault type, the callables, the pair types, the session document's `purpose`, the protocol collections) may be spelled ONLY in the owner-side files below - the ones of the
 *    purpose chips, the pair list, their calls, and the owner's session screen and view model. Every other main file, the scanned phone's screens, the report screen, the PDF generator and the session watcher
 *    among them, must hold none (the named files must also EXIST, so a rename is noticed);
 *  - `linkedScanId` is NOT one of the names: the app has an older, local pre/post-repair link (`ScanEntity`, the `pre_scan/{scanType}/{linkedScanId}` routes, `FirebaseSyncService`) that is another thing;
 *  - the owner's view model lives in `ViewModels.kt` with the scanned phone's and the report's: only the text of `GenerateSessionViewModel` may hold the names;
 *  - no field of the analysis' answer, nor of any type it holds, is named for the purpose, the type or the link.
 * What it cannot see (named, not hidden): a screen that reads the session document through a generic map and a key built at run time; the SERVER's own side (the same rule is the server's `maintenancePairVisibility`
 * test, S7). The scanned phone's reads of the session document are the status and the cancel reason (`SessionDocWatcher`), and this test keeps it so.
 */
class MaintenancePairVisibilityTest {

    private val mainDir: File = listOf("app/src/main", "../app/src/main").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") }

    private fun kotlinFiles(): List<File> = File(mainDir, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun rel(f: File) = f.path.replace('\\', '/').substringAfter("java/net/neuralcheck/")

    /** The feature's own names: the two purposes and the fault type, the pair and the purpose types, the callables, the session document's key and the server's protocol collections. */
    private val names = listOf(
        "maintenance_before", "maintenance_after", "defectType", "DefectType", "ScanPurposeChoice", "PurposeSelection", "PurposeUi", "PurposeChipLabels", "SessionPurposeWire",
        "PairCandidate", "PairLinkWire", "PairRefusal", "PairListUi", "PairLinkClient", "listOpenMaintenanceBefores", "confirmMaintenanceAfterLink",
        "linkState", "linkMethod", "scanPurpose", "\"purpose\"", "purposeSource", "scanProtocol", "protocolPending"
    )

    /** The files that may spell them: the owner's side only. */
    private val ownerSide = setOf(
        "data/remote/FirebaseAnalysisService.kt",
        "data/remote/PairLinkClient.kt",
        "data/remote/SessionContract.kt",
        "data/remote/SessionPurpose.kt",
        "domain/pair/PairLink.kt",
        "presentation/screen/GenerateSessionScreen.kt",
        "presentation/screen/PairListSurface.kt",
        "presentation/screen/SessionPurposeChips.kt",
        "presentation/viewmodel/PairListUi.kt",
        "presentation/viewmodel/SessionPurposeUi.kt",
        "presentation/viewmodel/ViewModels.kt",
        "util/EvalCardLog.kt"
    )

    /** What the scanned phone and the reports run: each must exist and hold none of the names. */
    private val customerSide = listOf(
        "presentation/screen/ReportScreen.kt",
        "util/ReportPdfGenerator.kt",
        "presentation/screen/ReportPdfExportButtons.kt",
        "presentation/screen/JoinSessionScreen.kt",
        "presentation/screen/QRScannerScreen.kt",
        "presentation/screen/ConsentScreen.kt",
        "presentation/screen/ConsentSurface.kt",
        "presentation/screen/ScannedPhoneScreen.kt",
        "presentation/screen/ActiveScanScreen.kt",
        "presentation/screen/PreScanScreen.kt",
        "presentation/screen/ScanHistoryScreen.kt",
        "presentation/screen/HistoryScreen.kt",
        "data/remote/SessionDocWatcher.kt",
        "data/remote/JoinWithDeviceIdentity.kt",
        "data/remote/ScanSessionHolder.kt",
        "data/remote/model/AiAnalysisResponse.kt"
    )

    private fun namesIn(code: String) = names.filter { code.contains(it) }

    /** The text of `ViewModels.kt` without the owner's view model (from its declaration to the next declaration at column 0). */
    private fun viewModelsWithoutOwner(): String {
        val code = stripComments(File(mainDir, "java/net/neuralcheck/presentation/viewmodel/ViewModels.kt").readText())
        val from = code.indexOf("\nclass GenerateSessionViewModel")
        assertTrue("the owner's view model is in ViewModels.kt", from >= 0)
        val after = Regex("""\n(?=@HiltViewModel|class |object |internal |private |fun |sealed |data |enum |interface )""").find(code, from + 5)
        val to = after?.range?.first ?: code.length
        // the file's import lines are not a read: the owner's view model needs its imports, and an import is not a use
        return (code.substring(0, from) + code.substring(to)).lines().filter { !it.startsWith("import ") }.joinToString("\n")
    }

    @Test
    fun `the feature's names are spelled only on the owner's side - every other main file holds none`() {
        val holders = kotlinFiles().filter { f ->
            val code = stripComments(f.readText())
            namesIn(code).isNotEmpty()
        }.map { rel(it) }.toSet()
        assertTrue("the owner-side files that spell the names: $holders", holders.isNotEmpty())
        assertEquals("a file outside the owner's side spells the feature's names", emptySet<String>(), holders - ownerSide)
        for (owner in ownerSide) assertTrue("the owner-side file $owner exists", File(mainDir, "java/net/neuralcheck/$owner").isFile)
    }

    @Test
    fun `the screens the scanned phone and the reports run exist and read none of them - the report screen, the PDF, the session watcher included`() {
        for (path in customerSide) {
            val file = File(mainDir, "java/net/neuralcheck/$path")
            assertTrue("$path does not exist - a rename must be followed here", file.isFile)
            val found = namesIn(stripComments(file.readText()))
            assertEquals("$path reads the purpose, the type or the link: $found", emptyList<String>(), found)
        }
    }

    @Test
    fun `the owner's view model is the only text of ViewModels dot kt that holds the names - the scanned phone's and the report's view models hold none`() {
        val rest = viewModelsWithoutOwner()
        assertEquals("a view model other than the owner's holds the names", emptyList<String>(), namesIn(rest))
        // the owner's own text does hold them (the cut is not an empty one)
        val whole = stripComments(File(mainDir, "java/net/neuralcheck/presentation/viewmodel/ViewModels.kt").readText())
        assertTrue(namesIn(whole).isNotEmpty())
        assertTrue("the cut left the rest of the file", rest.length > whole.length / 2)
    }

    @Test
    fun `the chips and the list are drawn by the owner's screen alone - nobody else calls the two surfaces or reads their state`() {
        for ((call, owner) in listOf("SessionPurposeChips(" to "GenerateSessionScreen.kt", "PairListSection(" to "GenerateSessionScreen.kt")) {
            val callers = kotlinFiles().filter { f -> stripComments(f.readText()).contains(call) && !f.name.startsWith("SessionPurposeChips") && !f.name.startsWith("PairListSurface") }.map { it.name }
            assertEquals("$call is called by $callers", listOf(owner), callers)
        }
        // the state types are read by the owner's screen, its view model and the files of the two surfaces and their state, nobody else
        val ownerFiles = setOf("GenerateSessionScreen.kt", "ViewModels.kt", "PairListUi.kt", "PairListSurface.kt", "SessionPurposeUi.kt", "SessionPurposeChips.kt")
        for (type in listOf("PairListUi", "PurposeUi")) {
            val readers = kotlinFiles().filter { stripComments(it.readText()).contains(type) }.map { it.name }.toSet()
            assertTrue("$type is read only by the owner's side: $readers", readers.isNotEmpty() && readers.all { it in ownerFiles })
        }
    }

    // ---- the analysis' answer ----

    private val forbiddenFields = setOf("scanPurpose", "purpose", "purposeSource", "defectType", "linkedScanId", "linkState", "linkMethod", "pair", "scanProtocol", "protocolPending", "maintenancePair", "linkModelCheck")

    private fun classesOf(type: Type, into: MutableSet<Class<*>>) {
        when (type) {
            is Class<*> -> {
                if (type.isArray) classesOf(type.componentType, into) else into += type
            }
            is ParameterizedType -> {
                classesOf(type.rawType, into)
                type.actualTypeArguments.forEach { classesOf(it, into) }
            }
            else -> Unit
        }
    }

    /** Every class [AiAnalysisResponse] holds, transitively, that is the app's own (not an enum, not a platform or Kotlin class). */
    private fun heldClasses(): Set<Class<*>> {
        val seen = linkedSetOf<Class<*>>()
        fun visit(c: Class<*>) {
            if (!seen.add(c)) return
            for (field in c.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                val found = linkedSetOf<Class<*>>()
                classesOf(field.genericType, found)
                for (held in found) if (held.name.startsWith("net.neuralcheck.") && !held.isEnum) visit(held)
            }
        }
        visit(AiAnalysisResponse::class.java)
        return seen
    }

    @Test
    fun `no field of the analysis' answer, nor of any type it holds, is named for the purpose, the fault type or the link`() {
        val classes = heldClasses()
        assertTrue("the walk reached the answer's own classes: ${classes.map { it.simpleName }}", classes.size > 3)
        for (c in classes) {
            for (field in c.declaredFields) {
                if (Modifier.isStatic(field.modifiers)) continue
                val serialized = field.annotations.filter { it.annotationClass.simpleName == "SerializedName" }.flatMap { a ->
                    val value = a.annotationClass.java.getMethod("value").invoke(a) as String
                    val alternate = (a.annotationClass.java.getMethod("alternate").invoke(a) as Array<*>).map { it.toString() }
                    listOf(value) + alternate
                }
                assertFalse("${c.simpleName}.${field.name} is named for the feature", field.name in forbiddenFields)
                for (s in serialized) assertFalse("${c.simpleName}.${field.name} is serialized as «$s»", s in forbiddenFields)
            }
        }
    }

    /**
     * Decision Q36 (2026-10-22; contract copy @f3c1bab): `scanProtocol.linkModelCheck` (`LINK_MODEL_MATCH` | `LINK_MODEL_MISMATCH` | `LINK_MODEL_ABSENT`, or null) is a SERVER-side note on the «after» scan: it is in no request,
     * no reply, not in `verifyReport` and not in a report - the contract says «خادمي (لا يصل العميل)». So no reader exists in the app: the field and its three values are spelled in NO main file at all (not even the
     * owner's side, which may spell the other names of the feature), and no field of the analysis' answer is named for it (the list above).
     */
    @Test
    fun `the server-only linkModelCheck and its three values are spelled in no main file - the app has no reader for what never reaches it`() {
        for (f in kotlinFiles()) {
            val code = stripComments(f.readText())
            for (name in listOf("linkModelCheck", "LINK_MODEL_MATCH", "LINK_MODEL_MISMATCH", "LINK_MODEL_ABSENT", "LINK_MODEL_CHECK")) {
                assertFalse("${rel(f)} spells $name", code.contains(name))
            }
        }
    }

    @Test
    fun `the old battery purposes are not on any screen either - nothing the app shows or sends says battery_before or battery_after`() {
        for (f in kotlinFiles()) {
            val code = stripComments(f.readText())
            assertFalse("${rel(f)} spells a battery purpose", code.contains("battery_before") || code.contains("battery_after"))
        }
    }
}
