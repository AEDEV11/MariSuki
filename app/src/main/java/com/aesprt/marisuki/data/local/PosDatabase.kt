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
        CashierShiftEntity::class,
        ShiftCashMovementEntity::class,
        ShiftReportEntity::class,
    ],
    version = 8,
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

        /**
         * v6 -> v7:
         * - Adds optional GCash/Maya referenceNumber on payments.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `payments` ADD COLUMN `referenceNumber` TEXT")
            }
        }

        /**
         * v7 -> v8:
         * - Links sales and refunds to cashier_shifts via shiftId.
         * - Creates cashier_shifts, shift_cash_movements, and shift_reports tables.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sales` ADD COLUMN `shiftId` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_sales_shiftId` ON `sales` (`shiftId`)")
                db.execSQL("ALTER TABLE `refunds` ADD COLUMN `shiftId` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_refunds_shiftId` ON `refunds` (`shiftId`)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `cashier_shifts` (
                        `shiftId` TEXT NOT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        `cashierId` TEXT NOT NULL,
                        `cashierNameSnapshot` TEXT NOT NULL,
                        `terminalId` TEXT NOT NULL,
                        `businessDate` TEXT NOT NULL,
                        `startedAt` INTEGER NOT NULL,
                        `endedAt` INTEGER,
                        `openingCash` INTEGER NOT NULL,
                        `expectedCash` INTEGER NOT NULL,
                        `actualCash` INTEGER,
                        `variance` INTEGER,
                        `reconciliationStatus` TEXT NOT NULL,
                        `shiftStatus` TEXT NOT NULL,
                        `closingRemarks` TEXT,
                        `approvedBy` TEXT,
                        `transactionCount` INTEGER NOT NULL,
                        `grossSalesCentavos` INTEGER NOT NULL,
                        `discountsCentavos` INTEGER NOT NULL,
                        `refundsCentavos` INTEGER NOT NULL,
                        `netSalesCentavos` INTEGER NOT NULL,
                        `cashPaymentsCentavos` INTEGER NOT NULL,
                        `gcashPaymentsCentavos` INTEGER NOT NULL,
                        `mayaPaymentsCentavos` INTEGER NOT NULL,
                        `cardPaymentsCentavos` INTEGER NOT NULL,
                        `creditSalesCentavos` INTEGER NOT NULL,
                        `nonCashPaymentsCentavos` INTEGER NOT NULL,
                        `cashInCentavos` INTEGER NOT NULL,
                        `cashOutCentavos` INTEGER NOT NULL,
                        `adjustmentsCentavos` INTEGER NOT NULL,
                        `cashRefundsCentavos` INTEGER NOT NULL,
                        `nonCashRefundsCentavos` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`shiftId`)
                    )""".trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cashier_shifts_cashierId` ON `cashier_shifts` (`cashierId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cashier_shifts_terminalId` ON `cashier_shifts` (`terminalId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cashier_shifts_businessDate` ON `cashier_shifts` (`businessDate`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cashier_shifts_shiftStatus` ON `cashier_shifts` (`shiftStatus`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cashier_shifts_startedAt` ON `cashier_shifts` (`startedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cashier_shifts_reconciliationStatus` ON `cashier_shifts` (`reconciliationStatus`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_cashier_shifts_idempotencyKey` ON `cashier_shifts` (`idempotencyKey`)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `shift_cash_movements` (
                        `movementId` TEXT NOT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        `shiftId` TEXT NOT NULL,
                        `movementType` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL,
                        `createdBy` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`movementId`),
                        FOREIGN KEY(`shiftId`) REFERENCES `cashier_shifts`(`shiftId`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )""".trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_cash_movements_shiftId` ON `shift_cash_movements` (`shiftId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_cash_movements_createdAt` ON `shift_cash_movements` (`createdAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shift_cash_movements_idempotencyKey` ON `shift_cash_movements` (`idempotencyKey`)")

                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `shift_reports` (
                        `reportId` TEXT NOT NULL,
                        `shiftId` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `fileUri` TEXT,
                        `generatedAt` INTEGER NOT NULL,
                        `reportStatus` TEXT NOT NULL,
                        PRIMARY KEY(`reportId`),
                        FOREIGN KEY(`shiftId`) REFERENCES `cashier_shifts`(`shiftId`) ON UPDATE NO ACTION ON DELETE RESTRICT
                    )""".trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shift_reports_shiftId` ON `shift_reports` (`shiftId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_shift_reports_generatedAt` ON `shift_reports` (`generatedAt`)")
            }
        }
    }
}
