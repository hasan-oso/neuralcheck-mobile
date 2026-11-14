package net.neuralcheck.service.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import net.neuralcheck.data.local.prefs.SecurePreferences
import net.neuralcheck.data.repository.ScanRepository
import java.util.concurrent.TimeUnit

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scanRepository: ScanRepository,
    private val firebaseSyncService: FirebaseSyncService,
    private val securePreferences: SecurePreferences,
    // feat/technician-evaluation: retry path for unsent evaluations —
    // a network drop at submission time must never lose a label.
    private val evaluationSubmitter: net.neuralcheck.domain.evaluation.EvaluationSubmitter
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!securePreferences.syncEnabled) return Result.success()

        return try {
            val unsynced = scanRepository.getUnsyncedScans()
            var failures = 0
            for (scan in unsynced) {
                val result = firebaseSyncService.uploadScan(scan)
                if (result.isSuccess) {
                    scanRepository.markSynced(scan.scanId)
                } else {
                    failures++
                }
            }
            // feat/technician-evaluation: flush any technician evaluations
            // that failed their first (fire-and-forget) submission. Rows
            // keep submitted=false until the callable succeeds, so this is
            // naturally idempotent; failures here don't force a worker
            // retry on their own — the next periodic/one-shot cycle picks
            // them up.
            runCatching { evaluationSubmitter.submitPendingBlocking() }
            if (failures > 0 && runAttemptCount < 3) Result.retry() else Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}

object SyncScheduler {
    private const val PERIODIC_NAME = "neuralcheck_sync_periodic"
    private const val ONESHOT_NAME = "neuralcheck_sync_oneshot"

    fun schedule(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Triggers an immediate sync — uses any network (includes mobile data). */
    fun syncNow(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONESHOT_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
