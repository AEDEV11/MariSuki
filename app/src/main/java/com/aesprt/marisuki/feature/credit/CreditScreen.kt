package com.aesprt.marisuki.feature.credit

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aesprt.marisuki.R
import com.aesprt.marisuki.data.local.dao.CustomerBalance
import com.aesprt.marisuki.domain.model.PaymentMethod
import com.aesprt.marisuki.feature.pos.PosViewModel
import com.aesprt.marisuki.data.local.entity.CustomerLedgerEntryEntity
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.ui.theme.PosLayout
import com.aesprt.marisuki.ui.theme.PosSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.runtime.remember

@Composable
fun CreditScreen(viewModel: PosViewModel) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var payTarget by rememberSaveable { mutableStateOf<String?>(null) }
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
            Column(Modifier.fillMaxSize().padding(if (expanded) PosLayout.tabletScreenPadding else PosLayout.compactScreenPadding)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(stringResource(R.string.credit_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        val totalDebt = customers.sumOf { it.balanceCentavos.coerceAtLeast(0) }
                        Text("${stringResource(R.string.outstanding_total)}  ${PosViewModel.formatPeso(totalDebt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val storeCredit = customers.sumOf { (-it.balanceCentavos).coerceAtLeast(0) }
                        if (storeCredit > 0) Text("${stringResource(R.string.store_credit_total)}  ${PosViewModel.formatPeso(storeCredit)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (canAddCustomer) Button(
                        onClick = { showAdd = true },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.heightIn(min = PosLayout.minimumTouchTarget),
                    ) {
                        androidx.compose.material3.Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.add_customer), modifier = Modifier.padding(start = PosSpacing.xs))
                    }
                }
                Spacer(Modifier.height(PosSpacing.sm))
                if (customers.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_customers)) }
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
                            CustomerRow(customer, showPayment = canAcceptPayments, onPayment = { payTarget = customer.id })
                        }
                    }
                }
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
private fun CustomerRow(
    customer: CustomerBalance,
    selected: Boolean = false,
    showPayment: Boolean = true,
    onSelect: (() -> Unit)? = null,
    onPayment: () -> Unit = {},
) {
    Card(
        modifier = if (onSelect == null) Modifier else Modifier.clickable(onClick = onSelect),
        shape = MaterialTheme.shapes.medium,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = PosSpacing.section, vertical = PosSpacing.sm)) {
            val paymentDue = showPayment && customer.balanceCentavos > 0
            val button: @Composable () -> Unit = {
                if (paymentDue) Button(
                    onClick = onPayment,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.heightIn(min = PosLayout.minimumTouchTarget),
                ) { Text(stringResource(R.string.record_payment)) }
            }
            val identity: @Composable (Modifier) -> Unit = { modifier ->
                Column(modifier, verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                    Text(customer.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    customer.phone?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            val balance: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.End) {
                    val shown = if (customer.balanceCentavos < 0) -customer.balanceCentavos else customer.balanceCentavos
                    Text(PosViewModel.formatPeso(shown), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(
                        when {
                            customer.balanceCentavos > 0 -> stringResource(R.string.outstanding_balance)
                            customer.balanceCentavos < 0 -> stringResource(R.string.store_credit_balance)
                            else -> stringResource(R.string.no_outstanding_balance)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (maxWidth < 420.dp && paymentDue) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                        identity(Modifier.weight(1f))
                        balance()
                    }
                    Box(Modifier.align(Alignment.End)) { button() }
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    identity(Modifier.weight(1f))
                    balance()
                    button()
                }
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
    Card(modifier, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxSize().padding(PosSpacing.section), verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
            Text(customer.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            customer.phone?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            val balance = customer.balanceCentavos
            Text(
                PosViewModel.formatPeso(kotlin.math.abs(balance)),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (balance > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(if (balance > 0) R.string.outstanding_balance else if (balance < 0) R.string.store_credit_balance else R.string.no_outstanding_balance),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showPayment && balance > 0) Button(onClick = onPayment, modifier = Modifier.heightIn(min = PosLayout.minimumTouchTarget)) {
                Text(stringResource(R.string.record_payment))
            }
            HorizontalDivider()
            Text(stringResource(R.string.utang_history_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (entries.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                        androidx.compose.material3.Icon(
                            Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.padding(bottom = PosSpacing.xs),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(stringResource(R.string.utang_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                    items(entries, key = { it.id }) { entry -> CustomerLedgerRow(entry) }
                }
            }
        }
    }
}

@Composable
private fun CustomerLedgerRow(entry: CustomerLedgerEntryEntity) {
    val date = remember(entry.occurredAtMillis) {
        DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.getDefault())
            .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(entry.occurredAtMillis))
    }
    Row(Modifier.fillMaxWidth().padding(vertical = PosSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
            val title = when (entry.sourceType) {
                "SALE" -> stringResource(R.string.utang_ledger_sale)
                "CUSTOMER_PAYMENT" -> stringResource(R.string.utang_ledger_payment)
                "REFUND" -> stringResource(R.string.utang_ledger_refund)
                else -> entry.sourceType.replace('_', ' ').lowercase().replaceFirstChar(Char::uppercase)
            }
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(entry.note?.takeIf(String::isNotBlank) ?: date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!entry.note.isNullOrBlank()) Text(date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            (when {
                entry.deltaCentavos > 0 -> "+"
                entry.deltaCentavos < 0 -> "−"
                else -> ""
            }) + PosViewModel.formatPeso(kotlin.math.abs(entry.deltaCentavos)),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (entry.deltaCentavos > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun AddCustomerDialog(onDismiss: () -> Unit, onSave: (String, String?) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_customer)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.customer_name)) }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.phone_optional)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(name.trim(), phone.trim().ifBlank { null }) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CustomerPaymentDialog(customer: CustomerBalance, onDismiss: () -> Unit, onSave: (String, PaymentMethod, String) -> Unit) {
    var amount by rememberSaveable(customer.id) { mutableStateOf(java.math.BigDecimal.valueOf(customer.balanceCentavos, 2).toPlainString()) }
    var method by rememberSaveable(customer.id) { mutableStateOf(PaymentMethod.CASH.name) }
    var idempotencyKey by rememberSaveable(customer.id) { mutableStateOf(java.util.UUID.randomUUID().toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_payment)) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${customer.name} · ${PosViewModel.formatPeso(customer.balanceCentavos)}")
                OutlinedTextField(amount, { amount = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.payment_amount)) }, prefix = { Text("₱ ") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PaymentMethod.entries.forEach { item ->
                        val label = when (item) {
                            PaymentMethod.CASH -> stringResource(R.string.method_cash)
                            PaymentMethod.GCASH -> stringResource(R.string.method_gcash)
                            PaymentMethod.MAYA -> stringResource(R.string.method_maya)
                        }
                        FilterChip(selected = method == item.name, onClick = { method = item.name }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { PaymentMethod.entries.firstOrNull { it.name == method }?.let { onSave(amount, it, idempotencyKey) } }, enabled = PosViewModel.parseCentavos(amount)?.let { it > 0 && it <= customer.balanceCentavos } == true) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
