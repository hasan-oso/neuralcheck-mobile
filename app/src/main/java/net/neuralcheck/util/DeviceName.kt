package net.neuralcheck.util

import android.os.Build

/**
 * The two states of a model's marketing name - row A-P6-20, the APP half (`plan/PLAN-06-DATA-COLLECTION.md:1527-1536`, the row `:1061`; contract A12 as copied in
 * `evidence/run-2026-10-18/protocol-contract-A8-A11-A12-neuralcheck-backend-5361116.md`, neuralcheck-backend@5361116 `functions/src/protocol/marketingNames.js`, NOT deployed - rule 29: a copy, it drifts):
 * «والطراز غير الموجود يُعرض `Build.MODEL` كما هو، موسوماً `name_unmapped`» (PLAN-06:1535). The server's `resolveMarketingName` answers `mapped` (a name the server proposed) or `name_unmapped`, the latter with a
 * reason; the app's static map has two of the four reasons - the other two ([A12] `MAP_ABSENT`, `MAP_UNREADABLE`) are about the server's document, which the app does not read. Nothing sends these: they are the app's own
 * reading of its own map (the server computes its own; `reports.deviceMarketingName` is untouched, A12).
 */
enum class MarketingNameState(val wire: String) {
    MAPPED(MarketingNameWire.MAPPED),
    NAME_UNMAPPED(MarketingNameWire.NAME_UNMAPPED)
}

/**
 * The two words of A12's `resolveMarketingName` state, spelled HERE and nowhere else (rule 24; `DeviceNameTest` pins that). [MarketingNameState] takes them for the app's own map, and the reader of the SERVER's
 * `deviceName.state` in the pair list's answer (`domain/pair/PairLink.kt`, contract A3 (2): «نتيجة `resolveMarketingName` نفسها (A12: المصدر الواحد لكل قارئ)») takes them from here too - the same words of the same
 * contract, read by two readers; nothing is sent with them.
 */
object MarketingNameWire {
    const val MAPPED = "mapped"
    const val NAME_UNMAPPED = "name_unmapped"
}

/** Why a model is `name_unmapped` in the app [A12 reasons that apply to a static map]. */
enum class NameUnmappedReason(val wire: String) {
    /** The map does not hold the model. */
    MODEL_NOT_IN_MAP("MODEL_NOT_IN_MAP"),

    /** There is no model to look up: null, empty or blank. */
    MODEL_ABSENT("MODEL_ABSENT")
}

/**
 * What the app's map says about one model, with the text the screens show for it ([display]). The display is exactly what [DeviceName.prettify] has always returned - this row changes no text and no stored value: a
 * model the map does not hold keeps the fallback below (`Manufacturer Model`), not the plan's bare `Build.MODEL`, because [DeviceName.current] feeds `ScanEntity.deviceModel` and the time-series header and changing
 * the fallback would change what is stored and sent (reported as pending, with the lines).
 */
sealed interface MarketingNameResolution {
    val state: MarketingNameState
    val display: String

    /** The model is in the map: [marketingName] is the map's text, [model] the model as it was asked for. */
    data class Mapped(val model: String, val marketingName: String, override val display: String) : MarketingNameResolution {
        override val state: MarketingNameState get() = MarketingNameState.MAPPED
    }

    /** The model is not in the map, or there is none: [model] comes back as asked for (null when it was null). */
    data class Unmapped(val model: String?, val reason: NameUnmappedReason, override val display: String) : MarketingNameResolution {
        override val state: MarketingNameState get() = MarketingNameState.NAME_UNMAPPED
    }
}

/**
 * Maps Android codenames (`Build.MODEL` strings such as "SM-G977N",
 * "M2102J20SG", "RMX3370") to their consumer-facing marketing names
 * ("Galaxy S10 5G", "POCO X3 Pro", "realme GT Neo 2") so the report screen,
 * history list, and outbound Firestore docs all read the way the user
 * thinks of their phone.
 *
 * Why a static map and not Google's device-catalog API:
 *   1. The device-catalog requires either a JSON pull or the AndroidX
 *      `play-services-base` runtime resolution — both add network or
 *      binary footprint we don't want for a single-screen UX nicety.
 *   2. The Syrian / Turkish second-hand market is dominated by ~50
 *      models (Samsung A/S/Note, Xiaomi Redmi/POCO, Realme, Oppo, Honor,
 *      Infinix). A curated list covers >90 % of scans we'll see; the
 *      fallback to "Manufacturer Model" handles the rest gracefully.
 *
 * A model is looked up as it was reported first, then upper-cased (A-P6-20): the upper-case keys are found whatever the case of the model, and the eleven Pixel keys - which have lower-case letters, so the
 * upper-cased lookup alone never reached them - are found by their own spelling. (The server's map matches literally, A12: the parity of the two maps is pending, see [resolve].)
 */
object DeviceName {

    private val MARKETING_NAMES: Map<String, String> = mapOf(
        // ── Samsung Galaxy S series ────────────────────────────────────
        "SM-G970F" to "Galaxy S10e",
        "SM-G970U" to "Galaxy S10e",
        "SM-G973F" to "Galaxy S10",
        "SM-G973U" to "Galaxy S10",
        "SM-G973N" to "Galaxy S10",
        "SM-G975F" to "Galaxy S10+",
        "SM-G975U" to "Galaxy S10+",
        "SM-G977B" to "Galaxy S10 5G",
        "SM-G977N" to "Galaxy S10 5G",
        "SM-G977U" to "Galaxy S10 5G",
        "SM-G980F" to "Galaxy S20",
        "SM-G981B" to "Galaxy S20 5G",
        "SM-G985F" to "Galaxy S20+",
        "SM-G986B" to "Galaxy S20+ 5G",
        "SM-G988B" to "Galaxy S20 Ultra",
        "SM-G990B" to "Galaxy S21 FE 5G",
        "SM-G991B" to "Galaxy S21 5G",
        "SM-G996B" to "Galaxy S21+ 5G",
        "SM-G998B" to "Galaxy S21 Ultra 5G",
        "SM-S901B" to "Galaxy S22",
        "SM-S906B" to "Galaxy S22+",
        "SM-S908B" to "Galaxy S22 Ultra",
        "SM-S911B" to "Galaxy S23",
        "SM-S916B" to "Galaxy S23+",
        "SM-S918B" to "Galaxy S23 Ultra",
        "SM-S921B" to "Galaxy S24",
        "SM-S926B" to "Galaxy S24+",
        "SM-S928B" to "Galaxy S24 Ultra",
        "SM-S931B" to "Galaxy S25",
        "SM-S936B" to "Galaxy S25+",
        "SM-S938B" to "Galaxy S25 Ultra",

        // ── Samsung Galaxy Note ────────────────────────────────────────
        "SM-N960F" to "Galaxy Note 9",
        "SM-N970F" to "Galaxy Note 10",
        "SM-N975F" to "Galaxy Note 10+",
        "SM-N980F" to "Galaxy Note 20",
        "SM-N981B" to "Galaxy Note 20 5G",
        "SM-N985F" to "Galaxy Note 20 Ultra",
        "SM-N986B" to "Galaxy Note 20 Ultra 5G",

        // ── Samsung Galaxy A series (very common second-hand) ───────────
        "SM-A105F" to "Galaxy A10",
        "SM-A107F" to "Galaxy A10s",
        "SM-A205F" to "Galaxy A20",
        "SM-A207F" to "Galaxy A20s",
        "SM-A305F" to "Galaxy A30",
        "SM-A307F" to "Galaxy A30s",
        "SM-A315F" to "Galaxy A31",
        "SM-A325F" to "Galaxy A32",
        "SM-A326B" to "Galaxy A32 5G",
        "SM-A336B" to "Galaxy A33 5G",
        "SM-A346B" to "Galaxy A34 5G",
        "SM-A405F" to "Galaxy A40",
        "SM-A415F" to "Galaxy A41",
        "SM-A505F" to "Galaxy A50",
        "SM-A507F" to "Galaxy A50s",
        "SM-A515F" to "Galaxy A51",
        "SM-A525F" to "Galaxy A52",
        "SM-A526B" to "Galaxy A52 5G",
        "SM-A528B" to "Galaxy A52s 5G",
        "SM-A536B" to "Galaxy A53 5G",
        "SM-A546B" to "Galaxy A54 5G",
        "SM-A556B" to "Galaxy A55 5G",
        "SM-A715F" to "Galaxy A71",
        "SM-A725F" to "Galaxy A72",
        "SM-A736B" to "Galaxy A73 5G",

        // ── Samsung Galaxy M / F series ────────────────────────────────
        "SM-M315F" to "Galaxy M31",
        "SM-M515F" to "Galaxy M51",
        "SM-F127G" to "Galaxy F12",

        // ── Samsung Galaxy Z (foldables) ───────────────────────────────
        "SM-F700F" to "Galaxy Z Flip",
        "SM-F711B" to "Galaxy Z Flip 3 5G",
        "SM-F721B" to "Galaxy Z Flip 4",
        "SM-F731B" to "Galaxy Z Flip 5",
        "SM-F900F" to "Galaxy Fold",
        "SM-F916B" to "Galaxy Z Fold 2 5G",
        "SM-F926B" to "Galaxy Z Fold 3 5G",
        "SM-F936B" to "Galaxy Z Fold 4",
        "SM-F946B" to "Galaxy Z Fold 5",

        // ── Xiaomi / Redmi / POCO ──────────────────────────────────────
        "M2007J20CG" to "POCO X3 NFC",
        "M2102J20SG" to "POCO X3 Pro",
        "21061110AG" to "POCO X3 GT",
        "2201116PG" to "POCO X4 Pro 5G",
        "22041216G" to "POCO X4 GT",
        "23013PC75G" to "POCO X5 5G",
        "22101320G" to "POCO X5 Pro 5G",
        "M2007J17G" to "Mi 10T Pro",
        "M2007J3SY" to "Mi 10T Lite",
        "M2102K1G" to "Mi 11",
        "2107113SG" to "Mi 11 Lite 5G",
        "M2103K19G" to "Redmi Note 10",
        "M2101K7AG" to "Redmi Note 10 Pro",
        "21091116AG" to "Redmi Note 11",
        "2201116TG" to "Redmi Note 11 Pro",
        "22101317G" to "Redmi Note 12",
        "23021RAA2Y" to "Redmi Note 12 Pro 5G",

        // ── realme ──────────────────────────────────────────────────────
        "RMX3085" to "realme 8 Pro",
        "RMX3241" to "realme 9 Pro+",
        "RMX3370" to "realme GT Neo 2",
        "RMX3471" to "realme GT Neo 3",
        "RMX3501" to "realme 9",

        // ── Oppo / OnePlus / Honor / Infinix / Tecno (popular MEA models)
        "CPH2305" to "OnePlus Nord 2 5G",
        "CPH2399" to "OnePlus Nord CE 2 Lite 5G",
        "CPH2459" to "OnePlus 10T 5G",
        "CPH2065" to "OPPO Reno4",
        "CPH2207" to "OPPO Reno5",
        "ANY-LX1" to "Honor 50",
        "RMO-NX1" to "Honor 70",
        "X680" to "Infinix Hot 10",
        "X688B" to "Infinix Hot 10 Play",
        "CK6" to "Tecno Camon 19",

        // ── Pixel ──────────────────────────────────────────────────────
        "Pixel 4" to "Pixel 4",
        "Pixel 4a" to "Pixel 4a",
        "Pixel 5" to "Pixel 5",
        "Pixel 6" to "Pixel 6",
        "Pixel 6 Pro" to "Pixel 6 Pro",
        "Pixel 7" to "Pixel 7",
        "Pixel 7 Pro" to "Pixel 7 Pro",
        "Pixel 8" to "Pixel 8",
        "Pixel 8 Pro" to "Pixel 8 Pro",
        "Pixel 9" to "Pixel 9",
        "Pixel 9 Pro" to "Pixel 9 Pro"
    )

    /**
     * Resolves the consumer-facing name for the device the app is currently
     * running on. Used at SCAN-TIME so the value persisted to Room +
     * Firestore is already pretty — old rows produced before this helper
     * existed get translated lazily via [prettify] on display.
     */
    fun current(): String {
        val rawModel = Build.MODEL ?: return Build.MANUFACTURER ?: "Unknown device"
        val manufacturer = Build.MANUFACTURER.orEmpty()
        return prettify(rawModel, manufacturer)
    }

    /**
     * Translates a stored codename (e.g. "SM-G977N") into a marketing
     * name (e.g. "Samsung Galaxy S10 5G"). Three-step fallback:
     *
     *   1. Map hit → "$Manufacturer $marketingName" if manufacturer is
     *      known, otherwise just `marketingName` (avoids "Samsung Samsung
     *      Galaxy …" when the stored manufacturer is already inside the
     *      marketing name).
     *   2. No map hit but `model` already contains the manufacturer
     *      (e.g. "Samsung SM-G977N") → return as-is, capitalized.
     *   3. Generic fallback → "$Manufacturer $model" with manufacturer
     *      title-cased ("samsung" → "Samsung").
     *
     * Returns the untouched input if it's blank — keeps the caller's
     * existing fallback story (e.g. "غير معروف") intact.
     */
    fun prettify(model: String?, manufacturer: String? = Build.MANUFACTURER): String = resolve(model, manufacturer).display

    /**
     * [prettify] with the state it came from (A-P6-20): [MarketingNameResolution.Mapped] for a model the map holds, [MarketingNameResolution.Unmapped] with its reason for the rest - the plan's `name_unmapped`
     * (PLAN-06:1535), the contract's reasons `MODEL_NOT_IN_MAP` and `MODEL_ABSENT` (A12). The [MarketingNameResolution.display] is the text [prettify] has always returned (the steps above); nothing reads the state
     * yet, as nothing reads the server's (A12: «لا قارئ بعد»).
     *
     * NOT DONE, and why (reported with its quotes): the map has no Arabic or Turkish name - the plan wants «بالعربية والتركية والإنجليزية، من بيانات يحرّرها المشرف» (PLAN-06:1533) and no document holds those
     * names (A12 pending 1: «معلّق: مواصفة غائبة»); and the golden-sample parity with the server's map (PLAN-06:1534) has no sample until the administrator enters the server's map (A12 pending 2: «لا عيّنة قبل وجود
     * بيانات»), nor a written definition of what «تطابق» compares (the server matches the model literally and keeps the administrator's English name as is; this map upper-cases and prefixes a manufacturer).
     */
    fun resolve(model: String?, manufacturer: String? = Build.MANUFACTURER): MarketingNameResolution {
        if (model.isNullOrBlank()) return MarketingNameResolution.Unmapped(model, NameUnmappedReason.MODEL_ABSENT, model.orEmpty())
        val mapped = MARKETING_NAMES[model] ?: MARKETING_NAMES[model.uppercase()]
        val mfr = manufacturer.orEmpty().lowercase().replaceFirstChar { it.uppercase() }
        if (mapped != null) {
            // Avoid duplicating manufacturer when the marketing name already
            // starts with it (e.g. "OPPO Reno4"). Substring check is
            // case-insensitive so "Pixel 8" doesn't get prefixed with
            // "Google" twice if the map ever ships a "Google Pixel 8" entry.
            val display = if (mfr.isNotBlank() && !mapped.startsWith(mfr, ignoreCase = true)) {
                "$mfr $mapped"
            } else {
                mapped
            }
            return MarketingNameResolution.Mapped(model, mapped, display)
        }
        // Some devices ship Build.MODEL already containing the manufacturer
        // (e.g. "Samsung SM-G977N"). Don't double-prefix.
        val display = if (mfr.isNotBlank() && model.startsWith(mfr, ignoreCase = true)) model else if (mfr.isNotBlank()) "$mfr $model" else model
        return MarketingNameResolution.Unmapped(model, NameUnmappedReason.MODEL_NOT_IN_MAP, display)
    }

    /** The map's entries, for the test of the map itself (A-P6-20: «وخريطة التطبيق نسخة مختبرة»): nothing else reads the map but [resolve]. */
    internal val knownEntries: Map<String, String> get() = MARKETING_NAMES
}
