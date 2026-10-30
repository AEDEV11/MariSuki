package com.aesprt.marisuki

import androidx.room.withTransaction
import com.aesprt.marisuki.data.PosRepository
import com.aesprt.marisuki.data.auth.AuthRepository
import com.aesprt.marisuki.data.auth.CredentialSecurity
import com.aesprt.marisuki.data.auth.EncryptedSessionStore
import com.aesprt.marisuki.data.local.PosDatabase
import com.aesprt.marisuki.data.local.entity.AuthAttemptStateEntity
import com.aesprt.marisuki.data.local.entity.RestrictedActionAuditEntity
import com.aesprt.marisuki.data.local.entity.StaffAccountEntity
import com.aesprt.marisuki.data.local.entity.StaffPermissionEntity
import com.aesprt.marisuki.data.local.entity.StoreProfileEntity
import com.aesprt.marisuki.domain.auth.AccountRole
import com.aesprt.marisuki.domain.auth.AuthenticatedActor
import com.aesprt.marisuki.domain.auth.DEFAULT_CASHIER_PERMISSIONS
import com.aesprt.marisuki.domain.auth.PosAccessControl
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.domain.model.CartLineInput
import com.aesprt.marisuki.domain.model.CheckoutCommand
import com.aesprt.marisuki.domain.model.DeliveryLineInput
import com.aesprt.marisuki.domain.model.NewProduct
import com.aesprt.marisuki.domain.model.PaymentMethod
import com.aesprt.marisuki.domain.model.ProductUnitInput
import com.aesprt.marisuki.domain.model.RefundCommand
import com.aesprt.marisuki.domain.model.RefundLineInput
import com.aesprt.marisuki.domain.model.SaleChannel
import com.aesprt.marisuki.domain.model.TenderInput
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext

private const val DEMO_STORE_NAME = "MariSuki Demo Grocery"
private const val DEMO_OWNER_ID = "demo-owner-account"
private const val DEMO_OWNER_USERNAME = "demo.owner"
private const val DEMO_OWNER_PASSWORD = "DemoOwner26!"
private const val DEMO_OWNER_APPROVAL_PIN = "880026"
private const val DEMO_MARIA_PIN = "123456"
private const val DEMO_ANA_PIN = "654321"
private val demoReady = CompletableDeferred<Unit>()

@Volatile
var demoSeedFailure: String? = null
    private set

fun installVariantDemoData(app: MariSukiApplication) {
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try {
            seedIfFresh(GlobalContext.get().get())
        } catch (error: Throwable) {
            demoSeedFailure = error.message ?: "Demo data setup failed."
        } finally {
            demoReady.complete(Unit)
        }
    }
}

suspend fun awaitVariantDemoData() = demoReady.await()

object DemoDataInstaller {
    suspend fun resetDemoData() = withContext(Dispatchers.IO) {
        val koin = GlobalContext.get()
        val database = koin.get<PosDatabase>()
        val profile = database.posDao().storeProfile()
        check(profile?.storeName == DEMO_STORE_NAME) { "Reset is available only for the isolated demo database." }
        koin.get<EncryptedSessionStore>().clear()
        database.clearAllTables()
        seedIfFresh(database)
    }

    suspend fun reseedProducts() = withContext(Dispatchers.IO) {
        val database = GlobalContext.get().get<PosDatabase>()
        check(database.posDao().storeProfile()?.storeName == DEMO_STORE_NAME) { "Product seeding is available only for demo data." }
        val repository = demoRepository(database)
        val existing = database.posDao().observeProducts().first()
        val missing = productFixtures().filterNot { fixture -> existing.any { it.name == fixture.name } }
        database.withTransaction { missing.forEach { fixture -> repository.createProduct(fixture.toNewProduct()) } }
    }

    suspend fun createSampleTransactions() = withContext(Dispatchers.IO) {
        val database = GlobalContext.get().get<PosDatabase>()
        check(database.posDao().storeProfile()?.storeName == DEMO_STORE_NAME) { "Transaction fixtures are available only for demo data." }
        seedTransactions(database)
    }

    suspend fun setLowStockTestState(outOfStock: Boolean) = withContext(Dispatchers.IO) {
        val database = GlobalContext.get().get<PosDatabase>()
        check(database.posDao().storeProfile()?.storeName == DEMO_STORE_NAME) { "Stock test controls are available only for demo data." }
        val repository = demoRepository(database)
        val product = database.posDao().observeProducts().first().firstOrNull { it.minimumStockMilliUnits > 0 }
            ?: error("No demo stock record exists.")
        val target = if (outOfStock) 0L else product.minimumStockMilliUnits / 2
        val delta = target - product.onHandMilliUnits
        if (delta != 0L) repository.adjustStock(product.id, delta, "COUNT", "DEMO-STOCK-TEST-${if (outOfStock) "OUT" else "LOW"}-${System.currentTimeMillis()}")
    }
}

private suspend fun seedIfFresh(database: PosDatabase) {
    val dao = database.posDao()
    if (dao.storeProfile() != null || dao.staffAccountCount() > 0 || dao.hasOperationalData()) return
    val verifier = CredentialSecurity()
    val ownerLogin = verifier.createVerifier(DEMO_OWNER_PASSWORD)
    val ownerPin = verifier.createVerifier(DEMO_OWNER_APPROVAL_PIN)
    val mariaVerifier = verifier.createVerifier(DEMO_MARIA_PIN)
    val anaVerifier = verifier.createVerifier(DEMO_ANA_PIN)
    val now = System.currentTimeMillis()
    val owner = StaffAccountEntity(
        id = DEMO_OWNER_ID, displayName = "Demo Owner", username = DEMO_OWNER_USERNAME,
        usernameNormalized = DEMO_OWNER_USERNAME, role = AccountRole.OWNER.name,
        loginCredentialType = "PASSWORD", loginSalt = ownerLogin.salt, loginVerifier = ownerLogin.verifier,
        ownerPinSalt = ownerPin.salt, ownerPinVerifier = ownerPin.verifier, enabled = true,
        createdAtMillis = now, updatedAtMillis = now,
    )
    val maria = demoCashier("demo-cashier-maria", "Maria", "maria", mariaVerifier.salt, mariaVerifier.verifier, now)
    val anaPermissions = DEFAULT_CASHIER_PERMISSIONS - setOf(PosPermission.ACCEPT_UTANG, PosPermission.ACCEPT_PARTIAL_PAYMENT)
    val ana = demoCashier("demo-cashier-ana", "Ana", "ana", anaVerifier.salt, anaVerifier.verifier, now)
    val repository = demoRepository(database)
    database.withTransaction {
        dao.saveStoreProfile(
            StoreProfileEntity(
                storeName = DEMO_STORE_NAME, address = "Debug fixture · not a real store", contactNumber = null,
                logoBytes = null, language = "en", theme = "SYSTEM", defaultCashierScreen = "SALES",
                lowStockAlertsEnabled = true, allowNegativeStock = false, receiptShowStoreInfo = true,
                receiptFooter = "DEBUG TEST DATA", setupComplete = true, createdAtMillis = now, updatedAtMillis = now,
            ),
        )
        dao.insertStaffAccount(owner)
        dao.insertStaffAccount(maria)
        dao.insertStaffAccount(ana)
        dao.saveStaffPermissions(DEFAULT_CASHIER_PERMISSIONS.map { StaffPermissionEntity(maria.id, it.name, true) })
        dao.saveStaffPermissions(anaPermissions.map { StaffPermissionEntity(ana.id, it.name, true) })
        seedProducts(repository)
        seedTransactions(database, repository)
    }
}

private fun demoCashier(id: String, name: String, username: String, salt: String, verifier: String, now: Long) = StaffAccountEntity(
    id = id, displayName = name, username = username, usernameNormalized = username, role = AccountRole.CASHIER.name,
    loginCredentialType = "PIN", loginSalt = salt, loginVerifier = verifier, ownerPinSalt = null, ownerPinVerifier = null,
    enabled = true, createdAtMillis = now, updatedAtMillis = now,
)

private suspend fun seedProducts(repository: PosRepository) {
    productFixtures().forEach { fixture -> repository.createProduct(fixture.toNewProduct()) }
}

private data class ProductFixture(
    val name: String,
    val category: String,
    val unit: String,
    val cost: Long,
    val price: Long,
    val stockMilli: Long,
    val lowStockMilli: Long,
    val packCount: Long = 0,
    val packPrice: Long = 0,
    val outOfStock: Boolean = false,
) {
    fun toNewProduct() = NewProduct(
        name = name,
        barcode = "MSKD" + name.hashCode().toUInt().toString(16).padStart(8, '0').takeLast(8).uppercase(),
        categoryName = category,
        baseUnitLabel = unit,
        baseUnitPriceCentavos = price,
        purchaseCostCentavos = cost,
        initialStockMilliUnits = if (outOfStock) 0 else stockMilli,
        minimumStockMilliUnits = lowStockMilli,
        wholeBaseUnitsOnly = unit !in setOf("Kilo", "Gram"),
        extraUnits = if (packCount > 0) listOf(ProductUnitInput(if (unit == "Kilo") "Sack" else "Case", packCount * PosRepository.SCALE, packPrice)) else emptyList(),
    )
}

private fun productFixtures(): List<ProductFixture> = listOf(
    ProductFixture("Sardines Tomato", "Canned goods", "Can", 1_350, 1_800, 24_000, 5_000, 5, 8_000),
    ProductFixture("Sardines Hot", "Canned goods", "Can", 1_400, 1_900, 18_000, 4_000, 5, 8_500),
    ProductFixture("Corned Beef 150g", "Canned goods", "Can", 3_200, 4_200, 20_000, 5_000, 6, 23_000),
    ProductFixture("Corned Beef 260g", "Canned goods", "Can", 5_800, 7_500, 12_000, 3_000),
    ProductFixture("Canned Tuna", "Canned goods", "Can", 2_100, 2_800, 16_000, 4_000),
    ProductFixture("Instant Noodles Chicken", "Grocery", "Pack", 850, 1_200, 40_000, 8_000, 5, 5_500),
    ProductFixture("Instant Noodles Beef", "Grocery", "Pack", 850, 1_200, 32_000, 8_000, 5, 5_500),
    ProductFixture("Rice Dinorado", "Rice and grains", "Kilo", 5_200, 6_500, 52_000, 10_000, 25, 157_500),
    ProductFixture("Rice Well-milled", "Rice and grains", "Kilo", 4_300, 5_200, 90_000, 15_000, 25, 125_000),
    ProductFixture("White Sugar", "Rice and grains", "Kilo", 5_000, 6_200, 27_000, 8_000),
    ProductFixture("Brown Sugar", "Rice and grains", "Kilo", 4_800, 6_000, 0, 5_000, outOfStock = true),
    ProductFixture("3-in-1 Coffee", "Coffee and drinks", "Sachet", 700, 1_000, 50_000, 10_000, 10, 9_000),
    ProductFixture("Ground Coffee 100g", "Coffee and drinks", "Pack", 6_500, 8_500, 8_000, 3_000),
    ProductFixture("Powdered Milk", "Coffee and drinks", "Sachet", 1_300, 1_800, 20_000, 5_000),
    ProductFixture("Shampoo Sachet", "Personal care", "Sachet", 550, 800, 35_000, 8_000, 12, 8_500),
    ProductFixture("Laundry Detergent Sachet", "Household", "Sachet", 650, 900, 28_000, 8_000, 12, 9_500),
    ProductFixture("Bath Soap", "Personal care", "Bar", 1_500, 2_000, 21_000, 5_000, 3, 5_700),
    ProductFixture("Dishwashing Liquid", "Household", "Bottle", 3_200, 4_500, 7_000, 2_000),
    ProductFixture("Soft Drink Cola 1.5L", "Beverages", "Bottle", 5_500, 7_000, 14_000, 4_000, 6, 40_000),
    ProductFixture("Soft Drink Orange", "Beverages", "Bottle", 2_000, 2_800, 19_000, 5_000),
    ProductFixture("Bottled Water 500ml", "Beverages", "Bottle", 650, 1_000, 48_000, 10_000, 12, 10_500),
    ProductFixture("Bottled Water 1L", "Beverages", "Bottle", 1_100, 1_600, 25_000, 6_000),
    ProductFixture("Butter Cookies", "Snacks", "Pack", 1_800, 2_500, 18_000, 4_000),
    ProductFixture("Crackers", "Snacks", "Pack", 1_200, 1_700, 30_000, 6_000, 10, 15_000),
    ProductFixture("Cooking Oil", "Cooking", "Bottle", 6_000, 7_500, 10_000, 3_000, 6, 43_000),
    ProductFixture("Loaf Bread", "Bakery", "Loaf", 4_000, 5_500, 5_000, 3_000),
    ProductFixture("Egg Large", "Fresh goods", "Piece", 850, 1_000, 90_000, 20_000, 30, 28_500),
    ProductFixture("Egg Medium", "Fresh goods", "Piece", 750, 900, 65_000, 15_000, 30, 25_500),
    ProductFixture("Instant Pancit Canton", "Grocery", "Pack", 1_000, 1_400, 36_000, 8_000, 6, 8_000),
    ProductFixture("Tomato Sauce", "Canned goods", "Pouch", 1_100, 1_500, 16_000, 4_000),
    ProductFixture("Soy Sauce", "Cooking", "Bottle", 1_800, 2_500, 14_000, 4_000),
    ProductFixture("Vinegar", "Cooking", "Bottle", 1_400, 2_000, 15_000, 4_000),
    ProductFixture("Toothpaste Sachet", "Personal care", "Sachet", 500, 700, 22_000, 5_000),
    ProductFixture("Toilet Tissue", "Household", "Roll", 1_000, 1_500, 12_000, 3_000),
    ProductFixture("Candies Assorted", "Snacks", "Piece", 100, 200, 110_000, 20_000),
    ProductFixture("Chocolate Bar", "Snacks", "Bar", 1_800, 2_500, 4_000, 3_000),
    ProductFixture("Corn Chips", "Snacks", "Pack", 1_500, 2_000, 20_000, 5_000),
    ProductFixture("Margarine", "Cooking", "Tub", 2_800, 3_800, 9_000, 2_000),
    ProductFixture("Ketchup Sachet", "Cooking", "Sachet", 300, 500, 25_000, 6_000),
    ProductFixture("Salt", "Rice and grains", "Pack", 700, 1_000, 18_000, 4_000),
)

private suspend fun seedTransactions(database: PosDatabase, existingRepository: PosRepository? = null) {
    val dao = database.posDao()
    val repository = existingRepository ?: demoRepository(database)
    val products = dao.observeProducts().first()
    val customerId = dao.observeCustomers().first().firstOrNull()?.id ?: repository.createCustomer("Aling Rosa", "09170000001")
    val wholesaleProduct = products.first { it.name == "Rice Well-milled" }
    val cannedProduct = products.first { it.name == "Sardines Tomato" }
    val noodleProduct = products.first { it.name == "Instant Noodles Chicken" }
    var seedNow = System.currentTimeMillis()
    val timedRepository = PosRepository(database, dao, PosAccessControl { AuthenticatedActor(DEMO_OWNER_ID, AccountRole.OWNER, 0) }) { seedNow }
    val currentDay = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    suspend fun sale(key: String, dateOffset: Int, productId: String, unitLabel: String, method: PaymentMethod?, customerId: String? = null, paid: Long? = null, channel: SaleChannel = SaleChannel.TINGI) {
        if (dao.saleByIdempotencyKey(key) != null) return
        seedNow = currentDay + dateOffset * 86_400_000L + 10 * 3_600_000L + key.hashCode().toUInt().toLong() % 7_200_000L
        val unit = dao.productUnits(productId).first { it.label.equals(unitLabel, true) }
        val total = unit.priceCentavos
        val reference = if (method == null || method == PaymentMethod.CASH) null else "DEMO-${key.takeLast(12)}"
        val tenders = if (method == null) emptyList() else listOf(TenderInput(method, paid ?: total, reference))
        timedRepository.checkout(CheckoutCommand(key, listOf(CartLineInput(productId, unit.id, PosRepository.SCALE)), tenders, customerId, salesChannel = channel))
    }
    sale("DEMO-SALE-CASH-TODAY", 0, cannedProduct.id, "Can", PaymentMethod.CASH)
    sale("DEMO-SALE-GCASH-TODAY", 0, noodleProduct.id, "Pack", PaymentMethod.GCASH)
    sale("DEMO-SALE-MAYA-YESTERDAY", -1, cannedProduct.id, "Can", PaymentMethod.MAYA)
    sale("DEMO-SALE-UTANG-WEEK", -7, noodleProduct.id, "Pack", null, customerId)
    sale("DEMO-SALE-PARTIAL-MONTH", -30, cannedProduct.id, "Can", PaymentMethod.CASH, customerId, paid = 500)
    sale("DEMO-SALE-WHOLESALE-MONTH", -45, wholesaleProduct.id, "Sack", PaymentMethod.GCASH, channel = SaleChannel.WHOLESALE)
    sale("DEMO-SALE-HISTORY-QUARTER", -90, noodleProduct.id, "Pack", PaymentMethod.CASH)

    val saleForRefund = dao.saleByIdempotencyKey("DEMO-SALE-MAYA-YESTERDAY")
    if (saleForRefund != null && dao.refundByKey("DEMO-REFUND-YESTERDAY") == null) {
        val item = dao.saleItems(saleForRefund.id).first()
        seedNow = currentDay - 86_400_000L + 15 * 3_600_000L
        timedRepository.refundSale(RefundCommand("DEMO-REFUND-YESTERDAY", saleForRefund.id, "Demo damaged can", listOf(RefundLineInput(item.id, PosRepository.SCALE, true))))
    }
    seedNow = currentDay + 8 * 3_600_000L
    if (dao.stockDelivery("DEMO-DELIVERY-TODAY") == null) {
        timedRepository.recordStockDelivery(
            "DEMO-DELIVERY-TODAY", "North Market Wholesale", "09170000002",
            listOf(DeliveryLineInput(cannedProduct.id, 12 * PosRepository.SCALE, 16_200)), "Debug sample delivery",
        )
    }
    if (dao.expense("DEMO-EXPENSE-TRANSPORT") == null) timedRepository.recordExpense("DEMO-EXPENSE-TRANSPORT", 4_500, "Transport", "Supplier pickup", "CASH")
    if (dao.customerBalance(customerId) > 0) timedRepository.recordCustomerPayment(customerId, minOf(2_000L, dao.customerBalance(customerId)), PaymentMethod.CASH, "DEMO-CUSTOMER-PAYMENT-001")
}

private fun demoRepository(database: PosDatabase) = PosRepository(
    database,
    database.posDao(),
    PosAccessControl { AuthenticatedActor(DEMO_OWNER_ID, AccountRole.OWNER, 0) },
)
