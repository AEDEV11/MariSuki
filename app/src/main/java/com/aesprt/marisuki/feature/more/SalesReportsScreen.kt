package com.aesprt.marisuki.feature.more

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.DateRange
import com.aesprt.marisuki.ui.components.ToolSectionHeader
import com.aesprt.marisuki.ui.components.ToolChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aesprt.marisuki.R
import com.aesprt.marisuki.data.SalesReport
import com.aesprt.marisuki.data.local.dao.LabelAmount
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.feature.pos.PosViewModel
import com.aesprt.marisuki.ui.theme.PosLayout
import com.aesprt.marisuki.ui.theme.PosSpacing

private enum class ReportPeriod { TODAY, YESTERDAY, WEEK, MONTH, CUSTOM }
private enum class DateField { FROM, UNTIL }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        val wide = maxWidth >= 900.dp
        Column(
            Modifier.widthIn(max = 1280.dp).fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (wide) PosLayout.tabletScreenPadding else PosLayout.compactScreenPadding)
                .padding(bottom = PosSpacing.major),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            ReportPeriodCard(
                period = period,
                periods = periods,
                fromDate = fromDate,
                untilDate = untilDate,
                onPeriodChange = { period = it },
                onChooseDate = { dateField = it },
            )

            if (state.isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.fillMaxWidth().padding(PosSpacing.section), verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                        Text(stringResource(R.string.report_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            state.error?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Text(
                        stringResource(if (error.name == "ACCESS_DENIED") R.string.report_access_denied else R.string.error_general),
                        modifier = Modifier.padding(PosSpacing.section),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            state.report?.let { report ->
                if (report.salesVisible) {
                    PrimaryMetrics(report, fromDate, untilDate)

                    if (wide) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm), verticalAlignment = Alignment.Top) {
                            DailySalesCard(report, Modifier.weight(1.8f))
                            PaymentMixCard(report, Modifier.weight(1f))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm), verticalAlignment = Alignment.Top) {
                            ProductListCard(report, Modifier.weight(1.15f))
                            ChannelListCard(report, Modifier.weight(1f))
                        }
                    } else {
                        DailySalesCard(report, Modifier.fillMaxWidth())
                        PaymentMixCard(report, Modifier.fillMaxWidth())
                        ProductListCard(report, Modifier.fillMaxWidth())
                        ChannelListCard(report, Modifier.fillMaxWidth())
                    }

                    if (report.saleCount == 0 && !state.isLoading && state.error == null) EmptyReportCard()
                }

                val secondaryMetrics = buildList {
                    report.grossProfitCentavos?.let { add(ReportMetric(R.string.report_gross_profit, it, R.string.gross_profit_description)) }
                    report.expensesCentavos?.let { add(ReportMetric(R.string.store_expenses, it, R.string.expense_report_description)) }
                    if (report.grossProfitCentavos != null && report.expensesCentavos != null) {
                        add(ReportMetric(R.string.report_net_estimate, report.grossProfitCentavos - report.expensesCentavos, R.string.net_report_description))
                    }
                    report.outstandingUtangCentavos?.let { add(ReportMetric(R.string.report_outstanding_utang, it, R.string.credit_not_cash)) }
                    report.inventoryValuationCentavos?.let { add(ReportMetric(R.string.report_inventory_value, it, R.string.report_inventory_value)) }
                }
                if (secondaryMetrics.isNotEmpty()) {
                    SectionTitle(stringResource(R.string.more_group_insights))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                    ) {
                        secondaryMetrics.forEach { metric ->
                            MetricCard(
                                title = stringResource(metric.title),
                                value = PosViewModel.formatPeso(metric.amountCentavos),
                                subtitle = stringResource(metric.subtitle),
                                modifier = Modifier.weight(1f).widthIn(min = 136.dp),
                            )
                        }
                    }
                }

                if (report.expenseCategories.isNotEmpty()) {
                    AmountListCard(stringResource(R.string.store_expenses), report.expenseCategories)
                }
                if (report.supplierPurchases.isNotEmpty()) {
                    AmountListCard(stringResource(R.string.report_supplier_purchases), report.supplierPurchases)
                }
                report.lowStockProducts?.let { rows ->
                    LowStockCard(rows.size, rows.map { "${it.productName} · ${PosViewModel.formatQuantity(it.onHandMilliUnits)} / ${PosViewModel.formatQuantity(it.minimumStockMilliUnits)}" })
                }

                if (PosPermission.EXPORT_REPORTS in permissions) {
                    Button(
                        onClick = { reportToExport = report; exportLauncher.launch("marisuki-report-${fromDate}.csv") },
                        modifier = Modifier.fillMaxWidth().heightIn(min = PosLayout.minimumTouchTarget),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = PosSpacing.section, vertical = PosSpacing.sm),
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = null)
                        Text(stringResource(R.string.report_export), modifier = Modifier.padding(start = PosSpacing.xs), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportPeriodCard(
    period: ReportPeriod,
    periods: List<Pair<ReportPeriod, Int>>,
    fromDate: LocalDate,
    untilDate: LocalDate,
    onPeriodChange: (ReportPeriod) -> Unit,
    onChooseDate: (DateField) -> Unit,
) {
    ReportCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(PosSpacing.screen), verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                    ToolSectionHeader("${dateLabel(fromDate)} – ${dateLabel(untilDate)}", Icons.Default.DateRange, stringResource(R.string.report_period_label))
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                periods.forEach { (value, label) ->
                    ToolChip(selected = period == value, onClick = { onPeriodChange(value) }, label = { Text(stringResource(label)) })
                }
            }
            if (period == ReportPeriod.CUSTOM) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs), verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                    TextButton(onClick = { onChooseDate(DateField.FROM) }) {
                        Text("${stringResource(R.string.range_from)} · ${fromDate.format(reportDateFormatter())}")
                    }
                    TextButton(onClick = { onChooseDate(DateField.UNTIL) }) {
                        Text("${stringResource(R.string.range_until)} · ${untilDate.format(reportDateFormatter())}")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PrimaryMetrics(report: SalesReport, fromDate: LocalDate, untilDate: LocalDate) {
    val metrics = listOf(
        ReportMetric(R.string.report_net_sales, report.netSalesCentavos, R.string.report_period_label, highlighted = true),
        ReportMetric(R.string.report_sale_count, report.saleCount.toLong(), R.string.report_period_label, count = true),
        ReportMetric(R.string.report_gross_sales, report.grossSalesCentavos, R.string.total_sales_description),
        ReportMetric(R.string.report_refunds, report.refundsCentavos, R.string.report_period_label),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
    ) {
        metrics.forEach { metric ->
            MetricCard(
                title = stringResource(metric.title),
                value = if (metric.count) metric.amountCentavos.toString() else PosViewModel.formatPeso(metric.amountCentavos),
                subtitle = stringResource(metric.subtitle).let { if (metric.highlighted) "${dateLabel(fromDate)} – ${dateLabel(untilDate)}" else it },
                highlighted = metric.highlighted,
                modifier = Modifier.weight(1f).widthIn(min = 136.dp),
            )
        }
    }
}

private data class ReportMetric(
    val title: Int,
    val amountCentavos: Long,
    val subtitle: Int,
    val highlighted: Boolean = false,
    val count: Boolean = false,
)

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
) {
    ReportCard(
        modifier,
        containerColor = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(PosSpacing.screen), verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = if (highlighted) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = if (highlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (highlighted) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f) else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun DailySalesCard(report: SalesReport, modifier: Modifier = Modifier) {
    ReportCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(PosSpacing.section), verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
            Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                Text(stringResource(R.string.report_daily_sales), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.report_gross_sales), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val values = report.dailySales
            if (values.isEmpty()) {
                EmptyRowsText()
            } else {
                val description = values.joinToString { "${it.day}: ${PosViewModel.formatPeso(it.salesCentavos)}" }
                val primary = MaterialTheme.colorScheme.primary
                val peakColor = MaterialTheme.colorScheme.tertiary
                val centerColor = MaterialTheme.colorScheme.surface
                val gridColor = MaterialTheme.colorScheme.outlineVariant
                Canvas(
                    Modifier.fillMaxWidth().height(196.dp).semantics { contentDescription = description },
                ) {
                    val maxValue = values.maxOf { it.salesCentavos }.coerceAtLeast(1L).toDouble()
                    val horizontalInset = 8.dp.toPx()
                    val top = 10.dp.toPx()
                    val bottom = size.height - 8.dp.toPx()
                    val chartWidth = (size.width - horizontalInset * 2).coerceAtLeast(1f)
                    val chartHeight = (bottom - top).coerceAtLeast(1f)
                    for (fraction in listOf(0.25f, 0.5f, 0.75f, 1f)) {
                        val y = bottom - chartHeight * fraction
                        drawLine(gridColor.copy(alpha = 0.75f), Offset(horizontalInset, y), Offset(size.width - horizontalInset, y), strokeWidth = 1.dp.toPx())
                    }
                    val points = values.mapIndexed { index, row ->
                        val xFraction = if (values.size <= 1) 0.5f else index.toFloat() / (values.lastIndex)
                        val salesFraction = (row.salesCentavos.coerceAtLeast(0L) / maxValue).toFloat()
                        Offset(horizontalInset + chartWidth * xFraction, bottom - chartHeight * salesFraction)
                    }
                    if (points.isNotEmpty()) {
                        val line = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            addSmoothSegments(points)
                        }
                        val area = Path().apply {
                            moveTo(points.first().x, bottom)
                            lineTo(points.first().x, points.first().y)
                            addSmoothSegments(points)
                            lineTo(points.last().x, bottom)
                            close()
                        }
                        drawPath(
                            area,
                            brush = Brush.verticalGradient(
                                colors = listOf(primary.copy(alpha = 0.32f), primary.copy(alpha = 0.015f)),
                                startY = top,
                                endY = bottom,
                            ),
                        )
                        drawPath(line, primary, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                        val peak = points[values.indices.maxBy { values[it].salesCentavos }]
                        drawCircle(peakColor, radius = 5.dp.toPx(), center = peak)
                        drawCircle(centerColor, radius = 2.dp.toPx(), center = peak)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(values.first().day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (values.size > 2) Text(values[values.lastIndex / 2].day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(values.last().day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
internal fun PaymentMixCard(report: SalesReport, modifier: Modifier = Modifier) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.outline,
    )
    val rows = report.payments.filter { it.amountCentavos > 0L }
    val total = rows.sumOf { it.amountCentavos }
    ReportCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(PosSpacing.section), verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
            Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                Text(stringResource(R.string.report_payment_breakdown), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            if (rows.isEmpty() || total <= 0L) {
                EmptyRowsText()
            } else {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 300.dp) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                            PaymentDonut(rows, total, colors)
                            PaymentLegend(rows, total, colors, Modifier.fillMaxWidth())
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                            PaymentDonut(rows, total, colors)
                            PaymentLegend(rows, total, colors, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentDonut(rows: List<LabelAmount>, total: Long, colors: List<Color>) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(122.dp).semantics {
            contentDescription = rows.joinToString { "${it.label}: ${PosViewModel.formatPeso(it.amountCentavos)}" }
        }) {
            val stroke = 18.dp.toPx()
            var startAngle = -90f
            rows.forEachIndexed { index, row ->
                val fullSweep = (row.amountCentavos.toDouble() / total.toDouble() * 360.0).toFloat()
                val gap = if (rows.size > 1) 3f else 0f
                val sweep = (fullSweep - gap).coerceAtLeast(0.5f)
                drawArc(
                    color = colors[index % colors.size],
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                startAngle += fullSweep
            }
        }
        Text(PosViewModel.formatPeso(total), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PaymentLegend(rows: List<LabelAmount>, total: Long, colors: List<Color>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
        rows.forEachIndexed { index, row ->
            val ratio = row.amountCentavos.toFloat() / total.toFloat()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                Box(Modifier.size(10.dp).background(colors[index % colors.size], CircleShape))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(row.label.replace('_', ' '), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(ratio * 100).toInt()}% · ${PosViewModel.formatPeso(row.amountCentavos)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun ProductListCard(report: SalesReport, modifier: Modifier = Modifier) {
    ReportCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(PosSpacing.section), verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
            SectionHeading(stringResource(R.string.report_top_products), stringResource(R.string.report_period_label))
            if (report.topProducts.isEmpty()) EmptyRowsText()
            report.topProducts.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("${index + 1}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(row.productName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(PosViewModel.formatQuantity(row.quantityMilliUnits), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(PosViewModel.formatPeso(row.netSalesCentavos), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
                if (index < report.topProducts.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
internal fun ChannelListCard(report: SalesReport, modifier: Modifier = Modifier) {
    ReportCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(PosSpacing.section), verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
            SectionHeading(stringResource(R.string.report_sales_types), stringResource(R.string.report_period_label))
            if (report.channels.isEmpty()) EmptyRowsText()
            val maxAmount = report.channels.maxOfOrNull { it.amountCentavos }?.coerceAtLeast(1L) ?: 1L
            report.channels.forEach { row ->
                Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(row.salesChannel.replace('_', ' '), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(pluralStringResource(R.plurals.report_sales_count, row.saleCount, row.saleCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(PosViewModel.formatPeso(row.amountCentavos), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { (row.amountCentavos.toFloat() / maxAmount.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
        }
    }
}

@Composable
private fun AmountListCard(title: String, rows: List<LabelAmount>) {
    ReportCard(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(PosSpacing.section), verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (rows.isEmpty()) EmptyRowsText()
            rows.forEachIndexed { index, row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(row.label.replace('_', ' '), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(PosSpacing.sm))
                    Text(PosViewModel.formatPeso(row.amountCentavos), fontWeight = FontWeight.Bold)
                }
                if (index < rows.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun LowStockCard(count: Int, rows: List<String>) {
    ReportCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(PosSpacing.section), verticalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.report_low_stock), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        count.toString(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            if (rows.isEmpty()) EmptyRowsText()
            rows.forEach { line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                    Text(line, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun EmptyReportCard() {
    ReportCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(PosSpacing.section), verticalArrangement = Arrangement.spacedBy(PosSpacing.xs)) {
            Text(stringResource(R.string.report_empty), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.report_no_rows), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, modifier = Modifier.padding(top = PosSpacing.xs), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
}

@Composable
private fun SectionHeading(title: String, supporting: String) {
    Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyRowsText() {
    Text(stringResource(R.string.report_no_rows), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ReportCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
        content = { content() },
    )
}

private fun todayUtcMillis(): Long = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
private fun dateFromUtc(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
private fun reportDateFormatter() = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
private fun dateLabel(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))

private fun Path.addSmoothSegments(points: List<Offset>) {
    if (points.size < 2) return
    for (index in 0 until points.lastIndex) {
        val previous = points[(index - 1).coerceAtLeast(0)]
        val start = points[index]
        val end = points[index + 1]
        val next = points[(index + 2).coerceAtMost(points.lastIndex)]
        cubicTo(
            start.x + (end.x - previous.x) / 6f,
            start.y + (end.y - previous.y) / 6f,
            end.x - (next.x - start.x) / 6f,
            end.y - (next.y - start.y) / 6f,
            end.x,
            end.y,
        )
    }
}
