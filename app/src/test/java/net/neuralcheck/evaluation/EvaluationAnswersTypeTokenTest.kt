package net.neuralcheck.evaluation

import com.google.gson.reflect.TypeToken
import net.neuralcheck.domain.evaluation.EvaluationAnswers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Type

/**
 * Order 2026-10-14 (user decision, device check 03 round 2, «Root fix + guards», items 1 and 2): why the technician-
 * evaluation card never showed on 1.0.8-1.0.13, and what the fix pins.
 *
 * ── The cause, as relayed by the coordinator (measured on the release APKs there; NOT reproducible here) ──
 * [EvaluationAnswers] built two Gson types in its initializer with `object : TypeToken<...>() {}`. R8 (AGP 8.2.2,
 * full mode) merged those two anonymous subclasses into one class and dropped its generic signature; Gson 2.10.1 then
 * throws in `TypeToken()` ("TypeToken must be created with a type argument ... generic signatures are preserved"),
 * so the object failed to initialise at its first use (`emptyDraft`, in `TechnicianEvaluationViewModel.bind`).
 *
 * ── What these tests can and cannot show (rule 37, said once) ──
 * This module's JVM tests run on the debug classes with NO R8, so none of them can reproduce the failure — "a JVM test
 * of «bind before the row» passes on today's code" (the user's own words in the decision). What they CAN pin is the
 * cause's precondition and its two removers:
 *  1. the compiled object has NO class extending [TypeToken] (nothing for R8 to merge or strip) — read from the
 *     compiled classes, not only from the text;
 *  2. the source builds the types with `TypeToken.getParameterized`, and the two types are the SAME types as before;
 *  3. `app/proguard-rules.pro` carries Gson's own two rules that keep the signature of TypeToken subclasses.
 * Whether R8 then keeps what it should is proved by the build block's check of the release APK, not here.
 */
class EvaluationAnswersTypeTokenTest {

    private val projectRoot: File = listOf(".", "..")
        .map(::File)
        .firstOrNull { File(it, "app/src/main/java").isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private fun stripComments(source: String): String =
        source.replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .lines()
            .joinToString("\n") { it.substringBefore("//") }

    private val answersSource: String by lazy {
        stripComments(File(projectRoot, "app/src/main/java/net/neuralcheck/domain/evaluation/EvaluationAnswers.kt").readText())
    }

    /** The private static field of the object, read by reflection. */
    private fun typeField(name: String): Type {
        val f = EvaluationAnswers::class.java.getDeclaredField(name)
        f.isAccessible = true
        return f.get(null) as Type
    }

    // ---- 1. nothing for R8 to merge or strip ----

    /** The names (no `.class`) of every class file of the object's package whose name starts with the object's own. */
    private fun compiledClassNames(): List<String> {
        val dir = "net/neuralcheck/domain/evaluation"
        val location = File(EvaluationAnswers::class.java.protectionDomain.codeSource.location.toURI())
        return when {
            // AGP hands the unit tests the module's classes as one jar (runtime_app_classes_jar/.../classes.jar) ...
            location.isFile -> java.util.jar.JarFile(location).use { jar ->
                jar.entries().asSequence().map { it.name }
                    .filter { it.startsWith("$dir/EvaluationAnswers") && it.endsWith(".class") }
                    .map { it.removePrefix("$dir/").removeSuffix(".class") }
                    .toList()
            }
            // ... or, in another layout, as a directory of class files.
            location.isDirectory -> File(location, dir).listFiles()
                ?.filter { it.name.startsWith("EvaluationAnswers") && it.name.endsWith(".class") }
                ?.map { it.name.removeSuffix(".class") }
                .orEmpty()
            else -> error("cannot read the module's compiled classes from $location")
        }
    }

    @Test
    fun `the compiled EvaluationAnswers has no class that extends TypeToken`() {
        val names = compiledClassNames()
        assertTrue("found the object's own class among $names", names.contains("EvaluationAnswers"))

        val subclassesOfTypeToken = names.filter {
            TypeToken::class.java.isAssignableFrom(Class.forName("net.neuralcheck.domain.evaluation.$it"))
        }
        assertEquals(
            "no class compiled from EvaluationAnswers (anonymous or named) extends TypeToken; checked $names",
            emptyList<String>(), subclassesOfTypeToken
        )
    }

    // ---- 2. the source, and the two types ----

    @Test
    fun `EvaluationAnswers_kt writes no anonymous TypeToken subclass and builds both types with getParameterized`() {
        val anonymous = Regex("""object\s*:\s*(?:[\w.]*\.)?TypeToken\b""")
        assertFalse(
            "an anonymous `object : TypeToken<...>() {}` is what R8 merged and stripped (order 2026-10-14)",
            anonymous.containsMatchIn(answersSource)
        )
        assertEquals(
            "two types, two calls: the map type and the list type",
            2, Regex("""TypeToken\.getParameterized\(""").findAll(answersSource).count()
        )
    }

    @Test
    fun `the two types are the same types the anonymous subclasses described`() {
        // The anonymous form is built HERE, in a test (no R8), only to say what the types were before the change.
        val expectedMap = object : TypeToken<LinkedHashMap<String, String>>() {}.type
        val expectedList = object : TypeToken<ArrayList<String>>() {}.type

        assertEquals(expectedMap, typeField("mapType"))
        assertEquals(expectedList, typeField("listType"))
        assertEquals(expectedMap.hashCode(), typeField("mapType").hashCode())
        assertEquals(expectedList.hashCode(), typeField("listType").hashCode())
    }

    @Test
    fun `parsing through the new types behaves as before - order kept, nulls and garbage give the empty answer`() {
        val ratings = EvaluationAnswers.parseCategoryRatings("""{"storage":"good","battery":"weak","memory":"not_tested"}""")
        assertEquals(listOf("storage", "battery", "memory"), ratings.keys.toList())
        assertTrue("a LinkedHashMap, so the insertion order survives", ratings is LinkedHashMap<*, *>)
        assertEquals(listOf("water_damage", "prior_repair"), EvaluationAnswers.parseDefects("""["water_damage","prior_repair"]"""))

        assertEquals(emptyMap<String, String>(), EvaluationAnswers.parseCategoryRatings("not json"))
        assertEquals(emptyList<String>(), EvaluationAnswers.parseDefects("{"))
        assertNotNull(EvaluationAnswers.parseCategoryRatings("{}"))
    }

    // ---- 3. the rules ----

    @Test
    fun `proguard-rules_pro carries Gson's two keep rules for TypeToken and its subclasses`() {
        val rules = File(projectRoot, "app/proguard-rules.pro").readLines()
            .map { it.substringBefore('#').trim() }
            .filter { it.isNotEmpty() }
        assertTrue(
            "the rule that keeps TypeToken's own generic signature",
            rules.contains("-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken")
        )
        assertTrue(
            "the rule that keeps the signature of every class extending TypeToken",
            rules.contains("-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken")
        )
    }
}
