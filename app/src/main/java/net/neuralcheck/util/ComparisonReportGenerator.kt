package net.neuralcheck.util

import android.content.Context
import android.graphics.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.neuralcheck.R

/** Generates comparison report image — before/after two-column layout. */
class ComparisonReportGenerator(private val context: Context) {

    suspend fun generateComparisonImage(
        beforeGrade: String, afterGrade: String,
        beforeScore: Float, afterScore: Float,
        @Suppress("UNUSED_PARAMETER") deviceModel: String,
        @Suppress("UNUSED_PARAMETER") date: String,
        changes: List<Triple<String, String, String>> // (testName, before, after)
    ): Bitmap = withContext(Dispatchers.Default) {
        val width = 1080
        val height = 400 + changes.size * 48 + 200
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 40f; typeface = Typeface.DEFAULT_BOLD; color = Color.parseColor("#006B5B"); textAlign = Paint.Align.CENTER }
        canvas.drawText(context.getString(R.string.comparison_image_title), width / 2f, 60f, titlePaint)

        // Two columns
        val colPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 32f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        colPaint.color = Color.parseColor("#FF9800")
        canvas.drawText(
            context.getString(R.string.comparison_image_before, beforeGrade, beforeScore),
            width * 0.25f, 120f, colPaint
        )
        colPaint.color = Color.parseColor("#4CAF50")
        canvas.drawText(
            context.getString(R.string.comparison_image_after, afterGrade, afterScore),
            width * 0.75f, 120f, colPaint
        )

        // Changes table
        var y = 180f
        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; color = Color.DKGRAY }
        val divPaint = Paint().apply { color = Color.parseColor("#E0E0E0"); strokeWidth = 1f }
        changes.forEach { (name, before, after) ->
            rowPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(name, (width - 48).toFloat(), y, rowPaint)
            rowPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(before, width * 0.35f, y, rowPaint)
            canvas.drawText(after, width * 0.65f, y, rowPaint)
            y += 40f
            canvas.drawLine(48f, y, (width - 48).toFloat(), y, divPaint)
            y += 8f
        }

        bitmap
    }
}
