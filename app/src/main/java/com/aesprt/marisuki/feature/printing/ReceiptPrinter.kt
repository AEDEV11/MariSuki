package com.aesprt.marisuki.feature.printing

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.PageRange
import android.print.pdf.PrintedPdfDocument
import com.aesprt.marisuki.data.SaleReceiptData
import com.aesprt.marisuki.feature.pos.PosViewModel
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlinx.coroutines.flow.first

object ReceiptPrinter {
    suspend fun print(context: Context, receipt: SaleReceiptData) {
        DebugPrintSimulation.beforePrint()
        val profile = PrinterSettingsStore(context).profile.first()
        if (profile.connectionType != PrinterConnectionType.SYSTEM) {
            PrinterTransport.send(context, profile, EscPosCommandBuilder.receipt(receipt, profile.receiptWidthMm))
            return
        }
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(
            "MariSuki-${receipt.sale.id.take(8)}",
            ReceiptAdapter(context.applicationContext, receipt),
            null,
        )
    }
}

private class ReceiptAdapter(
    private val context: Context,
    receipt: SaleReceiptData,
) : PrintDocumentAdapter() {
    private val lines = buildLines(receipt)
    private var pageCount = 1
    private var charsPerLine = 30
    private var printAttributes: PrintAttributes = PrintAttributes.Builder().build()

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: android.os.Bundle?,
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        printAttributes = newAttributes
        val media = newAttributes.mediaSize
        val widthPoints = (media?.widthMils ?: 8500) / 1000f * 72f
        val heightPoints = (media?.heightMils ?: 11000) / 1000f * 72f
        charsPerLine = ((widthPoints - 28f) / 5.4f).toInt().coerceAtLeast(18)
        val wrappedLines = lines.flatMap(::wrapLine)
        val linesPerPage = ((heightPoints - 32f) / 13f).toInt().coerceAtLeast(1)
        pageCount = ceil(wrappedLines.size.toDouble() / linesPerPage).toInt().coerceAtLeast(1)
        val info = PrintDocumentInfo.Builder("${receiptLabel()}-receipt.pdf")
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(pageCount)
            .build()
        callback.onLayoutFinished(info, oldAttributes != newAttributes)
    }

    override fun onWrite(
        pages: Array<PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        try {
            val document = PrintedPdfDocument(context, printAttributes)
            try {
                val pageHeight = document.pageHeight
                val margin = 16f
                val textSize = 9f
                val lineHeight = 13f
                val wrapped = lines.flatMap(::wrapLine)
                val linesPerPage = ((pageHeight - margin * 2) / lineHeight).toInt().coerceAtLeast(1)
                val outputPages = (0 until pageCount).filter { page ->
                    pages.isEmpty() || pages.any { page >= it.start && page <= it.end }
                }
                outputPages.forEach { pageIndex ->
                    if (cancellationSignal.isCanceled) {
                        callback.onWriteCancelled()
                        return
                    }
                    val page = document.startPage(pageIndex + 1)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.BLACK
                        this.textSize = textSize
                        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                    }
                    var y = margin + textSize
                    val start = pageIndex * linesPerPage
                    val end = minOf(start + linesPerPage, wrapped.size)
                    for (index in start until end) {
                        val line = wrapped[index]
                        if (index == 0) {
                            paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
                            paint.textSize = 12f
                        } else {
                            paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
                            paint.textSize = textSize
                        }
                        page.canvas.drawText(line, margin, y, paint)
                        y += if (index == 0) 16f else lineHeight
                    }
                    document.finishPage(page)
                }
                FileOutputStream(destination.fileDescriptor).use(document::writeTo)
            } finally {
                document.close()
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (error: Exception) {
            callback.onWriteFailed(error.localizedMessage ?: "Could not prepare this receipt for printing.")
        }
    }

    private fun wrapLine(value: String): List<String> {
        if (value.length <= charsPerLine) return listOf(value)
        val result = mutableListOf<String>()
        var current = StringBuilder()
        value.split(Regex("\\s+")).forEach { word ->
            if (current.isNotEmpty() && current.length + 1 + word.length > charsPerLine) {
                result += current.toString()
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) result += current.toString()
        return result.ifEmpty { listOf(value.take(charsPerLine)) }
    }

    private fun receiptLabel() = "MariSuki"

    private fun buildLines(receipt: SaleReceiptData): List<String> = buildList {
        add("MariSuki · Tindahan POS")
        add("SALE ${receipt.sale.id.take(8).uppercase()}")
        add(Instant.ofEpochMilli(receipt.sale.createdAtMillis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.forLanguageTag("en-PH"))))
        add("Type: ${receipt.sale.salesChannel}")
        receipt.customerName?.let { add("Customer: $it") }
        add("--------------------------------")
        receipt.items.forEach { item ->
            add(item.productNameSnapshot)
            val quantity = PosViewModel.formatQuantity(item.quantityMilliUnits)
            add("$quantity ${item.unitLabelSnapshot} x ${PosViewModel.formatPeso(item.unitPriceCentavos)}")
            add("  ${PosViewModel.formatPeso(item.lineTotalCentavos)}")
        }
        add("--------------------------------")
        add("TOTAL  ${PosViewModel.formatPeso(receipt.sale.totalCentavos)}")
        receipt.payments.forEach { payment ->
            add("${payment.method}  ${PosViewModel.formatPeso(payment.amountAppliedCentavos)}")
            if (payment.changeCentavos > 0) add("Change  ${PosViewModel.formatPeso(payment.changeCentavos)}")
        }
        val applied = receipt.payments.sumOf { it.amountAppliedCentavos }
        if (receipt.sale.totalCentavos > applied) add("Utang  ${PosViewModel.formatPeso(receipt.sale.totalCentavos - applied)}")
        add("Thank you! Salamat po.")
    }
}
