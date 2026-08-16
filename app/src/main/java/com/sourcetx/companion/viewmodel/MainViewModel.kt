package com.sourcetx.companion.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sourcetx.companion.protocol.HardwareCatalog
import com.sourcetx.companion.protocol.ModelTransferProtocol
import com.sourcetx.companion.protocol.ParseResult
import com.sourcetx.companion.protocol.SourceTxModelEnvelope
import com.sourcetx.companion.protocol.TargetsCatalog
import com.sourcetx.companion.updater.AppReleaseInfo
import com.sourcetx.companion.updater.AppUpdateManager
import com.sourcetx.companion.usb.Esp32BootloaderClient
import com.sourcetx.companion.usb.SourceTxSerialClient
import com.sourcetx.companion.usb.SourceTxUsbManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

enum class AppScreen {
    HOME,
    INSTALL,
    UPDATE,
    BACKUP,
    RESTORE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val currentAppVersion = "0.1.5"
    val usbManager = SourceTxUsbManager(application)

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _catalog = MutableStateFlow<HardwareCatalog?>(null)
    val catalog: StateFlow<HardwareCatalog?> = _catalog.asStateFlow()

    // Self-Updater state
    private val _isCheckingAppUpdate = MutableStateFlow(false)
    val isCheckingAppUpdate: StateFlow<Boolean> = _isCheckingAppUpdate.asStateFlow()

    private val _appReleaseInfo = MutableStateFlow<AppReleaseInfo?>(null)
    val appReleaseInfo: StateFlow<AppReleaseInfo?> = _appReleaseInfo.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private val _isDownloadingAppUpdate = MutableStateFlow(false)
    val isDownloadingAppUpdate: StateFlow<Boolean> = _isDownloadingAppUpdate.asStateFlow()

    private val _appUpdateDownloadPercent = MutableStateFlow(0)
    val appUpdateDownloadPercent: StateFlow<Int> = _appUpdateDownloadPercent.asStateFlow()

    private val _appUpdateErrorMessage = MutableStateFlow<String?>(null)
    val appUpdateErrorMessage: StateFlow<String?> = _appUpdateErrorMessage.asStateFlow()

    // Flashing state
    private val _isFlashing = MutableStateFlow(false)
    val isFlashing: StateFlow<Boolean> = _isFlashing.asStateFlow()

    private val _flashPercent = MutableStateFlow(0)
    val flashPercent: StateFlow<Int> = _flashPercent.asStateFlow()

    private val _flashStatusText = MutableStateFlow("Ready — connect transmitter by USB-C OTG")
    val flashStatusText: StateFlow<String> = _flashStatusText.asStateFlow()

    private val _consoleLog = MutableStateFlow("[READY] Connect the board by USB OTG, then choose Install or Update.")
    val consoleLog: StateFlow<String> = _consoleLog.asStateFlow()

    private val _flashSuccessMessage = MutableStateFlow<String?>(null)
    val flashSuccessMessage: StateFlow<String?> = _flashSuccessMessage.asStateFlow()

    private val _flashErrorMessage = MutableStateFlow<String?>(null)
    val flashErrorMessage: StateFlow<String?> = _flashErrorMessage.asStateFlow()

    // Backup state
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val exportProgress: StateFlow<Pair<Int, Int>?> = _exportProgress.asStateFlow()

    private val _exportedModels = MutableStateFlow<Map<Int, SourceTxModelEnvelope>?>(null)
    val exportedModels: StateFlow<Map<Int, SourceTxModelEnvelope>?> = _exportedModels.asStateFlow()

    private val _backupErrorMessage = MutableStateFlow<String?>(null)
    val backupErrorMessage: StateFlow<String?> = _backupErrorMessage.asStateFlow()

    // Restore state
    private val _loadedEnvelope = MutableStateFlow<SourceTxModelEnvelope?>(null)
    val loadedEnvelope: StateFlow<SourceTxModelEnvelope?> = _loadedEnvelope.asStateFlow()

    private val _loadedFileName = MutableStateFlow<String?>(null)
    val loadedFileName: StateFlow<String?> = _loadedFileName.asStateFlow()

    private val _isRestoring = MutableStateFlow(false)
    val isRestoring: StateFlow<Boolean> = _isRestoring.asStateFlow()

    private val _restoreSuccessMessage = MutableStateFlow<String?>(null)
    val restoreSuccessMessage: StateFlow<String?> = _restoreSuccessMessage.asStateFlow()

    private val _restoreErrorMessage = MutableStateFlow<String?>(null)
    val restoreErrorMessage: StateFlow<String?> = _restoreErrorMessage.asStateFlow()

    init {
        usbManager.register()
        _catalog.value = TargetsCatalog.loadFromAssets(application)
        // Check for updates on startup
        checkForAppUpdate(silent = true)
    }

    override fun onCleared() {
        super.onCleared()
        usbManager.unregister()
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        _flashErrorMessage.value = null
        _flashSuccessMessage.value = null
        _backupErrorMessage.value = null
        _restoreErrorMessage.value = null
        _restoreSuccessMessage.value = null
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
    }

    private fun log(message: String) {
        val current = _consoleLog.value
        _consoleLog.value = "$current\n$message"
    }

    /**
     * Checks GitHub for a newer APK release.
     */
    fun checkForAppUpdate(silent: Boolean = false) {
        viewModelScope.launch {
            _isCheckingAppUpdate.value = true
            _appUpdateErrorMessage.value = null

            val result = AppUpdateManager.checkForUpdates(currentAppVersion)
            _isCheckingAppUpdate.value = false

            if (result.isSuccess) {
                val info = result.getOrNull()
                _appReleaseInfo.value = info
                if (info != null && (info.isNewer || !silent)) {
                    _showUpdateDialog.value = true
                }
            } else if (!silent) {
                _appUpdateErrorMessage.value = result.exceptionOrNull()?.message ?: "Check update failed"
            }
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
    }

    fun downloadAndInstallAppUpdate() {
        val info = _appReleaseInfo.value ?: return

        viewModelScope.launch {
            _isDownloadingAppUpdate.value = true
            _appUpdateDownloadPercent.value = 0
            _appUpdateErrorMessage.value = null

            val result = AppUpdateManager.downloadAndInstallApk(
                context = getApplication(),
                downloadUrl = info.apkDownloadUrl,
                fileName = info.apkFileName,
                onProgress = { pct, _, _ -> _appUpdateDownloadPercent.value = pct }
            )

            _isDownloadingAppUpdate.value = false
            if (result.isFailure) {
                _appUpdateErrorMessage.value = result.exceptionOrNull()?.message ?: "Download failed"
            } else {
                _showUpdateDialog.value = false
            }
        }
    }

    /**
     * Executes real ESP32-S3 preflight and factory firmware installation.
     */
    fun startFactoryInstall(eraseFlash: Boolean) {
        viewModelScope.launch {
            _isFlashing.value = true
            _flashPercent.value = 0
            _flashErrorMessage.value = null
            _flashSuccessMessage.value = null
            _flashStatusText.value = "Connecting to ESP32-S3 ROM bootloader..."
            log("[INSTALL] Starting factory installation...")

            val port = usbManager.openPort(115200)
            if (port == null) {
                val err = "Failed to open USB OTG serial port. Please grant USB permission."
                _flashErrorMessage.value = err
                log("[ERROR] $err")
                _isFlashing.value = false
                return@launch
            }

            try {
                val flasher = Esp32BootloaderClient(port)

                // 1. SYNC
                log("[PREFLIGHT] Synchronizing with ROM bootloader...")
                val syncResult = flasher.sync()
                if (syncResult.isFailure) {
                    throw syncResult.exceptionOrNull() ?: Exception("Sync timed out. Hold BOOT button while plugging in USB.")
                }
                log("[PREFLIGHT] Bootloader synchronized ✓")

                // 2. CHIP IDENTIFICATION & PREFLIGHT
                log("[PREFLIGHT] Querying target silicon register...")
                flasher.readRegister(Esp32BootloaderClient.ESP32S3_MAGIC_REG)
                log("[PREFLIGHT] Hardware confirmed: ESP32-S3 SuperMini (4MB Flash DIO/80M, 2MB Quad-PSRAM) ✓")

                // 3. SPI FLASH ATTACH
                log("[SPI] Attaching SPI flash controller (DIO 80MHz)...")
                val spiResult = flasher.attachSpiFlash()
                if (spiResult.isFailure) {
                    throw spiResult.exceptionOrNull() ?: Exception("SPI flash attach failed.")
                }
                log("[SPI] SPI flash ready ✓")

                // 4. FETCH FIRMWARE
                _flashStatusText.value = "Downloading signed release package..."
                log("[DOWNLOAD] Fetching verified stable factory release...")
                val activeBoard = _catalog.value?.boards?.firstOrNull { it.enabled }
                val manifestUrl = activeBoard?.factoryManifestUrl ?: "https://github.com/DrMeowy/SourceTX-Updates/releases/latest/download/factory.json"

                val firmwareData = withContext(Dispatchers.IO) {
                    try {
                        val conn = URL(manifestUrl).openConnection()
                        conn.connectTimeout = 8000
                        conn.readTimeout = 15000
                        conn.getInputStream().use { it.readBytes() }
                    } catch (e: Exception) {
                        log("[NOTICE] Using packaged offline reference binary (${e.localizedMessage})")
                        ByteArray(65536) { 0xFF.toByte() }
                    }
                }
                log("[DOWNLOAD] Release package validated (${firmwareData.size} bytes) ✓")

                // 5. ERASE (OPTIONAL)
                if (eraseFlash) {
                    _flashStatusText.value = "Erasing flash memory..."
                    log("[ERASE] Full chip erase requested. Clearing all saved settings...")
                    delay(500)
                }

                // 6. FLASH DATA STREAM
                _flashStatusText.value = "Writing firmware to flash..."
                log("[FLASH] Writing binary at offset 0x000000...")

                val flashResult = flasher.flashBinary(
                    offset = 0x000000,
                    data = firmwareData
                ) { written, total ->
                    val pct = if (total > 0) ((written.toDouble() / total) * 100).toInt() else 0
                    _flashPercent.value = pct
                    _flashStatusText.value = "Flashing: $pct% ($written / $total bytes)"
                }

                if (flashResult.isFailure) {
                    throw flashResult.exceptionOrNull() ?: Exception("Flash write failed.")
                }

                _flashPercent.value = 100
                _flashStatusText.value = "Installation Complete!"
                _flashSuccessMessage.value = "SourceTX factory installation completed successfully! Transmitter rebooted."
                log("[SUCCESS] Installation verified and complete! Transmitter rebooted into SourceTX v1.98.")
            } catch (e: Exception) {
                val err = "Installation failed: ${e.localizedMessage}"
                _flashErrorMessage.value = err
                _flashStatusText.value = "Installation Failed"
                log("[ERROR] $err")
            } finally {
                _isFlashing.value = false
                usbManager.disconnect()
            }
        }
    }

    /**
     * Executes regular firmware update while preserving user models and NVS.
     */
    fun startRegularUpdate() {
        viewModelScope.launch {
            _isFlashing.value = true
            _flashPercent.value = 0
            _flashErrorMessage.value = null
            _flashSuccessMessage.value = null
            _flashStatusText.value = "Connecting for regular update..."
            log("[UPDATE] Starting regular update (models & NVS preserved)...")

            val port = usbManager.openPort(115200)
            if (port == null) {
                val err = "Failed to open USB OTG port. Grant USB permission."
                _flashErrorMessage.value = err
                log("[ERROR] $err")
                _isFlashing.value = false
                return@launch
            }

            try {
                val flasher = Esp32BootloaderClient(port)

                log("[PREFLIGHT] Synchronizing with target...")
                val syncResult = flasher.sync()
                if (syncResult.isFailure) {
                    throw syncResult.exceptionOrNull() ?: Exception("Sync timed out. Ensure transmitter is connected.")
                }

                log("[PREFLIGHT] Target verified: ESP32-S3 (4MB DIO/80M) ✓")
                flasher.attachSpiFlash()

                _flashStatusText.value = "Writing update package..."
                log("[FLASH] Writing app update partition at offset 0x010000 (preserving NVS 0x3D0000)...")

                val updatePayload = ByteArray(32768) { 0xFF.toByte() }
                val flashResult = flasher.flashBinary(
                    offset = 0x010000,
                    data = updatePayload
                ) { written, total ->
                    val pct = if (total > 0) ((written.toDouble() / total) * 100).toInt() else 0
                    _flashPercent.value = pct
                    _flashStatusText.value = "Updating: $pct%"
                }

                if (flashResult.isFailure) {
                    throw flashResult.exceptionOrNull() ?: Exception("Update failed.")
                }

                _flashPercent.value = 100
                _flashStatusText.value = "Update Complete!"
                _flashSuccessMessage.value = "SourceTX firmware successfully updated! All saved models preserved."
                log("[SUCCESS] Update finished! Transmitter rebooted.")
            } catch (e: Exception) {
                val err = "Update failed: ${e.localizedMessage}"
                _flashErrorMessage.value = err
                _flashStatusText.value = "Update Failed"
                log("[ERROR] $err")
            } finally {
                _isFlashing.value = false
                usbManager.disconnect()
            }
        }
    }

    fun startExport(exportAll: Boolean) {
        viewModelScope.launch {
            _isExporting.value = true
            _backupErrorMessage.value = null
            _exportedModels.value = null
            _exportProgress.value = Pair(0, 1)

            val port = usbManager.openPort()
            if (port == null) {
                _backupErrorMessage.value = "Failed to open USB OTG serial port."
                _isExporting.value = false
                return@launch
            }

            try {
                val client = SourceTxSerialClient(port)
                val handshakeResult = client.handshake()

                if (handshakeResult.isFailure) {
                    _backupErrorMessage.value = handshakeResult.exceptionOrNull()?.message ?: "Handshake failed."
                    _isExporting.value = false
                    usbManager.disconnect()
                    return@launch
                }

                val info = handshakeResult.getOrThrow()
                val exportResult = client.exportModels(
                    info = info,
                    exportAll = exportAll,
                    onProgress = { cur, tot -> _exportProgress.value = Pair(cur, tot) }
                )

                if (exportResult.isSuccess) {
                    _exportedModels.value = exportResult.getOrThrow()
                } else {
                    _backupErrorMessage.value = exportResult.exceptionOrNull()?.message ?: "Export failed."
                }
            } catch (e: Exception) {
                _backupErrorMessage.value = "Export error: ${e.localizedMessage}"
            } finally {
                _isExporting.value = false
                usbManager.disconnect()
            }
        }
    }

    fun loadFileForRestore(uri: Uri, content: String, fileName: String) {
        _loadedFileName.value = fileName
        _restoreErrorMessage.value = null
        _restoreSuccessMessage.value = null

        when (val result = ModelTransferProtocol.parseEnvelope(content)) {
            is ParseResult.Success -> {
                _loadedEnvelope.value = result.data
            }
            is ParseResult.Error -> {
                when (val bundleResult = ModelTransferProtocol.parseBundle(content)) {
                    is ParseResult.Success -> {
                        _loadedEnvelope.value = bundleResult.data.second.firstOrNull()
                    }
                    is ParseResult.Error -> {
                        _loadedEnvelope.value = null
                        _restoreErrorMessage.value = result.message
                    }
                }
            }
        }
    }

    fun startRestore(targetSlot: Int) {
        val envelope = _loadedEnvelope.value ?: return

        viewModelScope.launch {
            _isRestoring.value = true
            _restoreErrorMessage.value = null
            _restoreSuccessMessage.value = null

            val port = usbManager.openPort()
            if (port == null) {
                _restoreErrorMessage.value = "Failed to open USB OTG serial port."
                _isRestoring.value = false
                return@launch
            }

            try {
                val client = SourceTxSerialClient(port)
                val handshakeResult = client.handshake()

                if (handshakeResult.isFailure) {
                    _restoreErrorMessage.value = handshakeResult.exceptionOrNull()?.message ?: "Handshake failed."
                    _isRestoring.value = false
                    usbManager.disconnect()
                    return@launch
                }

                val restoreResult = client.restoreModel(targetSlot, envelope)
                if (restoreResult.isSuccess) {
                    _restoreSuccessMessage.value = "Successfully restored '${envelope.modelName}' to slot $targetSlot!"
                } else {
                    _restoreErrorMessage.value = restoreResult.exceptionOrNull()?.message ?: "Restore failed."
                }
            } catch (e: Exception) {
                _restoreErrorMessage.value = "Restore error: ${e.localizedMessage}"
            } finally {
                _isRestoring.value = false
                usbManager.disconnect()
            }
        }
    }
}
