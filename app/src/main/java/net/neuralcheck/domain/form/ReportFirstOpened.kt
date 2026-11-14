package net.neuralcheck.domain.form

import com.google.gson.JsonElement

/**
 * `recordReportFirstOpened` - row A-P6-08, the form's blindness. The server decides whether a technician's form was sent BEFORE the report was first shown, by comparing the form's send time with the
 * moment the report was first opened on EITHER device (PLAN-06:1321, «أوّل مرّة يُعرض فيها التقرير على أيّ من الجهازين»); when no first-open was recorded the comparison falls back to the report's
 * creation time (`created_at_fallback`) - a weaker basis. This is the call that records it.
 *
 * The wire, from the server's handler [SRV technicianForm.js:347-378 `recordReportFirstOpenedHandler`, as copied in
 * `evidence/run-2026-10-18/technician-form-server-wire-neuralcheck-backend-05275ba.md` from neuralcheck-backend@05275ba - rule 29: a copy, it drifts]:
 *  - request `{sessionId}` [SRV technicianForm.js:348 via `readCallerAndSession`, :150-157];
 *  - who may call: the session's owner OR its joined device [SRV :25 and :43, «المالك أو المنضمّ»]; the session must be `completed` with a report that exists, else a refusal
 *    (`SESSION_REPORT_NOT_AVAILABLE`) [SRV :362-364];
 *  - answer `{sessionId, recorded: boolean}`: `true` the first time, `false` for every later call [SRV :377; :43-44, the first open wins].
 *
 * Nothing is sent while `TECH_FORM_TEXTS_APPROVED` is false: the callable is behind it at the point the request leaves the phone (`FirebaseAnalysisService`,
 * [net.neuralcheck.data.remote.FormCallsNotApprovedException]). It is true since 2026-10-23; the function is not deployed yet, so the call fails and is only logged (`ReportFirstOpenedRecorder`) - never shown.
 */
object ReportFirstOpenedWire {
    const val CALLABLE = "recordReportFirstOpened" // [SRV technicianForm.js:345]
    const val KEY_SESSION_ID = TechnicianFormWire.KEY_SESSION_ID // [SRV :348]
    const val KEY_RECORDED = "recorded" // [SRV :377]
}

/** How the answer of `recordReportFirstOpened` reads - the same discipline as the form's configuration: a missing or other-shaped `recorded` is named, never read as `true` or `false` (rules 22, 25). */
sealed interface ReportFirstOpenedRead {
    /** `recorded: true` - this was the first time, the server stamped it. */
    data object Recorded : ReportFirstOpenedRead

    /** `recorded: false` - an earlier call already recorded it; nothing was written now. */
    data object AlreadyRecorded : ReportFirstOpenedRead

    /** The answer cannot be read as one of the two; [why] is a fixed token (`absent`, `not_an_object_<kind>`, `recorded_absent`, `recorded_not_a_boolean_<kind>`). */
    data class Unreadable(val why: String) : ReportFirstOpenedRead

    companion object {
        fun of(element: JsonElement?): ReportFirstOpenedRead {
            if (element == null || element.isJsonNull) return Unreadable("absent")
            if (!element.isJsonObject) return Unreadable("not_an_object_${kindOf(element)}")
            val recorded = element.asJsonObject.get(ReportFirstOpenedWire.KEY_RECORDED)
            if (recorded == null || recorded.isJsonNull) return Unreadable("recorded_absent")
            if (!recorded.isJsonPrimitive || !recorded.asJsonPrimitive.isBoolean) return Unreadable("recorded_not_a_boolean_${kindOf(recorded)}")
            return if (recorded.asBoolean) Recorded else AlreadyRecorded
        }

        private fun kindOf(element: JsonElement): String = when {
            element.isJsonNull -> "null"
            element.isJsonObject -> "object"
            element.isJsonArray -> "array"
            element.asJsonPrimitive.isBoolean -> "boolean"
            element.asJsonPrimitive.isNumber -> "number"
            else -> "string"
        }
    }
}
