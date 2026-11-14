package net.neuralcheck.presentation.viewmodel

import com.google.firebase.functions.FirebaseFunctionsException
import com.google.gson.JsonParser
import net.neuralcheck.data.remote.DefectType
import net.neuralcheck.data.remote.PartRead
import net.neuralcheck.data.remote.PurposeRequestRejection
import net.neuralcheck.data.remote.ProtocolPart
import net.neuralcheck.data.remote.ProtocolPartsRead
import net.neuralcheck.data.remote.ScanPurposeChoice
import net.neuralcheck.data.remote.SessionErrorCode
import net.neuralcheck.presentation.viewmodel.GenerateSessionViewModel.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The purpose chips as a state - A-P6-03. Pure values, no Android: what the create's answer makes of the shop's parts, when a tap is sent, what each end of a call leaves, and how a failure is
 * classified. Rule 30: every value of every enumeration that reaches this state is run - every [Phase], every kind of `purposes` part, every [ScanPurposeChoice], every
 * [SessionErrorCode] a refusal can carry - and what the state MAKES of each is asserted, not that it arrived.
 */
class SessionPurposeUiTest {

    private fun parts(text: String) = ProtocolPartsRead.of(JsonParser.parseString(text))

    /** The selection a chip of [choice] makes once its fault type is chosen: [DefectType.BATTERY] for a maintenance choice (the type is not what these tests are about), none for «normal». */
    private fun selectionOf(choice: ScanPurposeChoice, type: DefectType = DefectType.BATTERY): PurposeSelection =
        if (choice.needsDefectType) PurposeSelection.Maintenance(choice, type) else PurposeSelection.Normal

    private fun refusal(details: Any?): FirebaseFunctionsException {
        val e = mock<FirebaseFunctionsException>()
        whenever(e.details).thenReturn(details)
        return e
    }

    // ---- the shop's part decides whether the chips exist ----

    @Test
    fun `the chips exist for purposes read as enabled and for nothing else - every reading of the key and of the part`() {
        assertEquals(PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PARTS_ABSENT), purposeUiFor(ProtocolPartsRead.Absent))
        assertEquals(PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PARTS_NOT_AN_OBJECT), purposeUiFor(parts("[]")))
        assertEquals(PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PARTS_NOT_AN_OBJECT), purposeUiFor(parts("\"purposes\"")))
        assertEquals("purposes alone: the chips, and «normal» only (Q31)", PurposeUi.Offered(maintenanceOffered = false), purposeUiFor(parts("""{"purposes":true}""")))
        assertEquals(PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PURPOSES_DISABLED), purposeUiFor(parts("""{"purposes":false}""")))
        assertEquals("not listed is the contract's off, apart from false", PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PURPOSES_NOT_LISTED), purposeUiFor(parts("""{"consent":true}""")))
        assertEquals(PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PURPOSES_NOT_LISTED), purposeUiFor(parts("{}")))
        for (value in listOf("\"true\"", "1", "{}", "[]", "null")) {
            assertEquals("purposes=$value is declared, not read as on", PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PURPOSES_UNRECOGNIZED), purposeUiFor(parts("""{"purposes":$value}""")))
        }
        // another part on does not offer the chips
        for (other in ProtocolPart.values().filter { it != ProtocolPart.PURPOSES }) {
            assertFalse(other.wire, purposeUiFor(parts("""{"${other.wire}":true}""")) is PurposeUi.Offered)
        }
        // a hand-built map without the key reads as not listed, the same as the reader makes of an object without it
        assertEquals(
            PurposeUi.NotOffered(PurposeUi.NotOffered.Why.PURPOSES_NOT_LISTED),
            purposeUiFor(ProtocolPartsRead.Read(emptyMap(), emptyList()))
        )
        assertEquals("every Why is reachable", PurposeUi.NotOffered.Why.values().size, 5)
    }

    @Test
    fun `every PartRead of purposes has a place - the state names it, none is lost`() {
        val reads = listOf(PartRead.Enabled, PartRead.Disabled, PartRead.NotListed, PartRead.Unrecognized("x"))
        val results = reads.map { purposeUiFor(ProtocolPartsRead.Read(mapOf(ProtocolPart.PURPOSES to it), emptyList())) }
        assertEquals(4, results.toSet().size)
    }

    // ---- Q31 (2026-10-22): the maintenance chips and the type list need BOTH parts; «normal» needs purposes alone ----

    private val offeredBoth = PurposeUi.Offered(maintenanceOffered = true)

    @Test
    fun `the maintenance chips exist for purposes AND batteryBeforeAfter both enabled - all 25 pairs of readings of the two parts decide it`() {
        val reads: List<PartRead?> = listOf(PartRead.Enabled, PartRead.Disabled, PartRead.NotListed, PartRead.Unrecognized("x"), null) // null = the key is not in the map
        var bothOn = 0
        var purposesAlone = 0
        for (purposes in reads) {
            for (battery in reads) {
                val map = buildMap<ProtocolPart, PartRead> {
                    if (purposes != null) put(ProtocolPart.PURPOSES, purposes)
                    if (battery != null) put(ProtocolPart.BATTERY_BEFORE_AFTER, battery)
                }
                val read = ProtocolPartsRead.Read(map, emptyList())
                val label = "purposes=$purposes battery=$battery"
                val ui = purposeUiFor(read)
                if (purposes == PartRead.Enabled) {
                    val offered = ui as PurposeUi.Offered
                    assertEquals(label, battery == PartRead.Enabled, offered.maintenanceOffered)
                    assertEquals(label, battery == PartRead.Enabled, read.maintenanceOffered())
                    assertEquals(label, if (battery == PartRead.Enabled) ScanPurposeChoice.values().toList() else listOf(ScanPurposeChoice.NORMAL), offered.choices())
                    if (battery == PartRead.Enabled) bothOn++ else purposesAlone++
                } else {
                    assertTrue(label, ui is PurposeUi.NotOffered)
                    assertFalse(label, read.maintenanceOffered())
                }
            }
        }
        assertEquals("one pair of the 25 has both on", 1, bothOn)
        assertEquals("four pairs have purposes on and the other part not", 4, purposesAlone)
    }

    @Test
    fun `batteryBeforeAfter alone offers nothing - not even normal - and the other shapes of the key offer nothing`() {
        assertFalse(parts("""{"batteryBeforeAfter":true}""").maintenanceOffered())
        assertTrue(purposeUiFor(parts("""{"batteryBeforeAfter":true}""")) is PurposeUi.NotOffered)
        assertFalse(ProtocolPartsRead.Absent.maintenanceOffered())
        assertFalse(parts("[]").maintenanceOffered())
        assertTrue(parts("""{"purposes":true,"batteryBeforeAfter":true}""").maintenanceOffered())
        assertEquals(PurposeUi.Offered(maintenanceOffered = true), purposeUiFor(parts("""{"purposes":true,"batteryBeforeAfter":true}""")))
        for (value in listOf("\"true\"", "1", "{}", "[]", "null", "false")) {
            assertFalse("batteryBeforeAfter=$value is not read as on", parts("""{"purposes":true,"batteryBeforeAfter":$value}""").maintenanceOffered())
        }
    }

    @Test
    fun `for a shop with purposes alone a maintenance chip tap and a type tap go nowhere - with the named reason - and normal is still sent`() {
        val alone = PurposeUi.Offered()
        for (choice in maintenanceChoices) {
            assertEquals(choice.wire, PurposeChipTap.Ignore("maintenance_not_offered"), purposeChipTapDecision(alone, Phase.WAITING, hasSession = true, choice))
            // the base reasons come first: a shop that has no maintenance chip is told the phase is wrong before it is told that
            assertEquals(PurposeChipTap.Ignore("not_pending"), purposeChipTapDecision(alone, Phase.SCANNING, hasSession = true, choice))
            assertEquals(PurposeChipTap.Ignore("no_session"), purposeChipTapDecision(alone, Phase.WAITING, hasSession = false, choice))
        }
        assertEquals(PurposeChipTap.SendNormal, purposeChipTapDecision(alone, Phase.WAITING, hasSession = true, ScanPurposeChoice.NORMAL))
        for (type in DefectType.values()) {
            assertEquals(type.wire, PurposeTypeTap.Ignore("maintenance_not_offered"), purposeTypeTapDecision(alone, Phase.WAITING, hasSession = true, type))
        }
    }

    @Test
    fun `a state for a shop with purposes alone cannot ask for a type - and shows no type list even if a maintenance purpose is in it`() {
        val alone = PurposeUi.Offered()
        for (choice in maintenanceChoices) {
            val failure = runCatching { alone.typeRequested(choice) }.exceptionOrNull()
            assertTrue("${choice.wire}: $failure", failure is IllegalArgumentException)
            assertEquals("a hand-built accepted maintenance purpose is not enough: the shop does not have the list", null, alone.accepted(selectionOf(choice)).typeListFor())
        }
        assertEquals(listOf(ScanPurposeChoice.NORMAL), alone.choices())
        assertEquals(ScanPurposeChoice.values().toList(), offeredBoth.choices())
    }

    // ---- what is highlighted ----

    @Test
    fun `before any tap the highlighted chip is normal - the contract's creation default, a state apart from a chosen normal`() {
        val atCreation = PurposeUi.Offered()
        assertEquals(PurposeConfirmed.AtCreation, atCreation.confirmed)
        assertEquals(ScanPurposeChoice.NORMAL, atCreation.highlighted())
        val chosenNormal = PurposeUi.Offered().accepted(PurposeSelection.Normal)
        assertEquals(ScanPurposeChoice.NORMAL, chosenNormal.highlighted())
        assertTrue("the same chip, two different states", atCreation != chosenNormal)
    }

    @Test
    fun `every choice highlights itself once the server accepted it, and a choice still in flight does not`() {
        for (choice in ScanPurposeChoice.values()) {
            assertEquals(choice.wire, choice, PurposeUi.Offered().accepted(selectionOf(choice)).highlighted())
            val inFlight = PurposeUi.Offered().started(selectionOf(choice))
            assertEquals("in flight: not yet highlighted", ScanPurposeChoice.NORMAL, inFlight.highlighted())
            assertEquals(selectionOf(choice), inFlight.inFlight)
        }
    }

    // ---- a selection is a purpose with its fault type, or nothing (contract A2 addendum of 2026-10-22) ----

    @Test
    fun `a selection exists for normal alone and for a maintenance purpose with a type - every combination of the 3 x 6 grid says which`() {
        for (choice in ScanPurposeChoice.values()) {
            for (type in DefectType.values().toList() + null) {
                val built = PurposeSelection.of(choice, type)
                if (choice.needsDefectType == (type != null)) {
                    val selection = (built as PurposeSelectionBuild.Built).selection
                    assertEquals("${choice.wire}/${type?.wire}", choice, selection.choice)
                    assertEquals("${choice.wire}/${type?.wire}", type, selection.defectType)
                } else {
                    val expected = if (type == null) PurposeRequestRejection.DEFECT_TYPE_MISSING else PurposeRequestRejection.DEFECT_TYPE_NOT_APPLICABLE
                    assertEquals("${choice.wire}/${type?.wire}", PurposeSelectionBuild.Rejected(expected), built)
                }
            }
        }
    }

    @Test
    fun `a maintenance selection cannot be built without a fault type - nor a normal one with it - even by hand`() {
        for (choice in ScanPurposeChoice.values().filter { !it.needsDefectType }) {
            val failure = runCatching { PurposeSelection.Maintenance(choice, DefectType.BATTERY) }.exceptionOrNull()
            assertTrue("${choice.wire}: $failure", failure is IllegalArgumentException)
        }
        assertEquals(null, PurposeSelection.Normal.defectType)
        assertEquals(ScanPurposeChoice.NORMAL, PurposeSelection.Normal.choice)
    }

    // ---- when a tap is sent ----

    @Test
    fun `a tap is sent only for offered chips on a session this screen holds, still pending, not locked, with no call out - every phase, every state`() {
        val offered = PurposeUi.Offered()
        for (phase in Phase.values()) {
            val expected = if (phase == Phase.WAITING) PurposeTap.Send else PurposeTap.Ignore("not_pending")
            assertEquals("phase $phase", expected, purposeTapDecision(offered, phase, hasSession = true))
        }
        assertEquals(PurposeTap.Ignore("no_session"), purposeTapDecision(offered, Phase.WAITING, hasSession = false))
        assertEquals(PurposeTap.Ignore("not_offered"), purposeTapDecision(PurposeUi.NotRead, Phase.WAITING, hasSession = true))
        for (why in PurposeUi.NotOffered.Why.values()) {
            assertEquals(why.token, PurposeTap.Ignore("not_offered"), purposeTapDecision(PurposeUi.NotOffered(why), Phase.WAITING, hasSession = true))
        }
        assertEquals(PurposeTap.Ignore("locked"), purposeTapDecision(offered.copy(failure = PurposeFailure.NotPending), Phase.WAITING, hasSession = true))
        assertEquals(PurposeTap.Ignore("in_flight"), purposeTapDecision(offered.started(selectionOf(ScanPurposeChoice.MAINTENANCE_BEFORE)), Phase.WAITING, hasSession = true))
        // a refusal that is not the lock and a transport failure leave the chips usable
        assertEquals(PurposeTap.Send, purposeTapDecision(offered.copy(failure = PurposeFailure.Failed), Phase.WAITING, hasSession = true))
        assertEquals(PurposeTap.Send, purposeTapDecision(offered.copy(failure = PurposeFailure.Refused("X")), Phase.WAITING, hasSession = true))
    }

    @Test
    fun `the chips are locked in every phase but waiting, and for good after the not-pending refusal`() {
        val offered = PurposeUi.Offered()
        for (phase in Phase.values()) assertEquals("phase $phase", phase != Phase.WAITING, purposeChipsLocked(phase, offered))
        assertTrue(purposeChipsLocked(Phase.WAITING, offered.copy(failure = PurposeFailure.NotPending)))
        assertFalse(purposeChipsLocked(Phase.WAITING, offered.copy(failure = PurposeFailure.Failed)))
        assertFalse(purposeChipsLocked(Phase.WAITING, offered.copy(failure = PurposeFailure.Refused("X"))))
    }

    // ---- what each end of a call leaves ----

    @Test
    fun `an accepted call confirms the choice and clears the failure and the call in flight`() {
        val chosen = selectionOf(ScanPurposeChoice.MAINTENANCE_AFTER, DefectType.THERMAL)
        val before = PurposeUi.Offered(failure = PurposeFailure.Failed).started(chosen)
        assertEquals("starting a call clears the earlier failure", null, before.failure)
        val after = before.accepted(chosen)
        assertEquals(PurposeConfirmed.Chosen(chosen), after.confirmed)
        assertEquals(null, after.inFlight)
        assertEquals(null, after.failure)
    }

    @Test
    fun `a failed call leaves the confirmed purpose exactly as it was and records how it ended`() {
        val chosen = selectionOf(ScanPurposeChoice.MAINTENANCE_BEFORE, DefectType.CHARGING_PORT)
        val confirmed = PurposeUi.Offered().accepted(chosen)
        val after = confirmed.started(PurposeSelection.Normal).failed(java.io.IOException("no route"))
        assertEquals("the server did not say yes: still maintenance_before with its type", PurposeConfirmed.Chosen(chosen), after.confirmed)
        assertEquals(null, after.inFlight)
        assertEquals(PurposeFailure.Failed, after.failure)
        // and from the creation default, a failed call leaves the creation default (not a chosen normal)
        assertEquals(PurposeConfirmed.AtCreation, PurposeUi.Offered().started(selectionOf(ScanPurposeChoice.MAINTENANCE_AFTER)).failed(java.io.IOException("x")).confirmed)
    }

    // ---- the fault type: a maintenance chip opens its list and sends nothing, a type sends (order of 2026-10-22) ----

    private val maintenanceChoices = ScanPurposeChoice.values().filter { it.needsDefectType }

    @Test
    fun `a tap on a chip - normal is sent, a maintenance chip opens its types and sends nothing - in every phase`() {
        val offered = offeredBoth
        for (phase in Phase.values()) {
            for (choice in ScanPurposeChoice.values()) {
                val expected = when {
                    phase != Phase.WAITING -> PurposeChipTap.Ignore("not_pending")
                    choice.needsDefectType -> PurposeChipTap.OpenTypes(choice)
                    else -> PurposeChipTap.SendNormal
                }
                assertEquals("$phase / ${choice.wire}", expected, purposeChipTapDecision(offered, phase, hasSession = true, choice))
            }
        }
    }

    @Test
    fun `a chip tap is ignored for the same named reasons as any other - no session, not offered, locked, a call out`() {
        for (choice in ScanPurposeChoice.values()) {
            assertEquals(PurposeChipTap.Ignore("no_session"), purposeChipTapDecision(offeredBoth, Phase.WAITING, hasSession = false, choice))
            assertEquals(PurposeChipTap.Ignore("not_offered"), purposeChipTapDecision(PurposeUi.NotRead, Phase.WAITING, hasSession = true, choice))
            for (why in PurposeUi.NotOffered.Why.values()) {
                assertEquals(PurposeChipTap.Ignore("not_offered"), purposeChipTapDecision(PurposeUi.NotOffered(why), Phase.WAITING, hasSession = true, choice))
            }
            assertEquals(PurposeChipTap.Ignore("locked"), purposeChipTapDecision(offeredBoth.copy(failure = PurposeFailure.NotPending), Phase.WAITING, hasSession = true, choice))
            assertEquals(
                PurposeChipTap.Ignore("in_flight"),
                purposeChipTapDecision(offeredBoth.started(selectionOf(ScanPurposeChoice.MAINTENANCE_BEFORE)), Phase.WAITING, hasSession = true, choice)
            )
        }
    }

    @Test
    fun `a type tap is sent for the chip whose list of types is on the screen - the one awaiting its type or the accepted one - with that chip's purpose and the tapped type, for every pair`() {
        for (choice in maintenanceChoices) {
            for (type in DefectType.values()) {
                val awaiting = offeredBoth.typeRequested(choice)
                assertEquals("${choice.wire}/${type.wire}", PurposeTypeTap.Send(PurposeSelection.Maintenance(choice, type)), purposeTypeTapDecision(awaiting, Phase.WAITING, hasSession = true, type))
                // order of 2026-10-28: the list stays after the server accepted a type, and a tap on ANOTHER type (or the same) is sent as that chip's purpose - it was ignored with no_purpose_awaiting_type
                // (logcat-chips-fix.txt: 26 ignored taps after 3 accepted calls; logcat-type-switch.txt: 1 after 1)
                for (accepted in DefectType.values()) {
                    val acceptedUi = offeredBoth.accepted(PurposeSelection.Maintenance(choice, accepted))
                    assertEquals(
                        "${choice.wire}/${accepted.wire} accepted, then ${type.wire}",
                        PurposeTypeTap.Send(PurposeSelection.Maintenance(choice, type)),
                        purposeTypeTapDecision(acceptedUi, Phase.WAITING, hasSession = true, type)
                    )
                    // the call for that change is out: one call at a time, as for any tap
                    assertEquals(
                        PurposeTypeTap.Ignore("in_flight"),
                        purposeTypeTapDecision(acceptedUi.started(PurposeSelection.Maintenance(choice, type)), Phase.WAITING, hasSession = true, accepted)
                    )
                }
                // the OTHER maintenance chip tapped after an accepted one: its list opens, and the type goes with the chip tapped, not the accepted one
                val other = maintenanceChoices.first { it != choice }
                val moved = offeredBoth.accepted(PurposeSelection.Maintenance(choice, DefectType.BATTERY)).typeRequested(other)
                assertEquals(PurposeTypeTap.Send(PurposeSelection.Maintenance(other, type)), purposeTypeTapDecision(moved, Phase.WAITING, hasSession = true, type))
            }
        }
        for (type in DefectType.values()) {
            assertEquals("no list on the screen: the tap sends nothing", PurposeTypeTap.Ignore("no_purpose_awaiting_type"), purposeTypeTapDecision(offeredBoth, Phase.WAITING, hasSession = true, type))
            // an accepted «normal» has no list of types either
            assertEquals(
                PurposeTypeTap.Ignore("no_purpose_awaiting_type"),
                purposeTypeTapDecision(offeredBoth.accepted(PurposeSelection.Normal), Phase.WAITING, hasSession = true, type)
            )
        }
    }

    @Test
    fun `a type tap is ignored for the same named reasons - not pending in every other phase, no session, not offered, locked, a call out`() {
        val awaiting = offeredBoth.typeRequested(ScanPurposeChoice.MAINTENANCE_BEFORE)
        val type = DefectType.THERMAL
        for (phase in Phase.values().filter { it != Phase.WAITING }) {
            assertEquals("$phase", PurposeTypeTap.Ignore("not_pending"), purposeTypeTapDecision(awaiting, phase, hasSession = true, type))
        }
        assertEquals(PurposeTypeTap.Ignore("no_session"), purposeTypeTapDecision(awaiting, Phase.WAITING, hasSession = false, type))
        assertEquals(PurposeTypeTap.Ignore("not_offered"), purposeTypeTapDecision(PurposeUi.NotRead, Phase.WAITING, hasSession = true, type))
        assertEquals(PurposeTypeTap.Ignore("locked"), purposeTypeTapDecision(awaiting.copy(failure = PurposeFailure.NotPending), Phase.WAITING, hasSession = true, type))
        assertEquals(PurposeTypeTap.Ignore("in_flight"), purposeTypeTapDecision(awaiting.started(selectionOf(ScanPurposeChoice.MAINTENANCE_BEFORE)), Phase.WAITING, hasSession = true, type))
    }

    @Test
    fun `only a chip that needs a type can await one - normal cannot`() {
        val failure = runCatching { offeredBoth.typeRequested(ScanPurposeChoice.NORMAL) }.exceptionOrNull()
        assertTrue("$failure", failure is IllegalArgumentException)
        for (choice in maintenanceChoices) assertEquals(choice, offeredBoth.typeRequested(choice).awaitingType)
    }

    @Test
    fun `asking for a type clears an earlier failure but touches nothing else - the confirmed purpose stays`() {
        val confirmed = offeredBoth.accepted(PurposeSelection.Normal).copy(failure = PurposeFailure.Failed)
        val awaiting = confirmed.typeRequested(ScanPurposeChoice.MAINTENANCE_AFTER)
        assertEquals(null, awaiting.failure)
        assertEquals(confirmed.confirmed, awaiting.confirmed)
        assertEquals(ScanPurposeChoice.NORMAL, awaiting.highlighted())
    }

    @Test
    fun `the list of types is shown for the chip awaiting a type, or else the accepted maintenance chip - never for normal or the creation default`() {
        assertEquals(null, offeredBoth.typeListFor())
        assertEquals(null, offeredBoth.accepted(PurposeSelection.Normal).typeListFor())
        for (choice in maintenanceChoices) {
            assertEquals(choice, offeredBoth.typeRequested(choice).typeListFor())
            assertEquals(choice, offeredBoth.accepted(selectionOf(choice)).typeListFor())
        }
        // an accepted «after» with the technician now looking at «before»'s types: the list is «before»'s
        assertEquals(
            ScanPurposeChoice.MAINTENANCE_BEFORE,
            offeredBoth.accepted(selectionOf(ScanPurposeChoice.MAINTENANCE_AFTER)).typeRequested(ScanPurposeChoice.MAINTENANCE_BEFORE).typeListFor()
        )
    }

    @Test
    fun `the highlighted type is the accepted one and only while the list is the accepted chip's - for every choice and every type`() {
        assertEquals(null, offeredBoth.highlightedType())
        assertEquals(null, offeredBoth.accepted(PurposeSelection.Normal).highlightedType())
        for (choice in maintenanceChoices) {
            for (type in DefectType.values()) {
                val accepted = offeredBoth.accepted(PurposeSelection.Maintenance(choice, type))
                assertEquals("${choice.wire}/${type.wire}", type, accepted.highlightedType())
                // the same chip asked again: still the accepted type
                assertEquals(type, accepted.typeRequested(choice).highlightedType())
                // the other chip's list: no type is highlighted under it
                val other = maintenanceChoices.single { it != choice }
                assertEquals("${choice.wire}/${type.wire} under ${other.wire}", null, accepted.typeRequested(other).highlightedType())
            }
        }
    }

    @Test
    fun `a maintenance call keeps its chip's list open, a normal call closes it, an accepted call closes the wait, a failed one keeps it`() {
        val awaiting = offeredBoth.typeRequested(ScanPurposeChoice.MAINTENANCE_BEFORE)
        val maintenance = PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_BEFORE, DefectType.MOTHERBOARD)
        val started = awaiting.started(maintenance)
        assertEquals(ScanPurposeChoice.MAINTENANCE_BEFORE, started.awaitingType)
        assertEquals("a type in flight is not highlighted as chosen", null, started.highlightedType())
        assertEquals(null, awaiting.started(PurposeSelection.Normal).awaitingType)
        assertEquals(null, started.accepted(maintenance).awaitingType)
        assertEquals(DefectType.MOTHERBOARD, started.accepted(maintenance).highlightedType())
        val failed = started.failed(java.io.IOException("no route"))
        assertEquals("a failed call leaves the chip awaiting, so the technician can tap a type again", ScanPurposeChoice.MAINTENANCE_BEFORE, failed.awaitingType)
        assertEquals(PurposeFailure.Failed, failed.failure)
        assertEquals(PurposeConfirmed.AtCreation, failed.confirmed)
    }

    // ---- how a failure is classified (rule 21: by reasonCode only) ----

    @Test
    fun `a refusal is classified by details reasonCode - the one code is the lock, every other known code is a refusal that keeps the chips usable`() {
        for (code in SessionErrorCode.values()) {
            val expected = when (code) {
                SessionErrorCode.PURPOSE_SESSION_NOT_PENDING -> PurposeFailure.NotPending
                SessionErrorCode.PURPOSE_PART_NOT_ENABLED -> PurposeFailure.PartNotEnabled // Q31: its own state, its own text (ص21), and the chips stay usable
                else -> PurposeFailure.Refused(code.wire)
            }
            assertEquals(code.wire, expected, purposeFailureOf(refusal(mapOf("reasonCode" to code.wire))))
        }
        assertEquals(PurposeFailure.Refused("unrecognized"), purposeFailureOf(refusal(mapOf("reasonCode" to "PURPOSE_BRAND_NEW"))))
    }

    @Test
    fun `an exception with no reasonCode is a failure, whatever its type or message says - never the lock`() {
        assertEquals(PurposeFailure.Failed, purposeFailureOf(java.io.IOException("PURPOSE_SESSION_NOT_PENDING")))
        assertEquals(PurposeFailure.Failed, purposeFailureOf(RuntimeException("PURPOSE_SESSION_NOT_PENDING")))
        assertEquals(PurposeFailure.Failed, purposeFailureOf(IllegalStateException("Firebase Auth user is null")))
        assertEquals(PurposeFailure.Failed, purposeFailureOf(refusal(null)))
        assertEquals(PurposeFailure.Failed, purposeFailureOf(refusal(mapOf("code" to "PURPOSE_SESSION_NOT_PENDING"))))
        assertEquals("the case matters", PurposeFailure.Refused("unrecognized"), purposeFailureOf(refusal(mapOf("reasonCode" to "purpose_session_not_pending"))))
        // Q31: the new code is read from the reasonCode only - its name in a message, a type or another key is a failure, never the new state (rule 21)
        assertEquals(PurposeFailure.Failed, purposeFailureOf(java.io.IOException("PURPOSE_PART_NOT_ENABLED")))
        assertEquals(PurposeFailure.Failed, purposeFailureOf(refusal(mapOf("code" to "PURPOSE_PART_NOT_ENABLED"))))
        assertEquals(PurposeFailure.Refused("unrecognized"), purposeFailureOf(refusal(mapOf("reasonCode" to "purpose_part_not_enabled"))))
    }

    @Test
    fun `the part-not-enabled refusal leaves the chips usable - a send is allowed again, the chips are not locked`() {
        val refused = offeredBoth.copy(failure = PurposeFailure.PartNotEnabled)
        assertFalse(purposeChipsLocked(Phase.WAITING, refused))
        assertEquals(PurposeTap.Send, purposeTapDecision(refused, Phase.WAITING, hasSession = true))
    }

    // ---- order of 2026-10-28: the row shows the technician's tap at once (shownChoice / shownType); the confirmed purpose is untouched ----

    @Test
    fun `the row shows the chip in flight, else the chip awaiting its type, else the confirmed one - and the confirmed one is never moved by a request`() {
        // nothing asked: the confirmed one (the creation default)
        assertEquals(ScanPurposeChoice.NORMAL, offeredBoth.shownChoice(locked = false))
        // a maintenance chip tapped, its type awaited: shown at once, nothing confirmed
        val awaitingAfter = offeredBoth.typeRequested(ScanPurposeChoice.MAINTENANCE_AFTER)
        assertEquals(ScanPurposeChoice.MAINTENANCE_AFTER, awaitingAfter.shownChoice(locked = false))
        assertEquals(PurposeConfirmed.AtCreation, awaitingAfter.confirmed)
        assertEquals(ScanPurposeChoice.NORMAL, awaitingAfter.highlighted())
        // a call out for a type: the chip stays the shown one
        val inFlight = awaitingAfter.started(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_AFTER, DefectType.THERMAL))
        assertEquals(ScanPurposeChoice.MAINTENANCE_AFTER, inFlight.shownChoice(locked = false))
        assertEquals(PurposeConfirmed.AtCreation, inFlight.confirmed)
        // «normal» over an accepted maintenance purpose: «normal» is shown while its call is out
        val accepted = offeredBoth.accepted(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_BEFORE, DefectType.BATTERY))
        assertEquals(ScanPurposeChoice.MAINTENANCE_BEFORE, accepted.shownChoice(locked = false))
        assertEquals(ScanPurposeChoice.NORMAL, accepted.started(PurposeSelection.Normal).shownChoice(locked = false))
        assertEquals("the confirmed one is still the maintenance purpose until the server says yes", ScanPurposeChoice.MAINTENANCE_BEFORE, accepted.started(PurposeSelection.Normal).highlighted())
        // «before» confirmed, then «after» asked: the row shows «after» at once (the report of 2026-10-28: «قبل الصيانة» stayed lit after a tap on «بعد الصيانة»)
        assertEquals(ScanPurposeChoice.MAINTENANCE_AFTER, accepted.typeRequested(ScanPurposeChoice.MAINTENANCE_AFTER).shownChoice(locked = false))
    }

    @Test
    fun `a locked row or a call that did not end in an acceptance shows only the confirmed chip and type`() {
        val accepted = offeredBoth.accepted(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_BEFORE, DefectType.BATTERY))
        val asked = accepted.typeRequested(ScanPurposeChoice.MAINTENANCE_AFTER)
        // the customer's phone joined while a type was awaited: the server never took «after»
        assertEquals(ScanPurposeChoice.MAINTENANCE_BEFORE, asked.shownChoice(locked = true))
        assertEquals(DefectType.BATTERY, accepted.shownType(locked = true))
        // a call that failed or was refused: the confirmed one again
        val ended = asked.started(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_AFTER, DefectType.SOFTWARE)).failed(java.io.IOException("no route"))
        assertEquals(ScanPurposeChoice.MAINTENANCE_BEFORE, ended.shownChoice(locked = false))
        assertEquals(null, ended.shownType(locked = false))
        val refused = asked.copy(failure = PurposeFailure.PartNotEnabled)
        assertEquals(ScanPurposeChoice.MAINTENANCE_BEFORE, refused.shownChoice(locked = false))
    }

    @Test
    fun `the type shown is the one in flight for the chip whose list is open - else the accepted one - and never another chip's type`() {
        val awaitingBefore = offeredBoth.typeRequested(ScanPurposeChoice.MAINTENANCE_BEFORE)
        assertEquals(null, awaitingBefore.shownType(locked = false))
        val inFlight = awaitingBefore.started(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_BEFORE, DefectType.MOTHERBOARD))
        assertEquals(DefectType.MOTHERBOARD, inFlight.shownType(locked = false))
        assertEquals("the confirmed type is still none until the server says yes", null, inFlight.highlightedType())
        // the accepted type of «after» is not shown under the list of «before» that is awaited
        val acceptedAfter = offeredBoth.accepted(PurposeSelection.Maintenance(ScanPurposeChoice.MAINTENANCE_AFTER, DefectType.BATTERY))
        assertEquals(DefectType.BATTERY, acceptedAfter.shownType(locked = false))
        assertEquals(null, acceptedAfter.typeRequested(ScanPurposeChoice.MAINTENANCE_BEFORE).shownType(locked = false))
        // a «normal» call out has no type to show
        assertEquals(null, offeredBoth.started(PurposeSelection.Normal).shownType(locked = false))
    }
}
