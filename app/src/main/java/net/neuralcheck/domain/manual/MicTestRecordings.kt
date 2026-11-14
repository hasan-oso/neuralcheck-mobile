package net.neuralcheck.domain.manual

import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * The temporary recordings of the M-06 microphone test, and what is done about the ones a killed process leaves behind — batch 2, item 5 (E28; user decision
 * 2026-10-15: «E28: الاثنان: كنس تسجيلات الميكروفون عند فتح التطبيق، وبند في فحص المحطّة ٢»).
 *
 * The facts (the verifier's ruling at b592686, relayed by the coordinator; each one checkable in this repository): M-06 alone records, with a `MediaRecorder`, into
 * a file in `cacheDir` named `mic_test_<millis>.m4a`; the one delete was inside the dialog's `onDispose`, its failure swallowed; and nothing swept at start-up, so a
 * recording whose dialog never disposed (the process killed mid-recording) stayed in `cache/` - holding the technician's voice - until Android cleared the cache.
 *
 * ONE source for the name (rule 24): [PREFIX] and [SUFFIX] are read by the creation ([newFile], the dialog) and by the sweep ([sweep], `NeuralCheckApp.onCreate`).
 * Nothing else in the app spells `mic_test_`; a test scans the sources for it.
 *
 * Failures are told by their TEXT, never by their class (rule 21): [deleteOne] returns the `IOException`'s message, and a delete that failed prints a line carrying
 * that message ([deleteAndLog]) - it used to vanish in `catch (_: Exception) {}`. A failure with no message prints `absent`: a named state (rule 22).
 *
 * Pure Kotlin + `java.nio.file` (API 26; minSdk is 28): no Android type, so a JVM test runs it against a real temporary directory.
 */
object MicTestRecordings {

    /** The log tag of the lines below. */
    const val LOG_TAG = "NeuralCheck.Mic"

    /** What a recording's file name starts with. */
    const val PREFIX = "mic_test_"

    /** What a recording's file name ends with. */
    const val SUFFIX = ".m4a"

    /** The most characters of a failure's message printed on a line (rule 36's bound). */
    const val MESSAGE_MAX = 200

    /** At most this many `mic-sweep-failed` lines are printed per sweep (the summary line still counts them all). */
    const val FAILURE_LINES_MAX = 5

    /** The file name of the recording made at [nowMs]. */
    fun fileName(nowMs: Long): String = "$PREFIX$nowMs$SUFFIX"

    /** The file the recording made at [nowMs] goes to, in [cacheDir]. */
    fun newFile(cacheDir: File, nowMs: Long): File = File(cacheDir, fileName(nowMs))

    /** Whether [name] is a recording's: starts with [PREFIX] AND ends with [SUFFIX] (a name that has only one of the two is not ours). */
    fun matches(name: String): Boolean = name.startsWith(PREFIX) && name.endsWith(SUFFIX)

    /**
     * What a sweep found. [found] counts the regular files whose name [matches]; [deleted] those that are gone; [failed] those whose deletion threw;
     * [firstFailure] is the message TEXT of the first failure (null when none, or `absent` when it had no message).
     */
    data class SweepResult(val found: Int, val deleted: Int, val failed: Int, val firstFailure: String?)

    /**
     * Deletes [file]; returns null when it is gone (it did not exist, or was deleted) and the failure's message TEXT when the deletion threw
     * (`absent` when the exception had no message). Never throws.
     */
    fun deleteOne(file: File): String? =
        try {
            Files.deleteIfExists(file.toPath())
            null
        } catch (e: IOException) {
            e.message?.takeIf { it.isNotBlank() }?.take(MESSAGE_MAX) ?: "absent"
        } catch (e: SecurityException) {
            e.message?.takeIf { it.isNotBlank() }?.take(MESSAGE_MAX) ?: "absent"
        }

    /** [deleteOne], and a `mic-delete-failed` line through [log] when it failed (the dialog's disposal). */
    fun deleteAndLog(file: File, log: (String) -> Unit, deleter: (File) -> String? = ::deleteOne) {
        val failure = deleter(file) ?: return
        log("mic-delete-failed name=${file.name} message=$failure")
    }

    /**
     * Sweeps [cacheDir] (not its sub-directories): every REGULAR file whose name [matches] is deleted; anything else stays - another name, a directory, a file
     * with the prefix but not the suffix. Prints through [log]: one `mic-sweep found=N deleted=N failed=N` line always (a sweep that found nothing says so), and
     * up to [FAILURE_LINES_MAX] `mic-sweep-failed name=… message=…` lines, the message being the failure's text. A [cacheDir] that cannot be listed prints
     * `mic-sweep-unreadable` and counts as nothing found. [deleter] is [deleteOne] unless a test substitutes the one thing a JVM cannot make fail portably.
     */
    fun sweep(cacheDir: File, log: (String) -> Unit, deleter: (File) -> String? = ::deleteOne): SweepResult {
        val candidates = cacheDir.listFiles()
        if (candidates == null) {
            log("mic-sweep-unreadable dir=${cacheDir.name}")
            return SweepResult(found = 0, deleted = 0, failed = 0, firstFailure = null)
        }
        val recordings = candidates.filter { it.isFile && matches(it.name) }
        var deleted = 0
        var failed = 0
        var firstFailure: String? = null
        var failureLines = 0
        for (file in recordings) {
            val failure = deleter(file)
            if (failure == null) {
                deleted++
            } else {
                failed++
                if (firstFailure == null) firstFailure = failure
                if (failureLines < FAILURE_LINES_MAX) {
                    failureLines++
                    log("mic-sweep-failed name=${file.name} message=$failure")
                }
            }
        }
        log("mic-sweep found=${recordings.size} deleted=$deleted failed=$failed")
        return SweepResult(found = recordings.size, deleted = deleted, failed = failed, firstFailure = firstFailure)
    }
}
