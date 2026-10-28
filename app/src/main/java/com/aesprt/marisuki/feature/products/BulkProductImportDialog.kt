package com.aesprt.marisuki.feature.products

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aesprt.marisuki.domain.auth.PosPermission
import com.aesprt.marisuki.R
import com.aesprt.marisuki.data.importing.ImportProblem
import com.aesprt.marisuki.feature.pos.PosViewModel
import com.aesprt.marisuki.ui.components.ToolCard
import com.aesprt.marisuki.ui.components.ToolDialog
import com.aesprt.marisuki.ui.components.ToolSectionHeader

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BulkProductImportDialog(viewModel: PosViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.productImport.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    var showGuide by remember(state.preview) { mutableStateOf(state.preview == null) }
    var visible by remember(state.preview) { mutableIntStateOf(30) }
    val csvDownload = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { viewModel.saveProductTemplate(it, context.applicationContext, false) }
    }
    val excelDownload = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        uri?.let { viewModel.saveProductTemplate(it, context.applicationContext, true) }
    }
    val upload = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.readProductImport(it, context.contentResolver) }
    }
    ToolDialog(onDismissRequest = { if (!state.busy) onDismiss() },
        icon = { ProductDialogIcon(Icons.Default.FileUpload) }, title = { Text(stringResource(R.string.bulk_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                state.result?.let { result ->
                    ToolCard { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ToolSectionHeader(stringResource(if (result.alreadyImported) R.string.bulk_already_imported else R.string.bulk_success, result.count), Icons.Default.CheckCircle)
                        Text(stringResource(R.string.bulk_complete_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                } ?: run {
                    if (showGuide) {
                    ToolCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.bulk_step_template), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.bulk_template_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(enabled = !state.busy, onClick = { csvDownload.launch("MariSuki-products.csv") }, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Text("CSV", Modifier.padding(start = 8.dp))
                            }
                            OutlinedButton(enabled = !state.busy, onClick = { excelDownload.launch("MariSuki-products.xlsx") }, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Text("Excel (.xlsx)", Modifier.padding(start = 8.dp))
                            }
                        }
                        if (state.templateSaved) Text(stringResource(R.string.bulk_template_saved), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    } }
                    ToolCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.bulk_step_fill), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.bulk_fill_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.bulk_units_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } }
                    }
                    if (state.preview != null) TextButton(onClick = { showGuide = !showGuide }) {
                        Text(stringResource(if (showGuide) R.string.bulk_hide_guide else R.string.bulk_show_guide))
                    }
                    OutlinedButton(onClick = { upload.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream")) },
                        enabled = !state.busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.FileUpload, null, Modifier.size(20.dp))
                        Text(stringResource(R.string.bulk_choose_file), Modifier.padding(start = 8.dp))
                    }
                    if (state.busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(stringResource(R.string.bulk_working), style = MaterialTheme.typography.bodySmall) }
                    state.error?.let { Text(importProblemText(it), color = MaterialTheme.colorScheme.error) }
                    state.preview?.let { preview ->
                        Text(state.fileName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Surface(color = if (preview.canImport) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                            Text(stringResource(R.string.bulk_review_count, preview.validCount, preview.rows.size, preview.issues.size), Modifier.fillMaxWidth().padding(14.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(stringResource(R.string.bulk_review_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        preview.rows.take(visible).forEach { row ->
                            ToolCard { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(stringResource(R.string.bulk_row_name, row.row, row.name.ifBlank { "—" }), fontWeight = FontWeight.SemiBold)
                                row.input?.let { product ->
                                    Text("${PosViewModel.formatPeso(product.baseUnitPriceCentavos)} / ${product.baseUnitLabel}", color = MaterialTheme.colorScheme.primary)
                                    product.barcode?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                    product.categoryName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Text(stringResource(R.string.bulk_row_stock, PosViewModel.formatQuantity(product.initialStockMilliUnits), product.baseUnitLabel), style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.bulk_row_low_stock, PosViewModel.formatQuantity(product.minimumStockMilliUnits), product.baseUnitLabel), style = MaterialTheme.typography.bodySmall)
                                    if (PosPermission.VIEW_PURCHASE_COST in permissions || PosPermission.EDIT_PURCHASE_COST in permissions) Text(stringResource(R.string.bulk_row_cost, PosViewModel.formatPeso(product.purchaseCostCentavos), product.baseUnitLabel), style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(if (product.wholeBaseUnitsOnly) R.string.bulk_whole_only else R.string.bulk_fractions_allowed), style = MaterialTheme.typography.bodySmall)
                                    product.extraUnits.forEach { unit -> Text("1 ${unit.label} = ${PosViewModel.formatQuantity(unit.baseQuantityMilliUnits)} ${product.baseUnitLabel} · ${PosViewModel.formatPeso(unit.priceCentavos)}", style = MaterialTheme.typography.bodySmall) }
                                }
                                row.issues.forEach { issue -> Text("${issue.column}: ${importProblemText(issue.problem)}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            } }
                        }
                        if (preview.rows.size > visible) TextButton(onClick = { visible += 30 }) { Text(stringResource(R.string.bulk_show_more)) }
                    }
                }
            }
        }, confirmButton = {
            if (state.result != null) Button(onClick = onDismiss, shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.close)) }
            else Button(onClick = viewModel::commitProductImport, enabled = !state.busy && state.preview?.canImport == true,
                shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.bulk_import_count, state.preview?.validCount ?: 0)) }
        }, dismissButton = if (state.result == null) { { TextButton(enabled = !state.busy, onClick = onDismiss) { Text(stringResource(R.string.cancel)) } } } else null)
}

@Composable
private fun importProblemText(problem: ImportProblem): String = stringResource(when (problem) {
    ImportProblem.REQUIRED -> R.string.bulk_error_required
    ImportProblem.NUMBER -> R.string.bulk_error_number
    ImportProblem.FRACTION -> R.string.bulk_error_fraction
    ImportProblem.DUPLICATE_BARCODE -> R.string.bulk_error_duplicate
    ImportProblem.UNIT -> R.string.bulk_error_unit
    ImportProblem.BOOLEAN -> R.string.bulk_error_boolean
    ImportProblem.HEADERS -> R.string.bulk_error_headers
    ImportProblem.EMPTY -> R.string.bulk_error_empty
    ImportProblem.LIMIT -> R.string.bulk_error_limit
    ImportProblem.FORMAT -> R.string.bulk_error_format
    ImportProblem.FORMULA -> R.string.bulk_error_formula
    ImportProblem.PERMISSION -> R.string.bulk_error_permission
    ImportProblem.FAILED -> R.string.bulk_error_failed
})
