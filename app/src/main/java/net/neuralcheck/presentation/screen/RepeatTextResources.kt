package net.neuralcheck.presentation.screen

import androidx.annotation.StringRes
import net.neuralcheck.R
import net.neuralcheck.domain.repeat.RepeatRefusal

/**
 * Where the text of every [RepeatText] lives: ONE `R.string` per text (rule 24). The technician's notice ([RepeatText.NoticeBody], [RepeatText.NotNow]) is in `values/strings.xml` only (Arabic: the
 * technician's interface is Arabic only); the scanned phone's cooling screen, its seven refusals and its failure text are in `values/` and `values-tr/` (the customer sees that phone). No text is written in
 * Kotlin. The `when` has no `else` over the sealed type and over every refusal: a text or a refusal added without its case does not compile.
 *
 * Five refusals have no text of their own - the input errors of a wrong client and `REPEAT_PAIR_NOT_FOUND` (see [RepeatTexts.refusalsWithText]) - and [RepeatTexts.forFailure] never asks for them: asking is an
 * error the caller made, so it throws (rule 22: no default text), and `FormTextsTest` pins it.
 */
@StringRes
internal fun RepeatText.resource(): Int = when (this) {
    RepeatText.NoticeBody -> R.string.rp_notice_body
    RepeatText.NotNow -> R.string.rp_not_now
    RepeatText.WaitTitle -> R.string.rp_wait_title
    RepeatText.Ready -> R.string.rp_ready
    RepeatText.NotCooledWarning -> R.string.rp_not_cooled_warning
    RepeatText.StartAnyway -> R.string.rp_start_anyway
    RepeatText.Wait -> R.string.rp_wait
    RepeatText.TemperatureUnreadable -> R.string.rp_temperature_unreadable
    is RepeatText.StartRefused -> when (refusal) {
        RepeatRefusal.PARENT_JOINER_UNKNOWN -> R.string.rp_refused_parent_joiner_unknown
        RepeatRefusal.ALREADY_STARTED -> R.string.rp_refused_already_started
        RepeatRefusal.NOT_SELECTED -> R.string.rp_refused_not_selected
        RepeatRefusal.SESSION_NOT_COMPLETED -> R.string.rp_refused_session_not_completed
        RepeatRefusal.PARENT_OWNER_UNKNOWN -> R.string.rp_refused_parent_owner_unknown
        RepeatRefusal.RECORD_ABSENT -> R.string.rp_refused_record_absent
        RepeatRefusal.STATE_UNKNOWN -> R.string.rp_refused_state_unknown
        RepeatRefusal.NOT_AUTHENTICATED, RepeatRefusal.SESSION_ID_REQUIRED, RepeatRefusal.SCAN_ID_REQUIRED, RepeatRefusal.STARTED_BEFORE_COOLING_INVALID, RepeatRefusal.PAIR_NOT_FOUND ->
            throw IllegalArgumentException("the refusal ${refusal.wire} has no text of its own: the start's general failure text shows (RepeatTexts.forFailure)")
    }
    RepeatText.StartNotSent -> R.string.rp_start_not_sent
}
