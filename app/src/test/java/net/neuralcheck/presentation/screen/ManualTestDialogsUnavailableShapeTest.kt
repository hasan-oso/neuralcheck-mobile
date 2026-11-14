package net.neuralcheck.presentation.screen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Batch 1.0.12, second part: the dialogs that carry the «غير متوفر في هذا الجهاز» button READ the one list through the row, they do not hard-code
 * the button. A STATIC SOURCE SCAN of `ManualTestDialogs.kt` with comments removed (the technique `EvalCardLogSourceShapeTest` uses): when written the unit tests did not have a
 * Compose harness (`ManualUnavailableLineLayoutTest` renders the row since 3284cca). Which routes pass the argument is `ManualRowSourceShapeTest`; which rows are on the list is
 * `ManualUnavailableOfferTest`.
 *
 * What is pinned: the button's `onResult(ManualStatuses.UNAVAILABLE)` occurs in exactly five dialog bodies. Three are routed (the headphone jack, the SD card and
 * the shared battery-state detector that M-12 and M-14 use) and each puts the button under `dismissButton = if (offerUnavailable)`. Two are the retired OTG
 * (M-13) and IR (M-16) dialogs: no route, no list entry, dead code the order says not to touch — they are named here so that a THIRD ungated
 * button cannot appear unnoticed.
 */
class ManualTestDialogsUnavailableShapeTest {

    private val dialogsFile: File = listOf("app/src/main/java", "../app/src/main/java")
        .map { File(it, "net/neuralcheck/presentation/screen/ManualTestDialogs.kt") }
        .firstOrNull { it.isFile }
        ?: error("could not locate ManualTestDialogs.kt from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private val text: String by lazy { stripComments(dialogsFile.readText()) }

    /** For each occurrence of the button: the function it is in, and whether it is under `if (offerUnavailable)`. */
    private val buttons: List<Pair<String, Boolean>> by lazy {
        Regex("""onResult\(ManualStatuses\.UNAVAILABLE\)""").findAll(text).map { m ->
            // the TOP-LEVEL function (column 0) the button is in — inner helpers (onReceive, currentPlugged…) are indented
            val fn = Regex("""(?m)^(?:private\s+|internal\s+)?fun\s+(\w+)\(""").findAll(text.substring(0, m.range.first)).last().groupValues[1]
            val gated = text.substring(maxOf(0, m.range.first - 200), m.range.first).contains("if (offerUnavailable)")
            fn to gated
        }.toList()
    }

    @Test
    fun `the button is in five dialog bodies - three routed ones gated by offerUnavailable, and the two dead ones, ungated`() {
        assertEquals(
            listOf(
                "HeadphoneJackTestDialog" to true,
                "OtgTestDialog" to false,
                "SdCardTestDialog" to true,
                "IrBlasterTestDialog" to false,
                "DetectOnBatteryState" to true
            ),
            buttons
        )
    }

    @Test
    fun `the routed dialogs take the flag explicitly - no default, so a caller cannot forget it`() {
        for (fn in listOf("HeadphoneJackTestDialog", "ChargingPortTestDialog", "WirelessChargingTestDialog", "SdCardTestDialog", "DetectOnBatteryState")) {
            val signature = text.substringAfter("fun $fn(").substringBefore(") {")
            assertTrue("$fn takes `offerUnavailable: Boolean`", Regex("""\bofferUnavailable: Boolean\b""").containsMatchIn(signature))
            assertTrue("$fn gives it no default value", !Regex("""\bofferUnavailable: Boolean\s*=""").containsMatchIn(signature))
        }
    }

    @Test
    fun `the two wrappers hand the flag to the shared detector`() {
        for (fn in listOf("ChargingPortTestDialog", "WirelessChargingTestDialog")) {
            val body = text.substringAfter("fun $fn(").substringBefore("\n}\n")
            assertTrue("$fn passes offerUnavailable on", Regex("""offerUnavailable = offerUnavailable""").containsMatchIn(body))
        }
    }

    @Test
    fun `the three gated dialogs show no dismiss button when the row does not offer it - dismissButton is null`() {
        assertEquals(3, Regex("""dismissButton = if \(offerUnavailable\) \{""").findAll(text).count())
        assertEquals(
            "each of the three ends its `if` with `else null`",
            3, Regex("""dismissButton = if \(offerUnavailable\) \{\s*\{.*?\}\s*\} else null""", RegexOption.DOT_MATCHES_ALL).findAll(text).count()
        )
    }
}
