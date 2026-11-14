package net.neuralcheck.domain.form

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How the answer of `recordReportFirstOpened` reads - A-P6-08. The server's answer is `{sessionId, recorded: boolean}` (technicianForm.js:377 in
 * `evidence/run-2026-10-18/technician-form-server-wire-neuralcheck-backend-05275ba.md`, neuralcheck-backend@05275ba - rule 29, a copy that drifts). Rules 22, 25 and 30: both real values, then every
 * other kind of value in `recorded` and in the whole answer, each its own named state - none read as `true` or `false`.
 */
class ReportFirstOpenedReadTest {

    private fun read(text: String) = ReportFirstOpenedRead.of(JsonParser.parseString(text))

    @Test
    fun `the server's two answers - the first open is recorded, every later one is not`() {
        assertEquals(ReportFirstOpenedRead.Recorded, read("""{"sessionId":"sess-1","recorded":true}"""))
        assertEquals(ReportFirstOpenedRead.AlreadyRecorded, read("""{"sessionId":"sess-1","recorded":false}"""))
    }

    @Test
    fun `no answer, a JSON null, a value that is not an object, a missing recorded and a recorded that is not a boolean are each named`() {
        assertEquals(ReportFirstOpenedRead.Unreadable("absent"), ReportFirstOpenedRead.of(null))
        assertEquals(ReportFirstOpenedRead.Unreadable("absent"), read("null"))
        for ((json, kind) in listOf("[]" to "array", "\"x\"" to "string", "1" to "number", "true" to "boolean")) {
            assertEquals(json, ReportFirstOpenedRead.Unreadable("not_an_object_$kind"), read(json))
        }
        assertEquals(ReportFirstOpenedRead.Unreadable("recorded_absent"), read("{}"))
        assertEquals(ReportFirstOpenedRead.Unreadable("recorded_absent"), read("""{"sessionId":"sess-1"}"""))
        assertEquals(ReportFirstOpenedRead.Unreadable("recorded_absent"), read("""{"recorded":null}"""))
        for ((json, kind) in listOf("\"true\"" to "string", "1" to "number", "0" to "number", "{}" to "object", "[]" to "array")) {
            assertEquals(json, ReportFirstOpenedRead.Unreadable("recorded_not_a_boolean_$kind"), read("""{"recorded":$json}"""))
        }
    }

    @Test
    fun `the wire names are the server's`() {
        assertEquals("recordReportFirstOpened", ReportFirstOpenedWire.CALLABLE)
        assertEquals("sessionId", ReportFirstOpenedWire.KEY_SESSION_ID)
        assertEquals("recorded", ReportFirstOpenedWire.KEY_RECORDED)
    }
}
