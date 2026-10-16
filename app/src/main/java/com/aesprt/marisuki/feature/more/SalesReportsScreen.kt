package com.aesprt.marisuki.feature.more

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aesprt.marisuki.R
import com.aesprt.marisuki.data.PosRepository
import com.aesprt.marisuki.data.SalesReport
import com.aesprt.marisuki.feature.pos.PosViewModel
import com.aesprt.marisuki.ui.theme.PosLayout
import com.aesprt.marisuki.ui.theme.PosSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.Locale
import com.aesprt.marisuki.domain.auth.PosPermission

private enum class ReportPeriod { TODAY, YESTERDAY, WEEK, MONTH, CUSTOM }
private enum class DateField { FROM, UNTIL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesReportsScreen(viewModel: PosViewModel) {
    val state by viewModel.salesReport.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var period by rememberSaveable { mutableStateOf(ReportPeriod.TODAY) }
    var customFromUtc by rememberSaveable { mutableStateOf(todayUtcMillis()) }
    var customUntilUtc by rememberSaveable { mutableStateOf(todayUtcMillis()) }
    var dateField by rememberSaveable { mutableStateOf<DateField?>(null) }
    var reportToExport by remember { mutableStateOf<SalesReport?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val report = reportToExport
        if (uri != null && report != null) viewModel.exportSalesReport(uri, context.contentResolver, report.fromMillis, report.untilMillis)
        reportToExport = null
    }

    val today = LocalDate.now(ZoneId.systemDefault())
    val (fromDate, untilDate) = when (period) {
        ReportPeriod.TODAY -> today to today
        ReportPeriod.YESTERDAY -> today.minusDays(1) to today.minusDays(1)
        ReportPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) to today
        ReportPeriod.MONTH -> today.withDayOfMonth(1) to today
        ReportPeriod.CUSTOM -> dateFromUtc(customFromUtc) to dateFromUtc(customUntilUtc)
    }
    val zone = ZoneId.systemDefault()
    val fromMillis = fromDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val untilMillis = untilDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    LaunchedEffect(fromMillis, untilMillis) { viewModel.loadSalesReport(fromMillis, untilMillis) }

    val periods = listOf(
        ReportPeriod.TODAY to R.string.period_today,
        ReportPeriod.YESTERDAY to R.string.period_yesterday,
        ReportPeriod.WEEK to R.string.period_this_week,
        ReportPeriod.MONTH to R.string.period_this_month,
        ReportPeriod.CUSTOM to R.string.period_custom,
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
      LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 250.dp),
        modifier = Modifier.widthIn(max = 1280.dp).fillMaxWidth().fillMaxHeight()
            .padding(horizontal = PosLayout.compactScreenPadding),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        contentPadding = PaddingValues(bottom = PosSpacing.major),
      ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.report_period_label), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    periods.forEach { (value, label) ->
                        FilterChip(selected = period == value, onClick = { period = value }, label = { Text(stringResource(label)) })
                    }
                }
                if (period == ReportPeriod.CUSTOM) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { dateField = DateField.FROM }) {
                            Text("${stringResource(R.string.range_from)} · ${dateFromUtc(customFromUtc).format(reportDateFormatter())}")
                        }
                        TextButton(onClick = { dateField = DateField.UNTIL }) {
                            Text("${stringResource(R.string.range_until)} · ${dateFromUtc(customUntilUtc).format(reportDateFormatter())}")
                        }
                    }
                }
            }
        }
        if (state.isLoading) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.report_loading), Modifier.padding(vertical = 20.dp)) }
        }
        state.error?.let { error ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    stringResource(if (error.name == "ACCESS_DENIED") R.string.report_access_denied else R.string.error_general),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 20.dp),
                )
            }
        }
        state.report?.let { report ->
            if (report.salesVisible) {
                item { MetricCard(stringResource(R.string.report_net_sales), PosViewModel.formatPeso(report.netSalesCentavos), "${dateLabel(fromDate)} – ${dateLabel(untilDate)}", highlight = true) }
                item { MetricCard(stringResource(R.string.report_sale_count), report.saleCount.toString(), stringResource(R.string.report_period_label)) }
                item { MetricCard(stringResource(R.string.report_gross_sales), PosViewModel.formatPeso(report.grossSalesCentavos), stringResource(R.string.total_sales_description)) }
                item { MetricCard(stringResource(R.string.report_refunds), PosViewModel.formatPeso(report.refundsCentavos), stringResource(R.string.report_period_label)) }
                report.grossProfitCentavos?.let { value -> item { MetricCard(stringResource(R.string.report_gross_profit), PosViewModel.formatPeso(value), stringResource(R.string.gross_profit_description)) } }
            }
            report.expensesCentavos?.let { value -> item { MetricCard(stringResource(R.string.store_expenses), PosViewModel.formatPeso(value), stringResource(R.string.expense_report_description)) } }
            if (report.grossProfitCentavos != null && report.expensesCentavos != null) {
                item { MetricCard(stringResource(R.string.report_net_estimate), PosViewModel.formatPeso(report.grossProfitCentavos - report.expensesCentavos), stringResource(R.string.net_report_description)) }
            }
            report.outstandingUtangCentavos?.let { item { MetricCard(stringResource(R.string.report_outstanding_utang), PosViewModel.formatPeso(it), stringResource(R.string.credit_not_cash)) } }
            report.inventoryValuationCentavos?.let { item { MetricCard(stringResource(R.string.report_inventory_value), PosViewModel.formatPeso(it), stringResource(R.string.report_inventory_value)) } }
            if (report.salesVisible) {
                item(span = { GridItemSpan(if (maxLineSpan >= 3) maxLineSpan - 1 else maxLineSpan) }) { DailySalesCard(report) }
                item { AmountListCard(stringResource(R.string.report_payment_breakdown), report.payments) }
                item { ChannelListCard(report) }
                item { ProductListCard(report) }
            }
            if (report.expenseCategories.isNotEmpty()) item { AmountListCard(stringResource(R.string.store_expenses), report.expenseCategories) }
            if (report.supplierPurchases.isNotEmpty()) item { AmountListCard(stringResource(R.string.report_supplier_purchases), report.supplierPurchases) }
            report.lowStockProducts?.let { rows -> item(span = { GridItemSpan(maxLineSpan) }) { LowStockCard(rows.size, rows.map { "${it.productName} · ${PosViewModel.formatQuantity(it.onHandMilliUnits)} / ${PosViewModel.formatQuantity(it.minimumStockMilliUnits)}" }) } }
            if (PosPermission.EXPORT_REPORTS in permissions) item(span = { GridItemSpan(maxLineSpan) }) {
                Button(onClick = { reportToExport = report; exportLauncher.launch("marisuki-report-${fromDate}.csv") }) {
                    Text(stringResource(R.string.report_export))
                }
            }
        }
        if (state.report?.let { it.salesVisible && it.saleCount == 0 } == true && !state.isLoading && state.error == null) {
            item(span = { GridItemSpan(maxLineSpan) }) { Text(stringResource(R.string.report_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
      }
    }

    dateField?.let { field ->
        val selected = if (field == DateField.FROM) customFromUtc else customUntilUtc
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = selected)
        DatePickerDialog(
            onDismissRequest = { dateField = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { chosen ->
                        if (field == DateField.FROM) {
                            customFromUtc = chosen
                            if (chosen > customUntilUtc) customUntilUtc = chosen
                        } else {
                            customUntilUtc = chosen
                            if (chosen < customFromUtc) customFromUtc = chosen
                        }
                    }
                    dateField = null
                    period = ReportPeriod.CUSTOM
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { dateField = null }) { Text(stringResource(R.string.cancel)) } },
        ) {
            DatePicker(state = pickerState, title = { Text(stringResource(R.string.report_choose_date), Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String, highlight: Boolean = false) {
    Card(shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = if (highlight) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(PosSpacing.section), verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DailySalesCard(report: SalesReport) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.report_daily_sales), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val values = report.dailySales
            if (values.isEmpty()) {
                Text(stringResource(R.string.report_no_rows), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val description = values.joinToString { "${it.day}: ${PosViewModel.formatPeso(it.salesCentavos)}" }
                val barColor = MaterialTheme.colorScheme.primary
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                Canvas(Modifier.fillMaxWidth().height(176.dp).semantics { contentDescription = description }) {
                    val maxValue = values.maxOf { it.salesCentavos }.coerceAtLeast(1L).toDouble()
                    val cell = size.width / values.size
                    val barWidth = (cell * 0.58f).coerceAtLeast(2f)
                    for (fraction in listOf(0.25f, 0.5f, 0.75f)) {
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, size.height * fraction),
                            end = Offset(size.width, size.height * fraction),
                            strokeWidth = 1f,
                        )
                    }
                    values.forEachIndexed { index, row ->
                        val height = size.height * (row.salesCentavos.toDouble() / maxValue).toFloat()
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(index * cell + (cell - barWidth) / 2f, size.height - height),
                            size = Size(barWidth, height),
                            cornerRadius = CornerRadius(5f, 5f),
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(values.first().day, style = MaterialTheme.typography.labelSmall)
                    Text(values.last().day, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun AmountListCard(title: String, rows: List<com.aesprt.marisuki.data.local.dao.LabelAmount>) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (rows.isEmpty()) Text(stringResource(R.string.report_no_rows), color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(row.label.replace('_', ' '), Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(PosViewModel.formatPeso(row.amountCentavos), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ChannelListCard(report: SalesReport) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.report_sales_types), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (report.channels.isEmpty()) Text(stringResource(R.string.report_no_rows), color = MaterialTheme.colorScheme.onSurfaceVariant)
            report.channels.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${row.salesChannel.replace('_', ' ')} · ${row.saleCount}", Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Text(PosViewModel.formatPeso(row.amountCentavos), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ProductListCard(report: SalesReport) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.report_top_products), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (report.topProducts.isEmpty()) Text(stringResource(R.string.report_no_rows), color = MaterialTheme.colorScheme.onSurfaceVariant)
            report.topProducts.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(row.productName, Modifier.weight(1f))
                    Text(PosViewModel.formatPeso(row.netSalesCentavos), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun LowStockCard(count: Int, rows: List<String>) {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${stringResource(R.string.report_low_stock)} · $count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            if (rows.isEmpty()) Text(stringResource(R.string.report_no_rows), color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

private fun todayUtcMillis(): Long = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun dateFromUtc(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
private fun reportDateFormatter() = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private fun dateLabel(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))
