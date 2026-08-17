package com.sourcetx.companion.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
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
import com.sourcetx.companion.protocol.HardwareCatalog
import com.sourcetx.companion.ui.components.ReleaseChannelSelector
import com.sourcetx.companion.ui.components.TechnicalDetailsExpander
import com.sourcetx.companion.ui.theme.GreenSuccess
import com.sourcetx.companion.ui.theme.RedDanger
import com.sourcetx.companion.ui.theme.SourceTxTheme

@Composable
fun InstallScreen(
    catalog: HardwareCatalog?,
    isConnected: Boolean,
    isFlashing: Boolean,
    flashPercent: Int,
    flashStatusText: String,
    consoleLog: String,
    successMessage: String?,
    errorMessage: String?,
    onStartInstall: (eraseFlash: Boolean) -> Unit,
    onNavigateToConfig: (() -> Unit)? = null
) {
    val colors = SourceTxTheme.colors
    var eraseFlash by remember { mutableStateOf(false) }
    var showConfirmation by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Top Header with Channel Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.installBg)
                        .border(1.dp, colors.installBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Install",
                        tint = colors.installAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Install SourceTX",
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "For a new or fully erased board",
                        color = colors.installAccent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            ReleaseChannelSelector()
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Hardware Profile Specs Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceElevated)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Verified hardware profile: SourceTX ESP32-S3 reference transmitter",
                        color = colors.accent,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "The board, flash geometry (4MB DIO), display, and touch controller are verified before installation.",
                        color = colors.textSecondary,
                        fontSize = 10.5.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.backupBg)
                        .border(1.dp, colors.backupBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Supported",
                        color = GreenSuccess,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Erase Chip Option Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .border(1.dp, colors.border, RoundedCornerShape(10.dp))
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = eraseFlash,
                    onCheckedChange = { eraseFlash = it },
                    colors = CheckboxDefaults.colors(checkedColor = RedDanger)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Erase all saved settings and models",
                        color = colors.textPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Leaves transmitter in factory-new state. Leave off unless starting completely fresh.",
                        color = colors.textMuted,
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Progress Bar & Status Text
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = flashStatusText,
                    color = colors.textSecondary,
                    fontSize = 11.5.sp
                )
                Text(
                    text = "$flashPercent%",
                    color = colors.installAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            LinearProgressIndicator(
                progress = { flashPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = colors.installAccent,
                trackColor = colors.surfaceElevated
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Troubleshooting / Technical Details Dropdown
        TechnicalDetailsExpander(
            catalog = catalog,
            consoleLog = consoleLog
        )

        if (successMessage != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, GreenSuccess, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "✓ $successMessage",
                        color = GreenSuccess,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (onNavigateToConfig != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onNavigateToConfig,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.configAccent,
                                contentColor = androidx.compose.ui.graphics.Color(0xFF0A0C10)
                            ),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            Text("Would you like to configure pins now?", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
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

        Spacer(modifier = Modifier.height(16.dp))

        // Action Button
        Button(
            onClick = { showConfirmation = true },
            enabled = isConnected && !isFlashing,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.installAccent,
                contentColor = colors.background,
                disabledContainerColor = colors.surfaceElevated,
                disabledContentColor = colors.textMuted
            )
        ) {
            if (isFlashing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = colors.background,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Installing SourceTX...", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            } else {
                Text(
                    text = if (isConnected) "Install SourceTX" else "Connect USB OTG to Install",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text(if (eraseFlash) "Erase and Install SourceTX?" else "Install SourceTX?") },
            text = {
                Text(
                    if (eraseFlash) {
                        "This permanently erases every saved model and transmitter setting, then installs the verified stable SourceTX release. Keep USB connected until completion."
                    } else {
                        "Install the verified stable SourceTX release on the connected ESP32-S3? Existing flash sectors used by the image will be replaced. Keep USB connected until completion."
                    }
                )
            },
            confirmButton = {
                Button(onClick = {
                    showConfirmation = false
                    onStartInstall(eraseFlash)
                }) { Text(if (eraseFlash) "Erase & Install" else "Install") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}
