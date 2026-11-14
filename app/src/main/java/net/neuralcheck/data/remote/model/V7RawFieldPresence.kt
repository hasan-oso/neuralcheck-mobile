package net.neuralcheck.data.remote.model

import com.google.gson.JsonParser

/**
 * Independent raw-JSON presence check for [AiCategoryV7.letterCeilingReason]
 * — see that field's doc, and [AiAnalysisResponse.categoriesMissingLetterCeilingReason]'s,
 * for the full "why": Gson's reflective per-field decoding of [AiCategoryV7]
 * cannot distinguish "JSON key absent" from "JSON key present, value
 * `null`" — both land as Kotlin `null` on the same field — so this function
 * re-walks the SAME raw JSON text with Gson's tree model
 * ([com.google.gson.JsonParser], not the typed [AiAnalysisResponse]
 * deserializer) purely to answer the one question the typed read layer
 * cannot: which category objects never carried the key at all.
 *
 * Deliberately NOT a Gson `TypeAdapter`/`TypeAdapterFactory` registered on
 * [AiCategoryV7] itself — that would need explicit registration on every
 * `Gson` instance that touches this DTO (production's
 * [net.neuralcheck.data.remote.FirebaseAnalysisService] plus every test file
 * that builds its own plain `Gson()`), each one a silent place to drift out
 * of sync. A plain function taking the same JSON string already in hand at
 * both of [net.neuralcheck.data.remote.FirebaseAnalysisService]'s two parse
 * call sites has no such registration surface.
 */
fun categoriesMissingLetterCeilingReason(json: String): Set<String> {
    val root = runCatching { JsonParser.parseString(json) }.getOrNull() ?: return emptySet()
    if (!root.isJsonObject) return emptySet()
    val categoriesElement = root.asJsonObject.get("categories") ?: return emptySet()
    if (!categoriesElement.isJsonObject) return emptySet()

    val missing = mutableSetOf<String>()
    for ((key, value) in categoriesElement.asJsonObject.entrySet()) {
        // A category value that isn't even a JSON object is a different,
        // more fundamental shape problem than this one field — but
        // letterCeilingReason certainly isn't declared on it either way,
        // so it counts as missing here too rather than being silently
        // skipped.
        val declared = value.isJsonObject && value.asJsonObject.has("letterCeilingReason")
        if (!declared) missing.add(key)
    }
    return missing
}
