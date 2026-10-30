package com.aesprt.marisuki.feature.shift

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aesprt.marisuki.domain.model.CashMovementType
import com.aesprt.marisuki.domain.model.PHILIPPINE_PESO_DENOMINATIONS
import com.aesprt.marisuki.domain.model.ReconciliationStatus
import com.aesprt.marisuki.domain.model.ShiftGateState
import com.aesprt.marisuki.domain.model.ShiftReconciliationCalculator
import com.aesprt.marisuki.domain.model.ShiftReportStatus
import com.aesprt.marisuki.domain.model.ShiftSummary
import com.aesprt.marisuki.feature.pos.PosViewModel
import com.aesprt.marisuki.ui.components.PosEmptyState
import com.aesprt.marisuki.ui.components.PosStatusBadge
import com.aesprt.marisuki.ui.components.ToolCard
import com.aesprt.marisuki.ui.components.ToolChip
import com.aesprt.marisuki.ui.components.ToolDialog
import com.aesprt.marisuki.ui.components.ToolSectionHeader
import com.aesprt.marisuki.ui.theme.PosRadius
import com.aesprt.marisuki.ui.theme.PosSpacing
import com.aesprt.marisuki.ui.theme.StatusSuccess
import com.aesprt.marisuki.ui.theme.StockAmber
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartShiftScreen(
    state: ShiftGateState.NoActiveShift,
    viewModel: PosViewModel,
    onLogout: () -> Unit,
) {
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val shiftError by viewModel.shiftError.collectAsStateWithLifecycle()
    var openingCashText by rememberSaveable { mutableStateOf("2000.00") }
    var showConfirmStart by rememberSaveable { mutableStateOf(false) }
    var operationKey by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }

    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(15_000L)
        }
    }

    val zone = ZoneId.systemDefault()
    val formattedDate = remember(state.businessDate) {
        runCatching {
            LocalDate.parse(state.businessDate)
                .format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))
        }.getOrDefault(state.businessDate)
    }
    val formattedTime = remember(nowMillis) {
        Instant.ofEpochMilli(nowMillis)
            .atZone(zone)
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    }
    val parsedOpeningCentavos = PosViewModel.parseCentavos(openingCashText)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(PosSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        ToolCard(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(PosSpacing.major),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
            ) {
                ToolSectionHeader(
                    title = "Start Cashier Shift",
                    icon = Icons.Default.PointOfSale,
                    subtitle = "Confirm the opening cash drawer balance before processing sales.",
                )

                Surface(
                    shape = RoundedCornerShape(PosRadius.md),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(PosSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ShiftMetaRow("Cashier Name", state.cashierName)
                        ShiftMetaRow("Cashier ID", formatCashierCode(state.cashierId))
                        ShiftMetaRow("POS Terminal", state.terminalId)
                        ShiftMetaRow("Business Date", formattedDate)
                        ShiftMetaRow("Current Time", formattedTime)
                    }
                }

                OutlinedTextField(
                    value = openingCashText,
                    onValueChange = {
                        viewModel.clearShiftError()
                        openingCashText = it
                    },
                    label = { Text("Opening Cash Drawer Balance (₱)") },
                    supportingText = {
                        Text("Actual physical bills and coins placed in the drawer before sales begin.")
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = openingCashText.isNotBlank() && parsedOpeningCentavos == null,
                    modifier = Modifier.fillMaxWidth(),
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "₱0" to "0.00",
                        "₱1,000" to "1000.00",
                        "₱2,000" to "2000.00",
                        "₱3,000" to "3000.00",
                        "₱5,000" to "5000.00",
                    ).forEach { (label, amountValue) ->
                        ToolChip(
                            selected = openingCashText.trim() == amountValue,
                            onClick = { openingCashText = amountValue },
                            label = { Text(label) },
                        )
                    }
                }

                if (!shiftError.isNullOrBlank()) {
                    Text(
                        text = shiftError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                ) {
                    OutlinedButton(
                        onClick = onLogout,
                        enabled = !isWorking,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(PosRadius.sm),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Logout")
                    }
                    Button(
                        onClick = { showConfirmStart = true },
                        enabled = !isWorking && parsedOpeningCentavos != null,
                        modifier = Modifier
                            .weight(1.4f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(PosRadius.sm),
                    ) {
                        Icon(Icons.Default.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Start Shift")
                    }
                }
            }
        }
    }

    if (showConfirmStart && parsedOpeningCentavos != null) {
        ToolDialog(
            onDismissRequest = { if (!isWorking) showConfirmStart = false },
            title = { Text("Confirm Opening Cash") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Please confirm the starting cash drawer balance for this shift:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        shape = RoundedCornerShape(PosRadius.sm),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(PosSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ShiftMetaRow("Cashier", state.cashierName)
                            ShiftMetaRow("Business Date", formattedDate)
                            ShiftMetaRow("Terminal", state.terminalId)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            ShiftMetaRow(
                                label = "Opening Cash",
                                value = PosViewModel.formatPeso(parsedOpeningCentavos),
                                highlight = true,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.startShift(
                            openingCashText = openingCashText,
                            terminalId = state.terminalId,
                            idempotencyKey = operationKey,
                            onStarted = {
                                showConfirmStart = false
                                operationKey = UUID.randomUUID().toString()
                            },
                        )
                    },
                    enabled = !isWorking,
                ) {
                    Text("Confirm & Open Shift")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showConfirmStart = false },
                    enabled = !isWorking,
                ) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun PreviousShiftClosureGateScreen(
    state: ShiftGateState.PreviousShiftOpen,
    viewModel: PosViewModel,
    onLogout: () -> Unit,
) {
    BackHandler(enabled = true) {
        // Mandatory gate: back navigation cannot bypass previous shift closure
    }

    var showEndShiftDialog by rememberSaveable { mutableStateOf(false) }
    val formattedPrevDate = remember(state.summary.businessDate) {
        runCatching {
            LocalDate.parse(state.summary.businessDate)
                .format(DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US))
        }.getOrDefault(state.summary.businessDate)
    }
    val zone = ZoneId.systemDefault()
    val startedFormatted = remember(state.summary.startedAt) {
        Instant.ofEpochMilli(state.summary.startedAt)
            .atZone(zone)
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(PosSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        ToolCard(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(PosSpacing.major),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.md),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(PosRadius.md),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.LockClock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mandatory Previous Shift Closure",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        PosStatusBadge(
                            text = "PREVIOUS SHIFT STILL OPEN",
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(PosRadius.md),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                ) {
                    Text(
                        text = "Your previous shift from $formattedPrevDate is still open. You must close and reconcile this shift before starting a new shift.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(PosSpacing.md),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(PosRadius.md),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(PosSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ShiftMetaRow("Shift ID", state.summary.shiftId)
                        ShiftMetaRow("Cashier", state.summary.cashierName)
                        ShiftMetaRow("Cashier ID", formatCashierCode(state.summary.cashierId))
                        ShiftMetaRow("Business Date", formattedPrevDate)
                        ShiftMetaRow("Shift Started", startedFormatted)
                        ShiftMetaRow("Completed Sales", state.summary.transactionCount.toString())
                        ShiftMetaRow("Opening Cash", PosViewModel.formatPeso(state.summary.openingCashCentavos))
                        ShiftMetaRow("Net Sales", PosViewModel.formatPeso(state.summary.netSalesCentavos))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        ShiftMetaRow(
                            label = "Expected Drawer Cash",
                            value = PosViewModel.formatPeso(state.summary.expectedCashCentavos),
                            highlight = true,
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                ) {
                    OutlinedButton(
                        onClick = onLogout,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(PosRadius.sm),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Logout")
                    }
                    Button(
                        onClick = { showEndShiftDialog = true },
                        modifier = Modifier
                            .weight(1.5f)
                            .heightIn(min = 48.dp),
                        shape = RoundedCornerShape(PosRadius.sm),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(Icons.Default.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Close Previous Shift")
                    }
                }
            }
        }
    }

    if (showEndShiftDialog) {
        EndShiftReconciliationDialog(
            summary = state.summary,
            viewModel = viewModel,
            onDismiss = { showEndShiftDialog = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveShiftStatusBanner(
    summary: ShiftSummary,
    onOpenDetails: () -> Unit,
    onOpenCashMovement: () -> Unit,
    onOpenEndShift: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val startedTime = remember(summary.startedAt) {
        Instant.ofEpochMilli(summary.startedAt)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    }
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PosRadius.md),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Top row: Shift status + Cashier & start info on left, Expected Drawer on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PosStatusBadge(
                    text = "SHIFT OPEN",
                    containerColor = StatusSuccess.copy(alpha = 0.16f),
                    contentColor = StatusSuccess,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = summary.cashierName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Started $startedTime • Opening ${PosViewModel.formatPeso(summary.openingCashCentavos)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (summary.financialTotalsVisible) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            text = "Drawer",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            text = PosViewModel.formatPeso(summary.expectedCashCentavos),
                            style = MaterialTheme.typography.titleSmall.copy(fontFeatureSettings = "tnum"),
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                        )
                    }
                }
            }

            HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.6f))

            // Bottom row: 3 equal-width compact action buttons that never crush text on phone or tablet
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onOpenCashMovement,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp),
                    shape = RoundedCornerShape(PosRadius.sm),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Cash In/Out",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onOpenDetails,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp),
                    shape = RoundedCornerShape(PosRadius.sm),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Shift Info",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = onOpenEndShift,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 38.dp),
                    shape = RoundedCornerShape(PosRadius.sm),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primaryContainer,
                        contentColor = colors.onPrimaryContainer,
                    ),
                ) {
                    Text(
                        text = "End Shift",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecordCashMovementDialog(
    summary: ShiftSummary,
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
) {
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val shiftError by viewModel.shiftError.collectAsStateWithLifecycle()
    var selectedType by rememberSaveable { mutableStateOf(CashMovementType.CASH_IN) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var operationKey by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }

    val parsedAmount = PosViewModel.parseCentavos(amountText)
    val canSave = !isWorking && parsedAmount != null && parsedAmount > 0L && reason.isNotBlank()

    ToolDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text("Record Drawer Cash Movement") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Shift ${summary.shiftId} • ${summary.cashierName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        CashMovementType.CASH_IN to "Cash In (+)",
                        CashMovementType.CASH_OUT to "Cash Out (-)",
                        CashMovementType.ADJUSTMENT to "Adjustment",
                    ).forEach { (type, label) ->
                        ToolChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        viewModel.clearShiftError()
                        amountText = it
                    },
                    label = { Text("Amount (₱)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        viewModel.clearShiftError()
                        reason = it
                    },
                    label = { Text("Reason / Description (Required)") },
                    placeholder = {
                        Text(
                            when (selectedType) {
                                CashMovementType.CASH_IN -> "e.g., Added change coins to drawer"
                                CashMovementType.CASH_OUT -> "e.g., Safe drop / Petty cash payout"
                                CashMovementType.ADJUSTMENT -> "e.g., Authorised drawer adjustment"
                            },
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!shiftError.isNullOrBlank()) {
                    Text(
                        text = shiftError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.recordShiftCashMovement(
                        shiftId = summary.shiftId,
                        movementType = selectedType,
                        amountText = amountText,
                        reason = reason,
                        idempotencyKey = operationKey,
                        onRecorded = {
                            operationKey = UUID.randomUUID().toString()
                            onDismiss()
                        },
                    )
                },
                enabled = canSave,
            ) {
                Text("Save Movement")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isWorking) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun ShiftDetailsDialog(
    summary: ShiftSummary,
    onRecordMovement: () -> Unit,
    onEndShift: () -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val dateTimeFmt = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US)
    val startedText = remember(summary.startedAt) {
        Instant.ofEpochMilli(summary.startedAt).atZone(zone).format(dateTimeFmt)
    }

    ToolDialog(
        onDismissRequest = onDismiss,
        title = { Text("Active Shift Management") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(PosRadius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ShiftMetaRow("Shift ID", summary.shiftId)
                        ShiftMetaRow("Cashier", summary.cashierName)
                        ShiftMetaRow("Cashier ID", formatCashierCode(summary.cashierId))
                        ShiftMetaRow("Terminal", summary.terminalId)
                        ShiftMetaRow("Business Date", summary.businessDate)
                        ShiftMetaRow("Started", startedText)
                        ShiftMetaRow("Completed Transactions", summary.transactionCount.toString())
                    }
                }

                if (summary.financialTotalsVisible) {
                    Surface(
                        shape = RoundedCornerShape(PosRadius.sm),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "Sales & Drawer Breakdown",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            ShiftMetaRow("Opening Balance", PosViewModel.formatPeso(summary.openingCashCentavos))
                            ShiftMetaRow("Gross Sales", PosViewModel.formatPeso(summary.grossSalesCentavos))
                            ShiftMetaRow("Refunds & Returns", PosViewModel.formatPeso(summary.refundsCentavos))
                            ShiftMetaRow("Net Sales", PosViewModel.formatPeso(summary.netSalesCentavos), highlight = true)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            ShiftMetaRow("Cash Sales", PosViewModel.formatPeso(summary.cashPaymentsCentavos))
                            ShiftMetaRow("Non-Cash Sales (GCash / Maya)", PosViewModel.formatPeso(summary.nonCashPaymentsCentavos))
                            ShiftMetaRow("Credit / Utang Sales", PosViewModel.formatPeso(summary.creditSalesCentavos))
                            ShiftMetaRow("Cash In Movements", PosViewModel.formatPeso(summary.cashInCentavos))
                            ShiftMetaRow("Cash Out Movements", PosViewModel.formatPeso(summary.cashOutCentavos))
                            ShiftMetaRow("Cash Refunds", PosViewModel.formatPeso(summary.cashRefundsCentavos))
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            ShiftMetaRow(
                                label = "Expected Cash in Drawer",
                                value = PosViewModel.formatPeso(summary.expectedCashCentavos),
                                highlight = true,
                            )
                        }
                    }
                }

                if (summary.movements.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Recorded Drawer Movements (${summary.movements.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        summary.movements.forEach { movement ->
                            ShiftMetaRow(
                                label = "${movement.movementType.name}: ${movement.reason}",
                                value = PosViewModel.formatPeso(movement.amountCentavos),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRecordMovement) {
                    Text("Cash In/Out")
                }
                Button(onClick = onEndShift) {
                    Text("End Shift")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EndShiftReconciliationDialog(
    summary: ShiftSummary,
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
) {
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val shiftError by viewModel.shiftError.collectAsStateWithLifecycle()

    var actualCashText by rememberSaveable(summary.shiftId) {
        mutableStateOf(PosViewModel.formatAmountInput(summary.expectedCashCentavos))
    }
    var useDenominationCounter by rememberSaveable { mutableStateOf(false) }
    val denominationCounts = remember { mutableStateMapOf<String, Int>() }
    var closingRemarks by rememberSaveable { mutableStateOf("") }
    var ownerPin by rememberSaveable { mutableStateOf("") }
    var showConfirmClose by rememberSaveable { mutableStateOf(false) }
    var closeOperationKey by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }

    val denominationTotalCentavos = remember(denominationCounts.toMap()) {
        ShiftReconciliationCalculator.calculateDenominationTotalCentavos(denominationCounts)
    }

    LaunchedEffect(useDenominationCounter, denominationTotalCentavos) {
        if (useDenominationCounter) {
            actualCashText = PosViewModel.formatAmountInput(denominationTotalCentavos)
        }
    }

    val actualCashCentavos = PosViewModel.parseCentavos(actualCashText)
    val reconciliation = remember(summary.expectedCashCentavos, actualCashCentavos) {
        actualCashCentavos?.let { actual ->
            ShiftReconciliationCalculator.reconcile(
                expectedCashCentavos = summary.expectedCashCentavos,
                actualCashCentavos = actual,
            )
        }
    }

    val requiresRemarks = reconciliation != null && reconciliation.status != ReconciliationStatus.BALANCED
    val canProceedToConfirm = !isWorking &&
        actualCashCentavos != null &&
        (!requiresRemarks || closingRemarks.trim().isNotEmpty())

    val zone = ZoneId.systemDefault()
    val dateTimeFmt = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US)
    val startFormatted = remember(summary.startedAt) {
        Instant.ofEpochMilli(summary.startedAt).atZone(zone).format(dateTimeFmt)
    }
    val closeFormatted = remember {
        Instant.now().atZone(zone).format(dateTimeFmt)
    }

    ToolDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        title = { Text("End Shift & Cash Reconciliation") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Step 1 — Shift Summary
                Surface(
                    shape = RoundedCornerShape(PosRadius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Step 1 — Review Shift Summary",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        ShiftMetaRow("Cashier", summary.cashierName)
                        ShiftMetaRow("Shift ID", summary.shiftId)
                        ShiftMetaRow("Business Date", summary.businessDate)
                        ShiftMetaRow("Shift Start", startFormatted)
                        ShiftMetaRow("Closing Time", closeFormatted)
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        ShiftMetaRow("Opening Cash", PosViewModel.formatPeso(summary.openingCashCentavos))
                        ShiftMetaRow("Gross Sales (${summary.transactionCount} txns)", PosViewModel.formatPeso(summary.grossSalesCentavos))
                        ShiftMetaRow("Discounts", PosViewModel.formatPeso(summary.discountsCentavos))
                        ShiftMetaRow("Refunds", PosViewModel.formatPeso(summary.refundsCentavos))
                        ShiftMetaRow("Net Sales", PosViewModel.formatPeso(summary.netSalesCentavos), highlight = true)
                        ShiftMetaRow("Cash Payments", PosViewModel.formatPeso(summary.cashPaymentsCentavos))
                        ShiftMetaRow("Non-Cash Payments", PosViewModel.formatPeso(summary.nonCashPaymentsCentavos))
                        ShiftMetaRow("Credit / Utang Sales", PosViewModel.formatPeso(summary.creditSalesCentavos))
                        ShiftMetaRow("Cash In", PosViewModel.formatPeso(summary.cashInCentavos))
                        ShiftMetaRow("Cash Out", PosViewModel.formatPeso(summary.cashOutCentavos))
                        ShiftMetaRow("Cash Refunds", PosViewModel.formatPeso(summary.cashRefundsCentavos))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        ShiftMetaRow(
                            label = "Expected Cash in Drawer",
                            value = PosViewModel.formatPeso(summary.expectedCashCentavos),
                            highlight = true,
                        )
                    }
                }

                // Step 2 — Count Cash Drawer
                Surface(
                    shape = RoundedCornerShape(PosRadius.sm),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Step 2 — Count Physical Cash Drawer",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            ToolChip(
                                selected = useDenominationCounter,
                                onClick = { useDenominationCounter = !useDenominationCounter },
                                label = {
                                    Text(if (useDenominationCounter) "Hide Denomination Counter" else "Use Denomination Counter")
                                },
                            )
                        }

                        OutlinedTextField(
                            value = actualCashText,
                            onValueChange = {
                                viewModel.clearShiftError()
                                useDenominationCounter = false
                                actualCashText = it
                            },
                            label = { Text("Actual Cash Counted (₱)") },
                            supportingText = {
                                Text("Enter total physical cash in drawer or use the Philippine Peso denomination counter.")
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = actualCashText.isNotBlank() && actualCashCentavos == null,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (useDenominationCounter) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "Philippine Peso Bills & Coins",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    TextButton(onClick = { denominationCounts.clear() }) {
                                        Text("Reset Counts")
                                    }
                                }
                                PHILIPPINE_PESO_DENOMINATIONS.forEach { spec ->
                                    val count = denominationCounts[spec.key] ?: 0
                                    val lineTotal = spec.unitCentavos * count.toLong()
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = spec.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.width(84.dp),
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (count > 0) denominationCounts[spec.key] = count - 1
                                                },
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(Icons.Default.Remove, contentDescription = "Decrease")
                                            }
                                            OutlinedTextField(
                                                value = if (count == 0) "" else count.toString(),
                                                onValueChange = { text ->
                                                    val parsed = text.filter(Char::isDigit).take(5).toIntOrNull() ?: 0
                                                    denominationCounts[spec.key] = parsed
                                                },
                                                placeholder = { Text("0") },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                modifier = Modifier.width(74.dp),
                                            )
                                            IconButton(
                                                onClick = {
                                                    denominationCounts[spec.key] = (count + 1).coerceAtMost(99_999)
                                                },
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(Icons.Default.Add, contentDescription = "Increase")
                                            }
                                        }
                                        Text(
                                            text = PosViewModel.formatPeso(lineTotal),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Step 3 — Cash Reconciliation & Short/Over Detection
                if (reconciliation != null) {
                    val statusColor = reconciliationStatusColor(reconciliation.status)
                    Surface(
                        shape = RoundedCornerShape(PosRadius.sm),
                        color = statusColor.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.45f)),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Step 3 — Cash Reconciliation",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                                ReconciliationStatusBadge(reconciliation.status)
                            }
                            ShiftMetaRow("Expected Cash", PosViewModel.formatPeso(reconciliation.expectedCashCentavos))
                            ShiftMetaRow("Actual Cash Counted", PosViewModel.formatPeso(reconciliation.actualCashCentavos))
                            ShiftMetaRow(
                                label = when (reconciliation.status) {
                                    ReconciliationStatus.BALANCED -> "Variance (Balanced)"
                                    ReconciliationStatus.SHORT -> "Cash Shortage"
                                    ReconciliationStatus.OVER -> "Cash Overage"
                                    ReconciliationStatus.PENDING -> "Variance"
                                },
                                value = formatSignedVariance(reconciliation.varianceCentavos),
                                highlight = true,
                                valueColor = statusColor,
                            )
                        }
                    }

                    OutlinedTextField(
                        value = closingRemarks,
                        onValueChange = {
                            viewModel.clearShiftError()
                            closingRemarks = it
                        },
                        label = {
                            Text(
                                if (requiresRemarks) "Reason for Cash Variance (Required)"
                                else "Closing Remarks / Notes (Optional)",
                            )
                        },
                        placeholder = {
                            Text(
                                if (requiresRemarks) "Explain the cash shortage or overage..."
                                else "Optional shift handover notes",
                            )
                        },
                        isError = requiresRemarks && closingRemarks.isBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (requiresRemarks) {
                        OutlinedTextField(
                            value = ownerPin,
                            onValueChange = {
                                viewModel.clearShiftError()
                                ownerPin = it
                            },
                            label = { Text("Manager / Owner PIN Approval (Optional)") },
                            supportingText = {
                                Text("Enter Owner PIN if manager sign-off is required for this variance.")
                            },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (!shiftError.isNullOrBlank()) {
                    Text(
                        text = shiftError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { showConfirmClose = true },
                enabled = canProceedToConfirm,
            ) {
                Text("Review & End Shift")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isWorking) {
                Text("Cancel")
            }
        },
    )

    if (showConfirmClose && reconciliation != null && actualCashCentavos != null) {
        ConfirmEndShiftDialog(
            expectedCashCentavos = reconciliation.expectedCashCentavos,
            actualCashCentavos = reconciliation.actualCashCentavos,
            varianceCentavos = reconciliation.varianceCentavos,
            status = reconciliation.status,
            isWorking = isWorking,
            onCancel = { showConfirmClose = false },
            onConfirm = {
                viewModel.closeShiftAndGenerateReport(
                    shiftId = summary.shiftId,
                    actualCashCentavos = actualCashCentavos,
                    closingRemarks = closingRemarks.trim().ifBlank { null },
                    ownerPin = ownerPin.trim().ifBlank { null },
                    idempotencyKey = closeOperationKey,
                    onCompleted = {
                        showConfirmClose = false
                        closeOperationKey = UUID.randomUUID().toString()
                        onDismiss()
                    },
                )
            },
        )
    }
}

@Composable
fun ConfirmEndShiftDialog(
    expectedCashCentavos: Long,
    actualCashCentavos: Long,
    varianceCentavos: Long,
    status: ReconciliationStatus,
    isWorking: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val statusColor = reconciliationStatusColor(status)
    ToolDialog(
        onDismissRequest = { if (!isWorking) onCancel() },
        title = { Text("Confirm End Shift") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Please verify your cash count and shift totals. Once closed, this shift cannot be reopened through normal cashier operations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    shape = RoundedCornerShape(PosRadius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ShiftMetaRow("Expected Cash", PosViewModel.formatPeso(expectedCashCentavos))
                        ShiftMetaRow("Actual Cash", PosViewModel.formatPeso(actualCashCentavos))
                        ShiftMetaRow(
                            label = "Variance",
                            value = formatSignedVariance(varianceCentavos),
                            highlight = true,
                            valueColor = statusColor,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Reconciliation Status",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ReconciliationStatusBadge(status)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isWorking,
            ) {
                Text("Confirm End Shift")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isWorking,
            ) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun EndShiftReportSummaryDialog(
    summary: ShiftSummary,
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()
    val zone = ZoneId.systemDefault()
    val dateTimeFmt = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US)
    val startedText = remember(summary.startedAt) {
        Instant.ofEpochMilli(summary.startedAt).atZone(zone).format(dateTimeFmt)
    }
    val endedText = remember(summary.endedAt) {
        summary.endedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(dateTimeFmt) } ?: "—"
    }
    val statusColor = reconciliationStatusColor(summary.reconciliationStatus)
    val fileName = summary.reportFileName ?: EndShiftReportPdfExporter.buildDeterministicFileName(summary)

    ToolDialog(
        onDismissRequest = onDismiss,
        title = { Text("End Shift Summary Report") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(PosRadius.sm),
                    color = statusColor.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = summary.shiftId,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Business Date: ${summary.businessDate} • ${summary.cashierName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        ReconciliationStatusBadge(summary.reconciliationStatus)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(PosRadius.sm),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ShiftMetaRow("Terminal", summary.terminalId)
                        ShiftMetaRow("Shift Start", startedText)
                        ShiftMetaRow("Shift End", endedText)
                        ShiftMetaRow("Completed Sales", summary.transactionCount.toString())
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        ShiftMetaRow("Gross Sales", PosViewModel.formatPeso(summary.grossSalesCentavos))
                        ShiftMetaRow("Discounts", PosViewModel.formatPeso(summary.discountsCentavos))
                        ShiftMetaRow("Refunds", PosViewModel.formatPeso(summary.refundsCentavos))
                        ShiftMetaRow("Net Sales", PosViewModel.formatPeso(summary.netSalesCentavos), highlight = true)
                        ShiftMetaRow("Cash Sales", PosViewModel.formatPeso(summary.cashPaymentsCentavos))
                        ShiftMetaRow("GCash Payments", PosViewModel.formatPeso(summary.gcashPaymentsCentavos))
                        ShiftMetaRow("Maya Payments", PosViewModel.formatPeso(summary.mayaPaymentsCentavos))
                        ShiftMetaRow("Credit / Utang Sales", PosViewModel.formatPeso(summary.creditSalesCentavos))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        ShiftMetaRow("Opening Cash", PosViewModel.formatPeso(summary.openingCashCentavos))
                        ShiftMetaRow("Cash In", PosViewModel.formatPeso(summary.cashInCentavos))
                        ShiftMetaRow("Cash Out", PosViewModel.formatPeso(summary.cashOutCentavos))
                        ShiftMetaRow("Cash Refunds", PosViewModel.formatPeso(summary.cashRefundsCentavos))
                        ShiftMetaRow("Expected Cash", PosViewModel.formatPeso(summary.expectedCashCentavos), highlight = true)
                        ShiftMetaRow(
                            label = "Actual Cash Counted",
                            value = PosViewModel.formatPeso(summary.actualCashCentavos ?: summary.expectedCashCentavos),
                            highlight = true,
                        )
                        ShiftMetaRow(
                            label = "Variance",
                            value = formatSignedVariance(summary.varianceCentavos ?: 0L),
                            highlight = true,
                            valueColor = statusColor,
                        )
                        if (!summary.closingRemarks.isNullOrBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            ShiftMetaRow("Remarks", summary.closingRemarks)
                        }
                    }
                }

                // PDF Export Status Card
                Surface(
                    shape = RoundedCornerShape(PosRadius.sm),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "PDF Report (${summary.reportStatus?.name ?: "GENERATED"})",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "${EndShiftReportPdfExporter.PUBLIC_RELATIVE_PATH}$fileName",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.regenerateShiftReportPdf(summary.shiftId) {
                                        Toast.makeText(context, "PDF report regenerated in Documents/EndShiftReport/", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isWorking,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Regenerate PDF",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val intent = viewModel.buildOpenShiftPdfIntent(summary)
                                    if (intent != null) {
                                        runCatching { context.startActivity(intent) }.onFailure {
                                            Toast.makeText(context, "No PDF viewer app found. Use Share PDF.", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        viewModel.regenerateShiftReportPdf(summary.shiftId) { regenerated ->
                                            if (regenerated != null) {
                                                Toast.makeText(context, "PDF regenerated in Documents/EndShiftReport/", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(PosRadius.sm),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Open PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = viewModel.buildShareShiftPdfIntent(summary)
                                    if (intent != null) {
                                        runCatching { context.startActivity(intent) }.onFailure {
                                            Toast.makeText(context, "Unable to share PDF report.", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Regenerating PDF report first...", Toast.LENGTH_SHORT).show()
                                        viewModel.regenerateShiftReportPdf(summary.shiftId)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(PosRadius.sm),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftReportsScreen(viewModel: PosViewModel) {
    val context = LocalContext.current
    val reports by viewModel.historicalShiftReports.collectAsStateWithLifecycle()
    val isWorking by viewModel.isWorking.collectAsStateWithLifecycle()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var dateFilter by rememberSaveable { mutableStateOf("ALL") }
    var statusFilter by rememberSaveable { mutableStateOf("ALL") }
    var selectedReport by remember { mutableStateOf<ShiftSummary?>(null) }

    val today = remember { LocalDate.now(ZoneId.systemDefault()) }
    val filteredReports = remember(reports, searchQuery, dateFilter, statusFilter) {
        reports.filter { item ->
            val matchesQuery = searchQuery.isBlank() ||
                item.shiftId.contains(searchQuery, ignoreCase = true) ||
                item.cashierName.contains(searchQuery, ignoreCase = true) ||
                item.cashierId.contains(searchQuery, ignoreCase = true) ||
                item.businessDate.contains(searchQuery, ignoreCase = true)

            val matchesStatus = statusFilter == "ALL" || item.reconciliationStatus.name == statusFilter

            val itemDate = runCatching { LocalDate.parse(item.businessDate) }.getOrNull()
            val matchesDate = when (dateFilter) {
                "TODAY" -> itemDate == today
                "WEEK" -> itemDate != null && !itemDate.isBefore(today.minusDays(7))
                "MONTH" -> itemDate != null && itemDate.year == today.year && itemDate.month == today.month
                else -> true
            }
            matchesQuery && matchesStatus && matchesDate
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
    ) {
        ToolCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(PosSpacing.md),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search by Shift ID, Cashier, or Date (YYYY-MM-DD)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "ALL" to "All Dates",
                        "TODAY" to "Today",
                        "WEEK" to "Last 7 Days",
                        "MONTH" to "This Month",
                    ).forEach { (key, label) ->
                        ToolChip(
                            selected = dateFilter == key,
                            onClick = { dateFilter = key },
                            label = { Text(label) },
                        )
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(
                        "ALL" to "All Statuses",
                        "BALANCED" to "Balanced",
                        "SHORT" to "Short",
                        "OVER" to "Over",
                    ).forEach { (key, label) ->
                        ToolChip(
                            selected = statusFilter == key,
                            onClick = { statusFilter = key },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }

        if (filteredReports.isEmpty()) {
            PosEmptyState(
                icon = Icons.Default.History,
                title = "No Shift Reports Found",
                subtitle = "Closed cashier shifts and PDF reconciliation reports saved to Documents/EndShiftReport/ will appear here.",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(PosSpacing.sm),
                contentPadding = PaddingValues(bottom = PosSpacing.section),
            ) {
                items(filteredReports, key = { it.shiftId }) { item ->
                    HistoricalShiftReportCard(
                        summary = item,
                        isWorking = isWorking,
                        onView = { selectedReport = item },
                        onOpenPdf = {
                            val intent = viewModel.buildOpenShiftPdfIntent(item)
                            if (intent != null) {
                                runCatching { context.startActivity(intent) }.onFailure {
                                    Toast.makeText(context, "No PDF viewer installed. Use Share PDF.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                viewModel.regenerateShiftReportPdf(item.shiftId) { regenerated ->
                                    if (regenerated != null) {
                                        Toast.makeText(context, "PDF regenerated in Documents/EndShiftReport/", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        onSharePdf = {
                            val intent = viewModel.buildShareShiftPdfIntent(item)
                            if (intent != null) {
                                runCatching { context.startActivity(intent) }.onFailure {
                                    Toast.makeText(context, "Unable to open share sheet.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                viewModel.regenerateShiftReportPdf(item.shiftId) {
                                    Toast.makeText(context, "PDF regenerated. Tap Share again.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onRegeneratePdf = {
                            viewModel.regenerateShiftReportPdf(item.shiftId) {
                                Toast.makeText(context, "PDF report regenerated in Documents/EndShiftReport/", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
    }

    selectedReport?.let { current ->
        val latest = reports.find { it.shiftId == current.shiftId } ?: current
        EndShiftReportSummaryDialog(
            summary = latest,
            viewModel = viewModel,
            onDismiss = { selectedReport = null },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoricalShiftReportCard(
    summary: ShiftSummary,
    isWorking: Boolean,
    onView: () -> Unit,
    onOpenPdf: () -> Unit,
    onSharePdf: () -> Unit,
    onRegeneratePdf: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val timeFmt = DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US)
    val startText = remember(summary.startedAt) {
        Instant.ofEpochMilli(summary.startedAt).atZone(zone).format(timeFmt)
    }
    val endText = remember(summary.endedAt) {
        summary.endedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(timeFmt) } ?: "—"
    }
    val statusColor = reconciliationStatusColor(summary.reconciliationStatus)

    ToolCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(PosSpacing.md),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${summary.businessDate}  •  ${summary.shiftId}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Cashier: ${summary.cashierName} (${formatCashierCode(summary.cashierId)})  •  $startText – $endText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ReconciliationStatusBadge(summary.reconciliationStatus)
            }

            HorizontalDivider()

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MetricColumn("Opening Cash", PosViewModel.formatPeso(summary.openingCashCentavos))
                MetricColumn("Net Sales (${summary.transactionCount})", PosViewModel.formatPeso(summary.netSalesCentavos))
                MetricColumn("Expected Cash", PosViewModel.formatPeso(summary.expectedCashCentavos))
                MetricColumn("Actual Cash", PosViewModel.formatPeso(summary.actualCashCentavos ?: summary.expectedCashCentavos))
                MetricColumn(
                    label = "Short / Over",
                    value = formatSignedVariance(summary.varianceCentavos ?: 0L),
                    valueColor = statusColor,
                )
            }

            if (!summary.closingRemarks.isNullOrBlank()) {
                Text(
                    text = "Remarks: ${summary.closingRemarks}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                PosStatusBadge(
                    text = when (summary.reportStatus) {
                        ShiftReportStatus.GENERATED -> "PDF SAVED"
                        ShiftReportStatus.REGENERATED -> "PDF REGENERATED"
                        ShiftReportStatus.FAILED -> "PDF NEEDS REGENERATION"
                        null -> "PDF READY"
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onView) {
                        Text("View Report")
                    }
                    TextButton(onClick = onOpenPdf) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Open PDF")
                    }
                    TextButton(onClick = onSharePdf) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Share PDF")
                    }
                    TextButton(onClick = onRegeneratePdf, enabled = !isWorking) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Regenerate")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}

@Composable
fun OwnerShiftOverviewBanner(
    latestShift: ShiftSummary,
    todayClosedCount: Int,
    onViewReport: (ShiftSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
    val endedText = remember(latestShift.endedAt) {
        latestShift.endedAt?.let { Instant.ofEpochMilli(it).atZone(zone).format(timeFormatter) } ?: "Closed"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(PosRadius.md),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PosStatusBadge(
                        text = if (todayClosedCount > 1) "$todayClosedCount SHIFTS CLOSED" else "SHIFT CLOSED",
                        containerColor = StatusSuccess.copy(alpha = 0.14f),
                        contentColor = StatusSuccess,
                    )
                    Text(
                        text = "${latestShift.cashierName} • $endedText",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ReconciliationStatusBadge(latestShift.reconciliationStatus)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Net Sales: ${PosViewModel.formatPeso(latestShift.netSalesCentavos)} (${latestShift.transactionCount} sales)",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Cash: ${PosViewModel.formatPeso(latestShift.cashPaymentsCentavos)}  •  Non-Cash: ${PosViewModel.formatPeso(latestShift.nonCashPaymentsCentavos)}  •  Actual Drawer: ${PosViewModel.formatPeso(latestShift.actualCashCentavos ?: latestShift.expectedCashCentavos)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onViewReport(latestShift) },
                    shape = RoundedCornerShape(PosRadius.sm),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.heightIn(min = 34.dp),
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Shift Report", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun ShiftMetaRow(
    label: String,
    value: String,
    highlight: Boolean = false,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val compactValue = value.length <= 16
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = if (compactValue) Alignment.CenterVertically else Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = if (compactValue) {
                Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            } else {
                Modifier
                    .weight(0.45f)
                    .padding(end = 8.dp)
            },
            style = if (highlight) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodySmall,
            fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = if (compactValue) Modifier else Modifier.weight(0.55f),
            style = (if (highlight) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium).copy(
                fontFeatureSettings = "tnum",
            ),
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.SemiBold,
            color = valueColor,
            textAlign = TextAlign.End,
            maxLines = if (compactValue) 1 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun formatCashierCode(rawId: String): String {
    val trimmed = rawId.trim()
    return if (trimmed.length > 12 && '-' in trimmed) {
        "CSH-${trimmed.take(6).uppercase(Locale.US)}"
    } else {
        trimmed
    }
}

@Composable
fun ReconciliationStatusBadge(status: ReconciliationStatus) {
    val color = reconciliationStatusColor(status)
    val label = when (status) {
        ReconciliationStatus.BALANCED -> "BALANCED"
        ReconciliationStatus.SHORT -> "SHORT"
        ReconciliationStatus.OVER -> "OVER"
        ReconciliationStatus.PENDING -> "PENDING"
    }
    PosStatusBadge(
        text = label,
        containerColor = color.copy(alpha = 0.14f),
        contentColor = color,
    )
}

@Composable
private fun reconciliationStatusColor(status: ReconciliationStatus): Color = when (status) {
    ReconciliationStatus.BALANCED -> StatusSuccess
    ReconciliationStatus.SHORT -> MaterialTheme.colorScheme.error
    ReconciliationStatus.OVER -> StockAmber
    ReconciliationStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun formatSignedVariance(varianceCentavos: Long): String = when {
    varianceCentavos > 0L -> "+${PosViewModel.formatPeso(varianceCentavos)}"
    varianceCentavos < 0L -> "-${PosViewModel.formatPeso(kotlin.math.abs(varianceCentavos))}"
    else -> PosViewModel.formatPeso(0L)
}

