package net.neuralcheck.hygiene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The raw session code and session id stay out of logcat - A-SUB wave 1, item 10 (user decision «الآن», round-1 report line 433 item (5): «رمز الجلسة خاماً في logcat: QRScannerViewModel.kt:107 و:123 و:125،
 * وViewModels.kt:2587-2588 و:2766»). The contract of `EvalCardLog` already said it (no session id, owner id, uid, session code or scan id is ever printed raw) and these lines predated it.
 *
 * What it checks, from the source text: every `Log.x(...)` call in main prints a session code, a session id, a decoded QR text or a QR payload only through `LogRedaction.hashId(...)` - the value is
 * hashed, the event stays (the order: «تُهشَّر القيمة لا تُحذف الواقعة») - and each of the ten events is still there. A new Log call that prints one of these names raw fails here.
 * The behaviour (a real flow prints no raw value) is `SessionCodeNotLoggedTest`.
 *
 * Scope, named: the names below are the ones that hold a session code or id in this codebase today. A raw `scanId`, a Firebase `uid` and the Functions error `code` are other things and are not this test's
 * (the order is about the session code); a NEW variable holding a session code under another name is not seen by a text test - the runtime test and the review are what see it.
 */
class SessionCodeLogGuardTest {

    private val mainDir: File = listOf("app/src/main/java", "../app/src/main/java")
        .map { File(it) }
        .firstOrNull { it.isDirectory }
        ?: error("could not locate app/src/main/java from ${File(".").absolutePath}")

    private data class LogCall(val file: String, val line: Int, val text: String)

    /** Every `Log.x(` / `android.util.Log.x(` statement of main, with its arguments (string templates included), comments not stripped inside strings. */
    private val calls: List<LogCall> by lazy {
        val out = mutableListOf<LogCall>()
        for (file in mainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }) {
            val text = file.readText().replace("\r\n", "\n")
            val rel = file.relativeTo(mainDir).path.replace('\\', '/')
            for (m in Regex("""(?:android\.util\.)?Log\.[diwev]\(""").findAll(text)) {
                var i = m.range.last + 1
                var depth = 1
                var inString = false
                while (i < text.length && depth > 0) {
                    val c = text[i]
                    if (inString) {
                        if (c == '\\') i++ else if (c == '"') inString = false
                    } else when (c) {
                        '"' -> inString = true
                        '(' -> depth++
                        ')' -> depth--
                    }
                    i++
                }
                out.add(LogCall(rel, text.substring(0, m.range.first).count { it == '\n' } + 1, text.substring(m.range.first, i).replace(Regex("""\s+"""), " ")))
            }
        }
        out
    }

    /** The string-template interpolations of a call: `${expr}` and `$name`. */
    private fun interpolations(call: LogCall): List<String> =
        Regex("""\$\{((?:[^{}]|\{[^{}]*\})*)\}|\$([A-Za-z_][A-Za-z0-9_]*)""").findAll(call.text).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()

    /** An interpolation that prints a session code, a session id, a decoded QR text or a QR payload, raw. */
    private val rawNames = Regex("""\b(sessionId|sessionCode|activeSessionId|rawCode|session\.sessionId|session\.code)\b(?!\.length\b)|\bpayload\b(?!\.(keys|length)\b)""")

    @Test
    fun `the guard can see the Log calls it is meant to read`() {
        assertTrue("found the Log calls of main (${calls.size})", calls.size > 100)
        assertTrue(calls.any { it.file == "net/neuralcheck/presentation/viewmodel/QRScannerViewModel.kt" })
    }

    @Test
    fun `no Log call prints a session code, a session id, a decoded QR text or a QR payload raw`() {
        val offenders = mutableListOf<String>()
        for (call in calls) {
            for (expr in interpolations(call)) {
                if (expr.startsWith("LogRedaction.hashId(")) continue
                if (rawNames.containsMatchIn(expr)) offenders.add("${call.file}:${call.line}  \${$expr}")
            }
        }
        assertTrue("raw session values in the log: $offenders", offenders.isEmpty())
    }

    @Test
    fun `the joining line of the typed screen does not print the typed code - the one name that is too generic to match by itself`() {
        val joining = calls.single { it.text.contains("Joining session with code") }
        assertEquals("net/neuralcheck/presentation/viewmodel/ViewModels.kt", joining.file)
        assertFalse(joining.text, Regex("""[$]code\b|[$][{]code[}]""").containsMatchIn(joining.text))
        assertTrue(joining.text, joining.text.contains("LogRedaction.hashId(code)"))
    }

    @Test
    fun `the events are still logged - the value is hashed, the event is not deleted`() {
        val events = listOf(
            "onQrDetected: raw=\${LogRedaction.hashId(rawCode)}",
            "parseSessionCode -> \${LogRedaction.hashId(sessionCode)}",
            "dispatch: joinSession(\${LogRedaction.hashId(sessionCode)})",
            "Joining session with code: \${LogRedaction.hashId(code)}",
            "createScanSession result: sessionId=\${LogRedaction.hashId(session.sessionId)}",
            "code=\${LogRedaction.hashId(session.code)}",
            "Attaching session listener: path=\${SessionDocWatcher.COLLECTION}/\${LogRedaction.hashId(sessionId)}",
            "session listener error: path=\$collection/\${LogRedaction.hashId(sessionId)}",
            "bootstrapping anonymous auth for sessionId=\${LogRedaction.hashId(sessionId)}",
            "checkAccess: bypassing auth/subscription — sessionId=\${LogRedaction.hashId(activeSessionId)}",
            "GenerateSession: encoded QR = \${LogRedaction.hashId(payload)} len=\${payload.length}"
        )
        val all = calls.joinToString("\n") { it.text }
        for (event in events) assertTrue("the event is gone or the value is raw again: $event", all.contains(event))
    }
}
