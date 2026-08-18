package com.sourcetx.companion.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.CdcAcmSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UsbDeviceInfo(
    val deviceName: String,
    val vendorId: Int,
    val productId: Int,
    val productName: String,
    val manufacturerName: String,
    val isEspressif: Boolean
)

class SourceTxUsbManager(private val context: Context) {
    companion object {
        const val ACTION_USB_PERMISSION = "com.sourcetx.companion.USB_PERMISSION"
        const val ESPRESSIF_VID = 0x303A
        const val ESPRESSIF_USB_SERIAL_JTAG_PID = 0x1001
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _connectedDevice = MutableStateFlow<UsbDeviceInfo?>(null)
    val connectedDevice: StateFlow<UsbDeviceInfo?> = _connectedDevice.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private var activeDriver: UsbSerialDriver? = null
    private var activePort: UsbSerialPort? = null

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_USB_PERMISSION -> {
                    synchronized(this) {
                        val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                        }

                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            device?.let {
                                _hasPermission.value = true
                                updateDeviceInfo(it)
                            }
                        } else {
                            _hasPermission.value = false
                        }
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    scanDevices()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    disconnect()
                    _connectedDevice.value = null
                    _hasPermission.value = false
                }
            }
        }
    }

    fun register() {
        val filter = IntentFilter().apply {
            addAction(ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        ContextCompat.registerReceiver(
            context,
            usbReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        scanDevices()
    }

    fun unregister() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        disconnect()
    }

    fun scanDevices(): List<UsbSerialDriver> {
        val probeTable = UsbSerialProber.getDefaultProbeTable().apply {
            // ESP32-S3 native USB Serial/JTAG uses vendor-class descriptors, so
            // the library's normal CDC interface-class probe cannot discover it.
            addProduct(ESPRESSIF_VID, ESPRESSIF_USB_SERIAL_JTAG_PID, CdcAcmSerialDriver::class.java)
        }
        val availableDrivers = UsbSerialProber(probeTable).findAllDrivers(usbManager)
        if (availableDrivers.isNotEmpty()) {
            val driver = availableDrivers.sortedWith(
                compareByDescending<UsbSerialDriver> { it.device.vendorId == ESPRESSIF_VID }
                    .thenBy { it.device.deviceId }
            ).first()
            val device = driver.device
            activeDriver = driver
            updateDeviceInfo(device)

            if (usbManager.hasPermission(device)) {
                _hasPermission.value = true
            } else {
                _hasPermission.value = false
                requestPermission(device)
            }
        } else {
            activeDriver = null
            _connectedDevice.value = null
            _hasPermission.value = false
        }
        return availableDrivers
    }

    fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION).setPackage(context.packageName),
            flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun updateDeviceInfo(device: UsbDevice) {
        val info = UsbDeviceInfo(
            deviceName = device.deviceName,
            vendorId = device.vendorId,
            productId = device.productId,
            productName = device.productName ?: "USB serial device",
            manufacturerName = device.manufacturerName ?: "Unknown manufacturer",
            isEspressif = device.vendorId == ESPRESSIF_VID
        )
        _connectedDevice.value = info
    }

    fun openPort(baudRate: Int = 115200, requireEspressif: Boolean = false): UsbSerialPort? {
        val driver = activeDriver ?: return null
        if (requireEspressif && (
                driver.device.vendorId != ESPRESSIF_VID ||
                driver.device.productId != ESPRESSIF_USB_SERIAL_JTAG_PID
            )
        ) return null
        if (!usbManager.hasPermission(driver.device)) return null
        disconnect()
        val port = driver.ports.firstOrNull() ?: return null
        val connection = usbManager.openDevice(driver.device) ?: return null
        return try {
            port.open(connection)
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            port.dtr = false
            port.rts = false
            activePort = port
            port
        } catch (_: Exception) {
            connection.close()
            null
        }
    }

    fun disconnect() {
        try {
            activePort?.close()
        } catch (_: Exception) {}
        activePort = null
    }
}
