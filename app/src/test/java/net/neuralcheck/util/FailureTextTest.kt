package net.neuralcheck.util

import net.neuralcheck.util.FailureText.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Rule 21 (QA record of the 30-node case): the cause is told by the error
 * TEXT, because on Android one exception class — FileNotFoundException —
 * covers both a refusal (EACCES) and absence (ENOENT), whose remedies differ.
 */
class FailureTextTest {

    @Test
    fun `android file-open messages are told apart by their text`() {
        assertEquals(
            Kind.DENIED,
            FailureText.classify("/sys/devices/system/cpu/cpufreq/policy0/scaling_cur_freq: open failed: EACCES (Permission denied)")
        )
        assertEquals(
            Kind.ABSENT,
            FailureText.classify("/sys/devices/system/cpu/cpufreq/policy9/scaling_cur_freq: open failed: ENOENT (No such file or directory)")
        )
    }

    @Test
    fun `desktop jvm messages are told apart too`() {
        assertEquals(Kind.DENIED, FailureText.classify("/x (Permission denied)"))
        assertEquals(Kind.ABSENT, FailureText.classify("/x (No such file or directory)"))
    }

    @Test
    fun `the same exception class gives both answers`() {
        // Same class, different text -> different kind. A class-based test
        // could not produce this result.
        val denied = java.io.FileNotFoundException("p: open failed: EACCES (Permission denied)")
        val absent = java.io.FileNotFoundException("p: open failed: ENOENT (No such file or directory)")
        assertEquals(denied.javaClass, absent.javaClass)
        assertEquals(Kind.DENIED, FailureText.classify(denied.message!!))
        assertEquals(Kind.ABSENT, FailureText.classify(absent.message!!))
    }

    @Test
    fun `other refusal and absence spellings`() {
        assertEquals(Kind.DENIED, FailureText.classify("EPERM (Operation not permitted)"))
        assertEquals(Kind.DENIED, FailureText.classify("eacces"))
        assertEquals(Kind.ABSENT, FailureText.classify("ENOTDIR (Not a directory)"))
    }

    @Test
    fun `text that names neither stays OTHER - it is not guessed`() {
        assertEquals(Kind.OTHER, FailureText.classify("read failed: EIO (I/O error)"))
        assertEquals(Kind.OTHER, FailureText.classify("exception without message"))
        assertEquals(Kind.OTHER, FailureText.classify(""))
    }

    @Test
    fun `a text naming both counts as a refusal - the source exists`() {
        assertEquals(Kind.DENIED, FailureText.classify("ENOENT on parent, then EACCES on retry"))
    }
}
