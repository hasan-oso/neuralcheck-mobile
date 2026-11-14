package net.neuralcheck.util

/**
 * Wire rules for every "reason" text the app declares (fieldsVersion 68,
 * S-35 — order 2026-10-08: "سقف 200 محرف لنصّ السبب").
 *
 * A reason is the TEXT of an error (rule 21: the message, not the exception
 * class — a class can unify causes whose remedy differs, e.g. one
 * FileNotFoundException for both EACCES and ENOENT). The only thing done to
 * it here is a length cap; the text is otherwise forwarded as produced
 * (rule 20: no inference in the app — the server names the state).
 */
object ReasonText {

    /** S-35: cap, in characters, on any reason text. */
    const val MAX_CHARS: Int = 200

    /** Text used when a throwable carries no message — explicit, never blank. */
    const val NO_MESSAGE: String = "exception without message"

    /**
     * Cuts [text] to [maxChars] characters. A cut that would leave a lone
     * high surrogate at the end drops that half-character as well, so the
     * result is always well-formed UTF-16.
     */
    fun cap(text: String, maxChars: Int = MAX_CHARS): String {
        if (text.length <= maxChars) return text
        var end = maxChars
        if (end > 0 && Character.isHighSurrogate(text[end - 1])) end -= 1
        return text.substring(0, end)
    }

    /** The capped message text of [t]; [NO_MESSAGE] when it has none. */
    fun of(t: Throwable): String {
        val message = t.message
        return cap(if (message.isNullOrBlank()) NO_MESSAGE else message)
    }
}
