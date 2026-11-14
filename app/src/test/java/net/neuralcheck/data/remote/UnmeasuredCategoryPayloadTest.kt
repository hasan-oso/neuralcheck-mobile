package net.neuralcheck.data.remote

import com.google.gson.Gson
import net.neuralcheck.data.remote.model.AiAnalysisResponse
import net.neuralcheck.data.remote.model.AiCategoryScore
import net.neuralcheck.util.CategoryPresentation
import net.neuralcheck.util.CategoryTranslations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wire-level cover for the unmeasured-category contract: the new optional
 * fields must parse when present, stay absent-safe when missing, and the
 * two new categories (audio / input) must resolve to real labels.
 */
class UnmeasuredCategoryPayloadTest {

    private val gson = Gson()

    /** The exact shape the backend now sends for an unmeasured category. */
    private val unmeasuredJson = """
        {"category_scores":{"gps":{
            "grade":"?","score":0,"pass_count":0,"total_count":0,
            "note":"لا اختبارات متاحة لهذه الفئة",
            "not_tested_count":3,"unavailable_count":0,"fail_count":0,
            "warning_count":0,"declared_count":3,"excluded_count":0,
            "measured":false,"grade_source":"unmeasured","ai_grade":"?"}}}
    """.trimIndent()

    @Test
    fun `unmeasured category parses with every new field`() {
        val r = gson.fromJson(unmeasuredJson, AiAnalysisResponse::class.java)
        val gps = r.categoryScores!!.getValue("gps")
        assertEquals("?", gps.grade)
        assertEquals(0, gps.totalCount)
        assertEquals("لا اختبارات متاحة لهذه الفئة", gps.note)
        assertEquals(3, gps.notTestedCount)
        assertEquals(0, gps.unavailableCount)
        assertEquals(3, gps.declaredCount)
        assertEquals(0, gps.excludedCount)
        assertEquals(false, gps.measured)
        assertEquals("unmeasured", gps.gradeSource)
        assertEquals("?", gps.aiGrade)
    }

    @Test
    fun `unmeasured category is recognised and never dropped`() {
        val gps = gson.fromJson(unmeasuredJson, AiAnalysisResponse::class.java)
            .categoryScores!!.getValue("gps")
        assertTrue(CategoryPresentation.isUnmeasured(gps.measured, gps.grade, gps.totalCount))
        // No percentage, so the UI prints words instead of "0%".
        assertNull(CategoryPresentation.passPercent(gps.passCount, gps.totalCount))
        // excluded_count = 0 -> no coverage line: the "not measured"
        // badge already carries the whole message.
        assertNull(CategoryPresentation.notRunCount(
            gps.excludedCount, gps.declaredCount, gps.totalCount))
    }

    @Test
    fun `partially measured gpu surfaces its coverage gap`() {
        val json = """
            {"category_scores":{"gpu":{
                "grade":"A","score":100,"pass_count":2,"total_count":2,
                "note":"","not_tested_count":3,"declared_count":5,
                "excluded_count":3,"measured":true,"expected_range":"Adreno 640: 45-60 fps",
                "reconciled":true}}}
        """.trimIndent()
        val gpu = gson.fromJson(json, AiAnalysisResponse::class.java)
            .categoryScores!!.getValue("gpu")
        assertFalse(CategoryPresentation.isUnmeasured(gpu.measured, gpu.grade, gpu.totalCount))
        // 100% of what ran — but three of five tests never ran, and that
        // gap is what the coverage line exists to show.
        assertEquals(100, CategoryPresentation.passPercent(gpu.passCount, gpu.totalCount))
        assertEquals(3, CategoryPresentation.notRunCount(
            gpu.excludedCount, gpu.declaredCount, gpu.totalCount))
        assertEquals(5, CategoryPresentation.declaredTotal(gpu.declaredCount, gpu.totalCount, 3))
        // Conditional fields present on this one.
        assertEquals("Adreno 640: 45-60 fps", gpu.expectedRange)
        assertEquals(true, gpu.reconciled)
    }

    @Test
    fun `legacy payload without the new fields parses and behaves as before`() {
        val json = """
            {"category_scores":{"battery":{
                "grade":"B","score":78,"pass_count":6,"total_count":8,"note":"fine"}}}
        """.trimIndent()
        val bat = gson.fromJson(json, AiAnalysisResponse::class.java)
            .categoryScores!!.getValue("battery")
        assertNull(bat.measured)
        assertNull(bat.declaredCount)
        assertNull(bat.notTestedCount)
        assertNull(bat.gradeSource)
        assertNull(bat.excludedCount)
        assertNull("absent conditional fields arrive as null, never false", bat.reconciled)
        assertNull(bat.expectedRange)
        assertFalse(CategoryPresentation.isUnmeasured(bat.measured, bat.grade, bat.totalCount))
        assertEquals(75, CategoryPresentation.passPercent(bat.passCount, bat.totalCount))
        assertNull("no excluded/declared -> no coverage line",
            CategoryPresentation.notRunCount(
                bat.excludedCount, bat.declaredCount, bat.totalCount))
    }

    @Test
    fun `camelCase variants are accepted too`() {
        val json = """
            {"category_scores":{"audio":{
                "grade":"?","score":0,"passCount":0,"totalCount":0,
                "notTestedCount":2,"declaredCount":2,"excludedCount":0,
                "measured":false,"gradeSource":"unmeasured"}}}
        """.trimIndent()
        val audio = gson.fromJson(json, AiAnalysisResponse::class.java)
            .categoryScores!!.getValue("audio")
        assertEquals(2, audio.declaredCount)
        assertEquals(0, audio.excludedCount)
        assertEquals(false, audio.measured)
        assertEquals("unmeasured", audio.gradeSource)
    }

    @Test
    fun `the two new categories resolve to real labels not raw keys`() {
        for (key in listOf("audio", "input")) {
            val label = CategoryTranslations.labelFor(key)
            assertNotNull(label)
            assertFalse("$key must not fall back to the raw English key",
                label.ar.equals(key, ignoreCase = true))
            assertTrue("$key needs a Turkish label", label.tr.isNotBlank())
            assertTrue("$key needs an icon", label.icon.isNotBlank())
        }
    }

    @Test
    fun `defaults on the DTO keep an entirely empty object safe`() {
        val cat = gson.fromJson("""{"grade":"?"}""", AiCategoryScore::class.java)
        assertEquals(0, cat.totalCount)
        assertEquals(0, cat.passCount)
        assertNull(cat.measured)
        assertTrue(CategoryPresentation.isUnmeasured(cat.measured, cat.grade, cat.totalCount))
    }
}
