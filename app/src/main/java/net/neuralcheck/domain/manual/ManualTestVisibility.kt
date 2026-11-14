package net.neuralcheck.domain.manual

import android.content.Context
import android.util.Log

/**
 * Which manual tests the device MODEL does not have, and therefore are not asked
 * (fieldsVersion 69, contract docs/FV68_CONTRACT.md section 7-h and card T-15 (a),
 * S-15 (c): «ما لا يملكه الطراز لا سجلّ له، وقائمة hiddenByModel في رأس النتائج
 * اليدوية»).
 *
 * The mechanism, all of it:
 *  - [hiddenByModel] is the list the payload carries as
 *    `manualResultsHeader.hiddenByModel` (N-17);
 *  - a hidden test is not shown, and it is left OUT of the ids the screen asks
 *    `ManualTestsViewModel.commitAndContinue` to complete ([idsToAnswer]) —
 *    otherwise that function would fill it with `not_tested`, a record the
 *    decision says must not exist.
 *
 * **[RULES] IS EMPTY, deliberately.** The decision (and the order that carried it)
 * names the list, its place and the exclusion from `allIds`, but NOT which tests
 * are hidden nor on what signal — the model's declared system features
 * (`PackageManager.FEATURE_*`), a model catalog, or something else. Filling the
 * table would be a guess (rule 13), and a wrong row HIDES a real test: a part the
 * technician could have judged is then never recorded. With the table empty the
 * payload says `hiddenByModel = []` — «nothing was hidden», which is TRUE, not a
 * default that fills a gap (rule 22). Rows are added here, one line each, when
 * the user decides the signal per test.
 */
object ManualTestVisibility {

    /**
     * One row: [testId] is hidden when [modelLacks] says the model does not have the
     * component. [reason] is a plain-text reason kept next to the rule so that an
     * added row says why it is safe (a rule without a written reason is removed —
     * rule 22 supplement).
     */
    class HideRule(
        val testId: String,
        val reason: String,
        val modelLacks: (Context) -> Boolean
    )

    /** See the class comment: empty until the user decides which tests and on what signal. */
    val RULES: List<HideRule> = emptyList()

    /**
     * The ids of the tests the model does not have, ascending, without repeats.
     * A rule whose predicate throws does NOT hide its test: a test asked in vain
     * costs the technician one tap, a hidden test costs the record.
     */
    fun hiddenByModel(context: Context, rules: List<HideRule> = RULES): List<String> =
        rules.filter { rule ->
            try {
                rule.modelLacks(context)
            } catch (t: Throwable) {
                Log.w("NeuralCheck", "ManualTestVisibility: rule for ${rule.testId} failed (${t.message}); the test stays visible")
                false
            }
        }.map { it.testId }.distinct().sorted()

    /** [items] without the hidden ones, in their order. */
    fun <T> visible(items: List<T>, hidden: Collection<String>, idOf: (T) -> String): List<T> =
        items.filter { idOf(it) !in hidden }

    /**
     * The ids `commitAndContinue` completes as `not_tested` when unanswered: every
     * test the screen shows, and NOT the hidden ones.
     */
    fun idsToAnswer(allIds: List<String>, hidden: Collection<String>): List<String> =
        allIds.filter { it !in hidden }
}
