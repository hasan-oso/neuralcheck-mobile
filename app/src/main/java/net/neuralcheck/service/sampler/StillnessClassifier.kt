package net.neuralcheck.service.sampler

import net.neuralcheck.data.local.files.StillnessReport
import net.neuralcheck.data.local.files.StillnessStates

/**
 * fieldsVersion 67 — pure decision behind footer.scanStillness.isStill.
 * Extracted from StressTestService (pattern: CpuUsageProvenance,
 * BatteryChannelSanitizer) so the threshold and its sentinel handling are
 * unit-testable without an Android runtime.
 *
 * ── Why the threshold moved 0.1 → 0.5 m/s² ──
 *
 * Phase 1.7.E set 0.1 from HAR-literature figures for "stationary"
 * classification. Those come from filtered, high-rate captures of an
 * undisturbed device. This signal is different in every respect: it is the
 * population std-dev of the raw accelerometer MAGNITUDE (gravity included)
 * sampled at 1–10 Hz over a full 8-minute run, while our own stress
 * kernels drive the device thermally and electrically. Its noise floor on
 * a motionless phone is an order of magnitude above 0.1.
 *
 * Measured floor — 7 scans, 3 models, device flat on a table throughout
 * (Honor MTN-NX1, Samsung Galaxy S10 5G, Samsung Galaxy A51; fv 64–66):
 *
 *     0.2515  0.2526  0.2540  0.2609  0.3376  0.3518  0.3723
 *     mean 0.2972   sd 0.0540   max 0.3723
 *
 * All seven were reported isStill=false under 0.1 — the flag fired on
 * 100 % of correctly-placed scans and therefore carried no information.
 *
 * [STD_DEV_THRESHOLD] = mean + 3·sd = 0.459, rounded up to 0.5: 34 %
 * headroom over the highest motionless scan, and far below the 1.5–4 m/s²
 * that walking or hand-carrying produces on this same statistic.
 *
 * ── What the flag can and cannot say ──
 *
 * A hand-held S10 5G was recorded at 0.35 during Phase 1.6 validation —
 * inside the motionless range above. Held-steady and table-flat are NOT
 * separable by this statistic. isStill=true therefore means only "no
 * gross motion (carrying, walking, shaking) during the scan" — the
 * property the motion-correlated metrics (battery-current spikes, fps
 * jitter) actually depend on. It must not be read as "the device was on a
 * flat surface".
 */
object StillnessClassifier {

    /** Motion threshold on the accel-magnitude population std-dev, m/s². */
    const val STD_DEV_THRESHOLD: Float = 0.5f

    /**
     * @param accelStdDev population std-dev of |accel|, MEASURED (>= 0).
     * @return true when no gross motion was detected. fieldsVersion 68: there
     *         is no longer a "negative sentinel = still" branch — a standard
     *         deviation that could not be computed is not a number at all
     *         (rule 22), see [report].
     */
    fun isStill(accelStdDev: Float): Boolean = accelStdDev <= STD_DEV_THRESHOLD

    /**
     * The footer's stillness record (fieldsVersion 68, ت-18 row 6, N-13).
     * [accelStdDev] is `null` when fewer than 2 readings existed: then NOTHING
     * is claimed about motion — `isStill` and `accelStdDev` are absent and the
     * state says `insufficient_accel_samples`; before, that case wrote
     * `isStill = true` beside `accelStdDev = -1`, i.e. absence read as stillness.
     */
    fun report(accelStdDev: Float?, sampleCount: Int): StillnessReport =
        if (accelStdDev == null) {
            StillnessReport(
                isStill = null,
                accelStdDev = null,
                sampleCount = sampleCount,
                stillnessState = StillnessStates.INSUFFICIENT_ACCEL_SAMPLES
            )
        } else {
            StillnessReport(
                isStill = isStill(accelStdDev),
                accelStdDev = accelStdDev,
                sampleCount = sampleCount,
                stillnessState = StillnessStates.MEASURED
            )
        }
}
