package net.neuralcheck.presentation.screen

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.data.remote.model.AiCategoryV7
import net.neuralcheck.util.AiV7Presentation
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStreamReader

/**
 * Order 2026-10-01 (أمر القرارات، A5): "فئات الصوت واللمس ATTESTED_ONLY في
 * NC-5DYV7N وNC-83XL4C وNC-TM7PDN، والكاميرا في NC-5DYV7N. فلا يلزم انتظار
 * عيّنة ذهبية." This test proves the surface tag's actual gate —
 * `stateKind == AiV7Presentation.CategoryStateKind.ATTESTED_ONLY`, the exact
 * condition [AiCategoryRowV7] in ReportScreen.kt (the composable itself,
 * not called directly here - no test renders it; Robolectric is a test dependency since 3284cca) uses to
 * decide whether to render `report_surface_category_tag` — against REAL
 * `categories` fields copied from three live device exports, not a
 * hand-built golden sample (none of the three existing fixtures under
 * `app/src/test/resources/contract` carries an ATTESTED_ONLY category, and
 * this test does not touch or copy those files — rule: "ولا نسخ العيّنات
 * الذهبية").
 *
 * The fixture is DERIVED, not a copy: `_meta` names each source export's
 * absolute path (outside this repo, read-only) and the exact field path
 * extracted (`document.aiResponse.categories`) — only `categories` is kept,
 * no raw device/fingerprint/telemetry data. See
 * `app/src/test/resources/derived/v7-categories-from-device-exports-20261001.json`.
 *
 * Per-device expectations, independently confirmed by the coordinator
 * against the real export files before this order was sent, and reconfirmed
 * here by this test reading the same fixture:
 *   NC-5DYV7N: camera, audio, input — all ATTESTED_ONLY (tag shows on all three).
 *   NC-83XL4C: audio, input — ATTESTED_ONLY (tag shows); camera — MEASURED
 *     (tag must NOT show — the exact negative case the order asks for: "ولا
 *     يظهر على الفئة المقيسة في التقرير نفسه (camera في NC-83XL4C)").
 *   NC-TM7PDN: same shape as NC-83XL4C.
 */
class AttestedOnlySurfaceTagDerivedTest {

    private data class DerivedFixture(
        val categoriesByDevice: Map<String, Map<String, AiCategoryV7>>
    )

    private fun loadFixture(): DerivedFixture {
        val stream = javaClass.classLoader!!
            .getResourceAsStream("derived/v7-categories-from-device-exports-20261001.json")
            ?: error("Fixture not found on classpath — expected at app/src/test/resources/derived/v7-categories-from-device-exports-20261001.json")
        val text = InputStreamReader(stream, Charsets.UTF_8).readText()
        val raw = Gson().fromJson<Map<String, Any?>>(
            text,
            object : TypeToken<Map<String, Any?>>() {}.type
        )
        val gson = Gson()
        val byDevice = raw.filterKeys { it != "_meta" }.mapValues { (_, catsRaw) ->
            val catsJson = gson.toJson(catsRaw)
            val type = object : TypeToken<Map<String, AiCategoryV7>>() {}.type
            gson.fromJson<Map<String, AiCategoryV7>>(catsJson, type)
        }
        return DerivedFixture(byDevice)
    }

    /** Exactly [AiCategoryRowV7]'s own gate (ReportScreen.kt) — the function
     *  under test here is the real production decision, not a re-implementation. */
    private fun surfaceTagShows(cat: AiCategoryV7): Boolean =
        AiV7Presentation.classifyState(cat.state) == AiV7Presentation.CategoryStateKind.ATTESTED_ONLY

    @Test
    fun `NC-5DYV7N camera audio input are all ATTESTED_ONLY and show the tag`() {
        val cats = loadFixture().categoriesByDevice.getValue("NC-5DYV7N")
        assertEquals(true, surfaceTagShows(cats.getValue("camera")))
        assertEquals(true, surfaceTagShows(cats.getValue("audio")))
        assertEquals(true, surfaceTagShows(cats.getValue("input")))
    }

    @Test
    fun `NC-83XL4C audio and input show the tag, but camera (MEASURED) does not`() {
        val cats = loadFixture().categoriesByDevice.getValue("NC-83XL4C")
        assertEquals(true, surfaceTagShows(cats.getValue("audio")))
        assertEquals(true, surfaceTagShows(cats.getValue("input")))
        assertEquals("camera state in fixture", "MEASURED", cats.getValue("camera").state)
        assertEquals(false, surfaceTagShows(cats.getValue("camera")))
    }

    @Test
    fun `NC-TM7PDN audio and input show the tag, but camera (MEASURED) does not`() {
        val cats = loadFixture().categoriesByDevice.getValue("NC-TM7PDN")
        assertEquals(true, surfaceTagShows(cats.getValue("audio")))
        assertEquals(true, surfaceTagShows(cats.getValue("input")))
        assertEquals("camera state in fixture", "MEASURED", cats.getValue("camera").state)
        assertEquals(false, surfaceTagShows(cats.getValue("camera")))
    }
}
