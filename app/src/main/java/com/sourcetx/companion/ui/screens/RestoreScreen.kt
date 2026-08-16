package com.sourcetx.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sourcetx.companion.ui.theme.GreenSuccess
import com.sourcetx.companion.ui.theme.SourceTxTheme
import com.sourcetx.companion.viewmodel.LoadedModelBackup

@Composable
fun RestoreScreen(
    isConnected: Boolean,
    loadedBackup: LoadedModelBackup?,
    isRestoring: Boolean,
    restoreProgress: Pair<Int, Int>?,
    restoreSuccessMessage: String?,
    errorMessage: String?,
    onPickFile: () -> Unit,
    onStartRestore: (targetSlot: Int) -> Unit
) {
    val colors = SourceTxTheme.colors
    var selectedSlot by remember { mutableIntStateOf(1) }
    var showConfirmation by remember { mutableStateOf(false) }
    val complete = loadedBackup as? LoadedModelBackup.Complete
    val single = loadedBackup as? LoadedModelBackup.Single

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(colors.restoreBg)
                    .border(1.dp, colors.restoreBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FileDownload, "Restore", tint = colors.restoreAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Restore Models", color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Import a verified .stxm or complete .stxb backup", color = colors.restoreAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(12.dp)).padding(14.dp)
        ) {
            Column {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text("Model Backup File", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Button(
                        onClick = onPickFile,
                        enabled = !isRestoring,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(colors.surfaceElevated, colors.textPrimary)
                    ) {
                        Icon(Icons.Default.Folder, "Choose backup", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Choose File", fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (loadedBackup) {
                    is LoadedModelBackup.Single -> BackupSummary(
                        title = "Model: ${loadedBackup.envelope.modelName}",
                        fileName = loadedBackup.fileName,
                        detail = "Schema ${loadedBackup.envelope.schema} • ${loadedBackup.envelope.payloadSize} bytes • Integrity verified"
                    )
                    is LoadedModelBackup.Complete -> BackupSummary(
                        title = "Complete backup: ${loadedBackup.bundle.modelCount} models",
                        fileName = loadedBackup.fileName,
                        detail = "Active slot ${loadedBackup.bundle.activeModel} • SHA-256 and every model verified"
                    )
                    null -> Text(
                        "No backup selected. Choose a SourceTX .stxm or .stxb file.",
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (single != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(colors.surface).border(1.dp, colors.border, RoundedCornerShape(12.dp)).padding(14.dp)
            ) {
                Column {
                    Text("Choose Target Model Slot (1–20)", color = colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("The model currently in that slot will be overwritten.", color = colors.textMuted, fontSize = 10.5.sp)
                    Spacer(Modifier.height(10.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(5),
                        modifier = Modifier.fillMaxWidth().height(156.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        userScrollEnabled = false
                    ) {
                        items((1..20).toList()) { slot ->
                            val selected = selectedSlot == slot
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) colors.restoreAccent else colors.surfaceElevated)
                                    .border(1.dp, if (selected) colors.restoreAccent else colors.border, RoundedCornerShape(6.dp))
                                    .clickable(enabled = !isRestoring) { selectedSlot = slot }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$slot", color = if (selected) colors.background else colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        } else if (complete != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceElevated).border(1.dp, colors.restoreBorder, RoundedCornerShape(10.dp)).padding(12.dp)
            ) {
                Text(
                    "All ${complete.bundle.modelCount} model slots in the backup will be restored. Existing models in those slots will be overwritten.",
                    color = colors.textSecondary,
                    fontSize = 11.5.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { showConfirmation = true },
            enabled = isConnected && loadedBackup != null && !isRestoring,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.restoreAccent,
                contentColor = colors.background,
                disabledContainerColor = colors.surfaceElevated,
                disabledContentColor = colors.textMuted
            )
        ) {
            if (isRestoring) {
                CircularProgressIndicator(Modifier.size(20.dp), color = colors.background, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                val progress = restoreProgress?.let { " (${it.first}/${it.second})" }.orEmpty()
                Text("Restoring$progress...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            } else {
                Text(
                    when {
                        loadedBackup == null -> "Choose a Backup First"
                        !isConnected -> "Connect SourceTX by USB OTG"
                        complete != null -> "Restore All ${complete.bundle.modelCount} Models"
                        else -> "Restore Model to Slot $selectedSlot"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        MessageBox(restoreSuccessMessage, errorMessage)
    }

    if (showConfirmation && loadedBackup != null) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Confirm Model Restore") },
            text = {
                Text(if (complete != null) {
                    "Restore all ${complete.bundle.modelCount} models? Existing models in those slots will be overwritten."
                } else {
                    "Restore '${single?.envelope?.modelName}' to slot $selectedSlot? The existing model in that slot will be overwritten."
                })
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmation = false
                    onStartRestore(selectedSlot)
                }) { Text("Restore") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun BackupSummary(title: String, fileName: String, detail: String) {
    val colors = SourceTxTheme.colors
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated).border(1.dp, colors.border, RoundedCornerShape(8.dp)).padding(10.dp)
    ) {
        Column {
            Text(title, color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("File: $fileName", color = colors.textSecondary, fontSize = 11.sp)
            Text(detail, color = GreenSuccess, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun MessageBox(success: String?, error: String?) {
    val colors = SourceTxTheme.colors
    val message = success ?: error ?: return
    val successState = success != null
    Spacer(Modifier.height(12.dp))
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, if (successState) GreenSuccess else colors.border, RoundedCornerShape(8.dp)).padding(12.dp)
    ) {
        Text(
            (if (successState) "✓ " else "⚠ ") + message,
            color = if (successState) GreenSuccess else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (successState) FontWeight.Medium else FontWeight.Normal
        )
    }
}
