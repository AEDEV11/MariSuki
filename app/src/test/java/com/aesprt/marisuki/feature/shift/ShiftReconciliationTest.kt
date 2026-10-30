package com.aesprt.marisuki.feature.shift

import com.aesprt.marisuki.domain.model.ReconciliationStatus
import com.aesprt.marisuki.domain.model.ShiftReconciliationCalculator
import com.aesprt.marisuki.domain.model.ShiftStatus
import com.aesprt.marisuki.domain.model.ShiftSummary
import com.aesprt.marisuki.feature.pos.PosViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class ShiftReconciliationTest {
    @Test
    fun expectedCashFormulaIncludesOpeningCashSalesMovementsAndCashRefundsOnly() {
        // Opening: ₱2,000.00 (200,000)
        // Net Cash Sales: ₱8,450.00 (845,000)
        // Cash In: ₱500.00 (50,000)
        // Cash Out: ₱300.00 (30,000)
        // Cash Refunds: ₱150.00 (15,000)
        // Adjustments: -₱50.00 (-5,000)
        // Expected = 200,000 + 845,000 + 50,000 - 30,000 - 15,000 - 5,000 = 1,045,000 (₱10,450.00)
        val expected = ShiftReconciliationCalculator.calculateExpectedCash(
            openingCashCentavos = 200_000L,
            netCashPaymentsCentavos = 845_000L,
            cashInCentavos = 50_000L,
            cashOutCentavos = 30_000L,
            cashRefundsCentavos = 15_000L,
            adjustmentsCentavos = -5_000L,
        )
        assertEquals(1_045_000L, expected)
    }

    @Test
    fun reconcileClassifiesBalancedShortAndOverWithExactCentavoVariance() {
        val balanced = ShiftReconciliationCalculator.reconcile(
            expectedCashCentavos = 1_050_000L,
            actualCashCentavos = 1_050_000L,
        )
        assertEquals(0L, balanced.varianceCentavos)
        assertEquals(ReconciliationStatus.BALANCED, balanced.status)

        val short = ShiftReconciliationCalculator.reconcile(
            expectedCashCentavos = 1_050_000L,
            actualCashCentavos = 1_030_000L,
        )
        assertEquals(-20_000L, short.varianceCentavos)
        assertEquals(ReconciliationStatus.SHORT, short.status)

        val over = ShiftReconciliationCalculator.reconcile(
            expectedCashCentavos = 1_050_000L,
            actualCashCentavos = 1_065_000L,
        )
        assertEquals(15_000L, over.varianceCentavos)
        assertEquals(ReconciliationStatus.OVER, over.status)
    }

    @Test
    fun philippinePesoDenominationCounterComputesExactCentavoTotal() {
        val counts = mapOf(
            "BILL_1000" to 5,   // 500,000
            "BILL_500" to 4,    // 200,000
            "BILL_200" to 3,    //  60,000
            "BILL_100" to 2,    //  20,000
            "BILL_50" to 1,     //   5,000
            "BILL_20" to 2,     //   4,000
            "COIN_20" to 3,     //   6,000
            "COIN_10" to 4,     //   4,000
            "COIN_5" to 5,      //   2,500
            "COIN_1" to 9,      //     900
            "COIN_025" to 3,    //      75
            "COIN_010" to 2,    //      20
            "COIN_005" to 1,    //       5
            "COIN_001" to 4,    //       4
        )
        val total = ShiftReconciliationCalculator.calculateDenominationTotalCentavos(counts)
        assertEquals(802_504L, total)
        assertEquals("8025.04", PosViewModel.formatAmountInput(total))
        assertEquals(802_504L, PosViewModel.parseCentavos(PosViewModel.formatAmountInput(total)))
    }

    @Test
    fun deterministicPdfFileNameAndPhpFormattingFollowSpecification() {
        val summary = ShiftSummary(
            shiftId = "SHF-20261030-00012A",
            cashierId = "cashier-01",
            cashierName = "Juan Dela Cruz",
            terminalId = "POS-01",
            businessDate = "2026-10-30",
            startedAt = 1_800_000_000_000L,
            endedAt = 1_800_028_800_000L,
            shiftStatus = ShiftStatus.CLOSED,
            openingCashCentavos = 200_000L,
            transactionCount = 12,
            grossSalesCentavos = 1_245_000L,
            discountsCentavos = 0L,
            refundsCentavos = 15_000L,
            netSalesCentavos = 1_230_000L,
            cashPaymentsCentavos = 850_000L,
            gcashPaymentsCentavos = 250_000L,
            mayaPaymentsCentavos = 130_000L,
            cardPaymentsCentavos = 0L,
            creditSalesCentavos = 15_000L,
            nonCashPaymentsCentavos = 380_000L,
            cashInCentavos = 50_000L,
            cashOutCentavos = 30_000L,
            adjustmentsCentavos = 0L,
            cashRefundsCentavos = 15_000L,
            nonCashRefundsCentavos = 0L,
            expectedCashCentavos = 1_055_000L,
            actualCashCentavos = 1_055_000L,
            varianceCentavos = 0L,
            reconciliationStatus = ReconciliationStatus.BALANCED,
            closingRemarks = null,
            approvedBy = null,
        )
        val fileName = EndShiftReportPdfExporter.buildDeterministicFileName(summary)
        assertEquals("MariSuki_EndShift_2026-10-30_CASHIER01_26103000012A.pdf", fileName)
        assertEquals("₱10,550.00", EndShiftReportPdfExporter.formatPhp(1_055_000L))
        assertEquals("-₱200.00", EndShiftReportPdfExporter.formatPhp(-20_000L))
    }
}

