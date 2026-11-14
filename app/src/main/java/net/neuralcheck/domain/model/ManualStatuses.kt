package net.neuralcheck.domain.model

/**
 * The four answers a MANUAL test (M-xx) can hold — the one source of these
 * literals (fieldsVersion 69, rule 24; contract docs/FV68_CONTRACT.md section
 * 7-h, S-15): they are stored in `functional_results.status`, shown by the
 * manual-tests screens and shipped as `manualResults[].status`.
 *
 *  - [PASS] / [FAIL]: the technician says the part works / does not work.
 *  - [NOT_TESTED]: the technician did not test it («لم أختبر»).
 *  - [UNAVAILABLE]: the technician says the device does NOT HAVE the part
 *    (the one string `manual_mark_unavailable`). Until fieldsVersion 68 this answer was stored
 *    as [NOT_TESTED] (five dialog buttons), so the two were indistinguishable;
 *    from 69 they are not.
 *
 * All four are known to [TestStatus.fromString] (so none falls to `ERROR`);
 * `ManualStatusesTest` proves it for every value.
 */
object ManualStatuses {
    const val PASS = "pass"
    const val FAIL = "fail"
    const val NOT_TESTED = "not_tested"
    const val UNAVAILABLE = "unavailable"

    val ALL: List<String> = listOf(PASS, FAIL, NOT_TESTED, UNAVAILABLE)

    /**
     * The individual score a manual answer carries: 100 for [PASS], 0 for [FAIL],
     * and `null` for the other two — a skipped test or a part the device does not
     * have is IGNORED by the scoring layer, never a 0 (rule 22). One function for
     * the two screens that write manual rows (it used to be a copy in each).
     */
    fun scoreOf(status: String): Float? = when (status) {
        PASS -> 100f
        FAIL -> 0f
        else -> null
    }
}
