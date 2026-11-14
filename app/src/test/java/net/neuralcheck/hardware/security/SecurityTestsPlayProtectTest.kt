package net.neuralcheck.hardware.security

import android.content.Context
import net.neuralcheck.R
import net.neuralcheck.domain.model.TestStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Order 2026-09-30 (فحص NC-TM7PDN، البند ٢): SEC-08 (Play Protect) used to
 * unconditionally return TestStatus.PASS / "مفعّل" without reading anything —
 * SecurityTests.kt's old testPlayProtect() body was exactly:
 *   `return TestResult("SEC-08", nameAr, nameTr, "security", TestStatus.PASS,
 *   null, enabledAr, "", null)` — no SafetyNet/Play Integrity call, no
 * PackageManager or Settings read, nothing device-specific at all. The
 * launch flavor's POS-security summary then told the buyer "Play Protect
 * مفعّل" as a measured fact on every device, including ones where it's off.
 *
 * This exercises the REAL SecurityTests class (not a stand-in) through its
 * public runAll() — testPlayProtect() itself is private, so the only
 * production entry point is runAll(). Context is a Mockito mock stubbed
 * ONLY for the four getString() calls testPlayProtect() actually makes
 * (test_sec08_ar/_tr, value_unavailable_ar, sec08_status_not_read); every
 * other SEC-* test in the same runAll() call is left unstubbed and is
 * expected to fall into its own safeTest() exception fallback (SecurityTests
 * .kt's `safeTest` wraps each probe individually) — harmless here since this
 * test asserts only on the SEC-08 entry.
 *
 * There was no pre-existing test asserting SEC-08 == PASS anywhere in this
 * suite (checked via grep across app/src/test — only unrelated V6/V7
 * contract-fixture JSON samples mention the "SEC-08" string as one of many
 * IDs in a larger payload, none of them exercise SecurityTests.kt), so this
 * is a new test, not a replacement — القاعدة ٢٣ does not apply here.
 */
class SecurityTestsPlayProtectTest {

    private fun contextStubbedForPlayProtect(): Context {
        val context = mock<Context>()
        // runAll() also invokes SEC-01..SEC-07/SEC-09..SEC-11 in the same
        // call. Every one of those is expected to throw internally on this
        // bare mock (unmocked getSystemService()/Settings reads) and be
        // caught by SecurityTests.kt's own safeTest() — but that catch
        // block (SecurityTests.kt:68-69) itself calls context.getString()
        // to build a fallback name, and an unstubbed Mockito mock returns
        // null there, which crashes with NPE OUTSIDE any try/catch (Kotlin
        // inserts a not-null assertion on getOrDefault's inferred String
        // result). A blanket any() stub, overridden below by the four exact
        // resource IDs testPlayProtect() itself needs, keeps every OTHER
        // test's fallback path from crashing the whole runAll() call.
        whenever(context.getString(any<Int>())).thenReturn("stub")
        whenever(context.getString(R.string.test_sec08_ar)).thenReturn("Play Protect")
        whenever(context.getString(R.string.test_sec08_tr)).thenReturn("Play Protect")
        whenever(context.getString(R.string.value_unavailable_ar)).thenReturn("غير متوفر")
        whenever(context.getString(R.string.sec08_status_not_read))
            .thenReturn("لا تُقرأ حالة Play Protect في هذا الإصدار")
        return context
    }

    @Test
    fun `SEC-08 reports UNAVAILABLE with an explanatory note, never a hardcoded PASS`() {
        val result = SecurityTests(contextStubbedForPlayProtect())
            .runAll()
            .first { it.testId == "SEC-08" }

        assertEquals(TestStatus.UNAVAILABLE, result.status)
        assertEquals("غير متوفر", result.rawValue)
        assertNotNull("expected an explanatory note instead of a silent UNAVAILABLE", result.notes)
        assertEquals("لا تُقرأ حالة Play Protect في هذا الإصدار", result.notes)
    }
}
