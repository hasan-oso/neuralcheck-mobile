package net.neuralcheck.domain.scoring

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken

/**
 * Reads and parses scoring_config.json from assets.
 * Provides access to thresholds, weights, axis definitions, and grade boundaries.
 */
class ScoringConfigReader(private val context: Context) {

    private val config: JsonObject by lazy { parseConfig() }
    private val gson = Gson()

    private fun parseConfig(): JsonObject {
        val json = context.assets.open("scoring_config.json").bufferedReader().readText()
        return gson.fromJson(json, JsonObject::class.java)
    }

    // --- Threshold Points ---

    fun getThresholdConfig(testId: String): JsonObject? {
        return config.getAsJsonObject("check_thresholds")?.getAsJsonObject(testId)
    }

    fun getInterpolatedPoints(testId: String): List<Pair<Float, Float>>? {
        val threshold = getThresholdConfig(testId) ?: return null
        if (!threshold.has("points")) return null
        val points = threshold.getAsJsonArray("points")
        return points.map { arr ->
            val a = arr.asJsonArray
            a[0].asFloat to a[1].asFloat
        }
    }

    fun getDirection(testId: String): String {
        val threshold = getThresholdConfig(testId) ?: return "lower_better"
        return threshold.get("direction")?.asString ?: "lower_better"
    }

    fun getThresholdType(testId: String): String {
        val threshold = getThresholdConfig(testId) ?: return "info"
        return threshold.get("type")?.asString ?: "info"
    }

    // --- Category Weights ---

    fun getCategoryWeights(category: String): Map<String, Int> {
        val weights = config.getAsJsonObject("category_weights")?.getAsJsonObject(category) ?: return emptyMap()
        val type = object : TypeToken<Map<String, Int>>() {}.type
        return gson.fromJson(weights, type)
    }

    fun getAllCategories(): Set<String> {
        return config.getAsJsonObject("category_weights")?.keySet() ?: emptySet()
    }

    // --- Axis Definitions ---

    fun getAxisCategories(axis: String): Map<String, Int> {
        val axisObj = config.getAsJsonObject("axis_definitions")?.getAsJsonObject(axis) ?: return emptyMap()
        val cats = axisObj.getAsJsonObject("categories") ?: return emptyMap()
        val type = object : TypeToken<Map<String, Int>>() {}.type
        return gson.fromJson(cats, type)
    }

    fun getAxisNameAr(axis: String): String {
        return config.getAsJsonObject("axis_definitions")
            ?.getAsJsonObject(axis)
            ?.get("name_ar")?.asString ?: axis
    }

    fun getAxisNameTr(axis: String): String {
        return config.getAsJsonObject("axis_definitions")
            ?.getAsJsonObject(axis)
            ?.get("name_tr")?.asString ?: axis
    }

    fun getAllAxes(): Set<String> {
        return config.getAsJsonObject("axis_definitions")?.keySet() ?: emptySet()
    }

    // --- Overall Weights ---

    fun getOverallWeights(): Map<String, Int> {
        val weights = config.getAsJsonObject("overall_weights") ?: return emptyMap()
        val type = object : TypeToken<Map<String, Int>>() {}.type
        return gson.fromJson(weights, type)
    }

    // --- Critical Checks ---

    val criticalCheckIds: List<String>
        get() = getCriticalChecks().map { it.id }

    fun getCriticalChecks(): List<CriticalCheck> {
        val checks = config.getAsJsonObject("critical_checks")?.getAsJsonArray("checks") ?: return emptyList()
        return checks.map {
            val obj = it.asJsonObject
            CriticalCheck(
                id = obj.get("id").asString,
                condition = obj.get("condition")?.asString ?: "score == 0",
                description = obj.get("description")?.asString ?: ""
            )
        }
    }

    val criticalPenaltyPerFailure: Int
        get() = config.getAsJsonObject("critical_checks")?.get("penalty_per_failure")?.asInt ?: 10

    val maxCriticalPenalty: Int
        get() = config.getAsJsonObject("critical_checks")?.get("max_total_penalty")?.asInt ?: 40

    val criticalGradeCap: Float
        get() = config.getAsJsonObject("critical_checks")?.get("grade_cap_with_any_failure")?.asFloat ?: 65f

    // --- Incomplete Scan ---

    val incompleteScanCap: Float
        get() = config.getAsJsonObject("incomplete_scan")?.get("grade_cap")?.asFloat ?: 55f

    // --- Anti-Tamper ---

    val veryColdThreshold: Float
        get() = config.getAsJsonObject("anti_tamper")?.get("very_cold_threshold_c")?.asFloat ?: 10f

    val veryColdPenalty: Float
        get() = config.getAsJsonObject("anti_tamper")?.get("very_cold_penalty_points")?.asFloat ?: 5f

    // --- Grades ---

    fun getGradeForScore(score: Float): GradeInfo {
        val grades = config.getAsJsonObject("grades") ?: return GradeInfo("D", "#D32F2F", "ضعيف", "Zayıf")
        for (letter in listOf("A", "B", "C", "D")) {
            val g = grades.getAsJsonObject(letter) ?: continue
            val min = g.get("min").asFloat
            val max = g.get("max").asFloat
            if (score >= min && score <= max) {
                return GradeInfo(
                    letter = letter,
                    colorHex = g.get("color_hex")?.asString ?: "#000000",
                    descriptionAr = g.get("description_ar")?.asString ?: "",
                    descriptionTr = g.get("description_tr")?.asString ?: ""
                )
            }
        }
        return GradeInfo("D", "#B71C1C", "ضعيف — مشاكل جوهرية تؤثر على الاستخدام", "Zayıf — Kullanımı etkileyen önemli sorunlar")
    }
}

data class GradeInfo(
    val letter: String,
    val colorHex: String,
    val descriptionAr: String,
    val descriptionTr: String = ""
)

data class CriticalCheck(
    val id: String,
    val condition: String,
    val description: String
)
