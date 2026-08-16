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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.sourcetx.companion.viewmodel.PreparedModelBackup

@Composable
fun BackupScreen(
    isConnected: Boolean,
    isExporting: Boolean,
    exportProgress: Pair<Int, Int>?,
    preparedBackup: PreparedModelBackup?,
    errorMessage: String?,
    onStartExport: (exportAll: Boolean) -> Unit,
    onSaveBackup: (content: String, filename: String) -> Unit
) {
    val colors = SourceTxTheme.colors
    var exportAll by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp)
    ) {
        // Screen Title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.backupBg)
                    .border(1.dp, colors.backupBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FileUpload,
                    contentDescription = "Backup",
                    tint = colors.backupAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Back Up Models",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Export active model (.stxm) or full bundle (.stxb)",
                    color = colors.backupAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Options Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Column {
                Text(
                    text = "Backup Scope",
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { exportAll = false }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = !exportAll,
                        onClick = { exportAll = false },
                        colors = RadioButtonDefaults.colors(selectedColor = colors.backupAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Active Model Only (.stxm)", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Exports currently selected model envelope", color = colors.textSecondary, fontSize = 11.sp)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { exportAll = true }
                        .padding(vertical = 4.dp)
                ) {
                    RadioButton(
                        selected = exportAll,
                        onClick = { exportAll = true },
                        colors = RadioButtonDefaults.colors(selectedColor = colors.backupAccent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = "Export Everything (.stxb bundle)", color = colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Text(text = "Exports all configured transmitter models with SHA-256 integrity", color = colors.textSecondary, fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Action Button
        Button(
            onClick = { onStartExport(exportAll) },
            enabled = isConnected && !isExporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.backupAccent,
                contentColor = colors.background,
                disabledContainerColor = colors.surfaceElevated,
                disabledContentColor = colors.textMuted
            )
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colors.background,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                val progressText = exportProgress?.let { "Exporting (${it.first}/${it.second})..." } ?: "Connecting..."
                Text(text = progressText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            } else {
                Text(
                    text = if (isConnected) "Start Model Export" else "Connect USB OTG to Export",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "⚠️ $errorMessage",
                    color = colors.textSecondary,
                    fontSize = 11.5.sp
                )
            }
        }

        // Export Results
        if (preparedBackup != null && preparedBackup.models.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = GreenSuccess,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Verified ${preparedBackup.models.size} Model(s)",
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { onSaveBackup(preparedBackup.content, preparedBackup.fileName) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceElevated,
                        contentColor = colors.textPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Save Backup", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(preparedBackup.models.entries.toList()) { entry ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Slot ${entry.key}: ${entry.value.modelName}",
                                    color = colors.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Schema ${entry.value.schema} • ${entry.value.payloadSize} bytes • CRC 0x${entry.value.checksum.toString(16).uppercase()}",
                                    color = colors.textMuted,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
