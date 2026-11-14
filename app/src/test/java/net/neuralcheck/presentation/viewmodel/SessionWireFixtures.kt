package net.neuralcheck.presentation.viewmodel

import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.HttpsCallableResult
import com.google.gson.JsonObject

/**
 * The server's session refusals as they travel, and the exception the Firebase SDK makes of them — batch 2 (order 2026-10-15), item 2.
 *
 * ── Why the test builds BYTES and lets the SDK parse them ──
 * The order: «ما يُحاكي ردّ الخادم في الاختبارات يطابق الشكل في الملحق: {"error":{"details":{"reasonCode":…},"message":…,"status":…}}، ومنه
 * الرسالة المختلفة للرمز نفسه». So a refusal here is the JSON BODY the server sends ([wireBody]); [exceptionFromWire] hands it to the SDK's own
 * parser (`FirebaseFunctionsException.fromResponse`, package-private, firebase-functions 20.4.0 - read from its bytecode) with the SDK's own
 * `Serializer`, by reflection, and what comes out is the exception the app would catch: its `code` from the body's `status`, its `message` from the
 * body's `message`, and `details` a `HashMap` holding `reasonCode`. A hand-built `FirebaseFunctionsException(message, code, details)` would test
 * the test's idea of the SDK, not the SDK. (The parser needs a real `org.json`: these tests run under Robolectric, whose Android classes are real.)
 *
 * The rows are the two tables of Appendix A of the order (the backend diagnosis at neuralcheck-backend `bbb90a1`, relayed, not verified from
 * this repository): messages are the server's, VERBATIM - including the ones that are the same text under two different codes, and the same
 * code under two different texts. The app must not (and does not) tell them apart by message or by Functions code.
 */
internal object SessionWireFixtures {

    /** `{"error":{"details":{"reasonCode":…},"message":…,"status":…}}`; `details` is absent when [reasonCode] is null (`firebase-functions` `toJSON`). */
    fun wireBody(status: String, message: String, reasonCode: String?): String {
        val error = JsonObject()
        if (reasonCode != null) error.add("details", JsonObject().also { it.addProperty("reasonCode", reasonCode) })
        error.addProperty("message", message)
        error.addProperty("status", status)
        return JsonObject().also { it.add("error", error) }.toString()
    }

    /** The exception the SDK makes of [body]. The `Code` argument is the SDK's HTTP-status guess; the body's own `status` overrides it, as in a real answer. */
    fun exceptionFromWire(body: String): FirebaseFunctionsException {
        val serializerClass = Class.forName("com.google.firebase.functions.Serializer")
        val serializer = serializerClass.getDeclaredConstructor().also { it.isAccessible = true }.newInstance()
        val fromResponse = FirebaseFunctionsException::class.java.getDeclaredMethod(
            "fromResponse", FirebaseFunctionsException.Code::class.java, String::class.java, serializerClass
        ).also { it.isAccessible = true }
        return fromResponse.invoke(null, FirebaseFunctionsException.Code.INTERNAL, body, serializer) as FirebaseFunctionsException
    }

    /**
     * The exception the SDK makes when the HTTP call ITSELF fails - no connection, a socket timeout - and no answer ever came (batch 3, item 1). It is not
     * built here: it is the product of the SDK's own callback, `FirebaseFunctions$2.onFailure(call, IOException)` (firebase-functions 20.4.0, read from its bytecode),
     * instantiated by reflection with a `TaskCompletionSource`, so what comes out is what the app's `await()` would throw: for a plain `IOException`
     * `FirebaseFunctionsException(message = "INTERNAL", code = INTERNAL, details = null, cause = [io])`, for an `InterruptedIOException` (a socket timeout is one)
     * `DEADLINE_EXCEEDED` with the same shape. The connection text ("Unable to resolve host ...") is in the CAUSE, not in the message - the fact the leave
     * decision has to read. (`okhttp3.Call` is not on this module's compile classpath, so the method is found by name; `onFailure` does not use its first argument.)
     */
    fun exceptionFromTransportFailure(io: java.io.IOException): FirebaseFunctionsException {
        val tcs = TaskCompletionSource<HttpsCallableResult>()
        val callbackClass = Class.forName("com.google.firebase.functions.FirebaseFunctions$2")
        val callback = callbackClass.getDeclaredConstructor(FirebaseFunctions::class.java, TaskCompletionSource::class.java)
            .also { it.isAccessible = true }
            .newInstance(null, tcs)
        val onFailure = callbackClass.declaredMethods.single { it.name == "onFailure" }.also { it.isAccessible = true }
        onFailure.invoke(callback, null, io)
        return tcs.task.exception as? FirebaseFunctionsException
            ?: throw AssertionError("the SDK's onFailure did not fail the task with a FirebaseFunctionsException: ${tcs.task.exception}")
    }

    /**
     * The text of a socket CONNECT timeout in the shape batch 5, item 2 names it (`SocketTimeoutException("failed to connect to ... after Nms")`; user decision 2, 2026-10-16:
     * «مهلة الاتّصال («failed to connect») تُعدّ «لا اتّصال»»). The wording is the order's; the host, the addresses and the ports in it are made up, and it was not captured from a
     * device here. A READ timeout has no such text - its fixtures say `timeout` - and that difference is all that separates the two (the SDK gives both the same code).
     */
    const val CONNECT_TIMEOUT_MESSAGE = "failed to connect to cloudfunctions.net/142.250.185.74 (port 443) from /192.168.1.23 (port 51234) after 10000ms"

    /**
     * What the SDK throws for a connect timeout: its own `onFailure` ([exceptionFromTransportFailure]) with a `SocketTimeoutException`, an `InterruptedIOException` - so
     * `DEADLINE_EXCEEDED`, message the bare code name, the connection text in the cause only.
     */
    fun connectTimeoutFailure(): FirebaseFunctionsException =
        exceptionFromTransportFailure(java.net.SocketTimeoutException(CONNECT_TIMEOUT_MESSAGE))

    /**
     * The failures a call ends in when it never reached the server because there is no connection (batch 3, item 1; moved here in batch 4, item 1 because five places read
     * them - six since batch 5, item 1: the typed join, the QR join, the owner's cancel, the customer's leave, the evaluation submission and the analysis call). The first five
     * are what the SDK throws for a transport `IOException` - the product of its own `onFailure` callback ([exceptionFromTransportFailure]): message `INTERNAL` (`DEADLINE_EXCEEDED`
     * for the connect timeout, which is an `InterruptedIOException`), the connection text only in the cause. The last is the bare `IOException` older tests used before - the
     * text in the failure's own message; the SDK never produces it, but a caller's own `IOException` (the one `callWithTimeout` throws is of that shape) does.
     */
    fun offlineFailures(): List<Pair<String, Exception>> = listOf(
        "SDK: UnknownHostException" to exceptionFromTransportFailure(
            java.net.UnknownHostException("Unable to resolve host \"cloudfunctions.net\": No address associated with hostname")
        ),
        "SDK: ConnectException" to exceptionFromTransportFailure(java.net.ConnectException("Failed to connect to /142.250.185.74:443")),
        "SDK: SocketException, Network is unreachable" to exceptionFromTransportFailure(java.net.SocketException("Network is unreachable")),
        "SDK: the class name in the cause's text" to exceptionFromTransportFailure(java.io.IOException("java.net.UnknownHostException: cloudfunctions.net")),
        "SDK: a connect timeout, wrapped as DEADLINE_EXCEEDED (batch 5, item 2)" to connectTimeoutFailure(),
        "bare IOException with the connection text" to java.io.IOException("Unable to resolve host \"cloudfunctions.net\": No address associated with hostname")
    )

    /**
     * A refusal whose `reasonCode` is the TEXT «network» (a code the app does not know) and whose message says network too: the server ANSWERED, so it is not a connection
     * failure at any of the six places (batch 4, item 1; the QR join since batch 5, item 1). The status is `INTERNAL` on purpose: no place shows a server text verbatim for it, so what is shown is the
     * place's own text for a failure that is not a disconnect.
     */
    val REASON_CODE_NETWORK_REFUSAL = Refusal("a reasonCode whose text is network", "INTERNAL", "network error", "network")

    /** One refusal on the wire. [label] says which row of the Appendix it is (the phase for the join). */
    data class Refusal(val label: String, val status: String, val message: String, val reasonCode: String?) {
        val body: String get() = wireBody(status, message, reasonCode)
        fun exception(): FirebaseFunctionsException = exceptionFromWire(body)
    }

    // ---- the join: Appendix A, table (a). The three messages the join sends (joinScanSession.js:60-65 and the two stage tables). ----

    const val JOIN_INVALID_OR_EXPIRED_MESSAGE = "الكود غير صالح أو منتهي الصلاحية"
    const val JOIN_SESSION_MISSING_MESSAGE = "الجلسة غير موجودة"
    const val JOIN_NOT_AVAILABLE_MESSAGE = "الجلسة ليست متاحة للانضمام"
    const val JOIN_CODE_EXPIRED_MESSAGE = "انتهت صلاحية الكود"
    const val SESSION_NOT_VALID_MESSAGE = "جلسة الفحص غير صالحة"

    /** Every refusal `joinScanSession` sends WITH a `reasonCode` (the rows of table a), lookup phase and transaction phase. */
    val JOIN_REFUSALS: List<Refusal> = listOf(
        Refusal("lookup: no document with the code", "NOT_FOUND", JOIN_INVALID_OR_EXPIRED_MESSAGE, "SESSION_CODE_INVALID"),
        Refusal("transaction: the document vanished", "NOT_FOUND", JOIN_SESSION_MISSING_MESSAGE, "SESSION_CODE_INVALID"),
        Refusal("lookup: joined by another device", "NOT_FOUND", JOIN_INVALID_OR_EXPIRED_MESSAGE, "SESSION_ALREADY_JOINED"),
        Refusal("transaction: joined by another device meanwhile", "FAILED_PRECONDITION", JOIN_NOT_AVAILABLE_MESSAGE, "SESSION_ALREADY_JOINED"),
        Refusal("lookup: the joiner's own, completed", "NOT_FOUND", JOIN_INVALID_OR_EXPIRED_MESSAGE, "SESSION_COMPLETED"),
        Refusal("transaction: completed meanwhile", "FAILED_PRECONDITION", JOIN_NOT_AVAILABLE_MESSAGE, "SESSION_COMPLETED"),
        Refusal("lookup: cancelled", "NOT_FOUND", JOIN_INVALID_OR_EXPIRED_MESSAGE, "SESSION_CANCELLED"),
        Refusal("transaction: cancelled meanwhile", "FAILED_PRECONDITION", JOIN_NOT_AVAILABLE_MESSAGE, "SESSION_CANCELLED"),
        Refusal("expired (both phases)", "DEADLINE_EXCEEDED", JOIN_CODE_EXPIRED_MESSAGE, "SESSION_EXPIRED"),
        Refusal("transaction: expiresAt missing or corrupt", "PERMISSION_DENIED", SESSION_NOT_VALID_MESSAGE, "SESSION_EXPIRES_AT_INVALID"),
        Refusal("lookup: status unknown", "NOT_FOUND", JOIN_INVALID_OR_EXPIRED_MESSAGE, "SESSION_STATUS_UNKNOWN"),
        Refusal("transaction: status unknown", "FAILED_PRECONDITION", JOIN_NOT_AVAILABLE_MESSAGE, "SESSION_STATUS_UNKNOWN")
    )

    /** Refusals of the join that carry NO `reasonCode`: «الكود مطلوب» (`invalid-argument`) and the SDK's `internal` for a non-HttpsError. */
    val JOIN_REFUSALS_WITHOUT_CODE: List<Refusal> = listOf(
        Refusal("code missing (invalid-argument, no details)", "INVALID_ARGUMENT", "الكود مطلوب", null),
        Refusal("internal (a non-HttpsError, no details)", "INTERNAL", "INTERNAL", null)
    )

    /** A `reasonCode` this app has no case for (a server that grew a code). */
    val JOIN_REFUSAL_UNRECOGNIZED = Refusal("a reasonCode the app does not know", "NOT_FOUND", JOIN_INVALID_OR_EXPIRED_MESSAGE, "SESSION_BRAND_NEW_REASON")

    // ---- the cancel: Appendix A, table (b) (REFUSAL_RESPONSE, cancelScanSession.js:78-92). ----

    val CANCEL_REFUSALS: List<Refusal> = listOf(
        Refusal("not signed in", "UNAUTHENTICATED", "يجب تسجيل الدخول", "CANCEL_NOT_AUTHENTICATED"),
        Refusal("sessionId missing", "INVALID_ARGUMENT", "sessionId مطلوب", "CANCEL_SESSION_ID_REQUIRED"),
        Refusal("reason missing", "INVALID_ARGUMENT", "CANCEL_REASON_REQUIRED", "CANCEL_REASON_REQUIRED"),
        Refusal("reason invalid", "INVALID_ARGUMENT", "CANCEL_REASON_INVALID", "CANCEL_REASON_INVALID"),
        Refusal("not a participant, or no such session", "PERMISSION_DENIED", SESSION_NOT_VALID_MESSAGE, "SESSION_NOT_PARTICIPANT"),
        Refusal("reason not allowed for the role", "INVALID_ARGUMENT", "CANCEL_REASON_NOT_ALLOWED_FOR_ROLE", "CANCEL_REASON_NOT_ALLOWED_FOR_ROLE"),
        Refusal("completed", "FAILED_PRECONDITION", "SESSION_COMPLETED", "SESSION_COMPLETED"),
        Refusal("expired", "FAILED_PRECONDITION", "الجلسة منتهية", "SESSION_EXPIRED"),
        Refusal("expiresAt missing or corrupt", "PERMISSION_DENIED", SESSION_NOT_VALID_MESSAGE, "SESSION_EXPIRES_AT_INVALID"),
        Refusal("status unknown", "FAILED_PRECONDITION", "SESSION_STATUS_UNKNOWN", "SESSION_STATUS_UNKNOWN"),
        Refusal("an analysis is in flight", "FAILED_PRECONDITION", "SESSION_ANALYSIS_IN_FLIGHT", "SESSION_ANALYSIS_IN_FLIGHT"),
        Refusal("the lock's age is unknown", "FAILED_PRECONDITION", "SESSION_CLAIM_AGE_UNKNOWN", "SESSION_CLAIM_AGE_UNKNOWN")
    )

    val CANCEL_REFUSALS_WITHOUT_CODE: List<Refusal> = listOf(
        Refusal("internal (a non-HttpsError, no details)", "INTERNAL", "INTERNAL", null)
    )

    val CANCEL_REFUSAL_UNRECOGNIZED = Refusal("a reasonCode the app does not know", "FAILED_PRECONDITION", "SESSION_COMPLETED", "SESSION_BRAND_NEW_REASON")
}

/**
 * The texts the user approved, verbatim — the GOLDEN reference the resources and the paths are checked against (batch 2, item 2). Source: the
 * order of this batch (decision 90 for the six; `plan/TEXTS-APPROVED-2026-10-10.md:25-28` of the workspace for the four codes, relayed in the order).
 * Written out here on purpose: a test that read the expected text from the resource under test would pass whatever the resource said.
 */
internal object ApprovedSessionTexts {
    data class Text(val ar: String, val tr: String)

    val OWNER_BUTTON = Text("إلغاء الجلسة", "Oturumu iptal et")
    val OWNER_CONFIRM_TITLE = Text("إلغاء هذه الجلسة؟", "Bu oturum iptal edilsin mi?")
    val OWNER_CONFIRM_BODY = Text(
        "لن يتمكّن العميل من استخدام الرمز بعد الإلغاء، وإن كان فحصه جارياً فسيتوقّف.",
        "İptalden sonra müşteri bu kodu kullanamaz; devam eden taraması da durur."
    )
    val OWNER_CANCELLED_STATE = Text("أُلغيت الجلسة", "Oturum iptal edildi")
    /** Batch 3, item 2 (user decision 5, 2026-10-16, approved verbatim in both languages - decision 90): what the owner reads when the customer left. */
    val OWNER_CUSTOMER_LEFT_STATE = Text("غادر العميل الجلسة", "Müşteri oturumdan ayrıldı")
    val JOIN_COMPLETED = Text(
        "اكتمل فحص هذه الجلسة. اطلب رمزاً جديداً من الفنّي.",
        "Bu oturumun taraması tamamlandı. Teknisyenden yeni bir kod isteyin."
    )
    val LEAVE_BUTTON = Text("مغادرة الجلسة", "Oturumdan ayrıl")
    val LEAVE_CONFIRM_TITLE = Text("مغادرة الجلسة؟", "Oturumdan ayrılmak istiyor musunuz?")
    val LEAVE_CONFIRM_BODY = Text(
        "ستُلغى هذه الجلسة، وتحتاج رمزاً جديداً من الفنّي إن أردت الفحص لاحقاً.",
        "Bu oturum iptal edilecek; daha sonra tarama için teknisyenden yeni bir kod istemeniz gerekir."
    )
    val JOIN_ALREADY_JOINED = Text(
        "سبق الانضمام إلى هذه الجلسة. لفحص جديد اطلب رمزاً جديداً من الفنّي.",
        "Bu oturuma daha önce katılındı. Yeni bir tarama için teknisyenden yeni bir kod isteyin."
    )
    val JOIN_CANCELLED = Text(
        "أُلغيت هذه الجلسة. اطلب رمزاً جديداً من الفنّي.",
        "Bu oturum iptal edildi. Teknisyenden yeni bir kod isteyin."
    )
    val CANCEL_COMPLETED = Text(
        "اكتمل فحص هذه الجلسة ولا يمكن إلغاؤها.",
        "Bu oturumun taraması tamamlandı, iptal edilemez."
    )
    val CANCEL_IN_FLIGHT = Text(
        "التحليل جارٍ الآن. أعد محاولة الإلغاء بعد انتهائه.",
        "Analiz sürüyor. Bittikten sonra iptal etmeyi deneyin."
    )

    // ---- A-SUB wave 1 (order 2026-10-17; user decisions 2, 3, 7; decision 90): the four texts, verbatim (order lines 24-27), and the existing no-credit text ----
    val SHOP_SUBSCRIPTION_EXPIRED = Text("اشتراك المحلّ انتهى، تواصل مع الإدارة", "Mağaza aboneliği sona erdi. Lütfen yönetimle iletişime geçin.")
    val SUBSCRIPTION_EXPIRED = Text("انتهى اشتراكك، يرجى التجديد", "Aboneliğiniz sona erdi. Lütfen yenileyin.")
    val SHOP_NO_CREDIT = Text("رصيد فحوصات المحلّ انتهى، تواصل مع الإدارة", "Mağazanın tarama hakkı kalmadı. Lütfen yönetimle iletişime geçin.")
    val FREE_DEVICE_USED = Text("سبق استخدام الفحص المجاني لهذا الجهاز", "Bu cihaz için ücretsiz tarama daha önce kullanıldı.")

    /** The text that existed before A-SUB (`subscription_no_scans`): the personal no-credit refusal and the pre-scan gate show it; the owner's create-refusal for no credit reuses it (the coordinator's decision). */
    val NO_CREDIT_EXISTING = Text("لا يوجد رصيد فحوصات. يرجى شراء اشتراك.", "Tarama hakkınız kalmadı. Lütfen abonelik satın alın.")

    // ---- 2026-10-20 (order app-engineer-5; the user's decision of that date, verbatim: «النصوص العربية الأربعة معتمدة كما هي. التركيان معتمدان»): the two server texts the app now shows from its own resources ----
    /** The neutral refusal of a stored report that is not the caller's (DECISIONS-FOR-HASAN-2026-10-18.md section 2, row 1): it must say nothing about whether a report exists or whose it is. */
    val SCAN_REPORT_NOT_AVAILABLE = Text("تعذّر إتمام الطلب", "İşlem tamamlanamadı")

    /** The owner's create refused for a plan that does not allow a session (the same table, row 2). */
    val SHOP_PLAN_REQUIRED = Text("هذه الميزة غير متاحة في باقتك الحالية", "Bu özellik mevcut paketinizde kullanılamıyor")
}

/**
 * The scan refusals as the SERVER's own code produces them - A-SUB wave 1 (order 2026-10-17), rule 41 (a sample the reader is tested with matches what the real producer makes).
 *
 * ── Where each sample comes from (every one is named, none is invented) ──
 *  - [ANALYZE_BODIES]: **from probe-wire, not from production.** Copied byte for byte from the backend report
 *    `backend-engineer-asub-server-questions-3b7fbd2-2026-10-17.md` (neuralcheck-workspace `evidence/station2-plan/`), lines 113-117: the output of the server's own `probe-wire.js` at
 *    neuralcheck-backend `3b7fbd2` - the real `refuseScan`, `HttpsError.toJSON` and firebase-functions' `encode`, with `db` replaced in memory. That report's own limit stands here: it
 *    is the code's path after the HTTP layer, not a golden sample generated from the emulator through the whole call chain (rule 41's full form), and nothing was read from production.
 *    On that wire: HTTP 403 `PERMISSION_DENIED` for all five, and `details` holds `reasonCode` and nothing else (the report, lines 109-111: `detailsKeys` is `["reasonCode"]`; `stage` does not appear).
 *  - [JOIN_SHOP_SUBSCRIPTION_EXPIRED] and [CREATE_BODIES]: **built here from the text of the order, not from probe-wire and not from production.** The order of 2026-10-17 (from
 *    `PROTOCOL_CONTRACT.md` 6-d-1 at neuralcheck-backend `47ec36e`) says the join refuses with `reasonCode = SHOP_SUBSCRIPTION_EXPIRED` and the expiry text, and that `createScanSession`'s two
 *    refusals became `permission-denied` with `details.reasonCode` `SHOP_NO_CREDIT` / `SHOP_SUBSCRIPTION_EXPIRED` (messages «لا يوجد رصيد فحوصات», «انتهى اشتراكك، يرجى التجديد»). The
 *    body SHAPE is the same one [SessionWireFixtures.wireBody] builds for every other refusal, and the app reads neither the message nor the status of it.
 *
 *  - [CREATE_BODIES]'s third row (`SHOP_PLAN_REQUIRED`) and [STORED_REPORT_REFUSAL] (`SCAN_REPORT_NOT_AVAILABLE`): **built here from the text of the order of 2026-10-20, not from probe-wire and not from production** (order
 *    `order-app-engineer-5-turkish-texts.md` lines 13-14, relayed from neuralcheck-backend@05275ba `storedReportAccess.js:23-31` and `createScanSession.js:108-109`: RELAYED, NOT VERIFIED from this repository). The order names
 *    the Functions code of the first only (`permission-denied`); the create's is written `PERMISSION_DENIED` like its two siblings and the app reads neither (rule 21).
 *
 * The first two sets are not a claim about the deployed server (they were built when the branch `asub-wave1` was not deployed); the last two are the order's account of two refusals that the order says are deployed at neuralcheck-backend 8e5c426.
 */
internal object ScanRefusalWire {
    const val PROBE_WIRE_SOURCE =
        "backend-engineer-asub-server-questions-3b7fbd2-2026-10-17.md lines 113-117 (probe-wire.js at neuralcheck-backend 3b7fbd2) - from probe-wire, NOT from production"

    const val NO_CREDIT_MESSAGE = "لا يوجد رصيد فحوصات. يرجى شراء اشتراك."
    const val EXPIRED_MESSAGE = "انتهى اشتراكك، يرجى التجديد"
    const val FREE_DEVICE_MESSAGE = "تم استخدام الفحص المجاني لهذا الجهاز سابقاً"

    /** The two server messages of the order of 2026-10-20 (the same sentences the app now shows from its own resources; the app never reads them from the wire). */
    const val STORED_REPORT_MESSAGE = "تعذّر إتمام الطلب"
    const val PLAN_REQUIRED_MESSAGE = "هذه الميزة غير متاحة في باقتك الحالية"

    /** The five analysis refusals, verbatim from probe-wire (see the class doc), by `reasonCode`. */
    val ANALYZE_BODIES: Map<String, String> = linkedMapOf(
        "NO_CREDIT" to """{"error":{"details":{"reasonCode":"NO_CREDIT"},"message":"لا يوجد رصيد فحوصات. يرجى شراء اشتراك.","status":"PERMISSION_DENIED"}}""",
        "SUBSCRIPTION_EXPIRED" to """{"error":{"details":{"reasonCode":"SUBSCRIPTION_EXPIRED"},"message":"انتهى اشتراكك، يرجى التجديد","status":"PERMISSION_DENIED"}}""",
        "FREE_DEVICE_USED" to """{"error":{"details":{"reasonCode":"FREE_DEVICE_USED"},"message":"تم استخدام الفحص المجاني لهذا الجهاز سابقاً","status":"PERMISSION_DENIED"}}""",
        "SHOP_NO_CREDIT" to """{"error":{"details":{"reasonCode":"SHOP_NO_CREDIT"},"message":"لا يوجد رصيد فحوصات. يرجى شراء اشتراك.","status":"PERMISSION_DENIED"}}""",
        "SHOP_SUBSCRIPTION_EXPIRED" to """{"error":{"details":{"reasonCode":"SHOP_SUBSCRIPTION_EXPIRED"},"message":"انتهى اشتراكك، يرجى التجديد","status":"PERMISSION_DENIED"}}"""
    )

    /** The join's new refusal, built from the order's text (see the class doc). */
    val JOIN_SHOP_SUBSCRIPTION_EXPIRED: SessionWireFixtures.Refusal =
        SessionWireFixtures.Refusal("join: the shop's subscription ended (built from the order, not probe-wire)", "PERMISSION_DENIED", EXPIRED_MESSAGE, "SHOP_SUBSCRIPTION_EXPIRED")

    /** `createScanSession`'s two new refusals, built from the order's text (see the class doc). */
    val CREATE_BODIES: List<SessionWireFixtures.Refusal> = listOf(
        SessionWireFixtures.Refusal("create: the shop has no scan credit (built from the order, not probe-wire)", "PERMISSION_DENIED", "لا يوجد رصيد فحوصات", "SHOP_NO_CREDIT"),
        SessionWireFixtures.Refusal("create: the shop's subscription ended (built from the order, not probe-wire)", "PERMISSION_DENIED", EXPIRED_MESSAGE, "SHOP_SUBSCRIPTION_EXPIRED"),
        SessionWireFixtures.Refusal("create: the shop's plan does not allow a session (built from the order of 2026-10-20, not probe-wire)", "PERMISSION_DENIED", PLAN_REQUIRED_MESSAGE, "SHOP_PLAN_REQUIRED")
    )

    /** The analysis' neutral stored-report refusal on the account's own scan (order of 2026-10-20: permission-denied, `details.reasonCode = SCAN_REPORT_NOT_AVAILABLE`), built from the order's text. */
    val STORED_REPORT_REFUSAL: SessionWireFixtures.Refusal =
        SessionWireFixtures.Refusal("analysis, personal scan: a stored report that is not the caller's (built from the order of 2026-10-20, not probe-wire)", "PERMISSION_DENIED", STORED_REPORT_MESSAGE, "SCAN_REPORT_NOT_AVAILABLE")

    /** The exception the SDK makes of a probe-wire body (its own parser, see [SessionWireFixtures.exceptionFromWire]). */
    fun analyzeException(reasonCode: String): FirebaseFunctionsException =
        SessionWireFixtures.exceptionFromWire(ANALYZE_BODIES.getValue(reasonCode))
}
