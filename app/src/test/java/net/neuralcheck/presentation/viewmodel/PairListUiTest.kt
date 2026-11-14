package net.neuralcheck.presentation.viewmodel

import net.neuralcheck.data.remote.PairLinkClient.ConfirmOutcome
import net.neuralcheck.data.remote.PairLinkClient.ListOutcome
import net.neuralcheck.domain.pair.DeviceNameRead
import net.neuralcheck.domain.pair.OpenBeforesRead
import net.neuralcheck.domain.pair.PairCandidate
import net.neuralcheck.domain.pair.PairFailureKind
import net.neuralcheck.domain.pair.PairRefusal
import net.neuralcheck.domain.pair.failureKindOf
import net.neuralcheck.domain.repeat.TextRead
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The «after maintenance» pair list as a state - order of 2026-10-22. Pure values, no Android: what each end of the list's call makes of the list, when the list is on the screen, when a tap is applied, and what each
 * end of the confirmation leaves. Rule 30: every [Phase], every state of the list, every refusal of the contract and every outcome of each call is run, and what the state MAKES of each is asserted, not that it arrived.
 */
class PairListUiTest {

    private fun candidate(id: String, at: Long = 1_760_000_000_000L) =
        PairCandidate(id, DeviceNameRead.Unmapped(TextRead.Text("MODEL-$id"), TextRead.Text("MODEL_NOT_IN_MAP")), at)

    private val a = candidate("a", 1_000)
    private val b = candidate("b", 2_000)
    private val listing = PairListing(72, listOf(a, b))
    private val choosing = PairListUi.Choosing(listing, selected = null, confirm = PairConfirmUi.Idle)

    private fun listed(vararg candidates: PairCandidate, hours: Int = 72) =
        ListOutcome.Listed(OpenBeforesRead.Read(hours, candidates.toList(), emptyList(), emptyList()))

    private val everyState: List<PairListUi> = listOf(
        PairListUi.NotShown,
        PairListUi.Loading,
        PairListUi.LoadFailed(PairFailure(PairFailureKind.GENERAL, "not_sent")),
        PairListUi.Empty(72),
        choosing,
        choosing.copy(selected = "a"),
        choosing.copy(selected = "a", confirm = PairConfirmUi.InFlight("a")),
        choosing.copy(selected = "a", confirm = PairConfirmUi.Failed(PairFailure(PairFailureKind.EXPIRED, "BEFORE_EXPIRED"))),
        PairListUi.Linked(listing, a)
    )

    // ---- what the list's call makes of the list ----

    @Test
    fun `each outcome of the list's call is one state - a closed gate draws nothing, a list that reads is a list or the none-open state, anything else is a failure with its text`() {
        assertEquals(PairListUi.NotShown, pairListFor(ListOutcome.GuardClosed))
        assertEquals("candidates are the choosing state with no choice", choosing, pairListFor(listed(a, b)))
        assertEquals("no candidate is the none-open state, with the hours the answer carried", PairListUi.Empty(24), pairListFor(listed(hours = 24)))
        assertEquals(PairListUi.LoadFailed(PairFailure(PairFailureKind.GENERAL, "unreadable_candidates_absent")), pairListFor(ListOutcome.UnreadableAnswer("candidates_absent")))
        assertEquals(PairListUi.LoadFailed(PairFailure(PairFailureKind.GENERAL, "not_sent")), pairListFor(ListOutcome.NotSent))
    }

    @Test
    fun `every refusal of the list is shown with the text of its own reasonCode - the failure kind and the code both reach the state, for all seventeen`() {
        for (refusal in PairRefusal.values()) {
            val state = pairListFor(ListOutcome.Refused(refusal.wire, refusal))
            assertEquals(refusal.wire, PairListUi.LoadFailed(PairFailure(failureKindOf(refusal), refusal.wire)), state)
        }
        // a code the app has no case for, or the session contract's, is the general text with the code kept for the log
        for (code in listOf("SESSION_EXPIRED", "SESSION_NOT_PARTICIPANT", "PAIR_BRAND_NEW")) {
            assertEquals(code, PairListUi.LoadFailed(PairFailure(PairFailureKind.GENERAL, code)), pairListFor(ListOutcome.Refused(code, null)))
        }
        // the two refusals a shop without the feature meets are the one text that says so
        assertEquals(PairFailureKind.NOT_ENABLED, (pairListFor(ListOutcome.Refused("PAIR_PART_NOT_ENABLED", PairRefusal.PART_NOT_ENABLED)) as PairListUi.LoadFailed).failure.kind)
        assertEquals(PairFailureKind.NOT_ENABLED, (pairListFor(ListOutcome.Refused("PAIR_VALIDITY_SETTING_ABSENT", PairRefusal.VALIDITY_SETTING_ABSENT)) as PairListUi.LoadFailed).failure.kind)
    }

    @Test
    fun `the confirmation's outcomes - only a refusal, an unreadable answer or a failure is a failure - a confirmation and a closed gate are not`() {
        assertEquals(null, pairConfirmFailureFor(ConfirmOutcome.Confirmed))
        assertEquals(null, pairConfirmFailureFor(ConfirmOutcome.GuardClosed))
        assertEquals(PairFailure(PairFailureKind.GENERAL, "not_sent"), pairConfirmFailureFor(ConfirmOutcome.NotSent))
        assertEquals(PairFailure(PairFailureKind.GENERAL, "unreadable_link_method_absent"), pairConfirmFailureFor(ConfirmOutcome.UnreadableAnswer("link_method_absent")))
        val expected = mapOf(
            PairRefusal.BEFORE_NOT_FOUND to PairFailureKind.NOT_AVAILABLE, PairRefusal.BEFORE_NOT_OWNED to PairFailureKind.NOT_AVAILABLE, PairRefusal.BEFORE_WRONG_PURPOSE to PairFailureKind.NOT_AVAILABLE,
            PairRefusal.BEFORE_ALREADY_PAIRED to PairFailureKind.ALREADY_PAIRED, PairRefusal.BEFORE_EXPIRED to PairFailureKind.EXPIRED, PairRefusal.BEFORE_WRONG_DEFECT_TYPE to PairFailureKind.WRONG_DEFECT_TYPE,
            PairRefusal.OWNER_MISMATCH to PairFailureKind.NOT_AVAILABLE
        )
        for ((refusal, kind) in expected) assertEquals(refusal.wire, PairFailure(kind, refusal.wire), pairConfirmFailureFor(ConfirmOutcome.Refused(refusal.wire, refusal)))
        assertEquals(PairFailure(PairFailureKind.GENERAL, "SESSION_EXPIRED"), pairConfirmFailureFor(ConfirmOutcome.Refused("SESSION_EXPIRED", null)))
    }

    // ---- when the list is on the screen ----

    @Test
    fun `the list is on the screen while the session is pending or scanning and never otherwise - for every phase and every state, and not-shown draws nothing in any phase`() {
        for (phase in Phase.values()) {
            for (state in everyState) {
                val expected = state != PairListUi.NotShown && (phase == Phase.WAITING || phase == Phase.SCANNING)
                assertEquals("$phase / $state", expected, pairListVisible(phase, state))
            }
        }
    }

    // ---- when a tap on a candidate is applied ----

    @Test
    fun `a candidate is selected only while the list is on the screen, with the id one of the listed, and no confirmation out - choosing again after a link included`() {
        for (phase in Phase.values()) {
            val open = phase == Phase.WAITING || phase == Phase.SCANNING
            assertEquals("$phase", if (open) PairSelectTap.Select("a") else PairSelectTap.Ignore("not_visible"), pairSelectDecision(choosing, phase, true, "a"))
            assertEquals("$phase linked", if (open) PairSelectTap.Select("b") else PairSelectTap.Ignore("not_visible"), pairSelectDecision(PairListUi.Linked(listing, a), phase, true, "b"))
        }
        assertEquals(PairSelectTap.Ignore("no_session"), pairSelectDecision(choosing, Phase.WAITING, false, "a"))
        assertEquals(PairSelectTap.Ignore("unknown_candidate"), pairSelectDecision(choosing, Phase.WAITING, true, "zzz"))
        assertEquals(PairSelectTap.Ignore("unknown_candidate"), pairSelectDecision(PairListUi.Linked(listing, a), Phase.WAITING, true, "zzz"))
        assertEquals(PairSelectTap.Ignore("in_flight"), pairSelectDecision(choosing.copy(selected = "a", confirm = PairConfirmUi.InFlight("a")), Phase.WAITING, true, "b"))
        for (state in listOf(PairListUi.Loading, PairListUi.Empty(72), PairListUi.LoadFailed(PairFailure(PairFailureKind.GENERAL, "x")))) {
            assertEquals("$state", PairSelectTap.Ignore("not_choosing"), pairSelectDecision(state, Phase.WAITING, true, "a"))
        }
        assertEquals(PairSelectTap.Ignore("not_visible"), pairSelectDecision(PairListUi.NotShown, Phase.WAITING, true, "a"))
        // a refused confirmation does not lock the choice: another candidate can be chosen
        assertEquals(PairSelectTap.Select("b"), pairSelectDecision(choosing.copy(selected = "a", confirm = PairConfirmUi.Failed(PairFailure(PairFailureKind.EXPIRED, "BEFORE_EXPIRED"))), Phase.WAITING, true, "b"))
    }

    // ---- when «تأكيد» sends ----

    @Test
    fun `the confirmation is sent only for a selected candidate, on a list that is on the screen, with no call out - every phase, every state`() {
        for (phase in Phase.values()) {
            val open = phase == Phase.WAITING || phase == Phase.SCANNING
            assertEquals("$phase", if (open) PairConfirmTap.Confirm("a") else PairConfirmTap.Ignore("not_visible"), pairConfirmDecision(choosing.copy(selected = "a"), phase, true))
        }
        assertEquals(PairConfirmTap.Ignore("no_session"), pairConfirmDecision(choosing.copy(selected = "a"), Phase.WAITING, false))
        assertEquals("a button with no choice does nothing", PairConfirmTap.Ignore("nothing_selected"), pairConfirmDecision(choosing, Phase.WAITING, true))
        assertEquals(PairConfirmTap.Ignore("in_flight"), pairConfirmDecision(choosing.copy(selected = "a", confirm = PairConfirmUi.InFlight("a")), Phase.WAITING, true))
        // a refused confirmation can be tried again for the same choice
        assertEquals(
            PairConfirmTap.Confirm("a"),
            pairConfirmDecision(choosing.copy(selected = "a", confirm = PairConfirmUi.Failed(PairFailure(PairFailureKind.GENERAL, "not_sent"))), Phase.WAITING, true)
        )
        for (state in listOf(PairListUi.Loading, PairListUi.Empty(72), PairListUi.LoadFailed(PairFailure(PairFailureKind.GENERAL, "x")), PairListUi.Linked(listing, a))) {
            assertEquals("$state", PairConfirmTap.Ignore("not_choosing"), pairConfirmDecision(state, Phase.WAITING, true))
        }
        assertEquals(PairConfirmTap.Ignore("not_visible"), pairConfirmDecision(PairListUi.NotShown, Phase.WAITING, true))
    }

    // ---- what each step leaves ----

    @Test
    fun `selecting moves the choice and clears a refusal of the earlier confirmation - a linked list goes back to choosing with the new candidate - other states are untouched`() {
        val refused = choosing.copy(selected = "a", confirm = PairConfirmUi.Failed(PairFailure(PairFailureKind.ALREADY_PAIRED, "BEFORE_ALREADY_PAIRED")))
        assertEquals(choosing.copy(selected = "b", confirm = PairConfirmUi.Idle), refused.selected("b"))
        assertEquals(choosing.copy(selected = "a"), choosing.selected("a"))
        assertEquals("linked: choosing again", PairListUi.Choosing(listing, "b", PairConfirmUi.Idle), PairListUi.Linked(listing, a).selected("b"))
        for (state in listOf(PairListUi.NotShown, PairListUi.Loading, PairListUi.Empty(72), PairListUi.LoadFailed(PairFailure(PairFailureKind.GENERAL, "x")))) assertEquals(state, state.selected("a"))
    }

    @Test
    fun `a confirmation in flight keeps the choice, a confirmed one links the candidate chosen, a refused one keeps the choice and shows its failure, a closed gate puts it back to idle`() {
        val selected = choosing.copy(selected = "b")
        val inFlight = selected.confirmStarted("b") as PairListUi.Choosing
        assertEquals(PairConfirmUi.InFlight("b"), inFlight.confirm)
        assertEquals("b", inFlight.selected)
        assertEquals(PairListUi.Linked(listing, b), inFlight.confirmed("b"))
        val failure = PairFailure(PairFailureKind.EXPIRED, "BEFORE_EXPIRED")
        val failed = inFlight.confirmFailed(failure) as PairListUi.Choosing
        assertEquals(PairConfirmUi.Failed(failure), failed.confirm)
        assertEquals("the choice stays", "b", failed.selected)
        assertEquals(selected, inFlight.confirmNotMade())
        // the linked candidate is the one chosen, the listing stays whole
        assertEquals(listing, (inFlight.confirmed("b") as PairListUi.Linked).listing)
        assertEquals("an id the list does not hold links nothing", inFlight, inFlight.confirmed("zzz"))
        for (state in listOf(PairListUi.NotShown, PairListUi.Loading, PairListUi.Empty(72), PairListUi.Linked(listing, a))) {
            assertEquals(state, state.confirmStarted("a"))
            assertEquals(state, state.confirmFailed(failure))
            assertEquals(state, state.confirmed("a"))
            assertEquals(state, state.confirmNotMade())
        }
    }

    @Test
    fun `choosing, linking, choosing again and linking again - the second link replaces the first and the listing never changes`() {
        var ui: PairListUi = pairListFor(listed(a, b))
        ui = ui.selected("a").confirmStarted("a").confirmed("a")
        assertEquals(PairListUi.Linked(listing, a), ui)
        ui = ui.selected("b")
        assertEquals(PairListUi.Choosing(listing, "b", PairConfirmUi.Idle), ui)
        ui = ui.confirmStarted("b").confirmed("b")
        assertEquals(PairListUi.Linked(listing, b), ui)
        assertNotEquals(PairListUi.Linked(listing, a), ui)
        assertEquals(listing, (ui as PairListUi.Linked).listing)
    }
}
