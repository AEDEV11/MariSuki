package com.aesprt.marisuki.domain.auth

enum class AccountRole { OWNER, CASHIER }

/** Stable permission identifiers shared by the domain policy and the staff editor. */
enum class PosPermission(val group: String, val ownerOnly: Boolean = false) {
    CREATE_SALE("sales"),
    VIEW_SALES_HISTORY("sales"),
    REPRINT_SLIP("sales"),
    APPLY_DISCOUNT("sales"),
    OVERRIDE_SELLING_PRICE("sales"),
    CANCEL_ACTIVE_CART("sales"),
    VOID_COMPLETED_TRANSACTION("sales", ownerOnly = true),
    PROCESS_REFUND("sales", ownerOnly = true),
    ACCEPT_UTANG("sales"),
    ACCEPT_PARTIAL_PAYMENT("sales"),

    VIEW_PRODUCTS("inventory"),
    VIEW_STOCK("inventory"),
    ADD_PRODUCT("inventory"),
    EDIT_PRODUCT("inventory"),
    CHANGE_SELLING_PRICE("inventory"),
    VIEW_PURCHASE_COST("inventory"),
    EDIT_PURCHASE_COST("inventory"),
    ADD_STOCK("inventory"),
    ADJUST_STOCK("inventory", ownerOnly = true),
    RECORD_DAMAGED_STOCK("inventory", ownerOnly = true),
    ARCHIVE_PRODUCT("inventory"),

    VIEW_CUSTOMERS("customers"),
    ADD_CUSTOMER("customers"),
    RECORD_UTANG("customers"),
    VIEW_CUSTOMER_BALANCES("customers"),
    ACCEPT_UTANG_PAYMENTS("customers"),
    ADJUST_CREDIT_BALANCES("customers", ownerOnly = true),

    VIEW_TODAYS_SALES("reports"),
    VIEW_HISTORICAL_SALES("reports"),
    VIEW_PROFIT("reports"),
    VIEW_EXPENSES("reports"),
    VIEW_STOCK_VALUATION("reports"),
    EXPORT_REPORTS("reports"),

    MANAGE_SUPPLIERS("administration"),
    MANAGE_EXPENSES("administration"),
    MANAGE_PRINTERS("administration"),
    MANAGE_STAFF("administration", ownerOnly = true),
    CHANGE_SETTINGS("administration", ownerOnly = true),
    CREATE_BACKUPS("administration"),
    RESTORE_BACKUPS("administration", ownerOnly = true),
}

val DEFAULT_CASHIER_PERMISSIONS: Set<PosPermission> = setOf(
    PosPermission.CREATE_SALE,
    PosPermission.VIEW_SALES_HISTORY,
    PosPermission.REPRINT_SLIP,
    PosPermission.CANCEL_ACTIVE_CART,
    PosPermission.ACCEPT_UTANG,
    PosPermission.ACCEPT_PARTIAL_PAYMENT,
    PosPermission.VIEW_PRODUCTS,
    PosPermission.VIEW_STOCK,
    PosPermission.VIEW_CUSTOMERS,
    PosPermission.ADD_CUSTOMER,
    PosPermission.RECORD_UTANG,
    PosPermission.VIEW_CUSTOMER_BALANCES,
    PosPermission.ACCEPT_UTANG_PAYMENTS,
    PosPermission.VIEW_TODAYS_SALES,
)

data class AccountSummary(
    val id: String,
    val displayName: String,
    val username: String,
    val role: AccountRole,
    val enabled: Boolean,
    val permissionRevision: Int,
)

data class StoreRuntimeSettings(
    val theme: String = "SYSTEM",
    val defaultCashierScreen: String = "SALES",
    val storeName: String = "",
    val language: String = "fil",
)

data class AuthenticatedActor(
    val accountId: String,
    val role: AccountRole,
    val permissionRevision: Int,
)

fun interface PosAccessControl {
    suspend fun requirePermission(permission: PosPermission): AuthenticatedActor
}

data class PriceApprovalPreview(
    val approvalId: String,
    val productId: String,
    val beforeValue: String,
    val afterValue: String,
    val payloadDigest: String,
)

interface RestrictedActionApproval {
    suspend fun requestOwnerApproval(
        requesterAccountId: String,
        operationKey: String,
        targetId: String?,
        beforeValue: String?,
        afterValue: String?,
        payloadDigest: String,
    ): String

    suspend fun authorizeOwnerApproval(approvalId: String, ownerPin: String)

    suspend fun consumeOwnerApproval(
        approvalId: String,
        requesterAccountId: String,
        operationKey: String,
        targetId: String?,
        payloadDigest: String,
    ): String

    suspend fun finishOwnerApproval(approvalId: String, success: Boolean, resultDetails: String?)
    suspend fun cancelOwnerApproval(approvalId: String)
}

data class CredentialVerifier(val salt: String, val verifier: String)

data class SetupInput(
    val storeName: String,
    val address: String?,
    val contactNumber: String?,
    val logoBytes: ByteArray?,
    val ownerDisplayName: String,
    val ownerUsername: String,
    val ownerPassword: String,
    val confirmOwnerPassword: String,
    val ownerApprovalPin: String,
    val confirmOwnerApprovalPin: String,
    val language: String,
    val theme: String,
    val defaultCashierScreen: String,
    val lowStockAlertsEnabled: Boolean,
    val allowNegativeStock: Boolean,
    val receiptShowStoreInfo: Boolean,
    val receiptFooter: String?,
)

data class StaffAccountInput(
    val displayName: String,
    val username: String,
    val loginPin: String,
    val confirmLoginPin: String,
    val permissions: Set<PosPermission>,
)

data class SetupDraft(
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
)

sealed interface AuthDestination {
    data object Loading : AuthDestination
    data class Setup(val draft: SetupDraft?) : AuthDestination
    data class LegacyOwnerEnrollment(val storeName: String?, val legacyPinRequired: Boolean) : AuthDestination
    data class Login(val storeName: String?, val accounts: List<AccountSummary>) : AuthDestination
    data class SetupComplete(val account: AccountSummary) : AuthDestination
    data class SignedIn(val account: AccountSummary) : AuthDestination
}

sealed interface AuthFailure {
    data object InvalidCredentials : AuthFailure
    data object AccountDisabled : AuthFailure
    data object RateLimited : AuthFailure
    data object OwnerPinRejected : AuthFailure
    data object DuplicateUsername : AuthFailure
    data class Validation(val message: String) : AuthFailure
    data class General(val message: String) : AuthFailure
}
