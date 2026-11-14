package net.neuralcheck.data.local.db.converter

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.ToNumberPolicy
import com.google.gson.reflect.TypeToken
import net.neuralcheck.util.ReasonText

/**
 * fieldsVersion 68 (S-29 (a), N-16, order 2026-10-08): codec between the
 * `functional_results.measurements` TEXT column (one JSON object) and the
 * `Map<String, Any?>` carried by `TestResult.measurements` and the
 * `measurements` field of `FunctionalResultDto` / `ManualResultDto`.
 *
 * Two properties this codec exists to guarantee:
 *
 *  1. **Numbers keep their kind.** Gson's default turns every JSON number in
 *     a `Map<String, Any>` into `Double`, so an integer counter written as
 *     `3` would come back as `3.0` and reach the server as `3.0` — a
 *     different value type for the same raw. [ToNumberPolicy.LONG_OR_DOUBLE]
 *     reads `3` as Long and `3.5` as Double. (Rule 20: raw is stored and
 *     shipped as produced; nothing here converts or rounds.)
 *
 *  2. **A failure is never read as "no measurements"** (rule 22). Both
 *     directions can fail — [encode] on a value Gson refuses to write
 *     (`NaN`/`Infinity`), [decode] on text that is not a JSON object. A
 *     silent `null` would be indistinguishable from "this test supplied no
 *     compound raw", and a thrown exception would — at the one call site
 *     that maps every result of a scan inside a single try/catch
 *     (StressTestService, "Failed to save test results") — drop ALL the
 *     scan's results for one bad value. So a failure is returned as an
 *     explicit one-entry object, [ENCODE_FAILED_KEY] / [DECODE_FAILED_KEY]
 *     mapped to the failure text (capped by [ReasonText]; rule 21: the
 *     message text, not the exception class).
 */
object MeasurementsJson {

    /** Key of the explicit failure entry written by [encode]. */
    const val ENCODE_FAILED_KEY = "measurementsUnencodable"

    /** Key of the explicit failure entry returned by [decode]. */
    const val DECODE_FAILED_KEY = "measurementsUnreadable"

    private val gson: Gson = GsonBuilder()
        .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
        .create()

    private val mapType = TypeToken.getParameterized(
        Map::class.java, String::class.java, Any::class.java
    ).type

    /**
     * @return the JSON object text for [measurements], or `null` when
     *   [measurements] is null (the test supplied none). On an encoding
     *   failure: the JSON text of the one-entry failure object, never null.
     */
    fun encode(measurements: Map<String, Any?>?): String? {
        if (measurements == null) return null
        return try {
            gson.toJson(measurements, mapType)
        } catch (t: Exception) {
            gson.toJson(mapOf(ENCODE_FAILED_KEY to ReasonText.of(t)))
        }
    }

    /**
     * @return the decoded map, or `null` when [json] is null (column NULL =
     *   none supplied). Text that is not a JSON object yields the one-entry
     *   [DECODE_FAILED_KEY] map, never null.
     */
    fun decode(json: String?): Map<String, Any?>? {
        if (json == null) return null
        return try {
            gson.fromJson<Map<String, Any?>>(json, mapType)
                ?: mapOf(DECODE_FAILED_KEY to "column text decodes to JSON null or empty text")
        } catch (t: Exception) {
            mapOf(DECODE_FAILED_KEY to ReasonText.of(t))
        }
    }
}
