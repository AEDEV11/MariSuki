package com.aesprt.marisuki

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aesprt.marisuki.data.PosRepository
import com.aesprt.marisuki.data.auth.AuthException
import com.aesprt.marisuki.data.local.PosDatabase
import com.aesprt.marisuki.data.local.entity.ExpenseEntity
import com.aesprt.marisuki.domain.model.CartLineInput
import com.aesprt.marisuki.domain.model.CheckoutCommand
import com.aesprt.marisuki.domain.model.DeliveryLineInput
import com.aesprt.marisuki.domain.model.NewProduct
import com.aesprt.marisuki.domain.model.PaymentMethod
import com.aesprt.marisuki.domain.model.PosError
import com.aesprt.marisuki.domain.model.ProductUnitInput
import com.aesprt.marisuki.domain.model.RefundCommand
import com.aesprt.marisuki.domain.model.RefundLineInput
import com.aesprt.marisuki.domain.model.RefundMethod
import com.aesprt.marisuki.domain.model.SaleChannel
import com.aesprt.marisuki.domain.model.TenderInput
import com.aesprt.marisuki.domain.auth.AccountRole
import com.aesprt.marisuki.domain.auth.AuthenticatedActor
import com.aesprt.marisuki.domain.auth.AuthFailure
import com.aesprt.marisuki.domain.auth.PosAccessControl
import com.aesprt.marisuki.domain.auth.PosPermission
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosRepositoryInstrumentedTest {
    private lateinit var database: PosDatabase
    private lateinit var repository: PosRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = PosRepository(
            database,
            database.posDao(),
            PosAccessControl { AuthenticatedActor("test-owner", AccountRole.OWNER, 0) },
        ) { 1_800_000_000_000L }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun mixedCaseAndPieceCashSaleDeductsStockExactlyOnce() = runBlocking {
        val productId = createCannedProduct(stockPieces = 120)
        val units = repository.productUnits(productId)
        val can = units.single { it.baseQuantityMilliUnits == PosRepository.SCALE }
        val case = units.single { it.label == "Case" }
        val command = CheckoutCommand(
            idempotencyKey = "cash-sale-001",
            lines = listOf(
                CartLineInput(productId, case.id, PosRepository.SCALE),
                CartLineInput(productId, can.id, 2 * PosRepository.SCALE),
            ),
            tenders = listOf(TenderInput(PaymentMethod.CASH, 60_000)),
        )

        val first = repository.checkout(command)
        val retry = repository.checkout(command)

        assertEquals(55_000L, first.totalCentavos)
        assertEquals(60_000L, first.paidCentavos + first.changeCentavos)
        assertEquals(5_000L, first.changeCentavos)
        assertEquals(first.saleId, retry.saleId)
        assertTrue(retry.wasAlreadyCommitted)
        assertEquals(94 * PosRepository.SCALE, database.posDao().inventoryBalance(productId)?.onHandMilliUnits)
        assertEquals(1, repository.observeSales().first().size)
        assertEquals(2, database.posDao().saleItems(first.saleId).size)
    }

    @Test
    fun wholesaleChannelIsPersistedAndIncludedInTheRealRoomReport() = runBlocking {
        val productId = createCannedProduct(stockPieces = 10)
        val can = repository.productUnits(productId).single { it.baseQuantityMilliUnits == PosRepository.SCALE }
        val result = repository.checkout(
            CheckoutCommand(
                idempotencyKey = "wholesale-channel-report",
                lines = listOf(CartLineInput(productId, can.id, 2 * PosRepository.SCALE)),
                tenders = listOf(TenderInput(PaymentMethod.CASH, 5_000)),
                salesChannel = SaleChannel.WHOLESALE,
            ),
        )

        assertEquals("WHOLESALE", database.posDao().sale(result.saleId)?.salesChannel)
        val report = repository.salesReport(0L, Long.MAX_VALUE)
        assertEquals(5_000L, report.netSalesCentavos)
        assertEquals("WHOLESALE", report.channels.single().salesChannel)
        assertEquals(1, report.saleCount)
    }

    @Test
    fun expenseOnlyReportPermissionDoesNotExposeSalesMetricsAndCsvUsesCurrentPermissions() = runBlocking {
        database.posDao().insertExpense(
            ExpenseEntity("expense-report-only", 1_700L, "Utilities", "Water", "CASH", 1_800_000_000_000L),
        )
        val allowed = setOf(PosPermission.VIEW_EXPENSES, PosPermission.EXPORT_REPORTS)
        val restrictedRepository = PosRepository(
            database,
            database.posDao(),
            PosAccessControl { permission ->
                if (permission !in allowed) throw AuthException(AuthFailure.Validation("Permission denied"))
                AuthenticatedActor("test-cashier", AccountRole.CASHIER, 0)
            },
        ) { 1_800_000_000_000L }

        val report = restrictedRepository.salesReport(1_799_999_000_000L, 1_800_001_000_000L)
        assertFalse(report.salesVisible)
        assertEquals(0L, report.grossSalesCentavos)
        assertEquals(0, report.saleCount)
        assertTrue(report.payments.isEmpty())
        assertEquals(1_700L, report.expensesCentavos)
        assertEquals("Utilities", report.expenseCategories.single().label)

        val csv = ByteArrayOutputStream()
        restrictedRepository.exportSalesReportCsv(report.fromMillis, report.untilMillis, csv)
        val text = csv.toString("UTF-8")
        assertTrue(text.contains("Expenses,1700"))
        assertTrue(text.contains("\"Utilities\",1700"))
        assertFalse(text.contains("Gross sales"))
        assertFalse(text.contains("Completed sales"))
        assertFalse(text.contains("Payment channel"))
    }

    @Test
    fun insufficientStockLeavesSaleAndBalanceUntouched() = runBlocking {
        val productId = createCannedProduct(stockPieces = 10)
        val case = repository.productUnits(productId).single { it.label == "Case" }
        val failure = runCatching {
            repository.checkout(
                CheckoutCommand(
                    idempotencyKey = "insufficient-sale",
                    lines = listOf(CartLineInput(productId, case.id, PosRepository.SCALE)),
                    tenders = listOf(TenderInput(PaymentMethod.GCASH, 50_000)),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is PosError.InsufficientStock)
        assertEquals(10 * PosRepository.SCALE, database.posDao().inventoryBalance(productId)?.onHandMilliUnits)
        assertTrue(repository.observeSales().first().isEmpty())
    }

    @Test
    fun creditPartialRepaymentAndStockReceiptAreIdempotent() = runBlocking {
        val productId = createCannedProduct(stockPieces = 2)
        val can = repository.productUnits(productId).single { it.baseQuantityMilliUnits == PosRepository.SCALE }
        val customerId = repository.createCustomer("Aling Rosa", "09170000000")
        val sale = repository.checkout(
            CheckoutCommand(
                idempotencyKey = "credit-sale-001",
                lines = listOf(CartLineInput(productId, can.id, PosRepository.SCALE)),
                tenders = emptyList(),
                customerId = customerId,
            ),
        )
        repository.recordCustomerPayment(customerId, 1_000, PaymentMethod.CASH, "payment-001")
        repository.recordCustomerPayment(customerId, 1_000, PaymentMethod.CASH, "payment-001")
        repository.addStock(productId, 5 * PosRepository.SCALE, "DELIVERY", "delivery-001")
        repository.addStock(productId, 5 * PosRepository.SCALE, "DELIVERY", "delivery-001")

        assertEquals(2_500L, sale.creditCentavos)
        assertEquals(1_500L, database.posDao().customerBalance(customerId))
        assertEquals(6 * PosRepository.SCALE, database.posDao().inventoryBalance(productId)?.onHandMilliUnits)
        assertFalse(sale.wasAlreadyCommitted)
    }

    @Test
    fun customerLedgerFlowRequiresBalancePermission() = runBlocking {
        val productId = createCannedProduct(stockPieces = 2)
        val can = repository.productUnits(productId).single { it.baseQuantityMilliUnits == PosRepository.SCALE }
        val customerId = repository.createCustomer("Aling Rosa", null)
        repository.checkout(
            CheckoutCommand(
                idempotencyKey = "customer-ledger-permission",
                lines = listOf(CartLineInput(productId, can.id, PosRepository.SCALE)),
                tenders = emptyList(),
                customerId = customerId,
            ),
        )

        assertEquals("SALE", repository.observeCustomerLedger(customerId).first().single().sourceType)

        val deniedRepository = PosRepository(
            database,
            database.posDao(),
            PosAccessControl { throw AuthException(AuthFailure.Validation("Permission denied")) },
        )
        assertTrue(deniedRepository.observeCustomerLedger(customerId).first().isEmpty())
    }

    @Test
    fun supplierDeliveryUpdatesStockAndWeightedPurchaseCostAndExpenseIsIdempotent() = runBlocking {
        val productId = createCannedProduct(stockPieces = 10)
        repository.recordStockDelivery(
            operationId = "delivery-002",
            supplierName = "North Market",
            supplierPhone = "09171112222",
            lines = listOf(DeliveryLineInput(productId, 10 * PosRepository.SCALE, 20_000)),
            note = "Morning delivery",
        )
        repository.recordStockDelivery(
            operationId = "delivery-002",
            supplierName = "North Market",
            supplierPhone = null,
            lines = listOf(DeliveryLineInput(productId, 10 * PosRepository.SCALE, 20_000)),
            note = null,
        )
        repository.recordExpense("expense-001", 5_000, "Transport", "Supplier pickup", "CASH")
        repository.recordExpense("expense-001", 5_000, "Transport", "Supplier pickup", "CASH")

        assertEquals(20 * PosRepository.SCALE, database.posDao().inventoryBalance(productId)?.onHandMilliUnits)
        assertEquals(1_850L, database.posDao().product(productId)?.purchaseCostCentavos)
        assertEquals(1, repository.observeStockDeliveries().first().size)
        assertEquals(1, repository.observeSuppliers().first().size)
        assertEquals(1, repository.observeExpenses().first().size)
    }

    @Test
    fun internalBarcodeIsSavedOnceAndCanBeLookedUpByScanner() = runBlocking {
        val productId = createCannedProduct(stockPieces = 4)

        val barcode = repository.ensureProductBarcode(productId)

        assertTrue(barcode.startsWith("MSK"))
        assertEquals(15, barcode.length)
        assertEquals(barcode, repository.ensureProductBarcode(productId))
        assertEquals(barcode, database.posDao().product(productId)?.barcode)
        assertEquals(productId, repository.productByBarcode(barcode)?.id)
    }

    @Test
    fun fullReturnRestocksAndRefundRetriesDoNotDuplicateFinancialEffects() = runBlocking {
        val productId = createCannedProduct(stockPieces = 3)
        val can = repository.productUnits(productId).single { it.baseQuantityMilliUnits == PosRepository.SCALE }
        val sale = repository.checkout(
            CheckoutCommand(
                idempotencyKey = "sale-to-return",
                lines = listOf(CartLineInput(productId, can.id, PosRepository.SCALE)),
                tenders = listOf(TenderInput(PaymentMethod.CASH, 2_500)),
            ),
        )
        val saleItem = database.posDao().saleItems(sale.saleId).single()
        val command = RefundCommand(
            idempotencyKey = "refund-001",
            saleId = sale.saleId,
            reason = "Damaged packaging",
            lines = listOf(RefundLineInput(saleItem.id, saleItem.quantityMilliUnits, restock = true)),
        )

        val refund = repository.refundSale(command)
        val retry = repository.refundSale(command)

        assertEquals(2_500L, refund.amountCentavos)
        assertTrue(retry.wasAlreadyCommitted)
        assertEquals(refund.refundId, retry.refundId)
        assertEquals(3 * PosRepository.SCALE, database.posDao().inventoryBalance(productId)?.onHandMilliUnits)
        assertEquals(0L, database.posDao().observeSalesTotal(0, Long.MAX_VALUE).first())
        assertEquals(0L, database.posDao().observeSoldCostTotal(0, Long.MAX_VALUE).first())
        assertEquals(2_500L, database.posDao().observeRefundTotal("CASH", 0, Long.MAX_VALUE).first())
        assertEquals(2_500L, database.posDao().observePaymentTotal("CASH", 0, Long.MAX_VALUE).first())
        val summary = repository.observeSales().first().single()
        assertTrue(summary.hasRefund)
        assertTrue(summary.fullyRefunded)
        assertEquals(2_500L, summary.refundedCentavos)
        val history = repository.observeSaleHistoryDetails(sale.saleId).first()!!
        assertEquals(1, history.refunds.size)
        assertEquals("Damaged packaging", history.refunds.single().refund.reason)
        assertEquals("CASH", history.refunds.single().refund.refundMethod)
        assertEquals(saleItem.id, history.refunds.single().items.single().saleItemId)
        assertTrue(history.refunds.single().items.single().restock)
        assertEquals(2_500L, history.payments.single().amountAppliedCentavos)
        val secondRefund = runCatching { repository.refundSale(command.copy(idempotencyKey = "refund-002")) }.exceptionOrNull()
        assertTrue(secondRefund is PosError.InvalidQuantity)
    }

    @Test
    fun saleHistoryTracksPartialAndMultipleRefundsWithReasonsAndQuantities() = runBlocking {
        val productId = createCannedProduct(stockPieces = 3)
        val unit = repository.productUnits(productId).single { it.baseQuantityMilliUnits == PosRepository.SCALE }
        val sale = repository.checkout(CheckoutCommand(
            idempotencyKey = "history-sale",
            lines = listOf(CartLineInput(productId, unit.id, 2 * PosRepository.SCALE)),
            tenders = listOf(TenderInput(PaymentMethod.CASH, 5_000)),
        ))
        val original = repository.observeSales().first().single()
        assertFalse(original.hasRefund)
        val item = repository.saleItems(sale.saleId).single()
        repository.refundSale(RefundCommand(
            "history-return-1", sale.saleId, "Damaged can",
            listOf(RefundLineInput(item.id, PosRepository.SCALE, restock = false)), RefundMethod.CASH,
        ))
        val partial = repository.observeSales().first().single()
        assertTrue(partial.hasRefund)
        assertFalse(partial.fullyRefunded)
        assertEquals(2_500L, partial.refundedCentavos)
        val firstHistory = repository.observeSaleHistoryDetails(sale.saleId).first()!!
        assertEquals("Damaged can", firstHistory.refunds.single().refund.reason)
        assertEquals(PosRepository.SCALE, firstHistory.refunds.single().items.single().quantityMilliUnits)
        assertFalse(firstHistory.refunds.single().items.single().restock)
        repository.refundSale(RefundCommand(
            "history-return-2", sale.saleId, "Wrong item",
            listOf(RefundLineInput(item.id, PosRepository.SCALE, restock = true)), RefundMethod.GCASH,
        ))
        val full = repository.observeSales().first().single()
        assertTrue(full.fullyRefunded)
        assertEquals(5_000L, full.refundedCentavos)
        val history = repository.observeSaleHistoryDetails(sale.saleId).first()!!
        assertEquals(setOf("Damaged can", "Wrong item"), history.refunds.map { it.refund.reason }.toSet())
        assertEquals(setOf("CASH", "GCASH"), history.refunds.map { it.refund.refundMethod }.toSet())
        assertEquals(2 * PosRepository.SCALE, history.refunds.flatMap { it.items }.sumOf { it.quantityMilliUnits })
    }

    @Test
    fun storeCreditRefundClearsCustomerReceivableWithoutCashOutflow() = runBlocking {
        val productId = createCannedProduct(stockPieces = 2)
        val can = repository.productUnits(productId).single { it.baseQuantityMilliUnits == PosRepository.SCALE }
        val customerId = repository.createCustomer("Aling Liza", "09172223333")
        val sale = repository.checkout(
            CheckoutCommand(
                idempotencyKey = "credit-sale-return",
                lines = listOf(CartLineInput(productId, can.id, PosRepository.SCALE)),
                tenders = emptyList(),
                customerId = customerId,
            ),
        )
        val line = database.posDao().saleItems(sale.saleId).single()
        repository.refundSale(
            RefundCommand(
                idempotencyKey = "credit-return-001",
                saleId = sale.saleId,
                reason = "Changed mind",
                lines = listOf(RefundLineInput(line.id, line.quantityMilliUnits, restock = true)),
                paymentMethod = RefundMethod.STORE_CREDIT,
            ),
        )

        assertEquals(0L, database.posDao().customerBalance(customerId))
        assertEquals(0L, database.posDao().observeRefundTotal("CASH", 0, Long.MAX_VALUE).first())
        assertEquals(2_500L, database.posDao().observeRefundTotal("STORE_CREDIT", 0, Long.MAX_VALUE).first())
    }

    @Test
    fun cashierShiftLifecycleEnforcesPreviousDayGateAndExactDrawerReconciliation() = runBlocking {
        var nowMillis = 1_800_000_000_000L
        val cashierRepo = PosRepository(
            database,
            database.posDao(),
            PosAccessControl { AuthenticatedActor("cashier-01", AccountRole.CASHIER, 0) },
        ) { nowMillis }

        val productId = createCannedProduct(stockPieces = 50)
        val can = repository.productUnits(productId).single { it.baseQuantityMilliUnits == PosRepository.SCALE }

        // 1. Cashier cannot checkout before starting a shift
        val noShiftFailure = runCatching {
            cashierRepo.checkout(
                CheckoutCommand(
                    idempotencyKey = "no-shift-sale",
                    lines = listOf(CartLineInput(productId, can.id, PosRepository.SCALE)),
                    tenders = listOf(TenderInput(PaymentMethod.CASH, 2_500)),
                ),
            )
        }.exceptionOrNull()
        assertTrue(noShiftFailure is PosError.ShiftRequired)

        // 2. Start shift on Day 1 with ₱2,000.00 opening cash
        val started = cashierRepo.startShift(
            com.aesprt.marisuki.domain.model.StartShiftCommand(
                idempotencyKey = "shift-start-day1",
                openingCashCentavos = 200_000L,
                terminalId = "POS-01",
            ),
        )
        assertEquals("OPEN", started.shiftStatus.name)
        assertEquals(200_000L, started.openingCashCentavos)

        // Duplicate open shift is rejected
        val dupShift = runCatching {
            cashierRepo.startShift(
                com.aesprt.marisuki.domain.model.StartShiftCommand(
                    idempotencyKey = "shift-start-dup",
                    openingCashCentavos = 100_000L,
                ),
            )
        }.exceptionOrNull()
        assertTrue(dupShift is PosError.ShiftAlreadyOpen)

        // 3. Process Cash + GCash (with required reference number) + MIXED channel sales, plus Cash In/Out
        val cashSale = cashierRepo.checkout(
            CheckoutCommand(
                idempotencyKey = "day1-cash-sale",
                lines = listOf(CartLineInput(productId, can.id, 2 * PosRepository.SCALE)),
                tenders = listOf(TenderInput(PaymentMethod.CASH, 5_000L)),
                salesChannel = SaleChannel.MIXED,
            ),
        )
        assertEquals("MIXED", database.posDao().sale(cashSale.saleId)?.salesChannel)
        assertEquals(started.shiftId, database.posDao().sale(cashSale.saleId)?.shiftId)

        val gcashWithoutRef = runCatching {
            cashierRepo.checkout(
                CheckoutCommand(
                    idempotencyKey = "day1-gcash-noref",
                    lines = listOf(CartLineInput(productId, can.id, PosRepository.SCALE)),
                    tenders = listOf(TenderInput(PaymentMethod.GCASH, 2_500L, referenceNumber = null)),
                ),
            )
        }.exceptionOrNull()
        assertTrue(gcashWithoutRef is PosError.PaymentInvalid)

        cashierRepo.checkout(
            CheckoutCommand(
                idempotencyKey = "day1-gcash-sale",
                lines = listOf(CartLineInput(productId, can.id, PosRepository.SCALE)),
                tenders = listOf(TenderInput(PaymentMethod.GCASH, 2_500L, referenceNumber = "GC-998877")),
            ),
        )

        cashierRepo.recordShiftCashMovement(
            idempotencyKey = "move-in-1",
            shiftId = started.shiftId,
            movementType = com.aesprt.marisuki.domain.model.CashMovementType.CASH_IN,
            amountCentavos = 50_000L,
            reason = "Added change fund",
        )
        cashierRepo.recordShiftCashMovement(
            idempotencyKey = "move-out-1",
            shiftId = started.shiftId,
            movementType = com.aesprt.marisuki.domain.model.CashMovementType.CASH_OUT,
            amountCentavos = 20_000L,
            reason = "Safe drop",
        )

        // Expected Cash = 200_000 (opening) + 5_000 (cash sale) + 50_000 (cash in) - 20_000 (cash out) = 235_000
        val activeGate = cashierRepo.evaluateShiftGate()
        assertTrue(activeGate is com.aesprt.marisuki.domain.model.ShiftGateState.ActiveShift)
        val activeSummary = (activeGate as com.aesprt.marisuki.domain.model.ShiftGateState.ActiveShift).summary
        assertEquals(235_000L, activeSummary.expectedCashCentavos)
        assertEquals(2_500L, activeSummary.gcashPaymentsCentavos)

        // 4. Advance clock by 2 days without closing shift -> PreviousShiftOpen gate & checkout blocked
        nowMillis += 2L * 24L * 60L * 60L * 1_000L
        val nextDayGate = cashierRepo.evaluateShiftGate()
        assertTrue(nextDayGate is com.aesprt.marisuki.domain.model.ShiftGateState.PreviousShiftOpen)

        val blockedSale = runCatching {
            cashierRepo.checkout(
                CheckoutCommand(
                    idempotencyKey = "day2-blocked-sale",
                    lines = listOf(CartLineInput(productId, can.id, PosRepository.SCALE)),
                    tenders = listOf(TenderInput(PaymentMethod.CASH, 2_500L)),
                ),
            )
        }.exceptionOrNull()
        assertTrue(blockedSale is PosError.PreviousShiftUnclosed)

        // 5. Closing with a shortage requires remarks
        val missingRemarks = runCatching {
            cashierRepo.closeShift(
                com.aesprt.marisuki.domain.model.CloseShiftCommand(
                    idempotencyKey = "close-day1-no-remarks",
                    shiftId = started.shiftId,
                    actualCashCentavos = 230_000L,
                    closingRemarks = "",
                ),
            )
        }.exceptionOrNull()
        assertTrue(missingRemarks is PosError.ShiftVarianceReasonRequired)

        // 6. Close previous shift with explanation -> SHORT (-5_000 centavos)
        val closeResult = cashierRepo.closeShift(
            com.aesprt.marisuki.domain.model.CloseShiftCommand(
                idempotencyKey = "close-day1",
                shiftId = started.shiftId,
                actualCashCentavos = 230_000L,
                closingRemarks = "Short ₱50 change error",
            ),
        )
        assertFalse(closeResult.wasAlreadyClosed)
        assertEquals(com.aesprt.marisuki.domain.model.ReconciliationStatus.SHORT, closeResult.summary.reconciliationStatus)
        assertEquals(-5_000L, closeResult.summary.varianceCentavos)

        // 7. Now cashier can start a new shift on the current business date
        val day2Shift = cashierRepo.startShift(
            com.aesprt.marisuki.domain.model.StartShiftCommand(
                idempotencyKey = "shift-start-day2",
                openingCashCentavos = 150_000L,
            ),
        )
        assertEquals("OPEN", day2Shift.shiftStatus.name)
    }

    private suspend fun createCannedProduct(stockPieces: Long): String = repository.createProduct(
        NewProduct(
            name = "Canned goods",
            categoryName = "Groceries",
            baseUnitLabel = "Can",
            baseUnitPriceCentavos = 2_500,
            purchaseCostCentavos = 1_700,
            initialStockMilliUnits = stockPieces * PosRepository.SCALE,
            wholeBaseUnitsOnly = true,
            extraUnits = listOf(
                ProductUnitInput("Pack", 6 * PosRepository.SCALE, 14_000),
                ProductUnitInput("Case", 24 * PosRepository.SCALE, 50_000),
            ),
        ),
    )
}
