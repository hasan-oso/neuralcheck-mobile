package net.neuralcheck.util

import java.security.MessageDigest

/**
 * The ONE place the app turns an identifier into what it may print (rule 24: one exported source).
 *
 * Order 2026-10-10 (user decision, "Permanent named log lines"): the app's logcat lines on the technician-
 * evaluation chain carry booleans, named states and HASHED ids only — never a session id, owner id, uid, session
 * code or scan id. The format is the SERVER's, so an app line can be matched to a server line:
 * `sha256` over the id's UTF-8 bytes, the first 12 hex characters, prefixed with `#` (relayed by the coordinator
 * from neuralcheck-backend `functions/src/utils/logRedaction.js:46-57` — NOT read from this repository; the
 * production example is `session=#54575b6c694c`). An absent or empty value is `missing` (not hashed); a value
 * that is not a string is `invalid-type`.
 *
 * ASSUMPTION (named, not decided here): "empty" is the empty string only. How the server treats a string of
 * spaces is not known from this repository; this function hashes it.
 */
object LogRedaction {

    const val MISSING = "missing"
    const val INVALID_TYPE = "invalid-type"

    private const val HEX_CHARS = 12

    fun hashId(value: Any?): String {
        if (value == null) return MISSING
        if (value !is String) return INVALID_TYPE
        if (value.isEmpty()) return MISSING
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(HEX_CHARS)
        var i = 0
        while (hex.length < HEX_CHARS) {
            val b = digest[i++].toInt() and 0xFF
            hex.append(HEX_DIGITS[b ushr 4]).append(HEX_DIGITS[b and 0x0F])
        }
        return "#" + hex.toString()
    }

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
}
