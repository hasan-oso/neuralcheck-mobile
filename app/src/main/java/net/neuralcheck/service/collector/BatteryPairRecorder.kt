package net.neuralcheck.service.collector

import android.content.Intent
import android.os.BatteryManager
import net.neuralcheck.data.local.files.BatteryPair
import net.neuralcheck.data.local.files.BatteryPairsRecord
import net.neuralcheck.data.local.files.BatteryPairsState

/**
 * The six reads taken at one `ACTION_BATTERY_CHANGED` broadcast, each already
 * classified: a value, or `null` when that read was not a value (platform
 * failure marker, exception, extra missing). See [BatteryPair].
 */
data class BatteryPairInputs(
    val voltageRaw: Int?,
    val currentRaw: Int?,
    val chargeCounterRaw: Long?,
    val levelRaw: Int?,
    val levelScale: Int?,
    val tempRaw: Int?
) {
    /** How many of the six reads were not a value. */
    val invalidCount: Int
        get() = listOf(voltageRaw, currentRaw, chargeCounterRaw, levelRaw, levelScale, tempRaw).count { it == null }
}

/**
 * Turns one broadcast [Intent] plus the two property reads into
 * [BatteryPairInputs] WITHOUT interpreting anything (fieldsVersion 68, ت-06).
 * Pure with respect to Android state: the property reads come in as lambdas,
 * so a JVM test drives the real classification with a mocked `Intent`.
 */
object BatteryPairReads {

    fun fromIntent(
        intent: Intent,
        readCurrentNow: () -> Int,
        readChargeCounter: () -> Long
    ): BatteryPairInputs = BatteryPairInputs(
        voltageRaw = extra(intent, BatteryManager.EXTRA_VOLTAGE),
        currentRaw = property { readCurrentNow() }?.takeUnless { BatteryChannelSanitizer.isInvalidCurrent(it) },
        chargeCounterRaw = property { readChargeCounter() }?.takeUnless { BatteryChannelSanitizer.isInvalidLong(it) },
        levelRaw = extra(intent, BatteryManager.EXTRA_LEVEL),
        levelScale = extra(intent, BatteryManager.EXTRA_SCALE),
        tempRaw = extra(intent, BatteryManager.EXTRA_TEMPERATURE)
    )

    /**
     * An int extra as the intent holds it, `null` when the intent does not carry
     * the key — `getIntExtra(key, default)` would invent the default as a
     * reading. The platform's failure marker is `null` as well.
     */
    private fun extra(intent: Intent, key: String): Int? {
        if (!intent.hasExtra(key)) return null
        val raw = intent.getIntExtra(key, Int.MIN_VALUE)
        // Int.MIN_VALUE is the platform's failure marker for an int; it is never a reading.
        return raw.takeUnless { it == Int.MIN_VALUE }
    }

    private inline fun <T> property(read: () -> T): T? = try {
        read()
    } catch (_: Exception) {
        null
    }
}

/**
 * Keeps the first [cap] pairs of one scan and the counts that make the list
 * trustworthy (see [BatteryPairsState]). Thread-safe: the receiver calls
 * [onBroadcast] on the main thread, the service reads [finish] from another.
 */
class BatteryPairRecorder(private val cap: Int = CAP) {

    companion object {
        /** S-10: at most 200 pairs. */
        const val CAP: Int = 200
    }

    private val lock = Any()
    private var recording = false
    private var startMs = 0L
    private val pairs = ArrayList<BatteryPair>()
    private var broadcastCount = 0
    private var droppedByCap = 0
    private var invalidReads = 0

    /** Begins a scan's recording; whatever an earlier scan left is dropped. [scanStartElapsedMs] is the frame of `t`. */
    fun start(scanStartElapsedMs: Long) {
        synchronized(lock) {
            recording = true
            startMs = scanStartElapsedMs
            pairs.clear()
            broadcastCount = 0
            droppedByCap = 0
            invalidReads = 0
        }
    }

    /** True between [start] and [finish]; lets the receiver skip the property reads outside a scan. */
    fun isRecording(): Boolean = synchronized(lock) { recording }

    /** `t` of a broadcast arriving at [nowElapsedMs]. */
    fun relativeTime(nowElapsedMs: Long): Long = synchronized(lock) { nowElapsedMs - startMs }

    /** Records one broadcast. A broadcast outside a recording is ignored (and not counted). */
    fun onBroadcast(nowElapsedMs: Long, inputs: BatteryPairInputs) {
        synchronized(lock) {
            if (!recording) return
            broadcastCount++
            invalidReads += inputs.invalidCount
            if (pairs.size < cap) {
                pairs.add(
                    BatteryPair(
                        t = nowElapsedMs - startMs,
                        voltageRaw = inputs.voltageRaw,
                        currentRaw = inputs.currentRaw,
                        chargeCounterRaw = inputs.chargeCounterRaw,
                        levelRaw = inputs.levelRaw,
                        levelScale = inputs.levelScale,
                        tempRaw = inputs.tempRaw
                    )
                )
            } else {
                droppedByCap++
            }
        }
    }

    /**
     * Ends the recording and returns what it holds. [receiverRegistered] is the
     * reader's registration state at this moment, taken by the caller before it
     * unregisters. A recorder that never started returns `null`: nothing was
     * recorded and nothing may be claimed.
     */
    fun finish(receiverRegistered: Boolean): BatteryPairsRecord? = synchronized(lock) {
        if (!recording) return null
        recording = false
        BatteryPairsRecord(
            pairs = pairs.toList(),
            state = BatteryPairsState(
                receiverRegistered = receiverRegistered,
                broadcastCount = broadcastCount,
                droppedByCap = droppedByCap,
                invalidReads = invalidReads
            )
        )
    }
}
