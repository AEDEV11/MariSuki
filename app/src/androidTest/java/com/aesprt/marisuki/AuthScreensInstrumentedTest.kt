package com.aesprt.marisuki

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aesprt.marisuki.data.AppPreferences
import com.aesprt.marisuki.data.auth.AuthRepository
import com.aesprt.marisuki.data.auth.CredentialSecurity
import com.aesprt.marisuki.data.auth.EncryptedSessionStore
import com.aesprt.marisuki.data.local.PosDatabase
import com.aesprt.marisuki.domain.auth.SetupInput
import com.aesprt.marisuki.feature.auth.AuthGate
import com.aesprt.marisuki.feature.auth.AuthViewModel
import com.aesprt.marisuki.ui.theme.MariSukiTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runner.RunWith

/** All store/account data in these flows lives in a disposable in-memory database. */
@RunWith(AndroidJUnit4::class)
class AuthScreensInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var database: PosDatabase
    private lateinit var auth: AuthRepository
    private lateinit var scope: CoroutineScope
    private val files = mutableListOf<File>()

    @Before fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        fun store() = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "auth-ui-${UUID.randomUUID()}.preferences_pb").also { files += it }
        }
        database = Room.inMemoryDatabaseBuilder(context, PosDatabase::class.java).build()
        auth = AuthRepository(database, database.posDao(), AppPreferences(context, store()), CredentialSecurity(), EncryptedSessionStore(context, store()))
    }

    @After fun tearDown() {
        scope.cancel()
        database.close()
        files.forEach(File::delete)
    }

    private fun showAuth() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = Configuration(context.resources.configuration).apply { setLocale(Locale.ENGLISH) }
        val localized = context.createConfigurationContext(config)
        lateinit var viewModel: AuthViewModel
        compose.runOnUiThread { viewModel = AuthViewModel(auth) }
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalResources provides localized.resources, LocalConfiguration provides config) {
                MariSukiTheme(themePreference = "LIGHT") {
                    val state by viewModel.state.collectAsStateWithLifecycle()
                    AuthGate(viewModel, state.destination) { androidx.compose.material3.Text("Signed in successfully") }
                }
            }
        }
    }

    @Test fun loginUsesTypedUsernameAndPasswordAndCanSubmitFromKeyboard() {
        runBlocking {
            auth.completeFirstTimeSetup(SetupInput(
                storeName = "UI Test Store", address = null, contactNumber = null, logoBytes = null,
                language = "en", theme = "SYSTEM", defaultCashierScreen = "SALES",
                lowStockAlertsEnabled = true, allowNegativeStock = false, receiptShowStoreInfo = true, receiptFooter = null,
                ownerDisplayName = "UI Owner", ownerUsername = "ui.owner",
                ownerPassword = "UiTestPass26!", confirmOwnerPassword = "UiTestPass26!",
                ownerApprovalPin = "778899", confirmOwnerApprovalPin = "778899",
            ))
            auth.logout()
        }
        showAuth()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Username").fetchSemanticsNodes().isNotEmpty() }
        capture("login")
        compose.onNodeWithText("Username").performTextInput(" UI.OWNER ")
        compose.onNodeWithText("Password or login PIN").performTextInput("UiTestPass26!")
        compose.onNodeWithText("Password or login PIN").performImeAction()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Signed in successfully").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun initialSetupNavigatesBetweenStoreOwnerAndPreferences() {
        showAuth()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("English").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("English").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty() }
        capture("welcome")
        compose.onNodeWithText("Get started").performScrollTo().performClick()
        capture("store")
        compose.onNodeWithText("Store name *").performScrollTo().performTextInput("UI Test Store")
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        capture("owner")
        compose.onNodeWithText("Owner name *").performScrollTo().performTextInput("UI Owner")
        compose.onNodeWithText("Username *").performScrollTo().performTextInput("ui.owner")
        compose.onNodeWithText("Password (8+ characters) *").performScrollTo().performTextInput("UiTestPass26!")
        compose.onNodeWithText("Confirm password *").performScrollTo().performTextInput("UiTestPass26!")
        compose.onNodeWithText("Owner approval PIN (6 digits) *").performScrollTo().performTextInput("778899")
        compose.onNodeWithText("Confirm owner PIN *").performScrollTo().performTextInput("778899")
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Step 3 of 3").assertIsDisplayed()
        compose.onNodeWithText("A setup that suits you").assertIsDisplayed()
        capture("preferences")
        compose.onNodeWithText("Complete setup").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("All set for your first sale").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Open your store").performScrollTo().assertIsDisplayed()
        capture("complete")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, "auth-preview-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
