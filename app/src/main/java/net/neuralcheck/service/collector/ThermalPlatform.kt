package net.neuralcheck.service.collector

import android.annotation.SuppressLint
import android.os.Build
import android.os.PowerManager
import net.neuralcheck.data.local.files.ThermalListStates
import net.neuralcheck.util.ReasonText
import java.util.concurrent.Executor

/**
 * The outcome of ONE `getThermalHeadroom(0)` call: [headroom] only when the
 * call returned a finite number, and [state] naming (as TEXT, rule 21) what
 * the call did — see [ThermalListStates].
 */
data class ThermalHeadroomRead(val headroom: Float?, val state: String) {
    init {
        require((headroom != null) == (state == ThermalListStates.HEADROOM_OK)) {
            "a headroom value is present exactly when the state is ok (got $headroom / $state)"
        }
    }
}

/** What [ThermalPlatform.registerStatusListener] returns, to take the listener off again. */
fun interface ThermalStatusRegistration {
    fun unregister()
}

/**
 * The part of `PowerManager` the thermal lists need, behind an interface so
 * the collector's logic runs in a JVM test with a scripted platform and the
 * real one is a thin wrapper ([PowerManagerThermalPlatform]).
 *
 * Unlike [SystemFileReader.readThermalHeadroom] (which the governor guard, the
 * aggregator and the probe share, with its 1.1 s cache and its first-throw
 * pin — all unchanged, contract S-03), nothing here caches and nothing here is
 * pinned: S-35 — "no pinning of «unsupported»: every reading with its own
 * state, a call every 10 seconds".
 */
interface ThermalPlatform {
    val sdkInt: Int

    /** One platform call, categorised; never throws. API < 30 does not call the platform. */
    fun readHeadroom(): ThermalHeadroomRead

    /** `getCurrentThermalStatus()` (API 29+): the raw 0..6; throws what the platform throws. */
    fun currentStatus(): Int

    /** `addThermalStatusListener(executor, …)` (API 29+); throws what the platform throws. */
    fun registerStatusListener(executor: Executor, onChanged: (Int) -> Unit): ThermalStatusRegistration
}

/** [ThermalPlatform] over a real [PowerManager] (null = the service was unavailable). */
class PowerManagerThermalPlatform(
    private val powerManager: PowerManager?,
    override val sdkInt: Int = Build.VERSION.SDK_INT
) : ThermalPlatform {

    @SuppressLint("NewApi") // guarded by [sdkInt] before every platform call
    override fun readHeadroom(): ThermalHeadroomRead {
        if (sdkInt < Build.VERSION_CODES.R) {
            return ThermalHeadroomRead(null, ThermalListStates.API_BELOW_30)
        }
        val pm = powerManager ?: return ThermalHeadroomRead(
            null,
            ThermalListStates.EXCEPTION_PREFIX + "PowerManager unavailable (POWER_SERVICE returned null)"
        )
        return try {
            classify(pm.getThermalHeadroom(0)) // 0 = "right now"
        } catch (t: Exception) {
            ThermalHeadroomRead(null, ThermalListStates.EXCEPTION_PREFIX + ReasonText.of(t))
        }
    }

    @SuppressLint("NewApi")
    override fun currentStatus(): Int {
        val pm = powerManager ?: throw IllegalStateException("PowerManager unavailable (POWER_SERVICE returned null)")
        return pm.currentThermalStatus
    }

    @SuppressLint("NewApi")
    override fun registerStatusListener(executor: Executor, onChanged: (Int) -> Unit): ThermalStatusRegistration {
        val pm = powerManager ?: throw IllegalStateException("PowerManager unavailable (POWER_SERVICE returned null)")
        val listener = PowerManager.OnThermalStatusChangedListener { status -> onChanged(status) }
        pm.addThermalStatusListener(executor, listener)
        return ThermalStatusRegistration { pm.removeThermalStatusListener(listener) }
    }

    companion object {
        /**
         * Maps the platform's float to a read: a finite value is `ok`; `NaN`
         * is the platform's own "unknown" (kept as its own state, never
         * written as a number); +-Infinity cannot be serialised and is not a
         * documented return, so it is declared, not clamped.
         */
        internal fun classify(value: Float): ThermalHeadroomRead = when {
            value.isNaN() -> ThermalHeadroomRead(null, ThermalListStates.HEADROOM_NAN)
            value.isInfinite() -> ThermalHeadroomRead(null, ThermalListStates.NON_FINITE_PREFIX + value)
            else -> ThermalHeadroomRead(value, ThermalListStates.HEADROOM_OK)
        }
    }
}
