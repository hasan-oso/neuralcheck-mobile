package net.neuralcheck.hygiene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A SOURCE guard, not a behaviour test: it reads `memory_verify.h` / `memory_verify.cpp` as text
 * (the C++ cannot run in the JVM, so what the condition DOES is proved by the compiler's
 * `static_assert`s in the header and, on the device, by the real call).
 *
 * User decision 2026-10-08 (reading (b)): "The test does not run if free - 500 MiB < 512 MiB, i.e.
 * free < 1012 MiB, and `not_run:insufficient_free_memory` is declared. The condition is written in
 * the code and the contract AS IT IS COMPUTED, not as prose." So this pins that the two numbers it
 * rests on have ONE source (rule 24) and that the condition is the computation, not a copy:
 *  - `kMemoryVerifyReservedBytes` = 500 MiB and `kMemoryVerifyFloorBytes` = 512 MiB, in the header;
 *  - the condition compares `available_safe` with the floor;
 *  - the .cpp holds no literal copy of either number, and no Kotlin file under the memory producer
 *    keeps one (Kotlin only receives the raw free memory and the status).
 */
class MemoryFloorSourceTest {

    private fun cppDir(): File = listOf("app/src/main/cpp/stress", "../app/src/main/cpp/stress")
        .map(::File).firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/cpp/stress from ${File(".").absolutePath}")

    private fun javaDir(): File = listOf("app/src/main/java", "../app/src/main/java")
        .map(::File).firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private val header get() = File(cppDir(), "memory_verify.h").readText()
    private val source get() = File(cppDir(), "memory_verify.cpp").readText()

    private val mib = Regex("""1024ull\s*\*\s*1024ull""")

    @Test
    fun `the reserve is 500 MiB and the floor is 512 MiB - each defined once, in the header`() {
        val reserved = Regex("""kMemoryVerifyReservedBytes\s*=\s*500ull\s*\*\s*1024ull\s*\*\s*1024ull;""")
        val floor = Regex("""kMemoryVerifyFloorBytes\s*=\s*512ull\s*\*\s*1024ull\s*\*\s*1024ull;""")
        assertEquals(1, reserved.findAll(header).count())
        assertEquals(1, floor.findAll(header).count())
    }

    @Test
    fun `the condition is the computation - available_safe against the floor`() {
        val h = header
        assertTrue(
            "available_safe must be free minus the reserve (0 when free does not exceed it)",
            Regex("""memory_verify_available_safe\(size_t free_bytes\)[^{]*\{\s*return \(free_bytes > kMemoryVerifyReservedBytes\) \? \(free_bytes - kMemoryVerifyReservedBytes\) : 0;""")
                .containsMatchIn(h)
        )
        assertTrue(
            "the condition must be available_safe < floor",
            Regex("""memory_verify_insufficient_free_memory\(size_t free_bytes\)[^{]*\{\s*return memory_verify_available_safe\(free_bytes\) < kMemoryVerifyFloorBytes;""")
                .containsMatchIn(h)
        )
        assertTrue("the boundary is asserted by the compiler", h.contains("static_assert(!memory_verify_insufficient_free_memory(1012ull"))
        assertTrue(h.contains("static_assert(memory_verify_insufficient_free_memory(1011ull"))
    }

    @Test
    fun `the producer uses the condition before it maps anything`() {
        val s = source
        val check = s.indexOf("memory_verify_insufficient_free_memory(free_bytes)")
        val firstMap = s.indexOf("map_anonymous(mapped")
        assertTrue("the condition must be used in run_memory_verify", check > 0)
        assertTrue("... and before the first mapping", firstMap > check)
        assertTrue(s.contains("r.status = kMemoryVerifyInsufficientFreeMemory;"))
    }

    @Test
    fun `no second copy of either number in the producer's C++ or in Kotlin`() {
        assertFalse("memory_verify.cpp must use the header's constants", Regex("""\b(500|512)ull\s*\*""").containsMatchIn(source))
        for (name in listOf(
            "data/local/files/MemoryVerifyOutcome.kt",
            "service/stress/v2/NativeVerification.kt"
        )) {
            val text = File(javaDir(), "net/neuralcheck/$name").readText()
            assertFalse(
                "$name must not carry the 500 / 512 MiB numbers (it receives the raw free memory and the status)",
                Regex("""\b(500|512)L?\s*\*\s*1024""").containsMatchIn(text) || Regex("""\b1012\b""").containsMatchIn(text)
            )
        }
    }
}
