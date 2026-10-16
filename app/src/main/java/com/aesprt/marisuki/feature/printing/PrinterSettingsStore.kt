package com.aesprt.marisuki.feature.printing

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aesprt.marisuki.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.printerSettings by preferencesDataStore(
    name = if (BuildConfig.DEBUG) "marisuki_printers_debug" else "marisuki_printers",
)

enum class PrinterConnectionType { SYSTEM, BLUETOOTH_SPP, USB_BULK }

data class PrinterProfile(
    val connectionType: PrinterConnectionType = PrinterConnectionType.SYSTEM,
    val bluetoothAddress: String? = null,
    val usbVendorId: Int? = null,
    val usbProductId: Int? = null,
    val receiptWidthMm: Int = 58,
    val labelWidthMm: Int = 40,
    val labelHeightMm: Int = 30,
)

class PrinterSettingsStore(context: Context) {
    private val settings = context.applicationContext.printerSettings

    val profile: Flow<PrinterProfile> = settings.data.map { values ->
        PrinterProfile(
            connectionType = values[CONNECTION_TYPE]?.let { runCatching { PrinterConnectionType.valueOf(it) }.getOrNull() } ?: PrinterConnectionType.SYSTEM,
            bluetoothAddress = values[BLUETOOTH_ADDRESS],
            usbVendorId = values[USB_VENDOR_ID],
            usbProductId = values[USB_PRODUCT_ID],
            receiptWidthMm = values[RECEIPT_WIDTH_MM]?.takeIf { it == 58 || it == 80 } ?: 58,
            labelWidthMm = values[LABEL_WIDTH_MM]?.takeIf { it in setOf(40, 50, 60) } ?: 40,
            labelHeightMm = values[LABEL_HEIGHT_MM]?.takeIf { it in setOf(30, 40) } ?: 30,
        )
    }

    suspend fun setConnectionType(type: PrinterConnectionType) = settings.edit { it[CONNECTION_TYPE] = type.name }

    suspend fun setBluetoothPrinter(address: String) = settings.edit {
        it[CONNECTION_TYPE] = PrinterConnectionType.BLUETOOTH_SPP.name
        it[BLUETOOTH_ADDRESS] = address
        it.remove(USB_VENDOR_ID)
        it.remove(USB_PRODUCT_ID)
    }

    suspend fun setUsbPrinter(vendorId: Int, productId: Int) = settings.edit {
        it[CONNECTION_TYPE] = PrinterConnectionType.USB_BULK.name
        it[USB_VENDOR_ID] = vendorId
        it[USB_PRODUCT_ID] = productId
        it.remove(BLUETOOTH_ADDRESS)
    }

    suspend fun setReceiptWidth(widthMm: Int) {
        require(widthMm == 58 || widthMm == 80)
        settings.edit { it[RECEIPT_WIDTH_MM] = widthMm }
    }

    suspend fun setLabelSize(widthMm: Int, heightMm: Int) {
        require((widthMm to heightMm) in setOf(40 to 30, 50 to 30, 60 to 40))
        settings.edit {
            it[LABEL_WIDTH_MM] = widthMm
            it[LABEL_HEIGHT_MM] = heightMm
        }
    }

    companion object {
        private val CONNECTION_TYPE = stringPreferencesKey("printer_connection_type")
        private val BLUETOOTH_ADDRESS = stringPreferencesKey("printer_bluetooth_address")
        private val USB_VENDOR_ID = intPreferencesKey("printer_usb_vendor_id")
        private val USB_PRODUCT_ID = intPreferencesKey("printer_usb_product_id")
        private val RECEIPT_WIDTH_MM = intPreferencesKey("printer_receipt_width_mm")
        private val LABEL_WIDTH_MM = intPreferencesKey("printer_label_width_mm")
        private val LABEL_HEIGHT_MM = intPreferencesKey("printer_label_height_mm")
    }
}
