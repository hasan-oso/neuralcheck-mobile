package net.neuralcheck.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * P0.1 compatibility note (plan/PLAN-01-ROADMAP.md, section P0.1): "بحث
 * ملفّ الجهاز يتخطّى الطبقة المساوية للحارس" — the server's
 * findDeviceProfile skips a stage when `stage.value === stage.sentinel`
 * (S§6.8). That comparison is only sound if:
 *
 *   (a) the four sentinel strings — one per fallible layer (A, B, C, E;
 *       Layer D never returns a sentinel, see DeviceFingerprint.kt's class
 *       doc) — are pairwise distinct, so failure in one layer is never
 *       confused with failure in another, and
 *   (b) no real SHA-256 digest can ever equal one of those sentinels, so a
 *       successful (now-hashed) Layer B or Layer C is never mistaken for a
 *       failed layer.
 *
 * The literal sentinel values below are copied from DeviceFingerprint.kt's
 * private FALLBACK_* constants (no_keystore / no_fid / no_android_id /
 * no_sensors); they are `private`, not `internal`, so this test cannot
 * reference them by name. If those literals change, this test's copies
 * must be updated to match.
 */
class DeviceProfileKeyLayerDistinctnessTest {

    private val sentinels = listOf(
        "no_keystore", // Layer A
        "no_fid", // Layer B
        "no_android_id", // Layer C
        "no_sensors" // Layer E
    )

    private fun isSha256Hex(s: String) = s.matches(Regex("^[0-9a-f]{64}$"))

    @Test
    fun `the four layer sentinels are pairwise distinct`() {
        assertEquals(
            "a duplicate sentinel would make two different failure causes " +
                "on two different layers indistinguishable to the server's lookup",
            sentinels.size,
            sentinels.toSet().size
        )
    }

    @Test
    fun `no sentinel can ever be mistaken for a SHA-256 digest`() {
        sentinels.forEach { sentinel ->
            assertFalse(
                "sentinel '$sentinel' must not have the shape of a 64-hex-char " +
                    "digest, or the server's stage-skip check could misfire",
                isSha256Hex(sentinel)
            )
        }
    }

    @Test
    fun `hashed Layer B and Layer C values never collide with each other or with any sentinel`() {
        val layerBHashed = DeviceFingerprint.hashedOrSentinel("firebase-install-id-abc", "no_fid")
        val layerCHashed = DeviceFingerprint.hashedOrSentinel("android-id-def", "no_android_id")

        assertNotEquals(
            "Layer B and Layer C must not hash to the same digest for different inputs",
            layerBHashed,
            layerCHashed
        )

        val all = sentinels + listOf(layerBHashed, layerCHashed)
        assertEquals(
            "a hashed layer value collided with one of the four sentinel strings",
            all.size,
            all.toSet().size
        )
    }
}
