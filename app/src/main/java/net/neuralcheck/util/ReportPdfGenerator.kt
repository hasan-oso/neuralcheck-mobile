package net.neuralcheck.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.Log
import androidx.core.content.res.ResourcesCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.neuralcheck.R
import net.neuralcheck.data.local.db.entity.FunctionalResultEntity
import net.neuralcheck.data.remote.model.AiCategoryV7
import net.neuralcheck.data.remote.model.AiPipeline
import net.neuralcheck.domain.model.AiAnalysisResult
import net.neuralcheck.domain.model.IssueSeverity
import net.neuralcheck.domain.model.PredictedIssue
import net.neuralcheck.domain.scoring.CategoryScore
import net.neuralcheck.domain.scoring.ScoringResult
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a human-readable PDF report for a completed scan.
 *
 * Uses Android's built-in [PdfDocument] (no external library) and draws via [Canvas].
 * Supports Arabic RTL and Turkish LTR — locale is taken from the app config so the
 * generated PDF always matches the language the user sees in the UI. Brand tokens
 * ("NeuralCheck", verification URL) stay in Latin script regardless of locale.
 */
class ReportPdfGenerator(private val context: Context) {

    suspend fun generateReport(
        scoring: ScoringResult,
        functionalResults: List<FunctionalResultEntity>,
        deviceModel: String,
        scanTimestamp: Long,
        scanId: String,
        qrBitmap: Bitmap,
        aiAnalysis: AiAnalysisResult? = null,
        // Human-readable identifier shown in the QR caption (e.g.
        // "NC-A7K9M2"). Defaults to [scanId] for legacy callers — the launch
        // flavor passes the backend-supplied shortId so the printed caption
        // matches what the user sees on screen and inside the QR payload.
        displayId: String = scanId
    ): File = withContext(Dispatchers.Default) {
        // Defense-in-depth: ReportScreen already hides the Save / Share / QR
        // bottom-bar buttons for free-trial reports, but a future caller (or
        // a programmatic export) could bypass that gate. PDF export is a paid
        // feature — refusing here guarantees no free-trial result ever lands
        // on disk as a shareable artifact.
        if (aiAnalysis?.isFreeTrial == true) {
            throw IllegalStateException(
                "PDF export is not available for free-trial reports — subscribe to unlock"
            )
        }
        val locale = context.resources.configuration.locales[0]
        val isRtl = locale.language == "ar"

        // Use supplied AI analysis when available, otherwise synthesize a local one
        // so the report always has a "plain-language" section even offline.
        val analysis = aiAnalysis ?: buildLocalAnalysis(scoring, functionalResults, isRtl)

        val doc = PdfDocument()
        drawDocument(
            doc = doc,
            scoring = scoring,
            results = functionalResults,
            deviceModel = deviceModel,
            scanTimestamp = scanTimestamp,
            scanId = scanId,
            displayId = displayId,
            qrBitmap = qrBitmap,
            analysis = analysis,
            isRtl = isRtl
        )

        val dir = File(context.cacheDir, "shared_reports").apply { mkdirs() }
        val file = File(dir, "NeuralCheck_report_${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        file
    }

    // ───────────────────────── Layout constants ─────────────────────────

    // Dark palette mirrors the app's in-product theme (DeepSpace / CardDark /
    // NeuralGreen) so the exported PDF looks like an extension of the screen
    // the user just saw, not a generic whitepaper.
    private object Palette {
        const val PAGE_BG = "#0A0E1A"          // DeepSpace
        const val CARD_BG = "#111827"          // CardDark
        const val TEXT_PRIMARY = "#F1F5F9"
        const val TEXT_SECONDARY = "#94A3B8"
        const val TEXT_MUTED = "#64748B"
        const val BORDER = "#1E293B"
        const val DIVIDER = "#1E293B"
        const val BRAND = "#4ADE80"            // NeuralGreen

        const val GRADE_A = "#22C55E"
        const val GRADE_B = "#3B82F6"
        const val GRADE_C = "#F59E0B"
        const val GRADE_D = "#EF4444"

        const val AI_BG = "#0C1F38"
        const val AI_BORDER = "#1E3A5F"
        const val AI_ACCENT = "#7DD3FC"
        const val VERDICT_BG = "#0A2E1F"
        const val VERDICT_BORDER = "#15803D"
        const val VERDICT_ACCENT = "#86EFAC"

        // V2 — CRITICAL is darker than DANGER (silent silicon defect,
        // refuse-to-buy signal). HIGH sits between DANGER and WARNING
        // (orange) for issues serious enough to flag but not silicon-
        // level corruption. Severity ordering top→bottom in the
        // PDF: CRITICAL → DANGER → HIGH → WARNING → INFO.
        const val CRITICAL_BG = "#1A0303"
        const val CRITICAL_BORDER = "#5E0F0F"
        const val CRITICAL_ACCENT = "#FECACA"
        const val DANGER_BG = "#2A0A0A"
        const val DANGER_BORDER = "#7F1D1D"
        const val DANGER_ACCENT = "#FCA5A5"
        const val HIGH_BG = "#2A1505"
        const val HIGH_BORDER = "#9A3412"
        const val HIGH_ACCENT = "#FDBA74"
        const val WARNING_BG = "#2A1A03"
        const val WARNING_BORDER = "#78350F"
        const val WARNING_ACCENT = "#FDE68A"
        const val INFO_BG = "#082F49"
        const val INFO_BORDER = "#1E40AF"
        const val INFO_ACCENT = "#7DD3FC"
        // Order 2026-09-24 (جولة جرد القيم الافتراضية، البند ١): an
        // unrecognised/absent issue severity used to borrow INFO's blue —
        // indistinguishable from a real informational finding. Neutral grey
        // (matches TEXT_SECONDARY/BORDER's tone), deliberately not any of
        // the five real-severity colours above.
        const val UNKNOWN_BG = "#1F2937"
        const val UNKNOWN_BORDER = "#374151"
        const val UNKNOWN_ACCENT = "#9CA3AF"

        const val TAMPER_CLEAN_BG = "#0A2E1F"
        const val TAMPER_CLEAN_BORDER = "#15803D"
        const val TAMPER_CLEAN_TEXT = "#86EFAC"

        // Amber tier reserved for the AI root-cause hypothesis card. Distinct
        // from danger (red) and warning (orange-yellow) so the reader's eye
        // can tell "AI insight" from "broken thing" at a glance.
        const val ROOT_CAUSE_BG = "#2A1F00"
        const val ROOT_CAUSE_BORDER = "#B45309"
        const val ROOT_CAUSE_ACCENT = "#FFB74D"

        const val QR_BG = "#FFFFFF"            // white backing for scannable QR
    }

    private fun gradeColor(grade: String): Int = Color.parseColor(when (grade.uppercase()) {
        "A" -> Palette.GRADE_A
        "B" -> Palette.GRADE_B
        "C" -> Palette.GRADE_C
        "D" -> Palette.GRADE_D
        // Unmeasured-category fix: "?" previously fell through to GRADE_D
        // and printed red in the PDF, the strongest "this failed" signal
        // the document has. Unknown is neutral.
        else -> Palette.TEXT_MUTED
    })

    // A4 in points (1 point = 1/72 inch). PdfDocument expects integer pixels at 72 DPI.
    private val pageWidth = 595
    private val pageHeight = 842
    private val margin = 40f
    private val contentLeft = margin
    private val contentRight = pageWidth - margin
    private val contentWidth = contentRight - contentLeft

    // Reserved vertical space at the bottom of every page for the footer
    // (divider + two lines). Any block whose height would cross `pageHeight -
    // FOOTER_RESERVE` triggers an automatic page break via
    // [PageState.ensureSpace]. Picked empirically so the footer never collides
    // with the last line of content.
    private val footerReserve = 60f

    // ───────────────────────── Page state ─────────────────────────

    /**
     * Mutable rendering cursor plus the "open" PDF page + canvas. Every
     * `drawXxx(...)` helper receives a [PageState] and mutates [y] as it
     * lays out content. Before drawing any non-trivial block, callers invoke
     * [ensureSpace] — if the block won't fit above the footer reservation,
     * the current page is finalized (footer + [PdfDocument.finishPage]) and a
     * fresh page is opened with the brand header already drawn.
     *
     * Page totals: because [PdfDocument] doesn't let us revisit finalized
     * pages, we can't stamp the accurate "X / Y" footer on earlier pages
     * once we discover later overflows. The workaround is two passes — a
     * measurement pass (`knownTotal = null`) counts page breaks using a
     * throwaway [PdfDocument] that we discard, and the actual render pass
     * runs with [knownTotal] set so every footer displays the correct total.
     */
    private inner class PageState(
        private val doc: PdfDocument,
        val isRtl: Boolean,
        private val knownTotal: Int?
    ) {
        var pageNumber: Int = 0
            private set
        private lateinit var page: PdfDocument.Page
        lateinit var canvas: Canvas
            private set
        var y: Float = 0f

        private val totalPages: Int get() = knownTotal ?: pageNumber

        /** Opens the first page. Must be called exactly once before any draw. */
        fun start() {
            pageNumber = 1
            openPage()
        }

        /**
         * Guarantees at least [neededHeight] of usable vertical space below
         * the current [y]. If not, finalizes the current page and opens a
         * fresh one with the header already drawn — so the caller's next
         * draw call lands on the new page.
         */
        fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > pageHeight - footerReserve) {
                rollToNewPage()
            }
        }

        /** Finalizes the last page with its footer. Call once at the end. */
        fun finish() {
            drawFooter(canvas, pageNumber, totalPages, isRtl)
            doc.finishPage(page)
        }

        private fun rollToNewPage() {
            drawFooter(canvas, pageNumber, totalPages, isRtl)
            doc.finishPage(page)
            pageNumber++
            openPage()
        }

        private fun openPage() {
            page = doc.startPage(
                PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            )
            canvas = page.canvas
            fillPage(canvas)
            y = drawHeader(canvas, isRtl)
        }
    }

    // ───────────────────────── Document orchestration ─────────────────────────

    private fun drawDocument(
        doc: PdfDocument,
        scoring: ScoringResult,
        results: List<FunctionalResultEntity>,
        deviceModel: String,
        scanTimestamp: Long,
        scanId: String,
        displayId: String,
        qrBitmap: Bitmap,
        analysis: AiAnalysisResult,
        isRtl: Boolean
    ) {
        // PASS 1 — measure. Run the full pipeline against a throwaway
        // PdfDocument just to count how many pages the content spans. We
        // can't skip this by drawing on a null canvas because layout
        // decisions depend on StaticLayout heights that are only cheap to
        // compute once per block; duplicating rendering is the least-ugly
        // option.
        val measureDoc = PdfDocument()
        val measureState = PageState(measureDoc, isRtl, knownTotal = null)
        renderAll(measureState, scoring, results, deviceModel, scanTimestamp, scanId, displayId, qrBitmap, analysis)
        measureState.finish()
        val totalPages = measureState.pageNumber
        measureDoc.close()

        // PASS 2 — render for real with the accurate total so every footer
        // reads "X / Y" instead of "X / (approximated)".
        val state = PageState(doc, isRtl, knownTotal = totalPages)
        renderAll(state, scoring, results, deviceModel, scanTimestamp, scanId, displayId, qrBitmap, analysis)
        state.finish()
    }

    /**
     * Single source of truth for the render order. Called twice by
     * [drawDocument] — once for the measurement pass, once for the real
     * rendering. Every helper here mutates [PageState.y] and may trigger a
     * page break via [PageState.ensureSpace] without the caller needing to
     * know.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun renderAll(
        state: PageState,
        scoring: ScoringResult,
        results: List<FunctionalResultEntity>,
        deviceModel: String,
        scanTimestamp: Long,
        scanId: String,
        displayId: String,
        qrBitmap: Bitmap,
        analysis: AiAnalysisResult
    ) {
        state.start()
        drawGradeHero(state, scoring)
        drawDeviceInfo(state, deviceModel, scanTimestamp)
        // Phase 1.15: buyer note (depth vs surface) + "possibly serviced" flag,
        // before the AI body so the buyer reads the framing first.
        drawBuyerNote(state)
        drawMaintenanceFlag(state, analysis)
        drawAiAnalysis(state, analysis)
        drawIssues(state, analysis.issues)
        // Render order between issues and category breakdown so the report
        // narrative reads symptoms (issues) → cause (root_cause) → tailored
        // advice (buyer_advice) → component-by-component breakdown.
        drawRootCause(state, analysis.rootCause)
        drawBuyerAdvice(state, analysis.buyerAdvice)
        // Phase 3-c: this file was excluded from Phase 3-b's V7 display
        // work entirely (blocked — see the Phase 3-c order lifting that
        // block). A V7 response never populates `scoring.categoryScores`
        // (AiScoringEngine builds that map from `response.categoryScores`,
        // which V7 leaves null — see AiAnalysisResponse's pipeline doc), so
        // without this branch a V7 report's category section was silently
        // empty. `analysis.categories` (the V7-shaped map) is read instead
        // when the pipeline says so, exactly mirroring the
        // `ai.pipeline == AiPipeline.V7_FINDINGS_BASED` branch
        // ReportScreen's AiAnalysisSection already uses (Phase 3-b). The V6
        // path below is completely unchanged.
        if (analysis.pipeline == AiPipeline.V7_FINDINGS_BASED) {
            analysis.categories?.let { drawCategoryBreakdownV7(state, it) }
        } else {
            // Unmeasured-category fix: the old filter was `totalCount > 0`,
            // which silently DELETED any category the backend reported as
            // unmeasured (0/0) — it simply never appeared in the document, so
            // the reader had no way to know it existed. An unmeasured category
            // is now rendered as an explicit neutral row. Categories that are
            // genuinely empty AND carry no unmeasured marker (legacy reports,
            // rule-based engine) are still skipped.
            drawCategoryBreakdown(
                state,
                scoring.categoryScores.values.filter { cat ->
                    cat.totalCount > 0 || CategoryPresentation.isUnmeasured(
                        measured = cat.measured,
                        grade = cat.grade,
                        totalCount = cat.totalCount
                    )
                }
            )
        }
        drawSpecsGrid(state, results)
        drawPredictedProblems(state, analysis.predictedProblems)
        drawTamperBadge(state, scoring.antiTamperPenalty > 0f)
        // ai_model attribution intentionally not drawn — internal-only field
        // (see ReportScreen comment at AiAnalysisSection for the matching
        // policy on the screen surface).
        drawQrBlock(state, qrBitmap, displayId)
    }

    /** Paints the whole page with the dark background. PdfDocument starts pages
     *  as transparent, which most viewers render as white; we flood-fill to make
     *  the dark theme stick. */
    private fun fillPage(canvas: Canvas) {
        val bg = Paint().apply {
            color = Color.parseColor(Palette.PAGE_BG)
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat(), bg)
    }

    // ───────────────────────── Header / Footer ─────────────────────────

    private fun drawHeader(canvas: Canvas, @Suppress("UNUSED_PARAMETER") isRtl: Boolean): Float {
        val brand = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 22f
            color = Color.parseColor(Palette.BRAND)
            textAlign = Paint.Align.LEFT
        }
        val tagline = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
            textSize = 10f
            color = Color.parseColor(Palette.TEXT_SECONDARY)
            textAlign = Paint.Align.CENTER
        }
        val divider = Paint().apply {
            color = Color.parseColor(Palette.DIVIDER)
            strokeWidth = 0.8f
        }

        // Brand "NeuralCheck" + logo form a combined lockup, centred together so
        // the logo reads as part of the word, not a floating decoration.
        val brandText = "NeuralCheck"
        val brandWidth = brand.measureText(brandText)
        val logoSize = 26f
        val gap = 6f
        val totalWidth = logoSize + gap + brandWidth
        val cx = pageWidth / 2f
        val logoLeft = cx - totalWidth / 2f
        val textLeft = logoLeft + logoSize + gap
        val baselineY = margin + 18f

        drawLogo(canvas, logoLeft, baselineY - logoSize + 4f, logoSize)
        canvas.drawText(brandText, textLeft, baselineY, brand)
        canvas.drawText(
            context.getString(R.string.report_pdf_tagline),
            cx, margin + 34f, tagline
        )
        canvas.drawLine(contentLeft, margin + 48f, contentRight, margin + 48f, divider)
        return margin + 64f
    }

    /** Rasterises the brand vector drawable into a PDF-safe bitmap and draws it. */
    private fun drawLogo(canvas: Canvas, x: Float, y: Float, sizePx: Float) {
        val bitmap = logoBitmap(sizePx.toInt().coerceAtLeast(24))
        if (bitmap != null) canvas.drawBitmap(bitmap, x, y, null)
    }

    private var cachedLogo: Bitmap? = null
    private fun logoBitmap(sizePx: Int): Bitmap? {
        cachedLogo?.let { if (it.width == sizePx) return it }
        val drawable: Drawable = ResourcesCompat.getDrawable(
            context.resources, R.drawable.ic_launcher_foreground, null
        ) ?: return null
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(c)
        cachedLogo = bmp
        return bmp
    }

    private fun drawFooter(canvas: Canvas, page: Int, totalPages: Int, isRtl: Boolean) {
        val paint = TextPaint().apply {
            isAntiAlias = true
            textSize = 9f
            color = Color.parseColor(Palette.TEXT_MUTED)
            textAlign = Paint.Align.CENTER
        }
        val divider = Paint().apply {
            color = Color.parseColor(Palette.DIVIDER)
            strokeWidth = 0.5f
        }
        val y = pageHeight - margin
        canvas.drawLine(contentLeft, y - 22f, contentRight, y - 22f, divider)
        val line1 = "NeuralCheck · ${context.getString(R.string.report_pdf_verify_url)}"
        val line2 = "${context.getString(R.string.report_pdf_page_fmt, page, totalPages)} · ${context.getString(R.string.report_pdf_copyright)}"
        canvas.drawText(bidiSafe(line1, isRtl), pageWidth / 2f, y - 10f, paint)
        canvas.drawText(bidiSafe(line2, isRtl), pageWidth / 2f, y + 2f, paint)
    }

    // ───────────────────────── Grade hero ─────────────────────────

    private fun drawGradeHero(state: PageState, scoring: ScoringResult) {
        // Keep the full hero (circle + score + description) on one page; it's
        // visually indivisible so we force it onto a new page if needed.
        val heroHeight = 4f + 38f * 2 + 22f + 18f + 34f  // ≈ 148f
        state.ensureSpace(heroHeight)

        val canvas = state.canvas
        val isRtl = state.isRtl
        val cx = pageWidth / 2f
        val color = gradeColor(scoring.overallGrade)
        val circleRadius = 38f
        val circleCy = state.y + circleRadius + 4f

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, circleCy, circleRadius, circlePaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 2f
            alpha = 60
        }
        canvas.drawCircle(cx, circleCy, circleRadius + 6f, ringPaint)

        val gradePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 40f
            this.color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
        // Baseline offset ~14% of text size — letters A/B/C/D look centered.
        canvas.drawText(scoring.overallGrade, cx, circleCy + 14f, gradePaint)

        val scoreY = circleCy + circleRadius + 22f
        // Order 2026-09-26 (القرار ١٠٢ البند ٣، القرار ٦ — "score لا يُعرض
        // للمستخدم إطلاقاً"): the score percentage this PDF used to draw
        // under the grade circle (its own scorePaint TextPaint + a
        // canvas.drawText call) is REMOVED, not just null-guarded (order
        // 2026-09-24 القرار ٧٦ البند ١ only fixed a NullPointerException,
        // it never questioned showing the number). Both deleted together —
        // scorePaint had no other reader in this function (checked: zero
        // remaining references after this edit) — rather than left as
        // dead code. This export path is currently unreachable (the
        // Share/Save buttons that would call it are hidden —
        // ReportPdfExportButtons.HIDDEN) but the rule is general, per the
        // order, so it is fixed here too. scoreY is kept — it still
        // anchors the description text below.

        val descPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 12f
            this.color = Color.parseColor(Palette.TEXT_PRIMARY)
            textAlign = Paint.Align.CENTER
        }
        val description = gradeDescription(scoring.overallGrade)
        canvas.drawText(bidiSafe(description, isRtl), cx, scoreY + 18f, descPaint)

        state.y = scoreY + 34f
    }

    // Unknown-grade fix (same pattern as gradeColor() above, and as
    // ReportScreen.kt's GradeSummaryCard): this used to be `when (grade) {
    // "A" -> ...a; "B" -> ...b; "C" -> ...c; else -> report_pdf_grade_d }` —
    // an unresolved grade (the "?" sentinel scoring.overallGrade can hold,
    // see AiVerdictPresentation.resolveOverallGrade) fell into `else` and
    // printed "ضعيف — مشاكل جوهرية" ("Poor — fundamental problems") under
    // the grade circle in the exported PDF, the same false-failure signal
    // gradeColor() was already fixed to avoid for the circle's colour.
    // Shares AiVerdictPresentation.gradeBucket with that fix and with
    // GradeSummaryCard; exhaustive `when`, no `else`, so a forgotten bucket
    // fails to compile instead of silently reading as D.
    private fun gradeDescription(grade: String): String = context.getString(
        when (AiVerdictPresentation.gradeBucket(grade)) {
            AiVerdictPresentation.GradeBucket.A -> R.string.report_pdf_grade_a
            AiVerdictPresentation.GradeBucket.B -> R.string.report_pdf_grade_b
            AiVerdictPresentation.GradeBucket.C -> R.string.report_pdf_grade_c
            AiVerdictPresentation.GradeBucket.D -> R.string.report_pdf_grade_d
            AiVerdictPresentation.GradeBucket.UNKNOWN -> R.string.report_grade_unavailable
        }
    )

    // ───────────────────────── Device info row ─────────────────────────

    private fun drawDeviceInfo(state: PageState, deviceModel: String, timestamp: Long) {
        state.ensureSpace(18f)
        val paint = TextPaint().apply {
            isAntiAlias = true
            textSize = 10f
            color = Color.parseColor(Palette.TEXT_SECONDARY)
            textAlign = Paint.Align.CENTER
        }
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(timestamp))
        state.canvas.drawText(
            bidiSafe("$deviceModel  ·  $date", state.isRtl),
            pageWidth / 2f, state.y, paint
        )
        state.y += 18f
    }

    // ───────────────────────── AI analysis card ─────────────────────────

    private fun drawAiAnalysis(state: PageState, analysis: AiAnalysisResult) {
        val isRtl = state.isRtl
        val padding = 12f
        val cardLeft = contentLeft
        val cardRight = contentRight
        val cardWidth = cardRight - cardLeft

        val badgePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f
            color = Color.parseColor(Palette.AI_ACCENT)
        }
        val summaryPaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 13f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
        }
        val detailsPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.parseColor(Palette.TEXT_SECONDARY)
        }
        val verdictLabelPaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 10f
            color = Color.parseColor(Palette.VERDICT_ACCENT)
        }
        val verdictPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
        }

        // Measure text heights to compute card height
        val innerWidth = (cardWidth - padding * 2).toInt()
        val summaryLayout = buildLayout(analysis.summary, summaryPaint, innerWidth, isRtl)
        val detailsLayout = buildLayout(analysis.details, detailsPaint, innerWidth, isRtl)
        val verdictLabel = context.getString(R.string.report_pdf_ai_verdict_label)
        val verdictLabelLayout = buildLayout(verdictLabel, verdictLabelPaint, innerWidth - 16, isRtl)
        val verdictLayout = buildLayout(analysis.verdict, verdictPaint, innerWidth - 16, isRtl)

        val verdictCardHeight = (verdictLabelLayout.height + 4 + verdictLayout.height + 16).toFloat()
        val cardHeight = padding + 14f +
                summaryLayout.height + 6f +
                detailsLayout.height + 10f +
                verdictCardHeight + padding

        // Keep the whole AI card on a single page. Splitting it mid-card
        // (outer border on one page, verdict on another) breaks the visual
        // metaphor of "the AI's read on your device" being one statement.
        state.ensureSpace(cardHeight + 14f)
        val canvas = state.canvas
        val startY = state.y

        // Outer card
        drawRoundedCard(
            canvas,
            RectF(cardLeft, startY, cardRight, startY + cardHeight),
            fill = Color.parseColor(Palette.AI_BG),
            stroke = Color.parseColor(Palette.AI_BORDER)
        )

        var y = startY + padding
        val badgeText = context.getString(R.string.report_pdf_ai_badge)
        drawSingleLine(canvas, badgeText, cardLeft + padding, cardRight - padding, y + 10f, badgePaint, isRtl)
        y += 16f

        y = drawLayoutAt(canvas, summaryLayout, cardLeft + padding, y, isRtl, innerWidth).let { it + 6f }
        y = drawLayoutAt(canvas, detailsLayout, cardLeft + padding, y, isRtl, innerWidth).let { it + 10f }

        // Verdict inner card
        val verdictTop = y
        val verdictBottom = y + verdictCardHeight
        drawRoundedCard(
            canvas,
            RectF(cardLeft + padding, verdictTop, cardRight - padding, verdictBottom),
            fill = Color.parseColor(Palette.VERDICT_BG),
            stroke = Color.parseColor(Palette.VERDICT_BORDER),
            radius = 8f
        )
        var vy = verdictTop + 8f
        vy = drawLayoutAt(canvas, verdictLabelLayout, cardLeft + padding + 8f, vy, isRtl, innerWidth - 16) + 4f
        drawLayoutAt(canvas, verdictLayout, cardLeft + padding + 8f, vy, isRtl, innerWidth - 16)

        state.y = startY + cardHeight + 14f
    }

    // ───────────────────────── Issues list ─────────────────────────

    private fun drawIssues(state: PageState, issues: List<PredictedIssue>) {
        if (issues.isEmpty()) return

        val isRtl = state.isRtl
        val padding = 10f
        val titleFontPaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12f
        }
        val descFontPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 10.5f
        }
        val innerWidth = (contentWidth - padding * 2).toInt()

        // Measure the first issue so we can reserve enough space for "section
        // title + first card" together. Orphaning the title alone on a page
        // footer (with every card pushed to the next page) reads as a layout
        // bug.
        val firstIssue = issues.first()
        val (_, _, firstAccent) = severityColors(firstIssue.severity)
        titleFontPaint.color = Color.parseColor(firstAccent)
        descFontPaint.color = Color.parseColor(Palette.TEXT_PRIMARY)
        val firstTitleLayout = buildLayout(firstIssue.title, titleFontPaint, innerWidth, isRtl)
        val firstDescLayout = if (firstIssue.description.isNotBlank())
            buildLayout(firstIssue.description, descFontPaint, innerWidth, isRtl) else null
        val firstDescH = firstDescLayout?.height?.let { it + 4 } ?: 0
        val firstCardHeight = padding + firstTitleLayout.height + firstDescH + padding

        val sectionTitlePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
        }
        state.ensureSpace(20f + firstCardHeight + 8f)
        val sectionTitle = context.getString(R.string.report_pdf_issues_title)
        drawSingleLine(state.canvas, sectionTitle, contentLeft, contentRight, state.y + 12f, sectionTitlePaint, isRtl)
        state.y += 20f

        for ((index, issue) in issues.withIndex()) {
            val (bg, border, accent) = severityColors(issue.severity)
            titleFontPaint.color = Color.parseColor(accent)
            descFontPaint.color = Color.parseColor(Palette.TEXT_PRIMARY)

            val titleLayout = buildLayout(issue.title, titleFontPaint, innerWidth, isRtl)
            val descLayout = if (issue.description.isNotBlank())
                buildLayout(issue.description, descFontPaint, innerWidth, isRtl) else null
            val descHeight = descLayout?.height?.let { it + 4 } ?: 0
            val cardHeight = padding + titleLayout.height + descHeight + padding

            // First card's space was already reserved above together with the
            // title so we skip a redundant check there; every subsequent card
            // gets its own overflow gate.
            if (index > 0) state.ensureSpace(cardHeight + 8f)

            val canvas = state.canvas
            val cardTop = state.y
            drawRoundedCard(
                canvas,
                RectF(contentLeft, cardTop, contentRight, cardTop + cardHeight),
                fill = Color.parseColor(bg),
                stroke = Color.parseColor(border)
            )
            var ty = cardTop + padding
            ty = drawLayoutAt(canvas, titleLayout, contentLeft + padding, ty, isRtl, innerWidth)
            if (descLayout != null) {
                ty += 4f
                drawLayoutAt(canvas, descLayout, contentLeft + padding, ty, isRtl, innerWidth)
            }
            state.y = cardTop + cardHeight + 8f
        }
        state.y += 4f
    }

    private fun severityColors(severity: IssueSeverity): Triple<String, String, String> = when (severity) {
        IssueSeverity.CRITICAL -> Triple(Palette.CRITICAL_BG, Palette.CRITICAL_BORDER, Palette.CRITICAL_ACCENT)
        IssueSeverity.DANGER -> Triple(Palette.DANGER_BG, Palette.DANGER_BORDER, Palette.DANGER_ACCENT)
        IssueSeverity.HIGH -> Triple(Palette.HIGH_BG, Palette.HIGH_BORDER, Palette.HIGH_ACCENT)
        IssueSeverity.WARNING -> Triple(Palette.WARNING_BG, Palette.WARNING_BORDER, Palette.WARNING_ACCENT)
        IssueSeverity.INFO -> Triple(Palette.INFO_BG, Palette.INFO_BORDER, Palette.INFO_ACCENT)
        IssueSeverity.UNKNOWN -> Triple(Palette.UNKNOWN_BG, Palette.UNKNOWN_BORDER, Palette.UNKNOWN_ACCENT)
    }

    // ───────────────────────── Category breakdown ─────────────────────────

    private fun drawCategoryBreakdown(state: PageState, categories: List<CategoryScore>) {
        if (categories.isEmpty()) return
        val isRtl = state.isRtl
        val rowHeight = 30f

        // Reserve space for title + first row so we don't leave a dangling
        // "Categories" header on a page by itself.
        state.ensureSpace(6f + 22f + rowHeight)
        state.y += 6f

        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
        }
        drawSingleLine(state.canvas, context.getString(R.string.report_pdf_categories_title),
            contentLeft, contentRight, state.y + 12f, titlePaint, isRtl)
        state.y += 22f

        val namePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
        }
        val ratioPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 10f
            color = Color.parseColor(Palette.TEXT_SECONDARY)
        }
        val gradePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
        }

        for ((index, cat) in categories.withIndex()) {
            // First row's space was bundled with the section title reservation;
            // every subsequent row flips a page on its own when needed.
            if (index > 0) state.ensureSpace(rowHeight)

            val canvas = state.canvas
            val y = state.y
            val color = gradeColor(cat.grade)
            // Icons come from the shared [CategoryTranslations] table so the
            // PDF, the Compose report screen and the AI fingerprint all agree
            // on which glyph represents each category.
            val emoji = CategoryTranslations.iconFor(cat.name)
            val name = localisedCategoryName(cat)
            val unmeasured = CategoryPresentation.isUnmeasured(
                measured = cat.measured,
                grade = cat.grade,
                totalCount = cat.totalCount
            )
            // "0/0" beside a grade badge reads as a score of zero. For an
            // unmeasured category the words replace the fraction, and the
            // badge carries them too instead of a bare "?".
            val ratio = if (unmeasured) {
                context.getString(R.string.report_category_unmeasured)
            } else {
                "${cat.passCount}/${cat.totalCount}"
            }
            val gradeText = if (unmeasured) "—" else cat.grade
            // Same coverage gap as the screen, from the same helper and the
            // same authoritative source (the server's excludedCount), so the
            // two surfaces can never disagree about how much of a category
            // actually ran.
            val notRun = CategoryPresentation.notRunCount(
                excludedCount = cat.excludedCount,
                declaredCount = cat.declaredCount,
                totalCount = cat.totalCount
            )
            gradePaint.color = color

            // Name on the visual start, grade badge + ratio on visual end
            val startX = if (isRtl) contentRight else contentLeft
            val endX = if (isRtl) contentLeft else contentRight
            namePaint.textAlign = if (isRtl) Paint.Align.RIGHT else Paint.Align.LEFT
            ratioPaint.textAlign = if (isRtl) Paint.Align.LEFT else Paint.Align.RIGHT
            gradePaint.textAlign = if (isRtl) Paint.Align.LEFT else Paint.Align.RIGHT

            // The coverage gap rides in the name cell (the PDF row has no
            // second line): "🎮 الرسوميات · ٣ لم تُجرَ". Without it a 2/2
            // row reads as a complete category.
            val coverageSuffix = if (notRun != null) {
                " · " + context.getString(R.string.report_category_not_run_short, notRun)
            } else ""
            val nameText = if (isRtl) "$name$coverageSuffix $emoji" else "$emoji $name$coverageSuffix"
            canvas.drawText(bidiSafe(nameText, isRtl), startX, y + 10f, namePaint)
            canvas.drawText(bidiSafe(ratio, isRtl), endX + (if (isRtl) 26f else -26f), y + 10f, ratioPaint)
            canvas.drawText(gradeText, endX, y + 10f, gradePaint)

            // Order 2026-09-26 (القرار ١٠٢، الملحق الرابع، البند ٥): the
            // score-based progress bar that used to render here (`cat.score
            // / 100f` fill fraction, plus its background track) is REMOVED
            // ENTIRELY, not just its fill — the same principle already
            // applied to `AxisCard` on the report screen (القرار ١٠٢،
            // الملحق الثالث، البند ٣: "إن كان إخفاء درجات المحاور يترك رسماً
            // بلا محتوى، يُخفى الرسم كلّه"): a track-only bar, permanently
            // empty for every row once the fill (its only informative
            // element) is gone, would read as "0% for everyone" — worse
            // than no bar at all, and a new misleading state that doesn't
            // exist today (today an empty track means specifically
            // "unmeasured"). `cat.score` itself, and `ScoringResult`/
            // `CategoryScore` it lives on, are UNCHANGED (data layer, per
            // القرار ١٠٢، الملحق الثالث، البند ٢) — this only removed the
            // drawing. The name/ratio/grade text above (unaffected —
            // datacollect's own on-screen `CategoryRow`/`AxisCard`, which
            // this PDF path shares `ScoringResult.categoryScores` with, is
            // explicitly NOT changed by this order, القرار ١٠٢، الملحق
            // الرابع، البند ٥) and the coverage suffix are untouched.
            state.y += rowHeight
        }
        state.y += 6f
    }

    private fun localisedCategoryName(cat: CategoryScore): String {
        val locale = context.resources.configuration.locales[0]
        // Prefer the shared [CategoryTranslations] lookup so the PDF matches
        // the on-screen report exactly. This also covers the launch/AI path,
        // where [AiScoringEngine] fills `nameAr` with the raw English key
        // ("battery", "display", ...) — CategoryTranslations turns those back
        // into human names. For keys the table doesn't know about we fall
        // back to whatever the source engine provided.
        val translated = CategoryTranslations.localizedName(cat.name, locale.language)
        if (translated != cat.name) return translated
        return if (locale.language == "tr" && cat.nameTr.isNotBlank()) cat.nameTr else cat.nameAr
    }

    // ───────────────────────── V7 category breakdown (Phase 3-c) ─────────────────────────

    /**
     * V7 counterpart of [drawCategoryBreakdown]. V6's row (single line +
     * progress bar) has no room for V7's per-category state/letterSource/
     * reason text, so each category gets its own small card instead — the
     * same fields ReportScreen's `AiCategoryRowV7`/`AiV7LetterSourceRow`
     * already show on screen (Phase 3-b), so the PDF and the live report
     * agree.
     *
     * The battery category additionally renders its `indicators` map right
     * after its card (the Phase 3-c order's "battery fields are displayed,
     * not deferred to PLAN-03" requirement) — see [drawBatteryIndicators].
     *
     * Sort order matches `AiCategoryEvaluationSectionV7` in ReportScreen.kt:
     * worst letter first (D→C→B→A→unknown/unmeasured).
     */
    private fun drawCategoryBreakdownV7(state: PageState, categories: Map<String, AiCategoryV7>) {
        if (categories.isEmpty()) return
        val isRtl = state.isRtl
        val isTurkish = context.resources.configuration.locales[0].language == "tr"

        state.ensureSpace(6f + 22f)
        state.y += 6f
        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
        }
        drawSingleLine(
            state.canvas, context.getString(R.string.report_pdf_categories_title),
            contentLeft, contentRight, state.y + 12f, titlePaint, isRtl
        )
        state.y += 22f

        val priority = mapOf("D" to 4, "C" to 3, "B" to 2, "A" to 1)
        val sorted = categories.entries.sortedByDescending { (_, cat) ->
            priority[cat.letter?.trim()?.uppercase()] ?: 0
        }
        for ((key, cat) in sorted) {
            drawCategoryCardV7(state, key, cat, isTurkish)
        }
    }

    /** Text for the state annotation line; null when [kind] is MEASURED (nothing to show, matches ReportScreen).
     *  `internal` (not `private`) so ReportPdfGeneratorV7StateLineTest can call it directly - when written the module
     *  lacked Robolectric, so a plain JUnit test with a mocked [Context] was the only way to unit-test PDF text (Robolectric is a test dependency since 3284cca). */
    internal fun v7StateLine(kind: AiV7Presentation.CategoryStateKind, rawState: String?): String? = when (kind) {
        AiV7Presentation.CategoryStateKind.MEASURED -> null
        AiV7Presentation.CategoryStateKind.PARTIAL -> context.getString(R.string.report_v7_state_partial)
        AiV7Presentation.CategoryStateKind.UNMEASURED -> context.getString(R.string.report_category_unmeasured)
        AiV7Presentation.CategoryStateKind.UNKNOWN -> context.getString(R.string.report_v7_state_unknown)
        // Order 2026-09-24 (القرار ٨٢ البند ٤): dedicated, human-written
        // text for these two contract-declared states — approved this
        // round, no longer routed through the generic "relayed, no
        // interpretation yet" template below (mirrors ReportScreen.kt's
        // matching `when` block — kept as two independently-maintained
        // copies, same existing pattern as every other state label here).
        AiV7Presentation.CategoryStateKind.ATTESTED_ONLY ->
            context.getString(R.string.report_v7_state_attested_only)
        AiV7Presentation.CategoryStateKind.HARNESS_ERROR ->
            context.getString(R.string.report_v7_state_harness_error)
        // NO_DATA stays on the generic template — its dedicated "reason"
        // copy is القرار ٨٢ البند ٥, explicitly paused this round pending
        // the verifier's per-reason check.
        AiV7Presentation.CategoryStateKind.NO_DATA ->
            context.getString(R.string.report_v7_state_uninterpreted_fmt, rawState ?: "?")
    }

    private fun v7LetterSourceLine(kind: AiV7Presentation.LetterSourceKind): Pair<String, String> = when (kind) {
        AiV7Presentation.LetterSourceKind.DETERMINISTIC ->
            "⚙️" to context.getString(R.string.report_v7_letter_source_deterministic)
        AiV7Presentation.LetterSourceKind.LLM_ADJUSTED ->
            "🤖" to context.getString(R.string.report_v7_letter_source_llm_adjusted)
        AiV7Presentation.LetterSourceKind.UNKNOWN ->
            "❔" to context.getString(R.string.report_v7_letter_source_unknown)
    }

    private fun drawCategoryCardV7(state: PageState, key: String, cat: AiCategoryV7, isTurkish: Boolean) {
        val isRtl = state.isRtl
        val padding = 10f
        val innerWidth = (contentWidth - padding * 2).toInt()

        val label = CategoryTranslations.labelFor(key)
        val name = if (isTurkish) label.tr else label.ar
        val known = AiVerdictPresentation.isKnownGrade(cat.letter)
        val stateKind = AiV7Presentation.classifyState(cat.state)
        val letterSourceKind = AiV7Presentation.classifyLetterSource(cat.letterSource)
        val coveragePct = AiV7Presentation.coveragePercent(coveragePct = null, coverage = cat.coverage)
        val badgeColor = if (known) gradeColor(cat.letter!!.trim().uppercase()) else Color.parseColor(Palette.TEXT_MUTED)
        val badgeText = when {
            known -> cat.letter!!.trim().uppercase()
            stateKind == AiV7Presentation.CategoryStateKind.UNMEASURED -> context.getString(R.string.report_category_unmeasured)
            else -> context.getString(R.string.report_grade_unavailable)
        }

        val metaPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 9.5f
            color = Color.parseColor(Palette.TEXT_SECONDARY)
        }
        // Order 2026-09-25 (القرار ٨٤ البند ١ / القرار ٨٦ البند ٣ / القرار
        // ٨٧ البند ٤) — see ReportScreen.kt's AiCategoryRowV7 for the full
        // rationale (mirrored here so the PDF and the live report agree).
        // The (state, reason) pair now drives ONE line, replacing both the
        // old generic state text AND the raw cat.reason text that used to
        // render verbatim below it — a server-internal code reaching the
        // PDF (rule 22). No separate reasonLayout any more; classifyCategoryReason's
        // UNKNOWN branch logs the raw code instead of drawing it.
        val reasonKind = AiV7Presentation.classifyCategoryReason(cat.state, cat.reason)
        if (reasonKind == AiV7Presentation.CategoryReasonKind.UNKNOWN) {
            Log.w(
                "NeuralCheck.Pdf",
                "category '$key': unrecognised (state='${cat.state}', reason='${cat.reason}') " +
                    "pair — showing report_v7_state_unknown, raw reason not drawn"
            )
        }
        val stateText = when (reasonKind) {
            AiV7Presentation.CategoryReasonKind.NO_HEALTH_BEARING_TESTS_DECLARED ->
                context.getString(R.string.report_v7_reason_no_health_bearing_declared)
            AiV7Presentation.CategoryReasonKind.DECLARED_BUT_NONE_EXECUTED ->
                context.getString(R.string.report_v7_reason_declared_none_executed)
            AiV7Presentation.CategoryReasonKind.BATTERY_UNCALIBRATED ->
                context.getString(R.string.report_v7_reason_battery_uncalibrated)
            AiV7Presentation.CategoryReasonKind.BATTERY_UNMEASURED ->
                context.getString(R.string.report_v7_reason_battery_unmeasured)
            AiV7Presentation.CategoryReasonKind.HARNESS_ERROR_UNMEASURED ->
                context.getString(R.string.report_v7_reason_harness_error_unmeasured)
            AiV7Presentation.CategoryReasonKind.UNKNOWN ->
                context.getString(R.string.report_v7_state_unknown)
            AiV7Presentation.CategoryReasonKind.HIDDEN -> v7StateLine(stateKind, cat.state)
        }
        // Order 2026-09-25 (القرار ٨٧ البند ٤): "لا يظهر إلا لفئة لها حرف" —
        // this row (and its LLM_ADJUSTED reason child — the evidence child
        // was removed separately, القرار ٩٩ البند ٤) claims "حرف محسوب
        // مباشرة من نتائج القياس", meaningless for a category with no
        // letter — see ReportScreen.kt's matching fix.
        val letterSourceLayout = if (known) {
            val (lsIcon, lsLabel) = v7LetterSourceLine(letterSourceKind)
            buildLayout("$lsIcon $lsLabel", metaPaint, innerWidth, isRtl)
        } else null
        val isLlmAdjusted = known && letterSourceKind == AiV7Presentation.LetterSourceKind.LLM_ADJUSTED
        val adjustmentLayout = if (isLlmAdjusted) {
            val text = cat.adjustmentReason?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.report_v7_adjustment_reason_unavailable)
            buildLayout(text, metaPaint, innerWidth - 16, isRtl)
        } else null
        // Order 2026-09-25 (القرار ٩٩ البند ٤): "لا يُعرض الدليل في
        // الموضعين" — the raw evidence reference (adjustmentEvidence,
        // List<String>? — see AiAnalysisResponse.kt, unchanged) used to be
        // joined and drawn here as its own line; removed. adjustmentLayout
        // (the reason line) is unaffected.

        val headerHeight = 14f
        var cardHeight = padding + headerHeight
        if (stateText != null) cardHeight += 4f + 11f
        letterSourceLayout?.let { cardHeight += 8f + it.height }
        adjustmentLayout?.let { cardHeight += 4f + it.height }
        cardHeight += padding

        state.ensureSpace(cardHeight + 8f)
        val canvas = state.canvas
        val top = state.y
        drawRoundedCard(
            canvas,
            RectF(contentLeft, top, contentRight, top + cardHeight),
            fill = Color.parseColor(Palette.CARD_BG),
            stroke = Color.parseColor(Palette.BORDER)
        )

        val headerPaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11.5f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
            textAlign = if (isRtl) Paint.Align.RIGHT else Paint.Align.LEFT
        }
        val badgePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12f
            color = badgeColor
            textAlign = if (isRtl) Paint.Align.LEFT else Paint.Align.RIGHT
        }
        val coveragePaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 10f
            color = Color.parseColor(Palette.TEXT_SECONDARY)
            textAlign = if (isRtl) Paint.Align.LEFT else Paint.Align.RIGHT
        }
        val stateTextPaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 9.5f
            color = Color.parseColor(Palette.WARNING_ACCENT)
        }

        val startX = if (isRtl) contentRight - padding else contentLeft + padding
        val endX = if (isRtl) contentLeft + padding else contentRight - padding
        val headerText = if (isRtl) "$name ${label.icon}" else "${label.icon} $name"
        val headerBaselineY = top + padding + 9f
        canvas.drawText(bidiSafe(headerText, isRtl), startX, headerBaselineY, headerPaint)
        canvas.drawText(badgeText, endX, headerBaselineY, badgePaint)
        if (coveragePct != null) {
            val badgeWidth = badgePaint.measureText(badgeText)
            val coverageX = endX + (if (isRtl) badgeWidth + 8f else -(badgeWidth + 8f))
            canvas.drawText(NumberFormat.toLatin("$coveragePct%"), coverageX, headerBaselineY, coveragePaint)
        }
        var y = top + padding + headerHeight

        if (stateText != null) {
            y += 4f
            drawSingleLine(canvas, stateText, contentLeft + padding, contentRight - padding, y + 8f, stateTextPaint, isRtl)
            y += 11f
        }
        letterSourceLayout?.let {
            y += 8f
            y = drawLayoutAt(canvas, it, contentLeft + padding, y, isRtl, innerWidth)
        }
        adjustmentLayout?.let {
            y += 4f
            y = drawLayoutAt(canvas, it, contentLeft + padding + 16f, y, isRtl, innerWidth - 16)
        }

        state.y = top + cardHeight + 8f

        if (key.trim().lowercase() == "battery") {
            drawBatteryIndicators(state, cat.indicators, cat.socAtStart, cat.socAtEnd)
        }
    }

    // ───────────────────────── Battery indicators (Phase 3-c) ─────────────────────────

    /**
     * Battery-only. Renders `categories.battery.indicators` — the Phase
     * 3-c order's item 2 ("battery fields are displayed, not deferred to
     * PLAN-03" — the letter lock blocks the LETTER, not the display) — plus
     * the scan-level `socAtStart`/`socAtEnd`.
     *
     * `indicators` is a heterogeneous map — no fixed per-indicator schema
     * exists ([AiCategoryV7.indicators]'s doc), so beyond the five common
     * fields ([AiV7Presentation.readIndicator]'s `id`/`status`/`verdict`/
     * `value`/`unit`) every other key — including the differently-shaped
     * `gate` object per indicator — is rendered generically as "key: value"
     * via [AiV7Presentation.formatIndicatorValue], never hand-picked by
     * name, per the order's "لا يُخترع لها مخطَّط" instruction.
     *
     * `socAtStart`/`socAtEnd` are drawn ONCE for the whole section, worded
     * explicitly as scan-level — the exact opposite of the A77 mistake
     * this order calls out by name (misreading a scan-level value as
     * per-indicator). A missing side renders an explicit "unavailable"
     * label rather than a fabricated placeholder (rule 22); both missing
     * skips the line entirely instead of printing an empty one.
     */
    private fun drawBatteryIndicators(
        state: PageState,
        indicators: Map<String, Map<String, Any?>>?,
        socAtStart: Int?,
        socAtEnd: Int?
    ) {
        if (indicators.isNullOrEmpty()) return
        val isRtl = state.isRtl
        val padding = 10f
        val innerWidth = (contentWidth - padding * 2).toInt()

        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
            color = Color.parseColor(Palette.AI_ACCENT)
        }
        val socPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 9.5f
            color = Color.parseColor(Palette.TEXT_SECONDARY)
        }
        val rowPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 9.5f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
        }

        val titleLayout = buildLayout(
            context.getString(R.string.report_pdf_battery_indicators_title), titlePaint, innerWidth, isRtl
        )
        val socText = batterySocLine(socAtStart, socAtEnd)
        val socLayout = socText?.let { buildLayout(it, socPaint, innerWidth, isRtl) }
        val rowLayouts = indicators.entries.sortedBy { it.key }
            .map { (key, raw) -> buildLayout(indicatorRowText(key, raw), rowPaint, innerWidth, isRtl) }

        var cardHeight = padding + titleLayout.height
        socLayout?.let { cardHeight += 4f + it.height }
        rowLayouts.forEach { cardHeight += 8f + it.height }
        cardHeight += padding

        state.ensureSpace(cardHeight + 10f)
        val canvas = state.canvas
        val top = state.y
        drawRoundedCard(
            canvas,
            RectF(contentLeft, top, contentRight, top + cardHeight),
            fill = Color.parseColor(Palette.AI_BG),
            stroke = Color.parseColor(Palette.AI_BORDER)
        )
        var y = top + padding
        y = drawLayoutAt(canvas, titleLayout, contentLeft + padding, y, isRtl, innerWidth)
        socLayout?.let {
            y += 4f
            y = drawLayoutAt(canvas, it, contentLeft + padding, y, isRtl, innerWidth)
        }
        for (row in rowLayouts) {
            y += 8f
            y = drawLayoutAt(canvas, row, contentLeft + padding, y, isRtl, innerWidth)
        }
        state.y = top + cardHeight + 10f
    }

    private fun batterySocLine(socAtStart: Int?, socAtEnd: Int?): String? = when {
        socAtStart != null && socAtEnd != null ->
            context.getString(R.string.report_pdf_battery_soc_both_fmt, socAtStart, socAtEnd)
        socAtStart != null ->
            context.getString(R.string.report_pdf_battery_soc_start_only_fmt, socAtStart)
        socAtEnd != null ->
            context.getString(R.string.report_pdf_battery_soc_end_only_fmt, socAtEnd)
        else -> null
    }

    private fun indicatorStatusLabel(kind: AiV7Presentation.IndicatorStatusKind, raw: String?): String = when (kind) {
        AiV7Presentation.IndicatorStatusKind.MEASURED -> context.getString(R.string.report_pdf_indicator_status_measured)
        AiV7Presentation.IndicatorStatusKind.ABSTAINED -> context.getString(R.string.report_pdf_indicator_status_abstained)
        AiV7Presentation.IndicatorStatusKind.UNKNOWN -> context.getString(R.string.report_pdf_indicator_status_unknown_fmt, raw ?: "?")
    }

    private fun indicatorVerdictLabel(kind: AiV7Presentation.IndicatorVerdictKind, raw: String?): String = when (kind) {
        AiV7Presentation.IndicatorVerdictKind.THRESHOLD_NOT_CALIBRATED -> context.getString(R.string.report_pdf_indicator_advisory_tag)
        AiV7Presentation.IndicatorVerdictKind.UNKNOWN -> context.getString(R.string.report_pdf_indicator_verdict_unknown_fmt, raw ?: "?")
    }

    /** One "• BAT-XXX — value unit — status — verdict — extra: fields" line for [drawBatteryIndicators]. */
    private fun indicatorRowText(key: String, raw: Map<String, Any?>): String {
        val reading = AiV7Presentation.readIndicator(raw)
        val name = reading.id?.takeIf { it.isNotBlank() } ?: key
        val valueText = reading.value?.let { v ->
            val formatted = NumberFormat.toLatin(AiV7Presentation.formatIndicatorNumber(v))
            if (reading.unit != null) "$formatted ${reading.unit}" else formatted
        }
        val parts = mutableListOf(name)
        if (valueText != null) parts.add(valueText)
        parts.add(indicatorStatusLabel(reading.statusKind, reading.statusRaw))
        parts.add(indicatorVerdictLabel(reading.verdictKind, reading.verdictRaw))
        if (reading.otherFields.isNotEmpty()) {
            parts.add(reading.otherFields.joinToString(" · ") { (k, v) -> "$k: $v" })
        }
        return "•  " + parts.joinToString("  —  ")
    }

    // ───────────────────────── Specs grid (page 2) ─────────────────────────

    private fun drawSpecsGrid(state: PageState, results: List<FunctionalResultEntity>) {
        val specs = collectSpecs(results)
        if (specs.isEmpty()) return

        val isRtl = state.isRtl
        val columnGap = 12f
        val cellWidth = (contentWidth - columnGap) / 2f
        val cellHeight = 50f
        val rowPitch = cellHeight + 10f

        // Reserve title + first row so the section header doesn't get
        // stranded on the previous page.
        state.ensureSpace(22f + rowPitch)

        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
        }
        drawSingleLine(state.canvas, context.getString(R.string.report_pdf_specs_title),
            contentLeft, contentRight, state.y + 12f, titlePaint, isRtl)
        state.y += 22f

        val labelPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 9f
            color = Color.parseColor(Palette.TEXT_SECONDARY)
        }
        val valuePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 16f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
        }

        specs.chunked(2).forEachIndexed { rowIdx, pair ->
            // First row was bundled with the title reservation; subsequent
            // rows check on their own and flip pages when they don't fit.
            if (rowIdx > 0) state.ensureSpace(rowPitch)

            val canvas = state.canvas
            val top = state.y
            pair.forEachIndexed { idx, spec ->
                val col = if (isRtl) (1 - idx) else idx
                val left = contentLeft + col * (cellWidth + columnGap)
                val right = left + cellWidth
                val bottom = top + cellHeight
                drawRoundedCard(
                    canvas,
                    RectF(left, top, right, bottom),
                    fill = Color.parseColor(Palette.CARD_BG),
                    stroke = Color.parseColor(Palette.BORDER),
                    radius = 8f
                )
                val labelLayout = buildLayout(spec.label, labelPaint, (cellWidth - 20).toInt(), isRtl)
                drawLayoutAt(canvas, labelLayout, left + 10f, top + 8f, isRtl, (cellWidth - 20).toInt())
                val valueLayout = buildLayout(spec.value, valuePaint, (cellWidth - 20).toInt(), isRtl)
                drawLayoutAt(canvas, valueLayout, left + 10f, top + 22f, isRtl, (cellWidth - 20).toInt())
            }
            state.y += rowPitch
        }
        state.y += 4f
    }

    private data class Spec(val label: String, val value: String)

    private fun collectSpecs(results: List<FunctionalResultEntity>): List<Spec> {
        val byId = results.associateBy { it.testId }
        val out = mutableListOf<Spec>()
        fun add(id: String, labelRes: Int) {
            val r = byId[id] ?: return
            val raw = r.rawValue?.takeIf { it.isNotBlank() && it != "N/A" } ?: return
            val unit = r.rawValueUnit?.takeIf { it.isNotBlank() && !raw.contains(it) }
            val value = if (unit != null) "$raw $unit" else raw
            out.add(Spec(context.getString(labelRes), value))
        }
        // S-04 = sequential read speed, S-03 = sequential write speed,
        // S-02 = available storage, R-02 = available RAM, G-03 = FPS drop rate.
        // Using S-01/R-01/G-05 here would surface total-capacity / Vulkan flags with
        // mismatched labels (e.g. a storage capacity shown as "read speed").
        add("S-04", R.string.report_pdf_spec_read_speed)
        add("S-03", R.string.report_pdf_spec_write_speed)
        add("T-01", R.string.report_pdf_spec_max_temp)
        add("B-06", R.string.report_pdf_spec_drain_rate)
        add("R-02", R.string.report_pdf_spec_memory)
        add("S-02", R.string.report_pdf_spec_storage)
        add("C-06", R.string.report_pdf_spec_cpu_throttle)
        add("G-03", R.string.report_pdf_spec_gpu_fps)
        return out
    }

    // ───────────────────────── Predicted problems (page 2) ─────────────────────────

    /**
     * Renders the AI's `predicted_problems` list as a single bordered card on
     * page 2 (between the specs grid and the tamper badge). The entries are
     * short sentences the backend returns — one per probable future issue —
     * so a simple bulleted stack inside a single warning-tinted card reads as
     * "here are the things to keep an eye on" without duplicating the richer
     * per-issue cards already rendered on page 1.
     *
     * Returns `startY` unchanged when the list is empty, so the caller can
     * chain it regardless of whether the backend populated this field.
     */
    private fun drawPredictedProblems(state: PageState, problems: List<net.neuralcheck.data.remote.model.PredictedProblem>) {
        // Filter out empty predictions defensively — a malformed backend
        // payload could ship `{prediction: ""}` and we don't want a
        // bullet with no text on the PDF.
        val nonEmpty = problems.filter { it.prediction.isNotBlank() }
        if (nonEmpty.isEmpty()) return

        val isRtl = state.isRtl
        val padding = 10f
        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
        }
        val innerWidth = (contentWidth - padding * 2).toInt()
        val gap = 6f

        // Cap at 6 so we don't blow the page budget before tamper/QR. The
        // truncation is deliberate: the on-screen report shows every entry,
        // the PDF is a one-glance summary and anything beyond six items
        // stops being scannable.
        val capped = nonEmpty.take(6)
        val layouts = capped.map { p ->
            // V2 entries carry a timeframe ("3-6 months") which we
            // render inline as a parenthetical prefix, e.g.
            // "•  (3-6 months) battery degradation". V1 entries
            // (timeframe = null) render the prediction alone exactly as
            // before, preserving PDF layout for legacy reports.
            val text = if (p.timeframe.isNullOrBlank()) {
                "•  ${p.prediction}"
            } else {
                "•  (${p.timeframe}) ${p.prediction}"
            }
            buildLayout(text, bodyPaint, innerWidth, isRtl)
        }
        val heightsSum = layouts.sumOf { it.height }.toFloat()
        val gapsSum = gap * (layouts.size - 1).coerceAtLeast(0)
        val totalHeight = padding * 2 + heightsSum + gapsSum

        // Keep the list + its "Predicted problems" title together. The card
        // is single-bordered — splitting it mid-bullet would leave an
        // incomplete card on one page and an orphan on the next.
        state.ensureSpace(22f + totalHeight + 14f)

        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 14f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
        }
        val canvas = state.canvas
        drawSingleLine(
            canvas,
            context.getString(R.string.report_pdf_predicted_title),
            contentLeft, contentRight, state.y + 12f, titlePaint, isRtl
        )
        state.y += 22f

        val cardTop = state.y
        drawRoundedCard(
            canvas,
            RectF(contentLeft, cardTop, contentRight, cardTop + totalHeight),
            fill = Color.parseColor(Palette.WARNING_BG),
            stroke = Color.parseColor(Palette.WARNING_BORDER)
        )
        var ly = cardTop + padding
        layouts.forEach { layout ->
            drawLayoutAt(canvas, layout, contentLeft + padding, ly, isRtl, innerWidth)
            ly += layout.height + gap
        }
        state.y = cardTop + totalHeight + 14f
    }

    // ───────────────────────── Root cause card ─────────────────────────

    /**
     * Single-paragraph root-cause hypothesis from the backend AI. Rendered
     * as one amber-tinted card with a 🔍 prefix on the title so it stands
     * out from both the green AI summary cards and the red issue cards.
     * Skipped when the field is null/blank or the literal "null" sentinel
     * (Gemini occasionally emits the string when its confidence is low).
     */
    // ── Phase 1.15: buyer note (depth NeuralCheck guarantees vs surface) ──
    private fun drawBuyerNote(state: PageState) {
        val isRtl = state.isRtl
        val padding = 12f
        val innerWidth = (contentWidth - padding * 2).toInt()
        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 13f
            color = Color.parseColor(Palette.BRAND)
        }
        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.parseColor(Palette.TEXT_SECONDARY)
        }
        val titleStr = "🛡  " + context.getString(R.string.report_buyer_note_title)
        val titleLayout = buildLayout(titleStr, titlePaint, innerWidth, isRtl)
        val bodyLayout = buildLayout(context.getString(R.string.report_buyer_note_body), bodyPaint, innerWidth, isRtl)
        val cardHeight = padding + titleLayout.height + 6f + bodyLayout.height + padding
        state.ensureSpace(cardHeight + 14f)
        val canvas = state.canvas
        val cardTop = state.y
        drawRoundedCard(
            canvas,
            RectF(contentLeft, cardTop, contentRight, cardTop + cardHeight),
            fill = Color.parseColor(Palette.CARD_BG),
            stroke = Color.parseColor(Palette.BRAND)
        )
        var ty = cardTop + padding
        ty = drawLayoutAt(canvas, titleLayout, contentLeft + padding, ty, isRtl, innerWidth) + 6f
        drawLayoutAt(canvas, bodyLayout, contentLeft + padding, ty, isRtl, innerWidth)
        state.y = cardTop + cardHeight + 14f
    }

    // ── Phase 1.15: "possibly serviced" flag (manual test fail→pass vs prior scan) ──
    private fun drawMaintenanceFlag(state: PageState, analysis: AiAnalysisResult) {
        val change = analysis.changeAnalysis ?: return
        if (change.maintenanceSuspected != true) return
        val isRtl = state.isRtl
        val padding = 12f
        val innerWidth = (contentWidth - padding * 2).toInt()
        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11.5f
            color = Color.parseColor(Palette.WARNING_ACCENT)
        }
        val text = "🛠  " + (change.maintenanceNote?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.report_maintenance_suspected))
        val bodyLayout = buildLayout(text, bodyPaint, innerWidth, isRtl)
        val cardHeight = padding + bodyLayout.height + padding
        state.ensureSpace(cardHeight + 14f)
        val canvas = state.canvas
        val cardTop = state.y
        drawRoundedCard(
            canvas,
            RectF(contentLeft, cardTop, contentRight, cardTop + cardHeight),
            fill = Color.parseColor(Palette.WARNING_BG),
            stroke = Color.parseColor(Palette.WARNING_BORDER)
        )
        drawLayoutAt(canvas, bodyLayout, contentLeft + padding, cardTop + padding, isRtl, innerWidth)
        state.y = cardTop + cardHeight + 14f
    }

    private fun drawRootCause(state: PageState, rootCause: String?) {
        if (rootCause.isNullOrBlank()) return
        if (rootCause.equals("null", ignoreCase = true)) return

        val isRtl = state.isRtl
        val padding = 12f
        val innerWidth = (contentWidth - padding * 2).toInt()

        val titlePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 13f
            color = Color.parseColor(Palette.ROOT_CAUSE_ACCENT)
        }
        val bodyPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 11f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
        }

        // 🔍 prefix is emoji-rendered straight by Skia on most Android
        // versions; fall back to a plain title if the typeface lacks the
        // glyph (the line still reads correctly in either case).
        val titleStr = "🔍  " + context.getString(R.string.report_pdf_root_cause_title)
        val titleLayout = buildLayout(titleStr, titlePaint, innerWidth, isRtl)
        val bodyLayout = buildLayout(rootCause, bodyPaint, innerWidth, isRtl)

        val cardHeight = padding + titleLayout.height + 6f + bodyLayout.height + padding
        state.ensureSpace(cardHeight + 14f)

        val canvas = state.canvas
        val cardTop = state.y
        drawRoundedCard(
            canvas,
            RectF(contentLeft, cardTop, contentRight, cardTop + cardHeight),
            fill = Color.parseColor(Palette.ROOT_CAUSE_BG),
            stroke = Color.parseColor(Palette.ROOT_CAUSE_BORDER)
        )
        var ty = cardTop + padding
        ty = drawLayoutAt(canvas, titleLayout, contentLeft + padding, ty, isRtl, innerWidth) + 6f
        drawLayoutAt(canvas, bodyLayout, contentLeft + padding, ty, isRtl, innerWidth)
        state.y = cardTop + cardHeight + 14f
    }

    // ───────────────────────── Buyer-advice card ─────────────────────────

    /**
     * Per-persona usage advice. One card with three labelled paragraphs
     * (casual / gamer / photographer); each persona is skipped when the
     * AI didn't ship a useful sentence for it. The whole card is skipped
     * when all three personas are null/blank (typical for very early
     * scans before the backend started returning the field at all).
     */
    private fun drawBuyerAdvice(
        state: PageState,
        advice: net.neuralcheck.data.remote.model.BuyerAdvice?
    ) {
        if (advice == null) return
        val rows = listOfNotNull(
            advice.casual?.takeIf { it.isNotBlank() }?.let {
                Triple("📱", R.string.advice_casual, it)
            },
            advice.gamer?.takeIf { it.isNotBlank() }?.let {
                Triple("🎮", R.string.advice_gamer, it)
            },
            advice.photographer?.takeIf { it.isNotBlank() }?.let {
                Triple("📷", R.string.advice_photographer, it)
            }
        )
        if (rows.isEmpty()) return

        val isRtl = state.isRtl
        val padding = 12f
        val rowSpacing = 8f
        val innerWidth = (contentWidth - padding * 2).toInt()

        val sectionTitlePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 13f
            color = Color.parseColor(Palette.BRAND)
        }
        val rowTitlePaint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
            color = Color.parseColor(Palette.TEXT_PRIMARY)
        }
        val rowBodyPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 10.5f
            color = Color.parseColor(Palette.TEXT_SECONDARY)
        }

        val sectionTitleStr = "💡  " + context.getString(R.string.report_pdf_buyer_advice_title)
        val sectionTitleLayout = buildLayout(sectionTitleStr, sectionTitlePaint, innerWidth, isRtl)

        // Pre-build per-row layouts so we can size the card before drawing.
        // Indented 16f visually anchors the body under its title icon.
        val laidOut = rows.map { (icon, titleResId, text) ->
            val titleStr = "$icon  " + context.getString(titleResId)
            val titleLayout = buildLayout(titleStr, rowTitlePaint, innerWidth, isRtl)
            val bodyLayout = buildLayout(text, rowBodyPaint, innerWidth - 16, isRtl)
            Triple(titleLayout, bodyLayout, titleLayout.height + 4 + bodyLayout.height)
        }

        val rowsTotalHeight = laidOut.sumOf { it.third }.toFloat()
        val gapsTotal = rowSpacing * (laidOut.size - 1).coerceAtLeast(0)
        val cardHeight = padding + sectionTitleLayout.height + 10f +
                rowsTotalHeight + gapsTotal + padding

        state.ensureSpace(cardHeight + 14f)

        val canvas = state.canvas
        val cardTop = state.y
        drawRoundedCard(
            canvas,
            RectF(contentLeft, cardTop, contentRight, cardTop + cardHeight),
            fill = Color.parseColor(Palette.CARD_BG),
            stroke = Color.parseColor(Palette.BORDER)
        )
        var ty = cardTop + padding
        ty = drawLayoutAt(canvas, sectionTitleLayout, contentLeft + padding, ty, isRtl, innerWidth) + 10f
        for ((titleLayout, bodyLayout, _) in laidOut) {
            ty = drawLayoutAt(canvas, titleLayout, contentLeft + padding, ty, isRtl, innerWidth) + 4f
            ty = drawLayoutAt(canvas, bodyLayout, contentLeft + padding + 16f, ty, isRtl, innerWidth - 16) + rowSpacing
        }
        state.y = cardTop + cardHeight + 14f
    }

    // ───────────────────────── Tamper badge ─────────────────────────

    private fun drawTamperBadge(state: PageState, tampered: Boolean) {
        val isRtl = state.isRtl
        val padding = 10f
        val (bg, border, accent, msgRes) = if (tampered) {
            listOf(Palette.DANGER_BG, Palette.DANGER_BORDER, Palette.DANGER_ACCENT, R.string.report_pdf_tamper_detected)
        } else {
            listOf(Palette.TAMPER_CLEAN_BG, Palette.TAMPER_CLEAN_BORDER, Palette.TAMPER_CLEAN_TEXT, R.string.report_pdf_tamper_clean)
        }
        val paint = TextPaint().apply {
            isAntiAlias = true
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 11f
            color = Color.parseColor(accent as String)
        }
        val layout = buildLayout(
            context.getString(msgRes as Int), paint, (contentWidth - padding * 2).toInt(), isRtl
        )
        val cardHeight = padding * 2 + layout.height

        state.ensureSpace(cardHeight + 14f)
        val canvas = state.canvas
        val top = state.y
        drawRoundedCard(
            canvas,
            RectF(contentLeft, top, contentRight, top + cardHeight),
            fill = Color.parseColor(bg as String),
            stroke = Color.parseColor(border as String)
        )
        drawLayoutAt(canvas, layout, contentLeft + padding, top + padding, isRtl, (contentWidth - padding * 2).toInt())
        state.y = top + cardHeight + 14f
    }

    // ───────────────────────── QR block ─────────────────────────

    private fun drawQrBlock(state: PageState, qrBitmap: Bitmap, displayId: String) {
        val isRtl = state.isRtl
        val qrSize = 120
        val pad = 8f
        // White backing + QR + scan-instruction line + identifier line. We
        // bumped the identifier from muted small text to primary-tone bold
        // so a buyer can read the ID off the printed page without zooming
        // in; the block height grows by the extra leading we need.
        val blockHeight = pad + qrSize + pad + 16f + 22f

        state.ensureSpace(blockHeight)
        val canvas = state.canvas
        val top = state.y + pad
        val cx = pageWidth / 2f
        val qrLeft = cx - qrSize / 2f

        // White rounded backing so the black QR modules stay scannable on the
        // dark page background — scanners need strong contrast on both sides.
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(Palette.QR_BG)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(
            RectF(qrLeft - pad, top - pad, qrLeft + qrSize + pad, top + qrSize + pad),
            10f, 10f, bg
        )

        val scaled = if (qrBitmap.width == qrSize) qrBitmap
            else Bitmap.createScaledBitmap(qrBitmap, qrSize, qrSize, true)
        canvas.drawBitmap(scaled, qrLeft, top, null)

        val labelPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 10f
            color = Color.parseColor(Palette.TEXT_SECONDARY)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            bidiSafe(context.getString(R.string.report_pdf_qr_label), isRtl),
            cx, top + qrSize + 16f + pad, labelPaint
        )

        // Identifier line: "رقم الفحص: NC-A7K9M2". Primary-tone + bold so
        // it reads at a glance even when the page is printed and scaled.
        val idPaint = TextPaint().apply {
            isAntiAlias = true
            textSize = 11f
            isFakeBoldText = true
            color = Color.parseColor(Palette.TEXT_PRIMARY)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(
            bidiSafe("${context.getString(R.string.report_pdf_scan_id_label)}: $displayId", isRtl),
            cx, top + qrSize + 34f + pad, idPaint
        )
        state.y = top + qrSize + 34f + pad + 4f
    }

    // ───────────────────────── Low-level drawing helpers ─────────────────────────

    private fun drawRoundedCard(canvas: Canvas, rect: RectF, fill: Int, stroke: Int, radius: Float = 10f) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fill
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(rect, radius, radius, strokePaint)
    }

    private fun buildLayout(text: String, paint: TextPaint, width: Int, isRtl: Boolean): StaticLayout {
        val w = width.coerceAtLeast(16)
        val src = bidiSafe(text, isRtl)
        return StaticLayout.Builder
            .obtain(src, 0, src.length, paint, w)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(if (isRtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
            .setLineSpacing(1f, 1.1f)
            .setIncludePad(false)
            .build()
    }

    /** Draws [layout] at (x, y) and returns the Y below the rendered text. */
    private fun drawLayoutAt(canvas: Canvas, layout: StaticLayout, x: Float, y: Float, @Suppress("UNUSED_PARAMETER") isRtl: Boolean, @Suppress("UNUSED_PARAMETER") width: Int): Float {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
        return y + layout.height
    }

    /** Draws a single line of text anchored at the visual start of the content area. */
    private fun drawSingleLine(canvas: Canvas, text: String, left: Float, right: Float, baselineY: Float, paint: TextPaint, isRtl: Boolean) {
        paint.textAlign = if (isRtl) Paint.Align.RIGHT else Paint.Align.LEFT
        val x = if (isRtl) right else left
        canvas.drawText(bidiSafe(text, isRtl), x, baselineY, paint)
    }

    /**
     * Wraps runs of Latin/ASCII characters with LRM (U+200E) marks so that
     * model names, URLs, digits and units render left-to-right inside an RTL
     * StaticLayout. Without this, "228 MB/s" inside an Arabic sentence gets
     * visually reversed (e.g. "s/MB 228") because the Unicode bidi algorithm
     * treats "/" and space as neutrals that inherit the surrounding RTL run.
     */
    private val latinRun = Regex("[\\x21-\\x7E][\\x20-\\x7E]*")
    private fun bidiSafe(text: String, isRtl: Boolean): String {
        if (!isRtl || text.isEmpty()) return text
        if (!text.any { it.code in 0x20..0x7E }) return text
        return latinRun.replace(text) { "\u200E${it.value}\u200E" }
    }

    // ───────────────────────── Local rule-based analysis (AI fallback) ─────────────────────────

    private fun buildLocalAnalysis(
        scoring: ScoringResult,
        results: List<FunctionalResultEntity>,
        isRtl: Boolean
    ): AiAnalysisResult {
        val issues = buildLocalIssues(scoring, results, isRtl)
        val verdictRes = when (scoring.overallGrade) {
            "A" -> R.string.report_pdf_verdict_generic_a
            "B" -> R.string.report_pdf_verdict_generic_b
            "C" -> R.string.report_pdf_verdict_generic_c
            else -> R.string.report_pdf_verdict_generic_d
        }
        return AiAnalysisResult(
            summary = context.getString(R.string.report_pdf_summary_generic),
            details = context.getString(R.string.report_pdf_details_generic),
            verdict = context.getString(verdictRes),
            issues = issues
        )
    }

    private fun buildLocalIssues(
        scoring: ScoringResult,
        results: List<FunctionalResultEntity>,
        isRtl: Boolean
    ): List<PredictedIssue> {
        val byId = results.associateBy { it.testId }
        val out = mutableListOf<PredictedIssue>()

        byId["C-06"]?.takeIf { it.status == "fail" }?.let { r ->
            val value = r.rawValue?.let { "$it${r.rawValueUnit ?: ""}" } ?: "-"
            out.add(PredictedIssue(
                IssueSeverity.DANGER,
                context.getString(R.string.report_pdf_issue_thermal),
                context.getString(R.string.report_pdf_issue_thermal_desc, value)
            ))
        }
        byId["T-02"]?.takeIf { it.status == "fail" }?.let { r ->
            if (out.none { it.title == context.getString(R.string.report_pdf_issue_thermal) }) {
                val value = r.rawValue?.let { "$it${r.rawValueUnit ?: ""}" } ?: "-"
                out.add(PredictedIssue(
                    IssueSeverity.DANGER,
                    context.getString(R.string.report_pdf_issue_heat_rise),
                    context.getString(R.string.report_pdf_issue_heat_rise_desc, value)
                ))
            }
        }
        byId["B-06"]?.takeIf { it.status == "fail" }?.let { r ->
            val value = r.rawValue?.let { "$it${r.rawValueUnit ?: ""}" } ?: "-"
            out.add(PredictedIssue(
                IssueSeverity.WARNING,
                context.getString(R.string.report_pdf_issue_battery),
                context.getString(R.string.report_pdf_issue_battery_desc, value)
            ))
        }
        byId["SEC-04"]?.takeIf { it.status == "fail" }?.let {
            out.add(PredictedIssue(
                IssueSeverity.INFO,
                context.getString(R.string.report_pdf_issue_root),
                context.getString(R.string.report_pdf_issue_root_desc)
            ))
        }
        byId["S-01"]?.takeIf { it.status == "fail" }?.let { r ->
            val value = r.rawValue?.let { "$it${r.rawValueUnit ?: ""}" } ?: "-"
            out.add(PredictedIssue(
                IssueSeverity.WARNING,
                context.getString(R.string.report_pdf_issue_storage),
                context.getString(R.string.report_pdf_issue_storage_desc, value)
            ))
        }

        // Any category that landed at grade D
        scoring.categoryScores.values
            .filter { it.grade == "D" && it.totalCount > 0 }
            .forEach { cat ->
                val name = localisedCategoryName(cat)
                out.add(PredictedIssue(
                    IssueSeverity.WARNING,
                    context.getString(R.string.report_pdf_issue_category_fail, name),
                    context.getString(R.string.report_pdf_issue_category_fail_desc)
                ))
            }

        // Cap to avoid flooding the first page
        return out.take(4)
    }

}
