package com.aesprt.marisuki.feature.auth

import android.content.res.Configuration
import java.util.Locale
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import java.io.ByteArrayOutputStream
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.aesprt.marisuki.ui.components.ToolChip
import com.aesprt.marisuki.ui.theme.PosRadius
import com.aesprt.marisuki.ui.theme.PosSpacing
import com.aesprt.marisuki.domain.auth.AuthDestination
import com.aesprt.marisuki.domain.auth.SetupDraft
import com.aesprt.marisuki.domain.auth.SetupInput

@Composable
fun AuthGate(
    viewModel: AuthViewModel,
    destination: AuthDestination,
    onAuthenticated: @Composable () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (destination is AuthDestination.SignedIn) {
        onAuthenticated()
        return
    }
    val context = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val configuration = remember(state.language, baseConfiguration) {
        Configuration(baseConfiguration).apply { setLocale(Locale.forLanguageTag(state.language)) }
    }
    val resources = remember(context, configuration) { context.createConfigurationContext(configuration).resources }
    CompositionLocalProvider(LocalResources provides resources, LocalConfiguration provides configuration) {
        when (destination) {
            AuthDestination.Loading -> FullscreenProgress()
            is AuthDestination.SignedIn -> onAuthenticated()
            is AuthDestination.SetupComplete -> SetupCompleteScreen(
                destination.account.displayName,
                state,
                viewModel
            )

            is AuthDestination.Setup -> SetupWizard(viewModel, destination.draft, legacy = false)
            is AuthDestination.LegacyOwnerEnrollment -> SetupWizard(
                viewModel,
                draft = destination.let { null },
                legacy = true,
                initialStoreName = destination.storeName.orEmpty(),
                requiresLegacyPin = destination.legacyPinRequired,
            )

            is AuthDestination.Login -> LoginScreen(
                viewModel,
                destination.storeName.orEmpty()
            )
        }
    }
}

@Composable
private fun FullscreenProgress() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
internal fun BrandHeader(subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthLogo()
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoginScreen(viewModel: AuthViewModel, storeName: String) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    var credential by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(state.error) { showError = state.error != null }
    val submit = {
        if (!state.busy && username.isNotBlank() && credential.isNotEmpty()) {
            keyboardController?.hide()
            viewModel.login(username, credential)
            credential = ""
        }
    }
    AuthFrame {
        BrandHeader(stringResource(R.string.auth_welcome_back))
        if (storeName.isNotBlank()) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(storeName, style = MaterialTheme.typography.titleSmall)
            }
        }
        Text(stringResource(R.string.auth_sign_in_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.auth_sign_in_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AuthTextField(
            username, { username = it; showError = false }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.auth_username)) }, singleLine = true, enabled = !state.busy,
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
        )
        AuthTextField(
            credential, { credential = it; showError = false }, Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.auth_password_or_pin)) }, singleLine = true, enabled = !state.busy,
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        if (showError && state.error != null) AuthNotice(state.error.orEmpty(), error = true)
        Button(onClick = { submit() }, enabled = !state.busy && username.isNotBlank() && credential.isNotEmpty(),
            shape = PosRadius.button, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(stringResource(R.string.auth_sign_in))
        }
        Text(stringResource(R.string.auth_login_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SetupWizard(
    viewModel: AuthViewModel,
    draft: SetupDraft?,
    legacy: Boolean,
    initialStoreName: String = "",
    requiresLegacyPin: Boolean = false,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by rememberSaveable {
        mutableStateOf(
            if (legacy) 1 else (draft?.step ?: 0).coerceAtMost(2)
        )
    }
    var language by rememberSaveable { mutableStateOf(draft?.language ?: "fil") }
    var storeName by rememberSaveable {
        mutableStateOf(draft?.storeName?.ifBlank { initialStoreName } ?: initialStoreName)
    }
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
    var allowNegativeStock by rememberSaveable {
        mutableStateOf(
            draft?.allowNegativeStock ?: false
        )
    }
    var receiptShowStore by rememberSaveable { mutableStateOf(draft?.receiptShowStoreInfo ?: true) }
    var receiptFooter by rememberSaveable { mutableStateOf(draft?.receiptFooter.orEmpty()) }
    var deviceCredentialConfirmed by rememberSaveable { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri != null) scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) { readLogoBytes(context, uri) }
                }.onSuccess { bytes ->
                    logoBytes = bytes
                    logoError = null
                    if (!legacy) saveCurrentDraft(
                        viewModel,
                        storeName,
                        address,
                        contact,
                        language,
                        theme,
                        cashierScreen,
                        lowStockAlerts,
                        allowNegativeStock,
                        receiptShowStore,
                        receiptFooter,
                        bytes,
                        step.coerceAtLeast(1),
                    )
                }.onFailure { error -> logoError = error.message ?: "Could not read that image." }
            }
        }
    val keyguard =
        remember(context) { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }
    val deviceTitle = stringResource(R.string.auth_verify_device_title)
    val devicePrompt = stringResource(R.string.auth_verify_device_prompt)
    val deviceCredentialLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            deviceCredentialConfirmed = result.resultCode == Activity.RESULT_OK
        }
    LaunchedEffect(state.error) { showError = state.error != null }

    val allSteps = 3
    val wideWizard = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    AuthFrame(scrollKey = step) {
        if (step == 0 && !legacy) {
            BrandHeader(stringResource(R.string.auth_welcome))
            Text(stringResource(R.string.auth_welcome_body), style = MaterialTheme.typography.bodyLarge)
            AuthHeading(stringResource(R.string.auth_feature_sales), stringResource(R.string.auth_feature_offline), Icons.Default.PointOfSale)
            AuthHeading(stringResource(R.string.auth_feature_stock), stringResource(R.string.auth_hero_body), Icons.Default.Inventory2)
            LanguagePicker(language) {
                language = it
                viewModel.previewSetupLanguage(it)
                if (!legacy && step > 0) saveCurrentDraft(viewModel, storeName, address, contact, language, theme,
                    cashierScreen, lowStockAlerts, allowNegativeStock, receiptShowStore, receiptFooter, logoBytes, step)
            }
            Button(
                onClick = { step = 1 },
                shape = PosRadius.button,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
            ) { Text(stringResource(R.string.auth_start)) }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!legacy && step > 1) IconButton(onClick = {
                    step = (step - 1).coerceAtLeast(1)
                }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.back)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (legacy) R.string.auth_enroll_title else R.string.auth_setup_title),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        if (legacy) stringResource(R.string.auth_enroll_body) else stringResource(R.string.auth_step, step, allSteps),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AuthIcon(when (step) { 1 -> Icons.Default.Storefront; 2 -> Icons.Default.Lock; else -> Icons.Default.Tune })
            }
            SetupStepIndicator(step)
            LinearProgressIndicator(
                progress = { step.toFloat() / allSteps },
                modifier = Modifier.fillMaxWidth()
            )
            when {
                step == 1 -> {
                    AuthHeading(stringResource(R.string.auth_store_title), stringResource(R.string.auth_store_body), Icons.Default.Storefront)
                    if (wideWizard) Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            PosSpacing.sm
                        )
                    ) {
                        AuthTextField(
                            storeName,
                            { storeName = it },
                            Modifier.weight(1f),
                            label = { Text(stringResource(R.string.auth_store_name)) },
                            singleLine = true
                        )
                        AuthTextField(
                            contact,
                            { contact = it },
                            Modifier.weight(1f),
                            label = { Text(stringResource(R.string.auth_contact)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                        )
                    } else AuthTextField(
                        storeName,
                        { storeName = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.auth_store_name)) },
                        singleLine = true
                    )
                    AuthTextField(
                        address,
                        { address = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.auth_address)) },
                        minLines = 2
                    )
                    if (!wideWizard) AuthTextField(
                        contact,
                        { contact = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.auth_contact)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    if (!legacy) {
                        OutlinedButton(
                            onClick = { logoPicker.launch(arrayOf("image/*")) },
                            shape = PosRadius.button,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(if (logoBytes == null) R.string.auth_choose_logo else R.string.auth_change_logo))
                        }
                        logoError?.let {
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.auth_currency),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        shape = PosRadius.button,
                        onClick = {
                            if (storeName.isBlank()) return@Button
                            step = 2
                            saveCurrentDraft(
                                viewModel,
                                storeName,
                                address,
                                contact,
                                language,
                                theme,
                                cashierScreen,
                                lowStockAlerts,
                                allowNegativeStock,
                                receiptShowStore,
                                receiptFooter,
                                logoBytes,
                                2
                            )
                        },
                        enabled = storeName.isNotBlank() && !state.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp),
                    ) { Text(stringResource(R.string.auth_continue)) }
                }

                step == 2 -> {
                    AuthHeading(stringResource(R.string.auth_owner_title), stringResource(R.string.auth_owner_body), Icons.Default.Person)
                    if (wideWizard) Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            PosSpacing.sm
                        )
                    ) {
                        AuthTextField(
                            ownerName,
                            { ownerName = it },
                            Modifier.weight(1f),
                            label = { Text(stringResource(R.string.auth_owner_name)) },
                            singleLine = true
                        )
                        AuthTextField(
                            username,
                            { username = it },
                            Modifier.weight(1f),
                            label = { Text(stringResource(R.string.auth_username_required)) },
                            singleLine = true
                        )
                    } else {
                        AuthTextField(
                            ownerName,
                            { ownerName = it },
                            Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.auth_owner_name)) },
                            singleLine = true
                        )
                        AuthTextField(
                            username,
                            { username = it },
                            Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.auth_username_required)) },
                            singleLine = true
                        )
                    }
                    if (wideWizard) Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            PosSpacing.sm
                        )
                    ) {
                        AuthTextField(
                            password,
                            { password = it },
                            Modifier.weight(1f),
                            label = { Text(stringResource(R.string.auth_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        AuthTextField(
                            confirmPassword,
                            { confirmPassword = it },
                            Modifier.weight(1f),
                            label = { Text(stringResource(R.string.auth_confirm_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    } else {
                        AuthTextField(
                            password,
                            { password = it },
                            Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.auth_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                        AuthTextField(
                            confirmPassword,
                            { confirmPassword = it },
                            Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.auth_confirm_password)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                        )
                    }
                    if (wideWizard) Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            PosSpacing.sm
                        )
                    ) {
                        AuthTextField(
                            ownerPin,
                            {
                                if (it.length <= 6 && it.all(Char::isDigit)) ownerPin =
                                    it
                            },
                            Modifier.weight(1f),
                            label = { Text(stringResource(R.string.auth_owner_pin)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                        AuthTextField(
                            confirmOwnerPin,
                            {
                                if (it.length <= 6 && it.all(Char::isDigit)) confirmOwnerPin =
                                    it
                            },
                            Modifier.weight(1f),
                            label = { Text(stringResource(R.string.auth_confirm_pin)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                    } else {
                        AuthTextField(
                            ownerPin,
                            {
                                if (it.length <= 6 && it.all(Char::isDigit)) ownerPin =
                                    it
                            },
                            Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.auth_owner_pin)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                        AuthTextField(
                            confirmOwnerPin,
                            {
                                if (it.length <= 6 && it.all(Char::isDigit)) confirmOwnerPin =
                                    it
                            },
                            Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.auth_confirm_pin)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                        )
                    }
                    if (confirmPassword.isNotEmpty() && password != confirmPassword) AuthNotice(stringResource(R.string.auth_password_mismatch), error = true)
                    if (confirmOwnerPin.isNotEmpty() && ownerPin != confirmOwnerPin) AuthNotice(stringResource(R.string.auth_pin_mismatch), error = true)
                    if (legacy) {
                        if (requiresLegacyPin) {
                            AuthTextField(
                                legacyPin,
                                { legacyPin = it },
                                Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.auth_legacy_pin)) },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                            )
                        } else {
                            Text(
                                stringResource(R.string.auth_verify_device_body),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedButton(
                                onClick = {
                                    @Suppress("DEPRECATION")
                                    val intent =
                                        keyguard.createConfirmDeviceCredentialIntent(
                                            deviceTitle,
                                            devicePrompt
                                        )
                                    if (intent != null) deviceCredentialLauncher.launch(
                                        intent
                                    )
                                    else context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                                },
                                shape = PosRadius.button,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) { Text(stringResource(if (deviceCredentialConfirmed) R.string.auth_device_verified else if (keyguard.isDeviceSecure) R.string.auth_verify_device else R.string.auth_set_device_lock)) }
                        }
                    }
                    Button(
                        shape = PosRadius.button,
                        onClick = {
                            step = 3; if (!legacy) saveCurrentDraft(
                            viewModel,
                            storeName,
                            address,
                            contact,
                            language,
                            theme,
                            cashierScreen,
                            lowStockAlerts,
                            allowNegativeStock,
                            receiptShowStore,
                            receiptFooter,
                            logoBytes,
                            3
                        )
                        },
                        enabled = ownerName.isNotBlank() && username.isNotBlank() && password.length >= 8 && confirmPassword.isNotBlank() && ownerPin.length == 6 && confirmOwnerPin.length == 6 && (password == confirmPassword && ownerPin == confirmOwnerPin) && !state.busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp),
                    ) { Text(stringResource(R.string.auth_continue)) }
                }

                step == 3 -> {
                    AuthHeading(stringResource(R.string.auth_preferences_title), stringResource(R.string.auth_preferences_body), Icons.Default.Tune)
                    LanguagePicker(language) {
                        language = it
                        viewModel.previewSetupLanguage(it)
                        if (!legacy && step > 0) saveCurrentDraft(viewModel, storeName, address, contact, language, theme,
                            cashierScreen, lowStockAlerts, allowNegativeStock, receiptShowStore, receiptFooter, logoBytes, step)
                    }
                    Text(stringResource(R.string.auth_theme), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("LIGHT" to R.string.auth_theme_light, "DARK" to R.string.auth_theme_dark, "SYSTEM" to R.string.auth_theme_system).forEach { (value, label) ->
                            ToolChip(selected = theme == value, onClick = { theme = value }, label = { Text(stringResource(label)) })
                        }
                    }
                    Text(stringResource(R.string.auth_default_screen), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("SALES" to R.string.auth_sales, "PRODUCTS" to R.string.auth_products, "CREDIT" to R.string.auth_credit).forEach { (value, label) ->
                            ToolChip(selected = cashierScreen == value, onClick = { cashierScreen = value }, label = { Text(stringResource(label)) })
                        }
                    }
                    PreferenceToggle(
                        stringResource(R.string.auth_low_stock),
                        lowStockAlerts
                    ) { lowStockAlerts = it }
                    PreferenceToggle(
                        stringResource(R.string.auth_negative_stock),
                        allowNegativeStock
                    ) { allowNegativeStock = it }
                    PreferenceToggle(
                        stringResource(R.string.auth_receipt_info),
                        receiptShowStore
                    ) {
                        receiptShowStore = it
                    }
                    AuthTextField(
                        receiptFooter,
                        { receiptFooter = it },
                        Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.auth_footer)) },
                        singleLine = true
                    )
                    Button(
                        shape = PosRadius.button,
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
                            if (legacy) viewModel.enrollLegacyOwner(
                                input,
                                if (requiresLegacyPin) legacyPin else null,
                                deviceCredentialConfirmed
                            )
                            else viewModel.finishSetup(input)
                        },
                        enabled = !state.busy && ownerName.isNotBlank() && password == confirmPassword && ownerPin == confirmOwnerPin,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp),
                    ) {
                        if (state.busy) CircularProgressIndicator(
                            Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        else Text(stringResource(R.string.auth_finish_setup))
                    }
                }

            }
        }
        if (showError && state.error != null) AuthNotice(state.error.orEmpty(), error = true)
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
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = PosRadius.input) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = value, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun LanguagePicker(value: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.auth_language), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolChip(value == "fil", { onSelect("fil") }, label = { Text(stringResource(R.string.auth_filipino)) })
            ToolChip(value == "en", { onSelect("en") }, label = { Text(stringResource(R.string.auth_english)) })
        }
    }
}

@Composable
private fun SetupStepIndicator(step: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.auth_step_store, R.string.auth_step_owner, R.string.auth_step_preferences).forEachIndexed { index, title ->
            val active = index + 1 == step
            val done = index + 1 < step
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(10.dp),
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) {
                Column(Modifier.padding(vertical = 8.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (done) Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    else Text("${index + 1}", style = MaterialTheme.typography.labelLarge)
                    Text(stringResource(title), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun SetupCompleteScreen(ownerName: String, state: AuthUiState, viewModel: AuthViewModel) {
    AuthFrame {
        BrandHeader(stringResource(R.string.auth_complete_welcome))
        AuthHeading(stringResource(R.string.auth_complete_title), stringResource(R.string.auth_complete_body), Icons.Default.CheckCircle)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = PosRadius.card) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.storeName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(stringResource(R.string.auth_owner_summary, ownerName), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.auth_preferences_summary,
                    stringResource(if (state.language == "fil") R.string.auth_filipino else R.string.auth_english),
                    stringResource(when (state.theme) { "LIGHT" -> R.string.auth_theme_light; "DARK" -> R.string.auth_theme_dark; else -> R.string.auth_theme_system })),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick = viewModel::continueToPos, enabled = !state.busy, shape = PosRadius.button, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
            if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Text(stringResource(R.string.auth_open_pos))
        }
        state.error?.let { AuthNotice(it, error = true) }
    }
}
