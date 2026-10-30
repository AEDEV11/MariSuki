package com.aesprt.marisuki.feature.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.aesprt.marisuki.ui.components.ToolCard as Card
import com.aesprt.marisuki.ui.components.ToolIcon
import com.aesprt.marisuki.ui.components.ToolSectionHeader
import com.aesprt.marisuki.ui.components.ToolDialog as ModernDialog
import com.aesprt.marisuki.ui.theme.PosRadius
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aesprt.marisuki.DemoDataInstaller
import com.aesprt.marisuki.demoSeedFailure
import com.aesprt.marisuki.feature.printing.DebugPrintSimulation
import kotlinx.coroutines.launch

@Composable
fun DebugDeveloperTools() {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(demoSeedFailure ?: "Demo database is isolated from release data.") }
    var confirmReset by remember { mutableStateOf(false) }
    var showEmptyPreview by remember { mutableStateOf(false) }
    fun runAction(label: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            try { action(); status = label } catch (error: Throwable) { status = error.message ?: "Debug action failed." }
            finally { busy = false }
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolSectionHeader("Developer settings", Icons.Default.Code, "DEBUG BUILD")
        Text("All test actions use the separate marisuki-pos-debug.db database.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Demo accounts", style = MaterialTheme.typography.titleMedium)
                Text("Owner: demo.owner · DemoOwner26! · approval PIN 880026")
                Text("Cashier Maria: maria · PIN 123456")
                Text("Cashier Ana: ana · PIN 654321")
                Text("These are debug fixtures only; never use them for a real store.", color = MaterialTheme.colorScheme.error)
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { runAction("Demo products checked.") { DemoDataInstaller.reseedProducts() } }, enabled = !busy, shape = PosRadius.button, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Re-seed products") }
                OutlinedButton(onClick = { runAction("Sample transactions are present.") { DemoDataInstaller.createSampleTransactions() } }, enabled = !busy, shape = PosRadius.button, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Sample transactions") }
                OutlinedButton(onClick = { runAction("Stock set below the low-stock threshold.") { DemoDataInstaller.setLowStockTestState(false) } }, enabled = !busy, shape = PosRadius.button, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Test low stock") }
                OutlinedButton(onClick = { runAction("Demo product set out of stock.") { DemoDataInstaller.setLowStockTestState(true) } }, enabled = !busy, shape = PosRadius.button, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Test out of stock") }
                OutlinedButton(onClick = { DebugPrintSimulation.simulateDisconnection(); status = "The next print action will simulate a disconnected printer." }, shape = PosRadius.button, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Printer disconnected") }
                OutlinedButton(onClick = { DebugPrintSimulation.simulatePrintFailure(); status = "The next print action will simulate a print failure." }, shape = PosRadius.button, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Print failure") }
                OutlinedButton(onClick = { showEmptyPreview = !showEmptyPreview }, shape = PosRadius.button, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Test empty state") }
                OutlinedButton(onClick = { status = "Sign in as Ana to verify that utang permissions are denied." }, shape = PosRadius.button, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Permission denied") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Owner approval flow", style = MaterialTheme.typography.titleMedium)
                Text("Use a cashier account and perform an operation without permission to exercise approval and audit handling.")
                Text("Debug printer controls simulate errors before Android PrintManager; they do not verify Bluetooth or USB hardware.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = { confirmReset = true }, enabled = !busy, shape = PosRadius.button, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Reset demo data") }
        if (busy) CircularProgressIndicator()
        Text(status, color = MaterialTheme.colorScheme.primary)
        if (showEmptyPreview) Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Empty-state preview")
                Text("No matching products, customers, or transactions.")
            }
        }
    }
    if (confirmReset) ModernDialog(
        onDismissRequest = { confirmReset = false },
        icon = { ToolIcon(Icons.Default.Warning) },
        title = { Text("Reset demo data?") },
        text = { Text("This clears only the isolated debug demo database. It is available only when the stored profile is MariSuki Demo Grocery.") },
        confirmButton = {
            Button(onClick = { confirmReset = false; runAction("Demo data reset and reseeded.") { DemoDataInstaller.resetDemoData() } }, shape = PosRadius.button) { Text("Reset demo") }
        },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
}
