package net.neuralcheck.domain.model

import net.neuralcheck.util.CategoryTranslations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Order 2026-10-01 (أمر القرارات؛ متابعة المنسّق نفس اليوم): "ومعها اختبار
 * يثبت تطابق الخمس عشرة، بالمسار الذي تقرأ به الشاشة فعلاً." [TestCategory]
 * no longer carries its own copy of nameAr/nameTr at all — [TestCategory
 * .nameAr]/[TestCategory.nameTr] are now plain delegating getters calling
 * [CategoryTranslations] (see that enum's own doc for why this direction),
 * so this test exercises the REAL production getter on each of the 15
 * enum constants — not a hand-typed comparison table — and additionally
 * proves every [TestCategory] id is a key [CategoryTranslations] actually
 * recognizes (the completeness gap a silent typo in either list would
 * otherwise hide: [CategoryTranslations.labelFor] falls back to the raw
 * key for an unrecognized id, which `assertNotEquals` below would catch).
 */
class TestCategoryMatchesCategoryTranslationsTest {

    @Test
    fun `all 15 TestCategory entries read the same Arabic and Turkish names as CategoryTranslations`() {
        assertEquals("sanity check — this enum must have exactly 15 entries", 15, TestCategory.entries.size)
        for (category in TestCategory.entries) {
            assertEquals(
                "nameAr for '${category.id}'",
                CategoryTranslations.nameAr(category.id),
                category.nameAr
            )
            assertEquals(
                "nameTr for '${category.id}'",
                CategoryTranslations.nameTr(category.id),
                category.nameTr
            )
        }
    }

    @Test
    fun `every TestCategory id is a key CategoryTranslations actually recognizes, not its unrecognized-key fallback`() {
        for (category in TestCategory.entries) {
            // labelFor() falls back to the raw key itself (ar == key) only
            // when the key is NOT in its map — this would catch a typo'd id
            // on either side that the equality test above cannot (both
            // sides would "agree" on the same wrong fallback).
            assertFalse(
                "'${category.id}' is not a recognized CategoryTranslations key — " +
                    "labelFor() is silently using its unrecognized-key fallback",
                CategoryTranslations.nameAr(category.id) == category.id
            )
        }
    }

    @Test
    fun `CategoryTranslations has no extra category beyond TestCategory's 15`() {
        // Keeps the two lists from silently growing apart in the other
        // direction — a key added to CategoryTranslations without a
        // matching TestCategory entry would pass the tests above unnoticed.
        // Reads CategoryTranslations' own private `labels` map by
        // reflection rather than a hand-typed id list here — a second
        // typed-out copy of the same 15 keys would itself be exactly the
        // kind of duplicate this order is about.
        val labelsField = CategoryTranslations::class.java.getDeclaredField("labels")
        labelsField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val labels = labelsField.get(CategoryTranslations) as Map<String, *>

        val testCategoryIds = TestCategory.entries.map { it.id }.toSet()
        assertEquals(testCategoryIds, labels.keys)
    }
}
