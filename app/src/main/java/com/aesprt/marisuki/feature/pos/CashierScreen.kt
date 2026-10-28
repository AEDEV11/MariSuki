package com.aesprt.marisuki.feature.pos

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aesprt.marisuki.R
import com.aesprt.marisuki.data.local.dao.CustomerBalance
import com.aesprt.marisuki.data.local.dao.ProductSummary
import com.aesprt.marisuki.data.local.entity.ProductUnitEntity
import com.aesprt.marisuki.domain.model.PaymentMethod
import com.aesprt.marisuki.domain.model.SaleChannel
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.feature.products.ProductPhoto
import com.aesprt.marisuki.ui.theme.PosLayout
import com.aesprt.marisuki.ui.theme.PosSpacing
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashierScreen(viewModel: PosViewModel, expandedTablet: Boolean, mobileCameraScanning: Boolean) {
    val products by viewModel.products.collectAsStateWithLifecycle()
    val cart by viewModel.cart.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val submitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var showCart by rememberSaveable { mutableStateOf(false) }
    var showCameraScanner by rememberSaveable { mutableStateOf(false) }
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
    val onBarcode: (String) -> Unit = { barcode ->
        viewModel.resolveBarcode(barcode, onProductClick)
        search = ""
        viewModel.setSearchQuery("")
    }
    val onFavoriteClick: ((ProductSummary) -> Unit)? =
        if (PosPermission.VIEW_PRODUCTS in permissions) ({ product -> viewModel.setProductFavorite(product.id, !product.favorite) }) else null

    if (expandedTablet) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = PosLayout.tabletScreenPadding, vertical = PosSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(PosLayout.tabletPaneGap),
        ) {
            Column(Modifier.weight(1.55f).fillMaxHeight()) {
                SalesCatalogHeading(filteredProducts.size)
                Spacer(Modifier.height(PosSpacing.xs))
                SearchBar(
                    value = search,
                    onValueChange = { search = it; viewModel.setSearchQuery(it) },
                    onAddBarcode = { onBarcode(search) },
                    showCameraScan = mobileCameraScanning,
                    onCameraScan = { showCameraScanner = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(PosSpacing.xs))
                ProductFilters(
                    selectedFilter,
                    selectedCategory,
                    categories,
                    { selectedFilter = it; selectedCategory = "" },
                    { selectedCategory = it },
                    Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(PosSpacing.xs))
                ProductGrid(filteredProducts, onProductClick, search.isNotBlank(), Modifier.weight(1f), onFavoriteClick)
            }
            VerticalDivider(Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
            CartPanel(
                viewModel = viewModel,
                cart = cart,
                customers = customers,
                total = total,
                submitting = submitting,
                modifier = Modifier.weight(1f).widthIn(min = 320.dp, max = 440.dp).fillMaxHeight(),
                onClose = null,
            )
        }
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = PosLayout.compactScreenPadding, vertical = PosSpacing.xs)) {
            SalesCatalogHeading(filteredProducts.size)
            Spacer(Modifier.height(PosSpacing.sm))
            SearchBar(
                value = search,
                onValueChange = { search = it; viewModel.setSearchQuery(it) },
                onAddBarcode = { onBarcode(search) },
                showCameraScan = mobileCameraScanning,
                onCameraScan = { showCameraScanner = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(PosSpacing.xs))
            ProductFilters(
                selectedFilter,
                selectedCategory,
                categories,
                { selectedFilter = it; selectedCategory = "" },
                { selectedCategory = it },
                Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(PosSpacing.xs))
            ProductGrid(filteredProducts, onProductClick, search.isNotBlank(), Modifier.weight(1f), onFavoriteClick)
            CartSummaryButton(cart = cart, total = total, onClick = { showCart = true })
        }
        if (showCart) {
            ModalBottomSheet(
                onDismissRequest = { showCart = false },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                CartPanel(
                    viewModel = viewModel,
                    cart = cart,
                    customers = customers,
                    total = total,
                    submitting = submitting,
                    modifier = Modifier.fillMaxWidth(),
                    onClose = { showCart = false },
                )
            }
        }
    }
    if (showCameraScanner) {
        CameraBarcodeScannerDialog(
            onDismiss = { showCameraScanner = false },
            onBarcode = { barcode ->
                showCameraScanner = false
                onBarcode(barcode)
            },
        )
    }
    unitChoice?.let { (product, units) ->
        SellingUnitDialog(
            product = product,
            units = units,
            onDismiss = { unitChoice = null },
            onSelect = { unit ->
                viewModel.addProductInUnit(product, unit.id)
                unitChoice = null
            },
        )
    }
}

@Composable
internal fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onAddBarcode: () -> Unit,
    showCameraScan: Boolean,
    onCameraScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.onPreviewKeyEvent { event ->
            if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                onAddBarcode()
                true
            } else false
        },
        placeholder = { Text(stringResource(R.string.cashier_scan_hint)) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                    }
                }
                if (showCameraScan) {
                    IconButton(onClick = onCameraScan) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = stringResource(R.string.scan_with_camera), tint = MaterialTheme.colorScheme.primary)
                    }
                } else if (value.isNotBlank()) {
                    IconButton(onClick = onAddBarcode) {
                        Icon(Icons.Outlined.QrCodeScanner, contentDescription = stringResource(R.string.scan_entered_barcode), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onAddBarcode() }),
    )
}

@Composable
internal fun ProductFilters(
    selectedFilter: String,
    selectedCategory: String,
    categories: List<String>,
    onFilter: (String) -> Unit,
    onCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val chipColors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        )
        FilterChip(
            selected = selectedFilter == "ALL", onClick = { onFilter("ALL") },
            label = { Text(stringResource(R.string.filter_all), style = MaterialTheme.typography.labelLarge) },
            shape = RoundedCornerShape(12.dp), colors = chipColors,
        )
        FilterChip(
            selected = selectedFilter == "FAVORITES", onClick = { onFilter("FAVORITES") },
            label = { Text(stringResource(R.string.filter_favorites), style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { Icon(Icons.Filled.StarBorder, null, Modifier.size(18.dp)) },
            shape = RoundedCornerShape(12.dp), colors = chipColors,
        )
        FilterChip(
            selected = selectedFilter == "FREQUENT", onClick = { onFilter("FREQUENT") },
            label = { Text(stringResource(R.string.filter_frequently_sold), style = MaterialTheme.typography.labelLarge) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, null, Modifier.size(18.dp)) },
            shape = RoundedCornerShape(12.dp), colors = chipColors,
        )
        if (categories.isNotEmpty()) VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
        categories.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategory(if (selectedCategory == category) "" else category) },
                label = { Text(category, style = MaterialTheme.typography.labelLarge) },
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

@Composable
internal fun ProductGrid(
    products: List<ProductSummary>,
    onProductClick: (ProductSummary) -> Unit,
    hasSearch: Boolean,
    modifier: Modifier = Modifier,
    onFavoriteClick: ((ProductSummary) -> Unit)? = null,
) {
    if (products.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.widthIn(max = 360.dp).padding(PosSpacing.major),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (hasSearch) Icons.Filled.Inventory2 else Icons.Filled.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
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
            contentPadding = PaddingValues(bottom = PosSpacing.sm),
        ) {
            items(products, key = { it.id }) { product ->
                ProductTile(
                    product = product,
                    onClick = { onProductClick(product) },
                    onFavoriteClick = onFavoriteClick?.let { toggle -> { toggle(product) } },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProductTile(product: ProductSummary, onClick: () -> Unit, onFavoriteClick: (() -> Unit)?) {
    val enabled = product.onHandMilliUnits > 0L
    val lowStock = enabled && product.onHandMilliUnits <= product.minimumStockMilliUnits
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surface, disabledContainerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .7f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 2.dp),
    ) {
        ProductPhoto(
            product.imageFileName,
            Modifier.fillMaxWidth().aspectRatio(1.4f),
            contentScale = ContentScale.Fit,
            containerColor = colors.primaryContainer.copy(alpha = .45f),
            shape = RectangleShape,
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(product.name, Modifier.heightIn(min = 40.dp), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(PosViewModel.formatPeso(product.baseUnitPriceCentavos),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                    color = colors.primary, fontWeight = FontWeight.Bold)
                Text("/ ${product.baseUnitLabel}", Modifier.align(Alignment.CenterVertically),
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).background(when { !enabled -> colors.error; lowStock -> colors.tertiary; else -> colors.primary }, CircleShape))
                Text(
                    if (!enabled) stringResource(R.string.out_of_stock)
                    else "${PosViewModel.formatQuantity(product.onHandMilliUnits)} ${product.baseUnitLabel}",
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                    color = if (!enabled) colors.error else colors.onSurfaceVariant,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (onFavoriteClick != null) IconButton(onClick = onFavoriteClick, modifier = Modifier.size(PosLayout.minimumTouchTarget)) {
                    Icon(if (product.favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                        stringResource(if (product.favorite) R.string.remove_favorite else R.string.add_favorite),
                        tint = if (product.favorite) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(22.dp))
                } else if (product.favorite) Icon(Icons.Filled.Star, stringResource(R.string.filter_favorites), Modifier.size(20.dp), tint = colors.primary)
            }
        }
    }
}

@Composable
internal fun CartSummaryButton(cart: List<CartLineState>, total: Long, onClick: () -> Unit) {
    val hasItems = cart.isNotEmpty()
    Button(
        onClick = onClick, enabled = hasItems,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 64.dp),
        shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ShoppingCart, contentDescription = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(if (hasItems) R.string.sales_view_cart else R.string.sales_empty_cart), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(if (hasItems) pluralStringResource(R.plurals.checkout_item_count, cart.size, cart.size) else stringResource(R.string.sales_cart_hint), style = MaterialTheme.typography.bodySmall)
            }
            if (hasItems) {
                Text(PosViewModel.formatPeso(total), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
            }
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

    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val photos = remember(products) { products.associate { it.id to it.imageFileName } }
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(if (onClose == null) 24.dp else 0.dp),
        color = colors.surface,
        border = if (onClose == null) BorderStroke(1.dp, colors.outlineVariant) else null,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = colors.primary, contentColor = colors.onPrimary, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.ShoppingCart, null, Modifier.padding(12.dp).size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.cart_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(pluralStringResource(R.plurals.checkout_item_count, cart.size, cart.size), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                if (onClose != null) IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f, fill = onClose == null),
                verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 4.dp),
            ) {
                if (cart.isEmpty()) item {
                    Text(stringResource(R.string.cart_empty), Modifier.padding(vertical = 24.dp), color = colors.onSurfaceVariant)
                }
                items(cart, key = { "${it.productId}:${it.unitId}" }) { line ->
                    CartLineRow(line, viewModel, photos[line.productId])
                }
                if (cart.isNotEmpty()) item {
                    Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(R.string.sale_channel_label), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SaleChannel.entries.forEach { choice ->
                                    FilterChip(
                                        selected = salesChannel == choice, onClick = { salesChannel = choice },
                                        leadingIcon = if (salesChannel == choice) ({ Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }) else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            containerColor = colors.surface, selectedContainerColor = colors.primary,
                                            selectedLabelColor = colors.onPrimary, selectedLeadingIconColor = colors.onPrimary),
                                        label = { Text(stringResource(if (choice == SaleChannel.TINGI) R.string.sale_channel_tingi else R.string.sale_channel_wholesale)) },
                                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                    )
                                }
                            }
                            HorizontalDivider(color = colors.outlineVariant)
                            Text(stringResource(R.string.payment_method), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            PaymentChoiceChips(method, onSelect = { methodKey = it?.name ?: "UTANG" })
                            if (method == null || balance > 0) {
                                Box {
                                    OutlinedButton(
                                        onClick = { customerMenu = true },
                                        modifier = Modifier.fillMaxWidth().heightIn(min = PosLayout.minimumTouchTarget),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val selected = customers.firstOrNull { it.id == selectedCustomerId }
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Icon(Icons.Filled.Person, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Text(
                                                    selected?.name ?: stringResource(R.string.choose_customer),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                        }
                                    }
                                    DropdownMenu(expanded = customerMenu, onDismissRequest = { customerMenu = false }) {
                                        customers.forEach { customer ->
                                            DropdownMenuItem(
                                                text = { Text("${customer.name} · ${PosViewModel.formatPeso(customer.balanceCentavos)}") },
                                                onClick = { selectedCustomerId = customer.id; customerMenu = false },
                                            )
                                        }
                                    }
                                }
                            }

                            if (method != null) OutlinedTextField(
                                value = amount, onValueChange = { amount = it },
                                modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.amount_received)) },
                                placeholder = { Text(PosViewModel.formatPeso(total).removePrefix("₱").trim()) },
                                prefix = { Text("₱ ") }, singleLine = true, shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = colors.surface, focusedContainerColor = colors.surface),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            )
                            if (method == PaymentMethod.CASH) FinancialLine(stringResource(R.string.change_due), PosViewModel.formatPeso(change), emphasize = true)
                            if (balance > 0) FinancialLine(stringResource(R.string.amount_due), PosViewModel.formatPeso(balance), emphasize = true)
                        }
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(color = colors.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(stringResource(R.string.total), style = MaterialTheme.typography.titleSmall, color = colors.onSurfaceVariant)
                    }
                    Text(PosViewModel.formatPeso(total), style = MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings = "tnum"), color = colors.primary, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { viewModel.checkout(method, amount, selectedCustomerId, salesChannel) }, enabled = canComplete,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(16.dp),
                ) {
                    if (submitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.onPrimary)
                    else {
                        Icon(Icons.Default.CheckCircle, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(stringResource(R.string.checkout_complete_sale), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaymentChoiceChips(method: PaymentMethod?, onSelect: (PaymentMethod?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        )
        PaymentMethod.entries.forEach { choice ->
            FilterChip(selected = method == choice, onClick = { onSelect(choice) }, shape = RoundedCornerShape(12.dp), colors = colors,
                label = { Text(stringResource(when (choice) { PaymentMethod.CASH -> R.string.method_cash; PaymentMethod.GCASH -> R.string.method_gcash; PaymentMethod.MAYA -> R.string.method_maya })) })
        }
        FilterChip(selected = method == null, onClick = { onSelect(null) }, shape = RoundedCornerShape(12.dp), colors = colors,
            label = { Text(stringResource(R.string.method_credit)) })
    }
}

@Composable
private fun CartLineRow(line: CartLineState, viewModel: PosViewModel, imageFileName: String?) {
    val total = PosViewModel.calculateAmount(line.unitPriceCentavos, line.quantityMilliUnits)
    var quantityText by remember(line.productId, line.unitId) { mutableStateOf(PosViewModel.formatQuantity(line.quantityMilliUnits)) }
    LaunchedEffect(line.quantityMilliUnits) { quantityText = PosViewModel.formatQuantity(line.quantityMilliUnits) }
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surface, border = BorderStroke(1.dp, colors.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductPhoto(imageFileName, Modifier.size(56.dp), contentScale = ContentScale.Fit, containerColor = colors.primaryContainer.copy(alpha = .4f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(line.productName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${line.unitLabel} · ${PosViewModel.formatPeso(line.unitPriceCentavos)}", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.removeFromCart(line.productId, line.unitId) }) {
                    Icon(Icons.Default.DeleteOutline, stringResource(R.string.remove_item), tint = colors.error, modifier = Modifier.size(20.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = colors.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.updateCartQuantity(line.productId, line.unitId, -1_000) }) {
                            Icon(Icons.Default.Remove, stringResource(R.string.decrease_quantity), Modifier.size(18.dp))
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
                            modifier = Modifier.width(64.dp), shape = RoundedCornerShape(10.dp), singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center, fontFeatureSettings = "tnum"),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = colors.outlineVariant),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                val valid = PosViewModel.parseQuantityMilliUnits(quantityText)
                                if (valid == null || valid <= 0) quantityText = PosViewModel.formatQuantity(line.quantityMilliUnits)
                            }),
                        )
                        IconButton(onClick = { viewModel.updateCartQuantity(line.productId, line.unitId, 1_000) }) {
                            Icon(Icons.Default.Add, stringResource(R.string.increase_quantity), Modifier.size(18.dp))
                        }
                    }
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.checkout_line_total), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Text(PosViewModel.formatPeso(total), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.Bold, color = colors.primary)
                }
            }
        }
    }
}

@Composable
private fun FinancialLine(label: String, value: String, emphasize: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontFeatureSettings = "tnum"), fontWeight = if (emphasize) FontWeight.Bold else FontWeight.Normal)
    }
}
