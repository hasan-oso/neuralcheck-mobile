package net.neuralcheck.data.remote

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.neuralcheck.data.remote.model.BehaviorFingerprint

/**
 * The one place that turns a [BehaviorFingerprint] into what the `analyzeDevice`
 * callable receives (fieldsVersion 68, rules 24 / 37 / 39).
 *
 * Before 68 this was two statements inside `FirebaseAnalysisService.analyzeDevice`,
 * which the unit tests could not reach — so every test that wanted "the payload
 * as the server sees it" re-created `GsonBuilder().create()` by hand, and a change
 * to the production configuration (say `serializeNulls()`) would have left them
 * all passing against a payload the server never gets. Now the production call
 * and the golden-sample generator (`AppRequestPayloadGolden`) call this same
 * function.
 *
 * Configuration: the default [GsonBuilder] — no `serializeNulls()`, so a null
 * DTO field is an ABSENT key on the wire (rule 22: the server reads an absent
 * key as absent, and the builder's explicit-state fields — `fieldsNotInFile[]`,
 * `timeSeriesState`, the `*State` strings — are what say WHY).
 */
object AnalysisRequestSerializer {

    /**
     * Also what `FirebaseAnalysisService` uses for the response legs
     * (`AiAnalysisResponse`, `ScanSession`, …): one Gson configuration for the
     * service, not two.
     */
    val gson: Gson = GsonBuilder().create()

    /**
     * Callable Functions accept Map/List/primitive payloads, not arbitrary Kotlin
     * classes, so the fingerprint is flattened through Gson: DTO → JSON string →
     * `Map<String, Any>`. NOTE the second leg: Gson reads every JSON number into
     * a `Double` when the target is `Any` — the Firebase SDK then encodes a whole
     * `Double` as a JSON number without a fraction, so an integral value arrives
     * as `3`, not `3.0`.
     */
    fun fingerprintToMap(fingerprint: BehaviorFingerprint): Map<String, Any> {
        val fingerprintJson = gson.toJson(fingerprint)
        return gson.fromJson(fingerprintJson, object : TypeToken<Map<String, Any>>() {}.type)
    }
}
