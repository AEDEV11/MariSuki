package com.aesprt.marisuki.data.auth

import androidx.room.withTransaction
import com.aesprt.marisuki.data.AppPreferences
import com.aesprt.marisuki.data.local.PosDatabase
import com.aesprt.marisuki.data.local.dao.PosDao
import com.aesprt.marisuki.data.local.entity.AuthAttemptStateEntity
import com.aesprt.marisuki.data.local.entity.SetupDraftEntity
import com.aesprt.marisuki.data.local.entity.StaffAccountEntity
import com.aesprt.marisuki.data.local.entity.StaffPermissionEntity
import com.aesprt.marisuki.data.local.entity.StoreProfileEntity
import com.aesprt.marisuki.data.local.entity.RestrictedActionAuditEntity
import com.aesprt.marisuki.domain.auth.AccountRole
import com.aesprt.marisuki.domain.auth.AccountSummary
import com.aesprt.marisuki.domain.auth.AuthDestination
import com.aesprt.marisuki.domain.auth.AuthFailure
import com.aesprt.marisuki.domain.auth.AuthenticatedActor
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.domain.auth.PosAccessControl
import com.aesprt.marisuki.domain.auth.StaffAccountInput
import com.aesprt.marisuki.domain.auth.RestrictedActionApproval
import com.aesprt.marisuki.domain.auth.StoreRuntimeSettings
import com.aesprt.marisuki.domain.auth.SetupDraft
import com.aesprt.marisuki.domain.auth.SetupInput
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class AuthRepository(
    private val database: PosDatabase,
    private val dao: PosDao,
    private val preferences: AppPreferences,
    private val credentials: CredentialSecurity,
    private val sessions: EncryptedSessionStore,
    private val clock: () -> Long = System::currentTimeMillis,
) : PosAccessControl, RestrictedActionApproval {
    private val authMutex = Mutex()

    suspend fun runtimeSettings(): StoreRuntimeSettings {
        val profile = dao.storeProfile()
        val draft = dao.setupDraft()
        return StoreRuntimeSettings(
            theme = profile?.theme ?: draft?.theme ?: "SYSTEM",
            defaultCashierScreen = profile?.defaultCashierScreen ?: draft?.defaultCashierScreen ?: "SALES",
            storeName = profile?.storeName ?: draft?.storeName.orEmpty(),
            language = profile?.language ?: draft?.language ?: "fil",
        )
    }

    /** Updates the existing store preference without changing accounts or operational records. */
    suspend fun updateThemePreference(theme: String) {
        requirePermission(PosPermission.CHANGE_SETTINGS)
        val normalized = supportedTheme(theme)
        val profile = dao.storeProfile() ?: throw AuthException(AuthFailure.Validation("Store setup is incomplete."))
        dao.saveStoreProfile(profile.copy(theme = normalized, updatedAtMillis = clock()))
    }

    suspend fun startupDestination(): AuthDestination = authMutex.withLock {
        val profile = dao.storeProfile()
        val accountsExist = dao.staffAccountCount() > 0
        val ownerExists = dao.hasOwnerAccount()
        val operationalDataExists = dao.hasOperationalData()
        val legacyPinConfigured = preferences.ownerPinConfigured.first()

        if (profile?.setupComplete != true || !ownerExists) {
            if (accountsExist || operationalDataExists || legacyPinConfigured) {
                return@withLock AuthDestination.LegacyOwnerEnrollment(
                    storeName = profile?.storeName,
                    legacyPinRequired = legacyPinConfigured,
                )
            }
            return@withLock AuthDestination.Setup(dao.setupDraft()?.toDomain())
        }

        val active = signedInAccount(touch = false)
        if (active != null) AuthDestination.SignedIn(active.toSummary())
        else {
            AuthDestination.Login(
                storeName = profile.storeName,
                accounts = enabledAccounts(),
            )
        }
    }

    suspend fun saveSetupDraft(draft: SetupDraft) {
        require(draft.step in 1..4)
        require(draft.logoBytes == null || draft.logoBytes.size <= MAX_LOGO_BYTES)
        database.withTransaction {
            if (dao.storeProfile()?.setupComplete == true || dao.hasOwnerAccount()) {
                throw AuthException(AuthFailure.Validation("Store setup has already been completed."))
            }
            dao.saveSetupDraft(
                SetupDraftEntity(
                    step = draft.step,
                    storeName = draft.storeName.trim().take(MAX_STORE_NAME_LENGTH),
                    address = draft.address?.trim()?.ifBlank { null }?.take(MAX_ADDRESS_LENGTH),
                    contactNumber = draft.contactNumber?.trim()?.ifBlank { null }?.take(MAX_CONTACT_LENGTH),
                    logoBytes = draft.logoBytes,
                    language = supportedLanguage(draft.language),
                    theme = supportedTheme(draft.theme),
                    defaultCashierScreen = supportedCashierScreen(draft.defaultCashierScreen),
                    lowStockAlertsEnabled = draft.lowStockAlertsEnabled,
                    allowNegativeStock = draft.allowNegativeStock,
                    receiptShowStoreInfo = draft.receiptShowStoreInfo,
                    receiptFooter = draft.receiptFooter?.trim()?.ifBlank { null }?.take(MAX_RECEIPT_FOOTER_LENGTH),
                    updatedAtMillis = clock(),
                ),
            )
        }
    }

    suspend fun completeFirstTimeSetup(input: SetupInput): AccountSummary = authMutex.withLock {
        val profile = validateSetup(input)
        val account = createOwnerAccount(
            displayName = input.ownerDisplayName,
            username = input.ownerUsername,
            password = input.ownerPassword,
            approvalPin = input.ownerApprovalPin,
        )
        database.withTransaction {
            if (dao.storeProfile() != null || dao.staffAccountCount() != 0 || dao.hasOperationalData()) {
                throw AuthException(AuthFailure.Validation("This installation already contains store data. Use owner enrollment to preserve it."))
            }
            dao.insertStaffAccount(account)
            dao.saveStoreProfile(profile)
            dao.deleteSetupDraft()
        }
        preferences.clearLegacyOwnerPin()
        preferences.setLanguage(profile.language)
        createSession(account)
        account.toSummary()
    }

    suspend fun enrollLegacyOwner(
        input: SetupInput,
        legacyPin: String?,
        deviceCredentialConfirmed: Boolean,
    ): AccountSummary = authMutex.withLock {
        val legacyPinConfigured = preferences.ownerPinConfigured.first()
        if (legacyPinConfigured) {
            verifyRateLimited(
                lockKey = LEGACY_ENROLLMENT_LOCK_KEY,
                verify = { preferences.verifyOwnerPin(legacyPin.orEmpty()) },
                failure = AuthFailure.OwnerPinRejected,
            )
        } else if (!deviceCredentialConfirmed) {
            throw AuthException(AuthFailure.Validation("Confirm this device's screen lock to enroll the first owner."))
        }

        val requestedProfile = validateSetup(input).copy(setupComplete = true)
        val account = createOwnerAccount(
            displayName = input.ownerDisplayName,
            username = input.ownerUsername,
            password = input.ownerPassword,
            approvalPin = input.ownerApprovalPin,
        )
        database.withTransaction {
            if (dao.hasOwnerAccount() || dao.staffAccountCount() != 0) {
                throw AuthException(AuthFailure.Validation("An owner account already exists."))
            }
            val existingProfile = dao.storeProfile()
            val profile = existingProfile?.copy(setupComplete = true, updatedAtMillis = clock()) ?: requestedProfile
            dao.insertStaffAccount(account)
            dao.saveStoreProfile(profile)
            dao.deleteSetupDraft()
            dao.clearAuthAttemptState(LEGACY_ENROLLMENT_LOCK_KEY)
        }
        preferences.clearLegacyOwnerPin()
        preferences.setLanguage(requestedProfile.language)
        createSession(account)
        account.toSummary()
    }

    suspend fun login(accountId: String, credential: String): AccountSummary = authMutex.withLock {
        val account = dao.staffAccount(accountId) ?: run {
            throw AuthException(AuthFailure.InvalidCredentials)
        }
        if (!account.enabled) throw AuthException(AuthFailure.AccountDisabled)
        val lockKey = "LOGIN:${account.id}"
        ensureNotLocked(lockKey)
        val valid = withContext(Dispatchers.Default) {
            credentials.verify(credential, account.loginSalt, account.loginVerifier)
        }
        if (!valid) {
            recordFailure(lockKey)
            throw AuthException(if (isLocked(lockKey)) AuthFailure.RateLimited else AuthFailure.InvalidCredentials)
        }
        dao.clearAuthAttemptState(lockKey)
        createSession(account)
        account.toSummary()
    }

    suspend fun logout() {
        sessions.clear()
    }

    suspend fun enterPosAfterSetup() {
        val account = signedInAccount(touch = true)
            ?: throw AuthException(AuthFailure.InvalidCredentials)
        if (account.role != AccountRole.OWNER.name) throw AuthException(AuthFailure.InvalidCredentials)
    }

    suspend fun refreshSession(): AccountSummary? = authMutex.withLock {
        signedInAccount(touch = false)?.toSummary()
    }

    suspend fun touchSession(): AccountSummary? = authMutex.withLock {
        signedInAccount(touch = true)?.toSummary()
    }

    suspend fun currentActor(): AuthenticatedActor = authMutex.withLock {
        val account = signedInAccount(touch = true)
            ?: throw AuthException(AuthFailure.InvalidCredentials)
        AuthenticatedActor(account.id, AccountRole.valueOf(account.role), account.permissionRevision)
    }

    suspend fun currentPermissions(): Set<PosPermission> = authMutex.withLock {
        val account = signedInAccount(touch = true)
            ?: throw AuthException(AuthFailure.InvalidCredentials)
        if (account.role == AccountRole.OWNER.name) PosPermission.entries.toSet()
        else dao.staffPermissions(account.id).filter { it.enabled }.mapNotNull { row ->
            runCatching { PosPermission.valueOf(row.permissionCode) }.getOrNull()
        }.toSet()
    }

    override suspend fun requirePermission(permission: PosPermission): AuthenticatedActor = authMutex.withLock {
        val account = signedInAccount(touch = true)
            ?: throw AuthException(AuthFailure.InvalidCredentials)
        val role = AccountRole.valueOf(account.role)
        if (role == AccountRole.OWNER) return@withLock AuthenticatedActor(account.id, role, account.permissionRevision)
        if (permission.ownerOnly) throw AuthException(AuthFailure.Validation("This action is available to the owner only."))
        val allowed = dao.staffPermissions(account.id).any { it.enabled && it.permissionCode == permission.name }
        if (!allowed) throw AuthException(AuthFailure.Validation("Your account does not have permission for this action."))
        AuthenticatedActor(account.id, role, account.permissionRevision)
    }

    suspend fun verifyOwnerApprovalPin(accountId: String, pin: String): StaffAccountEntity = authMutex.withLock {
        val account = dao.staffAccount(accountId)
            ?: throw AuthException(AuthFailure.OwnerPinRejected)
        if (!account.enabled || account.role != AccountRole.OWNER.name || account.ownerPinSalt == null || account.ownerPinVerifier == null) {
            throw AuthException(AuthFailure.OwnerPinRejected)
        }
        val lockKey = "OWNER_APPROVAL_PIN:${account.id}"
        ensureNotLocked(lockKey)
        val valid = withContext(Dispatchers.Default) {
            credentials.verify(pin, account.ownerPinSalt, account.ownerPinVerifier)
        }
        if (!valid) {
            recordFailure(lockKey)
            throw AuthException(if (isLocked(lockKey)) AuthFailure.RateLimited else AuthFailure.OwnerPinRejected)
        }
        dao.clearAuthAttemptState(lockKey)
        account
    }

    override suspend fun requestOwnerApproval(
        requesterAccountId: String,
        operationKey: String,
        targetId: String?,
        beforeValue: String?,
        afterValue: String?,
        payloadDigest: String,
    ): String {
        val actor = currentActor()
        if (actor.accountId != requesterAccountId || actor.role != AccountRole.CASHIER) {
            throw AuthException(AuthFailure.Validation("Only the signed-in cashier may request a temporary approval."))
        }
        val permission = runCatching { PosPermission.valueOf(operationKey) }.getOrNull()
            ?: throw AuthException(AuthFailure.Validation("This action cannot use PIN approval."))
        if (permission !in APPROVABLE_PERMISSIONS || dao.staffPermissions(actor.accountId).any { it.enabled && it.permissionCode == permission.name }) {
            throw AuthException(AuthFailure.Validation("This action does not need or allow a temporary PIN approval."))
        }
        if (payloadDigest.length != 64 || targetId.isNullOrBlank()) {
            throw AuthException(AuthFailure.Validation("The requested action details are invalid."))
        }
        val now = clock()
        val request = RestrictedActionAuditEntity(
            id = UUID.randomUUID().toString(), requesterAccountId = actor.accountId, approverAccountId = null,
            operationKey = permission.name, targetId = targetId, payloadDigest = payloadDigest,
            beforeValue = beforeValue, afterValue = afterValue, authorizationResult = "PENDING",
            operationResult = "PENDING", requestedAtMillis = now, expiresAtMillis = now + APPROVAL_TTL_MILLIS,
            completedAtMillis = null,
        )
        dao.insertRestrictedActionAudit(request)
        return request.id
    }

    override suspend fun authorizeOwnerApproval(approvalId: String, ownerPin: String) {
        val actor = currentActor()
        val request = dao.restrictedActionAudit(approvalId)
            ?: throw AuthException(AuthFailure.Validation("This approval request is no longer available."))
        if (request.requesterAccountId != actor.accountId || request.authorizationResult != "PENDING" || request.operationResult != "PENDING" || request.expiresAtMillis <= clock()) {
            dao.denyRestrictedAction(approvalId, clock())
            throw AuthException(AuthFailure.Validation("This approval request expired. Start the action again."))
        }
        if (request.operationKey !in APPROVABLE_PERMISSIONS.map(PosPermission::name)) {
            dao.denyRestrictedAction(approvalId, clock())
            throw AuthException(AuthFailure.Validation("This operation cannot be authorized with an owner PIN."))
        }
        val owner = dao.enabledOwnerAccount() ?: run {
            dao.denyRestrictedAction(approvalId, clock())
            throw AuthException(AuthFailure.OwnerPinRejected)
        }
        try {
            verifyOwnerApprovalPin(owner.id, ownerPin)
        } catch (error: AuthException) {
            dao.denyRestrictedAction(approvalId, clock())
            throw error
        }
        if (dao.approveRestrictedAction(approvalId, owner.id, clock()) != 1) {
            dao.denyRestrictedAction(approvalId, clock())
            throw AuthException(AuthFailure.Validation("This approval request expired or was already used."))
        }
    }

    override suspend fun consumeOwnerApproval(
        approvalId: String,
        requesterAccountId: String,
        operationKey: String,
        targetId: String?,
        payloadDigest: String,
    ): String {
        val actor = currentActor()
        if (actor.accountId != requesterAccountId) throw AuthException(AuthFailure.InvalidCredentials)
        if (operationKey !in APPROVABLE_PERMISSIONS.map(PosPermission::name)) {
            throw AuthException(AuthFailure.Validation("This action cannot use PIN approval."))
        }
        val now = clock()
        val request = dao.restrictedActionAudit(approvalId)
            ?: throw AuthException(AuthFailure.Validation("This approval request was not found."))
        val approverId = request.approverAccountId
            ?: throw AuthException(AuthFailure.Validation("Owner approval is required for this action."))
        val owner = dao.staffAccount(approverId)
        if (owner == null || !owner.enabled || owner.role != AccountRole.OWNER.name) {
            throw AuthException(AuthFailure.Validation("The approving owner account is no longer active."))
        }
        if (dao.consumeRestrictedAction(approvalId, requesterAccountId, operationKey, targetId, payloadDigest, now) != 1) {
            throw AuthException(AuthFailure.Validation("Owner approval expired or does not match this exact action."))
        }
        return approverId
    }

    override suspend fun finishOwnerApproval(approvalId: String, success: Boolean, resultDetails: String?) {
        val actor = currentActor()
        val request = dao.restrictedActionAudit(approvalId) ?: return
        if (request.requesterAccountId != actor.accountId || request.authorizationResult != "APPROVED") return
        dao.finishRestrictedAction(approvalId, if (success) "SUCCESS" else "FAILED", clock())
    }

    override suspend fun cancelOwnerApproval(approvalId: String) {
        val actor = currentActor()
        val request = dao.restrictedActionAudit(approvalId) ?: return
        if (request.requesterAccountId == actor.accountId) dao.cancelRestrictedAction(approvalId, clock())
    }

    suspend fun enabledAccounts(): List<AccountSummary> =
        dao.observeStaffAccounts().first().filter { it.enabled }.map { it.toSummary() }

    suspend fun staffAccounts(): List<AccountSummary> {
        requireOwnerSession()
        return dao.observeStaffAccounts().first().map { it.toSummary() }
    }

    suspend fun permissionsFor(accountId: String): Set<PosPermission> {
        requireOwnerSession()
        val account = dao.staffAccount(accountId) ?: throw AuthException(AuthFailure.Validation("Staff account not found."))
        if (account.role != AccountRole.CASHIER.name) throw AuthException(AuthFailure.Validation("Owner permissions cannot be edited."))
        return dao.staffPermissions(accountId).filter { it.enabled }.mapNotNull { row ->
            runCatching { PosPermission.valueOf(row.permissionCode) }.getOrNull()
        }.toSet()
    }

    suspend fun createCashier(input: StaffAccountInput): AccountSummary = authMutex.withLock {
        val owner = requireOwnerSession()
        val cleanName = input.displayName.trim()
        val cleanUsername = input.username.trim()
        val normalized = normalizeUsername(cleanUsername)
        validateStaffInput(cleanName, cleanUsername, input.loginPin, input.confirmLoginPin)
        if (dao.staffAccountByUsername(normalized) != null) throw AuthException(AuthFailure.DuplicateUsername)
        val verifier = withContext(Dispatchers.Default) { credentials.createVerifier(input.loginPin) }
        val now = clock()
        val account = StaffAccountEntity(
            id = UUID.randomUUID().toString(), displayName = cleanName, username = cleanUsername,
            usernameNormalized = normalized, role = AccountRole.CASHIER.name, loginCredentialType = "PIN",
            loginSalt = verifier.salt, loginVerifier = verifier.verifier, ownerPinSalt = null,
            ownerPinVerifier = null, enabled = true, createdAtMillis = now, updatedAtMillis = now,
        )
        database.withTransaction {
            if (dao.staffAccountByUsername(normalized) != null) throw AuthException(AuthFailure.DuplicateUsername)
            dao.insertStaffAccount(account)
            dao.saveStaffPermissions(input.permissions.filterNot(PosPermission::ownerOnly).map { permission ->
                StaffPermissionEntity(account.id, permission.name, enabled = true)
            })
            dao.insertAudit(com.aesprt.marisuki.data.local.entity.AuditLogEntity(UUID.randomUUID().toString(), "STAFF_CREATED", account.id, "Cashier account created", now, owner.id))
        }
        account.toSummary()
    }

    suspend fun saveCashier(
        accountId: String?,
        displayName: String,
        username: String,
        loginPin: String,
        confirmLoginPin: String,
        permissions: Set<PosPermission>,
    ): AccountSummary = authMutex.withLock {
        val owner = requireOwnerSession()
        val cleanName = displayName.trim()
        val cleanUsername = username.trim()
        val normalized = normalizeUsername(cleanUsername)
        if (cleanName.isBlank() || cleanName.length > MAX_NAME_LENGTH) throw AuthException(AuthFailure.Validation("Enter a cashier name."))
        if (!USERNAME_PATTERN.matches(cleanUsername)) throw AuthException(AuthFailure.Validation("Use 3–32 letters, numbers, dots, dashes, or underscores for the username."))
        val existing = accountId?.let { id ->
            dao.staffAccount(id)?.also { if (it.role != AccountRole.CASHIER.name) throw AuthException(AuthFailure.Validation("Owner account cannot be edited here.")) }
                ?: throw AuthException(AuthFailure.Validation("Staff account not found."))
        }
        val resetPin = loginPin.isNotEmpty() || confirmLoginPin.isNotEmpty()
        if (existing == null || resetPin) {
            if (!CredentialSecurity.validCashierPin(loginPin) || loginPin != confirmLoginPin) {
                throw AuthException(AuthFailure.Validation("Cashier PINs must match and contain six digits."))
            }
        }
        val duplicate = dao.staffAccountByUsername(normalized)
        if (duplicate != null && duplicate.id != accountId) throw AuthException(AuthFailure.DuplicateUsername)
        val verifier = if (existing == null || resetPin) withContext(Dispatchers.Default) { credentials.createVerifier(loginPin) } else null
        val now = clock()
        val account = if (existing == null) {
            StaffAccountEntity(
                id = UUID.randomUUID().toString(), displayName = cleanName, username = cleanUsername,
                usernameNormalized = normalized, role = AccountRole.CASHIER.name, loginCredentialType = "PIN",
                loginSalt = verifier!!.salt, loginVerifier = verifier.verifier, ownerPinSalt = null,
                ownerPinVerifier = null, enabled = true, createdAtMillis = now, updatedAtMillis = now,
            )
        } else existing.copy(
            displayName = cleanName, username = cleanUsername, usernameNormalized = normalized,
            loginSalt = verifier?.salt ?: existing.loginSalt,
            loginVerifier = verifier?.verifier ?: existing.loginVerifier,
            updatedAtMillis = now,
            permissionRevision = existing.permissionRevision + 1,
        )
        database.withTransaction {
            if (existing == null) dao.insertStaffAccount(account) else dao.updateStaffAccount(account)
            dao.deleteStaffPermissions(account.id)
            dao.saveStaffPermissions(permissions.filterNot(PosPermission::ownerOnly).map { StaffPermissionEntity(account.id, it.name, true) })
            dao.insertAudit(com.aesprt.marisuki.data.local.entity.AuditLogEntity(
                UUID.randomUUID().toString(), if (existing == null) "STAFF_CREATED" else "STAFF_UPDATED",
                account.id, "Cashier account and permissions saved", now, owner.id,
            ))
        }
        account.toSummary()
    }

    suspend fun updateCashier(accountId: String, input: StaffAccountInput): AccountSummary = authMutex.withLock {
        val owner = requireOwnerSession()
        val existing = dao.staffAccount(accountId)
            ?: throw AuthException(AuthFailure.Validation("Staff account not found."))
        if (existing.role != AccountRole.CASHIER.name) throw AuthException(AuthFailure.Validation("Owner account security cannot be changed here."))
        val cleanName = input.displayName.trim()
        val cleanUsername = input.username.trim()
        val normalized = normalizeUsername(cleanUsername)
        validateStaffInput(cleanName, cleanUsername, input.loginPin, input.confirmLoginPin)
        val duplicate = dao.staffAccountByUsername(normalized)
        if (duplicate != null && duplicate.id != accountId) throw AuthException(AuthFailure.DuplicateUsername)
        val verifier = withContext(Dispatchers.Default) { credentials.createVerifier(input.loginPin) }
        val now = clock()
        val updated = existing.copy(
            displayName = cleanName, username = cleanUsername, usernameNormalized = normalized,
            loginSalt = verifier.salt, loginVerifier = verifier.verifier, updatedAtMillis = now,
            permissionRevision = existing.permissionRevision + 1,
        )
        database.withTransaction {
            dao.updateStaffAccount(updated)
            dao.deleteStaffPermissions(accountId)
            dao.saveStaffPermissions(input.permissions.filterNot(PosPermission::ownerOnly).map { permission ->
                StaffPermissionEntity(accountId, permission.name, enabled = true)
            })
            dao.insertAudit(com.aesprt.marisuki.data.local.entity.AuditLogEntity(UUID.randomUUID().toString(), "STAFF_UPDATED", accountId, "Cashier account and permissions updated", now, owner.id))
        }
        updated.toSummary()
    }

    suspend fun setCashierEnabled(accountId: String, enabled: Boolean) = authMutex.withLock {
        val owner = requireOwnerSession()
        val account = dao.staffAccount(accountId)
            ?: throw AuthException(AuthFailure.Validation("Staff account not found."))
        if (account.role != AccountRole.CASHIER.name) throw AuthException(AuthFailure.Validation("Owner account cannot be disabled here."))
        val now = clock()
        dao.updateStaffAccount(account.copy(enabled = enabled, updatedAtMillis = now, permissionRevision = account.permissionRevision + 1))
        dao.insertAudit(com.aesprt.marisuki.data.local.entity.AuditLogEntity(UUID.randomUUID().toString(), if (enabled) "STAFF_ENABLED" else "STAFF_DISABLED", accountId, "Cashier account status changed", now, owner.id))
        if (!enabled) sessions.invalidateAccount(accountId)
    }

    suspend fun setCashierPermissions(accountId: String, permissions: Set<PosPermission>) = authMutex.withLock {
        val owner = requireOwnerSession()
        val account = dao.staffAccount(accountId)
            ?: throw AuthException(AuthFailure.Validation("Staff account not found."))
        if (account.role != AccountRole.CASHIER.name) throw AuthException(AuthFailure.Validation("Owner permissions cannot be edited."))
        val now = clock()
        database.withTransaction {
            dao.deleteStaffPermissions(accountId)
            dao.saveStaffPermissions(permissions.filterNot(PosPermission::ownerOnly).map { StaffPermissionEntity(accountId, it.name, true) })
            dao.updateStaffAccount(account.copy(permissionRevision = account.permissionRevision + 1, updatedAtMillis = now))
            dao.insertAudit(com.aesprt.marisuki.data.local.entity.AuditLogEntity(UUID.randomUUID().toString(), "STAFF_PERMISSIONS_CHANGED", accountId, "Cashier permissions changed", now, owner.id))
        }
    }

    suspend fun staffActivity(accountId: String) = withOwnerSession { dao.observeStaffActivity(accountId) }

    suspend fun restrictedActionAudit() = withOwnerSession { dao.observeRestrictedActionAudit() }

    private suspend fun <T> withOwnerSession(block: suspend () -> T): T {
        requireOwnerSession()
        return block()
    }

    private suspend fun requireOwnerSession(): StaffAccountEntity {
        val owner = signedInAccount(touch = true)
            ?: throw AuthException(AuthFailure.InvalidCredentials)
        if (owner.role != AccountRole.OWNER.name || !owner.enabled) {
            throw AuthException(AuthFailure.Validation("Sign in with the owner account to manage staff."))
        }
        return owner
    }

    private fun validateStaffInput(name: String, username: String, pin: String, confirmPin: String) {
        if (name.isBlank() || name.length > MAX_NAME_LENGTH) throw AuthException(AuthFailure.Validation("Enter a cashier name."))
        if (!USERNAME_PATTERN.matches(username)) throw AuthException(AuthFailure.Validation("Use 3–32 letters, numbers, dots, dashes, or underscores for the username."))
        if (!CredentialSecurity.validCashierPin(pin) || pin != confirmPin) throw AuthException(AuthFailure.Validation("Cashier PINs must match and contain six digits."))
    }

    private suspend fun createOwnerAccount(
        displayName: String,
        username: String,
        password: String,
        approvalPin: String,
    ): StaffAccountEntity {
        val cleanName = displayName.trim()
        val cleanUsername = username.trim()
        val normalized = normalizeUsername(cleanUsername)
        if (cleanName.isBlank() || cleanName.length > MAX_NAME_LENGTH) {
            throw AuthException(AuthFailure.Validation("Enter the owner's name."))
        }
        if (!USERNAME_PATTERN.matches(cleanUsername)) {
            throw AuthException(AuthFailure.Validation("Use 3–32 letters, numbers, dots, dashes, or underscores for the username."))
        }
        if (!CredentialSecurity.validOwnerPassword(password) || password != password.trim()) {
            throw AuthException(AuthFailure.Validation("Use a password of 8–128 characters with at least one letter and one number."))
        }
        if (!CredentialSecurity.validOwnerApprovalPin(approvalPin)) {
            throw AuthException(AuthFailure.Validation("The owner approval PIN must contain six digits."))
        }
        val loginVerifier = withContext(Dispatchers.Default) { credentials.createVerifier(password) }
        val pinVerifier = withContext(Dispatchers.Default) { credentials.createVerifier(approvalPin) }
        return StaffAccountEntity(
            id = UUID.randomUUID().toString(),
            displayName = cleanName,
            username = cleanUsername,
            usernameNormalized = normalized,
            role = AccountRole.OWNER.name,
            loginCredentialType = "PASSWORD",
            loginSalt = loginVerifier.salt,
            loginVerifier = loginVerifier.verifier,
            ownerPinSalt = pinVerifier.salt,
            ownerPinVerifier = pinVerifier.verifier,
            enabled = true,
            createdAtMillis = clock(),
            updatedAtMillis = clock(),
        )
    }

    private suspend fun validateSetup(input: SetupInput): StoreProfileEntity {
        val storeName = input.storeName.trim()
        if (storeName.isBlank() || storeName.length > MAX_STORE_NAME_LENGTH) {
            throw AuthException(AuthFailure.Validation("Enter a store name of up to 80 characters."))
        }
        if (input.ownerPassword != input.confirmOwnerPassword) {
            throw AuthException(AuthFailure.Validation("The owner passwords do not match."))
        }
        if (input.ownerApprovalPin != input.confirmOwnerApprovalPin) {
            throw AuthException(AuthFailure.Validation("The owner approval PINs do not match."))
        }
        if (input.logoBytes != null && input.logoBytes.size > MAX_LOGO_BYTES) {
            throw AuthException(AuthFailure.Validation("Choose a store logo smaller than 1 MB."))
        }
        val userNameNormalized = normalizeUsername(input.ownerUsername.trim())
        if (dao.staffAccountByUsername(userNameNormalized) != null) {
            throw AuthException(AuthFailure.DuplicateUsername)
        }
        val now = clock()
        return StoreProfileEntity(
            storeName = storeName,
            address = input.address?.trim()?.ifBlank { null }?.take(MAX_ADDRESS_LENGTH),
            contactNumber = input.contactNumber?.trim()?.ifBlank { null }?.take(MAX_CONTACT_LENGTH),
            logoBytes = input.logoBytes,
            currencyCode = "PHP",
            language = supportedLanguage(input.language),
            theme = supportedTheme(input.theme),
            defaultCashierScreen = supportedCashierScreen(input.defaultCashierScreen),
            lowStockAlertsEnabled = input.lowStockAlertsEnabled,
            allowNegativeStock = input.allowNegativeStock,
            receiptShowStoreInfo = input.receiptShowStoreInfo,
            receiptFooter = input.receiptFooter?.trim()?.ifBlank { null }?.take(MAX_RECEIPT_FOOTER_LENGTH),
            setupComplete = true,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
    }

    private suspend fun createSession(account: StaffAccountEntity) {
        val now = clock()
        sessions.save(
            LocalSession(
                sessionId = UUID.randomUUID().toString(),
                accountId = account.id,
                issuedAtMillis = now,
                lastActivityAtMillis = now,
                recentAuthenticationAtMillis = now,
            ),
        )
    }

    private suspend fun signedInAccount(touch: Boolean): StaffAccountEntity? {
        val session = sessions.read() ?: return null
        val now = clock()
        if (now < session.issuedAtMillis || now - session.lastActivityAtMillis > IDLE_TIMEOUT_MILLIS ||
            now - session.issuedAtMillis > MAX_SESSION_MILLIS
        ) {
            sessions.clear()
            return null
        }
        val account = dao.staffAccount(session.accountId)
        if (account == null || !account.enabled) {
            sessions.clear()
            return null
        }
        if (touch) sessions.save(session.copy(lastActivityAtMillis = now))
        return account
    }

    private suspend fun verifyRateLimited(lockKey: String, verify: suspend () -> Boolean, failure: AuthFailure) {
        ensureNotLocked(lockKey)
        if (!verify()) {
            recordFailure(lockKey)
            throw AuthException(if (isLocked(lockKey)) AuthFailure.RateLimited else failure)
        }
        dao.clearAuthAttemptState(lockKey)
    }

    private suspend fun ensureNotLocked(lockKey: String) {
        if (isLocked(lockKey)) throw AuthException(AuthFailure.RateLimited)
    }

    private suspend fun isLocked(lockKey: String): Boolean =
        (dao.authAttemptState(lockKey)?.lockedUntilMillis ?: 0L) > clock()

    private suspend fun recordFailure(lockKey: String) {
        val now = clock()
        val failures = (dao.authAttemptState(lockKey)?.failedAttempts ?: 0) + 1
        val lockoutMillis = if (failures < FAILURES_BEFORE_LOCK) 0L else {
            val exponent = (failures - FAILURES_BEFORE_LOCK).coerceAtMost(MAX_LOCKOUT_EXPONENT)
            (BASE_LOCKOUT_MILLIS * (1L shl exponent)).coerceAtMost(MAX_LOCKOUT_MILLIS)
        }
        dao.saveAuthAttemptState(AuthAttemptStateEntity(lockKey, failures, now + lockoutMillis, now))
    }

    private fun supportedLanguage(value: String) = value.takeIf { it in AppPreferences.SUPPORTED_LANGUAGES } ?: "fil"
    private fun supportedTheme(value: String) = value.uppercase(Locale.ROOT).takeIf { it in SUPPORTED_THEMES } ?: "SYSTEM"
    private fun supportedCashierScreen(value: String) = value.takeIf { it in CASHIER_SCREENS } ?: "SALES"
    private fun normalizeUsername(value: String) = value.lowercase(Locale.ROOT)
    private fun SetupDraftEntity.toDomain() = SetupDraft(
        step, storeName, address, contactNumber, logoBytes, language, theme, defaultCashierScreen,
        lowStockAlertsEnabled, allowNegativeStock, receiptShowStoreInfo, receiptFooter,
    )

    private fun StaffAccountEntity.toSummary() = AccountSummary(
        id = id,
        displayName = displayName,
        username = username,
        role = AccountRole.valueOf(role),
        enabled = enabled,
        permissionRevision = permissionRevision,
    )

    companion object {
        private const val MAX_STORE_NAME_LENGTH = 80
        private const val MAX_NAME_LENGTH = 80
        private const val MAX_ADDRESS_LENGTH = 200
        private const val MAX_CONTACT_LENGTH = 40
        private const val MAX_RECEIPT_FOOTER_LENGTH = 160
        private const val MAX_LOGO_BYTES = 1_000_000
        private const val IDLE_TIMEOUT_MILLIS = 15 * 60 * 1000L
        private const val MAX_SESSION_MILLIS = 18 * 60 * 60 * 1000L
        private const val FAILURES_BEFORE_LOCK = 5
        private const val BASE_LOCKOUT_MILLIS = 30_000L
        private const val MAX_LOCKOUT_MILLIS = 15 * 60 * 1000L
        private const val MAX_LOCKOUT_EXPONENT = 5
        private const val LEGACY_ENROLLMENT_LOCK_KEY = "LEGACY_OWNER_ENROLLMENT"
        private const val APPROVAL_TTL_MILLIS = 60_000L
        // Only price changes currently have a complete request → PIN → exact-payload
        // commit flow. Keep other sensitive operations owner-only until they gain
        // their own operation-bound approval path.
        private val APPROVABLE_PERMISSIONS = setOf(PosPermission.CHANGE_SELLING_PRICE)
        private val USERNAME_PATTERN = Regex("[A-Za-z0-9._-]{3,32}")
        private val SUPPORTED_THEMES = setOf("LIGHT", "DARK", "SYSTEM")
        private val CASHIER_SCREENS = setOf("SALES", "PRODUCTS", "CREDIT")
    }
}

class AuthException(val failure: AuthFailure) : IllegalStateException()
