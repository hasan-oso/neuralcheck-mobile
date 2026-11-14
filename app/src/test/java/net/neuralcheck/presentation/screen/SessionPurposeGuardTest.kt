package net.neuralcheck.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The guard of the purpose chips and where their literals live - A-P6-03. Source-shape, with comments removed (the technique of `SessionScreensSourceShapeTest`).
 *
 * (Updated 2026-10-22, after Hasan's approval of rows ص1-ص21, rule 23: the guard is TRUE, the texts are in the resources, and the paragraph below describes the state BEFORE that approval, kept as the history of why the
 * guard existed.) Why: the chips' texts are PROPOSED, not approved (the two approved files, `plan/TEXTS-APPROVED-2026-10-10.md` and `plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md`, carry none; decision 90). So the surface
 * is built and tested, and drawn by nothing: [PURPOSE_CHIPS_TEXTS_APPROVED] is false, [purposeChipLabels] has no labels, the screen's one call to the chips is behind both, and no resource holds a
 * proposed text. THIS TEST FAILS ON PURPOSE the day the texts are approved and written; it is replaced then, in the same change (rule 23), not deleted by whoever finds it red.
 *
 * Rule 24: each wire literal of this feature is spelled in ONE main file.
 */
class SessionPurposeGuardTest {

    private val mainDir: File = listOf("app/src/main", "../app/src/main").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") }

    private fun squash(s: String) = s.replace(Regex("""\s+"""), " ").trim()

    private fun mainKotlin(): List<File> = File(mainDir, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun code(path: String): String = squash(stripComments(File(mainDir, "java/net/neuralcheck/$path").readText()))

    /**
     * Rule 23: REPLACES `the guard is false, the labels are none, and the screen draws the chips only behind both` (it pinned the guard false and `purposeChipLabels()` null), `no resource holds a proposed text or a
     * key for one` and `no resource holds a text of the fault type that is not approved` (they pinned the texts' ABSENCE from the resources). Hasan approved rows ص1-ص21 on 2026-10-22: the guard is true, the labels are read from the resources, and
     * `MaintenancePairApprovedTextsTest` pins each text, in both languages, to the approval.
     */
    @Test
    fun `the guard is true, the labels are read from the resources behind it, and the screen draws the chips only behind both`() {
        assertTrue("the chips' texts are approved (Hasan, 2026-10-22) and written to strings.xml in both languages", PURPOSE_CHIPS_TEXTS_APPROVED)
        val chips = code("presentation/screen/SessionPurposeChips.kt")
        assertTrue(
            "the labels are null while the guard is false, and are read from the resources of the phone's language otherwise",
            chips.contains("internal fun purposeChipLabels(): PurposeChipLabels? { if (!PURPOSE_CHIPS_TEXTS_APPROVED) return null LocalConfiguration.current return purposeChipLabelsOf(LocalContext.current.resources) }")
        )
        val screen = code("presentation/screen/GenerateSessionScreen.kt")
        assertEquals("the chips are called once", 1, Regex("""\bSessionPurposeChips\(""").findAll(screen).count())
        assertTrue(
            "behind the guard, an offered state and labels that exist",
            Regex("""if \(PURPOSE_CHIPS_TEXTS_APPROVED && purposeOffered != null && purposeLabels != null\) \{ [^}]*SessionPurposeChips\(""").containsMatchIn(screen)
        )
        assertEquals("the labels are read once, from the one function", 1, Regex("""\bpurposeChipLabels\(\)""").findAll(screen).count())
    }

    /**
     * Rule 23: REPLACES the literal map that pinned `battery_before` and `battery_after` to `SessionPurpose.kt` (the order of 2026-10-22 replaces the two battery purposes by `maintenance_before` / `maintenance_after`: contract A2
     * addendum, copy @b76c791). The two old values are now spelled NOWHERE in `app/src/main` - the app does not send them and reads them from no one - and the new literals, the fault type's two spelled ones and its key are
     * pinned to the one file. (`BATTERY`, `THERMAL` and `SOFTWARE` are not pinned: other features spell those words; the whole enum is pinned to the contract's table by `MaintenancePairContractCopyTest`.)
     */
    @Test
    fun `the screen hands the chips both callbacks - the chip tap and the type tap go to the two functions of the view model that decide`() {
        val screen = code("presentation/screen/GenerateSessionScreen.kt")
        assertEquals(1, Regex("""onSelectPurpose = \{ viewModel\.onPurposeChip\(it\) \}""").findAll(screen).count())
        assertEquals(1, Regex("""onSelectDefectType = \{ viewModel\.onDefectType\(it\) \}""").findAll(screen).count())
        assertTrue("the chips get both", Regex("""SessionPurposeChips\( offered = purposeOffered, locked = purposeChipsLocked\(state\.phase, purposeOffered\), labels = purposeLabels, onSelect = onSelectPurpose, onSelectType = onSelectDefectType \)""").containsMatchIn(screen))
        // the view model's only way to send a purpose is selectPurpose, which carries the type; the two entry points of the screen go through it
        val vm = code("presentation/viewmodel/ViewModels.kt")
        assertEquals("the chips' send primitive is called by the two entry points and nowhere else", 2, Regex("""\bselectPurpose\(""").findAll(vm).count() - 1)
        assertEquals(1, Regex("""analysisService\.setSessionPurpose\(""").findAll(vm).count())
    }

    /**
     * Q31 (2026-10-22), rule 24: the rule «the maintenance chips and the fault types need `purposes` AND `batteryBeforeAfter`» is written in ONE place - `ProtocolPartsRead.maintenanceOffered()` - and the chips' state is built
     * from it in ONE place (`purposeUiFor`). A second construction of the offered state, or a second reader of the second part for the chips, is a second place to forget when the rule changes.
     */
    @Test
    fun `the two-part rule of the maintenance chips is written once - one definition, one call that builds the offered state from it`() {
        val sources = mainKotlin().associateWith { stripComments(it.readText()) }
        val definitions = sources.filter { Regex("""fun maintenanceOffered\(\)""").containsMatchIn(it.value) }.keys.map { it.name }
        assertEquals(listOf("SessionPurpose.kt"), definitions)
        val callers = sources.filter { Regex("""\.maintenanceOffered\(\)""").containsMatchIn(it.value) }
        assertEquals(listOf("SessionPurposeUi.kt"), callers.keys.map { it.name })
        assertEquals(1, Regex("""\.maintenanceOffered\(\)""").findAll(callers.values.single()).count())
        val builders = sources.filter { Regex("""PurposeUi\.Offered\(""").containsMatchIn(it.value) }
        assertEquals("the offered state is built in one file", listOf("SessionPurposeUi.kt"), builders.keys.map { it.name })
        assertEquals("and at one place of it", 1, Regex("""PurposeUi\.Offered\(""").findAll(builders.values.single()).count())
        // the second part is named as a ProtocolPart by the rule and by the log line's token, and by nothing else
        val readers = sources.filter { it.value.contains("ProtocolPart.BATTERY_BEFORE_AFTER") }.keys.map { it.name }.sorted()
        assertEquals(listOf("SessionPurpose.kt", "SessionPurposeUi.kt"), readers)
    }

    /**
     * Q31 (2026-10-22), rule 22: the thirteen texts of the chips' surface have NO default value - a text that is not supplied is a compile error, not a blank on a technician's phone - and the last one, ص21 (the
     * refusal of a purpose the shop's parts do not allow), is among them; the labels are `null` while the guard is false, and read from the resources (thirteen, one `R.string` each) otherwise.
     */
    @Test
    fun `the labels of the chips have no default value - the part-not-enabled text is one of them - and each is read from a string resource`() {
        val chips = code("presentation/screen/SessionPurposeChips.kt")
        val constructor = Regex("""internal data class PurposeChipLabels\((.*?)\) \{""").find(chips)?.groupValues?.get(1) ?: error("PurposeChipLabels is not found in SessionPurposeChips.kt")
        assertFalse("a default value in the labels' constructor: $constructor", constructor.contains("="))
        assertTrue("the ص21 text is a label of the surface", constructor.contains("val partNotEnabled: String"))
        assertEquals("fifteen texts", 15, Regex("""\bval \w+: String""").findAll(constructor).count())
        assertTrue("the labels are read from the resources, one R.string per text, none written in Kotlin", Regex("""resources\.getString\(R\.string\.\w+\)""").findAll(chips).count() == 15)
        assertTrue(PURPOSE_CHIPS_TEXTS_APPROVED)
    }

    /**
     * The chips' counterpart of `PairListGuardTest`'s «the guard may be true only when no text is pending»: the guard may be true only when each of the thirteen texts of [PurposeChipLabels] has its string in `values` AND
     * in `values-tr` (it fails on purpose the day one is missing or a label is added without its text: rule 23, not deleted by whoever finds it red).
     */
    @Test
    fun `the guard may be true only when every text of the chips is written in both languages`() {
        val chips = code("presentation/screen/SessionPurposeChips.kt")
        val names = Regex("""resources\.getString\(R\.string\.(\w+)\)""").findAll(chips).map { it.groupValues[1] }.toList()
        assertEquals("one string per label of PurposeChipLabels", 15, names.size)
        assertEquals(names.size, names.toSet().size)
        val resDir = File(mainDir, "res")
        val files = listOf("values", "values-tr").associateWith { File(resDir, "$it/strings.xml").readText() }
        val missing = names.flatMap { n -> files.filter { (_, xml) -> Regex("""<string name="$n">[^<]+</string>""").findAll(xml).count() != 1 }.keys.map { "$n in $it" } }
        assertTrue("the guard is true while a text is missing: $missing", !PURPOSE_CHIPS_TEXTS_APPROVED || missing.isEmpty())
        assertEquals("no text is missing: the guard is true because of this", emptyList<String>(), missing)
    }

    @Test
    fun `each wire literal of the feature is spelled in one main file - and the two old battery purposes in none`() {
        val sources = mainKotlin().associateWith { stripComments(it.readText()) }
        val literals = mapOf(
            "setSessionPurpose" to "SessionPurpose.kt",
            "maintenance_before" to "SessionPurpose.kt",
            "maintenance_after" to "SessionPurpose.kt",
            "defectType" to "SessionPurpose.kt",
            "CHARGING_PORT" to "SessionPurpose.kt",
            "MOTHERBOARD" to "SessionPurpose.kt",
            "batteryBeforeAfter" to "SessionPurpose.kt",
            "protocolParts" to "SessionPurpose.kt",
            "PURPOSE_SESSION_NOT_PENDING" to "SessionContract.kt",
            "DEFECT_TYPE_REQUIRED" to "SessionContract.kt",
            "DEFECT_TYPE_INVALID" to "SessionContract.kt",
            "DEFECT_TYPE_NOT_APPLICABLE" to "SessionContract.kt",
            "SET_PURPOSE_INVALID" to "SessionContract.kt",
            "PURPOSE_PART_NOT_ENABLED" to "SessionContract.kt"
        )
        for ((literal, owner) in literals) {
            val holders = sources.filter { it.value.contains("\"$literal\"") }.keys.map { it.name }
            assertEquals("\"$literal\" is spelled in $holders", listOf(owner), holders)
        }
        for (old in listOf("battery_before", "battery_after")) {
            val holders = sources.filter { it.value.contains(old) }.keys.map { it.name }
            assertEquals("the old purpose $old is spelled in $holders", emptyList<String>(), holders)
        }
        // the call site and the DTO use the names, not the literals: the service has none of them quoted (the DTO's annotation takes the constant)
        val service = sources.entries.single { it.key.name == "FirebaseAnalysisService.kt" }.value
        assertFalse(service.contains("\"setSessionPurpose\""))
        assertEquals(1, Regex("""@SerializedName\(ProtocolPartsRead\.KEY\)""").findAll(service).count())
    }
}
