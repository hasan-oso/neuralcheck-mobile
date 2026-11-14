package net.neuralcheck.diagnostics

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Log
import net.neuralcheck.hardware.battery.BatteryTests
import java.io.File
import java.util.concurrent.Executors

/**
 * Field-diagnostic battery-sysfs probe — **datacollect flavor ONLY**.
 *
 * This file lives under `app/src/datacollect/java`, a flavor-specific
 * source set. It is not compiled into the `launch` flavor at all — there is
 * no BuildConfig flag gating it, because there is nothing to gate; the
 * class does not exist outside a datacollect build. app/src/launch has no
 * matching manifest entry either (see app/src/datacollect/AndroidManifest.xml).
 *
 * ── Why this exists ──
 *
 * `adb shell` reads on a Samsung SM-G977N (S10 5G, Android 11 / API 30) at
 * uid=2000 / u:r:shell:s0 found:
 *   - /sys/.../battery/cycle_count      → "No such file or directory"
 *   - /sys/.../battery/battery_cycle    → 1750
 *   - /sys/.../battery/fg_cycle         → 27       (Maxim fuel gauge)
 *   - /sys/.../battery/fg_fullcapnom    → 4181
 *   - /sys/.../battery/charge_full      → 4400000
 *   - /sys/.../battery/charge_full_design → 4400000
 *   - /sys/.../battery/fg_asoc         → "Permission denied"
 *
 * Those reads ran under the SHELL SELinux domain, not the app's own
 * `untrusted_app` domain — the two have different sysfs ACLs, so a node
 * readable to shell can be denied to the installed APK and vice versa.
 * This receiver re-runs the same reads from INSIDE the app process so we
 * can see what net.neuralcheck.datacollect itself can actually reach. A
 * different result here is the expected, useful outcome, not a bug.
 *
 * This is diagnosis, not a production fix: it does not read, call, modify,
 * or otherwise touch BatteryTests.testCycleCount(), B-08, or
 * BatteryCycleCount.read() (the one cycle-count reader since fieldsVersion 68). It reuses BatteryTests's
 * existing 22-path cycle-count candidate list
 * ([BatteryTests.CYCLE_COUNT_PROBE_PATHS]) by reference rather than
 * retyping it, per the probe spec.
 *
 * ── Invocation ──
 *
 *   adb shell am broadcast \
 *       -a net.neuralcheck.diagnostics.action.PROBE_BATTERY \
 *       -p net.neuralcheck.datacollect
 *
 * Output goes to logcat under tag [TAG] ("NC.BatteryProbe"), e.g.:
 *
 *   adb logcat -s NC.BatteryProbe:*
 *
 * The app does not need to be in the foreground; the receiver is a
 * manifest-registered (cold-start-capable) component, so this also works
 * right after `adb install` with the app never opened. No 8-minute scan is
 * required — a single broadcast completes in well under a second.
 */
class BatteryProbeReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_PROBE_BATTERY = "net.neuralcheck.diagnostics.action.PROBE_BATTERY"
        const val TAG = "NC.BatteryProbe"

        private const val BATTERY_BASE = "/sys/class/power_supply/battery"

        // Maxim fuel-gauge nodes observed on SM-G977N via shell (task item 2.b).
        private val MAXIM_FUEL_GAUGE_NODES = listOf(
            "$BATTERY_BASE/fg_cycle",
            "$BATTERY_BASE/fg_fullcapnom",
            "$BATTERY_BASE/fg_asoc",
            "$BATTERY_BASE/fg_fullcap",
            "$BATTERY_BASE/fg_repcap",
            "$BATTERY_BASE/fg_repsoc"
        )

        // Comparison nodes (task item 2.c).
        private val COMPARISON_NODES = listOf(
            "$BATTERY_BASE/charge_full",
            "$BATTERY_BASE/charge_full_design"
        )

        private const val SELINUX_CONTEXT_PATH = "/proc/self/attr/current"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_PROBE_BATTERY) return
        // File I/O off the main thread: onReceive must return quickly, and
        // goAsync() keeps the process alive long enough to finish the reads
        // even if the app has no foreground UI.
        val pending = goAsync()
        val appContext = context.applicationContext
        Executors.newSingleThreadExecutor().execute {
            try {
                runProbe(appContext)
            } finally {
                pending.finish()
            }
        }
    }

    private fun runProbe(context: Context) {
        Log.i(TAG, "════════════════════ BATTERY PROBE START ════════════════════")
        logExecutionContext(context)

        Log.i(TAG, "── (A) existing 22-path cycle-count catalog — reused from BatteryTests.CYCLE_COUNT_PROBE_PATHS ──")
        BatteryTests.CYCLE_COUNT_PROBE_PATHS.forEach { probeNode(it) }

        Log.i(TAG, "── (B) Maxim fuel-gauge nodes (SM-G977N field observation) ──")
        MAXIM_FUEL_GAUGE_NODES.forEach { probeNode(it) }

        Log.i(TAG, "── (C) comparison nodes ──")
        COMPARISON_NODES.forEach { probeNode(it) }

        Log.i(TAG, "════════════════════ BATTERY PROBE END ════════════════════")
    }

    /**
     * Reads one sysfs node and logs: path, raw value exactly as read (no
     * numeric parsing / conversion of any kind), or — on failure — the
     * exception's own class name and message. Never collapses a failure to
     * a bare null/catch-all; A87 requires the exception TYPE to be visible
     * (SecurityException vs FileNotFoundException vs IOException vs other),
     * because on Android both "missing" and "SELinux-denied" sysfs reads
     * commonly surface as the SAME exception class (FileNotFoundException)
     * and are only distinguishable by e.message (e.g. "ENOENT" / "No such
     * file or directory" vs "EACCES" / "Permission denied") — so the
     * message is recorded every time, not just the class name.
     */
    private fun probeNode(path: String) {
        try {
            val raw = File(path).readText().trim()
            Log.i(TAG, "$path = \"$raw\"")
        } catch (t: Throwable) {
            Log.i(TAG, "$path = FAILED  (${t.javaClass.simpleName}: ${t.message})")
        }
    }

    /**
     * Execution-context fields the report asks for: uid, package name,
     * SDK_INT, and — best-effort — the process's own SELinux context. A
     * failure to read the SELinux context is logged as its own line/node,
     * never silently skipped.
     */
    private fun logExecutionContext(context: Context) {
        Log.i(TAG, "── execution context ──")

        val uid = try {
            Process.myUid()
        } catch (t: Throwable) {
            Log.i(TAG, "android.os.Process.myUid() FAILED  (${t.javaClass.simpleName}: ${t.message})")
            null
        }
        if (uid != null) Log.i(TAG, "uid=$uid")

        val pkg = try {
            context.packageName
        } catch (t: Throwable) {
            Log.i(TAG, "context.packageName FAILED  (${t.javaClass.simpleName}: ${t.message})")
            null
        }
        if (pkg != null) Log.i(TAG, "packageName=$pkg")

        Log.i(TAG, "Build.VERSION.SDK_INT=${Build.VERSION.SDK_INT}")

        // SELinux context of this process, if readable. Per spec: a failed
        // read here is logged as its own explicit node, not swallowed.
        try {
            val selinuxContext = File(SELINUX_CONTEXT_PATH).readText().trim()
            Log.i(TAG, "$SELINUX_CONTEXT_PATH = \"$selinuxContext\"")
        } catch (t: Throwable) {
            Log.i(TAG, "$SELINUX_CONTEXT_PATH = FAILED  (${t.javaClass.simpleName}: ${t.message})")
        }
    }
}
