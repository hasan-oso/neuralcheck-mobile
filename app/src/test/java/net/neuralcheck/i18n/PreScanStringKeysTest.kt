package net.neuralcheck.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * fix/prescan-battery-gate: locks the pre-scan / scan-error message keys
 * into BOTH resource files (default = Arabic, values-tr = Turkish).
 *
 * Why a file-parsing test: `R.string.foo` references are compile-checked
 * against the DEFAULT file only — a key missing from values-tr silently
 * falls back to Arabic for Turkish users (exactly the report_pdf_* bug
 * fixed in the i18n round), and lint's MissingTranslation check is
 * disabled project-wide (see the lint block in app/build.gradle.kts).
 * This test is the guard lint would otherwise have provided, scoped to
 * the scan-funnel messages a field tester actually sees.
 *
 * Also asserts format-specifier parity between the two locales for every
 * listed key — a TR string whose placeholders diverge from the AR one
 * crashes with UnknownFormatConversionException for Turkish users only.
 */
class PreScanStringKeysTest {

    companion object {
        /**
         * Every message key the scan funnel (PreScanScreen +
         * PreScanViewModel + ActiveScanScreen's error dialog) resolves at
         * runtime. Additions to the dialog mapping in ActiveScanScreen
         * belong in this list.
         */
        private val REQUIRED_KEYS = listOf(
            // PreScanViewModel condition list
            "prescan_battery_level_fmt",
            "prescan_charger_connected_lbl",
            "prescan_charger_disconnected_lbl",
            "prescan_charger_status_fmt",
            "prescan_temperature_fmt",
            "prescan_uptime_fmt",
            "prescan_close_apps",
            "prescan_flat_surface",
            // PreScanScreen warnings dialog
            "prescan_instruction",
            "prescan_warn_title",
            "prescan_warn_charger",
            "prescan_warn_hot_fmt",
            "prescan_warn_hot_blocking_title",
            "prescan_warn_hot_blocking_body_fmt",
            "prescan_warn_hot_blocking_wait",
            "prescan_warn_hot_blocking_proceed",
            "prescan_warn_recently_unplugged_fmt",
            "prescan_start_confirm",
            // ActiveScanScreen error dialog — pre-flight rejections
            "scan_start_failed_title",
            "scan_start_failed_body_fmt",
            "scan_error_battery_too_low_fmt",
            "scan_error_battery_too_low_generic",
            "scan_error_device_too_hot",
            "scan_error_charger_connected",
            "scan_error_storage_full",
            "scan_error_ram_insufficient",
            // ActiveScanScreen error dialog — mid-scan SafetyMonitor stops
            "scan_safety_stop",
            "scan_safety_battery_temp",
            "scan_safety_cpu_temp",
            "scan_safety_battery_drain",
            "scan_safety_battery_low",
            // Denied-permissions notice (fieldsVersion 68, S-18): the label added with
            // ACTIVITY_RECOGNITION; Turkish approved by the user 2026-10-08.
            "scan_permission_label_activity_recognition"
        )

        /** Gradle runs unit tests with the module dir as working dir, but a
         *  root-project invocation is defended against too. */
        private fun resFile(relative: String): File {
            val fromModule = File(relative)
            if (fromModule.exists()) return fromModule
            val fromRoot = File("app", relative)
            check(fromRoot.exists()) {
                "strings file not found from '${File(".").absolutePath}': $relative"
            }
            return fromRoot
        }

        private fun loadKeys(relative: String): Map<String, String> {
            val doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(resFile(relative))
            val nodes = doc.getElementsByTagName("string")
            val out = mutableMapOf<String, String>()
            for (i in 0 until nodes.length) {
                val node = nodes.item(i)
                val name = node.attributes.getNamedItem("name")?.nodeValue ?: continue
                out[name] = node.textContent
            }
            return out
        }

        /** Positional conversions only; %% (escaped literal) is excluded. */
        private fun formatSpecs(value: String): List<String> =
            Regex("%(?:(\\d+)\\$)?([a-zA-Z%])").findAll(value)
                .filter { it.groupValues[2] != "%" }
                .map { it.value }
                .sorted()
                .toList()
    }

    private val arKeys = loadKeys("src/main/res/values/strings.xml")
    private val trKeys = loadKeys("src/main/res/values-tr/strings.xml")

    @Test
    fun `every prescan and scan-error key exists in the default (Arabic) file`() {
        val missing = REQUIRED_KEYS.filterNot { arKeys.containsKey(it) }
        assertTrue("missing from values/strings.xml: $missing", missing.isEmpty())
    }

    @Test
    fun `every prescan and scan-error key exists in the Turkish file`() {
        // This is the class of bug behind both the report_pdf_* Arabic
        // fallback in TR PDFs and the audio/input raw-key display: the
        // default file compiles fine while the TR copy silently lags.
        val missing = REQUIRED_KEYS.filterNot { trKeys.containsKey(it) }
        assertTrue("missing from values-tr/strings.xml: $missing", missing.isEmpty())
    }

    @Test
    fun `format specifiers agree between Arabic and Turkish`() {
        for (key in REQUIRED_KEYS) {
            val ar = arKeys[key] ?: continue
            val tr = trKeys[key] ?: continue
            assertEquals(
                "format-specifier mismatch for '$key' (AR='$ar' TR='$tr') — " +
                    "diverging placeholders crash getString for one locale only",
                formatSpecs(ar),
                formatSpecs(tr)
            )
        }
    }

    @Test
    fun `no listed key has a blank value in either locale`() {
        val blank = REQUIRED_KEYS.filter {
            arKeys[it]?.isBlank() == true || trKeys[it]?.isBlank() == true
        }
        assertTrue("blank values for: $blank", blank.isEmpty())
    }
}
