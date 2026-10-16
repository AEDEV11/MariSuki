package com.aesprt.marisuki.feature.printing

import android.content.Context
import android.graphics.Paint
import android.text.TextPaint
import android.graphics.Typeface
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.text.TextUtils
import com.aesprt.marisuki.data.local.dao.ProductSummary
import com.aesprt.marisuki.feature.pos.PosViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.FileOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first

object ProductLabelPrinter {
    private val supportedSizesMm = setOf(40 to 30, 50 to 30, 60 to 40)

    suspend fun print(context: Context, product: ProductSummary, copies: Int, widthMm: Int, heightMm: Int) {
        DebugPrintSimulation.beforePrint()
        require(copies in 1..100) { "Choose between 1 and 100 label copies." }
        require((widthMm to heightMm) in supportedSizesMm) { "Choose a supported label size." }
        val profile = PrinterSettingsStore(context).profile.first()
        if (profile.connectionType != PrinterConnectionType.SYSTEM) {
            PrinterTransport.send(context, profile, EscPosCommandBuilder.productLabel(product, widthMm, heightMm, copies))
            return
        }
        val mediaSize = PrintAttributes.MediaSize(
            "marisuki-label-${widthMm}x$heightMm",
            "MariSuki label · ${widthMm} × $heightMm mm",
            millimetersToMils(widthMm),
            millimetersToMils(heightMm),
        )
        val attributes = PrintAttributes.Builder()
            .setMediaSize(mediaSize)
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()
        val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        manager.print(
            "MariSuki-label-${product.name.take(28)}",
            ProductLabelAdapter(context.applicationContext, product, copies, widthMm, heightMm, attributes),
            attributes,
        )
    }

    private fun millimetersToMils(value: Int) = (value * 1000f / 25.4f).roundToInt()
}

private class ProductLabelAdapter(
    private val context: Context,
    private val product: ProductSummary,
    private val copies: Int,
    private val widthMm: Int,
    private val heightMm: Int,
    private val attributes: PrintAttributes,
) : PrintDocumentAdapter() {
    private val barcode = product.barcode ?: error("A barcode is required to print this label.")

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
        callback.onLayoutFinished(
            PrintDocumentInfo.Builder("${product.name.take(32)}-labels.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(copies)
                .build(),
            oldAttributes != newAttributes,
        )
    }

    override fun onWrite(
        pages: Array<PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback,
    ) {
        val document = PrintedPdfDocument(context, attributes)
        try {
            val requestedPages = (0 until copies).filter { pageIndex ->
                pages.isEmpty() || pages.any { pageIndex in it.start..it.end }
            }
            requestedPages.forEach { pageIndex ->
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                    return
                }
                val page = document.startPage(pageIndex + 1)
                drawLabel(page.canvas, document.pageWidth.toFloat(), document.pageHeight.toFloat())
                document.finishPage(page)
            }
            FileOutputStream(destination.fileDescriptor).use(document::writeTo)
            callback.onWriteFinished(pages.ifEmpty { arrayOf(PageRange(0, copies - 1)) })
        } catch (error: Exception) {
            callback.onWriteFailed(error.localizedMessage ?: "Could not prepare product labels for printing.")
        } finally {
            document.close()
        }
    }

    private fun drawLabel(canvas: android.graphics.Canvas, pageWidth: Float, pageHeight: Float) {
        val center = pageWidth / 2f
        val horizontalPadding = 4f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        paint.textSize = 8.5f
        val title = TextUtils.ellipsize(product.name, paint, pageWidth - horizontalPadding * 2, TextUtils.TruncateAt.END).toString()
        canvas.drawText(title, center, 11f, paint)

        paint.textSize = 12f
        canvas.drawText(PosViewModel.formatPeso(product.baseUnitPriceCentavos), center, 26f, paint)

        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        paint.textSize = 7.5f
        canvas.drawText("per ${product.baseUnitLabel}", center, 36f, paint)

        val matrix = MultiFormatWriter().encode(barcode, BarcodeFormat.CODE_128, 1, 1)
        val barcodeTop = 41f
        val barcodeHeight = (pageHeight - barcodeTop - 18f).coerceAtLeast(12f)
        val moduleWidth = ((pageWidth - horizontalPadding * 2) / matrix.width).coerceAtMost(0.72f)
        val barcodeWidth = matrix.width * moduleWidth
        val barcodeLeft = center - barcodeWidth / 2f
        paint.style = Paint.Style.FILL
        var runStart = -1
        for (x in 0..matrix.width) {
            val black = x < matrix.width && matrix.get(x, 0)
            if (black && runStart < 0) runStart = x
            if (!black && runStart >= 0) {
                canvas.drawRect(
                    barcodeLeft + runStart * moduleWidth,
                    barcodeTop,
                    barcodeLeft + x * moduleWidth,
                    barcodeTop + barcodeHeight,
                    paint,
                )
                runStart = -1
            }
        }

        paint.textSize = 7f
        canvas.drawText(barcode, center, pageHeight - 5f, paint)
    }
}
