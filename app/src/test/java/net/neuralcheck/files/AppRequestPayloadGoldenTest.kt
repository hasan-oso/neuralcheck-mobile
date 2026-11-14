package net.neuralcheck.files

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.neuralcheck.BuildConfig
import net.neuralcheck.data.local.files.CpuKnownAnswerNames
import net.neuralcheck.data.local.files.VerificationWireNames
import net.neuralcheck.data.remote.AnalysisRequestSerializer
import net.neuralcheck.data.remote.model.BehaviorFingerprint
import net.neuralcheck.data.remote.model.DeviceInfo
import net.neuralcheck.data.remote.model.FunctionalResultDto
import net.neuralcheck.data.remote.model.ManualResultDto
import net.neuralcheck.data.remote.model.ManualResultsHeaderDto
import net.neuralcheck.domain.model.ManualStatuses
import net.neuralcheck.hardware.gps.GnssObservation
import net.neuralcheck.hardware.gps.L02Measurements
import net.neuralcheck.hardware.sensor.SensorTests
import net.neuralcheck.data.remote.model.RawTimeSeriesPayload
import net.neuralcheck.data.remote.model.TimeSeriesFooterDto
import net.neuralcheck.data.remote.model.TimeSeriesHeaderDto
import net.neuralcheck.data.remote.model.TimeSeriesSampleDto
import net.neuralcheck.data.remote.model.VariableDto
import net.neuralcheck.util.DeviceProfileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStreamReader
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.nio.file.Files

/**
 * fieldsVersion 68, batch 3 part 3 — RULE 39: the golden trap follows the contract.
 *
 * The committed `derived/fv71-app-request-payload-derived-from-*.json` samples (named fv70-... until A-M02, fv69-... until
 * the D fix, fv68-... until batch 10; the fv70 files are in git history at 077bbf2, the fv69 files at c22251f and the fv68 ones at 99c0548) are
 * what the app sends, built by the real chain ([AppRequestPayloadGolden]). This
 * class makes them impossible to forget:
 *
 *  1. the payload the chain builds TODAY is compared with each committed file PATH BY
 *     PATH — any change to the shape or to a value fails, and the failure names the path;
 *  2. the committed bytes are the canonical text of that payload (so the sha256 in
 *     README.md is the sha256 of what a copy receives);
 *  3. a key added to [net.neuralcheck.data.remote.BehaviorFingerprintBuilder] (or to a
 *     DTO it fills, or to a wire map it calls) that no sample shows FAILS — unless the
 *     absence is declared here with its reason (rule 25);
 *  4. README.md carries the sha256 of every committed sample and keys file.
 *
 * To regenerate, in the SAME commit as the contract change (rule 39), from a tree
 * with a clean test result:
 *
 *   ( cd <home>/NeuralChek && export JAVA_HOME="C:\Program Files\Android\Android Studio\jbr" \
 *     && export PATH="/c/Program Files/Android/Android Studio/jbr/bin:$PATH" && export NC_REGENERATE_GOLDEN=1 \
 *     && ./gradlew --no-daemon cleanTestLaunchDebugUnitTest testLaunchDebugUnitTest \
 *        --tests net.neuralcheck.files.AppRequestPayloadGoldenTest )
 *
 * which rewrites the samples, their keys files and the sha256 block of README.md.
 */
class AppRequestPayloadGoldenTest {

    companion object {
        const val REGENERATE_ENV = "NC_REGENERATE_GOLDEN"
        const val REGENERATE_COMMAND = "set NC_REGENERATE_GOLDEN=1 and run: ./gradlew --no-daemon cleanTestLaunchDebugUnitTest " +
            "testLaunchDebugUnitTest --tests net.neuralcheck.files.AppRequestPayloadGoldenTest " +
            "(see the class comment of AppRequestPayloadGoldenTest for the whole command line)"

        private const val BEGIN = "<!-- golden-sha256:begin -->"
        private const val END = "<!-- golden-sha256:end -->"

        /** The source tree's folder, so a regeneration and the comparison read the same files. */
        val derivedDir: File by lazy {
            listOf(File("src/test/resources/derived"), File("app/src/test/resources/derived"))
                .firstOrNull { it.isDirectory } ?: error("cannot find app/src/test/resources/derived from ${File(".").absolutePath}")
        }

        private val mainSources: File by lazy {
            listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
                ?: error("cannot find app/src/main/java from ${File(".").absolutePath}")
        }

        val built: List<AppRequestPayloadGolden.Built> by lazy {
            AppRequestPayloadGolden.buildAll(Files.createTempDirectory("fv71-golden").toFile())
        }

        private val regenerated: Boolean by lazy {
            if (System.getenv(REGENERATE_ENV) == "1") { regenerateFiles(); true } else false
        }

        private fun sha(file: File) = GoldenJson.sha256(file.readBytes())

        /** README.md as LF text (a Windows checkout with autocrlf turns it into CRLF; the samples themselves are `-text`). */
        private fun readmeText(): String = File(derivedDir, "README.md").readText(Charsets.UTF_8).replace("\r\n", "\n")

        private fun readmeBlock(): String = buildString {
            append(BEGIN).append('\n')
            for (b in built) {
                for (name in listOf(AppRequestPayloadGolden.fileName(b.variant), AppRequestPayloadGolden.keysFileName(b.variant))) {
                    append("- `").append(name).append("` sha256 `").append(sha(File(derivedDir, name))).append("`\n")
                }
            }
            append(END)
        }

        private fun regenerateFiles() {
            for (b in built) {
                val json = File(derivedDir, AppRequestPayloadGolden.fileName(b.variant))
                json.writeBytes(b.text.toByteArray(Charsets.UTF_8))
                val keys = GoldenJson.keyPaths(GoldenJson.parse(b.text)).joinToString("\n", postfix = "\n")
                File(derivedDir, AppRequestPayloadGolden.keysFileName(b.variant)).writeBytes(keys.toByteArray(Charsets.UTF_8))
            }
            val readme = File(derivedDir, "README.md")
            val text = readmeText()
            val from = text.indexOf(BEGIN)
            val to = text.indexOf(END)
            check(from >= 0 && to > from) { "README.md must carry the $BEGIN ... $END block before the first regeneration" }
            readme.writeText(text.substring(0, from) + readmeBlock() + text.substring(to + END.length), Charsets.UTF_8)
        }
    }

    @Before
    fun regenerateWhenAsked() {
        regenerated
    }

    private fun committed(name: String): File {
        val f = File(derivedDir, name)
        assertTrue("$name is not committed in $derivedDir — $REGENERATE_COMMAND", f.isFile)
        return f
    }

    // ───────────────────────── 1 and 2: the samples follow the chain ─────────────────────────

    @Test
    fun `every committed sample equals the payload the real chain builds today - path by path`() {
        for (b in built) {
            val name = AppRequestPayloadGolden.fileName(b.variant)
            val committedTree = GoldenJson.parse(committed(name).readText(Charsets.UTF_8))
            val today: JsonElement = AnalysisRequestSerializer.gson.toJsonTree(b.payload)
            val differences = GoldenJson.diff(committedTree, today)
            assertTrue(
                "$name no longer equals the payload the chain builds (rule 39: regenerate IN THE SAME COMMIT as the change). " +
                    "${differences.size} difference(s), first ones:\n  " + differences.joinToString("\n  ") + "\n$REGENERATE_COMMAND",
                differences.isEmpty()
            )
        }
    }

    @Test
    fun `every committed sample is byte for byte the canonical text of that payload`() {
        for (b in built) {
            val name = AppRequestPayloadGolden.fileName(b.variant)
            assertEquals(
                "$name: the tree matches but the bytes do not — the sha256 in README.md would not be the sha256 of a copy. $REGENERATE_COMMAND",
                b.text, committed(name).readText(Charsets.UTF_8)
            )
            assertFalse("$name must be LF only (the sha256 is of the committed bytes)", committed(name).readText(Charsets.UTF_8).contains('\r'))
        }
    }

    @Test
    fun `every keys file is exactly the key paths of its sample`() {
        for (b in built) {
            val expected = GoldenJson.keyPaths(GoldenJson.parse(b.text)).joinToString("\n", postfix = "\n")
            val name = AppRequestPayloadGolden.keysFileName(b.variant)
            assertEquals("$name is out of step with its sample. $REGENERATE_COMMAND", expected, committed(name).readText(Charsets.UTF_8))
        }
    }

    @Test
    fun `README carries the sha256 of every committed sample and keys file`() {
        val readme = readmeText()
        val from = readme.indexOf(BEGIN)
        val to = readme.indexOf(END)
        assertTrue("README.md must carry the $BEGIN ... $END block", from >= 0 && to > from)
        assertEquals(
            "the sha256 block of README.md is out of step with the committed files. $REGENERATE_COMMAND",
            readmeBlock(), readme.substring(from, to + END.length)
        )
    }

    @Test
    fun `the generator runs the very function the service calls - there is no second copy of the serialization`() {
        val service = File(mainSources, "net/neuralcheck/data/remote/FirebaseAnalysisService.kt").readText(Charsets.UTF_8)
        assertTrue(
            "FirebaseAnalysisService.analyzeDevice must flatten the fingerprint with AnalysisRequestSerializer.fingerprintToMap " +
                "(rule 37: the golden generator calls that function; a copy in the service would not be tested)",
            service.contains("AnalysisRequestSerializer.fingerprintToMap(fingerprint)")
        )
        assertFalse(
            "FirebaseAnalysisService must not build its own Gson — AnalysisRequestSerializer.gson is the one configuration",
            service.contains("GsonBuilder()")
        )
    }

    // ───────────────────────── what the three samples are (so a edit that empties one is caught) ─────────────────────────

    private fun root(b: AppRequestPayloadGolden.Built): JsonObject = GoldenJson.parse(b.text).asJsonObject
    private fun footer(b: AppRequestPayloadGolden.Built): JsonObject =
        root(b).getAsJsonObject("timeSeries").getAsJsonObject("footer")

    private fun variableNames(b: AppRequestPayloadGolden.Built): List<String> =
        root(b).getAsJsonObject("timeSeries").getAsJsonArray("variables").map { it.asJsonObject.get("name").asString }

    @Test
    fun `the three samples have the layouts they are named for - 11, 13 and 16 columns`() {
        assertEquals(listOf(11, 13, 16), built.map { variableNames(it).size })
        for (b in built) {
            val ts = root(b).getAsJsonObject("timeSeries")
            val width = variableNames(b).size
            assertEquals("file", ts.get("timeSeriesState").asString)
            assertTrue("every sample row is exactly as wide as variables[]",
                ts.getAsJsonArray("samples").all { it.asJsonObject.getAsJsonArray("v").size() == width })
            assertEquals(ts.getAsJsonArray("samples").size(), footer(b).get("totalSamples").asInt)
            assertEquals(BuildConfig.FIELDS_VERSION, footer(b).get("fieldsVersion").asInt)
            assertEquals("the reader asked the file for everything and nothing was missing", 0, footer(b).getAsJsonArray("fieldsNotInFile").size())
        }
    }

    @Test
    fun `the blocked channel of the 13-column sample is visible in channelDiscovery and not in v`() {
        val b = built.first { it.variant.id.startsWith("13ch") }
        val discovery = footer(b).getAsJsonObject("channelDiscovery")
        val blocked = discovery.getAsJsonArray("blockedBySelinux").map { it.asString }
        assertEquals(listOf("cpu_freq_policy4_khz"), blocked)
        assertTrue(discovery.getAsJsonObject("reasons").has("cpu_freq_policy4_khz"))
        assertFalse("a refused channel has no column", variableNames(b).contains("cpu_freq_policy4_khz"))
        assertTrue("its policy is still reported, with the reason it is unreadable",
            footer(b).getAsJsonArray("cpuPolicies").any { it.asJsonObject.get("policy").asInt == 4 && it.asJsonObject.has("unreadableReason") })
    }

    @Test
    fun `the 16-column sample shows a failure list cut at 200 with the true count beside it`() {
        val b = built.first { it.variant.id.startsWith("16ch") }
        val list = footer(b).getAsJsonObject("channelReadFailures").getAsJsonArray("cpu_freq_policy2_khz")
        val count = footer(b).getAsJsonObject("channelReadFailureCounts").get("cpu_freq_policy2_khz").asInt
        assertEquals(200, list.size())
        assertEquals(230, count)
    }

    @Test
    fun `the 11-column sample says discovery ran and found no policy - an empty list, not an absent key`() {
        val b = built.first { it.variant.id.startsWith("11ch") }
        assertTrue(footer(b).has("cpuPolicies"))
        assertEquals(0, footer(b).getAsJsonArray("cpuPolicies").size())
        assertEquals(0, footer(b).getAsJsonObject("channelReadFailures").size())
    }

    private fun knownAnswerRuns(b: AppRequestPayloadGolden.Built): List<JsonObject> =
        footer(b).getAsJsonObject("verificationResults").getAsJsonArray("cpuKnownAnswerRuns").map { it.asJsonObject }

    private fun knownAnswerState(b: AppRequestPayloadGolden.Built): String =
        footer(b).getAsJsonObject("verificationResults").get("cpuKnownAnswerState").asString

    @Test
    fun `D - the 11-column sample shows runs the time budget left out - two statuses and nothing else - from the real scheduler (fieldsVersion 70)`() {
        val b = built.first { it.variant.id.startsWith("11ch") }
        val runs = knownAnswerRuns(b)
        assertEquals("six planned runs, skipped ones included", 6, runs.size)
        assertEquals("some ran and some were left out: partial, not ran", "partial:time_budget", knownAnswerState(b))

        val first = runs[0]
        assertEquals("match", first.get("sieveStatus").asString)
        assertEquals(410L, first.get("tEnd").asLong - first.get("t").asLong)   // SCRIPTED: sieve 380 + SHA 30
        assertEquals(1_000_000L, first.get("sieveLimit").asLong)

        for (i in 1..2) {
            val r = runs[i]
            assertEquals(3, r.get("phase").asInt)
            assertEquals("skipped:time_budget", r.get("sieveStatus").asString)
            assertEquals("skipped:time_budget", r.get("sha256Status").asString)
            assertEquals(
                "a skipped run has no time, no end, no count, no duration and no limit - absent, never 0",
                setOf("phase", "plannedAtPct", "sieveStatus", "sha256Status"), r.keySet()
            )
        }
        assertEquals(listOf(60, 92), runs.subList(1, 3).map { it.get("plannedAtPct").asInt })
        assertTrue("phase 5 is decided on its own and ran all three",
            runs.subList(3, 6).all { it.get("phase").asInt == 5 && it.get("sieveStatus").asString == "match" && it.get("sha256Status").asString == "match" })
        val totals = footer(b).getAsJsonObject("verificationResults").getAsJsonObject("cpuKnownAnswerTotalMsByPhase")
        assertEquals("phase 3: the one run that ran (the skipped ones add nothing)", 410L, totals.get("3").asLong)
        assertEquals("phase 5: three runs of 120 ms", 360L, totals.get("5").asLong)
    }

    @Test
    fun `D - the other two samples ran without a skip - a mismatch and a thrown sieve call - and only a sieve that reported has a limit (fieldsVersion 70)`() {
        val b13 = built.first { it.variant.id.startsWith("13ch") }
        assertEquals("ran", knownAnswerState(b13))
        val mismatch = knownAnswerRuns(b13).single { it.get("sieveStatus").asString == "mismatch" }
        assertEquals(78_499L, mismatch.get("sievePrimeCount").asLong)
        assertEquals("a wrong count still says what it was counted to", 1_000_000L, mismatch.get("sieveLimit").asLong)

        val b16 = built.first { it.variant.id.startsWith("16ch") }
        assertEquals("ran", knownAnswerState(b16))
        val thrown = knownAnswerRuns(b16).single { it.get("sieveStatus").asString.startsWith("native_call_failed:") }
        assertFalse("a sieve call that threw reported nothing: no limit, no count", thrown.has("sieveLimit") || thrown.has("sievePrimeCount"))

        for (b in built) {
            for (r in knownAnswerRuns(b)) {
                val status = r.get("sieveStatus").asString
                val reported = status == "match" || status == "mismatch" || status == "alloc_failed"
                assertEquals("$status: the limit is present exactly when the native sieve reported", reported, r.has("sieveLimit"))
                assertFalse("no run of any sample is an unknown status", status.startsWith("native_status_unknown"))
            }
        }
        assertFalse("only the 11-column sample skips", listOf(b13, b16).any { s -> knownAnswerRuns(s).any { it.get("sieveStatus").asString == "skipped:time_budget" } })
    }

    @Test
    fun `functionalResults and manualResults are inside every sample with all six statuses`() {
        for (b in built) {
            val r = root(b)
            val statuses = (r.getAsJsonArray("functionalResults") + r.getAsJsonArray("manualResults"))
                .map { it.asJsonObject.get("status").asString }.toSet()
            assertEquals(setOf("pass", "fail", "warning", "unavailable", "not_tested", "error"), statuses)
        }
    }

    @Test
    fun `manualResults carry all four manual answers - pass, fail, not_tested and unavailable - in every sample (fieldsVersion 69)`() {
        for (b in built) {
            val manual = root(b).getAsJsonArray("manualResults").map { it.asJsonObject.get("status").asString }.toSet()
            for (answer in ManualStatuses.ALL) {
                assertTrue("${b.variant.id}: manualResults has no \"$answer\" row (rule 30: every value the app can write is in a sample)", answer in manual)
            }
        }
    }

    @Test
    fun `manualResultsHeader is in every sample - an empty hiddenByModel is an explicit list, and a hidden test has no row (fieldsVersion 69, N-17)`() {
        for (b in built) {
            val r = root(b)
            assertTrue("${b.variant.id}: the header key is present (absent = a build that told us nothing)", r.has("manualResultsHeader"))
            val hidden = r.getAsJsonObject("manualResultsHeader").getAsJsonArray("hiddenByModel").map { it.asString }
            val rowIds = r.getAsJsonArray("manualResults").map { it.asJsonObject.get("testId").asString }.toSet()
            assertTrue("${b.variant.id}: a hidden test has NO row in manualResults", hidden.none { it in rowIds })
            if (b.variant.id.startsWith("13ch")) {
                assertEquals("the 13-column sample scripts two hidden tests", listOf("M-14", "M-15"), hidden)
            } else {
                assertEquals("${b.variant.id}: the production table is empty, so nothing is hidden - and the list says so", emptyList<String>(), hidden)
            }
        }
    }

    @Test
    fun `M-02 carries the new grid's raw of its variant, and the endReason agrees with the answer - every shape the contract has (fieldsVersion 71, contract 7-ي)`() {
        for (b in built) {
            val id = b.variant.id
            val row = root(b).getAsJsonArray("manualResults").map { it.asJsonObject }.first { it.get("testId").asString == "M-02" }
            val raw = row.getAsJsonObject("measurements")
            val strokes = raw.getAsJsonArray("strokes").map { it.asJsonObject }
            val cap = raw.getAsJsonObject("pathCap")
            val required = raw.get("totalCells").asInt
            val cols = raw.get("gridCols").asInt
            val rows = raw.get("gridRows").asInt
            val cutout = raw.getAsJsonArray("cutoutCells").map { it.asInt }
            val notRequired = raw.getAsJsonArray("notRequiredCells").map { it.asInt }
            val untouched = raw.getAsJsonArray("untouchedCells").map { it.asInt }
            // the contract's binding relations (7-ي ف-01, as changed on 2026-10-21 by wire-m02-frame-x-2026-10-21.md), on every sample
            assertEquals("$id: the pattern the build declares (the only one it writes)", "FRAME_AND_DIAGONALS", raw.get("gridPattern").asString)
            assertEquals("$id: relation 1 - the camera's cells and the pattern's left-out cells are separate", emptySet<Int>(), cutout.toSet() intersect notRequired.toSet())
            assertEquals("$id: relation 2 - totalCells = gridCols x gridRows - cutoutCells - notRequiredCells", cols * rows - cutout.size - notRequired.size, required)
            assertEquals("$id: relation 4 - required cells are touched or untouched", required, raw.get("touchedCells").asInt + untouched.size)
            assertTrue("$id: an untouched cell is not a cut-out cell and not a left-out one", untouched.none { it in cutout || it in notRequired })
            val pattern = net.neuralcheck.domain.manual.TouchGridPattern.cells(cols, rows)
            assertEquals("$id: the left-out cells are the grid minus the pattern minus the camera - from the one source of the shape", (0 until cols * rows).filter { it !in pattern && it !in cutout }, notRequired)
            assertTrue("$id: no frame cell is left out (the server's one check on the pattern)", notRequired.none { it in net.neuralcheck.domain.manual.TouchGridPattern.frameCells(cols, rows) })
            assertEquals("$id: relation 5 - auto_complete exactly when every required cell was lit", raw.get("endReason").asString == "auto_complete", untouched.isEmpty())
            assertEquals("$id: the cell target is the 4 mm", 4.0, raw.get("cellTargetMm").asDouble, 0.0)
            for (s in strokes) {
                val n = s.getAsJsonArray("x").size()
                assertEquals("$id: dtMs, x, y and touchMajorPx have one length", listOf(n, n, n), listOf(s.getAsJsonArray("dtMs").size(), s.getAsJsonArray("y").size(), s.getAsJsonArray("touchMajorPx").size()))
                assertEquals("$id: the first dtMs is 0", 0L, s.getAsJsonArray("dtMs")[0].asLong)
                assertTrue("$id: an end the contract lists", s.get("end").asString in listOf("up", "cancel", "cancel_flagged", "open_at_end"))
            }
            for (judgement in listOf("warning", "percent", "pct", "threshold", "recommendedStatus", "verdict")) {
                assertFalse("the app ships no verdict about the grid ($judgement)", raw.has(judgement))
            }
            when {
                id.startsWith("11ch") -> {
                    assertEquals("pass", row.get("status").asString)
                    assertEquals("auto_complete", raw.get("endReason").asString)
                    assertEquals("the window 1080 x 2100 on the scaled mode: 18 columns of 60 px, (2100 - 2 x 60) / 60 = 33 rows, the strip 120 px", 18 to 33, cols to rows)
                    assertEquals("the frame (98) and the diagonals' cells beyond it (90): 188 of the 594 cells are required (tools-m02-shape-reference.js, and the server's own reader: tools-m02-golden-crosscheck.js)", 188, required)
                    assertEquals("every required cell is lit", 188, raw.get("touchedCells").asInt)
                    assertEquals("no camera: nothing is cut out", emptyList<Int>(), cutout)
                    assertEquals(594 - 188, notRequired.size)
                    assertEquals(emptyList<Int>(), untouched)
                    assertEquals("Samsung's FHD+ on a QHD+ panel: the scaled-mode candidate", "XDPI_MODE_SCALED", raw.get("densitySource").asString)
                    assertEquals(0, raw.getAsJsonArray("densityCheck").size())
                    assertFalse("an automatic completion shows no marks: the key is ABSENT", raw.has("displayedSuspects"))
                    assertEquals("not_available_api", raw.get("roundedCornersState").asString)
                    assertFalse(raw.has("roundedCornerRadiiPx"))
                    assertEquals("not_available_api", raw.get("resampledFlagState").asString)
                    assertEquals(true, raw.get("unbufferedDispatch").asBoolean)
                    assertFalse("nothing was absent", raw.has("absentFieldReasons"))
                    assertEquals(listOf("open_at_end"), strokes.map { it.get("end").asString })
                    assertEquals(false, cap.get("reached").asBoolean)
                    assertEquals(0, cap.get("pointsDropped").asInt)
                    assertTrue(raw.has("elapsedMs") && raw.has("timeToFirstTouchMs"))
                    assertEquals(1_730L, raw.get("timeToFirstTouchMs").asLong)
                }
                id.startsWith("13ch") -> {
                    assertEquals("fail", row.get("status").asString)
                    assertEquals("user_marked_not_working", raw.get("endReason").asString)
                    assertEquals("the notch (80 x 226 px at the top centre, 90 px into the grid) covers columns 8 and 9 of rows 0 and 1", listOf(8, 9, 26, 27), cutout)
                    assertEquals("the pattern's 196 less the camera's two cells that are in it (8 and 9, in the frame's top row; 26 and 27, in row 1, are inside a triangle: the pattern never asked for them)", 194, required)
                    assertEquals("97 of the 194 required cells were lit by the scripted strokes: rows 0-17 hold 98 of them and 96 were lit (not the two across the jumps); rows 18-34 hold the other 96 and only the palm's cell (row 33, column 1) was lit", 97, raw.get("touchedCells").asInt)
                    assertTrue("a partial run leaves cells untouched", untouched.isNotEmpty())
                    assertTrue(
                        "the REQUIRED cells across the two scripted jumps - the diagonal's cell on row 4 (column 9) and on row 6 (column 8) - were not filled between the jump's ends; the free cells there are not required, so not listed",
                        untouched.containsAll(listOf(4 * 18 + 15, 6 * 18 + 14))
                    )
                    assertFalse("the palm's cell (row 33, column 1) is lit - the raw records the palm, the server decides what it means", 33 * 18 + 1 in untouched)
                    assertEquals(listOf("up", "up", "cancel_flagged"), strokes.map { it.get("end").asString })
                    assertTrue("the palm's contact is the one big major axis", strokes[2].getAsJsonArray("touchMajorPx")[0].asFloat > 150f)
                    val suspects = raw.getAsJsonArray("displayedSuspects").map { it.asJsonObject }
                    assertEquals(listOf("gap_jump", "coord_jump", "edge_exit", "edge_exit"), suspects.map { it.get("kind").asString })
                    for (s in suspects) {
                        val stroke = strokes[s.get("strokeIndex").asInt]
                        val n = stroke.getAsJsonArray("x").size()
                        assertTrue("a mark points at recorded samples", s.get("fromPointIndex").asInt in 0 until n && s.get("toPointIndex").asInt in 0 until n)
                        assertEquals("a mark's keys are the contract's four", setOf("kind", "strokeIndex", "fromPointIndex", "toPointIndex"), s.keySet())
                    }
                    assertTrue("roundedCornerRadiiPx is written, not the state", raw.has("roundedCornerRadiiPx") && !raw.has("roundedCornersState"))
                    assertEquals(1, raw.getAsJsonArray("cutoutRectsPx").size())
                    assertFalse(cap.get("reached").asBoolean)
                    assertEquals(1, raw.get("maxSimultaneousPointers").asInt)
                }
                id.startsWith("16ch") -> {
                    assertEquals("not_tested", row.get("status").asString)
                    assertEquals("user_marked_not_tested", raw.get("endReason").asString)
                    assertEquals("the strip along row 5 crosses 18 cells; six of them are required (the frame's two and two of each diagonal) and were lit", 6, raw.get("touchedCells").asInt)
                    assertEquals("the pattern's 196 less the camera's two cells that are in it", 194, required)
                    assertEquals("the cap is reached and declared", true, cap.get("reached").asBoolean)
                    assertEquals(15_000, cap.get("maxPoints").asInt)
                    assertEquals(300, cap.get("pointsDropped").asInt)
                    assertEquals(15_000, strokes.single().getAsJsonArray("x").size())
                    assertEquals("the finger was still down", "open_at_end", strokes.single().get("end").asString)
                    assertTrue("pressed by the technician: the marks were shown, none seen", raw.has("displayedSuspects") && raw.getAsJsonArray("displayedSuspects").size() == 0)
                    val reasons = raw.getAsJsonObject("absentFieldReasons")
                    assertEquals(setOf("modePhysicalWidthPx", "modePhysicalHeightPx", "unbufferedDispatch"), reasons.keySet())
                    assertTrue(reasons.entrySet().all { it.value.asString.startsWith("read_failed:") })
                    assertFalse("an absent key is not written", raw.has("modePhysicalWidthPx") || raw.has("unbufferedDispatch"))
                    assertEquals("without the mode there is one candidate", "XDPI", raw.get("densitySource").asString)
                }
                else -> error("a variant this test does not know: $id")
            }
        }
    }

    @Test
    fun `M-09 and M-10 carry the volume run's raw of their variant - every endReason and both detectionSource values appear (fieldsVersion 71, contract 7-ي ف-04)`() {
        class Expect(val status: String, val reason: String, val source: String?, val total: Int, val expectedDowns: Int, val otherDowns: Int, val windowMs: Long, val levels: List<Int>?)
        val expected = mapOf(
            "11ch" to mapOf(
                "M-09" to Expect("pass", "auto_detected", "key_event", 2, 1, 0, 2_910, listOf(15, 7, 8)),
                "M-10" to Expect("pass", "auto_detected", "volume_level_change", 0, 0, 0, 2_460, listOf(15, 7, 6))
            ),
            "13ch" to mapOf(
                "M-09" to Expect("pass", "user_marked_working", null, 2, 0, 1, 9_700, listOf(15, 7, 6)),
                "M-10" to Expect("fail", "user_marked_not_working", null, 0, 0, 0, 12_400, listOf(15, 7, 7))
            ),
            "16ch" to mapOf(
                "M-09" to Expect("not_tested", "user_marked_not_tested", null, 0, 0, 0, 6_000, null),
                "M-10" to Expect("not_tested", "dismissed", null, 2, 0, 1, 3_000, listOf(15, 7, 8))
            )
        )
        val reasons = mutableSetOf<String>()
        val sources = mutableSetOf<String>()
        for (b in built) {
            val rows = root(b).getAsJsonArray("manualResults").map { it.asJsonObject }
            for ((testId, e) in expected.entries.first { b.variant.id.startsWith(it.key) }.value) {
                val row = rows.first { it.get("testId").asString == testId }
                val raw = row.getAsJsonObject("measurements")
                val id = "${b.variant.id} $testId"
                assertEquals("$id: the answer", e.status, row.get("status").asString)
                assertEquals("$id: the end reason", e.reason, raw.get("endReason").asString)
                assertEquals("$id: the key this row expects", if (testId == "M-09") 24 else 25, raw.get("expectedKeyCode").asInt)
                assertEquals("$id: events seen", e.total, raw.get("eventsTotal").asInt)
                assertEquals("$id: events listed", e.total, raw.getAsJsonArray("events").size())
                assertEquals("$id: presses of the expected key", e.expectedDowns, raw.get("expectedDownCount").asInt)
                assertEquals("$id: presses of the other key", e.otherDowns, raw.get("otherKeyDownCount").asInt)
                assertEquals("$id: how long the window stayed", e.windowMs, raw.get("windowMs").asLong)
                assertEquals("$id: detectionSource is written for auto_detected alone", e.source, if (raw.has("detectionSource")) raw.get("detectionSource").asString else null)
                if (e.expectedDowns == 0) {
                    assertFalse("$id: no expected press, no time (ABSENT, not 0)", raw.has("timeToFirstExpectedDownMs"))
                    assertFalse("$id: and no hold", raw.has("holdMs"))
                }
                if (e.levels == null) {
                    assertFalse("$id: no levels were read", raw.has("volumeStreamMax") || raw.has("volumeLevelAtOpen") || raw.has("volumeLevelAtEnd"))
                } else {
                    assertEquals("$id: the three levels", e.levels, listOf(raw.get("volumeStreamMax").asInt, raw.get("volumeLevelAtOpen").asInt, raw.get("volumeLevelAtEnd").asInt))
                }
                for (ev in raw.getAsJsonArray("events").map { it.asJsonObject }) {
                    assertEquals("$id: an event's keys are the contract's six", setOf("tMs", "keyCode", "action", "repeat", "deviceId", "source"), ev.keySet())
                }
                for (judgement in listOf("warning", "percent", "pct", "threshold", "recommendedStatus", "verdict")) {
                    assertFalse("the app ships no verdict about the key ($judgement)", raw.has(judgement))
                }
                reasons += raw.get("endReason").asString
                if (raw.has("detectionSource")) sources += raw.get("detectionSource").asString
            }
        }
        assertEquals("the samples show every endReason the contract lists", setOf("auto_detected", "user_marked_working", "user_marked_not_working", "user_marked_not_tested", "dismissed"), reasons)
        assertEquals("and both ways of detecting", setOf("key_event", "volume_level_change"), sources)
        val first = built.first { it.variant.id.startsWith("11ch") }
        val m9 = root(first).getAsJsonArray("manualResults").map { it.asJsonObject }.first { it.get("testId").asString == "M-09" }.getAsJsonObject("measurements")
        assertEquals("the first press, from the opening", 2_310L, m9.get("timeToFirstExpectedDownMs").asLong)
        assertEquals("from the first press to its up", 100L, m9.get("holdMs").asLong)
        assertEquals("no key event reached M-10's dialog: [] does not mean not pressed", 0, root(first).getAsJsonArray("manualResults").map { it.asJsonObject }
            .first { it.get("testId").asString == "M-10" }.getAsJsonObject("measurements").getAsJsonArray("events").size())
    }

    @Test
    fun `the conditions of the scan are declared - powerSaveState in the header and the root, the battery window and the brightness lock in the footer (fieldsVersion 71, contract 7-ي ف-05)`() {
        for (b in built) {
            val id = b.variant.id
            val header = root(b).getAsJsonObject("timeSeries").getAsJsonObject("header")
            val footer = footer(b)
            // the window the app applied: from the one object, never a copy on the server
            val window = footer.getAsJsonObject("batteryWindowPct")
            assertEquals("$id: the window's keys", setOf("minPct", "maxPct"), window.keySet())
            assertEquals(30, window.get("minPct").asInt)
            assertEquals(85, window.get("maxPct").asInt)
            when {
                id.startsWith("11ch") -> {
                    assertEquals("off", header.get("powerSaveState").asString)
                    assertFalse(header.get("isPowerSaveMode").asBoolean)
                    assertEquals("off", root(b).get("powerSaveState").asString)
                    assertFalse(root(b).get("powerSaveMode").asBoolean)
                    val lock = footer.getAsJsonObject("brightnessLock")
                    assertEquals("production attempts nothing today: the state alone", setOf("state"), lock.keySet())
                    assertEquals("not_attempted:value_not_decided", lock.get("state").asString)
                }
                id.startsWith("13ch") -> {
                    assertEquals("off", header.get("powerSaveState").asString)
                    val lock = footer.getAsJsonObject("brightnessLock")
                    assertEquals(setOf("state", "value", "method"), lock.keySet())
                    assertEquals("applied", lock.get("state").asString)
                    assertEquals(0.5f, lock.get("value").asFloat, 0f)
                    assertEquals("window_attribute", lock.get("method").asString)
                }
                id.startsWith("16ch") -> {
                    assertEquals("read_failed", header.get("powerSaveState").asString)
                    assertFalse("a failed read is not written as false", header.has("isPowerSaveMode"))
                    assertEquals("read_failed", root(b).get("powerSaveState").asString)
                    assertFalse("a failed read is not written as false", root(b).has("powerSaveMode"))
                    val lock = footer.getAsJsonObject("brightnessLock")
                    assertEquals("apply_failed:the window refused the brightness attribute", lock.get("state").asString)
                    assertEquals(0.4f, lock.get("value").asFloat, 0f)
                    assertEquals("window_attribute", lock.get("method").asString)
                }
                else -> error("a variant this test does not know: $id")
            }
            assertFalse("$id: nothing is named missing from a 71 file", footer.getAsJsonArray("fieldsNotInFile").any { it.asString in listOf("batteryWindowPct", "brightnessLock") })
        }
    }

    @Test
    fun `SN-04 carries the light sensor raw in two lists, and its status is what the app computed in fv68 (fieldsVersion 69, N-20)`() {
        // The app cannot tell a covered sensor from an uncovered one (S-32 (b)): both are PASS; only an all-zero run is WARNING.
        val expected = mapOf("11ch" to "pass", "13ch" to "pass", "16ch" to "warning")
        for (b in built) {
            val row = root(b).getAsJsonArray("functionalResults").map { it.asJsonObject }.first { it.get("testId").asString == "SN-04" }
            assertEquals("${b.variant.id}: the status", expected.entries.first { b.variant.id.startsWith(it.key) }.value, row.get("status").asString)
            val raw = row.getAsJsonObject("measurements")
            assertEquals(4_000L, raw.get("windowMs").asLong)
            val promptAt = raw.get("coverPromptAtMs").asLong
            assertEquals(1_000L, promptAt)
            val before = raw.getAsJsonArray("readingsBeforeCover")
            val during = raw.getAsJsonArray("readingsDuringCover")
            assertTrue("${b.variant.id}: both lists have readings", before.size() > 0 && during.size() > 0)
            assertTrue("every reading before the request is before it", before.all { it.asJsonObject.get("t").asLong < promptAt })
            assertTrue("every reading after the request is at or after it", during.all { it.asJsonObject.get("t").asLong >= promptAt })
            assertTrue("each reading is {t, values}", (before + during).all { it.asJsonObject.keySet() == setOf("t", "values") })
            assertFalse("the app sends no not_measured (S-32 (b))", row.toString().contains("not_measured"))
        }
    }

    @Test
    fun `batteryHealth recomputed from the real rows equals what the real device wrote - in both full-length samples`() {
        val real = JsonParser.parseReader(
            InputStreamReader(AppRequestPayloadGolden::class.java.getResourceAsStream(AppRequestPayloadGolden.REAL_SOURCE_RESOURCE)!!, Charsets.UTF_8)
        ).asJsonObject.getAsJsonObject("footer").getAsJsonObject("batteryHealth")
        // The producer (BatteryIndicators) over the REAL rows must give back the numbers the REAL device's own run produced.
        val recomputedKeys = listOf(
            "dischargeRateUnderLoadMa", "voltageStabilityMv", "batteryTempStartC", "batteryTempMaxC",
            "batteryTempRiseC", "levelDropPct", "dischargeVsDesignRatioMah", "designCapacityMah"
        )
        for (b in built.filter { it.variant.earlyStopDuringPhase5Pct == null }) {
            val got = footer(b).getAsJsonObject("batteryHealth")
            for (k in recomputedKeys) {
                assertEquals("${b.variant.id}: batteryHealth.$k", real.get(k).asDouble, got.get(k).asDouble, 0.0)
            }
        }
    }

    @Test
    fun `README names every footer key of the samples in its assembled-by table`() {
        val readme = readmeText()
        val footerKeys = allKeyPaths
            .filter { it.startsWith("timeSeries.footer.") && it.count { c -> c == '.' } == 2 && !it.contains('[') }
            .map { it.substringAfterLast('.') }
        val missing = footerKeys.filter { !Regex("^\\| `$it` \\|", RegexOption.MULTILINE).containsMatchIn(readme) }
        assertTrue("README.md's assembled-by table lacks these footer keys: $missing", missing.isEmpty())
    }

    @Test
    fun `README names every declared absence`() {
        val readme = readmeText()
        val missing = declaredAbsent.keys.filter { !readme.contains("`$it`") }
        assertTrue("README.md does not mention these declared absences: $missing", missing.isEmpty())
    }

    // ───────────────────────── 3: a key the builder can write must be in a sample ─────────────────────────

    /**
     * Keys the builder CAN write that none of the three samples shows, each with the reason it cannot (rule 25: the
     * absence is declared, never silent). An entry that a sample now shows FAILS the test below — remove it then.
     * A key is a full path for DTO fields and a leaf NAME for a key the builder writes into a map.
     */
    private val declaredAbsent: Map<String, String> = mapOf(
        "device.buildFingerprint" to "android.os.Build.* are null in a JVM unit test; the device round (stage 2) has them",
        "device.buildBoard" to "android.os.Build.* are null in a JVM unit test; the device round (stage 2) has them",
        "device.buildHardware" to "android.os.Build.* are null in a JVM unit test; the device round (stage 2) has them",
        "device.buildBootloader" to "android.os.Build.* are null in a JVM unit test; the device round (stage 2) has them",
        "timeSeries.footer.maxCpuTemp" to "forwarded only from a pre-68 file that carries it (LegacyFv67FileTest); an fv68 file never has it",
        "phase3CpuFreqRatioMid" to "always null in the V5 schema: the per-cluster frequency channels are gone from v[] " +
            "(StressEffectivenessCalculator.kt: the V5 note at :80-82, IDX_CPU_FREQ_MID / IDX_CPU_FREQ_LITTLE = -1 at :123-124), so the ratio collapses to null",
        "phase3CpuFreqRatioLittle" to "always null in the V5 schema, same reason as phase3CpuFreqRatioMid"
    )

    private val dtos: List<Pair<String, Class<*>>> = listOf(
        "" to BehaviorFingerprint::class.java,
        "device" to DeviceInfo::class.java,
        "device.deviceProfileKey" to DeviceProfileKey::class.java,
        "functionalResults[]" to FunctionalResultDto::class.java,
        "manualResults[]" to ManualResultDto::class.java,
        "manualResultsHeader" to ManualResultsHeaderDto::class.java,
        "timeSeries" to RawTimeSeriesPayload::class.java,
        "timeSeries.header" to TimeSeriesHeaderDto::class.java,
        "timeSeries.variables[]" to VariableDto::class.java,
        "timeSeries.samples[]" to TimeSeriesSampleDto::class.java,
        "timeSeries.footer" to TimeSeriesFooterDto::class.java
    )

    private fun instanceFields(c: Class<*>): List<Field> =
        c.declaredFields.filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }

    /** Every DTO field as a full key path. */
    private fun dtoKeyPaths(): List<String> =
        dtos.flatMap { (prefix, cls) -> instanceFields(cls).map { if (prefix.isEmpty()) it.name else "$prefix.${it.name}" } }

    /** The simple names of every class a DTO field's type mentions (generics included) inside the remote model package. */
    private fun referencedModelClasses(f: Field): List<Class<*>> {
        val out = ArrayList<Class<*>>()
        fun add(t: java.lang.reflect.Type) {
            when (t) {
                is Class<*> -> if (t.name.startsWith("net.neuralcheck.data.remote.model.")) out += t
                is ParameterizedType -> { add(t.rawType); t.actualTypeArguments.forEach { add(it) } }
                else -> Unit
            }
        }
        add(f.genericType)
        return out
    }

    private fun functionBody(text: String, signature: String): String {
        val start = text.indexOf(signature)
        check(start >= 0) { "cannot find `$signature`" }
        val open = text.indexOf('{', start)
        var depth = 0
        for (i in open until text.length) {
            if (text[i] == '{') depth++
            if (text[i] == '}') { depth--; if (depth == 0) return text.substring(start, i + 1) }
        }
        error("unbalanced braces after `$signature`")
    }

    /** The String values of the named constants of a names object (a name that is not a String, like `SINCE_68`, is no key). */
    private fun constantsOf(owner: Class<*>, names: Collection<String>): List<String> = names.mapNotNull {
        owner.getDeclaredField(it).apply { isAccessible = true }.get(null) as? String
    }

    /** The keys the builder (and the wire map it calls for the known-answer runs) writes into a map. */
    private fun builderMapKeys(): Set<String> {
        val builder = File(mainSources, "net/neuralcheck/data/remote/BehaviorFingerprintBuilder.kt").readText(Charsets.UTF_8)
        val knownAnswer = functionBody(
            File(mainSources, "net/neuralcheck/data/local/files/CpuKnownAnswer.kt").readText(Charsets.UTF_8),
            "fun toWireMap()"
        )
        val text = builder + "\n" + knownAnswer
        val keys = LinkedHashSet<String>()
        Regex("\"([A-Za-z][A-Za-z0-9_]*)\"\\s+to\\b").findAll(text).forEach { keys += it.groupValues[1] }
        Regex("\\bmap\\[\"([A-Za-z][A-Za-z0-9_]*)\"\\]").findAll(text).forEach { keys += it.groupValues[1] }
        Regex("\\bm\\[\"([A-Za-z][A-Za-z0-9_]*)\"\\]").findAll(text).forEach { keys += it.groupValues[1] }
        for ((owner, cls) in listOf(
            "VerificationWireNames" to VerificationWireNames::class.java,
            "CpuKnownAnswerNames" to CpuKnownAnswerNames::class.java
        )) {
            val names = Regex("\\b$owner\\.([A-Z][A-Z0-9_]*)").findAll(text).map { it.groupValues[1] }.toSet()
            keys += constantsOf(cls, names)
        }
        return keys
    }

    /** The key paths of the COMMITTED samples — the guard answers "is it in the sample", not "is it in what the chain builds today". */
    private val allKeyPaths: Set<String> by lazy {
        built.flatMapTo(sortedSetOf()) {
            GoldenJson.keyPaths(GoldenJson.parse(committed(AppRequestPayloadGolden.fileName(it.variant)).readText(Charsets.UTF_8)))
        }
    }
    private val allLeafNames: Set<String> by lazy { allKeyPaths.map { GoldenJson.leafName(it) }.toSet() }

    @Test
    fun `the DTO classes the guard walks are all the DTO classes the payload is made of`() {
        val known = dtos.map { it.second }.toSet()
        for ((_, cls) in dtos) for (f in instanceFields(cls)) {
            for (ref in referencedModelClasses(f)) {
                assertTrue(
                    "${cls.simpleName}.${f.name} references the DTO ${ref.simpleName}, which the key-coverage guard does not walk — " +
                        "add it to `dtos` in AppRequestPayloadGoldenTest (rule 25)",
                    ref in known
                )
            }
        }
    }

    @Test
    fun `every DTO field the payload can carry appears in a sample - or its absence is declared`() {
        val missing = dtoKeyPaths().filter { it !in allKeyPaths && it !in declaredAbsent }
        assertTrue(
            "These payload keys are in a DTO and in NO sample: $missing. Add a scenario that carries each to " +
                "AppRequestPayloadGolden (and regenerate in the same commit), or declare the absence with its reason in " +
                "`declaredAbsent` (rule 25).",
            missing.isEmpty()
        )
    }

    @Test
    fun `every key the builder writes into a map appears in a sample - or its absence is declared`() {
        val missing = builderMapKeys().filter { it !in allLeafNames && it !in declaredAbsent }.sorted()
        assertTrue(
            "These keys are written by BehaviorFingerprintBuilder (or the known-answer wire map) and appear in NO sample: " +
                "$missing. Add a scenario that carries each to AppRequestPayloadGolden (and regenerate in the same commit), " +
                "or declare the absence with its reason in `declaredAbsent` (rule 25).",
            missing.isEmpty()
        )
    }

    @Test
    fun `no declared absence is stale - each is still absent from every sample and still exists in the source`() {
        val stale = declaredAbsent.keys.filter { it in allKeyPaths || it in allLeafNames }
        assertTrue("These declared absences are now shown by a sample; remove them from `declaredAbsent`: $stale", stale.isEmpty())
        val inSource = dtoKeyPaths().toSet() + builderMapKeys()
        val gone = declaredAbsent.keys.filter { it !in inSource }
        assertTrue("These declared absences name a key the source no longer has; remove them: $gone", gone.isEmpty())
    }
    // ───────────────────────── 3b: the raw keys of the rows whose producer is real (batch 1.0.12) ─────────────────────────

    /**
     * The key paths of a `measurements` map written the way [GoldenJson.keyPaths] writes them for a sample: `a`, `a.b`, and
     * `readings[].t` for a key inside the maps of a list.
     */
    private fun measurementPaths(value: Any?, path: String = "", out: MutableSet<String> = sortedSetOf()): Set<String> {
        when (value) {
            is Map<*, *> -> for ((k, v) in value) {
                val p = if (path.isEmpty()) k as String else "$path.$k"
                out += p
                measurementPaths(v, p, out)
            }
            is List<*> -> for (e in value) if (e is Map<*, *> || e is List<*>) measurementPaths(e, "$path[]", out)
        }
        return out
    }

    /**
     * Rows whose `measurements` come from a REAL producer in the samples, each with the key paths that producer can write — read off
     * the producer's own OUTPUT with every optional key present (rule 16: the tool reads the system, no hand-kept list to drift):
     * L-02 ([L02Measurements.build] and [L02Measurements.providerOnly]) and the step sensors SN-08 / SN-09
     * ([SensorTests.stepSensorMeasurements]). Until batch 1.0.12 the guard walked the keys of the builder only, so a row hand-built
     * from four numbers carried four of L-02's nine keys and no sample row existed for SN-08 / SN-09 — and it passed.
     */
    private fun producerMeasurementPaths(): Map<String, Set<String>> {
        val everything = GnssObservation.Snapshot(callbacks = 1, visible = 1, used = 1, ttffMs = 1, locationUpdates = 1)
        val l02 = measurementPaths(
            L02Measurements.build(true, L02Measurements.CALLBACK_REGISTERED, L02Measurements.UPDATES_REQUESTED, everything)
        ) + measurementPaths(L02Measurements.providerOnly(true))
        val step = measurementPaths(SensorTests.stepSensorMeasurements(listOf(1L to floatArrayOf(1f))))
        return mapOf("L-02" to l02, "SN-08" to step, "SN-09" to step)
    }

    @Test
    fun `the guard reads the producers of L-02 and the step sensors - a vacuous pass is not a guard`() {
        val producers = producerMeasurementPaths()
        assertEquals(
            setOf(
                "gpsProviderEnabled", "gnssCallbackState", "locationUpdatesState", "satelliteStatusCallbacks", "visibleSatellites",
                "usedSatellites", "locationUpdateCount", "timeToFirstFixMs", "timeToFirstFixState"
            ),
            producers.getValue("L-02")
        )
        assertEquals(setOf("windowMs", "readings", "readings[].t", "readings[].values"), producers.getValue("SN-08"))
        assertEquals(producers.getValue("SN-08"), producers.getValue("SN-09"))
    }

    @Test
    fun `every key a real producer writes into the measurements of L-02, SN-08 and SN-09 appears in a sample row of that test - and no such row shows a key its producer does not write`() {
        // The COMMITTED samples, not the regenerated ones: the guard answers "is it in the sample".
        val rows: Map<String, List<JsonObject>> = built
            .flatMap { b ->
                GoldenJson.parse(committed(AppRequestPayloadGolden.fileName(b.variant)).readText(Charsets.UTF_8))
                    .asJsonObject.getAsJsonArray("functionalResults").map { it.asJsonObject }
            }
            .groupBy { it.get("testId").asString }
        val problems = ArrayList<String>()
        for ((testId, produced) in producerMeasurementPaths()) {
            val testRows = rows[testId].orEmpty()
            if (testRows.isEmpty()) {
                problems += "$testId has NO row in any committed sample, so none of its raw keys ($produced) is shown (rule 25; rule 22: an absent row is not a pass)"
                continue
            }
            val shown = testRows.flatMapTo(sortedSetOf<String>()) { r ->
                if (r.has("measurements")) GoldenJson.keyPaths(r.get("measurements")) else emptyList()
            }
            if ((produced - shown).isNotEmpty()) {
                problems += "These keys the producer of $testId writes are in NO sample row of $testId: ${produced - shown} (rule 25)"
            }
            if ((shown - produced).isNotEmpty()) {
                problems += "A sample row of $testId shows keys its producer does not write: ${shown - produced} - the row is not the producer's output"
            }
        }
        assertTrue(
            "The committed samples do not show what the real producers write (batch 1.0.12):\n  " + problems.joinToString("\n  ") +
                "\nAdd a scenario that drives the real producer to AppRequestPayloadGolden and regenerate in the same commit. $REGENERATE_COMMAND",
            problems.isEmpty()
        )
    }
}
