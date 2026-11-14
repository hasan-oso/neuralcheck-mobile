package net.neuralcheck.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phase 3-b — pins [IssueSeverity.fromString] against both the V6 and V7
 * `issues[].severity` vocabularies. NEW tests (as of Phase 3-b): no prior
 * test in this codebase exercised `fromString` at all (confirmed by search
 * before writing this), so nothing in that phase replaced a test that
 * pinned the old `"fail" -> INFO` (via the catch-all `else`) behaviour —
 * there was none to replace (rule 23's "if none existed, say it's new").
 * The bug itself was real, though: found while wiring the V7 report
 * display, before any test existed to have caught it.
 *
 * 2026-09-24 update (جولة جرد القيم الافتراضية، البند ١، القرار ٧٦):
 * `else -> INFO` is replaced with `else -> UNKNOWN`, and `"info"` gets its
 * own explicit branch instead of reaching INFO only via that catch-all.
 * Per rule 23, `an unrecognised string falls back to INFO` — the test that
 * PINNED the exact behaviour just removed — is replaced below by
 * `an unrecognised or absent string falls back to UNKNOWN, not INFO`,
 * rather than left in place alongside a new one; keeping both would leave
 * a test in the suite asserting the old, now-wrong mapping.
 */
class IssueSeverityTest {

    // ── V6 vocabulary (unchanged by this phase) ──

    @Test
    fun `V6 severities map to their matching enum values`() {
        assertEquals(IssueSeverity.CRITICAL, IssueSeverity.fromString("critical"))
        assertEquals(IssueSeverity.DANGER, IssueSeverity.fromString("danger"))
        assertEquals(IssueSeverity.HIGH, IssueSeverity.fromString("high"))
        assertEquals(IssueSeverity.WARNING, IssueSeverity.fromString("warning"))
    }

    @Test
    fun `matching is case-insensitive`() {
        assertEquals(IssueSeverity.CRITICAL, IssueSeverity.fromString("CRITICAL"))
        assertEquals(IssueSeverity.DANGER, IssueSeverity.fromString("Danger"))
    }

    @Test
    fun `an unrecognised or absent string falls back to UNKNOWN, not INFO`() {
        // Replaces the pre-2026-09-24 `an unrecognised string falls back to
        // INFO` test (rule 23) — that was exactly the behaviour this order
        // removes: an unrecognised or missing severity used to render with
        // INFO's calm blue styling, indistinguishable from a real
        // informational finding. `""` stands in for the absent case too —
        // AiScoringEngine.kt calls `fromString(it.severity.orEmpty())`, so a
        // null `severity` reaches this function as `""`, never as a Kotlin
        // `null` (the function's parameter is non-null `String`).
        assertEquals(IssueSeverity.UNKNOWN, IssueSeverity.fromString("something_else"))
        assertEquals(IssueSeverity.UNKNOWN, IssueSeverity.fromString(""))
        assertEquals(IssueSeverity.UNKNOWN, IssueSeverity.fromString("null"))
        assertEquals(IssueSeverity.UNKNOWN, IssueSeverity.fromString("unknown"))
    }

    // ── V7 vocabulary (the Phase 3-b fix, extended 2026-09-24) ──

    @Test
    fun `V7 fail maps to DANGER - the golden sample's one real failure, SEC-04 root detected`() {
        // Before Phase 3-b, "fail" fell through the else branch to INFO —
        // the report's one actual security failure (root detection, the
        // finding that caps the whole letter at C) would have rendered
        // with the same blue "informational" styling as a passed test.
        assertEquals(IssueSeverity.DANGER, IssueSeverity.fromString("fail"))
        assertEquals(IssueSeverity.DANGER, IssueSeverity.fromString("FAIL"))
    }

    @Test
    fun `V7 warning maps to WARNING`() {
        assertEquals(IssueSeverity.WARNING, IssueSeverity.fromString("warning"))
        assertEquals(IssueSeverity.WARNING, IssueSeverity.fromString("WARNING"))
    }

    @Test
    fun `V7 info maps to INFO through its own explicit branch`() {
        // 2026-09-24: "info" now has its own `when` case instead of
        // reaching INFO only via the `else` catch-all — the catch-all now
        // means UNKNOWN, so this must be pinned as its own explicit branch.
        assertEquals(IssueSeverity.INFO, IssueSeverity.fromString("info"))
        assertEquals(IssueSeverity.INFO, IssueSeverity.fromString("INFO"))
    }

    @Test
    fun `V7 positive maps to INFO explicitly`() {
        // Kept explicit and distinct from the "info" branch above — see
        // IssueSeverity's class doc: no decision exists that POSITIVE must
        // look identical to INFO forever, only that it does today.
        assertEquals(IssueSeverity.INFO, IssueSeverity.fromString("positive"))
        assertEquals(IssueSeverity.INFO, IssueSeverity.fromString("POSITIVE"))
    }

    @Test
    fun `every V7 severity value V7 can emit has a distinct explicit mapping (rule 30)`() {
        // The four values V7 is observed to emit (see the golden sample and
        // live traffic cited in the 2026-09-24 order): FAIL/WARNING/INFO/
        // POSITIVE. Asserted together so a future accidental merge of any
        // two branches (e.g. collapsing "positive" back into the "info"
        // case, or losing the "info" branch back to `else`) fails here
        // rather than only in a UI test that happens to sample one value.
        assertEquals(IssueSeverity.DANGER, IssueSeverity.fromString("FAIL"))
        assertEquals(IssueSeverity.WARNING, IssueSeverity.fromString("WARNING"))
        assertEquals(IssueSeverity.INFO, IssueSeverity.fromString("INFO"))
        assertEquals(IssueSeverity.INFO, IssueSeverity.fromString("POSITIVE"))
        assertEquals(IssueSeverity.UNKNOWN, IssueSeverity.fromString(""))
    }
}
