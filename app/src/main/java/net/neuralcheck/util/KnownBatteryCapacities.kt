package net.neuralcheck.util

/**
 * V2 fix #56 (3.4) — small lookup table mapping known [Build.MODEL]
 * strings to their published design battery capacity in mAh.
 *
 * **Not** a rule engine. The AI is still the judge. This table exists so
 * the mobile side can attach a cross-reference to the footer, allowing
 * the AI to detect anomalies like "device reports 2250 mAh design but
 * known model has 4500 mAh" → likely dual-cell halving (kernel reports
 * one cell of a series-pair).
 *
 * Coverage is intentionally minimal: only widely-deployed flagships and
 * popular mid-rangers in MEA/SEA markets where the app sees most usage.
 * Adding more entries is safe (additive only, no logic depends on
 * exhaustiveness).
 *
 * Sources: GSMArena, official spec sheets, FCC filings.
 */
object KnownBatteryCapacities {
    private val map: Map<String, Int> = buildMap {
        // ── Samsung Galaxy S series ───────────────────────────────────
        put("SM-G977N", 4500)  // S10 5G (Korea)
        put("SM-G977B", 4500)  // S10 5G (international)
        put("SM-G977U", 4500)  // S10 5G (USA)
        put("SM-G973F", 3400)  // S10
        put("SM-G975F", 4100)  // S10+
        put("SM-G991B", 4000)  // S21
        put("SM-G996B", 4800)  // S21+
        put("SM-G998B", 5000)  // S21 Ultra
        put("SM-G998U", 5000)
        put("SM-G990B", 4500)  // S21 FE
        put("SM-S901B", 3700)  // S22
        put("SM-S906B", 4500)  // S22+
        put("SM-S908B", 5000)  // S22 Ultra
        put("SM-S911B", 3900)  // S23
        put("SM-S916B", 4700)  // S23+
        put("SM-S918B", 5000)  // S23 Ultra
        put("SM-S921B", 4000)  // S24
        put("SM-S926B", 4900)  // S24+
        put("SM-S928B", 5000)  // S24 Ultra
        put("SM-S928U", 5000)
        // ── Samsung Galaxy A series ───────────────────────────────────
        put("SM-A546B", 5000)  // A54
        put("SM-A536B", 5000)  // A53
        put("SM-A526B", 5000)  // A52
        put("SM-A346B", 5000)  // A34
        put("SM-A336B", 5000)  // A33
        put("SM-A256B", 5000)  // A25
        put("SM-A146B", 5000)  // A14
        // ── Xiaomi / Redmi / POCO ─────────────────────────────────────
        put("2306EPN60G", 5000)   // Xiaomi 13 Ultra
        put("23078PND5G", 4880)   // Xiaomi 13T
        put("2210132G", 4500)     // Xiaomi 13
        put("23116PN5BC", 5300)   // Redmi Note 13 Pro+
        put("M2101K9AG", 4250)    // Mi 11 Lite 5G
        put("21091116AG", 5000)   // Redmi Note 11 Pro
        // ── OnePlus / OPPO / realme ───────────────────────────────────
        put("CPH2305", 4500)  // OnePlus Nord 2 5G
        put("CPH2399", 5000)  // OnePlus Nord CE 2 Lite 5G
        put("CPH2459", 4800)  // OnePlus 10T 5G
        put("CPH2065", 4015)  // OPPO Reno4
        put("CPH2207", 4310)  // OPPO Reno5
        put("RMX3771", 5000)  // realme 11 Pro
        // ── Pixel ─────────────────────────────────────────────────────
        put("GD1YQ", 5000)    // Pixel 8 Pro
        put("GKWS6", 4575)    // Pixel 8
        put("G9BQD", 5050)    // Pixel 7 Pro
        put("GVU6C", 4355)    // Pixel 7
        put("GP4BC", 5003)    // Pixel 6 Pro
        // ── Honor / Huawei ────────────────────────────────────────────
        put("ANY-LX1", 5800)  // Honor Magic5 Pro
        put("ALN-AL10", 5450) // Honor Magic6 Pro
        // ── Transsion (Infinix/Tecno) ─────────────────────────────────
        put("X6856", 5000)    // Tecno Camon 30
        put("X6515", 5000)    // Infinix Hot 40 Pro
    }

    /**
     * Returns the published design capacity (mAh) for the given model
     * code, or null if not in the table. Caller can then compare to a
     * sysfs reading to detect dual-cell halving or counter drift.
     */
    fun get(model: String): Int? = map[model.trim()]

    /**
     * V2 fix #56 (3.4): coarse classification of a sysfs design reading
     * vs known capacity. Returns a tag describing the relationship:
     *   "full_capacity"               — 95-105% of known
     *   "likely_single_cell_reading"  — 45-55% (kernel reporting one cell of a series-pair)
     *   "moderate_degradation"        — 56-94% (real wear or reporting drift)
     *   "anomalous_<pct>pct"          — outside 45-105%
     *   "unknown"                     — sysfs or known capacity unavailable
     *
     * AI uses this to gate confidence in design-capacity-derived metrics:
     * "likely_single_cell_reading" means double the sysfs value before
     * computing SoH ratios; "anomalous" means downweight entirely.
     */
    fun classifyAgainstKnown(sysfsDesignMah: Int?, knownDesignMah: Int?): String {
        if (sysfsDesignMah == null || sysfsDesignMah <= 0) return "unknown"
        if (knownDesignMah == null || knownDesignMah <= 0) return "unknown"

        val ratio = sysfsDesignMah.toFloat() / knownDesignMah.toFloat()
        return when {
            ratio in 0.95f..1.05f -> "full_capacity"
            ratio in 0.45f..0.55f -> "likely_single_cell_reading"
            ratio in 0.56f..0.94f -> "moderate_degradation"
            else -> "anomalous_${(ratio * 100).toInt()}pct"
        }
    }
}
