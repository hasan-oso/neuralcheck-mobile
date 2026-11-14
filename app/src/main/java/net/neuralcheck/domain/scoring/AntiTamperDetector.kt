package net.neuralcheck.domain.scoring

import android.content.Context
import net.neuralcheck.R
import net.neuralcheck.domain.model.AntiTamperFlag
import net.neuralcheck.domain.model.AntiTamperType
import java.util.Locale

/**
 * Detects anti-tamper flags from scan metadata (ADR 4.12).
 * Flags indicate potential device preparation before scan.
 *
 * Descriptions are resolved from string resources so the UI sees the correct
 * language. The detector is stateless; pass in a [Context] when localized text
 * is needed, or call [detectWithoutLocalization] to get untranslated flags for
 * background/storage use.
 */
object AntiTamperDetector {

    fun detect(
        context: Context,
        batteryTempStart: Float,
        uptimeHours: Float,
        batteryVoltage: Int,
        batteryLevel: Int,
        cpuUsageBaseline: Float
    ): List<AntiTamperFlag> = buildFlags(
        batteryTempStart, uptimeHours, batteryVoltage, batteryLevel, cpuUsageBaseline
    ) { type ->
        when (type) {
            AntiTamperType.COLD_START -> context.getString(R.string.antitamper_cold_start)
            AntiTamperType.RECENT_REBOOT -> context.getString(R.string.antitamper_recent_reboot)
            AntiTamperType.CHARGER_JUST_REMOVED -> context.getString(R.string.antitamper_charger_removed)
            AntiTamperType.HEAVY_BACKGROUND_USAGE -> context.getString(R.string.antitamper_heavy_usage)
        }
    }

    /** Variant for places that don't have a Context (e.g. unit tests). */
    fun detectWithoutLocalization(
        batteryTempStart: Float,
        uptimeHours: Float,
        batteryVoltage: Int,
        batteryLevel: Int,
        cpuUsageBaseline: Float
    ): List<AntiTamperFlag> = buildFlags(
        batteryTempStart, uptimeHours, batteryVoltage, batteryLevel, cpuUsageBaseline
    ) { type -> type.name }

    private inline fun buildFlags(
        batteryTempStart: Float,
        uptimeHours: Float,
        batteryVoltage: Int,
        batteryLevel: Int,
        cpuUsageBaseline: Float,
        describe: (AntiTamperType) -> String
    ): List<AntiTamperFlag> {
        val flags = mutableListOf<AntiTamperFlag>()

        if (batteryTempStart < 15f) {
            flags.add(AntiTamperFlag(
                type = AntiTamperType.COLD_START,
                value = String.format(Locale.US, "%.1f°C", batteryTempStart),
                description = describe(AntiTamperType.COLD_START)
            ))
        }
        if (uptimeHours < 1f) {
            flags.add(AntiTamperFlag(
                type = AntiTamperType.RECENT_REBOOT,
                value = String.format(Locale.US, "%.1f hours", uptimeHours),
                description = describe(AntiTamperType.RECENT_REBOOT)
            ))
        }
        if (batteryTempStart > 35f && batteryVoltage > 4300 && batteryLevel > 95) {
            flags.add(AntiTamperFlag(
                type = AntiTamperType.CHARGER_JUST_REMOVED,
                value = "temp=${batteryTempStart}°C, volt=${batteryVoltage}mV, level=$batteryLevel%",
                description = describe(AntiTamperType.CHARGER_JUST_REMOVED)
            ))
        }
        if (cpuUsageBaseline > 25f) {
            flags.add(AntiTamperFlag(
                type = AntiTamperType.HEAVY_BACKGROUND_USAGE,
                value = String.format(Locale.US, "%.1f%%", cpuUsageBaseline),
                description = describe(AntiTamperType.HEAVY_BACKGROUND_USAGE)
            ))
        }
        return flags
    }

    /**
     * Calculate anti-tamper penalty points.
     * Very cold start (< 10°C) gets an additional 5-point deduction (ADR 8.11).
     */
    fun calculatePenalty(
        @Suppress("UNUSED_PARAMETER") flags: List<AntiTamperFlag>,
        batteryTempStart: Float
    ): Float {
        return if (batteryTempStart < 10f) 5f else 0f
    }
}
