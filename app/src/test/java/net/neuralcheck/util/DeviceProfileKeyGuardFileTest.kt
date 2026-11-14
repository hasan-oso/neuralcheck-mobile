package net.neuralcheck.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

/**
 * The release guard `checkDeviceProfileKeyMapping` (app/build.gradle.kts) compares R8's mapping.txt
 * with app/guard/deviceProfileKeyMapping.expected.properties. Since 2026-10-09 (user decision 5)
 * that file states the SOURCE names: every DeviceProfileKey field against itself, because the
 * release must emit `primary` ... `layerE_sensorSig`, not R8's letters.
 *
 * Rule 24: the six names are a literal a distinction is built on. They live in DeviceProfileKey
 * (and its @SerializedName values, pinned by GsonRequestRenameProtectionTest); this test makes the
 * guard's file follow them instead of being a seventh hand copy: a field added to, removed from or
 * renamed in DeviceProfileKey fails here until the guard file is updated with it.
 */
class DeviceProfileKeyGuardFileTest {

    private val guardFile: File = listOf("guard/deviceProfileKeyMapping.expected.properties", "app/guard/deviceProfileKeyMapping.expected.properties")
        .map(::File).firstOrNull { it.isFile }
        ?: error("could not locate app/guard/deviceProfileKeyMapping.expected.properties from ${File(".").absolutePath}")

    @Test
    fun `the guard file lists exactly the DeviceProfileKey fields, each against its own name`() {
        val expected = guardFile.readLines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { line -> line.substringBefore("=").trim() to line.substringAfter("=").trim() }
        val fields = DeviceProfileKey::class.java.declaredFields.filter { !Modifier.isStatic(it.modifiers) }.map { it.name }
        assertEquals("the guard file's keys must be DeviceProfileKey's fields", fields.toSet(), expected.map { it.first }.toSet())
        assertEquals("no field listed twice", fields.size, expected.size)
        assertEquals(
            "each name must map to itself: the release must emit the source names, not R8's letters",
            expected.map { it.first },
            expected.map { it.second }
        )
    }
}
