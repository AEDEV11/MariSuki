package com.aesprt.marisuki.domain.model

enum class PaymentMethod { CASH, GCASH, MAYA }
enum class RefundMethod { CASH, GCASH, MAYA, STORE_CREDIT }

data class ProductUnitInput(
    val label: String,
    /** Thousandths of the product's base unit represented by one selling unit. */
    val baseQuantityMilliUnits: Long,
    val priceCentavos: Long,
    val wholeQuantityOnly: Boolean = true,
)

data class NewProduct(
    val name: String,
    val barcode: String? = null,
    val categoryName: String? = null,
    val baseUnitLabel: String,
    val baseUnitPriceCentavos: Long,
    val purchaseCostCentavos: Long,
    val initialStockMilliUnits: Long = 0,
    val minimumStockMilliUnits: Long = 0,
    val wholeBaseUnitsOnly: Boolean = true,
    val extraUnits: List<ProductUnitInput> = emptyList(),
    /** Relative filename stored under the app-private posdb/{package}/item_images folder. */
    val imageFileName: String? = null,
)

data class CartLineInput(
    val productId: String,
    val unitId: String,
    /** Thousandths of a selling unit. Countable units must be whole. */
    val quantityMilliUnits: Long,
)

data class TenderInput(
    val method: PaymentMethod,
    /** Amount offered. Cash may exceed the balance due; non-cash may not. */
    val amountTenderedCentavos: Long,
    /** E-wallet transaction reference. Required for GCash and Maya; ignored for cash. */
    val referenceNumber: String? = null,
)

/** MIXED records a single sale that combines tingi and wholesale (pakyawan) items. */
enum class SaleChannel { TINGI, WHOLESALE, MIXED }

const val MAX_PAYMENT_REFERENCE_LENGTH = 40

data class CheckoutCommand(
    val idempotencyKey: String,
    val lines: List<CartLineInput>,
    val tenders: List<TenderInput>,
    val customerId: String? = null,
    val cashierMode: Boolean = true,
    val salesChannel: SaleChannel = SaleChannel.TINGI,
)

data class CheckoutResult(
    val saleId: String,
    val totalCentavos: Long,
    val paidCentavos: Long,
    val changeCentavos: Long,
    val creditCentavos: Long,
    val wasAlreadyCommitted: Boolean = false,
)

data class DeliveryLineInput(
    val productId: String,
    /** Quantity expressed in the product's base unit, in thousandths. */
    val quantityMilliUnits: Long,
    val totalCostCentavos: Long,
)

data class RefundLineInput(
    val saleItemId: String,
    /** Quantity expressed in that sale line's selling unit, in thousandths. */
    val quantityMilliUnits: Long,
    val restock: Boolean,
)

data class RefundCommand(
    val idempotencyKey: String,
    val saleId: String,
    val reason: String,
    val lines: List<RefundLineInput>,
    val paymentMethod: RefundMethod = RefundMethod.CASH,
)

data class RefundResult(
    val refundId: String,
    val amountCentavos: Long,
    val wasAlreadyCommitted: Boolean = false,
)

sealed class PosError(message: String) : IllegalArgumentException(message) {
    data object InvalidProduct : PosError("Check the product details.")
    data object InvalidQuantity : PosError("Enter a valid quantity.")
    data object ProductNotFound : PosError("That product is no longer available.")
    data object InsufficientStock : PosError("There is not enough stock for this sale.")
    data object CustomerRequired : PosError("Choose a customer to record the remaining balance as utang.")
    data object PaymentInvalid : PosError("Check the payment amount.")
    data object DuplicateName : PosError("A product with that barcode or category already exists.")
    data object ShiftRequired : PosError("Start a cashier shift before processing sales.")
    data object PreviousShiftUnclosed : PosError("Your previous shift is still open. Close and reconcile it before starting a new shift or processing sales.")
    data object ShiftAlreadyOpen : PosError("An active shift is already open.")
    data object ShiftAlreadyClosed : PosError("This shift has already been closed.")
    data object ShiftVarianceReasonRequired : PosError("Provide a reason for the cash shortage or overage.")
    data object ShiftApprovalRequired : PosError("Manager or owner approval is required for this cash variance.")
}

enum class ShiftStatus { OPEN, CLOSED }

enum class ReconciliationStatus { PENDING, BALANCED, SHORT, OVER }

enum class CashMovementType { CASH_IN, CASH_OUT, ADJUSTMENT }

enum class ShiftReportStatus { GENERATED, FAILED, REGENERATED }

data class DenominationSpec(
    val key: String,
    val label: String,
    val unitCentavos: Long,
    val isCoin: Boolean = false,
)

val PHILIPPINE_PESO_DENOMINATIONS: List<DenominationSpec> = listOf(
    DenominationSpec(key = "BILL_1000", label = "₱1,000", unitCentavos = 100_000L, isCoin = false),
    DenominationSpec(key = "BILL_500", label = "₱500", unitCentavos = 50_000L, isCoin = false),
    DenominationSpec(key = "BILL_200", label = "₱200", unitCentavos = 20_000L, isCoin = false),
    DenominationSpec(key = "BILL_100", label = "₱100", unitCentavos = 10_000L, isCoin = false),
    DenominationSpec(key = "BILL_50", label = "₱50", unitCentavos = 5_000L, isCoin = false),
    DenominationSpec(key = "BILL_20", label = "₱20 Bill", unitCentavos = 2_000L, isCoin = false),
    DenominationSpec(key = "COIN_20", label = "₱20 Coin", unitCentavos = 2_000L, isCoin = true),
    DenominationSpec(key = "COIN_10", label = "₱10", unitCentavos = 1_000L, isCoin = true),
    DenominationSpec(key = "COIN_5", label = "₱5", unitCentavos = 500L, isCoin = true),
    DenominationSpec(key = "COIN_1", label = "₱1", unitCentavos = 100L, isCoin = true),
    DenominationSpec(key = "COIN_025", label = "₱0.25", unitCentavos = 25L, isCoin = true),
    DenominationSpec(key = "COIN_010", label = "₱0.10", unitCentavos = 10L, isCoin = true),
    DenominationSpec(key = "COIN_005", label = "₱0.05", unitCentavos = 5L, isCoin = true),
    DenominationSpec(key = "COIN_001", label = "₱0.01", unitCentavos = 1L, isCoin = true),
)

data class ShiftReconciliationOutcome(
    val expectedCashCentavos: Long,
    val actualCashCentavos: Long,
    val varianceCentavos: Long,
    val status: ReconciliationStatus,
)

object ShiftReconciliationCalculator {
    /**
     * Exact centavo formula:
     * Expected Cash = Opening Cash + Net Cash Payments + Cash In - Cash Out - Cash Refunds + Adjustments
     */
    fun calculateExpectedCash(
        openingCashCentavos: Long,
        netCashPaymentsCentavos: Long,
        cashInCentavos: Long,
        cashOutCentavos: Long,
        cashRefundsCentavos: Long,
        adjustmentsCentavos: Long = 0L,
    ): Long = openingCashCentavos +
        netCashPaymentsCentavos +
        cashInCentavos -
        cashOutCentavos -
        cashRefundsCentavos +
        adjustmentsCentavos

    fun reconcile(
        expectedCashCentavos: Long,
        actualCashCentavos: Long,
    ): ShiftReconciliationOutcome {
        val variance = actualCashCentavos - expectedCashCentavos
        val status = when {
            variance == 0L -> ReconciliationStatus.BALANCED
            variance < 0L -> ReconciliationStatus.SHORT
            else -> ReconciliationStatus.OVER
        }
        return ShiftReconciliationOutcome(
            expectedCashCentavos = expectedCashCentavos,
            actualCashCentavos = actualCashCentavos,
            varianceCentavos = variance,
            status = status,
        )
    }

    fun calculateDenominationTotalCentavos(counts: Map<String, Int>): Long =
        PHILIPPINE_PESO_DENOMINATIONS.sumOf { spec ->
            val quantity = (counts[spec.key] ?: 0).coerceAtLeast(0).toLong()
            Math.multiplyExact(spec.unitCentavos, quantity)
        }
}

data class ShiftCashMovementRecord(
    val movementId: String,
    val shiftId: String,
    val movementType: CashMovementType,
    val amountCentavos: Long,
    val reason: String,
    val createdBy: String,
    val createdAt: Long,
)

data class ShiftSummary(
    val shiftId: String,
    val cashierId: String,
    val cashierName: String,
    val terminalId: String,
    val businessDate: String,
    val startedAt: Long,
    val endedAt: Long?,
    val shiftStatus: ShiftStatus,
    val openingCashCentavos: Long,
    val transactionCount: Int,
    val grossSalesCentavos: Long,
    val discountsCentavos: Long,
    val refundsCentavos: Long,
    val netSalesCentavos: Long,
    val cashPaymentsCentavos: Long,
    val gcashPaymentsCentavos: Long,
    val mayaPaymentsCentavos: Long,
    val cardPaymentsCentavos: Long,
    val creditSalesCentavos: Long,
    val nonCashPaymentsCentavos: Long,
    val cashInCentavos: Long,
    val cashOutCentavos: Long,
    val adjustmentsCentavos: Long,
    val cashRefundsCentavos: Long,
    val nonCashRefundsCentavos: Long,
    val expectedCashCentavos: Long,
    val actualCashCentavos: Long?,
    val varianceCentavos: Long?,
    val reconciliationStatus: ReconciliationStatus,
    val closingRemarks: String?,
    val approvedBy: String?,
    val movements: List<ShiftCashMovementRecord> = emptyList(),
    val reportId: String? = null,
    val reportFileName: String? = null,
    val reportFileUri: String? = null,
    val reportStatus: ShiftReportStatus? = null,
    val reportGeneratedAt: Long? = null,
    val financialTotalsVisible: Boolean = true,
)

sealed interface ShiftGateState {
    data object Loading : ShiftGateState
    data object NotRequired : ShiftGateState
    data class NoActiveShift(
        val cashierId: String,
        val cashierName: String,
        val terminalId: String,
        val businessDate: String,
    ) : ShiftGateState
    data class ActiveShift(
        val summary: ShiftSummary,
    ) : ShiftGateState
    data class PreviousShiftOpen(
        val currentBusinessDate: String,
        val summary: ShiftSummary,
    ) : ShiftGateState
}

data class StartShiftCommand(
    val idempotencyKey: String,
    val openingCashCentavos: Long,
    val terminalId: String? = null,
)

data class CloseShiftCommand(
    val idempotencyKey: String,
    val shiftId: String,
    val actualCashCentavos: Long,
    val closingRemarks: String? = null,
    val approvedBy: String? = null,
)

data class CloseShiftResult(
    val summary: ShiftSummary,
    val wasAlreadyClosed: Boolean = false,
)

