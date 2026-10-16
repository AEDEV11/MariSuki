package com.aesprt.marisuki.feature.printing

import com.aesprt.marisuki.data.SaleReceiptData
import com.aesprt.marisuki.data.local.dao.ProductSummary
import com.aesprt.marisuki.data.local.entity.PaymentEntity
import com.aesprt.marisuki.data.local.entity.SaleEntity
import com.aesprt.marisuki.data.local.entity.SaleItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosCommandBuilderTest {
    @Test
    fun receiptIncludesSaleTypeItemsPaymentAndPrinterInitialization() {
        val bytes = EscPosCommandBuilder.receipt(sampleReceipt(), 58)
        val content = bytes.toString(Charsets.ISO_8859_1)

        assertTrue(bytes.take(2) == listOf(0x1b.toByte(), 0x40.toByte()))
        assertTrue(content.contains("Type: WHOLESALE"))
        assertTrue(content.contains("Sardines"))
        assertTrue(content.contains("CASH"))
        assertTrue(content.contains("TOTAL"))
        assertTrue(content.contains("Thank you! Salamat po."))
        assertTrue(bytes.takeLast(3) == listOf(0x1d.toByte(), 0x56.toByte(), 0x00.toByte()))
    }

    @Test
    fun productLabelContainsCode128CommandAndRepeatsRequestedNumberOfTimes() {
        val product = ProductSummary(
            id = "product-1",
            name = "Coffee",
            barcode = "2000000000017",
            categoryName = "Drinks",
            baseUnitLabel = "piece",
            baseUnitPriceCentavos = 2_500,
            purchaseCostCentavos = 1_800,
            minimumStockMilliUnits = 0,
            wholeBaseUnitsOnly = true,
            onHandMilliUnits = 10_000,
            favorite = false,
            totalBaseSoldMilliUnits = 0,
        )
        val bytes = EscPosCommandBuilder.productLabel(product, 40, 30, 3)
        val content = bytes.toString(Charsets.ISO_8859_1)

        assertEquals(3, content.windowed("{B2000000000017".length).count { it == "{B2000000000017" })
        assertEquals(3, content.windowed("\u001bJ".length).count { it == "\u001bJ" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun receiptWidthRejectsUnsupportedMedia() {
        EscPosCommandBuilder.testReceipt(76)
    }

    private fun sampleReceipt() = SaleReceiptData(
        sale = SaleEntity("sale-12345678", "idempotency", 1_800_000_000_000L, 2_500, null, "COMPLETED", false, null, "WHOLESALE"),
        items = listOf(SaleItemEntity("line-1", "sale-12345678", "product-1", "Sardines", "can", 1_000, 1_000, 2_500, 2_500, 1_800, 1_800)),
        payments = listOf(PaymentEntity("payment-1", "sale-12345678", "CASH", 2_500, 2_500, 0, 1_800_000_000_000L)),
        customerName = null,
    )
}
