package com.aesprt.marisuki.feature.printing

import com.aesprt.marisuki.data.SaleReceiptData
import com.aesprt.marisuki.data.local.dao.ProductSummary
import com.aesprt.marisuki.feature.pos.PosViewModel
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Commands for common ESC/POS thermal printers. Non-ASCII text is transliterated for legacy code pages. */
object EscPosCommandBuilder {
    fun receipt(receipt: SaleReceiptData, widthMm: Int): ByteArray {
        require(widthMm == 58 || widthMm == 80)
        val columns = if (widthMm == 58) 32 else 48
        val output = ByteArrayOutputStream()
        output.command(0x1b, 0x40) // initialize
        output.command(0x1b, 0x61, 1)
        output.command(0x1b, 0x45, 1)
        output.line("MariSuki Grocery")
        output.command(0x1b, 0x45, 0)
        output.line("SALE ${receipt.sale.id.take(8).uppercase(Locale.ROOT)}")
        output.line(Instant.ofEpochMilli(receipt.sale.createdAtMillis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US)))
        output.command(0x1b, 0x61, 0)
        receipt.customerName?.let { output.wrap("Customer: $it", columns) }
        output.wrap("Type: ${receipt.sale.salesChannel}", columns)
        output.rule(columns)
        receipt.items.forEach { item ->
            output.wrap(item.productNameSnapshot, columns)
            val quantity = PosViewModel.formatQuantity(item.quantityMilliUnits)
            output.twoColumns("$quantity ${item.unitLabelSnapshot} x ${php(item.unitPriceCentavos)}", php(item.lineTotalCentavos), columns)
        }
        output.rule(columns)
        output.command(0x1b, 0x45, 1)
        output.twoColumns("TOTAL", php(receipt.sale.totalCentavos), columns)
        output.command(0x1b, 0x45, 0)
        receipt.payments.forEach { payment ->
            output.twoColumns(payment.method, php(payment.amountAppliedCentavos), columns)
            payment.referenceNumber?.let { output.wrap("Ref no. $it", columns) }
            if (payment.changeCentavos > 0) output.twoColumns("Change", php(payment.changeCentavos), columns)
        }
        val paid = receipt.payments.sumOf { it.amountAppliedCentavos }
        if (receipt.sale.totalCentavos > paid) output.twoColumns("Utang", php(receipt.sale.totalCentavos - paid), columns)
        output.command(0x1b, 0x61, 1)
        output.line("Thank you! Salamat po.")
        output.command(0x1b, 0x64, 3) // feed
        output.command(0x1d, 0x56, 0) // full cut when supported
        return output.toByteArray()
    }

    fun productLabel(product: ProductSummary, widthMm: Int, heightMm: Int, copies: Int): ByteArray {
        require((widthMm to heightMm) in setOf(40 to 30, 50 to 30, 60 to 40))
        require(copies in 1..100)
        val barcode = product.barcode?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("A barcode is required to print this label.")
        val encodedBarcode = "{B${ascii(barcode)}".toByteArray(Charsets.US_ASCII)
        require(encodedBarcode.size <= 255) { "The barcode is too long for this ESC/POS printer." }
        val output = ByteArrayOutputStream()
        repeat(copies) {
            output.command(0x1b, 0x40)
            output.command(0x1b, 0x61, 1)
            output.command(0x1b, 0x45, 1)
            output.line(product.name)
            output.command(0x1b, 0x45, 0)
            output.line(php(product.baseUnitPriceCentavos))
            output.line("per ${product.baseUnitLabel}")
            output.command(0x1d, 0x48, 2) // HRI below barcode
            output.command(0x1d, 0x68, 72) // barcode height in dots
            output.command(0x1d, 0x77, if (widthMm <= 40) 2 else 3) // module width
            output.command(0x1d, 0x6b, 73, encodedBarcode.size)
            output.write(encodedBarcode)
            output.write('\n'.code)
            // ESC J feeds in printer dots; 6 dots/mm stays within its one-byte range.
            output.command(0x1b, 0x4a, (heightMm * 6).coerceIn(1, 255))
        }
        return output.toByteArray()
    }

    fun testReceipt(widthMm: Int): ByteArray {
        require(widthMm == 58 || widthMm == 80)
        val columns = if (widthMm == 58) 32 else 48
        return ByteArrayOutputStream().apply {
            command(0x1b, 0x40)
            command(0x1b, 0x61, 1)
            command(0x1b, 0x45, 1)
            line("MariSuki Printer Test")
            command(0x1b, 0x45, 0)
            command(0x1b, 0x61, 0)
            rule(columns)
            twoColumns("Connection", "OK", columns)
            line("ESC/POS test page")
            command(0x1b, 0x64, 3)
            command(0x1d, 0x56, 0)
        }.toByteArray()
    }

    fun testLabel(widthMm: Int, heightMm: Int): ByteArray {
        val testProduct = ProductSummary(
            id = "printer-test",
            name = "MariSuki Test Label",
            barcode = "2000000000017",
            categoryName = "Test",
            baseUnitLabel = "piece",
            baseUnitPriceCentavos = 1_000,
            purchaseCostCentavos = 0,
            minimumStockMilliUnits = 0,
            wholeBaseUnitsOnly = false,
            onHandMilliUnits = 0,
            favorite = false,
            totalBaseSoldMilliUnits = 0,
        )
        return productLabel(testProduct, widthMm, heightMm, 1)
    }

    private fun php(centavos: Long) = "PHP ${PosViewModel.formatPeso(centavos).replace("₱", "").trim()}"

    private fun ByteArrayOutputStream.wrap(value: String, columns: Int) {
        val normalized = ascii(value)
        var line = StringBuilder()
        normalized.split(Regex("\\s+")).forEach { word ->
            if (line.isNotEmpty() && line.length + 1 + word.length > columns) {
                line(line.toString())
                line = StringBuilder()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(word)
        }
        if (line.isNotEmpty()) line(line.toString())
    }

    private fun ByteArrayOutputStream.twoColumns(left: String, right: String, columns: Int) {
        val l = ascii(left)
        val r = ascii(right)
        if (l.length + r.length + 1 > columns) {
            wrap(l, columns)
            wrap(r, columns)
        } else {
            line(l + " ".repeat(columns - l.length - r.length) + r)
        }
    }

    private fun ByteArrayOutputStream.rule(columns: Int) = line("-".repeat(columns))
    private fun ByteArrayOutputStream.line(value: String) {
        write(ascii(value).toByteArray(Charsets.US_ASCII))
        write('\n'.code)
    }

    private fun ByteArrayOutputStream.command(vararg values: Int) = values.forEach { write(it and 0xff) }

    private fun ascii(value: String): String = Normalizer.normalize(value.replace("₱", "PHP "), Normalizer.Form.NFD)
        .filter { Character.getType(it) != Character.NON_SPACING_MARK.toInt() && it.code in 0x20..0x7e }
}
