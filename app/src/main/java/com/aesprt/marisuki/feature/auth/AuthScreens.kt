package com.aesprt.marisuki.feature.auth

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import java.io.ByteArrayOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aesprt.marisuki.R
import com.aesprt.marisuki.ui.theme.PosLayout
import com.aesprt.marisuki.ui.theme.PosSpacing
import com.aesprt.marisuki.domain.auth.AccountRole
import com.aesprt.marisuki.domain.auth.AuthDestination
import com.aesprt.marisuki.domain.auth.SetupDraft
import com.aesprt.marisuki.domain.auth.SetupInput

@Composable
fun AuthGate(viewModel: AuthViewModel, destination: AuthDestination, onAuthenticated: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (destination) {
        AuthDestination.Loading -> FullscreenProgress()
        is AuthDestination.SignedIn -> onAuthenticated()
        is AuthDestination.SetupComplete -> SetupCompleteScreen(destination.account.displayName, state, viewModel)
        is AuthDestination.Setup -> SetupWizard(viewModel, destination.draft, legacy = false)
        is AuthDestination.LegacyOwnerEnrollment -> SetupWizard(
            viewModel,
            draft = destination.let { null },
            legacy = true,
            initialStoreName = destination.storeName.orEmpty(),
            requiresLegacyPin = destination.legacyPinRequired,
        )
        is AuthDestination.Login -> LoginScreen(viewModel, destination.storeName.orEmpty(), destination.accounts)
    }
}

@Composable
private fun FullscreenProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun BrandHeader(subtitle: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Image(painterResource(R.drawable.marisuki_logo), contentDescription = "MariSuki", modifier = Modifier.size(92.dp))
        Text("MariSuki", style = MaterialTheme.typography.headlineMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoginScreen(viewModel: AuthViewModel, storeName: String, accounts: List<com.aesprt.marisuki.domain.auth.AccountSummary>) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selectedId by rememberSaveable(accounts) { mutableStateOf(accounts.firstOrNull()?.id.orEmpty()) }
    var credential by remember { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(state.error) { showError = state.error != null }
    val selected = accounts.firstOrNull { it.id == selectedId }
    Scaffold { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets).imePadding().padding(PosLayout.compactScreenPadding), contentAlignment = Alignment.Center) {
            val cardWidth = if (maxWidth > 700.dp) 520.dp else maxWidth
            ElevatedCard(Modifier.width(cardWidth).heightIn(max = 720.dp)) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = PosSpacing.major, vertical = PosSpacing.major),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PosSpacing.section),
                ) {
                    BrandHeader("Maligayang pagbabalik!")
                    Text(storeName, style = MaterialTheme.typography.titleLarge)
                    Box {
                        OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selected?.let { "${it.displayName} · ${if (it.role == AccountRole.OWNER) "Owner" else "Cashier"}" } ?: "Piliin ang iyong account")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            accounts.forEach { account ->
                                DropdownMenuItem(
                                    text = { Text("${account.displayName} · ${if (account.role == AccountRole.OWNER) "Owner" else "Cashier"}") },
                                    onClick = { selectedId = account.id; credential = ""; menuExpanded = false },
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = credential,
                        onValueChange = { credential = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (selected?.role == AccountRole.CASHIER) "Login PIN" else "Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (selected?.role == AccountRole.CASHIER) KeyboardType.NumberPassword else KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
                    )
                    Button(
                        onClick = { viewModel.login(selectedId, credential); credential = "" },
                        enabled = !state.busy && selected != null && credential.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Mag-login / Log in")
                    }
                    if (showError) Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupWizard(
    viewModel: AuthViewModel,
    draft: SetupDraft?,
    legacy: Boolean,
    initialStoreName: String = "",
    requiresLegacyPin: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(if (legacy) 1 else (draft?.step ?: 0).coerceAtMost(2)) }
    var language by rememberSaveable { mutableStateOf(draft?.language ?: "fil") }
    var storeName by rememberSaveable { mutableStateOf(draft?.storeName?.ifBlank { initialStoreName } ?: initialStoreName) }
    var address by rememberSaveable { mutableStateOf(draft?.address.orEmpty()) }
    var contact by rememberSaveable { mutableStateOf(draft?.contactNumber.orEmpty()) }
    var logoBytes by remember { mutableStateOf(draft?.logoBytes) }
    var logoError by remember { mutableStateOf<String?>(null) }
    var ownerName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var ownerPin by remember { mutableStateOf("") }
    var confirmOwnerPin by remember { mutableStateOf("") }
    var legacyPin by remember { mutableStateOf("") }
    var theme by rememberSaveable { mutableStateOf(draft?.theme ?: "SYSTEM") }
    var cashierScreen by rememberSaveable { mutableStateOf(draft?.defaultCashierScreen ?: "SALES") }
    var lowStockAlerts by rememberSaveable { mutableStateOf(draft?.lowStockAlertsEnabled ?: true) }
    var allowNegativeStock by rememberSaveable { mutableStateOf(draft?.allowNegativeStock ?: false) }
    var receiptShowStore by rememberSaveable { mutableStateOf(draft?.receiptShowStoreInfo ?: true) }
    var receiptFooter by rememberSaveable { mutableStateOf(draft?.receiptFooter.orEmpty()) }
    var deviceCredentialConfirmed by rememberSaveable { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            runCatching {
                withContext(Dispatchers.IO) { readLogoBytes(context, uri) }
            }.onSuccess { bytes ->
                logoBytes = bytes
                logoError = null
                if (!legacy) saveCurrentDraft(
                    viewModel, storeName, address, contact, language, theme, cashierScreen,
                    lowStockAlerts, allowNegativeStock, receiptShowStore, receiptFooter, bytes, step.coerceAtLeast(1),
                )
            }.onFailure { error -> logoError = error.message ?: "Could not read that image." }
        }
    }
    val keyguard = remember(context) { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    val deviceCredentialLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        deviceCredentialConfirmed = result.resultCode == Activity.RESULT_OK
    }
    LaunchedEffect(state.error) { showError = state.error != null }

    val allSteps = if (legacy) 3 else 4
    Scaffold { insets ->
        BoxWithConstraints(
            Modifier.fillMaxSize().padding(insets).imePadding().padding(horizontal = PosLayout.compactScreenPadding, vertical = PosSpacing.sm),
            contentAlignment = Alignment.Center,
        ) {
            val contentWidth = if (maxWidth >= 900.dp) 760.dp else maxWidth
            val wideWizard = maxWidth >= 900.dp
            ElevatedCard(Modifier.width(contentWidth).heightIn(max = maxHeight)) {
                Column(
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = PosSpacing.major, vertical = PosSpacing.section),
                    verticalArrangement = Arrangement.spacedBy(PosSpacing.section),
                ) {
                    if (step == 0 && !legacy) {
                        BrandHeader("Welcome to your store")
                        Text("Offline na cashier at tindahan management para sa iyong grocery.", style = MaterialTheme.typography.bodyLarge)
                        LanguagePicker(language) { language = it }
                        Button(onClick = { step = 1 }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (language == "fil") "Simulan" else "Get Started") }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!legacy && step > 1) IconButton(onClick = { step = (step - 1).coerceAtLeast(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                            Column(Modifier.weight(1f)) {
                                Text(if (legacy) "Irehistro ang may-ari" else "I-set up ang tindahan", style = MaterialTheme.typography.headlineSmall)
                                Text(if (legacy) "Enrollment para mapanatili ang dating store data" else "Hakbang ${step.coerceAtMost(allSteps)} sa $allSteps", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.Storefront, contentDescription = null)
                        }
                        LinearProgressIndicator(progress = { step.toFloat() / allSteps }, modifier = Modifier.fillMaxWidth())
                        if (step == 1) {
                            Text("Impormasyon ng tindahan", style = MaterialTheme.typography.titleLarge)
                            if (wideWizard) Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                                OutlinedTextField(storeName, { storeName = it }, Modifier.weight(1f), label = { Text("Pangalan ng tindahan *") }, singleLine = true)
                                OutlinedTextField(contact, { contact = it }, Modifier.weight(1f), label = { Text("Contact number (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                            } else OutlinedTextField(storeName, { storeName = it }, Modifier.fillMaxWidth(), label = { Text("Pangalan ng tindahan *") }, singleLine = true)
                            OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("Address (optional)") }, minLines = 2)
                            if (!wideWizard) OutlinedTextField(contact, { contact = it }, Modifier.fillMaxWidth(), label = { Text("Contact number (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                            if (!legacy) {
                                OutlinedButton(onClick = { logoPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (logoBytes == null) "Choose store logo (optional)" else "Store logo selected · Change")
                                }
                                logoError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            }
                            Text("Currency: Philippine peso (PHP)", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = {
                                    if (storeName.isBlank()) return@Button
                                    step = 2
                                    saveCurrentDraft(viewModel, storeName, address, contact, language, theme, cashierScreen, lowStockAlerts, allowNegativeStock, receiptShowStore, receiptFooter, logoBytes, 2)
                                },
                                enabled = storeName.isNotBlank() && !state.busy,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) { Text("Magpatuloy / Continue") }
                        } else if (step == 2) {
                            Text(if (legacy) "Gumawa ng bagong owner account" else "Gumawa ng Owner account", style = MaterialTheme.typography.titleLarge)
                            if (wideWizard) Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                                OutlinedTextField(ownerName, { ownerName = it }, Modifier.weight(1f), label = { Text("Pangalan ng owner *") }, singleLine = true)
                                OutlinedTextField(username, { username = it }, Modifier.weight(1f), label = { Text("Username *") }, singleLine = true)
                            } else {
                                OutlinedTextField(ownerName, { ownerName = it }, Modifier.fillMaxWidth(), label = { Text("Pangalan ng owner *") }, singleLine = true)
                                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username *") }, singleLine = true)
                            }
                            if (wideWizard) Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                                OutlinedTextField(password, { password = it }, Modifier.weight(1f), label = { Text("Password (8+ characters) *") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                                OutlinedTextField(confirmPassword, { confirmPassword = it }, Modifier.weight(1f), label = { Text("Kumpirmahin ang password *") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                            } else {
                                OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password (8+ characters) *") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                                OutlinedTextField(confirmPassword, { confirmPassword = it }, Modifier.fillMaxWidth(), label = { Text("Kumpirmahin ang password *") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                            }
                            if (wideWizard) Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                                OutlinedTextField(ownerPin, { if (it.length <= 6 && it.all(Char::isDigit)) ownerPin = it }, Modifier.weight(1f), label = { Text("Owner approval PIN (6 digits) *") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                                OutlinedTextField(confirmOwnerPin, { if (it.length <= 6 && it.all(Char::isDigit)) confirmOwnerPin = it }, Modifier.weight(1f), label = { Text("Kumpirmahin ang Owner PIN *") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                            } else {
                                OutlinedTextField(ownerPin, { if (it.length <= 6 && it.all(Char::isDigit)) ownerPin = it }, Modifier.fillMaxWidth(), label = { Text("Owner approval PIN (6 digits) *") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                                OutlinedTextField(confirmOwnerPin, { if (it.length <= 6 && it.all(Char::isDigit)) confirmOwnerPin = it }, Modifier.fillMaxWidth(), label = { Text("Kumpirmahin ang Owner PIN *") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                            }
                            if (legacy) {
                                if (requiresLegacyPin) {
                                    OutlinedTextField(legacyPin, { legacyPin = it }, Modifier.fillMaxWidth(), label = { Text("Dating Owner PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                                } else {
                                    Text("Kumpirmahin ang screen lock ng device para mapatunayan ang enrollment.", style = MaterialTheme.typography.bodyMedium)
                                    OutlinedButton(
                                        onClick = {
                                            val intent = keyguard.createConfirmDeviceCredentialIntent("May-ari ng tindahan", "Kumpirmahin upang magpatuloy")
                                            if (intent != null) deviceCredentialLauncher.launch(intent)
                                            else context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) { Text(if (deviceCredentialConfirmed) "Na-verify ang screen lock" else if (keyguard.isDeviceSecure) "Kumpirmahin ang screen lock" else "I-set up ang screen lock") }
                                }
                            }
                            Button(
                                onClick = { step = 3; if (!legacy) saveCurrentDraft(viewModel, storeName, address, contact, language, theme, cashierScreen, lowStockAlerts, allowNegativeStock, receiptShowStore, receiptFooter, logoBytes, 3) },
                                enabled = ownerName.isNotBlank() && username.isNotBlank() && password.isNotBlank() && confirmPassword.isNotBlank() && ownerPin.length == 6 && confirmOwnerPin.length == 6 && (legacy || password == confirmPassword && ownerPin == confirmOwnerPin) && !state.busy && (legacy || true),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                            ) { Text("Magpatuloy / Continue") }
                        } else if (step == 3) {
                            Text("Mga pangunahing preference", style = MaterialTheme.typography.titleLarge)
                            LanguagePicker(language) { language = it }
                            Text("Theme", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("LIGHT" to "Light", "DARK" to "Dark", "SYSTEM" to "System").forEach { (value, label) ->
                                    if (theme == value) Button(onClick = { theme = value }) { Text(label) }
                                    else OutlinedButton(onClick = { theme = value }) { Text(label) }
                                }
                            }
                            Text("Default cashier screen", style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("SALES" to "Sales", "PRODUCTS" to "Products", "CREDIT" to "Utang").forEach { (value, label) ->
                                    if (cashierScreen == value) Button(onClick = { cashierScreen = value }) { Text(label) }
                                    else OutlinedButton(onClick = { cashierScreen = value }) { Text(label) }
                                }
                            }
                            PreferenceToggle("Low-stock alerts", lowStockAlerts) { lowStockAlerts = it }
                            PreferenceToggle("Allow negative stock", allowNegativeStock) { allowNegativeStock = it }
                            PreferenceToggle("Show store information on slips", receiptShowStore) { receiptShowStore = it }
                            OutlinedTextField(receiptFooter, { receiptFooter = it }, Modifier.fillMaxWidth(), label = { Text("Slip footer (optional)") }, singleLine = true)
                            Button(
                                onClick = {
                                    val input = SetupInput(
                                        storeName = storeName,
                                        address = address,
                                        contactNumber = contact,
                                        logoBytes = logoBytes,
                                        ownerDisplayName = ownerName,
                                        ownerUsername = username,
                                        ownerPassword = password,
                                        confirmOwnerPassword = confirmPassword,
                                        ownerApprovalPin = ownerPin,
                                        confirmOwnerApprovalPin = confirmOwnerPin,
                                        language = language,
                                        theme = theme,
                                        defaultCashierScreen = cashierScreen,
                                        lowStockAlertsEnabled = lowStockAlerts,
                                        allowNegativeStock = allowNegativeStock,
                                        receiptShowStoreInfo = receiptShowStore,
                                        receiptFooter = receiptFooter,
                                    )
                                    if (legacy) viewModel.enrollLegacyOwner(input, if (requiresLegacyPin) legacyPin else null, deviceCredentialConfirmed)
                                    else viewModel.finishSetup(input)
                                },
                                enabled = !state.busy && ownerName.isNotBlank() && password == confirmPassword && ownerPin == confirmOwnerPin,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                            ) {
                                if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                else Text("Tapusin ang setup / Complete setup")
                            }
                        }
                        if (showError && state.error != null) Text(state.error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

private fun saveCurrentDraft(
    viewModel: AuthViewModel,
    storeName: String,
    address: String,
    contact: String,
    language: String,
    theme: String,
    cashierScreen: String,
    lowStock: Boolean,
    negativeStock: Boolean,
    receiptShowStore: Boolean,
    receiptFooter: String,
    logoBytes: ByteArray?,
    nextStep: Int,
) {
    viewModel.saveDraft(
        SetupDraft(
            step = nextStep,
            storeName = storeName,
            address = address,
            contactNumber = contact,
            logoBytes = logoBytes,
            language = language,
            theme = theme,
            defaultCashierScreen = cashierScreen,
            lowStockAlertsEnabled = lowStock,
            allowNegativeStock = negativeStock,
            receiptShowStoreInfo = receiptShowStore,
            receiptFooter = receiptFooter,
        ),
        next = nextStep,
    )
}

private fun readLogoBytes(context: Context, uri: Uri): ByteArray {
    val input = context.contentResolver.openInputStream(uri) ?: error("Could not open that image.")
    input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_STORE_LOGO_BYTES) { "Choose an image smaller than 1 MB." }
            output.write(buffer, 0, count)
        }
        require(total > 0) { "The selected image is empty." }
        return output.toByteArray()
    }
}

private const val MAX_STORE_LOGO_BYTES = 1_000_000

@Composable
private fun PreferenceToggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun LanguagePicker(value: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs), verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
        Text("Wika / Language")
        if (value == "fil") Button(onClick = { onSelect("fil") }) { Text("Filipino") } else OutlinedButton(onClick = { onSelect("fil") }) { Text("Filipino") }
        if (value == "en") Button(onClick = { onSelect("en") }) { Text("English") } else OutlinedButton(onClick = { onSelect("en") }) { Text("English") }
    }
}

@Composable
private fun SetupCompleteScreen(ownerName: String, state: AuthUiState, viewModel: AuthViewModel) {
    Scaffold { insets ->
        Box(Modifier.fillMaxSize().padding(insets).padding(20.dp), contentAlignment = Alignment.Center) {
            ElevatedCard(Modifier.width(600.dp).heightIn(max = 700.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    BrandHeader("Handa na ang iyong tindahan!")
                    Text(state.storeName, style = MaterialTheme.typography.titleLarge)
                    Text("Owner: $ownerName", style = MaterialTheme.typography.titleLarge)
                    Text("Language: ${if (state.language == "fil") "Filipino" else "English"} · Theme: ${state.theme.lowercase().replaceFirstChar(Char::uppercase)}")
                    Text("Handa na ang tindahan. Naka-save na ang store setup at owner account sa device na ito.")
                    Button(onClick = viewModel::continueToPos, enabled = !state.busy, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Pumunta sa POS")
                    }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}
