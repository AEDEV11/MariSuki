package com.aesprt.marisuki

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.testing.MigrationTestHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PosDatabaseMigrationTest {
    @Test
    fun migrationThreeToSixPreservesExistingRecordsAndAddsPhotoReference() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val helper = MigrationTestHelper(
            instrumentation,
            com.aesprt.marisuki.data.local.PosDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )
        val dbName = "migration-v3-to-v4-test.db"
        instrumentation.targetContext.deleteDatabase(dbName)
        helper.createDatabase(dbName, 3).apply {
            execSQL("INSERT INTO customers (`id`, `name`, `phone`, `createdAtMillis`, `active`) VALUES ('customer-legacy', 'Legacy Customer', NULL, 1111, 1)")
            execSQL("INSERT INTO products (`id`, `name`, `barcode`, `categoryId`, `baseUnitLabel`, `baseUnitPriceCentavos`, `purchaseCostCentavos`, `minimumStockMilliUnits`, `wholeBaseUnitsOnly`, `active`, `createdAtMillis`, `updatedAtMillis`, `favorite`) VALUES ('product-legacy', 'Legacy Sardines', NULL, NULL, 'Can', 1800, 1200, 5000, 1, 1, 1111, 1111, 0)")
            execSQL("INSERT INTO product_units (`id`, `productId`, `label`, `baseQuantityMilliUnits`, `priceCentavos`, `wholeSellQuantityOnly`) VALUES ('unit-legacy', 'product-legacy', 'Can', 1000, 1800, 1)")
            execSQL("INSERT INTO inventory_balances (`productId`, `onHandMilliUnits`, `updatedAtMillis`) VALUES ('product-legacy', 12000, 1111)")
            execSQL("INSERT INTO sales (`id`, `idempotencyKey`, `createdAtMillis`, `totalCentavos`, `customerId`, `status`, `cashierMode`) VALUES ('sale-legacy', 'sale-key-legacy', 1111, 3600, 'customer-legacy', 'COMPLETED', 0)")
            execSQL("INSERT INTO app_settings (`key`, `value`) VALUES ('preserve-marker', 'legacy-store-data')")
            execSQL("INSERT INTO audit_log (`id`, `operation`, `targetId`, `details`, `occurredAtMillis`) VALUES ('legacy-audit', 'SALE_COMPLETED', 'sale-1', 'preserved audit row', 1234)")
            close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            8,
            true,
            com.aesprt.marisuki.data.local.PosDatabase.MIGRATION_3_4,
            com.aesprt.marisuki.data.local.PosDatabase.MIGRATION_4_5,
            com.aesprt.marisuki.data.local.PosDatabase.MIGRATION_5_6,
            com.aesprt.marisuki.data.local.PosDatabase.MIGRATION_6_7,
            com.aesprt.marisuki.data.local.PosDatabase.MIGRATION_7_8,
        ).use { migrated ->
            migrated.query("SELECT salesChannel, shiftId FROM sales WHERE id = 'sale-legacy'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("TINGI", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
            migrated.query("SELECT `value` FROM app_settings WHERE `key` = 'preserve-marker'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("legacy-store-data", cursor.getString(0))
            }
            migrated.query("SELECT COUNT(*) FROM audit_log WHERE id = 'legacy-audit'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            migrated.query("SELECT (SELECT COUNT(*) FROM products WHERE id = 'product-legacy') + (SELECT COUNT(*) FROM product_units WHERE id = 'unit-legacy') + (SELECT COUNT(*) FROM inventory_balances WHERE productId = 'product-legacy') + (SELECT COUNT(*) FROM sales WHERE id = 'sale-legacy') + (SELECT COUNT(*) FROM customers WHERE id = 'customer-legacy')").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(5, cursor.getInt(0))
            }
            migrated.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('store_profile', 'staff_accounts', 'staff_permissions', 'restricted_action_audit', 'cashier_shifts', 'shift_cash_movements', 'shift_reports')").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(7, cursor.getInt(0))
            }
            migrated.query("SELECT imageFileName FROM products WHERE id = 'product-legacy'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertTrue(cursor.isNull(0))
            }
        }
        instrumentation.targetContext.deleteDatabase(dbName)
    }

    @Test
    fun migrationSevenToEightPreservesPaymentReferenceAndAddsShiftTables() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val helper = MigrationTestHelper(
            instrumentation,
            com.aesprt.marisuki.data.local.PosDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )
        val dbName = "migration-v7-to-v8-test.db"
        instrumentation.targetContext.deleteDatabase(dbName)
        helper.createDatabase(dbName, 7).apply {
            execSQL("INSERT INTO sales (`id`, `idempotencyKey`, `createdAtMillis`, `totalCentavos`, `customerId`, `status`, `cashierMode`, `cashierAccountId`, `salesChannel`) VALUES ('sale-v7', 'sale-key-v7', 2222, 5000, NULL, 'COMPLETED', 0, NULL, 'MIXED')")
            execSQL("INSERT INTO payments (`id`, `saleId`, `method`, `amountAppliedCentavos`, `tenderedCentavos`, `changeCentavos`, `createdAtMillis`, `referenceNumber`) VALUES ('pay-v7', 'sale-v7', 'GCASH', 5000, 5000, 0, 2222, 'GC-998877')")
            close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            8,
            true,
            com.aesprt.marisuki.data.local.PosDatabase.MIGRATION_7_8,
        ).use { migrated ->
            migrated.query("SELECT referenceNumber FROM payments WHERE id = 'pay-v7'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("GC-998877", cursor.getString(0))
            }
            migrated.query("SELECT salesChannel, shiftId FROM sales WHERE id = 'sale-v7'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("MIXED", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
        }
        instrumentation.targetContext.deleteDatabase(dbName)
    }
}
