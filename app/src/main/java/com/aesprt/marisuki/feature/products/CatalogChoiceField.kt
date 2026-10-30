package com.aesprt.marisuki.feature.products

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.aesprt.marisuki.R
import com.aesprt.marisuki.ui.components.ToolDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CatalogChoiceField(
    value: String,
    options: List<String>,
    label: String,
    help: String,
    onSelected: (String) -> Unit,
    onSave: suspend (String) -> String,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
    unitLabels: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        ExposedDropdownMenuBox(expanded, { expanded = it }) {
            OutlinedTextField(value = if (unitLabels) catalogUnitLabel(value) else value, onValueChange = {}, readOnly = true,
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
            ExposedDropdownMenu(expanded, { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                if (optional) DropdownMenuItem(text = { Text(stringResource(R.string.catalog_none)) }, onClick = { onSelected(""); expanded = false })
                (listOf(value).filter(String::isNotBlank) + options).distinctBy { it.lowercase(java.util.Locale.ROOT) }.forEach { option ->
                    DropdownMenuItem(text = { Text(if (unitLabels) catalogUnitLabel(option) else option) },
                        trailingIcon = if (option.equals(value, true)) { { Icon(Icons.Default.Check, null) } } else null,
                        onClick = { onSelected(option); expanded = false })
                }
            }
        }
        Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { expanded = false; newName = ""; failed = false; adding = true }) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Text(stringResource(R.string.catalog_add_new, label), Modifier.padding(start = 6.dp))
        }
    }
    if (adding) ToolDialog(onDismissRequest = { if (!busy) adding = false },
        icon = { ProductDialogIcon(Icons.Default.Add) }, title = { Text(stringResource(R.string.catalog_add_new, label)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.catalog_saved_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(newName, { newName = it; failed = false }, Modifier.fillMaxWidth(), label = { Text(label) },
                    singleLine = true, enabled = !busy, shape = RoundedCornerShape(12.dp), isError = failed)
                if (failed) Text(stringResource(R.string.error_general), color = MaterialTheme.colorScheme.error)
            }
        }, confirmButton = {
            Button(enabled = !busy && newName.trim().isNotEmpty() && newName.trim().length <= 80,
                shape = RoundedCornerShape(12.dp), onClick = {
                    busy = true
                    scope.launch {
                        try { onSelected(onSave(newName)); adding = false }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                        finally { busy = false }
                    }
                }) { Text(stringResource(if (busy) R.string.saving else R.string.save)) }
        }, dismissButton = { TextButton(enabled = !busy, onClick = { adding = false }) { Text(stringResource(R.string.cancel)) } })
}

@Composable
private fun catalogUnitLabel(value: String): String = when (value.lowercase(java.util.Locale.ROOT)) {
    "piece" -> stringResource(R.string.unit_piece)
    "pack" -> stringResource(R.string.unit_pack)
    "case" -> stringResource(R.string.unit_case)
    "kilo" -> stringResource(R.string.unit_kilo)
    "gram" -> stringResource(R.string.unit_gram)
    "liter" -> stringResource(R.string.unit_liter)
    "milliliter" -> stringResource(R.string.unit_milliliter)
    "bottle" -> stringResource(R.string.unit_bottle)
    "can" -> stringResource(R.string.unit_can)
    "box" -> stringResource(R.string.unit_box)
    "bag" -> stringResource(R.string.unit_bag)
    "sachet" -> stringResource(R.string.unit_sachet)
    "loaf" -> stringResource(R.string.unit_loaf)
    "dozen" -> stringResource(R.string.unit_dozen)
    else -> value
}
