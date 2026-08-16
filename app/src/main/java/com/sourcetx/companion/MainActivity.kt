package com.sourcetx.companion

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.ByteArrayOutputStream
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
import androidx.compose.runtime.LaunchedEffect
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

    private var pendingBackupContent: String? = null
    private val backupSaverLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        val content = pendingBackupContent
        pendingBackupContent = null
        if (uri == null || content == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri, "wt")?.use { output ->
                output.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("Android could not open the selected destination.")
            Toast.makeText(this, "SourceTX backup saved.", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(this, "Could not save backup: ${error.message}", Toast.LENGTH_LONG).show()
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

            LaunchedEffect(updateError, showUpdateDialog) {
                if (updateError != null && !showUpdateDialog) {
                    Toast.makeText(this@MainActivity, updateError, Toast.LENGTH_LONG).show()
                    viewModel.consumeAppUpdateError()
                }
            }

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
            val preparedBackup by viewModel.preparedBackup.collectAsState()
            val backupError by viewModel.backupErrorMessage.collectAsState()

            // Restore state
            val loadedBackup by viewModel.loadedBackup.collectAsState()
            val isRestoring by viewModel.isRestoring.collectAsState()
            val restoreProgress by viewModel.restoreProgress.collectAsState()
            val restoreSuccess by viewModel.restoreSuccessMessage.collectAsState()
            val restoreError by viewModel.restoreErrorMessage.collectAsState()

            SourceTxTheme(darkTheme = isDarkTheme) {
                Scaffold(
                    topBar = {
                        SourceTxTopBar(
                            title = "SourceTX",
                            version = "v${viewModel.currentAppVersion}",
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { viewModel.toggleTheme() },
                            showBackButton = currentScreen != AppScreen.HOME,
                            onBackClick = { viewModel.navigateTo(AppScreen.HOME) }
                        )
                    },
                    bottomBar = {
                        SourceTxStatusBar(
                            connectedDevice = connectedDevice,
                            hasPermission = hasPermission,
                            version = "v${viewModel.currentAppVersion}",
                            isCheckingUpdate = isCheckingUpdate,
                            hasUpdateAvailable = appReleaseInfo?.isNewer == true,
                            onCheckUpdate = { viewModel.checkForAppUpdate(silent = false) },
                            onReportBug = { openGitHubIssues() }
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
                                    isConnected = connectedDevice?.isEspressif == true && hasPermission,
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
                                    isConnected = connectedDevice?.isEspressif == true && hasPermission,
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
                                    preparedBackup = preparedBackup,
                                    errorMessage = backupError,
                                    onStartExport = { exportAll -> viewModel.startExport(exportAll) },
                                    onSaveBackup = { content, filename -> saveBackupFile(content, filename) }
                                )
                            }
                            AppScreen.RESTORE -> {
                                RestoreScreen(
                                    isConnected = connectedDevice != null && hasPermission,
                                    loadedBackup = loadedBackup,
                                    isRestoring = isRestoring,
                                    restoreProgress = restoreProgress,
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

            val content = contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > 8 * 1024 * 1024) error("Backup files are limited to 8 MB.")
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            } ?: error("Android could not open the selected backup.")
            viewModel.loadFileForRestore(content, fileName)
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to read file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveBackupFile(content: String, filename: String) {
        pendingBackupContent = content
        backupSaverLauncher.launch(filename)
    }

    private fun openGitHubIssues() {
        val intent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://github.com/DrMeowy/-SourceTX-Companion-Android/issues")
        )
        startActivity(intent)
    }
}
