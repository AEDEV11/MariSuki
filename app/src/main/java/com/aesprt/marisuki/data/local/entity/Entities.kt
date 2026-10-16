package com.aesprt.marisuki.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(tableName = "categories", indices = [Index(value = ["name"], unique = true)])
data class CategoryEntity(
    val name: String,
    val createdAtMillis: Long,
    @androidx.room.PrimaryKey val id: String,
)

@Entity(
    tableName = "products",
    foreignKeys = [ForeignKey(entity = CategoryEntity::class, parentColumns = ["id"], childColumns = ["categoryId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("categoryId"), Index(value = ["barcode"], unique = true)],
)
data class ProductEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val barcode: String?,
    val categoryId: String?,
    val baseUnitLabel: String,
    val baseUnitPriceCentavos: Long,
    val purchaseCostCentavos: Long,
    val minimumStockMilliUnits: Long,
    val wholeBaseUnitsOnly: Boolean,
    val active: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val favorite: Boolean = false,
    val imageFileName: String? = null,
)

@Entity(
    tableName = "product_units",
    foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("productId"), Index(value = ["productId", "label"], unique = true)],
)
data class ProductUnitEntity(
    @androidx.room.PrimaryKey val id: String,
    val productId: String,
    val label: String,
    val baseQuantityMilliUnits: Long,
    val priceCentavos: Long,
    val wholeSellQuantityOnly: Boolean,
)

@Entity(
    tableName = "inventory_balances",
    foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)],
)
data class InventoryBalanceEntity(
    @androidx.room.PrimaryKey val productId: String,
    val onHandMilliUnits: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "inventory_movements",
    foreignKeys = [ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("productId"), Index("occurredAtMillis"), Index(value = ["sourceType", "sourceId", "productId"], unique = true)],
)
data class InventoryMovementEntity(
    @androidx.room.PrimaryKey val id: String,
    val productId: String,
    val deltaMilliUnits: Long,
    val reason: String,
    val sourceType: String,
    val sourceId: String,
    val occurredAtMillis: Long,
    val note: String?,
)

@Entity(
    tableName = "customers",
    indices = [Index(value = ["name", "phone"], unique = true)],
)
data class CustomerEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val createdAtMillis: Long,
    val active: Boolean = true,
)

@Entity(
    tableName = "sales",
    foreignKeys = [ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("createdAtMillis"), Index("customerId"), Index("cashierAccountId"), Index(value = ["idempotencyKey"], unique = true)],
)
data class SaleEntity(
    @androidx.room.PrimaryKey val id: String,
    val idempotencyKey: String,
    val createdAtMillis: Long,
    val totalCentavos: Long,
    val customerId: String?,
    val status: String,
    val cashierMode: Boolean,
    val cashierAccountId: String? = null,
    val salesChannel: String = "TINGI",
)

@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["saleId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("saleId"), Index("productId")],
)
data class SaleItemEntity(
    @androidx.room.PrimaryKey val id: String,
    val saleId: String,
    val productId: String,
    val productNameSnapshot: String,
    val unitLabelSnapshot: String,
    val quantityMilliUnits: Long,
    val baseQuantityMilliUnits: Long,
    val unitPriceCentavos: Long,
    val lineTotalCentavos: Long,
    val unitCostCentavos: Long,
    val lineCostCentavos: Long,
)

@Entity(
    tableName = "payments",
    foreignKeys = [ForeignKey(entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["saleId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("saleId"), Index("createdAtMillis")],
)
data class PaymentEntity(
    @androidx.room.PrimaryKey val id: String,
    val saleId: String,
    val method: String,
    val amountAppliedCentavos: Long,
    val tenderedCentavos: Long?,
    val changeCentavos: Long,
    val createdAtMillis: Long,
)

@Entity(
    tableName = "customer_ledger_entries",
    foreignKeys = [ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("customerId"), Index("occurredAtMillis"), Index(value = ["sourceType", "sourceId"], unique = true)],
)
data class CustomerLedgerEntryEntity(
    @androidx.room.PrimaryKey val id: String,
    val customerId: String,
    val deltaCentavos: Long,
    val sourceType: String,
    val sourceId: String,
    val occurredAtMillis: Long,
    val note: String?,
)

@Entity(
    tableName = "customer_payments",
    foreignKeys = [ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customerId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("customerId"), Index("receivedAtMillis"), Index(value = ["idempotencyKey"], unique = true)],
)
data class CustomerPaymentEntity(
    @androidx.room.PrimaryKey val id: String,
    val customerId: String,
    val idempotencyKey: String,
    val amountCentavos: Long,
    val paymentMethod: String,
    val receivedAtMillis: Long,
)

@Entity(tableName = "suppliers", indices = [Index("name", unique = true)])
data class SupplierEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val phone: String?,
    val createdAtMillis: Long,
    val active: Boolean = true,
)

@Entity(
    tableName = "stock_deliveries",
    foreignKeys = [ForeignKey(entity = SupplierEntity::class, parentColumns = ["id"], childColumns = ["supplierId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("supplierId"), Index("deliveredAtMillis")],
)
data class StockDeliveryEntity(
    @androidx.room.PrimaryKey val id: String,
    val supplierId: String?,
    val supplierNameSnapshot: String?,
    val deliveredAtMillis: Long,
    val totalCostCentavos: Long,
    val note: String?,
)

@Entity(
    tableName = "stock_delivery_items",
    foreignKeys = [
        ForeignKey(entity = StockDeliveryEntity::class, parentColumns = ["id"], childColumns = ["deliveryId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = ProductEntity::class, parentColumns = ["id"], childColumns = ["productId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("deliveryId"), Index("productId")],
)
data class StockDeliveryItemEntity(
    @androidx.room.PrimaryKey val id: String,
    val deliveryId: String,
    val productId: String,
    val productNameSnapshot: String,
    val unitLabelSnapshot: String,
    val quantityMilliUnits: Long,
    val baseQuantityMilliUnits: Long,
    val unitCostCentavos: Long,
    val lineCostCentavos: Long,
)

@Entity(tableName = "expenses", indices = [Index("occurredAtMillis"), Index("category")])
data class ExpenseEntity(
    @androidx.room.PrimaryKey val id: String,
    val amountCentavos: Long,
    val category: String,
    val description: String?,
    val paymentSource: String,
    val occurredAtMillis: Long,
)

@Entity(
    tableName = "refunds",
    foreignKeys = [ForeignKey(entity = SaleEntity::class, parentColumns = ["id"], childColumns = ["saleId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("saleId"), Index(value = ["idempotencyKey"], unique = true)],
)
data class RefundEntity(
    @androidx.room.PrimaryKey val id: String,
    val saleId: String,
    val idempotencyKey: String,
    val refundedCentavos: Long,
    val reason: String,
    val createdAtMillis: Long,
    val refundMethod: String = "CASH",
)

@Entity(
    tableName = "refund_items",
    foreignKeys = [
        ForeignKey(entity = RefundEntity::class, parentColumns = ["id"], childColumns = ["refundId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = SaleItemEntity::class, parentColumns = ["id"], childColumns = ["saleItemId"], onDelete = ForeignKey.RESTRICT),
    ],
    indices = [Index("refundId"), Index("saleItemId")],
)
data class RefundItemEntity(
    @androidx.room.PrimaryKey val id: String,
    val refundId: String,
    val saleItemId: String,
    val quantityMilliUnits: Long,
    val amountCentavos: Long,
    val restock: Boolean,
)

@Entity(tableName = "audit_log", indices = [Index("occurredAtMillis"), Index("operation"), Index("actorAccountId")])
data class AuditLogEntity(
    @androidx.room.PrimaryKey val id: String,
    val operation: String,
    val targetId: String?,
    val details: String,
    val occurredAtMillis: Long,
    val actorAccountId: String? = null,
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @androidx.room.PrimaryKey val key: String,
    val value: String,
)

@Entity(tableName = "printer_configurations")
data class PrinterConfigurationEntity(
    @androidx.room.PrimaryKey val id: String,
    val displayName: String,
    val connectionType: String,
    val address: String,
    val paperWidthMillimeters: Int,
    val commandLanguage: String,
    val enabled: Boolean,
)

@Entity(tableName = "store_profile")
data class StoreProfileEntity(
    @androidx.room.PrimaryKey val id: String = "store",
    val storeName: String,
    val address: String?,
    val contactNumber: String?,
    val logoBytes: ByteArray?,
    val currencyCode: String = "PHP",
    val language: String,
    val theme: String,
    val defaultCashierScreen: String,
    val lowStockAlertsEnabled: Boolean,
    val allowNegativeStock: Boolean,
    val receiptShowStoreInfo: Boolean,
    val receiptFooter: String?,
    val setupComplete: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/** A resumable wizard draft. Credential values are deliberately never persisted here. */
@Entity(tableName = "setup_drafts")
data class SetupDraftEntity(
    @androidx.room.PrimaryKey val id: Int = 1,
    val step: Int,
    val storeName: String,
    val address: String?,
    val contactNumber: String?,
    val logoBytes: ByteArray?,
    val language: String,
    val theme: String,
    val defaultCashierScreen: String,
    val lowStockAlertsEnabled: Boolean,
    val allowNegativeStock: Boolean,
    val receiptShowStoreInfo: Boolean,
    val receiptFooter: String?,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "staff_accounts",
    indices = [Index(value = ["usernameNormalized"], unique = true)],
)
data class StaffAccountEntity(
    @androidx.room.PrimaryKey val id: String,
    val displayName: String,
    val username: String,
    val usernameNormalized: String,
    val role: String,
    val loginCredentialType: String,
    val loginSalt: String,
    val loginVerifier: String,
    val ownerPinSalt: String?,
    val ownerPinVerifier: String?,
    val enabled: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val permissionRevision: Int = 0,
)

@Entity(
    tableName = "staff_permissions",
    primaryKeys = ["accountId", "permissionCode"],
    foreignKeys = [
        ForeignKey(
            entity = StaffAccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("accountId")],
)
data class StaffPermissionEntity(
    val accountId: String,
    val permissionCode: String,
    val enabled: Boolean,
)

@Entity(tableName = "auth_attempt_state")
data class AuthAttemptStateEntity(
    @androidx.room.PrimaryKey val lockKey: String,
    val failedAttempts: Int,
    val lockedUntilMillis: Long,
    val updatedAtMillis: Long,
)

/** Permanent, non-secret record of owner PIN approval and the exact operation it covered. */
@Entity(
    tableName = "restricted_action_audit",
    indices = [Index("requestedAtMillis"), Index("requesterAccountId"), Index("approverAccountId"), Index("operationKey")],
)
data class RestrictedActionAuditEntity(
    @androidx.room.PrimaryKey val id: String,
    val requesterAccountId: String,
    val approverAccountId: String?,
    val operationKey: String,
    val targetId: String?,
    val payloadDigest: String,
    val beforeValue: String?,
    val afterValue: String?,
    val authorizationResult: String,
    val operationResult: String,
    val requestedAtMillis: Long,
    val expiresAtMillis: Long,
    val completedAtMillis: Long?,
)
