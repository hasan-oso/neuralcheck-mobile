package net.neuralcheck.presentation.viewmodel

import net.neuralcheck.data.local.files.PreScanWarningsRead
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 1.7.D — Hilt-singleton bridge between [PreScanViewModel] and
 * [net.neuralcheck.service.stress.StressTestService] for pre-scan
 * warnings the user acknowledged-but-proceeded-through.
 *
 * Mirrors the [net.neuralcheck.data.remote.ScanSessionHolder] pattern
 * (mutable singleton instead of threading through every callsite). The
 * stress service reads this once at the top of `completeScan` and
 * folds the resulting list into `TimeSeriesFooter.preScanWarnings`,
 * so lab parsers know the scan was potentially compromised by hot
 * battery / recent unplug.
 *
 * Cleared by the stress service after the footer is built so a
 * subsequent scan doesn't inherit the previous one's warnings.
 *
 * Strings stored as stable identifiers (not localized text) so the
 * lab side can pattern-match without locale-specific substring checks.
 */
@Singleton
class PreScanWarningsHolder @Inject constructor() {

    companion object {
        const val WARN_HOT_BATTERY: String = "hot_battery"
        const val WARN_RECENTLY_UNPLUGGED: String = "recently_unplugged"
        // CHARGER_PLUGGED is a hard block — it never lands in the
        // holder because the scan never starts. The constant is
        // declared here for symmetry / future use if we ever soften it.
        const val WARN_CHARGER_PLUGGED: String = "charger_plugged"
    }

    @Volatile
    private var warnings: List<String> = emptyList()

    /**
     * fieldsVersion 68 (user decision 2026-10-08: "PreScanWarningsHolder: a third
     * state, not_written"): whether anyone called [set] since the last drain. An
     * empty list after a [set] is "the pre-scan check ran and placed no warning"
     * (PreScanScreen calls it with an empty list on a clean check); an empty list
     * with nothing written is "nobody put anything here" — and the two used to be
     * the same `[]`.
     */
    @Volatile
    private var written: Boolean = false

    fun set(values: List<String>) {
        warnings = values.toList()
        written = true
    }

    /** Drains the holder once: what was written (and whether anything was) and then forgets it. */
    fun readAndClear(): PreScanWarningsRead {
        val out = PreScanWarningsRead(written = written, warnings = warnings)
        warnings = emptyList()
        written = false
        return out
    }
}
