package net.neuralcheck.data.local.files

import net.neuralcheck.util.ReasonText

/**
 * fieldsVersion 68 (T-18 row 11): where a payload's time series came from, as
 * the one state that is ALWAYS present ([net.neuralcheck.data.remote.model.RawTimeSeriesPayload.timeSeriesState]).
 * The literals live here once (rule 24).
 */
object TimeSeriesStates {

    /** The `.ts.json.gz` was read: header, variables, samples and footer are the file's. */
    const val FILE = "file"

    /**
     * Only the footer-only backup was recovered (the main file was missing or
     * unreadable): the footer is real, the header and samples are not available
     * and are ABSENT — not the 0 / "" placeholders the recovery object holds.
     * Agent's name, not yet approved.
     */
    const val FOOTER_BACKUP = "footer_backup"

    /** Prefix of `no_file:<reason>`: there is no time series at all. */
    const val NO_FILE_PREFIX = "no_file:"

    /** Reason when the reader returned null without throwing (it logs, and does not tell why). */
    const val REASON_READER_RETURNED_NULL =
        "the reader found neither the time-series file nor a readable footer backup"

    /** Reason when the builder was called without a time series and without a reason. */
    const val REASON_NOT_PROVIDED = "no time series was provided to the payload builder"

    /** `no_file:<reason>`, the whole state capped at 200 characters (S-35). */
    fun noFile(reason: String?): String =
        ReasonText.cap(NO_FILE_PREFIX + (reason?.takeIf { it.isNotBlank() } ?: REASON_NOT_PROVIDED))
}
