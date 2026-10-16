package com.aesprt.marisuki.feature.pos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aesprt.marisuki.R
import com.aesprt.marisuki.data.local.dao.CustomerBalance
import com.aesprt.marisuki.data.local.dao.ProductSummary
import com.aesprt.marisuki.data.local.entity.ProductUnitEntity
import com.aesprt.marisuki.feature.products.ProductPhoto
import com.aesprt.marisuki.domain.model.PaymentMethod
import com.aesprt.marisuki.domain.model.SaleChannel
import com.aesprt.marisuki.ui.theme.PosLayout
import com.aesprt.marisuki.ui.theme.PosSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierScreen(viewModel: PosViewModel, expandedTablet: Boolean) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val submitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var showCart by rememberSaveable { mutableStateOf(false) }
    var selectedFilter by rememberSaveable { mutableStateOf("ALL") }
    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var unitChoice by remember { mutableStateOf<Pair<ProductSummary, List<ProductUnitEntity>>?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val total = viewModel.cartTotalCentavos
    val filteredProducts = remember(products, selectedFilter, selectedCategory) {
        products.filter { product ->
            val filterMatches = when (selectedFilter) {
                "FAVORITES" -> product.favorite
                "FREQUENT" -> product.totalBaseSoldMilliUnits > 0
                else -> true
            }
            filterMatches && (selectedCategory.isBlank() || product.categoryName == selectedCategory)
        }
    }
    val categories = remember(products) { products.mapNotNull { it.categoryName }.distinct().sortedBy { it.lowercase() } }

    LaunchedEffect(cart.isEmpty()) {
        if (cart.isEmpty()) showCart = false
    }
    BackHandler(enabled = showCart) { showCart = false }
    val onProductClick: (ProductSummary) -> Unit = { product ->
        coroutineScope.launch {
            val units = viewModel.unitsFor(product.id)
            if (units.size > 1) unitChoice = product to units else viewModel.addProduct(product)
        }
    }

    if (expandedTablet) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = PosLayout.tabletScreenPadding, vertical = PosSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(PosLayout.tabletPaneGap),
        ) {
            Column(Modifier.weight(1.55f).fillMaxHeight()) {
                Text(stringResource(R.string.cashier_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(PosSpacing.sm))
                SearchBar(search, { search = it; viewModel.setSearchQuery(it) }, { viewModel.addBarcode(search); search = ""; viewModel.setSearchQuery("") }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(PosSpacing.sm))
                ProductFilters(selectedFilter, selectedCategory, categories, { selectedFilter = it; selectedCategory = "" }, { selectedCategory = it }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(PosSpacing.xs))
                ProductGrid(filteredProducts, onProductClick, search.isNotBlank(), Modifier.weight(1f))
            }
            androidx.compose.material3.VerticalDivider(Modifier.fillMaxHeight())
            CartPanel(
                viewModel = viewModel,
                cart = cart,
                customers = customers,
                total = total,
                submitting = submitting,
                modifier = Modifier.weight(1f).widthIn(min = 300.dp, max = 420.dp).fillMaxHeight(),
                onClose = null,
            )
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = PosLayout.compactScreenPadding, vertical = PosSpacing.xs)) {
            SearchBar(search, { search = it; viewModel.setSearchQuery(it) }, { viewModel.addBarcode(search); search = ""; viewModel.setSearchQuery("") }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(PosSpacing.xs))
            ProductFilters(selectedFilter, selectedCategory, categories, { selectedFilter = it; selectedCategory = "" }, { selectedCategory = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(PosSpacing.xs))
            ProductGrid(filteredProducts, onProductClick, search.isNotBlank(), Modifier.weight(1f))
            CartSummaryButton(cart = cart, total = total, onClick = { showCart = true })
        }
        if (showCart) {
            ModalBottomSheet(
                onDismissRequest = { showCart = false },
                sheetState = sheetState,
            ) {
                CartPanel(
                    viewModel = viewModel,
                    cart = cart,
                    customers = customers,
                    total = total,
                    submitting = submitting,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(max = 680.dp),
                    onClose = { showCart = false },
                )
            }
        }
    }
    unitChoice?.let { (product, units) ->
        AlertDialog(
            onDismissRequest = { unitChoice = null },
            title = { Text(stringResource(R.string.choose_selling_unit, product.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    units.forEach { unit ->
                        OutlinedButton(onClick = { viewModel.addProductInUnit(product, unit.id); unitChoice = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${unit.label} · ×${PosViewModel.formatQuantity(unit.baseQuantityMilliUnits)}")
                                Text(PosViewModel.formatPeso(unit.priceCentavos), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { unitChoice = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SearchBar(value: String, onValueChange: (String) -> Unit, onScan: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                onScan()
                true
            } else false
        },
        label = { Text(stringResource(R.string.cashier_scan_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                }
                IconButton(onClick = onScan, enabled = value.isNotBlank()) {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = stringResource(R.string.scan_entered_barcode))
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onScan() }),
    )
}

@Composable
private fun ProductFilters(
    selectedFilter: String,
    selectedCategory: String,
    categories: List<String>,
    onFilter: (String) -> Unit,
    onCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    Box(modifier) {
        Row(
            Modifier.fillMaxWidth().then(if (scroll.maxValue > 0) Modifier.padding(end = PosLayout.minimumTouchTarget) else Modifier).horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = selectedFilter == "ALL", onClick = { onFilter("ALL") }, label = { Text(stringResource(R.string.filter_all)) })
            FilterChip(selected = selectedFilter == "FAVORITES", onClick = { onFilter("FAVORITES") }, label = { Text(stringResource(R.string.filter_favorites)) })
            FilterChip(selected = selectedFilter == "FREQUENT", onClick = { onFilter("FREQUENT") }, label = { Text(stringResource(R.string.filter_frequently_sold)) })
            categories.forEach { category ->
                FilterChip(selected = selectedCategory == category, onClick = { onCategory(if (selectedCategory == category) "" else category) }, label = { Text(category) })
            }
        }
        if (scroll.maxValue > 0) IconButton(
            onClick = { scope.launch { scroll.animateScrollTo(if (scroll.value < scroll.maxValue) scroll.maxValue else 0) } },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.more_categories))
        }
    }
}

@Composable
private fun ProductGrid(
    products: List<ProductSummary>,
    onProductClick: (ProductSummary) -> Unit,
    hasSearch: Boolean,
    modifier: Modifier = Modifier,
) {
    if (products.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 360.dp).padding(PosSpacing.major),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            ) {
                Icon(
                    if (hasSearch) Icons.Filled.Inventory2 else Icons.Filled.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(if (hasSearch) R.string.cashier_empty_search else R.string.cashier_empty_products),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = PosSpacing.sm),
        ) {
            items(products, key = { it.id }) { product ->
                ProductTile(product, onClick = { onProductClick(product) })
            }
        }
    }
}

@Composable
private fun ProductTile(product: ProductSummary, onClick: () -> Unit) {
    val enabled = product.onHandMilliUnits > 0L
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 172.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(PosSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs),
        ) {
            Box(Modifier.fillMaxWidth().height(48.dp)) {
                ProductPhoto(product.imageFileName, Modifier.size(48.dp))
                if (product.favorite) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            stringResource(R.string.filter_favorites),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
            Text(
                product.name,
                modifier = Modifier.heightIn(min = 38.dp),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    PosViewModel.formatPeso(product.baseUnitPriceCentavos),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                Text("/ ${product.baseUnitLabel}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val stock = if (enabled) {
                "${PosViewModel.formatQuantity(product.onHandMilliUnits)} ${product.baseUnitLabel}"
            } else stringResource(R.string.out_of_stock)
            Text(
                stock,
                style = MaterialTheme.typography.labelSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CartSummaryButton(cart: List<CartLineState>, total: Long, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = cart.isNotEmpty(),
        modifier = Modifier.fillMaxWidth().heightIn(min = PosLayout.minimumTouchTarget).padding(top = PosSpacing.xs),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.cart_items_count, cart.size))
            Text(PosViewModel.formatPeso(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CartPanel(
    viewModel: PosViewModel,
    cart: List<CartLineState>,
    customers: List<CustomerBalance>,
    total: Long,
    submitting: Boolean,
    modifier: Modifier,
    onClose: (() -> Unit)?,
) {
    var methodKey by rememberSaveable { mutableStateOf(PaymentMethod.CASH.name) }
    var amount by rememberSaveable { mutableStateOf("") }
    var selectedCustomerId by rememberSaveable { mutableStateOf<String?>(null) }
    var salesChannel by rememberSaveable { mutableStateOf(SaleChannel.TINGI) }
    var customerMenu by remember { mutableStateOf(false) }
    val method = PaymentMethod.entries.firstOrNull { it.name == methodKey }
    val enteredAmount = if (amount.isBlank() && method != null) total else PosViewModel.parseCentavos(amount)
    val appliedNow = when {
        method == null -> 0L
        enteredAmount == null -> 0L
        else -> minOf(enteredAmount, total)
    }
    val change = if (method == PaymentMethod.CASH && enteredAmount != null) (enteredAmount - total).coerceAtLeast(0) else 0L
    val balance = (total - appliedNow).coerceAtLeast(0)
    val customerSelected = customers.any { it.id == selectedCustomerId }
    val requiresCustomer = method == null || balance > 0
    val validTender = method == null || (enteredAmount != null && enteredAmount > 0 && (method == PaymentMethod.CASH || enteredAmount <= total))
    val canComplete = cart.isNotEmpty() && !submitting && validTender && (!requiresCustomer || customerSelected)

    Column(modifier = modifier.imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = PosSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                Text(stringResource(R.string.cart_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.cart_items_count, cart.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close)) }
        }

        if (cart.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(PosSpacing.sm))
                Text(stringResource(R.string.cart_empty), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = PosSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs),
            ) {
                items(cart, key = { "${it.productId}:${it.unitId}" }) { line -> CartLineRow(line, viewModel) }
            }
        }

        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxWidth().padding(top = PosSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs),
            ) {
                HorizontalDivider(Modifier.padding(bottom = PosSpacing.xxs))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.total), style = MaterialTheme.typography.titleMedium)
                    Text(PosViewModel.formatPeso(total), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }

                if (cart.isNotEmpty()) {
                    Text(stringResource(R.string.sale_channel_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                        SaleChannel.entries.forEach { choice ->
                            FilterChip(
                                selected = salesChannel == choice,
                                onClick = { salesChannel = choice },
                                label = { Text(stringResource(if (choice == SaleChannel.TINGI) R.string.sale_channel_tingi else R.string.sale_channel_wholesale), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Text(stringResource(R.string.payment_method), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        PaymentMethod.entries.forEach { choice ->
                            val label = when (choice) {
                                PaymentMethod.CASH -> stringResource(R.string.method_cash)
                                PaymentMethod.GCASH -> stringResource(R.string.method_gcash)
                                PaymentMethod.MAYA -> stringResource(R.string.method_maya)
                            }
                            FilterChip(
                                selected = method == choice,
                                onClick = { methodKey = choice.name },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        FilterChip(
                            selected = method == null,
                            onClick = { methodKey = "UTANG" },
                            label = { Text(stringResource(R.string.method_credit), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (method == null || balance > 0) {
                        Box {
                            OutlinedButton(onClick = { customerMenu = true }, modifier = Modifier.fillMaxWidth().heightIn(min = PosLayout.minimumTouchTarget)) {
                                val selected = customers.firstOrNull { it.id == selectedCustomerId }
                                Text(selected?.name ?: stringResource(R.string.choose_customer), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            androidx.compose.material3.DropdownMenu(expanded = customerMenu, onDismissRequest = { customerMenu = false }) {
                                customers.forEach { customer ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text("${customer.name} · ${PosViewModel.formatPeso(customer.balanceCentavos)}") },
                                        onClick = { selectedCustomerId = customer.id; customerMenu = false },
                                    )
                                }
                            }
                        }
                    }
                    if (method != null) {
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.amount_received)) },
                            prefix = { Text("₱ ") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                    if (method == PaymentMethod.CASH) FinancialLine(stringResource(R.string.change_due), PosViewModel.formatPeso(change), emphasize = true)
                    if (balance > 0) FinancialLine(stringResource(R.string.amount_due), PosViewModel.formatPeso(balance), emphasize = true)
                }

                Button(
                    onClick = { viewModel.checkout(method, amount, selectedCustomerId, salesChannel) },
                    enabled = canComplete,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    if (submitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.checkout_done), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun CartLineRow(line: CartLineState, viewModel: PosViewModel) {
    val total = PosViewModel.calculateAmount(line.unitPriceCentavos, line.quantityMilliUnits)
    var quantityText by remember(line.productId, line.unitId) { mutableStateOf(PosViewModel.formatQuantity(line.quantityMilliUnits)) }
    LaunchedEffect(line.quantityMilliUnits) { quantityText = PosViewModel.formatQuantity(line.quantityMilliUnits) }
    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(line.productName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 2)
                Text("${line.unitLabel} · ${PosViewModel.formatPeso(line.unitPriceCentavos)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(PosViewModel.formatPeso(total), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { viewModel.updateCartQuantity(line.productId, line.unitId, -1_000) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.decrease_quantity))
            }
            OutlinedTextField(
                value = quantityText,
                onValueChange = { value ->
                    quantityText = value
                    PosViewModel.parseQuantityMilliUnits(value)?.let { parsed ->
                        if (!line.wholeSellQuantityOnly || parsed % 1_000L == 0L) viewModel.setCartQuantity(line.productId, line.unitId, parsed)
                        else quantityText = PosViewModel.formatQuantity(line.quantityMilliUnits)
                    }
                },
                modifier = Modifier.width(84.dp),
                label = { Text(stringResource(R.string.quantity_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val valid = PosViewModel.parseQuantityMilliUnits(quantityText)
                    if (valid == null || valid <= 0) quantityText = PosViewModel.formatQuantity(line.quantityMilliUnits)
                }),
            )
            IconButton(onClick = { viewModel.updateCartQuantity(line.productId, line.unitId, 1_000) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.increase_quantity))
            }
            IconButton(onClick = { viewModel.removeFromCart(line.productId, line.unitId) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.remove_item))
            }
        }
    }
}

@Composable
private fun FinancialLine(label: String, value: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal)
    }
}
