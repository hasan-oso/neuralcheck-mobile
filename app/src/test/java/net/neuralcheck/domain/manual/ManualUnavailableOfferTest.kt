package net.neuralcheck.domain.manual

import net.neuralcheck.presentation.screen.interactiveManualTests
import net.neuralcheck.presentation.screen.manualTestItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Batch 1.0.12, second part (user decision 2026-10-13), narrowed by the user's decision of 2026-10-15 (station-2 build): the rows that offer
 * the answer `unavailable` (text: the one string `manual_mark_unavailable`) are ONE list, [ManualUnavailableOffer.TEST_IDS] — M-08, M-14, M-15. M-12 left it.
 *
 * Mutation guards for the list: a row added fails `the list is exactly the three` and the matrix; a row removed fails them too.
 * That the line under the chips and the dialogs' button read THIS list is `ManualRowSourceShapeTest` and
 * `ManualTestDialogsUnavailableShapeTest`.
 */
class ManualUnavailableOfferTest {

    @Test
    fun `the list is exactly the three rows the user named - one more or one fewer fails`() {
        assertEquals(listOf("M-08", "M-14", "M-15"), ManualUnavailableOffer.TEST_IDS)
        assertEquals("no repeats", ManualUnavailableOffer.TEST_IDS.size, ManualUnavailableOffer.TEST_IDS.toSet().size)
    }

    @Test
    fun `isOffered is true for those three and false for every other row of the manual list - M-12 included - the whole row set`() {
        val ids = manualTestItems.map { it.id }
        assertTrue("the row set is the real one", ids.size >= 15)
        assertTrue("M-12 is still a row of the list and still has its dialog - it left the offer, not the catalog", "M-12" in ids && "M-12" in interactiveManualTests)
        for (id in ids) {
            assertEquals("$id", id in setOf("M-08", "M-14", "M-15"), ManualUnavailableOffer.isOffered(id))
        }
        assertFalse("M-12 is not offered", ManualUnavailableOffer.isOffered("M-12"))
    }

    @Test
    fun `each offered row is a row of the manual list and has a dialog - the dialog is where the button was`() {
        val ids = manualTestItems.map { it.id }
        for (id in ManualUnavailableOffer.TEST_IDS) {
            assertTrue("$id is a row of the manual-tests list", id in ids)
            assertTrue("$id has an interactive dialog (its button lives there)", id in interactiveManualTests)
        }
    }

    @Test
    fun `the retired OTG and IR rows are not offered - their dialogs are dead code`() {
        assertFalse(ManualUnavailableOffer.isOffered("M-13"))
        assertFalse(ManualUnavailableOffer.isOffered("M-16"))
    }

    @Test
    fun `an id that is not a row is not offered - blank, other case, a neighbour`() {
        for (bad in listOf("", "m-08", "M-8", "M-08 ", "M-09", "M-11", "M-13", "M-16", "M-17")) {
            assertFalse("`$bad`", ManualUnavailableOffer.isOffered(bad))
        }
    }
}
