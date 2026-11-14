package net.neuralcheck.data.local.files

/**
 * The ONE source of every time-series channel name (rule 24: a literal on
 * which a distinction rests — a key, a tag, a field name — is a single
 * exported value, never a copy per file; before this object the names were
 * repeated on 49 lines across 5 files and a drift in one of them would have
 * been a silent mis-read).
 *
 * The channel NAME, not its index, is what identifies a column: the two
 * flavors share one `fieldsVersion` with different layouts (launch = V2
 * layout, datacollect = V1 layout), and from fieldsVersion 68 the V2 layout
 * itself is `v[0..10]` fixed followed by per-device channels appended in a
 * fixed order (docs/FV68_CONTRACT.md §٢.١, N-22). Every reader of `v[]` or
 * `variables[]` therefore resolves a column by name, or declares the name it
 * could not find.
 */
object ChannelNames {

    // ── V2 base layout, v[0..10] (frozen by contract: no index moves) ──
    const val TIMESTAMP_MS = "timestamp_ms"
    const val BATTERY_TEMP_C = "battery_temp_c"
    const val BATTERY_VOLTAGE_MV = "battery_voltage_mv"
    const val BATTERY_CURRENT_UA = "battery_current_ua"
    const val BATTERY_LEVEL_PCT = "battery_level_pct"
    const val CPU_FREQ_MAX_KHZ = "cpu_freq_max_khz"
    const val CPU_USAGE_PCT = "cpu_usage_pct"
    const val PACKAGE_POWER_MW = "package_power_mw"
    const val STORAGE_THROUGHPUT_MBPS = "storage_throughput_mbps"
    const val MEMORY_BANDWIDTH_MBPS = "memory_bandwidth_mbps"
    const val GPU_FPS = "gpu_fps"

    // ── Channels of the legacy V1 layout (datacollect flavor) that are not
    //    in the V2 base layout ──
    const val CPU_TEMP_C = "cpu_temp_c"
    const val CPU_FREQ_BIG_KHZ = "cpu_freq_big_khz"
    const val CPU_FREQ_LITTLE_KHZ = "cpu_freq_little_khz"
    const val CPU_FREQ_MID_KHZ = "cpu_freq_mid_khz"
    const val GPU_TEMP_C = "gpu_temp_c"
    const val ACCEL_X = "accel_x"
    const val ACCEL_Y = "accel_y"
    const val ACCEL_Z = "accel_z"
    const val GYRO_X = "gyro_x"
    const val GYRO_Y = "gyro_y"
    const val GYRO_Z = "gyro_z"

    // ── Sources probed by channel discovery that are NOT columns of v[] ──
    const val SKIN_TEMP_C_REAL = "skin_temp_c_real"
    const val THERMAL_HEADROOM = "thermal_headroom"
    const val THERMAL_STATUS = "thermal_status"
    const val ENERGY_COUNTER = "energy_counter"

    // ── fieldsVersion 68: per-device channels appended after v[10] ──
    /** BatteryManager charge counter, raw "as declared by the device" (N-01). */
    const val CHARGE_COUNTER_RAW = "charge_counter_raw"

    private const val CPU_FREQ_POLICY_PREFIX = "cpu_freq_policy"
    private const val CPU_FREQ_POLICY_SUFFIX = "_khz"

    /** `cpu_freq_policy{N}_khz` — one channel per cpufreq policy (S-05, N-22). */
    fun cpuFreqPolicy(policy: Int): String =
        "$CPU_FREQ_POLICY_PREFIX$policy$CPU_FREQ_POLICY_SUFFIX"

    /**
     * Inverse of [cpuFreqPolicy]: the policy number in [name], or null when
     * [name] is not a policy channel name (not the exact form, e.g.
     * `cpu_freq_policy_khz`, `cpu_freq_policy1x_khz`, `cpu_freq_policy-1_khz`).
     */
    fun cpuFreqPolicyIndexOf(name: String): Int? {
        if (!name.startsWith(CPU_FREQ_POLICY_PREFIX) || !name.endsWith(CPU_FREQ_POLICY_SUFFIX)) return null
        val digits = name.substring(
            CPU_FREQ_POLICY_PREFIX.length,
            name.length - CPU_FREQ_POLICY_SUFFIX.length
        )
        if (digits.isEmpty() || !digits.all { it in '0'..'9' }) return null
        val policy = digits.toIntOrNull() ?: return null
        // Only the canonical spelling: "01" would name the same policy as "1".
        return if (policy.toString() == digits) policy else null
    }
}
