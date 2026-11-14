package net.neuralcheck

import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import net.neuralcheck.domain.manual.MicTestRecordings
import net.neuralcheck.service.sync.SyncScheduler
import net.neuralcheck.util.LocaleHelper
import net.neuralcheck.util.NativeSmokeTestRunner
import net.neuralcheck.util.NotificationHelper
import java.io.PrintWriter
import java.io.StringWriter
import javax.inject.Inject

@HiltAndroidApp
class NeuralCheckApp : Application(), Configuration.Provider {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleHelper.wrap(base))
    }

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        // Install the global uncaught-exception handler BEFORE super.onCreate
        // so even a Hilt graph-build failure (which fires inside super) gets
        // captured. setDefaultUncaughtExceptionHandler is a static call with
        // no Hilt dependency, so it's safe to run before the @HiltAndroidApp
        // initialization that super.onCreate triggers.
        installCrashHandler()
        super.onCreate()

        // Defensive: clear any "scan in flight" flag left over from a crash
        // mid-scan. Without this reset a fatal in StressTestService could
        // persist a true value and HomeScreen would refuse the next attempt.
        runCatching {
            getSharedPreferences("scan_state", MODE_PRIVATE)
                .edit()
                .putBoolean("is_scanning", false)
                .apply()
        }

        // Create notification channels for scan progress, safety stops, and sync
        runCatching { notificationHelper.createChannels() }
            .onFailure { Log.w(TAG, "createChannels failed: ${it.message}", it) }
        // Schedule periodic background sync (WiFi only, every 6 hours)
        runCatching { SyncScheduler.schedule(this) }
            .onFailure { Log.w(TAG, "SyncScheduler.schedule failed: ${it.message}", it) }

        // V2 sub-phase 1h integration smoke: delegate to
        // NativeSmokeTestRunner so the orchestration logic is shared
        // between this first-launch probe and any future debug surface
        // (dev settings, instrumentation tests). Runs on a daemon
        // background thread because the cumulative cost (memory test
        // alone is ~600-1200 ms on Snapdragon 855) would otherwise
        // delay startup. Once V2's StressTestService is wired up, this
        // call site stays as the first-launch health check.
        Thread({
            runCatching { NativeSmokeTestRunner.runAll() }
                .onFailure { Log.w(TAG, "Native smoke test failed: ${it.message}", it) }
        }, "NeuralCheck-NativeHealth").apply { isDaemon = true }.start()

        // Batch 2, item 5 (E28; user decision 2026-10-15: «كنس تسجيلات الميكروفون عند فتح التطبيق»): a microphone-test recording left in cacheDir by a process that
        // was killed before its dialog disposed (it holds the technician's voice) is deleted at process start. On a daemon thread - a directory listing is
        // disk I/O - and the line it prints carries the counts and the failure's message text (MicTestRecordings.sweep).
        Thread({
            runCatching { MicTestRecordings.sweep(cacheDir, log = { Log.i(MicTestRecordings.LOG_TAG, it) }) }
                .onFailure { Log.w(MicTestRecordings.LOG_TAG, "mic-sweep-crashed message=${it.message?.take(MicTestRecordings.MESSAGE_MAX) ?: "absent"}") }
        }, "NeuralCheck-MicSweep").apply { isDaemon = true }.start()

        // Authentication bootstrap used to live here as a silent
        // `signInAnonymously()` + `registerUser()` pair so the first AI call
        // wouldn't fail with UNAUTHENTICATED. That is now the explicit job
        // of LoginScreen — we do not create accounts on the user's behalf,
        // and MainActivity routes unauthenticated launch-flavor users into
        // that screen before they can reach the home page. The datacollect
        // flavor never required auth and bypasses this flow entirely.
    }

    /**
     * Installs a defensive uncaught-exception handler so background-thread
     * crashes (sampler/sensor callbacks, EGL render loops, Firebase
     * threadpool, Room IO) don't take down the whole process. The error
     * is appended to a SharedPreferences ring buffer so a later debug
     * screen can surface it without needing logcat access.
     *
     * Main-thread crashes still delegate to the previous handler — those
     * indicate broken UI state where continuing on top of a half-rendered
     * Compose tree would just cascade into more obscure failures.
     */
    private fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val trace = sw.toString().take(8_000)
                Log.e(
                    TAG,
                    "UNCAUGHT on thread=${thread.name}: ${throwable.javaClass.simpleName}: ${throwable.message}",
                    throwable
                )
                persistCrash(thread.name, throwable, trace)
            } catch (_: Throwable) {
                // Never let our own handler throw — that would defeat the point.
            }
            // Main-thread / Looper-thread crashes need to reach the system
            // so the standard ANR / "App stopped" flow can run; otherwise
            // we'd leave a frozen UI that's worse than a clean crash.
            val isMain = thread === Looper.getMainLooper().thread
            if (isMain && previous != null) {
                previous.uncaughtException(thread, throwable)
            }
            // For background threads we deliberately do NOT delegate. The
            // system default would call System.exit(); swallowing keeps the
            // app alive so the user can finish what they were doing.
        }
    }

    private fun persistCrash(threadName: String, throwable: Throwable, trace: String) {
        try {
            val prefs = getSharedPreferences("neuralcheck_crashes", MODE_PRIVATE)
            val key = "crash_${System.currentTimeMillis()}"
            val payload = buildString {
                append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                append(" sdk=").append(Build.VERSION.SDK_INT)
                append(" thread=").append(threadName)
                append(" type=").append(throwable.javaClass.simpleName)
                append(" msg=").append(throwable.message ?: "")
                append('\n').append(trace)
            }.take(12_000)
            prefs.edit().putString(key, payload).apply()
            // Keep at most the 10 most recent crash records to bound prefs growth.
            val all = prefs.all.keys.filter { it.startsWith("crash_") }.sorted()
            if (all.size > 10) {
                val toDrop = all.take(all.size - 10)
                prefs.edit().apply {
                    toDrop.forEach { remove(it) }
                }.apply()
            }
        } catch (_: Throwable) { /* ignore */ }
    }

    private companion object {
        const val TAG = "NeuralCheck"
    }
}
