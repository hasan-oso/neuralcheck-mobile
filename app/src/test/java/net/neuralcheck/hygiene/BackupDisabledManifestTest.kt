package net.neuralcheck.hygiene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * User decision 2026-10-09 (item 6, order to the app agent): "backup is blocked in 1.0.11
 * (allowBackup=false, or dataExtractionRules that exclude everything), because the app runs on
 * the customer's phone." The app must not leave the device through Auto Backup (cloud) or a
 * device-to-device transfer, on Android 11 and lower AND on Android 12 and higher.
 *
 * What the official documentation says, and so what this test pins (each point's source is the
 * page named; fetched 2026-10-09):
 *  - `android:allowBackup="false"` — "no backup or restore of the application is ever performed,
 *    disabling all cloud backups and device-to-device (D2D) transfers, even by a full-system
 *    backup that otherwise causes all application data to save using `adb`"
 *    (developer.android.com/guide/topics/manifest/application-element). That is the whole answer
 *    for Android 11 and lower.
 *  - BUT, same page, for an app TARGETING Android 12 (API 31) or higher: "On devices from some
 *    device manufacturers, you can't disable device-to-device migration of your app's files",
 *    while "you can disable cloud-based backup and restore of your app's files by setting this
 *    attribute to false". This app targets 35, so allowBackup="false" alone is NOT enough for D2D.
 *  - Android 12 behaviour-changes page (developer.android.com/about/versions/12/behavior-changes-12,
 *    "Backup and restore"): D2D rules are given in the new `data-extraction-rules` format, pointed
 *    to by `android:dataExtractionRules`; once it is pointed to, `android:fullBackupContent` "is
 *    ignored on devices running Android 12 or higher", and it "is still required for devices
 *    running Android 11 or lower".
 *  - Auto Backup page (developer.android.com/identity/data/autobackup, "XML config syntax"): "If
 *    there are no rules for a particular backup mode, such as if the `<device-transfer>` section
 *    is missing, that mode is fully enabled for all content except for `no-backup` and `cache`
 *    directories" — so every section is written out, and each excludes every domain; `<exclude>`
 *    "takes precedence" over `<include>`, and there is no `<include>` anywhere.
 *
 * So the manifest carries all three: allowBackup="false" (Android 11 and lower, cloud on every
 * version), `android:fullBackupContent` excluding everything (the Android-11-and-lower mechanism,
 * belt and braces for a manufacturer that ignores allowBackup there), and
 * `android:dataExtractionRules` excluding everything in every section (Android 12 and higher).
 *
 * NOT covered, because no document covers it and nothing was run on a device: what a particular
 * manufacturer's own transfer tool (Smart Switch and the like) does with an app that opted out
 * through all three; and the `<cross-platform-transfer>` mode (Android 16 QPR2+, iOS only), which
 * needs `<platform-specific-params>` of a counterpart app this project does not have, and whose
 * MISSING section the page does not say is closed — so a section other than the two above fails
 * this test as "unreviewed" until someone decides.
 *
 * Reading the manifest: the unit-test task of each variant depends on that variant's manifest
 * task and passes the FINAL merged manifest's path in `nc.mergedManifest` (app/build.gradle.kts),
 * so a library manifest that added a backup attribute would be seen here. No property, or no
 * file at that path, FAILS the test (rule 22) — it never silently reads nothing.
 */
class BackupDisabledManifestTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    /** The five `domain` values of the OLD format (`<full-backup-content>`, Android 11 and lower) - the Android 12 behaviour page's syntax line. */
    private val oldDomains = listOf("root", "file", "database", "sharedpref", "external")

    /**
     * The nine `domain` values of the NEW format (`<data-extraction-rules>`), from the autobackup page's
     * "XML config syntax, Android 12 or higher". The lint bundled with AGP 8.2.2 knows only the five old ones and reports the four
     * device-protected ones as [FullBackupContent] errors in both files (measured 2026-10-09); the documentation is followed, and
     * data_extraction_rules.xml says tools:ignore="FullBackupContent" for that reason. This app uses no device-protected storage.
     */
    private val newDomains = oldDomains + listOf("device_root", "device_file", "device_database", "device_sharedpref")

    private val appModuleDir: File = listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")
        .map(::File).firstOrNull { it.isFile }
        ?.absoluteFile?.let { it.parentFile.parentFile.parentFile }
        ?: error("could not locate the app module from ${File(".").absolutePath}")

    private fun parse(file: File): Document {
        assertTrue("file not found: ${file.path}", file.isFile)
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // The inputs are this repository's own XML; still, no external entities.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        return factory.newDocumentBuilder().parse(file)
    }

    private fun parseText(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
    }

    /** null = the attribute is ABSENT (never read as a value). */
    private fun attr(el: Element, name: String): String? =
        if (el.hasAttributeNS(androidNs, name)) el.getAttributeNS(androidNs, name) else null

    private fun mergedManifest(): File {
        val path = System.getProperty("nc.mergedManifest")
        assertNotNull(
            "system property nc.mergedManifest is not set: this test must run through the Gradle unit-test task " +
                "of a variant (app/build.gradle.kts wires it) — it does not guess a manifest",
            path
        )
        assertFalse("nc.mergedManifest is blank", path!!.isBlank())
        return File(path)
    }

    private fun applicationOf(doc: Document): Element {
        val apps = doc.getElementsByTagName("application")
        assertEquals("a manifest has exactly one <application>", 1, apps.length)
        return apps.item(0) as Element
    }

    // ───────────────────────── the merged manifest ─────────────────────────

    @Test
    fun `the merged manifest of this variant is the app's own and turns backup off on every path`() {
        val manifest = mergedManifest()
        val app = applicationOf(parse(manifest))

        assertEquals(
            "this must be the app's manifest, not some other file (${manifest.path})",
            "net.neuralcheck.NeuralCheckApp",
            attr(app, "name")
        )
        assertEquals(
            "android:allowBackup must be present and false: Android 11 and lower, and cloud backup on every version",
            "false",
            attr(app, "allowBackup")
        )
        assertEquals(
            "android:dataExtractionRules must point to the exclude-everything rules: Android 12+ D2D, which allowBackup=false " +
                "alone does not guarantee for an app targeting 31+ on some manufacturers' devices",
            "@xml/data_extraction_rules",
            attr(app, "dataExtractionRules")
        )
        assertEquals(
            "android:fullBackupContent must point to the exclude-everything rules: the Android 11 and lower mechanism",
            "@xml/backup_rules",
            attr(app, "fullBackupContent")
        )
        assertEquals(
            "no backup agent may be declared (a custom agent would put its own data into a backup)",
            null,
            attr(app, "backupAgent")
        )
        assertTrue(
            "fullBackupOnly=true would opt the app INTO Auto Backup",
            attr(app, "fullBackupOnly").let { it == null || it == "false" }
        )
    }

    // ───────────────────────── the two rule files ─────────────────────────

    /** Problems with a rules document; empty = it excludes every domain in every given section and includes nothing. */
    private fun problemsOf(doc: Document, root: String, sections: List<String>?, domains: List<String>): List<String> {
        val problems = mutableListOf<String>()
        val top = doc.documentElement
        if (top.tagName != root) {
            return listOf("root element is <${top.tagName}>, expected <$root>")
        }
        fun excludesEveryDomain(scope: Element, label: String) {
            val children = (0 until scope.childNodes.length).map { scope.childNodes.item(it) }.filterIsInstance<Element>()
            for (c in children) {
                if (c.tagName != "exclude") problems += "$label has a <${c.tagName}> (only <exclude> is allowed here)"
            }
            val excluded = children.filter { it.tagName == "exclude" }
                .filter { it.getAttribute("path") == "." }
                .map { it.getAttribute("domain") }
            for (d in domains) {
                if (d !in excluded) problems += "$label does not exclude domain=\"$d\" path=\".\""
            }
            for (e in children.filter { it.tagName == "exclude" }) {
                if (e.getAttribute("path") != ".") problems += "$label has an <exclude> with path=\"${e.getAttribute("path")}\" (a partial exclusion)"
                if (e.getAttribute("domain") !in domains) problems += "$label has an <exclude> with an unknown domain=\"${e.getAttribute("domain")}\""
            }
        }
        if (sections == null) {
            excludesEveryDomain(top, "<$root>")
        } else {
            val children = (0 until top.childNodes.length).map { top.childNodes.item(it) }.filterIsInstance<Element>()
            for (s in sections) {
                val found = children.filter { it.tagName == s }
                if (found.size != 1) {
                    problems += "<$root> must have exactly one <$s> (a missing section means that mode is fully enabled); found ${found.size}"
                } else {
                    excludesEveryDomain(found[0], "<$s>")
                }
            }
            for (c in children) if (c.tagName !in sections) problems += "<$root> has an unreviewed section <${c.tagName}>"
        }
        return problems
    }

    @Test
    fun `data extraction rules (Android 12 and higher) exclude everything in every transfer mode`() {
        val file = File(appModuleDir, "src/main/res/xml/data_extraction_rules.xml")
        assertEquals(
            emptyList<String>(),
            problemsOf(parse(file), "data-extraction-rules", listOf("cloud-backup", "device-transfer"), newDomains)
        )
    }

    @Test
    fun `full backup rules (Android 11 and lower) exclude everything`() {
        val file = File(appModuleDir, "src/main/res/xml/backup_rules.xml")
        assertEquals(emptyList<String>(), problemsOf(parse(file), "full-backup-content", null, oldDomains))
    }

    @Test
    fun `the checker itself reports a missing section, a missing domain, a partial path and an include`() {
        val allDomains = newDomains.joinToString("") { """<exclude domain="$it" path="."/>""" }
        val good = """<data-extraction-rules><cloud-backup>$allDomains</cloud-backup><device-transfer>$allDomains</device-transfer></data-extraction-rules>"""
        assertEquals(
            "a document with the two sections excludes everything in them",
            emptyList<String>(),
            problemsOf(parseText(good), "data-extraction-rules", listOf("cloud-backup", "device-transfer"), newDomains)
        )
        val noDeviceTransfer = """<data-extraction-rules><cloud-backup>$allDomains</cloud-backup></data-extraction-rules>"""
        assertTrue(
            problemsOf(parseText(noDeviceTransfer), "data-extraction-rules", listOf("cloud-backup", "device-transfer"), newDomains)
                .any { "device-transfer" in it && "fully enabled" in it }
        )
        val missingDomain = good.replace("""<exclude domain="device_sharedpref" path="."/>""", "")
        assertTrue(
            problemsOf(parseText(missingDomain), "data-extraction-rules", listOf("cloud-backup", "device-transfer"), newDomains)
                .any { "device_sharedpref" in it }
        )
        val partial = good.replace("""<exclude domain="file" path="."/>""", """<exclude domain="file" path="reports"/>""")
        assertTrue(
            problemsOf(parseText(partial), "data-extraction-rules", listOf("cloud-backup", "device-transfer"), newDomains)
                .any { "partial exclusion" in it }
        )
        val withInclude = good.replace("<cloud-backup>", """<cloud-backup><include domain="file" path="."/>""")
        assertTrue(
            problemsOf(parseText(withInclude), "data-extraction-rules", listOf("cloud-backup", "device-transfer"), newDomains)
                .any { "<include>" in it }
        )
    }
}
