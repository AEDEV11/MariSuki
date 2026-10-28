package com.aesprt.marisuki.feature.products

import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.ui.res.pluralStringResource
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aesprt.marisuki.R
import com.aesprt.marisuki.data.local.dao.ProductSummary
import com.aesprt.marisuki.data.local.entity.ProductUnitEntity
import com.aesprt.marisuki.data.ProductImageStore
import com.aesprt.marisuki.domain.model.NewProduct
import com.aesprt.marisuki.domain.model.ProductUnitInput
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.feature.pos.PosViewModel
import com.aesprt.marisuki.feature.more.StockDialog
import com.aesprt.marisuki.feature.printing.ProductLabelPrinter
import com.aesprt.marisuki.ui.theme.PosLayout
import com.aesprt.marisuki.ui.theme.PosSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductsScreen(viewModel: PosViewModel) {
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    if (PosPermission.VIEW_PRODUCTS !in permissions) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.error_access_denied), style = MaterialTheme.typography.titleMedium)
        }
        return
    }
    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val pendingPriceChange by viewModel.pendingPriceChange.collectAsStateWithLifecycle()
    val priceApprovalError by viewModel.priceApprovalError.collectAsStateWithLifecycle()
    val working by viewModel.isWorking.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val printFailure = stringResource(R.string.product_label_print_failed)
    var showForm by rememberSaveable { mutableStateOf(false) }
    var showDetails by rememberSaveable { mutableStateOf(false) }
    val detailsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedProductId by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ProductSummary?>(null) }
    var archiveTarget by remember { mutableStateOf<ProductSummary?>(null) }
    var labelTarget by remember { mutableStateOf<ProductSummary?>(null) }
    var stockTarget by remember { mutableStateOf<ProductSummary?>(null) }
    var productSearch by rememberSaveable { mutableStateOf("") }
    var showBulkImport by rememberSaveable { mutableStateOf(false) }
    var stockFilter by rememberSaveable { mutableStateOf("ALL") }
    val visibleProducts = remember(products, productSearch, stockFilter) {
        products.filter { product ->
            val matchesSearch = productSearch.isBlank() ||
                product.name.contains(productSearch.trim(), ignoreCase = true) ||
                product.barcode?.contains(productSearch.trim(), ignoreCase = true) == true ||
                product.categoryName?.contains(productSearch.trim(), ignoreCase = true) == true
            val matchesStock = when (stockFilter) {
                "LOW" -> product.onHandMilliUnits > 0 && product.onHandMilliUnits <= product.minimumStockMilliUnits
                "OUT" -> product.onHandMilliUnits <= 0
                else -> true
            }
            matchesSearch && matchesStock
        }
    }

    LaunchedEffect(visibleProducts) {
        if (visibleProducts.none { it.id == selectedProductId }) selectedProductId = visibleProducts.firstOrNull()?.id
    }
    val selectedProduct = visibleProducts.firstOrNull { it.id == selectedProductId }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
        Column(Modifier.fillMaxSize().padding(if (expanded) PosLayout.tabletScreenPadding else PosLayout.compactScreenPadding)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.Inventory2, null, Modifier.padding(12.dp).size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.products_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(pluralStringResource(R.plurals.sales_product_count, products.size, products.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (PosPermission.ADD_PRODUCT in permissions) Button(
                    onClick = { editing = null; showForm = true }, shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Filled.Add, null, Modifier.size(20.dp))
                    Text(stringResource(if (expanded) R.string.add_product else R.string.product_add_short), Modifier.padding(start = 6.dp))
                }
            }
            if (PosPermission.ADD_PRODUCT in permissions) androidx.compose.material3.OutlinedButton(
                onClick = { viewModel.resetProductImport(); showBulkImport = true }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.FileUpload, null, Modifier.size(20.dp))
                Text(stringResource(R.string.bulk_title), Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(PosSpacing.sm))
            if (products.isNotEmpty()) {
                OutlinedTextField(
                    value = productSearch,
                    onValueChange = { productSearch = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.product_search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                        focusedContainerColor = MaterialTheme.colorScheme.surface, unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant),
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = if (productSearch.isNotEmpty()) ({
                        IconButton(onClick = { productSearch = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
                        }
                    }) else null,
                    singleLine = true,
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                ) {
                    listOf(
                        "ALL" to R.string.filter_all,
                        "LOW" to R.string.product_filter_low_stock,
                        "OUT" to R.string.out_of_stock,
                    ).forEach { (value, label) ->
                        FilterChip(selected = stockFilter == value, onClick = { stockFilter = value }, label = { Text(stringResource(label)) },
                            shape = RoundedCornerShape(12.dp), colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surface, selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
                    }
                }
                Spacer(Modifier.height(PosSpacing.xs))
            }
            if (products.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.widthIn(max = 420.dp).padding(PosSpacing.major),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                    ) {
                        Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.cashier_empty_products), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (PosPermission.ADD_PRODUCT in permissions) Button(onClick = { editing = null; showForm = true }, modifier = Modifier.heightIn(min = PosLayout.minimumTouchTarget)) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(stringResource(R.string.add_product), Modifier.padding(start = PosSpacing.xs))
                        }
                    }
                }
            } else if (visibleProducts.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.product_filter_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (expanded) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosLayout.tabletPaneGap)) {
                    LazyColumn(
                        modifier = Modifier.weight(0.9f),
                        verticalArrangement = Arrangement.spacedBy(PosSpacing.xs),
                        contentPadding = PaddingValues(bottom = PosSpacing.sm),
                    ) {
                        items(visibleProducts, key = { it.id }) { product ->
                            ProductListItem(product, selected = product.id == selectedProduct?.id, compact = true) {
                                selectedProductId = product.id
                            }
                        }
                    }
                    selectedProduct?.let { product ->
                        ProductDetailsPane(
                            product = product,
                            units = produceState<List<ProductUnitEntity>>(emptyList(), product.id) {
                                value = viewModel.unitsFor(product.id)
                            }.value,
                            permissions = permissions,
                            modifier = Modifier.weight(1.1f, fill = false).fillMaxWidth(),
                            onEdit = { editing = product; showForm = true },
                            onStock = { stockTarget = product },
                            onFavorite = { viewModel.setProductFavorite(product.id, !product.favorite) },
                            onPrintLabel = { labelTarget = product },
                            onArchive = { archiveTarget = product },
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosSpacing.xs), contentPadding = PaddingValues(bottom = PosSpacing.sm)) {
                    items(visibleProducts, key = { it.id }) { product ->
                        ProductListItem(
                            product = product,
                            compact = false,
                            onEdit = if (PosPermission.EDIT_PRODUCT in permissions) ({ editing = product; showForm = true }) else null,
                            onFavorite = if (PosPermission.VIEW_PRODUCTS in permissions) ({ viewModel.setProductFavorite(product.id, !product.favorite) }) else null,
                            onPrintLabel = if (PosPermission.MANAGE_PRINTERS in permissions) ({ labelTarget = product }) else null,
                            onArchive = if (PosPermission.ARCHIVE_PRODUCT in permissions) ({ archiveTarget = product }) else null,
                        ) { selectedProductId = product.id; showDetails = true }
                    }
                }
            }
        }
    }

    if (showDetails && selectedProduct != null) {
        val product = selectedProduct
        ModalBottomSheet(onDismissRequest = { showDetails = false }, sheetState = detailsSheetState,
            containerColor = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.product_details_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showDetails = false }) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                }
                ProductDetailsPane(
                    product, produceState<List<ProductUnitEntity>>(emptyList(), product.id) { value = viewModel.unitsFor(product.id) }.value,
                    permissions, Modifier.fillMaxWidth(),
                    onEdit = { showDetails = false; editing = product; showForm = true },
                    onStock = { showDetails = false; stockTarget = product },
                    onFavorite = { viewModel.setProductFavorite(product.id, !product.favorite) },
                    onPrintLabel = { showDetails = false; labelTarget = product },
                    onArchive = { showDetails = false; archiveTarget = product },
                )
            }
        }
    }

    if (showBulkImport) BulkProductImportDialog(viewModel, onDismiss = { showBulkImport = false; viewModel.resetProductImport() })
    if (showForm) {
        ProductFormDialog(
            viewModel = viewModel,
            initial = editing,
            onDismiss = { showForm = false; editing = null },
            onSave = { input ->
                val selected = editing
                if (selected == null) viewModel.createProduct(input) else viewModel.updateProduct(selected.id, input)
                showForm = false
                editing = null
            },
        )
    }
    archiveTarget?.let { product ->
        AlertDialog(
            onDismissRequest = { archiveTarget = null },
            containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
            icon = { ProductDialogIcon(Icons.Default.Archive) },
            title = { Text(stringResource(R.string.archive_product)) },
            text = { Text(stringResource(R.string.archive_product_confirm, product.name)) },
            confirmButton = {
                TextButton(onClick = { viewModel.archiveProduct(product.id); archiveTarget = null }) { Text(stringResource(R.string.archive_product)) }
            },
            dismissButton = { TextButton(onClick = { archiveTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    stockTarget?.let { product ->
        StockDialog(
            product = product,
            onDismiss = { stockTarget = null },
            onSave = { quantity, reason, note, increase, operationId ->
                if (increase) viewModel.receiveStock(product.id, quantity, reason, note, operationId)
                else viewModel.adjustStock(product.id, -quantity, reason, note, operationId)
                stockTarget = null
            },
            canRemove = PosPermission.ADJUST_STOCK in permissions,
        )
    }
    labelTarget?.let { product ->
        ProductLabelOptionsDialog(
            product = product,
            onDismiss = { labelTarget = null },
            onPrint = { copies, widthMm, heightMm ->
                labelTarget = null
                scope.launch {
                    try {
                        val barcode = viewModel.ensureProductBarcode(product.id)
                        ProductLabelPrinter.print(context, product.copy(barcode = barcode), copies, widthMm, heightMm)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        Toast.makeText(context, printFailure, Toast.LENGTH_LONG).show()
                    }
                }
            },
        )
    }
    pendingPriceChange?.let { pending ->
        var pin by remember(pending.preview.approvalId) { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = viewModel::cancelPendingPriceChange,
            containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
            icon = { ProductDialogIcon(Icons.Default.WarningAmber) },
            title = { Text("Owner Approval Required") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 380.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Wala kang permission na baguhin ang presyo. Kailangan ng Owner PIN.")
                    Text(pending.preview.beforeValue.split("; ").joinToString("\n") { pricePreviewLine(it) })
                    Text("Requested price", style = MaterialTheme.typography.titleSmall)
                    Text(pending.preview.afterValue.split("; ").joinToString("\n") { pricePreviewLine(it) })
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Owner authorization PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                    priceApprovalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(onClick = { val entered = pin; pin = ""; viewModel.authorizePendingPriceChange(entered) }, enabled = pin.length == 6 && !working) {
                    if (working) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Authorize and save")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::cancelPendingPriceChange, enabled = !working) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
internal fun ProductListItem(
    product: ProductSummary,
    selected: Boolean = false,
    compact: Boolean,
    onEdit: (() -> Unit)? = null,
    onFavorite: (() -> Unit)? = null,
    onPrintLabel: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onSelect: () -> Unit,
) {
    var actionsExpanded by rememberSaveable(product.id) { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Card(
        onClick = onSelect,
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) colors.primaryContainer.copy(alpha = .25f) else colors.surface,
            contentColor = colors.onSurface),
        border = BorderStroke(1.dp, if (selected) colors.primary.copy(alpha = .5f) else colors.outlineVariant.copy(alpha = .8f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp, pressedElevation = 1.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                ProductPhoto(product.imageFileName, Modifier.size(if (compact) 64.dp else 88.dp), contentScale = ContentScale.Fit,
                    containerColor = colors.primaryContainer.copy(alpha = .4f), shape = RoundedCornerShape(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    product.categoryName?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
                    Text(PosViewModel.formatPeso(product.baseUnitPriceCentavos), style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"), color = colors.primary, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.product_price_per_unit, product.baseUnitLabel), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
                if (!compact) {
                    Box {
                        IconButton(onClick = { actionsExpanded = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.product_actions))
                        }
                        DropdownMenu(expanded = actionsExpanded, onDismissRequest = { actionsExpanded = false }) {
                            if (onEdit != null) DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_product)) },
                                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                onClick = { actionsExpanded = false; onEdit() },
                            )
                            if (onFavorite != null) DropdownMenuItem(
                                text = { Text(stringResource(if (product.favorite) R.string.remove_favorite else R.string.add_favorite)) },
                                leadingIcon = { Icon(if (product.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null) },
                                onClick = { actionsExpanded = false; onFavorite() },
                            )
                            if (onPrintLabel != null) DropdownMenuItem(
                                text = { Text(stringResource(R.string.print_product_label)) },
                                leadingIcon = { Icon(Icons.Filled.QrCode2, contentDescription = null) },
                                onClick = { actionsExpanded = false; onPrintLabel() },
                            )
                            if (onArchive != null) DropdownMenuItem(
                                text = { Text(stringResource(R.string.archive_product)) },
                                leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                                onClick = { actionsExpanded = false; onArchive() },
                            )
                        }
                    }
                }
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = .65f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductStockBadge(product, Modifier.weight(1f, fill = false))
                Spacer(Modifier.weight(1f))
                if (product.favorite) Icon(Icons.Default.Favorite, stringResource(R.string.filter_favorites), Modifier.size(18.dp), tint = colors.primary)
            }
            if (!compact) product.barcode?.let { barcode ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode2, null, Modifier.size(16.dp), tint = colors.onSurfaceVariant)
                    Text(barcode, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun ProductStockBadge(product: ProductSummary, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val empty = product.onHandMilliUnits <= 0
    val low = !empty && product.onHandMilliUnits <= product.minimumStockMilliUnits
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = when { empty -> colors.errorContainer; low -> colors.tertiaryContainer; else -> colors.primaryContainer.copy(alpha = .5f) }) {
        Text(if (empty) stringResource(R.string.out_of_stock) else stringResource(R.string.product_stock_summary, PosViewModel.formatQuantity(product.onHandMilliUnits), product.baseUnitLabel),
            Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium,
            color = when { empty -> colors.onErrorContainer; low -> colors.onTertiaryContainer; else -> colors.onSurface })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProductDetailsPane(
    product: ProductSummary,
    units: List<ProductUnitEntity>,
    permissions: Set<PosPermission>,
    modifier: Modifier,
    onEdit: () -> Unit,
    onStock: () -> Unit,
    onFavorite: () -> Unit,
    onPrintLabel: () -> Unit,
    onArchive: () -> Unit,
) {
    val expanded = currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
    val actionModifier = (if (expanded) Modifier else Modifier.fillMaxWidth()).heightIn(min = PosLayout.minimumTouchTarget)
    Card(
        modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(PosSpacing.section),
            verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                ProductPhoto(product.imageFileName, Modifier.size(104.dp), contentScale = ContentScale.Fit,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f), shape = RoundedCornerShape(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs)) {
                    Text(product.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    product.categoryName?.takeIf(String::isNotBlank)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    product.barcode?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            ProductStockBadge(product)
            androidx.compose.material3.HorizontalDivider()
            Text(stringResource(R.string.inventory_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(PosSpacing.section)) {
                ProductDetailValue(
                    stringResource(R.string.selling_price),
                    "${PosViewModel.formatPeso(product.baseUnitPriceCentavos)} / ${product.baseUnitLabel}",
                    Modifier.weight(1f),
                )
                ProductDetailValue(
                    stringResource(R.string.product_stock_on_hand),
                    "${PosViewModel.formatQuantity(product.onHandMilliUnits)} ${product.baseUnitLabel}",
                    Modifier.weight(1f),
                    emphasize = product.onHandMilliUnits <= product.minimumStockMilliUnits,
                )
            }
            if (PosPermission.VIEW_PURCHASE_COST in permissions || PosPermission.EDIT_PURCHASE_COST in permissions) {
                ProductDetailValue(stringResource(R.string.purchase_cost), PosViewModel.formatPeso(product.purchaseCostCentavos), Modifier.fillMaxWidth())
            }
            val extraUnits = units.filter { it.baseQuantityMilliUnits != 1_000L || !it.label.equals(product.baseUnitLabel, ignoreCase = true) }
            if (extraUnits.isNotEmpty()) {
                androidx.compose.material3.HorizontalDivider()
                Text(stringResource(R.string.product_editor_units), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                extraUnits.forEach { unit ->
                    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(unit.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.unit_conversion_example, unit.label, PosViewModel.formatQuantity(unit.baseQuantityMilliUnits), product.baseUnitLabel),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(PosViewModel.formatPeso(unit.priceCentavos), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            androidx.compose.material3.HorizontalDivider()
            Text(stringResource(R.string.product_actions), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(PosSpacing.xs, Alignment.End),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.xxs),
            ) {
                if (PosPermission.EDIT_PRODUCT in permissions) Button(onClick = onEdit, modifier = actionModifier, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                    Text(stringResource(R.string.edit_product), Modifier.padding(start = PosSpacing.xs))
                }
                if (PosPermission.ADD_STOCK in permissions) androidx.compose.material3.OutlinedButton(onClick = onStock, modifier = actionModifier, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text(stringResource(R.string.add_stock), Modifier.padding(start = PosSpacing.xs))
                }
                if (PosPermission.VIEW_PRODUCTS in permissions) androidx.compose.material3.OutlinedButton(onClick = onFavorite, modifier = actionModifier, shape = RoundedCornerShape(12.dp)) {
                    Icon(if (product.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = null)
                    Text(stringResource(if (product.favorite) R.string.remove_favorite else R.string.add_favorite), Modifier.padding(start = PosSpacing.xs))
                }
                if (PosPermission.MANAGE_PRINTERS in permissions) androidx.compose.material3.OutlinedButton(onClick = onPrintLabel, modifier = actionModifier, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.QrCode2, contentDescription = null)
                    Text(stringResource(R.string.print_product_label), Modifier.padding(start = PosSpacing.xs))
                }
                if (PosPermission.ARCHIVE_PRODUCT in permissions) androidx.compose.material3.TextButton(onClick = onArchive, modifier = actionModifier, shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Filled.Archive, contentDescription = null)
                    Text(stringResource(R.string.archive_product), Modifier.padding(start = PosSpacing.xs))
                }
            }
        }
    }
}

@Composable
private fun ProductDetailValue(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"), fontWeight = FontWeight.SemiBold,
                color = if (emphasize) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun pricePreviewLine(value: String): String {
    val label = value.substringBefore('=')
    val price = value.substringAfter('=', "").toLongOrNull()
    return if (price == null) value else "$label · ${PosViewModel.formatPeso(price)}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProductLabelOptionsDialog(
    product: ProductSummary,
    onDismiss: () -> Unit,
    onPrint: (copies: Int, widthMm: Int, heightMm: Int) -> Unit,
) {
    var copiesText by rememberSaveable(product.id) { mutableStateOf("1") }
    var selectedSize by rememberSaveable(product.id) { mutableStateOf("50x30") }
    val sizes = listOf("40x30", "50x30", "60x40")
    val copies = copiesText.toIntOrNull()?.takeIf { it in 1..100 }
    ProductDialogFrame(
        title = stringResource(R.string.print_product_label), icon = Icons.Default.Print, onDismiss = onDismiss,
        actions = {
            TextButton(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.cancel)) }
            Button(enabled = copies != null, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp), onClick = {
                val (width, height) = selectedSize.split('x').map(String::toInt)
                copies?.let { onPrint(it, width, height) }
            }) {
                Icon(Icons.Default.Print, null, Modifier.size(18.dp))
                Text(stringResource(R.string.print_labels), Modifier.padding(start = 8.dp))
            }
        },
    ) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ProductPhoto(product.imageFileName, Modifier.size(72.dp), contentScale = ContentScale.Fit,
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .3f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${PosViewModel.formatPeso(product.baseUnitPriceCentavos)} / ${product.baseUnitLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Text(product.barcode ?: stringResource(R.string.internal_barcode_on_print), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        ProductField(copiesText, { copiesText = it.filter(Char::isDigit).take(3) }, stringResource(R.string.label_copies), keyboardType = KeyboardType.Number)
        Text(stringResource(R.string.label_size), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sizes.forEach { size ->
                FilterChip(selected = selectedSize == size, onClick = { selectedSize = size },
                    label = { Text("${size.substringBefore('x')} × ${size.substringAfter('x')} mm") },
                    leadingIcon = if (selectedSize == size) ({ Icon(Icons.Default.Check, null, Modifier.size(18.dp)) }) else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surface,
                        selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary))
            }
        }
    }
}

@Composable
private fun ProductFormDialog(
    viewModel: PosViewModel,
    initial: ProductSummary?,
    onDismiss: () -> Unit,
    onSave: (NewProduct) -> Unit,
) {
    val imageStore: ProductImageStore = koinInject()
    val scope = rememberCoroutineScope()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val canEditPurchaseCost = PosPermission.EDIT_PURCHASE_COST in permissions
    val canViewPurchaseCost = PosPermission.VIEW_PURCHASE_COST in permissions || canEditPurchaseCost
    val canAddStock = PosPermission.ADD_STOCK in permissions
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var barcode by rememberSaveable(initial?.id) { mutableStateOf(initial?.barcode.orEmpty()) }
    val savedCategories by viewModel.categoryNames.collectAsStateWithLifecycle()
    val savedStockUnits by viewModel.stockUnitNames.collectAsStateWithLifecycle()
    val savedSellingUnits by viewModel.sellingUnitNames.collectAsStateWithLifecycle()
    var category by rememberSaveable(initial?.id) { mutableStateOf(initial?.categoryName.orEmpty()) }
    var unit by rememberSaveable(initial?.id) { mutableStateOf(initial?.baseUnitLabel ?: "Piece") }
    var price by rememberSaveable(initial?.id) { mutableStateOf(initial?.baseUnitPriceCentavos?.let { formatDecimal(it) }.orEmpty()) }
    var cost by rememberSaveable(initial?.id) { mutableStateOf(if (canViewPurchaseCost) initial?.purchaseCostCentavos?.let { formatDecimal(it) }.orEmpty() else "0") }
    var stock by rememberSaveable(initial?.id) { mutableStateOf(initial?.let { PosViewModel.formatQuantity(it.onHandMilliUnits) } ?: if (canAddStock) "" else "0") }
    var lowStock by rememberSaveable(initial?.id) { mutableStateOf(initial?.let { PosViewModel.formatQuantity(it.minimumStockMilliUnits) } ?: "") }
    var fractional by rememberSaveable(initial?.id) { mutableStateOf(initial?.wholeBaseUnitsOnly == false) }
    var packEnabled by rememberSaveable(initial?.id) { mutableStateOf(false) }
    var packName by rememberSaveable(initial?.id) { mutableStateOf("") }
    var packCount by rememberSaveable(initial?.id) { mutableStateOf("") }
    var packPrice by rememberSaveable(initial?.id) { mutableStateOf("") }
    var caseEnabled by rememberSaveable(initial?.id) { mutableStateOf(false) }
    var caseName by rememberSaveable(initial?.id) { mutableStateOf("") }
    var caseCount by rememberSaveable(initial?.id) { mutableStateOf("") }
    var casePrice by rememberSaveable(initial?.id) { mutableStateOf("") }
    var imageFileName by rememberSaveable(initial?.id) { mutableStateOf(initial?.imageFileName) }
    var importingPhoto by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    var formTouched by rememberSaveable(initial?.id) { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable(initial?.id) { mutableStateOf(false) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            importingPhoto = true
            photoError = false
            scope.launch {
                try {
                imageFileName = withContext(Dispatchers.IO) { imageStore.importPhoto(uri) }
                    formTouched = true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    photoError = true
                } finally {
                    importingPhoto = false
                }
            }
        }
    }

    LaunchedEffect(initial?.id) {
        if (initial != null) {
            try {
                val unitRows = viewModel.unitsFor(initial.id)
                val base = unitRows.firstOrNull { it.label.equals(initial.baseUnitLabel, ignoreCase = true) }
                val extras = unitRows.filter { it.id != base?.id }
                extras.getOrNull(0)?.let { pack ->
                    packEnabled = true
                    packName = pack.label
                    packCount = PosViewModel.formatQuantity(pack.baseQuantityMilliUnits)
                    packPrice = formatDecimal(pack.priceCentavos)
                }
                extras.getOrNull(1)?.let { case ->
                    caseEnabled = true
                    caseName = case.label
                    caseCount = PosViewModel.formatQuantity(case.baseQuantityMilliUnits)
                    casePrice = formatDecimal(case.priceCentavos)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    val input = remember(name, barcode, category, unit, price, cost, stock, lowStock, fractional, packEnabled, packName, packCount, packPrice, caseEnabled, caseName, caseCount, casePrice, imageFileName) {
        val priceCents = PosViewModel.parseCentavos(price)
        val costCents = PosViewModel.parseCentavos(cost)
        val stockUnits = PosViewModel.parseQuantityMilliUnits(stock)
        val lowUnits = PosViewModel.parseQuantityMilliUnits(lowStock.ifBlank { "0" })
        val conversion = PosViewModel.parseQuantityMilliUnits(packCount)
        val packCents = PosViewModel.parseCentavos(packPrice)
        val caseConversion = PosViewModel.parseQuantityMilliUnits(caseCount)
        val caseCents = PosViewModel.parseCentavos(casePrice)
        if (priceCents == null || costCents == null || stockUnits == null || lowUnits == null || unit.isBlank() || name.isBlank()) null
        else {
            val extras = mutableListOf<ProductUnitInput>()
            var validExtras = true
            if (packEnabled) {
                if (packName.isBlank() || conversion == null || conversion <= 0 || packCents == null) validExtras = false
                else extras += ProductUnitInput(packName.trim(), conversion, packCents, wholeQuantityOnly = true)
            }
            if (caseEnabled) {
                if (caseName.isBlank() || caseConversion == null || caseConversion <= 0 || caseCents == null) validExtras = false
                else extras += ProductUnitInput(caseName.trim(), caseConversion, caseCents, wholeQuantityOnly = true)
            }
            val extra = if (validExtras) extras else null
            extra?.let {
                NewProduct(
                    name = name,
                    barcode = barcode,
                    categoryName = category,
                    baseUnitLabel = unit,
                    baseUnitPriceCentavos = priceCents,
                    purchaseCostCentavos = costCents,
                    initialStockMilliUnits = if (initial == null) stockUnits else 0,
                    minimumStockMilliUnits = lowUnits,
                    wholeBaseUnitsOnly = !fractional,
                    extraUnits = it,
                    imageFileName = imageFileName,
                )
            }
        }
    }

    val requestDismiss = { if (formTouched) confirmDiscard = true else onDismiss() }
    Dialog(onDismissRequest = requestDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.Center) {
            val wideForm = maxWidth >= 740.dp
            val fullScreenForm = maxWidth < 600.dp
            Surface(
                modifier = if (fullScreenForm) Modifier.fillMaxSize() else Modifier.widthIn(max = 780.dp).fillMaxWidth(0.96f).heightIn(max = maxHeight.coerceAtMost(740.dp)),
                shape = if (fullScreenForm) RoundedCornerShape(0.dp) else RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                Column(if (fullScreenForm) Modifier.fillMaxSize() else Modifier.fillMaxWidth().heightIn(max = maxHeight.coerceAtMost(740.dp))) {
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProductDialogIcon(if (initial == null) Icons.Default.Add else Icons.Default.Edit)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(if (initial == null) R.string.add_product else R.string.edit_product), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.product_form_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = requestDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close)) }
                        }
                    }
                    Column(
                        Modifier.weight(1f, fill = fullScreenForm).verticalScroll(rememberScrollState()).padding(horizontal = PosSpacing.section, vertical = PosSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                    ) {
                        ProductFormSection(stringResource(R.string.product_editor_basic), Icons.Default.Info) {
                            ProductField(name, { name = it; formTouched = true }, stringResource(R.string.product_name))
                            ProductField(barcode, { barcode = it; formTouched = true }, stringResource(R.string.barcode))
                            CatalogChoiceField(category, savedCategories, stringResource(R.string.catalog_category), stringResource(R.string.catalog_category_help),
                                { category = it; formTouched = true }, { viewModel.saveCatalogChoice("category", it) }, optional = true)
                            CatalogChoiceField(unit, savedStockUnits, stringResource(R.string.base_unit), stringResource(R.string.catalog_stock_unit_help),
                                { unit = it; formTouched = true }, { viewModel.saveCatalogChoice("stock", it) }, unitLabels = true)
                        }

                        ProductFormSection(stringResource(R.string.product_editor_pricing), Icons.Default.Payments) {
                            Text(stringResource(R.string.catalog_price_help, unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (wideForm) {
                                Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm)) {
                                    ProductField(price, { price = it; formTouched = true }, stringResource(R.string.selling_price), Modifier.weight(1f), KeyboardType.Decimal)
                                    when {
                                        canEditPurchaseCost -> ProductField(cost, { cost = it; formTouched = true }, stringResource(R.string.purchase_cost), Modifier.weight(1f), KeyboardType.Decimal)
                                        initial != null && canViewPurchaseCost -> Text(
                                            stringResource(R.string.purchase_cost) + ": " + PosViewModel.formatPeso(initial.purchaseCostCentavos),
                                            modifier = Modifier.weight(1f).padding(top = 16.dp),
                                        )
                                        else -> Spacer(Modifier.weight(1f))
                                    }
                                }
                            } else {
                                ProductField(price, { price = it; formTouched = true }, stringResource(R.string.selling_price), keyboardType = KeyboardType.Decimal)
                                if (canEditPurchaseCost) ProductField(cost, { cost = it; formTouched = true }, stringResource(R.string.purchase_cost), keyboardType = KeyboardType.Decimal)
                                else if (initial != null && canViewPurchaseCost) Text(stringResource(R.string.purchase_cost) + ": " + PosViewModel.formatPeso(initial.purchaseCostCentavos))
                            }
                            if (canViewPurchaseCost) Text(stringResource(R.string.catalog_cost_help, unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.catalog_stock_help, unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ProductPhotoField(
                                imageFileName = imageFileName,
                                imageStore = imageStore,
                                importing = importingPhoto,
                                error = photoError,
                                onChoose = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onRemove = { imageFileName = null; photoError = false; formTouched = true },
                            )
                            if (initial == null && canAddStock) ProductField(stock, { stock = it; formTouched = true }, stringResource(R.string.initial_stock), keyboardType = KeyboardType.Decimal)
                            if (fullScreenForm) ProductField(lowStock, { lowStock = it; formTouched = true }, stringResource(R.string.low_stock_threshold), keyboardType = KeyboardType.Decimal)
                            else Row(horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                                ProductField(lowStock, { lowStock = it; formTouched = true }, stringResource(R.string.low_stock_threshold), Modifier.weight(1f), KeyboardType.Decimal)
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = fractional, onCheckedChange = { fractional = it; formTouched = true })
                                    Text(stringResource(R.string.allow_fractional), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (fullScreenForm) Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = fractional, onCheckedChange = { fractional = it; formTouched = true })
                                Text(stringResource(R.string.allow_fractional), style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        ProductFormSection(stringResource(R.string.product_editor_units), Icons.Default.Sell) {
                            Text(stringResource(R.string.catalog_selling_unit_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = packEnabled, onCheckedChange = { packEnabled = it; formTouched = true })
                                Text(stringResource(R.string.catalog_first_selling_unit), style = MaterialTheme.typography.bodyMedium)
                            }
                            if (packEnabled) {
                                CatalogChoiceField(packName, savedSellingUnits, stringResource(R.string.catalog_selling_unit_label), stringResource(R.string.catalog_unit_name_help),
                                    { packName = it; formTouched = true }, { viewModel.saveCatalogChoice("selling", it) }, unitLabels = true)
                                ProductField(packCount, { packCount = it; formTouched = true }, stringResource(R.string.catalog_units_inside, unit), keyboardType = KeyboardType.Decimal)
                                ProductField(packPrice, { packPrice = it; formTouched = true }, stringResource(R.string.unit_price), keyboardType = KeyboardType.Decimal)
                                PosViewModel.parseQuantityMilliUnits(packCount)?.takeIf { it > 0 }?.let {
                                    Text(stringResource(R.string.unit_conversion_example, packName.ifBlank { stringResource(R.string.unit_pack) }, PosViewModel.formatQuantity(it), unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = caseEnabled, onCheckedChange = { caseEnabled = it; formTouched = true })
                                Text(stringResource(R.string.catalog_second_selling_unit), style = MaterialTheme.typography.bodyMedium)
                            }
                            if (caseEnabled) {
                                CatalogChoiceField(caseName, savedSellingUnits, stringResource(R.string.catalog_selling_unit_label), stringResource(R.string.catalog_unit_name_help),
                                    { caseName = it; formTouched = true }, { viewModel.saveCatalogChoice("selling", it) }, unitLabels = true)
                                ProductField(caseCount, { caseCount = it; formTouched = true }, stringResource(R.string.catalog_units_inside, unit), keyboardType = KeyboardType.Decimal)
                                ProductField(casePrice, { casePrice = it; formTouched = true }, stringResource(R.string.unit_price), keyboardType = KeyboardType.Decimal)
                                PosViewModel.parseQuantityMilliUnits(caseCount)?.takeIf { it > 0 }?.let {
                                    Text(stringResource(R.string.unit_conversion_example, caseName.ifBlank { stringResource(R.string.unit_case) }, PosViewModel.formatQuantity(it), unit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (attempted && input == null) Text(stringResource(R.string.error_invalid), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = requestDismiss, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.cancel)) }
                            Button(onClick = { attempted = true; input?.let(onSave) }, enabled = input != null && !importingPhoto,
                                shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                                Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                                Text(stringResource(R.string.save), Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmDiscard) AlertDialog(
        onDismissRequest = { confirmDiscard = false },
        containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
        icon = { ProductDialogIcon(Icons.Default.WarningAmber) },
        title = { Text(stringResource(R.string.discard_product_edits_title)) },
        text = { Text(stringResource(R.string.discard_product_edits_body)) },
        confirmButton = {
            TextButton(onClick = { confirmDiscard = false; onDismiss() }) { Text(stringResource(R.string.discard)) }
        },
        dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ProductPhotoField(
    imageFileName: String?,
    imageStore: ProductImageStore,
    importing: Boolean,
    error: Boolean,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductPhoto(imageFileName, Modifier.size(80.dp), imageStore, contentScale = ContentScale.Fit,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .4f), shape = RoundedCornerShape(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.product_photo_optional), Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.product_photo_help), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (error) Text(stringResource(R.string.product_photo_error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                androidx.compose.material3.OutlinedButton(onClick = onChoose, enabled = !importing, shape = RoundedCornerShape(12.dp), modifier = Modifier.heightIn(min = 48.dp)) {
                    if (importing) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text(stringResource(R.string.choose_product_photo))
                }
                if (imageFileName != null) TextButton(onClick = onRemove, enabled = !importing, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.remove_product_photo))
                }
            }
        }
    }
}

@Composable
internal fun ProductPhoto(
    filename: String?,
    modifier: Modifier = Modifier,
    imageStore: ProductImageStore? = null,
    contentScale: ContentScale = ContentScale.Crop,
    containerColor: androidx.compose.ui.graphics.Color? = null,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
) {
    val resolvedImageStore = if (filename.isNullOrBlank() || LocalInspectionMode.current) null else imageStore ?: koinInject()
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, filename, resolvedImageStore) {
        value = if (resolvedImageStore == null || filename.isNullOrBlank()) null
        else withContext(Dispatchers.IO) { resolvedImageStore.decodePreview(filename) }
    }
    Surface(modifier, shape = shape, color = containerColor ?: MaterialTheme.colorScheme.surfaceVariant) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = contentScale)
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProductField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

private fun formatDecimal(centavos: Long): String = java.math.BigDecimal.valueOf(centavos, 2).stripTrailingZeros().toPlainString()
