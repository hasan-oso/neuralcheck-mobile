package net.neuralcheck.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * fieldsVersion 69 (S-15, contract section 7-h): the four manual answers.
 *
 * Rule 30: every value of the vocabulary is classified by the reader that the
 * stored string passes through ([TestStatus.fromString]) — not one value from a
 * sample. `unavailable` used to be written as `not_tested`; the test that
 * pinned "the five «غير متوفر» buttons write not_tested" does not exist as a
 * unit test (it was a literal at five call sites), so there is nothing to
 * replace (rule 23) — this class is the first to pin the vocabulary.
 */
class ManualStatusesTest {

    @Test
    fun `the vocabulary is exactly the four answers, in the contract's order`() {
        assertEquals(listOf("pass", "fail", "not_tested", "unavailable"), ManualStatuses.ALL)
        assertEquals(setOf(ManualStatuses.PASS, ManualStatuses.FAIL, ManualStatuses.NOT_TESTED, ManualStatuses.UNAVAILABLE), ManualStatuses.ALL.toSet())
    }

    @Test
    fun `every manual answer is a status TestStatus knows - none falls to ERROR (rule 30)`() {
        for (answer in ManualStatuses.ALL) {
            val parsed = TestStatus.fromString(answer)
            assertNotEquals("\"$answer\" fell to ERROR in TestStatus.fromString", TestStatus.ERROR, parsed)
            assertEquals("\"$answer\" does not round-trip through TestStatus", answer, TestStatus.toDbString(parsed))
        }
    }

    @Test
    fun `unavailable is NOT not_tested - the two answers are distinct on the wire`() {
        assertNotEquals(ManualStatuses.NOT_TESTED, ManualStatuses.UNAVAILABLE)
        assertEquals(TestStatus.UNAVAILABLE, TestStatus.fromString(ManualStatuses.UNAVAILABLE))
        assertEquals(TestStatus.NOT_TESTED, TestStatus.fromString(ManualStatuses.NOT_TESTED))
    }

    @Test
    fun `only pass and fail carry a score - a skipped test or a missing part is ignored, never a 0`() {
        assertEquals(100f, ManualStatuses.scoreOf(ManualStatuses.PASS))
        assertEquals(0f, ManualStatuses.scoreOf(ManualStatuses.FAIL))
        assertNull(ManualStatuses.scoreOf(ManualStatuses.NOT_TESTED))
        assertNull(ManualStatuses.scoreOf(ManualStatuses.UNAVAILABLE))
        assertNull("an unknown string is not scored either", ManualStatuses.scoreOf("warning"))
    }
}
