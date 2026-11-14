package net.neuralcheck.data.remote.model

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * Generalizes [PredictedProblemTypeAdapter]'s defensive peek-then-branch
 * shape (item 4, phase 3-a) for the two V7 fields whose NAME the contract
 * gives (`coverage`, `coveragePct` on [AiOverallV7] / `coverage` on
 * [AiCategoryV7]) but whose JSON TYPE it does not specify.
 *
 * Why this exists instead of a plain `Double?`/`Int?` field: stock Gson's
 * own number adapters already tolerate a numeric-looking STRING (Gson
 * parses in lenient mode), but they THROW `JsonSyntaxException` on a
 * token they don't expect at all — an object, an array, or a genuinely
 * non-numeric string. That exception propagates out of
 * `gson.fromJson(json, AiAnalysisResponse::class.java)` in
 * [net.neuralcheck.data.remote.FirebaseAnalysisService.analyzeDevice],
 * which has no local try/catch — it is caught only by
 * [net.neuralcheck.domain.scoring.AiScoringEngine]'s broad
 * `catch (t: Throwable)`, which discards the ENTIRE response (every field,
 * not just this one) and renders a generic connection-style error. Losing
 * one field is the acceptable failure mode here (rule 22: the field comes
 * back null, an explicit "not read" state); losing the whole report to a
 * single unexpected token is not — it is indistinguishable from a real
 * network failure and defeats the entire point of this phase (read the
 * report completely, drop no field).
 */
class LenientDoubleAdapter : TypeAdapter<Double>() {
    override fun read(reader: JsonReader): Double? = when (reader.peek()) {
        JsonToken.NUMBER -> reader.nextDouble()
        JsonToken.STRING -> reader.nextString().toDoubleOrNull()
        JsonToken.NULL -> { reader.nextNull(); null }
        JsonToken.BOOLEAN -> { reader.nextBoolean(); null }
        else -> { reader.skipValue(); null }
    }

    override fun write(writer: JsonWriter, value: Double?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }
}

/**
 * Same reasoning as [LenientDoubleAdapter], for
 * [net.neuralcheck.data.remote.model.AiCategoryV7.socAtStart] /
 * [net.neuralcheck.data.remote.model.AiCategoryV7.socAtEnd] (battery-only
 * state-of-charge percentages, integer-shaped, contract type unspecified).
 *
 * Corrected 2026-09-24 (جولة جرد القيم الافتراضية، البند ط): this comment
 * previously named `coveragePct` here — stale, and never accurate.
 * `coveragePct` (on [net.neuralcheck.data.remote.model.AiOverallV7]) is
 * `Double`-typed and reads through [LenientDoubleAdapter] via its own
 * `@JsonAdapter` annotation; this class has never been attached to it. The
 * only two fields actually annotated `@JsonAdapter(LenientIntAdapter::class)`
 * in this codebase are `socAtStart`/`socAtEnd` above.
 *
 * Truncates a fractional NUMBER token rather than throwing — losing
 * sub-integer precision on a state-of-charge percentage is a reasonable
 * trade, silently swallowing the whole response is not. This truncation
 * happens at JSON-parse time, on the value that flows straight into
 * [net.neuralcheck.domain.model.AiAnalysisResult.categories] (which reuses
 * [net.neuralcheck.data.remote.model.AiCategoryV7] directly, no separate
 * domain type) and from there into Room's `aiAnalysisJson` blob — it is
 * NOT a display-only rounding step applied by [net.neuralcheck.util.ReportPdfGenerator]
 * or [net.neuralcheck.presentation.screen.ReportScreen]. Whether that is
 * itself consistent with rule 20 (raw values stored unrounded) is a
 * separate question this comment fix does not decide or resolve — flagged,
 * not changed, per this order's explicit "لا تغيّر ما يُخزَّن".
 */
class LenientIntAdapter : TypeAdapter<Int>() {
    override fun read(reader: JsonReader): Int? = when (reader.peek()) {
        JsonToken.NUMBER -> reader.nextDouble().toInt()
        JsonToken.STRING -> reader.nextString().toDoubleOrNull()?.toInt()
        JsonToken.NULL -> { reader.nextNull(); null }
        JsonToken.BOOLEAN -> { reader.nextBoolean(); null }
        else -> { reader.skipValue(); null }
    }

    override fun write(writer: JsonWriter, value: Int?) {
        if (value == null) writer.nullValue() else writer.value(value)
    }
}

/**
 * Generalizes [LenientDoubleAdapter]/[LenientIntAdapter]'s peek-then-branch
 * shape to a `List<String>?` field — first used for
 * [AiCategoryV7.adjustmentEvidence]. That field was typed plain `String?`
 * until a report (relayed from neuralcheck-backend's
 * `buildDisplayShapeV7.js`/`analyzeDevice.js`, both gating the field with
 * `Array.isArray(...)`) and a real observed value
 * (`["categoryStates.categories.storage.deterministicLetter"]`) showed the
 * true JSON shape is an ARRAY, not a bare string — undetected until now
 * because the one golden sample in this repo
 * (`app/src/test/resources/contract/v7-client-payload-sample.json`) never
 * exercises an `LLM_ADJUSTED` category, so every entry in it has
 * `adjustmentEvidence: null`, which parses fine under either type. A plain
 * `String?` field hitting a real `BEGIN_ARRAY` token throws
 * `JsonSyntaxException` out of `gson.fromJson(json, AiAnalysisResponse::class.java)`
 * with no local catch — same failure mode [LenientDoubleAdapter]'s doc
 * describes: the WHOLE response is lost, not just this field.
 *
 * Not independently confirmed against a live LLM_ADJUSTED payload from
 * this app repo (this repo cannot read neuralcheck-backend) — the lenient
 * adapter here is deliberate defense-in-depth for exactly that residual
 * uncertainty: an ARRAY parses to the list; any other shape (a bare
 * string/number/object, should the contract ever differ from what was
 * relayed) degrades to `null` instead of failing the whole parse.
 *
 * Array elements: STRING/NUMBER/BOOLEAN tokens are all accepted via
 * [JsonReader.nextString], which Gson itself treats as coercible to a
 * string (mirrors [LenientDoubleAdapter] accepting a numeric-looking
 * STRING). A NULL element is dropped; any other per-element token
 * (nested object/array) is skipped rather than failing the whole list.
 */
class LenientStringListAdapter : TypeAdapter<List<String>>() {
    override fun read(reader: JsonReader): List<String>? = when (reader.peek()) {
        JsonToken.BEGIN_ARRAY -> {
            reader.beginArray()
            val items = mutableListOf<String>()
            while (reader.hasNext()) {
                when (reader.peek()) {
                    JsonToken.STRING, JsonToken.NUMBER, JsonToken.BOOLEAN -> items.add(reader.nextString())
                    JsonToken.NULL -> reader.nextNull()
                    else -> reader.skipValue()
                }
            }
            reader.endArray()
            items
        }
        JsonToken.NULL -> { reader.nextNull(); null }
        else -> { reader.skipValue(); null }
    }

    override fun write(writer: JsonWriter, value: List<String>?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginArray()
        value.forEach { writer.value(it) }
        writer.endArray()
    }
}
