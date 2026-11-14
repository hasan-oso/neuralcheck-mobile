package net.neuralcheck.presentation.screen

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.domain.pair.PairFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * The approved texts of the maintenance before/after cycle (rows ص1 to ص21 of `TEXTS-FOR-APPROVAL-MAINTENANCE-PAIRS-2026-10-22.md`, approved by Hasan on 2026-10-22) are in the resources, VERBATIM, in both languages,
 * and the labels the screen uses are those resources (rules 13, 22, 23, 29, 41). The table they are tested against is [ApprovedMaintenanceTexts]: a copy, generated from the approval, with its source and sha256.
 *
 * What is asserted: every row is written once in `values` and once in `values-tr` and equals the approval; nothing else of the cycle's names is in the resources (no other text was written); the placeholders
 * (`%1$s %2$s %3$s %1$d`) are the same in both languages; the resources AS THE APP READS THEM under the Arabic and the Turkish locales give the approved text (this is what the AAPT build made of the XML, not the XML);
 * `purposeChipLabelsOf` and `pairLabelsOf` - the two functions the screen's labels are made by - give each text of the right row, in each language, with the arguments put in; every slot and every field names the resource of
 * its row (one source per text); and no Kotlin file of `app/src/main` holds one of the texts as a literal. Replaces the tests that pinned the texts' ABSENCE from the resources (rule 23).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MaintenancePairApprovedTextsTest {

    private val resDir: File = listOf("app/src/main/res", "../app/src/main/res").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/res from ${File(".").absolutePath}")

    private val mainDir: File = listOf("app/src/main", "../app/src/main").map { File(it) }.firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main from ${File(".").absolutePath}")

    private val valuesXml: String = File(resDir, "values/strings.xml").readText()
    private val valuesTrXml: String = File(resDir, "values-tr/strings.xml").readText()

    private val rows = ApprovedMaintenanceTexts.rows
    private val written = rows.filter { !it.existing }

    private fun definitions(xml: String, name: String): List<String> =
        Regex("""<string name="${Regex.escape(name)}"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).map { it.groupValues[1] }.toList()

    private fun namesWithPrefixes(xml: String): Set<String> =
        Regex("""<string name="([^"]+)"""").findAll(xml).map { it.groupValues[1] }.filter { n -> PREFIXES.any { n.startsWith(it) } }.toSet()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun resource(resources: Resources, name: String): String {
        val id = resources.getIdentifier(name, "string", context.packageName)
        assertTrue("no string resource named $name", id != 0)
        return resources.getString(id)
    }

    // ---- the files ----

    @Test
    fun `the table is the approval's twenty-five rows plus the two of 2026-10-28 - two of them existing, one derived Turkish - and every resource name is unique`() {
        assertEquals(27, rows.size)
        assertEquals(setOf("ص12", "ص13"), rows.filter { it.existing }.map { it.code }.toSet())
        assertEquals(listOf("ص10"), rows.filter { it.derivedTr }.map { it.code })
        assertEquals(rows.size, rows.map { it.code }.toSet().size)
        assertEquals(rows.size, rows.map { it.resource }.toSet().size)
        assertEquals("the source's sha256 is recorded", 64, ApprovedMaintenanceTexts.SOURCE_SHA256.length)
    }

    @Test
    fun `every approved text is written verbatim - once in values and once in values-tr - and the two existing ones are single and unchanged`() {
        for (row in rows) {
            assertEquals("${row.code} ${row.resource} in values", listOf(row.ar), definitions(valuesXml, row.resource))
            assertEquals("${row.code} ${row.resource} in values-tr", listOf(row.tr), definitions(valuesTrXml, row.resource))
        }
    }

    @Test
    fun `no other text of the cycle's names is in the resources - the names with its prefixes are exactly the written rows' in both files`() {
        val expected = written.map { it.resource }.toSet()
        assertEquals(expected, namesWithPrefixes(valuesXml))
        assertEquals(expected, namesWithPrefixes(valuesTrXml))
        assertEquals(25, expected.size)
    }

    @Test
    fun `the placeholders of every text are the same in both languages - and are the ones the screen fills`() {
        val placeholder = Regex("""%\d+\$[sd]""")
        val expected = mapOf("ص10" to listOf("%1\$s", "%2\$s", "%3\$s"), "ص11" to listOf("%1\$d"), "ص12" to listOf("%1\$s"))
        for (row in rows) {
            val ar = placeholder.findAll(row.ar).map { it.value }.toList()
            val tr = placeholder.findAll(row.tr).map { it.value }.toList()
            assertEquals("${row.code}: Arabic and Turkish placeholders", ar, tr)
            assertEquals("${row.code}: the placeholders the screen fills", expected[row.code] ?: emptyList<String>(), ar)
        }
        // and the same check on the files themselves, not on the table
        for (row in rows) {
            val inValues = placeholder.findAll(definitions(valuesXml, row.resource).single()).map { it.value }.toList()
            val inValuesTr = placeholder.findAll(definitions(valuesTrXml, row.resource).single()).map { it.value }.toList()
            assertEquals(row.code, inValues, inValuesTr)
        }
    }

    // ---- the resources as the app reads them ----

    @Test
    @Config(qualifiers = "ar")
    fun `the resources give the approved Arabic under the Arabic locale - every row`() {
        for (row in rows) assertEquals(row.code, row.ar, resource(context.resources, row.resource))
    }

    @Test
    @Config(qualifiers = "tr")
    fun `the resources give the approved Turkish under the Turkish locale - every row, none falls back to the Arabic`() {
        for (row in rows) {
            val text = resource(context.resources, row.resource)
            assertEquals(row.code, row.tr, text)
            if (row.ar != row.tr) assertTrue("${row.code} fell back to the Arabic", text != row.ar)
        }
    }

    // ---- the labels the screen is made of ----

    private fun checkChipLabels(resources: Resources, tr: Boolean) {
        fun t(code: String) = ApprovedMaintenanceTexts.text(code, tr)
        val labels = purposeChipLabelsOf(resources)
        assertEquals(t("ص1"), labels.title)
        assertEquals(t("ص2"), labels.normal)
        assertEquals(t("ص3"), labels.maintenanceBefore)
        assertEquals(t("ص4"), labels.maintenanceAfter)
        assertEquals(t("ص5"), labels.lockedNote)
        assertEquals(t("ص7"), labels.typeTitle)
        assertEquals(t("ص8"), labels.typeHint)
        assertEquals(t("ص22"), labels.typeTitleAfter)
        assertEquals(t("ص23"), labels.typeHintAfter)
        // the list under «بعد الصيانة» has its own title and hint, and «قبل الصيانة» keeps ص7 and ص8 (order of 2026-10-28: the two screens must not share these two texts)
        assertEquals(t("ص22"), labels.typeTitleFor(ScanPurposeChoice.MAINTENANCE_AFTER))
        assertEquals(t("ص23"), labels.typeHintFor(ScanPurposeChoice.MAINTENANCE_AFTER))
        assertEquals(t("ص7"), labels.typeTitleFor(ScanPurposeChoice.MAINTENANCE_BEFORE))
        assertEquals(t("ص8"), labels.typeHintFor(ScanPurposeChoice.MAINTENANCE_BEFORE))
        assertTrue("the after title differs from the before one", labels.typeTitleAfter != labels.typeTitle)
        assertTrue("the after hint differs from the before one", labels.typeHintAfter != labels.typeHint)
        assertEquals(t("ص21"), labels.partNotEnabled)
        for (choice in ScanPurposeChoice.values()) {
            val code = when (choice) {
                ScanPurposeChoice.NORMAL -> "ص2"
                ScanPurposeChoice.MAINTENANCE_BEFORE -> "ص3"
                ScanPurposeChoice.MAINTENANCE_AFTER -> "ص4"
            }
            assertEquals(choice.wire, t(code), labels.of(choice))
        }
        for (type in DefectType.values()) {
            val code = when (type) {
                DefectType.BATTERY -> "ص6-1"
                DefectType.CHARGING_PORT -> "ص6-2"
                DefectType.THERMAL -> "ص6-3"
                DefectType.MOTHERBOARD -> "ص6-4"
                DefectType.SOFTWARE -> "ص6-5"
            }
            assertEquals(type.wire, t(code), labels.of(type))
        }
    }

    private fun checkPairLabels(resources: Resources, tr: Boolean) {
        fun t(code: String) = ApprovedMaintenanceTexts.text(code, tr)
        val labels = pairLabelsOf(resources)
        assertEquals(t("ص9"), labels(PairText.PickPrompt))
        assertEquals(t("ص13"), labels(PairText.Confirm))
        assertEquals(t("ص20"), labels(PairText.NoDeviceName))
        for (kind in PairFailureKind.values()) {
            val code = when (kind) {
                PairFailureKind.NOT_AVAILABLE -> "ص14"
                PairFailureKind.ALREADY_PAIRED -> "ص15"
                PairFailureKind.EXPIRED -> "ص16"
                PairFailureKind.WRONG_DEFECT_TYPE -> "ص17"
                PairFailureKind.GENERAL -> "ص18"
                PairFailureKind.NOT_ENABLED -> "ص19"
            }
            assertEquals(kind.name, t(code), labels(PairText.Failure(kind)))
        }
        assertEquals(t("ص10").replace("%1\$s", "XYZ-1").replace("%2\$s", "22 Oct 2026").replace("%3\$s", "14:05"), labels(PairText.Row("XYZ-1", "22 Oct 2026", "14:05")))
        assertEquals(t("ص11").replace("%1\$d", "72"), labels(PairText.Empty(72)))
        assertEquals(t("ص12").replace("%1\$s", "22 Oct 2026"), labels(PairText.Linked("22 Oct 2026")))
    }

    @Test
    @Config(qualifiers = "ar")
    fun `the chips and the list are made of the approved Arabic - every label, with the arguments put in and the digits Latin`() {
        checkChipLabels(context.resources, tr = false)
        checkPairLabels(context.resources, tr = false)
    }

    @Test
    @Config(qualifiers = "tr")
    fun `the chips and the list are made of the approved Turkish - every label, with the arguments put in`() {
        checkChipLabels(context.resources, tr = true)
        checkPairLabels(context.resources, tr = true)
    }

    @Test
    fun `every slot of the pair list names the resource of its row, and every label of the chips reads the resource of its row - one source per text`() {
        val resources = context.resources
        for (slot in PairTextSlot.values()) {
            assertEquals(slot.code, ApprovedMaintenanceTexts.row(slot.code).resource, resources.getResourceEntryName(slot.resource))
        }
        assertEquals("every row of the list has a slot", rows.map { it.code }.filter { it in setOf("ص9", "ص10", "ص11", "ص12", "ص13", "ص14", "ص15", "ص16", "ص17", "ص18", "ص19", "ص20") }.toSet(), PairTextSlot.values().map { it.code }.toSet())
        // the chips: the fifteen R.string of purposeChipLabelsOf are the fifteen rows ص1-ص8, ص21 and ص22-ص23 (ص6 being five)
        val chipsSource = stripComments(File(mainDir, "java/net/neuralcheck/presentation/screen/SessionPurposeChips.kt").readText())
        val used = Regex("""R\.string\.(\w+)""").findAll(chipsSource).map { it.groupValues[1] }.toSet()
        val chipRows = rows.filter { it.code in CHIP_CODES }
        assertEquals(15, chipRows.size)
        assertEquals(chipRows.map { it.resource }.toSet(), used)
    }

    // ---- no text in Kotlin ----

    @Test
    fun `no Kotlin file of main holds one of the approved texts as a literal - they are read from the resources`() {
        val sources = File(mainDir, "java").walkTopDown().filter { it.isFile && it.extension == "kt" }.associateWith { stripComments(it.readText()) }
        val texts = rows.filter { !it.existing }.flatMap { listOf(it.ar, it.tr) }.filter { it.length >= 10 || it.contains(' ') }
        for (text in texts) {
            val holders = sources.filter { it.value.contains(text) }.keys.map { it.name }
            assertEquals("«$text» is a literal in $holders", emptyList<String>(), holders)
        }
        assertTrue("the check looked at texts", texts.size > 30)
    }

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") }

    private companion object {
        val PREFIXES = listOf("purpose_chip", "purpose_part", "defect_type_", "pair_")
        val CHIP_CODES = setOf("ص1", "ص2", "ص3", "ص4", "ص5", "ص6-1", "ص6-2", "ص6-3", "ص6-4", "ص6-5", "ص7", "ص8", "ص21", "ص22", "ص23")
    }
}
