package com.aesprt.marisuki.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aesprt.marisuki.data.local.dao.PosDao
import com.aesprt.marisuki.data.local.entity.*

@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        ProductUnitEntity::class,
        InventoryBalanceEntity::class,
        InventoryMovementEntity::class,
        CustomerEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        PaymentEntity::class,
        CustomerLedgerEntryEntity::class,
        CustomerPaymentEntity::class,
        SupplierEntity::class,
        StockDeliveryEntity::class,
        StockDeliveryItemEntity::class,
        ExpenseEntity::class,
        RefundEntity::class,
        RefundItemEntity::class,
        AuditLogEntity::class,
        AppSettingEntity::class,
        PrinterConfigurationEntity::class,
        StoreProfileEntity::class,
        SetupDraftEntity::class,
        StaffAccountEntity::class,
        StaffPermissionEntity::class,
        AuthAttemptStateEntity::class,
        RestrictedActionAuditEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class PosDatabase : RoomDatabase() {
    abstract fun posDao(): PosDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE refunds ADD COLUMN refundMethod TEXT NOT NULL DEFAULT 'CASH'")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sales` ADD COLUMN `cashierAccountId` TEXT")
                db.execSQL("ALTER TABLE `audit_log` ADD COLUMN `actorAccountId` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_cashierAccountId` ON `sales` (`cashierAccountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_actorAccountId` ON `audit_log` (`actorAccountId`)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `store_profile` (
                        `id` TEXT NOT NULL,
                        `storeName` TEXT NOT NULL,
                        `address` TEXT,
                        `contactNumber` TEXT,
                        `logoBytes` BLOB,
                        `currencyCode` TEXT NOT NULL,
                        `language` TEXT NOT NULL,
                        `theme` TEXT NOT NULL,
                        `defaultCashierScreen` TEXT NOT NULL,
                        `lowStockAlertsEnabled` INTEGER NOT NULL,
                        `allowNegativeStock` INTEGER NOT NULL,
                        `receiptShowStoreInfo` INTEGER NOT NULL,
                        `receiptFooter` TEXT,
                        `setupComplete` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent(),
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `setup_drafts` (
                        `id` INTEGER NOT NULL,
                        `step` INTEGER NOT NULL,
                        `storeName` TEXT NOT NULL,
                        `address` TEXT,
                        `contactNumber` TEXT,
                        `logoBytes` BLOB,
                        `language` TEXT NOT NULL,
                        `theme` TEXT NOT NULL,
                        `defaultCashierScreen` TEXT NOT NULL,
                        `lowStockAlertsEnabled` INTEGER NOT NULL,
                        `allowNegativeStock` INTEGER NOT NULL,
                        `receiptShowStoreInfo` INTEGER NOT NULL,
                        `receiptFooter` TEXT,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent(),
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `staff_accounts` (
                        `id` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `username` TEXT NOT NULL,
                        `usernameNormalized` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `loginCredentialType` TEXT NOT NULL,
                        `loginSalt` TEXT NOT NULL,
                        `loginVerifier` TEXT NOT NULL,
                        `ownerPinSalt` TEXT,
                        `ownerPinVerifier` TEXT,
                        `enabled` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        `permissionRevision` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )""".trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_staff_accounts_usernameNormalized` ON `staff_accounts` (`usernameNormalized`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `staff_permissions` (
                        `accountId` TEXT NOT NULL,
                        `permissionCode` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        PRIMARY KEY(`accountId`, `permissionCode`),
                        FOREIGN KEY(`accountId`) REFERENCES `staff_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )""".trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_staff_permissions_accountId` ON `staff_permissions` (`accountId`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `auth_attempt_state` (
                        `lockKey` TEXT NOT NULL,
                        `failedAttempts` INTEGER NOT NULL,
                        `lockedUntilMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`lockKey`)
                    )""".trimIndent(),
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `restricted_action_audit` (
                        `id` TEXT NOT NULL,
                        `requesterAccountId` TEXT NOT NULL,
                        `approverAccountId` TEXT,
                        `operationKey` TEXT NOT NULL,
                        `targetId` TEXT,
                        `payloadDigest` TEXT NOT NULL,
                        `beforeValue` TEXT,
                        `afterValue` TEXT,
                        `authorizationResult` TEXT NOT NULL,
                        `operationResult` TEXT NOT NULL,
                        `requestedAtMillis` INTEGER NOT NULL,
                        `expiresAtMillis` INTEGER NOT NULL,
                        `completedAtMillis` INTEGER,
                        PRIMARY KEY(`id`)
                    )""".trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_restricted_action_audit_requestedAtMillis` ON `restricted_action_audit` (`requestedAtMillis`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_restricted_action_audit_requesterAccountId` ON `restricted_action_audit` (`requesterAccountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_restricted_action_audit_approverAccountId` ON `restricted_action_audit` (`approverAccountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_restricted_action_audit_operationKey` ON `restricted_action_audit` (`operationKey`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sales` ADD COLUMN `salesChannel` TEXT NOT NULL DEFAULT 'TINGI'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `products` ADD COLUMN `imageFileName` TEXT")
            }
        }
    }
}
