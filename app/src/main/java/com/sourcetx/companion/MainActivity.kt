package com.sourcetx.companion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.sourcetx.companion.ui.components.AppUpdateDialog
import com.sourcetx.companion.ui.components.SourceTxStatusBar
import com.sourcetx.companion.ui.components.SourceTxTopBar
import com.sourcetx.companion.ui.screens.BackupScreen
import com.sourcetx.companion.ui.screens.HomeScreen
import com.sourcetx.companion.ui.screens.InstallScreen
import com.sourcetx.companion.ui.screens.RestoreScreen
import com.sourcetx.companion.ui.screens.UpdateScreen
import com.sourcetx.companion.ui.theme.SourceTxTheme
import com.sourcetx.companion.viewmodel.AppScreen
import com.sourcetx.companion.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleSelectedFile(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val isDarkTheme by viewModel.isDarkTheme.collectAsState()
            val currentScreen by viewModel.currentScreen.collectAsState()
            val connectedDevice by viewModel.usbManager.connectedDevice.collectAsState()
            val hasPermission by viewModel.usbManager.hasPermission.collectAsState()
            val catalog by viewModel.catalog.collectAsState()

            // In-app self update state
            val isCheckingUpdate by viewModel.isCheckingAppUpdate.collectAsState()
            val appReleaseInfo by viewModel.appReleaseInfo.collectAsState()
            val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
            val isDownloadingUpdate by viewModel.isDownloadingAppUpdate.collectAsState()
            val downloadPercent by viewModel.appUpdateDownloadPercent.collectAsState()
            val updateError by viewModel.appUpdateErrorMessage.collectAsState()

            // Flashing state
            val isFlashing by viewModel.isFlashing.collectAsState()
            val flashPercent by viewModel.flashPercent.collectAsState()
            val flashStatusText by viewModel.flashStatusText.collectAsState()
            val consoleLog by viewModel.consoleLog.collectAsState()
            val flashSuccess by viewModel.flashSuccessMessage.collectAsState()
            val flashError by viewModel.flashErrorMessage.collectAsState()

            // Backup state
            val isExporting by viewModel.isExporting.collectAsState()
            val exportProgress by viewModel.exportProgress.collectAsState()
            val exportedModels by viewModel.exportedModels.collectAsState()
            val backupError by viewModel.backupErrorMessage.collectAsState()

            // Restore state
            val loadedEnvelope by viewModel.loadedEnvelope.collectAsState()
            val loadedFileName by viewModel.loadedFileName.collectAsState()
            val isRestoring by viewModel.isRestoring.collectAsState()
            val restoreSuccess by viewModel.restoreSuccessMessage.collectAsState()
            val restoreError by viewModel.restoreErrorMessage.collectAsState()

            SourceTxTheme(darkTheme = isDarkTheme) {
                Scaffold(
                    topBar = {
                        SourceTxTopBar(
                            title = "SourceTX",
                            version = "v${viewModel.currentAppVersion}",
                            isDarkTheme = isDarkTheme,
                            isCheckingUpdate = isCheckingUpdate,
                            hasUpdateAvailable = appReleaseInfo?.isNewer == true,
                            onToggleTheme = { viewModel.toggleTheme() },
                            onCheckUpdate = { viewModel.checkForAppUpdate(silent = false) },
                            showBackButton = currentScreen != AppScreen.HOME,
                            onBackClick = { viewModel.navigateTo(AppScreen.HOME) }
                        )
                    },
                    bottomBar = {
                        SourceTxStatusBar(
                            connectedDevice = connectedDevice,
                            hasPermission = hasPermission
                        )
                    }
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(SourceTxTheme.colors.background)
                    ) {
                        when (currentScreen) {
                            AppScreen.HOME -> {
                                HomeScreen(
                                    onNavigateToInstall = { viewModel.navigateTo(AppScreen.INSTALL) },
                                    onNavigateToUpdate = { viewModel.navigateTo(AppScreen.UPDATE) },
                                    onNavigateToBackup = { viewModel.navigateTo(AppScreen.BACKUP) },
                                    onNavigateToRestore = { viewModel.navigateTo(AppScreen.RESTORE) }
                                )
                            }
                            AppScreen.INSTALL -> {
                                InstallScreen(
                                    catalog = catalog,
                                    isConnected = connectedDevice != null && hasPermission,
                                    isFlashing = isFlashing,
                                    flashPercent = flashPercent,
                                    flashStatusText = flashStatusText,
                                    consoleLog = consoleLog,
                                    successMessage = flashSuccess,
                                    errorMessage = flashError,
                                    onStartInstall = { eraseFlash -> viewModel.startFactoryInstall(eraseFlash) }
                                )
                            }
                            AppScreen.UPDATE -> {
                                UpdateScreen(
                                    catalog = catalog,
                                    isConnected = connectedDevice != null && hasPermission,
                                    isFlashing = isFlashing,
                                    flashPercent = flashPercent,
                                    flashStatusText = flashStatusText,
                                    consoleLog = consoleLog,
                                    successMessage = flashSuccess,
                                    errorMessage = flashError,
                                    onStartUpdate = { viewModel.startRegularUpdate() }
                                )
                            }
                            AppScreen.BACKUP -> {
                                BackupScreen(
                                    isConnected = connectedDevice != null && hasPermission,
                                    isExporting = isExporting,
                                    exportProgress = exportProgress,
                                    exportedModels = exportedModels,
                                    errorMessage = backupError,
                                    onStartExport = { exportAll -> viewModel.startExport(exportAll) },
                                    onShareBackup = { content, filename -> shareBackupFile(content, filename) }
                                )
                            }
                            AppScreen.RESTORE -> {
                                RestoreScreen(
                                    isConnected = connectedDevice != null && hasPermission,
                                    loadedEnvelope = loadedEnvelope,
                                    loadedFileName = loadedFileName,
                                    isRestoring = isRestoring,
                                    restoreSuccessMessage = restoreSuccess,
                                    errorMessage = restoreError,
                                    onPickFile = { filePickerLauncher.launch("*/*") },
                                    onStartRestore = { targetSlot -> viewModel.startRestore(targetSlot) }
                                )
                            }
                        }

                        // App Self-Update Dialog
                        if (showUpdateDialog && appReleaseInfo != null) {
                            AppUpdateDialog(
                                releaseInfo = appReleaseInfo!!,
                                isDownloading = isDownloadingUpdate,
                                downloadPercent = downloadPercent,
                                errorMessage = updateError,
                                onDismiss = { viewModel.dismissUpdateDialog() },
                                onConfirmUpdate = { viewModel.downloadAndInstallAppUpdate() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun handleSelectedFile(uri: Uri) {
        try {
            var fileName = "model.stxm"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            val content = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            viewModel.loadFileForRestore(uri, content, fileName)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareBackupFile(content: String, filename: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, content)
            putExtra(Intent.EXTRA_TITLE, filename)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Save or Share SourceTX Backup")
        startActivity(shareIntent)
    }
}
