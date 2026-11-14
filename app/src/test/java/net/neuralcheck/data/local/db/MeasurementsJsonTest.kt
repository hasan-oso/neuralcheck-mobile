package net.neuralcheck.data.local.db

import net.neuralcheck.data.local.db.converter.MeasurementsJson
import net.neuralcheck.util.ReasonText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * fieldsVersion 68 (N-16): the codec between functional_results.measurements
 * and the Map carried to the DTOs. The properties locked here are the ones the
 * class doc names: numbers keep their kind (rule 20 — raw as produced), and a
 * failure is an explicit entry, never "no measurements" (rule 22).
 */
class MeasurementsJsonTest {

    @Test
    fun `null in, null out - no compound raw stays absent in both directions`() {
        assertNull(MeasurementsJson.encode(null))
        assertNull(MeasurementsJson.decode(null))
    }

    @Test
    fun `integers stay Long and decimals stay Double through a round trip`() {
        val written = mapOf<String, Any?>(
            "touchedCells" to 87L,
            "totalCells" to 120L,
            "elapsedMs" to 4_250L,
            "ratio" to 0.725,
            "endReason" to "auto_complete",
            "flag" to true
        )
        val back = MeasurementsJson.decode(MeasurementsJson.encode(written))!!

        assertEquals(87L, back["touchedCells"])
        assertEquals("a count must not become 120.0", 120L, back["totalCells"])
        assertEquals(0.725, back["ratio"] as Double, 0.0)
        assertEquals("auto_complete", back["endReason"])
        assertEquals(true, back["flag"])
        assertTrue(back["touchedCells"] is Long)
    }

    @Test
    fun `nested lists of objects keep their structure and number kinds`() {
        val written = mapOf<String, Any?>(
            "readingsBeforeCover" to listOf(
                mapOf("t" to 100L, "values" to listOf(12.5, 0.0, 3.0)),
                mapOf("t" to 350L, "values" to listOf(11.75, 0.0, 3.0))
            )
        )
        val back = MeasurementsJson.decode(MeasurementsJson.encode(written))!!

        @Suppress("UNCHECKED_CAST")
        val readings = back["readingsBeforeCover"] as List<Map<String, Any?>>
        assertEquals(2, readings.size)
        assertEquals(100L, readings[0]["t"])
        assertEquals(listOf(12.5, 0.0, 3.0), readings[0]["values"])
        assertEquals(350L, readings[1]["t"])
    }

    @Test
    fun `an empty map is kept as an empty object, not turned into null`() {
        val text = MeasurementsJson.encode(emptyMap())
        assertEquals("{}", text)
        assertEquals(emptyMap<String, Any?>(), MeasurementsJson.decode(text))
    }

    @Test
    fun `a value Gson refuses to write yields an explicit failure entry, never null and never a throw`() {
        val text = MeasurementsJson.encode(mapOf("ratio" to Double.NaN))
        assertNotNull("NaN must not silently drop the measurements", text)

        val back = MeasurementsJson.decode(text)!!
        assertEquals(setOf(MeasurementsJson.ENCODE_FAILED_KEY), back.keys)
        val reason = back[MeasurementsJson.ENCODE_FAILED_KEY] as String
        assertTrue("reason is the message text: $reason", reason.contains("NaN", ignoreCase = true))
        assertTrue(reason.length <= ReasonText.MAX_CHARS)
    }

    @Test
    fun `text that is not a JSON object decodes to an explicit failure entry`() {
        val notJson = MeasurementsJson.decode("this is {not json")!!
        assertEquals(setOf(MeasurementsJson.DECODE_FAILED_KEY), notJson.keys)
        assertTrue((notJson[MeasurementsJson.DECODE_FAILED_KEY] as String).isNotBlank())

        val array = MeasurementsJson.decode("[1,2,3]")!!
        assertEquals(setOf(MeasurementsJson.DECODE_FAILED_KEY), array.keys)

        val jsonNull = MeasurementsJson.decode("null")!!
        assertEquals(setOf(MeasurementsJson.DECODE_FAILED_KEY), jsonNull.keys)

        val empty = MeasurementsJson.decode("")!!
        assertEquals(setOf(MeasurementsJson.DECODE_FAILED_KEY), empty.keys)
    }
}
