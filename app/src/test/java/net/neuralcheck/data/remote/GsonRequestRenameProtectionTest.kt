package net.neuralcheck.data.remote

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import net.neuralcheck.data.remote.model.BehaviorFingerprint
import net.neuralcheck.util.DeviceProfileKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Field
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

/**
 * User decision 2026-10-09, item 5, step one of the device-key fix (order to the app agent):
 * the release APK sent `deviceProfileKey` under R8's letters (`a`..`f`) instead of
 * `primary` / `layerA_keystore` / ... / `layerE_sensorSig`, because DeviceProfileKey lives in
 * net.neuralcheck.util and no keep rule covered that package. Evidence measured on the existing
 * release mapping of 62507f4 (app/build/outputs/mapping/launchRelease/mapping.txt, 2026-10-07):
 * "net.neuralcheck.util.DeviceProfileKey -> gf.t" with primary -> a ... layerE_sensorSig -> f.
 *
 * This test inventories every class Gson can reach when it serialises the `analyzeDevice`
 * request (BehaviorFingerprint, by the declared field types, exactly as Gson's reflective
 * adapter walks them: non-static, non-transient fields, generic arguments included) and checks
 * that each one is protected from R8 renaming in one of the two ways the project uses:
 *  (a) a `-keep class <pattern> { *; }` (or -keepclassmembers) line of app/proguard-rules.pro whose
 *      pattern matches the class — the rules file is PARSED here, not copied; or
 *  (b) every serialised field carries `@SerializedName`, so the wire name is the annotation's
 *      value whatever R8 does to the field.
 *
 * LIMITS, said once: it runs on the debug classes in the JVM and reads the rules file; it does
 * NOT run R8 and it does not see the release payload. A value typed `Any` (the footer's
 * Map<String, Any?> fields) is typed only at run time, so a type walk cannot see it: those maps
 * are filled in BehaviorFingerprintBuilder from primitives, strings, lists and maps (read by
 * hand when this test was written), and the rules of the library and other consumers
 * (consumer rules of Gson, Firebase) are outside this file. The release proof is the R8 mapping
 * check (checkDeviceProfileKeyMapping) and a release build, which this repository's agent does
 * not run without an explicit order.
 */
class GsonRequestRenameProtectionTest {

    private val moduleDir: File = listOf("proguard-rules.pro", "app/proguard-rules.pro")
        .map(::File).firstOrNull { it.isFile }
        ?.absoluteFile?.parentFile
        ?: error("could not locate app/proguard-rules.pro from ${File(".").absolutePath}")

    private val rulesLines: List<String> = File(moduleDir, "proguard-rules.pro").readLines()

    /** `-keep class X { *; }` / `-keepclassmembers class X { *; }` on ONE line, no modifiers (a modifier such as allowobfuscation would not protect the name). */
    private val keepRule = Regex("""^-keep(?:classmembers)?\s+class\s+(\S+)\s*\{\s*\*;\s*\}\s*$""")

    private fun keepPatterns(): List<String> =
        rulesLines.map { it.trim() }.mapNotNull { keepRule.matchEntire(it)?.groupValues?.get(1) }

    /** ProGuard class-name wildcards: `**` = any characters, `*` = any characters except the package separator. */
    private fun patternToRegex(p: String): Regex {
        val sb = StringBuilder()
        var i = 0
        while (i < p.length) {
            val c = p[i]
            when {
                c == '*' && i + 1 < p.length && p[i + 1] == '*' -> { sb.append(".*"); i += 2 }
                c == '*' -> { sb.append("[^.]*"); i++ }
                c == '?' -> { sb.append("[^.]"); i++ }
                else -> { sb.append(Regex.escape(c.toString())); i++ }
            }
        }
        return Regex(sb.toString())
    }

    private fun matchedBy(c: Class<*>): String? =
        keepPatterns().firstOrNull { patternToRegex(it).matches(c.name) }

    /** The fields Gson's reflective adapter serialises: declared in the class and its superclasses, not static, not transient. */
    private fun gsonFields(c: Class<*>): List<Field> {
        val out = mutableListOf<Field>()
        var k: Class<*>? = c
        while (k != null && k != Any::class.java) {
            out += k.declaredFields.filter { !Modifier.isStatic(it.modifiers) && !Modifier.isTransient(it.modifiers) && !it.isSynthetic }
            k = k.superclass
        }
        return out
    }

    /** The JSON keys Gson takes from [c]: per serialised field, the @SerializedName value, else the field's name. */
    private fun gsonKeys(c: Class<*>): List<String> =
        gsonFields(c).map { it.getAnnotation(SerializedName::class.java)?.value ?: it.name }

    private fun isFramework(c: Class<*>): Boolean {
        val n = c.name
        return c.isPrimitive || n.startsWith("java.") || n.startsWith("javax.") || n.startsWith("kotlin.") ||
            n.startsWith("kotlinx.") || n.startsWith("android.") || n.startsWith("com.google.gson.")
    }

    /** Every non-framework class reachable from [root] through serialised fields. */
    private fun reachable(root: Class<*>): Set<Class<*>> {
        val seen = linkedSetOf<Class<*>>()
        fun visit(t: Type) {
            when (t) {
                is Class<*> -> {
                    if (t.isArray) { visit(t.componentType); return }
                    if (isFramework(t) || t.isInterface) return
                    if (!seen.add(t)) return
                    gsonFields(t).forEach { visit(it.genericType) }
                }
                is ParameterizedType -> { visit(t.rawType); t.actualTypeArguments.forEach(::visit) }
                is GenericArrayType -> visit(t.genericComponentType)
                is WildcardType -> t.upperBounds.forEach(::visit)
                else -> Unit // type variables: nothing to name
            }
        }
        visit(root)
        return seen
    }

    private fun annotatedOnEveryField(c: Class<*>): Boolean {
        if (c.isEnum) {
            return c.enumConstants.isNotEmpty() && c.declaredFields.filter { it.isEnumConstant }
                .all { it.getAnnotation(SerializedName::class.java) != null }
        }
        val fields = gsonFields(c)
        return fields.isNotEmpty() && fields.all { it.getAnnotation(SerializedName::class.java) != null }
    }

    private fun protectionOf(c: Class<*>): String? =
        matchedBy(c)?.let { "keep rule `$it`" }
            ?: if (annotatedOnEveryField(c)) "@SerializedName on every field" else null

    // ──────────────────────────── the inventory ────────────────────────────

    @Test
    fun `the walker finds exactly the classes the analyzeDevice request is built from`() {
        val names = reachable(BehaviorFingerprint::class.java).map { it.simpleName }.toSortedSet()
        // Printed so a reviewer sees the inventory and how each class is protected (test report, system-out).
        reachable(BehaviorFingerprint::class.java).forEach {
            println("GSON-REQUEST-CLASS ${it.name} -> ${protectionOf(it) ?: "UNPROTECTED"}")
            // The JSON keys Gson takes from this class: the @SerializedName value, else the field's name.
            println("GSON-REQUEST-KEYS ${it.name} | ${gsonKeys(it).sorted().joinToString(",")}")
        }
        assertEquals(
            "a class was added to or removed from the request's type graph: look at it (rule 25), then update this list",
            sortedSetOf(
                "BehaviorFingerprint", "DeviceInfo", "DeviceProfileKey", "FunctionalResultDto", "ManualResultDto",
                // fieldsVersion 69 (N-17): the manual-results header; under data.remote.model.**, so the existing keep rule covers it.
                "ManualResultsHeaderDto", "RawTimeSeriesPayload", "TimeSeriesFooterDto", "TimeSeriesHeaderDto", "TimeSeriesSampleDto", "VariableDto"
            ),
            names
        )
    }

    @Test
    fun `every class Gson serialises in the analyzeDevice request is protected from R8 renaming`() {
        val unprotected = reachable(BehaviorFingerprint::class.java).filter { protectionOf(it) == null }.map { it.name }
        assertEquals(
            "these classes are serialised by Gson in the analyzeDevice request and R8 may rename their fields " +
                "(no keep rule matches, and not every field has @SerializedName) — the release APK would send a, b, c...",
            emptyList<String>(),
            unprotected
        )
    }

    @Test
    fun `the rules file keeps annotations and the names of annotated fields`() {
        val lines = rulesLines.map { it.trim() }
        assertTrue("-keepattributes *Annotation* is what lets R8 keep @SerializedName at all", lines.any { it == "-keepattributes *Annotation*" })
        val text = rulesLines.joinToString("\n")
        assertTrue(
            "the catch-all rule that keeps the backing field of every @SerializedName field is gone",
            Regex("""-keepclassmembers\s+class\s+\*\s*\{\s*@com\.google\.gson\.annotations\.SerializedName\s+<fields>;\s*\}""").containsMatchIn(text)
        )
        assertTrue("the rules file must hold at least one parsable keep rule (the parser is not blind)", keepPatterns().size >= 5)
    }

    // ──────────────────────────── DeviceProfileKey on the wire ────────────────────────────

    private val wireNames = listOf("primary", "layerA_keystore", "layerB_firebaseId", "layerC_androidId", "layerD_buildSig", "layerE_sensorSig")

    @Test
    fun `each DeviceProfileKey field is pinned to its source name and the JSON has those six keys`() {
        val fields = DeviceProfileKey::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
        assertEquals(wireNames.toSet(), fields.map { it.name }.toSet())
        for (f in fields) {
            val ann = f.getAnnotation(SerializedName::class.java)
            assertNotNull("${f.name} has no @SerializedName: R8 may rename it", ann)
            assertEquals("the wire name must equal the source name the server reads", f.name, ann!!.value)
            assertTrue("no alternate names: the wire key is one name", ann.alternate.isEmpty())
        }
        val json = GsonBuilder().create().toJson(DeviceProfileKey("p", "a", "b", "c", "d", "e"))
        @Suppress("UNCHECKED_CAST")
        val keys = (GsonBuilder().create().fromJson(json, Map::class.java) as Map<String, Any?>).keys.toList()
        assertEquals("the JSON has exactly the six source-named keys", wireNames.toSet(), keys.toSet())
    }

    // What the mechanism does, shown on stand-in classes whose field names R8 would produce.
    private class RenamedNoAnnotation(val a: String, val b: String)
    private class RenamedWithAnnotation(@SerializedName("primary") val a: String, @SerializedName("layerA_keystore") val b: String)

    @Test
    fun `Gson takes the key from the annotation, not from the (renamed) field - and from the field when there is none`() {
        val gson = GsonBuilder().create()
        assertEquals("""{"a":"x","b":"y"}""", gson.toJson(RenamedNoAnnotation("x", "y")))
        assertEquals("""{"primary":"x","layerA_keystore":"y"}""", gson.toJson(RenamedWithAnnotation("x", "y")))
    }
}
