package net.neuralcheck.presentation.screen

/**
 * The approved wording of the technician's form, the repeat scan and the scan-start battery window - the decisions' own text, or its reviewed rewording where [Entry.source] says so - the one table the tests pin
 * the resources against (rule 29: a copy carries its source). Each [Entry] says which text (the resource), which row (the sheet id, or `approved-before`), where it came from, and the Arabic and (for the scanned phone only) Turkish wording, character for character.
 *
 * Sources (read by tool when this table was written, 2026-10-23; sha256 of each file as read):
 *  - `neuralcheck-workspace/evidence/run-2026-10-18/DECISIONS-FOR-HASAN-2026-10-21.md` (65a6d3da6a13): rows A2 to A6 and A5h-2; Hasan delegated the pending texts on 2026-10-22 and they were reviewed on 2026-10-23
 *    (`texts-review.md`); a row reworded in the review says so in [Entry.source] with the reason.
 *  - `neuralcheck-workspace/plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md` (337eaa83dfc5): the five level words, the sixteen descriptions and the three rules - approved by the user, final, 2026-10-10.
 *  - `neuralcheck-backend docs/PROTOCOL_CONTRACT.md` between `SYMPTOM-TEXT:BEGIN` and `SYMPTOM-TEXT:END` at 0dfe892 (884856107120): the twelve symptoms and `none_noticed`, Arabic approved (PLAN-06:1262-1278);
 *    and «جُرّبت لعبة» (TL:31, the user's wording).
 * Hand-edit nothing here: a change of wording is a change of the decision, made in the sheet first (`scratchpad/app-delegated/spec.py` generated this file).
 */
internal object ApprovedTextsTable {
    /** The sha256 of the three sources as read when this table was written (the first is `DECISIONS-FOR-HASAN-2026-10-21.md`, the second the levels file, the third the backend's contract copy at 0dfe892). */
    const val SHEET_SHA256 = "65a6d3da6a1362adddf3bf33d1bf02482bda62dbd96e34849487a7b87d00398d"
    const val LEVELS_SHA256 = "337eaa83dfc59a2e7957f7046d4efda3e04442e1cb1c63c463ba53b40cee68da"
    const val CONTRACT_COPY_SHA256 = "884856107120a9ba09fe335b7928944e1ae709d79bf7e63841f48ea998da51b7"

    /** [slot] is the text's own `toString()` (`Title`, `GradeRow(category=BATTERY)`, ...): the tests find a row by it, so the map from a text to its resource is checked against the table and not against itself. */
    class Entry(val resource: String, val id: String, val source: String, val ar: String, val tr: String?, val slot: String) {
        /** The scanned phone's texts have a Turkish wording; the technician's have none (Arabic only). */
        val scannedPhone: Boolean get() = tr != null
    }

    /** The technician's form: the 86 texts that have their own resource (the 87th, [FormText.ConfigRetry], is the existing `btn_retry`). */
    val form: List<Entry> = listOf(
        Entry("tf_title", "A2-1", "DECISIONS-FOR-HASAN-2026-10-21.md A2-1", "تقييم الفنّي — قبل ظهور النتيجة", null, "Title"),
        Entry("tf_who_judges", "A2-2", "DECISIONS-FOR-HASAN-2026-10-21.md A2-2", "من يقيّم؟", null, "WhoJudges"),
        Entry("tf_grade_battery", "A2-3", "DECISIONS-FOR-HASAN-2026-10-21.md A2-3", "البطارية", null, "GradeRow(category=BATTERY)"),
        Entry("tf_grade_performance", "A2-4", "DECISIONS-FOR-HASAN-2026-10-21.md A2-4", "الأداء (ومعه الألعاب)", null, "GradeRow(category=PERFORMANCE)"),
        Entry("tf_grade_thermal", "A2-5", "DECISIONS-FOR-HASAN-2026-10-21.md A2-5", "الحرارة", null, "GradeRow(category=THERMAL)"),
        Entry("tf_grade_overall", "A2-6", "DECISIONS-FOR-HASAN-2026-10-21.md A2-6", "العامّ", null, "GradeRow(category=OVERALL)"),
        Entry("tf_row_screen_touch", "A2-7", "DECISIONS-FOR-HASAN-2026-10-21.md A2-7", "الشاشة واللمس", null, "FunctionalRowTitle(row=SCREEN_TOUCH)"),
        Entry("tf_row_camera", "A2-8", "DECISIONS-FOR-HASAN-2026-10-21.md A2-8", "الكاميرا", null, "FunctionalRowTitle(row=CAMERA)"),
        Entry("tf_row_audio", "A2-9", "DECISIONS-FOR-HASAN-2026-10-21.md A2-9", "الصوت", null, "FunctionalRowTitle(row=AUDIO)"),
        Entry("tf_row_buttons", "A2-10", "DECISIONS-FOR-HASAN-2026-10-21.md A2-10", "الأزرار", null, "FunctionalRowTitle(row=BUTTONS)"),
        Entry("tf_row_charging", "A2-11", "DECISIONS-FOR-HASAN-2026-10-21.md A2-11", "الشحن", null, "FunctionalRowTitle(row=CHARGING)"),
        Entry("tf_row_network", "A2-12", "DECISIONS-FOR-HASAN-2026-10-21.md A2-12", "الشبكة", null, "FunctionalRowTitle(row=NETWORK)"),
        Entry("tf_answer_works", "A2-13", "DECISIONS-FOR-HASAN-2026-10-21.md A2-13", "يعمل", null, "RowAnswerText(answer=WORKS)"),
        Entry("tf_answer_faulty", "A2-14", "DECISIONS-FOR-HASAN-2026-10-21.md A2-14", "معطوب", null, "RowAnswerText(answer=FAULTY)"),
        Entry("tf_answer_not_tested", "A2-15", "DECISIONS-FOR-HASAN-2026-10-21.md A2-15", "لم أختبر", null, "RowAnswerText(answer=NOT_TESTED)"),
        Entry("tf_defect_replaced_screen", "A2-16", "DECISIONS-FOR-HASAN-2026-10-21.md A2-16", "شاشة مستبدلة", null, "DefectRowTitle(row=REPLACED_SCREEN)"),
        Entry("tf_defect_water_damage", "A2-17", "DECISIONS-FOR-HASAN-2026-10-21.md A2-17", "أثر ماء", null, "DefectRowTitle(row=WATER_DAMAGE)"),
        Entry("tf_defect_prior_repair", "A2-18", "DECISIONS-FOR-HASAN-2026-10-21.md A2-18", "إصلاح سابق", null, "DefectRowTitle(row=PRIOR_REPAIR)"),
        Entry("tf_symptoms_header", "A2-19", "DECISIONS-FOR-HASAN-2026-10-21.md A2-19", "ما لاحظته (اختر ما ينطبق)", null, "SymptomsHeader"),
        Entry("tf_known_fault", "A2-20", "DECISIONS-FOR-HASAN-2026-10-21.md A2-20", "عطب معروف قبل الفحص", null, "KnownFault"),
        Entry("tf_battery_health_title", "A2-21", "DECISIONS-FOR-HASAN-2026-10-21.md A2-21", "صحّة البطارية (إن فحصتَها)", null, "BatteryHealthTitle"),
        Entry("tf_battery_percent", "A2-22", "DECISIONS-FOR-HASAN-2026-10-21.md A2-22", "نسبة من الإعدادات", null, "BatteryPercent"),
        Entry("tf_battery_state_title", "A2-23", "DECISIONS-FOR-HASAN-2026-10-21.md A2-23", "حالة", null, "BatteryStateTitle"),
        Entry("tf_battery_state_normal", "A2-24", "DECISIONS-FOR-HASAN-2026-10-21.md A2-24", "طبيعي", null, "BatteryStateText(key=NORMAL)"),
        Entry("tf_battery_state_weak", "A2-25", "DECISIONS-FOR-HASAN-2026-10-21.md A2-25", "ضعيف", null, "BatteryStateText(key=WEAK)"),
        Entry("tf_battery_state_bad", "A2-26", "DECISIONS-FOR-HASAN-2026-10-21.md A2-26", "سيّئ", null, "BatteryStateText(key=BAD)"),
        Entry("tf_battery_not_shown", "A2-27", "DECISIONS-FOR-HASAN-2026-10-21.md A2-27", "لا تظهر", null, "BatteryNotShown"),
        Entry("tf_note_label", "A2-28", "DECISIONS-FOR-HASAN-2026-10-21.md A2-28", "ما الذي لاحظته؟", null, "NoteLabel"),
        Entry("tf_prompt_body", "A2-29", "DECISIONS-FOR-HASAN-2026-10-21.md A2-29 (reworded: حُذف «: %1\$s» لأنّ الكود لا يمرّر له قيمة (كان سيظهر «%1\$s» حرفياً على الشاشة))", "إجاباتك تبدو متعارضة. هل هي صحيحة؟", null, "PromptBody"),
        Entry("tf_prompt_confirm", "A2-30", "DECISIONS-FOR-HASAN-2026-10-21.md A2-30", "نعم، أرسل", null, "PromptConfirm"),
        Entry("tf_prompt_revise", "A2-31", "DECISIONS-FOR-HASAN-2026-10-21.md A2-31", "سأراجع", null, "PromptRevise"),
        Entry("tf_submit", "A2-32", "DECISIONS-FOR-HASAN-2026-10-21.md A2-32", "أرسل التقييم", null, "Submit"),
        Entry("tf_submitted", "A2-33", "DECISIONS-FOR-HASAN-2026-10-21.md A2-33", "أُرسل التقييم، ولا يُعدَّل", null, "Submitted"),
        Entry("tf_yes", "A3-1", "DECISIONS-FOR-HASAN-2026-10-21.md A3-1", "نعم", null, "Yes"),
        Entry("tf_no", "A3-2", "DECISIONS-FOR-HASAN-2026-10-21.md A3-2", "لا", null, "No"),
        Entry("tf_sending", "A3-3", "DECISIONS-FOR-HASAN-2026-10-21.md A3-3", "جارٍ الإرسال…", null, "Sending"),
        Entry("tf_blocked_no_technician", "A3-4", "DECISIONS-FOR-HASAN-2026-10-21.md A3-4", "اختر من يقيّم أولاً", null, "Blocked(reason=NO_TECHNICIAN)"),
        Entry("tf_blocked_note_required", "A3-5", "DECISIONS-FOR-HASAN-2026-10-21.md A3-5", "اكتب ما لاحظته (مطلوب مع أيّ درجة دون «جيد»)", null, "Blocked(reason=NOTE_REQUIRED)"),
        Entry("tf_blocked_no_answers", "A3-6", "DECISIONS-FOR-HASAN-2026-10-21.md A3-6", "لم تُجب عن شيء بعد", null, "Blocked(reason=NO_ANSWERS)"),
        Entry("tf_failure_same_technician", "A3-7", "DECISIONS-FOR-HASAN-2026-10-21.md A3-7", "التقييم الثاني يجب أن يكون من فنّي آخر", null, "FailureSameTechnician"),
        Entry("tf_failure_refused", "A3-8", "DECISIONS-FOR-HASAN-2026-10-21.md A3-8", "رفض الخادم الإرسال", null, "FailureRefused"),
        Entry("tf_failure_not_sent", "A3-9", "DECISIONS-FOR-HASAN-2026-10-21.md A3-9", "لم يُرسل التقييم، تحقّق من الاتصال وأعد المحاولة", null, "FailureNotSent"),
        Entry("tf_config_loading", "A3-10", "DECISIONS-FOR-HASAN-2026-10-21.md A3-10", "جارٍ تحميل الاستمارة…", null, "ConfigLoading"),
        Entry("tf_config_failed", "A3-11", "DECISIONS-FOR-HASAN-2026-10-21.md A3-11", "تعذّر تحميل الاستمارة", null, "ConfigFailed"),
        Entry("tf_no_technicians", "A3-12", "DECISIONS-FOR-HASAN-2026-10-21.md A3-12", "لا فنّيين مسجَّلين لهذا المحلّ — يضيفهم المشرف", null, "NoTechnicians"),
        Entry("tf_scan_in_progress", "A4-1", "DECISIONS-FOR-HASAN-2026-10-21.md A4-1", "جارٍ الفحص. اترك الهاتف ولا تلمسه.", "Test sürüyor. Telefonu bırakın ve dokunmayın.", "ScanInProgress"),
        Entry("tf_second_eval_needed", "A5g-1", "DECISIONS-FOR-HASAN-2026-10-21.md A5g-1", "هذا الهاتف يحتاج تقييماً ثانياً من فنّي آخر", null, "SecondEvalNeeded"),
        Entry("tf_hand_over_device", "A5g-3", "DECISIONS-FOR-HASAN-2026-10-21.md A5g-3", "سلّم الجهاز لفنّي آخر", null, "HandOverDevice"),
        Entry("tf_level_excellent", "approved-before", "TL:11 «ممتاز / جيد / متوسط / ضعيف» و«لم أفحصها» خيار خامس لا مستوى", "ممتاز", null, "Level(answer=EXCELLENT)"),
        Entry("tf_level_good", "approved-before", "TL:11 «ممتاز / جيد / متوسط / ضعيف» و«لم أفحصها» خيار خامس لا مستوى", "جيد", null, "Level(answer=GOOD)"),
        Entry("tf_level_fair", "approved-before", "TL:11 «ممتاز / جيد / متوسط / ضعيف» و«لم أفحصها» خيار خامس لا مستوى", "متوسط", null, "Level(answer=FAIR)"),
        Entry("tf_level_weak", "approved-before", "TL:11 «ممتاز / جيد / متوسط / ضعيف» و«لم أفحصها» خيار خامس لا مستوى", "ضعيف", null, "Level(answer=WEAK)"),
        Entry("tf_level_not_tested", "approved-before", "TL:11 «ممتاز / جيد / متوسط / ضعيف» و«لم أفحصها» خيار خامس لا مستوى", "لم أفحصها", null, "Level(answer=NOT_TESTED)"),
        Entry("tf_desc_battery_excellent", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "لا انتفاخ، والنسبة تنزل ببطء، ولا يسخن وهو يُشحن. مثال: الغطاء الخلفي مستوٍ، وبعد الفحص كاملاً نزلت النسبة قليلاً فقط.", null, "LevelDescription(category=BATTERY, answer=EXCELLENT)"),
        Entry("tf_desc_battery_good", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "لا انتفاخ ولا قفزات، لكن البطارية ليست في أحسن حالها. مثال: لا علامة ظاهرة، لكن الهاتف يُظهر صحّة 80–89%، أو النسبة نزلت أثناء الفحص أكثر ممّا يتوقّعه الفنّي من خبرته.", null, "LevelDescription(category=BATTERY, answer=GOOD)"),
        Entry("tf_desc_battery_fair", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "النسبة تقفز فجأة، أو تنزل بسرعة واضحة، أو يسخن الهاتف وهو يُشحن. مثال: النسبة نزلت من 40% إلى 25% دفعة واحدة.", null, "LevelDescription(category=BATTERY, answer=FAIR)"),
        Entry("tf_desc_battery_weak", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "انتفاخ، أو ينطفئ قبل أن يصل إلى الصفر، أو لا يشحن كما يجب. تحتاج تغييراً قبل البيع. مثال: الغطاء الخلفي مرتفع من جهة، أو انطفأ والنسبة 15%.", null, "LevelDescription(category=BATTERY, answer=WEAK)"),
        Entry("tf_desc_performance_excellent", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "التطبيقات تفتح فوراً، والتمرير سلس، والكاميرا تفتح بلا انتظار. مثال: التنقّل بين التطبيقات فوري، وإن جرّبت لعبة فهي بلا تقطيع.", null, "LevelDescription(category=PERFORMANCE, answer=EXCELLENT)"),
        Entry("tf_desc_performance_good", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "انتظار قصير أحياناً عند فتح تطبيق أو الكاميرا، والتمرير سلس غالباً. مثال: المعرض يتأخّر ثانية عند الفتح الأوّل، ثم يعمل عادياً.", null, "LevelDescription(category=PERFORMANCE, answer=GOOD)"),
        Entry("tf_desc_performance_fair", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "بطء واضح في فتح التطبيقات أو التمرير، أو الكاميرا تتأخّر بوضوح. مثال: التمرير في قائمة طويلة يتقطّع، أو إن جرّبت لعبة صارت تتقطّع بعد قليل.", null, "LevelDescription(category=PERFORMANCE, answer=FAIR)"),
        Entry("tf_desc_performance_weak", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "تعليق، أو يعيد التشغيل وحده، أو تطبيق يُغلق وحده. مثال: الشاشة تجمّدت أثناء الفحص واحتاج الهاتف إعادة تشغيل، أو إن جرّبت لعبة توقّفت أو أُغلقت.", null, "LevelDescription(category=PERFORMANCE, answer=WEAK)"),
        Entry("tf_desc_thermal_excellent", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "حرارة طبيعية لهاتف سليم بعد إجهاد. مثال: دافئ بوضوح، لكنه مريح في اليد.", null, "LevelDescription(category=THERMAL, answer=EXCELLENT)"),
        Entry("tf_desc_thermal_good", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "أسخن قليلاً ممّا تعرفه بعد إجهاد مثله، لكنه يُمسَك ويبرد خلال دقائق. مثال: تشعر بالحرارة بوضوح، لكنك لا تحتاج أن تضعه.", null, "LevelDescription(category=THERMAL, answer=GOOD)"),
        Entry("tf_desc_thermal_fair", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "ساخن لدرجة تزعج اليد، أو يسخن بسرعة وهو يُشحن. مثال: تبدّله من يد إلى يد بعد قليل.", null, "LevelDescription(category=THERMAL, answer=FAIR)"),
        Entry("tf_desc_thermal_weak", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "ظهر تحذير حرارة من النظام، أو لا يُمسَك من الحرارة، أو أبطأ أو انطفأ بسببها. مثال: رسالة «الهاتف ساخن» على الشاشة.", null, "LevelDescription(category=THERMAL, answer=WEAK)"),
        Entry("tf_desc_overall_excellent", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "يعمل من الداخل كما ينبغي، بلا أيّ ملاحظة. مثال: البطارية والأداء والحرارة والوظائف كلّها بلا ملاحظة.", null, "LevelDescription(category=OVERALL, answer=EXCELLENT)"),
        Entry("tf_desc_overall_good", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "يعمل جيداً، مع ملاحظة صغيرة لا تمسّ الاستعمال اليومي. مثال: البطارية ليست جديدة لكنها سليمة.", null, "LevelDescription(category=OVERALL, answer=GOOD)"),
        Entry("tf_desc_overall_fair", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "يعمل، لكن فيه علّة تمسّ الاستعمال وتُصلَح. مثال: بطارية تحتاج تغييراً، أو سخونة تزعج في الاستعمال الطويل.", null, "LevelDescription(category=OVERALL, answer=FAIR)"),
        Entry("tf_desc_overall_weak", "approved-before", "plan/TECH-LEVELS-WORDING-FINAL-2026-10-10.md (معتمد اعتماداً أخيراً 2026-10-10)", "علّة كبيرة تمنع الاستعمال العادي. مثال: تعليق متكرّر، أو انطفاء، أو وظيفة أساسية معطّلة مثل الشحن أو الشبكة.", null, "LevelDescription(category=OVERALL, answer=WEAK)"),
        Entry("tf_rule_1", "approved-before", "TL:14 قواعد الفنّي الثلاث (معتمدة)", "احكم بما رأيته بنفسك على الهاتف، قبل أن ترى تقرير التطبيق.", null, "Rule(number=1)"),
        Entry("tf_rule_2", "approved-before", "TL:15 قواعد الفنّي الثلاث (معتمدة)", "إن وقع الهاتف بين مستويين، فاختر الأدنى.", null, "Rule(number=2)"),
        Entry("tf_rule_3", "approved-before", "TL:16 قواعد الفنّي الثلاث (معتمدة)", "إن لم تفحص الفئة، فاختر «لم أفحصها». لا تخمّن.", null, "Rule(number=3)"),
        Entry("tf_symptom_battery_dies_before_day_end", "approved-before", "neuralcheck-backend docs/PROTOCOL_CONTRACT.md §٥-ج (SYMPTOM-TEXT) = PLAN-06:1266-1280 (العربية معتمدة)", "تنفد قبل نهاية اليوم بالاستعمال العادي", null, "SymptomText(symptom=BATTERY_DIES_BEFORE_DAY_END)"),
        Entry("tf_symptom_battery_jumps_or_shuts_down", "approved-before", "neuralcheck-backend docs/PROTOCOL_CONTRACT.md §٥-ج (SYMPTOM-TEXT) = PLAN-06:1266-1280 (العربية معتمدة)", "تنقص النسبة بقفزات أو ينطفئ قبل الصفر", null, "SymptomText(symptom=BATTERY_JUMPS_OR_SHUTS_DOWN)"),
        Entry("tf_symptom_battery_swollen", "approved-before", "neuralcheck-backend docs/PROTOCOL_CONTRACT.md §٥-ج (SYMPTOM-TEXT) = PLAN-06:1266-1280 (العربية معتمدة)", "منتفخة أو الغطاء مرتفع", null, "SymptomText(symptom=BATTERY_SWOLLEN)"),
        Entry("tf_symptom_hot_while_charging", "approved-before", "neuralcheck-backend docs/PROTOCOL_CONTRACT.md §٥-ج (SYMPTOM-TEXT) = PLAN-06:1266-1280 (العربية معتمدة)", "يسخن أثناء الشحن", null, "SymptomText(symptom=HOT_WHILE_CHARGING)"),
        Entry("tf_symptom_charging_slow", "approved-before", "neuralcheck-backend docs/PROTOCOL_CONTRACT.md §٥-ج (SYMPTOM-TEXT) = PLAN-06:1266-1280 (العربية معتمدة)", "الشحن بطيء", null, "SymptomText(symptom=CHARGING_SLOW)"),
        Entry("tf_symptom_slow_or_freezes_daily", "approved-before", "neuralcheck-backend docs/PROTOCOL_CONTRACT.md §٥-ج (SYMPTOM-TEXT) = PLAN-06:1266-1280 (العربية معتمدة)", "بطء أو تعليق في الاستعمال اليومي", null, "SymptomText(symptom=SLOW_OR_FREEZES_DAILY)"),
        Entry("tf_symptom_restarts_under_load", "approved-before", "neuralcheck-backend docs/PROTOCOL_CONTRACT.md §٥-ج (SYMPTOM-TEXT) = PLAN-06:1266-1280 (العربية معتمدة)", "إعادة تشغيل مفاجئة تحت الحمل", null, "SymptomText(symptom=RESTARTS_UNDER_LOAD)"),
        Entry("tf_symptom_heats_fast_in_use", "approved-before", "neuralcheck-backend docs/PROTOCOL_CONTRACT.md §٥-ج (SYMPTOM-TEXT) = PLAN-06:1266-1280 (العربية معتمدة)", "يسخن سريعاً تحت الاستعمال", null, "SymptomText(symptom=HEATS_FAST_IN_USE)"),
        Entry("tf_symptom_system_heat_warning", "approved-before", "neuralcheck-backend docs/PROTOCOL_CONTRACT.md §٥-ج (SYMPTOM-TEXT) = PLAN-06:1266-1280 (العربية معتمدة)", "يظهر تحذير حرارة من النظام", null, "SymptomText(symptom=SYSTEM_HEAT_WARNING)"),
        Entry("tf_symptom_game_stutter_after_minutes", "approved-before", "neuralcheck-backend docs/PROTOCOL_CONTRACT.md §٥-ج (SYMPTOM-TEXT) = PLAN-06:1266-1280 (العربية معتمدة)", "تقطيع في الألعاب بعد دقائق", null, "SymptomText(symptom=GAME_STUTTER_AFTER_MINUTES)"),
        Entry("tf_symptom_game_screen_lines_or_flicker", "approved-before", "neuralcheck-backend docs/PROTOCOL_CONTRACT.md §٥-ج (SYMPTOM-TEXT) = PLAN-06:1266-1280 (العربية معتمدة)", "خطوط أو ومضات في الشاشة أثناء الألعاب", null, "SymptomText(symptom=GAME_SCREEN_LINES_OR_FLICKER)"),
        Entry("tf_symptom_games_freeze_or_close", "approved-before", "neuralcheck-backend docs/PROTOCOL_CONTRACT.md §٥-ج (SYMPTOM-TEXT) = PLAN-06:1266-1280 (العربية معتمدة)", "تتوقّف الألعاب أو تُغلق", null, "SymptomText(symptom=GAMES_FREEZE_OR_CLOSE)"),
        Entry("tf_symptom_none_noticed", "approved-before", "neuralcheck-backend docs/PROTOCOL_CONTRACT.md §٥-ج (SYMPTOM-TEXT) = PLAN-06:1266-1280 (العربية معتمدة)", "لم ألاحظ شيئاً", null, "SymptomText(symptom=NONE_NOTICED)"),
        Entry("tf_game_tried", "approved-before", "TL:31 «جُرّبت لعبة: نعم/لا» بصياغة المستخدم", "جُرّبت لعبة", null, "GameTried")
    )

    /** The repeat scan: the technician's notice (two texts) and the scanned phone's screen (fourteen). */
    val repeat: List<Entry> = listOf(
        Entry("rp_wait_title", "A5i-1", "DECISIONS-FOR-HASAN-2026-10-21.md A5i-1", "فحص مكرّر: انتظر حتى يبرد الهاتف", "Tekrar testi: telefonun soğumasını bekleyin", "WaitTitle"),
        Entry("rp_ready", "A5i-2", "DECISIONS-FOR-HASAN-2026-10-21.md A5i-2", "جاهز — ابدأ الفحص المكرّر", "Hazır — tekrar testini başlat", "Ready"),
        Entry("rp_not_cooled_warning", "A5i-3", "DECISIONS-FOR-HASAN-2026-10-21.md A5i-3 (reworded: «يُسجَّل» ادّعاء حفظ لا يفعله التطبيق؛ صار «يُرسَل … على أنه تكرار ساخن» وهو ما يفعله (العلم startedBeforeCooling). التركية مقترحة منّي حرفيةً من العربية: تحتاج متحدّثاً)", "لم يبرد الهاتف بعد — يُرسَل الفحص على أنه تكرار ساخن", "Telefon henüz soğumadı — test sıcak tekrar olarak gönderilir", "NotCooledWarning"),
        Entry("rp_start_anyway", "A5i-4", "DECISIONS-FOR-HASAN-2026-10-21.md A5i-4", "ابدأ رغم ذلك", "Yine de başlat", "StartAnyway"),
        Entry("rp_wait", "A5i-5", "DECISIONS-FOR-HASAN-2026-10-21.md A5i-5", "انتظر", "Bekle", "Wait"),
        Entry("rp_temperature_unreadable", "A5i-6", "DECISIONS-FOR-HASAN-2026-10-21.md A5i-6", "تعذّر قراءة حرارة البطارية", "Pil sıcaklığı okunamadı", "TemperatureUnreadable"),
        Entry("rp_notice_body", "A5j-1", "DECISIONS-FOR-HASAN-2026-10-21.md A5j-1 (reworded: حُذفت «يقيس ثبات النتائج»: ادّعاء قياس لا يفعله الكود)", "اختير هذا الهاتف لفحص مكرّر بارد. أعده حين يبرد.", null, "NoticeBody"),
        Entry("rp_not_now", "A5j-2", "DECISIONS-FOR-HASAN-2026-10-21.md A5j-2", "ليس الآن", null, "NotNow"),
        Entry("rp_refused_parent_joiner_unknown", "A6-1", "DECISIONS-FOR-HASAN-2026-10-21.md A6-1", "تعذّر بدء الفحص المكرّر: لا يمكن تحديد الهاتف المفحوص في هذه الجلسة.", "Tekrar testi başlatılamadı: bu oturumdaki test edilen telefon belirlenemiyor.", "StartRefused(refusal=PARENT_JOINER_UNKNOWN)"),
        Entry("rp_refused_already_started", "A6-2", "DECISIONS-FOR-HASAN-2026-10-21.md A6-2", "بدأ الفحص المكرّر لهذا الهاتف من قبل.", "Bu telefon için tekrar testi daha önce başlatıldı.", "StartRefused(refusal=ALREADY_STARTED)"),
        Entry("rp_refused_not_selected", "A6-3", "DECISIONS-FOR-HASAN-2026-10-21.md A6-3", "لم يُختَر هذا الهاتف لفحص مكرّر.", "Bu telefon tekrar testi için seçilmedi.", "StartRefused(refusal=NOT_SELECTED)"),
        Entry("rp_refused_session_not_completed", "A6-4", "DECISIONS-FOR-HASAN-2026-10-21.md A6-4", "لم يكتمل الفحص الأوّل بعد.", "İlk test henüz tamamlanmadı.", "StartRefused(refusal=SESSION_NOT_COMPLETED)"),
        Entry("rp_refused_parent_owner_unknown", "A6-5", "DECISIONS-FOR-HASAN-2026-10-21.md A6-5", "تعذّر بدء الفحص المكرّر: لا يمكن تحديد صاحب الجلسة.", "Tekrar testi başlatılamadı: oturum sahibi belirlenemiyor.", "StartRefused(refusal=PARENT_OWNER_UNKNOWN)"),
        Entry("rp_refused_record_absent", "A6-6", "DECISIONS-FOR-HASAN-2026-10-21.md A6-6", "لا يوجد سجلّ فحص مكرّر لهذا الفحص.", "Bu test için tekrar testi kaydı bulunmuyor.", "StartRefused(refusal=RECORD_ABSENT)"),
        Entry("rp_refused_state_unknown", "A6-7", "DECISIONS-FOR-HASAN-2026-10-21.md A6-7", "تعذّر قراءة حالة الفحص المكرّر.", "Tekrar testinin durumu okunamadı.", "StartRefused(refusal=STATE_UNKNOWN)"),
        Entry("rp_start_not_sent", "A6-8", "DECISIONS-FOR-HASAN-2026-10-21.md A6-8", "لم يبدأ الفحص المكرّر، تحقّق من الاتصال وأعد المحاولة", "Tekrar testi başlamadı; bağlantıyı kontrol edip tekrar deneyin", "StartNotSent")
    )

    /** A5h-2: the battery window of the scan start, with the three numbers the dialog fills (the window's two limits and the level now). */
    val window: Entry =
        Entry("scan_error_battery_window_fmt", "A5h-2", "DECISIONS-FOR-HASAN-2026-10-21.md A5h-2 (reworded: الرقمان 30 و85 صارا %1\$d و%2\$d من ScanBatteryWindow (القاعدة 24: رقم واحد في مكان واحد)؛ والتركية على نسق scan_error_battery_too_low_fmt القائم؛ التركية مقترحة منّي حرفيةً من العربية: تحتاج متحدّثاً)", "يجب أن يكون الشحن بين %1\$d%% و%2\$d%% لبدء الفحص — الآن %3\$d%%", "Taramayı başlatmak için şarj %%%1\$d ile %%%2\$d arasında olmalı — şu an %%%3\$d", "window")
}
