package net.neuralcheck.data.remote.model

import com.google.gson.TypeAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * AI-predicted problem entry. Replaces the legacy `List<String>` shape
 * for `predicted_problems` after the V2 backend started returning
 * `[{ "timeframe": "...", "prediction": "..." }, ...]`.
 *
 * Custom [PredictedProblemTypeAdapter] at the type level is the
 * critical part — it accepts BOTH the new object shape AND the legacy
 * raw-string shape so:
 *
 *   1. Existing reports in `reports/{scanId}` Firestore cache (written
 *      with `["bullet a", "bullet b"]`) keep deserialising — those
 *      strings become PredictedProblem(timeframe=null, prediction=str).
 *   2. Existing AiAnalysisResult JSON snapshots in Room (saved by
 *      ReportViewModel) keep deserialising for the same reason.
 *   3. New backend responses (`[{timeframe, prediction}, ...]`) deserialise
 *      as the structured form they were authored in.
 *
 * Without this dual handling, opening a freshly-cached report crashes
 * with `Expected a string but was BEGIN_OBJECT` (the bug reported in
 * the migration prompt) and the entire ReportScreen fails to render.
 */
@JsonAdapter(PredictedProblemTypeAdapter::class)
data class PredictedProblem(
    /** Optional human-readable timeframe (e.g. "3-6 months"). Null on
     *  legacy entries that were authored as flat strings. */
    val timeframe: String?,
    /** The actual prediction text. Always non-null but may be empty
     *  when the backend ships malformed JSON; consumers should
     *  defensive-skip on isBlank(). */
    val prediction: String
)

/**
 * Reads either a JSON string OR an object `{timeframe, prediction}`
 * into a [PredictedProblem]. Writes always as the object form (the
 * canonical V2 shape). Skipping null/missing fields is tolerant —
 * partial backend payloads don't crash the deserializer.
 */
class PredictedProblemTypeAdapter : TypeAdapter<PredictedProblem>() {
    override fun read(reader: JsonReader): PredictedProblem {
        return when (reader.peek()) {
            JsonToken.STRING -> {
                // Legacy shape: bare string in a List<String>.
                PredictedProblem(timeframe = null, prediction = reader.nextString())
            }
            JsonToken.BEGIN_OBJECT -> {
                var timeframe: String? = null
                var prediction: String = ""
                reader.beginObject()
                while (reader.hasNext()) {
                    when (reader.nextName()) {
                        "timeframe" -> timeframe = reader.nextStringOrNull()
                        "prediction" -> prediction = reader.nextStringOrNull() ?: ""
                        else -> reader.skipValue()
                    }
                }
                reader.endObject()
                PredictedProblem(timeframe = timeframe, prediction = prediction)
            }
            JsonToken.NULL -> {
                reader.nextNull()
                PredictedProblem(timeframe = null, prediction = "")
            }
            else -> {
                // Defensive: any unexpected token gets skipped to a
                // neutral "empty" entry rather than crashing the whole
                // List<PredictedProblem> deserialization.
                reader.skipValue()
                PredictedProblem(timeframe = null, prediction = "")
            }
        }
    }

    override fun write(writer: JsonWriter, value: PredictedProblem?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginObject()
        writer.name("timeframe").value(value.timeframe)
        writer.name("prediction").value(value.prediction)
        writer.endObject()
    }

    /** Reads a string OR null (Gson treats `"prediction": null` as
     *  JSON null token) without crashing on either. */
    private fun JsonReader.nextStringOrNull(): String? {
        return if (peek() == JsonToken.NULL) {
            nextNull(); null
        } else {
            nextString()
        }
    }
}
