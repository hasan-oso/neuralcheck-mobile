package net.neuralcheck.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Layers B (Firebase Installation ID) and C (ANDROID_ID) used to be
 * serialised RAW into the analyzeDevice payload via
 * DeviceInfo.deviceProfileKey — two durable device identifiers leaving the
 * device in the clear, while the project describes the fingerprint as
 * anonymous. They are now emitted as SHA-256 digests.
 *
 * The sentinels stay PLAIN, and that is load-bearing on the server rather
 * than a style choice: `findDeviceProfile` skips a stage when
 * `stage.value === stage.sentinel`. A hashed sentinel stops matching that
 * comparison, and the lookup degrades into an equality query on "this
 * device has no identifier" — linking unrelated devices together.
 */
class DeviceFingerprintHashingTest {

    private val fidSentinel = "no_fid"
    private val androidIdSentinel = "no_android_id"

    private fun isSha256Hex(s: String) = s.matches(Regex("^[0-9a-f]{64}$"))

    @Test
    fun `a real identifier is emitted as a digest, never in the clear`() {
        val fid = "cX9kQ2mTRb2abcdefghijk"
        val out = DeviceFingerprint.hashedOrSentinel(fid, fidSentinel)
        assertNotEquals("the raw identifier must not survive", fid, out)
        assertTrue("expected a SHA-256 hex digest, got: $out", isSha256Hex(out))
    }

    @Test
    fun `a real ANDROID_ID is emitted as a digest`() {
        val androidId = "9774d56d682e549c"
        val out = DeviceFingerprint.hashedOrSentinel(androidId, androidIdSentinel)
        assertNotEquals(androidId, out)
        assertTrue(isSha256Hex(out))
    }

    @Test
    fun `sentinels stay plain so the server's stage-skip keeps matching`() {
        // If these were hashed, findDeviceProfile's
        // `stage.value === stage.sentinel` check would stop firing and the
        // lookup would equality-match "no identifier" across devices.
        assertEquals(fidSentinel, DeviceFingerprint.hashedOrSentinel(null, fidSentinel))
        assertEquals(fidSentinel, DeviceFingerprint.hashedOrSentinel("", fidSentinel))
        assertEquals(fidSentinel, DeviceFingerprint.hashedOrSentinel("   ", fidSentinel))
        assertEquals(
            androidIdSentinel,
            DeviceFingerprint.hashedOrSentinel(null, androidIdSentinel)
        )
    }

    @Test
    fun `a sentinel is never mistaken for a digest`() {
        // The property the server relies on: sentinel and digest are
        // distinguishable by shape alone.
        assertTrue(!isSha256Hex(fidSentinel))
        assertTrue(!isSha256Hex(androidIdSentinel))
    }

    @Test
    fun `hashing is deterministic - the key still identifies the device`() {
        // The whole point of the layer survives hashing: same input, same
        // output across calls, so a device still matches itself.
        val id = "same-installation-id"
        assertEquals(
            DeviceFingerprint.hashedOrSentinel(id, fidSentinel),
            DeviceFingerprint.hashedOrSentinel(id, fidSentinel)
        )
    }

    @Test
    fun `different devices still produce different values`() {
        assertNotEquals(
            DeviceFingerprint.hashedOrSentinel("device-a", fidSentinel),
            DeviceFingerprint.hashedOrSentinel("device-b", fidSentinel)
        )
    }

    @Test
    fun `a value that merely looks like the sentinel is still hashed`() {
        // Defensive: only a blank/null value takes the sentinel branch, so
        // a device whose real id happened to read "no_fid" would still be
        // hashed rather than silently treated as absent.
        val out = DeviceFingerprint.hashedOrSentinel("no_fid_but_real", fidSentinel)
        assertTrue(isSha256Hex(out))
    }
}
