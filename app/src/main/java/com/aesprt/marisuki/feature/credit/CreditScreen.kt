package com.aesprt.marisuki.feature.credit

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aesprt.marisuki.R
import com.aesprt.marisuki.data.local.dao.CustomerBalance
import com.aesprt.marisuki.data.local.entity.CustomerLedgerEntryEntity
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.domain.model.PaymentMethod
import com.aesprt.marisuki.feature.pos.PosViewModel
import com.aesprt.marisuki.ui.components.PosEmptyState
import com.aesprt.marisuki.ui.components.ToolChip
import com.aesprt.marisuki.ui.theme.PosLayout
import com.aesprt.marisuki.ui.theme.PosRadius
import com.aesprt.marisuki.ui.theme.PosSpacing

@Composable
fun CreditScreen(viewModel: PosViewModel) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var payTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var historyTarget by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedCustomerId by rememberSaveable { mutableStateOf<String?>(null) }
    val canAddCustomer = PosPermission.ADD_CUSTOMER in permissions
    val canAcceptPayments = PosPermission.ACCEPT_UTANG_PAYMENTS in permissions
    val canViewBalances = PosPermission.VIEW_CUSTOMER_BALANCES in permissions

    LaunchedEffect(customers) {
        if (customers.none { it.id == selectedCustomerId }) selectedCustomerId = customers.firstOrNull()?.id
    }
    val selectedCustomer = customers.firstOrNull { it.id == selectedCustomerId }

    if (!canViewBalances) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.error_access_denied), style = MaterialTheme.typography.titleMedium)
        }
    } else {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val expanded = maxWidth >= 760.dp
            val totalDebt = customers.sumOf { it.balanceCentavos.coerceAtLeast(0) }
            val storeCredit = customers.sumOf { (-it.balanceCentavos).coerceAtLeast(0) }
            Column(Modifier.fillMaxSize().padding(if (expanded) PosLayout.tabletScreenPadding else PosLayout.compactScreenPadding)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.credit_title),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.credit_customer_count, customers.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (canAddCustomer) Button(
                        onClick = { showAdd = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.heightIn(min = PosLayout.minimumTouchTarget),
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.add_customer), modifier = Modifier.padding(start = PosSpacing.xs))
                    }
                }
                Spacer(Modifier.height(PosSpacing.sm))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosRadius.card,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                stringResource(R.string.outstanding_total),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                            )
                            Text(
                                PosViewModel.formatPeso(totalDebt),
                                style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            if (storeCredit > 0) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    stringResource(R.string.store_credit_total),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                                )
                                Text(
                                    PosViewModel.formatPeso(storeCredit),
                                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Surface(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .14f), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Filled.Payments, null, Modifier.padding(12.dp).size(26.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
                Spacer(Modifier.height(PosSpacing.sm))
                if (customers.isEmpty()) {
                    PosEmptyState(
                        icon = Icons.Filled.Person,
                        title = stringResource(R.string.no_customers),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                } else if (expanded) {
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosLayout.tabletPaneGap)) {
                        LazyColumn(
                            modifier = Modifier.weight(0.9f),
                            verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                            contentPadding = PaddingValues(bottom = PosSpacing.sm),
                        ) {
                            items(customers, key = { it.id }) { customer ->
                                CustomerRow(customer, selected = customer.id == selectedCustomer?.id, showPayment = false, onSelect = { selectedCustomerId = customer.id })
                            }
                        }
                        selectedCustomer?.let { customer ->
                            CustomerDetailsPane(
                                customer = customer,
                                viewModel = viewModel,
                                showPayment = canAcceptPayments,
                                onPayment = { payTarget = customer.id },
                                modifier = Modifier.weight(1.1f).fillMaxSize(),
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                        contentPadding = PaddingValues(bottom = PosSpacing.sm),
                    ) {
                        items(customers, key = { it.id }) { customer ->
                            CustomerRow(customer, showPayment = canAcceptPayments, onSelect = { historyTarget = customer.id }, onPayment = { payTarget = customer.id })
                        }
                    }
                }
            }
        }
    }

    if (canViewBalances) historyTarget?.let { id ->
        customers.firstOrNull { it.id == id }?.let { customer ->
            CreditDialogFrame(
                title = stringResource(R.string.utang_history_title), icon = Icons.AutoMirrored.Filled.ReceiptLong,
                onDismiss = { historyTarget = null }, scrollable = false,
                actions = { TextButton(onClick = { historyTarget = null }) { Text(stringResource(R.string.close)) } },
            ) {
                CustomerDetailsPane(customer, viewModel, canAcceptPayments, {
                    historyTarget = null
                    payTarget = customer.id
                }, Modifier.fillMaxSize())
            }
        }
    }
    if (showAdd && canAddCustomer) AddCustomerDialog(
        onDismiss = { showAdd = false },
        onSave = { name, phone -> viewModel.createCustomer(name, phone); showAdd = false },
    )
    payTarget?.let { customerId ->
        val customer = customers.firstOrNull { it.id == customerId }
        if (customer == null || customer.balanceCentavos <= 0 || !canAcceptPayments) payTarget = null
        else if (canAcceptPayments) CustomerPaymentDialog(
            customer = customer,
            onDismiss = { payTarget = null },
            onSave = { amount, method, key -> viewModel.recordUtangPayment(customer.id, amount, method, key); payTarget = null },
        )
    }
}

@Composable
internal fun CustomerRow(
    customer: CustomerBalance,
    selected: Boolean = false,
    showPayment: Boolean = true,
    onSelect: (() -> Unit)? = null,
    onPayment: () -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().then(if (onSelect != null) Modifier.clickable(onClick = onSelect) else Modifier),
        shape = PosRadius.card,
        colors = CardDefaults.cardColors(containerColor = if (selected) colors.primaryContainer.copy(alpha = 0.25f) else colors.surface),
        border = BorderStroke(1.dp, if (selected) colors.primary else colors.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(10.dp), color = colors.primaryContainer) {
                    Icon(Icons.Filled.Person, null, Modifier.padding(10.dp).size(20.dp), tint = colors.onPrimaryContainer)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(customer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(customer.phone ?: stringResource(R.string.credit_view_history), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (onSelect != null) Icon(Icons.Filled.ChevronRight, null, tint = colors.onSurfaceVariant)
            }
            Surface(
                color = if (customer.balanceCentavos > 0) colors.errorContainer.copy(alpha = .35f) else colors.surfaceContainerLow,
                shape = PosRadius.input,
                border = BorderStroke(1.dp, if (customer.balanceCentavos > 0) colors.error.copy(alpha = 0.2f) else colors.outlineVariant),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(if (customer.balanceCentavos > 0) R.string.outstanding_balance else if (customer.balanceCentavos < 0) R.string.store_credit_balance else R.string.no_outstanding_balance),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        PosViewModel.formatPeso(kotlin.math.abs(customer.balanceCentavos)),
                        style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
                        fontWeight = FontWeight.Bold,
                        color = if (customer.balanceCentavos > 0) colors.error else colors.primary,
                    )
                }
            }
            if (showPayment && customer.balanceCentavos > 0) Button(
                onClick = onPayment,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = PosRadius.button,
            ) {
                Icon(Icons.Filled.Payments, null, Modifier.size(18.dp))
                Text(stringResource(R.string.record_payment), Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun CustomerDetailsPane(
    customer: CustomerBalance,
    viewModel: PosViewModel,
    showPayment: Boolean,
    onPayment: () -> Unit,
    modifier: Modifier,
) {
    val ledgerFlow = remember(customer.id) { viewModel.observeCustomerLedger(customer.id) }
    val entries by ledgerFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    Card(
        modifier,
        shape = PosRadius.card,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(customer.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    customer.phone?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                }
            }

            Surface(
                shape = PosRadius.input,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(if (customer.balanceCentavos > 0) R.string.outstanding_balance else if (customer.balanceCentavos < 0) R.string.store_credit_balance else R.string.no_outstanding_balance),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            PosViewModel.formatPeso(kotlin.math.abs(customer.balanceCentavos)),
                            style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = if (customer.balanceCentavos > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (showPayment && customer.balanceCentavos > 0) {
                        Button(
                            onClick = onPayment,
                            shape = PosRadius.button,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text(stringResource(R.string.record_payment), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text(stringResource(R.string.utang_history_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (entries.isEmpty()) {
                PosEmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = stringResource(R.string.utang_history_empty),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(entries, key = { it.id }) { entry -> CustomerLedgerRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun CustomerLedgerRow(entry: CustomerLedgerEntryEntity) {
    val date = remember(entry.occurredAtMillis) {
        DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.occurredAtMillis))
    }
    Surface(
        shape = PosRadius.input,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val title = when (entry.sourceType) {
                    "SALE" -> stringResource(R.string.utang_ledger_sale)
                    "CUSTOMER_PAYMENT" -> stringResource(R.string.utang_ledger_payment)
                    "REFUND" -> stringResource(R.string.utang_ledger_refund)
                    else -> entry.sourceType.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
                }
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(entry.note?.takeIf(String::isNotBlank) ?: date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!entry.note.isNullOrBlank()) Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                (when {
                    entry.deltaCentavos > 0 -> "+"
                    entry.deltaCentavos < 0 -> "−"
                    else -> ""
                }) + PosViewModel.formatPeso(kotlin.math.abs(entry.deltaCentavos)),
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.Bold,
                color = if (entry.deltaCentavos > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun AddCustomerDialog(onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    CreditDialogFrame(title = stringResource(R.string.add_customer), icon = Icons.Filled.PersonAdd, onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            Button(onClick = { onSave(name.trim(), phone.trim().ifBlank { null }) }, enabled = name.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp), shape = PosRadius.button) { Text(stringResource(R.string.save)) }
        }) {
        Text(stringResource(R.string.credit_customer_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.customer_name)) },
            leadingIcon = { Icon(Icons.Filled.Person, null) }, singleLine = true, shape = PosRadius.input)
        OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.phone_optional)) },
            leadingIcon = { Icon(Icons.Filled.Phone, null) }, singleLine = true, shape = PosRadius.input,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomerPaymentDialog(customer: CustomerBalance, onDismiss: () -> Unit, onSave: (String, PaymentMethod, String) -> Unit) {
    var amount by rememberSaveable(customer.id) { mutableStateOf(java.math.BigDecimal.valueOf(customer.balanceCentavos, 2).toPlainString()) }
    var method by rememberSaveable(customer.id) { mutableStateOf(PaymentMethod.CASH.name) }
    val idempotencyKey by rememberSaveable(customer.id) { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    val parsed = PosViewModel.parseCentavos(amount)
    val valid = parsed != null && parsed > 0 && parsed <= customer.balanceCentavos
    CreditDialogFrame(title = stringResource(R.string.record_payment), icon = Icons.Filled.Payments, onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            Button(onClick = { PaymentMethod.entries.firstOrNull { it.name == method }?.let { onSave(amount, it, idempotencyKey) } },
                enabled = valid, modifier = Modifier.heightIn(min = 48.dp), shape = PosRadius.button) {
                Text(stringResource(R.string.save))
            }
        }) {
        Surface(
            shape = PosRadius.card,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(customer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.outstanding_balance), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(PosViewModel.formatPeso(customer.balanceCentavos), style = MaterialTheme.typography.headlineSmall.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            }
        }
        OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.payment_amount)) },
            prefix = { Text("₱ ") }, singleLine = true, shape = PosRadius.input,
            isError = parsed != null && parsed > customer.balanceCentavos,
            supportingText = if (parsed != null && parsed > customer.balanceCentavos) { { Text(stringResource(R.string.credit_payment_too_large)) } } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        Text(stringResource(R.string.payment_method), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PaymentMethod.entries.forEach { item ->
                val selected = method == item.name
                ToolChip(
                    selected = selected,
                    onClick = { method = item.name },
                    label = {
                        Text(
                            stringResource(
                                when (item) {
                                    PaymentMethod.CASH -> R.string.method_cash
                                    PaymentMethod.GCASH -> R.string.method_gcash
                                    PaymentMethod.MAYA -> R.string.method_maya
                                },
                            ),
                        )
                    },
                )
            }
        }
        if (valid) Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = PosRadius.input) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.credit_remaining_after_payment), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(PosViewModel.formatPeso(customer.balanceCentavos - parsed), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
