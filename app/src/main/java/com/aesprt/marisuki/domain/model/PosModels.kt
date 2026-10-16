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
)

enum class SaleChannel { TINGI, WHOLESALE }

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
}
