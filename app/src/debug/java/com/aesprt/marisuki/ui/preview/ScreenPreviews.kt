package com.aesprt.marisuki.ui.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aesprt.marisuki.data.SalesReport
import com.aesprt.marisuki.data.local.dao.CustomerBalance
import com.aesprt.marisuki.data.local.dao.DailySalesReportRow
import com.aesprt.marisuki.data.local.dao.LabelAmount
import com.aesprt.marisuki.data.local.dao.LowStockReportRow
import com.aesprt.marisuki.data.local.dao.ProductSummary
import com.aesprt.marisuki.data.local.dao.SalesChannelAmount
import com.aesprt.marisuki.data.local.dao.TopProductReportRow
import com.aesprt.marisuki.data.local.entity.ProductUnitEntity
import com.aesprt.marisuki.domain.auth.AccountRole
import com.aesprt.marisuki.domain.auth.AccountSummary
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.feature.auth.BrandHeader
import com.aesprt.marisuki.feature.auth.StaffAccountRow
import com.aesprt.marisuki.feature.credit.CustomerRow
import com.aesprt.marisuki.feature.more.ChannelListCard
import com.aesprt.marisuki.feature.more.DailySalesCard
import com.aesprt.marisuki.feature.more.PaymentMixCard
import com.aesprt.marisuki.feature.more.PrimaryMetrics
import com.aesprt.marisuki.feature.more.ProductListCard
import com.aesprt.marisuki.feature.more.ToolEntry
import com.aesprt.marisuki.feature.more.ToolGroup
import com.aesprt.marisuki.feature.pos.CartLineState
import com.aesprt.marisuki.feature.pos.CartSummaryButton
import com.aesprt.marisuki.feature.pos.ProductFilters
import com.aesprt.marisuki.feature.pos.ProductGrid
import com.aesprt.marisuki.feature.pos.SearchBar
import com.aesprt.marisuki.feature.products.ProductDetailsPane
import com.aesprt.marisuki.feature.products.ProductListItem
import java.time.LocalDate
import java.time.ZoneOffset

private val previewProducts = listOf(
    ProductSummary("crispy-patata", "Crispy Patata", "4800123456789", "Snacks", "Piece", 1_000, 450, 2_000, true, 14_000, true, 8_000),
    ProductSummary("rice", "Sinandomeng Rice", "4800123456796", "Pantry", "Kilo", 6_500, 5_100, 5_000, false, 28_000, false, 62_000),
    ProductSummary("orange-juice", "Orange Juice", null, "Drinks", "Bottle", 2_500, 1_200, 4_000, true, 1_500, false, 2_500),
)

private val previewCustomers = listOf(
    CustomerBalance("maria", "Maria Santos", "0917 123 4567", 1_250_00),
    CustomerBalance("jose", "Jose Reyes", "0918 456 7890", 3_800_00),
    CustomerBalance("ana", "Ana Cruz", null, -500_00),
)

@MariSukiDevicePreviews
@Composable
fun CashierScreenPreview() = MariSukiPreviewSurface {
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val wide = maxWidth >= 760.dp
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.5f).fillMaxSize()) {
                    Text("New sale", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    SearchBar("", {}, {}, showCameraScan = false, onCameraScan = {}, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    ProductFilters("ALL", "", listOf("Drinks", "Pantry", "Snacks"), {}, {}, Modifier.fillMaxWidth())
                    ProductGrid(previewProducts, {}, hasSearch = false, modifier = Modifier.weight(1f), onFavoriteClick = {})
                }
                PreviewCartPane(Modifier.width(330.dp).fillMaxSize())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Text("New sale", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                SearchBar("", {}, {}, showCameraScan = true, onCameraScan = {}, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                ProductFilters("ALL", "", listOf("Drinks", "Pantry", "Snacks"), {}, {}, Modifier.fillMaxWidth())
                ProductGrid(previewProducts, {}, hasSearch = false, modifier = Modifier.weight(1f), onFavoriteClick = {})
                CartSummaryButton(previewCart, 8_900, {})
            }
        }
    }
}

@Composable
private fun PreviewCartPane(modifier: Modifier = Modifier) {
    Card(
        modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Current sale", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            Text("Crispy Patata", style = MaterialTheme.typography.titleMedium)
            Text("2 pieces · ₱20.00", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Orange Juice", style = MaterialTheme.typography.titleMedium)
            Text("2 bottles · ₱50.00", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.titleMedium)
                Text("₱70.00", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Button(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Charge customer") }
        }
    }
}

private val previewCart = listOf(
    CartLineState("crispy-patata", "Crispy Patata", "piece", "Piece", 2_000, 1_000, 1_000, 14_000, true),
    CartLineState("orange-juice", "Orange Juice", "bottle", "Bottle", 2_000, 2_500, 1_000, 1_500, true),
)

@MariSukiDevicePreviews
@Composable
fun ProductsScreenPreview() = MariSukiPreviewSurface {
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        val wide = maxWidth >= 760.dp
        Column(Modifier.fillMaxSize()) {
            Text("Products", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("125 products · 8 low in stock", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField("", {}, Modifier.fillMaxWidth(), label = { Text("Search products") }, singleLine = true)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = true, onClick = {}, label = { Text("All") })
                FilterChip(selected = false, onClick = {}, label = { Text("Low stock") })
                FilterChip(selected = false, onClick = {}, label = { Text("Out of stock") })
            }
            Spacer(Modifier.height(8.dp))
            if (wide) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(0.9f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        previewProducts.forEachIndexed { index, product ->
                            ProductListItem(product, selected = index == 0, compact = true, onSelect = {})
                        }
                    }
                    ProductDetailsPane(
                        product = previewProducts.first(),
                        units = listOf(ProductUnitEntity("piece", "crispy-patata", "Box", 12_000, 10_000, true)),
                        permissions = PosPermission.entries.toSet(),
                        modifier = Modifier.weight(1.1f).fillMaxSize(),
                        onEdit = {}, onStock = {}, onFavorite = {}, onPrintLabel = {}, onArchive = {},
                    )
                }
            } else {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    previewProducts.forEach { product -> ProductListItem(product, compact = false, onSelect = {}) }
                }
            }
        }
    }
}

@MariSukiDevicePreviews
@Composable
fun CreditScreenPreview() = MariSukiPreviewSurface {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Credit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Button(onClick = {}) { Text("Add customer") }
        }
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("Outstanding balance", color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("₱50.50", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
            }
        }
        previewCustomers.forEachIndexed { index, customer ->
            CustomerRow(customer, selected = index == 0, showPayment = true, onSelect = {}, onPayment = {})
        }
    }
}

private val previewSalesReport = SalesReport(
    fromMillis = LocalDate.of(2026, 10, 16).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    untilMillis = LocalDate.of(2026, 10, 17).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    grossSalesCentavos = 248_750,
    refundsCentavos = 5_000,
    netSalesCentavos = 243_750,
    salesVisible = true,
    saleCount = 82,
    grossProfitCentavos = 62_300,
    expensesCentavos = 14_500,
    payments = listOf(LabelAmount("Cash", 140_000), LabelAmount("GCash", 72_250), LabelAmount("Maya", 31_500)),
    channels = listOf(SalesChannelAmount("TINGI", 178_000, 67), SalesChannelAmount("WHOLESALE", 65_750, 15)),
    dailySales = listOf(
        DailySalesReportRow("9 AM", 13_000), DailySalesReportRow("10 AM", 25_500),
        DailySalesReportRow("11 AM", 42_000), DailySalesReportRow("12 PM", 61_250),
        DailySalesReportRow("1 PM", 49_000), DailySalesReportRow("2 PM", 53_000),
    ),
    topProducts = listOf(
        TopProductReportRow("rice", "Sinandomeng Rice", 18_000, 117_000),
        TopProductReportRow("crispy-patata", "Crispy Patata", 14_000, 14_000),
        TopProductReportRow("orange-juice", "Orange Juice", 8_000, 20_000),
    ),
    expenseCategories = listOf(LabelAmount("Utilities", 8_000), LabelAmount("Supplies", 6_500)),
    outstandingUtangCentavos = 50_500,
    inventoryValuationCentavos = 1_850_000,
    lowStockProducts = listOf(LowStockReportRow("orange-juice", "Orange Juice", 1_500, 4_000)),
    supplierPurchases = listOf(LabelAmount("Dela Cruz Trading", 125_000)),
)

@MariSukiDevicePreviews
@Composable
fun SalesReportsScreenPreview() = MariSukiPreviewSurface {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 900.dp
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Sales report", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Today · October 16", color = MaterialTheme.colorScheme.onSurfaceVariant)
            PrimaryMetrics(previewSalesReport, LocalDate.of(2026, 10, 16), LocalDate.of(2026, 10, 16))
            if (wide) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DailySalesCard(previewSalesReport, Modifier.weight(1.6f))
                    PaymentMixCard(previewSalesReport, Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProductListCard(previewSalesReport, Modifier.weight(1.1f))
                    ChannelListCard(previewSalesReport, Modifier.weight(1f))
                }
            } else {
                DailySalesCard(previewSalesReport, Modifier.fillMaxWidth())
                PaymentMixCard(previewSalesReport, Modifier.fillMaxWidth())
                ProductListCard(previewSalesReport, Modifier.fillMaxWidth())
                ChannelListCard(previewSalesReport, Modifier.fillMaxWidth())
            }
        }
    }
}

@MariSukiDevicePreviews
@Composable
fun MoreToolsScreenPreview() = MariSukiPreviewSurface {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("More tools", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Manage inventory, sales history and store settings", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ToolGroup("Operations", listOf(
            ToolEntry(Icons.Filled.Inventory, "Inventory", "Review stock and record adjustments") {},
            ToolEntry(Icons.Filled.LocalShipping, "Deliveries", "Receive stock from suppliers") {},
            ToolEntry(Icons.Filled.History, "Sales history", "Find receipts and review past sales") {},
        ))
        ToolGroup("Insights", listOf(
            ToolEntry(Icons.Filled.Assessment, "Reports", "Review sales, payments and stock") {},
            ToolEntry(Icons.Filled.Payments, "Expenses", "Track store expenses") {},
        ))
        ToolGroup("Administration", listOf(
            ToolEntry(Icons.Filled.Settings, "Settings", "Store details and preferences") {},
            ToolEntry(Icons.Filled.ManageAccounts, "Staff", "Manage cashier accounts") {},
        ))
    }
}

@MariSukiDevicePreviews
@Composable
fun StaffManagementScreenPreview() = MariSukiPreviewSurface {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Staff", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Cashier accounts can use only the tools you allow.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Add cashier") }
        StaffAccountRow(AccountSummary("cashier-1", "Marilyn Espiritu", "marilyn", AccountRole.CASHIER, true, 1), true, {})
        StaffAccountRow(AccountSummary("cashier-2", "Jose Reyes", "jose", AccountRole.CASHIER, true, 1), false, {})
        StaffAccountRow(AccountSummary("cashier-3", "Ana Cruz", "ana", AccountRole.CASHIER, false, 1), false, {})
    }
}

@MariSukiDevicePreviews
@Composable
fun PrinterSettingsScreenPreview() = MariSukiPreviewSurface {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Printers", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Choose how receipts and labels are printed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = false, onClick = {}, label = { Text("System") })
                    FilterChip(selected = true, onClick = {}, label = { Text("Bluetooth") })
                    FilterChip(selected = false, onClick = {}, label = { Text("USB") })
                }
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("Connected · MariSuki Receipt Printer", Modifier.padding(horizontal = 12.dp, vertical = 7.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Receipt and label sizes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Receipt paper", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("58 mm") })
                    FilterChip(selected = false, onClick = {}, label = { Text("80 mm") })
                }
                Text("Product labels", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("40 × 30 mm") })
                    FilterChip(selected = false, onClick = {}, label = { Text("50 × 30 mm") })
                }
                Button(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Print test receipt") }
            }
        }
    }
}

@MariSukiDevicePreviews
@Composable
fun AuthenticationSetupScreenPreview() = MariSukiPreviewSurface {
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Card(
            modifier = Modifier.fillMaxWidth().then(if (maxWidth >= 700.dp) Modifier.width(520.dp) else Modifier),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BrandHeader("Set up your store")
                Text("Create the owner account that manages your POS.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField("Marilyn Espiritu", {}, Modifier.fillMaxWidth(), label = { Text("Owner name") }, singleLine = true)
                OutlinedTextField("My Store", {}, Modifier.fillMaxWidth(), label = { Text("Store name") }, singleLine = true)
                OutlinedTextField("", {}, Modifier.fillMaxWidth(), label = { Text("Create a 6 digit PIN") }, singleLine = true)
                Button(onClick = {}, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Create owner account") }
            }
        }
    }
}

