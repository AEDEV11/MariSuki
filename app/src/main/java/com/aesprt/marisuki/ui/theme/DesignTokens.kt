package com.aesprt.marisuki.ui.theme

import androidx.compose.ui.unit.dp

/** Shared spacing and sizing values for the cashier and store-management UI. */
object PosSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val screen = 16.dp
    val section = 20.dp
    val major = 24.dp
    val xl = 32.dp
}

object PosLayout {
    val compactScreenPadding = 16.dp
    val tabletScreenPadding = 24.dp
    val tabletPaneGap = 16.dp
    val formMaxWidth = 640.dp
    val settingsMaxWidth = 960.dp
    val minimumTouchTarget = 48.dp
    
    // Cashier 2-pane ratio weights on tablet landscape
    val catalogWeight = 0.62f
    val checkoutWeight = 0.38f
    
    // Product grid sizes
    val minProductCardWidth = 140.dp
    val productCardHeight = 180.dp
}

object PosElevation {
    val flat = 0.dp
    val card = 1.dp
    val raised = 3.dp
    val sheet = 8.dp
    val dialog = 12.dp
}

object PosBorders {
    val thin = 1.dp
    val focus = 2.dp
}
