package net.neuralcheck.hygiene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The build's own stamp and version describe data only at the moment it is
 * WRITTEN (fieldsVersion 68, batch 3 part 2-b, decision "legacy file": the
 * stamp describes the source of the samples, not the app that sends them).
 *
 * A payload built from a file written by an older build must carry that file's
 * stamp and appVersion, so no code on the read / send side may put this build's
 * `BuildConfig.FIELDS_VERSION` or `BuildConfig.VERSION_NAME` onto data. This
 * pins the sites that use them (measured with a scan of app/src/main, comments
 * excluded). A new site is not forbidden — it must be looked at, and this list
 * updated with the reason.
 */
class BuildStampSitesTest {

    private val mainSourceRoot: File = listOf("app/src/main/java", "../app/src/main/java")
        .map(::File).firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun usesOf(token: String): Set<String> =
        mainSourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { f ->
                f.readLines().any { line ->
                    val code = line.trim()
                    token in code && !code.startsWith("*") && !code.startsWith("//") && !code.startsWith("/*")
                }
            }
            .map { it.name }
            .toSortedSet()

    @Test
    fun `the guard can see the sources it protects`() {
        assertTrue(mainSourceRoot.walkTopDown().count { it.extension == "kt" } > 50)
        assertTrue("StressTestService.kt must be found", usesOf("BuildConfig.FIELDS_VERSION").isNotEmpty())
    }

    @Test
    fun `the stamp is written into a file in exactly one place - the scan writer`() {
        assertEquals(
            "BuildConfig.FIELDS_VERSION may only be written at scan time (StressTestService)",
            setOf("StressTestService.kt"),
            usesOf("BuildConfig.FIELDS_VERSION")
        )
    }

    @Test
    fun `the app version is read at scan time, in the export envelope and on screens - never in the payload path`() {
        val sites = usesOf("BuildConfig.VERSION_NAME")
        assertEquals(
            // StressTestService: the scan entity and the file header at scan time.
            // ExportDataUseCase: the export zip's metadata.json envelope (the exported
            //   .ts.json.gz files are copied verbatim, with their own header).
            // ProfileScreen / SettingsScreen: the "about" text.
            setOf("ExportDataUseCase.kt", "ProfileScreen.kt", "SettingsScreen.kt", "StressTestService.kt"),
            sites
        )
        val payloadPath = mainSourceRoot.resolve("net/neuralcheck/data/remote")
        val offenders = payloadPath.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filter { f -> f.readText().let { "BuildConfig.VERSION_NAME" in it || "BuildConfig.FIELDS_VERSION" in it } }
            .map { it.name }.toList()
        assertEquals("the payload path takes both from the file it reads", emptyList<String>(), offenders)
    }
}
