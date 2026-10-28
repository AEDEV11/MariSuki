package com.aesprt.marisuki.ui.navigation

import android.content.res.Configuration
import java.util.Locale
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import com.aesprt.marisuki.R
import com.aesprt.marisuki.domain.auth.AuthDestination
import com.aesprt.marisuki.feature.auth.AuthGate
import com.aesprt.marisuki.feature.auth.AuthViewModel
import com.aesprt.marisuki.feature.credit.CreditScreen
import com.aesprt.marisuki.feature.more.MoreToolsScreen
import com.aesprt.marisuki.feature.pos.CashierScreen
import com.aesprt.marisuki.feature.pos.PosEvent
import com.aesprt.marisuki.feature.pos.PosMessage
import com.aesprt.marisuki.feature.pos.PosViewModel
import com.aesprt.marisuki.feature.products.ProductsScreen
import org.koin.androidx.compose.koinViewModel

private data class Destination(
    val route: String,
    val label: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val destinations = listOf(
    Destination("sales", R.string.nav_sales, Icons.Filled.PointOfSale),
    Destination("products", R.string.nav_products, Icons.Filled.Inventory2),
    Destination("credit", R.string.nav_credit, Icons.Filled.CreditCard),
    Destination("more", R.string.nav_more, Icons.Filled.MoreHoriz),
)

@Composable
fun MariSukiApp(viewModel: PosViewModel = koinViewModel(), authViewModel: AuthViewModel = koinViewModel()) {
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(authState.destination) {
        val account = when (val destination = authState.destination) {
            is AuthDestination.SignedIn -> destination.account
            else -> null
        }
        viewModel.setAuthenticatedAccount(account)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, authViewModel, authState.destination) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && authState.destination is AuthDestination.SignedIn) {
                authViewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AuthGate(authViewModel, authState.destination) {
        MariSukiPosAppContent(viewModel, authViewModel, authState.defaultCashierScreen)
    }
}

@Composable
private fun MariSukiPosAppContent(viewModel: PosViewModel, authViewModel: AuthViewModel, defaultCashierScreen: String) {
    val language by viewModel.language.collectAsStateWithLifecycle()
    val baseContext = LocalContext.current
    val activityResultRegistryOwner = checkNotNull(LocalActivityResultRegistryOwner.current) {
        "MariSuki must be hosted by an ActivityResultRegistryOwner."
    }
    val baseConfiguration = LocalConfiguration.current
    val localizedConfiguration = remember(language, baseConfiguration) {
        Configuration(baseConfiguration).apply {
            if (language != "system") setLocale(Locale.forLanguageTag(language))
        }
    }
    val localizedContext = remember(baseContext, localizedConfiguration) {
        baseContext.createConfigurationContext(localizedConfiguration)
    }
    CompositionLocalProvider(
        LocalActivityResultRegistryOwner provides activityResultRegistryOwner,
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides localizedContext,
    ) {
        MariSukiAppContent(viewModel, authViewModel, defaultCashierScreen)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MariSukiAppContent(viewModel: PosViewModel, authViewModel: AuthViewModel, defaultCashierScreen: String) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val authUiState by authViewModel.state.collectAsStateWithLifecycle()
    var accountMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val accountName = (authUiState.destination as? AuthDestination.SignedIn)?.account?.displayName.orEmpty()

    LaunchedEffect(viewModel, resources) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is PosEvent.Error -> resources.getString(event.message.stringResourceId())
                is PosEvent.SaleSaved -> resources.getString(
                    if (event.result.creditCentavos > 0) R.string.sale_saved_credit else R.string.sale_saved,
                )
                PosEvent.ProductSaved -> resources.getString(R.string.product_saved)
                PosEvent.CustomerSaved -> resources.getString(R.string.customer_saved)
                PosEvent.StockSaved -> resources.getString(R.string.stock_saved)
                PosEvent.UtangPaymentSaved -> resources.getString(R.string.payment_saved)
                PosEvent.DeliverySaved -> resources.getString(R.string.delivery_saved)
                PosEvent.ExpenseSaved -> resources.getString(R.string.expense_saved)
                PosEvent.RefundSaved -> resources.getString(R.string.refund_saved)
                PosEvent.OwnerPinRejected -> resources.getString(R.string.owner_pin_incorrect)
                PosEvent.OwnerPinSet -> resources.getString(R.string.owner_pin_set)
                PosEvent.BackupSaved -> resources.getString(R.string.backup_saved)
                PosEvent.BackupRestored -> resources.getString(R.string.backup_restored)
                PosEvent.ReportExported -> resources.getString(R.string.report_exported)
            }
            snackbar.showSnackbar(message)
        }
    }

    run {
        val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
        val compact = !windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
        val tabletLandscapeWorkspace = windowSizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND)
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Image(
                                        painter = painterResource(R.drawable.marisuki_logo),
                                        contentDescription = stringResource(R.string.app_name),
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.Center) {
                                Text(
                                    stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    authUiState.storeName.ifBlank { stringResource(R.string.brand_tagline) },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    actions = {
                        if (accountName.isNotBlank()) {
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { accountMenuExpanded = true }
                                        .padding(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.AccountCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            accountName,
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 130.dp)
                                        )
                                    }
                                }
                                DropdownMenu(
                                    expanded = accountMenuExpanded,
                                    onDismissRequest = { accountMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.logout)) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Logout,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = { accountMenuExpanded = false; authViewModel.logout() },
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            },
            bottomBar = {
                if (compact) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 3.dp,
                    ) {
                        destinations.forEach { destination ->
                            val selected = backStack?.destination?.hierarchy?.any { it.route == destination.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navController.navigate(destination.route) { launchSingleTop = true; restoreState = true } },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(stringResource(destination.label)) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { innerPadding ->
            Row(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                verticalAlignment = Alignment.Top,
            ) {
                if (!compact) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        header = {
                            Spacer(modifier = Modifier.height(8.dp))
                        },
                    ) {
                        destinations.forEach { destination ->
                            val selected = backStack?.destination?.hierarchy?.any { it.route == destination.route } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navController.navigate(destination.route) { launchSingleTop = true; restoreState = true } },
                                icon = { Icon(destination.icon, contentDescription = null) },
                                label = { Text(stringResource(destination.label)) },
                                alwaysShowLabel = true,
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            )
                        }
                    }
                }
                NavHost(
                    navController = navController,
                    startDestination = defaultCashierScreen.lowercase().takeIf { it in setOf("sales", "products", "credit") } ?: "sales",
                    modifier = Modifier.weight(1f).fillMaxSize(),
                ) {
                    composable("sales") {
                        CashierScreen(
                            viewModel,
                            expandedTablet = tabletLandscapeWorkspace,
                            mobileCameraScanning = compact,
                        )
                    }
                    composable("products") { ProductsScreen(viewModel) }
                    composable("credit") { CreditScreen(viewModel) }
                    composable("more") { MoreToolsScreen(viewModel, authViewModel) }
                }
            }
        }
    }
}

private fun PosMessage.stringResourceId(): Int = when (this) {
    PosMessage.INVALID -> R.string.error_invalid
    PosMessage.OUT_OF_STOCK -> R.string.error_stock
    PosMessage.CUSTOMER_REQUIRED -> R.string.error_customer
    PosMessage.PRODUCT_UNAVAILABLE -> R.string.error_product
    PosMessage.PAYMENT_INVALID -> R.string.error_payment
    PosMessage.DUPLICATE -> R.string.error_duplicate
    PosMessage.ACCESS_DENIED -> R.string.error_access_denied
    PosMessage.GENERAL -> R.string.error_general
}
