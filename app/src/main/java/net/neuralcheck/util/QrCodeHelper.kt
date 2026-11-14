package net.neuralcheck.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

object QrCodeHelper {
    /**
     * Order 2026-09-26 (القرار ١٠٢ البند ٣ / القرار ٦ — "score لا يُعرض
     * للمستخدم إطلاقاً"). This function's own `&s=<score>` query param is
     * REMOVED, not just null-guarded (the null-guard was القرار ٧٦ البند
     * ١'s fix, 2026-09-24 — it only stopped the literal text "s=null" from
     * appearing, it never questioned emitting the score at all). Verified
     * before removal: `parseQrContent` (below) only ever extracts the
     * scanId via `neuralcheck://scan/([a-f0-9-]{36})` — it has never read
     * `g=` or `s=` — and no app version in this repo's history reads
     * `getQueryParameter` on this content (`git log --all -S
     * getQueryParameter` — zero hits) — so no reader anywhere needs this
     * param. The `score` PARAMETER itself is also removed (not merely
     * unused-but-kept) — this was its only purpose. `grade`/`g=` is
     * unaffected — kept as-is, not named by this order.
     */
    fun generateQrBitmap(scanId: String, grade: String? = null): Bitmap =
        generateRawQrBitmap(buildScanQrContent(scanId, grade))

    /**
     * Extracted (order 2026-09-26، القرار ١٠٢ البند ٣) so the payload
     * STRING itself is directly unit-testable without going through
     * [generateRawQrBitmap]'s real `android.graphics.Bitmap`/`Color` calls,
     * which this module's plain JVM unit tests cannot exercise (when written the
     * module lacked Robolectric; it is a test dependency since 3284cca, and whether
     * the bitmap could be inspected under it is not checked here -
     * `testOptions.unitTests.isReturnDefaultValues = true` just makes those
     * calls return stub defaults instead of throwing, it does not make
     * their output inspectable).
     */
    internal fun buildScanQrContent(scanId: String, grade: String?): String = buildString {
        append("neuralcheck://scan/")
        append(scanId)
        if (grade != null) {
            append("?g=$grade")
        }
    }

    /**
     * Generic QR encoder for arbitrary content (e.g. session deeplinks
     * `neuralcheck://session/NC-7429`). Keeps the same 512×512 size +
     * 1-module margin as [generateQrBitmap] so callers that drop the
     * bitmap into the same-sized surface don't need a separate scaler.
     */
    fun generateRawQrBitmap(content: String): Bitmap {
        val hints = mapOf(EncodeHintType.MARGIN to 1)
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512, hints)
        val width = matrix.width
        val height = matrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }

    fun parseQrContent(content: String): String? {
        val regex = "neuralcheck://scan/([a-f0-9-]{36})".toRegex()
        return regex.find(content)?.groupValues?.get(1)
    }

    /**
     * Pulls a session code out of any of the three QR / text formats we
     * expect on the customer-device side:
     *   1. Bare `NC-XXXXXX` text — what the user types into the field, or
     *      what older QRs encoded.
     *   2. App deeplink `neuralcheck://session/NC-XXXXXX` — what the
     *      shop-owner GenerateSessionScreen encodes today.
     *   3. Web URL `https://neuralcheck.net/scan?code=NC-XXXXXX` (also
     *      `http://`, optional `www.`, any path) — what a printed marketing
     *      QR or share-link could carry.
     *
     * Returns null when the input doesn't look like any of the three so
     * callers can surface "invalid code" without a separate validation
     * pass. Length window `{4,20}` accepts 4-digit legacy codes (`NC-1234`)
     * and the new 6-char alphanumeric codes (`NC-AB3F7K`) alike.
     */
    fun parseSessionCode(content: String): String? {
        val trimmed = content.trim().uppercase()
        val codeFragment = "[A-Z0-9-]{4,20}"
        // Format 2: app deeplink. Anchored start so a stray "session/" inside
        // a longer URL doesn't match — the deeplink IS the whole payload.
        val deep = "^NEURALCHECK://SESSION/($codeFragment)".toRegex()
        deep.find(trimmed)?.groupValues?.get(1)?.let { return it }
        // Format 3: web URL with `code=` query parameter. Matches both
        // http/https and any host (most likely neuralcheck.net but we don't
        // hard-code it — a future white-label could swap the domain).
        val webUrl = "^HTTPS?://[^?]+\\?(?:.*&)?CODE=($codeFragment)(?:&.*)?$".toRegex()
        webUrl.find(trimmed)?.groupValues?.get(1)?.let { return it }
        // Format 1: bare code — only when the whole string looks like a code.
        val raw = "^$codeFragment$".toRegex()
        return if (raw.matches(trimmed)) trimmed else null
    }
}
