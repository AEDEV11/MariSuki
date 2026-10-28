package com.aesprt.marisuki.feature.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.aesprt.marisuki.ui.components.ToolSectionHeader
import com.aesprt.marisuki.ui.components.ToolIcon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aesprt.marisuki.R
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.ui.theme.PosLayout
import com.aesprt.marisuki.ui.theme.PosSpacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun StaffManagementScreen(viewModel: StaffAdminViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected = state.accounts.firstOrNull { it.id == state.selectedId }
    var displayName by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var permissionNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val permissions = remember(permissionNames) {
        permissionNames.mapNotNull { name -> PosPermission.entries.firstOrNull { it.name == name } }.toSet()
    }
    var mobileEditor by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(selected?.id, state.selectedId) {
        displayName = selected?.displayName.orEmpty()
        username = selected?.username.orEmpty()
        pin = ""
        confirmPin = ""
        permissionNames = state.permissions.map(PosPermission::name)
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        if (wide) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = PosLayout.tabletScreenPadding, vertical = PosSpacing.screen),
                horizontalArrangement = Arrangement.spacedBy(PosLayout.tabletPaneGap),
            ) {
                StaffListPane(state, selected?.id, viewModel, Modifier.width(320.dp), onAdd = { viewModel.addCashier() })
                StaffDetailsPane(
                    state, selected, displayName, { displayName = it }, username, { username = it },
                    pin, { pin = it.filter(Char::isDigit).take(6) }, confirmPin,
                    { confirmPin = it.filter(Char::isDigit).take(6) }, permissions,
                    { permissionNames = it.map(PosPermission::name) }, viewModel,
                    Modifier.weight(1f).fillMaxSize(),
                )
            }
        } else if (mobileEditor) {
            StaffDetailsPane(
                state, selected, displayName, { displayName = it }, username, { username = it },
                pin, { pin = it.filter(Char::isDigit).take(6) }, confirmPin,
                { confirmPin = it.filter(Char::isDigit).take(6) }, permissions,
                { permissionNames = it.map(PosPermission::name) }, viewModel,
                Modifier.fillMaxSize(),
                onBack = { mobileEditor = false },
            )
        } else {
            StaffListPane(
                state, selected?.id, viewModel, Modifier.fillMaxSize().padding(vertical = PosSpacing.sm),
                onAdd = { viewModel.addCashier(); mobileEditor = true },
                onSelect = { id -> viewModel.select(id); mobileEditor = true },
            )
        }
    }
}

@Composable
private fun StaffListPane(
    state: StaffAdminState,
    selectedId: String?,
    viewModel: StaffAdminViewModel,
    modifier: Modifier,
    onAdd: () -> Unit,
    onSelect: (String) -> Unit = viewModel::select,
) {
    val cashiers = state.accounts.filter { it.role.name == "CASHIER" }
    LazyColumn(modifier, contentPadding = PaddingValues(bottom = PosSpacing.section), verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                Text(stringResource(R.string.staff_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth().heightIn(min = PosLayout.minimumTouchTarget),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.staff_add_cashier), fontWeight = FontWeight.SemiBold)
                }
                if (cashiers.isEmpty()) Text(stringResource(R.string.staff_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(cashiers, key = { it.id }) { account ->
            StaffAccountRow(account, selected = account.id == selectedId) { onSelect(account.id) }
        }
        state.error?.let { error -> item { Text(error, color = MaterialTheme.colorScheme.error) } }
    }
}

@Composable
internal fun StaffAccountRow(account: com.aesprt.marisuki.domain.auth.AccountSummary, selected: Boolean, onSelect: () -> Unit) {
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth().heightIn(min = PosLayout.minimumTouchTarget),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ToolIcon(Icons.Default.Person)
                Text(account.displayName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("@${account.username}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    stringResource(if (account.enabled) R.string.staff_enabled else R.string.staff_disabled),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (account.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun StaffDetailsPane(
    state: StaffAdminState,
    selected: com.aesprt.marisuki.domain.auth.AccountSummary?,
    displayName: String,
    onName: (String) -> Unit,
    username: String,
    onUsername: (String) -> Unit,
    pin: String,
    onPin: (String) -> Unit,
    confirmPin: String,
    onConfirmPin: (String) -> Unit,
    permissions: Set<PosPermission>,
    onPermissions: (Set<PosPermission>) -> Unit,
    viewModel: StaffAdminViewModel,
    modifier: Modifier,
    onBack: (() -> Unit)? = null,
) {
    Column(modifier.imePadding()) {
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                onBack?.let { OutlinedButton(onClick = it, shape = RoundedCornerShape(10.dp)) { Text(stringResource(R.string.staff_back)) } }
                ToolSectionHeader(stringResource(if (selected == null) R.string.staff_add_cashier else R.string.staff_cashier_account), Icons.Default.Person)
                OutlinedTextField(
                    displayName, onName, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.staff_full_name)) }, singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    username, onUsername, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.staff_username)) }, singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    pin, onPin, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(if (selected == null) R.string.staff_login_pin else R.string.staff_new_login_pin)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                OutlinedTextField(
                    confirmPin, onConfirmPin, Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.staff_confirm_pin)) },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (selected != null) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (selected.enabled) R.string.staff_account_enabled else R.string.staff_account_disabled), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                        Switch(checked = selected.enabled, onCheckedChange = { viewModel.setEnabled(selected.id, it) }, enabled = !state.busy)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ToolSectionHeader(stringResource(R.string.staff_permissions), Icons.Default.Lock)
                PosPermission.entries.filterNot(PosPermission::ownerOnly).groupBy { it.group }.forEach { (group, items) ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(group.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            items.forEach { permission ->
                                val checked = permission in permissions
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = PosLayout.minimumTouchTarget).toggleable(
                                        value = checked,
                                        role = Role.Checkbox,
                                        onValueChange = { enabled -> onPermissions(if (enabled) permissions + permission else permissions - permission) },
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(permission.name.lowercase().split('_').joinToString(" ").replaceFirstChar(Char::uppercase), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = null,
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                }
                }
                if (selected != null) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(R.string.staff_recent_activity), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (state.activity.isEmpty()) Text(stringResource(R.string.staff_no_activity), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    state.activity.take(8).forEach { event -> Text("${event.operation.replace('_', ' ')} · ${event.details}", style = MaterialTheme.typography.bodySmall) }
                    Text(stringResource(R.string.staff_approvals_count, state.restrictedActions.count { it.requesterAccountId == selected.id }), style = MaterialTheme.typography.bodySmall)
                }
                if (state.saved) Text(stringResource(R.string.staff_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Button(
            onClick = { viewModel.save(displayName, username, pin, confirmPin, permissions) },
            enabled = !state.busy && displayName.isNotBlank() && username.isNotBlank() && (selected != null || pin.length == 6) && (pin.isEmpty() || pin == confirmPin),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp).heightIn(min = 50.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (state.busy) CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(4.dp))
            else Text(stringResource(if (selected == null) R.string.staff_create_cashier else R.string.staff_save_changes), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }
    }
}
