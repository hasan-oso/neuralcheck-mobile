package net.neuralcheck.domain.evaluation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.neuralcheck.data.local.db.entity.SessionEvaluationEntity

/**
 * feat/technician-evaluation — pure state machine over
 * [SessionEvaluationEntity]. Every technician tap is a function
 * `entity → entity` here; the ViewModel only holds the current entity and
 * persists each result on Dispatchers.IO. Extracted (same pattern as
 * BatteryChannelSanitizer / estimateBatterySoh) so the answer-accumulation
 * rules, the sessionId-snapshot rule, and process-death restore are
 * unit-testable without Android.
 *
 * Vocabulary is fixed and closed — the technician never types text.
 */
object EvaluationAnswers {

    /** Verbal 4-point scale — stored + sent verbatim; A·B·C·D mapping is
     *  the server's job (contract feat/session-evaluation-contract). */
    const val GRADE_EXCELLENT = "excellent"
    const val GRADE_GOOD = "good"
    const val GRADE_FAIR = "fair"
    const val GRADE_WEAK = "weak"
    val GRADES = listOf(GRADE_EXCELLENT, GRADE_GOOD, GRADE_FAIR, GRADE_WEAK)

    /** Fifth category option: "أجاب: لم أفحصه" — an explicit attestation,
     *  fundamentally different from not answering (absent key). */
    const val RATING_NOT_TESTED = "not_tested"

    /** Depth categories only — never surface categories (display/camera/
     *  audio): the buyer checks those personally and asking would poison
     *  the training data. */
    const val CAT_BATTERY = "battery"
    const val CAT_CPU_PERFORMANCE = "cpu_performance"
    const val CAT_MEMORY = "memory"
    const val CAT_STORAGE = "storage"
    val CATEGORIES = listOf(CAT_BATTERY, CAT_CPU_PERFORMANCE, CAT_MEMORY, CAT_STORAGE)

    /** Visible-defect chips (multi-select). */
    const val DEFECT_WEAK_BATTERY = "weak_battery"
    const val DEFECT_REPLACED_SCREEN = "replaced_screen"
    const val DEFECT_WATER_DAMAGE = "water_damage"
    const val DEFECT_PRIOR_REPAIR = "prior_repair"
    const val DEFECT_NONE_NOTICED = "none_noticed"
    val DEFECTS = listOf(
        DEFECT_WEAK_BATTERY, DEFECT_REPLACED_SCREEN, DEFECT_WATER_DAMAGE,
        DEFECT_PRIOR_REPAIR, DEFECT_NONE_NOTICED
    )

    private val gson = Gson()

    // Order 2026-10-14 (user decision, device check 03 round 2, the card that never showed on 1.0.8-1.0.13):
    // built with TypeToken.getParameterized — NOT `object : TypeToken<...>() {}`. The anonymous subclasses were
    // built here, in this object's initializer, and R8 (full mode) merged the two into one class and stripped its
    // generic signature; Gson 2.10.1 then throws in `TypeToken()` ("TypeToken must be created with a type
    // argument ... make sure that generic signatures are preserved"), the initializer fails at the first
    // access (`emptyDraft`), and the card never gets its draft. A parameterized type built by call has no
    // subclass and no signature for R8 to strip. Same two types as before (LinkedHashMap<String, String> and
    // ArrayList<String>): EvaluationAnswersTest compares them with the anonymous-subclass form.
    private val mapType = TypeToken.getParameterized(
        LinkedHashMap::class.java, String::class.java, String::class.java
    ).type
    private val listType = TypeToken.getParameterized(
        ArrayList::class.java, String::class.java
    ).type

    fun parseCategoryRatings(json: String): Map<String, String> =
        runCatching { gson.fromJson<LinkedHashMap<String, String>>(json, mapType) }
            .getOrNull() ?: emptyMap()

    fun parseDefects(json: String): List<String> =
        runCatching { gson.fromJson<ArrayList<String>>(json, listType) }
            .getOrNull() ?: emptyList()

    /**
     * Fresh draft created when the questions card first appears.
     * [sessionIdSnapshot] MUST be captured from ScanSessionHolder at this
     * moment — the holder is cleared after the first analyzeDevice
     * (AiScoringEngine) and on sign-out, so it cannot be trusted at
     * submission time. The row only reaches disk with the first answer.
     */
    fun emptyDraft(scanId: String, sessionIdSnapshot: String, shownAtMs: Long) =
        SessionEvaluationEntity(
            scanId = scanId,
            sessionId = sessionIdSnapshot,
            shownAtMs = shownAtMs
        )

    /** Q1 — overall grade. Tapping the already-selected grade deselects
     *  it (back to "did not answer"). */
    fun withGrade(e: SessionEvaluationEntity, grade: String, nowMs: Long): SessionEvaluationEntity {
        require(grade in GRADES) { "unknown grade: $grade" }
        val next = if (e.technicianGradeRaw == grade) null else grade
        return e.copy(technicianGradeRaw = next, lastAnswerAtMs = nowMs)
    }

    /** Q2 — one depth category. Tapping the selected rating again removes
     *  the key entirely (back to "did not answer" — NOT not_tested). */
    fun withCategoryRating(
        e: SessionEvaluationEntity,
        category: String,
        rating: String,
        nowMs: Long
    ): SessionEvaluationEntity {
        require(category in CATEGORIES) { "unknown category: $category" }
        require(rating in GRADES || rating == RATING_NOT_TESTED) { "unknown rating: $rating" }
        val map = LinkedHashMap(parseCategoryRatings(e.categoryRatingsJson))
        if (map[category] == rating) map.remove(category) else map[category] = rating
        return e.copy(categoryRatingsJson = gson.toJson(map), lastAnswerAtMs = nowMs)
    }

    /**
     * Q3 — toggle one defect chip. [DEFECT_NONE_NOTICED] is mutually
     * exclusive with the concrete defects: selecting it clears them, and
     * selecting any concrete defect clears it — "nothing noticed" and
     * "water damage" cannot both be true.
     */
    fun withDefectToggled(
        e: SessionEvaluationEntity,
        defect: String,
        nowMs: Long
    ): SessionEvaluationEntity {
        require(defect in DEFECTS) { "unknown defect: $defect" }
        val current = parseDefects(e.visibleDefectsJson).toMutableList()
        if (defect in current) {
            current.remove(defect)
        } else {
            if (defect == DEFECT_NONE_NOTICED) current.clear()
            else current.remove(DEFECT_NONE_NOTICED)
            current.add(defect)
        }
        return e.copy(visibleDefectsJson = gson.toJson(current), lastAnswerAtMs = nowMs)
    }

    /** True when the draft holds at least one judgement — i.e. worth
     *  persisting/submitting. A judgement-free draft is never written. */
    fun hasAnyJudgement(e: SessionEvaluationEntity): Boolean =
        e.technicianGradeRaw != null ||
            parseCategoryRatings(e.categoryRatingsJson).isNotEmpty() ||
            parseDefects(e.visibleDefectsJson).isNotEmpty()
}
