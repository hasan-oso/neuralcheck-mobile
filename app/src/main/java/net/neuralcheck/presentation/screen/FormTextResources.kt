package net.neuralcheck.presentation.screen

import androidx.annotation.StringRes
import net.neuralcheck.R
import net.neuralcheck.domain.form.BatteryStateKey
import net.neuralcheck.domain.form.DefectRow
import net.neuralcheck.domain.form.FunctionalRow
import net.neuralcheck.domain.form.GradeAnswer
import net.neuralcheck.domain.form.GradeCategory
import net.neuralcheck.domain.form.RowAnswer
import net.neuralcheck.domain.form.SubmitBlock
import net.neuralcheck.domain.form.Symptom

/**
 * Where the text of every [FormText] lives: ONE `R.string` per text (rule 24), in `values/strings.xml` (Arabic - the technician's interface is Arabic only; [FormText.ScanInProgress], the one sentence the scanned
 * phone shows, is also in `values-tr/strings.xml`). No text is written in Kotlin. The `when` has no `else` over the sealed type and over every enum: a text or a value added without its resource does not compile.
 *
 * The one existing text is [FormText.ConfigRetry]: `btn_retry`, the app's «إعادة المحاولة», not a new one. A «لم أفحصها» answer is not a level and has no description (TL:11), so asking for it is an error the
 * caller made, not a text: [FormTextsTest] pins each text of the table and that this throws. `FormTextsTest` also pins every text against the table of `ApprovedTextsTable` (the approval source of each).
 */
@StringRes
internal fun FormText.resource(): Int = when (this) {
    FormText.Title -> R.string.tf_title
    FormText.WhoJudges -> R.string.tf_who_judges
    is FormText.GradeRow -> when (category) {
        GradeCategory.BATTERY -> R.string.tf_grade_battery
        GradeCategory.PERFORMANCE -> R.string.tf_grade_performance
        GradeCategory.THERMAL -> R.string.tf_grade_thermal
        GradeCategory.OVERALL -> R.string.tf_grade_overall
    }
    is FormText.Level -> when (answer) {
        GradeAnswer.EXCELLENT -> R.string.tf_level_excellent
        GradeAnswer.GOOD -> R.string.tf_level_good
        GradeAnswer.FAIR -> R.string.tf_level_fair
        GradeAnswer.WEAK -> R.string.tf_level_weak
        GradeAnswer.NOT_TESTED -> R.string.tf_level_not_tested
    }
    is FormText.LevelDescription -> when (category) {
        GradeCategory.BATTERY -> when (answer) {
            GradeAnswer.EXCELLENT -> R.string.tf_desc_battery_excellent
            GradeAnswer.GOOD -> R.string.tf_desc_battery_good
            GradeAnswer.FAIR -> R.string.tf_desc_battery_fair
            GradeAnswer.WEAK -> R.string.tf_desc_battery_weak
            GradeAnswer.NOT_TESTED -> throw IllegalArgumentException("a not_tested answer is not a level: it has no description (TL:11)")
        }
        GradeCategory.PERFORMANCE -> when (answer) {
            GradeAnswer.EXCELLENT -> R.string.tf_desc_performance_excellent
            GradeAnswer.GOOD -> R.string.tf_desc_performance_good
            GradeAnswer.FAIR -> R.string.tf_desc_performance_fair
            GradeAnswer.WEAK -> R.string.tf_desc_performance_weak
            GradeAnswer.NOT_TESTED -> throw IllegalArgumentException("a not_tested answer is not a level: it has no description (TL:11)")
        }
        GradeCategory.THERMAL -> when (answer) {
            GradeAnswer.EXCELLENT -> R.string.tf_desc_thermal_excellent
            GradeAnswer.GOOD -> R.string.tf_desc_thermal_good
            GradeAnswer.FAIR -> R.string.tf_desc_thermal_fair
            GradeAnswer.WEAK -> R.string.tf_desc_thermal_weak
            GradeAnswer.NOT_TESTED -> throw IllegalArgumentException("a not_tested answer is not a level: it has no description (TL:11)")
        }
        GradeCategory.OVERALL -> when (answer) {
            GradeAnswer.EXCELLENT -> R.string.tf_desc_overall_excellent
            GradeAnswer.GOOD -> R.string.tf_desc_overall_good
            GradeAnswer.FAIR -> R.string.tf_desc_overall_fair
            GradeAnswer.WEAK -> R.string.tf_desc_overall_weak
            GradeAnswer.NOT_TESTED -> throw IllegalArgumentException("a not_tested answer is not a level: it has no description (TL:11)")
        }
    }
    is FormText.Rule -> when (number) {
        1 -> R.string.tf_rule_1
        2 -> R.string.tf_rule_2
        3 -> R.string.tf_rule_3
        else -> throw IllegalArgumentException("the rules above the form are numbered 1 to 3: $number")
    }
    is FormText.FunctionalRowTitle -> when (row) {
        FunctionalRow.SCREEN_TOUCH -> R.string.tf_row_screen_touch
        FunctionalRow.CAMERA -> R.string.tf_row_camera
        FunctionalRow.AUDIO -> R.string.tf_row_audio
        FunctionalRow.BUTTONS -> R.string.tf_row_buttons
        FunctionalRow.CHARGING -> R.string.tf_row_charging
        FunctionalRow.NETWORK -> R.string.tf_row_network
    }
    is FormText.DefectRowTitle -> when (row) {
        DefectRow.REPLACED_SCREEN -> R.string.tf_defect_replaced_screen
        DefectRow.WATER_DAMAGE -> R.string.tf_defect_water_damage
        DefectRow.PRIOR_REPAIR -> R.string.tf_defect_prior_repair
    }
    is FormText.RowAnswerText -> when (answer) {
        RowAnswer.WORKS -> R.string.tf_answer_works
        RowAnswer.FAULTY -> R.string.tf_answer_faulty
        RowAnswer.NOT_TESTED -> R.string.tf_answer_not_tested
    }
    FormText.SymptomsHeader -> R.string.tf_symptoms_header
    is FormText.SymptomText -> when (symptom) {
        Symptom.BATTERY_DIES_BEFORE_DAY_END -> R.string.tf_symptom_battery_dies_before_day_end
        Symptom.BATTERY_JUMPS_OR_SHUTS_DOWN -> R.string.tf_symptom_battery_jumps_or_shuts_down
        Symptom.BATTERY_SWOLLEN -> R.string.tf_symptom_battery_swollen
        Symptom.HOT_WHILE_CHARGING -> R.string.tf_symptom_hot_while_charging
        Symptom.CHARGING_SLOW -> R.string.tf_symptom_charging_slow
        Symptom.SLOW_OR_FREEZES_DAILY -> R.string.tf_symptom_slow_or_freezes_daily
        Symptom.RESTARTS_UNDER_LOAD -> R.string.tf_symptom_restarts_under_load
        Symptom.HEATS_FAST_IN_USE -> R.string.tf_symptom_heats_fast_in_use
        Symptom.SYSTEM_HEAT_WARNING -> R.string.tf_symptom_system_heat_warning
        Symptom.GAME_STUTTER_AFTER_MINUTES -> R.string.tf_symptom_game_stutter_after_minutes
        Symptom.GAME_SCREEN_LINES_OR_FLICKER -> R.string.tf_symptom_game_screen_lines_or_flicker
        Symptom.GAMES_FREEZE_OR_CLOSE -> R.string.tf_symptom_games_freeze_or_close
        Symptom.NONE_NOTICED -> R.string.tf_symptom_none_noticed
    }
    FormText.KnownFault -> R.string.tf_known_fault
    FormText.BatteryHealthTitle -> R.string.tf_battery_health_title
    FormText.BatteryPercent -> R.string.tf_battery_percent
    FormText.BatteryStateTitle -> R.string.tf_battery_state_title
    is FormText.BatteryStateText -> when (key) {
        BatteryStateKey.NORMAL -> R.string.tf_battery_state_normal
        BatteryStateKey.WEAK -> R.string.tf_battery_state_weak
        BatteryStateKey.BAD -> R.string.tf_battery_state_bad
    }
    FormText.BatteryNotShown -> R.string.tf_battery_not_shown
    FormText.GameTried -> R.string.tf_game_tried
    FormText.Yes -> R.string.tf_yes
    FormText.No -> R.string.tf_no
    FormText.NoteLabel -> R.string.tf_note_label
    FormText.PromptBody -> R.string.tf_prompt_body
    FormText.PromptConfirm -> R.string.tf_prompt_confirm
    FormText.PromptRevise -> R.string.tf_prompt_revise
    FormText.Submit -> R.string.tf_submit
    FormText.Sending -> R.string.tf_sending
    FormText.Submitted -> R.string.tf_submitted
    is FormText.Blocked -> when (reason) {
        SubmitBlock.NO_TECHNICIAN -> R.string.tf_blocked_no_technician
        SubmitBlock.NOTE_REQUIRED -> R.string.tf_blocked_note_required
        SubmitBlock.NO_ANSWERS -> R.string.tf_blocked_no_answers
    }
    FormText.FailureSameTechnician -> R.string.tf_failure_same_technician
    FormText.FailureRefused -> R.string.tf_failure_refused
    FormText.FailureNotSent -> R.string.tf_failure_not_sent
    FormText.ConfigLoading -> R.string.tf_config_loading
    FormText.ConfigFailed -> R.string.tf_config_failed
    FormText.ConfigRetry -> R.string.btn_retry
    FormText.NoTechnicians -> R.string.tf_no_technicians
    FormText.SecondEvalNeeded -> R.string.tf_second_eval_needed
    FormText.HandOverDevice -> R.string.tf_hand_over_device
    FormText.ScanInProgress -> R.string.tf_scan_in_progress
}
