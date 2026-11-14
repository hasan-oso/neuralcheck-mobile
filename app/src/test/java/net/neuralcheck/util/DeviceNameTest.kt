package net.neuralcheck.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app's marketing-name map and its two states - A-P6-20, the APP half (`plan/PLAN-06-DATA-COLLECTION.md:1527-1536`, the row `:1061`; contract A12 as copied in
 * `evidence/run-2026-10-18/protocol-contract-A8-A11-A12-neuralcheck-backend-5361116.md`, neuralcheck-backend@5361116, NOT deployed). The plan: «وخريطة التطبيق تبقى للعرض دون شبكة، ويُختبر تطابقها مع مصدر الخادم بعيّنة
 * ذهبية» (`:1534`) and A12: «وخريطة التطبيق نسخة مختبرة» - until this row the map had NO test of any kind. What is asserted: the map is the plan's 118 entries and no key is spelled twice (a Kotlin `mapOf` keeps the last
 * of a repeated key without a word); every model of the map resolves, to itself, as `mapped` - the eleven Pixel keys that have lower-case letters did not (the lookup upper-cased the model and the keys are not upper
 * case); the display of every model, for any manufacturer, is what it was before the states existed (this row changes no text and no stored value: `current()` feeds `ScanEntity.deviceModel` and the time-series
 * header); a model the map does not hold, and a missing one, are `name_unmapped` with the contract's reason; the contract's names are spelled once.
 *
 * NOT asserted, because it cannot be: parity with the SERVER's map by a golden sample (`:1534`) - the server's map is ABSENT until the administrator enters it (A12 pending 2: «لا عيّنة قبل وجود بيانات»).
 */
class DeviceNameTest {

    private val manufacturers = listOf("", "Google", "samsung", "Xiaomi", "OPPO")

    private val source: String = listOf("app/src/main/java/net/neuralcheck/util/DeviceName.kt", "../app/src/main/java/net/neuralcheck/util/DeviceName.kt")
        .map { File(it) }.firstOrNull { it.isFile }?.readText() ?: error("could not locate DeviceName.kt from ${File(".").absolutePath}")

    /** `prettify` as it was written at 10f8950 (the lookup upper-cases the model; nothing else differs): what the display of every model was before the states. */
    private fun displayAtStart(model: String?, manufacturer: String?): String {
        if (model.isNullOrBlank()) return model.orEmpty()
        val mapped = DeviceName.knownEntries[model.uppercase()]
        val mfr = manufacturer.orEmpty().lowercase().replaceFirstChar { it.uppercase() }
        if (mapped != null) return if (mfr.isNotBlank() && !mapped.startsWith(mfr, ignoreCase = true)) "$mfr $mapped" else mapped
        if (mfr.isNotBlank() && model.startsWith(mfr, ignoreCase = true)) return model
        return if (mfr.isNotBlank()) "$mfr $model" else model
    }

    @Test
    fun `the map is the plan's 118 entries - each pair of the source is one entry, so no key is spelled twice`() {
        val pairs = Regex("""^\s+"[^"]+" to "[^"]+",?\s*$""", RegexOption.MULTILINE).findAll(source).count()
        assertEquals("PLAN-06:1529: «118 مدخلاً»", 118, pairs)
        assertEquals("a repeated key would be silently one entry", pairs, DeviceName.knownEntries.size)
    }

    @Test
    fun `every model of the map resolves, to itself, as mapped - the eleven Pixel keys with lower-case letters too`() {
        val lower = DeviceName.knownEntries.keys.filter { k -> k.any { it.isLowerCase() } }
        assertEquals("the keys the old lookup could never reach", 11, lower.size)
        for (model in DeviceName.knownEntries.keys) {
            val r = DeviceName.resolve(model, "Samsung")
            assertTrue("$model: $r", r is MarketingNameResolution.Mapped)
            r as MarketingNameResolution.Mapped
            assertEquals(model, r.model)
            assertEquals(DeviceName.knownEntries[model], r.marketingName)
            assertEquals(MarketingNameState.MAPPED, r.state)
        }
    }

    @Test
    fun `a model reported in lower case still maps - the lookup stays case-insensitive for the upper-case keys`() {
        val r = DeviceName.resolve("sm-g977n", "samsung")
        assertTrue("$r", r is MarketingNameResolution.Mapped)
        assertEquals("Samsung Galaxy S10 5G", r.display)
        assertEquals("a key with lower-case letters is found by its own spelling", MarketingNameState.MAPPED, DeviceName.resolve("Pixel 4a", "Google").state)
    }

    @Test
    fun `the display of every model, for every manufacturer, is what it was before the states - no text and no stored value changed`() {
        for (model in DeviceName.knownEntries.keys + listOf("SM-G977N", "sm-g977n", "Samsung SM-G977N", "23021RAA2Y", "ZZ-9999", "xiaomi ZZ-9999", "Pixel 4A", "pixel 4a", " ", "")) {
            for (m in manufacturers + listOf(null)) {
                assertEquals("$model / $m", displayAtStart(model, m), DeviceName.resolve(model, m).display)
                assertEquals("prettify is the display of resolve: $model / $m", DeviceName.resolve(model, m).display, DeviceName.prettify(model, m))
            }
        }
        assertEquals("Samsung Galaxy S10 5G", DeviceName.prettify("SM-G977N", "samsung"))
        assertEquals("Google Pixel 4a", DeviceName.prettify("Pixel 4a", "Google"))
    }

    @Test
    fun `a model the map does not hold is name_unmapped with its reason - and shows what it always showed`() {
        val unknown = DeviceName.resolve("ZZ-9999", "Xiaomi")
        assertEquals(MarketingNameResolution.Unmapped("ZZ-9999", NameUnmappedReason.MODEL_NOT_IN_MAP, "Xiaomi ZZ-9999"), unknown)
        assertEquals(MarketingNameState.NAME_UNMAPPED, unknown.state)
        // the manufacturer already inside the model is not doubled
        assertEquals(MarketingNameResolution.Unmapped("Samsung SM-X999Z", NameUnmappedReason.MODEL_NOT_IN_MAP, "Samsung SM-X999Z"), DeviceName.resolve("Samsung SM-X999Z", "samsung"))
        // no manufacturer: the model alone
        assertEquals(MarketingNameResolution.Unmapped("ZZ-9999", NameUnmappedReason.MODEL_NOT_IN_MAP, "ZZ-9999"), DeviceName.resolve("ZZ-9999", null))
    }

    @Test
    fun `a missing or blank model is name_unmapped with MODEL_ABSENT - the input comes back untouched, as before`() {
        assertEquals(MarketingNameResolution.Unmapped(null, NameUnmappedReason.MODEL_ABSENT, ""), DeviceName.resolve(null, "Google"))
        assertEquals(MarketingNameResolution.Unmapped("", NameUnmappedReason.MODEL_ABSENT, ""), DeviceName.resolve("", "Google"))
        assertEquals(MarketingNameResolution.Unmapped("  ", NameUnmappedReason.MODEL_ABSENT, "  "), DeviceName.resolve("  ", "Google"))
    }

    @Test
    fun `the contract's names are the contract's - and each is spelled in one main file`() {
        assertEquals(listOf("mapped", "name_unmapped"), MarketingNameState.values().map { it.wire })
        assertEquals(listOf("MODEL_NOT_IN_MAP", "MODEL_ABSENT"), NameUnmappedReason.values().map { it.wire })
        val main = listOf("app/src/main/java", "../app/src/main/java").map { File(it) }.first { it.isDirectory }
        val strip = { s: String -> s.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ").lines().joinToString("\n") { it.substringBefore("//") } }
        val files = main.walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.name to strip(it.readText()) }.toList()
        for (literal in listOf("name_unmapped", "MODEL_NOT_IN_MAP", "MODEL_ABSENT")) {
            assertEquals("\"$literal\"", listOf("DeviceName.kt"), files.filter { it.second.contains("\"$literal\"") }.map { it.first })
        }
        assertFalse("the app's states never leave the phone: nothing sends them", files.any { it.first != "DeviceName.kt" && it.second.contains("MarketingNameState") })
    }
}
