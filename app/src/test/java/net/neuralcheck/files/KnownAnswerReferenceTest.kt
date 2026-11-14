package net.neuralcheck.files

import net.neuralcheck.data.local.files.KnownAnswerLayout
import net.neuralcheck.util.StressNative
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * fieldsVersion 70 (contract docs/FV68_CONTRACT.md section 7-t): D's sieve runs to 1,000,000 and its
 * reference is 78,498. The two numbers live ONCE, in `reference_values.h` (rule 24); this test reads
 * that header as text and proves, on the JVM, that
 *
 *  - the reference IS pi(limit) (a plain sieve run to the limit the header names finds it), so the two
 *    cannot drift apart;
 *  - the pair is used by `sieve_known_answer()` (D) and NOT by `runPrimeSieveTest` and the legacy
 *    production stress, which stay on the 10M pair (they are not D);
 *  - the JNI array the native side returns is the layout Kotlin reads (version, length, and which value
 *    sits at which index) — the one place a native/Kotlin disagreement would otherwise hide;
 *  - Kotlin keeps no copy of either number outside comments.
 *
 * What this cannot prove (rule 37): that the compiled kernel counts the reference. The native build is
 * checked by the compiler (the syntax check and the .so build) and the count by a device.
 */
class KnownAnswerReferenceTest {

    private val cppDir: File = listOf(File("src/main/cpp"), File("app/src/main/cpp")).firstOrNull { it.isDirectory }
        ?: error("cannot find app/src/main/cpp from ${File(".").absolutePath}")
    private val javaDir: File = listOf(File("src/main/java"), File("app/src/main/java")).firstOrNull { it.isDirectory }
        ?: error("cannot find app/src/main/java from ${File(".").absolutePath}")

    private fun cpp(path: String) = File(cppDir, path).readText(Charsets.UTF_8)
    private fun kotlin(path: String) = File(javaDir, path).readText(Charsets.UTF_8)

    /** The value of `#define NAME <number>[u]` in the reference header. */
    private fun define(name: String): Long {
        val m = Regex("^#define\\s+" + Regex.escape(name) + "\\s+([0-9]+)u?\\s*$", RegexOption.MULTILINE)
            .find(cpp("reference/reference_values.h"))
            ?: error("`#define $name <number>` not found in reference_values.h")
        return m.groupValues[1].toLong()
    }

    /** pi(limit) by a plain sieve of Eratosthenes. */
    private fun pi(limit: Int): Long {
        val composite = BooleanArray(limit + 1)
        var p = 2
        while (p.toLong() * p <= limit) {
            if (!composite[p]) {
                var i = p * p
                while (i <= limit) { composite[i] = true; i += p }
            }
            p++
        }
        var count = 0L
        for (i in 2..limit) if (!composite[i]) count++
        return count
    }

    /** The text of the braced body that follows `signature` (the signature's own text included). */
    private fun functionBody(text: String, signature: String): String {
        val start = text.indexOf(signature)
        check(start >= 0) { "cannot find `$signature`" }
        val open = text.indexOf('{', start)
        var depth = 0
        for (i in open until text.length) {
            if (text[i] == '{') depth++
            if (text[i] == '}') { depth--; if (depth == 0) return text.substring(start, i + 1) }
        }
        error("unbalanced braces after `$signature`")
    }

    private fun stripKotlinComments(text: String): String =
        text.replace(Regex("/\\*[\\s\\S]*?\\*/"), "").replace(Regex("//[^\\n]*"), "")

    // ───────────────────────── the numbers ─────────────────────────

    @Test
    fun `D's pair is the user's decision - 1,000,000 and 78,498`() {
        assertEquals(1_000_000L, define("PRIME_SIEVE_1M_LIMIT"))
        assertEquals(78_498L, define("PRIME_SIEVE_1M_REFERENCE"))
    }

    @Test
    fun `the reference is pi of the limit - a sieve run to the header's own limit finds it`() {
        val limit = define("PRIME_SIEVE_1M_LIMIT")
        assertEquals(
            "PRIME_SIEVE_1M_REFERENCE must be the number of primes up to PRIME_SIEVE_1M_LIMIT",
            define("PRIME_SIEVE_1M_REFERENCE"), pi(limit.toInt())
        )
    }

    @Test
    fun `the 10M pair is untouched - it is still pi of its limit, and the Kotlin hook still expects it`() {
        assertEquals(10_000_000L, define("PRIME_SIEVE_10M_LIMIT"))
        assertEquals(664_579L, define("PRIME_SIEVE_10M_REFERENCE"))
        assertEquals(define("PRIME_SIEVE_10M_REFERENCE"), pi(define("PRIME_SIEVE_10M_LIMIT").toInt()))
        assertEquals(define("PRIME_SIEVE_10M_REFERENCE"), StressNative.PRIME_SIEVE_10M_EXPECTED)
    }

    // ───────────────────────── who uses which pair ─────────────────────────

    @Test
    fun `D's native kernel uses the 1M pair only - and records the limit it ran to`() {
        val body = functionBody(cpp("stress/known_answer.cpp"), "SieveKnownAnswer sieve_known_answer()")
        assertTrue(body.contains("prime_sieve_count(PRIME_SIEVE_1M_LIMIT)"))
        assertTrue(body.contains("PRIME_SIEVE_1M_REFERENCE"))
        assertTrue("the limit is read from the header constant, not retyped", body.contains("r.limit = static_cast<int64_t>(PRIME_SIEVE_1M_LIMIT)"))
        assertFalse("D must not touch the 10M pair", body.contains("PRIME_SIEVE_10M"))
    }

    @Test
    fun `runPrimeSieveTest and the legacy production stress stay on the 10M pair - they are not D`() {
        val jni = cpp("jni/stress_jni.cpp")
        val hook = functionBody(jni, "Java_net_neuralcheck_util_StressNative_runPrimeSieveTest")
        assertTrue(hook.contains("PRIME_SIEVE_10M_LIMIT") && hook.contains("PRIME_SIEVE_10M_REFERENCE"))
        assertFalse(hook.contains("PRIME_SIEVE_1M"))
        val production = cpp("stress/production_stress.cpp")
        assertTrue(production.contains("prime_sieve_count(PRIME_SIEVE_10M_LIMIT)"))
        assertTrue(production.contains("prime_count == PRIME_SIEVE_10M_REFERENCE"))
        assertFalse(production.contains("PRIME_SIEVE_1M"))
    }

    // ───────────────────────── the JNI array is the layout Kotlin reads ─────────────────────────

    /** The comma-separated initialiser of `values[kLen] = {...}` inside one JNI function, split. */
    private fun arrayInitialiser(functionName: String): List<String> {
        val body = functionBody(cpp("jni/stress_jni.cpp"), functionName)
        val m = Regex("values\\[kLen\\]\\s*=\\s*\\{([^}]*)\\}").find(body) ?: error("no values[kLen] initialiser in $functionName")
        return m.groupValues[1].split(',').map { it.trim() }
    }

    private fun kLen(functionName: String): Int {
        val body = functionBody(cpp("jni/stress_jni.cpp"), functionName)
        return Regex("constexpr\\s+jsize\\s+kLen\\s*=\\s*([0-9]+)\\s*;").find(body)?.groupValues?.get(1)?.toInt()
            ?: error("no kLen in $functionName")
    }

    @Test
    fun `the sieve array the native side returns is the layout 2 Kotlin reads - version, length and every slot`() {
        val name = "Java_net_neuralcheck_util_StressNative_runSieveKnownAnswer"
        assertEquals(KnownAnswerLayout.SIEVE_SIZE, kLen(name))
        val slots = arrayInitialiser(name)
        assertEquals(KnownAnswerLayout.SIEVE_SIZE, slots.size)
        assertEquals(KnownAnswerLayout.SIEVE_VERSION.toString(), slots[KnownAnswerLayout.SIEVE_IDX_VERSION])
        assertEquals("kLen", slots[KnownAnswerLayout.SIEVE_IDX_LENGTH])
        assertEquals("r.status", slots[KnownAnswerLayout.SIEVE_IDX_STATUS])
        assertEquals("r.prime_count", slots[KnownAnswerLayout.SIEVE_IDX_PRIME_COUNT])
        assertEquals("r.duration_ms", slots[KnownAnswerLayout.SIEVE_IDX_DURATION_MS])
        assertEquals("r.limit", slots[KnownAnswerLayout.SIEVE_IDX_LIMIT])
    }

    @Test
    fun `the SHA array is unchanged - layout 1, four slots`() {
        val name = "Java_net_neuralcheck_util_StressNative_runSha256KnownAnswer"
        assertEquals(KnownAnswerLayout.SHA_SIZE, kLen(name))
        val slots = arrayInitialiser(name)
        assertEquals(KnownAnswerLayout.SHA_SIZE, slots.size)
        assertEquals(KnownAnswerLayout.SHA_VERSION.toString(), slots[KnownAnswerLayout.SHA_IDX_VERSION])
        assertEquals("kLen", slots[KnownAnswerLayout.SHA_IDX_LENGTH])
        assertEquals("r.status", slots[KnownAnswerLayout.SHA_IDX_STATUS])
        assertEquals("r.duration_ms", slots[KnownAnswerLayout.SHA_IDX_DURATION_MS])
    }

    @Test
    fun `the two layouts have versions of their own - the sieve moved to 2, the SHA stayed 1`() {
        assertEquals(2L, KnownAnswerLayout.SIEVE_VERSION)
        assertEquals(1L, KnownAnswerLayout.SHA_VERSION)
    }

    // ───────────────────────── Kotlin keeps no copy (rule 24) ─────────────────────────

    @Test
    fun `Kotlin keeps no copy of D's limit or reference - outside comments`() {
        val files = listOf(
            "net/neuralcheck/data/local/files/CpuKnownAnswer.kt",
            "net/neuralcheck/service/stress/v2/CpuKnownAnswerScheduler.kt",
            "net/neuralcheck/data/remote/BehaviorFingerprintBuilder.kt"
        )
        val forbidden = listOf(
            "1_000_000", "1000000", "78_498", "78498", "664_579", "664579", "10_000_000", "10000000"
        )
        for (f in files) {
            val code = stripKotlinComments(kotlin(f))
            for (n in forbidden) {
                assertFalse("$f carries the literal $n in code: the limit and the reference come from the native layer (rule 24)", code.contains(n))
            }
        }
    }
}
