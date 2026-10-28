package com.aesprt.marisuki

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aesprt.marisuki.data.PosRepository
import com.aesprt.marisuki.data.auth.AuthException
import com.aesprt.marisuki.data.importing.*
import com.aesprt.marisuki.data.local.PosDatabase
import com.aesprt.marisuki.domain.auth.*
import com.aesprt.marisuki.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductCatalogImportInstrumentedTest {
    private lateinit var database: PosDatabase
    private lateinit var repository: PosRepository
    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, PosDatabase::class.java).allowMainThreadQueries().build()
        repository = PosRepository(database, database.posDao(), PosAccessControl { AuthenticatedActor("test-owner", AccountRole.OWNER, 0) })
    }
    @After fun tearDown() { database.close() }
    private fun item(name: String = "Snack", barcode: String? = "00123") = NewProduct(name, barcode, "Snacks", "Piece", 1000, 800, 24000, 5000, true, listOf(ProductUnitInput("Bundle", 12000, 11800)))
    @Test fun savedCategoryAndCustomUnitsRemainAvailableWithoutAProduct() = runBlocking {
        assertEquals("Drinks", repository.saveCatalogChoice("category", " Drinks "))
        assertEquals("Drinks", repository.saveCatalogChoice("category", "drinks"))
        assertEquals(listOf("Drinks"), repository.observeCategoryNames().first())
        repository.saveCatalogChoice("stock", "Tray"); repository.saveCatalogChoice("selling", "Crate")
        val reopenedRepository = PosRepository(database, database.posDao(), PosAccessControl { AuthenticatedActor("test-owner", AccountRole.OWNER, 0) })
        assertTrue("Tray" in reopenedRepository.observeUnitNames(false).first())
        assertTrue("Crate" in reopenedRepository.observeUnitNames(true).first())
        assertEquals(0, reopenedRepository.observeProducts().first().size)
    }
    @Test fun bulkCreatesProductsStockUnitsAndReusableCategoriesOnlyOnce() = runBlocking {
        val batch = listOf(item(), item("Rice", null).copy(baseUnitLabel = "Kilo", wholeBaseUnitsOnly = false))
        assertFalse(repository.importProducts(batch).alreadyImported)
        assertTrue(repository.importProducts(batch.reversed()).alreadyImported)
        val products = repository.observeProducts().first()
        assertEquals(2, products.size)
        products.forEach { assertEquals(24000L, it.onHandMilliUnits); assertEquals(2, repository.productUnits(it.id).size) }
        assertTrue("Snacks" in repository.observeCategoryNames().first())
        assertTrue("Bundle" in repository.observeUnitNames(true).first())
    }
    @Test fun usedUnitNamesRemainSavedWhenAProductIsEditedToDifferentUnits() = runBlocking {
        val original = item().copy(baseUnitLabel = "Tray", extraUnits = listOf(ProductUnitInput("Crate", 12000, 11800)))
        val id = repository.createProduct(original)
        repository.updateProduct(id, original.copy(baseUnitLabel = "Piece", extraUnits = emptyList()))
        assertTrue("Tray" in repository.observeUnitNames(false).first())
        assertTrue("Crate" in repository.observeUnitNames(true).first())
        assertEquals("Piece", repository.observeProducts().first().single().baseUnitLabel)
        assertEquals(24000L, repository.observeProducts().first().single().onHandMilliUnits)
    }
    @Test fun oneInvalidRowRollsBackProductsCategoriesUnitsAndBalances() = runBlocking {
        try { repository.importProducts(listOf(item(), item("Bad", "002").copy(baseUnitPriceCentavos = -1))); fail("Must reject batch") } catch (_: PosError) { }
        assertTrue(repository.observeProducts().first().isEmpty())
        assertTrue(repository.observeCategoryNames().first().isEmpty())
        assertFalse("Bundle" in repository.observeUnitNames(true).first())
    }
    @Test fun duplicateBarcodeAgainstExistingProductRollsBackEntireBatch() = runBlocking {
        repository.createProduct(item())
        try { repository.importProducts(listOf(item("New", "999"), item())); fail("Must reject duplicate") } catch (_: Exception) { }
        assertEquals(1, repository.observeProducts().first().size)
        assertEquals(24000L, repository.observeProducts().first().single().onHandMilliUnits)
        val header = ProductImport.headers.joinToString(",")
        val preview = ProductImport.parse((header + "\nOther,00123,Snacks,Piece,10,0,0,0,No,,,,,,").toByteArray(), false)
        assertTrue(repository.reviewProductImport(preview).issues.any { it.problem == ImportProblem.DUPLICATE_BARCODE })
    }
    @Test fun stockAndPurchaseCostPermissionsAreCheckedForBulkInsert() = runBlocking {
        val restricted = PosRepository(database, database.posDao(), PosAccessControl { permission ->
            if (permission == PosPermission.ADD_STOCK || permission == PosPermission.EDIT_PURCHASE_COST) throw AuthException(AuthFailure.Validation("Denied"))
            AuthenticatedActor("cashier", AccountRole.CASHIER, 0)
        })
        try { restricted.importProducts(listOf(item().copy(purchaseCostCentavos = 0, initialStockMilliUnits = 0), item("Denied", "002"))); fail("Must enforce permissions") } catch (_: AuthException) { }
        assertTrue(repository.observeProducts().first().isEmpty())
    }
    @Test fun excelTemplateIsReadableOnAndroidAndItsGuideIsNeverImported() {
        val bytes = InstrumentationRegistry.getInstrumentation().targetContext.assets.open("templates/products.xlsx").use { it.readBytes() }
        try { ProductImport.parse(bytes, true); fail("Template must start empty") } catch (e: ImportFileException) { assertEquals(ImportProblem.EMPTY, e.problem) }
    }
}
