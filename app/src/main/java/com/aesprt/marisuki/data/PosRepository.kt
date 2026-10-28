package com.aesprt.marisuki.data

import androidx.room.withTransaction
import com.aesprt.marisuki.data.local.PosDatabase
import com.aesprt.marisuki.data.local.dao.CustomerBalance
import com.aesprt.marisuki.data.local.dao.PosDao
import com.aesprt.marisuki.data.local.dao.ProductSummary
import com.aesprt.marisuki.data.local.dao.SaleSummary
import com.aesprt.marisuki.data.local.dao.LabelAmount
import com.aesprt.marisuki.data.local.dao.SalesChannelAmount
import com.aesprt.marisuki.data.local.dao.TopProductReportRow
import com.aesprt.marisuki.data.local.dao.DailySalesReportRow
import com.aesprt.marisuki.data.local.dao.LowStockReportRow
import com.aesprt.marisuki.data.local.entity.*
import com.aesprt.marisuki.domain.model.*
import com.aesprt.marisuki.domain.auth.AuthenticatedActor
import com.aesprt.marisuki.domain.auth.AuthFailure
import com.aesprt.marisuki.domain.auth.PosAccessControl
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.domain.auth.PriceApprovalPreview
import com.aesprt.marisuki.domain.auth.RestrictedActionApproval
import com.aesprt.marisuki.data.auth.AuthException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.io.BufferedWriter
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi

class PosRepository(
    private val database: PosDatabase,
    private val dao: PosDao,
    private val accessControl: PosAccessControl,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val actionApproval = accessControl as? RestrictedActionApproval
    fun observeProducts(): Flow<List<ProductSummary>> = securedProducts(dao.observeProducts())
    fun searchProducts(query: String): Flow<List<ProductSummary>> = securedProducts(dao.searchProducts(query.trim()))
    fun observeCustomers(): Flow<List<CustomerEntity>> = securedList(PosPermission.VIEW_CUSTOMERS, dao.observeCustomers())
    fun observeCustomerBalances(): Flow<List<CustomerBalance>> = securedList(PosPermission.VIEW_CUSTOMER_BALANCES, dao.observeCustomerBalances())
    fun observeCustomerLedger(customerId: String): Flow<List<CustomerLedgerEntryEntity>> =
        securedList(PosPermission.VIEW_CUSTOMER_BALANCES, dao.observeCustomerLedger(customerId))
    fun observeSales(): Flow<List<SaleSummary>> = securedList(PosPermission.VIEW_SALES_HISTORY, dao.observeSaleSummaries())
    fun observeSaleHistoryDetails(saleId: String) = securedScalar(
        PosPermission.VIEW_SALES_HISTORY, dao.observeSaleHistoryDetails(saleId), null,
    )
    suspend fun saleCashierName(accountId: String): String? {
        requirePermission(PosPermission.VIEW_SALES_HISTORY)
        return dao.saleCashierName(accountId)
    }
    fun observeLowStockCount(): Flow<Int> = securedScalar(PosPermission.VIEW_STOCK, dao.observeLowStockCount(), 0)
    fun observeSuppliers(): Flow<List<SupplierEntity>> = securedList(PosPermission.MANAGE_SUPPLIERS, dao.observeSuppliers())
    fun observeStockDeliveries(): Flow<List<StockDeliveryEntity>> = securedList(PosPermission.MANAGE_SUPPLIERS, dao.observeStockDeliveries())
    fun observeExpenses(): Flow<List<ExpenseEntity>> = securedList(PosPermission.VIEW_EXPENSES, dao.observeExpenses())

    private fun <T> securedList(permission: PosPermission, source: Flow<List<T>>): Flow<List<T>> = flow {
        try { accessControl.requirePermission(permission) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { emit(emptyList()); return@flow }
        emitAll(source)
    }

    private fun securedProducts(source: Flow<List<ProductSummary>>): Flow<List<ProductSummary>> = flow {
        try { requirePermission(PosPermission.VIEW_PRODUCTS) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { emit(emptyList()); return@flow }
        val canSeeCost = runCatching { requirePermission(PosPermission.VIEW_PURCHASE_COST) }.isSuccess
        emitAll(source.map { rows -> if (canSeeCost) rows else rows.map { it.copy(purchaseCostCentavos = 0L) } })
    }

    private fun <T> securedScalar(permission: PosPermission, source: Flow<T>, deniedValue: T): Flow<T> = flow {
        try { accessControl.requirePermission(permission) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Throwable) { emit(deniedValue); return@flow }
        emitAll(source)
    }

    private suspend fun requirePermission(permission: PosPermission): AuthenticatedActor = accessControl.requirePermission(permission)

    suspend fun requirePrinterManagement() {
        requirePermission(PosPermission.MANAGE_PRINTERS)
    }

    suspend fun salesReport(fromMillis: Long, untilMillis: Long): SalesReport {
        require(fromMillis >= 0 && fromMillis < untilMillis) { "Choose a valid report date range." }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowStart = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val salesPermission = if (fromMillis >= todayStart && untilMillis <= tomorrowStart) {
            PosPermission.VIEW_TODAYS_SALES
        } else {
            PosPermission.VIEW_HISTORICAL_SALES
        }
        val salesVisible = hasPermission(salesPermission)
        val expensesVisible = hasPermission(PosPermission.VIEW_EXPENSES)
        val stockValuationVisible = hasPermission(PosPermission.VIEW_STOCK_VALUATION)
        val customerBalancesVisible = hasPermission(PosPermission.VIEW_CUSTOMER_BALANCES)
        val stockVisible = hasPermission(PosPermission.VIEW_STOCK)
        val suppliersVisible = hasPermission(PosPermission.MANAGE_SUPPLIERS)
        if (!salesVisible && !expensesVisible && !stockValuationVisible && !customerBalancesVisible && !stockVisible && !suppliersVisible) {
            throw AuthException(AuthFailure.Validation("Your account does not have permission to view these reports."))
        }

        val grossSales = if (salesVisible) dao.reportGrossSales(fromMillis, untilMillis) else 0L
        val refunds = if (salesVisible) dao.reportRefundTotal(fromMillis, untilMillis) else 0L
        val grossProfit = if (salesVisible && hasPermission(PosPermission.VIEW_PROFIT)) {
            Math.subtractExact(
                Math.subtractExact(grossSales, refunds),
                Math.subtractExact(dao.reportSoldCost(fromMillis, untilMillis), dao.reportRestockedCost(fromMillis, untilMillis)),
            )
        } else null
        val expenses = if (expensesVisible) dao.reportExpenseTotal(fromMillis, untilMillis) else null
        val inventoryValue = if (stockValuationVisible) {
            dao.reportInventoryValuationRows().fold(0L) { total, row ->
                val rowValue = BigDecimal.valueOf(row.onHandMilliUnits)
                    .multiply(BigDecimal.valueOf(row.purchaseCostCentavos))
                    .divide(BigDecimal.valueOf(SCALE), 0, RoundingMode.HALF_UP)
                    .longValueExact()
                Math.addExact(total, rowValue)
            }
        } else null
        return SalesReport(
            fromMillis = fromMillis,
            untilMillis = untilMillis,
            grossSalesCentavos = grossSales,
            refundsCentavos = refunds,
            netSalesCentavos = Math.subtractExact(grossSales, refunds),
            salesVisible = salesVisible,
            saleCount = if (salesVisible) dao.reportSaleCount(fromMillis, untilMillis) else 0,
            grossProfitCentavos = grossProfit,
            expensesCentavos = expenses,
            payments = if (salesVisible) dao.reportPaymentBreakdown(fromMillis, untilMillis) else emptyList(),
            channels = if (salesVisible) dao.reportSalesByChannel(fromMillis, untilMillis) else emptyList(),
            dailySales = if (salesVisible) dao.reportDailySales(fromMillis, untilMillis) else emptyList(),
            topProducts = if (salesVisible) dao.reportTopProducts(fromMillis, untilMillis) else emptyList(),
            expenseCategories = if (expenses != null) dao.reportExpensesByCategory(fromMillis, untilMillis) else emptyList(),
            outstandingUtangCentavos = if (customerBalancesVisible) dao.reportOutstandingUtang() else null,
            inventoryValuationCentavos = inventoryValue,
            lowStockProducts = if (stockVisible) dao.reportLowStockProducts() else null,
            supplierPurchases = if (suppliersVisible) dao.reportSupplierPurchases(fromMillis, untilMillis) else emptyList(),
        )
    }

    suspend fun exportSalesReportCsv(fromMillis: Long, untilMillis: Long, output: OutputStream) {
        requirePermission(PosPermission.EXPORT_REPORTS)
        // Rebuild the report under the current session's permissions. The screen may
        // have been left open while an owner changed or disabled this account.
        val report = salesReport(fromMillis, untilMillis)
        BufferedWriter(OutputStreamWriter(output, StandardCharsets.UTF_8)).use { writer ->
            writer.appendLine("MariSuki POS report")
            writer.appendLine("From,${report.fromMillis}")
            writer.appendLine("Until,${report.untilMillis}")
            writer.appendLine("Metric,Centavos")
            if (report.salesVisible) {
                writer.appendLine("Gross sales,${report.grossSalesCentavos}")
                writer.appendLine("Refunds,${report.refundsCentavos}")
                writer.appendLine("Net sales,${report.netSalesCentavos}")
                writer.appendLine("Completed sales,${report.saleCount}")
                report.grossProfitCentavos?.let { writer.appendLine("Estimated gross profit,$it") }
            }
            report.expensesCentavos?.let { writer.appendLine("Expenses,$it") }
            report.outstandingUtangCentavos?.let { writer.appendLine("Outstanding customer balances,$it") }
            report.inventoryValuationCentavos?.let { writer.appendLine("Inventory valuation,$it") }
            writer.appendLine()
            if (report.salesVisible) {
                writer.appendLine("Payment channel,Amount centavos")
                report.payments.forEach { writer.appendLine("${csvCell(it.label)},${it.amountCentavos}") }
                writer.appendLine()
                writer.appendLine("Sales type,Amount centavos,Sale count")
                report.channels.forEach { writer.appendLine("${csvCell(it.salesChannel)},${it.amountCentavos},${it.saleCount}") }
                writer.appendLine()
                writer.appendLine("Best-selling products,Base quantity milli-units,Net sales centavos")
                report.topProducts.forEach { writer.appendLine("${csvCell(it.productName)},${it.quantityMilliUnits},${it.netSalesCentavos}") }
            }
            if (report.expenseCategories.isNotEmpty()) {
                writer.appendLine()
                writer.appendLine("Expense category,Amount centavos")
                report.expenseCategories.forEach { writer.appendLine("${csvCell(it.label)},${it.amountCentavos}") }
            }
            if (report.supplierPurchases.isNotEmpty()) {
                writer.appendLine()
                writer.appendLine("Supplier,Amount centavos")
                report.supplierPurchases.forEach { writer.appendLine("${csvCell(it.label)},${it.amountCentavos}") }
            }
            report.lowStockProducts?.let { rows ->
                writer.appendLine()
                writer.appendLine("Low stock product,On hand milli-units,Minimum milli-units")
                rows.forEach { writer.appendLine("${csvCell(it.productName)},${it.onHandMilliUnits},${it.minimumStockMilliUnits}") }
            }
        }
    }

    private suspend fun hasPermission(permission: PosPermission): Boolean = try {
        requirePermission(permission)
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: AuthException) {
        false
    }

    private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeTodayStats(): Flow<TodayStats> {
        return flow {
            val allowed = runCatching { requirePermission(PosPermission.VIEW_TODAYS_SALES) }.getOrNull()
            if (allowed == null) {
                emit(null)
                return@flow
            }
            val canSeeProfit = runCatching { requirePermission(PosPermission.VIEW_PROFIT) }.isSuccess
            val canSeeExpenses = runCatching { requirePermission(PosPermission.VIEW_EXPENSES) }.isSuccess
            val canSeeStock = runCatching { requirePermission(PosPermission.VIEW_STOCK) }.isSuccess
            while (true) {
                emit(Triple(canSeeProfit, canSeeExpenses, canSeeStock))
                val zone = ZoneId.systemDefault()
                val now = LocalDate.now(zone)
                val start = now.atStartOfDay(zone).toInstant().toEpochMilli()
                val end = now.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                val untilNextDay = (end - System.currentTimeMillis()).coerceAtLeast(1L)
                delay(untilNextDay)
            }
        }.flatMapLatest { permissions ->
            if (permissions == null) return@flatMapLatest flowOf(TodayStats())
            val (canSeeProfit, canSeeExpenses, canSeeStock) = permissions
            val zone = ZoneId.systemDefault()
            val now = LocalDate.now(zone)
            val start = now.atStartOfDay(zone).toInstant().toEpochMilli()
            val end = now.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            fun collected(method: String) = combine(
                dao.observePaymentTotal(method, start, end),
                dao.observeCustomerPaymentTotal(method, start, end),
                dao.observeRefundTotal(method, start, end),
            ) { salePayments, creditPayments, refunds -> Math.subtractExact(Math.addExact(salePayments, creditPayments), refunds) }
            val costs = combine(dao.observeSoldCostTotal(start, end), dao.observeExpenseTotal(start, end)) { cost, expenses -> cost to expenses }
            val financials = combine(
                dao.observeSalesTotal(start, end),
                collected("CASH"),
                collected("GCASH"),
                collected("MAYA"),
                costs,
            ) { sales, cash, gcash, maya, costValues ->
                listOf(sales, cash, gcash, maya, costValues.first, costValues.second)
            }
            combine(financials, dao.observeLowStockCount()) { values, lowStock ->
                TodayStats(
                    salesCentavos = values[0],
                    cashCentavos = values[1],
                    gcashCentavos = values[2],
                    mayaCentavos = values[3],
                    estimatedGrossProfitCentavos = values[0] - values[4],
                    expensesCentavos = values[5],
                    lowStockCount = if (canSeeStock) lowStock else 0,
                )
            }.map { stats -> stats.copy(
                estimatedGrossProfitCentavos = if (canSeeProfit) stats.estimatedGrossProfitCentavos else 0L,
                expensesCentavos = if (canSeeExpenses) stats.expensesCentavos else 0L,
            ) }
        }
    }
    suspend fun productUnits(productId: String): List<ProductUnitEntity> { requirePermission(PosPermission.VIEW_PRODUCTS); return dao.productUnits(productId) }
    suspend fun saleItems(saleId: String): List<SaleItemEntity> { requirePermission(PosPermission.VIEW_SALES_HISTORY); return dao.saleItems(saleId) }
    suspend fun salePayments(saleId: String): List<PaymentEntity> { requirePermission(PosPermission.VIEW_SALES_HISTORY); return dao.salePayments(saleId) }
    suspend fun saleRecord(saleId: String): SaleEntity? { requirePermission(PosPermission.VIEW_SALES_HISTORY); return dao.sale(saleId) }
    suspend fun saleReceipt(saleId: String, customerName: String?): SaleReceiptData? {
        requirePermission(PosPermission.REPRINT_SLIP)
        val sale = dao.sale(saleId) ?: return null
        return SaleReceiptData(sale, dao.saleItems(saleId), dao.salePayments(saleId), customerName)
    }
    suspend fun productByBarcode(barcode: String): ProductSummary? {
        requirePermission(PosPermission.VIEW_PRODUCTS)
        val product = dao.productByBarcode(barcode.trim()) ?: return null
        val summary = dao.productSummary(product.id) ?: return null
        return if (runCatching { requirePermission(PosPermission.VIEW_PURCHASE_COST) }.isSuccess) summary else summary.copy(purchaseCostCentavos = 0L)
    }

    suspend fun ensureProductBarcode(productId: String): String = database.withTransaction {
        val actor = requirePermission(PosPermission.VIEW_PRODUCTS)
        val product = dao.product(productId) ?: throw PosError.ProductNotFound
        if (!product.active) throw PosError.ProductNotFound
        product.barcode?.let { return@withTransaction it }
        repeat(8) {
            val code = "MSK" + UUID.randomUUID().toString().replace("-", "").take(12).uppercase()
            if (!dao.barcodeExists(code)) {
                if (dao.assignProductBarcode(productId, code, clock()) == 0) throw PosError.ProductNotFound
                dao.insertAudit(AuditLogEntity(UUID.randomUUID().toString(), "INTERNAL_BARCODE_ASSIGNED", productId, "Internal barcode assigned", clock(), actor.accountId))
                return@withTransaction code
            }
        }
        throw PosError.DuplicateName
    }

    suspend fun recordCustomerPayment(
        customerId: String,
        amountCentavos: Long,
        method: PaymentMethod,
        idempotencyKey: String,
    ) = database.withTransaction {
        val actor = requirePermission(PosPermission.ACCEPT_UTANG_PAYMENTS)
        val key = idempotencyKey.trim()
        if (key.isBlank() || amountCentavos <= 0 || dao.customer(customerId) == null) throw PosError.PaymentInvalid
        if (dao.customerPaymentByKey(key) != null) return@withTransaction
        val balance = dao.customerBalance(customerId)
        if (balance <= 0 || amountCentavos > balance) throw PosError.PaymentInvalid
        val now = clock()
        val paymentId = UUID.randomUUID().toString()
        dao.insertCustomerPayment(CustomerPaymentEntity(paymentId, customerId, key, amountCentavos, method.name, now))
        dao.insertCustomerLedger(CustomerLedgerEntryEntity(UUID.randomUUID().toString(), customerId, -amountCentavos, "CUSTOMER_PAYMENT", paymentId, now, "Utang payment"))
        dao.insertAudit(AuditLogEntity(UUID.randomUUID().toString(), "CUSTOMER_PAYMENT", customerId, "Utang payment recorded", now, actor.accountId))
    }

    fun observeCategoryNames() = securedList(PosPermission.VIEW_PRODUCTS, dao.observeCategoryNames())
    fun observeUnitNames(selling: Boolean): Flow<List<String>> {
        val prefix = if (selling) "catalog.selling_unit." else "catalog.stock_unit."
        val existing = if (selling) dao.observeSellingUnitNames() else dao.observeStockUnitNames()
        val defaults = if (selling) listOf("Pack", "Case", "Box", "Dozen", "Sack") else listOf("Piece", "Pack", "Case", "Kilo", "Gram", "Liter", "Milliliter", "Bottle", "Can", "Box", "Bag", "Sachet", "Loaf", "Dozen")
        return securedList(PosPermission.VIEW_PRODUCTS, combine(existing, dao.observeCatalogLabels(prefix)) { used, saved ->
            (defaults + used + saved).distinctBy { it.lowercase(java.util.Locale.ROOT) }.sortedBy { it.lowercase(java.util.Locale.ROOT) }
        })
    }

    suspend fun saveCatalogChoice(kind: String, name: String): String = database.withTransaction {
        requirePermission(PosPermission.VIEW_PRODUCTS)
        try { requirePermission(PosPermission.ADD_PRODUCT) } catch (_: AuthException) { requirePermission(PosPermission.EDIT_PRODUCT) }
        val label = name.trim()
        if (label.isBlank() || label.length > 80 || label.any(Char::isISOControl)) throw PosError.InvalidProduct
        when (kind) {
            "category" -> dao.categoryByName(label)?.name ?: label.also { dao.insertCategory(CategoryEntity(it, clock(), UUID.randomUUID().toString())) }
            "stock", "selling" -> {
                val key = "catalog.${if (kind == "stock") "stock" else "selling"}_unit.${label.lowercase(java.util.Locale.ROOT)}"
                dao.catalogSetting(key) ?: label.also { dao.saveCatalogSetting(AppSettingEntity(key, it)) }
            }
            else -> throw PosError.InvalidProduct
        }
    }

    suspend fun reviewProductImport(preview: com.aesprt.marisuki.data.importing.ImportPreview): com.aesprt.marisuki.data.importing.ImportPreview {
        requirePermission(PosPermission.ADD_PRODUCT)
        val canCost = try { requirePermission(PosPermission.EDIT_PURCHASE_COST); true } catch (_: AuthException) { false }
        val canStock = try { requirePermission(PosPermission.ADD_STOCK); true } catch (_: AuthException) { false }
        val existing = preview.rows.mapNotNull { it.input?.barcode }.distinct().filter { dao.barcodeExists(it) }.toSet()
        return preview.copy(rows = preview.rows.map { row ->
            val added = mutableListOf<com.aesprt.marisuki.data.importing.ImportIssue>()
            if (row.input?.barcode in existing) added += com.aesprt.marisuki.data.importing.ImportIssue(row.row, "Barcode", com.aesprt.marisuki.data.importing.ImportProblem.DUPLICATE_BARCODE)
            if (!canCost && (row.input?.purchaseCostCentavos ?: 0) > 0) added += com.aesprt.marisuki.data.importing.ImportIssue(row.row, "Purchase cost", com.aesprt.marisuki.data.importing.ImportProblem.PERMISSION)
            if (!canStock && (row.input?.initialStockMilliUnits ?: 0) > 0) added += com.aesprt.marisuki.data.importing.ImportIssue(row.row, "Starting stock", com.aesprt.marisuki.data.importing.ImportProblem.PERMISSION)
            row.copy(issues = (row.issues + added).distinct())
        })
    }

    suspend fun importProducts(inputs: List<NewProduct>): com.aesprt.marisuki.data.importing.ImportResult = database.withTransaction {
        val actor = requirePermission(PosPermission.ADD_PRODUCT)
        if (inputs.any { it.purchaseCostCentavos > 0 }) requirePermission(PosPermission.EDIT_PURCHASE_COST)
        if (inputs.any { it.initialStockMilliUnits > 0 }) requirePermission(PosPermission.ADD_STOCK)
        if (inputs.isEmpty() || inputs.size > com.aesprt.marisuki.data.importing.ProductImport.MAX_ROWS) throw PosError.InvalidProduct
        val canonical = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(canonical).use { output ->
            output.writeInt(inputs.size)
            inputs.sortedBy { it.toString() }.forEach { product ->
                output.writeUTF(product.name.trim()); output.writeUTF(product.barcode?.trim().orEmpty()); output.writeUTF(product.categoryName?.trim().orEmpty())
                output.writeUTF(product.baseUnitLabel.trim()); output.writeLong(product.baseUnitPriceCentavos); output.writeLong(product.purchaseCostCentavos)
                output.writeLong(product.initialStockMilliUnits); output.writeLong(product.minimumStockMilliUnits); output.writeBoolean(product.wholeBaseUnitsOnly)
                output.writeUTF(product.imageFileName.orEmpty()); output.writeInt(product.extraUnits.size)
                product.extraUnits.forEach { unit -> output.writeUTF(unit.label.trim()); output.writeLong(unit.baseQuantityMilliUnits); output.writeLong(unit.priceCentavos); output.writeBoolean(unit.wholeQuantityOnly) }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray()).joinToString("") { "%02x".format(it) }
        val key = "catalog.import.$digest"
        val previous = dao.catalogSetting(key)
        if (previous != null) return@withTransaction com.aesprt.marisuki.data.importing.ImportResult(previous.toInt(), true)
        inputs.forEach { insertProductRecord(it, actor) }
        dao.saveCatalogSetting(AppSettingEntity(key, inputs.size.toString()))
        com.aesprt.marisuki.data.importing.ImportResult(inputs.size, false)
    }

    suspend fun createProduct(input: NewProduct): String = database.withTransaction {
        val actor = requirePermission(PosPermission.ADD_PRODUCT)
        if (input.purchaseCostCentavos > 0) requirePermission(PosPermission.EDIT_PURCHASE_COST)
        if (input.initialStockMilliUnits > 0) requirePermission(PosPermission.ADD_STOCK)
        insertProductRecord(input, actor)
    }

    private suspend fun insertProductRecord(input: NewProduct, actor: AuthenticatedActor): String {
        val name = input.name.trim()
        val barcode = input.barcode?.trim()?.ifBlank { null }
        val baseLabel = input.baseUnitLabel.trim()
        if (name.isBlank() || baseLabel.isBlank() || input.baseUnitPriceCentavos < 0 ||
            input.purchaseCostCentavos < 0 || input.initialStockMilliUnits < 0 ||
            input.minimumStockMilliUnits < 0 ||
            (input.wholeBaseUnitsOnly && input.initialStockMilliUnits % SCALE != 0L)
        ) throw PosError.InvalidProduct

        val allLabels = (listOf(baseLabel) + input.extraUnits.map { it.label.trim() })
        if (allLabels.any(String::isBlank) || allLabels.map { it.lowercase() }.distinct().size != allLabels.size) {
            throw PosError.InvalidProduct
        }
        if (input.extraUnits.any {
                it.baseQuantityMilliUnits <= 0 || it.priceCentavos < 0
            }
        ) throw PosError.InvalidProduct

        val now = clock()
        val id = UUID.randomUUID().toString()
        val categoryId = input.categoryName?.trim()?.takeIf(String::isNotBlank)?.let { categoryName ->
            dao.categoryByName(categoryName)?.id ?: UUID.randomUUID().toString().also { newId ->
                dao.insertCategory(CategoryEntity(categoryName, now, newId))
            }
        }
        val product = ProductEntity(
            id = id,
            name = name,
            barcode = barcode,
            categoryId = categoryId,
            baseUnitLabel = baseLabel,
            baseUnitPriceCentavos = input.baseUnitPriceCentavos,
            purchaseCostCentavos = input.purchaseCostCentavos,
            minimumStockMilliUnits = input.minimumStockMilliUnits,
            wholeBaseUnitsOnly = input.wholeBaseUnitsOnly,
            active = true,
            createdAtMillis = now,
            updatedAtMillis = now,
            imageFileName = input.imageFileName,
        )
        dao.insertProduct(product)
        dao.insertProductUnits(
            listOf(
                ProductUnitEntity(UUID.randomUUID().toString(), id, baseLabel, SCALE, input.baseUnitPriceCentavos, input.wholeBaseUnitsOnly),
            ) + input.extraUnits.map { unit ->
                ProductUnitEntity(UUID.randomUUID().toString(), id, unit.label.trim(), unit.baseQuantityMilliUnits, unit.priceCentavos, unit.wholeQuantityOnly)
            },
        )
        dao.insertInventoryBalance(InventoryBalanceEntity(id, input.initialStockMilliUnits, now))
        if (input.initialStockMilliUnits > 0) {
            dao.insertMovement(
                InventoryMovementEntity(
                    UUID.randomUUID().toString(), id, input.initialStockMilliUnits, "OPENING_STOCK", "PRODUCT", id, now, null,
                ),
            )
        }
        rememberProductUnitNames(input)
        dao.insertAudit(AuditLogEntity(UUID.randomUUID().toString(), "PRODUCT_CREATED", id, "Product created", now, actor.accountId))
        return id
    }

    suspend fun requestPriceChangeApproval(productId: String, input: NewProduct): PriceApprovalPreview? {
        val actor = requirePermission(PosPermission.EDIT_PRODUCT)
        val product = dao.product(productId) ?: throw PosError.ProductNotFound
        val originalUnits = dao.productUnits(productId)
        val requestedUnits = requestedUnits(input)
        if (!sellingPriceChanged(originalUnits, requestedUnits)) return null
        if (runCatching { requirePermission(PosPermission.CHANGE_SELLING_PRICE) }.isSuccess) return null
        val currentBalance = dao.inventoryBalance(productId)?.onHandMilliUnits ?: 0L
        val effectiveCost = if (runCatching { requirePermission(PosPermission.EDIT_PURCHASE_COST) }.isSuccess) input.purchaseCostCentavos else product.purchaseCostCentavos
        val beforeValue = originalUnits.sortedBy { it.label.lowercase() }.joinToString("; ") { "${it.label}=${it.priceCentavos}" }
        val afterValue = requestedUnits.sortedBy { it.first.lowercase() }.joinToString("; ") { "${it.first}=${it.third}" }
        val digest = pricePayloadDigest(product, originalUnits, currentBalance, input, effectiveCost)
        val approvalId = (actionApproval ?: throw com.aesprt.marisuki.data.auth.AuthException(com.aesprt.marisuki.domain.auth.AuthFailure.Validation("Owner approval is unavailable.")))
            .requestOwnerApproval(actor.accountId, PosPermission.CHANGE_SELLING_PRICE.name, productId, beforeValue, afterValue, digest)
        return PriceApprovalPreview(approvalId, productId, beforeValue, afterValue, digest)
    }

    suspend fun updateProduct(
        id: String,
        input: NewProduct,
        approvalId: String? = null,
    ): ProductEntity {
        try {
            return database.withTransaction {
        val actor = requirePermission(PosPermission.EDIT_PRODUCT)
        val existing = dao.product(id) ?: throw PosError.ProductNotFound
        val originalUnits = dao.productUnits(id)
        val requestedUnits = requestedUnits(input)
        val priceChanged = sellingPriceChanged(originalUnits, requestedUnits)
        val hasDirectPricePermission = runCatching { requirePermission(PosPermission.CHANGE_SELLING_PRICE) }.isSuccess
        val canEditCost = runCatching { requirePermission(PosPermission.EDIT_PURCHASE_COST) }.isSuccess
        if (!canEditCost && input.purchaseCostCentavos != 0L && input.purchaseCostCentavos != existing.purchaseCostCentavos) {
            throw com.aesprt.marisuki.data.auth.AuthException(com.aesprt.marisuki.domain.auth.AuthFailure.Validation("You do not have permission to change purchase cost."))
        }
        val effectiveCost = if (canEditCost) input.purchaseCostCentavos else existing.purchaseCostCentavos
        var approvingOwnerId: String? = null
        if (priceChanged && !hasDirectPricePermission) {
            val requestId = approvalId ?: throw com.aesprt.marisuki.data.auth.AuthException(
                com.aesprt.marisuki.domain.auth.AuthFailure.Validation("Owner approval is required to change this selling price."),
            )
            val currentBalance = dao.inventoryBalance(id)?.onHandMilliUnits ?: 0L
            val digest = pricePayloadDigest(existing, originalUnits, currentBalance, input, effectiveCost)
            approvingOwnerId = (actionApproval ?: throw com.aesprt.marisuki.data.auth.AuthException(
                com.aesprt.marisuki.domain.auth.AuthFailure.Validation("Owner approval is unavailable."),
            )).consumeOwnerApproval(
                requestId, actor.accountId, PosPermission.CHANGE_SELLING_PRICE.name, id, digest,
            )
        } else if (approvalId != null) {
            throw com.aesprt.marisuki.data.auth.AuthException(com.aesprt.marisuki.domain.auth.AuthFailure.Validation("This owner approval does not match a pending price change."))
        }
        val name = input.name.trim()
        val baseLabel = input.baseUnitLabel.trim()
        val barcode = input.barcode?.trim()?.ifBlank { null }
        if (name.isBlank() || baseLabel.isBlank() || input.baseUnitPriceCentavos < 0 || effectiveCost < 0 || input.minimumStockMilliUnits < 0) {
            throw PosError.InvalidProduct
        }
        val labels = listOf(baseLabel) + input.extraUnits.map { it.label.trim() }
        if (labels.any(String::isBlank) || labels.map { it.lowercase() }.distinct().size != labels.size ||
            input.extraUnits.any { it.baseQuantityMilliUnits <= 0 || it.priceCentavos < 0 }
        ) throw PosError.InvalidProduct

        val currentBalance = dao.inventoryBalance(id)?.onHandMilliUnits ?: 0L
        if (input.wholeBaseUnitsOnly && currentBalance % SCALE != 0L) throw PosError.InvalidProduct
        val categoryId = input.categoryName?.trim()?.takeIf(String::isNotBlank)?.let { categoryName ->
            dao.categoryByName(categoryName)?.id ?: UUID.randomUUID().toString().also { newId ->
                dao.insertCategory(CategoryEntity(categoryName, clock(), newId))
            }
        }
        val now = clock()
        if (dao.updateProduct(id, name, barcode, categoryId, baseLabel, input.baseUnitPriceCentavos, effectiveCost, input.minimumStockMilliUnits, input.wholeBaseUnitsOnly, input.imageFileName, now) == 0) {
            throw PosError.ProductNotFound
        }
        rememberUnitName(false, existing.baseUnitLabel)
        originalUnits.filterNot { it.label.equals(existing.baseUnitLabel, ignoreCase = true) }.forEach { rememberUnitName(true, it.label) }
        rememberProductUnitNames(input)
        dao.deleteProductUnits(id)
        dao.insertProductUnits(
            listOf(ProductUnitEntity(UUID.randomUUID().toString(), id, baseLabel, SCALE, input.baseUnitPriceCentavos, input.wholeBaseUnitsOnly)) +
                input.extraUnits.map { ProductUnitEntity(UUID.randomUUID().toString(), id, it.label.trim(), it.baseQuantityMilliUnits, it.priceCentavos, it.wholeQuantityOnly) },
        )
        dao.insertAudit(AuditLogEntity(UUID.randomUUID().toString(), "PRODUCT_UPDATED", id, "Product details changed", now, actor.accountId))
        if (approvalId != null) actionApproval?.finishOwnerApproval(approvalId, success = true, resultDetails = null)
        existing
            }
        } catch (error: Throwable) {
            if (approvalId != null) runCatching { actionApproval?.finishOwnerApproval(approvalId, success = false, resultDetails = error.message) }
            throw error
        }
    }

    private suspend fun rememberProductUnitNames(input: NewProduct) {
        rememberUnitName(false, input.baseUnitLabel)
        input.extraUnits.forEach { rememberUnitName(true, it.label) }
    }

    private suspend fun rememberUnitName(selling: Boolean, name: String) {
        val label = name.trim()
        val key = "catalog.${if (selling) "selling" else "stock"}_unit.${label.lowercase(java.util.Locale.ROOT)}"
        if (dao.catalogSetting(key) == null) dao.saveCatalogSetting(AppSettingEntity(key, label))
    }

    private fun requestedUnits(input: NewProduct): List<Triple<String, Long, Long>> =
        listOf(Triple(input.baseUnitLabel.trim(), SCALE, input.baseUnitPriceCentavos)) +
            input.extraUnits.map { Triple(it.label.trim(), it.baseQuantityMilliUnits, it.priceCentavos) }

    private fun sellingPriceChanged(original: List<ProductUnitEntity>, requested: List<Triple<String, Long, Long>>): Boolean =
        original.map { it.label.trim().lowercase() to it.priceCentavos }.toSet() !=
            requested.map { it.first.trim().lowercase() to it.third }.toSet()

    private fun pricePayloadDigest(
        product: ProductEntity,
        oldUnits: List<ProductUnitEntity>,
        currentBalance: Long,
        input: NewProduct,
        effectiveCost: Long,
    ): String {
        val before = buildString {
            append(product.id).append('|').append(product.name).append('|').append(product.barcode).append('|')
            append(product.categoryId).append('|').append(product.baseUnitLabel).append('|').append(product.baseUnitPriceCentavos).append('|')
            append(product.purchaseCostCentavos).append('|').append(product.minimumStockMilliUnits).append('|').append(currentBalance).append('|')
            oldUnits.sortedBy { it.id }.forEach { append(it.id).append(':').append(it.label).append(':').append(it.baseQuantityMilliUnits).append(':').append(it.priceCentavos).append(':').append(it.wholeSellQuantityOnly).append(';') }
        }
        val after = buildString {
            append(input.name.trim()).append('|').append(input.barcode?.trim()).append('|').append(input.categoryName?.trim()).append('|')
            append(input.baseUnitLabel.trim()).append('|').append(input.baseUnitPriceCentavos).append('|').append(effectiveCost).append('|')
            append(input.initialStockMilliUnits).append('|').append(input.minimumStockMilliUnits).append('|').append(input.wholeBaseUnitsOnly).append('|')
            input.extraUnits.sortedBy { it.label.trim().lowercase() }.forEach { append(it.label.trim()).append(':').append(it.baseQuantityMilliUnits).append(':').append(it.priceCentavos).append(':').append(it.wholeQuantityOnly).append(';') }
        }
        val bytes = MessageDigest.getInstance("SHA-256").digest("$before||$after".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }

    suspend fun archiveProduct(id: String) = database.withTransaction {
        val actor = requirePermission(PosPermission.ARCHIVE_PRODUCT)
        val now = clock()
        if (dao.archiveProduct(id, now) == 0) throw PosError.ProductNotFound
        dao.insertAudit(AuditLogEntity(UUID.randomUUID().toString(), "PRODUCT_ARCHIVED", id, "Product archived", now, actor.accountId))
    }

    suspend fun setProductFavorite(id: String, favorite: Boolean) = database.withTransaction {
        val actor = requirePermission(PosPermission.VIEW_PRODUCTS)
        val now = clock()
        if (dao.updateFavorite(id, favorite, now) == 0) throw PosError.ProductNotFound
        dao.insertAudit(AuditLogEntity(UUID.randomUUID().toString(), "PRODUCT_FAVORITE_CHANGED", id, "Favorite set to $favorite", now, actor.accountId))
    }

    suspend fun addStock(
        productId: String,
        quantityMilliUnits: Long,
        reason: String,
        operationId: String = UUID.randomUUID().toString(),
        note: String? = null,
    ) = database.withTransaction {
        val actor = requirePermission(PosPermission.ADD_STOCK)
        require(quantityMilliUnits > 0)
        val product = dao.product(productId) ?: throw PosError.ProductNotFound
        if (!product.active || (product.wholeBaseUnitsOnly && quantityMilliUnits % SCALE != 0L)) throw PosError.InvalidQuantity
        if (dao.hasMovement("STOCK_IN", operationId, productId)) return@withTransaction
        val balance = dao.inventoryBalance(productId) ?: throw PosError.ProductNotFound
        val next = Math.addExact(balance.onHandMilliUnits, quantityMilliUnits)
        val now = clock()
        dao.updateBalance(productId, next, now)
        dao.insertMovement(InventoryMovementEntity(UUID.randomUUID().toString(), productId, quantityMilliUnits, reason, "STOCK_IN", operationId, now, note))
        dao.insertAudit(AuditLogEntity(UUID.randomUUID().toString(), "STOCK_ADDED", productId, "Stock received: $reason", now, actor.accountId))
    }

    suspend fun adjustStock(
        productId: String,
        deltaMilliUnits: Long,
        reason: String,
        operationId: String = UUID.randomUUID().toString(),
        note: String? = null,
    ) = database.withTransaction {
        val requiredPermission = if (reason.equals("DAMAGED", ignoreCase = true)) PosPermission.RECORD_DAMAGED_STOCK else PosPermission.ADJUST_STOCK
        val actor = requirePermission(requiredPermission)
        require(deltaMilliUnits != 0L && reason.isNotBlank())
        val product = dao.product(productId) ?: throw PosError.ProductNotFound
        if (dao.hasMovement("ADJUSTMENT", operationId, productId)) return@withTransaction
        val balance = dao.inventoryBalance(productId) ?: throw PosError.ProductNotFound
        if (!product.active || (product.wholeBaseUnitsOnly && deltaMilliUnits % SCALE != 0L)) throw PosError.InvalidQuantity
        val next = Math.addExact(balance.onHandMilliUnits, deltaMilliUnits)
        if (next < 0) throw PosError.InsufficientStock
        val now = clock()
        dao.updateBalance(productId, next, now)
        dao.insertMovement(InventoryMovementEntity(UUID.randomUUID().toString(), productId, deltaMilliUnits, reason, "ADJUSTMENT", operationId, now, note))
        dao.insertAudit(AuditLogEntity(UUID.randomUUID().toString(), "STOCK_ADJUSTED", productId, "Stock adjusted: $reason", now, actor.accountId))
    }

    suspend fun createCustomer(name: String, phone: String?): String = database.withTransaction {
        val actor = requirePermission(PosPermission.ADD_CUSTOMER)
        val cleanName = name.trim()
        val cleanPhone = phone?.trim()?.ifBlank { null }
        require(cleanName.isNotBlank())
        val id = UUID.randomUUID().toString()
        dao.insertCustomer(CustomerEntity(id, cleanName, cleanPhone, clock()))
        dao.insertAudit(AuditLogEntity(UUID.randomUUID().toString(), "CUSTOMER_CREATED", id, "Customer created", clock(), actor.accountId))
        id
    }

    suspend fun recordStockDelivery(
        operationId: String,
        supplierName: String?,
        supplierPhone: String?,
        lines: List<DeliveryLineInput>,
        note: String?,
    ) = database.withTransaction {
        val actor = requirePermission(PosPermission.MANAGE_SUPPLIERS)
        requirePermission(PosPermission.ADD_STOCK)
        val deliveryId = operationId.trim()
        if (deliveryId.isBlank() || lines.isEmpty() || lines.map { it.productId }.distinct().size != lines.size) {
            throw PosError.InvalidQuantity
        }
        if (dao.stockDelivery(deliveryId) != null) return@withTransaction
        val supplierLabel = supplierName?.trim()?.ifBlank { null }
        val supplierId = supplierLabel?.let { label ->
            dao.supplierByName(label)?.id ?: UUID.randomUUID().toString().also { newId ->
                dao.insertSupplier(SupplierEntity(newId, label, supplierPhone?.trim()?.ifBlank { null }, clock()))
            }
        }
        val now = clock()
        val prepared = lines.map { line ->
            if (line.quantityMilliUnits <= 0 || line.totalCostCentavos < 0) throw PosError.InvalidQuantity
            val product = dao.product(line.productId) ?: throw PosError.ProductNotFound
            if (!product.active || (product.wholeBaseUnitsOnly && line.quantityMilliUnits % SCALE != 0L)) throw PosError.InvalidQuantity
            val current = dao.inventoryBalance(line.productId) ?: throw PosError.ProductNotFound
            val next = Math.addExact(current.onHandMilliUnits, line.quantityMilliUnits)
            val lineCost = line.totalCostCentavos
            val weightedCost = BigDecimal.valueOf(product.purchaseCostCentavos)
                .multiply(BigDecimal.valueOf(current.onHandMilliUnits))
                .add(BigDecimal.valueOf(lineCost).multiply(BigDecimal.valueOf(SCALE)))
                .divide(BigDecimal.valueOf(next), 0, RoundingMode.HALF_UP)
                .longValueExact()
            val unitCost = BigDecimal.valueOf(lineCost)
                .multiply(BigDecimal.valueOf(SCALE))
                .divide(BigDecimal.valueOf(line.quantityMilliUnits), 0, RoundingMode.HALF_UP)
                .longValueExact()
            PreparedDeliveryLine(line, product, current.onHandMilliUnits, next, weightedCost, unitCost)
        }
        val totalCost = prepared.fold(0L) { total, item -> Math.addExact(total, item.input.totalCostCentavos) }
        dao.insertStockDelivery(StockDeliveryEntity(deliveryId, supplierId, supplierLabel, now, totalCost, note?.trim()?.ifBlank { null }))
        dao.insertStockDeliveryItems(prepared.map { item ->
            StockDeliveryItemEntity(
                UUID.randomUUID().toString(), deliveryId, item.product.id, item.product.name, item.product.baseUnitLabel,
                item.input.quantityMilliUnits, item.input.quantityMilliUnits, item.unitCostCentavos, item.input.totalCostCentavos,
            )
        })
        prepared.forEach { item ->
            dao.updateBalance(item.product.id, item.nextStockMilliUnits, now)
            dao.updatePurchaseCost(item.product.id, item.weightedCostCentavos, now)
            dao.insertMovement(InventoryMovementEntity(UUID.randomUUID().toString(), item.product.id, item.input.quantityMilliUnits, "SUPPLIER_DELIVERY", "DELIVERY", deliveryId, now, note))
        }
        dao.insertAudit(AuditLogEntity(UUID.randomUUID().toString(), "STOCK_DELIVERY", deliveryId, "Supplier delivery recorded", now, actor.accountId))
    }

    suspend fun recordExpense(
        operationId: String,
        amountCentavos: Long,
        category: String,
        description: String?,
        paymentSource: String,
    ) = database.withTransaction {
        val actor = requirePermission(PosPermission.MANAGE_EXPENSES)
        val id = operationId.trim()
        val cleanCategory = category.trim()
        if (id.isBlank() || amountCentavos <= 0 || cleanCategory.isBlank() || paymentSource !in setOf("CASH", "GCASH", "MAYA")) {
            throw PosError.PaymentInvalid
        }
        if (dao.expense(id) != null) return@withTransaction
        val now = clock()
        dao.insertExpense(ExpenseEntity(id, amountCentavos, cleanCategory, description?.trim()?.ifBlank { null }, paymentSource, now))
        dao.insertAudit(AuditLogEntity(UUID.randomUUID().toString(), "EXPENSE_RECORDED", id, "Store expense recorded: $cleanCategory", now, actor.accountId))
    }

    suspend fun refundSale(command: RefundCommand): RefundResult = database.withTransaction {
        val actor = requirePermission(PosPermission.PROCESS_REFUND)
        val key = command.idempotencyKey.trim()
        if (key.isBlank()) throw PosError.PaymentInvalid
        dao.refundByKey(key)?.let { return@withTransaction RefundResult(it.id, it.refundedCentavos, true) }
        val sale = dao.sale(command.saleId) ?: throw PosError.ProductNotFound
        if (command.paymentMethod == RefundMethod.STORE_CREDIT && sale.customerId == null) throw PosError.CustomerRequired
        if (sale.status != "COMPLETED" || command.lines.isEmpty() || command.reason.isBlank() ||
            command.lines.map { it.saleItemId }.distinct().size != command.lines.size
        ) throw PosError.InvalidQuantity
        val originalItems = dao.saleItems(sale.id).associateBy { it.id }
        val prepared = command.lines.map { requested ->
            val item = originalItems[requested.saleItemId] ?: throw PosError.ProductNotFound
            if (requested.quantityMilliUnits <= 0) throw PosError.InvalidQuantity
            val previouslyReturned = dao.refundedQuantity(item.id)
            val remainingQuantity = item.quantityMilliUnits - previouslyReturned
            if (requested.quantityMilliUnits > remainingQuantity) throw PosError.InvalidQuantity
            val previouslyRefunded = dao.refundedAmount(item.id)
            val remainingAmount = item.lineTotalCentavos - previouslyRefunded
            val amount = if (requested.quantityMilliUnits == remainingQuantity) {
                remainingAmount
            } else {
                minOf(remainingAmount, roundedScaledAmount(item.unitPriceCentavos, requested.quantityMilliUnits))
            }
            if (amount <= 0) throw PosError.PaymentInvalid
            val baseNumerator = Math.multiplyExact(item.baseQuantityMilliUnits, requested.quantityMilliUnits)
            if (baseNumerator % item.quantityMilliUnits != 0L) throw PosError.InvalidQuantity
            PreparedRefundLine(requested, item, baseNumerator / item.quantityMilliUnits, amount)
        }
        val refundTotal = prepared.fold(0L) { total, item -> Math.addExact(total, item.amountCentavos) }
        val refundId = UUID.randomUUID().toString()
        val now = clock()
        dao.insertRefund(RefundEntity(refundId, sale.id, key, refundTotal, command.reason.trim(), now, command.paymentMethod.name))
        dao.insertRefundItems(prepared.map { item ->
            RefundItemEntity(UUID.randomUUID().toString(), refundId, item.original.id, item.input.quantityMilliUnits, item.amountCentavos, item.input.restock)
        })
        val returnedByProduct = prepared.filter { it.input.restock }.groupBy { originalItems.getValue(it.original.id).productId }
        returnedByProduct.forEach { (productId, refunds) ->
            val balance = dao.inventoryBalance(productId) ?: throw PosError.ProductNotFound
            val quantity = refunds.fold(0L) { total, item -> Math.addExact(total, item.baseQuantityMilliUnits) }
            dao.updateBalance(productId, Math.addExact(balance.onHandMilliUnits, quantity), now)
            dao.insertMovement(InventoryMovementEntity(UUID.randomUUID().toString(), productId, quantity, "CUSTOMER_RETURN", "REFUND", refundId, now, command.reason.trim()))
        }
        if (sale.customerId != null && command.paymentMethod == RefundMethod.STORE_CREDIT) {
            dao.insertCustomerLedger(CustomerLedgerEntryEntity(UUID.randomUUID().toString(), sale.customerId, -refundTotal, "REFUND", refundId, now, command.reason.trim()))
        }
        dao.insertAudit(AuditLogEntity(UUID.randomUUID().toString(), "SALE_REFUNDED", sale.id, "Refund recorded: ${refundTotal} centavos", now, actor.accountId))
        RefundResult(refundId, refundTotal)
    }

    suspend fun checkout(command: CheckoutCommand): CheckoutResult = database.withTransaction {
        val actor = requirePermission(PosPermission.CREATE_SALE)
        val key = command.idempotencyKey.trim()
        if (key.isBlank() || command.salesChannel !in SaleChannel.entries) throw PosError.PaymentInvalid
        dao.saleByIdempotencyKey(key)?.let { existing -> return@withTransaction savedCheckoutResult(existing.id, existing.totalCentavos) }
        if (command.lines.isEmpty()) throw PosError.InvalidQuantity

        val prepared = command.lines.map { line ->
            if (line.quantityMilliUnits <= 0) throw PosError.InvalidQuantity
            val product = dao.product(line.productId) ?: throw PosError.ProductNotFound
            val unit = dao.productUnit(line.productId, line.unitId) ?: throw PosError.ProductNotFound
            if (!product.active || (unit.wholeSellQuantityOnly && line.quantityMilliUnits % SCALE != 0L)) throw PosError.InvalidQuantity
            val baseQty = exactScaledProduct(line.quantityMilliUnits, unit.baseQuantityMilliUnits)
            if (product.wholeBaseUnitsOnly && baseQty % SCALE != 0L) throw PosError.InvalidQuantity
            val lineTotal = roundedScaledAmount(unit.priceCentavos, line.quantityMilliUnits)
            val lineCost = roundedScaledAmount(product.purchaseCostCentavos, baseQty)
            PreparedLine(line, product, unit, baseQty, lineTotal, lineCost)
        }
        val total = prepared.fold(0L) { acc, line -> Math.addExact(acc, line.lineTotalCentavos) }
        val now = clock()
        val saleId = UUID.randomUUID().toString()

        val requiredByProduct = prepared.groupBy { it.product.id }.mapValues { (_, lines) ->
            lines.fold(0L) { acc, line -> Math.addExact(acc, line.baseQuantityMilliUnits) }
        }
        requiredByProduct.forEach { (productId, required) ->
            val balance = dao.inventoryBalance(productId)?.onHandMilliUnits ?: 0L
            if (balance < required) throw PosError.InsufficientStock
        }

        var paid = 0L
        var change = 0L
        val paymentRows = mutableListOf<PaymentEntity>()
        command.tenders.forEach { tender ->
            if (tender.amountTenderedCentavos <= 0) throw PosError.PaymentInvalid
            val due = total - paid
            if (due <= 0) throw PosError.PaymentInvalid
            val isCash = tender.method == PaymentMethod.CASH
            if (!isCash && tender.amountTenderedCentavos > due) throw PosError.PaymentInvalid
            val applied = minOf(tender.amountTenderedCentavos, due)
            val tenderChange = if (isCash) tender.amountTenderedCentavos - applied else 0L
            paid = Math.addExact(paid, applied)
            change = Math.addExact(change, tenderChange)
            paymentRows += PaymentEntity(
                id = UUID.randomUUID().toString(),
                saleId = saleId,
                method = tender.method.name,
                amountAppliedCentavos = applied,
                tenderedCentavos = if (isCash) tender.amountTenderedCentavos else null,
                changeCentavos = tenderChange,
                createdAtMillis = now,
            )
        }
        val credit = total - paid
        if (credit == total && credit > 0) requirePermission(PosPermission.ACCEPT_UTANG)
        if (credit > 0 && paid > 0) requirePermission(PosPermission.ACCEPT_PARTIAL_PAYMENT)
        if (credit > 0 && (command.customerId == null || dao.customer(command.customerId) == null)) throw PosError.CustomerRequired

        dao.insertSale(SaleEntity(saleId, key, now, total, command.customerId, "COMPLETED", command.cashierMode, actor.accountId, command.salesChannel.name))
        dao.insertSaleItems(
            prepared.map { line ->
                SaleItemEntity(
                    id = UUID.randomUUID().toString(),
                    saleId = saleId,
                    productId = line.product.id,
                    productNameSnapshot = line.product.name,
                    unitLabelSnapshot = line.unit.label,
                    quantityMilliUnits = line.input.quantityMilliUnits,
                    baseQuantityMilliUnits = line.baseQuantityMilliUnits,
                    unitPriceCentavos = line.unit.priceCentavos,
                    lineTotalCentavos = line.lineTotalCentavos,
                    unitCostCentavos = line.product.purchaseCostCentavos,
                    lineCostCentavos = line.lineCostCentavos,
                )
            },
        )
        if (paymentRows.isNotEmpty()) dao.insertPayments(paymentRows)
        if (credit > 0) {
            dao.insertCustomerLedger(
                CustomerLedgerEntryEntity(UUID.randomUUID().toString(), command.customerId!!, credit, "SALE", saleId, now, "Sale balance"),
            )
        }
        requiredByProduct.forEach { (productId, quantity) ->
            val old = dao.inventoryBalance(productId) ?: throw PosError.ProductNotFound
            val next = old.onHandMilliUnits - quantity
            dao.updateBalance(productId, next, now)
            dao.insertMovement(InventoryMovementEntity(UUID.randomUUID().toString(), productId, -quantity, "SALE", "SALE", saleId, now, null))
        }
        dao.insertAudit(AuditLogEntity(UUID.randomUUID().toString(), "SALE_COMPLETED", saleId, "Sale completed", now, actor.accountId))
        CheckoutResult(saleId, total, paid, change, credit)
    }

    private suspend fun savedCheckoutResult(saleId: String, total: Long): CheckoutResult {
        val payments = dao.salePayments(saleId)
        val paid = payments.sumOf { it.amountAppliedCentavos }
        val change = payments.sumOf { it.changeCentavos }
        return CheckoutResult(saleId, total, paid, change, total - paid, wasAlreadyCommitted = true)
    }

    companion object {
        const val SCALE = 1_000L

        private fun exactScaledProduct(left: Long, right: Long): Long {
            val product = Math.multiplyExact(left, right)
            if (product % SCALE != 0L) throw PosError.InvalidQuantity
            return product / SCALE
        }

        private fun roundedScaledAmount(amount: Long, quantityMilli: Long): Long =
            BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(quantityMilli))
                .divide(BigDecimal.valueOf(SCALE), 0, RoundingMode.HALF_UP)
                .longValueExact()
    }

    private data class PreparedLine(
        val input: CartLineInput,
        val product: ProductEntity,
        val unit: ProductUnitEntity,
        val baseQuantityMilliUnits: Long,
        val lineTotalCentavos: Long,
        val lineCostCentavos: Long,
    )

    private data class PreparedDeliveryLine(
        val input: DeliveryLineInput,
        val product: ProductEntity,
        val oldStockMilliUnits: Long,
        val nextStockMilliUnits: Long,
        val weightedCostCentavos: Long,
        val unitCostCentavos: Long,
    )

    private data class PreparedRefundLine(
        val input: RefundLineInput,
        val original: SaleItemEntity,
        val baseQuantityMilliUnits: Long,
        val amountCentavos: Long,
    )
}

data class TodayStats(
    val salesCentavos: Long = 0,
    val cashCentavos: Long = 0,
    val gcashCentavos: Long = 0,
    val mayaCentavos: Long = 0,
    val estimatedGrossProfitCentavos: Long = 0,
    val expensesCentavos: Long = 0,
    val lowStockCount: Int = 0,
)

data class SalesReport(
    val fromMillis: Long,
    val untilMillis: Long,
    val grossSalesCentavos: Long,
    val refundsCentavos: Long,
    val netSalesCentavos: Long,
    val salesVisible: Boolean,
    val saleCount: Int,
    val grossProfitCentavos: Long?,
    val expensesCentavos: Long?,
    val payments: List<LabelAmount>,
    val channels: List<SalesChannelAmount>,
    val dailySales: List<DailySalesReportRow>,
    val topProducts: List<TopProductReportRow>,
    val expenseCategories: List<LabelAmount>,
    val outstandingUtangCentavos: Long?,
    val inventoryValuationCentavos: Long?,
    val lowStockProducts: List<LowStockReportRow>?,
    val supplierPurchases: List<LabelAmount>,
)

data class SaleReceiptData(
    val sale: SaleEntity,
    val items: List<SaleItemEntity>,
    val payments: List<PaymentEntity>,
    val customerName: String?,
)
