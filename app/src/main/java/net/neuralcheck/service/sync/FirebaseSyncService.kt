package net.neuralcheck.service.sync

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import net.neuralcheck.data.local.db.entity.ScanEntity
import net.neuralcheck.data.local.db.entity.ScanScoreEntity
import net.neuralcheck.data.repository.ScoreRepository
import net.neuralcheck.data.repository.TechnicianRepository
import net.neuralcheck.data.repository.PeerRepository
import net.neuralcheck.data.repository.TestResultRepository
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Uploads a scan to Firebase:
 * - Time-series .json.gz file → Cloud Storage at scans/{deviceFp}/{scanId}.ts.json.gz
 * - Scan metadata, score, results → Firestore at scans/{scanId}
 */
@Singleton
class FirebaseSyncService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storage: FirebaseStorage,
    private val firestore: FirebaseFirestore,
    private val scoreRepository: ScoreRepository,
    private val testResultRepository: TestResultRepository,
    private val technicianRepository: TechnicianRepository,
    private val peerRepository: PeerRepository
) {
    companion object {
        private const val TAG = "NeuralCheck.Sync"
    }

    private val gson = Gson()

    suspend fun uploadScan(scan: ScanEntity): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. Upload time-series file (if present)
            var storageUrl: String? = null
            scan.timeSeriesFilePath?.let { path ->
                val file = File(path)
                if (file.exists()) {
                    val ref = storage.reference.child("scans/${scan.deviceFingerprint}/${scan.scanId}.ts.json.gz")
                    ref.putFile(android.net.Uri.fromFile(file)).await()
                    storageUrl = ref.path
                }
            }

            // 2. Gather all related data
            val score = scoreRepository.getByScanId(scan.scanId)
            val results = testResultRepository.getResults(scan.scanId)
            val techInput = technicianRepository.getByScanId(scan.scanId)
            val peerInput = peerRepository.getByScanId(scan.scanId)

            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

            // 3. Write metadata to Firestore. The userId field is the same
            // ownership marker `reports/{scanId}` carries (set by the
            // analyzeDevice Cloud Function from `request.auth.uid`); writing
            // it on the client side as well lets Firestore rules + history
            // queries filter scans by owner without consulting reports/.
            // Prefer the row's stored userId so a scan that started as
            // anonymous (e.g. customer-device session) doesn't get
            // back-stamped to whoever happens to be signed in at upload time.
            val ownerUid = scan.userId ?: Firebase.auth.currentUser?.uid
            val payload = mutableMapOf<String, Any?>(
                "scanId" to scan.scanId,
                "userId" to ownerUid,
                "deviceFingerprint" to scan.deviceFingerprint,
                "scanType" to scan.scanType,
                "linkedScanId" to scan.linkedScanId,
                "timestamp" to scan.timestamp,
                "batteryLevelStart" to scan.batteryLevelStart,
                "batteryLevelEnd" to scan.batteryLevelEnd,
                "ambientTempEstimate" to scan.ambientTempEstimate,
                "appVersion" to scan.appVersion,
                "androidApiLevel" to scan.androidApiLevel,
                "deviceModel" to scan.deviceModel,
                "androidVersion" to scan.androidVersion,
                "isComplete" to scan.isComplete,
                "earlyStopReason" to scan.earlyStopReason,
                "earlyStopAtMs" to scan.earlyStopAtMs,
                "timeSeriesStorageUrl" to storageUrl,
                "isPowerSaveMode" to powerManager.isPowerSaveMode,
                "uploadedAt" to System.currentTimeMillis()
            )

            score?.let {
                // Order 2026-09-24 (القرار ٧٦، البند ١): "حين تكون الدرجة
                // null يُحذف حقل overallScore من الحمولة. لا يُكتب null ولا
                // 0." — overallScore is now nullable (V7 never has one);
                // the key is simply OMITTED from the payload when absent,
                // never written as a literal `null` or a fabricated `0`.
                // `.set(payload)` on a fresh doc leaves the field entirely
                // absent; on an existing doc it leaves whatever was there
                // unchanged (never seen for a scanId — one upload per scan)
                // rather than clobbering a real prior value with nothing.
                it.overallScore?.let { s -> payload["overallScore"] = s }
                payload["overallGrade"] = it.overallGrade
                payload["axisScores"] = it.axisScoresJson
                payload["categoryScores"] = it.categoryScoresJson
                payload["criticalFailures"] = it.criticalFailuresJson
                payload["antiTamperPenalty"] = it.antiTamperPenalty
                payload["isIncomplete"] = it.isIncomplete
                payload["scoringMethod"] = it.scoringMethod
            }

            payload["functionalResults"] = gson.toJson(results)
            techInput?.let { payload["technicianInput"] = gson.toJson(it) }
            peerInput?.let { payload["peerInput"] = gson.toJson(it) }

            firestore.collection("scans").document(scan.scanId).set(payload).await()

            Log.i(TAG, "Uploaded scan ${scan.scanId} successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upload scan ${scan.scanId}: ${e.message}", e)
            Result.failure(e)
        }
    }
}
