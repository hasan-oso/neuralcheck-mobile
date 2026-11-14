package net.neuralcheck.domain.manual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The microphone-test recordings and their sweep — batch 2, item 5 (E28). The user's decision (2026-10-15): «الاثنان: كنس تسجيلات الميكروفون عند فتح التطبيق، وبند
 * في فحص المحطّة ٢». The order's tests: «ملفّات قديمة في cacheDir مؤقّت ⇒ تُحذف؛ وملفّ لا يطابق البادئة ⇒ يبقى؛ وفشل حذف ⇒ يظهر في السطر». They run against a REAL
 * temporary directory and the real deletion; the one thing a JVM cannot make fail the same way on every machine - a file the operating system refuses to delete -
 * is a substituted deleter whose failure text is the Windows one (a message TEXT, as the rule 21 line must carry). The real failing path
 * ([MicTestRecordings.deleteOne] on a directory that is not empty) is tested separately with the real filesystem.
 *
 * Rule 23: no earlier test covered the delete or the file name (`git grep -n "mic_test" -- app/src/test` was empty), so none is replaced. What the tests cannot
 * see: a real killed process and a real `cache/` on a phone - that is the station-2 device item (kill the process during M-06, read `cache/` before the next open and after).
 */
class MicTestRecordingsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val lines = mutableListOf<String>()
    private val log: (String) -> Unit = { lines.add(it) }

    private fun cache(): File = tmp.newFolder("cache")

    private fun File.make(name: String, bytes: Int = 16): File = File(this, name).also { it.writeBytes(ByteArray(bytes) { 1 }) }

    @Test
    fun `old recordings in the cache directory are deleted, and the line says how many`() {
        val dir = cache()
        val oldest = dir.make("mic_test_1700000000000.m4a").also { it.setLastModified(1_700_000_000_000L) }
        val another = dir.make("mic_test_1.m4a")
        val result = MicTestRecordings.sweep(dir, log)
        assertFalse(oldest.exists())
        assertFalse(another.exists())
        assertEquals(MicTestRecordings.SweepResult(found = 2, deleted = 2, failed = 0, firstFailure = null), result)
        assertEquals(listOf("mic-sweep found=2 deleted=2 failed=0"), lines)
    }

    @Test
    fun `a recording the code itself names is swept - the creation and the sweep read the same name`() {
        val dir = cache()
        val made = MicTestRecordings.newFile(dir, 1_700_000_123_456L)
        made.writeBytes(ByteArray(8))
        assertEquals("mic_test_1700000123456.m4a", made.name)
        assertTrue(MicTestRecordings.matches(made.name))
        assertEquals(1, MicTestRecordings.sweep(dir, log).deleted)
        assertFalse(made.exists())
    }

    @Test
    fun `a file that does not match the prefix stays - another name, another extension, a name that only contains the prefix, a directory`() {
        val dir = cache()
        val stays = listOf(
            dir.make("other.m4a"),
            dir.make("voice_note.m4a"),
            dir.make("mic_test_notes.txt"),   // the prefix, but not the suffix
            dir.make("xmic_test_1.m4a"),      // contains the prefix, does not start with it
            dir.make("mic_test_1.m4a.bak"),   // starts with the prefix, does not end with the suffix
            dir.make("MIC_TEST_1.M4A")        // the name is matched exactly
        )
        val folder = File(dir, "mic_test_folder.m4a").also { it.mkdir(); File(it, "inside.bin").writeBytes(ByteArray(4)) }
        val result = MicTestRecordings.sweep(dir, log)
        assertEquals(MicTestRecordings.SweepResult(found = 0, deleted = 0, failed = 0, firstFailure = null), result)
        for (file in stays) assertTrue("${file.name} must stay", file.exists())
        assertTrue("a directory is not a recording", folder.isDirectory && File(folder, "inside.bin").exists())
        assertEquals(listOf("mic-sweep found=0 deleted=0 failed=0"), lines)
    }

    @Test
    fun `a recording and an unrelated file together - only the recording goes`() {
        val dir = cache()
        val recording = dir.make("mic_test_5.m4a")
        val other = dir.make("report_cache.json")
        MicTestRecordings.sweep(dir, log)
        assertFalse(recording.exists())
        assertTrue(other.exists())
    }

    @Test
    fun `a failed delete shows in the sweep line with the failure's message text, and does not stop the others`() {
        val dir = cache()
        val locked = dir.make("mic_test_2.m4a")
        val free = dir.make("mic_test_3.m4a")
        val windowsText = "C:\\cache\\mic_test_2.m4a: The process cannot access the file because it is being used by another process"
        val result = MicTestRecordings.sweep(dir, log, deleter = { f -> if (f.name == "mic_test_2.m4a") windowsText else MicTestRecordings.deleteOne(f) })
        assertEquals(MicTestRecordings.SweepResult(found = 2, deleted = 1, failed = 1, firstFailure = windowsText), result)
        assertTrue(locked.exists())
        assertFalse("the other recording was still deleted", free.exists())
        assertEquals(
            listOf(
                "mic-sweep-failed name=mic_test_2.m4a message=$windowsText",
                "mic-sweep found=2 deleted=1 failed=1"
            ),
            lines
        )
    }

    @Test
    fun `at most five failure lines are printed, the summary still counts every failure`() {
        val dir = cache()
        repeat(8) { dir.make("mic_test_${it}.m4a") }
        val result = MicTestRecordings.sweep(dir, log, deleter = { "refused" })
        assertEquals(8, result.failed)
        assertEquals(5, lines.count { it.startsWith("mic-sweep-failed ") })
        assertEquals("mic-sweep found=8 deleted=0 failed=8", lines.last())
    }

    @Test
    fun `a cache directory that cannot be listed is named, not read as an empty one`() {
        val missing = File(tmp.root, "no-such-directory")
        val result = MicTestRecordings.sweep(missing, log)
        assertEquals(MicTestRecordings.SweepResult(0, 0, 0, null), result)
        assertEquals(listOf("mic-sweep-unreadable dir=no-such-directory"), lines)
    }

    @Test
    fun `deleteOne - a file is gone, a missing file is gone, a real failure comes back as its message text`() {
        val dir = cache()
        val file = dir.make("mic_test_9.m4a")
        assertNull(MicTestRecordings.deleteOne(file))
        assertFalse(file.exists())
        assertNull("a file that is already gone is not a failure", MicTestRecordings.deleteOne(file))
        // a real failure on the real filesystem: a directory that is not empty cannot be deleted. The text is the message, not the exception's class.
        val notEmpty = File(dir, "mic_test_dir").also { it.mkdir(); File(it, "x").writeBytes(ByteArray(1)) }
        val failure = MicTestRecordings.deleteOne(notEmpty)
        assertEquals(notEmpty.path, failure)
        assertFalse("the class name is not what is reported", failure!!.contains("Exception"))
    }

    @Test
    fun `deleteAndLog - nothing is printed for a delete that worked, a line with the message for one that failed`() {
        val dir = cache()
        val file = dir.make("mic_test_11.m4a")
        MicTestRecordings.deleteAndLog(file, log)
        assertFalse(file.exists())
        assertTrue("silent on success", lines.isEmpty())

        val notEmpty = File(dir, "mic_test_dir").also { it.mkdir(); File(it, "x").writeBytes(ByteArray(1)) }
        MicTestRecordings.deleteAndLog(notEmpty, log)
        assertEquals(listOf("mic-delete-failed name=mic_test_dir message=${notEmpty.path}"), lines)

        lines.clear()
        MicTestRecordings.deleteAndLog(dir.make("mic_test_12.m4a"), log, deleter = { "access denied by test" })
        assertEquals(listOf("mic-delete-failed name=mic_test_12.m4a message=access denied by test"), lines)
    }

    @Test
    fun `the name helpers - prefix and suffix are the ones the order names`() {
        assertEquals("mic_test_", MicTestRecordings.PREFIX)
        assertEquals(".m4a", MicTestRecordings.SUFFIX)
        assertEquals("mic_test_42.m4a", MicTestRecordings.fileName(42))
        assertTrue(MicTestRecordings.matches("mic_test_42.m4a"))
        assertFalse(MicTestRecordings.matches("mic_test_42.m4a.tmp"))
        assertFalse(MicTestRecordings.matches("a_mic_test_42.m4a"))
    }
}
