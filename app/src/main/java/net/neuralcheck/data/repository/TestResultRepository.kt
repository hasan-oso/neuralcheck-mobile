package net.neuralcheck.data.repository

import net.neuralcheck.data.local.db.entity.FunctionalResultEntity
import net.neuralcheck.domain.manual.ManualChipActions

interface TestResultRepository {
    suspend fun saveResults(results: List<FunctionalResultEntity>)

    /**
     * Overwrites any previous rows for the given (scanId, testId) pairs before
     * inserting. Use this when re-submitting manual test results — plain
     * [saveResults] appends because the entity's primary key auto-generates.
     */
    suspend fun upsertResults(results: List<FunctionalResultEntity>)

    suspend fun getResults(scanId: String): List<FunctionalResultEntity>
    suspend fun getResultsByCategory(scanId: String, category: String): List<FunctionalResultEntity>
}

/**
 * [entity], ready to be written over what is stored: the one place both manual-answer writers (`ManualTestsViewModel.setResult` and
 * `ReportViewModel.saveManualResults`) apply `ManualChipActions.rawToStore` — M7(ب) 2026-10-18, widened by E21 item 3 (approved 2026-10-15).
 *
 * Only an entity with no raw of its own, on a row in `ManualChipActions.TAP_KEEPS_STORED_RAW` (M-02, and since 2026-10-21 M-09 and M-10), reads the
 * store (one `getResults`), whatever answer it writes; every other entity is returned as it is, with no read — so no other row's write changes.
 * For those rows the stored raw (a grid run's, a volume run's) is carried onto the new row instead of being replaced by NULL, because
 * [TestResultRepository.upsertResults] deletes the old row first.
 */
suspend fun TestResultRepository.withStoredRawKept(entity: FunctionalResultEntity): FunctionalResultEntity {
    if (entity.measurements != null || !ManualChipActions.keepsStoredRaw(entity.testId)) return entity
    val stored = getResults(entity.scanId).firstOrNull { it.testId == entity.testId }
    return entity.copy(
        measurements = ManualChipActions.rawToStore(entity.testId, null, stored?.measurements)
    )
}
