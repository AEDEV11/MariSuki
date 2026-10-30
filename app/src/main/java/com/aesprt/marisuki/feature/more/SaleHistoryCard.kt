package com.aesprt.marisuki.feature.more

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aesprt.marisuki.R
import com.aesprt.marisuki.data.local.dao.SaleSummary
import com.aesprt.marisuki.data.local.dao.SaleHistoryDetails
import com.aesprt.marisuki.feature.pos.PosViewModel
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.ui.theme.PosRadius
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SaleHistoryCard(
    sale: SaleSummary,
    viewModel: PosViewModel,
    working: Boolean,
    onPrint: () -> Unit,
    onRefund: () -> Unit,
) {
    var expanded by rememberSaveable(sale.id) { mutableStateOf(false) }
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val container = if (sale.hasRefund) colors.errorContainer.copy(alpha = 0.35f) else colors.surface
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = PosRadius.card,
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, if (sale.hasRefund) colors.error.copy(alpha = 0.25f) else colors.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = if (sale.hasRefund) colors.errorContainer else colors.secondaryContainer,
                    contentColor = if (sale.hasRefund) colors.onErrorContainer else colors.onSecondaryContainer,
                    shape = PosRadius.badge,
                ) {
                    Text(
                        stringResource(when {
                            sale.fullyRefunded -> R.string.history_refunded
                            sale.hasRefund -> R.string.history_partial_refund
                            else -> R.string.history_completed
                        }),
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(historyDate(sale.createdAtMillis), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Text(sale.customerName ?: stringResource(R.string.walk_in_customer), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.history_receipt, sale.id.take(8).uppercase()), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            HistoryValue(stringResource(R.string.history_sale_total), PosViewModel.formatPeso(sale.totalCentavos), prominent = true)
            if (sale.hasRefund) {
                HistoryValue(stringResource(R.string.history_refund_total), PosViewModel.formatPeso(sale.refundedCentavos))
                HistoryValue(stringResource(R.string.history_net_sale), PosViewModel.formatPeso(sale.totalCentavos - sale.refundedCentavos))
            }
            if (expanded) SaleHistoryExpanded(sale.id, viewModel)
            HorizontalDivider(color = colors.outlineVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = { expanded = !expanded }, shape = PosRadius.button) {
                    Text(stringResource(if (expanded) R.string.history_hide_details else R.string.history_view_details))
                }
                if (PosPermission.REPRINT_SLIP in permissions) TextButton(onClick = onPrint, enabled = !working) { Text(stringResource(R.string.print_receipt)) }
                if (!sale.fullyRefunded) TextButton(onClick = onRefund, enabled = !working) { Text(stringResource(R.string.refund_sale)) }
            }
        }
    }
}

@Composable
private fun SaleHistoryExpanded(saleId: String, viewModel: PosViewModel) {
    var failed by remember(saleId) { mutableStateOf(false) }
    var attempt by remember(saleId) { mutableIntStateOf(0) }
    val source = remember(saleId, attempt) {
        viewModel.observeSaleHistoryDetails(saleId).catch { error ->
            if (error is CancellationException) throw error
            failed = true
            emit(null)
        }
    }
    val details by source.collectAsStateWithLifecycle(initialValue = null)
    if (failed) {
        Text(stringResource(R.string.history_load_failed), color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { failed = false; attempt++ }) { Text(stringResource(R.string.history_retry)) }
    } else {
        val history = details
        if (history == null) Text(stringResource(R.string.loading_sale_items))
        else SaleHistoryContents(history, viewModel)
    }
}

@Composable
private fun SaleHistoryContents(history: SaleHistoryDetails, viewModel: PosViewModel) {
    val sale = history.sale
    var cashierName by remember(sale.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(sale.cashierAccountId) {
        cashierName = sale.cashierAccountId?.let {
            try { viewModel.saleCashierName(it) } catch (error: Exception) {
                if (error is CancellationException) throw error
                null
            }
        }
    }
    HorizontalDivider()
    HistoryValue(stringResource(R.string.sale_channel_label), stringResource(saleChannelLabel(sale.salesChannel)))
    HistoryValue(stringResource(R.string.history_cashier), cashierName ?: stringResource(R.string.history_unavailable))
    Text(stringResource(R.string.history_items), style = MaterialTheme.typography.titleSmall)
    history.items.forEach { item ->
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            HistoryValue(item.productNameSnapshot, PosViewModel.formatPeso(item.lineTotalCentavos))
            Text(
                stringResource(R.string.history_item_price, PosViewModel.formatQuantity(item.quantityMilliUnits), item.unitLabelSnapshot, PosViewModel.formatPeso(item.unitPriceCentavos)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    HorizontalDivider()
    Text(stringResource(R.string.history_payments), style = MaterialTheme.typography.titleSmall)
    history.payments.forEach { payment ->
        HistoryValue(historyMethod(payment.method), PosViewModel.formatPeso(payment.amountAppliedCentavos))
        payment.referenceNumber?.let { HistoryValue(stringResource(R.string.reference_number), it) }
        payment.tenderedCentavos?.let { HistoryValue(stringResource(R.string.history_tendered), PosViewModel.formatPeso(it)) }
        if (payment.changeCentavos > 0) HistoryValue(stringResource(R.string.change_due), PosViewModel.formatPeso(payment.changeCentavos))
    }
    val originalCredit = (sale.totalCentavos - history.payments.sumOf { it.amountAppliedCentavos }).coerceAtLeast(0)
    if (originalCredit > 0) HistoryValue(stringResource(R.string.history_original_credit), PosViewModel.formatPeso(originalCredit))
    history.refunds.sortedByDescending { it.refund.createdAtMillis }.forEach { entry ->
        HorizontalDivider()
        Text(stringResource(R.string.history_refund_entry, historyDate(entry.refund.createdAtMillis)), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        HistoryValue(stringResource(R.string.history_refund_total), PosViewModel.formatPeso(entry.refund.refundedCentavos))
        HistoryValue(stringResource(R.string.refund_payment_method), historyMethod(entry.refund.refundMethod))
        Text(stringResource(R.string.refund_reason), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(entry.refund.reason, style = MaterialTheme.typography.bodyMedium)
        entry.items.forEach { returned ->
            val original = history.items.firstOrNull { it.id == returned.saleItemId }
            HistoryValue(original?.productNameSnapshot ?: returned.saleItemId, PosViewModel.formatPeso(returned.amountCentavos))
            Text(
                stringResource(R.string.history_returned_item, PosViewModel.formatQuantity(returned.quantityMilliUnits), original?.unitLabelSnapshot.orEmpty(), stringResource(if (returned.restock) R.string.history_restocked else R.string.history_not_restocked)),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(stringResource(R.string.history_refund_id, entry.refund.id), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text(stringResource(R.string.history_sale_id, sale.id), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HistoryValue(label: String, value: String, prominent: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End,
            style = (if (prominent) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium).copy(fontFeatureSettings = "tnum"),
            fontWeight = if (prominent) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun historyMethod(method: String): String = when (method) {
    "CASH" -> stringResource(R.string.method_cash)
    "GCASH" -> stringResource(R.string.method_gcash)
    "MAYA" -> stringResource(R.string.method_maya)
    "STORE_CREDIT" -> stringResource(R.string.refund_as_credit)
    else -> method
}

private fun historyDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
    .format(DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a", Locale.getDefault()))

/** Maps a stored sale channel to its localized label; unknown values fall back to tingi. */
internal fun saleChannelLabel(channel: String): Int = when (channel) {
    "WHOLESALE" -> R.string.sale_channel_wholesale
    "MIXED" -> R.string.sale_channel_mixed
    else -> R.string.sale_channel_tingi
}
