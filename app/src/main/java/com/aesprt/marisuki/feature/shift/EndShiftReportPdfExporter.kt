package com.aesprt.marisuki.feature.shift

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.aesprt.marisuki.domain.model.ReconciliationStatus
import com.aesprt.marisuki.domain.model.ShiftReportStatus
import com.aesprt.marisuki.domain.model.ShiftSummary
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExportedShiftReportPdf(
    val fileName: String,
    val fileUri: String,
    val reportStatus: ShiftReportStatus,
)

class EndShiftReportPdfExporter(
    private val context: Context,
) {
    suspend fun generateAndSave(
        summary: ShiftSummary,
        storeName: String,
        storeAddress: String?,
        isRegeneration: Boolean = false,
        generatedAtMillis: Long = System.currentTimeMillis(),
    ): ExportedShiftReportPdf = withContext(Dispatchers.IO) {
        val fileName = buildDeterministicFileName(summary)
        val pdfBytes = renderPdfBytes(
            summary = summary,
            storeName = storeName,
            storeAddress = storeAddress,
            generatedAtMillis = generatedAtMillis,
        )

        val internalBackup = saveInternalCopy(fileName, pdfBytes)
        val publicUri = runCatching {
            saveToPublicDocuments(fileName, pdfBytes)
        }.getOrNull()

        val resolvedUri = publicUri?.toString()
            ?: FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                internalBackup,
            ).toString()

        ExportedShiftReportPdf(
            fileName = fileName,
            fileUri = resolvedUri,
            reportStatus = if (isRegeneration) ShiftReportStatus.REGENERATED else ShiftReportStatus.GENERATED,
        )
    }

    fun isReportUriAccessible(summary: ShiftSummary): Boolean {
        val uriString = summary.reportFileUri ?: return false
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        }.getOrDefault(false)
    }

    fun buildOpenIntent(summary: ShiftSummary): Intent? {
        val uri = resolveReadableUri(summary) ?: return null
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_PDF)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildShareIntent(summary: ShiftSummary): Intent? {
        val uri = resolveReadableUri(summary) ?: return null
        val share = Intent(Intent.ACTION_SEND).apply {
            type = MIME_PDF
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "MariSuki End Shift Report — ${summary.businessDate} (${summary.shiftId})",
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(share, "Share End Shift Report PDF")
    }

    private fun resolveReadableUri(summary: ShiftSummary): Uri? {
        val savedUri = summary.reportFileUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (savedUri != null) {
            val readable = runCatching {
                context.contentResolver.openInputStream(savedUri)?.use { true } ?: false
            }.getOrDefault(false)
            if (readable) return savedUri
        }
        val fileName = summary.reportFileName ?: buildDeterministicFileName(summary)
        val internalFile = File(File(context.filesDir, SUBFOLDER_NAME), fileName)
        if (internalFile.exists() && internalFile.length() > 0L) {
            return runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    internalFile,
                )
            }.getOrNull()
        }
        return null
    }

    private fun saveInternalCopy(fileName: String, pdfBytes: ByteArray): File {
        val dir = File(context.filesDir, SUBFOLDER_NAME)
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { out ->
            out.write(pdfBytes)
            out.flush()
        }
        return file
    }

    private fun saveToPublicDocuments(fileName: String, pdfBytes: ByteArray): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$SUBFOLDER_NAME/"

            // Remove any prior entry with the same deterministic name so regeneration is clean
            runCatching {
                resolver.delete(
                    collection,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
                    arrayOf(fileName, relativePath),
                )
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_PDF)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values) ?: return null
            try {
                val out: OutputStream = resolver.openOutputStream(uri)
                    ?: throw IllegalStateException("Unable to open MediaStore output stream for $uri")
                out.use { stream ->
                    stream.write(pdfBytes)
                    stream.flush()
                }
                val finalizeValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(uri, finalizeValues, null, null)
                return uri
            } catch (error: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
        } else {
            @Suppress("DEPRECATION")
            val publicDocs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val targetDir = File(publicDocs, SUBFOLDER_NAME)
            if (targetDir.exists() || targetDir.mkdirs()) {
                val targetFile = File(targetDir, fileName)
                FileOutputStream(targetFile).use { out ->
                    out.write(pdfBytes)
                    out.flush()
                }
                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    targetFile,
                )
            }
            val appExternalDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            if (appExternalDocs != null) {
                val fallbackDir = File(appExternalDocs, SUBFOLDER_NAME)
                if (fallbackDir.exists() || fallbackDir.mkdirs()) {
                    val fallbackFile = File(fallbackDir, fileName)
                    FileOutputStream(fallbackFile).use { out ->
                        out.write(pdfBytes)
                        out.flush()
                    }
                    return FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        fallbackFile,
                    )
                }
            }
            return null
        }
    }

    companion object {
        const val SUBFOLDER_NAME = "EndShiftReport"
        const val PUBLIC_RELATIVE_PATH = "Documents/EndShiftReport/"
        private const val MIME_PDF = "application/pdf"
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN = 36f

        fun buildDeterministicFileName(summary: ShiftSummary): String {
            val safeDate = summary.businessDate.replace(Regex("[^0-9-]"), "").ifBlank { "date" }
            val safeCashier = formatCashierCode(summary.cashierId)
                .uppercase(Locale.ROOT)
                .replace(Regex("[^A-Z0-9]"), "")
                .take(12)
                .ifBlank { "CASHIER" }
            val safeShift = summary.shiftId
                .uppercase(Locale.ROOT)
                .replace(Regex("[^A-Z0-9]"), "")
                .takeLast(12)
                .ifBlank { "SHIFT" }
            return "MariSuki_EndShift_${safeDate}_${safeCashier}_${safeShift}.pdf"
        }

        fun renderPdfBytes(
            summary: ShiftSummary,
            storeName: String,
            storeAddress: String?,
            generatedAtMillis: Long = System.currentTimeMillis(),
        ): ByteArray {
            val document = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
                val page = document.startPage(pageInfo)
                drawReportPage(
                    canvas = page.canvas,
                    summary = summary,
                    storeName = storeName,
                    storeAddress = storeAddress,
                    generatedAtMillis = generatedAtMillis,
                    pageNumber = 1,
                    totalPages = 1,
                )
                document.finishPage(page)
                val out = ByteArrayOutputStream()
                document.writeTo(out)
                return out.toByteArray()
            } finally {
                document.close()
            }
        }

        private fun drawReportPage(
            canvas: Canvas,
            summary: ShiftSummary,
            storeName: String,
            storeAddress: String?,
            generatedAtMillis: Long,
            pageNumber: Int,
            totalPages: Int,
        ) {
            val zone = ZoneId.systemDefault()
            val dateTimeFmt = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US)
            val contentLeft = MARGIN
            val contentRight = PAGE_WIDTH - MARGIN
            val contentWidth = contentRight - contentLeft

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 15f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val storePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#2563EB")
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 10.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#475569")
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#0F172A")
                textSize = 9.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val rightValuePaint = Paint(valuePaint).apply {
                textAlign = Paint.Align.RIGHT
            }
            val mutedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#64748B")
                textSize = 8.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#E2E8F0")
                style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            val headerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#F8FAFC")
                style = Paint.Style.FILL
            }

            var y = MARGIN

            // Header Box
            val headerRect = RectF(contentLeft, y, contentRight, y + 66f)
            canvas.drawRoundRect(headerRect, 8f, 8f, headerFillPaint)
            canvas.drawRoundRect(headerRect, 8f, 8f, borderPaint)

            canvas.drawText("MARISUKI POS — END SHIFT REPORT", contentLeft + 14f, y + 22f, titlePaint)
            canvas.drawText(storeName.ifBlank { "MariSuki POS" }, contentLeft + 14f, y + 39f, storePaint)
            val addressLine = storeAddress?.takeIf { it.isNotBlank() } ?: "Main Store Terminal"
            canvas.drawText(
                "$addressLine  •  Terminal: ${summary.terminalId}",
                contentLeft + 14f,
                y + 54f,
                labelPaint,
            )

            val reportId = summary.reportId ?: "RPT-${summary.shiftId.removePrefix("SHF-")}"
            val genFormatted = Instant.ofEpochMilli(generatedAtMillis).atZone(zone).format(dateTimeFmt)
            canvas.drawText("Report ID: $reportId", contentRight - 14f, y + 22f, rightValuePaint)
            canvas.drawText("Business Date: ${summary.businessDate}", contentRight - 14f, y + 39f, rightValuePaint)
            val rightMuted = Paint(mutedPaint).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("Generated: $genFormatted", contentRight - 14f, y + 54f, rightMuted)

            y += 80f

            // Section 1: Cashier & Shift Information (2-column metadata grid)
            canvas.drawText("1. CASHIER & SHIFT INFORMATION", contentLeft, y, sectionPaint)
            y += 8f
            val infoBoxTop = y
            val infoBoxHeight = 76f
            val infoRect = RectF(contentLeft, infoBoxTop, contentRight, infoBoxTop + infoBoxHeight)
            canvas.drawRoundRect(infoRect, 6f, 6f, borderPaint)

            val colMid = contentLeft + contentWidth / 2f
            val startFormatted = Instant.ofEpochMilli(summary.startedAt).atZone(zone).format(dateTimeFmt)
            val endMillis = summary.endedAt ?: generatedAtMillis
            val endFormatted = Instant.ofEpochMilli(endMillis).atZone(zone).format(dateTimeFmt)
            val durationMinutes = Duration.ofMillis((endMillis - summary.startedAt).coerceAtLeast(0L)).toMinutes()
            val durationFormatted = "${durationMinutes / 60}h ${durationMinutes % 60}m"

            var rowY = infoBoxTop + 18f
            canvas.drawText("Cashier Name:", contentLeft + 12f, rowY, labelPaint)
            canvas.drawText(summary.cashierName, contentLeft + 98f, rowY, valuePaint)
            canvas.drawText("Shift Start:", colMid + 12f, rowY, labelPaint)
            canvas.drawText(startFormatted, colMid + 90f, rowY, valuePaint)

            rowY += 18f
            canvas.drawText("Cashier ID:", contentLeft + 12f, rowY, labelPaint)
            canvas.drawText(formatCashierCode(summary.cashierId), contentLeft + 98f, rowY, valuePaint)
            canvas.drawText("Shift End:", colMid + 12f, rowY, labelPaint)
            canvas.drawText(endFormatted, colMid + 90f, rowY, valuePaint)

            rowY += 18f
            canvas.drawText("Shift ID:", contentLeft + 12f, rowY, labelPaint)
            canvas.drawText(summary.shiftId, contentLeft + 98f, rowY, valuePaint)
            canvas.drawText("Duration / Status:", colMid + 12f, rowY, labelPaint)
            canvas.drawText("$durationFormatted (${summary.shiftStatus.name})", colMid + 90f, rowY, valuePaint)

            y = infoBoxTop + infoBoxHeight + 18f

            // Section 2 & 3 Side-by-side or Stacked Tables
            // Section 2: Sales Summary
            canvas.drawText("2. SALES SUMMARY", contentLeft, y, sectionPaint)
            y += 8f

            val salesRows = listOf(
                "Completed Transactions" to summary.transactionCount.toString(),
                "Gross Sales" to formatPhp(summary.grossSalesCentavos),
                "Discounts" to formatPhp(summary.discountsCentavos),
                "Refunds & Returns" to formatPhp(summary.refundsCentavos),
                "Net Sales" to formatPhp(summary.netSalesCentavos),
                "Cash Sales (Applied)" to formatPhp(summary.cashPaymentsCentavos),
                "E-Wallet — GCash" to formatPhp(summary.gcashPaymentsCentavos),
                "E-Wallet — Maya" to formatPhp(summary.mayaPaymentsCentavos),
                "Card / Other Non-Cash" to formatPhp(summary.cardPaymentsCentavos),
                "Credit / Utang Sales" to formatPhp(summary.creditSalesCentavos),
            )
            y = drawKeyValueTable(
                canvas = canvas,
                left = contentLeft,
                top = y,
                width = contentWidth,
                rows = salesRows,
                highlightIndices = setOf(4),
                labelPaint = labelPaint,
                valuePaint = rightValuePaint,
                borderPaint = borderPaint,
                headerFillPaint = headerFillPaint,
            )

            y += 18f

            // Section 3: Cash Drawer Reconciliation
            canvas.drawText("3. CASH DRAWER RECONCILIATION", contentLeft, y, sectionPaint)
            y += 8f

            val varianceCentavos = summary.varianceCentavos ?: 0L
            val actualCentavos = summary.actualCashCentavos ?: summary.expectedCashCentavos
            val varianceSign = when {
                varianceCentavos > 0L -> "+${formatPhp(varianceCentavos)}"
                varianceCentavos < 0L -> "-${formatPhp(kotlin.math.abs(varianceCentavos))}"
                else -> formatPhp(0L)
            }
            val reconRows = listOf(
                "Opening Cash Balance" to formatPhp(summary.openingCashCentavos),
                "Add: Net Cash Payments Received" to formatPhp(summary.cashPaymentsCentavos),
                "Add: Cash In Drawer Movements" to formatPhp(summary.cashInCentavos),
                "Less: Cash Out Drawer Movements" to formatPhp(summary.cashOutCentavos),
                "Less: Cash Refunds" to formatPhp(summary.cashRefundsCentavos),
                "Drawer Adjustments" to formatPhp(summary.adjustmentsCentavos),
                "Expected Cash in Drawer" to formatPhp(summary.expectedCashCentavos),
                "Actual Cash Counted" to formatPhp(actualCentavos),
                "Cash Variance (Short / Over)" to varianceSign,
                "Reconciliation Status" to summary.reconciliationStatus.name,
            )
            y = drawKeyValueTable(
                canvas = canvas,
                left = contentLeft,
                top = y,
                width = contentWidth,
                rows = reconRows,
                highlightIndices = setOf(6, 7, 8, 9),
                labelPaint = labelPaint,
                valuePaint = rightValuePaint,
                borderPaint = borderPaint,
                headerFillPaint = headerFillPaint,
                statusBadge = summary.reconciliationStatus,
            )

            y += 18f

            // Section 4: Remarks, Approvals & Drawer Movements
            canvas.drawText("4. REMARKS, APPROVALS & DRAWER LOG", contentLeft, y, sectionPaint)
            y += 8f
            val notesHeight = 84f
            val notesRect = RectF(contentLeft, y, contentRight, y + notesHeight)
            canvas.drawRoundRect(notesRect, 6f, 6f, borderPaint)

            val remarksText = summary.closingRemarks?.takeIf { it.isNotBlank() } ?: "None recorded"
            val approvalText = summary.approvedBy?.takeIf { it.isNotBlank() } ?: "Not required / Standard closure"
            val movementSummary = if (summary.movements.isEmpty()) {
                "No manual cash-in / cash-out drawer movements recorded during this shift."
            } else {
                summary.movements.take(3).joinToString("  |  ") { m ->
                    "${m.movementType.name}: ${formatPhp(m.amountCentavos)} (${m.reason.take(24)})"
                }
            }

            canvas.drawText("Cashier Explanation / Remarks:", contentLeft + 12f, y + 20f, labelPaint)
            canvas.drawText(remarksText.take(85), contentLeft + 165f, y + 20f, valuePaint)

            canvas.drawText("Manager / Owner Approval:", contentLeft + 12f, y + 40f, labelPaint)
            canvas.drawText(approvalText, contentLeft + 165f, y + 40f, valuePaint)

            canvas.drawText("Drawer Movement Log:", contentLeft + 12f, y + 60f, labelPaint)
            canvas.drawText(movementSummary.take(88), contentLeft + 165f, y + 60f, mutedPaint)

            // Footer
            val footerY = PAGE_HEIGHT - 24f
            canvas.drawLine(contentLeft, footerY - 12f, contentRight, footerY - 12f, borderPaint)
            canvas.drawText(
                "MariSuki POS — Official Shift Reconciliation Record • Saved to $PUBLIC_RELATIVE_PATH",
                contentLeft,
                footerY,
                mutedPaint,
            )
            canvas.drawText(
                "Page $pageNumber of $totalPages",
                contentRight,
                footerY,
                rightMuted,
            )
        }

        private fun drawKeyValueTable(
            canvas: Canvas,
            left: Float,
            top: Float,
            width: Float,
            rows: List<Pair<String, String>>,
            highlightIndices: Set<Int>,
            labelPaint: Paint,
            valuePaint: Paint,
            borderPaint: Paint,
            headerFillPaint: Paint,
            statusBadge: ReconciliationStatus? = null,
        ): Float {
            val rowHeight = 19.5f
            val totalHeight = rows.size * rowHeight
            val tableRect = RectF(left, top, left + width, top + totalHeight)
            canvas.drawRoundRect(tableRect, 6f, 6f, borderPaint)

            rows.forEachIndexed { index, (label, value) ->
                val rowTop = top + index * rowHeight
                val rowBottom = rowTop + rowHeight
                if (index in highlightIndices) {
                    canvas.drawRect(left + 1f, rowTop + 1f, left + width - 1f, rowBottom - 1f, headerFillPaint)
                }
                if (index > 0) {
                    canvas.drawLine(left, rowTop, left + width, rowTop, borderPaint)
                }
                val rowLabelPaint = if (index in highlightIndices) {
                    Paint(labelPaint).apply {
                        color = Color.parseColor("#0F172A")
                        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    }
                } else {
                    labelPaint
                }
                val rowValuePaint = Paint(valuePaint).apply {
                    if (statusBadge != null && index >= rows.size - 2) {
                        color = when (statusBadge) {
                            ReconciliationStatus.BALANCED -> Color.parseColor("#16A34A")
                            ReconciliationStatus.SHORT -> Color.parseColor("#DC2626")
                            ReconciliationStatus.OVER -> Color.parseColor("#D97706")
                            ReconciliationStatus.PENDING -> Color.parseColor("#475569")
                        }
                    }
                }
                val baseline = rowTop + 13.5f
                canvas.drawText(label, left + 12f, baseline, rowLabelPaint)
                canvas.drawText(value, left + width - 12f, baseline, rowValuePaint)
            }
            return top + totalHeight
        }

        fun formatPhp(centavos: Long): String {
            val sign = if (centavos < 0L) "-" else ""
            val absCentavos = kotlin.math.abs(centavos)
            val pesos = BigDecimal.valueOf(absCentavos, 2)
            val symbols = DecimalFormatSymbols(Locale.US)
            val formatter = DecimalFormat("#,##0.00", symbols)
            return "${sign}₱${formatter.format(pesos)}"
        }
    }
}

