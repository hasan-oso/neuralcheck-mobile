package net.neuralcheck.files

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Test support for the golden request-payload samples (fieldsVersion 68, batch 3
 * part 3, rule 39): how a payload map is written to a committed file, how the
 * committed file is compared with a regenerated one, and how its key paths are
 * listed.
 *
 * The compared thing is the TREE, never the text: [diff] walks both trees and
 * reports every path that differs, so the failure names the key that moved.
 */
object GoldenJson {

    // ───────────────────────── writing ─────────────────────────

    /**
     * The canonical text of [value] — the `Map<String, Any>` the callable receives
     * (Maps, Lists, Doubles, Strings, Booleans). Keys are SORTED (the wire order is
     * the DTO declaration order; sorting keeps a committed diff to the keys that
     * actually changed); an object holding only scalars and scalar lists is written
     * on one line; a whole-valued number is written without a fraction, as the
     * Firebase SDK sends it; two-space indent; a final newline.
     */
    fun canonicalText(value: Any?): String {
        val sb = StringBuilder()
        write(sb, value, 0)
        sb.append('\n')
        return sb.toString()
    }

    private fun isScalar(x: Any?): Boolean = x == null || x is String || x is Boolean || x is Number

    private fun isCompactList(l: List<*>) = l.all { isScalar(it) }

    private fun isCompactMap(m: Map<*, *>) =
        m.values.all { isScalar(it) || (it is List<*> && isCompactList(it)) }

    private fun write(sb: StringBuilder, value: Any?, indent: Int) {
        when (value) {
            null -> sb.append("null")
            is String -> sb.append(quote(value))
            is Boolean -> sb.append(value.toString())
            is Number -> sb.append(number(value))
            is Map<*, *> -> writeMap(sb, value, indent)
            is List<*> -> writeList(sb, value, indent)
            else -> error("the payload holds a ${value.javaClass.name}, which a callable payload cannot")
        }
    }

    private fun writeMap(sb: StringBuilder, m: Map<*, *>, indent: Int) {
        if (m.isEmpty()) { sb.append("{}"); return }
        val keys = m.keys.map { it as String }.sorted()
        if (isCompactMap(m)) {
            sb.append('{')
            keys.forEachIndexed { i, k ->
                if (i > 0) sb.append(", ")
                sb.append(quote(k)).append(": ")
                write(sb, m[k], indent)
            }
            sb.append('}')
            return
        }
        val pad = " ".repeat(indent + 2)
        sb.append("{\n")
        keys.forEachIndexed { i, k ->
            sb.append(pad).append(quote(k)).append(": ")
            write(sb, m[k], indent + 2)
            if (i < keys.size - 1) sb.append(',')
            sb.append('\n')
        }
        sb.append(" ".repeat(indent)).append('}')
    }

    private fun writeList(sb: StringBuilder, l: List<*>, indent: Int) {
        if (l.isEmpty()) { sb.append("[]"); return }
        if (isCompactList(l)) {
            sb.append('[')
            l.forEachIndexed { i, e ->
                if (i > 0) sb.append(", ")
                write(sb, e, indent)
            }
            sb.append(']')
            return
        }
        val pad = " ".repeat(indent + 2)
        sb.append("[\n")
        l.forEachIndexed { i, e ->
            sb.append(pad)
            write(sb, e, indent + 2)
            if (i < l.size - 1) sb.append(',')
            sb.append('\n')
        }
        sb.append(" ".repeat(indent)).append(']')
    }

    /** A JSON number: integral values without a fraction (the Firebase SDK sends a whole Double as `3`), the rest as Double.toString says, never an exponent. */
    fun number(n: Number): String {
        if (n is Long || n is Int || n is Short || n is Byte) return n.toString()
        val d = n.toDouble()
        require(d.isFinite()) { "a non-finite number cannot be written to JSON: $d" }
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return d.toLong().toString()
        val s = d.toString()
        return if (s.contains('E') || s.contains('e')) BigDecimal(s).toPlainString() else s
    }

    private fun quote(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c < ' ' -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }

    // ───────────────────────── reading ─────────────────────────

    fun parse(text: String): JsonElement = JsonParser.parseString(text)

    // ───────────────────────── key paths ─────────────────────────

    /**
     * One path per key anywhere in [root]: `a.b.c`, with `[]` after an array
     * (`timeSeries.samples[].v`). Sorted, no duplicates. A key whose NAME is data
     * (`channelReadFailures.cpu_freq_policy0_khz`, `phaseCompletionStatus.3`) is
     * listed literally — it is what a reader of that sample finds.
     */
    fun keyPaths(root: JsonElement): List<String> {
        val out = sortedSetOf<String>()
        walkKeys(root, "", out)
        return out.toList()
    }

    private fun walkKeys(e: JsonElement, path: String, out: MutableSet<String>) {
        when {
            e.isJsonObject -> for ((k, v) in e.asJsonObject.entrySet()) {
                val p = if (path.isEmpty()) k else "$path.$k"
                out += p
                walkKeys(v, p, out)
            }
            e.isJsonArray -> for (el in e.asJsonArray) {
                if (el.isJsonObject || el.isJsonArray) walkKeys(el, "$path[]", out)
            }
            else -> Unit
        }
    }

    /** The leaf NAME of a key path: `timeSeries.footer.cpuPolicies[].policy` -> `policy`. */
    fun leafName(path: String): String = path.substringAfterLast('.')

    // ───────────────────────── comparing ─────────────────────────

    /**
     * Every path at which [expected] (the committed file) and [actual] (the
     * regenerated payload) differ, at most [limit] lines. Numbers compare as
     * doubles; strings, booleans and structure exactly.
     */
    fun diff(expected: JsonElement, actual: JsonElement, limit: Int = 60): List<String> {
        val out = ArrayList<String>()
        diffInto(expected, actual, "$", out, limit)
        return out
    }

    private fun diffInto(e: JsonElement, a: JsonElement, path: String, out: MutableList<String>, limit: Int) {
        if (out.size >= limit) return
        when {
            e.isJsonObject && a.isJsonObject -> {
                val eo: JsonObject = e.asJsonObject
                val ao: JsonObject = a.asJsonObject
                for (k in eo.keySet()) if (!ao.has(k)) out += "$path.$k: in the committed file, NOT in the regenerated payload"
                for (k in ao.keySet()) if (!eo.has(k)) out += "$path.$k: in the regenerated payload, NOT in the committed file"
                for (k in eo.keySet()) if (ao.has(k)) diffInto(eo[k], ao[k], "$path.$k", out, limit)
            }
            e.isJsonArray && a.isJsonArray -> {
                val ea: JsonArray = e.asJsonArray
                val aa: JsonArray = a.asJsonArray
                if (ea.size() != aa.size()) out += "$path: array length ${ea.size()} in the committed file, ${aa.size()} regenerated"
                for (i in 0 until minOf(ea.size(), aa.size())) diffInto(ea[i], aa[i], "$path[$i]", out, limit)
            }
            e.isJsonPrimitive && a.isJsonPrimitive -> {
                val ep = e.asJsonPrimitive
                val ap = a.asJsonPrimitive
                val same = when {
                    ep.isNumber && ap.isNumber -> ep.asDouble == ap.asDouble
                    ep.isBoolean && ap.isBoolean -> ep.asBoolean == ap.asBoolean
                    ep.isString && ap.isString -> ep.asString == ap.asString
                    else -> false
                }
                if (!same) out += "$path: committed `$ep`, regenerated `$ap`"
            }
            else -> out += "$path: committed is ${kind(e)}, regenerated is ${kind(a)}"
        }
    }

    private fun kind(e: JsonElement) = when {
        e.isJsonObject -> "an object"
        e.isJsonArray -> "an array"
        e.isJsonNull -> "null"
        else -> "a scalar"
    }

    // ───────────────────────── hashing ─────────────────────────

    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
