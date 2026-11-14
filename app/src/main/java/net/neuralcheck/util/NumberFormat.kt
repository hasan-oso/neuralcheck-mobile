package net.neuralcheck.util

import java.util.Locale

object NumberFormat {
    private val arabicIndicDigits = charArrayOf('٠','١','٢','٣','٤','٥','٦','٧','٨','٩')
    private val persianDigits = charArrayOf('۰','۱','۲','۳','۴','۵','۶','۷','۸','۹')

    fun toLatin(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val latin = when {
                ch in '٠'..'٩' -> ('0' + (ch - '٠'))
                ch in '۰'..'۹' -> ('0' + (ch - '۰'))
                else -> ch
            }
            sb.append(latin)
        }
        return sb.toString()
    }

    fun format(value: Int): String = value.toString()

    fun format(value: Float, decimals: Int = 0): String =
        String.format(Locale.US, "%.${decimals}f", value)

    fun percent(value: Float, decimals: Int = 0): String =
        "${format(value, decimals)}%"

    fun percent(value: Int): String = "$value%"
}
