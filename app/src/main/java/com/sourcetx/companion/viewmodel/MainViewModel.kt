package com.sourcetx.companion.viewmodel

import android.app.Application
import com.sourcetx.companion.BuildConfig
import com.sourcetx.companion.firmware.FirmwareRepository
import com.sourcetx.companion.protocol.HardwareCatalog
import com.sourcetx.companion.protocol.ModelTransferProtocol
import com.sourcetx.companion.protocol.ParseResult
import com.sourcetx.companion.protocol.TargetsCatalog
import com.sourcetx.companion.updater.AppReleaseInfo
import com.sourcetx.companion.updater.AppUpdateManager
import com.sourcetx.companion.usb.Esp32BootloaderClient
import com.sourcetx.companion.usb.HardwarePinSettings
import com.sourcetx.companion.usb.SourceTxSerialClient
import com.sourcetx.companion.usb.SourceTxUsbManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppScreen { HOME, INSTALL, UPDATE, CONFIG, BACKUP, RESTORE }

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val currentAppVersion: String = BuildConfig.VERSION_NAME
    val usbManager = SourceTxUsbManager(application)
    private val firmwareRepository = FirmwareRepository(currentAppVersion)
    private val preferences = application.getSharedPreferences("companion_preferences", 0)

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()
    private val _isDarkTheme = MutableStateFlow(preferences.getBoolean("dark_theme", true))
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()
    private val _catalog = MutableStateFlow<HardwareCatalog?>(null)
    val catalog: StateFlow<HardwareCatalog?> = _catalog.asStateFlow()

    private val _isCheckingAppUpdate = MutableStateFlow(false)
    val isCheckingAppUpdate = _isCheckingAppUpdate.asStateFlow()
    private val _appReleaseInfo = MutableStateFlow<AppReleaseInfo?>(null)
    val appReleaseInfo = _appReleaseInfo.asStateFlow()
    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog = _showUpdateDialog.asStateFlow()
    private val _isDownloadingAppUpdate = MutableStateFlow(false)
    val isDownloadingAppUpdate = _isDownloadingAppUpdate.asStateFlow()
    private val _appUpdateDownloadPercent = MutableStateFlow(0)
    val appUpdateDownloadPercent = _appUpdateDownloadPercent.asStateFlow()
    private val _appUpdateErrorMessage = MutableStateFlow<String?>(null)
    val appUpdateErrorMessage = _appUpdateErrorMessage.asStateFlow()

    private val _isFlashing = MutableStateFlow(false)
    val isFlashing = _isFlashing.asStateFlow()
    private val _flashPercent = MutableStateFlow(0)
    val flashPercent = _flashPercent.asStateFlow()
    private val _flashStatusText = MutableStateFlow("Ready — connect the transmitter with USB OTG")
    val flashStatusText = _flashStatusText.asStateFlow()
    private val _consoleLog = MutableStateFlow("[READY] No firmware has been written.")
    val consoleLog = _consoleLog.asStateFlow()
    private val _flashSuccessMessage = MutableStateFlow<String?>(null)
    val flashSuccessMessage = _flashSuccessMessage.asStateFlow()
    private val _flashErrorMessage = MutableStateFlow<String?>(null)
    val flashErrorMessage = _flashErrorMessage.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting = _isExporting.asStateFlow()
    private val _exportProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val exportProgress = _exportProgress.asStateFlow()
    private val _preparedBackup = MutableStateFlow<PreparedModelBackup?>(null)
    val preparedBackup = _preparedBackup.asStateFlow()
    private val _backupErrorMessage = MutableStateFlow<String?>(null)
    val backupErrorMessage = _backupErrorMessage.asStateFlow()

    private val _loadedBackup = MutableStateFlow<LoadedModelBackup?>(null)
    val loadedBackup = _loadedBackup.asStateFlow()
    private val _isRestoring = MutableStateFlow(false)
    val isRestoring = _isRestoring.asStateFlow()
    private val _restoreProgress = MutableStateFlow<Pair<Int, Int>?>(null)
    val restoreProgress = _restoreProgress.asStateFlow()
    private val _restoreSuccessMessage = MutableStateFlow<String?>(null)
    val restoreSuccessMessage = _restoreSuccessMessage.asStateFlow()
    private val _restoreErrorMessage = MutableStateFlow<String?>(null)
    val restoreErrorMessage = _restoreErrorMessage.asStateFlow()

    // Hardware Pin Configuration state
    private val _hardwarePinSettings = MutableStateFlow(HardwarePinSettings())
    val hardwarePinSettings: StateFlow<HardwarePinSettings> = _hardwarePinSettings.asStateFlow()
    private val _isReadingHardwareConfig = MutableStateFlow(false)
    val isReadingHardwareConfig: StateFlow<Boolean> = _isReadingHardwareConfig.asStateFlow()
    private val _isSavingHardwareConfig = MutableStateFlow(false)
    val isSavingHardwareConfig: StateFlow<Boolean> = _isSavingHardwareConfig.asStateFlow()
    private val _hardwareConfigSuccess = MutableStateFlow<String?>(null)
    val hardwareConfigSuccess: StateFlow<String?> = _hardwareConfigSuccess.asStateFlow()
    private val _hardwareConfigError = MutableStateFlow<String?>(null)
    val hardwareConfigError: StateFlow<String?> = _hardwareConfigError.asStateFlow()
    private val _hardwareConfigLogs = MutableStateFlow<List<String>>(listOf("[READY] Connect the transmitter via USB OTG."))
    val hardwareConfigLogs: StateFlow<List<String>> = _hardwareConfigLogs.asStateFlow()

    init {
        usbManager.register()
        _catalog.value = TargetsCatalog.loadFromAssets(application)
        checkForAppUpdate(silent = true)
    }

    override fun onCleared() {
        usbManager.unregister()
        super.onCleared()
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        _flashErrorMessage.value = null
        _flashSuccessMessage.value = null
        _backupErrorMessage.value = null
        _restoreErrorMessage.value = null
        _restoreSuccessMessage.value = null
        _hardwareConfigError.value = null
        _hardwareConfigSuccess.value = null
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
        preferences.edit().putBoolean("dark_theme", _isDarkTheme.value).apply()
    }

    fun checkForAppUpdate(silent: Boolean = false) {
        viewModelScope.launch {
            _isCheckingAppUpdate.value = true
            _appUpdateErrorMessage.value = null
            val result = AppUpdateManager.checkForUpdates(currentAppVersion)
            _isCheckingAppUpdate.value = false
            result.onSuccess { info ->
                _appReleaseInfo.value = info
                if (info != null && (info.isNewer || !silent)) _showUpdateDialog.value = true
            }.onFailure { error ->
                if (!silent) _appUpdateErrorMessage.value = error.message ?: "Could not check for app updates."
            }
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
        _appUpdateErrorMessage.value = null
    }

    fun consumeAppUpdateError() {
        _appUpdateErrorMessage.value = null
    }

    fun downloadAndInstallAppUpdate() {
        val info = _appReleaseInfo.value ?: return
        if (!info.isNewer) {
            _showUpdateDialog.value = false
            return
        }
        viewModelScope.launch {
            _isDownloadingAppUpdate.value = true
            _appUpdateDownloadPercent.value = 0
            _appUpdateErrorMessage.value = null
            AppUpdateManager.downloadAndInstallApk(
                getApplication(),
                info
            ) { percent, _, _ -> _appUpdateDownloadPercent.value = percent }
                .onSuccess { _showUpdateDialog.value = false }
                .onFailure { _appUpdateErrorMessage.value = it.message ?: "App update download failed." }
            _isDownloadingAppUpdate.value = false
        }
    }

    fun startFactoryInstall(eraseFlash: Boolean) {
        startFirmwareOperation(factory = true, eraseFlash = eraseFlash)
    }

    fun startRegularUpdate() {
        startFirmwareOperation(factory = false, eraseFlash = false)
    }

    private fun startFirmwareOperation(factory: Boolean, eraseFlash: Boolean) {
        if (_isFlashing.value) return
        viewModelScope.launch {
            _isFlashing.value = true
            _flashPercent.value = 0
            _flashErrorMessage.value = null
            _flashSuccessMessage.value = null
            _consoleLog.value = if (factory) "[INSTALL] Preparing a verified factory installation." else "[UPDATE] Preparing a verified application update."
            try {
                val board = _catalog.value?.boards?.singleOrNull { it.enabled }
                    ?: error("The supported SourceTX hardware profile could not be loaded.")
                _flashStatusText.value = "Downloading and verifying the signed stable release..."
                val packageResult = if (factory) {
                    firmwareRepository.acquireFactory(board) { percent ->
                        _flashPercent.value = percent / 5
                    }
                } else {
                    firmwareRepository.acquireApplication(board) { percent ->
                        _flashPercent.value = percent / 5
                    }
                }
                val firmwarePackage = packageResult.getOrThrow()
                log("[VERIFY] Signed SourceTX v${firmwarePackage.manifest.version} package verified (${firmwarePackage.image.size} bytes).")

                _flashStatusText.value = "Checking the connected ESP32-S3..."
                val port = usbManager.openPort(115200, requireEspressif = true)
                    ?: error("Connect the ESP32-S3 by USB OTG, grant permission, and try again.")
                val flasher = Esp32BootloaderClient(port)
                val target = flasher.preflight().getOrThrow()
                log("[PREFLIGHT] ESP32-S3 confirmed; JEDEC flash ID 0x${target.flashId.toString(16).uppercase()}, 4MB.")
                _flashPercent.value = 22

                if (factory && eraseFlash) {
                    _flashStatusText.value = "Erasing all saved settings, models, and firmware..."
                    log("[ERASE] Full flash erase requested.")
                    flasher.eraseEntireFlash { status -> log("[ERASE] $status") }.getOrThrow()
                    _flashPercent.value = 28
                }

                val offset = if (factory) 0x000000 else 0x010000
                _flashStatusText.value = if (factory) "Installing SourceTX — do not disconnect USB..." else "Updating SourceTX — do not disconnect USB..."
                log("[FLASH] Writing verified image at 0x${offset.toString(16).padStart(6, '0').uppercase()}.")
                flasher.writeAndVerify(offset, firmwarePackage.image) { written, total ->
                    val start = if (factory && eraseFlash) 28 else 22
                    _flashPercent.value = start + ((written.toLong() * (96 - start)) / total.toLong()).toInt()
                    _flashStatusText.value = "Writing and verifying SourceTX: ${_flashPercent.value}%"
                }.getOrThrow()
                log("[VERIFY] ESP32-S3 flash MD5 matches the signed firmware package.")
                _flashPercent.value = 98
                _flashStatusText.value = "Restarting the transmitter..."
                flasher.reboot().getOrThrow()
                _flashPercent.value = 100
                _flashStatusText.value = if (factory) "Installation complete" else "Update complete"
                _flashSuccessMessage.value = if (factory) {
                    "SourceTX v${firmwarePackage.manifest.version} was installed and verified."
                } else {
                    "SourceTX v${firmwarePackage.manifest.version} was updated and verified. Saved models were preserved."
                }
                log("[SUCCESS] Firmware write, on-device verification, and restart completed.")
            } catch (error: Throwable) {
                val message = error.message ?: "Unknown firmware operation error."
                _flashErrorMessage.value = message
                _flashStatusText.value = if (factory) "Installation stopped" else "Update stopped"
                log("[ERROR] $message")
            } finally {
                usbManager.disconnect()
                _isFlashing.value = false
            }
        }
    }

    fun startExport(exportAll: Boolean) {
        if (_isExporting.value) return
        viewModelScope.launch {
            _isExporting.value = true
            _backupErrorMessage.value = null
            _preparedBackup.value = null
            _exportProgress.value = 0 to 1
            try {
                val port = usbManager.openPort() ?: error("Connect SourceTX by USB OTG and grant USB permission.")
                val client = SourceTxSerialClient(port)
                val info = client.handshake().getOrThrow()
                val models = client.exportModels(info, exportAll) { current, total ->
                    _exportProgress.value = current to total
                }.getOrThrow()
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
                _preparedBackup.value = if (exportAll) {
                    val bundle = when (val result = ModelTransferProtocol.createBundle(
                        models,
                        info.activeModel,
                        info.protocolVersion
                    )) {
                        is ParseResult.Error -> error(result.message)
                        is ParseResult.Success -> result.data
                    }
                    PreparedModelBackup(
                        ModelTransferProtocol.serializeBundle(bundle),
                        "SourceTX_all_models_$date.stxb",
                        models,
                        true
                    )
                } else {
                    val envelope = models.values.single()
                    PreparedModelBackup(
                        envelope.text,
                        "${safeFileName(envelope.modelName)}_model.stxm",
                        models,
                        false
                    )
                }
            } catch (error: Throwable) {
                _backupErrorMessage.value = error.message ?: "Model backup failed."
            } finally {
                usbManager.disconnect()
                _isExporting.value = false
            }
        }
    }

    fun loadFileForRestore(content: String, fileName: String) {
        _loadedBackup.value = null
        _restoreErrorMessage.value = null
        _restoreSuccessMessage.value = null
        val trimmed = content.trim()
        if (trimmed.startsWith("{")) {
            when (val result = ModelTransferProtocol.parseBundle(trimmed)) {
                is ParseResult.Error -> _restoreErrorMessage.value = result.message
                is ParseResult.Success -> _loadedBackup.value = LoadedModelBackup.Complete(
                    fileName,
                    result.data.first,
                    result.data.second
                )
            }
        } else {
            when (val result = ModelTransferProtocol.parseEnvelope(trimmed)) {
                is ParseResult.Error -> _restoreErrorMessage.value = result.message
                is ParseResult.Success -> _loadedBackup.value = LoadedModelBackup.Single(fileName, result.data)
            }
        }
    }

    fun startRestore(targetSlot: Int) {
        val backup = _loadedBackup.value ?: return
        if (_isRestoring.value) return
        viewModelScope.launch {
            _isRestoring.value = true
            _restoreErrorMessage.value = null
            _restoreSuccessMessage.value = null
            _restoreProgress.value = null
            try {
                val port = usbManager.openPort() ?: error("Connect SourceTX by USB OTG and grant USB permission.")
                val client = SourceTxSerialClient(port)
                val info = client.handshake().getOrThrow()
                when (backup) {
                    is LoadedModelBackup.Single -> {
                        client.importModel(info, targetSlot, backup.envelope).getOrThrow()
                        _restoreSuccessMessage.value = "Restored '${backup.envelope.modelName}' to model slot $targetSlot."
                    }
                    is LoadedModelBackup.Complete -> {
                        client.restoreBundle(info, backup.bundle, backup.envelopes) { current, total ->
                            _restoreProgress.value = current to total
                        }.getOrThrow()
                        _restoreSuccessMessage.value = "Restored all ${backup.bundle.modelCount} models successfully."
                    }
                }
            } catch (error: Throwable) {
                _restoreErrorMessage.value = error.message ?: "Model restore failed."
            } finally {
                usbManager.disconnect()
                _isRestoring.value = false
            }
        }
    }

    private fun log(message: String) {
        _consoleLog.value += "\n$message"
    }

    fun updateHardwareSettingsLocally(settings: HardwarePinSettings) {
        _hardwarePinSettings.value = settings
    }

    fun clearHardwareConfigMessages() {
        _hardwareConfigSuccess.value = null
        _hardwareConfigError.value = null
    }

    private fun logHardware(msg: String) {
        _hardwareConfigLogs.value = _hardwareConfigLogs.value + msg
    }

    fun readHardwareSettings() {
        if (_isReadingHardwareConfig.value || _isSavingHardwareConfig.value) return
        viewModelScope.launch {
            _isReadingHardwareConfig.value = true
            _hardwareConfigError.value = null
            _hardwareConfigSuccess.value = null
            logHardware("[READ] Requesting hardware pin settings from transmitter...")
            try {
                val port = usbManager.openPort() ?: error("Connect SourceTX by USB OTG, grant USB permission, and try again.")
                val client = SourceTxSerialClient(port)
                val settings = client.getHardwareSettings().getOrThrow()
                _hardwarePinSettings.value = settings
                _hardwareConfigSuccess.value = "Hardware settings loaded from transmitter NVS."
                logHardware("[SUCCESS] Loaded from NVS: CRSF=GPIO ${settings.crsfPin}, StatusMode=${settings.statusMode}, SoundMode=${settings.soundMode}")
            } catch (error: Throwable) {
                val msg = error.message ?: "Failed to read hardware settings."
                _hardwareConfigError.value = msg
                logHardware("[ERROR] $msg")
            } finally {
                usbManager.disconnect()
                _isReadingHardwareConfig.value = false
            }
        }
    }

    fun saveHardwareSettings(settings: HardwarePinSettings) {
        if (_isReadingHardwareConfig.value || _isSavingHardwareConfig.value) return
        viewModelScope.launch {
            _isSavingHardwareConfig.value = true
            _hardwareConfigError.value = null
            _hardwareConfigSuccess.value = null
            logHardware("[WRITE] Writing hardware pin settings to transmitter NVS...")
            try {
                // Check pin collisions
                val used = mutableMapOf<Int, String>()
                fun checkCol(pin: Int, name: String) {
                    if (pin >= 0) {
                        if (used.containsKey(pin)) {
                            error("GPIO $pin is assigned to both '${used[pin]}' and '$name'. Each physical pin can only be assigned to one function.")
                        }
                        used[pin] = name
                    }
                }
                checkCol(settings.crsfPin, "CRSF UART")
                if (settings.statusMode == 1) checkCol(settings.statusMonoPin, "Status Mono LED")
                if (settings.statusMode == 3) checkCol(settings.statusMonoPin, "Status WS2812 NeoPixel LED")
                if (settings.statusMode == 2) {
                    checkCol(settings.statusRedPin, "Status Red LED")
                    checkCol(settings.statusGreenPin, "Status Green LED")
                    checkCol(settings.statusBluePin, "Status Blue LED")
                }
                if (settings.soundMode != 0) checkCol(settings.soundPin, "Sound Output")
                checkCol(settings.vibrationPin, "Vibration Motor")

                val port = usbManager.openPort() ?: error("Connect SourceTX by USB OTG, grant USB permission, and try again.")
                val client = SourceTxSerialClient(port)
                client.setHardwareSettings(settings).getOrThrow()
                _hardwarePinSettings.value = settings
                _hardwareConfigSuccess.value = "Hardware pin configuration saved to transmitter NVS!"
                logHardware("[SUCCESS] Settings successfully written to NVS.")
            } catch (error: Throwable) {
                val msg = error.message ?: "Failed to save hardware settings."
                _hardwareConfigError.value = msg
                logHardware("[ERROR] $msg")
            } finally {
                usbManager.disconnect()
                _isSavingHardwareConfig.value = false
            }
        }
    }

    private fun safeFileName(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_', '.', ' ')
        .ifBlank { "SourceTX_model" }
}
