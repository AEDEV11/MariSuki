package com.aesprt.marisuki.feature.printing

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aesprt.marisuki.R
import com.aesprt.marisuki.feature.pos.PosViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun PrinterSettingsScreen(viewModel: PosViewModel) {
    val context = LocalContext.current
    val store = remember(context) { PrinterSettingsStore(context) }
    val profile by store.profile.collectAsState(initial = PrinterProfile())
    val scope = rememberCoroutineScope()
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var usbDevices by remember { mutableStateOf<List<UsbDevice>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val printerTestFailed = stringResource(R.string.printer_test_failed)
    val nearbyPermissionMessage = stringResource(R.string.printer_nearby_permission)
    val receiptTestSent = stringResource(R.string.printer_test_receipt_sent)
    val labelTestSent = stringResource(R.string.printer_test_label_sent)

    fun refreshBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val adapter = (context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        pairedDevices = runCatching { adapter?.bondedDevices?.toList().orEmpty() }.getOrDefault(emptyList())
    }

    fun refreshUsb() {
        val manager = context.getSystemService(android.content.Context.USB_SERVICE) as? UsbManager
        usbDevices = manager?.deviceList?.values?.toList().orEmpty()
    }

    fun authorizeSettingsChange(action: suspend () -> Unit) {
        scope.launch {
            try {
                viewModel.requirePrinterManagement()
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                status = error.localizedMessage ?: printerTestFailed
            }
        }
    }

    val bluetoothPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) refreshBluetooth() else status = nearbyPermissionMessage
    }

    LaunchedEffect(Unit) {
        refreshUsb()
        refreshBluetooth()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.printer_settings_description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.printer_connection), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        PrinterConnectionType.SYSTEM to R.string.printer_system,
                        PrinterConnectionType.BLUETOOTH_SPP to R.string.printer_bluetooth,
                        PrinterConnectionType.USB_BULK to R.string.printer_usb,
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = profile.connectionType == type,
                            onClick = { authorizeSettingsChange { store.setConnectionType(type); status = null } },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
                when (profile.connectionType) {
                    PrinterConnectionType.SYSTEM -> Text(stringResource(R.string.printer_system_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrinterConnectionType.BLUETOOTH_SPP -> {
                        Text(stringResource(R.string.printer_bluetooth_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val hasConnectPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (!hasConnectPermission) {
                            Button(onClick = { bluetoothPermission.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                                Text(stringResource(R.string.printer_allow_nearby))
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = ::refreshBluetooth) { Text(stringResource(R.string.printer_refresh_devices)) }
                                OutlinedButton(onClick = {
                                    runCatching { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
                                        .onFailure { status = it.localizedMessage ?: printerTestFailed }
                                }) { Text(stringResource(R.string.printer_pair_devices)) }
                            }
                            if (pairedDevices.isEmpty()) {
                                Text(stringResource(R.string.printer_no_paired_bluetooth), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            pairedDevices.forEach { device ->
                                val deviceNameValue = runCatching { device.name }.getOrNull().orEmpty()
                                val deviceName = if (deviceNameValue.isBlank()) stringResource(R.string.printer_bluetooth) else deviceNameValue
                                val selected = profile.bluetoothAddress == device.address
                                OutlinedButton(
                                    onClick = { authorizeSettingsChange { store.setBluetoothPrinter(device.address); status = null } },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(if (selected) "✓ $deviceName · ${device.address}" else "$deviceName · ${device.address}")
                                }
                            }
                        }
                    }
                    PrinterConnectionType.USB_BULK -> {
                        Text(stringResource(R.string.printer_usb_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = ::refreshUsb) { Text(stringResource(R.string.printer_refresh_devices)) }
                        if (usbDevices.isEmpty()) Text(stringResource(R.string.printer_no_usb), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        usbDevices.forEach { device ->
                            val selected = profile.usbVendorId == device.vendorId && profile.usbProductId == device.productId
                            val productNameValue = runCatching { device.productName }.getOrNull().orEmpty()
                            val productName = if (productNameValue.isBlank()) stringResource(R.string.printer_usb) else productNameValue
                            OutlinedButton(
                                onClick = { authorizeSettingsChange { store.setUsbPrinter(device.vendorId, device.productId); status = null } },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (selected) "✓ $productName · ${device.vendorId}:${device.productId}" else "$productName · ${device.vendorId}:${device.productId}")
                            }
                        }
                    }
                }
            }
        }

        Card {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.printer_receipt_width), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(58, 80).forEach { width ->
                        FilterChip(
                            selected = profile.receiptWidthMm == width,
                            onClick = { authorizeSettingsChange { store.setReceiptWidth(width) } },
                            label = { Text("${width}mm") },
                        )
                    }
                }
                Text(stringResource(R.string.printer_label_size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(40 to 30, 50 to 30, 60 to 40).forEach { (width, height) ->
                        FilterChip(
                            selected = profile.labelWidthMm == width && profile.labelHeightMm == height,
                            onClick = { authorizeSettingsChange { store.setLabelSize(width, height) } },
                            label = { Text("${width} × ${height}mm") },
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                status = null
                                try {
                                    viewModel.requirePrinterManagement()
                                    PrinterTransport.send(context, profile, EscPosCommandBuilder.testReceipt(profile.receiptWidthMm))
                                    status = receiptTestSent
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    status = error.localizedMessage ?: printerTestFailed
                                } finally { busy = false }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy && hasSelectedPrinter(profile),
                    ) { Text(stringResource(R.string.printer_test_receipt)) }
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                status = null
                                try {
                                    viewModel.requirePrinterManagement()
                                    PrinterTransport.send(context, profile, EscPosCommandBuilder.testLabel(profile.labelWidthMm, profile.labelHeightMm))
                                    status = labelTestSent
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (error: Exception) {
                                    status = error.localizedMessage ?: printerTestFailed
                                } finally { busy = false }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !busy && hasSelectedPrinter(profile),
                    ) { Text(stringResource(R.string.printer_test_label)) }
                }
                status?.let { Text(it, color = if (it.contains("failed", true) || it.contains("could not", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

private fun hasSelectedPrinter(profile: PrinterProfile): Boolean = when (profile.connectionType) {
    PrinterConnectionType.SYSTEM -> false
    PrinterConnectionType.BLUETOOTH_SPP -> !profile.bluetoothAddress.isNullOrBlank()
    PrinterConnectionType.USB_BULK -> profile.usbVendorId != null && profile.usbProductId != null
}
