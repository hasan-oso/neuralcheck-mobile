package net.neuralcheck.domain.manual

import net.neuralcheck.presentation.screen.interactiveManualTests
import net.neuralcheck.presentation.screen.manualTestItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a write does to the raw already stored with a manual row — [ManualChipActions.rawToStore], the one place.
 *
 * User decision 2026-10-21 (after the device round on the APK of f1a38be): «M-09 وM-10 (رفع وخفض الصوت): «يعمل» لا يفتح الحوار، كما M-02. قرار M7(ب)
 * يسري عليهما» (the user's words), and the order's own line that follows from it: «نقرات الشرائح الأربع (E21-3) على M-09/M-10 تكتب جوابها وتُبقي الخامّ المخزَّن
 * إن وُجد كما M-02». This file REPLACES the tests that
 * pinned user approval E22 of 2026-10-15 (the works chip of M-09 and M-10 opens the dialog: `PASS_OPENS_DIALOG` = {M-09, M-10} and the «open the dialog»
 * action, both deleted) and the ones that pinned the raw rule for M-02 alone — rule 23: they are rewritten to the new behaviour, not kept beside tests of it.
 *
 * Proved here: the rule of what a write does to the stored raw over its whole input space and over the whole row set — M-02, M-09 and M-10 keep it on
 * every chip, every other row stores none. That the chips of every row WRITE their answer and open nothing (every row times every answer, the works
 * chip of the volume rows included) is `ManualRowActionsTest`'s; that the real view model applies this rule to the volume rows over a real
 * volume run's raw is `ManualVolumeChipKeepsRawTest`'s; that the row calls the actions and nothing else is pinned beside them (`ManualRowSourceShapeTest`).
 */
class ManualChipActionsTest {

    private val gridRaw = """{"touchedCells":120,"endReason":"auto_complete"}"""
    private val partialRaw = """{"touchedCells":60,"endReason":"user_marked_not_working"}"""
    private val volumeRaw = """{"expectedKeyCode":24,"eventsTotal":2,"endReason":"auto_detected","detectionSource":"key_event"}"""

    private val rawRows = listOf(TouchGridRecorder.TEST_ID, VolumeKeyRecorder.TEST_ID_UP, VolumeKeyRecorder.TEST_ID_DOWN)

    @Test
    fun `the rows that keep a stored raw on a chip tap are the three rows whose answer comes with a raw run - the grid and the two volume rows`() {
        assertEquals(
            "a row added here is a user decision (2026-10-15, E21 item 3: M-02; 2026-10-21: M-09 and M-10)",
            setOf("M-02", "M-09", "M-10"), ManualChipActions.TAP_KEEPS_STORED_RAW
        )
        assertEquals(rawRows.toSet(), ManualChipActions.TAP_KEEPS_STORED_RAW)
        val ids = manualTestItems.map { it.id }
        for (id in rawRows) {
            assertTrue("$id is a row of the manual-tests list", id in ids)
            assertTrue("$id has a dialog that records a raw run", id in interactiveManualTests)
        }
    }

    @Test
    fun `a write that comes with a run's raw stores that raw - on every row, over any stored raw`() {
        for (id in manualTestItems.map { it.id }) for (stored in listOf(null, partialRaw)) {
            assertEquals("$id over $stored", gridRaw, ManualChipActions.rawToStore(id, gridRaw, stored))
        }
    }

    @Test
    fun `a chip tap with no run keeps whatever raw is stored on M-02, M-09 and M-10 - and a stored null stays null, nothing is invented`() {
        for ((id, stored) in listOf("M-02" to gridRaw, "M-09" to volumeRaw, "M-10" to volumeRaw)) {
            assertEquals(id, stored, ManualChipActions.rawToStore(id, null, stored))
            assertEquals(id, partialRaw, ManualChipActions.rawToStore(id, null, partialRaw))
            assertNull(id, ManualChipActions.rawToStore(id, null, null))
        }
    }

    @Test
    fun `no other row keeps a stored raw on a tap - the whole row set`() {
        for (id in manualTestItems.map { it.id }.filter { it !in rawRows }) {
            assertNull(id, ManualChipActions.rawToStore(id, null, gridRaw))
            assertNull(id, ManualChipActions.rawToStore(id, null, volumeRaw))
        }
    }

    @Test
    fun `keepsStoredRaw is true for those three rows only - it is what decides whether a write reads the store`() {
        for (id in manualTestItems.map { it.id }) assertEquals(id, id in rawRows, ManualChipActions.keepsStoredRaw(id))
    }
}
