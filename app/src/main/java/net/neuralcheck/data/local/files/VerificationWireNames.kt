package net.neuralcheck.data.local.files

/**
 * The keys the three native verification producers write into
 * `verificationResults` (fieldsVersion 68, batch 3 part 2-b) — one source
 * (rule 24). The payload builder writes them, the tests read them, and a
 * rename is a change to ONE line here.
 *
 * Which of these are approved and which are the agent's [م]:
 *  - approved (N-10): `memoryVerifyState`, `memoryVerifyBytes`,
 *    `memoryMarchErrors`, `memoryWalkingOnesErrors`;
 *  - the server's own name, carried over (A76): `memoryRandomVerifyErrors`;
 *  - approved (N-09): `storageVerifyState`, `storageVerifyBytesCompared`,
 *    `storageVerifyDurationMs`, `storageVerifyMethod`;
 *  - approved by the user 2026-10-08: `cpuKnownAnswerState`,
 *    `cpuKnownAnswerRuns` (their element fields and status values are the
 *    coordinator's proposal, not the user's text);
 *  - everything else here is the agent's [م], listed in the commit that
 *    introduced it.
 *
 * Deliberately ABSENT: any `…Passed` key. The server derives those from the raw
 * counts (rule 20); `memoryMarchPassed` / `memoryWalkingOnesPassed` are names
 * the server already watches, and the app must never write them.
 */
object VerificationWireNames {

    // ── memory (T-17) ──
    const val MEMORY_VERIFY_STATE = "memoryVerifyState"
    const val MEMORY_VERIFY_BYTES = "memoryVerifyBytes"
    const val MEMORY_MARCH_ERRORS = "memoryMarchErrors"
    const val MEMORY_WALKING_ONES_ERRORS = "memoryWalkingOnesErrors"
    const val MEMORY_RANDOM_VERIFY_ERRORS = "memoryRandomVerifyErrors"
    const val MEMORY_VERIFY_BUFFER_BYTES = "memoryVerifyBufferBytes"
    const val MEMORY_VERIFY_TIME_CAP_MS = "memoryVerifyTimeCapMs"
    const val MEMORY_VERIFY_DURATION_MS = "memoryVerifyDurationMs"
    const val MEMORY_VERIFY_FREE_BYTES = "memoryVerifyFreeBytes"
    const val MEMORY_PHASE_WINDOWS = "memoryPhaseWindows"

    // ── storage (T-16) ──
    // (storageWriteVerifyErrors / storageReadVerifyErrors / storageVerifyState are
    // written by the builder under their existing literals.)
    const val STORAGE_VERIFY_BYTES_COMPARED = "storageVerifyBytesCompared"
    const val STORAGE_VERIFY_BYTES_PLANNED = "storageVerifyBytesPlanned"
    const val STORAGE_VERIFY_DURATION_MS = "storageVerifyDurationMs"
    const val STORAGE_VERIFY_METHOD = "storageVerifyMethod"

    // PhaseWindow's own keys, as written on the wire
    const val WINDOW_NAME = "name"
    const val WINDOW_START_MS = "startMs"
    const val WINDOW_END_MS = "endMs"

    /**
     * Every `verificationResults` key that exists only in files stamped >= 68 (the
     * explicit states and the producers' outputs). A file stamped below 68 cannot
     * carry them, so the payload built from it must not either: Gson fills a
     * missing key with the Kotlin default, and each default here is a claim
     * ("no V2 result", "MiB/s", ["gpuComputeGemm"] not run) that is false for a
     * scan that did run. See [FileShape].
     */
    val SINCE_68: List<String> = listOf(
        "gpuComputeGemmState",
        "criticalDefectInputsNotRun",
        "storageVerifyState",
        STORAGE_VERIFY_BYTES_COMPARED,
        STORAGE_VERIFY_BYTES_PLANNED,
        STORAGE_VERIFY_DURATION_MS,
        STORAGE_VERIFY_METHOD,
        "storageThroughputSegmentsUnit",
        "workersPinState",
        MEMORY_VERIFY_STATE,
        MEMORY_VERIFY_BYTES,
        MEMORY_MARCH_ERRORS,
        MEMORY_WALKING_ONES_ERRORS,
        MEMORY_RANDOM_VERIFY_ERRORS,
        MEMORY_VERIFY_BUFFER_BYTES,
        MEMORY_VERIFY_TIME_CAP_MS,
        MEMORY_VERIFY_DURATION_MS,
        MEMORY_VERIFY_FREE_BYTES,
        MEMORY_PHASE_WINDOWS,
        CpuKnownAnswerNames.STATE,
        CpuKnownAnswerNames.RUNS,
        CpuKnownAnswerNames.TOTAL_MS_BY_PHASE
    )

    /** Names the app must NOT write — the server derives them from the raw counts. */
    val SERVER_DERIVED_NAMES: Set<String> = setOf(
        "memoryMarchPassed",
        "memoryWalkingOnesPassed"
    )
}
