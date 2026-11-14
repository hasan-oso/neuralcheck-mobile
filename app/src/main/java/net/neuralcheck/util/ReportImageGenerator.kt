package net.neuralcheck.util

import android.content.Context
import android.graphics.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.neuralcheck.R

/**
 * Generates shareable report image using Android Canvas API.
 * Width: 1080px, variable height based on content.
 *
 * All user-facing strings come from string resources so the image is rendered
 * in the user's active locale (Arabic or Turkish).
 */
class ReportImageGenerator(private val context: Context) {

    companion object {
        private const val WIDTH = 1080
        private const val MARGIN = 48
        private const val SECTION_GAP = 32
    }

    suspend fun generateReportImage(
        grade: String, score: Float, deviceModel: String, scanDate: String,
        categoryResults: Map<String, Pair<Int, Int>>, // category -> (pass, total)
        qrBitmap: Bitmap? = null
    ): Bitmap = withContext(Dispatchers.Default) {
        val totalHeight = calculateHeight(categoryResults.size, qrBitmap != null)
        val bitmap = Bitmap.createBitmap(WIDTH, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background
        canvas.drawColor(Color.WHITE)

        var y = MARGIN.toFloat()

        // Header: App name
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 48f; typeface = Typeface.DEFAULT_BOLD; color = Color.parseColor("#006B5B"); textAlign = Paint.Align.CENTER }
        canvas.drawText(context.getString(R.string.report_image_title), WIDTH / 2f, y + 48f, titlePaint)
        y += 80f

        // Grade circle
        val gradeColor = when (grade) { "A" -> Color.parseColor("#2E7D32"); "B" -> Color.parseColor("#66BB6A"); "C" -> Color.parseColor("#FF9800"); else -> Color.parseColor("#D32F2F") }
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = gradeColor; style = Paint.Style.FILL }
        canvas.drawCircle(WIDTH / 2f, y + 80f, 72f, circlePaint)
        val gradePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 64f; typeface = Typeface.DEFAULT_BOLD; color = Color.WHITE; textAlign = Paint.Align.CENTER }
        canvas.drawText(grade, WIDTH / 2f, y + 100f, gradePaint)
        y += 180f

        // Score
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f; color = gradeColor; textAlign = Paint.Align.CENTER }
        canvas.drawText("${"%.0f".format(score)}%", WIDTH / 2f, y, scorePaint)
        y += 48f

        // Device info
        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; color = Color.GRAY; textAlign = Paint.Align.CENTER }
        canvas.drawText("$deviceModel • $scanDate", WIDTH / 2f, y, infoPaint)
        y += SECTION_GAP + 16f

        // Divider
        val dividerPaint = Paint().apply { color = Color.parseColor("#E0E0E0"); strokeWidth = 2f }
        canvas.drawLine(MARGIN.toFloat(), y, (WIDTH - MARGIN).toFloat(), y, dividerPaint)
        y += SECTION_GAP.toFloat()

        // Category results — both columns right-aligned so the layout reads
        // correctly whether the canvas is rendered LTR or RTL. The category
        // label hugs the right margin; the count sits near the center, left
        // of the label, with enough whitespace to stay visually separate.
        val catLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; color = Color.BLACK; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        val catValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 28f; textAlign = Paint.Align.RIGHT }
        val valueColumnX = (WIDTH / 2f) - 32f
        categoryResults.forEach { (name, result) ->
            val (pass, total) = result
            canvas.drawText(name, (WIDTH - MARGIN).toFloat(), y, catLabelPaint)
            catValuePaint.color = if (pass == total) Color.parseColor("#4CAF50") else Color.parseColor("#FF9800")
            canvas.drawText("$pass/$total", valueColumnX, y, catValuePaint)
            y += 40f
        }
        y += SECTION_GAP.toFloat()

        // QR code
        if (qrBitmap != null) {
            canvas.drawLine(MARGIN.toFloat(), y, (WIDTH - MARGIN).toFloat(), y, dividerPaint)
            y += SECTION_GAP.toFloat()
            val qrSize = 256
            val qrLeft = (WIDTH - qrSize) / 2f
            val scaledQr = Bitmap.createScaledBitmap(qrBitmap, qrSize, qrSize, true)
            canvas.drawBitmap(scaledQr, qrLeft, y, null)
            y += qrSize + SECTION_GAP
        }

        bitmap
    }

    private fun calculateHeight(categoryCount: Int, hasQr: Boolean): Int {
        var h = MARGIN + 80 + 180 + 48 + SECTION_GAP + 16 + SECTION_GAP // Header + grade + score + info
        h += categoryCount * 40 + SECTION_GAP // Categories
        if (hasQr) h += SECTION_GAP + 256 + SECTION_GAP // QR
        h += MARGIN * 2 // Bottom margin
        return h
    }
}
