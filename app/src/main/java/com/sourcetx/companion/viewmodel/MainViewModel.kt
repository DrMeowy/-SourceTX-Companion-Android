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
import com.sourcetx.companion.usb.SourceTxSerialClient
import com.sourcetx.companion.usb.SourceTxUsbManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppScreen {
    HOME,
    INSTALL,
    UPDATE,
    BACKUP,
    RESTORE
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    val usbManager = SourceTxUsbManager(application)

    private val _currentScreen = MutableStateFlow(AppScreen.HOME)
    val currentScreen: StateFlow<AppScreen> = _currentScreen.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(true)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _catalog = MutableStateFlow<HardwareCatalog?>(null)
    val catalog: StateFlow<HardwareCatalog?> = _catalog.asStateFlow()

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
    }

    override fun onCleared() {
        super.onCleared()
        usbManager.unregister()
    }

    fun navigateTo(screen: AppScreen) {
        _currentScreen.value = screen
        _backupErrorMessage.value = null
        _restoreErrorMessage.value = null
        _restoreSuccessMessage.value = null
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
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
                // Try parsing as bundle
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
