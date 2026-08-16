package com.sourcetx.companion.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
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
    val manufacturerName: String
)

class SourceTxUsbManager(private val context: Context) {
    companion object {
        const val ACTION_USB_PERMISSION = "com.sourcetx.companion.USB_PERMISSION"
        const val ESPRESSIF_VID = 0x303A
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(usbReceiver, filter)
        }
        scanDevices()
    }

    fun unregister() {
        try {
            context.unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        disconnect()
    }

    fun scanDevices(): List<UsbSerialDriver> {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isNotEmpty()) {
            val driver = availableDrivers[0]
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
            context, 0, Intent(ACTION_USB_PERMISSION), flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    private fun updateDeviceInfo(device: UsbDevice) {
        val info = UsbDeviceInfo(
            deviceName = device.deviceName,
            vendorId = device.vendorId,
            productId = device.productId,
            productName = device.productName ?: "SourceTX Transmitter",
            manufacturerName = device.manufacturerName ?: "Espressif"
        )
        _connectedDevice.value = info
    }

    fun openPort(baudRate: Int = 115200): UsbSerialPort? {
        val driver = activeDriver ?: return null
        val connection = usbManager.openDevice(driver.device) ?: return null

        val port = driver.ports[0]
        port.open(connection)
        port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        port.dtr = false
        port.rts = false
        activePort = port
        return port
    }

    fun disconnect() {
        try {
            activePort?.close()
        } catch (_: Exception) {}
        activePort = null
    }
}
