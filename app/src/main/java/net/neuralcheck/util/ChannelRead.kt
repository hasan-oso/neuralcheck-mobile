package net.neuralcheck.util

/**
 * Outcome of ONE read of a numeric channel (fieldsVersion 68). A read is
 * either a value or a failure that carries its TEXT — never a sentinel
 * (`-1`, `0`, `Long.MIN_VALUE`, `NaN`) that the caller has to guess at and
 * that is indistinguishable from a real reading (rule 22). The text is the
 * error's message, not its class (rule 21), capped by [ReasonText].
 */
sealed interface ChannelRead {

    /** A real reading. [value] is always finite (callers check before wrapping). */
    data class Value(val value: Float) : ChannelRead

    /** No reading; [reason] is the error text (or a statement of what the source returned). */
    data class Failed(val reason: String) : ChannelRead
}

/**
 * Outcome of reading a small text file (a sysfs node): its trimmed text or
 * the failure text. Same contract as [ChannelRead]: the failure keeps the
 * message that tells EACCES from ENOENT, which the exception class alone
 * cannot (both are a FileNotFoundException on Android).
 */
sealed interface FileTextRead {
    data class Text(val text: String) : FileTextRead
    data class Failed(val reason: String) : FileTextRead
}

/**
 * Classifies a failure TEXT (rule 21). The decision rests on what the text
 * says, because one exception class covers causes whose remedy differs:
 * `FileNotFoundException` is thrown for both "open failed: EACCES (Permission
 * denied)" and "open failed: ENOENT (No such file or directory)".
 *
 * [Kind.OTHER] is "the text names neither" — it is not guessed into one of
 * the other two; callers that must still file it under a legacy two-way list
 * (ChannelDiscoveryReport.blockedBySelinux / .missing) say so at that point.
 */
object FailureText {

    enum class Kind {
        /** The source exists and refused access (permission / SELinux). */
        DENIED,

        /** The source does not exist on this device. */
        ABSENT,

        /** The text says neither. */
        OTHER
    }

    private val DENIED_MARKERS = listOf("EACCES", "EPERM", "Permission denied", "Operation not permitted")
    // ENOTDIR: a path component that should be a directory is not — there is
    // no such policy / node, same remedy as ENOENT.
    private val ABSENT_MARKERS = listOf("ENOENT", "No such file or directory", "ENOTDIR", "Not a directory")

    fun classify(text: String): Kind = when {
        DENIED_MARKERS.any { text.contains(it, ignoreCase = true) } -> Kind.DENIED
        ABSENT_MARKERS.any { text.contains(it, ignoreCase = true) } -> Kind.ABSENT
        else -> Kind.OTHER
    }
}
