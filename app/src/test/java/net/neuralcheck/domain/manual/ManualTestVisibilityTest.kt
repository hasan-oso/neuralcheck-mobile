package net.neuralcheck.domain.manual

import android.content.Context
import net.neuralcheck.domain.manual.ManualTestVisibility.HideRule
import net.neuralcheck.presentation.screen.manualTestItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock

/**
 * fieldsVersion 69 (N-17, S-15 (c), contract section 7-h): the hide-by-model
 * mechanism. The production table is EMPTY (which tests are hidden, and on what
 * signal, is not decided — rule 13), so the behaviour is proved with scripted
 * rules, and the empty production table is pinned as the explicit
 * "nothing hidden".
 */
class ManualTestVisibilityTest {

    private val context: Context = mock()

    private fun rule(id: String, lacks: Boolean) = HideRule(id, "SCRIPTED: test") { lacks }

    @Test
    fun `the production table is empty today - nothing is hidden, and hiddenByModel says so`() {
        assertTrue("a row added here needs a user decision per test (contract 7-h)", ManualTestVisibility.RULES.isEmpty())
        assertEquals(emptyList<String>(), ManualTestVisibility.hiddenByModel(context))
    }

    @Test
    fun `a rule hides its test only when the model lacks the component`() {
        val rules = listOf(rule("M-15", lacks = true), rule("M-19", lacks = false), rule("M-14", lacks = true))
        assertEquals(listOf("M-14", "M-15"), ManualTestVisibility.hiddenByModel(context, rules))
    }

    @Test
    fun `the list is ascending and without repeats whatever the order of the rules`() {
        val rules = listOf(rule("M-20", true), rule("M-08", true), rule("M-20", true))
        assertEquals(listOf("M-08", "M-20"), ManualTestVisibility.hiddenByModel(context, rules))
    }

    @Test
    fun `a rule that cannot decide does NOT hide - a test asked in vain costs a tap, a hidden one costs the record`() {
        val boom = HideRule("M-21", "SCRIPTED: throws") { throw IllegalStateException("feature table unreadable") }
        assertEquals(listOf("M-15"), ManualTestVisibility.hiddenByModel(context, listOf(boom, rule("M-15", true))))
    }

    @Test
    fun `visible() drops the hidden tests and keeps the order of the rest`() {
        val items = listOf("M-01", "M-02", "M-14", "M-15", "M-19")
        assertEquals(listOf("M-01", "M-02", "M-19"), ManualTestVisibility.visible(items, listOf("M-14", "M-15")) { it })
    }

    @Test
    fun `idsToAnswer leaves the hidden tests out - commitAndContinue would otherwise fill them with not_tested`() {
        val ids = listOf("M-01", "M-02", "M-14", "M-15")
        assertEquals(listOf("M-01", "M-02"), ManualTestVisibility.idsToAnswer(ids, listOf("M-14", "M-15")))
        assertEquals("nothing hidden: every id is answered", ids, ManualTestVisibility.idsToAnswer(ids, emptyList()))
    }

    @Test
    fun `every production rule names a test that exists and says why it is safe`() {
        val known = manualTestItems.map { it.id }.toSet()
        for (r in ManualTestVisibility.RULES) {
            assertTrue("${r.testId} is not a manual test the screen shows", r.testId in known)
            assertTrue("${r.testId}: a rule without a written reason is removed (rule 22 supplement)", r.reason.isNotBlank())
        }
    }
}
