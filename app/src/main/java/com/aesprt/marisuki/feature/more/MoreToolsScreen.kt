package com.aesprt.marisuki.feature.more

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aesprt.marisuki.R
import com.aesprt.marisuki.BuildConfig
import com.aesprt.marisuki.data.local.dao.ProductSummary
import com.aesprt.marisuki.domain.model.DeliveryLineInput
import com.aesprt.marisuki.domain.model.RefundLineInput
import com.aesprt.marisuki.domain.model.RefundMethod
import com.aesprt.marisuki.data.local.entity.SaleItemEntity
import com.aesprt.marisuki.feature.pos.PosViewModel
import com.aesprt.marisuki.feature.owner.OwnerPinDialog
import com.aesprt.marisuki.feature.auth.StaffManagementScreen
import com.aesprt.marisuki.feature.auth.AuthViewModel
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.feature.printing.ReceiptPrinter
import com.aesprt.marisuki.feature.printing.PrinterSettingsScreen
import com.aesprt.marisuki.ui.theme.PosLayout
import com.aesprt.marisuki.ui.theme.PosSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class MoreSection { INVENTORY, HISTORY, REPORTS, DELIVERIES, EXPENSES, SETTINGS, PRINTERS, BACKUP, STAFF, DEVELOPER }

@Composable
fun MoreToolsScreen(viewModel: PosViewModel, authViewModel: AuthViewModel) {
    var section by rememberSaveable { mutableStateOf<MoreSection?>(null) }
    var pendingSection by rememberSaveable { mutableStateOf<MoreSection?>(null) }
    var showOwnerPin by rememberSaveable { mutableStateOf(false) }
    val ownerAccess by viewModel.ownerAccessGranted.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    LaunchedEffect(ownerAccess) {
        if (ownerAccess) {
            pendingSection?.let { section = it }
            pendingSection = null
            showOwnerPin = false
        }
    }
    val selectSection: (MoreSection) -> Unit = { requested ->
        val allowed = when (requested) {
            MoreSection.INVENTORY -> permissions.any { it in setOf(PosPermission.VIEW_STOCK, PosPermission.ADJUST_STOCK, PosPermission.ADD_STOCK) }
            MoreSection.HISTORY -> PosPermission.VIEW_SALES_HISTORY in permissions
            MoreSection.REPORTS -> permissions.any { it in setOf(PosPermission.VIEW_TODAYS_SALES, PosPermission.VIEW_HISTORICAL_SALES, PosPermission.VIEW_EXPENSES, PosPermission.VIEW_STOCK_VALUATION, PosPermission.VIEW_CUSTOMER_BALANCES, PosPermission.VIEW_STOCK, PosPermission.MANAGE_SUPPLIERS) }
            MoreSection.DELIVERIES -> PosPermission.MANAGE_SUPPLIERS in permissions
            MoreSection.EXPENSES -> permissions.any { it in setOf(PosPermission.VIEW_EXPENSES, PosPermission.MANAGE_EXPENSES) }
            MoreSection.SETTINGS -> PosPermission.CHANGE_SETTINGS in permissions
            MoreSection.PRINTERS -> PosPermission.MANAGE_PRINTERS in permissions
            MoreSection.BACKUP -> permissions.any { it in setOf(PosPermission.CREATE_BACKUPS, PosPermission.RESTORE_BACKUPS) }
            MoreSection.STAFF -> ownerAccess
            MoreSection.DEVELOPER -> BuildConfig.DEBUG && ownerAccess
        }
        if (!allowed) {
            pendingSection = requested
            showOwnerPin = true
        } else section = requested
    }
    val title = when (section) {
        MoreSection.INVENTORY -> R.string.inventory_title
        MoreSection.HISTORY -> R.string.sales_history
        MoreSection.REPORTS -> R.string.reports_title
        MoreSection.DELIVERIES -> R.string.deliveries_title
        MoreSection.EXPENSES -> R.string.expenses_title
        MoreSection.SETTINGS -> R.string.settings_title
        MoreSection.PRINTERS -> R.string.printer_title
        MoreSection.BACKUP -> R.string.backup_title
        MoreSection.STAFF -> R.string.staff_title
        MoreSection.DEVELOPER -> R.string.debug_title
        null -> R.string.more_title
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 700.dp
        Column(
            Modifier.widthIn(max = if (section == null) 1152.dp else PosLayout.settingsMaxWidth).fillMaxWidth().fillMaxHeight()
                .align(Alignment.TopCenter)
                .padding(horizontal = if (wide) PosLayout.tabletScreenPadding else PosLayout.compactScreenPadding, vertical = PosSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                if (section != null) IconButton(onClick = { section = null }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
                Column {
                    Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    if (section == null) Text(stringResource(R.string.more_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(PosSpacing.sm))
            when (section) {
                MoreSection.INVENTORY -> InventoryTool(viewModel)
                MoreSection.HISTORY -> SalesHistoryTool(viewModel)
                MoreSection.REPORTS -> ReportsTool(viewModel)
                MoreSection.DELIVERIES -> StockDeliveryTool(viewModel)
                MoreSection.EXPENSES -> ExpensesTool(viewModel)
                MoreSection.SETTINGS -> SettingsTool(viewModel, authViewModel, selectSection)
                MoreSection.PRINTERS -> PrinterSettingsScreen(viewModel)
                MoreSection.BACKUP -> BackupTool(viewModel)
                MoreSection.STAFF -> StaffManagementScreen()
                MoreSection.DEVELOPER -> DebugDeveloperTools()
                null -> MoreMenu(viewModel, onSelect = selectSection)
            }
        }
    }
    if (showOwnerPin) OwnerPinDialog(viewModel, onDismiss = { showOwnerPin = false; pendingSection = null })
}

private data class ToolEntry(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: String,
    val description: String,
    val onClick: () -> Unit,
)

@Composable
private fun MoreMenu(viewModel: PosViewModel, onSelect: (MoreSection) -> Unit) {
    val lowStock by viewModel.lowStockCount.collectAsStateWithLifecycle()
    val ownerAccess by viewModel.ownerAccessGranted.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val operations = buildList {
        if (permissions.any { it in setOf(PosPermission.VIEW_STOCK, PosPermission.ADJUST_STOCK, PosPermission.ADD_STOCK) })
            add(ToolEntry(Icons.Filled.Inventory, stringResource(R.string.inventory_title), stringResource(R.string.low_stock_summary, lowStock)) { onSelect(MoreSection.INVENTORY) })
        if (PosPermission.MANAGE_SUPPLIERS in permissions)
            add(ToolEntry(Icons.Filled.LocalShipping, stringResource(R.string.deliveries_title), stringResource(R.string.deliveries_description)) { onSelect(MoreSection.DELIVERIES) })
        if (PosPermission.VIEW_SALES_HISTORY in permissions)
            add(ToolEntry(Icons.Filled.History, stringResource(R.string.sales_history), stringResource(R.string.history_description)) { onSelect(MoreSection.HISTORY) })
        if (permissions.any { it in setOf(PosPermission.VIEW_EXPENSES, PosPermission.MANAGE_EXPENSES) })
            add(ToolEntry(Icons.Filled.Payments, stringResource(R.string.expenses_title), stringResource(R.string.expenses_description)) { onSelect(MoreSection.EXPENSES) })
    }
    val insights = buildList {
        if (permissions.any { it in setOf(PosPermission.VIEW_TODAYS_SALES, PosPermission.VIEW_HISTORICAL_SALES, PosPermission.VIEW_STOCK_VALUATION, PosPermission.VIEW_CUSTOMER_BALANCES) })
            add(ToolEntry(Icons.Filled.Assessment, stringResource(R.string.reports_title), stringResource(R.string.report_description)) { onSelect(MoreSection.REPORTS) })
    }
    val administration = buildList {
        if (PosPermission.CHANGE_SETTINGS in permissions) add(ToolEntry(Icons.Filled.Settings, stringResource(R.string.settings_title), stringResource(R.string.settings_description)) { onSelect(MoreSection.SETTINGS) })
        if (PosPermission.MANAGE_PRINTERS in permissions) add(ToolEntry(Icons.Filled.Settings, stringResource(R.string.printer_title), stringResource(R.string.printer_settings_description)) { onSelect(MoreSection.PRINTERS) })
        if (permissions.any { it in setOf(PosPermission.CREATE_BACKUPS, PosPermission.RESTORE_BACKUPS) }) add(ToolEntry(Icons.Filled.Restore, stringResource(R.string.backup_title), stringResource(R.string.backup_description)) { onSelect(MoreSection.BACKUP) })
        if (ownerAccess) add(ToolEntry(Icons.Filled.ManageAccounts, stringResource(R.string.staff_title), stringResource(R.string.staff_description)) { onSelect(MoreSection.STAFF) })
        if (BuildConfig.DEBUG && ownerAccess) add(ToolEntry(Icons.Filled.Settings, stringResource(R.string.debug_title), stringResource(R.string.debug_description)) { onSelect(MoreSection.DEVELOPER) })
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 280.dp),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        contentPadding = PaddingValues(bottom = PosSpacing.section),
    ) {
        if (operations.isEmpty() && insights.isEmpty() && administration.isEmpty()) item {
            Text(stringResource(R.string.more_no_tools), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(PosSpacing.section))
        }
        if (operations.isNotEmpty()) item { ToolGroup(stringResource(R.string.more_group_operations), operations) }
        if (insights.isNotEmpty()) item { ToolGroup(stringResource(R.string.more_group_insights), insights) }
        if (administration.isNotEmpty()) item { ToolGroup(stringResource(R.string.more_group_admin), administration) }
    }
}

@Composable
private fun SettingsTool(viewModel: PosViewModel, authViewModel: AuthViewModel, onNavigate: (MoreSection) -> Unit) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val ownerAccess by viewModel.ownerAccessGranted.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = PosSpacing.major), verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
        Text(stringResource(R.string.settings_general), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        ListItem(
            headlineContent = { Text(stringResource(R.string.preferred_language)) },
            supportingContent = { Text(stringResource(R.string.language_description)) },
            leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                listOf(
                    "system" to stringResource(R.string.language_system),
                    "en" to stringResource(R.string.language_english),
                    "fil" to stringResource(R.string.language_filipino),
                ).forEach { (code, label) ->
                    FilterChip(selected = language == code, onClick = { viewModel.setLanguage(code) }, label = { Text(label) })
                }
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_theme)) },
                supportingContent = { Text(stringResource(R.string.settings_theme_description)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                listOf(
                    "SYSTEM" to R.string.language_system,
                    "LIGHT" to R.string.settings_light,
                    "DARK" to R.string.settings_dark,
                ).forEach { (value, label) ->
                    FilterChip(selected = authState.theme == value, onClick = { authViewModel.setThemePreference(value) }, label = { Text(stringResource(label)) })
                }
            }
            if (PosPermission.MANAGE_PRINTERS in permissions) {
                androidx.compose.material3.HorizontalDivider()
                Text(stringResource(R.string.settings_hardware), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                ListItem(
                    modifier = Modifier.clickable { onNavigate(MoreSection.PRINTERS) },
                    headlineContent = { Text(stringResource(R.string.printer_title)) },
                    supportingContent = { Text(stringResource(R.string.printer_settings_description)) },
                    leadingContent = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
            if (ownerAccess) {
                androidx.compose.material3.HorizontalDivider()
                Text(stringResource(R.string.settings_security), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                ListItem(
                    modifier = Modifier.clickable { onNavigate(MoreSection.STAFF) },
                    headlineContent = { Text(stringResource(R.string.staff_title)) },
                    supportingContent = { Text(stringResource(R.string.staff_description)) },
                    leadingContent = { Icon(Icons.Filled.ManageAccounts, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
                TextButton(onClick = viewModel::lockOwnerTools) { Text(stringResource(R.string.lock_owner_tools)) }
            }
            if (permissions.any { it in setOf(PosPermission.CREATE_BACKUPS, PosPermission.RESTORE_BACKUPS) }) {
                androidx.compose.material3.HorizontalDivider()
                Text(stringResource(R.string.settings_data), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                ListItem(
                    modifier = Modifier.clickable { onNavigate(MoreSection.BACKUP) },
                    headlineContent = { Text(stringResource(R.string.backup_title)) },
                    supportingContent = { Text(stringResource(R.string.backup_description)) },
                    leadingContent = { Icon(Icons.Filled.Restore, contentDescription = null) },
                    trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
            androidx.compose.material3.HorizontalDivider()
            Text(stringResource(R.string.settings_about), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text("${stringResource(R.string.settings_version)} ${BuildConfig.VERSION_NAME}") },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
    }
}

@Composable
private fun BackupTool(viewModel: PosViewModel) {
    var passphrase by remember { mutableStateOf("") }
    var restoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var confirmAutomaticRestore by rememberSaveable { mutableStateOf(false) }
    val working by viewModel.isWorking.collectAsStateWithLifecycle()
    val hasAutomaticBackup by viewModel.hasAutomaticBackup.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refreshAutomaticBackup() }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let { viewModel.exportBackup(it, passphrase) }
    }
    val chooseRestore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        restoreUri = uri
    }
    Column(Modifier.verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.backup_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.automatic_backup_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { confirmAutomaticRestore = true }, enabled = hasAutomaticBackup && !working) {
                    Text(stringResource(R.string.restore_latest_automatic_backup))
                }
                OutlinedTextField(
                    passphrase,
                    { passphrase = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    supportingText = { Text(stringResource(R.string.backup_password_help)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { createBackup.launch("marisuki-backup.mskb") },
                    enabled = !working && passphrase.length >= 12,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(stringResource(if (working) R.string.saving else R.string.export_backup)) }
                TextButton(onClick = { chooseRestore.launch(arrayOf("application/octet-stream", "*/*")) }, enabled = !working && passphrase.length >= 12) {
                    Text(stringResource(R.string.choose_backup_to_restore))
                }
            }
        }
        Text(stringResource(R.string.backup_portability_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    restoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoreUri = null },
            title = { Text(stringResource(R.string.restore_confirmation_title)) },
            text = { Text(stringResource(R.string.restore_confirmation_body)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.restoreBackup(uri, passphrase); restoreUri = null },
                    enabled = !working && passphrase.length >= 12,
                ) { Text(stringResource(R.string.restore_backup)) }
            },
            dismissButton = { TextButton(onClick = { restoreUri = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmAutomaticRestore) {
        AlertDialog(
            onDismissRequest = { confirmAutomaticRestore = false },
            title = { Text(stringResource(R.string.restore_confirmation_title)) },
            text = { Text(stringResource(R.string.restore_confirmation_body)) },
            confirmButton = {
                Button(
                    onClick = { viewModel.restoreLatestAutomaticBackup(); confirmAutomaticRestore = false },
                    enabled = !working,
                ) { Text(stringResource(R.string.restore_backup)) }
            },
            dismissButton = { TextButton(onClick = { confirmAutomaticRestore = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun ToolGroup(title: String, entries: List<ToolEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                entries.forEachIndexed { index, entry ->
                    ToolRow(entry)
                    if (index < entries.lastIndex) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 56.dp))
                }
            }
        }
    }
}

@Composable
private fun ToolRow(entry: ToolEntry) {
    ListItem(
        headlineContent = { Text(entry.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1) },
        supportingContent = { Text(entry.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) },
        leadingContent = {
            Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            }
        },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = entry.onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

@Composable
private fun InventoryTool(viewModel: PosViewModel) {
    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val canAdd = PosPermission.ADD_STOCK in permissions
    val canRemove = PosPermission.ADJUST_STOCK in permissions
    var target by rememberSaveable { mutableStateOf<String?>(null) }
    if (products.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.cashier_empty_products)) }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(products, key = { it.id }) { product ->
            InventoryRow(product, canAdd = canAdd, canRemove = canRemove, onStock = { target = product.id })
        }
    }
    target?.let { productId ->
        products.firstOrNull { it.id == productId }?.let { product ->
            StockDialog(
                product = product,
                onDismiss = { target = null },
                canAdd = canAdd,
                canRemove = canRemove,
                onSave = { quantity, reason, note, increase, operationId ->
                    if (increase) viewModel.receiveStock(product.id, quantity, reason, note, operationId)
                    else viewModel.adjustStock(product.id, -quantity, reason, note, operationId)
                    target = null
                },
            )
        }
    }
}

@Composable
private fun InventoryRow(product: ProductSummary, canAdd: Boolean, canRemove: Boolean, onStock: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.stock_available, PosViewModel.formatQuantity(product.onHandMilliUnits)) + " ${product.baseUnitLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${stringResource(R.string.low_stock_threshold)} ${PosViewModel.formatQuantity(product.minimumStockMilliUnits)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (canAdd || canRemove) Button(onClick = onStock, shape = RoundedCornerShape(12.dp)) {
                Text(stringResource(if (canAdd) R.string.add_stock else R.string.remove_stock))
            }
        }
    }
}

@Composable
fun StockDialog(product: ProductSummary, onDismiss: () -> Unit, onSave: (Long, String, String?, Boolean, String) -> Unit, canAdd: Boolean = true, canRemove: Boolean = true) {
    var quantity by rememberSaveable(product.id) { mutableStateOf("") }
    var note by rememberSaveable(product.id) { mutableStateOf("") }
    var increase by rememberSaveable(product.id, canAdd, canRemove) { mutableStateOf(canAdd) }
    var reason by rememberSaveable(product.id, canAdd, canRemove) { mutableStateOf(if (canAdd) "DELIVERY" else "DAMAGED") }
    var operationId by rememberSaveable(product.id) { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.inventory_adjustment, product.name)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${stringResource(R.string.current_stock)}: ${PosViewModel.formatQuantity(product.onHandMilliUnits)} ${product.baseUnitLabel}")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (canAdd) FilterChip(selected = increase, onClick = { increase = true; reason = "DELIVERY" }, label = { Text(stringResource(R.string.add_stock)) })
                    if (canRemove) FilterChip(selected = !increase, onClick = { increase = false; reason = "DAMAGED" }, label = { Text(stringResource(R.string.remove_stock)) })
                }
                OutlinedTextField(quantity, { quantity = it }, label = { Text(stringResource(R.string.stock_quantity)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("DELIVERY", "COUNT", "DAMAGED", "EXPIRED").filter { if (increase) it != "DAMAGED" && it != "EXPIRED" else it != "DELIVERY" }.forEach { key ->
                        val label = when (key) {
                            "DELIVERY" -> stringResource(R.string.reason_delivery)
                            "COUNT" -> stringResource(R.string.reason_count)
                            "DAMAGED" -> stringResource(R.string.reason_damaged)
                            else -> stringResource(R.string.reason_expired)
                        }
                        FilterChip(selected = reason == key, onClick = { reason = key }, label = { Text(label) })
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text(stringResource(R.string.note_optional)) }, minLines = 2)
            }
        },
        confirmButton = {
            val parsed = PosViewModel.parseQuantityMilliUnits(quantity)
            Button(onClick = { parsed?.let { onSave(it, reason, note.trim().ifBlank { null }, increase, operationId) } }, enabled = parsed != null && parsed > 0) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun SalesHistoryTool(viewModel: PosViewModel) {
    val sales by viewModel.sales.collectAsStateWithLifecycle()
    val ownerAccess by viewModel.ownerAccessGranted.collectAsStateWithLifecycle()
    val working by viewModel.isWorking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val receiptPrintFailure = stringResource(R.string.receipt_print_failed)
    val scope = rememberCoroutineScope()
    var refundTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var pinRequiredForSale by rememberSaveable { mutableStateOf<String?>(null) }
    var showOwnerPin by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ownerAccess) {
        if (ownerAccess) {
            refundTarget = pinRequiredForSale
            pinRequiredForSale = null
            showOwnerPin = false
        }
    }
    if (sales.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_sales)) }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        items(sales, key = { it.id }) { sale ->
            val dateText = Instant.ofEpochMilli(sale.createdAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a"))
            Card(shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(dateText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(sale.customerName ?: stringResource(R.string.walk_in_customer), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(sale.id.take(8).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(PosViewModel.formatPeso(sale.totalCentavos), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row {
                            TextButton(onClick = {
                                scope.launch {
                                    try {
                                        val receipt = viewModel.saleReceipt(sale.id, sale.customerName)
                                            ?: throw IllegalStateException("Sale is unavailable")
                                        ReceiptPrinter.print(context, receipt)
                                    } catch (cancelled: CancellationException) {
                                        throw cancelled
                                    } catch (_: Exception) {
                                        Toast.makeText(context, receiptPrintFailure, Toast.LENGTH_LONG).show()
                                    }
                                }
                            }) { Text(stringResource(R.string.print_receipt)) }
                            TextButton(onClick = {
                                if (ownerAccess) refundTarget = sale.id else {
                                    pinRequiredForSale = sale.id
                                    showOwnerPin = true
                                }
                            }) { Text(stringResource(R.string.refund_sale)) }
                        }
                    }
                }
            }
        }
    }
    refundTarget?.let { saleId ->
        val sale = sales.firstOrNull { it.id == saleId }
        if (sale == null) refundTarget = null else {
            var saleItems by remember(saleId) { mutableStateOf<List<SaleItemEntity>?>(null) }
            LaunchedEffect(saleId) { saleItems = viewModel.saleItems(saleId) }
            val items = saleItems
            if (items == null) {
                AlertDialog(onDismissRequest = { refundTarget = null }, title = { Text(stringResource(R.string.refund_sale)) }, text = { Text(stringResource(R.string.loading_sale_items)) }, confirmButton = {})
            } else if (items.isEmpty()) {
                AlertDialog(onDismissRequest = { refundTarget = null }, title = { Text(stringResource(R.string.refund_sale)) }, text = { Text(stringResource(R.string.error_product)) }, confirmButton = { TextButton(onClick = { refundTarget = null }) { Text(stringResource(R.string.close)) } })
            } else RefundDialog(
                saleLabel = sale.id.take(8).uppercase(),
                items = items,
                hasCustomer = sale.customerName != null,
                isWorking = working,
                onDismiss = { refundTarget = null },
                onRefund = { reason, lines, method, key ->
                    viewModel.refundSale(sale.id, reason, lines, method, key)
                    refundTarget = null
                },
            )
        }
    }
    if (showOwnerPin) OwnerPinDialog(viewModel, onDismiss = { showOwnerPin = false; pinRequiredForSale = null })
}

@Composable
private fun StockDeliveryTool(viewModel: PosViewModel) {
    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val deliveries by viewModel.deliveries.collectAsStateWithLifecycle()
    val working by viewModel.isWorking.collectAsStateWithLifecycle()
    var selectedProductId by rememberSaveable { mutableStateOf<String?>(null) }
    var productMenu by remember { mutableStateOf(false) }
    var supplier by rememberSaveable { mutableStateOf("") }
    var supplierPhone by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var operationId by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    val selected = products.firstOrNull { it.id == selectedProductId } ?: products.firstOrNull()
    val parsedQuantity = PosViewModel.parseQuantityMilliUnits(quantity)
    val parsedCost = PosViewModel.parseCentavos(cost)

    if (products.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.cashier_empty_products)) }
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.delivery_form_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Box {
                        androidx.compose.material3.OutlinedButton(onClick = { productMenu = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selected?.let { "${it.name} · ${it.baseUnitLabel}" } ?: stringResource(R.string.choose_product))
                        }
                        DropdownMenu(expanded = productMenu, onDismissRequest = { productMenu = false }) {
                            products.forEach { product ->
                                DropdownMenuItem(
                                    text = { Text("${product.name} · ${product.baseUnitLabel}") },
                                    onClick = { selectedProductId = product.id; operationId = java.util.UUID.randomUUID().toString(); productMenu = false },
                                )
                            }
                        }
                    }
                    OutlinedTextField(supplier, { supplier = it; operationId = java.util.UUID.randomUUID().toString() }, label = { Text(stringResource(R.string.supplier_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(supplierPhone, { supplierPhone = it; operationId = java.util.UUID.randomUUID().toString() }, label = { Text(stringResource(R.string.supplier_phone)) }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(quantity, { quantity = it; operationId = java.util.UUID.randomUUID().toString() }, label = { Text(stringResource(R.string.delivered_quantity, selected?.baseUnitLabel.orEmpty())) }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(cost, { cost = it; operationId = java.util.UUID.randomUUID().toString() }, label = { Text(stringResource(R.string.delivery_total_cost)) }, prefix = { Text("₱ ") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    }
                    OutlinedTextField(note, { note = it; operationId = java.util.UUID.randomUUID().toString() }, label = { Text(stringResource(R.string.note_optional)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            val product = selected ?: return@Button
                            val qty = parsedQuantity ?: return@Button
                            val amount = parsedCost ?: return@Button
                            viewModel.recordStockDelivery(
                                operationId,
                                supplier.trim().ifBlank { null },
                                supplierPhone.trim().ifBlank { null },
                                listOf(DeliveryLineInput(product.id, qty, amount)),
                                note.trim().ifBlank { null },
                            )
                        },
                        enabled = !working && selected != null && parsedQuantity != null && parsedQuantity > 0 && parsedCost != null && parsedCost >= 0,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                    ) { Text(stringResource(if (working) R.string.saving else R.string.save_delivery)) }
                }
            }
        }
        item { Text(stringResource(R.string.recent_deliveries), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (deliveries.isEmpty()) {
            item { Text(stringResource(R.string.no_deliveries), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(deliveries, key = { it.id }) { delivery ->
                Card(shape = RoundedCornerShape(14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(delivery.supplierNameSnapshot ?: stringResource(R.string.walk_in_supplier), fontWeight = FontWeight.SemiBold)
                            Text(Instant.ofEpochMilli(delivery.deliveredAtMillis).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MMM d, h:mm a")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(PosViewModel.formatPeso(delivery.totalCostCentavos), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpensesTool(viewModel: PosViewModel) {
    val expenses by viewModel.expenses.collectAsStateWithLifecycle()
    val working by viewModel.isWorking.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var paymentSource by rememberSaveable { mutableStateOf("CASH") }
    var operationId by rememberSaveable { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    val parsed = PosViewModel.parseCentavos(amount)
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Card(shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(stringResource(R.string.expense_form_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(amount, { amount = it; operationId = java.util.UUID.randomUUID().toString() }, label = { Text(stringResource(R.string.expense_amount)) }, prefix = { Text("₱ ") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(category, { category = it; operationId = java.util.UUID.randomUUID().toString() }, label = { Text(stringResource(R.string.expense_category)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(description, { description = it; operationId = java.util.UUID.randomUUID().toString() }, label = { Text(stringResource(R.string.note_optional)) }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("CASH", "GCASH", "MAYA").forEach { method ->
                            val label = when (method) {
                                "CASH" -> stringResource(R.string.method_cash)
                                "GCASH" -> stringResource(R.string.method_gcash)
                                else -> stringResource(R.string.method_maya)
                            }
                            FilterChip(selected = paymentSource == method, onClick = { paymentSource = method; operationId = java.util.UUID.randomUUID().toString() }, label = { Text(label) })
                        }
                    }
                    Button(
                        onClick = { parsed?.let { viewModel.recordExpense(operationId, it, category, description, paymentSource) } },
                        enabled = !working && parsed != null && parsed > 0 && category.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) { Text(stringResource(if (working) R.string.saving else R.string.save_expense)) }
                }
            }
        }
        item { Text(stringResource(R.string.recent_expenses), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (expenses.isEmpty()) item { Text(stringResource(R.string.no_expenses), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        else items(expenses, key = { it.id }) { expense ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(expense.category, fontWeight = FontWeight.SemiBold)
                        Text(expense.description ?: expense.paymentSource, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(PosViewModel.formatPeso(expense.amountCentavos), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun RefundDialog(
    saleLabel: String,
    items: List<SaleItemEntity>,
    hasCustomer: Boolean,
    isWorking: Boolean,
    onDismiss: () -> Unit,
    onRefund: (String, List<RefundLineInput>, RefundMethod, String) -> Unit,
) {
    var reason by rememberSaveable(saleLabel) { mutableStateOf("") }
    var restock by rememberSaveable(saleLabel) { mutableStateOf(true) }
    var operationId by rememberSaveable(saleLabel) { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    var refundMethod by rememberSaveable(saleLabel) { mutableStateOf(RefundMethod.CASH.name) }
    var methodMenu by remember { mutableStateOf(false) }
    var selectedIds by rememberSaveable(saleLabel) { mutableStateOf(emptyList<String>()) }
    var quantityTexts by rememberSaveable(
        saleLabel,
        stateSaver = androidx.compose.runtime.saveable.listSaver(
            save = { values: Map<String, String> -> values.flatMap { (id, quantity) -> listOf(id, quantity) } },
            restore = { values: List<String> -> values.chunked(2).mapNotNull { pair -> pair.takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap() },
        ),
    ) { mutableStateOf(items.associate { it.id to "" }) }
    val refundLines = items.mapNotNull { item ->
        if (item.id !in selectedIds) null
        else PosViewModel.parseQuantityMilliUnits(quantityTexts[item.id].orEmpty())
            ?.takeIf { it > 0 && it <= item.quantityMilliUnits }
            ?.let { RefundLineInput(item.id, it, restock) }
    }
    val allSelectedLinesValid = refundLines.size == selectedIds.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.refund_sale_title, saleLabel)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.refund_partial_explainer))
                items.forEach { item ->
                    val selected = item.id in selectedIds
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { checked ->
                                selectedIds = if (checked) selectedIds + item.id else selectedIds - item.id
                                if (checked && quantityTexts[item.id].isNullOrBlank()) {
                                    quantityTexts = quantityTexts + (item.id to PosViewModel.formatQuantity(item.quantityMilliUnits))
                                }
                                if (!checked) quantityTexts = quantityTexts + (item.id to "")
                            },
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.productNameSnapshot, fontWeight = FontWeight.SemiBold)
                            Text("${PosViewModel.formatQuantity(item.quantityMilliUnits)} ${item.unitLabelSnapshot} · ${PosViewModel.formatPeso(item.lineTotalCentavos)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (selected) OutlinedTextField(
                                value = quantityTexts[item.id].orEmpty(),
                                onValueChange = { value -> quantityTexts = quantityTexts + (item.id to value) },
                                label = { Text(stringResource(R.string.return_quantity)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                        }
                    }
                }
                OutlinedTextField(reason, { reason = it }, label = { Text(stringResource(R.string.refund_reason)) }, minLines = 2)
                Box {
                    androidx.compose.material3.OutlinedButton(onClick = { methodMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        val label = when (refundMethod) {
                            RefundMethod.CASH.name -> stringResource(R.string.method_cash)
                            RefundMethod.GCASH.name -> stringResource(R.string.method_gcash)
                            RefundMethod.MAYA.name -> stringResource(R.string.method_maya)
                            else -> stringResource(R.string.refund_as_credit)
                        }
                        Text("${stringResource(R.string.refund_payment_method)} · $label")
                    }
                    DropdownMenu(expanded = methodMenu, onDismissRequest = { methodMenu = false }) {
                        RefundMethod.entries.filter { hasCustomer || it != RefundMethod.STORE_CREDIT }.forEach { method ->
                            val label = when (method) {
                                RefundMethod.CASH -> stringResource(R.string.method_cash)
                                RefundMethod.GCASH -> stringResource(R.string.method_gcash)
                                RefundMethod.MAYA -> stringResource(R.string.method_maya)
                                RefundMethod.STORE_CREDIT -> stringResource(R.string.refund_as_credit)
                            }
                            DropdownMenuItem(text = { Text(label) }, onClick = { refundMethod = method.name; methodMenu = false })
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = restock, onCheckedChange = { restock = it })
                    Text(stringResource(R.string.return_to_stock))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { RefundMethod.entries.firstOrNull { it.name == refundMethod }?.let { onRefund(reason.trim(), refundLines, it, operationId) } },
                enabled = reason.isNotBlank() && selectedIds.isNotEmpty() && allSelectedLinesValid && !isWorking,
            ) {
                Text(stringResource(if (isWorking) R.string.saving else R.string.refund_sale))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ReportsTool(viewModel: PosViewModel) {
    SalesReportsScreen(viewModel)
}
