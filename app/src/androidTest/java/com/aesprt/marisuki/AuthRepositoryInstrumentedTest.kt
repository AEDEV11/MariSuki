package com.aesprt.marisuki

import androidx.room.Room
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aesprt.marisuki.data.AppPreferences
import com.aesprt.marisuki.data.EncryptedBackupManager
import com.aesprt.marisuki.data.PosRepository
import com.aesprt.marisuki.data.auth.AuthException
import com.aesprt.marisuki.data.auth.AuthRepository
import com.aesprt.marisuki.data.auth.CredentialSecurity
import com.aesprt.marisuki.data.auth.EncryptedSessionStore
import com.aesprt.marisuki.data.local.PosDatabase
import com.aesprt.marisuki.domain.auth.AccountRole
import com.aesprt.marisuki.domain.auth.AuthDestination
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.domain.auth.SetupInput
import com.aesprt.marisuki.domain.auth.StaffAccountInput
import com.aesprt.marisuki.domain.model.NewProduct
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.util.UUID
import org.koin.core.context.GlobalContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthRepositoryInstrumentedTest {
    private lateinit var database: PosDatabase
    private lateinit var auth: AuthRepository
    private lateinit var preferences: AppPreferences
    private lateinit var sessions: EncryptedSessionStore
    private lateinit var testScope: CoroutineScope
    private val testStoreFiles = mutableListOf<File>()
    private var time = 1_800_000_000_000L

    @Before
    fun setUp() {
        val testContext = InstrumentationRegistry.getInstrumentation().targetContext
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val settingsStore = isolatedDataStore(testContext, "settings")
        val sessionDataStore = isolatedDataStore(testContext, "session")
        database = Room.inMemoryDatabaseBuilder(testContext, PosDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        preferences = AppPreferences(testContext, settingsStore)
        sessions = EncryptedSessionStore(testContext, sessionDataStore)
        auth = AuthRepository(
            database,
            database.posDao(),
            preferences,
            CredentialSecurity(),
            sessions,
            clock = { time },
        )
    }

    @After
    fun tearDown() {
        if (::auth.isInitialized) runBlocking { auth.logout() }
        if (::database.isInitialized) database.close()
        if (::testScope.isInitialized) testScope.cancel()
        testStoreFiles.forEach(File::delete)
        testStoreFiles.clear()
    }

    @Test
    fun freshSetupPersistsOwnerAndRequiresLoginAfterLogout() = runBlocking {
        assertTrue(auth.startupDestination() is AuthDestination.Setup)
        val owner = auth.completeFirstTimeSetup(setupInput())
        assertEquals(AccountRole.OWNER, owner.role)
        assertEquals("MariSuki Test Store", database.posDao().storeProfile()?.storeName)
        assertTrue(auth.startupDestination() is AuthDestination.SignedIn)
        auth.logout()
        val login = auth.startupDestination() as AuthDestination.Login
        assertEquals(listOf(owner.id), login.accounts.map { it.id })
        assertEquals(owner, auth.login(owner.id, "StorePass26!"))
    }

    @Test
    fun setupPreferencesAreAvailableForThemeAndInitialPosRouting() = runBlocking {
        val input = setupInput().copy(theme = "DARK", defaultCashierScreen = "CREDIT", language = "en")
        auth.completeFirstTimeSetup(input)
        val settings = auth.runtimeSettings()
        assertEquals("DARK", settings.theme)
        assertEquals("CREDIT", settings.defaultCashierScreen)
        assertEquals("MariSuki Test Store", settings.storeName)
        assertEquals("en", settings.language)
        auth.updateThemePreference("LIGHT")
        assertEquals("LIGHT", auth.runtimeSettings().theme)
        assertEquals("LIGHT", database.posDao().storeProfile()?.theme)
        auth.logout()
        assertTrue(runCatching { auth.updateThemePreference("DARK") }.exceptionOrNull() is AuthException)
        assertEquals("LIGHT", auth.runtimeSettings().theme)
    }

    @Test
    fun loginAttemptsAreThrottledAndEncryptedSessionSurvivesRepositoryRestart() = runBlocking {
        val owner = auth.completeFirstTimeSetup(setupInput())
        auth.logout()

        repeat(4) {
            val failure = runCatching { auth.login(owner.id, "WrongPass26!") }.exceptionOrNull() as AuthException
            assertEquals(com.aesprt.marisuki.domain.auth.AuthFailure.InvalidCredentials, failure.failure)
        }
        val locked = runCatching { auth.login(owner.id, "WrongPass26!") }.exceptionOrNull() as AuthException
        assertEquals(com.aesprt.marisuki.domain.auth.AuthFailure.RateLimited, locked.failure)
        assertTrue(runCatching { auth.login(owner.id, "StorePass26!") }.exceptionOrNull() is AuthException)

        time += 30_001L
        auth.login(owner.id, "StorePass26!")
        val restartedRepository = AuthRepository(
            database,
            database.posDao(),
            preferences,
            CredentialSecurity(),
            sessions,
            clock = { time },
        )
        assertEquals(owner.id, (restartedRepository.startupDestination() as AuthDestination.SignedIn).account.id)
    }

    @Test
    fun idleSessionExpiresAtNextAuthorizationBoundary() = runBlocking {
        auth.completeFirstTimeSetup(setupInput())
        time += 15L * 60L * 1000L + 1L
        val destination = auth.startupDestination()
        assertTrue(destination is AuthDestination.Login)
    }

    @Test
    fun applicationGraphResolvesProtectedRepositoryAndBackupServices() {
        val koin = GlobalContext.get()
        assertTrue(koin.get<com.aesprt.marisuki.domain.auth.PosAccessControl>() is AuthRepository)
        assertTrue(koin.get<com.aesprt.marisuki.domain.auth.RestrictedActionApproval>() is AuthRepository)
        assertNotNull(koin.get<EncryptedBackupManager>())
    }

    @Test
    fun setupDraftResumesWithoutPersistingCredentialAndOwnerCannotDuplicate() = runBlocking {
        auth.saveSetupDraft(
            com.aesprt.marisuki.domain.auth.SetupDraft(
                step = 2, storeName = "Resume Shop", address = null, contactNumber = null, logoBytes = null,
                language = "en", theme = "SYSTEM", defaultCashierScreen = "SALES", lowStockAlertsEnabled = true,
                allowNegativeStock = false, receiptShowStoreInfo = true, receiptFooter = null,
            ),
        )
        val route = auth.startupDestination() as AuthDestination.Setup
        assertEquals(2, route.draft?.step)
        val owner = auth.completeFirstTimeSetup(setupInput(storeName = "Resume Shop"))
        val duplicate = runCatching { auth.completeFirstTimeSetup(setupInput(username = "other.owner", storeName = "Other Shop")) }.exceptionOrNull()
        assertTrue(duplicate is AuthException)
        assertEquals(1, database.posDao().staffAccountCount())
        assertNotEquals("StorePass26!", database.posDao().staffAccount(owner.id)?.loginVerifier)
    }

    @Test
    fun cashierPermissionIsEnforcedAtRepositoryBoundaryAndDisabledAccountsAreRejected() = runBlocking {
        val owner = auth.completeFirstTimeSetup(setupInput())
        val cashier = auth.createCashier(
            StaffAccountInput("Maria", "maria", "123456", "123456", setOf(PosPermission.CREATE_SALE)),
        )
        auth.logout()
        auth.login(cashier.id, "123456")
        val repository = PosRepository(database, database.posDao(), auth)
        val denied = runCatching { repository.recordExpense("expense-attempt", 5_000, "Transport", null, "CASH") }.exceptionOrNull()
        assertTrue(denied is AuthException)
        assertEquals(setOf(PosPermission.CREATE_SALE), auth.currentPermissions())

        auth.logout()
        auth.login(owner.id, "StorePass26!")
        auth.setCashierEnabled(cashier.id, false)
        auth.logout()
        val rejected = runCatching { auth.login(cashier.id, "123456") }.exceptionOrNull()
        assertEquals(com.aesprt.marisuki.domain.auth.AuthFailure.AccountDisabled, (rejected as AuthException).failure)
    }

    @Test
    fun ownerOnlyStockActionsCannotBeDelegatedOrAuthorizedByPin() = runBlocking {
        auth.completeFirstTimeSetup(setupInput())
        val cashier = auth.createCashier(
            StaffAccountInput("Ana", "ana", "654321", "654321", setOf(PosPermission.CREATE_SALE, PosPermission.ADJUST_STOCK)),
        )
        assertFalse(PosPermission.ADJUST_STOCK in auth.permissionsFor(cashier.id))
        auth.logout()
        auth.login(cashier.id, "654321")

        val direct = runCatching { auth.requirePermission(PosPermission.ADJUST_STOCK) }.exceptionOrNull()
        assertTrue(direct is AuthException)
        val approval = runCatching {
            auth.requestOwnerApproval(
                requesterAccountId = cashier.id,
                operationKey = PosPermission.ADJUST_STOCK.name,
                targetId = "product-1",
                beforeValue = "1000",
                afterValue = "0",
                payloadDigest = "0".repeat(64),
            )
        }.exceptionOrNull()
        assertTrue(approval is AuthException)
    }

    @Test
    fun priceApprovalIsOneTimeBoundToExactValuesAndAudited() = runBlocking {
        val owner = auth.completeFirstTimeSetup(setupInput())
        val repository = PosRepository(database, database.posDao(), auth)
        val productId = repository.createProduct(
            NewProduct("Sardines", baseUnitLabel = "Can", baseUnitPriceCentavos = 1_800, purchaseCostCentavos = 0),
        )
        val cashier = auth.createCashier(
            StaffAccountInput("Maria", "maria", "123456", "123456", setOf(PosPermission.EDIT_PRODUCT)),
        )
        auth.logout()
        auth.login(cashier.id, "123456")
        val product = database.posDao().product(productId)!!
        val requested = NewProduct("Sardines", baseUnitLabel = "Can", baseUnitPriceCentavos = 2_000, purchaseCostCentavos = 0)
        val pending = repository.requestPriceChangeApproval(productId, requested)!!
        auth.authorizeOwnerApproval(pending.approvalId, "778899")
        repository.updateProduct(productId, requested, pending.approvalId)
        assertEquals(2_000L, database.posDao().product(productId)?.baseUnitPriceCentavos)
        val approvedAudit = database.posDao().restrictedActionAudit(pending.approvalId)!!
        assertEquals("APPROVED", approvedAudit.authorizationResult)
        assertEquals("SUCCESS", approvedAudit.operationResult)
        assertEquals(cashier.id, approvedAudit.requesterAccountId)
        assertEquals(owner.id, approvedAudit.approverAccountId)

        val replay = runCatching { repository.updateProduct(productId, requested, pending.approvalId) }.exceptionOrNull()
        assertTrue(replay is AuthException)
        assertEquals("SUCCESS", database.posDao().restrictedActionAudit(pending.approvalId)?.operationResult)

        val changedRequest = requested.copy(baseUnitPriceCentavos = 2_200)
        val changedApproval = repository.requestPriceChangeApproval(productId, changedRequest)!!
        auth.authorizeOwnerApproval(changedApproval.approvalId, "778899")
        val changedAfterApproval = changedRequest.copy(baseUnitPriceCentavos = 2_300)
        val changed = runCatching { repository.updateProduct(productId, changedAfterApproval, changedApproval.approvalId) }.exceptionOrNull()
        assertTrue(changed is AuthException)
        assertEquals(2_000L, database.posDao().product(productId)?.baseUnitPriceCentavos)
        assertEquals("FAILED", database.posDao().restrictedActionAudit(changedApproval.approvalId)?.operationResult)

        val incorrect = repository.requestPriceChangeApproval(productId, changedRequest)!!
        val rejected = runCatching { auth.authorizeOwnerApproval(incorrect.approvalId, "000000") }.exceptionOrNull()
        assertTrue(rejected is AuthException)
        assertEquals("DENIED", database.posDao().restrictedActionAudit(incorrect.approvalId)?.authorizationResult)
    }

    private fun setupInput(
        storeName: String = "MariSuki Test Store",
        username: String = "owner.test",
    ) = SetupInput(
        storeName = storeName, address = null, contactNumber = null, logoBytes = null,
        ownerDisplayName = "Test Owner", ownerUsername = username,
        ownerPassword = "StorePass26!", confirmOwnerPassword = "StorePass26!",
        ownerApprovalPin = "778899", confirmOwnerApprovalPin = "778899",
        language = "en", theme = "SYSTEM", defaultCashierScreen = "SALES",
        lowStockAlertsEnabled = true, allowNegativeStock = false,
        receiptShowStoreInfo = true, receiptFooter = null,
    )

    private fun isolatedDataStore(context: android.content.Context, name: String): DataStore<Preferences> {
        val file = File(context.cacheDir, "$name-${UUID.randomUUID()}.preferences_pb")
        testStoreFiles += file
        return PreferenceDataStoreFactory.create(scope = testScope) { file }
    }
}
