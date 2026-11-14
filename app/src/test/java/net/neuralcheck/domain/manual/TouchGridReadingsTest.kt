package net.neuralcheck.domain.manual

import net.neuralcheck.util.ReasonText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * fieldsVersion 71 (contract 7-ي ف-02, N71-01): how an answer of the platform, or the lack of one, becomes a stated reading — across every API
 * level the app runs on (minSdk 28) and every failure — and that no literal of an enumerated wire value is spelled outside its one source (rule 24).
 */
class TouchGridReadingsTest {

    private val firstGestureApi = TouchGridReadings.FIRST_API_GESTURES

    @Test
    fun `the API thresholds are the contract's - gestures and exclusion rects from 29, rounded corners from 31`() {
        assertEquals(29, TouchGridReadings.FIRST_API_GESTURES)
        assertEquals(31, TouchGridReadings.FIRST_API_ROUNDED_CORNERS)
    }

    @Test
    fun `below the call's API level the platform is not asked and the reason is api_below_n`() {
        var asked = false
        val r = TouchGridReadings.sinceApi(28, firstGestureApi) { asked = true; 1 }
        assertEquals(Reading.Absent("api_below_29"), r)
        assertFalse("the platform was not called", asked)
    }

    @Test
    fun `from the call's API level the answer is the value`() {
        assertEquals(Reading.Value(7), TouchGridReadings.sinceApi(29, firstGestureApi) { 7 })
        assertEquals(Reading.Value(7), TouchGridReadings.sinceApi(35, firstGestureApi) { 7 })
    }

    @Test
    fun `a refusal is the error's MESSAGE text - not its class - capped at 200 characters`() {
        val r = TouchGridReadings.attempt<Int> { throw IllegalStateException("the view has no root window insets yet") }
        assertEquals(Reading.Absent("read_failed:the view has no root window insets yet"), r)
        val long = TouchGridReadings.attempt<Int> { throw IllegalStateException("x".repeat(500)) } as Reading.Absent
        assertEquals("read_failed:".length + ReasonText.MAX_CHARS, long.reason.length)
        val none = TouchGridReadings.attempt<Int> { throw RuntimeException() } as Reading.Absent
        assertEquals("read_failed:" + ReasonText.NO_MESSAGE, none.reason)
        assertFalse("never the exception class", long.reason.contains("IllegalStateException") || none.reason.contains("RuntimeException"))
    }

    @Test
    fun `a refusal above the API level is a read_failed, not an api reason`() {
        val r = TouchGridReadings.sinceApi<Int>(34, firstGestureApi) { throw IllegalStateException("insets are not there") }
        assertEquals(Reading.Absent("read_failed:insets are not there"), r)
    }

    @Test
    fun `the rounded corners are the four radii from API 31, the not_available_api state below it, and read_failed with the text when the read fails`() {
        val radii = RoundedCornerRadii(80, 82, 84, 86)
        for (api in listOf(28, 29, 30)) {
            var asked = false
            assertEquals("api $api", RoundedCorners.State("not_available_api"), TouchGridReadings.roundedCorners(api) { asked = true; radii })
            assertFalse(asked)
        }
        for (api in listOf(31, 34, 35)) assertEquals("api $api", RoundedCorners.Radii(radii), TouchGridReadings.roundedCorners(api) { radii })
        assertEquals(
            RoundedCorners.State("read_failed:no platform form"),
            TouchGridReadings.roundedCorners(34) { throw IllegalStateException("no platform form") }
        )
    }

    @Test
    fun `the wire shape of a rectangle and of the corners is the contract's`() {
        assertEquals(linkedMapOf<String, Any?>("left" to 1, "top" to 2, "right" to 3, "bottom" to 4), PxRect(1, 2, 3, 4).toWire())
        assertEquals(
            linkedMapOf<String, Any?>("topLeft" to 1, "topRight" to 2, "bottomRight" to 3, "bottomLeft" to 4),
            RoundedCornerRadii(1, 2, 3, 4).toWire()
        )
    }

    @Test
    fun `the resampled flag state is not_available_api below API 35 and not_read_by_app from it`() {
        assertEquals(35, TouchGridContract.ResampledFlagStates.FIRST_API_WITH_FLAG)
        for (api in 28..34) assertEquals("api $api", "not_available_api", TouchGridContract.ResampledFlagStates.stateFor(api))
        for (api in 35..36) assertEquals("api $api", "not_read_by_app", TouchGridContract.ResampledFlagStates.stateFor(api))
    }

    @Test
    fun `the absence reasons are spelled once - api_below_, read_failed colon, not_reported_by_device`() {
        assertEquals("api_below_29", TouchGridContract.AbsentReasons.apiBelow(29))
        assertEquals("read_failed:boom", TouchGridContract.AbsentReasons.readFailed("boom"))
        assertEquals("read_failed:boom", TouchGridContract.AbsentReasons.readFailed(IllegalStateException("boom")))
        assertEquals("not_reported_by_device", TouchGridContract.AbsentReasons.NOT_REPORTED_BY_DEVICE)
    }

    // ───────────────────────── rule 24: one source for each enumerated value ─────────────────────────

    private val mainSources: File by lazy {
        listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory } ?: error("cannot find app/src/main/java from ${File(".").absolutePath}")
    }

    private fun filesWithLiteral(literal: String): List<String> =
        mainSources.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f -> f.readText().lines().any { line -> line.substringBefore("//").contains("\"$literal\"") } }
            .map { it.name }.sorted().toList()

    @Test
    fun `each enumerated wire value of the grid is spelled in its one source file and nowhere else in the app`() {
        val contract = "TouchGridContract.kt"
        for (v in listOf(
            "gap_jump", "coord_jump", "edge_exit", "cancel_flagged", "open_at_end", "XDPI_MODE_SCALED", "DENSITY_DPI_FALLBACK",
            "not_reported_by_device", "not_read_by_app", "xdpi_ydpi_differ_over_5pct", "both_candidates_plausible"
        )) assertEquals("\"$v\"", listOf(contract), filesWithLiteral(v))
        // the end reasons and the keys of the map are the recorder's
        for (v in listOf("auto_complete", "user_marked_not_working", "user_marked_not_tested")) {
            assertTrue("\"$v\" is only the recorders'", filesWithLiteral(v).all { it == "TouchGridRecorder.kt" || it == "VolumeKeyRecorder.kt" || it == "ManualAnswerLog.kt" || it == "ManualChipActions.kt" })
        }
        assertEquals(listOf("TouchGridRecorder.kt"), filesWithLiteral("displayedSuspects"))
        assertEquals(listOf("TouchGridRecorder.kt"), filesWithLiteral("untouchedCells"))
        assertEquals(listOf("TouchGridRecorder.kt"), filesWithLiteral("absentFieldReasons"))
    }
}
