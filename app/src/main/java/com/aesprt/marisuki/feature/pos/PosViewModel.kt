package com.aesprt.marisuki.feature.pos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aesprt.marisuki.data.AppPreferences
import com.aesprt.marisuki.data.EncryptedBackupManager
import com.aesprt.marisuki.data.PosRepository
import com.aesprt.marisuki.data.SalesReport
import com.aesprt.marisuki.data.auth.AuthRepository
import com.aesprt.marisuki.data.auth.AuthException
import com.aesprt.marisuki.data.local.dao.CustomerBalance
import com.aesprt.marisuki.data.local.dao.ProductSummary
import com.aesprt.marisuki.domain.model.CartLineInput
import com.aesprt.marisuki.domain.model.CheckoutCommand
import com.aesprt.marisuki.domain.model.CheckoutResult
import com.aesprt.marisuki.domain.model.DeliveryLineInput
import com.aesprt.marisuki.domain.model.NewProduct
import com.aesprt.marisuki.domain.model.PaymentMethod
import com.aesprt.marisuki.domain.model.PosError
import com.aesprt.marisuki.domain.model.RefundCommand
import com.aesprt.marisuki.domain.model.RefundLineInput
import com.aesprt.marisuki.domain.model.RefundMethod
import com.aesprt.marisuki.domain.model.SaleChannel
import com.aesprt.marisuki.domain.model.TenderInput
import com.aesprt.marisuki.domain.auth.AccountRole
import com.aesprt.marisuki.domain.auth.AccountSummary
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.domain.auth.PriceApprovalPreview
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID
import android.net.Uri
import android.content.ContentResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProductImportState(
    val busy: Boolean = false,
    val fileName: String = "",
    val preview: com.aesprt.marisuki.data.importing.ImportPreview? = null,
    val error: com.aesprt.marisuki.data.importing.ImportProblem? = null,
    val result: com.aesprt.marisuki.data.importing.ImportResult? = null,
    val templateSaved: Boolean = false,
)

data class CartLineState(
    val productId: String,
    val productName: String,
    val unitId: String,
    val unitLabel: String,
    val quantityMilliUnits: Long,
    val unitPriceCentavos: Long,
    val baseQuantityMilliUnitsPerUnit: Long,
    val stockMilliUnits: Long,
    val wholeSellQuantityOnly: Boolean,
)

sealed interface PosEvent {
    data class SaleSaved(val result: CheckoutResult) : PosEvent
    data object ProductSaved : PosEvent
    data object CustomerSaved : PosEvent
    data object StockSaved : PosEvent
    data object UtangPaymentSaved : PosEvent
    data object DeliverySaved : PosEvent
    data object ExpenseSaved : PosEvent
    data object RefundSaved : PosEvent
    data object OwnerPinRejected : PosEvent
    data object OwnerPinSet : PosEvent
    data object BackupSaved : PosEvent
    data object BackupRestored : PosEvent
    data object ReportExported : PosEvent
    data class Error(val message: PosMessage) : PosEvent
}

enum class PosMessage { INVALID, OUT_OF_STOCK, CUSTOMER_REQUIRED, PRODUCT_UNAVAILABLE, PAYMENT_INVALID, DUPLICATE, ACCESS_DENIED, GENERAL }

data class PendingPriceChange(val preview: PriceApprovalPreview, val product: NewProduct)
data class SalesReportScreenState(val report: SalesReport? = null, val isLoading: Boolean = false, val error: PosMessage? = null)

@OptIn(ExperimentalCoroutinesApi::class)
class PosViewModel(
    private val repository: PosRepository,
    private val preferences: AppPreferences,
    private val backupManager: EncryptedBackupManager,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _cart = MutableStateFlow<List<CartLineState>>(emptyList())
    val cart: StateFlow<List<CartLineState>> = _cart.asStateFlow()
    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()
    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()
    private val _events = Channel<PosEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()
    private var checkoutKey = UUID.randomUUID().toString()

    val products = query.flatMapLatest { value -> repository.searchProducts(value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val allProducts = repository.observeProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val customers = repository.observeCustomerBalances()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun observeCustomerLedger(customerId: String) = repository.observeCustomerLedger(customerId)
    val sales = repository.observeSales()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lowStockCount = repository.observeLowStockCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val todayStats = repository.observeTodayStats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.aesprt.marisuki.data.TodayStats())
    val suppliers = repository.observeSuppliers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val deliveries = repository.observeStockDeliveries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val expenses = repository.observeExpenses()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val language = preferences.language
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppPreferences.SYSTEM_LANGUAGE)
    val ownerPinConfigured = preferences.ownerPinConfigured
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    private val _ownerAccessGranted = MutableStateFlow(false)
    val ownerAccessGranted: StateFlow<Boolean> = _ownerAccessGranted.asStateFlow()
    private val _permissions = MutableStateFlow<Set<PosPermission>>(emptySet())
    val permissions: StateFlow<Set<PosPermission>> = _permissions.asStateFlow()
    private val _pendingPriceChange = MutableStateFlow<PendingPriceChange?>(null)
    val pendingPriceChange: StateFlow<PendingPriceChange?> = _pendingPriceChange.asStateFlow()
    private val _priceApprovalError = MutableStateFlow<String?>(null)
    val priceApprovalError: StateFlow<String?> = _priceApprovalError.asStateFlow()
    private val _hasAutomaticBackup = MutableStateFlow(false)
    val hasAutomaticBackup: StateFlow<Boolean> = _hasAutomaticBackup.asStateFlow()
    private val _salesReport = MutableStateFlow(SalesReportScreenState(isLoading = true))
    val salesReport: StateFlow<SalesReportScreenState> = _salesReport.asStateFlow()

    init { refreshAutomaticBackup() }

    fun loadSalesReport(fromMillis: Long, untilMillis: Long) {
        viewModelScope.launch {
            _salesReport.value = SalesReportScreenState(isLoading = true)
            try {
                _salesReport.value = SalesReportScreenState(report = repository.salesReport(fromMillis, untilMillis))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _salesReport.value = SalesReportScreenState(error = error.toMessage())
            }
        }
    }

    fun exportSalesReport(uri: Uri, contentResolver: ContentResolver, fromMillis: Long, untilMillis: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val output = contentResolver.openOutputStream(uri) ?: error("Could not open the selected report file.")
                repository.exportSalesReportCsv(fromMillis, untilMillis, output)
                _events.send(PosEvent.ReportExported)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _events.send(PosEvent.Error(error.toMessage()))
            }
        }
    }

    val cartTotalCentavos: Long
        get() = _cart.value.fold(0L) { acc, line ->
            Math.addExact(acc, calculateAmount(line.unitPriceCentavos, line.quantityMilliUnits))
        }

    fun searchProducts(value: String) { query.value = value }
    fun setLanguage(value: String) {
        viewModelScope.launch {
            try {
                authRepository.requirePermission(PosPermission.CHANGE_SETTINGS)
                preferences.setLanguage(value)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _events.send(PosEvent.Error(error.toMessage()))
            }
        }
    }

    fun lockOwnerTools() { _ownerAccessGranted.value = false }

    suspend fun requirePrinterManagement() = repository.requirePrinterManagement()

    fun setAuthenticatedAccount(account: AccountSummary?) {
        viewModelScope.launch {
            if (account == null) {
                _ownerAccessGranted.value = false
                _permissions.value = emptySet()
                return@launch
            }
            _ownerAccessGranted.value = account.role == AccountRole.OWNER
            _permissions.value = try { authRepository.currentPermissions() } catch (_: Throwable) { emptySet() }
        }
    }

    fun clearCart() {
        viewModelScope.launch {
            try {
                authRepository.requirePermission(PosPermission.CANCEL_ACTIVE_CART)
                _cart.value = emptyList()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _events.send(PosEvent.Error(error.toMessage()))
            }
        }
    }

    fun exportBackup(uri: Uri, passphrase: String) = runOperation(PosEvent.BackupSaved) {
        authRepository.requirePermission(PosPermission.CREATE_BACKUPS)
        backupManager.export(uri, passphrase)
    }

    fun restoreBackup(uri: Uri, passphrase: String) = runOperation(PosEvent.BackupRestored) {
        authRepository.requirePermission(PosPermission.RESTORE_BACKUPS)
        backupManager.restore(uri, passphrase)
        authRepository.logout()
    }

    fun refreshAutomaticBackup() {
        viewModelScope.launch {
            try {
                _hasAutomaticBackup.value = backupManager.hasAutomaticBackup()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _events.send(PosEvent.Error(error.toMessage()))
            }
        }
    }

    fun restoreLatestAutomaticBackup() = runOperation(PosEvent.BackupRestored) {
        authRepository.requirePermission(PosPermission.RESTORE_BACKUPS)
        backupManager.restoreLatestAutomaticBackup()
        authRepository.logout()
        _hasAutomaticBackup.value = true
    }
    suspend fun unitsFor(productId: String) = repository.productUnits(productId)
    suspend fun saleReceipt(saleId: String, customerName: String?) = repository.saleReceipt(saleId, customerName)
    suspend fun ensureProductBarcode(productId: String) = repository.ensureProductBarcode(productId)

    fun addProduct(product: ProductSummary) {
        addProduct(product, requestedUnitId = null)
    }

    fun addProductInUnit(product: ProductSummary, unitId: String) {
        addProduct(product, requestedUnitId = unitId)
    }

    private fun addProduct(product: ProductSummary, requestedUnitId: String?) {
        viewModelScope.launch {
            try {
                val units = repository.productUnits(product.id)
                val unit = if (requestedUnitId != null) {
                    units.firstOrNull { it.id == requestedUnitId } ?: throw PosError.ProductNotFound
                } else {
                    units.firstOrNull { it.baseQuantityMilliUnits == SCALE && it.label.equals(product.baseUnitLabel, ignoreCase = true) }
                        ?: throw PosError.ProductNotFound
                }
                val current = _cart.value.toMutableList()
                val index = current.indexOfFirst { it.productId == product.id && it.unitId == unit.id }
                val nextQuantity = if (index >= 0) Math.addExact(current[index].quantityMilliUnits, SCALE) else SCALE
                val baseNeeded = Math.multiplyExact(nextQuantity, unit.baseQuantityMilliUnits) / SCALE
                val otherUnits = current.filter { it.productId == product.id && (index < 0 || it.unitId != unit.id) }
                    .sumOf { Math.multiplyExact(it.quantityMilliUnits, it.baseQuantityMilliUnitsPerUnit) / SCALE }
                if (Math.addExact(baseNeeded, otherUnits) > product.onHandMilliUnits) throw PosError.InsufficientStock
                val line = CartLineState(product.id, product.name, unit.id, unit.label, nextQuantity, unit.priceCentavos, unit.baseQuantityMilliUnits, product.onHandMilliUnits, unit.wholeSellQuantityOnly)
                if (index >= 0) current[index] = line else current += line
                _cart.value = current
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _events.send(PosEvent.Error(error.toMessage()))
            }
        }
    }

    fun setSearchQuery(value: String) = searchProducts(value)

    fun resolveBarcode(barcode: String, onProductFound: (ProductSummary) -> Unit) {
        viewModelScope.launch {
            try {
                val product = repository.productByBarcode(barcode) ?: throw PosError.ProductNotFound
                query.value = ""
                onProductFound(product)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _events.send(PosEvent.Error(error.toMessage()))
            }
        }
    }

    fun updateCartQuantity(productId: String, unitId: String, deltaMilliUnits: Long) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.productId == productId && it.unitId == unitId }
        if (index < 0) return
        setCartQuantity(productId, unitId, Math.addExact(current[index].quantityMilliUnits, deltaMilliUnits))
    }

    fun setCartQuantity(productId: String, unitId: String, changed: Long) {
        val current = _cart.value.toMutableList()
        val index = current.indexOfFirst { it.productId == productId && it.unitId == unitId }
        if (index < 0) return
        if (changed <= 0) {
            current.removeAt(index)
            _cart.value = current
            return
        }
        if (current[index].wholeSellQuantityOnly && changed % SCALE != 0L) {
            viewModelScope.launch { _events.send(PosEvent.Error(PosMessage.INVALID)) }
            return
        }
        val baseNeed = Math.multiplyExact(changed, current[index].baseQuantityMilliUnitsPerUnit) / SCALE
        val othersNeed = current.filterIndexed { i, line -> i != index && line.productId == productId }
            .sumOf { Math.multiplyExact(it.quantityMilliUnits, it.baseQuantityMilliUnitsPerUnit) / SCALE }
        if (Math.addExact(baseNeed, othersNeed) > current[index].stockMilliUnits) {
            viewModelScope.launch { _events.send(PosEvent.Error(PosMessage.OUT_OF_STOCK)) }
            return
        }
        current[index] = current[index].copy(quantityMilliUnits = changed)
        _cart.value = current
    }

    fun removeFromCart(productId: String, unitId: String) {
        viewModelScope.launch {
            try {
                authRepository.requirePermission(PosPermission.CANCEL_ACTIVE_CART)
                _cart.value = _cart.value.filterNot { it.productId == productId && it.unitId == unitId }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _events.send(PosEvent.Error(error.toMessage()))
            }
        }
    }

    val categoryNames = repository.observeCategoryNames().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stockUnitNames = repository.observeUnitNames(false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sellingUnitNames = repository.observeUnitNames(true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _productImport = MutableStateFlow(ProductImportState())
    val productImport = _productImport.asStateFlow()

    suspend fun saveCatalogChoice(kind: String, name: String): String = kotlinx.coroutines.withContext(Dispatchers.IO) { repository.saveCatalogChoice(kind, name) }
    fun resetProductImport() { if (!_productImport.value.busy) _productImport.value = ProductImportState() }
    fun readProductImport(uri: Uri, resolver: ContentResolver) {
        if (_productImport.value.busy) return
        _productImport.value = ProductImportState(busy = true)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fileName = resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else ""
                }.orEmpty()
                val bytes = resolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    while (true) {
                        val n = input.read(buffer); if (n < 0) break
                        if (output.size() + n > com.aesprt.marisuki.data.importing.ProductImport.MAX_BYTES) throw com.aesprt.marisuki.data.importing.ImportFileException(com.aesprt.marisuki.data.importing.ImportProblem.LIMIT)
                        output.write(buffer, 0, n)
                    }
                    output.toByteArray()
                } ?: throw java.io.IOException()
                val excel = fileName.endsWith(".xlsx", true) || (bytes.size >= 2 && bytes[0] == 80.toByte() && bytes[1] == 75.toByte())
                val preview = repository.reviewProductImport(com.aesprt.marisuki.data.importing.ProductImport.parse(bytes, excel))
                _productImport.value = ProductImportState(fileName = fileName, preview = preview)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _productImport.value = ProductImportState(error = (error as? com.aesprt.marisuki.data.importing.ImportFileException)?.problem ?: com.aesprt.marisuki.data.importing.ImportProblem.FAILED) }
        }
    }
    fun saveProductTemplate(uri: Uri, context: android.content.Context, excel: Boolean) {
        if (_productImport.value.busy) return
        _productImport.value = _productImport.value.copy(busy = true, error = null, templateSaved = false)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    if (excel) context.assets.open("templates/products.xlsx").use { it.copyTo(output) }
                    else output.write(com.aesprt.marisuki.data.importing.ProductImport.csvTemplate().toByteArray(Charsets.UTF_8))
                } ?: throw java.io.IOException()
                _productImport.value = _productImport.value.copy(busy = false, templateSaved = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _productImport.value = _productImport.value.copy(busy = false, error = com.aesprt.marisuki.data.importing.ImportProblem.FAILED) }
        }
    }
    fun commitProductImport() {
        val state = _productImport.value
        val preview = state.preview ?: return
        if (state.busy || state.result != null || !preview.canImport) return
        _productImport.value = state.copy(busy = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = repository.importProducts(preview.rows.map { requireNotNull(it.input) })
                _productImport.value = state.copy(result = result)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                val refreshed = try { repository.reviewProductImport(preview) } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { preview }
                _productImport.value = state.copy(preview = refreshed, error = com.aesprt.marisuki.data.importing.ImportProblem.FAILED)
            }
        }
    }

    fun createProduct(product: NewProduct) = runOperation(PosEvent.ProductSaved) { repository.createProduct(product) }
    fun updateProduct(id: String, product: NewProduct) {
        viewModelScope.launch {
            if (_isWorking.value) return@launch
            _isWorking.value = true
            _priceApprovalError.value = null
            try {
                val preview = repository.requestPriceChangeApproval(id, product)
                if (preview != null) {
                    _pendingPriceChange.value = PendingPriceChange(preview, product)
                } else {
                    repository.updateProduct(id, product)
                    _events.send(PosEvent.ProductSaved)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _events.send(PosEvent.Error(error.toMessage()))
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun authorizePendingPriceChange(ownerPin: String) {
        viewModelScope.launch {
            val pending = _pendingPriceChange.value ?: return@launch
            if (_isWorking.value) return@launch
            _isWorking.value = true
            _priceApprovalError.value = null
            try {
                authRepository.authorizeOwnerApproval(pending.preview.approvalId, ownerPin)
                repository.updateProduct(pending.preview.productId, pending.product, pending.preview.approvalId)
                _pendingPriceChange.value = null
                _events.send(PosEvent.ProductSaved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _priceApprovalError.value = error.approvalMessage()
                try {
                    val fresh = repository.requestPriceChangeApproval(pending.preview.productId, pending.product)
                    _pendingPriceChange.value = fresh?.let { PendingPriceChange(it, pending.product) }
                } catch (_: Throwable) {
                    _pendingPriceChange.value = null
                }
            } finally {
                _isWorking.value = false
            }
        }
    }

    fun cancelPendingPriceChange() {
        val request = _pendingPriceChange.value ?: return
        viewModelScope.launch {
            try { authRepository.cancelOwnerApproval(request.preview.approvalId) }
            finally { _pendingPriceChange.value = null; _priceApprovalError.value = null }
        }
    }
    fun archiveProduct(id: String) = runOperation(PosEvent.ProductSaved) { repository.archiveProduct(id) }
    fun setProductFavorite(id: String, favorite: Boolean) = runOperation(PosEvent.ProductSaved) { repository.setProductFavorite(id, favorite) }
    fun createCustomer(name: String, phone: String?) = runOperation(PosEvent.CustomerSaved) { repository.createCustomer(name, phone) }
    fun receiveStock(productId: String, quantityMilliUnits: Long, reason: String, note: String?, operationId: String = UUID.randomUUID().toString()) =
        runOperation(PosEvent.StockSaved) { repository.addStock(productId, quantityMilliUnits, reason, operationId, note) }
    fun adjustStock(productId: String, deltaMilliUnits: Long, reason: String, note: String?, operationId: String = UUID.randomUUID().toString()) =
        runOperation(PosEvent.StockSaved) { repository.adjustStock(productId, deltaMilliUnits, reason, operationId, note) }
    fun recordStockDelivery(operationId: String, supplier: String?, phone: String?, lines: List<DeliveryLineInput>, note: String?) =
        runOperation(PosEvent.DeliverySaved) { repository.recordStockDelivery(operationId, supplier, phone, lines, note) }
    fun recordExpense(operationId: String, amountCentavos: Long, category: String, description: String?, paymentSource: String) =
        runOperation(PosEvent.ExpenseSaved) { repository.recordExpense(operationId, amountCentavos, category, description, paymentSource) }
    fun refundSale(saleId: String, reason: String, lines: List<RefundLineInput>, method: RefundMethod, operationId: String) =
        runOperation(PosEvent.RefundSaved) {
            repository.refundSale(RefundCommand(operationId, saleId, reason, lines, method))
        }

    fun observeSaleHistoryDetails(saleId: String) = repository.observeSaleHistoryDetails(saleId)
    suspend fun saleCashierName(accountId: String) = repository.saleCashierName(accountId)
    suspend fun saleItems(saleId: String) = repository.saleItems(saleId)

    fun recordUtangPayment(customerId: String, amountText: String, method: PaymentMethod, idempotencyKey: String) {
        viewModelScope.launch {
            try {
                val amount = parseCentavos(amountText) ?: throw PosError.PaymentInvalid
                repository.recordCustomerPayment(customerId, amount, method, idempotencyKey)
                _events.send(PosEvent.UtangPaymentSaved)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _events.send(PosEvent.Error(error.toMessage()))
            }
        }
    }

    fun checkout(method: PaymentMethod?, amountText: String, customerId: String?, salesChannel: SaleChannel = SaleChannel.TINGI) {
        if (_isSubmitting.value) return
        val lines = _cart.value
        if (lines.isEmpty()) return
        viewModelScope.launch {
            _isSubmitting.value = true
            try {
                val amount = when {
                    method == null -> null
                    amountText.isBlank() -> cartTotalCentavos
                    else -> parseCentavos(amountText) ?: throw PosError.PaymentInvalid
                }
                val tenders = if (method == null || amount == null) emptyList() else listOf(TenderInput(method, amount))
                val result = repository.checkout(
                    CheckoutCommand(
                        idempotencyKey = checkoutKey,
                        lines = lines.map { CartLineInput(it.productId, it.unitId, it.quantityMilliUnits) },
                        tenders = tenders,
                        customerId = customerId,
                        salesChannel = salesChannel,
                    ),
                )
                _cart.value = emptyList()
                checkoutKey = UUID.randomUUID().toString()
                _events.send(PosEvent.SaleSaved(result))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _events.send(PosEvent.Error(error.toMessage()))
            } finally {
                _isSubmitting.value = false
            }
        }
    }

    private fun runOperation(success: PosEvent, action: suspend () -> Any?) {
        viewModelScope.launch {
            if (_isWorking.value) return@launch
            _isWorking.value = true
            try {
                action()
                _events.send(success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _events.send(PosEvent.Error(error.toMessage()))
            } finally {
                _isWorking.value = false
            }
        }
    }

    private fun Throwable.toMessage(): PosMessage = when (this) {
        PosError.InvalidProduct, PosError.InvalidQuantity -> PosMessage.INVALID
        PosError.InsufficientStock -> PosMessage.OUT_OF_STOCK
        PosError.CustomerRequired -> PosMessage.CUSTOMER_REQUIRED
        PosError.ProductNotFound -> PosMessage.PRODUCT_UNAVAILABLE
        PosError.PaymentInvalid -> PosMessage.PAYMENT_INVALID
        PosError.DuplicateName -> PosMessage.DUPLICATE
        is AuthException -> PosMessage.ACCESS_DENIED
        else -> PosMessage.GENERAL
    }

    private fun Throwable.approvalMessage(): String = when (this) {
        is AuthException -> when (val reason = failure) {
            com.aesprt.marisuki.domain.auth.AuthFailure.OwnerPinRejected -> "The owner PIN did not match. Enter it again."
            com.aesprt.marisuki.domain.auth.AuthFailure.RateLimited -> "Too many PIN attempts. Wait before trying again."
            is com.aesprt.marisuki.domain.auth.AuthFailure.Validation -> reason.message
            else -> "Owner approval could not be completed."
        }
        else -> message?.takeIf(String::isNotBlank) ?: "Owner approval could not be completed."
    }

    companion object {
        private const val SCALE = 1_000L
        fun calculateAmount(priceCentavos: Long, quantityMilliUnits: Long): Long =
            BigDecimal.valueOf(priceCentavos)
                .multiply(BigDecimal.valueOf(quantityMilliUnits))
                .divide(BigDecimal.valueOf(SCALE), 0, RoundingMode.HALF_UP)
                .longValueExact()

        fun parseCentavos(value: String): Long? = runCatching {
            val normalized = value.trim().replace("₱", "").replace(",", "").trim()
            if (normalized.isBlank() || !normalized.matches(Regex("\\d{1,9}(\\.\\d{1,2})?"))) return null
            BigDecimal(normalized).setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
        }.getOrNull()

        fun parseQuantityMilliUnits(value: String): Long? = runCatching {
            val normalized = value.trim().replace(",", "")
            if (normalized.isBlank() || !normalized.matches(Regex("\\d{1,9}(\\.\\d{1,3})?"))) return null
            BigDecimal(normalized).setScale(3, RoundingMode.UNNECESSARY).movePointRight(3).longValueExact()
        }.getOrNull()

        fun formatPeso(centavos: Long): String =
            NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-PH")).format(BigDecimal.valueOf(centavos, 2))

        fun formatQuantity(milliUnits: Long): String = BigDecimal.valueOf(milliUnits, 3).stripTrailingZeros().toPlainString()
    }
}
