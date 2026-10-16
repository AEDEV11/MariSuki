package com.aesprt.marisuki.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aesprt.marisuki.data.local.entity.*
import kotlinx.coroutines.flow.Flow

data class ProductSummary(
    val id: String,
    val name: String,
    val barcode: String?,
    val categoryName: String?,
    val baseUnitLabel: String,
    val baseUnitPriceCentavos: Long,
    val purchaseCostCentavos: Long,
    val minimumStockMilliUnits: Long,
    val wholeBaseUnitsOnly: Boolean,
    val onHandMilliUnits: Long,
    val favorite: Boolean,
    val totalBaseSoldMilliUnits: Long,
    val imageFileName: String? = null,
)

data class CustomerBalance(
    val id: String,
    val name: String,
    val phone: String?,
    val balanceCentavos: Long,
)

data class SaleSummary(
    val id: String,
    val createdAtMillis: Long,
    val totalCentavos: Long,
    val status: String,
    val customerName: String?,
)

data class LabelAmount(val label: String, val amountCentavos: Long)
data class SalesChannelAmount(val salesChannel: String, val amountCentavos: Long, val saleCount: Int)
data class TopProductReportRow(val productId: String, val productName: String, val quantityMilliUnits: Long, val netSalesCentavos: Long)
data class DailySalesReportRow(val day: String, val salesCentavos: Long)
data class LowStockReportRow(val productId: String, val productName: String, val onHandMilliUnits: Long, val minimumStockMilliUnits: Long)
data class InventoryValuationRow(val onHandMilliUnits: Long, val purchaseCostCentavos: Long)

@Dao
interface PosDao {
    @Query("SELECT * FROM store_profile LIMIT 1")
    suspend fun storeProfile(): StoreProfileEntity?

    @Query("SELECT * FROM store_profile LIMIT 1")
    fun observeStoreProfile(): Flow<StoreProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStoreProfile(profile: StoreProfileEntity)

    @Query("SELECT * FROM setup_drafts WHERE id = 1")
    suspend fun setupDraft(): SetupDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSetupDraft(draft: SetupDraftEntity)

    @Query("DELETE FROM setup_drafts WHERE id = 1")
    suspend fun deleteSetupDraft()

    @Query("SELECT (SELECT COUNT(*) FROM products) + (SELECT COUNT(*) FROM sales) + (SELECT COUNT(*) FROM customers) + (SELECT COUNT(*) FROM suppliers) + (SELECT COUNT(*) FROM expenses) + (SELECT COUNT(*) FROM stock_deliveries) + (SELECT COUNT(*) FROM refunds) > 0")
    suspend fun hasOperationalData(): Boolean

    @Query("SELECT * FROM staff_accounts WHERE usernameNormalized = :normalizedUsername LIMIT 1")
    suspend fun staffAccountByUsername(normalizedUsername: String): StaffAccountEntity?

    @Query("SELECT COUNT(*) FROM staff_accounts")
    suspend fun staffAccountCount(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM staff_accounts WHERE role = 'OWNER')")
    suspend fun hasOwnerAccount(): Boolean

    @Query("SELECT * FROM staff_accounts WHERE id = :id LIMIT 1")
    suspend fun staffAccount(id: String): StaffAccountEntity?

    @Query("SELECT * FROM staff_accounts WHERE role = 'OWNER' AND enabled = 1 ORDER BY createdAtMillis LIMIT 1")
    suspend fun enabledOwnerAccount(): StaffAccountEntity?

    @Query("SELECT * FROM staff_accounts ORDER BY role DESC, displayName COLLATE NOCASE")
    fun observeStaffAccounts(): Flow<List<StaffAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStaffAccount(account: StaffAccountEntity)

    @Update
    suspend fun updateStaffAccount(account: StaffAccountEntity): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveStaffPermissions(permissions: List<StaffPermissionEntity>)

    @Query("DELETE FROM staff_permissions WHERE accountId = :accountId")
    suspend fun deleteStaffPermissions(accountId: String)

    @Query("SELECT * FROM staff_permissions WHERE accountId = :accountId ORDER BY permissionCode")
    suspend fun staffPermissions(accountId: String): List<StaffPermissionEntity>

    @Query("SELECT * FROM staff_permissions WHERE accountId = :accountId AND enabled = 1")
    fun observeStaffPermissions(accountId: String): Flow<List<StaffPermissionEntity>>

    @Query("SELECT * FROM auth_attempt_state WHERE lockKey = :lockKey")
    suspend fun authAttemptState(lockKey: String): AuthAttemptStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveAuthAttemptState(state: AuthAttemptStateEntity)

    @Query("DELETE FROM auth_attempt_state WHERE lockKey = :lockKey")
    suspend fun clearAuthAttemptState(lockKey: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRestrictedActionAudit(entry: RestrictedActionAuditEntity)

    @Update
    suspend fun updateRestrictedActionAudit(entry: RestrictedActionAuditEntity): Int

    @Query("SELECT * FROM restricted_action_audit WHERE id = :id LIMIT 1")
    suspend fun restrictedActionAudit(id: String): RestrictedActionAuditEntity?

    @Query("UPDATE restricted_action_audit SET authorizationResult = 'APPROVED', approverAccountId = :ownerId WHERE id = :id AND authorizationResult = 'PENDING' AND operationResult = 'PENDING' AND expiresAtMillis > :now")
    suspend fun approveRestrictedAction(id: String, ownerId: String, now: Long): Int

    @Query("UPDATE restricted_action_audit SET authorizationResult = 'DENIED', operationResult = 'DENIED', completedAtMillis = :now WHERE id = :id AND authorizationResult = 'PENDING' AND operationResult = 'PENDING'")
    suspend fun denyRestrictedAction(id: String, now: Long): Int

    @Query("UPDATE restricted_action_audit SET operationResult = 'CONSUMED', completedAtMillis = :now WHERE id = :id AND requesterAccountId = :requesterId AND operationKey = :operationKey AND targetId IS :targetId AND payloadDigest = :payloadDigest AND authorizationResult = 'APPROVED' AND operationResult = 'PENDING' AND expiresAtMillis > :now")
    suspend fun consumeRestrictedAction(id: String, requesterId: String, operationKey: String, targetId: String?, payloadDigest: String, now: Long): Int

    @Query("UPDATE restricted_action_audit SET operationResult = :result, completedAtMillis = :now WHERE id = :id AND operationResult IN ('PENDING', 'CONSUMED')")
    suspend fun finishRestrictedAction(id: String, result: String, now: Long): Int

    @Query("UPDATE restricted_action_audit SET authorizationResult = 'CANCELLED', operationResult = 'CANCELLED', completedAtMillis = :now WHERE id = :id AND authorizationResult = 'PENDING' AND operationResult = 'PENDING'")
    suspend fun cancelRestrictedAction(id: String, now: Long): Int

    @Query("SELECT * FROM restricted_action_audit ORDER BY requestedAtMillis DESC LIMIT 500")
    fun observeRestrictedActionAudit(): Flow<List<RestrictedActionAuditEntity>>

    @Query("SELECT * FROM audit_log WHERE actorAccountId = :accountId ORDER BY occurredAtMillis DESC LIMIT 500")
    fun observeStaffActivity(accountId: String): Flow<List<AuditLogEntity>>

    @Query("SELECT p.id, p.name, p.barcode, c.name AS categoryName, p.baseUnitLabel, p.baseUnitPriceCentavos, p.purchaseCostCentavos, p.minimumStockMilliUnits, p.wholeBaseUnitsOnly, COALESCE(b.onHandMilliUnits, 0) AS onHandMilliUnits, p.favorite, (SELECT COALESCE(SUM(si.baseQuantityMilliUnits), 0) FROM sale_items si WHERE si.productId = p.id) AS totalBaseSoldMilliUnits, p.imageFileName FROM products p LEFT JOIN categories c ON c.id = p.categoryId LEFT JOIN inventory_balances b ON b.productId = p.id WHERE p.active = 1 ORDER BY p.favorite DESC, p.name COLLATE NOCASE")
    fun observeProducts(): Flow<List<ProductSummary>>

    @Query("SELECT p.id, p.name, p.barcode, c.name AS categoryName, p.baseUnitLabel, p.baseUnitPriceCentavos, p.purchaseCostCentavos, p.minimumStockMilliUnits, p.wholeBaseUnitsOnly, COALESCE(b.onHandMilliUnits, 0) AS onHandMilliUnits, p.favorite, (SELECT COALESCE(SUM(si.baseQuantityMilliUnits), 0) FROM sale_items si WHERE si.productId = p.id) AS totalBaseSoldMilliUnits, p.imageFileName FROM products p LEFT JOIN categories c ON c.id = p.categoryId LEFT JOIN inventory_balances b ON b.productId = p.id WHERE p.active = 1 AND (:query = '' OR p.name LIKE '%' || :query || '%' COLLATE NOCASE OR p.barcode = :query) ORDER BY p.favorite DESC, p.name COLLATE NOCASE")
    fun searchProducts(query: String): Flow<List<ProductSummary>>

    @Query("SELECT * FROM products WHERE id = :productId")
    suspend fun product(productId: String): ProductEntity?

    @Query("SELECT p.id, p.name, p.barcode, c.name AS categoryName, p.baseUnitLabel, p.baseUnitPriceCentavos, p.purchaseCostCentavos, p.minimumStockMilliUnits, p.wholeBaseUnitsOnly, COALESCE(b.onHandMilliUnits, 0) AS onHandMilliUnits, p.favorite, (SELECT COALESCE(SUM(si.baseQuantityMilliUnits), 0) FROM sale_items si WHERE si.productId = p.id) AS totalBaseSoldMilliUnits, p.imageFileName FROM products p LEFT JOIN categories c ON c.id = p.categoryId LEFT JOIN inventory_balances b ON b.productId = p.id WHERE p.id = :productId AND p.active = 1")
    suspend fun productSummary(productId: String): ProductSummary?

    @Query("SELECT * FROM products WHERE barcode = :barcode AND active = 1 LIMIT 1")
    suspend fun productByBarcode(barcode: String): ProductEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM products WHERE barcode = :barcode)")
    suspend fun barcodeExists(barcode: String): Boolean

    @Query("UPDATE products SET barcode = :barcode, updatedAtMillis = :updatedAt WHERE id = :id AND active = 1 AND barcode IS NULL")
    suspend fun assignProductBarcode(id: String, barcode: String, updatedAt: Long): Int

    @Query("SELECT * FROM product_units WHERE productId = :productId ORDER BY baseQuantityMilliUnits")
    suspend fun productUnits(productId: String): List<ProductUnitEntity>

    @Query("SELECT * FROM product_units WHERE id = :unitId AND productId = :productId")
    suspend fun productUnit(productId: String, unitId: String): ProductUnitEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCategory(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun categoryByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Query("SELECT * FROM customers WHERE id = :id AND active = 1")
    suspend fun customer(id: String): CustomerEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSupplier(supplier: SupplierEntity)

    @Query("SELECT * FROM suppliers WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun supplierByName(name: String): SupplierEntity?

    @Query("SELECT * FROM suppliers WHERE active = 1 ORDER BY name COLLATE NOCASE")
    fun observeSuppliers(): Flow<List<SupplierEntity>>

    @Query("SELECT * FROM suppliers WHERE id = :id AND active = 1")
    suspend fun supplier(id: String): SupplierEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: ProductEntity)

    @Query("UPDATE products SET purchaseCostCentavos = :cost, updatedAtMillis = :updatedAt WHERE id = :id")
    suspend fun updatePurchaseCost(id: String, cost: Long, updatedAt: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProductUnit(unit: ProductUnitEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProductUnits(units: List<ProductUnitEntity>)

    @Query("DELETE FROM product_units WHERE productId = :productId")
    suspend fun deleteProductUnits(productId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInventoryBalance(balance: InventoryBalanceEntity)

    @Query("UPDATE products SET name = :name, barcode = :barcode, categoryId = :categoryId, baseUnitLabel = :unit, baseUnitPriceCentavos = :price, purchaseCostCentavos = :cost, minimumStockMilliUnits = :minimumStock, wholeBaseUnitsOnly = :wholeOnly, imageFileName = :imageFileName, updatedAtMillis = :updatedAt WHERE id = :id AND active = 1")
    suspend fun updateProduct(id: String, name: String, barcode: String?, categoryId: String?, unit: String, price: Long, cost: Long, minimumStock: Long, wholeOnly: Boolean, imageFileName: String?, updatedAt: Long): Int

    @Query("UPDATE products SET active = 0, updatedAtMillis = :updatedAt WHERE id = :id AND active = 1")
    suspend fun archiveProduct(id: String, updatedAt: Long): Int

    @Query("UPDATE products SET favorite = :favorite, updatedAtMillis = :updatedAt WHERE id = :id AND active = 1")
    suspend fun updateFavorite(id: String, favorite: Boolean, updatedAt: Long): Int

    @Query("UPDATE inventory_balances SET onHandMilliUnits = :onHand, updatedAtMillis = :updatedAt WHERE productId = :productId")
    suspend fun updateBalance(productId: String, onHand: Long, updatedAt: Long): Int

    @Query("SELECT * FROM inventory_balances WHERE productId = :productId")
    suspend fun inventoryBalance(productId: String): InventoryBalanceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovement(movement: InventoryMovementEntity)

    @Query("SELECT EXISTS(SELECT 1 FROM inventory_movements WHERE sourceType = :sourceType AND sourceId = :sourceId AND productId = :productId)")
    suspend fun hasMovement(sourceType: String, sourceId: String, productId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayments(payments: List<PaymentEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomerLedger(entry: CustomerLedgerEntryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCustomerPayment(payment: CustomerPaymentEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockDelivery(delivery: StockDeliveryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertStockDeliveryItems(items: List<StockDeliveryItemEntity>)

    @Query("SELECT * FROM stock_deliveries WHERE id = :id")
    suspend fun stockDelivery(id: String): StockDeliveryEntity?

    @Query("SELECT * FROM stock_deliveries ORDER BY deliveredAtMillis DESC LIMIT 500")
    fun observeStockDeliveries(): Flow<List<StockDeliveryEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertExpense(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses ORDER BY occurredAtMillis DESC LIMIT 500")
    fun observeExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun expense(id: String): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRefund(refund: RefundEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRefundItems(items: List<RefundItemEntity>)

    @Query("SELECT * FROM refunds WHERE idempotencyKey = :key LIMIT 1")
    suspend fun refundByKey(key: String): RefundEntity?

    @Query("SELECT COALESCE(SUM(quantityMilliUnits), 0) FROM refund_items WHERE saleItemId = :saleItemId")
    suspend fun refundedQuantity(saleItemId: String): Long

    @Query("SELECT COALESCE(SUM(amountCentavos), 0) FROM refund_items WHERE saleItemId = :saleItemId")
    suspend fun refundedAmount(saleItemId: String): Long

    @Query("SELECT * FROM sales WHERE id = :id")
    suspend fun sale(id: String): SaleEntity?

    @Query("SELECT * FROM customer_payments WHERE idempotencyKey = :key LIMIT 1")
    suspend fun customerPaymentByKey(key: String): CustomerPaymentEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAudit(entry: AuditLogEntity)

    @Query("SELECT * FROM sales WHERE idempotencyKey = :key LIMIT 1")
    suspend fun saleByIdempotencyKey(key: String): SaleEntity?

    @Query("SELECT p.id, p.name, p.phone, COALESCE(SUM(l.deltaCentavos), 0) AS balanceCentavos FROM customers p LEFT JOIN customer_ledger_entries l ON l.customerId = p.id WHERE p.active = 1 GROUP BY p.id ORDER BY p.name COLLATE NOCASE")
    fun observeCustomerBalances(): Flow<List<CustomerBalance>>

    @Query("SELECT * FROM customers WHERE active = 1 ORDER BY name COLLATE NOCASE")
    fun observeCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT COALESCE(SUM(deltaCentavos), 0) FROM customer_ledger_entries WHERE customerId = :id")
    suspend fun customerBalance(id: String): Long

    @Query("SELECT * FROM customer_ledger_entries WHERE customerId = :id ORDER BY occurredAtMillis DESC")
    fun observeCustomerLedger(id: String): Flow<List<CustomerLedgerEntryEntity>>

    @Query("SELECT * FROM inventory_movements WHERE productId = :id ORDER BY occurredAtMillis DESC LIMIT 250")
    fun observeInventoryMovements(id: String): Flow<List<InventoryMovementEntity>>

    @Query("SELECT * FROM sales ORDER BY createdAtMillis DESC LIMIT 500")
    fun observeSales(): Flow<List<SaleEntity>>

    @Query("SELECT s.id, s.createdAtMillis, s.totalCentavos, s.status, c.name AS customerName FROM sales s LEFT JOIN customers c ON c.id = s.customerId ORDER BY s.createdAtMillis DESC LIMIT 200")
    fun observeSaleSummaries(): Flow<List<SaleSummary>>

    @Query("SELECT * FROM sale_items WHERE saleId = :saleId")
    suspend fun saleItems(saleId: String): List<SaleItemEntity>

    @Query("SELECT * FROM payments WHERE saleId = :saleId")
    suspend fun salePayments(saleId: String): List<PaymentEntity>

    @Query("SELECT COALESCE(SUM(totalCentavos - COALESCE((SELECT SUM(r.refundedCentavos) FROM refunds r WHERE r.saleId = sales.id), 0)), 0) FROM sales WHERE status = 'COMPLETED' AND createdAtMillis >= :from AND createdAtMillis < :until")
    fun observeSalesTotal(from: Long, until: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(si.lineCostCentavos), 0) - COALESCE((SELECT SUM(ROUND(ri.quantityMilliUnits * 1.0 * rsi.lineCostCentavos / rsi.quantityMilliUnits)) FROM refund_items ri INNER JOIN sale_items rsi ON rsi.id = ri.saleItemId INNER JOIN sales rs ON rs.id = rsi.saleId WHERE rs.status = 'COMPLETED' AND rs.createdAtMillis >= :from AND rs.createdAtMillis < :until AND ri.restock = 1), 0) FROM sale_items si INNER JOIN sales s ON s.id = si.saleId WHERE s.status = 'COMPLETED' AND s.createdAtMillis >= :from AND s.createdAtMillis < :until")
    fun observeSoldCostTotal(from: Long, until: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountCentavos), 0) FROM expenses WHERE occurredAtMillis >= :from AND occurredAtMillis < :until")
    fun observeExpenseTotal(from: Long, until: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountAppliedCentavos), 0) FROM payments WHERE method = :method AND createdAtMillis >= :from AND createdAtMillis < :until")
    fun observePaymentTotal(method: String, from: Long, until: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(amountCentavos), 0) FROM customer_payments WHERE paymentMethod = :method AND receivedAtMillis >= :from AND receivedAtMillis < :until")
    fun observeCustomerPaymentTotal(method: String, from: Long, until: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(refundedCentavos), 0) FROM refunds WHERE refundMethod = :method AND createdAtMillis >= :from AND createdAtMillis < :until")
    fun observeRefundTotal(method: String, from: Long, until: Long): Flow<Long>

    @Query("SELECT COALESCE(SUM(totalCentavos), 0) FROM sales WHERE status = 'COMPLETED' AND createdAtMillis >= :from AND createdAtMillis < :until")
    suspend fun salesTotal(from: Long, until: Long): Long

    @Query("SELECT COALESCE(SUM(amountAppliedCentavos), 0) FROM payments WHERE method = :method AND createdAtMillis >= :from AND createdAtMillis < :until")
    suspend fun paymentTotal(method: String, from: Long, until: Long): Long

    @Query("SELECT COALESCE(SUM(si.lineCostCentavos), 0) FROM sale_items si INNER JOIN sales s ON s.id = si.saleId WHERE s.status = 'COMPLETED' AND s.createdAtMillis >= :from AND s.createdAtMillis < :until")
    suspend fun soldCostTotal(from: Long, until: Long): Long

    @Query("SELECT COALESCE(SUM(totalCentavos), 0) FROM sales WHERE status = 'COMPLETED' AND createdAtMillis >= :from AND createdAtMillis < :until")
    suspend fun reportGrossSales(from: Long, until: Long): Long

    @Query("SELECT COUNT(*) FROM sales WHERE status = 'COMPLETED' AND createdAtMillis >= :from AND createdAtMillis < :until")
    suspend fun reportSaleCount(from: Long, until: Long): Int

    @Query("SELECT COALESCE(SUM(refundedCentavos), 0) FROM refunds WHERE createdAtMillis >= :from AND createdAtMillis < :until")
    suspend fun reportRefundTotal(from: Long, until: Long): Long

    @Query("SELECT COALESCE(SUM(si.lineCostCentavos), 0) FROM sale_items si INNER JOIN sales s ON s.id = si.saleId WHERE s.status = 'COMPLETED' AND s.createdAtMillis >= :from AND s.createdAtMillis < :until")
    suspend fun reportSoldCost(from: Long, until: Long): Long

    @Query("SELECT COALESCE(SUM(ROUND(ri.quantityMilliUnits * 1.0 * si.lineCostCentavos / si.quantityMilliUnits)), 0) FROM refund_items ri INNER JOIN refunds r ON r.id = ri.refundId INNER JOIN sale_items si ON si.id = ri.saleItemId WHERE ri.restock = 1 AND r.createdAtMillis >= :from AND r.createdAtMillis < :until")
    suspend fun reportRestockedCost(from: Long, until: Long): Long

    @Query("SELECT label, SUM(amountCentavos) AS amountCentavos FROM (SELECT p.method AS label, p.amountAppliedCentavos AS amountCentavos FROM payments p INNER JOIN sales s ON s.id = p.saleId WHERE s.status = 'COMPLETED' AND p.createdAtMillis >= :from AND p.createdAtMillis < :until UNION ALL SELECT cp.paymentMethod AS label, cp.amountCentavos AS amountCentavos FROM customer_payments cp WHERE cp.receivedAtMillis >= :from AND cp.receivedAtMillis < :until UNION ALL SELECT r.refundMethod AS label, -r.refundedCentavos AS amountCentavos FROM refunds r WHERE r.createdAtMillis >= :from AND r.createdAtMillis < :until) GROUP BY label ORDER BY label")
    suspend fun reportPaymentBreakdown(from: Long, until: Long): List<LabelAmount>

    @Query("SELECT salesChannel, SUM(amountCentavos) AS amountCentavos, SUM(saleCount) AS saleCount FROM (SELECT s.salesChannel AS salesChannel, s.totalCentavos AS amountCentavos, 1 AS saleCount FROM sales s WHERE s.status = 'COMPLETED' AND s.createdAtMillis >= :from AND s.createdAtMillis < :until UNION ALL SELECT s.salesChannel AS salesChannel, -r.refundedCentavos AS amountCentavos, 0 AS saleCount FROM refunds r INNER JOIN sales s ON s.id = r.saleId WHERE s.status = 'COMPLETED' AND r.createdAtMillis >= :from AND r.createdAtMillis < :until) GROUP BY salesChannel ORDER BY salesChannel")
    suspend fun reportSalesByChannel(from: Long, until: Long): List<SalesChannelAmount>

    @Query("SELECT si.productId AS productId, si.productNameSnapshot AS productName, MAX(0, SUM(si.baseQuantityMilliUnits) - COALESCE(SUM(refund.refundedBaseQuantityMilliUnits), 0)) AS quantityMilliUnits, SUM(si.lineTotalCentavos) - COALESCE(SUM(refund.refundedCentavos), 0) AS netSalesCentavos FROM sale_items si INNER JOIN sales s ON s.id = si.saleId LEFT JOIN (SELECT ri.saleItemId AS saleItemId, SUM(ROUND(ri.quantityMilliUnits * 1.0 * original.baseQuantityMilliUnits / original.quantityMilliUnits)) AS refundedBaseQuantityMilliUnits, SUM(ri.amountCentavos) AS refundedCentavos FROM refund_items ri INNER JOIN refunds r ON r.id = ri.refundId INNER JOIN sale_items original ON original.id = ri.saleItemId WHERE r.createdAtMillis >= :from AND r.createdAtMillis < :until GROUP BY ri.saleItemId) refund ON refund.saleItemId = si.id WHERE s.status = 'COMPLETED' AND s.createdAtMillis >= :from AND s.createdAtMillis < :until GROUP BY si.productId, si.productNameSnapshot ORDER BY netSalesCentavos DESC LIMIT 10")
    suspend fun reportTopProducts(from: Long, until: Long): List<TopProductReportRow>

    @Query("SELECT date(createdAtMillis / 1000, 'unixepoch', 'localtime') AS day, SUM(totalCentavos) AS salesCentavos FROM sales WHERE status = 'COMPLETED' AND createdAtMillis >= :from AND createdAtMillis < :until GROUP BY day ORDER BY day")
    suspend fun reportDailySales(from: Long, until: Long): List<DailySalesReportRow>

    @Query("SELECT category AS label, SUM(amountCentavos) AS amountCentavos FROM expenses WHERE occurredAtMillis >= :from AND occurredAtMillis < :until GROUP BY category ORDER BY amountCentavos DESC")
    suspend fun reportExpensesByCategory(from: Long, until: Long): List<LabelAmount>

    @Query("SELECT COALESCE(supplierNameSnapshot, 'Unspecified supplier') AS label, SUM(totalCostCentavos) AS amountCentavos FROM stock_deliveries WHERE deliveredAtMillis >= :from AND deliveredAtMillis < :until GROUP BY supplierNameSnapshot ORDER BY amountCentavos DESC")
    suspend fun reportSupplierPurchases(from: Long, until: Long): List<LabelAmount>

    @Query("SELECT COALESCE(SUM(MAX(balanceCentavos, 0)), 0) FROM (SELECT SUM(deltaCentavos) AS balanceCentavos FROM customer_ledger_entries GROUP BY customerId)")
    suspend fun reportOutstandingUtang(): Long

    @Query("SELECT onHandMilliUnits, purchaseCostCentavos FROM inventory_balances INNER JOIN products ON products.id = inventory_balances.productId WHERE products.active = 1")
    suspend fun reportInventoryValuationRows(): List<InventoryValuationRow>

    @Query("SELECT p.id AS productId, p.name AS productName, COALESCE(b.onHandMilliUnits, 0) AS onHandMilliUnits, p.minimumStockMilliUnits AS minimumStockMilliUnits FROM products p LEFT JOIN inventory_balances b ON b.productId = p.id WHERE p.active = 1 AND COALESCE(b.onHandMilliUnits, 0) <= p.minimumStockMilliUnits ORDER BY p.name COLLATE NOCASE")
    suspend fun reportLowStockProducts(): List<LowStockReportRow>

    @Query("SELECT COALESCE(SUM(amountCentavos), 0) FROM expenses WHERE occurredAtMillis >= :from AND occurredAtMillis < :until")
    suspend fun reportExpenseTotal(from: Long, until: Long): Long

    @Query("SELECT COUNT(*) FROM products WHERE active = 1 AND id IN (SELECT productId FROM inventory_balances WHERE onHandMilliUnits <= products.minimumStockMilliUnits)")
    fun observeLowStockCount(): Flow<Int>

}
