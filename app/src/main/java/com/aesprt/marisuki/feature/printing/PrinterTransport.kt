package com.aesprt.marisuki.feature.printing

import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

class PrinterException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Direct transport for paired Bluetooth SPP and USB bulk ESC/POS printers. */
object PrinterTransport {
    suspend fun send(context: Context, profile: PrinterProfile, bytes: ByteArray) {
        if (bytes.isEmpty()) throw PrinterException("There is nothing to print.")
        when (profile.connectionType) {
            PrinterConnectionType.SYSTEM -> throw PrinterException("Choose a direct printer or use Android system printing.")
            PrinterConnectionType.BLUETOOTH_SPP -> sendBluetooth(context.applicationContext, profile, bytes)
            PrinterConnectionType.USB_BULK -> sendUsb(context.applicationContext, profile, bytes)
        }
    }

    private suspend fun sendBluetooth(context: Context, profile: PrinterProfile, bytes: ByteArray) = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) throw PrinterException("Allow Nearby devices access before connecting to the paired printer.")
        val address = profile.bluetoothAddress ?: throw PrinterException("Select a paired Bluetooth printer in Printer Settings.")
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: throw PrinterException("Bluetooth is not available on this device.")
        val socket = try {
            adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (error: SecurityException) {
            throw PrinterException("Allow Nearby devices access to use the Bluetooth printer.", error)
        } catch (error: IllegalArgumentException) {
            throw PrinterException("The saved Bluetooth printer is unavailable. Select it again in Printer Settings.", error)
        }
        try {
            withTimeout(CONNECT_TIMEOUT_MILLIS) { socket.connect() }
            socket.outputStream.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
        } catch (error: PrinterException) {
            throw error
        } catch (error: SecurityException) {
            throw PrinterException("Allow Nearby devices access to use the Bluetooth printer.", error)
        } catch (error: Exception) {
            throw PrinterException("Could not connect to or print with the Bluetooth ESC/POS printer. Check that it is paired and powered on.", error)
        } finally {
            runCatching { socket.close() }
        }
    }

    private suspend fun sendUsb(context: Context, profile: PrinterProfile, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val vendorId = profile.usbVendorId ?: throw PrinterException("Select a connected USB printer in Printer Settings.")
        val productId = profile.usbProductId ?: throw PrinterException("Select a connected USB printer in Printer Settings.")
        val manager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: throw PrinterException("USB host printing is not available on this device.")
        val matching = manager.deviceList.values.filter { it.vendorId == vendorId && it.productId == productId }
        val device = matching.singleOrNull() ?: when {
            matching.isEmpty() -> throw PrinterException("The selected USB printer is disconnected.")
            else -> throw PrinterException("More than one matching USB printer is connected. Disconnect the extra device and try again.")
        }
        if (!manager.hasPermission(device) && !requestUsbPermission(context, manager, device)) {
            throw PrinterException("USB printer access was not granted.")
        }
        val connection = manager.openDevice(device) ?: throw PrinterException("Could not open the USB printer. Reconnect it and try again.")
        try {
            val endpoints = findBulkOutEndpoint(device)
                ?: throw PrinterException("This USB printer does not expose a compatible bulk-output ESC/POS interface.")
            val (usbInterface, endpoint) = endpoints
            if (!connection.claimInterface(usbInterface, true)) {
                throw PrinterException("Could not claim the USB printer interface. Close other apps using the printer and try again.")
            }
            try {
                var offset = 0
                while (offset < bytes.size) {
                    val count = minOf(USB_CHUNK_BYTES, bytes.size - offset)
                    val sent = connection.bulkTransfer(endpoint, bytes, offset, count, USB_TRANSFER_TIMEOUT_MILLIS)
                    if (sent <= 0) throw PrinterException("The USB printer stopped accepting data. Check its connection and paper.")
                    offset += sent
                }
            } finally {
                connection.releaseInterface(usbInterface)
            }
        } catch (error: PrinterException) {
            throw error
        } catch (error: SecurityException) {
            throw PrinterException("Android did not grant access to the USB printer.", error)
        } catch (error: Exception) {
            throw PrinterException("Could not print to the USB ESC/POS printer. Check the cable, printer, and paper.", error)
        } finally {
            connection.close()
        }
    }

    private fun findBulkOutEndpoint(device: UsbDevice): Pair<UsbInterface, UsbEndpoint>? {
        for (interfaceIndex in 0 until device.interfaceCount) {
            val usbInterface = device.getInterface(interfaceIndex)
            for (endpointIndex in 0 until usbInterface.endpointCount) {
                val endpoint = usbInterface.getEndpoint(endpointIndex)
                if (endpoint.direction == UsbConstants.USB_DIR_OUT && endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    return usbInterface to endpoint
                }
            }
        }
        return null
    }

    private suspend fun requestUsbPermission(context: Context, manager: UsbManager, device: UsbDevice): Boolean =
        suspendCancellableCoroutine { continuation ->
            val action = "${context.packageName}.USB_PRINTER_PERMISSION"
            val filter = IntentFilter(action)
            val finished = AtomicBoolean(false)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(receiverContext: Context, intent: Intent) {
                    if (intent.action != action || !finished.compareAndSet(false, true)) return
                    val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    }
                    runCatching { context.unregisterReceiver(this) }
                    if (continuation.isActive) continuation.resume(granted)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }
            continuation.invokeOnCancellation {
                if (finished.compareAndSet(false, true)) runCatching { context.unregisterReceiver(receiver) }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val permissionIntent = PendingIntent.getBroadcast(
                context,
                device.deviceId,
                Intent(action).setPackage(context.packageName),
                flags,
            )
            manager.requestPermission(device, permissionIntent)
        }

    private const val USB_CHUNK_BYTES = 8 * 1024
    private const val USB_TRANSFER_TIMEOUT_MILLIS = 10_000
    private const val CONNECT_TIMEOUT_MILLIS = 15_000L
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
}
